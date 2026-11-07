package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.CounterInsurgency;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.FindPirateBase;
import exerelin.campaign.intel.agents.InfiltrateCell;
import exerelin.campaign.intel.agents.RaiseRelations;
import exerelin.campaign.intel.agents.SabotageIndustry;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;

import java.util.*;

/**
 * Nexerelin agents (setting "Agents", off before setup). Only agents Autopilot recruited, or ones you hand over on the
 * empire panel's Mod features page, are given orders; the rest are left alone, and you can take any of them back. Each
 * agent has a role, the way a player sets theirs up (2.37.0):
 * - Security (the first): Nexerelin's own defensive operations, in the order its Internal security mode runs them:
 *   Counter-Insurgency on a rebellion, Infiltrate Cell on an active Pather cell, Find Pirate Base for a hidden base.
 * - Relations (the second, recruited once the budget allows): Raise Relations with your preferred factions, picked like
 *   ambassadors (Administrators and ambassadors: High priority first, then the ambassador focus, up to "Stop raising a
 *   faction at"), never one at war with you.
 * - Offense (only with "Defense and offense"): Sabotage Industry on a military industry of the faction set under "Offense
 *   target" (whoever attacks you, a random enemy, or one you pick), only factions already hostile to you, never allies, a
 *   ceasefire or your commissioner, at 60% estimated success or better, alert 0.2 or lower, one operation per faction.
 * An agent only gets operations its type can run (Nexerelin's agentConfig). Recruiting one costs Nexerelin's 12,000 plus
 * the convenience fee and takes RECRUIT_DAYS before the agent reports for duty; it never appears from nothing (before 2.37.0
 * one was created on the spot, the way Nexerelin's console command does). Operation costs and recruiting come out of the
 * monthly budget, never the credit reserve; Nexerelin pays salaries. Enemy operations the game doesn't show you are neither
 * shown nor used (they were before 2.37.0). Only person ids are saved, so the save loads without Nexerelin.
 */
public class CASpies {
    public static final String MOD = "nexerelin";
    static final int HIRE_FEE = 12000;
    static final float RECRUIT_DAYS = 60f;
    static final String SECURITY = "security", RELATIONS = "relations", OFFENSE = "offense";
    static final float MIN_SUCCESS = 60f, MAX_ALERT = 0.2f;
    static final String[] MILITARY = {Industries.HIGHCOMMAND, Industries.MILITARYBASE, Industries.PATROLHQ, Industries.HEAVYBATTERIES,
            Industries.GROUNDDEFENSES, Industries.MEGAPORT, Industries.SPACEPORT};

    public static boolean enabled() { return Global.getSettings().getModManager().isModEnabled(MOD); }

    /** Daily, from CAAuto. */
    public static void run(CAData d, CASettings s) {
        if (!s.spyChecked) {   // 2.42.2: saves set up before Defense was recommended had agents off, so Pather cells went unanswered
            s.spyChecked = true;
            if (s.spyMode == 0) {
                s.spyMode = 1;
                CALog.add("Agents: now on Defense (new in this version). One agent works security at your colonies: Pather cells, rebellions, "
                        + "hidden pirate bases; an idle agent of yours is put on it, or one is recruited. Change it under Settings, Agents (Nexerelin)", true);
            }
        }
        if (s.spyMode == 0 || Misc.getPlayerMarkets(false).isEmpty()) return;
        CovertOpsManager mgr = CovertOpsManager.getManager();
        if (mgr == null) return;
        List<AgentIntel> mine = managed(d, mgr);
        arrive(d, mgr, mine);
        hire(d, s, mgr, mine);
        for (AgentIntel a : mine) {
            if (busy(mgr, a)) continue;
            String role = role(d, s, a);
            if (SECURITY.equals(role)) defend(s, mgr, a);
            else if (RELATIONS.equals(role)) relations(s, a);
            else if (OFFENSE.equals(role)) { if (offense(s)) attack(s, mgr, a, mine); }
        }
    }

    // ---- roles

