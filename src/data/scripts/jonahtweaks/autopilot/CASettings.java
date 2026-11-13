package data.scripts.jonahtweaks.autopilot;

import java.util.ArrayList;
import java.util.List;

/** Global Colony Autopilot settings and this month's spending. Defaults are the doc's recommendations. */
public class CASettings {
    public boolean setupDone = false;

    // budget
    public boolean budgetPercentMode = true;
    // Automatic budget (recommended): budgetPercent of net income plus savingsShare of the credits above the reserve at the
    // start of the month (monthCredits), so a young colony that loses money still gets built and spending tapers as savings
    // fall. Overrides budgetPercentMode; off in older saves until setup or "Use recommended settings" turns it on.
    public boolean budgetAuto = false;
    public float savingsShare = 0.15f;
    public float monthCredits = -1f;
    public float budgetPercent = 0.5f;
    public float budgetFixed = 1000000f;
    public float perProjectCap = 2000000f;
    public float reserve = 500000f;
    // most of the monthly budget terraforming may use (colony_autopilot.json's terraformShare); fixNulls keeps it within 0-1
    public float terraformShare = 0.4f;
    // 0.3.0.0 (A3, 07 L3): the stability floor the manager never builds below (colony_autopilot.json's minStability). It lived in
    // CAData before 0.3.0; a save from before 0.3.0 loads 0 here and CAData.get() brings that one over, once
    public float minStability = CATuning.MIN_STABILITY;
    public float newUpkeepCap = 50000f;   // the Fixed cap; the Automatic one follows net income (CABudget.upkeepCap)
    // new upkeep cap: Automatic (10% of last month's net income, at least 20,000) unless set to Fixed. upkeepCapChecked
    // is false loading a save from before 0.2.24.0, whose fixed cap (set once at setup) becomes Automatic once
    public boolean upkeepCapAuto = true, upkeepCapChecked = true;
    public boolean pauseOnLoss = true;
    // surplus investment (CASurplus): credits over the line go into one focus world; index into SURPLUS_LINES (0 off).
    // surplusChecked is false loading a save from before 0.2.21.0, which gets the recommended line once.
    public int surplusLine = 2;
    // past CASurplus.RICH over the line every Approve/Auto colony spends from the surplus (false, also older saves); true: the focus world only
    public boolean surplusFocusOnly = false;
    // 0.3.0.0 (A3): richIncomeRef (the old pause's reference, unused since 0.2.34.1) is gone; CAXStream drops it from older saves
    public boolean noWonders = false;  // false (also older saves): spending freely builds the Daedalus Array and Bifrost gates (CAWonders)
    /** Announcement voice (0.2.41.0): 0 Machine (default, also older saves), 1 Plain. */
    public int voice = 0;
    public static final String[] VOICE_NAMES = {"Machine", "Plain"};
    public int screenLog = 0;          // log entries on screen: 0 every entry (default, also older saves), 1 important only, 2 off
    public boolean surplusChecked = true;
    public String focusId;        // the focus world you picked (null: Autopilot picks)
    public String focusAuto;      // the one Autopilot picked last, kept unless another beats it clearly
    public static final float[] SURPLUS_LINES = {0f, 1000000f, 3000000f, 5000000f, 10000000f};
    public static final String[] SURPLUS_NAMES = {"Off", "Over 1,000,000", "Over 3,000,000", "Over 5,000,000", "Over 10,000,000"};
    // surplus terraforming (CATerraformSurplus, with Terraforming Made Easy): what's left of the surplus goes into terraforming
    // at every Auto colony. 0 Max output (also what older saves load as), 1 Pays back in 10 years, 2 Off
    public int surplusTerraform = 0;
    public static final String[] SURPLUS_TF_NAMES = {"Max output", "Pays back in 10 years", "Off"};
    // surplus research (CASurplusResearch, with Vaults of Knowledge): more Research Facilities from the surplus, up to the 8 VoK
    // counts. False (on) is also what older saves load as
    public boolean noSurplusResearch = false;
    // endgame economy (CAEndgame): 0 from 1M a month (also what older saves load as), 1 from 2M, 2 from 500k, 3 off; names in
    // CAEndgame.NAMES. endgameNoReplace false (default): it may replace industries. The rest is its state: whether it's on,
    // the net income when it started (it keeps a third of it), and the income its changes gave up this month
    public int endgameMode = 0;
    public boolean endgameNoReplace = false;
    public boolean endgameOn = false;
    public float endgameRefNet = 0f, endgameLoss = 0f;

