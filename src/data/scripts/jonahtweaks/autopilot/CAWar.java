package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FleetRequest;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.ReliefFleetIntelAlt;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;

import java.util.*;

/**
 * War fleets (Nexerelin, 2.35.0; Settings, Colony policies, "War fleets": Protect and strike back (default), Protect, Off).
 * On top of CADefense's emergency fleets it requests Nexerelin fleets exactly as the fleet request dialog does, for security
 * only: never an invasion or a colony expedition, and an aggressive fleet only against a faction already hostile to you (one
 * against anyone else would make them hostile). In order, each day:
 * 1. Relief: a relief fleet to a colony under siege, one per colony every RELIEF_DAYS.
 * 2. Proactive defense: a Defense fleet to a colony whose threat is high or that a colony crisis targets, before anything arrives.
 * 3. Pirate eradication: base strikes on the pirate and Pather bases you know of, within BASE_LY of a colony (any distance
 *    while spending freely), at twice their station's strength.
 * 4. Strike back (default mode): raids on the markets that sent raids, invasions or blockades at your colonies in the last
 *    ATTACK_DAYS days, while their faction is still hostile.
 * 5. Spending freely only: a standing Defense fleet at every colony without one, biggest colonies first.
 * Normally it keeps RESERVE of Nexerelin's fleet request capacity for emergencies and starts one operation a day. Spending
 * freely (CASurplus.rich, 10M or more over the line) it shotguns: RICH_PER_DAY a day, down to RICH_RESERVE of capacity,
 * fleets RICH_SIZE times bigger, paid from the surplus. Relief and threatened colonies are emergencies, so they may go over
 * the monthly budget like CADefense; strikes and raids come out of the budget or the surplus. Never below the credit reserve.
 */
public class CAWar {
    static final String MOD = "nexerelin";
    static final float RESERVE = 0.4f, RICH_RESERVE = 0.1f, RICH_SIZE = 1.5f, MIN_FP = 100f;
    static final float BASE_LY = 15f, ATTACK_DAYS = 120f, RELIEF_DAYS = 60f;
    static final int RICH_PER_DAY = 3;
    static final Map<String, Long> lastRelief = new HashMap<String, Long>();
    static final Set<String> warned = new HashSet<String>();
    static String summary;

    // ---------------- pure rules (JTWarCheck) ----------------

    /** Fleet points a request may use: the stock above what's kept for emergencies. */
    static float spare(float stock, float capacity, boolean rich) { return stock - (rich ? RICH_RESERVE : RESERVE) * capacity; }

    /** Size of a request: what it wants (bigger while spending freely), at least MIN_FP, within the spare points; 0 if that's under MIN_FP. */
    static float size(float want, boolean rich, float spare) {
        float fp = Math.round(Math.min(Math.max(MIN_FP, want * (rich ? RICH_SIZE : 1f)), spare));
        return fp < MIN_FP ? 0f : fp;
    }

    /** A base strike: twice the station's fleet points plus 100, at least 200. */
    static float strikeFP(float stationFP) { return Math.max(200f, 2f * stationFP + 100f); }

    static int perDay(boolean rich) { return rich ? RICH_PER_DAY : 1; }

    /**
     * Who pays: "reserve" for an emergency (anything above the credit reserve), "pool" while spending freely, else "budget" when
     * the month's budget covers it or "pool" when the surplus does; null when nothing may.
     */
    static String payer(boolean emergency, boolean rich, float cost, float credits, float reserve, float budget, float pool) {
        if (credits - cost < reserve) return null;
        if (emergency) return "reserve";
        if (rich) return pool >= cost ? "pool" : null;
        if (budget >= cost) return "budget";
        return pool >= cost ? "pool" : null;
    }

    // ---------------- the sector ----------------

