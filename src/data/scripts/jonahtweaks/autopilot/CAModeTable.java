package data.scripts.jonahtweaks.autopilot;

import java.util.*;

/**
 * 0.3.3.0: one default table per mode (plans/2026-11-13-modes-rework.md section 9). Every CASettings field is one kind:
 * - EMPIRE: follows the empire mode (CASettings.empireMode); apply() writes the mode's column into every Empire field the
 *   player has not set. rungMode is tracked per rung ("rungMode[4]").
 * - COLONY: the value Auto colonies use; the colony's own mode gates it (Off and Advisor colonies never run these).
 * - SAME: one value in every mode (applyRecommended sets it at setup and on Reset to recommended).
 * - PICK: a player choice with no default (never written by a mode change or Reset).
 * - STATE: bookkeeping, never a setting.
 * Player-set: CASettings.playerSet holds the names of the fields you changed (a click's before/after snapshot, track());
 * those keep their value through empire mode changes. Reset to recommended clears them. Fields are read and written by
 * name through CARefl (no java.lang.reflect in the mod). JTModeTableCheck checks every field has exactly one kind and that
 * every column keeps the hard rules.
 */
public final class CAModeTable {
    private CAModeTable() { }

    /** Column index of a mode: Off 0, Advisor 1, Approve 2, Auto 3. */
    static int col(CAData.Mode m) { return m == null ? 1 : m.ordinal(); }

    /** Empire fields: name to the value for Off, Advisor, Approve, Auto. */
    static final Map<String, Object[]> EMPIRE = new LinkedHashMap<String, Object[]>();
    /** rungMode per mode (CARich.RUNGS order: Obligations, Stockpiles, Growth, Military, Shipyard, Expansion, Economy, Wonders). */
    static final int[][] RUNGS = {
            {1, 0, 0, 0, 0, 0, 0, 0},
            {1, 0, 0, 0, 0, 0, 0, 0},
            {1, 2, 2, 2, 2, 2, 2, 2},
            {1, 1, 1, 1, 1, 1, 1, 1}};

    static void e(String f, int off, int advisor, int approve, int auto) { EMPIRE.put(f, new Object[]{off, advisor, approve, auto}); }

    static {
        e("expandMode", 0, 0, 1, 2);            // Off, Off, Ask first, Auto
        e("stationMode", 2, 0, 0, 1);           // TASC stations: Off, Recommend only, Recommend only, Auto
        e("surplusLine", 0, 0, 2, 2);           // Off, Off, Over 3,000,000, Over 3,000,000
        e("endgameMode", 3, 3, 0, 0);           // Off, Off, from 1M, from 1M
        e("settlementMode", 3, 0, 1, 2);        // Off, Advisor, Approve, Auto
        e("contractMode", 2, 0, 0, 1);          // Off, Advisor, Advisor, Auto
        e("researchMode", 2, 0, 0, 1);          // Off, Advisor, Advisor, Auto
        e("projectSupplyMode", 2, 1, 1, 0);     // Off, Warn only, Warn only, Auto
        e("objectiveMode", 2, 1, 1, 0);         // Off, Recommend only, Recommend only, Auto
        e("spyMode", 0, 0, 0, 1);               // Off, Off, Off, Defense
        e("autonomyMode", 0, 1, 1, 1);          // Off, Recommend, Recommend, Recommend
        e("warLevel", 0, 0, 1, 2);              // Off, Off, Defend, Retaliate
        e("warMode", 2, 2, 1, 0);               // kept in step with warLevel: off, off, protect, protect and strike back
        e("conqueredMode", 2, 2, 1, 0);         // Advisor, Advisor, Approve, Auto
        e("logisticsMode", 0, 0, 0, 1);         // Off, Off, Off, On
        e("capitalMode", 2, 2, 0, 0);           // Off, Off, Deliveries and commerce, Deliveries and commerce
        e("makeRoom", 0, 0, 1, 2);              // Off, Off, Ask first, Auto
    }

    static final String RUNG = "rungMode";

    static final String[] COLONY = {"useAICores", "coreRisk", "alphaMode", "researchCoresByValue", "adminHiring", "adminOptimal",
            "hazardPayMode", "freePortMode", "noStationPlan", "noPrune", "endgameNoReplace", "capitalNoRoom", "surplusTerraform",
            "noSurplusResearch", "measuredMode", "augmentMode", "hypershuntMode", "disableAmbassadors", "disableVPC", "disableForgeVPC",
            "disableEdicts", "disableShipping", "disableSpecialties", "disableStabilizers", "disableDefenseFleets"};

