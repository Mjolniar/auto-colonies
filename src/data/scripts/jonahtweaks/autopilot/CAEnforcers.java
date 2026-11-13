package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.PerseanLeagueMembership;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 0.3.1.0: enforcer patrols. Colony patrols attack enforcement fleets in your systems. Every setting is in CASettings (enf*),
 * and each defaults to the cautious value (off, gates on).
 * - Script: transient (never saved), added on every load (JTModPlugin, ensure). It checks four times a day
 *   (CATuning.ENFORCER_CHECK_DAYS) and scans only the systems in scope: systems with Auto colonies, or with any colony of yours.
 * - Targets, by memory flag only: Hegemony investigators ($hegemonyInvestigator), Persean League enforcers ($leagueEnforcer),
 *   Luddic sacred protectors ($sacredProtectors), Tri-Tachyon commerce raiders ($triTachCommerceRaider), Nexerelin police
 *   ($nex_HA_police). Hegemony AI inspection task forces: the active fleets of a HegemonyInspectionIntel's routes. Fleet
 *   types such as "inspectionFleet" and "taskForce" are not used: unrelated fleets, your support fleets among them, carry them.
 * - Skipped: your fleet, your faction's fleets, support fleets, stations, a faction already hostile to you, a fleet the game
 *   already made hostile, and the gates (your commissioner, allies including League membership, ceasefires, Low profile,
 *   your fleet's system), each a setting.
 * - Mark: Misc.makeHostileToFaction(fleet, player, 1 day) and Auto Colonies' record in the fleet's memory (MEM_*), renewed by
 *   each check. Colony patrols ("marketId_military" routes) and stations then engage it. The mark ends when the fleet leaves
 *   the scope, a gate closes or the setting turns off. With the intercept setting, colony patrols in the system get an
 *   INTERCEPT order.
 * - Charge: once per fleet, when it loses a battle to fleets of your faction without you (Watch.reportBattleOccurred). A
 *   battle you fight yourself gets the game's own penalty. Reputation: the game's penalty for defeating that fleet type
 *   yourself (GAME_REP). Credits: its fleet points times Nexerelin's fleet request price per point (400 without
 *   Nexerelin), paid through CABudget on the Military rung where it applies (CAWarPolicy.payer, ENFORCER); never below the
 *   credit reserve. A charge that can't be paid is owed (CASettings.enfOwed), paid first; no new attacks until then.
 * - Records: by fleet id (marked), this session only; the fleet's memory carries the mark across a save.
 */
public class CAEnforcers implements EveryFrameScript {

    // ---------------- the fleet types ----------------

    public static final int INVESTIGATOR = 0, INSPECTION = 1, LEAGUE = 2, PROTECTOR = 3, RAIDER = 4, POLICE = 5, KINDS = 6;
    /** 0.3.1.0: the memory flag each type is found by; the inspection task force is found through its intel's routes (null). */
    static final String[] FLAGS = {"$hegemonyInvestigator", null, "$leagueEnforcer", "$sacredProtectors", "$triTachCommerceRaider", "$nex_HA_police"};
    /** 0.3.1.0: ids kept in the fleet's memory (MEM_KIND). */
    static final String[] KIND_IDS = {"investigator", "inspection", "league", "protector", "raider", "police"};
    /** 0.3.1.0: one fleet of each type, for the log. */
    static final String[] FLEET_NAMES = {"Hegemony investigator", "Hegemony inspection", "Persean League enforcer", "Luddic sacred protector",
            "Tri-Tachyon commerce raider", "Nexerelin police"};
    /**
     * 0.3.1.0: the reputation the game takes when you defeat that fleet yourself (FleetEncounterContext.adjustPlayerReputation):
     * - investigators, League enforcers, sacred protectors: low rep impact, so COMBAT_NORMAL_TOFF, -3 and not below Vengeful;
     * - inspection task force: COMBAT_NORMAL_TOFF with your transponder off, -3 and not below Vengeful (with it on, the same and
     *   at best Hostile); the -3 is used;
     * - commerce raiders and Nexerelin police: Independent fleets with no rep impact, none.
     */
    static final float[] GAME_REP = {-CoreReputationPlugin.RepRewards.MEDIUM, -CoreReputationPlugin.RepRewards.MEDIUM,
            -CoreReputationPlugin.RepRewards.MEDIUM, -CoreReputationPlugin.RepRewards.MEDIUM, 0f, 0f};

