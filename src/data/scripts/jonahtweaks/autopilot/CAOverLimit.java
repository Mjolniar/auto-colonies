package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Colonies over their industry limit (2.42.0). More industries than slots costs a flat 5 stability (vanilla
 * PopulationAndInfrastructure, "Maximum number of industries exceeded"), and below 5 stability every point cuts the
 * colony's income by a fifth, so a colony at 0 earns nothing. It happens when a colony shrinks, loses what gave it a slot,
 * or (before 2.42.0's check in CABuild.start) when one pass started two industries on one free slot.
 *
 * Weekly, at each Auto colony over the limit, unless the colony is about to grow a size that has room for them all:
 * - its weakest industry Autopilot can value (never one being built or upgraded, improved, locked, a capital trade
 *   building, a station, defense, fleet or ship-making building, or another plan's special step) is compared with what
 *   the 5 stability is worth there; if the industry is worth more, it stays and the log says why;
 * - otherwise it's taken down, its AI core and item to the colony's Autopilot Storage. One per colony every 30 days.
 * Setting: Fix colonies over the industry limit (Building and planning).
 */
public class CAOverLimit {
    static final float PENALTY = 5f, GAP_DAYS = 30f, GROWS_SOON = 0.8f;

    public static void run(List<MarketAPI> auto) {
        CASettings s = CAData.settings();
        CAData d = CAData.get();
        for (MarketAPI m : auto) {
            try { check(d, s, m); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "industry limit check failed at " + m.getName(), t); }
        }
    }

    static void check(CAData d, CASettings s, MarketAPI m) {
        int num = Misc.getNumIndustries(m), max = Misc.getMaxIndustries(m);
        String key = "overlimit:" + m.getId();
        if (num <= max) { CALog.forget(key + ":keep"); CALog.forget(key + ":grow"); CALog.forget(key + ":none"); CALog.forget(key + ":off"); return; }
        String over = num + " industries on " + max + " slots";
        if (s.noOverLimitFix) {
            if (CALog.once(key + ":off")) CALog.add(m.getName() + ": " + over + ", which costs 5 stability. Fixing it is off in Settings, so taking one down is up to you");
            return;
        }
        // about to grow into the room it needs
        int size = m.getSize();
        if (size < Misc.getMaxMarketSize(m) && Misc.getMarketSizeProgress(m) >= GROWS_SOON
                && max + PopulationAndInfrastructure.getMaxIndustries(size + 1) - PopulationAndInfrastructure.getMaxIndustries(size) >= num) {
            if (CALog.once(key + ":grow")) CALog.add(m.getName() + ": " + over + " (5 stability), but it's about to grow to size " + (size + 1) + ", which has room for them all, so it waits");
            return;
        }
        Long last = d.endgameBuilt().get(m.getId() + ":overlimit");
        if (last != null && Global.getSector().getClock().getElapsedDaysSince(last) < GAP_DAYS) return;
        CAData.Colony col = d.colony(m.getId());
        CAEndgame.Weakest wk = weakest(m, col);
        if (wk == null) {
            if (CALog.once(key + ":none")) CALog.add(m.getName() + ": " + over + ", which costs 5 stability. None of its industries is one Autopilot takes down "
                    + "(defenses, fleets, ship-making, plan or mod buildings it can't value), so taking one down is up to you");
            return;
        }
        // what 5 stability is worth here; with stability already at 0 the colony earns nothing, so there's no comparing
        float raw = m.getStability().getModifiedValue();
        float gain = m.getPrevStability() <= 0.5f ? Float.MAX_VALUE : CAItems.stabilityWorth(m, raw, PENALTY);
        if (wk.keep >= gain) {
            if (CALog.once(key + ":keep")) CALog.add(m.getName() + ": " + over + ", which costs 5 stability (about " + Misc.getDGSCredits(gain)
                    + " a month here); keeping them, since its weakest, " + wk.ind.getCurrentName() + ", is worth more (" + CAEngine.valueText(wk.keep) + ")");
            return;
        }
        takeDown(d, m, wk, over, gain);
    }

    /** The industry here worth least that may go: one the engine can value, and none of what a colony must keep. */
    static CAEndgame.Weakest weakest(MarketAPI m, CAData.Colony col) {
        float[] w = CAEngine.WEIGHTS[CAThreat.effective(m, col.profile).ordinal()];
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        CAEndgame.Weakest out = null;
        for (Industry ind : m.getIndustries()) {
            String id = ind.getId();
            if (!ind.isIndustry() || ind.isHidden() || ind.isBuilding() || ind.isUpgrading() || ind.isImproved() || !ind.canShutDown()) continue;
            if (ind.getSpec().hasTag("population") || ind.getSpec().hasTag("station") || col.locks.contains(id)) continue;
            if (CAEndgame.keepsPlanBonus(m, plan, id) || CAContracts.feeds(ind) != null) continue;
            CAEngine.Pick p = CAEngine.effectsOf(m, id, deficit);
            if (p == null) continue;   // a building the engine can't value for sure
            if (p.fx[3] > 0f || p.fx[4] > 0f || p.fx[5] > 0f || p.supplied.containsKey("ships")) continue;
            CAEngine.upkeep(m, ind.getSpec(), null, p);
            float v = 0f;
            for (int i = 0; i < 7; i++) v += w[i] * p.fx[i];
            if (out == null || v < out.keep) { out = new CAEndgame.Weakest(); out.ind = ind; out.keep = v; out.money = p.fx[0]; out.upkeep = p.upkeep; }
        }
        return out;
    }

    static void takeDown(CAData d, MarketAPI m, CAEndgame.Weakest wk, String over, float gain) {
        Industry ind = wk.ind;
        String core = ind.getAICoreId();
        SpecialItemData item = ind.getSpecialItem();
        String name = ind.getCurrentName(), id = ind.getId();
        CAStores.emptyInto(m, ind);   // core and item to this colony's Autopilot Storage
        m.removeIndustry(id, MarketAPI.MarketInteractionMode.REMOTE, false);
        long ts = Global.getSector().getClock().getTimestamp();
        d.endgameBuilt().put(m.getId() + ":overlimit", ts);
        d.endgameBuilt().put(m.getId() + ":replaced", ts);   // and no make-room swap at the capital for a while
        CALog.add(m.getName() + ": took down " + name + ": " + over + " cost 5 stability" + (gain == Float.MAX_VALUE ? " and all of its income"
                : " (about " + Misc.getDGSCredits(gain) + " a month)") + ", and " + name + " was worth least (" + CAEngine.valueText(wk.keep) + ")"
                + (core != null || item != null ? "; its " + (core != null && item != null ? "core and item" : core != null ? "core" : "item") + " to Autopilot Storage" : ""), true);
    }
}
