package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 0.3.4.0: the Settings page in LunaLib's settings menu (F3 by default). LunaLib stays optional.
 * - ROWS: one LunaLib field per Settings page row, in the page's order: the main groups on the General tab, each Advanced
 *   subgroup under its own header on the Advanced tab (CASetupUI.ADVANCED order). data/config/LunaSettings.csv is made from
 *   this table (csv(); JTLunaCheck fails when the file differs).
 * - LunaLib values are global. The first save loaded after the update seeds them from its CASettings (SEEDED flag), so that
 *   game doesn't change. After that every load reads LunaLib into CASettings (pull), and a save in LunaLib's menu applies the
 *   fields that changed since the last sync (changed). The "set by you" names (CASettings.playerSet, playerRows) of the
 *   fields in this table are kept in LunaLib too, so they are global with the values.
 * - A field changed in LunaLib counts as set by you. Presets are radio options that write the fields CAPresets writes.
 *   Numbers keep the Settings page's minimum, maximum and step: a changed value is rounded to the step and clamped.
 * - Per-save picks (capital, refit hub, sell hub, offense target) and faction stances stay on the intel Settings page.
 * - After an intel click, before a save and after a LunaLib save, CASettings is written back (push), so LunaLib shows what
 *   the game uses. Changes are found against the values of the last sync, so a value the game changed itself is never
 *   read back as yours.
 * No LunaLib class is referenced here: the values are read and written in LunaLib's own file (saves/common/LunaSettings),
 * and CALunaLink (the one class that references LunaLib) registers the listener and tells LunaLib to read the file again.
 */
public final class CALunaSync {
    private CALunaSync() { }

    public static final String MOD = "jonah_tweaks", LUNA = "lunalib";
    static final String FILE = "LunaSettings/" + MOD + ".json";
    static final String SEEDED = "ca__seeded", SETUP = "ca__setup", PLAYER_SET = "ca__playerSet", PLAYER_ROWS = "ca__playerRows";
    static final String HEADER = "Header", TEXT = "Text", BOOL = "Boolean", RADIO = "Radio", INT = "Int", DOUBLE = "Double";
    static final String GENERAL = "", ADVANCED = "Advanced";
    public static final String CUSTOM = CAPresets.CUSTOM_NAME;
    /** The line the intel Settings page shows while LunaLib holds the settings. */
    static final String WHERE = "Settings: in LunaLib's menu (F3). This save's picks:";

    interface Getter { Object get(CASettings s); }
    /** batch: the row keys changed in this LunaLib save; null when the value is read on load (no rounding, no extra writes). */
    interface Setter { void set(CASettings s, Object v, Set<String> batch); }

    static final class Row {
        final String type, key, name, tab;
        String desc = "";
        String[] opts;
        double min, max, step = 1;
        String[] fields = new String[0];
        Getter get;
        Setter set;
        boolean preset;
        Row(String type, String key, String name, String tab) { this.type = type; this.key = key; this.name = name; this.tab = tab; }
        String id() { return "ca_" + key; }
        boolean value() { return !HEADER.equals(type) && !TEXT.equals(type); }
        int index(Object v) {
            if (!(v instanceof String) || opts == null) return -1;
            for (int i = 0; i < opts.length; i++) if (opts[i].equals(v)) return i;
            return -1;
        }
    }

    static final List<Row> ROWS = new ArrayList<Row>();
    /** The tab rows get while the table is built (the static block); never read after. */
    static String tab = GENERAL;

    // ---------------- the table ----------------

