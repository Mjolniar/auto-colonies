package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import org.json.JSONObject;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A station at every colony (0.2.42.0). A station fights next to your fleet and patrols, and a raid, bombardment or
 * invasion has to beat it first, so it's how a colony holds off fleets; ground defenses only matter once troops land.
 * The engine's money-weighted score left most colonies without one: on Balanced a 250,000 Orbital Station came to about
 * -2,000 a month (its price spread over three years outweighed its defense and fleet value), so only the Security goal,
 * a threat, a Fortress or Bastion plan or spending freely built one.
 *
 * Now every colony plans one once it's out of its fast buildout and has grown to size 4 (or is half a year old), and
 * bigger colonies plan the next tier: a Battlestation from size 6 (5 at the capital), a Star Fortress from size 8 (7 at
 * the capital). These steps earn their plan rank even when their upkeep outweighs what the goal's weights give them
 * (CAEngine.finish), and follow the station line under Settings, Building and planning. Setting: Station at every colony.
 */
public class CAStations {
    static final int TIER2_SIZE = 6, TIER3_SIZE = 8;
    static final float YOUNG_DAYS = 180f;

    static CAPlan.Step step(String id, String label) { return new CAPlan.Step(CAPlan.Kind.BUILD, id, label); }

    /** The colony's station steps, put in front of its own plan (CAPlan.build). */
    public static List<CAPlan.Step> stepsFor(MarketAPI m) {
        List<CAPlan.Step> out = new ArrayList<CAPlan.Step>();
        if (m == null || CAData.settings().noStationPlan || CAEngine.isOutpost(m) || CAHypershunt.is(m) || m.hasTag("market_no_industries_allowed")) return out;
        if (CAData.get().colony(m.getId()).buildout) return out;   // a fast buildout makes money first
        int size = m.getSize();
        if (size < 4 && m.getDaysInExistence() < YOUNG_DAYS) return out;
        out.add(step("station", "Orbital station (every colony)"));
        boolean cap = CACapital.is(m);
        int tier = size >= TIER3_SIZE || (cap && size >= TIER3_SIZE - 1) ? 3 : size >= TIER2_SIZE || (cap && size >= TIER2_SIZE - 1) ? 2 : 1;
        for (int t = 2; t <= tier; t++) {
            String id = tierId(m, t);
            if (id != null) out.add(step(id, CAWorldPlanner.industryName(id) + " (size " + size + (cap ? ", capital" : "") + ")"));
        }
        return out;
    }

    /** The station of this tier in the colony's line: its own station's upgrades, or the line you picked (low tech by default). */
    static String tierId(MarketAPI m, int tier) {
        String id = null;
        for (Industry ind : m.getIndustries()) if (CAPlan.stationTier(ind.getSpec()) > 0) id = ind.getId();
        if (id == null) id = CASettings.stationStarter(CAData.settings().stationLine);
        if (id == null) id = "orbitalstation";
        for (int k = 0; k < 6 && id != null; k++) {
            IndustrySpecAPI sp = CAPlan.spec(id);
            if (sp == null) return null;
            int t = CAPlan.stationTier(sp);
            if (t == tier) return id;
            if (t > tier) return null;   // already past it
            id = sp.getUpgrade();
        }
        return null;
    }

    // ---------------- 0.3.6.3: station strength ----------------

    /** The fleet value a low-tech station of each tier gets in CAEngine.effects (index = tier; 4 reads like 3). */
    static final float[] REF = {0f, 0.3f, 0.3f, 0.5f, 0.5f};
    static final String[] LOW = {null, "orbitalstation", "battlestation", "starfortress", "starfortress"};
    static final Map<String, Float> STRENGTH = new HashMap<String, Float>();

    public static void reset() { STRENGTH.clear(); }

    /**
     * The station's fleet: durability (hull and armor) and firepower (weapon OP, fighter bays) of its core and modules, from the
     * variant its industry's data names. 0 when unknown (no data, no variant).
     */
    public static float strength(String industryId) {
        Float c = STRENGTH.get(industryId);
        if (c != null) return c;
        float v = 0f;
        try {
            IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(industryId);
            String data = sp == null ? null : sp.getData();
            if (data != null && !data.trim().isEmpty()) {
                String vid = new JSONObject(data).optString("variant", null);
                if (vid != null) {
                    ShipVariantAPI var = Global.getSettings().getVariant(vid);
                    float[] df = new float[2];
                    add(var, df);
                    if (var != null && var.getStationModules() != null)
                        for (String mv : var.getStationModules().values()) add(Global.getSettings().getVariant(mv), df);
                    v = pureStrength(df[0], df[1]);
                }
            }
        } catch (Throwable t) { v = 0f; }
        STRENGTH.put(industryId, v);
        return v;
    }

    static void add(ShipVariantAPI v, float[] df) {
        if (v == null || v.getHullSpec() == null) return;
        df[0] += v.getHullSpec().getHitpoints() + 10f * v.getHullSpec().getArmorRating();
        for (String slot : v.getFittedWeaponSlots()) {
            WeaponSpecAPI w = Global.getSettings().getWeaponSpec(v.getWeaponId(slot));
            if (w != null) df[1] += w.getOrdnancePointCost(null);
        }
        df[1] += 20f * v.getFittedWings().size();
    }

    /** Pure (JTStationCheck): durability and firepower as one number, their geometric mean. */
    static float pureStrength(float durability, float firepower) {
        return durability <= 0f ? 0f : (float) Math.sqrt(durability * Math.max(1f, firepower));
    }

    /** Pure (JTStationCheck): strength against the low-tech station of the tier, 0.5 to 2.5; 1 when either is unknown. */
    static float ratio(float s, float base) {
        if (s <= 0f || base <= 0f) return 1f;
        return Math.max(0.5f, Math.min(2.5f, s / base));
    }

    /** Pure (JTStationCheck): the fleet value effects() already gave this station (vanilla and Imperium are written in). */
    static float already(String id, int tier) {
        if (id.startsWith("orbitalstation") || id.startsWith("battlestation") || id.startsWith("starfortress")) return REF[tier];
        if (id.startsWith("ii_")) return tier == 1 ? 0.3f : tier == 2 ? 0.6f : 1f;
        return 0f;
    }

    /** Scores a station build or upgrade by its fleet's strength (CAEngine, after the effects): the low-tech station's value times the ratio. */
    public static void fleetValue(String id, CAEngine.Pick p) {
        try {
            int tier = CAPlan.stationTier(CAPlan.spec(id));
            if (tier <= 0) return;
            float r = ratio(strength(id), strength(LOW[Math.min(4, tier)]));
            float adj = REF[Math.min(4, tier)] * r - already(id, Math.min(4, tier));
            if (adj == 0f) return;
            p.fx[4] += adj;
            p.lines.add("Station fleet: " + Math.round(r * 100f) + "% of the low-tech station's strength");
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "station strength failed for " + id, t); }
    }

    /** A station step here, or what leads to one (an Orbital Station on the way to a Battlestation). */
    public static boolean isStep(MarketAPI m, String id) {
        if (id == null || m == null) return false;
        IndustrySpecAPI sp = CAPlan.spec(id);
        if (sp == null || CAPlan.stationTier(sp) <= 0) return false;
        for (CAPlan.Step s : stepsFor(m)) if (s.id.equals(id) || s.id.equals("station") || CAPlan.leadsTo(id, s.id)) return true;
        return false;
    }
}
