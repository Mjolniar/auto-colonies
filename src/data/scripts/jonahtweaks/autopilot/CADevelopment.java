package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Ashes of the Domain colony management styles (Vaults of Knowledge "Colony Development"). VoK only lets you pick one
 * when founding a colony and never changes it afterwards, so Autopilot doesn't either; it scores new buildings with the
 * style's real effects, taken from VoK 5.0.7's code (several tooltip claims aren't implemented):
 * - Centralized Urban Core: upkeep x0.8 except military buildings; each output line of a non-extractive building
 *   +min(2, size/3), of an extractive one (farming, mining and their AotD versions) -min(3, size-2).
 * - Distributed Regional Network: upkeep x1.5 (x1.3 with a working Maglev); extractive lines +min(3, size-2), others -min(4, size-2).
 * - Bore City: Mining and Extractive Operation output x1.7. Its x1.3 build cost hooks a Building Menu Overhaul
 *   listener method this BMO version never calls, so no surcharge is charged.
 * - Archeo-Site: Tech-Mining x1.7. Its build whitelist is BMO's hidden list, which CAAotD already obeys.
 * - Floating City: every building adds thruster fuel demand (5 heavy industry, 4 other industries, 3 structures); a fuel
 *   shortage lowers the city until it's lost, so nothing is built while fuel would run short.
 * Reflection only; without VoK every colony reads as Persean-Standardized (no effects).
 */
public class CADevelopment {
    public static final String MOD = "aotd_vok";
    static final String PKG = "data.kaysaar.aotd.vok.campaign.econ.colonydevelopment.";
    static final String ALTITUDE = "$aotd_memkey_floating_city_level";
    static final Map<String, String> NAMES = new HashMap<String, String>();
    static {
        NAMES.put("standard", "Persean-Standardized Protocol"); NAMES.put("core", "Centralized Urban Core");
        NAMES.put("distributed", "Distributed Regional Network"); NAMES.put("floatingcity", "Floating City");
        NAMES.put("borecity", "Bore City"); NAMES.put("archeosite", "Archeo-Site"); NAMES.put("abyss_colony", "Abyssal Frontier");
        NAMES.put("hypershunt", "Hypershunt Emergency Mode");
    }
    /** VoK fills its own set on load from the farming, aquaculture and extractive upgrade trees; this is the fallback. */
    static final Set<String> EXTRACTIVE = new HashSet<String>(Arrays.asList("aotd_monoculture_plots", "farming", "aotd_artisanal_farming",
            "aotd_subsidised_farming", "aquaculture", "aotd_fishing_harbour", "aotd_extractive_operation", "mining", "aotd_plasma_harvester",
            "aotd_mining_megaplex", "aotd_garden_of_elysium"));

    static Method planOf;
    static Field extractiveField;
    static boolean linked = false, broken = false;

    static boolean link() {
        if (linked || broken) return linked;
        if (!Global.getSettings().getModManager().isModEnabled(MOD)) return false;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            planOf = Class.forName(PKG + "models.ColonyDevelopmentManager", true, cl).getMethod("getColonyDevPlanIfPresent", MarketAPI.class);
            extractiveField = Class.forName(PKG + "impl.DistributedRegionalNetwork", true, cl).getField("extractiveOperationIndustries");
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Vaults of Knowledge's colony management styles; scored as standard", t);
        }
        return linked;
    }

    /** The colony's management style id; "standard" when VoK isn't running or has none recorded. */
    public static String id(MarketAPI m) {
        if (!link()) return "standard";
        try { Object o = planOf.invoke(null, m); return o == null ? "standard" : String.valueOf(o); }
        catch (Throwable t) { return "standard"; }
    }

    public static String name(String id) { String n = NAMES.get(id); return n == null ? id : n; }

    @SuppressWarnings("unchecked")
    static boolean extractive(String industryId) {
        try {
            Set<String> s = extractiveField == null ? null : (Set<String>) extractiveField.get(null);
            if (s != null && !s.isEmpty()) return s.contains(industryId);
        } catch (Throwable ignored) { }
        return EXTRACTIVE.contains(industryId);
    }

    /** Flat change to every output line of this building. */
    static int flat(String dev, int size, String industryId) {
        boolean ex = extractive(industryId);
        if ("core".equals(dev)) return ex ? -Math.min(3, size - 2) : Math.min(2, size / 3);
        if ("distributed".equals(dev)) return ex ? Math.min(3, size - 2) : -Math.min(4, size - 2);
        return 0;
    }

    static float mult(String dev, String industryId) {
        return "borecity".equals(dev) && (industryId.equals("mining") || industryId.equals("aotd_extractive_operation")) ? 1.7f : 1f;
    }

    public static float upkeepMult(MarketAPI m, IndustrySpecAPI spec) {
        String dev = id(m);
        if ("core".equals(dev)) {
            for (String tag : new String[]{"grounddefenses", "military", "command", "patrol"}) if (spec.hasTag(tag)) return 1f;
            return 0.8f;
        }
        if ("distributed".equals(dev)) {
            Industry maglev = m.getIndustry("aotd_maglev");
            return maglev != null && maglev.isFunctional() ? 1.3f : 1.5f;
        }
        return 1f;
    }

    public static float techMiningMult(MarketAPI m) { return "archeosite".equals(id(m)) ? 1.7f : 1f; }

    /** Thruster fuel a Floating City needs for this building once it's built. */
    static int thrusterFuel(Industry inst) {
        if (inst.getSpec().hasTag("heavyindustry")) return 5;
        return inst.isIndustry() ? 4 : 3;
    }

    /**
     * Applies the colony's style to a new building's own output (recorded in p.supplied) and, for a Floating City, its
     * thruster fuel. False when the building mustn't go up: a Floating City that would run short of fuel.
     */
    static boolean adjust(MarketAPI m, CAEngine.Pick p, Industry inst, Map<String, Integer> deficit) {
        String dev = id(m);
        if ("standard".equals(dev)) return true;
        int f = flat(dev, m.getSize(), p.id);
        float mu = mult(dev, p.id);
        if (f != 0 || mu != 1f) {
            for (Map.Entry<String, Integer> e : p.supplied.entrySet()) {
                int was = e.getValue();
                if (was <= 0) continue;
                int now = Math.max(0, Math.round((was + f) * mu));
                if (now != was) CAEngine.supplyChange(p, e.getKey(), now - was, deficit, name(dev));
            }
        }
        if ("floatingcity".equals(dev)) {
            int fuel = thrusterFuel(inst);
            CommodityOnMarketAPI c = m.getCommodityData("fuel");
            int shortBy = c == null ? 0 : Math.max(c.getDeficitQuantity(), c.getMaxDemand() + fuel - c.getAvailable());
            if (shortBy > 0) {
                if (CALog.once("floatfuel:" + m.getId())) CALog.add(m.getName() + ": Floating City: nothing new is built until fuel covers the thrusters "
                        + "(each building adds 3-5 fuel demand; a shortage lowers the city until it's lost)", true);
                return false;
            }
            CALog.forget("floatfuel:" + m.getId());
            float v = Global.getSettings().getCommoditySpec("fuel").getBasePrice() * CAEngine.unitValue * fuel;
            p.fx[0] -= v;
            p.lines.add("Floating City thrusters need " + fuel + " more fuel: about " + Misc.getDGSCredits(v) + " per month");
        }
        return true;
    }

    // ---------------- daily check ----------------

    /** Warns once per stage when a Floating City is losing altitude. */
    public static void run(List<MarketAPI> markets) {
        for (MarketAPI m : markets) {
            if (!"floatingcity".equals(id(m))) continue;
            float alt = altitude(m);
            String stage = stage(alt);
            if (alt > 750f) { CALog.forget("floatalt:" + m.getId() + ":Drifting"); CALog.forget("floatalt:" + m.getId() + ":Unstable"); CALog.forget("floatalt:" + m.getId() + ":Critical"); continue; }
            if (CALog.once("floatalt:" + m.getId() + ":" + stage)) CALog.add(m.getName() + ": Floating City is " + stage.toLowerCase() + " (altitude "
                    + Math.round(alt) + " of 1000); it falls while fuel is short and the colony is lost at 0. Fuel producers now score higher.", true);
        }
    }

    static float altitude(MarketAPI m) {
        try { return m.getMemoryWithoutUpdate().contains(ALTITUDE) ? m.getMemoryWithoutUpdate().getFloat(ALTITUDE) : 1000f; }
        catch (Throwable t) { return 1000f; }
    }

    static String stage(float alt) { return alt > 750f ? "Stable" : alt > 500f ? "Drifting" : alt > 250f ? "Unstable" : "Critical"; }

    // ---------------- colony panel ----------------

    /** What the colony's style does to Autopilot's choices. */
    public static void render(TooltipMakerAPI t, MarketAPI m) {
        String dev = id(m);
        java.awt.Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        int s = m.getSize();
        String what;
        if ("core".equals(dev)) what = "upkeep x0.8 except military buildings; non-extractive output " + signed(Math.min(2, s / 3))
                + " per line, farming and mining " + signed(-Math.min(3, s - 2)) + "; synergy efficiency +15";
        else if ("distributed".equals(dev)) what = "upkeep x" + (upkeepMult(m, Global.getSettings().getIndustrySpec("mining")) == 1.3f ? "1.3 (Maglev)" : "1.5")
                + "; farming and mining output " + signed(Math.min(3, s - 2)) + " per line, everything else " + signed(-Math.min(4, s - 2)) + "; synergy efficiency -10";
        else if ("borecity".equals(dev)) what = "Mining and Extractive Operation output x1.7";
        else if ("archeosite".equals(dev)) what = "Tech-Mining x1.7; only stations, Tech-Mining, Research Facility, Ground Defenses and Spaceport can be built";
        else if ("floatingcity".equals(dev)) what = "every building adds 3-5 thruster fuel demand; nothing is built while fuel would run short. Altitude "
                + Math.round(altitude(m)) + " of 1000 (" + stage(altitude(m)).toLowerCase() + ")";
        else if ("hypershunt".equals(dev)) what = "no buildings allowed";
        else what = "no effect on building choices";
        t.addPara("Management style: %s. " + CALog.esc(Character.toUpperCase(what.charAt(0)) + what.substring(1)) + ". Chosen when the colony was founded; VoK can't change it.",
                6f, hl, name(dev));
        if (!linked && Global.getSettings().getModManager().isModEnabled(MOD)) t.addPara("(Vaults of Knowledge's styles couldn't be read; scored as standard.)", gray, 2f);
    }

    static String signed(int v) { return (v > 0 ? "+" : "") + v; }
}
