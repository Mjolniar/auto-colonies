package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FleetRequest;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;

import java.util.HashSet;
import java.util.Set;

/**
 * WS-D (0.3.0.0, research 05 sections 3.1-3.2 and 6.4-6.6): Nexerelin colony fleets for Auto-expansion. Loaded only when
 * Nexerelin is enabled (CAExpansion checks first), like CAWar; CAExpansion itself has no Nexerelin imports.
 *
 * Launch follows the fleet request dialog (Nex_FleetRequest.launchFleet) with one change: the intel is set up and init()
 * runs before anything is paid. ColonyExpeditionIntel.init() ends the expedition at once when no jump point leads to the
 * target, and Nexerelin refunds the player fee of an expedition that ends while organizing; with the fee set before
 * init(), that refund would hand back credits that were never taken. So the fee is set, and the price paid, only after
 * init() leaves the expedition running (commit). An expedition dropped before that (abort) has a fee of 0 and refunds
 * nothing.
 *
 * Price: Nexerelin's own, fp x NexConfig.fleetRequestCostPerFP x 2 + the colony goods at base price (165,000 at 100 points,
 * 205,000 at 150). Fleet request points go down by fp. Nexerelin refunds a cancelled expedition by stage (1 organizing or
 * assembling, 0.67 travelling, 0.33 at the target) and a failed one (fleet lost, crew short, someone else first) not at
 * all. The points do not come back.
 *
 * Nexerelin's founding (createColonyStatic) puts a random person of the faction in charge. That person is not a hired
 * administrator and the colony does not count toward the colony limit. fixAdmin hands the colony to you
 * (ColonyManager.reassignAdminIfNeeded), so it counts like a colony you found yourself (fair play).
 * Nothing here is saved and nothing here holds state: an order keeps the planet id, and the intel is looked up each day.
 */
public class CAExpansionNex {
    /** Nexerelin's memory flag on a colony its expedition founded (createColonyStatic sets it for the player too). */
    static final String NPC_COLONY = "$nex_npcColony";

    // ---------------- availability and price ----------------

    /** Whether an expedition of fp points fits in the fleet request points CAWar.spare leaves after the emergency share. */
    static boolean available(CASettings s, float fp) {
        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        if (mgr == null) return false;
        boolean rich = CASurplus.rich(s);
        return CAWar.spare(mgr.getFleetRequestStock(), mgr.getFleetRequestCapacity(), rich) >= fp;
    }

    /** Fleet request points in stock and the capacity, for the Expansion page; null without the manager. */
    static int[] points() {
        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        if (mgr == null) return null;
        return new int[]{(int) mgr.getFleetRequestStock(), mgr.getFleetRequestCapacity()};
    }

    /** Nexerelin's price for a colony fleet of fp points (the fleet request dialog's updateCost). */
    static float cost(float fp) {
        return CAExpansion.nexCost(fp, NexConfig.fleetRequestCostPerFP, CAExpansion.goods());
    }

    /** Your nearest colony with a working spaceport (Nexerelin's rule for a fleet request source), or null. */
    static MarketAPI source(PlanetAPI target) {
        if (target == null || target.getMarket() == null) return null;
        return CAWar.source(target.getMarket());
    }

    /** Days until the colony fleet reaches the planet: organizing, assembling (Nexerelin says 3 to 6), travel, landing. */
    static float days(float fp, MarketAPI from, PlanetAPI target) {
        float organize = Nex_FleetRequest.getTimeToLaunch(fp, Nex_FleetRequest.FleetType.COLONY);
        float travel = 0f;
        try { travel = RouteLocationCalculator.getTravelDays(from.getPrimaryEntity(), target); } catch (Throwable ignored) { }
        return organize + CAExpansion.NEX_ASSEMBLE_DAYS + travel + CAExpansion.NEX_LAND_DAYS;
    }

    // ---------------- launch ----------------