    static final String[] SAME = {"budgetAuto", "budgetPercentMode", "budgetPercent", "savingsShare", "budgetFixed", "projectCapFixed",
            "perProjectCap", "upkeepCapAuto", "upkeepShare", "newUpkeepCap", "reserve", "terraformShare", "minStability", "pauseOnLoss",
            "feeLevel", "horizonMonths", "richPreset", "shipyardStop", "shipyardFee", "surplusFocusOnly", "noWonders", "stationLine",
            "inspectionMode", "allowRemovals", "storyPointRule", "storyPointMonthlyCap", "ambassadorFocus", "ambassadorStopAt",
            "hazardPayColonyShare", "hazardPayEmpireShare", "settlementMinIncome", "researchItemMode", "spyNoHiring", "spyKeep",
            "noScreenButton", "voice", "screenLog", "showThinking", "logisticsShips", "logisticsSupplies", "partsToCapital", "capitalStart",
            "capitalNoGather", "noMarketModel", "noOverLimitFix", "expandLine", "expandScope", "expandGoal", "expandMethod", "expandMax",
            "noExpandBuildout", "noExpandAdmins", "noWarRetake", "warAutoRivals", "warCoalitionOK", "warScorchedOK", "scorchedTargets",
            "enfOn", "enfInvestigators", "enfInspections", "enfLeague", "enfProtectors", "enfRaiders", "enfPolice", "enfCost",
            "enfCommissionOK", "enfAlliesOK", "enfCeasefireOK", "enfLowProfileOK", "enfNearYouOK", "enfAllColonies", "enfIntercept",
            "productionMode", "floorHullSources", "floorCapacity", "floorOrderMonths", "floorQuality", "floorPartsReserve", "floorSupplies",
            "floorFuel", "floorMoneyGoal", "floorValHull", "floorValCapacity", "floorValQuality", "floorValPart", "floorValOrders",
            "floorValGoods", "needsFirst", "moneyTaper", "taperLow", "taperHigh", "diversityCopies", "diversityShare", "goalWeights",
            "keepProductionGoods", "shipyardFromSurplus", "approvalDays", "buildHints", "hintScoreOnOpen", "legacyPlanner"};

    static final String[] PICK = {"lowProfile", "factionStance", "factionPriority", "capitalId", "focusId", "logisticsHub",
            "logisticsSellHub", "spyTarget"};

    static final String[] STATE = {"setupDone", "monthCredits", "month", "spent", "fees", "upkeepAdded", "terraSpent", "budgetCarry",
            "spMonth", "lastNet", "lastNetKnown", "endgameOn", "endgameRefNet", "endgameLoss", "capitalAuto", "capitalGatherSet",
            "capitalStarted", "capitalModeBeforeNone", "focusAuto", "enfOwed", "log", "settingsVersion", "upkeepCapChecked",
            "surplusChecked", "hazardLimitChecked", "spyChecked", "warLevelChecked", "productionChecked", "expandConfirmed",
            "playerSet", "playerRows", "empireMode", "productionFilled"};

    /** Never flagged by the migration: worked out from credits and income at setup. */
    static final Set<String> COMPUTED = new HashSet<String>(Arrays.asList("reserve", "perProjectCap", "newUpkeepCap"));

    /** The fields a click's snapshot compares: Empire, Colony and Same (rungMode per rung). */
    static List<String> tracked() {
        List<String> out = new ArrayList<String>(EMPIRE.keySet());
        out.add(RUNG);
        out.addAll(Arrays.asList(COLONY));
        out.addAll(Arrays.asList(SAME));
        return out;
    }

    // ---------------- field access by name (CARefl: no java.lang.reflect in the mod) ----------------

    static final Map<String, Object> FIELDS = new HashMap<String, Object>();

    static Object field(String name) throws Exception {
        Object f = FIELDS.get(name);
        if (f == null) { f = CARefl.field(CASettings.class, name); FIELDS.put(name, f); }
        return f;
    }

    static Object get(CASettings s, String name) {
        try {
            Object v = CARefl.get(field(name), s);
            return v instanceof int[] ? ((int[]) v).clone() : v;
        } catch (Throwable t) { return null; }
    }

    static boolean set(CASettings s, String name, Object v) {
        try { CARefl.set(field(name), s, v instanceof int[] ? ((int[]) v).clone() : v); return true; }
        catch (Throwable t) { return false; }
    }

