package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;

import java.util.*;

/**
 * 0.3.0.0 (research 07 L7, L8, M1): the weekly tidy-up of the saved data (CAData.prune in the plan). Three things:
 * - once-keys past their time (CALog.tidy; the old set was wiped whole at 2,000);
 * - timestamps nobody reads any more: entries past a year that aren't state (every reader of the year-long ones looks back
 *   at most 365 days; ":zerosince" and ":useless:" are state and stay, and CATombstone's build stamps ("@money|rich|endgame"),
 *   which CATombstone tidies itself), and worldDead past a year;
 * - what's left of colonies that are gone for good (no market with that id in the economy, at two weekly checks in a row
 *   this session): their settings, stuck plan steps, dead worlds, endgame and over-limit stamps, surplus and enabler
 *   records, upgrade targets, and an empty Autopilot Storage. A colony that was captured still has its market, so it keeps
 *   everything for the day you take it back; a store with anything in it stays in the save.
 * Nothing here changes a decision: every removed entry is one no reader would act on.
 * 0.3.0.0 (fix, 07 L7): endgameBuilt held the stamps of five features, and CAEndgame.tidy dropped everything in it past a year
 * when the endgame switched on, CAOverLimit's ":zerosince" and CAPrune's ":useless:" timers included. Each feature now has its
 * own map in CAData: overLimitAt (CAOverLimit), pruneAt (CAPrune), swapAt (the last make-room swap at a colony, ":replaced",
 * for CAEndgame, CACapital and CAOverLimit), and endgameBuilt keeps CAEndgame's own. An older save's keys move over by suffix
 * the first time each getter runs (split), so no reader ever sees an empty map first; the weekly run calls the getters too.
 */
public final class CATidy {
    private CATidy() { }

    /** Days an endgameBuilt or worldDead stamp is kept: past every reader's window (365) with a margin. */
    static final float KEEP_DAYS = 400f;
    static final float EVERY_DAYS = 7f;

    static float lastRun = -99f;
    /** Market ids seen gone at the last check this session; gone again at the next one and they're tidied. */
    static final Set<String> goneBefore = new HashSet<String>();

    /** 0.3.0.0 (07 H6): cleared on load (CAReset.onLoad), so a market missing in another save never counts here. */
    public static void reset() { lastRun = -99f; goneBefore.clear(); }

    // ---------------- endgameBuilt split into purpose maps (0.3.0.0 fix, 07 L7) ----------------

    /** CAOverLimit's stamps: ":zerostab", ":zerosince" (state), ":shed", ":shed:<id>", ":overlimit". */
    public static final String OVERLIMIT = "overlimit";
    /** CAPrune's: ":useless:<id>" (state: since when it's been worth less than nothing), ":pruned". */
    public static final String PRUNE = "prune";
    /** The last make-room swap or forced takedown at a colony, ":replaced" (CAEndgame, CACapital, CAOverLimit). */
    public static final String SWAP = "swap";

    /** Whether an endgameBuilt key belongs to that purpose's map (CAEndgame's own "<id>" and ":-<id>" belong to none). Pure. */
    static boolean belongs(String key, String purpose) {
        if (key == null) return false;
        if (OVERLIMIT.equals(purpose)) return key.endsWith(":zerostab") || key.endsWith(":zerosince") || key.endsWith(":shed")
                || key.contains(":shed:") || key.endsWith(":overlimit");
        if (PRUNE.equals(purpose)) return key.contains(":useless:") || key.endsWith(":pruned");
        if (SWAP.equals(purpose)) return key.endsWith(":replaced");
        return false;
    }

    /**
     * A purpose's map, made from an older save's endgameBuilt: its keys move over (and out of `from`), so nothing is lost and
     * nothing is read twice. Never null; `from` may be. For CAData's lazy getters. Pure (works on the map it's given).
     */
    public static Map<String, Long> split(Map<String, Long> from, String purpose) {
        Map<String, Long> out = new HashMap<String, Long>();
        if (from == null) return out;
        for (Iterator<Map.Entry<String, Long>> it = from.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (!belongs(e.getKey(), purpose)) continue;
            if (e.getValue() != null) out.put(e.getKey(), e.getValue());
            it.remove();
        }
        return out;
    }

    /** Weekly from the daily pass. */
    public static void weekly(CAData d) {
        float day = CADay.now();
        if (day - lastRun < EVERY_DAYS && day >= lastRun) return;
        lastRun = day;
        if (CALog.whatIf()) return;
        long now = Global.getSector().getClock().getTimestamp();
        int keys = CALog.tidy();
        d.overLimitAt(); d.pruneAt(); d.swapAt();   // 0.3.0.0 (fix): an older save's endgameBuilt keys move to their own maps first
        int stamps = expire(d.endgameBuilt, now, KEEP_DAYS, true) + expire(d.overLimitAt, now, KEEP_DAYS, true)
                + expire(d.pruneAt, now, KEEP_DAYS, true) + expire(d.swapAt, now, KEEP_DAYS, false) + expire(d.worldDead, now, KEEP_DAYS, false);
        Set<String> gone = new HashSet<String>();
        com.fs.starfarer.api.campaign.econ.EconomyAPI eco = Global.getSector().getEconomy();
        boolean economyUp = eco != null && !eco.getMarketsCopy().isEmpty();   // never judge "gone" against an empty economy
        if (economyUp && d.colonies != null) for (String id : d.colonies.keySet()) if (id != null && eco.getMarket(id) == null) gone.add(id);
        Set<String> sure = new HashSet<String>(gone);
        sure.retainAll(goneBefore);
        goneBefore.clear();
        goneBefore.addAll(gone);
        int lost = sure.isEmpty() ? 0 : forget(d, sure);
        if (keys + stamps + lost > 0)
            CALog.quiet("saved data tidied: " + keys + " old log keys, " + stamps + " old timestamps" + (sure.isEmpty() ? "" : ", "
                    + lost + " entries of " + sure.size() + " colonies that are gone (" + String.join(", ", sure) + ")"));
    }

