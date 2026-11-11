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
            JSONArray arr = Global.getSettings().getMergedSpreadsheetDataForMod("conditionId",
                    "data/campaign/tme_terraforming_options.csv", "terraformingmadeeasy");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String s = o.optString("structureId", ""), c = o.optString("conditionId", "");
                if (s.isEmpty() || c.isEmpty() || s.startsWith("#")) continue;
                Row r = new Row();
                r.structure = s; r.condition = c;
                r.cost = (float) o.optDouble("cost", 0); r.days = (float) o.optDouble("buildTime", 0);
                r.gasGiants = "TRUE".equalsIgnoreCase(o.optString("canChangeGasGiants", "false"));
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
        if (r.structure.equals("tme_stellar_manufactory")) {
            // Stellar Manufactory needs mirror/shade choices, so the player starts these projects
        }
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
            return done;
        }
        CAPlan.Plan best = null; float bestScore = -1e30f;
        for (String w : WORLDS) {
            float f = fit(w, prof);
            if (f <= 0f) continue;
            if (CAPlan.worldDead(m, w)) continue;   // its own steps were out of reach: the plain plan for a year (0.2.38.0)
            Row r = rows().get(w);
            if (r == null) continue;
            Sim sim = new Sim();
            sim.conds = new HashSet<String>();
            for (MarketConditionAPI c : m.getConditions()) sim.conds.add(c.getId());
            sim.inds = new HashSet<String>();
            for (Industry i : m.getIndustries()) sim.inds.add(i.getId());
            sim.gasGiant = m.getPrimaryEntity() instanceof PlanetAPI && ((PlanetAPI) m.getPrimaryEntity()).isGasGiant();
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
            List<Boolean> ia = new ArrayList<Boolean>();
            // the world's industries must be possible once its projects are done: Mining needs a deposit and Farming farmland
            // (a water world's Aquaculture counts), which Hive, Death and Waste worlds' projects never add (0.2.38.0)
            boolean possible = true;
            for (String[] g : groups(r.likedInd, new ArrayList<Boolean>())) for (String ind : g) {
                if (ind.equals("mining") && !m.hasIndustry("mining") && !anyPrefix(done.conds, "ore_", "rare_ore_", "volatiles_", "organics_")) possible = false;
                if (ind.equals("farming") && !m.hasIndustry("farming") && !m.hasIndustry("aquaculture") && !anyPrefix(done.conds, "farmland_")) possible = false;
            }
            if (!possible) continue;
            for (String[] g : groups(r.likedInd, ia)) for (String ind : g) {
                p.exact.add(ind);
                String label = industryName(ind) + (ind.equals("mining") && m.hasIndustry("IndEvo_SwitchableMining") ? " (vanilla Mining; Specialized Mining doesn't count)" :
                        ind.equals("fuelprod") && m.hasIndustry("ix_fuel_production") ? " (vanilla Fuel Production; Advanced doesn't count)" : "");
                CAPlan.Step st = new CAPlan.Step(CAPlan.Kind.BUILD, ind, label);
                st.exact = true;
                p.steps.add(st);
            }
            p.steps.addAll(done.steps);
            p.steps.add(new CAPlan.Step(CAPlan.Kind.BUILD, "tme_unification_center", industryName("tme_unification_center")));
            p.steps.add(new CAPlan.Step(CAPlan.Kind.COND, w, name(w)));
            // the world's industries, plus the colony's other industries, must fit the slots it can have at its max size
            int slots = 0;
            for (String ind : p.exact) if (isIndustry(ind)) slots++;
            for (Industry i : m.getIndustries()) {
                if (i.isHidden() || !i.isIndustry()) continue;
                boolean covers = false;
                for (String ind : p.exact) if (i.getId().equals(ind) || CAPlan.leadsTo(i.getId(), ind) || CAPlan.leadsTo(ind, i.getId())) covers = true;
                if (!covers) slots++;
            }
            if (slots > slotsAt(m, Misc.getMaxMarketSize(m))) continue;
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
