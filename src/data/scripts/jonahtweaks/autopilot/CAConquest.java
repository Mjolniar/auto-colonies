package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FleetRequest;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_GrantAutonomy;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.groundbattle.GBUtils;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;

import java.awt.Color;
import java.util.*;

/**
 * 0.3.0.0 (WS-E, research 06 sections 2.2, 2.3, 4.7-4.9): invasions (Nexerelin) and the colonies they take.
 *
 * Invasions are requested as Nexerelin's fleet request does (InvasionIntel, its price of fleet points x 400 + marines x 50,
 * plus Colony Manager's fee, its fleet request points, its organize and travel time), at 1.25 x Nexerelin's wanted fleet
 * size with 1.25 x its own marine count. Targets:
 * - Retake (War level Retaliate, setting on): a market that was yours, held by a faction you may attack; your lost capital first.
 * - Conquer (War level Conquer): an Enemy faction's best market while it costs at most a quarter of the surplus; every
 *   Destroy faction's markets by score, its capital last.
 * One invasion at a time (two while rich); none while a conquered colony is unstable (rebels at half or more, stability
 * under 3, autonomy not taken back), while infamy with it would reach the coalition line (unless you allowed one), while
 * a colony you govern couldn't take one more colony governed by you (2 stability each, under the colony limit too; 0.3.0.0
 * fix) unless an idle administrator will govern the capture, while it would put you over the colony limit with no
 * administrator free (rich empires go ahead there: the recruiter covers it), or while the spare fleet request points can't send the
 * whole fleet (never a smaller, doomed one). 0.3.0.0 (fix): none while you hold a commission; an invasion whose market is
 * taken no longer counts as under way (CAWar.undecided).
 *
 * Conquered colonies (captures of Colony Manager's own invasions, CAData.conquered): Nexerelin's autonomy taken back at
 * once (autonomy: one more colony governed by you and the unrest only when every colony stays at the stability floor; CAItems
 * hires an administrator for the limit; no autonomy as a bridge); the mode your "Conquered colonies"
 * setting gives (a colony that was yours keeps its own); the Security goal until the rebellion is over, stability is 5
 * and the threat is down, then the goal it had; a relief fleet once Nexerelin's capture timeout ends and unrest remains;
 * a log line at stability 2 or less. It never becomes the capital by itself. Every action here that can cost stability
 * asks CAStability.canLose first.
 */
public class CAConquest {

    // ---------------- sizes and prices ----------------

    /** Nexerelin's expectBombable: not a habitable, unpolluted world, and its ground defense under 3,000. */
    static boolean bombable(MarketAPI t) {
        if (t.hasCondition(Conditions.HABITABLE) && !t.hasCondition(Conditions.POLLUTION)) return false;
        try { return MarketCMD.getBombardmentCost(t, null) < 3000; } catch (Throwable e) { return false; }
    }

    /** Marines to send: Nexerelin's own count (GBUtils garrison and its settings, CAWarPolicy.marines) x 1.25. */
    static int marinesFor(MarketAPI target) {
        float garrison = GBUtils.estimateTotalDefenderStrength(target, Global.getSector().getPlayerFaction(), false);
        return CAWarPolicy.marines(garrison, NexConfig.groundBattleGarrisonSizeMult, NexConfig.groundBattleInvasionTroopSizeMult,
                bombable(target), CAWarPolicy.MARINE_MARGIN);
    }

    /** Fleet points to send: 1.25 x Nexerelin's wanted invasion fleet (InvasionFleetManager.getWantedFleetSize), at least 100. */
    static float wantedFP(MarketAPI target) {
        float w = InvasionFleetManager.getWantedFleetSize(Global.getSector().getPlayerFaction(), target, 0f, false);
        return Math.max(CAWar.MIN_FP, Math.round(CAWarPolicy.FP_MARGIN * w));
    }

    /** Nexerelin's largest fleet request (nex_fleetRequestMaxFP, 2,000). */
    static float maxFP() {
        try { return Global.getSettings().getFloat("nex_fleetRequestMaxFP"); } catch (Throwable t) { return 2000f; }
    }

    /** Nexerelin's price per marine on a request: a quarter of the marines' base price. */
    static float perMarine() {
        try { return Global.getSettings().getCommoditySpec(Commodities.MARINES).getBasePrice() * Nex_FleetRequest.MARINE_COST_MULT; }
        catch (Throwable t) { return CAWarPolicy.MARINE_PRICE; }
    }

