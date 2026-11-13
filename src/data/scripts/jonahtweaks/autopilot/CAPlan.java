package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * Colony plans: a target layout chosen ahead of time from the planet, its hazards and resources, and the profile.
 * Plan steps are favoured in scoring; the first unfinished build and terraforming steps get the biggest bonus.
 * 0.3.0.0 (WS-B2, research 02 sections 1 and 15): the fixed bonus by rank is only for true enablers (Terraforming Made Easy
 * structures and the buildings a special world needs, special-project producers, the capital's trade buildings, stations,
 * spending freely's structures, the Research Facility, the Bifrost); an ordinary step stands on its own worth plus half of
 * what the plan steps it opens the way to would add (CAEngine.finish). A step that has scored nothing for
 * CAHorizon.WORTHLESS_DAYS takes no rank and holds no industry slot until it's worth something again (worthless), an
 * off-plan industry must beat the plan industry it would push out of a slot (reserve), and a calm colony's station steps
 * come after its first open economic step (build) and score as waiting while it's open (stationWaitsFor).
 *
 * Plans work with any mix of mods: a reachable Terraforming Made Easy world first (CAWorldPlanner), else a plain plan
 * for the goal. Any step whose building or condition the game doesn't have (its mod isn't installed) is dropped, so
 * it never takes a plan rank; Ashes of the Domain's own versions count through their upgrade links (leadsTo), and
 * research-locked steps are what CAResearch works toward. A colony whose plan steps are all done or blocked still
 * builds whatever is worth most on its own: the plan only adds priority.
 */
public class CAPlan {
    public enum Kind { BUILD, COND, NOCOND }

    public static class Step {
        public Kind kind; public String id, label;
        public boolean exact;   // the Unification Center checks this building by id, so upgraded versions don't count
        public boolean extra;   // put in front of the colony's own plan (research, projects, spending freely, wonders): not the world's
        public Step(Kind k, String id, String label) { this.kind = k; this.id = id; this.label = label; }
        public boolean done(MarketAPI m) {
            if (kind == Kind.COND) return m.hasCondition(id);
            if (kind == Kind.NOCOND) return !m.hasCondition(id);
            return exact ? inChain(m, id) : hasOrBetter(m, id);
        }
    }

    public static class Plan {
        public String name;
        public String world;                                              // TME world condition this plan ends in, if any
        public java.util.Set<String> exact = new java.util.HashSet<String>();   // buildings the Unification Center checks by exact id
        public List<Step> steps = new ArrayList<Step>();
        public int doneCount(MarketAPI m) { int n = 0; for (Step s : steps) if (s.done(m)) n++; return n; }
    }

    static final String[][] CHAINS = {
            {"spaceport", "megaport"}, {"heavyindustry", "orbitalworks"}, {"grounddefenses", "heavybatteries"},
            {"patrolhq", "militarybase", "highcommand"}, {"orbitalstation", "battlestation", "starfortress"},
            {"orbitalstation_mid", "battlestation_mid", "starfortress_mid"}, {"orbitalstation_high", "battlestation_high", "starfortress_high"},
            {"ii_orbitalstation", "ii_battlestation", "ii_starfortress", "ii_stellacastellum"},
            {"kol_battlestation_libra", "kol_battlestar_libra"}, {"IndEvo_ComArray", "IndEvo_IntArray"},
            {"fuelprod", "ix_fuel_production"}};

    /**
     * True when the colony has this building or something upgraded from it: a later step of a known chain, or a
     * building whose downgrade links lead back to it (AotD's Artisanal Farming counts as Farming, Farming as Monoculture Plots).
     */
    static boolean hasOrBetter(MarketAPI m, String id) {
        if (m != null && m == focusAt) {   // 0.3.0.0 (A4): kept for the evaluation running at this colony
            Boolean b = FOCUS_HAS.get(id);
            if (b == null) { b = hasOrBetterNow(m, id); FOCUS_HAS.put(id, b); }
            return b;
        }
        return hasOrBetterNow(m, id);
    }

    static boolean hasOrBetterNow(MarketAPI m, String id) {
        if (inChain(m, id)) return true;
        if (id.equals("farming") && m.hasIndustry("aquaculture")) return true;   // a water world's farming (vanilla Farming can't go there)
        for (Industry i : m.getIndustries()) if (leadsTo(id, i.getId())) return true;
        return false;
    }

    /** True when the colony has this building or a later step of one of the fixed upgrade chains. */
    static boolean inChain(MarketAPI m, String id) {
        if (m != null && m == focusAt) {   // 0.3.0.0 (A4): kept for the evaluation running at this colony
            Boolean b = FOCUS_CHAIN.get(id);
            if (b == null) { b = inChainNow(m, id); FOCUS_CHAIN.put(id, b); }
            return b;
        }
        return inChainNow(m, id);
    }

    static boolean inChainNow(MarketAPI m, String id) {
        if (m.hasIndustry(id)) return true;
        if (id.equals("station")) { for (Industry i : m.getIndustries()) if (i.getSpec().hasTag("station")) return true; return false; }
        for (String[] chain : CHAINS) {
            int at = -1;
            for (int i = 0; i < chain.length; i++) if (chain[i].equals(id)) at = i;
            if (at < 0) continue;
            for (int i = at + 1; i < chain.length; i++) if (m.hasIndustry(chain[i])) return true;
        }
        return false;
    }

    /**
     * A station's tier from its tags, so every mod's stations count like vanilla's (Interstellar Imperium, Knights of Ludd and
     * Ashes of the Domain tag theirs the same way): 0 not a station (or vanilla's abandoned station_base, which has no
     * fleet), 1 orbital station, 2 battlestation, 3 star fortress, 4 a tier above (Imperium's Stella Castellum, AotD's Star
     * Citadels).
     */
    public static int stationTier(IndustrySpecAPI spec) {
        if (spec == null || !spec.hasTag("station") || spec.hasTag("parent_item")) return 0;
        if (spec.hasTag("starcitadel") || spec.getId().equals("ii_stellacastellum")) return 4;
        if (spec.hasTag("starfortress")) return 3;
        if (spec.hasTag("battlestation")) return 2;
        return 1;
    }

    /** 0.3.0.0 (A4): kept for the load (specs don't change in a game); an id the game doesn't have is remembered too, since
     *  asking for one throws, and the plan asked for "station" and every missing mod's step that way over and over. */
    static IndustrySpecAPI spec(String id) {
        if (id == null) return null;
        sameSettings();
        IndustrySpecAPI sp = SPECS.get(id);
        if (sp != null || MISSING.contains(id)) return sp;
        try { sp = Global.getSettings().getIndustrySpec(id); } catch (Throwable t) { sp = null; }
        if (sp == null) MISSING.add(id); else SPECS.put(id, sp);
        return sp;
    }

    /** True when target is reached by upgrading from: from appears in target's chain of downgrade links. */
    static boolean leadsTo(String from, String target) {
        return from != null && target != null && downgrades(target).contains(from);
    }

    /**
     * 0.3.0.0 (A4): target's downgrade links (at most 8, as leadsTo always walked them), read once per load. leadsTo was asked
     * thousands of times an evaluation (every plan step, for every pick, for every building), each a fresh walk.
     */
    static List<String> downgrades(String target) {
        sameSettings();
        List<String> l = DOWN.get(target);
        if (l != null) return l;
        l = new ArrayList<String>(2);
        String cur = target;
        for (int k = 0; k < 8 && cur != null; k++) {
            IndustrySpecAPI s = spec(cur);
            if (s == null) break;
            cur = s.getDowngrade();
            if (cur != null) l.add(cur);
        }
        DOWN.put(target, l);
        return l;
    }

    // ---------------- 0.3.0.0 (A4): memos (research 07 M7) ----------------

    static final java.util.Map<String, IndustrySpecAPI> SPECS = new java.util.HashMap<String, IndustrySpecAPI>();
    static final java.util.Set<String> MISSING = new java.util.HashSet<String>();
    static final java.util.Map<String, List<String>> DOWN = new java.util.HashMap<String, List<String>>();
    /** The game settings the specs above came from: the offline checks swap them, and a new one starts over. */
    static Object specsFrom = null;

    static void sameSettings() {
        Object s = Global.getSettings();
        if (s != specsFrom) { SPECS.clear(); MISSING.clear(); DOWN.clear(); specsFrom = s; }
    }

    /**
     * While one evaluation runs (CAEngine.evaluate) the colony's buildings can't change, so whether it has a building (or
     * better) is kept for that evaluation: the plan bonus asked it for every plan step, for every pick. focus(m) starts it
     * and returns true when this call started it; focus(null) ends it. An evaluation inside another leaves the outer one's.
     */
    static MarketAPI focusAt = null;
    static final java.util.Map<String, Boolean> FOCUS_HAS = new java.util.HashMap<String, Boolean>(), FOCUS_CHAIN = new java.util.HashMap<String, Boolean>();

    static boolean focus(MarketAPI m) {
        if (m != null && focusAt != null) return false;
        focusAt = m;
        FOCUS_HAS.clear(); FOCUS_CHAIN.clear();
        return m != null;
    }

    /**
     * The colony's own plan (buildOwn: the Terraforming Made Easy world search, else the plain plan for its goal) was worked
     * out again by each of 16 callers, several times a day per colony; with TME that's a depth-4 search with copied sets each
     * time. It only depends on the colony's goal, size, slots, conditions and buildings, the worlds dropped there and surplus
     * terraforming's enablers (planSig), so it's kept per colony and goal for the day while those stay the same and the
     * epoch doesn't move. Every caller gets its own copy. The steps in front (capital, stations, research, projects,
     * spending freely, wonders) are still asked every time: they follow settings, money and projects, not the colony.
     */
    static final CACache.Memo<Plan> OWN = new CACache.Memo<Plan>("plans", 400);

    static Plan own(MarketAPI m, CAData.Profile prof) {
        int day = CAEngine.today();
        if (day < 0 || m == null || prof == null) return buildOwn(m, prof);
        int ep = CACache.epoch();
        long sig;
        try { sig = planSig(m); } catch (Throwable t) { return buildOwn(m, prof); }
        String key = m.getId() + "|" + prof.name();
        Plan p = OWN.get(key, day, sig);
        if (p == null) {
            long t0 = System.nanoTime();
            p = OWN.put(key, day, ep, sig, buildOwn(m, prof));
            CAPerf.record("plan worked out", System.nanoTime() - t0);
        }
        return copy(p);
    }

