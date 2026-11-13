package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;

import java.util.*;

/**
 * 0.3.3.2: Mod Index batch 1. Each mod is reached by condition, building and setting ids only; no class of these mods is
 * referenced. Without a mod, no colony has its conditions or buildings and nothing here changes. Checked offline by
 * JTIndexBatch1Check.
 * - JaydeePiracy 6.0.3 (jaydeepiracy): three conditions change what a new building makes and needs (CAEngine.effects). The
 *   numbers are the mod's own, inline in its condition classes, so they are copied here.
 *   - Quarry Lakes (jdp_quarryLakes): Refining needs no ore or transplutonic ore, heavy machinery demand +2.
 *   - Domain-era Meshugatron (jdp_domainMeshugatron): Fuel Production +3 fuel (the Synchrotron's bonus), volatiles demand +3.
 *   - Eutrophicated Biosphere (jdp_eutrophicatedBiosphere): Farming or Aquaculture adds organics, half its food, rounded;
 *     heavy machinery demand +3.
 *   - The Planetkiller Launch Site is left to you (CAMods.LEFT). The planetkiller item is never installed (CAItems.NEVER).
 * - Corrupt.Officials 1.0.b (corruptofficials): a colony's net income above the cutoff (40,000 by default) is cut to
 *   cutoff + excess x (income / cutoff)^pow (pow -0.6 by default). Extra money at such a colony counts at the share of it the
 *   colony keeps (CAValue.add, keepDetail, itemMoney). The mod's LunaLib settings are read by name (CALuna).
 * - New colonies (CAExpansion.blocked): Tahlan Shipworks' Kassadari Claim (accessibility -100%, hazard +50%) and Gladiator
 *   Society's The Gulf (sealed by the Society) are skipped.
 * - Left to you (CAMods.LEFT): Scy Nation's Intelligence Command Center, CJY's Toy Box's three quest buildings and AI-Retrofits'
 *   Population Replacement Center.
 * - Production floors (CAFloors.SHIP_PRODUCERS): Blue's Lanestate Battleyards and Oculian Armada's Orbital Matrix.
 */
public class CAIndexMods {
    private CAIndexMods() { }

    // ---------------- JaydeePiracy ----------------

    public static final String JDP = "jaydeepiracy";
    static final String QUARRY = "jdp_quarryLakes", MESHUGATRON = "jdp_domainMeshugatron", EUTROPHIC = "jdp_eutrophicatedBiosphere";
    static final String PLANETKILLER_SITE = "jdp_planetkillerLaunchSite", PLANETKILLER = "planetkiller";
    /** jdp_quarryLakes.DEMAND: heavy machinery on Refining. */
    static final int QUARRY_MACHINERY = 2;
    /** jdp_domainMeshugatron: SYNCHROTRON_FUEL_BONUS on Fuel Production's supply, and DEMAND volatiles. */
    static final int MESHUGATRON_FUEL = 3, MESHUGATRON_VOLATILES = 3;
    /** jdp_eutrophicatedBiosphere.DEMAND: heavy machinery on Farming. */
    static final int EUTROPHIC_MACHINERY = 3;

    static boolean has(MarketAPI m, String cond) {
        try { return m != null && m.hasCondition(cond); } catch (Throwable t) { return false; }
    }

    /** Quarry Lakes here: Refining takes no ore or transplutonic ore. */
    public static boolean quarryLakes(MarketAPI m) { return has(m, QUARRY); }

    /** A new Refining at Quarry Lakes: heavy machinery demand +2 in place of its ore demand (charged only when your colonies lack it). */
    static void quarryRefining(CAEngine.Pick p, Map<String, Integer> deficit) {
        CAEngine.demand(p, "heavy_machinery", QUARRY_MACHINERY, deficit);
        p.lines.add("Quarry Lakes: needs no ore or transplutonic ore. Heavy machinery demand +" + QUARRY_MACHINERY);
    }

    /** Fuel a new Fuel Production gains here: +3 at a Domain-era Meshugatron, else 0. */
    public static int meshugatronFuel(MarketAPI m) { return has(m, MESHUGATRON) ? MESHUGATRON_FUEL : 0; }

    /** Volatiles a new Fuel Production needs over its own here: +3 at a Domain-era Meshugatron, else 0. */
    public static int meshugatronVolatiles(MarketAPI m) { return has(m, MESHUGATRON) ? MESHUGATRON_VOLATILES : 0; }

