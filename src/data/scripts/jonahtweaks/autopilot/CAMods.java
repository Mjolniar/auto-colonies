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

    public static boolean roiderHas(String id) { return (id.equals("roider_dives") || id.equals("roider_union_hq")) && on(ROIDER); }

    /** Best deposit Roider miners reach for this commodity: the colony's own, or a system's void resource within range. */
    static Integer bestBonus(MarketAPI m, String com, float range) {
        Integer best = null;
        for (MarketConditionAPI c : m.getConditions()) {
            Integer b = BONUS.get(c.getId());
            if (b != null && c.getId().startsWith(com + "_") && !(com.equals("ore") && c.getId().startsWith("rare_ore")) && (best == null || b > best)) best = b;
        }
        if (m.getLocationInHyperspace() == null) return best;
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (Misc.getDistanceLY(s.getLocation(), m.getLocationInHyperspace()) > range) continue;
            Object v = s.getMemoryWithoutUpdate().get("$roider_voidResource_" + com);
            Integer b = v == null ? null : BONUS.get(String.valueOf(v));
            if (b != null && (best == null || b > best)) best = b;
        }
        return best;
    }

    static int output(MarketAPI m, float range, CAEngine.Pick p, Map<String, Integer> deficit, boolean add) {
        int total = 0;
        for (String com : MINED) {
            Integer b = bestBonus(m, com, range);
            if (b == null) continue;
            int u = Math.max(0, BASE.get(com) + b);
            total += u;
            if (add && u > 0) CAEngine.supply(p, com, u, deficit);
        }
        return total;
    }

    /** Dives (1 ly) or the step up to Union HQ (6 ly): what's mined, less for the HQ what the Dives already mined. False when nothing is in reach. */
    static boolean roiderApply(MarketAPI m, String id, CAEngine.Pick p, Map<String, Integer> deficit) {
        int s = m.getSize();
        if (id.equals("roider_dives")) {
            if (output(m, 1f, p, deficit, true) <= 0) return false;
            CAEngine.supply(p, "crew", 3, deficit);
            CAEngine.demand(p, "heavy_machinery", 1, deficit); CAEngine.demand(p, "drugs", 3, deficit); CAEngine.demand(p, "supplies", 2, deficit);
            p.lines.add("Roider miners work deposits within 1 light-year");
            return true;
        }
        Map<String, Integer> was = new HashMap<String, Integer>();
        for (String com : MINED) { Integer b = bestBonus(m, com, 1f); was.put(com, b == null ? 0 : Math.max(0, BASE.get(com) + b)); }
        for (String com : MINED) {
            Integer b = bestBonus(m, com, 6f);
            int u = b == null ? 0 : Math.max(0, BASE.get(com) + b);
            if (u > was.get(com)) CAEngine.supply(p, com, u - was.get(com), deficit);
        }
        CAEngine.supply(p, "crew", Math.max(0, s - 3), deficit);
        p.fx[1] += 1f; p.fx[3] += 0.2f; p.fx[4] += 0.2f;
        CAEngine.demand(p, "heavy_machinery", s - 2, deficit); CAEngine.demand(p, "supplies", s, deficit); CAEngine.demand(p, "fuel", s - 2, deficit);
        p.lines.add("Union HQ: mines deposits within 6 light-years, stability +1, patrols");
        return true;
    }

    // ---------------- conditions that add output straight to Mining ----------------

    /** Unknown Skies' Luminescent Clouds and What We Left Behind's Fractured World add to Mining directly (not through the deposit tables). */
    static void miningExtras(MarketAPI m, CAEngine.Pick p, Map<String, Integer> deficit) {
        if (m.hasCondition("US_fluorescent")) CAEngine.supply(p, "volatiles", 3, deficit);
        if (m.hasCondition("MPC_fracturedWorld")) { CAEngine.supply(p, "ore", 5, deficit); CAEngine.supply(p, "rare_ore", 5, deficit); }
    }

    // ---------------- Astral Ascension ----------------

    /** Astral Ascension crashes a colony without a planet once it passes size 10 (its fix isn't registered), so don't push one there. */
    public static boolean growthUnsafe(MarketAPI m) {
        return on("Planetace_AstralAscension") && m.getPlanetEntity() == null && m.getSize() >= 10;
    }

    // ---------------- engine hooks ----------------

    public static boolean has(String id) { return vicHas(id) || roiderHas(id); }

    /** Effects of a building handled here; false when it isn't worth building at this colony. */
    public static boolean apply(MarketAPI m, String id, CAEngine.Pick p, Map<String, Integer> deficit) {
        if (vicHas(id)) return vicApply(m, id, p);
        if (roiderHas(id)) return roiderApply(m, id, p, deficit);
        return false;
    }
}
