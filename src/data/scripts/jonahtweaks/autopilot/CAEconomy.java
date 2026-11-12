package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.util.Misc;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 0.3.0.0 (WS-C4, research 03 F5, F6, F8, F9, F11): the rules for the spending the money ladder's Economy rung covers
 * (CARich.RUNGS "Economy"): surplus research, surplus terraforming, the capital's trade buildings that cost more than they
 * earn, hazard pay a terraforming project saves, and shipping between colonies' Autopilot Storage. The pure rules are
 * checked offline (JTEndgameCheck, JTStoresCheck); the live reads below feed them. Nothing here is saved.
 *
 * - F5 research: from the money tier Idle money up, another Research Facility's speed must beat half its upkeep; below,
 *   all of it. Below Rich, surplus research builds at most RESEARCH_CAP facilities of its own.
 * - Outside the pool (0.3.0.0 fix, DECISIONS 14b): surplus research, surplus terraforming and the capital's trade buildings
 *   spend surplus credits and the month's budget under their own rules below, outside the ladder's monthly pool. Their
 *   spending is booked on the rung (spent) for the Budget panel and idle months, and no rung's allowance changes with it.
 *   outsidePool (daily, from the endgame step) tells the ladder the rung wants nothing, so its pool share goes down to the
 *   next rung.
 * - One gate for all three buyers (0.3.0.0 fix, buyerOpen): they start something only from the money tier Surplus up with
 *   the Economy rung On. Recommend only starts none, as on the Wonders rung: there is no Economy proposal to approve. The
 *   summaries still show what would be bought.
 * - F6 terraforming: a surplus terraforming sequence that pays back within TERRAFORM_PAYBACK months may use the month's
 *   budget when surplus credits don't cover its next step, within the terraforming share of the budget and its savings,
 *   while buyerOpen and never over free cash.
 * - F8 the capital: a trade building that costs more than it earns is built only from the money tier Surplus up, with the
 *   Economy rung On, while such buildings' upkeep stays within COMMERCE_LOSS_SHARE of colony net. The capital's make-room
 *   swap waits for the tier Idle money.
 * - F9 hazard pay: a project that lowers the hazard of a colony that can still grow also saves the hazard pay its growth
 *   would need (vanilla: 1,000 a month per point of hazard penalty), counted for the months to its max size over the
 *   horizon. It doesn't depend on whether hazard pay is switched on there today.
 * - F11 shipping: a shipment whose fee is more than its cargo is worth waits, unless a plan needs it. For research, one
 *   tech's shipments are judged together (techShippingWorth), with one fee per route. 0.3.0.0 fix (C4 review 2): a route is
 *   the source colony's market id (your fleet's id for its cargo) and the destination (CAStores.routeKey), so a colony's
 *   Autopilot Storage, Storage and buildings are one trip and one fee.
 * - F13 storage logistics: a load goes when it carries 10 times its fee in value, or 30 days after its route's last load
 *   (loadWorthSending, for CALogistics).
 * With the hidden legacyPlanner switch every rule here gives 0.2.45.1's answer (tier() is -1).
 */
public final class CAEconomy {
    private CAEconomy() { }

    // ---------------- the numbers ----------------

    /** F5: below the money tier Rich, surplus research builds at most this many Research Facilities of its own. */
    static final int RESEARCH_CAP = 3;
    /** F5: from Idle money up, another facility's speed must beat this share of its upkeep (all of it below Idle money). */
    static final float RESEARCH_IDLE_UPKEEP = 0.5f;
    /** F6: surplus terraforming that pays back within this many months may use the month's budget. */
    static final float TERRAFORM_PAYBACK = 24f;
    /** F8: the upkeep of the capital's trade buildings that cost more than they earn stays within this share of colony net. */
    static final float COMMERCE_LOSS_SHARE = 0.05f;
    /** F11: a shipment joins a load on the same route sent this many days ago or less (one fee; the load waits for it). */
    static final float MERGE_DAYS = 7f;
    /** F9: vanilla's hazard pay numbers (settings.json), used when the game's own can't be read. */
    static final float PER_HAZARD = 0.05f, SIZE_MULT = 0.5f, PER_POINT = 1000f;

