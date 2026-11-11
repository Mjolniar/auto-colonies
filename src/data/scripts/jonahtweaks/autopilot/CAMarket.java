package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityMarketDataAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * The sector market model (0.2.44.0): what more of a good is really worth to you, by the game's own export rule
 * (CommodityMarketData in the core jar):
 * - every market exporting a good gets a weight: what it can ship off-world, min(supply, available, shipping capacity),
 *   times its accessibility; its share of the good's sector market is its weight over everyone's;
 * - its export income is that share times the good's sector market value, times the colony's income multiplier;
 * - your colonies selling to each other earn nothing (they cut upkeep instead, up to half, already at its most at every
 *   colony that has all its demand met in-faction).
 * So a fifth colony making ore splits the same pot four others already hold: the more of a market you have, the less
 * another unit adds, and a good few others make is worth most. Before this, every unit of anything was worth one flat
 * rate (your average export income per credit of base price), which is why the same producers went up everywhere.
 *
 * A colony's supply of a good is its largest single source, so a new building only adds what's above that; a building
 * under construction counts as made (pending claims, kept in the save) so two colonies don't start the same producer
 * months apart without either seeing the other. Cached once a day; Diagnostics gets a "market" section and a weekly check
 * of this model against the shares the game itself reports.
 */
public final class CAMarket {
    private CAMarket() { }

    public static boolean on() {
        try { return !CAData.settings().noMarketModel; } catch (Throwable t) { return false; }
    }

    // ---------------- the math (pure, checked offline by JTMarketCheck) ----------------

    /**
     * Your export income change when colony m's weight grows by dw: m goes from wm/T to (wm+dw)/(T+dw) of the market, and
     * your other legal exporters (their income-weighted weight othersIncW) are diluted from /T to /(T+dw).
     * V: the good's sector market value; incM: m's income multiplier; mult: your export multiplier for the good.
     */
    static float gain(float V, float T, float wm, float incM, float othersIncW, float dw, float mult) {
        if (dw <= 0f || V <= 0f) return 0f;
        float before = T > 0f ? (incM * wm + othersIncW) / T : 0f;
        float after = (incM * (wm + dw) + othersIncW) / (T + dw);
        return V * mult * (after - before);
    }

    /** A colony's supply of a good after a new building's line of units: its largest single source (plus the administrator's bonus on that line). */
    static int buildSupply(int units, int admin, int other) { return Math.max(units > 0 ? units + admin : 0, Math.max(0, other)); }

    /** After an upgrade changes the building's own line from own0 by delta: still the largest single source. */
    static int upgradeSupply(int own0, int delta, int other) { return Math.max(Math.max(0, own0 + delta), Math.max(0, other)); }

    /**
     * 0.3.0.0 (B1 fix): the colony's supply of a good once a pick's line holds units. A new building, or an upgrade passing the
     * target's whole line (whole: CAEngine.effects, e.g. Monoculture Plots' 3 food becoming Farming's 6), is a line of its
     * own with the administrator's bonus, the old building's line replaced (buildSupply); an upgrade passing a change (CAMods'
     * Union HQ, a probe measurement) changes the old building's own0 (upgradeSupply). Before, a whole line was stacked on the
     * old one (3 + 6), so the upgrade added 6 instead of 3.
     */
    static int supplyAfter(boolean upgrade, boolean whole, int own0, int units, int admin, int other) {
        return upgrade && !whole ? upgradeSupply(own0, units, other) : buildSupply(units, admin, other);
    }

    /** Weight change: only what can be shipped off-world counts. */
    static float weightDelta(int before, int after, int ship, float acc) {
        if (acc <= 0f) return 0f;
        int b = Math.min(before, ship), a = Math.min(after, ship);
        return Math.max(0, a - b) * acc;
    }

    // ---------------- the snapshot ----------------

    static class Mine {
        MarketAPI m; float w, inc, acc; int supply, ship; boolean illegal;
    }

    static class Snap {
        String com; float value, total, mult, mine; List<Mine> colonies = new ArrayList<Mine>();
        Mine at(MarketAPI m) { for (Mine x : colonies) if (x.m == m) return x; return null; }
        /** Your share of the good's market, 0 to 1. */
        float share() { return total > 0f ? mine / total : 0f; }
        /** Sum of inc x w over your legal exporters other than m. */
        float othersIncW(MarketAPI m) {
            float s = 0f;
            for (Mine x : colonies) if (x.m != m && !x.illegal) s += x.inc * x.w;
            return s;
        }
    }