    /** One planned invasion. */
    static final class Plan {
        MarketAPI t; String kind; float fp, cost, fee, score; int marines; String why;
    }

    static Plan plan(CASettings s, MarketAPI t, String kind) {
        Plan p = new Plan();
        p.t = t; p.kind = kind;
        p.fp = wantedFP(t);
        p.marines = marinesFor(t);
        p.cost = Math.round(CAWarPolicy.invasionCost(p.fp, p.marines, NexConfig.fleetRequestCostPerFP, perMarine()));
        p.fee = CAWar.fee(s, kind, p.cost);
        return p;
    }

    // ---------------- targets ----------------

    /** A market an invasion may go at: targetable (CAWar), invadable by you (Nexerelin), no ground battle there, nothing of yours under way. */
    static boolean invadable(MarketAPI m) {
        if (!CAWar.targetable(m)) return false;
        try { if (!NexUtilsMarket.canBeInvaded(m, true)) return false; } catch (Throwable t) { return false; }
        try { if (GroundBattleIntel.getOngoing(m) != null) return false; } catch (Throwable ignored) { }
        return !CAWar.oursAt(m);
    }

    /** It was yours at the start (Nexerelin's original owner). */
    static boolean wasYours(MarketAPI m) {
        try { return NexUtilsMarket.wasOriginalOwner(m, Factions.PLAYER); } catch (Throwable t) { return false; }
    }

    /** The original owner is allied with you or is you (or unknown): a capture adds no infamy (DiplomacyManager.notifyMarketCaptured). */
    static boolean alliedWithOriginal(MarketAPI m) {
        try {
            String orig = NexUtilsMarket.getOriginalOwner(m);
            return orig == null || AllianceManager.areFactionsAllied(orig, Factions.PLAYER);
        } catch (Throwable t) { return true; }
    }