    static {
        tab = GENERAL;
        // 0.3.6.0: story mode, first, under its own header. Each save follows it on load and on a save in this menu (onLoad, changed)
        header("storymode", "Story mode");
        Row st = row(BOOL, "storymode", "Story mode", "On: the Colony Autopilot stays dormant until you find the Autarch AI Core, then runs the "
                + "abilities you buy with the AI cores you give it. Off: every feature works from the start. Each save keeps its story progress "
                + "when you switch, but processing power you spent is not refunded.");
        st.get = s -> CASync.on();
        st.set = (s, v, b) -> { };   // applied before the other rows (story())
        text("presets", "Presets set several fields at once; each field is on the Advanced tab. Mode default sets the group to the empire mode's "
                + "values, which then follow the mode. Custom shows that the fields match no option; picking it changes nothing.");
        header("playstyle", "Playstyle");
        Row r = row(RADIO, "empire", "Empire mode", "Empire-wide settings follow it. Off: nothing. Advisor: advice. Approve: asks first. Auto: acts. "
                + "A setting you change keeps its value through mode changes.");
        final CAData.Mode[] modes = CAData.Mode.values();
        r.opts = new String[modes.length];
        for (int i = 0; i < modes.length; i++) r.opts[i] = modes[i].label;
        r.get = s -> s.empire().label;
        r.set = (s, v, b) -> { int i = idx("empire", v); if (i >= 0) CAModeTable.setEmpire(s, modes[i]); };
        r = row(BOOL, "lowprofile", "Low profile", "Slows colony crises and earns less. No AI cores above size 3, no Pather interest, "
                + "free ports, raids or sabotage. Off: Full expansion.");
        r.fields = new String[]{"lowProfile"};
        r.get = s -> s.lowProfile;
        r.set = (s, v, b) -> CALowProfile.set(s, (Boolean) v, "LunaLib settings");
        preset("p_spend", "Spending", CAPresets.SPENDING, "Thrifty: 30% of net income and 5% of savings. Normal: 50% and 15%. "
                + "Lavish: 80% and 30%. The per-project cap is half, all or twice net income.");
        preset("p_extras", "Extras", CAPresets.EXTRAS, "Mod buildings and features: stable locations, measured mod buildings, trade contracts, "
                + "research, special project supply, megastructures, station augments, RAT settlement, IndEvo automation, Antimatter Stabilizers.");
        preset("p_newcol", "New colonies", CAPresets.NEW_COLONIES, "Founds colonies with idle money. Ask first: each one waits for your approval.");
        preset("p_war", "War", CAPresets.WAR, "How far war fleets go; stances cap it. Defend: relief. Retaliate: raids on attackers, retakes "
                + "of your lost colonies. Hold them down: raids on Rival and up. Conquer: invasions. Scorched: bombardment. Needs Nexerelin.");

        header("money", "Money");
        credits("reserve", "Credit reserve", "reserve", 0, 100000000, 50000, "Credits Autopilot does not spend.");
        r = radio("surplus", "Surplus investment", "surplusLine", CASettings.SURPLUS_NAMES, "Credits over this go to one focus world.");
        final Setter surplus = r.set;
        r.set = (s, v, b) -> { surplus.set(s, v, b); s.surplusChecked = true; };
        radio("richpreset", "What to buy", "richPreset", CASettings.RICH_PRESET_NAMES, "What idle money buys first. Each rung is on the Advanced tab, "
                + "Surplus and money ladder.");
        radio("endgame", "Endgame", "endgameMode", CAEndgame.NAMES, "At this income, build for production and defense.");
        preset("p_story", "Story points", CAPresets.STORY, "Story points Autopilot may spend on improvements.");

        header("building", "Building");
        preset("p_station", "Stations", CAPresets.STATIONS, "Stations from size 4, upgraded with growth. Battlestation from size 6, Star Fortress "
                + "from 8, a size sooner at the capital. Imperium needs Interstellar Imperium.");
        preset("p_takedown", "Take down buildings", CAPresets.TAKE_DOWN, "Never: nothing is taken down. Clean up: industries that stopped paying, "
                + "room for capital trade buildings and endgame changes. Free hand: Clean up and Allow removals.");
        preset("p_policy", "Colony policies", CAPresets.POLICIES, "Auto: hazard pay, free port and edicts where they pay. Don't touch: left as you set them.");

        header("people", "Items and people");
        preset("p_cores", "AI cores", CAPresets.AI_CORES, "AI cores from Autopilot Storage. Safe: never inspected. Defended resists inspections, "
                + "which makes the Hegemony hostile.");
        preset("p_admins", "Administrators", CAPresets.ADMINS, "Place only: assigns the administrators you have. Hire and place: also hires them.");

        header("security", "Security and diplomacy");
        radio("spy", "Agents", "spyMode", CASettings.SPY_NAMES, "Security and relations; offense adds sabotage. Needs Nexerelin.");
        bool("amb", "Ambassadors", "disableAmbassadors", true, "Hires and installs ambassadors, Ally and Friendly factions first. Needs Industrial Evolution.");
        text("picks", "Faction stances and this save's picks (capital, refit hub, sell hub, offense target) are on the intel Settings page.");

        header("interface", "Interface");
        radio("screenlog", "On-screen log", "screenLog", CALog.SCREEN_NAMES, "Short lines on screen. The Log page keeps every entry.");

        header("reset", "Reset");
        r = row(BOOL, "reset", "Reset to recommended", "Tick and save: settings return to the empire mode's defaults. Low profile, faction "
                + "stances and this save's picks are kept. Other changes in the same save are applied after the reset. The box unticks itself.");
        r.get = s -> Boolean.FALSE;
        r.set = (s, v, b) -> { };

        // ---------------- Advanced: one header per CASetupUI.ADVANCED subgroup, same order ----------------
        tab = ADVANCED;
        header("adv_budget", "Budget");
        r = row(RADIO, "type", "Budget type", "Automatic: a share of net income and a share of savings above the reserve. "
                + "Share of net income: the first share only. Fixed amount: Monthly budget.");
        r.opts = new String[]{"Automatic", "Share of net income", "Fixed amount"};
        r.fields = new String[]{"budgetAuto", "budgetPercentMode"};
        r.get = s -> idxName("type", s.budgetAuto ? 0 : s.budgetPercentMode ? 1 : 2);
        r.set = (s, v, b) -> {
            int i = idx("type", v);
            if (i == 0) s.budgetAuto = true;
            else if (i == 1) { s.budgetAuto = false; s.budgetPercentMode = true; }
            else if (i == 2) { s.budgetAuto = false; s.budgetPercentMode = false; }
        };
        pct("budget", "From net income (percent)", "budgetPercent", 10, 100, 10, "Used by Automatic and Share of net income.");
        r = pct("savings", "From savings (percent)", "savingsShare", 5, 50, 5, "Used by Automatic: this share of the credits above the reserve each month.");
        r.get = s -> Math.round((s.savingsShare > 0f ? s.savingsShare : 0.15f) * 100f);
        credits("budgetfixed", "Monthly budget", "budgetFixed", 0, 100000000, 250000, "Used by Fixed amount.");
        r = row(RADIO, "captype", "Per-project cap", "Automatic: half, all or twice net income, by Spending. Fixed: Fixed cap.");
        r.opts = new String[]{"Automatic", "Fixed"};
        r.fields = new String[]{"projectCapFixed", "perProjectCap"};
        r.get = s -> idxName("captype", s.projectCapFixed ? 1 : 0);
        r.set = (s, v, b) -> {
            int i = idx("captype", v);
            if (i < 0) return;
            if (b != null && i == 1 && !s.projectCapFixed) s.perProjectCap = CAPresets.projectCap(s);   // Fixed starts from the cap in effect
            s.projectCapFixed = i == 1;
        };
        r = credits("cap", "Fixed cap", "perProjectCap", 250000, 100000000, 500000, "Most one project may cost. A changed value sets the cap to Fixed.");
        final Setter cap = r.set;
        r.set = (s, v, b) -> { cap.set(s, v, b); if (b != null && !b.contains("captype")) s.projectCapFixed = true; };
        r = row(RADIO, "upkeeptype", "New upkeep per month", "Automatic: a share of net income set by Spending, 10% on Normal. Fixed: Fixed upkeep.");
        r.opts = new String[]{"Automatic", "Fixed"};
        r.fields = new String[]{"upkeepCapAuto", "newUpkeepCap"};
        r.get = s -> idxName("upkeeptype", s.upkeepCapAuto ? 0 : 1);
        r.set = (s, v, b) -> {
            int i = idx("upkeeptype", v);
            if (i < 0) return;
            if (b != null && i == 1 && s.upkeepCapAuto) s.newUpkeepCap = CASetupUI.round(CABudget.upkeepCap(s), 10000f);   // Fixed starts from today's amount
            s.upkeepCapAuto = i == 0;
            s.upkeepCapChecked = true;
        };
        credits("upkeep", "Fixed upkeep", "newUpkeepCap", 0, 10000000, 10000, "Most new upkeep a month with Fixed.");
        bool("pause", "Pause on loss", "pauseOnLoss", false, "No spending while net income is below 0.");
        radio("fees", "Convenience fees", "feeLevel", CASettings.FEE_NAMES, "On remote building, shipping and orders.");

        header("adv_rich", "Surplus and money ladder");
        r = row(RADIO, "surplusrich", "Surplus over " + num(CASurplus.RICH), "Past this much over the surplus line: every Approve and Auto colony "
                + "spends from the surplus, or the focus world only.");
        r.opts = new String[]{"Every colony", "Focus world only"};
        flag(r, "surplusFocusOnly");
        bool("wonders", "Wonders and gates", "noWonders", true, "The Daedalus Array and Bifrost gates once their goods are made. Needs Vaults of Knowledge.");
        radio("surplustf", "Surplus terraforming", "surplusTerraform", CASettings.SURPLUS_TF_NAMES, "What the focus world leaves goes into "
                + "terraforming. Needs Terraforming Made Easy.");
        bool("surplusres", "Surplus research", "noSurplusResearch", true, "More Research Facilities from the surplus while research is worth "
                + "the speed. Needs Vaults of Knowledge.");
        for (int k = CARich.STOCKPILES; k < CARich.RUNGS.length; k++) {
            final int rung = k;
            r = row(RADIO, "rung_" + k, CARich.RUNGS[k] + " rung", "Off: its share goes to the next rung.");
            r.opts = labels(CASettings.RUNG_MODE_NAMES);
            r.fields = new String[]{CAModeTable.RUNG + "[" + k + "]"};
            r.get = s -> idxName("rung_" + rung, s.rungMode(rung));
            r.set = (s, v, b) -> {
                int i = idx("rung_" + rung, v);
                if (i < 0) return;
                if (s.rungMode == null || s.rungMode.length < CARich.RUNGS.length) s.fixNulls();
                s.rungMode[rung] = i;
            };
        }
        integer("shipyardstop", "Shipyard stops at", "shipyardStop", 1, 30, 1, "Unclaimed hulls the shipyard ordered, in the capital's Storage.");

        header("adv_build", "Building");
        bool("overlimit", "Over-limit fix", "noOverLimitFix", true, "A colony over its industry limit takes down its weakest industry.");
        bool("marketmodel", "Exports valued by market share", "noMarketModel", true, "Exports are valued by your share of each sector market.");
        bool("prune", "Take down unpaying industries", "noPrune", true, "An industry that stopped paying for 30 days is taken down.");
        bool("endgamerep", "Endgame replaces industries", "endgameNoReplace", true, "Weak industries make way for production chains, fleets and defense.");
        bool("removals", "Allow removals", "allowRemovals", false, "Autopilot may remove buildings for better ones.");
        integer("spcap", "Story points a month", "storyPointMonthlyCap", 0, 50, 1, "Used when Story points is 2 a month, 5 a month or Custom.");

        header("adv_cores", "AI cores and items");
        bool("cores", "Use AI cores", "useAICores", false, "AI cores from Autopilot Storage go into colony buildings.");
        radio("alpha", "Alpha cores", "alphaMode", CASettings.ALPHA_NAMES, "Where Alpha cores may go.");
        radio("corerisk", "Inspection risk", "coreRisk", CASettings.CORE_RISK_NAMES, "How many AI cores colonies hold against Hegemony inspections.");
        radio("inspect", "Hegemony inspections", "inspectionMode", CASettings.INSPECTION_NAMES, "Resist where defended makes the Hegemony hostile.");
        r = row(RADIO, "researchcores", "Alphas in research buildings", "Alpha cores halve research buildings' costs. Needs Vaults of Knowledge.");
        r.opts = new String[]{"First", "By value"};
        flag(r, "researchCoresByValue");
        radio("resitems", "Research uses colony items", "researchItemMode", CASettings.RESEARCH_ITEM_NAMES, "Only while credits are over the "
                + "surplus line. Needs Vaults of Knowledge.");

        header("adv_policy", "Colony policies");
        radio("hazard", "Hazard pay", "hazardPayMode", CASettings.POLICY_NAMES, "Auto: hazard pay where it speeds growth up enough.");
        pct("hpcol", "Hazard pay growth threshold (percent)", "hazardPayColonyShare", 5, 100, 5, "Hazard pay where it speeds growth up by at least this much.");
        pct("hpemp", "Hazard pay budget (percent of net income)", "hazardPayEmpireShare", 1, 100, 1, "All hazard pay stays within this share of net income.");
        radio("freeport", "Free port", "freePortMode", CASettings.POLICY_NAMES, "Auto: a free port where it pays.");
        bool("edicts", "Senate edicts", "disableEdicts", true, "Needs Industrial Evolution.");

        header("adv_people", "People and diplomacy");
        bool("admin", "Administrator hiring", "adminHiring", false, "Hires administrators for colonies without one.");
        bool("adminopt", "Assign administrators optimally", "adminOptimal", false, "Places each administrator where their skills pay most.");
        radio("ambfocus", "Among equal stances, first", "ambassadorFocus", CASettings.FOCUS_NAMES, "Which factions ambassadors and agents work on "
                + "first. Needs Industrial Evolution or Nexerelin.");
        radio("ambstop", "Stop raising relations at", "ambassadorStopAt", CASettings.STOP_NAMES, "Needs Industrial Evolution or Nexerelin.");
        bool("spyhire", "Recruit agents", "spyNoHiring", true, "Needs Nexerelin.");
        radio("spykeep", "Agents to keep", "spyKeep", new String[]{"Auto", "1", "2", "3", "4"}, "Needs Nexerelin.");
        radio("autonomy", "Autonomy", "autonomyMode", CASettings.AUTONOMY_NAMES, "Recommend: names the colony that would help in a money emergency. "
                + "Auto also only recommends. Needs Nexerelin.");

        header("adv_war", "War");
        bool("defense", "Emergency defense fleets", "disableDefenseFleets", true, "Needs Nexerelin.");
        bool("warretake", "Retake your lost colonies", "noWarRetake", true, "At Retaliate and up. Needs Nexerelin.");
        bool("warrivals", "Top hostile threat as Rival", "warAutoRivals", false, "Needs Nexerelin.");
        radio("warconq", "Conquered colonies start in", "conqueredMode", CASettings.CONQUERED_NAMES, "Needs Nexerelin.");
        radio("warscorch", "Bombardment targets", "scorchedTargets", CASettings.SCORCHED_TARGET_NAMES, "At Scorched. Needs Nexerelin.");
        bool("warcoal", "Coalition risk allowed", "warCoalitionOK", false, "Off: offensives stop before a coalition forms. Needs Nexerelin.");
        bool("enf", "Enforcer patrols", "enfOn", false, "Your colony patrols attack the fleet types set to On. Charge: once per fleet your forces "
                + "defeat. A battle you fight yourself gets the game's penalty.");
        String[] kindFields = {"enfInvestigators", "enfInspections", "enfLeague", "enfProtectors", "enfRaiders", "enfPolice"};
        for (int k = 0; k < CASettings.ENF_KIND_NAMES.length; k++)
            bool("enfk_" + k, CASettings.ENF_KIND_NAMES[k], kindFields[k], false, "Used with Enforcer patrols." + (k == CAEnforcers.POLICE ? " Needs Nexerelin." : ""));
        radio("enfcost", "Enforcer cost", "enfCost", CASettings.ENF_COST_NAMES, "Reputation: the game's penalty for defeating that fleet yourself, -3. "
                + "Commerce raiders and police: none. Credits: fleet points times Nexerelin's fleet request price (400 without it), paid above the credit reserve.");
        r = row(RADIO, "enfwhere", "Enforcer patrols in", "Systems where colony patrols attack.");
        r.opts = labels(CASettings.ENF_WHERE_NAMES);
        flag(r, "enfAllColonies");
        bool("enfcomm", "Skip your commissioner", "enfCommissionOK", true, "No attacks on the faction you hold a commission with.");
        bool("enfally", "Skip allies and League membership", "enfAlliesOK", true, "No attacks on allies, or on the League while you are a member.");
        bool("enfcease", "Skip ceasefires", "enfCeasefireOK", true, "Needs Nexerelin.");
        bool("enflow", "Off in Low profile", "enfLowProfileOK", true, "No attacks while Low profile is on.");
        bool("enfnear", "Off in your fleet's system", "enfNearYouOK", true, "Your system: a marked fleet is hostile to your fleet too.");
        bool("enfint", "Patrols intercept", "enfIntercept", false, "Colony patrols in the system get an order to intercept the fleet.");

        header("adv_capital", "Capital and storage");
        r = row(RADIO, "capmode", "Capital does", "Deliveries and Storage gather at the capital. Off: no capital. The capital pick is on the intel "
                + "Settings page.");
        r.opts = labels(CACapital.MODE_NAMES);
        r.fields = new String[]{"capitalMode"};
        r.get = s -> idxName("capmode", clamp(s.capitalMode, 3));
        r.set = (s, v, b) -> {
            int i = idx("capmode", v);
            if (i < 0 || i == s.capitalMode) return;
            if (i == 2) { s.capitalModeBeforeNone = s.capitalMode; s.capitalId = null; }   // as the intel Capital row's None
            s.capitalMode = i;
        };
        radio("capstart", "Trade hub starts at", "capitalStart", CACapital.START_NAMES, "When the capital starts its trade buildings.");
        bool("caproom", "Make room for trade buildings", "capitalNoRoom", true, "The capital takes down an industry for its trade buildings.");
        bool("capgather", "Gather Storage at the capital", "capitalNoGather", true, "Autopilot Storage gathers at the capital.");
        radio("logi", "Storage logistics", "logisticsMode", new String[]{"Off", "On"}, "Moves goods to the refit hub weekly. The hubs are on the "
                + "intel Settings page.");
        bool("logiships", "Ships to the refit hub", "logisticsShips", false, "Used with Storage logistics.");
        bool("logigoods", "Supplies, fuel, crew, marines too", "logisticsSupplies", false, "Used with Storage logistics.");

        header("adv_extras", "Extras");
        radio("objectives", "Stable locations", "objectiveMode", CASettings.OBJECTIVE_NAMES, "Comm relays, nav buoys and sensor arrays at stable locations.");
        radio("measured", "Measured mod buildings", "measuredMode", CASettings.MEASURED_NAMES, "Mod buildings scored from probe-colony measurements.");
        radio("contract", "Trade contracts", "contractMode", CASettings.CONTRACT_NAMES, "Needs Theory of Toolbox.");
        radio("research", "Research", "researchMode", CASettings.CONTRACT_NAMES, "Needs Vaults of Knowledge.");
        radio("projsupply", "Special project supply", "projectSupplyMode", CASettings.PROJECT_SUPPLY_NAMES, "A producer for goods a special project "
                + "takes that no colony makes. Needs Vaults of Knowledge.");
        radio("hs", "Megastructures", "hypershuntMode", CASettings.AUGMENT_NAMES, "Coronal Hypershunt range and restoration. Needs Vaults of Knowledge.");
        radio("aug", "Station augments", "augmentMode", CASettings.AUGMENT_NAMES, "Needs Station Augments.");
        radio("settle", "RAT settlement", "settlementMode", CASettings.SETTLE_NAMES, "Needs Random Assortment of Things.");
        credits("settleinc", "Found when a site earns", "settlementMinIncome", 5000, 10000000, 5000, "Needs Random Assortment of Things.");
        bool("spec", "IndEvo specialties", "disableSpecialties", true, "Needs Industrial Evolution.");
        bool("vpc", "VPC chips", "disableVPC", true, "Needs Industrial Evolution.");
        bool("forge", "Forge may use up chips", "disableForgeVPC", true, "Needs Industrial Evolution.");
        bool("ship", "Courier shipping", "disableShipping", true, "Needs Industrial Evolution.");
        bool("stab", "Antimatter Stabilizers", "disableStabilizers", true, "Needs Emergent Threats IX Revival.");

        header("adv_expand", "New colonies");
        radio("expline", "Money for new colonies", "expandLine", CASettings.EXPAND_LINE_NAMES, "Used with New colonies on Ask first or Auto.");
        radio("expscope", "Where", "expandScope", CASettings.EXPAND_SCOPE_NAMES, "Low profile: systems you hold only.");
        CAData.Profile[] goals = CAData.Profile.values();
        String[] goalNames = new String[goals.length + 1];
        goalNames[0] = "Fits its plan";
        for (int i = 0; i < goals.length; i++) goalNames[i + 1] = goals[i].label;
        radio("expgoal", "Goal of a new colony", "expandGoal", goalNames, "The goal a new colony starts with.");
        radio("expmethod", "Method", "expandMethod", CASettings.EXPAND_METHOD_NAMES, "Needs Nexerelin.");
        radio("expmax", "Most colonies", "expandMax", CASettings.EXPAND_MAX_NAMES, "New colonies stop at this count.");
        bool("expbuildout", "Fast buildout", "noExpandBuildout", true, "A new colony is built up at once.");
        bool("expadmins", "Administrator past the limit", "noExpandAdmins", true, "An administrator is hired for a new colony past the limit.");
        radio("tascst", "TASC stations", "stationMode", CASettings.TASC_STATION_NAMES, "With New colonies on Auto, Auto stations only recommend "
                + "while the Expansion rung asks first. Needs Terraforming and Station Construction.");

        header("adv_prod", "Production");
        preset("p_prod", "Production", CAPresets.PRODUCTION, "Off: the 0.3.1.0 picks. Light and Full set the floors below.");
        integer("fhulls", "Hull sources", "floorHullSources", 0, 3, 1, "Ship producers wanted.");
        credits("fcap", "Hull capacity a month", "floorCapacity", 0, 1000000, 50000, "Ship production capacity wanted, in credits a month.");
        integer("fmonths", "Order goods within (months)", "floorOrderMonths", 3, 24, 1, "Months for Ashes of the Domain order goods.");
        dbl("fqual", "Production quality", "floorQuality", 0.5, 1.2, 0.1, "Ship production quality wanted.");
        integer("fparts", "Parts reserve", "floorPartsReserve", 0, 1000, 50, "Domain-grade Parts kept on top of the fleet's repair need.");
        radio("fsup", "Supplies floor", "floorSupplies", CASettings.FLOOR_GOODS_NAMES, "Supplies production wanted.");
        radio("ffuel", "Fuel floor", "floorFuel", CASettings.FLOOR_GOODS_NAMES, "Fuel production wanted.");
        bool("fmoney", "Floors at Money colonies", "floorMoneyGoal", false, "Floors also apply at colonies with the Money goal.");
        bool("fneeds", "Floor builds first", "needsFirst", false, "Floor builds rank ahead of other builds.");
        radio("froom", "Make room", "makeRoom", CASettings.MAKE_ROOM_NAMES, "A floor producer may replace the weakest income industry.");
        radio("ftaper", "Money taper", "moneyTaper", CASettings.TAPER_NAMES, "Money counts for less in the planner at high income.");
        r = credits("ftlow", "Taper from", "taperLow", 100000, 9900000, 100000, "Used by Gentle. Stays 100,000 below Taper top from.");
        final Setter low = r.set;
        r.set = (s, v, b) -> { low.set(s, v, b); if (b != null && !b.contains("fthigh")) s.taperLow = Math.min(s.taperLow, s.taperHigh - 100000f); };
        r = credits("fthigh", "Taper top from", "taperHigh", 200000, 10000000, 100000, "Used by Gentle. Stays 100,000 above Taper from.");
        final Setter high = r.set;
        r.set = (s, v, b) -> { high.set(s, v, b); if (b != null) s.taperHigh = Math.max(s.taperHigh, s.taperLow + 100000f); };
        integer("fdivn", "Same building copies", "diversityCopies", 0, 6, 1, "Copies of a building family before the next counts less. 0: off.");
        pct("fdivd", "Next copy counts (percent)", "diversityShare", 50, 100, 5, "What each copy past that count is worth.");
        radio("fweights", "Goal weights", "goalWeights", CASettings.GOAL_WEIGHT_NAMES, "Which table weighs each colony goal.");
        bool("fkeep", "Contracts keep floor goods", "keepProductionGoods", false, "Trade contracts leave floor goods alone while their floor is open.");
        bool("fpartscap", "Parts to the capital", "partsToCapital", false, "Storage logistics brings Domain-grade Parts to the capital's Storage weekly.");
        bool("fshipyard", "Shipyard from Surplus", "shipyardFromSurplus", false, "The Shipyard rung opens at Surplus while floors are on.");
        credits("fvhull", "Value: hull source", "floorValHull", 0, 10000000, 10000, "Worth of one hull source a month in the planner.");
        dbl("fvcap", "Value: capacity credit", "floorValCapacity", 0, 100, 0.1, "Worth of one credit of capacity a month.");
        credits("fvqual", "Value: 1.0 quality", "floorValQuality", 0, 10000000, 10000, "Worth of 1.0 production quality a month.");
        credits("fvpart", "Value: a part", "floorValPart", 0, 100000, 50, "Worth of one Domain-grade Part.");
        dbl("fvorder", "Value: order goods (times)", "floorValOrders", 0, 100, 0.5, "Multiplier on order goods' worth.");
        dbl("fvgoods", "Value: goods (times)", "floorValGoods", 0, 100, 0.5, "Multiplier on floor goods' worth.");

        header("adv_ui", "Interface");
        radio("voice", "Announcement voice", "voice", CASettings.VOICE_NAMES, "Machine or plain wording in announcements.");
        bool("screenbtn", "Button on the colony screen", "noScreenButton", true, "The Colony Autopilot button on your colony's screen.");
        r = row(RADIO, "thinking", "Thinking list", "The Log page's list of what Autopilot weighed.");
        r.opts = new String[]{"Hide", "Show"};
        flag(r, "showThinking");
        r = row(RADIO, "approvaldays", "Approval summary", "One dialog with every decision waiting for approval.");
        r.opts = new String[CAApprovalDialog.DAYS.length];
        for (int i = 0; i < CAApprovalDialog.DAYS.length; i++) r.opts[i] = CAApprovalDialog.daysName(CAApprovalDialog.DAYS[i]);
        r.fields = new String[]{"approvalDays"};
        r.get = s -> CAApprovalDialog.daysName(s.approvalDays);
        r.set = (s, v, b) -> { int i = idx("approvaldays", v); if (i >= 0) s.approvalDays = CAApprovalDialog.DAYS[i]; };
        radio("hints", "Build menu hints", "buildHints", CASettings.HINT_NAMES, "Autopilot's ranking beside the game's build menu.");
        bool("hintscore", "Score on open", "hintScoreOnOpen", false, "Scores an out-of-date colony once a day when its build menu opens.");
    }

