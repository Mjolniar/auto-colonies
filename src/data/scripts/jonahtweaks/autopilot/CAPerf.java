package data.scripts.jonahtweaks.autopilot;

import java.util.*;

/**
 * 0.3.0.0 (WS-A4, research 07 section 2 item 8): how long each step of the daily pass takes, measured instead of guessed.
 * CAStep times every step into record(); the engine adds its own lines (an evaluation, a plan, a probe measurement); the
 * daily pass reports its total through pass(). Per name: runs, last, a rolling average and the slowest, in milliseconds.
 * Shown on the Diagnostics page; a pass over SLOW_PASS_MS writes one quiet line to starsector.log (never on screen).
 * Nothing here is saved; reset() on every load.
 */
public final class CAPerf {
    private CAPerf() { }

    /** A daily pass slower than this gets one log line (07 section 2: a month-start pass with 6 colonies took about 75 ms). */
    public static final float SLOW_PASS_MS = 50f;
    /** Weight of the newest run in the rolling average. */
    static final float AVG_WEIGHT = 0.1f;
    /** The whole daily pass, as pass() records it. */
    public static final String PASS = "daily pass (total)";

    public static final class Stat {
        public final String name;
        public long runs;
        public float last, avg, max, total;
        Stat(String name) { this.name = name; }
    }

    static final Map<String, Stat> STATS = new LinkedHashMap<String, Stat>();
    /** The game day of the last slow-pass log line, so a long slow stretch writes one line a day at most. */
    static int slowLoggedDay = Integer.MIN_VALUE;

    /** One run of a step, in nanoseconds (System.nanoTime() after minus before). */
    public static void record(String name, long nanos) { recordMs(name, nanos / 1000000f); }

    /** One run of a step, in milliseconds. */
    public static void recordMs(String name, float ms) {
        if (name == null || ms < 0f || Float.isNaN(ms)) return;
        Stat s = STATS.get(name);
        if (s == null) { s = new Stat(name); STATS.put(name, s); }
        s.runs++;
        s.last = ms;
        s.total += ms;
        s.avg = s.runs == 1 ? ms : s.avg + AVG_WEIGHT * (ms - s.avg);
        if (ms > s.max) s.max = ms;
    }

    /** The whole daily pass: recorded under PASS, and one quiet log line when it ran over SLOW_PASS_MS. */
    public static void pass(long nanos) {
        float ms = nanos / 1000000f;
        recordMs(PASS, ms);
        int day = CAEngine.today();
        if (!slow(ms) || day == slowLoggedDay) return;
        slowLoggedDay = day;
        try {
            CADiagnostics.LOG.info(CADiagnostics.TAG + "daily pass took " + Math.round(ms) + " ms; slowest steps: " + summary(3));
        } catch (Throwable ignored) { }
    }

    /** Over the slow-pass line. */
    static boolean slow(float ms) { return ms > SLOW_PASS_MS; }

    public static Stat get(String name) { return STATS.get(name); }

    /** The steps by rolling average, slowest first (the pass total left out), at most n. */
    public static List<Stat> top(int n) {
        List<Stat> l = new ArrayList<Stat>();
        for (Stat s : STATS.values()) if (!PASS.equals(s.name)) l.add(s);
        Collections.sort(l, new Comparator<Stat>() { public int compare(Stat a, Stat b) { return Float.compare(b.avg, a.avg); } });
        return l.size() > n ? new ArrayList<Stat>(l.subList(0, n)) : l;
    }

    /** "name 4.1 ms, name 2.0 ms" for the log line. */
    static String summary(int n) {
        StringBuilder b = new StringBuilder();
        for (Stat s : top(n)) { if (b.length() > 0) b.append(", "); b.append(s.name).append(' ').append(ms(s.last)).append(" ms"); }
        return b.length() == 0 ? "none timed" : b.toString();
    }

    /** One decimal under 10 ms, whole numbers above. */
    public static String ms(float v) {
        return v < 10f ? String.format(Locale.ROOT, "%.1f", v) : String.valueOf(Math.round(v));
    }

    /** Called on every load (CAReset). */
    public static void reset() { STATS.clear(); slowLoggedDay = Integer.MIN_VALUE; }
}
