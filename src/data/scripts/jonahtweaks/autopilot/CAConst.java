package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;

import java.util.*;

/**
 * Numbers read from a mod's own public static fields, with the value Autopilot was written against as the fallback,
 * so a mod rebalance is picked up rather than copied numbers going stale. A live value that differs from the fallback
 * is logged once and listed in Diagnostics; a field that's gone falls back quietly (the health check reports the mod).
 * Read once per load.
 */
public class CAConst {
    static final Map<String, Object> cache = new HashMap<String, Object>();
    public static final List<String> drift = new ArrayList<String>();

    public static void reset() { cache.clear(); drift.clear(); }

    static Object field(String cls, String name) {
        String key = cls + "#" + name;
        if (cache.containsKey(key)) return cache.get(key);
        Object v = null;
        try { v = CARefl.get(CARefl.field(Class.forName(cls, true, Global.getSettings().getScriptClassLoader()), name), null); }
        catch (Throwable ignored) { }
        cache.put(key, v);
        return v;
    }

    static void noteDrift(String what, Object was, Object now) {
        String line = what + ": " + was + " -> " + now;
        if (drift.contains(line)) return;
        drift.add(line);
        CADiagnostics.LOG.info(CADiagnostics.TAG + "mod value changed since Autopilot was written, using the new one: " + line);
    }

    /** A number field, or the fallback. */
    public static float num(String cls, String name, float fallback) {
        Object v = field(cls, name);
        if (!(v instanceof Number)) return fallback;
        float f = ((Number) v).floatValue();
        if (Math.abs(f - fallback) > 1e-4f) noteDrift(simple(cls) + "." + name, fallback, f);
        return f;
    }

    /** One entry of a Map<String, Number> field, or the fallback. */
    public static int mapInt(String cls, String name, String key, int fallback) {
        Object v = field(cls, name);
        if (!(v instanceof Map)) return fallback;
        Object x = ((Map<?, ?>) v).get(key);
        if (!(x instanceof Number)) return fallback;
        int i = ((Number) x).intValue();
        if (i != fallback) noteDrift(simple(cls) + "." + name + "[" + key + "]", fallback, i);
        return i;
    }

    static String simple(String cls) { int i = cls.lastIndexOf('.'); return i < 0 ? cls : cls.substring(i + 1); }
}
