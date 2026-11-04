package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Surplus investment: credits over a line (3,000,000 recommended), less what's already promised (custom production still
 * to build, and Ashes of the Domain's production orders), go into one focus world, to make it the best colony it
 * can be. While there's a surplus the focus world may spend it past the monthly budget and the per-project cap, its new
 * upkeep cap is 10% of net income, its builds and terraforming count their price at a quarter, and research counts its
 * plan twice. Colony items waiting in Autopilot Storage count toward what a building is worth anywhere (CAItems.idleBonus).
 * The focus world is yours to pick on its colony panel, or Autopilot's: the Auto colony with the most to build on (size,
 * free industry slots, deposits, habitability, hazard, waiting items that fit, income, a reachable special world).
 */
public class CASurplus {
    /** An Autopilot-picked focus world changes only for one that scores this much higher, so it doesn't flip back and forth. */
    static final float MARGIN = 10f;
    /** At the focus world, while there's a surplus, a price counts a quarter. */
    static final float CHEAP = 0.25f;
    /** The focus world's new upkeep cap while there's a surplus: this share of net income, if more than the setting. */
    static final float UPKEEP_SHARE = 0.1f;

    static String focusId;      // today's focus world
    static float computedAt = -99f;
    static final Map<String, Float> scores = new LinkedHashMap<String, Float>();
    static final Map<String, String> whys = new HashMap<String, String>();

    public static void reset() {
        focusId = null; computedAt = -99f; scores.clear(); whys.clear(); CAItems.idle = null; CAItems.idleAt = -99f;
        committedAt = Long.MIN_VALUE;
        CASurplusResearch.summary = null;
        CAProjects.reset();
    }

    static float now() { return CADay.now(); }

    public static float line(CASettings s) { return CASettings.SURPLUS_LINES[Math.max(0, Math.min(CASettings.SURPLUS_LINES.length - 1, s.surplusLine))]; }

    public static String lineName(CASettings s) { return CASettings.SURPLUS_NAMES[Math.max(0, Math.min(CASettings.SURPLUS_NAMES.length - 1, s.surplusLine))]; }

    /** Credits over the line, and never below the credit reserve; 0 when surplus investment is off. */
    static float amount(float line, float reserve, float credits) {
        if (line <= 0f) return 0f;
        return Math.max(0f, credits - Math.max(line, reserve));
    }

    public static float amount(CASettings s) { return amount(line(s), s.reserve, CABuild.credits() - committed()); }

    static long committedAt = Long.MIN_VALUE;
    static float committedCache;

    /**
     * Credits already promised: the game's custom production still to build, and with Ashes of the Domain its production
     * orders' remaining units (each is paid when it's finished; Jonah's Auto Outfitting's weapon orders go there).
     */
    public static float committed() {
        long ts;
        try { ts = Global.getSector().getClock().getTimestamp(); } catch (Throwable t) { ts = Long.MIN_VALUE + 1; }
        if (ts == committedAt) return committedCache;   // scoring asks for every pick; once per moment of game time is enough
        committedAt = ts;
        committedCache = readCommitted();
        return committedCache;
    }

    static float readCommitted() {
        float c = 0f;
        try { c += Math.max(0, Global.getSector().getPlayerFaction().getProduction().getTotalCurrentCost()); } catch (Throwable ignored) { }
        try { if (Global.getSettings().getModManager().isModEnabled("aotd_vok")) c += aotdOrders(); }
        catch (Throwable t) {
            if (!aotdWarned) { aotdWarned = true; CADiagnostics.LOG.warn(CADiagnostics.TAG + "couldn't read Ashes of the Domain's production orders; surplus investment counts the game's own only", t); }
        }
        return c;
    }

    static boolean aotdWarned = false;
    static final String AOTD_PRODUCTION = "data.kaysaar.aotd.vok.campaign.econ.produciton.manager.AoTDProductionManager";