    // ---------------- table helpers ----------------

    static Row row(String type, String key, String name, String desc) {
        Row r = new Row(type, key, name, tab);
        r.desc = desc == null ? "" : desc;
        ROWS.add(r);
        return r;
    }

    static void header(String key, String title) { row(HEADER, "h_" + key, title, null); }

    static void text(String key, String line) { row(TEXT, "t_" + key, "", line); }

    static Row find(String key) {
        for (Row r : ROWS) if (r.key.equals(key)) return r;
        return null;
    }

    static int idx(String key, Object v) {
        Row r = find(key);
        return r == null ? -1 : r.index(v);
    }

    static String idxName(String key, int i) {
        Row r = find(key);
        return r.opts[clamp(i, r.opts.length)];
    }

    static int clamp(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }

    /** Option labels LunaLib can hold: its radio options are split on commas (1,000,000 becomes 1M, 100,000 becomes 100k). */
    static String[] labels(String[] names) {
        String[] out = new String[names.length];
        for (int i = 0; i < names.length; i++) out[i] = names[i].replace(",000,000", "M").replace(",000", "k").replace(",", "");
        return out;
    }

    static String num(double v) { return String.format(Locale.ROOT, "%,d", Math.round(v)); }

    /** 50,000 or 0.5. */
    static String plain(double v) { return v == Math.rint(v) ? num(v) : String.valueOf(v); }