    /** Rung modes (CASettings.RUNG_MODE_NAMES). */
    static final int OFF = 0, ON = 1, RECOMMEND = 2;
    /** Where a surplus terraforming step's money comes from (terraformSource). */
    static final int NONE = 0, SURPLUS = 1, BUDGET = 2;

    // ---------------- live reads ----------------

    /** The money tier for these rules; -1 with legacyPlanner (0.2.45.1's rules), Tight when it can't be read. */
    static int tier(CASettings s) {
        if (s == null || CABudget.legacy(s)) return -1;
        try { return CARich.tier(); } catch (Throwable t) { return CARich.T0; }
    }

    /** The Economy rung's index in CARich.RUNGS, -1 when there is none. */
    static int economyRung() {
        for (int i = 0; i < CARich.RUNGS.length; i++) if ("Economy".equals(CARich.RUNGS[i])) return i;
        return -1;
    }

    /**
     * The Economy rung's mode (CASettings.rungMode): Off also when the "Surplus: what to buy" preset leaves the rung out
     * (Save). On when the ladder has no such rung.
     */
    static int economyMode(CASettings s) {
        int r = economyRung();
        if (s == null || r < 0) return ON;
        int mode;
        try { mode = s.rungMode(r); } catch (Throwable t) { return ON; }
        if (mode != OFF && !CARich.inPreset(s.richPreset, r)) return OFF;
        return mode;
    }

    /** Records credits spent from surplus credits on the Economy rung's purchases (CARich.spent: this month's line, idle months). */
    static void spent(float credits) {
        int r = economyRung();
        if (r >= 0 && credits > 0f) CARich.spent(r, credits);
    }

    // ---------------- 0.3.0.0 fix (C4 review 2): one gate for the Economy rung's buyers, outside the pool ----------------
    // The first review fix capped surplus credits at the rung's allowance (rungRoom, fitsRung) and had the buyers tell the
    // ladder what they wanted (want, publish). That went against DECISIONS 14b, the cap held per purchase only (the rung's
    // spending isn't in the pool, CARich.spentInPool), and the rung's want changed several times a day. Removed; the rung's
    // want is 0 every day (outsidePool).

    /**
     * Pure: an Economy buyer (surplus research, surplus terraforming, a capital trade building at a loss) may start
     * something: with legacyPlanner (tier -1) always, as 0.2.45.1; otherwise from the money tier Surplus up (the tier the rung
     * opens at, CARich.FROM_TIER) with the rung On. Off and Recommend only start none.
     */
    static boolean buyerOpen(int tier, int rungMode) {
        if (tier < 0) return true;
        return tier >= CARich.T1 && rungMode == ON;
    }

    /** Live: buyerOpen now. False with no settings. */
    static boolean buyerOpen(CASettings s) { return s != null && buyerOpen(tier(s), economyMode(s)); }

    /** Pure: why buyerOpen is false, for summaries and the log (null when it's true). preset: whether the preset funds the rung. */
    static String closedWhy(int tier, int savedMode, boolean preset) {
        if (tier < 0) return null;
        if (tier < CARich.T1) return "the money tier is " + CARich.name(tier) + " and the Economy rung opens at Surplus";
        if (savedMode == OFF) return "the money ladder's Economy rung is Off";
        if (!preset) return "the money ladder's preset leaves the Economy rung out";
        if (savedMode == RECOMMEND) return "the money ladder's Economy rung is Recommend only; set it On to start these";
        return savedMode == ON ? null : "the money ladder's Economy rung is not On";
    }

