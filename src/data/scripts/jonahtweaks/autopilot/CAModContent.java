package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.*;

/**
 * 0.3.35.0: mod content coverage (plans/2026-11-27-ac-mod-coverage), reached by ids only; no class of these mods is referenced.
 * Numbers are the mods' own, read from their code:
 * - Left to you: faction buildings the game never lets you build (Nightcross, Emergent Threats IX Revival, Arma Armatura,
 *   Secrets of the Frontier, United Aurora Federation, Luddic Enhancement, Navarchy of Tania, Interstellar Imperium), so a
 *   colony you take keeps them, plus Crab Shack's Wormhole Nexus (ship storage) and Bionic Alteration's hidden unit.
 * - Conditions that change a new building's lines (the probe colony copies vanilla conditions only), applied after
 *   CAKadur.adjust: Domain Phase Lab's Production Union (+1 on every line of a heavyindustry building) and Unmotivated
 *   Cadets (no marines from the Military Base or High Command), IX Revival's Algae Blooms (Light Industry +2 drugs,
 *   Aquaculture -2 food), Yunru's Worlds' HEPHAESTUS (forges need marines at the colony's size), Theory of Toolbox's
 *   Sindrian refinery mastery (fuel x1.8).
 * - New colonies: IX Revival's Killsats (decivilizes any colony above size 3).
 * - Items: Domain Phase Lab's mercenary crisis counts a heavyindustry building holding an item; UAF's Access Router adds 35%
 *   to Tech-Mining finds (TechMining reads tech_mining_mult).
 * Checked offline by JTModContentCheck.
 */
public final class CAModContent {
    private CAModContent() { }

    static final String DPL = "dpl_phase_lab", PROD_UNION = "dpl_prod_union", CADETS = "dpl_unmotivated_cadets";
    static final String ALGAE = "ix_algae", KILLSATS = "ix_killsats";
    static final String HEPHAESTUS = "yunru_hephaestus", HEPHAESTUS_H = "yunru_horizon_h", SINDRIA = "aotd_sindira_fuel_booster";
    static final String ACCESS_ROUTER = "uaf_access_router";

    /** dpl_ProdUnion.PROD_BONUS: every supply and demand line of a heavyindustry building. */
    static final int PROD_UNION_BONUS = 1;
    /** IXAlgae: DRUG_BONUS on Light Industry, FOOD_PENALTY on Aquaculture. */
    static final int ALGAE_DRUGS = 2, ALGAE_FOOD = -2;
    /** SindrianRefineryMastery: fuel supply x1.8. */
    static final float SINDRIA_FUEL = 1.8f;
    /** IXKillsats.POPULATION_MAXIMUM: a colony above this size is decivilized. */
    static final int KILLSATS_MAX = 3;
    /** UAF's Access Router: tech_mining_mult x1.35. */
    static final float ROUTER_SHARE = 0.35f;

    // ---------------- left to you ----------------

