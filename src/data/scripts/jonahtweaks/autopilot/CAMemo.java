package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.3.20.1: reads kept while a read-only look runs. With Ashes of the Domain a colony's income read prices its exports
 * against the sector's demand, and Building Menu Overhaul's hidden list runs every BMO listener (one makes a plugin for every
 * Grand Wonder) on each read; an evaluation read both once per building it scores (the profile of 2026-11-25: most of the
 * special project supply step, the focus world and surplus terraforming).
 * - scope(): begin() and end() around a look (CAEngine.evaluate, CAProjects.update, CASurplus.compute, the surplus
 *   terraforming plan of one colony). Outside one, every read goes to the game.
 * - A kept value is used only for the same market with the same sig(): game time, size, industries (with what they're
 *   building or upgrading to, their AI core and item) and conditions. A build, an install or a condition change in the
 *   middle of a look reads again.
 * Session only: the last end() empties it; CAReset empties it on load.
 */
public final class CAMemo {
    private CAMemo() { }

    /** Kind and market id -> {sig, value}. */
    static final Map<String, Object[]> KEPT = new HashMap<String, Object[]>();
    /** Open scopes. */
    static int depth = 0;

    public interface Read<T> { T get(); }

    static void begin() { depth++; }

    static void end() {
        if (--depth > 0) return;
        depth = 0;
        KEPT.clear();
    }

    /** Runs r with reads kept. */
    static void scope(Runnable r) {
        begin();
        try { r.run(); } finally { end(); }
    }

    /** Pure: a market state's signature from its parts (CAMemo.sig). */
    static long mix(long h, long v) { return h * 1_000_003L + v; }

    /** The parts of a market that the kept reads depend on. */
    static long sig(MarketAPI m) {
        long h = 17L;
        try { h = mix(h, Global.getSector().getClock().getTimestamp()); } catch (Throwable ignored) { }
        h = mix(h, m.getSize());
        for (Industry ind : m.getIndustries()) {
            h = mix(h, ind.getId().hashCode());
            h = mix(h, ind.isBuilding() ? 1 : 0);
            h = mix(h, ind.isUpgrading() ? 1 : 0);
            String core = ind.getAICoreId();
            h = mix(h, core == null ? 0 : core.hashCode());
            h = mix(h, ind.getSpecialItem() == null ? 0 : ind.getSpecialItem().getId().hashCode());
        }
        for (MarketConditionAPI c : m.getConditions()) h = mix(h, c.getId().hashCode());
        return h;
    }

    /** The kept read of this kind at m inside a scope, else a fresh one. */
    @SuppressWarnings("unchecked")
    static <T> T kept(String kind, MarketAPI m, Read<T> r) {
        if (depth <= 0 || m == null) return r.get();
        String key = kind + "|" + m.getId();
        long s = sig(m);
        Object[] k = KEPT.get(key);
        if (k != null && (Long) k[0] == s) return (T) k[1];
        T v = r.get();
        KEPT.put(key, new Object[]{s, v});
        return v;
    }

    /** m.getGrossIncome(), kept inside a scope. */
    public static float gross(final MarketAPI m) { return kept("gross", m, () -> m.getGrossIncome()); }

    /** m.getNetIncome(), kept inside a scope. */
    public static float net(final MarketAPI m) { return kept("net", m, () -> m.getNetIncome()); }

    public static void reset() { KEPT.clear(); depth = 0; }
}
