package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * WS-C2/C3. Money tiers T0-T4 (with 60% exit hysteresis) and the rung waterfall: stockpiles, growth, military, shipyard,
 * expansion, economy, wonders. Replaces CASurplus.rich() call sites. Research 03 section 5.
 *
 * 0.3.0.0 (WS-C2, research 03 F1 and sections 5.1-5.2): the money tier. Its inputs (CABudget, C1) are free cash F (credits
 * less the reserve, committed production and the runway), colony net N (the 3-month median) and idle months I:
 * <pre>
 *   T0 Tight       N &lt;= 0 or F &lt;= 0, and anything under T1
 *   T1 Surplus     F &gt;= max(1M, 2N)
 *   T2 Idle money  F &gt;= max(3M, 5N), or T1 and I &gt;= 2
 *   T3 Rich        F &gt;= max(10M, 15N), or T2 by free cash and I &gt;= 4
 *   T4 Endgame     T3 and N &gt;= the endgame setting's line (CAEndgame.ENTER_NET; none when the endgame is Off)
 * </pre>
 * Idle months raise the tier one step from what free cash gives (0.3.0.0, C12 fix): T1 and I &gt;= 2 is T2, and T2 held by
 * free cash (reached, or held on 60% of its own line) and I &gt;= 4 is T3. T2 raised from T1 by idle months stays T2.
 * Each tier exits at 60% of its entry (CATuning.TIER_EXIT). A tier reached by free cash holds while F stays at 60% of its
 * own line. A tier reached by idle months holds while F stays at 60% of the line of the tier it was raised from, whether
 * the idle months then reset or not: the money that raised it is still there, and the tier doesn't drop and come back
 * every time a month has something to build. T4 holds while N stays at 60% of the endgame line.
 * What each tier changes: T0 and T1 nothing over 0.2.45.1 (the focus world as before, on the surplus line); T2 a credit
 * counts IDLE_MONEY (half) in what a build is worth at every Approve and Auto colony (CASurplus.moneyWeight), and
 * horizonCredits hands CAHorizon free cash to slide the payback horizon on; T3 is spending freely (CASurplus.rich: from half the surplus line over it, until under a fifth of it); T4 the
 * endgame. The rung
 * waterfall (C3) spends from the tier's monthly pool.
 * The tier is worked out once a game day (tier()), saved in CAData.richTier with CAData.richTierSince, and a CAMark stamp
 * (VIA_IDLE) while it was reached by idle months, so both exit lines hold across a load. With the hidden legacyPlanner
 * switch the tier is still worked out and shown, but idle months aren't counted and spending freely is 0.2.45.1's rule.
 *
 * 0.3.0.0 (WS-C3, research 03 section 5.3): the rung waterfall. Each month the ladder may spend a pool: the tier's draw of free
 * cash (DRAW: Surplus 20%, Idle money 35%, Rich 60%, Endgame 80%; Tight none). The rungs are funded in the order of the
 * "Surplus: what to buy" preset (CASettings.richPreset, ORDERS), each up to its share of the pool (SHARE: Stockpiles 10%,
 * Growth 20%, Military 20%, Shipyard 25%, Expansion 15%, Economy 10%) plus what the rungs before it passed down. A rung
 * passes its share down when it's Off, its tier isn't reached (FROM_TIER) or the preset leaves it out, and passes down the
 * part its buyer says it doesn't want this month (want()); a rung whose buyer says nothing keeps its share. After the last
 * rung, what's left goes to the preset's last call (REST): Wonders for Balanced and Economy, the Shipyard for Fleet,
 * Expansion for Expansion, nothing for Save. All of it together stays within what's left of the pool, given out in the
 * preset's order, and no rung's allowance passes free cash now (0.3.0.0, C3 fix). Obligations (0-stability fixes,
 * emergencies, colony-limit administrators, plan steps, production already ordered) aren't paid from the pool and come
 * first: the pool is a share of free cash, which is credits after the reserve, what's promised and two months of upkeep and
 * fleet costs, and every ladder purchase asks the planner (CAPlanner.setAside) so it doesn't spend what's saved up for its
 * next pick. Per rung: On, Recommend only (proposed on the Budget panel, bought on Approve) or Off (CASettings.rungMode).
 * run() walks the order daily. The rungs bought here get room(): their share plus what came down, less their spending.
 * 0.3.0.0 (C3 fix) what each rung's buyer says it wants:
 * - Stockpiles (CAStockpile): the purchases still wanted after each look.
 * - Growth: hazard pay (CAPolicies). The game pays it from income at month end; its cost a month is the rung's want, so the
 *   rung's share holds it and the rest goes down. On, the rung raises the hazard pay budget to the tier's share of colony
 *   net, within the rung's room; Recommend only keeps your setting and names the colonies the raise would add. Cloning and
 *   hazard-clearing terraforming are not on this rung (the planner and the terraforming steps buy them).
 * - Military: one want, the military goods still wanted (CAStockpile), the cheapest upgrade that fits its upkeep line and
 *   room (CAMilitary), and a standing guard fleet that waits for credits (wantFleets, reported by CAWar).
 * - Shipyard (CAShipyard): what its order or proposal costs.
 * - Expansion (CAExpansion) says its own want; war fleets (CAWar) read allowance() themselves.
 * - Economy: surplus research, surplus terraforming and the capital's trade buildings spend surplus credits and the month's
 *   budget under their own rules (CAEconomy, C4), outside the pool. Their spending shows on the Budget panel and counts for
 *   idle months, and it isn't taken from the pool's room or any rung's allowance. The ladder doesn't cap them (free cash and
 *   their own rules do). 0.3.0.0 (C3 fix 2, DECISIONS 14b): the rung's want is 0 only, so its share goes down to the rungs
 *   after it. run() sets it daily, CAEconomy.outsidePool may say 0 earlier in the day, and want() ignores any other value
 *   for the rung. Before this fix CAEconomy also wrote a want over 0, later in the day than run(), and Wonders and
 *   CAPolicies read allowances with the share still held.
 * - Proposals (0.3.0.0, C3 fix 2): run() drops the Stockpiles, Military and Shipyard proposals with legacyPlanner, or while
 *   the preset's order leaves their rung out, since their buyers aren't called then.
 * - Wonders: Ashes of the Domain's wonders (CAWonders) and the endgame's replacements (CAEndgame) start only while
 *   wondersOpen(): the rung On, the tier Rich, and a preset that funds it (Balanced, Economy). A wonder site's price must
 *   fit the rung's allowance (wondersFit) and is recorded on it.
 * With legacyPlanner the ladder buys nothing (0.2.45.1), and wonders and replacements keep 0.2.45.1's rules.
 */
