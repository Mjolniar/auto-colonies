package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Colonies over their industry limit (2.42.1), and colonies at 0 stability (2.42.2).
 *
 * More industries than slots costs a flat 5 stability (vanilla PopulationAndInfrastructure, "Maximum number of industries
 * exceeded"), and below 5 stability every point cuts the colony's income by a fifth. Worse, the game decivilizes a colony
 * that sits at 0 stability (DecivTracker: about monthly samples over 16 months, at least half of them 0 including the first
 * and the latest 3 in a row; it warns once, then 10% a month per month of streak past 3). Wind was decivilized that way in
 * 2.42.0: 3 industries on 2 slots (two picks of one pass took one free slot, fixed in CABuild.start) kept it at 0 for months.
 *
 * Daily, at every Auto colony (sieges included: this is an emergency, not a build):
 * - over the limit at 0 stability or below: its weakest industry Autopilot may take down goes at once, whatever it earns;
 * - over the limit otherwise: unless the colony is about to grow a size with room for them all, its weakest industry goes,
 *   one a day until it's back within the limit (2.44.1: it used to keep them when the weakest earned more than the 5
 *   stability was worth, or when none was one it could value, so colonies Autopilot itself had put over sat there for months);
 * - at 0 stability, an important alert on screen (once a month a colony), Auto or not, with what to do.
 * Never a building being built or upgraded, improved, locked, a station, population, or one holding up 5 or more stability;
 * a plan's special step, a capital trade building, a contract feeder, or one with defense, fleet, ship quality or ship output
 * only when nothing else can go, and one that adds stability last. Its AI core and item go to the colony's Autopilot
 * Storage. Setting: Fix colonies over the industry limit (Building and planning).
 */
public class CAOverLimit {
    static final float PENALTY = 5f, GAP_DAYS = 1f, GROWS_SOON = 0.8f, ALERT_DAYS = 30f;
    /** At or under this stability a colony over its industry limit sheds an industry at once, whatever it earns. */
    static final float LOW = 2f;
    /** Days between two stability-costing buildings taken down at a colony at 0, and how long one stays off the capital's plan. */
    static final float SHED_DAYS = 10f, SHED_HOLD_DAYS = 365f;
    /** Days at 0 before a stability-costing building is taken down (the game's decivilization needs months at 0). */
    static final float ZERO_WAIT_DAYS = 20f;

