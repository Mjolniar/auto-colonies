package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * 0.3.34.0 (GitHub #2): Take down buildings and Colony policies for conquered colonies, apart from your other colonies.
 * A conquered colony is yours now and started the game with another faction (Nexerelin's market memory $startingFactionId;
 * without Nexerelin no colony counts). Both settings default to "Main setting" (0), which reads the main settings.
 * Story mode's holds win: without the package that allows a row, the conquered setting is ignored and the held main
 * settings apply.
 */
public final class CAConquered {
    private CAConquered() { }

    /** Nexerelin's ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, read by name (no Nexerelin import). */
    static final String STARTING_FACTION = "$startingFactionId";
    public static final String[] TAKE_DOWN_NAMES = {"Main setting", "Never", "Clean up", "Free hand"};
    public static final String[] POLICY_NAMES = {"Main setting", "Auto", "Don't touch"};

    /** Pure: a starting owner that isn't you. */
    static boolean conquered(String startingFaction) {
        return startingFaction != null && !startingFaction.isEmpty() && !"player".equals(startingFaction);
    }

    /** Whether m is yours and started the game with another faction. */
    public static boolean conquered(MarketAPI m) {
        try { return m != null && m.isPlayerOwned() && conquered(m.getMemoryWithoutUpdate().getString(STARTING_FACTION)); }
        catch (Throwable t) { return false; }
    }

    /** Pure: the preset value a conquered setting gives (Take down: 0 Never, 1 Clean up, 2 Free hand; Policies: 0 Auto, 1 Don't touch), -1 for the main settings. */
    static int value(int setting, boolean conquered, boolean held, int names) {
        if (setting <= 0 || setting >= names || !conquered || held) return -1;
        return setting - 1;
    }

    /** Story mode holds this row (no package for it). */
    static boolean held(String ability) {
        try { return CASync.on() && !CASync.has(ability); } catch (Throwable t) { return false; }
    }

    static int takeDown(CASettings s, MarketAPI m) {
        if (s == null || s.conqTakeDown <= 0) return -1;
        return value(s.conqTakeDown, conquered(m), held("cleanup"), TAKE_DOWN_NAMES.length);
    }

    static int policies(CASettings s, MarketAPI m, String ability) {
        if (s == null || s.conqPolicies <= 0) return -1;
        return value(s.conqPolicies, conquered(m), held(ability), POLICY_NAMES.length);
    }

    // ---------------- the fields, per colony ----------------

    public static boolean noPrune(CASettings s, MarketAPI m) { int v = takeDown(s, m); return v < 0 ? s.noPrune : CAPresets.TD_NO[v]; }

    public static boolean endgameNoReplace(CASettings s, MarketAPI m) { int v = takeDown(s, m); return v < 0 ? s.endgameNoReplace : CAPresets.TD_NO[v]; }

    public static boolean capitalNoRoom(CASettings s, MarketAPI m) { int v = takeDown(s, m); return v < 0 ? s.capitalNoRoom : CAPresets.TD_NO[v]; }

    public static boolean allowRemovals(CASettings s, MarketAPI m) { int v = takeDown(s, m); return v < 0 ? s.allowRemovals : CAPresets.TD_REMOVALS[v]; }

    public static int hazardPayMode(CASettings s, MarketAPI m) { int v = policies(s, m, "policies"); return v < 0 ? s.hazardPayMode : v; }

    public static int freePortMode(CASettings s, MarketAPI m) { int v = policies(s, m, "policies"); return v < 0 ? s.freePortMode : v; }

    public static boolean disableEdicts(CASettings s, MarketAPI m) {
        if (held("indevo")) return s.disableEdicts;
        int v = policies(s, m, "policies");
        return v < 0 ? s.disableEdicts : v == 1;
    }

    /** Whether any colony could prune: the main setting allows it, or conquered colonies have their own. */
    public static boolean anyPrune(CASettings s) {
        return !s.noPrune || (s.conqTakeDown > 0 && !held("cleanup") && !CAPresets.TD_NO[Math.min(2, s.conqTakeDown - 1)]);
    }
}
