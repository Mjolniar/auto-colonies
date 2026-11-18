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

    /** A button with its text to the right. 0.3.9.10: the text wraps inside the page and the next row starts under it. */
    static void row(TooltipMakerAPI t, String label, String id, boolean on, float w, String text, Color tc, String... hl) {
        ButtonAPI b = button(t, label, id, on, w, 4f);
        LabelAPI l = t.addPara(CALog.esc(text), 0f, tc, Misc.getHighlightColor(), hl);
        CALog.endRow(t, b, CALog.beside(t, b, l, 12f, w, pageWidth));
    }

    static String feedLabel(String id, String label, int pts) { return CASync.isArmed("feed:" + id) ? "Confirm: +" + pts + " bandwidth" : label; }

    /** 0.3.10.2: the page's width, set by render, for row's wrap (CAReset clears it). */
    static float pageWidth = 0f;

    public static void render(TooltipMakerAPI t, float width) {
        pageWidth = width;
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor();
        t.addSectionHeading("The Autarch AI Core", Alignment.MID, 0f);
        if (CASync.notice != null) t.addPara(CASync.notice, hl, 6f);   // 0.3.8.6: no CALog.esc: plain addPara text and highlight values show as is

        // the core
        boolean awake = !CASync.dormant();
        String ph = CASync.phase();
        if (awake) t.addPara("The Autarch AI Core is online, and it runs the abilities you switch on below using its bandwidth.", hl, 6f);
        else if (CASync.pulled()) t.addPara("The Autarch AI Core is out of its cradle, so the Colony Autopilot is dormant. Once the core's back in, you get every ability and all of its bandwidth again.", bad, 6f);
        else if ("breaking".equals(ph)) t.addPara("The Colony Autopilot is dormant while Engineering breaks the Autarch AI Core down.", hl, 6f);
        else if (!ph.isEmpty()) t.addPara("You don't have an Autarch AI Core, so your TriPad's Colony Autopilot is dormant.", gray, 6f);
        else if (CASync.seen(CATriPad.FOUND)) t.addPara("The Colony Autopilot on your TriPad is dormant. It needs an %s to run, and until then your colonies won't run on their own.", 6f, hl, "Autarch-class AI core");
        else t.addPara("The Colony Autopilot on your TriPad is dormant.", gray, 6f);

        if (CASync.dormant() && CAAutarch.inCargo() > 0)
            row(t, CASync.pulled() ? "Reseat the core" : "Decide on the core", "core", true, 200f, "Your TriPad asks once you close this screen.", gray);
        else if (CASync.dormant() && CAAutarch.inCargo() == 0 && ph.isEmpty() && !CAAutarch.anywhere()) {   // 0.3.8.9: not while it sits in a storage tab
            String area = CAAutarch.area();
            t.addPara(area != null && CASync.seen(CATriPad.FOUND) ? "Your TriPad points to a derelict research station somewhere in the " + area + "."
                    : "You haven't found it yet.", gray, 4f);
        }
        if (awake && CASync.cradle()) row(t, "Pull the core", "pull", true, 200f, "This takes the cradled core out, so the Colony Autopilot goes dormant.", gray);

        // processing power
        if (CASync.installed()) {   // 0.3.8.9: the unused fed() read is removed
            t.addSectionHeading("Bandwidth", Alignment.MID, 12f);
            t.addPara("The core has %s bandwidth. Your abilities use %s, and %s are free.", 6f, hl, "" + CASync.total(), "" + CASync.spent(), "" + Math.max(0, CASync.free()));
            float mult = CASync.costMult(), share = CASync.falloffNow();   // 0.3.10.4
            if (mult > 1f) t.addPara("Your mods hand out a lot of AI cores, so new purchases cost %s more.", 3f, hl, Math.round((mult - 1f) * 100f) + "%");
            if (share < 1f) t.addPara("Cores you feed now give %s of their bandwidth.", 3f, hl, Math.round(share * 100f) + "%");
            int[] h = CASync.onHand();   // 0.3.7.2: the buttons are the core slots; no counts or explanations
            if (awake) {
                int pa = CASync.gainNow(0, 0, 1), pb = CASync.gainNow(0, 1, 0), pg = CASync.gainNow(1, 0, 0);   // 0.3.10.4: falloff
                ButtonAPI a = button(t, feedLabel("a1", "1 Alpha (+" + pa + ")", pa), "feed:a1", h[2] > 0, 150f, 6f);
                ButtonAPI b = button(t, feedLabel("b1", "1 Beta (+" + pb + ")", pb), "feed:b1", h[1] > 0, 150f, 0f);
                b.getPosition().rightOfTop(a, 6f);
                ButtonAPI g = button(t, feedLabel("g1", "1 Gamma (+" + pg + ")", pg), "feed:g1", h[0] > 0, 150f, 0f);
                g.getPosition().rightOfTop(b, 6f);
                int all = CASync.gainNow(h[0], h[1], h[2]);
                ButtonAPI x = button(t, feedLabel("all", "All of them (+" + all + ")", all), "feed:all", all > 0, 180f, 0f);
                x.getPosition().rightOfTop(g, 6f);
                CALog.endRow(t, a);
                t.addPara("On hand: %s Alpha, %s Beta, %s Gamma.", 4f, gray, hl, "" + h[2], "" + h[1], "" + h[0]);   // 0.3.7.5: cargo and Autopilot Storage
            }
        }

        if (!CALunaSync.active()) settingsRow(t);   // 0.3.6.2: switch back from here while dormant, without LunaLib
        if (!awake) { story(t); devTools(t); return; }

        // managed colonies (0.3.7.2: with Approve mode bought)
        if (CASync.has("approve")) {
            t.addSectionHeading("Managed colonies", Alignment.MID, 12f);
            t.addPara("%s colonies can be on Approve or Auto (one, plus %s more). The next costs %s bandwidth.", 6f, hl,
                    "" + CASync.managedCap(), "" + CASync.extraColonies(), "" + CASync.colonyCost());
            if (CAStoryLimit.now() > 0) t.addPara("They add %s to your colony limit.", 3f, hl, "" + CAStoryLimit.now());   // 0.3.10.3
            ButtonAPI plus = button(t, "+1 colony", "col+", CASync.free() >= CASync.colonyCost() && CASync.extraColonies() < CASyncCatalog.MAX_COLONIES, 120f, 4f);
            ButtonAPI minus = button(t, "-1 colony", "col-", CASync.extraColonies() > 0, 120f, 0f);
            minus.getPosition().rightOfTop(plus, 6f);
            CALog.endRow(t, plus);
        }
        // 0.3.8.0: the Autarch administrator's colonies
        if (CASync.has("autarchAdmin")) {
            t.addSectionHeading("Autarch administrator", Alignment.MID, 12f);
            t.addPara("It governs %s of up to %s colonies (one, plus %s more). The next costs %s bandwidth.", 6f, hl,
                    "" + CAAutarchAdmin.count(), "" + CASync.seats(), "" + CASync.extraSeats(), "" + CASync.seatCost());
            ButtonAPI plus = button(t, "+1 colony", "seat+", CASync.free() >= CASync.seatCost() && CASync.seats() < CASync.managedCap()
                    && CASync.extraSeats() < CASyncCatalog.MAX_SEATS, 120f, 4f);
            ButtonAPI minus = button(t, "-1 colony", "seat-", CASync.extraSeats() > 0, 120f, 0f);
            minus.getPosition().rightOfTop(plus, 6f);
            CALog.endRow(t, plus);
        }

        // abilities
        // 0.3.7.3: by tier. Open tiers list their abilities; the next tier shows what opens it; later tiers don't show
        int open = CASync.openTier();
        for (int ti = 0; ti < CASyncCatalog.TIERS.length; ti++) {
            List<CASyncCatalog.Pack> list = CASyncCatalog.inTier(ti);
            if (list.isEmpty()) continue;
            if (ti > open) {
                int[] pr = CASync.tierProgress(open);
                t.addSectionHeading(CASyncCatalog.TIERS[ti], Alignment.MID, 12f);
                t.addPara("It opens with %s of the " + CASyncCatalog.TIERS[open] + " abilities (you have %s).", 6f, gray, hl, "" + pr[1], "" + pr[0]);
                break;
            }
            t.addSectionHeading(CASyncCatalog.TIERS[ti], Alignment.MID, 12f);
            for (CASyncCatalog.Pack p : list) {   // 0.3.7.4: packages
                boolean on = CASync.ticked(p.id);
                boolean needs = p.requires != null && !CASync.ticked(p.requires);
                int cost = on ? CASync.paidFor(p) : CASync.price(p);   // 0.3.10.4: what you paid, or the price now
                boolean afford = CASync.free() >= cost;
                String desc = p.name + " (" + cost + " bandwidth). " + p.desc   // 0.3.7.5: what it does
                        + (needs ? " It needs " + CASyncCatalog.pack(p.requires).name + " first." : "");
                row(t, on ? "On" : "Off", "tick:" + p.id, on || (!needs && afford), 60f, desc, on ? Misc.getTextColor() : gray, p.name);
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
        if (CATriPad.INSTALL.equals(b)) return "Autarch AI Core online";
        if (CATriPad.FEED.equals(b)) return "Integrating";
        if (CATriPad.TIER2.equals(b)) return "The partition";   // 0.3.8.3
        if (CATriPad.TIER3.equals(b)) return "Procurement records";
        if (CATriPad.TIER4.equals(b)) return "Transfer order";
        if (CATriPad.RECORD.equals(b)) return "The journal";
        if (CATriPad.PULL.equals(b)) return "Pulling the core";
        if (CATriPad.BROKEN.equals(b)) return "Broken down";
        if (CATriPad.RETURN.equals(b)) return "Another signature";
        return b;
    }

    /** The empire panel's list entry for this page. */
    public static String navLabel() { return "Story"; }   // 0.3.7.4: the bandwidth is on the page; the longer label ran past the button

    /** The Story mode line, first on the intel Settings page while LunaLib is off. Its button is CASync's (handle: "storymode"). */
    public static void settingsRow(TooltipMakerAPI t) {
        ButtonAPI b = t.addButton("CYCLE", CASync.PREFIX + "storymode", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID,
                CutStyle.TL_BR, 86f, 20f, 4f);
        LabelAPI l = t.addPara("%s: %s.", 0f, Misc.getHighlightColor(), "Story mode", CASync.on() ? "On" : "Off");
        l.getPosition().rightOfMid(b, 12f);
        CALog.endRow(t, b);
        t.addPara("When it's On, the Colony Autopilot starts dormant and the story unlocks it. Switching back keeps your story progress.", Misc.getGrayColor(), 2f);
    }

    /** The colony panel's Fast buildout section while the ability is off. */
    public static void buildoutOff(TooltipMakerAPI t, com.fs.starfarer.api.campaign.econ.MarketAPI m) {
        t.addPara("It's losing %s a month, and fast buildout needs %s on the Story page.", 6f, Misc.getNegativeHighlightColor(),
                Misc.getDGSCredits(-m.getNetIncome()), CASyncCatalog.buy("buildout"));
    }

    // 0.3.7.1: renderDormantColony is gone: the dormant colony panel shows this page and Diagnostics (CAColonyDialog.pages)
}