    /** A boolean field, shown On when the field is true (or false, for an inverted noX field). */
    static Row bool(String key, String name, final String field, final boolean inverted, String desc) {
        Row r = row(BOOL, key, name, desc);
        r.fields = new String[]{field};
        r.get = s -> { Object v = CAModeTable.get(s, field); return v instanceof Boolean ? (Boolean) v != inverted : null; };
        r.set = (s, v, b) -> CAModeTable.set(s, field, (Boolean) v != inverted);
        return r;
    }

    /** A boolean field as two options: index 0 is false. */
    static void flag(Row r, final String field) {
        final String key = r.key;
        r.fields = new String[]{field};
        r.get = s -> idxName(key, Boolean.TRUE.equals(CAModeTable.get(s, field)) ? 1 : 0);
        r.set = (s, v, b) -> { int i = idx(key, v); if (i >= 0) CAModeTable.set(s, field, i == 1); };
    }

    /** An int field as an index into names. */
    static Row radio(final String key, String name, final String field, String[] names, String desc) {
        Row r = row(RADIO, key, name, desc);
        r.opts = labels(names);
        r.fields = new String[]{field};
        r.get = s -> { Object v = CAModeTable.get(s, field); return idxName(key, v instanceof Integer ? (Integer) v : 0); };
        r.set = (s, v, b) -> { int i = idx(key, v); if (i >= 0) CAModeTable.set(s, field, i); };
        return r;
    }