    /** 0.3.1.0: Auto Colonies' record in a marked fleet's memory: its type, fleet points when marked, the colony, charged. */
    static final String MEM_KIND = "$ac_enfKind", MEM_FP = "$ac_enfFP", MEM_MARKET = "$ac_enfMarket", MEM_CHARGED = "$ac_enfCharged";
    /** 0.3.1.0: the flag Misc.makeHostileToFaction(fleet, player, ...) sets. */
    static final String HOSTILE_TO_YOU = MemFlags.MEMORY_KEY_MAKE_HOSTILE + "_" + Factions.PLAYER;
    static final String NEX = "nexerelin";

    // ---------------- this session ----------------

    /** 0.3.1.0: one marked fleet and the patrols sent to intercept it. */
    static final class Mark {
        final CampaignFleetAPI fleet;
        final List<CampaignFleetAPI> sent = new ArrayList<CampaignFleetAPI>();
        Mark(CampaignFleetAPI fleet) { this.fleet = fleet; }
    }

    /** 0.3.1.0: fleets marked this session, by fleet id. CAReset clears it. */
    static final Map<String, Mark> marked = new LinkedHashMap<String, Mark>();
    /** 0.3.1.0: lines logged once this session: attacks started (a repeat goes through CALog.status) and skips. CAReset clears it. */
    static final Set<String> announced = new HashSet<String>();

    public static void reset() { marked.clear(); announced.clear(); }

    /** 0.3.1.0: adds the script and the battle listener unless this session has them (both transient). */
    public static void ensure() {
        SectorAPI sector = Global.getSector();
        boolean script = false, listener = false;
        try { for (EveryFrameScript x : sector.getTransientScripts()) if (x instanceof CAEnforcers) script = true; } catch (Throwable ignored) { }
        try { for (CampaignEventListener l : sector.getAllListeners()) if (l instanceof Watch) listener = true; } catch (Throwable ignored) { }
        if (!script) sector.addTransientScript(new CAEnforcers());
        if (!listener) sector.addTransientListener(new Watch());
    }

    // ---------------- the script ----------------

