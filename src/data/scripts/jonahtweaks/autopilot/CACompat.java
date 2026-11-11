package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Load-time health check of every mod integration, so a mod update that renames something shows up in Diagnostics
 * instead of silently doing nothing. Each integration lists the mod it serves, the version it was written against,
 * the ids it relies on (buildings, conditions, items, commodities) and, for the reflection-based ones, the check that
 * every reflected name still resolves. Status:
 * - OK: the version it was written against, everything found.
 * - Newer: another version, but every id and name still found; it keeps working, and its estimates are trusted a
 *   little less (CAEngine confidence) until they're calibrated against your colonies.
 * - Partly off: some ids are gone; those parts can't act (the game has no such building or item), the rest works.
 * - Off: its reflected names can't be read; it does nothing.
 * Only reflection-only classes are linked here, so this never loads a class that needs an absent mod.
 */
public class CACompat {
    public enum Status { OK, NEWER, PARTIAL, OFF, ABSENT }

    public static class Entry {
        public final String mod, name, tested;
        final String[] industries, conditions, items, commodities;
        final Callable<Boolean> link;
        public Status status = Status.ABSENT;
        public String version = "";
        public List<String> missing = new ArrayList<String>();

        Entry(String mod, String name, String tested, String[] ind, String[] cond, String[] items, String[] com, Callable<Boolean> link) {
            this.mod = mod; this.name = name; this.tested = tested; this.industries = ind; this.conditions = cond; this.items = items; this.commodities = com; this.link = link;
        }
    }

    static final String[] NONE = new String[0];
    static String[] a(String... s) { return s; }

