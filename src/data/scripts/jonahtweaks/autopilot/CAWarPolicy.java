package data.scripts.jonahtweaks.autopilot;

/**
 * 0.3.0.0 (WS-E, research 06 sections 4.2 and 4.4-4.8): the War level and the war rules. Pure: no Nexerelin and no game
 * classes (the settings screen loads without Nexerelin), checked offline by JTStanceCheck, JTConquestCheck and JTWarSim.
 *
 * War level (CASettings.warLevel, one empire-wide setting):
 * <pre>
 *  Off             nothing (CADefense's emergency fleets keep their own setting)
 *  Defend          relief, proactive defense, pirate and Pather base strikes, standing guard while rich (0.2.45 "Protect")
 *  Retaliate       + strike back at markets that attacked you, retake your own lost colonies (0.2.45 default)
 *  Hold them down  + raids and blockades on Rival, Enemy and Destroy markets in reach, sabotage
 *  Conquer         + invasions of Enemy and Destroy markets
 *  Scorched        + saturation bombardment of Destroy capitals, once you authorize it on the War page
 * </pre>
 * effective() gives the highest level used on one faction: the War level, capped by the faction's stance (CAStance.cap)
 * and by the safety rules (allies, your commissioner, ceasefires, Low profile, a commission, no war it didn't start).
 */
public class CAWarPolicy {
    public static final int OFF = 0, DEFEND = 1, RETALIATE = 2, HOLD = 3, CONQUER = 4, SCORCHED = 5;
    public static final String[] LEVEL_NAMES = {"Off", "Defend", "Retaliate", "Hold them down", "Conquer", "Scorched"};

    // ---------------- the numbers ----------------

    /** Infamy x (1 + dominance) stays under this (Nexerelin's ContainAggressionConcern ends under 75 and starts at 100). */
    public static final float INFAMY_LINE = 75f;
    /** A log line when infamy passes this. */
    public static final float INFAMY_NEAR = 60f;
    /** Auto Colonies' fee on the new offensive operations: the fee setting applied to this share of Nexerelin's price (DECISIONS 10). */
    public static final float FEE_SHARE = 0.1f;
    /** Invasion fleets and marines are sized this much over Nexerelin's own estimate. */
    public static final float FP_MARGIN = 1.25f, MARINE_MARGIN = 1.25f;
    /** Nexerelin's price per marine on an invasion request (a quarter of the marines' base price, 200). */
    public static final float MARINE_PRICE = 50f;
    /** Nexerelin's fleet request limits on marines (InvasionIntel 100..MAX_MARINES_TOTAL). */
    public static final int MIN_MARINES = 100, MAX_MARINES = 16000;
    /** 03 F10: proactive defense and standing guard send at most one fleet per system in this many days. */
    public static final float DEFENSE_COOLDOWN_DAYS = 45f;
    /** Raids and blockades under way against one faction at a time. */
    public static final int RAIDS_PER_FACTION = 2;
    /** Days between two saturation bombardments, and how many may be under way. */
    public static final float BOMB_GAP_DAYS = 120f;
    public static final int BOMBS_AT_ONCE = 1;
    /** Days between two war declarations (Nexerelin's MIN_INTERVAL_BETWEEN_WARS), and between two keep-hostile re-declarations of one faction. */
    public static final float DECLARE_GAP_DAYS = 30f, KEEP_HOSTILE_DAYS = 30f;
    /** An Enemy faction's markets are invaded only when the invasion costs at most this share of the surplus. */
    public static final float OPPORTUNISTIC_SHARE = 0.25f;
    /** A conquered colony goes back to its goal at this stability, and blocks the next invasion under UNSTABLE_UNDER or with rebels at REBELS_BLOCK. */
    public static final float STABLE_AT = 5f, UNSTABLE_UNDER = 3f, REBELS_BLOCK = 0.5f;
    /** Days a conquered colony is looked after at least, and days an operation stays in the saved record. */
    public static final float SETTLE_DAYS = 90f, OP_MEMORY_DAYS = 365f;
    /** Price of one unit of fuel for a bombardment (its base price). */
    public static final float FUEL_PRICE = 25f;
    /** Fuel a bombardment fleet carries per fleet point when the fight is auto-resolved (Misc.FP_TO_BOMBARD_COST_APPROX_MULT). */
    public static final float FUEL_PER_FP = 12f;
    /**
     * 0.3.0.0 (fix): days a blockade Auto Colonies sent keeps its market off the hold-down list, from the saved record:
     * Nexerelin's organize time and travel plus its 120 days on station (BlockadeWrapperIntel payloadDays).
     */
    public static final float BLOCKADE_DAYS = 150f;
    /**
     * 0.3.0.0 (fix, 06 section 4.7): days after a bombardment of a Destroy capital lands in which that capital is not bombarded
     * again and is invaded first (a saturation bombardment disrupts every industry for 365 days or more).
     */
    public static final float BOMBED_DAYS = 365f;

