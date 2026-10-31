package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.List;

/** Story point improvements under the chosen rule: Player only (recommend), One per colony, or a Monthly cap. */
public class CAStory {

    /** The improvement the manager would pick at this colony, or null. */
    public static Industry bestImprovement(MarketAPI m, CAData.Colony c) {
        Industry best = null; int bestP = 99;
        for (Industry ind : m.getIndustries()) {
            try {
                if (ind.isHidden() || !ind.isFunctional() || ind.isImproved() || !ind.canImprove() || c.locks.contains(ind.getId())) continue;
                int p = CAItems.industryPriority(ind);
                if (p < bestP) { bestP = p; best = ind; }
            } catch (Throwable t) { CADiagnostics.quarantine(ind.getId(), "improvement check", t); }
        }
        return best;
    }

    public static void run(List<MarketAPI> markets) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (s.storyPointRule == 0) return;
        for (MarketAPI m : markets) {
            CAData.Colony c = d.colony(m.getId());
            if (s.storyPointRule == 1 && c.spUsed >= 1) continue;
            if (s.storyPointRule == 2 && s.spMonth >= s.storyPointMonthlyCap) return;
            Industry ind = bestImprovement(m, c);
            if (ind == null) continue;
            int cost = ind.getImproveStoryPoints();
            if (Global.getSector().getPlayerStats().getStoryPoints() < cost) return;
            try {
                Global.getSector().getPlayerStats().spendStoryPoints(cost, true, null, false, "Colony Autopilot: improved " + ind.getCurrentName());
                ind.setImproved(true);
                m.reapplyIndustries();
                c.spUsed++;
                s.spMonth++;
                CALog.add(m.getName() + ": spent " + cost + " story point" + (cost == 1 ? "" : "s") + " improving " + ind.getCurrentName(), true);
                return;
            } catch (Throwable t) {
                CADiagnostics.quarantine(ind.getId(), "improving with story points", t);
                return;
            }
        }
    }
}
