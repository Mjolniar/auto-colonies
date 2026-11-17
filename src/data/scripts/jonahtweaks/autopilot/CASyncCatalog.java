package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.3.6.0 story mode: every ability the Autarch AI Core can run, and the packages that hold them with their cost in processing power (PP).
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
    /** 0.3.8.0: one more colony the Autarch administrator governs, past the one its package gives ("autarchSeat" in "pp"). */
    public static int SEAT_PP = 10;
    /** Breakdown: days it takes, and what it gives. Sale: days until a new core is placed. */
    public static int BREAKDOWN_DAYS = 365, BREAKDOWN_ALPHA = 3, BREAKDOWN_BETA = 2, RETURN_DAYS = 365;
    /** After a breakdown: at least RETURN_DAYS, then up to this many more, before salvage can hold one; then this chance per site. */
    public static int RETURN_RANDOM_DAYS = 365;
    public static float SALVAGE_CHANCE = 0.2f;
    /** 0.3.7.8: Rapid construction's flat fee per build or upgrade, and the share of the build time it takes. */
    public static float RAPID_FEE = 100000f, RAPID_TIME = 1f / 3f;

    // 0.3.8.9: GROUPS, the ability names, texts and costs, available() and Pack.contents() are removed (nothing read them;
    // packages carry the names, costs and descriptions)

    public static class Ability {
        public final String id, mod, requires;
        public final int group;

        Ability(String id, int group, String mod, String requires) {
            this.id = id; this.group = group; this.mod = mod; this.requires = requires;
        }

        /** Its mod is enabled (or it needs none). */
        public boolean available() { return mod == null || modOn(mod); }
    }

    static final List<Ability> ALL = new ArrayList<Ability>();
    static final Map<String, Ability> BY_ID = new LinkedHashMap<String, Ability>();

    static void add(String id, int group, String mod, String requires) {
        Ability a = new Ability(id, group, mod, requires);
        ALL.add(a);
        BY_ID.put(id, a);
    }

    static {
        // Colony management
        add("advisor", 0, null, null);   // 0.3.7.2
        add("approve", 0, null, null);   // 0.3.7.2
        add("auto", 0, null, "approve");
        add("buildout", 0, null, "auto");
        add("cleanup", 0, null, null);
        add("stations", 0, null, null);
        add("policies", 0, null, null);
        add("storyPoints", 0, null, null);
        add("capital", 0, null, null);
        add("logistics", 0, null, null);
        add("objectives", 0, null, null);
        add("crisis", 0, null, null);
        // Items and people
        add("items", 1, null, null);
        add("cores", 1, null, null);
        add("admins", 1, null, null);
        add("omega", 1, "omega", "cores");
        // Money
        add("spending", 2, null, null);
        add("surplus", 2, null, null);
        add("floors", 2, null, null);
        add("stockpiles", 2, null, null);
        add("military", 2, null, null);
        add("shipyard", 2, null, null);
        add("endgame", 2, null, null);
        add("halfFees", 2, null, null);
        add("noFees", 2, null, "halfFees");
        // Expansion
        add("expansion", 3, null, null);
        add("colonyFleets", 3, "nexerelin", "expansion");
        // Security and war
        add("lowProfile", 4, null, null);
        add("enforcers", 4, null, null);
        add("defense", 4, "nexerelin", null);
        add("agents", 4, "nexerelin", null);
        add("offense", 4, "nexerelin", "agents");
        add("diplomacy", 4, "nexerelin", null);
        add("autonomy", 4, "nexerelin", null);
        add("retaliate", 4, "nexerelin", "defense");
        add("strike", 4, "nexerelin", "retaliate");
        add("conquest", 4, "nexerelin", "strike");
        add("scorched", 4, "nexerelin", "conquest");
        // Mod features
        add("terraform", 5, "terra", null);
        add("surplusTerra", 5, "terraformingmadeeasy", "terraform");
        add("research", 5, "aotd_vok", null);
        add("surplusResearch", 5, "aotd_vok", "research");
        add("mega", 5, "aotd_vok", null);
        add("wonders", 5, "aotd_vok", null);
        add("contracts", 5, "aotd_theory_of_toolbox", null);
        add("indevo", 5, "IndEvo", null);
        add("augments", 5, "niko_stationAugments", null);
        add("settlement", 5, "assortment_of_things", null);
        // Story perks
        add("rapidBuild", 6, null, null);   // 0.3.7.8: replaces Instant and Free construction
        add("twoBuilds", 6, null, null);
        add("threeBuilds", 6, null, "twoBuilds");
        add("autarchAdmin", 6, null, null);   // 0.3.7.9; 0.3.8.0: per colony
        add("rapidTerra", 6, "terra", null);   // 0.3.7.9
        add("rapidResearch", 6, "aotd_vok", null);   // 0.3.9.9: Vaults of Knowledge research in 2/3 of the time (CARapidResearch)
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
        /** 0.3.7.5: what it does, one line in the core's words: the Story page and the TriPad's summary show it. */
        public String desc = "";

        Pack(String id, int tier, int cost, String requires, String name, String... members) {
            this.id = id; this.tier = tier; this.cost = cost; this.requires = requires; this.name = name; this.members = members;
        }

        public boolean available() {
            for (String m : members) { Ability a = BY_ID.get(m); if (a != null && a.available()) return true; }
            return false;
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
        pack("instant", 3, 20, null, "Rapid construction", "rapidBuild");   // 0.3.7.8: the id stays, so saves that bought it keep it
        pack("terraforming", 3, 12, null, "Rapid terraforming", "rapidTerra");   // 0.3.7.9
        pack("autarch", 3, 10, "staff", "Autarch administrator", "autarchAdmin");
        pack("fastResearch", 3, 20, null, "Rapid research", "rapidResearch");   // 0.3.9.9: Tier IV, shown only with Vaults of Knowledge   // 0.3.7.9; 0.3.8.0: Tier IV, its cost is the first colony
        desc("advice", "It suggests what to build next at your colonies.");
        desc("oversight", "It proposes builds at one colony and waits for your OK.");
        desc("upkeep", "It cuts unprofitable industry, keeps up orbital stations, sets colony policy and preps for crises.");
        desc("supply", "It handles the capital, storage shipments, stable locations, colony items and stockpiles.");
        desc("automation", "It builds at colonies on Auto without asking you first.");
        desc("staff", "It places AI cores, hires administrators and spends story points on improvements.");
        desc("economy", "It raises the spending limit, keeps production floors up and takes trade contracts.");
        desc("security", "It runs enforcer patrols, emergency defense, agents, diplomacy and autonomy.");
        desc("projects", "It runs terraforming, research and other mod projects.");
        desc("growth", "It puts savings into new colonies and the surplus, at lower fees.");
        desc("industry", "It funds military buildings and hulls, and places Omega-class cores.");
        desc("frontier", "It'll found new colonies with spare money.");
        desc("warfare", "It retaliates against attackers and runs agent sabotage.");
        desc("megaprojects", "It funds large terraforming, research and megastructure projects.");
        desc("dominion", "It runs the endgame economy and wonders, with no fees.");
        desc("warlord", "It strikes, invades and, once authorized, bombards hostile factions.");
        desc("parallel", "It runs up to three builds per colony at once.");
        numberDescs();
        desc("terraforming", "It halves terraforming time at managed colonies.");
        desc("fastResearch", "It makes Vaults of Knowledge research take 2/3 of its usual time, so projects finish faster.");
    }

    static void desc(String id, String text) { PACK_BY_ID.get(id).desc = text; }

    /** 0.3.8.9: the descriptions that name a config value (rapidFee, autarchSeat), built from it: here and after load(). */
    static void numberDescs() {
        desc("instant", "Builds take a third of the time, and each one costs " + String.format(java.util.Locale.ROOT, "%,d", Math.round(RAPID_FEE))
                + " credits more.");
        desc("autarch", "Governs one managed colony per " + SEAT_PP + " bandwidth, with its own skill, Autarchy.");
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

    // 0.3.8.9: packs() is removed (its one caller, CASync.keepForFeeding, is gone)

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
                SEAT_PP = Math.max(0, pp.optInt("autarchSeat", SEAT_PP));   // 0.3.8.0
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
            RAPID_FEE = (float) Math.max(0.0, j.optDouble("rapidFee", RAPID_FEE));   // 0.3.7.8
            JSONObject costs = j.optJSONObject("packs");   // 0.3.7.4: packages are what costs bandwidth
            if (costs != null) for (Pack p : PACKS) p.cost = Math.max(0, costs.optInt(p.id, p.cost));
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "story costs: " + PATH + " couldn't be read, using the built-in costs", t);
        }
        numberDescs();   // 0.3.8.9: the file's fee and seat cost in the text
    }
}
