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
import exerelin.utilities.NexUtilsMarket;

import java.util.*;

/**
 * Emergency defense (Nexerelin): when an Auto colony's system is targeted by a hostile raid, invasion or crisis fleet,
 * or hostile fleets there outweigh its defenses, request a Defense fleet exactly as Nexerelin's fleet request dialog does.
 * Uses fleet request points and credits; may exceed the monthly budget (it's an emergency) but never the credit reserve.
 */
public class CADefense {
    static final float MIN_FP = 100f;
    static final float COOLDOWN_DAYS = 30f;
    static final Map<String, Long> lastSent = new HashMap<String, Long>();
    static final Set<String> warned = new HashSet<String>();

    static float hostileFP(StarSystemAPI sys) {
        float fp = 0f;
        for (CampaignFleetAPI f : sys.getFleets()) {
            if (f.isPlayerFleet() || f.getFaction() == null || !f.getFaction().isHostileTo(Factions.PLAYER)) continue;
            if (f.isStationMode()) continue;
            fp += f.getFleetPoints();
        }
        return fp;
    }

    static float friendlyFP(StarSystemAPI sys) {
        float fp = 0f;
        for (CampaignFleetAPI f : sys.getFleets()) {
            if (f.isPlayerFleet() || f.getFaction() == null || !Factions.PLAYER.equals(f.getFaction().getId())) continue;
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

    static boolean defenseUnderWay(MarketAPI m) {
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(DefenseFleetIntel.class)) {
            DefenseFleetIntel d = (DefenseFleetIntel) i;
            if (!d.isEnded() && !d.isEnding() && d.getTarget() != null && d.getTarget().getStarSystem() == m.getStarSystem()) return true;
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
            Long last = lastSent.get(sys.getId());
            if (last != null && Global.getSector().getClock().getElapsedDaysSince(last) < COOLDOWN_DAYS) continue;
            if (defenseUnderWay(m)) continue;
            float hostile = hostileFP(sys);
            float ours = CAItems.defenseFP(m) + friendlyFP(sys);
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
            if (warned.add(target.getId())) CALog.add(target.getName() + ": needs a defense fleet (" + why + "), but only " + (int) stock + " fleet request points are available", true);
            return;
        }
        float cost = Math.round(fp * NexConfig.fleetRequestCostPerFP);
        if (CABuild.credits() - cost < s.reserve) {
            if (warned.add(target.getId() + ":credits")) CALog.add(target.getName() + ": needs a defense fleet costing " + Misc.getDGSCredits(cost) + " but that would go below your credit reserve", true);
            return;
        }
        MarketAPI source = null; float bestDist = Float.MAX_VALUE;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!NexUtilsMarket.hasWorkingSpaceport(m) || m.getPrimaryEntity() == null || target.getPrimaryEntity() == null) continue;
            float dist = Misc.getDistanceLY(m.getPrimaryEntity(), target.getPrimaryEntity());
            if (dist < bestDist) { bestDist = dist; source = m; }
        }
        if (source == null) return;
        try {
            float time = Nex_FleetRequest.getTimeToLaunch(fp, Nex_FleetRequest.FleetType.DEFENSE);
            DefenseFleetIntel intel = new DefenseFleetIntel(Global.getSector().getPlayerFaction(), source, target, fp, time);
            intel.setPlayerFee(Math.round(cost));
            intel.setPlayerSpawned(true);
            intel.init();
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
            mgr.modifyFleetRequestStock(-(int) fp);
            s.spent += cost;
            lastSent.put(target.getStarSystem().getId(), Global.getSector().getClock().getTimestamp());
            warned.remove(target.getId()); warned.remove(target.getId() + ":credits");
            CALog.add(target.getName() + ": sent a " + (int) fp + "-point defense fleet from " + source.getName() + " for "
                    + Misc.getDGSCredits(cost) + " (" + why + ")", true);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "defense fleet request failed for " + target.getName(), t);
        }
    }
}
