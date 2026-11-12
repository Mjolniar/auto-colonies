package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.impl.BoostIndustryInstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.InstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.0.0 (WS-B1, research 02 sections 6, 7 and 9): one way to value anything, new or already there, so the builders and the
 * removers agree and nothing is built only to be taken down.
 *
 * - add(m, pick): what a new building or upgrade is worth a month: its effects (CAEngine.effects, valued on the 0.2.44.0
 *   market model by CAEngine.supply) at the colony's weights, the endgame's production chains on the units it really adds,
 *   confidence applied. The same number CAEngine.finish called "value".
 * - keep(m, building): the same math backwards: what the colony loses a month without it. Its live output lines, each only
 *   above the colony's largest other source, priced by export share (CAMarket.keepExport) plus the shortage it would leave;
 *   its own upkeep; everything else from the engine's reading of it, at the same weights.
 * - deficits(): your colonies' shortages, pending-aware: a building under construction counts as made at the supply level
 *   it will reach (Autopilot's claims, and anything started by hand), so two colonies never build for one shortage.
 * - boost(m, building, good, n): n more (or fewer) units on an existing building's line, netted the same way, for AI cores,
 *   colony items, story point improvements and synergies. 0.3.0.0 (B1 fix): synergy() puts a synergy's units on the pick's
 *   own line when the pick is the colony's biggest source; itemPlacement() scores where a colony item goes by what its units
 *   really add there.
 * - 0.3.0.0 (B1 fix, second pass): keep() also counts the Vaults of Knowledge synergies a building keeps going
 *   (keepSynergies: CASynergies.score run backwards), and an IndEvo producer valued by its flat estimate (Salvage Yards, the
 *   VPC buildings) is valued by that estimate both ways, netted the same way: the share of its line above the colony's other
 *   sources (addShare when it's built, keptShare once it's there).
 *
 * Shortage relief follows the game's own rule: a colony gets the best supply level in your faction (up to its demand), so a
 * source covers the shortage at every colony its level reaches, and a source below what a colony already gets covers
 * nothing there. Relief is never more than the units a line adds (as 0.2.44.0) nor the shortage itself. The add and keep sides
 * use one function each way (reliefAdd, reliefKeep), so a building's relief the day after it's built is what it was
 * scored with (JTValueCheck).
 *
 * Nothing here is saved: the snapshot is a day cache (CACache), claims are CAMarket's (ids and numbers in CAData).
 */
public final class CAValue {
    private CAValue() { }

    /** keep()'s answer for a building it can't value (no formula, estimate or measurement): priceless, never the weakest, never taken down for value. */
    public static final float UNKNOWN = Float.MAX_VALUE;

    public static boolean known(float v) { return !Float.isNaN(v) && v < UNKNOWN; }

    // ---------------- the math (pure, JTValueCheck) ----------------

    /** Net present value over the horizon: worth x (H - days/30) - cost - fee. Pure, offline-checked. */
    public static float npv(float worth, float horizonMonths, float days, float cost, float fee) {
        return worth * (horizonMonths - days / 30f) - cost - fee;
    }

    /** Units of shortage at these colonies: each is short of what it gets (avail) under what it needs (demand). */
    static int shortage(int[] demand, int[] avail) {
        int s = 0;
        for (int i = 0; i < demand.length; i++) s += Math.max(0, demand[i] - Math.max(0, avail[i]));
        return s;
    }

    /** Units of shortage a source at this supply level covers: every colony gets the best level in your faction, up to its demand. */
    static int coverAt(int[] demand, int[] avail, int level) {
        int c = 0;
        for (int i = 0; i < demand.length; i++) c += Math.max(0, Math.min(demand[i], level) - Math.min(demand[i], Math.max(0, avail[i])));
        return c;
    }

    /** What each colony gets once builds under way reach pending (the best level they'll bring), up to its demand. */
    static int[] withPending(int[] demand, int[] avail, int pending) {
        int[] out = new int[avail.length];
        for (int i = 0; i < avail.length; i++) out[i] = Math.max(Math.max(0, avail[i]), Math.min(demand[i], Math.max(0, pending)));
        return out;
    }

    /**
     * The add side: units of shortage a line covers when it takes its colony's supply of the good from base to level, adding
     * `added` units over the colony's other sources. Never more than it adds (0.2.44.0) nor the empire's shortage (lack), and
     * only what its level reaches at your colonies (a 3-unit source where a colony already gets 4 covers nothing there).
     * demand null: no per-colony data, the 0.2.44.0 rule min(added, lack).
     */
    static int reliefAdd(int[] demand, int[] avail, int base, int level, int added, int lack) {
        if (added <= 0 || lack <= 0) return 0;
        int cap = Math.min(added, lack);
        if (demand == null) return cap;
        return Math.max(0, Math.min(cap, coverAt(demand, avail, level) - coverAt(demand, avail, base)));
    }

    /**
     * The keep side, the same rule backwards: units your colonies would be short of without a line at `level`, the best level
     * left being `without` (the colony's other sources, your other colonies, builds under way). A colony that gets more than
     * the line makes gets it elsewhere; one that gets it from this line falls back to `without`. Never more than the units
     * the line adds over the colony's other sources (lost).
     */
    static int reliefKeep(int[] demand, int[] avail, int level, int without, int lost) {
        if (lost <= 0 || demand == null) return 0;
        int up = 0;
        for (int i = 0; i < demand.length; i++) {
            int a = Math.max(0, avail[i]);
            int aw = a > level ? a : Math.min(a, Math.max(0, without));
            up += Math.min(demand[i], a) - Math.min(demand[i], aw);
        }
        return Math.max(0, Math.min(lost, up));
    }

    /** The flat rate's shortage units (model off): of a pick's prior units of a good, units more (or fewer) cover this much more of lack. */
    static int flatCover(int prior, int units, int lack) {
        if (lack <= 0) return 0;
        int a = Math.max(0, prior), b = Math.max(0, prior + units);
        return Math.min(b, lack) - Math.min(a, lack);
    }

    /** What n more units on a building's own line (own) add to the colony's supply, its largest source, when another source makes other. */
    static int boostUnits(int own, int n, int other) {
        int o = Math.max(0, other);
        return Math.max(Math.max(0, own + n), o) - Math.max(Math.max(0, own), o);
    }

    /** What the colony's supply loses without a line of own units, its other biggest source making other. */
    static int lostUnits(int own, int other) { return Math.max(0, own - Math.max(0, other)); }

    /**
     * 0.3.0.0 (B1 fix): of a new line of units (plus the administrator's bonus on it), the share above the colony's other biggest
     * source (other). The same as keptShare on that line once it's there, so an estimate netted by it is kept at what it was
     * added at. Before, the share was of units alone (an administrator's +1 made it 4/6 where keeping it said 4/7).
     */
    static float addShare(int units, int admin, int other) {
        if (units <= 0) return 1f;
        int line = units + Math.max(0, admin), o = Math.max(0, other);
        return (float) Math.max(0, CAMarket.buildSupply(units, Math.max(0, admin), o) - o) / line;
    }

    /** 0.3.0.0 (B1 fix): of a live line of own units, the share the colony's supply would lose without it (1 for no line). */
    static float keptShare(int own, int other) { return own <= 0 ? 1f : (float) lostUnits(own, other) / own; }

    /** The goods an Industrial Evolution VPC chip makes, read from its id's suffix (IndEvo_vpc_marines_hand_weapons: marines and hand weapons). */
    static List<String> vpcGoods(String suffix, Collection<String> goods) {
        List<String> out = new ArrayList<String>();
        if (suffix == null) return out;
        String s = "_" + suffix + "_";
        for (String g : goods) if (g != null && s.contains("_" + g + "_")) out.add(g);
        return out;
    }

    /** Shortages per good from per-colony data and the best level builds under way will bring (pure; JTValueCheck). */
    static Map<String, Integer> shortages(Econ e, Map<String, Map<String, Integer>> pending) {
        Map<String, Integer> out = new HashMap<String, Integer>();
        for (Good g : e.goods.values()) {
            int s = shortage(g.demand, withPending(g.demand, g.avail, best(pending.get(g.com), null)));
            if (s > 0) out.put(g.com, s);
        }
        return out;
    }

    /**
     * Held trade contracts' shortfalls (AotD) on top: a good no colony is short of but a contract is gets 1, as 0.2.45 did,
     * unless builds under way already add at least the contract's gap of it.
     */
    static void withContracts(Map<String, Integer> out, Map<String, Integer> gaps, Map<String, Integer> pendingAdded) {
        if (gaps == null) return;
        for (Map.Entry<String, Integer> e : gaps.entrySet()) {
            if (out.containsKey(e.getKey())) continue;
            Integer more = pendingAdded == null ? null : pendingAdded.get(e.getKey());
            int gap = e.getValue() == null ? 1 : Math.max(1, e.getValue());
            if (more != null && more >= gap) continue;
            out.put(e.getKey(), 1);
        }
    }

    /** The highest level in a colony -> level map, leaving out one colony (null: none left out). */
    static int best(Map<String, Integer> levels, String skip) {
        int b = 0;
        if (levels != null) for (Map.Entry<String, Integer> e : levels.entrySet())
            if (e.getValue() != null && (skip == null || !skip.equals(e.getKey()))) b = Math.max(b, e.getValue());
        return b;
    }

    // ---------------- the per-colony snapshot ----------------

    /** One good across your colonies: each colony's demand, what it gets (demand less its shortage) and its own supply level. */
    static final class Good {
        final String com;
        final List<String> idList = new ArrayList<String>();
        final List<int[]> rows = new ArrayList<int[]>();
        String[] ids; int[] demand, avail, level;
        Good(String com) { this.com = com; }

        void done() {
            int n = rows.size();
            ids = idList.toArray(new String[n]);
            demand = new int[n]; avail = new int[n]; level = new int[n];
            for (int i = 0; i < n; i++) { demand[i] = rows.get(i)[0]; avail[i] = rows.get(i)[1]; level[i] = rows.get(i)[2]; }
        }

        int indexOf(String marketId) {
            for (int i = 0; i < ids.length; i++) if (ids[i].equals(marketId)) return i;
            return -1;
        }
    }

    /** The day's reading of every colony (outposts and Substance.Abuse drinks left out, as 0.2.45's shortages). */
    static final class Econ {
        final Map<String, Good> goods = new HashMap<String, Good>();
        /** marketId -> good -> units, for buildings under construction (not upgrades) the engine can read. */
        final Map<String, Map<String, Integer>> underway = new HashMap<String, Map<String, Integer>>();

        /** A colony's line for a good; its shortage kept exactly (demand at least the shortage), so the sum is the game's own. */
        void add(String marketId, String com, int demand, int deficit, int level) {
            int def = Math.max(0, deficit), d = Math.max(Math.max(0, demand), def);
            if (d <= 0 && level <= 0) return;
            Good g = goods.get(com);
            if (g == null) { g = new Good(com); goods.put(com, g); }
            g.idList.add(marketId);
            g.rows.add(new int[]{d, d - def, Math.max(0, level)});
        }

        void building(String marketId, String com, int units) {
            if (units <= 0) return;
            Map<String, Integer> at = underway.get(marketId);
            if (at == null) { at = new HashMap<String, Integer>(); underway.put(marketId, at); }
            Integer was = at.get(com);
            if (was == null || units > was) at.put(com, units);
        }

        Econ done() { for (Good g : goods.values()) g.done(); return this; }
    }

    /** The day's snapshot, kept like 0.2.45's shortages were (same memo name): by day, epoch and every colony's signature. */
    static final CACache.Memo<Econ> ECON = new CACache.Memo<Econ>("shortages", 4);
    /** Set while a snapshot is read: reading what's under construction runs the engine's formulas, which must not ask for another. */
    static boolean reading = false;

    /** On load (CAEngine.resetCaches, from CAReset): the memo itself is emptied by CACache.reset. 0.3.0.0 (B1 fix): the keep side's flags too. */
    static void reset() { reading = false; keeping = null; rawEstimates = false; }

    static Econ econ(int day, int ep, long empire) {
        if (Global.getSector() == null) return null;
        Econ e = day >= 0 ? ECON.get("empire", day, empire) : null;
        if (e == null) {
            if (reading) return null;
            reading = true;
            try { e = read(); }
            catch (Throwable t) { CAStep.warn("shortages", "reading your colonies' shortages failed; 0.2.45's count is used", t); e = null; }
            finally { reading = false; }
            if (e != null && day >= 0) ECON.put("empire", day, ep, empire, e);
        }
        return e;
    }

    /**
     * The snapshot for lines scored now: the one kept today at this epoch, read without working out every colony's
     * signature again (the evaluation that scores these lines just did, in empireDeficits); else a fresh read.
     */
    static Econ econNow() {
        int day = CAEngine.today();
        if (day < 0) return null;
        CACache.Entry<Econ> k = ECON.map.get("empire");
        if (k != null && k.value != null && k.day == day && k.epoch == CACache.epoch()) return k.value;
        return econ(day, CACache.epoch(), CAEngine.empireSig());
    }

    static Econ read() {
        Econ e = new Econ();
        Industry sup = CAEngine.supplier;
        MarketAPI at = CAEngine.supplierAt;
        CAEngine.scoring(null, null);   // the engine's formulas below must take the flat path, never the market model
        try {
            for (MarketAPI m : Misc.getPlayerMarkets(false)) {
                if (CAEngine.isOutpost(m)) continue;   // its own economy: your colonies can't supply it anyway
                for (CommodityOnMarketAPI c : m.getAllCommodities()) {
                    if (CABrewery.isDrink(c.getId())) continue;   // Substance.Abuse drinks: other colonies' demand has no penalty
                    e.add(m.getId(), c.getId(), c.getMaxDemand(), c.getDeficitQuantity(), c.getMaxSupply());
                }
                // a building under construction makes nothing yet; read what it will make (Autopilot's own claim it too)
                for (Industry ind : m.getIndustries()) {
                    if (!ind.isBuilding() || ind.isUpgrading()) continue;
                    try {
                        CAEngine.Pick p = CAEngine.effectsOf(m, ind.getId(), Collections.<String, Integer>emptyMap());
                        if (p != null) for (Map.Entry<String, Integer> u : p.supplied.entrySet()) e.building(m.getId(), u.getKey(), u.getValue());
                    } catch (Throwable ignored) { }
                }
            }
        } finally { CAEngine.scoring(sup, at); }
        return e.done();
    }

    // ---------------- builds under way ----------------

    /**
     * good -> colony -> its supply level once its builds under way finish: CAMarket's claims and the snapshot's underway.
     * Read fresh each time (a handful of claims), so a build started a moment ago counts at once.
     */
    static Map<String, Map<String, Integer>> pending(Econ e, String skipMarket, String skipId) {
        Map<String, Map<String, Integer>> out = CAMarket.claimLevels(skipMarket, skipId);
        // buildings under construction (not upgrades): keep() never asks about one, so none is left out here
        if (e != null) for (Map.Entry<String, Map<String, Integer>> at : e.underway.entrySet()) {
            for (Map.Entry<String, Integer> u : at.getValue().entrySet()) {
                Map<String, Integer> l = out.get(u.getKey());
                if (l == null) { l = new HashMap<String, Integer>(); out.put(u.getKey(), l); }
                Integer was = l.get(at.getKey());
                if (was == null || u.getValue() > was) l.put(at.getKey(), u.getValue());
            }
        }
        return out;
    }

    /** pending() for everyone, finished and stale claims dropped first (once a day, CAMarket.daily). */
    static Map<String, Map<String, Integer>> pendingNow(Econ e) {
        try { if (Global.getSector() != null) CAMarket.daily(); } catch (Throwable ignored) { }
        return pending(e, null, null);
    }

    /** Units builds under way add per good over what their colonies make today (for the contracts' rule). */
    static Map<String, Integer> pendingAdded(Econ e, Map<String, Map<String, Integer>> pend) {
        Map<String, Integer> out = new HashMap<String, Integer>();
        for (Map.Entry<String, Map<String, Integer>> g : pend.entrySet()) {
            Good good = e == null ? null : e.goods.get(g.getKey());
            int more = 0;
            for (Map.Entry<String, Integer> at : g.getValue().entrySet()) {
                int i = good == null ? -1 : good.indexOf(at.getKey());
                more += Math.max(0, at.getValue() - (i < 0 ? 0 : good.level[i]));
            }
            if (more > 0) out.put(g.getKey(), more);
        }
        return out;
    }

    /** One good as the relief rules read it: demand, availability raised to what builds under way bring, and supply levels. */
    static final class View {
        String[] ids; int[] demand, avail, level; int pending;
        /** The colonies' shortage today, before builds under way: 0 means any shortage a caller sees is a trade contract's. */
        int raw;
    }

    static View view(String com, Econ e, Map<String, Map<String, Integer>> pend) {
        if (e == null || com == null) return null;
        Good g = e.goods.get(com);
        if (g == null) return null;   // none of your colonies makes or needs it
        View v = new View();
        v.ids = g.ids; v.demand = g.demand; v.level = g.level;
        v.pending = best(pend == null ? null : pend.get(com), null);
        v.avail = withPending(g.demand, g.avail, v.pending);
        v.raw = shortage(g.demand, g.avail);
        return v;
    }

    // ---------------- shortages ----------------

    /** Your colonies' shortages, pending-aware (CAEngine.empireDeficits is the same map). */
    public static Map<String, Integer> deficits() { return CAEngine.empireDeficits(); }

    static Map<String, Integer> deficits(int day, int ep, long empire) {
        if (Global.getSector() == null) return new HashMap<String, Integer>();
        Econ e = econ(day, ep, empire);
        Map<String, Integer> out;
        Map<String, Integer> more = null;
        if (e == null) out = CAEngine.colonyDeficits();   // re-entered while reading, or the read failed: 0.2.45's count
        else {
            Map<String, Map<String, Integer>> pend = pendingNow(e);
            out = shortages(e, pend);
            more = pendingAdded(e, pend);
        }
        if (Global.getSettings().getModManager().isModEnabled(CAContracts.MOD)) withContracts(out, CAContracts.shortfalls(), more);
        return out;
    }

    /** A good's shortage in a deficit map, 0 for none. */
    static int lack(Map<String, Integer> deficit, String com) {
        Integer d = deficit == null ? null : deficit.get(com);
        return d != null && d > 0 ? d : 0;
    }

    /**
     * Units of shortage a pick's line covers once its colony's supply of com is `level` (from base, adding `added` over its
     * other sources), capped at lack (the shortage in the caller's map, 0 when the caller counts none). Without per-colony
     * data, or for a shortage only a trade contract has (no colony short of it today), the 0.2.44.0 rule min(added, lack). A
     * colony shortage builds under way already cover gives nothing, even if the caller's map is from before they started.
     */
    static int reliefUnits(String com, int base, int level, int added, int lack) {
        if (added <= 0 || lack <= 0) return 0;
        View v = null;
        try {
            Econ e = econNow();
            if (e != null) v = view(com, e, pendingNow(e));
        } catch (Throwable ignored) { }
        if (v == null || v.raw <= 0) return Math.min(added, lack);
        return reliefAdd(v.demand, v.avail, base, level, added, lack);
    }

    /** The relief one call of CAEngine.supply/supplyChange adds (negative for a cut), from the market model's Gain. */
    static int reliefCover(String com, CAMarket.Gain g, int lack) {
        if (lack <= 0) return 0;
        return reliefUnits(com, g.base, g.after, g.addedAfter, lack) - reliefUnits(com, g.base, g.before, g.addedBefore, lack);
    }

    /**
     * The keep side: units your colonies would be short of without a line of `lost` units at `level` at m (the colony's other
     * sources make other), the claims of ind itself left out. localOnly: an illegal good, which only covers the colony's own.
     */
    static int keepRelief(MarketAPI m, Industry ind, String com, int level, int other, int lost, boolean localOnly) {
        if (lost <= 0) return 0;
        Econ e = econNow();
        if (e == null) return 0;
        Map<String, Map<String, Integer>> pend = pending(e, m.getId(), ind == null ? null : ind.getId());
        View v = view(com, e, pend);
        if (v == null) return 0;
        int without = Math.max(0, other);
        if (localOnly) {
            int i = indexOf(v.ids, m.getId());
            if (i < 0) return 0;
            Map<String, Integer> here = pend.get(com);
            Integer p = here == null ? null : here.get(m.getId());
            if (p != null) without = Math.max(without, p);
            return reliefKeep(new int[]{v.demand[i]}, new int[]{v.avail[i]}, level, without, lost);
        }
        for (int i = 0; i < v.ids.length; i++) if (!v.ids[i].equals(m.getId())) without = Math.max(without, v.level[i]);
        without = Math.max(without, v.pending);
        return reliefKeep(v.demand, v.avail, level, without, lost);
    }

    static int indexOf(String[] ids, String id) {
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(id)) return i;
        return -1;
    }

    // ---------------- weights ----------------

    static CAData.Profile profileOf(MarketAPI m) {
        CAData.Profile own = CAData.Profile.BALANCED;
        try { CAData.Profile p = CAData.get().colony(m.getId()).profile; if (p != null) own = p; } catch (Throwable ignored) { }
        try { return CAThreat.effective(m, own); } catch (Throwable t) { return own; }
    }

    /** The weights a value is counted with: fast buildout's money-only ones, the endgame's, or the goal's (as CAEngine.finish). */
    static float[] weights(CAData.Profile profile, boolean endgame) {
        if (CAEngine.profitOnly) return CAEngine.PROFIT;
        float[] goal = CAEngine.WEIGHTS[(profile == null ? CAData.Profile.BALANCED : profile).ordinal()];
        return endgame ? CAEndgame.weights(goal) : goal;
    }

    static boolean endgame(MarketAPI m) {
        if (CAEngine.profitOnly || m == null) return false;
        try { return CAEndgame.at(m); } catch (Throwable t) { return false; }
    }

    static float moneyWeight(MarketAPI m, boolean endgame) {
        if (CAEngine.profitOnly || endgame || m == null) return 1f;
        try { return CASurplus.moneyWeight(m); } catch (Throwable t) { return 1f; }
    }

    /** The units a pick's production chains count (the endgame): what it adds over the colony's other sources, never a duplicate's. */
    public static Map<String, Integer> chainUnits(CAEngine.Pick p) {
        Map<String, Integer> src = p.added.isEmpty() ? p.supplied : p.added;
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> e : src.entrySet()) if (e.getValue() != null && e.getValue() > 0) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** CAEndgame.chainValue on these units; its lines kept only when explain is set. */
    static float chainValue(MarketAPI m, CAEngine.Pick p, Map<String, Integer> units, boolean removing, boolean explain) {
        Map<String, Integer> was = p.supplied;
        int lines = p.lines.size();
        p.supplied = units;
        try { return CAEndgame.chainValue(m, p, removing); }
        finally {
            p.supplied = was;
            if (!explain) while (p.lines.size() > lines) p.lines.remove(p.lines.size() - 1);
        }
    }

    // ---------------- add ----------------

    /** Monthly worth of adding this pick at m at the colony's goal (threat counted), confidence applied. */
    public static float add(MarketAPI m, CAEngine.Pick p) { return add(m, p, profileOf(m), false); }

    public static float add(MarketAPI m, CAEngine.Pick p, CAData.Profile profile) { return add(m, p, profile, false); }

    /**
     * The worth CAEngine.finish works from: the pick's effects at the weights (fast buildout's, the endgame's or the goal's),
     * money at the surplus's weight, the endgame's production chains on the units it adds over the colony's other sources
     * (chainUnits: a duplicate earns none, research 02 section 9), and an uncertain gain at its confidence. explain: keep the
     * endgame's lines on the pick. No plan bonus, price or wait: those are the planner's (B2).
     */
    public static float add(MarketAPI m, CAEngine.Pick p, CAData.Profile profile, boolean explain) {
        if (p == null) return 0f;
        boolean endgame = endgame(m);
        float[] w = weights(profile, endgame);
        float mw = moneyWeight(m, endgame);
        float v = 0f;
        // 0.3.0.4: survival mode's corruption keeps only part of extra colony income (CASurvival)
        float money = CASurvival.kept(p.fx[0]);
        if (explain && money < p.fx[0]) p.lines.add("Survival mode corruption: extra income counts " + Math.round(100f * money / p.fx[0]) + "%");
        v += w[0] * money * mw;
        for (int i = 1; i < 7; i++) v += w[i] * p.fx[i];
        if (endgame) v += chainValue(m, p, chainUnits(p), false, explain);
        if (v > 0f && p.confidence < 1f) v *= p.confidence;
        return v;
    }

    // ---------------- keep ----------------

    /** What keeping a building is worth a month, and what it's made of (for the removers' log lines). */
    public static final class Kept {
        public Industry ind;
        /** At the colony's weights, as add(): what the colony loses a month without it. */
        public float value;
        /** Credits a month before weights: export + relief + other - upkeep. */
        public float money, export, relief, upkeep;
        /**
         * Money the engine reads besides its output lines: salvage, income estimates. 0.3.0.0 (B1 fix): never the inputs your
         * colonies lack (a build is charged for them, keeping one isn't: Pick.inputPenalty). 0.3.0.0 (B1 fix, second pass): an
         * IndEvo producer's flat estimate netted as add() netted it (estimate), and the money of the synergies it keeps going
         * (synergy[0]), are in here.
         */
        public float other;
        /** 0.3.0.0 (B1 fix): disrupted (a raid, a bombardment): its lines are the formula's, valued as if built again (keepDetail). */
        public boolean disrupted;
        /**
         * 0.3.0.0 (B1 fix): an IndEvo producer's whole flat estimate (Pick.outputEstimate), when it has live lines. 0.3.0.0 (B1
         * fix, second pass): it's valued by that estimate, as add() valued it, netted by the share of the line the estimate
         * stands for (estimateLine) above the colony's other sources of the goods its live lines make (in other); its lines
         * aren't priced again (no export, relief or lost units). Above 0: CAPrune leaves it alone, as 0.2.45.1 did (it read the estimate as income the market can't see).
         */
        public float estimate;
        /**
         * 0.3.0.0 (B1 fix, second pass): the Vaults of Knowledge synergies it keeps going (keepSynergies), as the engine's effects:
         * [0] credits a month (in other and money), [1..6] stability to growth (in value at the same weights). All 0 without
         * Vaults of Knowledge.
         */
        public final float[] synergy = new float[7];
        /** Every line it makes is made in larger amounts by another building here (it only costs upkeep). */
        public boolean allDup;
        /** The buildings here that out-make it, and the goods they out-make it in (for "already makes more ..." lines). */
        public final List<String> bigger = new ArrayList<String>(), goods = new ArrayList<String>();
        /** Units per good the colony's supply would lose without it (none for one valued by its estimate). */
        public final Map<String, Integer> lost = new LinkedHashMap<String, Integer>();
        /** The engine's own reading of it (non-money effects in fx[1..6], what its formulas say it makes in supplied). */
        public CAEngine.Pick p;
        /** At least one output line. */
        public int lines;
    }

    /** Monthly worth of keeping this building at the colony's goal (threat counted); UNKNOWN when it can't be valued. */
    public static float keep(MarketAPI m, Industry ind) { return keep(m, ind, profileOf(m)); }

    public static float keep(MarketAPI m, Industry ind, CAData.Profile profile) {
        Kept k = keepDetail(m, ind, profile, CAEngine.empireDeficits());
        return k == null ? UNKNOWN : k.value;
    }

    /**
     * The same math as add(), backwards (research 02 section 6): each live output line counts only above the colony's other
     * biggest source (builds under way there included), at what it holds of the good's sector market (CAMarket.keepExport,
     * the share rule run backwards) or the flat rate with the model off; the shortage it would leave (reliefKeep, the
     * pending-aware deficits); its real upkeep; and everything
     * else (stability, defense, fleets, accessibility, growth, salvage) from the engine's reading of it, at the same weights,
     * money at the same weight, the endgame's chains on the units it really adds. Null for one it can't value (no formula,
     * estimate or measurement) or one still being built.
     *
     * 0.3.0.0 (B1 fix): inputs your colonies lack are a build's cost only (0.2.45.1's prune left them out too: the game already
     * cuts the building's live output for its own shortage, and taking it down eases no other colony's); an IndEvo producer's
     * flat estimate isn't counted on top of its live lines; and a disrupted building (the game clears its supply until it
     * recovers) is valued on its formula's lines as if built again (lineWorth, the add side), so a raid never makes it the
     * weakest. 0.2.45.1 ranked by formula, so a raid never moved the choice.
     *
     * 0.3.0.0 (B1 fix, second pass): the synergies it keeps going count too (keepSynergies: a Mining that gives the Refining its
     * +2 keeps that +2's worth, as add() counted it when the Mining was built); and an IndEvo producer add() valued by its flat
     * estimate (Salvage Yards, the VPC buildings) is kept at that estimate netted the same way, by the share of the line the
     * estimate stands for (estimateLine) above the colony's other sources (keptShare, addShare's rule), not its live lines at
     * market share (added at 6,200, kept at -400).
     */
    public static Kept keepDetail(MarketAPI m, Industry ind, CAData.Profile profile, Map<String, Integer> deficit) {
        if (m == null || ind == null || (ind.isBuilding() && !ind.isUpgrading())) return null;
        String id = ind.getId();
        CAEngine.Pick p;
        Industry sup = CAEngine.supplier;
        MarketAPI at = CAEngine.supplierAt;
        boolean raw = rawEstimates;
        CAEngine.scoring(null, null);   // the engine's own reading: formulas on the flat path; the lines that matter are read live below
        rawEstimates = true;   // 0.3.0.0 (B1 fix): an IndEvo producer's whole estimate, netted below as add() nets it
        try {
            p = CAEngine.effectsOf(m, id, deficit);
            if (p == null && CAEngine.estimated(id)) {
                p = new CAEngine.Pick();
                p.id = id;
                p.recording = true;
                try { CAEngine.estimate(m, id, p, deficit); } catch (Throwable t) { p = null; }
                if (p != null) p.recording = false;
            }
        } finally { CAEngine.scoring(sup, at); rawEstimates = raw; }
        if (p == null) return null;
        Kept k = new Kept();
        k.ind = ind; k.p = p; k.allDup = true;
        boolean model = CAMarket.on();
        float uv = CAEngine.unitValue;
        k.disrupted = disrupted(ind);
        // 0.3.0.0 (B1 fix, second pass): valued by its estimate, as add() valued it; its live lines only say which goods the
        // estimate stands for, and it's netted by the same line add() netted it by (estimateLine), so it's kept at what it was
        // added at (a fresh Salvage Yards makes 2 parts, not the size-6 line its estimate is)
        boolean byEstimate = p.outputEstimate > 0f;
        int estLine = byEstimate ? estimateLine(m) : 0;
        float share = -1f;
        for (Map.Entry<String, Integer> line : keepLines(m, ind, p, k.disrupted).entrySet()) {
            int own = line.getValue();
            String com = line.getKey();
            k.lines++;
            Industry top = null;
            int otherBuildings = 0;
            for (Industry o : m.getIndustries()) {
                if (o == ind) continue;
                int u = CAMarket.own(o, com);
                if (u > otherBuildings) { otherBuildings = u; top = o; }
            }
            int other = CAMarket.largestOther(m, ind, com);
            int lost = model ? lostUnits(own, other) : own;
            float base = price(com);
            if (lost > 0) { k.allDup = false; if (!byEstimate) k.lost.put(com, lost); }
            else {
                if (top != null && !k.bigger.contains(top.getCurrentName())) k.bigger.add(top.getCurrentName());
                k.goods.add(CAMarket.name(com).toLowerCase(Locale.ROOT));
            }
            if (byEstimate) {   // the best share over the estimate's goods, as vpcShare takes the best chip's (model off: all of it)
                if (estimateGood(id, com)) share = Math.max(share, model ? keptShare(estLine, other) : 1f);
                continue;
            }
            if (lost <= 0) continue;
            if (k.disrupted) {
                // 0.3.0.0 (B1 fix): the market and the shortages are read without it now, so it's what it adds back once it
                // recovers, the add side's math (a new line of its formula's units over the colony's other sources)
                float[] back = lineWorth(m, ind, 0, other, com, own, deficit);
                k.export += back[0]; k.relief += back[1];
                continue;
            }
            boolean legal = legal(m, ind, com);
            if (legal) {
                Float ex = model ? CAMarket.keepExport(m, com, own, other) : null;
                k.export += ex != null ? ex : base * uv * lost;
            }
            int level = Math.max(own, Math.max(0, other));
            int r = keepRelief(m, ind, com, level, other, lost, !legal);
            // as the build side counts it: the model at the flat rate per unit; the flat rate's shortage premium; an illegal good x1.5 at home
            k.relief += base * uv * r * (!legal ? CATuning.SHORTAGE_FACTOR : model ? 1f : CATuning.SHORTAGE_FACTOR - 1f);
        }
        k.allDup = k.allDup && k.lines > 0;   // a building that makes nothing isn't a duplicate of anything
        try { k.upkeep = ind.getUpkeep().getModifiedValue(); } catch (Throwable t) { k.upkeep = 0f; }
        // 0.3.0.0 (B1 fix, second pass): its estimate at the share add() gave it (all of it with none of the estimate's goods: a
        // Variable Assembler with no chip, as before), and the synergies it keeps going
        float est = byEstimate ? p.outputEstimate * (share < 0f ? 1f : share) : 0f;
        k.estimate = byEstimate && k.lines > 0 ? p.outputEstimate : 0f;
        float[] syn = keepSynergies(synergies(m, deficit), m, ind, deficit);
        System.arraycopy(syn, 0, k.synergy, 0, 7);
        k.other = otherMoney(p) + est + syn[0];   // the engine's money besides its supply lines (salvage, income)
        k.money = k.export + k.relief + k.other - k.upkeep;
        boolean endgame = endgame(m);
        float[] w = weights(profile, endgame);
        float v = w[0] * moneyWeight(m, endgame) * CASurvival.kept(k.money);   // 0.3.0.4: corruption, as add()
        for (int i = 1; i < 7; i++) v += w[i] * (p.fx[i] + syn[i]);
        // an estimate's chains: none, as add() counts none for it (no lines recorded); else the units it really adds
        if (endgame) v += chainValue(m, p, new LinkedHashMap<String, Integer>(k.lost), true, false);
        k.value = v;
        return k;
    }

    /**
     * 0.3.0.0 (B1 fix): the money keep() takes from the engine's reading besides the output lines (valued live): its fx[0] less
     * its supply lines' value, with the inputs-your-colonies-lack charge put back (a build's cost, not a keep's), less an
     * IndEvo producer's whole flat estimate (keepDetail adds it back netted as add() netted it).
     */
    static float otherMoney(CAEngine.Pick p) {
        return p.fx[0] - p.suppliedValue + p.inputPenalty - p.outputEstimate;
    }

    /** 0.3.0.0 (B1 fix, second pass): the good the Salvage Yards' estimate stands for (its ship parts), which both sides net it by. */
    static final String YARD_GOOD = "IndEvo_parts";

    /**
     * 0.3.0.0 (B1 fix, second pass): the line an IndEvo estimate stands for (1,200 x size is size parts at the flat rate; a VPC
     * building's per-size income the same): the colony's size with the administrator's bonus, the line addShare nets on the
     * add side (netShare, vpcShare). keepDetail nets by it too, not the live line (a Salvage Yards makes 3 + its salvage
     * bonus - 1 parts), so the share kept is the share added whatever the yard has salvaged.
     */
    static int estimateLine(MarketAPI m) { return Math.max(0, m.getSize()) + CAMods.adminBonus(m); }

    /** 0.3.0.0 (B1 fix, second pass): whether a live line of com nets an estimate building's estimate: the yard's parts, any VPC building's chip goods. */
    static boolean estimateGood(String id, String com) { return !"IndEvo_ScrapYard".equals(id) || YARD_GOOD.equals(com); }

    /** 0.3.0.0 (B1 fix): a raid or bombardment has it disrupted (Industry.isFunctional is false then; also while built, ruled out earlier). */
    static boolean disrupted(Industry ind) {
        try { return ind.isDisrupted() || !ind.isFunctional(); } catch (Throwable t) { return false; }
    }

    /**
     * 0.3.0.0 (B1 fix): the lines keep() counts, good -> units. A working building's live supply; a disrupted one's (the game
     * clears its supply until it recovers) its formula's, with the administrator's bonus as a new line gets it, so a raid
     * doesn't turn a Refining into a building that makes nothing.
     */
    static Map<String, Integer> keepLines(MarketAPI m, Industry ind, CAEngine.Pick p, boolean disrupted) {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        if (!disrupted) {
            for (MutableCommodityQuantity q : ind.getAllSupply()) {
                int own = Math.max(0, q.getQuantity().getModifiedInt());
                if (own > 0) out.put(q.getCommodityId(), own);
            }
            return out;
        }
        int admin = CAMods.adminBonus(m);
        for (Map.Entry<String, Integer> e : p.supplied.entrySet())
            if (e.getValue() != null && e.getValue() > 0) out.put(e.getKey(), e.getValue() + admin);
        return out;
    }

    // ---------------- boosts: more or fewer units on a building that's there ----------------

    static float price(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getBasePrice(); } catch (Throwable t) { return 0f; }
    }

    static boolean legal(MarketAPI m, Industry ind, String com) {
        if (ind == null) return true;
        try { CommodityOnMarketAPI c = m.getCommodityData(com); return c == null || ind.isSupplyLegal(c); } catch (Throwable t) { return true; }
    }

    /**
     * What n more units (n below 0: fewer) on ind's line of com are worth a month at m (research 02 section 9): only what
     * they add to the colony's supply, its largest single source (so +1 on a building another out-makes is worth nothing),
     * priced by export share, plus the shortage they cover (or leave), as add() and keep() count them. Credits, before any
     * goal weights. ind null: a new line of n units. With the market model off, the flat rate as before (n units, the
     * shortage premium on the units that cover it). For AI cores, colony items, story point improvements and synergies.
     */
    public static float boost(MarketAPI m, Industry ind, String com, int n, Map<String, Integer> deficit) {
        if (m == null || com == null || n == 0) return 0f;
        return boostLine(m, ind, ind == null || !CAMarket.on() ? 0 : CAMarket.own(ind, com), com, n, deficit);
    }

    /**
     * boost() on a line of own units: ind's own (boost), or a building not there yet (ind null), such as the new building a
     * colony item waiting in storage would go in (CAItems.itemWorth: its +n on the pick's own units).
     */
    public static float boostLine(MarketAPI m, Industry ind, int own, String com, int n, Map<String, Integer> deficit) {
        if (m == null || com == null || n == 0) return 0f;
        float[] v = lineWorth(m, ind, own, CAMarket.on() ? CAMarket.largestOther(m, ind, com) : 0, com, n, deficit);
        return v[0] + v[1];
    }

    /**
     * 0.3.0.0 (B1 fix): boostLine's math with the colony's other biggest source given (other), split into {export, relief}
     * credits a month: n more (or fewer) units on a line of own units of ind's (or a building not there yet). Also how keep()
     * values a disrupted building's lines (what they add back) and how CAValue.synergy values a boost next to the pick.
     */
    static float[] lineWorth(MarketAPI m, Industry ind, int own, int other, String com, int n, Map<String, Integer> deficit) {
        float[] out = new float[2];
        if (m == null || com == null || n == 0) return out;
        float base = price(com), uv = CAEngine.unitValue;
        if (base <= 0f) return out;
        int lack = lack(deficit, com);
        if (!CAMarket.on()) {
            int cover = n > 0 ? Math.min(n, lack) : -Math.min(-n, lack);
            out[0] = base * uv * n;
            out[1] = base * uv * (CATuning.SHORTAGE_FACTOR - 1f) * cover;
            return out;
        }
        int before = Math.max(Math.max(0, own), Math.max(0, other));
        int delta = boostUnits(own, n, other), after = before + delta;
        if (delta == 0) return out;
        boolean legal = legal(m, ind, com);
        if (legal) {
            Float ex = null;
            try { ex = CAMarket.lineValue(m, com, before, after); } catch (Throwable ignored) { }
            out[0] = ex != null ? ex : base * uv * delta;
        }
        int r;
        if (delta > 0) {
            if (legal) r = reliefUnits(com, before, after, delta, lack);
            else {
                int local = 0;
                try { local = Math.max(0, m.getCommodityData(com).getDeficitQuantity()); } catch (Throwable ignored) { }
                r = Math.min(delta, local);
            }
        } else r = -keepRelief(m, ind, com, before, after, -delta, !legal);
        out[1] = base * uv * r * (legal ? 1f : CATuning.SHORTAGE_FACTOR);
        return out;
    }

    /** boost() on the colony's biggest source of com (a new line of n units if it makes none): synergies that add to "the" Refining's output. */
    public static float boostLargest(MarketAPI m, String com, int n, Map<String, Integer> deficit) {
        if (m == null || com == null || n == 0) return 0f;
        if (!CAMarket.on()) return boost(m, null, com, n, deficit);   // the flat rate needs no building
        Industry top = null;
        int best = 0;
        for (Industry ind : m.getIndustries()) {
            int u = CAMarket.own(ind, com);
            if (u > best) { best = u; top = ind; }
        }
        return boost(m, top, com, n, deficit);
    }

    /**
     * 0.3.0.0 (B1 fix, research 02 section 9): a synergy's n more (or fewer) units of com at m, worked out while `pick` is scored
     * (null: the colony as it is, boostLargest). The output lands on the colony's biggest source of com once the pick is
     * built. When that's the pick itself (Refining built next to a Mining starts aotd_mining_feed, Heavy Industry next to
     * Refining, Fuel Production with aotd_volatile_line or ildrenium_aep), the units go on the pick's own line, as
     * CAEngine.supplyChange would: export priced from the pick's level (its shipping cap counted) and relief only past what the
     * pick's own line already covers, so the two never relieve more than the shortage. Before, they were priced as a new line
     * from 0. Otherwise the biggest building already there that stays (not the one the pick replaces), netted against the
     * rest and the pick's line. Model off: the flat rate, the shortage premium only past the units the pick already counts.
     * The pick is never changed (CASynergies adds the value to its own sum). 0.3.0.0 (B1 fix, second pass): with no pick while
     * keepDetail values the synergies a building keeps going (keeping), what the colony would lose without these units.
     */
    public static float synergy(MarketAPI m, CAEngine.Pick pick, String com, int n, Map<String, Integer> deficit) {
        if (m == null || com == null || n == 0) return 0f;
        if (pick == null && keeping != null) return keptSynergy(m, keeping, com, n, deficit);
        if (pick == null) return boostLargest(m, com, n, deficit);
        if (!CAMarket.on()) {
            if (!pick.added.containsKey(com)) return boostLargest(m, com, n, deficit);
            float base = price(com);
            int prior = CAEngine.units(pick.added, com), now = Math.max(0, prior + n);
            int cover = flatCover(prior, now - prior, lack(deficit, com));
            return base * CAEngine.unitValue * ((now - prior) + (CATuning.SHORTAGE_FACTOR - 1f) * cover);
        }
        if (!pick.marketUnits.containsKey(com)) return boostLargest(m, com, n, deficit);   // the pick makes none: an existing building's line
        Industry gone = pick.upgrade ? pick.existing : null;
        Industry top = null;
        int best = 0, second = 0;
        for (Industry ind : m.getIndustries()) {
            if (ind == gone) continue;
            int u = CAMarket.own(ind, com);
            if (u > best) { second = best; best = u; top = ind; }
            else if (u > second) second = u;
        }
        int mine = CAMarket.pickLevel(m, pick, com);
        if (top == null || mine >= best) {
            CAMarket.Gain g = null;
            try { g = CAMarket.pickGainMore(m, pick, com, n); } catch (Throwable ignored) { }
            if (g == null) return boostLargest(m, com, n, deficit);
            return g.value + price(com) * CAEngine.unitValue * reliefCover(com, g, lack(deficit, com));
        }
        int other = Math.max(Math.max(second, mine), CAMarket.pendingAt(m, com, gone == null ? null : gone.getId()));
        float[] v = lineWorth(m, top, best, other, com, n, deficit);
        return v[0] + v[1];
    }

    // ---------------- synergies a building keeps going (0.3.0.0, B1 fix, second pass) ----------------

    /** Set while keepSynergies values a building's synergies: synergy() then prices their units as what the colony would lose. Session only. */
    static Industry keeping;

    /** The colony's synergy reading for keep() (CASynergies.context), kept for the day while its buildings don't change. Session only. */
    static final CACache.Memo<CASynergies.Ctx> SYNERGIES = new CACache.Memo<CASynergies.Ctx>("kept synergies", 60);

    /** What that reading depends on: CAEngine.econSig (buildings and their state, size, administrator, free port, conditions), and stockpile use. */
    static long synergySig(MarketAPI m) {
        long h;
        try { h = CAEngine.econSig(m); }
        catch (Throwable t) {   // a colony the full signature can't read: its buildings and size
            List<String> ids = new ArrayList<String>();
            for (Industry ind : m.getIndustries()) ids.add(ind.getId());
            h = CACache.mix(CACache.mix(CACache.spread(m.getId()), m.getSize()), CACache.unordered(ids));
        }
        boolean stock = false;
        try { stock = m.isUseStockpilesForShortages(); } catch (Throwable ignored) { }
        return CACache.mix(h, stock ? 1 : 0);
    }

    /** The colony's synergies for keep(); null without Vaults of Knowledge or when they can't be read (then keep() counts none, as before). */
    static CASynergies.Ctx synergies(MarketAPI m, Map<String, Integer> deficit) {
        if (m == null) return null;
        try { if (!Global.getSettings().getModManager().isModEnabled(CASynergies.MOD)) return null; } catch (Throwable t) { return null; }
        int day = CAEngine.today();
        long sig = day >= 0 ? synergySig(m) : 0L;
        CASynergies.Ctx c = day >= 0 ? SYNERGIES.get(m.getId(), day, sig) : null;
        if (c != null) return c;
        int ep = CACache.epoch();
        c = CASynergies.context(m, deficit);
        if (c != null && day >= 0) SYNERGIES.put(m.getId(), day, ep, sig, c);
        return c;
    }

    /**
     * What a building's Vaults of Knowledge synergies are worth keeping, as the engine's effects (fx[0] credits a month,
     * fx[1..6] stability to growth): every synergy researched here, valued with the building minus without it (its id out of
     * the colony's buildings, its part of the synergy efficiency out), CASynergies.score run backwards. So a Mining built
     * partly for the Refining's +2 (aotd_mining_feed) keeps that +2's worth, and a Spaceport the efficiency it lends every
     * synergy (research 02 section 6: the old prune ignored synergies, so it took down what it had built them for). Output a
     * synergy puts on another building is what that building's line would lose (keptSynergy); output on its own line is in
     * its live lines already. Inputs your colonies lack aren't counted (a build's cost, as keepDetail). Zeros without a reading.
     */
    static float[] keepSynergies(CASynergies.Ctx c, MarketAPI m, Industry ind, Map<String, Integer> deficit) {
        float[] out = new float[7];
        if (c == null || m == null || ind == null || c.open.isEmpty()) return out;
        Set<String> without = new HashSet<String>(c.have);
        if (!without.remove(ind.getId())) return out;   // a reading from before it was there
        float effWithout = effWithout(c, ind);
        Industry was = keeping;
        keeping = ind;
        try {
            for (String id : c.open.keySet()) {
                boolean on = CASynergies.met(id, c.have), still = CASynergies.met(id, without);
                if (!on && !still) continue;
                float[] with = on ? synergyFx(id, m, c.have, c.eff, deficit) : new float[7];
                float[] rest = still ? synergyFx(id, m, without, effWithout, deficit) : new float[7];
                for (int i = 0; i < 7; i++) out[i] += with[i] - rest[i];
            }
        } finally { keeping = was; }
        return out;
    }

    /** One synergy's effects with these buildings at this efficiency, the input-shortage charge left out; zeros if it can't be read. */
    static float[] synergyFx(String id, MarketAPI m, Set<String> have, float eff, Map<String, Integer> deficit) {
        float[] fx = new float[7];
        CAEngine.Pick p = new CAEngine.Pick();
        try { CASynergies.effects(id, m, have, eff, p, deficit); } catch (Throwable t) { return fx; }
        for (int i = 0; i < 7; i++) fx[i] = p.fx[i];
        fx[0] += p.inputPenalty;
        return fx;
    }

    /** The colony's synergy efficiency without this building's part (CASynergies.effAfter's first half); as it is when that can't be read. */
    static float effWithout(CASynergies.Ctx c, Industry ind) {
        float e = c.eff;
        try {
            Object s = CASynergies.source(ind.getId());
            if (s != null && CASynergies.srcClass.isInstance(s)) e -= ((Number) CARefl.invoke(CASynergies.srcEff, s, ind, true)).floatValue();
        } catch (Throwable ignored) { }
        return Math.max(0f, e);
    }

    /**
     * n units a synergy puts on the colony's biggest source of com, kept: what that building's line would lose without them
     * (boost() the other way, netted and priced the same), so keeping it is worth what adding it was. 0 when that building is
     * the one kept (its live line holds them already); with no building making com, a new line's worth, as the add side.
     */
    static float keptSynergy(MarketAPI m, Industry kept, String com, int n, Map<String, Integer> deficit) {
        Industry top = null;
        int best = 0;
        for (Industry o : m.getIndustries()) {
            int u = CAMarket.own(o, com);
            if (u > best) { best = u; top = o; }
        }
        if (top == null) return boost(m, null, com, n, deficit);
        if (top == kept) return 0f;
        return -boost(m, top, com, -n, deficit);
    }

    // ---------------- colony items (0.3.0.0, B1 fix) ----------------

    /**
     * Units a colony item adds to each of a building's live lines (research 02 section 9): an item the game boosts every line
     * with (BoostIndustryInstallableItemEffect: Catalytic Core, Synchrotron, Soil Nanites, Biofactory Embryo, the nanoforges,
     * the AotD and UAF boosters) its supply increase on every line it makes (the game adds it to each line above 0); the
     * Mantle Bore and Plasma Dynamo their +3 on the goods they work. Empty for an item that adds no output, or a building that
     * makes nothing now.
     */
    static Map<String, Integer> itemUnits(Industry ind, String itemId, InstallableItemEffect effect) {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        if (ind == null || effect == null) return out;
        int up = 0;
        Collection<String> only = null;
        if (effect instanceof BoostIndustryInstallableItemEffect) up = ((BoostIndustryInstallableItemEffect) effect).getSupplyIncrease();
        else if ("mantle_bore".equals(itemId)) { up = ItemEffectsRepo.MANTLE_BORE_MINING_BONUS; only = ItemEffectsRepo.MANTLE_BORE_COMMODITIES; }
        else if ("plasma_dynamo".equals(itemId)) { up = ItemEffectsRepo.PLASMA_DYNAMO_MINING_BONUS; only = ItemEffectsRepo.PLASMA_DYNAMO_COMMODITIES; }
        if (up <= 0) return out;
        for (MutableCommodityQuantity q : ind.getAllSupply()) {
            if (q.getQuantity().getModifiedInt() <= 0 || (only != null && !only.contains(q.getCommodityId()))) continue;
            out.put(q.getCommodityId(), up);
        }
        return out;
    }

    /**
     * A colony item's placement score in building ind at m (no item in it yet), in credits a month (CAItems.itemWorth's
     * scale), so items that add output go where they really add it (research 02 section 9): its units on each of the
     * building's lines netted against the colony's other sources and priced by market share (lineWorth, as boost()), at the
     * colony's money weight; a nanoforge's ship quality on top at its flat score; an item that adds no output its flat score.
     * flat: CAItems.itemScore in this building (points; 0 or less: it never goes there, as before). Before, every item was
     * ranked by the flat score alone, so a +3 could go where another building already out-makes the one it goes in.
     */
    public static float itemPlacement(MarketAPI m, Industry ind, String itemId, InstallableItemEffect effect, float flat,
                                      CAData.Profile profile, Map<String, Integer> deficit) {
        if (flat <= 0f || m == null || ind == null) return 0f;
        Map<String, Integer> units = itemUnits(ind, itemId, effect);
        if (units.isEmpty()) return flatItemCredits(flat);
        return itemMoney(m, lineChange(m, ind, units, Collections.<String, Integer>emptyMap(), deficit), profile) + quality(itemId, flat);
    }

    /** What the item installed in ind adds now (what taking it out would lose), on itemPlacement's scale: a swap's "now". */
    public static float itemInstalled(MarketAPI m, Industry ind, String itemId, InstallableItemEffect effect, float flat,
                                      CAData.Profile profile, Map<String, Integer> deficit) {
        if (flat <= 0f || m == null || ind == null) return 0f;
        Map<String, Integer> units = itemUnits(ind, itemId, effect);
        if (units.isEmpty()) return flatItemCredits(flat);
        return itemMoney(m, -lineChange(m, ind, Collections.<String, Integer>emptyMap(), units, deficit), profile) + quality(itemId, flat);
    }

    /**
     * A waiting item's score in place of the one installed in ind (old), on itemPlacement's scale: a swap's "then". For two
     * items that add output, what the old one adds now plus the change of each line from the old item's units to the new
     * one's (a Pristine Nanoforge for a Corrupted one: +2 a line), so then - now is what the swap itself adds.
     */
    public static float itemSwap(MarketAPI m, Industry ind, String itemId, InstallableItemEffect effect, float flat, String old,
                                 InstallableItemEffect oldEffect, CAData.Profile profile, Map<String, Integer> deficit) {
        if (flat <= 0f || m == null || ind == null) return 0f;
        Map<String, Integer> units = itemUnits(ind, itemId, effect);
        if (units.isEmpty()) return flatItemCredits(flat);
        Map<String, Integer> was = itemUnits(ind, old, oldEffect);
        float money = -lineChange(m, ind, Collections.<String, Integer>emptyMap(), was, deficit) + lineChange(m, ind, units, was, deficit);
        return itemMoney(m, money, profile) + quality(itemId, flat);
    }

    /** Credits a month the building's lines change by when each goes up by plus and down by minus units (lineWorth on its live line). */
    static float lineChange(MarketAPI m, Industry ind, Map<String, Integer> plus, Map<String, Integer> minus, Map<String, Integer> deficit) {
        Set<String> goods = new LinkedHashSet<String>(plus.keySet());
        goods.addAll(minus.keySet());
        float v = 0f;
        for (String com : goods) {
            int d = CAEngine.units(plus, com) - CAEngine.units(minus, com);
            if (d == 0) continue;
            int other = CAMarket.on() ? CAMarket.largestOther(m, ind, com) : 0;
            float[] w = lineWorth(m, ind, CAMarket.own(ind, com), other, com, d, deficit);
            v += w[0] + w[1];
        }
        return v;
    }

    /** Money at the colony's money weight (its goal's, the endgame's, the surplus's), as add() counts fx[0]. */
    static float itemMoney(MarketAPI m, float credits, CAData.Profile profile) {
        boolean endgame = endgame(m);
        return CASurvival.kept(credits) * weights(profile, endgame)[0] * moneyWeight(m, endgame);   // 0.3.0.4: corruption, as add()
    }

    /** A nanoforge's ship and weapon quality, which isn't output: its flat score, as before. 0 for any other item. */
    static float quality(String itemId, float flat) { return itemId != null && itemId.endsWith("nanoforge") ? flatItemCredits(flat) : 0f; }

    /** CAItems.itemScore points as credits a month (CAItems.POINT_CREDITS at the unit value, as itemWorth). */
    static float flatItemCredits(float points) { return points * CAItems.POINT_CREDITS * CAEngine.unitValue / 100f; }

    /**
     * 0.3.0.0 (B1 fix, second pass): set while keepDetail reads a building: an IndEvo producer's estimate comes out whole (netShare
     * and vpcShare give 1), and keepDetail nets it by the goods the building's live lines make instead of the chips waiting in
     * storage (the same line, estimateLine).
     */
    static boolean rawEstimates = false;

    /**
     * Of a new line of units of com at m, the share above the colony's other sources (1 with the model off or no units).
     * 0.3.0.0 (B1 fix, second pass): the share of the whole line, the administrator's bonus in (addShare), which is what
     * keepDetail's keptShare reads off the same line (estimateLine) once it's built.
     */
    public static float netShare(MarketAPI m, String com, int units) {
        if (m == null || com == null || units <= 0 || !CAMarket.on() || rawEstimates) return 1f;
        return addShare(units, CAMods.adminBonus(m), CAMarket.largestOther(m, null, com));
    }

    /**
     * Industrial Evolution's VPC buildings (Adaptive Assembly, Adaptive Manufactory, Commodity Forge) make the good of the chip
     * they hold, which Autopilot takes from Autopilot Storage. Their flat estimate (CAEstimates) times this: the best share,
     * over the chips waiting there, of `units` of the chip's good above what the colony already makes (research 02 section 9).
     * 1 when no chip's good can be read. keepDetail nets a built one the same way, for the goods its live lines make (keptShare
     * of estimateLine).
     */
    public static float vpcShare(MarketAPI m, int units) {
        if (m == null || units <= 0 || !CAMarket.on() || rawEstimates) return 1f;
        List<String> chips = new ArrayList<String>();
        try {
            for (CAStores.Held h : CAStores.stacks()) {
                if (!h.stack.isSpecialStack() || h.stack.getSpecialDataIfSpecial() == null) continue;
                String sid = h.stack.getSpecialDataIfSpecial().getId();
                if (sid != null) chips.add(sid);
            }
        } catch (Throwable ignored) { }
        return vpcShare(m, units, chips);
    }

    /** vpcShare over these special items' ids (the IndEvo_vpc_ ones count); split out for JTValueCheck. */
    static float vpcShare(MarketAPI m, int units, Collection<String> chips) {
        if (m == null || units <= 0 || !CAMarket.on() || rawEstimates) return 1f;
        float best = -1f;
        try {
            List<String> ids = CAMarket.goods();
            for (String sid : chips) {
                if (sid == null || !sid.startsWith("IndEvo_vpc_")) continue;
                for (String com : vpcGoods(sid.substring("IndEvo_vpc_".length()), ids)) best = Math.max(best, netShare(m, com, units));
            }
        } catch (Throwable ignored) { }
        return best < 0f ? 1f : best;
    }
}
