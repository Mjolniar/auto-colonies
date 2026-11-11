package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.0.0 (WS-B3, research 02 "Construction 0.3.0" model and sections 3, 4, 5, 13, 14; research 01 section 3 items 1, 2, 6, 7):
 * one queue for every colony. Before 0.3.0 the build pass, surplus terraforming, surplus research, wonders, stable locations
 * and story points each spent on their own, by their own value and from their own money, so a 360-day TME structure held
 * Ming Adi's one construction queue for eleven months while a Heavy Industry worth ~218,000 a month waited, cheap picks
 * spent every month's budget ahead of a big one that never saved up, and two colonies could start the same producer or a
 * Research Facility the same day.
 *
 * Now every module offers CAActions (worth, price and fee, days, upkeep, NPV over the horizon, what each ties up) and one
 * pass ranks and starts them all:
 * - Per colony, new buildings go through its one construction queue by NPV per construction day (Smith's rule); a lesser
 *   one may take the queue while a better one waits only when it finishes before the better one could start (gate).
 *   Upgrades and TME projects run in parallel, limited by money and upkeep room, never two on one building.
 *   0.3.0.0 (B3 fix): a better new build that waits keeps its industry slot and its stability room at the colony, so a lesser
 *   one that slips through the gate can't take them (not one short of money it can't save up in six months: it has no date,
 *   and the slot would stall for good); one waiting for stability room, or whose start failed for now, keeps long builds
 *   out of the queue too (DECISIONS 19).
 * - Across colonies, money goes by NPV per credit from one pool (the month's budget with savings, plus surplus credits
 *   where 0.2.45.1 let a colony draw on them), with an earmark for the first pick that waits for money: what else starts may
 *   only spend what keeps that pick's savings date, so cheap picks never starve it (02 section 5). The date is the month
 *   roll-over that brings the money in, and only the roll-overs still to come before it count (fix: an earmark set
 *   mid-month counted one month's budget too many, so cheap picks spent it and the pick never started). Fix: the earmark
 *   stays while its pick only waits for its colony's queue (a short build the gate let go first), and it lapses a month
 *   after its date (at least EARMARK_DAYS after it was set), so a pick five or six months off keeps it to its date.
 * - The new-upkeep cap counts net risk, the upkeep a building doesn't cover itself; a big step waiting for upkeep room gets
 *   the month's room held for it once its price is covered by the next roll-over (fix: also while it still saves up, and
 *   it's saved up for too), and only the build engine's picks that pay may be the month's first over the whole cap (02 s3;
 *   fix: rule-only and module actions fit within the cap, as in 0.2.45.1). Fix: the hold keeps the room from that roll-over
 *   on, not for the rest of the month it was set in, which its step could never use.
 * - Fix: defenses at a colony under siege are emergencies, as in 0.2.45.1: never held for the earmark or the upkeep hold.
 * - Fix: a proposal you rejected isn't proposed (or started) again at that colony for 30 days, whatever wakes it.
 * - The plan's own TME projects (CATerraform) and CAItems' paid core, item and administrator moves don't offer actions yet
 *   (deferred to 0.3.0.x): they still spend in their own steps after this pass, but ask setAside first, so they never spend
 *   what's saved up for the earmarked pick or take the upkeep room kept for the hold.
 * - After every start the actions that share its colony, a good, a building or a one-per-empire flag are re-scored before
 *   they can start (02 section 14: no same-pass staleness).
 * - Event-driven: a colony gets a fresh look when a construction, upgrade or project at it starts or finishes, a slot
 *   opens, its threat or siege state changes, the month rolls over (colonies that waited for money), the money tier or
 *   spending freely changes; otherwise weekly (CAAuto.wait). In between, its last offer is kept and re-scored before
 *   anything from it starts, so money arriving mid-week is spent the same day. Emergencies (0 stability, the industry
 *   limit, sieges) stay outside and run first (CAAuto.runPass).
 * - Approve colonies get the first action by the same ranking proposed; nothing there starts or spends.
 * - Removals' marks (CATombstone) keep anything from being built again at the same colony within a year.
 * The hidden legacyPlanner switch (and the planner stopping for the session after three failed days) hands everything
 * back to the 0.2.45 build pass and the modules' own spending (inCharge). Nothing here is saved: every static is cleared on
 * load (CAAuto.reset -> reset). JTPlannerCheck and JTPlannerSim test the pure part (execute and the event rules).
 */
public class CAPlanner {

    // ---------------- tuning (0.3.0.0, B3; the numbers live in CATuning) ----------------

    /** Days an earmark holds other spending back at most (research 02 section 5, EARMARK_DAYS). */
    static final float EARMARK_DAYS = CATuning.PLAN_EARMARK_DAYS;
    /** A pick that needs more than this many months of budget isn't saved up for (the savings hold 6 months of budget). */
    static final float EARMARK_MAX_MONTHS = CATuning.PLAN_EARMARK_MAX_MONTHS;
    /** Days an upkeep hold (next month's room kept for a waiting step) lasts at most (02 section 3: "or 1 month passes"). */
    static final float UPKEEP_HOLD_DAYS = CATuning.PLAN_UPKEEP_HOLD_DAYS;
    /**
     * While a better new building waits at a colony with no known start date (your approval, or money it can't save up in six
     * months), longer builds than this don't take its queue: a 360-day TME structure must not start ahead of it (DECISIONS 19).
     */
    static final float LONG_BUILD_DAYS = CATuning.PLAN_LONG_BUILD_DAYS;
    /** Days a colony's offer is kept between its looks (a weekly look and a day's slack). */
    static final float OFFER_DAYS = CATuning.PLAN_OFFER_DAYS;
    /** Every colony at once (wake). */
    static final String ALL = "*";
    /** One-per-empire flags (02 section 14): their worth assumes no other starts the same day. */
    static final String ONCE_RESEARCH = "research facility", ONCE_WONDER = "wonder site", ONCE_CREW = "stable location crew",
            ONCE_STORY = "story point";
    static final Map<String, String> ONCE_IDS = new HashMap<String, String>();
    static {
        ONCE_IDS.put("aotd_research_facility", ONCE_RESEARCH);
        ONCE_IDS.put("aotd_blacksite", ONCE_RESEARCH);
        ONCE_IDS.put("aotd_bfc_ind", "bifrost control");
        ONCE_IDS.put("IndEvo_PrivatePort", "courier port");
        ONCE_IDS.put("aotd_gw_construction_site", ONCE_WONDER);
    }

    // ---------------- session state (nothing saved; reset() on every load) ----------------

    /** A colony's last offer: its actions as scored at its last look, kept until the next. */
    static final class Offer {
        float day;
        boolean approve;
        final List<CAAction> list = new ArrayList<CAAction>();
    }

    static final Map<String, Offer> offers = new HashMap<String, Offer>();
    /** Per colony: what's under construction or upgrading (a change wakes it), free industry slots, threat and siege. */
    static final Map<String, String> sigs = new HashMap<String, String>();
    static final Map<String, Integer> slotsSeen = new HashMap<String, Integer>();
    static final Map<String, String> threatSeen = new HashMap<String, String>();
    /** Wake-ups asked for (wake): colony ids, or ALL. */
    static final Set<String> wakes = new HashSet<String>();
    /** Colonies whose pick waited for money or upkeep room in the last pass: woken when the month rolls over. */
    static final Set<String> waiting = new HashSet<String>();
    /** Per colony: nothing worth starting at its last look (for the money tiers' idle months, WS-C). */
    static final Map<String, Boolean> idle = new HashMap<String, Boolean>();
    /** A colony's actions as re-scored since the last start this pass (one evaluation re-scores all of them). */
    static final Map<String, Map<String, CAAction>> regen = new HashMap<String, Map<String, CAAction>>();
    /** The month and the money state the last pass saw (-1 / null: none yet). */
    static int lastMonth = -1;
    static String lastTier = null;
    /** The pick money is saved up for, and the pick next month's upkeep room is kept for (null: none). */
    static Earmark earmark = null;
    static Hold hold = null;
    /** The last pass: when, and how many it started (the panels' status line). */
    static float lastPass = Float.NaN;
    static int lastStarted = 0;

    /** Called by CAAuto.reset (CAReset.onLoad): a load starts with no offers, events, earmark or hold. */
    static void reset() {
        offers.clear(); sigs.clear(); slotsSeen.clear(); threatSeen.clear(); wakes.clear(); waiting.clear(); idle.clear(); regen.clear();
        lastMonth = -1; lastTier = null; earmark = null; hold = null; lastPass = Float.NaN; lastStarted = 0;
    }

    /** Money saved up for one pick: its key and price, the day it should be affordable by, when it was set, and whether it may also draw on the surplus. */
    static final class Earmark {
        String key, name, what, place;   // name: "Heavy Industry at Ming Adi"; what and place for the "Place: sentence" log line
        float total, due, set;
        boolean surplus;
        /**
         * 0.3.0.0 (B3 fix): its colony and whether it's a new build (it then keeps the earmark while that colony's queue is busy
         * and its look doesn't offer it), and the day it lapses: a month after its first date, EARMARK_DAYS after it was set
         * at the soonest (it lapsed EARMARK_DAYS after it was set, before the date of a pick five or six months off).
         */
        String market;
        boolean queue;
        float lapse;
        Earmark() { }
        Earmark(String key, String name, float total, float due, float set) {
            this.key = key; this.name = name; this.total = total; this.due = due; this.set = set;
            this.lapse = lapseDay(set, due);
        }
    }

    /** 0.3.0.0 (B3 fix): the day an earmark lapses: a month after its date, EARMARK_DAYS after it was set at the soonest. */
    static float lapseDay(float set, float due) { return Math.max(set + EARMARK_DAYS, due + EARMARK_GRACE); }

