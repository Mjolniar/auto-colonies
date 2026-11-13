package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Surplus terraforming (0.2.26.0): credits over the surplus line, after the focus world has had its pick, go into
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
 * 0.3.0.0 (WS-B3): the planner (CAPlanner) starts these from its one queue (offer), so a structure takes a colony's
 * construction queue only when nothing better waits there; run() keeps the plan, the summary and the clean-up.
 * 0.3.0.0 (WS-C4, research 03 F6): a sequence that pays back within 24 months (its prices, upkeep while it works and fees
 * against its gain a month) may use the month's budget when surplus credits don't cover its next step, within the
 * terraforming share of the budget and its savings (CAEconomy.terraformSource); that step's price then counts against
 * the month's budget and the terraforming share, as a plan project's does. Wind's Terrestrial Engine (+81,062 a month for
 * 525,000) waited months for surplus while 1.3M to 1.6M of budget went unspent. It's part of the money ladder's Economy
 * rung: with that rung Off it starts nothing, and only with it On does it use the budget. What it pays from surplus
 * credits is booked on that rung (CAEconomy.spent).
 * 0.3.0.0 (WS-C4, research 03 F9): a project that lowers the hazard of a colony that can still grow also counts the hazard
 * pay its growth no longer needs (CAEconomy.hazardPaySaved), for the months to its max size over the horizon.
 * 0.3.0.0 fix (C4 review): a structure it built whose first project hasn't run yet stays at least UNUSED_HOLD_DAYS after
 * it's finished, so a change in a sequence's value can't build it and take it down unused.
 * 0.3.0.0 fix (C4 review 2): surplus credits are outside the ladder's pool (DECISIONS 14b), so no rung allowance caps a
 * step's price. It starts something only from the money tier Surplus up with the Economy rung On (CAEconomy.buyerOpen),
 * in the planner's offer and in act() (the planner stopped for the session); with the rung on Recommend only the summary
 * names the best project and none starts.
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

    /**
     * base: the value already coming from projects under way, so a plan made on top of them counts only what it adds.
     * 0.3.0.0 fix (C4 review): conds, today's conditions, and hpScale, the colony's CAEconomy.hazardPayScale, worked out once
     * for every end state the plan values.
     */
    static CATerraSim.Colony colony(final MarketAPI m, final CAData.Profile prof, final float base, final Set<String> conds, final float hpScale) {
        final Map<String, Boolean> can = new HashMap<String, Boolean>();
        return new CATerraSim.Colony() {
            // 0.3.0.0 (C4, research 03 F9): plus the hazard pay its growth no longer needs, for the months it still grows
            public float value(Set<String> after) { return CAConditions.change(m, after, prof, null) + CAEconomy.hazardPaySaved(m, conds, after, hpScale) - base; }
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
    static Map<String, CATme.Proj> busy(MarketAPI m) {
        Map<String, CATme.Proj> out = new HashMap<String, CATme.Proj>();
        for (Industry ind : m.getIndustries()) {
            if (!(CATme.isTerraforming(ind))) continue;
            Industry ti = ind;
            if (ti.isBuilding() || ti.isUpgrading() || CATme.ongoing(ti)) out.put(ind.getId(), CATme.ongoing(ti) ? CATme.project(ti) : null);
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
        Map<String, CATme.Proj> busy = busy(m);
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
        for (CATme.Proj pd : busy.values()) {
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
        float hp = CAEconomy.hazardPayScale(m);   // 0.3.0.0 fix (C4 review): once per plan
        float base = start == conds ? 0f : CAConditions.change(m, start, prof, null) + CAEconomy.hazardPaySaved(m, conds, start, hp);   // 0.3.0.0 (C4): as value() counts it
        CATerraSim.Result r = CATerraSim.plan(start, gas, rows, keep, avoid, CATme.buildCostMult(),
                CATme.removalCostMult(), s.surplusTerraform, colony(m, prof, base, conds, hp), rich);
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
        // marks first: with surplus terraforming off they never expired, and a marked hazard stayed protected for good (0.2.38.0)
        if (s.surplusTerraform >= 2) { d.enablers().clear(); d.enablerAt().clear(); }
        expireEnablers(d);
        if (s.surplusTerraform >= 2 || CASurplus.line(s) <= 0f) { CATerraSim.summary = null; return; }
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
        // 0.3.0.0 fix (C4 review 2): the Economy buyers' one gate (CAEconomy.buyerOpen): the summary says why none starts
        String closed = CAEconomy.closedWhy(s);
        if (closed != null && CATerraSim.summary != null) CATerraSim.summary += " None starts: " + closed + ".";
        // 0.3.0.0 (B3): the planner starts these from its one queue (offer), ranked with every other build; this keeps the plan,
        // the summary and the clean-up. With legacyPlanner (or the planner stopped for the session) it spends as in 0.2.45
        if (CAPlanner.inCharge()) return;
        if (closed != null) return;   // 0.3.0.0 fix (C4 review 2): act() under the same gate as the planner's offer
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
                if (CALog.once("tsu:" + key)) CALog.add(m.getName() + ": " + sp.getName() + " waits for next month's upkeep room");
                return 0f;
            }
            return buildStructure(m, k.q, sp, cost, fee, up) == null ? cost + fee : 0f;
        }
        if (!(CATme.isTerraforming(ind))) return 0f;
        Industry ti = ind;
        if (ti.isBuilding() || ti.isUpgrading() || CATme.ongoing(ti)) return 0f;
        CATme.Proj pd = project(m, ti, mv, key);
        if (pd == null) return 0f;
        float cost = CATerraform.projectCost(m, pd), fee = CABudget.fee(s, CATerraform.STANDARD_FEE);
        if (cost + fee > budget) { waits(m, key, what, cost + fee); return 0f; }
        return startProject(m, k.q, ti, pd, cost, fee) == null ? cost + fee : 0f;
    }

    /**
     * The project a move starts on an idle structure, or null: a TME project toggles its condition, so on a world that already
     * is the way the move wants it would undo it (pollution removed by the plan an hour ago, then added back by this): never
     * one that goes the wrong way. 0.3.0.0 (B3): shared by act() and the planner's offer.
     */
    static CATme.Proj project(MarketAPI m, Industry ti, CATerraSim.Move mv, String key) {
        if (!wantsChange(mv.add, m.hasCondition(mv.condition))) {
            planAt.remove(m.getId());
            CADiagnostics.LOG.info(CADiagnostics.TAG + m.getName() + ": surplus terraforming dropped '" + mv.label() + "': the colony already is that way; replanning");
            return null;
        }
        CATme.Proj pd = null;
        List<CATme.Proj> projects = CATme.projects(ti);
        if (projects != null) for (CATme.Proj x : projects) if (x.id.equals(mv.condition)) pd = x;
        if (pd == null || !CATme.canRun(ti, pd)) {
            if (CALog.once("tsn:" + key)) CALog.add(m.getName() + ": " + ti.getCurrentName() + " can't " + mv.label() + " there yet");
            return null;
        }
        return pd;
    }

    /** Builds a structure for a sequence, paid from the surplus; null when it started, else why not. 0.3.0.0 (B3): shared by act() and the planner. */
    static String buildStructure(MarketAPI m, CATerraSim.Seq q, IndustrySpecAPI sp, float cost, float fee, float up) {
        return buildStructure(m, q, sp, cost, fee, up, false);
    }

    /**
     * As above. 0.3.0.0 (C4, research 03 F6): budget: the month's budget pays (the price counts against it and against the
     * terraforming share), else surplus credits (only the fee counts against the month, as in 0.2.45.1).
     */
    static String buildStructure(MarketAPI m, CATerraSim.Seq q, IndustrySpecAPI sp, float cost, float fee, float up, boolean budget) {
        CASettings s = CAData.settings();
        CATerraSim.Move mv = q.moves.get(0);
        String key = m.getId() + ":" + mv.structure + ":" + mv.condition;
        CAEngine.Pick p = new CAEngine.Pick();
        p.id = mv.structure; p.name = sp.getName(); p.verb = "Build"; p.cost = cost; p.upkeep = up;
        String err = CABuild.start(m, p);
        if (err != null) {
            if (CALog.once("tse:" + key + ":" + err)) CALog.add(m.getName() + ": " + sp.getName() + " not started: " + err);
            return err;
        }
        paid(s, m, cost, fee, up, budget);   // CABuild.start took the price
        CAData.get().surplusBuilt().add(m.getId() + ":" + mv.structure);
        if (!CABudget.legacy(s)) CAMark.set(unusedKey(m.getId(), mv.structure), CAMark.now());   // 0.3.0.0 fix (C4 review): kept until its first project runs (takeDown)
        CALog.add(m.getName() + ": building " + sp.getName() + " (" + Misc.getDGSCredits(cost) + (budget ? ", from the month's budget" : "") + ") to " + q.text());
        return null;
    }

    /** Starts a project on an idle structure, paid from the surplus; null when it started, else why not. 0.3.0.0 (B3): shared by act() and the planner. */
    static String startProject(MarketAPI m, CATerraSim.Seq q, Industry ti, CATme.Proj pd, float cost, float fee) {
        return startProject(m, q, ti, pd, cost, fee, false);
    }

    /** As above. 0.3.0.0 (C4, research 03 F6): budget: the month's budget pays, as buildStructure. */
    static String startProject(MarketAPI m, CATerraSim.Seq q, Industry ti, CATme.Proj pd, float cost, float fee, boolean budget) {
        CASettings s = CAData.settings();
        CATerraSim.Move mv = q.moves.get(0);
        if (!CABreaker.allowed(ACTION)) return "paused for this session (see Diagnostics)";
        CATme.setProject(ti, pd);
        ti.startUpgrading();
        if (!CABreaker.check(ACTION, ti.isUpgrading() || CATme.ongoing(ti), m.getName())) return "it didn't take effect, so nothing was charged";
        CABudget.take(cost);   // 0.3.0.0 (C4): through CABudget's one line that takes credits (C1)
        paid(s, m, cost, fee, 0f, budget);
        CAMark.clear(unusedKey(m.getId(), mv.structure));   // 0.3.0.0 fix (C4 review): its first project ran
        if (mv.add && CATerraSim.ENABLERS.contains(mv.condition) && q.moves.size() > 1) {   // the plan leaves it until the deposit is in
            CAData.get().enablers().put(m.getId() + ":" + mv.condition, q.moves.get(1).condition);
            CAData.get().enablerAt().put(m.getId() + ":" + mv.condition, Global.getSector().getClock().getTimestamp());
        }
        CALog.add(m.getName() + ": " + mv.label() + " (" + Math.round(pd.buildTime) + " days, " + Misc.getDGSCredits(cost) + (budget ? " from the month's budget" : "")
                + ", " + CAEngine.valueText(q.gain) + (q.moves.size() > 1 ? ", then " + q.moves.get(1).label() : "") + ")");
        return null;
    }

    /**
     * 0.3.0.0 (C4): books a start whose price was already taken: from the month's budget, price and fee count against the month
     * and the price against the terraforming share; from surplus credits, only the fee counts against the month and the
     * price is booked on the money ladder's Economy rung.
     */
    static void paid(CASettings s, MarketAPI m, float cost, float fee, float up, boolean budget) {
        if (budget) {
            CABudget.charge(s, m.getId(), cost, fee, up);
            s.terraSpent += Math.max(0f, cost);
        } else {
            CABudget.charge(s, m.getId(), 0f, fee, up);   // the price came from the surplus, outside the monthly budget
            CAEconomy.spent(cost);
        }
    }

    /** 0.3.0.0 (C4): the fees of a whole sequence: its structures' construction fees and a project fee for each move. */
    static float seqFees(CASettings s, CATerraSim.Seq q) {
        float fees = 0f;
        for (String b : q.builds) {
            IndustrySpecAPI bs = spec(b);
            if (bs != null) fees += CABudget.constructionFee(s, bs.getCost());
        }
        for (int i = 0; i < q.moves.size(); i++) fees += CABudget.fee(s, CATerraform.STANDARD_FEE);
        return fees;
    }

    /**
     * 0.3.0.0 (C4, research 03 F6): where a step's money comes from now (CAEconomy.SURPLUS, BUDGET or NONE): surplus credits
     * when they cover it; the month's budget when the sequence pays back within 24 months, budgetOk, and the terraforming
     * budget room covers it. step: the step's price and fee. 0.3.0.0 fix (C4 review 2): no rung cap on surplus credits
     * (outside the ladder's pool, DECISIONS 14b).
     */
    static int source(CASettings s, CATerraSim.Seq q, float step, boolean budgetOk) {
        return CAEconomy.terraformSource(step, q.cost + seqFees(s, q), q.gain, CASurplus.amount(s), CAEconomy.terraformBudgetRoom(s), budgetOk);
    }

    // ---------------- 0.3.0.0 (WS-B3): the planner's actions ----------------

    /** The planner's name for these actions. */
    static final String SOURCE = "surplus terraforming";
    /** 0.3.0.0 fix (C4 review): a starter's reason when no money source covers the step now. */
    static final String NO_MONEY = "neither surplus credits nor the terraforming budget cover it now";

    /**
     * 0.3.0.0 (WS-B3, research 01 section 3 items 2 and 6, 02 section 13): each Auto colony's next surplus terraforming move as
     * an action in the planner's one queue, instead of spending on its own after the build pass: building its structure takes
     * the colony's construction queue and is ranked by NPV per construction day against the colony's other new buildings (a
     * 360-day structure never goes ahead of a better pick waiting there); a project on a structure that's built runs
     * alongside. Its NPV covers the whole sequence (CAValue.npv: the structure, the projects, the upkeep while they work, the
     * fees, over the horizon). What's worth doing is still your setting's call (Max output / Pays back in 10 years, CATerraSim),
     * so it's started by that rule after everything that pays, from surplus credits only, as in 0.2.45.1; while spending freely
     * a colony goes on to its next free structure the same day (followUp, up to PARALLEL). A structure the colony's own plan
     * has a step for today is left to the plan's terraforming (CATerraform runs after the planner).
     */
    public static List<CAAction> offer(List<MarketAPI> auto) {
        List<CAAction> out = new ArrayList<CAAction>();
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (s.surplusTerraform >= 2 || CASurplus.line(s) <= 0f) return out;
        // 0.3.0.0 (C4): none with the money ladder's Economy rung Off. 0.3.0.0 fix (C4 review 2): nor below the money tier Surplus
        // or with the rung on Recommend only (CAEconomy.buyerOpen, as act() and the other Economy buyers)
        if (!CAEconomy.buyerOpen(s)) return out;
        float h = CAHorizon.months();
        boolean rich = CASurplus.rich(s);
        for (MarketAPI m : auto) {
            if (CAHypershunt.is(m) || CAEngine.isOutpost(m) || m.getPlanetEntity() == null) continue;
            try {
                CAData.Colony c = d.colony(m.getId());
                CATerraSim.Result r = plan(m, c, s);
                if (r.best == null) continue;
                CAAction a = action(m, c, r.best, s, h, rich, 0);
                if (a != null) out.add(a);
            } catch (Throwable t) { CAStep.warn("planner", "surplus terraforming offer failed at " + m.getName(), t); }
        }
        return out;
    }

    /** The next move of a sequence as an action, or null when it can't start today. */
    static CAAction action(MarketAPI m, CAData.Colony c, CATerraSim.Seq q, CASettings s, float h, boolean rich, int round) {
        CATerraSim.Move mv = q.moves.get(0);
        if (c.locks.contains(mv.structure)) return null;
        String key = m.getId() + ":" + mv.structure + ":" + mv.condition;
        CAAction a = new CAAction();
        a.market = m; a.marketId = m.getId(); a.source = SOURCE; a.payload = q; a.round = round;
        a.why = q.text();
        // the whole sequence's construction days and fees, for its NPV
        float days = 0f, fees = 0f;
        for (String b : q.builds) {
            IndustrySpecAPI bs = spec(b);
            if (bs != null) { days += bs.getBuildTime(); fees += CABudget.constructionFee(s, bs.getCost()); }
        }
        // 0.3.0.9: a project's table days times TME's build time multiplier, as TME runs them (CATme.buildDays)
        float timeMult = CATme.buildTimeMult();
        for (CATerraSim.Move x : q.moves) { days += CATme.buildDays(x.days, timeMult); fees += CABudget.fee(s, CATerraform.STANDARD_FEE); }
        Industry ind = m.getIndustry(mv.structure);
        if (ind == null) {
            IndustrySpecAPI sp = spec(mv.structure);
            if (sp == null) return null;
            a.kind = CAAction.Kind.BUILD; a.id = mv.structure; a.name = sp.getName();
            a.cost = sp.getCost(); a.fee = CABudget.constructionFee(s, a.cost); a.days = sp.getBuildTime(); a.upkeep = CAEngine.baseUpkeep(m, sp);
            a.usesQueue = true;
            a.usesSlot = CAEngine.isIndustry(mv.structure);
            a.tombstone = CAPlanner.tombstoneDays(m.getId(), mv.structure);   // taken down here lately: not built again within the year (B4)
            a.starter = (x) -> {
                if (Misc.getCurrentlyBeingConstructed(x.market) != null) return "a new building is already under construction here";
                IndustrySpecAPI xs = spec(x.id);
                if (xs == null) return "not supported";
                // 0.3.0.0 (C4, research 03 F6): surplus credits, or the month's budget for a sequence that pays back within 24 months
                int src = source(CAData.settings(), (CATerraSim.Seq) x.payload, x.total(), x.fromBudget);
                if (src == CAEconomy.NONE) { x.failWaits = true; return NO_MONEY; }
                return buildStructure(x.market, (CATerraSim.Seq) x.payload, xs, x.cost, x.fee, x.upkeep, src == CAEconomy.BUDGET);
            };
        } else {
            if (!(CATme.isTerraforming(ind))) return null;
            Industry ti = ind;
            if (ti.isBuilding() || ti.isUpgrading() || CATme.ongoing(ti)) return null;
            if (planWants(m, c, ti)) return null;   // the plan's own step goes first (CATerraform, later today)
            CATme.Proj pd = project(m, ti, mv, key);
            if (pd == null) return null;
            a.kind = CAAction.Kind.PROJECT; a.id = mv.condition; a.name = ti.getCurrentName() + ": " + mv.label(); a.building = mv.structure;
            a.cost = CATerraform.projectCost(m, pd); a.fee = CABudget.fee(s, CATerraform.STANDARD_FEE); a.days = pd.buildTime;   // 0.3.0.9: TME's multiplier is in buildTime (CATme.wrap)
            a.starter = (x) -> {
                Industry xi = x.market.getIndustry(x.building);
                if (!(CATme.isTerraforming(xi))) return "the structure is gone";
                Industry xt = xi;
                if (xt.isBuilding() || xt.isUpgrading() || CATme.ongoing(xt)) return "the structure is busy";
                CATerraSim.Seq xq = (CATerraSim.Seq) x.payload;
                CATme.Proj xp = project(x.market, xt, xq.moves.get(0), x.market.getId() + ":" + x.building + ":" + x.id);
                if (xp == null) return "that project can't start there now";
                int src = source(CAData.settings(), xq, x.total(), x.fromBudget);   // 0.3.0.0 (C4, F6)
                if (src == CAEconomy.NONE) { x.failWaits = true; return NO_MONEY; }
                return startProject(x.market, xq, xt, xp, x.cost, x.fee, src == CAEconomy.BUDGET);
            };
        }
        a.worth = q.gain;
        a.npv = CAValue.npv(q.gain, h, days, q.cost, fees);
        a.basis = Math.max(1f, q.cost + fees);
        a.byRule = true;   // your surplus terraforming setting decides what's worth doing with surplus credits, as in 0.2.45.1
        a.fromBudget = false; a.fromSurplus = true; a.chargeAll = false;   // from the surplus; only the fee counts against the month's budget
        // 0.3.0.0 (C4, research 03 F6): when surplus credits don't cover this step, a sequence that pays back within 24 months
        // may use the month's budget (the terraforming share of it and its savings); then the whole price counts against it
        if (source(s, q, a.total(), CAEconomy.terraformBudgetOk(s)) == CAEconomy.BUDGET) { a.fromBudget = true; a.chargeAll = true; }
        a.upkeepCap = CASurplus.upkeepCap(m, s);
        a.rescore = (x) -> rescore(x);
        if (rich && round < PARALLEL - 1) a.followUp = (x) -> followUp(x);
        return a;
    }

    /** Re-scores from the colony's plan made again: still the same first move, else dropped. */
    static boolean rescore(CAAction a) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        CAData.Colony c = d.colony(a.marketId);
        CATerraSim.Result r = plan(a.market, c, s);
        if (r.best == null) return false;
        CAAction f = action(a.market, c, r.best, s, CAHorizon.months(), CASurplus.rich(s), a.round);
        if (f == null || !f.key().equals(a.key())) return false;
        CAPlanner.copyNumbers(f, a);
        a.chargeAll = f.chargeAll;   // 0.3.0.0 (C4): copyNumbers leaves it; the money source can change between scores
        a.starter = f.starter;
        return true;
    }

    /** Spending freely: the colony's next move on another free structure, planned on top of what just started. */
    static List<CAAction> followUp(CAAction started) {
        List<CAAction> out = new ArrayList<CAAction>();
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (!CASurplus.rich(s) || started.market == null) return out;
        CAData.Colony c = d.colony(started.marketId);
        CATerraSim.Result r = plan(started.market, c, s);
        if (r.best == null) return out;
        CAAction a = action(started.market, c, r.best, s, CAHorizon.months(), true, started.round + 1);
        if (a != null && !a.key().equals(started.key())) out.add(a);
        return out;
    }

    /**
     * Whether the colony's own plan has an open terraforming step this idle structure can run now: the same test CATerraform
     * makes for its first choice. In 0.2.45 the plan's terraforming ran first and surplus terraforming took what it left idle;
     * the planner runs before it, so it leaves these structures to it.
     */
    static boolean planWants(MarketAPI m, CAData.Colony c, Industry ti) {
        List<CATme.Proj> projects;
        try { projects = CATme.projects(ti); } catch (Throwable t) { return false; }
        if (projects == null) return false;
        boolean pollutionBack = CAConditions.pollutionReturns(m);
        for (CAPlan.Step st : CAPlan.build(m, c.profile).steps) {
            if (st.kind == CAPlan.Kind.BUILD) continue;
            try { if (st.done(m)) continue; } catch (Throwable t) { continue; }
            if (!ti.getId().equals(CAWorldPlanner.structureFor(st.id)) && st.kind == CAPlan.Kind.COND) continue;
            for (CATme.Proj pd : projects) {
                if (!pd.id.equals(st.id)) continue;
                boolean adding = !m.hasCondition(pd.id);
                if ((st.kind == CAPlan.Kind.COND) != adding) continue;
                if (!adding && pd.id.equals("pollution") && pollutionBack) continue;
                try { if (CATme.canRun(ti, pd)) return true; } catch (Throwable ignored) { }
            }
        }
        return false;
    }

    /**
     * Takes down a structure surplus terraforming built once it's idle and no worthwhile sequence starts with it, unless the
     * colony's plan has it as a step or you locked it. TME's conditions stay; its upkeep stops. 0.3.0.0 fix (C4 review): one
     * whose first project hasn't run yet stays until UNUSED_HOLD_DAYS after its build time (unusedHeld).
     */
    static void takeDown(MarketAPI m, CATerraSim.Result r, CAData.Colony c) {
        Set<String> built = CAData.get().surplusBuilt();
        String prefix = m.getId() + ":";
        boolean legacy = CABudget.legacy(CAData.settings());
        for (String key : new ArrayList<String>(built)) {
            if (!key.startsWith(prefix)) continue;
            String id = key.substring(prefix.length());
            Industry ind = m.getIndustry(id);
            if (ind == null) { built.remove(key); CAMark.clear(unusedKey(m.getId(), id)); continue; }   // gone already
            if (ind.isBuilding() || ind.isUpgrading() || (CATme.isTerraforming(ind) && CATme.ongoing(ind))) continue;
            if (r.wanted.contains(id) || c.locks.contains(id)) continue;
            if (id.equals("tme_geomorphology_station") && CAConditions.pollutionReturns(m)) continue;   // the forge's upgrade brings pollution back: it'll be needed
            boolean planned = false;
            for (CAPlan.Step st : CAPlan.build(m, c.profile).steps) if (st.kind == CAPlan.Kind.BUILD && st.id.equals(id)) planned = true;
            if (planned) { built.remove(key); CAMark.clear(unusedKey(m.getId(), id)); continue; }   // the plan wants it now: it's the plan's to keep
            // 0.3.0.0 fix (C4 review): built for a sequence that dropped out of the plan before its first project ran (a value
            // that changed for a while): it stays for the hold, so it isn't built and taken down unused
            Long since = legacy ? null : CAMark.at(unusedKey(m.getId(), id));
            if (since != null) {
                IndustrySpecAPI sp = spec(id);
                float left = unusedHeld(CAMark.days(since, CAMark.now()), sp == null ? 0f : sp.getBuildTime());
                if (left > 0f) {
                    if (CALog.once("tsk:" + m.getId() + ":" + id + ":" + since))
                        CALog.quiet(m.getName() + ": idle " + ind.getCurrentName() + " kept: its first project hasn't run yet (" + Math.round(left) + " days left)");
                    continue;
                }
            }
            float up = ind.getUpkeep().getModifiedValue();
            CAStores.emptyInto(m, ind);   // its AI core and item to Autopilot Storage (with no mode the game would delete them)
            m.removeIndustry(id, com.fs.starfarer.api.campaign.econ.MarketAPI.MarketInteractionMode.REMOTE, false);
            CATombstone.add(m.getId(), id, CATombstone.TERRAFORM, CATombstone.sunk(m.getId(), id));   // 0.3.0.0 (B4, research 02 section 18): every removal leaves a year's mark
            built.remove(key);
            CAMark.clear(unusedKey(m.getId(), id));
            CALog.add(m.getName() + ": removed idle " + ind.getCurrentName() + " (-" + Misc.getDGSCredits(up) + "/month)");
        }
    }

    /** 0.3.0.0 fix (C4 review): days a structure surplus terraforming built stays idle after its build time while its first project hasn't run. */
    static final float UNUSED_HOLD_DAYS = 90f;
    /** 0.3.0.0 fix (C4 review): CAMark prefix: "tfnew:<marketId>|<structure>" from its build until its first surplus project starts. */
    static final String UNUSED = "tfnew:";

    static String unusedKey(String marketId, String structure) { return UNUSED + marketId + "|" + structure; }

    /** Pure, 0.3.0.0 fix (C4 review): days an unused structure still stays: its build time plus UNUSED_HOLD_DAYS from the build, less its age (0: it may go). */
    static float unusedHeld(float ageDays, float buildDays) {
        return Math.max(0f, Math.max(0f, buildDays) + UNUSED_HOLD_DAYS - Math.max(0f, ageDays));
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
        if (CALog.once("tsw:" + key)) CALog.add(m.getName() + ": " + what + " waits for " + Misc.getDGSCredits(need) + " of surplus");
    }
}
