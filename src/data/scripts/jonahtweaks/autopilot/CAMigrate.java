package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;

import java.util.Map;

/**
 * 0.3.0.0 (A0): moves a save from before 0.3.0 forward, once, from CAData.get(). It only translates what you already set into
 * the 0.3.0 fields; it never escalates: War never goes above what 0.2.45 did for you, no faction is made an Enemy or marked
 * for Destroy, retaking lost colonies stays off, and Auto-expansion, bombardment, coalitions, Resist inspections, Free
 * hand, story point spending and every old field are left exactly as they were. Research 04 section 5, 06 sections 4.2-4.3.
 * Each step is its own try, and the save is stamped as moved before the steps run, so a failure loads the save anyway
 * and is never retried every frame.
 */
public class CAMigrate {
    /** The saved-data layout this build writes (CAData.schema and CASettings.settingsVersion). 0.3.2.0: 4 (production floors). 0.3.3.0: 5 (mode presets). */
    public static final int SCHEMA = 5;
    /** The 0.3.0 layout: a save below it gets to3 as well. */
    static final int SCHEMA_030 = 3;

    /** warMode (0 protect and strike back, 1 protect, 2 off) to the 0.3.0 War level, matching what CAWar actually did with it. */
    static int warLevelFor(int warMode) {
        if (warMode == 0) return CAWarPolicy.RETALIATE;   // CAWar struck back only at warMode 0
        if (warMode == 2) return CAWarPolicy.OFF;         // and did nothing at 2
        return CAWarPolicy.DEFEND;                        // anything else protected only
    }

    /**
     * An old faction priority (0 Ignore, 1 Normal, 2 High) as a stance: High is Friendly, or Ally when ambassadors were set
     * to never stop raising; Ignore is Rival (no raising, which at War level Retaliate does nothing Auto wouldn't); Normal
     * stays Auto. Never Enemy or Destroy: those reject ceasefires and keep a faction hostile, which 0.2.45 never did.
     */
    static int stanceFor(int priority, int ambassadorStopAt) {
        if (priority >= 2) return ambassadorStopAt >= 3 ? CAStance.ALLY : CAStance.FRIENDLY;
        if (priority <= 0) return CAStance.RIVAL;
        return CAStance.AUTO;
    }

    /** Called from CAData.get() whenever either version is behind; does nothing on a save already moved. */
    public static void run(CAData d, CASettings s) {
        if (d == null || s == null) return;
        if (d.schema >= SCHEMA && s.settingsVersion >= SCHEMA) return;
        int fromData = d.schema, fromSettings = s.settingsVersion;
        // stamped first: CALog below calls CAData.get(), and a failing step must not run again on every call
        d.schema = SCHEMA;
        s.settingsVersion = SCHEMA;
        // settings from before 0.3.0 get the whole move; an old save whose settings were just re-created (new CASettings,
        // already the current version) only needs its colony_autopilot.json priorities as stances. 0.3.2.0: a 0.3.0 or 0.3.1
        // save (version 3) gets only to4
        boolean before030 = fromSettings < SCHEMA_030 || fromData < SCHEMA_030;
        boolean ok = true;
        if (fromSettings < SCHEMA_030) ok = to3(d, s);
        else if (fromData < SCHEMA_030) ok = stances(s);
        if (fromSettings < 4 && !to4(d, s)) ok = false;
        if (fromSettings < 5 && !to5(d, s)) ok = false;
        if (before030) {
            try { d.migratedAt = Global.getSector().getClock().getTimestamp(); } catch (Throwable ignored) { }
            try {
                CALog.quiet("saved data moved to 0.3.0 (data " + fromData + ", settings " + fromSettings + "): War level "
                        + CAWarPolicy.LEVEL_NAMES[Math.max(0, Math.min(CAWarPolicy.LEVEL_NAMES.length - 1, s.warLevel))]
                        + " from War fleets " + s.warMode + ", " + s.factionStance().size() + " faction stance(s) from your priorities"
                        + (s.noWarRetake ? ", retaking lost colonies off until you turn it on" : "") + (ok ? "" : "; a step failed, see above"));
            } catch (Throwable ignored) { }
        }
    }

    /**
     * 0.3.2.0: the move to production floors. fixNulls already gave the production fields their defaults (Light) on a save
     * that didn't have them (CAFloors.update says so once on the first daily pass); this copies the Log page's Thinking list
     * switch into the settings. Idempotent.
     */
    static boolean to4(CAData d, CASettings s) {
        try {
            if (d.thinkingShown) s.showThinking = true;
            return true;
        } catch (Throwable t) { warn("production floors", t); return false; }
    }