    /** 0.3.0.0 (B3 fix): days after its date an earmark still holds (the date slips when the month's budget comes in short). */
    static final float EARMARK_GRACE = 30f;

    /** Next month's new-upkeep room kept for one pick that waits only for it. */
    static final class Hold {
        String key, name, what, place;
        float net, set;
        /**
         * 0.3.0.0 (B3 fix): its colony and whether it's a new build (as Earmark), the day of the roll-over after it was set and
         * the month it was set in (-1: unknown). It keeps the room from that roll-over on: the rest of the month it was set
         * in is no use to its step, and holding it then took two months of everyone else's new-upkeep room for one step.
         */
        String market;
        boolean queue;
        float from;
        int month = -1;
        Hold() { }
        Hold(String key, String name, float net, float set) { this(key, name, net, set, set + 30f, -1); }
        Hold(String key, String name, float net, float set, float from, int month) {
            this.key = key; this.name = name; this.net = net; this.set = set; this.from = from; this.month = month;
        }
    }

    /**
     * Pure (0.3.0.0 B3 fix): whether the hold keeps the room today: from the roll-over after it was set (by the game's month
     * when both are known, else by the day; half a day of slack on a float clock), until it lapses.
     */
    static boolean holdApplies(Hold k, int month, float now) {
        if (k == null || holdLapsed(k, now)) return false;
        if (k.month >= 0 && month >= 0) return month != k.month;
        return now + 0.5f >= k.from;
    }

    /** Pure (0.3.0.0 B3 fix): a hold lapses UPKEEP_HOLD_DAYS after the roll-over it starts on (or on a clock gone back: an earlier save). */
    static boolean holdLapsed(Hold k, float now) { return now < k.set || now > k.from + UPKEEP_HOLD_DAYS; }

    // ---------------- who's in charge ----------------

    /**
     * Whether the planner starts things: not with the hidden legacyPlanner switch, and not once it stopped for the session
     * after three failed days (CAStep); then the 0.2.45 build pass and each module's own spending take over again.
     */
    public static boolean inCharge() {
        try {
            CASettings s = CAData.settings();
            if (s == null || s.legacyPlanner) return false;
        } catch (Throwable t) { return false; }
        return !CAStep.broken("planner");
    }

    /** Marks a wake-up: "finish:<market>", "slot:<market>", "threat:<market>" for one colony; "month", "tier" for all. */
    public static void wake(String event) {
        if (event == null) return;
        int i = event.indexOf(':');
        wakes.add(i < 0 ? ALL : event.substring(i + 1));
    }

    // ---------------- the pure part: money, upkeep and ordering ----------------

    /** The money and room one pass works with. Plain numbers: live() reads them from the game, the checks set them by hand. */
    static final class Pool {
        /** Credits on hand, the credit reserve, what's left of the month's budget with savings, and a month's new budget. */
        float credits, reserve, budget, monthly;
        /** Credits over this are surplus (the surplus line or the reserve, whichever is higher, plus committed production); MAX: none. */
        float surplusLine = Float.MAX_VALUE;
        /** New upkeep (net risk) added this month. */
        float upkeepAdded;
        /** Story points on hand. */
        int storyPoints;
        /** Today, days left in the month, and the stability floor. */
        float now, daysLeft = 30f, floor = CATuning.MIN_STABILITY;
        /** 0.3.0.0 (B3 fix): the game's month (CASettings.month), for the upkeep hold; -1: unknown (the checks count days). */
        int month = -1;
        /** Per colony: stability now, free industry slots (no entry: no limit). */
        final Map<String, Float> stability = new HashMap<String, Float>();
        final Map<String, Integer> slots = new HashMap<String, Integer>();
        /** Colonies with a new building under way, buildings ("market|id") busy, one-per-empire flags taken this pass. */
        final Set<String> busyQueue = new HashSet<String>(), busyBuildings = new HashSet<String>(), onceTaken = new HashSet<String>();
        Earmark earmark;
        Hold hold;

        float surplus() { return surplusLine >= Float.MAX_VALUE / 2f ? 0f : Math.max(0f, credits - surplusLine); }

        float spendable() { return Math.max(0f, credits - reserve); }

        /** What this action may spend now: budget and surplus as it draws on them, never into the reserve (0.2.45.1's avail). */
        float room(CAAction a) {
            float b = a.fromBudget ? Math.max(0f, Math.min(budget, spendable())) : 0f;
            float s = a.fromSurplus ? surplus() : 0f;
            return Math.min(spendable(), b + s);
        }

        /** Books a start: credits, the month's budget (the whole price, or only the fee), upkeep, story points and what it ties up. */
        void spend(CAAction a) {
            float total = a.total();
            credits -= total;
            budget -= a.chargeAll ? total : Math.max(0f, a.fee);
            upkeepAdded += a.netUpkeep();
            storyPoints -= Math.max(0, a.storyPoints);
            String m = a.marketId;
            if (a.usesQueue && m != null) busyQueue.add(m);
            if (a.building != null) busyBuildings.add(m + "|" + a.building);
            if (a.once != null) onceTaken.add(a.once);
            if (a.usesSlot && m != null && slots.containsKey(m)) slots.put(m, slots.get(m) - 1);
            if (a.stabilityDelta < 0f && m != null && stability.containsKey(m)) stability.put(m, stability.get(m) + a.stabilityDelta);
        }
    }

    /** What a pass did: what started, what was proposed, and every action with why it didn't start. */
    static final class Result {
        final List<CAAction> all = new ArrayList<CAAction>(), started = new ArrayList<CAAction>(), proposed = new ArrayList<CAAction>();
        Earmark earmarkSet;
        Hold holdSet;

        int count(String reasonKind) {
            int n = 0;
            for (CAAction a : all) if (reasonKind.equals(a.reasonKind)) n++;
            return n;
        }
    }

    /** What execute needs from the game; the checks use PURE (or their own). */
    interface Hooks {
        /** Starts it; null on success, else why not. */
        String start(CAAction a);
        /** Re-scores a stale action in place; false drops it. */
        boolean rescore(CAAction a);
        /** After a start, with the pool already booked: the live hooks read the real numbers back. */
        void after(CAAction a, Pool p);
        /** Proposes it for your approval. */
        void propose(CAAction a, String why);
    }

    static final Hooks PURE = new Hooks() {
        public String start(CAAction a) { return a.starter != null ? a.starter.start(a) : null; }
        public boolean rescore(CAAction a) { return a.rescore == null || a.rescore.rescore(a); }
        public void after(CAAction a, Pool p) { }
        public void propose(CAAction a, String why) { }
    };

    /** Pure: the upkeep the new-upkeep cap counts, what the building doesn't cover itself at its confidence (02 section 3). */
    static float netRisk(float upkeep, float gross, float confidence) {
        if (upkeep <= 0f) return 0f;
        float covered = Math.max(0f, Math.min(1f, confidence)) * Math.max(0f, gross);
        return Math.max(0f, upkeep - covered);
    }

    /**
     * Pure: new upkeep (net risk) fits this month: within the cap less what's kept for another waiting pick; or, for a pick
     * whose upkeep alone is over the whole cap, as the month's first new upkeep when nothing is kept for another (0.2.45 only
     * let plan steps do that, and it waited a month at most only if nothing else came first).
     */
    static boolean upkeepFits(float added, float net, float cap, float heldForOther) { return upkeepFits(added, net, cap, heldForOther, true); }

    /**
     * Pure: as above; allowBig false (0.3.0.0 B3 fix): no month's-first route over the whole cap. Only the build engine's picks
     * that pay get it (bigStep): 0.2.45.1 never started a rule-only or module action over the cap (an endgame Research Facility
     * at 40,000 a month against the 20,000 floor would otherwise go up the first pass of every month).
     */
    static boolean upkeepFits(float added, float net, float cap, float heldForOther, boolean allowBig) {
        if (net <= 0f) return true;
        float held = Math.max(0f, heldForOther);
        if (added + net <= cap - held) return true;
        return allowBig && net > cap && added <= 0f && held <= 0f;
    }

    /** 0.3.0.0 (B3 fix): may be the month's first new upkeep over the whole cap: the build engine's picks that pay. */
    static boolean bigStep(CAAction a) { return !ruleOnly(a) && CAAction.SOURCE_BUILD.equals(a.source); }

    /** 0.3.0.0 (B3 fix): its net upkeep can fit in some month: within the whole cap, or as the month's first (bigStep). */
    static boolean canFit(CAAction a, float net) { return net <= 0f || net <= a.upkeepCap || bigStep(a); }

    /** Pure: months of budget until `total` is affordable from `room` (0 now; infinite with no budget coming). */
    static float monthsToAfford(float total, float room, float monthly) {
        if (total <= room) return 0f;
        if (monthly <= 0f) return Float.POSITIVE_INFINITY;
        return (float) Math.ceil((total - room) / monthly);
    }

    /** Pure (0.3.0.0 B3 fix): short of money with no date: more than six months of budget off, or none coming (CAAction.openWait). */
    static boolean openWait(float total, float room, float monthly) {
        return total > room && monthsToAfford(total, room, monthly) > EARMARK_MAX_MONTHS;
    }

    /** Pure: days until a pick waiting for money can start (the next month rolls over in daysLeft); LONG_BUILD_DAYS when unknown. */
    static float readyIn(float total, float room, float monthly, float daysLeft) {
        float months = monthsToAfford(total, room, monthly);
        if (months <= 0f) return 0f;
        if (Float.isInfinite(months) || months > EARMARK_MAX_MONTHS) return LONG_BUILD_DAYS;
        return Math.max(1f, daysLeft) + (months - 1f) * 30f;
    }