    /** Heavy machinery a new Farming or Aquaculture needs over its own here: +3 at Eutrophicated Biosphere, else 0. */
    public static int eutrophicMachinery(MarketAPI m) { return has(m, EUTROPHIC) ? EUTROPHIC_MACHINERY : 0; }

    /** Pure: organics Eutrophicated Biosphere adds to a farm making `food`: half of it, rounded (Math.round, as the mod). */
    static int eutrophicOrganics(int food) { return food <= 0 ? 0 : Math.round(food / 2f); }

    /** A new Farming or Aquaculture at Eutrophicated Biosphere: its organics line. */
    static void eutrophicFarm(MarketAPI m, int food, CAEngine.Pick p, Map<String, Integer> deficit) {
        if (!has(m, EUTROPHIC)) return;
        int org = eutrophicOrganics(food);
        if (org > 0) CAEngine.supply(p, "organics", org, deficit);
        p.lines.add("Eutrophicated Biosphere: organics +" + org + " (half the food). Heavy machinery demand +" + EUTROPHIC_MACHINERY);
    }

    // ---------------- Corrupt.Officials ----------------

    public static final String CORRUPT = "corruptofficials", CORRUPT_COND = "corruptofficials_cond";
    /** The mod's LunaLib fields. The enabled field is spelled this way in the mod (Settings.CORRUPTION_ENABLED). */
    static final String CORRUPT_ENABLED = "corrputofficials_enabled", CORRUPT_CUTOFF = "corruptofficials_cutoff", CORRUPT_POW = "corruptofficials_pow";
    /** The mod's defaults (its LunaSettings.csv), used without LunaLib. */
    static final float CUTOFF_DEFAULT = 40000f, POW_DEFAULT = -0.6f;

    /** Settings read on the day in cfgAt (NaN: not read); reset on load (CAReset). */
    static Boolean corruptOn;
    static float cfgAt = Float.NaN, cutoff = CUTOFF_DEFAULT, pow = POW_DEFAULT;
    static boolean enabled = true;
    /** Each colony's kept share for the day: market id to {day, share}. */
    static final Map<String, float[]> SHARES = new HashMap<String, float[]>();

    static void reset() { corruptOn = null; cfgAt = Float.NaN; cutoff = CUTOFF_DEFAULT; pow = POW_DEFAULT; enabled = true; SHARES.clear(); }

    static boolean corruptMod() {
        if (corruptOn != null) return corruptOn;
        try { corruptOn = Global.getSettings().getModManager().isModEnabled(CORRUPT); }
        catch (Throwable t) { return false; }
        return corruptOn;
    }

    /** Pure: the income a colony keeps at raw net income `income` (the mod's targetIncome). */
    static float corruptKeptIncome(float income, float cutoff, float pow) {
        if (!(cutoff > 0f) || !(income > cutoff) || !(pow < 0f)) return income;
        return (float) (cutoff + (income - cutoff) * Math.pow(income / cutoff, pow));
    }

    /**
     * Pure: share of one more credit of net income the colony keeps at raw net income `income`, the slope of corruptKeptIncome:
     * x^pow x (1 + pow x (1 - 1/x)) with x = income / cutoff, between 0 and 1. 1 at or below the cutoff.
     */
    static float corruptMarginal(float income, float cutoff, float pow) {
        if (!(cutoff > 0f) || !(income > cutoff) || !(pow < 0f)) return 1f;
        double x = income / cutoff;
        double s = Math.pow(x, pow) * (1.0 + pow * (1.0 - 1.0 / x));
        return (float) Math.max(0.0, Math.min(1.0, s));
    }

    /** The mod's settings, read once a day: from LunaLib by name, else its defaults. */
    static void corruptSettings() {
        float now = CADay.now();
        if (Math.abs(now - cfgAt) < 1f) return;
        cfgAt = now;
        Boolean e = CALuna.b(CORRUPT, CORRUPT_ENABLED);
        Float c = CALuna.f(CORRUPT, CORRUPT_CUTOFF), w = CALuna.f(CORRUPT, CORRUPT_POW);
        enabled = e == null || e;
        cutoff = c != null && c > 0f ? c : CUTOFF_DEFAULT;
        pow = w != null && w < 0f ? w : POW_DEFAULT;
        SHARES.clear();
    }

