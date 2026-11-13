package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.Misc;

/**
 * The monthly budget, caps and convenience fees. Fees are fixed vanilla-scale amounts, not scaled by income.
 * 0.3.0.0 (WS-B5, research 03 F4, 01 sections 1 and 7): two numbers instead of one. Colony net (netIncome) is what the
 * colonies themselves made, the monthly report's Colonies node without custom production (vanilla's and Ashes of the
 * Domain's orders) or your fleet's draw on colony stockpiles, and without hazard pay (a choice Auto Colonies makes, judged
 * on its own; DECISIONS 14), as the median of the last 3 months. It sets the budget, the caps, hazard pay, the endgame
 * and the money emergency, so one bad month or a ship order no longer moves them all (a -187,821 month switched hazard
 * pay off at every colony and cut the savings by 1.8M). Cash flow (cashFlow, the whole report as before) is only for
 * "can we afford it". The savings no longer throw unspent budget away while the credits are really there: past 6 months'
 * worth they keep what's above your credit reserve, so the planner's one pool can still spend it.
 * 0.3.0.0 (WS-C1, research 03 F3 and section 5.1): the money ladder's inputs. Free cash (freeCash) is credits minus the
 * reserve, what's already promised (committed) and a runway of two months of the colonies' gross upkeep and your fleet's
 * monthly cost (runway). pay() and refund() move credits; charge() takes only the fee where the price was taken with
 * payPrice() (CABuild.start). All of them go through take() and give(), the only lines here that touch your credits, and
 * no file outside CABudget moves credits (0.3.0.0, C12 fix; JTRichCheck). At each month's end
 * rollMonth hands last month's spending and budget to CARich.monthEnded for the idle-month count.
 */
public class CABudget {