    /** Every colony: Auto ones are fixed, the rest get the alert. */
    public static void run(List<MarketAPI> auto) {
        CASettings s = CAData.settings();
        CAData d = CAData.get();
        Set<String> autoIds = new HashSet<String>();
        for (MarketAPI m : auto) autoIds.add(m.getId());
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (CAEngine.isOutpost(m)) continue;
            try {
                if (autoIds.contains(m.getId())) {
                    boolean acted = check(d, s, m);
                    if (!acted && atZero(m)) shed(d, m);   // still at 0: a building that costs stability goes
                }
                zeroAlert(d, m, autoIds.contains(m.getId()));
            } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "industry limit / stability check failed at " + m.getName(), t); }
        }
    }

    static boolean atZero(MarketAPI m) { return m.getStabilityValue() <= 0f; }

    /** An important line once a month while a colony sits at 0 stability. */
    static void zeroAlert(CAData d, MarketAPI m, boolean auto) {
        String key = m.getId() + ":zerostab";
        if (!atZero(m)) { d.endgameBuilt().remove(key); d.endgameBuilt().remove(m.getId() + ":zerosince"); return; }
        if (!d.endgameBuilt().containsKey(m.getId() + ":zerosince")) d.endgameBuilt().put(m.getId() + ":zerosince", Global.getSector().getClock().getTimestamp());
        Long last = d.endgameBuilt().get(key);
        if (last != null && Global.getSector().getClock().getElapsedDaysSince(last) < ALERT_DAYS) return;
        d.endgameBuilt().put(key, Global.getSector().getClock().getTimestamp());
        int num = Misc.getNumIndustries(m), max = Misc.getMaxIndustries(m);
        CALog.add(m.getName() + ": stability is at 0, and the game decivilizes a colony that stays at 0 for months. Biggest drags: "
                + causes(m) + (num > max ? ". It has " + num + " industries on " + max + " slots (-5)" : "")
                + (auto ? ". Autopilot sheds what costs stability until it's off 0" : ". It isn't on Auto, so raising it is up to you"), true);
    }

    /** The colony's worst stability modifiers as the game lists them, "Maximum number of industries exceeded -5, Commerce -3". */
    static String causes(MarketAPI m) {
        List<com.fs.starfarer.api.combat.MutableStat.StatMod> neg = new ArrayList<com.fs.starfarer.api.combat.MutableStat.StatMod>();
        for (com.fs.starfarer.api.combat.MutableStat.StatMod sm : m.getStability().getFlatMods().values()) if (sm.value < 0f) neg.add(sm);
        Collections.sort(neg, new Comparator<com.fs.starfarer.api.combat.MutableStat.StatMod>() {
            public int compare(com.fs.starfarer.api.combat.MutableStat.StatMod a, com.fs.starfarer.api.combat.MutableStat.StatMod b) { return Float.compare(a.value, b.value); }
        });
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < Math.min(4, neg.size()); i++) out.add((neg.get(i).desc != null ? neg.get(i).desc : neg.get(i).source) + " " + Math.round(neg.get(i).value));
        return out.isEmpty() ? "none listed" : CAContracts.join(out);
    }

    /** What a building does to the colony's stability, read from the game's own modifiers ("ind_<id>", BaseIndustry.getModId). */
    static float stabilityOf(MarketAPI m, Industry ind) {
        String key = "ind_" + ind.getId();
        float v = 0f;
        for (Map.Entry<String, com.fs.starfarer.api.combat.MutableStat.StatMod> e : m.getStability().getFlatMods().entrySet())
            if (e.getKey().equals(key) || e.getKey().startsWith(key + "_")) v += e.getValue().value;
        return v;
    }

    /**
     * 2.42.2: at 0 stability (and not over the industry limit, or with nothing there to take down), the building that costs
     * the most stability goes, read from the game's own modifiers so mod buildings count too (Commerce -3, a Bazaar, a
     * Privateer Base), one every SHED_DAYS until the colony is off 0. A capital trade building shed this way stays off the
     * capital's plan for a year. Never population, a station, one being built or upgraded, improved or locked.
     */
    static void shed(CAData d, MarketAPI m) {
        // a short dip (a raid, a fresh capture) passes on its own; the game needs months at 0 before it decivilizes
        Long since = d.endgameBuilt().get(m.getId() + ":zerosince");
        if (since == null || Global.getSector().getClock().getElapsedDaysSince(since) < ZERO_WAIT_DAYS) return;
        Long last = d.endgameBuilt().get(m.getId() + ":shed");
        if (last != null && Global.getSector().getClock().getElapsedDaysSince(last) < SHED_DAYS) return;
        CAData.Colony col = d.colony(m.getId());
        Industry worst = null;
        float worstV = 0f;
        for (Industry ind : m.getIndustries()) {
            if (ind.isHidden() || ind.isBuilding() || ind.isUpgrading() || ind.isImproved() || !ind.canShutDown()) continue;
            if (ind.getSpec().hasTag("population") || ind.getSpec().hasTag("station") || col.locks.contains(ind.getId())) continue;
            float v = stabilityOf(m, ind);
            if (v < worstV) { worstV = v; worst = ind; }
        }
        if (worst == null) {
            if (CALog.once("stab:none:" + m.getId())) CALog.add(m.getName() + ": stability is at 0 and none of its buildings is what's costing it ("
                    + causes(m) + "), so Autopilot can't raise it by taking one down", true);
            return;
        }
        CALog.forget("stab:none:" + m.getId());
        String core = worst.getAICoreId(), name = worst.getCurrentName(), id = worst.getId();
        SpecialItemData item = worst.getSpecialItem();
        CAStores.emptyInto(m, worst);
        m.removeIndustry(id, MarketAPI.MarketInteractionMode.REMOTE, false);
        long ts = Global.getSector().getClock().getTimestamp();
        d.endgameBuilt().put(m.getId() + ":shed", ts);
        d.endgameBuilt().put(m.getId() + ":shed:" + id, ts);
        CALog.add(m.getName() + ": took down " + name + " (" + Math.round(worstV) + " stability): the colony was at 0, where the game can decivilize it"
                + (core != null || item != null ? "; its " + (core != null && item != null ? "core and item" : core != null ? "core" : "item") + " to Autopilot Storage" : ""), true);
    }

    /** A building shed at this colony for stability within the last year: the capital's plan leaves it out. */
    static boolean shedRecently(MarketAPI m, String id) {
        Long t = CAData.get().endgameBuilt().get(m.getId() + ":shed:" + id);
        return t != null && Global.getSector().getClock().getElapsedDaysSince(t) < SHED_HOLD_DAYS;
    }

    /** Returns true when it took an industry down. */
    static boolean check(CAData d, CASettings s, MarketAPI m) {
        int num = Misc.getNumIndustries(m), max = Misc.getMaxIndustries(m);
        String key = "overlimit:" + m.getId();
        if (num <= max) { CALog.forget(key + ":keep"); CALog.forget(key + ":grow"); CALog.forget(key + ":none"); CALog.forget(key + ":off"); return false; }
        String over = num + " industries on " + max + " slots";
        boolean zero = atZero(m), danger = m.getStabilityValue() <= LOW;
        if (s.noOverLimitFix) {
            if (CALog.once(key + ":off")) CALog.add(m.getName() + ": " + over + ", which costs 5 stability. Fixing it is off in Settings, so taking one down is up to you", zero);
            return false;
        }
        if (!danger) {
            // about to grow into the room it needs
            int size = m.getSize();
            if (size < Misc.getMaxMarketSize(m) && Misc.getMarketSizeProgress(m) >= GROWS_SOON
                    && max + PopulationAndInfrastructure.getMaxIndustries(size + 1) - PopulationAndInfrastructure.getMaxIndustries(size) >= num) {
                if (CALog.once(key + ":grow")) CALog.add(m.getName() + ": " + over + " (5 stability), but it's about to grow to size " + (size + 1) + ", which has room for them all, so it waits");
                return false;
            }
            Long last = d.endgameBuilt().get(m.getId() + ":overlimit");
            if (last != null && Global.getSector().getClock().getElapsedDaysSince(last) < GAP_DAYS) return false;
        }
        CAData.Colony col = d.colony(m.getId());
        // 2.44.1: an Auto colony over its limit always sheds one: the weakest of what a colony can do without first, then
        // (defenses, fleets, ship-making, plan steps, mod buildings it can't value) the weakest of anything else that may go
        CAEndgame.Weakest wk = weakest(m, col, false);
        if (wk == null) wk = weakest(m, col, true);
        if (wk == null) {
            if (CALog.once(key + ":none")) CALog.add(m.getName() + ": " + over + ", which costs 5 stability, and every industry there is locked, "
                    + "being built or upgraded, improved, or holds up its stability, so taking one down is up to you", true);
            return false;
        }
        takeDown(d, m, wk, over, danger ? Float.MAX_VALUE : 0f, zero);
        return true;
    }

    /**
     * The industry here worth least that may go: one the engine can value, and none of what a colony must keep. With any
     * (stability at 2 or under), also defense, fleet and ship-making ones and mod ones it can't value (counted as worth 0),
     * but never population, a station, improved, locked, or one being built.
     */
    static CAEndgame.Weakest weakest(MarketAPI m, CAData.Colony col, boolean any) {
        float[] w = CAEngine.WEIGHTS[CAThreat.effective(m, col.profile).ordinal()];
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        CAEndgame.Weakest out = null;
        float outRank = 0f;
        for (Industry ind : m.getIndustries()) {
            String id = ind.getId();
            if (!ind.isIndustry() || ind.isHidden() || ind.isBuilding() || ind.isUpgrading() || ind.isImproved() || !ind.canShutDown()) continue;
            if (ind.getSpec().hasTag("population") || ind.getSpec().hasTag("station") || col.locks.contains(id)) continue;
            if (CAMods.leftToYou(id) != null) continue;   // you built it, Autopilot never does: it stays (2.45.0)
            // one that holds up the colony's stability by 5 or more: taking it down wouldn't lift the colony at all (2.44.1)
            float stab = stabilityOf(m, ind);
            if (stab >= PENALTY) continue;
            if (!any && (CAEndgame.keepsPlanBonus(m, plan, id) || CAContracts.feeds(ind) != null)) continue;
            CAEngine.Pick p = CAEngine.effectsOf(m, id, deficit);
            if (p == null && any) {   // a mod industry the engine can't value: worth 0, so it goes before anything that earns
                float r = rank(0f, stab);
                if (out == null || r < outRank) { out = new CAEndgame.Weakest(); out.ind = ind; out.keep = 0f; outRank = r; }
                continue;
            }
            if (p == null) continue;   // a building the engine can't value for sure
            if (!any && (p.fx[3] > 0f || p.fx[4] > 0f || p.fx[5] > 0f || p.supplied.containsKey("ships"))) continue;
            CAEngine.upkeep(m, ind.getSpec(), null, p);
            float v = 0f;
            for (int i = 0; i < 7; i++) v += w[i] * p.fx[i];
            float r = rank(v, stab);
            if (out == null || r < outRank) { out = new CAEndgame.Weakest(); out.ind = ind; out.keep = v; out.money = p.fx[0]; out.upkeep = p.upkeep; outRank = r; }
        }
        return out;
    }

    /** Ranking for weakest(): one that adds stability goes only when nothing else can (2.44.1). */
    static float rank(float value, float stability) { return stability > 0f ? value + 1e9f : value; }

    static void takeDown(CAData d, MarketAPI m, CAEndgame.Weakest wk, String over, float gain, boolean zero) {
        Industry ind = wk.ind;
        String core = ind.getAICoreId();
        SpecialItemData item = ind.getSpecialItem();
        String name = ind.getCurrentName(), id = ind.getId();
        CAStores.emptyInto(m, ind);   // core and item to this colony's Autopilot Storage
        m.removeIndustry(id, MarketAPI.MarketInteractionMode.REMOTE, false);
        long ts = Global.getSector().getClock().getTimestamp();
        d.endgameBuilt().put(m.getId() + ":overlimit", ts);
        d.endgameBuilt().put(m.getId() + ":replaced", ts);   // and no make-room swap at the capital for a while
        CALog.add(m.getName() + ": took down " + name + ": " + over + " cost 5 stability" + (zero ? " and left it at 0, where a colony can decivilize"
                : gain == Float.MAX_VALUE && m.getStabilityValue() <= LOW ? " and left it at " + Math.round(m.getStabilityValue()) + ", close to 0 where a colony can decivilize"
                : "") + ", and " + name
                + " was worth least (" + CAEngine.valueText(wk.keep) + ")"
                + (core != null || item != null ? "; its " + (core != null && item != null ? "core and item" : core != null ? "core" : "item") + " to Autopilot Storage" : ""), true);
    }
}
