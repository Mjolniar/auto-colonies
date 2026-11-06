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
        if (inChain(m, id)) return true;
        if (id.equals("farming") && m.hasIndustry("aquaculture")) return true;   // a water world's farming (vanilla Farming can't go there)
        for (Industry i : m.getIndustries()) if (leadsTo(id, i.getId())) return true;
        return false;
    }

    /** True when the colony has this building or a later step of one of the fixed upgrade chains. */
    static boolean inChain(MarketAPI m, String id) {
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

    static IndustrySpecAPI spec(String id) {
        try { return Global.getSettings().getIndustrySpec(id); } catch (Throwable t) { return null; }
    }

    /** True when target is reached by upgrading from: from appears in target's chain of downgrade links. */
    static boolean leadsTo(String from, String target) {
        String cur = target;
        for (int k = 0; k < 8 && cur != null; k++) {
            IndustrySpecAPI s;
            try { s = Global.getSettings().getIndustrySpec(cur); } catch (Throwable t) { return false; }
            if (s == null) return false;
            cur = s.getDowngrade();
            if (from.equals(cur)) return true;
        }
        return false;
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
        Plan p = buildOwn(m, prof);
        List<Step> extra = new ArrayList<Step>(CAResearch.stepsFor(m));   // a Research Facility when there's none and research is Auto
        extra.addAll(CAProjects.stepsFor(m));
        extra.addAll(CASurplus.richSteps(m));   // spending freely: every station and defense maxed
        extra.addAll(CAWonders.stepsFor(m));    // and the Bifrost Control Center at one colony
        if (extra.isEmpty()) return p;
        List<Step> steps = new ArrayList<Step>();
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (Step s : extra) if (exists(s) && ids.add(s.id)) { s.extra = true; steps.add(s); }
        for (Step s : p.steps) if (s.kind != Kind.BUILD || !ids.contains(s.id)) steps.add(s);
        p.steps = steps;
        return p;
    }

    static Plan buildOwn(MarketAPI m, CAData.Profile prof) {
        if (CAHypershunt.is(m)) { Plan h = new Plan(); h.name = "Coronal Hypershunt"; return h; }   // nothing can be built there
        Plan wp = null;
        try { wp = CAWorldPlanner.plan(m, prof); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "world planner failed at " + m.getName(), t); }
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
            // (2.38.0: these three had slipped into the comment above in 2.30.0, so a Paradise plan was nearly empty)
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
        return p;
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
     */
    public static float bonusFor(MarketAPI m, Plan plan, String industryId) { return bonusFor(m, plan, industryId, true); }

    /**
     * skipBlocked false: the step's rank as if research had opened every step, which is what the research it waits on is
     * worth (counting blocked steps out there made a blocked step worth nothing every other day).
     */
    public static float bonusFor(MarketAPI m, Plan plan, String industryId, boolean skipBlocked) {
        int rank = 0;
        for (Step s : plan.steps) {
            if (s.kind != Kind.BUILD || s.done(m) || (skipBlocked && (BLOCKED.contains(m.getId() + ":" + s.id) || dead(m, s.id)))) continue;
            if (held(m, plan, s)) continue;   // the Unification Center, until the rest of its world is ready (2.39.1)
            boolean match = s.id.equals(industryId) || (s.id.equals("station") && stationTier(spec(industryId)) > 0)
                    || leadsTo(industryId, s.id);
            if (match) return rank == 0 ? 60000f : rank == 1 ? 40000f : 20000f;
            rank++;
        }
        return 0f;
    }

    static final String UNIFICATION = "tme_unification_center";

    /**
     * The Unification Center waits for the rest of its world's plan (2.39.1). It only runs the world's conversion, which
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
            if (s.extra || s.done(m) || dead(m, s.id)) continue;
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
     * Orbital Works were two before 2.38.0, and a step that could never be built held its slot forever).
     */
    static boolean needsSlot(MarketAPI m, Plan plan, Step s) {
        if (s.kind != Kind.BUILD || s.done(m) || !industry(s.id)) return false;
        if (BLOCKED.contains(m.getId() + ":" + s.id) || dead(m, s.id)) return false;
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

    // ---------------- steps with no way to them (2.38.0) ----------------
    // A plan is built from what's installed, but a step can still be out of reach in a game: a building research never
    // unlocks, a blueprint you don't know, a deposit the planet lacks, a mod removed mid-save, a world that can't form.
    // Such a step held the plan's top rank and an industry slot forever. Now every open build step is checked each pass;
    // one with no way to it (it, or something on the way to it, can't be built or started here, and no research Autopilot
    // is running would open it) for DEAD_DAYS stops counting: no plan rank, no reserved slot, a log line. It counts again
    // the day a way opens. A special world (Terraforming Made Easy) whose own buildings go out of reach is dropped for
    // WORLD_DEAD_DAYS, so the colony goes back to the plain plan for its goal.

    public static final float DEAD_DAYS = 60f, WORLD_DEAD_DAYS = 365f;

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
            if (CAMods.leftToYou(id) != null) return false;
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
                    CALog.add(m.getName() + ": the plan's " + s.label + " step can be reached again, so it counts again");
                }
            } else if (!d.stepStuck().containsKey(k)) d.stepStuck().put(k, now);
            else if (dead(m, s.id) && CALog.once("plan:dead:" + k))
                CALog.add(m.getName() + ": the plan's " + s.label + " step can't be reached here (no way to build it for " + Math.round(DEAD_DAYS)
                        + " days), so the plan goes on without it. It counts again if that changes");
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
            CALog.add(m.getName() + ": the " + plan.name + " can't form here (" + s.label + " is out of reach), so for a year the colony follows the plain plan for its goal");
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
            if (adding && s.kind == Kind.COND && s.id.equals(condId)) return 60000f;
            if (!adding && s.kind == Kind.NOCOND && s.id.equals(condId)) return 50000f;
        }
        return 0f;
    }
}
