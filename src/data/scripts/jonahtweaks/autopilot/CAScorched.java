package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FleetRequest;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.RevengeanceManager;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.satbomb.SatBombIntel;
import exerelin.utilities.NexConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 0.3.0.0 (WS-E, research 06 sections 2.5, 4.6, 4.7): saturation bombardment of a Destroy faction's capital (Nexerelin), at War
 * level Scorched and only once you authorize it on the War page (CASettings.warScorchedOK, cleared by Stand down). With
 * "Scorched earth targets: capitals and small colonies" also a Destroy market of size 4 or less (decivilized at once) when
 * invading it costs more than bombing it.
 *
 * The fleet is a player-faction SatBombIntel (Nexerelin has no fleet request type for it; this path is untested by Nexerelin
 * and needs the in-game test before release; the fallback is to drop it and invade capitals). Its size is 1.25 x
 * Nexerelin's wanted fleet and enough to carry twice the fuel the bombardment needs (SatBombActionStage only bombs when the
 * fleet has the fuel). Price: fleet points x 400 + the fuel at its base price + Auto Colonies' fee, from the surplus only,
 * never below the credit reserve. One under way at a time and 120 days between two; infamy stays under the coalition line.
 * 0.3.0.0 (fix): candidates are tried in order (capital first, then small markets), up to CAConquest.TRIES a day; a capital
 * whose bombardment landed in the last 365 days isn't bombarded again and is invaded first (06 section 4.7).
 *
 * Nexerelin's fleet path applies none of the reputation cost a bombardment in person has, so when Auto Colonies'
 * bombardment lands (CAWarListener, or the daily catchMissed when the listener missed it) applyAtrocity applies it, exactly
 * once: the victim -0.04 x size and at best Vengeful;
 * every faction that cares about atrocities (and isn't Vengeful toward the victim) -0.02 x size, at best Hostile (Neutral
 * under limited hatred, no cap for a Monstrous victim); the permanent cap on relations (Nexerelin's satbomb setting, twice
 * for the victim); the atrocity counters, which also bar the Persean League if it was the victim; the death toll for
 * revenge fleets. Nexerelin adds the infamy itself.
 */
public class CAScorched {
    /** Saved-record key of a bombardment that landed (the 120-day gap counts from it). */
    static final String DONE = "satbombed";

    /** A faction capital by its condition: Nexerelin's headquarters or a regional capital. */
    static boolean isCapital(MarketAPI m) {
        return m != null && (m.hasCondition("headquarters") || m.hasCondition(Conditions.REGIONAL_CAPITAL));
    }

    /** A faction's capital: its headquarters, else a regional capital, else its biggest market with High Command or a Military Base, else its biggest. */
    static MarketAPI capital(FactionAPI f) {
        MarketAPI hq = null, regional = null, military = null, biggest = null;
        for (MarketAPI m : Misc.getFactionMarkets(f)) {
            if (!CAWar.targetable(m)) continue;
            if (m.hasCondition("headquarters") && hq == null) hq = m;
            if (m.hasCondition(Conditions.REGIONAL_CAPITAL) && (regional == null || m.getSize() > regional.getSize())) regional = m;
            if ((m.hasIndustry(Industries.HIGHCOMMAND) || m.hasIndustry(Industries.MILITARYBASE)) && (military == null || m.getSize() > military.getSize())) military = m;
            if (biggest == null || m.getSize() > biggest.getSize()) biggest = m;
        }
        return hq != null ? hq : regional != null ? regional : military != null ? military : biggest;
    }

    /** Markets of this size or less are decivilized by a saturation bombardment (bombardSaturationDestroySize, 4). */
    static int destroySize() {
        try { return Global.getSettings().getInt("bombardSaturationDestroySize"); } catch (Throwable t) { return 4; }
    }

    /** Fuel the bombardment needs (MarketCMD.getBombardmentCost: ground defense, at least 2). */
    static float fuel(MarketAPI t) {
        try { return MarketCMD.getBombardmentCost(t, null); } catch (Throwable e) { return 2f; }
    }