    // services
    public int feeLevel = 2;              // 0 off, 1 low, 2 standard, 3 high
    public String stationLine = "score";  // score, low, mid, high
    public boolean useAICores = false;
    public int alphaMode = 0;             // 0 only where removable (default), 1 anywhere, 2 never
    public boolean researchCoresByValue = false;   // false (default, and older saves): Alpha cores go into research buildings first
    // special project supply (CAProjects, Vaults of Knowledge): 0 Auto (also older saves), 1 Warn only, 2 Off
    public int projectSupplyMode = 0;
    public static final String[] PROJECT_SUPPLY_NAMES = {"Auto", "Warn only", "Off"};
    // 0.3.0.0 (A3): storageCheat (moved to Easy Sector in 0.2.37.0) is gone; CAXStream drops it from older saves
    public int coreRisk = 1;              // 0 more where defenses beat the next inspection, 1 never inspected (default for new saves), 2 no limit, 3 small colonies only
    public int inspectionMode = 0;        // Hegemony inspections of Auto colonies: 0 advise (so older saves load as advise), 1 resist where defended, 2 off
    public boolean allowRemovals = false;
    public int storyPointRule = 0;        // 0 player only, 1 one per colony, 2 monthly cap
    public int storyPointMonthlyCap = 2;
    public boolean adminHiring = false;
    public boolean adminOptimal = true;
    // 0.3.0.0 (A3, 07 L2): adminDelay is gone: nothing read it, and hired administrators and ambassadors always travel
    // CATuning.TRAVEL_DAYS (fair play). CAXStream drops it from older saves
    // ambassadors: focus 0 worst relations, 1 strongest factions, 2 nearest neighbors, 3 priority list only
    public int ambassadorFocus = 0;
    public int ambassadorStopAt = 2;   // index into STOP_NAMES/STOP_VALUES
    public java.util.Map<String, Integer> factionPriority;   // faction id -> 0 ignore, 1 normal (default), 2 high
    public static final String[] FOCUS_NAMES = {"Worst relations first", "Strongest factions", "Nearest neighbors", "Priority list only"};
    public static final String[] STOP_NAMES = {"Welcoming", "Friendly", "Cooperative", "Never stop"};
    public static final float[] STOP_VALUES = {0.25f, 0.5f, 0.75f, 2f};
    public static final String[] PRIORITY_NAMES = {"Ignore", "Normal", "High"};

    /** 0.3.0.0 (WS-E): the ambassador priority from the stance table (CAStance); factionPriority stays so old saves load. */
    public int priorityOf(String factionId) {
        return CAStance.ambassadorPriority(stanceOf(factionId));
    }

    public int hazardPayMode = 0, freePortMode = 0;   // 0 auto, 1 don't touch
    public float hazardPayColonyShare = CATuning.HAZARD_COLONY_SHARE, hazardPayEmpireShare = CATuning.HAZARD_EMPIRE_SHARE;
    public boolean hazardLimitChecked = true;   // false in saves from before 0.2.20.0, whose old 10% default CAPolicies raises once
    public static final String[] POLICY_NAMES = {"Auto", "Don't touch"};
    // Industrial Evolution automation (stored inverted so older saves default to on)
    public boolean disableAmbassadors = false, disableVPC = false, disableForgeVPC = false, disableEdicts = false, disableShipping = false, disableSpecialties = false, disableStabilizers = false, disableDefenseFleets = false;
    // Random Assortment of Things settlement: 0 Advisor (so older saves load as Advisor), 1 Approve, 2 Auto, 3 Off
    public int settlementMode = 0;
    public float settlementMinIncome = 20000f;
    public static final String[] SETTLE_NAMES = {"Advisor", "Approve", "Auto", "Off"};
    // mod buildings scored from probe-colony measurements: 0 build like any other (default), 1 recommend only, 2 ignore
    public int measuredMode = 0;
    public static final String[] MEASURED_NAMES = {"Build when best", "Recommend only", "Ignore"};
    // Station Augments: 0 follow each colony's mode (Auto applies, Approve logs a recommendation), 1 recommend only, 2 off
    public int augmentMode = 0;
    public static final String[] AUGMENT_NAMES = {"Follow colony mode", "Recommend only", "Off"};
    // Ashes of the Domain trade contracts: 0 Advisor (default), 1 Auto, 2 Off
    public int contractMode = 0;
    public static final String[] CONTRACT_NAMES = {"Advisor", "Auto", "Off"};
    // Ashes of the Domain Coronal Hypershunt range and restoration: same choices as station augments
    public int hypershuntMode = 0;
    // Ashes of the Domain research: 0 Advisor (default), 1 Auto, 2 Off (names as CONTRACT_NAMES)
    public int researchMode = 0;
    // Nexerelin agents (CASpies): 0 off (default), 1 defense, 2 defense and offense; hiring on unless spyNoHiring; agents to keep, 0 = auto
    public int spyMode = 0;
    // 0.2.42.2: false in saves from before it (the field is missing, so it loads false): Agents go to Defense once, since with
    // them off nothing stops a Pather cell. New saves start true and get Defense from the recommended settings
    public boolean spyChecked = true;
    public boolean spyNoHiring = false;
    public int spyKeep = 0;
    public static final String[] SPY_NAMES = {"Off", "Defense", "Defense and offense"};
    // whom the offense agent sabotages (0.2.37.0): null whoever attacks you, else Nexerelin's pick; "random" a random enemy; or a faction id
    public String spyTarget = null;
    // research spends colony items (CAResearch, Vaults of Knowledge), only while credits are over the surplus line: 0 off
    // (default, also older saves), 1 from storage (Autopilot Storage and colonies' Storage tabs), 2 also out of buildings
    public int researchItemMode = 0;
    public static final String[] RESEARCH_ITEM_NAMES = {"Off", "From storage", "From storage and buildings"};
    // Nexerelin emergency autonomy (CAAutonomy): 0 off (default, so older saves load as off), 1 recommend. Autonomy is your
    // call: 2 (the old Auto) now only recommends too.
    public int autonomyMode = 0;
    public static final String[] AUTONOMY_NAMES = {"Off", "Recommend", "Auto"};
    // war fleets (CAWar, Nexerelin): 0 protect and strike back (default, so older saves get it), 1 protect only, 2 off.
    // The names live here so the settings screen loads without Nexerelin.
    public int warMode = 0;
    public static final String[] WAR_NAMES = {"Protect and strike back", "Protect", "Off"};
    // the Colony Autopilot button on your colony's screen (CAScreenButton); on unless this is set
    public boolean noScreenButton = false;
    // 0.3.0.0 (A3): haFleetCap and nexAttackGap (the Derelict Empire "Attacks on your colonies" settings, in Easy Sector
    // since 0.2.37.0) are gone; CAXStream drops them from older saves

