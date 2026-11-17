package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.combat.MutableStat;

/**
 * 0.3.9.9: the Rapid research package (story mode, Tier IV, 20 bandwidth, needs Ashes of the Domain: Vaults of Knowledge).
 * VoK's research takes timeToResearch x its speed setting / (1 + the player manager's researchSpeedBonus)
 * (AoDUtilis.getDaysFromResearch, VoK 5.0.7). The package adds KEY to that MutableStat: +1/3 flat, then x1.5, so
 * (1 + bonus) becomes 1.5 x (1 + bonus) and every project takes 2/3 of its time. The items a project needs
 * (payForResearch: databanks, cores, colony items) are left as they are.
 * - Never in a save: remove() runs before every save (JTModPlugin.beforeGameSave) and sync() after it and on load, so a
 *   save without Auto Colonies has no trace of it. Prepare for removal takes it off too (CARemoval.prepare).
 * - sync(): on while the package's ability runs (CASync.has: story mode on, not dormant, the package bought), off otherwise;
 *   daily (CASync.daily) and on load.
 */
public final class CARapidResearch {
    private CARapidResearch() { }

    public static final String ABILITY = "rapidResearch", KEY = "ac_rapid_research";
    /** Research takes this share of its time with the package. */
    static final float TIME = 2f / 3f;
    static final float FLAT = 1f / 3f, MULT = 1.5f;

    /** Pure: the speed bonus with the package, from the bonus without it. */
    static float withPackage(float bonus) { return (bonus + FLAT) * MULT; }

    /** Pure: the share of its time a project takes, from the bonus without and with the package (VoK's days formula). */
    static float timeShare(float without, float with) { return (1f + without) / (1f + with); }

    /** VoK's player research speed stat, or null without VoK or when it can't be read. */
    static MutableStat stat() {
        if (!CASyncCatalog.modOn("aotd_vok") || !CAResearch.link()) return null;
        try {
            Object mgr = CARefl.invoke(CAResearch.player, CARefl.invoke(CAResearch.instance, null));
            Object st = mgr == null ? null : CARefl.call(mgr, "getResearchSpeedBonus");
            return st instanceof MutableStat ? (MutableStat) st : null;
        } catch (Throwable t) { return null; }
    }

    /** On while the package runs, off otherwise. */
    public static void sync() {
        boolean on;
        try { on = !CARemoval.off && CASync.has(ABILITY); } catch (Throwable t) { on = false; }
        if (on) apply(); else remove();
    }

    static void apply() { apply(stat()); }

    /** Puts the package's change on st (JTRapidResearchCheck runs it on a plain MutableStat). */
    static void apply(MutableStat st) {
        if (st == null) return;
        st.modifyFlat(KEY, FLAT, "Rapid research");
        st.modifyMult(KEY, MULT, "Rapid research");
    }

    /** Takes the package's change off VoK's stat (before a save, on Prepare for removal, when the package is gone). */
    public static void remove() { remove(stat()); }

    static void remove(MutableStat st) {
        if (st == null) return;
        st.unmodifyFlat(KEY);
        st.unmodifyMult(KEY);
    }
}