    /** A preset row (CAPresets): its values, then Mode default and Custom, which show the state and write nothing. */
    static void preset(String key, String name, final String presetRow, String desc) {
        Row r = row(RADIO, key, name, desc);
        String[] vals = CAPresets.values(presetRow);
        r.opts = new String[vals.length + 2];
        System.arraycopy(labels(vals), 0, r.opts, 0, vals.length);
        r.opts[vals.length] = CAPresets.MODE_DEFAULT;
        r.opts[vals.length + 1] = CUSTOM;
        r.preset = true;
        r.fields = CAPresets.fields(presetRow);
        final Row self = r;
        r.get = s -> {
            int m = CAPresets.match(s, presetRow);
            if (m >= 0 && m < vals.length) return self.opts[m];
            return CAPresets.MODE_DEFAULT.equals(CAPresets.name(s, presetRow)) ? CAPresets.MODE_DEFAULT : CUSTOM;
        };
        r.set = (s, v, b) -> { int i = self.index(v); if (i >= 0 && i < vals.length) CAPresets.apply(s, presetRow, i); };
    }

    static Row number(String type, String key, String name, String desc, double min, double max, double step) {
        Row r = row(type, key, name, desc + " From " + plain(min) + " to " + plain(max) + (step != 1 ? ", steps of " + plain(step) : "") + ".");
        r.min = min; r.max = max; r.step = step;
        return r;
    }