    // this month
    public int month = -1;
    public float spent = 0f, fees = 0f, upkeepAdded = 0f, terraSpent = 0f;
    public float budgetCarry = 0f;   // 0.2.39.0: unspent budget saved up from earlier months (CABudget.rollMonth), up to 6 months' worth
    // 0.2.39.0 storage logistics (CALogistics): 0 off (default), 1 on; refit hub market id (null: the production gathering
    // point), sell hub market id (null: none); ships and supplies/fuel/crew/marines go to the refit hub too
    public int logisticsMode = 0;
    /** Stable locations (0.2.40.0, CAObjectives): 0 Auto (also what older saves get), 1 Recommend only, 2 Off. */
    public int objectiveMode = 0;
    /** Terraforming & Station Construction's stations (0.2.40.1, CATascStations): 0 Recommend only (also older saves), 1 Auto, 2 Off. */
    public int stationMode = 0;
    public static final String[] TASC_STATION_NAMES = {"Recommend only", "Auto", "Off"};
    public static final String[] OBJECTIVE_NAMES = {"Auto", "Recommend only", "Off"};
    public String logisticsHub, logisticsSellHub;
    // 0.2.42.0 the capital (CACapital): mode 0 deliveries and commerce (default, also older saves), 1 deliveries only, 2 off;
    // when commerce starts (index into CACapital.START_NAMES, 0 recommended); your pick (null: Autopilot's, kept in
    // capitalAuto); the gathering point Autopilot last set, to tell when you moved it yourself; whether the commerce build-up
    // has started; and two inverted switches so older saves get them on: gather Storage there, take down an industry for room
    public int capitalMode = 0, capitalStart = 0;
    public String capitalId, capitalAuto, capitalGatherSet;
    public boolean capitalStarted = false, capitalNoGather = false, capitalNoRoom = false;
    // 0.2.42.0 a station at every colony (CAStations); inverted so older saves get it
    public boolean noStationPlan = false;
    // 0.2.42.0 an Auto colony over its industry limit takes down its weakest industry (CAOverLimit); inverted so older saves get it
    public boolean noOverLimitFix = false;
    // 0.2.43.0 Low profile (CALowProfile): grow while drawing as little attention as possible; off (Full expansion) for older saves,
    // picked when your first colony is founded
    public boolean lowProfile = false;
    // 0.2.44.0 exports valued by what they add to your share of each sector market (CAMarket); inverted so older saves get it
    public boolean noMarketModel = false;
    // 0.2.44.1 an Auto colony takes down an industry that stopped paying for 30 days (CAPrune); inverted so older saves get it
    public boolean noPrune = false;
    public boolean logisticsShips = true, logisticsSupplies = true;
    public int spMonth = 0;   // story points spent this month (counted improvements before 0.2.20.1)
    public float lastNet = 0f;
    public boolean lastNetKnown = false;

