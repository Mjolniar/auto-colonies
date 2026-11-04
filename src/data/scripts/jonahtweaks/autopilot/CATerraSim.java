package data.scripts.jonahtweaks.autopilot;

import java.util.*;

/**
 * Terraforming Made Easy's rules on a set of planet conditions, for planning surplus terraforming (CATerraformSurplus)
 * without touching the game: what a finished project adds and removes, and the best next one or two projects.
 * Mirrors BaseTerraformingIndustry 2.9.8: adding a condition removes its "hated" ones (very hot and extreme tectonic
 * activity both hate habitable), removing one is the same project at REMOVAL_COST_MULTIPLIER, and after every project
 * updatePlanetConditions runs: a planet left with no harsh condition becomes habitable and gains a step of farmland and
 * organics; harsh ones take farmland, organics, water and lobster pens away. Reads TME's project table
 * (CAWorldPlanner.Row) only, so it loads without TME.
 */
public class CATerraSim {
    /** Conditions that keep TME from making a planet habitable. */
    static final String[] HARSH = {"very_cold", "very_hot", "no_atmosphere", "thin_atmosphere", "dense_atmosphere", "toxic_atmosphere",
            "irradiated", "dark", "extreme_tectonic_activity"};
    static final String[] FARMLAND = {"farmland_poor", "farmland_adequate", "farmland_rich", "farmland_bountiful"};
    static final String[] ORGANICS = {"organics_trace", "organics_common", "organics_abundant", "organics_plentiful"};
    /** Structures surplus terraforming leaves alone: the Unification Center's worlds are the colony plan's, and the Stellar
     *  Manufactory needs mirror and shade choices. */
    static final Set<String> SKIP = new HashSet<String>(Arrays.asList("tme_unification_center", "tme_stellar_manufactory"));
    static final int DEPTH = 2;
    /** Less than this a month isn't worth a project (Max output mode). */
    static final float MIN_GAIN = 1000f;
    /** "Pays back" mode: the price has to come back within this many months. */
    static final float PAYBACK_MONTHS = 120f;

    /** What a structure's best plan is worth a month at colonies where research still locks it (CAResearch values its tech by this). */
    public static final Map<String, Float> lockedValue = new HashMap<String, Float>();
    /** One line for the empire panel: the next surplus terraforming move, or why there's none. */
    public static String summary;
    /** Each colony's plan and the day it was made (CATerraformSurplus keeps them here, so a load can clear them without TME). */
    static final Map<String, Result> plans = new HashMap<String, Result>();
    static final Map<String, Float> planAt = new HashMap<String, Float>();

    public static void reset() { plans.clear(); planAt.clear(); lockedValue.clear(); summary = null; }

    public static class Move {
        public String structure, condition;
        public boolean add;
        public float cost, days;
        public String label() { return (add ? "add " : "remove ") + CAWorldPlanner.name(condition); }
    }

    /** One or two projects in order, what the colony is worth after them, and what they cost with any structure to build. */
    public static class Seq {
        public final List<Move> moves = new ArrayList<Move>();
        public final Set<String> builds = new LinkedHashSet<String>();   // structures to build first, in order
        public Set<String> after;
        public float gain, cost;
        public String locked;   // the first structure research still locks, or null
        public float ratio() { return cost <= 0f ? gain : gain / cost; }
        public String text() {
            List<String> l = new ArrayList<String>();
            for (Move m : moves) l.add(m.label());
            return CAContracts.join(l);
        }
    }

    /** What the planner needs from the colony: the value of an end state and each structure's price, upkeep and availability. */
    public interface Colony {
        /** Monthly value of going from today's conditions to these (CAConditions.change). */
        float value(Set<String> after);
        boolean has(String structure);
        /** Built by surplus terraforming, so it's taken down when its work is done: its upkeep counts only while it works. */
        boolean ours(String structure);
        /** Research and the build menu allow it here now. */
        boolean buildable(String structure);
        float buildCost(String structure);
        float buildDays(String structure);
        float upkeep(String structure);
    }

    static boolean meets(Set<String> conds, String liked) {
        List<Boolean> all = new ArrayList<Boolean>();
        List<String[]> gs = CAWorldPlanner.groups(liked, all);
        for (int i = 0; i < gs.size(); i++) {
            boolean any = false, every = true;
            for (String id : gs.get(i)) { if (conds.contains(id)) any = true; else every = false; }
            if (all.get(i) ? !every : !any) return false;
        }
        return true;
    }

