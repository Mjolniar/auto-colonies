package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.loading.IndustrySpecAPI;

import java.util.*;

/**
 * Gives a name to monthly report rows another mod left without one. The Command tab's Income panel sorts rows by name
 * (IntelIncomePanel), and a row whose name is null crashes the game when you click a column to sort. Industrial
 * Evolution 4.1.b leaves such rows for Pet Storage and the Academy at other factions' markets (an "industries" row and
 * one per building). Twice a second, also while paused, every unnamed row in this month's and last month's report gets
 * the name of the building its key is the id of, or its key made readable ("industries" becomes "Industries"). Only
 * names are filled in; amounts and everything else are left alone.
 */
public class CAReportFix implements EveryFrameScript {
    static boolean logged = false;
    static Map<String, String> industryNames;
    private float timer = 0f;

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return true; }   // the Income panel pauses the game

    public void advance(float amount) {
        timer -= amount;
        if (timer > 0f) return;
        timer = 0.5f;
        fix();
    }

    /** Names every unnamed row in the current and previous report; returns how many it named. */
    public static int fix() {
        int n = 0;
        try {
            SharedData data = SharedData.getData();
            if (data == null) return 0;
            n += fix(data.getCurrentReport());
            n += fix(data.getPreviousReport());
        } catch (Throwable t) {
            if (!logged) { logged = true; CADiagnostics.LOG.warn(CADiagnostics.TAG + "couldn't check the monthly report for unnamed rows", t); }
            return n;
        }
        if (n > 0 && !logged) {
            logged = true;
            CADiagnostics.LOG.info(CADiagnostics.TAG + "named " + n + " monthly report rows another mod left unnamed, so sorting the Income panel can't crash");
        }
        return n;
    }

    static int fix(MonthlyReport report) {
        if (report == null || report.getRoot() == null) return 0;
        return children(report.getRoot(), 0);   // the root itself is never listed
    }

    static int children(MonthlyReport.FDNode node, int depth) {
        Map<String, MonthlyReport.FDNode> kids = node.getChildren();
        if (kids == null || kids.isEmpty() || depth > 16) return 0;
        int n = 0;
        for (Map.Entry<String, MonthlyReport.FDNode> e : new ArrayList<Map.Entry<String, MonthlyReport.FDNode>>(kids.entrySet())) {
            MonthlyReport.FDNode child = e.getValue();
            if (child == null) continue;
            if (child.name == null) { child.name = nameFor(e.getKey()); n++; }
            n += children(child, depth + 1);
        }
        return n;
    }

    /** A building's name when the key is a building id, else the key made readable. */
    static String nameFor(String key) {
        if (key == null || key.trim().isEmpty()) return "Other";
        String building = industryName(key);
        if (building != null) return building;
        String k = key.replace('_', ' ').trim();
        return Character.toUpperCase(k.charAt(0)) + k.substring(1);
    }

    static String industryName(String id) {
        if (industryNames == null) {
            Map<String, String> m = new HashMap<String, String>();
            try { for (IndustrySpecAPI s : Global.getSettings().getAllIndustrySpecs()) m.put(s.getId(), s.getName()); }
            catch (Throwable ignored) { }   // not loaded yet: readable keys instead
            industryNames = m;
        }
        return industryNames.get(id);
    }
}
