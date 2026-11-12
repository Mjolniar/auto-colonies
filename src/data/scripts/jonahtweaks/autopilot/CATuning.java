package data.scripts.jonahtweaks.autopilot;

/**
 * Auto Colonies' own numbers in one place (0.3.0.0, WS-A3, research 07 section 5). CAConst holds other mods' and the game's
 * constants; this holds CM's tuning: score bonuses, fees and prices, day counts, caps and stability margins. One comment
 * each: what it is and where it came from. Every value is the one 0.2.45.1 used, so moving them here changed nothing.
 *
 * The classes that had their own name for a value keep it as an alias (CAItems.HIRE_FEE = CATuning.HIRE_FEE), so their code
 * and the offline checks read the same; the literal numbers that were inline now name the constant here. All of them are
 * compile-time constants, so nothing here is loaded or read at run time. JTTuningCheck pins every value and alias.
 */
public final class CATuning {
    private CATuning() { }

    // ---------------- 0.3.0 workstreams (A0 stub; their owners use them) ----------------

    /** B2: payback horizon in months, normal and when money piles up. */
    public static final float HORIZON_MIN = 36f, HORIZON_MAX = 120f;
    /** B4: how long a removal blocks rebuilding the same thing at the same colony. */
    public static final float TOMBSTONE_DAYS = 365f;
    /** C2: tier entry lines (exit at 60% of entry). */
    public static final float T1_MIN = 1_000_000f, T2_MIN = 3_000_000f, T3_MIN = 10_000_000f, TIER_EXIT = 0.6f;
    /** B2: a plan step carries this share of what the steps it leads to would add; an ordinary plan step's tie-break share of its own worth; days a plan step scores nothing before it stops holding rank and a slot; the shortest horizon a setting can ask for (CAHorizon). */
    public static final float DOWNSTREAM_SHARE = 0.5f, PLAN_TIE = 0.05f, WORTHLESS_DAYS = 30f, HORIZON_FLOOR = 12f;
    /**
     * B3 (CAPlanner): days an earmark holds other spending back at most (research 02 section 5); a pick needing more months of
     * budget than this isn't saved up for (the savings cap); days an upkeep hold lasts at most (02 section 3); builds longer
     * than this don't take the queue of a better build that waits with no known start (DECISIONS 19); days a colony's offer
     * is kept between its looks.
     */
    public static final float PLAN_EARMARK_DAYS = 120f, PLAN_EARMARK_MAX_MONTHS = 6f, PLAN_UPKEEP_HOLD_DAYS = 31f, PLAN_LONG_BUILD_DAYS = 120f,
            PLAN_OFFER_DAYS = 8f;
    /** B5 (CABuildout): how much faster per credit an off-plan industry must pay back to take a slot the plan's industries need. */
    public static final float BUILDOUT_MARGIN = 1.5f;
    /** B5 (CAPlan, DECISIONS 20): a plan that undoes a committed world's paid changes must be worth this many times as much. */
    public static final float WORLD_SWITCH_WORTH = 2f;
    /** B5 (CASiege): hostiles at the colony with at least this share of its defense fleet points are a siege at once; days weaker ones must stay. */
    public static final float SIEGE_NEAR_SHARE = 0.5f, SIEGE_NEAR_DAYS = 1f;

    // ---------------- scoring (CAEngine, CAPlan) ----------------

