package data.scripts.jonahtweaks;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

/**
 * Starts Colony Autopilot on every save load. Each step is its own try (0.2.37.0), so one failing (the diagnostics scan,
 * say) can't stop the daily script or the colony screen button from starting; failures go to starsector.log.
 */
public class JTModPlugin extends BaseModPlugin {

    /** Counts the three mods' warnings and errors from the start, for the Diagnostics page. */
    public void onApplicationLoad() {
        try { data.scripts.jonahtweaks.autopilot.CALogWatch.install(); }
        catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] log watch failed", t); }
    }

    static void step(String what, Runnable r) {
        try { r.run(); }
        catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] startup step failed: " + what, t); }
    }

    public void onGameLoad(boolean newGame) {
        step("log watch", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CALogWatch.install(); } });
        step("retired condition", new Runnable() { public void run() { JTCampaignScript.removeRetired(); } });   // nothing adds it any more
        step("saved data", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAData.get(); } });
        step("calibration", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CACalibrate.migrate(); } });
        step("daily script", new Runnable() { public void run() {
            data.scripts.jonahtweaks.autopilot.CAAuto.reset();
            data.scripts.jonahtweaks.autopilot.CASurplus.reset();
            data.scripts.jonahtweaks.autopilot.CACapital.reset();
            data.scripts.jonahtweaks.autopilot.CATerraSim.reset();
            Global.getSector().addTransientScript(new data.scripts.jonahtweaks.autopilot.CAScript());
        } });
        step("colony screen button", new Runnable() { public void run() {
            Global.getSector().addTransientScript(new data.scripts.jonahtweaks.autopilot.CAScreenButton());
        } });
        step("colony menu option", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAOptionProvider.register(); } });
        step("intel panel", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAIntel.ensure(); } });
        step("storage tabs", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAItems.ensureStorageTabs(); } });
        step("diagnostics scan", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CADiagnostics.runScan(); } });
    }
}