    /** Limited hatred for this target (CAWarPolicy.limitedHatred, Nex_MarketCMD.checkForSatBombLimitedHatred). */
    static boolean limited(MarketAPI t) { return limited(t, t.getFaction(), t.getSize()); }

    /** 0.3.0.0 (fix): the same for a given owner and size (catchMissed: the victim and the size before the bombardment). */
    static boolean limited(MarketAPI t, FactionAPI f, int size) {
        boolean monstrous = false, bomber = false, expedition = false;
        try { monstrous = DiplomacyTraits.hasTrait(f.getId(), DiplomacyTraits.TraitIds.MONSTROUS); } catch (Throwable ignored) { }
        try { MemoryAPI mem = f.getMemoryWithoutUpdate(); bomber = mem.contains(SatBombIntel.FACTION_MEMORY_KEY) && mem.getInt(SatBombIntel.FACTION_MEMORY_KEY) > 0; } catch (Throwable ignored) { }
        try { expedition = t.getMemoryWithoutUpdate().getBoolean(exerelin.campaign.intel.colony.ColonyExpeditionIntel.MEMORY_KEY_COLONY); } catch (Throwable ignored) { }
        return CAWarPolicy.limitedHatred(t.isHidden(), monstrous, size, expedition, bomber);
    }

    /** One planned bombardment. */
    static final class Plan {
        MarketAPI t; float fp, fuel, cost, fee; String why;
    }

    static Plan plan(CASettings s, MarketAPI t, String why) {
        Plan p = new Plan();
        p.t = t; p.why = why;
        p.fuel = fuel(t);
        float wanted = 0f;
        try { wanted = InvasionFleetManager.getWantedFleetSize(Global.getSector().getPlayerFaction(), t, 0f, false); } catch (Throwable ignored) { }
        p.fp = Math.max(CAWar.MIN_FP, Math.round(CAWarPolicy.satBombFP(wanted, p.fuel)));
        p.cost = Math.round(CAWarPolicy.satBombCost(p.fp, NexConfig.fleetRequestCostPerFP, p.fuel, fuelPrice()));
        p.fee = CAWar.fee(s, CAWarPolicy.SATBOMB, p.cost);
        return p;
    }

    static float fuelPrice() {
        try { return Global.getSettings().getCommoditySpec(com.fs.starfarer.api.impl.campaign.ids.Commodities.FUEL).getBasePrice(); }
        catch (Throwable t) { return CAWarPolicy.FUEL_PRICE; }
    }

    /** Days since the last of Auto Colonies' bombardments landed (MAX_VALUE with none in the record). */
    static float daysSinceLast(CAData d) {
        float best = Float.MAX_VALUE;
        for (Map.Entry<String, Long> e : d.warOps().entrySet()) {
            if (!e.getKey().startsWith(DONE + ":")) continue;
            best = Math.min(best, CAWar.daysSince(d, e.getKey()));
        }
        return best;
    }

    /**
     * 0.3.0.0 (fix): Auto Colonies' bombardment of this market has landed: onBombed replaced the launch record ("satbomb:<id>")
     * with the landed one ("satbombed:<id>"). A new launch at the same market writes the launch record again.
     */
    static boolean landed(CAData d, MarketAPI t) {
        if (d == null || t == null) return false;
        return !d.warOps().containsKey(CAWarPolicy.SATBOMB + ":" + t.getId()) && d.warOps().containsKey(DONE + ":" + t.getId());
    }

    /**
     * 0.3.0.0 (fix, 06 section 4.7): a capital whose bombardment landed within CAWarPolicy.BOMBED_DAYS: not bombarded again, and
     * invaded first (CAConquest.targets) while its industries are disrupted.
     */
    static boolean bombedRecently(CAData d, MarketAPI m) {
        if (d == null || m == null) return false;
        return CAWarPolicy.bombedRecently(CAWar.daysSince(d, DONE + ":" + m.getId()));
    }

