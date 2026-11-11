package data.scripts.jonahtweaks.autopilot;

/**
 * 0.3.0.0 (WS-F1, research 04 section 3): the settings screen's preset rows. Each row writes the old fields, as
 * CASetupUI.applyRecommended does, and the engine keeps reading those fields. The value a row shows is worked out on every
 * draw: match() returns the value whose fields all equal the row's table, or CUSTOM (-1) when they match none. Only the
 * fields of enabled mods count, so a field of a mod that is off never makes a row read Custom.
 * <pre>
 *  row            values (rec marked *)                                   fields it writes
 *  Spending       Thrifty, Normal*, Lavish                                budgetAuto, budgetPercentMode, budgetPercent, savingsShare,
 *                                                                         upkeepCapAuto, upkeepShare, surplusTerraform unless Off
 *                                                                         (not matched); an Automatic per-project cap follows the
 *                                                                         value (projectCap(CASettings)); pauseOnLoss is left alone
 *  Extras         Auto*, Recommend only, Off                              objectiveMode, measuredMode, contractMode (ToT), researchMode,
 *                                                                         projectSupplyMode, hypershuntMode (VoK), augmentMode (SA),
 *                                                                         settlementMode (RAT), disableSpecialties, disableVPC,
 *                                                                         disableShipping (IndEvo), disableStabilizers (ET IX)
 *  New colonies   Off*, Ask first, Auto                                   expandMode, stationMode (TASC; tascMode() is the one in effect)
 *  Take down      Never, Clean up*, Free hand                             noPrune, endgameNoReplace, capitalNoRoom, allowRemovals
 *  AI cores       Off, Safe*, Small colonies only, Defended, No limit     useAICores, coreRisk, inspectionMode (Off keeps the last two)
 *  Admins         Off, Place only, Hire and place*                        adminHiring, adminOptimal
 *  Policies       Auto*, Don't touch                                      hazardPayMode, freePortMode, disableEdicts (IndEvo)
 *  Story points   Yours only*, One per colony, 2 a month, 5 a month       storyPointRule, storyPointMonthlyCap
 *  Stations       Pick by score*, Low tech .. Imperium, Only where they pay   stationLine, noStationPlan
 *  War            Off .. Scorched (CAWarPolicy.LEVEL_NAMES), Retaliate*   warLevel
 * </pre>
 * The over-limit fix (noOverLimitFix) is in no row: it keeps colonies off 0 stability and stays on through every preset.
 * Pure apart from reading which mods are enabled and the net income for the per-project cap; apply() writes fields only,
 * the settings screen does the side effects (cache resets). Checked offline by JTPresetsCheck.
 * 0.3.0.0 (WS-F1 fix): the per-project cap is Automatic (DECISIONS 13) and read through projectCap(CASettings); Spending
 * leaves surplus terraforming Off and Pause on loss as you set them; tascMode() keeps TASC stations from founding colonies
 * while New colonies on Auto asks first.
 */
public class CAPresets {
    public static final String SPENDING = "spending", EXTRAS = "extras", NEW_COLONIES = "newcolonies", TAKE_DOWN = "takedown",
            AI_CORES = "aicores", ADMINS = "admins", POLICIES = "policies", STORY = "story", STATIONS = "stations", WAR = "war";
    /** Every preset row, in menu order. */
    public static final String[] ROWS = {SPENDING, EXTRAS, NEW_COLONIES, WAR, STORY, STATIONS, TAKE_DOWN, POLICIES, AI_CORES, ADMINS};
    /** match() when the fields fit no value; shown as "Custom". */
    public static final int CUSTOM = -1;
    public static final String CUSTOM_NAME = "Custom";

    // mod ids, as the classes that read the fields check them
    static final String TOT = "aotd_theory_of_toolbox", VOK = "aotd_vok", RAT = "assortment_of_things", SA = "niko_stationAugments",
            INDEVO = "IndEvo", IX = "EmergentThreats_IX_Revival", TASC = "Terraforming & Station Construction";

    // ---------------- the tables ----------------

