package data.scripts.jonahtweaks.autopilot;

import java.util.*;

/**
 * 0.3.0.0 (WS-A4, research 07 section 2 item 8): how long each step of the daily pass takes, measured instead of guessed.
 * CAStep times every step into record(); the engine adds its own lines (an evaluation, a plan, a probe measurement); the
 * daily pass reports its total through pass(). Per name: runs, last, a rolling average and the slowest, in milliseconds.
 * Shown on the Diagnostics page; a pass over SLOW_PASS_MS writes one quiet line to starsector.log (never on screen), at most
 * once a week (0.3.8.7).
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
    /** The game day of the last slow-pass log line, so a long slow stretch writes one line a week at most (0.3.8.7: was a day). */
    static int slowLoggedDay = Integer.MIN_VALUE;
    /**
     * 0.3.8.7: days between slow-pass lines. Since 0.3.6.9 the pass runs over frames and its total is the CPU of every step, so
     * a larger empire passes SLOW_PASS_MS on most days while no frame is slow.
     */
    static final int SLOW_LOG_DAYS = 7;

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

    /** 0.3.6.9: the longest frame a daily pass used (CAPass spreads the pass over frames). */
    public static final String FRAME = "daily pass (longest frame)";

    /** The whole daily pass in one call (no frames): as pass(nanos, 1, nanos). */
    public static void pass(long nanos) { pass(nanos, 1, nanos); }

    /**
     * The whole daily pass: its steps' time recorded under PASS, its longest frame under FRAME, and one quiet log line when
     * the steps took over SLOW_PASS_MS in all. 0.3.6.9: the pass runs over several frames (CAPass).
     */
    public static void pass(long nanos, int frames, long longestNanos) {
        float ms = nanos / 1000000f, frame = longestNanos / 1000000f;
        recordMs(PASS, ms);
        recordMs(FRAME, frame);
        monthly();   // 0.3.20.1
        int day = CAEngine.today();
        // 0.3.20.1: a line when a frame went over FRAME_LOG_MS (a visible hitch), once a day at most; before, a slow total, weekly
        // 0.3.33.0: frames up to HITCH_MS once a week, with how many passes went over since the last line
        if (frame <= FRAME_LOG_MS) return;
        if (!frameLogDue(frame, day, slowLoggedDay)) { slowSkipped++; return; }
        slowLoggedDay = day;
        String more = slowSkipped > 0 ? "; " + slowSkipped + " more " + (slowSkipped == 1 ? "pass" : "passes") + " with a frame over "
                + Math.round(FRAME_LOG_MS) + " ms since the last line" : "";
        slowSkipped = 0;
        try {
            String top = summary(3);
            String story = top.contains(STORY + " ") && !PARTS.isEmpty() ? "; story parts: " + parts(3) : "";
            CADiagnostics.LOG.info(CADiagnostics.TAG + "daily pass took " + Math.round(ms) + " ms over " + frames + " frames, longest frame "
                    + ms(frame) + " ms; slowest steps: " + top + story + more);
        } catch (Throwable ignored) { }
    }

    /** 0.3.33.0: a frame over this gets its line on the day; shorter ones once every SLOW_LOG_DAYS. */
    public static final float HITCH_MS = 50f;
    /** 0.3.33.0: passes with a frame over FRAME_LOG_MS that got no line since the last one. */
    static int slowSkipped = 0;

    /** 0.3.33.0: whether a pass whose longest frame is over FRAME_LOG_MS gets a line today. Pure. */
    static boolean frameLogDue(float frame, int day, int last) {
        if (day == last) return false;
        return frame > HITCH_MS || logDue(day, last);
    }

    // ---------------- 0.3.33.0: the story step's parts ----------------

    /** The story step's CAStep name. */
    static final String STORY = "story";
    /** The story step's parts by name (not in top(): the step itself is there). */
    static final Map<String, Stat> PARTS = new LinkedHashMap<String, Stat>();
    static long partAt = 0L;

    /** The story step starts: its parts are timed from here. */
    public static void partsStart() { partAt = System.nanoTime(); }

    /** The part just done, timed since the last part (or partsStart). */
    public static void part(String name) {
        long now = System.nanoTime();
        if (partAt != 0L && name != null) {
            float ms = (now - partAt) / 1000000f;
            Stat s = PARTS.get(name);
            if (s == null) { s = new Stat(name); PARTS.put(name, s); }
            s.runs++; s.last = ms; s.total += ms;
            s.avg = s.runs == 1 ? ms : s.avg + AVG_WEIGHT * (ms - s.avg);
            if (ms > s.max) s.max = ms;
        }
        partAt = now;
    }

    /** "core timers 9.1 ms, story entry 1.2 ms": the parts of the last run, slowest first, at most n. */
    static String parts(int n) {
        List<Stat> l = new ArrayList<Stat>(PARTS.values());
        Collections.sort(l, new Comparator<Stat>() { public int compare(Stat a, Stat b) { return Float.compare(b.last, a.last); } });
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size() && i < n; i++) { if (b.length() > 0) b.append(", "); b.append(l.get(i).name).append(' ').append(ms(l.get(i).last)).append(" ms"); }
        return b.toString();
    }

    /** The parts' rolling averages, for the report. */
    public static Map<String, Float> partAverages() {
        Map<String, Float> out = new LinkedHashMap<String, Float>();
        for (Stat s : PARTS.values()) out.put(s.name, s.avg);
        return out;
    }

    /** Over the slow-pass line. */
    static boolean slow(float ms) { return ms > SLOW_PASS_MS; }

    /** 0.3.8.7: whether a slow-pass line may be written today: never written, a day before the last (a load), or SLOW_LOG_DAYS on. Pure. */
    static boolean logDue(int day, int last) {
        return last == Integer.MIN_VALUE || day < last || (long) day - last >= SLOW_LOG_DAYS;
    }

    public static Stat get(String name) { return STATS.get(name); }

    /** The steps by rolling average, slowest first (the pass total left out), at most n. */
    public static List<Stat> top(int n) {
        List<Stat> l = new ArrayList<Stat>();
        for (Stat s : STATS.values()) if (!PASS.equals(s.name) && !FRAME.equals(s.name)) l.add(s);
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

    // ---------------- 0.3.20.1: daily pass frames ----------------

    /** Frame lines (ms): FRAMES counts frames up to each, and over the last. */
    static final float[] FRAME_LINES = {4f, 8f, 16f, 50f};
    /** Daily pass frames since the last monthly summary: up to 4, 8, 16, 50 ms, and over 50. */
    static final int[] FRAMES = new int[5];
    /** A pass whose longest frame is over this gets a log line. */
    public static final float FRAME_LOG_MS = 8f;
    /** The month of the last summary line; -1 none this session. */
    static int summaryMonth = -1;

    /** Pure: the histogram slot for a frame of this many ms. */
    static int slot(float ms) {
        for (int i = 0; i < FRAME_LINES.length; i++) if (ms <= FRAME_LINES[i]) return i;
        return FRAME_LINES.length;
    }

    /** One daily pass frame (CAPass.drain). */
    static void frame(long nanos) { FRAMES[slot(nanos / 1000000f)]++; }

    /** "120 up to 4 ms, 3 up to 8 ms, ..." */
    static String frames() {
        String[] n = {"up to 4 ms", "up to 8 ms", "up to 16 ms", "up to 50 ms", "over 50 ms"};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < FRAMES.length; i++) { if (b.length() > 0) b.append(", "); b.append(FRAMES[i]).append(' ').append(n[i]); }
        return b.toString();
    }

    /** Once a month: the frame histogram and the slowest steps, then the histogram starts again. */
    static void monthly() {
        int month;
        try { month = com.fs.starfarer.api.Global.getSector().getClock().getMonth(); } catch (Throwable t) { return; }
        if (summaryMonth == month) return;
        if (summaryMonth >= 0) {
            try { CADiagnostics.LOG.info(CADiagnostics.TAG + "daily pass frames this month: " + frames() + "; slowest steps: " + summary(5)); } catch (Throwable ignored) { }
        }
        summaryMonth = month;
        java.util.Arrays.fill(FRAMES, 0);
    }

    /** Called on every load (CAReset). */
    public static void reset() {
        STATS.clear(); slowLoggedDay = Integer.MIN_VALUE; java.util.Arrays.fill(FRAMES, 0); summaryMonth = -1;
        PARTS.clear(); partAt = 0L; slowSkipped = 0;   // 0.3.33.0
    }
}
