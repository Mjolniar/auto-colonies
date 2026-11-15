package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;

/**
 * 0.3.6.0: the Story page, first in the empire panel in story mode (and the only one besides Diagnostics while the Autopilot
 * is dormant): the core, its processing power, feeding cores, managed colonies, every ability with its On/Off button, the
 * story so far, and in dev mode the test tools.
 */
public class CASyncPage {

    static ButtonAPI button(TooltipMakerAPI t, String label, String id, boolean on, float w, float pad) {
        Color c = on ? Misc.getBasePlayerColor() : Misc.getGrayColor();
        return t.addButton(label, CASync.PREFIX + id, c, Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, w, 22f, pad);
    }

    /** A button with a line of text to its right. */
    static void row(TooltipMakerAPI t, String label, String id, boolean on, float w, String text, Color tc, String... hl) {
        ButtonAPI b = button(t, label, id, on, w, 4f);
        LabelAPI l = t.addPara(CALog.esc(text), 0f, tc, Misc.getHighlightColor(), hl);
        l.getPosition().rightOfMid(b, 12f);
        CALog.endRow(t, b);
    }

    static String feedLabel(String id, String label, int pts) { return CASync.isArmed("feed:" + id) ? "Confirm: +" + pts + " bandwidth" : label; }

    public static void render(TooltipMakerAPI t, float width) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor();
        t.addSectionHeading("The Autarch AI Core", Alignment.MID, 0f);
        if (CASync.notice != null) t.addPara(CALog.esc(CASync.notice), hl, 6f);

        // the core
        boolean awake = !CASync.dormant();
        String ph = CASync.phase();
        if (awake) t.addPara("The Autarch AI Core is online. It runs the abilities you switch on below, paid for with its bandwidth.", hl, 6f);
        else if (CASync.pulled()) t.addPara("The Autarch AI Core is out of its cradle, so the Colony Autopilot is dormant. Every ability and all of its bandwidth come back when the core goes back in.", bad, 6f);
        else if ("breaking".equals(ph)) t.addPara("Engineering is breaking the Autarch AI Core down. The Colony Autopilot is dormant.", hl, 6f);
        else if (!ph.isEmpty()) t.addPara("You have no Autarch AI Core. The Colony Autopilot on your TriPad is dormant.", gray, 6f);
        else if (CASync.seen(CATriPad.FOUND)) t.addPara("The Colony Autopilot on your TriPad is dormant. It needs an %s to run, and until then nothing at your colonies runs on its own.", 6f, hl, "Autarch-class AI core");
        else t.addPara("The Colony Autopilot on your TriPad is dormant.", gray, 6f);

        if (CASync.dormant() && CAAutarch.inCargo() > 0)
            row(t, CASync.pulled() ? "Reseat the core" : "Decide on the core", "core", true, 200f, "Your TriPad asks once you close this screen.", gray);
        else if (CASync.dormant() && CAAutarch.inCargo() == 0 && ph.isEmpty()) {
            String area = CAAutarch.area();
            t.addPara(area != null && CASync.seen(CATriPad.FOUND) ? "Your TriPad points to a derelict research station somewhere in the " + area + "."
                    : "You haven't found it yet.", gray, 4f);
        }
        if (awake && CASync.cradle()) row(t, "Pull the core", "pull", true, 200f, "This takes the cradled core out, and the Colony Autopilot goes dormant.", gray);

        // processing power
        if (CASync.installed()) {
            int[] f = CASync.fed();
            t.addSectionHeading("Bandwidth", Alignment.MID, 12f);
            t.addPara("The core has %s bandwidth. Your abilities use %s, and %s are free.", 6f, hl, "" + CASync.total(), "" + CASync.spent(), "" + Math.max(0, CASync.free()));
            int[] h = CASync.onHand();   // 0.3.7.2: the buttons are the core slots; no counts or explanations
            if (awake) {
                ButtonAPI a = button(t, feedLabel("a1", "1 Alpha (+" + CASyncCatalog.ALPHA_PP + ")", CASyncCatalog.ALPHA_PP), "feed:a1", h[2] > 0, 150f, 6f);
                ButtonAPI b = button(t, feedLabel("b1", "1 Beta (+" + CASyncCatalog.BETA_PP + ")", CASyncCatalog.BETA_PP), "feed:b1", h[1] > 0, 150f, 0f);
                b.getPosition().rightOfTop(a, 6f);
                ButtonAPI g = button(t, feedLabel("g1", "1 Gamma (+" + CASyncCatalog.GAMMA_PP + ")", CASyncCatalog.GAMMA_PP), "feed:g1", h[0] > 0, 150f, 0f);
                g.getPosition().rightOfTop(b, 6f);
                int all = CASyncCatalog.points(h[0], h[1], h[2]);
                ButtonAPI x = button(t, feedLabel("all", "All of them (+" + all + ")", all), "feed:all", all > 0, 180f, 0f);
                x.getPosition().rightOfTop(g, 6f);
                CALog.endRow(t, a);
                int[] keep = CASync.keepForFeeding();
                row(t, "Change", "keep", true, 86f, "Keep cores for feeding is " + (CASync.keepCores() ? "on" : "off")
                        + ". Autopilot leaves " + CASyncCatalog.cores(keep[2], keep[1], keep[0]) + " in storage for your next ability.", gray);
            }
        }

        if (!CALunaSync.active()) settingsRow(t);   // 0.3.6.2: switch back from here while dormant, without LunaLib
        if (!awake) { story(t); devTools(t); return; }

