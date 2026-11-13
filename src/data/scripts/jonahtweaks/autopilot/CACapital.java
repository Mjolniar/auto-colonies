package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionProductionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * The capital (0.2.42.0): your home port. One colony, yours to pick or Autopilot's, where everything you own ends up and
 * where you come back to trade.
 *
 * Deliveries: the game's production gathering point follows the capital, so custom production (ships, weapons, wings)
 * is delivered there, and storage logistics (CALogistics) sends the ships, weapons, wings and blueprints sitting in your
 * other Storage tabs there once a week, at its usual fee and travel time. Move the gathering point yourself and the
 * capital follows you.
 *
 * Commerce: once your colonies have ramped up (no fast buildout running at the capital, and last month's net income past
 * the line you pick), the capital's plan gets its commerce steps in front of everything else: a Spaceport and Megaport,
 * Commerce, every installed mod's building that lets you buy or sell there (COMMERCE), and a Waystation. They take
 * industry slots like any plan step (reserved ahead of other industries), earn their plan rank even where they cost more
 * than they make, and with every industry slot taken the capital may take down its weakest ordinary industry for one
 * (one every 90 days; never a defense, fleet or ship-making building, a mod building, or another plan's special step;
 * 0.3.0.0: nor one whose swap would leave the capital under the stability floor, CAStability).
 * In a money emergency (net income below zero) the commerce steps wait.
 * 0.3.0.0 (WS-B4): the make-room swap is the endgame's math (CAEndgame.weakest, worthIt): what it takes down is valued by
 * CAValue.keep with the teardown loss, it leaves a year's mark (CATombstone), the capital's own plan steps stay, and a trade
 * building taken down here within the year isn't a step until the mark is gone.
 * 0.3.0.0 (fix, B4 review): outside the endgame the old building's money counts at weight 1 in that keep, as 0.2.45.1 counted
 * it (CATombstone.forRemoval with fullMoney): spending freely's quarter set a Refining earning 50,000 a month against
 * Commerce's unweighted plan bonus, so nearly any capital earner could be swapped out, one every 90 days.
 * 0.3.0.0 (WS-C4, research 03 F8): trade buildings that cost more than they earn are the money ladder's Economy rung. Once a
 * day (daily) each trade step still to build is valued as a build there (CAValue.add); one worth less than 0 is a step
 * only from the money tier Surplus up, with the Economy rung On, while the upkeep of the capital's trade buildings at a
 * loss stays within 5% of colony net (CAEconomy.lossCommerceAllowed). Commerce itself, buildings already there and ones
 * the engine can't value stay as in 0.2.45.1. The make-room swap waits for the money tier Idle money. Salvage Yards at Wind
 * scored +23,226 on a plan bonus of 60,000 while worth -30,768 a month.
 * 0.3.0.0 fix (C4 review 2): the valuation runs after the ramp-up check, so the planner's first pass after the build-up starts
 * has values, and until today's values for the capital are in (a new capital, a failed valuation) every trade step still
 * to build except Commerce waits (waits, valued).
 */
public class CACapital {
    /** capitalMode: 0 deliveries and commerce (default, also older saves), 1 deliveries only, 2 off. */
    public static final String[] MODE_NAMES = {"Deliveries and commerce", "Deliveries only", "Off"};
    /** capitalStart: when the commerce build-up starts. Index 0 is the recommended one, so older saves get it. */
    public static final String[] START_NAMES = {"Net income over 100,000", "Net income over 50,000", "Net income over 250,000", "Right away"};
    static final float[] START_NET = {100000f, 50000f, 250000f, -Float.MAX_VALUE};
    /** Days between two industries the capital takes down to make room for commerce. */
    static final float ROOM_DAYS = CATuning.CAPITAL_ROOM_DAYS;   // 0.3.0.0 (A3): in CATuning

