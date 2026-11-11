package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Industries that stopped paying (0.2.44.1). Daily, at every Auto colony not under siege: an industry whose whole worth is
 * what it makes, read live and valued the way a build is (CAMarket: only what's above the colony's own biggest other
 * source of each good, what it adds to your share of each sector market, what your colonies would be short of without
 * it), worth less than nothing after upkeep for 30 days in a row, is taken down; one per colony every 30 days. Godecifica's
 * and Zenemosrae's Roider Dives mined less ore and organics than the Mining already there, so they only cost upkeep.
 *
 * Never population, a station, military, improved, locked, being built or upgraded, one that adds stability, a plan step or
 * anything below one in its upgrade chain (the capital's Union HQ needs its Dives), a contract feeder, one with defense,
 * fleet or ship output, one the engine can't value, one that earns some other way (salvage, income), nor one that makes
 * nothing. Not in the endgame economy, which replaces weak industries itself. Since a build is valued the same way, what
 * goes doesn't come back. Its AI core and item go to the colony's Autopilot Storage. Setting: Take down industries that
 * stopped paying (Building and planning). 0.3.0.0: every takedown goes through CAStability.canLose too.
 *
 * 0.3.0.0 (WS-B4, research 02 sections 6 and 8, 03 F12): removal is the build math backwards.
 * - Worth is CAValue.keep, the same numbers a build is scored with (it used to be a third way of its own, so a building
 *   could be built for a shortage this step didn't see and taken down 30 days later).
 * - It goes when keep x H + the teardown loss is under 0 (CATombstone.removeGain; H the payback horizon, the loss the part
 *   of what Autopilot paid for it that it hasn't paid back yet), so a building put up a few months ago isn't taken down
 *   because the numbers wobbled.
 * - One built under another valuation (spending freely, the endgame economy) isn't judged under this one for a year
 *   (CATombstone.built); its 30-day clock starts again, as it does at a colony in the endgame (02 section 8: the clock kept
 *   running through endgame days, so a building could go the first day the endgame stopped).
 * - Every takedown leaves a year's mark (CATombstone), so the planner doesn't build it back.
 * - The weekly tidy of the marks and the sunk-cost records runs from here, whatever the setting.
 * 0.3.0.0 (fix, B4 review): never more eager than 0.2.45.1. The keep is read as a remover reads it (CATombstone.forRemoval): the
 * engine's charge for inputs your colonies lack doesn't count against a building that's there (0.2.45.1 never counted it; with
 * it every size-6 Mining read -36,000 a month while any colony was short of drugs, and came down), and the shortage it would
 * leave is read against today's supply, not counting builds under way (a producer read as relieving nothing the day a
 * replacement was started). And while a build under way here will out-make one of its lines, its 30-day clock waits.
 * 0.3.0.0 (fix, B4 review): and the relief of each line is never under 0.2.45.1's reading of it (CATombstone.reliefFloor: your
 * colonies' summed demand against their summed supply, at the full rate, legal or not). Read per colony, a second Farming
 * whose export didn't cover its upkeep relieved nothing once each colony was covered, and was pruned and blocked for a
 * year, though 0.2.45.1 kept it for the empire-wide shortfall it covered.
 */
public class CAPrune {
    static final float BELOW_DAYS = CATuning.PRUNE_BELOW_DAYS, GAP_DAYS = CATuning.PRUNE_GAP_DAYS, EVERY_DAYS = CATuning.PRUNE_EVERY_DAYS;   // 0.3.0.0 (A3): in CATuning
    /** Last look per colony; every few days is plenty for a 30-day rule, and a look reads the colony's whole plan. */
    static final Map<String, Float> looked = new HashMap<String, Float>();

    public static void run(List<MarketAPI> auto) {
        CAData d = CAData.get();
        // 0.3.0.0 (B4): removal marks past their year and cost records of buildings that are gone, weekly, pruning on or off
        try { CATombstone.weekly(d); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "removal mark tidy failed", t); }
        CASettings s = CAData.settings();
        if (s.noPrune) return;
        float now = CADay.now();
        for (MarketAPI m : auto) {
            if (CAEngine.isOutpost(m) || CAHypershunt.is(m)) continue;
            Float last = looked.get(m.getId());
            if (last != null && now - last < EVERY_DAYS && now >= last) continue;
            looked.put(m.getId(), now);
            try { check(d, m); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "industry pruning check failed at " + m.getName(), t); }
        }
    }

    static float days(long since) { return Global.getSector().getClock().getElapsedDaysSince(since); }

    /** One industry as this step judges it: CAValue's reading (0.3.0.0, B4), its build record and what taking it down gains. */
    static class Keep {
        Industry ind;
        CAValue.Kept kept;
        CATombstone.Built built;
        /** CAValue.keep (a month, at the colony's weights), and the horizon it was judged over. */
        float value, h;
        /** What taking it down gains over the horizon (CATombstone.removeGain): positive means it should go. */
        float gain;
    }

    static void check(CAData d, MarketAPI m) {
        String pre = m.getId() + ":useless:";
        if (CAEndgame.at(m)) {   // the endgame replaces weak industries itself; 0.3.0.0 (B4): and the clocks start again after it (02 section 8)
            clear(d, pre, null);
            return;
        }
        CAData.Colony col = d.colony(m.getId());
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        CAData.Profile prof = CAThreat.effective(m, col.profile);
        float h = CAHorizon.months();
        String val = CATombstone.valuation(m);
        long now = Global.getSector().getClock().getTimestamp();
        Set<String> seen = new HashSet<String>();
        Keep worst = null;
        for (Industry ind : m.getIndustries()) {
            String key = pre + ind.getId();
            seen.add(key);
            Keep k = keep(m, ind, col, plan, deficit, prof, h, val);
            if (k == null || k.gain <= 0f) { d.pruneAt().remove(key); continue; }
            // 0.3.0.0 (fix): a build under way here will out-make one of its lines, so it reads as a duplicate before the new
            // building makes anything: the clock waits for that one to finish (0.2.45.1 read only what's there)
            if (CATombstone.outmadeSoon(m, ind)) { d.pruneAt().remove(key); continue; }
            Long since = d.pruneAt().get(key);
            if (since == null) { d.pruneAt().put(key, now); continue; }
            if (days(since) < BELOW_DAYS) continue;
            if (worst == null || k.gain > worst.gain) worst = k;
        }
        clear(d, pre, seen);
        if (worst == null) return;
        Long last = d.pruneAt().get(m.getId() + ":pruned");
        if (last != null && days(last) < GAP_DAYS) return;
        takeDown(d, m, worst);
    }

    /** Drops this colony's "below since" stamps, all of them (seen null) or those of buildings no longer there. */
    static void clear(CAData d, String pre, Set<String> seen) {
        for (Iterator<String> it = d.pruneAt().keySet().iterator(); it.hasNext(); ) {
            String k = it.next();
            if (k.startsWith(pre) && (seen == null || !seen.contains(k))) it.remove();
        }
    }

    /**
     * What keeping this industry is worth, or null when it isn't one this step judges. 0.3.0.0 (B4): CAValue.keepDetail at the
     * colony's goal (threat counted), the teardown loss from what Autopilot paid for it, and never one built under another
     * valuation within the year.
     */
    static Keep keep(MarketAPI m, Industry ind, CAData.Colony col, CAPlan.Plan plan, Map<String, Integer> deficit, CAData.Profile prof,
                     float h, String valuation) {
        String id = ind.getId();
        if (!ind.isIndustry() || ind.isHidden() || ind.isBuilding() || ind.isUpgrading() || ind.isImproved() || !ind.canShutDown()) return null;
        IndustrySpecAPI spec = ind.getSpec();
        if (spec.hasTag("population") || spec.hasTag("station") || spec.hasTag("military") || spec.hasTag("patrol") || spec.hasTag("command")
                || col.locks.contains(id) || CAMods.leftToYou(id) != null) return null;   // left to you: never taken down (0.2.45.0)
        if (CAEndgame.keepsPlanBonus(m, plan, id) || CAContracts.feeds(ind) != null) return null;
        if (plan != null) for (CAPlan.Step st : plan.steps) if (id.equals(st.id) || CAPlan.inChainBelow(id, st.id)) return null;
        if (CAStability.of(m, ind) > 0f) return null;   // never one that adds stability (0.2.44.1)
        // 0.3.0.0: and never one the colony can't lose (CAStability, the one rule for every takedown)
        if (!CAStability.canLose(m, CAStability.takeDownDelta(m, ind), null)) return null;
        // 0.3.0.0 (B4): built under another valuation within the year (spending freely, the endgame): not judged under this one
        CATombstone.Built b = CATombstone.built(m, id, h, valuation);
        if (b.protect != null) return null;
        // 0.3.0.0 (fix): as a remover reads it: no charge for inputs your colonies lack, relief against today's supply; and
        // (B4 review) never less relief on a line than 0.2.45.1's empire-wide reading, so 0.2.45.1 keeps what it kept
        CAValue.Kept kv = CATombstone.forRemoval(m, CAValue.keepDetail(m, ind, prof, deficit), prof, true, false);
        if (kv == null || kv.p == null) return null;   // a building the engine can't value
        CAEngine.Pick p = kv.p;
        if (p.fx[3] > 0f || p.fx[4] > 0f || p.fx[5] > 0f || p.supplied.containsKey("ships")) return null;
        if (kv.other > 1f || kv.estimate > 1f) return null;   // earns some other way (salvage, income) the market can't see; 0.3.0.0 (B1 fix): or an IndEvo producer once valued by its estimate, never judged here (as 0.2.45.1)
        if (kv.disrupted) return null;   // 0.3.0.0 (B1 fix): raided or bombarded: judged once it recovers (0.2.45.1 read no live lines then)
        if (kv.lines == 0) return null;   // makes nothing: whatever it's worth is somewhere this step can't see
        Keep k = new Keep();
        k.ind = ind; k.kept = kv; k.built = b; k.h = h;
        k.value = kv.value;
        k.gain = removeGain(kv.value, h, b.loss);
        return k;
    }

    /**
     * Pure (0.3.0.0, B4; JTTombstoneCheck): what taking a building down gains over the horizon, CATombstone.removeGain; NaN
     * or an unknown keep never gains anything.
     */
    static float removeGain(float keep, float h, float loss) {
        if (!CAValue.known(keep)) return 0f;
        return CATombstone.removeGain(keep, h, loss);
    }

    /** Units your colonies would be short of if a source of added units went: the shortfall it leaves, less any there already. */
    static int coverIfRemoved(int demand, int supply, int added) {
        if (added <= 0) return 0;
        return Math.max(0, demand - (supply - added)) - Math.max(0, demand - supply);
    }

    static void takeDown(CAData d, MarketAPI m, Keep k) {
        Industry ind = k.ind;
        String core = ind.getAICoreId(), name = ind.getCurrentName(), id = ind.getId();
        // 0.3.0.0: checked again at the moment it goes, with a log line if the one stability rule holds it
        if (!CAStability.canLose(m, CAStability.takeDownDelta(m, ind), "taking down " + name)) return;
        SpecialItemData item = ind.getSpecialItem();
        CAStores.emptyInto(m, ind);   // core and item to this colony's Autopilot Storage
        m.removeIndustry(id, MarketAPI.MarketInteractionMode.REMOTE, false);
        long ts = Global.getSector().getClock().getTimestamp();
        d.pruneAt().put(m.getId() + ":pruned", ts);
        d.pruneAt().remove(m.getId() + ":useless:" + id);
        // 0.3.0.0 (B4): a year's mark, so the planner doesn't build it back here (and what it cost goes with it)
        CATombstone.add(m.getId(), id, CATombstone.PRUNE, k.built == null ? 0f : k.built.paid);
        CAMarket.invalidate();
        CALog.add(m.getName() + ": took down " + name + ": " + why(k) + CATombstone.paidText(k.built)
                + (core != null || item != null ? "; its " + (core != null && item != null ? "core and item" : core != null ? "core" : "item") + " to Autopilot Storage" : ""));
    }

    /** The log's reason: "Mining already makes more ore, so it only cost upkeep (about 9,000 a month)", or what it added against its upkeep. */
    static String why(Keep k) {
        CAValue.Kept kv = k.kept;
        if (kv == null) return "it was worth " + CAEngine.valueText(k.value);
        if (kv.allDup && !kv.bigger.isEmpty() && !kv.goods.isEmpty())
            return CAContracts.join(new ArrayList<String>(kv.bigger)) + " already makes more " + CAContracts.join(new ArrayList<String>(kv.goods))
                    + ", so it only cost upkeep (about " + Misc.getDGSCredits(kv.upkeep) + " a month)";
        return "for a month it added about " + Misc.getDGSCredits(Math.max(0f, kv.export + kv.relief)) + " a month against "
                + Misc.getDGSCredits(kv.upkeep) + " upkeep";
    }
}