    static final String[] SPENDING_NAMES = {"Thrifty", "Normal", "Lavish"};
    static final float[] SPEND_INCOME = {0.30f, 0.50f, 0.80f};
    static final float[] SPEND_SAVINGS = {0.05f, 0.15f, 0.30f};
    static final float[] SPEND_UPKEEP = {0.05f, 0.10f, 0.20f};
    /** The Automatic per-project cap: this many times net income, between the floor and the ceiling (Normal = 0.2.45's recommendation). */
    static final float[] CAP_MULT = {0.5f, 1f, 2f}, CAP_MIN = {500000f, 1000000f, 2000000f}, CAP_MAX = {2500000f, 5000000f, 10000000f};
    /** Surplus terraforming by Spending: Thrifty only what pays back in 10 years (CASettings.SURPLUS_TF_NAMES). Off is kept. */
    static final int[] SPEND_TF = {1, 0, 0};
    static final int SURPLUS_TF_OFF = 2;

    static final String[] EXTRAS_NAMES = {"Auto", "Recommend only", "Off"};
    static final int[] X_OBJECTIVE = {0, 1, 2}, X_MEASURED = {0, 1, 2}, X_CONTRACT = {1, 0, 2}, X_RESEARCH = {1, 0, 2},
            X_PROJECT = {0, 1, 2}, X_SHUNT = {0, 1, 2}, X_AUGMENT = {0, 1, 2}, X_SETTLE = {2, 0, 3};
    static final boolean[] X_DISABLE = {false, true, true};

    static final String[] NEW_COLONY_NAMES = {"Off", "Ask first", "Auto"};
    /** TASC stations by New colonies: Recommend only (0, also every older save) unless Auto (1). */
    static final int[] NC_TASC = {0, 0, 1};

    static final String[] TAKE_DOWN_NAMES = {"Never", "Clean up", "Free hand"};
    static final boolean[] TD_NO = {true, false, false}, TD_REMOVALS = {false, false, true};

    static final String[] AI_CORE_NAMES = {"Off", "Safe", "Small colonies only", "Defended", "No limit"};
    /** coreRisk and inspectionMode per value; Off (index 0) keeps both. Defended resists inspections, which makes the Hegemony hostile. */
    static final int[] CORE_RISK = {-1, 1, 3, 0, 2}, CORE_INSPECT = {-1, 0, 0, 1, 0};
    public static final int CORES_DEFENDED = 3;

    static final String[] ADMIN_NAMES = {"Off", "Place only", "Hire and place"};
    static final boolean[] ADMIN_HIRE = {false, false, true}, ADMIN_PLACE = {false, true, true};

    static final String[] POLICY_NAMES = {"Auto", "Don't touch"};

    static final String[] STORY_NAMES = {"Yours only", "One per colony", "2 a month", "5 a month"};
    static final int[] STORY_RULE = {0, 1, 2, 2}, STORY_CAP = {-1, -1, 2, 5};

    /** CASettings.STATION_NAMES, then "Only where they pay" (noStationPlan: no station planned at every colony, the line kept). */
    static final String[] STATION_NAMES = {"Pick by score", "Low tech", "Midline", "High tech", "Imperium", "Only where they pay"};
    public static final int STATIONS_ONLY_WHERE_THEY_PAY = 5;

    /** The recommended value of each row (CASetupUI.applyRecommended lands on these). */
    public static int rec(String row) {
        if (SPENDING.equals(row)) return 1;
        if (TAKE_DOWN.equals(row)) return 1;
        if (AI_CORES.equals(row)) return 1;
        if (ADMINS.equals(row)) return 2;
        if (WAR.equals(row)) return CAWarPolicy.RETALIATE;
        return 0;   // Extras Auto, New colonies Off, Policies Auto, Story points Yours only, Stations Pick by score
    }

    public static String[] values(String row) {
        if (SPENDING.equals(row)) return SPENDING_NAMES;
        if (EXTRAS.equals(row)) return EXTRAS_NAMES;
        if (NEW_COLONIES.equals(row)) return NEW_COLONY_NAMES;
        if (TAKE_DOWN.equals(row)) return TAKE_DOWN_NAMES;
        if (AI_CORES.equals(row)) return AI_CORE_NAMES;
        if (ADMINS.equals(row)) return ADMIN_NAMES;
        if (POLICIES.equals(row)) return POLICY_NAMES;
        if (STORY.equals(row)) return STORY_NAMES;
        if (STATIONS.equals(row)) return STATION_NAMES;
        if (WAR.equals(row)) return CAWarPolicy.LEVEL_NAMES;
        return null;
    }

