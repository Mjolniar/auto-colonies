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
 * - 0.3.0.9: survival mode's open-market tariff and administrator share (ext_normalTariff, ext_adminAvailableFraction);
 *   player support fleets; the Askonia standoff that gates the Diktat's crisis progress; parts-based d-mod repairs.
 */
public final class CASurvival {
    private CASurvival() { }

    static final String CORRUPTION_NODE = "node_id_corruption";   // MonthlyReport.CORRUPTION, 0.98.5a only

    private static boolean looked;
    private static MethodHandle isExtended;
    /** 0.3.0.9: Misc.isPlayerSupportFleet (0.98.5a), null on 0.98a. */
    private static MethodHandle isPlayerSupportFleet;
    /** 0.3.0.9: SDResolution.isAskoniaStandoffInProgress and isAskoniaStandoffCompleted (0.98.5a), null on 0.98a. */
    private static MethodHandle standoffInProgress, standoffCompleted;
    /** 0.3.0.9: Misc.usePartsBasedDModRepairs (0.98.5a), null on 0.98a. */
    private static MethodHandle partsRepairs;
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
        // 0.3.0.9: 0.98.5a's support fleets, the Askonia standoff and parts-based repairs
        try {
            isPlayerSupportFleet = l.findStatic(com.fs.starfarer.api.util.Misc.class, "isPlayerSupportFleet",
                    MethodType.methodType(boolean.class, com.fs.starfarer.api.campaign.CampaignFleetAPI.class));
        } catch (Throwable t) { isPlayerSupportFleet = null; }
        try {
            Class<?> sd = Class.forName("com.fs.starfarer.api.impl.campaign.intel.events.sd.SDResolution", false, CASurvival.class.getClassLoader());
            standoffInProgress = l.findStatic(sd, "isAskoniaStandoffInProgress", MethodType.methodType(boolean.class));
            standoffCompleted = l.findStatic(sd, "isAskoniaStandoffCompleted", MethodType.methodType(boolean.class));
        } catch (Throwable t) { standoffInProgress = standoffCompleted = null; }
        try {
            partsRepairs = l.findStatic(com.fs.starfarer.api.util.Misc.class, "usePartsBasedDModRepairs", MethodType.methodType(boolean.class));
        } catch (Throwable t) { partsRepairs = null; }
    }

    /**
     * 0.3.0.9: a player support fleet (0.98.5a, SupportFleetSpawner): "$supportFleet" set, a "$supportFleetType", or a fleet of
     * yours of type "taskForce"; Misc.isPlayerSupportFleet where the game has it. It follows you, so it is never a colony's
     * defense, a threat, a siege or a target. Never one on 0.98a.
     */
    public static boolean supportFleet(com.fs.starfarer.api.campaign.CampaignFleetAPI f) {
        if (f == null) return false;
        try {
            com.fs.starfarer.api.campaign.rules.MemoryAPI mem = f.getMemoryWithoutUpdate();
            boolean player = f.getFaction() != null && f.getFaction().isPlayerFaction();
            if (mem != null && supportFleet(mem.getBoolean("$supportFleet"), mem.contains("$supportFleetType"), player, mem.getString("$fleetType"))) return true;
        } catch (Throwable ignored) { }
        look();
        if (isPlayerSupportFleet == null) return false;
        try { return (boolean) isPlayerSupportFleet.invoke(f); } catch (Throwable t) { return false; }
    }

    /** Pure (0.3.0.9, JTSurvivalCheck): the support fleet test on what a fleet's memory and faction say. */
    static boolean supportFleet(boolean flag, boolean hasType, boolean yours, String fleetType) {
        return flag || (yours && (hasType || "taskForce".equals(fleetType)));
    }

    /**
     * 0.3.0.9: whether the Sindrian Diktat's crisis factor counts progress now. On 0.98.5a its getProgress is 0 unless the
     * Askonia standoff is under way and not over (SindrianDiktatHostileActivityFactor); null on 0.98a, where it always counts.
     */
    public static Boolean diktatStandoff() {
        look();
        if (standoffInProgress == null || standoffCompleted == null) return null;
        try { return (boolean) standoffInProgress.invoke() && !(boolean) standoffCompleted.invoke(); }
        catch (Throwable t) { return null; }
    }

    /** 0.3.0.9: parts-based d-mod repairs are on (0.98.5a, Misc.usePartsBasedDModRepairs), so markets stock Domain-grade Parts. False on 0.98a. */
    public static boolean partsRepairs() {
        look();
        if (partsRepairs == null || Global.getSettings() == null) return false;
        try { return (boolean) partsRepairs.invoke(); } catch (Throwable t) { return false; }
    }

    /** This campaign runs in survival mode (0.98.5a). Always false on 0.98a. */
    public static boolean extended() {
        look();
        if (isExtended == null || Global.getSector() == null) return false;   // 0.3.0.9: no campaign (offline checks): Misc isn't loaded for this
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

    /**
     * 0.3.0.9: the open market's tariff in survival mode (OpenMarketPlugin.getTariff reads ext_normalTariff, 0.4 in the game's
     * settings); 0 outside survival mode and on 0.98a. Autopilot adds it to goods it buys for you (CAExpansion's founding goods).
     */
    public static float survivalTariff() {
        if (!extended()) return 0f;
        try {
            float t = Global.getSettings().getFloat("ext_normalTariff");
            return t > 0f ? t : 0f;
        } catch (Throwable t) { return 0f; }
    }

    /**
     * 0.3.0.9: the share of administrators the game offers in survival mode (OfficerManagerEvent multiplies its chance by
     * ext_adminAvailableFraction, 0.1 in the game's settings); 1 outside survival mode and on 0.98a.
     */
    public static float adminAvailableFraction() {
        if (!extended()) return 1f;
        try {
            float f = Global.getSettings().getFloat("ext_adminAvailableFraction");
            return f > 0f && f < 1f ? f : 1f;
        } catch (Throwable t) { return 1f; }
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

    /** Money a month after corruption: a gain at the kept share, a loss in full. For money with no upkeep in it (items). */
    public static float kept(float money) {
        return money > 0f ? money * keptShare() : money;
    }

    /**
     * 0.3.0.8: net money (income minus upkeep) after corruption. The game takes corruption from gross colony income only,
     * so the kept share applies to the income part (net + upkeep) and upkeep counts in full.
     */
    public static float keptGross(float net, float upkeep) {
        float gross = net + upkeep;
        return gross > 0f ? keptGross(net, upkeep, keptShare()) : net;
    }

    /** Pure: keptGross at a given kept share. */
    static float keptGross(float net, float upkeep, float share) {
        float gross = net + upkeep;
        if (!(gross > 0f) || !(share < 1f)) return net;
        return gross * share - upkeep;
    }
}