    /** What Ashes of the Domain's production orders still owe: each order line's remaining units at its price. By reflection. */
    static float aotdOrders() throws Exception {
        Class<?> mgr = Class.forName(AOTD_PRODUCTION, true, Global.getSettings().getScriptClassLoader());
        Object inst = CARefl.invoke(CARefl.method(mgr, "getInstance"), null);
        Object snaps = inst == null ? null : CARefl.call(inst, "getCurrentSnapshots");
        float c = 0f;
        if (!(snaps instanceof List)) return c;
        for (Object snap : (List<?>) snaps) {
            Object lines = CARefl.get(CARefl.field(snap.getClass(), "productionData"), snap);
            if (!(lines instanceof List)) continue;
            for (Object d : (List<?>) lines) {
                int left = ((Number) CARefl.call(d, "getRemainingUnits")).intValue();
                Object spec = left > 0 ? CARefl.call(d, "getSpec") : null;
                if (spec != null) c += left * ((Number) CARefl.call(spec, "getProductionCost")).floatValue();
            }
        }
        return c;
    }

    public static boolean on(CASettings s) { return amount(s) > 0f; }

    /**
     * How much there is to build on at a colony, for picking the focus world: bigger, roomier, richer, more habitable and
     * less hazardous worlds, ones where waiting items fit, that earn more, and that can reach a special world.
     */
    static float score(int size, int freeSlots, int depositPoints, boolean habitable, float hazard, int itemsFit, float netIncome, boolean world) {
        return size * 10f + freeSlots * 6f + depositPoints * 3f + (habitable ? 10f : 0f) + (1.5f - hazard) * 20f + itemsFit * 6f
                + Math.max(-10f, Math.min(30f, netIncome / 20000f)) + (world ? 10f : 0f);
    }

    /** Keeps the last pick unless another beats it by MARGIN; ties go to the first. */
    static String pick(Map<String, Float> scores, String last) {
        String best = null;
        for (Map.Entry<String, Float> e : scores.entrySet()) if (best == null || e.getValue() > scores.get(best)) best = e.getKey();
        if (best == null) return null;
        if (last != null && scores.containsKey(last) && scores.get(best) < scores.get(last) + MARGIN) return last;
        return best;
    }