    /** Plan bonus for the first, second and any later open build step of a colony's plan (CAPlan.bonusFor). B2: enablers and a special world's buildings only (CAEngine.finishNpv); every plan step with legacyPlanner. */
    public static final float PLAN_BONUS_FIRST = 60_000f, PLAN_BONUS_SECOND = 40_000f, PLAN_BONUS_LATER = 20_000f;
    /** Plan bonus for terraforming that adds a condition the plan wants, or removes a hazard it doesn't (CAPlan.bonusForCondition). */
    public static final float PLAN_COND_ADD = 60_000f, PLAN_COND_REMOVE = 50_000f;
    /** What a new off-plan industry gives up when it takes a slot the plan's industries need (CAEngine). B2: legacyPlanner's flat reserve; in 0.3.0 the stand-in for a plan industry that hasn't scored yet (CAPlan.reserve). */
    public static final float SLOT_RESERVE = 30_000f;
    /** Taken off the score of a building that pollutes a habitable world; it's shown, never started (CAEngine.finish, 0.2.36.2). */
    public static final float POLLUTION_PENALTY = 30_000f;
    /** Follow-through: added to an upgrade of what's already built (CAEngine.finish). legacyPlanner only (B2). */
    public static final float FOLLOW_THROUGH = 15_000f;
    /** Months a build's price is spread over: legacyPlanner's engine price spread and CAObjectives' payback. */
    public static final float PAYBACK_MONTHS = 36f;
    /** Score a build gives up per day of construction: a little for the wait (CAEngine.finish). legacyPlanner only (B2). */
    public static final float WAIT_COST_PER_DAY = 10f;
    /** Credits a month per credit of base price for each unit a building needs of a good your colonies already lack (CAEngine.demand, CAItems). */
    public static final float SHORT_INPUT_PRICE = 30f;
    /** Supply that covers your colonies' shortage, or an illegal good covering the colony's own, counts this much more (CAEngine.supply). */
    public static final float SHORTAGE_FACTOR = 1.5f;
    /** The measured unit value (credits a month per unit per credit of base price): without exports to measure, and its clamp (CAEngine.calibrate). */
    public static final float UNIT_VALUE_DEFAULT = 100f, UNIT_VALUE_MIN = 50f, UNIT_VALUE_MAX = 3_000f;
    /** Payback a Terraforming & Station Construction station must reach, in months (CATascStations, 0.2.40.1). */
    public static final float TASC_PAYBACK_MONTHS = 60f;

    // ---------------- endgame economy (CAEndgame, 0.2.36.0) ----------------

    /** Net income may fall to this share of what it was when the endgame started. */
    public static final float ENDGAME_KEEP = 1f / 3f;
    /** What a credit counts in a build's worth during the endgame. */
    public static final float ENDGAME_MONEY = 0.05f;
    /** A production chain the empire doesn't have yet: worth this per tier, plus its units. */
    public static final float ENDGAME_NEW_CHAIN = 40_000f;
    /** A good made at only one colony: a second source elsewhere is worth this. */
    public static final float ENDGAME_SECOND_SOURCE = 10_000f;
    /** What a unit of a chain's output is worth. */
    public static final float ENDGAME_PER_UNIT = 2_000f;
    /** A replacement must beat the industry it takes down by this much. */
    public static final float ENDGAME_REPLACE_MARGIN = 10_000f;
    /** Days between two replacements at one colony. */
    public static final float ENDGAME_REPLACE_COLONY_DAYS = 30f;
    /** Days what the endgame built or took down is remembered, so it's never undone or redone sooner. */
    public static final float ENDGAME_MEMORY_DAYS = 365f;

    // ---------------- fees and prices ----------------

    /** Construction convenience fee: this share of the price, at least FEE_FLOOR, times the fee level (CABudget.constructionFee). */
    public static final float FEE_SHARE = 0.05f, FEE_FLOOR = 5_000f;
    /** Fee to move an item, core or administrator between colonies, before the fee level (CAItems, CAIndEvo ambassadors). */
    public static final float MOVE_FEE = 10_000f;
    /** Fee to hire an administrator or an ambassador remotely, before the fee level (CAItems, CAIndEvo). */
    public static final float HIRE_FEE = 25_000f;
    /** What a recruiter charges to find an administrator none is on offer for; a price, so the fee level never waives it (CAItems, 0.2.41.2). */
    public static final float RECRUITER_PRICE = 250_000f;
    /** Fee to recruit a Nexerelin agent remotely, before the fee level (CASpies, 0.2.37.0). */
    public static final int AGENT_HIRE_FEE = 12_000;
    /** Base fee for a crew to a stable location (CAObjectives, 0.2.40.0). */
    public static final float OBJECTIVE_BASE_FEE = 5_000f;
    /** Base fee for a crew to found a mining or siphon station (CATascStations, 0.2.40.1). */
    public static final float TASC_BASE_FEE = 25_000f;
    /** Fee for a Terraforming Made Easy project started remotely, before the fee level (CATerraform). */
    public static final float TERRAFORM_FEE = 25_000f;
    /** Shipping to a colony's Autopilot Storage: fee floor and fee per light year (before the fee level), and days on top of a day per light year (CAStores, 0.2.37.0). */
    public static final float SHIP_BASE = 2_000f, SHIP_PER_LY = 300f, SHIP_DAYS = 2f;

    // ---------------- budget and surplus ----------------

