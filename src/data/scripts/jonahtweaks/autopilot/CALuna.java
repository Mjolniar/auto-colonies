package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;

/**
 * Another mod's LunaLib setting, read through reflection (2.45.0): null when LunaLib isn't there, the mod has no such
 * setting, or it can't be read. Same route as CAGrand's (the game's script loader bans java.lang.reflect, so CARefl).
 */
public class CALuna {
    static Object getBoolean, getFloat, getDouble;
    static boolean linked = false, broken = false;

    static boolean link() {
        if (linked || broken) return linked;
        try {
            Class<?> l = Class.forName("lunalib.lunaSettings.LunaSettings", true, Global.getSettings().getScriptClassLoader());
            getBoolean = CARefl.method(l, "getBoolean", String.class, String.class);
            getFloat = CARefl.method(l, "getFloat", String.class, String.class);
            try { getDouble = CARefl.method(l, "getDouble", String.class, String.class); } catch (Throwable ignored) { }
            linked = true;
        } catch (Throwable t) { broken = true; }
        return linked;
    }

    public static Boolean b(String mod, String key) {
        try {
            if (!link()) return null;
            Object v = CARefl.invoke(getBoolean, null, mod, key);
            return v instanceof Boolean ? (Boolean) v : null;
        } catch (Throwable t) { return null; }
    }

    public static Float f(String mod, String key) {
        try {
            if (!link()) return null;
            Object v = CARefl.invoke(getFloat, null, mod, key);
            if (v == null && getDouble != null) v = CARefl.invoke(getDouble, null, mod, key);
            return v instanceof Number ? ((Number) v).floatValue() : null;
        } catch (Throwable t) { return null; }
    }
}