    /** Market size the sector loses to one bombardment: the whole market at the destroy size or less, else one size. */
    static int loss(MarketAPI t) { return t.getSize() <= destroySize() ? t.getSize() : 1; }

    /** Market size your bombardments under way would take from the sector. 0.3.0.0 (fix): only those still to land (CAWar.undecided). */
    static int pendingLoss() {
        int n = 0;
        for (exerelin.campaign.intel.fleets.OffensiveFleetIntel o : CAWar.offensives())
            if (o instanceof SatBombIntel && CAWar.yours(o) && o.getTarget() != null && CAWar.undecided(o)) n += loss(o.getTarget());
        return n;
    }

    /** Infamy the bombardments of yours under way will add. 0.3.0.0 (fix): only those still to land (CAWar.undecided). */
    static float pendingInfamy() {
        float add = 0f;
        for (exerelin.campaign.intel.fleets.OffensiveFleetIntel o : CAWar.offensives())
            if (o instanceof SatBombIntel && CAWar.yours(o) && o.getTarget() != null && CAWar.undecided(o))
                add += CAWarPolicy.bombInfamy(o.getTarget().getSize(), limited(o.getTarget()));
        return add;
    }

    /** 9. Scorched: a Destroy faction's capital (and small markets, with that setting) when authorized; returns whether one went. */
    static boolean step(CAWar.Pass p) {
        CAWarListener.ensure();   // 0.3.0.0 (fix): the listener applies a landed bombardment's cost; it is gone after a load until added again
        List<Plan> cands = new ArrayList<Plan>();
        boolean any = false;
        List<FactionAPI> seen = new ArrayList<FactionAPI>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            FactionAPI f = m.getFaction();
            if (f == null || f.isPlayerFaction() || seen.contains(f)) continue;
            seen.add(f);
            if (p.stance(f) < CAStance.DESTROY || !p.allows(CAWarPolicy.SATBOMB, f)) continue;
            any = true;
            MarketAPI cap = capital(f);
            // 0.3.0.0 (fix, 06 section 4.7): a capital bombarded in the last BOMBED_DAYS isn't bombarded again; it's invaded
            // first while its industries are disrupted (CAConquest.targets)
            if (cap != null && !CAWar.oursAt(cap) && !bombedRecently(p.d, cap)) cands.add(plan(p.s, cap, "Destroy: " + f.getDisplayName() + "'s capital"));
            if (p.s.scorchedTargets == 1) {
                for (MarketAPI sm : Misc.getFactionMarkets(f)) {
                    if (sm == cap || !CAWar.targetable(sm) || sm.getSize() > destroySize() || CAWar.oursAt(sm)) continue;
                    Plan bomb = plan(p.s, sm, "Destroy: " + f.getDisplayName() + ", size " + sm.getSize());
                    CAConquest.Plan inv = CAConquest.plan(p.s, sm, CAWarPolicy.INVASION);
                    if (inv.cost + inv.fee > bomb.cost + bomb.fee) cands.add(bomb);   // only where invading costs more
                }
            }
        }
        if (cands.isEmpty()) return false;
        if (!p.s.warScorchedOK) {
            if (any) p.waiting.add("bombardment (authorize it on the War page)");
            return false;
        }
        // 0.3.0.0 (fix): candidates in order, up to CAConquest.TRIES, as invasions do: a capital whose infamy waits doesn't hold
        // back a small market under limited hatred; one wait line (the first one's)
        int[] sz = CAConquest.sizes();
        int underWay = CAWar.bombsUnderWay(), gain = CAConquest.pendingGain(), lost = pendingLoss();
        float since = daysSinceLast(p.d), badboy = CAConquest.badboy(), dom0 = CAConquest.dominance();
        float pending = pendingInfamy() + CAConquest.pendingInfamy();
        String first = null;
        for (int i = 0; i < cands.size() && i < CAConquest.TRIES; i++) {
            Plan pl = cands.get(i);
            String payer = CAWar.payerFor(p.s, CAWarPolicy.SATBOMB, p.rich, pl.cost + pl.fee);
            // dominance once everything under way lands and the sector loses what this bombardment takes
            float dom = CAWarPolicy.projectedDominance(dom0, sz[0], gain, sz[1], lost + loss(pl.t));
            String wait = CAWarPolicy.bombWait(true, underWay, since, badboy, dom, pending, CAWarPolicy.bombInfamy(pl.t.getSize(), limited(pl.t)),
                    p.s.warCoalitionOK, p.spare(), pl.fp, payer);
            if (wait == null) wait = launch(p, pl, payer);
            if (wait == null) return true;
            if (first == null) first = "bombardment of " + pl.t.getName() + " (" + wait + ")";
        }
        if (first != null) p.waiting.add(first);
        return false;
    }

    /** Launches the bombardment fleet (SatBombIntel, organized like a raid); returns null when it went, or why not. */
    static String launch(CAWar.Pass p, Plan pl, String payer) {
        MarketAPI t = pl.t;
        MarketAPI from = CAWar.invasionSource(t);
        if (from == null) return "no colony with a working spaceport";
        SatBombIntel intel = new SatBombIntel(Global.getSector().getPlayerFaction(), from, t, pl.fp,
                Nex_FleetRequest.getTimeToLaunch(pl.fp, Nex_FleetRequest.FleetType.RAID));
        intel.setPlayerSpawned(true);
        intel.init();
        if (intel.isEnded() || intel.isEnding()) return "no route there";
        // 0.3.0.0 (fix): the fee only after init went through. init ends at once with no jump point to the target, and an
        // ending intel refunds its player fee (OffensiveFleetIntel.refundPlayerFeeIfNeeded): credits never paid
        intel.setPlayerFee((int) pl.cost);
        CAWar.pay(p.s, payer, pl.cost, pl.fee);
        p.mgr.modifyFleetRequestStock(-(int) pl.fp);
        CAWar.record(p.d, CAWarPolicy.SATBOMB + ":" + t.getId());
        CALog.did(t.getName() + ": " + (int) pl.fp + "-pt saturation bombardment fleet from " + from.getName() + ", " + Math.round(pl.fuel) + " fuel, "
                + CAWar.money(pl.cost, pl.fee) + " (" + pl.why + ").", true);
        return null;
    }

    /**
     * From CAWarListener: a saturation bombardment by an NPC fleet landed. Auto Colonies' own (attacker the player faction
     * and a bombardment of this market in the record) gets the in-person consequences, once: the record is replaced by the
     * "landed" one before anything is applied.
     */
    static void onBombed(MarketAPI m, MarketCMD.TempData data) {
        if (m == null || !(data instanceof Nex_MarketCMD.NexTempData) || CALog.whatIf()) return;
        Nex_MarketCMD.NexTempData nd = (Nex_MarketCMD.NexTempData) data;
        if (nd.attackerFaction == null || !nd.attackerFaction.isPlayerFaction()) return;
        FactionAPI victim = nd.targetFaction != null ? nd.targetFaction : m.getFaction();
        int size = nd.sizeBeforeBombardment > 0 ? nd.sizeBeforeBombardment : m.getSize();
        landedNow(CAData.get(), m, victim, size, nd.satBombLimitedHatred);
    }

    /**
     * 0.3.0.0 (fix): records a bombardment of Auto Colonies' as landed and applies its cost, once: the launch record
     * ("satbomb:<id>") is replaced by the landed one ("satbombed:<id>") before anything is applied. False when there is no
     * launch record from the last year (not Auto Colonies', or applied already). Used by onBombed and catchMissed.
     */
    static boolean landedNow(CAData d, MarketAPI m, FactionAPI victim, int size, boolean limited) {
        String key = CAWarPolicy.SATBOMB + ":" + m.getId();
        if (CAWar.daysSince(d, key) > CAWarPolicy.OP_MEMORY_DAYS) return false;
        d.warOps().remove(key);
        CAWar.record(d, DONE + ":" + m.getId());
        applyAtrocity(m, victim, size, limited);
        return true;
    }

    /**
     * 0.3.0.0 (fix): daily (CAConquest.tend, at any War level), the bombardments the listener missed: it is transient, so after a
     * load it is gone until CAWarListener.ensure runs again, and the war fleet step that also adds it can be stopped by its
     * breaker. A SatBombIntel of Auto Colonies' whose outcome is SUCCESS (Nexerelin sets it when the bombardment lands; the
     * intel stays in the intel manager while the fleet flies home) at a market that still has its launch record gets the
     * landed path once (landedNow). The size before is read back (the market's size when it was decivilized, else one more)
     * and limited hatred is worked out again (limited). Skipped while a newer bombardment of yours is still to land there.
     */
    static void catchMissed(CAData d) {
        if (CALog.whatIf()) return;
        List<SatBombIntel> landed = new ArrayList<SatBombIntel>();
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(SatBombIntel.class)) {
            if (!(i instanceof SatBombIntel)) continue;
            SatBombIntel o = (SatBombIntel) i;
            if (o.getTarget() == null || o.getOutcome() != OffensiveFleetIntel.OffensiveOutcome.SUCCESS || !CAWar.yours(o) || !CAWar.cmOp(d, o)) continue;
            landed.add(o);
        }
        for (SatBombIntel o : landed) {
            MarketAPI t = o.getTarget();
            try {
                if (newerUnderWay(t)) continue;
                FactionAPI victim = o.getTargetFaction() != null ? o.getTargetFaction() : t.getFaction();
                boolean deciv = !t.isInEconomy() || t.hasCondition(Conditions.DECIVILIZED);
                int size = deciv ? t.getSize() : t.getSize() + 1;
                if (landedNow(d, t, victim, size, limited(t, victim, size)))
                    CADiagnostics.LOG.info(CADiagnostics.TAG + "bombardment of " + t.getName() + " applied by the daily scan (the war listener missed it)");
            } catch (Throwable e) { CAStep.warn("conquered colonies step", "missed bombardment of " + t.getName() + " failed", e); }
        }
    }

    /** A bombardment of yours at this market that hasn't landed yet (catchMissed leaves the launch record to it). */
    static boolean newerUnderWay(MarketAPI t) {
        for (OffensiveFleetIntel o : CAWar.offensives())
            if (o instanceof SatBombIntel && o.getTarget() == t && CAWar.yours(o) && o.getOutcome() == null) return true;
        return false;
    }

    static RepLevel level(float code) {
        int c = Math.round(code);
        return c == CAWarPolicy.AT_VENGEFUL ? RepLevel.VENGEFUL : c == CAWarPolicy.AT_HOSTILE ? RepLevel.HOSTILE : c == CAWarPolicy.AT_NEUTRAL ? RepLevel.NEUTRAL : null;
    }

    /** What a saturation bombardment in person costs you (Nex_MarketCMD.bombardSaturation and bombardConfirm, MiscEventsManager), applied once. */
    static void applyAtrocity(MarketAPI m, FactionAPI victim, int size, boolean limited) {
        if (victim == null) return;
        boolean ignore = false;
        try { ignore = Global.getSettings().getBoolean("nex_ignoreSatBomb"); } catch (Throwable ignored) { }
        boolean hidden = m.isHidden();
        List<FactionAPI> hit = new ArrayList<FactionAPI>();
        hit.add(victim);
        if (!hidden && !ignore) {
            for (FactionAPI f : Global.getSector().getAllFactions()) {
                if (f == victim || f.isPlayerFaction() || f.isNeutralFaction()) continue;
                if (f.getCustomBoolean(Factions.CUSTOM_CARES_ABOUT_ATROCITIES) && f.getRelationshipLevel(victim) != RepLevel.VENGEFUL) hit.add(f);
            }
        }
        boolean monstrous = false;
        try { monstrous = DiplomacyTraits.hasTrait(victim.getId(), DiplomacyTraits.TraitIds.MONSTROUS); } catch (Throwable ignored) { }
        String playerFid = Factions.PLAYER;
        try { playerFid = PlayerFactionStore.getPlayerFactionId(); } catch (Throwable ignored) { }
        boolean perma = !hidden && !ignore && !limited && NexConfig.permaHateFromPlayerSatBomb > 0f;
        List<String> others = new ArrayList<String>();
        for (FactionAPI f : hit) {
            boolean isVictim = f == victim;
            float[] imp = CAWarPolicy.atrocityImpact(size, isVictim, limited, monstrous);
            CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
            impact.delta = imp[0];
            impact.ensureAtBest = level(imp[1]);
            try {
                DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(f.getId());
                if (brain != null) brain.reportDiplomacyEvent(playerFid, imp[0]);
            } catch (Throwable ignored) { }
            try {
                Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, impact, null, null, true, true), f.getId());
            } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "bombardment reputation failed for " + f.getId(), t); }
            if (perma) {
                try { DiplomacyManager.getManager().modifyMaxRelationshipMod("satbomb", CAWarPolicy.permaHate(NexConfig.permaHateFromPlayerSatBomb, isVictim, false),
                        f.getId(), Factions.PLAYER, "Saturation bombardment"); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "bombardment relation cap failed for " + f.getId(), t); }
            }
            if (!isVictim) others.add(f.getDisplayName());
        }
        try {
            MemoryAPI cm = Global.getSector().getCharacterData().getMemoryWithoutUpdate();
            cm.set(MemFlags.PLAYER_ATROCITIES, (int) cm.getFloat(MemFlags.PLAYER_ATROCITIES) + 1);
            MemoryAPI vm = victim.getMemoryWithoutUpdate();
            vm.set(MemFlags.FACTION_SATURATION_BOMBARED_BY_PLAYER, vm.getInt(MemFlags.FACTION_SATURATION_BOMBARED_BY_PLAYER) + 1);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "atrocity counters failed", t); }
        if (perma) {
            try { RevengeanceManager.getManager().updateSatBombDeathToll(victim.getId(), size); } catch (Throwable ignored) { }
        }
        String cap = limited ? "at best Neutral" : monstrous ? "no cap" : "at best Hostile";
        CALog.did(m.getName() + ": saturation bombardment landed. " + victim.getDisplayName() + " Vengeful" + (others.isEmpty() ? "" : ". "
                + CAContracts.join(others) + " " + cap) + (perma ? ". Permanent -" + Math.round(NexConfig.permaHateFromPlayerSatBomb * 100f) + " on relations" : "")
                + ". Nexerelin adds the infamy.", true);
    }

    /** Lines for the authorization confirmation: what each bombardment costs you. */
    static List<String> preview() {
        List<String> out = new ArrayList<String>();
        out.add("At War level Scorched, Auto Colonies sends a bombardment fleet at each Destroy faction's capital"
                + (CAData.settings().scorchedTargets == 1 ? ", and at its markets of size " + destroySize() + " or less where invading costs more" : "") + ".");
        out.add("Size " + destroySize() + " or less: the colony is decivilized. Bigger: it loses one size, every industry is disrupted for a year or more, stability -10.");
        out.add("Each one costs you as in person: the victim -0.04 x size and Vengeful; factions that care about atrocities -0.02 x size and Hostile.");
        out.add("A permanent cap on relations and infamy of size squared x 4. Bombing the Persean League bars membership.");
        List<String> care = new ArrayList<String>();
        for (FactionAPI f : Global.getSector().getAllFactions())
            if (!f.isPlayerFaction() && f.isShowInIntelTab() && f.getCustomBoolean(Factions.CUSTOM_CARES_ABOUT_ATROCITIES)) care.add(f.getDisplayName());
        if (!care.isEmpty()) out.add("Factions that care about atrocities: " + CAContracts.join(care) + ".");
        out.add("Paid from the surplus only, one at a time, " + Math.round(CAWarPolicy.BOMB_GAP_DAYS) + " days apart. Stand down clears this authorization.");
        return out;
    }
}
