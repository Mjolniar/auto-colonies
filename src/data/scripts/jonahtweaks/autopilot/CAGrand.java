package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.lang.reflect.Method;

/**
 * How many buildings a colony can hold. The vanilla colony screen shows 12 tiles, and a 13th building is built but
 * can't be seen or managed, so without Grand.Colonies nothing new is built past 12. Grand.Colonies (SirHartley) lifts
 * that, and by default makes every building past its threshold (12) multiply all of the colony's upkeep by 1.1,
 * compounding; that cost is added to each such build. Settings are read from LunaLib by reflection, with the mod's
 * defaults when they can't be read.
 */
public class CAGrand {
    public static final String MOD = "GrandColonies";
    static final int SCREEN = 12;
    static Method lunaBool, lunaInt, lunaFloat;
    static boolean linked = false, broken = false;

    static boolean link() {
        if (linked || broken) return linked;
        try {
            Class<?> l = Class.forName("lunalib.lunaSettings.LunaSettings", true, Global.getSettings().getScriptClassLoader());
            lunaBool = l.getMethod("getBoolean", String.class, String.class);
            lunaInt = l.getMethod("getInt", String.class, String.class);
            lunaFloat = l.getMethod("getFloat", String.class, String.class);
            linked = true;
        } catch (Throwable t) { broken = true; }
        return linked;
    }

    static Object luna(Method m, String key) {
        try { return link() ? m.invoke(null, MOD, key) : null; } catch (Throwable t) { return null; }
    }

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
        Object on = luna(lunaBool, "GrandColonies_AddPenalty");
        if (on instanceof Boolean && !((Boolean) on)) return 0f;
        Object th = luna(lunaInt, "GrandColonies_PenaltyThreshold"), pp = luna(lunaFloat, "GrandColonies_UpkeepPenaltyPerBuilding");
        int threshold = th instanceof Number ? ((Number) th).intValue() : 12;
        float per = pp instanceof Number ? ((Number) pp).floatValue() : 1.1f;
        if (buildings(m) + 1 <= threshold || per <= 1f) return 0f;
        return (m.getIndustryUpkeep() + newUpkeep) * (per - 1f);
    }
}