    static final Map<String, Snap> cache = new HashMap<String, Snap>();
    static float cacheDay = -99f;

    static void invalidate() { cache.clear(); }

    /** 0.3.0.0 (B1): once a day, the snapshots go and finished or stale claims are dropped (snap() and CAValue both call it). */
    static void daily() {
        float today = (float) Math.floor(CADay.now());
        if (today != cacheDay) { cache.clear(); cacheDay = today; prune(); }
    }

    static float accessibility(MarketAPI k) {
        try { return Math.max(0f, k.getAccessibilityMod().computeEffective(0f)); } catch (Throwable t) { return 0f; }
    }

    /** A market's weight in the good's market, as the game computes it. */
    static float weight(CommodityMarketDataAPI cmd, MarketAPI k, CommodityOnMarketAPI c, int pendingSupply) {
        int units = Math.max(Math.min(c.getMaxSupply(), c.getAvailable()), pendingSupply);
        int ship = cmd.getMaxShipping(k, false);
        return Math.max(0, Math.min(units, ship)) * accessibility(k);
    }

    /** The good's market today, or null when the game has no market data for it (a non-economy good). */
    static Snap snap(String com) {
        daily();
        if (cache.containsKey(com)) return cache.get(com);
        Snap s = null;
        try { s = build(com); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "market model failed for " + com, t); }
        cache.put(com, s);
        return s;
    }

    static Snap build(String com) {
        List<MarketAPI> mine = Misc.getPlayerMarkets(false);
        if (mine.isEmpty()) return null;
        CommodityOnMarketAPI any = mine.get(0).getCommodityData(com);
        if (any == null || any.isNonEcon()) return null;
        CommodityMarketDataAPI cmd = any.getCommodityMarketData();
        if (cmd == null) return null;
        Snap s = new Snap();
        s.com = com;
        s.value = cmd.getMarketValue();
        s.mult = 1f;
        try { s.mult = Global.getSector().getPlayerStats().getDynamic().getValue(Stats.getCommodityExportCreditsMultId(com)); } catch (Throwable ignored) { }
        Set<MarketAPI> seen = new HashSet<MarketAPI>();
        for (MarketAPI k : cmd.getMarkets()) {
            CommodityOnMarketAPI c = k.getCommodityData(com);
            if (c == null) continue;
            seen.add(k);
            boolean player = k.isPlayerOwned() || k.getFaction() == Global.getSector().getPlayerFaction();
            int pend = player ? pendingAt(k, com) : 0;
            float w = weight(cmd, k, c, pend);
            s.total += w;
            if (player) s.colonies.add(mine(cmd, k, c, w, pend, com));
        }
        // a colony of yours the good's market data doesn't list yet (founded this month): it adds nothing until it does
        for (MarketAPI k : mine) {
            if (seen.contains(k)) continue;
            CommodityOnMarketAPI c = k.getCommodityData(com);
            if (c != null) s.colonies.add(mine(cmd, k, c, 0f, pendingAt(k, com), com));
        }
        for (Mine x : s.colonies) s.mine += x.w;
        return s;
    }

    static Mine mine(CommodityMarketDataAPI cmd, MarketAPI k, CommodityOnMarketAPI c, float w, int pend, String com) {
        Mine x = new Mine();
        x.m = k; x.w = w; x.acc = accessibility(k);
        x.supply = Math.max(c.getMaxSupply(), pend);
        x.ship = cmd.getMaxShipping(k, false);
        try { x.inc = k.getIncomeMult().getModifiedValue(); } catch (Throwable t) { x.inc = 1f; }
        try { x.illegal = cmd.getMarketShareData(k) != null && cmd.getMarketShareData(k).isSourceIsIllegal(); } catch (Throwable t) { x.illegal = false; }
        return x;
    }

    // ---------------- one pick's line of a good ----------------

    /** What a pick's line adds: value per month (export income change), units added at the colony, shares before and after. */
    public static class Gain {
        float value; int addedBefore, addedAfter, after; float shareBefore, shareAfter; boolean capped;
        /** 0.3.0.0 (B1): the colony's supply of the good before the pick (base) and before this call's units (before), for CAValue's relief. */
        int base, before;
    }

    /** One building's own supply of a good. */
    static int own(Industry ind, String com) {
        if (ind == null) return 0;
        for (MutableCommodityQuantity q : ind.getAllSupply())
            if (com.equals(q.getCommodityId())) return Math.max(0, q.getQuantity().getModifiedInt());
        return 0;
    }

    /**
     * The colony's largest source of a good other than skip, with any claimed building under construction there. 0.3.0.0 (B1):
     * a claim for skip itself (its upgrade under way) is not another source.
     */
    static int largestOther(MarketAPI m, Industry skip, String com) {
        int best = 0;
        for (Industry ind : m.getIndustries()) if (ind != skip) best = Math.max(best, own(ind, com));
        return Math.max(best, pendingAt(m, com, skip == null ? null : skip.getId()));
    }

    /**
     * 0.3.0.0 (B1, research 02 section 9): what the colony's supply of a good going from before to after (its largest source,
     * so a building that isn't the biggest changes nothing) does to your export income a month: the share rule both ways,
     * gain() up and keepValue() down. Null without data for the good; 0 where it's illegal (an illegal source exports nothing).
     */
    static Float lineValue(MarketAPI m, String com, int before, int after) {
        Snap s = snap(com);
        if (s == null) return null;
        if (after == before) return 0f;
        Mine here = s.at(m);
        if (here == null || here.illegal) return 0f;
        float others = s.othersIncW(m);
        if (after > before) return gain(s.value, s.total, here.w, here.inc, others, weightDelta(before, after, here.ship, here.acc), s.mult);
        return -keepValue(s.value, s.total, here.w, here.inc, others, weightDelta(after, before, here.ship, here.acc), s.mult);
    }

    /**
     * The value of a pick's line of units of a good at m (CAEngine.supply), counting what the pick already supplied of it
     * (supply can be called more than once per good). Null when the model has nothing to go on; the caller falls back to
     * the flat rate. 0.3.0.0 (B1): units may be negative (CAEngine.supplyChange: a management style's cut), and what the line
     * adds over the colony's other sources is kept in p.added for the endgame's production chains (research 02 section 9).
     */
    static Gain pickGain(MarketAPI m, CAEngine.Pick p, String com, int units) {
        Snap s = snap(com);
        if (s == null) return null;
        Integer was = p.marketUnits.get(com);
        int prior = was == null ? 0 : was, cum = Math.max(0, prior + units);
        p.marketUnits.put(com, cum);
        Mine here = s.at(m);
        int ship = here != null ? here.ship : 0;
        float acc = here != null ? here.acc : accessibility(m);
        float wm = here != null ? here.w : 0f, inc = here != null ? here.inc : 1f;
        boolean up = p.upgrade && p.existing != null;
        boolean whole = up && p.wholeLines;   // 0.3.0.0 (B1 fix): the target's whole line replaces the old building's (supplyAfter)
        int own0 = up ? own(p.existing, com) : 0;
        int other = up ? largestOther(m, p.existing, com) : (here != null ? here.supply : largestOther(m, null, com));
        int admin = up && !whole ? 0 : CAMods.adminBonus(m);
        int base0 = up ? upgradeSupply(own0, 0, other) : Math.max(0, other);
        int sPrior = prior == 0 ? base0 : supplyAfter(up, whole, own0, prior, admin, other);
        int sCum = supplyAfter(up, whole, own0, cum, admin, other);
        float others = s.othersIncW(m);
        float dwPrior = weightDelta(base0, sPrior, ship, acc), dwCum = weightDelta(base0, sCum, ship, acc);
        Gain g = new Gain();
        g.value = gain(s.value, s.total, wm, inc, others, dwCum, s.mult) - gain(s.value, s.total, wm, inc, others, dwPrior, s.mult);
        g.addedBefore = Math.max(0, sPrior - base0);
        g.addedAfter = Math.max(0, sCum - base0);
        g.after = sCum;
        g.capped = sCum > ship && sPrior < sCum;
        g.shareBefore = s.share();
        g.shareAfter = (s.mine + dwCum) / Math.max(1e-6f, s.total + dwCum);
        g.base = base0; g.before = sPrior;   // 0.3.0.0 (B1)
        p.added.put(com, g.addedAfter);      // 0.3.0.0 (B1): netted units, for CAValue.chainUnits
        p.marketAfter.put(com, sCum);
        Float v = p.marketValue.get(com);
        p.marketValue.put(com, (v == null ? 0f : v) + g.value);
        return g;
    }

    /**
     * 0.3.0.0 (B1 fix): the pick's own line of a good once built (0 when it has none the model valued): a new building's or a
     * whole-line upgrade's units with the administrator's bonus, or the old building's line changed by a delta upgrade's.
     * For CAValue.synergy: whether a synergy's extra output lands on the pick itself.
     */
    static int pickLevel(MarketAPI m, CAEngine.Pick p, String com) {
        Integer u = p.marketUnits.get(com);
        if (u == null || u <= 0) return 0;
        if (p.upgrade && p.existing != null && !p.wholeLines) return Math.max(0, own(p.existing, com) + u);
        return u + CAMods.adminBonus(m);
    }

    /**
     * 0.3.0.0 (B1 fix): pickGain for n more units on the pick's line, worked out on a copy so the pick itself is unchanged
     * (CASynergies adds the value to its own sum). Null when the model has nothing to go on.
     */
    static Gain pickGainMore(MarketAPI m, CAEngine.Pick p, String com, int n) {
        CAEngine.Pick s = new CAEngine.Pick();
        s.id = p.id; s.upgrade = p.upgrade; s.existing = p.existing; s.wholeLines = p.wholeLines;
        s.marketUnits.putAll(p.marketUnits);
        return pickGain(m, s, com, n);
    }

    // ---------------- a building already there (CAPrune, 0.2.44.1) ----------------

    /** What a source of dw weight already in the market earns you: your income with it, less without it. */
    static float keepValue(float V, float T, float wm, float incM, float othersIncW, float dw, float mult) {
        float d = Math.min(dw, Math.max(0f, wm));
        if (d <= 0f) return 0f;
        return gain(V, Math.max(0f, T - d), wm - d, incM, othersIncW, d, mult);
    }

    /** A building's export worth at m: own units of the good where the colony's other biggest source makes other. Null without data. */
    static Float keepExport(MarketAPI m, String com, int own, int other) {
        Snap s = snap(com);
        if (s == null) return null;
        Mine here = s.at(m);
        if (here == null || here.illegal) return 0f;
        int without = Math.max(0, other), with = Math.max(own, without);
        float dw = weightDelta(without, with, here.ship, here.acc);
        return keepValue(s.value, s.total, here.w, here.inc, s.othersIncW(m), dw, s.mult);
    }

    // ---------------- buildings under construction ----------------

    static String key(MarketAPI m, CAEngine.Pick p) {
        return m.getId() + "|" + p.id + "|" + (p.upgrade && p.existing != null ? p.existing.getId() : "");
    }

    /** A pick just started: its goods count as made at that colony until it's finished. */
    static void claim(MarketAPI m, CAEngine.Pick p) {
        if (p.marketAfter.isEmpty()) return;
        CAData d = CAData.get();
        d.marketPending().put(key(m, p), new HashMap<String, Integer>(p.marketAfter));
        d.marketPendingSince().put(key(m, p), CADay.now());
        invalidate();
    }

    static int pendingAt(MarketAPI m, String com) { return pendingAt(m, com, null); }

    /** 0.3.0.0 (B1): as above, leaving out claims for the building skipId (as the new building or the one being upgraded). */
    static int pendingAt(MarketAPI m, String com, String skipId) {
        int best = 0;
        try {
            String pre = m.getId() + "|";
            for (Map.Entry<String, Map<String, Integer>> e : CAData.get().marketPending().entrySet()) {
                if (!e.getKey().startsWith(pre) || claimFor(e.getKey(), skipId)) continue;
                Integer u = e.getValue().get(com);
                if (u != null) best = Math.max(best, u);
            }
        } catch (Throwable ignored) { }
        return best;
    }

    /** 0.3.0.0 (B1): whether a claim key (market|building|upgraded building) belongs to building id. */
    static boolean claimFor(String key, String id) {
        if (id == null || key == null) return false;
        String[] k = key.split("\\|", -1);
        return k.length == 3 && (id.equals(k[1]) || id.equals(k[2]));
    }

    /**
     * 0.3.0.0 (B1, research 02 section 7): every claim as good -> colony -> the colony's supply of it once built, for CAValue's
     * pending-aware shortages. skipMarket/skipId leave out the claims of one building (keep() asks about that building).
     */
    static Map<String, Map<String, Integer>> claimLevels(String skipMarket, String skipId) {
        Map<String, Map<String, Integer>> out = new HashMap<String, Map<String, Integer>>();
        try {
            for (Map.Entry<String, Map<String, Integer>> e : CAData.get().marketPending().entrySet()) {
                String[] k = e.getKey().split("\\|", -1);
                if (k.length != 3 || e.getValue() == null) continue;
                if (skipMarket != null && skipMarket.equals(k[0]) && claimFor(e.getKey(), skipId)) continue;
                for (Map.Entry<String, Integer> u : e.getValue().entrySet()) {
                    if (u.getValue() == null || u.getValue() <= 0) continue;
                    Map<String, Integer> at = out.get(u.getKey());
                    if (at == null) { at = new HashMap<String, Integer>(); out.put(u.getKey(), at); }
                    Integer was = at.get(k[0]);
                    if (was == null || u.getValue() > was) at.put(k[0], u.getValue());
                }
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** Drops claims whose building is finished, gone, or a year old. */
    static void prune() {
        try {
            CAData d = CAData.get();
            float now = CADay.now();
            for (Iterator<Map.Entry<String, Map<String, Integer>>> it = d.marketPending().entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Map<String, Integer>> e = it.next();
                String[] k = e.getKey().split("\\|", -1);
                Float since = d.marketPendingSince().get(e.getKey());
                boolean keep = false;
                if (k.length == 3 && (since == null || now - since < CATuning.MARKET_CLAIM_DAYS)) {
                    MarketAPI m = Global.getSector().getEconomy().getMarket(k[0]);
                    if (m != null && m.isPlayerOwned()) {
                        Industry ind = m.getIndustry(k[1]);
                        Industry old = k[2].isEmpty() ? null : m.getIndustry(k[2]);
                        keep = (ind != null && ind.isBuilding() && !ind.isUpgrading()) || (old != null && old.isUpgrading());
                    }
                }
                if (!keep) { it.remove(); d.marketPendingSince().remove(e.getKey()); }
            }
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "market model: pending builds check failed", t); }
    }

    /**
     * Of a pick's export value, the part from goods in claimed: the same-pass check in CAAuto. 0.3.0.0 (B1, research 02 section
     * 7): p.marketValue holds the shortage relief too (CAEngine.supply), so a pick that only covers a shortage another colony
     * just claimed is caught as well.
     */
    static float claimedPart(CAEngine.Pick p, Set<String> claimed) {
        float all = 0f, part = 0f;
        for (Map.Entry<String, Float> e : p.marketValue.entrySet()) {
            if (e.getValue() <= 0f) continue;
            all += e.getValue();
            if (claimed.contains(e.getKey())) part += e.getValue();
        }
        return all > 0f ? part / all : 0f;
    }

    // ---------------- Diagnostics ----------------

    static String pct(float f) { return Math.round(f * 100f) + "%"; }

    static String name(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getName(); } catch (Throwable t) { return com; }
    }

    static List<String> goods() {
        List<String> out = new ArrayList<String>();
        try {
            for (com.fs.starfarer.api.campaign.econ.CommoditySpecAPI c : Global.getSettings().getAllCommoditySpecs())
                if (!c.isNonEcon() && !c.isMeta() && !c.isPersonnel()) out.add(c.getId());
        } catch (Throwable ignored) { }
        return out;
    }

    /** What one more unit at your best-placed colony would add a month. */
    static float bestUnit(Snap s) {
        float best = 0f;
        for (Mine x : s.colonies) {
            if (x.illegal || x.acc <= 0f || x.supply >= x.ship) continue;
            best = Math.max(best, gain(s.value, s.total, x.w, x.inc, s.othersIncW(x.m), x.acc, s.mult));
        }
        return best;
    }

    /** The report's "market" section: per good, its sector value, your share, your exporters, and what +1 unit adds. */
    public static JSONArray json() throws Exception {
        JSONArray arr = new JSONArray();
        if (Global.getSector() == null) return arr;
        for (String com : goods()) {
            Snap s = snap(com);
            if (s == null || s.value <= 0f) continue;
            JSONObject o = new JSONObject();
            o.put("good", com); o.put("marketValue", Math.round(s.value)); o.put("yourShare", Math.round(s.share() * 1000f) / 10f);
            JSONArray ex = new JSONArray();
            for (Mine x : s.colonies) if (x.w > 0f) ex.put(x.m.getName() + " " + x.supply + (x.illegal ? " (illegal, earns nothing)" : "") + " = "
                    + Math.round(x.w / Math.max(1e-6f, s.total) * 1000f) / 10f + "%");
            o.put("yourExporters", ex);
            o.put("plusOneUnit", Math.round(bestUnit(s)));
            arr.put(o);
        }
        return arr;
    }

    static float lastWeek = Float.NaN;   // 0.3.0.0 (A1 fix): never fired; the game's day is negative, so -99 read as a later date

    /**
     * Weekly: checks the model's shares against the game's (getExportMarketSharePercent, which rounds to whole percents)
     * and writes one line on where you hold most and the biggest markets you don't supply. starsector.log only.
     */
    public static void weekly(float now) {
        if (!on() || !CATime.due(lastWeek, now, 7f) || Global.getSector() == null) return;   // 0.3.0.0 (A1 fix): on the game's own (negative) clock
        lastWeek = now;
        invalidate();
        int checked = 0, off = 0;
        StringBuilder bad = new StringBuilder();
        List<Snap> all = new ArrayList<Snap>();
        for (String com : goods()) {
            Snap s = snap(com);
            if (s == null || s.value <= 0f) continue;
            all.add(s);
            CommodityMarketDataAPI cmd = null;
            for (Mine x : s.colonies) {
                if (x.w <= 0f) continue;
                try {
                    if (cmd == null) cmd = x.m.getCommodityData(com).getCommodityMarketData();
                    if (pendingAt(x.m, com) > 0) continue;   // the model counts a building the game doesn't yet
                    int game = cmd.getExportMarketSharePercent(x.m);
                    float model = 100f * x.w / Math.max(1e-6f, s.total);
                    checked++;
                    if (Math.abs(model - game) > 2f) {
                        off++;
                        if (off <= 3) bad.append(off > 1 ? "; " : "").append(x.m.getName()).append(" ").append(name(com)).append(" ")
                                .append(Math.round(model)).append("% vs the game's ").append(game).append("%");
                    }
                } catch (Throwable ignored) { }
            }
        }
        if (checked > 0) {
            String line = CADiagnostics.TAG + "market model: " + (checked - off) + " of " + checked + " colony exports within 2 points of the game's share"
                    + (off > 0 ? " (" + bad + ")" : "");
            if (off > 0) CADiagnostics.LOG.warn(line); else CADiagnostics.LOG.info(line);
        }
        Collections.sort(all, new Comparator<Snap>() { public int compare(Snap a, Snap b) { return Float.compare(b.share(), a.share()); } });
        StringBuilder held = new StringBuilder(), open = new StringBuilder();
        int n = 0;
        for (Snap s : all) {
            if (n >= 3 || s.share() < 0.2f) break;
            int count = 0;
            for (Mine x : s.colonies) if (x.w > 0f) count++;
            held.append(n++ > 0 ? ", " : "").append(pct(s.share())).append(" of ").append(name(s.com)).append(" (").append(count)
                    .append(count == 1 ? " colony)" : " colonies)");
        }
        Collections.sort(all, new Comparator<Snap>() { public int compare(Snap a, Snap b) { return Float.compare(b.value, a.value); } });
        n = 0;
        for (Snap s : all) {
            if (n >= 3) break;
            if (s.mine > 0f) continue;
            open.append(n++ > 0 ? ", " : "").append(name(s.com)).append(" ").append(Misc.getDGSCredits(s.value));
        }
        if (held.length() > 0 || open.length() > 0)
            CADiagnostics.LOG.info(CADiagnostics.TAG + "market: " + (held.length() > 0 ? "you hold " + held : "no big share anywhere")
                    + (open.length() > 0 ? "; biggest markets you don't supply: " + open : ""));
    }
}
