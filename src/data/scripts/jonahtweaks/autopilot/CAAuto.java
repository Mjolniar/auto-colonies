package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/** Approve and Auto modes: one pass per colony every 7 days or when a build finishes, funded empire-wide by score per credit. */
public class CAAuto {
    static final Map<String, Boolean> wasBuilding = new HashMap<String, Boolean>();
    static final Map<String, Float> nextPass = new HashMap<String, Float>();
    static float lastNow = 0f;

    static class Cand {
        MarketAPI m; CAData.Colony c; CAEngine.Pick p; float total;
    }

    public static void reset() { wasBuilding.clear(); nextPass.clear(); lastNow = 0f; }

    public static void runPass(float now) {
        lastNow = now;
        CAData d = CAData.get();
        CASettings s = d.settings;
        CABudget.rollMonth(s);
        List<Cand> cands = new ArrayList<Cand>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if (c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) continue;
            boolean building = Misc.getCurrentlyBeingConstructed(m) != null;
            Boolean was = wasBuilding.put(m.getId(), building);
            Float next = nextPass.get(m.getId());
            boolean justFinished = was != null && was;
            if (!justFinished && next != null && now < next) continue;
            nextPass.put(m.getId(), now + 7f);
            CAEngine.Result r;
            try { r = CAEngine.evaluate(m, CAThreat.effective(m, c.profile), d.minStability); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "pass failed at " + m.getName(), t); continue; }
            if (r.picks.isEmpty()) continue;
            // upgrades run alongside new construction, so take the best of each
            CAEngine.Pick bestUp = null, bestNew = null;
            for (CAEngine.Pick q : r.picks) {
                if (q.upgrade && bestUp == null) bestUp = q;
                if (!q.upgrade && bestNew == null && !building) bestNew = q;
            }
            if (c.mode == CAData.Mode.APPROVE) {
                CAEngine.Pick p = bestNew != null && (bestUp == null || bestNew.score >= bestUp.score) ? bestNew : bestUp;
                if (p != null) propose(m, c, p, "waiting for your approval");
                continue;
            }
            for (CAEngine.Pick p : new CAEngine.Pick[]{bestUp, bestNew}) {
                if (p == null) continue;
                Cand k = new Cand();
                k.m = m; k.c = c; k.p = p; k.total = p.cost + CABudget.constructionFee(s, p.cost);
                cands.add(k);
            }
            if (building && bestUp == null) nextPass.put(m.getId(), now + 1f);   // re-check soon for the new build
        }
        Collections.sort(cands, new Comparator<Cand>() {
            public int compare(Cand a, Cand b) {
                return Float.compare(b.p.score / Math.max(1f, b.total), a.p.score / Math.max(1f, a.total));
            }
        });
        for (Cand k : cands) {
            if (k.p.cost > s.perProjectCap) { propose(k.m, k.c, k.p, "over the per-project cap, needs your approval"); continue; }
            if (k.p.measured && s.measuredMode == 1) { propose(k.m, k.c, k.p, "a mod building scored from measurements, needs your approval"); continue; }
            String key = k.m.getId() + ":" + k.p.id;
            if (k.total > CABudget.available(s)) {
                if (CALog.once(key)) CALog.add(k.m.getName() + ": " + k.p.name + " waits for budget ("
                        + Misc.getDGSCredits(k.total) + " needed, " + Misc.getDGSCredits(CABudget.available(s)) + " left this month)");
                continue;
            }
            if (s.upkeepAdded + k.p.upkeep > s.newUpkeepCap) {
                if (CALog.once(key + ":upkeep")) CALog.add(k.m.getName() + ": " + k.p.name + " waits for next month (new upkeep cap reached)");
                continue;
            }
            execute(k.m, k.c, k.p, "Auto");
        }
        try { CAItems.ensureStorageTabs(); CAItems.run(); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "item, core or administrator placement failed", t); }
        List<MarketAPI> auto = CAItems.autoMarkets();
        if (!auto.isEmpty() && Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy")) {
            try { CATerraform.run(auto); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "terraforming planner failed", t); }
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
        }
        try { CAPolicies.run(auto); }
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
    }

    static void propose(MarketAPI m, CAData.Colony c, CAEngine.Pick p, String why) {
        if (p.id.equals(c.pendingId)) return;
        c.pendingId = p.id; c.pendingName = p.verb + " " + p.name; c.pendingUpgrade = p.upgrade; c.pendingCost = p.cost;
        c.pendingNotified = true;
        CALog.add(m.getName() + ": proposes " + c.pendingName + " for " + Misc.getDGSCredits(p.cost) + ", " + why, true);
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
        CALog.add(m.getName() + ": " + who + " started " + p.verb.toLowerCase() + " " + p.name + " for "
                + Misc.getDGSCredits(p.cost) + (fee > 0 ? " + " + Misc.getDGSCredits(fee) + " fee" : ""), true);
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