    /**
     * Pure: whether starting this action keeps the earmarked pick's savings date (02 section 5): after its price comes off
     * the credits and its charge off the month's budget (the whole price, or only the fee), what the saved-up-for pick could
     * draw on, plus the budget still to come by its date, must still cover it. True for the earmarked pick itself, with no
     * earmark, or for an action that costs nothing.
     */
    static boolean keepsDate(Pool p, CAAction a) {
        Earmark e = p.earmark;
        if (e == null || e.key.equals(a.key())) return true;
        float total = a.total();
        return keepsDate(p, total, a.chargeAll ? total : Math.max(0f, a.fee));
    }

    /**
     * Pure: the same for spending outside the pass (setAside): `total` off the credits, `charge` off the month's budget.
     * 0.3.0.0 (B3 fix): the budget still to come is one month's per roll-over left before the date (arrivals), not the months
     * to the date rounded up, which counted one roll-over too many all month long after an earmark set mid-month.
     */
    static boolean keepsDate(Pool p, float total, float charge) {
        Earmark e = p.earmark;
        if (e == null || total <= 0f) return true;
        float credits = p.credits - total, budget = p.budget - Math.max(0f, charge);
        float spendable = Math.max(0f, credits - p.reserve);
        float b = Math.max(0f, Math.min(budget, spendable));
        float s = e.surplus && p.surplusLine < Float.MAX_VALUE / 2f ? Math.max(0f, credits - p.surplusLine) : 0f;
        float room = Math.min(spendable, b + s);
        return room + arrivals(e.due, p.now, p.daysLeft) * Math.max(0f, p.monthly) >= e.total;
    }

    /**
     * Pure (0.3.0.0 B3 fix): month roll-overs from now up to and including `due`: the next is daysLeft away, then one every
     * 30 days. Half a day of slack: the clock is a float and the daily pass doesn't run on the day's first tick.
     */
    static float arrivals(float due, float now, float daysLeft) {
        float next = now + Math.max(1f, daysLeft), slack = 0.5f;
        if (due + slack < next) return 0f;
        return 1f + (float) Math.floor((due + slack - next) / 30f);
    }

    /** Ranked only by the rule you set: no positive NPV of its own. */
    static boolean ruleOnly(CAAction a) { return a.byRule && a.npv <= 0f; }

    /** Across colonies: NPV per credit, rule-only actions last (stable for ties, so a module's own order stands). */
    static final Comparator<CAAction> RANK = new Comparator<CAAction>() {
        public int compare(CAAction x, CAAction y) {
            boolean rx = ruleOnly(x), ry = ruleOnly(y);
            if (rx != ry) return rx ? 1 : -1;
            if (rx) return 0;
            int c = Float.compare(y.perCredit(), x.perCredit());
            return c != 0 ? c : Float.compare(y.npv, x.npv);
        }
    };

    /** A colony's construction queue: NPV per construction day (Smith's rule), rule-only actions last. */
    static final Comparator<CAAction> QUEUE = new Comparator<CAAction>() {
        public int compare(CAAction x, CAAction y) {
            boolean rx = ruleOnly(x), ry = ruleOnly(y);
            if (rx != ry) return rx ? 1 : -1;
            if (rx) return 0;
            int c = Float.compare(y.perDay(), x.perDay());
            return c != 0 ? c : Float.compare(y.npv, x.npv);
        }
    };

    /** The scaffold's form: one pool of money and a month's upkeep room, nothing else limited (for quick checks). */
    static List<CAAction> execute(List<CAAction> all, float pool, float upkeepRoom) {
        Pool p = new Pool();
        p.credits = pool; p.budget = pool;
        for (CAAction a : all) if (a != null && a.upkeepCap <= 0f) a.upkeepCap = upkeepRoom;
        return execute(all, p, PURE).started;
    }

    static final int NOT_YET = 0, MOVED = 1, RESTART = 2, STARTED = 3;

    /**
     * Pure: ranks the actions, starts what fits, and marks why the rest didn't (CAAction.reasonKind). The pool is booked as
     * things start (the live hooks read the game's numbers back after each); its earmark and hold carry the saving-up state
     * between passes. Never throws for one action: a starter that throws is that action's failure.
     */
    static Result execute(List<CAAction> all, Pool pool, Hooks h) {
        if (h == null) h = PURE;
        Result res = new Result();
        List<CAAction> alive = new ArrayList<CAAction>();
        for (CAAction a : all) {
            if (a == null) continue;
            res.all.add(a);
            a.started = false; a.dropped = false; a.passed = false; a.reason = null; a.reasonKind = null; a.failWaits = false; a.openWait = false;
            if (!a.worthwhile()) { mark(a, true, CAAction.WORTHLESS, "doesn't pay back its price within the horizon"); continue; }
            if (a.tombstone > 0f) { mark(a, true, CAAction.TOMBSTONE, "was taken down here; not built again for " + Math.round(a.tombstone) + " more days"); continue; }
            // 0.3.0.0 (B3 fix): you rejected it here: not proposed or started again till the rejection's 30 days are up
            if (a.rejected > 0f) { mark(a, true, CAAction.REJECTED, "you turned it down; not proposed again for " + Math.max(1, Math.round(a.rejected)) + " more days"); continue; }
            alive.add(a);
        }
        validate(pool, alive);
        Collections.sort(alive, RANK);
        Map<String, Float> gate = new HashMap<String, Float>();
        Set<String> proposedAt = new HashSet<String>();
        boolean again = true;
        int guard = 0;
        while (again && guard++ < 20000) {   // every sweep marks, starts or re-scores one action at least; the guard only stops a bug looping
            again = false;
            for (int i = 0; i < alive.size(); i++) {
                CAAction a = alive.get(i);
                if (a.started || a.dropped || a.passed) continue;
                int r = consider(a, alive, pool, h, gate, proposedAt, res);
                if (r == NOT_YET) continue;
                if (r == MOVED) { again = true; continue; }
                if (r == STARTED && a.followUp != null) {
                    List<CAAction> more = null;
                    try { more = a.followUp.next(a); } catch (Throwable t) { more = null; }
                    if (more != null) for (CAAction x : more) {
                        if (x == null) continue;
                        res.all.add(x);
                        if (!x.worthwhile()) { mark(x, true, CAAction.WORTHLESS, "doesn't pay back its price within the horizon"); continue; }
                        alive.add(x);
                    }
                }
                Collections.sort(alive, RANK);
                again = true;
                break;
            }
        }
        // the saved-up-for pick keeps its earmark only while it waits for money or upkeep room; the same for the upkeep hold.
        // 0.3.0.0 (B3 fix): a big step waits for both (upkeep is tested first), and an earmarked pick held back by the upkeep
        // room kept for another still waits its turn, so neither loses what's kept for it
        // 0.3.0.0 (B3 fix): also while the pick only waits for its colony's queue (QUEUE, or not offered while that colony
        // builds): a short build the gate let go first there dropped it the next day, and cheap picks spent its savings
        if (pool.earmark != null) {
            CAAction e = find(alive, pool.earmark.key);
            if (e == null ? !queueBusy(pool, pool.earmark.queue, pool.earmark.market) : !waits(e, true)) pool.earmark = null;
        }
        if (pool.hold != null) {
            CAAction e = find(alive, pool.hold.key);
            if (e == null ? !queueBusy(pool, pool.hold.queue, pool.hold.market) : !waits(e, false)) pool.hold = null;
        }
        return res;
    }

    /**
     * 0.3.0.0 (B3 fix): the pick still waits for money or upkeep room it can get (orHeld: or for the upkeep room kept for
     * another), or only for its own colony's queue (QUEUE: a build under way there, which the gate lets go first only when it
     * finishes before the pick could start).
     */
    static boolean waits(CAAction a, boolean orHeld) {
        String k = a.reasonKind;
        if (CAAction.MONEY.equals(k) || CAAction.QUEUE.equals(k)) return true;
        if (CAAction.UPKEEP.equals(k)) return canFit(a, a.netUpkeep());   // over a cap it can never fit (a module's, or the cap fell): nothing to keep
        return orHeld && CAAction.HELD.equals(k);
    }

    /** 0.3.0.0 (B3 fix): a new build's colony has a new building under way (its look then doesn't offer the pick at all). */
    static boolean queueBusy(Pool pool, boolean queue, String market) { return queue && market != null && pool.busyQueue.contains(market); }

    /**
     * The earmark and hold from earlier passes: only while their pick is still offered (or, for a new build, its colony's
     * queue is busy) and not too old; a late date is set again. 0.3.0.0 (B3 fix): the earmark lapses a month after its first
     * date (lapse), not EARMARK_DAYS after it was set; the hold UPKEEP_HOLD_DAYS after the roll-over it starts on.
     */
    static void validate(Pool pool, List<CAAction> alive) {
        if (pool.earmark != null) {
            Earmark e = pool.earmark;
            CAAction a = find(alive, e.key);
            if (pool.now < e.set || pool.now > e.lapse || (a == null && !queueBusy(pool, e.queue, e.market))) pool.earmark = null;
            else if (a != null) {   // not offered while its colony builds: kept as it was
                e.total = a.total();   // its price as scored today
                if (pool.now >= e.due) {   // the budget came in slower than planned: a new date, or none
                    float room = pool.room(a), months = monthsToAfford(e.total, room, pool.monthly);
                    if (months > EARMARK_MAX_MONTHS) pool.earmark = null;
                    else e.due = pool.now + readyIn(e.total, room, pool.monthly, pool.daysLeft);   // 0.3.0.0 (B3 fix): on the roll-over that brings it in
                }
            }
        }
        if (pool.hold != null) {
            Hold k = pool.hold;
            CAAction a = find(alive, k.key);
            if (holdLapsed(k, pool.now) || (a == null && !queueBusy(pool, k.queue, k.market))) pool.hold = null;
            else if (a != null) k.net = a.netUpkeep();
        }
    }

