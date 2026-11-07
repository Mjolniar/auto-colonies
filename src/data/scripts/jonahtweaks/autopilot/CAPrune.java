package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Industries that stopped paying (2.44.1). Daily, at every Auto colony not under siege: an industry whose whole worth is
 * what it makes, read live and valued the way a build is (CAMarket: only what's above the colony's own biggest other
 * source of each good, what it adds to your share of each sector market, what your colonies would be short of without
 * it), worth less than nothing after upkeep for 30 days in a row, is taken down; one per colony every 30 days. Godecifica's
 * and Zenemosrae's Roider Dives mined less ore and organics than the Mining already there, so they only cost upkeep.
 *
 * Never population, a station, military, improved, locked, being built or upgraded, one that adds stability, a plan step or
 * anything below one in its upgrade chain (the capital's Union HQ needs its Dives), a contract feeder, one with defense,
 * fleet or ship output, one the engine can't value, one that earns some other way (salvage, income), nor one that makes
 * nothing. Not in the endgame economy, which replaces weak industries itself. Since a build is valued the same way, what
 * goes doesn't come back. Its AI core and item go to the colony's Autopilot Storage. Setting: Take down industries that
 * stopped paying (Building and planning).
 */
public class CAPrune {
    static final float BELOW_DAYS = 30f, GAP_DAYS = 30f, EVERY_DAYS = 5f;
    /** Last look per colony; every few days is plenty for a 30-day rule, and a look reads the colony's whole plan. */
    static final Map<String, Float> looked = new HashMap<String, Float>();

    public static void run(List<MarketAPI> auto) {
        CASettings s = CAData.settings();
        if (s.noPrune) return;
        CAData d = CAData.get();
        float now = CADay.now();
        for (MarketAPI m : auto) {
            if (CAEngine.isOutpost(m) || CAHypershunt.is(m)) continue;
            Float last = looked.get(m.getId());
            if (last != null && now - last < EVERY_DAYS && now >= last) continue;
            looked.put(m.getId(), now);
            try { check(d, m); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "industry pruning check failed at " + m.getName(), t); }
        }
    }

    static float days(long since) { return Global.getSector().getClock().getElapsedDaysSince(since); }

    static class Keep {
        Industry ind; float value, export, relief, upkeep; boolean allDup; String bigger, goods;
    }

    static void check(CAData d, MarketAPI m) {
        if (CAEndgame.at(m)) return;
        CAData.Colony col = d.colony(m.getId());
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        float[] w = CAEngine.WEIGHTS[CAThreat.effective(m, col.profile).ordinal()];
        float mw = CASurplus.moneyWeight(m);
        long now = Global.getSector().getClock().getTimestamp();
        String pre = m.getId() + ":useless:";
        Set<String> seen = new HashSet<String>();
        Keep worst = null;
        for (Industry ind : m.getIndustries()) {
            String key = pre + ind.getId();
            seen.add(key);
            Keep k = keep(m, ind, col, plan, deficit, w, mw);
            if (k == null || k.value >= 0f) { d.endgameBuilt().remove(key); continue; }
            Long since = d.endgameBuilt().get(key);
            if (since == null) { d.endgameBuilt().put(key, now); continue; }
            if (days(since) < BELOW_DAYS) continue;
            if (worst == null || k.value < worst.value) worst = k;
        }
        for (Iterator<String> it = d.endgameBuilt().keySet().iterator(); it.hasNext(); ) {
            String k = it.next();
            if (k.startsWith(pre) && !seen.contains(k)) it.remove();
        }
        if (worst == null) return;
        Long last = d.endgameBuilt().get(m.getId() + ":pruned");
        if (last != null && days(last) < GAP_DAYS) return;
        takeDown(d, m, worst);
    }

    /** What keeping this industry is worth a month with the colony's weights, or null when it isn't one this step judges. */
    static Keep keep(MarketAPI m, Industry ind, CAData.Colony col, CAPlan.Plan plan, Map<String, Integer> deficit, float[] w, float mw) {
        String id = ind.getId();
        if (!ind.isIndustry() || ind.isHidden() || ind.isBuilding() || ind.isUpgrading() || ind.isImproved() || !ind.canShutDown()) return null;
        IndustrySpecAPI spec = ind.getSpec();
        if (spec.hasTag("population") || spec.hasTag("station") || spec.hasTag("military") || spec.hasTag("patrol") || spec.hasTag("command")
                || col.locks.contains(id) || CAMods.leftToYou(id) != null) return null;   // left to you: never taken down (2.45.0)
        if (CAEndgame.keepsPlanBonus(m, plan, id) || CAContracts.feeds(ind) != null) return null;
        if (plan != null) for (CAPlan.Step st : plan.steps) if (id.equals(st.id) || CAPlan.inChainBelow(id, st.id)) return null;
        if (CAOverLimit.stabilityOf(m, ind) > 0f) return null;
        // the engine's own reading of it, for everything besides what it makes
        CAEngine.Pick p = CAEngine.effectsOf(m, id, deficit);
        if (p == null && CAEngine.estimated(id)) {
            p = new CAEngine.Pick();
            p.id = id;
            p.recording = true;
            try { CAEngine.estimate(m, id, p, deficit); } catch (Throwable t) { p = null; }
        }
        if (p == null) return null;   // a building the engine can't value
        if (p.fx[3] > 0f || p.fx[4] > 0f || p.fx[5] > 0f || p.supplied.containsKey("ships")) return null;
        if (p.fx[0] - p.suppliedValue > 1f) return null;   // earns some other way (salvage, income) the market can't see
        Keep k = new Keep();
        k.ind = ind;
        k.allDup = true;
        boolean model = CAMarket.on();
        Set<String> bigger = new LinkedHashSet<String>();
        List<String> goods = new ArrayList<String>();
        int lines = 0;
        for (MutableCommodityQuantity q : ind.getAllSupply()) {
            int own = Math.max(0, q.getQuantity().getModifiedInt());
            if (own <= 0) continue;
            String com = q.getCommodityId();
            lines++;
            Industry top = null;
            int other = 0;
            for (Industry o : m.getIndustries()) {
                if (o == ind) continue;
                int u = CAMarket.own(o, com);
                if (u > other) { other = u; top = o; }
            }
            int added = model ? Math.max(0, Math.max(own, other) - other) : own;
            float base = 0f;
            try { base = Global.getSettings().getCommoditySpec(com).getBasePrice(); } catch (Throwable ignored) { }
            if (added > 0) k.allDup = false;
            else {
                if (top != null) bigger.add(top.getCurrentName());
                goods.add(CAMarket.name(com).toLowerCase(Locale.ROOT));
            }
            boolean legal = true;
            try { CommodityOnMarketAPI c = m.getCommodityData(com); legal = c == null || ind.isSupplyLegal(c); } catch (Throwable ignored) { }
            if (legal && added > 0) {
                Float e = model ? CAMarket.keepExport(m, com, own, other) : null;
                k.export += e != null ? e : base * CAEngine.unitValue * added;
            }
            k.relief += base * CAEngine.unitValue * coverIfRemoved(empireDemand(com), empireSupply(com), added);
        }
        if (lines == 0) return null;   // makes nothing: whatever it's worth is somewhere this step can't see
        try { k.upkeep = ind.getUpkeep().getModifiedValue(); } catch (Throwable t) { k.upkeep = 0f; }
        float rest = 0f;
        for (int i = 1; i < 7; i++) rest += w[i] * p.fx[i];
        k.value = w[0] * mw * (k.export + k.relief - k.upkeep) + rest;
        k.bigger = bigger.isEmpty() ? null : CAContracts.join(new ArrayList<String>(bigger));
        k.goods = goods.isEmpty() ? null : CAContracts.join(goods);
        return k;
    }

    /** Units your colonies would be short of if a source of added units went: the shortfall it leaves, less any there already. */
    static int coverIfRemoved(int demand, int supply, int added) {
        if (added <= 0) return 0;
        return Math.max(0, demand - (supply - added)) - Math.max(0, demand - supply);
    }

    static int empireDemand(String com) {
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) { CommodityOnMarketAPI c = m.getCommodityData(com); if (c != null) n += c.getMaxDemand(); }
        return n;
    }

    static int empireSupply(String com) {
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) { CommodityOnMarketAPI c = m.getCommodityData(com); if (c != null) n += c.getMaxSupply(); }
        return n;
    }

    static void takeDown(CAData d, MarketAPI m, Keep k) {
        Industry ind = k.ind;
        String core = ind.getAICoreId(), name = ind.getCurrentName(), id = ind.getId();
        SpecialItemData item = ind.getSpecialItem();
        CAStores.emptyInto(m, ind);   // core and item to this colony's Autopilot Storage
        m.removeIndustry(id, MarketAPI.MarketInteractionMode.REMOTE, false);
        long ts = Global.getSector().getClock().getTimestamp();
        d.endgameBuilt().put(m.getId() + ":pruned", ts);
        d.endgameBuilt().remove(m.getId() + ":useless:" + id);
        CAMarket.invalidate();
        String why = k.allDup && k.bigger != null && k.goods != null
                ? k.bigger + " already makes more " + k.goods + ", so it only cost upkeep (about " + Misc.getDGSCredits(k.upkeep) + " a month)"
                : "for a month it added about " + Misc.getDGSCredits(k.export + k.relief) + " a month against " + Misc.getDGSCredits(k.upkeep) + " upkeep";
        CALog.add(m.getName() + ": took down " + name + ": " + why
                + (core != null || item != null ? "; its " + (core != null && item != null ? "core and item" : core != null ? "core" : "item") + " to Autopilot Storage" : ""));
    }
}