    /** Auto colonies that can be the focus world: not a Nexerelin outpost or a Coronal Hypershunt, where nothing is built. */
    static List<MarketAPI> candidates() {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : CAItems.autoMarkets()) if (!CAHypershunt.is(m) && !m.hasTag("market_no_industries_allowed")) out.add(m);
        return out;
    }

    static int depositPoints(MarketAPI m) {
        int n = 0;
        for (MarketConditionAPI c : m.getConditions()) {
            Integer mod = ResourceDepositsCondition.MODIFIER.get(c.getId());
            if (mod != null) n += Math.max(0, mod + 2);
        }
        return n;
    }

    /** Works out today's focus world: yours if you picked one that's still an Auto colony, else Autopilot's. */
    static void compute(CASettings s) {
        computedAt = now();
        scores.clear(); whys.clear();
        List<MarketAPI> cands = candidates();
        List<CAItems.Idle> idle = CAItems.idleItems();
        for (MarketAPI m : cands) {
            try {
                CAData.Colony c = CAData.get().colony(m.getId());
                int free = Math.max(0, Misc.getMaxIndustries(m) - Misc.getNumIndustries(m));
                int dep = depositPoints(m), fit = 0;
                for (CAItems.Idle it : idle) if (CAItems.fitsAt(it, m) != null) fit++;
                boolean hab = m.hasCondition("habitable");
                boolean world = false;
                try { world = CAPlan.build(m, c.profile).world != null; } catch (Throwable ignored) { }
                float sc = score(m.getSize(), free, dep, hab, m.getHazardValue(), fit, m.getNetIncome(), world);
                scores.put(m.getId(), sc);
                List<String> w = new ArrayList<String>();
                w.add("size " + m.getSize());
                if (free > 0) w.add(free + " free industry slot" + (free == 1 ? "" : "s"));
                if (dep > 0) w.add("deposits");
                if (hab) w.add("habitable");
                w.add(Math.round(m.getHazardValue() * 100f) + "% hazard");
                if (fit > 0) w.add(fit + " waiting item" + (fit == 1 ? "" : "s") + " fit");
                if (world) w.add("can become a special world");
                whys.put(m.getId(), CAContracts.join(w));
            } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "focus world score failed at " + m.getName(), t); }
        }
        if (s.focusId != null && scores.containsKey(s.focusId)) { focusId = s.focusId; return; }
        focusId = pick(scores, s.focusAuto);
        if (focusId != null) s.focusAuto = focusId;
    }

    /** The focus world, or null (no Auto colony). Worked out once a day. */
    public static MarketAPI focus() {
        CASettings s = CAData.settings();
        // not while research scores a what-if state (techs marked researched): yesterday's pick stands until it's put back
        if (now() - computedAt >= 1f && !(CAEngine.noProbe && computedAt > -99f)) compute(s);
        return focusId == null ? null : CAAuto.market(focusId);
    }

    /** True for the focus world while there's a surplus. Cheap: the focus world is worked out once a day. */
    public static boolean isFocus(MarketAPI m) {
        if (m == null) return false;
        CASettings s = CAData.settings();
        if (!on(s)) return false;
        MarketAPI f = focus();
        return f != null && f.getId().equals(m.getId());
    }

    // ---------------- spending freely ----------------
    // Past RICH over the line, one focus world can't spend it all (built out, one new building at a time), so every Approve
    // and Auto colony spends from the surplus like the focus world, and a credit counts RICH_MONEY in what a build is worth:
    // upkeep matters little against that much, so buildings that add defense, stability, growth or access go up even when
    // they cost more than they earn. It pauses while net income is under half the best it's been while rich, so new upkeep
    // can never eat the income that makes the surplus.

    /** Credits over the line past which every colony spends from the surplus. */
    public static final float RICH = 10000000f;
    /** While rich, a credit counts this much in what a build is worth (income and upkeep alike). */
    public static final float RICH_MONEY = 0.25f;
    /** While rich, the month's new upkeep may reach this share of net income. */
    static final float RICH_UPKEEP_SHARE = 0.2f;
    /** Rich spending pauses while net income is under this share of the best it's been while rich. */
    static final float RICH_FLOOR = 0.5f;

    /** Spending freely: surplus spreading on, this far over the line, and net income still at least half its best since. */
    public static boolean rich(CASettings s) {
        return richAt(s.surplusFocusOnly, amount(s), CABudget.netIncome(s), s.richIncomeRef);
    }

    static boolean richAt(boolean focusOnly, float amount, float net, float ref) {
        return !focusOnly && amount >= RICH && net > 0f && net >= RICH_FLOOR * ref;
    }

    /** Spends from the surplus: the focus world, or while rich every Approve or Auto colony that builds. */
    public static boolean spends(MarketAPI m) {
        if (m == null) return false;
        CASettings s = CAData.settings();
        if (!on(s)) return false;
        if (rich(s)) {
            CAData.Mode mode = CAData.get().colony(m.getId()).mode;
            return (mode == CAData.Mode.AUTO || mode == CAData.Mode.APPROVE) && !CAEngine.isOutpost(m) && !CAHypershunt.is(m);
        }
        MarketAPI f = focus();
        return f != null && f.getId().equals(m.getId());
    }

    /** What a credit counts in a build's worth at this colony: RICH_MONEY while rich, else 1. */
    public static float moneyWeight(MarketAPI m) { return m != null && spends(m) && rich(CAData.settings()) ? RICH_MONEY : 1f; }

    /** Spending the colony may do beyond the monthly budget: the surplus, at the focus world (every colony while rich). 0 elsewhere. */
    public static float extra(MarketAPI m, CASettings s) { return spends(m) ? amount(s) : 0f; }

    public static float projectCap(MarketAPI m, CASettings s) { return spends(m) ? Math.max(s.perProjectCap, amount(s)) : s.perProjectCap; }

    public static float upkeepCap(MarketAPI m, CASettings s) {
        float cap = CABudget.upkeepCap(s);
        if (!spends(m)) return cap;
        return Math.max(cap, (rich(s) ? RICH_UPKEEP_SHARE : UPKEEP_SHARE) * Math.max(0f, CABudget.netIncome(s)));
    }

    /** How much a price counts at this colony: a quarter where the surplus is spent. */
    public static float costWeight(MarketAPI m) { return spends(m) ? CHEAP : 1f; }

    /** Daily: turns surplus investment on once in saves from before it existed, works out the focus world, and logs changes. */
    public static void daily(CASettings s) {
        if (!s.surplusChecked) {
            s.surplusChecked = true;
            if (s.surplusLine == 0) {
                s.surplusLine = 2;
                CALog.add("Surplus investment is on (new in this version): credits over " + Misc.getDGSCredits(line(s)) + " go into one focus world. "
                        + "Change it under Settings, Budget and spending", true);
            }
        }
        compute(s);
        richDaily(s);
        String key = "surplus:on:" + focusId;
        if (!on(s) || focusId == null) {
            for (String id : scores.keySet()) CALog.forget("surplus:on:" + id);
            return;
        }
        MarketAPI f = CAAuto.market(focusId);
        if (f != null && CALog.once(key)) {
            for (String id : scores.keySet()) if (!id.equals(focusId)) CALog.forget("surplus:on:" + id);
            CALog.add("Surplus investment: " + Misc.getDGSCredits(amount(s)) + " over your " + Misc.getDGSCredits(line(s)) + " line goes into "
                    + f.getName() + ", the focus world" + (focusId.equals(s.focusId) ? " (your pick)" : " (" + whys.get(focusId) + ")"), true);
        }
    }

    /** Daily: tracks the best net income while rich (the floor), and logs when spending freely starts, pauses or ends. */
    static void richDaily(CASettings s) {
        float a = amount(s), net = CABudget.netIncome(s);
        if (s.surplusFocusOnly || a < RICH) {
            if (s.richIncomeRef > 0f) {
                s.richIncomeRef = 0f;
                CALog.forget("surplus:rich"); CALog.forget("surplus:richfloor");
                CALog.add("Surplus: under " + Misc.getDGSCredits(RICH) + " over the line now, so only the focus world spends it", true);
            }
            return;
        }
        if (s.richIncomeRef <= 0f || net > s.richIncomeRef) s.richIncomeRef = Math.max(net, 1f);
        if (rich(s)) {
            CALog.forget("surplus:richfloor");
            if (CALog.once("surplus:rich")) CALog.add("Surplus: " + Misc.getDGSCredits(a) + " over your line, so every Auto colony spends it now: past the monthly "
                    + "budget, prices at a quarter, new upkeep up to a fifth of net income a month, and credits count a quarter in what a building "
                    + "is worth, so whatever adds defense, stability, growth or access goes up. Hazard pay goes wherever it speeds growth", true);
        } else if (CALog.once("surplus:richfloor")) CALog.add("Surplus: spending freely pauses: net income (" + Misc.getDGSCredits(net) + ") is under half the best since it began ("
                + Misc.getDGSCredits(s.richIncomeRef) + "), so no more new upkeep until it recovers", true);
    }

    // ---------------- panels ----------------

    static final String SET = "ca:focus:set", AUTO = "ca:focus:auto";

    /** The empire panel's line under Budget. */
    public static void renderEmpire(TooltipMakerAPI t, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        if (line(s) <= 0f) { t.addPara("Surplus investment is off (Settings, Budget and spending).", gray, 4f); return; }
        MarketAPI f = focus();
        float a = amount(s);
        if (f == null) t.addPara("Surplus investment: no Auto colony to be the focus world.", gray, 4f);
        else if (a <= 0f) t.addPara("Surplus investment: credits over %s go into %s, the focus world; you're under the line now.", 4f, hl,
                Misc.getDGSCredits(line(s)), f.getName());
        else t.addPara("Surplus investment: %s over your %s line goes into %s, the focus world" + (f.getId().equals(s.focusId) ? " (your pick)." : " ("
                + CALog.esc(whys.get(f.getId())) + ")."), 4f, hl, Misc.getDGSCredits(a), Misc.getDGSCredits(line(s)), f.getName());
        if (rich(s)) t.addPara("Spending freely (over %s): every Auto colony spends the surplus, prices and credits count a quarter, new upkeep up to a fifth "
                + "of net income a month, hazard pay wherever it speeds growth, and several terraforming projects at once.", 2f, hl, Misc.getDGSCredits(RICH));
        else if (!s.surplusFocusOnly && a >= RICH) t.addPara("Spending freely is paused: net income is under half its best since it began (%s), so no new upkeep "
                + "until it recovers.", 2f, Misc.getNegativeHighlightColor(), Misc.getDGSCredits(s.richIncomeRef));
        float c = committed();
        if (c > 0f) t.addPara("Kept back: %s already promised to production that hasn't been paid yet.", 2f, gray, hl, Misc.getDGSCredits(c));
        if (CATerraSim.summary != null) t.addPara(CALog.esc(CATerraSim.summary), gray, 2f);
        if (CASurplusResearch.summary != null) t.addPara(CALog.esc(CASurplusResearch.summary), gray, 2f);
    }

    /** The colony panel's section: whether this is the focus world, and the buttons to pick it or hand it back. */
    public static void renderColony(TooltipMakerAPI t, MarketAPI m, CASettings s, float pad) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        t.addSectionHeading("Surplus investment", Alignment.MID, pad);
        if (line(s) <= 0f) { t.addPara("Off. Turn it on under Settings, Budget and spending.", gray, 6f); return; }
        MarketAPI f = focus();
        boolean here = f != null && f.getId().equals(m.getId()), mine = m.getId().equals(s.focusId);
        float a = amount(s);
        if (here) t.addPara("This is the focus world" + (mine ? " (your pick)" : " (Autopilot's pick: " + CALog.esc(whys.get(m.getId())) + ")") + ". "
                + (a > 0f ? "%s over your line is here to spend: past the monthly budget and the per-project cap, upkeep up to 10%% of net income a month, "
                + "prices counted at a quarter, and research counts its plan twice." : "Under the line now (%s); it starts when credits pass it."), 6f, hl,
                a > 0f ? Misc.getDGSCredits(a) : Misc.getDGSCredits(line(s)));
        else t.addPara("Credits over %s go into " + (f == null ? "the focus world" : CALog.esc(f.getName()) + ", the focus world") + ". This colony scores %s"
                + (f != null && scores.containsKey(f.getId()) ? " to its %s." : "."), 6f, hl, Misc.getDGSCredits(line(s)),
                scores.containsKey(m.getId()) ? "" + Math.round(scores.get(m.getId())) : "nothing (Auto colonies only)",
                f != null && scores.containsKey(f.getId()) ? "" + Math.round(scores.get(f.getId())) : "");
        if (mine) {
            ButtonAPI b = t.addButton("Let Autopilot choose", AUTO, base, dark, Alignment.MID, CutStyle.ALL, 220f, 24f, 6f);
        } else if (scores.containsKey(m.getId())) {
            ButtonAPI b = t.addButton("Make this the focus world", SET, here ? hl : base, dark, Alignment.MID, CutStyle.ALL, 220f, 24f, 6f);
        }
    }

    /** A colony panel button; returns a message, or null if the id isn't one of these. */
    public static String handle(MarketAPI m, Object id) {
        CASettings s = CAData.settings();
        if (SET.equals(id)) { s.focusId = m.getId(); computedAt = -99f; return m.getName() + " is the focus world"; }
        if (AUTO.equals(id)) { s.focusId = null; computedAt = -99f; return "Autopilot picks the focus world again"; }
        return null;
    }
}