    // ---------------- 0.3.0.0 (A0): every new setting of the 0.3.0 workstreams, added at once (plans/cm-3.0 FIELDS.md) ----------------
    // XStream skips constructors, so an older save loads a missing field as 0, false or null: "on by default" switches are
    // inverted (noX), numbers that must not be 0 get their default in fixNulls, and CAMigrate.to3 translates the rest once.
    /** Settings layout version: 3 for new saves; every save from before 0.3.0 loads 0, so CAMigrate.to3 runs once. */
    public int settingsVersion = CAMigrate.SCHEMA;
    /** B (hidden): the 0.2.45 build engine instead of the 0.3.0 planner, kept for 0.3.0.x in case the planner misbehaves. */
    public boolean legacyPlanner = false;
    /** B: payback horizon in months; the planner slides it up to CATuning.HORIZON_MAX as money piles up. Older saves: fixNulls. */
    public int horizonMonths = 36;
    /** C: "Surplus: what to buy" preset (index into RICH_PRESET_NAMES), 0 Balanced. */
    public int richPreset = 0;
    public static final String[] RICH_PRESET_NAMES = {"Balanced", "Fleet", "Expansion", "Economy", "Save"};
    /** C: per rung (CARich.RUNGS order): 0 Off, 1 On, 2 Recommend only. Shipyard and Expansion start on Recommend only. */
    public int[] rungMode = defaultRungs();
    public static final String[] RUNG_MODE_NAMES = {"Off", "On", "Recommend only"};
    static final int[] RUNG_DEFAULTS = {1, 1, 1, 1, 2, 2, 1, 1};
    public static int[] defaultRungs() { return RUNG_DEFAULTS.clone(); }
    /** C: the shipyard stops ordering at this many unclaimed hulls it ordered in the capital's Storage (DECISIONS 8). */
    public int shipyardStop = 6;
    /** C: convenience fee on remote ship orders, a share of their price (DECISIONS 9). */
    public float shipyardFee = 0.02f;
    /** C/F: the automatic upkeep cap's share of net income (0.2.24.0's 10%), set by the Spending preset. */
    public float upkeepShare = CATuning.UPKEEP_SHARE;
    /** D: Auto-expansion (EXPAND_NAMES), 0 Off for every save (DECISIONS 5). */
    public int expandMode = 0;
    /** F (WS-F1 fix, DECISIONS 13): true: the per-project cap is the Fixed number in perProjectCap. false: Automatic, it follows net income and Spending (CAPresets.projectCap). A save from before 0.3.0 loads false and CAMigrate.to3 sets true when its cap differs from the Automatic one. A new CASettings is Fixed until first-time setup (CASetupUI.applyRecommended) sets Automatic. */
    public boolean projectCapFixed = true;
    /** F (WS-F1 fix): Capital does (0 or 1) in effect before the Capital row went to None; leaving None restores it. Older saves load 0 (Deliveries and commerce, as 0.2.45.1 left Off). */
    public int capitalModeBeforeNone = 0;
    public static final String[] EXPAND_NAMES = {"Off", "Ask first", "Auto"};
    /** D: indices into the names below; 0 is the recommended choice for each. */
    public int expandLine = 0, expandScope = 0, expandGoal = 0, expandMethod = 0, expandMax = 0;
    public static final String[] EXPAND_LINE_NAMES = {"Money ladder (Idle money and up)", "Over 5,000,000", "Over 25,000,000", "Over 50,000,000"};
    public static final float[] EXPAND_LINES = {0f, 5000000f, 25000000f, 50000000f};   // 0: the money ladder decides (CARich)
    public static final String[] EXPAND_SCOPE_NAMES = {"Systems you hold", "Those first, then nearby systems", "Within 15 ly of the capital"};
    public static final String[] EXPAND_METHOD_NAMES = {"Nexerelin colony fleet when possible", "Autopilot crew only"};
    public static final int[] EXPAND_MAX = {0, 5, 10, 15, 20};   // 0: your colony limit plus the administrators you can employ
    public static final String[] EXPAND_MAX_NAMES = {"Colony limit and administrators", "5 colonies", "10 colonies", "15 colonies", "20 colonies"};
    /** D: inverted so every save has them on: fast buildout on a new colony, and an administrator hired for it past the limit. */
    public boolean noExpandBuildout = false, noExpandAdmins = false;
    /** D: the first-time Auto-expansion dialog has been shown. */
    public boolean expandConfirmed = false;
    /** E: War level (CAWarPolicy.LEVEL_NAMES), 2 Retaliate = 0.2.45's "Protect and strike back". Older saves: CAMigrate from warMode. */
    public int warLevel = 2;
    public static final String[] WAR_LEVEL_NAMES = CAWarPolicy.LEVEL_NAMES;
    /** E: false in saves from before 0.3.0 (missing field), so CAMigrate sets warLevel from warMode once. */
    public boolean warLevelChecked = true;
    /** E: inverted: retake your own lost colonies at Retaliate and up (DECISIONS 3). CAMigrate turns it off for older saves. */
    public boolean noWarRetake = false;
    /** E: treat the top-threat hostile Auto faction as Rival; a coalition against you is acceptable; saturation bombardment authorized. */
    public boolean warAutoRivals = false, warCoalitionOK = false, warScorchedOK = false;
    /** E: mode a conquered colony gets (CONQUERED_NAMES); what Scorched bombards (SCORCHED_TARGET_NAMES). */
    public int conqueredMode = 0, scorchedTargets = 0;
    public static final String[] CONQUERED_NAMES = {"Auto", "Approve", "Advisor"};
    public static final String[] SCORCHED_TARGET_NAMES = {"Capitals only", "Capitals and small colonies"};
    /** E: faction id -> stance (CAStance, -3 Ally .. +3 Destroy). A missing entry is Auto (0). Older saves: CAMigrate from factionPriority. */
    public java.util.Map<String, Integer> factionStance;

