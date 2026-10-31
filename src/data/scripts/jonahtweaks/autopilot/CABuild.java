package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Starts a recommended build or upgrade exactly like the build menu: one at a time, paid from your credits. With
 * Building Menu Overhaul (Ashes of the Domain) its research locks and upgrade paths apply too.
 */
public class CABuild {

    public static float credits() {
        return Global.getSector().getPlayerFleet().getCargo().getCredits().get();
    }

    /** Returns null on success, or the reason it couldn't start. */
    public static String start(MarketAPI m, CAEngine.Pick p) {
        if (!p.upgrade && Misc.getCurrentlyBeingConstructed(m) != null) return "A new building is already under construction here (upgrades can still run)";
        if (credits() < p.cost) return "Not enough credits (" + Misc.getDGSCredits(p.cost) + " needed)";
        if (!CADiagnostics.usable(p.id)) return "Not supported by Colony Autopilot";
        String action = "building " + p.name;
        if (!CABreaker.allowed(action)) return "Paused for this session: it didn't take effect three times (see Diagnostics)";
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
                Industry ind = m.getIndustry(p.id);
                if (ind == null) return "The game refused to add that building";
                ind.startBuilding();
            }
            Industry now = p.upgrade ? p.existing : m.getIndustry(p.id);
            CABreaker.check(action, now != null && (p.upgrade ? now.isUpgrading() : now.isBuilding()), m.getName());
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(p.cost);
            CADiagnostics.LOG.info(CADiagnostics.TAG + (p.upgrade ? "upgrade to " : "build ") + p.id + " at " + m.getName()
                    + " for " + Misc.getDGSCredits(p.cost));
            return null;
        } catch (Throwable t) {
            CADiagnostics.quarantine(p.id, "starting construction at " + m.getName(), t);
            return "Error starting construction; logged in Diagnostics";
        }
    }
}