    /** One action's turn: NOT_YET (not its queue's turn), MOVED (marked; something else may now go), RESTART (re-scored), STARTED. */
    static int consider(CAAction a, List<CAAction> alive, Pool pool, Hooks h, Map<String, Float> gate, Set<String> proposedAt, Result res) {
        String m = a.marketId;
        if (a.usesQueue) {
            if (m != null && pool.busyQueue.contains(m)) { mark(a, false, CAAction.QUEUE, "a new building is already under construction here"); return MOVED; }
            if (head(m, alive) != a) return NOT_YET;
            Float g = m == null ? null : gate.get(m);
            if (g != null && a.days > g) {
                mark(a, false, CAAction.GATE, "a better pick goes through this colony's construction queue first (" + Math.round(a.days) + " days of building)");
                return MOVED;
            }
        }
        if (a.once != null && pool.onceTaken.contains(a.once)) { mark(a, true, CAAction.ONCE, "another " + a.once + " started this pass"); return MOVED; }
        if (a.building != null && pool.busyBuildings.contains(m + "|" + a.building)) { mark(a, true, CAAction.BUILDING, "that building is busy"); return MOVED; }
        if (a.usesSlot && m != null) {
            Integer f = pool.slots.get(m);
            if (f != null && f <= 0) { mark(a, false, CAAction.SLOT, "no free industry slot"); return MOVED; }
            // 0.3.0.0 (B3 fix): a better new build waiting here keeps its slot: the gate looked only at construction days, so a
            // lesser industry that finished before it took the colony's last slot and it never started
            CAAction by = f == null ? null : keptSlot(a, alive, f);
            if (by != null) { mark(a, false, CAAction.SLOT, "the free industry slot is kept for " + by.label()); return MOVED; }
        }
        if (a.stabilityDelta < 0f && m != null) {
            Float st = pool.stability.get(m);
            if (st != null && !CAStability.canLose(st, a.stabilityDelta, 0f, pool.floor)) {
                mark(a, false, CAAction.STABILITY, "stability " + Math.round(st) + " would go to " + Math.round(st + a.stabilityDelta) + ", under Autopilot's floor of "
                        + Math.round(pool.floor));
                if (a.usesQueue) narrow(gate, m, LONG_BUILD_DAYS);   // 0.3.0.0 (B3 fix): a better new build waits for stability: no long build takes its queue meanwhile
                return MOVED;
            }
            // 0.3.0.0 (B3 fix): nor the stability room a better new build waiting here needs (the same gap as its slot)
            float keep = 0f;
            CAAction by = null;
            for (CAAction w : alive) if (w != a && w.stabilityDelta < 0f && m.equals(w.marketId) && keeps(w) && !CAAction.STABILITY.equals(w.reasonKind)) { keep -= w.stabilityDelta; by = w; }
            if (st != null && by != null && !CAStability.canLose(st - keep, a.stabilityDelta, 0f, pool.floor)) {
                mark(a, false, CAAction.STABILITY, KEPT_STABILITY + by.label());
                if (a.usesQueue) narrow(gate, m, LONG_BUILD_DAYS);
                return MOVED;
            }
        }
        if (a.storyPoints > 0 && a.storyPoints > pool.storyPoints) { mark(a, false, CAAction.STORY, "needs " + a.storyPoints + " story points"); return MOVED; }
        if (a.approveOnly || a.approval != null) {
            String why = a.approveOnly ? "waiting for your approval" : a.approval;
            if (a.fresh && m != null && proposedAt.add(m)) {
                h.propose(a, why);
                res.proposed.add(a);
                mark(a, false, CAAction.PROPOSED, why);
            } else mark(a, false, CAAction.APPROVAL, why);
            // a better new building waits for you: no long build takes its queue meanwhile (DECISIONS 19)
            if (a.usesQueue && !a.approveOnly) narrow(gate, m, LONG_BUILD_DAYS);
            return MOVED;
        }
        // upkeep: net risk against the cap, less what's kept for another waiting pick. 0.3.0.0 (B3 fix): only the build
        // engine's picks that pay may be the month's first over the whole cap (bigStep); a step short of both upkeep room and
        // money is still saved up for, and gets the room kept once the next roll-over covers its price
        // 0.3.0.0 (B3 fix): the hold keeps the room from the roll-over after it was set (holdApplies), and a siege's defenses
        // (emergency) never wait for it, as in 0.2.45.1
        float net = a.netUpkeep();
        boolean big = bigStep(a);
        float heldForOther = !a.emergency && pool.hold != null && !pool.hold.key.equals(a.key()) && holdApplies(pool.hold, pool.month, pool.now) ? pool.hold.net : 0f;
        if (!upkeepFits(pool.upkeepAdded, net, a.upkeepCap, heldForOther, big)) {
            boolean real = !upkeepFits(pool.upkeepAdded, net, a.upkeepCap, 0f, big);
            if (real) {
                float total = a.total(), room = pool.room(a);
                boolean fits = canFit(a, net);
                a.openWait = openWait(total, room, pool.monthly);
                if (total > room) earmarkFor(a, pool, res, total, room, fits);
                if (fits) holdFor(a, pool, res, net);
                float ready = total > room ? Math.max(pool.daysLeft, readyIn(total, room, pool.monthly, pool.daysLeft)) : pool.daysLeft;
                if (!fits) mark(a, false, CAAction.UPKEEP, "for upkeep room: its " + credits(net) + "/month is over the month's new-upkeep cap (" + credits(a.upkeepCap) + ")");
                else mark(a, false, CAAction.UPKEEP, "for next month's upkeep room (" + credits(net) + " of " + credits(a.upkeepCap) + ")");
                if (a.usesQueue) narrow(gate, m, fits ? ready : LONG_BUILD_DAYS);
            } else {
                mark(a, false, CAAction.HELD, "this month's upkeep room is kept for " + pool.hold.name);
                if (a.usesQueue) narrow(gate, m, LONG_BUILD_DAYS);
            }
            return MOVED;
        }
        // money: what it may draw on, less what the earmark keeps for the pick that's saved up for
        float total = a.total();
        if (total > 0f) {
            float room = pool.room(a);
            if (total > room) {
                a.openWait = openWait(total, room, pool.monthly);
                earmarkFor(a, pool, res, total, room, true);
                if (big && net > a.upkeepCap) holdFor(a, pool, res, net);   // 0.3.0.0 (B3 fix): the month's first route stays open for it on the roll-over it can pay
                mark(a, false, CAAction.MONEY, "for " + (a.fromBudget ? "budget" : "surplus credits") + " (" + credits(total) + ", " + credits(room) + " left; saving up)");
                if (a.usesQueue) narrow(gate, m, readyIn(total, room, pool.monthly, pool.daysLeft));
                return MOVED;
            }
            if (!a.emergency && !keepsDate(pool, a)) {   // 0.3.0.0 (B3 fix): a siege's defenses go as in 0.2.45.1
                mark(a, false, CAAction.HELD, "credits are set aside for " + pool.earmark.name);
                if (a.usesQueue) narrow(gate, m, LONG_BUILD_DAYS);   // it goes once the saved-up-for pick has: no long build takes its queue meanwhile
                return MOVED;
            }
        }
        // it would start: re-score it first if something started since it was scored
        if (a.stale) {
            a.stale = false;
            boolean ok;
            try { ok = h.rescore(a); } catch (Throwable t) { ok = false; }
            if (!ok) { mark(a, true, CAAction.RESCORE, "no longer offered once scored again"); return MOVED; }
            if (!a.worthwhile()) { mark(a, true, CAAction.WORTHLESS, "doesn't pay back its price once scored again"); return MOVED; }
            return RESTART;
        }
        String err;
        try { err = h.start(a); } catch (Throwable t) { err = "failed: " + t.getClass().getSimpleName(); }
        if (err != null) {
            mark(a, false, CAAction.FAILED, err);
            // 0.3.0.0 (B3 fix): a better new build that failed for now (the endgame's income floor, credits, stability) still
            // waits: no long build takes its queue meanwhile (DECISIONS 19)
            if (a.usesQueue && a.failWaits) narrow(gate, m, LONG_BUILD_DAYS);
            return MOVED;
        }
        a.started = true;
        res.started.add(a);
        pool.spend(a);
        try { h.after(a, pool); } catch (Throwable ignored) { }
        boolean freed = false;
        if (pool.earmark != null && pool.earmark.key.equals(a.key())) { pool.earmark = null; freed = true; }
        if (pool.hold != null && pool.hold.key.equals(a.key())) { pool.hold = null; freed = true; }
        affect(a, alive, freed);
        return STARTED;
    }

    /**
     * Saves up for a pick that waits for money, when nothing else is saved up for yet: it pays, draws on the budget (which
     * comes in monthly), its upkeep can fit in some month, and six months of budget cover it. 0.3.0.0 (B3 fix): its date is
     * the month roll-over that brings the money in (readyIn), as keepsDate counts it, not "now + months x 30".
     */
    static void earmarkFor(CAAction a, Pool pool, Result res, float total, float room, boolean fits) {
        if (pool.earmark != null || !fits || ruleOnly(a) || a.npv <= 0f || !a.fromBudget || a.emergency) return;   // 0.3.0.0 (B3 fix): never for a siege's defense
        if (monthsToAfford(total, room, pool.monthly) > EARMARK_MAX_MONTHS) return;
        pool.earmark = new Earmark(a.key(), a.label(), total, pool.now + readyIn(total, room, pool.monthly, pool.daysLeft), pool.now);
        pool.earmark.surplus = a.fromSurplus;
        pool.earmark.what = a.name != null ? a.name : a.id; pool.earmark.place = a.place();
        pool.earmark.market = a.marketId; pool.earmark.queue = a.usesQueue;
        res.earmarkSet = pool.earmark;
    }