    public java.util.Map<String, Integer> factionStance() {
        if (factionStance == null) factionStance = new java.util.HashMap<String, Integer>();
        return factionStance;
    }

    /** A faction's stance, Auto (0) when none is set. */
    public int stanceOf(String factionId) {
        if (factionStance == null || factionId == null) return 0;
        Integer v = factionStance.get(factionId);
        return v == null ? 0 : v;
    }

    // ---------------- 0.3.1.0: enforcer patrols (CAEnforcers) ----------------
    // Every field loads as 0 or false from an older save, and 0 or false is the cautious choice for each: the feature off,
    // every fleet type off, cost Reputation, every gate on (the ...OK fields are inverted), Auto colonies' systems only,
    // no intercept orders, nothing owed.
    /** 0.3.1.0: master switch. */
    public boolean enfOn = false;
    /** 0.3.1.0: one switch per fleet type (CAEnforcers kind order, ENF_KIND_NAMES). */
    public boolean enfInvestigators = false, enfInspections = false, enfLeague = false, enfProtectors = false, enfRaiders = false, enfPolice = false;
    /** 0.3.1.0: cost mode, index into ENF_COST_NAMES; 0 Reputation. */
    public int enfCost = 0;
    public static final String[] ENF_COST_NAMES = {"Reputation", "Credits", "Both", "None"};
    public static final int ENF_REP = 0, ENF_CREDITS = 1, ENF_BOTH = 2, ENF_NONE = 3;
    /** 0.3.1.0: fleet types, CAEnforcers kind order. Police is Nexerelin's. */
    public static final String[] ENF_KIND_NAMES = {"Hegemony investigators", "Hegemony AI inspections", "Persean League enforcers",
            "Luddic sacred protectors", "Tri-Tachyon commerce raiders", "Nexerelin police"};
    /** 0.3.1.0: gates, inverted so false keeps the gate on: your commissioner, allies, ceasefires, Low profile, your fleet's system. */
    public boolean enfCommissionOK = false, enfAlliesOK = false, enfCeasefireOK = false, enfLowProfileOK = false, enfNearYouOK = false;
    /** 0.3.1.0: false: systems with Auto colonies only; true: systems with any colony of yours. */
    public boolean enfAllColonies = false;
    /** 0.3.1.0: colony patrols in the system get an intercept order. */
    public boolean enfIntercept = false;
    /** 0.3.1.0: credits owed for defeated fleets that could not be paid above the reserve; paid first, no new attacks until then. */
    public float enfOwed = 0f;
    public static final String[] ENF_WHERE_NAMES = {"Systems with Auto colonies", "Systems with any colony"};

    /** 0.3.1.0: whether one fleet type is on (CAEnforcers kind index); false for an unknown index. */
    public boolean enfKind(int kind) {
        switch (kind) {
            case 0: return enfInvestigators;
            case 1: return enfInspections;
            case 2: return enfLeague;
            case 3: return enfProtectors;
            case 4: return enfRaiders;
            case 5: return enfPolice;
            default: return false;
        }
    }

