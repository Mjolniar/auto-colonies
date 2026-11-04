package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import terraformingmadeeasy.Settings;
import terraformingmadeeasy.Utils;
import terraformingmadeeasy.industries.BaseTerraformingIndustry;
import terraformingmadeeasy.ui.plugins.ProjectListPlugin;

import java.util.*;

/**
 * Surplus terraforming (2.26.0): credits over the surplus line, after the focus world has had its pick, go into
 * Terraforming Made Easy projects at every Auto colony, most value per credit first: richer deposits and farmland, harsh
 * conditions cleared (a planet with none left turns habitable), building the structure first when the colony has none.
 * Planned on TME's own rules (CATerraSim) one or two projects ahead, so an enabler such as tectonic activity counts with the
 * richer transplutonics it allows, and a project that costs more than it gives (extreme tectonic activity takes a habitable
 * world's farmland and organics) never goes. What the colony's plan needs is never undone, and nothing it wants gone is
 * added. A structure it builds is taken down once nothing worth doing is left for it (TME's changes stay), so its upkeep
 * counts only while it works; structures you or the plan built stay. Mode (Settings, Budget and spending): Max output
 * funds anything worth 1,000 a month or more; Pays back in 10 years only what earns its price back by then; Off. Paid
 * from the surplus alone, outside the monthly budget, so ordinary builds never wait for it. Structures research still
 * locks are valued for CAResearch (CATerraSim.lockedValue). Only loaded with TME.
 */
public class CATerraformSurplus {
    static final String ACTION = "surplus terraforming";
    static final Map<String, CATerraSim.Result> plans = CATerraSim.plans;
    static final Map<String, Float> planAt = CATerraSim.planAt;
    /** The conditions each colony's cached plan was made for. */
    static final Map<String, Set<String>> planConds = new HashMap<String, Set<String>>();

    static class Cand { MarketAPI m; CATerraSim.Seq q; Cand(MarketAPI m, CATerraSim.Seq q) { this.m = m; this.q = q; } }

    static float now() { return CADay.now(); }

    static IndustrySpecAPI spec(String id) {
        try { return Global.getSettings().getIndustrySpec(id); } catch (Throwable t) { return null; }
    }

    /** Whether the colony could start building this structure now: supported, unlocked by research and the build menu. */
    static boolean buildable(MarketAPI m, String id) {
        try {
            IndustrySpecAPI sp = spec(id);
            if (sp == null || !CADiagnostics.usable(id)) return false;
            if (CAAotD.enabled() && !CAAotD.allows(id, m)) return false;
            Industry probe = sp.getNewPluginInstance(m);
            return probe != null && probe.isAvailableToBuild();
        } catch (Throwable t) { return false; }
    }

    /** base: the value already coming from projects under way, so a plan made on top of them counts only what it adds. */
    static CATerraSim.Colony colony(final MarketAPI m, final CAData.Profile prof, final float base) {
        final Map<String, Boolean> can = new HashMap<String, Boolean>();
        return new CATerraSim.Colony() {
            public float value(Set<String> after) { return CAConditions.change(m, after, prof, null) - base; }
            public boolean has(String s) { return m.hasIndustry(s); }
            public boolean ours(String s) { return CAData.get().surplusBuilt().contains(m.getId() + ":" + s); }
            public float buildDays(String s) { IndustrySpecAPI sp = spec(s); return sp == null ? 0f : sp.getBuildTime(); }
            public boolean buildable(String s) {
                Boolean b = can.get(s);
                if (b == null) { b = CATerraformSurplus.buildable(m, s); can.put(s, b); }
                return b;
            }
            public float buildCost(String s) { IndustrySpecAPI sp = spec(s); return sp == null ? 1e12f : sp.getCost(); }
            public float upkeep(String s) { IndustrySpecAPI sp = spec(s); return sp == null ? 0f : CAEngine.baseUpkeep(m, sp); }
        };
    }