    /**
     * Keeps next month's new-upkeep room for a pick that waits for it, when none is kept yet: it pays and its price is
     * covered by the next roll-over. 0.3.0.0 (B3 fix): the first 0.3.0.0 form held it only for a pick it could pay for today, so a
     * big step short of money as well was never held for, and cheap picks took the upkeep room every month.
     */
    static void holdFor(CAAction a, Pool pool, Result res, float net) {
        if (pool.hold != null || ruleOnly(a) || net <= 0f || a.emergency) return;   // 0.3.0.0 (B3 fix): never for a siege's defense
        if (a.total() > pool.room(a) + (a.fromBudget ? Math.max(0f, pool.monthly) : 0f)) return;   // it can't start next month either
        // 0.3.0.0 (B3 fix): it keeps the room from the next roll-over on (holdApplies)
        pool.hold = new Hold(a.key(), a.label(), net, pool.now, pool.now + Math.max(1f, pool.daysLeft), pool.month);
        pool.hold.what = a.name != null ? a.name : a.id; pool.hold.place = a.place();
        pool.hold.market = a.marketId; pool.hold.queue = a.usesQueue;
        res.holdSet = pool.hold;
    }

    static float total(CAAction a) { return a.total(); }

    /** 0.3.0.0 (B3 fix): the reason line of an action held back by the stability room a better new build at its colony needs. */
    static final String KEPT_STABILITY = "stability room is kept for ";

    /**
     * 0.3.0.0 (B3 fix): a new build passed over this pass for something it waits for (money, upkeep room it can get, the
     * earmark or the hold, your approval, story points, stability room, a start that failed for now). It's the better
     * pick at its colony (the queue and gate only let a lesser one through after it was marked), so it keeps its industry
     * slot and stability room there (keptSlot, consider), as the gate keeps its construction days.
     */
    static boolean keeps(CAAction h) {
        if (!h.usesQueue || !h.passed || h.started || h.dropped || h.approveOnly) return false;
        String k = h.reasonKind;
        // 0.3.0.0 (B3 fix): money it can't save up in six months has no date: keeping the colony's last slot for it stalled
        // the slot for good (the savings hold six months of budget), where 0.2.45.1 built the next pick
        if (h.openWait && (CAAction.MONEY.equals(k) || CAAction.UPKEEP.equals(k))) return false;
        if (CAAction.MONEY.equals(k) || CAAction.HELD.equals(k) || CAAction.APPROVAL.equals(k) || CAAction.PROPOSED.equals(k)
                || CAAction.STORY.equals(k) || CAAction.STABILITY.equals(k)) return true;
        if (CAAction.UPKEEP.equals(k)) return canFit(h, h.netUpkeep());   // never fits: nothing to keep
        return CAAction.FAILED.equals(k) && h.failWaits;
    }

    /** 0.3.0.0 (B3 fix): the better new build waiting at a's colony that the last of its `free` industry slots is kept for, or null. */
    static CAAction keptSlot(CAAction a, List<CAAction> alive, int free) {
        int n = 0;
        CAAction by = null;
        for (CAAction h : alive) {
            if (h == a || !h.usesSlot || !a.marketId.equals(h.marketId) || !keeps(h)) continue;
            n++;
            if (by == null) by = h;
        }
        return n >= free ? by : null;
    }

    /** The colony's queue head: its best waiting new build by NPV per construction day. */
    static CAAction head(String marketId, List<CAAction> alive) {
        CAAction best = null;
        for (CAAction x : alive) {
            if (!x.usesQueue || x.started || x.dropped || x.passed) continue;
            if (marketId == null ? x.marketId != null : !marketId.equals(x.marketId)) continue;
            if (best == null || QUEUE.compare(x, best) < 0) best = x;
        }
        return best;
    }

    static void narrow(Map<String, Float> gate, String m, float days) {
        if (m == null) return;
        Float g = gate.get(m);
        if (g == null || days < g) gate.put(m, days);
    }

    /**
     * After a start: an action with the same one-per-empire flag, or on the same building, is out of the pass; one that shares
     * its colony (stability, slots, the plan) or a good it makes (deficits, the market model's claims) is re-scored before it
     * can start (02 section 14). freed: the earmark or the upkeep hold went, so what it held back gets another look.
     */
    static void affect(CAAction s, List<CAAction> alive, boolean freed) {
        for (CAAction x : alive) {
            if (x == s || x.started || x.dropped) continue;
            if (s.once != null && s.once.equals(x.once)) { mark(x, true, CAAction.ONCE, "another " + s.once + " started this pass"); continue; }
            boolean same = s.marketId != null && s.marketId.equals(x.marketId);
            if (same && s.building != null && s.building.equals(x.building)) { mark(x, true, CAAction.BUILDING, "that building is busy"); continue; }
            if (same && (CAAction.SOURCE_BUILD.equals(s.source) || CAAction.SOURCE_BUILD.equals(x.source) || eq(s.source, x.source))) x.stale = true;
            else if (!x.goods.isEmpty() && !Collections.disjoint(s.goods, x.goods)) x.stale = true;
            if (freed && x.passed && CAAction.HELD.equals(x.reasonKind)) { x.passed = false; x.reason = null; x.reasonKind = null; }
        }
    }

    static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    static CAAction find(List<CAAction> list, String key) {
        if (key == null) return null;
        for (CAAction a : list) if (key.equals(a.key())) return a;
        return null;
    }

    static void mark(CAAction a, boolean drop, String kind, String why) {
        if (drop) a.dropped = true; else a.passed = true;
        a.reasonKind = kind;
        a.reason = why;
    }

    static String credits(float v) {
        try { return Misc.getDGSCredits(v); } catch (Throwable t) { return String.valueOf(Math.round(v)); }
    }

    // ---------------- events (pure) ----------------

    /** Pure: why a colony gets a look today, or null: what's under construction changed, a slot opened, its threat or siege changed. */
    static String event(String sigWas, String sigNow, Integer slotsWas, int slotsNow, String threatWas, String threatNow) {
        if (sigWas != null && !sigWas.equals(sigNow)) return "finish";
        if (slotsWas != null && slotsNow > slotsWas) return "slot";
        if (threatWas != null && !threatWas.equals(threatNow)) return "threat";
        return null;
    }

    /** Pure: a colony looks today when woken, when it has no date, when its date has come, or when the date is further off than any wait (an earlier save). */
    static boolean dueColony(boolean woke, Float next, float now) {
        if (woke || next == null) return true;
        return now >= next || next - now > CAAuto.MAX_WAIT + 1f;
    }

    /**
     * Pure: an empire-wide wake-up (the month, the money tier) doesn't override a rejected proposal's hold: a colony whose
     * next look is further off than any normal wait (CAAuto.reject sets 30 days) keeps it.
     */
    static boolean heldByRejection(Float next, float now, float normalWait) {
        return next != null && next > now && next - now > normalWait + 1f && next - now <= CAAuto.MAX_WAIT + 1f;
    }

    /** Pure: what's under construction or upgrading at a colony, as one string (sorted ids). */
    static String signature(List<String> busy) {
        List<String> l = new ArrayList<String>(busy);
        Collections.sort(l);
        StringBuilder b = new StringBuilder();
        for (String x : l) b.append(x).append(';');
        return b.toString();
    }

    // ---------------- the daily pass ----------------

    public static void run(CAData d, CASettings s) { run(d, s, null); }

