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
    public float terraformShare = 0.4f;
    public float newUpkeepCap = 50000f;   // the Fixed cap; the Automatic one follows net income (CABudget.upkeepCap)
    // new upkeep cap: Automatic (10% of last month's net income, at least 20,000) unless set to Fixed. upkeepCapChecked
    // is false loading a save from before 2.24.0, whose fixed cap (set once at setup) becomes Automatic once
    public boolean upkeepCapAuto = true, upkeepCapChecked = true;
    public boolean pauseOnLoss = true;
    // surplus investment (CASurplus): credits over the line go into one focus world; index into SURPLUS_LINES (0 off).
    // surplusChecked is false loading a save from before 2.21.0, which gets the recommended line once.
    public int surplusLine = 2;
    // past CASurplus.RICH over the line every Approve/Auto colony spends from the surplus (false, also older saves); true: the focus world only
    public boolean surplusFocusOnly = false;
    public float richIncomeRef = 0f;   // best net income while spending freely (its floor is half of it)
    public boolean noWonders = false;  // false (also older saves): spending freely builds the Daedalus Array and Bifrost gates (CAWonders)
    /** Announcement voice (2.41.0): 0 Machine (default, also older saves), 1 Plain. */
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
    public boolean storageCheat = false;  // unused since 2.37.0 (the storage cheat moved to Jonah's Easy Sector); kept so older saves load
    public int coreRisk = 1;              // 0 more where defenses beat the next inspection, 1 never inspected (default for new saves), 2 no limit, 3 small colonies only
    public int inspectionMode = 0;        // Hegemony inspections of Auto colonies: 0 advise (so older saves load as advise), 1 resist where defended, 2 off
    public boolean allowRemovals = false;
    public int storyPointRule = 0;        // 0 player only, 1 one per colony, 2 monthly cap
    public int storyPointMonthlyCap = 2;
    public boolean adminHiring = false;
    public boolean adminOptimal = true;
    public boolean adminDelay = true;
    // ambassadors: focus 0 worst relations, 1 strongest factions, 2 nearest neighbors, 3 priority list only
    public int ambassadorFocus = 0;
    public int ambassadorStopAt = 2;   // index into STOP_NAMES/STOP_VALUES
    public java.util.Map<String, Integer> factionPriority;   // faction id -> 0 ignore, 1 normal (default), 2 high
    public static final String[] FOCUS_NAMES = {"Worst relations first", "Strongest factions", "Nearest neighbors", "Priority list only"};
    public static final String[] STOP_NAMES = {"Welcoming", "Friendly", "Cooperative", "Never stop"};
    public static final float[] STOP_VALUES = {0.25f, 0.5f, 0.75f, 2f};
    public static final String[] PRIORITY_NAMES = {"Ignore", "Normal", "High"};

    public int priorityOf(String factionId) {
        if (factionPriority == null) return 1;
        Integer p = factionPriority.get(factionId);
        return p == null ? 1 : p;
    }

    public int hazardPayMode = 0, freePortMode = 0;   // 0 auto, 1 don't touch
    public float hazardPayColonyShare = 0.25f, hazardPayEmpireShare = 0.30f;
    public boolean hazardLimitChecked = true;   // false in saves from before 2.20.0, whose old 10% default CAPolicies raises once
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
    public boolean spyNoHiring = false;
    public int spyKeep = 0;
    public static final String[] SPY_NAMES = {"Off", "Defense", "Defense and offense"};
    // whom the offense agent sabotages (2.37.0): null whoever attacks you, else Nexerelin's pick; "random" a random enemy; or a faction id
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
    // unused since 2.37.0, kept so older saves load (the save can't drop a field): the Derelict Empire "Attacks on your
    // colonies" settings moved to Jonah's Easy Sector, and so did the storage cheat (storageCheat, above)
    public int haFleetCap = 0, nexAttackGap = 0;

    // this month
    public int month = -1;
    public float spent = 0f, fees = 0f, upkeepAdded = 0f, terraSpent = 0f;
    public float budgetCarry = 0f;   // 2.39.0: unspent budget saved up from earlier months (CABudget.rollMonth), up to 6 months' worth
    // 2.39.0 storage logistics (CALogistics): 0 off (default), 1 on; refit hub market id (null: the production gathering
    // point), sell hub market id (null: none); ships and supplies/fuel/crew/marines go to the refit hub too
    public int logisticsMode = 0;
    /** Stable locations (2.40.0, CAObjectives): 0 Auto (also what older saves get), 1 Recommend only, 2 Off. */
    public int objectiveMode = 0;
    /** Terraforming & Station Construction's stations (2.40.1, CATascStations): 0 Recommend only (also older saves), 1 Auto, 2 Off. */
    public int stationMode = 0;
    public static final String[] TASC_STATION_NAMES = {"Recommend only", "Auto", "Off"};
    public static final String[] OBJECTIVE_NAMES = {"Auto", "Recommend only", "Off"};
    public String logisticsHub, logisticsSellHub;
    public boolean logisticsShips = true, logisticsSupplies = true;
    public int spMonth = 0;   // story points spent this month (counted improvements before 2.20.1)
    public float lastNet = 0f;
    public boolean lastNetKnown = false;

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
        if (hazardPayColonyShare <= 0f) hazardPayColonyShare = 0.25f;   // older saves load these as 0
        if (hazardPayEmpireShare <= 0f) hazardPayEmpireShare = 0.30f;
        if (settlementMinIncome <= 0f) settlementMinIncome = 20000f;
    }
}
