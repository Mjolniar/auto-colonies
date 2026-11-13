package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.util.Misc;

import java.util.Map;

/**
 * The one "never 0 stability" rule (0.3.0.0, WS-A2, research 07 M4). Every takedown, move, pull, policy change and new action that
 * can cost a colony stability asks here first: it may go ahead when it doesn't cost stability, or when the colony stays at or
 * above the floor afterwards (and, for an effect that grows with the colony, after one more size too). Before 0.3.0 the rule lived
 * in five places with five numbers (3, 4, 2 and 5, minStability, minStability + 1), and the endgame's and the capital's swaps,
 * administrator moves and the free port didn't look at it at all.
 *
 * The floor is max(3, minStability): colony_autopilot.json's minStability (5 by default), never under 3. Stability here is the
 * game's raw number (what the colony panel adds up, before it's clamped to 0-10), so a colony at 13 has room for a -3 change.
 */
public class CAStability {
    /** The floor never goes under this, whatever minStability says (CAOddisz, CACapital and CALowProfile used 3 before 0.3.0). */
    static final float MIN_FLOOR = CATuning.STABILITY_FLOOR;   // 0.3.0.0 (A3): in CATuning

    /** 0.3.2.1: the floor at one colony: floor(), and 6 where Rogue Planets' Viable Slave Labor or Supercooled Cryo-Sinks punish 5 or lower. */
    public static float floor(MarketAPI m) { return Math.max(floor(), CARogue.stabilityFloor(m)); }

    /** The lowest stability Autopilot will knowingly leave an Auto colony at: max(3, minStability). */
    public static float floor() {
        float min = CATuning.MIN_STABILITY;
        try { min = CAData.settings().minStability; } catch (Throwable ignored) { }   // 0.3.0.0 (A3): a setting now, not CAData's
        return floor(min);
    }

    /** Pure: the floor for a minStability setting. */
    static float floor(float minStability) { return Math.max(MIN_FLOOR, minStability); }

    /** The colony's stability as the game adds it up (unclamped); the shown value if that can't be read. */
    public static float now(MarketAPI m) {
        try { return m.getStability().getModifiedValue(); } catch (Throwable t) { return m.getStabilityValue(); }
    }

    /** Under the floor now: changes with no stability cost of their own that still unsettle a colony (pulling items) wait. */
    public static boolean below(MarketAPI m) { return now(m) < floor(m); }

    // ---------------- the rule ----------------

    /** Whether the colony can take this stability change and stay at or above the floor. why: logged once when it can't (null: silent). */
    public static boolean canLose(MarketAPI m, float delta, String why) { return canLose(m, delta, false, why); }

    /**
     * Whether the colony can take this stability change (and, for effects that grow with the colony's size, one more size) and
     * stay at or above the floor. A change that raises stability or costs nothing is always fine, even under the floor: that's
     * how a colony gets back up. why: what's being held, for one log line ("taking down Mining"); null in rankings, which stay
     * quiet.
     */
    public static boolean canLose(MarketAPI m, float delta, boolean growsWithSize, String why) {
        if (m == null) return false;
        float extra = 0f;
        if (growsWithSize && delta < 0f) {
            int size = m.getSize();
            boolean maxed = false;
            try { maxed = size >= Misc.getMaxMarketSize(m); } catch (Throwable ignored) { }
            if (size > 0 && !maxed) extra = -delta / size;   // a cost of k per size costs k more at the next size
        }
        return decide(m, now(m), delta, extra, why);
    }

    /**
     * For a change already made whose effect is easier to measure than to predict (an item pulled, an edict enacted): whether the
     * colony may keep it, judged from its stability before. False means undo it. why: logged once when it can't.
     */
    public static boolean canKeep(MarketAPI m, float before, String why) {
        if (m == null) return true;
        return decide(m, before, now(m) - before, 0f, why);
    }

    static boolean decide(MarketAPI m, float stability, float delta, float extra, String why) {
        float floor = floor(m);   // 0.3.2.1: the colony's own floor
        boolean ok = canLose(stability, delta, extra, floor);
        if (why == null) return ok;
        String key = "stab:hold:" + m.getId() + ":" + why;
        try {
            if (ok) CALog.forget(key);
            else if (CALog.once(key))
                CALog.think(m.getName() + ": " + why + " waits. Stability " + Math.round(stability) + " would go to "
                        + Math.round(after(stability, delta, extra)) + ", under Autopilot's floor of " + Math.round(floor) + ".");
        } catch (Throwable ignored) { }   // the rule never fails because the log did
        return ok;
    }

    /**
     * Pure form for the offline check: false when the change costs stability and the colony would end under the floor (never
     * under 1), counting extraNextSize more for an effect that grows with the colony's next size.
     */
    static boolean canLose(float stability, float delta, float extraNextSize, float floor) {
        float cost = -delta + Math.max(0f, extraNextSize);
        if (cost <= 0f) return true;   // raises stability, or costs nothing
        return stability - cost >= Math.max(floor, 1f);
    }

    static float after(float stability, float delta, float extraNextSize) { return stability + delta - Math.max(0f, extraNextSize); }

    // ---------------- what a change does to a colony's stability ----------------

