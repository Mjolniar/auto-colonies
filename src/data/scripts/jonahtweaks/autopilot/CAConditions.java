package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition;

import java.util.*;

/** What a planet condition is worth to a colony, shared by the terraforming planners (Terraforming Made Easy, TASC). */
public class CAConditions {

    /** Whether the item pollutes in a forge: vanilla's nanoforges do, Ashes of the Domain's no_pollution items don't. */
    static boolean polluting(com.fs.starfarer.api.campaign.SpecialItemData item) { return item != null && polluting(item.getId()); }

    /** By id. UAF's Dimensional Nanoforge isn't tagged, but it removes the colony's pollution every time its effect applies. */
    static boolean polluting(String id) {
        if (id == null || id.equals("uaf_dimen_nanoforge")) return false;
        try {
            com.fs.starfarer.api.campaign.SpecialItemSpecAPI sp = Global.getSettings().getSpecialItemSpec(id);
            return sp == null || !sp.hasTag("no_pollution");
        } catch (Throwable t) { return true; }
    }

    /**
     * Whether pollution removed now comes back by itself: vanilla's Heavy Industry (and every forge built on it) with a
     * polluting item on a habitable world adds pollution once per building, and an upgrade under way (Heavy Industry to
     * Orbital Works, Orbital Works to Orbital Skunkworks) makes a new one when it finishes. So pollution removal waits for it.
     * (A forge that found pollution already there adds it back once after the first removal, then the next removal sticks.)
     */
    public static boolean pollutionReturns(MarketAPI m) {
        if (!m.hasCondition("habitable")) return false;
        for (com.fs.starfarer.api.campaign.econ.Industry ind : m.getIndustries()) {
            if (!(ind instanceof com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry) || !polluting(ind.getSpecialItem())) continue;
            if (ind.isUpgrading()) return true;
        }
        return false;
    }

    /** Hazard rating change of a condition (from condition_gen_data). */
    static final Map<String, Float> HAZARD = new HashMap<String, Float>();
    static {
        String[] q = {"cold", "hot", "tectonic_activity", "thin_atmosphere", "extreme_weather", "low_gravity",
                "inimical_biosphere", "water_surface", "poor_light", "pollution", "decivilized", "decivilized_subpop"};
        String[] h = {"very_cold", "very_hot", "extreme_tectonic_activity", "no_atmosphere", "toxic_atmosphere",
                "dense_atmosphere", "high_gravity", "irradiated", "dark", "meteor_impacts"};
        for (String s : q) HAZARD.put(s, 0.25f);
        for (String s : h) HAZARD.put(s, 0.5f);
        HAZARD.put("habitable", -0.25f);
        HAZARD.put("mild_climate", -0.25f);
        // 0.3.2.1: Rogue Planets' hazard conditions (set in their code, not condition_gen_data)
        HAZARD.put("geothermal_leylines", 0.5f);
        HAZARD.put("supercritical_crust", 0.5f);
        HAZARD.put("watched", 0.25f);
        HAZARD.put("fragile_biosphere", -0.1f);
        HAZARD.put("hydrocarbon_fractionation", -0.5f);
    }

    static final Map<String, CAData.Profile[]> WORLD_FITS = new HashMap<String, CAData.Profile[]>();
    static {
        WORLD_FITS.put("tme_paradise_world", new CAData.Profile[]{CAData.Profile.INCOME, CAData.Profile.GROWTH, CAData.Profile.BALANCED});
        WORLD_FITS.put("tme_forge_world", new CAData.Profile[]{CAData.Profile.WAR, CAData.Profile.SELF});
        WORLD_FITS.put("tme_fortress_world", new CAData.Profile[]{CAData.Profile.SECURITY, CAData.Profile.WAR});
        WORLD_FITS.put("tme_hive_world", new CAData.Profile[]{CAData.Profile.GROWTH});
        WORLD_FITS.put("tme_death_world", new CAData.Profile[]{CAData.Profile.SECURITY});
        WORLD_FITS.put("tme_waste_world", new CAData.Profile[]{CAData.Profile.SELF});
    }

    /** Units a deposit condition gives its industry here, by the game's own deposit tables (mods included); null if it isn't one. */
    static Integer depositUnits(MarketAPI m, String cond) {
        String com = ResourceDepositsCondition.COMMODITY.get(cond);
        Integer mod = ResourceDepositsCondition.MODIFIER.get(cond);
        if (com == null || mod == null) { com = CAEngine.DEPOSIT.get(cond); mod = CAEngine.DEPOSIT_MOD.get(cond); }
        if (com == null || mod == null) return null;
        Integer base = ResourceDepositsCondition.BASE_MODIFIER.get(com);
        if (base == null) base = CAEngine.BASE_MOD.containsKey(com) ? CAEngine.BASE_MOD.get(com) : 0;
        int size = ResourceDepositsCondition.BASE_ZERO.contains(com) ? 0 : m.getSize();
        return size + base + mod;
    }

