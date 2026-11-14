package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;

/**
 * 0.3.6.0 story mode: shows the TriPad's next beat (CASync.nextBeat) as soon as the campaign is free: no dialog, menu or screen
 * open, so a beat never interrupts the loot screen, a battle or the founding dialog. Checked twice a second, real time.
 * Transient: added on every load.
 */
public class CATriPadWatch implements EveryFrameScript {
    float timer = 2f;   // a moment after loading, so the game is settled first

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return true; }

    public void advance(float amount) {
        timer -= amount;
        if (timer > 0f) return;
        timer = 0.5f;
        try {
            if (Global.getSector().isInNewGameAdvance()) return;
            CampaignUIAPI ui = Global.getSector().getCampaignUI();
            if (ui.isShowingDialog() || ui.isShowingMenu() || ui.getCurrentCoreTab() != null) return;
            if (Global.getSector().getPlayerFleet() == null) return;
            CATriPad beat = CASync.nextBeat();
            if (beat == null) return;
            if (!ui.showInteractionDialog(beat, beat.entity())) CASync.queued = beat;   // try again next time
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "TriPad watch failed", t);
            timer = 30f;   // don't spam the log
        }
    }
}
