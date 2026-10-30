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

    public static void add(String text, boolean notify) {
        List<String> log = CAData.settings().log;
        String date = Global.getSector().getClock().getDateString();
        log.add(0, date + ": " + text);
        while (log.size() > 100) log.remove(log.size() - 1);
        CADiagnostics.LOG.info(CADiagnostics.TAG + text);
        if (notify) {
            try { Global.getSector().getCampaignUI().addMessage("Colony Autopilot: " + text, Misc.getHighlightColor()); }
            catch (Throwable ignored) { }
        }
    }
}
