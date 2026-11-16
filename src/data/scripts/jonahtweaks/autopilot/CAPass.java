package data.scripts.jonahtweaks.autopilot;

import java.util.LinkedList;
import java.util.List;

/**
 * 0.3.6.9: the daily pass spread over frames. CAScript starts a pass with start(): CAAuto.runPass runs once to queue its
 * steps (CAStep.run and CAStep.guard queue while building() is true, and later() queues plain code), then drain() runs
 * queued steps each frame until the frame's share (CATuning.PASS_FRAME_MS) is used, at least one step a frame. The
 * planner queues one task per colony look (CAPlanner.run while draining()), so no frame waits for every colony.
 * Code outside a pass (tests, a direct call) runs at once as before: building() is false there.
 * Transient: a save in the middle of a pass drops the rest of it, CAReset clears the queue on load, and the next day's
 * pass starts fresh (the planner's looks and offers are cleared on load too).
 */
public final class CAPass {
    private CAPass() { }

    static final LinkedList<Runnable> queue = new LinkedList<Runnable>();
    static boolean building = false, draining = false;
    /** The pass in progress: CPU time of its steps, frames used, the longest frame's share. */
    static long total = 0L, longest = 0L;
    static int frames = 0;
    /** Frames skipped while Theory of Toolbox's economy threads read markets (CAScript.contractsBusy). */
    static int waited = 0;

    /** True while runPass queues its steps. */
    public static boolean building() { return building; }

    /** True while queued steps run (the planner then queues its colony looks). */
    public static boolean draining() { return draining; }

    /** A pass is queued or in progress. */
    public static boolean running() { return !queue.isEmpty(); }

    /** Queues a pass: runs `queue` once with building() true (runPass), then nothing until drain(). */
    public static void start(Runnable queue) {
        reset();
        building = true;
        long t0 = System.nanoTime();
        try { queue.run(); }
        finally { building = false; }
        total += System.nanoTime() - t0;
    }

    /** Queues code while a pass is being built; runs it now otherwise (so the same runPass also works in one call). */
    public static void later(Runnable r) {
        if (building) queue.add(r);
        else r.run();
    }

    /** Queues a step (CAStep.run while building). */
    static void add(Runnable r) { queue.add(r); }

    /** Runs `l` right after the step that's running, in order (the planner's colony looks). Runs it now outside a pass. */
    public static void next(List<Runnable> l) {
        if (!draining) { for (Runnable r : l) r.run(); return; }
        for (int i = l.size() - 1; i >= 0; i--) queue.addFirst(l.get(i));
    }

    /** Drops the rest of the pass (story mode's Autopilot went dormant). */
    public static void stop() { queue.clear(); }

    /**
     * One frame's share: runs queued steps until `budgetMs` is used (at least one). `busy`: another mod's threads are
     * reading markets, so the frame is skipped, up to `maxWait` frames in a row. Returns true when the pass just finished.
     */
    public static boolean drain(float budgetMs, boolean busy, int maxWait) {
        if (queue.isEmpty()) return false;
        if (busy && waited < maxWait) { waited++; return false; }
        waited = 0;
        long start = System.nanoTime(), budget = (long) (budgetMs * 1000000f);
        draining = true;
        try {
            do {
                Runnable r = queue.poll();
                try { r.run(); }
                catch (Throwable t) { CAStep.warn("daily pass task", "daily pass task failed", t); }   // CAStep catches step failures; this is the queue's own code. 0.3.8.7: deduplicated
            } while (!queue.isEmpty() && System.nanoTime() - start < budget);
        } finally { draining = false; }
        long used = System.nanoTime() - start;
        total += used;
        frames++;
        if (used > longest) longest = used;
        if (!queue.isEmpty()) return false;
        try { CAPerf.pass(total, frames, longest); } catch (Throwable ignored) { }
        return true;
    }

    /** Pure: how many frames a pass of `taskMs` steps takes at `budgetMs` a frame (each frame runs at least one step). For the checks. */
    static int framesFor(float[] taskMs, float budgetMs) {
        int f = 0, i = 0;
        while (i < taskMs.length) {
            float used = 0f;
            do { used += taskMs[i++]; } while (i < taskMs.length && used < budgetMs);
            f++;
        }
        return f;
    }

    public static void reset() {
        queue.clear();
        building = false; draining = false;
        total = 0L; longest = 0L; frames = 0; waited = 0;
    }
}
