package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Colonies over their industry limit (0.2.42.1), and colonies at 0 stability (0.2.42.2).
 *
 * More industries than slots costs a flat 5 stability (vanilla PopulationAndInfrastructure, "Maximum number of industries
 * exceeded"), and below 5 stability every point cuts the colony's income by a fifth. Worse, the game decivilizes a colony
 * that sits at 0 stability (DecivTracker: about monthly samples over 16 months, at least half of them 0 including the first
 * and the latest 3 in a row; it warns once, then 10% a month per month of streak past 3). Wind was decivilized that way in
 * 0.2.42.0: 3 industries on 2 slots (two picks of one pass took one free slot, fixed in CABuild.start) kept it at 0 for months.
 *
 * Daily, at every Auto colony (sieges included: this is an emergency, not a build):
 * - over the limit at 0 stability or below: its weakest industry Autopilot may take down goes at once, whatever it earns;
 * - over the limit otherwise: unless the colony is about to grow a size with room for them all, its weakest industry goes,
 *   one a day until it's back within the limit (0.2.44.1: it used to keep them when the weakest earned more than the 5
 *   stability was worth, or when none was one it could value, so colonies Autopilot itself had put over sat there for months);
 * - at 0 stability, an important alert on screen (once a month a colony), Auto or not, with what to do.
 * Never a building being built or upgraded, improved, locked, a station, population, or one holding up 5 or more stability;
 * a plan's special step, a capital trade building, a contract feeder, or one with defense, fleet, ship quality or ship output
 * only when nothing else can go, and one that adds stability last. Its AI core and item go to the colony's Autopilot
 * Storage. Setting: Fix colonies over the industry limit (Building and planning).
 *
 * 0.3.0.0: slots are counted one way everywhere (CABuild.usedSlots, research 07 M12: upgrades into industries under way count,
 * the way the build gate counts them), never one the colony can't lose (CAStability: taking it down would leave the colony
 * under the floor), and the first day a colony goes over, the log says why (07 M4: the 0.2.44.1 log had Zenemosrae at "4
 * industries on 3 slots ... left it at 0" and nothing about how it got there).
 * 0.3.0.0 (WS-B4): the weakest is ranked by CAValue.keep plus the teardown loss (what Autopilot paid for it and hasn't paid
 * back yet over the horizon), and every takedown here, shedding at 0 included, leaves a year's mark (CATombstone) so the
 * planner doesn't build it back. Neither step ever waits for a mark: they keep their own rules.
 * 0.3.0.0 (fix, B4 review): the over-limit check, 0-stability shedding and the alert run in their own try at each colony, so a
 * failure in the ranking never skips the 0-stability safety net, and a building the ranking can't read only drops out of it;
 * the ranking reads each one as a remover does (CATombstone.forRemoval: no charge for inputs your colonies lack, relief
 * against today's supply).
 */
public class CAOverLimit {
    // 0.3.0.0 (A3): CM's own numbers live in CATuning; PENALTY is the game's over-limit stability penalty
    static final float PENALTY = 5f, GAP_DAYS = CATuning.OVER_LIMIT_GAP_DAYS, GROWS_SOON = CATuning.OVER_LIMIT_GROWS_SOON, ALERT_DAYS = CATuning.OVER_LIMIT_ALERT_DAYS;
    /** At or under this stability a colony over its industry limit sheds an industry at once, whatever it earns. */
    static final float LOW = CATuning.OVER_LIMIT_LOW;
    /** Days between two stability-costing buildings taken down at a colony at 0, and how long one stays off the capital's plan. */
    static final float SHED_DAYS = CATuning.OVER_LIMIT_SHED_DAYS, SHED_HOLD_DAYS = CATuning.OVER_LIMIT_SHED_HOLD_DAYS;
    /** Days at 0 before a stability-costing building is taken down (the game's decivilization needs months at 0). */
    static final float ZERO_WAIT_DAYS = CATuning.OVER_LIMIT_ZERO_WAIT_DAYS;
    /** 0.3.0.9: days at LOW_STABILITY or below count toward ZERO_WAIT_DAYS; a day above it takes LOW_DECAY days off. */
    static final float LOW_STABILITY = CATuning.OVER_LIMIT_LOW_STABILITY, LOW_DECAY = CATuning.OVER_LIMIT_LOW_DECAY;
    static final long DAY_MS = 86_400_000L;

    /** Every colony: Auto ones are fixed, the rest get the alert. */
    public static void run(List<MarketAPI> auto) {
        CASettings s = CAData.settings();
        CAData d = CAData.get();
        Set<String> autoIds = new HashSet<String>();
        for (MarketAPI m : auto) autoIds.add(m.getId());
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (CAEngine.isOutpost(m)) continue;
            // 0.3.0.0 (fix, B4 review): the over-limit check, 0-stability shedding and the alert each in their own try, so a
            // failure in the (much bigger since B4) over-limit ranking never skips the 0-stability safety net
            boolean acted = false;
            // 0.3.0.9: the low-stability day count first, so the shedding below reads today's count (a colony back from a
            // capture is never counted low for the days it was held)
            try { countLow(d, m); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "low-stability day count failed at " + m.getName(), t); }
            if (autoIds.contains(m.getId())) {
                int had = m.getIndustries().size();
                try { acted = check(d, s, m); }
                catch (Throwable t) {
                    acted = m.getIndustries().size() != had;   // it took one down before failing: no second one today
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "industry limit check failed at " + m.getName(), t);
                }
                try { if (!acted && atZero(m)) shed(d, m); }   // still at 0: a building that costs stability goes
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "0-stability shedding failed at " + m.getName(), t); }
            }
            try { zeroAlert(d, m, autoIds.contains(m.getId())); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "0-stability alert failed at " + m.getName(), t); }
            try { remember(m); } catch (Throwable ignored) { }
        }
    }

    static boolean atZero(MarketAPI m) { return m.getStabilityValue() <= 0f; }

    /**
     * 0.3.0.9: the ":zerosince" stamp holds the low-stability day count as a start time (now less the count), so the day count
     * is the days since it. ":lowseen" is when the count was last moved. A day off 0 keeps the count (lowDays). An older save's
     * stamp reads as a count of the days since it.
     */
    static void countLow(CAData d, MarketAPI m) {
        com.fs.starfarer.api.campaign.CampaignClockAPI clock = Global.getSector().getClock();
        String sinceKey = m.getId() + ":zerosince", seenKey = m.getId() + ":lowseen";
        Long since = d.overLimitAt().get(sinceKey), seen = d.overLimitAt().get(seenKey);
        long now = clock.getTimestamp();
        float elapsed = seen == null ? 1f : clock.getElapsedDaysSince(seen);
        float count = since == null ? 0f : clock.getElapsedDaysSince(since) - (seen == null ? 1f : elapsed);
        float next = lowDays(count, elapsed, m.getStabilityValue() <= LOW_STABILITY);
        if (next > 0f) d.overLimitAt().put(sinceKey, now - (long) (next * DAY_MS));
        else d.overLimitAt().remove(sinceKey);
        d.overLimitAt().put(seenKey, now);
    }

    /** 0.3.0.9: the most days one look adds to the count (the pass looks daily; a colony out of sight, captured, isn't counted for that time). */
    static final float LOW_MAX_STEP = 2f;

    /**
     * Pure (0.3.0.9, JTOverLimitCheck): the low-stability day count after `days` more days, at LOW_STABILITY or below (low) or
     * above it. Low days add one each, at most LOW_MAX_STEP a look; days above take LOW_DECAY each, down to 0. A negative or
     * NaN count or day span counts as 0 (a clock that went back: an earlier save).
     */
    static float lowDays(float count, float days, boolean low) {
        float c = count > 0f ? count : 0f, t = days > 0f ? days : 0f;
        return low ? c + Math.min(t, LOW_MAX_STEP) : Math.max(0f, c - t * LOW_DECAY);
    }

    // ---------------- what each colony looked like yesterday, for the cause of an over-limit day (0.3.0.0) ----------------

    static final class Seen {
        int used, max, size;
        final Set<String> ids = new HashSet<String>();
    }

    /** Session memory only (CAReset.onLoad clears it): a missing day just means a shorter cause line. */
    static final Map<String, Seen> seen = new HashMap<String, Seen>();

    public static void reset() { seen.clear(); }

    static void remember(MarketAPI m) {
        Seen s = new Seen();
        s.used = CABuild.usedSlots(m);
        s.max = Misc.getMaxIndustries(m);
        s.size = m.getSize();
        for (Industry ind : m.getIndustries()) s.ids.add(ind.getId());
        seen.put(m.getId(), s);
    }

    /**
     * Why a colony is over its industry limit (0.3.0.0, 07 M4), for the log the first day it is: what's counted, upgrades and
     * builds under way, what its size allows and what adds to that, and what changed since the day before.
     */
    static String cause(MarketAPI m, int used, int max) {
        List<String> parts = new ArrayList<String>();
        int game = Misc.getNumIndustries(m);
        if (game != used) parts.add("the game counts " + game + " until its upgrades finish");
        Map<String, String> rec = CAData.get().upgradeTargets();
        for (Industry ind : m.getIndustries()) {
            if (ind.isHidden()) continue;
            if (ind.isUpgrading()) {
                String to = rec.get(m.getId() + "|" + ind.getId());
                if (to == null) to = ind.getSpec().getUpgrade();
                String toName = to;
                try { toName = Global.getSettings().getIndustrySpec(to).getName(); } catch (Throwable ignored) { }
                parts.add(ind.getCurrentName() + " upgrading" + (toName != null ? " to " + toName : ""));
            } else if (ind.isBuilding() && ind.isIndustry()) parts.add(ind.getCurrentName() + " being built");
        }
        int size = m.getSize(), base = PopulationAndInfrastructure.getMaxIndustries(size);
        parts.add("size " + size + " allows " + base + (max > base ? ", +" + (max - base) + " from its buildings and conditions"
                : max < base ? ", " + (max - base) + " from its conditions" : ""));
        Seen y = seen.get(m.getId());
        if (y != null) {
            if (y.max > max) parts.add("its limit fell from " + y.max + (y.size > size ? " when it shrank from size " + y.size : ""));
            List<String> added = new ArrayList<String>();
            for (Industry ind : m.getIndustries()) if (ind.isIndustry() && !y.ids.contains(ind.getId())) added.add(ind.getCurrentName());
            if (!added.isEmpty()) parts.add(CAContracts.join(added) + " new since yesterday");
        }
        return used + " of " + max + " slots (" + CAContracts.join(parts) + ")";
    }

    /** "costs 5 stability", or, while only an upgrade under way puts it over, what it will cost once that finishes. */
    static String costText(MarketAPI m) {
        float p = CAStability.overLimitPenalty(m);
        return p > 0f ? "costs " + Math.round(p) + " stability" : "will cost " + Math.round(PENALTY) + " stability once its upgrades finish";
    }

    /** An important line once a month while a colony sits at 0 stability. */
    static void zeroAlert(CAData d, MarketAPI m, boolean auto) {
        String key = m.getId() + ":zerostab";
        // 0.3.0.9: ":zerosince" is the low-stability day count (countLow); this alert leaves it alone
        if (!atZero(m)) { d.overLimitAt().remove(key); return; }
        Long last = d.overLimitAt().get(key);
        if (last != null && Global.getSector().getClock().getElapsedDaysSince(last) < ALERT_DAYS) return;
        d.overLimitAt().put(key, Global.getSector().getClock().getTimestamp());
        int used = CABuild.usedSlots(m), max = Misc.getMaxIndustries(m);   // 0.3.0.0: one slot count (07 M12)
        float penalty = CAStability.overLimitPenalty(m);
        CALog.advise(m.getName() + ": Stability: 0. A colony at 0 for months decivilizes. Biggest drags: "
                + causes(m) + (used > max ? ". " + used + " industries on " + max + " slots" + (penalty > 0f ? " (-" + Math.round(penalty) + ")" : "") : "")
                + (auto ? ". Autopilot takes down what costs stability until it is above 0." : ". Not on Auto. Raising it is up to you."), true);
    }

    /** The colony's worst stability modifiers as the game lists them, "Maximum number of industries exceeded -5, Commerce -3". */
    static String causes(MarketAPI m) {
        List<com.fs.starfarer.api.combat.MutableStat.StatMod> neg = new ArrayList<com.fs.starfarer.api.combat.MutableStat.StatMod>();
        for (com.fs.starfarer.api.combat.MutableStat.StatMod sm : m.getStability().getFlatMods().values()) if (sm.value < 0f) neg.add(sm);
        Collections.sort(neg, new Comparator<com.fs.starfarer.api.combat.MutableStat.StatMod>() {
            public int compare(com.fs.starfarer.api.combat.MutableStat.StatMod a, com.fs.starfarer.api.combat.MutableStat.StatMod b) { return Float.compare(a.value, b.value); }
        });
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < Math.min(4, neg.size()); i++) out.add((neg.get(i).desc != null ? neg.get(i).desc : neg.get(i).source) + " " + Math.round(neg.get(i).value));
        return out.isEmpty() ? "none listed" : CAContracts.join(out);
    }

    /** What a building does to the colony's stability, read from the game's own modifiers (0.3.0.0: CAStability.of, one place). */
    static float stabilityOf(MarketAPI m, Industry ind) { return CAStability.of(m, ind); }

    /**
     * Pure (0.3.0.0, JTOverLimitCheck): whether a stability-costing building goes today at a colony at 0 (days < 0: never stamped).
     * 0.3.0.9: daysAtZero is the low-stability day count (countLow), days at 1 or below with a slow decay.
     */
    static boolean shedDue(float daysAtZero, float daysSinceShed) {
        // a short dip (a raid, a fresh capture) passes on its own; the game needs months at 0 before it decivilizes
        if (daysAtZero < ZERO_WAIT_DAYS) return false;
        return daysSinceShed < 0f || daysSinceShed >= SHED_DAYS;
    }

    /**
     * 0.2.42.2: at 0 stability (and not over the industry limit, or with nothing there to take down), the building that costs
     * the most stability goes, read from the game's own modifiers so mod buildings count too (Commerce -3, a Bazaar, a
     * Privateer Base), one every SHED_DAYS until the colony is off 0. A capital trade building shed this way stays off the
     * capital's plan for a year. Never population, a station, one being built or upgraded, improved or locked.
     */
    static void shed(CAData d, MarketAPI m) {
        Long since = d.overLimitAt().get(m.getId() + ":zerosince"), last = d.overLimitAt().get(m.getId() + ":shed");
        if (!shedDue(since == null ? -1f : Global.getSector().getClock().getElapsedDaysSince(since),
                last == null ? -1f : Global.getSector().getClock().getElapsedDaysSince(last))) return;
        CAData.Colony col = d.colony(m.getId());
        Industry worst = null;
        float worstV = 0f;
        for (Industry ind : m.getIndustries()) {
            if (ind.isHidden() || ind.isBuilding() || ind.isUpgrading() || ind.isImproved() || !ind.canShutDown()) continue;
            if (ind.getSpec().hasTag("population") || ind.getSpec().hasTag("station") || col.locks.contains(ind.getId())) continue;
            float v = stabilityOf(m, ind);
            if (v < worstV) { worstV = v; worst = ind; }
        }
        if (worst == null) {
            // 0.3.0.9: not important (the 0-stability alert is); on screen as the decision it leaves you
            if (CALog.once("stab:none:" + m.getId())) CALog.advise(m.getName() + ": Stability: 0. None of its buildings causes it ("
                    + causes(m) + "). Raising it is up to you.");
            return;
        }
        CALog.forget("stab:none:" + m.getId());
        // 0.3.0.0: through the one rule like every takedown; it only ever raises stability here, so it always passes
        if (!CAStability.canLose(m, CAStability.takeDownDelta(m, worst), "taking down " + worst.getCurrentName())) return;
        String core = worst.getAICoreId(), name = worst.getCurrentName(), id = worst.getId();
        SpecialItemData item = worst.getSpecialItem();
        float paid = CATombstone.sunk(m.getId(), id);
        CAStores.emptyInto(m, worst);
        m.removeIndustry(id, MarketAPI.MarketInteractionMode.REMOTE, false);
        long ts = Global.getSector().getClock().getTimestamp();
        d.overLimitAt().put(m.getId() + ":shed", ts);
        d.overLimitAt().put(m.getId() + ":shed:" + id, ts);
        // 0.3.0.0 (B4): shedding keeps its own rules and never reads a mark, but it leaves one: not built back here for a year
        CATombstone.add(m.getId(), id, CATombstone.SHED, paid);
        CALog.did(m.getName() + ": Took down " + name + " (" + Math.round(worstV) + " stability). Stability was 0."
                + (core != null || item != null ? " Its " + (core != null && item != null ? "core and item" : core != null ? "core" : "item") + " to Autopilot Storage." : ""), true);
    }

    /** A building shed at this colony for stability within the last year: the capital's plan leaves it out. */
    static boolean shedRecently(MarketAPI m, String id) {
        Long t = CAData.get().overLimitAt().get(m.getId() + ":shed:" + id);
        return t != null && Global.getSector().getClock().getElapsedDaysSince(t) < SHED_HOLD_DAYS;
    }

    // ---------------- over the limit ----------------

    /** What check() does at a colony (0.3.0.0, pure for JTOverLimitCheck). */
    enum Act { WITHIN, OFF, GROWS, WAIT, SHED }

    /**
     * Pure: used and max industry slots (CABuild.usedSlots), stability as shown, the setting, whether the colony is about to
     * grow (80% of the way to a size it can still reach), the limit at that size, and days since its last over-limit takedown
     * (negative: never). At LOW stability or under it sheds at once; otherwise it waits for growth that makes room, and sheds at
     * most one a GAP_DAYS.
     */
    static Act decide(int used, int max, float stability, boolean noFix, boolean growsSoon, int maxNextSize, float daysSinceLast) {
        if (used <= max) return Act.WITHIN;
        if (noFix) return Act.OFF;
        if (stability > LOW) {
            if (growsSoon && maxNextSize >= used) return Act.GROWS;
            if (daysSinceLast >= 0f && daysSinceLast < GAP_DAYS) return Act.WAIT;
        }
        return Act.SHED;
    }

    /** Returns true when it took an industry down. */
    static boolean check(CAData d, CASettings s, MarketAPI m) {
        int used = CABuild.usedSlots(m), max = Misc.getMaxIndustries(m);   // 0.3.0.0: the build gate's count (07 M12)
        String key = "overlimit:" + m.getId();
        if (used <= max) {
            CALog.forget(key + ":keep"); CALog.forget(key + ":grow"); CALog.forget(key + ":none"); CALog.forget(key + ":off"); CALog.forget(key + ":cause");
            return false;
        }
        // 0.3.0.0 (07 M4); 0.3.0.9: starsector.log only, so a pass leaves one line on screen (the decision below, or the takedown)
        if (CALog.once(key + ":cause")) CALog.quiet(m.getName() + ": over the industry limit: " + cause(m, used, max));
        String over = used + " industries on " + max + " slots";
        int size = m.getSize();
        boolean growsSoon = size < Misc.getMaxMarketSize(m) && Misc.getMarketSizeProgress(m) >= GROWS_SOON;
        int maxNext = max + PopulationAndInfrastructure.getMaxIndustries(size + 1) - PopulationAndInfrastructure.getMaxIndustries(size);
        Long last = d.overLimitAt().get(m.getId() + ":overlimit");
        float since = last == null ? -1f : Global.getSector().getClock().getElapsedDaysSince(last);
        boolean zero = atZero(m), danger = m.getStabilityValue() <= LOW;
        switch (decide(used, max, m.getStabilityValue(), s.noOverLimitFix, growsSoon, maxNext, since)) {
            case OFF:
                if (CALog.once(key + ":off")) CALog.advise(m.getName() + ": " + over + ", " + costText(m) + ". Over-limit fix: off in Settings. Taking one down is up to you.", zero);
                return false;
            case GROWS:
                if (CALog.once(key + ":grow")) CALog.think(m.getName() + ": " + over + " (5 stability). Waits for size " + (size + 1) + ", with room for all of them.");
                return false;
            case WAIT:
            case WITHIN:
                return false;
            default:
                break;
        }
        CAData.Colony col = d.colony(m.getId());
        // 0.2.44.1: an Auto colony over its limit always sheds one: the weakest of what a colony can do without first, then
        // (defenses, fleets, ship-making, plan steps, mod buildings it can't value) the weakest of anything else that may go
        CAEndgame.Weakest wk = weakest(m, col, false);
        if (wk == null) wk = weakest(m, col, true);
        if (wk == null) {
            if (CALog.once(key + ":none")) CALog.advise(m.getName() + ": " + over + ", " + costText(m) + ". Every industry there is locked, "
                    + "under construction, improved or needed for stability. Taking one down is up to you.", true);
            return false;
        }
        return takeDown(d, m, wk, over, danger, zero);
    }

    /**
     * The industry here worth least that may go: one the engine can value, and none of what a colony must keep. With any
     * (stability at 2 or under), also defense, fleet and ship-making ones and mod ones it can't value (counted as worth 0),
     * but never population, a station, improved, locked, or one being built. 0.3.0.0: never one the colony can't lose
     * (CAStability.canLose with what taking it down does now, or with what shedding back to the limit with it does: mayShed).
     * 0.3.0.0 (WS-B4): worth is CAValue.keep, the numbers a build is scored with, plus the teardown loss spread over the horizon
     * (what Autopilot paid for it and hasn't paid back yet, CATombstone), so a building put up last month at full price isn't
     * the first to go when an older one does the same for less. A removal mark never holds this step up: it keeps its rules.
     */
    static CAEndgame.Weakest weakest(MarketAPI m, CAData.Colony col, boolean any) {
        CAData.Profile prof = CAThreat.effective(m, col.profile);
        float h = CAHorizon.months();
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        List<CAEndgame.Weakest> cands = new ArrayList<CAEndgame.Weakest>();
        List<float[]> keys = new ArrayList<float[]>();   // {value, stability it adds, holder 1/0}
        // 0.3.0.0 (fix): everything over-limit may ever take down here and what each adds, so a holder is judged by shedding
        // back to the limit, not by one takedown alone
        List<Industry> pool = new ArrayList<Industry>();
        List<Float> shares = new ArrayList<Float>();
        for (Industry ind : m.getIndustries()) {
            if (!sheddable(m, col, ind)) continue;
            pool.add(ind); shares.add(stabilityOf(m, ind));
        }
        for (int k = 0; k < pool.size(); k++) {
            Industry ind = pool.get(k);
            String id = ind.getId();
            float stab = shares.get(k);
            if (!any && (CAEndgame.keepsPlanBonus(m, plan, id) || CAContracts.feeds(ind) != null)) continue;
            // 0.3.0.0: a stability holder (taking it down would leave the colony under the floor) is never picked; fix: one that
            // adds stability isn't a holder when shedding back to the limit with it lifts the 5 and ends at or above the floor
            boolean holder = !mayShed(m, ind, others(shares, k), null);
            if (holder) {
                CAEndgame.Weakest wk = new CAEndgame.Weakest();
                wk.ind = ind;
                cands.add(wk); keys.add(new float[]{0f, stab, 1f});
                continue;
            }
            // 0.3.0.0 (B4): CAValue's reading (the same numbers a build is scored with) and what was paid for it; no valuation
            // protection here: the limit is a stability rule, not a judgement of worth. 0.3.0.0 (fix): read as a remover reads
            // it (CATombstone.forRemoval), and a failure reading one only drops that candidate
            CAValue.Kept kv;
            CATombstone.Built b;
            try {
                kv = CATombstone.forRemoval(m, CAValue.keepDetail(m, ind, prof, deficit), prof);
                b = CATombstone.built(m, id, h, null);
            } catch (Throwable t) {
                CAStep.warn("industry limit", "industry limit: couldn't value " + ind.getCurrentName() + " at " + m.getName() + ", left out", t);
                continue;
            }
            if (kv == null && any) {   // a mod industry the engine can't value: worth 0, so it goes before anything that earns
                CAEndgame.Weakest wk = CAEndgame.of(ind, null, b, h);
                cands.add(wk); keys.add(new float[]{wk.keep, stab, 0f});
                continue;
            }
            if (kv == null || kv.p == null) continue;   // a building the engine can't value for sure
            CAEngine.Pick p = kv.p;
            if (!any && (p.fx[3] > 0f || p.fx[4] > 0f || p.fx[5] > 0f || p.supplied.containsKey("ships"))) continue;
            CAEndgame.Weakest wk = CAEndgame.of(ind, kv, b, h);
            cands.add(wk); keys.add(new float[]{wk.keep, stab, 0f});
        }
        float[] value = new float[keys.size()], stab = new float[keys.size()];
        boolean[] holder = new boolean[keys.size()];
        for (int i = 0; i < keys.size(); i++) { value[i] = keys.get(i)[0]; stab[i] = keys.get(i)[1]; holder[i] = keys.get(i)[2] > 0f; }
        int i = pick(value, stab, holder);
        return i < 0 ? null : cands.get(i);
    }

    /** Pure (0.3.0.0): the candidate to take down, by rank(), never a stability holder; -1 when there's none. */
    static int pick(float[] value, float[] stability, boolean[] holder) {
        int best = -1;
        float bestRank = 0f;
        for (int i = 0; i < value.length; i++) {
            if (holder[i]) continue;
            float r = rank(value[i], stability[i]);
            if (best < 0 || r < bestRank) { best = i; bestRank = r; }
        }
        return best;
    }

    /** Ranking for weakest(): one that adds stability goes only when nothing else can (0.2.44.1). */
    static float rank(float value, float stability) { return stability > 0f ? value + 1e9f : value; }

    // ---------------- 0.3.0.0 (fix): holders judged by shedding back to the limit ----------------
    // takeDownDelta alone only lifts the 5 for the last one over, so at a colony 2 or more over every building that adds
    // stability (Military Base +2, a mod's +1) came out as a holder at 0-4 stability, and one with only those left sat at 0

    /** Whether over-limit may ever take this one down (population, stations, locked, improved, under way and 5+ holders never). */
    static boolean sheddable(MarketAPI m, CAData.Colony col, Industry ind) {
        String id = ind.getId();
        if (!ind.isIndustry() || ind.isHidden() || ind.isBuilding() || ind.isUpgrading() || ind.isImproved() || !ind.canShutDown()) return false;
        if (ind.getSpec().hasTag("population") || ind.getSpec().hasTag("station") || col.locks.contains(id)) return false;
        if (CAMods.leftToYou(id) != null) return false;   // you built it, Autopilot never does: it stays (0.2.45.0)
        // one that holds up the colony's stability by 5 or more: taking it down wouldn't lift the colony at all (0.2.44.1)
        return stabilityOf(m, ind) < PENALTY;
    }

    /** The shares of the pool without index k. */
    static float[] others(List<Float> shares, int k) {
        float[] o = new float[Math.max(0, shares.size() - 1)];
        for (int i = 0, j = 0; i < shares.size(); i++) if (i != k) o[j++] = shares.get(i);
        return o;
    }

    /** What the other buildings over-limit may take down here add, for takeDown's last-moment check. */
    static float[] others(MarketAPI m, CAData.Colony col, Industry except) {
        List<Float> s = new ArrayList<Float>();
        for (Industry ind : m.getIndustries()) if (ind != except && sheddable(m, col, ind)) s.add(stabilityOf(m, ind));
        float[] o = new float[s.size()];
        for (int i = 0; i < o.length; i++) o[i] = s.get(i);
        return o;
    }

    /**
     * Pure (JTOverLimitCheck): what shedding back to the limit starting with this one does to stability. Its own share and those
     * of the (gameCount - max - 1) others cheapest to lose come off, and the limit's penalty lifts at the end. When it alone
     * gets the colony within the limit, or there aren't enough others to get there, it's just this one's takedown.
     */
    static float toLimitDelta(float own, float[] others, float penalty, int gameCount, int max) {
        int more = gameCount - max - 1;
        if (penalty <= 0f || more <= 0) return CAStability.takeDownDelta(own, penalty, gameCount, max, true);
        if (others == null || others.length < more) return -own;
        float[] o = Arrays.copyOf(others, others.length);
        Arrays.sort(o);   // a building that costs stability first: taking it down raises it
        float d = penalty - own;
        for (int i = 0; i < more; i++) d -= o[i];
        return d;
    }

    /** Pure (JTOverLimitCheck): the colony can take this takedown on its own, or shedding back to the limit with it ends where it can. */
    static boolean mayShed(float stability, float own, float[] others, float penalty, int gameCount, int max, float floor) {
        return CAStability.canLose(stability, CAStability.takeDownDelta(own, penalty, gameCount, max, true), 0f, floor)
                || CAStability.canLose(stability, toLimitDelta(own, others, penalty, gameCount, max), 0f, floor);
    }

    /** mayShed at the colony, through CAStability's one rule. why: logged once when it can't (null: quiet, for rankings). */
    static boolean mayShed(MarketAPI m, Industry ind, float[] others, String why) {
        if (CAStability.canLose(m, CAStability.takeDownDelta(m, ind), null)) return true;
        int num = Misc.getNumIndustries(m), max = Misc.getMaxIndustries(m);
        return CAStability.canLose(m, toLimitDelta(stabilityOf(m, ind), others, CAStability.overLimitPenalty(m), num, max), why);
    }

    /** Takes it down; false when the one rule holds it after all (0.3.0.0: checked again at the last moment, with a log line). */
    static boolean takeDown(CAData d, MarketAPI m, CAEndgame.Weakest wk, String over, boolean danger, boolean zero) {
        Industry ind = wk.ind;
        String name = ind.getCurrentName(), id = ind.getId();
        if (!mayShed(m, ind, others(m, d.colony(m.getId()), ind), "taking down " + name)) return false;   // 0.3.0.0 (fix): same test as weakest()
        // 0.3.0.0 (fix): one that adds stability can go first of several; the log says the 5 makes up for it at the end
        float own = stabilityOf(m, ind);
        boolean first = own > 0f && Misc.getNumIndustries(m) - 1 > Misc.getMaxIndustries(m);
        String core = ind.getAICoreId();
        SpecialItemData item = ind.getSpecialItem();
        String cost = costText(m);
        CATombstone.Built b = wk.built != null ? wk.built : CATombstone.built(m, id, CAHorizon.months(), null);   // read before the mark clears it
        CAStores.emptyInto(m, ind);   // core and item to this colony's Autopilot Storage
        m.removeIndustry(id, MarketAPI.MarketInteractionMode.REMOTE, false);
        long ts = Global.getSector().getClock().getTimestamp();
        d.overLimitAt().put(m.getId() + ":overlimit", ts);
        d.swapAt().put(m.getId() + ":replaced", ts);   // and no make-room swap at the capital for a while
        // 0.3.0.0 (B4): a year's mark, so it isn't built back here a month later (Wind's Junkyard Forge, research 01 section 3 item 4)
        CATombstone.add(m.getId(), id, CATombstone.OVERLIMIT, b.paid);
        CALog.did(m.getName() + ": Took down " + name + ". " + over + ", " + cost + "." + (zero ? " Stability left at 0."
                : danger && m.getStabilityValue() <= LOW ? " Stability left at " + Math.round(m.getStabilityValue()) + "."
                : "") + " " + name
                + " was worth least (" + CAEngine.valueText(wk.built != null ? wk.value : wk.keep) + CATombstone.paidText(b) + ")."
                + (first ? " It added " + Math.round(own) + " stability. The 5 lifts once the colony is within its limit." : "")
                + (core != null || item != null ? " Its " + (core != null && item != null ? "core and item" : core != null ? "core" : "item") + " to Autopilot Storage." : ""), true);
        return true;
    }
}