    static final List<Entry> ENTRIES = new ArrayList<Entry>();
    static {
        ENTRIES.add(new Entry("bmo", "Building Menu Overhaul", "2.1.0", NONE, NONE, NONE, NONE, () -> CAAotD.link()));
        ENTRIES.add(new Entry("aotd_vok", "Ashes of the Domain: Vaults of Knowledge", "5.0.7",
                a("pluto_station", "nidavelir_complex", "aotd_coronal_control"), a("aotd_coronal_market_cond", "aotd_colony_development_cond"),
                a("aotd_shrouded_nanoforge", "turing_engine"), a("purified_rare_metal", "domain_heavy_machinery"),
                () -> CASynergies.link() && CAHypershunt.link() && CAMegastructures.link() && CAResearch.link() && CADevelopment.link()));
        ENTRIES.add(new Entry("aotd_theory_of_toolbox", "Ashes of the Domain: Theory of Toolbox", "1.0.13", NONE, NONE, NONE, NONE, () -> CAContracts.link()));
        ENTRIES.add(new Entry("terraformingmadeeasy", "Terraforming Made Easy", "2.9.8",
                a("tme_atmosphere_regulator", "tme_geomorphology_station", "tme_terrestrial_engine", "tme_mineral_replicator", "tme_element_synthesizer",
                        "tme_agricultural_laboratory", "tme_unification_center"), NONE, NONE, NONE, null));
        ENTRIES.add(new Entry("IndEvo", "Industrial Evolution", "4.1.b",
                a("IndEvo_embassy", "IndEvo_PrivatePort", "IndEvo_AdAssem", "IndEvo_AdManuf", "IndEvo_ComForge", "IndEvo_AdInfra"), NONE, NONE, NONE, null));
        ENTRIES.add(new Entry("nexerelin", "Nexerelin", "0.12.2d", NONE, NONE, NONE, NONE, null));
        ENTRIES.add(new Entry("assortment_of_things", "Random Assortment of Things", "3.3.1", NONE, NONE, NONE, NONE, null));
        ENTRIES.add(new Entry("niko_stationAugments", "Station Augments", "2.4.3", NONE, NONE, NONE, NONE, null));
        ENTRIES.add(new Entry("EmergentThreats_IX_Revival", "Emergent Threats: IX Revival", "1.4.0", a("ix_fuel_production"), NONE, NONE, a("ix_antimatter_stabilizer"), null));
        ENTRIES.add(new Entry("Terraforming & Station Construction", "Terraforming & Station Construction", "10.0.9",
                a(CATasc.PROCESSOR, CATasc.REFLECTOR, "BOGGLED_DOMED_CITIES", "BOGGLED_GENELAB", "BOGGLED_CHAMELEON", "BOGGLED_MESOZOIC_PARK", "BOGGLED_KLETKA_SIMULATOR"),
                a("terraforming_controller"), NONE, a("domain_artifacts"), () -> CATasc.link()));
        ENTRIES.add(new Entry("GrandColonies", "Grand.Colonies", "2.1.b", NONE, a("GrandColonies_CrampedCondition"), NONE, NONE, () -> CAGrand.link()));
        ENTRIES.add(new Entry("alcoholism", "Substance.Abuse", "1.1.d", a("alcohol_brewery"), NONE, a("alcoholism_recipe_book", "alcoholism_stout_item"), a("alcoholism_stout_c"), null));
        ENTRIES.add(new Entry("vic", "Volkov Industrial Conglomerate", "1.6.3a", a("vic_antiEVCt1", "vic_antiEVCt2", "vic_antiEVCt3"), a("VIC_VBomb_scar"),
                a("vic_soil_nanites2"), a("vic_genetech"), null));
        ENTRIES.add(new Entry("roider", "Roider Union", "2.3.1", a("roider_dives", "roider_union_hq"), NONE, a(CAMods.CHARTER), NONE, null));
        // 0.2.38.0
        ENTRIES.add(new Entry("omega_core_everywhere", "Omega Core Everywhere", "1.2.2", NONE, NONE, NONE, a(CAOmega.CORE), () -> CAOmega.linkOce()));
        ENTRIES.add(new Entry("astral_ascension_renewed", "Astral Ascension Renewed", "1.0.1", NONE,
                a("population_11", "aar_arcology_world", "aar_ecumenopolis", "aar_crowned_world"), NONE, a(CAOmega.CORE), () -> CAOmega.adminPlugin() != null));
        ENTRIES.add(new Entry("niko_scavengableindustries", "Niko's Scavengable Industries", "1.0.0",
                a("NSI_blastCoreMining", "NSI_SpyBureau", "NSI_SpyOutpost", "NSI_cryosanctum", "NSI_outpostDefense", "NSI_ASBUplink", "NSI_expertDockworks"), NONE, NONE, NONE, null));
        ENTRIES.add(new Entry("JYD", "Junk Yard Dogs", "3.7.0", a("JYD_forge", "JYD_scrap"), NONE, NONE, NONE, null));
        ENTRIES.add(new Entry("CFT", "Carter's Freetraders", "3.3.1", a("CFT_biolab", "CFT_greyindustry"), NONE, NONE, NONE, null));
        ENTRIES.add(new Entry("vulpoids", "Vulpoids", "0.10.3", a("organfarms", "biofacility", "vulpoidagency"), NONE,
                a("vulpoid_biofactory", "lobster_bioforge", "midas_nanoforge", "air_filter_core"), a("vulpoids"), null));
        ENTRIES.add(new Entry("yunruindustries", "Yunru Industries", "2025-03-30", a(CAMods.DRYDOCK, CAMods.BAZAAR, "yunru_advent"), NONE, NONE, NONE, null));
        ENTRIES.add(new Entry("SBTM", "Slightly Better Tech-Mining", "2025-03-29", a("techmining"), NONE, NONE, NONE, () -> CAMods.sbtm()));
        ENTRIES.add(new Entry("yunruworlds", "Yunru's Worlds", "2025-07-06", NONE, a("yunru_gaia", "yunru_hephaestus", "yunru_crashedcryo"), NONE, NONE, null));
        ENTRIES.add(new Entry("wyv_planetaryShieldAccessControl", "Planetary Shield: Access Control", "1.7", a("planetaryshield"), NONE, NONE, NONE, () -> CAMods.psac()));
        ENTRIES.add(new Entry("US", "Unknown Skies", "3.0.3", NONE, a("US_fluorescent"), NONE, NONE, null));
        ENTRIES.add(new Entry("niko_morePlanetaryConditions", "What We Left Behind", "4.5.3", NONE, a("MPC_fracturedWorld"), NONE, NONE, null));
        ENTRIES.add(new Entry("Planetace_AstralAscension", "Astral Ascension", "1.2.8", NONE, NONE, NONE, NONE, null));
        // 0.2.45.0: Oddisz's colony mods (CAOddisz); the pack carries Cloning and the Private Arsenal under the same ids
        ENTRIES.add(new Entry(CAOddisz.CLONING_MOD, "Cloning", "0.2.1", CAOddisz.CLONING, NONE, NONE, NONE, null));
        ENTRIES.add(new Entry(CAOddisz.ARSENAL_MOD, "Reverse Engineered Private Arsenal", "1.3.1", CAOddisz.HUB, NONE, NONE, NONE, null));
        ENTRIES.add(new Entry(CAOddisz.PACK, "Coalesced Little Mods", "0.4.0", CAOddisz.packIndustries(), NONE, NONE, NONE, null));
        ENTRIES.add(new Entry(CAOddisz.CURVES_MOD, "Toolbox Economy Curves", "1.0.2", NONE, NONE, NONE, NONE, null));
        ENTRIES.add(new Entry(CAOddisz.SOL_MOD, "Sol Renewed", "1.0.1", NONE, NONE, NONE, NONE, () -> CAOddisz.solLink()));
    }