    /** An int field. */
    static Row integer(String key, String name, final String field, int min, int max, int step, String desc) {
        final Row r = number(INT, key, name, desc, min, max, step);
        r.fields = new String[]{field};
        r.get = s -> CAModeTable.get(s, field);
        r.set = (s, v, b) -> CAModeTable.set(s, field, (int) Math.round(fit(r, ((Number) v).doubleValue(), b)));
        return r;
    }

    /** A float field of credits, shown as a whole number. */
    static Row credits(String key, String name, final String field, int min, int max, int step, String desc) {
        final Row r = number(INT, key, name, desc, min, max, step);
        r.fields = new String[]{field};
        r.get = s -> { Object v = CAModeTable.get(s, field); return v instanceof Float ? Math.round((Float) v) : null; };
        r.set = (s, v, b) -> CAModeTable.set(s, field, (float) fit(r, ((Number) v).doubleValue(), b));
        return r;
    }

    /** A float share (0 to 1), shown as a whole percent. */
    static Row pct(String key, String name, final String field, int min, int max, int step, String desc) {
        final Row r = number(INT, key, name, desc, min, max, step);
        r.fields = new String[]{field};
        r.get = s -> { Object v = CAModeTable.get(s, field); return v instanceof Float ? Math.round((Float) v * 100f) : null; };
        r.set = (s, v, b) -> CAModeTable.set(s, field, (float) (fit(r, ((Number) v).doubleValue(), b) / 100.0));
        return r;
    }

    /** A float field shown with decimals. */
    static Row dbl(String key, String name, final String field, double min, double max, double step, String desc) {
        final Row r = number(DOUBLE, key, name, desc, min, max, step);
        r.fields = new String[]{field};
        r.get = s -> { Object v = CAModeTable.get(s, field); return v instanceof Float ? Math.round((Float) v * 10000.0) / 10000.0 : null; };
        r.set = (s, v, b) -> CAModeTable.set(s, field, (float) fit(r, ((Number) v).doubleValue(), b));
        return r;
    }

    /** A value typed in LunaLib: rounded to the row's step and kept within its range. A value read on load is kept as it is. */
    static double fit(Row r, double v, Set<String> batch) {
        if (batch == null || Double.isNaN(v)) return v;
        double x = Math.round(v / r.step) * r.step;
        x = Math.round(x * 10000.0) / 10000.0;
        return Math.max(r.min, Math.min(r.max, x));
    }

    // ---------------- values ----------------

    /** Every field name the table writes (rungs as "rungMode[i]"). */
    static Set<String> covered() {
        Set<String> out = new HashSet<String>();
        for (Row r : ROWS) out.addAll(Arrays.asList(r.fields));
        return out;
    }

    static Set<String> rowKeys() {
        Set<String> out = new HashSet<String>();
        for (Row r : ROWS) if (r.value()) out.add(r.key);
        return out;
    }

    /** The intel row key a LunaLib row marks as set by you (the Monthly budget is the intel Budget row's Fixed amount). */
    static String intelKey(Row r) { return "budgetfixed".equals(r.key) ? "budget" : r.key; }

    /** A row's value from the JSON, in its LunaLib type, or null. */
    static Object read(JSONObject j, Row r) {
        if (j == null || !j.has(r.id())) return null;
        try {
            if (BOOL.equals(r.type)) return j.getBoolean(r.id());
            if (INT.equals(r.type)) return (int) Math.round(j.getDouble(r.id()));
            if (DOUBLE.equals(r.type)) return j.getDouble(r.id());
            if (RADIO.equals(r.type)) return j.getString(r.id());
        } catch (Exception e) { return null; }
        return null;
    }

