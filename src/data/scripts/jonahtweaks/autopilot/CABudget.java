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
        s.month = m;
        s.spent = 0f; s.fees = 0f; s.upkeepAdded = 0f; s.terraSpent = 0f; s.spMonth = 0;
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
        if (!first) CALog.add("New month: budget " + Misc.getDGSCredits(limit(s)) + ", last month's net income "
                + Misc.getDGSCredits(netIncome(s)));
    }

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
        float byBudget = limit(s) - s.spent;
        float byReserve = CABuild.credits() - s.reserve;
        return Math.max(0f, Math.min(byBudget, byReserve));
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
