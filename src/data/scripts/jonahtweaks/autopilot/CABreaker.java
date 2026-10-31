package data.scripts.jonahtweaks.autopilot;

import java.util.*;

/**
 * Circuit breaker for actions that reach into other mods. After each one Autopilot checks that it took effect (the
 * project is running, the item is installed, the setting reads back). Three failures in a row stop that kind of
 * action for the rest of the session, with a log line and a Diagnostics entry, so a mod update that changes how
 * something works can't make Autopilot repeat a broken action every day. A success resets the count; reloading
 * the game tries again.
 */
public class CABreaker {
    static final int LIMIT = 3;
    static final Map<String, Integer> fails = new HashMap<String, Integer>();
    static final Map<String, String> tripped = new LinkedHashMap<String, String>();

    public static void reset() { fails.clear(); tripped.clear(); }

    public static boolean allowed(String action) { return !tripped.containsKey(action); }

    /** Records whether an action worked; returns ok. */
    public static boolean check(String action, boolean ok, String where) {
        if (ok) { fails.remove(action); return true; }
        Integer n = fails.get(action);
        n = n == null ? 1 : n + 1;
        fails.put(action, n);
        CADiagnostics.LOG.warn(CADiagnostics.TAG + action + " didn't take effect at " + where + " (" + n + " of " + LIMIT + ")");
        if (n >= LIMIT && !tripped.containsKey(action)) {
            tripped.put(action, where);
            CALog.add("Autopilot stopped " + action + " for this session: it didn't take effect " + LIMIT + " times in a row (last at "
                    + where + "). A mod update may have changed how it works; see Diagnostics.", true);
        }
        return false;
    }

    public static Map<String, String> tripped() { return tripped; }
}