    /**
     * Removes stamps older than `days`; with `state`, the keys that are state (":zerosince" in overLimitAt, ":useless:" in
     * pruneAt, or either still in an older save's endgameBuilt) stay however old. Returns how many went. Pure.
     */
    static int expire(Map<String, Long> m, long now, float days, boolean state) {
        if (m == null) return 0;
        int n = 0;
        for (Iterator<Map.Entry<String, Long>> it = m.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            String k = e.getKey();
            // 0.3.0.0 (fix, B4): CATombstone's build stamps ("marketId:industryId@money|rich|endgame") count for the teardown loss
            // for up to HORIZON_MAX (120) months; CATombstone.tidySunk drops them when the building or colony is gone or past that
            if (state && k != null && (k.endsWith(":zerosince") || k.contains(":useless:") || CATombstone.stampKey(k) != null)) continue;
            if (e.getValue() == null || (now > e.getValue() && (now - e.getValue()) / 86400000.0 > days)) { it.remove(); n++; }
        }
        return n;
    }

    /** Removes the keys of `m` that belong to a gone market: "id", or "id" + one of the separators + anything. Returns how many. Pure. */
    static int dropKeys(Collection<String> keys, Set<String> gone, String... seps) {
        if (keys == null || gone.isEmpty()) return 0;
        int n = 0;
        for (Iterator<String> it = keys.iterator(); it.hasNext(); ) {
            String k = it.next();
            if (k != null && owner(k, gone, seps) != null) { it.remove(); n++; }
        }
        return n;
    }

    /** The gone market a key belongs to, or null: the key is the id, or the id followed by a separator. Pure. */
    static String owner(String key, Set<String> gone, String... seps) {
        if (gone.contains(key)) return key;
        for (String sep : seps) {
            for (int i = key.indexOf(sep); i > 0; i = key.indexOf(sep, i + 1)) {
                String id = key.substring(0, i);
                if (gone.contains(id)) return id;
            }
        }
        return null;
    }

    /** Everything kept for these gone markets. Returns how many entries went. */
    static int forget(CAData d, Set<String> gone) {
        int n = 0;
        n += dropKeys(d.colonies == null ? null : d.colonies.keySet(), gone);
        n += dropKeys(d.stepStuck == null ? null : d.stepStuck.keySet(), gone, ":");
        n += dropKeys(d.worldDead == null ? null : d.worldDead.keySet(), gone, ":");
        n += dropKeys(d.endgameBuilt == null ? null : d.endgameBuilt.keySet(), gone, ":");
        n += dropKeys(d.overLimitAt == null ? null : d.overLimitAt.keySet(), gone, ":");   // 0.3.0.0 (fix): the split maps too
        n += dropKeys(d.pruneAt == null ? null : d.pruneAt.keySet(), gone, ":");
        n += dropKeys(d.swapAt == null ? null : d.swapAt.keySet(), gone, ":");
        n += dropKeys(d.surplusBuilt, gone, ":");
        n += dropKeys(d.surplusResearch, gone);
        n += dropKeys(d.siegeSystem == null ? null : d.siegeSystem.keySet(), gone, ":");   // 0.3.0.0 (B5): CASiege also keeps "<marketId>:near" stamps there
        n += dropKeys(d.augRefit == null ? null : d.augRefit.keySet(), gone);
        n += dropKeys(d.upgradeTargets == null ? null : d.upgradeTargets.keySet(), gone, "|");
        if (d.enablers != null) n += dropKeys(d.enablers.keySet(), gone, ":");
        if (d.enablerAt != null) n += dropKeys(d.enablerAt.keySet(), gone, ":");
        n += CAMark.forgetMarkets(gone);   // 0.3.0.0 (B5): per-colony keep: marks (free port reopen, world commitments)
        if (d.stores != null) {
            for (Iterator<Map.Entry<String, com.fs.starfarer.api.campaign.CargoAPI>> it = d.stores.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, com.fs.starfarer.api.campaign.CargoAPI> e = it.next();
                if (!gone.contains(e.getKey())) continue;
                com.fs.starfarer.api.campaign.CargoAPI c = e.getValue();
                if (c == null || c.isEmpty()) { it.remove(); n++; }
                else CALog.quiet("Autopilot Storage of a colony that's gone (" + e.getKey() + ") still holds " + c.getStacksCopy().size() + " stacks; left in the save");
            }
        }
        return n;
    }
}