    static String depositCommodity(String cond) {
        String com = ResourceDepositsCondition.COMMODITY.get(cond);
        return com != null ? com : CAEngine.DEPOSIT.get(cond);
    }

    /** Credit-equivalent per month of having this condition at this colony (negative = better without it). */
    public static float value(MarketAPI m, String cond, CAData.Profile p) {
        float[] w = CAEngine.goalWeights(p);
        float v = 0f;
        Float hz = HAZARD.get(cond);
        if (hz != null) {
            float upkeep = Math.max(0f, CAMemo.gross(m) - CAMemo.net(m));   // 0.3.20.1: kept inside a look
            v -= hz * upkeep;                         // upkeep scales with hazard
            v -= hz * 10f * w[6] / 2f;                // growth penalty
        }
        String com = depositCommodity(cond);
        Integer units = com == null ? null : depositUnits(m, cond);
        if (units != null && Global.getSettings().getCommoditySpec(com) != null) {
            float price = Global.getSettings().getCommoditySpec(com).getBasePrice();
            v += Math.max(0, units) * price * CAEngine.unitValue * 0.8f;
        }
        if (cond.equals("solar_array")) v += 3000f;
        if (cond.equals("volturnian_lobster_pens")) v += 6000f;
        CAData.Profile[] fits = WORLD_FITS.get(cond);
        if (fits != null) {
            v += 20000f;
            for (CAData.Profile f : fits) if (f == p) v += 60000f;
        }
        return v;
    }

    /** Value of going from the colony's current conditions to this set: everything gained less everything lost. */
    public static float change(MarketAPI m, Collection<String> after, CAData.Profile p, List<String> lines) {
        Set<String> now = new HashSet<String>();
        for (MarketConditionAPI c : m.getConditions()) now.add(c.getId());
        float v = 0f;
        for (String c : after) if (!now.contains(c)) { float x = value(m, c, p); v += x; if (lines != null && Math.abs(x) >= 1f) lines.add("Adds " + name(c) + ": " + CAEngine.valueText(x)); }
        for (String c : now) if (!after.contains(c)) { float x = -value(m, c, p); v += x; if (lines != null && Math.abs(x) >= 1f) lines.add("Removes " + name(c) + ": " + CAEngine.valueText(x)); }
        return v;
    }

    /**
     * 0.3.21.0: change() with a deposit no building uses (depositUsed) counted at 0, added or removed. For surplus terraforming:
     * farmland at a colony with no farm adds nothing.
     */
    public static float changeUsed(MarketAPI m, Collection<String> after, CAData.Profile p, CAPlan.Plan plan) {
        Set<String> now = new HashSet<String>();
        for (MarketConditionAPI c : m.getConditions()) now.add(c.getId());
        float v = 0f;
        for (String c : after) if (!now.contains(c) && depositUsed(m, c, plan)) v += value(m, c, p);
        for (String c : now) if (!after.contains(c) && depositUsed(m, c, plan)) v -= value(m, c, p);
        return v;
    }

    /**
     * 0.3.21.0: a deposit condition is used when a building at the colony makes its good, when the colony has Farming or
     * Mining (or an upgrade of one, Aquaculture for food), or when its plan builds one. Any other condition: true.
     */
    static boolean depositUsed(MarketAPI m, String cond, CAPlan.Plan plan) {
        String good = depositCommodity(cond);
        if (good == null) return true;
        String user = "food".equals(good) ? "farming" : "mining";
        for (Industry ind : m.getIndustries()) {
            String id = ind.getId();
            if (user.equals(id) || CAPlan.leadsTo(user, id) || ("farming".equals(user) && ("aquaculture".equals(id) || CAPlan.leadsTo("aquaculture", id)))) return true;
            try {
                for (com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity q : ind.getAllSupply())
                    if (good.equals(q.getCommodityId()) && q.getQuantity().getModifiedInt() > 0 && !"population".equals(id)) return true;
            } catch (Throwable ignored) { }
        }
        if (plan != null) for (CAPlan.Step st : plan.steps)
            if (st.kind == CAPlan.Kind.BUILD && st.id != null && (user.equals(st.id) || CAPlan.leadsTo(user, st.id))) return true;
        return false;
    }

    static String name(String cond) {
        try { return Global.getSettings().getMarketConditionSpec(cond).getName(); } catch (Throwable t) { return cond; }
    }
}