    /**
     * 0.3.3.0: the move to mode presets (plans/2026-11-13-modes-rework.md section 9). The empire mode: Advisor before setup,
     * else Auto with any Auto colony, else Approve with any Approve colony, else Advisor. Player-set flags: every tracked
     * field that differs from what the save started with (the recommended values after setup, a new save's before), less the
     * amounts setup works out from credits (reserve, per-project cap, upkeep cap). Then the empire mode's column goes into
     * every field you never changed; each change is one Thinking line and one did line counts them. Idempotent: a save with
     * its flags and empire mode set is left alone.
     */
    static boolean to5(CAData d, CASettings s) {
        try {
            boolean flagged = s.playerSet != null, moded = s.empireMode != null;
            if (flagged && moded) return true;
            if (!moded) {
                java.util.List<CAData.Mode> modes = new java.util.ArrayList<CAData.Mode>();
                for (CAData.Colony c : d.colonies.values()) if (c != null && c.mode != null) modes.add(c.mode);
                s.empireMode = CAModeTable.deriveEmpire(s.setupDone, modes);
            }
            if (!flagged) {
                CASettings base = baseline(s.setupDone);
                s.playerSet = new java.util.HashSet<String>(CAModeTable.changedFrom(s, base));
            }
            s.playerRows();
            java.util.List<String> written = CAModeTable.apply(s, s.empireMode);
            try {
                for (String f : written) CALog.think("Settings: " + CAModeTable.label(f) + " follows the " + s.empireMode.label + " mode now.");
                CALog.did("Settings: empire mode " + s.empireMode.label + ". " + written.size() + " settings follow it; "
                        + s.playerSet.size() + " you set stay as they are.", true);
            } catch (Throwable ignored) { }
            return true;
        } catch (Throwable t) { warn("mode presets", t); return false; }
    }

    /** What an older save's settings started as: the recommended values after setup, a new save's before it. */
    static CASettings baseline(boolean setupDone) {
        CASettings b = new CASettings();
        b.empireMode = null;
        if (setupDone) { try { CASetupUI.applyRecommended(b); } catch (Throwable t) { warn("mode presets baseline", t); } }
        return b;
    }

    /**
     * The move to 0.3.0 for a save from before it. Pure apart from the settings object: returns false if a step threw.
     * Idempotent: running it again changes nothing.
     */
    static boolean to3(CAData d, CASettings s) {
        boolean ok = true;
        try {
            // War fleets -> War level, once (the spyChecked pattern: older saves load warLevelChecked false, warLevel 0)
            if (!s.warLevelChecked) {
                s.warLevel = warLevelFor(s.warMode);
                s.warLevelChecked = true;
                // 0.3.0.0: retaking your own lost colonies is an invasion, which 0.2.45 never sent; old saves start with it off
                s.noWarRetake = true;
            }
        } catch (Throwable t) { ok = false; warn("War level", t); }
        if (!stances(s)) ok = false;
        try {
            // the automatic upkeep cap's share of net income (0.2.24.0's 10%); fixNulls does the same for any 0
            if (s.upkeepShare <= 0f) s.upkeepShare = CATuning.UPKEEP_SHARE;
        } catch (Throwable t) { ok = false; warn("upkeep share", t); }
        // 0.3.0.0 (WS-F1 fix, research 04 section 5.4): an old save's cap stays a Fixed number unless it equals the Automatic cap now
        try { CAPresets.migrateProjectCap(s); } catch (Throwable t) { ok = false; warn("per-project cap", t); }
        return ok;
    }

    /**
     * Your faction priorities as stances, written only where they differ from Auto and only for factions with no stance
     * yet (so a stance you set is never overwritten, and running it twice changes nothing). Also used for a new save's
     * settings, whose priorities may come from colony_autopilot.json.
     */
    static boolean stances(CASettings s) {
        try {
            Map<String, Integer> st = s.factionStance();
            if (s.factionPriority == null) return true;
            for (Map.Entry<String, Integer> e : s.factionPriority.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || st.containsKey(e.getKey())) continue;
                int stance = stanceFor(e.getValue(), s.ambassadorStopAt);
                if (stance != CAStance.AUTO) st.put(e.getKey(), stance);
            }
            return true;
        } catch (Throwable t) { warn("faction stances", t); return false; }
    }

    static void warn(String what, Throwable t) {
        try { CADiagnostics.LOG.warn(CADiagnostics.TAG + "0.3.0 migration step failed: " + what, t); } catch (Throwable ignored) { }
    }
}
