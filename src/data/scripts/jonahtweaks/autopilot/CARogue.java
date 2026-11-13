package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.2.1: Rogue Planets 1.0.0 (mod id rogue_planets, coolestofguys). Reached by condition ids, memory keys and the public static
 * numbers of its condition classes (CAConst, by name); no class of the mod is referenced. Without the mod every hook here is
 * neutral (on() is false and no colony has its conditions).
 * - Conditions: TABLE holds each condition's kind, the buildings it needs and how Autopilot handles it. effect() is the
 *   condition's own rule from the mod's code, as the engine's effects (fx) and output lines, for a set of buildings.
 * - Build values: score() adds what a pick starts, ends or changes (the synergy pairs, Pilgrim World's per-industry bonus,
 *   Hydrocarbon Fractionation's Light Industry lines), build time at Geographically Efficient Zoning, the crustquake share at
 *   Supercritical Crust and the patrol quality at Chrono-Spatial Dilation. keepFx() is the same run backwards for keep().
 * - Upkeep: upkeepMult() for CAEngine.baseUpkeep (Geothermal Leylines, Chrono-Spatial Dilation, Closed-loop Hydroponic
 *   Matrix, Megalithic Outcrops, Forgotten Vault-World).
 * - Rules: forbids() (Fragile Biosphere, the reactor pair, the vault's Tech-Mining), keepReason() (the running reactor, the
 *   vault's Tech-Mining), stabilityFloor() (Viable Slave Labor, Supercooled Cryo-Sinks), noCores() (Pilgrim World),
 *   adminLocked() (Trash King), hazardPayBuiltIn() (Diamond Core), portPenaltyCovered() (Smugglers' Haven), storyShip()
 *   (the Afterlight).
 * Checked offline by JTRoguePlanetsCheck.
 */
public class CARogue {
    private CARogue() { }

    public static final String MOD = "rogue_planets";
    static final String PKG = "roguePlanets.econ.conditions.";

    // ---------------- condition ids ----------------

    static final String FRAGILE = "fragile_biosphere", CRUST = "supercritical_crust", DEGENERATE = "degenerate_volatiles",
            LEYLINES = "geothermal_leylines", DIAMOND = "diamond_core", SHIPYARDS = "automated_shipyards",
            HYDROCARBON = "hydrocarbon_fractionation", VAULT = "forgotten_vault_world", ANOMALY = "antimatter_anomaly",
            SYNDICATE = "clandestine_harvesting_syndicate", MEGALITHIC = "megalithic_outcrops", ZONING = "geographically_efficient_zoning",
            RADIOLYTIC = "radiolytic_catalyst", CHRONO = "chrono_spatial_dilation", INVERSION = "rapid_geomagnetic_inversion",
            HYDROPONICS = "closed_loop_hydroponics", CRYO = "supercooled_cryo_sinks", MAGMATIC = "magmatic_antimatter_forge",
            CALDERA = "hollowed_caldera_garrisons", RESORT = "vacation_paradise_resort", SMUGGLERS = "smugglers_haven", WATCHED = "watched",
            REACTOR = "volatile_additive_reactor", SLAVES = "viable_slave_labor", OBSERVATORY = "cold_sky_observatory",
            MISSILES = "ground_missile_defense", SIGNAL = "resonant_signal_trap", GRAVEYARD = "ship_graveyard", PILGRIM = "pilgrim_world",
            KING = "trash_king", ICE = "ice_locked_seas";

    // ---------------- memory keys (the mod's own, stored in saves) ----------------

    /** ViableSlaveLaborCondition.KEY_UNREST_MONTHS. */
    static final String UNREST = "$slave_labor_unrest_months";
    /** ForgottenVaultWorldCondition.VAULT_MONTHS_KEY. */
    static final String VAULT_MONTHS = "$vault_excavation_months";
    /** TrashKingCondition's state key and its exiled value. */
    static final String KING_STATE = "$rp_king_state", KING_EXILED = "exiled";
    /** AdditiveReactorScript's running flag and VolatileAdditiveReactorCondition.KEY_MELTDOWN. */
    static final String REACTOR_RUNNING = "$additive_wasActive", MELTDOWN = "$additive_meltdown";
    /** ShipGraveyardCondition.KEY_LEDGER. */
    static final String LEDGER = "$rp_graveyard_ledger";
    /** AfterlightCaptainScript.HULL_PREFIX and KEY_MEMBER. */
    static final String AFTERLIGHT_HULL = "wolf_afterlight_", AFTERLIGHT_MEMBER = "$afterlight_member_id";

    // ---------------- building ids ----------------

    static final String MINING = "mining", OW = "orbitalworks", HI = "heavyindustry", MEGAPORT = "megaport", SPACEPORT = "spaceport",
            LI = "lightindustry", MB = "militarybase", HC = "highcommand", FARMING = "farming", AQUA = "aquaculture", COMMERCE = "commerce",
            REFINING = "refining", FUEL = "fuelprod", WAYSTATION = "waystation", BATTERIES = "heavybatteries", TECHMINING = "techmining",
            POPULATION = "population";
    static final String[] STATIONS = {"orbitalstation", "orbitalstation_mid", "orbitalstation_high", "battlestation", "battlestation_mid",
            "battlestation_high", "starfortress", "starfortress_mid", "starfortress_high"};

    /** Magmatic Antimatter Forge at the production gathering point: an estimate of what d-mod-free, s-modded new hulls are worth a month. */
    static final float MAGMA_VALUE = 3000f;
    /** Ground-to-Space Missile Defense: counted as an orbital station's fleet (CAEngine: orbital station fx[4] 0.3). */
    static final float MISSILE_FLEET = 0.3f;
    /** Diamond Core: the hazard pay effect, counted as Omega's "growth x2" (CAOmega). */
    static final float DIAMOND_GROWTH = 2f;
    /** Storage deliveries (fuel, crustquake goods, vault loot) are counted at this share of base price: what selling them gets. */
    static final float CARGO_SHARE = 0.5f;

    // ---------------- the condition table ----------------

    /** What a condition is, as Autopilot sees it. */
    public enum Kind { SYNERGY, BUILDING, COLONY, STORY, PLAYER }

    /** One condition: its class, kind, the buildings it needs (one of each group), what it does and how Autopilot handles it. */
    public static final class Cond {
        public final String id, cls, name, does, handled;
        public final Kind kind;
        public final String[][] needs;
        Cond(String id, String cls, String name, Kind kind, String[][] needs, String does, String handled) {
            this.id = id; this.cls = cls; this.name = name; this.kind = kind; this.needs = needs; this.does = does; this.handled = handled;
        }
    }

    static String[][] g(String[]... groups) { return groups; }
    static String[] o(String... ids) { return ids; }
    static final String[][] NO_NEEDS = new String[0][];

    static final Map<String, Cond> TABLE = new LinkedHashMap<String, Cond>();
    static void put(String id, String cls, String name, Kind k, String[][] needs, String does, String handled) {
        TABLE.put(id, new Cond(id, cls, name, k, needs, does, handled));
    }
    static {
        // synergy pairs: one building from each group, both working
        put(DEGENERATE, "DegenerateVolatilesCondition", "Degenerate Volatiles", Kind.SYNERGY, g(o(MINING), o(OW)),
                "Orbital Works +2 heavy armaments and +2 heavy machinery, stability -1, accessibility -15%.",
                "Counted in build values. The output counts toward the weapons and producer input floors.");
        put(DIAMOND, "DiamondCoreCondition", "Diamond Core", Kind.SYNERGY, g(o(MINING), o(MEGAPORT)),
                "The hazard pay growth effect at no cost. Hazard pay is held off.",
                "Counted in build values as growth. Autopilot does not turn hazard pay on while it runs.");
        put(RADIOLYTIC, "RadiolyticCatalystCondition", "Radiolytic Catalyst", Kind.SYNERGY, g(o(MINING), o(LI)),
                "Drug export income x3.",
                "Counted in build values from the colony's drug export income now.");
        put(CALDERA, "HollowedCalderaGarrisonsCondition", "Hollowed Caldera Garrisons", Kind.SYNERGY, g(o(MINING), o(MB, HC)),
                "Ground defense x1.5 with both, x0.5 without.",
                "Counted in build values as defense.");
        put(RESORT, "VacationParadiseResortCondition", "Vacation Paradise & Resort", Kind.SYNERGY, g(o(FARMING, AQUA), o(COMMERCE)),
                "Colony income +5% per stability point above 5 (-5% per point below), ground defense x0.",
                "Counted in build values: income by stability, and the lost ground defense.");
        put(SMUGGLERS, "SmugglersHavenCondition", "Smugglers' Haven", Kind.SYNERGY, g(o(COMMERCE), o(HI, OW)),
                "Free port and Commerce stability penalties cancelled, tariffs x0.25 for you.",
                "Counted in build values as stability. Opening the free port costs no stability while it runs.");
        put(SLAVES, "ViableSlaveLaborCondition", "Viable Slave Labor Population", Kind.SYNERGY, g(o(MB, HC), o(COMMERCE)),
                "Colony income +50%, stability -3. Three month-ends in a row at stability 5 or lower: the colony goes to pirates.",
                "Counted in build values. Stability floor 6 at this colony. At the second month of unrest Autopilot takes down Commerce if that lifts stability above 5.");
        put(REACTOR, "VolatileAdditiveReactorCondition", "Volatile Additive Reactor", Kind.SYNERGY, g(o(REFINING), o(FUEL)),
                "Your fleets in the system +2 burn. If Refining or Fuel Production stops working: every building disrupted 180 days, hazard +100%.",
                "Autopilot does not build the second of the pair. Once both are there, Autopilot does not take down, replace or upgrade either.");
        put(MAGMATIC, "MagmaticAntimatterForgeCondition", "Magmatic Antimatter Forge", Kind.SYNERGY, g(o(HI, OW), o(FUEL)),
                "Ships your production delivers here lose their d-mods and can gain s-mods.",
                "Counted in build values at the production gathering point (estimate). Deliveries follow the capital; move the gathering point there and the capital follows. Preferred host for floor Heavy Industry and Fuel Production.");
        put(OBSERVATORY, "ColdSkyObservatoryCondition", "Cold Sky Observatory", Kind.PLAYER, g(o(WAYSTATION), STATIONS),
                "Surveys 1 to 3 unsurveyed planets a month.",
                "No build value: its surveys are exploration.");
        put(MISSILES, "GroundMissileDefenseCondition", "Ground-to-Space Missile Defense", Kind.SYNERGY, g(o(BATTERIES), o(WAYSTATION)),
                "500 missiles into battles in the system.",
                "Counted in build values as an orbital station's fleet.");
        put(SIGNAL, "ResonantSignalTrapCondition", "Resonant Signal Trap", Kind.PLAYER, g(o(WAYSTATION), o(MEGAPORT)),
                "An escape from one encounter in the system every 30 days.",
                "No build value: it serves your fleet.");
        // one building
        put(HYDROCARBON, "HydrocarbonFractionationCondition", "Hydrocarbon Fractionation", Kind.BUILDING, g(o(MINING, LI)),
                "Hazard -50%. Mining's volatiles become organics. Light Industry +2 on each output.",
                "Counted in build values and in the plan (no Fuel Depot plan on volatiles that become organics).");
        put(MEGALITHIC, "MegalithicOutcropsCondition", "Megalithic Outcrops", Kind.BUILDING, g(o(MINING)),
                "Mining upkeep 0, its drugs and heavy machinery demand -2.",
                "Counted in Mining's upkeep and demand.");
        put(SYNDICATE, "ClandestineHarvestingSyndicateCondition", "Clandestine Harvesting Syndicate", Kind.BUILDING, g(o(SPACEPORT, MEGAPORT)),
                "Stability -2, growth -5 per size above 2. Spaceport or Megaport +organs (size - 2). A mercenary fleet hunts you in the system.",
                "Organs counted in the port's build value (illegal without a free port: the colony's own shortage only).");
        put(PILGRIM, "PilgrimWorldCondition", "Pilgrim World", Kind.BUILDING, NO_NEEDS,
                "Each working industry +1 stability and +20% accessibility. Any AI core or AI administrator turns these into penalties.",
                "Counted in build values. Autopilot puts no AI cores and no AI administrator here.");
        put(FRAGILE, "FragileBiosphereCondition", "Fragile Biosphere", Kind.BUILDING, g(o(FARMING)),
                "Hazard -10%. Farming is the only industry allowed; the game takes others down.",
                "Autopilot builds no other industry here and plans none.");
        put(GRAVEYARD, "ShipGraveyardCondition", "Ship Graveyard", Kind.BUILDING, g(o(REFINING, HI, OW, MINING)),
                "Wrecks from your battles in the system become hulls in storage. Refining, Heavy Industry, Orbital Works and Mining improve them.",
                "No build value (hulls depend on battles). Preferred host for floor Heavy Industry and Orbital Works.");
        put(VAULT, "ForgottenVaultWorldCondition", "Forgotten Vault-World", Kind.STORY, g(o(TECHMINING)),
                "With Tech-Mining: monthly vault loot for 18 or 30 months and a capital ship at the end; stability -3, accessibility -50%, hazard +50%, Tech-Mining upkeep x10.",
                "Left to you: Autopilot does not build Tech-Mining here or take it down by value. Its upkeep is counted.");
        // the colony as a whole
        put(LEYLINES, "GeothermalLeylinesCondition", "Geothermal Leylines", Kind.COLONY, NO_NEEDS,
                "Hazard +50%, upkeep of every building x0.5.", "Counted in every upkeep here.");
        put(CHRONO, "ChronoSpatialDilationCondition", "Chrono-Spatial Dilation", Kind.COLONY, NO_NEEDS,
                "Accessibility +50%, upkeep of every building x2, ship quality -100% for local fleets, disruptions end twice as fast.",
                "Counted in every upkeep here. Patrol and fleet value counted at half.");
        put(HYDROPONICS, "ClosedLoopHydroponicsCondition", "Closed-loop Hydroponic Matrix", Kind.COLONY, NO_NEEDS,
                "No food demand, Population and Infrastructure upkeep x2.", "Counted in upkeep.");
        put(ZONING, "GeographicallyEfficientZoningCondition", "Geographically Efficient Zoning", Kind.COLONY, NO_NEEDS,
                "Builds and upgrades take half the time.", "Counted in build values (half the wait).");
        put(INVERSION, "RapidGeomagneticInversionCondition", "Rapid Geomagnetic Inversion", Kind.COLONY, NO_NEEDS,
                "Hazard +100% in odd months, up to -100% in even months.", "Read live from the hazard rating.");
        put(CRYO, "SupercooledCryoSinksCondition", "Supercooled Cryo-Sinks", Kind.COLONY, NO_NEEDS,
                "After 120 days, stability 5 or lower: hazard +50% and stability -3. Stability above 5: +700 sensor range for you in the system.",
                "Stability floor 6 at this colony.");
        put(CRUST, "SupercriticalCrustCondition", "Supercritical Crust", Kind.COLONY, NO_NEEDS,
                "Hazard +50%, ground defense x0.5. Each month one building is disrupted 30 days and ore, rare ore and luxury goods go to storage.",
                "New buildings counted at their share of working time. The storage goods count in new-colony value.");
        put(ANOMALY, "AntimatterAnomalyCondition", "Antimatter Anomaly", Kind.COLONY, NO_NEEDS,
                "Fuel to storage each month: 100 per size above 2, up to 400.", "Counted in new-colony value.");
        put(SHIPYARDS, "AutomatedShipyardsCondition", "Automated Shipyards", Kind.COLONY, NO_NEEDS,
                "Stability -3. A defense flotilla from size 3.", "Its stability is read live. No building changes it.");
        put(ICE, "IceLockedSeasCondition", "Ice-Locked Seas", Kind.COLONY, NO_NEEDS,
                "A food deposit at -1 for Aquaculture on Snowball worlds.", "Read from the game's deposit tables.");
        put(KING, "TrashKingCondition", "Trash King", Kind.STORY, NO_NEEDS,
                "The king is the administrator. Deposing him costs up to 4 stability and 25% accessibility.",
                "Left to you: Autopilot does not seat, move or replace an administrator here.");
        put(WATCHED, "WatchedCondition", "Watched", Kind.STORY, NO_NEEDS,
                "Hazard +25%. Only in the Violet Dwarf system.", "Left to you: the system is marked not to be colonized, so Auto-expansion skips it.");
    }

    /** Whether a condition id is Rogue Planets'. */
    public static boolean is(String cond) { return cond != null && TABLE.containsKey(cond); }

    /** Every condition id, for CACompat. */
    static String[] ids() { return TABLE.keySet().toArray(new String[0]); }

    /** The synergy pairs: condition id to its two groups of buildings. */
    static Map<String, String[][]> pairs() {
        Map<String, String[][]> out = new LinkedHashMap<String, String[][]>();
        for (Cond c : TABLE.values()) if (c.needs.length == 2) out.put(c.id, c.needs);
        return out;
    }

    // ---------------- on / link ----------------

    static Boolean on;

    /** Rogue Planets is enabled (asked once a session). */
    static boolean on() {
        if (on != null) return on;
        try { on = Global.getSettings().getModManager().isModEnabled(MOD); }
        catch (Throwable t) { return false; }
        return on;
    }

    /** For CACompat: every condition class resolves by name. */
    static boolean link() {
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            for (Cond c : TABLE.values()) Class.forName(PKG + c.cls, false, cl);
            return true;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "Rogue Planets: a condition class is gone; its rules use Autopilot's copies of its numbers", t);
            return false;
        }
    }

    /** Offline checks: the building ids that count as industries (null in game: the game's specs decide). */
    static Set<String> testIndustries;

    /** Whether the game counts building id as an industry (CAEngine.isIndustry). */
    static boolean industry(String id) { return testIndustries != null ? testIndustries.contains(id) : CAEngine.isIndustry(id); }

    /** A number of the mod's own (public static), or the value Autopilot was written against. */
    static float k(String cls, String field, float dflt) { return CAConst.num(PKG + cls, field, dflt); }

    // ---------------- the colony's facts ----------------

    /** What the condition rules read about a colony besides its buildings. Pure data. */
    static final class Facts {
        int size;
        /** Raw stability (unclamped). */
        float stability;
        /** The colony's income before multipliers: industries' income plus exports (ViableSlaveLaborCondition.getGrossIncome). */
        float gross;
        /** Drug export income a month. */
        float drugExport;
        /** Free port and Commerce stability penalties now, positive (SmugglersHavenCondition.calculatePenaltiesToNullify). */
        float portPenalty;
        /** This colony is where your production is delivered. */
        boolean gathering;
        /** Ground defense structures in the engine's units (Ground Defenses 1, Heavy Batteries 2, Planetary Shield 2), 0 without any. */
        float defense;
        boolean maxSize;
        /** Any AI core in a building, or an AI core administrator (Pilgrim World). */
        boolean cores;
        /** The vault still has loot (Forgotten Vault-World). */
        boolean vaultOpen = true;
        /** Vault loot a month, credits (estimate). */
        float vaultLoot;
        /** Building ids here the game counts as industries. */
        final Set<String> industries = new HashSet<String>();

        Facts copy() {
            Facts f = new Facts();
            f.size = size; f.stability = stability; f.gross = gross; f.drugExport = drugExport; f.portPenalty = portPenalty;
            f.gathering = gathering; f.defense = defense; f.maxSize = maxSize; f.cores = cores; f.vaultOpen = vaultOpen; f.vaultLoot = vaultLoot;
            f.industries.addAll(industries);
            return f;
        }
    }

    // ---------------- one condition's rule ----------------

    /** What one condition does with a set of buildings: the engine's effects, output per good on one building, and a short text. */
    static final class Fx {
        final float[] fx = new float[7];
        final Map<String, Integer> units = new LinkedHashMap<String, Integer>();
        boolean active;
        String text = "";
    }

    static boolean has(Set<String> have, String... ids) {
        for (String id : ids) if (have.contains(id)) return true;
        return false;
    }

    static boolean met(String[][] needs, Set<String> have) {
        if (needs.length == 0) return false;
        for (String[] grp : needs) if (!has(have, grp)) return false;
        return true;
    }

    static int clampStability(float s) { return Math.max(0, Math.min(10, Math.round(s))); }

    /** Defense units from a ground defense multiplier: x1.5 is 0.5, x2 is 1, x0.5 is -1 (the engine's units). Pure. */
    static float defenseUnits(float mult) { return mult >= 1f ? mult - 1f : mult > 0f ? -(1f / mult - 1f) : -3f; }

    /**
     * Pure: what condition `cond` does at a colony with these facts and buildings (have: built or under way; industries: which of
     * them the game counts as industries), by the mod's own rules and numbers (CAConst, Autopilot's copies as fallback).
     */
    static Fx effect(String cond, Facts f, Set<String> have) {
        Fx x = new Fx();
        Cond c = TABLE.get(cond);
        if (c == null) return x;
        if (cond.equals(DEGENERATE)) {
            if (!met(c.needs, have)) return x;
            x.active = true;
            int arm = Math.round(k("DegenerateVolatilesCondition", "ARMAMENTS_SUPPLY", 2f)), mach = Math.round(k("DegenerateVolatilesCondition", "MACHINERY_SUPPLY", 2f));
            x.units.put("hand_weapons", arm); x.units.put("heavy_machinery", mach);
            x.fx[1] -= k("DegenerateVolatilesCondition", "STABILITY_PENALTY", 1f);
            x.fx[2] -= k("DegenerateVolatilesCondition", "ACCESSIBILITY_PENALTY", 0.15f);
            x.text = "Orbital Works +" + arm + " heavy armaments and +" + mach + " heavy machinery, stability -1, accessibility -15%";
        } else if (cond.equals(DIAMOND)) {
            if (!met(c.needs, have)) return x;
            x.active = true;
            if (!f.maxSize) x.fx[6] += DIAMOND_GROWTH;
            x.text = f.maxSize ? "the hazard pay effect (the colony is at its size limit)" : "the hazard pay growth effect at no cost";
        } else if (cond.equals(RADIOLYTIC)) {
            if (!met(c.needs, have)) return x;
            x.active = true;
            float mult = k("RadiolyticCatalystCondition", "DRUG_EXPORT_MULT", 3f);
            x.fx[0] += Math.max(0f, f.drugExport) * (mult - 1f);
            x.text = f.drugExport > 0f ? "drug exports x" + Math.round(mult) + ", about +" + Misc0.k(f.drugExport * (mult - 1f)) + " a month"
                    : "drug exports x" + Math.round(mult) + " (the colony exports no drugs now)";
        } else if (cond.equals(CALDERA)) {
            float up = k("HollowedCalderaGarrisonsCondition", "DEFENSE_MULT", 1.5f), down = k("HollowedCalderaGarrisonsCondition", "DEFENSE_PENALTY_MULT", 0.5f);
            x.active = met(c.needs, have);
            x.fx[3] += defenseUnits(x.active ? up : down);
            x.text = "ground defense x" + (x.active ? up : down);
        } else if (cond.equals(RESORT)) {
            if (!met(c.needs, have)) return x;
            x.active = true;
            float per = k("VacationParadiseResortCondition", "INCOME_PER_STABILITY", 0.05f);
            int diff = clampStability(f.stability) - 5;
            x.fx[0] += Math.max(0f, f.gross) * diff * per;
            x.fx[3] -= 1f + Math.max(0f, f.defense);
            x.text = "income " + (diff >= 0 ? "+" : "") + Math.round(diff * per * 100f) + "% at stability " + clampStability(f.stability) + ", ground defense x0";
        } else if (cond.equals(SMUGGLERS)) {
            if (!met(c.needs, have)) return x;
            x.active = true;
            x.fx[1] += Math.max(0f, f.portPenalty);
            x.text = f.portPenalty > 0f ? "free port and Commerce stability penalties cancelled (+" + Math.round(f.portPenalty) + ")" : "free port stability penalty cancelled";
        } else if (cond.equals(SLAVES)) {
            if (!met(c.needs, have)) return x;
            x.active = true;
            float pct = k("ViableSlaveLaborCondition", "INCOME_BONUS_PERCENT", 50f);
            x.fx[0] += Math.max(0f, f.gross) * pct / 100f;
            x.fx[1] += k("ViableSlaveLaborCondition", "STABILITY_PENALTY", -3f);
            x.text = "income +" + Math.round(pct) + "%, stability -3";
        } else if (cond.equals(MAGMATIC)) {
            if (!met(c.needs, have)) return x;
            x.active = true;
            if (f.gathering) x.fx[0] += MAGMA_VALUE;
            x.text = f.gathering ? "new production hulls lose their d-mods (estimate)" : "only at the production gathering point";
        } else if (cond.equals(MISSILES)) {
            if (!met(c.needs, have)) return x;
            x.active = true;
            x.fx[4] += MISSILE_FLEET;
            x.text = Math.round(k("GroundMissileDefenseCondition", "MISSILES", 500f)) + " missiles into battles in the system";
        } else if (cond.equals(REACTOR) || cond.equals(OBSERVATORY) || cond.equals(SIGNAL)) {
            x.active = met(c.needs, have);   // no colony value: the burn, surveys and escapes serve you; the reactor is a rule
            x.text = cond.equals(REACTOR) ? "your fleets +2 burn in the system" : cond.equals(OBSERVATORY) ? "planet surveys" : "an encounter escape";
        } else if (cond.equals(HYDROCARBON)) {
            if (!has(have, LI)) return x;
            x.active = true;
            int bonus = Math.round(k("HydrocarbonFractionationCondition", "LIGHT_INDUSTRY_BONUS", 2f));
            if (f.size > 0) x.units.put("domestic_goods", bonus);
            if (f.size > 2) x.units.put("luxury_goods", bonus);
            x.text = "Light Industry +" + bonus + " on each output";
        } else if (cond.equals(SYNDICATE)) {
            if (!has(have, SPACEPORT, MEGAPORT)) return x;
            x.active = true;
            int n = Math.max(1, f.size - 2);
            x.units.put("organs", n);
            x.text = "the port +" + n + " organs";
        } else if (cond.equals(PILGRIM)) {
            int n = 0;
            for (String id : have) if (f.industries.contains(id) && !id.equals(POPULATION)) n++;
            if (n == 0) return x;
            x.active = true;
            float sign = f.cores ? -1f : 1f;
            x.fx[1] += sign * n * k("PilgrimWorldCondition", "STABILITY_PER_INDUSTRY", 1f);
            x.fx[2] += sign * n * k("PilgrimWorldCondition", "ACCESSIBILITY_PER_INDUSTRY", 0.2f);
            x.text = f.cores ? n + " industries: stability -" + n + ", accessibility -" + (20 * n) + "% (AI cores here)"
                    : n + " industries: stability +" + n + ", accessibility +" + (20 * n) + "%";
        } else if (cond.equals(VAULT)) {
            if (!has(have, TECHMINING) || !f.vaultOpen) return x;
            x.active = true;
            x.fx[1] += k("ForgottenVaultWorldCondition", "STABILITY_PENALTY", -3f);
            x.fx[2] += k("ForgottenVaultWorldCondition", "ACCESSIBILITY_PENALTY", -0.5f);
            x.fx[0] += Math.max(0f, f.vaultLoot);
            x.text = "vault loot about " + Misc0.k(f.vaultLoot) + " a month (estimate), stability -3, accessibility -50%";
        }
        return x;
    }

    /** Number text without the game (offline checks). */
    static final class Misc0 {
        static String k(float v) {
            try { return Misc.getDGSCredits(v); } catch (Throwable t) { return String.format(Locale.ROOT, "%,d", Math.round(v)); }
        }
    }

    // ---------------- reading a colony ----------------

    /** One colony's Rogue Planets state for one evaluation. */
    public static final class Ctx {
        MarketAPI m;
        Map<String, Integer> deficit;
        final Set<String> conds = new LinkedHashSet<String>();
        final Set<String> have = new HashSet<String>();
        Facts f;
        /** Industries working now that a quake can hit (Supercritical Crust). */
        int disruptable;
    }

    /** The Rogue Planets conditions at a colony, in table order. */
    static Set<String> conds(MarketAPI m) {
        Set<String> out = new LinkedHashSet<String>();
        if (m == null) return out;
        try {
            for (MarketConditionAPI c : m.getConditions()) if (TABLE.containsKey(c.getId())) out.add(c.getId());
        } catch (Throwable ignored) { }
        return out;
    }

    /** Whether the colony has any Rogue Planets condition (the mod enabled). */
    public static boolean any(MarketAPI m) { return on() && !conds(m).isEmpty(); }

    static boolean hasCond(MarketAPI m, String cond) {
        if (!on() || m == null) return false;
        try { return m.hasCondition(cond); } catch (Throwable t) { return false; }
    }

    static boolean working(MarketAPI m, String id) {
        try { Industry ind = m.getIndustry(id); return ind != null && ind.isFunctional(); } catch (Throwable t) { return false; }
    }

    static boolean present(MarketAPI m, String id) {
        try { return m.getIndustry(id) != null; } catch (Throwable t) { return false; }
    }

    static Facts facts(MarketAPI m) {
        Facts f = new Facts();
        try { f.size = m.getSize(); } catch (Throwable ignored) { }
        f.stability = CAStability.now(m);
        try {
            float gross = 0f;
            for (Industry ind : m.getIndustries()) gross += ind.getIncome().getModifiedValue();
            for (CommodityOnMarketAPI c : m.getAllCommodities()) gross += c.getExportIncome();
            f.gross = gross;
        } catch (Throwable ignored) { }
        try { CommodityOnMarketAPI d = m.getCommodityData("drugs"); f.drugExport = d == null ? 0f : d.getExportIncome(); } catch (Throwable ignored) { }
        f.portPenalty = portPenalty(m);
        try { f.gathering = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint() == m; } catch (Throwable ignored) { }
        f.defense = (present(m, "grounddefenses") ? 1f : 0f) + (present(m, BATTERIES) ? 2f : 0f) + (present(m, "planetaryshield") ? 2f : 0f);
        try { f.maxSize = m.getSize() >= Misc.getMaxMarketSize(m); } catch (Throwable ignored) { }
        try {
            for (Industry ind : m.getIndustries()) {
                if (ind.getAICoreId() != null) f.cores = true;
                if (ind.isIndustry()) f.industries.add(ind.getId());
            }
            if (m.getAdmin() != null && m.getAdmin().isAICore()) f.cores = true;
        } catch (Throwable ignored) { }
        try {
            boolean vast = m.hasCondition("ruins_vast");
            int months = m.getMemoryWithoutUpdate().getInt(VAULT_MONTHS), max = vast ? 30 : 18;
            f.vaultOpen = months < max;
            f.vaultLoot = vaultLoot(vast, months / (float) max, price("metals"), price("supplies"), price("fuel"), price("heavy_machinery"), price("rare_metals"));
        } catch (Throwable ignored) { }
        return f;
    }

    static float price(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getBasePrice(); } catch (Throwable t) { return 0f; }
    }

    /**
     * Pure: the vault's two monthly rolls of goods (ForgottenVaultWorldCondition.generateDistinctVaultRoll, averages), at
     * CARGO_SHARE of base price, decayed by progress. Weapons, blueprints, cores and hulls are left out.
     */
    static float vaultLoot(boolean vast, float progress, float pMetals, float pSupplies, float pFuel, float pMachinery, float pRare) {
        float decay = Math.max(0.2f, 1f - Math.max(0f, Math.min(1f, progress)) * 0.85f);
        float metals = 60f + (vast ? 60f : 30f), supplies = 20f + (vast ? 20f : 12.5f), fuel = supplies, machinery = 2f + (vast ? 3f : 2f);
        float rare = (vast ? 1f : 0.6f) * (10f + (vast ? 17.5f : 10f));
        float roll = (metals * pMetals + supplies * pSupplies + fuel * pFuel + machinery * pMachinery + rare * pRare) * decay;
        return 2f * roll * CARGO_SHARE;
    }

    /** SmugglersHavenCondition.calculatePenaltiesToNullify: negative flat stability mods named for the free port or Commerce. */
    static float portPenalty(MarketAPI m) {
        float total = 0f;
        try {
            Map<String, MutableStat.StatMod> mods = m.getStability().getFlatMods();
            if (mods != null) for (Map.Entry<String, MutableStat.StatMod> e : mods.entrySet()) {
                String key = e.getKey() == null ? "" : e.getKey().toLowerCase(Locale.ROOT);
                String desc = e.getValue().getDesc() == null ? "" : e.getValue().getDesc().toLowerCase(Locale.ROOT);
                float v = e.getValue().getValue();
                if (v < 0f && (key.contains("free_port") || desc.contains("free port") || key.contains("freeport") || key.contains("commerce") || desc.contains("commerce")))
                    total += -v;
            }
            if (m.isFreePort() && total == 0f) total = 3f;
        } catch (Throwable ignored) { }
        return total;
    }

    /** One colony's state for an evaluation; null without the mod or without its conditions here. */
    public static Ctx context(MarketAPI m, Map<String, Integer> deficit) {
        if (!on() || m == null) return null;
        Set<String> cs = conds(m);
        if (cs.isEmpty()) return null;
        Ctx c = new Ctx();
        c.m = m; c.deficit = deficit; c.conds.addAll(cs);
        try {
            for (Industry ind : m.getIndustries()) {
                if (ind.isHidden()) continue;
                c.have.add(ind.getId());
                if (!POPULATION.equals(ind.getId()) && ind.isFunctional()) c.disruptable++;
            }
        } catch (Throwable ignored) { }
        c.f = facts(m);
        return c;
    }

    // ---------------- build values ----------------

    /** Units of a good valued the way the engine values a synergy's: on the pick's line or the colony's biggest source; illegal goods only for the colony's own shortage. */
    static float unitsValue(MarketAPI m, CAEngine.Pick p, String com, int n, Map<String, Integer> deficit) {
        if (n == 0 || m == null) return 0f;
        if (illegal(m, com)) {
            int local = 0;
            try { local = Math.max(0, m.getCommodityData(com).getDeficitQuantity()); } catch (Throwable ignored) { }
            int count = n > 0 ? Math.min(n, local) : -Math.min(-n, local);
            return price(com) * CAEngine.unitValue * count * CATuning.SHORTAGE_FACTOR;
        }
        try { return CAValue.synergy(m, p, com, n, deficit == null ? new HashMap<String, Integer>() : deficit); } catch (Throwable t) { return 0f; }
    }

    static boolean illegal(MarketAPI m, String com) {
        try { return !m.isFreePort() && m.getFaction() != null && m.getFaction().isIllegal(com); } catch (Throwable t) { return false; }
    }

    /**
     * What building p does to this colony's Rogue Planets conditions, added to its effects (after its upkeep, before finish):
     * each condition before and after, the difference in fx and output, one line per condition it starts, ends or changes. Then
     * the colony-wide rules: half the build time (Geographically Efficient Zoning), the crustquake share (Supercritical Crust),
     * half the fleet value (Chrono-Spatial Dilation), and upkeep is already in p (CAEngine.baseUpkeep).
     */
    public static void score(Ctx c, CAEngine.Pick p) {
        if (c == null || p == null) return;
        Set<String> after = new HashSet<String>(c.have);
        Facts fa = c.f.copy();
        if (p.upgrade && p.existing != null) { after.remove(p.existing.getId()); fa.industries.remove(p.existing.getId()); }
        after.add(p.id);
        if (industry(p.id)) fa.industries.add(p.id);
        fa.stability += p.fx[1];
        fa.defense += Math.max(0f, p.fx[3]);   // a defense structure built at a resort is multiplied by 0 too
        for (String cond : c.conds) {
            Fx b = effect(cond, c.f, c.have), a = effect(cond, fa, after);
            float change = 0f;
            for (int i = 0; i < 7; i++) {
                float d = a.fx[i] - b.fx[i];
                p.fx[i] += d;
                change += Math.abs(d);
            }
            Set<String> goods = new LinkedHashSet<String>(a.units.keySet());
            goods.addAll(b.units.keySet());
            for (String com : goods) {
                int n = units(a.units, com) - units(b.units, com);
                if (n == 0) continue;
                float v = unitsValue(c.m, p, com, n, c.deficit);
                p.fx[0] += v;
                change += Math.abs(v);
                if (n > 0) CAEngine.addUnits(p.added, com, n);   // the floors and the endgame's chains read p.added
            }
            String name = TABLE.get(cond).name;
            if (!b.active && a.active) p.lines.add(name + ": starts. " + a.text);
            else if (b.active && !a.active) p.lines.add(name + ": ends. " + b.text);
            else if (a.active && change > 0.001f) p.lines.add(name + ": " + a.text);
        }
        if (c.conds.contains(ZONING) && p.days > 0f) {
            p.days *= 0.5f;
            p.lines.add("Geographically Efficient Zoning: built in half the time");
        }
        if (c.conds.contains(CRUST)) {
            int n = c.disruptable + (p.upgrade ? 0 : 1);
            float share = crustShare(n);
            if (share > 0f) {
                float gross = p.fx[0] + p.upkeep;
                if (gross > 0f) p.fx[0] -= gross * share;
                for (int i = 1; i < 7; i++) if (p.fx[i] > 0f) p.fx[i] -= p.fx[i] * share;
                p.lines.add("Supercritical Crust: one building a month is disrupted 30 days. Counted at " + Math.round((1f - share) * 100f) + "%");
            }
        }
        if (c.conds.contains(CHRONO) && p.fx[4] > 0f) {
            p.fx[4] *= 0.5f;
            p.lines.add("Chrono-Spatial Dilation: local fleets at -100% ship quality. Fleet value counted at half");
        }
    }

    static int units(Map<String, Integer> m, String com) { Integer u = m.get(com); return u == null ? 0 : u; }

    /** Pure: the share of its working time a building loses to crustquakes with n buildings that can be hit (one a month, 30 days). */
    static float crustShare(int n) { return n <= 0 ? 0f : 1f / n; }

    // ---------------- keep values ----------------

    /**
     * What a building keeps going through Rogue Planets conditions, as the engine's effects (for CAValue.keepDetail): each
     * condition with it minus without it. Output on another building is what that line would lose (CAValue.keeping).
     */
    public static float[] keepFx(MarketAPI m, Industry ind, Map<String, Integer> deficit) {
        float[] out = new float[7];
        Ctx c = context(m, deficit);
        if (c == null || ind == null || !c.have.contains(ind.getId())) return out;
        Set<String> without = new HashSet<String>(c.have);
        without.remove(ind.getId());
        Industry was = CAValue.keeping;
        CAValue.keeping = ind;
        try {
            for (String cond : c.conds) {
                Fx with = effect(cond, c.f, c.have), wo = effect(cond, c.f, without);
                for (int i = 0; i < 7; i++) out[i] += with.fx[i] - wo.fx[i];
                Set<String> goods = new LinkedHashSet<String>(with.units.keySet());
                goods.addAll(wo.units.keySet());
                for (String com : goods) {
                    int n = units(with.units, com) - units(wo.units, com);
                    if (n != 0) out[0] += unitsValue(m, null, com, n, deficit);
                }
            }
        } finally { CAValue.keeping = was; }
        return out;
    }

    /** The stability a building brings through Rogue Planets conditions now (Pilgrim World +1, a synergy's -1 or -3); 0 without. */
    public static float stabilityShare(MarketAPI m, Industry ind) {
        Ctx c = context(m, null);
        if (c == null || ind == null || !c.have.contains(ind.getId())) return 0f;
        Set<String> without = new HashSet<String>(c.have);
        without.remove(ind.getId());
        float v = 0f;
        for (String cond : c.conds) v += effect(cond, c.f, c.have).fx[1] - effect(cond, c.f, without).fx[1];
        return v;
    }

    // ---------------- upkeep ----------------

    /** Pure: the upkeep multiplier these conditions put on building id (vaultOpen: the vault still has loot). */
    static float upkeepMult(Set<String> conds, String id, boolean vaultOpen) {
        float mult = 1f;
        if (conds.contains(LEYLINES)) mult *= k("GeothermalLeylinesCondition", "UPKEEP_MULT", 0.5f);
        if (conds.contains(CHRONO)) mult *= k("ChronoSpatialDilationCondition", "UPKEEP_MULT", 2f);
        if (conds.contains(HYDROPONICS) && POPULATION.equals(id)) mult *= k("ClosedLoopHydroponicsCondition", "POP_UPKEEP_MULT", 2f);
        if (conds.contains(MEGALITHIC) && MINING.equals(id)) mult *= k("MegalithicOutcropsCondition", "UPKEEP_MULT", 0f);
        if (conds.contains(VAULT) && TECHMINING.equals(id) && vaultOpen) mult *= k("ForgottenVaultWorldCondition", "UPKEEP_MULT", 10f);
        return mult;
    }

    /** The upkeep multiplier Rogue Planets puts on building id at this colony (CAEngine.baseUpkeep); 1 without the mod. */
    public static float upkeepMult(MarketAPI m, String id) {
        if (!on() || m == null || id == null) return 1f;
        Set<String> cs = conds(m);
        if (cs.isEmpty()) return 1f;
        boolean open = true;
        if (cs.contains(VAULT)) {
            try { open = m.getMemoryWithoutUpdate().getInt(VAULT_MONTHS) < (m.hasCondition("ruins_vast") ? 30 : 18); } catch (Throwable ignored) { }
        }
        return upkeepMult(cs, id, open);
    }

    /** Megalithic Outcrops: Mining's drugs and heavy machinery demand is this much lower (CAEngine.effects). */
    public static int miningDemandCut(MarketAPI m) {
        if (!hasCond(m, MEGALITHIC)) return 0;
        return Math.round(-k("MegalithicOutcropsCondition", "DEMAND_REDUCTION", -2f));
    }

    /** Hydrocarbon Fractionation: what a Mining deposit good comes out as here (volatiles become organics). */
    public static String minedAs(MarketAPI m, String industry, String com) {
        if ("volatiles".equals(com) && MINING.equals(industry) && hasCond(m, HYDROCARBON)) return "organics";
        return com;
    }

    /** Hydrocarbon Fractionation turns this colony's volatiles into organics (for the plan). */
    public static boolean volatilesAsOrganics(MarketAPI m) { return hasCond(m, HYDROCARBON); }

    // ---------------- rules ----------------

    /**
     * Pure: why Autopilot never builds `id` (upgraded from `from`, or null) at a colony with these conditions and buildings, or
     * null. industry: the game counts `id` as an industry.
     */
    static String forbids(Set<String> conds, Set<String> have, String id, String from, boolean industry) {
        if (id == null) return null;
        if (conds.contains(FRAGILE) && industry && !FARMING.equals(id))
            return "Fragile Biosphere: Farming is the only industry allowed. The game takes others down";
        if (conds.contains(REACTOR)) {
            boolean both = have.contains(REFINING) && have.contains(FUEL);
            if ((REFINING.equals(id) && have.contains(FUEL)) || (FUEL.equals(id) && have.contains(REFINING)))
                return "Volatile Additive Reactor: the second of the pair is yours to build. If either stops working, every building is disrupted 180 days";
            if (both && from != null && (REFINING.equals(from) || FUEL.equals(from)))
                return "Volatile Additive Reactor: Refining and Fuel Production stay as they are while the reactor runs";
        }
        if (conds.contains(VAULT) && TECHMINING.equals(id)) return "Forgotten Vault-World: the vault excavation is yours to start";
        return null;
    }

    static Set<String> built(MarketAPI m) {
        Set<String> out = new HashSet<String>();
        try { for (Industry ind : m.getIndustries()) out.add(ind.getId()); } catch (Throwable ignored) { }
        return out;
    }

    /** Why Autopilot never builds `id` here (upgraded from `from`, or null), or null. */
    public static String forbids(MarketAPI m, String id, Industry from) {
        if (!on() || m == null) return null;
        Set<String> cs = conds(m);
        if (cs.isEmpty()) return null;
        return forbids(cs, built(m), id, from == null ? null : from.getId(), industry(id));
    }

    /** The context's own forbids (no extra reads). */
    static String forbids(Ctx c, String id, Industry from) {
        if (c == null) return null;
        return forbids(c.conds, c.have, id, from == null ? null : from.getId(), industry(id));
    }

    /**
     * Pure: why a building must stay, or null. hard: taking it down or replacing it breaks the mod's content (the running
     * reactor); otherwise Autopilot does not take it down by value (the vault's Tech-Mining), and the 0-stability rule may.
     */
    static String keepReason(Set<String> conds, Set<String> have, String id, boolean hardOnly) {
        if (conds.contains(REACTOR) && (REFINING.equals(id) || FUEL.equals(id)) && have.contains(REFINING) && have.contains(FUEL))
            return "Volatile Additive Reactor: taking down or replacing Refining or Fuel Production melts the reactor down";
        if (!hardOnly && conds.contains(VAULT) && TECHMINING.equals(id)) return "Forgotten Vault-World: the vault excavation is yours to run";
        return null;
    }

    /** Why this building must stay at this colony, or null (hardOnly: only what breaks the mod's content). */
    public static String keepReason(MarketAPI m, Industry ind, boolean hardOnly) {
        if (!on() || m == null || ind == null) return null;
        Set<String> cs = conds(m);
        if (cs.isEmpty()) return null;
        return keepReason(cs, built(m), ind.getId(), hardOnly);
    }

    /** Pure: the lowest stability Autopilot leaves a colony with these conditions at (0: no floor of their own). */
    static float stabilityFloor(Set<String> conds) {
        float f = 0f;
        if (conds.contains(SLAVES)) f = Math.max(f, k("ViableSlaveLaborCondition", "STABILITY_THRESHOLD", 5f) + 1f);
        if (conds.contains(CRYO)) f = Math.max(f, 6f);   // SupercooledCryoSinksCondition.isHighStability: above 5
        return f;
    }

    /** Viable Slave Labor and Supercooled Cryo-Sinks: stability 6 or more at this colony; 0 elsewhere. */
    public static float stabilityFloor(MarketAPI m) {
        if (!on() || m == null) return 0f;
        Set<String> cs = conds(m);
        return cs.isEmpty() ? 0f : stabilityFloor(cs);
    }

    /** Fragile Biosphere: Farming is the only industry the game allows here. */
    public static boolean onlyFarming(MarketAPI m) { return hasCond(m, FRAGILE); }

    /** Pilgrim World: any AI core here turns its bonus into a penalty, so Autopilot places none. */
    public static boolean noCores(MarketAPI m) { return hasCond(m, PILGRIM); }

    /** Trash King: the king governs (or can still be called back), so Autopilot never seats or moves an administrator here. */
    public static boolean adminLocked(MarketAPI m) {
        if (!hasCond(m, KING)) return false;
        try { return !KING_EXILED.equals(m.getMemoryWithoutUpdate().getString(KING_STATE)); } catch (Throwable t) { return true; }
    }

    /** Diamond Core with Mining and a Megaport working: hazard pay's effect is built in and the toggle is held off. */
    public static boolean hazardPayBuiltIn(MarketAPI m) {
        return hasCond(m, DIAMOND) && working(m, MINING) && working(m, MEGAPORT);
    }

    /** Smugglers' Haven with Commerce and Heavy Industry or Orbital Works working: the free port costs no stability. */
    public static boolean portPenaltyCovered(MarketAPI m) {
        return hasCond(m, SMUGGLERS) && working(m, COMMERCE) && (working(m, HI) || working(m, OW));
    }

    /** The Afterlight (the story's ship): never shipped between storages. */
    public static boolean storyShip(com.fs.starfarer.api.fleet.FleetMemberAPI fm) {
        if (!on() || fm == null) return false;
        try { if (fm.getHullId() != null && fm.getHullId().startsWith(AFTERLIGHT_HULL)) return true; } catch (Throwable ignored) { }
        try {
            Object id = Global.getSector().getMemoryWithoutUpdate().get(AFTERLIGHT_MEMBER);
            return id != null && id.equals(fm.getId());
        } catch (Throwable t) { return false; }
    }

    // ---------------- the plan and the floors ----------------

    /** The plan without the steps these conditions forbid (Fragile Biosphere's industries, the reactor's second building, the vault's Tech-Mining). */
    public static List<CAPlan.Step> planFilter(MarketAPI m, List<CAPlan.Step> steps) {
        if (!on() || m == null || steps == null) return steps;
        Set<String> cs = conds(m);
        if (cs.isEmpty()) return steps;
        Set<String> have = built(m);
        List<CAPlan.Step> out = new ArrayList<CAPlan.Step>(steps.size());
        for (CAPlan.Step s : steps) {
            if (s.kind == CAPlan.Kind.BUILD && !"station".equals(s.id) && forbids(cs, have, s.id, null, industry(s.id)) != null) continue;
            out.add(s);
        }
        return out;
    }

    /**
     * Pure: what a host's conditions add to its rank for a floor producer (CAFloors.host): Degenerate Volatiles with Mining for
     * Heavy Industry and Orbital Works, Ship Graveyard for Heavy Industry, Orbital Works and Refining, Magmatic Antimatter Forge
     * for Heavy Industry with Fuel Production there and for Fuel Production with a forge there.
     */
    static float hostBonus(String building, Set<String> conds, Set<String> has) {
        if (conds == null || conds.isEmpty()) return 0f;
        float v = 0f;
        boolean forge = HI.equals(building) || OW.equals(building);
        if (forge && conds.contains(DEGENERATE) && has.contains(MINING)) v += 40f;
        if ((forge || REFINING.equals(building)) && conds.contains(GRAVEYARD)) v += 30f;
        if (forge && conds.contains(MAGMATIC) && has.contains(FUEL)) v += 20f;
        if (FUEL.equals(building) && conds.contains(MAGMATIC) && (has.contains(HI) || has.contains(OW))) v += 20f;
        return v;
    }

    /** Pure: a floor producer this host's conditions forbid (CAFloors.host). */
    static boolean hostForbids(String building, String base, Set<String> conds, Set<String> has, boolean industry) {
        return conds != null && !conds.isEmpty() && forbids(conds, has, building, base, industry) != null;
    }

    // ---------------- Auto-expansion ----------------

    /**
     * What a planet's Rogue Planets conditions add to a new colony a month at `size` (CAExpansion): fuel and crustquake goods
     * to storage at CARGO_SHARE of base price, less the stability they take at the goal's weight (Automated Shipyards,
     * Clandestine Harvesting Syndicate). Fragile Biosphere's limit is in the deposits (onlyFarming). names: short reasons.
     */
    public static float prospect(MarketAPI m, int size, float stabilityWeight, List<String> names) {
        if (!on() || m == null) return 0f;
        Set<String> cs = conds(m);
        if (cs.isEmpty()) return 0f;
        float v = 0f;
        if (cs.contains(ANOMALY)) {
            float fuel = anomalyFuel(size);
            v += fuel * price("fuel") * CARGO_SHARE;
            if (fuel > 0f && names != null) names.add("antimatter fuel");
        }
        if (cs.contains(CRUST)) {
            v += size * (k("SupercriticalCrustCondition", "ORE_PER_SIZE", 100f) * price("ore") + k("SupercriticalCrustCondition", "RARE_ORE_PER_SIZE", 50f) * price("rare_ore")
                    + k("SupercriticalCrustCondition", "LUXURY_PER_SIZE", 25f) * price("luxury_goods")) * CARGO_SHARE;
            if (names != null) names.add("crustquake goods");
        }
        if (cs.contains(SHIPYARDS)) v -= k("AutomatedShipyardsCondition", "STABILITY_PENALTY", 3f) * stabilityWeight;
        if (cs.contains(SYNDICATE)) v -= -k("ClandestineHarvestingSyndicateCondition", "STABILITY_PENALTY", -2f) * stabilityWeight;
        return v;
    }

    /** Pure: Antimatter Anomaly's fuel a month at a colony size (AntimatterAnomalyCondition.fuelForSize). */
    static float anomalyFuel(int size) {
        float per = k("AntimatterAnomalyCondition", "FUEL_PER_STEP", 100f);
        int min = Math.round(k("AntimatterAnomalyCondition", "MIN_SIZE", 3f)), max = Math.round(k("AntimatterAnomalyCondition", "MAX_SIZE", 6f));
        if (size < min) return 0f;
        return (Math.min(size, max) - (min - 1)) * per;
    }

    // ---------------- daily ----------------

    /**
     * Viable Slave Labor: three month-ends in a row at stability 5 or lower and the colony goes to pirates. From the first month
     * of unrest the log says so; at the second, an Auto colony whose Commerce feeds the synergy has it taken down when the
     * synergy's -3 ending lifts stability above 5. Nothing is spent.
     */
    public static void daily(CAData d, List<MarketAPI> auto) {
        if (!on() || auto == null) return;
        for (MarketAPI m : auto) {
            try {
                if (!m.hasCondition(SLAVES)) continue;
                int months = m.getMemoryWithoutUpdate().getInt(UNREST);
                if (months <= 0) { CALog.forgetAll("rogue:unrest:" + m.getId(), null); continue; }
                float stab = CAStability.now(m);
                int limit = Math.round(k("ViableSlaveLaborCondition", "REBELLION_MONTHS", 3f));
                if (CALog.once("rogue:unrest:" + m.getId() + ":" + months))
                    CALog.advise(m.getName() + ": Viable Slave Labor. Stability " + Math.round(stab) + ", month " + months + " of " + limit
                            + " at 5 or lower. The colony goes to pirates after month " + limit + ".", true);
                if (months < limit - 1 || d.colony(m.getId()).mode != CAData.Mode.AUTO) continue;
                Industry com = m.getIndustry(COMMERCE);
                boolean synergy = working(m, COMMERCE) && (working(m, MB) || working(m, HC));
                float lift = com == null ? 0f : CAStability.takeDownDelta(m, com);   // the synergy's -3 ending is in it (stabilityShare)
                if (com == null || !synergy || d.colony(m.getId()).locks.contains(COMMERCE) || stab + lift <= k("ViableSlaveLaborCondition", "STABILITY_THRESHOLD", 5f)) continue;
                CAStores.emptyInto(m, com);
                m.removeIndustry(COMMERCE, MarketAPI.MarketInteractionMode.REMOTE, false);
                CATombstone.add(m.getId(), COMMERCE, CATombstone.SHED, CATombstone.sunk(m.getId(), COMMERCE));
                CAMarket.invalidate();
                CALog.did(m.getName() + ": Took down Commerce. Viable Slave Labor: stability " + Math.round(stab) + " at " + months
                        + " month-ends in a row. Its -3 stability ends.", true);
            } catch (Throwable t) { CAStep.warn("Rogue Planets step", "slave labor check failed at " + m.getName(), t); }
        }
    }

    // ---------------- colony panel ----------------

    /** The colony's Rogue Planets conditions: what each does now and how Autopilot handles it. */
    public static void render(TooltipMakerAPI t, MarketAPI m) {
        java.awt.Color gray = Misc.getGrayColor();
        Ctx c = context(m, CAEngine.empireDeficits());
        if (c == null) { t.addPara("No Rogue Planets conditions here.", gray, 6f); return; }
        for (String cond : c.conds) {
            Cond row = TABLE.get(cond);
            Fx x = effect(cond, c.f, c.have);
            String state = row.needs.length == 2 ? (x.active ? "active" : "inactive") : x.active ? "active" : null;
            t.addPara(CALog.esc(row.name + ": " + (state == null ? "" : state + ". ") + row.does), 6f);
            t.addPara(CALog.esc(row.handled), gray, 2f);
        }
    }
}