    /** What a building adds to the colony's stability, from the game's own modifiers ("ind_<id>" and "ind_<id>_<n>", BaseIndustry.getModId). */
    public static float of(MarketAPI m, Industry ind) {
        if (m == null || ind == null) return 0f;
        try { return sum(m.getStability().getFlatMods(), "ind_" + ind.getId()); } catch (Throwable t) { return 0f; }
    }

    /** Pure: the flat modifiers under key or key_<anything>. */
    static float sum(Map<String, MutableStat.StatMod> mods, String key) {
        float v = 0f;
        if (mods == null) return v;
        for (Map.Entry<String, MutableStat.StatMod> e : mods.entrySet())
            if (e.getKey().equals(key) || e.getKey().startsWith(key + "_")) v += e.getValue().value;
        return v;
    }

    /** The industry limit's penalty the colony pays now (PopulationAndInfrastructure's "_overmax" modifier, 5), 0 when within it. */
    public static float overLimitPenalty(MarketAPI m) {
        try { return penalty(m.getStability().getFlatMods()); } catch (Throwable t) { return 0f; }
    }

    /** Pure: the size of the "_overmax" modifier, as a positive number. */
    static float penalty(Map<String, MutableStat.StatMod> mods) {
        float v = 0f;
        if (mods == null) return v;
        for (Map.Entry<String, MutableStat.StatMod> e : mods.entrySet()) if (e.getKey().endsWith("_overmax")) v += e.getValue().value;
        return Math.max(0f, -v);
    }

    /** What taking this building down does to the colony's stability now: its own share comes off, and the limit's penalty lifts if it was the last one over. */
    public static float takeDownDelta(MarketAPI m, Industry ind) {
        int num = 0, max = Integer.MAX_VALUE;
        try { num = Misc.getNumIndustries(m); max = Misc.getMaxIndustries(m); } catch (Throwable ignored) { }
        boolean counted = false;
        try { counted = ind.isIndustry(); } catch (Throwable ignored) { }
        // 0.3.2.1: and what it brings through Rogue Planets conditions (Pilgrim World's +1, a pair's -1 or -3) comes off too
        return takeDownDelta(of(m, ind), overLimitPenalty(m), num, max, counted) - CARogue.stabilityShare(m, ind);
    }

    /** 0.3.2.1: what a building adds to the colony's stability: its own modifiers and what it brings through Rogue Planets conditions. */
    public static float share(MarketAPI m, Industry ind) { return of(m, ind) + CARogue.stabilityShare(m, ind); }

    /** Pure: own = what it adds now; penalty = the limit's penalty now (positive); the game's count and limit; whether the game counts it. */
    static float takeDownDelta(float own, float penalty, int gameCount, int max, boolean countedByGame) {
        float d = -own;
        if (penalty > 0f && countedByGame && gameCount - 1 <= max) d += penalty;
        return d;
    }

    // ---------------- administrators and the colony limit ----------------

    /** Stability an administrator brings with their own skills: an AI core's Hypercognition (+1) and Vulpoids' Profecto (+1). */
    public static float adminOwn(PersonAPI p) {
        if (p == null) return 0f;
        try { if (p.isPlayer() || p.getStats() == null) return 0f; } catch (Throwable t) { return 0f; }
        float v = 0f;
        try { if (p.getStats().getSkillLevel("hypercognition") > 0f) v += com.fs.starfarer.api.impl.campaign.skills.Hypercognition.STABILITY_BONUS; }
        catch (Throwable ignored) { }
        try { if (p.getStats().getSkillLevel("vulpoid_brain") > 0f) v += 1f; } catch (Throwable ignored) { }
        return v;
    }

    /**
     * The colony limit's number right now (PopulationAndInfrastructure.getMismanagementPenalty): what every colony you govern
     * yourself loses; negative under the limit, where it's the "Management bonus" each of them gets.
     */
    public static float governedPenalty() {
        try { return PopulationAndInfrastructure.getMismanagementPenalty(); } catch (Throwable t) { return 0f; }
    }

    /** Pure: an administrator takes over a colony you governed (own: what they bring; penalty: the colony limit's number before). */
    static float adminArrives(float own, float penalty) { return own + penalty; }

    /** Pure: an administrator leaves a colony that you then govern (the colony limit's number stays the same in a move). */
    static float adminLeaves(float own, float penalty) { return -own - penalty; }

    /** What one more colony governed by you costs every colony you govern (Misc.getOutpostPenalty, 2), past the limit or under it. */
    public static float oneMoreGoverned() {
        try { return Misc.getOutpostPenalty(); } catch (Throwable t) { return 2f; }
    }

    /**
     * Whether every colony you govern yourself can take delta (for Auto-expansion: a new colony you'd govern costs each of them
     * oneMoreGoverned()). why: logged once for the first one that can't.
     */
    public static boolean canGovernedLose(float delta, String why) {
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            try {
                if (m.getAdmin() != null && !m.getAdmin().isPlayer()) continue;
                if (CAEngine.isOutpost(m)) continue;
            } catch (Throwable ignored) { continue; }
            if (!canLose(m, delta, why)) return false;
        }
        return true;
    }
}
