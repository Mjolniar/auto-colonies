package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;

/**
 * 0.3.36.0: Tech, which technology the empire runs on (Settings, Playstyle), beside Balance and Profile:
 * - Mixed: stations by score, AI cores as Profile sets them, every colony item.
 * - High tech: the high-tech station line, AI cores as Profile sets them (never Off).
 * - Luddic: the low-tech station line, AI cores Off, and no colony item the Luddic Path counts (its pather tags). The game's
 *   Pather interest in a building is its AI core and its item only (BaseIndustry.getPatherInterest), so a Luddic colony gives
 *   the cells nothing. Items already installed stay where they are.
 * CASettings.tech holds it: 0 Mixed (every save before it), 1 High tech, 2 Luddic. It writes stationLine and, for Luddic and
 * back, the AI cores row; Profile leaves AI cores Off while Tech is Luddic (CAProfile.apply and match).
 */
public final class CATech {
    private CATech() { }

    public static final String[] NAMES = {"Mixed", "High tech", "Luddic"};
    public static final int MIXED = 0, HIGH = 1, LUDDIC = 2;
    /** CASettings.stationLine by value. */
    static final String[] LINE = {"score", "high", "low"};
    /** One sentence each, for the settings descriptions. */
    public static final String[] DESC = {
            "Mixed picks stations by score and uses every AI core and colony item your other settings allow.",
            "High tech builds the high-tech station line and uses AI cores as your Profile sets them.",
            "Luddic builds low-tech stations and uses no AI cores and no colony items the Luddic Path cares about, so Pather cells have nothing to go after."};

    public static int of(CASettings s) { return s == null ? MIXED : Math.max(MIXED, Math.min(LUDDIC, s.tech)); }

    static int now() {
        try { return of(CAData.settings()); } catch (Throwable t) { return MIXED; }   // offline checks: Mixed
    }

    public static boolean luddic() { return now() == LUDDIC; }

    public static String name(CASettings s) { return NAMES[of(s)]; }

    /** The settings row's value: the name and what it sets. */
    static String label(CASettings s) {
        int v = of(s);
        return v == HIGH ? "High tech (high-tech stations)" : v == LUDDIC ? "Luddic (low-tech stations, no AI cores or Pather items)" : "Mixed (stations by score)";
    }

    public static int index(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
        return -1;
    }

    /** Pure: the AI cores row value to set when Tech changes, or -1 to leave it (profile: CAProfile.match before the change, -1 Custom). */
    static int coresFor(int was, int v, int coresNow, int profile) {
        if (v == LUDDIC) return 0;
        if (was == LUDDIC || coresNow == 0) return CAProfile.AI_CORES[profile < 0 ? CAProfile.NORMAL : profile];
        return -1;
    }

    public static void apply(CASettings s, int v) {
        if (s == null || v < 0 || v >= NAMES.length) return;
        int was = of(s), profile = CAProfile.match(s), cores = CAPresets.match(s, CAPresets.AI_CORES);
        s.tech = v;
        s.stationLine = LINE[v];
        int set = coresFor(was, v, cores, profile);
        if (set >= 0) CAPresets.apply(s, CAPresets.AI_CORES, set);
        CAAuto.nextPass.clear();
    }

    /** The Settings page's click: Mixed, High tech, Luddic, then Mixed again. */
    public static void cycle(CASettings s) { apply(s, (of(s) + 1) % NAMES.length); }

    /** Luddic: an item the Luddic Path counts is never installed (CAItems.itemScore). */
    static boolean blocksItem(String id) {
        if (!luddic() || id == null) return false;
        try {
            SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(id);
            return spec != null && CALowProfile.itemInterest(spec) > 0;
        } catch (Throwable t) { return false; }
    }
}
