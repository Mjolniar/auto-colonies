package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Ashes of the Domain industry synergies (Vaults of Knowledge): building combinations on one colony that add output,
 * income or accessibility, some scaled by the colony's synergy efficiency (spaceport, waystation, maglev, Daedalus
 * Array, admin, development path). The planner adds what a build would start, end or strengthen to its score.
 * Requirements and effects are modelled from VoK 5.0.7's code, which applies most effects in full whatever the
 * efficiency (its tooltips scale them all). VoK is asked whether each synergy is researched here and what the
 * colony's efficiency is, by reflection; if that can't be read, synergies are left out of scoring.
 */
public class CASynergies {
    public static final String MOD = "aotd_vok";
    static final String PKG = "data.kaysaar.aotd.vok.campaign.econ.synergies.";
    static final float PATHER = 0.03f;   // stability effect per point of Pather interest

    static Object mgrInstance, calcEff, canShow, metReq, synName, srcEff, tree;
    static Object scripts, sources, srcBase;
    static Class<?> srcClass;
    static boolean linked = false, broken = false;
    static Map<String, List<Set<String>>> groups;

    static boolean link() {
        if (linked || broken) return linked;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> mgr = Class.forName(PKG + "models.IndustrySynergiesManager", true, cl);
            Class<?> syn = Class.forName(PKG + "models.BaseIndustrySynergy", true, cl);
            Class<?> src = Class.forName(PKG + "impl.sources.BaseIndustrySynergySource", true, cl);
            Class<?> misc = Class.forName(PKG + "IndustrySynergiesMisc", true, cl);
            mgrInstance = CARefl.method(mgr, "getInstance");
            calcEff = CARefl.method(mgr, "calculateEfficiency", MarketAPI.class);
            scripts = CARefl.field(mgr, "synergyScripts");
            sources = CARefl.field(mgr, "sourcesOfSynergy");
            canShow = CARefl.method(syn, "canShowSynergyInUI", MarketAPI.class);
            metReq = CARefl.method(syn, "doesSynergyMetReq", MarketAPI.class);
            synName = CARefl.method(syn, "getSynergyName");
            srcClass = src;
            srcBase = CARefl.field(src, "baseEfficiency");
            srcEff = CARefl.method(src, "calculateEfficiencyFromIndustry", Industry.class, boolean.class);
            tree = CARefl.method(misc, "getIdsOfTreeFromIndustry", String.class);
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Vaults of Knowledge's industry synergies; they're left out of scoring", t);
        }
        return linked;
    }

    // ---------------- requirements (VoK 5.0.7) ----------------

    @SuppressWarnings("unchecked")
    static Set<String> tree(String root) {
        Set<String> out = new LinkedHashSet<String>();
        out.add(root);
        try { out.addAll((Collection<String>) CARefl.invoke(tree, null, root)); } catch (Throwable ignored) { }
        return out;
    }

    static Set<String> one(String... ids) { return new LinkedHashSet<String>(Arrays.asList(ids)); }

    static Set<String> tag(String tag) {
        Set<String> out = new LinkedHashSet<String>();
        for (IndustrySpecAPI s : Global.getSettings().getAllIndustrySpecs()) if (s.hasTag(tag)) out.add(s.getId());
        return out;
    }

    static Set<String> minus(Set<String> a, Set<String> b) { Set<String> out = new LinkedHashSet<String>(a); out.removeAll(b); return out; }

    static Set<String> union(Set<String> a, Set<String> b) { Set<String> out = new LinkedHashSet<String>(a); out.addAll(b); return out; }

    static void put(String id, Set<String> a, Set<String> b) { put(id, a, b, null); }

    static void put(String id, Set<String> a, Set<String> b, Set<String> c) {
        List<Set<String>> g = new ArrayList<Set<String>>();
        g.add(a); g.add(b);
        if (c != null) g.add(c);
        groups.put(id, g);
    }

    static String join(List<String> l) { return join(l, ", "); }

    static String join(List<String> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append(sep); sb.append(s); }
        return sb.toString();
    }

    /** One building from each group is needed. Ids as VoK registers them. */
    static Map<String, List<Set<String>>> groups() {
        if (groups != null) return groups;
        groups = new LinkedHashMap<String, List<Set<String>>>();
        put("agro_tourism", one("aotd_resort_center"), one("aotd_artisanal_farming"));
        put("aotd_mining_feed", tree("refining"), minus(tree("mining"), one("aotd_plasma_harvester")));
        put("aotd_refining_feed", tree("refining"), tree("heavyindustry"));
        put("cabbage_comm_materials", tree("refining"), minus(tree("heavyindustry"), tree("aotd_civilian_heavy_prod")));
        put("ildrenium_black_site_fools", one("techmining"), one("aotd_blacksite"));
        put("darkhellthepro_syndicate_line", one("aotd_underworld"), one("cryosanctum", "aotd_druglight", "lightindustry"));
        put("aotd_lockheed_martin", one("triheavy"), one("aotd_blacksite"));
        put("cabbage_gas_station", tree("waystation"), tree("fuelprod"));
        put("touchOfVanilla_deep_sea_scan", tree("aquaculture"), tree("techmining"));
        put("tschudy_emergency_measures", tree("waystation"), tree("heavyindustry"));
        put("omeganavie_fishing_resort", one("aotd_resort_center"), tree("aquaculture"));
        put("tata_mono_bio_synthesis", one("aotd_druglight"), one("aotd_subsidised_farming"));
        put("seventhslayer_defensive_network", tag("starfortress"), one("heavybatteries"), one("highcommand", "aotd_hexagon"));
        put("mstachife_rock_and_stone", one("techmining"), one("mining", "aotd_mining_megaplex"));
        put("aotd_rapid_transportation", union(tree("spaceport"), tree("aotd_daedalus_array")), tree("waystation"));
        put("r3dstylum_frl", union(tree("megaport"), tree("aotd_daedalus_array")), tree("aotd_terminus"), tree("aotd_research_facility"));
        put("ildrenium_aep", tree("fuelprod"), one("aotd_exomatter_processing"));
        put("aotd_volatile_line", tree("aotd_plasma_harvester"), tree("fuelprod"));
        put("somerandomsmuck_ditd", tree("aotd_research_facility"), tree("heavyindustry"), tree("aquaculture"));
        return groups;
    }

    /** Conditions besides buildings that the colony meets now. */
    static boolean extra(String id, MarketAPI m) {
        if (id.equals("darkhellthepro_syndicate_line")) return m.isFreePort();
        if (id.equals("cabbage_gas_station")) return !m.hasCondition("extreme_weather");
        if (id.equals("touchOfVanilla_deep_sea_scan")) return ruins(m) > 0;
        if (id.equals("tschudy_emergency_measures")) return m.isUseStockpilesForShortages();
        return true;
    }

    static boolean met(String id, Set<String> have) {
        for (Set<String> g : groups().get(id)) if (Collections.disjoint(g, have)) return false;
        return true;
    }

    static int ruins(MarketAPI m) {
        return m.hasCondition("ruins_vast") ? 4 : m.hasCondition("ruins_extensive") ? 3 : m.hasCondition("ruins_widespread") ? 2 : m.hasCondition("ruins_scattered") ? 1 : 0;
    }

    // ---------------- effects ----------------

    static Industry first(MarketAPI m, Set<String> ids) {
        for (String id : ids) if (m.hasIndustry(id)) return m.getIndustry(id);
        return null;
    }

    /** A stat's value without the multipliers synergies put on it. */
    static float without(MutableStat s, String... ids) {
        float v = s.getModifiedValue();
        for (String id : ids) { MutableStat.StatMod mod = s.getMultStatMod(id); if (mod != null && mod.getValue() > 0f) v /= mod.getValue(); }
        return v;
    }

    static void supply(CAEngine.Pick p, String com, int units, Map<String, Integer> deficit) {
        if (units > 0) CAEngine.supply(p, com, units, deficit);
        else if (units < 0) p.fx[0] -= Global.getSettings().getCommoditySpec(com).getBasePrice() * CAEngine.unitValue * -units;
    }

    /** What one active synergy is worth, in the engine's units; p.lines gets a short description. */
    static void effects(String id, MarketAPI m, Set<String> have, float eff, CAEngine.Pick p, Map<String, Integer> deficit) {
        if (id.equals("agro_tourism") || id.equals("omeganavie_fishing_resort")) {
            float share = id.equals("agro_tourism") ? 0.3f : 0.1f;
            Industry resort = m.hasIndustry("aotd_resort_center") ? m.getIndustry("aotd_resort_center") : null;
            if (resort != null) p.fx[0] += share * eff * without(resort.getIncome(), "agro_tourism", "FishingResort");
            p.lines.add("Resort Center income +" + Math.round(share * eff * 100f) + "%");
        } else if (id.equals("aotd_mining_feed")) {
            supply(p, "metals", 2, deficit); supply(p, "rare_metals", 2, deficit);
            p.lines.add("Refining +2 output");
        } else if (id.equals("aotd_refining_feed")) {
            for (String c : new String[]{"heavy_machinery", "supplies", "hand_weapons", "ships"}) supply(p, c, 1, deficit);
            p.fx[5] += 0.15f * eff;
            p.lines.add("Heavy Industry +1 output, ship quality +" + Math.round(15f * eff) + "%");
        } else if (id.equals("cabbage_comm_materials")) {
            supply(p, "supplies", 2, deficit); supply(p, "hand_weapons", 2, deficit);
            supply(p, "metals", -1, deficit); supply(p, "rare_metals", -1, deficit);
            p.lines.add("Heavy Industry +2 supplies and weapons, Refining -1 output");
        } else if (id.equals("ildrenium_black_site_fools")) {
            Industry bs = m.hasIndustry("aotd_blacksite") ? m.getIndustry("aotd_blacksite") : null;
            if (bs != null) p.fx[0] += 0.1f * eff * without(bs.getUpkeep(), "plausible_deniability");
            p.fx[1] += 5f * eff * PATHER;
            p.lines.add("Black Site upkeep -" + Math.round(10f * eff) + "%, less Pather interest");
        } else if (id.equals("darkhellthepro_syndicate_line")) {
            if (have.contains("cryosanctum")) supply(p, "organs", 3, deficit);
            float pct = have.contains("aotd_druglight") ? 0.6f * eff : have.contains("lightindustry") ? 0.1f * eff : 0f;
            p.fx[0] += pct * Math.max(0f, m.getGrossIncome());
            p.lines.add((have.contains("cryosanctum") ? "+3 organs; " : "") + "colony income +" + Math.round(pct * 100f) + "%");
        } else if (id.equals("aotd_lockheed_martin")) {
            p.lines.add("Black site projects up to 10% faster");
        } else if (id.equals("cabbage_gas_station")) {
            p.fx[2] += 0.1f * eff;
            p.lines.add("Accessibility +" + Math.round(10f * eff) + "%, Waystation needs 1 less fuel");
        } else if (id.equals("touchOfVanilla_deep_sea_scan")) {
            p.fx[0] += 400f * ruins(m);
            p.lines.add("Tech-mining depletes the ruins 30% slower");
        } else if (id.equals("tschudy_emergency_measures")) {
            p.fx[3] += 0.05f;
            p.lines.add("Disrupted buildings recover faster from stockpiled supplies and machinery");
        } else if (id.equals("tata_mono_bio_synthesis")) {
            supply(p, "food", 2, deficit); CAEngine.demand(p, "drugs", 1, deficit);
            p.lines.add("Subsidised Farming +2 food, needs 1 drugs");
        } else if (id.equals("seventhslayer_defensive_network")) {
            p.fx[3] += 0.5f; p.fx[4] += 0.2f;
            p.lines.add("Ground defense x1.5, fleet size +20%");
        } else if (id.equals("mstachife_rock_and_stone")) {
            int chances = Math.round((have.contains("aotd_mining_megaplex") ? 3 : 1) * eff);
            if (chances > 0) p.fx[0] += 1000f * ruins(m) * chances;
            p.lines.add(chances > 0 ? "Tech-mining finds more rare items" : "Tech-mining finds more rare items (needs more efficiency here)");
        } else if (id.equals("aotd_rapid_transportation")) {
            p.fx[2] += 0.1f * eff;
            p.lines.add("Accessibility +" + Math.round(10f * eff) + "%");
        } else if (id.equals("r3dstylum_frl")) {
            p.fx[2] += 0.05f * eff;
            Industry rf = first(m, tree("aotd_research_facility"));
            if (rf != null) p.fx[0] += 0.1f * eff * without(rf.getUpkeep(), "FacilitatedResearchLogistics");
            p.lines.add("Accessibility +" + Math.round(5f * eff) + "%, research 5% faster, research facility upkeep lower");
        } else if (id.equals("ildrenium_aep")) {
            supply(p, "fuel", 2, deficit);
            p.fx[1] -= 10f * PATHER;
            p.lines.add("Fuel Production +2, much more Pather interest");
        } else if (id.equals("aotd_volatile_line")) {
            supply(p, "fuel", Math.round(2f * eff), deficit);
            p.lines.add("Fuel Production +" + Math.round(2f * eff));
        } else if (id.equals("somerandomsmuck_ditd")) {
            p.fx[2] -= 0.1f;
            p.fx[1] += 8f * PATHER;
            p.lines.add("Accessibility -10%, less Pather interest");
        }
    }

    // ---------------- planning ----------------

    /** One colony's synergy state, computed once per evaluation. */
    public static class Ctx {
        MarketAPI m;
        Map<String, Integer> deficit;
        Set<String> have = new HashSet<String>();
        float eff;
        Map<String, Object> open = new LinkedHashMap<String, Object>();   // researched here, other conditions met
        Map<String, String> names = new HashMap<String, String>();
        Map<String, CAEngine.Pick> now = new HashMap<String, CAEngine.Pick>();
        int locked;
    }

    /** Null when Vaults of Knowledge can't be read. */
    @SuppressWarnings("unchecked")
    public static Ctx context(MarketAPI m, Map<String, Integer> deficit) {
        if (!link()) return null;
        try {
            Object mgr = CARefl.invoke(mgrInstance, null);
            Ctx c = new Ctx();
            c.m = m; c.deficit = deficit;
            for (Industry ind : m.getIndustries()) c.have.add(ind.getId());
            c.eff = Math.max(0f, ((Number) CARefl.invoke(calcEff, mgr, m)).floatValue());
            Map<String, Object> all = (Map<String, Object>) CARefl.get(scripts, mgr);
            if (all == null) return c;
            for (Map.Entry<String, Object> e : all.entrySet()) {
                if (!groups().containsKey(e.getKey())) continue;   // a synergy this version doesn't model
                c.names.put(e.getKey(), String.valueOf(CARefl.invoke(synName, e.getValue())));
                if (!Boolean.TRUE.equals(CARefl.invoke(canShow, e.getValue(), m))) { c.locked++; continue; }
                if (!extra(e.getKey(), m)) continue;
                c.open.put(e.getKey(), e.getValue());
                if (met(e.getKey(), c.have)) { CAEngine.Pick p = new CAEngine.Pick(); effects(e.getKey(), m, c.have, c.eff, p, deficit); c.now.put(e.getKey(), p); }
            }
            return c;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "industry synergy check failed at " + m.getName(), t);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    static Object source(String industryId) throws Exception {
        Map<String, Object> s = (Map<String, Object>) CARefl.get(sources, CARefl.invoke(mgrInstance, null));
        return s == null ? null : s.get(industryId);
    }

    /** Synergy efficiency once p is built: the replaced building's share out, the new one's base share in. */
    static float effAfter(Ctx c, CAEngine.Pick p) {
        float e = c.eff;
        try {
            if (p.upgrade && p.existing != null) {
                Object s = source(p.existing.getId());
                if (s != null && srcClass.isInstance(s)) e -= ((Number) CARefl.invoke(srcEff, s, p.existing, true)).floatValue();
            }
            Object s = source(p.id);
            if (s != null && srcClass.isInstance(s)) e += CARefl.getFloat(srcBase, s);
        } catch (Throwable ignored) { }
        return Math.max(0f, e);
    }

    /** Adds what building p does to this colony's synergies to its effects. */
    public static void score(Ctx c, CAEngine.Pick p) {
        if (c == null) return;
        Set<String> after = new HashSet<String>(c.have);
        if (p.upgrade && p.existing != null) after.remove(p.existing.getId());
        after.add(p.id);
        float eff = effAfter(c, p);
        List<String> strengthened = new ArrayList<String>();
        for (String id : c.open.keySet()) {
            CAEngine.Pick before = c.now.get(id);
            boolean on = met(id, after);
            if (before == null && !on) continue;
            CAEngine.Pick next = new CAEngine.Pick();
            if (on) effects(id, c.m, after, eff, next, c.deficit);
            float change = 0f;
            for (int i = 0; i < 7; i++) {
                float d = next.fx[i] - (before == null ? 0f : before.fx[i]);
                p.fx[i] += d;
                change += Math.abs(d);
            }
            String name = c.names.get(id);
            if (before == null) p.lines.add("Starts the " + name + " synergy: " + next.lines.get(0));
            else if (!on) p.lines.add("Ends the " + name + " synergy");
            else if (change > 0.001f) strengthened.add(name);
        }
        if (!strengthened.isEmpty() && eff > c.eff) {
            p.lines.add("Synergy efficiency " + Math.round(c.eff * 100f) + "% -> " + Math.round(eff * 100f) + "%: strengthens " + join(strengthened));
        }
    }

    // ---------------- colony panel ----------------

    public static void render(TooltipMakerAPI t, MarketAPI m) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        Ctx c = context(m, CAEngine.empireDeficits());
        if (c == null) { t.addPara("Vaults of Knowledge's synergies can't be read; see starsector.log.", gray, 6f); return; }
        List<String> active = new ArrayList<String>(), near = new ArrayList<String>();
        try {
            for (Map.Entry<String, Object> e : c.open.entrySet()) {
                if (Boolean.TRUE.equals(CARefl.invoke(metReq, e.getValue(), m))) { active.add(c.names.get(e.getKey())); continue; }
                Set<String> missing = null;
                int unmet = 0;
                for (Set<String> g : groups().get(e.getKey())) if (Collections.disjoint(g, c.have)) { unmet++; missing = g; }
                if (unmet != 1) continue;
                List<String> opts = new ArrayList<String>();
                for (String id : missing) {
                    try { opts.add(Global.getSettings().getIndustrySpec(id).getName()); } catch (Throwable ignored) { }
                    if (opts.size() >= 2) break;
                }
                if (!opts.isEmpty()) near.add(c.names.get(e.getKey()) + " (needs " + join(opts, " or ") + ")");
            }
        } catch (Throwable ex) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "industry synergy panel failed at " + m.getName(), ex);
        }
        t.addPara("Synergy efficiency: %s. Spaceport, Waystation, Maglev and Daedalus Array raise it; some synergies grow with it.", 6f, hl, Math.round(c.eff * 100f) + "%");
        t.addPara("Active: %s", 4f, hl, active.isEmpty() ? "none" : join(active));
        if (!near.isEmpty()) t.addPara("One building away: %s", 4f, hl, join(near, "; "));
        if (c.locked > 0) t.addPara(c.locked + " more need research first.", gray, 4f);
        t.addPara("Builds that start, end or strengthen a synergy include it in their estimated value.", gray, 4f);
    }
}
