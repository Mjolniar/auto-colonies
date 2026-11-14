package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 0.3.6.3: the station line, asked once per save before the first station. When a colony in Approve or Auto plans its first
 * station, no colony has one yet, the Stations row isn't set by you and two or more lines can be built there, station builds
 * wait (hold) and this pop-up lists each line with its strength (CAStations), price and upkeep. The answer sets the Stations
 * row as your choice. Escape is Pick by score.
 */
public class CAStationAsk implements InteractionDialogPlugin {
    static final String SCORE = "score";
    /** Station builds wait for the answer (CAEngine). Worked out on the daily pass. */
    public static boolean hold = false;
    static String colonyId = null;
    InteractionDialogAPI dialog;

    public static void reset() { hold = false; colonyId = null; }

    static boolean hasStation(MarketAPI m) {
        for (Industry i : m.getIndustries()) if (CAPlan.stationTier(i.getSpec()) > 0) return true;
        return false;
    }

    /** CASettings.STATION_IDS indexes (not Pick by score) whose first station this colony can build. */
    static List<Integer> openLines(MarketAPI m) {
        List<Integer> out = new ArrayList<Integer>();
        for (int v = 1; v < CASettings.STATION_IDS.length; v++) {
            String st = CASettings.stationStarter(CASettings.STATION_IDS[v]);
            if (st == null) continue;
            try { if (Global.getSettings().getIndustrySpec(st).getNewPluginInstance(m).isAvailableToBuild()) out.add(v); }
            catch (Throwable ignored) { }
        }
        return out;
    }

    /** Daily: whether to ask now. */
    public static void daily() {
        hold = false;
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (d.stationAsked) return;
        if (s.playerSet().contains("stationLine") || s.playerSet().contains("noStationPlan") || s.noStationPlan) { d.stationAsked = true; return; }
        List<MarketAPI> ms = Misc.getPlayerMarkets(false);
        for (MarketAPI m : ms) if (hasStation(m)) { d.stationAsked = true; return; }
        MarketAPI first = null;
        for (MarketAPI m : ms) {
            CAData.Mode mo = d.colony(m.getId()).mode;
            if ((mo == CAData.Mode.APPROVE || mo == CAData.Mode.AUTO) && !CAStations.stepsFor(m).isEmpty()) { first = m; break; }
        }
        if (first == null) return;
        if (openLines(first).size() < 2) { d.stationAsked = true; return; }   // nothing to choose between
        hold = true;
        colonyId = first.getId();
        if (!Global.getSector().hasTransientScript(Watch.class)) Global.getSector().addTransientScript(new Watch());
    }

    MarketAPI market() { return colonyId == null ? null : Global.getSector().getEconomy().getMarket(colonyId); }

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.setPromptText("Colony Autopilot");
        try {
            try { dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("illustrations", "orbital_construction", 640, 400)); }
            catch (Throwable ignored) { }
            MarketAPI m = market();
            TextPanelAPI t = dialog.getTextPanel();
            t.addPara((m == null ? "Your colony" : m.getName()) + ": first station. Which line? Strength is against low tech.");
            float base = CAStations.strength("orbitalstation");
            OptionPanelAPI o = dialog.getOptionPanel();
            for (int v : m == null ? new ArrayList<Integer>() : openLines(m)) {
                String st = CASettings.stationStarter(CASettings.STATION_IDS[v]);
                IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(st);
                float s = CAStations.strength(st);
                String pct = s > 0f && base > 0f ? Math.round(100f * s / base) + "%" : "?";
                t.addPara("%s: strength %s, %s, upkeep %s a month.", Misc.getHighlightColor(),
                        CASettings.STATION_NAMES[v], pct, Misc.getDGSCredits(sp.getCost()), Misc.getDGSCredits(sp.getUpkeep()));
                o.addOption(CASettings.STATION_NAMES[v], v);
            }
            o.addOption(CASettings.STATION_NAMES[0], 0);
            o.setShortcut(0, Keyboard.KEY_ESCAPE, false, false, false, true);
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "station line pop-up failed", e);
            dialog.getOptionPanel().clearOptions();
            dialog.getOptionPanel().addOption(CASettings.STATION_NAMES[0], 0);
        }
    }

    public void optionSelected(String text, Object data) {
        try {
            int v = data instanceof Integer ? (Integer) data : 0;
            CAData d = CAData.get();
            CASettings s = d.settings;
            CAPresets.apply(s, CAPresets.STATIONS, v);
            s.playerSet().add("stationLine");   // your choice: mode changes and presets leave it
            s.playerRows().add("p_station");
            d.stationAsked = true;
            hold = false;
            CALunaSync.sync();
            CALog.did("Stations: " + CASettings.STATION_NAMES[v] + ", your choice.", true);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "station line choice failed", t); }
        dialog.dismiss();
    }

    public void optionMousedOver(String text, Object data) { }
    public void advance(float amount) { }
    public void backFromEngagement(EngagementResultAPI result) { }
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return null; }

    /** Shows the pop-up when no dialog, menu or screen is open; done once answered. */
    public static class Watch implements EveryFrameScript {
        float timer = 2f;
        boolean done = false;

        public boolean isDone() { return done; }
        public boolean runWhilePaused() { return true; }

        public void advance(float amount) {
            timer -= amount;
            if (timer > 0f) return;
            timer = 0.5f;
            try {
                if (!hold || CAData.get().stationAsked) { done = true; return; }
                if (Global.getSector().isInNewGameAdvance() || Global.getSector().getPlayerFleet() == null) return;
                CampaignUIAPI ui = Global.getSector().getCampaignUI();
                if (ui.isShowingDialog() || ui.isShowingMenu() || ui.getCurrentCoreTab() != null) return;
                if (ui.showInteractionDialog(new CAStationAsk(), Global.getSector().getPlayerFleet())) done = true;
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "station line pop-up failed", t);
                timer = 30f;
            }
        }
    }
}
