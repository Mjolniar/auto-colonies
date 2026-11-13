package data.scripts.jonahtweaks.autopilot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 0.3.0.0 (WS-B5, research 01 sections 3, 5, 12 and 13): small saved stamps for decisions that have to hold across a load:
 * a free port waiting to reopen, a world plan money already went into, contracts signed before Theory of Toolbox counts
 * them. Kept in CAData.notedAt under CALog.KEEP ("keep:") keys, which never expire and are never tidied away, so they
 * need no new saved field: a key is ids and numbers, its value a game timestamp (save safety, plans/cm-3.0 FIELDS.md).
 * Nothing is written while a what-if valuation runs (CALog.whatIf); with no save (offline checks) every read is empty.
 * No state of its own, so CAReset has nothing to clear here.
 */
final class CAMark {
    private CAMark() { }

    static final float DAY_MS = 86_400_000f;

    /** The save's once-keys, or null with no save. */
    static Map<String, Long> map() {
        try { return CALog.notedMap(); } catch (Throwable t) { return null; }
    }

    /** When this mark was set (a game timestamp), or null. key is without the keep: prefix. */
    static Long at(String key) {
        Map<String, Long> m = map();
        return m == null ? null : m.get(CALog.KEEP + key);
    }

    /** Sets the mark to this time (replacing any). */
    static void set(String key, long ts) {
        if (CALog.whatIf()) return;
        Map<String, Long> m = map();
        if (m != null) m.put(CALog.KEEP + key, ts);
    }

    /** Sets the mark now unless it's set already; returns when it was set (now when it wasn't, or with no save). */
    static long since(String key, long now) {
        Long t = at(key);
        if (t != null) return t;
        set(key, now);
        return now;
    }

    /** Clears the mark. */
    static void clear(String key) {
        if (CALog.whatIf()) return;
        Map<String, Long> m = map();
        if (m != null) m.remove(CALog.KEEP + key);
    }

    /** The marks under this prefix, without the keep: part (a copy, so the caller may clear them as it goes). */
    static List<String> keys(String prefix) {
        List<String> out = new ArrayList<String>();
        Map<String, Long> m = map();
        if (m == null) return out;
        String p = CALog.KEEP + prefix;
        for (String k : m.keySet()) if (k != null && k.startsWith(p)) out.add(k.substring(CALog.KEEP.length()));
        return out;
    }

    /**
     * The per-colony marks ("<prefix><marketId>" or "<prefix><marketId>|..."): a free port waiting to reopen (CAPolicies),
     * a world commitment and its conditions (CAPlan), a rejected stockpile proposal (CAStockpile, "stockno:<marketId>|<commodity>"),
     * a rejected military proposal (CAMilitary, "milno:<marketId>|<building id>"), an upgrade the Military rung started
     * (CAMilitary, "milup:<marketId>|<building now>|<building it started from>") and an unused surplus terraforming structure
     * (CATerraformSurplus, "tfnew:<marketId>|<structure>"). 0.3.3.0: a rejected build proposal (CAAuto, "buildno:<marketId>|<pick id>").
     */
    static final String[] PER_COLONY = {"fpok:", "world:", "worldcond:", "stockno:", "milno:", "milup:", "tfnew:", "buildno:"};

    /** For CATidy.forget: drops the per-colony marks of colonies that are gone. Returns how many went. */
    static int forgetMarkets(java.util.Set<String> gone) {
        if (gone == null || gone.isEmpty()) return 0;
        int n = 0;
        for (String p : PER_COLONY) {
            for (String k : keys(p)) {
                if (gone.contains(owner(k, p))) { clear(k); n++; }
            }
        }
        return n;
    }

    /** Pure: the market id of a per-colony mark under prefix p. */
    static String owner(String key, String p) {
        String rest = key.substring(p.length());
        int bar = rest.indexOf('|');
        return bar < 0 ? rest : rest.substring(0, bar);
    }

    /** Pure: days from a mark to now (0 when the clock reads earlier than the mark). */
    static float days(long at, long now) { return now <= at ? 0f : (now - at) / DAY_MS; }

    /** The game clock's timestamp (0 with no sector). */
    static long now() { return CALog.clock(); }
}
