package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

/** Settings rows with recommendations, used by the first-time setup window and the empire panel's settings section. */
public class CASetupUI {
    public static final String PREFIX = "ca:set:";
    /** Recommended surplus line: over 3,000,000. */
    static final int REC_SURPLUS = 2;

    static float round(float v, float step) { return Math.max(step, Math.round(v / step) * step); }

    /**
     * The recommended settings: what a player who only switches colonies to Auto should get. Everything that pays for
     * itself and can't hurt is on; choices that are hard to undo or are really the player's (removing buildings, story
     * points, offense) stay off. Scaled to the empire's income and credits now.
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
        s.newUpkeepCap = round(Math.max(20000f, net * 0.1f), 10000f);   // only used if you switch to Fixed
        s.upkeepCapAuto = true;    // follows net income: 10% of last month's, at least 20,000
        s.upkeepCapChecked = true;
        s.surplusLine = REC_SURPLUS;   // credits over 3,000,000 go into one focus world
        s.surplusChecked = true;
        s.surplusTerraform = 0;        // what the focus world leaves of it goes into terraforming, as much output as it buys
        s.noSurplusResearch = false;   // and into more Research Facilities while research left is worth the speed
        s.surplusFocusOnly = false;    // past 10,000,000 over the line, every colony spends it
        s.noWonders = false;           // and builds the Daedalus Array and Bifrost gates once their goods are made
        s.endgameMode = 0;             // from 1,000,000 a month while spending freely, money stops being the goal
        s.endgameNoReplace = false;    // and weak industries make way for production chains, fleets and defense
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
        s.adminDelay = true;
        s.disableAmbassadors = false;
        // policies and mod automation: each one only acts when it pays
        s.hazardPayMode = 0;
        s.freePortMode = 0;
        s.hazardPayColonyShare = 0.25f;   // hazard pay where it speeds growth up by at least 25%
        s.hazardPayEmpireShare = 0.30f;   // all of it within 30% of empire net income: the more you earn, the more colonies get it
        s.disableEdicts = false;
        s.disableDefenseFleets = false;
        s.warMode = 0;          // protect and strike back: security only, never invasions
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

    /** Which group is expanded (UI state only, not saved); at most one. Budget starts open. */
    static final java.util.Set<String> open = new java.util.HashSet<String>(java.util.Arrays.asList("budget"));

    static boolean group(TooltipMakerAPI t, String id, String title, String summary) {
        boolean isOpen = open.contains(id);
        ButtonAPI b = t.addButton((isOpen ? "[-]  " : "[+]  ") + title.toUpperCase(java.util.Locale.ROOT), PREFIX + "group:" + id,
                isOpen ? Misc.getHighlightColor() : Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.LMID, CutStyle.TL_BR, 300f, 24f, 10f);
        com.fs.starfarer.api.ui.LabelAPI l = t.addPara(CALog.esc("// " + summary), Misc.getGrayColor(), 0f);
        l.getPosition().rightOfMid(b, 12f);
        CALog.endRow(t, b);
        return isOpen;
    }

    static String onOff(boolean on) { return on ? "On" : "Off"; }

