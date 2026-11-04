package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.InstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.PerseanLeagueMembership;
import com.fs.starfarer.api.impl.campaign.intel.events.HegemonyAICoresActivityCause;
import com.fs.starfarer.api.impl.campaign.intel.events.HegemonyHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Places items from Autopilot Storage; places, upgrades, moves and pulls back AI cores by what they add against Hegemony
 * inspection risk; and places, moves and hires administrators where they earn their salary. Auto-mode colonies only.
 */
public class CAItems {
    static final float STANDARD_MOVE_FEE = 10000f;

    static final Map<String, Float> ITEM_VALUE = new HashMap<String, Float>();
    static {
        ITEM_VALUE.put("pristine_nanoforge", 100f); ITEM_VALUE.put("corrupted_nanoforge", 40f);
        ITEM_VALUE.put("synchrotron", 60f); ITEM_VALUE.put("catalytic_core", 50f);
        ITEM_VALUE.put("mantle_bore", 50f); ITEM_VALUE.put("plasma_dynamo", 50f);
        ITEM_VALUE.put("soil_nanites", 40f); ITEM_VALUE.put("biofactory_embryo", 40f);
        ITEM_VALUE.put("fullerene_spool", 40f);   // the Orbital Fusion Lamp is valued by what it changes (lampValue)
        ITEM_VALUE.put("coronal_portal", 80f); ITEM_VALUE.put("cryoarithmetic_engine", 70f);
        ITEM_VALUE.put("drone_replicator", 40f); ITEM_VALUE.put("dealmaker_holosuite", 60f);
        // Ashes of the Domain's Tenebrian items and Turing Engine (effects from VoK 5.0.7's code)
        ITEM_VALUE.put("aotd_shrouded_nanoforge", 160f);       // +6 output, +80% quality, no pollution
        ITEM_VALUE.put("aotd_shrouded_synchrotron", 120f);     // +6 fuel (+4 compound), no atmosphere needed
        ITEM_VALUE.put("aotd_atmospheric_driver", 70f);        // +3 food, suppresses extreme weather
        ITEM_VALUE.put("aotd_shrouded_catalytic_core", 50f);   // +3 metals, no atmosphere needed
        ITEM_VALUE.put("turing_engine", 110f);                 // +6 advanced components
    }
    /** Items this placer never installs: Ashes of the Domain blanks the Coronal Hypershunt Tap's buildings (it builds receivers),
     *  the AquaOrb has no effect so the game can't install it, and the Hyperdimensional Processor is for research. */
    static final Set<String> NEVER = new HashSet<String>(Arrays.asList("coronal_portal", "aotd_aquaorb", "omega_processor",
            "planetkiller", "jdp_lobster_eggs"));   // JaydeePiracy: a story item, and +1 lobster for +8 Pather interest

    /** True when Autopilot Storage holds an Industrial Evolution VPC chip. */
    public static boolean storageHasVPC() {
        for (CargoStackAPI stack : CAData.get().storage().getStacksCopy())
            if (stack.isSpecialStack() && stack.getSpecialDataIfSpecial() != null && stack.getSpecialDataIfSpecial().getId().startsWith("IndEvo_vpc_")) return true;
        return false;
    }

    public static List<MarketAPI> autoMarkets() {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (CAData.get().colony(m.getId()).mode == CAData.Mode.AUTO && !CAEngine.isOutpost(m)) out.add(m);
        return out;
    }

