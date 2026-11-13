package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/** Colony Autopilot scoring: vanilla effect formulas from the game code, weighted by the colony's profile. */
public class CAEngine {

    /** Vanilla buildings the engine has formulas for; everything else is never acted on. */
    static final Set<String> VANILLA = new HashSet<String>(Arrays.asList(
            "farming", "aquaculture", "mining", "techmining", "refining", "lightindustry", "heavyindustry",
            "orbitalworks", "fuelprod", "spaceport", "megaport", "waystation", "patrolhq", "militarybase",
            "highcommand", "grounddefenses", "heavybatteries", "planetaryshield", "cryosanctum", "cryorevival",
            "orbitalstation", "battlestation", "starfortress", "orbitalstation_mid", "battlestation_mid",
            "starfortress_mid", "orbitalstation_high", "battlestation_high", "starfortress_high"));

    public static boolean isSupportedVanilla(String id) { return VANILLA.contains(id); }

    /** Buildings the engine can score: vanilla formulas plus estimated effects for mapped mod buildings. */
    public static boolean isSupported(String id) {
        return VANILLA.contains(id) || CAEstimates.has(id) || CATasc.has(id) || CABrewery.has(id) || CAMods.has(id) || CACapital.has(id);
    }

    public static String credits(float v) { return Misc.getDGSCredits(v); }

    /** Credits per month per unit, per credit of base price, measured from your colonies' real export income. */
    public static float unitValue = CATuning.UNIT_VALUE_DEFAULT;

    static void calibrate() {
        int day = today();
        calibrate(day, CACache.epoch(), day >= 0 ? empireSig() : 0L);
    }

    /** 0.3.0.0 (A4, 07 M7): worked out once a day while no colony changes (it read every commodity of every colony per evaluation). */
    static final CACache.Memo<Float> UNIT_VALUES = new CACache.Memo<Float>("unit value", 4);

    static void calibrate(int day, int ep, long empire) {
        Float kept = day >= 0 ? UNIT_VALUES.get("empire", day, empire) : null;
        if (kept != null) { unitValue = kept; return; }
        calibrateNow();
        if (day >= 0) UNIT_VALUES.put("empire", day, ep, empire, unitValue);
    }

