package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The settings screen (empire panel, Settings page) and the first-time setup rows.
 * 0.3.0.0 (WS-F1, research 04 sections 3 and 6-7): 20 main rows in 6 groups (Playstyle, Money, Building, Items and people,
 * Security and diplomacy, Interface), a collapsed Advanced group with one subgroup open at a time, and a link to the
 * Factions page. Preset rows (CAPresets) write the old fields; every old field keeps a row in Advanced or a page. Each main
 * row has one gray line of 20 words or fewer; longer explanations are hover text on the row and its line. The gray lines
 * and hover text are in the DESC and HOVER tables, and the screen opens with a 5-line glossary. JTMenuTextCheck checks the
 * row count, the keys and the word budgets; JTPresetsCheck the presets and Reset to recommended.
 */
public class CASetupUI {
    public static final String PREFIX = "ca:set:";
    /** Recommended surplus line: over 3,000,000. */
    static final int REC_SURPLUS = 2;
    /** 0.3.0.0 (WS-F1): the empire panel page the Faction stances link opens (CANav id; the page is CAFactionsPage, WS-F2). */
    public static final String FACTIONS_PAGE = "factions";
    static final float TIP_W = 420f;

    static float round(float v, float step) { return Math.max(step, Math.round(v / step) * step); }

    /**
     * The recommended settings: what a player who only switches colonies to Auto should get. Everything that pays for
     * itself and can't hurt is on; choices that are hard to undo or are really the player's (removing buildings, story
     * points, offense) stay off. Scaled to the empire's income and credits now. Lands every CAPresets row on its
     * recommended value (JTPresetsCheck).
     */
    public static void applyRecommended(CASettings s) {
        float net = Math.max(0f, CABudget.netIncome(s)), credits = CABuild.credits();
        // budget: half of net income plus 15% of savings above the reserve each month. A young colony that loses money
        // still gets built (share-of-income alone is 0 then), and spending slows as savings run down.
        s.budgetAuto = true;
        s.budgetPercentMode = true;
        s.budgetPercent = 0.5f;
        s.savingsShare = 0.15f;
        s.pauseOnLoss = false;   // the Automatic budget already tapers; pausing would stop every early colony
        s.reserve = Math.min(500000f, round(Math.max(50000f, credits * 0.25f), 50000f));   // a quarter of what you have, 50k-500k
        s.perProjectCap = Math.min(5000000f, round(Math.max(1000000f, net), 250000f));
        s.projectCapFixed = false;   // 0.3.0.0 (WS-F1 fix, DECISIONS 13): the cap in effect follows net income (CAPresets.projectCap)
        s.newUpkeepCap = round(Math.max(20000f, net * 0.1f), 10000f);   // only used if you switch to Fixed
        s.upkeepCapAuto = true;    // follows net income: 10% of last month's, at least 20,000
        s.upkeepCapChecked = true;
        s.upkeepShare = CATuning.UPKEEP_SHARE;   // 0.3.0.0 (WS-F1): the Spending preset Normal (10%)
        s.surplusLine = REC_SURPLUS;   // credits over 3,000,000 go into one focus world
        s.surplusChecked = true;
        s.surplusTerraform = 0;        // what the focus world leaves of it goes into terraforming, as much output as it buys
        s.noSurplusResearch = false;   // and into more Research Facilities while research left is worth the speed
        s.surplusFocusOnly = false;    // past 10,000,000 over the line, every colony spends it
        s.noWonders = false;           // and builds the Daedalus Array and Bifrost gates once their goods are made
        s.endgameMode = 0;             // from 1,000,000 a month while spending freely, money stops being the goal
        s.endgameNoReplace = false;    // and weak industries make way for production chains, fleets and defense
        // 0.3.0.0 (WS-F1): the money ladder's recommended order and rung modes (Shipyard and Expansion Recommend only, DECISIONS)
        s.richPreset = 0;
        s.rungMode = CASettings.defaultRungs();
        s.shipyardStop = 6;
        s.monthCredits = credits;
        s.feeLevel = 2;
        s.stationLine = "score";
        s.measuredMode = 0;
        s.allowRemovals = false;   // removing buildings is the player's call
        s.storyPointRule = 0;      // story points too
        // items and people: only items you put in Autopilot Storage are used; cores are kept within inspection limits
        s.useAICores = true;
        s.alphaMode = 0;
        s.researchCoresByValue = false;   // Alpha cores halve research buildings' costs: they go there first
        s.projectSupplyMode = 0;   // the goods a special project takes that no colony makes get a producer
        s.coreRisk = 1;           // Never inspected: More where defended only pays if inspections are resisted, which means war with the Hegemony
        s.inspectionMode = 0;      // Advise: resisting turns the Hegemony hostile, so that's your call
        s.disableStabilizers = false;
        s.adminHiring = true;
        s.adminOptimal = true;
        s.disableAmbassadors = false;
        // policies and mod automation: each one only acts when it pays
        s.hazardPayMode = 0;
        s.freePortMode = 0;
        s.hazardPayColonyShare = CATuning.HAZARD_COLONY_SHARE;   // hazard pay where it speeds growth up by at least 25%
        s.hazardPayEmpireShare = CATuning.HAZARD_EMPIRE_SHARE;   // all of it within 30% of empire net income: the more you earn, the more colonies get it
        s.disableEdicts = false;
        s.disableDefenseFleets = false;
        s.warMode = 0;          // protect and strike back: security only, never invasions
        s.warLevel = CAWarPolicy.RETALIATE;   // 0.3.0.0 (WS-E): the War level that matches warMode 0
        s.disableSpecialties = false;
        s.disableVPC = false;
        s.disableForgeVPC = false;
        s.disableShipping = false;
        s.settlementMode = 2;   // Auto: founds RAT's settlement only once a site clears the income threshold
        s.objectiveMode = 0;    // Auto: comm relays, nav buoys and sensor arrays at stable locations (0.2.40.0)
        s.augmentMode = 0;
        s.contractMode = 1;     // Auto: only contracts with 15% to spare that beat exporting
        s.researchMode = 1;     // Auto: databank-only techs, ranked by what they unlock
        s.hypershuntMode = 0;
        s.spyMode = 1;          // Defense: one agent on security (Pather cells, rebellions, pirate bases), an idle one of yours or a recruit
        s.spyChecked = true;
        s.spyNoHiring = false;
        s.spyKeep = 0;
        s.autonomyMode = 1;     // Recommend: autonomy is your call; Autopilot only says which colony would help in a money emergency
        s.noScreenButton = false;
        s.screenLog = 0;           // every log entry on screen, as one short line
        s.voice = 0;               // in the machine voice (0.2.41.0)
        s.capitalMode = 0;         // the capital gets your deliveries and, once colonies ramp up, its trade buildings (0.2.42.0)
        s.capitalStart = 0;        // from 100,000 a month of net income
        s.capitalNoGather = false;
        s.capitalNoRoom = false;
        s.noStationPlan = false;   // a station at every colony (0.2.42.0)
        s.noMarketModel = false;   // exports valued by your share of each sector market (0.2.44.0)
        s.noOverLimitFix = false;  // and a colony over its industry limit takes down its weakest industry
        s.noPrune = false;         // and an industry that stopped paying for 30 days (0.2.44.1)
    }

    /**
     * 0.3.0.0 (WS-F1, research 04 section 5 item 6): the Reset to recommended button. applyRecommended, except that Expansion
     * style, the War level, New colonies (Auto-expansion and its settings, TASC stations), faction stances, the Capital row
     * (the pick, and None), Capital does and the focus world stay as they are. Make room for trade buildings is part of
     * Take down buildings and is reset with it.
     */
    public static void resetToRecommended(CASettings s) {
        int warLevel = s.warLevel, warMode = s.warMode, capitalMode = s.capitalMode;
        applyRecommended(s);
        s.warLevel = warLevel;
        s.warMode = warMode;
        s.capitalMode = capitalMode;   // 0.3.0.0 (WS-F1 fix): None, Deliveries only or Deliveries and commerce, as you set it
        // applyRecommended doesn't touch lowProfile, expand*, stationMode, factionStance, capitalId, capitalModeBeforeNone or focusId
    }

    // ---------------- text (JTMenuTextCheck: a gray line is 20 words or fewer, a paragraph 40 or fewer) ----------------

