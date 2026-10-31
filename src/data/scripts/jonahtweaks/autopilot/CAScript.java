package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

/** Runs the Approve/Auto pass once per in-game day; each colony is actually re-planned every 7 days or when a build finishes. */
public class CAScript implements EveryFrameScript {
    private float now = 0f, acc = 0f;

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return false; }

    public void advance(float amount) {
        try { CABuildout.checkOffer(amount); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "fast buildout offer failed", t); CAData.get().buildoutOffered = true; }
        float days = Global.getSector().getClock().convertToDays(amount);
        now += days;
        acc += days;
        if (acc < 1f) return;
        if (acc < 2f && CAContracts.busy()) return;   // ToT's economy threads are reading markets; try again next frame, but never wait past a day
        acc = 0f;
        try { CAAuto.runPass(now); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "daily pass failed", t); }
    }
}
