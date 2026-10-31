package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

/** Loads data/config/colony_autopilot.json into a new save's Colony Autopilot settings. */
public class CASettingsFile {
    public static final String PATH = "data/config/colony_autopilot.json";

    public static void apply(CAData d) {
        CASettings s = d.settings;
        try {
            JSONObject j = Global.getSettings().loadJSON(PATH, "jonah_tweaks");
            s.budgetPercentMode = j.optBoolean("budgetPercentMode", s.budgetPercentMode);
            s.budgetAuto = j.optBoolean("budgetAuto", s.budgetAuto);
            s.savingsShare = (float) j.optDouble("savingsShare", s.savingsShare);
            s.budgetPercent = (float) j.optDouble("budgetPercent", s.budgetPercent);
            s.budgetFixed = (float) j.optDouble("budgetFixed", s.budgetFixed);
            s.perProjectCap = (float) j.optDouble("perProjectCap", s.perProjectCap);
            s.reserve = (float) j.optDouble("reserve", s.reserve);
            s.terraformShare = (float) j.optDouble("terraformShare", s.terraformShare);
            s.newUpkeepCap = (float) j.optDouble("newUpkeepCap", s.newUpkeepCap);
            s.pauseOnLoss = j.optBoolean("pauseOnLoss", s.pauseOnLoss);
            s.feeLevel = Math.max(0, Math.min(3, j.optInt("feeLevel", s.feeLevel)));
            s.stationLine = j.optString("stationLine", s.stationLine);
            s.allowRemovals = j.optBoolean("allowRemovals", s.allowRemovals);
            s.storyPointRule = Math.max(0, Math.min(2, j.optInt("storyPointRule", s.storyPointRule)));
            s.storyPointMonthlyCap = j.optInt("storyPointMonthlyCap", s.storyPointMonthlyCap);
            s.useAICores = j.optBoolean("useAICores", s.useAICores);
            s.alphaMode = Math.max(0, Math.min(2, j.optInt("alphaMode", s.alphaMode)));
            s.adminHiring = j.optBoolean("adminHiring", s.adminHiring);
            s.adminOptimal = j.optBoolean("adminOptimal", s.adminOptimal);
            s.adminDelay = j.optBoolean("adminDelay", s.adminDelay);
            s.ambassadorFocus = Math.max(0, Math.min(3, j.optInt("ambassadorFocus", s.ambassadorFocus)));
            s.ambassadorStopAt = Math.max(0, Math.min(3, j.optInt("ambassadorStopAt", s.ambassadorStopAt)));
            JSONObject fp = j.optJSONObject("factionPriority");
            if (fp != null) {
                s.factionPriority = new java.util.HashMap<String, Integer>();
                java.util.Iterator<?> it = fp.keys();
                while (it.hasNext()) { String k = String.valueOf(it.next()); s.factionPriority.put(k, Math.max(0, Math.min(2, fp.optInt(k, 1)))); }
            }
            s.hazardPayMode = Math.max(0, Math.min(1, j.optInt("hazardPayMode", s.hazardPayMode)));
            s.hazardPayColonyShare = (float) j.optDouble("hazardPayColonyShare", s.hazardPayColonyShare);
            s.hazardPayEmpireShare = (float) j.optDouble("hazardPayEmpireShare", s.hazardPayEmpireShare);
            s.freePortMode = Math.max(0, Math.min(1, j.optInt("freePortMode", s.freePortMode)));
            d.minStability = (float) j.optDouble("minStability", d.minStability);
            CADiagnostics.LOG.info(CADiagnostics.TAG + "loaded defaults from " + PATH);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "could not read " + PATH + "; using built-in defaults", t);
        }
    }
}