    /** The gray line under each main row, by row key. */
    static final Map<String, String> DESC = new LinkedHashMap<String, String>();
    /** Hover text by row key: on the row's label and its gray line. */
    static final Map<String, String[]> HOVER = new LinkedHashMap<String, String[]>();
    static {
        DESC.put("lowprofile", "Low profile avoids colony crises and earns less.");
        DESC.put("p_spend", "Normal: 50% of net income and 15% of savings.");
        DESC.put("p_extras", "Mod buildings and features.");
        DESC.put("p_newcol", "Founds colonies with idle money.");
        DESC.put("p_war", "How far war fleets go; stances cap it.");
        DESC.put("reserve", "Credits Autopilot does not spend.");
        DESC.put("surplus", "Credits over this go to one focus world.");
        DESC.put("richpreset", "What idle money buys first.");
        DESC.put("endgame", "At this income, build for production and defense.");
        DESC.put("p_story", "Story points Autopilot may spend on improvements.");
        DESC.put("p_station", "Stations from size 4, upgraded with growth.");
        DESC.put("p_takedown", "Clean up removes industries that stopped paying.");
        DESC.put("p_policy", "Hazard pay, free port and edicts.");
        DESC.put("p_cores", "AI cores from Autopilot Storage.");
        DESC.put("p_admins", "Hires and places administrators.");
        DESC.put("capid", "Deliveries and Storage gather here.");
        DESC.put("logi", "Refit gear goes to one refit hub.");
        DESC.put("spy", "Security and relations; offense adds sabotage.");
        DESC.put("amb", "Ally and Friendly factions first.");
        DESC.put("stances", "One stance per faction. Agents, ambassadors, ceasefires and war fleets follow it.");
        DESC.put("screenlog", "Short lines on screen.");

        HOVER.put("lowprofile", new String[]{"No AI cores above size 3, no Pather interest, free ports, raids or sabotage."});
        HOVER.put("p_spend", new String[]{"Thrifty 30% and 5%, Normal 50% and 15%, Lavish 80% and 30%. The per-project cap is half, all or twice net income. Surplus terraforming: 10-year payback on Thrifty."});
        HOVER.put("p_war", new String[]{"Defend: relief. Retaliate: raids on attackers, retakes of your lost colonies. Hold them down: raids on Rival and up. Conquer: invasions. Scorched: bombardment."});
        // Advanced
        HOVER.put("fees", new String[]{"On remote building, shipping and orders."});
        HOVER.put("rung", new String[]{"Off: its share goes to the next rung."});
        HOVER.put("warcoal", new String[]{"Off: offensives stop before a coalition forms."});
    }

    /** The glossary at the top of the Settings page: term, then its line. */
    static final String[][] GLOSSARY = {
            {"Budget", "what Autopilot may spend a month."},
            {"Reserve", "credits Autopilot does not spend."},
            {"Surplus", "credits over the surplus line."},
            {"Endgame", "building for production and defense at high income."},
            {"Inspection points", "from AI cores; the Hegemony inspects over the line."}};
    /** The box on the Settings page for 30 days after a save moves to 0.3.0 (WS-F4). */
    static final String[] WHATS_NEW = {
            "20 main rows; Advanced has every older setting.",
            "Nothing you set changed.",
            "New: War level, faction stances, New colonies, What to buy."};
    static final float WHATS_NEW_DAYS = 30f;
    static final String SETUP_REST = "Everything else uses the recommended settings.";
    static final String RESET_NOTE = "Keeps Expansion style, War, New colonies, stances, Capital and Capital does.";

    /** The main rows' keys, in menu order (20 with every supported mod; JTMenuTextCheck). */
    static final String[] MAIN_KEYS = {"lowprofile", "p_spend", "p_extras", "p_newcol", "p_war", "reserve", "surplus", "richpreset", "endgame",
            "p_story", "p_station", "p_takedown", "p_policy", "p_cores", "p_admins", "capid", "logi", "spy", "amb", "screenlog"};
    /** Preset row keys to their CAPresets row. */
    static final Map<String, String> PRESET_KEYS = new LinkedHashMap<String, String>();
    static {
        PRESET_KEYS.put("p_spend", CAPresets.SPENDING);
        PRESET_KEYS.put("p_extras", CAPresets.EXTRAS);
        PRESET_KEYS.put("p_newcol", CAPresets.NEW_COLONIES);
        PRESET_KEYS.put("p_war", CAPresets.WAR);
        PRESET_KEYS.put("p_story", CAPresets.STORY);
        PRESET_KEYS.put("p_station", CAPresets.STATIONS);
        PRESET_KEYS.put("p_takedown", CAPresets.TAKE_DOWN);
        PRESET_KEYS.put("p_policy", CAPresets.POLICIES);
        PRESET_KEYS.put("p_cores", CAPresets.AI_CORES);
        PRESET_KEYS.put("p_admins", CAPresets.ADMINS);
    }
    /** Advanced subgroups: id, title. */
    static final String[][] ADVANCED = {
            {"adv_budget", "Budget"}, {"adv_rich", "Surplus and money ladder"}, {"adv_build", "Building"}, {"adv_cores", "AI cores and items"},
            {"adv_policy", "Colony policies"}, {"adv_people", "People and diplomacy"}, {"adv_war", "War"}, {"adv_capital", "Capital and storage"},
            {"adv_extras", "Extras"}, {"adv_expand", "New colonies"}, {"adv_ui", "Interface"}};

    // ---------------- groups ----------------

    /** Which groups are open (UI state only, not saved): one main group, or Advanced and one of its subgroups. */
    static final Set<String> open = new HashSet<String>(Arrays.asList("playstyle"));

    /** "budget" is what the first colony's setup page opened before 0.3.0; it opens Playstyle. */
    static boolean isOpen(String id) { return open.contains(id) || ("playstyle".equals(id) && open.contains("budget")); }

    static boolean group(TooltipMakerAPI t, String id, String title, String summary) {
        boolean isOpen = isOpen(id);
        ButtonAPI b = t.addButton((isOpen ? "[-]  " : "[+]  ") + title.toUpperCase(Locale.ROOT), PREFIX + "group:" + id,
                isOpen ? Misc.getHighlightColor() : Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.LMID, CutStyle.TL_BR, 300f, 24f, 10f);
        LabelAPI l = t.addPara(CALog.esc("// " + summary), Misc.getGrayColor(), 0f);
        l.getPosition().rightOfMid(b, 12f);
        CALog.endRow(t, b);
        return isOpen;
    }

    /** An Advanced subgroup's button, smaller than a group's (one subgroup open at a time). */
    static boolean subgroup(TooltipMakerAPI t, String id, String title) {
        boolean isOpen = open.contains(id);
        t.addButton((isOpen ? "   [-]  " : "   [+]  ") + title.toUpperCase(Locale.ROOT), PREFIX + "group:" + id,
                isOpen ? Misc.getHighlightColor() : Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.LMID, CutStyle.TL_BR, 260f, 20f, 6f);
        return isOpen;
    }

    static String onOff(boolean on) { return on ? "On" : "Off"; }

    static String lower(String v) { return v == null ? "" : v.toLowerCase(Locale.ROOT); }

    static boolean mod(String id) {
        try { return Global.getSettings().getModManager().isModEnabled(id); } catch (Throwable t) { return false; }
    }

    static boolean nex() { return mod(CASpies.MOD); }

    /** Factions that own markets and could receive an ambassador (not the player, not hidden factions). */
    static java.util.List<com.fs.starfarer.api.campaign.FactionAPI> ambassadorFactions() {
        Set<String> seen = new HashSet<String>();
        java.util.List<com.fs.starfarer.api.campaign.FactionAPI> out = new java.util.ArrayList<com.fs.starfarer.api.campaign.FactionAPI>();
        for (com.fs.starfarer.api.campaign.econ.MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            com.fs.starfarer.api.campaign.FactionAPI f = m.getFaction();
            if (f == null || m.isPlayerOwned() || f.isPlayerFaction() || !f.isShowInIntelTab() || !seen.add(f.getId())) continue;
            out.add(f);
        }
        java.util.Collections.sort(out, new java.util.Comparator<com.fs.starfarer.api.campaign.FactionAPI>() {
            public int compare(com.fs.starfarer.api.campaign.FactionAPI a, com.fs.starfarer.api.campaign.FactionAPI b) { return a.getDisplayName().compareTo(b.getDisplayName()); }
        });
        return out;
    }

