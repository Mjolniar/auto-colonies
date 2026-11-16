package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;


/**
 * How many buildings a colony can hold. The vanilla colony screen shows 12 tiles, and a 13th building is built but
 * can't be seen or managed, so without Grand.Colonies nothing new is built past 12. Grand.Colonies (SirHartley) lifts
 * that, and by default makes every building past its threshold (12) multiply all of the colony's upkeep by 1.1,
 * compounding; that cost is added to each such build. Settings are read from LunaLib through CALuna (0.3.9.2, J L15),
 * with the mod's defaults when they can't be read.
 */
public class CAGrand {
    public static final String MOD = "GrandColonies";
    static final int SCREEN = 12;

    /** LunaLib can be read (CACompat's link test for Grand.Colonies). */
    static boolean link() { return CALuna.link(); }

    public static boolean enabled() { return Global.getSettings().getModManager().isModEnabled(MOD); }

    /** Buildings the colony screen counts: everything not hidden, plus what's queued. */
    public static int buildings(MarketAPI m) {
        int n = 0;
        for (Industry ind : m.getIndustries()) if (!ind.isHidden()) n++;
        try { n += m.getConstructionQueue().getItems().size(); } catch (Throwable ignored) { }
        return n;
    }

    /** Why nothing new can be built here, or null: the vanilla screen's 12 tiles are full and Grand.Colonies isn't there to scroll. */
    public static String full(MarketAPI m) {
        return !enabled() && buildings(m) >= SCREEN ? "The colony screen shows at most 12 buildings (Grand.Colonies lifts this)" : null;
    }

    /** Grand.Colonies' extra upkeep for one more building here, per month, or 0. */
    public static float penalty(MarketAPI m, float newUpkeep) {
        if (!enabled()) return 0f;
        Boolean on = CALuna.b(MOD, "GrandColonies_AddPenalty");
        if (on != null && !on) return 0f;
        Integer th = CALuna.i(MOD, "GrandColonies_PenaltyThreshold");
        Float pp = CALuna.f(MOD, "GrandColonies_UpkeepPenaltyPerBuilding");
        int threshold = th != null ? th : 12;
        float per = pp != null ? pp : 1.1f;
        if (buildings(m) + 1 <= threshold || per <= 1f) return 0f;
        return (m.getIndustryUpkeep() + newUpkeep) * (per - 1f);
    }
}
