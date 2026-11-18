package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableStat;

/**
 * 0.3.10.3: managed colonies raise the colony limit (story mode). The game lets you hold the colonies you govern yourself
 * (playerStats.getOutpostNumber) plus one for each administrator you may employ (getAdminNumber), 2 and 2 at the start.
 * Each managed colony past that total adds one to the colonies you govern yourself, so 5 managed colonies with a total of 4
 * hold 5 colonies without the over-limit penalty. The Autarch administrator's seats are apart from it: each governs a colony.
 * - Never in a save: remove() runs before every save (JTModPlugin.beforeGameSave) and sync() after it and on load, so a save
 *   without Auto Colonies has no trace of it. Prepare for removal takes it off too (CARemoval.prepare).
 * - sync(): on while story mode is on and the core is awake, off otherwise; daily (CASync.daily), after every change on the
 *   Story page (CASync.afterChange) and on load.
 */
public final class CAStoryLimit {
    private CAStoryLimit() { }

    public static final String KEY = "ac_story_limit";

    /** Pure: what the managed colonies add to the colonies you govern: the ones past your own limit and your administrators. */
    static int bonus(int managedCap, int ownLimit, int admins) {
        return Math.max(0, managedCap - Math.max(0, ownLimit) - Math.max(0, admins));
    }

    static MutableStat stat() {
        try { return Global.getSector().getPlayerStats().getOutpostNumber(); } catch (Throwable t) { return null; }
    }

    static int admins() {
        try { return (int) Global.getSector().getPlayerStats().getAdminNumber().getModifiedValue(); } catch (Throwable t) { return 0; }
    }

    /** What it adds to your colony limit now (0 when it's off). */
    public static int now() {
        MutableStat s = stat();
        MutableStat.StatMod m = s == null ? null : s.getFlatStatMod(KEY);
        return m == null ? 0 : Math.round(m.getValue());
    }

    public static void sync() {
        MutableStat s = stat();
        if (s == null) return;
        if (CARemoval.off || !CASync.on() || CASync.dormant()) { s.unmodify(KEY); return; }
        int own = Math.round(s.getModifiedValue()) - now();
        int b = bonus(CASync.managedCap(), own, admins());
        if (b > 0) s.modifyFlat(KEY, b, "Managed colonies (Auto Colonies)");
        else s.unmodify(KEY);
    }

    public static void remove() {
        MutableStat s = stat();
        if (s != null) s.unmodify(KEY);
    }
}