    /** Total cost of each colony's next station step under the chosen station line (new station or next upgrade). */
    public static float stationLineItem(CASettings s) {
        float total = 0f;
        String starter = CASettings.stationStarter(s.stationLine);
        if (starter == null) starter = "orbitalstation";
        for (com.fs.starfarer.api.campaign.econ.MarketAPI m : Misc.getPlayerMarkets(false)) {
            try {
                com.fs.starfarer.api.campaign.econ.Industry station = null;
                for (com.fs.starfarer.api.campaign.econ.Industry i : m.getIndustries()) if (i.getSpec().hasTag("station")) station = i;
                String next = station == null ? starter : station.getSpec().getUpgrade();
                if (next == null) continue;
                total += Global.getSettings().getIndustrySpec(next).getNewPluginInstance(m).getBuildCost();
            } catch (Throwable ignored) { }
        }
        return total;
    }

    // ---------------- the Settings page ----------------

    public static void render(TooltipMakerAPI t, CASettings s, float width) {
        Color gray = Misc.getGrayColor(), hl = Misc.getHighlightColor();
        whatsNew(t);
        for (String[] g : GLOSSARY) t.addPara("%s: " + CALog.esc(g[1]), 2f, hl, g[0]);
        boolean nex = nex(), indevo = mod(CAPresets.INDEVO);

        if (group(t, "playstyle", "Playstyle", (s.lowProfile ? "low profile" : "full expansion") + ", "
                + lower(CAPresets.name(s, CAPresets.SPENDING)) + " spending")) {
            row(t, "Expansion style", s.lowProfile ? "Low profile" : "Full expansion", null, "lowprofile", true);
            desc(t, "lowprofile");
            preset(t, s, "Spending", "p_spend");
            preset(t, s, "Extras", "p_extras");
            newColonies(t, s);
            if (nex) war(t, s);
        }
        if (group(t, "money", "Money", "reserve " + Misc.getDGSCredits(s.reserve) + ", surplus " + lower(CASurplus.lineName(s)))) {
            reserve(t, s);
            row(t, "Surplus investment", CASurplus.lineName(s), CASettings.SURPLUS_NAMES[REC_SURPLUS], "surplus", true);
            desc(t, "surplus");
            row(t, "What to buy", CASettings.RICH_PRESET_NAMES[clamp(s.richPreset, CASettings.RICH_PRESET_NAMES.length)],
                    CASettings.RICH_PRESET_NAMES[0], "richpreset", true, richTip());
            desc(t, "richpreset", richTip());
            row(t, "Endgame", CAEndgame.NAMES[clamp(s.endgameMode, CAEndgame.NAMES.length)], CAEndgame.NAMES[0], "endgame", true);
            desc(t, "endgame");
            preset(t, s, "Story points", "p_story");
        }
        if (group(t, "building", "Building", lower(CAPresets.name(s, CAPresets.TAKE_DOWN)) + ", policies " + lower(CAPresets.name(s, CAPresets.POLICIES)))) {
            String[] st = stationTip(s);
            row(t, "Stations", CAPresets.name(s, CAPresets.STATIONS), CAPresets.recName(CAPresets.STATIONS), "p_station", true, st);
            desc(t, "p_station", st);
            preset(t, s, "Take down buildings", "p_takedown");
            preset(t, s, "Colony policies", "p_policy");
        }
        if (group(t, "people", "Items and people", "cores " + lower(CAPresets.name(s, CAPresets.AI_CORES)) + ", admins " + adminShort(s))) {
            preset(t, s, "AI cores", "p_cores");
            if (CAPresets.match(s, CAPresets.AI_CORES) == CAPresets.CORES_DEFENDED)
                t.addPara("Defended makes the Hegemony hostile.", Misc.getNegativeHighlightColor(), 2f);
            preset(t, s, "Administrators", "p_admins");
            if (CACapital.on(s)) row(t, "Capital", CACapital.name(s), "Autopilot picks", "capid", true);
            else row(t, "Capital", "None", "Autopilot picks", "capid", true);
            desc(t, "capid");
            row(t, "Storage logistics", s.logisticsMode == 1 ? "On" : "Off", null, "logi", true);
            desc(t, "logi");
        }
        if (group(t, "security", "Security and diplomacy", (nex ? "agents " + lower(CASettings.SPY_NAMES[clamp(s.spyMode, 3)]) + ", " : "")
                + lower(stanceSummary(s)))) {
            if (nex) {
                row(t, "Agents", CASettings.SPY_NAMES[clamp(s.spyMode, 3)] + (s.spyMode == 2 && s.lowProfile ? " (Low profile: defense only)" : ""),
                        "Defense", "spy", true);
                desc(t, "spy");
            }
            if (indevo) {
                row(t, "Ambassadors", s.disableAmbassadors ? "Off" : "Hire and install", "Hire and install", "amb", true);
                desc(t, "amb");
            }
            ButtonAPI b = t.addButton("OPEN", CANav.PREFIX + FACTIONS_PAGE, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.TL_BR, 86f, 20f, 4f);
            LabelAPI l = t.addPara("%s  ::  %s", 0f, hl, "FACTION STANCES", stanceSummary(s));
            l.getPosition().rightOfMid(b, 12f);
            CALog.endRow(t, b);
            desc(t, "stances");
        }
        if (group(t, "interface", "Interface", "log " + lower(CALog.SCREEN_NAMES[clamp(s.screenLog, 3)]))) {
            row(t, "On-screen log", CALog.SCREEN_NAMES[clamp(s.screenLog, 3)], "Every entry", "screenlog", true);
            desc(t, "screenlog");
        }
        if (group(t, "advanced", "Advanced", "each setting on its own")) {
            for (String[] sub : ADVANCED) {
                if ("adv_war".equals(sub[0]) && !nex) continue;
                if (subgroup(t, sub[0], sub[1])) advanced(t, s, sub[0]);
            }
        }
        t.addButton("Reset to recommended", PREFIX + "recommend:x", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.TL_BR, 260f, 24f, 14f);
        t.addPara(CALog.esc(RESET_NOTE), gray, 4f);
    }

    /**
     * 0.3.0.0 (WS-F1, DECISIONS 17): the first-time setup's rows: Spending, Extras, New colonies, War (Nexerelin) and the Credit
     * reserve, then one line that everything else is recommended. Expansion style was asked when the first colony was founded.
     */
    public static void renderFirstTime(TooltipMakerAPI t, CASettings s, float width) {
        preset(t, s, "Spending", "p_spend");
        preset(t, s, "Extras", "p_extras");
        newColonies(t, s);
        if (nex()) war(t, s);
        reserve(t, s);
        t.addPara(CALog.esc(SETUP_REST), Misc.getGrayColor(), 10f);
    }

    /** The box for 30 days after a save moved to 0.3.0 (CAData.migratedAt; new 0.3.0 saves never show it). */
    static void whatsNew(TooltipMakerAPI t) {
        try {
            long at = CAData.get().migratedAt;
            if (at == 0L || Global.getSector().getClock().getElapsedDaysSince(at) >= WHATS_NEW_DAYS) return;
        } catch (Throwable e) { return; }
        CATheme.heading(t, "What's new in 0.3.0", 6f);
        for (String p : WHATS_NEW) t.addPara(CALog.esc(p), Misc.getGrayColor(), 4f);
        CATheme.heading(t, "Settings", 10f);
    }

    static void preset(TooltipMakerAPI t, CASettings s, String label, String key) {
        String row = PRESET_KEYS.get(key);
        row(t, label, CAPresets.name(s, row), CAPresets.recName(row), key, true);
        desc(t, key);
    }

    static void newColonies(TooltipMakerAPI t, CASettings s) {
        String v = CAPresets.name(s, CAPresets.NEW_COLONIES);
        int m = CAPresets.match(s, CAPresets.NEW_COLONIES);
        // the money ladder's Expansion rung on Recommend only makes Auto ask first (CAExpansion.mode)
        if (m == 2 && CAExpansion.mode(s.expandMode, CAExpansion.lineIndex(s), s.rungMode(CARich.EXPANSION)) == 1) v += " (asks first: Expansion rung)";
        else if (m > 0 && CAExpansion.mode(s.expandMode, CAExpansion.lineIndex(s), s.rungMode(CARich.EXPANSION)) == 0) v += " (Expansion rung is Off)";
        row(t, "New colonies", v, CAPresets.recName(CAPresets.NEW_COLONIES), "p_newcol", true);
        desc(t, "p_newcol");
    }