    static Map<String, String> left() {
        Map<String, String> l = new LinkedHashMap<String, String>();
        l.put("ii_interstellarbazaar", "the Imperium capital's Starship Bazaar; it can't be built and its market isn't modeled");
        l.put("battlestation_aria", "a Nightcross faction station that can't be built");
        l.put("na_armory_hq", "a Nightcross faction base that can't be built; it spawns its own patrols");
        l.put("na_aria_hq", "a Nightcross faction base that can't be built; it spawns its own patrols");
        l.put("cs_wormholeNexus", "it stores ships for combat and needs a scanned Gate in the system. No colony effect");
        l.put("luddenhance_refit", "it exists only at Alexandretta and can't be built");
        l.put("tania_fuel_siphon", "it works only for the Navarchy of Tania and can't be built");
        l.put("ba_mental_unit", "Bionic Alteration adds it for an administrator with bionics. Its upkeep is the treatment fee and it comes back if removed");
        l.put("ix_embassy_player", "one per game, unlocked by IX, and it changes IX and Trinity Worlds relations every month");
        for (String id : new String[]{"ix_panopticon", "ix_panopticon_node"}) l.put(id, "the Vertex Station's defense; it can't be built");
        for (String id : new String[]{"ix_fleet_command", "ix_marzanna_base"}) l.put(id, "an IX or Marzanna military base that can't be built");
        for (String id : new String[]{"tw_cloudburst_academy", "tw_fleet_embassy", "tw_orbital_farms"}) l.put(id, "Trinity Worlds only; it can't be built");
        for (String id : new String[]{"ix_kresnik_mechanism", "tw_soliton_siegebreaker"}) l.put(id, "a blockade defense at Vertex or Culmen that can't be built");
        l.put("sotf_centralprocessing", "the population of a Dustkeeper station; it can't be built");
        for (String id : new String[]{"armaa_armaarmatura", "armaa_mrc", "armaa_orbitalFabricator"}) l.put(id, "a New Meshan or MRC structure that can't be built");
        l.put("uaf_bakery", "New Auroria only; it can't be built");
        l.put("uaf_bakery_branch", "it needs a UAF commission and Nia's permit, and its lines lose money at every size. Its monthly delivery isn't modeled");
        for (String id : new String[]{"uaf_novemberRoyalNavy", "uaf_automataExpeditionaryDivision"}) l.put(id, "it works only on a UAF market and can't be built");
        l.put("uaf_tourism", "Favonius only; it can't be built");
        for (String id : new String[]{"IndEvo_Artillery_mortar", "IndEvo_Artillery_missile", "IndEvo_Artillery_railgun"})
            l.put(id, "it restores a Domain artillery station. Its worth is in fleet combat");
        l.put("niko_MPC_coronaResist", "it shields the system's fleets from corona damage and does nothing for the colony");
        l.put("niko_MPC_constructionAcceleratorIndustry", "it only speeds up other construction and needs six goods");
        return l;
    }

    // ---------------- new colonies ----------------

    /** Why Autopilot founds no colony here, or null. */
    static String noColony(MarketAPI m) {
        if (m == null) return null;
        if (m.hasCondition(KILLSATS)) return "Killsats: a colony above size " + KILLSATS_MAX + " is decivilized.";
        // What We Left Behind
        if (m.hasCondition("MPC_fracturedWorld")) return "Fractured World: accessibility -100%, hazard +300%.";
        if (m.hasCondition("niko_MPC_magnetarCondition")) return "Magnetar: accessibility -75%, hazard +150%.";
        if (m.hasCondition("niko_MPC_derelictEscort")) return "Derelict Escort: accessibility -40%, and its fleets bring a pirate event.";
        if (m.hasCondition("niko_MPC_overgrownNanoforgeCondition")) return "Overgrown Nanoforge: its growths spread random effects that have to be culled by hand.";
        return null;
    }

    // ---------------- conditions on a new building's lines ----------------

    /** Pure: Production Union's change to one line of `units` (0 for a line the building doesn't make). */
    static int unionDelta(int units, boolean union, boolean heavy) { return units > 0 && union && heavy ? PROD_UNION_BONUS : 0; }

    /** Pure: the Sindrian refinery's extra fuel on a line of `units`. */
    static int sindriaExtra(int units) { return units <= 0 ? 0 : Math.round(units * SINDRIA_FUEL) - units; }

    /** Pure: the marines a Military Base or High Command makes under Unmotivated Cadets (planning: an administrator with Industrial Planning). */
    static int cadetMarines(int units, boolean planning) { return Math.max(0, units - units - (planning ? 1 : 0)); }

    static boolean tagged(Industry inst, String id, String tag) {
        try { return inst != null ? inst.getSpec().hasTag(tag) : Global.getSettings().getIndustrySpec(id).hasTag(tag); }
        catch (Throwable t) { return false; }
    }