    /** Live: closedWhy now; null when buyers may start, and with no settings or no Economy rung. */
    static String closedWhy(CASettings s) {
        int r = economyRung();
        if (s == null || r < 0) return null;
        try { return closedWhy(tier(s), s.rungMode(r), CARich.inPreset(s.richPreset, r)); } catch (Throwable t) { return null; }
    }

    /** The Budget panel note while the buyers may start: where their money comes from. */
    static final String POOL_NOTE = "surplus research, surplus terraforming and capital trade buildings use surplus credits and the month's budget, outside the pool";

    /** Pure: the Economy rung's Budget panel note: POOL_NOTE, or why none starts (closedWhy). */
    static String poolNote(String closed) { return closed == null ? POOL_NOTE : "none starts: " + closed; }

    /**
     * Live, 0.3.0.0 fix (C4 review 2): tells the money ladder the Economy rung wants nothing from its pool this month
     * (CARich.want 0), since the rung's buyers spend outside it (DECISIONS 14b); the rung's share then goes down to the next
     * rung. Also sets the rung's Budget panel note (poolNote) while the rung is open, and clears it while it's closed (Off,
     * below its tier, left out of the preset: the whole share goes down then) or with legacyPlanner (the ladder buys
     * nothing). Daily from the endgame step, before the planner reads any allowance.
     */
    static void outsidePool(CASettings s) {
        int r = economyRung();
        if (s == null || r < 0) return;
        boolean open = tier(s) >= 0 && CARich.open(r);
        if (open) CARich.want(r, 0f);
        CARich.note(r, open ? poolNote(closedWhy(s)) : null);
    }

    // ---------------- F5: research ----------------

    /** Pure: the share of a Research Facility's upkeep its speed must beat at this tier (-1: legacyPlanner, all of it). */
    static float researchUpkeepWeight(int tier) { return tier >= CARich.T2 ? RESEARCH_IDLE_UPKEEP : 1f; }

    /** Pure: surplus research has built all it may now: RESEARCH_CAP of its own below Rich. No cap from Rich up, in the endgame, or with legacyPlanner (-1). */
    static boolean researchCapped(int builtBySurplus, int tier, boolean endgame) {
        return tier >= 0 && tier < CARich.T3 && !endgame && builtBySurplus >= RESEARCH_CAP;
    }

    /**
     * Pure: what one tech costs in research upkeep with these facilities, as a multiple of its cost with one facility. VoK
     * charges every facility (upkeepMult: their AI core multipliers summed, at least 1) and each past the first adds 10% speed.
     */
    static float costPerTech(float upkeepMult, int facilities) {
        return Math.max(1f, upkeepMult) / CASurplusResearch.speed(Math.max(1, facilities));
    }

    // ---------------- F6: surplus terraforming ----------------

    /** Pure: months a price takes to pay back at this gain a month (infinite for no gain). */
    static float paybackMonths(float total, float gain) {
        return gain > 0f ? Math.max(0f, total) / gain : Float.POSITIVE_INFINITY;
    }

    /**
     * Pure: budget surplus terraforming may use: the terraforming share of the month's budget and its savings, less what
     * terraforming already spent this month, never more than the budget left.
     */
    static float terraformBudgetRoom(float available, float limit, float carry, float share, float spentOnTerraforming) {
        float room = Math.max(0f, share) * (Math.max(0f, limit) + Math.max(0f, carry)) - Math.max(0f, spentOnTerraforming);
        return Math.max(0f, Math.min(Math.max(0f, available), room));
    }

    /**
     * Pure: where a surplus terraforming step's money comes from. SURPLUS when surplus credits cover the step (as in 0.2.45.1);
     * BUDGET when its whole sequence (seqTotal: prices, upkeep while it works and fees) pays back within TERRAFORM_PAYBACK
     * months at its gain and the terraforming budget room covers the step; NONE otherwise. budgetOk false: never BUDGET.
     * 0.3.0.0 fix (C4 review 2): surplus credits are outside the ladder's pool (DECISIONS 14b), so no rung cap applies here.
     */
    static int terraformSource(float step, float seqTotal, float gain, float surplus, float budgetRoom, boolean budgetOk) {
        if (step <= surplus) return SURPLUS;
        if (budgetOk && paybackMonths(seqTotal, gain) <= TERRAFORM_PAYBACK && step <= budgetRoom) return BUDGET;
        return NONE;
    }