    static void war(TooltipMakerAPI t, CASettings s) {
        String v = CAPresets.name(s, CAPresets.WAR);
        if (s.lowProfile && CAWarPolicy.clamp(s.warLevel) > CAWarPolicy.DEFEND) v += " (Low profile: Defend)";
        row(t, "War", v, CAPresets.recName(CAPresets.WAR), "p_war", true);
        desc(t, "p_war");
    }

    static void reserve(TooltipMakerAPI t, CASettings s) {
        row(t, "Credit reserve", Misc.getDGSCredits(s.reserve), "a quarter of your credits, 50,000-500,000", "reserve", false);
        desc(t, "reserve");
    }

    // ---------------- Advanced ----------------

    static void advanced(TooltipMakerAPI t, CASettings s, String id) {
        boolean vok = mod(CAPresets.VOK), indevo = mod(CAPresets.INDEVO), nex = nex();
        String on = "On", off = "Off", yes = "Yes";
        if ("adv_budget".equals(id)) {
            String auto = "Automatic";
            String type = s.budgetAuto ? auto : s.budgetPercentMode ? "Share of net income" : "Fixed amount";
            row(t, "Budget type", type, auto, "type", true);
            if (s.budgetAuto) {
                row(t, "From net income", Math.round(s.budgetPercent * 100f) + "%", "50%", "budget", false);
                row(t, "From savings", Math.round((s.savingsShare > 0f ? s.savingsShare : 0.15f) * 100f) + "% ("
                        + Misc.getDGSCredits(CABudget.fromSavings(s)) + " now)", "15%", "savings", false);
            } else if (s.budgetPercentMode) row(t, "From net income", Math.round(s.budgetPercent * 100f) + "% ("
                    + Misc.getDGSCredits(CABudget.limit(s)) + " now)", null, "budget", false);
            else row(t, "Monthly budget", Misc.getDGSCredits(s.budgetFixed), null, "budget", false);
            // 0.3.0.0 (WS-F1 fix, DECISIONS 13): the per-project cap is Automatic (follows net income and Spending) or Fixed
            row(t, "Per-project cap", s.projectCapFixed ? "Fixed" : auto + " (" + Misc.getDGSCredits(CAPresets.projectCap(s)) + " now)",
                    auto, "captype", true);
            if (s.projectCapFixed) row(t, "Fixed cap", Misc.getDGSCredits(s.perProjectCap), null, "cap", false);
            row(t, "New upkeep per month", s.upkeepCapAuto ? auto + ", " + Math.round(CAPresets.upkeepShare(s) * 100f) + "% ("
                    + Misc.getDGSCredits(CABudget.upkeepCap(s)) + " now)" : "Fixed", auto, "upkeeptype", true);
            if (!s.upkeepCapAuto) row(t, "Fixed upkeep", Misc.getDGSCredits(s.newUpkeepCap), null, "upkeep", false);
            row(t, "Pause on loss", onOff(s.pauseOnLoss), off, "pause", true);
            row(t, "Convenience fees", CASettings.FEE_NAMES[clamp(s.feeLevel, CASettings.FEE_NAMES.length)], CASettings.FEE_NAMES[2], "fees", true);
        } else if ("adv_rich".equals(id)) {
            String every = "Every colony";
            row(t, "Over " + Misc.getDGSCredits(CASurplus.RICH) + " of surplus", s.surplusFocusOnly ? "Focus world only" : every, every, "surplusrich", true);
            if (vok) row(t, "Wonders and gates", onOff(!s.noWonders), on, "wonders", true);
            if (mod("terraformingmadeeasy"))
                row(t, "Surplus terraforming", CASettings.SURPLUS_TF_NAMES[clamp(s.surplusTerraform, 3)], CASettings.SURPLUS_TF_NAMES[0], "surplustf", true);
            if (vok) row(t, "Surplus research", onOff(!s.noSurplusResearch), on, "surplusres", true);
            for (int r = CARich.STOCKPILES; r < CARich.RUNGS.length; r++)
                row(t, CARich.RUNGS[r] + " rung", CASettings.RUNG_MODE_NAMES[clamp(s.rungMode(r), 3)],
                        CASettings.RUNG_MODE_NAMES[CASettings.RUNG_DEFAULTS[r]], "rung_" + r, true, HOVER.get("rung"));
            row(t, "Shipyard stops at", s.shipyardStop + " unclaimed hulls", "6", "shipyardstop", false);
        } else if ("adv_build".equals(id)) {
            row(t, "Over-limit fix", onOff(!s.noOverLimitFix), on, "overlimit", true);
            row(t, "Exports valued by market share", onOff(!s.noMarketModel), on, "marketmodel", true);
            row(t, "Take down unpaying industries", onOff(!s.noPrune), on, "prune", true);
            row(t, "Endgame replaces industries", onOff(!s.endgameNoReplace), on, "endgamerep", true);
            row(t, "Allow removals", onOff(s.allowRemovals), off, "removals", true);
            if (s.storyPointRule == 2) row(t, "Story points a month", "" + s.storyPointMonthlyCap, "2", "spcap", false);
        } else if ("adv_cores".equals(id)) {
            row(t, "Use AI cores", onOff(s.useAICores), on, "cores", true);
            row(t, "Alpha cores", CASettings.ALPHA_NAMES[clamp(s.alphaMode, 3)], CASettings.ALPHA_NAMES[0], "alpha", true);
            row(t, "Inspection risk", CASettings.CORE_RISK_NAMES[clamp(s.coreRisk, 4)], CASettings.CORE_RISK_NAMES[1], "corerisk", true);
            row(t, "Hegemony inspections", CASettings.INSPECTION_NAMES[clamp(s.inspectionMode, 3)], CASettings.INSPECTION_NAMES[0], "inspect", true);
            if (vok) {
                row(t, "Alphas in research buildings", s.researchCoresByValue ? "By value" : "First", "First", "researchcores", true);
                row(t, "Research uses colony items", CASettings.RESEARCH_ITEM_NAMES[clamp(s.researchItemMode, 3)], CASettings.RESEARCH_ITEM_NAMES[0], "resitems", true);
            }
        } else if ("adv_policy".equals(id)) {
            String auto = CASettings.POLICY_NAMES[0];
            row(t, "Hazard pay", CASettings.POLICY_NAMES[clamp(s.hazardPayMode, 2)], auto, "hazard", true);
            if (s.hazardPayMode == 0) {
                row(t, "Hazard pay growth threshold", Math.round(s.hazardPayColonyShare * 100f) + "%", "25%", "hpcol", false);
                row(t, "Hazard pay budget", Math.round(s.hazardPayEmpireShare * 100f) + "% of net income", "30%", "hpemp", false);
            }
            row(t, "Free port", CASettings.POLICY_NAMES[clamp(s.freePortMode, 2)], auto, "freeport", true);
            if (indevo) row(t, "Senate edicts", onOff(!s.disableEdicts), on, "edicts", true);
        } else if ("adv_people".equals(id)) {
            row(t, "Administrator hiring", onOff(s.adminHiring), on, "admin", true);
            row(t, "Assign administrators optimally", onOff(s.adminOptimal), on, "adminopt", true);
            if (indevo || nex) {
                row(t, "Among equal stances, first", CASettings.FOCUS_NAMES[clamp(s.ambassadorFocus, 4)], CASettings.FOCUS_NAMES[0], "ambfocus", true);
                row(t, "Stop raising relations at", CASettings.STOP_NAMES[clamp(s.ambassadorStopAt, 4)], CASettings.STOP_NAMES[2], "ambstop", true);
            }
            if (nex) {
                row(t, "Recruit agents", onOff(!s.spyNoHiring), on, "spyhire", true);
                row(t, "Agents to keep", s.spyKeep == 0 ? "Auto" : "" + s.spyKeep, "Auto", "spykeep", true);
                if (s.spyMode == 2) row(t, "Offense target", spyTargetName(s.spyTarget), spyTargetName(null), "spytarget", true);
                row(t, "Autonomy", CASettings.AUTONOMY_NAMES[clamp(s.autonomyMode, 3)], CASettings.AUTONOMY_NAMES[1], "autonomy", true);
            }
        } else if ("adv_war".equals(id)) {
            row(t, "Emergency defense fleets", onOff(!s.disableDefenseFleets), on, "defense", true);
            row(t, "Retake your lost colonies", onOff(!s.noWarRetake), on, "warretake", true);
            row(t, "Top hostile threat as Rival", onOff(s.warAutoRivals), off, "warrivals", true);
            row(t, "Conquered colonies start in", CASettings.CONQUERED_NAMES[clamp(s.conqueredMode, CASettings.CONQUERED_NAMES.length)],
                    CASettings.CONQUERED_NAMES[0], "warconq", true);
            row(t, "Bombardment targets", CASettings.SCORCHED_TARGET_NAMES[clamp(s.scorchedTargets, CASettings.SCORCHED_TARGET_NAMES.length)],
                    CASettings.SCORCHED_TARGET_NAMES[0], "warscorch", true);
            row(t, "Coalition risk allowed", onOff(s.warCoalitionOK), off, "warcoal", true);
        } else if ("adv_capital".equals(id)) {
            if (CACapital.on(s)) {
                row(t, "Capital does", CACapital.MODE_NAMES[clamp(s.capitalMode, 2)], CACapital.MODE_NAMES[0], "capmode", true);
                if (s.capitalMode == 0) {
                    row(t, "Trade hub starts at", CACapital.START_NAMES[clamp(s.capitalStart, CACapital.START_NAMES.length)], CACapital.START_NAMES[0], "capstart", true);
                    row(t, "Make room for trade buildings", onOff(!s.capitalNoRoom), on, "caproom", true);
                }
                row(t, "Gather Storage at the capital", onOff(!s.capitalNoGather), on, "capgather", true);
            }
            row(t, "Refit hub", CALogistics.hubName(s.logisticsHub, true), null, "logihub", true);
            row(t, "Sell hub", CALogistics.hubName(s.logisticsSellHub, false), null, "logisell", true);
            row(t, "Ships to the refit hub", s.logisticsShips ? yes : "No", yes, "logiships", true);
            row(t, "Supplies, fuel, crew, marines too", s.logisticsSupplies ? yes : "No", yes, "logigoods", true);
        } else if ("adv_extras".equals(id)) {
            row(t, "Stable locations", CASettings.OBJECTIVE_NAMES[clamp(s.objectiveMode, 3)], CASettings.OBJECTIVE_NAMES[0], "objectives", true);
            row(t, "Measured mod buildings", CASettings.MEASURED_NAMES[clamp(s.measuredMode, 3)], CASettings.MEASURED_NAMES[0], "measured", true);
            if (mod(CAPresets.TOT)) row(t, "Trade contracts", CASettings.CONTRACT_NAMES[clamp(s.contractMode, 3)], CASettings.CONTRACT_NAMES[1], "contract", true);
            if (vok) {
                row(t, "Research", CASettings.CONTRACT_NAMES[clamp(s.researchMode, 3)], CASettings.CONTRACT_NAMES[1], "research", true);
                row(t, "Special project supply", CASettings.PROJECT_SUPPLY_NAMES[clamp(s.projectSupplyMode, 3)], CASettings.PROJECT_SUPPLY_NAMES[0], "projsupply", true);
                row(t, "Megastructures", CASettings.AUGMENT_NAMES[clamp(s.hypershuntMode, 3)], CASettings.AUGMENT_NAMES[0], "hs", true);
            }
            if (mod(CAPresets.SA)) row(t, "Station augments", CASettings.AUGMENT_NAMES[clamp(s.augmentMode, 3)], CASettings.AUGMENT_NAMES[0], "aug", true);
            if (mod(CAPresets.RAT)) {
                row(t, "RAT settlement", CASettings.SETTLE_NAMES[clamp(s.settlementMode, 4)], CASettings.SETTLE_NAMES[2], "settle", true);
                row(t, "Found when a site earns", Misc.getDGSCredits(s.settlementMinIncome), "20,000", "settleinc", false);
            }
            if (indevo) {
                row(t, "IndEvo specialties", onOff(!s.disableSpecialties), on, "spec", true);
                row(t, "VPC chips", onOff(!s.disableVPC), on, "vpc", true);
                row(t, "Forge may use up chips", s.disableForgeVPC ? "No" : yes, yes, "forge", true);
                row(t, "Courier shipping", onOff(!s.disableShipping), on, "ship", true);
            }
            if (mod(CAPresets.IX)) row(t, "Antimatter Stabilizers", onOff(!s.disableStabilizers), on, "stab", true);
        } else if ("adv_expand".equals(id)) {
            row(t, "Money for new colonies", CASettings.EXPAND_LINE_NAMES[clamp(s.expandLine, CASettings.EXPAND_LINE_NAMES.length)],
                    CASettings.EXPAND_LINE_NAMES[0], "expline", true);
            row(t, "Where", CASettings.EXPAND_SCOPE_NAMES[clamp(s.expandScope, CASettings.EXPAND_SCOPE_NAMES.length)]
                    + (s.lowProfile && s.expandScope > 0 ? " (Low profile: systems you hold)" : ""), CASettings.EXPAND_SCOPE_NAMES[0], "expscope", true);
            row(t, "Goal of a new colony", goalName(s.expandGoal), goalName(0), "expgoal", true);
            if (nex) row(t, "Method", CASettings.EXPAND_METHOD_NAMES[clamp(s.expandMethod, CASettings.EXPAND_METHOD_NAMES.length)],
                    CASettings.EXPAND_METHOD_NAMES[0], "expmethod", true);
            row(t, "Most colonies", CASettings.EXPAND_MAX_NAMES[clamp(s.expandMax, CASettings.EXPAND_MAX_NAMES.length)],
                    CASettings.EXPAND_MAX_NAMES[0], "expmax", true);
            row(t, "Fast buildout", onOff(s.expandBuildout()), on, "expbuildout", true);
            row(t, "Administrator past the limit", onOff(s.expandAdmins()), on, "expadmins", true);
            // 0.3.0.0 (WS-F1 fix): Auto stations only recommend while New colonies on Auto asks first (CAPresets.tascMode)
            if (CATasc.enabled()) row(t, "TASC stations", CASettings.TASC_STATION_NAMES[clamp(s.stationMode, 3)]
                    + (CAPresets.tascMode(s) != clamp(s.stationMode, 3) ? " (recommends: Expansion rung)" : ""), CASettings.TASC_STATION_NAMES[0], "tascst", true);
        } else if ("adv_ui".equals(id)) {
            row(t, "Announcement voice", CASettings.VOICE_NAMES[clamp(s.voice, 2)], CASettings.VOICE_NAMES[0], "voice", true);
            row(t, "Button on the colony screen", onOff(!s.noScreenButton), on, "screenbtn", true);
        }
    }

