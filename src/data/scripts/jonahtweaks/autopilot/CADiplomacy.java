package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.intel.diplomacy.AllianceOfferIntel;
import exerelin.campaign.intel.diplomacy.CeasefirePromptIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 0.3.0.0 (WS-E, research 06 section 4.10): diplomacy by stance (Nexerelin), daily from CAAuto after the agents.
 * - Ceasefire and peace offers (Nexerelin's CeasefirePromptIntel): accepted from Ally, Friendly and Neutral factions at any
 *   War level; rejected from Enemy and Destroy factions from War level Retaliate up (at Off and Defend they wait for you);
 *   from Auto and Rival factions they wait for you, with one log line saying which answer Colony Manager would give and why.
 * - Alliance offers from an Ally faction: one log line. Joining stays your call: an alliance brings its wars.
 * - Keep hostile: an Enemy or Destroy faction you declared war on (War page) whose relations rose above Hostile is made
 *   hostile again, at most once in 30 days per faction, the way Nexerelin's fleet request does it.
 * - Declarations: only from the War page's confirmation (declareWar), one in 30 days.
 * It never creates a ceasefire or peace treaty itself: it answers Nexerelin's own offers, as you would in the intel.
 * 0.3.0.0 (fix): a stance a save from before 0.3.0 got from its faction priority, and that you haven't set since, leaves every
 * offer to you, as 0.2.45 did (CAStance.fromOldPriority).
 */
public class CADiplomacy {

    public static void run(CAData d, CASettings s) {
        int level = CAWarPolicy.clamp(s.warLevel);
        offers(d, s, level);
        alliances(s);
        keepHostile(d, s, level);
    }

    /** Ceasefire and peace offers waiting for an answer, answered by the offering faction's stance. */
    static void offers(CAData d, CASettings s, int level) {
        List<CeasefirePromptIntel> open = new ArrayList<CeasefirePromptIntel>();
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(CeasefirePromptIntel.class)) {
            if (!(i instanceof CeasefirePromptIntel)) continue;
            CeasefirePromptIntel c = (CeasefirePromptIntel) i;
            if (!c.isEnded() && !c.isEnding() && c.getState() == 0) open.add(c);
        }
        for (CeasefirePromptIntel c : open) {
            FactionAPI f = c.getFactionForUIColors();
            if (f == null) continue;
            int st = s.stanceOf(f.getId());
            String what = c.getName(false);
            // 0.3.0.0 (fix): a stance from a 0.2.45 priority you haven't set since leaves offers to you, as 0.2.45 did
            boolean old = CAStance.fromOldPriority(s, d, f.getId());
            int answer = CAStance.ceasefire(st, old);
            if (answer > 0) {
                c.accept();
                CALog.add(f.getDisplayName() + ": " + lower(what) + " accepted (" + CAStance.name(st) + ")", true);
            } else if (answer < 0 && level >= CAWarPolicy.RETALIATE) {
                c.reject();
                CALog.add(f.getDisplayName() + ": " + lower(what) + " rejected (" + CAStance.name(st) + ")", true);
            } else {
                Long at = c.getPlayerVisibleTimestamp();
                if (CALog.once("dip:offer:" + f.getId() + ":" + (at == null ? 0L : at)))
                    CALog.add(f.getDisplayName() + ": " + lower(what) + " waits for you in the intel; " + advice(d, f, st)
                            + (old ? ". Stance " + CAStance.name(st) + " is from your 0.2.45 priority; set it on the War page to let Autopilot answer" : ""));
            }
        }
    }

    /** What Colony Manager would answer a faction it leaves to you, and why. */
    static String advice(CAData d, FactionAPI f, int st) {
        int ops = 0;
        try {
            for (OffensiveFleetIntel o : CAWar.offensives())
                if (CAWar.yours(o) && o.getTarget() != null && o.getTarget().getFaction() == f) ops++;
        } catch (Throwable ignored) { }
        float weariness = 0f;
        try { weariness = DiplomacyManager.getWarWeariness(PlayerFactionStore.getPlayerFactionId()); } catch (Throwable ignored) { }
        if (CAStance.wantsWar(st)) return "reject recommended (" + CAStance.name(st) + "; War level Off or Defend leaves it to you)";
        if (ops > 0) return "reject recommended (" + ops + " operation(s) of yours under way against them)";
        return "accept recommended (" + CAStance.name(st) + ", no operations under way; your war weariness " + Math.round(weariness) + ")";
    }

    static String lower(String s) { return s == null || s.isEmpty() ? "offer" : s.substring(0, 1).toLowerCase(Locale.ROOT) + s.substring(1); }

    /** Alliance offers from a faction you marked Ally: one line each. Joining stays your call. */
    static void alliances(CASettings s) {
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(AllianceOfferIntel.class)) {
            if (!(i instanceof AllianceOfferIntel)) continue;
            AllianceOfferIntel a = (AllianceOfferIntel) i;
            if (a.isEnded() || a.isEnding() || a.getState() != 0) continue;
            FactionAPI f = a.getFactionForUIColors();
            if (f == null || s.stanceOf(f.getId()) != CAStance.ALLY) continue;
            Long at = a.getPlayerVisibleTimestamp();
            if (CALog.once("dip:alliance:" + f.getId() + ":" + (at == null ? 0L : at)))
                CALog.add(f.getDisplayName() + ": alliance offer waits for you in the intel; joining recommended (Ally). An alliance brings its wars.");
        }
    }

    /** Days since the last declaration of any war by Colony Manager (keep-hostile counts too); MAX_VALUE with none. */
    static float daysSinceLastDeclaration(CAData d) {
        float best = Float.MAX_VALUE;
        for (Long at : d.warDeclared().values()) {
            if (at == null) continue;
            float days = Global.getSector().getClock().getElapsedDaysSince(at);
            if (days >= 0f) best = Math.min(best, days);
        }
        return best;
    }

    /** Why war can't be declared on f now (CAWarPolicy.declareWait), or null. */
    static String declareWhy(CAData d, CASettings s, FactionAPI f) {
        if (f == null || f.isPlayerFaction()) return "not a faction you can be at war with";
        return CAWarPolicy.declareWait(s.stanceOf(f.getId()), f.isHostileTo(Factions.PLAYER), Misc.getCommissionFaction() != null,
                CALowProfile.on(), CAWar.allied(f) || CAWar.commissionerSide(f), CAWar.ceasefire(f), s.warLevel, daysSinceLastDeclaration(d));
    }

    /** Makes f hostile exactly as Nexerelin's fleet request does: no relation change beyond the war itself (CustomRepImpact, delta 0, at best Hostile). */
    static void makeHostile(FactionAPI f) {
        CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
        impact.delta = 0f;
        impact.ensureAtBest = RepLevel.HOSTILE;
        Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, impact, null, null, false), f.getId());
    }

    /**
     * The War page's confirmed "Declare war": makes f hostile (makeHostile), records the declaration (CAData.warDeclared) and
     * the confirmation (CAData.warAuthorized). Nexerelin then runs its alliance votes and your commissioner's reaction.
     * Returns null when declared, or why not.
     */
    public static String declareWar(FactionAPI f) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        String why = declareWhy(d, s, f);
        if (why != null) return why;
        makeHostile(f);
        d.warDeclared().put(f.getId(), Global.getSector().getClock().getTimestamp());
        d.warAuthorized().add(f.getId());
        CALog.add(f.getDisplayName() + ": war declared (" + CAStance.name(s.stanceOf(f.getId())) + ", War level " + CAWarPolicy.levelName(s.warLevel)
                + "). Alliances vote on joining.", true);
        return null;
    }

    /** Lines for the Declare war confirmation: relation, alliances that will vote, commission, the League, what follows. */
    public static List<String> preview(FactionAPI f) {
        List<String> out = new ArrayList<String>();
        CAData d = CAData.get();
        CASettings s = d.settings;
        float rel = Global.getSector().getPlayerFaction().getRelationship(f.getId());
        out.add("Relation now: " + f.getRelationshipLevel(Factions.PLAYER).getDisplayName() + " (" + Math.round(rel * 100f) + "). It becomes Hostile.");
        try {
            Alliance theirs = AllianceManager.getFactionAlliance(f.getId());
            out.add(theirs == null ? "It is in no alliance." : "Its alliance " + theirs.getName() + " votes on joining: " + names(theirs) + ".");
            Alliance ours = AllianceManager.getPlayerAlliance(false);
            if (ours != null) out.add("Your alliance " + ours.getName() + " votes on joining: " + names(ours) + ".");
        } catch (Throwable ignored) { }
        String why = declareWhy(d, s, f);
        if (why != null) out.add("Blocked: " + why + ".");
        int st = s.stanceOf(f.getId());
        int eff = Math.min(CAWarPolicy.clamp(s.warLevel), CAStance.cap(st));
        out.add("Then at War level " + CAWarPolicy.levelName(s.warLevel) + " (" + CAStance.name(st) + " allows " + CAWarPolicy.levelName(eff) + "): "
                + (eff >= CAWarPolicy.CONQUER ? "invasions, raids and blockades" : eff >= CAWarPolicy.HOLD ? "raids and blockades" : "strikes back only")
                + ", each at Nexerelin's price plus the fee, never below your credit reserve.");
        int markets = 0; MarketAPI near = null; float nearLY = Float.MAX_VALUE;
        for (MarketAPI m : Misc.getFactionMarkets(f)) {
            if (!CAWar.targetable(m)) continue;
            markets++;
            float ly = CAWar.nearestColonyLY(m);
            if (ly < nearLY) { nearLY = ly; near = m; }
        }
        if (near != null) out.add(markets + " market(s); nearest " + near.getName() + " at " + Math.round(nearLY) + " light years.");
        try {
            float badboy = DiplomacyManager.getBadboy(Global.getSector().getPlayerFaction());
            float dom = DiplomacyManager.getDominanceFactor(PlayerFactionStore.getPlayerFactionId());
            out.add("Infamy now " + Math.round(CAWarPolicy.infamy(badboy, dom)) + " of " + Math.round(CAWarPolicy.INFAMY_LINE) + "; each capture adds size squared x 2.");
        } catch (Throwable ignored) { }
        return out;
    }

    static String names(Alliance a) {
        List<String> n = new ArrayList<String>();
        for (String id : a.getMembersCopy()) {
            FactionAPI f = Global.getSector().getFaction(id);
            if (f != null) n.add(f.getDisplayName());
        }
        return CAContracts.join(n);
    }

    /**
     * Keep hostile: an Enemy or Destroy faction you declared war on whose relations rose above Hostile (random diplomacy
     * events, ceasefires after an elimination) is made hostile again, at most once in KEEP_HOSTILE_DAYS, from War level
     * Retaliate up. A faction no longer Enemy or Destroy loses its war confirmation.
     */
    static void keepHostile(CAData d, CASettings s, int level) {
        for (String fid : new ArrayList<String>(d.warAuthorized())) {
            FactionAPI f = Global.getSector().getFaction(fid);
            if (f == null) { d.warAuthorized().remove(fid); continue; }
            int st = s.stanceOf(fid);
            if (!CAStance.wantsWar(st)) {
                d.warAuthorized().remove(fid);
                CALog.add(f.getDisplayName() + ": war confirmation cleared (stance now " + CAStance.name(st) + ")");
                continue;
            }
            if (f.isHostileTo(Factions.PLAYER) || level < CAWarPolicy.RETALIATE) continue;
            if (Misc.getCommissionFaction() != null || CALowProfile.on() || CAWar.allied(f) || CAWar.commissionerSide(f) || CAWar.ceasefire(f)) continue;
            Long at = d.warDeclared().get(fid);
            float days = at == null ? Float.MAX_VALUE : Global.getSector().getClock().getElapsedDaysSince(at);
            if (days < 0f) days = Float.MAX_VALUE;
            if (!CAWarPolicy.keepHostileDue(days)) continue;
            float rel = Global.getSector().getPlayerFaction().getRelationship(fid);
            makeHostile(f);
            d.warDeclared().put(fid, Global.getSector().getClock().getTimestamp());
            CALog.add(f.getDisplayName() + ": made hostile again (" + CAStance.name(st) + "; relations had risen to " + Math.round(rel * 100f) + ")", true);
        }
    }
}