    /** 0.3.1.0: sets one fleet type on or off (CAEnforcers kind index). */
    public void setEnfKind(int kind, boolean on) {
        switch (kind) {
            case 0: enfInvestigators = on; break;
            case 1: enfInspections = on; break;
            case 2: enfLeague = on; break;
            case 3: enfProtectors = on; break;
            case 4: enfRaiders = on; break;
            case 5: enfPolice = on; break;
            default: break;
        }
    }

    // ---------------- 0.3.2.0: production priorities (CAFloors, CAMoneyCurve, diversity) ----------------
    // An older save loads every field below as 0, false or null. productionChecked is false then, and fixNulls fills them
    // with productionDefaults() once (CAMigrate.to4 logs it). A field you set later is never filled again.
    /** 0.3.2.0: the production fields below hold a value you or the defaults set (false: an older save, filled by fixNulls). */
    public boolean productionChecked = true;
    /** 0.3.2.0: Production preset: CATuning.PROD_OFF (the 0.3.1.0 picks), PROD_LIGHT (default), PROD_FULL. */
    public int productionMode = CATuning.PROD_LIGHT;
    public static final String[] PRODUCTION_NAMES = {"Off", "Light", "Full"};
    /** 0.3.2.0: floor 1, ship producers wanted (0 to 3); floor 2, capacity a month; floor 3, months for AoTD order goods. */
    public int floorHullSources = CATuning.FLOOR_HULLS_LIGHT;
    public float floorCapacity = CATuning.FLOOR_CAPACITY_LIGHT;
    public int floorOrderMonths = CATuning.FLOOR_ORDER_MONTHS;
    /** 0.3.2.0: floor 4, production quality; floor 6, parts kept on top of the fleet's repair need. */
    public float floorQuality = CATuning.FLOOR_QUALITY_LIGHT;
    public int floorPartsReserve = CATuning.FLOOR_PARTS_RESERVE;
    /** 0.3.2.0: floors 7 and 8 (FLOOR_GOODS_NAMES). */
    public int floorSupplies = CATuning.FLOOR_GOODS_SHORTAGE, floorFuel = CATuning.FLOOR_GOODS_SHORTAGE;
    public static final String[] FLOOR_GOODS_NAMES = {"Off", "No shortage", "Fleet use"};
    /** 0.3.2.0: floors also apply at Money-goal colonies (off by default). */
    public boolean floorMoneyGoal = false;
    /** 0.3.2.0: floor values a month (CATuning.FLOOR_VAL_*): hull source, per capacity credit, per 1.0 quality, per part, order factor, goods factor. */
    public float floorValHull = CATuning.FLOOR_VAL_HULL, floorValCapacity = CATuning.FLOOR_VAL_CAPACITY, floorValQuality = CATuning.FLOOR_VAL_QUALITY,
            floorValPart = CATuning.FLOOR_VAL_PART, floorValOrders = CATuning.FLOOR_VAL_ORDERS, floorValGoods = CATuning.FLOOR_VAL_GOODS;
    /** 0.3.2.0: floor actions rank ahead of other actions in the planner. */
    public boolean needsFirst = true;
    /** 0.3.2.0: a floor producer may replace the weakest income industry: CATuning.MAKE_ROOM_OFF, MAKE_ROOM_ASK (default), MAKE_ROOM_AUTO. */
    public int makeRoom = CATuning.MAKE_ROOM_ASK;
    public static final String[] MAKE_ROOM_NAMES = {"Off", "Ask first", "Auto"};
    /** 0.3.2.0: money taper (CATuning.TAPER_*): Off, Gentle (default), Strong; its two lines for Gentle. */
    public int moneyTaper = CATuning.TAPER_GENTLE;
    public static final String[] TAPER_NAMES = {"Off", "Gentle", "Strong"};
    public float taperLow = CATuning.TAPER_LOW, taperHigh = CATuning.TAPER_HIGH;
    /** 0.3.2.0: diversity: copies of a building family before the next counts less (0: off), and that share per extra copy. */
    public int diversityCopies = CATuning.DIVERSITY_COPIES;
    public float diversityShare = CATuning.DIVERSITY_SHARE;
    /** 0.3.2.0: goal weights: 0 the 0.3.2 table (default), 1 the 0.3.0 table. */
    public int goalWeights = 0;
    public static final String[] GOAL_WEIGHT_NAMES = {"0.3.2 table", "0.3.0 table"};
    /** 0.3.2.0: trade contracts leave floor goods alone while their floor is open (CAContracts). */
    public boolean keepProductionGoods = true;
    /** 0.3.2.0: storage logistics brings Domain-grade Parts from Local Resources to the capital's Storage weekly (off by default). */
    public boolean partsToCapital = false;
    /** 0.3.2.0: the Shipyard rung opens at Surplus (else Idle money) while production floors are on. */
    public boolean shipyardFromSurplus = true;
    /** 0.3.2.0: the Log page shows its Thinking list (CAData.thinkingShown before 0.3.2.0; CAMigrate.to4 copies it). */
    public boolean showThinking = false;

