package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

/** Runs the Approve/Auto pass once per in-game day; each colony is actually re-planned every 7 days or when a build finishes. */
public class CAScript implements EveryFrameScript {
    private float acc = 0f;

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return false; }

    public void advance(float amount) {
        try { CABuildout.checkOffer(amount); }
        catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "fast buildout offer failed", t);
            try { CAData.get().buildoutOffered = true; } catch (Throwable ignored) { }   // never let the handler itself throw into the game
        }
        try { CAApprovalDialog.check(amount); }   // 0.3.3.0: the approval summary, at a safe moment once per interval
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "approval summary check failed", t); }
        acc += Global.getSector().getClock().convertToDays(amount);
        if (acc < 1f) return;
        if (acc < 2f && contractsBusy()) return;   // ToT's economy threads are reading markets; try again next frame, but never wait past a day
        acc = 0f;
        // 0.3.0.0 (A1): the pass runs on the game's own clock (CATime), not on days counted since this session started, so
        // weekly steps keep their rhythm across loads (07 H2); a failure is logged once with its trace, then quietly (CAStep);
        // the whole pass is timed for Diagnostics, with one quiet log line when it's slow (CAPerf, A4)
        float now = CATime.now();
        long t0 = System.nanoTime();
        try { CAAuto.runPass(now); }
        catch (Throwable t) { CAStep.warn("daily pass", "daily pass failed", t); }
        try { CAPerf.pass(System.nanoTime() - t0); } catch (Throwable ignored) { }
    }

    static boolean contractsBusy() {
        try { return CAContracts.busy(); } catch (Throwable t) { return false; }
    }
}
