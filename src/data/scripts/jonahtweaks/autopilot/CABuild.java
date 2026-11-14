package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Map;

/**
 * Starts a recommended build or upgrade exactly like the build menu: one at a time, paid from your credits. With
 * Building Menu Overhaul (Ashes of the Domain) its research locks and upgrade paths apply too.
 */
public class CABuild {

    public static float credits() {
        return Global.getSector().getPlayerFleet().getCargo().getCredits().get();
    }

    /**
     * Industry slots in use (0.2.44.1): the game's count (Misc.getNumIndustries counts a structure being upgraded only along
     * its vanilla upgrade, spec.getUpgrade) corrected by the upgrade actually under way, recorded when Autopilot started it:
     * Ashes of the Domain's other upgrade targets weren't counted, so Zenemosrae started Biolab -> Grey Industries, Junkyard
     * Forge -> Scrapyard and Trade Outpost -> Commerce in one pass on one free slot and ended up 5 of 3.
     * 0.3.0.0: the only slot counter (research 07 M12): the build gate, the planner and CAOverLimit all count with it, so what
     * one calls over the limit the others do too.
     */
    public static int usedSlots(MarketAPI m) {
        int n = Misc.getNumIndustries(m);
        Map<String, String> rec;
        try { rec = CAData.get().upgradeTargets(); } catch (Throwable t) { rec = new java.util.HashMap<String, String>(); }
        for (Industry ind : m.getIndustries()) {
            String key = m.getId() + "|" + ind.getId();
            if (!ind.isUpgrading()) { rec.remove(key); continue; }
            if (ind.isIndustry()) continue;
            String vanilla = ind.getSpec().getUpgrade(), target = rec.containsKey(key) ? rec.get(key) : vanilla;
            n += slotFix(vanilla != null && CAEngine.isIndustry(vanilla), target != null && CAEngine.isIndustry(target));
        }
        return n;
    }

    /** The correction for one structure being upgraded: +1 when it becomes an industry the game didn't count, -1 the other way. */
    static int slotFix(boolean gameCounts, boolean becomesIndustry) { return (becomesIndustry ? 1 : 0) - (gameCounts ? 1 : 0); }

    /** Free industry slots, counting upgrades into industries under way. */
    public static int freeSlots(MarketAPI m) { return Misc.getMaxIndustries(m) - usedSlots(m); }

    /** Whether starting this pick adds an industry: a new one, or an upgrade that turns a structure into one. */
    static boolean takesSlot(MarketAPI m, CAEngine.Pick p) {
        try {
            com.fs.starfarer.api.loading.IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(p.id);
            if (spec == null || !spec.getNewPluginInstance(m).isIndustry()) return false;
            return !p.upgrade || p.existing == null || !p.existing.isIndustry();
        } catch (Throwable t) { return false; }
    }

