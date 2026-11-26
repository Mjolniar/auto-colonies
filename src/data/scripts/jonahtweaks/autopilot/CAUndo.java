package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.json.JSONObject;

import java.util.*;

/**
 * 0.3.22.0 (plan 2026-11-25, batch 1): the loop guard. Autopilot's changes at a colony are stamped in CAData.swapAt as
 * "marketId:kind:+target" or "marketId:kind:-target" (+ adds or puts in, - removes or takes out). A change that repeats one
 * Autopilot made at the same colony within DAYS (repeats), or puts back what it took out there (undoes), is held. Kinds:
 * "tf" (a terraforming project's condition, CATerraform and CATerraformSurplus) and "item" (target "industryId/itemId",
 * CAItems). The weekly tidy drops stamps after CATidy.KEEP_DAYS and those of colonies that are gone.
 * Repeat watch: an action line (CALog.did) repeated WATCH_TIMES times within WATCH_DAYS goes to starsector.log once a session,
 * with the colony, so a loop of a kind the guard doesn't cover shows up.
 */
final class CAUndo {
    private CAUndo() { }

    /** Days a stamp holds a repeat or an undo. */
    static final float DAYS = 365f;
    static final int WATCH_TIMES = 3;
    static final float WATCH_DAYS = 90f;

    /** Pure: the stamp key. */
    static String key(String marketId, String kind, boolean in, String target) { return marketId + ":" + kind + ":" + (in ? "+" : "-") + target; }

    /** Stamps a change Autopilot just made. */
    static void did(MarketAPI m, String kind, boolean in, String target) {
        try { CAData.get().swapAt().put(key(m.getId(), kind, in, target), Global.getSector().getClock().getTimestamp()); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "loop guard stamp failed at " + m.getName(), t); }
    }

    /** Days since the change was stamped here, or the largest float when it never was. */
    static float since(MarketAPI m, String kind, boolean in, String target) {
        try {
            Long at = CAData.get().swapAt().get(key(m.getId(), kind, in, target));
            return at == null ? Float.MAX_VALUE : Global.getSector().getClock().getElapsedDaysSince(at);
        } catch (Throwable t) { return Float.MAX_VALUE; }
    }

    /** Pure: a stamp this many days old still holds. */
    static boolean holds(float days) { return days >= 0f && days < DAYS; }

    /** The same change was made here within DAYS. */
    static boolean repeats(MarketAPI m, String kind, boolean in, String target) { return holds(since(m, kind, in, target)); }

    /** The opposite change was made here within DAYS: doing this now puts back what Autopilot took out, or the reverse. */
    static boolean undoes(MarketAPI m, String kind, boolean in, String target) { return holds(since(m, kind, !in, target)); }

    /**
     * 0.3.25.1: a terraforming change is held when it repeats one made here within DAYS, or undoes one (the plan removing
     * what surplus terraforming added, or the reverse). An enabler (tectonic activity) may still be taken off once its deposit is in.
     */
    static boolean tfHeld(MarketAPI m, String cond, boolean add) {
        return repeats(m, "tf", add, cond) || (!CATerraSim.ENABLERS.contains(cond) && undoes(m, "tf", add, cond));
    }

    /** One starsector.log line a session for a held change. */
    static void held(MarketAPI m, String kind, boolean in, String target, String what) {
        if (LOGGED.add(key(m.getId(), kind, in, target)))
            CADiagnostics.LOG.info(CADiagnostics.TAG + m.getName() + ": " + what + " is held by the loop guard: Autopilot made this change or its opposite here within "
                    + Math.round(DAYS) + " days.");
    }

    static final Set<String> LOGGED = new HashSet<String>();

    // ---------------- repeat watch ----------------

    /** Pattern -> game days it was seen, this session. */
    static final Map<String, List<Float>> SEEN = new HashMap<String, List<Float>>();
    static final Set<String> WARNED = new HashSet<String>();

    /** Pure: an action line with its numbers turned into #, the key the watch counts by. */
    static String pattern(String line) { return line == null ? "" : line.replaceAll("[0-9][0-9,.]*", "#"); }

    /** Pure: how many of these days are within window of now. */
    static int recent(List<Float> days, float now, float window) {
        int n = 0;
        for (Float d : days) if (d != null && now - d >= 0f && now - d <= window) n++;
        return n;
    }

    /** Counts an action line (CALog.did); one warning a session for a pattern seen WATCH_TIMES times within WATCH_DAYS. */
    /** 0.3.25.1: action lines that repeat by design (weekly deliveries, fleet limits), left out of the watch. */
    static final String[] WATCH_SKIP = {"Storage logistics", "Fleet limit", "Hazard pay", "Month:", "Month at", "Money tier"};   // 0.3.27.1: monthly lines

    static void watch(String line) {
        if (line == null || CALog.whatIf()) return;
        for (String k : WATCH_SKIP) if (line.contains(k)) return;
        float now = CADay.now();
        String p = pattern(line);
        List<Float> l = SEEN.get(p);
        if (l == null) {
            if (SEEN.size() > 4000) SEEN.clear();   // a long session: start over rather than grow
            l = new ArrayList<Float>();
            SEEN.put(p, l);
        }
        l.add(now);
        while (l.size() > 16) l.remove(0);
        int n = recent(l, now, WATCH_DAYS);
        if (n >= WATCH_TIMES && WARNED.add(p))
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "repeat watch: done " + n + " times in " + Math.round(WATCH_DAYS) + " days: " + line);
    }

    /** On load (CAReset). */
    static void reset() { SEEN.clear(); WARNED.clear(); LOGGED.clear(); }

    /** For the report: the loop guard's stamps, newest first, with their age in days. */
    static JSONObject json() throws Exception {
        JSONObject o = new JSONObject();
        List<Map.Entry<String, Long>> l = new ArrayList<Map.Entry<String, Long>>();
        for (Map.Entry<String, Long> e : CAData.get().swapAt().entrySet()) if (e.getKey().contains(":tf:") || e.getKey().contains(":item:")) l.add(e);
        Collections.sort(l, new Comparator<Map.Entry<String, Long>>() {
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) { return Long.compare(b.getValue(), a.getValue()); }
        });
        for (Map.Entry<String, Long> e : l) o.put(e.getKey(), Math.round(Global.getSector().getClock().getElapsedDaysSince(e.getValue())) + " days ago");
        return o;
    }

    /** For the console: the stamps as lines, newest first. */
    static List<String> lines() {
        List<String> out = new ArrayList<String>();
        try {
            JSONObject o = json();
            Iterator<?> it = o.keys();
            while (it.hasNext()) { String k = String.valueOf(it.next()); out.add(k + ": " + o.optString(k)); }
        } catch (Throwable t) { out.add("The loop guard's records couldn't be read."); }
        return out;
    }
}
