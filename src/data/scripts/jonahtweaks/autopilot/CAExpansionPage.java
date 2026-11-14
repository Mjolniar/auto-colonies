package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;

/**
 * 0.3.0.0 (WS-F2, research 05 section 6.1): the Expansion page of the empire panel. A heading and one line on what
 * Auto-expansion does and where it is set, the last daily check when today's is not in yet, then CAExpansion.render
 * (mode, scope, money, colony limit, the proposal with Approve and Reject, expeditions under way with days left, the best
 * five candidates with value, cost, plan and why, Found now and Never this planet, the never list).
 *
 * No Nexerelin classes; CAExpansion keeps Nexerelin's colony fleets in CAExpansionNex. Buttons are CAExpansion's
 * {"expand", action, id} (CAIntel calls CAExpansion.handle). Found now spends credits, so it asks first: the confirmation
 * shows the method, price, fee, an administrator's hire when the colony limit needs one, and the days, from
 * CAExpansion.costFor on the click (the same check launch() runs). Drawing never scans and never launches.
 */
public final class CAExpansionPage {
    /** The CANav page id. */
    static final String PAGE = "expand";

    private CAExpansionPage() { }

    /** The nav label: "Expansion", or "Expansion (1 to approve)" while a proposal waits. */
    static String label(CAData d) { return d.expandPendingId != null ? "Expansion (1 to approve)" : "Expansion"; }

    /** The page. */
    static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color gray = Misc.getGrayColor();
        boolean nex = Global.getSettings().getModManager().isModEnabled("nexerelin");
        CATheme.heading(t, "Expansion", 0f);
        t.addPara("Founds colonies by " + (nex ? "Nexerelin colony fleet or " : "") + "Autopilot crew, at cost plus the fee.", gray, 6f);
        String last = CAExpansion.SCAN.status;
        boolean today = last != null && CAExpansion.SCAN.statusDay == CATime.today();
        if (CAExpansion.mode(s) > 0 && !today)
            t.addPara(last != null ? "Last daily check: " + CALog.esc(last) : "Not checked yet.", gray, 4f);
        try { CAExpansion.render(t, d, s); }
        catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "expansion page failed", e);
            t.addPara(CATheme.NOT_SHOWN, gray, 6f);
        }
    }

    // ---------------- confirmation ----------------

    /** Found now asks first: it spends credits at once. */
    static boolean needsConfirm(String action) { return "found".equals(action); }

    /** The candidate from the kept scan, or null. */
    static CAExpansion.Prospect prospect(String planetId) {
        List<CAExpansion.Prospect> l = CAExpansion.cached();
        if (l == null || planetId == null) return null;
        for (CAExpansion.Prospect p : l) if (planetId.equals(p.planetId)) return p;
        return null;
    }

    /** The Found now confirmation: what it costs now and how it goes, or why it can't go. */
    static void confirmPrompt(TooltipMakerAPI t, String action, String planetId, CAData d, CASettings s) {
        if (!"found".equals(action)) return;
        Color hl = Misc.getHighlightColor(), bad = Misc.getNegativeHighlightColor();
        CAExpansion.Prospect p = prospect(planetId);
        if (p == null) { t.addPara("Send a colony expedition to this planet now?", 0f); return; }
        t.addPara("Send a colony expedition to %s in %s now?", 0f, hl, p.planetName, p.systemName);
        CAExpansion.ExpansionCost c = null;
        try { c = CAExpansion.costFor(d, s, p); }
        catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "expansion cost for the confirmation failed", e); }
        if (c == null) {
            t.addPara("About %s plus a %s fee.", 6f, hl, Misc.getDGSCredits(p.cost), Misc.getDGSCredits(p.fee));
        } else if (c.why != null) {
            t.addPara("It can't go now: " + CALog.esc(c.why), bad, 6f);
            return;
        } else {
            t.addPara("%s from %s, about %s days. %s plus a %s fee.", 6f, hl, CAExpansion.methodName(c.method),
                    c.from == null ? "your nearest colony" : c.from.getName(), String.valueOf(Math.round(c.days)),
                    Misc.getDGSCredits(c.price), Misc.getDGSCredits(c.fee));
            if (c.hire != null) t.addPara("Administrator: %s plus a %s fee, %s a month.", 4f, hl,
                    Misc.getDGSCredits(c.hire.price), Misc.getDGSCredits(c.hire.fee), Misc.getDGSCredits(c.hire.salary));
            if (CABuild.credits() - c.total < s.reserve)
                t.addPara("Over your credit reserve (%s). It does not go.", 4f, bad, Misc.getDGSCredits(s.reserve));
        }
        t.addPara("Plan: %s, goal %s. %s a month at size %s. Payback %s months.", 6f, hl, p.plan, CAExpansion.goalLabel(p.goal),
                Misc.getDGSCredits(p.value), String.valueOf(CAExpansion.VALUE_SIZE), p.payback >= 1000f ? "over 1,000" : String.valueOf(Math.max(1, Math.round(p.payback))));
    }
}
