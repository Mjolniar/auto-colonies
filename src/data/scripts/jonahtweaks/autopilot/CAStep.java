package data.scripts.jonahtweaks.autopilot;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 0.3.0.0 (A1): every daily and weekly step of the pass runs through here (07 H3, M8).
 * - Isolation: a step that throws never skips the steps after it; run() and guard() catch everything.
 * - Timing: every run goes to CAPerf (milliseconds per step for the Diagnostics page, A4).
 * - Breaker: a step that fails LIMIT times in a row (a daily step: three days running) is stopped for the session, with one
 *   line in the panel log and a Diagnostics entry, like CABreaker does for mod actions. A success resets the count;
 *   loading a save tries again (CAReset). guard() is the same without the breaker, for the 0-stability safety net and the
 *   steps CABreaker already stops: those keep trying.
 * - Quieter failures: a failure's stack trace goes to starsector.log once per step, exception and top frame; after that
 *   the same failure is one short line at most once a week with a count (0.2.45 logged the full trace every day).
 * Nothing here is saved; CAReset clears it on every load.
 */
public class CAStep {
    public interface Body { void run() throws Exception; }

    /** Failures in a row that stop a step for the session (CABreaker's three). */
    static final int LIMIT = 3;
    /** Days between repeat lines in starsector.log for the same failure, after its first stack trace. */
    static final float REPEAT_DAYS = 7f;

    /** One step's record this session (its timings are in CAPerf). */
    static final class Stat {
        int runs, fails, inRow;
        String lastError;   // the latest failure, short ("NullPointerException at CAEngine.java:120")
        String broken;      // why it's stopped for the session, or null
    }

    static final Map<String, Stat> stats = new LinkedHashMap<String, Stat>();
    /** Failures seen, by step|exception|top frame: {times seen, day last logged}. */
    static final Map<String, float[]> seen = new HashMap<String, float[]>();

    /** Runs one step with the breaker; returns false when it threw or is stopped for the session. */
    public static boolean run(String name, Body body) { return run(name, body, true); }

    /** Runs one step that's never stopped for the session (the safety net, or a step CABreaker stops); false when it threw. */
    public static boolean guard(String name, Body body) { return run(name, body, false); }

    static boolean run(String name, Body body, boolean breaker) {
        boolean allowed = true;
        try { allowed = CASync.step(name); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story check for step " + name + " failed", t); }   // 0.3.6.2
        if (!allowed) return true;   // 0.3.6.0 story mode: a step whose ability is off doesn't run
        Stat st = stat(name);
        if (st.broken != null) return false;
        long t0 = System.nanoTime();
        Throwable err = null;
        try { body.run(); }
        catch (Throwable t) { err = t; }
        long nanos = System.nanoTime() - t0;
        st.runs++;
        try { CAPerf.record(name, nanos); } catch (Throwable ignored) { }   // timing never fails a step
        if (err == null) { st.inRow = 0; return true; }
        st.fails++;
        st.inRow++;
        st.lastError = brief(err);
        warn(name, name + " failed", err);
        if (breaker && st.inRow >= LIMIT) stop(name, st);
        return false;
    }

    static Stat stat(String name) {
        Stat st = stats.get(name);
        if (st == null) { st = new Stat(); stats.put(name, st); }
        return st;
    }

    /** Stops a step for the session: one line in starsector.log and one in the panel log. */
    static void stop(String name, Stat st) {
        st.broken = st.inRow + " failures in a row, last: " + st.lastError;
        CADiagnostics.LOG.warn(CADiagnostics.TAG + name + " stopped for this session: " + st.broken);
        try { CALog.advise("Autopilot stopped its " + name + " for this session after " + st.inRow + " failures in a row. See Diagnostics.", true); }
        catch (Throwable ignored) { }   // the log itself failing must never throw out of a step
    }

    /**
     * A failure in starsector.log, deduplicated (07 M8): the stack trace the first time this step fails with this exception
     * at this place, then one short line with the count at most once a week. For per-colony and per-candidate catches too.
     */
    public static void warn(String step, String text, Throwable t) {
        try {
            String sig = step + "|" + t.getClass().getName() + "|" + top(t);
            float now = CATime.now();
            float[] s = seen.get(sig);
            if (s == null) {
                seen.put(sig, new float[]{1f, now});
                CADiagnostics.LOG.warn(CADiagnostics.TAG + text, t);
                return;
            }
            s[0]++;
            if (!CATime.due(s[1], now, REPEAT_DAYS)) return;
            s[1] = now;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + text + ": " + brief(t) + " (" + (int) s[0] + " times this session; stack trace logged the first time)");
        } catch (Throwable ignored) { }
    }

    /** The first stack frame, "CAEngine.java:120", or "?" for none. */
    static String top(Throwable t) {
        StackTraceElement[] st = t.getStackTrace();
        if (st == null || st.length == 0) return "?";
        return st[0].getFileName() + ":" + st[0].getLineNumber();
    }

    /** "NullPointerException at CAEngine.java:120". */
    static String brief(Throwable t) { return t.getClass().getSimpleName() + " at " + top(t); }

    /** Whether a step is stopped for the session. */
    public static boolean broken(String name) {
        Stat st = stats.get(name);
        return st != null && st.broken != null;
    }

    /** Steps stopped for the session and why, for the Diagnostics page. */
    public static Map<String, String> broken() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, Stat> e : stats.entrySet()) if (e.getValue().broken != null) out.put(e.getKey(), e.getValue().broken);
        return out;
    }

    /** Per-step milliseconds (CAPerf's rolling average) for the Diagnostics page, in the order the steps first ran. */
    public static Map<String, Float> timings() {
        Map<String, Float> out = new LinkedHashMap<String, Float>();
        for (String name : stats.keySet()) {
            CAPerf.Stat p = CAPerf.get(name);
            out.put(name, p == null ? 0f : p.avg);
        }
        return out;
    }

    /** Every step's record (read-only). */
    static Map<String, Stat> stats() { return Collections.unmodifiableMap(stats); }

    /** Called by CAReset.onLoad: every step runs again after a load, and its failures log their trace once more. */
    static void reset() { stats.clear(); seen.clear(); }
}