    /** TME structures at the colony that are being built, upgraded or running a project, with the project each runs (null while building). */
    static Map<String, Utils.ProjectData> busy(MarketAPI m) {
        Map<String, Utils.ProjectData> out = new HashMap<String, Utils.ProjectData>();
        for (Industry ind : m.getIndustries()) {
            if (!(ind instanceof BaseTerraformingIndustry)) continue;
            BaseTerraformingIndustry ti = (BaseTerraformingIndustry) ind;
            if (ti.isBuilding() || ti.isUpgrading() || ti.hasOngoingProject()) out.put(ind.getId(), ti.hasOngoingProject() ? ti.getProject() : null);
        }
        return out;
    }

    /**
     * The colony's surplus terraforming plan, worked out once a day and again whenever its conditions or busy structures
     * change. It's made from the conditions the colony will have once the projects under way finish, for the structures
     * that are free, and leaves out any project that would touch a running one's condition either way (TME projects
     * remove what they hate, so the order they finish in would matter): several can then run at once.
     */
    static CATerraSim.Result plan(MarketAPI m, CAData.Colony c, CASettings s) {
        Set<String> conds = new HashSet<String>();
        for (MarketConditionAPI mc : m.getConditions()) conds.add(mc.getId());
        Map<String, Utils.ProjectData> busy = busy(m);
        Set<String> key = new HashSet<String>(conds);
        for (String b : busy.keySet()) key.add("busy:" + b);
        Float at = planAt.get(m.getId());
        CATerraSim.Result cached = plans.get(m.getId());
        // a plan from before the colony's conditions changed (a project just finished) is redone at once
        if (cached != null && at != null && now() - at < 1f && key.equals(planConds.get(m.getId()))) return cached;
        planConds.put(m.getId(), key);
        CAPlan.Plan plan = CAPlan.build(m, c.profile);
        Set<String>[] ka = CATerraform.keepAvoid(m, plan, conds);   // the same rule as the plan's own projects, so neither undoes the other
        Set<String> keep = ka[0], avoid = ka[1];
        if (CAConditions.pollutionReturns(m)) keep.add("pollution");   // its forge's upgrade adds it back: removing it now is wasted
        // spending freely: tectonic activity may go in first for the richer deposits that need it (CATerraSim.enablerOk), and the
        // best is what adds most, not most per credit
        boolean rich = CASurplus.rich(s);
        if (rich && plan.world == null) avoid.removeAll(CATerraSim.ENABLERS);
        boolean gas = CATerraform.gasGiant(m);
        Map<String, CAWorldPlanner.Row> all = CAWorldPlanner.rows();
        Set<String> start = conds, running = new HashSet<String>(), runningHate = new HashSet<String>();
        for (Utils.ProjectData pd : busy.values()) {
            if (pd == null) continue;
            CAWorldPlanner.Row row = all.get(pd.id);
            running.add(pd.id);
            if (row == null) continue;
            runningHate.addAll(CAWorldPlanner.idsOf(row.hated));
            CATerraSim.Move mv = new CATerraSim.Move();
            mv.structure = row.structure; mv.condition = pd.id; mv.add = !start.contains(pd.id);
            start = CATerraSim.apply(start, mv, row, gas);
        }
        Map<String, CAWorldPlanner.Row> rows = new HashMap<String, CAWorldPlanner.Row>();
        for (Map.Entry<String, CAWorldPlanner.Row> e : all.entrySet()) {
            CAWorldPlanner.Row r = e.getValue();
            if (busy.containsKey(r.structure) || running.contains(r.condition) || runningHate.contains(r.condition)) continue;
            boolean touches = false;
            for (String h : CAWorldPlanner.idsOf(r.hated)) if (running.contains(h)) touches = true;
            if (!touches) rows.put(e.getKey(), r);
        }
        CAData.Profile prof = CAThreat.effective(m, c.profile);
        float base = start == conds ? 0f : CAConditions.change(m, start, prof, null);
        CATerraSim.Result r = CATerraSim.plan(start, gas, rows, keep, avoid, Settings.BUILD_COST_MULTIPLIER,
                Settings.REMOVAL_COST_MULTIPLIER, s.surplusTerraform, colony(m, prof, base), rich);
        plans.put(m.getId(), r);
        planAt.put(m.getId(), now());
        return r;
    }

