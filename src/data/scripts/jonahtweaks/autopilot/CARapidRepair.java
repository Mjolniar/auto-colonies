package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 0.3.11.1: the Rapid repairs package (story mode, Tier IV, 30 bandwidth). Disrupted buildings at your colonies recover in
 * half the time: each day takes one more day off every disruption timer at your colonies (raids, bombardment, strikes),
 * so a timer runs down twice as fast. Your colonies only, never another faction's. Nothing is saved: a timer it shortened is
 * just a shorter timer, so Prepare for removal and turning the package off leave nothing to undo.
 */
public final class CARapidRepair {
    private CARapidRepair() { }

    static final String ABILITY = "rapidRepair";

    /** Pure: a timer one more day shorter, never under 0. */
    static float faster(float left) { return Math.max(0f, left - 1f); }

    /** Daily, from CASync.daily (story mode on). */
    public static void daily() {
        if (!CASync.has(ABILITY)) return;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            for (Industry ind : m.getIndustries()) {
                try {
                    if (!ind.isDisrupted()) continue;
                    ind.setDisrupted(faster(ind.getDisruptedDays()));
                } catch (Throwable ignored) { }
            }
        }
    }
}