    /** Pure, 0.3.0.0 fix: the budget room, never over free cash (NaN: free cash not known, no cap). */
    static float capToFreeCash(float room, float freeCash) {
        if (Float.isNaN(freeCash)) return Math.max(0f, room);
        return Math.max(0f, Math.min(room, freeCash));
    }

    /**
     * Live: the terraforming budget room now (0 while the budget is paused). 0.3.0.0 fix: never over free cash, since the
     * budget's own room only keeps the reserve and not production already ordered or the runway.
     */
    static float terraformBudgetRoom(CASettings s) {
        if (s == null || CABudget.paused(s)) return 0f;
        float room = terraformBudgetRoom(CABudget.available(s), CABudget.limit(s), s.budgetCarry, s.terraformShare, s.terraSpent);
        float free;
        try { free = CABudget.freeCash(s); } catch (Throwable t) { free = Float.NaN; }
        return capToFreeCash(room, free);
    }

    /**
     * Pure, 0.3.0.0 fix: the budget path is open: from the money tier Surplus up (the tier the Economy rung opens at,
     * CARich.FROM_TIER), the rung On and a terraforming share set. At Tight it was open, and surplus terraforming, which
     * spent nothing there in 0.2.45.1, could spend the money the ladder holds for production already ordered and the runway.
     */
    static boolean budgetPathOpen(int tier, int rungMode, float share) {
        return tier >= 0 && buyerOpen(tier, rungMode) && share > 0f;   // 0.3.0.0 fix (C4 review 2): the buyers' one gate
    }

    /** Live: surplus terraforming may use the budget at all (budgetPathOpen). Never with legacyPlanner (tier -1). */
    static boolean terraformBudgetOk(CASettings s) {
        return s != null && budgetPathOpen(tier(s), economyMode(s), s.terraformShare);
    }

    // ---------------- F8: the capital's trade buildings ----------------

    /**
     * Pure: a trade building that costs more than it earns may be built at the capital: from the money tier Surplus up, with
     * the Economy rung On, while such buildings' upkeep (lossUpkeep: the ones there; upkeep: this one) stays within
     * COMMERCE_LOSS_SHARE of colony net. legacyPlanner (-1): always, as 0.2.45.1.
     */
    static boolean lossCommerceAllowed(int tier, int rungMode, float lossUpkeep, float upkeep, float colonyNet) {
        if (tier < 0) return true;
        if (!buyerOpen(tier, rungMode)) return false;   // 0.3.0.0 fix (C4 review 2): the buyers' one gate
        return Math.max(0f, lossUpkeep) + Math.max(0f, upkeep) <= COMMERCE_LOSS_SHARE * Math.max(0f, colonyNet);
    }

    /** Pure: the capital may take down an industry to make room for a trade building: from Idle money up (legacyPlanner: always). */
    static boolean roomSwapAllowed(int tier) { return tier < 0 || tier >= CARich.T2; }

    // ---------------- F9: hazard pay a project saves ----------------

    /** Pure: vanilla's hazard penalty points for immigration at this hazard and size (CoreImmigrationPluginImpl.getImmigrationHazardPenalty). */
    static float hazardPoints(float hazard, int size, float perHazard, float sizeMult) {
        float p = Math.round((hazard - 1f) / Math.max(0.0001f, perHazard));
        if (p < 0f) p = 0f;
        return p * (1f + (size - 3f) * sizeMult);
    }

