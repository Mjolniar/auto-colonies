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

    /** Hazard rating change of a condition (from condition_gen_data). */
    static final Map<String, Float> HAZARD = new HashMap<String, Float>();
    static {
        String[] q = {"cold", "hot", "tectonic_activity", "thin_atmosphere", "extreme_weather", "low_gravity",
                "inimical_biosphere", "water_surface", "poor_light", "pollution", "decivilized", "decivilized_subpop"};
        String[] h = {"very_cold", "very_hot", "extreme_tectonic_activity", "no_atmosphere", "toxic_atmosphere",
                "dense_atmosphere", "high_gravity", "irradiated", "dark", "meteor_impacts"};
        for (String s : q) HAZARD.put(s, 0.25f);
        for (String s : h) HAZARD.put(s, 0.5f);
        HAZARD.put("habitable", -0.25f);
        HAZARD.put("mild_climate", -0.25f);
    }

    static final Map<String, CAData.Profile[]> WORLD_FITS = new HashMap<String, CAData.Profile[]>();
    static {
        WORLD_FITS.put("tme_paradise_world", new CAData.Profile[]{CAData.Profile.INCOME, CAData.Profile.GROWTH, CAData.Profile.BALANCED});
        WORLD_FITS.put("tme_forge_world", new CAData.Profile[]{CAData.Profile.WAR, CAData.Profile.SELF});
        WORLD_FITS.put("tme_fortress_world", new CAData.Profile[]{CAData.Profile.SECURITY, CAData.Profile.WAR});
        WORLD_FITS.put("tme_hive_world", new CAData.Profile[]{CAData.Profile.GROWTH});
        WORLD_FITS.put("tme_death_world", new CAData.Profile[]{CAData.Profile.SECURITY});
        WORLD_FITS.put("tme_waste_world", new CAData.Profile[]{CAData.Profile.SELF});
    }

    /** Credit-equivalent per month of having this condition at this colony (negative = better without it). */
    static float conditionValue(MarketAPI m, String cond, CAData.Profile p) {
        float[] w = CAEngine.WEIGHTS[p.ordinal()];
        float v = 0f;
        Float hz = HAZARD.get(cond);
        if (hz != null) {
            float upkeep = Math.max(0f, m.getGrossIncome() - m.getNetIncome());
            v -= hz * upkeep;                         // upkeep scales with hazard
            v -= hz * 10f * w[6] / 2f;                // growth penalty
        }
        String com = CAEngine.DEPOSIT.get(cond);
        if (com != null) {
            int units = m.getSize() + CAEngine.BASE_MOD.get(com) + CAEngine.DEPOSIT_MOD.get(cond);
            float price = Global.getSettings().getCommoditySpec(com).getBasePrice();
            v += Math.max(0, units) * price * CAEngine.unitValue * 0.8f;
        }
        if (cond.equals("solar_array")) v += 3000f;
        if (cond.equals("volturnian_lobster_pens")) v += 6000f;
        CAData.Profile[] fits = WORLD_FITS.get(cond);
        if (fits != null) {
            v += 20000f;
            for (CAData.Profile f : fits) if (f == p) v += 60000f;
        }
        return v;
    }

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
            if (k.cost > s.perProjectCap) {
                if (CALog.once("tfcap:" + k.m.getId() + ":" + k.pd.id))
                    CALog.add(k.m.getName() + ": terraforming (" + k.why + ") is over the per-project cap; start it from " + k.ind.getCurrentName() + " if you want it", true);
                continue;
            }
            if (k.cost + fee > Math.min(CABudget.available(s), terraLeft) || CABuild.credits() < k.cost + fee) {
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