    /**
     * A colony's net income before the mod's cut: its net income now plus the cut on its Population line ("Government
     * Corruption", modifier id corruptofficials_cond), which the market's income multiplier scales.
     */
    static float rawNetIncome(MarketAPI m) {
        float net = m.getNetIncome();
        float cut = 0f;
        Industry pop = m.getIndustry("population");
        if (pop == null) for (Industry i : m.getIndustries()) if (i.getSpec() != null && i.getSpec().hasTag("population")) { pop = i; break; }
        if (pop != null && pop.getIncome() != null) {
            MutableStat.StatMod mod = pop.getIncome().getFlatStatMod(CORRUPT_COND);
            if (mod != null) cut = -mod.getValue();
        }
        float mult = 1f;
        try { mult = m.getIncomeMult().getModifiedValue(); } catch (Throwable ignored) { }
        return net + Math.max(0f, cut) * mult;
    }

    /** Share of extra net income this colony keeps under Corrupt.Officials: 1 without the mod, with it off, or under the cutoff. */
    public static float corruptShare(MarketAPI m) {
        if (m == null || !corruptMod()) return 1f;
        try {
            if (!m.hasCondition(CORRUPT_COND)) return 1f;
            corruptSettings();
            if (!enabled) return 1f;
            float now = CADay.now();
            float[] c = SHARES.get(m.getId());
            if (c != null && Math.abs(now - c[0]) < 1f) return c[1];
            float s = corruptMarginal(rawNetIncome(m), cutoff, pow);
            SHARES.put(m.getId(), new float[]{now, s});
            return s;
        } catch (Throwable t) { return 1f; }
    }

    /** Money a month at this colony after Corrupt.Officials: a gain at the kept share, a loss in full. */
    public static float corruptKept(MarketAPI m, float money) {
        if (!(money > 0f)) return money;
        float s = corruptShare(m);
        return s < 1f ? money * s : money;
    }

    // ---------------- new colonies ----------------

    public static final String TAHLAN = "tahlan", KASSADARI = "tahlan_kassadariclaim";
    public static final String GLADIATOR = "gladiatorsociety", GULF = "gs_gulf_restriction";

    /** Why Autopilot founds no colony on this planet, or null: Kassadari Claim, The Gulf. */
    public static String noColony(MarketAPI m) {
        if (has(m, KASSADARI)) return "Kassadari Claim: accessibility -100%, hazard +50%.";
        if (has(m, GULF)) return "The Gulf: sealed by the Gladiator Society.";
        return null;
    }

    // ---------------- left to you ----------------

    public static final String SCY = "SCY", SCY_INCOM = "SCY_incom";
    static final String CTB_PRODUCTION = "ctb_erosionprod", CTB_GREENHOUSE = "ctb_bluehouse", CTB_GREENHOUSE2 = "ctb_bluehouse2";
    public static final String RETROFITS = "AI-Retrofits", RETROFITS_PURGE = "AIRetrofit_AIRetrofitPurgeOption";

    /** For CAMods.LEFT: the buildings of this batch Autopilot never builds or test-builds, with why. */
    static Map<String, String> left() {
        Map<String, String> l = new LinkedHashMap<String, String>();
        l.put(PLANETKILLER_SITE, "it launches JaydeePiracy's Planetkiller and doubles Pather interest at the colony");
        l.put(SCY_INCOM, "its intel chips are priced at 10,000 credits each with no buyer");
        l.put(CTB_PRODUCTION, "a quest building, one per empire; its goods sell only to CJY's Toy Box markets");
        l.put(CTB_GREENHOUSE, "a quest building; its ore sells only to CJY's Toy Box markets");
        l.put(CTB_GREENHOUSE2, "it spawns a greenhouse station and supply fleets and removes them when taken down");
        l.put(RETROFITS_PURGE, "it swaps the colony between human and robot population");
        return l;
    }

    // ---------------- production floors ----------------

    /** For CAFloors.SHIP_PRODUCERS: Blue's Lanestate Battleyards and Oculian Armada's Orbital Matrix make ships and weapons. */
    static final String XLU_YARDS = "xlu_battle_yards", OCUA_MATRIX = "ocua_orbital_matrix";
}
