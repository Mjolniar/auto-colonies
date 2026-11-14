package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.PerseanLeagueMembership;
import com.fs.starfarer.api.impl.campaign.intel.group.BlockadeFGI;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FleetRequest;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.fleets.BlockadeWrapperIntel;
import exerelin.campaign.intel.fleets.NexBlockadeFGI;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.ReliefFleetIntelAlt;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.invasion.RespawnInvasionIntel;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.campaign.intel.satbomb.SatBombIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;

import java.awt.Color;
import java.util.*;

/**
 * War fleets and the War level (Nexerelin). 0.2.35.0 made the defensive fleets; 0.3.0.0 (WS-E, research 06) adds the War level
 * (CASettings.warLevel, CAWarPolicy), the faction stance table (CASettings.factionStance, CAStance) and offense. Fleets are
 * requested exactly as Nexerelin's fleet request dialog does: Nexerelin's price, its fleet request points, its organize
 * and travel time. Once a day, sharing one launch counter, in this order:
 *  1. Relief (Defend): a colony under siege, once per colony every RELIEF_DAYS, as an emergency.
 *  2. Proactive defense (Defend): a colony whose threat is high or that a crisis targets. 0.3.0.0 (03 F10): an emergency
 *     only when hostile fleets or a raid really target the system (CADefense.realThreat); otherwise paid from the military
 *     rung, else the month's budget (the surplus while rich), at most once per system every 45 days.
 *  3. Retake (Retaliate, setting on): an invasion of a colony that was yours, your lost capital first (CAConquest).
 *  4. Base strikes (Defend): pirate, Pather and respawn bases within BASE_LY of a colony (any distance while rich, or for a
 *     Destroy faction), at twice their station's strength.
 *  5. Strike back (Retaliate): raids on markets that attacked you in the last ATTACK_DAYS days.
 *  6. Respawn watch (Destroy): a defense fleet where a respawn invasion is headed for a colony of yours, else a raid on its source.
 *  7. Hold them down (Hold): raids and blockades on Rival, Enemy and Destroy markets in reach, RAIDS_PER_FACTION at a time.
 *  8. Conquer (Conquer): invasions of Enemy (opportunistic) and Destroy (every market) markets (CAConquest).
 *  9. Scorched (Scorched, authorized): saturation bombardment of a Destroy faction's capital (CAScorched).
 * 10. Standing guard (rich): a defense fleet at every colony, biggest first, once per system every 45 days.
 * Each faction's effective level (CAWarPolicy.effective) caps what goes at it; nothing ever goes at a faction that isn't
 * hostile to you. Every launch keeps RESERVE of the fleet request capacity for emergencies (RICH_RESERVE while rich; 0.3.0.9:
 * relief fleets and emergency defense use it) and
 * never goes below the credit reserve. The operations new in 0.3.0 carry Auto Colonies' fee (CAWarPolicy.feeApplies).
 * Every launch is recorded in CAData.warOps ("kind:marketId" to when): ids and timestamps only.
 */
public class CAWar {
    static final String MOD = "nexerelin";
    // 0.3.0.0 (A3): the numbers live in CATuning
    static final float RESERVE = CATuning.WAR_RESERVE, RICH_RESERVE = CATuning.WAR_RICH_RESERVE, RICH_SIZE = CATuning.WAR_RICH_SIZE, MIN_FP = CATuning.WAR_MIN_FP;
    static final float BASE_LY = CATuning.WAR_BASE_LY, ATTACK_DAYS = CATuning.WAR_ATTACK_DAYS, RELIEF_DAYS = CATuning.WAR_RELIEF_DAYS;
    static final int RICH_PER_DAY = CATuning.WAR_RICH_PER_DAY;
    /**
     * 0.3.0.0: circuit breaker keys for the offensive parts, so one failing part doesn't stop the others. CONQUEST_KEY is retake
     * and conquer only; 0.3.0.0 (fix): CAConquest.tend has its own, CONQUERED_KEY (CAAuto).
     */
    static final String CONQUEST_KEY = "nexerelin:conquest", SCORCHED_KEY = "nexerelin:scorched", CONQUERED_KEY = "nexerelin:conquered";
    static final Map<String, Long> lastRelief = new HashMap<String, Long>();
    static final Set<String> warned = new HashSet<String>();
    static String summary;

    // ---------------- pure rules (JTWarCheck) ----------------

    /** Fleet points a request may use: the stock above what's kept for emergencies. */
    static float spare(float stock, float capacity, boolean rich) { return stock - (rich ? RICH_RESERVE : RESERVE) * capacity; }

    /**
     * Pure (0.3.0.9, JTWarCheck): as spare(), except a relief or emergency request (urgent) may use the whole stock: the
     * reserve is kept for them.
     */
    static float spare(float stock, float capacity, boolean rich, boolean urgent) { return urgent ? stock : spare(stock, capacity, rich); }

    /** Pure (0.3.0.9, JTWarCheck): a relief fleet, or anything paid as an emergency (a real attack on a colony). */
    static boolean urgent(String kind, String payKind) { return CAWarPolicy.EMERGENCY.equals(payKind) || CAWarPolicy.RELIEF.equals(kind); }

    /** Size of a request: what it wants (bigger while spending freely), at least MIN_FP, within the spare points; 0 if that's under MIN_FP. */
    static float size(float want, boolean rich, float spare) {
        float fp = Math.round(Math.min(Math.max(MIN_FP, want * (rich ? RICH_SIZE : 1f)), spare));
        return fp < MIN_FP ? 0f : fp;
    }

    /** A base strike: twice the station's fleet points plus 100, at least 200. */
    static float strikeFP(float stationFP) { return Math.max(200f, 2f * stationFP + 100f); }

    static int perDay(boolean rich) { return rich ? RICH_PER_DAY : 1; }

    /**
     * 0.2.45's rule, kept for its offline check: "reserve" for an emergency (anything above the credit reserve), "pool" while
     * spending freely, else "budget" when the month's budget covers it or "pool" when the surplus does; null when nothing may.
     * 0.3.0.0: launches use CAWarPolicy.payer, which gives the same answers for these cases.
     */
    static String payer(boolean emergency, boolean rich, float cost, float credits, float reserve, float budget, float pool) {
        return CAWarPolicy.payer(emergency ? CAWarPolicy.EMERGENCY : CAWarPolicy.RAID, rich, cost, credits, reserve, budget, pool);
    }

    /** The military rung (CARich.RUNGS) that pays proactive defense and standing guard (03 F10), and records what the surplus pays. */
    static int militaryRung() {
        for (int i = 0; i < CARich.RUNGS.length; i++) if ("Military".equals(CARich.RUNGS[i])) return i;
        return 3;
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

    /** 0.3.0.0: every fleet operation of any faction that hasn't ended, blockades included (blockades()). */
    static List<OffensiveFleetIntel> offensives() {
        List<OffensiveFleetIntel> out = new ArrayList<OffensiveFleetIntel>();
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(RaidIntel.class)) {
            if (!(i instanceof OffensiveFleetIntel)) continue;
            OffensiveFleetIntel o = (OffensiveFleetIntel) i;
            if (!o.isEnded() && !o.isEnding()) out.add(o);
        }
        for (OffensiveFleetIntel b : blockades()) if (!out.contains(b)) out.add(b);
        return out;
    }

