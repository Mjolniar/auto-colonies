package data.scripts.jonahtweaks.autopilot;

/**
 * 0.3.14.0: Profile, three ways to run the empire, each setting several rows at once (CAPresets rows and fields):
 * - Low profile: Low profile on, no new colonies, AI cores Safe, War Defend.
 * - Normal: expansion into the systems you hold, AI cores where they can be defended (Defended), War Retaliate, so crises
 *   are fought rather than avoided.
 * - High profile: expansion as wide and fast as it goes (Within 15 ly of the capital, the Expansion rung On so Auto doesn't
 *   ask first), AI cores No limit, War Retaliate.
 * It has no field of its own: the menu shows the value whose fields all match, else Custom. It isn't a CAPresets row, so
 * Mode default and the recommended settings don't apply to it (Reset to recommended leaves New colonies Off).
 */
public final class CAProfile {
    private CAProfile() { }

    public static final String[] NAMES = {"Low profile", "Normal", "High profile"};
    public static final String CUSTOM = CAPresets.CUSTOM_NAME;
    public static final int LOW = 0, NORMAL = 1, HIGH = 2;
    static final boolean[] LOW_PROFILE = {true, false, false};
    /** CAPresets values: New colonies (Off, Ask first, Auto), AI cores (Off, Safe, Small colonies only, Defended, No limit). */
    static final int[] NEW_COLONIES = {0, 2, 2}, AI_CORES = {1, CAPresets.CORES_DEFENDED, 4};
    static final int[] WAR = {CAWarPolicy.DEFEND, CAWarPolicy.RETALIATE, CAWarPolicy.RETALIATE};
    /** expandScope (CASettings): Systems you hold, Those first then nearby systems, Within 15 ly of the capital. */
    static final int[] SCOPE = {0, 0, 2};
    /** One sentence each, for the new-game pop-up. */
    public static final String[] DESC = {
            "Low profile keeps crises slow and earnings lower, with no new colonies, AI cores only in small colonies and no free ports, raids or sabotage.",
            "Normal expands steadily into systems you already hold, places AI cores where they can be defended and fights crises head on.",
            "High profile expands as fast and as wide as it can, uses every AI core you have and founds colonies without asking first."};

    /** The fields Profile writes (its rows' fields, Low profile and where new colonies go, and the money ladder rungs). */
    public static String[] fields() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        out.add("lowProfile");
        out.addAll(java.util.Arrays.asList(CAPresets.fields(CAPresets.NEW_COLONIES)));
        out.addAll(java.util.Arrays.asList(CAPresets.fields(CAPresets.AI_CORES)));
        out.addAll(java.util.Arrays.asList(CAPresets.fields(CAPresets.WAR)));
        out.add("expandScope");
        out.add("rungMode");
        return out.toArray(new String[0]);
    }

    static int expansionRung() {
        for (int i = 0; i < CARich.RUNGS.length; i++) if ("Expansion".equals(CARich.RUNGS[i])) return i;
        return -1;
    }

    static boolean expansionOn(CASettings s) {
        int i = expansionRung();
        return i >= 0 && s.rungMode != null && i < s.rungMode.length && s.rungMode[i] == CARich.ON;
    }

    public static void apply(CASettings s, int v) {
        if (s == null || v < 0 || v >= NAMES.length) return;
        CALowProfile.set(s, LOW_PROFILE[v], "Profile");
        CAPresets.apply(s, CAPresets.NEW_COLONIES, NEW_COLONIES[v]);
        CAPresets.apply(s, CAPresets.AI_CORES, AI_CORES[v]);
        CAPresets.apply(s, CAPresets.WAR, WAR[v]);
        s.expandScope = SCOPE[v];
        if (v == HIGH) {
            int i = expansionRung();
            if (i >= 0) {
                int[] r = s.rungMode == null ? CASettings.defaultRungs() : s.rungMode.clone();
                if (i < r.length) { r[i] = CARich.ON; s.rungMode = r; }
            }
        }
    }

    /** 0.3.14.0: marks the profile's fields set by you, so the empire mode's column never overwrites them (the new-game pop-up). */
    public static void markSet(CASettings s, int v) {
        if (s == null || v < 0 || v >= NAMES.length) return;
        for (String f : fields()) if (!"rungMode".equals(f)) s.playerSet().add(f);
        int i = expansionRung();
        if (v == HIGH && i >= 0) s.playerSet().add("rungMode[" + i + "]");
    }

    /** The profile whose fields all match, or -1 (Custom). High profile also needs the Expansion rung On. */
    public static int match(CASettings s) {
        if (s == null) return -1;
        for (int v = 0; v < NAMES.length; v++) {
            if (s.lowProfile != LOW_PROFILE[v] || s.expandScope != SCOPE[v]) continue;
            if (CAPresets.match(s, CAPresets.NEW_COLONIES) != NEW_COLONIES[v] || CAPresets.match(s, CAPresets.AI_CORES) != AI_CORES[v]) continue;
            if (CAWarPolicy.clamp(s.warLevel) != WAR[v]) continue;
            if (v == HIGH && !expansionOn(s)) continue;
            return v;
        }
        return -1;
    }

    public static String name(CASettings s) {
        int v = match(s);
        return v >= 0 ? NAMES[v] : CUSTOM;
    }

    public static int index(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
        return -1;
    }

    /** The Settings page's click: Low profile, Normal, High profile, then Low profile again (Custom goes to Normal). */
    public static void cycle(CASettings s) {
        int v = match(s);
        apply(s, v < 0 ? NORMAL : (v + 1) % NAMES.length);
    }
}