    /** Resets the month's spending when the in-game month changes, and records last month's net income. */
    public static void rollMonth(CASettings s) {
        int m = Global.getSector().getClock().getMonth();
        if (s.month == m) return;
        boolean first = s.month < 0;
        // what's left of the month's budget is saved up for next month (0.2.39.0): every building that cost more than one
        // month's budget waited until savings had grown enough by themselves, months with credits piling up and nothing built.
        // 0.3.0.0 (B5, 01 section 1): past CARRY_MONTHS budgets it keeps what the credits above your reserve really cover,
        // instead of throwing it away (the 6-month cap dropped up to 19.9M of budget room in the author's game)
        java.util.List<Float> hist = null;
        try { hist = CAData.get().colonyNetHistory(); } catch (Throwable ignored) { }
        int had = hist == null ? 0 : hist.size();   // 0.3.0.0 (B5): limit() may seed an empty history from this same report
        float before = s.budgetCarry, endedLimit = first ? 0f : limit(s);
        if (!first) s.budgetCarry = legacy(s) ? carried(endedLimit, s.budgetCarry, s.spent)
                : carried(endedLimit, s.budgetCarry, s.spent, CABuild.credits() - s.reserve);
        float overCap = first ? 0f : Math.max(0f, s.budgetCarry - CARRY_MONTHS * endedLimit);
        // 0.3.0.0: last month's Auto Colonies spending, read before the reset so the colony lines can log it (research 01 section 5)
        float lastSpent = s.spent, lastFees = s.fees, lastUpkeep = s.upkeepAdded;
        s.month = m;
        s.spent = 0f; s.fees = 0f; s.upkeepAdded = 0f; s.terraSpent = 0f; s.spMonth = 0; s.endgameLoss = 0f;
        s.monthCredits = CABuild.credits();
        float month = Float.NaN;
        try {
            MonthlyReport prev = SharedData.getData().getPreviousReport();
            if (prev != null && prev.getRoot() != null) {
                prev.computeTotals();
                s.lastNet = prev.getRoot().totalIncome - prev.getRoot().totalUpkeep;   // cash flow: the whole report
                s.lastNetKnown = true;
                month = monthNet(prev);   // 0.3.0.0 (B5): the colonies' own net, for the 3-month median
            }
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "could not read last month's report", t);
        }
        if (!first && !Float.isNaN(month) && hist != null && hist.size() == had) {   // not twice when limit() just seeded it from this report
            try { record(hist, month); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony net history failed", t); }
        }
        if (!first && !Float.isNaN(month) && s.lastNetKnown) rebaseEndgame(s, month, s.lastNet);   // 0.3.0.0 (B5 fix): once, if the seeding below couldn't
        // 0.3.0.9: monthly status, starsector.log only
        if (!first) CALog.quiet("Month: net " + Misc.getDGSCredits(netIncome(s)) + ", budget " + Misc.getDGSCredits(limit(s))
                + (s.budgetCarry > 0f ? " plus " + Misc.getDGSCredits(s.budgetCarry) + " saved up" : ""));
        // 0.3.0.0 (B5): the numbers behind the Month line, starsector.log only
        if (!first) CALog.quiet("Month detail: colony net " + (Float.isNaN(month) ? "unknown" : Misc.getDGSCredits(month)) + " last month, "
                + Misc.getDGSCredits(netIncome(s)) + " as the " + NET_MONTHS + "-month median; cash flow " + Misc.getDGSCredits(s.lastNet)
                + "; savings " + Misc.getDGSCredits(before) + " -> " + Misc.getDGSCredits(s.budgetCarry)
                + (overCap > 0f ? " (" + Misc.getDGSCredits(overCap) + " of it past " + Math.round(CARRY_MONTHS) + " months, covered by credits over the reserve)" : ""));
        if (!first) CALog.colonyMonth(lastSpent, lastFees, lastUpkeep);   // 0.3.0.0: one money line per colony in starsector.log, with what Auto Colonies spent there (research 01 section 5)
        // 0.3.0.0 (C1/C2): the ended month counts toward the money tiers' idle months (its budget without savings, what Auto Colonies spent)
        if (!first) {
            try { CARich.monthEnded(s, lastSpent, endedLimit); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "idle month count failed", t); }
        }
    }

    /** Unspent budget is saved up for at most this many months' worth. */
    public static final float CARRY_MONTHS = CATuning.CARRY_MONTHS;   // 0.3.0.0 (A3): numbers in CATuning

    /** What's saved up after a month with this budget, savings and spending: what's left, never below 0 or above CARRY_MONTHS budgets. Pure. */
    static float carried(float limit, float carry, float spent) {
        return Math.max(0f, Math.min(CARRY_MONTHS * limit, limit + carry - spent));
    }

    /**
     * 0.3.0.0 (B5, research 01 section 1): what's saved up when the credits above the reserve (spendable) are known: what's
     * left, never below 0; past CARRY_MONTHS budgets only as much as spendable covers, so unspent budget that's really in
     * the account stays in the pool and only budget room no credits stand behind is dropped. Pure.
     */
    static float carried(float limit, float carry, float spent, float spendable) {
        float left = limit + carry - spent;
        return Math.max(0f, Math.min(left, Math.max(CARRY_MONTHS * limit, spendable)));
    }

    /** 0.3.0.0 (B5): the hidden switch back to 0.2.45's engine also brings back its 6-month savings cap. */
    static boolean legacy(CASettings s) { return s != null && s.legacyPlanner; }

    /** Budget saved up from earlier months (0.2.39.0). */
    public static float saved(CASettings s) { return s.budgetCarry; }

    // ---------------- 0.3.0.0 (WS-B5): colony net (research 03 F4, 01 section 7) ----------------

    /** Months the colony net median looks back over. */
    public static final int NET_MONTHS = CATuning.NET_MONTHS;   // 0.3.0.0 (B5): the numbers live in CATuning
    /**
     * Colony net as a scale never goes under this (research 03 section 5.1): what "months of colony net" are counted in
     * (netScale, for the money tiers), so a tiny or negative month doesn't make a pile of credits look like years of
     * income. Budgets, caps and pause on loss use the median itself, so a losing empire still pauses as in 0.2.45.
     */
    public static final float NET_FLOOR = CATuning.NET_FLOOR;
    /** Ashes of the Domain's custom production node under Colonies (AoTDProductionOrderData.getMonthlyReportNodeForOrder). */
    static final String AOTD_PRODUCTION = "production";
    /** Hazard pay's node under each colony's node (CoreScript.reportEconomyMonthEnd). */
    static final String HAZARD_PAY = "incentives";
    /**
     * Children of the Colonies node that aren't the colonies' own money: custom production (vanilla's ships and their
     * weapons, AotD's orders) and what your fleet draws from colony stockpiles. Each is a choice you make, not income.
     */
    static final String[] NOT_COLONY = {MonthlyReport.PRODUCTION, MonthlyReport.PRODUCTION_WEAPONS, AOTD_PRODUCTION, MonthlyReport.RESTOCKING};

    /**
     * Colony net, the number budgets, caps, hazard pay, the endgame and emergencies run on (0.3.0.0, B5): the median of the
     * last NET_MONTHS months of colony net (colonyNet). Before the first month is on record (a new game, or a save from
     * before 0.3.0 until its next month) last month's report is read now; failing that, last month's whole net, and before
     * any month this month's colony net income, as 0.2.45 did.
     */
    public static float netIncome(CASettings s) {
        Float n = colonyNet();
        if (n != null) return n;
        if (s.lastNetKnown) return s.lastNet;
        float sum = 0f;
        try { for (MarketAPI m : Misc.getPlayerMarkets(false)) sum += m.getNetIncome(); } catch (Throwable ignored) { }
        return sum;
    }

    /** The colony net median, or null when none is on record and last month's report can't be read (offline: always null). */
    static Float colonyNet() {
        java.util.List<Float> h;
        try { h = CAData.get().colonyNetHistory(); } catch (Throwable t) { return null; }
        if (h.isEmpty() && !CALog.whatIf()) {
            try {   // a save from before 0.3.0, or the first month: last month's report, once
                if (CAData.settings().lastNetKnown) {
                    MonthlyReport prev = SharedData.getData().getPreviousReport();
                    if (prev != null && prev.getRoot() != null) {
                        prev.computeTotals();
                        float month = monthNet(prev);
                        record(h, month);
                        // 0.3.0.0 (B5 fix): before the endgame reads its room against colony net, its 0.2.45 reference moves to it
                        if (!Float.isNaN(month)) rebaseEndgame(CAData.settings(), month, prev.getRoot().totalIncome - prev.getRoot().totalUpkeep);
                    }
                }
            } catch (Throwable ignored) { }
        }
        return h.isEmpty() ? null : median(h, NET_MONTHS);
    }

    // ---------------- 0.3.0.0 (WS-B5 fix): the endgame's reference moves to colony net, once ----------------
    // 0.2.45 recorded the endgame's reference (CASettings.endgameRefNet) as the whole report's net, your fleet and custom
    // production included, and kept that net over a third of it. 0.3.0 compares the reference with colony net, which leaves
    // those out and so reads higher by them: on a save that had the endgame on, the floor stayed a third of the old, lower
    // number and the endgame could give up that much more income a month (reference 600,000, fleet 300,000: its room went
    // from 400,000 to 700,000). The first time a save from before 0.3.0 reads a month's colony net, a reference it has moves
    // once to colony net, so its floor still keeps 0.2.45's promise on the whole net: the floor rises by last month's gap
    // between colony net and whole net. It never lowers a reference, and a save whose endgame never ran (reference 0) or a
    // save started on 0.3.0 is left alone. Marked done with a CAMark stamp (save safety: a key and a timestamp).
    // 0.3.0.0 (B5 fix 2): the stamp goes on the first month a save from before 0.3.0 reads, reference or not. Before, a save
    // whose endgame never ran (reference 0) was never stamped, so when the endgame first started on 0.3.0 (its reference
    // already colony net) the next month moved it up by the whole gap / keep, cutting the endgame's room for good.

    /** CAMark key: this save's endgame reference was moved to colony net. */
    static final String ENDGAME_REBASED = "endgameref3";

    /**
     * Pure: a 0.2.45 endgame reference (whole net) in colony net units, keeping its floor (keep x reference) on whole net:
     * the floor rises by gap (colony net minus whole net last month), so the reference by gap / keep. Only a positive gap
     * moves it; no reference, no change.
     */
    static float rebasedEndgameRef(float refWhole, float gap, float keep) {
        if (!(refWhole > 0f) || !(gap > 0f) || !(keep > 0f)) return refWhole;
        return refWhole + gap / keep;
    }

    /** Pure: whether the move applies: a save moved from before 0.3.0 (migrated), with a reference, not moved yet. */
    static boolean rebaseDue(boolean migrated, float refNet, boolean done) { return migrated && refNet > 0f && !done; }

    /** Pure (0.3.0.0 B5 fix 2): whether this month stamps the move done: any save from before 0.3.0 not stamped yet, reference or not. */
    static boolean rebaseStamps(boolean migrated, boolean done) { return migrated && !done; }

    /** Pure (0.3.0.0 B5 fix 2): the reference after one month's look: moved when due, else as it was. */
    static float rebaseMonth(boolean migrated, float refNet, boolean done, float gap, float keep) {
        return rebaseDue(migrated, refNet, done) ? rebasedEndgameRef(refNet, gap, keep) : refNet;
    }

    /** Once per save from before 0.3.0: moves its endgame reference to colony net with a month's colony net and whole net. */
    static void rebaseEndgame(CASettings s, float colonyMonth, float wholeMonth) {
        if (s == null || CALog.whatIf() || Float.isNaN(colonyMonth) || Float.isNaN(wholeMonth)) return;
        try {
            boolean migrated = CAData.get().migratedAt != 0L, done = CAMark.at(ENDGAME_REBASED) != null;
            if (!rebaseStamps(migrated, done)) return;
            float was = s.endgameRefNet;
            s.endgameRefNet = rebaseMonth(migrated, was, done, colonyMonth - wholeMonth, CATuning.ENDGAME_KEEP);
            CAMark.set(ENDGAME_REBASED, CAMark.now());   // 0.3.0.0 (B5 fix 2): stamped with or without a reference, so it's once for good
            if (s.endgameRefNet != was)
                CALog.quiet("endgame reference moved to colony net: " + Misc.getDGSCredits(was) + " -> " + Misc.getDGSCredits(s.endgameRefNet)
                        + " (colony net " + Misc.getDGSCredits(colonyMonth) + ", whole net " + Misc.getDGSCredits(wholeMonth) + " last month), so it keeps 0.2.45's floor");
            else CALog.quiet("endgame reference: " + (was > 0f ? "no gap to move" : "none from 0.2.45") + ", left as is; any set from now on is colony net");
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "endgame reference move failed", t); }
    }

    /** Last month's whole net (the report's total, your fleet and production included): for affordability and runway only. */
    public static float cashFlow(CASettings s) { return s.lastNetKnown ? s.lastNet : netIncome(s); }

    /** Colony net as a scale for "months of it" (the money tiers): the median, never under NET_FLOOR. */
    public static float netScale(CASettings s) { return Math.max(NET_FLOOR, netIncome(s)); }

    /** Adds a month to the history (newest last), keeping NET_MONTHS; NaN (no Colonies node) adds nothing. */
    static void record(java.util.List<Float> h, float n) {
        if (h == null || Float.isNaN(n) || Float.isInfinite(n)) return;
        h.add(n);
        while (h.size() > NET_MONTHS) h.remove(0);
    }

    /** Pure: the median of the newest `months` entries (newest last); the mean of the middle two for an even count; 0 for none. */
    static float median(java.util.List<Float> h, int months) {
        int n = h == null ? 0 : Math.min(months, h.size());
        if (n <= 0) return 0f;
        float[] v = new float[n];
        for (int i = 0; i < n; i++) { Float x = h.get(h.size() - n + i); v[i] = x == null ? 0f : x; }
        java.util.Arrays.sort(v);
        return n % 2 == 1 ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2f;
    }

    /** A month's colony net from its report (totals computed), or NaN when the report has no Colonies node yet. */
    static float monthNet(MonthlyReport r) {
        MonthlyReport.FDNode col = r.getRoot() == null ? null : r.getRoot().getChildren().get(MonthlyReport.OUTPOSTS);
        return col == null ? Float.NaN : colonyNet(col);
    }

    /**
     * Pure (on a report whose totals are computed): the Colonies node's net without the NOT_COLONY children, with each
     * colony's hazard pay added back.
     */
    static float colonyNet(MonthlyReport.FDNode colonies) {
        float n = colonies.totalIncome - colonies.totalUpkeep;
        for (java.util.Map.Entry<String, MonthlyReport.FDNode> e : colonies.getChildren().entrySet()) {
            MonthlyReport.FDNode c = e.getValue();
            if (c == null) continue;
            if (java.util.Arrays.asList(NOT_COLONY).contains(e.getKey())) { n -= c.totalIncome - c.totalUpkeep; continue; }
            for (java.util.Map.Entry<String, MonthlyReport.FDNode> k : c.getChildren().entrySet()) {
                MonthlyReport.FDNode x = k.getValue();
                if (x != null && (HAZARD_PAY.equals(k.getKey()) || MonthlyReport.INCENTIVES.equals(x.custom))) n += x.totalUpkeep - x.totalIncome;
            }
        }
        return n;
    }

    public static float limit(CASettings s) {
        if (s.budgetAuto) return Math.max(0f, netIncome(s) * s.budgetPercent) + fromSavings(s);
        if (!s.budgetPercentMode) return s.budgetFixed;
        return Math.max(0f, netIncome(s) * s.budgetPercent);
    }

    /** The Automatic budget's share of savings: savingsShare of the credits above the reserve at the start of the month. */
    public static float fromSavings(CASettings s) {
        float start = s.monthCredits > 0f ? s.monthCredits : CABuild.credits();
        float share = s.savingsShare > 0f ? s.savingsShare : 0.15f;
        return Math.max(0f, start - s.reserve) * share;
    }

    public static boolean paused(CASettings s) { return s.pauseOnLoss && netIncome(s) < 0f; }

    /** Credits the manager may still spend this month. */
    public static float available(CASettings s) {
        if (paused(s)) return 0f;
        float byBudget = limit(s) + s.budgetCarry - s.spent;
        float byReserve = CABuild.credits() - s.reserve;
        return Math.max(0f, Math.min(byBudget, byReserve));
    }

    /** The Automatic new-upkeep cap: this share of last month's net income, never under the floor. */
    static final float UPKEEP_SHARE = CATuning.UPKEEP_SHARE, UPKEEP_FLOOR = CATuning.UPKEEP_FLOOR;

    static float autoUpkeepCap(float net) { return autoUpkeepCap(net, UPKEEP_SHARE); }

    /** 0.3.0.0 (WS-F1): the share comes from the Spending preset (CASettings.upkeepShare); the floor scales with it, 200,000 x share (10,000 / 20,000 / 40,000). */
    static float autoUpkeepCap(float net, float share) {
        float sh = share > 0f ? share : UPKEEP_SHARE;
        return Math.max(UPKEEP_FLOOR * sh / UPKEEP_SHARE, sh * Math.max(0f, net));
    }

    /**
     * New upkeep Auto may add this month: Automatic, 10% of last month's net income and at least 20,000, so it keeps up
     * as income grows (a fixed number set at setup went stale: 20,000 against 760,000 a month held a plan step back for
     * seven months); or your Fixed amount.
     */
    public static float upkeepCap(CASettings s) { return s.upkeepCapAuto ? autoUpkeepCap(netIncome(s), s.upkeepShare) : s.newUpkeepCap; }

    /** A save from before 0.2.24.0 had a fixed cap, set once at setup; it becomes Automatic once, with a log line. */
    static void migrateUpkeepCap(CASettings s) {
        if (s.upkeepCapChecked) return;
        s.upkeepCapChecked = true;
        if (s.upkeepCapAuto) return;
        s.upkeepCapAuto = true;
        CALog.add("New upkeep cap per month is Automatic now: 10% of last month's net income, at least 20,000 (" + Misc.getDGSCredits(upkeepCap(s))
                + " now; it was a fixed " + Misc.getDGSCredits(s.newUpkeepCap) + "). Settings, Advanced, Budget can set a Fixed cap again", true);
    }

    /**
     * New upkeep fits this month: within the cap, or a plan step whose upkeep alone is over the whole cap, in a month nothing
     * else has added upkeep, so it waits a month at most rather than forever.
     */
    static boolean upkeepFits(float added, float upkeep, float cap, boolean planStep) {
        if (added + upkeep <= cap) return true;
        return planStep && upkeep > cap && added <= 0f;
    }

    public static float constructionFee(CASettings s, float cost) {
        return Math.max(CATuning.FEE_FLOOR, CATuning.FEE_SHARE * cost) * CASettings.FEE_MULT[Math.max(0, Math.min(3, s.feeLevel))];
    }

    public static float fee(CASettings s, float standard) {
        return standard * CASettings.FEE_MULT[Math.max(0, Math.min(3, s.feeLevel))];
    }

    public static void charge(CASettings s, float cost, float fee, float upkeep) { charge(s, null, cost, fee, upkeep); }

    /**
     * Charges the fee and counts the spending against the month. 0.3.0.0 (research 01 section 5): also booked in the month's
     * ledger under that colony (marketId null: not one colony), for the monthly colony lines (CALog.spent, colonyMonth).
     */
    public static void charge(CASettings s, String marketId, float cost, float fee, float upkeep) {
        charge(s, marketId, cost, fee, upkeep, upkeep);
    }

    /**
     * 0.3.0.0 (B3, research 02 section 3): as charge(), but only capUpkeep counts against the month's new-upkeep cap (the planner
     * counts net risk: the upkeep a building doesn't cover itself); the ledger still books the full upkeep.
     */
    public static void charge(CASettings s, String marketId, float cost, float fee, float upkeep, float capUpkeep) {
        take(fee);   // 0.3.0.0 (C1): through the one line that takes credits
        book(s, marketId, cost, fee, upkeep, capUpkeep);
    }

    /** Counts a payment against the month's budget, fees and new-upkeep cap, and books it in the month's ledger. */
    static void book(CASettings s, String marketId, float cost, float fee, float upkeep, float capUpkeep) {
        s.spent += cost + fee;
        s.fees += fee;
        s.upkeepAdded += Math.max(0f, capUpkeep);
        CALog.spent(marketId, cost + fee, fee, Math.max(0f, upkeep));
    }

    // ---------------- 0.3.0.0 (WS-C1, research 03 F3): paying ----------------
    // 0.2.45.0 charged every Autopilot Storage shipment's fee and every agent's recruiting fee twice: the caller subtracted the
    // fee and charge() subtracted it again (0.2.45.1 removed those subtractions). pay() takes price and fee in one call, so a
    // caller has nothing left to subtract itself. 0.3.0.0 (C12 fix): credits move only here. CABuild.start takes a build's
    // price with payPrice() (it alone knows whether the build took effect) and its callers book it and take the fee with
    // charge() or payFromPool(), as before. JTRichCheck fails any file outside CABudget that moves credits, and any method
    // that moves credits itself and also pays through CABudget.

    /** The one line that takes credits from you (nothing for 0 or less). */
    static void take(float credits) {
        if (credits > 0f) Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(credits);
    }

    /** The one line that gives credits back (nothing for 0 or less). */
    static void give(float credits) {
        if (credits > 0f) Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);
    }

    /** Pays a price and its fee from your credits and counts both against the month (marketId null: not one colony). */
    public static void pay(CASettings s, String marketId, float price, float fee, float upkeep) { pay(s, marketId, price, fee, upkeep, upkeep); }

    /** As pay(), with only capUpkeep counted against the month's new-upkeep cap (the planner's net risk, as charge()). */
    public static void pay(CASettings s, String marketId, float price, float fee, float upkeep, float capUpkeep) {
        float p = Math.max(0f, price), f = Math.max(0f, fee);
        take(p + f);
        book(s, marketId, p, f, upkeep, capUpkeep);
    }

    /**
     * 0.3.0.0 (C12 fix, research 03 F3): takes a price whose caller books it: CABuild.start, which pays a build's price only
     * once the build took effect; the start's caller then books it and takes the fee with charge() (or payFromPool() with
     * price 0). Nothing for 0 or less. Where price and fee are known in one place, pay() is the call.
     */
    public static void payPrice(float price) {
        take(price);
    }

    /**
     * Pays for a money-ladder purchase (rung: a CARich.RUNGS index): price and fee leave your credits, the fee and the
     * colony's ledger are booked as for any payment, and the rung's spending is recorded (CARich.spent). It's drawn from the
     * ladder's monthly pool, so it isn't counted against the month's budget (s.spent) or its new-upkeep cap; a purchase that
     * adds upkeep (a building) is paid with pay() like any build.
     */
    public static void payFromPool(CASettings s, int rung, String marketId, float price, float fee) {
        float p = Math.max(0f, price), f = Math.max(0f, fee);
        take(p + f);
        s.fees += f;
        CALog.spent(marketId, p + f, f, 0f);
        CARich.spent(rung, p + f);
    }

    /**
     * Gives credits back: an order that couldn't be carried out. The month's spending isn't lowered (as before 0.3.0); the
     * credits land in your account, where the reserve test and free cash see them. The caller logs what came back.
     */
    public static void refund(CASettings s, String marketId, float credits) {
        give(credits);
    }

    // ---------------- 0.3.0.0 (WS-C1, research 03 section 5.1): free cash and runway ----------------

    /** Months of the colonies' gross upkeep and your fleet's monthly cost free cash keeps back. */
    public static final float RUNWAY_MONTHS = 2f;

    /**
     * Credits already promised: the game's custom production still to build and Ashes of the Domain's orders still to
     * finish (CASurplus.committed). Auto Colonies' own ship orders sit in those queues and are counted there; shipments in
     * transit and expeditions were paid when they left, so nothing more is owed for them.
     */
    public static float committed() { return CASurplus.committed(); }

    /** Pure: free cash, credits less the reserve, what's promised and the runway (may be below 0). */
    static float freeCash(float credits, float reserve, float committed, float runway) {
        return credits - Math.max(0f, reserve) - Math.max(0f, committed) - Math.max(0f, runway);
    }

    /** Free cash now. */
    public static float freeCash(CASettings s) { return freeCash(CABuild.credits(), s.reserve, committed(), runway(s)); }

    /** Pure: the runway, RUNWAY_MONTHS of the colonies' gross upkeep and the fleet's monthly cost. */
    static float runwayOf(float coloniesUpkeep, float fleetCost) {
        return RUNWAY_MONTHS * (Math.max(0f, coloniesUpkeep) + Math.max(0f, fleetCost));
    }

    /**
     * The runway from last month's report: the Colonies node's upkeep without custom production and your fleet's stockpile
     * draw (NOT_COLONY), and the fleet node's cost (crew, officers, marines). Before the first report, the colonies'
     * building upkeep now.
     */
    public static float runway(CASettings s) {
        try {
            MonthlyReport prev = SharedData.getData().getPreviousReport();
            MonthlyReport.FDNode root = prev == null ? null : prev.getRoot();
            MonthlyReport.FDNode col = root == null ? null : root.getChildren().get(MonthlyReport.OUTPOSTS);
            if (col != null) {
                prev.computeTotals();
                return runwayOf(coloniesUpkeep(col), fleetCost(root.getChildren().get(MonthlyReport.FLEET)));
            }
        } catch (Throwable ignored) { }
        float up = 0f;
        try { for (MarketAPI m : Misc.getPlayerMarkets(false)) up += Math.max(0f, m.getIndustryUpkeep()); } catch (Throwable ignored) { }
        return runwayOf(up, 0f);
    }

    /** Pure (on a report whose totals are computed): the Colonies node's upkeep without the NOT_COLONY children's. */
    static float coloniesUpkeep(MonthlyReport.FDNode colonies) {
        if (colonies == null) return 0f;
        float u = colonies.totalUpkeep;
        for (java.util.Map.Entry<String, MonthlyReport.FDNode> e : colonies.getChildren().entrySet()) {
            MonthlyReport.FDNode c = e.getValue();
            if (c != null && java.util.Arrays.asList(NOT_COLONY).contains(e.getKey())) u -= c.totalUpkeep;
        }
        return Math.max(0f, u);
    }

    /** Pure (totals computed): the fleet node's monthly cost, upkeep less income, never below 0; 0 without the node. */
    static float fleetCost(MonthlyReport.FDNode fleet) {
        return fleet == null ? 0f : Math.max(0f, fleet.totalUpkeep - fleet.totalIncome);
    }
}