    /** Months of unspent budget the savings can hold (CABudget.rollMonth, 0.2.39.0). */
    public static final float CARRY_MONTHS = 6f;
    /** The automatic upkeep cap: this share of last month's net income, at least UPKEEP_FLOOR (CABudget, 0.2.24.0; CASettings.upkeepShare's default). */
    public static final float UPKEEP_SHARE = 0.10f, UPKEEP_FLOOR = 20_000f;
    /** B5 (CABudget, research 03 F4): months the colony net median looks back over; colony net as a scale never goes under NET_FLOOR. */
    public static final int NET_MONTHS = 3;
    public static final float NET_FLOOR = 50_000f;
    /** At the focus world, while there's a surplus, a price counts this much (CASurplus). legacyPlanner (and CATerraform/CATascStations until they move to CAHorizon). */
    public static final float FOCUS_PRICE = 0.25f;
    /** The focus world's upkeep cap while there's a surplus: this share of net income, if more than the setting (CASurplus). */
    public static final float FOCUS_UPKEEP_SHARE = 0.10f;
    /** Spending freely: credits this far over the surplus line, and every colony spends from it (CASurplus.RICH, 0.2.32.0). */
    public static final float RICH = 10_000_000f;
    /** B5 (CASurplus.RICH_OFF): spending freely stops only when the surplus falls under this (0.7 x RICH). */
    public static final float RICH_OFF = 0.7f * RICH;
    /** While spending freely a credit counts this much in a build's worth (CASurplus). */
    public static final float RICH_MONEY = 0.25f;
    /** While spending freely the month's new upkeep may reach the surplus divided by this many months (CASurplus, 0.2.34.1). */
    public static final float RICH_UPKEEP_MONTHS = 60f;
    /** While spending freely a build's price counts this much (CASurplus, 0.2.33.0). legacyPlanner (and CATerraform/CATascStations until they move to CAHorizon). */
    public static final float RICH_PRICE = 0.05f;
    /** Hazard pay defaults: only where it speeds growth up by this share, and all of it within this share of empire net income (CAPolicies, 0.2.20.0). */
    public static final float HAZARD_COLONY_SHARE = 0.25f, HAZARD_EMPIRE_SHARE = 0.30f;

    // ---------------- day counts ----------------

    /** Hazard pay and the free port: fewest days between two changes at a colony (CAPolicies). */
    public static final float HAZARD_PAY_MIN_DAYS = 30f, FREE_PORT_MIN_DAYS = 90f;
    /** B5 (CAPolicies, research 01 section 12): days a closed free port's reasons to open must hold in a row before it opens. */
    public static final float FREE_PORT_REOPEN_DAYS = 30f;
    /** B5 (CAContracts): days a signed contract's need is counted at most (by two month-ends ToT's own figures carry it). */
    public static final float CONTRACT_LEDGER_DAYS = 62f;
    /** CAPrune (0.2.44.1): days an industry is worth less than nothing before it comes down, days between two at a colony, days between looks. */
    public static final float PRUNE_BELOW_DAYS = 30f, PRUNE_GAP_DAYS = 30f, PRUNE_EVERY_DAYS = 5f;
    /** CAOverLimit (0.2.42.1): days between two industries shed at a colony, and between two alerts while a colony sits at 0 stability. */
    public static final float OVER_LIMIT_GAP_DAYS = 1f, OVER_LIMIT_ALERT_DAYS = 30f;
    /** CAOverLimit: days between two stability-costing buildings taken down at 0, and how long one stays off the capital's plan. */
    public static final float OVER_LIMIT_SHED_DAYS = 10f, OVER_LIMIT_SHED_HOLD_DAYS = 365f;
    /** CAOverLimit: days at 0 stability before a stability-costing building is taken down (decivilization takes months at 0). */
    public static final float OVER_LIMIT_ZERO_WAIT_DAYS = 20f;
    /** CAOverLimit: growth this close to the next size waits for the room it brings instead of shedding. */
    public static final float OVER_LIMIT_GROWS_SOON = 0.8f;
    /** Days between two industries the capital takes down to make room for commerce (CACapital, 0.2.42.0). */
    public static final float CAPITAL_ROOM_DAYS = 90f;
    /** A plan step with no way to it stops counting after this many days; a special world whose own steps can't be reached is dropped this long (CAPlan, 0.2.38.0). */
    public static final float STEP_DEAD_DAYS = 60f, WORLD_DEAD_DAYS = 365f;
    /** B5 (CAPlan): days after the committed world was last the colony's plan that the commitment lasts (a dropped world comes back after WORLD_DEAD_DAYS). */
    public static final float WORLD_COMMIT_DAYS = WORLD_DEAD_DAYS + 30f;
    /** Days the colony limit can go short of administrators with none on offer before a recruiter finds one (CAItems, 0.2.41.2). */
    public static final float ADMIN_RECRUIT_DAYS = 90f;
    /** Days a hired administrator or ambassador travels before taking a post (CAItems, CAIndEvo; fair play). */
    public static final float TRAVEL_DAYS = 30f;
    /** Days a Nexerelin agent takes to recruit (CASpies, 0.2.37.0). */
    public static final float AGENT_RECRUIT_DAYS = 60f;
    /** Days a flipping status waits before its next line in the panel log, and before the same ping shows again (CALog). */
    public static final float STATUS_DAYS = 10f, PING_DAYS = 7f;
    /** Days between two "nothing worth starting" traces of a colony in starsector.log (CAAuto). */
    public static final float TRACE_DAYS = 30f;
    /** Days a building under construction stays in the market model's pending supply at most (CAMarket.prune, 0.2.44.0). */
    public static final float MARKET_CLAIM_DAYS = 400f;

