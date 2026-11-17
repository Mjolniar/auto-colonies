package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Map;

/**
 * 0.3.9.4: the Prepare for removal dialog (CARemoval). Shown once the campaign is free after the LunaLib row turns On or the
 * Mod features page's button is pressed. Confirm prepares the save; Cancel turns the row back Off. A colony that would fall to
 * 0 stability stops it before anything changes.
 */
public class CARemovalPrompt implements InteractionDialogPlugin {
    static final String CONFIRM = "confirm", CANCEL = "cancel", LEAVE = "leave";
    InteractionDialogAPI dialog;

    static boolean luna() {
        try { return Global.getSettings().getModManager().isModEnabled(CALunaSync.LUNA); } catch (Throwable t) { return false; }
    }

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.setPromptText("");
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI o = dialog.getOptionPanel();
        if (blocked(text)) {
            o.addOption("Leave", LEAVE);
            o.setShortcut(LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
            return;
        }
        text.addPara("This prepares your save for removal.");
        text.addPara("It'll empty Autopilot Storage into Storage, deliver shipments and refund crews en route.");
        if (story()) text.addPara("Autarch AI Cores become Alpha Cores. Story progress ends.");
        text.addPara("Then Auto Colonies stops.");
        o.addOption("Prepare for removal", CONFIRM);
        o.addOption("Cancel", CANCEL);
        o.setShortcut(CANCEL, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    /** A colony would fall to 0 stability: says which, turns the setting back Off and returns true. */
    static boolean blocked(TextPanelAPI text) {
        List<String> block = CARemoval.blockers();
        if (block.isEmpty()) return false;
        for (String b : block) text.addPara(b);
        text.addPara("Nothing was changed.");
        CARemoval.cancel();
        return true;
    }

    /** The save has story mode state to mention: on, a core installed, or administrators seated. */
    static boolean story() {
        try { return CASync.on() || CASync.installed() || CAAutarchAdmin.count() > 0 || CAAutarch.anywhere(); }
        catch (Throwable t) { return true; }
    }

    public void optionSelected(String label, Object data) {
        if (CANCEL.equals(data)) { CARemoval.cancel(); dialog.dismiss(); return; }
        if (LEAVE.equals(data)) { dialog.dismiss(); return; }
        if (!CONFIRM.equals(data)) return;
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI o = dialog.getOptionPanel();
        o.clearOptions();
        List<String> report = null;
        if (!blocked(text)) {
            try { report = CARemoval.prepare(); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "removal failed", t); }
            if (report == null) text.addPara("Removal failed. See starsector.log.");
        }
        if (report != null) {
            for (String r : report) text.addPara(r);
            text.addPara("Save, quit and pull the mod.");
            text.addPara(luna() ? "To undo, turn it off in LunaLib (F3)." : "To undo, load an earlier save.", Misc.getGrayColor());
        }
        o.addOption("Leave", LEAVE);
        o.setShortcut(LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    public void optionMousedOver(String label, Object data) { }
    public void advance(float amount) { }
    public void backFromEngagement(EngagementResultAPI result) { }
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return null; }

    /** Shows the dialog when no dialog, menu or screen is open. Checked twice a second, real time; done once shown. */
    public static class Watch implements EveryFrameScript {
        float timer = 0.5f;
        boolean done = false;

        public boolean isDone() { return done; }
        public boolean runWhilePaused() { return true; }

        public void advance(float amount) {
            timer -= amount;
            if (timer > 0f) return;
            timer = 0.5f;
            try {
                if (!CARemoval.asked || CARemoval.off) { done = true; return; }
                if (Global.getSector().getPlayerFleet() == null) return;
                CampaignUIAPI ui = Global.getSector().getCampaignUI();
                if (ui.isShowingDialog() || ui.isShowingMenu() || ui.getCurrentCoreTab() != null) return;
                if (ui.showInteractionDialog(new CARemovalPrompt(), Global.getSector().getPlayerFleet())) done = true;
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "removal dialog failed", t);
                timer = 30f;
            }
        }
    }
}
