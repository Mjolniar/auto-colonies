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
    public static float unitValue = 100f;

    static void calibrate() {
        float income = 0f, base = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            for (CommodityOnMarketAPI c : m.getAllCommodities()) {
                int ex = c.getExportIncome();
                if (ex <= 0 || c.getMaxSupply() <= 0) continue;
                income += ex;
                base += c.getCommodity().getBasePrice() * c.getMaxSupply();
            }
        }
        unitValue = base > 0f ? Math.max(50f, Math.min(3000f, income / base)) : 100f;
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

    /** Rough credit value of one supply unit per month, per credit of the commodity's base price. */
    static final float UNIT_VALUE = 100f;

    /** Profile weights: credit-equivalent per month for one unit of each effect. */
    static final float[][] WEIGHTS = {
            //            credits stab   access  defense fleet  quality growth
            /* BALANCED */ {1.0f, 3000f, 20000f, 3000f, 4000f, 10000f, 1500f},
            /* INCOME   */ {1.6f, 2500f, 30000f, 800f, 800f, 2000f, 1000f},
            /* SECURITY */ {0.5f, 4000f, 8000f, 9000f, 9000f, 4000f, 500f},
            /* WAR      */ {0.8f, 2500f, 10000f, 4000f, 10000f, 30000f, 500f},
            /* GROWTH   */ {0.6f, 3500f, 30000f, 1500f, 1000f, 1000f, 6000f},
            /* SELF     */ {1.0f, 3000f, 20000f, 3000f, 4000f, 10000f, 1500f}};
    /** Fast buildout's weights: the Money goal's, with ground defense, fleets and ship quality worth nothing, since they earn nothing and add upkeep. */
    static final float[] PROFIT = {1.6f, 2500f, 30000f, 0f, 0f, 0f, 1000f};

    public static class Pick {
        public String id, name, verb;
        public boolean upgrade;
        public Industry existing;
        public float cost, days, score, upkeep;
        /** How score is made up (finish()): the effects valued with the goal's weights, the plan bonus, and the build cost
         *  and time spread over three years. score = worth + planBonus + followThrough - buildCost. */
        public float worth, planBonus, followThrough, buildCost;
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
    }

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

        void noSlot(Pick p) { noSlot++; if (bestNoSlot == null || p.score > bestNoSlot.score) bestNoSlot = p; }
    }

    /** Set while CAResearch values techs: no probe-colony measurements under a what-if research state. */
    static boolean noProbe = false;
    /** Set while evaluateForProfit runs. */
    static boolean profitOnly = false;

    /**
     * Fast buildout's ranking: the Money goal whatever the colony's goal or threat, with PROFIT's weights and no plan or
     * upgrade bonus, so only what raises income scores above zero, ranked by what it earns.
     */
    public static Result evaluateForProfit(MarketAPI m, float minStability) {
        profitOnly = true;
        try { return evaluate(m, CAData.Profile.INCOME, minStability); }
        finally { profitOnly = false; }
    }

    public static Result evaluate(MarketAPI m, CAData.Profile profile, float minStability) {
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
        calibrate();
        // the colony's own goal, not the threat-adjusted one the weights use: every other planner (terraforming, research)
        // reads the same plan, so a passing threat doesn't switch the world it's working toward (Zendia's gravity went back and forth)
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        try { CAPlan.track(m, plan); }   // plan steps with no way to them stop holding rank and slots (0.2.38.0)
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "plan step check failed at " + m.getName(), t); }
        Industry building = Misc.getCurrentlyBeingConstructed(m);
        if (building != null) r.blocked = "Already building " + building.getCurrentName();
        String full = CAGrand.full(m);   // the vanilla colony screen shows 12 buildings; a 13th couldn't be seen or managed
        if (full != null && r.blocked == null) r.blocked = full;
        Map<String, Integer> deficit = empireDeficits();
        Set<String> upgradeTargets = new HashSet<String>();
        for (IndustrySpecAPI s : Global.getSettings().getAllIndustrySpecs()) if (s.getUpgrade() != null) upgradeTargets.add(s.getUpgrade());
        boolean hasStation = false;
        for (Industry ind : m.getIndustries()) if (ind.getSpec().hasTag("station")) hasStation = true;
        int slotsFree = CABuild.freeSlots(m);   // upgrades into industries under way count (0.2.44.1)
        boolean bmo = CAAotD.enabled();
        Set<String> hidden = CAAotD.hidden(m);
        CASynergies.Ctx syn = Global.getSettings().getModManager().isModEnabled(CASynergies.MOD) ? CASynergies.context(m, deficit) : null;

        for (IndustrySpecAPI spec : Global.getSettings().getAllIndustrySpecs()) {
            if (full != null) break;
            String id = spec.getId();
            if (CAPlan.hasOrBetter(m, id) || spec.hasTag("parent_item") || spec.hasTag("population")
                    || spec.hasTag("do_not_show_in_build_dialog") || upgradeTargets.contains(id) || CAAotD.upgradeOnly(spec)) continue;
            if (spec.hasTag("station") && hasStation) continue;
            if (spec.hasTag("station") && !"score".equals(line) && !stationMatches(id, line)) continue;
            if (id.equals(CAWonders.SITE)) continue;   // a Grand Wonder site is only ever built for a wonder (CAWonders)
            if (CAPrune.goneRecently(m, id)) continue;   // 0.2.45.2 (from 0.3.0): Autopilot took it down here within the year
            boolean supported = CADiagnostics.industryLevel(id) == CADiagnostics.Level.SUPPORTED;
            boolean measurable = !supported && !noProbe && CADiagnostics.industryLevel(id) == CADiagnostics.Level.MEASURED && CAData.settings().measuredMode != 2;
            try {
                Industry inst = spec.getNewPluginInstance(m);
                if (!(CATasc.isTasc(spec) ? CATasc.available(m, inst) : inst.isAvailableToBuild())) continue;
                if (bmo && !CAAotD.allows(id, m, hidden)) { if (supported || measurable) r.locked++; continue; }
                CASimulator.Effects measuredFx = null;
                if (measurable) { measuredFx = CASimulator.measure(m, id); if (measuredFx.error != null) measuredFx = null; }
                if (!supported && measuredFx == null) { r.unsupportedBuildable++; continue; }
                if ((id.equals("IndEvo_AdAssem") || id.equals("IndEvo_AdManuf") || id.equals("IndEvo_ComForge")) && !CAItems.storageHasVPC()) continue;
                if (id.equals("IndEvo_PrivatePort") && !courierPortSpot(m)) continue;
                Pick p = new Pick();
                p.id = id; p.name = spec.getName(); p.verb = "Build"; p.upgrade = false;
                p.cost = inst.getBuildCost(); p.days = inst.getBuildTime();
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
                if (cramped > 0f) { p.fx[0] -= cramped; p.lines.add("Past Grand.Colonies' building limit: all upkeep here rises, about " + credits(cramped) + " per month"); }
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
                    Industry upInst = upSpec.getNewPluginInstance(m);
                    if ((up.equals(vanillaUp) && !ind.canUpgrade()) || !upInst.isAvailableToBuild()) continue;   // as the build menu: a Military Base needs a working spaceport
                    if (bmo && !CAAotD.allows(up, m, hidden)) continue;
                    boolean needsSlot = upInst.isIndustry() && !ind.isIndustry() && slotsFree <= 0;
                    if (!up.equals(vanillaUp) && upSpec.hasTag("consumes") && ind.getSpecialItem() != null) continue;   // the menu would discard the item
                    Pick p = new Pick();
                    p.id = up; p.name = upSpec.getName(); p.verb = "Upgrade to"; p.upgrade = true; p.existing = ind;
                    p.cost = upInst.getBuildCost(); p.days = upInst.getBuildTime();
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

    /** What a new off-plan industry gives up when it would take a slot the plan's industries need (a mid plan step's bonus). */
    static final float SLOT_RESERVE = 30000f;

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
        float[] w = profitOnly ? PROFIT : endgame ? CAEndgame.weights(WEIGHTS[profile.ordinal()]) : WEIGHTS[profile.ordinal()];
        // spending freely (CASurplus.rich): money counts a quarter, so what else a building adds decides
        float mw = profitOnly || endgame ? 1f : CASurplus.moneyWeight(m);
        if (endgame && p.fx[0] != 0f) p.lines.add("Endgame economy: its money counts 5%");
        else if (mw < 1f && p.fx[0] < 0f) p.lines.add("Surplus credits: its money counts a quarter");
        float value = 0f;
        for (int i = 0; i < 7; i++) value += w[i] * p.fx[i] * (i == 0 ? mw : 1f);
        if (endgame) value += CAEndgame.chainValue(m, p, false);
        // an uncertain gain must beat a known one by a margin (an estimate at 0.85 needs about 18% more to win)
        if (value > 0f && p.confidence < 1f) {
            value *= p.confidence;
            p.lines.add("Estimate rather than a game formula: counted at " + Math.round(p.confidence * 100f) + "%");
        }
        // upgrading a forge that holds a nanoforge makes a new building, which pollutes a clean habitable world again
        if (!p.pollution && m != null && p.upgrade && p.existing instanceof com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry
                && CAConditions.polluting(p.existing.getSpecialItem()) && m.hasCondition("habitable") && !m.hasCondition("pollution")) p.pollution = true;
        if (p.pollution) {   // the same for every goal (War production used to mind it a sixth as much), and never started (below)
            value -= 30000f;
            p.lines.add("Pollutes this habitable world (permanent after 90 days): Autopilot never starts this; build it yourself if you want it");
        }
        // the plan only orders buildings that are worth something on their own, except Terraforming Made Easy's structures
        // and the Unification Center: they cost more than they earn, but they're how the plan's world is reached
        // and producers a special project needs (CAProjects): nothing else makes those goods
        // and the capital's trade buildings (0.2.42.0): what they're for is a place to trade at home, not income
        boolean enabler = p.id.startsWith("tme_") || CAProjects.isStep(m, p.id) || CASurplus.isRichStep(m, p.id) || CAResearch.isFacilityStep(m, p.id)
                || CACapital.isStep(m, p.id) || CAStations.isStep(m, p.id)
                || (CAWonders.BFC.equals(p.id) && !CAWonders.stepsFor(m).isEmpty());
        float planBonus = (value > 0f || enabler) && !profitOnly ? CAPlan.bonusFor(m, plan, p.id) : 0f;
        if (endgame && planBonus > 0f && !CAEndgame.keepsPlanBonus(m, plan, p.id)) planBonus = 0f;   // a money plan's steps stand on their own
        if (planBonus > 0f) p.lines.add(0, "Next step of this colony's " + plan.name + " plan" + (value <= 0f ? " (costs more than it earns; the plan needs it)" : ""));
        // a new industry the plan doesn't ask for, when the free slots are all the plan's industries have left
        float reserve = 0f;
        if (!p.upgrade && planBonus <= 0f && !profitOnly && isIndustry(p.id) && plan != null) {
            int free = CABuild.freeSlots(m);
            int needed = endgame ? CAEndgame.openKeptIndustrySteps(m, plan) : CAPlan.openIndustrySteps(m, plan);
            if (needed > 0 && free <= needed) {
                reserve = SLOT_RESERVE;
                p.lines.add("Takes an industry slot this colony's " + plan.name + " plan needs (" + needed + " plan industr" + (needed == 1 ? "y" : "ies") + " to come)");
            }
        }
        // 0.2.45.2 (from 0.3.0): no flat +15k "follow-through" for upgrades; an upgrade stands on what it's worth (Heavy Batteries paid back in 25 years)
        float followThrough = 0f;
        if (followThrough > 0f) p.lines.add("Follow-through: upgrades what's already built");
        p.worth = value; p.planBonus = planBonus; p.followThrough = followThrough;
        if (p.pollution) { planBonus = 0f; followThrough = 0f; p.planBonus = 0f; p.followThrough = 0f; }
        float cw = CASurplus.costWeight(m);   // a quarter at the focus world while there are surplus credits
        p.buildCost = (p.cost / 36f + p.days * 10f) * cw;   // the price spread over three years, plus a little for the wait
        if (cw < 1f && p.cost > 0f) p.lines.add("Focus world with surplus credits: the price counts a quarter");
        p.score = value + planBonus + followThrough - p.buildCost - reserve;
        if (p.pollution) p.score = Math.min(p.score, -1f);   // shown, never started: worthwhile() needs a positive score
        if (p.lowProfile != null) { p.lines.add(0, p.lowProfile); p.score = Math.min(p.score, -1f); }   // the same for Low profile's Pather line
        return true;   // every option is ranked and shown; Approve and Auto only act on a positive score (worthwhile())
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
        int local = illegalUnits(com, units);
        if (local >= 0) {
            float v = Global.getSettings().getCommoditySpec(com).getBasePrice() * unitValue * local * 1.5f;
            p.fx[0] += v;
            if (p.recording) { Integer was = p.supplied.get(com); p.supplied.put(com, (was == null ? 0 : was) + units); p.suppliedValue += v; }
            p.lines.add("+" + units + " " + Global.getSettings().getCommoditySpec(com).getName() + ": illegal here without a free port, so it exports nothing"
                    + (local > 0 ? "; covers " + local + " of this colony's own shortage, about " + Misc.getDGSCredits(v) + " per month" : ""));
            return;
        }
        float base = Global.getSettings().getCommoditySpec(com).getBasePrice();
        String name = Global.getSettings().getCommoditySpec(com).getName();
        Integer d = deficit.get(com);
        // 0.2.44.0: what it adds to your share of the good's sector market (CAMarket), plus the shortage it covers at the flat rate;
        // the flat rate for everything only when the model is off or has no data for the good
        CAMarket.Gain g = null;
        if (supplierAt != null && CAMarket.on()) {
            try { g = CAMarket.pickGain(supplierAt, p, com, units); } catch (Throwable t) { g = null; }
        }
        if (g != null) {
            int lack = d != null && d > 0 ? d : 0;
            int cover = Math.min(g.addedAfter, lack) - Math.min(g.addedBefore, lack);
            float relief = base * unitValue * cover;
            float v = g.value + relief;
            p.fx[0] += v;
            if (p.recording) { Integer was = p.supplied.get(com); p.supplied.put(com, (was == null ? 0 : was) + units); p.suppliedValue += v; }
            if (g.addedAfter <= g.addedBefore) {
                p.lines.add("+" + units + " " + name + ": already made here in larger amounts (a colony counts only its biggest source), adds nothing");
                return;
            }
            String share = Math.round(g.shareBefore * 100f) + "% -> " + Math.round(g.shareAfter * 100f) + "%";
            p.lines.add("+" + units + " " + name + ": about " + Misc.getDGSCredits(g.value) + " per month in exports (your share of the sector's "
                    + name.toLowerCase(Locale.ROOT) + " " + share + ")" + (g.capped ? ", past what the colony can ship" : "")
                    + (cover > 0 ? "; covers " + cover + " of your colonies' shortage, about " + Misc.getDGSCredits(relief) + " more" : ""));
            return;
        }
        float mult = (d != null && d > 0) ? 1.5f : 1f;
        float v = base * unitValue * units * mult;
        p.fx[0] += v;
        if (p.recording) { Integer was = p.supplied.get(com); p.supplied.put(com, (was == null ? 0 : was) + units); p.suppliedValue += v; }
        p.lines.add("+" + units + " " + name + (mult > 1f ? " (your colonies are short)" : "") + ": about " + Misc.getDGSCredits(v) + " per month");
    }

    /**
     * Whether a caller has to take what the colony already makes off its own line (Roider Dives, Yunru's drydocks): only
     * while scoring without the market model, which counts a colony's largest single source itself (0.2.44.0). Outside scoring
     * (calibration) a building's line is its whole output.
     */
    static boolean callerNets() { return supplierAt != null && !CAMarket.on(); }

    /** A change to output already counted, e.g. from the colony's management style; delta may be negative. */
    static void supplyChange(Pick p, String com, int delta, Map<String, Integer> deficit, String why) {
        if (delta == 0) return;
        float base = Global.getSettings().getCommoditySpec(com).getBasePrice();
        Integer d = deficit.get(com);
        float v = base * unitValue * delta * ((d != null && d > 0) ? 1.5f : 1f);
        p.fx[0] += v;
        p.lines.add((delta > 0 ? "+" : "") + delta + " " + Global.getSettings().getCommoditySpec(com).getName() + " from " + why
                + ": about " + Misc.getDGSCredits(v) + " per month");
    }

    static void demand(Pick p, String com, int units, Map<String, Integer> deficit) {
        if (units <= 0) return;
        Integer d = deficit.get(com);
        if (d == null || d <= 0) return;
        float v = Global.getSettings().getCommoditySpec(com).getBasePrice() * 30f * units;
        p.fx[0] -= v;
        p.lines.add("Needs " + Global.getSettings().getCommoditySpec(com).getName() + ", which your colonies already lack");
    }

    static void effects(MarketAPI m, String id, Pick p, Map<String, Integer> deficit) {
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
            // a Heavy Industry pollutes only with a polluting item in it, and Autopilot never puts one in on a habitable
            // world (CAItems.wouldPollute, 0.2.36.2), so building one here doesn't pollute
        } else if (id.equals("orbitalworks")) {
            p.fx[5] += 0.2f; p.fx[4] += 0.2f; p.lines.add("+20% ship quality and more ship output");
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
            CAEstimates.apply(m, id, p, deficit);
        }
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
        if (Global.getSettings().getModManager().isModEnabled(CAContracts.MOD)) {
            for (Map.Entry<String, Integer> e : CAContracts.shortfalls().entrySet()) if (!out.containsKey(e.getKey())) out.put(e.getKey(), 1);
        }
        return out;
    }

    /** Credit-equivalent value per month, for display. */
    public static String valueText(float v) { return (v >= 0 ? "+" : "") + Misc.getDGSCredits(v) + "/month"; }

    /** Worth building now: it pays for itself within about three years, with the goal's weights. Approve and Auto need this. */
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

    public static final String OUTPOST_TEXT = "Nexerelin outpost: a forward base for resupply and storage, outside your trade economy, so Autopilot doesn't build or change anything here";


    public static String signed(float v) { return (v >= 0 ? "+" : "-") + Misc.getDGSCredits(Math.abs(v)); }
}