    /** Pure: hazard pay a month saved going from one hazard to another, with vanilla's numbers. */
    static float hazardPaySaved(float hazardNow, float hazardAfter, int size) {
        return hazardPaySaved(hazardNow, hazardAfter, size, PER_HAZARD, SIZE_MULT, PER_POINT);
    }

    /** Pure: hazard pay a month saved: the drop in penalty points times the credits a point costs. */
    static float hazardPaySaved(float hazardNow, float hazardAfter, int size, float perHazard, float sizeMult, float perPoint) {
        return (hazardPoints(hazardNow, size, perHazard, sizeMult) - hazardPoints(hazardAfter, size, perHazard, sizeMult)) * perPoint;
    }

    static float setting(String key, float fallback) {
        try { return Global.getSettings().getFloat(key); } catch (Throwable t) { return fallback; }
    }

    /**
     * Pure, 0.3.0.0 fix: a month's hazard pay saving as it counts over the horizon: it lasts only for the months the colony
     * still grows to its max size (monthsLeft; 0 or below: it's there, or it doesn't grow), so it's the saving times those
     * months over the horizon, at most the whole saving. The first form counted it as a gain every month for good.
     */
    static float hazardPayOverHorizon(float savedPerMonth, float monthsLeft, float horizon) {
        if (savedPerMonth == 0f || Float.isNaN(monthsLeft) || !(monthsLeft > 0f)) return 0f;
        return savedPerMonth * Math.min(1f, monthsLeft / Math.max(1f, horizon));
    }

    /** Live: months until the colony reaches its max size at its growth with hazard pay (CAPolicies.growth); -1 when it doesn't grow. */
    static float monthsToMaxSize(MarketAPI m) {
        CAPolicies.Growth g = CAPolicies.growth(m);
        float rate = g.base + g.points;
        if (!(rate > 0f)) return -1f;
        float need = CoreImmigrationPluginImpl.getWeightForMarketSizeStatic(Misc.getMaxMarketSize(m)) - m.getPopulation().getWeightValue();
        return Math.max(0f, need) / rate;
    }

    /**
     * Live, 0.3.0.0 fix: the share of a month's hazard pay saving that counts at this colony (hazardPayOverHorizon of 1): 0 at
     * its max size, at a Hypershunt and where growth is unsafe (CAPolicies never pays hazard pay there), with legacyPlanner,
     * or when it can't be read. It doesn't read whether hazard pay is on there today: that switch changes with the money
     * tier and the hazard pay budget (24 changes in the author's game), and a sequence worth doing only while it was on lost its
     * value, so surplus terraforming took down a structure built for it before its first project ran.
     */
    static float hazardPayScale(MarketAPI m) {
        try {
            if (m == null || CABudget.legacy(CAData.settings())) return 0f;
            if (m.getSize() >= Misc.getMaxMarketSize(m) || CAHypershunt.is(m) || CAMods.growthUnsafe(m)) return 0f;
            return hazardPayOverHorizon(1f, monthsToMaxSize(m), CAHorizon.months());
        } catch (Throwable t) { return 0f; }
    }

    /**
     * Live: hazard pay a month the colony no longer needs (negative: needs more) with these conditions instead of today's,
     * counted for the months it still grows (hazardPayScale). 0 with legacyPlanner, as 0.2.45.1 valued terraforming.
     */
    public static float hazardPaySaved(MarketAPI m, Collection<String> after) {
        return hazardPaySaved(m, null, after, Float.NaN);
    }