    /**
     * A condition's change to one line of a new building (Random Assortment of Things, Unknown Skies, JaydeePiracy; read from
     * their apply code). target: "id:" a building id, "tag:" a spec tag, "any" every building. com null: every line it makes.
     * add: a line the building doesn't make yet (supply), else a change to a line it makes (only when it makes it).
     */
    static final class LineRule {
        final String cond, target, com, unless; final int delta; final boolean add;
        LineRule(String cond, String target, String com, int delta, boolean add, String unless) {
            this.cond = cond; this.target = target; this.com = com; this.delta = delta; this.add = add; this.unless = unless;
        }
        boolean hits(String id, Industry inst) {
            if ("any".equals(target)) return true;
            if (target.startsWith("id:")) return target.substring(3).equals(id);
            return target.startsWith("tag:") && tagged(inst, id, target.substring(4));
        }
    }

    static final List<LineRule> RULES = new ArrayList<LineRule>();
    static void r(String cond, String target, String com, int delta) { RULES.add(new LineRule(cond, target, com, delta, false, null)); }
    static {
        // Random Assortment of Things
        for (String c : new String[]{"heavy_machinery", "supplies", "hand_weapons", "ships"}) r("rat_ancient_industries", "tag:heavyindustry", c, 1);
        r("rat_ancient_industries", "id:lightindustry", "domestic_goods", 1); r("rat_ancient_industries", "id:lightindustry", "luxury_goods", 1);
        r("rat_ancient_fuel_hub", "id:mining", "volatiles", 1); r("rat_ancient_fuel_hub", "id:fuelprod", "fuel", 1);
        r("rat_bionic_plantlife", "id:farming", "food", 2);
        // Unknown Skies
        r("US_virus", "id:heavyindustry", "hand_weapons", 3); r("US_virus", "id:orbitalworks", "hand_weapons", 3);
        r("US_crystals", "id:lightindustry", "luxury_goods", 1);
        r("US_mind", "any", null, -1);
        r("US_sakura", "id:farming", "food", -2);
        // JaydeePiracy
        for (String f : new String[]{"id:farming", "id:aquaculture"}) {
            RULES.add(new LineRule("jdp_makeshiftSolarArray", f, "food", 2, false, "solar_array"));
            r("jdp_waterIceImport", f, "food", 1);
        }
        RULES.add(new LineRule("jdp_volturnianLobsterPens", "id:aquaculture", "lobster", 1, true, null));
    }

    /** Pure: a rule's change to a line of `units` (an existing-line rule leaves a line the building doesn't make alone; never below 0). */
    static int ruleDelta(int units, int delta, boolean add) {
        if (add) return delta;
        if (units <= 0) return 0;
        return Math.max(-units, delta);
    }

    /** Theory of Toolbox's farm output factor at this colony: 2^(size - 1) (AoTDToolboxFoodProd), or Toolbox Economy Curves' farmFood. 1 without ToT. */
    static float farmFactor(MarketAPI m) {
        if (m == null || !m.hasCondition(TOOLBOX_FOOD)) return 1f;
        int size = Math.max(1, m.getSize());
        if (CAOddisz.on(CAOddisz.CURVES_MOD)) {
            try {
                Class<?> st = Class.forName("toolboxeconomycurves.Settings", true, Global.getSettings().getScriptClassLoader());
                Object curve = CARefl.invoke(CARefl.method(st, "curve"), null);
                double f = ((Number) CARefl.invoke(CARefl.method(curve.getClass(), "farmFood", int.class), curve, size)).doubleValue();
                if (f >= 1 && f < 10000) return (float) f;
            } catch (Throwable ignored) { }
        }
        return toolboxFactor(size);
    }

    static final String TOOLBOX_FOOD = "aotd_toolbox_food_corrector";

    /** Pure: Theory of Toolbox's own factor, 2^(size - 1). */
    static float toolboxFactor(int size) { return (float) Math.pow(2.0, Math.max(1, size) - 1); }

    /** Pure: the extra food a farm line of `food` makes with the factor (ToT rounds the total). */
    static int farmExtra(int food, float factor) { return food <= 0 || factor <= 1f ? 0 : Math.round(food * factor) - food; }

    static boolean farm(Industry inst, String id) {
        return id.equals("farming") || id.equals("aquaculture") || tagged(inst, id, "farming") || tagged(inst, id, "aquaculture");
    }