    public static void ensureStorageTabs() {
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            try { if (!m.hasSubmarket(CAStoragePlugin.ID)) m.addSubmarket(CAStoragePlugin.ID); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "could not add Autopilot Storage at " + m.getName(), t); }
        }
    }

    static boolean canPay(CASettings s, float fee) {
        return fee <= 0f || (fee <= CABudget.available(s) && CABuild.credits() >= fee);
    }

    /**
     * Value of an item with no entry above: any mod's item that registers an effect in the game's own item table (the
     * same table the install menu checks). An output booster is worth what its extra output suggests next to the vanilla
     * ones (Catalytic Core: +3 for 50); anything else gets a small value, so it's still used rather than left in storage.
     */
    static float modItemValue(String id) {
        InstallableItemEffect e = ItemEffectsRepo.ITEM_EFFECTS.get(id);
        if (e == null) return 0f;
        if (e instanceof com.fs.starfarer.api.impl.campaign.econ.impl.BoostIndustryInstallableItemEffect) {
            int up = ((com.fs.starfarer.api.impl.campaign.econ.impl.BoostIndustryInstallableItemEffect) e).getSupplyIncrease();
            if (up > 0) return 15f * up + 5f;
        }
        return 20f;
    }

    static float itemScore(String id, MarketAPI m, CAData.Profile p) {
        if (id.equals(LAMP)) {
            float lv = lampValue(m, p, false);
            if (lv <= 0f && m.hasCondition("hot") && planNeedsHeat(m, p)) lv = 1000f;   // its Extreme Heat opens a plan step
            return lv > 0f ? lv / 1000f : 0f;
        }
        Float v = ITEM_VALUE.get(id);
        if (v == null) v = modItemValue(id);
        if (v <= 0f) return 0f;
        float mult = 1f;
        boolean war = p == CAData.Profile.WAR, sec = p == CAData.Profile.SECURITY, inc = p == CAData.Profile.INCOME;
        if (war && (id.endsWith("nanoforge") || id.equals("cryoarithmetic_engine"))) mult = 2f;
        if (sec && (id.equals("drone_replicator") || id.equals("cryoarithmetic_engine"))) mult = 1.5f;
        if (inc && (id.equals("dealmaker_holosuite") || id.equals("fullerene_spool"))) mult = 1.5f;
        return v * mult * m.getSize();
    }

    /** One placement per day at most, so each move is visible in the log. Only among the given Auto colonies (those not under siege). */
    public static void run(List<MarketAPI> markets) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (markets.isEmpty()) return;
        if (pullLamp(d, markets)) return;
        if (placeItem(d, s, markets)) return;
        if (!s.disableStabilizers && s.allowRemovals && convertFuel(d, s, markets)) return;   // replaces a building, so only with Allow removals
        if (s.useAICores && placeCore(d, s, markets)) return;
        if (s.adminOptimal && (assignAdmin(d, markets) || moveAdmin(markets))) return;
        if (s.adminHiring) hireAdmin(d, s, markets);
        else if (adminsNeeded(d) > 0 && CALog.once("admin:need:off:" + adminsNeeded(d)))
            CALog.add("Administrators needed: " + limitText(adminsNeeded(d)) + ". Administrator hiring is off, so hire one yourself, or turn on "
                    + "Hire administrators under Administrators and ambassadors", true);
    }

    static boolean placeItem(CAData d, CASettings s, List<MarketAPI> markets) {
        CargoAPI st = d.storage();
        for (CargoStackAPI stack : st.getStacksCopy()) {
            if (!stack.isSpecialStack() || stack.getSize() < 1f) continue;
            SpecialItemSpecAPI spec = stack.getSpecialItemSpecIfSpecial();
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (spec == null || data == null || NEVER.contains(spec.getId())) continue;
            if (CADiagnostics.itemLevel(spec.getId()) == CADiagnostics.Level.QUARANTINED) continue;   // its code failed before
            InstallableItemEffect effect = ItemEffectsRepo.ITEM_EFFECTS.get(spec.getId());   // any mod's item, if it registers an effect
            String params = spec.getParams();
            if (effect == null || params == null) continue;
            Set<String> targets = new HashSet<String>();
            for (String p : params.split(",")) targets.add(p.trim());
            int minSize = CABrewery.minSize(data);   // Substance.Abuse's Recipe Book makes nothing below its recipe count
            if (minSize < 0) continue;
            Industry best = null; float bestScore = 0f; MarketAPI bestM = null;
            for (MarketAPI m : markets) {
                if (CAHypershunt.is(m) || m.getSize() < minSize) continue;   // hypershunt: no buildings allowed, so an extra slot or output is wasted
                if (!CAMods.itemFits(spec.getId(), m)) continue;   // VIC's nanites: vanilla farmland and genetech only
                CAData.Colony c = d.colony(m.getId());
                for (Industry ind : m.getIndustries()) {
                    if (!targets.contains(ind.getId()) || ind.getSpecialItem() != null || !ind.isFunctional() || ind.isHidden()) continue;
                    if (c.locks.contains(ind.getId())) continue;
                    try {
                        if (!effect.getUnmetRequirements(ind).isEmpty()) continue;
                    } catch (Throwable t) { CADiagnostics.quarantine(ind.getId(), "item requirement check", t); continue; }
                    float sc = itemScore(spec.getId(), m, c.profile);
                    if (sc > bestScore) { bestScore = sc; best = ind; bestM = m; }
                }
            }
            if (best == null) {
                if (swapItem(d, s, markets, spec, data, effect, targets)) return true;
                continue;
            }
            float fee = CABudget.fee(s, STANDARD_MOVE_FEE);
            if (!canPay(s, fee)) return false;
            try {
                // the stack's own object: some mods keep state in a SpecialItemData subclass (a copy of Substance.Abuse's
                // Recipe Book loses its recipes and crashes the colony on every update)
                best.setSpecialItem(data);
                st.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1f);
                bestM.reapplyIndustries();   // the item's effect otherwise waits for the next economy update
                CABudget.charge(s, 0f, fee, 0f);
                idleAt = -99f;
                CALog.add(bestM.getName() + ": installed " + spec.getName() + " in " + best.getCurrentName()
                        + (fee > 0 ? " (" + Misc.getDGSCredits(fee) + " fee)" : ""), true);
                return true;
            } catch (Throwable t) {
                CADiagnostics.quarantine(best.getId(), "installing " + spec.getId(), t);
                return false;
            }
        }
        return false;
    }

    /** Worth swapping: the waiting item scores at least a quarter more than the known item installed. */
    static boolean better(float waiting, float installed) { return installed > 0f && waiting >= installed * 1.25f; }

    /**
     * With no free building for it, swaps a waiting item in for a lesser one in the same kind of building (a Pristine
     * Nanoforge for a Corrupted one); the old one goes back to Autopilot Storage. Never the Orbital Fusion Lamp (pullLamp
     * handles it), an item Autopilot can't value, or a building that uses its item up.
     */
    static boolean swapItem(CAData d, CASettings s, List<MarketAPI> markets, SpecialItemSpecAPI spec, SpecialItemData data,
                            InstallableItemEffect effect, Set<String> targets) {
        Industry best = null; MarketAPI bestM = null; float bestGain = 0f;
        for (MarketAPI m : markets) {
            if (CAHypershunt.is(m) || !CAMods.itemFits(spec.getId(), m)) continue;
            CAData.Colony c = d.colony(m.getId());
            for (Industry ind : m.getIndustries()) {
                SpecialItemData old = ind.getSpecialItem();
                if (old == null || !targets.contains(ind.getId()) || old.getId().equals(spec.getId()) || LAMP.equals(old.getId())) continue;
                if (!ind.isFunctional() || ind.isHidden() || c.locks.contains(ind.getId()) || ind.getSpec().hasTag("consumes")) continue;
                try { if (!effect.getUnmetRequirements(ind).isEmpty()) continue; } catch (Throwable t) { continue; }
                float now = itemScore(old.getId(), m, c.profile), then = itemScore(spec.getId(), m, c.profile);
                if (better(then, now) && then - now > bestGain) { bestGain = then - now; best = ind; bestM = m; }
            }
        }
        if (best == null) return false;
        float fee = CABudget.fee(s, STANDARD_MOVE_FEE);
        if (!canPay(s, fee)) return false;
        SpecialItemData old = best.getSpecialItem();
        String oldName = name(old.getId());
        try {
            best.setSpecialItem(data);
            d.storage().removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1f);
            d.storage().addSpecial(old, 1f);
            bestM.reapplyIndustries();
            CABudget.charge(s, 0f, fee, 0f);
            idleAt = -99f;
            CALog.add(bestM.getName() + ": swapped " + spec.getName() + " into " + best.getCurrentName() + " for the " + oldName
                    + ", which went back to Autopilot Storage" + (fee > 0 ? " (" + Misc.getDGSCredits(fee) + " fee)" : ""), true);
            return true;
        } catch (Throwable t) {
            CADiagnostics.quarantine(best.getId(), "swapping in " + spec.getId(), t);
            return false;
        }
    }

    static String name(String itemId) {
        try { return Global.getSettings().getSpecialItemSpec(itemId).getName(); } catch (Throwable t) { return itemId; }
    }

    // ---------------- waiting items ----------------

    /** A colony item in Autopilot Storage and the buildings it goes in. */
    public static class Idle {
        public SpecialItemData data;
        public SpecialItemSpecAPI spec;
        public InstallableItemEffect effect;
        public Set<String> targets = new HashSet<String>();
    }

    /** Credits a month per point of itemScore, at the default unit value: a Catalytic Core's 50 x size 6 comes to about +3 metals. */
    static final float POINT_CREDITS = 30f;

    static List<Idle> idle;
    static float idleAt = -99f;

    /** An Auto colony's building this item would go in right now (its kind, no item yet, requirements met), or null. */
    static Industry emptyTarget(Idle it, MarketAPI m) {
        CAData.Colony c = CAData.get().colony(m.getId());
        for (Industry ind : m.getIndustries()) {
            if (!it.targets.contains(ind.getId()) || ind.getSpecialItem() != null || !ind.isFunctional() || ind.isHidden() || c.locks.contains(ind.getId())) continue;
            try { if (it.effect.getUnmetRequirements(ind).isEmpty()) return ind; } catch (Throwable ignored) { }
        }
        return null;
    }

    /**
     * Colony items in Autopilot Storage that no Auto colony has a building for: a new building that would take one is
     * worth that much more (idleBonus). Industrial Evolution's VPC chips have their own placer. Refreshed daily.
     */
    public static List<Idle> idleItems() {
        float now = CADay.now();
        if (idle != null && now - idleAt < 1f) return idle;
        idleAt = now;
        List<Idle> out = new ArrayList<Idle>();
        List<MarketAPI> auto = autoMarkets();
        for (CargoStackAPI stack : CAData.get().storage().getStacksCopy()) {
            if (!stack.isSpecialStack() || stack.getSize() < 1f) continue;
            SpecialItemSpecAPI spec = stack.getSpecialItemSpecIfSpecial();
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (spec == null || data == null || NEVER.contains(spec.getId()) || LAMP.equals(spec.getId()) || spec.getId().startsWith("IndEvo_vpc_")) continue;
            if (CADiagnostics.itemLevel(spec.getId()) == CADiagnostics.Level.QUARANTINED) continue;
            InstallableItemEffect effect = ItemEffectsRepo.ITEM_EFFECTS.get(spec.getId());
            if (effect == null || spec.getParams() == null || CABrewery.minSize(data) < 0) continue;
            Idle it = new Idle();
            it.data = data; it.spec = spec; it.effect = effect;
            for (String p : spec.getParams().split(",")) it.targets.add(p.trim());
            boolean home = false;
            for (MarketAPI m : auto) if (!CAHypershunt.is(m) && CAMods.itemFits(spec.getId(), m) && emptyTarget(it, m) != null) { home = true; break; }
            if (!home) out.add(it);
        }
        idle = out;
        return out;
    }

    /** The building a waiting item would go in at this colony: one it has, or one it could build now; null if neither. */
    public static String fitsAt(Idle it, MarketAPI m) {
        if (!CAMods.itemFits(it.spec.getId(), m)) return null;
        Industry has = emptyTarget(it, m);
        if (has != null) return has.getId();
        boolean free = Misc.getMaxIndustries(m) - Misc.getNumIndustries(m) > 0;
        for (String id : it.targets) {
            try {
                com.fs.starfarer.api.loading.IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(id);
                if (sp == null || m.hasIndustry(id) || CAPlan.hasOrBetter(m, id)) continue;
                Industry inst = sp.getNewPluginInstance(m);
                if (!inst.isAvailableToBuild() || (inst.isIndustry() && !free)) continue;
                if (CAAotD.enabled() && !CAAotD.allows(id, m)) continue;
                if (it.effect.getUnmetRequirements(inst).isEmpty()) return id;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    /** What a waiting item adds a month in a building of the pick's kind: its extra output, else its value next to the vanilla items. */
    static float itemWorth(Idle it, MarketAPI m, CAEngine.Pick p, CAData.Profile prof) {
        if (it.effect instanceof com.fs.starfarer.api.impl.campaign.econ.impl.BoostIndustryInstallableItemEffect) {
            int up = ((com.fs.starfarer.api.impl.campaign.econ.impl.BoostIndustryInstallableItemEffect) it.effect).getSupplyIncrease();
            float v = 0f;
            for (String com : p.supplied.keySet()) {
                try { v += up * Global.getSettings().getCommoditySpec(com).getBasePrice() * CAEngine.unitValue; } catch (Throwable ignored) { }
            }
            if (v > 0f) return v;
        }
        return itemScore(it.spec.getId(), m, prof) * POINT_CREDITS * CAEngine.unitValue / 100f;
    }

    /** A new building that would take a waiting item (one no colony has a building for) is worth what the item adds there. */
    public static void idleBonus(MarketAPI m, Industry inst, CAEngine.Pick p, CAData.Profile prof) {
        if (p.upgrade) return;
        Idle best = null; float bestV = 0f;
        for (Idle it : idleItems()) {
            if (!it.targets.contains(p.id) || !CAMods.itemFits(it.spec.getId(), m)) continue;
            try { if (!it.effect.getUnmetRequirements(inst).isEmpty()) continue; } catch (Throwable t) { continue; }
            float v = itemWorth(it, m, p, prof);
            if (v > bestV) { bestV = v; best = it; }
        }
        if (best == null) return;
        p.fx[0] += bestV;
        p.lines.add("Uses the " + best.spec.getName() + " waiting in Autopilot Storage: about " + Misc.getDGSCredits(bestV) + " per month");
    }

    // ---------------- Orbital Fusion Lamp ----------------
    // In Population & Infrastructure (ItemEffectsRepo, PopulationAndInfrastructure.setSpecialItem) it cancels Cold, Very
    // Cold, Poor Light and Dark and always needs 10 volatiles (a shortage adds up to +50% hazard). On a world that isn't
    // cold it adds heat: Hot, or Extreme Heat in place of Hot; taking it out puts the old heat back. So it pays on a cold
    // or dark world, or where the colony's plan needs Extreme Heat (Terraforming Made Easy's ore projects take it in place
    // of extreme tectonic activity). Anywhere else it's only hazard: higher upkeep and slower growth.

    static final String LAMP = "orbital_fusion_lamp";
    static final String[] LAMP_CANCELS = {"cold", "very_cold", "poor_light", "dark"};
    static final int LAMP_VOLATILES = 10;

    /**
     * What the lamp is worth a month at this colony (CAConditions values): the conditions it cancels, less the heat it
     * adds and its volatiles at their base price. With the lamp in, the heat it added is already there, so a world that
     * isn't cold counts as having gone from Hot to Extreme Heat.
     */
    static float lampValue(MarketAPI m, CAData.Profile p, boolean installed) {
        float v = 0f;
        boolean cold = m.hasCondition("cold") || m.hasCondition("very_cold");
        for (String c : LAMP_CANCELS) if (m.hasCondition(c)) v -= CAConditions.value(m, c, p);
        if (!cold) {
            if (installed || m.hasCondition("hot")) v += CAConditions.value(m, "very_hot", p) - CAConditions.value(m, "hot", p);
            else if (!m.hasCondition("very_hot")) v += CAConditions.value(m, "hot", p);
        }
        try { v -= LAMP_VOLATILES * Global.getSettings().getCommoditySpec("volatiles").getBasePrice(); } catch (Throwable ignored) { }
        return v;
    }

    /** True when an open step of the colony's plan needs Extreme Heat and nothing here stands in for it (extreme tectonic activity). */
    static boolean planNeedsHeat(MarketAPI m, CAData.Profile p) {
        if (m.hasCondition("extreme_tectonic_activity")) return false;
        try {
            for (CAPlan.Step s : CAPlan.build(m, p).steps) {
                if (s.kind != CAPlan.Kind.COND || s.done(m)) continue;
                if (s.id.equals("very_hot")) return true;
                CAWorldPlanner.Row r = CAWorldPlanner.rows().get(s.id);
                if (r != null && CAWorldPlanner.idsOf(r.liked).contains("very_hot")) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /**
     * Takes an Orbital Fusion Lamp back to Autopilot Storage from an Auto colony where it costs more than it gives,
     * unless the colony's plan needs its Extreme Heat. The game puts the colony's old heat back. One a day, like placements.
     */
    static boolean pullLamp(CAData d, List<MarketAPI> markets) {
        if (CADiagnostics.itemLevel(LAMP) == CADiagnostics.Level.QUARANTINED) return false;
        for (MarketAPI m : markets) {
            CAData.Colony c = d.colony(m.getId());
            for (Industry ind : m.getIndustries()) {
                SpecialItemData item = ind.getSpecialItem();
                if (item == null || !LAMP.equals(item.getId()) || c.locks.contains(ind.getId())) continue;
                if (planNeedsHeat(m, c.profile) || lampValue(m, c.profile, true) > 0f) continue;
                boolean cancels = false;
                for (String k : LAMP_CANCELS) if (m.hasCondition(k)) cancels = true;
                try {
                    ind.setSpecialItem(null);
                    d.storage().addSpecial(item, 1f);
                    m.reapplyConditions();
                    m.reapplyIndustries();
                    CALog.add(m.getName() + ": took the Orbital Fusion Lamp out of " + ind.getCurrentName() + ", back to Autopilot Storage ("
                            + (cancels ? "the heat it adds costs more than the cold or darkness it cancels"
                                       : "it only made the colony hotter and used " + LAMP_VOLATILES + " volatiles; it helps only a cold or dark world")
                            + ")", true);
                    return true;
                } catch (Throwable t) {
                    CADiagnostics.quarantine(LAMP, "taking out the Orbital Fusion Lamp at " + m.getName(), t);
                    return false;
                }
            }
        }
        return false;
    }

    // ---------------- Antimatter Stabilizer (Emergent Threats) ----------------

    /**
     * Replaces Fuel Production with Advanced Fuel Production using a stabilizer from Autopilot Storage, the same way
     * Emergent Threats' portmaster does it: the AI core, colony item and improvement carry over.
     */
    static boolean convertFuel(CAData d, CASettings s, List<MarketAPI> markets) {
        CargoAPI st = d.storage();
        if (st.getCommodityQuantity("ix_antimatter_stabilizer") < 1f) return false;
        if (Global.getSettings().getIndustrySpec("ix_fuel_production") == null) return false;
        MarketAPI best = null; int bestSize = -1;
        for (MarketAPI m : markets) {
            Industry fuel = m.getIndustry("fuelprod");
            if (fuel == null || !fuel.isFunctional() || fuel.isBuilding() || fuel.isUpgrading() || m.hasIndustry("ix_fuel_production")) continue;
            if (d.colony(m.getId()).locks.contains("fuelprod")) continue;
            if (CAPlan.build(m, d.colony(m.getId()).profile).exact.contains("fuelprod")) continue;   // the Unification Center needs vanilla Fuel Production
            int score = m.getSize() * 10 + (fuel.getSpecialItem() != null ? 5 : 0) + (fuel.getAICoreId() != null ? 3 : 0);
            if (score > bestSize) { bestSize = score; best = m; }
        }
        if (best == null) return false;
        float fee = CABudget.fee(s, STANDARD_MOVE_FEE);
        if (!canPay(s, fee)) return false;
        try {
            Industry fuel = best.getIndustry("fuelprod");
            boolean improved = fuel.isImproved();
            String core = fuel.getAICoreId();
            String item = fuel.getSpecialItem() == null ? null : fuel.getSpecialItem().getId();
            if (item == null) best.addIndustry("ix_fuel_production");
            else best.addIndustry("ix_fuel_production", new ArrayList<String>(Arrays.asList(item)));
            Industry adv = best.getIndustry("ix_fuel_production");
            if (core != null) adv.setAICoreId(core);
            adv.setImproved(improved);
            fuel.setAICoreId(null);
            fuel.setSpecialItem(null);
            best.removeIndustry("fuelprod", null, false);
            st.removeCommodity("ix_antimatter_stabilizer", 1f);
            CABudget.charge(s, 0f, fee, 0f);
            CALog.add(best.getName() + ": used an Antimatter Stabilizer to upgrade Fuel Production to Advanced Fuel Production", true);
            return true;
        } catch (Throwable t) {
            CADiagnostics.quarantine("ix_fuel_production", "Antimatter Stabilizer conversion at " + best.getName(), t);
            return false;
        }
    }

    // ---------------- AI cores ----------------
    // A core is worth what it does in that building, valued with the colony's goal weights the way buildings are
    // (CAEngine.WEIGHTS): vanilla's BaseIndustry gives every core -1 demand, Alpha and Beta -25% upkeep, and an Alpha +1 to
    // each output, or in a structure its own bonus instead (fleet size, station strength, ground defense, accessibility,
    // income, growth), less the colony crisis progress it adds (CACrisis): the Hegemony counts every core at a colony
    // above size 3, and a colony of size 4 or more with 7 Pather interest gets a Pather cell, whose interest then counts
    // too. A core that pays less than that stays in storage. The Hegemony only inspects a colony holding more than an
    // Alpha, a Beta and a Gamma's points (HegemonyHostileActivityFactor.pickTargetMarket), so that's every colony's limit
    // unless the risk setting allows more; Small colonies only keeps cores out of colonies above size 3 altogether.

    static final String[] CORES = {"alpha_core", "beta_core", "gamma_core"};
    static final String[] POINT_KEYS = {"hegemonyPointsAlpha", "hegemonyPointsBeta", "hegemonyPointsGamma"};
    static final int[] DEFAULT_POINTS = {4, 2, 1};
    /** A core moves to another building only for this much more a month, so cores don't shuffle. */
    static final float CORE_MOVE_MARGIN = 3000f;
    /** How much stronger than the next inspection a colony's defenses must be to hold more; the second while its threat is high. */
    static final float DEFENSE_MARGIN = 1.5f, DEFENSE_MARGIN_THREAT = 2f;

    static int points(int k) {
        try { return Math.round(Global.getSettings().getFloat(POINT_KEYS[k])); } catch (Throwable t) { return DEFAULT_POINTS[k]; }
    }

    static int tier(String coreId) {
        for (int k = 0; k < CORES.length; k++) if (CORES[k].equals(coreId)) return k;
        return -1;
    }

    /** Inspection points the Hegemony counts at m: 10 for an AI core administrator, then 4, 2 and 1 for each core. */
    public static int aiPoints(MarketAPI m) {
        try { return Math.round(HegemonyAICoresActivityCause.getAICorePoints(m)); } catch (Throwable ignored) { }
        int total = m.getAdmin() != null && m.getAdmin().isAICore() ? 10 : 0;
        for (Industry ind : m.getIndustries()) { int k = tier(ind.getAICoreId()); if (k >= 0) total += points(k); }
        return total;
    }

    /** The most points a colony can hold and never be picked for an inspection. */
    public static int safePoints() { return points(0) + points(1) + points(2); }

    public static float nextInspectionFP() {
        int defeated = 0;
        try { defeated = HegemonyHostileActivityFactor.getInspectionsDefeated(); } catch (Throwable ignored) { }
        return defeated <= 0 ? 150f : defeated == 1 ? 600f : 1400f;
    }

    public static boolean hegemonyDone() {
        try { return HegemonyHostileActivityFactor.isPlayerDefeatedHegemony(); } catch (Throwable t) { return false; }
    }

    /** Why no inspection can come now, or null when one can. Persean League members get none: the Hegemony's progress is negated, and an event is only picked from progress. */
    public static String noInspections() {
        if (hegemonyDone()) return "you've defeated the Hegemony's inspections";
        try { if (PerseanLeagueMembership.isLeagueMember()) return "Persean League membership keeps the Hegemony from acting"; } catch (Throwable ignored) { }
        for (MarketAPI o : Global.getSector().getEconomy().getMarketsCopy()) if (Factions.HEGEMONY.equals(o.getFactionId())) return null;
        return "the Hegemony holds no colonies";
    }

    /**
     * Colonies an inspection is planned against or on its way to. Their cores stay as they are: the inspectors expect what
     * they saw, and finding less disrupts every building there (HIActionStage), while adding more only adds to what's taken.
     */
    public static Set<MarketAPI> inspected() {
        Set<MarketAPI> out = new HashSet<MarketAPI>();
        try {
            HostileActivityEventIntel ha = HostileActivityEventIntel.get();
            HostileActivityEventIntel.HAERandomEventData r = ha == null ? null : ha.getRollDataForEvent();
            if (r != null && r.factor instanceof HegemonyHostileActivityFactor && r.custom instanceof MarketAPI) out.add((MarketAPI) r.custom);
        } catch (Throwable ignored) { }
        try {
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(HegemonyInspectionIntel.class)) {
                if (i.isEnded() || i.isEnding()) continue;
                MarketAPI target = ((HegemonyInspectionIntel) i).getTarget();
                if (target != null) out.add(target);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** Station fleet points by CAPlan.stationTier: orbital station, battlestation, star fortress, Stella Castellum or Star Citadel. */
    static final float[] STATION_FP = {0f, 120f, 300f, 600f, 900f};

    /** Rough defense estimate in fleet points: station plus patrol fleets, scaled by fleet size. Disrupted ones don't count. */
    public static float defenseFP(MarketAPI m) {
        float station = 0f, patrol = 0f;
        for (Industry ind : m.getIndustries()) {
            if (!ind.isFunctional()) continue;
            String id = ind.getId();
            int tier = CAPlan.stationTier(ind.getSpec());   // any mod's station by its tags (Imperium's, Knights of Ludd's, AotD's citadels)
            if (tier > 0) station = Math.max(station, STATION_FP[tier]);
            else if (id.equals("highcommand")) patrol = Math.max(patrol, 300f);
            else if (id.equals("militarybase")) patrol = Math.max(patrol, 180f);
            else if (id.equals("patrolhq")) patrol = Math.max(patrol, 80f);
        }
        float fleetMult = 1f;
        try { fleetMult = Math.max(1f, m.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f)); }
        catch (Throwable ignored) { }
        return station + patrol * fleetMult;
    }

    /** Whether m's defenses beat the next inspection by DEFENSE_MARGIN, or DEFENSE_MARGIN_THREAT while its threat is high. */
    public static boolean defended(MarketAPI m) {
        float margin = CAData.get().colony(m.getId()).threatHigh ? DEFENSE_MARGIN_THREAT : DEFENSE_MARGIN;
        return defenseFP(m) >= margin * nextInspectionFP();
    }

    /**
     * The most inspection points Autopilot fills m to: no limit when no inspection can come, with No limit, or with More
     * where defended at a defended colony; none above size 3 with Small colonies only; and while a Pather attack is coming
     * for m, few enough that its Pather interest falls under 7.
     */
    public static int coreLimit(CASettings s, MarketAPI m, String noInspections) {
        int lim = hegemonyLimit(s, m, noInspections), relief = pathRelief(m);
        return relief >= 0 ? Math.min(lim, relief) : lim;
    }

    static int hegemonyLimit(CASettings s, MarketAPI m, String noInspections) {
        if (noInspections != null || s.coreRisk == 2) return Integer.MAX_VALUE;
        if (s.coreRisk == 0 && defended(m)) return Integer.MAX_VALUE;
        if (s.coreRisk == 3 && m.getSize() > HegemonyAICoresActivityCause.IGNORE_COLONY_THRESHOLD && CACrisis.hegemonyCounts()) return 0;
        return safePoints();
    }

    static int pathRelief(MarketAPI m) {
        try { return CACrisis.pathReliefLimit(m); } catch (Throwable t) { return -1; }
    }

    /** Why m's core limit is what it is, for the panels and the log. */
    public static String limitWhy(CASettings s, MarketAPI m, String none, int lim) {
        if (lim == pathRelief(m)) return "limit " + lim + ": a Pather attack is coming for it, so its Pather interest goes under 7 and its cell can dissolve";
        if (lim == 0) return "limit 0: above size " + HegemonyAICoresActivityCause.IGNORE_COLONY_THRESHOLD + " every core adds to the Hegemony's crisis (AI cores: Small colonies only)";
        if (lim != Integer.MAX_VALUE) return "limit " + lim + ", which the Hegemony never inspects";
        if (none != null) return "no limit: " + none;
        if (s.coreRisk == 2) return "no limit (Inspection risk: No limit)";
        return "no limit: its defenses (about " + (int) defenseFP(m) + " fleet points) beat the next inspection's " + (int) nextInspectionFP();
    }

    /** A nanoforge that pollutes (vanilla's; AotD's no_pollution ones don't) waits in Autopilot Storage. */
    static boolean pollutingForgeWaiting() {
        try {
            for (CargoStackAPI st : CAData.get().storage().getStacksCopy()) {
                if (!st.isSpecialStack() || st.getSpecialDataIfSpecial() == null) continue;
                if (st.getSpecialDataIfSpecial().getId().endsWith("_nanoforge") && CAConditions.polluting(st.getSpecialDataIfSpecial())) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /** An Alpha core only goes where it can be taken out cleanly: vanilla buildings, never Population & Infrastructure or mod code. */
    static boolean alphaRemovable(Industry ind) {
        if (research(ind)) return true;   // VoK recomputes its research costs from whatever core is in
        return ind.getSpec().getSourceMod() == null && CAEngine.isSupportedVanilla(ind.getId()) && !ind.getSpec().hasTag("population");
    }

    // ---------------- research buildings ----------------
    // Vaults of Knowledge's Research Facility and Black Site (ResearchFacility/BlackSite.apply): a core doesn't speed research.
    // It cuts the building's own costs, the research cost (10,000 x the running tech's tier), the 10,000 maintenance and a
    // Black Site's cover-up (20,000 per point of stability under 10), by half for an Alpha, a quarter for a Beta and a tenth
    // for a Gamma. An Alpha also adds a research databank a month on a world with a pre-collapse facility.

    static final float[] RESEARCH_CUT = {0.5f, 0.25f, 0.1f};
    /** Alpha cores: research buildings first. An Alpha there counts this much more, so it beats any other placement. */
    static final float RESEARCH_FIRST = 1e7f;

    static boolean research(Industry ind) { return ind.getId().equals("aotd_research_facility") || ind.getId().equals("aotd_blacksite"); }

    /** Credits a month core k saves in a research building whose research costs are `costs` now, with core `cur` in it (-1: none). */
    static float researchSaving(float costs, int cur, int k) {
        float now = cur >= 0 ? 1f - RESEARCH_CUT[cur] : 1f;
        return costs / now * RESEARCH_CUT[k];
    }

    /** The part of a research building's upkeep that its core cuts (VoK's aotd_research flat mods), at the colony's upkeep modifiers. */
    static float researchUpkeep(Industry ind) {
        com.fs.starfarer.api.combat.MutableStat up = ind.getUpkeep();
        float flats = 0f, costs = 0f;
        for (Map.Entry<String, com.fs.starfarer.api.combat.MutableStat.StatMod> e : up.getFlatMods().entrySet()) {
            flats += e.getValue().getValue();
            if (e.getKey().startsWith("aotd_research")) costs += e.getValue().getValue();
        }
        float raw = up.getBaseValue() + flats;
        return raw <= 0f || costs <= 0f ? 0f : up.getModifiedValue() * costs / raw;
    }

    /** Core priority by building type; matches the vanilla id, or the type tag Ashes of the Domain puts on its own versions. */
    static int industryPriority(Industry ind) {
        String id = ind.getId();
        if (id.equals("orbitalworks") || is(ind, "heavyindustry")) return 0;
        if (is(ind, "refining") || is(ind, "fuelprod") || is(ind, "mining")) return 1;
        if (is(ind, "lightindustry") || is(ind, "farming") || is(ind, "aquaculture") || is(ind, "commerce")) return 2;
        if (id.equals("highcommand") || id.equals("militarybase") || CAPlan.stationTier(ind.getSpec()) >= 2) return 3;
        return 4;
    }

    static boolean is(Industry ind, String type) {
        return ind.getId().equals(type) || ind.getSpec().hasTag(type) || ind.getSpec().hasTag("aotd_" + type);
    }

    /** What an Alpha does in vanilla's structures instead of +1 output, or null for a building that makes things. */
    static String structure(Industry ind) {
        String id = ind.getId();
        if (id.equals("patrolhq") || id.equals("militarybase") || id.equals("highcommand")) return "fleet";
        if (CAPlan.stationTier(ind.getSpec()) > 0) return "station";
        if (id.equals("grounddefenses") || id.equals("heavybatteries") || id.equals("planetaryshield")) return "ground";
        if (id.equals("spaceport") || id.equals("megaport")) return "port";
        if (is(ind, "commerce")) return "commerce";
        if (id.equals("cryorevival")) return "growth";
        if (id.equals("waystation") || id.equals("techmining")) return "other";
        return null;
    }

    /** Defensive structures: the only buildings a colony under a high threat gets new cores in. */
    static boolean defensive(Industry ind) {
        String st = structure(ind);
        return "fleet".equals(st) || "station".equals(st) || "ground".equals(st);
    }

    /** A building Autopilot may put a core in or take one out of: working, unlocked, supported and taking cores, with no core or a vanilla one. */
    static boolean coreSlot(CAData.Colony c, Industry ind) {
        if (!ind.isFunctional() || ind.isHidden() || c.locks.contains(ind.getId())) return false;
        if (CADiagnostics.industryLevel(ind.getId()) != CADiagnostics.Level.SUPPORTED && !ind.getSpec().hasTag("population") && !research(ind)) return false;
        if (ind.getAICoreId() != null && tier(ind.getAICoreId()) < 0) return false;
        try { return ind.canInstallAICores(); } catch (Throwable t) { return false; }
    }

    /** The colony's goal weights: fast buildout's money-only ones, else its goal's, or Security while its threat is high. */
    static float[] coreWeights(MarketAPI m, CAData.Colony c) {
        if (c.buildout) return CAEngine.PROFIT;
        return CAEngine.WEIGHTS[CAThreat.effective(m, c.profile).ordinal()];
    }

    /** Credits a month core k adds in ind with goal weights w, less the Hegemony and Pather crisis progress it adds (crisis null: none counted). */
    public static float coreValue(MarketAPI m, Industry ind, int k, float[] w, Map<String, Integer> deficit, CACrisis.Base crisis) {
        float[] fx = new float[7];
        boolean rs = research(ind);
        if (rs) {   // VoK's own cut instead of vanilla's x0.75, for all three cores; an Alpha also adds a databank on a pre-collapse facility world
            fx[0] += researchSaving(researchUpkeep(ind), tier(ind.getAICoreId()), k);
            if (k == 0 && m.hasCondition("pre_collapse_facility")) fx[0] += CAResearch.DATABANK_VALUE;
        }
        if (k <= 1 && !rs) {   // Alpha and Beta: upkeep x0.75
            float up = ind.getUpkeep().getModifiedValue();
            if (tier(ind.getAICoreId()) == 0 || tier(ind.getAICoreId()) == 1) up /= BaseIndustry.UPKEEP_MULT;
            fx[0] += up * (1f - BaseIndustry.UPKEEP_MULT);
        }
        // -1 demand matters where an input is short; a core already in there has taken its unit off
        int back = ind.getAICoreId() != null ? BaseIndustry.DEMAND_REDUCTION : 0;
        boolean shortInput = false;
        for (MutableCommodityQuantity q : ind.getAllDemand()) {
            int dem = q.getQuantity().getModifiedInt() + back;
            CommodityOnMarketAPI com = m.getCommodityData(q.getCommodityId());
            if (dem <= 0 || com == null || com.getCommodity() == null || dem <= com.getAvailable()) continue;
            shortInput = true;
            fx[0] += com.getCommodity().getBasePrice() * 30f;   // as CAEngine.demand counts an input your colonies lack
        }
        // output: a short input cuts it unit for unit, and an Alpha adds +1 outside structures
        String st = structure(ind);
        int more = (shortInput ? 1 : 0) + (k == 0 && st == null ? BaseIndustry.SUPPLY_BONUS : 0);
        if (more > 0) {
            for (MutableCommodityQuantity q : ind.getAllSupply()) {
                CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(q.getCommodityId());
                if (spec == null || q.getQuantity().getModifiedInt() <= 0) continue;
                Integer d = deficit.get(q.getCommodityId());
                fx[0] += spec.getBasePrice() * CAEngine.unitValue * more * (d != null && d > 0 ? 1.5f : 1f);
            }
        }
        if (k == 0 && st != null) {
            if (st.equals("fleet")) fx[4] += 0.15f;           // fleet size x1.25 (MilitaryBase)
            else if (st.equals("station")) fx[4] += 0.15f;    // station combat effectiveness
            else if (st.equals("ground")) fx[3] += 0.5f;      // ground defense x1.5
            else if (st.equals("port")) fx[2] += 0.2f;        // +20% accessibility (Spaceport)
            else if (st.equals("growth")) fx[6] += 1f;        // more immigration (Cryorevival)
            else if (st.equals("commerce")) {                 // +25% to the colony's income multiplier (TradeCenter)
                float income = 0f;
                for (Industry o : m.getIndustries()) income += Math.max(0f, o.getIncome().getModifiedValue());
                fx[0] += 0.25f * income;
            }
        }
        // Station Augments' AI Fighter Uplink makes this core the captain of every fighter a carrier station launches
        if ("station".equals(st) && Global.getSettings().getModManager().isModEnabled(CAAugments.MOD) && CAAugments.knows("SA_aiFighterUplink")) {
            try { fx[4] += CAAugmentModel.uplinkCoreFx(CAStationKit.of(m), k); } catch (Throwable ignored) { }
        }
        float v = 0f;
        for (int i = 0; i < 7; i++) v += w[i] * fx[i];
        if (crisis == null) return v;
        int without = tier(ind.getAICoreId()) >= 0 ? points(tier(ind.getAICoreId())) : 0;   // priced from the colony without this building's core
        return v - CACrisis.hegemonyCost(crisis, m, points(k), without) - CACrisis.pathCost(crisis, m, points(k), without);
    }

    /** A building that can take a core, with what each core is worth there. */
    static class CoreSlot {
        MarketAPI m; Industry ind; int cur; boolean threat;
        boolean first;   // a research building that gets Alpha cores before anything else
        float[] net = new float[3];
        /** What core k counts for when ranking placements: what it adds, plus RESEARCH_FIRST for an Alpha in a research building first in line. */
        float rank(int k) { return net[k] + (k == 0 && first ? RESEARCH_FIRST : 0f); }
    }

    static String coreName(int k) {
        try { return Global.getSettings().getCommoditySpec(CORES[k]).getName(); } catch (Throwable t) { return CORES[k]; }
    }

    /**
     * One change a day. A colony over its limit sends its least useful core back to storage first. Otherwise the best of:
     * a core from storage into a building where it adds more than it costs, a better core replacing a lesser one (which
     * goes back to storage), or a core moved to a building where it adds at least CORE_MOVE_MARGIN more. Colonies an
     * inspection is coming for are left as they are.
     */
    static boolean placeCore(CAData d, CASettings s, List<MarketAPI> markets) {
        CargoAPI st = d.storage();
        String none = noInspections();
        Set<MarketAPI> frozen = inspected();
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        CACrisis.Base crisis = CACrisis.base();
        List<CoreSlot> slots = new ArrayList<CoreSlot>();
        Map<MarketAPI, Integer> points = new HashMap<MarketAPI, Integer>(), limit = new HashMap<MarketAPI, Integer>();
        for (MarketAPI m : markets) {
            if (frozen.contains(m)) continue;
            CAData.Colony c = d.colony(m.getId());
            float[] w = coreWeights(m, c);   // first: it updates the threat state the limit reads
            points.put(m, aiPoints(m));
            limit.put(m, coreLimit(s, m, none));
            for (Industry ind : m.getIndustries()) {
                if (!coreSlot(c, ind)) continue;
                CoreSlot x = new CoreSlot();
                x.m = m; x.ind = ind; x.cur = tier(ind.getAICoreId()); x.threat = c.threatHigh;
                x.first = !s.researchCoresByValue && research(ind);
                for (int k = 0; k < CORES.length; k++) x.net[k] = coreValue(m, ind, k, w, deficit, crisis);
                slots.add(x);
            }
        }

        // over the limit: the core that adds least per point goes back to storage
        for (MarketAPI m : points.keySet()) {
            int pts = points.get(m), lim = limit.get(m);
            if (pts <= lim) continue;
            CoreSlot worst = null;
            int movable = 0;
            for (CoreSlot x : slots) {
                if (x.m != m || x.cur < 0 || (x.cur == 0 && s.alphaMode == 0 && !alphaRemovable(x.ind))) continue;
                movable += points(x.cur);
                if (worst == null || x.rank(x.cur) / points(x.cur) < worst.rank(worst.cur) / points(worst.cur)) worst = x;
            }
            if (worst == null || pts - movable > lim) {   // an AI core administrator or locked buildings keep it over anyway
                if (CALog.once("cores:fixed:" + m.getId() + ":" + pts + ":" + lim))
                    CALog.add(m.getName() + ": " + pts + " inspection points (" + limitWhy(s, m, none, lim) + ") from cores Autopilot doesn't move "
                            + "(an AI core administrator, or locked buildings), so no more cores go there");
                continue;
            }
            worst.ind.setAICoreId(null);
            st.addCommodity(CORES[worst.cur], 1f);
            CALog.add(m.getName() + ": took the " + coreName(worst.cur) + " out of " + worst.ind.getCurrentName() + " and back to Autopilot Storage: "
                    + pts + " inspection points, " + limitWhy(s, m, none, lim)
                    + (s.coreRisk == 0 && lim == safePoints() ? " (its defenses don't beat the next inspection)" : ""), true);
            return true;
        }

        int[] have = new int[CORES.length];
        for (int k = 0; k < CORES.length; k++) have[k] = (int) st.getCommodityQuantity(CORES[k]);
        if (s.alphaMode != 2 && makeRoom(slots, points, limit, have[0] > 0, s, st, none)) return true;
        CoreSlot bestTo = null, bestFrom = null;
        int bestK = -1;
        float bestGain = 0f;
        for (CoreSlot to : slots) {
            if (to.threat && !defensive(to.ind)) continue;   // a threatened colony only gets cores in its defenses
            int room = limit.get(to.m) - points.get(to.m);
            for (int k = 0; k < CORES.length; k++) {
                if (k == 0 && (s.alphaMode == 2 || (s.alphaMode == 0 && !alphaRemovable(to.ind)))) continue;
                if (to.cur >= 0 && k >= to.cur) continue;   // into an empty building, or replacing a lesser core
                if (to.net[k] <= 0f) continue;
                int extra = points(k) - (to.cur >= 0 ? points(to.cur) : 0);
                float gain = to.rank(k) - (to.cur >= 0 ? to.rank(to.cur) : 0f);
                if (have[k] > 0) {
                    if (extra <= room && gain > bestGain) { bestGain = gain; bestTo = to; bestFrom = null; bestK = k; }
                    continue;
                }
                for (CoreSlot from : slots) {   // none in storage: the same kind from where it adds least
                    if (from.cur != k || from == to || (k == 0 && s.alphaMode == 0 && !alphaRemovable(from.ind))) continue;
                    if ((from.m == to.m ? extra - points(k) : extra) > room) continue;
                    float g = gain - from.rank(k);
                    if (g > CORE_MOVE_MARGIN && g > bestGain) { bestGain = g; bestTo = to; bestFrom = from; bestK = k; }
                }
            }
        }
        if (bestTo == null) return false;
        float fee = CABudget.fee(s, STANDARD_MOVE_FEE);
        if (!canPay(s, fee)) return false;
        int old = bestTo.cur;
        // what the change adds, without research buildings' place in line
        float real = bestTo.net[bestK] - (old >= 0 ? bestTo.net[old] : 0f) - (bestFrom != null ? bestFrom.net[bestK] : 0f);
        boolean first = bestK == 0 && bestTo.first && (bestFrom == null || !bestFrom.first);
        if (bestFrom != null) bestFrom.ind.setAICoreId(null);
        else st.removeCommodity(CORES[bestK], 1f);
        bestTo.ind.setAICoreId(CORES[bestK]);
        if (old >= 0) st.addCommodity(CORES[old], 1f);
        CABudget.charge(s, 0f, fee, 0f);
        int lim = limit.get(bestTo.m);
        String where = bestTo.m.getName() + ": ";
        String what = bestFrom != null
                ? "moved the " + coreName(bestK) + " from " + (bestFrom.m == bestTo.m ? "" : bestFrom.m.getName() + "'s ") + bestFrom.ind.getCurrentName() + " to " + bestTo.ind.getCurrentName()
                : (old >= 0 ? "replaced the " + coreName(old) + " in " + bestTo.ind.getCurrentName() + " with " + (coreName(bestK).matches("(?i)[aeiou].*") ? "an " : "a ") + coreName(bestK) + " (the " + coreName(old) + " is back in Autopilot Storage)"
                        : "installed " + coreName(bestK) + " in " + bestTo.ind.getCurrentName());
        String gainText = first ? ": research buildings get Alpha cores first (Settings, Items and AI cores); it cuts their costs by "
                + Misc.getDGSCredits(bestTo.net[0]) + " a month here" : ": about " + Misc.getDGSCredits(real) + " a month more";
        CALog.add(where + what + gainText + " (" + aiPoints(bestTo.m) + " inspection points"
                + (lim == Integer.MAX_VALUE ? (none != null ? ", no inspections" : s.coreRisk == 2 ? ", no limit" : ", defended") : " of " + lim) + ")", true);
        return true;
    }

    /**
     * Alpha cores: research buildings first. A research building waiting for an Alpha its colony has no inspection points
     * left for gets them: the colony's least useful other core goes back to storage, one a day, until the Alpha fits. Only
     * while an Alpha is to be had (in storage, or in another colony's ordinary building), only where that core's removal is
     * enough together with the others, and never under a high threat (research isn't a defense). Returns whether it acted.
     */
    static boolean makeRoom(List<CoreSlot> slots, Map<MarketAPI, Integer> points, Map<MarketAPI, Integer> limit, boolean alphaStored,
                            CASettings s, CargoAPI st, String none) {
        for (CoreSlot to : slots) {
            if (!to.first || to.cur == 0 || to.threat || to.net[0] <= 0f) continue;
            int room = limit.get(to.m) - points.get(to.m);
            int extra = points(0) - (to.cur >= 0 ? points(to.cur) : 0);
            if (extra <= room) continue;   // it fits: the ordinary pass puts it in
            boolean alpha = alphaStored;
            for (CoreSlot x : slots) if (x.cur == 0 && !x.first && x.m != to.m && (s.alphaMode != 0 || alphaRemovable(x.ind))) alpha = true;
            if (!alpha) continue;
            CoreSlot worst = null;
            int freeable = 0;
            for (CoreSlot x : slots) {
                if (x.m != to.m || x == to || x.cur < 0 || (x.cur == 0 && x.first)) continue;
                if (x.cur == 0 && s.alphaMode == 0 && !alphaRemovable(x.ind)) continue;
                freeable += points(x.cur);
                if (worst == null || x.net[x.cur] / points(x.cur) < worst.net[worst.cur] / points(worst.cur)) worst = x;
            }
            if (worst == null || room + freeable < extra) {
                if (CALog.once("cores:room:" + to.m.getId() + ":" + points.get(to.m)))
                    CALog.add(to.m.getName() + ": " + to.ind.getCurrentName() + " should get an Alpha core first, but even without the colony's "
                            + "other movable cores it's over its inspection limit (" + limitWhy(s, to.m, none, limit.get(to.m)) + ")");
                continue;
            }
            worst.ind.setAICoreId(null);
            st.addCommodity(CORES[worst.cur], 1f);
            CALog.add(to.m.getName() + ": took the " + coreName(worst.cur) + " out of " + worst.ind.getCurrentName() + " and back to Autopilot Storage, "
                    + "to make inspection room for an Alpha core in " + to.ind.getCurrentName() + " (research buildings get Alpha cores first)", true);
            return true;
        }
        return false;
    }

    // ---------------- administrators ----------------
    // An administrator is worth what a colony gains from them rather than from you governing it: their skills against yours,
    // and the colony limit (vanilla PopulationAndInfrastructure: each colony you govern past your limit costs every colony you
    // govern 2 stability, and each free place under it adds 2). Stability is only money below 5, where each point is 20% of
    // the colony's income (getIncomeStabilityMult); from 5 up it's counted at OTHER_STAB for fleets, ship quality and unrest.
    // That worth is set against vanilla's salaries (2,500 a month unskilled, 20,000 with a skill; 10% while unassigned).

    /** Credits a month for a point of stability at 5 or more, where it no longer changes income. */
    static final float OTHER_STAB = 1000f;
    /** A move must gain this much a month (or a quarter of the salary, if more), so administrators don't shuffle. */
    static final float MOVE_MARGIN = 5000f;

    static boolean governedByYou(MarketAPI m) { return m.getAdmin() == null || m.getAdmin().isPlayer(); }

    /** The hired administrator governing m, or null. */
    static AdminData adminAt(MarketAPI m) {
        for (AdminData a : Global.getSector().getCharacterData().getAdmins()) if (a.getMarket() == m) return a;
        return null;
    }

    static float idleSalaryMult() {
        try { return Global.getSettings().getFloat("idleAdminSalaryMult"); } catch (Throwable t) { return 0.1f; }
    }

    /** How many colonies you can govern yourself before the mismanagement penalty starts. */
    static int maxGoverned() { return Global.getSector().getPlayerStats().getOutpostNumber().getModifiedInt(); }

    /** How many more colonies you govern yourself than your limit, counted as vanilla does (PopulationAndInfrastructure.getMismanagementPenalty); 0 or less within it. */
    static int overLimit() {
        int n = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) if (m.isPlayerOwned() && governedByYou(m)) n++;
        return n - maxGoverned();
    }

    /**
     * Administrators the colony limit still needs: the colonies you govern past it, each costing every colony you govern 2
     * stability, plus colonies Autopilot made autonomous (Nexerelin), which come back once there's room for them. These
     * are placed or hired even when the money they add doesn't cover the salary: stability matters most in a new colony.
     */
    static int adminsNeeded(CAData d) {
        int back = 0;
        for (String id : d.autonomous().keySet()) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m != null && !m.isPlayerOwned()) back++;
        }
        return Math.max(0, overLimit() + back);
    }

    /** "1 more colony than your colony limit (...)" or, when it's colonies waiting to come back from autonomy, that. */
    static String limitText(int need) {
        int over = Math.max(0, Math.min(need, overLimit()));
        int back = need - over;
        String s = over > 0 ? over + " more " + (over == 1 ? "colony" : "colonies") + " than your colony limit (each costs every colony you govern "
                + Math.round(Misc.getOutpostPenalty()) + " stability)" : "";
        if (back > 0) s += (s.isEmpty() ? "" : ", and ") + back + " autonomous " + (back == 1 ? "colony" : "colonies")
                + " Autopilot takes back once there's room under your colony limit";
        return s;
    }

    /** Credits a month that changing m's stability by delta is worth, from raw (unclamped) stability. */
    static float stabilityWorth(MarketAPI m, float raw, float delta) {
        float cur = Math.max(0f, Math.min(10f, raw)), next = Math.max(0f, Math.min(10f, raw + delta));
        return fullIncome(m) * (Math.min(next, 5f) - Math.min(cur, 5f)) / 5f + OTHER_STAB * (next - cur);
    }

    /** m's gross income without the stability cut, which only applies below 5. */
    static float fullIncome(MarketAPI m) {
        float mult = PopulationAndInfrastructure.getIncomeStabilityMult(m.getPrevStability());
        return Math.max(0f, m.getGrossIncome()) / Math.max(0.2f, mult);
    }

    /** What p's skills add at m: Industrial Planning's +1 to every commodity the colony's industries make. Other mods' admin skills count as nothing. */
    static float skillWorth(MarketAPI m, PersonAPI p) {
        if (p == null || p.getStats() == null || p.getStats().getSkillLevel("industrial_planning") <= 0f) return 0f;
        Set<String> made = new HashSet<String>();
        for (Industry ind : m.getIndustries()) {
            if (ind.isHidden() || !ind.isFunctional()) continue;
            for (MutableCommodityQuantity q : ind.getAllSupply()) if (q.getQuantity().getModifiedInt() > 0) made.add(q.getCommodityId());
        }
        float v = 0f;
        for (String c : made) {
            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(c);
            if (spec != null) v += spec.getBasePrice() * CAEngine.unitValue;
        }
        return v;
    }

    /**
     * Credits a month m gains from being governed by admin rather than by you: skills, and the colony limit's stability on m
     * and on every other colony you govern. A big, productive colony gains far more than a small one.
     */
    static float adminWorth(MarketAPI m, PersonAPI admin) {
        float v = skillWorth(m, admin) - skillWorth(m, Global.getSector().getPlayerPerson());
        boolean youNow = governedByYou(m);
        float per = Misc.getOutpostPenalty();
        List<MarketAPI> yours = new ArrayList<MarketAPI>();
        for (MarketAPI o : Misc.getPlayerMarkets(false)) if (o != m && governedByYou(o)) yours.add(o);
        int over = yours.size() + 1 - maxGoverned();   // with m governed by you
        // stabilities as they'd be with m governed by you, then the change an administrator makes
        v += stabilityWorth(m, m.getStability().getModifiedValue() - (youNow ? 0f : per * over), per * over);
        for (MarketAPI o : yours) v += stabilityWorth(o, o.getStability().getModifiedValue() - (youNow ? 0f : per), per);
        return v;
    }

    /** An administrator who has arrived (past the hiring delay) and governs nothing, or null. */
    static AdminData idleAdmin(CAData d) {
        for (AdminData a : Global.getSector().getCharacterData().getAdmins()) {
            if (a.getMarket() != null || a.getPerson() == null || a.getPerson().isAICore()) continue;
            Long hired = d.adminTransit().get(a.getPerson().getId());
            if (hired != null && Global.getSector().getClock().getElapsedDaysSince(hired) < 30f) continue;   // still travelling
            return a;
        }
        return null;
    }

    /**
     * Places an idle administrator where they add the most, if that beats the 90% of salary that starts with the post, or
     * anyway while the colony limit needs one (adminsNeeded).
     */
    static boolean assignAdmin(CAData d, List<MarketAPI> markets) {
        List<AdminData> idle = new ArrayList<AdminData>();
        for (AdminData a : Global.getSector().getCharacterData().getAdmins()) {
            if (a.getMarket() != null || a.getPerson() == null || a.getPerson().isAICore()) continue;
            Long hired = d.adminTransit().get(a.getPerson().getId());
            if (hired != null && Global.getSector().getClock().getElapsedDaysSince(hired) < 30f) continue;   // still travelling
            d.adminTransit().remove(a.getPerson().getId());
            idle.add(a);
        }
        if (idle.isEmpty()) return false;
        List<MarketAPI> open = selfGoverned(markets);
        if (open.isEmpty()) return false;
        float idleMult = idleSalaryMult();
        int need = adminsNeeded(d);
        AdminData bestA = null;
        MarketAPI bestM = null;
        float bestNet = need > 0 ? -Float.MAX_VALUE : 0f, bestWorth = 0f;
        for (AdminData a : idle) {
            float cost = Misc.getAdminSalary(a.getPerson()) * (1f - idleMult);
            for (MarketAPI m : open) {
                float w = adminWorth(m, a.getPerson());
                if (w - cost > bestNet) { bestNet = w - cost; bestWorth = w; bestA = a; bestM = m; }
            }
        }
        if (bestA == null) {
            for (AdminData a : idle) {
                float salary = Misc.getAdminSalary(a.getPerson());
                if (CALog.once("admin:idle:" + a.getPerson().getId()))
                    CALog.add("Administrator " + a.getPerson().getNameString() + " stays unassigned: no Auto colony you govern gains more than their "
                            + Misc.getDGSCredits(salary) + " a month. Unassigned they cost " + Misc.getDGSCredits(salary * idleMult) + "; dismissing them is up to you");
            }
            return false;
        }
        try {
            bestM.setAdmin(bestA.getPerson());
            bestA.setMarket(bestM);
            CALog.forget("admin:idle:" + bestA.getPerson().getId());
            CALog.forget("admin:need");
            CALog.add(bestM.getName() + ": assigned administrator " + bestA.getPerson().getNameString() + " (" + (need > 0 ? limitText(need) + "; "
                    : "") + "adds about " + Misc.getDGSCredits(bestWorth) + " a month, salary " + Misc.getDGSCredits(Misc.getAdminSalary(bestA.getPerson())) + ")", true);
            return true;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "admin assignment failed at " + bestM.getName(), t);
            return false;
        }
    }

    /** Moves an administrator between Auto colonies when a colony you govern yourself gains clearly more from them. */
    static boolean moveAdmin(List<MarketAPI> markets) {
        List<MarketAPI> open = selfGoverned(markets);
        if (open.isEmpty()) return false;
        AdminData bestA = null;
        MarketAPI bestM = null;
        float bestGain = 0f;
        for (MarketAPI from : markets) {
            AdminData a = adminAt(from);
            if (a == null || a.getPerson() == null || a.getPerson().isAICore()) continue;
            float here = adminWorth(from, a.getPerson());
            float margin = Math.max(MOVE_MARGIN, 0.25f * Misc.getAdminSalary(a.getPerson()));
            for (MarketAPI to : open) {
                float gain = adminWorth(to, a.getPerson()) - here;
                if (gain > margin && gain > bestGain) { bestGain = gain; bestA = a; bestM = to; }
            }
        }
        if (bestA == null) return false;
        MarketAPI from = bestA.getMarket();
        try {
            from.setAdmin(Global.getSector().getPlayerPerson());
            bestM.setAdmin(bestA.getPerson());
            bestA.setMarket(bestM);
            CALog.add("Moved administrator " + bestA.getPerson().getNameString() + " from " + from.getName() + " to " + bestM.getName()
                    + ": about " + Misc.getDGSCredits(bestGain) + " a month more there", true);
            return true;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "admin move to " + bestM.getName() + " failed", t);
            return false;
        }
    }

    static final float HIRE_FEE = 25000f;

    /** Colonies the player still governs personally (no administrator). */
    static List<MarketAPI> selfGoverned(List<MarketAPI> markets) {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : markets) if (governedByYou(m)) out.add(m);
        return out;
    }

    /**
     * Hires an administrator on offer at a non-hostile market, the way the vanilla hire dialog does, when an Auto colony you
     * govern yourself would gain more from them than their salary plus the hiring cost spread over three years, and no idle
     * administrator is available or on the way; or, while the colony limit needs administrators (adminsNeeded) and not
     * enough are idle or on the way, whatever the money says, past the monthly budget if need be but never below the credit
     * reserve. Picks the candidate and colony that come out furthest ahead. Optional 30-day arrival delay.
     */
    static boolean hireAdmin(CAData d, CASettings s, List<MarketAPI> markets) {
        List<MarketAPI> open = selfGoverned(markets);
        int need = adminsNeeded(d);
        if (open.isEmpty()) {
            if (need > 0 && CALog.once("admin:need:none:" + need))
                CALog.add("Administrators needed: " + limitText(need) + ". But none of your Auto colonies is governed by you, so Autopilot has nowhere to put an "
                        + "administrator. Put one on a colony yourself, or switch a colony you govern to Auto", true);
            return false;
        }
        int idleOrComing = 0;
        for (AdminData a : Global.getSector().getCharacterData().getAdmins()) if (a.getMarket() == null) idleOrComing++;
        if (idleOrComing >= (need > 0 ? Math.min(need, open.size()) : open.size())) return false;
        int max = (int) Global.getSector().getPlayerStats().getAdminNumber().getModifiedValue();
        if (Global.getSector().getCharacterData().getAdmins().size() >= max) {
            if (need > 0 && CALog.once("admin:need:max:" + max))
                CALog.add("Administrators needed: " + limitText(need) + ". But you already have as many as you can employ (" + max + ")", true);
            return false;
        }
        float fee = CABudget.fee(s, HIRE_FEE);
        com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.AvailableOfficer best = null;
        com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent bestEvent = null;
        MarketAPI bestAt = null, bestFor = null;
        float bestNet = -Float.MAX_VALUE, bestWorth = 0f;
        for (MarketAPI src : Global.getSector().getEconomy().getMarketsCopy()) {
            if (src.getFaction() == null || src.getFaction().isHostileTo(com.fs.starfarer.api.impl.campaign.ids.Factions.PLAYER)) continue;
            if (src.getCommDirectory() == null) continue;
            for (com.fs.starfarer.api.campaign.CommDirectoryEntryAPI e : src.getCommDirectory().getEntriesCopy()) {
                if (!(e.getEntryData() instanceof PersonAPI)) continue;
                PersonAPI p = (PersonAPI) e.getEntryData();
                Object ref = p.getMemoryWithoutUpdate().get("$ome_eventRef");
                if (!(ref instanceof com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent)) continue;
                com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent ev = (com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent) ref;
                com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.AvailableOfficer ao = ev.getAdmin(p.getId());
                if (ao == null) continue;
                for (MarketAPI m : open) {
                    float w = adminWorth(m, p);
                    float net = w - ao.salary - (ao.hiringBonus + fee) / 36f;   // hiring cost spread over three years, like a build
                    if (net > bestNet) { bestNet = net; bestWorth = w; best = ao; bestEvent = ev; bestAt = src; bestFor = m; }
                }
            }
        }
        if (best == null) {
            if (need > 0 && CALog.once("admin:need:" + Global.getSector().getClock().getMonth()))
                CALog.add("Administrators needed: " + limitText(need) + ". None is for hire at a market that isn't hostile to you. "
                        + "New ones appear in markets' comm directories over time; Autopilot hires one as soon as there is", true);
            return false;
        }
        if (bestNet <= 0f && need <= 0) {
            if (CALog.once("admin:skip:" + best.person.getId()))
                CALog.add("Not hiring an administrator: the best on offer (" + best.person.getNameString() + " at " + bestAt.getName() + ", salary "
                        + Misc.getDGSCredits(best.salary) + " a month) would add about " + Misc.getDGSCredits(bestWorth) + " a month at " + bestFor.getName()
                        + ", less than they cost");
            return false;
        }
        float total = best.hiringBonus + fee;
        if (need > 0 ? CABuild.credits() - s.reserve < total : total > CABudget.available(s) || CABuild.credits() < total) {
            if (CALog.once("admin:" + best.person.getId()))
                CALog.add("An administrator is for hire at " + bestAt.getName() + " (" + Misc.getDGSCredits(best.hiringBonus) + " + fee), waiting for budget");
            return false;
        }
        try {
            best.person.setPostId(com.fs.starfarer.api.impl.campaign.ids.Ranks.POST_ADMINISTRATOR);
            bestEvent.removeAvailable(best);
            Global.getSector().getCharacterData().addAdmin(best.person);
            Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(best.hiringBonus);
            CABudget.charge(s, best.hiringBonus, fee, best.salary);
            if (s.adminDelay) d.adminTransit().put(best.person.getId(), Global.getSector().getClock().getTimestamp());
            CALog.add("Hired administrator " + best.person.getNameString() + " from " + bestAt.getName() + " for " + Misc.getDGSCredits(best.hiringBonus)
                    + (fee > 0 ? " + " + Misc.getDGSCredits(fee) + " fee" : "") + ", salary " + Misc.getDGSCredits(best.salary) + "/month, for "
                    + bestFor.getName() + (need > 0 ? ": " + limitText(need) : " (adds about " + Misc.getDGSCredits(bestWorth) + " a month)")
                    + (s.adminDelay ? "; arrives in 30 days" : ""), true);
            return true;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "administrator hire failed", t);
            return false;
        }
    }
}
