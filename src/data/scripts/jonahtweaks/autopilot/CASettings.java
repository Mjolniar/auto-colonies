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
    public float newUpkeepCap = 50000f;
    public boolean pauseOnLoss = true;

    // services
    public int feeLevel = 2;              // 0 off, 1 low, 2 standard, 3 high
    public String stationLine = "score";  // score, low, mid, high
    public boolean useAICores = false;
    public int alphaMode = 0;             // 0 only where removable (default), 1 anywhere, 2 never
    public boolean storageCheat = false;  // cheat: Autopilot Storage takes anything and colonies draw shortages from it
    public int coreRisk = 0;              // 0 more where defenses beat the next inspection (default), 1 never inspected, 2 no limit
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
    public float hazardPayColonyShare = 0.25f, hazardPayEmpireShare = 0.10f;
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
    // Nexerelin emergency autonomy (CAAutonomy): 0 off (default, so older saves load as off), 1 recommend, 2 auto
    public int autonomyMode = 0;
    public static final String[] AUTONOMY_NAMES = {"Off", "Recommend", "Auto"};
    // the Colony Autopilot button on your colony's screen (CAScreenButton); on unless this is set
    public boolean noScreenButton = false;
    // attacks on your colonies (CAAttacks), never in the recommended settings: Hostile Activity fleets per system (0 = the
    // game's own) and, with Nexerelin, days between its invasions, raids and blockades of yours (0 = the game's own)
    public int haFleetCap = 0, nexAttackGap = 0;
    public static final int[] HA_CAPS = {0, 8, 6, 4, 2};
    public static final int[] NEX_GAPS = {0, 30, 60, 90, 180};

    static int next(int[] steps, int v) {
        for (int i = 0; i < steps.length; i++) if (steps[i] == v) return steps[(i + 1) % steps.length];
        return steps[0];
    }

    // this month
    public int month = -1;
    public float spent = 0f, fees = 0f, upkeepAdded = 0f, terraSpent = 0f;
    public int spMonth = 0;
    public float lastNet = 0f;
    public boolean lastNetKnown = false;

    public List<String> log = new ArrayList<String>();

    public static final String[] FEE_NAMES = {"Off", "Low", "Standard", "High"};
    public static final float[] FEE_MULT = {0f, 0.5f, 1f, 2f};
    public static final String[] SP_NAMES = {"Player only", "One per colony", "Monthly cap"};
    public static final String[] ALPHA_NAMES = {"Only where removable", "Anywhere", "Never"};
    public static final String[] CORE_RISK_NAMES = {"More where defended", "Never inspected", "No limit"};
    public static final String[] STATION_IDS = {"score", "low", "mid", "high"};
    public static final String[] STATION_NAMES = {"Pick by score", "Low tech", "Midline", "High tech"};

    void fixNulls() {
        if (log == null) log = new ArrayList<String>();
        if (stationLine == null) stationLine = "score";
        if (hazardPayColonyShare <= 0f) hazardPayColonyShare = 0.25f;   // older saves load these as 0
        if (hazardPayEmpireShare <= 0f) hazardPayEmpireShare = 0.10f;
        if (settlementMinIncome <= 0f) settlementMinIncome = 20000f;
    }
}