    /**
     * As above, for callers that value many end states at one colony: now, today's conditions (null: read here); scale, the
     * colony's hazardPayScale (NaN: worked out here).
     */
    static float hazardPaySaved(MarketAPI m, Set<String> now, Collection<String> after, float scale) {
        try {
            if (m == null || after == null) return 0f;
            float k = Float.isNaN(scale) ? hazardPayScale(m) : scale;
            if (!(k > 0f)) return 0f;
            if (now == null) {
                now = new HashSet<String>();
                for (MarketConditionAPI c : m.getConditions()) now.add(c.getId());
            }
            float delta = 0f;
            for (String c : after) if (!now.contains(c)) { Float h = CAConditions.HAZARD.get(c); if (h != null) delta += h; }
            for (String c : now) if (!after.contains(c)) { Float h = CAConditions.HAZARD.get(c); if (h != null) delta -= h; }
            if (delta == 0f) return 0f;
            float hz = m.getHazardValue();
            return k * hazardPaySaved(hz, hz + delta, m.getSize(), setting("immigrationPerHazard", PER_HAZARD),
                    setting("immigrationHazardMultExtraPerColonySizeAbove3", SIZE_MULT), setting("immigrationIncentiveCostPerPoint", PER_POINT));
        } catch (Throwable t) { return 0f; }
    }

    // ---------------- F11: shipping ----------------

    /** Pure: a shipment is worth its fee: the fee is no more than what the cargo is worth, or a plan needs it (blocked). */
    static boolean worthShipping(float fee, float value, boolean blocked) {
        return blocked || fee <= 0f || fee <= value;
    }

    /**
     * Pure, 0.3.0.0 fix: the fee each planned shipment for one tech pays: the first on a route pays its quote (0 when it joins
     * a load already on the way, CAStores.quote), later ones on the same route join it and pay nothing. routes: each
     * shipment's source key (CAStores.routeKey; all of one tech's shipments go to the same colony).
     */
    static float[] routeFees(String[] routes, float[] quotes) {
        float[] out = new float[quotes.length];
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < quotes.length; i++) {
            String r = routes != null && i < routes.length ? routes[i] : null;
            out[i] = r != null && !seen.add(r) ? 0f : Math.max(0f, quotes[i]);
        }
        return out;
    }

    /**
     * Pure, 0.3.0.0 fix: one tech's shipments go together or not at all: all their fees against the value of everything shipped
     * for it (worthShipping), or a plan needs the tech (blocked). Judged per shipment, a tech short of one databank at a far
     * colony shipped the near ones and paid their fees, then couldn't start, and another tech used what was moved.
     */
    static boolean techShippingWorth(float[] fees, float[] values, boolean blocked) {
        float f = 0f, v = 0f;
        if (fees != null) for (float x : fees) if (x > 0f) f += x;
        if (values != null) for (float x : values) if (x > 0f) v += x;
        return worthShipping(f, v, blocked);
    }

    // ---------------- F13: storage logistics loads (for CALogistics) ----------------

    /** F13: a Storage logistics load goes when it carries at least this many times its fee in value... */
    static final float LOAD_FEE_MULT = 10f;
    /** ...or this many days after the last load on its route. */
    static final float LOAD_DAYS = 30f;

    /** Pure: a Storage logistics load is worth its fee now: its cargo is worth LOAD_FEE_MULT times the fee, or LOAD_DAYS have passed on its route. */
    static boolean loadWorthSending(float value, float fee, float daysSinceLast) {
        return fee <= 0f || value >= LOAD_FEE_MULT * fee || daysSinceLast >= LOAD_DAYS;
    }

    /** Live: the base value of a cargo: its stacks at their base value a unit, and its mothballed ships at their base value. */
    static float cargoValue(com.fs.starfarer.api.campaign.CargoAPI c) {
        float v = 0f;
        if (c == null) return v;
        try {
            for (com.fs.starfarer.api.campaign.CargoStackAPI st : c.getStacksCopy()) v += Math.max(0f, st.getBaseValuePerUnit()) * Math.max(0f, st.getSize());
            if (c.getMothballedShips() != null)
                for (com.fs.starfarer.api.fleet.FleetMemberAPI fm : c.getMothballedShips().getMembersListCopy()) v += Math.max(0f, fm.getBaseValue());
        } catch (Throwable ignored) { }
        return v;
    }
}
