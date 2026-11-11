package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.Misc;

/** The monthly budget, caps and convenience fees. Fees are fixed vanilla-scale amounts, not scaled by income. */
public class CABudget {

    /** Resets the month's spending when the in-game month changes, and records last month's net income. */
    public static void rollMonth(CASettings s) {
        int m = Global.getSector().getClock().getMonth();
        if (s.month == m) return;
        boolean first = s.month < 0;
        // what's left of the month's budget is saved up for next month (0.2.39.0): every building that cost more than one
        // month's budget waited until savings had grown enough by themselves, months with credits piling up and nothing built
        if (!first) s.budgetCarry = carried(limit(s), s.budgetCarry, s.spent);
        s.month = m;
        s.spent = 0f; s.fees = 0f; s.upkeepAdded = 0f; s.terraSpent = 0f; s.spMonth = 0; s.endgameLoss = 0f;
        s.monthCredits = CABuild.credits();
        try {
            MonthlyReport prev = SharedData.getData().getPreviousReport();
            if (prev != null && prev.getRoot() != null) {
                prev.computeTotals();
                s.lastNet = prev.getRoot().totalIncome - prev.getRoot().totalUpkeep;
                s.lastNetKnown = true;
            }
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "could not read last month's report", t);
        }
        if (!first) CALog.add("Month: net " + Misc.getDGSCredits(netIncome(s)) + ", budget " + Misc.getDGSCredits(limit(s))
                + (s.budgetCarry > 0f ? " plus " + Misc.getDGSCredits(s.budgetCarry) + " saved up" : ""));
    }

    /** Unspent budget is saved up for at most this many months' worth. */
    public static final float CARRY_MONTHS = 6f;

    /** What's saved up after a month with this budget, savings and spending: what's left, never below 0 or above CARRY_MONTHS budgets. Pure. */
    static float carried(float limit, float carry, float spent) {
        return Math.max(0f, Math.min(CARRY_MONTHS * limit, limit + carry - spent));
    }

    /** Budget saved up from earlier months (0.2.39.0). */
    public static float saved(CASettings s) { return s.budgetCarry; }

    /** Last month's net income, or this month's colony net income until a full month has passed. */
    public static float netIncome(CASettings s) {
        if (s.lastNetKnown) return s.lastNet;
        float n = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) n += m.getNetIncome();
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
    static final float UPKEEP_SHARE = 0.1f, UPKEEP_FLOOR = 20000f;

    static float autoUpkeepCap(float net) { return Math.max(UPKEEP_FLOOR, UPKEEP_SHARE * Math.max(0f, net)); }

    /**
     * New upkeep Auto may add this month: Automatic, 10% of last month's net income and at least 20,000, so it keeps up
     * as income grows (a fixed number set at setup went stale: 20,000 against 760,000 a month held a plan step back for
     * seven months); or your Fixed amount.
     */
    public static float upkeepCap(CASettings s) { return s.upkeepCapAuto ? autoUpkeepCap(netIncome(s)) : s.newUpkeepCap; }

    /** A save from before 0.2.24.0 had a fixed cap, set once at setup; it becomes Automatic once, with a log line. */
    static void migrateUpkeepCap(CASettings s) {
        if (s.upkeepCapChecked) return;
        s.upkeepCapChecked = true;
        if (s.upkeepCapAuto) return;
        s.upkeepCapAuto = true;
        CALog.add("New upkeep cap per month is Automatic now: 10% of last month's net income, at least 20,000 (" + Misc.getDGSCredits(upkeepCap(s))
                + " now; it was a fixed " + Misc.getDGSCredits(s.newUpkeepCap) + "). Settings, Budget and spending can set a Fixed cap again", true);
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
        return Math.max(5000f, 0.05f * cost) * CASettings.FEE_MULT[Math.max(0, Math.min(3, s.feeLevel))];
    }

    public static float fee(CASettings s, float standard) {
        return standard * CASettings.FEE_MULT[Math.max(0, Math.min(3, s.feeLevel))];
    }

    public static void charge(CASettings s, float cost, float fee, float upkeep) {
        if (fee > 0f) Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(fee);
        s.spent += cost + fee;
        s.fees += fee;
        s.upkeepAdded += Math.max(0f, upkeep);
    }
}
