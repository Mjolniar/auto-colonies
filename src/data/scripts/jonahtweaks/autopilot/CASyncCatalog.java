package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.3.6.0 story mode: every ability the Autarch AI Core can run and its cost in processing power (PP).
 * The core gives BASE_PP; each AI core fed to it adds ALPHA_PP, BETA_PP or GAMMA_PP. The
 * player ticks abilities on the Story page up to their PP. Costs can be changed in data/config/colony_autopilot_story.json
 * without a rebuild. Mod ids are literals here, so this class never loads an optional mod's integration class.
 */
public class CASyncCatalog {
    public static final String GAMMA = "gamma_core", BETA = "beta_core", ALPHA = "alpha_core";
    public static final String PATH = "data/config/colony_autopilot_story.json";

    public static int BASE_PP = 10, ALPHA_PP = 10, BETA_PP = 3, GAMMA_PP = 1;   // 0.3.7.4: the core's own 10, down from 20
    /** One more managed colony (Approve or Auto) past the first; at most MAX_COLONIES bought. */
    public static int COLONY_PP = 2, MAX_COLONIES = 30;
    /** Breakdown: days it takes, and what it gives. Sale: days until a new core is placed. */
    public static int BREAKDOWN_DAYS = 365, BREAKDOWN_ALPHA = 3, BREAKDOWN_BETA = 2, RETURN_DAYS = 365;
    /** After a breakdown: at least RETURN_DAYS, then up to this many more, before salvage can hold one; then this chance per site. */
    public static int RETURN_RANDOM_DAYS = 365;
    public static float SALVAGE_CHANCE = 0.2f;

    public static final String[] GROUPS = {"Colony management", "Items and people", "Money", "Expansion", "Security and war", "Mod features", "Story perks"};

    public static class Ability {
        public final String id, name, text, mod, requires;
        public final int group;
        public int cost;

        Ability(String id, int group, int cost, String mod, String requires, String name, String text) {
            this.id = id; this.group = group; this.cost = cost; this.mod = mod; this.requires = requires; this.name = name; this.text = text;
        }

        /** Its mod is enabled (or it needs none). */
        public boolean available() { return mod == null || modOn(mod); }
    }

    static final List<Ability> ALL = new ArrayList<Ability>();
    static final Map<String, Ability> BY_ID = new LinkedHashMap<String, Ability>();

    static void add(String id, int group, int cost, String mod, String requires, String name, String text) {
        Ability a = new Ability(id, group, cost, mod, requires, name, text);
        ALL.add(a);
        BY_ID.put(id, a);
    }