    // ---------------- hover text built from the game's state ----------------

    /** What to buy: each preset's rung order (CARich.ORDERS) and where what's left goes. */
    static String[] richTip() {
        String[] out = new String[CASettings.RICH_PRESET_NAMES.length + 1];
        for (int p = 0; p < CASettings.RICH_PRESET_NAMES.length; p++) {
            StringBuilder b = new StringBuilder(CASettings.RICH_PRESET_NAMES[p]).append(": ");
            int[] order = CARich.order(p);
            for (int i = 0; i < order.length; i++) b.append(i > 0 ? ", " : "").append(CARich.RUNGS[order[i]]);
            int rest = CARich.rest(p);
            b.append(". Left over: ").append(rest >= 0 ? CARich.RUNGS[rest] : "stays in your account").append('.');
            out[p] = b.toString();
        }
        out[out.length - 1] = "Each rung: Advanced, Surplus and money ladder.";
        return out;
    }

    static String[] stationTip(CASettings s) {
        return new String[]{"Battlestation from size 6, Star Fortress from 8, a size sooner at the capital, where they pay.",
                "Next tier everywhere: about " + Misc.getDGSCredits(stationLineItem(s)) + "."};
    }

    static String goalName(int goal) {
        CAData.Profile[] all = CAData.Profile.values();
        return goal >= 1 && goal <= all.length ? all[goal - 1].label : "Fits its plan";
    }

    static String adminShort(CASettings s) {
        int v = CAPresets.match(s, CAPresets.ADMINS);
        return v == 2 ? "on" : v == 1 ? "place only" : v == 0 ? "off" : "custom";
    }