    /** The CASettings fields a row may write (JTPresetsCheck: apply touches nothing else). */
    public static String[] fields(String row) {
        if (SPENDING.equals(row)) return new String[]{"budgetAuto", "budgetPercentMode", "budgetPercent", "savingsShare", "upkeepCapAuto",
                "upkeepCapChecked", "upkeepShare", "surplusTerraform"};
        if (EXTRAS.equals(row)) return new String[]{"objectiveMode", "measuredMode", "contractMode", "researchMode", "projectSupplyMode",
                "hypershuntMode", "augmentMode", "settlementMode", "disableSpecialties", "disableVPC", "disableShipping", "disableStabilizers"};
        if (NEW_COLONIES.equals(row)) return new String[]{"expandMode", "stationMode"};
        if (TAKE_DOWN.equals(row)) return new String[]{"noPrune", "endgameNoReplace", "capitalNoRoom", "allowRemovals"};
        if (AI_CORES.equals(row)) return new String[]{"useAICores", "coreRisk", "inspectionMode"};
        if (ADMINS.equals(row)) return new String[]{"adminHiring", "adminOptimal"};
        if (POLICIES.equals(row)) return new String[]{"hazardPayMode", "freePortMode", "disableEdicts"};
        if (STORY.equals(row)) return new String[]{"storyPointRule", "storyPointMonthlyCap"};
        if (STATIONS.equals(row)) return new String[]{"stationLine", "noStationPlan"};
        if (WAR.equals(row)) return new String[]{"warLevel"};
        return new String[0];
    }

    // ---------------- apply ----------------

    /** Writes the row's fields for this value. A value out of range does nothing. */
    public static void apply(CASettings s, String row, int value) {
        String[] names = values(row);
        if (s == null || names == null || value < 0 || value >= names.length) return;
        if (SPENDING.equals(row)) applySpending(s, value);
        else if (EXTRAS.equals(row)) {
            s.objectiveMode = X_OBJECTIVE[value];
            s.measuredMode = X_MEASURED[value];
            s.contractMode = X_CONTRACT[value];
            s.researchMode = X_RESEARCH[value];
            s.projectSupplyMode = X_PROJECT[value];
            s.hypershuntMode = X_SHUNT[value];
            s.augmentMode = X_AUGMENT[value];
            s.settlementMode = X_SETTLE[value];
            s.disableSpecialties = s.disableVPC = s.disableShipping = s.disableStabilizers = X_DISABLE[value];
        } else if (NEW_COLONIES.equals(row)) {
            s.expandMode = value;
            s.stationMode = NC_TASC[value];
        } else if (TAKE_DOWN.equals(row)) {
            s.noPrune = s.endgameNoReplace = s.capitalNoRoom = TD_NO[value];
            s.allowRemovals = TD_REMOVALS[value];
        } else if (AI_CORES.equals(row)) {
            s.useAICores = value != 0;
            if (value != 0) { s.coreRisk = CORE_RISK[value]; s.inspectionMode = CORE_INSPECT[value]; }
        } else if (ADMINS.equals(row)) {
            s.adminHiring = ADMIN_HIRE[value];
            s.adminOptimal = ADMIN_PLACE[value];
        } else if (POLICIES.equals(row)) {
            s.hazardPayMode = value;
            s.freePortMode = value;
            s.disableEdicts = value == 1;
        } else if (STORY.equals(row)) {
            s.storyPointRule = STORY_RULE[value];
            if (STORY_CAP[value] > 0) s.storyPointMonthlyCap = STORY_CAP[value];
        } else if (STATIONS.equals(row)) {
            if (value == STATIONS_ONLY_WHERE_THEY_PAY) s.noStationPlan = true;   // the line is kept
            else { s.noStationPlan = false; s.stationLine = CASettings.STATION_IDS[value]; }
        } else if (WAR.equals(row)) s.warLevel = CAWarPolicy.clamp(value);
    }

