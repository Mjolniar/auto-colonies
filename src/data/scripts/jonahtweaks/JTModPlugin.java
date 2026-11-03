package data.scripts.jonahtweaks;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

/** Starts Colony Autopilot on every save load. Balance changes and sticky autopilot moved to Jonah's Easy Sector. */
public class JTModPlugin extends BaseModPlugin {

    /** Counts the three mods' warnings and errors from the start, for the Diagnostics page. */
    public void onApplicationLoad() {
        try { data.scripts.jonahtweaks.autopilot.CALogWatch.install(); }
        catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] log watch failed", t); }
    }

    public void onGameLoad(boolean newGame) {
        try { data.scripts.jonahtweaks.autopilot.CALogWatch.install(); } catch (Throwable ignored) { }
        try { JTCampaignScript.removeRetired(); }   // once a load: nothing adds the retired condition any more
        catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] removing a retired condition failed", t); }
        try {
            data.scripts.jonahtweaks.autopilot.CAData.get();
            data.scripts.jonahtweaks.autopilot.CAAttacks.apply(data.scripts.jonahtweaks.autopilot.CAData.settings());   // per save: puts the game's own values back for a save without them
            data.scripts.jonahtweaks.autopilot.CACalibrate.migrate();
            data.scripts.jonahtweaks.autopilot.CADiagnostics.runScan();
            data.scripts.jonahtweaks.autopilot.CAOptionProvider.register();
            data.scripts.jonahtweaks.autopilot.CAIntel.ensure();
            data.scripts.jonahtweaks.autopilot.CAAuto.reset();
            data.scripts.jonahtweaks.autopilot.CASurplus.reset();
            data.scripts.jonahtweaks.autopilot.CAItems.ensureStorageTabs();
            Global.getSector().addTransientScript(new data.scripts.jonahtweaks.autopilot.CAScript());
            Global.getSector().addTransientScript(new data.scripts.jonahtweaks.autopilot.CAScreenButton());
            data.scripts.jonahtweaks.autopilot.CAStockpile.reset();
            Global.getSector().addTransientScript(new data.scripts.jonahtweaks.autopilot.CAStockpile());
        } catch (Throwable t) {
            Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] startup failed", t);
        }
    }

    /** The storage stockpile's supply bonus is worked out every frame; it never goes into a save. */
    public void beforeGameSave() {
        try { data.scripts.jonahtweaks.autopilot.CAStockpile.clear(); }
        catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] clearing the storage stockpile before saving failed", t); }
    }
}