    // ---------------- caps ----------------

    /**
     * Once-only log keys (CAData.notedAt) kept before CALog.prune drops the oldest expiring ones (guards never go). 0.3.0.0 (A5):
     * replaced 0.2.45.1's 2,000-key set that was wiped whole (CAData.noted no longer wipes); alias CALog.CAP.
     */
    public static final int NOTED_CAP = 4_000;
    /** Days before a once-logged wait or recommendation can show again while it still holds (CALog.holdDays; alias CALog.WAIT_DAYS). */
    public static final float NOTED_WAIT_DAYS = 90f;
    /** Days an event guard's once-key (an inspection looked at) holds before it's tidied (CALog.holdDays; alias CALog.YEAR_DAYS). */
    public static final float NOTED_EVENT_DAYS = 365f;
    /** Lines the panel log keeps (CALog.add). */
    public static final int LOG_LINES = 100;
    /** Picks a colony's pass keeps: upgrades, new builds, and extra upgrades while spending freely (CAAuto). */
    public static final int PASS_UPGRADES = 2, PASS_NEW = 3, PASS_RICH_UPGRADES = 2;
    /** Upgrades a colony may start in one pass; while spending freely (CAAuto, 0.2.45.1's maxUps; never two on one building). */
    public static final int PASS_MAX_UPGRADES = 1, PASS_RICH_MAX_UPGRADES = 3;

    // ---------------- stability ----------------

    /** minStability's default (colony_autopilot.json, "Stability floor the manager never builds below"); 0 on a save also means this. */
    public static final float MIN_STABILITY = 5f;
    /** The stability floor never goes under this, whatever minStability says (CAStability, 0.3.0.0; CAOddisz/CACapital/CALowProfile's 3). */
    public static final float STABILITY_FLOOR = 3f;
    /** The free port is wanted only while stability, after its cost, is at least minStability plus this (CAPolicies). */
    public static final float FREE_PORT_STABILITY_MARGIN = 1f;
    /** At or under this stability a colony over its industry limit sheds an industry at once, whatever it earns (CAOverLimit). */
    public static final float OVER_LIMIT_LOW = 2f;

    // ---------------- war and defense (Nexerelin) ----------------

    /** Share of Nexerelin's fleet request capacity kept for emergencies: normally, and while spending freely (CAWar, 0.2.35.0). */
    public static final float WAR_RESERVE = 0.4f, WAR_RICH_RESERVE = 0.1f;
    /** While spending freely, war fleets are this much bigger and this many start a day (CAWar). */
    public static final float WAR_RICH_SIZE = 1.5f;
    public static final int WAR_RICH_PER_DAY = 3;
    /** Smallest fleet CAWar requests, in fleet points. */
    public static final float WAR_MIN_FP = 100f;
    /** Base strikes reach pirate and Pather bases within this many light years of a colony (any distance while spending freely). */
    public static final float WAR_BASE_LY = 15f;
    /** Markets that attacked you in the last this-many days are struck back; one relief fleet per colony every this-many days (CAWar). */
    public static final float WAR_ATTACK_DAYS = 120f, WAR_RELIEF_DAYS = 60f;
    /** CADefense's emergency fleets: smallest, in fleet points, and days between two at a colony. */
    public static final float DEFENSE_MIN_FP = 100f, DEFENSE_COOLDOWN_DAYS = 30f;
}
