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
 * Nexerelin agents (setting "Agents", off by default). Only agents Autopilot hired are given orders; yours are left alone.
 * Nexerelin has no counter-intelligence and no bonus for an agent parked at a colony, so defense means running the
 * operations Nexerelin's own automatic mode runs, in the same order: Counter-Insurgency on a rebellion, Infiltrate Cell
 * on an active Pather cell, Find Pirate Base for hidden bases. After those, Raise Relations with a faction that has
 * operations running against your colonies while it isn't at war with you: above Neutral its Strategic AI can't start
 * them and drops the ones it is running. Offense (setting "Defense and offense") only targets factions already hostile to
 * you, never allies, factions in a ceasefire or your commissioner, since a detected operation can start a war: Sabotage
 * Industry on the military industry of a market an invasion or raid against you comes from, else Nexerelin's own
 * military sabotage pick, at 60% estimated success or better, alert 0.2 or lower, one operation per enemy faction.
 * Operation costs and hiring come out of the monthly budget and never the credit reserve; Nexerelin pays salaries
 * itself. Hiring makes a level-1 agent for 12,000 (Nexerelin's own price for a chosen specialization), Hybrid first, a
 * Saboteur with offense on, never past "Agents to keep" or Nexerelin's agent limit, and only while there's work for one.
 * Only person ids are saved, so the save loads without Nexerelin.
 */
public class CASpies {
    public static final String MOD = "nexerelin";
    static final int HIRE_FEE = 12000;
    static final float MIN_SUCCESS = 60f, MAX_ALERT = 0.2f;
    static final String[] MILITARY = {Industries.HIGHCOMMAND, Industries.MILITARYBASE, Industries.PATROLHQ, Industries.HEAVYBATTERIES,
            Industries.GROUNDDEFENSES, Industries.MEGAPORT, Industries.SPACEPORT};

    public static boolean enabled() { return Global.getSettings().getModManager().isModEnabled(MOD); }

    /** Daily, from CAAuto. */
    public static void run(CAData d, CASettings s) {
        if (s.spyMode == 0 || Misc.getPlayerMarkets(false).isEmpty()) return;
        CovertOpsManager mgr = CovertOpsManager.getManager();
        if (mgr == null) return;
        List<AgentIntel> mine = managed(d, mgr);
        hire(d, s, mgr, mine);
        for (AgentIntel a : mine) {
            if (busy(mgr, a)) continue;
            if (defend(s, mgr, a)) continue;
            if (s.spyMode == 2) attack(s, mgr, a, mine);
        }
    }

    /** Autopilot's agents that still serve; drops the ids of dismissed ones. */
    static List<AgentIntel> managed(CAData d, CovertOpsManager mgr) {
        List<AgentIntel> out = new ArrayList<AgentIntel>();
        Set<String> alive = new HashSet<String>();
        for (AgentIntel a : mgr.getAgents()) {
            if (a.isDeadOrDismissed() || a.getAgent() == null) continue;
            if (d.spyAgents().contains(a.getAgent().getId())) { out.add(a); alive.add(a.getAgent().getId()); }
        }
        d.spyAgents().retainAll(alive);
        return out;
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
        if (local(s, a, new FindPirateBase(a, null, pf, true, null), mine, "findPirateBase", "finding the pirate base")) return true;
        return calm(s, mgr, a);
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
        CALog.add(target.getName() + ": agent " + name(a) + " sent for " + what + (cost > 0 ? " (" + Misc.getDGSCredits(cost) + ")" : ""), true);
        return true;
    }

    /** Enemy operations in flight against your colonies (the game never shows these). */
    static List<CovertActionIntel> enemyOps(CovertOpsManager mgr) {
        List<CovertActionIntel> out = new ArrayList<CovertActionIntel>();
        for (CovertActionIntel op : mgr.getOngoingCovertActionsOfType(CovertActionIntel.class)) {
            if (op.isPlayerInvolved() || op.isEnded() || op.isEnding() || op.getResult() != null) continue;
            MarketAPI t = op.getMarket();
            if (t != null && t.isPlayerOwned() && op.getAgentFaction() != null) out.add(op);
        }
        return out;
    }

    /** Raise Relations with a faction running operations against you that isn't at war with you. */
    static boolean calm(CASettings s, CovertOpsManager mgr, AgentIntel a) {
        if (!canDo(a, "raiseRelations")) return false;
        FactionAPI pf = Global.getSector().getPlayerFaction();
        Map<FactionAPI, Integer> count = new HashMap<FactionAPI, Integer>();
        for (CovertActionIntel op : enemyOps(mgr)) {
            FactionAPI f = op.getAgentFaction();
            Integer n = count.get(f);
            count.put(f, n == null ? 1 : n + 1);
        }
        List<FactionAPI> order = new ArrayList<FactionAPI>(count.keySet());
        final Map<FactionAPI, Integer> c = count;
        Collections.sort(order, new Comparator<FactionAPI>() { public int compare(FactionAPI x, FactionAPI y) { return c.get(y) - c.get(x); } });
        for (FactionAPI f : order) {
            if (f.isPlayerFaction() || f.isHostileTo(Factions.PLAYER) || !f.isAtBest(Factions.PLAYER, RepLevel.NEUTRAL)) continue;   // at war is your call
            if (!RaiseRelations.canModifyRelations(f, a)) continue;
            MarketAPI target = nearest(Misc.getFactionMarkets(f), a.getMarket());
            if (target == null) continue;
            RaiseRelations op = new RaiseRelations(a, target, pf, f, pf, true, new HashMap<String, Object>());
            int cost = op.getCost();
            String key = "spy:calm:" + f.getId();
            if (cost > CABudget.available(s)) {
                if (CALog.once(key)) CALog.add("Agent " + name(a) + " waits for budget to raise relations with " + f.getDisplayName() + " (" + Misc.getDGSCredits(cost) + ")");
                return false;
            }
            order(a, op, target, cost);
            CABudget.charge(s, cost, 0f, 0f);
            CALog.forget(key);
            CALog.add("Agent " + name(a) + " sent to " + target.getName() + " to raise relations with " + f.getDisplayName()
                    + ", which has operations running against your colonies (" + Misc.getDGSCredits(cost) + ")", true);
            return true;
        }
        return false;
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

    /** What to sabotage: the military of a market attacking you, else Nexerelin's own military pick. Null if nothing qualifies. */
    static Industry target(CovertOpsManager mgr) {
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
        Industry ind = target(mgr);
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
                + ", about " + Math.round(success(op, m)) + " percent success, " + Misc.getDGSCredits(cost) + ")", true);
        return true;
    }

    // ---- hiring

    static int keep(CASettings s) { return s.spyKeep > 0 ? s.spyKeep : (s.spyMode == 2 ? 2 : 1); }

    static boolean has(List<AgentIntel> mine, AgentIntel.Specialization spec) {
        for (AgentIntel a : mine) if (a.getSpecializationsCopy().contains(spec)) return true;
        return false;
    }

    /** Work a new agent would have now. */
    static boolean work(CovertOpsManager mgr, CASettings s, List<AgentIntel> mine) {
        if (s.spyMode == 2 && !has(mine, AgentIntel.Specialization.SABOTEUR) && target(mgr) != null) return true;
        if (!mine.isEmpty()) return false;   // one defender covers the defensive work
        if (!enemyOps(mgr).isEmpty()) return true;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (RebellionIntel.isOngoing(m) || m.hasCondition(Conditions.PATHER_CELLS) || m.hasCondition(Conditions.PIRATE_ACTIVITY)) return true;
        }
        return false;
    }

    static void hire(CAData d, CASettings s, CovertOpsManager mgr, List<AgentIntel> mine) {
        if (s.spyNoHiring || mine.size() >= keep(s)) return;
        if (!work(mgr, s, mine)) return;
        if (mgr.getAgents().size() >= mgr.getMaxAgents().getModifiedInt()) {
            if (CALog.once("spy:max")) CALog.add("Agents: Nexerelin's agent limit is reached, so no agent was hired (Officer Training and Officer Management raise it)");
            return;
        }
        if (HIRE_FEE > CABudget.available(s)) {
            if (CALog.once("spy:hirebudget")) CALog.add("Agents: hiring waits for budget (" + Misc.getDGSCredits(HIRE_FEE) + ")");
            return;
        }
        MarketAPI home = null;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (home == null || m.getSize() > home.getSize()) home = m;
        if (home == null) return;
        AgentIntel.Specialization spec = s.spyMode == 2 && has(mine, AgentIntel.Specialization.HYBRID) && !has(mine, AgentIntel.Specialization.SABOTEUR)
                ? AgentIntel.Specialization.SABOTEUR : AgentIntel.Specialization.HYBRID;
        FactionAPI pf = Global.getSector().getPlayerFaction();
        PersonAPI p = pf.createRandomPerson();
        p.setRankId(Ranks.AGENT);
        p.setPostId(Ranks.POST_AGENT);
        AgentIntel a = new AgentIntel(p, pf, 1);
        if (NexConfig.useAgentSpecializations) a.addSpecialization(spec);
        a.setMarket(home);
        a.init();   // as Nexerelin's own AddAgent command does
        a.setImportant(true);
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(HIRE_FEE);
        CABudget.charge(s, HIRE_FEE, 0f, 0f);
        d.spyAgents().add(p.getId());
        CALog.forget("spy:max"); CALog.forget("spy:hirebudget");
        CALog.add("Hired agent " + p.getNameString() + " (" + spec.getName() + ", level 1) at " + home.getName() + " for "
                + Misc.getDGSCredits(HIRE_FEE) + "; Nexerelin pays " + Misc.getDGSCredits(AgentIntel.getSalary(1)) + " a month in salary", true);
    }

    // ---- empire panel

    static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        CovertOpsManager mgr = CovertOpsManager.getManager();
        if (mgr == null) return;
        List<CovertActionIntel> enemy = enemyOps(mgr);
        if (s.spyMode == 0 && enemy.isEmpty()) return;
        java.awt.Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        t.addSectionHeading("Agents (Nexerelin)", Alignment.MID, 16f);
        t.addPara("Mode: %s. Hiring: %s, keeping %s.", 6f, hl, CASettings.SPY_NAMES[Math.max(0, Math.min(2, s.spyMode))],
                s.spyNoHiring ? "off" : "on", "" + keep(s));
        List<AgentIntel> mine = managed(d, mgr);
        if (s.spyMode != 0 && mine.isEmpty()) t.addPara("No agents hired by Autopilot yet; one is hired when there's work for it.", gray, 4f);
        for (AgentIntel a : mine) {
            String doing = "idle";
            for (CovertActionIntel op : mgr.getOngoingCovertActionsOfType(CovertActionIntel.class)) {
                if (op.getAgent() == a && !op.isEnded() && !op.isEnding() && op.getResult() == null) {
                    doing = op.getActionName(false) + (op.getMarket() != null ? " at " + op.getMarket().getName() : "");
                    break;
                }
            }
            t.addPara("%s, level %s " + CALog.esc(String.join("/", a.getSpecializationNames())) + ": " + CALog.esc(doing)
                    + (a.getMarket() != null ? " (at " + CALog.esc(a.getMarket().getName()) + ")" : ""), 3f, hl, CALog.esc(name(a)), "" + a.getLevel());
        }
        if (enemy.isEmpty()) t.addPara("No enemy operations against your colonies right now.", gray, 6f);
        else {
            t.addPara("Enemy operations against your colonies (the game doesn't show these):", 6f);
            for (CovertActionIntel op : enemy) {
                t.addPara("%s: " + CALog.esc(op.getActionName(false)) + " at " + CALog.esc(op.getMarket().getName()) + ", about %s days left", 3f,
                        op.getAgentFaction().getBaseUIColor(), CALog.esc(op.getAgentFaction().getDisplayName()), "" + Math.round(op.getStrategicActionDaysRemaining()));
            }
        }
    }
}
