package data.scripts.jonahtweaks.autopilot;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

/**
 * 0.3.4.0: the only class that references LunaLib. It loads only when LunaLib is enabled (every caller checks
 * isModEnabled("lunalib"); build_verify.ps1 and audit_combos.ps1 verify the mod without it). The settings logic is in
 * CALunaSync.
 */
public class CALunaLink implements LunaSettingsListener {
    static void register() {
        if (!LunaSettings.hasSettingsListenerOfClass(CALunaLink.class)) LunaSettings.addSettingsListener(new CALunaLink());
    }

    /** LunaLib reads Auto Colonies' settings file again (CALunaSync writes it). */
    static void reload() { LunaSettings.SettingsCreator.refresh(CALunaSync.MOD); }

    /** A save in LunaLib's menu applies the changed values to this save. */
    @Override
    public void settingsChanged(String modId) {
        if (CALunaSync.MOD.equals(modId)) CALunaSync.changed();
    }
}