    static boolean underWay(Class<?> type, MarketAPI target) {
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(type)) {
            if (!(i instanceof OffensiveFleetIntel)) continue;
            OffensiveFleetIntel o = (OffensiveFleetIntel) i;
            if (!o.isEnded() && !o.isEnding() && o.getTarget() == target) return true;
        }
        return false;
    }

    /** Remembers the markets that sent raids, invasions or blockades at your colonies (CAData.attackers), and when. */
    static void noteAttackers(CAData d) {
        Set<Object> mine = new HashSet<Object>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (m.getStarSystem() != null) mine.add(m.getStarSystem());
        try {
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(RaidIntel.class)) {
                if (!(i instanceof OffensiveFleetIntel) || i instanceof DefenseFleetIntel) continue;
                OffensiveFleetIntel o = (OffensiveFleetIntel) i;
                if (o.isEnded() || o.getFaction() == null || !o.getFaction().isHostileTo(Factions.PLAYER)) continue;
                MarketAPI t = o.getTarget();
                if (!(t != null && t.isPlayerOwned()) && !mine.contains(o.getSystem())) continue;
                MarketAPI from = o.getMarketFrom();
                if (from != null && !from.isPlayerOwned()) d.attackers().put(from.getId(), Global.getSector().getClock().getTimestamp());
            }
        } catch (Throwable ignored) { }
    }

    static float stationFP(MarketAPI m) {
        try { CampaignFleetAPI f = Misc.getStationFleet(m); return f == null ? 0f : f.getFleetPoints(); } catch (Throwable t) { return 0f; }
    }

    static MarketAPI source(MarketAPI target) {
        MarketAPI best = null; float bestDist = Float.MAX_VALUE;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!NexUtilsMarket.hasWorkingSpaceport(m) || m.getPrimaryEntity() == null || target.getPrimaryEntity() == null) continue;
            float dist = Misc.getDistanceLY(m.getPrimaryEntity(), target.getPrimaryEntity());
            if (dist < bestDist) { bestDist = dist; best = m; }
        }
        return best;
    }

    static float nearestColonyLY(MarketAPI target) {
        float best = Float.MAX_VALUE;
        for (MarketAPI m : Misc.getPlayerMarkets(false))
            if (m.getPrimaryEntity() != null && target.getPrimaryEntity() != null) best = Math.min(best, Misc.getDistanceLY(m.getPrimaryEntity(), target.getPrimaryEntity()));
        return best;
    }

    static String what(String kind) {
        return kind.equals("defense") ? "defense fleet" : kind.equals("strike") ? "base strike" : kind.equals("relief") ? "relief fleet" : "raid";
    }

    /** Launches one request as Nexerelin's fleet request dialog does; returns whether it went. */
    static boolean launch(CASettings s, InvasionFleetManager mgr, String kind, MarketAPI target, float want, boolean rich, boolean emergency, String why) {
        float fp = size(want, rich, spare(mgr.getFleetRequestStock(), mgr.getFleetRequestCapacity(), rich));
        if (fp <= 0f) return false;
        MarketAPI from = source(target);
        if (from == null) return false;
        ReliefFleetIntelAlt relief = null;
        if (kind.equals("relief")) {
            relief = ReliefFleetIntelAlt.createEvent(from, target);
            if (relief == null) return false;
            fp = relief.calcFP();
            if (fp > mgr.getFleetRequestStock()) { relief.endImmediately(); return false; }
        }
        float cost = Math.round(fp * NexConfig.fleetRequestCostPerFP * (relief != null ? Nex_FleetRequest.RELIEF_COST_MULT : 1f));
        String payer = payer(emergency, rich, cost, CABuild.credits(), s.reserve, CABudget.available(s), CASurplus.amount(s));
        if (payer == null) {
            if (relief != null) relief.endImmediately();
            return false;
        }
        try {
            if (relief != null) relief.setPlayerFee((int) cost);
            else {
                Nex_FleetRequest.FleetType type = kind.equals("defense") ? Nex_FleetRequest.FleetType.DEFENSE
                        : kind.equals("strike") ? Nex_FleetRequest.FleetType.BASESTRIKE : Nex_FleetRequest.FleetType.RAID;
                float time = Nex_FleetRequest.getTimeToLaunch(fp, type);
                OffensiveFleetIntel o = kind.equals("defense") ? new DefenseFleetIntel(Global.getSector().getPlayerFaction(), from, target, fp, time)
                        : kind.equals("strike") ? new BaseStrikeIntel(Global.getSector().getPlayerFaction(), from, target, fp, time)
                        : new NexRaidIntel(Global.getSector().getPlayerFaction(), from, target, fp, time);
                o.setPlayerFee((int) cost);
                o.setPlayerSpawned(true);
                o.init();
            }
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
            mgr.modifyFleetRequestStock(-(int) fp);
            if (!payer.equals("pool")) s.spent += cost;   // the surplus is what's left above the line, so it pays by being spent
            CALog.add(target.getName() + ": " + (int) fp + "-pt " + what(kind) + " from " + from.getName() + ", " + Misc.getDGSCredits(cost) + " (" + why + ")", true);
            return true;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + what(kind) + " request failed for " + target.getName(), t);
            return false;
        }
    }

    // ---------------- daily pass ----------------

    public static void run(List<MarketAPI> managed) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        summary = null;
        if (s.warMode == 2) return;
        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        if (mgr == null) return;
        noteAttackers(d);
        boolean rich = CASurplus.rich(s);
        int left = perDay(rich), started = 0;
        List<String> waiting = new ArrayList<String>();
        long now = Global.getSector().getClock().getTimestamp();

        // 1. relief for a colony under siege
        for (MarketAPI m : managed) {
            if (left <= 0) break;
            if (!CASiege.held(m)) continue;
            Long at = lastRelief.get(m.getId());
            if (at != null && Global.getSector().getClock().getElapsedDaysSince(at) < RELIEF_DAYS) continue;
            if (launch(s, mgr, "relief", m, 0f, rich, true, "under siege")) { lastRelief.put(m.getId(), now); left--; started++; }
            else waiting.add("relief for " + m.getName());
        }
        // 2. proactive defense: threat high or a crisis coming, before anything arrives
        Set<MarketAPI> crisis = CACrisis.targets();
        float defenseWant = Math.max(MIN_FP * 1.5f, 0.2f * mgr.getFleetRequestCapacity());
        for (MarketAPI m : managed) {
            if (left <= 0) break;
            if (m.getStarSystem() == null || !(d.colony(m.getId()).threatHigh || crisis.contains(m)) || CADefense.defenseUnderWay(m)) continue;
            if (launch(s, mgr, "defense", m, defenseWant, rich, true, crisis.contains(m) ? "crisis coming" : "threat high")) { left--; started++; }
            else waiting.add("defense at " + m.getName());
        }
        // 3. pirate and Pather bases near your colonies (every known one while spending freely)
        List<MarketAPI> bases = new ArrayList<MarketAPI>();
        try { bases.addAll(Nex_FleetRequest.getHiddenBases()); } catch (Throwable ignored) { }
        for (MarketAPI b : bases) {
            if (left <= 0) break;
            if (b.getFaction() == null || !b.getFaction().isHostileTo(Factions.PLAYER) || underWay(NexRaidIntel.class, b)) continue;
            if (!rich && nearestColonyLY(b) > BASE_LY) continue;
            if (launch(s, mgr, "strike", b, strikeFP(stationFP(b)), rich, false, b.getFaction().getDisplayName() + " base")) { left--; started++; }
            else waiting.add("strike on " + b.getName());
        }
        // 4. strike back at the markets that attacked you
        if (s.warMode == 0) for (String id : new ArrayList<String>(d.attackers().keySet())) {
            if (left <= 0) break;
            MarketAPI t = Global.getSector().getEconomy().getMarket(id);
            Long at = d.attackers().get(id);
            if (t == null || t.isPlayerOwned() || at == null || Global.getSector().getClock().getElapsedDaysSince(at) > ATTACK_DAYS
                    || t.getFaction() == null || !t.getFaction().isHostileTo(Factions.PLAYER)) { d.attackers().remove(id); continue; }
            if (underWay(NexRaidIntel.class, t)) continue;
            if (launch(s, mgr, "raid", t, Math.max(200f, 1.5f * CAItems.defenseFP(t)), rich, false, "it attacked you")) { left--; started++; }
            else waiting.add("raid on " + t.getName());
        }
        // 5. spending freely: a standing defense fleet at every colony, biggest first
        if (rich && left > 0) {
            List<MarketAPI> byBig = new ArrayList<MarketAPI>(managed);
            Collections.sort(byBig, new Comparator<MarketAPI>() {
                public int compare(MarketAPI a, MarketAPI b) { return b.getSize() - a.getSize(); }
            });
            for (MarketAPI m : byBig) {
                if (left <= 0) break;
                if (m.getStarSystem() == null || CADefense.defenseUnderWay(m)) continue;
                if (launch(s, mgr, "defense", m, defenseWant, true, false, "standing guard")) { left--; started++; }
                else break;   // out of points or pool: the rest wait too
            }
        }
        if (waiting.isEmpty()) warned.clear();   // one line per wait, not one a day
        else if (warned.add(waiting.get(0)))
            CALog.status("war:wait", "War fleets: waiting for points or credits (" + waiting.get(0) + (waiting.size() > 1 ? ", +" + (waiting.size() - 1) : "") + ")");
        summary = (rich ? "Spending freely: up to " + RICH_PER_DAY + " a day, keeping " + Math.round(RICH_RESERVE * 100f) + "%"
                : "One a day, keeping " + Math.round(RESERVE * 100f) + "%") + " of fleet request points for emergencies. Points "
                + (int) mgr.getFleetRequestStock() + " of " + mgr.getFleetRequestCapacity() + (started > 0 ? "; " + started + " sent today" : "")
                + (waiting.isEmpty() ? "." : "; waiting: " + CAContracts.join(waiting) + ".");
    }

    // ---------------- Mod features page ----------------

    static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        java.awt.Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        t.addSectionHeading("War fleets (Nexerelin)", Alignment.MID, 0f);
        t.addPara("Mode: %s (Settings, Colony policies). Relief for sieges, defense where a threat or crisis is coming, base strikes on "
                + "pirate and Pather bases" + (s.warMode == 0 ? ", raids on markets that attacked you" : "") + ". Never invasions or new "
                + "colonies, and only against factions already hostile to you.", 6f, hl, CASettings.WAR_NAMES[Math.max(0, Math.min(2, s.warMode))]);
        if (s.warMode == 2) return;
        if (summary != null) t.addPara(CALog.esc(summary), gray, 4f);
        int shown = 0;
        try {
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(RaidIntel.class)) {
                if (!(i instanceof OffensiveFleetIntel)) continue;
                OffensiveFleetIntel o = (OffensiveFleetIntel) i;
                if (!o.isPlayerSpawned() || o.isEnded() || o.isEnding() || o.getTarget() == null) continue;
                if (shown++ >= 8) break;
                String kind = o instanceof DefenseFleetIntel ? "Defense" : o instanceof BaseStrikeIntel ? "Base strike" : o instanceof NexRaidIntel ? "Raid" : "Fleet";
                t.addPara("%s: %s, %s points" + (o.getMarketFrom() != null ? " from " + CALog.esc(o.getMarketFrom().getName()) : "") + ".", 4f, hl,
                        kind, o.getTarget().getName(), "" + Math.round(o.getFP()));
            }
        } catch (Throwable ignored) { }
        if (shown == 0) t.addPara("No fleets of yours under way.", gray, 4f);
        if (s.warMode == 0 && !d.attackers().isEmpty()) {
            List<String> names = new ArrayList<String>();
            for (String id : d.attackers().keySet()) {
                MarketAPI m = Global.getSector().getEconomy().getMarket(id);
                if (m != null) names.add(m.getName());
            }
            if (!names.isEmpty()) t.addPara("Marked for strikes back (attacked you in the last " + (int) ATTACK_DAYS + " days): " + CALog.esc(CAContracts.join(names)) + ".", gray, 4f);
        }
    }
}