    /** A new building's lines at this colony, adjusted for the conditions above (CAEngine, after CAKadur.adjust). */
    public static void adjust(MarketAPI m, CAEngine.Pick p, Industry inst, Map<String, Integer> deficit) {
        if (m == null || p == null || p.id == null) return;
        try {
            String id = p.id;
            for (LineRule r : RULES) {
                if (!m.hasCondition(r.cond) || (r.unless != null && m.hasCondition(r.unless)) || !r.hits(id, inst)) continue;
                String why = CAConditions.name(r.cond);
                if (r.com == null) {
                    for (Map.Entry<String, Integer> e : new ArrayList<Map.Entry<String, Integer>>(p.supplied.entrySet())) {
                        int d = ruleDelta(e.getValue() == null ? 0 : e.getValue(), r.delta, false);
                        if (d != 0) CAEngine.supplyChange(p, e.getKey(), d, deficit, why);
                    }
                } else if (r.add) {
                    if (!p.supplied.containsKey(r.com)) CAEngine.supply(p, r.com, r.delta, deficit);
                } else {
                    Integer u = p.supplied.get(r.com);
                    int d = ruleDelta(u == null ? 0 : u, r.delta, false);
                    if (d != 0) CAEngine.supplyChange(p, r.com, d, deficit, why);
                }
            }
            if (m.hasCondition(PROD_UNION) && tagged(inst, id, "heavyindustry")) {
                for (Map.Entry<String, Integer> e : new ArrayList<Map.Entry<String, Integer>>(p.supplied.entrySet())) {
                    int d = unionDelta(e.getValue() == null ? 0 : e.getValue(), true, true);
                    if (d != 0) CAEngine.supplyChange(p, e.getKey(), d, deficit, "the Production Union");
                }
                CAEngine.demand(p, "metals", PROD_UNION_BONUS, deficit);
                CAEngine.demand(p, "rare_metals", PROD_UNION_BONUS, deficit);
            }
            if (m.hasCondition(CADETS) && (id.equals("militarybase") || id.equals("highcommand"))) {
                Integer mar = p.supplied.get("marines");
                if (mar != null && mar > 0) CAEngine.supplyChange(p, "marines", cadetMarines(mar, false) - mar, deficit, "Unmotivated Cadets");
            }
            if (m.hasCondition(ALGAE)) {
                if (id.equals("lightindustry")) CAEngine.supply(p, "drugs", ALGAE_DRUGS, deficit);
                else if (id.equals("aquaculture") && p.supplied.containsKey("food")) CAEngine.supplyChange(p, "food", ALGAE_FOOD, deficit, "Algae Blooms");
            }
            if ((m.hasCondition(HEPHAESTUS) || m.hasCondition(HEPHAESTUS_H)) && (id.equals("heavyindustry") || id.equals("orbitalworks"))) {
                CAEngine.demand(p, "marines", m.getSize(), deficit);
                p.lines.add("HEPHAESTUS: the forge needs " + m.getSize() + " marines");
            }
            if (m.hasCondition(SINDRIA)) {
                Integer fuel = p.supplied.get("fuel");
                int extra = sindriaExtra(fuel == null ? 0 : fuel);
                if (extra > 0) CAEngine.supplyChange(p, "fuel", extra, deficit, "Sindrian refinery mastery");
            }
            // last: Theory of Toolbox multiplies a farm's whole food line, the conditions' changes included
            if (farm(inst, id)) {
                float f = farmFactor(m);
                Integer food = p.supplied.get("food");
                int extra = farmExtra(food == null ? 0 : food, f);
                if (extra > 0) {
                    CAEngine.supplyChange(p, "food", extra, deficit, "Theory of Toolbox's farm output (x" + Math.round(f) + ")");
                    int base = id.equals("aquaculture") ? m.getSize() : Math.max(0, m.getSize() - 3);
                    CAEngine.demand(p, "heavy_machinery", Math.round(base * (Math.max(1f, Math.round(0.3f * f)) - 1f)), deficit);
                }
            }
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "mod condition effects failed at " + m.getName(), t); }
    }

    // ---------------- items ----------------

    /** Pure: Domain Phase Lab's monthly crisis points for a colony of this size with an item in a heavyindustry building. */
    static int dplPoints(int size) { return size >= 7 ? 24 : size == 6 ? 16 : size == 5 ? 8 : size == 4 ? 4 : 0; }

    /** The Phase Lab's mercenary crisis is live: its faction and Security market exist and it hasn't been dealt with. */
    static boolean dplCrisis() {
        try {
            if (!Global.getSettings().getModManager().isModEnabled(DPL) || Global.getSector() == null) return false;
            if (Global.getSector().getFaction(DPL) == null || Global.getSector().getEconomy().getMarket("dpl_security") == null) return false;
            com.fs.starfarer.api.campaign.rules.MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            return !mem.getBoolean("$dpl_DealtWithBadGuy") && !mem.getBoolean("$dpl_defeatedDPLAttack");
        } catch (Throwable t) { return false; }
    }

    /**
     * Credits a month an item placed in `ind` costs in Phase Lab crisis progress (dpl_ActivityCause): only a heavyindustry
     * building, and only the colony's first such building with an item (the crisis counts colonies, not items).
     */
    static float itemCrisisCost(MarketAPI m, Industry ind) {
        if (m == null || ind == null || !ind.getSpec().hasTag("heavyindustry") || !dplCrisis()) return 0f;
        for (Industry o : m.getIndustries())
            if (o != ind && o.getSpecialItem() != null && o.getSpec().hasTag("heavyindustry")) return 0f;
        return dplPoints(m.getSize()) * CACrisis.pointCost();
    }

    /** Roider Union's Historical Roider Charter: RoiderItems.CHARTER_FLEET_SIZE_BONUS. */
    static final float CHARTER_FLEET = 0.1f;

    /** Pure (0.3.39.0): units the Charter's longer reach adds to one mined line. */
    static int reachGain(int now, int more) { return Math.max(0, more - now); }

    /**
     * 0.3.39.0: what a Historical Roider Charter adds in `ind` (Dives or Union HQ) beyond its +1 per mined line: the deposits
     * its +6 light-years reach (only while Roider's mining area is read, CAMods.reach), at the flat unit value, and +0.1 fleet
     * size at the goal's fleet weight. Credits a month, on CAItems' placement scale.
     */
    static float charterExtra(MarketAPI m, Industry ind, CAData.Profile prof) {
        if (m == null || ind == null) return 0f;
        boolean hq = "roider_union_hq".equals(ind.getId());
        if (!hq && !"roider_dives".equals(ind.getId())) return 0f;
        float v = CHARTER_FLEET * CAEngine.goalWeights(prof)[4];
        try {
            float base = hq ? CAMods.hqRange() : CAMods.divesRange();
            float now = CAMods.reach(base), more = CAMods.reach(base + CAMods.charterRange());
            if (now >= 0f && more >= 0f) {
                int x = CAMods.extra(m) + (CAMods.charter(m) ? 0 : 1);
                for (String com : CAMods.MINED) {
                    int g = reachGain(CAMods.mined(m, com, now, x), CAMods.mined(m, com, more, x));
                    if (g > 0) v += g * Global.getSettings().getCommoditySpec(com).getBasePrice() * CAEngine.unitValue;
                }
            }
        } catch (Throwable ignored) { }
        return v;
    }

    /** UAF's Access Router in Tech-Mining: 35% more finds, in CAItems.itemScore points. 0 anywhere else. */
    static float routerScore(MarketAPI m, Industry ind) {
        if (m == null || ind == null || !"techmining".equals(ind.getId())) return 0f;
        float credits = ROUTER_SHARE * CAEngine.techMiningMonthly(m);
        float perPoint = CAItems.POINT_CREDITS * CAEngine.unitValue / 100f;
        return perPoint <= 0f ? 0f : credits / perPoint;
    }
}