    /** Factions that own markets and could receive an ambassador (not the player, not hidden factions). */
    static java.util.List<com.fs.starfarer.api.campaign.FactionAPI> ambassadorFactions() {
        java.util.Set<String> seen = new java.util.HashSet<String>();
        java.util.List<com.fs.starfarer.api.campaign.FactionAPI> out = new java.util.ArrayList<com.fs.starfarer.api.campaign.FactionAPI>();
        for (com.fs.starfarer.api.campaign.econ.MarketAPI m : com.fs.starfarer.api.Global.getSector().getEconomy().getMarketsCopy()) {
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
                total += com.fs.starfarer.api.Global.getSettings().getIndustrySpec(next).getNewPluginInstance(m).getBuildCost();
            } catch (Throwable ignored) { }
        }
        return total;
    }

    public static void render(TooltipMakerAPI t, CASettings s, float width) {
        String budget = s.budgetAuto ? Misc.getDGSCredits(CABudget.limit(s)) + " this month"
                : s.budgetPercentMode ? Math.round(s.budgetPercent * 100f) + "% of net income (" + Misc.getDGSCredits(CABudget.limit(s)) + " now)"
                : Misc.getDGSCredits(s.budgetFixed);
        String type = s.budgetAuto ? "Automatic" : s.budgetPercentMode ? "Share of net income" : "Fixed amount";

        if (group(t, "style", "Expansion style", s.lowProfile ? "low profile" : "full expansion")) {
            row(t, "Expansion style", s.lowProfile ? "Low profile" : "Full expansion", "Your call", "lowprofile", true);
            t.addPara("Full expansion builds whatever pays most and prices colony crises at their usual cost. Low profile keeps your colonies "
                    + "growing but stays under the radar: attention counts ten times as much, no AI cores above size 3, nothing that takes a "
                    + "colony to 7 Pather interest, no free ports, no raids on other factions' colonies or sabotage by agents, and inspections "
                    + "are never resisted. Exports competing with Tri-Tachyon, fuel from 4 units and farming-only habitable colonies (Luddic "
                    + "majorities) are priced in. Each faction's rule lifts once its crisis is over for good. Pirates and the Persean League "
                    + "count colony size, so nothing avoids them; Low profile defends against them sooner. It earns less.", Misc.getGrayColor(), 4f);
        }
        if (group(t, "budget", "Budget and spending", budget + ", reserve " + Misc.getDGSCredits(s.reserve) + ", fees " + CASettings.FEE_NAMES[s.feeLevel])) {
            row(t, "Budget type", type, "Automatic", "type", true);
            if (s.budgetAuto) {
                row(t, "From net income", Math.round(s.budgetPercent * 100f) + "% of last month's", "50%", "budget", false);
                row(t, "From savings", Math.round((s.savingsShare > 0f ? s.savingsShare : 0.15f) * 100f) + "% of credits above the reserve, each month ("
                        + Misc.getDGSCredits(CABudget.fromSavings(s)) + " now)", "15%", "savings", false);
            } else row(t, "Monthly budget", budget, "use the Automatic type", "budget", false);
            row(t, "Per-project cap", Misc.getDGSCredits(s.perProjectCap), "monthly net income, 1M-5M", "cap", false);
            row(t, "Credit reserve", Misc.getDGSCredits(s.reserve), "a quarter of your credits, 50,000-500,000", "reserve", false);
            row(t, "New upkeep cap per month", s.upkeepCapAuto ? "Automatic: 10% of last month's net income, at least 20,000 ("
                    + Misc.getDGSCredits(CABudget.upkeepCap(s)) + " now)" : "Fixed", "Automatic", "upkeeptype", true);
            if (!s.upkeepCapAuto) row(t, "Fixed new upkeep cap", Misc.getDGSCredits(s.newUpkeepCap) + " a month", "use Automatic", "upkeep", false);
            row(t, "Surplus investment", CASurplus.lineName(s), CASettings.SURPLUS_NAMES[REC_SURPLUS], "surplus", true);
            t.addPara("Credits over the line go into one focus world, to make it the best colony it can be: past the monthly budget and the "
                    + "per-project cap, new upkeep up to a tenth of net income a month, prices counted at a quarter, its terraforming paid from "
                    + "the surplus, and research counting its plan twice. Pick the focus world on its colony panel, or let Autopilot pick "
                    + "the Auto colony with the most to build on.", Misc.getGrayColor(), 4f);
            row(t, "Over " + Misc.getDGSCredits(CASurplus.RICH) + " of surplus", s.surplusFocusOnly ? "Focus world only" : "Every colony spends it",
                    "Every colony spends it", "surplusrich", true);
            t.addPara("Past that, one focus world can't spend it all, so every Approve and Auto colony spends from the surplus: past the monthly "
                    + "budget and the per-project cap, prices at a quarter, credits counting a quarter in what a building is worth (so what adds "
                    + "defense, stability, growth or access goes up even if it costs more than it earns), new upkeep each month up to a sixtieth "
                    + "of the surplus (paid from the pool, not from income), hazard pay wherever it speeds growth, and up to three terraforming "
                    + "projects at once per colony.", Misc.getGrayColor(), 4f);
            row(t, "Endgame economy", CAEndgame.NAMES[Math.max(0, Math.min(CAEndgame.NAMES.length - 1, s.endgameMode))], CAEndgame.NAMES[0], "endgame", true);
            if (s.endgameMode < CAEndgame.ENTER_NET.length) {
                row(t, "Endgame replaces industries", onOff(!s.endgameNoReplace), "On", "endgamerep", true);
                t.addPara("Spending freely with net income at that level, money stops being the goal: a credit counts 5%%, fleets, ship quality "
                        + "and defense count more, and what a building makes is valued as production chains: a good none of your colonies "
                        + "make is worth most, then covering your own shortfalls, then a bigger share of the sector's production, spread over "
                        + "more than one world; war goods count 1.5x and Ashes of the Domain's advanced goods 2x. Research Facilities go up "
                        + "while any research is left, and AI cores and items go where they add most to fleets and defense. A money plan's "
                        + "Commerce or Light Industry no longer gets plan priority. With no free industry slot, an Auto colony may take down "
                        + "its weakest industry for a better one (one a day, one a month per colony; never a military, plan, project or improved one, "
                        + "one you locked, nor a good's last producer while you'd be short; its core and item go to Autopilot Storage). It "
                        + "gives up income only down to a third of the net income it started at.", Misc.getGrayColor(), 4f);
            }
            if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled(CAResearch.MOD)) {
                row(t, "Wonders and gates while spending freely", onOff(!s.noWonders), "On", "wonders", true);
                t.addPara("The Daedalus Array (Grand Wonder) and the Bifrost network: the Control Center becomes a plan step, so research works "
                        + "toward it, then a gate goes up in each of your systems. AotD takes their construction goods from your exports every "
                        + "month, so each starts only once your colonies make every good it needs. The Garden of Elysium and Star Citadels stay yours.",
                        Misc.getGrayColor(), 4f);
            }
            if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy")) {
                row(t, "Surplus terraforming", CASettings.SURPLUS_TF_NAMES[Math.max(0, Math.min(2, s.surplusTerraform))], "Max output", "surplustf", true);
                t.addPara("What the focus world leaves of the surplus goes into Terraforming Made Easy projects at every Auto colony, most value "
                        + "per credit first: richer deposits and farmland, harsh conditions cleared, the structure built first when needed. "
                        + "Planned on TME's own rules, so a project that loses more than it gains (extreme tectonic activity takes a habitable "
                        + "world's farmland and organics) never goes, and nothing a colony's plan needs is undone. Max output: anything worth "
                        + "1,000 a month or more after the structure's upkeep. Paid from the surplus only, outside the monthly budget.", Misc.getGrayColor(), 4f);
            }
            if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled(CAResearch.MOD)) {
                row(t, "Surplus research", onOff(!s.noSurplusResearch), "On", "surplusres", true);
                t.addPara("The surplus also builds Research Facilities at Auto colonies that have no research building, one at a time, up to the "
                        + CASurplusResearch.MAX + " Vaults of Knowledge counts (each past the first is +10%% research speed), while the research "
                        + "left is worth more than a quarter of the upkeep (10,000 a month plus 10,000 x the tech's tier, half with an Alpha core). "
                        + "Worlds with a pre-collapse facility go first. The ones it built come down once there's nothing left to research.",
                        Misc.getGrayColor(), 4f);
            }
            row(t, "Pause on loss", onOff(s.pauseOnLoss), "Off", "pause", true);
            row(t, "Convenience fees", CASettings.FEE_NAMES[s.feeLevel], "Standard", "fees", true);
            float st = stationLineItem(s);
            t.addPara("Station line item: about %s to give each of your colonies its next station tier (" + CALog.esc(stationName(s.stationLine))
                    + "), within about 2-3 months of budget.", 8f, Misc.getHighlightColor(), Misc.getDGSCredits(st));
        }
        if (group(t, "build", "Building and planning", "stations: " + stationName(s.stationLine) + ", removals " + onOff(s.allowRemovals).toLowerCase()
                + ", story points: " + CASettings.SP_NAMES[s.storyPointRule])) {
            row(t, "Station line", stationName(s.stationLine), "Pick by score", "station", true);
            row(t, "Station at every colony", s.noStationPlan ? "No" : "Yes", "Yes", "stationplan", true);
            row(t, "Exports valued by market share", onOff(!s.noMarketModel), "On", "marketmodel", true);
            if (!s.noMarketModel) t.addPara("A colony's exports earn its share of each good's sector market, so the more of a market your "
                    + "colonies already hold, the less one more producer adds, and a good few others make is worth the most. Buildings count "
                    + "what they'd add to that share, only what's above the colony's own biggest source of the good, and a building under "
                    + "construction counts as made. Off: every unit of anything is worth one flat rate, as before 0.2.44.0.", Misc.getGrayColor(), 4f);
            if (!s.noStationPlan) t.addPara("A station fights next to your fleets and anything raiding, bombarding or invading has to beat it first. "
                    + "Every colony plans one from size 4 (or half a year old) once its fast buildout is done, a Battlestation from size 6 and a "
                    + "Star Fortress from size 8 (a size sooner at the capital).", Misc.getGrayColor(), 4f);
            row(t, "Fix colonies over the industry limit", s.noOverLimitFix ? "No" : "Yes", "Yes", "overlimit", true);
            if (!s.noOverLimitFix) t.addPara("More industries than slots costs 5 stability, and under 5 every point cuts a fifth of the colony's income. "
                    + "An Auto colony over its limit takes down its weakest industry (core and item to Autopilot Storage), one a day until it's "
                    + "back within the limit, unless it's about to grow into the room. Upgrades into industries count against the limit while "
                    + "they're under way, so Autopilot doesn't start more than there's room for.",
                    Misc.getGrayColor(), 4f);
            row(t, "Take down industries that stopped paying", s.noPrune ? "No" : "Yes", "Yes", "prune", true);
            if (!s.noPrune) t.addPara("An industry at an Auto colony whose whole worth is what it makes, worth less than its upkeep for 30 days "
                    + "(say a second source of ore where Mining already makes more), is taken down, one per colony every 30 days. Never one "
                    + "you locked, a plan step or what's below one, military, defenses, ships, or one that adds stability.", Misc.getGrayColor(), 4f);
            row(t, "Allow removals", onOff(s.allowRemovals), "Off", "removals", true);
            row(t, "Mod buildings with measured effects", CASettings.MEASURED_NAMES[Math.max(0, Math.min(2, s.measuredMode))], "Build when best", "measured", true);
            row(t, "Story points", CASettings.SP_NAMES[s.storyPointRule], "Player only", "sp", true);
            if (s.storyPointRule == 2) row(t, "Story point monthly cap", "" + s.storyPointMonthlyCap, "2", "spcap", false);
        }
        if (group(t, "items", "Items and AI cores", "AI cores " + onOff(s.useAICores).toLowerCase()
                + (s.useAICores ? ", Alpha: " + CASettings.ALPHA_NAMES[Math.max(0, Math.min(2, s.alphaMode))]
                        + ", risk: " + CASettings.CORE_RISK_NAMES[Math.max(0, Math.min(3, s.coreRisk))].toLowerCase() : ""))) {
            row(t, "Use AI cores", onOff(s.useAICores), "On", "cores", true);
            if (s.useAICores) {
                row(t, "Alpha cores", CASettings.ALPHA_NAMES[Math.max(0, Math.min(2, s.alphaMode))], "Only where removable", "alpha", true);
                if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled(CAResearch.MOD)) {
                    row(t, "Alpha cores in research buildings", s.researchCoresByValue ? "By value" : "First", "First", "researchcores", true);
                    t.addPara("A core in a Research Facility or Black Site doesn't speed research: it cuts the building's research, maintenance and "
                            + "cover-up costs, by half for an Alpha (a quarter for a Beta, a tenth for a Gamma), and an Alpha adds a research "
                            + "databank a month on a world with a pre-collapse facility. First puts Alpha cores there before anywhere else, "
                            + "making inspection room by moving the colony's other cores out; By value weighs them like any other building.",
                            Misc.getGrayColor(), 4f);
                }
                row(t, "Inspection risk", CASettings.CORE_RISK_NAMES[Math.max(0, Math.min(3, s.coreRisk))], "Never inspected", "corerisk", true);
                t.addPara("Each core goes where it adds most for the colony's goal (output, upkeep, shortages, a structure's own bonus), "
                        + "less the colony crisis progress it adds: the Hegemony counts every core at a colony above size 3 (size 3 or less "
                        + "is free), and 7 Pather interest gives a colony of size 4 or more a Pather cell. A better core replaces a lesser "
                        + "one, which goes back to storage. Never inspected keeps colonies at " + CAItems.safePoints() + " inspection points or "
                        + "less, which the Hegemony never inspects; More where defended allows more where the defenses beat the next inspection "
                        + "by 1.5 times (2 times while the threat is high), which only helps with Resist where defended below; Small colonies "
                        + "only keeps cores out of colonies above size 3. There's no limit once you've defeated the Hegemony's inspections, "
                        + "while you're in the Persean League, or when the Hegemony holds no colonies. A colony an inspection is coming for "
                        + "is left as it is, and one a Pather attack is coming for drops under 7 Pather interest so its cell can dissolve.",
                        Misc.getGrayColor(), 6f);
                row(t, "Hegemony inspection orders", CASettings.INSPECTION_NAMES[Math.max(0, Math.min(2, s.inspectionMode))], "Advise", "inspect", true);
                t.addPara("The game's default is to comply, which hands over every core at that colony. Advise logs each inspection coming for "
                        + "an Auto colony with its strength and the colony's defenses, so you can resist or bribe from its intel. Resist where "
                        + "defended resists at a colony whose defenses beat it by the margin above: the Hegemony turns hostile, and three beaten "
                        + "inspections end them for good.", Misc.getGrayColor(), 6f);
            }
            if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled(CAResearch.MOD)) {
                row(t, "Special project supply", CASettings.PROJECT_SUPPLY_NAMES[Math.max(0, Math.min(2, s.projectSupplyMode))], "Auto", "projsupply", true);
                t.addPara("A black site project takes goods every month from your own colonies, and a stage stops while one isn't delivered at "
                        + "all. Several are AotD goods no faction makes (Electronic Components, Domain-grade machinery, Refined Metal, Enriched "
                        + "Transplutonics). Auto makes the producer of each one your colonies lack a first plan step at the Approve or Auto "
                        + "colony best placed for it, with the producers of its own inputs, so research works toward it and it gets built. "
                        + "Warn only logs what to build where.", Misc.getGrayColor(), 4f);
            }
            row(t, "Antimatter Stabilizers to Advanced Fuel Production", s.disableStabilizers ? "Off" : "Convert", "Convert", "stab", true);
        }
        if (group(t, "people", "Administrators and ambassadors", "hiring " + onOff(s.adminHiring).toLowerCase() + ", ambassadors "
                + (s.disableAmbassadors ? "off" : "on, " + CASettings.FOCUS_NAMES[Math.max(0, Math.min(3, s.ambassadorFocus))].toLowerCase()))) {
            row(t, "Administrator hiring", onOff(s.adminHiring), "On", "admin", true);
            row(t, "Assign administrators optimally", onOff(s.adminOptimal), "On", "adminopt", true);
            row(t, "Ambassadors (Industrial Evolution)", s.disableAmbassadors ? "Off" : "Hire and install", "Hire and install", "amb", true);
            t.addPara("An administrator is hired, placed or moved only where they add more than they cost: Industrial Planning's "
                    + "+1 output (worth more at a productive colony) and the stability from your colony limit, against their salary "
                    + "(2,500 or 20,000 a month) and the hiring cost spread over three years. The game only offers one at a market you "
                    + "dock at, so past your colony limit with none on offer for 90 days, a recruiter finds a basic one for 250,000 more.",
                    Misc.getGrayColor(), 6f);
            if (!s.disableAmbassadors) {
                row(t, "Ambassador focus", CASettings.FOCUS_NAMES[Math.max(0, Math.min(3, s.ambassadorFocus))], "Worst relations first", "ambfocus", true);
                row(t, "Stop raising a faction at", CASettings.STOP_NAMES[Math.max(0, Math.min(3, s.ambassadorStopAt))], "Cooperative", "ambstop", true);
                t.addPara("Faction priority for ambassadors (High is always picked first; Ignore is never picked):", Misc.getGrayColor(), 8f);
                for (com.fs.starfarer.api.campaign.FactionAPI f : ambassadorFactions()) {
                    int p = s.priorityOf(f.getId());
                    float rel = com.fs.starfarer.api.Global.getSector().getPlayerFaction().getRelationship(f.getId());
                    row(t, f.getDisplayName(), CASettings.PRIORITY_NAMES[p] + ", now " + com.fs.starfarer.api.Global.getSector().getPlayerFaction().getRelationshipLevel(f.getId()).getDisplayName()
                            + " (" + Math.round(rel * 100f) + ")", "Normal", "fprio_" + f.getId(), true);
                }
            }
        }
        if (group(t, "policy", "Colony policies", "hazard pay " + CASettings.POLICY_NAMES[Math.max(0, Math.min(1, s.hazardPayMode))].toLowerCase()
                + ", free port " + CASettings.POLICY_NAMES[Math.max(0, Math.min(1, s.freePortMode))].toLowerCase() + ", edicts " + (s.disableEdicts ? "off" : "on") + ", defense fleets " + (s.disableDefenseFleets ? "off" : "on")
                + (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled("nexerelin") ? ", war fleets " + CASettings.WAR_NAMES[Math.max(0, Math.min(2, s.warMode))].toLowerCase() : ""))) {
            row(t, "Hazard pay", CASettings.POLICY_NAMES[Math.max(0, Math.min(1, s.hazardPayMode))], "Auto", "hazard", true);
            if (s.hazardPayMode == 0) {
                row(t, "Hazard pay where growth is at least", Math.round(s.hazardPayColonyShare * 100f) + "% faster", "25%", "hpcol", false);
                row(t, "Hazard pay limit, all colonies", Math.round(s.hazardPayEmpireShare * 100f) + "% of empire net income", "30%", "hpemp", false);
                t.addPara("Every goal wants growth. Hazard pay goes first to the colonies it speeds up the most (a new colony that barely "
                        + "grows gains most), until all of it reaches the share of empire net income; the more you earn, the more colonies "
                        + "get it. Off in a money emergency and at max size.", Misc.getGrayColor(), 6f);
            }
            row(t, "Free port", CASettings.POLICY_NAMES[Math.max(0, Math.min(1, s.freePortMode))], "Auto", "freeport", true);
            row(t, "Senate edicts", s.disableEdicts ? "Off" : "Enact", "Enact", "edicts", true);
            row(t, "Emergency defense fleets (Nexerelin)", s.disableDefenseFleets ? "Off" : "Send when threatened", "Send when threatened", "defense", true);
            if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled("nexerelin")) {
                row(t, "War fleets (Nexerelin)", CASettings.WAR_NAMES[Math.max(0, Math.min(2, s.warMode))], "Protect and strike back", "war", true);
                t.addPara("Relief fleets for sieges, defense fleets where the threat is high or a colony crisis is coming, base strikes on "
                        + "pirate and Pather bases near your colonies, and (strike back) raids on the markets that attacked you. Never "
                        + "invasions or new colonies, and only against factions already hostile to you. One a day, keeping 40%% of fleet "
                        + "request points for emergencies; spending freely (10M or more), up to 3 a day, bigger, every known base, a "
                        + "standing defense fleet at every colony, paid from the surplus.", Misc.getGrayColor(), 6f);
            }
        }
        if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled("nexerelin")
                && group(t, "spies", "Agents (Nexerelin)", CASettings.SPY_NAMES[Math.max(0, Math.min(2, s.spyMode))].toLowerCase()
                + (s.spyMode == 0 ? "" : ", hiring " + (s.spyNoHiring ? "off" : "on")))) {
            row(t, "Agents", CASettings.SPY_NAMES[Math.max(0, Math.min(2, s.spyMode))], "Defense", "spy", true);
            if (s.spyMode != 0) {
                row(t, "Recruit agents", s.spyNoHiring ? "Off" : "On, 12,000 each, 60 days", "On", "spyhire", true);
                row(t, "Agents to keep", s.spyKeep == 0 ? "Auto (2, or 3 with offense)" : "" + s.spyKeep, "Auto", "spykeep", true);
                if (s.spyMode == 2) row(t, "Offense target", spyTargetName(s.spyTarget), "Whoever attacks you", "spytarget", true);
                t.addPara("The first agent is on security: counter-insurgency, Pather cells and pirate bases. The second raises relations with "
                        + "your preferred factions, picked like ambassadors (Administrators and ambassadors), never one at war with you. With "
                        + "offense, a third sabotages the military of the offense target, only factions already at war with you. Recruiting "
                        + "costs 12,000 plus the fee and takes 60 days; the first comes once you have a colony, the others once the budget allows. "
                        + "Costs come out of the monthly budget; Nexerelin pays salaries. Only agents Autopilot recruited, or you hand over on the "
                        + "Mod features page, are given orders, and only operations their type can run.", Misc.getGrayColor(), 6f);
            }
        }
        if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled("nexerelin")
                && group(t, "autonomy", "Autonomy (Nexerelin)", CASettings.AUTONOMY_NAMES[Math.max(0, Math.min(2, s.autonomyMode))].toLowerCase())) {
            row(t, "Autonomy", CASettings.AUTONOMY_NAMES[Math.max(0, Math.min(2, s.autonomyMode))], "Recommend", "autonomy", true);
            t.addPara("Only for money, never as a way past the colony limit (that gets an administrator), and never for a colony under a "
                    + "year old: a colony that has lost money 3 months in a row, or the one that saves the most in a money emergency (net "
                    + "income negative with under 3 months of it left above the reserve). Recommend logs it, and the Mod features page lists "
                    + "money-losing colonies with a Grant button. Auto makes it autonomous, one every 30 days at most; outside an emergency not "
                    + "while spending freely, since the pool pays for it. Autonomous colonies stay so until you take them back there.",
                    Misc.getGrayColor(), 6f);
        }
        if (group(t, "industry", "Industry automation", "specialties " + (s.disableSpecialties ? "off" : "on") + ", VPC chips " + (s.disableVPC ? "off" : "on")
                + ", shipping " + (s.disableShipping ? "off" : "on"))) {
            row(t, "Specialized Mining and Refining", s.disableSpecialties ? "Off" : "Switch to best", "Switch to best", "spec", true);
            row(t, "VPC chips", s.disableVPC ? "Off" : "Install", "Install", "vpc", true);
            row(t, "Commodity Forge may use up chips", s.disableForgeVPC ? "No" : "Yes", "Yes", "forge", true);
            row(t, "Supply shipping (Courier Port)", s.disableShipping ? "Off" : "Cover shortages", "Cover shortages", "ship", true);
        }
        if (CATasc.enabled() && group(t, "tascstations", "Station construction (Terraforming & Station Construction)",
                CASettings.TASC_STATION_NAMES[Math.max(0, Math.min(2, s.stationMode))].toLowerCase())) {
            row(t, "Mining and siphon stations", CASettings.TASC_STATION_NAMES[Math.max(0, Math.min(2, s.stationMode))], "Recommend only", "tascst", true);
            t.addPara("Founds a mining station in an asteroid belt or a siphon station over a gas giant as a new colony, in systems with "
                    + "your Auto colonies that are calm or well defended, when there's room under your colony limit and TASC's research "
                    + "for it is done. A crew does it for TASC's own costs (500,000 credits and materials by default) plus a fee, in 60 "
                    + "days; the new colony runs in Auto with the Money goal. Recommend only logs the best site.", Misc.getGrayColor(), 6f);
        }
        if (group(t, "objectives", "Stable locations", CASettings.OBJECTIVE_NAMES[Math.max(0, Math.min(2, s.objectiveMode))].toLowerCase())) {
            row(t, "Comm relays, nav buoys, sensor arrays", CASettings.OBJECTIVE_NAMES[Math.max(0, Math.min(2, s.objectiveMode))], "Auto", "objectives", true);
            t.addPara("In systems with your Auto colonies, a construction crew builds what the stable locations there are worth most "
                    + "for those colonies' goals: a comm relay (+1 stability), nav buoy (+2 burn) or sensor array (+400 sensor range), "
                    + "for vanilla's materials at base price plus a fee, 10 days after it's sent. With Terraforming Made Easy, a makeshift "
                    + "one is upgraded to Domain-era with a spare Beta core; with Ashes of the Domain, a Hypershunt Receiver goes up "
                    + "once researched, with a Coronal Hypershunt Tap in Autopilot Storage.", Misc.getGrayColor(), 6f);
        }
        if (group(t, "capital", "Capital", !CACapital.on(s) ? "off" : CACapital.name(s) + ", " + CACapital.MODE_NAMES[Math.max(0, Math.min(2, s.capitalMode))].toLowerCase())) {
            row(t, "Capital", CACapital.name(s), "Autopilot picks", "capid", true);   // 0.2.45.1: was "cap", the per-project cap's key
            row(t, "Capital does", CACapital.MODE_NAMES[Math.max(0, Math.min(2, s.capitalMode))], "Deliveries and commerce", "capmode", true);
            if (s.capitalMode == 0) {
                row(t, "Commerce starts", CACapital.START_NAMES[Math.max(0, Math.min(CACapital.START_NAMES.length - 1, s.capitalStart))], "Net income over 100,000", "capstart", true);
                row(t, "Make room for trade buildings", s.capitalNoRoom ? "No" : "Yes", "Yes", "caproom", true);
            }
            if (s.capitalMode != 2) row(t, "Gather your Storage there", s.capitalNoGather ? "No" : "Yes", "Yes", "capgather", true);
            t.addPara("Your home port. Custom production is delivered there (it moves the production gathering point; move that yourself "
                    + "and the capital follows), and once a week your other Storage tabs ship their ships, weapons, wings and blueprints there, "
                    + "at the storage logistics fee. Once your colonies have ramped up, the capital builds itself into a place to trade: "
                    + "Spaceport and Megaport, Commerce, every installed mod building that lets you buy or sell there, and a Waystation, ahead "
                    + "of its other plans. With every industry slot taken it takes down its weakest ordinary industry for one, at most one every "
                    + "90 days, never defenses, fleets or ship production. Autopilot picks the colony where your things already are; pick "
                    + "your own on its colony panel.", Misc.getGrayColor(), 6f);
        }
        if (group(t, "logistics", "Storage logistics", s.logisticsMode == 1 ? "on, refit hub " + CALogistics.hubName(s.logisticsHub, true)
                + (s.logisticsSellHub != null ? ", sell hub " + CALogistics.hubName(s.logisticsSellHub, false) : "") : "off")) {
            row(t, "Storage logistics", s.logisticsMode == 1 ? "On" : "Off", "Your call", "logi", true);
            row(t, "Refit hub", CALogistics.hubName(s.logisticsHub, true), "Production gathering point", "logihub", true);
            row(t, "Sell hub", CALogistics.hubName(s.logisticsSellHub, false), "Your call", "logisell", true);
            row(t, "Ships go to the refit hub", s.logisticsShips ? "Yes" : "No", "Yes", "logiships", true);
            row(t, "Supplies, fuel, crew and marines go too", s.logisticsSupplies ? "Yes" : "No", "Yes", "logigoods", true);
            t.addPara("Once a week, what you refit a fleet with (mothballed ships, weapons, fighter wings, blueprints and hull mod specs you "
                    + "don't know yet) is shipped from every other Storage you have to the refit hub's Storage. Blueprints and specs you "
                    + "already know, and spare copies, go to the sell hub, so you can sell them in one place. Each load costs what an Autopilot "
                    + "Storage shipment does plus 1,000 a ship, from the monthly budget, and takes 2 days plus a day a light-year. Colony "
                    + "items and AI cores stay where they are.", Misc.getGrayColor(), 6f);
        }
        if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled(CASettlement.MOD)
                && group(t, "settle", "Settlement (Random Assortment of Things)", CASettings.SETTLE_NAMES[Math.max(0, Math.min(3, s.settlementMode))].toLowerCase()
                + ", found at " + Misc.getDGSCredits(s.settlementMinIncome) + "/month")) {
            row(t, "Settlement mode", CASettings.SETTLE_NAMES[Math.max(0, Math.min(3, s.settlementMode))], "Auto", "settle", true);
            row(t, "Found when a site makes at least", Misc.getDGSCredits(s.settlementMinIncome) + " per month", "20,000", "settleinc", false);
        }
        if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled(CAAugments.MOD)
                && group(t, "augments", "Station augments (Station Augments)", CASettings.AUGMENT_NAMES[Math.max(0, Math.min(2, s.augmentMode))].toLowerCase())) {
            row(t, "Station augments", CASettings.AUGMENT_NAMES[Math.max(0, Math.min(2, s.augmentMode))], "Follow colony mode", "aug", true);
        }
        boolean tot = com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled(CAContracts.MOD);
        boolean vok = com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled(CAHypershunt.MOD);
        if ((tot || vok) && group(t, "aotd", "Ashes of the Domain", (tot ? "contracts " + CASettings.CONTRACT_NAMES[Math.max(0, Math.min(2, s.contractMode))].toLowerCase() : "")
                + (tot && vok ? ", " : "") + (vok ? "research " + CASettings.CONTRACT_NAMES[Math.max(0, Math.min(2, s.researchMode))].toLowerCase()
                + ", hypershunt " + CASettings.AUGMENT_NAMES[Math.max(0, Math.min(2, s.hypershuntMode))].toLowerCase() : ""))) {
            if (tot) row(t, "Trade contracts", CASettings.CONTRACT_NAMES[Math.max(0, Math.min(2, s.contractMode))], "Auto", "contract", true);
            if (vok) row(t, "Research", CASettings.CONTRACT_NAMES[Math.max(0, Math.min(2, s.researchMode))], "Auto", "research", true);
            if (vok) {
                row(t, "Research spends colony items (surplus only)", CASettings.RESEARCH_ITEM_NAMES[Math.max(0, Math.min(2, s.researchItemMode))], "Off", "resitems", true);
                t.addPara("Some research takes colony items (a Pristine Nanoforge, a Synchrotron Core...). Off: those techs are yours to start. "
                        + "With credits over the surplus line, From storage lets Auto research take them from Autopilot Storage and your "
                        + "colonies' Storage tabs; From storage and buildings also takes them out of buildings, the one where the item does "
                        + "least first. Only when everything the tech needs is on hand.", Misc.getGrayColor(), 4f);
            }
            if (vok) row(t, "Megastructures (Hypershunt, Pluto, Nidavelir)", CASettings.AUGMENT_NAMES[Math.max(0, Math.min(2, s.hypershuntMode))], "Follow colony mode", "hs", true);
        }
        if (group(t, "interface", "Interface", "colony screen button " + (s.noScreenButton ? "off" : "on"))) {
            row(t, "Button on the colony screen", s.noScreenButton ? "Off" : "On", "On", "screenbtn", true);
            t.addPara("Opens this panel from your colony's own screen. Right-clicking Population & Infrastructure works either way.", Misc.getGrayColor(), 6f);
            row(t, "Log entries on screen", CALog.SCREEN_NAMES[Math.max(0, Math.min(2, s.screenLog))], "Every entry", "screenlog", true);
            row(t, "Announcement voice", CASettings.VOICE_NAMES[Math.max(0, Math.min(1, s.voice))], "Machine", "voice", true);
            t.addPara("Machine: a code first, like BUILD / Hot Springs: Planetary Shield (750k), plus a ping now and then about what "
                    + "it's weighing. Plain: Autopilot: and the full sentence. The panel log always has the full sentence.", Misc.getGrayColor(), 4f);
            t.addPara("Every entry shows each thing Autopilot does as one short line. States that flip back and forth (siege, threat, free port, "
                    + "hazard pay, station augments) show at most once every 10 days per colony. Important only: emergencies, defense fleets, "
                    + "wonders and gates, and what's yours to do.", Misc.getGrayColor(), 6f);
        }
        ButtonAPI rec = t.addButton("Use recommended settings", PREFIX + "recommend:x", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.TL_BR, 260f, 24f, 14f);
    }

    // ---- offense target (agents): Whoever attacks you (null), a random enemy, or one faction at war with you

    static String spyTargetName(String t) {
        if (t == null || t.isEmpty()) return "Whoever attacks you";
        if ("random".equals(t)) return "A random enemy";
        com.fs.starfarer.api.campaign.FactionAPI f = com.fs.starfarer.api.Global.getSector().getFaction(t);
        return f == null ? "Whoever attacks you" : f.getDisplayName() + (f.isHostileTo("player") ? "" : " (not at war with you: none)");
    }

    /** Cycles Whoever attacks you, A random enemy, then each faction now at war with you (vanilla API only: this loads without Nexerelin). */
    static String nextSpyTarget(String cur) {
        java.util.List<String> order = new java.util.ArrayList<String>();
        order.add(null);
        order.add("random");
        for (com.fs.starfarer.api.campaign.FactionAPI f : com.fs.starfarer.api.Global.getSector().getAllFactions())
            if (!f.isPlayerFaction() && f.isShowInIntelTab() && f.isHostileTo("player")) order.add(f.getId());
        int i = order.indexOf(cur);
        return order.get((i + 1) % order.size());
    }

    static String stationName(String id) {
        for (int i = 0; i < CASettings.STATION_IDS.length; i++) if (CASettings.STATION_IDS[i].equals(id)) return CASettings.STATION_NAMES[i];
        return id;
    }

    /** One compact line: the buttons on the left, then "Label: value (recommended: ...)". */
    static void row(TooltipMakerAPI t, String label, String value, String rec, String key, boolean toggle) {
        Color base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        ButtonAPI first, last;
        if (toggle) {
            first = last = t.addButton("CYCLE", PREFIX + key + ":next", base, dark, Alignment.MID, CutStyle.TL_BR, 86f, 20f, 4f);
        } else {
            first = t.addButton("-", PREFIX + key + ":-", base, dark, Alignment.MID, CutStyle.TL_BR, 40f, 20f, 4f);
            last = t.addButton("+", PREFIX + key + ":+", base, dark, Alignment.MID, CutStyle.TL_BR, 40f, 20f, 0f);
            last.getPosition().rightOfTop(first, 6f);
        }
        com.fs.starfarer.api.ui.LabelAPI l = t.addPara("%s  ::  %s   // rec " + CALog.esc(rec), 0f, Misc.getHighlightColor(), label.toUpperCase(java.util.Locale.ROOT), value);
        l.getPosition().rightOfMid(last, 12f);
        CALog.endRow(t, first);
    }
    /** Handles a settings button; returns true if the id was a settings button. */
    public static boolean handle(Object id, CASettings s) {
        if (!(id instanceof String) || !((String) id).startsWith(PREFIX)) return false;
        String[] a = ((String) id).substring(PREFIX.length()).split(":");
        String k = a[0], op = a.length > 1 ? a[1] : "next";
        float dir = "-".equals(op) ? -1f : 1f;
        if (k.equals("group")) {   // one group open at a time, so the list stays short
            String g = a.length > 1 ? a[1] : "";
            boolean was = open.contains(g);
            open.clear();
            if (!was) open.add(g);
            return true;
        }
        if (k.equals("recommend")) applyRecommended(s);
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
        else if (k.equals("cap")) s.perProjectCap = Math.max(250000f, s.perProjectCap + 500000f * dir);
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
        else if (k.equals("station")) {
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
        else if (k.equals("war")) s.warMode = (Math.max(0, Math.min(2, s.warMode)) + 1) % 3;
        else if (k.equals("ambfocus")) s.ambassadorFocus = (Math.max(0, Math.min(3, s.ambassadorFocus)) + 1) % 4;
        else if (k.equals("ambstop")) s.ambassadorStopAt = (Math.max(0, Math.min(3, s.ambassadorStopAt)) + 1) % 4;
        else if (k.startsWith("fprio_")) {
            String fid = ((String) id).substring((PREFIX + "fprio_").length()).replace(":next", "");
            if (s.factionPriority == null) s.factionPriority = new java.util.HashMap<String, Integer>();
            s.factionPriority.put(fid, (s.priorityOf(fid) + 2) % 3);   // Normal -> High -> Ignore -> Normal
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
        else if (k.equals("capid")) { s.capitalId = CACapital.next(s.capitalId); if (s.capitalId != null) s.capitalAuto = s.capitalId; CACapital.reset(); }
        else if (k.equals("capmode")) { s.capitalMode = (Math.max(0, Math.min(2, s.capitalMode)) + 1) % 3; CACapital.reset(); }
        else if (k.equals("capstart")) s.capitalStart = (Math.max(0, Math.min(CACapital.START_NAMES.length - 1, s.capitalStart)) + 1) % CACapital.START_NAMES.length;
        else if (k.equals("caproom")) s.capitalNoRoom = !s.capitalNoRoom;
        else if (k.equals("stationplan")) s.noStationPlan = !s.noStationPlan;
        else if (k.equals("marketmodel")) { s.noMarketModel = !s.noMarketModel; CAMarket.invalidate(); CAAuto.nextPass.clear(); }
        else if (k.equals("overlimit")) s.noOverLimitFix = !s.noOverLimitFix;
        else if (k.equals("prune")) s.noPrune = !s.noPrune;
        else if (k.equals("lowprofile")) CALowProfile.set(s, !s.lowProfile, "Settings");
        else if (k.equals("capgather")) s.capitalNoGather = !s.capitalNoGather;
        else if (k.equals("settleinc")) s.settlementMinIncome = Math.max(5000f, s.settlementMinIncome + 5000f * dir);
        return true;
    }
}