    /**
     * 0.3.0.0 (fix): blockades that haven't ended. Nexerelin adds only a blockade's NexBlockadeFGI to the intel manager, never its
     * BlockadeWrapperIntel (the wrapper overrides addOrQueueIntelIfNeeded; JTWarLinkCheck asserts it), so they are read
     * through the FGI's wrapper.
     */
    static List<OffensiveFleetIntel> blockades() {
        List<OffensiveFleetIntel> out = new ArrayList<OffensiveFleetIntel>();
        try {
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(NexBlockadeFGI.class)) {
                if (!(i instanceof NexBlockadeFGI)) continue;
                NexBlockadeFGI g = (NexBlockadeFGI) i;
                if (g.isEnded() || g.isEnding()) continue;
                BlockadeWrapperIntel w = g.getWrapper();
                if (w != null && !w.isEnded() && !w.isEnding() && !out.contains(w)) out.add(w);
            }
        } catch (Throwable t) { CAStep.warn("war fleet step", "reading blockades failed", t); }   // deduplicated: offensives() runs many times a day
        return out;
    }

    /**
     * 0.3.0.0 (fix): an operation's stage, 0 organizing, 1 assembling, 2 travelling, 3 in action, 4 finishing. A blockade's
     * wrapper stays at stage 0 (one placeholder stage), so its stage comes from the FGI: pre-launch delay, gathering
     * (PREPARE_ACTION), travel, on station (PAYLOAD_ACTION), going home.
     */
    static int stageOf(OffensiveFleetIntel o) {
        if (o instanceof BlockadeWrapperIntel) {
            try {
                BlockadeFGI g = ((BlockadeWrapperIntel) o).getFgi();
                if (g == null || g.isInPreLaunchDelay()) return 0;
                if (g.isCurrent(GenericRaidFGI.PREPARE_ACTION)) return 1;
                if (g.isCurrent(GenericRaidFGI.TRAVEL_ACTION)) return 2;
                if (g.isCurrent(GenericRaidFGI.PAYLOAD_ACTION)) return 3;
                return 4;
            } catch (Throwable t) { return 4; }
        }
        return o.getCurrentStage();
    }

    /**
     * 0.3.0.0 (fix, 06 section 4.8): an operation whose result isn't in yet, so its capture, infamy or bombardment is still to
     * come. Nexerelin sets the outcome when an invasion takes the market or a bombardment lands, but the InvasionIntel stays
     * 90 more days at the market it took (InvWaitStage) and a SatBombIntel flies home after it bombs. Also decided: an
     * invasion whose market is yours already, and a bombardment of Auto Colonies' that the record shows as landed.
     */
    static boolean undecided(OffensiveFleetIntel o) {
        if (o.getOutcome() != null) return false;
        MarketAPI t = o.getTarget();
        if (t == null) return false;
        if (o instanceof InvasionIntel && t.getFaction() != null && t.getFaction().isPlayerFaction()) return false;
        if (o instanceof SatBombIntel) {
            try { if (CAScorched.landed(CAData.get(), t)) return false; } catch (Throwable ignored) { }
        }
        return true;
    }

    /** Whether a fleet operation is yours: the player faction's (Auto Colonies', your own requests, or Nexerelin's followers). */
    static boolean yours(OffensiveFleetIntel o) {
        FactionAPI f = o.getFaction();
        if (f == null) return false;
        if (f.isPlayerFaction()) return true;
        try { return f.getId().equals(PlayerFactionStore.getPlayerFactionId()) && o.isPlayerSpawned(); } catch (Throwable t) { return false; }
    }

    /**
     * 0.3.0.0 (06 section 4.5, the generalized underWay): an operation of yours of any kind already at this market, also your own
     * requests and Nexerelin followers' fleets, so targets don't double up.
     */
    static boolean oursAt(MarketAPI target) {
        if (target == null) return false;
        for (OffensiveFleetIntel o : offensives()) if (o.getTarget() == target && yours(o) && !(o instanceof DefenseFleetIntel)) return true;
        return false;
    }

    /** The saved-record kinds an operation of this class can be. */
    static String[] kindsOf(OffensiveFleetIntel o) {
        if (o instanceof InvasionIntel) return new String[]{CAWarPolicy.INVASION, CAWarPolicy.RETAKE};
        if (o instanceof SatBombIntel) return new String[]{CAWarPolicy.SATBOMB};
        if (o instanceof BlockadeWrapperIntel) return new String[]{CAWarPolicy.BLOCKADE};
        if (o instanceof BaseStrikeIntel) return new String[]{CAWarPolicy.STRIKE};
        if (o instanceof NexRaidIntel) return new String[]{CAWarPolicy.RAID, CAWarPolicy.HOLD_RAID};
        if (o instanceof DefenseFleetIntel) return new String[]{CAWarPolicy.DEFENSE};
        return new String[0];
    }

    /** Whether Auto Colonies launched this operation (its kind and target are in CAData.warOps). */
    static boolean cmOp(CAData d, OffensiveFleetIntel o) {
        if (o.getTarget() == null || !o.isPlayerSpawned()) return false;
        for (String k : kindsOf(o)) if (d.warOps().containsKey(k + ":" + o.getTarget().getId())) return true;
        return false;
    }

    /**
     * Your invasions under way (any of yours: Auto Colonies', your own requests, followers'), respawn invasions aside.
     * 0.3.0.0 (fix): only those still to land (undecided), so a capture's 90-day wait doesn't hold the next invasion.
     */
    static List<InvasionIntel> invasionsUnderWay() {
        List<InvasionIntel> out = new ArrayList<InvasionIntel>();
        for (OffensiveFleetIntel o : offensives())
            if (o instanceof InvasionIntel && !(o instanceof RespawnInvasionIntel) && yours(o) && undecided(o)) out.add((InvasionIntel) o);
        return out;
    }

    /** Your bombardments under way. 0.3.0.0 (fix): only those still to land (undecided), not a fleet flying home. */
    static int bombsUnderWay() {
        int n = 0;
        for (OffensiveFleetIntel o : offensives()) if (o instanceof SatBombIntel && yours(o) && undecided(o)) n++;
        return n;
    }

    /** Your raids and blockades under way at one faction's markets. */
    static int raidsAt(FactionAPI f) {
        int n = 0;
        for (OffensiveFleetIntel o : offensives()) {
            if (!yours(o) || o.getTarget() == null || o.getTarget().getFaction() != f) continue;
            if ((o instanceof NexRaidIntel && !(o instanceof BaseStrikeIntel)) || o instanceof BlockadeWrapperIntel) n++;
        }
        return n;
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

    /**
     * 0.3.0.0 (06 section 4.5): the source of an invasion or bombardment, by Nexerelin's own weighting
     * (InvasionFleetManager.getMarketWeightForInvasionSource: military buildings, size x stability, distance) among your
     * colonies with a working spaceport; the nearest one if the weighting can't be read.
     */
    static MarketAPI invasionSource(MarketAPI target) {
        MarketAPI best = null; float bestW = 0f;
        try {
            for (MarketAPI m : Misc.getPlayerMarkets(false)) {
                if (!NexUtilsMarket.hasWorkingSpaceport(m) || m.getPrimaryEntity() == null) continue;
                float w = InvasionFleetManager.getMarketWeightForInvasionSource(m, target.getLocationInHyperspace());
                if (w > bestW) { bestW = w; best = m; }
            }
        } catch (Throwable t) { best = null; }
        return best != null ? best : source(target);
    }

    static float nearestColonyLY(MarketAPI target) {
        float best = Float.MAX_VALUE;
        for (MarketAPI m : Misc.getPlayerMarkets(false))
            if (m.getPrimaryEntity() != null && target.getPrimaryEntity() != null) best = Math.min(best, Misc.getDistanceLY(m.getPrimaryEntity(), target.getPrimaryEntity()));
        return best;
    }

    static String what(String kind) {
        if (CAWarPolicy.DEFENSE.equals(kind) || CAWarPolicy.GUARD.equals(kind)) return "defense fleet";
        if (CAWarPolicy.STRIKE.equals(kind)) return "base strike";
        if (CAWarPolicy.RELIEF.equals(kind)) return "relief fleet";
        if (CAWarPolicy.BLOCKADE.equals(kind)) return "blockade";
        if (CAWarPolicy.INVASION.equals(kind) || CAWarPolicy.RETAKE.equals(kind)) return "invasion";
        if (CAWarPolicy.SATBOMB.equals(kind)) return "saturation bombardment";
        return "raid";
    }

    /** 0.3.0.0 (06 section 4.7): a market an operation may go at: not hidden, in the economy, reachable, not story-critical, not a derelict. */
    static boolean targetable(MarketAPI m) {
        if (m == null || m.isHidden() || !m.isInEconomy() || m.getPrimaryEntity() == null || m.isPlayerOwned()) return false;
        if (m.getContainingLocation() == null || m.getContainingLocation().isHyperspace()) return false;
        try { if (Misc.isStoryCritical(m)) return false; } catch (Throwable ignored) { }
        try { if (m.getStarSystem() != null && m.getStarSystem().hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false; } catch (Throwable ignored) { }
        try { if ("nex_derelict".equals(m.getFactionId()) || m.getMemoryWithoutUpdate().getBoolean("$nex_derelict")) return false; } catch (Throwable ignored) { }
        return true;
    }

    // ---------------- stances and the effective level ----------------

    /** The faction's stance, with "Pick rivals automatically": the top-threat hostile Auto faction counts as Rival. */
    static int stance(CASettings s, String factionId, String autoRival) {
        int st = s.stanceOf(factionId);
        if (st == CAStance.AUTO && factionId != null && factionId.equals(autoRival)) return CAStance.RIVAL;
        return st;
    }

    /**
     * "Pick rivals automatically" (warAutoRivals, off by default): the hostile Auto faction with the highest threat
     * (CAWarPolicy.rivalThreat), or null. Never makes anyone Enemy or Destroy.
     */
    static String autoRival(CAData d, CASettings s) {
        if (!s.warAutoRivals) return null;
        Map<String, Float> threat = new HashMap<String, Float>();
        Map<String, int[]> counts = new HashMap<String, int[]>();   // {attacks, near, offensives}
        try {
            for (String id : d.attackers().keySet()) {
                MarketAPI m = Global.getSector().getEconomy().getMarket(id);
                if (m != null && m.getFaction() != null) count(counts, m.getFactionId())[0]++;
            }
            List<MarketAPI> mine = Misc.getPlayerMarkets(false);
            for (MarketAPI o : Global.getSector().getEconomy().getMarketsCopy()) {
                if (o.isPlayerOwned() || o.getFaction() == null || o.getPrimaryEntity() == null) continue;
                for (MarketAPI pm : mine) {
                    if (pm.getPrimaryEntity() != null && Misc.getDistanceLY(pm.getPrimaryEntity(), o.getPrimaryEntity()) <= 8f) { count(counts, o.getFactionId())[1]++; break; }
                }
            }
            for (OffensiveFleetIntel o : offensives())
                if (o.getTarget() != null && o.getTarget().isPlayerOwned() && o.getFaction() != null) count(counts, o.getFaction().getId())[2]++;
        } catch (Throwable ignored) { }
        String best = null; float bestT = 0f;
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            FactionAPI f = Global.getSector().getFaction(e.getKey());
            if (f == null || f.isPlayerFaction() || !f.isHostileTo(Factions.PLAYER) || s.stanceOf(e.getKey()) != CAStance.AUTO) continue;
            float t = CAWarPolicy.rivalThreat(e.getValue()[0], e.getValue()[1], e.getValue()[2]);
            threat.put(e.getKey(), t);
            if (t > bestT) { bestT = t; best = e.getKey(); }
        }
        return best;
    }

    static int[] count(Map<String, int[]> m, String fid) {
        int[] c = m.get(fid);
        if (c == null) { c = new int[3]; m.put(fid, c); }
        return c;
    }

    /** Allied with you (Nexerelin alliances), or the Persean League while you're a member. */
    static boolean allied(FactionAPI f) {
        try { if (AllianceManager.areFactionsAllied(f.getId(), Factions.PLAYER)) return true; } catch (Throwable ignored) { }
        try { if (Factions.PERSEAN.equals(f.getId()) && PerseanLeagueMembership.isLeagueMember()) return true; } catch (Throwable ignored) { }
        return false;
    }

    /** A ceasefire with you is in force (Nexerelin's diplomacy brain). */
    static boolean ceasefire(FactionAPI f) {
        try {
            DiplomacyManager dm = DiplomacyManager.getManager();
            DiplomacyBrain brain = dm == null ? null : dm.getDiplomacyBrain(f.getId());
            return brain != null && brain.hasCeasefireWith(Factions.PLAYER);
        } catch (Throwable t) { return false; }
    }

    /** Your commissioner, or a faction not hostile to it (operations would be against your commissioner's friends). */
    static boolean commissionerSide(FactionAPI f) {
        FactionAPI comm = Misc.getCommissionFaction();
        return comm != null && (comm == f || !f.isHostileTo(comm));
    }

    /** The highest War level Auto Colonies may use against this faction today (CAWarPolicy.effective). */
    static int effective(CAData d, CASettings s, FactionAPI f, String autoRival) {
        if (f == null || f.isPlayerFaction()) return CAWarPolicy.OFF;
        int st = stance(s, f.getId(), autoRival);
        return CAWarPolicy.effective(s.warLevel, st, f.isHostileTo(Factions.PLAYER), d.warAuthorized().contains(f.getId()), allied(f),
                commissionerSide(f), ceasefire(f), CALowProfile.on(), Misc.getCommissionFaction() != null);
    }

    /** One day's pass: the counter every launch shares, what waits, and each faction's stance and level worked out once. */
    static final class Pass {
        final CAData d; final CASettings s; final InvasionFleetManager mgr; final boolean rich; final int level; final String rival;
        final long now;
        /** 0.3.0.0 (fix): you hold a commission (no invasion of any kind, CAWarPolicy.allows). */
        final boolean commissioned;
        int left, started;
        final List<String> waiting = new ArrayList<String>();
        final Map<String, Integer> eff = new HashMap<String, Integer>();

        Pass(CAData d, CASettings s, InvasionFleetManager mgr, boolean rich) {
            this.d = d; this.s = s; this.mgr = mgr; this.rich = rich;
            this.level = CAWarPolicy.clamp(s.warLevel);
            this.rival = autoRival(d, s);
            this.now = Global.getSector().getClock().getTimestamp();
            this.left = perDay(rich);
            this.commissioned = Misc.getCommissionFaction() != null;
        }

        int eff(FactionAPI f) {
            if (f == null) return CAWarPolicy.OFF;
            Integer v = eff.get(f.getId());
            if (v == null) { v = effective(d, s, f, rival); eff.put(f.getId(), v); }
            return v;
        }

        int stance(FactionAPI f) { return f == null ? CAStance.AUTO : CAWar.stance(s, f.getId(), rival); }

        /** Whether this kind of operation may go at this faction today (and it's hostile to you, for an attack). */
        boolean allows(String kind, FactionAPI f) {
            if (f == null) return false;
            if (CAWarPolicy.offensive(kind) && !f.isHostileTo(Factions.PLAYER)) return false;
            return CAWarPolicy.allows(kind, eff(f), stance(f), commissioned);
        }

        /** 0.3.0.9: less the points a waiting Auto-expansion proposal keeps (CAExpansion.reservedFP), as send() counts them. */
        float spare() { return CAWar.spare(mgr.getFleetRequestStock() - CAExpansion.reservedFP(), mgr.getFleetRequestCapacity(), rich); }

        void went() { left--; started++; }
    }

    // ---------------- paying and recording ----------------

    /** Days since a saved operation record, or MAX_VALUE when there's none. */
    static float daysSince(CAData d, String key) {
        Long at = d.warOps().get(key);
        if (at == null) return Float.MAX_VALUE;
        float days = Global.getSector().getClock().getElapsedDaysSince(at);
        return days < 0f ? Float.MAX_VALUE : days;   // a record from a later date (an earlier save loaded): as if none
    }

    static void record(CAData d, String key) { d.warOps().put(key, Global.getSector().getClock().getTimestamp()); }

    /** Drops operation records older than a year (ids and timestamps; keeps the saved map small). */
    static void tidyOps(CAData d) {
        for (Iterator<Map.Entry<String, Long>> it = d.warOps().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() == null || Global.getSector().getClock().getElapsedDaysSince(e.getValue()) > CAWarPolicy.OP_MEMORY_DAYS) it.remove();
        }
    }

    /** The fee on an operation (CAWarPolicy.fee with the fee setting). */
    static float fee(CASettings s, String kind, float price) {
        if (!CAWarPolicy.feeApplies(kind)) return 0f;
        return Math.round(CABudget.fee(s, CAWarPolicy.FEE_SHARE * price));
    }

    /** Who pays price + fee for this kind (CAWarPolicy.payer), with today's budget, surplus and military rung. */
    static String payerFor(CASettings s, String payKind, boolean rich, float total) {
        // 0.3.0.8: the Military rung pays only while it is On. On Recommend only or Off, standing guard and proactive defense
        // draw on neither the rung nor the surplus pool; they use the month's budget, as before the money ladder
        int r = militaryRung(), mode = CARich.ON;
        try { mode = CARich.mode(r); } catch (Throwable ignored) { }
        float rung = 0f;
        if (mode == CARich.ON) try { rung = CARich.allowance(r); } catch (Throwable ignored) { }
        boolean guard = CAWarPolicy.DEFENSE.equals(payKind) || CAWarPolicy.GUARD.equals(payKind);
        boolean poolOk = rich && !(guard && mode != CARich.ON);
        return CAWarPolicy.payer(payKind, poolOk, total, CABuild.credits(), s.reserve, CABudget.available(s), CASurplus.amount(s), rung);
    }

    /**
     * Pays through CABudget, the one place credits move (0.3.0.0, C1): from the surplus or the military rung with payFromPool
     * (not counted against the month's budget, as 0.2.45 did for the surplus), otherwise with pay (counted against it).
     */
    static void pay(CASettings s, String payer, float price, float fee) {
        if ("pool".equals(payer) || "rung".equals(payer)) CABudget.payFromPool(s, militaryRung(), null, price, fee);
        else CABudget.pay(s, null, price, fee, 0f);
    }

    static String money(float price, float fee) {
        return Misc.getDGSCredits(price) + (fee > 0f ? " + " + Misc.getDGSCredits(fee) + " fee" : "");
    }

    // ---------------- launching ----------------

    /** 0.2.45's launch, kept for the relief CAConquest sends: true when it went. */
    static boolean launch(CASettings s, InvasionFleetManager mgr, String kind, MarketAPI target, float want, boolean rich, boolean emergency, String why) {
        return send(CAData.get(), s, mgr, kind, emergency ? CAWarPolicy.EMERGENCY : kind, target, want, rich, why, kind + ":" + target.getId()) == null;
    }

    /**
     * The fleet points send() asks for: want sized (RICH_SIZE while rich, at least MIN_FP) within the spare points. 0.3.0.0
     * (WS-D): less the points a waiting Auto-expansion proposal would use, except for an urgent request. 0.3.0.9: an urgent
     * one (relief, emergency) also uses the reserve (spare with urgent). The standing guard's credit wait prices this same size.
     */
    static float sendFP(InvasionFleetManager mgr, String kind, String payKind, float want, boolean rich) {
        boolean urgent = urgent(kind, payKind);
        return size(want, rich, spare(mgr.getFleetRequestStock() - (urgent ? 0f : CAExpansion.reservedFP()), mgr.getFleetRequestCapacity(), rich, urgent));
    }

    /**
     * Launches one relief, defense, base strike, raid or blockade as Nexerelin's fleet request dialog does. payKind picks who
     * pays (CAWarPolicy.payer); recordKey is what CAData.warOps keeps. Returns null when it went, or why it waits.
     */
    static String send(CAData d, CASettings s, InvasionFleetManager mgr, String kind, String payKind, MarketAPI target, float want,
                       boolean rich, String why, String recordKey) {
        float fp = sendFP(mgr, kind, payKind, want, rich);
        if (fp <= 0f) return "points";
        MarketAPI from = source(target);
        if (from == null) return "no spaceport";
        ReliefFleetIntelAlt relief = null;
        if (CAWarPolicy.RELIEF.equals(kind)) {
            relief = ReliefFleetIntelAlt.createEvent(from, target);
            if (relief == null) return "no relief";
            fp = relief.calcFP();
            if (fp > mgr.getFleetRequestStock()) { relief.endImmediately(); return "points"; }
        }
        // Nexerelin's own prices (Nex_FleetRequest.updateCost): fleet points x the cost per point, but a relief fleet is twice the
        // colony's stabilize package (needed credits plus the goods), whatever its size (0.2.37.0: it was priced by points, far too cheap)
        float cost = Math.round(relief != null ? 2f * com.fs.starfarer.api.impl.campaign.rulecmd.Nex_StabilizePackage.getNominalCost(target)
                : fp * NexConfig.fleetRequestCostPerFP);
        float fee = fee(s, kind, cost);
        String payer = payerFor(s, payKind, rich, cost + fee);
        if (payer == null) {
            if (relief != null) relief.endImmediately();
            return "credits";
        }
        try {
            if (relief != null) relief.setPlayerFee((int) cost);
            else {
                FactionAPI pf = Global.getSector().getPlayerFaction();
                OffensiveFleetIntel o;
                if (CAWarPolicy.DEFENSE.equals(kind) || CAWarPolicy.GUARD.equals(kind))
                    o = new DefenseFleetIntel(pf, from, target, fp, Nex_FleetRequest.getTimeToLaunch(fp, Nex_FleetRequest.FleetType.DEFENSE));
                else if (CAWarPolicy.STRIKE.equals(kind))
                    o = new BaseStrikeIntel(pf, from, target, fp, Nex_FleetRequest.getTimeToLaunch(fp, Nex_FleetRequest.FleetType.BASESTRIKE));
                else if (CAWarPolicy.BLOCKADE.equals(kind))
                    o = new BlockadeWrapperIntel(pf, from, target, fp, Nex_FleetRequest.getTimeToLaunch(fp, Nex_FleetRequest.FleetType.BLOCKADE));
                else o = new NexRaidIntel(pf, from, target, fp, Nex_FleetRequest.getTimeToLaunch(fp, Nex_FleetRequest.FleetType.RAID));
                o.setPlayerSpawned(true);
                o.init();
                // 0.3.0.8: the fee only after init went through, as CAConquest.launch: an operation that ends at once refunds
                // its player fee (OffensiveFleetIntel.refundPlayerFeeIfNeeded), so nothing is charged and no points are taken
                if (o.isEnded() || o.isEnding()) return "no route";
                o.setPlayerFee((int) cost);
            }
            pay(s, payer, cost, fee);
            mgr.modifyFleetRequestStock(-(int) fp);
            if (recordKey != null) record(d, recordKey);
            CALog.did(target.getName() + ": " + (int) fp + "-pt " + what(kind) + " from " + from.getName() + ", " + money(cost, fee) + " (" + why + ").", true);
            return null;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + what(kind) + " request failed for " + target.getName(), t);
            return "failed";
        }
    }

    /** Short text for a wait reason from send(). */
    static String waitText(String why) {
        return "points".equals(why) ? "fleet points" : "credits".equals(why) ? "credits" : why;
    }

    // ---------------- daily pass ----------------

    public static void run(List<MarketAPI> managed) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        summary = null;
        CAWarListener.ensure();   // 0.3.0.0: captures and bombardments are reported at any War level
        if (CAWarPolicy.clamp(s.warLevel) == CAWarPolicy.OFF) return;
        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        if (mgr == null) return;
        noteAttackers(d);
        tidyOps(d);
        Pass p = new Pass(d, s, mgr, CASurplus.rich(s));

        relief(p, managed);
        proactive(p, managed);
        if (p.level >= CAWarPolicy.RETALIATE && s.warRetake() && p.left > 0 && CABreaker.allowed(CONQUEST_KEY)) {
            boolean ok = true;
            try { if (CAConquest.retake(p)) p.went(); }
            catch (Throwable t) { ok = false; CADiagnostics.LOG.warn(CADiagnostics.TAG + "retake step failed", t); }
            CABreaker.check(CONQUEST_KEY, ok, "retaking your colonies");
        }
        baseStrikes(p);
        strikeBack(p);
        respawnWatch(p, managed);
        holdDown(p);
        if (p.level >= CAWarPolicy.CONQUER && p.left > 0 && CABreaker.allowed(CONQUEST_KEY)) {
            boolean ok = true;
            try { if (CAConquest.conquer(p)) p.went(); }
            catch (Throwable t) { ok = false; CADiagnostics.LOG.warn(CADiagnostics.TAG + "conquer step failed", t); }
            CABreaker.check(CONQUEST_KEY, ok, "invasions");
        }
        if (p.level >= CAWarPolicy.SCORCHED && p.left > 0 && CABreaker.allowed(SCORCHED_KEY)) {
            boolean ok = true;
            try { if (CAScorched.step(p)) p.went(); }
            catch (Throwable t) { ok = false; CADiagnostics.LOG.warn(CADiagnostics.TAG + "scorched step failed", t); }
            CABreaker.check(SCORCHED_KEY, ok, "saturation bombardment");
        }
        standingGuard(p, managed);

        if (p.waiting.isEmpty()) warned.clear();   // one line per wait, not one a day
        else if (warned.add(p.waiting.get(0)))
            CALog.thinkStatus("war:wait", "War fleets: waiting (" + p.waiting.get(0) + (p.waiting.size() > 1 ? ", +" + (p.waiting.size() - 1) : "") + ").");
        summary = "War level " + CAWarPolicy.levelName(p.level) + ". " + (p.rich ? "Rich: up to " + RICH_PER_DAY + " a day, keeping " + Math.round(RICH_RESERVE * 100f)
                : "One a day, keeping " + Math.round(RESERVE * 100f)) + " percent of fleet request points for emergencies. Points "
                + (int) mgr.getFleetRequestStock() + " of " + mgr.getFleetRequestCapacity() + (p.started > 0 ? "; " + p.started + " sent today" : "")
                + (p.waiting.isEmpty() ? "." : "; waiting: " + CAContracts.join(p.waiting) + ".");
    }

    /** 1. Relief for a colony under siege (Defend), an emergency. */
    static void relief(Pass p, List<MarketAPI> managed) {
        for (MarketAPI m : managed) {
            if (p.left <= 0) break;
            if (!CASiege.held(m)) continue;
            Long at = lastRelief.get(m.getId());
            if (at != null && Global.getSector().getClock().getElapsedDaysSince(at) < RELIEF_DAYS) continue;
            if (launch(p.s, p.mgr, CAWarPolicy.RELIEF, m, 0f, p.rich, true, "under siege")) { lastRelief.put(m.getId(), p.now); p.went(); }
            else p.waiting.add("relief for " + m.getName());
        }
    }

    /**
     * 2. Proactive defense (Defend): threat high or a crisis coming, before anything arrives. 0.3.0.0 (03 F10): paid as an
     * emergency only for a real attack on the system; otherwise as guard (military rung, budget), once per system every 45 days.
     */
    static void proactive(Pass p, List<MarketAPI> managed) {
        Set<MarketAPI> crisis = CACrisis.targets();
        float want = Math.max(MIN_FP * 1.5f, 0.2f * p.mgr.getFleetRequestCapacity());
        for (MarketAPI m : managed) {
            if (p.left <= 0) break;
            StarSystemAPI sys = m.getStarSystem();
            if (sys == null || !(p.d.colony(m.getId()).threatHigh || crisis.contains(m)) || CADefense.defenseUnderWay(m)) continue;
            boolean real = CADefense.realThreat(m);
            String key = "guard:" + sys.getId();
            if (!real && daysSince(p.d, key) < CAWarPolicy.DEFENSE_COOLDOWN_DAYS) continue;
            String why = real ? "hostiles at the system" : crisis.contains(m) ? "crisis coming" : "threat high";
            String w = send(p.d, p.s, p.mgr, CAWarPolicy.DEFENSE, real ? CAWarPolicy.EMERGENCY : CAWarPolicy.GUARD, m, want, p.rich, why, key);
            if (w == null) p.went();
            else p.waiting.add("defense at " + m.getName() + " (" + waitText(w) + ")");
        }
    }

    /** 4. Base strikes on hidden bases of factions you may attack: within BASE_LY, any distance while rich or for a Destroy faction. */
    static void baseStrikes(Pass p) {
        List<MarketAPI> bases = new ArrayList<MarketAPI>();
        try { bases.addAll(Nex_FleetRequest.getHiddenBases()); } catch (Throwable ignored) { }
        for (MarketAPI b : bases) {
            if (p.left <= 0) break;
            FactionAPI f = b.getFaction();
            if (f == null || !p.allows(CAWarPolicy.STRIKE, f) || underWay(NexRaidIntel.class, b)) continue;
            if (!p.rich && p.stance(f) < CAStance.DESTROY && nearestColonyLY(b) > BASE_LY) continue;
            String w = send(p.d, p.s, p.mgr, CAWarPolicy.STRIKE, CAWarPolicy.STRIKE, b, strikeFP(stationFP(b)), p.rich, f.getDisplayName() + " base", CAWarPolicy.STRIKE + ":" + b.getId());
            if (w == null) p.went();
            else p.waiting.add("strike on " + b.getName() + " (" + waitText(w) + ")");
        }
    }

    /** 5. Strike back (Retaliate) at markets that attacked you, while their faction may be attacked (never in Low profile). */
    static void strikeBack(Pass p) {
        for (String id : new ArrayList<String>(p.d.attackers().keySet())) {
            if (p.left <= 0) break;
            MarketAPI t = Global.getSector().getEconomy().getMarket(id);
            Long at = p.d.attackers().get(id);
            if (t == null || t.isPlayerOwned() || at == null || Global.getSector().getClock().getElapsedDaysSince(at) > ATTACK_DAYS
                    || t.getFaction() == null || !t.getFaction().isHostileTo(Factions.PLAYER)) { p.d.attackers().remove(id); continue; }
            if (!p.allows(CAWarPolicy.RAID, t.getFaction()) || underWay(NexRaidIntel.class, t)) continue;
            String w = send(p.d, p.s, p.mgr, CAWarPolicy.RAID, CAWarPolicy.RAID, t, Math.max(200f, 1.5f * CAItems.defenseFP(t)), p.rich, "it attacked you", CAWarPolicy.RAID + ":" + t.getId());
            if (w == null) p.went();
            else p.waiting.add("raid on " + t.getName() + " (" + waitText(w) + ")");
        }
    }

    /** 6. Respawn watch (Destroy): a respawn invasion headed for a colony of yours gets a defense fleet there, any other a raid on its source. */
    static void respawnWatch(Pass p, List<MarketAPI> managed) {
        List<RespawnInvasionIntel> respawns;
        try { respawns = RespawnInvasionIntel.getOngoing(); } catch (Throwable t) { return; }
        if (respawns == null) return;
        for (RespawnInvasionIntel r : respawns) {
            if (p.left <= 0) break;
            if (r.isEnded() || r.isEnding() || r.getFaction() == null || p.stance(r.getFaction()) < CAStance.DESTROY) continue;
            MarketAPI t = r.getTarget(), from = r.getMarketFrom();
            if (t != null && t.isPlayerOwned() && managed.contains(t)) {
                if (CADefense.defenseUnderWay(t) || p.eff(r.getFaction()) < CAWarPolicy.DEFEND) continue;
                String w = send(p.d, p.s, p.mgr, CAWarPolicy.DEFENSE, CAWarPolicy.GUARD, t, Math.max(MIN_FP * 1.5f, r.getFP()), p.rich,
                        r.getFaction().getDisplayName() + " respawn invasion", "guard:" + (t.getStarSystem() != null ? t.getStarSystem().getId() : t.getId()));
                if (w == null) p.went();
            } else if (from != null && targetable(from) && p.allows(CAWarPolicy.HOLD_RAID, from.getFaction()) && !oursAt(from)) {
                String w = send(p.d, p.s, p.mgr, CAWarPolicy.HOLD_RAID, CAWarPolicy.HOLD_RAID, from, Math.max(200f, 1.5f * CAItems.defenseFP(from)), p.rich,
                        r.getFaction().getDisplayName() + " respawn invasion leaves from here", CAWarPolicy.HOLD_RAID + ":" + from.getId());
                if (w == null) p.went();
                else p.waiting.add("raid on " + from.getName() + " (" + waitText(w) + ")");
            }
        }
    }

    /** A military industry is there (raid it; blockade the rest). */
    static boolean military(MarketAPI m) {
        return m.hasIndustry(Industries.HIGHCOMMAND) || m.hasIndustry(Industries.MILITARYBASE) || m.hasIndustry(Industries.PATROLHQ);
    }

    /** 7. Hold them down (Hold): raids on military markets and blockades on the rest, nearest first, RAIDS_PER_FACTION per faction. */
    static void holdDown(Pass p) {
        if (p.level < CAWarPolicy.HOLD || p.left <= 0) return;
        Map<FactionAPI, List<MarketAPI>> byFaction = new LinkedHashMap<FactionAPI, List<MarketAPI>>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            FactionAPI f = m.getFaction();
            if (f == null || f.isPlayerFaction() || p.stance(f) < CAStance.RIVAL || !targetable(m)) continue;
            if (!p.allows(CAWarPolicy.HOLD_RAID, f)) continue;
            if (!p.rich && nearestColonyLY(m) > BASE_LY) continue;
            List<MarketAPI> l = byFaction.get(f);
            if (l == null) { l = new ArrayList<MarketAPI>(); byFaction.put(f, l); }
            l.add(m);
        }
        for (Map.Entry<FactionAPI, List<MarketAPI>> e : byFaction.entrySet()) {
            if (p.left <= 0) break;
            if (raidsAt(e.getKey()) >= CAWarPolicy.RAIDS_PER_FACTION) continue;
            List<MarketAPI> l = e.getValue();
            Collections.sort(l, new Comparator<MarketAPI>() {
                public int compare(MarketAPI a, MarketAPI b) { return Float.compare(nearestColonyLY(a), nearestColonyLY(b)); }
            });
            for (MarketAPI t : l) {
                // 0.3.0.0 (fix): also while a blockade Auto Colonies sent there can still be on station (the saved record)
                if (oursAt(t) || daysSince(p.d, CAWarPolicy.BLOCKADE + ":" + t.getId()) < CAWarPolicy.BLOCKADE_DAYS) continue;
                boolean raid = military(t);
                String kind = raid ? CAWarPolicy.HOLD_RAID : CAWarPolicy.BLOCKADE;
                float want = raid ? Math.max(200f, 1.5f * CAItems.defenseFP(t)) : blockadeFP(t);
                String w = send(p.d, p.s, p.mgr, kind, kind, t, want, p.rich, CAStance.name(p.stance(e.getKey())) + ": " + e.getKey().getDisplayName(), kind + ":" + t.getId());
                if (w == null) p.went();
                else p.waiting.add(what(kind) + " at " + t.getName() + " (" + waitText(w) + ")");
                break;   // one a faction a day
            }
        }
    }

    /** A blockade's size: 1.25 x Nexerelin's wanted fleet for the market, at least 200. */
    static float blockadeFP(MarketAPI t) {
        try { return Math.max(200f, CAWarPolicy.FP_MARGIN * InvasionFleetManager.getWantedFleetSize(Global.getSector().getPlayerFaction(), t, 0f, false)); }
        catch (Throwable e) { return Math.max(200f, 1.5f * CAItems.defenseFP(t)); }
    }

    /** 10. Rich only: a standing defense fleet at every colony without one, biggest first, once per system every 45 days. */
    static void standingGuard(Pass p, List<MarketAPI> managed) {
        if (!p.rich || p.left <= 0) { CARich.wantFleets(0f); return; }   // 0.3.0.0 (C3 fix): no guard due for the Military rung to hold
        float want = Math.max(MIN_FP * 1.5f, 0.2f * p.mgr.getFleetRequestCapacity());
        float due = 0f;   // 0.3.0.0 (C3 fix): price and fee of the first guard that waits for credits (CARich.wantFleets)
        List<MarketAPI> byBig = new ArrayList<MarketAPI>(managed);
        Collections.sort(byBig, new Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) { return b.getSize() - a.getSize(); }
        });
        for (MarketAPI m : byBig) {
            if (p.left <= 0) break;
            if (m.getStarSystem() == null || CADefense.defenseUnderWay(m)) continue;
            String key = "guard:" + m.getStarSystem().getId();
            if (daysSince(p.d, key) < CAWarPolicy.DEFENSE_COOLDOWN_DAYS) continue;
            String w = send(p.d, p.s, p.mgr, CAWarPolicy.GUARD, CAWarPolicy.GUARD, m, want, true, "standing guard", key);
            if (w == null) p.went();
            else {
                if ("credits".equals(w)) {
                    // 0.3.0.9: priced at the size send() asked for (sendFP: want x 1.5 while rich, within the spare points)
                    float c = Math.round(sendFP(p.mgr, CAWarPolicy.GUARD, CAWarPolicy.GUARD, want, true) * NexConfig.fleetRequestCostPerFP);
                    due = c + fee(p.s, CAWarPolicy.GUARD, c);
                }
                break;   // out of points or money: the rest wait too
            }
        }
        CARich.wantFleets(due);   // 0.3.0.0 (C3 fix): the Military rung holds this much of its share for the guard
    }

    // ---------------- stand down (06 section 4.11) ----------------

    /**
     * 0.3.0.0 (fix, 06 section 4.11): an invasion whose ground battle has started: its troops are on the ground. Its stage stays
     * 3 until every route is done (InvActionStage), and its GroundBattleIntel runs on its own, so cancelling it would refund
     * 33 percent while the battle can still take the market, and would let the next invasion launch during it. Read from
     * InvasionIntel.getGroundBattle, else from a ground battle at its market; kept (true) when neither can be read.
     */
    static boolean troopsLanded(OffensiveFleetIntel o) {
        if (!(o instanceof InvasionIntel)) return false;
        try { return ((InvasionIntel) o).getGroundBattle() != null; }
        catch (Throwable t) {
            try { return o.getTarget() != null && GroundBattleIntel.getOngoing(o.getTarget()) != null; }
            catch (Throwable t2) { return true; }
        }
    }

    /**
     * The emergency stop: War level to Defend (Off stays Off), bombardment authorization and every war confirmation cleared,
     * and Auto Colonies' offensives still organizing or assembling cancelled (Nexerelin refunds its whole price; the fee
     * isn't refunded). recall also cancels those travelling (67% back) or in action (33%). Fleet request points never come back.
     * 0.3.0.0 (fix): an invasion with troops on the ground (troopsLanded) is neither cancelled nor recalled.
     */
    static void standDown(CAData d, CASettings s, boolean recall) {
        int was = CAWarPolicy.clamp(s.warLevel);
        s.warLevel = CAWarPolicy.standDownLevel(was);
        s.warScorchedOK = false;
        d.warAuthorized().clear();
        d.warStandDown = Global.getSector().getClock().getTimestamp();
        int cancelled = 0, moving = 0, landed = 0; float refund = 0f;
        List<OffensiveFleetIntel> mine = new ArrayList<OffensiveFleetIntel>();
        for (OffensiveFleetIntel o : offensives()) if (!(o instanceof DefenseFleetIntel) && cmOp(d, o)) mine.add(o);
        for (OffensiveFleetIntel o : mine) {
            int stage = stageOf(o);
            boolean troops = troopsLanded(o);
            if (troops && (CAWarPolicy.cancelOnStandDown(stage) || CAWarPolicy.recallable(stage))) landed++;   // not one that already took its market
            if (CAWarPolicy.stopsOnStandDown(stage, recall, troops)) {
                float back = o.getPlayerFee() * CAWarPolicy.refundMult(stage);
                refund += back;
                // 0.3.0.0 (fix): Nexerelin refunds a blockade's whole fee at any stage (its wrapper stays at stage 0); the fee is
                // set to the stage's share first, so a recalled blockade gets 67 or 33 percent back like every other operation
                if (o instanceof BlockadeWrapperIntel) o.setPlayerFee(Math.round(back));
                try { o.terminateEvent(OffensiveFleetIntel.OffensiveOutcome.OTHER); cancelled++; }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "cancelling an operation failed", t); }
            } else if (!troops && CAWarPolicy.recallable(stage)) moving++;
        }
        CALog.did("War: " + (recall ? "recalled" : "stood down") + ". Level " + CAWarPolicy.levelName(s.warLevel) + " (was " + CAWarPolicy.levelName(was)
                + "). War confirmations and bombardment authorization cleared. " + cancelled + " operation(s) cancelled, about "
                + Misc.getDGSCredits(refund) + " back from Nexerelin." + (moving > 0 ? " " + moving + " travelling or in action (Recall all on the War page)." : "")
                + (landed > 0 ? " " + landed + " invasion(s) with troops on the ground kept." : ""), true);
    }

    // ---------------- War page (WS-F wires it; buttons are {"war", action, arg}) ----------------

    /** Buttons that need the intel's confirmation dialog: declare, scorched, recall, standdown. */
    public static boolean needsConfirm(String action) {
        return "declare".equals(action) || "scorched".equals(action) || "recall".equals(action) || "standdown".equals(action);
    }

    /** The confirmation text for a War page button. */
    public static void confirmPrompt(TooltipMakerAPI t, String action, String arg) {
        Color hl = Misc.getHighlightColor(), bad = Misc.getNegativeHighlightColor();
        if ("declare".equals(action)) {
            FactionAPI f = Global.getSector().getFaction(arg);
            if (f == null) return;
            t.addPara("Declare war on %s?", 0f, bad, f.getDisplayName());
            List<String> lines = CADiplomacy.preview(f);
            if (lines != null) for (String l : lines) t.addPara(CALog.esc(l), 4f);
        } else if ("scorched".equals(action)) {
            t.addPara("Authorize saturation bombardment of Destroy factions' capitals?", bad, 0f);
            for (String l : CAScorched.preview()) t.addPara(CALog.esc(l), 4f);
        } else if ("recall".equals(action)) {
            t.addPara("Stand down and recall offensives travelling or in action? Refunds %s travelling, %s in action.",   // 0.3.0.0 (WS-F3 fix pass 3): the button's hover said it stands down
                    0f, hl, "67 percent", "33 percent");
        } else if ("standdown".equals(action)) {
            t.addPara("Stand down: War level to %s, authorizations cleared, organizing offensives cancelled.",
                    0f, hl, "Defend");
        }
    }

    /** A War page button: stance steps, declare, scorched, recall, standdown. */
    public static void handle(CAData d, String action, String arg) {
        CASettings s = d.settings;
        if ("stance".equals(action) && arg != null) {
            int c = arg.lastIndexOf(':');
            if (c <= 0) return;
            String fid = arg.substring(0, c);
            int st = CAStance.step(s.stanceOf(fid), "-".equals(arg.substring(c + 1)) ? -1 : 1);
            if (st == CAStance.AUTO) s.factionStance().remove(fid); else s.factionStance().put(fid, st);
            CAStance.markSetByYou(fid);   // 0.3.0.0 (fix): a stance you set acts fully in diplomacy (CAStance.fromOldPriority)
            FactionAPI f = Global.getSector().getFaction(fid);
            CALog.did("War: " + (f != null ? f.getDisplayName() : fid) + " stance " + CAStance.name(st) + ".");
        } else if ("declare".equals(action)) {
            FactionAPI f = Global.getSector().getFaction(arg);
            String why = f == null ? "no such faction" : CADiplomacy.declareWar(f);
            if (why != null) CALog.advise("War: war not declared on " + (f != null ? f.getDisplayName() : arg) + ": " + why + ".", true);
        } else if ("scorched".equals(action)) {
            s.warScorchedOK = true;
            CALog.did("War: saturation bombardment authorized for Destroy factions' capitals at War level Scorched.", true);
        } else if ("recall".equals(action)) {
            standDown(d, s, true);
        } else if ("standdown".equals(action)) {
            standDown(d, s, false);
        }
    }

    /** Factions with markets you can see, by name (the War page's list). */
    static List<FactionAPI> factions() {
        Set<String> seen = new HashSet<String>();
        List<FactionAPI> out = new ArrayList<FactionAPI>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            FactionAPI f = m.getFaction();
            if (f == null || m.isPlayerOwned() || f.isPlayerFaction() || !f.isShowInIntelTab() || !seen.add(f.getId())) continue;
            out.add(f);
        }
        Collections.sort(out, new Comparator<FactionAPI>() {
            public int compare(FactionAPI a, FactionAPI b) { return a.getDisplayName().compareTo(b.getDisplayName()); }
        });
        return out;
    }

    static Color toneColor(int stance) {
        int t = CAStance.tone(stance);
        return t > 0 ? Misc.getPositiveHighlightColor() : t < 0 ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
    }

    /** One faction's line on the War page: stance with its < > buttons, relation, markets, and Declare war where it applies. */
    static void renderFaction(TooltipMakerAPI t, CAData d, CASettings s, FactionAPI f, String rival) {
        Color base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor(), gray = Misc.getGrayColor();
        String fid = f.getId();
        int st = stance(s, fid, rival);
        ButtonAPI lower = t.addButton("<", new String[]{"war", "stance", fid + ":-"}, base, dark, Alignment.MID, CutStyle.TL_BR, 30f, 20f, 6f);
        ButtonAPI higher = t.addButton(">", new String[]{"war", "stance", fid + ":+"}, base, dark, Alignment.MID, CutStyle.TL_BR, 30f, 20f, 0f);
        higher.getPosition().rightOfTop(lower, 4f);
        boolean hostile = f.isHostileTo(Factions.PLAYER);
        float rel = Global.getSector().getPlayerFaction().getRelationship(fid);
        int markets = 0;
        for (MarketAPI m : Misc.getFactionMarkets(f)) if (!m.isHidden() && m.isInEconomy()) markets++;
        boolean alive = true;
        try { alive = SectorManager.isFactionAlive(fid); } catch (Throwable ignored) { }
        String state = (hostile ? "hostile" : f.getRelationshipLevel(Factions.PLAYER).getDisplayName().toLowerCase(Locale.ROOT)) + " ("
                + Math.round(rel * 100f) + "), " + (alive ? markets + (markets == 1 ? " market" : " markets") : "eliminated")
                + (st != s.stanceOf(fid) ? ", picked as rival" : "") + ", allows " + CAWarPolicy.levelName(effective(d, s, f, rival)).toLowerCase(Locale.ROOT)
                + (CAStance.fromOldPriority(s, d, fid) ? ", from your 0.2.45 priority" : "");
        com.fs.starfarer.api.ui.LabelAPI l = t.addPara("%s: %s. " + CALog.esc(Character.toUpperCase(state.charAt(0)) + state.substring(1)) + ".", 0f,
                new Color[]{Misc.getHighlightColor(), toneColor(st)}, f.getDisplayName(), CAStance.name(st));
        l.getPosition().rightOfMid(higher, 10f);
        CALog.endRow(t, lower);
        if (CAStance.wantsWar(st) && !hostile) {
            String why = CADiplomacy.declareWhy(d, s, f);
            if (why == null) t.addButton("Declare war on " + f.getDisplayName(), new String[]{"war", "declare", fid}, base, dark, Alignment.MID, CutStyle.TL_BR, 260f, 20f, 2f);
            else t.addPara("Not at war: " + CALog.esc(why) + ". Acts as Rival.", gray, 2f);
        }
        for (OffensiveFleetIntel o : offensives()) {
            if (!yours(o) || o.getTarget() == null || o.getTarget().getFaction() != f || o instanceof DefenseFleetIntel) continue;
            t.addPara("  %s at %s, %s points, " + stageName(stageOf(o)) + (troopsLanded(o) ? ", troops on the ground" : "")
                    + (cmOp(d, o) ? "" : " (not Auto Colonies')") + ".", 2f, Misc.getHighlightColor(),
                    what(kindsOf(o).length > 0 ? kindsOf(o)[0] : "raid"), o.getTarget().getName(), "" + Math.round(o.getFP()));
        }
    }

    static String stageName(int stage) {
        return stage <= 0 ? "organizing" : stage == 1 ? "assembling" : stage == 2 ? "travelling" : stage == 3 ? "in action" : "finishing";
    }
}
