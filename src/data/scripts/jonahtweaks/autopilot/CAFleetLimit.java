package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 0.3.16.5: fleet limit per system. Dozens of small fleets in one system (Roider nomads and miners, traders, scavengers,
 * pilgrims, mercenaries, other factions' patrols, on top of your patrols and defense fleets) lag the campaign map.
 * Daily, in each system with one of your colonies, the fleets past LIMIT are sent home: filler fleets first, then other
 * factions' patrols and mercenaries, the ones farthest from you first and never within NEAR of you. A fleet sent home
 * despawns as arrived, so its route ends and its source sends a new one on its own schedule.
 * Only fleets on a route (RouteManager) are touched. Never your faction's fleets, stations, hostile or threatening fleets,
 * war, raid or invasion fleets, mission fleets, busy fleets or fleets in a battle.
 * While a system is at the limit, Auto Colonies sends it no standing guard and no threat-high defense fleet; an emergency
 * (hostiles really there) still goes (CAWar, CAOwnWar). Setting: Fleet limit per system (CASettings.noFleetLimit).
 * Vanilla only.
 */
public final class CAFleetLimit {
    private CAFleetLimit() { }

    static final int LIMIT = CATuning.FLEET_LIMIT;
    static final float NEAR = CATuning.FLEET_LIMIT_NEAR;
    /** Fleet types that are filler whatever their flags (Roider Union's nomads and miners, the game's pilgrims and shuttles). */
    static final String[] FILLER_TYPES = {"trade", "smuggl", "scaveng", "nomad", "miner", "pilgrim", "tourism", "academy", "prospect", "salvag"};

    static boolean on(CASettings s) { return s != null && !s.noFleetLimit; }

    /** Pure: 0 kept, 1 filler (sent home first), 2 other factions' patrols and mercenaries (sent home after the filler). */
    static int rank(boolean ownFaction, boolean station, boolean threat, boolean important, boolean war, boolean busy, boolean battle,
                    boolean onRoute, boolean trade, boolean scavenger, boolean patrol, String type) {
        if (ownFaction || station || threat || important || war || busy || battle || !onRoute) return 0;
        String t = type == null ? "" : type.toLowerCase();
        if (trade || scavenger) return 1;
        for (String k : FILLER_TYPES) if (t.contains(k)) return 1;
        if (patrol || t.startsWith("merc")) return 2;
        return 0;
    }

    /** Pure: how many to send home from `count` fleets at `limit` (0 under it). */
    static int over(int count, int limit) { return limit > 0 ? Math.max(0, count - limit) : 0; }

    /** Fleets that count: every fleet in the system but yours (the player's own fleet) and stations. */
    static int count(LocationAPI l) {
        int n = 0;
        for (CampaignFleetAPI f : l.getFleets()) if (!f.isPlayerFleet() && !f.isStationMode()) n++;
        return n;
    }

    /** The system is at the limit (and the limit is on). */
    public static boolean full(StarSystemAPI sys) {
        try { return sys != null && on(CAData.settings()) && count(sys) >= LIMIT; } catch (Throwable t) { return false; }
    }

    static int rank(CampaignFleetAPI f, Set<CampaignFleetAPI> routed) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        boolean own = f.getFaction() != null && Factions.PLAYER.equals(f.getFaction().getId());
        boolean important = mem.getBoolean("$missionImportant") || mem.contains("$sotf_seraphFleet");
        boolean war = mem.getBoolean("$isWarFleet") || mem.getBoolean("$isRaider");
        return rank(own, f.isStationMode(), CAEnforcers.threat(f), important, war, mem.getBoolean("$core_fleetBusy"), f.getBattle() != null,
                routed.contains(f), mem.getBoolean("$isTradeFleet"), mem.getBoolean("$isScavenger"), mem.getBoolean("$isPatrol"),
                mem.getString("$fleetType"));
    }

    /** Daily: each system with one of your colonies, down to the limit. */
    public static void daily() {
        if (!on(CAData.settings())) return;
        Set<StarSystemAPI> systems = new LinkedHashSet<StarSystemAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (m.getStarSystem() != null) systems.add(m.getStarSystem());
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        for (StarSystemAPI sys : systems) {
            try { trim(sys, player); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "fleet limit failed in " + sys.getName(), t); }
        }
    }

    static void trim(StarSystemAPI sys, final CampaignFleetAPI player) {
        int extra = over(count(sys), LIMIT);
        if (extra <= 0) return;
        Set<CampaignFleetAPI> routed = new HashSet<CampaignFleetAPI>();
        for (RouteManager.RouteData r : RouteManager.getInstance().getRoutesInLocation(sys))
            if (r.getActiveFleet() != null) routed.add(r.getActiveFleet());
        final boolean here = player != null && player.getContainingLocation() == sys;
        List<CampaignFleetAPI> first = new ArrayList<CampaignFleetAPI>(), second = new ArrayList<CampaignFleetAPI>();
        for (CampaignFleetAPI f : sys.getFleets()) {
            if (f.isPlayerFleet()) continue;
            if (here && Misc.getDistance(f, player) < NEAR) continue;
            int r = rank(f, routed);
            if (r == 1) first.add(f); else if (r == 2) second.add(f);
        }
        Comparator<CampaignFleetAPI> far = new Comparator<CampaignFleetAPI>() {
            public int compare(CampaignFleetAPI a, CampaignFleetAPI b) {
                return here ? Float.compare(Misc.getDistance(b, player), Misc.getDistance(a, player)) : 0;
            }
        };
        Collections.sort(first, far);
        Collections.sort(second, far);
        List<CampaignFleetAPI> go = new ArrayList<CampaignFleetAPI>(first);
        go.addAll(second);
        int sent = 0;
        for (CampaignFleetAPI f : go) {
            if (sent >= extra) break;
            f.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
            sent++;
        }
        if (sent > 0) CALog.quiet(sys.getName() + ": Fleet limit: sent " + sent + " small fleets home (" + LIMIT + " a system).");
    }
}
