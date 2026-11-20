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
    /** 0.3.8.7: the values of the last sync, as a JSON string, so a load can tell what a save in the menu with no game loaded changed. */
    static final String LAST = "ca__last";
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
        /** 0.3.8.7: a preset row's CAPresets row (pull applies it on load). */
        String presetRow;
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
        Row st = row(BOOL, "storymode", "Story mode", "On means the Colony Autopilot starts dormant and you unlock it through the story. "
                + "Off means everything works from the start. If you switch, each save keeps its story progress.");
        st.get = s -> CASync.on();
        st.set = (s, v, b) -> { };   // applied before the other rows (story())
        text("presets", "Presets set several fields at once, and you'll find all of those fields on the Advanced tab. "
                + "Mode default puts the group back on the empire mode's values, so it follows the mode. Custom means the fields "
                + "don't match any option, so picking it does nothing.");
        header("playstyle", "Playstyle");
        Row r = row(RADIO, "empire", "Empire mode", "Empire-wide settings follow this. Off does nothing, Advisor advises, Approve asks you "
                + "first and Auto just does it. Anything you've set yourself stays when the mode changes.");
        final CAData.Mode[] modes = CAData.Mode.values();
        r.opts = new String[modes.length];
        for (int i = 0; i < modes.length; i++) r.opts[i] = modes[i].label;
        r.get = s -> s.empire().label;
        r.set = (s, v, b) -> { int i = idx("empire", v); if (i >= 0) CAModeTable.setEmpire(s, modes[i]); };
        r = row(BOOL, "lowprofile", "Low profile", "Colony crises come slower, but you'll earn less. It won't put AI cores in colonies "
                + "above size 3, and it stays away from Pather interest, free ports, raids and sabotage. Off means Full expansion.");
        r.fields = new String[]{"lowProfile"};
        r.get = s -> s.lowProfile;
        r.set = (s, v, b) -> CALowProfile.set(s, (Boolean) v, "LunaLib settings");
        preset("p_spend", "Spending", CAPresets.SPENDING, "Thrifty spends 30% of net income and 5% of savings, Normal 50% and 15%, "
                + "and Lavish 80% and 30%. The per-project cap follows it too (half, all or twice your net income).");
        preset("p_extras", "Extras", CAPresets.EXTRAS, "It covers the mod buildings and features: stable locations, measured mod buildings, "
                + "trade contracts, research, special project supply, megastructures, station augments, RAT settlement, IndEvo automation "
                + "and Antimatter Stabilizers.");
        preset("p_newcol", "New colonies", CAPresets.NEW_COLONIES, "It'll found new colonies with idle money, and on Ask first you approve each one.");
        preset("p_war", "War", CAPresets.WAR, "This is how far war fleets go (faction stances cap it). Defend sends relief, Retaliate raids "
                + "attackers and retakes your lost colonies, and Hold them down raids anyone at Rival and up. Conquer invades and Scorched "
                + "bombards. Without Nexerelin, Defend sends Auto Colonies' own defense fleets, and the levels past it need Nexerelin.");

        header("money", "Money");
        credits("reserve", "Credit reserve", "reserve", 0, 100000000, 50000, "The money Autopilot won't touch.");
        r = radio("surplus", "Surplus investment", "surplusLine", CASettings.SURPLUS_NAMES, "Anything you've got over this goes into one focus world.");
        final Setter surplus = r.set;
        r.set = (s, v, b) -> { surplus.set(s, v, b); s.surplusChecked = true; };
        radio("richpreset", "What to buy", "richPreset", CASettings.RICH_PRESET_NAMES, "It's what idle money buys first. The rungs are under "
                + "Advanced, in Surplus and money ladder.");
        radio("endgame", "Endgame", "endgameMode", CAEndgame.NAMES, "At this income, it'll start building for production and defense.");
        preset("p_story", "Story points", CAPresets.STORY, "It caps the story points Autopilot spends on improvements.");

        header("building", "Building");
        preset("p_station", "Stations", CAPresets.STATIONS, "It builds stations from size 4 and upgrades them as colonies grow "
                + "(Battlestation at 6, Star Fortress at 8, and a size sooner at the capital). Imperium needs Interstellar Imperium.");
        preset("p_takedown", "Take down buildings", CAPresets.TAKE_DOWN, "Never means nothing comes down. Clean up removes industries that stopped "
                + "paying, makes room for capital trade buildings and allows endgame changes. Free hand is Clean up plus Allow removals.");
        preset("p_policy", "Colony policies", CAPresets.POLICIES, "Auto uses hazard pay, free port and edicts where they pay, and Don't touch leaves them alone.");

        header("people", "Items and people");
        preset("p_cores", "AI cores", CAPresets.AI_CORES, "AI cores come from Autopilot Storage. Safe is never inspected, and Defended resists "
                + "inspections, so the Hegemony turns hostile.");
        preset("p_admins", "Administrators", CAPresets.ADMINS, "Place only uses the administrators you've got, and Hire and place hires more too.");

        header("security", "Security and diplomacy");
        radio("spy", "Agents", "spyMode", CASettings.SPY_NAMES, "Agents handle security and relations (offense adds sabotage). Needs Nexerelin.");
        bool("amb", "Ambassadors", "disableAmbassadors", true, "It hires and installs ambassadors (Ally and Friendly factions first). Needs Industrial Evolution.");
        text("picks", "You'll find faction stances and this save's picks (capital, refit hub, sell hub, offense target) on the "
                + "Autopilot panel's Settings page.");

        header("interface", "Interface");
        radio("screenlog", "On-screen log", "screenLog", CALog.SCREEN_NAMES, "Short lines show on screen, and the Log page keeps every entry.");

        header("reset", "Reset");
        r = row(BOOL, "reset", "Reset to recommended", "If you tick it and save, your settings go back to the empire mode's defaults. "
                + "Low profile, faction stances and this save's picks stay. Anything else you changed in the same save applies after the "
                + "reset, and then the box unticks itself.");
        r.get = s -> Boolean.FALSE;
        r.set = (s, v, b) -> { };
        // 0.3.9.4: Prepare for removal (CARemoval). Applied before every other row (changed); each load shows the save's state
        header("removal", "Removal");
        r = row(BOOL, "removal", "Prepare for removal", "Turn it on and a dialog in the campaign asks you first, then Auto Colonies moves "
                + "Autopilot Storage into each colony's Storage, takes out what it keeps in this save and stops. Then save, quit and "
                + "remove the mod. Turning it off lets Auto Colonies run again in this save.");
        r.get = s -> CARemoval.off || CARemoval.asked;
        r.set = (s, v, b) -> { };

        // ---------------- Advanced: one header per CASetupUI.ADVANCED subgroup, same order ----------------
        tab = ADVANCED;
        header("adv_budget", "Budget");
        r = row(RADIO, "type", "Budget type", "Automatic spends a share of net income plus a share of savings above the reserve. "
                + "Share of net income uses just the first share, and Fixed amount uses the Monthly budget.");
        r.opts = new String[]{"Automatic", "Share of net income", "Fixed amount"};
        r.fields = new String[]{"budgetAuto", "budgetPercentMode"};
        r.get = s -> idxName("type", s.budgetAuto ? 0 : s.budgetPercentMode ? 1 : 2);
        r.set = (s, v, b) -> {
            int i = idx("type", v);
            if (i == 0) s.budgetAuto = true;
            else if (i == 1) { s.budgetAuto = false; s.budgetPercentMode = true; }
            else if (i == 2) { s.budgetAuto = false; s.budgetPercentMode = false; }
        };
        pct("budget", "From net income (percent)", "budgetPercent", 10, 100, 10, "Automatic and Share of net income both use this.");
        r = pct("savings", "From savings (percent)", "savingsShare", 5, 50, 5, "Each month, Automatic spends this share of whatever credits you've got above the reserve.");
        r.get = s -> Math.round((s.savingsShare > 0f ? s.savingsShare : 0.15f) * 100f);
        credits("budgetfixed", "Monthly budget", "budgetFixed", 0, 100000000, 250000, "Fixed amount spends this monthly.");
        r = row(RADIO, "captype", "Per-project cap", "Automatic follows Spending (half, all or twice net income), and Fixed uses Fixed cap.");
        r.opts = new String[]{"Automatic", "Fixed"};
        r.fields = new String[]{"projectCapFixed", "perProjectCap"};
        r.get = s -> idxName("captype", s.projectCapFixed ? 1 : 0);
        r.set = (s, v, b) -> {
            int i = idx("captype", v);
            if (i < 0) return;
            if (b != null && i == 1 && !s.projectCapFixed) s.perProjectCap = CAPresets.projectCap(s);   // Fixed starts from the cap in effect
            s.projectCapFixed = i == 1;
        };
        r = credits("cap", "Fixed cap", "perProjectCap", 250000, 100000000, 500000, "This is the most one project can cost, and changing it switches the cap to Fixed.");
        final Setter cap = r.set;
        r.set = (s, v, b) -> { cap.set(s, v, b); if (b != null && !b.contains("captype")) s.projectCapFixed = true; };
        r = row(RADIO, "upkeeptype", "New upkeep per month", "Automatic allows a share of net income that follows Spending "
                + "(10% on Normal), and Fixed uses Fixed upkeep.");
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
        credits("upkeep", "Fixed upkeep", "newUpkeepCap", 0, 10000000, 10000, "Fixed allows this much new upkeep a month.");
        bool("pause", "Pause on loss", "pauseOnLoss", false, "It stops spending while your net income is below 0.");
        radio("fees", "Convenience fees", "feeLevel", CASettings.FEE_NAMES, "These apply to remote building, shipping and orders.");

        header("adv_rich", "Surplus and money ladder");
        r = row(RADIO, "surplusrich", "Surplus over " + num(CASurplus.RICH), "Once you're this much over the surplus line, every Approve and Auto "
                + "colony spends from the surplus (or just the focus world).");
        r.opts = new String[]{"Every colony", "Focus world only"};
        flag(r, "surplusFocusOnly");
        bool("wonders", "Wonders and gates", "noWonders", true, "It builds the Daedalus Array and Bifrost gates once their goods are "
                + "made. Needs Vaults of Knowledge.");
        radio("surplustf", "Surplus terraforming", "surplusTerraform", CASettings.SURPLUS_TF_NAMES, "Whatever's left after the focus world goes into "
                + "terraforming. Needs Terraforming Made Easy.");
        bool("surplusres", "Surplus research", "noSurplusResearch", true, "It builds more Research Facilities from the surplus while the speed "
                + "is worth it. Needs Vaults of Knowledge.");
        for (int k = CARich.STOCKPILES; k < CARich.RUNGS.length; k++) {
            final int rung = k;
            r = row(RADIO, "rung_" + k, CARich.RUNGS[k] + " rung", "If it's Off, its share goes to the next rung.");
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
        integer("shipyardstop", "Shipyard stops at", "shipyardStop", 1, 30, 1, "It counts the shipyard's unclaimed hulls in the capital's Storage.");

        header("adv_build", "Building");
        bool("overlimit", "Over-limit fix", "noOverLimitFix", true, "If a colony's over its industry limit, it takes down its weakest industry.");
        bool("marketmodel", "Exports valued by market share", "noMarketModel", true, "It values exports by how much of each sector market you hold.");
        bool("prune", "Take down unpaying industries", "noPrune", true, "It takes down any industry that hasn't paid for 30 days.");
        bool("endgamerep", "Endgame replaces industries", "endgameNoReplace", true, "It'll replace weak industries with production chains, fleets and defense.");
        bool("removals", "Allow removals", "allowRemovals", false, "This lets Autopilot replace buildings with better ones.");
        integer("spcap", "Story points a month", "storyPointMonthlyCap", 0, 50, 1, "This applies when Story points is on 2 a month, 5 a month or Custom.");

        header("adv_cores", "AI cores and items");
        bool("cores", "Use AI cores", "useAICores", false, "It puts AI cores from Autopilot Storage into colony buildings.");
        radio("alpha", "Alpha cores", "alphaMode", CASettings.ALPHA_NAMES, "It sets where Alpha cores can go.");
        radio("corerisk", "Inspection risk", "coreRisk", CASettings.CORE_RISK_NAMES, "It sets how many AI cores colonies hold against Hegemony inspections.");
        radio("inspect", "Hegemony inspections", "inspectionMode", CASettings.INSPECTION_NAMES, "Resist where defended will turn the Hegemony hostile.");
        r = row(RADIO, "researchcores", "Alphas in research buildings", "Alpha cores cut research buildings' costs in half. Needs Vaults of Knowledge.");
        r.opts = new String[]{"First", "By value"};
        flag(r, "researchCoresByValue");
        radio("resitems", "Research uses colony items", "researchItemMode", CASettings.RESEARCH_ITEM_NAMES, "This only happens while credits are "
                + "over the surplus line. Needs Vaults of Knowledge.");

        header("adv_policy", "Colony policies");
        radio("hazard", "Hazard pay", "hazardPayMode", CASettings.POLICY_NAMES, "Auto turns on hazard pay where it speeds growth up enough.");
        pct("hpcol", "Hazard pay growth threshold (percent)", "hazardPayColonyShare", 5, 100, 5, "Hazard pay goes on where it speeds growth up by at least this much.");
        pct("hpemp", "Hazard pay budget (percent of net income)", "hazardPayEmpireShare", 1, 100, 1, "All your hazard pay together stays within this share of net income.");
        radio("freeport", "Free port", "freePortMode", CASettings.POLICY_NAMES, "Auto sets a free port where it pays.");
        bool("edicts", "Senate edicts", "disableEdicts", true, "Needs Industrial Evolution.");

        header("adv_people", "People and diplomacy");
        bool("admin", "Administrator hiring", "adminHiring", false, "It hires administrators for colonies without one.");
        bool("adminopt", "Assign administrators optimally", "adminOptimal", false, "It places each administrator where their skills pay the most.");
        radio("ambfocus", "Among equal stances, first", "ambassadorFocus", CASettings.FOCUS_NAMES, "Ambassadors and agents work on these factions "
                + "first. Needs Industrial Evolution or Nexerelin.");
        radio("ambstop", "Stop raising relations at", "ambassadorStopAt", CASettings.STOP_NAMES, "Needs Industrial Evolution or Nexerelin.");
        bool("spyhire", "Recruit agents", "spyNoHiring", true, "Needs Nexerelin.");
        radio("spykeep", "Agents to keep", "spyKeep", new String[]{"Auto", "1", "2", "3", "4"}, "Needs Nexerelin.");
        radio("autonomy", "Autonomy", "autonomyMode", CASettings.AUTONOMY_NAMES, "Recommend names the colony that would help in a money emergency, "
                + "and Auto only recommends too. Needs Nexerelin.");

        header("adv_war", "War");
        bool("defense", "Emergency defense fleets", "disableDefenseFleets", true, "When a raid is coming or hostiles outweigh a colony's defenses, "
                + "it sends a defense fleet, paid from credits above the reserve. Without Nexerelin, Auto Colonies raises the fleet itself.");
        bool("warretake", "Retake your lost colonies", "noWarRetake", true, "It works at Retaliate and up. Needs Nexerelin.");
        bool("warrivals", "Top hostile threat as Rival", "warAutoRivals", false, "Needs Nexerelin.");
        radio("warconq", "Conquered colonies start in", "conqueredMode", CASettings.CONQUERED_NAMES, "Needs Nexerelin.");
        radio("warscorch", "Bombardment targets", "scorchedTargets", CASettings.SCORCHED_TARGET_NAMES, "It's used at Scorched. Needs Nexerelin.");
        bool("warcoal", "Coalition risk allowed", "warCoalitionOK", false, "When it's off, offensives stop before a coalition forms. Needs Nexerelin.");
        // 0.3.8.6: Sindrian Diktat raiders, their own switch (inverted field, on by default)
        bool("enfdiktat", CASettings.ENF_DIKTAT_NAME, "enfDiktatOff", true, "Colony patrols attack them even with Enforcer patrols off, "
                + "and it doesn't cost anything or change reputation.");
        // 0.3.9.5: patrol kills count for crises (CAPatrolKills; inverted field, on by default)
        bool("crisiskills", CASettings.CRISIS_KILLS_NAME, "crisisKillsOff", true, "Ships destroyed by your Auto colonies' patrols and stations "
                + "count toward the colony crisis and Tri-Tachyon commerce raiding, the same as your own kills.");
        bool("enf", "Enforcer patrols", "enfOn", false, "Colony patrols attack the fleet types you set to On. You're charged once for each fleet "
                + "your forces defeat, and if you fight one yourself you get the game's penalty.");
        String[] kindFields = {"enfInvestigators", "enfInspections", "enfLeague", "enfProtectors", "enfRaiders", "enfPolice"};
        for (int k = 0; k < CASettings.ENF_KIND_NAMES.length; k++)
            bool("enfk_" + k, CASettings.ENF_KIND_NAMES[k], kindFields[k], false, "It's part of Enforcer patrols." + (k == CAEnforcers.POLICE ? " Needs Nexerelin." : ""));
        radio("enfcost", "Enforcer cost", "enfCost", CASettings.ENF_COST_NAMES, "Reputation means the game's penalty for defeating that fleet yourself (-3), "
                + "and commerce raiders and police don't cost any. Credits means fleet points times Nexerelin's fleet request price "
                + "(400 without it), paid above the credit reserve.");
        r = row(RADIO, "enfwhere", "Enforcer patrols in", "Colony patrols attack in these systems.");
        r.opts = labels(CASettings.ENF_WHERE_NAMES);
        flag(r, "enfAllColonies");
        bool("enfcomm", "Skip your commissioner", "enfCommissionOK", true, "Patrols leave the faction you hold a commission with alone.");
        bool("enfally", "Skip allies and League membership", "enfAlliesOK", true, "Patrols leave allies alone, and the League too while you're a member.");
        bool("enfcease", "Skip ceasefires", "enfCeasefireOK", true, "Needs Nexerelin.");
        bool("enflow", "Off in Low profile", "enfLowProfileOK", true, "Patrols don't attack while Low profile is on.");
        // 0.3.6.6: was "Off in your fleet's system" (enfnear, inverted, default on); patrols now attack while you are there by default
        bool("enfhere", "Attack in your fleet's system", "enfNearYouOK", false, "When it's on, patrols attack even while you're in "
                + "the system, and a marked fleet turns hostile to you too.");
        bool("enfint", "Patrols intercept", "enfIntercept", false, "Colony patrols in the system get told to intercept the fleet.");

        header("adv_capital", "Capital and storage");
        r = row(RADIO, "capmode", "Capital does", "Deliveries and Storage gather at the capital, and Off means no capital. You pick the "
                + "capital on the Autopilot panel's Settings page.");
        r.opts = labels(CACapital.MODE_NAMES);
        r.fields = new String[]{"capitalMode"};
        r.get = s -> idxName("capmode", clamp(s.capitalMode, 3));
        r.set = (s, v, b) -> {
            int i = idx("capmode", v);
            if (i < 0 || i == s.capitalMode) return;
            // as the intel Capital row's None. 0.3.8.7: the pick is cleared only for a change in LunaLib's menu (b set), never on load
            if (i == 2) { s.capitalModeBeforeNone = s.capitalMode; if (b != null) s.capitalId = null; }
            s.capitalMode = i;
        };
        radio("capstart", "Trade hub starts at", "capitalStart", CACapital.START_NAMES, "The capital starts its trade buildings at this point.");
        bool("caproom", "Make room for trade buildings", "capitalNoRoom", true, "The capital will take down an industry to fit its trade buildings.");
        bool("capgather", "Gather Storage at the capital", "capitalNoGather", true, "It gathers Autopilot Storage at the capital.");
        radio("logi", "Storage logistics", "logisticsMode", new String[]{"Off", "On"}, "It moves goods to the refit hub weekly, and you pick the hubs "
                + "on the Autopilot panel's Settings page.");
        bool("logiships", "Ships to the refit hub", "logisticsShips", false, "It's part of Storage logistics.");
        bool("logigoods", "Supplies, fuel, crew, marines too", "logisticsSupplies", false, "It's part of Storage logistics.");

        header("adv_extras", "Extras");
        radio("objectives", "Stable locations", "objectiveMode", CASettings.OBJECTIVE_NAMES, "This covers comm relays, nav buoys and "
                + "sensor arrays at stable locations.");
        radio("measured", "Measured mod buildings", "measuredMode", CASettings.MEASURED_NAMES, "Mod buildings get scored from probe-colony measurements.");
        radio("contract", "Trade contracts", "contractMode", CASettings.CONTRACT_NAMES, "Needs Theory of Toolbox.");
        radio("research", "Research", "researchMode", CASettings.CONTRACT_NAMES, "Needs Vaults of Knowledge.");
        radio("projsupply", "Special project supply", "projectSupplyMode", CASettings.PROJECT_SUPPLY_NAMES, "It builds a producer for special project "
                + "goods that no colony makes. Needs Vaults of Knowledge.");
        radio("hs", "Megastructures", "hypershuntMode", CASettings.AUGMENT_NAMES, "This covers Coronal Hypershunt range and "
                + "restoration. Needs Vaults of Knowledge.");
        radio("aug", "Station augments", "augmentMode", CASettings.AUGMENT_NAMES, "Needs Station Augments.");
        radio("settle", "RAT settlement", "settlementMode", CASettings.SETTLE_NAMES, "Needs Random Assortment of Things.");
        credits("settleinc", "Found when a site earns", "settlementMinIncome", 5000, 10000000, 5000, "Needs Random Assortment of Things.");
        bool("spec", "IndEvo specialties", "disableSpecialties", true, "Needs Industrial Evolution.");
        bool("vpc", "VPC chips", "disableVPC", true, "Needs Industrial Evolution.");
        bool("forge", "Forge may use up chips", "disableForgeVPC", true, "Needs Industrial Evolution.");
        bool("ship", "Courier shipping", "disableShipping", true, "Needs Industrial Evolution.");
        bool("stab", "Antimatter Stabilizers", "disableStabilizers", true, "Needs Emergent Threats IX Revival.");
        bool("repa", "Reverse engineering", "disableRepa", true, "It sends weapons and fighters from your Storage to the hub's research. "
                + "Needs Reverse Engineered Private Arsenal.");

        header("adv_expand", "New colonies");
        radio("expline", "Money for new colonies", "expandLine", CASettings.EXPAND_LINE_NAMES, "New colonies uses this on Ask first or Auto.");
        radio("expscope", "Where", "expandScope", CASettings.EXPAND_SCOPE_NAMES, "Low profile means only systems you hold.");
        CAData.Profile[] goals = CAData.Profile.values();
        String[] goalNames = new String[goals.length + 1];
        goalNames[0] = "Fits its plan";
        for (int i = 0; i < goals.length; i++) goalNames[i + 1] = goals[i].label;
        radio("expgoal", "Goal of a new colony", "expandGoal", goalNames, "A new colony starts with this goal.");
        radio("expmethod", "Method", "expandMethod", CASettings.EXPAND_METHOD_NAMES, "Needs Nexerelin.");
        radio("expmax", "Most colonies", "expandMax", CASettings.EXPAND_MAX_NAMES, "It stops founding colonies at this count.");
        bool("expbuildout", "Fast buildout", "noExpandBuildout", true, "It builds up a new colony right away.");
        bool("expadmins", "Administrator past the limit", "noExpandAdmins", true, "It hires an administrator for a new colony past the limit.");
        radio("tascst", "TASC stations", "stationMode", CASettings.TASC_STATION_NAMES, "With New colonies on Auto, Auto stations only recommend "
                + "while the Expansion rung asks first. Needs Terraforming and Station Construction.");

        header("adv_prod", "Production");
        preset("p_prod", "Production", CAPresets.PRODUCTION, "Off uses the 0.3.1.0 picks, and Light and Full set the floors below.");
        integer("fhulls", "Hull sources", "floorHullSources", 0, 3, 1, "It wants this many ship producers.");
        credits("fcap", "Hull capacity a month", "floorCapacity", 0, 1000000, 50000, "It wants this much ship production capacity, in credits a month.");
        integer("fmonths", "Order goods within (months)", "floorOrderMonths", 3, 24, 1, "It spreads Ashes of the Domain order goods over this many months.");
        dbl("fqual", "Production quality", "floorQuality", 0.5, 1.2, 0.1, "It wants this ship production quality.");
        integer("fparts", "Parts reserve", "floorPartsReserve", 0, 1000, 50, "It keeps this many Domain-grade Parts beyond the fleet's repair need.");
        radio("fsup", "Supplies floor", "floorSupplies", CASettings.FLOOR_GOODS_NAMES, "It wants this much supplies production.");
        radio("ffuel", "Fuel floor", "floorFuel", CASettings.FLOOR_GOODS_NAMES, "It wants this much fuel production.");
        bool("fmoney", "Floors at Money colonies", "floorMoneyGoal", false, "Floors also apply at colonies with the Money goal.");
        bool("fneeds", "Floor builds first", "needsFirst", false, "Floor builds rank ahead of other builds.");
        radio("froom", "Make room", "makeRoom", CASettings.MAKE_ROOM_NAMES, "A floor producer can replace the weakest income industry.");
        radio("ftaper", "Money taper", "moneyTaper", CASettings.TAPER_NAMES, "Money counts for less in the planner at high income.");
        r = credits("ftlow", "Taper from", "taperLow", 100000, 9900000, 100000, "Gentle uses this, and it stays 100,000 below Taper top from.");
        final Setter low = r.set;
        r.set = (s, v, b) -> { low.set(s, v, b); if (b != null && !b.contains("fthigh")) s.taperLow = Math.min(s.taperLow, s.taperHigh - 100000f); };
        r = credits("fthigh", "Taper top from", "taperHigh", 200000, 10000000, 100000, "Gentle uses this, and it stays 100,000 above Taper from.");
        final Setter high = r.set;
        r.set = (s, v, b) -> { high.set(s, v, b); if (b != null) s.taperHigh = Math.max(s.taperHigh, s.taperLow + 100000f); };
        integer("fdivn", "Same building copies", "diversityCopies", 0, 6, 1, "After this many copies of a building family, the next counts less (0 is off).");
        pct("fdivd", "Next copy counts (percent)", "diversityShare", 50, 100, 5, "Each copy past that count is worth this much.");
        radio("fweights", "Goal weights", "goalWeights", CASettings.GOAL_WEIGHT_NAMES, "It weighs each colony goal with this table.");
        bool("fkeep", "Contracts keep floor goods", "keepProductionGoods", false, "Trade contracts leave floor goods alone while their floor is open.");
        bool("fpartscap", "Parts to the capital", "partsToCapital", false, "Storage logistics brings Domain-grade Parts to the capital's Storage weekly.");
        bool("fshipyard", "Shipyard from Surplus", "shipyardFromSurplus", false, "The Shipyard rung opens at Surplus while floors are on.");
        credits("fvhull", "Value: hull source", "floorValHull", 0, 10000000, 10000, "The planner values one hull source at this much a month.");
        dbl("fvcap", "Value: capacity credit", "floorValCapacity", 0, 100, 0.1, "One credit of capacity is worth this a month.");
        credits("fvqual", "Value: 1.0 quality", "floorValQuality", 0, 10000000, 10000, "1.0 production quality is worth this a month.");
        credits("fvpart", "Value: a part", "floorValPart", 0, 100000, 50, "One Domain-grade Part is worth this.");
        dbl("fvorder", "Value: order goods (times)", "floorValOrders", 0, 100, 0.5, "This multiplies order goods' worth.");
        dbl("fvgoods", "Value: goods (times)", "floorValGoods", 0, 100, 0.5, "This multiplies floor goods' worth.");

        header("adv_ui", "Interface");
        radio("voice", "Announcement voice", "voice", CASettings.VOICE_NAMES, "Announcements use machine or plain wording.");
        bool("screenbtn", "Button on the colony screen", "noScreenButton", true, "It puts the Colony Autopilot button on your colony's screen.");
        r = row(RADIO, "thinking", "Thinking list", "The Log page lists what Autopilot weighed.");
        r.opts = new String[]{"Hide", "Show"};
        flag(r, "showThinking");
        r = row(RADIO, "approvaldays", "Approval summary", "You get one dialog with every decision waiting for approval.");
        r.opts = new String[CAApprovalDialog.DAYS.length];
        for (int i = 0; i < CAApprovalDialog.DAYS.length; i++) r.opts[i] = CAApprovalDialog.daysName(CAApprovalDialog.DAYS[i]);
        r.fields = new String[]{"approvalDays"};
        r.get = s -> CAApprovalDialog.daysName(s.approvalDays);
        r.set = (s, v, b) -> { int i = idx("approvaldays", v); if (i >= 0) s.approvalDays = CAApprovalDialog.DAYS[i]; };
        radio("hints", "Build menu hints", "buildHints", CASettings.HINT_NAMES, "It shows Autopilot's ranking beside the game's build menu.");
        bool("hintscore", "Score on open", "hintScoreOnOpen", false, "It scores an out-of-date colony once a day when you open its build menu.");
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
        r.presetRow = presetRow;   // 0.3.8.7
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
        Row r = row(type, key, name, desc + " It goes from " + plain(min) + " to " + plain(max) + (step != 1 ? ", in steps of " + plain(step) : "") + ".");   // 0.3.9.8: in the voice
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
        JSONObject l = new JSONObject();   // 0.3.8.7: read back by stored() on the next load
        for (Map.Entry<String, Object> e : vals.entrySet()) if (e.getValue() != null) l.put(e.getKey(), e.getValue());
        j.put(LAST, l.toString());
        return vals;
    }

    /** 0.3.8.7: the values of the last sync from the file (LAST), by id; a row it lacks takes the file's value. Null when there are none. */
    static Map<String, Object> stored(JSONObject j) {
        String txt = j == null ? "" : j.optString(LAST, "");
        if (txt.isEmpty()) return null;
        JSONObject l;
        try { l = new JSONObject(txt); } catch (Exception e) { return null; }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        for (Row r : ROWS) if (r.value()) out.put(r.id(), l.has(r.id()) ? read(l, r) : read(j, r));
        return out;
    }

    /**
     * 0.3.8.7: the file read into this save on load. A save in LunaLib's menu with no game loaded (the title screen) only writes
     * the file. The rows it changed differ from the values of the last sync (stored), and are applied as a save in the menu
     * during a game is (applyChanges: Reset, presets, set by you). The rest is read as before (pull), from the last sync's
     * values. Without stored values, pull alone. Returns the row keys that changed.
     */
    static List<String> readIn(CASettings s, JSONObject j) throws Exception {
        Map<String, Object> lastSync = stored(j);
        if (lastSync == null) return pull(s, j);
        JSONObject base = new JSONObject(j.toString());
        for (Row r : ROWS) { Object v = lastSync.get(r.id()); if (r.value() && v != null) base.put(r.id(), v); }
        List<String> changed = pull(s, base);
        for (String k : applyChanges(s, j, lastSync)) if (!changed.contains(k)) changed.add(k);
        return changed;
    }

    /**
     * Reads the JSON into CASettings on load: the set-by-you names, the empire mode (its column goes into the fields LunaLib
     * doesn't hold), then each row whose value differs. 0.3.8.7: a preset row whose value is one of its options and differs
     * from the save's is applied before the field rows: seven fields have no row of their own (War, New colonies, Story
     * points, Stations, Production, the Spending upkeep share). The field rows after it keep their exact values. Returns the
     * row keys that changed.
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
        for (Row r : ROWS) {   // 0.3.8.7: presets first; Mode default and Custom write nothing
            if (!r.preset || r.presetRow == null) continue;
            int i = r.index(read(j, r));
            if (i < 0 || i >= CAPresets.values(r.presetRow).length || i == CAPresets.match(s, r.presetRow)) continue;
            try { CAPresets.apply(s, r.presetRow, i); changed.add(r.key); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "LunaLib setting " + r.id() + " not read", t); }
        }
        for (Row r : ROWS) {
            if (!r.value() || r.preset || "empire".equals(r.key) || "reset".equals(r.key) || "storymode".equals(r.key) || "removal".equals(r.key)) continue;
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
            try { CALog.think("Settings: reset to recommended (LunaLib)."); } catch (Throwable ignored) { }   // 0.3.8.6: your tick; Thinking list
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
                if (!batch.contains(r.key) || r.preset != (pass == 0) || "empire".equals(r.key) || "reset".equals(r.key) || "storymode".equals(r.key)
                        || "removal".equals(r.key)) continue;
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

    /** 0.3.8.7: the title screen: an exited game's settings are not the next load's. */
    static boolean title() {
        try { return Global.getCurrentState() == com.fs.starfarer.api.GameState.TITLE; } catch (Throwable t) { return false; }
    }

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
                try { changed = readIn(s, j); } finally { CASync.enforce(s); }   // 0.3.8.7: and what the title screen changed
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

    /**
     * A save in LunaLib's menu (CALunaLink.settingsChanged). 0.3.8.7: on the title screen (or before any load) nothing is
     * applied: the file keeps the change and the next load reads it (readIn).
     */
    public static void changed() {
        if (title()) return;
        // 0.3.9.4: Prepare for removal first; in a save prepared for removal it is the only row read
        try { if (CARemoval.lunaChanged(load())) return; }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "LunaLib Prepare for removal failed", t); }
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
        if (!active() || CARemoval.off) return;
        try {
            CASettings s = CAData.settings();
            JSONObject j = load();
            last = push(s, j);
            configured = j.optBoolean(SETUP, false);
            save(j);
            reload();
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "LunaLib settings write failed", t); }
    }

    /** 0.3.9.4: LunaLib's Prepare for removal row shows this save's state (CARemoval). */
    static void markRemoval(boolean on) {
        if (!lunaEnabled()) return;
        try {
            JSONObject j = load();
            j.put(CARemoval.LUNA_ROW, on);
            save(j);
            reload();
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "LunaLib Prepare for removal write failed", t); }
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
