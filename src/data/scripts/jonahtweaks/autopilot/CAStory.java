package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.ArrayList;
import java.util.List;

/** Story point improvements under the chosen rule: Player only (recommend), One per colony, or a monthly cap on story points spent. */
public class CAStory {

    /** The improvement the manager would pick at this colony, or null. */
    public static Industry bestImprovement(MarketAPI m, CAData.Colony c) {
        Industry best = null; int bestP = 99;
        for (Industry ind : m.getIndustries()) {
            try {
                if (ind.isHidden() || !ind.isFunctional() || ind.isImproved() || !ind.canImprove() || c.locks.contains(ind.getId())) continue;
                if (CAMods.noImprove(ind.getId())) continue;   // its improvement does nothing (Yunru's Advent Outreach)
                int p = CAItems.industryPriority(ind);
                if (p < bestP) { bestP = p; best = ind; }
            } catch (Throwable t) { CADiagnostics.quarantine(ind.getId(), "improvement check", t); }
        }
        return best;
    }

    /**
     * One improvement a day. Each one at a colony costs twice the last there (industryImproveBase x 2^improved), so the
     * cheapest anywhere goes first, and the monthly cap counts story points spent (spMonth), not improvements.
     */
    public static void run(List<MarketAPI> markets) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (s.storyPointRule == 0) return;
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
        if (i < 0) return;
        MarketAPI m = ms.get(i);
        Industry ind = inds.get(i);
        int cost = costs.get(i);
        try {
            Global.getSector().getPlayerStats().spendStoryPoints(cost, true, null, false, "Colony Autopilot: improved " + ind.getCurrentName());
            ind.setImproved(true);
            m.reapplyIndustries();
            d.colony(m.getId()).spUsed++;
            s.spMonth += cost;
            CALog.add(m.getName() + ": spent " + cost + " story point" + (cost == 1 ? "" : "s") + " improving " + ind.getCurrentName()
                    + (s.storyPointRule == 2 ? " (" + s.spMonth + " of " + s.storyPointMonthlyCap + " this month)" : ""), true);
        } catch (Throwable t) {
            CADiagnostics.quarantine(ind.getId(), "improving with story points", t);
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