public class CARich {
    public static final int T0 = 0, T1 = 1, T2 = 2, T3 = 3, T4 = 4;
    public static final String[] TIER_NAMES = {"Tight", "Surplus", "Idle money", "Rich", "Endgame"};
    public static final String[] RUNGS = {"Obligations", "Stockpiles", "Growth", "Military", "Shipyard", "Expansion", "Economy", "Wonders"};

    // ---------------- 0.3.0.0 (C2): the numbers (the lines' floors and the 60% exit are CATuning.T1_MIN..T3_MIN, TIER_EXIT) ----------------

    /** Entry lines in months of colony net: T1 2, T2 5, T3 15 (each at least its CATuning floor). */
    static final float T1_NET = 2f, T2_NET = 5f, T3_NET = 15f;
    /** Idle months that raise T1 to T2, and T2 to T3. */
    static final int IDLE_T2 = 2, IDLE_T3 = 4;
    /** Idle months stop counting up here (a number that only has to pass IDLE_T3). */
    static final int IDLE_MAX = 120;
    /** A month is idle when at least this share of the managed colonies had nothing worth starting at their last look... */
    static final float IDLE_COLONIES = 0.5f;
    /** ...and Auto Colonies spent under this share of the month's budget (savings not counted). */
    static final float IDLE_SPENT = 0.25f;
    /** At Idle money and up, a credit counts this much in what a build is worth at every Approve and Auto colony (Rich: CASurplus.RICH_MONEY). */
    public static final float IDLE_MONEY = 0.5f;
    /** CAMark key: the tier now was reached by idle months (its exit line is the line of the tier under it). */
    static final String VIA_IDLE = "richidle";

    // ---------------- session state (reset on every load through CASurplus.reset, CAReset's "surplus investment") ----------------

    /** The game day the tier was last worked out, and that tier. */
    static int day = Integer.MIN_VALUE;
    static int cached = T0;
    /** The numbers behind it, for the panels and the rungs (NaN: not worked out yet this session). */
    static float lastFree = Float.NaN, lastNet = Float.NaN, lastRunway = Float.NaN, lastCommitted = Float.NaN;
    /** The game day the tier last changed this session (CASurplus keeps its spending-freely line off screen that day). */
    static int changedDay = Integer.MIN_VALUE;

    /** Called by CASurplus.reset (on every load): the next ask works the tier out again from the saved one. */
    static void reset() {
        day = Integer.MIN_VALUE; cached = T0; changedDay = Integer.MIN_VALUE;
        lastFree = Float.NaN; lastNet = Float.NaN; lastRunway = Float.NaN; lastCommitted = Float.NaN;
        resetLadder();   // 0.3.0.0 (C3): the pool, what each rung's buyer wants and its panel note
        CAPolicies.lastShare = 0f;   // 0.3.0.0 (C3): the Growth rung's hazard pay share, next to CAReset's CAPolicies.lastCap
        CAPolicies.lastCost = Float.NaN;   // 0.3.0.0 (C3 fix): hazard pay a month, the Growth rung's want
        CAPolicies.recommended = null;
        try { CAShipyard.reset(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "shipyard reset failed", t); }
        try { CAStockpile.reset(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "stockpile reset failed", t); }
        try { CAMilitary.reset(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "military rung reset failed", t); }
    }

    // ---------------- the tier ----------------