    /** While spending freely (CASurplus.rich), up to this many surplus projects start at a colony a day, on different structures. */
    static final int PARALLEL = 3;

    public static void run(List<MarketAPI> auto) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        CATerraSim.lockedValue.clear();
        if (s.surplusTerraform >= 2 || CASurplus.line(s) <= 0f) { CATerraSim.summary = null; return; }
        expireEnablers(d);
        List<Cand> cands = new ArrayList<Cand>();
        String lockedText = null; float lockedBest = 0f;
        for (MarketAPI m : auto) {
            if (CAHypershunt.is(m) || CAEngine.isOutpost(m) || m.getPlanetEntity() == null) continue;
            CATerraSim.Result r;
            try { r = plan(m, d.colony(m.getId()), s); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus terraforming plan failed at " + m.getName(), t); continue; }
            for (Map.Entry<String, CATerraSim.Seq> e : r.locked.entrySet()) {
                Float cur = CATerraSim.lockedValue.get(e.getKey());
                CATerraSim.lockedValue.put(e.getKey(), (cur == null ? 0f : cur) + e.getValue().gain);
                if (e.getValue().gain > lockedBest) {
                    lockedBest = e.getValue().gain;
                    lockedText = CAWorldPlanner.industryName(e.getKey()) + " (research still locks it) would let " + m.getName() + " "
                            + e.getValue().text() + ": " + CAEngine.valueText(e.getValue().gain);
                }
            }
            if (r.best != null) cands.add(new Cand(m, r.best));
            try { takeDown(m, r, d.colony(m.getId())); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus terraforming clean-up failed at " + m.getName(), t); }
        }
        Collections.sort(cands, new Comparator<Cand>() { public int compare(Cand a, Cand b) { return Float.compare(b.q.ratio(), a.q.ratio()); } });
        String mode = CASettings.SURPLUS_TF_NAMES[Math.max(0, Math.min(2, s.surplusTerraform))];
        if (cands.isEmpty()) CATerraSim.summary = "Surplus terraforming (" + mode + "): nothing worth doing with what's built and researched"
                + (lockedText == null ? "." : "; " + lockedText + ".");
        else {
            Cand top = cands.get(0);
            CATerraSim.summary = "Surplus terraforming (" + mode + "): " + cands.size() + (cands.size() == 1 ? " colony has" : " colonies have")
                    + " a project worth doing; best per credit is " + top.m.getName() + ": " + top.q.text() + ", " + CAEngine.valueText(top.q.gain)
                    + " for " + Misc.getDGSCredits(top.q.cost) + (top.q.builds.isEmpty() ? "" : " with the structure") + ".";
        }
        float budget = CASurplus.amount(s);
        Set<String> acted = new HashSet<String>();
        for (Cand k : cands) {
            try { float spent = act(k, s, budget); budget -= spent; if (spent > 0f) acted.add(k.m.getId()); }
            catch (Throwable t) { CADiagnostics.quarantine(k.q.moves.get(0).structure, "surplus terraforming at " + k.m.getName(), t); }
        }
        // spending freely: a colony that started one goes again for its other free structures, planned on top of what's running
        if (!CASurplus.rich(s)) return;
        for (int pass = 1; pass < PARALLEL && !acted.isEmpty(); pass++) {
            Set<String> again = new HashSet<String>();
            for (MarketAPI m : auto) {
                if (!acted.contains(m.getId())) continue;
                try {
                    CATerraSim.Result r = plan(m, d.colony(m.getId()), s);
                    if (r.best == null) continue;
                    float spent = act(new Cand(m, r.best), s, budget);
                    budget -= spent;
                    if (spent > 0f) again.add(m.getId());
                } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus terraforming (more at once) failed at " + m.getName(), t); }
            }
            acted = again;
        }
    }

    /** Takes the next step of a colony's plan if the surplus covers it; returns what it spent. */
    static float act(Cand k, CASettings s, float budget) {
        MarketAPI m = k.m;
        CATerraSim.Move mv = k.q.moves.get(0);
        String key = m.getId() + ":" + mv.structure + ":" + mv.condition;
        String what = k.q.text() + ", " + CAEngine.valueText(k.q.gain) + " once done";
        if (CAData.get().colony(m.getId()).locks.contains(mv.structure)) return 0f;
        Industry ind = m.getIndustry(mv.structure);
        if (ind == null) {
            if (Misc.getCurrentlyBeingConstructed(m) != null) return 0f;   // one new building at a time; it waits its turn
            IndustrySpecAPI sp = spec(mv.structure);
            if (sp == null) return 0f;
            float cost = sp.getCost(), fee = CABudget.constructionFee(s, cost), up = CAEngine.baseUpkeep(m, sp);
            if (cost + fee > budget) { waits(m, key, "building " + sp.getName() + " (" + what + ")", cost + fee); return 0f; }
            if (s.upkeepAdded + up > CASurplus.upkeepCap(m, s)) {
                if (CALog.once("tsu:" + key)) CALog.add(m.getName() + ": surplus terraforming (" + sp.getName() + ", " + Misc.getDGSCredits(up)
                        + " a month) waits for next month's new-upkeep room");
                return 0f;
            }
            CAEngine.Pick p = new CAEngine.Pick();
            p.id = mv.structure; p.name = sp.getName(); p.verb = "Build"; p.cost = cost; p.upkeep = up;
            String err = CABuild.start(m, p);
            if (err != null) {
                if (CALog.once("tse:" + key + ":" + err)) CALog.add(m.getName() + ": surplus terraforming couldn't start building " + sp.getName() + ": " + err);
                return 0f;
            }
            CABudget.charge(s, 0f, fee, up);   // the price came from the surplus (CABuild.start took it), outside the monthly budget
            CAData.get().surplusBuilt().add(m.getId() + ":" + mv.structure);
            CALog.add(m.getName() + ": surplus terraforming started building " + sp.getName() + " for " + Misc.getDGSCredits(cost)
                    + (fee > 0f ? " + " + Misc.getDGSCredits(fee) + " fee" : "") + ", for " + what + "; it's taken down again once its work is done", true);
            return cost + fee;
        }
        if (!(ind instanceof BaseTerraformingIndustry)) return 0f;
        BaseTerraformingIndustry ti = (BaseTerraformingIndustry) ind;
        if (ti.isBuilding() || ti.isUpgrading() || ti.hasOngoingProject()) return 0f;
        // a TME project toggles its condition: on a world that already is the way the move wants, it would undo it
        // (pollution removed by the plan an hour ago, then added back by this). Never start one that goes the wrong way
        if (!wantsChange(mv.add, m.hasCondition(mv.condition))) {
            planAt.remove(m.getId());
            CADiagnostics.LOG.info(CADiagnostics.TAG + m.getName() + ": surplus terraforming dropped '" + mv.label() + "': the colony already is that way; replanning");
            return 0f;
        }
        Utils.ProjectData pd = null;
        List<Utils.ProjectData> projects = ti.getProjects();
        if (projects != null) for (Utils.ProjectData x : projects) if (x.id.equals(mv.condition)) pd = x;
        if (pd == null || !ProjectListPlugin.canBuild(ti, pd)) {
            if (CALog.once("tsn:" + key)) CALog.add(m.getName() + ": surplus terraforming wanted " + ti.getCurrentName() + " to " + mv.label()
                    + ", but Terraforming Made Easy doesn't offer it there now");
            return 0f;
        }
        float cost = CATerraform.projectCost(m, pd), fee = CABudget.fee(s, CATerraform.STANDARD_FEE);
        if (cost + fee > budget) { waits(m, key, what, cost + fee); return 0f; }
        if (!CABreaker.allowed(ACTION)) return 0f;
        ti.setProject(pd);
        ti.startUpgrading();
        if (!CABreaker.check(ACTION, ti.isUpgrading() || ti.hasOngoingProject(), m.getName())) return 0f;
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
        CABudget.charge(s, 0f, fee, 0f);
        if (mv.add && CATerraSim.ENABLERS.contains(mv.condition) && k.q.moves.size() > 1) {   // the plan leaves it until the deposit is in
            CAData.get().enablers().put(m.getId() + ":" + mv.condition, k.q.moves.get(1).condition);
            CAData.get().enablerAt().put(m.getId() + ":" + mv.condition, Global.getSector().getClock().getTimestamp());
        }
        CALog.add(m.getName() + ": surplus terraforming: " + ti.getCurrentName() + " started to " + mv.label() + " (" + Math.round(pd.buildTime)
                + " days) for " + Misc.getDGSCredits(cost) + (fee > 0f ? " + " + Misc.getDGSCredits(fee) + " fee" : "")
                + (k.q.moves.size() > 1 ? "; then " + k.q.moves.get(1).label() : "") + ": " + CAEngine.valueText(k.q.gain) + " once done", true);
        return cost + fee;
    }

    /**
     * Takes down a structure surplus terraforming built once it's idle and no worthwhile sequence starts with it, unless the
     * colony's plan has it as a step or you locked it. TME's conditions stay; its upkeep stops.
     */
    static void takeDown(MarketAPI m, CATerraSim.Result r, CAData.Colony c) {
        Set<String> built = CAData.get().surplusBuilt();
        String prefix = m.getId() + ":";
        for (String key : new ArrayList<String>(built)) {
            if (!key.startsWith(prefix)) continue;
            String id = key.substring(prefix.length());
            Industry ind = m.getIndustry(id);
            if (ind == null) { built.remove(key); continue; }   // gone already
            if (ind.isBuilding() || ind.isUpgrading() || (ind instanceof BaseTerraformingIndustry && ((BaseTerraformingIndustry) ind).hasOngoingProject())) continue;
            if (r.wanted.contains(id) || c.locks.contains(id)) continue;
            if (id.equals("tme_geomorphology_station") && CAConditions.pollutionReturns(m)) continue;   // the forge's upgrade brings pollution back: it'll be needed
            boolean planned = false;
            for (CAPlan.Step st : CAPlan.build(m, c.profile).steps) if (st.kind == CAPlan.Kind.BUILD && st.id.equals(id)) planned = true;
            if (planned) { built.remove(key); continue; }   // the plan wants it now: it's the plan's to keep
            float up = ind.getUpkeep().getModifiedValue();
            m.removeIndustry(id, null, false);
            built.remove(key);
            CALog.add(m.getName() + ": surplus terraforming took down " + ind.getCurrentName() + ": nothing worth doing is left for it; "
                    + "what it changed stays, and its " + Misc.getDGSCredits(up) + " a month upkeep stops");
        }
    }

    /** Days an enabler waits for its deposit before the plan may clean it up anyway. */
    static final float ENABLER_DAYS = 540f;

    /** Drops enabler marks whose deposit is in (the plan may then clear the hazard; the deposit stays), is gone, or waited too long. */
    static void expireEnablers(CAData d) {
        for (String key : new ArrayList<String>(d.enablers().keySet())) {
            int at = key.lastIndexOf(':');
            MarketAPI m = at < 0 ? null : CAAuto.market(key.substring(0, at));
            Long since = d.enablerAt().get(key);
            boolean done = m == null || !m.isPlayerOwned() || m.hasCondition(d.enablers().get(key))
                    || since == null || Global.getSector().getClock().getElapsedDaysSince(since) > ENABLER_DAYS;
            if (done) { d.enablers().remove(key); d.enablerAt().remove(key); }
        }
    }

    /** Whether a move still changes the colony: adding a condition it lacks, or removing one it has (TME toggles either way). */
    static boolean wantsChange(boolean add, boolean has) { return add != has; }

    static void waits(MarketAPI m, String key, String what, float need) {
        if (CALog.once("tsw:" + key)) CALog.add(m.getName() + ": surplus terraforming (" + what + ") waits for " + Misc.getDGSCredits(need) + " of surplus credits");
    }
}
