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
 * 0.3.0.0 (WS-C2, research 03 section 5.2): spending freely follows the money tier (CARich): it's on at Rich and Endgame
 * (rich), while there are credits over the line and the focus-world-only setting is off; 0.3.0.0 (C12 fix): it starts at half
 * the surplus line over it and, once on, ends under a fifth of the line over it (or with the tier), so it's never on with no
 * credits over the line. Every caller of rich() (the
 * planner and build pass, hazard pay, surplus terraforming, war fleets, wonders, research, the endgame) follows the tier
 * through it. At Idle money and up a credit counts half in what a build is worth at every Approve and Auto colony
 * (moneyWeight). With the hidden legacyPlanner switch, spending freely is 0.2.45.1's rule (richAt). The focus world itself
 * is unchanged: credits over the line, at any tier.
 */
public class CASurplus {
    /** An Autopilot-picked focus world changes only for one that scores this much higher, so it doesn't flip back and forth. */
    static final float MARGIN = 10f;
    /** At the focus world, while there's a surplus, a price counts a quarter. */
    static final float CHEAP = CATuning.FOCUS_PRICE;   // 0.3.0.0 (A3): in CATuning
    /** The focus world's new upkeep cap while there's a surplus: this share of net income, if more than the setting. */
    static final float UPKEEP_SHARE = CATuning.FOCUS_UPKEEP_SHARE;

    static String focusId;      // today's focus world
    static float computedAt = -99f;
    static final Map<String, Float> scores = new LinkedHashMap<String, Float>();
    static final Map<String, String> whys = new HashMap<String, String>();

