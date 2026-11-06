package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

/** Action log: kept in the save (last 100 entries), shown in the empire panel, echoed to the game log. */
public class CALog {
    /** Escapes % so free text is safe in the game's formatted text calls. */
    public static String esc(String s) { return s == null ? "" : s.replace("%", "%%"); }

    /** Ends a row of side-by-side buttons so the next element starts back at the left edge, under the row. */
    public static void endRow(com.fs.starfarer.api.ui.TooltipMakerAPI t, com.fs.starfarer.api.ui.UIComponentAPI first) {
        t.addSpacer(0f).getPosition().belowLeft(first, 0f);
    }

    /** True the first time a key is seen in this save; for messages that should be logged once, not after every load. */
    public static boolean once(String key) { return CAData.get().noted().add(key); }

    /** Lets a once() message be logged again, after the thing it reported has happened. */
    public static void forget(String key) { CAData.get().noted().remove(key); }

    public static void add(String text) { add(text, false); }

    /** Days a flipping status (siege, threat, station augments, policy toggles) waits before its next line in the panel log. */
    static final float STATUS_DAYS = 10f;
    static final java.util.Map<String, Float> statusAt = new java.util.HashMap<String, Float>();

    /** A status that can flip back and forth: in the panel log at most once per key every STATUS_DAYS, always in starsector.log. */
    public static void status(String key, String text) {
        float now = CADay.now();
        Float at = statusAt.get(key);
        if (at != null && now - at < STATUS_DAYS && now >= at) { quiet(text); return; }
        statusAt.put(key, now);
        add(text);
    }

    /** starsector.log only: routine detail that doesn't belong in the panel log. */
    public static void quiet(String text) { CADiagnostics.LOG.info(CADiagnostics.TAG + text); }

    /** Days before the same ping shows again. */
    static final float PING_DAYS = 7f;
    static final java.util.Map<String, Float> pingAt = new java.util.HashMap<String, Float>();

    /**
     * A ping (2.41.0): a short machine line on screen about what the autopilot is weighing or doing in the background, at
     * most once per key a week. Only with the Machine voice and every entry on screen; never in the panel log.
     */
    public static void ping(String key, String text) {
        try {
            CASettings s = CAData.settings();
            if (s.voice != 0 || s.screenLog != 0) return;
            float now = CADay.now();
            Float at = pingAt.get(key);
            if (at != null && now - at < PING_DAYS && now >= at) return;
            pingAt.put(key, now);
            Global.getSector().getCampaignUI().addMessage(CATheme.machine(text), Misc.getTextColor());
        } catch (Throwable ignored) { }
    }

    /**
     * A log entry: the panel log, starsector.log, and on screen as a short line (Settings, Interface: every entry, the
     * default; important ones only; or none). important marks what shows in "important only": emergencies, defense
     * fleets, wonders and gates, things that are yours to do.
     */
    public static void add(String text, boolean important) {
        CASettings s = CAData.settings();
        List<String> log = s.log;
        String date = Global.getSector().getClock().getDateString();
        log.add(0, date + ": " + text);
        while (log.size() > 100) log.remove(log.size() - 1);
        CADiagnostics.LOG.info(CADiagnostics.TAG + text);
        if (s.screenLog == 0 || (s.screenLog == 1 && important)) {
            try {
                if (s.voice == 0) Global.getSector().getCampaignUI().addMessage(CATheme.machine(text), important ? Misc.getHighlightColor() : Misc.getTextColor());
                else Global.getSector().getCampaignUI().addMessage("Autopilot: " + text, important ? Misc.getHighlightColor() : Misc.getTextColor());
            }
            catch (Throwable ignored) { }
        }
    }

    public static final String[] SCREEN_NAMES = {"Every entry", "Important only", "Off"};
}
