package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FleetRequest;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.utilities.NexConfig;

import java.util.*;

/**
 * Emergency defense (Nexerelin): when an Auto colony's system is targeted by a hostile raid, invasion or crisis fleet,
 * or hostile fleets there outweigh its defenses, request a Defense fleet exactly as Nexerelin's fleet request dialog does.
 * Uses fleet request points and credits; may exceed the monthly budget (it's an emergency) but never the credit reserve.
 * 0.3.0.0 (WS-E): its own setting (Defense fleets), whatever the War level; realThreat() is the test CAWar uses to decide
 * whether proactive defense is an emergency (03 F10). No convenience fee, as in 0.2.45.
 */
public class CADefense {
    static final float MIN_FP = CATuning.DEFENSE_MIN_FP;   // 0.3.0.0 (A3): in CATuning
    static final float COOLDOWN_DAYS = CATuning.DEFENSE_COOLDOWN_DAYS;
    static final Set<String> warned = new HashSet<String>();
    /** 0.3.9.0: CAData.warOps key prefix of a defense fleet sent to a system ("defense:<systemId>"): the cooldown is saved. */
    static final String SENT = CAWarPolicy.DEFENSE + ":";

    static float hostileFP(StarSystemAPI sys) {
        float fp = 0f;
        for (CampaignFleetAPI f : sys.getFleets()) {
            // 0.3.8.6: also fleets made hostile to your faction and Sindrian Diktat raiders (CAEnforcers.threat)
            if (f.isPlayerFleet() || f.getFaction() == null || !CAEnforcers.threat(f)) continue;
            if (f.isStationMode() || CASurvival.supportFleet(f)) continue;   // 0.3.0.9: never a support fleet (0.98.5a)
            fp += f.getFleetPoints();
        }
        return fp;
    }

    static boolean raidTargets(StarSystemAPI sys) {
        try {
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(RaidIntel.class)) {
                RaidIntel r = (RaidIntel) i;
                if (r.isEnded() || r.isEnding() || r.getSystem() != sys || r.getFaction() == null) continue;
                if (r.getFaction().isHostileTo(Factions.PLAYER)) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /**
     * 0.3.0.0 (03 F10): a real attack on the colony's system: a hostile raid, invasion or crisis fleet targets it, or hostile
     * fleets there outweigh its defenses (this class's own test). CAWar pays proactive defense as an emergency only then.
     * 0.3.8.6: hostile fleets include Sindrian Diktat raiders and fleets made hostile to your faction (hostileFP).
     */
    static boolean realThreat(MarketAPI m) {
        StarSystemAPI sys = m == null ? null : m.getStarSystem();
        if (sys == null) return false;
        if (raidTargets(sys)) return true;
        return hostileFP(sys) > Math.max(150f, CAItems.defenseFP(m) + CASiege.friendlyFP(sys));   // 0.3.9.0: CASiege's, the same test
    }

    /** A defense fleet of yours is on its way to or at the colony's system. 0.3.0.9: yours only (requested by you, or of your faction). */
    static boolean defenseUnderWay(MarketAPI m) {
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(DefenseFleetIntel.class)) {
            DefenseFleetIntel d = (DefenseFleetIntel) i;
            if (d.isEnded() || d.isEnding() || d.getTarget() == null || d.getTarget().getStarSystem() != m.getStarSystem()) continue;
            if (d.isPlayerSpawned() || (d.getFaction() != null && d.getFaction().isPlayerFaction())) return true;
        }
        return false;
    }

    public static void run(List<MarketAPI> auto) {
        CASettings s = CAData.settings();
        if (s.disableDefenseFleets) return;
        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        if (mgr == null) return;
        MarketAPI target = null; float need = 0f; String why = null;
        for (MarketAPI m : auto) {
            StarSystemAPI sys = m.getStarSystem();
            if (sys == null) continue;
            // 0.3.9.0: the cooldown is the saved record (CAData.warOps), so a load does not allow the next fleet at once
            if (CAWar.daysSince(CAData.get(), SENT + sys.getId()) < COOLDOWN_DAYS) continue;
            if (defenseUnderWay(m)) continue;
            float hostile = hostileFP(sys);
            float ours = CAItems.defenseFP(m) + CASiege.friendlyFP(sys);
            boolean raid = raidTargets(sys);
            float n = 0f; String w = null;
            if (raid) { n = Math.max(MIN_FP * 2f, hostile * 1.25f - ours); w = "a hostile raid or invasion is targeting the system"; }
            else if (hostile > Math.max(150f, ours)) { n = hostile * 1.25f - ours; w = (int) hostile + " fleet points of hostiles in the system against about " + (int) ours + " of defense"; }
            if (n > need) { need = n; target = m; why = w; }
        }
        if (target == null) return;
        float stock = mgr.getFleetRequestStock();
        float fp = Math.round(Math.min(Math.max(need, MIN_FP), stock * 0.75f));
        if (fp < MIN_FP) {
            // 0.3.0.9: cannot, starsector.log only
            if (warned.add(target.getId())) CALog.quiet(target.getName() + ": needs a defense fleet (" + why + "), but only " + (int) stock + " fleet request points are available");
            return;
        }
        float cost = Math.round(fp * NexConfig.fleetRequestCostPerFP);
        if (CABuild.credits() - cost < s.reserve) {
            if (warned.add(target.getId() + ":credits")) CALog.quiet(target.getName() + ": needs a defense fleet costing " + Misc.getDGSCredits(cost) + " but that would go below your credit reserve");   // 0.3.0.9
            return;
        }
        MarketAPI source = CAWar.source(target);   // 0.3.9.0: CAWar's, the same nearest colony with a working spaceport
        if (source == null) return;
        try {
            float time = Nex_FleetRequest.getTimeToLaunch(fp, Nex_FleetRequest.FleetType.DEFENSE);
            DefenseFleetIntel intel = new DefenseFleetIntel(Global.getSector().getPlayerFaction(), source, target, fp, time);
            intel.setPlayerSpawned(true);
            intel.init();
            // 0.3.0.8: the fee only after init went through (an operation that ends at once refunds its fee), as CAConquest.launch
            if (intel.isEnded() || intel.isEnding()) return;
            intel.setPlayerFee(Math.round(cost));
            // 0.3.0.0 (C1): paid through CABudget.pay, the one place credits move; counted against the month as before, no fee
            CABudget.pay(s, target.getId(), cost, 0f, 0f);
            mgr.modifyFleetRequestStock(-(int) fp);
            CAWar.record(CAData.get(), SENT + target.getStarSystem().getId());   // 0.3.9.0: saved (was a session-only map)
            warned.remove(target.getId()); warned.remove(target.getId() + ":credits");
            CALog.did(target.getName() + ": Sent a " + (int) fp + "-point defense fleet from " + source.getName() + " for "
                    + Misc.getDGSCredits(cost) + " (" + why + ").", true);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "defense fleet request failed for " + target.getName(), t);
        }
    }
}