    // ---------------- kinds of operation ----------------

    public static final String RELIEF = "relief", DEFENSE = "defense", GUARD = "guard", STRIKE = "strike", RAID = "raid",
            RETAKE = "retake", HOLD_RAID = "holdraid", BLOCKADE = "blockade", INVASION = "invasion", SATBOMB = "satbomb",
            EMERGENCY = "emergency";

    public static int clamp(int level) { return Math.max(OFF, Math.min(SCORCHED, level)); }

    public static String levelName(int level) { return LEVEL_NAMES[clamp(level)]; }

    /** The lowest War level that uses this kind of operation (SCORCHED + 1 for an unknown kind: never). */
    public static int levelOf(String kind) {
        if (RELIEF.equals(kind) || DEFENSE.equals(kind) || GUARD.equals(kind) || STRIKE.equals(kind) || EMERGENCY.equals(kind)) return DEFEND;
        if (RAID.equals(kind) || RETAKE.equals(kind)) return RETALIATE;
        if (HOLD_RAID.equals(kind) || BLOCKADE.equals(kind)) return HOLD;
        if (INVASION.equals(kind)) return CONQUER;
        if (SATBOMB.equals(kind)) return SCORCHED;
        return SCORCHED + 1;
    }

    /** Operations against another faction's markets (the rest defend your own colonies). */
    public static boolean offensive(String kind) {
        return STRIKE.equals(kind) || RAID.equals(kind) || RETAKE.equals(kind) || HOLD_RAID.equals(kind) || BLOCKADE.equals(kind)
                || INVASION.equals(kind) || SATBOMB.equals(kind);
    }

    /** The operations new in 0.3.0 carry Auto Colonies' fee; 0.2.45's defensive fleets, base strikes and strikes back don't (DECISIONS 10). */
    public static boolean feeApplies(String kind) {
        return RETAKE.equals(kind) || HOLD_RAID.equals(kind) || BLOCKADE.equals(kind) || INVASION.equals(kind) || SATBOMB.equals(kind);
    }

    /** Whether this kind of operation may go against a faction at this effective level and stance (never an attack on Ally, Friendly or Neutral). */
    public static boolean allows(String kind, int effective, int stance) {
        return effective >= levelOf(kind) && (!offensive(kind) || CAStance.attackable(stance));
    }

    /** 0.3.0.0 (fix): the operations that take a market: an invasion, and a retake of a colony that was yours. */
    public static boolean takesMarket(String kind) { return INVASION.equals(kind) || RETAKE.equals(kind); }

    /**
     * allows() with your commission: 0.3.0.0 (fix, 06 section 7.6) no invasion of any kind while you hold one. Nexerelin gives a
     * market a requested fleet captures to your commissioner (GroundBattleIntel.handleTransfer; InvasionIntel never sets
     * playerInitiated), so you would pay for your commissioner's colony.
     */
    public static boolean allows(String kind, int effective, int stance, boolean commissioned) {
        if (commissioned && takesMarket(kind)) return false;
        return allows(kind, effective, stance);
    }

    // ---------------- permission ----------------

