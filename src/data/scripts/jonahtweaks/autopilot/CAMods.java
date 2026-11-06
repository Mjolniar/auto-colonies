package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Smaller colony-mod integrations, by literal ids only (each is inert without its mod):
 * - Volkov Industrial Conglomerate: viral contamination (VIC_VBomb_scar, from Nexerelin bombardments) shrinks a colony
 *   until it's lost; the anti-EVC chain (t1, then t2 to stop the shrinking, t3 at +25 relations to cancel it) is built
 *   first. Its soil-nanites item only goes where farmland is vanilla (other farmland crashes it) and genetech arrives.
 * - Planetary Shield: Access Control: the shield adds +300 ground defense and cuts Pather interest only while free port
 *   is off, instead of vanilla's x3.
 * - Roider Union: Dives and Union HQ mine deposits within 1 or 6 light-years, which a probe colony can't see.
 * - Unknown Skies and What We Left Behind conditions that add output straight to Mining.
 * - Astral Ascension: a colony without a planet crashes past size 10, so its growth isn't pushed there.
 */
public class CAMods {

    static boolean on(String mod) { return Global.getSettings().getModManager().isModEnabled(mod); }

    // ---------------- Volkov Industrial Conglomerate ----------------

    static final String VIC = "vic", SCAR = "VIC_VBomb_scar";

    public static boolean vicHas(String id) { return id.startsWith("vic_antiEVCt") && on(VIC); }

    /** The anti-EVC chain: worth more than anything while the colony is contaminated, nothing otherwise. */
    static boolean vicApply(MarketAPI m, String id, CAEngine.Pick p) {
        if (!m.hasCondition(SCAR)) return false;
        if (id.equals("vic_antiEVCt1")) {
            if (m.hasIndustry("vic_antiEVCt1") || m.hasIndustry("vic_antiEVCt2") || m.hasIndustry("vic_antiEVCt3")) return false;   // its own check allows duplicates
            p.fx[0] += 200000f; p.lines.add("Viral contamination: starts the treatment (stability +2, smaller penalties)");
        } else if (id.equals("vic_antiEVCt2")) {
            p.fx[0] += 150000f; p.lines.add("Viral contamination: stops the colony shrinking toward decivilization");
        } else if (id.equals("vic_antiEVCt3")) {
            p.fx[0] += 80000f; p.lines.add("Viral contamination: cancels every penalty (needs genetech imports from VIC)");
        } else return false;
        return true;
    }

    static final Set<String> VANILLA_FARMLAND = new HashSet<String>(Arrays.asList("farmland_poor", "farmland_adequate", "farmland_rich", "farmland_bountiful"));

    /** False where an item would misbehave: VIC's nanites crash on other mods' farmland and do nothing without genetech. */
    public static boolean itemFits(String item, MarketAPI m) {
        if (!item.equals("vic_soil_nanites2")) return true;
        for (MarketConditionAPI c : m.getConditions()) if (c.getId().startsWith("farmland") && !VANILLA_FARMLAND.contains(c.getId())) return false;
        try { return m.getCommodityData("vic_genetech").getAvailable() > 0; } catch (Throwable t) { return false; }
    }

    // ---------------- Planetary Shield: Access Control ----------------

    /** The shield is Access Control's, not vanilla's (it swaps the plugin behind the same id). */
    public static boolean psac() {
        if (!on("wyv_planetaryShieldAccessControl")) return false;
        try {
            if (!"data.scripts.wyv.PlanetaryShield".equals(Global.getSettings().getIndustrySpec("planetaryshield").getPluginClass())) return false;
            return !Global.getSettings().loadJSON("wyv_ps_settings.json", "wyv_planetaryShieldAccessControl").optBoolean("revertToVanillaShieldBehavior", false);
        } catch (Throwable t) { return false; }
    }

    static void shield(MarketAPI m, CAEngine.Pick p) {
        p.fx[3] += 1.5f;
        if (!m.isFreePort()) p.fx[1] += 0.5f;
        p.confidence = 0.85f;   // an estimate of the mod's flat bonus, not a formula
        p.lines.add("Planetary Shield (Access Control): +300 ground defense; Pather interest -4 while free port is off");
    }

