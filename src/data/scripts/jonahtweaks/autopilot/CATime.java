package data.scripts.jonahtweaks.autopilot;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.3.0.0 (A1): one clock. Game days from the sector clock (CADay), so a timer means the same thing in every session.
 * Before 0.3.0 the daily pass counted days from when this session started, and a weekly step stamped on one session's count
 * sat out as long as that session had run after another save was loaded (07 H2).
 * due() and fresh() also treat a stamp later than today as stale: loading an earlier save sends the clock back, and a timer
 * must never wait for a date this timeline hasn't reached (07 H6). Stamps live here, not in the save; CAReset clears them
 * on every load.
 * 0.3.0.0 (A1 fix): the game clock counts milliseconds from 1970, so every game day is negative (c206 starts near -644,300; a c213 save reads -641,746).
 * A timer's "never fired" start value is NaN, not -99 or -1: `now - (-99) < 7` holds every day on this clock, and a
 * `day >= 0` test never does. due() and fresh() are right on either sign; use them for anything new.
 */
public final class CATime {
    private CATime() { }

    /** When each timer last fired or each cache was filled, by key. */
    static final Map<String, Float> stamps = new HashMap<String, Float>();

    /** Game days from the sector clock (0 when there's no sector yet). */
    public static float now() { return CADay.now(); }

    /** Today's whole day number, for "once a day" caches keyed by day. */
    public static int today() { return (int) Math.floor(now()); }

    /** True when `every` days have passed since this key last returned true (and stamps it); true the first time. */
    public static boolean due(String key, float every) {
        float now = now();
        Float last = stamps.get(key);
        if (!due(last == null ? Float.NaN : last, now, every)) return false;
        stamps.put(key, now);
        return true;
    }

    /** True when the value cached under key was stamped less than maxAge days ago (and not in the future). */
    public static boolean fresh(String key, float maxAge) {
        Float at = stamps.get(key);
        return at != null && fresh(at, now(), maxAge);
    }

    /** Marks the value cached under key as filled now. */
    public static void stamp(String key) { stamps.put(key, now()); }

    /** Lets a timer fire, or a cache refill, on the next ask. */
    public static void forget(String key) { stamps.remove(key); }

    /**
     * Pure: a timer that last fired at `last` is due at `now` when it never fired (NaN), when the clock went back past it
     * (an earlier save was loaded), or when `every` days have passed.
     */
    public static boolean due(float last, float now, float every) {
        return Float.isNaN(last) || now < last || now - last >= every;
    }

    /** Pure: a value cached at `at` is still good at `now` when it's younger than maxAge days and not from the future. */
    public static boolean fresh(float at, float now, float maxAge) {
        return !Float.isNaN(at) && now >= at && now - at < maxAge;
    }

    /** Called by CAReset.onLoad. */
    static void reset() { stamps.clear(); }
}
