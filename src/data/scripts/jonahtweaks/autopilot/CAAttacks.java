package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Attacks on your colonies, only in a Nexerelin Derelict Empire start ($nex_scenarioId in sector memory is
 * derelict_empire or derelict_empire_v2; V2 runs V1's setup outside Corvus mode). That start hands every faction 0.8 of
 * an invasion's points on day 0, and a blockade needs only 0.75, so blockades start within weeks while the colony
 * is new. In any other game both settings are ignored and the game's own values stay. The recommended settings turn
 * them on in that start. Settings group of the same name:
 * - Pirate and crisis fleets: the game keeps up to maxHostileActivityFleetsPerSystem (11) Hostile Activity fleets in a
 *   system with your colonies, more the bigger and less stable they are. This lowers that cap. The value is the game's
 *   settings value read live by DisposableHostileActivityFleetManager; the game's own value is kept and put back when
 *   the option is off (and is what's in settings.json again at the next start).
 * - Nexerelin's invasions, raids and blockades (IX Revival's come through it too): Nexerelin skips your colonies as
 *   targets while your faction has $nex_recentlyTargetedMilitary, which it sets for 60 days at each attack, so attacks
 *   that last 120 days can overlap. While one is on its way to or at one of your colonies, this keeps the flag at the
 *   chosen gap, so the next can only start that many days after it ends. Nexerelin's hard mode ignores the flag.
 */
public class CAAttacks {
    static final String HA_KEY = "maxHostileActivityFleetsPerSystem";
    static final String NEX_COOLDOWN = "$nex_recentlyTargetedMilitary";   // InvasionFleetManager.MEM_KEY_FACTION_TARGET_COOLDOWN
    static final String SCENARIO = "$nex_scenarioId";   // ScenarioManager.MEMORY_KEY_SCENARIO
    public static final int REC_CAP = 4, REC_GAP = 90;
    static Float haOwn, haSet;

    /** This game is a Nexerelin Derelict Empire start. */
    public static boolean forThisStart() {
        try {
            if (!Global.getSettings().getModManager().isModEnabled("nexerelin")) return false;
            Object id = Global.getSector().getMemoryWithoutUpdate().get(SCENARIO);
            return "derelict_empire".equals(id) || "derelict_empire_v2".equals(id);
        } catch (Throwable t) { return false; }
    }

    /** Daily and on load. */
    public static void apply(CASettings s) {
        try { fleetCap(s); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "pirate and crisis fleet cap failed", t); }
        try { if (Global.getSettings().getModManager().isModEnabled("nexerelin")) nexGap(s); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Nexerelin attack spacing failed", t); }
    }

    /** The game's own fleet cap: what was there before Autopilot set it, or what something else has set since. */
    public static int gameCap() {
        try {
            float cur = Global.getSettings().getFloat(HA_KEY);
            return Math.round(haSet != null && cur == haSet && haOwn != null ? haOwn : cur);
        } catch (Throwable t) { return 11; }
    }

    static void fleetCap(CASettings s) {
        SettingsAPI g = Global.getSettings();
        float cur = g.getFloat(HA_KEY);
        if (haSet == null || cur != haSet) haOwn = cur;   // not ours: the game's (or another mod's) value
        float want = s.haFleetCap > 0 && forThisStart() ? Math.min(haOwn, s.haFleetCap) : haOwn;
        if (cur != want) g.setFloat(HA_KEY, want);
        haSet = want;
    }

    static void nexGap(CASettings s) {
        if (s.nexAttackGap <= 0 || !forThisStart() || !attackUnderWay()) return;
        MemoryAPI mem = Global.getSector().getPlayerFaction().getMemoryWithoutUpdate();
        float left = mem.contains(NEX_COOLDOWN) ? mem.getExpire(NEX_COOLDOWN) : 0f;
        if (left >= 0f && left < s.nexAttackGap) mem.set(NEX_COOLDOWN, true, s.nexAttackGap);   // never shortens Nexerelin's own
    }

    /** A hostile raid, invasion or blockade on its way to or at one of your colonies' systems. */
    public static boolean attackUnderWay() {
        java.util.Set<StarSystemAPI> seen = new java.util.HashSet<StarSystemAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            StarSystemAPI sys = m.getStarSystem();
            if (sys != null && seen.add(sys) && CASiege.incoming(sys) != null) return true;
        }
        return false;
    }

    /** "Game's own (11)" or "At most 4". */
    public static String capName(CASettings s) { return s.haFleetCap > 0 ? "At most " + s.haFleetCap : "Game's own (" + gameCap() + ")"; }

    public static String gapName(CASettings s) { return s.nexAttackGap > 0 ? "One at a time, " + s.nexAttackGap + " days apart" : "Game's own (can overlap)"; }
}