    /**
     * Sets up and starts a colony expedition from `from` to the planet, with no fee yet. Returns the intel (typed Object, so
     * CAExpansion holds no Nexerelin type), or null when Nexerelin ended it at once (no jump point to the target).
     */
    static Object start(PlanetAPI target, MarketAPI from, float fp) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        float time = Nex_FleetRequest.getTimeToLaunch(fp, Nex_FleetRequest.FleetType.COLONY);
        ColonyExpeditionIntel intel = new ColonyExpeditionIntel(player, from, target.getMarket(), fp, time);
        intel.setPlayerSpawned(true);
        intel.init();
        if (intel.isEnded() || intel.isEnding()) return null;
        return intel;
    }

    /** After the price was paid: the fee Nexerelin refunds on a cancellation, and the fleet request points the fleet uses. */
    static void commit(Object intel, float price, float fp) {
        ((ColonyExpeditionIntel) intel).setPlayerFee(Math.round(price));
        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        if (mgr != null) mgr.modifyFleetRequestStock(-Math.round(fp));
    }

    /** Drops an expedition that was started but not paid for (its fee is 0, so Nexerelin refunds nothing). */
    static void abort(Object intel) {
        try { ((ColonyExpeditionIntel) intel).terminateEvent(OffensiveFleetIntel.OffensiveOutcome.OTHER); }
        catch (Throwable t) {
            try { ((ColonyExpeditionIntel) intel).endImmediately(); } catch (Throwable ignored) { }
        }
    }

    // ---------------- the expedition under way ----------------

    /** The player-spawned colony expedition to this order's planet (a running one before an ended one), or null. */
    static ColonyExpeditionIntel find(CAExpansion.Order o) {
        if (o == null || o.planetId == null) return null;
        ColonyExpeditionIntel best = null;
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(ColonyExpeditionIntel.class)) {
            if (!(i instanceof ColonyExpeditionIntel)) continue;
            ColonyExpeditionIntel c = (ColonyExpeditionIntel) i;
            if (!c.isPlayerSpawned()) continue;
            PlanetAPI p = c.getTargetPlanet();
            if (p == null || !o.planetId.equals(p.getId())) continue;
            if (best == null || (best.isEnded() && !c.isEnded())) best = c;
        }
        return best;
    }

    /**
     * "underway", "success" (Nexerelin founded it), "cancelled" (ended with a cancelled outcome: Nexerelin refunds by
     * stage), "failed" (fleet lost, crew short, someone else first: no refund) or "missing" (no such intel: not listed yet,
     * cleaned up after it ended, or changed by a Nexerelin update).
     */
    static String status(CAExpansion.Order o) {
        ColonyExpeditionIntel c = find(o);
        if (c == null) return "missing";
        if (c.getColonyOutcome() == ColonyExpeditionIntel.ColonyOutcome.SUCCESS) return "success";
        if (!c.isEnded() && !c.isEnding()) return "underway";
        OffensiveFleetIntel.OffensiveOutcome out = c.getOutcome();
        if (out != null && out.isCancelled()) return "cancelled";
        return "failed";
    }

    /** The expedition's stage (0 organizing, 1 assembling, 2 travelling, 3 at the target, 4 returning), or -1. */
    static int stage(CAExpansion.Order o) {
        ColonyExpeditionIntel c = find(o);
        if (c == null) return -1;
        try { return c.getCurrentStage(); } catch (Throwable t) { return -1; }
    }

    /**
     * Cancels the expedition (the planet was taken first) with a cancelled outcome, so Nexerelin refunds by stage; a failed
     * outcome later would refund nothing. Returns the share refunded (CAExpansion.nexRefundMult), or -1 when there was no
     * running expedition to cancel.
     */
    static float cancel(CAExpansion.Order o) {
        ColonyExpeditionIntel c = find(o);
        if (c == null || c.isEnded() || c.isEnding()) return -1f;
        int stage = c.getCurrentStage();
        c.terminateEvent(OffensiveFleetIntel.OffensiveOutcome.OTHER);
        return CAExpansion.nexRefundMult(stage);
    }

    // ---------------- the sector ----------------

    /** Planets any colony expedition is headed for now, yours or another faction's (ColonyManager.getExistingColonyTargets). */
    static Set<String> targeted() {
        Set<String> out = new HashSet<String>();
        ColonyManager mgr = ColonyManager.getManager();
        if (mgr == null) return out;
        for (PlanetAPI p : mgr.getExistingColonyTargets()) if (p != null) out.add(p.getId());
        return out;
    }

    /** Whether a colony expedition of any faction is headed for this planet. */
    static boolean targetedByOthers(PlanetAPI p) {
        return p != null && targeted().contains(p.getId());
    }

    /** The faction that owns the system by Nexerelin's rule (NexUtilsFaction.getSystemOwner), as an id, or null. */
    static String systemOwner(LocationAPI loc) {
        FactionAPI f = NexUtilsFaction.getSystemOwner(loc);
        return f == null ? null : f.getId();
    }

    /** Whether a Nexerelin invasion is on its way to one of your colonies. */
    static boolean invasionUnderWay() {
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(InvasionIntel.class)) {
            if (!(i instanceof InvasionIntel)) continue;
            InvasionIntel v = (InvasionIntel) i;
            if (v.isEnded() || v.isEnding()) continue;
            MarketAPI t = v.getTarget();
            if (t != null && t.isPlayerOwned()) return true;
        }
        return false;
    }

    /** Whether Nexerelin's expedition founded this colony (its own memory flag). */
    static boolean foundedByNex(MarketAPI m) {
        try { return m != null && m.getMemoryWithoutUpdate().getBoolean(NPC_COLONY); } catch (Throwable t) { return false; }
    }

    /**
     * Hands a colony that Nexerelin's expedition founded to you, so it counts toward the colony limit (no free
     * administrator). Returns true when the governor changed.
     */
    static boolean fixAdmin(MarketAPI m) {
        if (m == null || !m.isPlayerOwned()) return false;
        if (CARogue.adminLocked(m)) return false;   // 0.3.2.1: Rogue Planets' Trash King governs it
        if (m.getAdmin() == null) { m.setAdmin(Global.getSector().getPlayerPerson()); return true; }
        if (m.getAdmin().isPlayer()) return false;
        ColonyManager.reassignAdminIfNeeded(m, Global.getSector().getFaction(Factions.NEUTRAL), Global.getSector().getPlayerFaction());
        if (m.getAdmin() == null || !m.getAdmin().isPlayer()) m.setAdmin(Global.getSector().getPlayerPerson());
        return true;
    }
}
