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
 */
public class CAPlan {
    public enum Kind { BUILD, COND, NOCOND }

    public static class Step {
        public Kind kind; public String id, label;
        public boolean exact;   // the Unification Center checks this building by id, so upgraded versions don't count
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
            {"ii_orbitalstation", "ii_battlestation", "ii_starfortress"}, {"IndEvo_ComArray", "IndEvo_IntArray"},
            {"fuelprod", "ix_fuel_production"}};

    /**
     * True when the colony has this building or something upgraded from it: a later step of a known chain, or a
     * building whose downgrade links lead back to it (AotD's Artisanal Farming counts as Farming, Farming as Monoculture Plots).
     */
    static boolean hasOrBetter(MarketAPI m, String id) {
        if (inChain(m, id)) return true;
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

    static void b(Plan p, String id, String label) { p.steps.add(new Step(Kind.BUILD, id, label)); }
    static void c(Plan p, String id, String label) { p.steps.add(new Step(Kind.COND, id, label)); }
    static void n(Plan p, String id, String label) { p.steps.add(new Step(Kind.NOCOND, id, label)); }

    public static Plan build(MarketAPI m, CAData.Profile prof) {
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
            for (Step s : p.steps) if (seen.add(s.kind + ":" + s.id)) keep.add(s);
            p.steps = keep;
            return p;
        }
        Plan p = new Plan();
        boolean hab = m.hasCondition("habitable");
        boolean ore = has(m, "ore_"), rare = has(m, "rare_ore_"), vol = has(m, "volatiles_"), org = has(m, "organics_"), farm = has(m, "farmland_");
        boolean cold = m.hasCondition("cold") || m.hasCondition("very_cold");
        boolean war = prof == CAData.Profile.WAR, sec = prof == CAData.Profile.SECURITY, inc = prof == CAData.Profile.INCOME,
                grow = prof == CAData.Profile.GROWTH, self = prof == CAData.Profile.SELF;

        b(p, "spaceport", "Spaceport"); b(p, "megaport", "Megaport");
        if ((war || self || (ore && rare)) && !hab && (ore || rare)) {
            p.name = "Forge World";
            b(p, "mining", "Mining"); b(p, "refining", "Refining"); b(p, "heavyindustry", "Heavy Industry"); b(p, "orbitalworks", "Orbital Works");
            b(p, "tme_mineral_replicator", "Mineral Replicator"); b(p, "tme_element_synthesizer", "Element Synthesizer");
            b(p, "tme_terrestrial_engine", "Terrestrial Engine");
            c(p, "extreme_tectonic_activity", "Extreme tectonic activity (lets rich deposits be added)");
            c(p, "ore_ultrarich", "Ultrarich ore"); c(p, "rare_ore_ultrarich", "Ultrarich transplutonic ore");
            b(p, "tme_unification_center", "Unification Center"); c(p, "tme_forge_world", "Forge World");
        } else if ((sec || war) && hab) {
            p.name = "Fortress World";
            b(p, "heavyindustry", "Heavy Industry"); b(p, "orbitalworks", "Orbital Works"); b(p, "patrolhq", "Patrol HQ");
            b(p, "militarybase", "Military Base"); b(p, "highcommand", "High Command"); b(p, "fuelprod", "Fuel Production");
            b(p, "grounddefenses", "Ground Defenses"); b(p, "heavybatteries", "Heavy Batteries"); b(p, "station", "Orbital station");
            b(p, "tme_element_synthesizer", "Element Synthesizer"); c(p, "volatiles_plentiful", "Plentiful volatiles");
            b(p, "tme_unification_center", "Unification Center"); c(p, "tme_fortress_world", "Fortress World");
        } else if (hab && (inc || grow || prof == CAData.Profile.BALANCED)) {
            p.name = "Paradise World";
            b(p, "farming", "Farming"); b(p, "lightindustry", "Light Industry"); b(p, "commerce", "Commerce"); b(p, "waystation", "Waystation");
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
            if (!p.name.equals("Forge World")) { n(p, "toxic_atmosphere", "Remove toxic atmosphere"); n(p, "dense_atmosphere", "Remove dense atmosphere"); }
        }
        if (m.hasCondition("irradiated") || m.hasCondition("pollution") || m.hasCondition("inimical_biosphere")) {
            b(p, "tme_geomorphology_station", "Geomorphology Station");
            n(p, "irradiated", "Remove radiation"); n(p, "pollution", "Remove pollution"); n(p, "inimical_biosphere", "Remove inimical biosphere");
        }
        if (!p.name.equals("Forge World") && (m.hasCondition("high_gravity") || m.hasCondition("extreme_tectonic_activity"))) {
            b(p, "tme_terrestrial_engine", "Terrestrial Engine");
            n(p, "high_gravity", "Remove high gravity"); n(p, "extreme_tectonic_activity", "Remove extreme tectonic activity");
        }
        b(p, "grounddefenses", "Ground Defenses");
        // drop steps that don't apply (conditions that were never present for NOCOND)
        List<Step> keep = new ArrayList<Step>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (Step s : p.steps) {
            if (s.kind == Kind.NOCOND && !m.hasCondition(s.id)) continue;
            if (!seen.add(s.kind + ":" + s.id)) continue;
            keep.add(s);
        }
        p.steps = keep;
        return p;
    }

    /**
     * Score bonus for a build or upgrade pick that advances the plan; the earliest open build steps get the most.
     * A pick that a plan building is upgraded from counts too (with AotD, Monoculture Plots on the way to Farming).
     */
    public static float bonusFor(MarketAPI m, Plan plan, String industryId) {
        int rank = 0;
        for (Step s : plan.steps) {
            if (s.kind != Kind.BUILD || s.done(m)) continue;
            boolean match = s.id.equals(industryId) || (s.id.equals("station") && industryId.startsWith("orbitalstation"))
                    || leadsTo(industryId, s.id);
            if (match) return rank == 0 ? 60000f : rank == 1 ? 40000f : 20000f;
            rank++;
        }
        return 0f;
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