    /** Daily from CAAuto (step "planner"). auto: the Auto colonies not under siege (null: worked out here). */
    public static void run(CAData d, CASettings s, List<MarketAPI> auto) {
        if (!inCharge()) return;
        float now = CATime.now();
        int today = CATime.today();
        if (auto == null) auto = CASiege.free(CAItems.autoMarkets());
        boolean rich = CASurplus.rich(s);
        empireEvents(d, s);
        regen.clear();
        List<CAAction> all = new ArrayList<CAAction>();
        Set<String> managed = new HashSet<String>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            String id = m.getId();
            try {   // one colony's failure never skips the rest
                CAData.Colony c = d.colony(id);
                if (c.buildout && c.mode != CAData.Mode.AUTO) CABuildout.stop(m, c, "the colony left Auto");
                if (c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) { offers.remove(id); idle.remove(id); continue; }
                managed.add(id);
                boolean siege = CASiege.held(m);
                String ev = colonyEvent(m, c, siege);
                Float next = CAAuto.nextPass.get(id);
                float wait = CAAuto.wait(siege, rich);
                boolean woke = ev != null || wakes.contains(id) || (wakes.contains(ALL) && !heldByRejection(next, now, CAAuto.wait(false, false)));
                Offer kept = offers.get(id);
                if (kept != null && kept.approve != (c.mode == CAData.Mode.APPROVE)) woke = true;   // switched between Approve and Auto: a fresh look
                if (!dueColony(woke, next, now)) {
                    Offer o = kept;
                    if (o == null || o.approve || !CATime.fresh(o.day, now, OFFER_DAYS)) continue;
                    for (CAAction a : o.list) {   // kept: re-scored before anything starts
                        a.fresh = false;
                        if (o.day < today) a.stale = true;
                        if (CAAction.SOURCE_BUILD.equals(a.source)) a.rejected = CAAuto.rejectedDays(id, a.id, now);   // 0.3.0.0 (B3 fix): rejected since the look
                        all.add(a);
                    }
                    continue;
                }
                CAAuto.nextPass.put(id, now + wait);
                Offer o = look(m, c, s, now, siege);
                o.day = today;
                offers.put(id, o);
                all.addAll(o.list);
            } catch (Throwable t) { CAStep.warn("planner", "planner look failed at " + m.getName(), t); }
        }
        wakes.clear();
        for (Iterator<String> it = offers.keySet().iterator(); it.hasNext(); ) if (!managed.contains(it.next())) it.remove();   // colonies lost or Off
        all.addAll(modules(d, s, auto));
        Pool pool = live(d, s, now, all);
        Live hooks = new Live(s);
        Result r = execute(all, pool, hooks);
        earmark = pool.earmark;
        hold = pool.hold;
        afterPass(r, pool, now);
    }

    /** Empire-wide wake-ups: the month rolled over (colonies that waited for money), the money tier or spending freely changed (all). */
    static void empireEvents(CAData d, CASettings s) {
        if (lastMonth >= 0 && s.month != lastMonth) wakes.addAll(waiting);
        lastMonth = s.month;
        String tier = d.richTier + "|" + CASurplus.rich(s) + "|" + CASurplus.on(s);
        if (lastTier != null && !lastTier.equals(tier)) wakes.add(ALL);
        lastTier = tier;
    }

    /** The colony's own event since yesterday; updates what was seen. */
    static String colonyEvent(MarketAPI m, CAData.Colony c, boolean siege) {
        String id = m.getId();
        List<String> busy = new ArrayList<String>();
        for (Industry ind : m.getIndustries()) {
            try {
                if (ind.isBuilding()) busy.add(ind.getId() + ":b");
                else if (ind.isUpgrading()) busy.add(ind.getId() + ":u");   // TME projects run as upgrades too
            } catch (Throwable ignored) { }
        }
        String sig = signature(busy);
        int slots = CABuild.freeSlots(m);
        String threat = c.threatHigh + "|" + c.profile + "|" + siege;   // CAPolicies keeps the threat state current daily
        String ev = event(sigs.get(id), sig, slotsSeen.get(id), slots, threatSeen.get(id), threat);
        sigs.put(id, sig);
        slotsSeen.put(id, slots);
        threatSeen.put(id, threat);
        return ev;
    }

    /**
     * A colony's look: the build engine's picks as actions, as the 0.2.45 build pass took them (only what pays for itself, only
     * defenses under siege, no new building while one is under way), the endgame's or the capital's replacement when every
     * industry slot is taken, and fast buildout's own pass first. Logs why nothing is worth starting (CAAuto.trace).
     */
    static Offer look(MarketAPI m, CAData.Colony c, CASettings s, float now, boolean siege) {
        Offer o = new Offer();
        o.approve = c.mode == CAData.Mode.APPROVE;
        boolean building = Misc.getCurrentlyBeingConstructed(m) != null;
        if (c.buildout && c.mode == CAData.Mode.AUTO) {
            if (siege) return o;   // a buildout builds no defenses, so it waits out the siege
            try { if (CABuildout.pass(m, c, now, building)) return o; }
            catch (Throwable t) { CAStep.warn("planner", "fast buildout pass failed at " + m.getName(), t); return o; }
        }
        CAData.Profile used;
        CAEngine.Result r;
        try {
            CAData.Profile prof = CAThreat.effective(m, c.profile);   // keeps the threat state current
            used = siege ? CAData.Profile.SECURITY : prof;
            r = CAEngine.evaluate(m, used, s.minStability);
        } catch (Throwable t) { CAStep.warn("planner", "planner look failed at " + m.getName(), t); return o; }
        o.list.addAll(pickActions(m, c, s, r, building, siege));
        // the endgame economy, then the capital: with every industry slot taken, the best industry with no slot may replace the weakest
        if (c.mode == CAData.Mode.AUTO && !building && !siege && r.blocked == null && CAEngine.worthwhile(r.bestNoSlot)) {
            CAEndgame.Weakest wk = null;
            try { wk = CAEndgame.replacement(m, c, r.bestNoSlot, used); }
            catch (Throwable t) { CAStep.warn("planner", "endgame replacement check failed at " + m.getName(), t); }
            if (wk == null) {
                try { wk = CACapital.replacement(m, c, r.bestNoSlot, used); }
                catch (Throwable t) { CAStep.warn("planner", "capital room check failed at " + m.getName(), t); }
            }
            if (wk != null) {
                CAAction k = fromPick(m, c, r.bestNoSlot, s, false, wk);
                o.list.add(k);
            }
        }
        idle.put(m.getId(), o.list.isEmpty());
        if (o.list.isEmpty()) CAAuto.trace(m, r, building, now);
        return o;
    }

    /** The evaluation's picks as actions: what pays for itself; under siege only defenses; no new building while one is under way. */
    static List<CAAction> pickActions(MarketAPI m, CAData.Colony c, CASettings s, CAEngine.Result r, boolean building, boolean siege) {
        List<CAAction> out = new ArrayList<CAAction>();
        boolean approve = c.mode == CAData.Mode.APPROVE;
        for (CAEngine.Pick q : r.picks) {
            if (!CAEngine.worthwhile(q)) continue;   // never the least-bad option: only what pays for itself
            if (siege && !CASiege.defensive(q)) continue;   // under siege only defenses are built
            if (!q.upgrade && building) continue;   // one new building at a time; upgrades still run
            try {
                CAAction a = fromPick(m, c, q, s, approve, null);
                a.emergency = siege && CASiege.defensive(q);   // 0.3.0.0 (B3 fix): a siege's defenses never wait for the earmark or the upkeep hold
                out.add(a);
            }
            catch (Throwable t) { CAStep.warn("planner", "planner offer failed at " + m.getName(), t); }
        }
        return out;
    }

    /** What a pick action carries for its starter. */
    static final class PickRef {
        final CAData.Colony c;
        final CAEngine.Pick p;
        final CAEndgame.Weakest remove;
        PickRef(CAData.Colony c, CAEngine.Pick p, CAEndgame.Weakest remove) { this.c = c; this.p = p; this.remove = remove; }
    }

    /** One of the build engine's picks as an action. remove: the industry a replacement takes down first (its slot and upkeep). */
    static CAAction fromPick(MarketAPI m, CAData.Colony c, CAEngine.Pick p, CASettings s, boolean approveOnly, CAEndgame.Weakest remove) {
        CAAction a = new CAAction();
        a.kind = p.upgrade ? CAAction.Kind.UPGRADE : CAAction.Kind.BUILD;
        a.market = m; a.marketId = m.getId(); a.source = CAAction.SOURCE_BUILD;
        a.id = p.id; a.name = p.name;
        a.why = p.lines.isEmpty() ? null : p.lines.get(0);
        a.cost = p.cost; a.fee = CABudget.constructionFee(s, p.cost); a.days = p.days;
        a.upkeep = p.upkeep - (remove != null ? remove.upkeep : 0f);   // a replaced industry's upkeep stops
        a.worth = p.worth;
        a.gross = p.fx[0] + p.upkeep;   // money before upkeep (fx[0] is after it)
        a.confidence = p.confidence;
        float h = p.horizon > 0f ? p.horizon : CAHorizon.months();
        a.npv = (p.score - (remove != null ? remove.keep : 0f)) * h;   // the score is NPV a month over the horizon (B2); a replacement gives up what it removes
        a.usesQueue = !p.upgrade;
        a.usesSlot = remove == null && CABuild.takesSlot(m, p);
        a.enabler = p.enabler;
        a.stabilityDelta = p.fx[1];
        a.building = remove != null ? "replace:" + remove.ind.getId() : p.upgrade && p.existing != null ? p.existing.getId() : null;
        a.once = ONCE_IDS.get(p.id);
        a.goods = goods(p);
        a.fromBudget = true;
        a.fromSurplus = CASurplus.spends(m);   // the focus world (every colony while spending freely) may draw on the surplus, as in 0.2.45.1
        a.chargeAll = true;
        a.upkeepCap = CASurplus.upkeepCap(m, s);
        a.approveOnly = approveOnly;
        if (!approveOnly) a.approval = approval(m, p, s);
        a.tombstone = tombstoneDays(m.getId(), p.id);
        a.rejected = CAAuto.rejectedDays(m.getId(), p.id, CATime.now());   // 0.3.0.0 (B3 fix): you rejected it here within 30 days
        a.payload = new PickRef(c, p, remove);
        a.starter = PICK_START;
        a.rescore = PICK_RESCORE;
        return a;
    }

    /** Why a pick at an Auto colony needs your approval (0.2.45.1's rules), or null. */
    static String approval(MarketAPI m, CAEngine.Pick p, CASettings s) {
        float cap = CASurplus.projectCap(m, s);   // the focus world may go past it with surplus credits
        if (p.cost > cap) return "over the per-project cap, needs your approval";
        if (p.measured && s.measuredMode == 1) return "a mod building scored from measurements, needs your approval";
        if (p.confidence < 0.9f && p.cost > 0.5f * cap) return "scored from an estimate rather than a game formula and over half the per-project cap, needs your approval";
        return null;
    }

    /** Goods a pick makes: its output lines and what it's worth in the market model. */
    static Set<String> goods(CAEngine.Pick p) {
        Set<String> out = new LinkedHashSet<String>();
        try {
            for (Map.Entry<String, Integer> e : p.supplied.entrySet()) if (e.getValue() != null && e.getValue() > 0) out.add(e.getKey());
            for (Map.Entry<String, Float> e : p.marketValue.entrySet()) if (e.getValue() != null && e.getValue() > 0f) out.add(e.getKey());
        } catch (Throwable ignored) { }
        return out;
    }

    /**
     * Days a removal's mark keeps this from being built here again (CATombstone, WS-B4), counting a mark on what it upgrades
     * into too: Wind built a Junkyard Forge a month after its Scrapyard came down (research 01 section 3 item 4).
     */
    static float tombstoneDays(String marketId, String id) {
        float days = 0f;
        try { days = CATombstone.blocked(marketId, id); } catch (Throwable ignored) { }
        try {
            List<CATombstone.Mark> marks = CATombstone.all(CAData.get());
            if (marks != null) for (CATombstone.Mark k : marks) {
                if (k == null || k.id == null || !marketId.equals(k.market) || k.id.equals(id)) continue;
                if (CAPlan.leadsTo(id, k.id)) days = Math.max(days, CATombstone.blocked(marketId, k.id));
            }
        } catch (Throwable ignored) { }
        return Math.max(0f, days);
    }

    /** Starts a pick the way the 0.2.45 build pass did: the endgame's income floor, a replacement's take-down, then CAAuto.execute. */
    static final CAAction.Starter PICK_START = new CAAction.Starter() {
        public String start(CAAction a) {
            PickRef ref = (PickRef) a.payload;
            MarketAPI m = a.market;
            CAEngine.Pick p = ref.p;
            CASettings s = CAData.settings();
            // the endgame economy gives up income only down to a third of what it was when it started
            boolean eg = CAEndgame.at(m);
            float loss = eg ? (ref.remove != null ? ref.remove.money : 0f) - p.fx[0] : 0f;
            // 0.3.0.0 (B3 fix): failWaits marks the failures that are for now (the income floor, credits, stability), so a better
            // new build that hit one still keeps long builds out of its colony's queue
            boolean noCredits = CABuild.credits() < p.cost + CABudget.constructionFee(s, p.cost);
            if (eg && !CAEndgame.lossFits(s, loss)) {
                if (CALog.once(m.getId() + ":" + p.id + ":endgame")) CALog.add(m.getName() + ": " + p.name + " waits: it costs " + Misc.getDGSCredits(loss)
                        + "/month of income and the endgame keeps " + Misc.getDGSCredits(CAEndgame.floor(s)) + "/month");
                a.failWaits = true;
                return "the endgame keeps its income floor";
            }
            if (ref.remove != null) {
                String why = CAEndgame.cantStart(m, p);
                if (why != null) {
                    if (CALog.once(m.getId() + ":" + p.id + ":replace")) CALog.add(m.getName() + ": " + p.name + " would replace " + ref.remove.ind.getCurrentName() + ", but " + why);
                    a.failWaits = noCredits;
                    return why;
                }
                if (!CAEndgame.takeDown(m, ref.remove, p)) { a.failWaits = true; return "stability held the swap"; }   // CAStability logs why
            }
            String err = CAAuto.execute(m, ref.c, p, "Auto");
            if (err != null) { a.failWaits = noCredits || failsForNow(err); return err; }
            if (eg) { CAEndgame.charge(s, loss); CAEndgame.built(m, p); }
            return null;
        }
    };

    /**
     * 0.3.0.0 (B3 fix): CABuild.start's refusals that pass with time: credits, stability, a new building the planner didn't
     * know was under way. The others (not supported, locked by research, paused for the session) aren't waits.
     */
    static boolean failsForNow(String err) {
        return err != null && (err.startsWith("Not enough credits") || err.startsWith("It would take stability") || err.startsWith("A new building is already under construction"));
    }

    /** Re-scores a pick from a fresh look at its colony (one evaluation re-scores every pick there until the next start). */
    static final CAAction.Rescore PICK_RESCORE = new CAAction.Rescore() {
        public boolean rescore(CAAction a) {
            if (a.market == null || !(a.payload instanceof PickRef)) return false;
            if (((PickRef) a.payload).remove != null) return false;   // a replacement is offered again at the colony's next look
            Map<String, CAAction> fresh = regen.get(a.marketId);
            if (fresh == null) {
                fresh = freshPicks(a.market);
                regen.put(a.marketId, fresh);
            }
            CAAction f = fresh.get(a.key());
            if (f == null) return false;
            copyNumbers(f, a);
            return true;
        }
    };

    /** A fresh evaluation of a colony's picks as actions, by key. */
    static Map<String, CAAction> freshPicks(MarketAPI m) {
        Map<String, CAAction> out = new HashMap<String, CAAction>();
        CAData d = CAData.get();
        CASettings s = d.settings;
        CAData.Colony c = d.colony(m.getId());
        if (c.mode != CAData.Mode.AUTO && c.mode != CAData.Mode.APPROVE) return out;
        boolean building = Misc.getCurrentlyBeingConstructed(m) != null, siege = CASiege.held(m);
        CAData.Profile used = siege ? CAData.Profile.SECURITY : CAThreat.effective(m, c.profile);
        CAEngine.Result r = CAEngine.evaluate(m, used, s.minStability);
        for (CAAction x : pickActions(m, c, s, r, building, siege)) out.put(x.key(), x);
        return out;
    }

    /** Copies what scoring decides from a re-scored action (not the pass's own marks). */
    static void copyNumbers(CAAction f, CAAction a) {
        a.worth = f.worth; a.cost = f.cost; a.fee = f.fee; a.days = f.days; a.upkeep = f.upkeep; a.npv = f.npv;
        a.gross = f.gross; a.confidence = f.confidence; a.stabilityDelta = f.stabilityDelta; a.usesSlot = f.usesSlot;
        a.goods = f.goods; a.upkeepCap = f.upkeepCap; a.fromBudget = f.fromBudget; a.fromSurplus = f.fromSurplus;
        a.approval = f.approval; a.tombstone = f.tombstone; a.payload = f.payload; a.basis = f.basis; a.why = f.why;
        a.byRule = f.byRule; a.storyPoints = f.storyPoints; a.approveOnly = a.approveOnly || f.approveOnly;
        a.emergency = f.emergency; a.rejected = f.rejected;   // 0.3.0.0 (B3 fix): the siege and your rejection as they are now
    }

    /** Every other module's actions, each in its own try so one failing never keeps the rest out. */
    static List<CAAction> modules(CAData d, CASettings s, List<MarketAPI> auto) {
        List<CAAction> out = new ArrayList<CAAction>();
        com.fs.starfarer.api.ModManagerAPI mods = Global.getSettings().getModManager();
        if (!auto.isEmpty()) {
            try { out.addAll(terraforming(auto)); } catch (Throwable t) { CAStep.warn("planner", "surplus terraforming offer failed", t); }
            if (mods.isModEnabled(CAResearch.MOD)) {
                try { out.addAll(CASurplusResearch.offer(auto)); } catch (Throwable t) { CAStep.warn("planner", "surplus research offer failed", t); }
            }
        }
        if (mods.isModEnabled(CAResearch.MOD)) {
            try { out.addAll(CAWonders.offer()); } catch (Throwable t) { CAStep.warn("planner", "wonders offer failed", t); }
        }
        try { out.addAll(CAObjectives.offer(d, s, auto)); } catch (Throwable t) { CAStep.warn("planner", "stable location offer failed", t); }
        try { out.addAll(CAStory.offer(auto)); } catch (Throwable t) { CAStep.warn("planner", "story point offer failed", t); }
        return out;
    }

    /** Surplus terraforming's offer (Terraforming Made Easy only; this method checks it, for audit_combos' guard rule). */
    static List<CAAction> terraforming(List<MarketAPI> auto) {
        if (!Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy") || !CACompat.linked("CATerraformSurplus")) return new ArrayList<CAAction>();
        return CATerraformSurplus.offer(auto);
    }

    /** The pool from the game: credits, the month's budget, the surplus line, upkeep added, story points, and each colony's state. */
    static Pool live(CAData d, CASettings s, float now, List<CAAction> all) {
        Pool p = moneyPool(s, now);
        p.floor = CAStability.floor();
        p.earmark = earmark;
        p.hold = hold;
        Set<String> seen = new HashSet<String>();
        for (CAAction a : all) {
            if (a.market == null || !seen.add(a.marketId)) continue;
            MarketAPI m = a.market;
            try {
                p.stability.put(a.marketId, CAStability.now(m));
                p.slots.put(a.marketId, CABuild.freeSlots(m));
                if (Misc.getCurrentlyBeingConstructed(m) != null) p.busyQueue.add(a.marketId);
                for (Industry ind : m.getIndustries()) if (ind.isUpgrading() || ind.isBuilding()) p.busyBuildings.add(a.marketId + "|" + ind.getId());
            } catch (Throwable t) { CAStep.warn("planner", "planner read failed at " + m.getName(), t); }
        }
        // 0.3.0.0 (B3 fix): the saved-up-for pick's colony and the held-for step's, even with nothing offered there today (a new
        // build under way: its look offers no other new build), so they keep their earmark and hold while it builds
        for (String id : new String[]{earmark != null && earmark.queue ? earmark.market : null, hold != null && hold.queue ? hold.market : null}) {
            if (id == null || seen.contains(id)) continue;
            try {
                MarketAPI m = CAAuto.market(id);
                if (m != null && Misc.getCurrentlyBeingConstructed(m) != null) p.busyQueue.add(id);
            } catch (Throwable t) { CAStep.warn("planner", "planner read failed at " + id, t); }
        }
        return p;
    }

    /** The pool's money from the game: credits, the reserve, the month's budget, the surplus line, upkeep added, days left in the month. */
    static Pool moneyPool(CASettings s, float now) {
        Pool p = new Pool();
        p.now = now;
        p.credits = CABuild.credits();
        p.reserve = s.reserve;
        p.month = s.month;   // 0.3.0.0 (B3 fix): for the upkeep hold (holdApplies); CABudget.rollMonth runs first in the day
        readMoney(p, s);
        float line = CASurplus.line(s);
        p.surplusLine = line <= 0f ? Float.MAX_VALUE : Math.max(line, s.reserve) + CASurplus.committed();
        try {
            int day = Global.getSector().getClock().getDay();
            p.daysLeft = Math.max(1f, 31f - day);
        } catch (Throwable ignored) { }
        return p;
    }

    /**
     * 0.3.0.0 (B3 fix; their own actions are deferred to 0.3.0.x): what spending outside the pass asks before it goes: the plan's
     * own TME projects (CATerraform) and CAItems' paid core, item and administrator moves. Null when it may spend; else why
     * it waits: it would push back the savings date of the pick the planner saves up for (keepsDate), or its new upkeep
     * would take the room kept for a big waiting step (the hold). Asks nothing while the planner isn't in charge or nothing
     * is saved up for or kept, so 0.2.45.1's own tests stay the only ones then. Emergencies (the colony limit's administrators)
     * don't ask. total: credits it pays now; charge: what counts against the month's budget; upkeep: new upkeep a month
     * (net); m: the colony, for its new-upkeep cap (null: the empire's).
     */
    public static String setAside(float total, float charge, float upkeep, MarketAPI m) {
        try {
            if (!inCharge()) return null;
            Earmark e = earmark;
            Hold k = hold;
            if (e == null && k == null) return null;
            CASettings s = CAData.settings();
            Pool p = moneyPool(s, CATime.now());
            // 0.3.0.0 (B3 fix): the earmark until it lapses (a month after its date); the hold from the roll-over after it was
            // set until it lapses (holdApplies), as in the pass
            if (e != null && p.now >= e.set && p.now <= e.lapse) {
                p.earmark = e;
                if (!keepsDate(p, total, charge)) return "credits are set aside for " + e.name;
            }
            if (k != null && upkeep > 0f && holdApplies(k, p.month, p.now)) {
                float cap = m != null ? CASurplus.upkeepCap(m, s) : CABudget.upkeepCap(s);
                if (!upkeepFits(p.upkeepAdded, upkeep, cap, k.net, false)) return "this month's upkeep room is kept for " + k.name;
            }
        } catch (Throwable t) { CAStep.warn("planner", "planner set-aside check failed", t); }
        return null;
    }

    /** The numbers that move as things start: credits, the month's budget, upkeep added, story points. */
    static void readMoney(Pool p, CASettings s) {
        p.credits = CABuild.credits();
        boolean paused = CABudget.paused(s);
        p.monthly = paused ? 0f : CABudget.limit(s);
        p.budget = paused ? 0f : CABudget.limit(s) + s.budgetCarry - s.spent;
        p.upkeepAdded = s.upkeepAdded;
        try { p.storyPoints = Global.getSector().getPlayerStats().getStoryPoints(); } catch (Throwable t) { p.storyPoints = 0; }
    }

    /** The live hooks: starters, re-scoring, the pool read back from the game, proposals. */
    static final class Live implements Hooks {
        final CASettings s;
        float upkeepBefore;
        Live(CASettings s) { this.s = s; }

        public String start(CAAction a) {
            upkeepBefore = s.upkeepAdded;
            return a.starter == null ? "nothing to start" : a.starter.start(a);
        }

        public boolean rescore(CAAction a) { return a.rescore == null || a.rescore.rescore(a); }

        public void after(CAAction a, Pool p) {
            // the cap counts net risk: the starters booked the gross upkeep (CABudget.charge), which the monthly colony lines keep
            float added = s.upkeepAdded - upkeepBefore, net = a.netUpkeep();
            if (added > net) s.upkeepAdded = upkeepBefore + Math.max(0f, net);
            readMoney(p, s);
            regen.clear();   // anything re-scored before this start is scored again
            CALog.forget(waitKey(a, CAAction.MONEY));
            CALog.forget(waitKey(a, CAAction.UPKEEP));
            CALog.forget("planner:held:" + a.key());
            if (a.marketId != null && idle.containsKey(a.marketId)) idle.put(a.marketId, false);
        }

        public void propose(CAAction a, String why) {
            if (!(a.payload instanceof PickRef) || a.market == null) return;
            PickRef ref = (PickRef) a.payload;
            CAAuto.propose(a.market, ref.c, ref.p, why);
        }
    }

    /**
     * 0.3.0.0 (B3 fix): one once-key for surplus research's waits, whichever colony: it offers a Research Facility at every
     * candidate colony, and one line each (six colonies, six lines, again every 90 days) was new spam; 0.2.45.1 logged one.
     */
    static final String RESEARCH_WAIT = "planner:research-wait";

    /** The once-key of a wait line: 0.2.45.1's for the build engine's picks (CAAuto.execute forgets them), the action's own otherwise. */
    static String waitKey(CAAction a, String kind) {
        String base = CAAction.SOURCE_BUILD.equals(a.source) ? a.marketId + ":" + a.id
                : CASurplusResearch.SOURCE.equals(a.source) ? RESEARCH_WAIT : "planner:" + a.key();
        return CAAction.UPKEEP.equals(kind) ? base + ":upkeep" : base;
    }

    /** After a pass: the kept offers lose what started, colonies that waited are remembered, waits and the earmark are logged once. */
    static void afterPass(Result r, Pool pool, float now) {
        for (CAAction a : r.started) {
            Offer o = a.marketId == null ? null : offers.get(a.marketId);
            if (o != null) o.list.remove(a);
        }
        waiting.clear();
        for (CAAction a : r.all) {
            if (!a.passed || a.reasonKind == null) continue;
            String k = a.reasonKind;
            if ((CAAction.MONEY.equals(k) || CAAction.UPKEEP.equals(k) || CAAction.HELD.equals(k)) && a.marketId != null) waiting.add(a.marketId);
            try { logWait(a); } catch (Throwable t) { CAStep.warn("planner", "planner log failed", t); }
        }
        if (r.earmarkSet != null && pool.earmark == r.earmarkSet) {
            Earmark e = r.earmarkSet;
            if (CALog.once("planner:earmark:" + e.key))
                CALog.add(e.place + ": saving up for " + e.what + " (" + credits(e.total) + ", about " + Math.max(1, Math.round((e.due - now) / 30f))
                        + (Math.round((e.due - now) / 30f) <= 1 ? " month" : " months") + "); smaller buys wait");
        }
        if (r.holdSet != null && pool.hold == r.holdSet) {
            Hold k = r.holdSet;
            if (CALog.once("planner:hold:" + k.key))
                CALog.add(k.place + ": next month's new upkeep room is kept for " + k.what + " (" + credits(k.net) + "/month)");
        }
        lastPass = now;
        lastStarted = r.started.size();
        if (!r.started.isEmpty() || r.earmarkSet != null) {
            CADiagnostics.LOG.info(CADiagnostics.TAG + "planner: " + r.started.size() + " started, " + r.count(CAAction.MONEY) + " wait for money, "
                    + r.count(CAAction.UPKEEP) + " for upkeep room, " + r.count(CAAction.HELD) + " held back, " + r.proposed.size() + " proposed"
                    + (pool.earmark != null ? "; saving up for " + pool.earmark.name + " (" + credits(pool.earmark.total) + ")" : ""));
        }
    }

    /** One line for a wait, once (CALog.once): money and upkeep room on screen as 0.2.45.1 did; the rest in starsector.log only. */
    static void logWait(CAAction a) {
        String k = a.reasonKind, name = a.name != null ? a.name : a.id;
        if (CASurplusResearch.SOURCE.equals(a.source) && CAAction.MONEY.equals(k)) {
            // 0.3.0.0 (B3 fix): as 0.2.45.1, surplus research's money wait is in its Surplus research summary, not on screen
            if (CALog.once(waitKey(a, k))) CALog.quiet(a.place() + ": " + name + " waits " + a.reason);
        } else if (CAAction.MONEY.equals(k) || CAAction.UPKEEP.equals(k)) {
            if (CALog.once(waitKey(a, k))) CALog.add(a.place() + ": " + name + " waits " + a.reason);
        } else if (CAAction.STABILITY.equals(k) && a.reason != null && a.reason.startsWith(KEPT_STABILITY)) {
            // 0.3.0.0 (B3 fix): held for a better pick's stability room: starsector.log only, like the other holds
            if (CALog.once("planner:stabkept:" + a.key())) CALog.quiet(a.place() + ": " + name + " waits: " + a.reason);
        } else if (CAAction.STABILITY.equals(k)) {
            if (CALog.once("planner:stab:" + a.key())) CALog.add(a.place() + ": " + name + " waits: " + a.reason);
        } else if (CAAction.HELD.equals(k) || CAAction.GATE.equals(k) || CAAction.SLOT.equals(k)) {
            if (CALog.once("planner:" + k + ":" + a.key())) CALog.quiet(a.place() + ": " + name + " waits: " + a.reason);
        } else if (CAAction.TOMBSTONE.equals(k)) {
            if (CALog.once("planner:tomb:" + a.key())) CALog.quiet(a.place() + ": " + name + " " + a.reason);
        }
    }

    // ---------------- for the money tiers (WS-C) and the panels (WS-F) ----------------

    /** Share of the managed colonies that had nothing worth starting at their last look (0 with none), for CARich's idle months. */
    public static float idleShare() {
        if (idle.isEmpty()) return 0f;
        int n = 0;
        for (Boolean b : idle.values()) if (Boolean.TRUE.equals(b)) n++;
        return n / (float) idle.size();
    }

    /** What the planner is saving up for, or null: "Heavy Industry at Ming Adi (465,000)". */
    public static String earmarkText() {
        Earmark e = earmark;
        return e == null ? null : e.name + " (" + credits(e.total) + ")";
    }

    /** Short lines for the Budget panel: the last pass, the earmark and the upkeep hold. */
    public static List<String> status() {
        List<String> out = new ArrayList<String>();
        if (!inCharge()) { out.add("Planner: off (the 0.2.45 build pass is in charge)"); return out; }
        out.add(Float.isNaN(lastPass) ? "Planner: no pass yet this session" : "Planner: " + lastStarted + " started in the last pass");
        if (earmark != null) out.add("Saving up for " + earmark.name + " (" + credits(earmark.total) + ")");
        if (hold != null) out.add("Upkeep room kept for " + hold.name + " (" + credits(hold.net) + "/month)");
        return out;
    }
}
