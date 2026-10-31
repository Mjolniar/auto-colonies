package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Self-calibration against your own colonies, so estimates follow a mod's rebalance instead of trusting copied
 * numbers. Once a week, every plain building you have (working, no AI core, item or improvement, so the reading is
 * clean) is compared with what the engine would predict for it:
 * - upkeep, for every building type;
 * - output, for buildings scored from estimates rather than the game's formulas.
 * The ratio is smoothed (70% old, 30% new) and kept between 0.5x and 2x, so one odd reading can't swing it, and it's
 * applied once a building type has two readings. Kept in the save.
 */
public class CACalibrate {
    static final float MIN = 0.5f, MAX = 2f, KEEP = 0.7f;
    static float last = -99f;

    static void sample(Map<String, Float> f, Map<String, Integer> n, String id, float ratio) {
        float r = Math.max(MIN, Math.min(MAX, ratio));
        Integer k = n.get(id);
        Float old = f.get(id);
        f.put(id, k == null || k == 0 || old == null ? r : KEEP * old + (1f - KEEP) * r);
        n.put(id, Math.min(100, (k == null ? 0 : k) + 1));
    }

    /** Weekly: reads every clean building at every colony. */
    public static void run(float now) {
        if (now - last < 7f) return;
        last = now;
        CAData d = CAData.get();
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (CAHypershunt.is(m)) continue;
            for (Industry ind : m.getIndustries()) {
                try {
                    if (ind.isHidden() || !ind.isFunctional() || ind.isBuilding() || ind.isUpgrading()
                            || ind.getAICoreId() != null || ind.isImproved() || ind.getSpecialItem() != null) continue;
                    String id = ind.getId();
                    float pred = CAEngine.baseUpkeep(m, ind.getSpec());
                    float act = ind.getUpkeep().getModifiedValue();
                    if (pred > 100f && act > 0f) sample(d.calUp(), d.calUpN(), id, act / pred);
                    if (!CAEngine.estimated(id)) continue;
                    CAEngine.Pick p = new CAEngine.Pick();
                    p.id = id; p.name = id; p.recording = true;
                    if (!CAEngine.estimate(m, id, p, deficit)) continue;
                    int predU = 0, actU = 0;
                    for (Integer u : p.supplied.values()) predU += Math.max(0, u);
                    for (MutableCommodityQuantity q : ind.getAllSupply()) actU += Math.max(0, q.getQuantity().getModifiedInt());
                    if (predU > 0 && actU > 0) sample(d.calOut(), d.calOutN(), id, actU / (float) predU);
                } catch (Throwable t) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "calibration reading failed for " + ind.getId() + " at " + m.getName(), t);
                }
            }
        }
    }

    static float factor(Map<String, Float> f, Map<String, Integer> n, String id) {
        Integer k = n.get(id);
        Float v = f.get(id);
        return k == null || k < 2 || v == null ? 1f : v;
    }

    public static float upkeepFactor(String id) { CAData d = CAData.get(); return factor(d.calUp(), d.calUpN(), id); }

    public static float outputFactor(String id) { CAData d = CAData.get(); return factor(d.calOut(), d.calOutN(), id); }

    /** Enough readings that the estimate is checked against real output. */
    public static boolean calibrated(String id) { Integer k = CAData.get().calOutN().get(id); return k != null && k >= 3; }

    /** Building types whose output or upkeep differs from the engine's numbers by more than 15%, for Diagnostics. */
    public static List<String> notable() {
        CAData d = CAData.get();
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Float> e : d.calOut().entrySet()) {
            float f = factor(d.calOut(), d.calOutN(), e.getKey());
            if (Math.abs(f - 1f) > 0.15f) out.add(e.getKey() + " output x" + String.format(Locale.ROOT, "%.2f", f));
        }
        for (Map.Entry<String, Float> e : d.calUp().entrySet()) {
            float f = factor(d.calUp(), d.calUpN(), e.getKey());
            if (Math.abs(f - 1f) > 0.15f) out.add(e.getKey() + " upkeep x" + String.format(Locale.ROOT, "%.2f", f));
        }
        return out;
    }
}
