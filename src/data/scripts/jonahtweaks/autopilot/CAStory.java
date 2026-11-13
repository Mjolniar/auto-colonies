package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * Story point improvements under the chosen rule: Player only (recommend), One per colony, or a monthly cap on story points spent.
 * 0.3.0.0 (WS-B3): the day's improvement is an action in the planner's one queue (offer), so it never goes into a building
 * that starts an upgrade the same day (one action per building); with legacyPlanner run() spends as before.
 */
public class CAStory {

    /** The planner's name for these actions. */
    static final String SOURCE = "story points";

    /**
     * The improvement the manager would pick at this colony, or null. 0.3.0.0 (B1, research 02 sections 9 and 17): an industry
     * that makes goods is ranked by what its +1 output is really worth (CAValue.boost on each line: only what it adds above
     * the colony's other sources), ties broken by CAItems.industryPriority, and skipped when that's nothing (the +1 lands on a
     * line another building out-makes); those come first. Anything else keeps the priority rule.
     */
    public static Industry bestImprovement(MarketAPI m, CAData.Colony c) {
        Industry best = null; int bestP = 99;          // the priority rule (buildings whose +1 isn't output)
        Industry bestOut = null; float bestV = 0f; int bestOutP = 99;   // industries ranked by their +1 output's worth
        java.util.Map<String, Integer> deficit = null;   // read once per call
        for (Industry ind : m.getIndustries()) {
            try {
                if (ind.isHidden() || !ind.isFunctional() || ind.isImproved() || !ind.canImprove() || c.locks.contains(ind.getId())) continue;
                if (CAMods.noImprove(ind.getId())) continue;   // its improvement does nothing (Yunru's Advent Outreach)
                int p = CAItems.industryPriority(ind);
                if (ind.isIndustry()) {
                    float v = 0f; boolean makes = false;
                    for (com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity q : ind.getAllSupply()) {
                        if (q.getQuantity().getModifiedInt() <= 0) continue;
                        if (deficit == null) deficit = CAEngine.empireDeficits();
                        makes = true;
                        v += CAValue.boost(m, ind, q.getCommodityId(), 1, deficit);
                    }
                    if (makes) {
                        if (v <= 0f) continue;   // the +1 adds nothing the colony's other sources don't already give
                        if (bestOut == null || v > bestV || (v == bestV && p < bestOutP)) { bestOut = ind; bestV = v; bestOutP = p; }
                        continue;
                    }
                }
                if (p < bestP) { bestP = p; best = ind; }
            } catch (Throwable t) { CADiagnostics.quarantine(ind.getId(), "improvement check", t); }
        }
        return bestOut != null ? bestOut : best;
    }

    /** The day's choice: which colony and building, and what it costs. */
    static final class Choice {
        MarketAPI m;
        Industry ind;
        int cost;
    }

    /**
     * The cheapest improvement anywhere that fits the rule, what's left of the monthly cap and the story points you have; null
     * for none. Each one at a colony costs twice the last there (industryImproveBase x 2^improved), so the cheapest anywhere
     * goes first, and the monthly cap counts story points spent (spMonth), not improvements.
     */
    static Choice choose(List<MarketAPI> markets, CAData d, CASettings s) {
        int have = Global.getSector().getPlayerStats().getStoryPoints();
        int left = s.storyPointRule == 2 ? s.storyPointMonthlyCap - s.spMonth : Integer.MAX_VALUE;
        List<MarketAPI> ms = new ArrayList<MarketAPI>();
        List<Industry> inds = new ArrayList<Industry>();
        List<Integer> costs = new ArrayList<Integer>();
        for (MarketAPI m : markets) {
            CAData.Colony c = d.colony(m.getId());
            if (s.storyPointRule == 1 && c.spUsed >= 1) continue;
            Industry ind = bestImprovement(m, c);
            if (ind == null) continue;
            try { costs.add(ind.getImproveStoryPoints()); } catch (Throwable t) { CADiagnostics.quarantine(ind.getId(), "improvement cost", t); continue; }
            ms.add(m);
            inds.add(ind);
        }
        int i = pick(costs, left, have);
        if (i < 0) return null;
        Choice ch = new Choice();
        ch.m = ms.get(i); ch.ind = inds.get(i); ch.cost = costs.get(i);
        return ch;
    }

    /** One improvement a day (0.2.45 path; with the planner in charge it's offered instead). */
    public static void run(List<MarketAPI> markets) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (s.storyPointRule == 0) return;
        if (CAPlanner.inCharge()) return;   // 0.3.0.0 (B3): offered to the planner (offer)
        Choice ch = choose(markets, d, s);
        if (ch == null) return;
        improve(ch.m, ch.ind, ch.cost);
    }

    /**
     * 0.3.0.0 (WS-B3): the day's improvement as a planner action: story points, no credits; started by the rule you set (One
     * per colony or a monthly cap), one a day (a flag), and it ties up its building so an upgrade starting there the same
     * pass keeps it from going into the old building.
     */
    public static List<CAAction> offer(List<MarketAPI> markets) {
        List<CAAction> out = new ArrayList<CAAction>();
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (s.storyPointRule == 0 || markets.isEmpty()) return out;
        Choice ch = choose(markets, d, s);
        if (ch == null) return out;
        CAAction a = new CAAction();
        a.kind = CAAction.Kind.STORY; a.source = SOURCE; a.market = ch.m; a.marketId = ch.m.getId();
        a.id = ch.ind.getId(); a.name = "improve " + ch.ind.getCurrentName(); a.building = ch.ind.getId();
        a.storyPoints = ch.cost;
        a.byRule = true;   // your story point rule spends them
        a.fromBudget = false; a.chargeAll = false;
        a.once = CAPlanner.ONCE_STORY;
        a.payload = ch.ind;
        a.starter = (x) -> {
            Industry ind = (Industry) x.payload;
            if (ind.isImproved() || !ind.canImprove()) return "already improved";
            return improve(x.market, ind, x.storyPoints) ? null : "the improvement failed (see Diagnostics)";
        };
        out.add(a);
        return out;
    }

    /** Spends the story points and improves it; false when that failed (quarantined). */
    static boolean improve(MarketAPI m, Industry ind, int cost) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        try {
            Global.getSector().getPlayerStats().spendStoryPoints(cost, true, null, false, "Colony Autopilot: improved " + ind.getCurrentName());
            ind.setImproved(true);
            m.reapplyIndustries();
            d.colony(m.getId()).spUsed++;
            s.spMonth += cost;
            CALog.did(m.getName() + ": Spent " + cost + " story point" + (cost == 1 ? "" : "s") + " improving " + ind.getCurrentName()
                    + (s.storyPointRule == 2 ? " (" + s.spMonth + " of " + s.storyPointMonthlyCap + " this month)" : "") + ".", true);
            return true;
        } catch (Throwable t) {
            CADiagnostics.quarantine(ind.getId(), "improving with story points", t);
            return false;
        }
    }

    /** The cheapest cost that fits both what's left of the monthly cap and the story points you have, or -1. */
    static int pick(List<Integer> costs, int left, int have) {
        int best = -1;
        for (int i = 0; i < costs.size(); i++) {
            int c = costs.get(i);
            if (c <= 0 || c > left || c > have) continue;
            if (best < 0 || c < costs.get(best)) best = i;
        }
        return best;
    }
}
