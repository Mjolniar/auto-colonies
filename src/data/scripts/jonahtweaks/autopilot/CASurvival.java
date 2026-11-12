package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.List;

/**
 * 0.3.0.4: game rules added in Starsector 0.98.5a. Each is looked up through MethodHandles, so the same jar runs on 0.98a,
 * where every answer here is the 0.98a one (no survival mode, no corruption, Waystation cores cut demand).
 *
 * - Survival mode (Misc.isExtended()): CoreScript takes Corruption from colony income over ext_corruptionThreshold
 *   (200,000) a month: (1 - (threshold / base)^(1 / ext_corruptionExponent)) x base, with base the Colonies node's gross
 *   income (ext_corruptionUsesGrossIncome). Build cost, build time and growth need nothing here: the game's industry spec
 *   and CoreImmigrationPluginImpl.getWeightForMarketSizeStatic already apply the survival multipliers.
 * - Waystation: AI cores raise stockpiles; they no longer cut demand.
 * - Drone Replicator: cancels the stability penalty of ItemEffectsRepo.DRONE_REPLICATOR_CONDITIONS (Decivilized
 *   Subpopulation).
 */
public final class CASurvival {
    private CASurvival() { }

    static final String CORRUPTION_NODE = "node_id_corruption";   // MonthlyReport.CORRUPTION, 0.98.5a only

    private static boolean looked;
    private static MethodHandle isExtended;
    private static boolean waystationStockpileCores;
    private static List<String> replicatorConditions = Collections.emptyList();

    @SuppressWarnings("unchecked")
    private static void look() {
        if (looked) return;
        looked = true;
        MethodHandles.Lookup l = MethodHandles.publicLookup();
        try {
            isExtended = l.findStatic(com.fs.starfarer.api.util.Misc.class, "isExtended", MethodType.methodType(boolean.class));
        } catch (Throwable t) { isExtended = null; }
        try {
            l.findStaticGetter(com.fs.starfarer.api.impl.campaign.econ.impl.Waystation.class, "ALPHA_CORE_STOCKPILES", float.class);
            waystationStockpileCores = true;
        } catch (Throwable t) { waystationStockpileCores = false; }
        try {
            Object o = l.findStaticGetter(com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo.class, "DRONE_REPLICATOR_CONDITIONS", List.class).invoke();
            if (o instanceof List) replicatorConditions = (List<String>) o;
        } catch (Throwable t) { replicatorConditions = Collections.emptyList(); }
    }

    /** This campaign runs in survival mode (0.98.5a). Always false on 0.98a. */
    public static boolean extended() {
        look();
        if (isExtended == null) return false;
        try { return (boolean) isExtended.invoke(); } catch (Throwable t) { return false; }
    }

    /** 0.98.5a: an AI core in a Waystation raises stockpiles and no longer cuts its demand. */
    public static boolean waystationStockpileCores() {
        look();
        return waystationStockpileCores;
    }

    /** Conditions whose stability penalty a Drone Replicator cancels (0.98.5a); empty on 0.98a. */
    public static List<String> replicatorConditions() {
        look();
        return replicatorConditions;
    }

    /** Last month's report the share was worked out for, and the share; reset on load (CAReset). */
    static MonthlyReport shareReport;
    static float share = 1f;

    /**
     * Share of one more credit of colony income kept after corruption, at last month's colony income: 1 outside survival
     * mode and below the threshold. With the game's settings (200,000, exponent 2) it is 0.5 x sqrt(200,000 / income):
     * 0.5 just above the threshold, 0.25 at 800,000.
     */
    public static float keptShare() {
        if (!extended()) return 1f;
        try {
            MonthlyReport prev = SharedData.getData().getPreviousReport();
            if (prev == null || prev.getRoot() == null) return 1f;
            if (prev == shareReport) return share;
            prev.computeTotals();
            MonthlyReport.FDNode col = prev.getRoot().getChildren().get(MonthlyReport.OUTPOSTS);
            float s = 1f;
            if (col != null) {
                boolean gross = Global.getSettings().getBoolean("ext_corruptionUsesGrossIncome");
                float base = col.totalIncome;
                if (!gross) {
                    base -= col.totalUpkeep;
                    MonthlyReport.FDNode c = col.getChildren().get(CORRUPTION_NODE);
                    if (c != null) base += c.totalUpkeep;   // last month's corruption is not part of the base
                }
                s = marginal(base, Global.getSettings().getFloat("ext_corruptionThreshold"), Global.getSettings().getFloat("ext_corruptionExponent"));
            }
            shareReport = prev;
            share = s;
            return s;
        } catch (Throwable t) {
            return 1f;
        }
    }

    /**
     * Pure: d(income - corruption) / d(income) at base. Kept income is threshold^(1/e) x base^(1 - 1/e) above the
     * threshold, so the share is (1 - 1/e) x (threshold / base)^(1/e); 1 at or below the threshold.
     */
    static float marginal(float base, float threshold, float exponent) {
        if (!(threshold > 0f) || !(exponent > 0f) || !(base > threshold)) return 1f;
        return (float) ((1.0 - 1.0 / exponent) * Math.pow(threshold / base, 1.0 / exponent));
    }

    /** Money a month after corruption: a gain at the kept share, a loss in full. */
    public static float kept(float money) {
        return money > 0f ? money * keptShare() : money;
    }
}
