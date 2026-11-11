package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;

/**
 * Game days since the clock's epoch, for "once a day" caches. Don't use getElapsedDaysSince(0L): the game reads a 0
 * timestamp as "never" and returns Float.MAX_VALUE, which before 0.2.30.0 froze every daily cache for the whole session
 * (surplus terraforming's plans, research ranking, the focus world, siege and crisis checks).
 */
public final class CADay {
    private CADay() { }

    /** The game clock's timestamp is in milliseconds (CampaignClock.getElapsedDaysSince divides by 86,400,000). */
    static float of(long timestamp) { return (float) (timestamp / 86400000.0); }

    public static float now() {
        try { return of(Global.getSector().getClock().getTimestamp()); } catch (Throwable t) { return 0f; }
    }
}