    /** 0.3.2.0: every production field at its default (the Light preset). Reset to recommended and older saves use it. */
    public void productionDefaults() {
        productionMode = CATuning.PROD_LIGHT;
        floorHullSources = CATuning.FLOOR_HULLS_LIGHT;
        floorCapacity = CATuning.FLOOR_CAPACITY_LIGHT;
        floorOrderMonths = CATuning.FLOOR_ORDER_MONTHS;
        floorQuality = CATuning.FLOOR_QUALITY_LIGHT;
        floorPartsReserve = CATuning.FLOOR_PARTS_RESERVE;
        floorSupplies = CATuning.FLOOR_GOODS_SHORTAGE;
        floorFuel = CATuning.FLOOR_GOODS_SHORTAGE;
        floorMoneyGoal = false;
        floorValHull = CATuning.FLOOR_VAL_HULL; floorValCapacity = CATuning.FLOOR_VAL_CAPACITY; floorValQuality = CATuning.FLOOR_VAL_QUALITY;
        floorValPart = CATuning.FLOOR_VAL_PART; floorValOrders = CATuning.FLOOR_VAL_ORDERS; floorValGoods = CATuning.FLOOR_VAL_GOODS;
        needsFirst = true;
        makeRoom = CATuning.MAKE_ROOM_ASK;
        moneyTaper = CATuning.TAPER_GENTLE;
        taperLow = CATuning.TAPER_LOW; taperHigh = CATuning.TAPER_HIGH;
        diversityCopies = CATuning.DIVERSITY_COPIES;
        diversityShare = CATuning.DIVERSITY_SHARE;
        goalWeights = 0;
        keepProductionGoods = true;
        partsToCapital = false;
        shipyardFromSurplus = true;
        productionChecked = true;
    }

    /** 0.3.2.0: production floors and the rest of the 0.3.2.0 production rules are on (not the Off preset). */
    public boolean productionOn() { return productionMode == CATuning.PROD_LIGHT || productionMode == CATuning.PROD_FULL; }

    public boolean expandBuildout() { return !noExpandBuildout; }
    public boolean expandAdmins() { return !noExpandAdmins; }
    public boolean warRetake() { return !noWarRetake; }

    /** A rung's mode (RUNG_MODE_NAMES); the default when the saved list is shorter. */
    public int rungMode(int rung) {
        if (rung < 0 || rung >= RUNG_DEFAULTS.length) return 0;
        return rungMode != null && rung < rungMode.length ? rungMode[rung] : RUNG_DEFAULTS[rung];
    }

    public List<String> log = new ArrayList<String>();

    public static final String[] FEE_NAMES = {"Off", "Low", "Standard", "High"};
    public static final float[] FEE_MULT = {0f, 0.5f, 1f, 2f};
    public static final String[] SP_NAMES = {"Player only", "One per colony", "Monthly cap"};
    public static final String[] ALPHA_NAMES = {"Only where removable", "Anywhere", "Never"};
    public static final String[] CORE_RISK_NAMES = {"More where defended", "Never inspected", "No limit", "Small colonies only"};
    public static final String[] INSPECTION_NAMES = {"Advise", "Resist where defended", "Off"};
    public static final String[] STATION_IDS = {"score", "low", "mid", "high", "imperium"};
    public static final String[] STATION_NAMES = {"Pick by score", "Low tech", "Midline", "High tech", "Imperium"};
    /** The first station of each line; a line whose station the game doesn't have (its mod is off) isn't offered and counts as Pick by score. */
    public static final String[] STATION_STARTERS = {null, "orbitalstation", "orbitalstation_mid", "orbitalstation_high", "ii_orbitalstation"};

    /** The station the line starts with, or null for Pick by score (also when that station's mod isn't enabled). */
    public static String stationStarter(String line) {
        for (int i = 0; i < STATION_IDS.length; i++) {
            if (!STATION_IDS[i].equals(line) || STATION_STARTERS[i] == null) continue;
            try { if (com.fs.starfarer.api.Global.getSettings().getIndustrySpec(STATION_STARTERS[i]) != null) return STATION_STARTERS[i]; }
            catch (Throwable ignored) { }
        }
        return null;
    }

