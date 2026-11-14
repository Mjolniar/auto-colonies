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

    static String feedLabel(String id, String label, int pts) { return CASync.isArmed("feed:" + id) ? "Confirm: +" + pts + " PP" : label; }

    public static void render(TooltipMakerAPI t, float width) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor();
        t.addSectionHeading("The Autarch AI Core", Alignment.MID, 0f);
        if (CASync.notice != null) t.addPara(CALog.esc(CASync.notice), hl, 6f);

        // the core
        boolean awake = !CASync.dormant();
        String ph = CASync.phase();
        if (awake) t.addPara("The Autarch AI Core is online. It runs the abilities you switch on below, paid for with its processing power (PP).", hl, 6f);
        else if (CASync.pulled()) t.addPara("The Autarch AI Core is out of its cradle, so the Colony Autopilot is dormant. Every ability and all of its PP come back when the core goes back in.", bad, 6f);
        else if ("breaking".equals(ph)) t.addPara("Engineering is breaking the Autarch AI Core down. The Colony Autopilot is dormant.", hl, 6f);
        else if (!ph.isEmpty()) t.addPara("You have no Autarch AI Core. The Colony Autopilot on your TriPad is dormant.", gray, 6f);
        else t.addPara("The Colony Autopilot on your TriPad is dormant. It needs the %s to run, and until then nothing at your colonies runs on its own.", 6f, hl, "Autarch AI Core");

        if (CASync.dormant() && CAAutarch.inCargo() > 0)
            row(t, CASync.pulled() ? "Reseat the core" : "Decide on the core", "core", true, 200f, "Your TriPad asks once you close this screen.", gray);
        else if (CASync.dormant() && CAAutarch.inCargo() == 0 && ph.isEmpty()) {
            String sys = CAAutarch.system();
            t.addPara(sys != null && CASync.seen(CATriPad.FOUND) ? "Your TriPad points to a derelict research station in the " + sys + " system."
                    : "You haven't found it yet.", gray, 4f);
        }
        if (awake && CASync.cradle()) row(t, "Pull the core", "pull", true, 200f, "This takes the cradled core out, and the Colony Autopilot goes dormant.", gray);

        // processing power
        if (CASync.installed()) {
            int[] f = CASync.fed();
            t.addSectionHeading("Processing power", Alignment.MID, 12f);
            t.addPara("The core has %s PP. Your abilities use %s, and %s are free.", 6f, hl, "" + CASync.total(), "" + CASync.spent(), "" + Math.max(0, CASync.free()));
            t.addPara("That is the core's own " + CASyncCatalog.BASE_PP + ", plus " + CASyncCatalog.cores(f[0], f[1], f[2]) + " you fed it.", gray, 4f);
            int[] cargo = CASync.inCargo(), store = CASync.inStorage(), h = CASync.onHand();
            t.addPara("You have %s Alpha, %s Beta and %s Gamma cores on hand, in your cargo and in Autopilot Storage.", 6f, hl, "" + h[2], "" + h[1], "" + h[0]);
            t.addPara("Your cargo has " + CASyncCatalog.cores(cargo[0], cargo[1], cargo[2]) + "; Autopilot Storage has " + CASyncCatalog.cores(store[0], store[1], store[2]) + ".", gray, 2f);
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
                t.addPara("A core you feed it becomes part of it and is used up.", gray, 4f);
                int[] keep = CASync.keepForFeeding();
                row(t, "Change", "keep", true, 86f, "Keep cores for feeding is " + (CASync.keepCores() ? "on" : "off")
                        + ". Autopilot leaves " + CASyncCatalog.cores(keep[2], keep[1], keep[0]) + " in storage for your next ability.", gray);
            }
        }

        if (!CALunaSync.active()) settingsRow(t);   // 0.3.6.2: switch back from here while dormant, without LunaLib
        if (!awake) { story(t); devTools(t); return; }

        // managed colonies
        t.addSectionHeading("Managed colonies", Alignment.MID, 12f);
        int cap = CASync.managedCap();
        t.addPara("It can run %s colonies in Approve or Auto: the first one, plus %s more at %s PP each.", 6f, hl,
                "" + cap, "" + CASync.extraColonies(), "" + CASyncCatalog.COLONY_PP);
        ButtonAPI plus = button(t, "+1 colony", "col+", CASync.free() >= CASyncCatalog.COLONY_PP, 120f, 4f);
        ButtonAPI minus = button(t, "-1 colony", "col-", CASync.extraColonies() > 0, 120f, 0f);
        minus.getPosition().rightOfTop(plus, 6f);
        CALog.endRow(t, plus);

        // abilities
        List<CASyncCatalog.Ability> list = CASyncCatalog.available();
        for (int gi = 0; gi < CASyncCatalog.GROUPS.length; gi++) {
            boolean any = false;
            for (CASyncCatalog.Ability a : list) if (a.group == gi) { any = true; break; }
            if (!any) continue;
            t.addSectionHeading(CASyncCatalog.GROUPS[gi], Alignment.MID, 12f);
            for (CASyncCatalog.Ability a : list) {
                if (a.group != gi) continue;
                boolean on = CASync.ticked(a.id);
                boolean needs = a.requires != null && !CASync.ticked(a.requires);
                boolean afford = CASync.free() >= a.cost;
                String desc = a.name + " (" + a.cost + " PP). " + a.text
                        + (needs ? " It needs " + CASyncCatalog.get(a.requires).name + " first." : "");
                row(t, on ? "On" : "Off", "tick:" + a.id, on || (!needs && afford), 60f, desc, on ? Misc.getTextColor() : gray, a.name);
            }
        }
        t.addPara("While an ability is off, the settings it covers stay off, and their rows on the Settings page say what they need. The values you set come back when you switch it on.", gray, 8f);

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
        ButtonAPI c = button(t, "+100 PP", "beta:pp", true, 100f, 0f); c.getPosition().rightOfTop(b, 6f);
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
    public static String navLabel() { return CASync.dormant() ? "Story (dormant)" : "Story (" + Math.max(0, CASync.free()) + " PP free)"; }

    /** The Story mode line, first on the intel Settings page while LunaLib is off. Its button is CASync's (handle: "storymode"). */
    public static void settingsRow(TooltipMakerAPI t) {
        ButtonAPI b = t.addButton("CYCLE", CASync.PREFIX + "storymode", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID,
                CutStyle.TL_BR, 86f, 20f, 4f);
        LabelAPI l = t.addPara("%s: %s.", 0f, Misc.getHighlightColor(), "Story mode", CASync.on() ? "On" : "Off");
        l.getPosition().rightOfMid(b, 12f);
        CALog.endRow(t, b);
        t.addPara("On: the Colony Autopilot stays dormant until you find the Autarch AI Core, then runs the abilities you buy with AI cores. "
                + "Switching back keeps this save's story progress.", Misc.getGrayColor(), 2f);
    }

    /** The colony panel's Fast buildout section while the ability is off. */
    public static void buildoutOff(TooltipMakerAPI t, com.fs.starfarer.api.campaign.econ.MarketAPI m) {
        t.addPara("Loses %s a month. Fast buildout is off; it costs %s PP on the Story page.", 6f, Misc.getNegativeHighlightColor(),
                Misc.getDGSCredits(-m.getNetIncome()), "" + CASyncCatalog.get("buildout").cost);
    }

    /** The colony panel while the Autopilot is dormant. */
    public static void renderDormantColony(TooltipMakerAPI t, String name) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        t.addTitle(name + ": Colony Autopilot");
        t.addSectionHeading("Colonial Administration Suite", Alignment.MID, 0f);
        if (CASync.pulled()) t.addPara("The Autarch AI Core is out of its cradle. You can put it back from the Story page of the Colony Autopilot intel.", hl, 6f);
        else t.addPara("STATUS: DORMANT. REQUIRED COGNITIVE SUBSTRATE NOT PRESENT.", hl, 6f);
        t.addPara("This old Domain program on your TriPad plans, builds and runs colonies. It needs the Autarch AI Core to run on.", gray, 6f);
        t.addPara("The Story page of the Colony Autopilot intel shows what it needs and what it can do.", gray, 6f);
    }
}