        // managed colonies (0.3.7.2: with Approve mode bought)
        if (CASync.has("approve")) {
            t.addSectionHeading("Managed colonies", Alignment.MID, 12f);
            t.addPara("%s colonies in Approve or Auto: one, plus %s more at %s bandwidth each.", 6f, hl,
                    "" + CASync.managedCap(), "" + CASync.extraColonies(), "" + CASyncCatalog.COLONY_PP);
            ButtonAPI plus = button(t, "+1 colony", "col+", CASync.free() >= CASyncCatalog.COLONY_PP, 120f, 4f);
            ButtonAPI minus = button(t, "-1 colony", "col-", CASync.extraColonies() > 0, 120f, 0f);
            minus.getPosition().rightOfTop(plus, 6f);
            CALog.endRow(t, plus);
        }

        // abilities
        // 0.3.7.3: by tier. Open tiers list their abilities; the next tier shows what opens it; later tiers don't show
        int open = CASync.openTier();
        for (int ti = 0; ti < CASyncCatalog.TIERS.length; ti++) {
            List<CASyncCatalog.Ability> list = CASyncCatalog.inTier(ti);
            if (list.isEmpty()) continue;
            if (ti > open) {
                int[] pr = CASync.tierProgress(open);
                t.addSectionHeading(CASyncCatalog.TIERS[ti], Alignment.MID, 12f);
                t.addPara("Opens with %s of the " + CASyncCatalog.TIERS[open] + " abilities. You have %s.", 6f, gray, hl, "" + pr[1], "" + pr[0]);
                break;
            }
            t.addSectionHeading(CASyncCatalog.TIERS[ti], Alignment.MID, 12f);
            for (CASyncCatalog.Ability a : list) {
                boolean on = CASync.ticked(a.id);
                boolean needs = a.requires != null && !CASync.ticked(a.requires);
                boolean afford = CASync.free() >= a.cost;
                String desc = a.name + " (" + a.cost + " bandwidth). " + a.text
                        + (needs ? " It needs " + CASyncCatalog.get(a.requires).name + " first." : "");
                row(t, on ? "On" : "Off", "tick:" + a.id, on || (!needs && afford), 60f, desc, on ? Misc.getTextColor() : gray, a.name);
            }
        }

        story(t);
        devTools(t);
    }

    static void story(TooltipMakerAPI t) {
        List<String> read = CASync.readable();
        if (read.isEmpty()) return;
        t.addSectionHeading("The story so far", Alignment.MID, 14f);
        for (String b : read) row(t, "Read again", "read:" + b, true, 120f, beatTitle(b), Misc.getGrayColor());
    }

    /** Test tools, in dev mode only. */
    static void devTools(TooltipMakerAPI t) {
        if (!CASync.devMode()) return;
        t.addSectionHeading("Test tools (dev mode)", Alignment.MID, 14f);
        ButtonAPI a = button(t, "5 of each core", "beta:cores", true, 140f, 6f);
        ButtonAPI b = button(t, "Autarch AI Core", "beta:core", true, 140f, 0f); b.getPosition().rightOfTop(a, 6f);
        ButtonAPI c = button(t, "+100 bandwidth", "beta:pp", true, 140f, 0f); c.getPosition().rightOfTop(b, 6f);
        ButtonAPI d = button(t, "A year passes", "beta:year", true, 140f, 0f); d.getPosition().rightOfTop(c, 6f);
        CALog.endRow(t, a);
        ButtonAPI e = button(t, "Story beats again", "beta:beats", true, 160f, 6f);
        ButtonAPI f = button(t, "Clear story state", "beta:reset", true, 160f, 0f); f.getPosition().rightOfTop(e, 6f);
        CALog.endRow(t, e);
    }

    static String beatTitle(String b) {
        if (CATriPad.FOUND.equals(b)) return "Dormant firmware";
        if (CATriPad.INSTALL.equals(b)) return "Autarch online";
        if (CATriPad.FEED.equals(b)) return "Integrating";
        if (CATriPad.PULL.equals(b)) return "Pulling the core";
        if (CATriPad.BROKEN.equals(b)) return "Broken down";
        if (CATriPad.RETURN.equals(b)) return "Another signature";
        return b;
    }

    /** The empire panel's list entry for this page. */
    public static String navLabel() { return CASync.dormant() ? "Story (dormant)" : "Story (" + Math.max(0, CASync.free()) + " bandwidth free)"; }

    /** The Story mode line, first on the intel Settings page while LunaLib is off. Its button is CASync's (handle: "storymode"). */
    public static void settingsRow(TooltipMakerAPI t) {
        ButtonAPI b = t.addButton("CYCLE", CASync.PREFIX + "storymode", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID,
                CutStyle.TL_BR, 86f, 20f, 4f);
        LabelAPI l = t.addPara("%s: %s.", 0f, Misc.getHighlightColor(), "Story mode", CASync.on() ? "On" : "Off");
        l.getPosition().rightOfMid(b, 12f);
        CALog.endRow(t, b);
        t.addPara("On: the Colony Autopilot starts dormant, and a storyline unlocks it. Switching back keeps this save's story progress.", Misc.getGrayColor(), 2f);
    }

    /** The colony panel's Fast buildout section while the ability is off. */
    public static void buildoutOff(TooltipMakerAPI t, com.fs.starfarer.api.campaign.econ.MarketAPI m) {
        t.addPara("Loses %s a month. Fast buildout is off; it costs %s bandwidth on the Story page.", 6f, Misc.getNegativeHighlightColor(),
                Misc.getDGSCredits(-m.getNetIncome()), "" + CASyncCatalog.get("buildout").cost);
    }

    // 0.3.7.1: renderDormantColony is gone: the dormant colony panel shows this page and Diagnostics (CAColonyDialog.pages)
}
