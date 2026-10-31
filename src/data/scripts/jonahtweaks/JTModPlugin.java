package data.scripts.jonahtweaks;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

/** Starts Colony Autopilot on every save load. Balance changes and sticky autopilot moved to Jonah's Easy Sector. */
public class JTModPlugin extends BaseModPlugin {

    public void onGameLoad(boolean newGame) {
        Global.getSector().addTransientScript(new JTCampaignScript());
        try {
            data.scripts.jonahtweaks.autopilot.CAData.get();
            data.scripts.jonahtweaks.autopilot.CADiagnostics.runScan();
            data.scripts.jonahtweaks.autopilot.CAOptionProvider.register();
            data.scripts.jonahtweaks.autopilot.CAIntel.ensure();
            data.scripts.jonahtweaks.autopilot.CAAuto.reset();
            data.scripts.jonahtweaks.autopilot.CAItems.ensureStorageTabs();
            Global.getSector().addTransientScript(new data.scripts.jonahtweaks.autopilot.CAScript());
            Global.getSector().addTransientScript(new data.scripts.jonahtweaks.autopilot.CAScreenButton());
        } catch (Throwable t) {
            Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] startup failed", t);
        }
    }
}