    static void calibrateNow() {
        float income = 0f, base = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            for (CommodityOnMarketAPI c : m.getAllCommodities()) {
                int ex = c.getExportIncome();
                if (ex <= 0 || c.getMaxSupply() <= 0) continue;
                income += ex;
                base += c.getCommodity().getBasePrice() * c.getMaxSupply();
            }
        }
        unitValue = base > 0f ? Math.max(CATuning.UNIT_VALUE_MIN, Math.min(CATuning.UNIT_VALUE_MAX, income / base)) : CATuning.UNIT_VALUE_DEFAULT;
    }

    static final Map<String, String> DEPOSIT = new HashMap<String, String>();
    static final Map<String, Integer> DEPOSIT_MOD = new HashMap<String, Integer>();
    static final Map<String, Integer> BASE_MOD = new HashMap<String, Integer>();
    static {
        put("ore_sparse", "ore", -1); put("ore_moderate", "ore", 0); put("ore_abundant", "ore", 1);
        put("ore_rich", "ore", 2); put("ore_ultrarich", "ore", 3);
        put("rare_ore_sparse", "rare_ore", -1); put("rare_ore_moderate", "rare_ore", 0); put("rare_ore_abundant", "rare_ore", 1);
        put("rare_ore_rich", "rare_ore", 2); put("rare_ore_ultrarich", "rare_ore", 3);
        put("volatiles_trace", "volatiles", -1); put("volatiles_diffuse", "volatiles", 0);
        put("volatiles_abundant", "volatiles", 1); put("volatiles_plentiful", "volatiles", 2);
        put("organics_trace", "organics", -1); put("organics_common", "organics", 0);
        put("organics_abundant", "organics", 1); put("organics_plentiful", "organics", 2);
        put("farmland_poor", "food", -1); put("farmland_adequate", "food", 0);
        put("farmland_rich", "food", 1); put("farmland_bountiful", "food", 2);
        BASE_MOD.put("ore", 0); BASE_MOD.put("rare_ore", -2); BASE_MOD.put("volatiles", -2);
        BASE_MOD.put("organics", 0); BASE_MOD.put("food", 0);
    }
    static void put(String cond, String com, int mod) { DEPOSIT.put(cond, com); DEPOSIT_MOD.put(cond, mod); }

    /** Profile weights: credit-equivalent per month for one unit of each effect. 0.3.2.0: the 0.3.0 table (WEIGHTS_030), used with production Off or the 0.3.0 table setting. */
    static final float[][] WEIGHTS = {
            //            credits stab   access  defense fleet  quality growth
            /* BALANCED */ {1.0f, 3000f, 20000f, 3000f, 4000f, 10000f, 1500f},
            /* INCOME   */ {1.6f, 2500f, 30000f, 800f, 800f, 2000f, 1000f},
            /* SECURITY */ {0.5f, 4000f, 8000f, 9000f, 9000f, 4000f, 500f},
            /* WAR      */ {0.8f, 2500f, 10000f, 4000f, 10000f, 30000f, 500f},
            /* GROWTH   */ {0.6f, 3500f, 30000f, 1500f, 1000f, 1000f, 6000f},
            /* SELF     */ {1.0f, 3000f, 20000f, 3000f, 4000f, 10000f, 1500f}};
    static final float[][] WEIGHTS_030 = WEIGHTS;
    /** 0.3.2.0 (M4): the 0.3.2 table. Balanced, Self-sufficiency and War production count fleets and ship quality more; Money, Security and Rapid growth are unchanged. */
    static final float[][] WEIGHTS_NEW = {
            //            credits stab   access  defense fleet  quality growth
            /* BALANCED */ {1.0f, 3500f, 20000f, 3500f, 6000f, 15000f, 1500f},
            /* INCOME   */ {1.6f, 2500f, 30000f, 800f, 800f, 2000f, 1000f},
            /* SECURITY */ {0.5f, 4000f, 8000f, 9000f, 9000f, 4000f, 500f},
            /* WAR      */ {0.8f, 2500f, 10000f, 4000f, 15000f, 40000f, 500f},
            /* GROWTH   */ {0.6f, 3500f, 30000f, 1500f, 1000f, 1000f, 6000f},
            /* SELF     */ {0.9f, 3000f, 15000f, 3000f, 4000f, 10000f, 1500f}};

    /** 0.3.2.0: a goal's weights in use: the 0.3.2 table while production is on with it, else the 0.3.0 table (production Off: exactly 0.3.1.0). */
    public static float[] goalWeights(CAData.Profile profile) {
        CAData.Profile p = profile == null ? CAData.Profile.BALANCED : profile;
        CAFloors.State st = CAFloors.state();
        return (st != null && st.on() && st.newWeights ? WEIGHTS_NEW : WEIGHTS)[p.ordinal()];
    }
    /** Fast buildout's weights: the Money goal's, with ground defense, fleets and ship quality worth nothing, since they earn nothing and add upkeep. */
    static final float[] PROFIT = {1.6f, 2500f, 30000f, 0f, 0f, 0f, 1000f};

    public static class Pick {
        public String id, name, verb;
        public boolean upgrade;
        public Industry existing;
        public float cost, days, score, upkeep;
        /** How score is made up (finish()): the effects valued with the goal's weights, the plan bonus, and the build cost
         *  and time spread over three years. score = worth + planBonus + followThrough - buildCost.
         *  0.3.0.0 (B2): score = worth + planBonus - buildCost - reserve, a month: buildCost is the price and fee over the
         *  horizon plus the worth lost while it's built (CAHorizon.price), planBonus is half of what it opens the way to plus a
         *  small tie-break for an ordinary plan step, or the fixed bonus by rank for an enabler; followThrough is 0 (legacyPlanner only). */
        public float worth, planBonus, followThrough, buildCost;
        /**
         * 0.3.0.0 (B2): the parts of the 0.3.0 score. downstream: half of what the plan steps it opens the way to would add (in
         * planBonus); reserve: what an off-plan industry gives up for a slot the plan needs; fee: the convenience fee priced in;
         * horizon: months it's paid back over; npv: worth + downstream - buildCost, times the horizon (credits).
         */
        public float downstream, reserve, fee, horizon, npv;
        /** 0.3.0.0 (B2): it advances an open step of the colony's plan (whatever its bonus); enabler: one built for what it enables (fixed bonus). */
        public boolean planStep, enabler;
        public float[] fx = new float[7];
        public List<String> lines = new ArrayList<String>();
        public boolean pollution;
        /** Low profile (0.2.43.0): why it never starts this (it crosses the Pather line), or null. */
        public String lowProfile;
        public boolean measured;   // a mod building scored from probe-colony measurements (CASimulator)
        /** The building's own output lines, recorded while its effects are read, so a management style can adjust them. */
        public Map<String, Integer> supplied = new LinkedHashMap<String, Integer>();
        boolean recording;
        float suppliedValue;   // value of those lines, for calibration
        /** Market model (CAMarket, 0.2.44.0): units passed per good so far, the colony's supply of each once built, and each good's export value. */
        Map<String, Integer> marketUnits = new HashMap<String, Integer>(), marketAfter = new LinkedHashMap<String, Integer>();
        Map<String, Float> marketValue = new LinkedHashMap<String, Float>();
        /** How sure the value is: 1 for the game's own formulas, less for measurements and estimates (see finish()). */
        public float confidence = 1f;
        /**
         * 0.3.0.0 (B1, research 02 section 9): units per good the pick adds over the colony's other sources (the market model's
         * netted line; the whole line with the model off). The endgame's production chains read these through
         * CAValue.chainUnits, not supplied, so a duplicate source earns no chain value. supplied stays whole for calibration.
         */
        public Map<String, Integer> added = new LinkedHashMap<String, Integer>();
        /** 0.3.0.0 (B1): the colony its lines were scored at, so a later change to them (a management style) is netted the same way. Session only. */
        MarketAPI at;
        /**
         * 0.3.0.0 (B1 fix): what demand() charged it for inputs your colonies lack (credits a month, in fx[0] as a minus). A build
         * pays it; keeping one doesn't (CAValue.keepDetail): the game already cuts the building's live output for its own
         * shortage, and taking a consumer down at one colony eases no other colony's shortage (0.2.45.1's prune left it out too).
         */
        float inputPenalty;
        /**
         * 0.3.0.0 (B1 fix): the part of an estimate's flat income (CAEstimates) that stands for goods the building really makes
         * (Industrial Evolution's Variable Assembler, Manufactory and Commodity Forge, Salvage Yards). 0.3.0.0 (B1 fix, second
         * pass): netted on both sides the same way, so it's kept at what it was added at: here by the share of its line above
         * the colony's other sources (CAValue.netShare, vpcShare), in keep() by the share of the same line (keptShare of
         * CAValue.estimateLine) for the goods its live lines make; its live lines aren't priced on top. keep() reads it whole
         * (CAValue.rawEstimates) and nets it itself.
         */
        float outputEstimate;
        /**
         * 0.3.0.0 (B1 fix): its lines are a building's whole output (effects(): the game's formulas or Autopilot's estimates), not
         * a change to one. An upgrade scored that way (Monoculture Plots to Farming with Building Menu Overhaul) replaces the old
         * building's line in CAMarket.pickGain instead of stacking on it; CAMods' Union HQ and probe measurements pass changes.
         */
        boolean wholeLines;
        /** 0.3.2.0: what it adds to the open production floors a month (CAFloors.value, in its worth); the planner ranks floor picks first. */
        public float floorValue;
        /** 0.3.2.0: Domain-grade Parts its market gains a month (parts()), for the parts floor. */
        public float partsAdded;
    }

    /** 0.3.0.0 (B1 fix): Autopilot's estimates whose flat income is the goods the building makes (see Pick.outputEstimate). */
    static final Set<String> OUTPUT_ESTIMATES = new HashSet<String>(Arrays.asList("IndEvo_AdAssem", "IndEvo_AdManuf", "IndEvo_ComForge", "IndEvo_ScrapYard"));

    /** Buildings scored from Autopilot's own estimates rather than the game's formulas or a probe measurement. */
    static boolean estimated(String id) { return CATasc.has(id) || CABrewery.has(id) || CAMods.has(id) || CAEstimates.has(id) || CACapital.has(id); }

    /**
     * What a building does here by the game's formulas or Autopilot's estimates, with its output lines recorded (p.supplied);
     * null for one the engine can't value that way (measured or special-cased mod buildings). For the endgame's production
     * chains and what an existing building is worth.
     */
    static Pick effectsOf(MarketAPI m, String id, Map<String, Integer> deficit) {
        if (!VANILLA.contains(id) && !CAEstimates.has(id)) return null;
        Pick p = new Pick();
        p.id = id;
        p.recording = true;
        effects(m, id, p, deficit);
        p.recording = false;
        return p;
    }

    /**
     * 0.3.0.0 (B1 fix, second pass): effectsOf for a new building valued outside evaluate (a plan step's downstream worth,
     * stepWorth), read the way evaluate reads a pick: under scoring(inst, m), so supply() goes through the 0.2.44.0 market model
     * (CAMarket.pickGain: netted against the colony's largest other source, priced by share, relief capped at the shortage).
     * effectsOf takes the flat path (supplierAt null), so a step that duplicates a source here counted its whole output at the
     * flat rate. existing: the building it upgrades when that's here (its line is replaced, not stacked on), else null. inst:
     * the building itself, for whether its output is legal here (null: legal). Null as effectsOf.
     */
    static Pick effectsAt(MarketAPI m, String id, Industry inst, Industry existing, Map<String, Integer> deficit) {
        if (m == null || (!VANILLA.contains(id) && !CAEstimates.has(id))) return null;
        Pick p = new Pick();
        p.id = id;
        if (existing != null) { p.upgrade = true; p.existing = existing; }
        p.recording = true;
        Industry sup = supplier;
        MarketAPI at = supplierAt;
        scoring(inst, m);
        try { effects(m, id, p, deficit); }
        finally { scoring(sup, at); }
        p.recording = false;
        return p;
    }

    /** Effects of an estimated building; false when it isn't worth building here. */
    static boolean estimate(MarketAPI m, String id, Pick p, Map<String, Integer> deficit) {
        if (CATasc.has(id)) return CATasc.apply(m, id, p, deficit);   // TASC: scored here, never test-built
        if (CABrewery.has(id)) return CABrewery.apply(m, p, deficit);   // Substance.Abuse: only with a recipe on hand
        if (CAMods.has(id)) return CAMods.apply(m, id, p, deficit);   // VIC treatment, Roider Dives and Union HQ
        if (CACapital.has(id)) return CACapital.apply(m, id, p, deficit);   // mod trade buildings: only at the capital (0.2.42.0)
        if (CAEstimates.has(id)) { CAEstimates.apply(m, id, p, deficit); return true; }
        return false;
    }

    /**
     * How far to trust a pick: estimates 0.85 (0.92 once checked against your colonies), measurements 0.9, a mod
     * version the integration wasn't written for 0.05 less. Estimated output is scaled by what your colonies showed.
     */
    static void trust(IndustrySpecAPI spec, Pick p, boolean measured) {
        String id = spec.getId();
        if (estimated(id)) {
            p.confidence = CACalibrate.calibrated(id) ? 0.92f : 0.85f;
            float f = CACalibrate.outputFactor(id);
            if (Math.abs(f - 1f) > 0.01f && p.suppliedValue != 0f) {
                p.fx[0] += (f - 1f) * p.suppliedValue;
                p.lines.add("Output corrected from your colonies (x" + String.format(Locale.ROOT, "%.2f", f) + ")");
            }
        } else if (measured) p.confidence = 0.9f;
        if (spec.getSourceMod() != null && CACompat.untested(spec.getSourceMod().getId())) p.confidence -= 0.05f;
    }

    public static class Result {
        public List<Pick> picks = new ArrayList<Pick>();
        public int unsupportedBuildable = 0;
        public int noSlot = 0;
        public int locked = 0;   // supported buildings the build menu locks here (Ashes of the Domain research)
        public Pick bestNoSlot = null;   // the best of the noSlot ones, scored as if a slot were free
        public String blocked = null;
        /** 0.3.0.0 (A4 fix): cached() while a panel draws, with nothing kept: an empty stand-in, not an evaluation. */
        public boolean notScored = false;
        /** 0.3.2.0: the best pick with no slot that adds to an open production floor (make room, CAFloors.makeRoom), and every such pick (CAAuto.approve). */
        public Pick bestFloorNoSlot = null;
        public List<Pick> floorNoSlot = new ArrayList<Pick>();

        void noSlot(Pick p) {
            noSlot++;
            if (bestNoSlot == null || p.score > bestNoSlot.score) bestNoSlot = p;
            if (p.floorValue > 0f) {
                floorNoSlot.add(p);
                if (bestFloorNoSlot == null || p.score > bestFloorNoSlot.score) bestFloorNoSlot = p;
            }
        }
    }

    /** Set while CAResearch values techs: no probe-colony measurements under a what-if research state. */
    static boolean noProbe = false;
    /** Set while evaluateForProfit runs. */
    static boolean profitOnly = false;
    /**
     * 0.3.0.0 (A4 fix, 07 M6): set while an evaluation is only for showing (a panel's Refresh button, the daily pass's panel
     * scores): no CAPlan.track, so looking at a colony never writes stepStuck or worldDead, logs, or bumps the epoch.
     */
    static boolean forDisplay = false;
    /** 0.3.0.0 (A4 fix): above 0 while a panel draws (CAIntel, CAColonyDialog). cached() then never evaluates. */
    static int drawing = 0;

    /** A panel starts (true) or ends (false) drawing; always in pairs, the end in a finally. */
    public static void drawing(boolean on) { drawing = on ? drawing + 1 : Math.max(0, drawing - 1); }

    /**
     * 0.3.0.0 (A4 fix): evaluations made while a page drew, this session. Should stay 0 (pages read kept()); anything above is
     * shown on the Diagnostics page and logged once with the caller's stack trace, then weekly (CAStep.warn). Not refused:
     * a caller like research's ranking would keep the empty answer for a day.
     */
    static int drawnEvals = 0;

    static void drawnEval(MarketAPI m) {
        drawnEvals++;
        String name;
        try { name = m == null ? "?" : m.getName(); } catch (Throwable t) { name = "?"; }
        CAStep.warn("page drawing", "a colony (" + name + ") was scored while a page was drawing; pages should read kept scores (CAEngine.kept), the trace shows the caller",
                new IllegalStateException("evaluate while a page draws"));
    }

    /**
     * Fast buildout's ranking: the Money goal whatever the colony's goal or threat, with PROFIT's weights and no plan or
     * upgrade bonus, so only what raises income scores above zero, ranked by what it earns.
     */
    public static Result evaluateForProfit(MarketAPI m, float minStability) {
        profitOnly = true;
        try { return evaluate(m, CAData.Profile.INCOME, minStability); }
        finally { profitOnly = false; }
    }

    /**
     * Scores every building and upgrade the colony could start. 0.3.0.0 (A4): timed into CAPerf; the colony's plan-step
     * answers are kept for the length of this evaluation (CAPlan.focus); the result is kept for the day (RESULTS) and for the
     * panels (SHOWN), which read it and never evaluate (07 M6). A research what-if (noProbe) keeps nothing.
     */
    public static Result evaluate(MarketAPI m, CAData.Profile profile, float minStability) {
        long t0 = System.nanoTime();
        boolean drawn = drawing > 0;
        if (drawn) drawnEval(m);   // 0.3.0.0 (A4 fix): pages never evaluate (07 section 2 item 5); one that does is counted and logged
        int day = today(), ep = CACache.epoch();
        long empire = day >= 0 ? empireSig() : 0L;
        long sig = day >= 0 && !noProbe ? resultSig(m) : 0L;   // 0.3.0.0 (A4 fix): read before scoring, as the epoch is
        boolean focus = CAPlan.focus(m);
        try {
            Result r = score(m, profile, minStability, day, ep, empire);
            if (day >= 0 && !noProbe) keep(resultKey(m, profile, minStability, profitOnly), day, ep, sig, r);
            return r;
        } finally {
            if (focus) CAPlan.focus(null);
            CAPerf.record(drawn ? "evaluate (while a page draws)" : noProbe ? "evaluate (research what-if)" : forDisplay ? "evaluate (panel scores)"
                    : profitOnly ? "evaluate (fast buildout)" : "evaluate", System.nanoTime() - t0);
        }
    }

    /**
     * 0.3.0.0 (A4, 07 M6): the colony's evaluation for this goal from earlier today when nothing that feeds it has changed
     * since, else one now (CAAugments' slot value in the daily pass). 0.3.0.0 (A4 fix): while a panel draws it never evaluates:
     * the last one kept even if things changed since, else an empty result marked notScored. The panels themselves read kept().
     */
    public static Result cached(MarketAPI m, CAData.Profile profile, float minStability, boolean forProfit) {
        int day = today();
        String key = readKey(m, profile, minStability, forProfit);
        if (day >= 0 && !noProbe) {
            Result r = RESULTS.get(key, day, resultSig(m));
            if (r != null) return r;
        }
        if (drawing > 0) {
            Shown s = noProbe ? null : SHOWN.get(key);
            if (s != null) return s.result;
            Result none = new Result();
            none.notScored = true;
            return none;
        }
        return forProfit ? evaluateForProfit(m, minStability) : evaluate(m, profile, minStability);
    }

    /**
     * 0.3.0.0 (A4 fix): a panel's Refresh button, and the daily fill. One evaluation now for showing (no plan tracking,
     * forDisplay), kept where the panels read it. A failure is remembered (FILL_FAILED) so the daily fill doesn't try that
     * colony again every day; a success clears it.
     */
    public static Result refresh(MarketAPI m, CAData.Profile profile, float minStability, boolean forProfit) {
        boolean was = forDisplay;
        forDisplay = true;
        try {
            Result r = forProfit ? evaluateForProfit(m, minStability) : evaluate(m, profile, minStability);
            FILL_FAILED.remove(m.getId());
            return r;
        } catch (Throwable t) {
            FILL_FAILED.put(m.getId(), CATime.now());
            throw t;
        }
        finally { forDisplay = was; }
    }

    /** The 0.2.45 evaluate, with the caches (A4) in place of the per-call work. */
    static Result score(MarketAPI m, CAData.Profile profile, float minStability, int day, int ep, long empire) {
        CAPlan.newEvaluation();   // 0.3.0.0 (B2): what plan steps score is recorded per evaluation (CAPlan.noteWorth)
        CAData.Colony col = CAData.get().colony(m.getId());
        String line = CAData.settings().stationLine;
        // a station line whose first station can't be built here (a blueprint you don't have) meant no station at all (0.2.38.0)
        String starter = CASettings.stationStarter(line);
        if (starter != null && !"score".equals(line)) {
            try {
                IndustrySpecAPI ss = Global.getSettings().getIndustrySpec(starter);
                // without a working spaceport no station can be built anyway, so only judge the line once there is one
                if (ss == null || (m.hasSpaceport() && !ss.getNewPluginInstance(m).isAvailableToBuild())) line = "score";
            } catch (Throwable t) { line = "score"; }
        }
        Result r = new Result();
        if (CAHypershunt.is(m) || m.hasTag("market_no_industries_allowed")) {
            r.blocked = CAHypershunt.is(m) ? "Coronal Hypershunt: Ashes of the Domain allows no buildings here" : "This colony allows no buildings";
            return r;
        }
        if (isOutpost(m)) { r.blocked = OUTPOST_TEXT; return r; }
        calibrate(day, ep, empire);
        // the colony's own goal, not the threat-adjusted one the weights use: every other planner (terraforming, research)
        // reads the same plan, so a passing threat doesn't switch the world it's working toward (Zendia's gravity went back and forth)
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        // 0.3.0.0 (A4, 07 M2): not under research's what-if, where techs are marked researched: tracking there wrote the
        // hypothetical reachability into the save, so a research-locked step's 60-day timer was reset on every ranking.
        // 0.3.0.0 (A4 fix, 07 M6): nor for an evaluation that's only shown (forDisplay): showing a colony writes and logs nothing.
        // Advisor colonies, which the build pass never evaluates, are tracked by the daily fill instead (fill, toTrack)
        if (!noProbe && !forDisplay) {
            try { CAPlan.track(m, plan); }   // plan steps with no way to them stop holding rank and slots (0.2.38.0)
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "plan step check failed at " + m.getName(), t); }
        }
        Industry building = Misc.getCurrentlyBeingConstructed(m);
        if (building != null) r.blocked = "Already building " + building.getCurrentName();
        String full = CAGrand.full(m);   // the vanilla colony screen shows 12 buildings; a 13th couldn't be seen or managed
        if (full != null && r.blocked == null) r.blocked = full;
        Map<String, Integer> deficit = empireDeficits(day, ep, empire);
        boolean hasStation = false;
        for (Industry ind : m.getIndustries()) if (ind.getSpec().hasTag("station")) hasStation = true;
        int slotsFree = CABuild.freeSlots(m);   // upgrades into industries under way count (0.2.44.1)
        boolean bmo = CAAotD.enabled();
        Menu menu = menu(m, day, ep, empire);   // 0.3.0.0 (A4): the colony's build-menu answers for the day
        CASynergies.Ctx syn = Global.getSettings().getModManager().isModEnabled(CASynergies.MOD) ? CASynergies.context(m, deficit) : null;

        // 0.3.0.0 (A4): the specs filtered once per load (buildable()) instead of every spec and an upgrade-target loop each time
        for (IndustrySpecAPI spec : buildable()) {
            if (full != null) break;
            String id = spec.getId();
            if (CAPlan.hasOrBetter(m, id)) continue;
            if (spec.hasTag("station") && hasStation) continue;
            if (spec.hasTag("station") && !"score".equals(line) && !stationMatches(id, line)) continue;
            if (id.equals(CAWonders.SITE)) continue;   // a Grand Wonder site is only ever built for a wonder (CAWonders)
            boolean supported = CADiagnostics.industryLevel(id) == CADiagnostics.Level.SUPPORTED;
            boolean measurable = !supported && !noProbe && CADiagnostics.industryLevel(id) == CADiagnostics.Level.MEASURED && CAData.settings().measuredMode != 2;
            try {
                // 0.3.0.0 (A4): whether the build menu offers it here (mod code) is asked once a day per colony
                Industry inst = null;
                Boolean open = menu.open.get(id);
                if (open == null) {
                    inst = spec.getNewPluginInstance(m);
                    open = CATasc.isTasc(spec) ? CATasc.available(m, inst) : inst.isAvailableToBuild();
                    menu.open.put(id, open);
                }
                if (!open) continue;
                if (bmo && !menu.allows(id, m)) { if (supported || measurable) r.locked++; continue; }
                CASimulator.Effects measuredFx = null;
                if (measurable) { measuredFx = CASimulator.measure(m, id); if (measuredFx.error != null) measuredFx = null; }
                if (!supported && measuredFx == null) { r.unsupportedBuildable++; continue; }
                if ((id.equals("IndEvo_AdAssem") || id.equals("IndEvo_AdManuf") || id.equals("IndEvo_ComForge")) && !CAItems.storageHasVPC()) continue;
                if (id.equals("IndEvo_PrivatePort") && !courierPortSpot(m)) continue;
                if (inst == null) inst = spec.getNewPluginInstance(m);   // scoring still needs the building itself
                Pick p = new Pick();
                p.id = id; p.name = spec.getName(); p.verb = "Build"; p.upgrade = false;
                float[] price = menu.price(id, inst);
                p.cost = price[0]; p.days = price[1];
                p.recording = true;
                boolean worth = true;
                scoring(inst, m);
                try {
                    if (estimated(id) && !CAEstimates.has(id)) worth = estimate(m, id, p, deficit);
                    else if (measuredFx != null) { p.measured = true; CASimulator.apply(m, measuredFx, null, p, deficit); }
                    else effects(m, id, p, deficit);   // vanilla formulas, and CAEstimates' at the end
                } finally { scoring(null, null); }
                p.recording = false;
                if (!worth) continue;
                trust(spec, p, p.measured);
                CAItems.idleBonus(m, inst, p, profile);   // a colony item waiting in Autopilot Storage that would go in it
                if (!CADevelopment.adjust(m, p, inst, deficit)) continue;   // AotD management style
                CASynergies.score(syn, p);
                upkeep(m, spec, null, p);
                float cramped = CAGrand.penalty(m, p.upkeep);
                if (cramped > 0f) { p.fx[0] -= cramped; p.lines.add("Past Grand.Colonies' building limit: upkeep here rises about " + credits(cramped) + " per month"); }
                CALowProfile.score(m, inst, null, p);   // Low profile: the Pather line and the attention it draws (0.2.43.0)
                CAOddisz.score(m, inst, p);   // Sol Renewed: an industry in Sol feeds its crisis meter for good (0.2.45.0)
                if (!finish(m, p, profile, minStability, plan)) continue;
                if (inst.isIndustry() && slotsFree <= 0) r.noSlot(p);
                else r.picks.add(p);
            } catch (Throwable t) {
                CADiagnostics.quarantine(id, "build check at " + m.getName(), t);
            }
        }
        for (Industry ind : m.getIndustries()) {
            if (ind.isHidden() || ind.isBuilding() || ind.isUpgrading() || col.locks.contains(ind.getId())) continue;   // hidden: you can't see or manage it
            // the vanilla upgrade, plus (with BMO) every building whose downgrade is this one, e.g. AotD's
            // Monoculture Plots -> Farming; never past a building the Unification Center needs by exact id
            String vanillaUp = ind.getSpec().getUpgrade();
            Set<String> ups = new LinkedHashSet<String>();
            if (vanillaUp != null) ups.add(vanillaUp);
            if (bmo && !plan.exact.contains(ind.getId())) ups.addAll(CAAotD.children(ind.getId()));
            for (String up : ups) {
                boolean upMeasured = CADiagnostics.industryLevel(up) == CADiagnostics.Level.MEASURED && CAData.settings().measuredMode != 2;
                if (CADiagnostics.industryLevel(up) != CADiagnostics.Level.SUPPORTED && !upMeasured) continue;
                try {
                    IndustrySpecAPI upSpec = Global.getSettings().getIndustrySpec(up);
                    if (up.equals(vanillaUp) && !ind.canUpgrade()) continue;
                    // as the build menu: a Military Base needs a working spaceport (0.3.0.0, A4: asked once a day per colony)
                    Industry upInst = null;
                    Boolean upOpen = menu.upOpen.get(up);
                    if (upOpen == null) {
                        upInst = upSpec.getNewPluginInstance(m);
                        upOpen = upInst.isAvailableToBuild();
                        menu.upOpen.put(up, upOpen);
                    }
                    if (!upOpen) continue;
                    if (bmo && !menu.allows(up, m)) continue;
                    if (upInst == null) upInst = upSpec.getNewPluginInstance(m);
                    boolean needsSlot = upInst.isIndustry() && !ind.isIndustry() && slotsFree <= 0;
                    if (!up.equals(vanillaUp) && upSpec.hasTag("consumes") && ind.getSpecialItem() != null) continue;   // the menu would discard the item
                    Pick p = new Pick();
                    p.id = up; p.name = upSpec.getName(); p.verb = "Upgrade to"; p.upgrade = true; p.existing = ind;
                    float[] price = menu.price(up, upInst);
                    p.cost = price[0]; p.days = price[1];
                    p.recording = true;
                    scoring(upInst, m);
                    try {
                        if (CAMods.has(up)) {
                            if (!CAMods.apply(m, up, p, deficit)) continue;   // VIC treatment, Roider Union HQ
                        } else if (upMeasured) {
                            // measure both on the same probe colony so the difference is like for like
                            CASimulator.Effects to = CASimulator.measure(m, up), from = CASimulator.measure(m, ind.getId());
                            if (to.error != null || from.error != null) continue;
                            p.measured = true;
                            CASimulator.apply(m, to, from, p, deficit);
                        } else effects(m, up, p, deficit);
                    } finally { scoring(null, null); }
                    p.recording = false;
                    trust(upSpec, p, p.measured);
                    CASynergies.score(syn, p);
                    upkeep(m, upSpec, ind.getSpec(), p);
                    CALowProfile.score(m, upInst, ind, p);
                    CAOddisz.score(m, upInst, p);   // an upgrade's cost adds to Sol's meter too (0.2.45.0)
                    if (!finish(m, p, profile, minStability, plan)) continue;
                    if (needsSlot) r.noSlot(p);
                    else r.picks.add(p);
                } catch (Throwable t) {
                    CADiagnostics.quarantine(up, "upgrade check at " + m.getName(), t);
                }
            }
        }
        Collections.sort(r.picks, new Comparator<Pick>() {
            public int compare(Pick a, Pick b) { return Float.compare(b.score, a.score); }
        });
        return r;
    }

    /** What a new off-plan industry gives up when it would take a slot the plan's industries need (a mid plan step's bonus).
     *  0.3.0.0 (B2): legacyPlanner's flat reserve, and the stand-in for a plan industry that hasn't scored yet (CAPlan.reserve). */
    static final float SLOT_RESERVE = CATuning.SLOT_RESERVE;   // 0.3.0.0 (A3): the number lives in CATuning

    static boolean isIndustry(String id) {
        try {
            com.fs.starfarer.api.loading.IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(id);
            return sp != null && sp.hasTag(com.fs.starfarer.api.impl.campaign.ids.Industries.TAG_INDUSTRY);
        } catch (Throwable t) { return false; }
    }

    /** Combines effects with profile weights; false when the pick breaks a rule or isn't worth building. */
    static boolean finish(MarketAPI m, Pick p, CAData.Profile profile, float minStability, CAPlan.Plan plan) {
        if (p.fx[1] < 0 && m.getStabilityValue() + p.fx[1] < minStability) return false;
        // the endgame economy (CAEndgame): money counts 5%, military and security at least its weights, and what the building
        // makes is valued as production chains
        boolean endgame = !profitOnly && CAEndgame.at(m);
        // spending freely (CASurplus.rich): money counts a quarter, so what else a building adds decides
        float mw = profitOnly || endgame ? 1f : CASurplus.moneyWeight(m);
        if (endgame && p.fx[0] != 0f) p.lines.add("Endgame economy: its money counts 5%");
        else if (mw < 1f && p.fx[0] < 0f) p.lines.add("Surplus credits: its money counts a quarter");
        // 0.3.0.0 (B1/B2): the worth is CAValue.add's (the goal's weights, or fast buildout's or the endgame's, money at the
        // surplus's weight, the endgame's chains on the units it really adds, confidence), the number CAValue.keep runs backwards
        float value = CAValue.add(m, p, profile, true);
        // an uncertain gain must beat a known one by a margin (an estimate at 0.85 needs about 18% more to win)
        if (value > 0f && p.confidence < 1f) p.lines.add("Estimated score: counted at " + Math.round(p.confidence * 100f) + "%");   // 0.3.1.0: THE FORMAT
        // upgrading a forge that holds a nanoforge makes a new building, which pollutes a clean habitable world again
        if (!p.pollution && m != null && p.upgrade && p.existing instanceof com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry
                && CAConditions.polluting(p.existing.getSpecialItem()) && m.hasCondition("habitable") && !m.hasCondition("pollution")) p.pollution = true;
        if (p.pollution) {   // the same for every goal (War production used to mind it a sixth as much), and never started (below)
            value -= CATuning.POLLUTION_PENALTY;
            p.lines.add("Pollution: permanent after 90 days. Autopilot does not build it");
        }
        // the plan only orders buildings that are worth something on their own, except Terraforming Made Easy's structures
        // and the Unification Center: they cost more than they earn, but they're how the plan's world is reached
        // and producers a special project needs (CAProjects): nothing else makes those goods
        // and the capital's trade buildings (0.2.42.0): what they're for is a place to trade at home, not income
        boolean enabler = CAPlan.enabler(m, p.id);   // 0.3.0.0 (B2): the same list, in CAPlan so the plan's worthless rule reads it too
        if (!CAPlan.legacy()) return finishNpv(m, p, profile, plan, value, enabler, endgame);
        // ---------------- 0.3.0.0 (B2): the 0.2.45 score, with the hidden legacyPlanner switch on (for 0.3.0.x) ----------------
        float planBonus = (value > 0f || enabler) && !profitOnly ? CAPlan.bonusFor(m, plan, p.id) : 0f;
        if (endgame && planBonus > 0f && !CAEndgame.keepsPlanBonus(m, plan, p.id)) planBonus = 0f;   // a money plan's steps stand on their own
        if (planBonus > 0f) p.lines.add(0, "Plan: next step of the " + plan.name + " plan" + (value <= 0f ? ". Costs more than it earns. The plan needs it" : ""));
        // a new industry the plan doesn't ask for, when the free slots are all the plan's industries have left
        float reserve = 0f;
        if (!p.upgrade && planBonus <= 0f && !profitOnly && isIndustry(p.id) && plan != null) {
            int free = CABuild.freeSlots(m);
            int needed = endgame ? CAEndgame.openKeptIndustrySteps(m, plan) : CAPlan.openIndustrySteps(m, plan);
            if (needed > 0 && free <= needed) {
                reserve = SLOT_RESERVE;
                p.lines.add("Takes an industry slot the " + plan.name + " plan needs (" + needed + " plan industr" + (needed == 1 ? "y" : "ies") + " to come)");
            }
        }
        float followThrough = p.upgrade && value > 0f && !profitOnly ? CATuning.FOLLOW_THROUGH : 0f;
        if (followThrough > 0f) p.lines.add("Follow-through: upgrades what's already built");
        p.worth = value; p.planBonus = planBonus; p.followThrough = followThrough;
        p.planStep = planBonus > 0f; p.enabler = enabler; p.reserve = reserve; p.horizon = CATuning.PAYBACK_MONTHS;   // 0.3.0.0 (B2)
        if (p.pollution) { planBonus = 0f; followThrough = 0f; p.planBonus = 0f; p.followThrough = 0f; }
        float cw = CASurplus.costWeight(m);   // a quarter at the focus world while there are surplus credits
        p.buildCost = (p.cost / CATuning.PAYBACK_MONTHS + p.days * CATuning.WAIT_COST_PER_DAY) * cw;   // the price spread over three years, plus a little for the wait
        if (cw < 1f && p.cost > 0f) p.lines.add("Focus world with surplus credits: the price counts a quarter");
        p.score = value + planBonus + followThrough - p.buildCost - reserve;
        if (p.pollution) p.score = Math.min(p.score, -1f);   // shown, never started: worthwhile() needs a positive score
        if (p.lowProfile != null) { p.lines.add(0, p.lowProfile); p.score = Math.min(p.score, -1f); }   // the same for Low profile's Pather line
        return true;   // every option is ranked and shown; Approve and Auto only act on a positive score (worthwhile())
    }

    /**
     * 0.3.0.0 (WS-B2, research 02 sections 1, 2, 10, 12, 15 and 16): the score as net present value over one horizon, a month:
     * score = worth - (price + fee) / H - worth x days / 30 / H (CAHorizon), so worthwhile() means it pays back its price, the
     * fee and the months its value waits for construction within H (36 months, up to 120 as surplus credits pile up, the same
     * at every colony). Gone: the +15,000 follow-through for any upgrade worth more than nothing (5 to 25 year paybacks got
     * built), 10 credits a construction day (a 150-day build delays all its value 5 months), the focus world's quarter price
     * and spending freely's 5% price (H replaces both), and the fixed plan bonus for ordinary steps (60,000 swamped the
     * market model's worth). An ordinary plan step carries its own worth plus half of what the steps it opens the way to
     * would add, and a 5% tie-break once it pays; enablers and a special world's buildings keep the fixed bonus by rank,
     * except a calm colony's station while an open economic step is in front of it (B2 fix: CAPlan.stationWaitsFor).
     */
    static boolean finishNpv(MarketAPI m, Pick p, CAData.Profile profile, CAPlan.Plan plan, float value, boolean enabler, boolean endgame) {
        float h = CAHorizon.months();
        float fee = fee(p.cost);
        CAPlan.Match match = plan == null ? null : CAPlan.match(m, plan, p.id, true);
        CAPlan.Match ranked = match != null && !match.worthless ? match : null;   // a worthless step takes no rank (CAPlan.worthless)
        // a building the special world needs and the extra steps in front of the plan are ranked as in 0.2.45 (enablers whatever
        // they earn, the rest when they earn something); every other plan step is ordinary (a worthless one never is an enabler)
        boolean fixedKind = match != null && (enabler || match.step.extra || CAPlan.worldStep(plan, match.step.id) || CAPlan.worldStep(plan, p.id));
        boolean ordinary = match != null && !fixedKind;
        float down = ordinary && !profitOnly ? downstream(m, plan, p, profile, h) : 0f;
        float gross = value + down;
        float price = CAHorizon.price(p.cost, fee, gross, p.days, h);
        float own = gross - price;
        float fixed = 0f, tie = 0f;
        CAPlan.Step waitsFor = null;
        if (match != null && !profitOnly) {
            if (ranked != null && fixedKind) {
                if (enabler || value > 0f) fixed = CAPlan.rankBonus(ranked.rank);
                if (endgame && fixed > 0f && !CAEndgame.keepsPlanBonus(m, plan, p.id)) fixed = 0f;   // a money plan's steps stand on their own
                // 0.3.0.0 (B2 fix, research 01 section 10, 02 section 15): a calm colony's station waiting behind its first open
                // economic step gets only what pays its own price plus STATION_WAIT a month, not its rank's 40,000 (that still put
                // a 250,000 Orbital Station ahead of a Commerce worth 20,000 in the queue); a threatened or border colony keeps it
                if (fixed > 0f && (waitsFor = CAPlan.stationWaitsFor(m, plan, ranked.step)) != null)
                    fixed = Math.min(fixed, Math.max(0f, price - value + CAPlan.STATION_WAIT));
            } else if (ranked != null && !endgame && own > 0f && value > 0f) tie = CAHorizon.PLAN_TIE * value;   // breaks ties, never makes a bad step pay
            // 0.3.1.0: facts only (THE FORMAT)
            if (ranked == null) p.lines.add(0, "Plan: a " + plan.name + " plan step. It has not paid for itself here lately. No plan rank or slot held for it");
            else if (waitsFor != null) p.lines.add(0, "Plan: a " + plan.name + " plan step after " + waitsFor.label + ". A calm colony builds earning buildings first");
            else p.lines.add(0, "Plan: next step of the " + plan.name + " plan" + (fixedKind && value <= 0f ? ". Costs more than it earns. The plan needs it"
                    : ordinary && own <= 0f ? ". Does not pay for itself here yet" : ""));
        }
        // a new industry the plan doesn't ask for must beat the plan industry it would push out of the free slots (0.2.45: a flat
        // 30,000 whatever either was worth); in fast buildout too, for plan industries that pay on money alone (02 section 10)
        float reserve = 0f;
        if (!p.upgrade && ranked == null && plan != null && isIndustry(p.id)) {
            CAPlan.Reserve r = CAPlan.reserve(m, plan, CABuild.freeSlots(m), endgame);
            if (r.amount > 0f) {
                reserve = r.amount;
                p.lines.add("Takes an industry slot the " + plan.name + " plan needs (" + r.needed + " plan industr" + (r.needed == 1 ? "y" : "ies")
                        + " to come). Slot reserve: about " + credits(reserve) + " per month");
            }
        }
        float planBonus = down + tie + fixed;
        p.worth = value; p.planBonus = planBonus; p.followThrough = 0f; p.buildCost = price;
        p.downstream = down; p.reserve = reserve; p.fee = fee; p.horizon = h; p.npv = own * h;
        p.planStep = ranked != null; p.enabler = fixedKind || enabler;
        if (p.pollution) { planBonus = 0f; p.planBonus = 0f; }
        // only a pick that passes because surplus credits stretch the horizon says so
        float base = CAHorizon.heldBase;
        if (!Float.isNaN(base) && h > base + 0.5f && own > 0f && gross - CAHorizon.price(p.cost, fee, gross, p.days, base) <= 0f)
            p.lines.add("Payback horizon: " + Math.round(h) + " months with surplus credits (usually " + Math.round(base) + ")");
        float asStep = value + planBonus - price;   // what it scores as the plan's step, before any slot reserve
        p.score = asStep - reserve;
        if (p.pollution) { p.score = Math.min(p.score, -1f); asStep = Math.min(asStep, -1f); }   // shown, never started: worthwhile() needs a positive score
        if (p.lowProfile != null) { p.lines.add(0, p.lowProfile); p.score = Math.min(p.score, -1f); asStep = Math.min(asStep, -1f); }   // the same for Low profile's Pather line
        // what the plan step scored, so one that pays nothing stops holding rank and an industry slot (CAPlan.worthless) and one
        // that pays again gets them back; not under research's what-if, where techs are marked researched
        if (match != null && !noProbe && !CALog.whatIf()) {
            CAPlan.noteWorth(m, match.step.id, asStep, profitOnly);
            // 0.3.0.0 (B2 fix): and for the worthless step it also advances (a Heavy Industry pick ranked as its Orbital Works), or
            // that step's record never changed again: worthless for good, and its chain held no industry slot
            if (match.idle != null && match.idle != match.step) CAPlan.noteWorth(m, match.idle.id, asStep, profitOnly);
        }
        return true;   // every option is ranked and shown; Approve and Auto only act on a positive score (worthwhile())
    }

    /** 0.3.0.0 (B2): the convenience fee on a price, as the build pass charges it (CABudget.constructionFee); the standard level offline. */
    static float fee(float cost) {
        try { return CABudget.constructionFee(CAData.settings(), cost); }
        catch (Throwable t) { return Math.max(CATuning.FEE_FLOOR, CATuning.FEE_SHARE * cost); }
    }

    /** 0.3.0.0 (B2): for one evaluation, what each step a pick opens the way to would add (by colony, step, goal and horizon). */
    static final Map<String, Float> DOWNSTREAM = new HashMap<String, Float>();
    static int downstreamSeq = -1;

    /**
     * 0.3.0.0 (B2, research 02 section 1): half of what the plan steps this pick opens the way to would add here (Orbital
     * Works after Heavy Industry), each scored as the engine would score that upgrade later: its effects and extra upkeep at
     * the same weights, its own price and fee over the horizon; only ones that would pay count. A chain step that's weak by
     * itself but leads to a strong one still gets built; one that leads nowhere worth having stands on its own.
     */
    static float downstream(MarketAPI m, CAPlan.Plan plan, Pick p, CAData.Profile profile, float h) {
        if (m == null || plan == null) return 0f;
        List<CAPlan.Step> next = CAPlan.unlocked(m, plan, p.id);
        if (next.isEmpty()) return 0f;
        if (downstreamSeq != CAPlan.worthSeq) { DOWNSTREAM.clear(); downstreamSeq = CAPlan.worthSeq; }
        float sum = 0f;
        List<String> names = new ArrayList<String>();
        Map<String, Integer> deficit = null;
        for (CAPlan.Step t : next) {
            String key = m.getId() + "|" + t.id + "|" + profile + "|" + profitOnly + "|" + Math.round(h);
            Float v = DOWNSTREAM.get(key);
            if (v == null) {
                if (deficit == null) deficit = empireDeficits();
                v = stepWorth(m, t.id, CAPlan.upgradedFrom(t.id, p.id), profile, h, deficit);
                DOWNSTREAM.put(key, v);
            }
            if (v > 0f) { sum += v; names.add(t.label); }
        }
        if (sum <= 0f) return 0f;
        float d = CAHorizon.DOWNSTREAM_SHARE * sum;
        p.lines.add("Leads to " + CAContracts.join(names) + " in the plan: half of " + (names.size() == 1 ? "its" : "their") + " value counts"
                + ", about " + credits(d) + " per month");
        return d;
    }

    /** What building this here, upgraded from `from`, would add a month over the horizon (NPV / H); 0 when it can't be valued. */
    static float stepWorth(MarketAPI m, String id, String from, CAData.Profile profile, float h, Map<String, Integer> deficit) {
        try {
            IndustrySpecAPI spec = CAPlan.spec(id);
            if (spec == null) return 0f;
            Industry inst = spec.getNewPluginInstance(m);
            // 0.3.0.0 (B1 fix, second pass): on the market model, as evaluate reads a pick (effectsOf took the flat path, un-netted)
            Pick q = effectsAt(m, id, inst, from == null ? null : m.getIndustry(from), deficit);   // vanilla formulas and Autopilot's estimates only (null for anything else)
            if (q == null) return 0f;
            IndustrySpecAPI fromSpec = from == null ? null : CAPlan.spec(from);
            upkeep(m, spec, fromSpec, q);
            trust(spec, q, false);
            float worth = CAValue.add(m, q, profile);
            float cost = inst.getBuildCost(), days = inst.getBuildTime();
            return CAHorizon.npvMonthly(worth, cost, fee(cost), days, h);
        } catch (Throwable t) { return 0f; }
    }

    /** The engine's own upkeep prediction for a building here, before calibration (CACalibrate compares it with real upkeep). */
    static float baseUpkeep(MarketAPI m, IndustrySpecAPI spec) {
        // as BaseIndustry.applyIncomeAndUpkeep: the spec's upkeep (already in credits: the game multiplies the CSV value by 500
        // when it loads) times max(1, size - 2) times the colony's upkeep multiplier
        float sizeMult = (CATasc.fixedUpkeep(spec.getId()) ? 1 : Math.max(1, m.getSize() - 2)) * m.getUpkeepMult().getModifiedValue();
        return spec.getUpkeep() * sizeMult * CADevelopment.upkeepMult(m, spec);
    }

    static void upkeep(MarketAPI m, IndustrySpecAPI spec, IndustrySpecAPI replaced, Pick p) {
        float f = CACalibrate.upkeepFactor(spec.getId());
        float u = baseUpkeep(m, spec) * f;
        if (replaced != null) u -= baseUpkeep(m, replaced) * CACalibrate.upkeepFactor(replaced.getId());
        p.fx[0] -= u;
        p.upkeep = u;
        if (u > 0) p.lines.add("Upkeep about " + Misc.getDGSCredits(u) + " per month" + (Math.abs(f - 1f) > 0.05f ? " (corrected from your colonies)" : ""));
    }

    /**
     * The building being scored and where, while evaluate() scores it, so supply() can ask the building whether its output is
     * legal there (Industry.isSupplyLegal: vanilla's Heavy Industry makes hand weapons legally, a mod building may not).
     */
    static Industry supplier;
    static MarketAPI supplierAt;

    static void scoring(Industry ind, MarketAPI m) { supplier = ind; supplierAt = m; }

    /**
     * Output the colony's faction bans (drugs, hand weapons and organs for yours) and the building doesn't make legally: the
     * game gives it no export income unless the colony is a free port (CommodityMarketData: an illegal source exports
     * nothing), so it's only worth the shortage it covers at the colony itself (Carter's Freetraders' Biolab and Grey
     * Industries, 0.2.38.0). Returns the units that count, or -1 when the output is legal.
     */
    static int illegalUnits(String com, int units) {
        if (supplier == null || supplierAt == null) return -1;
        try {
            CommodityOnMarketAPI c = supplierAt.getCommodityData(com);
            if (c == null || supplier.isSupplyLegal(c)) return -1;
            return Math.min(units, Math.max(0, c.getDeficitQuantity()));
        } catch (Throwable t) { return -1; }
    }

    static void supply(Pick p, String com, int units, Map<String, Integer> deficit) {
        if (units <= 0) return;
        if (supplierAt != null) p.at = supplierAt;   // 0.3.0.0 (B1): supplyChange nets later changes to these lines at the same colony
        int local = illegalUnits(com, units);
        if (local >= 0) {
            float v = Global.getSettings().getCommoditySpec(com).getBasePrice() * unitValue * local * CATuning.SHORTAGE_FACTOR;
            p.fx[0] += v;
            if (p.recording) { Integer was = p.supplied.get(com); p.supplied.put(com, (was == null ? 0 : was) + units); p.suppliedValue += v; }
            addUnits(p.added, com, units);   // 0.3.0.0 (B1)
            p.lines.add("+" + units + " " + Global.getSettings().getCommoditySpec(com).getName() + ": illegal here without a free port. No exports"
                    + (local > 0 ? ". Covers " + local + " of this colony's own shortage, about " + Misc.getDGSCredits(v) + " per month" : ""));
            return;
        }
        float base = Global.getSettings().getCommoditySpec(com).getBasePrice();
        String name = Global.getSettings().getCommoditySpec(com).getName();
        // 0.3.0.0 (B1, research 02 section 7): the shortage counts builds under way and claims as made (CAValue.deficits), so two
        // colonies never build for one shortage
        int lack = CAValue.lack(deficit, com);
        // 0.2.44.0: what it adds to your share of the good's sector market (CAMarket), plus the shortage it covers at the flat rate;
        // the flat rate for everything only when the model is off or has no data for the good
        CAMarket.Gain g = null;
        if (supplierAt != null && CAMarket.on()) {
            try { g = CAMarket.pickGain(supplierAt, p, com, units); } catch (Throwable t) { g = null; }
        }
        if (g != null) {
            // 0.3.0.0 (B1): the shortage this line covers, never more than it adds (0.2.44.0), the shortage itself, or what the
            // colony's new supply level reaches at your colonies (CAValue.reliefUnits)
            int cover = CAValue.reliefCover(com, g, lack);
            float relief = base * unitValue * cover;
            float v = g.value + relief;
            p.fx[0] += v;
            if (relief != 0f) addValue(p.marketValue, com, relief);   // 0.3.0.0 (B1, 02 section 7): the same-pass claim check sees relief too
            if (p.recording) { Integer was = p.supplied.get(com); p.supplied.put(com, (was == null ? 0 : was) + units); p.suppliedValue += v; }
            if (g.addedAfter <= g.addedBefore) {
                p.lines.add("+" + units + " " + name + ": a larger source here already. Adds 0");
                return;
            }
            String share = Math.round(g.shareBefore * 100f) + "% -> " + Math.round(g.shareAfter * 100f) + "%";
            p.lines.add("+" + units + " " + name + ": about " + Misc.getDGSCredits(g.value) + " per month in exports (your share of the sector's "
                    + name.toLowerCase(Locale.ROOT) + " " + share + ")" + (g.capped ? ", capped at what the colony can ship" : "")
                    + (cover > 0 ? ". Covers " + cover + " of your colonies' shortage, about " + Misc.getDGSCredits(relief) + " more" : ""));
            return;
        }
        // 0.3.0.0 (B1): the shortage premium only on the units that cover the shortage (it was on every unit of any short good)
        int prior = units(p.added, com);
        int cover = CAValue.flatCover(prior, units, lack);
        p.added.put(com, prior + units);
        float v = base * unitValue * (units + (CATuning.SHORTAGE_FACTOR - 1f) * cover);
        p.fx[0] += v;
        if (p.recording) { Integer was = p.supplied.get(com); p.supplied.put(com, (was == null ? 0 : was) + units); p.suppliedValue += v; }
        p.lines.add("+" + units + " " + name + (cover > 0 ? " (covers " + cover + " of your colonies' shortage)" : "") + ": about " + Misc.getDGSCredits(v) + " per month");
    }

    /** 0.3.0.0 (B1): a pick's units of a good so far (p.added), 0 for none. */
    static int units(Map<String, Integer> m, String com) {
        Integer u = m.get(com);
        return u == null ? 0 : u;
    }

    static void addUnits(Map<String, Integer> m, String com, int units) { m.put(com, units(m, com) + units); }

    static void addValue(Map<String, Float> m, String com, float v) {
        Float was = m.get(com);
        m.put(com, (was == null ? 0f : was) + v);
    }

    /**
     * Whether a caller has to take what the colony already makes off its own line (Roider Dives, Yunru's drydocks): only
     * while scoring without the market model, which counts a colony's largest single source itself (0.2.44.0). Outside scoring
     * (calibration) a building's line is its whole output.
     */
    static boolean callerNets() { return supplierAt != null && !CAMarket.on(); }

    /**
     * A change to output already counted, e.g. from the colony's management style; delta may be negative. 0.3.0.0 (B1, research
     * 02 section 9): a line the market model valued changes the same way (netted against the colony's other sources, priced
     * by share, relief capped at the shortage), so a style that adds to a line the colony already out-makes adds nothing;
     * otherwise the flat rate, the shortage premium only on units that cover the shortage.
     */
    static void supplyChange(Pick p, String com, int delta, Map<String, Integer> deficit, String why) {
        if (delta == 0) return;
        float base = Global.getSettings().getCommoditySpec(com).getBasePrice();
        String name = Global.getSettings().getCommoditySpec(com).getName();
        int lack = CAValue.lack(deficit, com);
        CAMarket.Gain g = null;
        if (p.at != null && p.marketUnits.containsKey(com) && CAMarket.on()) {
            try { g = CAMarket.pickGain(p.at, p, com, delta); } catch (Throwable t) { g = null; }
        }
        float v;
        if (g != null) {
            int cover = CAValue.reliefCover(com, g, lack);
            float relief = base * unitValue * cover;
            v = g.value + relief;
            if (relief != 0f) addValue(p.marketValue, com, relief);
        } else {
            Integer sup = p.supplied.get(com);
            int prior = p.added.containsKey(com) ? units(p.added, com) : sup == null ? 0 : sup;
            int now = Math.max(0, prior + delta);
            int cover = CAValue.flatCover(prior, now - prior, lack);
            p.added.put(com, now);
            v = base * unitValue * ((now - prior) + (CATuning.SHORTAGE_FACTOR - 1f) * cover);
        }
        p.fx[0] += v;
        p.lines.add((delta > 0 ? "+" : "") + delta + " " + name + " from " + why + ": about " + Misc.getDGSCredits(v) + " per month"
                + (g != null && g.addedAfter == g.addedBefore ? ". No change: a larger source here" : ""));
    }

    static void demand(Pick p, String com, int units, Map<String, Integer> deficit) {
        if (units <= 0) return;
        Integer d = deficit.get(com);
        if (d == null || d <= 0) return;
        float v = Global.getSettings().getCommoditySpec(com).getBasePrice() * CATuning.SHORT_INPUT_PRICE * units;
        p.fx[0] -= v;
        p.inputPenalty += v;   // 0.3.0.0 (B1 fix): a build is charged it, keeping one isn't (CAValue.keepDetail)
        p.lines.add("Needs " + Global.getSettings().getCommoditySpec(com).getName() + ". Your colonies already lack it");
    }

    static void effects(MarketAPI m, String id, Pick p, Map<String, Integer> deficit) {
        p.wholeLines = true;   // 0.3.0.0 (B1 fix): the formulas below give the building's whole output (an upgrade replaces the old line)
        int s = m.getSize();
        boolean habitable = m.hasCondition("habitable");
        if (id.equals("farming") || id.equals("aquaculture")) {
            deposits(m, "farming", p, deficit);
            demand(p, "heavy_machinery", s - 3, deficit);
        } else if (id.equals("mining")) {
            deposits(m, "mining", p, deficit);
            CAMods.miningExtras(m, p, deficit);   // Unknown Skies / What We Left Behind conditions that add to Mining directly
            demand(p, "heavy_machinery", s - 3, deficit);
            demand(p, "drugs", s, deficit);
        } else if (id.equals("techmining")) {
            int r = m.hasCondition("ruins_vast") ? 4 : m.hasCondition("ruins_extensive") ? 3 : m.hasCondition("ruins_widespread") ? 2 : 1;
            float left = m.getMemoryWithoutUpdate().contains("$core_techMiningMult") ? m.getMemoryWithoutUpdate().getFloat("$core_techMiningMult") : 1f;   // ruins deplete 5% a month
            // Slightly Better Tech-Mining: far richer finds, by colony size (0.2.38.0)
            float v = (CAMods.sbtm() ? CAMods.sbtmMonthly(m, left) : 4000f * r * left) * CADevelopment.techMiningMult(m);   // x1.7 at an AotD Archeo-Site
            p.fx[0] += v;
            p.lines.add("Salvage from the ruins: about " + Misc.getDGSCredits(v) + " per month in value");
        } else if (id.equals("refining")) {
            supply(p, "metals", s, deficit); supply(p, "rare_metals", s - 2, deficit);
            demand(p, "ore", s + 2, deficit); demand(p, "rare_ore", s, deficit);
        } else if (id.equals("lightindustry")) {
            supply(p, "domestic_goods", s, deficit); supply(p, "luxury_goods", s - 2, deficit);
            demand(p, "organics", s, deficit);
        } else if (id.equals("heavyindustry")) {
            supply(p, "heavy_machinery", s - 2, deficit); supply(p, "supplies", s - 2, deficit);
            supply(p, "hand_weapons", s - 2, deficit); supply(p, "ships", s - 2, deficit);
            demand(p, "metals", s, deficit); demand(p, "rare_metals", s - 2, deficit);
            parts(m, p, s - 2, 0f);   // 0.3.0.9: Domain-grade Parts from its supplies (0.98.5a)
            // a Heavy Industry pollutes only with a polluting item in it, and Autopilot never puts one in on a habitable
            // world (CAItems.wouldPollute, 0.2.36.2), so building one here doesn't pollute
        } else if (id.equals("orbitalworks")) {
            p.fx[5] += 0.2f; p.fx[4] += 0.2f; p.lines.add("+20% ship quality and more ship output");
            parts(m, p, s - 2, ORBITAL_WORKS_QUALITY);   // 0.3.0.9: more Domain-grade Parts from the better production quality (0.98.5a)
        } else if (id.equals("fuelprod")) {
            supply(p, "fuel", s - 2, deficit); demand(p, "volatiles", s, deficit);
            CACrisis.diktat(m, s - 2, p);   // the Sindrian Diktat counts fuel output from 4
        } else if (id.equals("spaceport")) {
            p.fx[2] += 1.5f; p.fx[6] += 3f; p.lines.add("Removes the -100% no-spaceport penalty and adds +50% accessibility");
        } else if (id.equals("megaport")) {
            p.fx[2] += 0.3f; p.fx[6] += 1f; p.lines.add("+30% accessibility over a Spaceport");
        } else if (id.equals("waystation")) {
            p.fx[2] += 0.1f; p.fx[6] += 0.5f; p.lines.add("+10% accessibility; stockpiles fuel and supplies");
        } else if (id.equals("patrolhq")) {
            p.fx[3] += 0.1f; p.fx[4] += 0.3f; p.lines.add("Patrol fleets; +10% ground defense"); CACrisis.military(id, p);
        } else if (id.equals("militarybase")) {
            p.fx[3] += 0.1f; p.fx[4] += 0.4f; p.lines.add("Larger patrols, marines and crew; ground defense +20%"); CACrisis.military(id, p);
        } else if (id.equals("highcommand")) {
            p.fx[3] += 0.1f; p.fx[4] += 0.5f; p.lines.add("Strongest patrols; ground defense +30%"); CACrisis.military(id, p);
        } else if (id.equals("grounddefenses")) {
            p.fx[3] += 1f; p.lines.add("Ground defense x2");
        } else if (id.equals("heavybatteries")) {
            p.fx[3] += 1f; p.lines.add("Ground defense x3 (from x2)");
        } else if (id.equals("planetaryshield")) {
            if (CAMods.psac()) CAMods.shield(m, p);   // Planetary Shield: Access Control's version
            else { p.fx[3] += 2f; p.lines.add("Ground defense x3; blocks meteor impacts"); }
        } else if (id.startsWith("orbitalstation")) {
            p.fx[3] += 0.5f; p.fx[4] += 0.3f; p.lines.add("Station fleet; ground defense x1.5");
        } else if (id.startsWith("battlestation")) {
            p.fx[3] += 0.5f; p.fx[4] += 0.3f; p.lines.add("Stronger station; ground defense x2");
        } else if (id.startsWith("starfortress")) {
            p.fx[3] += 1f; p.fx[4] += 0.5f; p.lines.add("Strongest station; ground defense x3");
        } else if (id.equals("cryosanctum")) {
            supply(p, "organs", s, deficit);
        } else if (id.equals("cryorevival")) {
            p.fx[6] += 4f; p.lines.add("Large growth bonus from the in-system cryosleeper");
        } else if (CAEstimates.has(id)) {
            float was = p.fx[0];
            CAEstimates.apply(m, id, p, deficit);
            // 0.3.0.0 (B1 fix): an IndEvo producer's flat income is its output; keep() nets it the same way (CAValue.keepDetail)
            if (OUTPUT_ESTIMATES.contains(id)) {
                float est = p.fx[0] - was;
                // 0.3.0.0 (B1 fix, second pass): the Salvage Yards' estimate netted as the VPC buildings' is (CAValue.vpcShare):
                // only the share above the ship parts the colony makes already, which is what keep() reads off its line
                if (id.equals("IndEvo_ScrapYard")) {
                    float share = CAValue.netShare(m, CAValue.YARD_GOOD, m.getSize());   // 1 while keep() reads it, or with the model off
                    if (share < 1f) {
                        p.fx[0] -= est * (1f - share);
                        est *= share;
                        p.lines.add(Math.round(share * 100f) + "% of it above the ship parts this colony makes (estimated)");
                    }
                }
                p.outputEstimate += est;
            }
        }
    }

    /** 0.3.0.9: Orbital Works' production quality over Heavy Industry (HeavyIndustry.ORBITAL_WORKS_QUALITY_BONUS). */
    static final float ORBITAL_WORKS_QUALITY = 0.2f;

    /**
     * Pure (0.3.0.9, JTValueCheck): Domain-grade Parts a colony's Local Resources gains a month on 0.98.5a, by the game's rule
     * (BaseSubmarketPlugin.getPartsGeneratedAsFractionOfSupplies with LocalResourcesSubmarketPlugin's stockpile): supplies
     * production x the econ unit x stockpileMultProduction, times (fBase + (quality + 0.3) x fProd x min(1.5, production / 10))
     * x mult (ext_partsForSaleFraction in survival mode, else 1). 0 with no supplies production.
     */
    static float partsPerMonth(int suppliesProd, float econUnit, float stockMult, float quality, float fBase, float fProd, float mult) {
        if (suppliesProd <= 0) return 0f;
        float share = (fBase + (quality + 0.3f) * fProd * Math.min(1.5f, suppliesProd / 10f)) * mult;
        return Math.max(0f, suppliesProd * econUnit * stockMult * share);
    }

    /**
     * 0.3.0.9: what a Heavy Industry or Orbital Works adds in Domain-grade Parts (0.98.5a; nothing when the game has no "parts"
     * commodity or parts-based repairs are off): the parts the colony's market gains a month with supplies production at
     * suppliesAfter and production quality qualityAdd higher, less what it gains now (partsPerMonth), at the game's sell
     * price for a part (base price x its sell price mult: 250 of 10,000). Parts are not traded in the economy, so they earn
     * no export income; this sell price is the value the game puts on a part a market takes.
     */
    static void parts(MarketAPI m, Pick p, int suppliesAfter, float qualityAdd) {
        try {
            if (!CASurvival.partsRepairs()) return;
            if (p.id != null && m.getIndustry(p.id) != null) return;   // already built: its supplies and quality are in what the colony has now
            com.fs.starfarer.api.campaign.econ.CommoditySpecAPI parts = Global.getSettings().getCommoditySpec("parts");
            com.fs.starfarer.api.campaign.econ.CommoditySpecAPI sup = Global.getSettings().getCommoditySpec("supplies");
            if (parts == null || sup == null) return;
            CommodityOnMarketAPI c = m.getCommodityData("supplies");
            int now = c == null ? 0 : Math.max(0, c.getMaxSupply());
            float q = m.getStats().getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.PRODUCTION_QUALITY_MOD).computeEffective(0f);
            float fBase = Global.getSettings().getFloat("partsForSaleBaseSuppliesFraction"), fProd = Global.getSettings().getFloat("partsForSaleBaseProductionFraction");
            float stock = Global.getSettings().getFloat("stockpileMultProduction");
            float mult = CASurvival.extended() ? Global.getSettings().getFloat("ext_partsForSaleFraction") : 1f;
            float units = partsPerMonth(Math.max(now, suppliesAfter), sup.getEconUnit(), stock, q + qualityAdd, fBase, fProd, mult)
                    - partsPerMonth(now, sup.getEconUnit(), stock, q, fBase, fProd, mult);
            if (!(units >= 0.5f)) return;
            p.partsAdded = units;   // 0.3.2.0: the parts floor (CAFloors) counts them while the fleet's repair need is open
            // parts earn no income: counted at PARTS_VALUE_SHARE of their sell price, a tie-breaker for the d-mod repairs they allow
            float v = units * parts.getBasePrice() * parts.getSellPriceMult() * CATuning.PARTS_VALUE_SHARE;
            if (!(v > 0f)) return;
            p.fx[0] += v;
            p.lines.add("Domain-grade Parts: about " + Math.round(units) + " more a month in its market, counted as " + Misc.getDGSCredits(v)
                    + " a month (no income)");
        } catch (Throwable ignored) { }
    }

    /**
     * What a deposit industry makes here, by the tables the game's own ResourceDepositsCondition uses (so deposits mods
     * register there count too), falling back to the vanilla table above.
     */
    static void deposits(MarketAPI m, String industry, Pick p, Map<String, Integer> deficit) {
        for (MarketConditionAPI c : m.getConditions()) {
            String com = ResourceDepositsCondition.COMMODITY.get(c.getId());
            Integer mod = ResourceDepositsCondition.MODIFIER.get(c.getId());
            if (com == null || mod == null) { com = DEPOSIT.get(c.getId()); mod = DEPOSIT_MOD.get(c.getId()); }
            if (com == null || mod == null || Global.getSettings().getCommoditySpec(com) == null) continue;
            String by = ResourceDepositsCondition.INDUSTRY.get(com);
            if (by == null) by = com.equals("food") ? "farming" : "mining";
            if (!by.equals(industry)) continue;
            Integer base = ResourceDepositsCondition.BASE_MODIFIER.get(com);
            if (base == null) base = BASE_MOD.containsKey(com) ? BASE_MOD.get(com) : 0;
            int size = ResourceDepositsCondition.BASE_ZERO.contains(com) ? 0 : m.getSize();
            supply(p, com, size + base + mod, deficit);
        }
    }

    /** One Courier Port serves every contract, so only the best-connected colony gets one, and only if none exists yet. */
    static boolean courierPortSpot(MarketAPI m) {
        MarketAPI best = null; float bestAcc = -99f;
        for (MarketAPI o : Misc.getPlayerMarkets(false)) {
            if (o.hasIndustry("IndEvo_PrivatePort")) return false;
            float a = o.getAccessibilityMod().computeEffective(0f);
            if (a > bestAcc) { bestAcc = a; best = o; }
        }
        return best == m;
    }

    static boolean stationMatches(String id, String line) {
        String starter = CASettings.stationStarter(line);
        return starter == null || id.equals(starter);
    }

    /** Unmet demand per commodity across all player colonies, plus what held trade contracts (AotD) are short of. */
    public static Map<String, Integer> empireDeficits() {
        int day = today();
        return empireDeficits(day, CACache.epoch(), day >= 0 ? empireSig() : 0L);
    }

    /**
     * 0.3.0.0 (A4, 07 M7): the colonies' part is worked out once a day while no colony changes (it was every commodity of every
     * colony, from 12 callers, several times a day); held contracts' shortfalls are still asked every time. Every caller
     * gets its own map. 0.3.0.0 (B1, research 02 section 7): pending-aware: a building under construction (claimed by
     * Autopilot or started by hand) counts as made, at the supply level it will reach, so two colonies never build for one
     * shortage. The per-colony day snapshot behind it lives in CAValue (memo "shortages"); claims are read fresh.
     */
    static Map<String, Integer> empireDeficits(int day, int ep, long empire) {
        return CAValue.deficits(day, ep, empire);
    }

    /** Unmet demand per commodity across all player colonies, outposts and Substance.Abuse drinks left out (0.2.45, no builds under way counted; CAValue's fallback). */
    static Map<String, Integer> colonyDeficits() {
        Map<String, Integer> out = new HashMap<String, Integer>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (isOutpost(m)) continue;   // its own economy: your colonies can't supply it anyway
            for (CommodityOnMarketAPI c : m.getAllCommodities()) {
                int d = c.getDeficitQuantity();
                if (d > 0 && !CABrewery.isDrink(c.getId())) {   // Substance.Abuse drinks: other colonies' demand has no penalty
                    Integer cur = out.get(c.getId());
                    out.put(c.getId(), (cur == null ? 0 : cur) + d);
                }
            }
        }
        return out;
    }

    // ---------------- 0.3.0.0 (A4): the engine's caches (research 07 section 2, M6, M7) ----------------

    /** The game day from the sector clock (counted as CAPlan.lockedSteps counts it); -1 with no sector, which turns caching off. */
    public static int today() {
        try {
            if (Global.getSector() == null || Global.getSector().getClock() == null) return -1;
            com.fs.starfarer.api.campaign.CampaignClockAPI c = Global.getSector().getClock();
            return c.getCycle() * 400 + c.getMonth() * 32 + c.getDay();
        } catch (Throwable t) { return -1; }
    }

    /**
     * The buildings evaluate() looks at, filtered once per load instead of on every evaluation: no item parents, no
     * Population, nothing the build menu never shows, nothing only reached by upgrading (a vanilla upgrade target, or with
     * Building Menu Overhaul anything with a downgrade). Specs don't change during a game. Cleared by resetCaches().
     */
    static List<IndustrySpecAPI> buildableSpecs = null;

    static List<IndustrySpecAPI> buildable() {
        List<IndustrySpecAPI> l = buildableSpecs;
        if (l != null) return l;
        List<IndustrySpecAPI> all = Global.getSettings().getAllIndustrySpecs();
        Set<String> upgradeTargets = new HashSet<String>();
        for (IndustrySpecAPI s : all) if (s.getUpgrade() != null) upgradeTargets.add(s.getUpgrade());
        l = new ArrayList<IndustrySpecAPI>();
        for (IndustrySpecAPI s : all) {
            if (s.hasTag("parent_item") || s.hasTag("population") || s.hasTag("do_not_show_in_build_dialog")
                    || upgradeTargets.contains(s.getId()) || CAAotD.upgradeOnly(s)) continue;
            l.add(s);
        }
        buildableSpecs = l;
        return l;
    }

    /**
     * One colony's build-menu answers for the day (07 section 2 item 3), the expensive part of an evaluation since each runs
     * mod code: whether the menu offers a building (Industry.isAvailableToBuild, or TASC's own check), Building Menu
     * Overhaul's hidden set and requirement check (through method handles), and the price and build time. Filled as
     * evaluations ask; kept per colony for the day, the epoch and the empire's signature (another colony's buildings can
     * change what's offered here). Never kept under a research what-if, where techs are marked researched.
     */
    static final class Menu {
        Set<String> hidden;
        final Map<String, Boolean> open = new HashMap<String, Boolean>(), upOpen = new HashMap<String, Boolean>(),
                allowed = new HashMap<String, Boolean>();
        final Map<String, float[]> price = new HashMap<String, float[]>();

        boolean allows(String id, MarketAPI m) {
            Boolean b = allowed.get(id);
            if (b == null) {
                if (hidden == null) hidden = CAAotD.hidden(m);
                b = CAAotD.allows(id, m, hidden);
                allowed.put(id, b);
            }
            return b;
        }

        float[] price(String id, Industry inst) {
            float[] p = price.get(id);
            if (p == null) { p = new float[]{inst.getBuildCost(), inst.getBuildTime()}; price.put(id, p); }
            return p;
        }
    }

    static final CACache.Memo<Menu> MENUS = new CACache.Memo<Menu>("build menus", 200);

    static Menu menu(MarketAPI m, int day, int ep, long empire) {
        if (day < 0 || noProbe) return new Menu();
        Menu menu = MENUS.get(m.getId(), day, empire);
        return menu != null ? menu : MENUS.put(m.getId(), day, ep, empire, new Menu());
    }

    /** The day's evaluations, by colony, goal, ranking (fast buildout's money-only or the goal's) and stability floor. */
    static final CACache.Memo<Result> RESULTS = new CACache.Memo<Result>("colony results", 400);

    static String resultKey(MarketAPI m, CAData.Profile profile, float minStability, boolean forProfit) {
        return m.getId() + "|" + profile + "|" + (forProfit ? "money" : "goal") + "|" + minStability;
    }

    /** The key a panel reads: fast buildout's ranking is kept under the Money goal (evaluateForProfit), whatever the colony's goal. */
    static String readKey(MarketAPI m, CAData.Profile profile, float minStability, boolean forProfit) {
        return resultKey(m, forProfit ? CAData.Profile.INCOME : profile, minStability, forProfit);
    }

    /**
     * 0.3.0.0 (A4 fix, 07 M6): what a kept evaluation was read from. Everything about its own colony (colonySig), the
     * buildings, items and conditions of the others (econSig), the quarantined buildings and the two settings evaluate reads.
     * Another colony's goal, mode, fast buildout, locks or stability are left out: with them, one Next goal or lock click
     * anywhere threw away every colony's kept result. If reading fails, a value that never repeats, so nothing is used.
     */
    static long resultSig(MarketAPI m) {
        try {
            CASettings s = CAData.settings();
            return resultSig(m, Misc.getPlayerMarkets(false), CAData.get(), CADiagnostics.count(CADiagnostics.Level.QUARANTINED), s.stationLine, s.measuredMode);
        } catch (Throwable t) { return sigFailed(t); }
    }

    static long resultSig(MarketAPI m, List<MarketAPI> markets, CAData d, int quarantined, String stationLine, int measuredMode) {
        long own = 0L, others = 0L;
        int n = 0;
        boolean found = false;
        for (MarketAPI o : markets) {
            n++;
            if (o == m || o.getId().equals(m.getId())) { own = colonySig(o, d); found = true; }
            else others += econSig(o);
        }
        if (!found) own = colonySig(m, d);
        long h = CACache.mix(own, CACache.mix(n, others));
        h = CACache.mix(h, quarantined);
        h = CACache.mix(h, CACache.spread(stationLine));
        return CACache.mix(h, measuredMode);
    }

    /**
     * Every player colony's signature, summed so their order doesn't matter: a change at any colony misses (the build menus,
     * shortages and unit value). If reading one fails, a value that never repeats, so nothing is kept and everything is
     * worked out as in 0.2.45.
     */
    static long empireSig() {
        try {
            long sum = 0L;
            int n = 0;
            CAData d = CAData.get();
            for (MarketAPI m : Misc.getPlayerMarkets(false)) { sum += colonySig(m, d); n++; }
            return CACache.mix(n, sum);
        } catch (Throwable t) { return sigFailed(t); }
    }

    static long sigFailed(Throwable t) {
        if (!sigFailed) { sigFailed = true; CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony signature failed; caches off until it works", t); }
        return CACache.mix(0x7F4A7C15L, ++sigMisses);
    }

    static boolean sigFailed = false;
    static long sigMisses = 0L;

    /**
     * 0.3.0.0 (A4 fix): what an evaluation reads from another colony: size, slots, administrator, free port, its conditions,
     * and each building with its state (building, upgrading, disrupted, hidden, improved), item and AI core. Not its stability
     * or what Autopilot is told to do there.
     */
    static long econSig(MarketAPI m) {
        long h = CACache.mix(CACache.spread(m.getId()), m.getSize());
        h = CACache.mix(h, Misc.getMaxIndustries(m));
        h = CACache.mix(h, CACache.spread(m.getAdmin() == null ? null : m.getAdmin().getId()));
        h = CACache.mix(h, m.isFreePort() ? 1 : 0);
        long conds = 0L;
        for (MarketConditionAPI c : m.getConditions()) conds += CACache.spread(c.getId());
        h = CACache.mix(h, conds);
        long inds = 0L;
        for (Industry ind : m.getIndustries()) {
            int flags = (ind.isBuilding() ? 1 : 0) | (ind.isUpgrading() ? 2 : 0) | (ind.isDisrupted() ? 4 : 0) | (ind.isHidden() ? 8 : 0)
                    | (ind.isImproved() ? 16 : 0);
            long k = CACache.mix(CACache.spread(ind.getId()), flags);
            k = CACache.mix(k, CACache.spread(ind.getSpecialItem() == null ? null : ind.getSpecialItem().getId()));
            inds += CACache.mix(k, CACache.spread(ind.getAICoreId()));
        }
        return CACache.mix(h, inds);
    }

    /** What an evaluation of this colony reads from it: econSig, its whole stability, and Autopilot's goal, mode, fast buildout and locks for it. */
    static long colonySig(MarketAPI m) { return colonySig(m, CAData.get()); }

    static long colonySig(MarketAPI m, CAData d) {
        long h = CACache.mix(econSig(m), (long) Math.floor(m.getStabilityValue()));
        CAData.Colony c = d.colony(m.getId());
        h = CACache.mix(h, (c.profile == null ? 0 : c.profile.ordinal() + 1) * 16 + (c.mode == null ? 0 : c.mode.ordinal() + 1) * 2 + (c.buildout ? 1 : 0));
        return CACache.mix(h, CACache.unordered(c.locks));
    }

    // ---------------- 0.3.0.0 (A4 fix, 07 M6): what the panels show ----------------

    /**
     * The last evaluation made for each key, whatever day or epoch, for the panels. RESULTS keeps only what's still current
     * today; this keeps the last one so a panel can show it as "scored N days ago" instead of evaluating while it draws.
     * Session only, emptied on load and rescan; the oldest goes when it's full.
     */
    static final class Shown {
        final Result result; final int day, epoch; final long sig; final float at;
        Shown(Result result, int day, int epoch, long sig, float at) { this.result = result; this.day = day; this.epoch = epoch; this.sig = sig; this.at = at; }
    }

    static final Map<String, Shown> SHOWN = new HashMap<String, Shown>();
    static final int SHOWN_MAX = 400;

    /** An evaluation kept for the day (RESULTS) and for the panels (SHOWN). */
    static void keep(String key, int day, int ep, long sig, Result r) {
        RESULTS.put(key, day, ep, sig, r);
        if (r == null || key == null) return;
        if (SHOWN.size() >= SHOWN_MAX && !SHOWN.containsKey(key)) {
            String oldest = null; float at = Float.MAX_VALUE;
            for (Map.Entry<String, Shown> e : SHOWN.entrySet()) if (e.getValue().at < at) { at = e.getValue().at; oldest = e.getKey(); }
            SHOWN.remove(oldest);
        }
        SHOWN.put(key, new Shown(r, day, ep, sig, CATime.now()));
    }

    /** A kept evaluation as a panel shows it: the result, whether nothing that feeds it changed since (current), and its age in days. */
    public static final class Kept {
        public final Result result;
        public final boolean current;
        public final int days;
        Kept(Result result, boolean current, int days) { this.result = result; this.current = current; this.days = days; }
    }

    /**
     * What a panel shows for a colony and goal (fast buildout's money ranking with forProfit): the last evaluation kept, or
     * null when there's none yet. Never evaluates; the daily pass fills these (fill) and the panels' Refresh buttons
     * evaluate on a click (refresh, refreshAll).
     */
    public static Kept kept(MarketAPI m, CAData.Profile profile, float minStability, boolean forProfit) {
        Shown s = SHOWN.get(readKey(m, profile, minStability, forProfit));
        if (s == null) return null;
        int day = today();
        return view(s, day, CACache.epoch(), day >= 0 ? resultSig(m) : 0L, CATime.now());
    }

    /**
     * The rule kept() uses: current on the same day and epoch from the same signature (as every memo); the age in whole days
     * from the clock, at least 1 once the date has changed.
     */
    static Kept view(Shown s, int day, int epoch, long sig, float now) {
        if (s == null) return null;
        int days = Math.max(0, (int) Math.floor(now - s.at));
        if (day >= 0 && day != s.day) days = Math.max(1, days);
        return new Kept(s.result, CACache.fresh(s.day, s.epoch, s.sig, day, epoch, sig), days);
    }

    /** Out-of-date panel results the daily pass scores again each day, oldest first, beside every colony with none kept yet. */
    static final int FILL_STALE = 3;

    /**
     * 0.3.0.0 (A4 fix, 07 M6): the daily pass's last step (CAAuto.runPass). The panels never evaluate, so every colony that
     * isn't Off gets its Colonies-page result kept here (its own goal, or fast buildout's money ranking): each one with none
     * kept yet, and of those whose kept one is out of date (a new day, a build anywhere, a change at the colony) the
     * FILL_STALE oldest, so the pass doesn't add an evaluation per colony per day. The build pass keeps what it evaluates
     * too. The scores here are for showing (refresh, no plan tracking), but first the daily fill tracks the plan steps of
     * the colonies the build pass never plans for (Advisor, toTrack), once a day: in 0.2.45 drawing the Colonies page did that,
     * so their dead steps aged, came back and stopped holding rank and slots in the recommendations. A colony whose score
     * failed waits FILL_RETRY_DAYS before the daily fill tries it again, and failures log through CAStep.warn (one stack
     * trace, then a short line weekly), not a trace per colony per day.
     */
    public static void fill(CAData d) { fill(d, FILL_STALE, true); }

    /** The Colonies page's Refresh button: every colony not Off whose kept result is out of date or missing, now. No plan tracking. */
    public static int refreshAll(CAData d) { return fill(d, Integer.MAX_VALUE, false); }

    /** 0.3.0.0 (A4 fix): colonies whose panel score failed this session, and when (CATime days). Emptied on load (resetCaches). */
    static final Map<String, Float> FILL_FAILED = new HashMap<String, Float>();
    /** Days the daily fill waits before scoring a failed colony again (Refresh always tries); CAStep.warn's weekly repeat too. */
    static final float FILL_RETRY_DAYS = 7f;
    /** The day the daily fill last tracked plan steps, so it's once a day even if the pass runs twice. */
    static int trackedDay = -1;

    /** A colony whose panel score failed less than FILL_RETRY_DAYS ago: the daily fill leaves it for now. */
    static boolean failedRecently(String id, float now) {
        Float at = FILL_FAILED.get(id);
        return at != null && !CATime.due(at, now, FILL_RETRY_DAYS);
    }

    /** Whether the panel shows a colony whose score failed (the Colonies page and the colony panel say so instead of "not scored yet"). */
    public static boolean scoreFailed(MarketAPI m) { return m != null && FILL_FAILED.containsKey(m.getId()); }

    /**
     * 0.3.0.0 (A4 fix): the colonies whose plan steps the daily fill tracks: the ones the build pass never evaluates (not
     * Approve or Auto, CAAuto.buildPass) and that aren't Off (0.2.45 never scored those either). Today that's Advisor.
     */
    static List<MarketAPI> toTrack(List<MarketAPI> ms, CAData d) {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : ms) {
            CAData.Mode mode = d.colony(m.getId()).mode;
            if (mode != CAData.Mode.OFF && mode != CAData.Mode.APPROVE && mode != CAData.Mode.AUTO) out.add(m);
        }
        return out;
    }

    static int fill(CAData d, int maxStale, boolean daily) {
        int day = today();
        if (day < 0) return 0;
        float minStability = d.settings.minStability, now = CATime.now();
        List<MarketAPI> shown = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if (c.mode == CAData.Mode.OFF || isOutpost(m) || CAHypershunt.is(m)) continue;   // the Colonies page shows no pick for these
            shown.add(m);
        }
        // 0.3.0.0 (A4 fix): before scoring, so a step that went dead or came back today is ranked that way (a bump makes the
        // kept results out of date, which the loop below then sees)
        if (daily && day != trackedDay) {
            trackedDay = day;
            for (MarketAPI m : toTrack(shown, d)) {
                try { CAPlan.track(m, CAPlan.build(m, d.colony(m.getId()).profile)); }   // the plan evaluate tracked in 0.2.45 (the colony's own goal)
                catch (Throwable t) { CAStep.warn("panel scores", "plan step check failed at " + m.getName(), t); }
            }
        }
        List<MarketAPI> ms = new ArrayList<MarketAPI>();
        List<Float> at = new ArrayList<Float>();
        List<Boolean> current = new ArrayList<Boolean>();
        for (MarketAPI m : shown) {
            if (daily && failedRecently(m.getId(), now)) continue;   // failed lately: tried again in a week, or on Refresh
            CAData.Colony c = d.colony(m.getId());
            Shown s = SHOWN.get(readKey(m, c.profile, minStability, buildoutRanking(c)));
            ms.add(m);
            at.add(s == null ? null : s.at);
            current.add(s != null && view(s, day, CACache.epoch(), resultSig(m), now).current);
        }
        int n = 0;
        for (int i : fillOrder(at, current, maxStale)) {
            MarketAPI m = ms.get(i);
            CAData.Colony c = d.colony(m.getId());
            try { refresh(m, c.profile, minStability, buildoutRanking(c)); n++; }
            catch (Throwable t) { CAStep.warn("panel scores", "panel score failed at " + m.getName(), t); }   // refresh marked it in FILL_FAILED
        }
        return n;
    }

    /** Fast buildout ranks by money only (CABuildout), and only in Auto: what the Colonies page and the colony panel read. */
    static boolean buildoutRanking(CAData.Colony c) { return c.buildout && c.mode == CAData.Mode.AUTO; }

    /**
     * Which colonies fill scores, as indexes: every one with nothing kept (null), in list order, then up to maxStale of those
     * kept but not current, oldest first. Current ones never.
     */
    static List<Integer> fillOrder(List<Float> keptAt, List<Boolean> current, int maxStale) {
        List<Integer> out = new ArrayList<Integer>(), stale = new ArrayList<Integer>();
        for (int i = 0; i < keptAt.size(); i++) {
            if (keptAt.get(i) == null) out.add(i);
            else if (!Boolean.TRUE.equals(current.get(i))) stale.add(i);
        }
        final List<Float> when = keptAt;
        Collections.sort(stale, new Comparator<Integer>() { public int compare(Integer a, Integer b) { return Float.compare(when.get(a), when.get(b)); } });
        for (int i = 0; i < stale.size() && i < maxStale; i++) out.add(stale.get(i));
        return out;
    }

    /** Every cache of the engine and its plans emptied: on load (CAReset) and on a rescan (CASimulator.reset). */
    public static void resetCaches() {
        buildableSpecs = null;
        sigFailed = false; sigMisses = 0L;
        SHOWN.clear();   // 0.3.0.0 (A4 fix): the panels' results too (drawing and forDisplay always end in a finally)
        FILL_FAILED.clear(); trackedDay = -1; drawnEvals = 0;   // 0.3.0.0 (A4 fix): a load tries every colony again and tracks the same day
        CAValue.reset();   // 0.3.0.0 (B1): the shortages snapshot moved to CAValue (its memo goes with CACache.reset below)
        CAHorizon.reset(); DOWNSTREAM.clear(); downstreamSeq = -1;   // 0.3.0.0 (B2): the day's horizon and the per-evaluation downstream values
        CACache.reset();
        CAPlan.resetCaches();
    }

    /** Credit-equivalent value per month, for display. */
    public static String valueText(float v) { return (v >= 0 ? "+" : "") + Misc.getDGSCredits(v) + "/month"; }

    /** Worth building now: it pays for itself within about three years, with the goal's weights. Approve and Auto need this.
     *  0.3.0.0 (B2): within the horizon (CAHorizon: 36 months, up to 120 with surplus credits), price, fee and build time counted. */
    public static boolean worthwhile(Pick p) { return p != null && p.score > 0f; }

    /**
     * A Nexerelin player outpost: a size-2 forward base Nexerelin builds on a station, with a Spaceport and Waystation and a
     * flat upkeep it charges itself, in an economy group of its own, so nothing built there can export or earn. It's for
     * resupply and storage, not income, so Autopilot leaves it alone: nothing is built, upgraded, placed or switched there,
     * and its shortages aren't counted as your colonies'. Read from Nexerelin's own market flag and entity tag (literal ids,
     * so this works without Nexerelin).
     */
    public static boolean isOutpost(MarketAPI m) {
        if (m == null) return false;
        if (m.getMemoryWithoutUpdate().contains("$nex_playerOutpost")) return true;
        return m.getPrimaryEntity() != null && m.getPrimaryEntity().hasTag("nex_playerOutpost");
    }

    public static final String OUTPOST_TEXT = "Nexerelin outpost: resupply and storage base outside your trade economy. Autopilot does not build or change anything here";


    public static String signed(float v) { return (v >= 0 ? "+" : "-") + Misc.getDGSCredits(Math.abs(v)); }
}
