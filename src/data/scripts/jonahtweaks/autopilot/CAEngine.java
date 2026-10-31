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
        return VANILLA.contains(id) || CAEstimates.has(id) || CATasc.has(id) || CABrewery.has(id) || CAMods.has(id);
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

    public static class Pick {
        public String id, name, verb;
        public boolean upgrade;
        public Industry existing;
        public float cost, days, score, upkeep;
        public float[] fx = new float[7];
        public List<String> lines = new ArrayList<String>();
        public boolean pollution;
        public boolean measured;   // a mod building scored from probe-colony measurements (CASimulator)
        /** The building's own output lines, recorded while its effects are read, so a management style can adjust them. */
        public Map<String, Integer> supplied = new LinkedHashMap<String, Integer>();
        boolean recording;
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

    public static Result evaluate(MarketAPI m, CAData.Profile profile, float minStability) {
        CAData.Colony col = CAData.get().colony(m.getId());
        String line = CAData.settings().stationLine;
        Result r = new Result();
        if (CAHypershunt.is(m) || m.hasTag("market_no_industries_allowed")) {
            r.blocked = CAHypershunt.is(m) ? "Coronal Hypershunt: Ashes of the Domain allows no buildings here" : "This colony allows no buildings";
            return r;
        }
        calibrate();
        CAPlan.Plan plan = CAPlan.build(m, profile);
        Industry building = Misc.getCurrentlyBeingConstructed(m);
        if (building != null) r.blocked = "Already building " + building.getCurrentName();
        String full = CAGrand.full(m);   // the vanilla colony screen shows 12 buildings; a 13th couldn't be seen or managed
        if (full != null && r.blocked == null) r.blocked = full;
        Map<String, Integer> deficit = empireDeficits();
        Set<String> upgradeTargets = new HashSet<String>();
        for (IndustrySpecAPI s : Global.getSettings().getAllIndustrySpecs()) if (s.getUpgrade() != null) upgradeTargets.add(s.getUpgrade());
        boolean hasStation = false;
        for (Industry ind : m.getIndustries()) if (ind.getSpec().hasTag("station")) hasStation = true;
        int slotsFree = Misc.getMaxIndustries(m) - Misc.getNumIndustries(m);
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
                if (CATasc.has(id)) worth = CATasc.apply(m, id, p, deficit);   // TASC: scored here, never test-built
                else if (CABrewery.has(id)) worth = CABrewery.apply(m, p, deficit);   // Substance.Abuse: only with a recipe on hand
                else if (CAMods.has(id)) worth = CAMods.apply(m, id, p, deficit);   // VIC treatment, Roider Dives
                else if (measuredFx != null) { p.measured = true; CASimulator.apply(m, measuredFx, null, p, deficit); }
                else effects(m, id, p, deficit);
                p.recording = false;
                if (!worth) continue;
                if (!CADevelopment.adjust(m, p, inst, deficit)) continue;   // AotD management style
                CASynergies.score(syn, p);
                upkeep(m, spec, null, p);
                float cramped = CAGrand.penalty(m, p.upkeep);
                if (cramped > 0f) { p.fx[0] -= cramped; p.lines.add("Past Grand.Colonies' building limit: all upkeep here rises, about " + credits(cramped) + " per month"); }
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
                    if (up.equals(vanillaUp) ? !ind.canUpgrade() : !upInst.isAvailableToBuild()) continue;
                    if (bmo && !CAAotD.allows(up, m, hidden)) continue;
                    boolean needsSlot = upInst.isIndustry() && !ind.isIndustry() && slotsFree <= 0;
                    if (!up.equals(vanillaUp) && upSpec.hasTag("consumes") && ind.getSpecialItem() != null) continue;   // the menu would discard the item
                    Pick p = new Pick();
                    p.id = up; p.name = upSpec.getName(); p.verb = "Upgrade to"; p.upgrade = true; p.existing = ind;
                    p.cost = upInst.getBuildCost(); p.days = upInst.getBuildTime();
                    if (CAMods.has(up)) {
                        if (!CAMods.apply(m, up, p, deficit)) continue;   // VIC treatment, Roider Union HQ
                    } else if (upMeasured) {
                        // measure both on the same probe colony so the difference is like for like
                        CASimulator.Effects to = CASimulator.measure(m, up), from = CASimulator.measure(m, ind.getId());
                        if (to.error != null || from.error != null) continue;
                        p.measured = true;
                        CASimulator.apply(m, to, from, p, deficit);
                    } else effects(m, up, p, deficit);
                    CASynergies.score(syn, p);
                    upkeep(m, upSpec, ind.getSpec(), p);
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

    /** Combines effects with profile weights; false when the pick breaks a rule or isn't worth building. */
    static boolean finish(MarketAPI m, Pick p, CAData.Profile profile, float minStability, CAPlan.Plan plan) {
        if (p.fx[1] < 0 && m.getStabilityValue() + p.fx[1] < minStability) return false;
        float[] w = WEIGHTS[profile.ordinal()];
        float value = 0f;
        for (int i = 0; i < 7; i++) value += w[i] * p.fx[i];
        if (p.pollution) {
            value -= profile == CAData.Profile.WAR ? 5000f : 30000f;
            p.lines.add("Pollutes this habitable world (permanent after 90 days)");
        }
        float planBonus = CAPlan.bonusFor(m, plan, p.id);
        if (planBonus > 0f) p.lines.add(0, "Next step of this colony's " + plan.name + " plan");
        float followThrough = p.upgrade ? 15000f : 0f;
        if (p.upgrade) p.lines.add("Follow-through: upgrades what's already built");
        p.score = value + planBonus + followThrough - p.cost / 36f - p.days * 10f;
        return true;   // always rank every option: the best available pick is always offered
    }

    static void upkeep(MarketAPI m, IndustrySpecAPI spec, IndustrySpecAPI replaced, Pick p) {
        float sizeMult = (CATasc.fixedUpkeep(spec.getId()) ? 1 : Math.max(1, m.getSize() - 2)) * 500f * m.getUpkeepMult().getModifiedValue();
        float u = spec.getUpkeep() * sizeMult * CADevelopment.upkeepMult(m, spec);
        if (replaced != null) u -= replaced.getUpkeep() * sizeMult * CADevelopment.upkeepMult(m, replaced);
        p.fx[0] -= u;
        p.upkeep = u;
        if (u > 0) p.lines.add("Upkeep about " + Misc.getDGSCredits(u) + " per month");
    }

    static void supply(Pick p, String com, int units, Map<String, Integer> deficit) {
        if (units <= 0) return;
        float base = Global.getSettings().getCommoditySpec(com).getBasePrice();
        Integer d = deficit.get(com);
        float mult = (d != null && d > 0) ? 1.5f : 1f;
        float v = base * unitValue * units * mult;
        p.fx[0] += v;
        if (p.recording) { Integer was = p.supplied.get(com); p.supplied.put(com, (was == null ? 0 : was) + units); }
        p.lines.add("+" + units + " " + Global.getSettings().getCommoditySpec(com).getName()
                + (mult > 1f ? " (your colonies are short)" : "") + ": about " + Misc.getDGSCredits(v) + " per month");
    }

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
            float v = 4000f * r * left * CADevelopment.techMiningMult(m);   // x1.7 at an AotD Archeo-Site
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
            if (habitable && !m.hasCondition("pollution")) p.pollution = true;
        } else if (id.equals("orbitalworks")) {
            p.fx[5] += 0.2f; p.fx[4] += 0.2f; p.lines.add("+20% ship quality and more ship output");
        } else if (id.equals("fuelprod")) {
            supply(p, "fuel", s - 2, deficit); demand(p, "volatiles", s, deficit);
        } else if (id.equals("spaceport")) {
            p.fx[2] += 1.5f; p.fx[6] += 3f; p.lines.add("Removes the -100% no-spaceport penalty and adds +50% accessibility");
        } else if (id.equals("megaport")) {
            p.fx[2] += 0.3f; p.fx[6] += 1f; p.lines.add("+30% accessibility over a Spaceport");
        } else if (id.equals("waystation")) {
            p.fx[2] += 0.1f; p.fx[6] += 0.5f; p.lines.add("+10% accessibility; stockpiles fuel and supplies");
        } else if (id.equals("patrolhq")) {
            p.fx[3] += 0.1f; p.fx[4] += 0.3f; p.lines.add("Patrol fleets; +10% ground defense");
        } else if (id.equals("militarybase")) {
            p.fx[3] += 0.1f; p.fx[4] += 0.4f; p.lines.add("Larger patrols, marines and crew; ground defense +20%");
        } else if (id.equals("highcommand")) {
            p.fx[3] += 0.1f; p.fx[4] += 0.5f; p.lines.add("Strongest patrols; ground defense +30%");
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
        if ("low".equals(line)) return id.equals("orbitalstation");
        if ("mid".equals(line)) return id.equals("orbitalstation_mid");
        if ("high".equals(line)) return id.equals("orbitalstation_high");
        return true;
    }

    /** Unmet demand per commodity across all player colonies, plus what held trade contracts (AotD) are short of. */
    public static Map<String, Integer> empireDeficits() {
        Map<String, Integer> out = new HashMap<String, Integer>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
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
}
