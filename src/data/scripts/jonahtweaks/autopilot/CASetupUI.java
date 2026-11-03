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
        s.monthCredits = credits;
        s.feeLevel = 2;
        s.stationLine = "score";
        s.measuredMode = 0;
        s.allowRemovals = false;   // removing buildings is the player's call
        s.storyPointRule = 0;      // story points too
        // items and people: only items you put in Autopilot Storage are used; cores are kept within inspection limits
        s.useAICores = true;
        s.alphaMode = 0;
        s.coreRisk = 1;            // Never inspected: More where defended only pays if inspections are resisted, which means war with the Hegemony
        s.inspectionMode = 0;      // Advise: resisting turns the Hegemony hostile, so that's your call
        s.storageCheat = false;    // cheats are never recommended
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
        s.disableSpecialties = false;
        s.disableVPC = false;
        s.disableForgeVPC = false;
        s.disableShipping = false;
        s.settlementMode = 2;   // Auto: founds RAT's settlement only once a site clears the income threshold
        s.augmentMode = 0;
        s.contractMode = 1;     // Auto: only contracts with 15% to spare that beat exporting
        s.researchMode = 1;     // Auto: databank-only techs, ranked by what they unlock
        s.hypershuntMode = 0;
        s.spyMode = 1;          // Defense: an agent is hired only when there's a rebellion, Pather cell, pirate base or enemy operation
        s.spyNoHiring = false;
        s.spyKeep = 0;
        s.autonomyMode = 1;     // Recommend: autonomy is your call; Autopilot only says which colony would help in a money emergency
        s.noScreenButton = false;
        // attacks on your colonies: only a Derelict Empire start needs them (attacks from the first weeks)
        if (CAAttacks.forThisStart()) { s.haFleetCap = CAAttacks.REC_CAP; s.nexAttackGap = CAAttacks.REC_GAP; }
    }

    /** Which group is expanded (UI state only, not saved); at most one. Budget starts open. */
    static final java.util.Set<String> open = new java.util.HashSet<String>(java.util.Arrays.asList("budget"));

    static boolean group(TooltipMakerAPI t, String id, String title, String summary) {
        boolean isOpen = open.contains(id);
        ButtonAPI b = t.addButton((isOpen ? "-  " : "+  ") + title, PREFIX + "group:" + id, isOpen ? Misc.getHighlightColor() : Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), Alignment.LMID, CutStyle.TL_BR, 300f, 24f, 10f);
        com.fs.starfarer.api.ui.LabelAPI l = t.addPara(CALog.esc(summary), Misc.getGrayColor(), 0f);
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
        String starter = "mid".equals(s.stationLine) ? "orbitalstation_mid" : "high".equals(s.stationLine) ? "orbitalstation_high" : "orbitalstation";
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
            if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy")) {
                row(t, "Surplus terraforming", CASettings.SURPLUS_TF_NAMES[Math.max(0, Math.min(2, s.surplusTerraform))], "Max output", "surplustf", true);
                t.addPara("What the focus world leaves of the surplus goes into Terraforming Made Easy projects at every Auto colony, most value "
                        + "per credit first: richer deposits and farmland, harsh conditions cleared, the structure built first when needed. "
                        + "Planned on TME's own rules, so a project that loses more than it gains (extreme tectonic activity takes a habitable "
                        + "world's farmland and organics) never goes, and nothing a colony's plan needs is undone. Max output: anything worth "
                        + "1,000 a month or more after the structure's upkeep. Paid from the surplus only, outside the monthly budget.", Misc.getGrayColor(), 4f);
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
            row(t, "Antimatter Stabilizers to Advanced Fuel Production", s.disableStabilizers ? "Off" : "Convert", "Convert", "stab", true);
        }
        if (group(t, "people", "Administrators and ambassadors", "hiring " + onOff(s.adminHiring).toLowerCase() + ", ambassadors "
                + (s.disableAmbassadors ? "off" : "on, " + CASettings.FOCUS_NAMES[Math.max(0, Math.min(3, s.ambassadorFocus))].toLowerCase()) + ", arrival delay " + onOff(s.adminDelay).toLowerCase())) {
            row(t, "Administrator hiring", onOff(s.adminHiring), "On", "admin", true);
            row(t, "Assign administrators optimally", onOff(s.adminOptimal), "On", "adminopt", true);
            row(t, "Ambassadors (Industrial Evolution)", s.disableAmbassadors ? "Off" : "Hire and install", "Hire and install", "amb", true);
            row(t, "Arrival delay", s.adminDelay ? "On (30 days)" : "Off", "On", "admindelay", true);
            t.addPara("An administrator is hired, placed or moved only where they add more than they cost: Industrial Planning's "
                    + "+1 output (worth more at a productive colony) and the stability from your colony limit, against their salary "
                    + "(2,500 or 20,000 a month) and the hiring cost spread over three years.", Misc.getGrayColor(), 6f);
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
                + ", free port " + CASettings.POLICY_NAMES[Math.max(0, Math.min(1, s.freePortMode))].toLowerCase() + ", edicts " + (s.disableEdicts ? "off" : "on") + ", defense fleets " + (s.disableDefenseFleets ? "off" : "on"))) {
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
        }
        if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled("nexerelin")
                && group(t, "spies", "Agents (Nexerelin)", CASettings.SPY_NAMES[Math.max(0, Math.min(2, s.spyMode))].toLowerCase()
                + (s.spyMode == 0 ? "" : ", hiring " + (s.spyNoHiring ? "off" : "on")))) {
            row(t, "Agents", CASettings.SPY_NAMES[Math.max(0, Math.min(2, s.spyMode))], "Defense", "spy", true);
            if (s.spyMode != 0) {
                row(t, "Hire agents", s.spyNoHiring ? "Off" : "On, 12,000 each", "On", "spyhire", true);
                row(t, "Agents to keep", s.spyKeep == 0 ? "Auto (1, or 2 with offense)" : "" + s.spyKeep, "Auto", "spykeep", true);
                t.addPara("Defense: counter-insurgency, Pather cells, pirate bases, and raising relations with factions running operations "
                        + "against you. Offense only targets factions already at war with you. Costs come out of the monthly budget; "
                        + "Nexerelin pays salaries. Only agents Autopilot hired, or you hand over on the Mod features page, are given orders, "
                        + "and only operations their type can run.", Misc.getGrayColor(), 6f);
            }
        }
        if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled("nexerelin")
                && group(t, "autonomy", "Emergency autonomy (Nexerelin)", CASettings.AUTONOMY_NAMES[Math.max(0, Math.min(2, s.autonomyMode))].toLowerCase())) {
            row(t, "Emergency autonomy", CASettings.AUTONOMY_NAMES[Math.max(0, Math.min(2, s.autonomyMode))], "Recommend", "autonomy", true);
            t.addPara("Autonomy is yours to grant: in a money emergency (net income negative with under 3 months of it left above the "
                    + "credit reserve), Recommend logs which colony a year old or more would save the most, and the Mod features page has "
                    + "a Grant autonomy button. Colonies you make autonomous are never touched. Colonies an older version of Autopilot made "
                    + "autonomous are taken back as soon as there's room under your colony limit, without Nexerelin's unrest.", Misc.getGrayColor(), 6f);
        }
        if (group(t, "industry", "Industry automation", "specialties " + (s.disableSpecialties ? "off" : "on") + ", VPC chips " + (s.disableVPC ? "off" : "on")
                + ", shipping " + (s.disableShipping ? "off" : "on"))) {
            row(t, "Specialized Mining and Refining", s.disableSpecialties ? "Off" : "Switch to best", "Switch to best", "spec", true);
            row(t, "VPC chips", s.disableVPC ? "Off" : "Install", "Install", "vpc", true);
            row(t, "Commodity Forge may use up chips", s.disableForgeVPC ? "No" : "Yes", "Yes", "forge", true);
            row(t, "Supply shipping (Courier Port)", s.disableShipping ? "Off" : "Cover shortages", "Cover shortages", "ship", true);
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
            if (vok) row(t, "Megastructures (Hypershunt, Pluto, Nidavelir)", CASettings.AUGMENT_NAMES[Math.max(0, Math.min(2, s.hypershuntMode))], "Follow colony mode", "hs", true);
        }
        if (CAAttacks.forThisStart() && group(t, "attacks", "Attacks on your colonies (Derelict Empire start)", "pirate and crisis fleets: "
                + CAAttacks.capName(s).toLowerCase() + ", Nexerelin attacks: " + CAAttacks.gapName(s).toLowerCase())) {
            t.addPara("Only in this start: Nexerelin's Derelict Empire gives every faction enough points for a blockade or raid on day 0, "
                    + "so they come within weeks while your colony is new.", Misc.getGrayColor(), 6f);
            row(t, "Pirate and crisis fleets per system", CAAttacks.capName(s), "At most " + CAAttacks.REC_CAP, "hacap", true);
            t.addPara("Hostile Activity keeps up to this many pirate, Pather and other crisis fleets in a system with your colonies; "
                    + "a big colony at low stability gets the most. Fewer means smaller swarms.", Misc.getGrayColor(), 6f);
            row(t, "Nexerelin invasions, raids and blockades", CAAttacks.gapName(s), "One at a time, " + CAAttacks.REC_GAP + " days apart", "nexgap", true);
            t.addPara("Nexerelin waits only 60 days between attacks on you, and a blockade lasts 120, so they can stack up. One at a "
                    + "time stops a new invasion, raid or blockade of your colonies while one is on its way or under way, and for the "
                    + "chosen days after it ends. Not in Nexerelin's hard mode, which ignores it."
                    + (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled("EmergentThreats_IX_Revival")
                    ? " IX Revival's blockades come through Nexerelin, so this covers them; IX's own difficulty is in its LunaLib menu." : ""),
                    Misc.getGrayColor(), 6f);
        }
        if (group(t, "interface", "Interface", "colony screen button " + (s.noScreenButton ? "off" : "on"))) {
            row(t, "Button on the colony screen", s.noScreenButton ? "Off" : "On", "On", "screenbtn", true);
            t.addPara("Opens this panel from your colony's own screen. Right-clicking Population & Infrastructure works either way.", Misc.getGrayColor(), 6f);
        }
        if (group(t, "cheats", "Cheats", "storage cheat " + (s.storageCheat ? "on" : "off"))) {
            row(t, "Store anything, colonies draw shortages from it (cheat)", s.storageCheat ? "On (cheat)" : "Off", "Off", "storecheat", true);
            t.addPara("Autopilot Storage takes anything but ships and ambassadors, and you can take it out at any colony. Every colony "
                    + "that uses stockpiles for shortages draws from it, for free, before its own stockpile: a colony short of a commodity "
                    + "in storage gets the missing amount, and the storage is drawn down at the game's stockpile rate.", Misc.getGrayColor(), 6f);
        }
        ButtonAPI rec = t.addButton("Use recommended settings", PREFIX + "recommend:x", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.ALL, 260f, 24f, 14f);
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
            first = last = t.addButton("Change", PREFIX + key + ":next", base, dark, Alignment.MID, CutStyle.ALL, 86f, 20f, 4f);
        } else {
            first = t.addButton("-", PREFIX + key + ":-", base, dark, Alignment.MID, CutStyle.ALL, 40f, 20f, 4f);
            last = t.addButton("+", PREFIX + key + ":+", base, dark, Alignment.MID, CutStyle.ALL, 40f, 20f, 0f);
            last.getPosition().rightOfTop(first, 6f);
        }
        com.fs.starfarer.api.ui.LabelAPI l = t.addPara("%s: %s   (recommended: " + CALog.esc(rec) + ")", 0f, Misc.getHighlightColor(), label, value);
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
            int i = 0;
            for (int j = 0; j < CASettings.STATION_IDS.length; j++) if (CASettings.STATION_IDS[j].equals(s.stationLine)) i = j;
            s.stationLine = CASettings.STATION_IDS[(i + 1) % CASettings.STATION_IDS.length];
        }
        else if (k.equals("cores")) s.useAICores = !s.useAICores;
        else if (k.equals("storecheat")) s.storageCheat = !s.storageCheat;
        else if (k.equals("removals")) s.allowRemovals = !s.allowRemovals;
        else if (k.equals("alpha")) s.alphaMode = (Math.max(0, Math.min(2, s.alphaMode)) + 1) % 3;
        else if (k.equals("inspect")) s.inspectionMode = (Math.max(0, Math.min(2, s.inspectionMode)) + 1) % 3;
        else if (k.equals("corerisk")) s.coreRisk = (Math.max(0, Math.min(3, s.coreRisk)) + 1) % 4;
        else if (k.equals("sp")) s.storyPointRule = (s.storyPointRule + 1) % 3;
        else if (k.equals("spcap")) s.storyPointMonthlyCap = Math.max(0, s.storyPointMonthlyCap + (int) dir);
        else if (k.equals("admin")) s.adminHiring = !s.adminHiring;
        else if (k.equals("adminopt")) s.adminOptimal = !s.adminOptimal;
        else if (k.equals("admindelay")) s.adminDelay = !s.adminDelay;
        else if (k.equals("amb")) s.disableAmbassadors = !s.disableAmbassadors;
        else if (k.equals("vpc")) s.disableVPC = !s.disableVPC;
        else if (k.equals("forge")) s.disableForgeVPC = !s.disableForgeVPC;
        else if (k.equals("edicts")) s.disableEdicts = !s.disableEdicts;
        else if (k.equals("ship")) s.disableShipping = !s.disableShipping;
        else if (k.equals("spec")) s.disableSpecialties = !s.disableSpecialties;
        else if (k.equals("stab")) s.disableStabilizers = !s.disableStabilizers;
        else if (k.equals("defense")) s.disableDefenseFleets = !s.disableDefenseFleets;
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
        else if (k.equals("screenbtn")) s.noScreenButton = !s.noScreenButton;
        else if (k.equals("hacap")) { s.haFleetCap = CASettings.next(CASettings.HA_CAPS, s.haFleetCap); CAAttacks.apply(s); }
        else if (k.equals("nexgap")) { s.nexAttackGap = CASettings.next(CASettings.NEX_GAPS, s.nexAttackGap); CAAttacks.apply(s); }
        else if (k.equals("spy")) s.spyMode = (Math.max(0, Math.min(2, s.spyMode)) + 1) % 3;
        else if (k.equals("spyhire")) s.spyNoHiring = !s.spyNoHiring;
        else if (k.equals("autonomy")) s.autonomyMode = s.autonomyMode == 0 ? 1 : 0;
        else if (k.equals("spykeep")) s.spyKeep = (Math.max(0, Math.min(4, s.spyKeep)) + 1) % 5;
        else if (k.equals("settleinc")) s.settlementMinIncome = Math.max(5000f, s.settlementMinIncome + 5000f * dir);
        return true;
    }
}