    /** With Access Control, a working shield keeps Pathers out only while the port is closed. */
    public static boolean shieldWantsClosedPort(MarketAPI m) {
        if (!psac()) return false;
        Industry s = m.getIndustry("planetaryshield");
        return s != null && s.isFunctional() && m.hasCondition("pather_cells");
    }

    // ---------------- Roider Union ----------------

    static final String ROIDER = "roider";
    static final String[] MINED = {"ore", "rare_ore", "organics", "volatiles"};
    static final Map<String, Integer> BASE = new HashMap<String, Integer>();
    static final Map<String, Integer> BONUS = new HashMap<String, Integer>();
    static {
        BASE.put("ore", 1); BASE.put("rare_ore", -1); BASE.put("organics", 1); BASE.put("volatiles", -1);
        String[] ore = {"sparse", "moderate", "abundant", "rich", "ultrarich"}, gas = {"trace", "common", "abundant", "plentiful"}, vol = {"trace", "diffuse", "abundant", "plentiful"};
        for (int i = 0; i < ore.length; i++) { BONUS.put("ore_" + ore[i], i - 1); BONUS.put("rare_ore_" + ore[i], i - 1); }
        for (int i = 0; i < gas.length; i++) BONUS.put("organics_" + gas[i], i - 1);
        for (int i = 0; i < vol.length; i++) BONUS.put("volatiles_" + vol[i], i - 1);
    }

    static final String DSI = "roiderUnion.econ.DivesSupplyInteractor", DI = "roiderUnion.econ.DivesInteractor";

    /** Roider's own tables when it exposes them (read live, so a rebalance is followed), these copies otherwise. */
    static int base(String com) { return CAConst.mapInt(DSI, "BASE_SUPPLY", com, BASE.get(com)); }
    static Integer bonus(String com, String cond) { Integer b = BONUS.get(cond); return b == null ? null : CAConst.mapInt(DSI, "BONUS_SUPPLY", com + cond, b); }
    static float divesRange() { return CAConst.num(DI, "DIVES_RANGE", 1f); }
    static float hqRange() { return divesRange() + CAConst.num(DI, "HQ_RANGE_BONUS", 5f); }

    public static boolean roiderHas(String id) { return (id.equals("roider_dives") || id.equals("roider_union_hq")) && on(ROIDER); }