    /**
     * Spending. 0.3.0.0 (WS-F1 fix): writes the matched fields and surplus terraforming, which stays Off when you set it Off.
     * Pause on loss and the per-project cap are left as they are; an Automatic cap follows the new value through
     * projectCap(CASettings), and a Fixed one keeps its number. A full CYCLE keeps surplus terraforming Off, Pause on loss
     * and a Fixed cap as they were (JTPresetsCheck).
     */
    static void applySpending(CASettings s, int v) {
        s.budgetAuto = true;
        s.budgetPercentMode = true;
        s.budgetPercent = SPEND_INCOME[v];
        s.savingsShare = SPEND_SAVINGS[v];
        s.upkeepCapAuto = true;
        s.upkeepCapChecked = true;
        s.upkeepShare = SPEND_UPKEEP[v];
        if (s.surplusTerraform != SURPLUS_TF_OFF) s.surplusTerraform = SPEND_TF[v];
    }

    /** The per-project cap of a Spending value at this net income, rounded to 250,000. */
    static float projectCap(int v, float net) {
        float n = Math.max(0f, net) * CAP_MULT[v];
        return Math.min(CAP_MAX[v], CASetupUI.round(Math.max(CAP_MIN[v], n), 250000f));
    }

    /**
     * 0.3.0.0 (WS-F1 fix, DECISIONS 13): the per-project cap in effect, read by every check against the cap (CASurplus.projectCap,
     * CATerraform, CASettlement, CAHypershunt). Automatic (projectCapFixed false) follows net income each time it is read, like
     * the upkeep cap: half, all or twice net income for Thrifty, Normal and Lavish, within each value's floor and ceiling.
     * Fixed is the number in perProjectCap.
     */
    public static float projectCap(CASettings s) {
        if (s == null) return 0f;
        if (s.projectCapFixed) return s.perProjectCap;
        return projectCap(capTier(s), net(s));
    }

    /**
     * The Spending value an Automatic cap uses: the one whose share of net income is nearest the budget's. That is the
     * matched value, and for Custom spending the closest one (a tie goes to the lower value).
     */
    static int capTier(CASettings s) {
        int best = rec(SPENDING);
        float bestGap = Float.MAX_VALUE;
        for (int v = 0; v < SPEND_INCOME.length; v++) {
            float gap = Math.abs(s.budgetPercent - SPEND_INCOME[v]);
            if (gap < bestGap - 0.0001f) { bestGap = gap; best = v; }
        }
        return best;
    }

    /**
     * 0.3.0.0 (WS-F1 fix, research 04 section 5.4): a save from before 0.3.0 keeps its per-project cap as a Fixed number unless
     * it equals the Automatic cap now. Called once from CAMigrate.to3; running it again at the same net income changes nothing.
     * 0.3.0.0 (WS-F1 fix 2): reads net income through netPeek, which records nothing, so the load-time endgame reference move
     * (CABudget.rebaseEndgame) still runs after CAMigrate.run stamps migratedAt.
     */
    public static void migrateProjectCap(CASettings s) {
        if (s == null || s.projectCapFixed) return;
        float auto;
        try { auto = projectCap(capTier(s), netPeek(s)); } catch (Throwable t) { s.projectCapFixed = true; return; }
        if (Math.abs(s.perProjectCap - auto) > 0.5f) s.projectCapFixed = true;
    }

    static float net(CASettings s) {
        try { return Math.max(0f, CABudget.netIncome(s)); } catch (Throwable t) { return 0f; }
    }

