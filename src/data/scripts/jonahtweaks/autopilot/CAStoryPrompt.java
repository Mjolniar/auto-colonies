package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.util.Map;

/**
 * 0.3.6.0: the new-game pop-up. On the first load of a new game (JTModPlugin.onGameLoad), once the campaign is free, it asks
 * Normal or Story mode. The choice sets the save's mode (CASync.setOn) and the Story mode setting (LunaLib's, through
 * CALunaSync.sync; without LunaLib the save's own mode is the setting). Older saves never see it. Until it's answered, it
 * comes back on the next load (CASync.askMode).
 */
public class CAStoryPrompt implements InteractionDialogPlugin {
    static final String NORMAL = "normal", STORY = "story";
    InteractionDialogAPI dialog;

    /** A new game: ask once. */
    public static void ask() {
        CASync.askMode(true);
        watch();
    }

    /** The watcher that shows the pop-up once the campaign is free. */
    static void watch() {
        if (!Global.getSector().hasTransientScript(Watch.class)) Global.getSector().addTransientScript(new Watch());
    }

    static boolean luna() {
        try { return Global.getSettings().getModManager().isModEnabled(CALunaSync.LUNA); } catch (Throwable t) { return false; }
    }

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.setPromptText("");
        try { dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("illustrations", "orbital_construction", 640, 400)); }
        catch (Throwable ignored) { }
        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("Your TriPad is showing a setup screen for the Colony Autopilot, an old Domain program for running colonies.");
        text.addPara("In Normal mode it works from the start. You set each colony to Advisor, Approve or Auto, and it plans, builds and hires within the budget you give it.");
        text.addPara("In Story mode it stays dormant until you find the Autarch AI Core it was written for. After that, each AI core you give it adds processing power, and you choose which of its abilities it runs.");
        text.addPara(luna() ? "You can change this later in LunaLib's settings menu (F3), under Auto Colonies. A save switched back keeps its story progress."
                : "You can change this later on the Settings page of the Colony Autopilot intel. A save switched back keeps its story progress.", Misc.getGrayColor());
        OptionPanelAPI o = dialog.getOptionPanel();
        o.addOption("Normal mode", NORMAL);
        o.addOption("Story mode", STORY);
        o.setShortcut(NORMAL, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    public void optionSelected(String label, Object data) {
        boolean story = STORY.equals(data);
        try {
            CASync.askMode(false);
            CASync.setOn(story);
            if (story) CAAutarch.ensurePlaced();   // 0.3.6.2: placed after the answer (CASync waits for it on a new game)
            CALunaSync.sync();   // LunaLib's Story mode setting follows the choice
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode choice failed", t); }
        dialog.dismiss();
    }

    public void optionMousedOver(String label, Object data) { }
    public void advance(float amount) { }
    public void backFromEngagement(EngagementResultAPI result) { }
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return null; }

    /** Shows the pop-up when no dialog, menu or screen is open. Checked twice a second, real time; done once answered. */
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
                if (!CASync.askMode()) { done = true; return; }
                if (Global.getSector().isInNewGameAdvance() || Global.getSector().getPlayerFleet() == null) return;
                CampaignUIAPI ui = Global.getSector().getCampaignUI();
                if (ui.isShowingDialog() || ui.isShowingMenu() || ui.getCurrentCoreTab() != null) return;
                if (ui.showInteractionDialog(new CAStoryPrompt(), Global.getSector().getPlayerFleet())) done = true;
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode pop-up failed", t);
                timer = 30f;
            }
        }
    }
}
