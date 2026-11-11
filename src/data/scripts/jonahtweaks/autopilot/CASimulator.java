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

    /**
     * 0.3.0.0 (A4, research 07 M5): kept for the session, keyed by what a measurement depends on (the building, the colony's
     * size and the vanilla conditions copied onto the probe), so colonies alike share one and nothing is measured again
     * on the 1st. Before, the whole cache was wiped every month and the month's first pass re-measured every measured
     * building at every colony (about 1 ms each). Each entry is still taken again after MEASURE_DAYS, so something that
     * changes a building's output over time (research, faction bonuses) is picked up, one entry at a time. Nothing measured
     * under research's what-if is kept (measure()).
     */
    static final Map<String, Effects> cache = new HashMap<String, Effects>();
    static final Map<String, Long> measuredAt = new HashMap<String, Long>();
    static final float MEASURE_DAYS = 90f;
    static long hits, misses;

    /** Clears the measurements. On load and on a rescan (CADiagnostics.runScan), so it also empties the engine's day caches,
     *  which depend on what's measured and supported (0.3.0.0, A4). */
    public static void reset() {
        cache.clear(); measuredAt.clear(); hits = 0; misses = 0;
        CAEngine.resetCaches();
    }

    /** Measured effects of building this at the colony (null colony: a generic size-6 colony). Kept for the session (above). */
    public static Effects measure(MarketAPI colony, String industryId) {
        List<String> conds;
        try { conds = colony == null ? Collections.<String>emptyList() : copied(colony); }
        catch (Throwable t) { conds = Collections.singletonList("?" + colony.getId()); }   // unreadable: kept for this colony alone; run() reports what fails
        String key = key(industryId, colony == null ? 6 : colony.getSize(), conds);
        Effects e = cache.get(key);
        Long at = measuredAt.get(key);
        boolean clock = Global.getSector() != null && Global.getSector().getClock() != null;
        if (e != null && (!clock || at == null || Global.getSector().getClock().getElapsedDaysSince(at) < maxAge(key))) { hits++; return e; }
        misses++;
        long t0 = System.nanoTime();
        e = run(colony, industryId);
        CAPerf.record("probe measurement", System.nanoTime() - t0);
        // 0.3.0.0 (A4 fix): never kept from research's what-if (CAEngine.noProbe), where every candidate tech is only marked
        // researched. AotD buildings read research in apply() (Mining Megaplex, Fuel Refinery), and an entry is shared by every
        // colony alike for up to four months, so a bonus you don't have would have ranked real upgrades and builds. Read, never written.
        if (!CAEngine.noProbe) {
            cache.put(key, e);
            if (clock) measuredAt.put(key, Global.getSector().getClock().getTimestamp());
        }
        return e;
    }

    /** MEASURE_DAYS plus up to a month more by key, so measurements made the same day don't all come due on the same day. */
    static float maxAge(String key) { return MEASURE_DAYS + (key.hashCode() & 31); }

    /** The cache key: the building, the size, and the copied conditions in sorted order (a colony lists them in any order). */
    static String key(String industryId, int size, Collection<String> copiedConditions) {
        List<String> c = new ArrayList<String>(copiedConditions);
        Collections.sort(c);
        StringBuilder b = new StringBuilder(industryId).append('@').append(size).append(':');
        for (int i = 0; i < c.size(); i++) { if (i > 0) b.append(','); b.append(c.get(i)); }
        return b.toString();
    }

    /** The colony's conditions a probe copies: vanilla ones (mod conditions may touch the sector) that aren't run by intel. */
    static List<String> copied(MarketAPI colony) {
        List<String> out = new ArrayList<String>();
        for (MarketConditionAPI c : colony.getConditions()) {
            MarketConditionSpecAPI spec = Global.getSettings().getMarketConditionSpec(c.getId());
            if (spec == null || spec.getSourceMod() != null) continue;
            if (intelBound(c.getId(), spec)) continue;
            out.add(c.getId());
        }
        return out;
    }

    /**
     * Vanilla conditions that only work with the intel or event they were added with: Pather cells, pirate and hostile
     * activity, a blockade, a food shortage, smuggling, a system bounty. Copied onto the probe without it they throw on
     * every apply (LuddicPathCells.apply reads intel.isSleeper()), which failed every building's measurement at a colony
     * with Pather cells (0.2.39.0). They're events, not deposits or hazards, so they're never copied.
     */
    static final Set<String> INTEL_BOUND = new HashSet<String>(Arrays.asList("pather_cells", "pirate_activity",
            "hostile_activity", "blockaded", "event_food_shortage", "event_smuggling", "event_bounty"));

    static boolean intelBound(String id, MarketConditionSpecAPI spec) {
        if (INTEL_BOUND.contains(id) || id.startsWith("event_")) return true;
        String cls = spec.getScriptClass();
        return cls != null && cls.contains(".campaign.intel.");
    }

    static MarketAPI probe(MarketAPI colony) {
        MarketAPI p = Global.getFactory().createMarket("jt_ca_sim", "Autopilot Simulation", colony == null ? 6 : colony.getSize());
        p.setFactionId(Factions.PLAYER);
        if (colony != null) {
            // vanilla deposits and hazards only (mod conditions may touch the sector), never one run by intel (copied())
            for (String id : copied(colony)) {
                // any other condition that fails here is taken back off. addCondition applies it right away, so when that's
                // what throws there's no token, and it's removed by id
                String token = null;
                try { token = p.addCondition(id); p.reapplyCondition(token); }
                catch (Throwable t) {
                    try { if (token != null) p.removeSpecificCondition(token); else p.removeCondition(id); } catch (Throwable ignored) { }
                }
            }
        }
        try { p.reapplyConditions(); }
        catch (Throwable t) {
            // a copied condition still fails: measure on a bare probe rather than fail every building
            List<MarketConditionAPI> on = p.getConditions();
            if (on != null) for (MarketConditionAPI c : new ArrayList<MarketConditionAPI>(on))
                try { p.removeSpecificCondition(c.getIdForPluginModifications()); } catch (Throwable ignored) { }
            try { p.reapplyConditions(); } catch (Throwable ignored) { }
        }
        stock(p);
        return p;
    }

    /**
     * Every commodity on hand at the probe colony. It isn't in the economy, so nothing is ever available there, and a building
     * that cuts its output by its input shortage (BaseIndustry.getMaxDeficit, applyDeficitToProduction) measured as making
     * little or nothing: AotD's Artisanal Farming read no food at all. Inputs are valued separately (CAEngine.demand), so the
     * measurement wants full output (0.2.38.0).
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
        // 0.3.0.0 (A4, 07 M5): identity sets, so the leak check below is one lookup per script instead of a list scan (it was O(n^2))
        Set<EveryFrameScript> scripts = Collections.newSetFromMap(new IdentityHashMap<EveryFrameScript, Boolean>());
        scripts.addAll(Global.getSector().getScripts());
        Set<IntelInfoPlugin> intel = Collections.newSetFromMap(new IdentityHashMap<IntelInfoPlugin, Boolean>());
        intel.addAll(Global.getSector().getIntelManager().getIntel());
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
            // colony's upkeep (0.2.37.0). A building's own upkeep is predicted separately (CAEngine.upkeep).
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
