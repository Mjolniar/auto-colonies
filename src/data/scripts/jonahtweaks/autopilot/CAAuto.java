package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/** Approve and Auto modes: one pass per colony every 7 days or when a build finishes, funded empire-wide by score per credit. */
public class CAAuto {
    /** Picks per colony a pass: the best upgrades and new builds, the rest standing by if the best has to wait. */
    static final int UPS = 2, NEWS = 3;
    static final Map<String, Boolean> wasBuilding = new HashMap<String, Boolean>();
    static final Map<String, Float> nextPass = new HashMap<String, Float>();
    static float lastNow = 0f;

    static class Cand {
        MarketAPI m; CAData.Colony c; CAEngine.Pick p; float total;
    }

    public static void reset() { wasBuilding.clear(); nextPass.clear(); lastTrace.clear(); lastNow = 0f; }

    public static void runPass(float now) {
        lastNow = now;
        CAData d = CAData.get();
        CASettings s = d.settings;
        CABudget.rollMonth(s);
        CABudget.migrateUpkeepCap(s);
        try { CACalibrate.run(now); }   // weekly: compare estimates with what your buildings really do
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "calibration failed", t); }
        try { CASiege.update(d); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "siege check failed", t); }
        CAAttacks.apply(s);
        CACrisis.daily(d, s);   // inspection orders, and a log line when a colony crisis is picked
        try { CASurplus.daily(s); }   // the focus world for credits over the surplus line
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus investment step failed", t); }
        try { CAProjects.update(); }   // before any plan is read: a special project's producers are plan steps
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "special project supply step failed", t); }
        List<Cand> cands = new ArrayList<Cand>();
        boolean rich = CASurplus.rich(s);
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if (c.buildout && c.mode != CAData.Mode.AUTO) CABuildout.stop(m, c, "the colony left Auto");
            if (c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) continue;
            boolean building = Misc.getCurrentlyBeingConstructed(m) != null;
            Boolean was = wasBuilding.put(m.getId(), building);
            Float next = nextPass.get(m.getId());
            boolean justFinished = was != null && was;
            if (!justFinished && next != null && now < next) continue;
            boolean siege = CASiege.held(m);
            // under siege, look daily so it carries on as soon as it ends; spending freely, daily too, so the surplus keeps moving
            nextPass.put(m.getId(), now + (siege || rich ? 1f : 7f));
            if (c.buildout && c.mode == CAData.Mode.AUTO) {
                if (siege) continue;   // a buildout builds no defenses, so it waits out the siege
                try { if (CABuildout.pass(m, c, now, building)) continue; }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "fast buildout pass failed at " + m.getName(), t); continue; }
            }
            CAEngine.Result r;
            try {
                CAData.Profile prof = CAThreat.effective(m, c.profile);   // keeps the threat state current
                r = CAEngine.evaluate(m, siege ? CAData.Profile.SECURITY : prof, d.minStability);
            }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "pass failed at " + m.getName(), t); continue; }
            if (r.picks.isEmpty()) continue;
            // upgrades run alongside new construction; the next best of each stand by in case the best has to wait (budget,
            // caps), so one held-back build doesn't stop the colony
            List<CAEngine.Pick> ups = new ArrayList<CAEngine.Pick>(), news = new ArrayList<CAEngine.Pick>();
            for (CAEngine.Pick q : r.picks) {
                if (!CAEngine.worthwhile(q)) continue;   // never the least-bad option: only what pays for itself
                if (siege && !CASiege.defensive(q)) continue;   // under siege only defenses are built
                if (q.upgrade) { if (ups.size() < (rich ? UPS + 2 : UPS)) ups.add(q); }
                else if (!building && news.size() < NEWS) news.add(q);
            }
            if (ups.isEmpty() && news.isEmpty()) trace(m, r, building, now);
            if (c.mode == CAData.Mode.APPROVE) {
                CAEngine.Pick bestUp = ups.isEmpty() ? null : ups.get(0), bestNew = news.isEmpty() ? null : news.get(0);
                CAEngine.Pick p = bestNew != null && (bestUp == null || bestNew.score >= bestUp.score) ? bestNew : bestUp;
                if (p != null) propose(m, c, p, "waiting for your approval");
                continue;
            }
            List<CAEngine.Pick> all = new ArrayList<CAEngine.Pick>(ups);
            all.addAll(news);
            for (CAEngine.Pick p : all) {
                Cand k = new Cand();
                k.m = m; k.c = c; k.p = p; k.total = p.cost + CABudget.constructionFee(s, p.cost);
                cands.add(k);
            }
            if (building && ups.isEmpty()) nextPass.put(m.getId(), now + 1f);   // re-check soon for the new build
        }
        Collections.sort(cands, new Comparator<Cand>() {
            public int compare(Cand a, Cand b) {
                return Float.compare(b.p.score / Math.max(1f, b.total), a.p.score / Math.max(1f, a.total));
            }
        });
        // a plan step whose upkeep is over the whole cap only fits as the month's first new upkeep, so it goes first
        List<Cand> big = new ArrayList<Cand>();
        for (Cand k : cands) if (k.p.planBonus > 0f && k.p.upkeep > CASurplus.upkeepCap(k.m, s)) big.add(k);
        cands.removeAll(big);
        cands.addAll(0, big);
        // one new building and one upgrade per colony a pass; spending freely, up to three upgrades (never two on one building)
        Set<String> startedNew = new HashSet<String>();
        Map<String, Integer> startedUp = new HashMap<String, Integer>();
        Set<Object> upgrading = new HashSet<Object>();
        int maxUps = rich ? 3 : 1;
        for (Cand k : cands) {
            Integer ups = startedUp.get(k.m.getId());
            if (k.p.upgrade ? (ups != null && ups >= maxUps) || upgrading.contains(k.p.existing) : startedNew.contains(k.m.getId())) continue;
            float cap = CASurplus.projectCap(k.m, s);   // the focus world may go past it with surplus credits
            if (k.p.cost > cap) { propose(k.m, k.c, k.p, "over the per-project cap, needs your approval"); continue; }
            if (k.p.measured && s.measuredMode == 1) { propose(k.m, k.c, k.p, "a mod building scored from measurements, needs your approval"); continue; }
            if (k.p.confidence < 0.9f && k.p.cost > 0.5f * cap) {
                propose(k.m, k.c, k.p, "scored from an estimate rather than a game formula and over half the per-project cap, needs your approval");
                continue;
            }
            String key = k.m.getId() + ":" + k.p.id;
            float avail = Math.min(Math.max(0f, CABuild.credits() - s.reserve), CABudget.available(s) + CASurplus.extra(k.m, s));
            if (k.total > avail) {
                if (CALog.once(key)) CALog.quiet(k.m.getName() + ": " + k.p.name + " waits for budget (" + Misc.getDGSCredits(k.total) + ", "
                        + Misc.getDGSCredits(avail) + " left)");
                continue;
            }
            float upCap = CASurplus.upkeepCap(k.m, s);
            if (!CABudget.upkeepFits(s.upkeepAdded, k.p.upkeep, upCap, k.p.planBonus > 0f)) {
                if (CALog.once(key + ":upkeep")) CALog.quiet(k.m.getName() + ": " + k.p.name + " waits for next month's upkeep room ("
                        + Misc.getDGSCredits(k.p.upkeep) + " of " + Misc.getDGSCredits(upCap) + ")");
                continue;
            }
            if (execute(k.m, k.c, k.p, "Auto") == null) {
                if (!k.p.upgrade) startedNew.add(k.m.getId());
                else { startedUp.put(k.m.getId(), (ups == null ? 0 : ups) + 1); upgrading.add(k.p.existing); }
            }
        }
        List<MarketAPI> allAuto = CAItems.autoMarkets();
        List<MarketAPI> auto = CASiege.free(allAuto);   // colonies under siege are left as they are
        try { CAItems.ensureStorageTabs(); CAItems.run(auto); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "item, core or administrator placement failed", t); }
        if (!auto.isEmpty() && Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy")) {
            try { CATerraform.run(auto); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "terraforming planner failed", t); }
            try { CATerraformSurplus.run(auto); }   // after the plan's own projects, so it only takes structures they leave idle
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus terraforming failed", t); }
        }
        if (Global.getSettings().getModManager().isModEnabled(CAResearch.MOD)) {
            try { CASurplusResearch.run(auto); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus research failed", t); }
            try { CAWonders.run(); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "wonders and gates step failed", t); }
        }
        if (!auto.isEmpty() && Global.getSettings().getModManager().isModEnabled("IndEvo")) {
            try { CAIndEvo.run(auto); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Industrial Evolution automation failed", t); }
            try { CACourier.run(auto); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Courier Port shipping failed", t); }
        }
        if (Global.getSettings().getModManager().isModEnabled("nexerelin")) {
            List<MarketAPI> managed = new ArrayList<MarketAPI>();
            for (MarketAPI m : Misc.getPlayerMarkets(false)) if (d.colony(m.getId()).mode != CAData.Mode.OFF) managed.add(m);
            try { CADefense.run(managed); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "emergency defense step failed", t); }
            if (CABreaker.allowed(CASpies.MOD + ":agents")) {
                boolean ok = true;
                try { CASpies.run(d, s); }
                catch (Throwable t) { ok = false; CADiagnostics.LOG.warn(CADiagnostics.TAG + "agent step failed", t); }
                CABreaker.check(CASpies.MOD + ":agents", ok, "Nexerelin agents");
            }
            try { CAAutonomy.run(d, s); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "emergency autonomy step failed", t); }
        }
        try { CAPolicies.run(allAuto); }   // holds changes under siege itself, but still switches off in an emergency
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony policy step failed", t); }
        try { CAStory.run(auto); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story point step failed", t); }
        if (Global.getSettings().getModManager().isModEnabled(CASettlement.MOD)) {
            try { CASettlement.run(d, s); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "settlement step failed", t); }
        }
        if (Global.getSettings().getModManager().isModEnabled(CAAugments.MOD)) {
            try { CAAugments.run(d, s, now); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment step failed", t); }
        }
        if (Global.getSettings().getModManager().isModEnabled(CAContracts.MOD)) {
            try { CAContracts.run(d, s); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "trade contract step failed", t); }
        }
        if (Global.getSettings().getModManager().isModEnabled(CAHypershunt.MOD)) {
            try { CAHypershunt.run(d, s); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt step failed", t); }
            try { CAMegastructures.run(d, s); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "megastructure step failed", t); }
            List<MarketAPI> managed = new ArrayList<MarketAPI>();
            for (MarketAPI m : Misc.getPlayerMarkets(false)) if (d.colony(m.getId()).mode != CAData.Mode.OFF) managed.add(m);
            try { CADevelopment.run(managed); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Floating City check failed", t); }
            try { CAResearch.run(d, s); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "research step failed", t); }
        }
        if (CATasc.enabled()) {
            try { CATasc.run(CASiege.free(Misc.getPlayerMarkets(false))); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "TASC terraforming step failed", t); }
        }
    }

    static final Map<String, Float> lastTrace = new HashMap<String, Float>();
    static final float TRACE_DAYS = 30f;

    /**
     * Why a colony starts nothing: its best three options with how each score is made up, and the best one that only lacks
     * an industry slot. Written to starsector.log (not the panel log) at most once a month per colony, so a stalled
     * colony can be explained from the log alone.
     */
    static void trace(MarketAPI m, CAEngine.Result r, boolean building, float now) {
        Float last = lastTrace.get(m.getId());
        if (last != null && now - last < TRACE_DAYS) return;
        lastTrace.put(m.getId(), now);
        StringBuilder b = new StringBuilder(m.getName()).append(": nothing worth starting");
        if (CASurplus.isFocus(m)) b.append(" (focus world, ").append(Misc.getDGSCredits(CASurplus.amount(CAData.settings()))).append(" surplus)");
        if (building) b.append("; a new building is under construction, so only upgrades count");
        if (r.blocked != null) b.append("; ").append(r.blocked);
        b.append("; ").append(r.picks.size()).append(" options");
        for (int i = 0; i < Math.min(3, r.picks.size()); i++) b.append(i == 0 ? ", best: " : "; ").append(scoreText(r.picks.get(i)));
        if (r.bestNoSlot != null) b.append("; with a free industry slot: ").append(scoreText(r.bestNoSlot)).append(" (").append(r.noSlot).append(" need a slot)");
        if (r.locked > 0) b.append("; ").append(r.locked).append(" locked by research");
        CADiagnostics.LOG.info(CADiagnostics.TAG + b);
    }

    static String scoreText(CAEngine.Pick p) {
        return p.verb + " " + p.name + " " + Math.round(p.score) + " (worth " + Math.round(p.worth) + ", plan " + Math.round(p.planBonus)
                + ", follow-through " + Math.round(p.followThrough) + ", price -" + Math.round(p.buildCost) + ", upkeep " + Math.round(p.upkeep) + "/month)";
    }

    static void propose(MarketAPI m, CAData.Colony c, CAEngine.Pick p, String why) {
        if (p.id.equals(c.pendingId)) return;
        c.pendingId = p.id; c.pendingName = p.verb + " " + p.name; c.pendingUpgrade = p.upgrade; c.pendingCost = p.cost;
        c.pendingNotified = true;
        CALog.add(m.getName() + ": proposes " + c.pendingName + " for " + Misc.getDGSCredits(p.cost) + ", " + why);
    }

    /** Starts a pick, charging the fee and counting it against the budget. Returns null on success. */
    static String execute(MarketAPI m, CAData.Colony c, CAEngine.Pick p, String who) {
        CASettings s = CAData.settings();
        float fee = CABudget.constructionFee(s, p.cost);
        if (CABuild.credits() < p.cost + fee) return "Not enough credits for the build plus the " + Misc.getDGSCredits(fee) + " fee";
        String err = CABuild.start(m, p);
        if (err != null) { CALog.add(m.getName() + ": could not start " + p.name + ": " + err); return err; }
        CABudget.charge(s, p.cost, fee, p.upkeep);
        CALog.forget(m.getId() + ":" + p.id);
        CALog.forget(m.getId() + ":" + p.id + ":upkeep");
        CAData.get().clearPending(c);
        wasBuilding.put(m.getId(), true);
        CALog.add(m.getName() + ": " + (p.upgrade ? "upgrading to " : "building ") + p.name + " (" + Misc.getDGSCredits(p.cost) + ")"
                + ("Auto".equals(who) ? "" : ", " + who));
        return null;
    }

    /** Approves a colony's pending proposal: re-checks it is still the right pick, then starts it. */
    public static String approve(MarketAPI m) {
        CAData d = CAData.get();
        CAData.Colony c = d.colony(m.getId());
        if (c.pendingId == null) return "Nothing is waiting for approval";
        CAEngine.Result r = CAEngine.evaluate(m, CAThreat.effective(m, c.profile), d.minStability);
        if (r.blocked != null && !c.pendingUpgrade) return r.blocked;
        for (CAEngine.Pick p : r.picks) {
            if (p.id.equals(c.pendingId) && p.upgrade == c.pendingUpgrade) return execute(m, c, p, "Approved:");
        }
        d.clearPending(c);
        return "That proposal no longer applies; cleared it";
    }

    public static void reject(MarketAPI m) {
        CAData d = CAData.get();
        CAData.Colony c = d.colony(m.getId());
        if (c.pendingName != null) CALog.add(m.getName() + ": rejected " + c.pendingName);
        d.clearPending(c);
        nextPass.put(m.getId(), lastNow + 30f);
    }

    public static MarketAPI market(String id) {
        try { return Global.getSector().getEconomy().getMarket(id); } catch (Throwable t) { return null; }
    }
}
