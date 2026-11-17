package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.HAPatherBaseDestroyedFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HAPirateBaseDestroyedFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HAShipsDestroyedFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.StandardLuddicPathActivityCause2;
import com.fs.starfarer.api.impl.campaign.intel.events.TriTachyonHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.ttcr.TTCRCommerceRaidersDestroyedFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.ttcr.TTCRPoints;
import com.fs.starfarer.api.impl.campaign.intel.events.ttcr.TTCRTradeFleetsDestroyedFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.ttcr.TriTachyonCommerceRaiding;
import com.fs.starfarer.api.impl.campaign.rulecmd.HA_CMD;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * 0.3.9.5: kills by Auto colony patrols count toward the colony crisis and Tri-Tachyon commerce raiding, as your own kills do.
 * - Battle: one without your fleet (a battle you fight gets the game's own count), with a patrol or the station of an Auto
 *   colony on one side ("your side" below). CAEnforcers.Watch, the transient battle listener, calls battle().
 * - Each lost ship counts once: the game reports a battle without you once per autoresolve round, and a ship lost in an
 *   earlier round is still missing from the battle's snapshot. The ids of counted ships are kept in CAData.crisisCounted for
 *   KEEP_DAYS (saved, so a battle running across a save counts nothing twice). Ships the game counts for you, in a battle
 *   you fight, are kept there too, so Auto Colonies never counts them again when your patrols finish that battle.
 * - Colony crisis: the game's rules (HostileActivityEventIntel.reportBattleOccurred, 0.98.5a) with that side as yours. Your
 *   side won and destroyed a pirate base station (HA_pirateBase by tier) or a Luddic Path base station (HA_patherBase*) in the
 *   battle's system: that factor alone. Otherwise, when the winner is near a system with a colony of yours: the fleet points
 *   the other side lost, through HostileActivityEventIntel.computeProgressPoints, as HAShipsDestroyedFactor. With Nexerelin's
 *   crisis (NexHostileActivityEventIntel) its two changes apply: any base station counts, and only systems with a visible
 *   colony of your faction count (no outposts). The game's rules use your fleet's location for the base check; this uses the
 *   battle's.
 * - Commerce raiding: the game's rules (TriTachyonHostileActivityFactor.reportBattleOccurred): commerce raiders, and trade
 *   fleets serving a Tri-Tachyon colony (x TTCRPoints.TRADE_FLEET_FP_MULT), lost by the other side, through
 *   computeTTCRProgressPoints and TriTachyonCommerceRaiding.addFactorCreateIfNecessary. Only while the factor listens (the
 *   crisis is on), not after you counter-raided Tri-Tachyon, and not while it has no progress and no event.
 * - Not needed: the Sindrian Diktat's devastation event (SDDevastation) counts every battle in Askonia already.
 * - Only the game's factor classes go into the events, so a save never holds an Auto Colonies class from this.
 * - Setting: CASettings.crisisKillsOff (inverted, On by default and in an older save). Normal and story mode alike.
 */
public class CAPatrolKills {
    static final String NEX_CRISIS = "exerelin.campaign.intel.hostileactivity.NexHostileActivityEventIntel";
    /** Days a counted ship id is kept: longer than any battle lasts. */
    static final float KEEP_DAYS = 30f;

    // ---------------- pure (JTPatrolKillCheck) ----------------

    /** The side with Auto colony forces: 1 or 2, or 0 when neither side or both have them. Pure. */
    static int side(boolean one, boolean two) { return one == two ? 0 : one ? 1 : 2; }

    /**
     * An Auto colony's force: a fleet of your faction other than your own fleet; a station, when it is an Auto colony's
     * station; any other fleet, when it is one of an Auto colony's patrols. Pure.
     */
    static boolean force(boolean playerFaction, boolean playerFleet, boolean station, boolean autoStation, boolean autoPatrol) {
        if (!playerFaction || playerFleet) return false;
        return station ? autoStation : autoPatrol;
    }

    /** A base station counts as destroyed: the game's rule needs it empty; Nexerelin's takes any station. Pure. */
    static boolean baseGone(boolean nexRules, boolean station, boolean empty) { return station && (nexRules || empty); }

    /** Commerce raiding counts this battle: not after a counter-raid, and not with no progress and no event. Pure. */
    static boolean ttcrCounts(boolean counterRaided, int progress, boolean event) { return !counterRaided && !(progress <= 0 && !event); }

    /** Fleet points one lost ship adds: {trade, raider}; a trade fleet's points x mult; a fleet that is neither adds none. Pure. */
    static int[] ttcrFP(boolean trader, boolean raider, int lostFP, int mult) {
        if (trader) return new int[]{lostFP * mult, 0};
        if (raider) return new int[]{0, lostFP};
        return new int[]{0, 0};
    }

    /** Whether a lost ship counts now: not counted before. Records it as counted at now. Pure. */
    static boolean fresh(String id, Map<String, Long> counted, long now) {
        if (id == null || counted.containsKey(id)) return false;
        counted.put(id, now);
        return true;
    }

    /** Drops ids counted more than keepDays ago (ageDays: days since a timestamp); returns how many. Pure. */
    static int prune(Map<String, Long> counted, ToDoubleFunction<Long> ageDays, float keepDays) {
        int n = 0;
        for (Iterator<Map.Entry<String, Long>> it = counted.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() == null || ageDays.applyAsDouble(e.getValue()) > keepDays) { it.remove(); n++; }
        }
        return n;
    }

    // ---------------- the battle ----------------

    /** A battle report (CAEnforcers.Watch): once per autoresolve round without you, once per encounter with you. */
    static void battle(CampaignFleetAPI winner, BattleAPI b) {
        if (winner == null || b == null) return;
        CampaignClockAPI clock = Global.getSector().getClock();
        // a battle you fight: the game counts the other side's lost ships as yours; they are never counted again here
        if (b.isPlayerInvolved()) { lost(b.getNonPlayerSideSnapshot(), counted(clock), clock.getTimestamp()); return; }
        if (CAData.settings().crisisKillsOff) return;
        List<CampaignFleetAPI> one = b.getSnapshotSideOne(), two = b.getSnapshotSideTwo();
        // 0.3.9.6: most reports (every AI battle round in the sector) have no fleet of your faction: nothing more to read
        if (!CAEnforcers.yourForces(one == null ? new ArrayList<CampaignFleetAPI>() : one)
                && !CAEnforcers.yourForces(two == null ? new ArrayList<CampaignFleetAPI>() : two)) return;
        List<MarketAPI> auto = autoColonies();
        if (auto.isEmpty()) return;
        Map<String, Long> counted = counted(clock);
        int s = side(hasForce(one, auto), hasForce(two, auto));
        if (s == 0) return;
        List<CampaignFleetAPI> ours = s == 1 ? one : two, theirs = s == 1 ? two : one;
        Map<CampaignFleetAPI, List<FleetMemberAPI>> lost = lost(theirs, counted, clock.getTimestamp());
        if (lost.isEmpty()) return;
        boolean won = ours.contains(winner) || (s == 1 ? b.getSideOne() : b.getSideTwo()).contains(winner);
        String place = auto.get(0).getName();
        LocationAPI loc = winner.getContainingLocation();
        for (MarketAPI m : auto) if (m.getContainingLocation() == loc) { place = m.getName(); break; }
        try { crisis(winner, won, lost, place); } catch (Throwable t) { CAStep.warn("crisis kills", "crisis kills: colony crisis failed", t); }
        try { commerceRaiding(lost, place); } catch (Throwable t) { CAStep.warn("crisis kills", "crisis kills: commerce raiding failed", t); }
    }

    /** The counted ship ids (CAData.crisisCounted), ids older than KEEP_DAYS dropped. */
    static Map<String, Long> counted(CampaignClockAPI clock) {
        Map<String, Long> counted = CAData.get().crisisCounted();
        prune(counted, clock::getElapsedDaysSince, KEEP_DAYS);
        return counted;
    }

    /** The side's ships lost since the battle's snapshot and not counted before, by fleet; each is recorded as counted. */
    static Map<CampaignFleetAPI, List<FleetMemberAPI>> lost(List<CampaignFleetAPI> side, Map<String, Long> counted, long now) {
        Map<CampaignFleetAPI, List<FleetMemberAPI>> out = new LinkedHashMap<CampaignFleetAPI, List<FleetMemberAPI>>();
        if (side == null) return out;
        for (CampaignFleetAPI f : side) {
            if (f == null) continue;
            List<FleetMemberAPI> l = new ArrayList<FleetMemberAPI>();
            for (FleetMemberAPI m : Misc.getSnapshotMembersLost(f)) if (fresh(m.getId(), counted, now)) l.add(m);
            if (!l.isEmpty()) out.put(f, l);
        }
        return out;
    }

    /** Your colonies on Auto. */
    static List<MarketAPI> autoColonies() {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (d.colony(m.getId()).mode == CAData.Mode.AUTO) out.add(m);
        return out;
    }

    static boolean hasForce(List<CampaignFleetAPI> side, List<MarketAPI> auto) {
        if (side == null) return false;
        for (CampaignFleetAPI f : side) if (f != null && isForce(f, auto)) return true;
        return false;
    }

    /** force() for one fleet: a patrol by its patrol flag and source colony, or by the colony's patrol route ("marketId_military"). */
    static boolean isForce(CampaignFleetAPI f, List<MarketAPI> auto) {
        boolean playerFaction = f.getFaction() != null && f.getFaction().isPlayerFaction();
        boolean station = f.isStationMode(), autoStation = false, autoPatrol = false;
        if (playerFaction && !f.isPlayerFleet()) {
            MemoryAPI mem = f.getMemoryWithoutUpdate();
            String source = mem.getString(MemFlags.MEMORY_KEY_SOURCE_MARKET);
            for (MarketAPI m : auto) {
                if (station) { if (Misc.getStationFleet(m) == f) autoStation = true; }
                else if ((mem.getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET) && m.getId().equals(source))
                        || RouteManager.getInstance().getRoute(m.getId() + "_military", f) != null) autoPatrol = true;
            }
        }
        return force(playerFaction, f.isPlayerFleet(), station, autoStation, autoPatrol);
    }

    /** The colony crisis: HostileActivityEventIntel.reportBattleOccurred with your side in place of your fleet's, new losses only. */
    static void crisis(CampaignFleetAPI winner, boolean won, Map<CampaignFleetAPI, List<FleetMemberAPI>> lost, String place) {
        HostileActivityEventIntel ha = HostileActivityEventIntel.get();
        if (ha == null || ha.isEnded() || ha.isEnding()) return;
        boolean nex = NEX_CRISIS.equals(ha.getClass().getName());
        LocationAPI loc = winner.getContainingLocation();
        if (won && loc instanceof StarSystemAPI) {
            StarSystemAPI system = (StarSystemAPI) loc;
            for (CampaignFleetAPI other : lost.keySet()) {   // a station destroyed in an earlier round was counted then
                if (!baseGone(nex, other.isStationMode(), other.getFleetData().getMembersListCopy().isEmpty())) continue;
                PirateBaseIntel pirate = PirateBaseIntel.getIntelFor(system);
                if (pirate != null && Misc.getStationFleet(pirate.getMarket()) == other && HA_CMD.baseInvolved(system, pirate)) {
                    int tier = pirate.getTier().ordinal();
                    if (tier < 0) tier = 0;
                    if (tier > 4) tier = 4;
                    int points = -1 * Global.getSettings().getIntFromArray("HA_pirateBase", tier);
                    ha.addFactor(new HAPirateBaseDestroyedFactor(points));
                    CALog.quiet(place + ": Crisis: patrols destroyed a pirate base. Colony crisis " + points + ".");
                    return;
                }
                LuddicPathBaseIntel pather = LuddicPathBaseIntel.getIntelFor(system);
                if (pather != null && Misc.getStationFleet(pather.getMarket()) == other) {
                    float totalInterest = 0f, activeCells = 0f;
                    for (StarSystemAPI curr : Misc.getPlayerSystems(false)) {
                        totalInterest += StandardLuddicPathActivityCause2.getPatherInterest(curr, 0f, 0f, 1f);
                        activeCells += StandardLuddicPathActivityCause2.getPatherInterest(curr, 0f, 0f, 1f, true);
                    }
                    if (totalInterest > 0) {
                        int flat = Global.getSettings().getInt("HA_patherBaseFlat");
                        int perCell = Global.getSettings().getInt("HA_patherBasePerActiveCell");
                        int max = Global.getSettings().getInt("HA_patherBaseMax");
                        int points = -1 * Math.min(max, (flat + perCell * (int) Math.round(activeCells)));
                        ha.addFactor(new HAPatherBaseDestroyedFactor(points));
                        CALog.quiet(place + ": Crisis: patrols destroyed a Luddic Path base. Colony crisis " + points + ".");
                    }
                    return;
                }
            }
        }
        boolean near = false;
        for (StarSystemAPI system : crisisSystems(nex)) {
            if (Misc.isNear(winner, system.getLocation())) { near = true; break; }
        }
        if (!near) return;
        float fp = 0f;
        for (List<FleetMemberAPI> l : lost.values()) for (FleetMemberAPI loss : l) fp += loss.getFleetPointCost();
        int points = HostileActivityEventIntel.computeProgressPoints(fp);
        if (points > 0) {
            ha.addFactor(new HAShipsDestroyedFactor(-1 * points));
            CALog.quiet(place + ": Crisis: patrols destroyed " + Math.round(fp) + " fleet points. Colony crisis -" + points + ".");
        }
    }

    /** The systems the crisis counts kills near: the game's (Misc.getPlayerSystems(false)), or Nexerelin's (visible colonies of your faction). */
    static List<StarSystemAPI> crisisSystems(boolean nex) {
        if (!nex) return Misc.getPlayerSystems(false);
        List<StarSystemAPI> out = new ArrayList<StarSystemAPI>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.isHidden() || !Factions.PLAYER.equals(m.getFactionId())) continue;
            StarSystemAPI s = m.getStarSystem();
            if (s != null && !out.contains(s)) out.add(s);
        }
        return out;
    }

    /** Tri-Tachyon commerce raiding: TriTachyonHostileActivityFactor.reportBattleOccurred with your side in place of your fleet's, new losses only. */
    static void commerceRaiding(Map<CampaignFleetAPI, List<FleetMemberAPI>> lost, String place) {
        List<TriTachyonHostileActivityFactor> tt = Global.getSector().getListenerManager().getListeners(TriTachyonHostileActivityFactor.class);
        if (tt == null || tt.isEmpty()) return;
        if (!ttcrCounts(TriTachyonHostileActivityFactor.isPlayerCounterRaidedTriTach(), tt.get(0).getProgress(null), TriTachyonCommerceRaiding.get() != null)) return;
        int traderFP = 0, raiderFP = 0;
        for (Map.Entry<CampaignFleetAPI, List<FleetMemberAPI>> e : lost.entrySet()) {
            CampaignFleetAPI other = e.getKey();
            boolean trader = TriTachyonHostileActivityFactor.isTraderServingATTColony(other);
            boolean raider = TriTachyonHostileActivityFactor.isCommerceRaider(other);
            if (!trader && !raider) continue;
            for (FleetMemberAPI loss : e.getValue()) {
                int[] add = ttcrFP(trader, raider, loss.getFleetPointCost(), TTCRPoints.TRADE_FLEET_FP_MULT);
                traderFP += add[0];
                raiderFP += add[1];
            }
        }
        if (traderFP > 0) {
            int points = TriTachyonHostileActivityFactor.computeTTCRProgressPoints(traderFP);
            if (points > 0) {
                TriTachyonCommerceRaiding.addFactorCreateIfNecessary(new TTCRTradeFleetsDestroyedFactor(points), null);
                CALog.quiet(place + ": Crisis: patrols destroyed Tri-Tachyon trade fleets. Commerce raiding +" + points + ".");
            }
        }
        if (raiderFP > 0) {
            int points = TriTachyonHostileActivityFactor.computeTTCRProgressPoints(raiderFP);
            if (points > 0) {
                TriTachyonCommerceRaiding.addFactorCreateIfNecessary(new TTCRCommerceRaidersDestroyedFactor(points), null);
                CALog.quiet(place + ": Crisis: patrols destroyed commerce raiders. Commerce raiding +" + points + ".");
            }
        }
    }
}