    /**
     * Buildings that make the capital a place to trade, in the order they're wanted, from a scan of what each one does on a
     * player colony (0.2.42.0). A step whose building the game doesn't have is dropped (CAPlan.exists), and one that can't be
     * reached here (research, a blueprint, one per system) stops counting after 60 days (CAPlan.track). Column 4 "only":
     * nothing else scores it, so Autopilot builds it at the capital and nowhere else. Left out: Military Base and Free Port
     * (no market on a player colony), Interstellar Imperium's Bazaar (can't be built), Industrial Evolution's Animal
     * Center (pets) and the Senate's Open Market edict (Commerce already gives one).
     */
    static final String[][] COMMERCE = {
            // id, label, what you can do there, "only" when nothing else scores it
            {"commerce", "Commerce", "an Open Market and +25% income", ""},   // vanilla TradeCenter, IndEvo's too
            {"aotd_trade_outpost", "Trade Outpost", "a no-tariff market, the first 7 days of each month", ""},
            {"yunru_bazaar", "Bazaar", "an Open Market and a black market", "only"},
            {"IndEvo_ReqCenter", "Requisitions Centre", "weapons from factions that like you", ""},
            {"roider_union_hq", "Union HQ", "the Roiders Market", ""},
            {"IndEvo_ScrapYard", "Salvage Yards", "ships built from scrap", ""},
            {"aotd_underworld", "Underworld", "a black market", ""},
    };
    static final Map<String, String> WHAT = new HashMap<String, String>();
    static final Set<String> MOD_IDS = new HashSet<String>();
    static {
        for (String[] c : COMMERCE) { WHAT.put(c[0], c[2]); if ("only".equals(c[3])) MOD_IDS.add(c[0]); }
    }

    static String pickedId;
    static float pickedAt = -99f;
    /**
     * 0.2.42.2: true while the capital (yours or Autopilot's pick) is still a market but not yours (captured): a stand-in
     * colony takes production deliveries, nothing is gathered anywhere, and the capital is the old one again the day it's
     * yours. In 0.2.42.0 a captured Wind made Ming Adi the capital for good, and once Wind was retaken its whole Storage (853
     * weapons, 39 wings) was shipped to Ming Adi.
     */
    static boolean standIn = false;

    public static void reset() { pickedId = null; pickedAt = -99f; standIn = false; resetWorth(); }

    // ---------------- 0.3.0.0 (C4, research 03 F8): trade buildings at a loss ----------------

    /** Today's worth a month of each trade step still to build at the capital (CAValue.add, at the colony's weights). Session only. */
    static final Map<String, Float> stepWorth = new HashMap<String, Float>();
    /** Each one's upkeep a month, as the engine predicts it. */
    static final Map<String, Float> stepUpkeep = new HashMap<String, Float>();
    /** Upkeep a month of the trade buildings at the capital that cost more than they earn. */
    static float lossUpkeep = 0f;
    /** The colony and game day those were worked out for (null / NaN: not yet this session). */
    static String worthAt;
    static float worthDay = Float.NaN;

    static void resetWorth() { stepWorth.clear(); stepUpkeep.clear(); lossUpkeep = 0f; worthAt = null; worthDay = Float.NaN; }

    static MarketAPI market(String id) { return id == null ? null : CAAuto.market(id); }

    /** A colony that can be the capital: yours, a real colony (not a Nexerelin outpost or a Coronal Hypershunt), with a Storage tab. */
    static boolean valid(MarketAPI m) {
        try {
            return m != null && m.isPlayerOwned() && !CAEngine.isOutpost(m) && !CAHypershunt.is(m) && m.getPrimaryEntity() != null
                    && Misc.getStorageCargo(m) != null;
        } catch (Throwable t) { return false; }
    }

    public static boolean on(CASettings s) { return s.capitalMode != 2; }

    /** The capital, or null (no colony yet, or the feature is off). Worked out once a day. */
    public static MarketAPI capital() {
        CASettings s = CAData.settings();
        if (!on(s)) return null;
        float now = CADay.now();
        if (now - pickedAt >= 1f || now < pickedAt) compute(s, now);
        return market(pickedId);
    }

    public static boolean is(MarketAPI m) {
        if (m == null) return false;
        MarketAPI c = capital();
        return c != null && c.getId().equals(m.getId());
    }

