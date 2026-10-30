package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.impl.campaign.econ.impl.InstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.events.HegemonyHostileActivityFactor;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/** Places items and AI cores from Autopilot Storage, and assigns idle administrators. Auto-mode colonies only. */
public class CAItems {
    static final float STANDARD_MOVE_FEE = 10000f;

    static final Map<String, Float> ITEM_VALUE = new HashMap<String, Float>();
    static {
        ITEM_VALUE.put("pristine_nanoforge", 100f); ITEM_VALUE.put("corrupted_nanoforge", 40f);
        ITEM_VALUE.put("synchrotron", 60f); ITEM_VALUE.put("catalytic_core", 50f);
        ITEM_VALUE.put("mantle_bore", 50f); ITEM_VALUE.put("plasma_dynamo", 50f);
        ITEM_VALUE.put("soil_nanites", 40f); ITEM_VALUE.put("biofactory_embryo", 40f);
        ITEM_VALUE.put("fullerene_spool", 40f); ITEM_VALUE.put("orbital_fusion_lamp", 30f);
        ITEM_VALUE.put("coronal_portal", 80f); ITEM_VALUE.put("cryoarithmetic_engine", 70f);
        ITEM_VALUE.put("drone_replicator", 40f); ITEM_VALUE.put("dealmaker_holosuite", 60f);
    }

    /** True when Autopilot Storage holds an Industrial Evolution VPC chip. */
    public static boolean storageHasVPC() {
        for (CargoStackAPI stack : CAData.get().storage().getStacksCopy())
            if (stack.isSpecialStack() && stack.getSpecialDataIfSpecial() != null && stack.getSpecialDataIfSpecial().getId().startsWith("IndEvo_vpc_")) return true;
        return false;
    }

