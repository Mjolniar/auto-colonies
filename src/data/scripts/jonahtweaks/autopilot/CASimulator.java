package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import java.util.*;

/**
 * Measures what a mod building does by building it on a throwaway probe market: same size and vanilla conditions as
 * the colony, but no planet or location, so its code can't place anything in the sector. Reads the building's own
 * supply and demand and the market-level changes (stability, accessibility, income and upkeep multipliers, ground
 * defense, fleet size and patrols, ship quality). A building whose code fails here, or that registers sector scripts
 * or intel while being built, is reported as "can't be measured" and what it added is removed. Effects that only
 * happen over time (monthly items, spawned fleets) aren't seen, so measured values lean low, never high.
 */
public class CASimulator {

    /** One measurement. fx uses CAEngine's units: [0] credits/month (filled in later), [1] stability, [2] accessibility,
     *  [3] ground defense multiplier, [4] fleet, [5] ship quality, [6] growth. */
    public static class Effects {
        public Map<String, Integer> supply = new LinkedHashMap<String, Integer>(), demand = new LinkedHashMap<String, Integer>();
        public float stability, access, ground, fleet, quality, incomeMult, upkeepMult;
        public String error;
        public String summary() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> e : supply.entrySet()) add(sb, "+" + e.getValue() + " " + e.getKey());
            for (Map.Entry<String, Integer> e : demand.entrySet()) add(sb, "needs " + e.getValue() + " " + e.getKey());
            if (stability != 0f) add(sb, sign(stability, 1) + " stability");
            if (access != 0f) add(sb, sign(access * 100f, 0) + "% accessibility");
            if (incomeMult != 0f) add(sb, sign(incomeMult * 100f, 0) + "% income");
            if (upkeepMult != 0f) add(sb, sign(upkeepMult * 100f, 0) + "% upkeep");
            if (ground != 0f) add(sb, "ground defense " + sign(ground, 1) + "x");
            if (fleet != 0f) add(sb, "fleet " + sign(fleet, 2));
            if (quality != 0f) add(sb, sign(quality * 100f, 0) + "% ship quality");
            return sb.length() == 0 ? "no immediate effect" : sb.toString();
        }
        static void add(StringBuilder sb, String s) { if (sb.length() > 0) sb.append(", "); sb.append(s); }
        static String sign(float v, int dec) { String s = dec == 0 ? "" + Math.round(v) : String.format(Locale.ROOT, "%." + dec + "f", v); return v > 0 ? "+" + s : s; }
    }

    static final Map<String, Effects> cache = new HashMap<String, Effects>();
    static int cacheMonth = -1;

    public static void reset() { cache.clear(); }

    /** Measured effects of building this at the colony (null colony: a generic size-6 colony). Cached for the month. */
    public static Effects measure(MarketAPI colony, String industryId) {
        int month = Global.getSector().getClock().getMonth();
        if (month != cacheMonth) { cache.clear(); cacheMonth = month; }
        String key = industryId + "@" + (colony == null ? "generic" : colony.getId() + ":" + colony.getSize());
        Effects e = cache.get(key);
        if (e == null) { e = run(colony, industryId); cache.put(key, e); }
        return e;
    }

    static MarketAPI probe(MarketAPI colony) {
        MarketAPI p = Global.getFactory().createMarket("jt_ca_sim", "Autopilot Simulation", colony == null ? 6 : colony.getSize());
        p.setFactionId(Factions.PLAYER);
        if (colony != null) {
            for (MarketConditionAPI c : colony.getConditions()) {
                MarketConditionSpecAPI spec = Global.getSettings().getMarketConditionSpec(c.getId());
                if (spec == null || spec.getSourceMod() != null) continue;   // vanilla deposits and hazards only; mod conditions may touch the sector
                // some vanilla conditions only work with the intel they were added with (Pather cells, pirate activity, a
                // blockade); copied without it they fail on every apply, and every measurement with them, so they're left off
                String token = null;
                try { token = p.addCondition(c.getId()); p.reapplyCondition(token); }
                catch (Throwable t) {
                    if (token != null) try { p.removeSpecificCondition(token); } catch (Throwable ignored) { }
                }
            }
        }
        p.reapplyConditions();
        stock(p);
        return p;
    }

    /**
     * Every commodity on hand at the probe colony. It isn't in the economy, so nothing is ever available there, and a building
     * that cuts its output by its input shortage (BaseIndustry.getMaxDeficit, applyDeficitToProduction) measured as making
     * little or nothing: AotD's Artisanal Farming read no food at all. Inputs are valued separately (CAEngine.demand), so the
     * measurement wants full output (2.38.0).
     */
    static void stock(MarketAPI p) {
        List<com.fs.starfarer.api.campaign.econ.CommoditySpecAPI> all = null;
        try { all = Global.getSettings().getAllCommoditySpecs(); } catch (Throwable ignored) { }
        if (all == null) return;
        for (com.fs.starfarer.api.campaign.econ.CommoditySpecAPI c : all) {
            try { p.getCommodityData(c.getId()).getAvailableStat().modifyFlat("jt_ca_probe_stock", 999f); }
            catch (Throwable ignored) { }
        }
    }

    static float[] snapshot(MarketAPI p) {
        return new float[]{
                p.getStability().getModifiedValue(),
                p.getAccessibilityMod().computeEffective(0f),
                p.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(1f),
                p.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(1f)
                        + 0.1f * p.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).computeEffective(0f)
                        + 0.2f * p.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).computeEffective(0f)
                        + 0.3f * p.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).computeEffective(0f),
                p.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).computeEffective(0f),
                p.getIncomeMult().getModifiedValue(),
                p.getUpkeepMult().getModifiedValue()};
    }

    static Effects run(MarketAPI colony, String industryId) {
        Effects e = new Effects();
        List<EveryFrameScript> scripts = new ArrayList<EveryFrameScript>(Global.getSector().getScripts());
        List<IntelInfoPlugin> intel = new ArrayList<IntelInfoPlugin>(Global.getSector().getIntelManager().getIntel());
        MarketAPI p = null;
        try {
            p = probe(colony);
            float[] before = snapshot(p);
            p.addIndustry(industryId);
            Industry ind = p.getIndustry(industryId);
            if (ind == null) { e.error = "the game refused to add it to a probe colony"; return e; }
            p.reapplyIndustries();
            p.reapplyConditions();
            float[] after = snapshot(p);
            for (MutableCommodityQuantity q : ind.getAllSupply()) {
                int u = q.getQuantity().getModifiedInt();
                if (u > 0) e.supply.put(q.getCommodityId(), u);
            }
            for (MutableCommodityQuantity q : ind.getAllDemand()) {
                int u = q.getQuantity().getModifiedInt();
                if (u > 0) e.demand.put(q.getCommodityId(), u);
            }
            e.stability = after[0] - before[0]; e.access = after[1] - before[1]; e.ground = after[2] - before[2];
            e.fleet = after[3] - before[3]; e.quality = after[4] - before[4]; e.incomeMult = after[5] - before[5];
            // the probe colony's upkeep multiplier rises by the same +25% whatever is built on it (all 57 measured buildings
            // in one game), so it's the probe, not the building: counting it cost every measured building a quarter of the
            // colony's upkeep (2.37.0). A building's own upkeep is predicted separately (CAEngine.upkeep).
            e.upkeepMult = 0f;
        } catch (Throwable t) {
            e.error = "its code failed on a probe colony: " + t;
        } finally {
            if (p != null) try { p.removeIndustry(industryId, null, false); } catch (Throwable ignored) { }   // even when it threw
            boolean leaked = false;
            for (EveryFrameScript s : new ArrayList<EveryFrameScript>(Global.getSector().getScripts()))
                if (!scripts.contains(s)) { Global.getSector().removeScript(s); leaked = true; }
            for (IntelInfoPlugin i : new ArrayList<IntelInfoPlugin>(Global.getSector().getIntelManager().getIntel()))
                if (!intel.contains(i)) { Global.getSector().getIntelManager().removeIntel(i); leaked = true; }
            if (leaked) {
                e.error = "it registers sector scripts or intel when built, so it isn't simulated";
                CADiagnostics.LOG.warn(CADiagnostics.TAG + industryId + " registered scripts or intel on the probe colony; removed them");
            }
        }
        return e;
    }

    /** Adds measured effects to a pick in CAEngine's units; an upgrade subtracts what the building it replaces does. */
    public static void apply(MarketAPI m, Effects e, Effects replaced, CAEngine.Pick p, Map<String, Integer> deficit) {
        Map<String, Integer> sup = new LinkedHashMap<String, Integer>(e.supply), dem = new LinkedHashMap<String, Integer>(e.demand);
        float stab = e.stability, acc = e.access, gr = e.ground, fl = e.fleet, q = e.quality, inc = e.incomeMult, upk = e.upkeepMult;
        if (replaced != null) {
            for (Map.Entry<String, Integer> x : replaced.supply.entrySet()) sup.put(x.getKey(), (sup.containsKey(x.getKey()) ? sup.get(x.getKey()) : 0) - x.getValue());
            for (Map.Entry<String, Integer> x : replaced.demand.entrySet()) dem.put(x.getKey(), (dem.containsKey(x.getKey()) ? dem.get(x.getKey()) : 0) - x.getValue());
            stab -= replaced.stability; acc -= replaced.access; gr -= replaced.ground; fl -= replaced.fleet; q -= replaced.quality;
            inc -= replaced.incomeMult; upk -= replaced.upkeepMult;
        }
        for (Map.Entry<String, Integer> x : sup.entrySet()) {
            if (Global.getSettings().getCommoditySpec(x.getKey()) == null) continue;
            if (x.getValue() > 0) CAEngine.supply(p, x.getKey(), x.getValue(), deficit);
        }
        for (Map.Entry<String, Integer> x : dem.entrySet()) {
            if (Global.getSettings().getCommoditySpec(x.getKey()) == null) continue;
            if (x.getValue() > 0) CAEngine.demand(p, x.getKey(), x.getValue(), deficit);
        }
        float gross = Math.max(0f, m.getGrossIncome());
        if (inc != 0f) p.fx[0] += inc * gross;
        if (upk != 0f) p.fx[0] -= upk * m.getIndustryUpkeep();
        p.fx[1] += stab; p.fx[2] += acc; p.fx[3] += gr; p.fx[4] += fl; p.fx[5] += q;
        p.lines.add("Measured on a probe colony: " + e.summary() + (replaced != null ? " (before replacing the current building)" : ""));
    }

    /** One-line report for Diagnostics. */
    public static String report(Effects e) {
        return e.error != null ? "can't be measured: " + e.error : "measured on a probe colony: " + e.summary();
    }
}