    public static void reset() {
        focusId = null; computedAt = -99f; scores.clear(); whys.clear(); CAItems.idle = null; CAItems.idleAt = -99f;
        committedAt = Long.MIN_VALUE;
        CASurplusResearch.summary = null;
        CAProjects.reset();
        CARich.reset();   // 0.3.0.0 (C2): the money tier's day cache; the tier itself is in the save
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
     * orders' remaining units (each is paid when it's finished; Auto Outfitting's weapon orders go there).
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
                int free = Math.max(0, CABuild.freeSlots(m));
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
        if (!CATime.fresh(computedAt, now(), 1f) && !(CAEngine.noProbe && computedAt != -99f)) compute(s);   // 0.3.0.0 (A1 fix): right on the game's negative clock
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
    // they cost more than they earn. What it may spend comes from the pool, not from income (0.2.34.1): builds and projects draw
    // on the surplus, and the month's new upkeep may reach a sixtieth of it (the pool covers five years of it), shrinking as
    // the pool does. A weak month doesn't stop it (it used to pause under half the best month, and cap upkeep at a fifth of net income).
    // 0.3.0.0 (C2): it starts and ends with the money tier Rich (CARich). RICH and RICH_OFF are the rule with legacyPlanner.

    /** Credits over the line past which every colony spends from the surplus (legacyPlanner; CARich's Rich line otherwise). */
    public static final float RICH = CATuning.RICH;   // 0.3.0.0 (A3): these numbers live in CATuning
    /** While rich, a credit counts this much in what a build is worth (income and upkeep alike). */
    public static final float RICH_MONEY = CATuning.RICH_MONEY;
    /** While rich, the month's new upkeep may reach the surplus divided by this many months. */
    static final float RICH_UPKEEP_MONTHS = CATuning.RICH_UPKEEP_MONTHS;

    /**
     * 0.3.0.0 (WS-B5, research 02 section 8): once on, spending freely stays on until the surplus falls under this. Without
     * it a few 750,000 shields took a 10.5M surplus under 10M and everything flipped back the same week: weekly passes,
     * money at full weight, the rich plan steps gone, the endgame off at non-focus colonies, and a prune that took down
     * what spending freely had just built.
     */
    public static final float RICH_OFF = CATuning.RICH_OFF;   // 0.7 x RICH; the number lives in CATuning
    /**
     * The once-key that says spending freely is on (CALog.GUARDS "surplus:", kept in the save). It's the hysteresis state:
     * with legacyPlanner 0.2.45.1's (on at RICH, off under RICH_OFF); 0.3.0.0 (C12 fix) otherwise the surplus band below
     * (RICH_START_SHARE, RICH_HOLD_SHARE). 0.3.0.0 (C2): a save from before 0.3.0 with it set starts at the money tier Rich
     * (CARich.update).
     */
    static final String RICH_KEY = "surplus:rich";

    /** 0.3.0.0 (C12 fix): spending freely starts with credits this share of the surplus line over it (1.5M over the 3M line)... */
    static final float RICH_START_SHARE = 0.5f;
    /** ...and once on holds while they stay this share of the line over it (600,000 over the 3M line). Never at 0 surplus. */
    static final float RICH_HOLD_SHARE = 0.2f;

    /**
     * Spending freely. 0.3.0.0 (C2): the money tier is Rich or Endgame (CARich.tier, which holds down to 60% of its line),
     * there are credits over the surplus line, and the focus-world-only setting is off. With legacyPlanner, 0.2.45.1's rule:
     * RICH over the line, and once on until under RICH_OFF.
     * 0.3.0.0 (C12 fix): the credits over the line have a start and an end line of their own, both over the surplus line:
     * it starts at RICH_START_SHARE of the line over it and, once on (RICH_KEY noted), ends under RICH_HOLD_SHARE of it. One
     * build or one month's upkeep inside that band doesn't end it and start it again (each flip wrote two screen lines,
     * changed the pass cadence, re-planned every colony and turned the endgame off and on). It's never on with no credits
     * over the line, so the callers that pay from the surplus while rich (war fleets, uncapped hazard pay, the endgame,
     * research items) always have some to pay from, as in 0.2.45.1 (7M at least there). The first C12 form held it while the
     * tier was Rich, also under the line. Surplus investment Off (no line) ends it at once, as in 0.2.45.1.
     */
    public static boolean rich(CASettings s) {
        if (CABudget.legacy(s)) return richAt(s.surplusFocusOnly, amount(s), richWas());
        return richFor(s.surplusFocusOnly, line(s), amount(s), CARich.tier(), richWas());
    }

    /** Pure (0.3.0.0, C12 fix): credits over the line that start spending freely at this surplus line. */
    static float richStart(float line) { return RICH_START_SHARE * Math.max(0f, line); }

    /** Pure (0.3.0.0, C12 fix): credits over the line under which spending freely ends once on. */
    static float richHold(float line) { return RICH_HOLD_SHARE * Math.max(0f, line); }

    /**
     * Pure (0.3.0.0, C12 fix): spending freely. focusOnly: the focus-world-only setting; line: the surplus line (0: surplus
     * investment Off); amount: credits over it (amount()); tier: the money tier; wasOn: it was on at its last daily look
     * (RICH_KEY). On at Rich or Endgame with richStart(line) over the line; once on, while the tier holds and richHold(line)
     * is over it. Off with no credits over the line.
     */
    static boolean richFor(boolean focusOnly, float line, float amount, int tier, boolean wasOn) {
        if (focusOnly || !(line > 0f) || tier < CARich.T3 || !(amount > 0f)) return false;
        return amount >= richStart(line) || (wasOn && amount >= richHold(line));
    }

    /** Whether spending freely was on at its last daily look (the saved once-key); false with no save. */
    static boolean richWas() {
        try { return CALog.noted(RICH_KEY); } catch (Throwable t) { return false; }
    }

    /** Pure: spending freely switching on (0.2.45's rule, no memory): RICH or more over the line. */
    static boolean richAt(boolean focusOnly, float amount) { return richAt(focusOnly, amount, false); }

    /** Pure, 0.3.0.0 (B5): on at RICH; once on (wasOn) it stays on down to RICH_OFF. The focus-world-only setting turns it off at once. */
    static boolean richAt(boolean focusOnly, float amount, boolean wasOn) {
        if (focusOnly) return false;
        return amount >= RICH || (wasOn && amount >= RICH_OFF);
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

    /**
     * What a credit counts in a build's worth at this colony: RICH_MONEY while rich where it spends the surplus; 0.3.0.0 (C2):
     * CARich.IDLE_MONEY at any Approve or Auto colony while the money tier is Idle money or higher; else 1.
     */
    public static float moneyWeight(MarketAPI m) {
        if (m == null) return 1f;
        CASettings s = CAData.settings();
        if (spends(m) && rich(s)) return RICH_MONEY;
        boolean legacy = CABudget.legacy(s);
        int tier = legacy ? CARich.T0 : CARich.tier();
        if (tier < CARich.T2) return 1f;
        CAData.Mode mode = CAData.get().colony(m.getId()).mode;
        boolean managed = (mode == CAData.Mode.AUTO || mode == CAData.Mode.APPROVE) && !CAEngine.isOutpost(m) && !CAHypershunt.is(m);
        return moneyWeight(false, managed, tier, legacy);
    }

    /** Pure (0.3.0.0, C2): what a credit counts: rich where the surplus is spent; half at a managed colony from Idle money up (not with legacyPlanner); else 1. */
    static float moneyWeight(boolean spendsRich, boolean managed, int tier, boolean legacy) {
        if (spendsRich) return RICH_MONEY;
        return !legacy && managed && tier >= CARich.T2 ? CARich.IDLE_MONEY : 1f;
    }

    /** Spending the colony may do beyond the monthly budget: the surplus, at the focus world (every colony while rich). 0 elsewhere. */
    public static float extra(MarketAPI m, CASettings s) { return spends(m) ? amount(s) : 0f; }

    public static float projectCap(MarketAPI m, CASettings s) { float cap = CAPresets.projectCap(s); return spends(m) ? Math.max(cap, amount(s)) : cap; }   // 0.3.0.0 (WS-F1 fix): the cap in effect, Automatic or Fixed

    public static float upkeepCap(MarketAPI m, CASettings s) {
        float cap = CABudget.upkeepCap(s);
        if (!spends(m)) return cap;
        if (rich(s)) return Math.max(cap, amount(s) / RICH_UPKEEP_MONTHS);   // from the pool, not from income
        return Math.max(cap, UPKEEP_SHARE * Math.max(0f, CABudget.netIncome(s)));
    }

    /** How much a price counts at this colony: a quarter where the surplus is spent, a twentieth while spending freely. */
    public static float costWeight(MarketAPI m) {
        if (!spends(m)) return 1f;
        return rich(CAData.settings()) ? RICH_PRICE : CHEAP;
    }

    /** While spending freely, a build's price counts this much: tens of millions have nothing better to do. */
    static final float RICH_PRICE = CATuning.RICH_PRICE;

    /**
     * Spending freely, every colony maxes its structures (no industry slot needed): its station to the top tier of its line,
     * Heavy Batteries, a Planetary Shield, a Megaport and a Patrol HQ. They go first in its plan, built even when they
     * earn less than they cost (CAEngine.finish); ones the colony can't build here (research, a mod's rules) simply wait.
     */
    static final String[] RICH_STRUCTURES = {"heavybatteries", "planetaryshield", "megaport", "patrolhq"};

    static List<CAPlan.Step> richSteps(MarketAPI m) {
        if (m == null || !spends(m) || !rich(CAData.settings())) return Collections.emptyList();
        List<CAPlan.Step> out = new ArrayList<CAPlan.Step>();
        String top = stationTop(m);
        if (top != null) out.add(new CAPlan.Step(CAPlan.Kind.BUILD, top, CAWorldPlanner.industryName(top) + " (surplus credits: every station maxed)"));
        for (String id : RICH_STRUCTURES) out.add(new CAPlan.Step(CAPlan.Kind.BUILD, id, CAWorldPlanner.industryName(id) + " (surplus credits)"));
        return out;
    }

    static boolean isRichStep(MarketAPI m, String industryId) {
        for (CAPlan.Step s : richSteps(m)) if (s.id.equals(industryId) || CAPlan.leadsTo(industryId, s.id)) return true;
        return false;
    }

    /** The top of the colony's station line (its own station's upgrades, or the chosen line's), up to a star fortress; null when it's there. */
    static String stationTop(MarketAPI m) {
        com.fs.starfarer.api.campaign.econ.Industry st = null;
        for (com.fs.starfarer.api.campaign.econ.Industry ind : m.getIndustries()) if (CAPlan.stationTier(ind.getSpec()) > 0) st = ind;
        String id = st != null ? st.getId() : CASettings.stationStarter(CAData.settings().stationLine);
        if (id == null) id = "orbitalstation";
        for (int k = 0; k < 6; k++) {
            com.fs.starfarer.api.loading.IndustrySpecAPI sp = CAPlan.spec(id);
            String up = sp == null ? null : sp.getUpgrade();
            com.fs.starfarer.api.loading.IndustrySpecAPI upSpec = up == null ? null : CAPlan.spec(up);
            if (upSpec == null || CAPlan.stationTier(upSpec) > 3) break;   // Star Citadels and the Stella Castellum aren't built from the menu
            id = up;
        }
        return st != null && st.getId().equals(id) ? null : id;
    }

    /** Daily: turns surplus investment on once in saves from before it existed, works out the focus world, and logs changes. */
    public static void daily(CASettings s) {
        if (!s.surplusChecked) {
            s.surplusChecked = true;
            if (s.surplusLine == 0) {
                s.surplusLine = 2;
                CALog.did("Surplus investment: on. Credits over " + Misc.getDGSCredits(line(s)) + " go into one focus world. "
                        + "Setting: Settings, Money.");
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
            CALog.quiet("Focus world: " + f.getName() + (focusId.equals(s.focusId) ? " (your pick)" : "") + ", " + Misc.getDGSCredits(amount(s)) + " surplus");   // 0.3.0.9: status, starsector.log only
        }
    }

    /**
     * Daily: logs when spending freely starts or ends. 0.3.0.0 (B5): its once-key is the hysteresis state too, so it's set
     * the day spending freely starts and cleared only the day it ends (under RICH_OFF, or the focus-world-only setting).
     * 0.3.0.0 (C2): outside legacyPlanner it ends when the money tier drops under Rich, or with the focus-world-only setting.
     * 0.3.0.0 (C12 fix): and when the credits over the surplus line fall under richHold, or surplus investment is turned Off.
     */
    static void richDaily(CASettings s) {
        float a = amount(s);
        boolean legacy = CABudget.legacy(s);
        // 0.3.0.9: spending freely starting and ending are status, starsector.log only
        if (rich(s)) {
            if (CALog.once(RICH_KEY)) CALog.quiet("Spending freely: " + Misc.getDGSCredits(a) + " surplus, every Auto colony spends it");
        } else if (CALog.noted(RICH_KEY)) {   // 0.3.0.0: once-keys live in CAData.notedAt (CALog)
            CALog.forget(RICH_KEY);
            // 0.3.0.0 (C12 fix): outside legacyPlanner it ends with the tier, the surplus under richHold, the focus-world-only
            // setting or surplus investment Off (rich())
            String why = s.surplusFocusOnly ? "focus world only" : line(s) <= 0f ? "surplus investment off"
                    : legacy ? "surplus under " + Misc.getDGSCredits(RICH_OFF)
                    : CARich.tier() < CARich.T3 ? "money tier " + CARich.name(CARich.tier()) : "surplus under " + Misc.getDGSCredits(richHold(line(s)));
            CALog.quiet("Spending freely ended (" + why + ")");
        }
    }

    // ---------------- panels ----------------

    static final String SET = "ca:focus:set", AUTO = "ca:focus:auto";
    /** 0.3.0.0 (WS-F3 fix): the hover of the Budget page's spending freely line. */
    static final String RICH_HOVER = "Every Auto colony spends the surplus.";

    /** The empire panel's line under Budget. */
    public static void renderEmpire(TooltipMakerAPI t, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        if (line(s) <= 0f) { t.addPara("Surplus is off (Settings, Money).", gray, 4f); return; }
        MarketAPI f = focus();
        float a = amount(s);
        if (f == null) t.addPara("Surplus: no Auto colony for a focus world.", gray, 4f);
        else if (a <= 0f) t.addPara("Surplus line %s, focus world %s. Under the line now.", 4f, hl,
                Misc.getDGSCredits(line(s)), f.getName());
        else t.addPara("Surplus: %s over the %s line goes into %s" + (f.getId().equals(s.focusId) ? " (your pick)." : " ("
                + CALog.esc(whys.get(f.getId())) + ")."), 4f, hl, Misc.getDGSCredits(a), Misc.getDGSCredits(line(s)), f.getName());
        boolean legacy = CABudget.legacy(s);
        // 0.3.0.0 (WS-F3 fix): spending freely in one line each (was 32 and 41 words); what it spends on is hover text (RICH_HOVER)
        if (rich(s) && legacy) {
            t.addPara("Spending freely from %s to under %s. New upkeep to %s.", 2f, hl,
                    Misc.getDGSCredits(RICH), Misc.getDGSCredits(RICH_OFF), Misc.getDGSCredits(a / RICH_UPKEEP_MONTHS));
            CATheme.hover(t, RICH_HOVER);
        }
        // 0.3.0.0 (C2): spending freely and the half money weight follow the money tier (CARich)
        // 0.3.0.0 (C12 fix): with the surplus band it starts and ends at (richStart, richHold)
        else if (rich(s)) {
            t.addPara("Spending freely (tier %s) from %s over the line to under %s. New upkeep up to %s.",
                    2f, hl, CARich.name(CARich.tier()), Misc.getDGSCredits(richStart(line(s))), Misc.getDGSCredits(richHold(line(s))),
                    Misc.getDGSCredits(a / RICH_UPKEEP_MONTHS));
            CATheme.hover(t, RICH_HOVER);
        }
        else if (!legacy && CARich.tier() >= CARich.T3 && !s.surplusFocusOnly) t.addPara("Money tier %s: spending freely from %s over the line (%s now).",
                2f, hl, CARich.name(CARich.tier()),
                Misc.getDGSCredits(richStart(line(s))), Misc.getDGSCredits(a));
        else if (!legacy && CARich.tier() >= CARich.T2) t.addPara("Money tier %s: credits count half in build values.",
                2f, hl, CARich.name(CARich.tier()));
        CAEndgame.render(t, s);
        float c = committed();
        if (c > 0f) t.addPara("Kept back: %s promised to production.", 2f, gray, hl, Misc.getDGSCredits(c));
        if (CATerraSim.summary != null) t.addPara(CALog.esc(CATerraSim.summary), gray, 2f);
        if (CASurplusResearch.summary != null) t.addPara(CALog.esc(CASurplusResearch.summary), gray, 2f);
        if (CAWonders.summary != null) t.addPara(CALog.esc(CAWonders.summary), gray, 2f);
    }

    /** The colony panel's section: whether this is the focus world, and the buttons to pick it or hand it back. */
    public static void renderColony(TooltipMakerAPI t, MarketAPI m, CASettings s, float pad) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        CATheme.heading(t, "Surplus investment", pad);
        if (line(s) <= 0f) { t.addPara("Off (Settings, Money).", gray, 6f); return; }
        MarketAPI f = focus();
        boolean here = f != null && f.getId().equals(m.getId()), mine = m.getId().equals(s.focusId);
        float a = amount(s);
        if (here) {   // 0.3.0.0 (WS-F3): 40 words or fewer; the spending rules on hover
            t.addPara("This is the focus world" + (mine ? " (your pick)" : " (Autopilot's pick: " + CALog.esc(whys.get(m.getId())) + ")") + ". "
                    + (a > 0f ? "%s over the line to spend." : "Under the line (%s)."), 6f, hl,
                    a > 0f ? Misc.getDGSCredits(a) : Misc.getDGSCredits(line(s)));
            // 0.3.0.0 (WS-F3 fix pass 2): spending past the budget and the per-project cap is in the guide
        }
        else t.addPara("Over %s goes to " + (f == null ? "the focus world" : CALog.esc(f.getName())) + ". Score here: %s"
                + (f != null && scores.containsKey(f.getId()) ? " to its %s." : "."), 6f, hl, Misc.getDGSCredits(line(s)),
                scores.containsKey(m.getId()) ? "" + Math.round(scores.get(m.getId())) : "nothing (Auto only)",
                f != null && scores.containsKey(f.getId()) ? "" + Math.round(scores.get(f.getId())) : "");
        if (mine) {
            ButtonAPI b = t.addButton("Let Autopilot choose", AUTO, base, dark, Alignment.MID, CutStyle.TL_BR, 220f, 24f, 6f);
        } else if (scores.containsKey(m.getId())) {
            ButtonAPI b = t.addButton("Make this the focus world", SET, here ? hl : base, dark, Alignment.MID, CutStyle.TL_BR, 220f, 24f, 6f);
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