    /**
     * 0.3.0.0 (WS-F1 fix 2): the net income CABudget.netIncome would return now, never below 0, read without its side effects.
     * On a save from before 0.3.0 the colony net history is empty, and netIncome seeds it from last month's report and moves
     * the endgame reference to colony net (CABudget.colonyNet, rebaseEndgame). Inside CAMigrate.to3 migratedAt is still 0,
     * so that move saw a save started on 0.3.0, skipped, and never ran at load; the endgame kept 0.2.45's whole-net reference
     * against colony net until the next month. This reads the same number in the same order (the median on record, last
     * month's colony net from the report, last month's whole net, this month's colony net income) and records nothing.
     */
    static float netPeek(CASettings s) {
        try {
            java.util.List<Float> h = CAData.get().colonyNetHistory();
            if (!h.isEmpty()) return Math.max(0f, CABudget.median(h, CABudget.NET_MONTHS));
            if (s.lastNetKnown && !CALog.whatIf()) {
                com.fs.starfarer.api.campaign.econ.MonthlyReport prev =
                        com.fs.starfarer.api.impl.campaign.shared.SharedData.getData().getPreviousReport();
                if (prev != null && prev.getRoot() != null) {
                    prev.computeTotals();
                    float month = CABudget.monthNet(prev);
                    if (!Float.isNaN(month) && !Float.isInfinite(month)) return Math.max(0f, month);
                }
            }
        } catch (Throwable ignored) { }
        if (s.lastNetKnown) return Math.max(0f, s.lastNet);
        float sum = 0f;
        try { for (com.fs.starfarer.api.campaign.econ.MarketAPI m : com.fs.starfarer.api.util.Misc.getPlayerMarkets(false)) sum += m.getNetIncome(); }
        catch (Throwable ignored) { }
        return Math.max(0f, sum);
    }

    /**
     * 0.3.0.0 (WS-F1 fix): the TASC station mode in effect (CASettings.TASC_STATION_NAMES: 0 Recommend only, 1 Auto, 2 Off),
     * read by CATascStations.run. With New colonies on Auto, TASC stations follow its mode in effect (CAExpansion.mode): while
     * the Expansion rung makes it ask first or stops it, Auto stations are only recommended. With New colonies Off or Ask
     * first, the Advanced TASC row's setting applies as it did in 0.2.45.1.
     */
    public static int tascMode(CASettings s) {
        int m = Math.max(0, Math.min(2, s.stationMode));
        if (m == 1 && s.expandMode == 2 && CAExpansion.mode(s) < 2) return 0;
        return m;
    }

    // ---------------- match ----------------