    /**
     * The highest War level Auto Colonies may use against one faction:
     * - Off when the War level is Off;
     * - the War level, capped by the stance (CAStance.cap);
     * - Defend for an ally (or the Persean League while you're a member), your commissioner or a faction not hostile to it,
     *   and a faction with a ceasefire;
     * - Defend for a faction not hostile to you, unless it's Enemy or Destroy and you confirmed the war (CM never starts one);
     * - Defend in Low profile (pirate and Pather base strikes stay, as in 0.2.45);
     * - Hold them down at most while you hold a commission (Nexerelin hands conquests to the commissioner). Retakes sit at
     *   Retaliate, under that cap, so allows() with commissioned blocks them separately (0.3.0.0 fix).
     */
    public static int effective(int level, int stance, boolean hostile, boolean authorized, boolean allied, boolean commissioner,
                                boolean ceasefire, boolean lowProfile, boolean commissioned) {
        int lvl = clamp(level);
        if (lvl == OFF) return OFF;
        lvl = Math.min(lvl, CAStance.cap(stance));
        if (allied || commissioner || ceasefire) return Math.min(lvl, DEFEND);
        if (!hostile && !(CAStance.wantsWar(stance) && authorized)) return Math.min(lvl, DEFEND);
        if (lowProfile) return Math.min(lvl, DEFEND);
        if (commissioned) return Math.min(lvl, HOLD);
        return lvl;
    }

    /** Offense agents may sabotage: Auto factions from Retaliate (0.2.45's offense), Rival and up from Hold them down. */
    public static boolean sabotageOk(int effective, int stance) {
        int st = CAStance.clamp(stance);
        if (st == CAStance.AUTO) return effective >= RETALIATE;
        return st >= CAStance.RIVAL && effective >= HOLD;
    }

    /** "Pick rivals automatically": a hostile Auto faction's threat (attacks on you in 120 days x 3, its markets near you, its offensives at you x 5). */
    public static float rivalThreat(int attacks, int nearMarkets, int offensives) {
        return 3f * Math.max(0, attacks) + Math.max(0, nearMarkets) + 5f * Math.max(0, offensives);
    }

    // ---------------- infamy (Nexerelin's "badboy") ----------------

    /** Infamy a capture adds (DiplomacyManager.notifyMarketCaptured): size squared x 2, none when the market's original owner is allied with you (or is you). */
    public static float captureInfamy(int size, boolean alliedWithOriginal) { return alliedWithOriginal ? 0f : size * size * 2f; }

    /** Infamy a saturation bombardment adds (ColonyManager.reportSatBomb): size squared x 4, none under limited hatred. */
    public static float bombInfamy(int size, boolean limited) { return limited ? 0f : size * size * 4f; }

    /** Whether infamy stays under the coalition line after adding add: (badboy + add) x (1 + dominance) under 75, or you allowed a coalition. */
    public static boolean infamyOk(float badboy, float dominance, float add, boolean coalitionOK) {
        return coalitionOK || (Math.max(0f, badboy) + Math.max(0f, add)) * (1f + Math.max(0f, dominance)) < INFAMY_LINE;
    }

    /** Infamy as the coalition test counts it. */
    public static float infamy(float badboy, float dominance) { return Math.max(0f, badboy) * (1f + Math.max(0f, dominance)); }

    /**
     * Nexerelin's dominance (your market sizes over every market's, DiplomacyManager.getDominanceFactor) after your side gains
     * addSize and the sector loses lostSize (a decivilized market): a capture raises it too, so the infamy test uses this.
     * Scales the factor itself, so Nexerelin's hard-mode multiplier carries over; counting only your own colonies as ourSize
     * (not an alliance's) makes it rise faster, never slower.
     */
    public static float projectedDominance(float dom, int ourSize, int addSize, int globalSize, int lostSize) {
        int g = globalSize - Math.max(0, lostSize);
        if (g <= 0) return Math.max(0f, dom);
        int add = Math.max(0, addSize);
        if (ourSize <= 0 || dom <= 0f) return Math.max(0f, dom) + (float) add / g;
        return dom * (ourSize + add) / ourSize * globalSize / g;
    }

    // ---------------- sizes and prices ----------------

    /**
     * Marines for an invasion, Nexerelin's own rule (InvasionIntel.setMarineCount) with a margin on top: garrison over the
     * garrison size setting, x 0.75 rounded up, x 1.3 when a tactical bombardment isn't expected, x the troop size setting,
     * within 100..16,000; then x margin, within 16,000. Whole numbers truncate the way Nexerelin's int arithmetic does.
     */
    public static int marines(float garrison, float garrisonMult, float troopMult, boolean bombable, float margin) {
        float g = Math.max(0f, garrison) / (garrisonMult > 0f ? garrisonMult : 1f);
        int m = (int) Math.ceil(g * 0.75f);
        if (!bombable) m = (int) (m * 1.3f);
        m = (int) (m * (troopMult > 0f ? troopMult : 1f));
        m = clampMarines(m);
        return clampMarines((int) Math.ceil(m * Math.max(1f, margin)));
    }