    static boolean same(Row r, Object a, Object b) {
        if (a == null || b == null) return a == b;
        if (DOUBLE.equals(r.type) && a instanceof Number && b instanceof Number) return Math.abs(((Number) a).doubleValue() - ((Number) b).doubleValue()) < 1e-6;
        if (INT.equals(r.type) && a instanceof Number && b instanceof Number) return ((Number) a).longValue() == ((Number) b).longValue();
        return a.equals(b);
    }

    static Object value(Row r, CASettings s) {
        try { return r.get.get(s); } catch (Throwable t) { return null; }
    }

    /** The table's values from CASettings, by LunaLib id. */
    static Map<String, Object> values(CASettings s) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Row r : ROWS) if (r.value()) out.put(r.id(), value(r, s));
        return out;
    }

    static String join(Set<String> names) {
        StringBuilder b = new StringBuilder();
        for (String n : new TreeSet<String>(names)) b.append(b.length() > 0 ? "," : "").append(n);
        return b.toString();
    }

    static Set<String> split(String v) {
        Set<String> out = new LinkedHashSet<String>();
        if (v == null) return out;
        for (String p : v.split(",")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    // ---------------- seed, pull, push, changed (pure apart from side effects; JTLunaCheck runs them on a JSONObject) ----------------

    /** Writes CASettings into the JSON: every row, the reset box unticked, the set-by-you names, the flags. Returns what was written. */
    static Map<String, Object> push(CASettings s, JSONObject j) throws Exception {
        // 0.3.6.0: LunaLib is global, so it gets the values you set, never a story-mode hold (CASync.release); the holds go back on
        CASync.release(s);
        Map<String, Object> vals;
        try { vals = values(s); } finally { CASync.enforce(s); }
        for (Map.Entry<String, Object> e : vals.entrySet()) if (e.getValue() != null) j.put(e.getKey(), e.getValue());
        Set<String> mine = new HashSet<String>(s.playerSet());
        mine.retainAll(covered());
        Set<String> rows = new HashSet<String>(s.playerRows());
        rows.retainAll(rowKeys());
        j.put(PLAYER_SET, join(mine));
        j.put(PLAYER_ROWS, join(rows));
        j.put(SETUP, j.optBoolean(SETUP, false) || s.setupDone);
        j.put(SEEDED, true);
        return vals;
    }

    /**
     * Reads the JSON into CASettings on load: the set-by-you names, the empire mode (its column goes into the fields LunaLib
     * doesn't hold), then each row whose value differs. Presets are skipped: their fields carry the values. Returns the row
     * keys that changed.
     */
    static List<String> pull(CASettings s, JSONObject j) {
        List<String> changed = new ArrayList<String>();
        Set<String> cov = covered(), keys = rowKeys();
        s.playerSet().removeAll(cov);
        for (String f : split(j.optString(PLAYER_SET, ""))) if (cov.contains(f)) s.playerSet().add(f);
        s.playerRows().removeAll(keys);
        for (String k : split(j.optString(PLAYER_ROWS, ""))) if (keys.contains(k)) s.playerRows().add(k);
        Row empire = find("empire");
        int m = empire.index(read(j, empire));
        if (m >= 0 && CAData.Mode.values()[m] != s.empire()) {
            s.empireMode = CAData.Mode.values()[m];
            CAModeTable.apply(s, s.empireMode);
            changed.add("empire");
        }
        for (Row r : ROWS) {
            if (!r.value() || r.preset || "empire".equals(r.key) || "reset".equals(r.key) || "storymode".equals(r.key)) continue;
            Object v = read(j, r);
            if (v == null || same(r, v, value(r, s))) continue;
            try { r.set.set(s, v, null); changed.add(r.key); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "LunaLib setting " + r.id() + " not read", t); }
        }
        for (String k : changed) sideEffect(k, s);
        return changed;
    }

    /**
     * A save in LunaLib's menu: the rows whose value differs from the last sync are applied in this order: Reset to recommended,
     * the empire mode (its column), presets, then each field. Every field they change is set by you, with its row. Returns
     * the row keys applied.
     */
    static List<String> applyChanges(CASettings s, JSONObject j, Map<String, Object> last) {
        List<String> applied = new ArrayList<String>();
        if (last == null) return applied;
        Set<String> batch = new LinkedHashSet<String>();
        for (Row r : ROWS) {
            if (!r.value()) continue;
            Object v = read(j, r);
            if (v != null && !same(r, v, last.get(r.id()))) batch.add(r.key);
        }
        if (batch.isEmpty()) return applied;
        if (batch.contains("reset") && Boolean.TRUE.equals(read(j, find("reset")))) {
            CASetupUI.resetToRecommended(s);
            applied.add("reset");
            try { CALog.did("Settings: reset to recommended (LunaLib).", true); } catch (Throwable ignored) { }
        }
        if (batch.contains("empire")) {
            Row e = find("empire");
            e.set.set(s, read(j, e), batch);
            applied.add("empire");
        }
        Map<String, Object> before = CAModeTable.snapshot(s);
        List<String> mine = new ArrayList<String>();
        List<Row> followMode = new ArrayList<Row>();   // 0.3.6.3: preset rows set to Mode default
        for (int pass = 0; pass < 2; pass++) {
            for (Row r : ROWS) {
                if (!batch.contains(r.key) || r.preset != (pass == 0) || "empire".equals(r.key) || "reset".equals(r.key) || "storymode".equals(r.key)) continue;
                try {
                    Object v = read(j, r);
                    if (r.preset && CAPresets.MODE_DEFAULT.equals(v)) { CAModeTable.resetGroup(s, r.fields); followMode.add(r); continue; }
                    r.set.set(s, v, batch); mine.add(r.key);
                }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "LunaLib setting " + r.id() + " not applied", t); }
            }
        }
        Set<String> fields = CAModeTable.diff(before, CAModeTable.snapshot(s));
        s.playerSet().addAll(fields);
        for (Row r : followMode) {   // their fields follow the empire mode, never "set by you"
            s.playerSet().removeAll(Arrays.asList(r.fields));
            s.playerRows().remove(intelKey(r));
            applied.add(r.key);
            try { CALog.did("Settings: " + r.name + " follows the empire mode (" + s.empire().label + ").", true); } catch (Throwable ignored) { }
        }
        for (String k : mine) {
            Row r = find(k);
            boolean any = false;
            for (String f : r.fields) if (fields.contains(f)) any = true;
            if (any) s.playerRows().add(intelKey(r));
        }
        applied.addAll(mine);
        for (String k : applied) sideEffect(k, s);
        try { CACache.bump("settings"); } catch (Throwable ignored) { }
        return applied;
    }

    /** 0.3.6.0: the save switches to LunaLib's Story mode setting (CASync.setOn); nothing when the file has no value yet. */
    static void story(JSONObject j) {
        Object v = read(j, find("storymode"));
        if (v instanceof Boolean) CASync.setOn((Boolean) v);
    }

    /** The caches a change feeds, as CASetupUI.handle drops them. */
    static void sideEffect(String key, CASettings s) {
        try {
            if ("surplustf".equals(key) || "p_spend".equals(key)) CATerraSim.reset();
            else if ("projsupply".equals(key) || "p_extras".equals(key)) CAProjects.computedAt = -99f;
            else if ("resitems".equals(key)) { CAResearch.updateItemMode(s); CAResearch.cached = null; }
            else if ("marketmodel".equals(key)) { CAMarket.invalidate(); CAAuto.nextPass.clear(); }
            else if ("capmode".equals(key)) CACapital.reset();
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "LunaLib setting side effect " + key + " failed", t); }
    }

    // ---------------- the game side ----------------

    /** What LunaLib held at the last sync, by id; null while no save has synced (LunaLib off, or the title screen). */
    static Map<String, Object> last;
    /** LunaLib holds settings from a save that finished first-time setup: a new save's setup keeps them. */
    static boolean configured;

    public static void reset() { last = null; configured = false; }

    static boolean lunaEnabled() {
        try { return Global.getSettings().getModManager().isModEnabled(LUNA); } catch (Throwable t) { return false; }
    }

    /** LunaLib holds this save's settings: the intel Settings page shows the picks only. */
    public static boolean active() { return last != null && lunaEnabled(); }

    /** First-time setup keeps LunaLib's settings (no recommended values, no empire mode change). */
    public static boolean keepsSetup() { return configured && active(); }

    static JSONObject load() throws Exception {
        String txt = null;
        try { txt = Global.getSettings().readTextFileFromCommon(FILE); } catch (Exception ignored) { }
        return txt == null || txt.trim().isEmpty() ? new JSONObject() : new JSONObject(txt);
    }

    static void save(JSONObject j) throws Exception { Global.getSettings().writeTextFileToCommon(FILE, j.toString(3)); }

    /** onApplicationLoad: the listener for saves in LunaLib's menu. */
    public static void register() {
        if (!Global.getSettings().getModManager().isModEnabled(LUNA)) return;
        CALunaLink.register();
    }

    /** LunaLib reads its file again, so its menu shows what was written. */
    static void reload() {
        if (!Global.getSettings().getModManager().isModEnabled(LUNA)) return;
        CALunaLink.reload();
    }

    /** onGameLoad, after the save's own data and migrations: seed LunaLib once, or read it into this save. */
    public static void onLoad() {
        reset();
        if (!Global.getSettings().getModManager().isModEnabled(LUNA)) return;
        CALunaLink.register();
        try {
            CASettings s = CAData.settings();
            JSONObject j = load();
            if (!j.optBoolean(SEEDED, false)) {
                last = push(s, j);
                CADiagnostics.LOG.info(CADiagnostics.TAG + "LunaLib settings seeded from this save");
            } else {
                story(j);
                CASync.release(s);   // 0.3.6.0: LunaLib's values are the ones you set; the story-mode holds go back on after
                List<String> changed;
                try { changed = pull(s, j); } finally { CASync.enforce(s); }
                if (!changed.isEmpty()) CADiagnostics.LOG.info(CADiagnostics.TAG + "LunaLib settings read into this save: " + changed);
                last = push(s, j);
            }
            configured = j.optBoolean(SETUP, false);
            save(j);
            CALunaLink.reload();
        } catch (Throwable t) {
            last = null;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "LunaLib settings failed; the intel Settings page has them", t);
        }
    }

    /** A save in LunaLib's menu (CALunaLink.settingsChanged). */
    public static void changed() {
        if (!active()) return;
        try {
            CASettings s = CAData.settings();
            JSONObject j = load();
            story(j);
            CASync.release(s);   // 0.3.6.0: as on load
            List<String> applied;
            try { applied = applyChanges(s, j, last); } finally { CASync.enforce(s); }
            if (!applied.isEmpty()) CADiagnostics.LOG.info(CADiagnostics.TAG + "LunaLib settings applied: " + applied);
            last = push(s, j);
            configured = j.optBoolean(SETUP, false);
            save(j);
            reload();
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "LunaLib settings change failed", t); }
    }

    /** CASettings changed in the game (an intel click, first-time setup, a save): LunaLib gets the values. */
    public static void sync() {
        if (!active()) return;
        try {
            CASettings s = CAData.settings();
            JSONObject j = load();
            last = push(s, j);
            configured = j.optBoolean(SETUP, false);
            save(j);
            reload();
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "LunaLib settings write failed", t); }
    }

    // ---------------- the CSV (data/config/LunaSettings.csv; JTLunaCheck) ----------------

    static String cell(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    /** LunaLib draws names and descriptions with addPara, so a % is written %%. */
    static String para(String v) { return v == null ? "" : v.replace("%", "%%"); }

    static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    /** The CSV, defaults from a new save with the recommended values and the Advisor column. */
    public static String csv() {
        CASettings d = new CASettings();
        CASetupUI.applyRecommended(d);
        CAModeTable.apply(d, d.empire());
        StringBuilder b = new StringBuilder("fieldID,fieldName,fieldType,defaultValue,secondaryValue,fieldDescription,minValue,maxValue,tab\n");
        String lastTab = null;
        for (Row r : ROWS) {
            if (lastTab != null && !lastTab.equals(r.tab) || HEADER.equals(r.type) && lastTab != null) b.append(",,,,,,,,\n");
            lastTab = r.tab;
            String def = "", sec = "", min = "", max = "";
            if (HEADER.equals(r.type)) def = r.name;
            else if (TEXT.equals(r.type)) def = para(r.desc);
            else {
                Object v = value(r, d);
                def = v instanceof Double ? fmt((Double) v) : String.valueOf(v);
                if (RADIO.equals(r.type)) sec = String.join(", ", r.opts);
                if (INT.equals(r.type) || DOUBLE.equals(r.type)) { min = fmt(r.min); max = fmt(r.max); }
            }
            String name = HEADER.equals(r.type) ? r.name : para(r.name);
            String desc = r.value() ? para(r.desc) : "";
            b.append(cell(r.id())).append(',').append(cell(name)).append(',').append(r.type).append(',').append(cell(def)).append(',')
                    .append(cell(sec)).append(',').append(cell(desc)).append(',').append(min).append(',').append(max).append(',')
                    .append(cell(r.tab)).append('\n');
        }
        return b.toString();
    }
}
