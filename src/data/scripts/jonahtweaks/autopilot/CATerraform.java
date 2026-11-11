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
    static final float STANDARD_FEE = CATuning.TERRAFORM_FEE;   // 0.3.0.0 (A3): the number lives in CATuning

    /** Hazard rating change of a condition (shared with TASC's planner in CAConditions). */
    static final Map<String, Float> HAZARD = CAConditions.HAZARD;
    static final Map<String, CAData.Profile[]> WORLD_FITS = CAConditions.WORLD_FITS;

    static float projectCost(MarketAPI m, Utils.ProjectData pd) {
        float mult = m.hasCondition(pd.id) ? Settings.REMOVAL_COST_MULTIPLIER : 1f;
        return Math.round(pd.cost * mult * Settings.BUILD_COST_MULTIPLIER);
    }

    static class Cand { MarketAPI m; BaseTerraformingIndustry ind; Utils.ProjectData pd; float cost, score; String why; boolean planned; String world; }

    static final String ACTION = "terraforming";

    /** Hazards a plain plan clears (CAPlan's clean-up), which no terraforming may bring back; a Forge keeps tectonic activity and gravity. */
    static final String[] CLEANUP = {"toxic_atmosphere", "dense_atmosphere", "irradiated", "pollution", "inimical_biosphere", "high_gravity",
            "extreme_tectonic_activity"};

    /**
     * What terraforming must leave alone at a colony, shared by the plan's projects and surplus terraforming. keep: its COND
     * steps, the world its plan works toward and every condition that world needs, and a world it has formed (TME never
     * re-checks a formed world's requirements, so only the world itself). avoid: what it wants gone: NOCOND steps, what the
     * target world hates, and on a plain plan the hazards it clears, so nothing adds back what the plan just paid to remove.
     */
    @SuppressWarnings("unchecked")
    static Set<String>[] keepAvoid(MarketAPI m, CAPlan.Plan plan, Set<String> conds) {
        Set<String> keep = new HashSet<String>(), avoid = new HashSet<String>();
        // an enabler surplus terraforming added on the way to a richer deposit stays until the deposit is in
        for (String e : CATerraSim.ENABLERS) if (conds.contains(e) && CAData.enabler(m, e)) keep.add(e);
        for (CAPlan.Step st : plan.steps) {
            if (st.kind == CAPlan.Kind.COND) keep.add(st.id);
            else if (st.kind == CAPlan.Kind.NOCOND) avoid.add(st.id);
        }
        for (String w : CAWorldPlanner.WORLDS) {
            if (conds.contains(w)) { keep.add(w); continue; }
            if (!w.equals(plan.world)) continue;
            keep.add(w);
            CAWorldPlanner.Row r = CAWorldPlanner.rows().get(w);
            if (r != null) { keep.addAll(CAWorldPlanner.idsOf(r.liked)); avoid.addAll(CAWorldPlanner.idsOf(r.hated)); }
        }
        if (plan.world == null) for (String h : CLEANUP) {
            if ("Forge".equals(plan.name) && (h.equals("extreme_tectonic_activity") || h.equals("high_gravity"))) continue;
            avoid.add(h);
        }
        Set<String>[] held = CAPlan.protectedConds(m); keep.addAll(held[0]); avoid.addAll(held[1]);   // 0.3.0.0 (B5): what a committed world plan paid for, so surplus terraforming doesn't undo it either
        avoid.removeAll(keep);
        return new Set[]{keep, avoid};
    }

    static Set<String> conditions(MarketAPI m) {
        Set<String> c = new HashSet<String>();
        for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI mc : m.getConditions()) c.add(mc.getId());
        return c;
    }

    static boolean gasGiant(MarketAPI m) {
        return m.getPrimaryEntity() instanceof com.fs.starfarer.api.campaign.PlanetAPI && ((com.fs.starfarer.api.campaign.PlanetAPI) m.getPrimaryEntity()).isGasGiant();
    }

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
            Set<String> conds = conditions(m);
            Set<String>[] ka = keepAvoid(m, plan, conds);
            boolean gas = gasGiant(m), pollutionBack = CAConditions.pollutionReturns(m);
            if (pollutionBack && m.hasCondition("pollution") && CALog.once("tfpollution:" + m.getId()))
                CALog.add(m.getName() + ": pollution isn't removed while its nanoforge forge is being upgraded: the new building adds it back when it finishes");
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
                        if (!adding && pd.id.equals("pollution") && pollutionBack) continue;
                        try { if (!ProjectListPlugin.canBuild(ind, pd)) continue; } catch (Throwable t) { continue; }
                        pick = new Cand(); pick.m = m; pick.ind = ind; pick.pd = pd; pick.cost = projectCost(m, pd);
                        pick.score = 1000000f - order * 1000f; pick.planned = true;
                        pick.world = plan.world;
                        pick.why = (adding ? "add " : "remove ") + pd.name + " (" + plan.name + " plan, step " + order + ")";
                        break;
                    }
                    if (pick != null) break;
                }
                // 2) otherwise the structure's best-value project, valued on what the colony is after it: TME removes what the
                // added condition hates (sparse transplutonics deletes abundant ones) and settles farmland, organics and habitability
                if (pick == null) {
                    for (Utils.ProjectData pd : projects) {
                        try {
                            if (!ProjectListPlugin.canBuild(ind, pd)) continue;
                            boolean adding = !m.hasCondition(pd.id);
                            if (!adding && pd.id.equals("pollution") && pollutionBack) continue;
                            CATerraSim.Move mv = new CATerraSim.Move();
                            mv.structure = ind.getId(); mv.condition = pd.id; mv.add = adding;
                            CAWorldPlanner.Row row = CAWorldPlanner.rows().get(pd.id);
                            Set<String> after;
                            if (row != null) after = CATerraSim.apply(conds, mv, row, gas);
                            else { after = new HashSet<String>(conds); if (adding) after.add(pd.id); else after.remove(pd.id); }
                            if (!CATerraSim.valid(conds, after, ka[0], ka[1])) continue;   // never undoes what the plan needs, never adds what it clears
                            float value = CAConditions.change(m, after, prof, null) + CAEconomy.hazardPaySaved(m, after) + CAPlan.bonusForCondition(m, plan, pd.id, adding);   // 0.3.0.0 (C4, research 03 F9): plus the hazard pay the colony's growth no longer needs, for the months to its max size (CAEconomy.hazardPaySaved)
                            float cost = projectCost(m, pd);
                            float score = value - (CAPlan.legacy() ? cost / 36f * CASurplus.costWeight(m) : cost / CAHorizon.months()) - pd.buildTime * 30f;   // 0.3.0.0 (B2): the price over the payback horizon (CAHorizon), the same at every colony; legacyPlanner: 0.2.45's quarter at the focus world
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
                                + " above the credit reserve");
                    continue;
                }
            } else if (CASurplus.spends(k.m)) {
                // the focus world's best-value projects (every colony's while spending freely) are paid from surplus credits,
                // past the caps and the terraforming budget
                if (k.cost + fee > CASurplus.amount(s)) {
                    if (CALog.once("tfsurplus:" + k.m.getId() + ":" + k.pd.id))
                        CALog.add(k.m.getName() + ": terraforming (" + k.why + ") waits for " + Misc.getDGSCredits(k.cost + fee) + " of surplus credits");
                    continue;
                }
            } else if (k.cost > CAPresets.projectCap(s)) {   // 0.3.0.0 (WS-F1 fix): the cap in effect
                if (CALog.once("tfcap:" + k.m.getId() + ":" + k.pd.id))
                    CALog.add(k.m.getName() + ": terraforming (" + k.why + ") is over the per-project cap; start it from " + k.ind.getCurrentName() + " if you want it");
                continue;
            }
            else if (k.cost + fee > Math.min(CABudget.available(s), terraLeft) || CABuild.credits() < k.cost + fee) {
                if (CALog.once("tf:" + k.m.getId() + ":" + k.pd.id))
                    CALog.add(k.m.getName() + ": terraforming (" + k.why + ") waits for the terraforming budget");
                continue;
            }
            // 0.3.0.0 (B3 fix): the plan's projects don't offer the planner actions yet (0.3.0.x), so they ask it first: credits it saves
            // up for its earmarked pick aren't spent here (CAPlanner.setAside; null while it isn't in charge or saves up for nothing)
            String held = CAPlanner.setAside(k.cost + fee, k.cost + fee, 0f, k.m);
            if (held != null) {
                if (CALog.once("tfheld:" + k.m.getId() + ":" + k.pd.id)) CALog.quiet(k.m.getName() + ": terraforming (" + k.why + ") waits: " + held);
                continue;
            }
            try {
                if (!CABreaker.allowed(ACTION)) continue;
                k.ind.setProject(k.pd);
                k.ind.startUpgrading();
                if (!CABreaker.check(ACTION, k.ind.isUpgrading() || k.ind.hasOngoingProject(), k.m.getName())) continue;   // not charged if it didn't start
                CABudget.pay(s, null, k.cost, fee, 0f);
                if (k.planned && k.world != null) CAPlan.commitWorld(k.m, k.world, k.pd.id, !k.m.hasCondition(k.pd.id));   // 0.3.0.0 (B5): the condition this project changes is kept for the commitment
                s.terraSpent += k.cost;
                CALog.forget("tf:" + k.m.getId() + ":" + k.pd.id);
                CALog.forget("tfheld:" + k.m.getId() + ":" + k.pd.id);
                CALog.add(k.m.getName() + ": " + k.ind.getCurrentName() + " started: " + k.why + " for " + Misc.getDGSCredits(k.cost)
                        + (fee > 0 ? " + " + Misc.getDGSCredits(fee) + " fee" : ""));
            } catch (Throwable t) {
                CADiagnostics.quarantine(k.ind.getId(), "starting terraforming project " + k.pd.id, t);
            }
        }
    }

    /** A Terraforming Made Easy project running at the colony now adds or removes this condition. */
    static boolean producing(MarketAPI m, String condId) {
        for (Industry raw : m.getIndustries()) {
            if (!(raw instanceof BaseDevelopmentIndustry)) continue;
            BaseDevelopmentIndustry ind = (BaseDevelopmentIndustry) raw;
            try {
                if (ind.hasOngoingProject() && ind.getProject() != null && condId.equals(ind.getProject().id)) return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    static boolean planNeeds(CAPlan.Plan plan, String condId) {
        for (CAPlan.Step st : plan.steps) if (st.kind == CAPlan.Kind.COND && st.id.equals(condId)) return true;
        return false;
    }
}