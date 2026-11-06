package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.changeling.industry.SubIndustryAPI;
import indevo.industries.changeling.industry.SubIndustryData;
import indevo.industries.changeling.industry.SwitchableIndustryAPI;
import indevo.industries.embassy.AmbassadorItemHelper;
import indevo.industries.embassy.listeners.AmbassadorItemTrackerPlugin;
import indevo.industries.embassy.listeners.AmbassadorPersonManager;
import indevo.industries.senate.conditions.EdictAPI;
import indevo.items.specialitemdata.AmbassadorItemData;

import java.util.*;

/**
 * Industrial Evolution automation for Auto colonies: ambassadors (hire and install), VPC chips, and Senate edicts.
 * Only loaded when Industrial Evolution is enabled; every call into its code is guarded.
 */
public class CAIndEvo {
    static final String AMB = "IndEvo_amb";
    static final float HIRE_FEE = 25000f, MOVE_FEE = 10000f;

    public static void run(List<MarketAPI> markets) {
        CASettings s = CAData.settings();
        try { if (!s.disableAmbassadors && installAmbassador(s, markets)) return; } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "ambassador install failed", t); }
        try { if (!s.disableAmbassadors && hireAmbassador(s, markets)) return; } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "ambassador hire failed", t); }
        try { if (!s.disableVPC && installVPC(s, markets)) return; } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "VPC install failed", t); }
        try { if (!s.disableSpecialties && specialize(s, markets)) return; } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "specialty step failed", t); }
        try { if (!s.disableEdicts) applyEdict(s, markets); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "edict step failed", t); }
    }

    static CargoAPI storageOf(MarketAPI m) {
        SubmarketAPI sub = m.getSubmarket("storage");
        return sub == null ? null : sub.getCargo();
    }

    static Industry emptyEmbassy(MarketAPI m) {
        Industry e = m.getIndustry("IndEvo_embassy");
        if (e == null || !e.isFunctional() || e.getSpecialItem() != null) return null;
        if (CAData.get().colony(m.getId()).locks.contains("IndEvo_embassy")) return null;
        return e;
    }

    static Set<String> embassyFactions() {
        Set<String> out = new HashSet<String>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            Industry e = m.getIndustry("IndEvo_embassy");
            if (e == null || e.getSpecialItem() == null) continue;
            FactionAPI f = AmbassadorItemHelper.getFactionForItem(e.getSpecialItem());
            if (f != null) out.add(f.getId());
        }
        return out;
    }

    /** Installs an ambassador waiting in a colony's normal Storage (Industrial Evolution only tracks them there). */
    static boolean installAmbassador(CASettings s, List<MarketAPI> markets) {
        CAData d = CAData.get();

        for (MarketAPI src : Misc.getPlayerMarkets(false)) {
            CargoAPI st = storageOf(src);
            if (st == null) continue;
            for (CargoStackAPI stack : st.getStacksCopy()) {
                if (!stack.isSpecialStack() || stack.getSpecialDataIfSpecial() == null || !AMB.equals(stack.getSpecialDataIfSpecial().getId())) continue;
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                PersonAPI p = data instanceof AmbassadorItemData ? ((AmbassadorItemData) data).getPerson() : null;
                Long hired = p == null ? null : d.ambTransit().get(p.getId());
                if (hired != null && Global.getSector().getClock().getElapsedDaysSince(hired) < 30f) continue;
                Industry target = src.isPlayerOwned() && markets.contains(src) ? emptyEmbassy(src) : null;
                MarketAPI tm = src;
                if (target == null) for (MarketAPI m : markets) { target = emptyEmbassy(m); tm = m; if (target != null) break; }
                if (target == null || !target.wantsToUseSpecialItem(data)) continue;
                float fee = tm == src ? 0f : CABudget.fee(s, MOVE_FEE);
                if (fee > 0 && !CAItems.canPay(s, fee)) return false;
                target.setSpecialItem(data);
                st.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1f);
                if (p != null) d.ambTransit().remove(p.getId());
                CABudget.charge(s, 0f, fee, 0f);
                FactionAPI f = AmbassadorItemHelper.getFactionForItem(data);
                CALog.add(tm.getName() + ": installed the " + (f == null ? "" : f.getDisplayName() + " ") + "ambassador in the Embassy");
                return true;
            }
        }
        return false;
    }

    static int waitingAmbassadors() {
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CargoAPI st = storageOf(m);
            if (st == null) continue;
            for (CargoStackAPI stack : st.getStacksCopy())
                if (stack.isSpecialStack() && stack.getSpecialDataIfSpecial() != null && AMB.equals(stack.getSpecialDataIfSpecial().getId())) n += (int) stack.getSize();
        }
        return n;
    }

    /** Hires an ambassador remotely, the way Industrial Evolution's own dialog does, for an empty Embassy. */
    static boolean hireAmbassador(CASettings s, List<MarketAPI> markets) {
        MarketAPI target = null; Industry emb = null;
        int empty = 0;
        for (MarketAPI m : markets) { Industry e = emptyEmbassy(m); if (e != null) { empty++; if (target == null) { target = m; emb = e; } } }
        if (target == null || empty <= waitingAmbassadors()) return false;
        Set<String> taken = embassyFactions();
        Set<String> allowed = AmbassadorItemHelper.getAllowedFactionID();
        FactionAPI player = Global.getSector().getPlayerFaction();
        float stopAt = CASettings.STOP_VALUES[Math.max(0, Math.min(3, s.ambassadorStopAt))];
        // faction strength (total market size) and nearness (markets within 10 ly of a player colony)
        Map<String, Float> strength = new HashMap<String, Float>(), near = new HashMap<String, Float>();
        List<MarketAPI> mine = Misc.getPlayerMarkets(false);
        for (MarketAPI o : Global.getSector().getEconomy().getMarketsCopy()) {
            if (o.isPlayerOwned() || o.getFaction() == null) continue;
            String fid = o.getFactionId();
            strength.put(fid, (strength.containsKey(fid) ? strength.get(fid) : 0f) + o.getSize());
            for (MarketAPI pm : mine) {
                if (pm.getPrimaryEntity() == null || o.getPrimaryEntity() == null) continue;
                if (Misc.getDistanceLY(pm.getPrimaryEntity(), o.getPrimaryEntity()) <= 10f) { near.put(fid, (near.containsKey(fid) ? near.get(fid) : 0f) + 1f); break; }
            }
        }
        MarketAPI src = null; float bestScore = -1e9f;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.isPlayerOwned() || m.getFaction() == null) continue;
            String fid = m.getFactionId();
            if (taken.contains(fid) || (allowed != null && !allowed.contains(fid)) || m.getFaction().isHostileTo(Factions.PLAYER)) continue;
            int prio = s.priorityOf(fid);
            if (prio == 0) continue;                                   // Ignore
            if (s.ambassadorFocus == 3 && prio != 2) continue;          // Priority list only
            float rel = player.getRelationship(fid);
            if (rel >= stopAt) continue;                               // already high enough
            if (!AmbassadorPersonManager.hasAmbassador(m)) continue;
            float score;
            if (s.ambassadorFocus == 1) score = strength.containsKey(fid) ? strength.get(fid) : 0f;
            else if (s.ambassadorFocus == 2) score = near.containsKey(fid) ? near.get(fid) : 0f;
            else score = -rel * 100f;
            if (prio == 2) score += 100000f;                           // High always wins
            if (score > bestScore) { bestScore = score; src = m; }
        }
        if (src == null) return false;
        float fee = CABudget.fee(s, HIRE_FEE);
        if (!CAItems.canPay(s, fee)) return false;
        PersonAPI person = AmbassadorPersonManager.getAmbassador(src);
        if (person == null) return false;
        AmbassadorItemData data = new AmbassadorItemData(AMB, null, person);
        Global.getSector().getListenerManager().addListener(new AmbassadorItemTrackerPlugin(person, data));
        AmbassadorPersonManager.removeAmbassadorFromMarket(src);
        CABudget.charge(s, 0f, fee, 0f);
        String who = src.getFaction().getDisplayName() + " ambassador " + person.getNameString();
        if (true) {   // always travels (2.37.0): a hire made from afar arrives in 30 days, never on the spot
            CargoAPI st = storageOf(target);
            if (st == null) { emb.setSpecialItem(data); CALog.add(target.getName() + ": hired and installed the " + who); return true; }
            st.addSpecial(data, 1f);
            CAData.get().ambTransit().put(person.getId(), Global.getSector().getClock().getTimestamp());
            CALog.add(target.getName() + ": hired the " + who + " from " + src.getName() + "; arrives in 30 days"
                    + (fee > 0 ? " (" + Misc.getDGSCredits(fee) + " fee)" : ""));
        } else {
            emb.setSpecialItem(data);
            CALog.add(target.getName() + ": hired and installed the " + who + (fee > 0 ? " (" + Misc.getDGSCredits(fee) + " fee)" : ""));
        }
        return true;
    }

    static final String[] VPC_TARGETS = {"IndEvo_AdManuf", "IndEvo_AdAssem", "IndEvo_ComForge"};

    /**
     * One chip a day into a free VPC building, building by building (2.39.0). A chip already in that colony's Autopilot
     * Storage goes in first; while one it can use is on its way there, nothing else is shipped for it; otherwise one is
     * shipped from another colony. Before, chips went in storage order, so one that had arrived kept losing its turn to the
     * next chip at Hot Springs, which shipped another, a different kind each day: every kind twice for one Commodity Forge.
     */
    static boolean installVPC(CASettings s, List<MarketAPI> markets) {
        for (String tid : VPC_TARGETS) {
            if (tid.equals("IndEvo_ComForge") && s.disableForgeVPC) continue;
            for (MarketAPI m : markets) {
                Industry ind = m.getIndustry(tid);
                if (ind == null || !ind.isFunctional() || ind.getSpecialItem() != null) continue;
                if (CAData.get().colony(m.getId()).locks.contains(tid)) continue;
                CargoAPI here = CAStores.of(m);
                for (CargoStackAPI stack : here.getStacksCopy()) {   // one that's already here
                    SpecialItemData data = chip(stack);
                    if (data == null || !ind.wantsToUseSpecialItem(data)) continue;
                    float fee = CABudget.fee(s, MOVE_FEE);
                    if (!CAItems.canPay(s, fee)) return false;
                    ind.setSpecialItem(new SpecialItemData(data.getId(), data.getData()));
                    here.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1f);
                    CABudget.charge(s, 0f, fee, 0f);
                    CALog.add(m.getName() + ": installed " + stack.getDisplayName() + " in " + ind.getCurrentName()
                            + (tid.equals("IndEvo_ComForge") ? " (the forge uses the chip up after three production cycles)" : ""));
                    return true;
                }
                if (chipComing(m, ind)) continue;   // it goes in once it's here
                for (CAStores.Held held : CAStores.stacks()) {   // stored at another colony: shipped here first (2.37.0)
                    if (held.market == m) continue;
                    SpecialItemData data = chip(held.stack);
                    if (data == null || !ind.wantsToUseSpecialItem(data)) continue;
                    if (CAStores.ship(held.market, m, null, data, 1, "for " + ind.getCurrentName())) return true;
                    break;   // couldn't pay for shipping today
                }
            }
        }
        return false;
    }

    static SpecialItemData chip(CargoStackAPI stack) {
        SpecialItemData data = stack.isSpecialStack() && stack.getSize() >= 1f ? stack.getSpecialDataIfSpecial() : null;
        return data != null && data.getId().startsWith("IndEvo_vpc_") ? data : null;
    }

    /** A chip this building can use is on its way to the colony. */
    static boolean chipComing(MarketAPI m, Industry ind) {
        for (CAStores.Shipment sh : CAData.get().shipments()) {
            if (sh.special == null || !m.getId().equals(sh.to) || !sh.special.getId().startsWith("IndEvo_vpc_")) continue;
            try { if (ind.wantsToUseSpecialItem(sh.special)) return true; } catch (Throwable t) { return true; }
        }
        return false;
    }

    // ---------------- edicts ----------------

    static boolean hasEdict(MarketAPI m) {
        for (MarketConditionAPI c : m.getConditions()) if (c.getId().startsWith("IndEvo_edict")) return true;
        return false;
    }

    /** The edict this colony should run, or null. Harsh edicts (forced labour, cleansing, single-child, labour limits) are never automated. */
    static String wantedEdict(MarketAPI m, CAData.Profile prof) {
        if (CAThreat.isHigh(m)) return "IndEvo_edict_lockdown";
        if (prof == CAData.Profile.WAR) return "IndEvo_edict_deadlines";
        if (prof == CAData.Profile.GROWTH && m.getSize() < 5) return "IndEvo_edict_forced_reloc";
        if (prof == CAData.Profile.INCOME || prof == CAData.Profile.BALANCED || prof == CAData.Profile.SELF) return "IndEvo_edict_ind_incent";
        return null;
    }

    static boolean applyEdict(CASettings s, List<MarketAPI> markets) {
        for (MarketAPI m : markets) {
            if (hasEdict(m)) continue;
            String id = wantedEdict(m, CAData.get().colony(m.getId()).profile);
            if (id == null) continue;
            String key = m.getId() + ":" + id;
            try {
                MarketConditionSpecAPI spec = Global.getSettings().getMarketConditionSpec(id);
                if (spec == null) continue;
                Object o = Global.getSettings().getInstanceOfScript(spec.getScriptClass());
                if (!(o instanceof EdictAPI)) continue;
                EdictAPI e = (EdictAPI) o;
                String reason = e.getUnavailableReason(m);
                if (reason != null && !reason.isEmpty()) {
                    if (CALog.once(key)) CALog.add(m.getName() + ": " + spec.getName() + " unavailable: " + reason);
                    continue;
                }
                m.addCondition(id);
                m.reapplyConditions();
                CALog.forget(key);
                CALog.add(m.getName() + ": enacted " + spec.getName() + " (effects estimated; edicts are never removed automatically)");
                return true;
            } catch (Throwable t) {
                if (CALog.once(key)) CADiagnostics.LOG.warn(CADiagnostics.TAG + "edict check failed for " + id + " at " + m.getName(), t);
            }
        }
        return false;
    }

    // ---------------- specialties (Specialized Mining / Refining) ----------------

    /** Monthly value of a commodity this colony could output, using deposits and empire shortages. */
    static float commodityValue(MarketAPI m, String com, java.util.Map<String, Integer> deficit) {
        int units;
        if (com.equals("metals")) units = m.getSize();
        else if (com.equals("rare_metals")) units = m.getSize() - 2;
        else {
            int mod = -99;
            for (MarketConditionAPI c : m.getConditions())
                if (com.equals(CAEngine.DEPOSIT.get(c.getId()))) mod = CAEngine.DEPOSIT_MOD.get(c.getId());
            if (mod == -99) return 0f;
            units = m.getSize() + CAEngine.BASE_MOD.get(com) + mod;
        }
        if (units <= 0) return 0f;
        Integer d = deficit.get(com);
        float need = (d != null && d > 0) ? 1.5f : 1f;
        return Global.getSettings().getCommoditySpec(com).getBasePrice() * CAEngine.unitValue * units * need;
    }

    /** Estimated value of a specialty: the specialised commodity at about 1.5x, or everything at 1x for the base type. */
    static float specialtyValue(MarketAPI m, String sub, java.util.Map<String, Integer> deficit) {
        if (sub.equals("base_mining"))
            return commodityValue(m, "ore", deficit) + commodityValue(m, "rare_ore", deficit) + commodityValue(m, "volatiles", deficit) + commodityValue(m, "organics", deficit);
        if (sub.equals("base_refining")) return commodityValue(m, "metals", deficit) + commodityValue(m, "rare_metals", deficit);
        String com = sub.equals("ore_mining") ? "ore" : sub.equals("rare_mining") ? "rare_ore" : sub.equals("volatile_mining") ? "volatiles"
                : sub.equals("organics_mining") ? "organics" : sub.equals("ore_refining") ? "metals" : sub.equals("rare_refining") ? "rare_metals" : null;
        return com == null ? 0f : 1.5f * commodityValue(m, com, deficit);
    }

    static boolean specialize(CASettings s, List<MarketAPI> markets) {
        java.util.Map<String, Integer> deficit = CAEngine.empireDeficits();
        for (MarketAPI m : markets) {
            for (String id : new String[]{"IndEvo_SwitchableMining", "IndEvo_SwitchableRefining"}) {
                Industry ind = m.getIndustry(id);
                if (!(ind instanceof SwitchableIndustryAPI) || !ind.isFunctional() || ind.isBuilding() || ind.isUpgrading()) continue;
                if (CAData.get().colony(m.getId()).locks.contains(id)) continue;
                SwitchableIndustryAPI sw = (SwitchableIndustryAPI) ind;
                if (!sw.canChange() || sw.getCurrent() == null) continue;
                float current = specialtyValue(m, sw.getCurrent().getId(), deficit);
                SubIndustryData best = null; float bestV = current * 1.15f;
                for (SubIndustryData data : sw.getIndustryList()) {
                    if (data.id.equals(sw.getCurrent().getId())) continue;
                    SubIndustryAPI inst = data.newInstance();
                    inst.init(ind);
                    if (!inst.isAvailableToBuild()) continue;
                    float v = specialtyValue(m, data.id, deficit);
                    if (v > bestV) { bestV = v; best = data; }
                }
                if (best == null) continue;
                float fee = CABudget.constructionFee(s, best.cost);
                if (best.cost + fee > CABudget.available(s) || CABuild.credits() < best.cost + fee) return false;
                sw.setCurrent(best.newInstance(), false);
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(best.cost);
                CABudget.charge(s, best.cost, fee, 0f);
                CALog.add(m.getName() + ": switched " + ind.getCurrentName() + " to " + best.name + " for " + Misc.getDGSCredits(best.cost)
                        + " (estimated " + CAEngine.credits(bestV) + "/month vs " + CAEngine.credits(current) + ")");
                return true;
            }
        }
        return false;
    }
}