    /** The value whose fields all match, or CUSTOM. Fields of mods that are off are skipped. */
    public static int match(CASettings s, String row) {
        if (s == null) return CUSTOM;
        if (SPENDING.equals(row)) {
            if (!s.budgetAuto || !s.upkeepCapAuto) return CUSTOM;
            for (int v = 0; v < SPENDING_NAMES.length; v++)
                if (near(s.budgetPercent, SPEND_INCOME[v]) && near(s.savingsShare, SPEND_SAVINGS[v]) && near(upkeepShare(s), SPEND_UPKEEP[v])) return v;
            return CUSTOM;
        }
        if (EXTRAS.equals(row)) {
            boolean tot = mod(TOT), vok = mod(VOK), rat = mod(RAT), sa = mod(SA), ie = mod(INDEVO), ix = mod(IX);
            for (int v = 0; v < EXTRAS_NAMES.length; v++) {
                if (s.objectiveMode != X_OBJECTIVE[v] || s.measuredMode != X_MEASURED[v]) continue;
                if (tot && s.contractMode != X_CONTRACT[v]) continue;
                if (vok && (s.researchMode != X_RESEARCH[v] || s.projectSupplyMode != X_PROJECT[v] || s.hypershuntMode != X_SHUNT[v])) continue;
                if (sa && s.augmentMode != X_AUGMENT[v]) continue;
                if (rat && s.settlementMode != X_SETTLE[v]) continue;
                if (ie && (s.disableSpecialties != X_DISABLE[v] || s.disableVPC != X_DISABLE[v] || s.disableShipping != X_DISABLE[v])) continue;
                if (ix && s.disableStabilizers != X_DISABLE[v]) continue;
                return v;
            }
            return CUSTOM;
        }
        if (NEW_COLONIES.equals(row)) {
            int v = s.expandMode;
            if (v < 0 || v >= NEW_COLONY_NAMES.length) return CUSTOM;
            return !mod(TASC) || s.stationMode == NC_TASC[v] ? v : CUSTOM;
        }
        if (TAKE_DOWN.equals(row)) {
            for (int v = 0; v < TAKE_DOWN_NAMES.length; v++)
                if (s.noPrune == TD_NO[v] && s.endgameNoReplace == TD_NO[v] && s.capitalNoRoom == TD_NO[v] && s.allowRemovals == TD_REMOVALS[v]) return v;
            return CUSTOM;
        }
        if (AI_CORES.equals(row)) {
            if (!s.useAICores) return 0;
            for (int v = 1; v < AI_CORE_NAMES.length; v++) if (s.coreRisk == CORE_RISK[v] && s.inspectionMode == CORE_INSPECT[v]) return v;
            return CUSTOM;   // e.g. More where defended with Advise: shown as Custom, never read as Defended (which resists)
        }
        if (ADMINS.equals(row)) {
            for (int v = 0; v < ADMIN_NAMES.length; v++) if (s.adminHiring == ADMIN_HIRE[v] && s.adminOptimal == ADMIN_PLACE[v]) return v;
            return CUSTOM;
        }
        if (POLICIES.equals(row)) {
            boolean ie = mod(INDEVO);
            for (int v = 0; v < POLICY_NAMES.length; v++)
                if (s.hazardPayMode == v && s.freePortMode == v && (!ie || s.disableEdicts == (v == 1))) return v;
            return CUSTOM;
        }
        if (STORY.equals(row)) {
            if (s.storyPointRule == 0) return 0;
            if (s.storyPointRule == 1) return 1;
            if (s.storyPointRule == 2) for (int v = 2; v < STORY_NAMES.length; v++) if (s.storyPointMonthlyCap == STORY_CAP[v]) return v;
            return CUSTOM;   // a monthly cap other than 2 or 5 (name() shows "N a month")
        }
        if (STATIONS.equals(row)) {
            if (s.noStationPlan) return STATIONS_ONLY_WHERE_THEY_PAY;
            for (int v = 1; v < CASettings.STATION_IDS.length; v++)
                if (CASettings.STATION_IDS[v].equals(s.stationLine) && stationAvailable(v)) return v;
            return 0;   // Pick by score, also a line whose station the game doesn't have (its mod is off)
        }
        if (WAR.equals(row)) return CAWarPolicy.clamp(s.warLevel);
        return CUSTOM;
    }

    /** The value the row shows: its name, "N a month" for another story point cap, or Custom. */
    public static String name(CASettings s, String row) {
        int v = match(s, row);
        String[] names = values(row);
        if (v >= 0 && names != null && v < names.length) return names[v];
        if (STORY.equals(row) && s != null && s.storyPointRule == 2) return s.storyPointMonthlyCap + " a month";
        return CUSTOM_NAME;
    }

    /** The recommended value's name. */
    public static String recName(String row) {
        String[] names = values(row);
        return names == null ? "" : names[rec(row)];
    }

    /**
     * The value CYCLE moves to: the next one, wrapping; from Custom, the recommended one. Stations skip lines whose station
     * the game doesn't have.
     */
    public static int next(CASettings s, String row) {
        String[] names = values(row);
        if (names == null) return CUSTOM;
        int cur = match(s, row);
        if (cur < 0) return rec(row);
        for (int step = 1; step <= names.length; step++) {
            int v = (cur + step) % names.length;
            if (STATIONS.equals(row) && v > 0 && v < STATIONS_ONLY_WHERE_THEY_PAY && !stationAvailable(v)) continue;
            return v;
        }
        return cur;
    }

    /** Whether a station line's first station exists in the game (Imperium only with Interstellar Imperium). */
    static boolean stationAvailable(int v) {
        if (v <= 0 || v >= CASettings.STATION_IDS.length) return v == 0;
        return CASettings.stationStarter(CASettings.STATION_IDS[v]) != null;
    }

    /** The upkeep share in effect: an older save's 0 reads as 0.2.45's 10% (CASettings.fixNulls does the same). */
    static float upkeepShare(CASettings s) { return s.upkeepShare > 0f ? s.upkeepShare : CATuning.UPKEEP_SHARE; }

    static boolean near(float a, float b) { return Math.abs(a - b) < 0.001f; }

    static boolean mod(String id) {
        try { return com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled(id); } catch (Throwable t) { return false; }
    }
}
