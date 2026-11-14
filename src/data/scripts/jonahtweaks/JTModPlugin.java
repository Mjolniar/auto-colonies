package data.scripts.jonahtweaks;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.thoughtworks.xstream.XStream;

/**
 * Starts Colony Autopilot on every save load. Each step is its own try (0.2.37.0), so one failing (the diagnostics scan,
 * say) can't stop the daily script or the colony screen button from starting; failures go to starsector.log.
 */
public class JTModPlugin extends BaseModPlugin {

    /** Counts the three mods' warnings and errors from the start, for the Diagnostics page. */
    public void onApplicationLoad() {
        try { data.scripts.jonahtweaks.autopilot.CALogWatch.install(); }
        catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] log watch failed", t); }
        // 0.3.4.0: saves in LunaLib's settings menu reach the loaded game (only with LunaLib enabled)
        try { data.scripts.jonahtweaks.autopilot.CALunaSync.register(); }
        catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] LunaLib listener failed", t); }
    }

    /** 0.3.4.0: LunaLib's settings get what changed in the game since the last sync. */
    public void beforeGameSave() {
        try { data.scripts.jonahtweaks.autopilot.CALunaSync.sync(); }
        catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] LunaLib settings write failed", t); }
    }

    /**
     * 0.3.0.0: short stable names for the classes Auto Colonies keeps in the save, and retired fields dropped (CAXStream),
     * so 0.3.0 can move classes without breaking saves. Never throws: a failure here would stop every save from loading.
     */
    public void configureXStream(XStream x) {
        try { data.scripts.jonahtweaks.autopilot.CAXStream.configure(x); }
        catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] save names failed", t); }
    }

    static void step(String what, Runnable r) {
        try { r.run(); }
        catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] startup step failed: " + what, t); }
    }

    public void onGameLoad(boolean newGame) {
        step("log watch", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CALogWatch.install(); } });
        step("retired condition", new Runnable() { public void run() { JTCampaignScript.removeRetired(); } });   // nothing adds it any more
        // 0.3.0.0: every cache and timer from the last save cleared before this one's data is read (CAReset, A1)
        step("reset", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAReset.onLoad(); } });
        step("saved data", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAData.get(); } });   // also moves a pre-0.3.0 save forward (CAMigrate)
        // 0.3.0.0 (WS-E fix): the war listener is transient, so a load drops it; added here (ensure does nothing when it is there)
        step("war listener", new Runnable() { public void run() {
            if (Global.getSettings().getModManager().isModEnabled("nexerelin") && data.scripts.jonahtweaks.autopilot.CACompat.linked("CAWarListener"))
                data.scripts.jonahtweaks.autopilot.CAWarListener.ensure();
        } });
        // 0.3.1.0: enforcer patrols (CAEnforcers): a transient script and battle listener, so a load drops them; added here
        step("enforcer patrols", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAEnforcers.ensure(); } });
        step("calibration", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CACalibrate.migrate(); } });
        // 0.3.6.0: story mode: the costs, and in story mode the core, the holds, the Story tab and the TriPad
        step("story mode", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CASync.onLoad(); } });
        // 0.3.4.0: the first save after the update seeds LunaLib's settings; later loads read them into this save (LunaLib only).
        // 0.3.6.0: the save switches to LunaLib's Story mode setting first
        step("LunaLib settings", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CALunaSync.onLoad(); } });
        // 0.3.6.0: a new game asks Normal or Story mode once
        if (newGame) step("story mode pop-up", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAStoryPrompt.ask(); } });
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
        // 0.3.3.0: Autopilot's ranking beside the game's build menu (Advisor colonies by default)
        step("build menu hints", new Runnable() { public void run() {
            Global.getSector().addTransientScript(new data.scripts.jonahtweaks.autopilot.CABuildHints());
        } });
        step("colony menu option", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAOptionProvider.register(); } });
        step("intel panel", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAIntel.ensure(); } });
        step("storage tabs", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CAItems.ensureStorageTabs(); } });
        step("diagnostics scan", new Runnable() { public void run() { data.scripts.jonahtweaks.autopilot.CADiagnostics.runScan(); } });
    }
}