    /** The projects TME offers on these conditions: add one whose liked conditions are there, or remove one that's present. */
    static List<Move> moves(Set<String> conds, boolean gasGiant, Collection<CAWorldPlanner.Row> rows, Set<String> keep, float costMult, float removalMult) {
        List<Move> out = new ArrayList<Move>();
        for (CAWorldPlanner.Row r : rows) {
            if (SKIP.contains(r.structure) || (gasGiant && !r.gasGiants)) continue;
            Move m = new Move();
            m.structure = r.structure; m.condition = r.condition; m.days = r.days;
            m.add = !conds.contains(r.condition);
            if (!m.add && keep.contains(r.condition)) continue;
            if (m.add && !meets(conds, r.liked)) continue;
            m.cost = r.cost * costMult * (m.add ? 1f : removalMult);
            out.add(m);
        }
        return out;
    }

    /** The conditions once a project finishes. */
    static Set<String> apply(Set<String> conds, Move m, CAWorldPlanner.Row r, boolean gasGiant) {
        Set<String> c = new HashSet<String>(conds);
        if (m.add) { c.add(m.condition); c.removeAll(CAWorldPlanner.idsOf(r.hated)); }
        else c.remove(m.condition);
        settle(c, gasGiant);
        return c;
    }

    /** TME's updatePlanetConditions. */
    static void settle(Set<String> c, boolean gasGiant) {
        boolean removeFarming = false, removeOrganics = false, reduceOrganics = false, removeLobsters = true, removeWater = true;
        if (!gasGiant && !c.contains("habitable") && !any(c, HARSH)) {
            step(c, FARMLAND); step(c, ORGANICS); c.add("habitable");
        }
        if (c.contains("water_surface")) { removeFarming = true; removeLobsters = false; removeWater = false; }
        if (any(c, "no_atmosphere", "very_hot", "very_cold", "extreme_tectonic_activity")) { removeFarming = removeOrganics = removeLobsters = removeWater = true; }
        if (c.contains("thin_atmosphere") || c.contains("toxic_atmosphere")) { removeFarming = reduceOrganics = removeLobsters = removeWater = true; }
        if (c.contains("irradiated")) { removeFarming = removeOrganics = removeLobsters = removeWater = true; reduceOrganics = false; }
        if (!c.contains("habitable") && !c.contains("no_atmosphere") && any(c, "tectonic_activity", "extreme_tectonic_activity")
                && any(c, "very_hot", "hot", "very_cold")) { removeFarming = removeOrganics = removeLobsters = removeWater = true; reduceOrganics = false; }
        if (gasGiant) { removeFarming = removeOrganics = removeLobsters = removeWater = true; reduceOrganics = false; }
        if (removeFarming) c.removeAll(Arrays.asList(FARMLAND));
        if (removeLobsters) c.remove("volturnian_lobster_pens");
        if (removeWater) c.remove("water_surface");
        if (reduceOrganics) {
            if (c.contains("organics_abundant") || c.contains("organics_plentiful")) { c.remove("organics_abundant"); c.remove("organics_plentiful"); c.add("organics_common"); }
        } else if (removeOrganics) c.removeAll(Arrays.asList(ORGANICS));
    }

    /** One step up a ladder, or its first rung when there's none (addOrImproveFarming / addOrImproveOrganics). */
    static void step(Set<String> c, String[] ladder) {
        for (int i = 0; i < ladder.length - 1; i++) if (c.contains(ladder[i])) { c.remove(ladder[i]); c.add(ladder[i + 1]); return; }
        if (!c.contains(ladder[ladder.length - 1])) c.add(ladder[0]);
    }

    static boolean any(Set<String> c, String... ids) { for (String id : ids) if (c.contains(id)) return true; return false; }

    /** Every one- and two-project sequence that keeps what the colony's plan needs (keep) and adds nothing it wants gone (avoid). */
    static List<Seq> sequences(Set<String> conds, boolean gasGiant, Map<String, CAWorldPlanner.Row> rows, Set<String> keep, Set<String> avoid,
                               float costMult, float removalMult, Colony col) {
        List<Seq> out = new ArrayList<Seq>();
        for (Move a : moves(conds, gasGiant, rows.values(), keep, costMult, removalMult)) {
            Set<String> s1 = apply(conds, a, rows.get(a.condition), gasGiant);
            if (!valid(conds, s1, keep, avoid)) continue;
            out.add(seq(col, s1, a));
            if (DEPTH < 2) continue;
            for (Move b : moves(s1, gasGiant, rows.values(), keep, costMult, removalMult)) {
                if (b.condition.equals(a.condition)) continue;
                Set<String> s2 = apply(s1, b, rows.get(b.condition), gasGiant);
                if (!valid(conds, s2, keep, avoid)) continue;
                out.add(seq(col, s2, a, b));
            }
        }
        return out;
    }

