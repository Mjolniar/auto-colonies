package data.scripts.jonahtweaks.autopilot;

import java.util.regex.Pattern;

/**
 * 0.3.0.7: the Autopilot log shows what affects the player (spending, items, building, finds) and advice. Lines that
 * only say Autopilot is not acting (waiting for budget or upkeep room, over a cap, can't yet, not started) go to
 * starsector.log only. Lines marked important and advice ("waits for you", "recommended") stay on screen.
 * 0.3.0.9: "holding" (the weekly scan ping) is not acting. Advice adds "your approval" (Approve-mode proposals) and
 * "start it from" / "start it in" (a project over the per-project cap that you can start yourself).
 */
final class CALogFilter {
    private CALogFilter() { }

    static final Pattern NOT_ACTING = Pattern.compile(
            "(?i)\\b(waits?|waiting)\\b|is over the per-project cap|can't be reached here|\\bcan't\\b.*\\byet\\b|isn't removed while|\\bnot started\\b"
                    + "|\\bholding\\b");
    static final Pattern ADVICE = Pattern.compile("(?i)\\bfor you\\b|\\brecommended\\b|\\byour approval\\b|\\bstart it (from|in)\\b");

    /** The line only reports that Autopilot is not acting. */
    static boolean notActing(String text) {
        return text != null && NOT_ACTING.matcher(text).find() && !ADVICE.matcher(text).find();
    }
}