    /** The role an agent has, giving it the first one free that its type can run: security, relations, then offense. */
    static String role(CAData d, CASettings s, AgentIntel a) {
        String id = a.getAgent().getId();
        String r = d.spyRoles().get(id);
        if (r != null) return r;
        Set<String> held = new HashSet<String>(d.spyRoles().values());
        if (!held.contains(SECURITY)) r = SECURITY;
        else if (!held.contains(RELATIONS) && canDo(a, "raiseRelations")) r = RELATIONS;
        else if (offense(s) && !held.contains(OFFENSE) && canDo(a, "sabotageIndustry")) r = OFFENSE;
        else r = canDo(a, "raiseRelations") ? RELATIONS : SECURITY;
        d.spyRoles().put(id, r);
        CALog.add("Agent " + name(a) + " (" + types(a) + ") takes the " + r + " role");
        return r;
    }

    static String roleName(String r) {
        return SECURITY.equals(r) ? "security (rebellions, Pather cells, pirate bases)" : RELATIONS.equals(r) ? "relations with your preferred factions"
                : OFFENSE.equals(r) ? "offense (sabotage)" : r;
    }

    /** Every agent you have that still serves, whoever hired it. */
    static List<AgentIntel> agents(CovertOpsManager mgr) {
        List<AgentIntel> out = new ArrayList<AgentIntel>();
        for (AgentIntel a : mgr.getAgents()) if (!a.isDeadOrDismissed() && a.getAgent() != null) out.add(a);
        return out;
    }

    /** The agents Autopilot gives orders to (hired by it or handed over); drops the ids of dismissed ones. */
    static List<AgentIntel> managed(CAData d, CovertOpsManager mgr) {
        List<AgentIntel> out = new ArrayList<AgentIntel>();
        Set<String> alive = new HashSet<String>();
        for (AgentIntel a : agents(mgr)) {
            if (d.spyAgents().contains(a.getAgent().getId())) { out.add(a); alive.add(a.getAgent().getId()); }
        }
        d.spyAgents().retainAll(alive);
        d.spyRoles().keySet().retainAll(alive);
        return out;
    }

    /** The agent's types as Nexerelin names them ("Negotiator", "Saboteur/Hybrid"), or "agent" without specializations. */
    static String types(AgentIntel a) {
        List<String> n = a.getSpecializationNames();
        return n == null || n.isEmpty() ? "agent" : String.join("/", n);
    }

    /** Empire panel buttons: "use" hands one of your agents to Autopilot, "back" takes one back. */
    static void handle(CAData d, String action, String personId) {
        CovertOpsManager mgr = CovertOpsManager.getManager();
        if (mgr == null) return;
        for (AgentIntel a : agents(mgr)) {
            if (!a.getAgent().getId().equals(personId)) continue;
            if ("use".equals(action)) {
                d.spyAgents().add(personId);
                CALog.forget("spy:yours");
                CALog.add("Agent " + name(a) + " (" + types(a) + ") handed to Autopilot; it gets orders once it's idle");
            } else {
                d.spyAgents().remove(personId);
                d.spyRoles().remove(personId);
                CALog.add("Agent " + name(a) + " is yours again; Autopilot won't give it orders");
            }
        }
    }

    /** True while the agent is travelling, injured or running an operation. */
    static boolean busy(CovertOpsManager mgr, AgentIntel a) {
        if (a.getMarket() == null) return true;
        for (CovertActionIntel op : mgr.getOngoingCovertActionsOfType(CovertActionIntel.class))
            if (op.getAgent() == a && !op.isEnded() && !op.isEnding() && op.getResult() == null) return true;
        return false;
    }

    static boolean canDo(AgentIntel a, String defId) {
        CovertOpsManager.CovertActionDef def = CovertOpsManager.getDef(defId);
        return def != null && (!NexConfig.useAgentSpecializations || def.canAgentExecute(a));
    }

    static String name(AgentIntel a) { return a.getAgent().getNameString(); }

    // ---- defense

    static boolean defend(CASettings s, CovertOpsManager mgr, AgentIntel a) {
        FactionAPI pf = Global.getSector().getPlayerFaction();
        List<MarketAPI> mine = Misc.getPlayerMarkets(false);
        if (local(s, a, new CounterInsurgency(a, null, pf, null, true, null), mine, "counterInsurgency", "counter-insurgency")) return true;
        if (local(s, a, new InfiltrateCell(a, null, pf, null, true, null), mine, "infiltrateCell", "infiltrating the Pather cell")) return true;
        return local(s, a, new FindPirateBase(a, null, pf, true, null), mine, "findPirateBase", "finding the pirate base");
    }