    static int clampMarines(int m) { return Math.max(MIN_MARINES, Math.min(MAX_MARINES, m)); }

    /** Nexerelin's invasion price (Nex_FleetRequest.updateCost): fleet points x the price per point + marines x 50. */
    public static float invasionCost(float fp, int marines, float perFP) { return invasionCost(fp, marines, perFP, MARINE_PRICE); }

    public static float invasionCost(float fp, int marines, float perFP, float perMarine) { return fp * perFP + marines * perMarine; }

    /** Auto Colonies' fee on an operation: the fee level's multiplier on FEE_SHARE of the price, for the new offensive kinds only. */
    public static float fee(String kind, float price, float feeMult) { return feeApplies(kind) ? FEE_SHARE * Math.max(0f, price) * Math.max(0f, feeMult) : 0f; }

    /** Bombardment fleet: 1.25 x Nexerelin's wanted size, and big enough to carry twice the fuel the bombardment needs. */
    public static float satBombFP(float wanted, float bombardFuel) {
        return Math.max(FP_MARGIN * wanted, (float) Math.ceil(2f * bombardFuel / FUEL_PER_FP));
    }

    /** Bombardment price: fleet points x the price per point + the fuel it burns at its base price. */
    public static float satBombCost(float fp, float perFP, float fuel, float fuelPrice) { return fp * perFP + Math.max(0f, fuel) * fuelPrice; }

    // ---------------- what a bombardment costs you (06 section 4.6, Nex_MarketCMD.bombardConfirm) ----------------

    /** ensureAtBest codes for atrocityImpact: no cap, Vengeful, Hostile, Neutral. */
    public static final int AT_NONE = 0, AT_VENGEFUL = 1, AT_HOSTILE = 2, AT_NEUTRAL = 3;

    /**
     * {delta, ensureAtBest code} for one faction's reaction to a saturation bombardment, as Nexerelin applies it in person:
     * -0.02 x size; the victim twice that and at best Vengeful; every faction that cares about atrocities at best Hostile,
     * Neutral under limited hatred, and with no cap when the victim is Monstrous.
     */
    public static float[] atrocityImpact(int size, boolean victim, boolean limited, boolean monstrous) {
        float delta = -0.02f * size;
        if (victim) return new float[]{2f * delta, AT_VENGEFUL};
        if (monstrous) return new float[]{delta, AT_NONE};
        if (limited) return new float[]{delta, AT_NEUTRAL};
        return new float[]{delta, AT_HOSTILE};
    }

    /** The permanent cap on relations a bombardment leaves (MiscEventsManager): -setting, twice for the victim, none under limited hatred. */
    public static float permaHate(float setting, boolean victim, boolean limited) {
        if (limited || setting <= 0f) return 0f;
        return -setting * (victim ? 2f : 1f);
    }

    /** Limited hatred (Nex_MarketCMD.checkForSatBombLimitedHatred): hidden, a Monstrous victim, size 3 or less, a colony expedition's market, or a victim that bombarded before. */
    public static boolean limitedHatred(boolean hidden, boolean monstrous, int size, boolean expeditionColony, boolean victimBombedBefore) {
        return hidden || monstrous || size <= 3 || expeditionColony || victimBombedBefore;
    }

    // ---------------- who pays ----------------

    /** payer() without a military rung allowance. */
    public static String payer(String kind, boolean rich, float cost, float credits, float reserve, float budget, float pool) {
        return payer(kind, rich, cost, credits, reserve, budget, pool, 0f);
    }

