package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Endgame economy (0.2.36.0). Once money is no longer the problem (spending freely, 10M or more over the surplus line, and last
 * month's net income at least the setting's level, 1M by default), what a building is worth changes at every colony that
 * spends the surplus:
 * - a credit counts MONEY (5%) instead of a quarter: income hardly matters any more;
 * - defense, fleets, ship quality, stability, access and growth count at least ENDGAME_WEIGHTS (Security and War production's
 *   highs, fleets and quality higher still), or the colony goal's own if higher;
 * - every commodity a building makes is valued as a production chain (chainValue): a good none of your colonies makes is
 *   worth NEW_CHAIN plus its units, units that cover your own colonies' shortfall count 1.5x, the rest count by how much of
 *   the sector's production isn't yours yet (toward a monopoly), and a second colony making a good only one makes adds
 *   SECOND_SOURCE, so production spreads over your worlds. War goods count 1.5x, Ashes of the Domain's advanced goods 2x;
 * - the plan only adds priority for what can't be had another way: Terraforming Made Easy's structures and special-world
 *   buildings, special project producers, maxed stations and defenses, the Bifrost Control Center. A money plan's Commerce
 *   or Light Industry is valued like anything else;
 * - with no free industry slot, an Auto colony replaces its weakest industry when the best one it has no slot for beats
 *   it by REPLACE_MARGIN (one a day across the empire, one a month per colony; never a plan or project building, a military
 *   one (fleets, ship quality, defense), a story point improvement, one you locked, one it built in the last year, nor a
 *   good's last producer while you'd be short; 0.3.0.0: nor one whose swap would leave the colony under the stability floor,
 *   CAStability).
 *   Its AI core and item go to Autopilot Storage, to be placed again;
 * - Research Facilities go up while any research is left, whatever the money says; colony items and AI cores are placed
 *   with these weights.
 * What it may cost in income: last month's net income may fall to KEEP (a third) of what it was when the endgame started
 * (3M a month keeps 1M); each month's changes that earn less draw on that room, and wait once it's used. It ends when the
 * surplus falls under 5M or net income goes below zero, and starts again while spending freely at the income it keeps.
 * Between 5M and 10M it stays on but, like spending freely, only the focus world spends.
 *
 * 0.3.0.0 (WS-B4, research 02 "Construction 0.3.0"): replacing is the build math backwards. The weakest industry is valued by
 * CAValue.keep, the same numbers a build is scored with (what the colony loses a month without it), and a swap happens when
 * NPV(new) - keep x H - teardown loss > REPLACE_MARGIN x H (CATombstone.worthReplacing; H the payback horizon, the loss the
 * part of what Autopilot paid for the old one it hasn't paid back yet). Nothing built under another valuation (normally,
 * spending freely) is swapped out within the year, nothing taken down here within the year is the replacement (CATombstone's
 * marks), and every swap leaves a mark. The capital's make-room swap (CACapital) uses the same weakest() and rule.
 * 0.3.0.0 (fix, B4 review): the keep is read as a remover reads it (CATombstone.forRemoval): the build side's charge for inputs
 * your colonies lack doesn't count against a building that's there, and the shortage it would leave is read against today's
 * supply, so a producer isn't swapped out the day a replacement elsewhere was started.
 * 0.3.0.0 (WS-C2/C4, research 03 section 5.2): the endgame is the money tier Endgame (CARich T4: Rich, and colony net at the
 * setting's line). It starts there, or once it has run at the income it keeps while the tier is Rich; it stays on while
 * the tier is Rich or higher (Rich holds down to 60% of its line) and colony net isn't below zero (decideTier). With the
 * hidden legacyPlanner switch it's 0.2.45.1's rule on the surplus (decide).
 * 0.3.0.0 (WS-C4, research 03 F7): a second colony making a good only one of yours makes earns SECOND_SOURCE only when that
 * one producer is under threat (siege, crisis target, high threat) or the good is a war good; otherwise the second source
 * duplicates a producer for nothing. The units counted are the ones a build adds over the colony's other sources
 * (CAValue.chainUnits, B1), so a duplicate gets no chain value.
 */
public class CAEndgame {
    /** Net income a month needed to start, by setting (the last one is Off). */
    public static final float[] ENTER_NET = {1000000f, 2000000f, 500000f};
    public static final String[] NAMES = {"From 1,000,000/month", "From 2,000,000/month", "From 500,000/month", "Off"};
    // 0.3.0.0 (A3): the numbers live in CATuning
    static final float KEEP = CATuning.ENDGAME_KEEP;
    static final float MONEY = CATuning.ENDGAME_MONEY;
    //                                          credits stab   access  defense fleet   quality growth
    static final float[] ENDGAME_WEIGHTS = {MONEY, 4000f, 10000f, 15000f, 40000f, 40000f, 2000f};
    static final float NEW_CHAIN = CATuning.ENDGAME_NEW_CHAIN, SECOND_SOURCE = CATuning.ENDGAME_SECOND_SOURCE,
            PER_UNIT = CATuning.ENDGAME_PER_UNIT, REPLACE_MARGIN = CATuning.ENDGAME_REPLACE_MARGIN;
    static final float REPLACE_COLONY_DAYS = CATuning.ENDGAME_REPLACE_COLONY_DAYS, MEMORY_DAYS = CATuning.ENDGAME_MEMORY_DAYS;
    static final Set<String> WAR_GOODS = new HashSet<String>(Arrays.asList("ships", "hand_weapons", "supplies", "fuel", "heavy_machinery",
            "metals", "rare_metals", "marines"));
    static float lastReplaceDay = -99f;
    static String summary;

    // ---------------- pure rules (JTEndgameCheck) ----------------

    /**
     * Whether the endgame runs. It starts while spending freely with net income at the setting's level, or, once it has run,
     * at the income it keeps (so the room it used doesn't stop it coming back). Once on, it stays on until the surplus is under
     * half the spending-freely line or net income goes below zero, so a surplus hovering around 10M doesn't flip it.
     */
    static boolean decide(boolean on, int mode, boolean rich, float amount, float net, float refNet) {
        if (mode < 0 || mode >= ENTER_NET.length) return false;
        if (on) return amount >= CASurplus.RICH / 2f && net >= 0f;
        float need = refNet > 0f ? Math.min(ENTER_NET[mode], floor(refNet)) : ENTER_NET[mode];
        return rich && net >= need;
    }

    /**
     * 0.3.0.0 (C2/C4): whether the endgame runs, on the money tier. rich: spending freely (CASurplus.rich: the tier is Rich or
     * Endgame and the focus-world-only setting is off; it starts at half the surplus line over it and ends under a fifth of
     * it, or with the tier (CASurplus.richStart, richHold)). It starts at the tier Endgame, or once it has run, while spending freely at the income it keeps (refNet), as decide(). Once on it stays on while spending
     * freely and colony net isn't below zero: the Rich tier holds down to 60% of its line, as the old half-line did.
     */
    static boolean decideTier(boolean on, int mode, boolean rich, int tier, float net, float refNet) {
        if (mode < 0 || mode >= ENTER_NET.length) return false;
        if (on) return rich && net >= 0f;
        if (!rich) return false;
        if (tier >= CARich.T4) return net >= 0f;
        float need = refNet > 0f ? Math.min(ENTER_NET[mode], floor(refNet)) : ENTER_NET[mode];
        return net >= need;
    }

    /** Net income the endgame keeps: a third of what it was when it started. */
    static float floor(float refNet) { return KEEP * Math.max(0f, refNet); }

    /** Income a month the endgame may still give up this month. */
    static float room(float net, float refNet, float lostThisMonth) { return Math.max(0f, net - floor(refNet) - Math.max(0f, lostThisMonth)); }

    /** Weights: the endgame's, or the colony goal's where higher; a credit counts MONEY. */
    static float[] weights(float[] goal) {
        float[] w = new float[7];
        w[0] = MONEY;
        for (int i = 1; i < 7; i++) w[i] = Math.max(goal[i], ENDGAME_WEIGHTS[i]);
        return w;
    }

    /**
     * What `units` more of a good are worth a month, given what your colonies make (mine) and need (demand), what the whole
     * sector makes (sector, yours included), how many of your colonies make it and whether this one does; tier 1, 1.5
     * (war goods) or 2 (advanced goods).
     */
    static float chainValue(int units, float mine, float demand, float sector, int producers, boolean producesHere, float tier) {
        return chainValue(units, mine, demand, sector, producers, producesHere, tier, true);
    }

    /**
     * As above. 0.3.0.0 (C4, research 03 F7): spread: a second source is worth SECOND_SOURCE (the one producer is under
     * threat, or the good is a war good); false: a second producer counts only for its units, like any other.
     */
    static float chainValue(int units, float mine, float demand, float sector, int producers, boolean producesHere, float tier, boolean spread) {
        if (units <= 0) return 0f;
        if (mine <= 0f) return tier * (NEW_CHAIN + units * PER_UNIT * 2f);
        float cover = Math.min(units, Math.max(0f, demand - mine));
        float rest = units - cover;
        float share = Math.min(1f, mine / Math.max(1f, sector));
        float v = cover * PER_UNIT * 1.5f + rest * PER_UNIT * (1f - share) * 0.5f;
        if (spread && producers == 1 && !producesHere) v += SECOND_SOURCE;
        return tier * v;
    }

    /** Pure, 0.3.0.0 (C4, F7): a second source earns SECOND_SOURCE: with legacyPlanner (0.2.45.1), for a war good, or while the one producer is at risk. */
    static boolean spreads(boolean legacy, boolean warGood, boolean producerAtRisk) { return legacy || warGood || producerAtRisk; }

    /** Replace when the new industry's score beats what the old one is worth by the margin. */
    static boolean beats(float newScore, float keepValue) { return newScore - keepValue > REPLACE_MARGIN; }

    // ---------------- state ----------------

    public static boolean on(CASettings s) { return s.endgameOn; }

    /** The endgame's valuation applies here: it's on, and this colony spends the surplus. */
    public static boolean at(MarketAPI m) { return m != null && on(CAData.settings()) && CASurplus.spends(m); }

    static float floor(CASettings s) { return floor(s.endgameRefNet); }

    static float room(CASettings s) { return room(CABudget.netIncome(s), s.endgameRefNet, s.endgameLoss); }

    static boolean lossFits(CASettings s, float loss) { return loss <= 0f || loss <= room(s); }

    static void charge(CASettings s, float loss) { if (loss > 0f) s.endgameLoss += loss; }

    static float now() { return CADay.now(); }

    /** Daily: starts or ends the endgame, with a log line each way. */
    public static void daily(CASettings s) {
        // 0.3.0.0 fix (C4 review 2): the Economy rung's buyers spend outside the ladder's pool (DECISIONS 14b), so the rung wants
        // nothing from it and its share goes down to the next rung, from before the planner reads any allowance today. This
        // replaces CAEconomy.publish, which named the buyers' prices and changed the rung's want several times a day
        try { CAEconomy.outsidePool(s); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Economy rung want failed", t); }
        boolean rich = CASurplus.rich(s);
        float net = CABudget.netIncome(s), amount = CASurplus.amount(s);
        // 0.3.0.0 (C2/C4): on the money tier (CARich); legacyPlanner keeps 0.2.45.1's surplus rule
        boolean legacy = CABudget.legacy(s);
        int tier = legacy ? CARich.T0 : CARich.tier();
        boolean next = legacy ? decide(s.endgameOn, s.endgameMode, rich, amount, net, s.endgameRefNet)
                : decideTier(s.endgameOn, s.endgameMode, rich, tier, net, s.endgameRefNet);
        if (next && !s.endgameOn) {
            s.endgameOn = true;
            s.endgameRefNet = Math.max(s.endgameRefNet, net);   // coming back doesn't lower what it keeps
            chains = null;
            CALog.did("Endgame economy: on. " + Misc.getDGSCredits(net) + "/month, " + Misc.getDGSCredits(CASurplus.amount(s))
                    + " surplus. Builds for production chains, military and research. Keeps " + Misc.getDGSCredits(floor(s)) + "/month.", true);
        } else if (!next && s.endgameOn) {
            s.endgameOn = false;
            chains = null;
            String why = s.endgameMode >= ENTER_NET.length ? "turned off"
                    : legacy ? (amount < CASurplus.RICH / 2f ? "surplus under " + Misc.getDGSCredits(CASurplus.RICH / 2f) : "net income below zero")
                    : !rich ? (tier < CARich.T3 ? "money tier " + CARich.name(tier) : "spending freely ended") : "colony net below zero";
            CALog.did("Endgame economy: off (" + why + ").", true);
        }
        summary = s.endgameOn ? summaryText(s) : null;
    }

    // ---------------- production chains ----------------

    static class Chains {
        float day;
        final Map<String, Float> mine = new HashMap<String, Float>(), demand = new HashMap<String, Float>(), sector = new HashMap<String, Float>();
        final Map<String, Set<String>> producers = new HashMap<String, Set<String>>();
        final Map<String, Map<String, Float>> byMarket = new HashMap<String, Map<String, Float>>();

        float get(Map<String, Float> m, String k) { Float v = m.get(k); return v == null ? 0f : v; }

        void addMine(String marketId, String com, float units) {
            if (units <= 0f) return;
            mine.put(com, get(mine, com) + units);
            sector.put(com, get(sector, com) + units);
            Set<String> p = producers.get(com);
            if (p == null) producers.put(com, p = new HashSet<String>());
            p.add(marketId);
            Map<String, Float> at = byMarket.get(marketId);
            if (at == null) byMarket.put(marketId, at = new HashMap<String, Float>());
            at.put(com, get(at, com) + units);
        }
    }

    static Chains chains;

    static boolean economic(CommoditySpecAPI c) {
        return c != null && !c.isMeta() && !c.isNonEcon() && !c.hasTag("nonecon");
    }

    /** What your colonies and the sector make and need, worked out once a day (and kept up to date as builds start). */
    static Chains chains() {
        if (chains != null && now() - chains.day < 1f && now() >= chains.day) return chains;
        Chains c = new Chains();
        c.day = now();
        Set<String> mineIds = new HashSet<String>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (CAEngine.isOutpost(m)) continue;
            mineIds.add(m.getId());
            for (CommodityOnMarketAPI com : m.getAllCommodities()) {
                if (!economic(com.getCommodity())) continue;
                c.addMine(m.getId(), com.getId(), com.getMaxSupply());
                if (com.getMaxDemand() > 0) c.demand.put(com.getId(), c.get(c.demand, com.getId()) + com.getMaxDemand());
            }
            // buildings under construction count as made already, so two colonies don't start the same missing good
            for (Industry ind : m.getIndustries()) {
                if (!ind.isBuilding() || ind.isUpgrading()) continue;
                try {
                    CAEngine.Pick p = CAEngine.effectsOf(m, ind.getId(), Collections.<String, Integer>emptyMap());
                    if (p != null) for (Map.Entry<String, Integer> e : p.supplied.entrySet()) c.addMine(m.getId(), e.getKey(), e.getValue());
                } catch (Throwable ignored) { }
            }
        }
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (mineIds.contains(m.getId()) || m.isHidden()) continue;
            for (CommodityOnMarketAPI com : m.getAllCommodities())
                if (com.getMaxSupply() > 0 && economic(com.getCommodity())) c.sector.put(com.getId(), c.get(c.sector, com.getId()) + com.getMaxSupply());
        }
        chains = c;
        return c;
    }

    /** A build started: its goods count as made from now on. */
    static void note(MarketAPI m, CAEngine.Pick p) {
        if (chains == null || m == null || p == null) return;
        // 0.3.0.0 (B1, 02 section 9): the units it adds over the colony's other sources, not its whole lines (a duplicate adds none)
        for (Map.Entry<String, Integer> e : CAValue.chainUnits(p).entrySet()) chains.addMine(m.getId(), e.getKey(), e.getValue());
    }

    static float tier(String com) {
        for (String g : CAProjects.AOTD_GOODS) if (g.equals(com)) return 2f;
        return WAR_GOODS.contains(com) ? 1.5f : 1f;
    }

    static String name(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getName(); } catch (Throwable t) { return com; }
    }

    static boolean legacy() {
        try { return CABudget.legacy(CAData.settings()); } catch (Throwable t) { return false; }
    }

    /** 0.3.0.0 (C4): the producer in the set that isn't this colony, or null. */
    static String other(Set<String> producers, String here) {
        if (producers == null) return null;
        for (String id : producers) if (!id.equals(here)) return id;
        return null;
    }

    /**
     * 0.3.0.0 (C4, research 03 F7): a colony whose production could stop: under siege, a colony crisis's target, or in its
     * high-threat state (read as saved, so this changes nothing).
     */
    static boolean atRisk(String marketId) {
        if (marketId == null) return false;
        try {
            MarketAPI x = CAAuto.market(marketId);
            if (x == null) return false;
            if (CASiege.held(x)) return true;
            try { if (CACrisis.targets().contains(x)) return true; } catch (Throwable ignored) { }
            return CAData.get().colony(marketId).threatHigh;
        } catch (Throwable t) { return false; }
    }

    /**
     * The production-chain value of what a pick makes (p.supplied), with a line each. removing: the value of the goods an
     * existing building makes here, as if it were gone (so a good's last producer is worth a new chain).
     */
    static float chainValue(MarketAPI m, CAEngine.Pick p, boolean removing) {
        if (p.supplied.isEmpty()) return 0f;
        Chains c = chains();
        float total = 0f;
        for (Map.Entry<String, Integer> e : p.supplied.entrySet()) {
            String com = e.getKey();
            int units = e.getValue();
            if (!economic(Global.getSettings().getCommoditySpec(com))) continue;
            float mine = c.get(c.mine, com), sector = c.get(c.sector, com);
            Set<String> prod = c.producers.get(com);
            int producers = prod == null ? 0 : prod.size();
            boolean here = prod != null && prod.contains(m.getId());
            if (removing) {
                mine = Math.max(0f, mine - units);
                sector = Math.max(0f, sector - units);
                Map<String, Float> at = c.byMarket.get(m.getId());
                float hereUnits = at == null ? 0f : c.get(at, com);
                if (here && hereUnits <= units) { producers--; here = false; }
            }
            // 0.3.0.0 (C4, research 03 F7): the second-source bonus only where a second source protects something
            boolean spread = producers != 1 || here || spreads(legacy(), WAR_GOODS.contains(com), atRisk(other(prod, m.getId())));
            float v = chainValue(units, mine, c.get(c.demand, com), sector, producers, here, tier(com), spread);
            if (v <= 0f) continue;
            total += v;
            String what = mine <= 0f ? "a good none of your colonies make" : mine < c.get(c.demand, com) ? "your colonies are short"
                    : Math.round(100f * Math.min(1f, mine / Math.max(1f, sector))) + "% of the sector's is yours";
            p.lines.add("Endgame: " + name(com) + " (" + what + ") " + CAEngine.valueText(v));
        }
        return total;
    }

    /** Production chains none of your colonies make that the sector does, for the panel. */
    static List<String> missing() {
        Chains c = chains();
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Float> e : c.sector.entrySet()) if (e.getValue() > 0f && c.get(c.mine, e.getKey()) <= 0f) out.add(name(e.getKey()));
        Collections.sort(out);
        return out;
    }

    // ---------------- the plan ----------------

    /** In the endgame the plan only adds priority for what can't be had another way. */
    static boolean keepsPlanBonus(MarketAPI m, CAPlan.Plan plan, String id) {
        if (id == null) return false;
        if (id.startsWith("tme_") || CAPlan.WORLD_STEPS.contains(id) || (plan != null && plan.exact.contains(id))) return true;
        if (CAProjects.isStep(m, id) || CASurplus.isRichStep(m, id) || CAResearch.isFacilityStep(m, id)) return true;
        if (CACapital.isStep(m, id) || CAStations.isStep(m, id)) return true;   // the capital's trade buildings and every colony's station (0.2.42.0)
        return CAWonders.BFC.equals(id);
    }

    /** Open plan industries the endgame still keeps a slot for. */
    static int openKeptIndustrySteps(MarketAPI m, CAPlan.Plan plan) {
        int n = 0;
        for (CAPlan.Step st : plan.steps) {
            if (!keepsPlanBonus(m, plan, st.id) || !CAPlan.needsSlot(m, plan, st)) continue;   // 0.2.38.0: not blocked, reachable, one per chain
            n++;
        }
        return n;
    }

    // ---------------- replacing industries ----------------

    /**
     * An industry a remover picked. keep: what taking it down gives up a month, its CAValue.keep (value) plus the teardown loss
     * spread over the horizon (0.3.0.0, B4), so every comparison (best.score - keep, the planner's NPV of a swap) counts the loss;
     * money and upkeep: its credits a month and its upkeep, for the endgame's income room and the upkeep cap.
     */
    static class Weakest {
        Industry ind; float keep, money, upkeep;
        /** 0.3.0.0 (B4): CAValue.keep alone, the teardown loss (credits), the horizon they were read at, and its build record. */
        float value, loss, h;
        CATombstone.Built built;
    }

    /** 0.3.0.0 (B4): a candidate from CAValue's reading of it (null: one it can't value, worth 0) and its build record, at horizon h. */
    static Weakest of(Industry ind, CAValue.Kept k, CATombstone.Built b, float h) {
        Weakest wk = new Weakest();
        wk.ind = ind; wk.built = b; wk.h = h;
        wk.value = k == null ? 0f : k.value;
        wk.money = k == null ? 0f : k.money;
        wk.upkeep = k == null ? 0f : k.upkeep;
        wk.loss = b == null ? 0f : b.loss;
        wk.keep = wk.value + wk.loss / Math.max(1f, h);
        return wk;
    }

    /** Why the new industry couldn't start once the old one is down (checked first, so nothing is taken down for nothing); null if it can. */
    static String cantStart(MarketAPI m, CAEngine.Pick p) {
        CASettings s = CAData.settings();
        if (CABuild.credits() < p.cost + CABudget.constructionFee(s, p.cost)) return "the credits aren't there";
        if (!CADiagnostics.usable(p.id)) return "Autopilot can't build it";
        if (!CABreaker.allowed("building " + p.name)) return "building it is paused this session";
        if (CAAotD.enabled() && !CAAotD.allows(p.id, m)) return "research hasn't unlocked it";
        return null;
    }

    static boolean protectedHere(MarketAPI m, CAData.Colony col, CAPlan.Plan plan, Industry ind, CAData d) {
        String id = ind.getId();
        if (!ind.isIndustry() || ind.isHidden() || ind.isBuilding() || ind.isUpgrading() || ind.isImproved() || !ind.canShutDown()) return true;
        if (ind.getSpec().hasTag("population") || ind.getSpec().hasTag("station") || col.locks.contains(id)) return true;
        if (keepsPlanBonus(m, plan, id) || CASurplusResearch.FACILITY.equals(id) || CASurplusResearch.BLACK_SITE.equals(id) || CAWonders.SITE.equals(id)) return true;
        if (!CAEngine.isSupportedVanilla(id) && !CAEstimates.has(id)) return true;   // only what the engine can value for certain
        if (CAContracts.feeds(ind) != null) return true;   // a trade contract you hold needs what it makes (0.2.39.0)
        if (CARogue.keepReason(m, ind, false) != null) return true;   // 0.3.2.1: the running reactor's pair, the vault's Tech-Mining
        Long built = d.endgameBuilt().get(m.getId() + ":" + id);
        return built != null && Global.getSector().getClock().getElapsedDaysSince(built) < MEMORY_DAYS;
    }

    /**
     * 0.3.0.0: what a swap does to the colony's stability: the old building's share comes off at once, and a new one that costs
     * stability costs it once it's built (one that adds stability only helps after it's built, so it isn't counted).
     */
    static float swapDelta(MarketAPI m, Industry old, CAEngine.Pick p) {
        return CAStability.takeDownDelta(m, old) + Math.min(0f, p == null ? 0f : p.fx[1]);
    }

    /** The industry here worth least in the endgame that may be replaced, or null. */
    static Weakest weakest(MarketAPI m, CAData.Colony col, CAPlan.Plan plan, CAData.Profile profile) { return weakest(m, col, plan, profile, null); }

    /** As above, for this new pick: never one whose swap the colony can't take (0.3.0.0, CAStability; research 07 M4). */
    static Weakest weakest(MarketAPI m, CAData.Colony col, CAPlan.Plan plan, CAData.Profile profile, CAEngine.Pick next) {
        return weakest(m, col, plan, profile, next, false);
    }

    /**
     * The industry here that's cheapest to give up for `next`, or null. 0.3.0.0 (B4): valued by CAValue.keep at the colony's
     * goal (the endgame's weights and chains where it runs, as a build is scored there), plus the teardown loss spread over
     * the horizon; never one built under another valuation within the year (CATombstone.built). capital: the capital's
     * make-room swap, which never takes down one of its own trade buildings (and, 0.3.0.0 fix, counts their money at weight 1
     * outside the endgame, as 0.2.45.1). Fleets, ship quality, defense and ship production always stay: they're what the
     * endgame builds up, and the home port keeps them.
     */
    static Weakest weakest(MarketAPI m, CAData.Colony col, CAPlan.Plan plan, CAData.Profile profile, CAEngine.Pick next, boolean capital) {
        CAData d = CAData.get();
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        float h = CAHorizon.months();
        String val = CATombstone.valuation(m);
        Weakest best = null;
        for (Industry ind : m.getIndustries()) {
            if (protectedHere(m, col, plan, ind, d)) continue;
            if (capital && CACapital.isStep(m, ind.getId())) continue;
            // 0.3.0.0 (B4, research 02 section 11): nor one of the capital's own plan steps (or below one in its chain): taking it
            // down would only open the step again, with a slot held for something the year's mark won't let it rebuild
            if (capital && planStep(plan, ind.getId())) continue;
            if (!CAStability.canLose(m, swapDelta(m, ind, next), null)) continue;
            CATombstone.Built b = CATombstone.built(m, ind.getId(), h, val);
            if (b.protect != null) continue;   // built under another valuation within the year (02 section 8)
            // 0.3.0.0 (fix): as a remover reads it (CATombstone.forRemoval): no charge for inputs your colonies lack (a Mining
            // read 36,000 a month weaker while any colony was short of drugs), relief against today's supply. 0.3.0.0 (fix, B4
            // review): the capital's swap counts the old building's money at weight 1 outside the endgame, as 0.2.45.1 did
            // (spending freely's quarter let Commerce's plan bonus swap out a Refining earning 50,000 a month)
            CAValue.Kept k = CATombstone.forRemoval(m, CAValue.keepDetail(m, ind, profile, deficit), profile, false, capital);
            if (k == null || k.p == null) continue;   // one the engine can't value
            CAEngine.Pick p = k.p;
            if (p.fx[3] > 0f || p.fx[4] > 0f || p.fx[5] > 0f || p.supplied.containsKey("ships")) continue;
            Weakest wk = of(ind, k, b, h);
            if (best == null || wk.keep < best.keep) best = wk;
        }
        return best;
    }

    /** 0.3.0.0 (B4): a step of this plan, or below one in its upgrade chain (as CAPrune leaves them). */
    static boolean planStep(CAPlan.Plan plan, String id) {
        if (plan == null || id == null) return false;
        for (CAPlan.Step st : plan.steps) if (id.equals(st.id) || CAPlan.inChainBelow(id, st.id)) return true;
        return false;
    }

    /**
     * 0.3.0.0 (B4): the swap pays: NPV(new) - keep x H - teardown loss > the margin, both over the horizon (CATombstone.worthReplacing;
     * the new pick's score is its NPV a month, as the planner counts it). The same as beats(score, keep + loss / H).
     */
    static boolean worthIt(CAEngine.Pick best, Weakest wk) {
        if (best == null || wk == null) return false;
        float h = wk.h > 0f ? wk.h : CAHorizon.months();
        return CATombstone.worthReplacing(best.score * h, wk.value, h, wk.loss, REPLACE_MARGIN * h);
    }

    /** 0.3.0.0 (B4): taken down here within the year (its own mark, or one on what it upgrades into): not the replacement. */
    static boolean rebuildBlocked(MarketAPI m, String id) {
        try { return CAPlanner.tombstoneDays(m.getId(), id) > 0f; } catch (Throwable t) { return false; }
    }

    /** Whether a replacement may happen today here (one a day empire-wide, one a month per colony). */
    static boolean replaceAllowed(MarketAPI m, CASettings s) {
        if (!CARich.wondersOpen(s)) return false;   // 0.3.0.0 (C3 fix): replacements are the Wonders and endgame rung's: none while it's Off or Recommend only, or under Fleet, Expansion or Save
        if (s.endgameNoReplace || now() - lastReplaceDay < 1f && now() >= lastReplaceDay) return false;
        Long last = CAData.get().swapAt().get(m.getId() + ":replaced");
        return last == null || Global.getSector().getClock().getElapsedDaysSince(last) >= REPLACE_COLONY_DAYS;
    }

    /** A replacement candidate for this colony: the weakest industry the best slot-less pick beats, or null. */
    static Weakest replacement(MarketAPI m, CAData.Colony col, CAEngine.Pick best, CAData.Profile profile) {
        if (best == null || best.measured || !at(m) || !replaceAllowed(m, CAData.settings())) return null;
        Long gone = CAData.get().endgameBuilt().get(m.getId() + ":-" + best.id);   // what it took down isn't built again there for a year
        if (gone != null && Global.getSector().getClock().getElapsedDaysSince(gone) < MEMORY_DAYS) return null;
        if (rebuildBlocked(m, best.id)) return null;   // 0.3.0.0 (B4): any remover's mark, not only the endgame's own
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        Weakest wk = weakest(m, col, plan, profile, best);
        if (wk == null || !worthIt(best, wk)) return null;   // 0.3.0.0 (B4): NPV less keep and teardown loss over the horizon
        return wk;
    }

    /**
     * Takes the old industry down (its AI core and item to Autopilot Storage), to make room for the new one. 0.3.0.0: false (and
     * nothing done) when the swap would leave the colony under the stability floor (CAStability), checked at the last moment.
     */
    static boolean takeDown(MarketAPI m, Weakest wk, CAEngine.Pick p) { return takeDown(m, wk, p, null); }

    /** 0.3.2.0: as above; reason CAPlanner.PickRef.FLOOR: a make-room swap for a production floor producer (its own mark and log reason). */
    static boolean takeDown(MarketAPI m, Weakest wk, CAEngine.Pick p, String reason) {
        Industry ind = wk.ind;
        if (!CAStability.canLose(m, swapDelta(m, ind, p), "replacing " + ind.getCurrentName() + " with " + p.name)) return false;
        CAData d = CAData.get();
        String core = ind.getAICoreId();
        SpecialItemData item = ind.getSpecialItem();
        CAStores.emptyInto(m, ind);   // core and item to this colony's Autopilot Storage
        String oldName = ind.getCurrentName(), oldId = ind.getId();
        boolean floor = CAPlanner.PickRef.FLOOR.equals(reason);
        boolean capital = !floor && !at(m) && CACapital.isStep(m, p.id);
        CATombstone.Built b = wk.built != null ? wk.built : CATombstone.built(m, oldId, CAHorizon.months(), null);   // read before the mark clears it
        m.removeIndustry(oldId, MarketAPI.MarketInteractionMode.REMOTE, false);
        long ts = Global.getSector().getClock().getTimestamp();
        d.endgameBuilt().put(m.getId() + ":-" + oldId, ts);
        d.swapAt().put(m.getId() + ":replaced", ts);
        CATombstone.add(m.getId(), oldId, floor ? CATombstone.FLOOR : capital ? CATombstone.CAPITAL : CATombstone.ENDGAME, b.paid);   // 0.3.0.0 (B4): not built back here for a year
        if (!floor) lastReplaceDay = now();   // 0.3.2.0: a make-room swap doesn't use the endgame's one-a-day
        chains = null;
        String why = floor ? "room for a production floor producer" : capital ? "room for the capital's trade" : "endgame";
        CALog.did(m.getName() + ": Took down " + oldName + " for " + p.name + " (" + why + ", worth " + CAEngine.valueText(p.score - wk.keep) + " more"
                + CATombstone.paidText(b) + ")."
                + (core != null || item != null ? " Its " + (core != null && item != null ? "core and item" : core != null ? "core" : "item") + " to Autopilot Storage." : ""), true);
        return true;
    }

    /** A build the endgame started: not replaced again for a year. */
    static void built(MarketAPI m, CAEngine.Pick p) {
        CAData.get().endgameBuilt().put(m.getId() + ":" + p.id, Global.getSector().getClock().getTimestamp());
        note(m, p);
    }

    // ---------------- panel ----------------

    static String summaryText(CASettings s) {
        List<String> miss = missing();
        int n = 0;
        for (String k : CAData.get().endgameBuilt().keySet()) if (k.contains(":-")) n++;
        // 0.3.0.0 (WS-F3 fix pass 2): one status line of 40 words or fewer; what decides, and the missing goods, on hover (HOVER, missingLine).
        // 0.3.0.0 (WS-F3 fix pass 3): what turns it on (the money tier Endgame, or legacyPlanner's surplus line) is the Settings row's
        return "Endgame economy on: net income kept over " + Misc.getDGSCredits(floor(s)) + "; " + Misc.getDGSCredits(room(s))
                + " may still go this month. " + (miss.isEmpty() ? "Your colonies make every good." : miss.size() + " goods you don't make.")
                + (n > 0 ? " Replaced " + n + " building" + (n == 1 ? "" : "s") + " this year." : "");
    }

    static final String HOVER = "Credits count 5%; production chains, fleets, ship quality, defense and research decide.";

    /** The goods the sector makes and your colonies don't, up to 10, for the hover; null when none. */
    static String missingLine() {
        List<String> miss = missing();
        return miss.isEmpty() ? null : "Missing: " + CAContracts.join(miss.size() > 10 ? miss.subList(0, 10) : miss) + (miss.size() > 10 ? ", ..." : "") + ".";
    }

    static void render(TooltipMakerAPI t, CASettings s) {
        if (!on(s)) return;
        if (summary == null) summary = summaryText(s);
        Color gray = Misc.getGrayColor();
        t.addPara(CALog.esc(summary), gray, 2f);
        CATheme.hover(t, HOVER, missingLine());
    }
}