    /** One of Nexerelin's own defensive orders at the closest colony that needs it, if the budget allows. */
    static boolean local(CASettings s, AgentIntel a, CovertActionIntel template, List<MarketAPI> mine, String defId, String what) {
        if (!canDo(a, defId)) return false;
        List<MarketAPI> list = new ArrayList<MarketAPI>(mine);
        list.removeAll(a.getOtherAgentActionTargets(template.getClass()));
        MarketAPI target = a.getClosestMarketForAction(list, template);
        if (target == null) return false;
        template.setMarket(target);
        template.setTargetFaction(target.getFaction());
        int cost = template.getCost();
        String key = "spy:" + defId + ":" + target.getId();
        if (cost > CABudget.available(s)) {
            if (CALog.once(key)) CALog.add(target.getName() + ": agent " + name(a) + " waits for budget for " + what + " (" + Misc.getDGSCredits(cost) + ")");
            return false;
        }
        if (template instanceof CounterInsurgency) a.issueCOINOrder(target);
        else if (template instanceof InfiltrateCell) a.issueCellKillOrder(target);
        else a.issueBaseFindOrder(target);   // these charge the credits themselves
        CABudget.charge(s, cost, 0f, 0f);
        CALog.forget(key);
        CALog.add(target.getName() + ": agent " + name(a) + " sent for " + what + (cost > 0 ? " (" + Misc.getDGSCredits(cost) + ")" : ""));
        return true;
    }

    /**
     * Raise Relations with your preferred faction, picked as ambassadors are (CAIndEvo): High priority first, Ignore never,
     * Priority list only if that's the focus, else by the focus (worst relations, strongest, nearest), never at war with you
     * and never past "Stop raising a faction at".
     */
    static boolean relations(CASettings s, AgentIntel a) {
        if (!canDo(a, "raiseRelations")) return false;
        FactionAPI pf = Global.getSector().getPlayerFaction();
        float stopAt = CASettings.STOP_VALUES[Math.max(0, Math.min(3, s.ambassadorStopAt))];
        Map<String, Float> strength = new HashMap<String, Float>(), near = new HashMap<String, Float>();
        List<MarketAPI> mine = Misc.getPlayerMarkets(false);
        for (MarketAPI o : Global.getSector().getEconomy().getMarketsCopy()) {
            if (o.isPlayerOwned() || o.getFaction() == null || o.isHidden()) continue;
            String fid = o.getFactionId();
            strength.put(fid, (strength.containsKey(fid) ? strength.get(fid) : 0f) + o.getSize());
            for (MarketAPI pm : mine) {
                if (pm.getPrimaryEntity() == null || o.getPrimaryEntity() == null) continue;
                if (Misc.getDistanceLY(pm.getPrimaryEntity(), o.getPrimaryEntity()) <= 10f) { near.put(fid, (near.containsKey(fid) ? near.get(fid) : 0f) + 1f); break; }
            }
        }
        FactionAPI best = null; float bestScore = -1e9f;
        for (String fid : strength.keySet()) {
            FactionAPI f = Global.getSector().getFaction(fid);
            if (f == null || f.isPlayerFaction() || f.isHostileTo(Factions.PLAYER)) continue;   // at war is your call
            int prio = s.priorityOf(fid);
            if (prio == 0 || (s.ambassadorFocus == 3 && prio != 2)) continue;
            float rel = pf.getRelationship(fid);
            if (rel >= stopAt || !RaiseRelations.canModifyRelations(f, a)) continue;
            float score = s.ambassadorFocus == 1 ? strength.get(fid) : s.ambassadorFocus == 2 ? (near.containsKey(fid) ? near.get(fid) : 0f) : -rel * 100f;
            if (prio == 2) score += 100000f;
            if (score > bestScore) { bestScore = score; best = f; }
        }
        if (best == null) return false;
        MarketAPI target = nearest(Misc.getFactionMarkets(best), a.getMarket());
        if (target == null) return false;
        RaiseRelations op = new RaiseRelations(a, target, pf, best, pf, true, new HashMap<String, Object>());
        int cost = op.getCost();
        String key = "spy:rel:" + best.getId();
        if (cost > CABudget.available(s)) {
            if (CALog.once(key)) CALog.add("Agent " + name(a) + " waits for budget to raise relations with " + best.getDisplayName() + " (" + Misc.getDGSCredits(cost) + ")");
            return false;
        }
        order(a, op, target, cost);
        CABudget.charge(s, cost, 0f, 0f);
        CALog.forget(key);
        CALog.add("Agent " + name(a) + " sent to " + target.getName() + " to raise relations with " + best.getDisplayName() + " (" + Misc.getDGSCredits(cost) + ")");
        return true;
    }

