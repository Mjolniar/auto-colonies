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
 * 0.3.14.0: three steps in one dialog: Story or Normal, then Balance (CABalance), then Profile (CAProfile), each option with
 * one sentence. The choices apply when the last one is picked; Profile's fields are marked set by you and the first setup
 * keeps it (CASetupUI.recommended reads CASync "profilePick").
 */
public class CAStoryPrompt implements InteractionDialogPlugin {
    static final String NORMAL = "normal", STORY = "story", BALANCE = "balance:", PROFILE = "profile:";
    InteractionDialogAPI dialog;
    boolean story;
    int balance = -1;

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
        text.addPara("In Story mode it starts dormant, and you unlock it piece by piece by finding its core and feeding it AI cores.");   // 0.3.7.8: Story first, the default
        text.addPara("In Normal mode it works right away, and every colony you hand it plans, builds and hires within the budget you give it.");
        text.addPara(luna() ? "You can change all three choices later in LunaLib's settings menu (F3), under Auto Colonies. Switching back keeps your story progress."
                : "You can change all three choices later on the Settings page of the Autopilot panel. Switching back keeps your story progress.", Misc.getGrayColor());
        OptionPanelAPI o = dialog.getOptionPanel();
        o.addOption("Story mode", STORY);
        o.addOption("Normal mode", NORMAL);
        o.setShortcut(STORY, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    public void optionSelected(String label, Object data) {
        String d = String.valueOf(data);
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI o = dialog.getOptionPanel();
        if (STORY.equals(d) || NORMAL.equals(d)) {   // 0.3.14.0: step 2, Balance
            story = STORY.equals(d);
            dialog.addOptionSelectedText(data);
            text.addPara("Next, pick how expensive the Autopilot is.");
            for (int i = 0; i < CABalance.NAMES.length; i++) text.addPara(CABalance.DESC[i]);
            o.clearOptions();
            for (int i = 0; i < CABalance.NAMES.length; i++) o.addOption(CABalance.NAMES[i], BALANCE + i);
            o.setShortcut(BALANCE + 2, Keyboard.KEY_ESCAPE, false, false, false, true);
            return;
        }
        if (d.startsWith(BALANCE)) {   // step 3, Profile
            balance = parse(d.substring(BALANCE.length()));
            dialog.addOptionSelectedText(data);
            text.addPara("Last, pick how your empire grows.");
            for (int i = 0; i < CAProfile.NAMES.length; i++) text.addPara(CAProfile.DESC[i]);
            o.clearOptions();
            for (int i = 0; i < CAProfile.NAMES.length; i++) o.addOption(CAProfile.NAMES[i], PROFILE + i);
            o.setShortcut(PROFILE + CAProfile.NORMAL, Keyboard.KEY_ESCAPE, false, false, false, true);
            return;
        }
        int profile = d.startsWith(PROFILE) ? parse(d.substring(PROFILE.length())) : CAProfile.NORMAL;
        try {
            CASync.askMode(false);
            CASync.setOn(story);
            if (story) CAAutarch.ensurePlaced();   // 0.3.6.2: placed after the answer (CASync waits for it on a new game)
            CASettings s = CAData.settings();
            CABalance.set(s, balance < 0 ? 2 : balance);
            CAProfile.apply(s, profile);
            CAProfile.markSet(s, profile);
            CASync.num("profilePick", profile + 1);   // the first setup keeps it (CASetupUI.recommended)
            // 0.3.15.1: a new game runs on Auto from the start (the panel's empire run tiles change it)
            if (!s.setupDone) { if (!CALunaSync.keepsSetup()) CASetupUI.applyRecommended(s); s.setupDone = true; }
            CAModeTable.setEmpire(s, CAData.Mode.AUTO);
            CALunaSync.sync();   // LunaLib's Story mode, Balance and Profile follow the choices
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "new-game choices failed", t); }
        dialog.dismiss();
    }

    static int parse(String n) { try { return Integer.parseInt(n); } catch (Throwable t) { return -1; } }

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