    private float acc = 0f;

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return false; }

    public void advance(float amount) {
        if (!marked.isEmpty()) {
            try { nearYou(); } catch (Throwable t) { CAStep.warn("enforcer patrols", "enforcer patrols: system check failed", t); }
        }
        acc += Global.getSector().getClock().convertToDays(amount);
        if (acc < CATuning.ENFORCER_CHECK_DAYS) return;
        acc = 0f;
        CAStep.run("enforcer patrols", CAEnforcers::check);
    }

    /** 0.3.1.0: every frame while something is marked: a mark in your fleet's system ends at once (the fleet is hostile to you too). */
    static void nearYou() {
        CASettings s = CAData.settings();
        if (s.enfNearYouOK) return;
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        LocationAPI here = pf == null ? null : pf.getContainingLocation();
        if (here == null) return;
        for (Mark m : new ArrayList<Mark>(marked.values()))
            if (m.fleet.getContainingLocation() == here) end(m.fleet, placeOf(m.fleet), "your fleet is in the system");
    }

    /** 0.3.1.0: one check: owed credits first, then every fleet in scope marked, renewed or let go. */
    static void check() {
        CASettings s = CAData.settings();
        settleOwed(s);
        if (!s.enfOn && marked.isEmpty()) return;
        Map<LocationAPI, List<MarketAPI>> scope = scope(s);
        Map<CampaignFleetAPI, Integer> found = new LinkedHashMap<CampaignFleetAPI, Integer>();
        if (s.enfOn) find(s, scope, found);
        // ongoing marks first, so a new one never takes the credits an ongoing one counts on
        Set<CampaignFleetAPI> order = new LinkedHashSet<CampaignFleetAPI>();
        for (Mark m : new ArrayList<Mark>(marked.values())) {
            if (found.containsKey(m.fleet)) order.add(m.fleet);
            else end(m.fleet, placeOf(m.fleet), !s.enfOn ? "enforcer patrols are off" : alive(m.fleet) ? "it left your systems" : "it is gone");
        }
        order.addAll(found.keySet());
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        LocationAPI here = pf == null ? null : pf.getContainingLocation();
        int mode = mode(s);
        float perFP = usesCredits(mode) ? perFP() : 0f, committed = 0f;
        for (CampaignFleetAPI f : order) {
            int kind = found.get(f);
            List<MarketAPI> markets = scope.get(f.getContainingLocation());
            MarketAPI m = markets == null || markets.isEmpty() ? null : markets.get(0);
            String why = m == null ? "outside your systems" : why(s, kind, facts(f, here));
            if (why == null && usesCredits(mode) && !f.getMemoryWithoutUpdate().getBoolean(MEM_CHARGED)) {
                float c = credits(mode, fpOf(f), perFP);
                if (s.enfOwed > 0f) why = "credits are owed for an earlier fleet";
                else if (payer(s, committed + c) == null) why = Misc.getDGSCredits(committed + c) + " is not available above the reserve";
                else committed += c;
            }
            if (why != null) { skip(f, kind, m, why); continue; }
            mark(s, f, kind, m, markets, mode, perFP);
        }
    }

    // ---------------- pure (JTEnforcerCheck) ----------------

    /** 0.3.1.0: the game's facts about one fleet, for why(). */
    static final class Facts {
        boolean yours, nex, lowProfile, commissioner, allied, ceasefire, hostile, flagged, nearYou;
    }

    /** 0.3.1.0: the fleet type from its memory flags (has: whether a flag is set), or -1. Pure. */
    static int kindOf(Predicate<String> has) {
        for (int k = 0; k < KINDS; k++) if (FLAGS[k] != null && has.test(FLAGS[k])) return k;
        return -1;
    }

    /** 0.3.1.0: a type id from the fleet's memory (KIND_IDS), or -1. Pure. */
    static int kindIndex(String id) {
        for (int k = 0; k < KINDS; k++) if (KIND_IDS[k].equals(id)) return k;
        return -1;
    }

    /** 0.3.1.0: why a fleet of this type is not attacked, or null when it is. Pure. */
    static String why(CASettings s, int kind, Facts x) {
        if (!s.enfOn) return "enforcer patrols are off";
        if (kind < 0 || kind >= KINDS) return "not an enforcement fleet";
        if (x.yours) return "your fleet or your faction's";
        if (!s.enfKind(kind)) return CASettings.ENF_KIND_NAMES[kind] + " are off";
        if (kind == POLICE && !x.nex) return "Nexerelin is off";
        if (x.lowProfile && !s.enfLowProfileOK) return "Low profile";
        if (x.commissioner && !s.enfCommissionOK) return "your commissioner";
        if (x.allied && !s.enfAlliesOK) return "an ally";
        if (x.ceasefire && !s.enfCeasefireOK) return "a ceasefire";
        if (x.hostile) return "already hostile to you";
        if (x.flagged) return "the game already made it hostile";
        if (x.nearYou && !s.enfNearYouOK) return "your fleet is in the system";
        return null;
    }

    /** 0.3.1.0: whether a colony's system is scanned: Auto colonies, or any colony with the setting. Pure. */
    static boolean inScope(boolean allColonies, boolean auto) { return allColonies || auto; }

    /** 0.3.1.0: the cost mode, Reputation for a value out of range. Pure. */
    static int mode(CASettings s) { return s.enfCost >= 0 && s.enfCost < CASettings.ENF_COST_NAMES.length ? s.enfCost : CASettings.ENF_REP; }

    static boolean usesRep(int mode) { return mode == CASettings.ENF_REP || mode == CASettings.ENF_BOTH; }

    static boolean usesCredits(int mode) { return mode == CASettings.ENF_CREDITS || mode == CASettings.ENF_BOTH; }

    /** 0.3.1.0: the reputation change for defeating this type under this cost mode (0 or less). Pure. */
    static float repDelta(int mode, int kind) {
        return usesRep(mode) && kind >= 0 && kind < KINDS ? GAME_REP[kind] : 0f;
    }

    /** 0.3.1.0: the credits for defeating a fleet of fp points under this cost mode: fp x the price per point, rounded. Pure. */
    static float credits(int mode, float fp, float perFP) {
        return usesCredits(mode) ? Math.round(Math.max(0f, fp) * Math.max(0f, perFP)) : 0f;
    }

    /** 0.3.1.0: the price per fleet point: Nexerelin's fleet request price, or CATuning's without Nexerelin or with no value. Pure. */
    static float perFP(boolean nex, float nexValue) {
        return nex && nexValue > 0f ? nexValue : CATuning.ENFORCER_CREDITS_PER_FP;
    }

    /** 0.3.1.0: "Hegemony relations -3 and 34,000 credits", or "no cost". Pure apart from the credit format. */
    static String costText(String faction, float delta, float credits) {
        List<String> parts = new ArrayList<String>();
        if (delta < 0f) parts.add(faction + " relations " + Math.round(delta * 100f));
        if (credits > 0f) parts.add(Misc.getDGSCredits(credits) + " credits");
        return parts.isEmpty() ? "no cost" : String.join(" and ", parts);
    }

    // ---------------- finding ----------------

    /** 0.3.1.0: the systems in scope, each with its colonies in scope, largest first (the first names the place in the log). */
    static Map<LocationAPI, List<MarketAPI>> scope(CASettings s) {
        Map<LocationAPI, List<MarketAPI>> out = new LinkedHashMap<LocationAPI, List<MarketAPI>>();
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            StarSystemAPI sys = m.getStarSystem();
            if (sys == null || !inScope(s.enfAllColonies, d.colony(m.getId()).mode == CAData.Mode.AUTO)) continue;
            List<MarketAPI> l = out.get(sys);
            if (l == null) { l = new ArrayList<MarketAPI>(); out.put(sys, l); }
            l.add(m);
        }
        for (List<MarketAPI> l : out.values()) l.sort((a, b) -> b.getSize() - a.getSize());
        return out;
    }

    /** 0.3.1.0: every enforcement fleet in scope by flag, every fleet with Auto Colonies' mark, and the inspection task forces. */
    static void find(CASettings s, Map<LocationAPI, List<MarketAPI>> scope, Map<CampaignFleetAPI, Integer> out) {
        for (LocationAPI loc : scope.keySet()) {
            for (CampaignFleetAPI f : loc.getFleets()) {
                if (f == null || !alive(f)) continue;
                MemoryAPI mem = f.getMemoryWithoutUpdate();
                boolean ours = mem.contains(MEM_KIND);
                int k = kindOf(mem::getBoolean);
                if (k < 0 && ours) k = kindIndex(mem.getString(MEM_KIND));
                if (k >= 0 && (ours || s.enfKind(k))) out.put(f, k);   // a type set to Off is looked at only to end its mark
            }
        }
        if (!s.enfInspections) return;
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel()) {
            if (!(i instanceof HegemonyInspectionIntel) || i.isEnded() || i.isEnding()) continue;
            String source = ((HegemonyInspectionIntel) i).getRouteSourceId();
            for (RouteManager.RouteData r : RouteManager.getInstance().getRoutesForSource(source)) {
                CampaignFleetAPI f = r.getActiveFleet();
                if (f != null && alive(f) && scope.containsKey(f.getContainingLocation()) && !out.containsKey(f)) out.put(f, INSPECTION);
            }
        }
    }

    static boolean alive(CampaignFleetAPI f) {
        return f.isAlive() && f.getContainingLocation() != null && f.getFleetPoints() > 0;
    }

    /** 0.3.1.0: what the gates need to know about one fleet. */
    static Facts facts(CampaignFleetAPI f, LocationAPI here) {
        Facts x = new Facts();
        FactionAPI fac = f.getFaction();
        x.nex = Global.getSettings().getModManager().isModEnabled(NEX);
        x.yours = f.isPlayerFleet() || fac == null || fac.isPlayerFaction() || f.isStationMode() || CASurvival.supportFleet(f);
        if (x.yours) return x;
        x.lowProfile = CALowProfile.on();
        FactionAPI comm = Misc.getCommissionFaction();
        x.commissioner = comm != null && comm.getId().equals(fac.getId());
        x.allied = allied(fac);
        x.ceasefire = ceasefire(fac);
        x.hostile = fac.isHostileTo(Factions.PLAYER);
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        x.flagged = !mem.contains(MEM_KIND) && (mem.contains(MemFlags.MEMORY_KEY_MAKE_HOSTILE) || mem.getBoolean(HOSTILE_TO_YOU));
        x.nearYou = here != null && f.getContainingLocation() == here;
        return x;
    }

    /** 0.3.1.0: allied with you: Nexerelin's alliances and League membership (CAWar.allied), or League membership without Nexerelin. */
    static boolean allied(FactionAPI f) {
        if (Global.getSettings().getModManager().isModEnabled(NEX) && CACompat.linked("CAWar")) {
            try { return CAWar.allied(f); } catch (Throwable ignored) { }
        }
        try { return Factions.PERSEAN.equals(f.getId()) && PerseanLeagueMembership.isLeagueMember(); } catch (Throwable t) { return false; }
    }

    /** 0.3.1.0: a ceasefire with you is in force (Nexerelin, CAWar.ceasefire); none without Nexerelin. */
    static boolean ceasefire(FactionAPI f) {
        if (!Global.getSettings().getModManager().isModEnabled(NEX) || !CACompat.linked("CAWar")) return false;
        try { return CAWar.ceasefire(f); } catch (Throwable t) { return false; }
    }

    /** 0.3.1.0: Nexerelin's fleet request price per fleet point (NexConfig, read by name), or CATuning's without Nexerelin. */
    static float perFP() {
        boolean nex = Global.getSettings().getModManager().isModEnabled(NEX);
        float v = nex ? CAConst.num("exerelin.utilities.NexConfig", "fleetRequestCostPerFP", CATuning.ENFORCER_CREDITS_PER_FP) : 0f;
        return perFP(nex, v);
    }

    static float fpOf(CampaignFleetAPI f) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        return mem.contains(MEM_FP) ? mem.getFloat(MEM_FP) : f.getFleetPoints();
    }

    static String placeOf(CampaignFleetAPI f) {
        String id = f.getMemoryWithoutUpdate().getString(MEM_MARKET);
        MarketAPI m = id == null ? null : Global.getSector().getEconomy().getMarket(id);
        if (m != null) return m.getName();
        LocationAPI loc = f.getContainingLocation();
        return loc != null ? loc.getName() : "Your colonies";
    }

    // ---------------- marking ----------------

    /** 0.3.1.0: marks a fleet or renews its mark; logs a new attack. */
    static void mark(CASettings s, CampaignFleetAPI f, int kind, MarketAPI m, List<MarketAPI> markets, int mode, float perFP) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        boolean fresh = !mem.contains(MEM_KIND);
        float days = CATuning.ENFORCER_HOSTILE_DAYS, fp = fpOf(f);
        Misc.makeHostileToFaction(f, Factions.PLAYER, days);
        mem.set(MEM_KIND, KIND_IDS[kind], days);
        mem.set(MEM_FP, fp, days);
        if (fresh || !mem.contains(MEM_MARKET)) mem.set(MEM_MARKET, m.getId(), days);
        else mem.set(MEM_MARKET, mem.getString(MEM_MARKET), days);
        if (mem.getBoolean(MEM_CHARGED)) mem.set(MEM_CHARGED, true, days);
        Mark mk = marked.get(f.getId());
        if (mk == null) { mk = new Mark(f); marked.put(f.getId(), mk); }
        if (s.enfIntercept) intercept(mk, markets);
        if (!fresh) return;
        String faction = f.getFaction() == null ? "" : f.getFaction().getDisplayName();
        String text = m.getName() + ": Enforcers: patrols attack a " + FLEET_NAMES[kind] + " fleet (" + Math.round(fp) + " points; on defeat "
                + costText(faction, repDelta(mode, kind), credits(mode, fp, perFP)) + ").";
        if (announced.add("attack:" + f.getId())) CALog.did(text, true);
        else CALog.thinkStatus("enf:" + f.getId(), text);
    }

    /** 0.3.1.0: a fleet not attacked: an ongoing mark ends; anything else is one starsector.log line per fleet and reason this session. */
    static void skip(CampaignFleetAPI f, int kind, MarketAPI m, String why) {
        if (f.getMemoryWithoutUpdate().contains(MEM_KIND) || marked.containsKey(f.getId())) { end(f, placeOf(f), why); return; }
        if (announced.add("skip:" + f.getId() + ":" + why))
            CALog.quiet((m != null ? m.getName() : placeOf(f)) + ": Enforcers: " + FLEET_NAMES[kind] + " fleet left alone (" + why + ").");
    }

    /** 0.3.1.0: ends a mark: the hostility flag, Auto Colonies' record and the intercept orders it gave. */
    static void end(CampaignFleetAPI f, String place, String why) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        if (mem.contains(MEM_KIND)) {
            int kind = kindIndex(mem.getString(MEM_KIND));
            Misc.makeHostileToFaction(f, Factions.PLAYER, false, 0f);
            CALog.quiet(place + ": Enforcers: attack on a " + (kind >= 0 ? FLEET_NAMES[kind] : "marked") + " fleet ended (" + why + ").");
        }
        mem.unset(MEM_KIND);
        mem.unset(MEM_FP);
        mem.unset(MEM_MARKET);
        mem.unset(MEM_CHARGED);
        Mark mk = marked.remove(f.getId());
        if (mk != null) {
            for (CampaignFleetAPI p : mk.sent) {
                FleetAssignmentDataAPI a = p.getCurrentAssignment();
                if (a != null && a.getAssignment() == FleetAssignment.INTERCEPT && a.getTarget() == f) p.removeFirstAssignment();
            }
        }
    }

    /** 0.3.1.0: colony patrols in the fleet's system get an intercept order, unless they have one on it. */
    static void intercept(Mark mk, List<MarketAPI> markets) {
        CampaignFleetAPI target = mk.fleet;
        LocationAPI loc = target.getContainingLocation();
        if (loc == null || markets == null) return;
        for (CampaignFleetAPI p : loc.getFleets()) {
            if (!colonyPatrol(p, markets)) continue;
            FleetAssignmentDataAPI a = p.getCurrentAssignment();
            if (a != null && a.getAssignment() == FleetAssignment.INTERCEPT && a.getTarget() == target) continue;
            p.addAssignmentAtStart(FleetAssignment.INTERCEPT, target, CATuning.ENFORCER_HOSTILE_DAYS, (com.fs.starfarer.api.Script) null);
            if (!mk.sent.contains(p)) mk.sent.add(p);
        }
    }

    /** 0.3.1.0: a patrol of one of these colonies: your faction, on the colony's "marketId_military" route. */
    static boolean colonyPatrol(CampaignFleetAPI p, List<MarketAPI> markets) {
        if (p == null || p.isPlayerFleet() || p.isStationMode() || p.getFaction() == null || !p.getFaction().isPlayerFaction()) return false;
        for (MarketAPI m : markets) if (RouteManager.getInstance().getRoute(m.getId() + "_military", p) != null) return true;
        return false;
    }

    // ---------------- charging ----------------

    /** 0.3.1.0: who pays (CAWarPolicy.payer, ENFORCER): the Military rung while it is On, the surplus while rich, the month's budget. */
    static String payer(CASettings s, float cost) {
        int r = CARich.MILITARY, rungMode = CARich.mode(r);
        float rung = 0f;
        if (rungMode == CARich.ON) try { rung = CARich.allowance(r); } catch (Throwable ignored) { }
        boolean rich = false;
        try { rich = CASurplus.rich(s); } catch (Throwable ignored) { }
        return CAWarPolicy.payer(CAWarPolicy.ENFORCER, rich && rungMode == CARich.ON, cost, CABuild.credits(), s.reserve,
                CABudget.available(s), CASurplus.amount(s), rung);
    }

    /** 0.3.1.0: pays through CABudget: from the Military rung or the surplus with payFromPool, otherwise against the month's budget. */
    static void pay(CASettings s, String payer, String marketId, float price) {
        if ("pool".equals(payer) || "rung".equals(payer)) CABudget.payFromPool(s, CARich.MILITARY, marketId, price, 0f);
        else CABudget.pay(s, marketId, price, 0f, 0f);
    }

    /** 0.3.1.0: owed credits paid once there is room above the reserve. */
    static void settleOwed(CASettings s) {
        if (!(s.enfOwed > 0f)) return;
        float owed = s.enfOwed;
        String payer = payer(s, owed);
        if (payer == null) return;
        pay(s, payer, null, owed);
        s.enfOwed = 0f;
        CALog.did("Enforcers: " + Misc.getDGSCredits(owed) + " credits owed for defeated fleets paid.", true);
    }

    /** 0.3.1.0: a marked fleet lost to your faction's fleets: the cost mode's charge, once per fleet. */
    static void charge(CampaignFleetAPI f) {
        MemoryAPI mem = f.getMemoryWithoutUpdate();
        if (!mem.contains(MEM_KIND) || mem.getBoolean(MEM_CHARGED)) return;
        int kind = kindIndex(mem.getString(MEM_KIND));
        if (kind < 0) return;
        mem.set(MEM_CHARGED, true, CATuning.ENFORCER_HOSTILE_DAYS);
        CASettings s = CAData.settings();
        int mode = mode(s);
        String marketId = mem.getString(MEM_MARKET);
        FactionAPI fac = f.getFaction();
        List<String> parts = new ArrayList<String>();
        float delta = repDelta(mode, kind);
        if (delta < 0f && fac != null && !fac.isPlayerFaction()) {
            CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
            impact.delta = delta;
            impact.limit = RepLevel.VENGEFUL;   // as the game's COMBAT_NORMAL_TOFF
            Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, impact, null, null, false), fac.getId());
            parts.add(fac.getDisplayName() + " relations " + Math.round(delta * 100f));
        }
        float cost = credits(mode, mem.getFloat(MEM_FP), usesCredits(mode) ? perFP() : 0f);
        if (cost > 0f) {
            String payer = s.enfOwed > 0f ? null : payer(s, cost);
            if (payer != null) {
                pay(s, payer, marketId, cost);
                parts.add(Misc.getDGSCredits(cost) + " credits paid");
            } else {
                s.enfOwed += cost;
                parts.add(Misc.getDGSCredits(cost) + " credits owed, paid when credits are above the reserve");
            }
        }
        if (parts.isEmpty()) parts.add(mode == CASettings.ENF_NONE ? "No cost (Cost: None)" : "No cost (the game takes none for this fleet type)");
        CALog.did(placeOf(f) + ": Enforcers: " + FLEET_NAMES[kind] + " fleet defeated. " + String.join(". ", parts) + ".", true);
    }

    /** 0.3.1.0: a battle without you: each marked fleet on the losing side is charged when your faction's fleets won. */
    static void battle(CampaignFleetAPI winner, BattleAPI b) {
        if (winner == null || b == null || b.isPlayerInvolved()) return;   // a battle you fight gets the game's own penalty
        boolean oneWon = has(b.getSnapshotSideOne(), winner) || has(b.getSideOne(), winner);
        boolean twoWon = has(b.getSnapshotSideTwo(), winner) || has(b.getSideTwo(), winner);
        if (oneWon == twoWon) return;
        List<CampaignFleetAPI> won = oneWon ? b.getSnapshotSideOne() : b.getSnapshotSideTwo();
        List<CampaignFleetAPI> lost = oneWon ? b.getSnapshotSideTwo() : b.getSnapshotSideOne();
        if (won == null || lost == null || !yourForces(won)) return;
        for (CampaignFleetAPI f : lost) if (f != null && f.getMemoryWithoutUpdate().contains(MEM_KIND)) charge(f);
    }

    static boolean has(List<CampaignFleetAPI> side, CampaignFleetAPI f) { return side != null && side.contains(f); }

    /** 0.3.1.0: a side with a fleet of your faction other than your own fleet: patrols, stations, other fleets of yours. */
    static boolean yourForces(List<CampaignFleetAPI> side) {
        for (CampaignFleetAPI f : side) if (f != null && !f.isPlayerFleet() && f.getFaction() != null && f.getFaction().isPlayerFaction()) return true;
        return false;
    }

    /** 0.3.1.0: the battle listener; transient (ensure), so a save never holds it. */
    static final class Watch extends BaseCampaignEventListener {
        Watch() { super(false); }

        @Override
        public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
            try { battle(primaryWinner, battle); }
            catch (Throwable t) { CAStep.warn("enforcer patrols", "enforcer patrols: battle report failed", t); }
        }

        @Override
        public void reportFleetDespawned(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
            try { if (fleet != null) marked.remove(fleet.getId()); } catch (Throwable ignored) { }
        }
    }
}