    /**
     * The money tier today. Worked out on the first ask of each game day from free cash, colony net and the idle months,
     * starting from the saved CAData.richTier (also after a load), so the 60% exit lines hold across reloads. A
     * what-if valuation (CALog.whatIf) reads the saved tier and changes nothing.
     */
    public static int tier() {
        int today = CATime.today();
        if (today == day) return cached;
        CAData d;
        try { d = CAData.get(); } catch (Throwable t) { return cached; }
        if (d == null || d.settings == null) return cached;
        if (CALog.whatIf()) return clamp(d.richTier);
        try { update(d, d.settings, today); }
        catch (Throwable t) {
            day = today; cached = clamp(d.richTier);   // once a day at most
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "money tier failed; it stays " + name(cached), t);
        }
        return cached;
    }

    /** Works the tier out, saves it, and logs a change. */
    static void update(CAData d, CASettings s, int today) {
        float n = CABudget.netIncome(s), committed = CABudget.committed(), runway = CABudget.runway(s);
        float f = CABudget.freeCash(CABuild.credits(), s.reserve, committed, runway);
        int was = clamp(d.richTier);
        boolean fresh = d.richTierSince == 0L;   // 0: not worked out yet in this save
        // a save from before 0.3.0 that was spending freely (0.2.45.1's once-key) starts at Rich, so its 60% exit line holds
        int from = fresh && was == T0 && CASurplus.richWas() ? T3 : was;
        boolean viaIdle = CAMark.at(VIA_IDLE) != null;
        Step st = next(f, n, d.idleMonths, from, viaIdle, endgameLine(s));
        day = today; cached = st.tier;
        lastFree = f; lastNet = n; lastRunway = runway; lastCommitted = committed;
        if (st.idle && !viaIdle) CAMark.set(VIA_IDLE, CAMark.now());
        else if (!st.idle && viaIdle) CAMark.clear(VIA_IDLE);
        if (st.tier == was && !fresh) return;
        d.richTier = st.tier;
        d.richTierSince = CAMark.now();
        changedDay = today;
        String line = changeLine(fresh ? -1 : was, st.tier, f, n, d.idleMonths, st.idle);
        CALog.think(line);   // 0.3.0.9: a tier change is status, off screen (0.3.1.0: the Thinking list)
    }

    /** Whether the tier changed today. */
    static boolean changedToday() { return changedDay == CATime.today(); }

    /** Whether idle months raised the tier now, for callers that want Rich reached by free cash only. */
    public static boolean byIdle() {
        tier();
        return CAMark.at(VIA_IDLE) != null;
    }

    /** Free cash as of today's tier (NaN before it's worked out, or offline). */
    public static float freeCash() {
        tier();
        return lastFree;
    }

    /** The tier's name. */
    public static String name(int t) { return TIER_NAMES[clamp(t)]; }

    static int clamp(int t) { return Math.max(T0, Math.min(T4, t)); }

    /** The endgame setting's colony net line, 0 when the endgame is Off. */
    static float endgameLine(CASettings s) {
        int m = s.endgameMode;
        return m >= 0 && m < CAEndgame.ENTER_NET.length ? CAEndgame.ENTER_NET[m] : 0f;
    }

    // ---------------- pure rules (JTRichCheck) ----------------

    /** A tier and whether idle months raised it (its exit line is then the line of the tier under it). */
    static final class Step {
        final int tier;
        final boolean idle;
        Step(int tier, boolean idle) { this.tier = tier; this.idle = idle; }
        public String toString() { return TIER_NAMES[tier] + (idle ? " (idle)" : ""); }
    }

    /** Pure: the free cash lines of T1, T2 and T3 at this colony net (index = tier; [0] is 0). */
    static float[] lines(float net) {
        float n = Math.max(0f, net);
        return new float[]{0f, Math.max(CATuning.T1_MIN, T1_NET * n), Math.max(CATuning.T2_MIN, T2_NET * n), Math.max(CATuning.T3_MIN, T3_NET * n)};
    }

    /** Pure: the tier rule as the A0 stub named it: a tier reached by free cash, no endgame line. */
    static int tierFor(float freeCash, float colonyNet, int idleMonths, int current) {
        return next(freeCash, colonyNet, idleMonths, current, false, 0f).tier;
    }

    /**
     * Pure: the tier after today's look. current: the tier held now; viaIdle: idle months raised it; endgameLine: the
     * endgame setting's colony net (0: Off). See the class comment for the table and the exit lines.
     */
    static Step next(float f, float n, int idle, int current, boolean viaIdle, float endgameLine) {
        if (!(n > 0f) || !(f > 0f)) return new Step(T0, false);
        float[] l = lines(n);
        float exit = CATuning.TIER_EXIT;
        // entered by free cash today
        int cash = T0;
        for (int k = T1; k <= T3; k++) if (f >= l[k]) cash = k;
        // what the tier held now still holds: 60% of its own line, or of the line under it when idle months raised it
        int held = T0;
        boolean heldIdle = false;
        for (int k = Math.min(T3, Math.max(T0, current)); k >= T1; k--) {
            boolean idleK = viaIdle && k >= T2;
            if (f >= exit * l[idleK ? k - 1 : k]) { held = k; heldIdle = idleK; break; }
        }
        int t = cash >= held ? cash : held;
        boolean via = cash < held && heldIdle;
        // 0.3.0.0 (C12 fix): idle months raise Idle money to Rich only when Idle money is held by free cash (reached today, or
        // held on 60% of its own line). Idle money raised from Surplus by idle months stays there: Surplus plus any number of
        // idle months is Idle money at most (WS-C C2 and research 03 section 5.2: the author's save, F 1.7M, N 600,000, lands in T2)
        boolean t2ByCash = t == T2 && !via;
        // idle months raise it
        if (t == T1 && idle >= IDLE_T2) { t = T2; via = true; }
        if (t2ByCash && idle >= IDLE_T3) { t = T3; via = true; }
        // the endgame on top of Rich
        if (t == T3 && endgameLine > 0f && (n >= endgameLine || (current >= T4 && n >= exit * endgameLine))) t = T4;
        return new Step(t, via);
    }

    /** Pure: whether a month counts as idle: enough colonies had nothing worth starting, and little of the budget was spent. */
    static boolean idleMonth(float idleShare, float spent, float budget) {
        return idleShare >= IDLE_COLONIES && budget > 0f && spent < IDLE_SPENT * budget;
    }

    /** Pure: the idle months after a month: one more when it was idle, 0 when not, as they were when it wasn't counted. */
    static int idleAfter(int months, boolean counted, boolean idle) {
        if (!counted) return Math.max(0, months);
        return idle ? Math.min(IDLE_MAX, Math.max(0, months) + 1) : 0;
    }

    /** Pure: the log line for a tier change (was -1: the first look in this save). */
    static String changeLine(int was, int now, float f, float n, int idle, boolean viaIdle) {
        StringBuilder b = new StringBuilder("Money tier: ").append(TIER_NAMES[clamp(now)]);
        if (was >= 0) b.append(now > was ? ", up from " : ", down from ").append(TIER_NAMES[clamp(was)]);
        if (now == T4) b.append("; colony net ").append(Misc.getDGSCredits(n)).append(" a month");
        else if (!(n > 0f)) b.append("; colony net ").append(Misc.getDGSCredits(n)).append(" a month");
        else b.append("; free cash ").append(Misc.getDGSCredits(f));
        if (viaIdle && now >= T2 && now <= T3) b.append(", idle ").append(idle).append(idle == 1 ? " month" : " months");
        return b.toString();
    }

    // ---------------- idle months (from CABudget.rollMonth) ----------------

    /**
     * When a month ends: counts it toward d.idleMonths when at least half the managed colonies had nothing worth starting
     * at their last look (CAPlanner.idleShare) and Auto Colonies spent under a quarter of the month's budget, the ladder's
     * purchases (d.rungSpent) included; resets the count otherwise. A month the planner has no look for (it isn't in
     * charge, or the game was just loaded) leaves the count as it is. Clears d.rungSpent for the new month.
     */
    static void monthEnded(CASettings s, float spent, float budget) {
        if (CALog.whatIf()) return;
        CAData d = CAData.get();
        boolean counted = CAPlanner.inCharge() && !CAPlanner.idle.isEmpty();
        float share = counted ? CAPlanner.idleShare() : 0f;
        float all = Math.max(0f, spent) + total(d.rungSpent());
        boolean idle = idleMonth(share, all, budget);
        int was = d.idleMonths;
        d.idleMonths = idleAfter(was, counted, idle);
        d.rungSpent().clear();
        day = Integer.MIN_VALUE;   // the tier is worked out again with the new count
        resetLadder();   // 0.3.0.0 (C3): a new month's pool, from the new month's free cash
        CALog.quiet("Money tiers: last month " + (counted ? (idle ? "idle" : "not idle") + " (" + Math.round(share * 100f)
                + "% of colonies with nothing worth starting; Auto Colonies spent " + Misc.getDGSCredits(all) + " of a "
                + Misc.getDGSCredits(budget) + " budget)" : "not counted (no planner look this session)") + "; idle months " + was + " -> " + d.idleMonths);
    }

    static float total(Map<String, Float> m) {
        float t = 0f;
        if (m != null) for (Float v : m.values()) if (v != null && v > 0f) t += v;
        return t;
    }

    // ---------------- 0.3.0.0 (C3): the rung waterfall (research 03 section 5.3) ----------------

    /** Rung indices (RUNGS order). */
    public static final int OBLIGATIONS = 0, STOCKPILES = 1, GROWTH = 2, MILITARY = 3, SHIPYARD = 4, EXPANSION = 5, ECONOMY = 6, WONDERS = 7;
    /** Rung modes (CASettings.rungMode, RUNG_MODE_NAMES). */
    public static final int OFF = 0, ON = 1, RECOMMEND = 2;
    /** Share of free cash the month's pool is, by tier T0..T4. */
    static final float[] DRAW = {0f, 0.20f, 0.35f, 0.60f, 0.80f};
    /** Each rung's share of the pool, RUNGS order. Obligations aren't paid from the pool; Wonders get what's left (REST). */
    static final float[] SHARE = {0f, 0.10f, 0.20f, 0.20f, 0.25f, 0.15f, 0.10f, 0f};
    /** The tier each rung opens at, RUNGS order (Economy is payback-gated below Rich by its own buyers). */
    static final int[] FROM_TIER = {T0, T1, T1, T2, T2, T2, T1, T3};
    /** Rung order by preset (CASettings.RICH_PRESET_NAMES): Balanced, Fleet, Expansion, Economy (0.2.45.1's sinks), Save. */
    static final int[][] ORDERS = {
            {STOCKPILES, GROWTH, MILITARY, SHIPYARD, EXPANSION, ECONOMY, WONDERS},
            {SHIPYARD, MILITARY, GROWTH, STOCKPILES, EXPANSION, ECONOMY},
            {EXPANSION, GROWTH, MILITARY, STOCKPILES, SHIPYARD, ECONOMY},
            {ECONOMY, GROWTH, STOCKPILES, WONDERS},
            {STOCKPILES, GROWTH}};
    /** Where what no rung wanted goes after the last rung, by preset; -1: nowhere (it stays in your account). */
    static final int[] REST = {WONDERS, SHIPYARD, EXPANSION, WONDERS, -1};

    /** The month's pool (session): its month key, the tier it was worked out at, and the credits. */
    static int poolMonth = Integer.MIN_VALUE, poolTier = -1;
    static float poolValue = 0f;
    /** What each rung's buyer said it still wants this month (credits), and the month key it said it in. */
    static final float[] wants = new float[RUNGS.length];
    static final int[] wantMonth = new int[RUNGS.length];
    /** A short panel note per rung ("2 Eagle ordered, delivered to Wind at month end"), session only. */
    static final String[] notes = new String[RUNGS.length];

    /** 0.3.0.0 (C3 fix): a standing guard fleet's price and fee that waits for credits (CAWar.standingGuard), and its month key. */
    static float fleetsWant = 0f;
    static int fleetsMonth = Integer.MIN_VALUE;

    static void resetLadder() {
        poolMonth = Integer.MIN_VALUE; poolTier = -1; poolValue = 0f;
        java.util.Arrays.fill(wants, 0f);
        java.util.Arrays.fill(wantMonth, Integer.MIN_VALUE);
        java.util.Arrays.fill(notes, null);
        fleetsWant = 0f; fleetsMonth = Integer.MIN_VALUE;
    }
    static { resetLadder(); }

    /** The game month as one number (cycle x 12 + month), Integer.MIN_VALUE + 1 with no sector. */
    static int monthKey() {
        try {
            com.fs.starfarer.api.campaign.CampaignClockAPI c = com.fs.starfarer.api.Global.getSector().getClock();
            return c.getCycle() * 12 + c.getMonth();
        } catch (Throwable t) { return Integer.MIN_VALUE + 1; }
    }

    /** Pure: the rungs of a preset in order (Balanced for an unknown preset). */
    static int[] order(int preset) { return ORDERS[preset >= 0 && preset < ORDERS.length ? preset : 0]; }

    /** Pure: the preset's last call (-1: none). */
    static int rest(int preset) { return REST[preset >= 0 && preset < REST.length ? preset : 0]; }

    /** Pure: whether the preset funds this rung at all. */
    static boolean inPreset(int preset, int rung) {
        for (int r : order(preset)) if (r == rung) return true;
        return rest(preset) == rung;
    }

    /** Pure: whether a rung is open: not legacyPlanner, its tier reached, its mode not Off, and in the preset. */
    static boolean open(int rung, int tier, int mode, int preset, boolean legacy) { return open(rung, tier, mode, preset, legacy, false); }

    /** Pure, 0.3.2.0 (M7): as above; shipyardEarly: the Shipyard rung opens at Surplus (production floors on, CASettings.shipyardFromSurplus). */
    static boolean open(int rung, int tier, int mode, int preset, boolean legacy, boolean shipyardEarly) {
        if (legacy || rung <= OBLIGATIONS || rung >= RUNGS.length) return false;
        return tier >= fromTier(rung, shipyardEarly) && mode != OFF && inPreset(preset, rung);
    }

    /** Pure, 0.3.2.0: the tier a rung opens at: FROM_TIER, with the Shipyard at Surplus (T1) when shipyardEarly. */
    static int fromTier(int rung, boolean shipyardEarly) { return rung == SHIPYARD && shipyardEarly ? T1 : FROM_TIER[rung]; }

    /** 0.3.2.0: the Shipyard rung opens at Surplus: production Light or Full with Shipyard from Surplus on (Off: Idle money, as 0.3.1.0). */
    static boolean shipyardEarly(CASettings s) { return s != null && s.productionOn() && s.shipyardFromSurplus; }

    /** Pure: the month's pool: the tier's draw of free cash as it was at the month's start (free cash now plus what the ladder spent since). */
    static float poolOf(int tier, float freeCash, float spentThisMonth) {
        float f = freeCash + Math.max(0f, spentThisMonth);
        if (!(f > 0f)) return 0f;
        return DRAW[clamp(tier)] * f;
    }

    /**
     * Pure: what each rung may still spend this month (RUNGS order). pool: the month's pool; order and rest: the preset's;
     * open: per rung; spent: what each spent this month; want: what each buyer still wants (NaN: it didn't say, so the rung
     * keeps its share). Walks the order: a rung has its share plus what came down; a closed rung passes all of it down; an
     * open one keeps what it wants of what's left after its spending and passes the rest down. The last call gets what's left
     * at the end. 0.3.0.0 (C3 fix): all of it together stays within what's left of the pool (a rung that spent past its share
     * takes from the ones after it), given out in the preset's order; the Economy rung's spending is outside the pool
     * (spentInPool) and doesn't count, neither in the pool's total nor in the rung's own share, so what the rung doesn't want
     * goes down whole (0.3.0.0, C3 fix 2, DECISIONS 14b).
     */
    static float[] waterfall(float pool, int[] order, int rest, boolean[] open, float[] spent, float[] want) {
        float[] out = new float[RUNGS.length];
        if (!(pool > 0f) || order == null) return out;
        float used = 0f;
        for (int r = 0; r < RUNGS.length; r++) used += spentInPool(spent, r);
        float carry = 0f;
        for (int r : order) {
            float avail = SHARE[r] * pool + carry;
            if (!open[r]) { carry = avail; continue; }
            // 0.3.0.0 (C3 fix 2): a rung's own spending lowers what it has left; the Economy rung's buyers spend outside the
            // pool (DECISIONS 14b), so its share goes down whole
            float left = Math.max(0f, avail - spentInPool(spent, r));
            float w = want == null || Float.isNaN(want[r]) ? Float.MAX_VALUE : Math.max(0f, want[r]);
            float keep = Math.min(left, w);
            out[r] += keep;
            carry = left - keep;
        }
        boolean restInOrder = false;
        for (int r : order) if (r == rest) restInOrder = true;
        if (rest >= 0 && rest < RUNGS.length && open[rest]) out[rest] += carry;
        // 0.3.0.0 (C3 fix): what's left of the pool, given out in the preset's order (the last call's rest with its own place)
        float room = Math.max(0f, pool - used);
        for (int r : order) { out[r] = Math.min(out[r], room); room -= out[r]; }
        if (rest >= 0 && rest < RUNGS.length && !restInOrder) out[rest] = Math.min(out[rest], room);
        return out;
    }

    /** Pure, 0.3.0.0 (C3 fix): a rung's spending this month as the pool counts it: the Economy rung's buyers spend outside the pool. */
    static float spentInPool(float[] spent, int rung) {
        if (rung == ECONOMY) return 0f;
        return spentOwn(spent, rung);
    }

    /** Pure: a rung's spending this month as recorded (every rung, Economy included); the waterfall reads spentInPool. */
    static float spentOwn(float[] spent, int rung) {
        if (spent == null || rung < 0 || rung >= spent.length) return 0f;
        return Math.max(0f, spent[rung]);
    }

    /** Whether a rung is open now (see open(...)). Safe offline: false with no save. */
    public static boolean open(int rung) {
        try {
            CASettings s = CAData.settings();
            return open(rung, tier(), s.rungMode(rung), s.richPreset, CABudget.legacy(s), shipyardEarly(s));
        } catch (Throwable t) { return false; }
    }

    /** A rung's mode (OFF, ON, RECOMMEND); OFF with no save. */
    public static int mode(int rung) {
        try { return CAData.settings().rungMode(rung); } catch (Throwable t) { return OFF; }
    }

    /** The month's pool (0 at Tight, with legacyPlanner, or with no save). Worked out once a month and tier. */
    public static float pool() {
        try {
            CAData d = CAData.get();
            CASettings s = d.settings;
            if (CABudget.legacy(s)) return 0f;
            int t = tier(), key = monthKey();
            if (key == poolMonth && t == poolTier) return poolValue;
            float f = CABudget.freeCash(s);
            poolValue = poolOf(t, Float.isNaN(f) ? 0f : f, total(d.rungSpent()));
            poolMonth = key; poolTier = t;
            return poolValue;
        } catch (Throwable t) { return 0f; }
    }

    /** What each rung spent this month, RUNGS order. */
    static float[] spentNow(CAData d) {
        float[] out = new float[RUNGS.length];
        Map<String, Float> m = d.rungSpent();
        for (int r = 0; r < RUNGS.length; r++) { Float v = m.get(RUNGS[r]); out[r] = v == null ? 0f : v; }
        return out;
    }

    /** Every rung's allowance now (all 0 with no save). */
    static float[] allowances() { return allowances(-1); }

    /**
     * Every rung's allowance now; keepAll: a rung whose own want is left out, so it keeps all it could (room()), -1 none.
     * 0.3.0.0 (C3 fix): no allowance passes free cash now. The pool is fixed at the month's first look, and free cash can fall
     * during the month (production you order, the planner's builds), so a rung never spends into the reserve or runway.
     */
    static float[] allowances(int keepAll) {
        try {
            CAData d = CAData.get();
            CASettings s = d.settings;
            int t = tier(), key = monthKey();
            boolean legacy = CABudget.legacy(s);
            boolean[] open = new boolean[RUNGS.length];
            float[] w = new float[RUNGS.length];
            for (int r = 0; r < RUNGS.length; r++) {
                open[r] = open(r, t, s.rungMode(r), s.richPreset, legacy, shipyardEarly(s));
                w[r] = r != keepAll && wantMonth[r] == key ? wants[r] : Float.NaN;
            }
            float[] out = waterfall(pool(), order(s.richPreset), rest(s.richPreset), open, spentNow(d), w);
            float free = CABudget.freeCash(s);
            float cap = Float.isNaN(free) ? 0f : Math.max(0f, free);
            for (int r = 0; r < out.length; r++) out[r] = Math.min(out[r], cap);
            return out;
        } catch (Throwable t) { return new float[RUNGS.length]; }
    }

    /** Credits a rung may spend this month (0 when off or not reached). */
    public static float allowance(int rung) {
        if (rung < 0 || rung >= RUNGS.length) return 0f;
        return allowances()[rung];
    }

    /**
     * 0.3.0.0 (C3 fix): what a rung could spend this month if its buyer wanted all of it: its share plus what came down to it,
     * less its spending, within free cash. A rung's own buyer spends from this; what it says it wants (want()) decides what
     * goes down to the rungs after it.
     */
    public static float room(int rung) {
        if (rung < 0 || rung >= RUNGS.length) return 0f;
        return allowances(rung)[rung];
    }

    /**
     * 0.3.0.0 (C3 fix): CAWar says what a standing guard fleet that waits for credits costs (price and fee; 0: none due). It
     * counts in the Military rung's want for the rest of the month.
     */
    public static void wantFleets(float credits) {
        fleetsWant = Math.max(0f, Float.isNaN(credits) ? 0f : credits);
        fleetsMonth = monthKey();
    }

    /** 0.3.0.0 (C3 fix): the Military rung's one want: military goods, the cheapest upgrade that fits, and a guard fleet due. */
    static float militaryWant() {
        float goods = Math.max(0f, CAStockpile.lastWant[1]), up = Math.max(0f, CAMilitary.lastWant);
        float fleets = fleetsMonth == monthKey() ? fleetsWant : 0f;
        return goods + up + fleets;
    }

    /** Pure, 0.3.0.0 (C3 fix): whether wonders and the endgame's replacements may start: 0.2.45.1's rules with legacyPlanner, else the Wonders rung On, the tier Rich and a preset that funds it. */
    static boolean wondersOpen(boolean legacy, int tier, int mode, int preset) {
        if (legacy) return true;
        return mode == ON && open(WONDERS, tier, mode, preset, false);
    }

    /**
     * 0.3.0.0 (C3 fix): whether Ashes of the Domain's wonders (CAWonders) and the endgame's replacements (CAEndgame) may start
     * now. Recommend only starts none (there is no wonder proposal): the rung's note says so.
     */
    public static boolean wondersOpen(CASettings s) {
        if (s == null) return false;
        try { return wondersOpen(CABudget.legacy(s), tier(), s.rungMode(WONDERS), s.richPreset); } catch (Throwable t) { return false; }
    }

    /** 0.3.0.0 (C3 fix): whether a wonder's price fits the Wonders rung's allowance this month (always with legacyPlanner). */
    public static boolean wondersFit(CASettings s, float credits) {
        if (s == null) return false;
        if (CABudget.legacy(s)) return true;
        return Math.max(0f, credits) <= allowance(WONDERS) + 1f;
    }

    /**
     * A rung's buyer says how much more it wants this month (credits; 0: nothing). The rest of the rung's share then goes
     * down to the next rung. Only a rung's one buyer should say it; a rung no buyer speaks for keeps its share.
     * 0.3.0.0 (C3 fix 2): the Economy rung's want is 0 only. Its buyers spend outside the pool (DECISIONS 14b), so a want
     * over 0 for it is ignored; 0 is taken (CAEconomy.outsidePool says it from the endgame step, run() sets it daily).
     */
    public static void want(int rung, float credits) {
        if (rung == ECONOMY && credits > 0f) return;
        setWant(rung, credits);
    }

    /** Stores a rung's want for this month. 0.3.0.0 (C3 fix 2): want() for the buyers (Economy 0 only); run() sets the Economy rung's 0. */
    static void setWant(int rung, float credits) {
        if (rung < 0 || rung >= RUNGS.length) return;
        wants[rung] = Math.max(0f, Float.isNaN(credits) ? 0f : credits);
        wantMonth[rung] = monthKey();
    }

    /** A short note for the Budget panel about what a rung did or waits for (null clears it). */
    public static void note(int rung, String text) {
        if (rung >= 0 && rung < RUNGS.length) notes[rung] = text;
    }

    /** Records spending against a rung for this month (d.rungSpent, cleared when the month ends). */
    public static void spent(int rung, float credits) {
        if (rung < 0 || rung >= RUNGS.length || !(credits > 0f) || CALog.whatIf()) return;
        try {
            Map<String, Float> m = CAData.get().rungSpent();
            Float v = m.get(RUNGS[rung]);
            m.put(RUNGS[rung], (v == null ? 0f : v) + credits);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "rung spending record failed", t); }
    }

    /**
     * Daily from CAAuto (step "money tiers"): works out today's tier if nothing asked yet, which logs a change, then runs
     * the rungs bought here (0.3.0.0, C3). (C4: surplus terraforming under the 24-month payback line may use the month's
     * budget: CATerraformSurplus.action sets fromBudget and chargeAll.)
     */
    public static void run(CAData d, CASettings s) {
        tier();
        // 0.3.0.0 (C3): goods bought for Local Resources arrive, and the shipyard's free cash guard runs, whatever the ladder does now
        try { CAStockpile.deliver(d); } catch (Throwable t) { CAStep.warn("money tiers", "stockpile deliveries failed", t); }
        try { CAShipyard.guard(d, s); } catch (Throwable t) { CAStep.warn("money tiers", "shipyard free cash check failed", t); }
        if (CALog.whatIf()) return;
        // 0.3.0.0 (C3 fix 2): a proposal whose rung the ladder doesn't run now (legacyPlanner, or a preset without the rung)
        // can't stay on the Budget panel for Approve
        try { dropProposals(s); } catch (Throwable t) { CAStep.warn("money tiers", "dropping closed rungs' proposals failed", t); }
        if (CABudget.legacy(s)) return;
        // 0.3.0.0 (C3 fix): Growth is hazard pay, paid by the game from income at month end; its cost a month is the rung's want,
        // so the rung's share holds it and the rest goes down (CAPolicies.growthWant)
        if (open(GROWTH)) want(GROWTH, CAPolicies.growthWant(s));
        // 0.3.0.0 (C3 fix 2, DECISIONS 14b): the Economy rung's buyers spend surplus credits and the month's budget outside the
        // pool (CAEconomy, C4). Its want is 0 every day (want() takes no other value for it), so its share goes down to
        // Wonders and the rungs after it. CAEconomy.outsidePool's note (with why none starts) is kept when it's there
        if (open(ECONOMY)) {
            setWant(ECONOMY, 0f);
            if (notes[ECONOMY] == null) note(ECONOMY, "surplus research, terraforming and trade buildings use surplus credits and the month's budget, outside the pool");
        }
        // 0.3.0.0 (C3 fix): wonders and the endgame's replacements start only with the Wonders rung On (wondersOpen)
        if (open(WONDERS)) note(WONDERS, mode(WONDERS) == RECOMMEND ? "Recommend only: no wonder or endgame replacement starts; set the rung On to start them" : null);
        // 0.3.0.0 (C3): the rungs bought here, in the preset's order; expansion (CAExpansion) and war fleets (CAWar) read
        // allowance() in their own steps. 0.3.0.0 (C3 fix): each buyer here spends from its rung's room; what it says it wants
        // decides what goes down
        for (int r : order(s.richPreset)) {
            try {
                if (r == STOCKPILES) CAStockpile.run(d, s, room(STOCKPILES), false);
                else if (r == MILITARY) {
                    // 0.3.0.0 (C3 fix): goods and upgrades fail apart, so one failing doesn't skip the other
                    try { CAStockpile.run(d, s, room(MILITARY), true); } catch (Throwable t) { CAStep.warn("money tiers", "Military goods failed", t); }
                    CAMilitary.run(d, s, room(MILITARY));
                }
                else if (r == SHIPYARD) CAShipyard.run(d, s, room(SHIPYARD));
            } catch (Throwable t) { CAStep.warn("money tiers", RUNGS[r] + " rung failed", t); }
            // 0.3.0.0 (C3 fix): the Military rung's one want (goods, the cheapest upgrade that fits, a guard fleet due), so what it
            // doesn't need goes down to the Shipyard and the rungs after it
            if (r == MILITARY && open(MILITARY)) want(MILITARY, militaryWant());
        }
    }

    /** Pure, 0.3.0.0 (C3 fix 2): whether the ladder runs a rung's buyer: not legacyPlanner, and the rung in the preset's order. */
    static boolean runsBuyer(int rung, int preset, boolean legacy) {
        if (legacy) return false;
        for (int r : order(preset)) if (r == rung) return true;
        return false;
    }

    /**
     * 0.3.0.0 (C3 fix 2): drops the proposals and wants of the rungs bought here (Stockpiles, Military, Shipyard) whose buyer
     * the ladder doesn't run now (runsBuyer). run() calls a buyer only for the rungs in the preset's order and none with
     * legacyPlanner, and a buyer drops its own proposal only when it's called, so a proposal from before a preset change or
     * the legacyPlanner switch stayed on the Budget panel with Approve.
     */
    static void dropProposals(CASettings s) {
        boolean legacy = CABudget.legacy(s);
        if (!runsBuyer(STOCKPILES, s.richPreset, legacy)) {
            CAStockpile.pending[0] = null;
            CAStockpile.lastWant[0] = 0f;
            note(STOCKPILES, null);
        }
        if (!runsBuyer(MILITARY, s.richPreset, legacy)) {
            CAStockpile.pending[1] = null;
            CAStockpile.lastWant[1] = 0f;
            CAMilitary.pending = null;
            CAMilitary.lastWant = 0f;
            note(MILITARY, null);
        }
        if (!runsBuyer(SHIPYARD, s.richPreset, legacy)) {
            CAShipyard.pending = null;
            note(SHIPYARD, null);
        }
    }

    /**
     * A Budget panel button of the ladder's (Approve or Reject a shipyard, stockpile or military proposal). Returns a message
     * for the log, "" when it was handled and logged already, or null when the id isn't one of these.
     */
    public static String handle(Object buttonId) {
        if (!(buttonId instanceof String)) return null;
        String k = (String) buttonId;
        if (k.startsWith(CAShipyard.KEY)) return CAShipyard.handle(k);
        if (k.startsWith(CAStockpile.KEY)) return CAStockpile.handle(k);
        if (k.startsWith(CAMilitary.KEY)) return CAMilitary.handle(k);
        return null;
    }

    /**
     * 0.3.0.0 (C2, for CAHorizon): the credits the payback horizon slides on. The surplus over the line as in B2; at Idle
     * money and up the free cash when it's more, so builds at every colony may pay back over a longer time.
     */
    public static float horizonCredits(CASettings s) {
        float a = CASurplus.amount(s);
        if (CABudget.legacy(s) || tier() < T2 || Float.isNaN(lastFree)) return a;
        return Math.max(a, lastFree);
    }

    // ---------------- panels (wired by WS-F) ----------------

    /** The Budget panel's tier line, and this month's ladder spending when there is any. */
    public static void render(TooltipMakerAPI t) {
        if (t == null) return;
        int tr = tier();
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        int idle = 0;
        try { idle = CAData.get().idleMonths; } catch (Throwable ignored) { }
        if (Float.isNaN(lastFree)) {
            t.addPara("Money tier: %s.", 4f, hl, name(tr));
        } else {
            float months = lastFree / Math.max(CABudget.NET_FLOOR, lastNet);
            t.addPara("Money tier: %s. Free cash %s (%s months of colony net), idle %s " + (idle == 1 ? "month." : "months."), 4f, hl,
                    name(tr), Misc.getDGSCredits(lastFree), String.format(java.util.Locale.ROOT, "%.1f", months), String.valueOf(idle));
            CATheme.hover(t, "Free cash: credits less the reserve (" + Misc.getDGSCredits(reserve()) + "), production to pay ("
                    + Misc.getDGSCredits(lastCommitted) + ") and two months of costs (" + Misc.getDGSCredits(lastRunway) + ").");   // 0.3.0.0 (WS-F3 fix pass 2)
        }
        // 0.3.0.0 (C3): the month's pool, what each open rung may still spend, and each rung's note
        try {
            String ladder = ladderText();
            if (ladder != null) t.addPara(CALog.esc(ladder), gray, 2f);
            for (int r = 1; r < RUNGS.length; r++) if (notes[r] != null) t.addPara(CALog.esc(RUNGS[r] + ": " + notes[r]), gray, 2f);
        } catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "ladder line failed", e); }
        String spent = spentText();
        if (spent != null) t.addPara(CALog.esc(spent), gray, 2f);
    }

    /**
     * 0.3.0.0 (C3): "Ladder (Balanced): 595,000¢ this month, 35% of free cash. Left: Stockpiles 59,500¢, Military 119,000¢,
     * Shipyard 148,750¢ (recommend only)." Null with no save; a short line at Tight or with legacyPlanner.
     */
    static String ladderText() {
        CASettings s;
        try { s = CAData.settings(); } catch (Throwable t) { return null; }
        if (CABudget.legacy(s)) return "Ladder: off (the 0.2.45 engine is on).";
        int tr = tier(), p = Math.max(0, Math.min(CASettings.RICH_PRESET_NAMES.length - 1, s.richPreset));
        String preset = CASettings.RICH_PRESET_NAMES[p];
        float pool = pool();
        if (!(pool > 0f)) return "Ladder (" + preset + "): nothing to spend at " + name(tr) + ".";
        float[] a = allowances();
        List<String> parts = new ArrayList<String>();
        for (int r : order(p)) {
            if (!open(r, tr, s.rungMode(r), p, false, shipyardEarly(s))) continue;
            parts.add(RUNGS[r] + " " + Misc.getDGSCredits(a[r]) + (s.rungMode(r) == RECOMMEND ? " (recommend only)" : ""));
        }
        return "Ladder (" + preset + "): " + Misc.getDGSCredits(pool) + " this month, " + Math.round(DRAW[clamp(tr)] * 100f) + "% of free cash."
                + (parts.isEmpty() ? " No rung is open." : " Left: " + CAContracts.join(parts) + ".");
    }

    /**
     * "This month: Stockpiles 40,000¢, Growth 41,000¢ (hazard pay)." or null when the ladder spent nothing yet. 0.3.0.0 (C3
     * fix): Growth is the hazard pay a month at your Auto colonies (paid by the game at month end) while the rung is open.
     */
    static String spentText() {
        Map<String, Float> m;
        try { m = CAData.get().rungSpent(); } catch (Throwable t) { return null; }
        List<String> parts = new ArrayList<String>();
        for (int i = 0; i < RUNGS.length; i++) {
            Float v = m.get(RUNGS[i]);
            if (v != null && v > 0f) parts.add(RUNGS[i] + " " + Misc.getDGSCredits(v));
            else if (i == GROWTH && wantMonth[GROWTH] == monthKey() && wants[GROWTH] > 0f) parts.add(RUNGS[i] + " " + Misc.getDGSCredits(wants[GROWTH]) + " (hazard pay)");
        }
        return parts.isEmpty() ? null : "This month: " + CAContracts.join(parts) + ".";
    }

    static float reserve() {
        try { return CAData.settings().reserve; } catch (Throwable t) { return 0f; }
    }
}