    static boolean valid(Set<String> before, Set<String> after, Set<String> keep, Set<String> avoid) {
        for (String k : keep) if (before.contains(k) && !after.contains(k)) return false;
        for (String a : avoid) if (!before.contains(a) && after.contains(a)) return false;
        return true;
    }

    /**
     * A structure surplus terraforming builds is taken down once its work is done (TME's changes stay), so its price
     * and its upkeep while it's built and works count as one-off costs; one the colony keeps anyway costs nothing extra.
     */
    static Seq seq(Colony col, Set<String> after, Move... ms) {
        Seq q = new Seq();
        q.after = after;
        for (Move m : ms) {
            q.moves.add(m);
            q.cost += m.cost;
            boolean build = !col.has(m.structure);
            if (build && q.builds.add(m.structure)) {
                q.cost += col.buildCost(m.structure) + col.upkeep(m.structure) * col.buildDays(m.structure) / 30f;
                if (q.locked == null && !col.buildable(m.structure)) q.locked = m.structure;
            }
            if (build || col.ours(m.structure)) q.cost += col.upkeep(m.structure) * m.days / 30f;
        }
        q.gain = col.value(after);
        return q;
    }

    /** Whether a sequence is worth doing with surplus credits: mode 0 anything that adds value, 1 only if it pays back in 10 years. */
    static boolean worth(Seq q, int mode) {
        if (q.gain < MIN_GAIN) return false;
        return mode == 0 || q.gain * PAYBACK_MONTHS >= q.cost;
    }

    /** The plan for one colony: the best sequence to start now (most value per credit), and per locked structure the best it would allow. */
    public static class Result {
        public Seq best;
        public final Map<String, Seq> locked = new LinkedHashMap<String, Seq>();
        public final Set<String> wanted = new HashSet<String>();   // structures some worthwhile sequence starts with
    }

    static Result plan(Set<String> conds, boolean gasGiant, Map<String, CAWorldPlanner.Row> rows, Set<String> keep, Set<String> avoid,
                       float costMult, float removalMult, int mode, Colony col) {
        return plan(conds, gasGiant, rows, keep, avoid, costMult, removalMult, mode, col, false);
    }

    /**
     * Hazards that are only ever added as the first of two projects whose second needs them: tectonic activity for moderate
     * ore and transplutonics, extreme tectonic activity for abundant, rich and ultrarich (TME's liked conditions).
     */
    static final Set<String> ENABLERS = new HashSet<String>(Arrays.asList("tectonic_activity", "extreme_tectonic_activity"));

    /** A sequence that adds an enabler must add it first, for a second project that adds something needing it. */
    static boolean enablerOk(Seq q, Set<String> conds, Map<String, CAWorldPlanner.Row> rows) {
        for (int i = 0; i < q.moves.size(); i++) {
            Move m = q.moves.get(i);
            if (!m.add || !ENABLERS.contains(m.condition) || conds.contains(m.condition)) continue;
            if (i != 0 || q.moves.size() < 2 || !q.moves.get(1).add) return false;
            CAWorldPlanner.Row r2 = rows.get(q.moves.get(1).condition);
            if (r2 == null || !CAWorldPlanner.idsOf(r2.liked).contains(m.condition)) return false;
        }
        return true;
    }

    /** byGain: the sequence that adds most a month (spending freely), not the most per credit. */
    static Result plan(Set<String> conds, boolean gasGiant, Map<String, CAWorldPlanner.Row> rows, Set<String> keep, Set<String> avoid,
                       float costMult, float removalMult, int mode, Colony col, boolean byGain) {
        Result r = new Result();
        for (Seq q : sequences(conds, gasGiant, rows, keep, avoid, costMult, removalMult, col)) {
            if (!worth(q, mode) || !enablerOk(q, conds, rows)) continue;
            if (q.locked != null) {
                Seq cur = r.locked.get(q.locked);
                if (cur == null || q.gain > cur.gain) r.locked.put(q.locked, q);
                continue;
            }
            r.wanted.add(q.moves.get(0).structure);
            if (r.best == null || (byGain ? q.gain > r.best.gain : q.ratio() > r.best.ratio())) r.best = q;
        }
        return r;
    }
}