    void fixNulls() {
        if (log == null) log = new ArrayList<String>();
        if (stationLine == null) stationLine = "score";
        if (hazardPayColonyShare <= 0f) hazardPayColonyShare = CATuning.HAZARD_COLONY_SHARE;   // older saves load these as 0
        if (hazardPayEmpireShare <= 0f) hazardPayEmpireShare = CATuning.HAZARD_EMPIRE_SHARE;
        if (settlementMinIncome <= 0f) settlementMinIncome = 20000f;
        // 0.3.0.0 (A3, 07 L3): the floor is never 0 (as CAData's was: 0 meant the default), and terraforming never gets more than
        // the whole budget or less than none (a broken json value can only make it spend less, never more)
        if (!(minStability > 0f)) minStability = CATuning.MIN_STABILITY;
        if (!(terraformShare >= 0f)) terraformShare = 0f;
        if (terraformShare > 1f) terraformShare = 1f;
        // 0.3.0.0: new numbers older saves load as 0 (and a rung list from before a rung was added) get their defaults
        if (horizonMonths <= 0) horizonMonths = 36;
        if (shipyardStop <= 0) shipyardStop = 6;
        if (shipyardFee <= 0f) shipyardFee = 0.02f;
        if (upkeepShare <= 0f) upkeepShare = CATuning.UPKEEP_SHARE;
        if (rungMode == null || rungMode.length < RUNG_DEFAULTS.length) {
            int[] r = defaultRungs();
            if (rungMode != null) System.arraycopy(rungMode, 0, r, 0, rungMode.length);
            rungMode = r;
        }
        // 0.3.1.0: a cost mode out of range reads as Reputation; owed credits are never below 0 or NaN
        if (enfCost < 0 || enfCost >= ENF_COST_NAMES.length) enfCost = ENF_REP;
        if (!(enfOwed > 0f)) enfOwed = 0f;
        // 0.3.2.0: an older save loads the production fields as 0 and productionChecked false: the defaults (Light), once
        if (!productionChecked) { productionDefaults(); productionFilled = true; }
        // 0.3.2.0: values out of range (a broken settings file) read as the nearest allowed one
        if (productionMode < CATuning.PROD_OFF || productionMode > CATuning.PROD_FULL) productionMode = CATuning.PROD_LIGHT;
        floorHullSources = Math.max(0, Math.min(3, floorHullSources));
        if (!(floorCapacity >= 0f)) floorCapacity = CATuning.FLOOR_CAPACITY_LIGHT;
        floorOrderMonths = Math.max(3, Math.min(24, floorOrderMonths));
        if (!(floorQuality >= 0.5f) || floorQuality > 1.2f) floorQuality = CATuning.FLOOR_QUALITY_LIGHT;
        floorPartsReserve = Math.max(0, Math.min(1000, floorPartsReserve));
        if (floorSupplies < 0 || floorSupplies > 2) floorSupplies = CATuning.FLOOR_GOODS_SHORTAGE;
        if (floorFuel < 0 || floorFuel > 2) floorFuel = CATuning.FLOOR_GOODS_SHORTAGE;
        if (makeRoom < 0 || makeRoom > 2) makeRoom = CATuning.MAKE_ROOM_ASK;
        if (moneyTaper < 0 || moneyTaper > 2) moneyTaper = CATuning.TAPER_GENTLE;
        if (!(taperLow > 0f)) taperLow = CATuning.TAPER_LOW;
        if (!(taperHigh > taperLow)) taperHigh = Math.max(taperLow + 1f, CATuning.TAPER_HIGH);
        diversityCopies = Math.max(0, Math.min(6, diversityCopies));
        if (!(diversityShare >= 0.5f) || diversityShare > 1f) diversityShare = CATuning.DIVERSITY_SHARE;
        if (goalWeights < 0 || goalWeights > 1) goalWeights = 0;
        if (!(floorValHull >= 0f)) floorValHull = CATuning.FLOOR_VAL_HULL;
        if (!(floorValCapacity >= 0f)) floorValCapacity = CATuning.FLOOR_VAL_CAPACITY;
        if (!(floorValQuality >= 0f)) floorValQuality = CATuning.FLOOR_VAL_QUALITY;
        if (!(floorValPart >= 0f)) floorValPart = CATuning.FLOOR_VAL_PART;
        if (!(floorValOrders >= 0f)) floorValOrders = CATuning.FLOOR_VAL_ORDERS;
        if (!(floorValGoods >= 0f)) floorValGoods = CATuning.FLOOR_VAL_GOODS;
    }

    /** 0.3.2.0: fixNulls filled the production fields this session (an older save); CAMigrate.to4 logs it once. Not saved. */
    transient boolean productionFilled;
}
