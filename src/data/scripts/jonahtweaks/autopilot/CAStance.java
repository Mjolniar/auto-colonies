package data.scripts.jonahtweaks.autopilot;

/**
 * 0.3.0.0 (WS-E, research 06 section 4.3): the faction stance scale. One empire-wide table, CASettings.factionStance (faction
 * id to stance); a faction with no entry is Auto. Pure: no Nexerelin and no game classes, so the settings screen loads
 * without Nexerelin and every rule here is checked offline (JTStanceCheck).
 * <pre>
 *  stance    value  highest War level used on it   relations (agents, IndEvo ambassadors)          ceasefire offers
 *  Ally       -3    Defend (never attacked)        raised first, also while hostile, no stop line   accepted
 *  Friendly   -2    Defend                         raised up to the stop line, also while hostile   accepted
 *  Neutral    -1    Defend (no strike back)        raised only while below 0, also while hostile    accepted
 *  Auto        0    Retaliate                      as 0.2.45: by the focus, never while hostile       left to you
 *  Rival      +1    Hold them down                 not raised                                       left to you
 *  Enemy      +2    Conquer                        not raised; sabotage, isolation                  rejected
 *  Destroy    +3    Scorched                       not raised; sabotage, isolation, rebellion       rejected
 * </pre>
 * Enemy and Destroy only act on a faction that is hostile to you. War on a faction that isn't hostile needs the War
 * page's Declare war confirmation (CADiplomacy.declareWar); until then the stance acts as Rival.
 * 0.3.0.0 (fix): a stance a save from before 0.3.0 got from its faction priority (High to Friendly or Ally) keeps 0.2.45's
 * diplomacy until you set it yourself: offers left to you and no raising while hostile (fromOldPriority). The rules above
 * are pure; markSetByYou and setByYou read and write one saved stamp through CAMark.
 */
public class CAStance {
    public static final int ALLY = -3, FRIENDLY = -2, NEUTRAL = -1, AUTO = 0, RIVAL = 1, ENEMY = 2, DESTROY = 3;
    public static final String[] NAMES = {"Ally", "Friendly", "Neutral", "Auto", "Rival", "Enemy", "Destroy"};

    /** Relations past this are never reached (they run -1 to 1): "never stop raising". */
    public static final float NEVER_STOP = 2f;
    /** Relations under this are never reached: "never raise". */
    public static final float NEVER_RAISE = -2f;

    public static int clamp(int stance) { return Math.max(ALLY, Math.min(DESTROY, stance)); }

    public static String name(int stance) { return NAMES[clamp(stance) + 3]; }

    /** Highest War level (CAWarPolicy) Auto Colonies may use on a faction with this stance. */
    public static int cap(int stance) {
        int st = clamp(stance);
        if (st <= NEUTRAL) return CAWarPolicy.DEFEND;
        if (st == AUTO) return CAWarPolicy.RETALIATE;
        if (st == RIVAL) return CAWarPolicy.HOLD;
        if (st == ENEMY) return CAWarPolicy.CONQUER;
        return CAWarPolicy.SCORCHED;
    }

    /** One step friendlier (dir -1) or more hostile (dir +1), within Ally..Destroy. */
    public static int step(int stance, int dir) { return clamp(stance + dir); }

    /** An old faction priority as a stance: High (2) Friendly, Ignore (0) Rival, Normal (1) or none Auto. */
    public static int fromPriority(Integer p) {
        if (p == null) return AUTO;
        if (p >= 2) return FRIENDLY;
        if (p <= 0) return RIVAL;
        return AUTO;
    }

    /** The old ambassador priority this stance gives (CAIndEvo, CASpies): Ally and Friendly 2 High, Neutral and Auto 1 Normal, the rest 0 Ignore. */
    public static int ambassadorPriority(int stance) {
        int st = clamp(stance);
        if (st <= FRIENDLY) return 2;
        if (st <= AUTO) return 1;
        return 0;
    }

    /** The ambassador priority of a faction from your stance table (Normal with no settings). */
    public static int priorityOf(CASettings s, String factionId) {
        return ambassadorPriority(s == null ? AUTO : s.stanceOf(factionId));
    }

    /**
     * Relations at which raising stops: Ally never stops; Friendly and Auto at your "Stop raising a faction at" line; Neutral
     * at 0 (only kept out of hostility); Rival, Enemy and Destroy are never raised.
     */
    public static float raiseUntil(int stance, float stopAt) {
        int st = clamp(stance);
        if (st == ALLY) return NEVER_STOP;
        if (st == FRIENDLY || st == AUTO) return stopAt;
        if (st == NEUTRAL) return 0f;
        return NEVER_RAISE;
    }