    static {
        // Colony management
        add("advisor", 0, 1, null, null, "Advisor mode", "Colonies in Advisor get build advice in the build menu and the colony panel.");   // 0.3.7.2
        add("approve", 0, 2, null, null, "Approve mode", "One colony in Approve proposes builds and waits for your answer.");   // 0.3.7.2
        add("auto", 0, 4, null, "approve", "Auto mode", "Colonies in Auto act without asking you first.");
        add("buildout", 0, 2, null, "auto", "Fast buildout", "It pours your savings into a new or money-losing colony until the colony earns.");
        add("cleanup", 0, 2, null, null, "Clean up", "It takes down industries that stopped paying and frees their slots for better ones.");
        add("stations", 0, 2, null, null, "Stations", "It builds and upgrades orbital stations.");
        add("policies", 0, 1, null, null, "Colony policies", "It sets hazard pay, free ports and edicts where they pay.");
        add("storyPoints", 0, 3, null, null, "Story point improvements", "It spends story points on improvements.");
        add("capital", 0, 1, null, null, "Capital", "It picks the capital and keeps the gathering point there.");
        add("logistics", 0, 1, null, null, "Storage logistics", "It ships ships, weapons and items to the refit and sell hubs.");
        add("objectives", 0, 1, null, null, "Stable locations", "It builds comm relays, nav buoys and sensor arrays.");
        add("crisis", 0, 2, null, null, "Crisis response", "It plans defenses at the colony a crisis is coming for.");
        // Items and people
        add("items", 1, 2, null, null, "Colony items", "It places colony items from Autopilot Storage.");
        add("cores", 1, 2, null, null, "AI cores", "It places AI cores from Autopilot Storage.");
        add("admins", 1, 2, null, null, "Administrators", "It hires and places administrators.");
        add("omega", 1, 3, "omega", "cores", "Omega and IX cores", "It places Omega cores and IX Revival's Panopticon cores.");
        // Money
        add("spending", 2, 2, null, null, "Higher spending", "It can use the Lavish spending preset.");
        add("surplus", 2, 4, null, null, "Surplus investment", "Money over the surplus line goes into one focus world.");
        add("floors", 2, 2, null, null, "Production floors", "It builds for ship hulls, weapons, parts, supplies and fuel before money.");
        add("stockpiles", 2, 1, null, null, "Stockpiles", "The money ladder can buy stockpiles.");
        add("military", 2, 3, null, null, "Military", "The money ladder can buy station tiers and military buildings.");
        add("shipyard", 2, 3, null, null, "Shipyard", "The money ladder can order Doctrine hulls through custom production.");
        add("endgame", 2, 5, null, null, "Endgame economy", "At high income it builds for production and defense, and the money ladder can buy the economy rung.");
        add("halfFees", 2, 4, null, null, "Half fees", "The convenience fee can be set to Low.");
        add("noFees", 2, 8, null, "halfFees", "No fees", "The convenience fee can be set to Off.");
        // Expansion
        add("expansion", 3, 6, null, null, "Auto-expansion", "It founds new colonies with idle money, and the money ladder can buy the expansion rung.");
        add("colonyFleets", 3, 2, "nexerelin", "expansion", "Colony fleets", "It sends Nexerelin colony expeditions.");
        // Security and war
        add("lowProfile", 4, 1, null, null, "Low profile", "It can use the Low profile expansion style.");
        add("enforcers", 4, 3, null, null, "Enforcer patrols", "Colony patrols attack enforcement fleets.");
        add("defense", 4, 3, "nexerelin", null, "Emergency defense", "It sends defense fleets to colonies under attack, at the Defend war level.");
        add("agents", 4, 2, "nexerelin", null, "Agents: defense", "It recruits agents for security and relations.");
        add("offense", 4, 3, "nexerelin", "agents", "Agents: offense", "Its agents sabotage the target you set.");
        add("diplomacy", 4, 2, "nexerelin", null, "Diplomacy", "It answers ceasefire and peace offers by your stances.");
        add("autonomy", 4, 1, "nexerelin", null, "Autonomy", "It grants autonomy to colonies that keep losing money.");
        add("retaliate", 4, 3, "nexerelin", "defense", "War: Retaliate", "Its war fleets raid factions that attacked you.");
        add("strike", 4, 5, "nexerelin", "retaliate", "War: Hold them down", "Its war fleets strike hostile factions.");
        add("conquest", 4, 8, "nexerelin", "strike", "War: Conquer", "Its war fleets invade and retake colonies, and it looks after the ones you conquer.");
        add("scorched", 4, 5, "nexerelin", "conquest", "War: Scorched", "Its war fleets bombard targets once you authorize it.");
        // Mod features
        add("terraform", 5, 3, "terra", null, "Terraforming", "It runs Terraforming Made Easy's plan projects, and TASC's projects and stations.");
        add("surplusTerra", 5, 4, "terraformingmadeeasy", "terraform", "Surplus terraforming", "It remakes worlds with the surplus.");
        add("research", 5, 3, "aotd_vok", null, "Research", "It runs research, special project supply and research items.");
        add("surplusResearch", 5, 3, "aotd_vok", "research", "Surplus research", "It builds more Research Facilities.");
        add("mega", 5, 4, "aotd_vok", null, "Megastructures", "It runs the Coronal Hypershunt, Pluto, Nidavelir and the Floating City.");
        add("wonders", 5, 5, "aotd_vok", null, "Wonders and gates", "It builds the Daedalus Array and the Bifrost network, and the money ladder can buy the wonders rung.");
        add("contracts", 5, 1, "aotd_theory_of_toolbox", null, "Trade contracts", "It takes Theory of Toolbox contracts.");
        add("indevo", 5, 2, "IndEvo", null, "Industrial Evolution", "It runs edicts, ambassadors, VPC chips, specialties and Courier Port shipping.");
        add("augments", 5, 2, "niko_stationAugments", null, "Station augments", "It fits Station Augments loadouts.");
        add("settlement", 5, 2, "assortment_of_things", null, "Settlement", "It runs Random Assortment of Things' settlement.");
        // Story perks
        add("instantBuild", 6, 15, null, null, "Instant construction", "Builds and upgrades it starts finish the same day.");
        add("freeBuild", 6, 25, null, "instantBuild", "Free construction", "Builds and upgrades it starts cost nothing.");
        add("twoBuilds", 6, 6, null, null, "Two builds at once", "Each colony can build two new buildings at once.");
        add("threeBuilds", 6, 8, null, "twoBuilds", "Three builds at once", "Each colony can build three new buildings at once.");
    }

    /** The Story perks group: past the game's rules, so they run only in story mode. */
    static final int PERKS = 6;