    /** Military markets of the target's faction in its system other than it (counter-invasions come from there). */
    static int sameSystemMilitary(MarketAPI t) {
        int n = 0;
        if (t.getContainingLocation() == null) return 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarkets(t.getContainingLocation())) {
            if (m == t || m.getFaction() != t.getFaction()) continue;
            if (m.hasIndustry(Industries.HIGHCOMMAND) || m.hasIndustry(Industries.MILITARYBASE) || m.hasIndustry(Industries.PATROLHQ)) n++;
        }
        return n;
    }

    static float score(Plan p, boolean capital) { return score(p, capital, false); }

    static float score(Plan p, boolean capital, boolean capitalFirst) {
        MarketAPI t = p.t;
        boolean port = t.hasSpaceport() || t.hasIndustry(Industries.HIGHCOMMAND) || t.hasIndustry(Industries.MILITARYBASE);
        return CAWarPolicy.invasionScore(t.getSize(), port, wasYours(t), capital, capitalFirst, p.cost + p.fee, sameSystemMilitary(t), CAWar.nearestColonyLY(t));
    }

    /**
     * Invasion targets of one faction at this effective level, best first: an Enemy faction's markets that cost at most a
     * quarter of the surplus, every Destroy faction's market with the capital last. 0.3.0.0 (fix, 06 section 4.7): a Destroy
     * capital whose bombardment landed in the last 365 days goes first instead, while its industries are disrupted.
     */
    static List<Plan> targets(CAData d, CASettings s, FactionAPI f, int stance) {
        List<Plan> out = new ArrayList<Plan>();
        MarketAPI capital = CAScorched.capital(f);
        boolean bombedCapital = stance >= CAStance.DESTROY && CAScorched.bombedRecently(d, capital);
        float pool = CASurplus.amount(s);
        List<MarketAPI> ms = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getFactionMarkets(f)) if (invadable(m)) ms.add(m);
        for (MarketAPI m : ms) {
            boolean isCap = m == capital;
            if (stance >= CAStance.DESTROY && isCap && ms.size() > 1 && !bombedCapital) continue;   // the capital last
            Plan p = plan(s, m, CAWarPolicy.INVASION);
            if (!CAWarPolicy.worthInvading(stance, p.cost + p.fee, pool)) continue;
            p.score = score(p, isCap, isCap && bombedCapital) * (isCap && bombedCapital ? 1000f : 1f);
            p.why = CAStance.name(stance) + ": " + f.getDisplayName() + (isCap && bombedCapital ? ", capital after bombardment" : "");
            out.add(p);
        }
        Collections.sort(out, BY_SCORE);
        return out;
    }

    // ---------------- gates and launching ----------------

    /** The first conquered colony that blocks the next invasion, as a reason, or null. */
    static String unstable(CAData d) {
        for (String id : d.conquered().keySet()) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null || m.getFaction() == null || !m.getFaction().isPlayerFaction()) continue;
            String why = CAWarPolicy.unstable(m.getName(), rebelShare(m), CAStability.now(m), !m.isPlayerOwned());
            if (why != null) return why;
        }
        return null;
    }

    /** Infamy the invasions of yours under way will add when they land (0.3.0.0 fix: only those still to land, CAWar.undecided). */
    static float pendingInfamy() {
        float add = 0f;
        for (InvasionIntel i : CAWar.invasionsUnderWay()) {
            MarketAPI t = i.getTarget();
            if (t != null) add += CAWarPolicy.captureInfamy(t.getSize(), alliedWithOriginal(t));
        }
        return add;
    }

    static float badboy() {
        try { return DiplomacyManager.getBadboy(Global.getSector().getPlayerFaction()); } catch (Throwable t) { return 0f; }
    }

    static float dominance() {
        try { return DiplomacyManager.getDominanceFactor(PlayerFactionStore.getPlayerFactionId()); } catch (Throwable t) { return 0f; }
    }

    /** Market sizes: {yours, every market's} (Nexerelin's dominance counts these). */
    static int[] sizes() {
        int ours = 0, all = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            all += m.getSize();
            if (m.getFaction() != null && m.getFaction().isPlayerFaction()) ours += m.getSize();
        }
        return new int[]{ours, all};
    }

    /** Why this invasion waits (CAWarPolicy.invasionWait and Nexerelin's largest request), or null. */
    static String gate(CAWar.Pass p, Plan pl) {
        if (pl.fp > maxFP()) return "it needs " + Math.round(pl.fp) + " points, over Nexerelin's largest request (" + Math.round(maxFP()) + ")";
        // 0.3.0.0 (fix): every capture you govern yourself costs each colony you govern CAStability.oneMoreGoverned() (2), under
        // the colony limit too (the management bonus shrinks), so the stability check runs for every invasion and retake. Before,
        // it ran only past the limit, and a capture under it could put a colony you govern at 0. over counts the invasions
        // under way. An idle administrator governs one capture past the limit (Nexerelin makes it autonomous, autonomy takes it
        // back with them); a capture under the limit lands governed by you, so there it covers nothing.
        // At most one capture lands under the limit at a time: a second one would cost the first 2 before the first could be
        // checked (CAWarPolicy.adminWait with captures).
        int coming = CAWar.invasionsUnderWay().size();
        int over = CAItems.overLimit() + 1 + coming;
        boolean idle = over > 0 && CAItems.idleAdmin(p.d) != null;
        String room = CAWarPolicy.adminWait(1 + coming, over, idle, p.rich, governedCanTake(1 + coming - (idle ? 1 : 0)));
        String payer = CAWar.payerFor(p.s, pl.kind, p.rich, pl.cost + pl.fee);
        // dominance once this capture and everything under way land (a capture raises it, and with it the infamy test)
        int[] sz = sizes();
        float dom = CAWarPolicy.projectedDominance(dominance(), sz[0], pendingGain() + pl.t.getSize(), sz[1], CAScorched.pendingLoss());
        return CAWarPolicy.invasionWait(coming, p.rich, unstable(p.d), room, badboy(), dom,
                pendingInfamy() + CAScorched.pendingInfamy(), CAWarPolicy.captureInfamy(pl.t.getSize(), alliedWithOriginal(pl.t)),
                p.s.warCoalitionOK, p.spare(), pl.fp, payer);
    }

    /** Market size your invasions under way would add (0.3.0.0 fix: only those still to land, CAWar.undecided). */
    static int pendingGain() {
        int gain = 0;
        for (InvasionIntel i : CAWar.invasionsUnderWay()) if (i.getTarget() != null) gain += i.getTarget().getSize();
        return gain;
    }

    /** Launches the invasion as Nexerelin's fleet request does; returns null when it went, or why not. */
    static String launch(CAWar.Pass p, Plan pl) {
        MarketAPI t = pl.t;
        MarketAPI from = CAWar.invasionSource(t);
        if (from == null) return "no colony with a working spaceport";
        String payer = CAWar.payerFor(p.s, pl.kind, p.rich, pl.cost + pl.fee);
        if (payer == null) return "waiting for credits";
        InvasionIntel intel = new InvasionIntel(Global.getSector().getPlayerFaction(), from, t, pl.fp,
                Nex_FleetRequest.getTimeToLaunch(pl.fp, Nex_FleetRequest.FleetType.INVASION));
        intel.setPlayerSpawned(true);
        intel.init();
        if (intel.isEnded() || intel.isEnding()) return "no route there";
        // 0.3.0.0 (fix): the fee only after init went through. init ends at once with no jump point to the target, and an
        // ending intel refunds its player fee (OffensiveFleetIntel.refundPlayerFeeIfNeeded): credits never paid
        intel.setPlayerFee((int) pl.cost);
        intel.setMarinesTotal(pl.marines);   // after init, which sets Nexerelin's own count (Nex_FleetRequest.launchFleet)
        CAWar.pay(p.s, payer, pl.cost, pl.fee);
        p.mgr.modifyFleetRequestStock(-(int) pl.fp);
        CAWar.record(p.d, pl.kind + ":" + t.getId());
        CALog.add(t.getName() + ": " + (int) pl.fp + "-pt invasion from " + from.getName() + ", " + pl.marines + " marines, "
                + CAWar.money(pl.cost, pl.fee) + " (" + pl.why + ")", true);
        float after = CAWarPolicy.infamy(badboy() + pendingInfamy(), dominance());
        if (after >= CAWarPolicy.INFAMY_NEAR && CALog.once("war:infamy:" + (int) (after / 5f)))
            CALog.add("War: infamy will be about " + Math.round(after) + " of " + Math.round(CAWarPolicy.INFAMY_LINE) + " once the invasions under way land (decays 3 a month)", true);
        return null;
    }

    /** Candidates tried per day, best first (a smaller market may pass the infamy line where the best doesn't). */
    static final int TRIES = 8;

    /** Launches the first candidate, best first, that passes the gates; one wait line (the best one's) when none does. */
    static boolean tryBest(CAWar.Pass p, List<Plan> cands) {
        String first = null;
        for (int i = 0; i < cands.size() && i < TRIES; i++) {
            Plan pl = cands.get(i);
            String wait = gate(p, pl);
            if (wait == null) wait = launch(p, pl);
            if (wait == null) return true;
            if (first == null) first = "invasion of " + pl.t.getName() + " (" + wait + ")";
        }
        if (first != null) p.waiting.add(first);
        return false;
    }

    /** 3. Retake (Retaliate, setting on): the best market that was yours, held by a faction you may attack; your lost capital first. */
    static boolean retake(CAWar.Pass p) {
        MarketAPI lost = CACapital.lostCapital(p.s);
        List<Plan> cands = new ArrayList<Plan>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            FactionAPI f = m.getFaction();
            if (f == null || f.isPlayerFaction() || m.isPlayerOwned() || !wasYours(m)) continue;
            // 0.3.0.0 (fix, 06 section 7.6): none while you hold a commission (Nexerelin gives the capture to your commissioner);
            // one wait line when a retake would otherwise go
            if (p.commissioned) {
                if (!f.isHostileTo(Factions.PLAYER) || !CAWarPolicy.allows(CAWarPolicy.RETAKE, p.eff(f), p.stance(f)) || !invadable(m)) continue;
                p.waiting.add("retake of " + m.getName() + " (not while you hold a commission: Nexerelin gives captures to your commissioner)");
                return false;
            }
            if (!p.allows(CAWarPolicy.RETAKE, f) || !invadable(m)) continue;
            Plan pl = plan(p.s, m, CAWarPolicy.RETAKE);
            pl.score = score(pl, false) * (m == lost ? 1000f : 1f);
            pl.why = m == lost ? "your capital" : "it was yours";
            cands.add(pl);
        }
        if (cands.isEmpty()) return false;
        Collections.sort(cands, BY_SCORE);
        return tryBest(p, cands);
    }

    static final Comparator<Plan> BY_SCORE = new Comparator<Plan>() { public int compare(Plan a, Plan b) { return Float.compare(b.score, a.score); } };

    /** 8. Conquer: the best invasion target over every Enemy and Destroy faction you may invade that passes the gates. */
    static boolean conquer(CAWar.Pass p) {
        if (p.commissioned) return false;   // 0.3.0.0 (fix): no invasions while you hold a commission (CAWarPolicy.allows)
        List<Plan> cands = new ArrayList<Plan>();
        Set<FactionAPI> seen = new HashSet<FactionAPI>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            FactionAPI f = m.getFaction();
            if (f == null || f.isPlayerFaction() || !seen.add(f)) continue;
            int st = p.stance(f);
            if (st < CAStance.ENEMY || !p.allows(CAWarPolicy.INVASION, f)) continue;
            cands.addAll(targets(p.d, p.s, f, st));
        }
        if (cands.isEmpty()) return false;
        Collections.sort(cands, BY_SCORE);
        return tryBest(p, cands);
    }

    /** For CASpies.prepare: the Destroy market an invasion would go at next (InstigateRebellion there first), or null. */
    static MarketAPI nextDestroyTarget(CAData d, CASettings s) {
        String rival = CAWar.autoRival(d, s);
        Plan best = null;
        Set<FactionAPI> seen = new HashSet<FactionAPI>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            FactionAPI f = m.getFaction();
            if (f == null || f.isPlayerFaction() || !seen.add(f) || CAWar.stance(s, f.getId(), rival) < CAStance.DESTROY) continue;
            if (!f.isHostileTo(Factions.PLAYER) || CAWar.effective(d, s, f, rival) < CAWarPolicy.CONQUER) continue;
            List<Plan> l = targets(d, s, f, CAStance.DESTROY);
            if (!l.isEmpty() && (best == null || l.get(0).score > best.score)) best = l.get(0);
        }
        return best == null ? null : best.t;
    }

    // ---------------- conquered colonies ----------------

    /** The rebels' share of the fighting strength at m (0 with no rebellion). */
    static float rebelShare(MarketAPI m) {
        try {
            RebellionIntel r = RebellionIntel.getOngoingEvent(m);
            return r == null ? 0f : CAWarPolicy.rebelShare(r.getGovtStrength(), r.getRebelStrength());
        } catch (Throwable t) { return 0f; }
    }

    /** Whether Colony Manager sent the invasion that took m (an invasion or retake of it recorded in the last year). */
    static boolean ours(CAData d, String marketId) {
        return CAWar.daysSince(d, CAWarPolicy.INVASION + ":" + marketId) <= CAWarPolicy.OP_MEMORY_DAYS
                || CAWar.daysSince(d, CAWarPolicy.RETAKE + ":" + marketId) <= CAWarPolicy.OP_MEMORY_DAYS;
    }

    /** CAMark key that holds the goal a conquered colony had before Colony Manager set Security ("conqprof:<id>|<PROFILE>"). */
    static final String PROFILE_MARK = "conqprof:";

    /**
     * From CAWarListener: a market changed hands. A capture by you of a market Colony Manager invaded is recorded and set up
     * (mode, Security goal); everything else is left alone (your own conquests stay yours to run).
     */
    static void onCaptured(MarketAPI m, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved, boolean isCapture) {
        if (m == null || newOwner == null || !isCapture || !newOwner.isPlayerFaction() || CALog.whatIf()) return;
        CAData d = CAData.get();
        if (!ours(d, m.getId()) || d.conquered().containsKey(m.getId())) return;
        record(d, m, oldOwner == null ? null : oldOwner.getDisplayName());
    }

    /** Records a conquest and sets the colony up. */
    static void record(CAData d, MarketAPI m, String from) {
        String id = m.getId();
        boolean known = d.colonies.containsKey(id);   // a colony that was yours keeps its own mode
        d.conquered().put(id, Global.getSector().getClock().getTimestamp());
        CAData.Colony c = d.colony(id);
        CASettings s = d.settings;
        if (!known) {
            int mode = Math.max(0, Math.min(2, s.conqueredMode));
            c.mode = mode == 0 ? CAData.Mode.AUTO : mode == 1 ? CAData.Mode.APPROVE : CAData.Mode.ADVISOR;
        }
        if (c.profile != CAData.Profile.SECURITY) {
            CAMark.set(PROFILE_MARK + id + "|" + c.profile.name(), CAMark.now());
            c.profile = CAData.Profile.SECURITY;
        }
        CALog.add(m.getName() + ": captured" + (from != null ? " from " + from : "") + ". Mode " + c.mode.label + ", goal Security until stable", true);
    }

    /** Puts back the goal a conquered colony had before Security, once (its CAMark). */
    static void restoreProfile(CAData d, MarketAPI m) {
        for (String k : CAMark.keys(PROFILE_MARK + m.getId() + "|")) {
            String name = k.substring(k.indexOf('|') + 1);
            CAMark.clear(k);
            CAData.Colony c = d.colony(m.getId());
            if (c.profile != CAData.Profile.SECURITY) continue;   // you changed it since: yours
            try { c.profile = CAData.Profile.valueOf(name); } catch (Throwable t) { c.profile = CAData.Profile.BALANCED; }
            CALog.add(m.getName() + ": stable after capture (stability " + Math.round(CAStability.now(m)) + "); goal back to " + c.profile.label);
        }
    }

    static boolean profileHeld(MarketAPI m) { return !CAMark.keys(PROFILE_MARK + m.getId() + "|").isEmpty(); }

    /** Catches a capture the listener missed (a load in between): a market of yours that Colony Manager invaded and Nexerelin marks as just captured. */
    static void catchMissed(CAData d) {
        for (MarketAPI m : Misc.getFactionMarkets(Global.getSector().getPlayerFaction())) {
            if (d.conquered().containsKey(m.getId()) || !ours(d, m.getId())) continue;
            String from = null;
            try {
                if (!m.getMemoryWithoutUpdate().contains(SectorManager.MEMORY_KEY_RECENTLY_CAPTURED)) continue;
                FactionAPI f = Global.getSector().getFaction(m.getMemoryWithoutUpdate().getString(SectorManager.MEMORY_KEY_RECENTLY_CAPTURED));
                from = f == null ? null : f.getDisplayName();
            } catch (Throwable t) { continue; }
            record(d, m, from);
        }
    }

    /**
     * Whether every colony you govern (no administrator) can take this many more colonies governed by you and stay at the
     * stability floor. Each one costs every colony you govern CAStability.oneMoreGoverned() (Misc.getOutpostPenalty, 2):
     * PopulationAndInfrastructure.getMismanagementPenalty is (governed - limit) x that number with no floor at 0, a penalty
     * past the colony limit and a smaller management bonus under it. 0.3.0.0 (fix): the check is the same under the limit.
     * Quiet.
     */
    static boolean governedCanTake(int more) {
        float pen = Math.max(0, more) * CAStability.oneMoreGoverned();
        if (pen <= 0f) return true;
        for (MarketAPI x : Misc.getPlayerMarkets(false))
            if (CAItems.governedByYou(x) && !CAStability.canLose(x, -pen, null)) return false;
        return true;
    }

    /**
     * Nexerelin makes a captured market autonomous when you're out of governing capacity. 0.3.0.0 (fix, 06 section 4.9): taken
     * back at once, checked again every day until it is:
     * - once the colony's own stability can take the unrest (none when Nexerelin set its no-loss flag on capture), and past the
     *   colony limit with no administrator also its share of the colony-limit penalty;
     * - while every colony you govern can take one more colony governed by you (2 stability each, under the colony limit
     *   too) besides the captures of the invasions under way, or an idle administrator governs it. 0.3.0.0 (fix): under the
     *   limit this wasn't checked. An idle administrator is put there past the limit, and under it when the colonies you
     *   govern can't take one more. Past the limit with no administrator, CAItems then hires one for the limit.
     * Never 0 stability (CAStability.canLose): while the limit is what holds it, it is on CAAutonomy's list so CAItems hires an
     * administrator; while its own stability, or under the limit the colonies you govern, hold it, it is off that list, so
     * CAAutonomy.run (later the same day) can't take it back without these checks. Rich empires invade over the limit only
     * when the penalty fits (gate), so this wait is rare.
     */
    static void autonomy(CAData d, MarketAPI m) {
        String id = m.getId();
        if (m.isPlayerOwned()) return;   // CAAutonomy.tidy drops it from its list
        boolean noLoss = m.getMemoryWithoutUpdate().getBoolean(Nex_GrantAutonomy.MEMORY_KEY_NO_STAB_LOSS);
        int over = CAItems.overLimit() + 1;
        // 0.3.0.0 (fix): the colonies you govern are checked at any count, with room kept for the invasions under way (gate
        // counted their captures); an idle administrator is used past the limit, or under it when they can't take one more
        int coming = CAWar.invasionsUnderWay().size();
        boolean othersFit = governedCanTake(1 + coming);
        AdminData idle = over > 0 || !othersFit ? CAItems.idleAdmin(d) : null;
        boolean fits = over <= 0 || idle != null;
        // 0.3.0.0 (fix): governed by you, it also pays for the captures under way that land under the limit after it
        float later = idle != null ? 0f : CAWarPolicy.landGoverned(coming, over + coming) * CAStability.oneMoreGoverned();
        float own = (noLoss ? 0f : CAAutonomy.REVOKE_UNREST) + (fits ? 0f : over * Misc.getOutpostPenalty()) + later;
        boolean ownOk = own <= 0f || CAStability.canLose(m, -own, null);
        boolean othersOk = idle != null || othersFit;
        if (ownOk && othersOk) {
            Nex_GrantAutonomy.revokeAutonomy(m);
            d.autonomous().remove(id);
            if (idle != null) { m.setAdmin(idle.getPerson()); idle.setMarket(m); }
            CALog.forget("conq:auton:stab:" + id);
            CALog.forget("conq:auton:limit:" + id);
            CALog.forget("conq:auton:gov:" + id);
            CALog.add(m.getName() + ": taken back from Nexerelin's autonomy after capture" + (idle != null ? "; " + idle.getPerson().getNameString() + " governs it"
                    : fits ? "" : "; over your colony limit until an administrator is hired"), true);
            return;
        }
        if (fits && !othersOk) {
            // 0.3.0.0 (fix): under the limit with no idle administrator, the colonies you govern hold it. Off CAAutonomy's list:
            // CAAutonomy takes colonies on it back under the limit without this check
            d.autonomous().remove(id);
            if (CALog.once("conq:auton:gov:" + id))
                CALog.add(m.getName() + ": autonomous after capture (Nexerelin); taking it back would cost each colony you govern "
                        + Math.round(CAStability.oneMoreGoverned()) + " stability and put one under " + Math.round(CAStability.floor())
                        + ". Taken back once they can take it or an administrator is free", true);
            return;
        }
        if (fits) {
            d.autonomous().remove(id);   // its own stability holds it: CAAutonomy must not take it back today
            if (CALog.once("conq:auton:stab:" + id))
                CALog.add(m.getName() + ": autonomous after capture (Nexerelin); taken back once its stability can take " + CAAutonomy.REVOKE_UNREST + " unrest", true);
            return;
        }
        if (!d.autonomous().containsKey(id)) d.autonomous().put(id, Global.getSector().getClock().getTimestamp());   // CAItems hires for it
        if (CALog.once("conq:auton:limit:" + id))
            CALog.add(m.getName() + ": autonomous after capture (Nexerelin); taking it back over your colony limit would put a colony under stability "
                    + Math.round(CAStability.floor()) + ". Autopilot hires an administrator and takes it back then", true);
    }

    /** A relief fleet once Nexerelin's capture timeout is over and unrest remains (2 x the stabilize package, an emergency). */
    static void reliefIfDue(CASettings s, MarketAPI m) {
        if (CAWarPolicy.clamp(s.warLevel) < CAWarPolicy.DEFEND || !m.isPlayerOwned()) return;
        if (m.getMemoryWithoutUpdate().contains(SectorManager.MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT)) return;
        if (RecentUnrest.getPenalty(m) <= 0) return;
        Long last = CAWar.lastRelief.get(m.getId());
        if (last != null && Global.getSector().getClock().getElapsedDaysSince(last) < CAWar.RELIEF_DAYS) return;
        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        if (mgr == null) return;
        if (CAWar.launch(s, mgr, CAWarPolicy.RELIEF, m, 0f, CASurplus.rich(s), true, "unrest after capture"))
            CAWar.lastRelief.put(m.getId(), Global.getSector().getClock().getTimestamp());
    }

    /**
     * Daily (CAAuto, breaker "nexerelin:conquered"): looks after every conquered colony. 0.3.0.0 (fix): its own breaker key
     * (it shared "nexerelin:conquest" with retake and conquer, so one tripped or reset the other); each colony in its own
     * try, so one that throws doesn't skip the others; also adds the war listener after a load (CAWarListener.ensure, also
     * while the war fleet step is stopped) and catches a bombardment the listener missed (CAScorched.catchMissed).
     */
    public static void tend(CAData d, CASettings s) {
        try { CAWarListener.ensure(); } catch (Throwable t) { CAStep.warn("conquered colonies step", "war listener failed", t); }
        try { catchMissed(d); } catch (Throwable t) { CAStep.warn("conquered colonies step", "missed-capture scan failed", t); }
        try { CAScorched.catchMissed(d); } catch (Throwable t) { CAStep.warn("conquered colonies step", "missed-bombardment scan failed", t); }
        for (String id : new ArrayList<String>(d.conquered().keySet())) {
            try { tendOne(d, s, id); }
            catch (Throwable t) { CAStep.warn("conquered colonies step", "conquered colony " + id + " failed", t); }
        }
    }

    /** One conquered colony's day (tend). */
    static void tendOne(CAData d, CASettings s, String id) {
        MarketAPI m = Global.getSector().getEconomy().getMarket(id);
        Long at = d.conquered().get(id);
        if (m == null || m.getFaction() == null || !m.getFaction().isPlayerFaction()) {
            d.conquered().remove(id);
            for (String k : CAMark.keys(PROFILE_MARK + id + "|")) CAMark.clear(k);
            CALog.add((m != null ? m.getName() : id) + ": conquered colony lost" + (m != null && m.getFaction() != null ? " to " + m.getFaction().getDisplayName() : ""), true);
            return;
        }
        autonomy(d, m);
        float stab = CAStability.now(m), rebels = rebelShare(m);
        boolean calm = rebels <= 0f && stab >= CAWarPolicy.STABLE_AT && !d.colony(id).threatHigh;
        if (calm && m.isPlayerOwned()) restoreProfile(d, m);
        reliefIfDue(s, m);
        if (stab <= 2f) {
            if (CALog.once("conq:low:" + id))
                CALog.add(m.getName() + ": stability " + Math.round(stab) + " after capture; no new invasion until it's 3 or more", true);
        } else CALog.forget("conq:low:" + id);
        float days = at == null ? Float.MAX_VALUE : Global.getSector().getClock().getElapsedDaysSince(at);
        if (calm && m.isPlayerOwned() && !profileHeld(m) && days >= CAWarPolicy.SETTLE_DAYS) {
            d.conquered().remove(id);
            CALog.forget("conq:low:" + id);
            CALog.add(m.getName() + ": settled after capture (stability " + Math.round(stab) + ")");
        }
    }

    /** Conquered colonies with the rebels at half the fighting strength or more (CASpies puts counter-insurgency there first). */
    static List<MarketAPI> rebelling(CAData d) {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (String id : d.conquered().keySet()) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m != null && m.isPlayerOwned() && rebelShare(m) >= CAWarPolicy.REBELS_BLOCK) out.add(m);
        }
        return out;
    }

    /** War page: the conquered colonies Colony Manager looks after. */
    static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        if (d.conquered().isEmpty()) return;
        Color hl = Misc.getHighlightColor();
        t.addPara("Conquered colonies (mode " + CASettings.CONQUERED_NAMES[Math.max(0, Math.min(2, s.conqueredMode))] + "):", Misc.getGrayColor(), 8f);
        for (String id : d.conquered().keySet()) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null) continue;
            float timeout = 0f;
            try {
                if (m.getMemoryWithoutUpdate().contains(SectorManager.MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT))
                    timeout = m.getMemoryWithoutUpdate().getExpire(SectorManager.MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT);
            } catch (Throwable ignored) { }
            String admin = m.getAdmin() == null || m.getAdmin().isPlayer() ? "governed by you" : "administrator " + m.getAdmin().getNameString();
            t.addPara("%s: stability %s, rebels %s percent, " + (timeout > 0f ? "timeout " + (int) Math.ceil(timeout) + " days, " : "")
                    + (m.isPlayerOwned() ? "" : "autonomous, ") + CALog.esc(admin) + ".", 2f, hl,
                    m.getName(), "" + Math.round(CAStability.now(m)), "" + Math.round(rebelShare(m) * 100f));
        }
    }
}
