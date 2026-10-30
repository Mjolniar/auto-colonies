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

    static float round(float v, float step) { return Math.max(step, Math.round(v / step) * step); }

    /** Recommended values from current income; the defaults otherwise. */
    public static void applyRecommended(CASettings s) {
        float net = Math.max(0f, CABudget.netIncome(s));
        s.budgetPercentMode = true;
        s.budgetPercent = 0.5f;
        s.perProjectCap = Math.min(5000000f, round(Math.max(1000000f, net), 250000f));
        s.reserve = 500000f;
        s.newUpkeepCap = round(Math.max(20000f, net * 0.1f), 10000f);
        s.pauseOnLoss = true;
        s.feeLevel = 2;
        s.stationLine = "score";
        s.useAICores = false;
        s.alphaMode = 0;
        s.hazardPayColonyShare = 0.25f;
        s.hazardPayEmpireShare = 0.10f;
        s.allowRemovals = false;
        s.storyPointRule = 0;
        s.adminHiring = false;
        s.adminOptimal = true;
        s.adminDelay = true;
    }

    /** Which groups are expanded (UI state only, not saved). Budget starts open. */
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

    /** Opens every group (the first-time setup popup shows all options). */
    public static void openAll() { open.addAll(java.util.Arrays.asList("budget", "build", "items", "people", "policy", "industry", "settle")); }

    public static void render(TooltipMakerAPI t, CASettings s, float width) {
        String budget = s.budgetPercentMode ? Math.round(s.budgetPercent * 100f) + "% of net income (" + Misc.getDGSCredits(CABudget.limit(s)) + " now)"
                : Misc.getDGSCredits(s.budgetFixed);

        if (group(t, "budget", "Budget and spending", budget + ", reserve " + Misc.getDGSCredits(s.reserve) + ", fees " + CASettings.FEE_NAMES[s.feeLevel])) {
            row(t, "Budget type", s.budgetPercentMode ? "Share of net income" : "Fixed amount", "Share of net income", "type", true);
            row(t, "Monthly budget", budget, "50% of net income", "budget", false);
            row(t, "Per-project cap", Misc.getDGSCredits(s.perProjectCap), "monthly net income, 1M-5M", "cap", false);
            row(t, "Credit reserve", Misc.getDGSCredits(s.reserve), "500,000", "reserve", false);
            row(t, "New upkeep cap per month", Misc.getDGSCredits(s.newUpkeepCap), "10% of net income", "upkeep", false);
            row(t, "Pause on loss", onOff(s.pauseOnLoss), "On", "pause", true);
            row(t, "Convenience fees", CASettings.FEE_NAMES[s.feeLevel], "Standard", "fees", true);
            float st = stationLineItem(s);
            t.addPara("Station line item: about %s to give each of your colonies its next station tier (" + CALog.esc(stationName(s.stationLine))
                    + "). Recommended: allow it within 2-3 months of budget.", 8f, Misc.getHighlightColor(), Misc.getDGSCredits(st));
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
                + (s.useAICores ? ", Alpha: " + CASettings.ALPHA_NAMES[Math.max(0, Math.min(2, s.alphaMode))] : ""))) {
            row(t, "Use AI cores", onOff(s.useAICores), "Off", "cores", true);
            if (s.useAICores) row(t, "Alpha cores", CASettings.ALPHA_NAMES[Math.max(0, Math.min(2, s.alphaMode))], "Only where removable", "alpha", true);
            row(t, "Antimatter Stabilizers to Advanced Fuel Production", s.disableStabilizers ? "Off" : "Convert", "Convert", "stab", true);
        }
        if (group(t, "people", "Administrators and ambassadors", "hiring " + onOff(s.adminHiring).toLowerCase() + ", ambassadors "
                + (s.disableAmbassadors ? "off" : "on, " + CASettings.FOCUS_NAMES[Math.max(0, Math.min(3, s.ambassadorFocus))].toLowerCase()) + ", arrival delay " + onOff(s.adminDelay).toLowerCase())) {
            row(t, "Administrator hiring", onOff(s.adminHiring), "Off", "admin", true);
            row(t, "Assign administrators optimally", onOff(s.adminOptimal), "On", "adminopt", true);
            row(t, "Ambassadors (Industrial Evolution)", s.disableAmbassadors ? "Off" : "Hire and install", "Hire and install", "amb", true);
            row(t, "Arrival delay", s.adminDelay ? "On (30 days)" : "Off", "On", "admindelay", true);
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
                row(t, "Hazard pay limit per colony", Math.round(s.hazardPayColonyShare * 100f) + "% of its net income", "25%", "hpcol", false);
                row(t, "Hazard pay limit, all colonies", Math.round(s.hazardPayEmpireShare * 100f) + "% of empire net income", "10%", "hpemp", false);
            }
            row(t, "Free port", CASettings.POLICY_NAMES[Math.max(0, Math.min(1, s.freePortMode))], "Auto", "freeport", true);
            row(t, "Senate edicts", s.disableEdicts ? "Off" : "Enact", "Enact", "edicts", true);
            row(t, "Emergency defense fleets (Nexerelin)", s.disableDefenseFleets ? "Off" : "Send when threatened", "Send when threatened", "defense", true);
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
            row(t, "Settlement mode", CASettings.SETTLE_NAMES[Math.max(0, Math.min(3, s.settlementMode))], "Advisor", "settle", true);
            row(t, "Found when a site makes at least", Misc.getDGSCredits(s.settlementMinIncome) + " per month", "20,000", "settleinc", false);
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
        if (k.equals("group")) { String g = a.length > 1 ? a[1] : ""; if (!open.remove(g)) open.add(g); return true; }
        if (k.equals("recommend")) applyRecommended(s);
        else if (k.equals("type")) s.budgetPercentMode = !s.budgetPercentMode;
        else if (k.equals("budget")) {
            if (s.budgetPercentMode) s.budgetPercent = Math.max(0.1f, Math.min(1f, s.budgetPercent + 0.1f * dir));
            else s.budgetFixed = Math.max(0f, s.budgetFixed + 250000f * dir);
        }
        else if (k.equals("cap")) s.perProjectCap = Math.max(250000f, s.perProjectCap + 500000f * dir);
        else if (k.equals("reserve")) s.reserve = Math.max(0f, s.reserve + 250000f * dir);
        else if (k.equals("upkeep")) s.newUpkeepCap = Math.max(0f, s.newUpkeepCap + 10000f * dir);
        else if (k.equals("pause")) s.pauseOnLoss = !s.pauseOnLoss;
        else if (k.equals("fees")) s.feeLevel = (s.feeLevel + 1) % 4;
        else if (k.equals("station")) {
            int i = 0;
            for (int j = 0; j < CASettings.STATION_IDS.length; j++) if (CASettings.STATION_IDS[j].equals(s.stationLine)) i = j;
            s.stationLine = CASettings.STATION_IDS[(i + 1) % CASettings.STATION_IDS.length];
        }
        else if (k.equals("cores")) s.useAICores = !s.useAICores;
        else if (k.equals("removals")) s.allowRemovals = !s.allowRemovals;
        else if (k.equals("alpha")) s.alphaMode = (Math.max(0, Math.min(2, s.alphaMode)) + 1) % 3;
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
        else if (k.equals("settleinc")) s.settlementMinIncome = Math.max(5000f, s.settlementMinIncome + 5000f * dir);
        return true;
    }
}