    // ---------------- 0.3.7.4: packages and tiers ----------------

    /**
     * What you buy on the Story page: a package of abilities with one cost, in a tier. The abilities stay the switches the
     * code asks (CASync.has); a package's abilities run once it's bought, each as far as its own mod and what it needs allow.
     * A package shows while one of its abilities has its mod.
     */
    public static class Pack {
        public final String id, name, requires;
        public final String[] members;
        public final int tier;
        public int cost;

        Pack(String id, int tier, int cost, String requires, String name, String... members) {
            this.id = id; this.tier = tier; this.cost = cost; this.requires = requires; this.name = name; this.members = members;
        }

        public boolean available() {
            for (String m : members) { Ability a = BY_ID.get(m); if (a != null && a.available()) return true; }
            return false;
        }

        /** Its abilities' names, the ones their mods allow. */
        public String contents() {
            StringBuilder sb = new StringBuilder();
            for (String m : members) {
                Ability a = BY_ID.get(m);
                if (a == null || !a.available()) continue;
                sb.append(sb.length() == 0 ? "" : ", ").append(a.name);
            }
            return sb.toString();
        }
    }

    /** The tiers, in order. A tier opens when you own TIER_SHARE of the packages in the tier before it (CASync.openTier). */
    public static final String[] TIERS = {"Tier I", "Tier II", "Tier III", "Tier IV"};
    /** Share of a tier's packages (the ones their mods allow) that opens the next tier; "tierShare" in the config file. */
    static float TIER_SHARE = 2f / 3f;
    static final List<Pack> PACKS = new ArrayList<Pack>();
    static final Map<String, Pack> PACK_BY_ID = new LinkedHashMap<String, Pack>(), PACK_OF = new HashMap<String, Pack>();

    static void pack(String id, int tier, int cost, String requires, String name, String... members) {
        Pack p = new Pack(id, tier, cost, requires, name, members);
        PACKS.add(p);
        PACK_BY_ID.put(id, p);
        for (String m : members) PACK_OF.put(m, p);
    }

    static {
        pack("advice", 0, 2, null, "Advisor mode", "advisor");
        pack("oversight", 0, 4, null, "Approve mode", "approve");
        pack("upkeep", 0, 5, null, "Colony upkeep", "cleanup", "stations", "policies", "crisis", "lowProfile");
        pack("supply", 0, 5, null, "Supply and items", "capital", "logistics", "objectives", "items", "stockpiles");
        pack("automation", 1, 6, "oversight", "Auto mode", "auto");
        pack("staff", 1, 6, null, "Cores and staff", "cores", "admins", "storyPoints");
        pack("economy", 1, 5, null, "Economy", "spending", "floors", "contracts");
        pack("security", 1, 6, null, "Security", "enforcers", "defense", "agents", "diplomacy", "autonomy");
        pack("projects", 1, 6, null, "Mod projects", "terraform", "research", "indevo", "augments", "settlement");
        pack("growth", 2, 9, "automation", "Growth", "buildout", "surplus", "halfFees");
        pack("industry", 2, 8, null, "Industry", "military", "shipyard", "omega");
        pack("frontier", 2, 8, null, "Expansion", "expansion", "colonyFleets");
        pack("warfare", 2, 7, "security", "War", "retaliate", "offense");
        pack("megaprojects", 2, 8, "projects", "Large projects", "surplusTerra", "surplusResearch", "mega");
        pack("dominion", 3, 15, "growth", "Endgame", "endgame", "noFees", "wonders");
        pack("warlord", 3, 15, "warfare", "Conquest", "strike", "conquest", "scorched");
        pack("parallel", 3, 12, null, "Parallel construction", "twoBuilds", "threeBuilds");
        pack("instant", 3, 30, null, "Instant construction", "instantBuild", "freeBuild");
    }

    public static Pack pack(String id) { return PACK_BY_ID.get(id); }

    /** The package an ability is in, or null. */
    public static Pack packOf(String ability) { return PACK_OF.get(ability); }

    /** The tier of a package, or of an ability's package (0 for Tier I). */
    public static int tierOf(String id) {
        Pack p = PACK_BY_ID.get(id);
        if (p == null) p = PACK_OF.get(id);
        return p == null ? 0 : p.tier;
    }

    /** The packages in a tier that their mods allow. */
    public static List<Pack> inTier(int t) {
        List<Pack> out = new ArrayList<Pack>();
        for (Pack p : PACKS) if (p.tier == t && p.available()) out.add(p);
        return out;
    }