    public static List<MarketAPI> autoMarkets() {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (CAData.get().colony(m.getId()).mode == CAData.Mode.AUTO) out.add(m);
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

    static float itemScore(String id, MarketAPI m, CAData.Profile p) {
        Float v = ITEM_VALUE.get(id);
        if (v == null) return 0f;
        float mult = 1f;
        boolean war = p == CAData.Profile.WAR, sec = p == CAData.Profile.SECURITY, inc = p == CAData.Profile.INCOME;
        if (war && (id.endsWith("nanoforge") || id.equals("cryoarithmetic_engine"))) mult = 2f;
        if (sec && (id.equals("drone_replicator") || id.equals("cryoarithmetic_engine"))) mult = 1.5f;
        if (inc && (id.equals("dealmaker_holosuite") || id.equals("fullerene_spool"))) mult = 1.5f;
        return v * mult * m.getSize();
    }

    /** One placement per day at most, so each move is visible in the log. */
    public static void run() {
        CAData d = CAData.get();
        CASettings s = d.settings;
        List<MarketAPI> markets = autoMarkets();
        if (markets.isEmpty()) return;
        if (placeItem(d, s, markets)) return;
        if (!s.disableStabilizers && s.allowRemovals && convertFuel(d, s, markets)) return;   // replaces a building, so only with Allow removals
        if (s.useAICores && placeCore(d, s, markets)) return;
        if (s.adminOptimal && assignAdmin(d, markets)) return;
        if (s.adminHiring) hireAdmin(d, s, markets);
    }

    static boolean placeItem(CAData d, CASettings s, List<MarketAPI> markets) {
        CargoAPI st = d.storage();
        for (CargoStackAPI stack : st.getStacksCopy()) {
            if (!stack.isSpecialStack() || stack.getSize() < 1f) continue;
            SpecialItemSpecAPI spec = stack.getSpecialItemSpecIfSpecial();
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (spec == null || data == null || spec.getSourceMod() != null) continue;   // vanilla items only for now
            InstallableItemEffect effect = ItemEffectsRepo.ITEM_EFFECTS.get(spec.getId());
            String params = spec.getParams();
            if (effect == null || params == null) continue;
            Set<String> targets = new HashSet<String>();
            for (String p : params.split(",")) targets.add(p.trim());
            Industry best = null; float bestScore = 0f; MarketAPI bestM = null;
            for (MarketAPI m : markets) {
                CAData.Colony c = d.colony(m.getId());
                for (Industry ind : m.getIndustries()) {
                    if (!targets.contains(ind.getId()) || ind.getSpecialItem() != null || !ind.isFunctional()) continue;
                    if (c.locks.contains(ind.getId())) continue;
                    try {
                        if (!effect.getUnmetRequirements(ind).isEmpty()) continue;
                    } catch (Throwable t) { CADiagnostics.quarantine(ind.getId(), "item requirement check", t); continue; }
                    float sc = itemScore(spec.getId(), m, c.profile);
                    if (sc > bestScore) { bestScore = sc; best = ind; bestM = m; }
                }
            }
            if (best == null) continue;
            float fee = CABudget.fee(s, STANDARD_MOVE_FEE);
            if (!canPay(s, fee)) return false;
            try {
                best.setSpecialItem(new SpecialItemData(data.getId(), data.getData()));
                st.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1f);
                CABudget.charge(s, 0f, fee, 0f);
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

    static final String[] CORES = {"alpha_core", "beta_core", "gamma_core"};
    static final int[] CORE_POINTS = {4, 2, 1};
    static final int SAFE_POINTS = 7;

    public static int aiPoints(MarketAPI m) {
        int total = 0;
        if (m.getAdmin() != null && m.getAdmin().isAICore()) total += 10;
        for (Industry ind : m.getIndustries()) {
            String id = ind.getAICoreId();
            if ("alpha_core".equals(id)) total += 4; else if ("beta_core".equals(id)) total += 2; else if ("gamma_core".equals(id)) total += 1;
        }
        return total;
    }

    public static float nextInspectionFP() {
        int defeated = 0;
        try { defeated = HegemonyHostileActivityFactor.getInspectionsDefeated(); } catch (Throwable ignored) { }
        return defeated <= 0 ? 150f : defeated == 1 ? 600f : 1400f;
    }

    public static boolean hegemonyDone() {
        try { return HegemonyHostileActivityFactor.isPlayerDefeatedHegemony(); } catch (Throwable t) { return false; }
    }

    /** Rough defense estimate in fleet points: station plus patrol fleets, scaled by fleet size. */
    public static float defenseFP(MarketAPI m) {
        float station = 0f, patrol = 0f;
        for (Industry ind : m.getIndustries()) {
            if (!ind.isFunctional()) continue;
            String id = ind.getId();
            if (id.startsWith("starfortress")) station = Math.max(station, 600f);
            else if (id.startsWith("battlestation")) station = Math.max(station, 300f);
            else if (id.startsWith("orbitalstation")) station = Math.max(station, 120f);
            else if (id.equals("highcommand")) patrol = Math.max(patrol, 300f);
            else if (id.equals("militarybase")) patrol = Math.max(patrol, 180f);
            else if (id.equals("patrolhq")) patrol = Math.max(patrol, 80f);
        }
        float fleetMult = 1f;
        try { fleetMult = Math.max(1f, m.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).computeEffective(0f)); }
        catch (Throwable ignored) { }
        return station + patrol * fleetMult;
    }

    /** The colony that should hold stacked cores, or null when none is strong enough. */
    public static MarketAPI fortress(List<MarketAPI> markets) {
        MarketAPI best = null; float bestFP = 0f;
        for (MarketAPI m : markets) { float fp = defenseFP(m); if (fp > bestFP) { bestFP = fp; best = m; } }
        if (best == null) return null;
        if (hegemonyDone() || bestFP >= 1.5f * nextInspectionFP()) return best;
        return null;
    }

    /** An Alpha core only goes where it can be taken out cleanly: vanilla buildings, never Population & Infrastructure or mod code. */
    static boolean alphaRemovable(Industry ind) {
        return ind.getSpec().getSourceMod() == null && CAEngine.isSupportedVanilla(ind.getId()) && !ind.getSpec().hasTag("population");
    }

    /** Core priority by building type; matches the vanilla id, or the type tag Ashes of the Domain puts on its own versions. */
    static int industryPriority(Industry ind) {
        String id = ind.getId();
        if (id.equals("orbitalworks") || is(ind, "heavyindustry")) return 0;
        if (is(ind, "refining") || is(ind, "fuelprod") || is(ind, "mining")) return 1;
        if (is(ind, "lightindustry") || is(ind, "farming") || is(ind, "aquaculture") || is(ind, "commerce")) return 2;
        if (id.equals("highcommand") || id.equals("militarybase") || id.startsWith("starfortress") || id.startsWith("battlestation")) return 3;
        return 4;
    }

    static boolean is(Industry ind, String type) {
        return ind.getId().equals(type) || ind.getSpec().hasTag(type) || ind.getSpec().hasTag("aotd_" + type);
    }

    static boolean placeCore(CAData d, CASettings s, List<MarketAPI> markets) {
        CargoAPI st = d.storage();
        MarketAPI fort = fortress(markets);
        for (int k = 0; k < CORES.length; k++) {
            if (st.getCommodityQuantity(CORES[k]) < 1f) continue;
            boolean alpha = k == 0;
            if (alpha && s.alphaMode == 2) continue;
            List<MarketAPI> order = new ArrayList<MarketAPI>(markets);
            if (fort != null) { order.remove(fort); order.add(0, fort); }
            for (MarketAPI m : order) {
                boolean unlimited = m == fort;
                if (!unlimited && aiPoints(m) + CORE_POINTS[k] > SAFE_POINTS) continue;
                CAData.Colony c = d.colony(m.getId());
                List<Industry> inds = new ArrayList<Industry>(m.getIndustries());
                Collections.sort(inds, new Comparator<Industry>() {
                    public int compare(Industry a, Industry b) { return industryPriority(a) - industryPriority(b); }
                });
                for (Industry ind : inds) {
                    if (ind.getAICoreId() != null || !ind.isFunctional() || c.locks.contains(ind.getId())) continue;
                    if (CADiagnostics.industryLevel(ind.getId()) != CADiagnostics.Level.SUPPORTED && !ind.getSpec().hasTag("population")) continue;
                    if (alpha && s.alphaMode == 0 && !alphaRemovable(ind)) continue;
                    float fee = CABudget.fee(s, STANDARD_MOVE_FEE);
                    if (!canPay(s, fee)) return false;
                    ind.setAICoreId(CORES[k]);
                    st.removeCommodity(CORES[k], 1f);
                    CABudget.charge(s, 0f, fee, 0f);
                    CALog.add(m.getName() + ": installed " + Global.getSettings().getCommoditySpec(CORES[k]).getName() + " in "
                            + ind.getCurrentName() + " (" + aiPoints(m) + " inspection points" + (unlimited ? ", fortress world" : "") + ")", true);
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------- administrators ----------------

    static int skillCount(AdminData a) {
        try { return a.getPerson().getStats().getSkillsCopy().size(); } catch (Throwable t) { return 0; }
    }

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
        Collections.sort(idle, new Comparator<AdminData>() {
            public int compare(AdminData a, AdminData b) { return skillCount(b) - skillCount(a); }
        });
        List<MarketAPI> open = new ArrayList<MarketAPI>();
        for (MarketAPI m : markets) if (m.getAdmin() == null || m.getAdmin().isPlayer()) open.add(m);
        if (open.isEmpty()) return false;
        Collections.sort(open, new Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) { return b.getSize() - a.getSize(); }
        });
        AdminData a = idle.get(0);
        MarketAPI m = open.get(0);
        try {
            m.setAdmin(a.getPerson());
            a.setMarket(m);
            CALog.add(m.getName() + ": assigned idle administrator " + a.getPerson().getNameString(), true);
            return true;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "admin assignment failed at " + m.getName(), t);
            return false;
        }
    }

    static final float HIRE_FEE = 25000f;

    /** Colonies the player still governs personally (no administrator). */
    static List<MarketAPI> selfGoverned(List<MarketAPI> markets) {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : markets) if (m.getAdmin() == null || m.getAdmin().isPlayer()) out.add(m);
        return out;
    }

    /**
     * Hires an administrator on offer at a non-hostile market, the way the vanilla hire dialog does, when an Auto colony
     * is still self-governed and no idle administrator is available or on the way. Optional 30-day arrival delay.
     */
    static boolean hireAdmin(CAData d, CASettings s, List<MarketAPI> markets) {
        List<MarketAPI> open = selfGoverned(markets);
        if (open.isEmpty()) return false;
        int idleOrComing = 0;
        for (AdminData a : Global.getSector().getCharacterData().getAdmins()) if (a.getMarket() == null) idleOrComing++;
        if (idleOrComing >= open.size()) return false;
        int max = (int) Global.getSector().getPlayerStats().getAdminNumber().getModifiedValue();
        if (Global.getSector().getCharacterData().getAdmins().size() >= max) return false;
        com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.AvailableOfficer best = null;
        com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent bestEvent = null;
        MarketAPI bestAt = null;
        for (MarketAPI src : Global.getSector().getEconomy().getMarketsCopy()) {
            if (src.getFaction() == null || src.getFaction().isHostileTo(com.fs.starfarer.api.impl.campaign.ids.Factions.PLAYER)) continue;
            if (src.getCommDirectory() == null) continue;
            for (com.fs.starfarer.api.campaign.CommDirectoryEntryAPI e : src.getCommDirectory().getEntriesCopy()) {
                if (!(e.getEntryData() instanceof com.fs.starfarer.api.characters.PersonAPI)) continue;
                com.fs.starfarer.api.characters.PersonAPI p = (com.fs.starfarer.api.characters.PersonAPI) e.getEntryData();
                Object ref = p.getMemoryWithoutUpdate().get("$ome_eventRef");
                if (!(ref instanceof com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent)) continue;
                com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent ev = (com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent) ref;
                com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.AvailableOfficer ao = ev.getAdmin(p.getId());
                if (ao == null) continue;
                int skills = 0;
                try { skills = p.getStats().getSkillsCopy().size(); } catch (Throwable ignored) { }
                int bestSkills = -1;
                if (best != null) try { bestSkills = best.person.getStats().getSkillsCopy().size(); } catch (Throwable ignored) { }
                if (best == null || skills > bestSkills || (skills == bestSkills && ao.hiringBonus < best.hiringBonus)) { best = ao; bestEvent = ev; bestAt = src; }
            }
        }
        if (best == null) return false;
        float fee = CABudget.fee(s, HIRE_FEE);
        float total = best.hiringBonus + fee;
        if (total > CABudget.available(s) || CABuild.credits() < total) {
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
                    + (fee > 0 ? " + " + Misc.getDGSCredits(fee) + " fee" : "") + ", salary " + Misc.getDGSCredits(best.salary) + "/month"
                    + (s.adminDelay ? "; arrives in 30 days" : ""), true);
            return true;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "administrator hire failed", t);
            return false;
        }
    }
}