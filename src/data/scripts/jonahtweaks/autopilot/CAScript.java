package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

/**
 * Runs the Approve/Auto pass once per in-game day; each colony is actually re-planned every 7 days or when a build finishes.
 * 0.3.6.9: the pass is queued (CAPass.start) and its steps run over the next frames, CATuning.PASS_FRAME_MS a frame, so
 * no single frame waits for the whole pass. A new day's pass waits until the last one is done.
 */
public class CAScript implements EveryFrameScript {
    private float acc = 0f;

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return false; }

    public void advance(float amount) {
        // 0.3.8.7: both run once a second; CAStep.warn logs a failure's trace once, then one line a week
        try { CABuildout.checkOffer(amount); }
        catch (Throwable t) {
            CAStep.warn("fast buildout offer", "fast buildout offer failed", t);
            try { CAData.get().buildoutOffered = true; } catch (Throwable ignored) { }   // never let the handler itself throw into the game
        }
        try { CAApprovalDialog.check(amount); }   // 0.3.3.0: the approval summary, at a safe moment once per interval
        catch (Throwable t) { CAStep.warn("approval summary", "approval summary check failed", t); }
        acc += Global.getSector().getClock().convertToDays(amount);
        // 0.3.6.9: a pass in progress gets this frame's share. ToT's economy threads reading markets skip the frame (up to
        // CATuning.PASS_MAX_WAIT frames in a row), as the start below waits for them
        if (CAPass.running()) {
            try { CAPass.drain(CATuning.PASS_FRAME_MS, contractsBusy(), CATuning.PASS_MAX_WAIT); }
            catch (Throwable t) { CAStep.warn("daily pass", "daily pass failed", t); CAPass.stop(); }
            return;
        }
        if (acc < 1f) return;
        if (acc < 2f && contractsBusy()) return;   // ToT's economy threads are reading markets; try again next frame, but never wait past a day
        acc = 0f;
        // 0.3.0.0 (A1): the pass runs on the game's own clock (CATime), not on days counted since this session started, so
        // weekly steps keep their rhythm across loads (07 H2); a failure is logged once with its trace, then quietly (CAStep);
        // the whole pass is timed for Diagnostics, with one quiet log line when it's slow (CAPerf.pass, from CAPass, A4)
        final float now = CATime.now();
        try {
            CAPass.start(() -> CAAuto.runPass(now));
            CAPass.drain(CATuning.PASS_FRAME_MS, false, 0);   // the first share this frame
        } catch (Throwable t) { CAStep.warn("daily pass", "daily pass failed", t); CAPass.stop(); }
    }

    static boolean contractsBusy() {
        try { return CAContracts.busy(); } catch (Throwable t) { return false; }
    }
}