    static final Map<String, Entry> BY_MOD = new HashMap<String, Entry>();
    static { for (Entry e : ENTRIES) BY_MOD.put(e.mod, e); }

    /** Runs every check; called with each Diagnostics scan (every load). */
    public static void check() {
        for (Entry e : ENTRIES) {
            e.missing.clear();
            if (!Global.getSettings().getModManager().isModEnabled(e.mod)) { e.status = Status.ABSENT; e.version = ""; continue; }
            ModSpecAPI spec = Global.getSettings().getModManager().getModSpec(e.mod);
            e.version = spec == null || spec.getVersion() == null ? "?" : spec.getVersion().trim();
            boolean linked = true;
            if (e.link != null) {
                try { linked = Boolean.TRUE.equals(e.link.call()); } catch (Throwable t) { linked = false; }
            }
            for (String id : e.industries) if (spec(() -> Global.getSettings().getIndustrySpec(id)) == null) e.missing.add("building " + id);
            for (String id : e.conditions) if (spec(() -> Global.getSettings().getMarketConditionSpec(id)) == null) e.missing.add("condition " + id);
            for (String id : e.items) if (spec(() -> Global.getSettings().getSpecialItemSpec(id)) == null) e.missing.add("item " + id);
            for (String id : e.commodities) if (spec(() -> Global.getSettings().getCommoditySpec(id)) == null) e.missing.add("commodity " + id);
            e.status = !linked ? Status.OFF : !e.missing.isEmpty() ? Status.PARTIAL : sameVersion(e.version, e.tested) ? Status.OK : Status.NEWER;
            String line = e.name + " " + e.version + ": " + describe(e);
            if (e.status == Status.OK) CADiagnostics.LOG.info(CADiagnostics.TAG + "integration " + line);
            else CADiagnostics.LOG.warn(CADiagnostics.TAG + "integration " + line);
        }
    }

    static Object spec(Callable<Object> c) {
        try { return c.call(); } catch (Throwable t) { return null; }
    }

    static boolean sameVersion(String have, String tested) {
        String h = have.replaceAll("[^0-9A-Za-z.]", ""), t = tested.replaceAll("[^0-9A-Za-z.]", "");
        return h.equalsIgnoreCase(t) || h.endsWith(t);
    }

    public static String describe(Entry e) {
        switch (e.status) {
            case OK: return "OK";
            case NEWER: return "written for " + e.tested + "; every id and name still found, so it keeps working (estimates trusted a little less)";
            case PARTIAL: return "partly off, missing " + String.join(", ", e.missing);
            case OFF: return "off: its code can't be read (likely renamed in an update)";
            default: return "not installed";
        }
    }

    /** True when the integration for this mod runs on a version it wasn't written for, or with parts missing. */
    public static boolean untested(String mod) {
        Entry e = BY_MOD.get(mod);
        return e != null && (e.status == Status.NEWER || e.status == Status.PARTIAL);
    }

    public static List<Entry> installed() {
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : ENTRIES) if (e.status != Status.ABSENT) out.add(e);
        return out;
    }
}