    /** What buildOwn reads from the colony and the save. */
    static long planSig(MarketAPI m) {
        long h = CACache.mix(m.getSize(), com.fs.starfarer.api.util.Misc.getMaxIndustries(m));
        h = CACache.mix(h, com.fs.starfarer.api.util.Misc.getMaxMarketSize(m));
        h = CACache.mix(h, m.hasTag("aotd_hypershunt") ? 1 : 0);
        long conds = 0L;
        for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : m.getConditions()) conds += CACache.spread(c.getId());
        h = CACache.mix(h, conds);
        long inds = 0L;
        for (Industry i : m.getIndustries()) inds += CACache.mix(CACache.spread(i.getId()), i.isHidden() ? 1 : 0);
        h = CACache.mix(h, inds);
        CAData d = CAData.get();
        h = CACache.mix(h, d.worldDead().hashCode());
        return CACache.mix(h, d.enablers().hashCode());
    }

    /** A plan of its own for the caller: the same steps (never changed once made) in a new list. */
    static Plan copy(Plan p) {
        Plan c = new Plan();
        c.name = p.name; c.world = p.world;
        c.exact = new java.util.HashSet<String>(p.exact);
        c.steps = new ArrayList<Step>(p.steps);
        return c;
    }

    /** On load and on a rescan (CAEngine.resetCaches). */
    static void resetCaches() {
        SPECS.clear(); MISSING.clear(); DOWN.clear(); specsFrom = null;
        focusAt = null; FOCUS_HAS.clear(); FOCUS_CHAIN.clear();
        lockedDay = -1; lockedCache = 0;
        WORTH.clear(); WORTHLESS_LOGGED.clear(); worthSeq = 0;   // 0.3.0.0 (B2): what plan steps scored, this session only
        quietMemo.clear(); quietDay = Integer.MIN_VALUE;   // 0.3.0.0 (B2 fix): the day's calm colonies
    }

    static boolean has(MarketAPI m, String prefix) {
        for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : m.getConditions()) if (c.getId().startsWith(prefix)) return true;
        return false;
    }

    /** Structures that only serve a special world (they add its conditions, or run its last project). */
    static final java.util.Set<String> WORLD_STEPS = new java.util.HashSet<String>(java.util.Arrays.asList("tme_unification_center",
            "tme_mineral_replicator", "tme_element_synthesizer", "tme_agricultural_laboratory"));

    /** Whether the game has this step's building or condition: a step from a mod that isn't installed is dropped. */
    static boolean exists(Step s) {
        try {
            if (s.kind == Kind.BUILD) return s.id.equals("station") || Global.getSettings().getIndustrySpec(s.id) != null;
            return Global.getSettings().getMarketConditionSpec(s.id) != null;
        } catch (Throwable t) { return false; }
    }

    static void b(Plan p, String id, String label) { p.steps.add(new Step(Kind.BUILD, id, label)); }
    static void c(Plan p, String id, String label) { p.steps.add(new Step(Kind.COND, id, label)); }
    static void n(Plan p, String id, String label) { p.steps.add(new Step(Kind.NOCOND, id, label)); }

    /** The colony's plan, with any producer a special project needs from it first (CAProjects). */
    public static Plan build(MarketAPI m, CAData.Profile prof) {
        Plan p = own(m, prof);   // 0.3.0.0 (A4): the colony's own plan from the day memo, as a copy of its own
        List<Step> extra = new ArrayList<Step>(CACapital.stepsFor(m));   // the capital's commerce build-up first (0.2.42.0)
        List<Step> stations = CAStations.stepsFor(m);
        extra.addAll(stations);   // and a station at every colony, a bigger one at bigger colonies (0.2.42.0)
        extra.addAll(CAResearch.stepsFor(m));   // a Research Facility when there's none and research is Auto
        extra.addAll(CAProjects.stepsFor(m));
        extra.addAll(CAFloors.stepsFor(m));     // 0.3.2.0: production floor producers (none with production Off)
        extra.addAll(CASurplus.richSteps(m));   // spending freely: every station and defense maxed
        extra.addAll(CAWonders.stepsFor(m));    // and the Bifrost Control Center at one colony
        if (extra.isEmpty()) return p;
        List<Step> steps = new ArrayList<Step>();
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (Step s : extra) if (exists(s) && ids.add(s.id)) { s.extra = true; steps.add(s); }
        for (Step s : p.steps) if (s.kind != Kind.BUILD || !ids.contains(s.id)) steps.add(s);
        // 0.3.0.0 (B2, research 02 section 15, 01 section 10): a calm colony's station steps come after its first open economic
        // step, so every colony stops spending 90 days on a 250,000 station before anything that earns; a threatened colony
        // (threat high, or just not low), a border colony, the capital and a Security or War goal keep them in front (calm; a
        // colony whose threat goes high gets them back the same day, a new hostile neighbor the next). B2 fix: the order alone
        // barely moved the scores, so CAEngine.finish also holds the waiting station's rank bonus back (stationWaitsFor)
        if (!stations.isEmpty() && calm(m)) {
            try {
                java.util.Set<Step> moving = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Step, Boolean>());
                moving.addAll(stations);
                Step first = null;
                for (Step s : steps) {
                    if (!openOwn(m, p, s)) continue;
                    first = s;
                    break;
                }
                steps = stationsAfter(steps, moving, first);
            } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "station order failed at " + m.getName(), t); }
        }
        p.steps = steps;
        return p;
    }

    /**
     * 0.3.0.0 (B2): pure: the steps with the moving ones (a calm colony's station steps) taken out and put back right after
     * `first` (the colony's first open economic step), in their own order. No `first`, or not there: unchanged.
     */
    static List<Step> stationsAfter(List<Step> steps, java.util.Set<Step> moving, Step first) {
        if (first == null || moving.isEmpty() || moving.contains(first) || !steps.contains(first)) return steps;
        List<Step> out = new ArrayList<Step>(steps.size()), moved = new ArrayList<Step>();
        for (Step s : steps) if (moving.contains(s)) moved.add(s);
        if (moved.isEmpty()) return steps;
        for (Step s : steps) {
            if (moving.contains(s)) continue;
            out.add(s);
            if (s == first) out.addAll(moved);
        }
        return out;
    }

    /**
     * 0.3.0.0 (B2): an open economic step of the colony's own plan (not one put in front of it): a build step not done, not
     * blocked by research, not out of reach and not worthless. The first one is what a calm colony's station waits behind.
     */
    static boolean openOwn(MarketAPI m, Plan plan, Step s) {
        return !s.extra && s.kind == Kind.BUILD && !s.done(m) && !BLOCKED.contains(key(m, s.id)) && !dead(m, s.id) && !worthless(m, plan, s);
    }

    /**
     * 0.3.0.0 (B2): a calm colony for the station order: not the capital, not on the Security or War goal, threat not high
     * (the saved state CAThreat keeps; read only, so building a plan logs and changes nothing). Never with legacyPlanner.
     * B2 fix (research 02 section 15: the station keeps its rank when the threat "is not low or the colony is a border
     * colony"): threatHigh only flips at CAThreat.THRESHOLD 0.7 and hostile neighbors alone cap at 0.6, so a border colony
     * and a medium-threat one counted as calm. Now also not calm with a hostile market within BORDER_LY, or with a threat
     * estimate over CALM_THREAT (quiet).
     */
    static boolean calm(MarketAPI m) {
        if (m == null || legacy()) return false;
        try {
            if (CACapital.is(m)) return false;
            CAData.Colony c = CAData.get().colony(m.getId());
            if (c.threatHigh || c.profile == CAData.Profile.SECURITY || c.profile == CAData.Profile.WAR) return false;
            return quiet(m);
        } catch (Throwable t) { return false; }
    }

    /** 0.3.0.0 (B2 fix): the threat estimate a calm colony stays at or under (CAThreat.estimate): half its neighbors-only cap
     *  of 0.6, the Hostile Activity meter at three quarters by itself (THRESHOLD, where threatHigh flips, is 0.7). */
    static final float CALM_THREAT = 0.3f;
    /** 0.3.0.0 (B2 fix): a hostile market this close makes a border colony (CAThreat.estimate's neighbor range). */
    static final float BORDER_LY = 8f;
    /** 0.3.0.0 (B2 fix): quiet() per colony for the game day (it reads every market in the sector); never kept offline. */
    static final java.util.Map<String, Boolean> quietMemo = new java.util.HashMap<String, Boolean>();
    static int quietDay = Integer.MIN_VALUE;

    /** 0.3.0.0 (B2 fix): no hostile market within BORDER_LY and a threat estimate at or under CALM_THREAT; read only. */
    static boolean quiet(MarketAPI m) {
        int day = CAEngine.today();
        if (day >= 0) {
            if (day != quietDay) { quietMemo.clear(); quietDay = day; }
            Boolean b = quietMemo.get(m.getId());
            if (b != null) return b;
        }
        boolean q = hostileNear(m) == 0 && CAThreat.estimate(m) <= CALM_THREAT;
        if (day >= 0) quietMemo.put(m.getId(), q);
        return q;
    }

    /** 0.3.0.0 (B2 fix): markets hostile to you within BORDER_LY of the colony (CAThreat.estimate's neighbor count). */
    static int hostileNear(MarketAPI m) {
        int n = 0;
        try {
            if (m.getPrimaryEntity() == null) return 0;
            for (MarketAPI o : Global.getSector().getEconomy().getMarketsCopy()) {
                if (o == m || o.isPlayerOwned() || o.getFaction() == null || !o.getFaction().isHostileTo(com.fs.starfarer.api.impl.campaign.ids.Factions.PLAYER)) continue;
                if (o.getPrimaryEntity() == null) continue;
                if (com.fs.starfarer.api.util.Misc.getDistanceLY(m.getPrimaryEntity(), o.getPrimaryEntity()) > BORDER_LY) continue;
                n++;
            }
        } catch (Throwable ignored) { }
        return n;
    }

    /** 0.3.0.0 (B2 fix): what a calm colony's waiting station scores a month over its own price and fee (CAEngine.finish):
     *  enough to be worth building when nothing in front pays, far under anything that earns (about 200 a construction day
     *  for a 90-day Orbital Station, where 0.2.45's 40,000 rank bonus made it about 12,800). */
    static final float STATION_WAIT = 500f;

    /** 0.3.0.0 (B2 fix): a station step (the generic one or a tier of the colony's line). */
    static boolean stationStep(Step s) {
        return s != null && s.kind == Kind.BUILD && ("station".equals(s.id) || stationTier(spec(s.id)) > 0);
    }

    /**
     * 0.3.0.0 (B2 fix, research 01 section 10, 02 section 15): the open economic step a calm colony's station step waits
     * behind (build put it there), or null when it doesn't wait: a threatened, border or capital colony, a Security or War
     * goal, legacyPlanner, or nothing of the colony's own plan open in front of it. While it waits, CAEngine.finish gives it
     * only enough to pay its own price plus STATION_WAIT a month instead of its rank's 40,000, so anything in front that
     * pays goes into the construction queue first, and it's still built when nothing does (a plan industry waiting for a
     * slot is never offered, so it can't hold the station back for good).
     */
    static Step stationWaitsFor(MarketAPI m, Plan plan, Step step) {
        if (m == null || plan == null || step == null || !step.extra || !stationStep(step) || !calm(m)) return null;
        Step ahead = null;
        for (Step s : plan.steps) {
            if (s == step) return ahead;
            if (ahead == null && openOwn(m, plan, s)) ahead = s;
        }
        return null;
    }

    /** 0.3.0.0 (B2): the hidden switch back to the 0.2.45 build engine (CASettings.legacyPlanner); false offline. */
    static boolean legacy() {
        try { return CAData.settings().legacyPlanner; } catch (Throwable t) { return false; }
    }

    static Plan buildOwn(MarketAPI m, CAData.Profile prof) { return buildOwn(m, prof, false); }

    /** plainOnly (0.3.0.0 B5 fix 2): the plain plan for the goal, without the world search or a commitment (for seedPaid). */
    static Plan buildOwn(MarketAPI m, CAData.Profile prof, boolean plainOnly) {
        if (CAHypershunt.is(m)) { Plan h = new Plan(); h.name = "Coronal Hypershunt"; return h; }   // nothing can be built there
        Plan wp = null;
        if (!plainOnly) try { wp = CAWorldPlanner.plan(m, prof); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "world planner failed at " + m.getName(), t); }
        // 0.3.0.0 (WS-B5, research 01 section 3, DECISIONS 20): once money went into terraforming toward a world, a plan that
        // would undo it must be worth twice as much; otherwise the colony keeps working toward the world it paid for, or, while
        // that world is out of reach, follows the plain plan without the steps that would undo it (stableWorld)
        Commit k = null;
        if (!plainOnly) try {
            Stable st = stableWorld(m, prof, wp);
            wp = st.plan;
            k = st.commit;
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "world plan stability failed at " + m.getName(), t); }
        if (wp != null) {
            Plan p = new Plan();
            p.name = wp.name; p.world = wp.world; p.exact = wp.exact;
            if (wp.exact.contains("megaport") || !m.hasCondition(wp.world)) b(p, "spaceport", "Spaceport");
            p.steps.addAll(wp.steps);
            b(p, "grounddefenses", "Ground Defenses");
            List<Step> keep = new ArrayList<Step>();
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (Step s : p.steps) if (exists(s) && seen.add(s.kind + ":" + s.id)) keep.add(s);
            p.steps = keep;
            return p;
        }
        Plan p = new Plan();
        boolean hab = m.hasCondition("habitable");
        boolean ore = has(m, "ore_"), rare = has(m, "rare_ore_"), vol = has(m, "volatiles_"), org = has(m, "organics_"), farm = has(m, "farmland_");
        boolean cold = m.hasCondition("cold") || m.hasCondition("very_cold");
        boolean war = prof == CAData.Profile.WAR, sec = prof == CAData.Profile.SECURITY, inc = prof == CAData.Profile.INCOME,
                grow = prof == CAData.Profile.GROWTH, self = prof == CAData.Profile.SELF;
        boolean tme = Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy");

        b(p, "spaceport", "Spaceport"); b(p, "megaport", "Megaport");
        if ((war || self || (ore && rare)) && !hab && (ore || rare)) {
            p.name = "Forge";
            b(p, "mining", "Mining"); b(p, "refining", "Refining"); b(p, "heavyindustry", "Heavy Industry"); b(p, "orbitalworks", "Orbital Works");
            b(p, "tme_mineral_replicator", "Mineral Replicator"); b(p, "tme_element_synthesizer", "Element Synthesizer");
            // only there to add the tectonic activity below, which is a world step: without a reachable world it'd sit idle
            if (!tme) b(p, "tme_terrestrial_engine", "Terrestrial Engine");
            c(p, "extreme_tectonic_activity", "Extreme tectonic activity (lets rich deposits be added)");
            c(p, "ore_ultrarich", "Ultrarich ore"); c(p, "rare_ore_ultrarich", "Ultrarich transplutonic ore");
            b(p, "tme_unification_center", "Unification Center"); c(p, "tme_forge_world", "Forge World");
        } else if ((sec || war) && hab) {
            p.name = "Fortress";
            b(p, "heavyindustry", "Heavy Industry"); b(p, "orbitalworks", "Orbital Works"); b(p, "patrolhq", "Patrol HQ");
            b(p, "militarybase", "Military Base"); b(p, "highcommand", "High Command"); b(p, "fuelprod", "Fuel Production");
            b(p, "grounddefenses", "Ground Defenses"); b(p, "heavybatteries", "Heavy Batteries"); b(p, "station", "Orbital station");
            b(p, "tme_element_synthesizer", "Element Synthesizer"); c(p, "volatiles_plentiful", "Plentiful volatiles");
            b(p, "tme_unification_center", "Unification Center"); c(p, "tme_fortress_world", "Fortress World");
        } else if (hab && (inc || grow || prof == CAData.Profile.BALANCED)) {
            p.name = "Paradise";
            if (farm) b(p, "farming", "Farming");   // Farming needs farmland; without it the step could never be done and held the plan's first rank
            // (0.2.38.0: these three had slipped into the comment above in 0.2.30.0, so a Paradise plan was nearly empty)
            b(p, "lightindustry", "Light Industry"); b(p, "commerce", "Commerce"); b(p, "waystation", "Waystation");
            b(p, "tme_agricultural_laboratory", "Agricultural Laboratory"); c(p, farm ? "farmland_bountiful" : "farmland_adequate", "Better farmland");
            c(p, "mild_climate", "Mild climate (Stellar Manufactory, start it yourself)");
            b(p, "tme_unification_center", "Unification Center"); c(p, "tme_paradise_world", "Paradise World");
        } else if (sec) {
            p.name = "Bastion";
            b(p, "grounddefenses", "Ground Defenses"); b(p, "heavybatteries", "Heavy Batteries"); b(p, "patrolhq", "Patrol HQ");
            b(p, "militarybase", "Military Base"); b(p, "highcommand", "High Command"); b(p, "station", "Orbital station");
            b(p, "planetaryshield", "Planetary Shield");
        } else if (vol && cold) {
            p.name = "Fuel Depot";
            b(p, "mining", "Mining"); b(p, "fuelprod", "Fuel Production"); b(p, "waystation", "Waystation");
            b(p, "tme_element_synthesizer", "Element Synthesizer"); c(p, "volatiles_plentiful", "Plentiful volatiles");
        } else if (ore || rare || org || vol) {
            p.name = "Mining Colony";
            b(p, "mining", "Mining"); b(p, "refining", "Refining"); b(p, "tme_mineral_replicator", "Mineral Replicator");
            if (ore) c(p, "ore_ultrarich", "Ultrarich ore");
            b(p, "commerce", "Commerce");
        } else {
            p.name = inc ? "Trade Hub" : "General Colony";
            b(p, "commerce", "Commerce"); b(p, "waystation", "Waystation"); b(p, "lightindustry", "Light Industry");
        }
        // hazard clean-up applies to every plan
        if (m.hasCondition("no_atmosphere") || m.hasCondition("toxic_atmosphere") || m.hasCondition("dense_atmosphere")) {
            b(p, "tme_atmosphere_regulator", "Atmosphere Regulator");
            if (!p.name.equals("Forge")) { n(p, "toxic_atmosphere", "Remove toxic atmosphere"); n(p, "dense_atmosphere", "Remove dense atmosphere"); }
        }
        if (m.hasCondition("irradiated") || m.hasCondition("pollution") || m.hasCondition("inimical_biosphere")) {
            b(p, "tme_geomorphology_station", "Geomorphology Station");
            n(p, "irradiated", "Remove radiation"); n(p, "pollution", "Remove pollution"); n(p, "inimical_biosphere", "Remove inimical biosphere");
        }
        if (!p.name.equals("Forge") && (m.hasCondition("high_gravity") || m.hasCondition("extreme_tectonic_activity"))) {
            b(p, "tme_terrestrial_engine", "Terrestrial Engine");
            n(p, "high_gravity", "Remove high gravity"); n(p, "extreme_tectonic_activity", "Remove extreme tectonic activity");
        }
        b(p, "grounddefenses", "Ground Defenses");
        // drop steps that don't apply (conditions that were never present for NOCOND); and with Terraforming Made Easy on,
        // the world planner found no world this colony can reach, so the world steps (Unification Center, the structures
        // that add conditions, the conditions) go too: hazard clean-up stays, since it lets the colony grow
        List<Step> keep = new ArrayList<Step>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (Step s : p.steps) {
            if (s.kind == Kind.NOCOND && !m.hasCondition(s.id)) continue;
            if (s.kind == Kind.NOCOND && CAData.enabler(m, s.id)) continue;   // surplus terraforming's step toward a richer deposit
            if (tme && (s.kind == Kind.COND || WORLD_STEPS.contains(s.id))) continue;
            if (!exists(s) || !seen.add(s.kind + ":" + s.id)) continue;
            keep.add(s);
        }
        p.steps = keep;
        // 0.3.0.0 (B5): the plain plan never pays to undo what a world plan paid for while that world still fits the colony's goal
        try {
            if (k != null && !switchAllowed(PLAIN, Float.NaN, CAWorldPlanner.fit(k.world, prof) > 0f)) {
                List<Step> u = undoes(m, p.steps, k);
                if (!u.isEmpty()) { p.steps = without(p.steps, u); keptNote(m, k, p.name); }
            }
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "world plan stability failed at " + m.getName(), t); }
        return p;
    }

    // ---------------- 0.3.0.0 (WS-B5): world plan stability (research 01 section 3, DECISIONS 20) ----------------
    // Ming Adi paid 1,525,000 for an Atmosphere Regulator and a project that added Toxic Atmosphere for its Waste World
    // plan; the world went out of reach, the plain Mining Colony plan took over, and its hazard clean-up then planned to pay
    // again to remove the Toxic Atmosphere. Now the colony is committed to a world once money went into it: CATerraform
    // started a project for one of its steps (commitWorld), or one is running there (paidToward), or, on a save from before
    // 0.3.0, its 0.2.45 terraforming is seeded once (seedPaid), and the condition each project changes is remembered when it
    // starts (a world plan never lists a condition that's already in, so a finished step can't be read back from it). While
    // the commitment holds, a plan that would undo them (removing one, adding one it keeps away, or adding one whose project
    // removes a kept one as a side effect) needs twice the worth (B5 fix 2: the world planner's score for each plan here
    // now, worth; the plain plan never undoes a world that fits the goal). Short of that the colony keeps working toward the
    // committed world while it's in reach (its own plan, worldPlanFor), which keeps the commitment fresh; only while that
    // world is out of reach does it get the plain plan without the undo steps. The commitment ends when a world forms there,
    // or COMMIT_DAYS after the committed world was last the colony's plan (longer than a dropped world's year). Kept as
    // CAMark stamps in the save: "world:<market>|<world>" and "worldcond:<market>|+<condition>" (keep it) or "|-" (keep it away).

    static final String COMMIT = "world:", COMMIT_COND = "worldcond:";
    /** Days after the committed world was last the colony's plan that the commitment lasts (a dropped world comes back after WORLD_DEAD_DAYS). */
    static final float COMMIT_DAYS = CATuning.WORLD_COMMIT_DAYS;
    /** A plan that undoes a committed world's paid changes must be worth this many times as much (DECISIONS 20). */
    static final float SWITCH_WORTH = CATuning.WORLD_SWITCH_WORTH;

    /** A colony's commitment: the world, and the conditions it changed to keep (+) or keep away (-). */
    static final class Commit {
        String world;
        final java.util.Set<String> keep = new java.util.HashSet<String>(), avoid = new java.util.HashSet<String>();
    }

    // 0.3.0.0 (B5 fix 2): "worth" was CAWorldPlanner.fit, a 0-3 goal rank, so a fit-1 commitment always lost to any fit-2
    // world and a fit-2 or 3 one could never be overridden, whatever the money. It's now the world planner's own score
    // (CAWorldPlanner.plan: fit x 1,000,000, less what its condition projects still cost, less 200,000 per open step, plus a
    // year of what its end conditions change), worked out for each plan at the colony as it is now (worth). The committed
    // world counts only what's left to pay (finished steps are gone from its plan, a running project counts as paid), so the
    // money already in it counts in its favour; the new plan's cost holds the projects that would undo it. Fit stays only as
    // "fits the goal at all". The plain plan has no score: it never undoes a commitment whose world still fits the goal.

    /** switchAllowed's `next` for the plain plan (no world, no score). */
    static final float PLAIN = Float.NaN;
    /** The world planner's score: per point of fit, per open step, months of condition change (kept in step with CAWorldPlanner.plan). */
    static final float FIT_SCORE = 1000000f, OPEN_STEP_SCORE = 200000f, CHANGE_MONTHS = 12f;

    /** Pure: the world planner's score from its parts (cost: what its condition projects still cost; change: a month's). */
    static float worldScore(float fit, float cost, int open, float change) {
        return fit * FIT_SCORE - cost - open * OPEN_STEP_SCORE + CHANGE_MONTHS * change;
    }

    /**
     * Pure: switching from a committed world to a plan that undoes it. committedFits: the committed world fits the colony's
     * goal at all (fit above 0), else anything may undo it. committed: its worth now (NaN: out of reach here for now); next:
     * the new plan's (PLAIN: the plain plan). Both NaN cases keep the commitment. Otherwise the new plan must be worth
     * SWITCH_WORTH times as much: twice a positive score, or, when finishing the committed world costs more than it's
     * worth (a score under 0), at most half that loss.
     */
    static boolean switchAllowed(float next, float committed, boolean committedFits) {
        if (!committedFits) return true;
        if (Float.isNaN(next) || Float.isNaN(committed)) return false;
        return committed > 0f ? next >= SWITCH_WORTH * committed : next >= committed / SWITCH_WORTH;
    }

    /**
     * 0.3.0.0 (B5 fix 2): what a world plan is worth at this colony now, by the world planner's score: its fit for the goal, its
     * condition steps' projects not yet paid for (a running one is), its open steps, and a year of what its end conditions
     * change (the steps walked in order, each project's hated conditions going, as CAWorldPlanner.resolve does).
     */
    static float worth(MarketAPI m, CAData.Profile prof, Plan wp) {
        java.util.Set<String> conds = new java.util.HashSet<String>();
        for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : m.getConditions()) conds.add(c.getId());
        float cost = 0f;
        int open = 0;
        for (Step s : wp.steps) {
            if (!s.done(m)) open++;
            if (s.kind != Kind.COND || s.id.equals(wp.world) || m.hasCondition(s.id)) continue;
            CAWorldPlanner.Row r = CAWorldPlanner.rows().get(s.id);
            if (r == null) continue;
            if (!running(m, s.id)) cost += r.cost;
            conds.add(s.id);
            conds.removeAll(CAWorldPlanner.idsOf(r.hated));
        }
        float change = 0f;
        try { change = CAConditions.change(m, conds, prof, null); } catch (Throwable ignored) { }
        return worldScore(CAWorldPlanner.fit(wp.world, prof), cost, open, change);
    }

    /**
     * Pure: the steps of a plan that undo a commitment, given the colony's conditions now: removing a condition it keeps,
     * or adding one it keeps away.
     */
    static List<Step> undoes(List<Step> steps, java.util.Set<String> conds, java.util.Set<String> keep, java.util.Set<String> avoid) {
        return undoes(steps, conds, keep, avoid, java.util.Collections.<String, java.util.Set<String>>emptyMap());
    }

    /**
     * Pure: as above, with Terraforming Made Easy's side effects (hatedBy: condition -> what adding it removes, its row's
     * hated list). 0.3.0.0 (B5 fix): a step adding a condition whose project strips one the commitment keeps undoes it too (a
     * Death World's Thin Atmosphere removes the Toxic Atmosphere a Waste World paid for, though neither world's own list
     * names the other's). The steps are walked in order on a copy of the conditions, so a step only undoes what is still
     * there when it runs.
     */
    static List<Step> undoes(List<Step> steps, java.util.Set<String> conds, java.util.Set<String> keep, java.util.Set<String> avoid,
                             java.util.Map<String, java.util.Set<String>> hatedBy) {
        List<Step> out = new ArrayList<Step>();
        if (steps == null) return out;
        java.util.Set<String> now = new java.util.HashSet<String>(conds);
        for (Step s : steps) {
            if (s.kind == Kind.BUILD) continue;
            boolean undo = false;
            if (s.kind == Kind.NOCOND) {
                undo = keep.contains(s.id) && now.contains(s.id);
                now.remove(s.id);
            } else {
                undo = avoid.contains(s.id) && !now.contains(s.id);
                java.util.Set<String> gone = hatedBy == null ? null : hatedBy.get(s.id);
                if (gone != null) for (String h : gone) if (!h.equals(s.id) && keep.contains(h) && now.contains(h)) undo = true;
                if (gone != null) now.removeAll(gone);
                now.add(s.id);
            }
            if (undo) out.add(s);
        }
        return out;
    }

    static List<Step> undoes(MarketAPI m, List<Step> steps, Commit k) {
        java.util.Set<String> conds = new java.util.HashSet<String>();
        for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : m.getConditions()) conds.add(c.getId());
        java.util.Map<String, java.util.Set<String>> hatedBy = new java.util.HashMap<String, java.util.Set<String>>();
        try {
            for (Step s : steps) {
                if (s.kind != Kind.COND) continue;
                CAWorldPlanner.Row r = CAWorldPlanner.rows().get(s.id);
                if (r != null) hatedBy.put(s.id, CAWorldPlanner.idsOf(r.hated));
            }
        } catch (Throwable ignored) { }   // no table: the plain rule above
        return undoes(steps, conds, k.keep, k.avoid, hatedBy);
    }

    /**
     * Pure: the steps without the removed ones, and without a Terraforming Made Easy structure that only served them (the
     * Atmosphere Regulator of a removed "Remove toxic atmosphere"). structureFor: condition -> its structure.
     */
    static List<Step> without(List<Step> steps, List<Step> removed, java.util.Map<String, String> structureFor) {
        java.util.Set<String> gone = new java.util.HashSet<String>(), still = new java.util.HashSet<String>();
        for (Step s : removed) { String st = structureFor.get(s.id); if (st != null) gone.add(st); }
        for (Step s : steps) {
            if (s.kind == Kind.BUILD || removed.contains(s)) continue;
            String st = structureFor.get(s.id);
            if (st != null) still.add(st);
        }
        List<Step> out = new ArrayList<Step>();
        for (Step s : steps) {
            if (removed.contains(s)) continue;
            if (s.kind == Kind.BUILD && gone.contains(s.id) && !still.contains(s.id)) continue;
            out.add(s);
        }
        return out;
    }

    static List<Step> without(List<Step> steps, List<Step> removed) {
        java.util.Map<String, String> sf = new java.util.HashMap<String, String>();
        for (Step s : steps) {
            if (s.kind == Kind.BUILD) continue;
            String st = CAWorldPlanner.structureFor(s.id);
            if (st != null) sf.put(s.id, st);
        }
        return without(steps, removed, sf);
    }

    /** 0.3.0.0 (B5): what buildOwn follows once the commitment is applied: the world plan (null: the plain plan) and the commitment still holding (null: none). */
    static final class Stable {
        Plan plan;
        Commit commit;
    }

    /** The world the colony is committed to, or null. */
    static String committedWorld(String marketId) {
        List<String> marks = CAMark.keys(COMMIT + marketId + "|");
        return marks.isEmpty() ? null : marks.get(0).substring((COMMIT + marketId + "|").length());
    }

    /**
     * 0.3.0.0 (B5): the colony's commitment applied to the world plan CAWorldPlanner picked (best, null: none in reach). A
     * formed world ends it. A best world that would undo it and isn't worth twice as much (worth, against the committed world's
     * own plan here now; B5 fix 2) doesn't take over: the colony
     * keeps the committed world's own plan while that world is in reach (B5 fix: before, it fell to the plain plan, which
     * with Terraforming Made Easy has no world steps, so it worked toward neither world and the commitment ran out a year
     * later, letting the other world undo the paid terraforming anyway), else the plain plan. The committed world as the
     * plan refreshes the commitment and records the projects running toward it; COMMIT_DAYS without it ends it, and then
     * the best world takes over at once. A new commitment starts when money goes into the followed world (paidToward).
     */
    static Stable stableWorld(MarketAPI m, CAData.Profile prof, Plan best) {
        Stable out = new Stable();
        out.plan = best;
        String id = m.getId();
        seedPaidOnce(m);   // 0.3.0.0 (B5 fix 2): a save from before 0.3.0 first commits what 0.2.45 paid for
        String world = committedWorld(id);
        if (world != null) for (String w : CAWorldPlanner.WORLDS) if (m.hasCondition(w)) { endCommit(id); world = null; break; }   // formed: TME keeps it
        long now = CAMark.now();
        if (world != null) {
            Commit k = kept(id, world);
            Plan follow = best;
            // 0.3.0.0 (B5 fix 2): worth is the world planner's score for each plan here now (worth), not the 0-3 fit
            boolean undo = best != null && !world.equals(best.world) && !undoes(m, best.steps, k).isEmpty();
            Plan own = undo ? worldPlanFor(m, prof, world) : null;
            float next = undo ? worth(m, prof, best) : PLAIN, held = own == null ? Float.NaN : worth(m, prof, own);
            boolean blocked = undo && !switchAllowed(next, held, CAWorldPlanner.fit(world, prof) > 0f);
            if (blocked) follow = own;   // null: out of reach here now, so the plain plan without the undo steps
            if (undo && CALog.once("worldworth:" + id + ":" + world + ":" + best.world))
                CALog.quiet(m.getName() + ": the " + best.name + " plan is worth " + Math.round(next) + " against " + (own == null ? "the out-of-reach "
                        + CAWorldPlanner.name(world) : Math.round(held) + " for the " + CAWorldPlanner.name(world)) + " it's committed to; "
                        + (blocked ? "not " + Math.round(SWITCH_WORTH) + "x, so it keeps the commitment" : "enough to undo it"));
            if (follow != null && world.equals(follow.world)) {
                commitWorld(m, follow, now);
                k = kept(id, world);   // with what the refresh just recorded
            } else {
                Long at = CAMark.at(COMMIT + id + "|" + world);
                if (at != null && CAMark.days(at, now) > COMMIT_DAYS) {
                    endCommit(id);
                    CALog.quiet(m.getName() + ": no longer committed to " + CAWorldPlanner.name(world) + " (not its plan for " + Math.round(COMMIT_DAYS) + " days)");
                    k = null;
                    if (blocked) follow = best;   // nothing holds it back any more
                }
            }
            if (blocked && k != null) keptNote(m, k, best.name);
            out.plan = follow;
            out.commit = k;
        }
        if (out.plan != null && out.plan.world != null && (out.commit == null || !out.commit.world.equals(out.plan.world)) && paidToward(m, out.plan)) {
            if (out.commit != null) endCommit(id);   // a world worth twice as much (or one that undoes nothing) took over, and money went into it
            commitWorld(m, out.plan, now);
            CALog.quiet(m.getName() + ": committed to the " + out.plan.name + " plan (money went into its terraforming)");
            out.commit = null;
        }
        return out;
    }

    static boolean tme() {
        try { return Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy"); } catch (Throwable t) { return false; }
    }

    /**
     * 0.3.0.0 (B5 fix): one world's own plan at this colony, the search CAWorldPlanner.plan runs for each world (kept in step
     * with it), for the world a colony is committed to when the planner's best pick would undo it. Null when that world is
     * out of reach here now: dropped (worldDead), worth nothing to the colony's goal, refused by a condition it has, no route
     * to its conditions, or its industries can't be had.
     */
    static Plan worldPlanFor(MarketAPI m, CAData.Profile prof, String w) {
        if (m == null || w == null || prof == null || !tme() || CAWorldPlanner.rows().isEmpty()) return null;
        if (m.hasCondition(w)) {
            Plan done = new Plan();
            done.name = CAWorldPlanner.name(w); done.world = w;
            done.steps.add(new Step(Kind.COND, w, done.name));
            return done;
        }
        if (CAWorldPlanner.fit(w, prof) <= 0f || worldDead(m, w)) return null;
        CAWorldPlanner.Row r = CAWorldPlanner.rows().get(w);
        if (r == null) return null;
        CAWorldPlanner.Sim sim = new CAWorldPlanner.Sim();
        sim.conds = new java.util.HashSet<String>();
        for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : m.getConditions()) sim.conds.add(c.getId());
        sim.inds = new java.util.HashSet<String>();
        for (Industry i : m.getIndustries()) sim.inds.add(i.getId());
        sim.gasGiant = m.getPrimaryEntity() instanceof com.fs.starfarer.api.campaign.PlanetAPI && ((com.fs.starfarer.api.campaign.PlanetAPI) m.getPrimaryEntity()).isGasGiant();
        for (String b : CAWorldPlanner.idsOf(r.hated)) if (sim.conds.contains(b)) return null;
        List<String> need = new ArrayList<String>();
        for (String[] g : CAWorldPlanner.groups(r.liked, new ArrayList<Boolean>())) need.addAll(java.util.Arrays.asList(g));
        CAWorldPlanner.Sim done = null;
        for (int order = 0; order < 2; order++) {   // both orders, since one project's side effects can undo another's
            CAWorldPlanner.Sim trial = sim.copy();
            List<String> seq = new ArrayList<String>(need);
            if (order == 1) java.util.Collections.reverse(seq);
            boolean good = true;
            for (String c : seq) { if (!CAWorldPlanner.resolve(trial, c, 0)) { good = false; break; } trial.keep.add(c); }
            if (good) for (String c : need) if (!trial.conds.contains(c)) good = false;
            if (good && (done == null || trial.cost < done.cost)) done = trial;
        }
        if (done == null) return null;
        for (String[] g : CAWorldPlanner.groups(r.likedInd, new ArrayList<Boolean>())) for (String ind : g) {
            if (ind.equals("mining") && !m.hasIndustry("mining") && !CAWorldPlanner.anyPrefix(done.conds, "ore_", "rare_ore_", "volatiles_", "organics_")) return null;
            if (ind.equals("farming") && !m.hasIndustry("farming") && !m.hasIndustry("aquaculture") && !CAWorldPlanner.anyPrefix(done.conds, "farmland_")) return null;
        }
        Plan p = new Plan();
        p.name = CAWorldPlanner.name(w); p.world = w;
        for (String[] g : CAWorldPlanner.groups(r.likedInd, new ArrayList<Boolean>())) for (String ind : g) {
            p.exact.add(ind);
            String label = CAWorldPlanner.industryName(ind) + (ind.equals("mining") && m.hasIndustry("IndEvo_SwitchableMining") ? " (vanilla Mining; Specialized Mining doesn't count)" :
                    ind.equals("fuelprod") && m.hasIndustry("ix_fuel_production") ? " (vanilla Fuel Production; Advanced doesn't count)" : "");
            Step st = new Step(Kind.BUILD, ind, label);
            st.exact = true;
            p.steps.add(st);
        }
        p.steps.addAll(done.steps);
        p.steps.add(new Step(Kind.BUILD, UNIFICATION, CAWorldPlanner.industryName(UNIFICATION)));
        p.steps.add(new Step(Kind.COND, w, CAWorldPlanner.name(w)));
        int slots = 0;
        for (String ind : p.exact) if (CAWorldPlanner.isIndustry(ind)) slots++;
        for (Industry i : m.getIndustries()) {
            if (i.isHidden() || !i.isIndustry()) continue;
            boolean covers = false;
            for (String ind : p.exact) if (i.getId().equals(ind) || leadsTo(i.getId(), ind) || leadsTo(ind, i.getId())) covers = true;
            if (!covers) slots++;
        }
        if (slots > CAWorldPlanner.slotsAt(m, com.fs.starfarer.api.util.Misc.getMaxMarketSize(m))) return null;
        if (stepDropped(m, p)) return null;   // a structure it needs is out of reach here (0.3.0.0 test fix)
        return p;
    }

    /**
     * The commitment as kept: the conditions its paid projects changed (recorded as they started or ran), plus what the
     * world itself needs and refuses (Terraforming Made Easy's table: a Waste World needs Toxic Atmosphere and Irradiated).
     */
    static Commit kept(String marketId, String world) {
        Commit k = new Commit();
        k.world = world;
        String cp = COMMIT_COND + marketId + "|";
        for (String c : CAMark.keys(cp)) {
            String cond = c.substring(cp.length());
            if (cond.length() < 2) continue;
            if (cond.charAt(0) == '+') k.keep.add(cond.substring(1)); else k.avoid.add(cond.substring(1));
        }
        try {
            CAWorldPlanner.Row r = CAWorldPlanner.rows().get(world);
            if (r != null) { k.keep.addAll(CAWorldPlanner.idsOf(r.liked)); k.avoid.addAll(CAWorldPlanner.idsOf(r.hated)); }
        } catch (Throwable ignored) { }
        k.avoid.removeAll(k.keep);
        return k;
    }

    /**
     * Whether money is going into this world plan here: a Terraforming Made Easy project for one of its condition steps (not
     * the world itself) is running at the colony. 0.3.0.0 (B5 fix): it looked for a finished condition step, which a world
     * plan never lists (CAWorldPlanner.resolve skips a condition that's in), so it never found one; a running project also
     * covers one already under way when a save from before 0.3.0 loads.
     */
    static boolean paidToward(MarketAPI m, Plan wp) {
        for (Step s : wp.steps) if (s.kind != Kind.BUILD && !s.id.equals(wp.world) && running(m, s.id)) return true;
        return false;
    }

    /** A Terraforming Made Easy project at the colony is making (or removing) this condition now; false without TME. */
    static boolean running(MarketAPI m, String condId) {
        if (m == null || condId == null) return false;
        try {   // CATerraform links TME's classes; the guard sits in this method so audit_combos sees it (as tme(), inlined)
            if (!Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy")) return false;
            return CATerraform.producing(m, condId);
        } catch (Throwable t) { return false; }
    }

    /**
     * Records (or refreshes) the commitment to this world plan, with the condition of each of its steps a project is
     * working on now (0.3.0.0 B5 fix: a step's condition is gone from the plan once it's in, so it's recorded while it's made).
     */
    static void commitWorld(MarketAPI m, Plan wp, long now) {
        String id = m.getId();
        CAMark.set(COMMIT + id + "|" + wp.world, now);
        for (Step s : wp.steps) {
            if (s.kind == Kind.BUILD || s.id.equals(wp.world) || !running(m, s.id)) continue;
            markCond(id, s.id, !m.hasCondition(s.id), now);
        }
    }

    /** Records a condition a paid project changes: kept (adds) or kept away; the opposite mark goes, so the newest project wins. */
    static void markCond(String marketId, String cond, boolean adds, long now) {
        CAMark.clear(COMMIT_COND + marketId + "|" + (adds ? "-" : "+") + cond);
        CAMark.since(COMMIT_COND + marketId + "|" + (adds ? "+" : "-") + cond, now);
    }

    /**
     * 0.3.0.0 (B5): for Terraforming Made Easy's planner (CATerraform) when it starts a project for a step of the colony's world
     * plan: the colony is committed to that world from now on, before the project finishes. Without the project's condition,
     * every project running at the colony now is recorded.
     */
    public static void commitWorld(MarketAPI m, String world) { commitWorld(m, world, null, true); }

    /**
     * 0.3.0.0 (B5 fix): as above, with the condition the project changes (adds: it adds it, else it removes it), recorded for
     * the commitment so a later plan can't undo it for free even after the world plan stops listing it: Forge World's Ultrarich
     * ore needs Extreme Tectonic Activity, which a dropped Forge's plain Mining Colony plan would otherwise pay to remove.
     */
    public static void commitWorld(MarketAPI m, String world, String cond, boolean adds) {
        if (m == null || world == null || CALog.whatIf()) return;
        try {
            String id = m.getId();
            String was = committedWorld(id);
            if (was != null && !was.equals(world)) endCommit(id);
            if (!world.equals(was)) CALog.quiet(m.getName() + ": committed to " + CAWorldPlanner.name(world) + " (a project started toward it)");
            long now = CAMark.now();
            CAMark.set(COMMIT + id + "|" + world, now);
            if (cond != null) markCond(id, cond, adds, now);
            else if (tme()) for (String c : CAWorldPlanner.rows().keySet()) if (!c.equals(world) && running(m, c)) markCond(id, c, !m.hasCondition(c), now);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "world commitment failed at " + m.getName(), t); }
    }

    static void endCommit(String marketId) {
        for (String k : CAMark.keys(COMMIT + marketId + "|")) CAMark.clear(k);
        for (String k : CAMark.keys(COMMIT_COND + marketId + "|")) CAMark.clear(k);
    }

    /**
     * 0.3.0.0 (B5): conditions terraforming must leave as they are at this colony while it's committed to a world it isn't
     * planning now: {keep, keep away} (both empty when none). For CATerraform.keepAvoid, so surplus terraforming doesn't
     * undo them either.
     */
    @SuppressWarnings("unchecked")
    public static java.util.Set<String>[] protectedConds(MarketAPI m) {
        java.util.Set<String>[] out = new java.util.Set[]{new java.util.HashSet<String>(), new java.util.HashSet<String>()};
        if (m == null) return out;
        try {
            String id = m.getId();
            seedPaidOnce(m);   // 0.3.0.0 (B5 fix 2): surplus terraforming may look before any plan is built
            List<String> marks = CAMark.keys(COMMIT + id + "|");
            if (marks.isEmpty()) return out;
            Commit k = kept(id, marks.get(0).substring((COMMIT + id + "|").length()));
            out[0].addAll(k.keep);
            out[1].addAll(k.avoid);
        } catch (Throwable ignored) { }
        return out;
    }

    /** One log line (at most every CALog.WAIT_DAYS while it holds) when the commitment keeps a plan from undoing it. */
    static void keptNote(MarketAPI m, Commit k, String plan) {
        if (CALog.once("worldkeep:" + m.getId() + ":" + k.world))
            CALog.quiet(m.getName() + ": keeps the " + CAWorldPlanner.name(k.world) + " terraforming it paid for; the " + plan + " plan won't undo it");   // 0.3.0.9: starsector.log only
    }

    // ---------------- 0.3.0.0 (WS-B5 fix 2): commitments for terraforming paid for under 0.2.45 ----------------
    // A commitment only starts when a 0.3.0 project starts or runs, so Ming Adi's Toxic Atmosphere (its Waste World project ran
    // under 0.2.45 from m38, the regulator came down at m44) had none, and the plain Mining Colony plan still listed "Remove
    // toxic atmosphere", the pay-to-undo research 01 section 3 found. Once per save from before 0.3.0, the first time anything
    // plans: a colony on the plain plan (no world in reach for its goal, no commitment, no world formed) with a condition its
    // plain plan would pay to remove, which a world fitting its goal needs, is committed to that world with that condition
    // kept (the world keeping the most such conditions, then the better fit, then TME's own order). 0.2.45 kept no record of
    // what it paid for, so a hazard the colony always had counts too; that only holds the clean-up back while the commitment
    // lasts (COMMIT_DAYS unless that world becomes its plan), so it spends less, never more. A colony with a world in reach
    // is left to that world's plan, which commits it once money goes in (stableWorld). Marked done with a CAMark stamp.

    /** CAMark key: this save's 0.2.45 terraforming was looked at for commitments (once). */
    static final String SEEDED = "worldseed3";

    /**
     * Pure: the world to commit to, or null: of the worlds that fit the goal (fit above 0), the one whose needed conditions
     * (keeps: world to the conditions it needs) include the most the plain plan would pay to remove (removes); then the
     * better fit, then WORLDS order. out gets those conditions.
     */
    static String seedWorld(java.util.Set<String> removes, CAData.Profile prof, java.util.Map<String, java.util.Set<String>> keeps, java.util.Set<String> out) {
        String pick = null;
        java.util.Set<String> best = null;
        float bestFit = 0f;
        for (String w : CAWorldPlanner.WORLDS) {
            float f = CAWorldPlanner.fit(w, prof);
            java.util.Set<String> need = keeps == null ? null : keeps.get(w);
            if (f <= 0f || need == null) continue;
            java.util.Set<String> hit = new java.util.TreeSet<String>(need);
            hit.retainAll(removes);
            if (hit.isEmpty()) continue;
            if (pick == null || hit.size() > best.size() || (hit.size() == best.size() && f > bestFit)) { pick = w; best = hit; bestFit = f; }
        }
        if (pick != null && out != null) out.addAll(best);
        return pick;
    }

    /**
     * Once per save from before 0.3.0 (with Terraforming Made Easy): seedPaid for this colony and every colony of yours. The
     * mark goes on first, so nothing below (which never reaches stableWorld: plainOnly) can start it again.
     */
    static void seedPaidOnce(MarketAPI current) {
        if (CALog.whatIf()) return;
        try {
            if (CAMark.at(SEEDED) != null || CAData.get().migratedAt == 0L) return;
            long now = CAMark.now();
            CAMark.set(SEEDED, now);   // first: one look per save, whatever happens below
            if (!tme() || CAWorldPlanner.rows().isEmpty()) return;
            java.util.Set<String> done = new java.util.HashSet<String>();
            List<MarketAPI> all = new ArrayList<MarketAPI>();
            if (current != null) all.add(current);
            try { all.addAll(com.fs.starfarer.api.util.Misc.getPlayerMarkets(false)); } catch (Throwable ignored) { }   // no economy offline
            for (MarketAPI m : all) {
                if (m == null || !done.add(m.getId())) continue;
                try { seedPaid(m, CAData.get().colony(m.getId()).profile, now); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "0.2.45 terraforming commitment failed at " + m.getName(), t); }
            }
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "0.2.45 terraforming commitments failed", t);
        }
    }

    /** One colony's look (see above): the world it's now committed to, or null. */
    static String seedPaid(MarketAPI m, CAData.Profile prof, long now) {
        if (m == null || prof == null || committedWorld(m.getId()) != null) return null;
        for (String w : CAWorldPlanner.WORLDS) if (m.hasCondition(w)) return null;
        if (CAWorldPlanner.plan(m, prof) != null) return null;   // a world in reach: its own plan decides
        java.util.Set<String> removes = new java.util.HashSet<String>();
        for (Step s : buildOwn(m, prof, true).steps) if (s.kind == Kind.NOCOND) removes.add(s.id);
        if (removes.isEmpty()) return null;
        java.util.Map<String, java.util.Set<String>> keeps = new java.util.HashMap<String, java.util.Set<String>>();
        for (String w : CAWorldPlanner.WORLDS) {
            CAWorldPlanner.Row r = CAWorldPlanner.rows().get(w);
            if (r != null) keeps.put(w, CAWorldPlanner.idsOf(r.liked));
        }
        java.util.Set<String> conds = new java.util.TreeSet<String>();
        String w = seedWorld(removes, prof, keeps, conds);
        if (w == null) return null;
        String id = m.getId();
        CAMark.set(COMMIT + id + "|" + w, now);
        List<String> names = new ArrayList<String>();
        for (String c : conds) { markCond(id, c, true, now); names.add(CAWorldPlanner.name(c)); }
        CALog.quiet(m.getName() + ": committed to " + CAWorldPlanner.name(w) + " on moving to 0.3.0: keeps its " + String.join(", ", names)
                + " (that world needs it) instead of paying to remove it while the commitment lasts");
        return w;
    }

    /**
     * Steps research blocks for now, "marketId:stepId": the building the colony must add next on the way to the step
     * (the step, or the building it's upgraded from) is locked. Filled by CAResearch from its daily ranking; empty without
     * Vaults of Knowledge. They don't take a plan rank, so the next step that can be built gets the most.
     */
    public static final java.util.Set<String> BLOCKED = new java.util.HashSet<String>();

    /**
     * Score bonus for a build or upgrade pick that advances the plan; the earliest open build steps get the most.
     * A pick that a plan building is upgraded from counts too (with AotD, Monoculture Plots on the way to Farming), so a
     * step whose own building is locked passes its rank to what it's upgraded from.
     * 0.3.0.0 (B2): CAEngine.finish gives this fixed bonus only to enablers and a special world's buildings (rankBonus of
     * match); research still values the steps it opens with it (CAResearch). A worthless step takes no rank.
     */
    public static float bonusFor(MarketAPI m, Plan plan, String industryId) { return bonusFor(m, plan, industryId, true); }

    /**
     * skipBlocked false: the step's rank as if research had opened every step, which is what the research it waits on is
     * worth (counting blocked steps out there made a blocked step worth nothing every other day).
     */
    public static float bonusFor(MarketAPI m, Plan plan, String industryId, boolean skipBlocked) {
        Match x = match(m, plan, industryId, skipBlocked);
        return x == null || x.worthless ? 0f : rankBonus(x.rank);
    }

    /** The fixed bonus of a plan rank: the first open step, the second, any later one (none below 0). */
    static float rankBonus(int rank) {
        return rank < 0 ? 0f : rank == 0 ? CATuning.PLAN_BONUS_FIRST : rank == 1 ? CATuning.PLAN_BONUS_SECOND : CATuning.PLAN_BONUS_LATER;
    }

    /**
     * 0.3.0.0 (B2): the plan step a pick advances, and its rank among the open steps. worthless: the only step it advances
     * has scored nothing for WORTHLESS_DAYS (rank -1; it takes no rank, but the engine still records what the pick scores for
     * it, so it counts again once it's worth something).
     */
    public static final class Match {
        public final Step step;
        public final int rank;
        public final boolean worthless;
        /**
         * 0.3.0.0 (B2 fix): a worthless step this pick also advances, in front of the ranked step it returns (Heavy Industry,
         * when the pick ranks as the Orbital Works it leads to); null when none, or when step is that worthless step itself.
         * The engine records the pick's score for it too, or it never counted again once its pick paid.
         */
        public final Step idle;
        Match(Step step, int rank, boolean worthless) { this(step, rank, worthless, null); }
        Match(Step step, int rank, boolean worthless, Step idle) { this.step = step; this.rank = rank; this.worthless = worthless; this.idle = idle; }
    }

    /** Whether a pick of this building advances this step: the building, a station for the generic station step, or what it's upgraded from. */
    static boolean advances(Step s, String industryId) {
        return s.id.equals(industryId) || (s.id.equals("station") && stationTier(spec(industryId)) > 0) || leadsTo(industryId, s.id);
    }

    /**
     * The open build step this pick advances, ranked as bonusFor ranks them (0.2.45): done steps, research-blocked and dead ones
     * (skipBlocked) and a held Unification Center take no rank. 0.3.0.0 (B2): nor do worthless steps (research 02 section 1).
     * Null when it advances none.
     */
    public static Match match(MarketAPI m, Plan plan, String industryId, boolean skipBlocked) {
        if (plan == null || industryId == null) return null;
        int rank = 0;
        Step idle = null;
        for (Step s : plan.steps) {
            if (s.kind != Kind.BUILD || s.done(m) || (skipBlocked && (BLOCKED.contains(m.getId() + ":" + s.id) || dead(m, s.id)))) continue;
            if (held(m, plan, s)) continue;   // the Unification Center, until the rest of its world is ready (0.2.39.1)
            boolean hit = advances(s, industryId);
            if (worthless(m, plan, s)) { if (hit && idle == null) idle = s; continue; }   // 0.3.0.0 (B2): no rank while it's worth nothing
            if (hit) return new Match(s, rank, false, idle);   // 0.3.0.0 (B2 fix): with the worthless step it also advances, so that's re-scored too
            rank++;
        }
        return idle == null ? null : new Match(idle, -1, true);
    }

    static final String UNIFICATION = "tme_unification_center";

    /**
     * The Unification Center waits for the rest of its world's plan (0.2.39.1). It only runs the world's conversion, which
     * needs every other part of the plan in place (its buildings by exact id, the world's conditions); by itself it's +1
     * stability for upkeep and marines and supplies. Ranked like any step, it went up as soon as research opened it, which is
     * before the Geomorphology Station (Global Centralization comes before Surface Restructuring), then sat idle for months
     * and held the colony's one construction slot. Now it waits until every step of the colony's own plan before it is done,
     * out of reach, or a condition a running terraforming project is already making, so it's built during that last project.
     */
    public static boolean held(MarketAPI m, Plan plan, Step step) {
        if (step == null || step.kind != Kind.BUILD || !UNIFICATION.equals(step.id) || plan == null) return false;
        for (Step s : plan.steps) {
            if (s == step) return false;
            if (s.extra || s.done(m) || dead(m, s.id) || worthless(m, plan, s)) continue;   // 0.3.0.0 (B2): nor for a step that's worth nothing
            if (s.kind != Kind.BUILD && Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy") && CATerraform.producing(m, s.id)) continue;
            return true;
        }
        return false;
    }

    /** Open plan steps that will each take a new industry slot (needsSlot). */
    public static int openIndustrySteps(MarketAPI m, Plan plan) {
        int n = 0;
        for (Step s : plan.steps) if (needsSlot(m, plan, s)) n++;
        return n;
    }

    static boolean industry(String id) {
        IndustrySpecAPI sp = spec(id);
        return sp != null && sp.hasTag(com.fs.starfarer.api.impl.campaign.ids.Industries.TAG_INDUSTRY);
    }

    /**
     * Whether an open step will take a new industry slot: an industry, not blocked by research or out of reach (dead), and
     * not an upgrade of an industry the colony has or an earlier open step builds (one slot per chain: Heavy Industry and
     * Orbital Works were two before 0.2.38.0, and a step that could never be built held its slot forever).
     */
    static boolean needsSlot(MarketAPI m, Plan plan, Step s) {
        if (s.kind != Kind.BUILD || s.done(m) || !industry(s.id)) return false;
        if (BLOCKED.contains(m.getId() + ":" + s.id) || dead(m, s.id)) return false;
        // 0.3.0.0 (B4): a step whose building was taken down here within the year can't be built back yet (CATombstone): no slot held for it
        if (CATombstone.blocked(m.getId(), s.id) > 0f) return false;
        // 0.3.0.0 (B2, research 02 sections 1 and 10): a step that's worth nothing here holds no slot; fast buildout (money only)
        // keeps one only for a plan industry that pays on money alone, as it last scored
        if (CAEngine.profitOnly ? !legacy() && !paysOnMoney(m, s) : worthless(m, plan, s)) return false;
        for (Industry i : m.getIndustries()) if (i.isIndustry() && (leadsTo(i.getId(), s.id) || inChainBelow(i.getId(), s.id))) return false;
        for (Step t : plan.steps) {
            if (t == s || t.kind != Kind.BUILD || t.done(m) || !industry(t.id)) continue;
            if (leadsTo(t.id, s.id) || inChainBelow(t.id, s.id)) return false;
        }
        return true;
    }

    /** low comes before high in one of the fixed upgrade chains. */
    static boolean inChainBelow(String low, String high) {
        for (String[] chain : CHAINS) {
            int a = -1, b = -1;
            for (int i = 0; i < chain.length; i++) { if (chain[i].equals(low)) a = i; if (chain[i].equals(high)) b = i; }
            if (a >= 0 && b > a) return true;
        }
        return false;
    }

    // ---------------- 0.3.0.0 (WS-B2): what plan steps are worth (research 02 section 1) ----------------
    // Since 0.2.44.0's market model many plan producers are worth little or nothing (a fifth ore producer, Light Industry at
    // size 6), but every open step kept its rank and an industry slot, so the real next step got a smaller bonus and better
    // off-plan industries paid the slot reserve forever. Now each evaluation records what a pick scored for the plan step it
    // advances (its final score: own worth, half of what it opens the way to, price over the horizon). A step whose last
    // score is 0 or less for WORTHLESS_DAYS takes no rank and holds no slot; it counts again the day it scores above 0.
    // Enablers (terraforming structures, a special world's buildings, projects, the capital, stations, spending freely,
    // research, the Bifrost) never go worthless: they're built for what they enable, not what they earn. Session only: a
    // load starts every clock again (the 0.2.45 behavior until then), nothing is saved.

    static final class Worth {
        float score;
        /** Game day it last went to 0 or less (NaN while it's worth something). */
        float since = Float.NaN;
        /** The evaluation it was recorded in: two picks for one step in one evaluation keep the better score. */
        int seq = -1;
    }

    /** "marketId:stepId" (plus "|money" for fast buildout's money-only scores) -> what the step last scored. */
    static final java.util.Map<String, Worth> WORTH = new java.util.HashMap<String, Worth>();
    /** Steps whose going worthless was written to starsector.log this session. */
    static final java.util.Set<String> WORTHLESS_LOGGED = new java.util.HashSet<String>();
    /** Bumped at the start of each evaluation (CAEngine.score). */
    static int worthSeq = 0;

    static void newEvaluation() { worthSeq++; }

    static String worthKey(MarketAPI m, String id, boolean money) { return key(m, id) + (money ? "|money" : ""); }

    /** What an evaluation's pick scored for the plan step it advances (money: fast buildout's money-only ranking). */
    static void noteWorth(MarketAPI m, String stepId, float score, boolean money) {
        if (m == null || stepId == null || Float.isNaN(score)) return;
        String k = worthKey(m, stepId, money);
        Worth w = WORTH.get(k);
        if (w == null) { w = new Worth(); WORTH.put(k, w); }
        if (w.seq == worthSeq) score = Math.max(score, w.score);   // two ways to the same step in one evaluation: the better one
        w.score = score;
        w.seq = worthSeq;
        w.since = sinceAfter(w.since, score, CATime.now());
        if (score > 0f) WORTHLESS_LOGGED.remove(k);
    }

    /** Pure: since when a step has been worth nothing, after a new score at `now` (NaN once it's worth something; a clock that went back starts again). */
    static float sinceAfter(float since, float score, float now) {
        if (score > 0f) return Float.NaN;
        return Float.isNaN(since) || now < since ? now : since;
    }

    /** Pure: worth nothing for at least `days` (never while worth something, or with a date this timeline hasn't reached). */
    static boolean worthlessFor(float since, float now, float days) {
        return !Float.isNaN(since) && now >= since && now - since >= days;
    }

    /** The step's last score in this mode, or null when no evaluation has scored it this session. */
    static Float lastScore(MarketAPI m, String stepId, boolean money) {
        if (m == null) return null;
        Worth w = WORTH.get(worthKey(m, stepId, money));
        return w == null ? null : w.score;
    }

    /** Fast buildout keeps a slot only for a plan industry whose money-only score was above 0 (research 02 section 10). */
    static boolean paysOnMoney(MarketAPI m, Step s) {
        Float v = lastScore(m, s.id, true);
        return v != null && v > 0f;
    }

    /**
     * A plan step that has scored nothing for WORTHLESS_DAYS: no rank, no reserved slot, doesn't hold the Unification
     * Center. Never an extra step (they're all enablers), an enabler, a building the special world needs, nor with legacyPlanner.
     */
    public static boolean worthless(MarketAPI m, Plan plan, Step s) {
        if (m == null || s == null || s.extra || s.kind != Kind.BUILD || WORTH.isEmpty()) return false;
        Worth w = WORTH.get(worthKey(m, s.id, false));
        if (w == null || !worthlessFor(w.since, CATime.now(), CAHorizon.WORTHLESS_DAYS)) return false;
        if (worldStep(plan, s.id) || legacy()) return false;
        boolean enabler;
        try { enabler = enabler(m, s.id); } catch (Throwable t) { enabler = true; }   // can't tell: keep it as 0.2.45 did
        if (enabler) return false;
        String k = worthKey(m, s.id, false);
        if (!CAEngine.forDisplay && !CAEngine.noProbe && WORTHLESS_LOGGED.add(k)) {   // starsector.log only, once a session; never from a panel's scoring
            try {
                CADiagnostics.LOG.info(CADiagnostics.TAG + m.getName() + ": the plan's " + s.label + " step has scored nothing for "
                        + Math.round(CAHorizon.WORTHLESS_DAYS) + " days (" + Math.round(w.score) + "), so it takes no plan rank or industry slot until it pays");
            } catch (Throwable ignored) { }
        }
        return true;
    }

    /**
     * The picks the plan builds for what they enable rather than what they earn (0.2.45 CAEngine.finish's list, kept as it was):
     * Terraforming Made Easy's structures, a special project's producers, spending freely's structures, the Research Facility
     * research needs, the capital's trade buildings, the colony's stations and the Bifrost Control Center for a wonder.
     */
    public static boolean enabler(MarketAPI m, String id) {
        if (id == null) return false;
        return id.startsWith("tme_") || CAProjects.isStep(m, id) || CASurplus.isRichStep(m, id) || CAResearch.isFacilityStep(m, id)
                || CACapital.isStep(m, id) || CAStations.isStep(m, id)
                || (CAWonders.BFC.equals(id) && !CAWonders.stepsFor(m).isEmpty());
    }

    /** A building the plan's special world needs by exact id (the Unification Center checks it): ranked as in 0.2.45 whatever it earns. */
    static boolean worldStep(Plan plan, String id) {
        return plan != null && plan.world != null && id != null && (WORLD_STEPS.contains(id) || plan.exact.contains(id));
    }

    /** 0.3.0.0 (B2): what a new off-plan industry must beat to take one of the slots the plan's industries need. */
    public static final class Reserve {
        /** Plan industries still to come that need a new slot. */
        public int needed;
        /** The score of the one it would push out (0: room for all of them). */
        public float amount;
    }

    /**
     * 0.3.0.0 (B2, research 02 sections 1 and 10): the plan's open industries that need a new slot (needsSlot: not worthless,
     * one per chain; fast buildout: only ones that pay on money; the endgame: only the ones it keeps), each at its last
     * score in this mode, or CAEngine.SLOT_RESERVE (0.2.45's flat reserve) while it hasn't scored above 0.
     */
    static Reserve reserve(MarketAPI m, Plan plan, int free, boolean endgame) {
        Reserve r = new Reserve();
        if (m == null || plan == null) return r;
        boolean money = CAEngine.profitOnly;
        List<Float> amounts = new ArrayList<Float>();
        for (Step s : plan.steps) {
            if (!needsSlot(m, plan, s)) continue;
            if (endgame && !CAEndgame.keepsPlanBonus(m, plan, s.id)) continue;
            Float v = lastScore(m, s.id, money);
            amounts.add(v != null && v > 0f ? v : CAEngine.SLOT_RESERVE);
        }
        r.needed = amounts.size();
        r.amount = reserveAmount(amounts, free);
        return r;
    }

    /**
     * Pure: the plan's industries would fill the free slots best first, so a new off-plan one pushes out the weakest of those
     * that fit (the free-th best) and must beat its score. 0 when there's a slot for every one of them.
     */
    static float reserveAmount(List<Float> amounts, int free) {
        int n = amounts.size();
        if (n == 0 || free > n) return 0f;
        List<Float> sorted = new ArrayList<Float>(amounts);
        java.util.Collections.sort(sorted, java.util.Collections.<Float>reverseOrder());
        return sorted.get(Math.max(0, Math.min(n - 1, free - 1)));
    }

    /**
     * 0.3.0.0 (B2, research 02 section 1): the plan's open build steps reached by upgrading from this building (Orbital Works
     * after Heavy Industry, Military Base and High Command after a Patrol HQ, Farming after AotD's Monoculture Plots), so
     * the step it is carries part of what they'd add. Not steps research blocks for now or that have no way to them.
     */
    static List<Step> unlocked(MarketAPI m, Plan plan, String id) {
        List<Step> out = new ArrayList<Step>();
        if (m == null || plan == null || id == null) return out;
        for (Step t : plan.steps) {
            if (t.kind != Kind.BUILD || t.id.equals(id) || "station".equals(t.id) || t.done(m)) continue;
            if (!(leadsTo(id, t.id) || inChainBelow(id, t.id))) continue;
            if (BLOCKED.contains(key(m, t.id)) || dead(m, t.id)) continue;
            out.add(t);
        }
        return out;
    }

    /** The building a step's building is upgraded from (its downgrade, or the fixed chain's step before it), else fallback. */
    static String upgradedFrom(String id, String fallback) {
        IndustrySpecAPI sp = spec(id);
        String d = sp == null ? null : sp.getDowngrade();
        if (d != null) return d;
        for (String[] chain : CHAINS) for (int i = 1; i < chain.length; i++) if (chain[i].equals(id)) return chain[i - 1];
        return fallback;
    }

    // ---------------- steps with no way to them (0.2.38.0) ----------------
    // A plan is built from what's installed, but a step can still be out of reach in a game: a building research never
    // unlocks, a blueprint you don't know, a deposit the planet lacks, a mod removed mid-save, a world that can't form.
    // Such a step held the plan's top rank and an industry slot forever. Now every open build step is checked each pass;
    // one with no way to it (it, or something on the way to it, can't be built or started here, and no research Autopilot
    // is running would open it) for DEAD_DAYS stops counting: no plan rank, no reserved slot, a log line. It counts again
    // the day a way opens. A special world (Terraforming Made Easy) whose own buildings go out of reach is dropped for
    // WORLD_DEAD_DAYS, so the colony goes back to the plain plan for its goal.

    public static final float DEAD_DAYS = CATuning.STEP_DEAD_DAYS, WORLD_DEAD_DAYS = CATuning.WORLD_DEAD_DAYS;   // 0.3.0.0 (A3): numbers in CATuning

    static String key(MarketAPI m, String id) { return m.getId() + ":" + id; }

    static float daysSince(Long t) { return t == null ? 0f : Global.getSector().getClock().getElapsedDaysSince(t); }

    /** Out of reach for DEAD_DAYS or more. */
    public static boolean dead(MarketAPI m, String id) {
        if (Global.getSector() == null) return false;
        Long since = CAData.get().stepStuck().get(key(m, id));
        return since != null && daysSince(since) >= DEAD_DAYS;
    }

    /** A special world dropped at this colony for now. */
    public static boolean worldDead(MarketAPI m, String world) {
        Long t = CAData.get().worldDead().get(key(m, world));
        return t != null && daysSince(t) < WORLD_DEAD_DAYS;
    }

    /**
     * 0.3.0.0 (test fix, 2026-11-11): worldDead key prefix for a structure that is out of reach here. Every special world whose
     * plan needs it is dropped with it for WORLD_DEAD_DAYS. Every world plan ends with the Unification Center, so before this
     * each world was dropped on its own, one log line a day or a week (Hive, Death, Waste, Paradise, Forge on the test save).
     */
    static final String STEP_MARK = "step:";

    /** A world plan that needs a structure dropped at this colony (STEP_MARK). */
    public static boolean stepDropped(MarketAPI m, Plan p) {
        if (m == null || p == null) return false;
        for (Step s : p.steps) if (s.kind == Kind.BUILD && worldDead(m, STEP_MARK + s.id)) return true;
        return false;
    }

    /** Vaults of Knowledge research that Autopilot runs itself, so a research lock is a way forward. */
    static boolean autoResearch() {
        return CAData.settings().researchMode == 1 && Global.getSettings().getModManager().isModEnabled(CAResearch.MOD) && CAResearch.link();
    }

    /** It can be built or started here now, ignoring money, slots and what's under construction; a research lock counts while Autopilot researches. */
    static boolean reachable(MarketAPI m, String id) {
        try {
            IndustrySpecAPI sp = spec(id);
            if (sp == null) return false;
            if (!CADiagnostics.autopilotBuilds(id) && !CAEngine.isSupported(id)) return false;   // Autopilot doesn't build it
            if (CAMods.leftToYou(id) != null && !CACapital.isStep(m, id)) return false;   // Yunru's Bazaar: only the capital's (0.2.42.0)
            Industry inst = sp.getNewPluginInstance(m);
            if (!(CATasc.isTasc(sp) ? CATasc.available(m, inst) : inst.isAvailableToBuild())) return false;
            if (CAAotD.enabled() && !CAAotD.allows(id, m)) return autoResearch();
            return true;
        } catch (Throwable t) { return false; }
    }

    /** A way to the step: it or something on the way to it can be built, or is being built, here. */
    static boolean wayTo(MarketAPI m, Step s) {
        if (s.id.equals("station")) return true;
        String cur = s.id;
        for (int k = 0; k < 8 && cur != null; k++) {
            Industry have = m.getIndustry(cur);
            if (have != null) { if (have.isBuilding() || have.isUpgrading()) return true; }
            else if (reachable(m, cur)) return true;
            IndustrySpecAPI sp = spec(cur);
            cur = sp == null ? null : sp.getDowngrade();
        }
        for (String[] chain : CHAINS) for (int i = 1; i < chain.length; i++)   // a fixed chain's earlier step, buildable here
            if (chain[i].equals(s.id) && !m.hasIndustry(chain[i - 1]) && reachable(m, chain[i - 1])) return true;
        return false;
    }

    /** Daily, from every evaluation: which open steps have no way to them, and since when. */
    public static void track(MarketAPI m, Plan plan) {
        if (CALog.whatIf()) return;   // 0.3.0.0 (07 M2): a what-if valuation (CAResearch) writes nothing to the save (stepStuck, worldDead) and logs nothing
        if (Global.getSector() == null || plan == null) return;
        CAData d = CAData.get();
        long now = Global.getSector().getClock().getTimestamp();
        java.util.Set<String> open = new java.util.HashSet<String>();
        for (Step s : plan.steps) {
            if (s.kind != Kind.BUILD || s.done(m)) continue;
            String k = key(m, s.id);
            open.add(k);
            if (wayTo(m, s)) {
                Long was = d.stepStuck().remove(k);
                if (was != null && daysSince(was) >= DEAD_DAYS) {
                    CALog.forget("plan:dead:" + k);
                    CALog.think(m.getName() + ": the plan's " + s.label + " step is reachable again. It counts again.");
                    CACache.bump("plan step back");   // 0.3.0.0 (A4): evaluations kept today ranked without it
                }
            } else if (!d.stepStuck().containsKey(k)) d.stepStuck().put(k, now);
            else if (dead(m, s.id) && CALog.once("plan:dead:" + k)) {
                CALog.think(m.getName() + ": the plan's " + s.label + " step can't be reached here (no way to build it for " + Math.round(DEAD_DAYS)
                        + " days). The plan goes on without it. It counts again if that changes.");
                CACache.bump("plan step dead");   // 0.3.0.0 (A4): evaluations kept today still ranked it
            }
        }
        String pre = m.getId() + ":";
        for (java.util.Iterator<String> it = d.stepStuck().keySet().iterator(); it.hasNext(); ) {
            String k = it.next();
            if (k.startsWith(pre) && !open.contains(k)) it.remove();   // done, or no longer in the plan
        }
        if (plan.world == null) return;
        for (Step s : plan.steps) {
            if (s.kind != Kind.BUILD || s.done(m) || !(WORLD_STEPS.contains(s.id) || plan.exact.contains(s.id)) || !dead(m, s.id)) continue;
            d.worldDead().put(key(m, plan.world), now);
            d.worldDead().put(key(m, STEP_MARK + s.id), now);   // every other special world that needs this structure goes with it
            CACache.bump("world dropped");   // 0.3.0.0 (A4): kept plans and evaluations still aimed at that world
            CALog.quiet(m.getName() + ": " + (UNIFICATION.equals(s.id) ? "no special world can form here" : "the " + plan.name + " and other special worlds that need "
                    + s.label + " can't form here") + " (" + s.label + " is out of reach). For a year the colony follows the plain plan for its goal");
            break;
        }
    }

    /** Research-locked open steps at any colony, from the last tracking passes (a Research Facility is worth having for them). */
    static int lockedCache = 0, lockedDay = -1;

    public static int lockedSteps() {
        if (Global.getSector() == null || !CAAotD.enabled()) return 0;
        com.fs.starfarer.api.campaign.CampaignClockAPI clock = Global.getSector().getClock();
        int day = clock.getCycle() * 400 + clock.getMonth() * 32 + clock.getDay();
        if (day == lockedDay) return lockedCache;
        lockedDay = day;
        int n = 0;
        for (MarketAPI m : CAItems.autoMarkets()) {
            CAPlan.Plan p;
            try { p = build(m, CAData.get().colony(m.getId()).profile); } catch (Throwable t) { continue; }
            for (Step s : p.steps) if (s.kind == Kind.BUILD && !s.done(m) && spec(s.id) != null && !CAAotD.allows(s.id, m)) n++;
        }
        lockedCache = n;
        return n;
    }

    /** Score bonus for a terraforming project that adds a plan condition or removes a plan hazard. */
    public static float bonusForCondition(MarketAPI m, Plan plan, String condId, boolean adding) {
        for (Step s : plan.steps) {
            if (s.done(m)) continue;
            if (adding && s.kind == Kind.COND && s.id.equals(condId)) return CATuning.PLAN_COND_ADD;
            if (!adding && s.kind == Kind.NOCOND && s.id.equals(condId)) return CATuning.PLAN_COND_REMOVE;
        }
        return 0f;
    }
}
