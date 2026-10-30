package data.scripts.jonahtweaks;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HTPoints;
import org.json.JSONObject;

public class JTModPlugin extends BaseModPlugin {

    public static final int TOPO_MULT = 5;

    // runs once per game launch, so values are multiplied exactly once
    // Balance changes removed for a vanilla-balance game; Colony Autopilot and sticky autopilot stay.

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
        } catch (Throwable t) {
            Global.getLogger(JTModPlugin.class).warn("[ColonyAutopilot] startup failed", t);
        }
    }

    private static void mulSetting(JSONObject js, String key, double mult) {
        try {
            Object o = js.opt(key);
            double base; boolean intLike;
            if (o instanceof Number) { base = ((Number) o).doubleValue(); intLike = (o instanceof Integer || o instanceof Long); }
            else if (o instanceof String) { base = Double.parseDouble(((String) o).trim()); intLike = !((String) o).contains("."); }
            else { Global.getLogger(JTModPlugin.class).warn("Jonah Tweaks: setting missing " + key + " (" + o + ")"); return; }
            double v = base * mult;
            if (intLike) js.put(key, (int) Math.round(v)); else js.put(key, v);
            Global.getLogger(JTModPlugin.class).info("Jonah Tweaks: " + key + " = " + Global.getSettings().getFloat(key));
        } catch (Throwable t) { Global.getLogger(JTModPlugin.class).warn("Jonah Tweaks: failed " + key, t); }
    }
}