    static boolean same(Object a, Object b) {
        if (a instanceof int[] && b instanceof int[]) return Arrays.equals((int[]) a, (int[]) b);
        if (a instanceof Number && b instanceof Number && !(a instanceof Float) && !(b instanceof Float))
            return ((Number) a).longValue() == ((Number) b).longValue();
        if (a instanceof Float && b instanceof Float) return Math.abs((Float) a - (Float) b) < 1e-4f;
        return Objects.equals(a, b);
    }

    // ---------------- snapshots and player-set flags ----------------

    /** The tracked fields' values, by name. */
    static Map<String, Object> snapshot(CASettings s) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (s == null) return out;
        for (String f : tracked()) out.put(f, get(s, f));
        return out;
    }

    /** Pure: the names that differ between two snapshots; a changed rungMode is named per rung ("rungMode[4]"). */
    static Set<String> diff(Map<String, Object> before, Map<String, Object> after) {
        Set<String> out = new LinkedHashSet<String>();
        for (Map.Entry<String, Object> e : after.entrySet()) {
            Object a = before.get(e.getKey()), b = e.getValue();
            if (RUNG.equals(e.getKey()) && a instanceof int[] && b instanceof int[]) {
                int[] x = (int[]) a, y = (int[]) b;
                for (int i = 0; i < Math.max(x.length, y.length); i++) {
                    int xi = i < x.length ? x[i] : -1, yi = i < y.length ? y[i] : -1;
                    if (xi != yi) out.add(RUNG + "[" + i + "]");
                }
            } else if (!same(a, b)) out.add(e.getKey());
        }
        return out;
    }

    /** Names written by the mod itself during the current click (apply, recommended values): never flagged. */
    static final Set<String> SYSTEM = new HashSet<String>();
    static int depth = 0;
    static Map<String, Object> clickBefore;

    /** A click starts: the tracked fields are read. Nested calls (a page inside a page) count once. */
    public static void clickStart(CASettings s) {
        if (depth++ > 0) return;
        SYSTEM.clear();
        try { clickBefore = snapshot(s); } catch (Throwable t) { clickBefore = null; }
    }

    /** A click ends: every tracked field it changed, less what the mod wrote itself, is player-set (with the row key, if any). */
    public static void clickEnd(CASettings s, Object id) {
        if (depth <= 0) { depth = 0; return; }
        if (--depth > 0) return;
        boolean any = false;
        try {
            if (clickBefore == null || s == null) return;
            Set<String> changed = diff(clickBefore, snapshot(s));
            any = !changed.isEmpty() || CASetupUI.rowKey(id) != null;
            changed.removeAll(SYSTEM);
            if (changed.isEmpty()) return;
            s.playerSet().addAll(changed);
            String key = CASetupUI.rowKey(id);
            if (key != null) s.playerRows().add(key);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "settings change tracking failed", t);
        } finally {
            clickBefore = null; SYSTEM.clear();
            if (any) CALunaSync.sync();   // 0.3.4.0: LunaLib's menu shows what the click changed
        }
    }

    /** Runs a write the mod makes itself (recommended values, a mode column): what it changes is never flagged. */
    public static void system(CASettings s, Runnable r) {
        Map<String, Object> before = depth > 0 ? snapshot(s) : null;
        r.run();
        if (before != null) SYSTEM.addAll(diff(before, snapshot(s)));
    }

    // ---------------- the mode column ----------------

    /**
     * Writes the mode's column into every Empire field the player has not set (rungMode per rung). Returns the names
     * written (rungs as "rungMode[i]").
     */
    public static List<String> apply(final CASettings s, final CAData.Mode mode) {
        final List<String> written = new ArrayList<String>();
        if (s == null) return written;
        system(s, new Runnable() { public void run() { written.addAll(write(s, mode)); } });
        return written;
    }

    static List<String> write(CASettings s, CAData.Mode mode) {
        List<String> out = new ArrayList<String>();
        int c = col(mode);
        Set<String> mine = s.playerSet();
        for (Map.Entry<String, Object[]> e : EMPIRE.entrySet()) {
            String f = e.getKey();
            if (mine.contains(f)) continue;
            Object v = e.getValue()[c];
            if (same(get(s, f), v)) continue;
            if (set(s, f, v)) out.add(f);
        }
        int[] r = s.rungMode == null ? CASettings.defaultRungs() : s.rungMode.clone();
        if (r.length < RUNGS[c].length) { int[] w = CASettings.defaultRungs(); System.arraycopy(r, 0, w, 0, r.length); r = w; }
        boolean any = false;
        for (int i = 0; i < RUNGS[c].length; i++) {
            if (mine.contains(RUNG + "[" + i + "]") || mine.contains(RUNG) || r[i] == RUNGS[c][i]) continue;
            r[i] = RUNGS[c][i];
            out.add(RUNG + "[" + i + "]");
            any = true;
        }
        if (any) s.rungMode = r;
        return out;
    }

    /** The column's value for one Empire field (rungs: "rungMode[i]"), or null when it isn't one. */
    static Object value(String name, CAData.Mode mode) {
        int c = col(mode);
        if (name.startsWith(RUNG + "[")) {
            try { return RUNGS[c][Integer.parseInt(name.substring(RUNG.length() + 1, name.length() - 1))]; } catch (Throwable t) { return null; }
        }
        Object[] v = EMPIRE.get(name);
        return v == null ? null : v[c];
    }

    /** Sets the empire mode (a player choice) and writes its column; returns what changed. */
    public static List<String> setEmpire(CASettings s, CAData.Mode mode) {
        if (s == null || mode == null) return new ArrayList<String>();
        s.empireMode = mode;
        List<String> w = apply(s, mode);
        try { CALog.did("Settings: empire mode " + mode.label + ". " + w.size() + (w.size() == 1 ? " setting follows it." : " settings follow it."), true); }
        catch (Throwable ignored) { }
        return w;
    }

    /** Reset to recommended: the flags are cleared, then the recommended values and the empire mode's column. */
    public static void reset(final CASettings s) {
        if (s == null) return;
        s.playerSet().clear();
        s.playerRows().clear();
        system(s, new Runnable() { public void run() { CASetupUI.applyRecommended(s); } });
        apply(s, s.empire());
    }

    // ---------------- old saves (CAMigrate.to5) ----------------

    /** Pure: the empire mode an old save gets: Advisor before setup; else Auto with any Auto colony, else Approve with any Approve colony, else Advisor. */
    static CAData.Mode deriveEmpire(boolean setupDone, Collection<CAData.Mode> colonyModes) {
        if (!setupDone) return CAData.Mode.ADVISOR;
        boolean approve = false;
        for (CAData.Mode m : colonyModes) {
            if (m == CAData.Mode.AUTO) return CAData.Mode.AUTO;
            if (m == CAData.Mode.APPROVE) approve = true;
        }
        return approve ? CAData.Mode.APPROVE : CAData.Mode.ADVISOR;
    }

    /** Pure apart from field access: the tracked fields that differ from the baseline (rungs per rung), less the computed amounts. */
    static Set<String> changedFrom(CASettings s, CASettings baseline) {
        Set<String> out = diff(snapshot(baseline), snapshot(s));
        out.removeAll(COMPUTED);
        return out;
    }

    /** A short name for a field in the Thinking list. */
    static String label(String f) {
        if (f.startsWith(RUNG + "[")) {
            try { return CARich.RUNGS[Integer.parseInt(f.substring(RUNG.length() + 1, f.length() - 1))] + " rung"; } catch (Throwable t) { return f; }
        }
        String l = LABELS.get(f);
        return l == null ? f : l;
    }

    static final Map<String, String> LABELS = new HashMap<String, String>();
    static {
        String[][] l = {{"expandMode", "New colonies"}, {"stationMode", "TASC stations"}, {"surplusLine", "Surplus investment"},
                {"endgameMode", "Endgame"}, {"settlementMode", "RAT settlement"}, {"contractMode", "Trade contracts"},
                {"researchMode", "Research"}, {"projectSupplyMode", "Special project supply"}, {"objectiveMode", "Stable locations"},
                {"spyMode", "Agents"}, {"autonomyMode", "Autonomy"}, {"warLevel", "War"}, {"warMode", "War fleets"},
                {"conqueredMode", "Conquered colonies"}, {"logisticsMode", "Storage logistics"}, {"capitalMode", "Capital does"},
                {"makeRoom", "Make room"}};
        for (String[] x : l) LABELS.put(x[0], x[1]);
    }

    /** CAReset: the field lookups and a click left open. */
    public static void reset() { FIELDS.clear(); SYSTEM.clear(); depth = 0; clickBefore = null; }
}