    /** A remote order the way Nexerelin queues one: travel there, then the operation starts on arrival. */
    static void order(AgentIntel a, CovertActionIntel op, MarketAPI target, int cost) {
        a.issueTravelOrderIfNeeded(target);
        op.init();
        a.addAction(op);
        if (target == a.getMarket()) op.activate();
        if (cost > 0) Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cost);
    }

    static MarketAPI nearest(List<MarketAPI> markets, MarketAPI from) {
        MarketAPI best = null; float bestD = Float.MAX_VALUE;
        for (MarketAPI m : markets) {
            if (m.isHidden() || !m.isInEconomy() || m.getPrimaryEntity() == null) continue;
            float dist = from == null ? 0f : Misc.getDistanceLY(m.getLocationInHyperspace(), from.getLocationInHyperspace());
            if (dist < bestD) { bestD = dist; best = m; }
        }
        return best;
    }

    // ---- offense

    /** A faction operations may target: hostile to you, not allied, no ceasefire, not your commissioner, allows agent actions. */
    static boolean allowed(FactionAPI f) {
        if (f == null || f.isPlayerFaction() || !f.isHostileTo(Factions.PLAYER)) return false;
        NexFactionConfig conf = NexConfig.getFactionConfig(f.getId());
        if (conf != null && !conf.allowAgentActions) return false;
        FactionAPI comm = Misc.getCommissionFaction();
        if (comm != null && (comm == f || !f.isHostileTo(comm))) return false;   // operations resolve as your commissioner
        if (AllianceManager.areFactionsAllied(f.getId(), Factions.PLAYER)) return false;
        DiplomacyManager dm = DiplomacyManager.getManager();
        DiplomacyBrain brain = dm == null ? null : dm.getDiplomacyBrain(f.getId());
        return brain == null || !brain.hasCeasefireWith(Factions.PLAYER);
    }

    static boolean allowed(MarketAPI m) { return m != null && !m.isHidden() && m.isInEconomy() && allowed(m.getFaction()); }

    static Industry military(MarketAPI m) {
        for (String id : MILITARY) {
            Industry ind = m.getIndustry(id);
            if (ind != null && ind.isFunctional() && ind.canBeDisrupted() && !ind.getSpec().hasTag(Industries.TAG_STATION)) return ind;
        }
        return null;
    }

    /**
     * What to sabotage, by the "Offense target" setting: a faction you picked (its military nearest your agents), a random
     * enemy faction, or (the default) the military of a market whose raid or invasion against you is on its way, else
     * Nexerelin's own military pick. Only factions offense may target (allowed). Null if nothing qualifies.
     */
    static Industry target(CovertOpsManager mgr, CASettings s) {
        if (s.spyTarget != null && !s.spyTarget.isEmpty() && !RANDOM.equals(s.spyTarget)) {
            FactionAPI f = Global.getSector().getFaction(s.spyTarget);
            return allowed(f) ? militaryOf(f) : null;
        }
        if (RANDOM.equals(s.spyTarget)) {
            List<FactionAPI> enemies = new ArrayList<FactionAPI>();
            for (FactionAPI f : Global.getSector().getAllFactions()) if (allowed(f)) enemies.add(f);
            Collections.shuffle(enemies);
            for (FactionAPI f : enemies) { Industry ind = militaryOf(f); if (ind != null) return ind; }
            return null;
        }
        InvasionFleetManager inv = InvasionFleetManager.getManager();
        if (inv != null) {
            for (OffensiveFleetIntel o : inv.getActiveIntelCopy()) {
                if (o.isEnded() || o.isEnding() || o.getTarget() == null || !o.getTarget().isPlayerOwned()) continue;
                MarketAPI from = o.getMarketFrom();
                if (!allowed(from)) continue;
                Industry ind = military(from);
                if (ind != null) return ind;
            }
        }
        Industry ind = mgr.pickMilitarySabotageTarget(Global.getSector().getPlayerFaction());
        return ind != null && allowed(ind.getMarket()) ? ind : null;
    }

    static final String RANDOM = "random";

    /** The biggest market of a faction with a military industry to sabotage, or null. */
    static Industry militaryOf(FactionAPI f) {
        Industry best = null; int bestSize = -1;
        for (MarketAPI m : Misc.getFactionMarkets(f)) {
            if (!allowed(m)) continue;
            Industry ind = military(m);
            if (ind != null && m.getSize() > bestSize) { bestSize = m.getSize(); best = ind; }
        }
        return best;
    }

    /** Estimated success in percent, as Nexerelin computes it (its own method is protected). */
    static float success(CovertActionIntel op, MarketAPI m) {
        CovertOpsManager.CovertActionDef def = op.getDef();
        int level = op.getLevel();
        float base = def.successChance * 100f;
        if (base <= 0f) return 0f;
        float chance = base + (100f - base) * 0.15f * (level - 1), mult = 1f;
        if (def.useIndustrySecurity) for (Industry ind : m.getIndustries()) mult *= CovertOpsManager.getIndustrySuccessMult(ind);
        if (def.useAlertLevel) mult *= Math.max(0f, 1f - CovertOpsManager.getAlertLevel(m));
        if (def.useIndustrySecurity && m.getAdmin() != null && m.getAdmin().isAICore()) mult *= 0.75f;
        return chance * mult;
    }

    static boolean attack(CASettings s, CovertOpsManager mgr, AgentIntel a, List<AgentIntel> mine) {
        if (!canDo(a, "sabotageIndustry")) return false;
        Industry ind = target(mgr, s);
        if (ind == null) return false;
        MarketAPI m = ind.getMarket();
        for (CovertActionIntel op : mgr.getOngoingCovertActionsOfType(CovertActionIntel.class)) {   // one operation per enemy faction
            if (op.isPlayerInvolved() && !op.isEnded() && mine.contains(op.getAgent()) && op.getTargetFaction() == m.getFaction()
                    && !(op instanceof exerelin.campaign.intel.agents.Travel)) return false;
        }
        if (CovertOpsManager.getAlertLevel(m) > MAX_ALERT) return false;
        FactionAPI pf = Global.getSector().getPlayerFaction();
        SabotageIndustry op = new SabotageIndustry(a, m, ind, pf, m.getFaction(), true, new HashMap<String, Object>());
        if (success(op, m) < MIN_SUCCESS) return false;
        int cost = op.getCost();
        String key = "spy:sabotage:" + m.getId();
        if (cost > CABudget.available(s)) {
            if (CALog.once(key)) CALog.add("Agent " + name(a) + " waits for budget to sabotage " + ind.getCurrentName() + " at " + m.getName() + " (" + Misc.getDGSCredits(cost) + ")");
            return false;
        }
        order(a, op, m, cost);
        CABudget.charge(s, cost, 0f, 0f);
        CALog.forget(key);
        CALog.add("Agent " + name(a) + " sent to sabotage " + ind.getCurrentName() + " at " + m.getName() + " (" + m.getFaction().getDisplayName()
                + ", about " + Math.round(success(op, m)) + " percent success, " + Misc.getDGSCredits(cost) + ")");
        return true;
    }

    // ---- recruiting

    /** Agents to keep: your setting, or Auto: security and relations, plus offense with "Defense and offense". */
    static int keep(CASettings s) { return s.spyKeep > 0 ? s.spyKeep : (offense(s) ? 3 : 2); }

    /** Defense and offense, unless Low profile is on: sabotage draws the faction it hits (2.43.0). */
    static boolean offense(CASettings s) { return s.spyMode == 2 && !CALowProfile.on(); }

    /** The roles to fill, in order: security, relations, then offense (only with offense on), up to keep(). */
    static List<String> wanted(CASettings s) {
        List<String> out = new ArrayList<String>();
        out.add(SECURITY);
        out.add(RELATIONS);
        if (offense(s)) out.add(OFFENSE);
        while (out.size() > keep(s)) out.remove(out.size() - 1);
        return out;
    }

    /** Recruits whose RECRUIT_DAYS are up report for duty. */
    static void arrive(CAData d, CovertOpsManager mgr, List<AgentIntel> mine) {
        Iterator<Map.Entry<String, Long>> it = d.spyRecruits().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (Global.getSector().getClock().getElapsedDaysSince(e.getValue()) < RECRUIT_DAYS) continue;
            MarketAPI home = null;
            for (MarketAPI m : Misc.getPlayerMarkets(false)) if (!CAEngine.isOutpost(m) && (home == null || m.getSize() > home.getSize())) home = m;
            if (home == null) continue;   // no colony to report to yet
            String role = e.getKey();
            AgentIntel.Specialization spec = SECURITY.equals(role) ? AgentIntel.Specialization.HYBRID
                    : RELATIONS.equals(role) ? AgentIntel.Specialization.NEGOTIATOR : AgentIntel.Specialization.SABOTEUR;
            FactionAPI pf = Global.getSector().getPlayerFaction();
            PersonAPI p = pf.createRandomPerson();
            p.setRankId(Ranks.AGENT);
            p.setPostId(Ranks.POST_AGENT);
            AgentIntel a = new AgentIntel(p, pf, 1);
            if (NexConfig.useAgentSpecializations) a.addSpecialization(spec);
            a.setMarket(home);
            a.init();
            a.setImportant(true);
            d.spyAgents().add(p.getId());
            d.spyRoles().put(p.getId(), role);
            mine.add(a);
            it.remove();
            CALog.add("Agent " + p.getNameString() + " (" + spec.getName() + ", level 1) reported for duty at " + home.getName() + ": " + roleName(role)
                    + "; Nexerelin pays " + Misc.getDGSCredits(AgentIntel.getSalary(1)) + " a month in salary", true);
        }
    }

    /**
     * Recruits for the first role in wanted() nobody holds or is being recruited for: security as soon as you have a colony,
     * the others once the month's budget has twice the cost to spare. Pays now; the agent reports RECRUIT_DAYS later.
     */
    static void hire(CAData d, CASettings s, CovertOpsManager mgr, List<AgentIntel> mine) {
        if (s.spyNoHiring) return;
        Set<String> held = new HashSet<String>();
        for (AgentIntel a : mine) { String r = d.spyRoles().get(a.getAgent().getId()); if (r != null) held.add(r); }
        held.addAll(d.spyRecruits().keySet());
        String role = null;
        for (String r : wanted(s)) if (!held.contains(r)) { role = r; break; }
        if (role == null || mine.size() + d.spyRecruits().size() >= keep(s)) return;
        // one of your own idle agents could take it. Security (2.42.2): Autopilot takes it over, since an unanswered Pather cell
        // wrecks a colony; you can take it back on the Mod features page. Other roles: don't pay for another, say how to hand it over
        for (AgentIntel a : agents(mgr)) {
            if (mine.contains(a) || busy(mgr, a)) continue;
            if (SECURITY.equals(role) && canDo(a, "infiltrateCell")) {
                d.spyAgents().add(a.getAgent().getId());
                d.spyRoles().put(a.getAgent().getId(), SECURITY);
                mine.add(a);
                CALog.add("Agents: took over your idle agent " + name(a) + " (" + types(a) + ") for " + roleName(SECURITY)
                        + ". Take it back on the Colony Autopilot intel's Mod features page, under Agents", true);
                return;
            }
            if (CALog.once("spy:yours")) CALog.add("Agents: your agent " + name(a) + " (" + types(a) + ") is idle, so Autopilot doesn't recruit one. "
                    + "To have Autopilot use it, click Let Autopilot use it on the Colony Autopilot intel's Mod features page, under Agents");
            return;
        }
        if (mgr.getAgents().size() + d.spyRecruits().size() >= mgr.getMaxAgents().getModifiedInt()) {
            if (CALog.once("spy:max")) CALog.add("Agents: Nexerelin's agent limit is reached, so no agent was recruited (Officer Training and Officer Management raise it)");
            return;
        }
        float fee = CABudget.fee(s, HIRE_FEE * 0.1f), total = HIRE_FEE + fee;
        float room = CABudget.available(s);
        if (SECURITY.equals(role) ? total > room : 2f * total > room) {
            if (CALog.once("spy:hirebudget:" + role)) CALog.add("Agents: recruiting for " + roleName(role) + " waits for budget (" + Misc.getDGSCredits(total) + ")");
            return;
        }
        if (CABuild.credits() - total < s.reserve) return;
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(total);
        CABudget.charge(s, HIRE_FEE, fee, 0f);
        d.spyRecruits().put(role, Global.getSector().getClock().getTimestamp());
        CALog.forget("spy:max"); CALog.forget("spy:yours");
        CALog.add("Agents: recruiting one for " + roleName(role) + " (" + Misc.getDGSCredits(total) + "); reports for duty in " + Math.round(RECRUIT_DAYS) + " days");
    }

    // ---- empire panel

    /** The Agents section of the empire panel's Mod features page: every agent you have, by type, with who gives it orders, and enemy operations. */
    static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        java.awt.Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        java.awt.Color base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        CATheme.heading(t, "Agents (Nexerelin)", 0f);
        CovertOpsManager mgr = CovertOpsManager.getManager();
        if (mgr == null) { t.addPara("Nexerelin's agents aren't running in this save.", gray, 6f); return; }
        if (s.spyMode == 0) t.addPara("Agents are off: Autopilot gives no orders and hires no one. Turn them on in Settings, under Agents (Nexerelin).", gray, 6f);
        else t.addPara("Mode: %s. Recruiting: %s, keeping %s.", 6f, hl, CASettings.SPY_NAMES[Math.max(0, Math.min(2, s.spyMode))],
                s.spyNoHiring ? "off" : "on", "" + keep(s));
        for (Map.Entry<String, Long> e : d.spyRecruits().entrySet()) {
            float left = Math.max(0f, RECRUIT_DAYS - Global.getSector().getClock().getElapsedDaysSince(e.getValue()));
            t.addPara("Recruiting for %s: reports for duty in %s days.", 4f, hl, roleName(e.getKey()), "" + (int) Math.ceil(left));
        }
        t.addPara("Autopilot only gives orders to agents it hired or you hand over, and only operations the agent's type can run: "
                + "Negotiators and Hybrids raise relations, Saboteurs sabotage industry, any type handles rebellions, Pather cells and pirate bases.", gray, 4f);
        t.addPara("Roles: the first agent is on security (what Nexerelin's Internal security mode does: Counter-Insurgency on a rebellion, "
                + "Infiltrate Cell on a Pather cell, Find Pirate Base for a hidden base); the second raises relations with your preferred factions "
                + "(picked like ambassadors); with offense on, a third sabotages enemy military. Recruiting one costs 12,000 plus the fee and takes "
                + Math.round(RECRUIT_DAYS) + " days. Agents with nothing to do wait at their colony.", gray, 4f);
        List<AgentIntel> all = agents(mgr), mine = managed(d, mgr);
        if (all.isEmpty()) t.addPara(s.spyMode != 0 && !s.spyNoHiring ? "You have no agents; Autopilot hires one when there's work for it." : "You have no agents.", gray, 6f);
        for (AgentIntel a : all) {
            boolean auto = mine.contains(a);
            String role = auto ? d.spyRoles().get(a.getAgent().getId()) : null;
            String doing = !auto || s.spyMode == 0 ? "idle" : SECURITY.equals(role) ? "idle: no rebellion, Pather cell or hidden pirate base at your colonies"
                    : RELATIONS.equals(role) ? "idle: no preferred faction to raise relations with (or no budget)"
                    : OFFENSE.equals(role) ? "idle: no enemy military target it can sabotage at 60% or better" : "idle";
            for (CovertActionIntel op : mgr.getOngoingCovertActionsOfType(CovertActionIntel.class)) {
                if (op.getAgent() == a && !op.isEnded() && !op.isEnding() && op.getResult() == null) {
                    doing = op.getActionName(false) + (op.getMarket() != null ? " at " + op.getMarket().getName() : "");
                    break;
                }
            }
            t.addPara("%s, level %s %s: " + CALog.esc(doing) + (a.getMarket() != null ? " (at " + CALog.esc(a.getMarket().getName()) + ")" : "")
                    + ". %s", 8f, new java.awt.Color[]{hl, hl, hl, auto ? hl : gray},
                    CALog.esc(name(a)), "" + a.getLevel(), CALog.esc(types(a)), auto ? "Autopilot gives its orders" + (role != null ? ", " + roleName(role) : "") : "Yours; Autopilot leaves it alone");
            t.addButton(auto ? "Take it back" : "Let Autopilot use it", new String[]{"agent", auto ? "back" : "use", a.getAgent().getId()},
                    base, dark, Alignment.MID, com.fs.starfarer.api.ui.CutStyle.TL_BR, 180f, 22f, 4f);
        }
    }
}
