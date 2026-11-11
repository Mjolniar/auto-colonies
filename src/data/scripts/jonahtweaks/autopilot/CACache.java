package data.scripts.jonahtweaks.autopilot;

import java.util.*;

/**
 * 0.3.0.0 (WS-A4, research 07 section 2 and M5-M7): the day caches behind the engine.
 *
 * A colony was re-scored from scratch by every caller: the daily pass, both panels on every render, research, the endgame,
 * the capital, pruning, items and terraforming each rebuilt its plan and the empire's shortages, often several times a day.
 * Now each answer is kept with three things it was read from, and any of them changing is a miss:
 * - the game day (so nothing lives past midnight, and a reloaded earlier save never hits);
 * - the epoch, one counter for the whole empire that goes up when something changes what a colony can build or is worth
 *   (a build or upgrade started, a takedown, an item, core or administrator moved, a terraforming start, a settings change);
 * - a signature of the colonies it was read from (size, stability, conditions, every building with its state, item, core),
 *   so a change nobody reported (the player building by hand) still misses.
 * Pure Java, no game calls, so JTPerfCacheCheck tests the rules offline. Cleared on every load (reset).
 */
public final class CACache {
    private CACache() { }

    static int epoch = 0;
    static String lastWhy = null;
    /** Every memo, so a load or rescan clears them all at once. */
    static final List<Memo<?>> ALL = new ArrayList<Memo<?>>();

    /** Something changed what colonies can build or are worth: every memo misses from now on. */
    public static void bump(String why) { epoch++; lastWhy = why; }

    public static int epoch() { return epoch; }

    /** A game load or a rescan: every memo emptied, the epoch back to 0, hit counts cleared. */
    public static void reset() {
        epoch = 0; lastWhy = null;
        for (Memo<?> m : ALL) { m.clear(); m.hits = 0; m.misses = 0; }
    }

    /** One kept answer and what it was read from. */
    static final class Entry<V> {
        int day, epoch; long sig; V value;
    }

    /**
     * Answers kept by key, each valid only for the day, epoch and signature it was made with. Nulls are never stored (a
     * null is a miss). At most max keys: past that, entries that can no longer hit go first, then everything.
     */
    public static final class Memo<V> {
        public final String name;
        final int max;
        final Map<String, Entry<V>> map = new HashMap<String, Entry<V>>();
        public long hits, misses;
        int lastDay = Integer.MIN_VALUE, lastEpoch = Integer.MIN_VALUE;

        public Memo(String name, int max) {
            this.name = name; this.max = Math.max(1, max);
            ALL.add(this);
        }

        /** The answer kept under key for this day, the current epoch and this signature; null when there is none (a miss). */
        public V get(String key, int day, long sig) {
            Entry<V> e = map.get(key);
            if (e != null && fresh(e.day, e.epoch, e.sig, day, epoch, sig)) { hits++; return e.value; }
            misses++;
            return null;
        }

        /**
         * Keeps an answer. epochAtStart is the epoch read before the answer was worked out: if something bumped it meanwhile,
         * the answer is already stale and the next get misses.
         */
        public V put(String key, int day, int epochAtStart, long sig, V v) {
            if (v == null || key == null) return v;
            // a new day or epoch: what was kept before can never be used again (both only go up until a load resets them),
            // so it goes now rather than holding a day's evaluations in memory
            if (day != lastDay || epoch != lastEpoch) { prune(day); lastDay = day; lastEpoch = epoch; }
            if (map.size() >= max && !map.containsKey(key)) prune(day);
            Entry<V> e = new Entry<V>();
            e.day = day; e.epoch = epochAtStart; e.sig = sig; e.value = v;
            map.put(key, e);
            return v;
        }

        /** Drops entries from another day or epoch; if that leaves it full, all of them. */
        void prune(int day) {
            for (Iterator<Entry<V>> it = map.values().iterator(); it.hasNext(); ) {
                Entry<V> e = it.next();
                if (e.day != day || e.epoch != epoch) it.remove();
            }
            if (map.size() >= max) map.clear();
        }

        public void clear() { map.clear(); lastDay = Integer.MIN_VALUE; lastEpoch = Integer.MIN_VALUE; }
        public int size() { return map.size(); }

        /** Share of reads answered from the memo since the load, 0-100. */
        public int hitPercent() { long n = hits + misses; return n == 0 ? 0 : (int) Math.round(100.0 * hits / n); }
    }

    /** The rule every memo uses: same day, same epoch, same signature. */
    static boolean fresh(int day, int ep, long sig, int nowDay, int nowEpoch, long nowSig) {
        return day == nowDay && ep == nowEpoch && sig == nowSig && nowDay >= 0;
    }

    // ---------------- signatures ----------------

    /** Ordered mix: mix(mix(h, a), b) differs from mix(mix(h, b), a). */
    public static long mix(long h, long v) {
        long x = h * 0x9E3779B97F4A7C15L + v;
        x ^= (x >>> 31);
        return x * 0xBF58476D1CE4E5B9L;
    }

    /** One id spread over 64 bits, for unordered sums (a colony's conditions or buildings in any order give the same sum). */
    public static long spread(String s) {
        long x = s == null ? 0x51ED27L : s.hashCode() * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        x ^= (x >>> 29); x *= 0xBF58476D1CE4E5B9L; x ^= (x >>> 32);
        return x;
    }

    /** Order-independent signature of a set of ids. */
    public static long unordered(Collection<String> ids) {
        long sum = 0L;
        if (ids != null) for (String s : ids) sum += spread(s);
        return mix(ids == null ? 0 : ids.size(), sum);
    }
}
