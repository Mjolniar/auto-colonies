package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Plans a Terraforming Made Easy special world (Forge, Fortress, Paradise, Hive, Death, Waste) from TME's own
 * requirement table: every needAll/needOne requirement is resolved back through the projects that create it,
 * and each project's side effects (TME removes a new condition's "hated" conditions) are simulated.
 * Reads TME's CSV only, so it never links against TME code.
 */
public class CAWorldPlanner {
    public static class Row {
        String structure, condition, liked, likedInd, hated;
        float cost, days;
        boolean gasGiants;
    }

    static Map<String, Row> rows = null;   // condition id -> project row

    static Map<String, Row> rows() {
        if (rows != null) return rows;
        rows = new HashMap<String, Row>();
        try {
            String[] tb = CATme.table();   // 0.3.0.5: TME 2.10.0 moved the table and renamed its gas giant column
            JSONArray arr = Global.getSettings().getMergedSpreadsheetDataForMod(tb[1], tb[0], "terraformingmadeeasy");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String s = o.optString("structureId", ""), c = o.optString("conditionId", "");
                if (s.isEmpty() || c.isEmpty() || s.startsWith("#")) continue;
                Row r = new Row();
                r.structure = s; r.condition = c;
                r.cost = (float) o.optDouble("cost", 0); r.days = (float) o.optDouble("buildTime", 0);
                r.gasGiants = "TRUE".equalsIgnoreCase(o.optString(tb[2], "false"));
                r.liked = o.optString("likedConditions", "").replaceAll("\\s", "");
                r.likedInd = o.optString("likedIndustries", "").replaceAll("\\s", "");
                r.hated = o.optString("hatedConditions", "").replaceAll("\\s", "");
                if (!rows.containsKey(c) || s.equals("tme_unification_center")) rows.put(c, r);
            }
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "could not read Terraforming Made Easy's project table", t);
        }
        return rows;
    }

    /** Parsed requirement: each group is needAll (AND) or needOne (OR) over ids. */
    static List<String[]> groups(String text, List<Boolean> isAll) {
        List<String[]> out = new ArrayList<String[]>();
        if (text == null || text.isEmpty()) return out;
        for (String expr : text.split(",")) {
            boolean all = !expr.contains("needOne");
            String ids = expr.replace("needAll:", "").replace("needOne:", "");
            if (ids.isEmpty()) continue;
            out.add(ids.split("\\|"));
            isAll.add(all);
        }
        return out;
    }

    static Set<String> idsOf(String text) {
        Set<String> s = new HashSet<String>();
        List<Boolean> all = new ArrayList<Boolean>();
        for (String[] g : groups(text, all)) s.addAll(Arrays.asList(g));
        return s;
    }

    static class Sim {
        Set<String> conds; Set<String> inds; List<CAPlan.Step> steps = new ArrayList<CAPlan.Step>();
        float cost = 0f; Set<String> keep = new HashSet<String>(); boolean gasGiant;
        Sim copy() {
            Sim s = new Sim(); s.conds = new HashSet<String>(conds); s.inds = new HashSet<String>(inds);
            s.steps = new ArrayList<CAPlan.Step>(steps); s.cost = cost; s.keep = new HashSet<String>(keep); s.gasGiant = gasGiant; return s;
        }
    }

    static String name(String condId) {
        try { return Global.getSettings().getMarketConditionSpec(condId).getName(); } catch (Throwable t) { return condId; }
    }

    static String industryName(String id) {
        try { return Global.getSettings().getIndustrySpec(id).getName(); } catch (Throwable t) { return id; }
    }

    /** Adds the steps that make condition c present; false when it can't be reached (depth-limited). */
    static boolean resolve(Sim sim, String c, int depth) {
        if (sim.conds.contains(c)) return true;
        if (depth > 4) return false;
        Row r = rows().get(c);
        if (r == null || r.structure.equals("tme_unification_center") && depth > 0) return false;
        if (sim.gasGiant && !r.gasGiants) return false;
        // 0.3.9.2 (I C3): the Stellar Manufactory's projects (mirror and shade choices, the player starts them) are named in the step label below
        List<Boolean> all = new ArrayList<Boolean>();
        List<String[]> gs = groups(r.liked, all);
        for (int i = 0; i < gs.size(); i++) {
            String[] g = gs.get(i);
            if (all.get(i)) {
                for (String id : g) if (!resolve(sim, id, depth + 1)) return false;
            } else {
                boolean ok = false;
                for (String id : g) if (sim.conds.contains(id)) { ok = true; break; }
                if (!ok) {
                    Sim best = null;
                    for (String id : g) {
                        Sim trial = sim.copy();
                        if (resolve(trial, id, depth + 1) && (best == null || trial.cost < best.cost)) best = trial;
                    }
                    if (best == null) return false;
                    sim.conds = best.conds; sim.inds = best.inds; sim.steps = best.steps; sim.cost = best.cost; sim.keep = best.keep;
                }
            }
        }
        // side effects: adding c removes its hated conditions; refuse if that breaks something the plan needs
        Set<String> hated = idsOf(r.hated);
        for (String h : hated) if (sim.keep.contains(h) && sim.conds.contains(h)) return false;
        if (!sim.inds.contains(r.structure)) {
            sim.steps.add(new CAPlan.Step(CAPlan.Kind.BUILD, r.structure, industryName(r.structure)));
            sim.inds.add(r.structure);
        }
        String label = name(c) + (r.structure.equals("tme_stellar_manufactory") ? " (Stellar Manufactory: you start this one)" : "");
        sim.steps.add(new CAPlan.Step(CAPlan.Kind.COND, c, label));
        sim.conds.add(c);
        sim.conds.removeAll(hated);
        sim.keep.add(c);
        sim.cost += r.cost;
        return true;
    }

    static final String[] WORLDS = {"tme_forge_world", "tme_fortress_world", "tme_paradise_world", "tme_hive_world", "tme_death_world", "tme_waste_world"};

    /** 0.3.18.0: the Waste World's id. */
    static final String WASTE = "tme_waste_world";

    /**
     * Pure (0.3.18.0): a world no plan works toward. With Ashes of the Domain, the Waste World: TME's Unification Center
     * needs Heavy Industry by its exact id until the world forms, which holds back Orbital Works, the Skunkworks and the
     * Fleetworks, and the world adds +1 output to Population, Mining and Refining at a hazard of +125% with its conditions.
     */
    static boolean dropped(String world, boolean aotd) { return aotd && WASTE.equals(world); }

    /**
     * Pure (0.3.29.0): with TME's rules as loaded (live), the Waste World stays: TME's Vaults of Knowledge rule takes Orbital
     * Works or its upgrades in place of Heavy Industry. It's dropped only with Ashes of the Domain when that rule can't be read.
     */
    static boolean dropped(String world, boolean aotd, boolean live) { return aotd && !live && WASTE.equals(world); }

    /** 0.3.18.0: dropped() with Ashes of the Domain's mods as loaded (Vaults of Knowledge or Building Menu Overhaul). */
    static boolean dropped(String world) {
        if (!WASTE.equals(world)) return false;
        try {
            com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
            String rule = liveRules().get(WASTE);
            return dropped(world, mm.isModEnabled(CAResearch.MOD) || mm.isModEnabled("bmo"), rule != null && !rule.contains("heavyindustry"));   // 0.3.29.0
        } catch (Throwable t) { return false; }
    }

    // ---------------- 0.3.29.0 (plan 2026-11-25, batch 6): TME's rules as the game checks them ----------------

    static final String HIVE = "tme_hive_world", FISHING = "aotd_fishing_harbour";

    /**
     * The industries each world gives +1 output (TME 2.10.6's world conditions, the ids that exist with Vaults of Knowledge
     * 5.0.7). An upgrade out of one loses it, so these aren't upgraded at a world that's planned or formed. The Hive World
     * gives it to every industry, so it keeps nothing.
     */
    static final Map<String, Set<String>> BONUS = new HashMap<String, Set<String>>();
    static {
        BONUS.put("tme_forge_world", new HashSet<String>(Arrays.asList("orbitalworks", "mining", "refining", "fuelprod", "triheavy", "hegeheavy", "nidavelir_complex", "pluto_station")));
        BONUS.put("tme_fortress_world", new HashSet<String>(Arrays.asList("population", "orbitalworks", "highcommand", "fuelprod", "triheavy", "hegeheavy", "nidavelir_complex")));
        BONUS.put("tme_paradise_world", new HashSet<String>(Arrays.asList("population", "farming", "aquaculture", "lightindustry")));
        BONUS.put("tme_death_world", new HashSet<String>(Arrays.asList("population", "orbitalworks", "highcommand", "mining", "triheavy", "hegeheavy", "nidavelir_complex", "pluto_station")));
        BONUS.put(WASTE, new HashSet<String>(Arrays.asList("population", "mining", "refining", "pluto_station")));
    }

    /** Pure: a world's industry that isn't upgraded while the world is planned or formed. */
    static boolean keeps(String world, String id) { Set<String> b = BONUS.get(world); return b != null && b.contains(id); }

    /** Pure: what a formed world keeps (the Hive World nothing). */
    static Set<String> formedKeep(String world) { Set<String> b = BONUS.get(world); return b == null ? new HashSet<String>() : new HashSet<String>(b); }

    /** The Unification Center's industry rule per world as TME converted it at load (Vaults of Knowledge's ids); empty when unreadable. */
    static Map<String, String> liveRules = null;

    static Map<String, String> liveRules() {
        if (liveRules != null) return liveRules;
        liveRules = new HashMap<String, String>();
        try {
            Class<?> c = Class.forName("terraformingmadeeasy.loading.specs.TerraformingProjectSpec", true, Global.getSettings().getScriptClassLoader());
            Collection<?> all = Global.getSettings().getAllSpecs(c);
            if (all != null) for (Object sp : all) {
                if (!"tme_unification_center".equals(String.valueOf(CARefl.call(sp, "getStructureId")))) continue;
                Object rule = CARefl.call(sp, "getLikedIndustries");
                if (rule != null) liveRules.put(String.valueOf(CARefl.call(sp, "getConditionId")), String.valueOf(rule).replaceAll("\\s", ""));
            }
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Terraforming Made Easy's rules as loaded; world plans use its table", t);
        }
        return liveRules;
    }

    /** The industries a world's Unification Center needs, as the game checks them (TME's table when they can't be read). */
    static String likedIndustries(Row r) {
        if (r == null) return "";
        String live = liveRules().get(r.condition);
        return live != null && !live.isEmpty() ? live : r.likedInd;
    }

    /**
     * Pure: the building a needOne group's step builds: one the colony has; on a water world the Fishing Harbour for the
     * farming group; else the first one this game has (known).
     */
    static String pick(String[] group, Set<String> have, boolean water, Set<String> known) { return pick(group, have, water, known, null); }

    /**
     * Pure with its leads test (CAPlan.leadsTo in the game): 0.3.34.1: after one the colony has, a member it reaches by
     * upgrading a building it has. With Vaults of Knowledge, Civilian Heavy Production leads to the Macro Industrial Complex
     * and not to Orbital Works, and neither it nor Heavy Industry is in TME's heavy group.
     */
    static String pick(String[] group, Set<String> have, boolean water, Set<String> known, java.util.function.BiPredicate<String, String> leads) {
        for (String id : group) if (have.contains(id)) return id;
        if (leads != null) for (String id : group) if (known.contains(id)) for (String h : have) if (leads.test(h, id)) return id;
        List<String> g = Arrays.asList(group);
        if (water && g.contains(FISHING) && known.contains(FISHING)) return FISHING;
        for (String id : group) if (known.contains(id)) return id;
        return null;
    }

    /**
     * Pure: whether a group (or a needAll id as a group of one) can be had once the world's projects are done: Mining needs a
     * deposit and Farming farmland, Aquaculture or a water world with the Fishing Harbour in the group.
     */
    static boolean possible(String[] group, Set<String> have, boolean deposit, boolean farmland, boolean water) {
        List<String> g = Arrays.asList(group);
        for (String id : group) if (have.contains(id)) return true;
        if (g.contains("mining") && !deposit) return false;
        if (g.contains("farming") && !have.contains("aquaculture") && !farmland && !(water && g.contains(FISHING))) return false;
        return true;
    }

    static String label(MarketAPI m, String ind) {
        return industryName(ind) + (ind.equals("mining") && m.hasIndustry("IndEvo_SwitchableMining") ? " (vanilla Mining; Specialized Mining doesn't count)" :
                ind.equals("fuelprod") && m.hasIndustry("ix_fuel_production") ? " (vanilla Fuel Production; Advanced doesn't count)" : "");
    }

    /**
     * Adds a world's industry steps to p the way the game checks them: a needAll id is an exact step, and a needOne group
     * (with Vaults of Knowledge, "Mining or Plasma Harvester or Mining Megaplex or Pluto Mining Station") is one exact step
     * that any building of the group does. Fills p.exact and p.keep (needAll ids, and group steps that get the world's +1).
     * False when one can't be had here after the world's projects (condsAfter).
     */
    static boolean addIndustrySteps(MarketAPI m, String world, Row r, Set<String> condsAfter, CAPlan.Plan p) {
        Set<String> have = new HashSet<String>();
        for (Industry i : m.getIndustries()) have.add(i.getId());
        boolean water = condsAfter.contains("water_surface") || m.hasCondition("water_surface");
        boolean deposit = anyPrefix(condsAfter, "ore_", "rare_ore_", "volatiles_", "organics_"), farmland = anyPrefix(condsAfter, "farmland_");
        List<Boolean> all = new ArrayList<Boolean>();
        List<String[]> gs = groups(likedIndustries(r), all);
        List<CAPlan.Step> steps = new ArrayList<CAPlan.Step>();
        Set<String> seen = new HashSet<String>();
        for (int gi = 0; gi < gs.size(); gi++) {
            String[] g = gs.get(gi);
            if (all.get(gi)) {
                for (String ind : g) {
                    if (!possible(new String[]{ind}, have, deposit, farmland, water)) return false;
                    if (!seen.add(ind)) continue;
                    CAPlan.Step st = new CAPlan.Step(CAPlan.Kind.BUILD, ind, label(m, ind));
                    st.exact = true;
                    steps.add(st); p.exact.add(ind); p.keep.add(ind);   // TME checks this id itself
                }
                continue;
            }
            if (!possible(g, have, deposit, farmland, water)) return false;
            Set<String> known = new HashSet<String>();
            for (String id : g) if (CAPlan.spec(id) != null) known.add(id);
            String id = pick(g, have, water, known, CAPlan::leadsTo);
            if (id == null) return false;
            if (!seen.add(id)) continue;   // TME lists Heavy Industry and Orbital Works as the same group
            CAPlan.Step st = new CAPlan.Step(CAPlan.Kind.BUILD, id, label(m, id));
            st.exact = true;
            st.anyOf = new LinkedHashSet<String>(Arrays.asList(g));
            steps.add(st); p.exact.add(id);
            if (keeps(world, id)) p.keep.add(id);
        }
        p.steps.addAll(steps);
        return true;
    }

    /** Industry slots the plan's world industries plus the colony's other industries take (a group's building covers its step). */
    static int slotsNeeded(MarketAPI m, CAPlan.Plan p) {
        int slots = 0;
        for (String ind : p.exact) if (isIndustry(ind)) slots++;
        for (Industry i : m.getIndustries()) {
            if (i.isHidden() || !i.isIndustry()) continue;
            boolean covers = false;
            for (String ind : p.exact) if (i.getId().equals(ind) || CAPlan.leadsTo(i.getId(), ind) || CAPlan.leadsTo(ind, i.getId())) covers = true;
            for (CAPlan.Step st : p.steps) if (st.anyOf != null && st.anyOf.contains(i.getId())) covers = true;
            if (!covers) slots++;
        }
        return slots;
    }

    static float fit(String world, CAData.Profile p) {
        switch (p) {
            case WAR: return world.equals("tme_forge_world") ? 3f : world.equals("tme_fortress_world") ? 2f : 0f;
            case SECURITY: return world.equals("tme_fortress_world") ? 3f : world.equals("tme_death_world") ? 2f : 0f;
            case INCOME: return world.equals("tme_paradise_world") ? 3f : world.equals("tme_forge_world") ? 1f : 0f;
            case GROWTH: return world.equals("tme_hive_world") ? 3f : world.equals("tme_paradise_world") ? 2f : 0f;
            case SELF: return world.equals("tme_forge_world") ? 3f : world.equals("tme_waste_world") ? 2f : 0f;
            default: return world.equals("tme_paradise_world") ? 2f : world.equals("tme_forge_world") ? 2f : 1f;
        }
    }

    /** The best reachable special-world plan for this colony and profile, or null when none is reachable. */
    public static CAPlan.Plan plan(MarketAPI m, CAData.Profile prof) {
        if (!Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy") || rows().isEmpty()) return null;
        for (String w : WORLDS) if (m.hasCondition(w)) {
            CAPlan.Plan done = new CAPlan.Plan();
            done.name = name(w); done.world = w;
            done.steps.add(new CAPlan.Step(CAPlan.Kind.COND, w, name(w)));
            done.keep.addAll(formedKeep(w));   // 0.3.29.0: the buildings that get its +1 aren't upgraded
            return done;
        }
        CAPlan.Plan best = null; float bestScore = -1e30f;
        for (String w : WORLDS) {
            float f = fit(w, prof);
            if (f <= 0f || dropped(w)) continue;   // 0.3.18.0: dropped()
            if (CAPlan.worldDead(m, w)) continue;   // its own steps were out of reach: the plain plan for a year (0.2.38.0)
            Row r = rows().get(w);
            if (r == null) continue;
            Sim sim = new Sim();
            sim.conds = CATerraform.conditions(m);   // 0.3.9.2 (I C4): the one helper
            sim.inds = new HashSet<String>();
            for (Industry i : m.getIndustries()) sim.inds.add(i.getId());
            sim.gasGiant = CATerraform.gasGiant(m);
            Set<String> blocked = idsOf(r.hated);
            boolean ok = true;
            for (String b : blocked) if (sim.conds.contains(b)) ok = false;
            List<Boolean> all = new ArrayList<Boolean>();
            List<String> need = new ArrayList<String>();
            for (String[] g : groups(r.liked, all)) need.addAll(Arrays.asList(g));
            // try both orders for two requirements, since one project's side effects can undo another's
            Sim done = null;
            for (int order = 0; order < 2 && ok; order++) {
                Sim trial = sim.copy();
                List<String> seq = new ArrayList<String>(need);
                if (order == 1) Collections.reverse(seq);
                boolean good = true;
                for (String c : seq) { if (!resolve(trial, c, 0)) { good = false; break; } trial.keep.add(c); }
                if (good) for (String c : need) if (!trial.conds.contains(c)) good = false;
                if (good && (done == null || trial.cost < done.cost)) done = trial;
            }
            if (done == null) continue;
            CAPlan.Plan p = new CAPlan.Plan();
            p.name = name(w); p.world = w;
            // the world's industries must be possible once its projects are done (0.2.38.0), checked the way the game checks
            // them (0.3.29.0: TME's rules as loaded, one step per needOne group)
            if (!addIndustrySteps(m, w, r, done.conds, p)) continue;
            p.steps.addAll(done.steps);
            p.steps.add(new CAPlan.Step(CAPlan.Kind.BUILD, "tme_unification_center", industryName("tme_unification_center")));
            p.steps.add(new CAPlan.Step(CAPlan.Kind.COND, w, name(w)));
            if (CAPlan.stepDropped(m, p)) continue;   // a structure it needs is out of reach here: dropped with the world that found it (0.3.0.0 test fix)
            // the world's industries, plus the colony's other industries, must fit the slots it can have at its max size
            if (slotsNeeded(m, p) > slotsAt(m, Misc.getMaxMarketSize(m))) continue;
            int open = 0;
            for (CAPlan.Step st : p.steps) if (!st.done(m)) open++;
            // a year of what the end conditions change (hazard on upkeep and growth, deposits), so a world that keeps or adds
            // hazards loses to one that clears them: growth is what every colony needs
            float change = 0f;
            try { change = CAConditions.change(m, done.conds, prof, null); } catch (Throwable ignored) { }
            float score = f * 1000000f - done.cost - open * 200000f + 12f * change;
            if (score > bestScore) { bestScore = score; best = p; }
        }
        return best;
    }

    static boolean anyPrefix(Set<String> conds, String... prefixes) {
        for (String c : conds) for (String p : prefixes) if (c.startsWith(p)) return true;
        return false;
    }

    static boolean isIndustry(String id) {
        try {
            com.fs.starfarer.api.loading.IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(id);
            return sp != null && sp.hasTag(com.fs.starfarer.api.impl.campaign.ids.Industries.TAG_INDUSTRY);
        } catch (Throwable t) { return false; }
    }

    /** Industry slots the colony would have at this size: today's (with every bonus) moved along the game's size table. */
    static int slotsAt(MarketAPI m, int size) {
        return Misc.getMaxIndustries(m) - com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure.getMaxIndustries(m.getSize())
                + com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure.getMaxIndustries(size);
    }

    /** Structure that runs the project for this condition, or null. */
    public static String structureFor(String condId) {
        Row r = rows().get(condId);
        return r == null ? null : r.structure;
    }
}
