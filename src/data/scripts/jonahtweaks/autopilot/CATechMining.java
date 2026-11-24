package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.listeners.CustomProductionListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;
import com.fs.starfarer.api.impl.campaign.intel.misc.ProductionReportIntel;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.3.16.6: with Slightly Better Tech-Mining on (CAMods.sbtm), Tech-Mining is a plan step at the Auto colony with the best
 * ruins: SBTM's ruins factor (vast 2.5, extensive 1.5, widespread 1, scattered 0.5) times what's left of them
 * ($core_techMiningMult, 1 until mined), the bigger colony on a tie. As a plan step it stays: the endgame's replacements,
 * pruning and the over-limit cleanup leave it (CAEndgame.keepsPlanBonus), and a removal mark on it there doesn't hold the
 * build back (CAPlanner). Without SBTM nothing changes. Vanilla API only (SBTM is read through the industry spec).
 */
public final class CATechMining {
    private CATechMining() { }

    static final String ID = "techmining";

    /** Pure: a colony's ruins score, 0 without ruins. */
    static float score(boolean vast, boolean extensive, boolean widespread, boolean scattered, float left) {
        if (!vast && !extensive && !widespread && !scattered) return 0f;
        return CAMods.sbtmRuins(vast, extensive, widespread) * Math.max(0f, left);
    }

    static float score(MarketAPI m) {
        float left = 1f;
        try { if (m.getMemoryWithoutUpdate().contains(TechMining.TECH_MINING_MULT)) left = m.getMemoryWithoutUpdate().getFloat(TechMining.TECH_MINING_MULT); }
        catch (Throwable ignored) { }
        return score(m.hasCondition("ruins_vast"), m.hasCondition("ruins_extensive"), m.hasCondition("ruins_widespread"), m.hasCondition("ruins_scattered"), left);
    }

    /** The Auto colony with the best ruins, or null (SBTM off, or no Auto colony with ruins). */
    static MarketAPI site() {
        if (!CAMods.sbtm()) return null;
        MarketAPI best = null;
        float bestScore = 0f;
        for (MarketAPI m : CAItems.autoMarkets()) {
            float sc = score(m);
            if (sc <= 0f) continue;
            if (best == null || sc > bestScore || (sc == bestScore && m.getSize() > best.getSize())) { best = m; bestScore = sc; }
        }
        return best;
    }

    /** The plan step at the best-ruins colony. */
    static List<CAPlan.Step> stepsFor(MarketAPI m) {
        MarketAPI s = m == null ? null : site();
        if (s == null || !s.getId().equals(m.getId())) return Collections.emptyList();
        return Collections.singletonList(new CAPlan.Step(CAPlan.Kind.BUILD, ID, "Tech-Mining (best ruins)"));
    }

    /** Tech-Mining at the best-ruins colony while SBTM is on. */
    public static boolean isStep(MarketAPI m, String id) {
        if (m == null || !ID.equals(id)) return false;
        MarketAPI s = site();
        return s != null && s.getId().equals(m.getId());
    }

    // ---------------- 0.3.16.7: Tech-Mining finds to Autopilot Storage ----------------

    /** Pure: a find goes to Autopilot Storage: the setting is on and it's a colony item (an installable effect with targets). */
    static boolean moves(boolean keep, boolean colonyItem) { return !keep && colonyItem; }

    /**
     * Each month, after the game puts the colonies' finds in the gathering point's Storage (CoreScript, then
     * CustomProductionListener): the colony items in Tech-Mining's share go on to that colony's Autopilot Storage, where
     * CAItems installs them. Blueprints, weapons, cores and everything else stay in Storage. Setting: Tech-Mining finds to
     * Autopilot Storage (CASettings.techFindsKeep). Any Tech-Mining, with or without SBTM.
     */
    public static class Finds implements CustomProductionListener {
        /** Adds the listener unless this session has it (transient: addListener(x, true)). */
        public static void ensure() {
            ListenerManagerAPI lm = Global.getSector().getListenerManager();
            if (!lm.hasListenerOfClass(Finds.class)) lm.addListener(new Finds(), true);
        }

        @Override
        public void reportCustomProductionGenerated(MonthlyReport report, ProductionReportIntel intel) {
            try { move(intel); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Tech-Mining finds could not be moved", t); }
        }
    }

    static void move(ProductionReportIntel intel) {
        CASettings s = CAData.settings();
        if (s == null || s.techFindsKeep || intel == null || intel.getData() == null) return;
        MarketAPI gp = intel.getGatheringPoint();
        if (gp == null || !CAStores.holds(gp)) return;
        CargoAPI storage = Misc.getStorageCargo(gp);
        if (storage == null) return;
        Set<String> titles = new HashSet<String>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            Industry ind = m.getIndustry(ID);
            if (ind != null) titles.add(ind.getCargoTitleForGatheringPoint());
        }
        CargoAPI to = CAStores.of(gp);
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, CargoAPI> e : intel.getData().data.entrySet()) {
            if (e.getValue() == null || !titles.contains(e.getKey())) continue;
            for (CargoStackAPI st : e.getValue().getStacksCopy()) {
                if (!st.isSpecialStack()) continue;
                SpecialItemSpecAPI spec = st.getSpecialItemSpecIfSpecial();
                SpecialItemData data = st.getSpecialDataIfSpecial();
                if (spec == null || data == null) continue;
                boolean colonyItem = ItemEffectsRepo.ITEM_EFFECTS.get(spec.getId()) != null && spec.getParams() != null && !spec.getParams().isEmpty();
                if (!moves(s.techFindsKeep, colonyItem)) continue;
                float n = Math.min(st.getSize(), storage.getQuantity(CargoAPI.CargoItemType.SPECIAL, data));
                if (n < 1f) continue;
                storage.removeItems(CargoAPI.CargoItemType.SPECIAL, data, n);
                to.addSpecial(data, n);
                names.add((n > 1f ? Math.round(n) + " " : "") + spec.getName());
            }
        }
        if (!names.isEmpty()) CALog.did(gp.getName() + ": Tech-Mining found " + CAContracts.join(names) + ". Moved to Autopilot Storage.");
    }
}