    /** The packages their mods allow, in menu order. */
    public static List<Pack> packs() {
        List<Pack> out = new ArrayList<Pack>();
        for (Pack p : PACKS) if (p.available()) out.add(p);
        return out;
    }

    /** "Approve mode (4 bandwidth)": the package an ability is bought in, for "needs" lines. */
    public static String buy(String ability) {
        Pack p = PACK_OF.get(ability);
        return p == null ? "" : p.name + " (" + p.cost + " bandwidth)";
    }

    /** Pure: abilities to own in a tier of `count` to open the next one. */
    public static int needed(int count, float share) { return count <= 0 ? 0 : Math.max(1, (int) Math.ceil(count * share - 1e-4)); }

    public static Ability get(String id) { return BY_ID.get(id); }

    /** 0.3.6.7: a Story perk (off with story mode off). */
    public static boolean perk(String id) {
        Ability a = BY_ID.get(id);
        return a != null && a.group == PERKS;
    }

    /** Abilities whose mod is enabled, in menu order. */
    public static List<Ability> available() {
        List<Ability> out = new ArrayList<Ability>();
        for (Ability a : ALL) if (a.available()) out.add(a);
        return out;
    }

    static boolean modOn(String mod) {
        try {
            com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
            if ("terra".equals(mod)) return mm.isModEnabled("terraformingmadeeasy") || mm.isModEnabled("Terraforming & Station Construction");
            if ("omega".equals(mod)) return mm.isModEnabled("omega_core_everywhere") || mm.isModEnabled("astral_ascension_renewed")
                    || mm.isModEnabled("EmergentThreats_IX_Revival");
            return mm.isModEnabled(mod);
        } catch (Throwable t) { return false; }
    }

    /** "10 Alpha, 3 Beta" style text for a set of cores {gamma, beta, alpha}. */
    public static String cores(int g, int b, int a) {
        List<String> p = new ArrayList<String>();
        if (a > 0) p.add(a + " Alpha");
        if (b > 0) p.add(b + " Beta");
        if (g > 0) p.add(g + " Gamma");
        if (p.isEmpty()) return "no cores";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < p.size(); i++) s.append(i == 0 ? "" : i == p.size() - 1 ? " and " : ", ").append(p.get(i));
        return s.append(a + b + g == 1 ? " core" : " cores").toString();
    }

    /** PP for a set of cores {gamma, beta, alpha}. */
    public static int points(int g, int b, int a) { return g * GAMMA_PP + b * BETA_PP + a * ALPHA_PP; }

    static boolean loaded = false;

    /** Costs and numbers from the config file, once a session; anything missing keeps the built-in value. */
    public static void load() {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject j = Global.getSettings().loadJSON(PATH, CASync.MOD_ID);
            JSONObject pp = j.optJSONObject("pp");
            if (pp != null) {
                BASE_PP = Math.max(0, pp.optInt("base", BASE_PP));
                ALPHA_PP = Math.max(0, pp.optInt("alpha", ALPHA_PP));
                BETA_PP = Math.max(0, pp.optInt("beta", BETA_PP));
                GAMMA_PP = Math.max(0, pp.optInt("gamma", GAMMA_PP));
                COLONY_PP = Math.max(0, pp.optInt("colony", COLONY_PP));
                MAX_COLONIES = Math.max(0, pp.optInt("maxColonies", MAX_COLONIES));
            }
            JSONObject core = j.optJSONObject("core");
            if (core != null) {
                BREAKDOWN_DAYS = Math.max(0, core.optInt("breakdownDays", BREAKDOWN_DAYS));
                BREAKDOWN_ALPHA = Math.max(0, core.optInt("breakdownAlpha", BREAKDOWN_ALPHA));
                BREAKDOWN_BETA = Math.max(0, core.optInt("breakdownBeta", BREAKDOWN_BETA));
                RETURN_DAYS = Math.max(0, core.optInt("returnDays", RETURN_DAYS));
                RETURN_RANDOM_DAYS = Math.max(0, core.optInt("returnRandomDays", RETURN_RANDOM_DAYS));
                SALVAGE_CHANCE = (float) Math.max(0.0, Math.min(1.0, core.optDouble("salvageChance", SALVAGE_CHANCE)));
            }
            TIER_SHARE = (float) Math.max(0.0, Math.min(1.0, j.optDouble("tierShare", TIER_SHARE)));   // 0.3.7.3
            JSONObject costs = j.optJSONObject("packs");   // 0.3.7.4: packages are what costs bandwidth
            if (costs != null) for (Pack p : PACKS) p.cost = Math.max(0, costs.optInt(p.id, p.cost));
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "story costs: " + PATH + " couldn't be read, using the built-in costs", t);
        }
    }
}