    /**
     * Who pays an operation (cost with the fee): never below the credit reserve (null: it waits).
     * - emergency and relief: "reserve", anything above the reserve, over the month's budget (as 0.2.45);
     * - defense and guard (proactive defense, standing guard; 03 F10): the military rung's allowance ("rung"), else the
     *   surplus while rich ("pool"), else the month's budget;
     * - invasion: the surplus, or the budget when it holds twice the cost;
     * - satbomb: the surplus only;
     * - the rest (strike, raid, retake, holdraid, blockade): the surplus while rich, else the budget, else the surplus.
     */
    public static String payer(String kind, boolean rich, float cost, float credits, float reserve, float budget, float pool, float rung) {
        if (credits - cost < reserve) return null;
        if (EMERGENCY.equals(kind) || RELIEF.equals(kind)) return "reserve";
        if (DEFENSE.equals(kind) || GUARD.equals(kind)) {
            if (rung >= cost) return "rung";
            if (rich) return pool >= cost ? "pool" : null;
            return budget >= cost ? "budget" : null;
        }
        if (INVASION.equals(kind)) {
            if (pool >= cost) return "pool";
            return budget >= 2f * cost ? "budget" : null;
        }
        if (SATBOMB.equals(kind)) return pool >= cost ? "pool" : null;
        if (rich) return pool >= cost ? "pool" : null;
        if (budget >= cost) return "budget";
        return pool >= cost ? "pool" : null;
    }

    // ---------------- targets ----------------

    /**
     * Invasion score (06 section 4.7): value = size squared, x 1.5 with a spaceport or military, x 3 when it was yours, x 0.5
     * for a capital unless capitals go first; divided by cost x risk (1 + 0.5 per military market of theirs in the same
     * system, where counter-invasions come from) x distance (1 + light years / 10).
     */
    public static float invasionScore(int size, boolean portOrMilitary, boolean originallyYours, boolean capital, boolean capitalFirst,
                                      float cost, int sameSystemMilitary, float ly) {
        float value = size * size * (portOrMilitary ? 1.5f : 1f) * (originallyYours ? 3f : 1f) * (capital && !capitalFirst ? 0.5f : 1f);
        float risk = 1f + 0.5f * Math.max(0, sameSystemMilitary);
        float dist = 1f + Math.max(0f, ly) / 10f;
        return 1e6f * value / (Math.max(1f, cost) * risk * dist);
    }

    /** An Enemy faction's market is invaded only when it costs at most OPPORTUNISTIC_SHARE of the surplus; a Destroy faction's always. */
    public static boolean worthInvading(int stance, float cost, float pool) {
        if (stance >= CAStance.DESTROY) return true;
        return stance == CAStance.ENEMY && cost <= OPPORTUNISTIC_SHARE * Math.max(0f, pool);
    }

    // ---------------- pacing ----------------

    /** Invasions under way at once: 1, or 2 while rich. */
    public static int maxInvasions(boolean rich) { return rich ? 2 : 1; }

    /**
     * Why a conquered colony blocks the next invasion, or null: Nexerelin's autonomy not taken back yet, rebels at half the
     * fighting strength or more, or stability under 3 (consolidate first, 06 section 4.8).
     */
    public static String unstable(String name, float rebelShare, float stability, boolean autonomous) {
        if (autonomous) return name + " is still autonomous";
        if (rebelShare >= REBELS_BLOCK) return "rebels hold " + Math.round(rebelShare * 100f) + " percent at " + name;
        if (stability < UNSTABLE_UNDER) return name + " is at stability " + Math.round(stability);
        return null;
    }

    /**
     * Room for a conquered colony. penaltyOk: every colony you govern stays at the stability floor after the captures you
     * would govern yourself (this one and those under way, less one an idle administrator governs). Each of those costs every
     * colony you govern 2 stability, under the colony limit too (the management bonus shrinks), so penaltyOk is needed at any
     * count; 0.3.0.0 (fix): before, a capture under the limit had no check. idleAdmin: an idle administrator will govern a
     * capture past the limit (Nexerelin makes it autonomous, CAConquest.autonomy takes it back with them). Past the limit
     * after that, only rich empires go ahead (the recruiter covers it), so the capture can be taken back from Nexerelin's
     * autonomy at once.
     */
    public static boolean adminRoom(int overLimitAfter, boolean idleAdmin, boolean rich, boolean penaltyOk) {
        if (!penaltyOk) return false;
        return overLimitAfter - (idleAdmin ? 1 : 0) <= 0 || rich;
    }

