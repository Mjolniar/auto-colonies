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

    /** 0.3.6.7: new buildings under construction here, counted as Misc.getCurrentlyBeingConstructed finds one (upgrades and the population building not counted). */
    public static int newBuilds(MarketAPI m) {
        int n = 0;
        if (m == null) return 0;
        for (Industry ind : m.getIndustries()) {
            try {
                if (ind.getSpec().hasTag(com.fs.starfarer.api.impl.campaign.ids.Industries.TAG_POPULATION)) continue;
                if (ind.isBuilding() && !ind.isUpgrading()) n++;
            } catch (Throwable ignored) { }
        }
        return n;
    }

    /**
     * 0.3.7.8: Rapid construction: the build or upgrade just started finishes in CASyncCatalog.RAPID_TIME of its time. The
     * game counts buildProgress up to buildTime (BaseIndustry's own fields), so progress starts at the rest.
     */
    static void rapid(Industry ind) throws Exception {
        if (!(ind instanceof com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry)) return;
        Class<?> base = com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry.class;
        Object time = CARefl.declared(base, "buildTime"), progress = CARefl.declared(base, "buildProgress");
        if (time == null || progress == null) return;
        float total = CARefl.getFloat(time, ind), done = CARefl.getFloat(progress, ind);
        if (total <= 0f) return;
        // 0.3.16.1: through the industry's own setBuildProgress, so a building with tiers of its own (RePA's Reverse Engineering
        // Hub, MultiTierIndustry) moves them too; before, only its field moved and the hub read finished while its tier still built
        ((com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry) ind).setBuildProgress(Math.max(done, total * (1f - CASyncCatalog.RAPID_TIME)));
    }

    /** 0.3.6.7: room for more new buildings here: CASync.buildsAtOnce (1, the game's rule; 2 or 3 with the story perks) less those under way. */
    public static int buildRoom(MarketAPI m) { return Math.max(0, CASync.buildsAtOnce() - newBuilds(m)); }

    /** 0.3.6.7: no room for another new building here. Upgrades still run. */
    public static boolean buildsFull(MarketAPI m) { return buildRoom(m) <= 0; }

    // ---------------- 0.3.12.1: the game's construction queue ----------------

    /** Whether the game's construction queue here holds this building. */
    public static boolean queued(MarketAPI m, String id) {
        try { return m.getConstructionQueue() != null && m.getConstructionQueue().hasItem(id); } catch (Throwable t) { return false; }
    }

    /** Industries waiting in the game's construction queue here: each takes a slot once it starts. */
    static int queuedSlots(MarketAPI m) {
        int n = 0;
        try {
            for (com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue.ConstructionQueueItem q : m.getConstructionQueue().getItems()) {
                com.fs.starfarer.api.loading.IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(q.id);
                if (sp != null && sp.getNewPluginInstance(m).isIndustry()) n++;
            }
        } catch (Throwable ignored) { }
        return n;
    }

    /**
     * 0.3.12.1: your Build click on a new building while one is already under way: it goes into the game's construction queue,
     * paid now as the game's own queue is (the price is kept on the item, so taking it out of the queue gives it back), and the
     * game starts it when the current one is done. The same checks as start(), with the queue's industries counted for slots.
     */
    public static String queue(MarketAPI m, CAEngine.Pick p) {
        if (p.upgrade) return "Upgrades don't queue";
        if (m.hasIndustry(p.id)) return "Already built";
        if (queued(m, p.id)) return "Already queued";
        float price = CASync.buildPrice(p.cost);
        if (credits() < price) return "Not enough credits (" + Misc.getDGSCredits(price) + " needed)";
        if (!CASync.mayBuild(p.id)) return "Stations need " + CASyncCatalog.buy("stations") + " on the Story page.";
        if (!CADiagnostics.usable(p.id)) return "Not supported by Colony Autopilot";
        if (p.fx != null && p.fx.length > 1 && p.fx[1] < 0f && !CAStability.canLose(m, p.fx[1] + CAStability.pending(m), null))
            return "It would take stability under Autopilot's floor of " + Math.round(CAStability.floor());
        if (takesSlot(m, p) && usedSlots(m) + queuedSlots(m) >= Misc.getMaxIndustries(m))
            return "No free industry slot (" + (usedSlots(m) + queuedSlots(m)) + " of " + Misc.getMaxIndustries(m) + " used, the queue included)";
        if (CAAotD.enabled() && !CAAotD.allows(p.id, m)) return "Locked in the build menu: " + CAAotD.lockReason(p.id, m);
        m.getConstructionQueue().addToEnd(p.id, Math.round(price));
        if (price > 0f) CABudget.payPrice(price);
        try { CACache.bump("queue"); } catch (Throwable ignored) { }
        CADiagnostics.LOG.info(CADiagnostics.TAG + "queued " + p.id + " at " + m.getName() + " for " + Misc.getDGSCredits(p.cost));
        return null;
    }

    /**
     * 0.3.17.2: why your Build click (CAApprovals.startByPlayer) can't start or queue this pick now, read without changing
     * anything; null when it can. The same gates as start() and queue(), with the credits for the game's price, Rapid
     * construction's fee and the construction fee (CABudget.buildTotal). The next-build card draws dark when this isn't null.
     */
    public static String blocked(MarketAPI m, CAEngine.Pick p) {
        if (m == null || p == null) return "Not available";
        float total = CABudget.buildTotal(CAData.settings(), p.cost);
        if (credits() < total) return "Not enough credits";
        if (!p.upgrade && (m.hasIndustry(p.id) || queued(m, p.id))) return "Already built or queued";
        if (!CASync.mayBuild(p.id)) return "Needs " + CASyncCatalog.buy("stations");
        if (!CADiagnostics.usable(p.id)) return "Not supported";
        if (p.fx != null && p.fx.length > 1 && p.fx[1] < 0f && !CAStability.canLose(m, p.fx[1] + CAStability.pending(m), null))
            return "Stability floor";
        if (takesSlot(m, p) && usedSlots(m) + queuedSlots(m) >= Misc.getMaxIndustries(m)) return "No free industry slot";
        if (p.upgrade) {
            Industry ex = p.existing;
            if (ex == null || ex.isUpgrading() || ex.isBuilding()) return "Can't upgrade now";
            if (p.id.equals(ex.getSpec().getUpgrade())) { if (!ex.canUpgrade()) return "Can't upgrade now"; }
            else if (!(CAAotD.enabled() && CAAotD.children(ex.getId()).contains(p.id))) return "Can't upgrade now";
            else if (!CAAotD.allows(p.id, m)) return "Locked";
        }
        else if (CAAotD.enabled() && !CAAotD.allows(p.id, m)) return "Locked";
        return null;
    }

    /** Returns null on success, or the reason it couldn't start. */
    public static String start(MarketAPI m, CAEngine.Pick p) {
        if (!p.upgrade && queued(m, p.id)) return "Already in the construction queue";   // 0.3.12.1: the game starts it
        if (!p.upgrade && buildsFull(m)) return "A new building is already under construction here" + (CASync.buildsAtOnce() > 1 ? " (" + CASync.buildsAtOnce() + " at once)" : "") + ". Upgrades can still run";
        float price = CASync.buildPrice(p.cost);   // 0.3.8.8: with Rapid construction's RAPID_FEE (story mode)
        if (credits() < price) return "Not enough credits (" + Misc.getDGSCredits(price) + " needed)";
        if (!CASync.mayBuild(p.id)) return "Stations need " + CASyncCatalog.buy("stations") + " on the Story page.";
        if (!CADiagnostics.usable(p.id)) return "Not supported by Colony Autopilot";
        // 0.3.0.0: the one stability rule (CAStability) as the last gate for a new building or upgrade: what it costs once built must
        // leave the colony at or above the floor. The planner already holds such picks back; this catches any path that doesn't
        // 0.3.9.6 (B3): buildings under way count with their stability cost (CAStability.pending)
        if (p.fx != null && p.fx.length > 1 && p.fx[1] < 0f && !CAStability.canLose(m, p.fx[1] + CAStability.pending(m), null))
            return "It would take stability from " + Math.round(CAStability.now(m)) + " to " + Math.round(CAStability.now(m) + CAStability.pending(m) + p.fx[1])
                    + ", under Autopilot's floor of " + Math.round(CAStability.floor());
        // 0.2.42.0: the build menu's industry limit. Picks are scored with the slots free when the pass began, and one pass can
        // start a new building and an upgrade, so a structure upgraded into an industry (Junkyard Forge -> Scrapyard) and a
        // new industry both took Wind's one free slot: 3 of 2, -5 stability, its income cut to nothing
        if (takesSlot(m, p) && usedSlots(m) + queuedSlots(m) >= Misc.getMaxIndustries(m))   // 0.3.12.1: the queue's industries too
            return "No free industry slot (" + (usedSlots(m) + queuedSlots(m)) + " of " + Misc.getMaxIndustries(m) + " used, upgrades under way included)";
        String action = "building " + p.name;
        if (!CABreaker.allowed(action)) return "Paused for this session: no effect three times. See Diagnostics";
        boolean added = false, paid = false;
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
                CARatForge.stage(m, p.id);   // 0.3.7.7: a Forge-Assisted BP from Autopilot Storage, where Random Assortment uses it up
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
            if (price > 0f) CABudget.payPrice(price);   // 0.3.8.8: the price includes Rapid construction's RAPID_FEE (story mode)
            paid = true;
            if (now != null && CASync.rapidBuild()) {   // 0.3.7.8 story mode: Rapid construction
                try { rapid(now); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "rapid construction failed for " + p.id + " at " + m.getName(), t); }
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
            // 0.3.8.8: an upgrade that started before the throw and isn't paid is stopped, so it never runs unpaid
            if (p.upgrade && !paid && p.existing != null) {
                try {
                    if (p.existing.isUpgrading()) {
                        p.existing.cancelUpgrade();
                        CAData.get().upgradeTargets().remove(m.getId() + "|" + p.existing.getId());
                    }
                } catch (Throwable ignored) { }
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