    static void compute(CASettings s, float now) {
        pickedAt = now;
        standIn = false;
        MarketAPI mine = market(s.capitalId);
        if (valid(mine)) { pickedId = mine.getId(); return; }
        MarketAPI last = market(s.capitalAuto);
        if (valid(last)) { pickedId = last.getId(); return; }   // Autopilot's pick stays put: moving home is your call
        // the capital still exists but isn't yours (captured): a stand-in for deliveries only, and the capital stays put
        boolean lost = (mine != null && mine.getPrimaryEntity() != null) || (s.capitalId == null && last != null && last.getPrimaryEntity() != null);
        MarketAPI best = null;
        float bestScore = -Float.MAX_VALUE;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!valid(m)) continue;
            float sc = score(m);
            if (sc > bestScore) { bestScore = sc; best = m; }
        }
        pickedId = best == null ? null : best.getId();
        if (lost) { standIn = best != null; return; }
        if (best != null) s.capitalAuto = best.getId();   // gone for good (decivilized, abandoned) or never picked: a new capital
    }

    /** The capital that's lost for now (captured), or null. */
    static MarketAPI lostCapital(CASettings s) {
        if (!standIn) return null;
        MarketAPI m = market(s.capitalId);
        return m != null ? m : market(s.capitalAuto);
    }

    /** Where your things already are counts most: the production gathering point, then what's in Storage; then size and reach. */
    static float score(MarketAPI m) {
        float sc = m.getSize() * 10f + Math.max(0f, m.getAccessibilityMod().computeEffective(0f)) * 20f
                + Math.max(0, CABuild.freeSlots(m)) * 5f;
        try {
            MarketAPI g = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint();
            if (g != null && g.getId().equals(m.getId())) sc += 1000f;
        } catch (Throwable ignored) { }
        try {
            CargoAPI st = Misc.getStorageCargo(m);
            if (st != null) {
                sc += Math.min(200f, st.getStacksCopy().size() * 2f);
                if (st.getMothballedShips() != null) sc += Math.min(300f, st.getMothballedShips().getNumMembers() * 10f);
            }
        } catch (Throwable ignored) { }
        if (CAData.get().colony(m.getId()).mode == CAData.Mode.AUTO) sc += 20f;
        return sc;
    }

    // ---------------- daily ----------------

    public static void daily(CAData d, CASettings s) {
        MarketAPI cap = capital();
        if (cap == null) return;
        MarketAPI lost = lostCapital(s);
        if (lost != null) {
            if (CALog.once("capital:lost:" + lost.getId()))
                CALog.did(lost.getName() + ": Capital: lost. Production deliveries go to " + cap.getName() + " until it is retaken. "
                        + "Storage gathering paused until then.", true);
            deliveries(s, cap);
            return;
        }
        if (CALog.once("capital:" + cap.getId()))
            CALog.did(cap.getName() + ": Capital: set" + (cap.getId().equals(s.capitalId) ? " (your pick)" : "") + ". Production deliveries come here."
                    + (gathers(s) ? " Your other Storage gathers here weekly." : "")
                    + (s.capitalMode == 0 ? " Builds into a trade hub once your colonies have ramped up." : ""));
        deliveries(s, cap);
        cap = capital();   // the gathering point may have moved it
        if (cap == null) return;
        // the commerce build-up starts once the colonies have ramped up
        boolean startsNow = false;
        float line = START_NET[Math.max(0, Math.min(START_NET.length - 1, s.capitalStart))];
        if (s.capitalMode == 0 && !s.capitalStarted) {
            CAData.Colony c = d.colony(cap.getId());
            boolean ramped = !c.buildout && (line == -Float.MAX_VALUE || (s.lastNetKnown && CABudget.netIncome(s) >= line));
            if (ramped) { s.capitalStarted = true; startsNow = true; }
        }
        // 0.3.0.0 (C4, research 03 F8): what each trade step still to build is worth today, before the planner reads the plan.
        // 0.3.0.0 fix (C4 review 2): after the ramp-up check, so the planner's first pass after the build-up starts has values
        // (until today's values are in, waits() holds every trade step but Commerce)
        if (commerce(s)) {
            try { valueSteps(cap, d.colony(cap.getId()), s); }
            catch (Throwable t) { resetWorth(); CADiagnostics.LOG.warn(CADiagnostics.TAG + "capital trade building valuation failed at " + cap.getName(), t); }
        }
        if (!startsNow) return;
        List<String> left = stepLabels(cap);
        CALog.did(cap.getName() + ": Capital: commerce build-up starts" + (line == -Float.MAX_VALUE ? " now" : ", net income "
                + Misc.getDGSCredits(CABudget.netIncome(s)) + " a month") + ". " + (left.isEmpty() ? "Every trade building is already here."
                : "First: " + CAContracts.join(left) + "."), true);
    }

    /** The gathering point follows the capital; if you moved it to another colony yourself, the capital follows you instead. */
    static void deliveries(CASettings s, MarketAPI cap) {
        FactionProductionAPI prod;
        try { prod = Global.getSector().getPlayerFaction().getProduction(); } catch (Throwable t) { return; }
        if (prod == null) return;
        MarketAPI g = prod.getGatheringPoint();
        if (g != null && s.capitalGatherSet != null && !g.getId().equals(s.capitalGatherSet) && !g.getId().equals(cap.getId()) && valid(g)) {
            s.capitalId = g.getId();
            s.capitalGatherSet = g.getId();
            pickedAt = -99f;
            CALog.did(g.getName() + ": Capital: moved here with your production deliveries.", true);
            return;
        }
        if (g != null && g.getId().equals(cap.getId())) { s.capitalGatherSet = cap.getId(); return; }
        try {
            prod.setGatheringPoint(cap);
            s.capitalGatherSet = cap.getId();
            CALog.did(cap.getName() + ": Capital: production deliveries now come here" + (g != null ? " (were going to " + g.getName() + ")" : "") + ".");
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "couldn't move the production gathering point to " + cap.getName(), t); }
    }

    /** Whether storage logistics runs for the capital even with Storage logistics itself off: never while a stand-in has it. */
    public static boolean gathers(CASettings s) { return on(s) && !s.capitalNoGather && capital() != null && !standIn; }

    /** Where the capital's Storage gathering sends things: the capital, or null (off, none, or a stand-in). */
    public static MarketAPI gatherTarget(CASettings s) { return gathers(s) ? capital() : null; }

    /**
     * Below this stability the capital's trade buildings wait: most of them cost stability, and at 0 a colony can decivilize.
     * 0.3.0.0: CAStability's lowest floor (3, as before); each one that costs stability is held to the full floor when it's picked.
     */
    static final float COMMERCE_MIN_STABILITY = CAStability.MIN_FLOOR;

    /** Whether the commerce build-up is running now: started, and not in a money emergency. */
    public static boolean commerce(CASettings s) {
        return s.capitalMode == 0 && s.capitalStarted && !standIn && !(s.lastNetKnown && CABudget.netIncome(s) < 0f);
    }

    // ---------------- the plan ----------------

    static List<String[]> wanted() {
        List<String[]> out = new ArrayList<String[]>();
        out.add(new String[]{"spaceport", "Spaceport", "traders can dock"});
        out.add(new String[]{"megaport", "Megaport", "+30% accessibility"});
        out.addAll(Arrays.asList(COMMERCE));
        out.add(new String[]{"waystation", "Waystation", "fuel and supplies stockpiled at home"});
        return out;
    }

    /** The capital's commerce steps, put in front of its own plan (CAPlan.build) while the build-up runs. */
    public static List<CAPlan.Step> stepsFor(MarketAPI m) {
        List<CAPlan.Step> out = new ArrayList<CAPlan.Step>();
        if (m == null || !commerce(CAData.settings()) || !is(m)) return out;
        if (m.getStabilityValue() < COMMERCE_MIN_STABILITY) {   // 0.2.42.2: Commerce and the Bazaars cost stability
            if (CALog.once("capital:lowstab:" + m.getId()))
                CALog.think(m.getName() + ": Capital: trade buildings wait while stability is under " + Math.round(COMMERCE_MIN_STABILITY) + ".");
            return out;
        }
        CALog.forget("capital:lowstab:" + m.getId());
        for (String[] w : wanted()) {
            if (waits(m, w[0])) continue;   // 0.3.0.0 (C4, F8): costs more than it earns, and the money ladder doesn't allow it yet
            if (CAOverLimit.shedRecently(m, w[0])) continue;   // taken down at 0 stability this year: not built again yet
            // 0.3.0.0 (B4): taken down here for any reason within the year (CATombstone): no plan rank or slot held for it meanwhile
            if (CATombstone.blocked(m.getId(), w[0]) > 0f) continue;
            out.add(new CAPlan.Step(CAPlan.Kind.BUILD, w[0], w[1] + " (capital)"));
        }
        return out;
    }

    static List<String> stepLabels(MarketAPI m) {
        List<String> out = new ArrayList<String>();
        for (String[] w : wanted()) if (CAPlan.spec(w[0]) != null && !CAPlan.hasOrBetter(m, w[0])) out.add(w[1]);
        return out;
    }

    /** A commerce step at the capital, or what leads to one (Ashes of the Domain's Trade Outpost before Commerce). */
    public static boolean isStep(MarketAPI m, String id) {
        if (id == null || m == null || !commerce(CAData.settings()) || !is(m)) return false;
        for (String[] w : wanted()) {
            if (waits(m, w[0])) continue;   // 0.3.0.0 (C4, F8): not a step, so no plan rank, while it waits for the money ladder
            if (w[0].equals(id) || CAPlan.leadsTo(id, w[0])) return true;
        }
        return false;
    }

    /**
     * 0.3.0.0 (C4, research 03 F8): today's worth of each trade step still to build at the capital (as a build there, at the
     * colony's goal), and the upkeep of the trade buildings there that cost more than they earn (CAValue.keep below 0). A
     * step that waits for the money ladder gets one log line. Commerce, and buildings the engine can't value, aren't valued:
     * they stay steps as in 0.2.45.1. With legacyPlanner nothing is valued.
     */
    static void valueSteps(MarketAPI cap, CAData.Colony col, CASettings s) {
        float day = CADay.now();
        if (valued(worthAt, worthDay, cap.getId(), day)) return;
        stepWorth.clear(); stepUpkeep.clear(); lossUpkeep = 0f;
        worthAt = cap.getId(); worthDay = day;
        if (CABudget.legacy(s)) return;
        CAData.Profile prof = col == null || col.profile == null ? CAData.Profile.BALANCED : col.profile;
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        for (String[] w : wanted()) {
            String id = w[0];
            if ("commerce".equals(id) || CAPlan.spec(id) == null) continue;   // Commerce earns (+25% income): as before
            Industry ind = cap.getIndustry(id);
            if (ind != null) {
                if (ind.isBuilding() && !ind.isUpgrading()) continue;   // under construction: counted once it runs
                CAValue.Kept k = CAValue.keepDetail(cap, ind, prof, deficit);
                if (k != null && k.value < 0f) lossUpkeep += Math.max(0f, ind.getUpkeep().getModifiedValue());
                continue;
            }
            if (CAPlan.hasOrBetter(cap, id)) continue;
            CAEngine.Pick q = pickFor(cap, id, deficit);
            if (q == null) continue;
            stepWorth.put(id, CAValue.add(cap, q, prof));
            stepUpkeep.put(id, Math.max(0f, q.upkeep));
        }
        int tier = CAEconomy.tier(s);
        for (Map.Entry<String, Float> e : stepWorth.entrySet()) {
            String key = "capital:loss:" + cap.getId() + ":" + e.getKey();
            if (lossAllowed(e.getKey(), s, tier)) { CALog.forget(key); continue; }
            if (CALog.once(key)) CALog.think(cap.getName() + ": Capital: " + CAWorldPlanner.industryName(e.getKey()) + " waits (worth "
                    + CAEngine.valueText(e.getValue()) + "): " + lossWhy(s, tier, e.getKey()) + ".");
        }
    }

    /** 0.3.0.0 (C4): why a trade building at a loss waits, for the log. */
    static String lossWhy(CASettings s, int tier, String id) {
        if (tier < CARich.T1) return "loss-making trade buildings start at money tier Surplus, now " + CARich.name(tier);
        String closed = CAEconomy.closedWhy(s);   // 0.3.0.0 fix (C4 review 2): the Economy buyers' one gate and its wording
        if (closed != null) return closed;
        Float up = stepUpkeep.get(id);
        return "loss-making trade buildings: " + Misc.getDGSCredits(lossUpkeep + (up == null ? 0f : up)) + " a month upkeep, over the "
                + Misc.getDGSCredits(CAEconomy.COMMERCE_LOSS_SHARE * Math.max(0f, CABudget.netIncome(s))) + " limit (5 percent of colony net)";
    }

    /** 0.3.0.0 (C4): a trade step may be built: it earns, wasn't valued today, or the money ladder allows one at a loss now. */
    static boolean lossAllowed(String id, CASettings s, int tier) {
        Float w = stepWorth.get(id);
        if (w == null || !(w < 0f)) return true;
        Float up = stepUpkeep.get(id);
        return CAEconomy.lossCommerceAllowed(tier, CAEconomy.economyMode(s), lossUpkeep, up == null ? 0f : up, CABudget.netIncome(s));
    }

    /**
     * 0.3.0.0 (C4, F8): a trade step at this capital that waits today. 0.3.0.0 fix (C4 review 2): it also waits while today's
     * values for this capital aren't in (waitsUnvalued): the first form let every trade step through then, ones worth less
     * than 0 included, on the day the build-up started, the day the capital changed and after a failed valuation.
     */
    static boolean waits(MarketAPI m, String id) {
        if (m == null || id == null) return false;
        CASettings s = CAData.settings();
        if (CABudget.legacy(s)) return false;   // as 0.2.45.1
        if (!valued(worthAt, worthDay, m.getId(), CADay.now()))
            return waitsUnvalued(id, m.getIndustry(id) != null || CAPlan.hasOrBetter(m, id));
        return waitsForLoss(m, id, s);
    }

    /** 0.3.0.0 (C4, F8): a trade step valued today at less than 0 that the money ladder doesn't allow now. */
    static boolean waitsForLoss(MarketAPI m, String id, CASettings s) {
        if (m == null || id == null || !m.getId().equals(worthAt)) return false;
        Float w = stepWorth.get(id);
        if (w == null || !(w < 0f)) return false;
        return !lossAllowed(id, s, CAEconomy.tier(s));
    }

    /**
     * Pure, 0.3.0.0 fix (C4 review 2): the values were worked out for this capital within the last day (valueSteps keeps them
     * that long). worthAt null or worthDay NaN: not yet this session, or the last valuation failed.
     */
    static boolean valued(String worthAt, float worthDay, String capId, float now) {
        return capId != null && capId.equals(worthAt) && !Float.isNaN(worthDay) && now >= worthDay && now - worthDay < 1f;
    }

    /**
     * Pure, 0.3.0.0 fix (C4 review 2): whether a trade step waits while it isn't valued today: every one still to build but
     * Commerce (which earns; never valued, as in 0.2.45.1). One already there (built, or being built) isn't a step to wait on,
     * and the capital's make-room swap still leaves it alone (CAEndgame.weakest reads isStep).
     */
    static boolean waitsUnvalued(String id, boolean there) {
        return id != null && !"commerce".equals(id) && !there;
    }

    /**
     * 0.3.0.0 (C4): a trade building as a new build here, valued the way the engine reads a pick: vanilla formulas and
     * Autopilot's estimates on the market model (CAEngine.effectsAt), other estimated buildings by their own rules, then
     * the engine's upkeep and trust. Null for one the engine can't value.
     */
    static CAEngine.Pick pickFor(MarketAPI m, String id, Map<String, Integer> deficit) {
        IndustrySpecAPI spec = CAPlan.spec(id);
        if (spec == null) return null;
        Industry inst = spec.getNewPluginInstance(m);
        String from = CAPlan.upgradedFrom(id, null);
        Industry existing = from == null ? null : m.getIndustry(from);
        CAEngine.Pick q;
        if (CAEngine.isSupportedVanilla(id) || CAEstimates.has(id)) q = CAEngine.effectsAt(m, id, inst, existing, deficit);
        else if (CAEngine.estimated(id)) {
            q = new CAEngine.Pick();
            q.id = id;
            if (existing != null) { q.upgrade = true; q.existing = existing; }
            q.recording = true;
            boolean ok;
            try { ok = CAEngine.estimate(m, id, q, deficit); } finally { q.recording = false; }
            if (!ok) return null;
        } else return null;
        if (q == null) return null;
        CAEngine.upkeep(m, spec, existing == null ? null : existing.getSpec(), q);
        CAEngine.trust(spec, q, false);
        return q;
    }

    /** 0.3.0.0 (C4): the trade steps at this capital that wait for the money ladder today (worth less than 0), by name. */
    static List<String> waiting(MarketAPI m) {
        List<String> out = new ArrayList<String>();
        CASettings s = CAData.settings();
        if (CABudget.legacy(s)) return out;
        for (String[] w : wanted()) if (waitsForLoss(m, w[0], s)) out.add(w[1]);   // 0.3.0.0 fix (C4 review 2): not the ones still to be valued
        return out;
    }

    // ---------------- mod buildings Autopilot only knows as trade buildings ----------------

    /** A mod building in COMMERCE that no other part of the engine scores. */
    public static boolean has(String id) { return MOD_IDS.contains(id) && !CAEstimates.has(id) && !CAMods.has(id); }

    /** Its effect: a place to trade at the capital, worth its plan rank there; elsewhere Autopilot doesn't build it. */
    static boolean apply(MarketAPI m, String id, CAEngine.Pick p, Map<String, Integer> deficit) {
        if (!is(m) || !commerce(CAData.settings())) return false;
        String what = WHAT.get(id);
        p.lines.add("Capital trade building" + (what != null ? ": " + what : "") + "; scored as a capital plan step");
        return true;
    }

    // ---------------- making room ----------------

    /**
     * At the capital with every industry slot taken: its weakest ordinary industry, if the commerce pick beats it. 0.3.0.0 (WS-B4):
     * the endgame's weakest() and rule (CAEndgame.weakest with capital set, CAEndgame.worthIt): valued by CAValue.keep plus the
     * teardown loss, NPV(new) - keep x H - loss over the margin; never its own trade buildings, defenses, fleets or what builds
     * your ships, one of its own plan's steps (research 02 section 11: the step would only open again), one built under another
     * valuation within the year, one whose swap would leave the capital under the stability floor (CAStability; Commerce
     * alone is -3), nor a trade building taken down here within the year.
     */
    static CAEndgame.Weakest replacement(MarketAPI m, CAData.Colony col, CAEngine.Pick best, CAData.Profile profile) {
        CASettings s = CAData.settings();
        if (best == null || s.capitalNoRoom || !isStep(m, best.id) || col.mode != CAData.Mode.AUTO) return null;
        if (!CAEconomy.roomSwapAllowed(CAEconomy.tier(s))) return null;   // 0.3.0.0 (C4, research 03 F8): from the money tier Idle money up
        Long last = CAData.get().swapAt().get(m.getId() + ":replaced");
        if (last != null && Global.getSector().getClock().getElapsedDaysSince(last) < ROOM_DAYS) return null;
        if (CAEndgame.rebuildBlocked(m, best.id)) return null;   // 0.3.0.0 (B4): taken down here within the year
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        CAEndgame.Weakest out = CAEndgame.weakest(m, col, plan, profile, best, true);
        if (out == null || !CAEndgame.worthIt(best, out)) return null;
        return out;
    }

    // ---------------- panels ----------------

    static final String SET = "ca:capital:set", AUTO = "ca:capital:auto";

    static int doneSteps(MarketAPI m) {
        int n = 0;
        for (String[] w : wanted()) if (CAPlan.spec(w[0]) != null && CAPlan.hasOrBetter(m, w[0])) n++;
        return n;
    }

    static int allSteps() {
        int n = 0;
        for (String[] w : wanted()) if (CAPlan.spec(w[0]) != null) n++;
        return n;
    }

    static String status(CASettings s, MarketAPI cap) {
        if (s.capitalMode == 1) return "deliveries only";
        if (commerce(s)) return "commerce " + doneSteps(cap) + " of " + allSteps() + " built";
        if (s.capitalStarted) return "commerce waits (net income negative)";
        return "commerce starts at " + START_NAMES[Math.max(0, Math.min(START_NAMES.length - 1, s.capitalStart))].toLowerCase(Locale.ROOT);
    }

    /** The empire panel's line. */
    public static void renderEmpire(TooltipMakerAPI t, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        if (!on(s)) { t.addPara("Capital: off (Settings, Capital).", gray, 4f); return; }
        MarketAPI cap = capital();
        if (cap == null) { t.addPara("Capital: none yet.", gray, 4f); return; }
        t.addPara("Capital: %s" + (cap.getId().equals(s.capitalId) ? " (your pick)" : "") + ", deliveries"
                + (gathers(s) ? " and Storage" : "") + "; " + CALog.esc(status(s, cap)) + ".", 4f, hl, cap.getName());
    }

    /** The colony panel's section. */
    public static void renderColony(TooltipMakerAPI t, MarketAPI m, CASettings s, float pad) {
        if (!on(s) || !valid(m)) return;
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        CATheme.heading(t, "Capital", pad);
        MarketAPI cap = capital();
        boolean here = cap != null && cap.getId().equals(m.getId()), mine = m.getId().equals(s.capitalId);
        if (here) {
            t.addPara("This is your capital" + (mine ? " (your pick)" : " (Autopilot's pick)") + ": deliveries"
                    + (gathers(s) ? " and Storage gather here" : " come here") + ". " + CALog.esc(Character.toUpperCase(status(s, cap).charAt(0)) + status(s, cap).substring(1)) + ".", 6f);
            if (s.capitalMode == 0) {
                List<String> left = stepLabels(m);
                if (!left.isEmpty()) t.addPara("Still to build here: " + CALog.esc(CAContracts.join(left)) + ".", gray, 2f);
                // 0.3.0.0 (C4, research 03 F8)
                List<String> held = waiting(m);
                if (!held.isEmpty()) t.addPara("Waiting for the money ladder: " + CALog.esc(CAContracts.join(held)) + ".", gray, 2f);
            }
        } else {
            t.addPara("Your capital is %s.", 6f, hl,
                    cap == null ? "not set" : cap.getName());
        }
        if (mine) t.addButton("Let Autopilot choose", AUTO, base, dark, Alignment.MID, CutStyle.TL_BR, 220f, 24f, 6f);
        else if (!here) t.addButton("Make this the capital", SET, base, dark, Alignment.MID, CutStyle.TL_BR, 220f, 24f, 6f);
    }

    /** A colony panel button; returns a message, or null if the id isn't one of these. */
    public static String handle(MarketAPI m, Object id) {
        CASettings s = CAData.settings();
        if (SET.equals(id)) { s.capitalId = m.getId(); s.capitalAuto = m.getId(); pickedAt = -99f; return m.getName() + " is the capital"; }
        if (AUTO.equals(id)) { s.capitalId = null; pickedAt = -99f; return "Autopilot keeps " + m.getName() + " as the capital until you pick another"; }
        return null;
    }

    /** Settings: the next capital choice, Autopilot's pick (null) then each colony. */
    public static String next(String cur) {
        List<String> order = new ArrayList<String>();
        order.add(null);
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (valid(m)) order.add(m.getId());
        int i = order.indexOf(cur);
        return order.get((i + 1) % order.size());
    }

    public static String name(CASettings s) {
        MarketAPI m = market(s.capitalId);
        if (m != null) return m.getName();
        MarketAPI c = on(s) ? capital() : null;
        return "Autopilot picks" + (c != null ? " (" + c.getName() + ")" : "");
    }
}