    /** 0.3.0.0 (fix): why adminRoom says no, or null when there's room. */
    public static String adminWait(int overLimitAfter, boolean idleAdmin, boolean rich, boolean penaltyOk) {
        if (adminRoom(overLimitAfter, idleAdmin, rich, penaltyOk)) return null;
        if (!penaltyOk) return "one more colony governed by you would put a colony you govern under the stability floor";
        return "waiting for an administrator (colony limit)";
    }

    /**
     * 0.3.0.0 (fix): adminWait for `captures` landing (this one and those under way, overLimitAfter once all land), and at most
     * one of them under the colony limit. A capture under the limit lands governed by you; a second one landing after it costs
     * it 2 stability, and penaltyOk can't count a colony that isn't yours yet. Past the limit a capture lands autonomous and
     * is taken back only once the colonies you govern, that one included, can take it (CAConquest.autonomy).
     */
    public static String adminWait(int captures, int overLimitAfter, boolean idleAdmin, boolean rich, boolean penaltyOk) {
        String w = adminWait(overLimitAfter, idleAdmin, rich, penaltyOk);
        if (w == null && landGoverned(captures, overLimitAfter) > 1) return "one invasion at a time under the colony limit";
        return w;
    }

    /** 0.3.0.0 (fix): of `captures` landing with overLimitAfter past the colony limit once all land, how many land under it (governed by you). */
    public static int landGoverned(int captures, int overLimitAfter) {
        return Math.max(0, Math.min(captures, captures - Math.max(0, overLimitAfter)));
    }

    /**
     * 0.3.0.0 (fix): pure form of CAConquest.governedCanTake for the offline checks: whether every colony you govern (their
     * stabilities) stays at the floor (never under 1) after `more` colonies governed by you, each costing perColony.
     */
    public static boolean governedCanTake(float[] governed, int more, float perColony, float floor) {
        float cost = Math.max(0, more) * perColony;
        if (cost <= 0f || governed == null) return true;
        for (float st : governed) if (st - cost < Math.max(floor, 1f)) return false;
        return true;
    }

    /** 0.3.0.0 (fix): a Destroy capital bombarded this many days ago is invaded first and not bombarded again (BOMBED_DAYS). */
    public static boolean bombedRecently(float daysSinceLanded) { return daysSinceLanded >= 0f && daysSinceLanded <= BOMBED_DAYS; }

    /** The rebels' share of the fighting strength at a colony (0 with no rebellion). */
    public static float rebelShare(float government, float rebels) {
        float total = Math.max(0f, government) + Math.max(0f, rebels);
        return total <= 0f ? 0f : Math.max(0f, rebels) / total;
    }

    /**
     * Why a new invasion waits, or null when it may go: one under way already (two while rich), a conquered colony that
     * isn't stable, no room for the capture (adminWait: a colony you govern would go under the stability floor, or no
     * administrator past the colony limit), infamy past the line with what's under way and this capture,
     * too few spare fleet points for the full fleet (never a smaller, doomed one), or no one to pay.
     */
    public static String invasionWait(int underWay, boolean rich, String unstable, boolean adminRoom, float badboy, float dominance,
                                      float pendingInfamy, float addInfamy, boolean coalitionOK, float spareFP, float fp, String payer) {
        return invasionWait(underWay, rich, unstable, adminRoom ? null : "waiting for an administrator (colony limit)", badboy, dominance,
                pendingInfamy, addInfamy, coalitionOK, spareFP, fp, payer);
    }

    /** 0.3.0.0 (fix): the same with the colony-limit reason (adminWait) in place of a yes or no; a null roomWait means there's room. */
    public static String invasionWait(int underWay, boolean rich, String unstable, String roomWait, float badboy, float dominance,
                                      float pendingInfamy, float addInfamy, boolean coalitionOK, float spareFP, float fp, String payer) {
        if (underWay >= maxInvasions(rich)) return "an invasion is under way";
        if (unstable != null) return unstable;
        if (roomWait != null) return roomWait;
        if (!infamyOk(badboy + pendingInfamy, dominance, addInfamy, coalitionOK))
            return "infamy would reach " + Math.round(infamy(badboy + pendingInfamy + addInfamy, dominance)) + " of " + Math.round(INFAMY_LINE);
        if (spareFP < fp) return "waiting for fleet points (" + Math.round(Math.max(0f, spareFP)) + " of " + Math.round(fp) + ")";
        if (payer == null) return "waiting for credits";
        return null;
    }