    /** Whether relations are raised while the faction is hostile to you: Ally, Friendly and Neutral (the paid way to peace). */
    public static boolean raiseWhileHostile(int stance) { return clamp(stance) <= NEUTRAL; }

    /** Order for picking whom to raise relations with: Ally 3, Friendly 2, Neutral 1, Auto 0; -1 never. */
    public static int raiseRank(int stance) {
        int st = clamp(stance);
        if (st >= RIVAL) return -1;
        return -st;
    }

    /** A ceasefire or peace offer from a faction with this stance: 1 accept, -1 reject, 0 left to you. */
    public static int ceasefire(int stance) { return stance <= NEUTRAL ? 1 : stance >= ENEMY ? -1 : 0; }

    // ---------------- stances that came from a 0.2.45 faction priority (0.3.0.0 fix) ----------------

    /**
     * Whether a stance came from a 0.2.45 faction priority (CAMigrate.stanceFor: High is Friendly or Ally, Ignore is Rival) and
     * you haven't set it since: the save was moved from before 0.3.0, the old priority is there, the stance is the one it gives,
     * and the War page or the settings never marked it as yours (setByYou).
     */
    public static boolean fromOldPriority(int stance, Integer oldPriority, boolean migrated, boolean setByYou) {
        if (!migrated || setByYou || oldPriority == null) return false;
        int st = clamp(stance);
        if (oldPriority >= 2) return st == FRIENDLY || st == ALLY;
        if (oldPriority <= 0) return st == RIVAL;
        return false;
    }

    /**
     * The ceasefire answer with 0.2.45's rule for a stance from an old priority: 0.2.45 never answered offers (left to you, 0).
     * Keeps an existing save no more aggressive than 0.2.45.1 until you set the stance yourself.
     */
    public static int ceasefire(int stance, boolean fromOldPriority) { return fromOldPriority ? 0 : ceasefire(stance); }

    /** raiseWhileHostile with 0.2.45's rule for a stance from an old priority: 0.2.45 never raised relations with a hostile faction. */
    public static boolean raiseWhileHostile(int stance, boolean fromOldPriority) { return !fromOldPriority && raiseWhileHostile(stance); }

    /** CAMark key that records a stance you set on the War page or in the settings ("stanceset:<faction id>"). */
    public static final String SET_MARK = "stanceset:";

    /**
     * Records that you set this faction's stance (a saved stamp, CAMark; nothing with no save). Called by the War page and the
     * settings screen's stance buttons. No Nexerelin classes, so the settings screen can call it without Nexerelin.
     */
    public static void markSetByYou(String factionId) {
        if (factionId == null) return;
        try { CAMark.set(SET_MARK + factionId, CAMark.now()); } catch (Throwable ignored) { }
    }

    /** Whether you set this faction's stance since 0.3.0 (markSetByYou). */
    public static boolean setByYou(String factionId) {
        if (factionId == null) return false;
        try { return CAMark.at(SET_MARK + factionId) != null; } catch (Throwable t) { return false; }
    }

    /** fromOldPriority for a faction with this save's settings and data (false with no settings). */
    public static boolean fromOldPriority(CASettings s, CAData d, String factionId) {
        if (s == null || d == null || factionId == null) return false;
        Integer p = s.factionPriority == null ? null : s.factionPriority.get(factionId);
        return fromOldPriority(s.stanceOf(factionId), p, d.migratedAt != 0L, setByYou(factionId));
    }

    /** Enemy and Destroy: war is wanted (declared on the War page when the faction isn't hostile yet). */
    public static boolean wantsWar(int stance) { return stance >= ENEMY; }

    /** Whether Auto Colonies may attack this faction at all (base strikes, raids, invasions): Auto and up. */
    public static boolean attackable(int stance) { return stance >= AUTO; }

    /** Ally and Friendly: never the third party in an isolation operation. */
    public static boolean friendly(int stance) { return stance <= FRIENDLY; }

    /** Colour for the stance on screen: 1 positive (Ally, Friendly), -1 negative (Enemy, Destroy), 0 the highlight colour. */
    public static int tone(int stance) {
        int st = clamp(stance);
        return st <= FRIENDLY ? 1 : st >= ENEMY ? -1 : 0;
    }
}