    /** Returns null on success, or the reason it couldn't start. */
    public static String start(MarketAPI m, CAEngine.Pick p) {
        if (!p.upgrade && Misc.getCurrentlyBeingConstructed(m) != null) return "A new building is already under construction here. Upgrades can still run";
        float price = CASync.buildPrice(p.cost);   // 0.3.6.0 story mode: Free construction
        if (credits() < price) return "Not enough credits (" + Misc.getDGSCredits(price) + " needed)";
        if (!CASync.mayBuild(p.id)) return "Stations is off on the Story page.";
        if (!CADiagnostics.usable(p.id)) return "Not supported by Colony Autopilot";
        // 0.3.0.0: the one stability rule (CAStability) as the last gate for a new building or upgrade: what it costs once built must
        // leave the colony at or above the floor. The planner already holds such picks back; this catches any path that doesn't
        if (p.fx != null && p.fx.length > 1 && p.fx[1] < 0f && !CAStability.canLose(m, p.fx[1], null))
            return "It would take stability from " + Math.round(CAStability.now(m)) + " to " + Math.round(CAStability.now(m) + p.fx[1])
                    + ", under Autopilot's floor of " + Math.round(CAStability.floor());
        // 0.2.42.0: the build menu's industry limit. Picks are scored with the slots free when the pass began, and one pass can
        // start a new building and an upgrade, so a structure upgraded into an industry (Junkyard Forge -> Scrapyard) and a
        // new industry both took Wind's one free slot: 3 of 2, -5 stability, its income cut to nothing
        if (takesSlot(m, p) && usedSlots(m) >= Misc.getMaxIndustries(m))
            return "No free industry slot (" + usedSlots(m) + " of " + Misc.getMaxIndustries(m) + " used, upgrades under way included)";
        String action = "building " + p.name;
        if (!CABreaker.allowed(action)) return "Paused for this session: no effect three times. See Diagnostics";
        boolean added = false;
        try {
            if (p.upgrade) {
                Industry ex = p.existing;
                if (ex == null || ex.isUpgrading() || ex.isBuilding()) return "That building can't be upgraded right now";
                if (p.id.equals(ex.getSpec().getUpgrade())) {
                    if (!ex.canUpgrade()) return "That building can't be upgraded right now";
                    ex.startUpgrading();
                } else if (CAAotD.enabled() && CAAotD.children(ex.getId()).contains(p.id)) {
                    if (!CAAotD.allows(p.id, m)) return "Locked in the build menu: " + CAAotD.lockReason(p.id, m);
                    CAAotD.startUpgrade(ex, p.id);
                } else {
                    return "That upgrade isn't offered for " + ex.getCurrentName();
                }
            } else {
                if (m.hasIndustry(p.id)) return "Already built";
                if (CAAotD.enabled() && !CAAotD.allows(p.id, m)) return "Locked in the build menu: " + CAAotD.lockReason(p.id, m);
                m.addIndustry(p.id);
                added = true;
                Industry ind = m.getIndustry(p.id);
                if (ind == null) return "The game refused to add that building";
                ind.startBuilding();
            }
            String targetKey = p.upgrade && p.existing != null ? m.getId() + "|" + p.existing.getId() : null;
            if (targetKey != null) CAData.get().upgradeTargets().put(targetKey, p.id);   // usedSlots (0.2.44.1)
            Industry now = p.upgrade ? p.existing : m.getIndustry(p.id);
            boolean started = now != null && (p.upgrade ? now.isUpgrading() : now.isBuilding());
            CABreaker.check(action, started, m.getName());
            // 0.2.45.1: paid only when it took effect. An upgrade that didn't start (and didn't finish at once) costs nothing; a new
            // building that exists is paid as before, instant ones included
            if (!tookEffect(p.upgrade, started, p.upgrade && m.hasIndustry(p.id), now != null)) {
                if (targetKey != null) CAData.get().upgradeTargets().remove(targetKey);
                return "It did not take effect. Nothing charged";
            }
            if (price > 0f) CABudget.payPrice(price);   // 0.3.6.0 story mode: Free construction pays nothing
            if (now != null && CASync.instantBuild()) {   // 0.3.6.0 story mode: Instant construction
                try { now.finishBuildingOrUpgrading(); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "instant construction failed for " + p.id + " at " + m.getName(), t); }
            }
            // 0.3.0.0 (C12 fix, research 03 F3): credits move only in CABudget; the caller books the price and takes the fee with charge() or payFromPool() as before
            CADiagnostics.LOG.info(CADiagnostics.TAG + (p.upgrade ? "upgrade to " : "build ") + p.id + " at " + m.getName()
                    + " for " + Misc.getDGSCredits(p.cost));
            // 0.3.0.0 (B4, 03 F12): sunk-cost memory: what it cost, when and under which valuation (CATombstone: removal log, teardown loss)
            try { CATombstone.paid(m, p.id, p.upgrade && p.existing != null ? p.existing.getId() : null, p.cost); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "build record failed for " + p.id, t); }
            // 0.3.6.2: guarded on its own, so a throw here can't remove a building that's already paid for
            try { CACache.bump("build"); }   // 0.3.0.0 (A4): kept evaluations are worked out again after a build (the colony signature catches it too)
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "cache refresh failed after building " + p.id, t); }
            return null;
        } catch (Throwable t) {
            // 0.2.45.1: a building half-added when something threw comes off again, so nothing is left unpaid or stuck
            if (added) {
                try { if (m.hasIndustry(p.id)) m.removeIndustry(p.id, MarketAPI.MarketInteractionMode.REMOTE, false); }
                catch (Throwable ignored) { }
            }
            CADiagnostics.quarantine(p.id, "starting construction at " + m.getName(), t);
            return "Error starting construction; logged in Diagnostics";
        }
    }

    /** Whether a start took effect and should be paid (0.2.45.1): an upgrade that's under way or already done, or a new building that exists. */
    static boolean tookEffect(boolean upgrade, boolean started, boolean upgradeDone, boolean exists) {
        return upgrade ? started || upgradeDone : exists;
    }
}
