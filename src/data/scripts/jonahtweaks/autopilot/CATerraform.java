package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import terraformingmadeeasy.Settings;
import terraformingmadeeasy.Utils;
import terraformingmadeeasy.industries.BaseDevelopmentIndustry;
import terraformingmadeeasy.industries.BaseTerraformingIndustry;
import terraformingmadeeasy.ui.plugins.ProjectListPlugin;

import java.util.*;

/**
 * Terraforming planner for Terraforming Made Easy. Only loaded when that mod is enabled.
 * Uses TME's own project lists and build checks, and starts projects exactly like TME's dialog.
 * Plans one project ahead, plus a two-step look-ahead for replacing a deposit with a richer one.
 */
public class CATerraform {
    static final float STANDARD_FEE = 25000f;

    /** Hazard rating change of a condition (shared with TASC's planner in CAConditions). */
    static final Map<String, Float> HAZARD = CAConditions.HAZARD;
    static final Map<String, CAData.Profile[]> WORLD_FITS = CAConditions.WORLD_FITS;

    /** Credit-equivalent per month of having this condition at this colony (negative = better without it). */
    static float conditionValue(MarketAPI m, String cond, CAData.Profile p) { return CAConditions.value(m, cond, p); }

    static float projectCost(MarketAPI m, Utils.ProjectData pd) {
        float mult = m.hasCondition(pd.id) ? Settings.REMOVAL_COST_MULTIPLIER : 1f;
        return Math.round(pd.cost * mult * Settings.BUILD_COST_MULTIPLIER);
    }

    static class Cand { MarketAPI m; BaseTerraformingIndustry ind; Utils.ProjectData pd; float cost, score; String why; boolean planned; }

    /**
     * Every idle TME structure in an Auto colony works its part of the colony plan: the earliest unfinished plan step
     * it can run comes first; otherwise its best-value project. Candidates are funded in priority order within the budget.
     */
    public static void run(List<MarketAPI> markets) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        List<Cand> cands = new ArrayList<Cand>();
        for (MarketAPI m : markets) {
            CAData.Colony c = d.colony(m.getId());
            CAData.Profile prof = CAThreat.effective(m, c.profile);
            CAPlan.Plan plan = CAPlan.build(m, c.profile);
            for (Industry raw : m.getIndustries()) {
                if (!(raw instanceof BaseTerraformingIndustry)) continue;
                BaseTerraformingIndustry ind = (BaseTerraformingIndustry) raw;
                if (ind.isBuilding() || ind.isUpgrading() || ind.hasOngoingProject() || c.locks.contains(ind.getId())) continue;
                if ("tme_stellar_manufactory".equals(ind.getId())) continue;   // needs mirror and shade choices
                List<Utils.ProjectData> projects;
                try { projects = ind.getProjects(); } catch (Throwable t) { CADiagnostics.quarantine(ind.getId(), "reading terraforming projects", t); continue; }
                if (projects == null) continue;
                Cand pick = null;
                // 1) the earliest unfinished plan step this structure can run
                int order = 0;
                for (CAPlan.Step st : plan.steps) {
                    order++;
                    if (st.done(m) || st.kind == CAPlan.Kind.BUILD) continue;
                    if (!ind.getId().equals(CAWorldPlanner.structureFor(st.id)) && st.kind == CAPlan.Kind.COND) continue;
                    for (Utils.ProjectData pd : projects) {
                        if (!pd.id.equals(st.id)) continue;
                        boolean adding = !m.hasCondition(pd.id);
                        if ((st.kind == CAPlan.Kind.COND) != adding) continue;
                        try { if (!ProjectListPlugin.canBuild(ind, pd)) continue; } catch (Throwable t) { continue; }
                        pick = new Cand(); pick.m = m; pick.ind = ind; pick.pd = pd; pick.cost = projectCost(m, pd);
                        pick.score = 1000000f - order * 1000f; pick.planned = true;
                        pick.why = (adding ? "add " : "remove ") + pd.name + " (" + plan.name + " plan, step " + order + ")";
                        break;
                    }
                    if (pick != null) break;
                }
                // 2) otherwise the structure's best-value project
                if (pick == null) {
                    for (Utils.ProjectData pd : projects) {
                        try {
                            if (!ProjectListPlugin.canBuild(ind, pd)) continue;
                            boolean adding = !m.hasCondition(pd.id);
                            if (!adding && plan.world != null && planNeeds(plan, pd.id)) continue;   // never undo a plan condition
                            float value = conditionValue(m, pd.id, prof) * (adding ? 1f : -1f) + CAPlan.bonusForCondition(m, plan, pd.id, adding);
                            float cost = projectCost(m, pd);
                            float score = value - cost / 36f - pd.buildTime * 30f;
                            if (score > 0f && (pick == null || score > pick.score)) {
                                pick = new Cand(); pick.m = m; pick.ind = ind; pick.pd = pd; pick.cost = cost; pick.score = score;
                                pick.why = (adding ? "add " : "remove ") + pd.name;
                            }
                        } catch (Throwable t) { CADiagnostics.quarantine(ind.getId(), "scoring terraforming project " + pd.id, t); }
                    }
                }
                if (pick != null) cands.add(pick);
            }
        }
        Collections.sort(cands, new Comparator<Cand>() { public int compare(Cand a, Cand b) { return Float.compare(b.score, a.score); } });
        for (Cand k : cands) {
            float fee = CABudget.fee(s, STANDARD_FEE);
            float terraLeft = CABudget.limit(s) * s.terraformShare - s.terraSpent;
            if (k.planned) {
                // a plan step, the world's final project too: past the per-project cap and the monthly budget, paid from
                // savings above the credit reserve, since it's how the colony reaches its plan
                if (CABuild.credits() - s.reserve < k.cost + fee) {
                    if (CALog.once("tfsave:" + k.m.getId() + ":" + k.pd.id))
                        CALog.add(k.m.getName() + ": terraforming (" + k.why + ") waits until you have " + Misc.getDGSCredits(k.cost + fee)
                                + " above the credit reserve", true);
                    continue;
                }
            } else if (k.cost > s.perProjectCap) {
                if (CALog.once("tfcap:" + k.m.getId() + ":" + k.pd.id))
                    CALog.add(k.m.getName() + ": terraforming (" + k.why + ") is over the per-project cap; start it from " + k.ind.getCurrentName() + " if you want it", true);
                continue;
            }
            else if (k.cost + fee > Math.min(CABudget.available(s), terraLeft) || CABuild.credits() < k.cost + fee) {
                if (CALog.once("tf:" + k.m.getId() + ":" + k.pd.id))
                    CALog.add(k.m.getName() + ": terraforming (" + k.why + ") waits for the terraforming budget");
                continue;
            }
            try {
                k.ind.setProject(k.pd);
                k.ind.startUpgrading();
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(k.cost);
                CABudget.charge(s, k.cost, fee, 0f);
                s.terraSpent += k.cost;
                CALog.forget("tf:" + k.m.getId() + ":" + k.pd.id);
                CALog.add(k.m.getName() + ": " + k.ind.getCurrentName() + " started: " + k.why + " for " + Misc.getDGSCredits(k.cost)
                        + (fee > 0 ? " + " + Misc.getDGSCredits(fee) + " fee" : ""), true);
            } catch (Throwable t) {
                CADiagnostics.quarantine(k.ind.getId(), "starting terraforming project " + k.pd.id, t);
            }
        }
    }

    static boolean planNeeds(CAPlan.Plan plan, String condId) {
        for (CAPlan.Step st : plan.steps) if (st.kind == CAPlan.Kind.COND && st.id.equals(condId)) return true;
        return false;
    }
}