    /** "2 Friendly, 1 Enemy", or "All Auto". */
    static String stanceSummary(CASettings s) {
        int[] n = new int[CAStance.NAMES.length];
        int set = 0;
        if (s.factionStance != null) for (Integer v : s.factionStance.values()) {
            if (v == null || v == CAStance.AUTO) continue;
            n[CAStance.clamp(v) + 3]++;
            set++;
        }
        if (set == 0) return "All Auto";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n.length; i++) if (n[i] > 0) b.append(b.length() > 0 ? ", " : "").append(n[i]).append(' ').append(CAStance.NAMES[i]);
        return b.toString();
    }

    static int clamp(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }

    // ---------------- the stance table (for the Factions page, WS-F2) ----------------

    /**
     * One stance row per faction that owns markets, sorted Ally to Destroy and then by name, and a Reset all to Auto button.
     * Its buttons are handled by handle() ("stance_<faction id>", "stancereset").
     */
    public static void renderStances(TooltipMakerAPI t, final CASettings s) {
        t.addPara(CALog.esc(DESC.get("stances") + " < friendlier, > more hostile."), Misc.getGrayColor(), 6f);
        java.util.List<com.fs.starfarer.api.campaign.FactionAPI> list = ambassadorFactions();
        java.util.Collections.sort(list, new java.util.Comparator<com.fs.starfarer.api.campaign.FactionAPI>() {
            public int compare(com.fs.starfarer.api.campaign.FactionAPI a, com.fs.starfarer.api.campaign.FactionAPI b) {
                int c = Integer.compare(s.stanceOf(a.getId()), s.stanceOf(b.getId()));
                return c != 0 ? c : a.getDisplayName().compareTo(b.getDisplayName());
            }
        });
        com.fs.starfarer.api.campaign.FactionAPI player = Global.getSector().getPlayerFaction();
        for (com.fs.starfarer.api.campaign.FactionAPI f : list) {
            float rel = player.getRelationship(f.getId());
            stanceRow(t, f.getDisplayName(), s.stanceOf(f.getId()), player.getRelationshipLevel(f.getId()).getDisplayName()
                    + " (" + Math.round(rel * 100f) + ")", "stance_" + f.getId());
        }
        t.addButton("Reset all to Auto", PREFIX + "stancereset:x", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.TL_BR, 200f, 22f, 10f);
    }

    // ---- offense target (agents): Whoever attacks you (null), a random enemy, or one faction at war with you

    static String spyTargetName(String t) {
        if (t == null || t.isEmpty()) return "Whoever attacks you";
        if ("random".equals(t)) return "A random enemy";
        com.fs.starfarer.api.campaign.FactionAPI f = Global.getSector().getFaction(t);
        return f == null ? "Whoever attacks you" : f.getDisplayName() + (f.isHostileTo("player") ? "" : " (not at war with you: none)");
    }

    /** Cycles Whoever attacks you, A random enemy, then each faction now at war with you (vanilla API only: this loads without Nexerelin). */
    static String nextSpyTarget(String cur) {
        java.util.List<String> order = new java.util.ArrayList<String>();
        order.add(null);
        order.add("random");
        for (com.fs.starfarer.api.campaign.FactionAPI f : Global.getSector().getAllFactions())
            if (!f.isPlayerFaction() && f.isShowInIntelTab() && f.isHostileTo("player")) order.add(f.getId());
        int i = order.indexOf(cur);
        return order.get((i + 1) % order.size());
    }

    static String stationName(String id) {
        for (int i = 0; i < CASettings.STATION_IDS.length; i++) if (CASettings.STATION_IDS[i].equals(id)) return CASettings.STATION_NAMES[i];
        return id;
    }

    // ---------------- rows ----------------

    /** One compact line: the buttons on the left, then "LABEL :: value", and "// rec ..." only when the value differs. */
    static ButtonAPI row(TooltipMakerAPI t, String label, String value, String rec, String key, boolean toggle) {
        return row(t, label, value, rec, key, toggle, HOVER.get(key));
    }

    static ButtonAPI row(TooltipMakerAPI t, String label, String value, String rec, String key, boolean toggle, String[] tip) {
        Color base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        ButtonAPI first, last;
        if (toggle) {
            first = last = t.addButton("CYCLE", PREFIX + key + ":next", base, dark, Alignment.MID, CutStyle.TL_BR, 86f, 20f, 4f);
        } else {
            first = t.addButton("-", PREFIX + key + ":-", base, dark, Alignment.MID, CutStyle.TL_BR, 40f, 20f, 4f);
            last = t.addButton("+", PREFIX + key + ":+", base, dark, Alignment.MID, CutStyle.TL_BR, 40f, 20f, 0f);
            last.getPosition().rightOfTop(first, 6f);
        }
        // 0.3.0.0 (WS-F1, research 04 section 3): the recommendation shows only when the value differs from it
        boolean showRec = rec != null && !rec.isEmpty() && !rec.equalsIgnoreCase(value);
        LabelAPI l = t.addPara("%s  ::  %s" + (showRec ? "   // rec " + CALog.esc(rec) : ""), 0f, Misc.getHighlightColor(),
                label.toUpperCase(Locale.ROOT), value);
        l.getPosition().rightOfMid(last, 12f);
        if (tip != null) t.addTooltipToPrevious(tip(tip), TooltipMakerAPI.TooltipLocation.BELOW);
        CALog.endRow(t, first);
        return first;
    }

    /** The main row's gray line, with the row's hover text. */
    static void desc(TooltipMakerAPI t, String key) { desc(t, key, HOVER.get(key)); }

    static void desc(TooltipMakerAPI t, String key, String[] tip) {
        String d = DESC.get(key);
        if (d == null) return;
        t.addPara(CALog.esc(d), Misc.getGrayColor(), 2f);
        if (tip != null) t.addTooltipToPrevious(tip(tip), TooltipMakerAPI.TooltipLocation.BELOW);
    }

    static TooltipMakerAPI.TooltipCreator tip(final String[] lines) {
        return new TooltipMakerAPI.TooltipCreator() {
            public boolean isTooltipExpandable(Object o) { return false; }
            public float getTooltipWidth(Object o) { return TIP_W; }
            public void createTooltip(TooltipMakerAPI tt, boolean expanded, Object o) {
                for (int i = 0; i < lines.length; i++) tt.addPara(CALog.esc(lines[i]), i == 0 ? 0f : 6f);
            }
        };
    }

    /** 0.3.0.0 (WS-E): one faction's stance line: < friendlier, > more hostile, the stance in its tone's colour, then the relation. */
    static void stanceRow(TooltipMakerAPI t, String label, int stance, String relation, String key) {
        Color base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        ButtonAPI lower = t.addButton("<", PREFIX + key + ":-", base, dark, Alignment.MID, CutStyle.TL_BR, 30f, 20f, 4f);
        ButtonAPI higher = t.addButton(">", PREFIX + key + ":+", base, dark, Alignment.MID, CutStyle.TL_BR, 30f, 20f, 0f);
        higher.getPosition().rightOfTop(lower, 4f);
        int tone = CAStance.tone(stance);
        Color c = tone > 0 ? Misc.getPositiveHighlightColor() : tone < 0 ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
        LabelAPI l = t.addPara("%s  ::  %s   // now " + CALog.esc(relation), 0f, new Color[]{Misc.getHighlightColor(), c},
                label.toUpperCase(Locale.ROOT), CAStance.name(stance).toUpperCase(Locale.ROOT));
        l.getPosition().rightOfMid(higher, 12f);
        CALog.endRow(t, lower);
    }

    // ---------------- buttons ----------------

    /** Handles a settings button; returns true if the id was a settings button. */
    public static boolean handle(Object id, CASettings s) {
        if (!(id instanceof String) || !((String) id).startsWith(PREFIX)) return false;
        String[] a = ((String) id).substring(PREFIX.length()).split(":");
        String k = a[0], op = a.length > 1 ? a[1] : "next";
        float dir = "-".equals(op) ? -1f : 1f;
        if (k.equals("group")) {
            String g = a.length > 1 ? a[1] : "";
            if (g.startsWith("adv_")) {   // 0.3.0.0 (WS-F1): one Advanced subgroup open at a time, inside Advanced
                boolean was = open.contains(g);
                open.clear();
                open.add("advanced");
                if (!was) open.add(g);
            } else {   // one group open at a time, so the list stays short
                boolean was = isOpen(g);
                open.clear();
                if (!was) open.add(g);
            }
            return true;
        }
        CACache.bump("settings");   // 0.3.0.0 (A4): kept plans, evaluations and build menus are worked out again after a settings change
        if (k.equals("recommend")) resetToRecommended(s);   // 0.3.0.0 (WS-F1): Reset to recommended
        else if (PRESET_KEYS.containsKey(k)) applyPreset(s, PRESET_KEYS.get(k), CAPresets.next(s, PRESET_KEYS.get(k)));
        else if (k.equals("type")) {   // Automatic -> Share of net income -> Fixed amount -> Automatic
            if (s.budgetAuto) { s.budgetAuto = false; s.budgetPercentMode = true; }
            else if (s.budgetPercentMode) s.budgetPercentMode = false;
            else s.budgetAuto = true;
        }
        else if (k.equals("budget")) {
            if (s.budgetAuto || s.budgetPercentMode) s.budgetPercent = Math.max(0.1f, Math.min(1f, s.budgetPercent + 0.1f * dir));
            else s.budgetFixed = Math.max(0f, s.budgetFixed + 250000f * dir);
        }
        else if (k.equals("savings")) s.savingsShare = Math.max(0.05f, Math.min(0.5f, (s.savingsShare > 0f ? s.savingsShare : 0.15f) + 0.05f * dir));
        else if (k.equals("cap")) {   // 0.3.0.0 (WS-F1 fix): a number you set makes the cap Fixed, starting from the cap in effect
            if (!s.projectCapFixed) { s.perProjectCap = CAPresets.projectCap(s); s.projectCapFixed = true; }
            s.perProjectCap = Math.max(250000f, s.perProjectCap + 500000f * dir);
        }
        else if (k.equals("captype")) {   // 0.3.0.0 (WS-F1 fix): Automatic <-> Fixed; Fixed starts from the cap in effect now
            if (!s.projectCapFixed) s.perProjectCap = CAPresets.projectCap(s);
            s.projectCapFixed = !s.projectCapFixed;
        }
        else if (k.equals("reserve")) s.reserve = Math.max(0f, s.reserve + 50000f * dir);
        else if (k.equals("upkeep")) s.newUpkeepCap = Math.max(0f, s.newUpkeepCap + 10000f * dir);
        else if (k.equals("upkeeptype")) {   // Automatic <-> Fixed; Fixed starts from today's automatic amount, not a stale one
            if (s.upkeepCapAuto) s.newUpkeepCap = round(CABudget.upkeepCap(s), 10000f);
            s.upkeepCapAuto = !s.upkeepCapAuto;
            s.upkeepCapChecked = true;
        }
        else if (k.equals("pause")) s.pauseOnLoss = !s.pauseOnLoss;
        else if (k.equals("surplus")) { s.surplusLine = (Math.max(0, s.surplusLine) + 1) % CASettings.SURPLUS_LINES.length; s.surplusChecked = true; }
        else if (k.equals("surplustf")) { s.surplusTerraform = (Math.max(0, s.surplusTerraform) + 1) % CASettings.SURPLUS_TF_NAMES.length; CATerraSim.reset(); }
        else if (k.equals("fees")) s.feeLevel = (s.feeLevel + 1) % 4;
        else if (k.equals("station")) {   // the 0.2.45 station line row; the Stations preset row (p_station) replaced it
            int i = 0, n = CASettings.STATION_IDS.length;
            for (int j = 0; j < n; j++) if (CASettings.STATION_IDS[j].equals(s.stationLine)) i = j;
            for (int step = 1; step <= n; step++) {   // the next line whose station the game has (Imperium only with Interstellar Imperium)
                String next = CASettings.STATION_IDS[(i + step) % n];
                if ("score".equals(next) || CASettings.stationStarter(next) != null) { s.stationLine = next; break; }
            }
        }
        else if (k.equals("cores")) s.useAICores = !s.useAICores;
        else if (k.equals("removals")) s.allowRemovals = !s.allowRemovals;
        else if (k.equals("alpha")) s.alphaMode = (Math.max(0, Math.min(2, s.alphaMode)) + 1) % 3;
        else if (k.equals("researchcores")) s.researchCoresByValue = !s.researchCoresByValue;
        else if (k.equals("projsupply")) { s.projectSupplyMode = (Math.max(0, Math.min(2, s.projectSupplyMode)) + 1) % 3; CAProjects.computedAt = -99f; }
        else if (k.equals("surplusres")) s.noSurplusResearch = !s.noSurplusResearch;
        else if (k.equals("surplusrich")) s.surplusFocusOnly = !s.surplusFocusOnly;
        else if (k.equals("wonders")) s.noWonders = !s.noWonders;
        else if (k.equals("endgame")) s.endgameMode = (Math.max(0, Math.min(CAEndgame.NAMES.length - 1, s.endgameMode)) + 1) % CAEndgame.NAMES.length;
        else if (k.equals("endgamerep")) s.endgameNoReplace = !s.endgameNoReplace;
        else if (k.equals("inspect")) s.inspectionMode = (Math.max(0, Math.min(2, s.inspectionMode)) + 1) % 3;
        else if (k.equals("corerisk")) s.coreRisk = (Math.max(0, Math.min(3, s.coreRisk)) + 1) % 4;
        else if (k.equals("sp")) s.storyPointRule = (s.storyPointRule + 1) % 3;
        else if (k.equals("spcap")) s.storyPointMonthlyCap = Math.max(0, s.storyPointMonthlyCap + (int) dir);
        else if (k.equals("admin")) s.adminHiring = !s.adminHiring;
        else if (k.equals("adminopt")) s.adminOptimal = !s.adminOptimal;
        else if (k.equals("amb")) s.disableAmbassadors = !s.disableAmbassadors;
        else if (k.equals("vpc")) s.disableVPC = !s.disableVPC;
        else if (k.equals("forge")) s.disableForgeVPC = !s.disableForgeVPC;
        else if (k.equals("edicts")) s.disableEdicts = !s.disableEdicts;
        else if (k.equals("ship")) s.disableShipping = !s.disableShipping;
        else if (k.equals("spec")) s.disableSpecialties = !s.disableSpecialties;
        else if (k.equals("stab")) s.disableStabilizers = !s.disableStabilizers;
        else if (k.equals("defense")) s.disableDefenseFleets = !s.disableDefenseFleets;
        else if (k.equals("war")) s.warLevel = (CAWarPolicy.clamp(s.warLevel) + 1) % CAWarPolicy.LEVEL_NAMES.length;   // 0.3.0.0 (WS-E): War level
        else if (k.equals("warretake")) s.noWarRetake = !s.noWarRetake;
        else if (k.equals("warrivals")) s.warAutoRivals = !s.warAutoRivals;
        else if (k.equals("warconq")) s.conqueredMode = (Math.max(0, Math.min(CASettings.CONQUERED_NAMES.length - 1, s.conqueredMode)) + 1) % CASettings.CONQUERED_NAMES.length;
        else if (k.equals("warscorch")) s.scorchedTargets = (Math.max(0, Math.min(CASettings.SCORCHED_TARGET_NAMES.length - 1, s.scorchedTargets)) + 1) % CASettings.SCORCHED_TARGET_NAMES.length;
        else if (k.equals("warcoal")) s.warCoalitionOK = !s.warCoalitionOK;
        else if (k.equals("ambfocus")) s.ambassadorFocus = (Math.max(0, Math.min(3, s.ambassadorFocus)) + 1) % 4;
        else if (k.equals("ambstop")) s.ambassadorStopAt = (Math.max(0, Math.min(3, s.ambassadorStopAt)) + 1) % 4;
        else if (k.startsWith("stance_")) {   // 0.3.0.0 (WS-E): the stance table (CAStance); Auto removes the entry
            String fid = k.substring("stance_".length());
            int st = CAStance.step(s.stanceOf(fid), "-".equals(op) ? -1 : 1);
            if (st == CAStance.AUTO) s.factionStance().remove(fid); else s.factionStance().put(fid, st);
            CAStance.markSetByYou(fid);   // 0.3.0.0 (WS-E fix): a stance you set acts fully in diplomacy (CAStance.fromOldPriority)
        }
        else if (k.equals("stancereset")) {   // 0.3.0.0 (WS-F1): Reset all to Auto, each one marked as set by you
            for (String fid : new java.util.ArrayList<String>(s.factionStance().keySet())) CAStance.markSetByYou(fid);
            s.factionStance().clear();
        }
        else if (k.equals("hazard")) s.hazardPayMode = s.hazardPayMode == 0 ? 1 : 0;
        else if (k.equals("hpcol")) s.hazardPayColonyShare = Math.max(0.05f, Math.min(1f, s.hazardPayColonyShare + 0.05f * dir));
        else if (k.equals("hpemp")) s.hazardPayEmpireShare = Math.max(0.01f, Math.min(1f, s.hazardPayEmpireShare + 0.01f * dir));
        else if (k.equals("freeport")) s.freePortMode = s.freePortMode == 0 ? 1 : 0;
        else if (k.equals("settle")) s.settlementMode = (Math.max(0, Math.min(3, s.settlementMode)) + 1) % 4;
        else if (k.equals("measured")) s.measuredMode = (Math.max(0, Math.min(2, s.measuredMode)) + 1) % 3;
        else if (k.equals("aug")) s.augmentMode = (Math.max(0, Math.min(2, s.augmentMode)) + 1) % 3;
        else if (k.equals("contract")) s.contractMode = (Math.max(0, Math.min(2, s.contractMode)) + 1) % 3;
        else if (k.equals("hs")) s.hypershuntMode = (Math.max(0, Math.min(2, s.hypershuntMode)) + 1) % 3;
        else if (k.equals("research")) s.researchMode = (Math.max(0, Math.min(2, s.researchMode)) + 1) % 3;
        else if (k.equals("resitems")) { s.researchItemMode = (Math.max(0, Math.min(2, s.researchItemMode)) + 1) % 3; CAResearch.updateItemMode(s); CAResearch.cached = null; }
        else if (k.equals("screenbtn")) s.noScreenButton = !s.noScreenButton;
        else if (k.equals("screenlog")) s.screenLog = (Math.max(0, Math.min(2, s.screenLog)) + 1) % 3;
        else if (k.equals("voice")) s.voice = s.voice == 0 ? 1 : 0;
        else if (k.equals("spy")) s.spyMode = (Math.max(0, Math.min(2, s.spyMode)) + 1) % 3;
        else if (k.equals("spyhire")) s.spyNoHiring = !s.spyNoHiring;
        else if (k.equals("autonomy")) s.autonomyMode = (Math.max(0, Math.min(2, s.autonomyMode)) + 1) % 3;
        else if (k.equals("spykeep")) s.spyKeep = (Math.max(0, Math.min(4, s.spyKeep)) + 1) % 5;
        else if (k.equals("spytarget")) s.spyTarget = nextSpyTarget(s.spyTarget);
        else if (k.equals("logi")) s.logisticsMode = s.logisticsMode == 1 ? 0 : 1;
        else if (k.equals("objectives")) s.objectiveMode = (Math.max(0, Math.min(2, s.objectiveMode)) + 1) % 3;
        else if (k.equals("tascst")) s.stationMode = (Math.max(0, Math.min(2, s.stationMode)) + 1) % 3;
        else if (k.equals("logihub")) s.logisticsHub = CALogistics.next(s.logisticsHub, false);
        else if (k.equals("logisell")) s.logisticsSellHub = CALogistics.next(s.logisticsSellHub, true);
        else if (k.equals("logiships")) s.logisticsShips = !s.logisticsShips;
        else if (k.equals("logigoods")) s.logisticsSupplies = !s.logisticsSupplies;
        else if (k.equals("capid")) {   // 0.3.0.0 (WS-F1): Autopilot picks, each colony, then None (capitalMode 2), then Autopilot picks
            // 0.3.0.0 (WS-F1 fix): leaving None restores the Capital does in effect before it (an older save's None: Deliveries and commerce)
            if (s.capitalMode == 2) { s.capitalMode = s.capitalModeBeforeNone == 1 ? 1 : 0; s.capitalId = null; }
            else {
                String next = CACapital.next(s.capitalId);
                if (next == null) { s.capitalModeBeforeNone = s.capitalMode; s.capitalId = null; s.capitalMode = 2; }
                else { s.capitalId = next; s.capitalAuto = next; }
            }
            CACapital.reset();
        }
        else if (k.equals("capmode")) { if (s.capitalMode != 2) s.capitalMode = s.capitalMode == 0 ? 1 : 0; CACapital.reset(); }   // None is on the Capital row
        else if (k.equals("capstart")) s.capitalStart = (Math.max(0, Math.min(CACapital.START_NAMES.length - 1, s.capitalStart)) + 1) % CACapital.START_NAMES.length;
        else if (k.equals("caproom")) s.capitalNoRoom = !s.capitalNoRoom;
        else if (k.equals("stationplan")) s.noStationPlan = !s.noStationPlan;
        else if (k.equals("marketmodel")) { s.noMarketModel = !s.noMarketModel; CAMarket.invalidate(); CAAuto.nextPass.clear(); }
        else if (k.equals("overlimit")) s.noOverLimitFix = !s.noOverLimitFix;
        else if (k.equals("prune")) s.noPrune = !s.noPrune;
        else if (k.equals("lowprofile")) CALowProfile.set(s, !s.lowProfile, "Settings");
        else if (k.equals("capgather")) s.capitalNoGather = !s.capitalNoGather;
        else if (k.equals("settleinc")) s.settlementMinIncome = Math.max(5000f, s.settlementMinIncome + 5000f * dir);
        // 0.3.0.0 (WS-F1): the 0.3.0 settings that had no row yet (money ladder, Auto-expansion)
        else if (k.equals("richpreset")) s.richPreset = (clamp(s.richPreset, CASettings.RICH_PRESET_NAMES.length) + 1) % CASettings.RICH_PRESET_NAMES.length;
        else if (k.startsWith("rung_")) {
            int r = -1;
            try { r = Integer.parseInt(k.substring("rung_".length())); } catch (NumberFormatException ignored) { }
            if (r > CARich.OBLIGATIONS && r < CARich.RUNGS.length) {
                if (s.rungMode == null || s.rungMode.length < CARich.RUNGS.length) s.fixNulls();
                s.rungMode[r] = (clamp(s.rungMode(r), CASettings.RUNG_MODE_NAMES.length) + 1) % CASettings.RUNG_MODE_NAMES.length;
            }
        }
        else if (k.equals("shipyardstop")) s.shipyardStop = Math.max(1, Math.min(30, s.shipyardStop + (int) dir));
        else if (k.equals("expline")) s.expandLine = (clamp(s.expandLine, CASettings.EXPAND_LINE_NAMES.length) + 1) % CASettings.EXPAND_LINE_NAMES.length;
        else if (k.equals("expscope")) s.expandScope = (clamp(s.expandScope, CASettings.EXPAND_SCOPE_NAMES.length) + 1) % CASettings.EXPAND_SCOPE_NAMES.length;
        else if (k.equals("expgoal")) s.expandGoal = (clamp(s.expandGoal, CAData.Profile.values().length + 1) + 1) % (CAData.Profile.values().length + 1);
        else if (k.equals("expmethod")) s.expandMethod = (clamp(s.expandMethod, CASettings.EXPAND_METHOD_NAMES.length) + 1) % CASettings.EXPAND_METHOD_NAMES.length;
        else if (k.equals("expmax")) s.expandMax = (clamp(s.expandMax, CASettings.EXPAND_MAX_NAMES.length) + 1) % CASettings.EXPAND_MAX_NAMES.length;
        else if (k.equals("expbuildout")) s.noExpandBuildout = !s.noExpandBuildout;
        else if (k.equals("expadmins")) s.noExpandAdmins = !s.noExpandAdmins;
        return true;
    }

    /** 0.3.0.0 (WS-F1): a preset row's click: CAPresets writes the fields, then the caches those fields feed are dropped. */
    static void applyPreset(CASettings s, String row, int value) {
        CAPresets.apply(s, row, value);
        if (CAPresets.SPENDING.equals(row)) CATerraSim.reset();                // surplus terraforming may have changed
        else if (CAPresets.EXTRAS.equals(row)) CAProjects.computedAt = -99f;   // special project supply may have changed
    }
}
