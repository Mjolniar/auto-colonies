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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.3.16.5: fleet limit per system. Dozens of small fleets in one system (Roider nomads and miners, traders, scavengers,
 * pilgrims, mercenaries, other factions' patrols, on top of your patrols and defense fleets) lag the campaign map.
 * Daily, in each system with one of your colonies, the fleets past LIMIT are sent home: filler fleets first, then other
 * factions' patrols and mercenaries, the ones farthest from you first and never within NEAR of you. A fleet sent home
 * despawns as arrived, so its route ends and its source sends a new one on its own schedule.
 * Only fleets on a route (RouteManager) are touched. Never your faction's fleets, stations, hostile or threatening fleets,
 * war, raid or invasion fleets, mission fleets, busy fleets or fleets in a battle.
 * 0.3.17.9: in a calm system (no siege, no colony at high threat, no threatening fleet there) the limit is CALM_LIMIT, and
 * your own patrols on a route and Auto Colonies' defense fleets go home last, after filler and other factions' patrols.
 * The Autarch armada, stations and important, war, busy or fighting fleets stay.
 * 0.3.18.2: your colonies' patrol counts follow the system's threat (patrolsNeeded, capPatrols), and your patrols past it
 * go home in any state. Near you a fleet sent home flies back to its colony instead of vanishing.
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

    /**
     * Pure (0.3.17.9): 3 when your faction's fleet may be sent home in a calm system (after filler and other factions'
     * patrols): a patrol on a route, or a defense fleet Auto Colonies raised whose duty outlasted the threat. Never a station,
     * the Autarch armada, an important, war, busy or fighting fleet, or any while the system isn't calm.
     */
    static int ownRank(boolean own, boolean calm, boolean station, boolean armada, boolean important, boolean war, boolean busy,
                       boolean battle, boolean onRoute, boolean patrol, boolean acDefense) {
        if (!own || !calm || station || armada || important || war || busy || battle) return 0;
        return (onRoute && patrol) || acDefense ? 3 : 0;
    }

    /** Pure (0.3.17.9): the limit in force: CALM_LIMIT while the system is calm, else LIMIT. */
    static int limit(boolean calm) { return calm ? CALM_LIMIT : LIMIT; }

    static final int CALM_LIMIT = CATuning.FLEET_LIMIT_CALM;

    /**
     * 0.3.17.9: no colony of yours in the system is under siege or at high threat (a raid, crisis or invasion coming:
     * CAThreat's saved state), and no fleet there threatens you (CAEnforcers.threat).
     */
    static boolean calm(StarSystemAPI sys) {
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m.getStarSystem() != sys) continue;
            if (d.colony(m.getId()).threatHigh || CASiege.held(m)) return false;
        }
        for (CampaignFleetAPI f : sys.getFleets()) if (!f.isStationMode() && CAEnforcers.threat(f)) return false;
        return true;
    }

    /** Pure: how many to send home from `count` fleets at `limit` (0 under it). */
    static int over(int count, int limit) { return limit > 0 ? Math.max(0, count - limit) : 0; }

    /** Fleets that count: every fleet in the system but yours (the player's own fleet) and stations. */
    static int count(LocationAPI l) {
        int n = 0;
        for (CampaignFleetAPI f : l.getFleets()) if (!f.isPlayerFleet() && !f.isStationMode() && !sentHome(f)) n++;   // 0.3.18.2: not one flying home
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
        if (!on(CAData.settings())) { uncapAll(); return; }   // 0.3.18.2: the setting off gives the patrols back
        Set<StarSystemAPI> systems = new LinkedHashSet<StarSystemAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (m.getStarSystem() != null) systems.add(m.getStarSystem());
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        for (StarSystemAPI sys : systems) {
            try { trim(sys, player); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "fleet limit failed in " + sys.getName(), t); }
        }
    }

    // ---------------- 0.3.18.2: patrols sized to the threat ----------------
    // Your colonies' military buildings keep their full patrol counts whatever is coming: three colonies with High Command
    // in Mia's Star kept about 28 patrols around the capital with nothing inbound. Now each system's patrols follow its
    // threat: CALM_PATROLS, plus enough to beat the threat's fleet points by a quarter. The game's own patrol counts
    // (PATROL_NUM_*_MOD) are lowered at its colonies by PATROL_MOD, and patrols over the count go home.

    static final String PATROL_MOD = "ca_fleetlimit_patrols";
    /** Patrols a calm system keeps, a patrol's fleet points about (light 20 to 40, medium 50 to 70, heavy 90 to 120). */
    static final int CALM_PATROLS = 3;
    static final float PATROL_FP = 50f, RAID_FP = 300f, CRISIS_FP = 200f;
    /** Fleet memory key: a fleet sent home that is flying back (it doesn't count, and isn't sent twice). */
    static final String SENT = "$ca_fleetLimitHome";
    static final String[] PATROL_STATS = {com.fs.starfarer.api.impl.campaign.ids.Stats.PATROL_NUM_LIGHT_MOD,
            com.fs.starfarer.api.impl.campaign.ids.Stats.PATROL_NUM_MEDIUM_MOD, com.fs.starfarer.api.impl.campaign.ids.Stats.PATROL_NUM_HEAVY_MOD};

    /**
     * 0.3.18.3: per system id, the patrol count held {count, day it was set}: a threat raises it at once and it holds HOLD_DAYS
     * before stepping down, so hostiles coming and going don't send patrols home one day and spawn new ones the next
     * (Mia's Star went 3, 10, 21, 9 patrols within weeks). Session only (CAReset).
     */
    static final Map<String, float[]> PEAKS = new HashMap<String, float[]>();
    /** 0.3.18.3: per system id, fleets sent home since the last summary {filler, other factions' patrols, your patrols, your defense fleets, flying back, first day}. */
    static final Map<String, float[]> WEEK = new HashMap<String, float[]>();
    static final float HOLD_DAYS = 30f, SUMMARY_DAYS = 7f;

    /** Pure (0.3.18.3): the patrol count kept {count, day} from today's need and the held one (null: none). */
    static float[] held(int need, float[] peak, float now) {
        if (peak == null || need >= peak[0] || now < peak[1] || now - peak[1] > HOLD_DAYS) return new float[]{need, now};
        return new float[]{peak[0], peak[1]};
    }

    /** Pure (0.3.18.3): the summary line's list: "15 traders and other filler, 2 of your patrols"; null when nothing went. */
    static String summary(float[] w) {
        List<String> l = new ArrayList<String>();
        String[] what = {"traders and other filler", "other factions' patrols", "of your patrols", "of your defense fleets"};
        for (int i = 0; i < 4; i++) if (w[i] > 0f) l.add(Math.round(w[i]) + " " + what[i]);
        return l.isEmpty() ? null : CAContracts.join(l);
    }

    /** Pure: patrols a system needs for a threat of this many fleet points (0: calm). */
    static int patrolsNeeded(float threatFP) {
        return CALM_PATROLS + (threatFP > 0f ? (int) Math.ceil(threatFP * 1.25f / PATROL_FP) : 0);
    }

    /**
     * Pure: `allowed` patrols split over the colonies' own maxima {light, medium, heavy} (biggest colony first): in rounds,
     * each colony takes one heavy, one medium and one light while it has room. Returns each colony's {light, medium, heavy}.
     */
    static int[][] split(int[][] max, int allowed) {
        int[][] out = new int[max.length][3];
        int left = Math.max(0, allowed);
        boolean any = true;
        while (left > 0 && any) {
            any = false;
            for (int i = 0; i < max.length && left > 0; i++)
                for (int t = 2; t >= 0 && left > 0; t--)
                    if (out[i][t] < max[i][t]) { out[i][t]++; left--; any = true; }
        }
        return out;
    }

    /**
     * The threat to a system in fleet points: the hostiles there, a raid group on its way, a hostile raid or invasion aimed at
     * it (RAID_FP), a colony there at high threat or under siege (CRISIS_FP: a crisis or raid coming). 0 when calm.
     */
    static float threatFP(StarSystemAPI sys) {
        float fp = CAOwnWar.hostileFP(sys);
        com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI g = CAOwnWar.raidGroup(sys);
        if (g != null) fp = Math.max(fp, CAOwnWar.groupFP(g));
        if (CAOwnWar.raidIntel(sys)) fp = Math.max(fp, RAID_FP);
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false))
            if (m.getStarSystem() == sys && (d.colony(m.getId()).threatHigh || CASiege.held(m))) fp = Math.max(fp, CRISIS_FP);
        return fp;
    }

    /** Lowers the game's patrol counts at the system's colonies to `allowed` patrols in all; returns the counts the game had. */
    static int capPatrols(StarSystemAPI sys, int allowed) {
        List<MarketAPI> ms = new ArrayList<MarketAPI>();
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false))
            if (m.getStarSystem() == sys && m.isPlayerOwned() && d.colony(m.getId()).mode != CAData.Mode.OFF) ms.add(m);
        Collections.sort(ms, new Comparator<MarketAPI>() { public int compare(MarketAPI a, MarketAPI b) { return b.getSize() - a.getSize(); } });
        int[][] max = new int[ms.size()][3];
        int total = 0;
        for (int i = 0; i < ms.size(); i++) for (int t = 0; t < 3; t++) {
            com.fs.starfarer.api.combat.StatBonus b = ms.get(i).getStats().getDynamic().getMod(PATROL_STATS[t]);
            b.unmodifyFlat(PATROL_MOD);
            max[i][t] = Math.max(0, (int) b.computeEffective(0f));
            total += max[i][t];
        }
        int[][] allow = split(max, allowed);
        for (int i = 0; i < ms.size(); i++) for (int t = 0; t < 3; t++) {
            int cut = allow[i][t] - max[i][t];
            if (cut < 0) ms.get(i).getStats().getDynamic().getMod(PATROL_STATS[t]).modifyFlat(PATROL_MOD, cut, "Fleet limit (Auto Colonies)");
        }
        return total;
    }

    /** The patrol caps off at every colony (the setting turned off). */
    static void uncapAll() {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy())
            for (String st : PATROL_STATS) m.getStats().getDynamic().getMod(st).unmodifyFlat(PATROL_MOD);
    }

    static boolean sentHome(CampaignFleetAPI f) { return f.getMemoryWithoutUpdate().getBoolean(SENT); }

    /**
     * Sends a fleet home: out of your sight it despawns as arrived; near you it flies back to its colony and despawns there,
     * so nothing vanishes in front of you. Returns true when it despawned now.
     */
    static boolean home(CampaignFleetAPI f, MarketAPI source, boolean nearYou) {
        if (!nearYou || source == null || source.getPrimaryEntity() == null) { f.despawn(FleetDespawnReason.REACHED_DESTINATION, null); return true; }
        f.clearAssignments();
        f.addAssignment(com.fs.starfarer.api.campaign.FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, source.getPrimaryEntity(), 30f, "returning to " + source.getName());
        f.getMemoryWithoutUpdate().set(SENT, true, 30f);
        return false;
    }

    /** The colony a fleet flies back to: its route's market, else your biggest colony in the system. */
    static MarketAPI sourceOf(CampaignFleetAPI f, Map<CampaignFleetAPI, MarketAPI> routed, StarSystemAPI sys) {
        MarketAPI m = routed.get(f);
        if (m != null && m.getStarSystem() == sys) return m;
        MarketAPI best = null;
        for (MarketAPI x : Misc.getPlayerMarkets(false)) if (x.getStarSystem() == sys && (best == null || x.getSize() > best.getSize())) best = x;
        return best;
    }

    static void trim(StarSystemAPI sys, final CampaignFleetAPI player) {
        boolean calm = calm(sys);   // 0.3.17.9
        float threat = threatFP(sys);   // 0.3.18.2
        float now = CADay.now();
        float[] keep = held(patrolsNeeded(threat), PEAKS.get(sys.getId()), now);   // 0.3.18.3: held HOLD_DAYS before stepping down
        PEAKS.put(sys.getId(), keep);
        int allowed = Math.round(keep[0]), had = capPatrols(sys, allowed);
        int lim = limit(calm);
        Map<CampaignFleetAPI, MarketAPI> routed = new HashMap<CampaignFleetAPI, MarketAPI>();
        for (RouteManager.RouteData r : RouteManager.getInstance().getRoutesInLocation(sys))
            if (r.getActiveFleet() != null) routed.put(r.getActiveFleet(), r.getMarket());
        final boolean here = player != null && player.getContainingLocation() == sys;
        List<CampaignFleetAPI> first = new ArrayList<CampaignFleetAPI>(), second = new ArrayList<CampaignFleetAPI>(), third = new ArrayList<CampaignFleetAPI>(),
                patrols = new ArrayList<CampaignFleetAPI>();
        for (CampaignFleetAPI f : sys.getFleets()) {
            if (f.isPlayerFleet() || sentHome(f)) continue;
            int r = rank(f, routed.keySet());
            if (r == 1) first.add(f); else if (r == 2) second.add(f);
            else if (ownRank(f, routed.keySet(), true) == 3 && f.getMemoryWithoutUpdate().getBoolean("$isPatrol") && routed.containsKey(f)) patrols.add(f);   // 0.3.18.2
            else if (ownRank(f, routed.keySet(), calm) == 3) third.add(f);   // 0.3.17.9: your spent defense fleets, last
        }
        Comparator<CampaignFleetAPI> far = new Comparator<CampaignFleetAPI>() {
            public int compare(CampaignFleetAPI a, CampaignFleetAPI b) {
                return here ? Float.compare(Misc.getDistance(b, player), Misc.getDistance(a, player)) : 0;
            }
        };
        Collections.sort(first, far);
        Collections.sort(second, far);
        Collections.sort(third, far);
        Collections.sort(patrols, far);
        float[] w = WEEK.get(sys.getId());
        if (w == null) { w = new float[]{0f, 0f, 0f, 0f, 0f, now}; WEEK.put(sys.getId(), w); }
        // 0.3.18.2: your patrols past what the threat needs go home, whatever the system's count
        int extraPatrols = Math.max(0, patrols.size() - allowed);
        for (int i = 0; i < extraPatrols; i++) {
            CampaignFleetAPI f = patrols.get(i);
            if (!home(f, sourceOf(f, routed, sys), here && Misc.getDistance(f, player) < NEAR)) w[4]++;
            w[2]++;
        }
        List<CampaignFleetAPI> rest = new ArrayList<CampaignFleetAPI>(patrols.subList(extraPatrols, patrols.size()));
        int extra = over(count(sys), lim);
        List<CampaignFleetAPI> go = new ArrayList<CampaignFleetAPI>(first);
        go.addAll(second);
        go.addAll(third);
        if (calm) go.addAll(rest);   // a calm system's remaining patrols count down to the limit last
        int sent = 0;
        for (CampaignFleetAPI f : go) {
            if (sent >= extra) break;
            int kind = first.contains(f) ? 0 : second.contains(f) ? 1 : third.contains(f) ? 3 : 2;
            if (!home(f, sourceOf(f, routed, sys), here && Misc.getDistance(f, player) < NEAR)) w[4]++;
            w[kind]++;
            sent++;
        }
        if (CALog.once("fleetcap:" + sys.getId() + ":" + allowed + ":" + had))
            CALog.quiet(sys.getName() + ": Fleet limit: " + allowed + " patrols for a threat of " + Math.round(threat) + " fleet points (the colonies' buildings allow " + had + ").");
        // 0.3.18.3: one line a week per system with what went, not one a day (128 lines in 8 minutes of play)
        if (now - w[5] >= SUMMARY_DAYS || now < w[5]) {
            String list = summary(w);
            if (list != null) CALog.quiet(sys.getName() + ": Fleet limit: sent home in " + Math.round(Math.max(1f, now - w[5])) + " days: " + list + "."
                    + (w[4] > 0f ? " " + Math.round(w[4]) + " flew back to their colony." : "") + " Limit " + lim + (calm ? " while calm." : "."));
            WEEK.put(sys.getId(), new float[]{0f, 0f, 0f, 0f, 0f, now});
        }
    }

    /** 0.3.17.9: ownRank() for one fleet. */
    static int ownRank(CampaignFleetAPI f, Set<CampaignFleetAPI> routed, boolean calm) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        boolean own = f.getFaction() != null && Factions.PLAYER.equals(f.getFaction().getId());
        boolean important = mem.getBoolean("$missionImportant") || mem.contains("$sotf_seraphFleet");
        boolean war = mem.getBoolean("$isWarFleet") || mem.getBoolean("$isRaider");
        return ownRank(own, calm, f.isStationMode(), mem.getBoolean(CATierFive.ARMADA_MEM), important, war, mem.getBoolean("$core_fleetBusy"),
                f.getBattle() != null, routed.contains(f), mem.getBoolean("$isPatrol"), mem.contains(CAOwnWar.MEM));
    }
}