    /** Why a saturation bombardment waits, or null: not authorized, one under way, under 120 days since the last, infamy, fleet points, credits. */
    public static String bombWait(boolean authorized, int underWay, float daysSinceLast, float badboy, float dominance, float pendingInfamy,
                                  float addInfamy, boolean coalitionOK, float spareFP, float fp, String payer) {
        if (!authorized) return "bombardment isn't authorized on the War page";
        if (underWay >= BOMBS_AT_ONCE) return "a bombardment is under way";
        if (daysSinceLast < BOMB_GAP_DAYS) return "the last bombardment was " + Math.round(daysSinceLast) + " days ago (" + Math.round(BOMB_GAP_DAYS) + " between)";
        if (!infamyOk(badboy + pendingInfamy, dominance, addInfamy, coalitionOK))
            return "infamy would reach " + Math.round(infamy(badboy + pendingInfamy + addInfamy, dominance)) + " of " + Math.round(INFAMY_LINE);
        if (spareFP < fp) return "waiting for fleet points (" + Math.round(Math.max(0f, spareFP)) + " of " + Math.round(fp) + ")";
        if (payer == null) return "waiting for credits";
        return null;
    }

    /**
     * Why war can't be declared on a faction, or null: only Enemy and Destroy, not one already hostile, not with a
     * commission, Low profile, an alliance or a ceasefire, not at War level Off or Defend, and one declaration every 30 days.
     */
    public static String declareWait(int stance, boolean hostile, boolean commissioned, boolean lowProfile, boolean allied, boolean ceasefire,
                                     int level, float daysSinceLastDeclaration) {
        if (!CAStance.wantsWar(stance)) return "only for an Enemy or Destroy stance";
        if (hostile) return "already hostile";
        if (commissioned) return "not while you hold a commission";
        if (lowProfile) return "not in Low profile";
        if (allied) return "allied with you";
        if (ceasefire) return "a ceasefire is in force";
        if (clamp(level) < RETALIATE) return "War level is " + levelName(level);
        if (daysSinceLastDeclaration < DECLARE_GAP_DAYS)
            return "one declaration every " + Math.round(DECLARE_GAP_DAYS) + " days (" + (int) Math.ceil(DECLARE_GAP_DAYS - daysSinceLastDeclaration) + " left)";
        return null;
    }

    /** Keep hostile (Enemy and Destroy with war confirmed): re-declared at most once in KEEP_HOSTILE_DAYS. */
    public static boolean keepHostileDue(float daysSinceLast) { return daysSinceLast >= KEEP_HOSTILE_DAYS; }

    // ---------------- stand down ----------------

    /** Nexerelin's refund of its own price on a cancelled operation by stage (OffensiveFleetIntel.getCreditRefundMult). */
    public static float refundMult(int stage) {
        if (stage <= 1) return 1f;
        if (stage == 2) return 0.67f;
        if (stage == 3) return 0.33f;
        return 0f;
    }

    /** Stand down cancels operations still organizing or assembling (stage 0 or 1, a full refund of Nexerelin's price). */
    public static boolean cancelOnStandDown(int stage) { return stage <= 1; }

    /** Recall all also cancels operations travelling or in action (stage 2 or 3); landed troops can't be recalled. */
    public static boolean recallable(int stage) { return stage == 2 || stage == 3; }

    /**
     * 0.3.0.0 (fix, 06 section 4.11): whether Stand down (recall false) or Recall all (recall true) ends this operation. An
     * invasion whose ground battle has started stays at stage 3 until every route is done; its troops are on the ground and
     * its GroundBattleIntel keeps running, so it is neither cancelled nor refunded.
     */
    public static boolean stopsOnStandDown(int stage, boolean recall, boolean troopsLanded) {
        if (troopsLanded) return false;
        return cancelOnStandDown(stage) || (recall && recallable(stage));
    }

    /** The War level after Stand down: Defend, or Off when it was Off. */
    public static int standDownLevel(int level) { return Math.min(clamp(level), DEFEND); }
}