    /** Best deposit Roider miners reach for this commodity: the colony's own, or a system's void resource within range. */
    static Integer bestBonus(MarketAPI m, String com, float range) {
        Integer best = null;
        for (MarketConditionAPI c : m.getConditions()) {
            Integer b = c.getId().startsWith(com + "_") ? bonus(com, c.getId()) : null;
            if (b != null && !(com.equals("ore") && c.getId().startsWith("rare_ore")) && (best == null || b > best)) best = b;
        }
        if (m.getLocationInHyperspace() == null) return best;
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (Misc.getDistanceLY(s.getLocation(), m.getLocationInHyperspace()) > range) continue;
            Object v = s.getMemoryWithoutUpdate().get("$roider_voidResource_" + com);
            Integer b = v == null ? null : bonus(com, String.valueOf(v));
            if (b != null && (best == null || b > best)) best = b;
        }
        return best;
    }

    static final String CHARTER = "roider_charter";

    /** The Historical Roider Charter in the colony's Dives or Union HQ: +1 to every mined output and +6 light-years of range (RoiderItems). */
    static boolean charter(MarketAPI m) {
        for (String id : new String[]{"roider_dives", "roider_union_hq"}) {
            Industry ind = m.getIndustry(id);
            if (ind != null && ind.getSpecialItem() != null && CHARTER.equals(ind.getSpecialItem().getId())) return true;
        }
        return false;
    }

    static float charterRange() { return CAConst.num("roiderUnion.ids.RoiderItems", "CHARTER_MINING_RANGE_BONUS", 6f); }

    /** Unknown Skies' Psychoactive (and Shrooms) worlds: Roider miners need no drugs there; Psychoactive also takes 1 off each mined output (DivesSupplyInteractor). */
    static boolean noDrugs(MarketAPI m) { return m.hasCondition("US_mind") || m.hasCondition("US_shrooms"); }

    /** Mined units of one commodity at this range: Roider's flat table (not by colony size), +1 with a Charter, -1 on a Psychoactive world. */
    static int mined(MarketAPI m, String com, float range, int extra) {
        Integer b = bestBonus(m, com, range);
        if (b == null) return 0;
        return Math.max(0, base(com) + b + extra);
    }

    static int extra(MarketAPI m) { return (charter(m) ? 1 : 0) - (m.hasCondition("US_mind") ? 1 : 0); }

    static int output(MarketAPI m, float range, CAEngine.Pick p, Map<String, Integer> deficit, boolean add) {
        return output(m, range, p, deficit, add, 0);
    }

    /**
     * cut: heavy machinery the miners are short. Roider takes the whole shortage off every mined output
     * (DivesSupplyInteractor.applySupplyAndDemand: applyDeficitToProduction over ore, transplutonic ore, organics and
     * volatiles), so 1 short turns 1+1+1+1 into nothing: Umuzi's Dives mined 0 of a predicted 4 (2.39.0).
     */
    static int output(MarketAPI m, float range, CAEngine.Pick p, Map<String, Integer> deficit, boolean add, int cut) {
        int total = 0, x = extra(m) - Math.max(0, cut);
        for (String com : MINED) {
            int u = mined(m, com, range, x);
            total += u;
            if (add && u > 0) CAEngine.supply(p, com, u, deficit);
        }
        return total;
    }

    /** Heavy machinery Roider miners needing this much would be short: what your colonies already lack, up to their need. */
    static int machineryShort(Map<String, Integer> deficit, int need) {
        Integer d = deficit == null ? null : deficit.get("heavy_machinery");
        return d == null || d <= 0 ? 0 : Math.min(d, need);
    }

    /**
     * Dives (1 ly) or the step up to Union HQ (6 ly): what's mined, less for the HQ what the Dives already mined. False when
     * nothing is in reach. Checked against Roider Union 2.3.1's code (2.38.0): the HQ's crew is the colony's size (not size - 3)
     * and it needs 3 drugs too; neither needs drugs on Unknown Skies' Psychoactive or Shrooms worlds; a Charter adds 1 to each
     * output and 6 light-years of range.
     */
    static boolean roiderApply(MarketAPI m, String id, CAEngine.Pick p, Map<String, Integer> deficit) {
        int s = m.getSize();
        float add = charter(m) ? charterRange() : 0f;
        int drugs = noDrugs(m) ? 0 : 3;
        if (id.equals("roider_dives")) {
            int cut = machineryShort(deficit, 1);
            if (output(m, divesRange() + add, p, deficit, true, cut) <= 0) return false;
            if (cut > 0) p.lines.add("Your colonies lack heavy machinery, and Roider miners lose the shortage from every output");
            CAEngine.supply(p, "crew", 3, deficit);
            CAEngine.demand(p, "heavy_machinery", 1, deficit); CAEngine.demand(p, "drugs", drugs, deficit); CAEngine.demand(p, "supplies", 2, deficit);
            p.lines.add("Roider miners work deposits within " + Math.round(divesRange() + add) + " light-years");
            return true;
        }
        int x = extra(m), cutWas = machineryShort(deficit, 1), cutNow = machineryShort(deficit, Math.max(1, s - 2));   // the HQ needs size - 2
        for (String com : MINED) {
            int was = mined(m, com, divesRange() + add, x - cutWas), u = mined(m, com, hqRange() + add, x - cutNow);
            if (u > was) CAEngine.supply(p, com, u - was, deficit);
        }
        CAEngine.supply(p, "crew", Math.max(0, s - 3), deficit);   // the Dives' 3 crew become the colony's size
        p.fx[1] += 1f; p.fx[3] += 0.2f; p.fx[4] += 0.2f;
        CAEngine.demand(p, "heavy_machinery", s - 3, deficit); CAEngine.demand(p, "supplies", s - 2, deficit); CAEngine.demand(p, "fuel", s - 2, deficit);
        p.lines.add("Union HQ: mines deposits within " + Math.round(hqRange() + add) + " light-years, stability +1, patrols");
        return true;
    }

    // ---------------- conditions that add output straight to Mining ----------------

    /** Unknown Skies' Luminescent Clouds and What We Left Behind's Fractured World add to Mining directly (not through the deposit tables). */
    static void miningExtras(MarketAPI m, CAEngine.Pick p, Map<String, Integer> deficit) {
        if (m.hasCondition("US_fluorescent")) CAEngine.supply(p, "volatiles", 3, deficit);
        if (m.hasCondition("MPC_fracturedWorld")) {
            int ore = Math.round(CAConst.num("data.scripts.campaign.singularity.MPC_fracturedWorld", "TOTAL_ORE_BONUS", 5f));
            CAEngine.supply(p, "ore", ore, deficit); CAEngine.supply(p, "rare_ore", ore, deficit);
        }
    }

    // ---------------- Astral Ascension ----------------

    /** Astral Ascension crashes a colony without a planet once it passes size 10 (its fix isn't registered), so don't push one there. */
    public static boolean growthUnsafe(MarketAPI m) {
        return on("Planetace_AstralAscension") && m.getPlanetEntity() == null && m.getSize() >= 10;
    }

    // ---------------- Yunru Industries ----------------

    static final String YUNRU = "yunruindustries", DRYDOCK = "yunru_heavyindustry", BAZAAR = "yunru_bazaar";
    static final String[] DRYDOCK_OUT = {"heavy_machinery", "supplies", "hand_weapons", "ships"};

    public static boolean yunruHas(String id) { return id.equals(DRYDOCK) && on(YUNRU); }

    /**
     * Secondary Drydocks' output per line (YunruIndustry): a Heavy Industry's size - 2 on its own; next to a working Heavy
     * Industry or Orbital Works (only those two ids), that building's bonuses (+1 improved, +1 Alpha core, +1 corrupted or
     * +3 pristine nanoforge) are added and everything is x1.5. Pure, for the checks.
     */
    static int drydockOutput(int size, boolean nextToForge, int forgeBonus) {
        int base = size - 2;
        return nextToForge ? Math.round((base + forgeBonus) * 1.5f) : base;
    }

    static Industry forge(MarketAPI m) {
        for (String id : new String[]{"orbitalworks", "heavyindustry"}) {
            Industry ind = m.getIndustry(id);
            if (ind != null && ind.isFunctional()) return ind;
        }
        return null;
    }

    static int forgeBonus(Industry f) {
        if (f == null) return 0;
        int b = (f.isImproved() ? 1 : 0) + ("alpha_core".equals(f.getAICoreId()) ? 1 : 0);
        if (f.getSpecialItem() != null) {
            String it = f.getSpecialItem().getId();
            b += "pristine_nanoforge".equals(it) ? 3 : "corrupted_nanoforge".equals(it) ? 1 : 0;
        }
        return b;
    }

    /**
     * Secondary Drydocks: a colony only counts its best source of each commodity, so next to a Heavy Industry it's worth
     * what it makes beyond that building, and on its own it's a Heavy Industry that never pollutes and takes no nanoforge.
     * Its hand weapons aren't legal for your faction (it doesn't make them legally the way vanilla's forge does), so they
     * only count without a free port where the colony is short (CAEngine.illegalUnits).
     */
    static boolean drydockApply(MarketAPI m, CAEngine.Pick p, Map<String, Integer> deficit) {
        int s = m.getSize();
        Industry f = forge(m);
        int out = drydockOutput(s, f != null, forgeBonus(f));
        int added = 0;
        for (String com : DRYDOCK_OUT) {
            int have = 0;
            if (f != null) try { have = f.getSupply(com).getQuantity().getModifiedInt(); } catch (Throwable ignored) { }
            int gain = out - have;
            if (gain > 0) { CAEngine.supply(p, com, gain, deficit); added += gain; }
        }
        if (added <= 0) return false;
        float mult = f != null ? 1.5f : 1f;
        CAEngine.demand(p, "metals", Math.round(s * mult), deficit); CAEngine.demand(p, "rare_metals", Math.round((s - 2) * mult), deficit);
        p.lines.add(f != null ? "Secondary Drydocks next to " + f.getCurrentName() + ": " + out + " of each line, what it adds beyond that building counts"
                : "Secondary Drydocks: a Heavy Industry without pollution; takes no nanoforge");
        return true;
    }

    // ---------------- Slightly Better Tech-Mining ----------------

    static final String SBTM_PLUGIN = "data.scripts.industry.YunruTechMining";

    /** SBTM replaces vanilla's Tech-Mining code: finds valued at TechMineValue x a ruins factor x colony size a month. */
    public static boolean sbtm() {
        try { return SBTM_PLUGIN.equals(Global.getSettings().getIndustrySpec("techmining").getPluginClass()); } catch (Throwable t) { return false; }
    }

    /** SBTM's ruins factor (YunruTechMining): vast 2.5, extensive 1.5, widespread 1, scattered 0.5. Pure. */
    static float sbtmRuins(boolean vast, boolean extensive, boolean widespread) { return vast ? 2.5f : extensive ? 1.5f : widespread ? 1f : 0.5f; }

    /**
     * A month of SBTM finds in credits: its budget (TechMineValue x ruins x size x what's left of the ruins) is a random draw
     * of AI cores, weapons, blueprints and colony items worth that at base price, counted at half (what selling them gets).
     */
    static float sbtmMonthly(MarketAPI m, float left) {
        float v = CAConst.num(SBTM_PLUGIN, "YUNRU_DVALUE", 10000f);
        float r = sbtmRuins(m.hasCondition("ruins_vast"), m.hasCondition("ruins_extensive"), m.hasCondition("ruins_widespread"));
        return 0.5f * v * r * m.getSize() * left;
    }

    // ---------------- engine hooks ----------------

    public static boolean has(String id) { return vicHas(id) || roiderHas(id) || yunruHas(id); }

    /** Effects of a building handled here; false when it isn't worth building at this colony. */
    public static boolean apply(MarketAPI m, String id, CAEngine.Pick p, Map<String, Integer> deficit) {
        if (vicHas(id)) return vicApply(m, id, p);
        if (roiderHas(id)) return roiderApply(m, id, p, deficit);
        if (id.equals(DRYDOCK)) return drydockApply(m, p, deficit);
        return false;
    }

    // ---------------- left to you ----------------

    /**
     * Mod buildings Autopilot never builds or upgrades into, and never puts on a probe colony, with why. Diagnostics lists
     * them as left to you; you can still build them yourself, and Autopilot leaves them alone once built (2.38.0).
     */
    static final Map<String, String> LEFT = new LinkedHashMap<String, String>();
    static {
        LEFT.put("NSI_blastCoreMining", "it permanently lowers the colony's ore, rare ore, organics and volatiles deposits every 60 days");
        LEFT.put("NSI_SpyBureau", "it sabotages other factions' colonies, takes two industry slots and can't be tested on a probe colony");
        LEFT.put("NSI_SpyBureauSecondInd", "the Spy Bureau's hidden second slot");
        LEFT.put("NSI_SpyOutpost", "it sabotages other factions' colonies and can't be tested on a probe colony");
        LEFT.put("NSI_cryosanctum", "it starts the Nomios protest crisis and a pirate raid on the colony");
        LEFT.put("NSI_outpostDefense", "it stops the colony growing past size 5");
        LEFT.put("NSI_luddicMonument", "its main effect needs Patchlib");
        LEFT.put("NSI_ASBUplink", "it works through an area that damages fleets, which a probe colony can't show");
        LEFT.put("NSI_expertDockworks", "it takes a nanoforge of its own for fleet s-mods, a military choice");
        LEFT.put("roider_nomadBase", "only Roider nomads build it");
        LEFT.put(BAZAAR, "it's a market for you to trade at; it does nothing for the colony, so Autopilot builds it only at your capital (2.42.0)");
    }

    /** Why Autopilot leaves this building to you, or null. */
    public static String leftToYou(String id) { return LEFT.get(id); }

    /** Buildings a story point improvement does nothing for: Yunru's Advent Outreach (its improvement has no effect). */
    public static boolean noImprove(String id) { return id.equals("yunru_advent"); }
}
