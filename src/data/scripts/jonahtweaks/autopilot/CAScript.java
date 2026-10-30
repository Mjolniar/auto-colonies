package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;

/** Runs the Approve/Auto pass once per in-game day; each colony is actually re-planned every 7 days or when a build finishes. */
public class CAScript implements EveryFrameScript {
    private float now = 0f, acc = 0f;

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return false; }

    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);
        now += days;
        acc += days;
        if (acc < 1f) return;
        acc = 0f;
        try { CAAuto.runPass(now); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "daily pass failed", t); }
    }
}
