package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
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
        int open = CASync.openTier(), running = CASync.runningTier();   // 0.3.13.1: tiers above running are paused
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
            boolean paused = ti > running;
            if (paused) {
                int[] pr = CASync.tierProgress(running);
                t.addPara("Paused until " + CASyncCatalog.TIERS[running] + " has %s packages on (it has %s).", 6f, gray, hl, "" + pr[1], "" + pr[0]);
            }
            tileGrid(t, list, paused);   // 0.3.13.1: picture tiles for everyone
        }

        story(t);
        devTools(t);
    }

    // ---------------- picture tiles (0.3.13.1: for everyone) ----------------

    static final float TILE_PAD = 8f, TILE_GAP = 8f, IMG_W = 96f, IMG_H = 48f, TILE_BTN_W = 70f, TILE_BTN_H = 22f;

    /** A picture for each package: i: an industry's image, c: a commodity's icon, s: a special item's icon (the game's own art). */
    static final String[][] ART = {
            {"modes", "i:population"}, {"upkeep", "i:orbitalstation"}, {"supply", "i:waystation"}, {"staff", "c:beta_core"},
            {"economy", "i:commerce"}, {"security", "i:patrolhq"}, {"projects", "i:lightindustry"}, {"growth", "i:farming"},
            {"industry", "i:heavyindustry"}, {"frontier", "i:spaceport"}, {"warfare", "i:militarybase"}, {"megaprojects", "i:orbitalworks"},
            {"dominion", "i:megaport"}, {"warlord", "i:highcommand"}, {"parallel", "i:refining"}, {"instant", "s:pristine_nanoforge"},
            {"terraforming", "s:soil_nanites"}, {"autarch", "c:alpha_core"}, {"uafHire", "i:cryosanctum"}, {"historian", "i:techmining"},
            {"fastResearch", "s:synchrotron"}, {"repairs", "s:fullerene_spool"}, {"outfitting", "i:starfortress"}};

    /** Pure: the art key for a package, or null. */
    static String artOf(String packId) {
        for (String[] a : ART) if (a[0].equals(packId)) return a[1];
        return null;
    }

    /** The sprite path for an art key, read from the game's specs (so art mods change it too), or null. */
    static String sprite(String key) {
        if (key == null || key.length() < 3) return null;
        String id = key.substring(2);
        try {
            if (key.startsWith("i:")) return Global.getSettings().getIndustrySpec(id).getImageName();
            if (key.startsWith("c:")) return Global.getSettings().getCommoditySpec(id).getIconName();
            if (key.startsWith("s:")) return Global.getSettings().getSpecialItemSpec(id).getIconName();
        } catch (Throwable ignored) { }
        return null;
    }

    /** Pure: tiles to a row on a page this wide: three from 760 px, else two. */
    static int columns(float width) { return width >= 760f ? 3 : 2; }

    /** One tier's packages as picture tiles, in rows of columns(pageWidth). */
    static void tileGrid(TooltipMakerAPI t, List<CASyncCatalog.Pack> list, boolean paused) {
        float width = Math.max(400f, (pageWidth > 0f ? pageWidth : 790f) - CALog.SCROLL_ROOM);
        int cols = columns(width);
        float tileW = (width - TILE_GAP * (cols - 1)) / cols;
        for (int start = 0; start < list.size(); start += cols) {
            List<CASyncCatalog.Pack> row = list.subList(start, Math.min(list.size(), start + cols));
            List<CustomPanelAPI> panels = new java.util.ArrayList<CustomPanelAPI>();
            UIComponentAPI first = null, prev = null;
            float rowH = 0f;
            for (CASyncCatalog.Pack p : row) {
                boolean ticked = CASync.ticked(p.id), needs = p.requires != null && !CASync.ticked(p.requires);
                boolean on = ticked && !paused;   // 0.3.13.1: a paused tier's packages stay bought but don't run
                int cost = ticked ? CASync.paidFor(p) : CASync.price(p);
                boolean afford = CASync.free() >= cost, can = ticked || (!needs && afford && !paused);
                CustomPanelAPI panel = Global.getSettings().createCustom(tileW, 100f, new Tile(sprite(artOf(p.id)), on, !can || paused));
                UIComponentAPI c = t.addCustom(panel, first == null ? 8f : 0f);
                if (prev != null) c.getPosition().rightOfTop(prev, TILE_GAP);
                if (first == null) first = c;
                prev = c;
                panels.add(panel);
                float inner = tileW - TILE_PAD;   // from the tile's left edge to the text's right edge, less the pad
                // name and cost beside the picture
                LabelAPI name = t.addPara(p.name, on ? Misc.getHighlightColor() : can ? Misc.getTextColor() : Misc.getGrayColor(), 0f);
                name.autoSizeToWidth(inner - IMG_W - 2f * TILE_PAD);
                name.getPosition().rightOfTop(c, -(tileW - IMG_W - 2f * TILE_PAD)).setYAlignOffset(-TILE_PAD);
                String state = paused && ticked ? "Paused, " + cost + " bandwidth"
                        : needs && !ticked ? "Needs " + CASyncCatalog.pack(p.requires).name : cost + " bandwidth" + (!ticked && !afford ? ", " + Math.max(0, CASync.free()) + " free" : "");
                LabelAPI cl = t.addPara(state, on ? Misc.getTextColor() : Misc.getGrayColor(), 0f);
                cl.autoSizeToWidth(inner - IMG_W - 2f * TILE_PAD);
                cl.getPosition().rightOfTop(c, -(tileW - IMG_W - 2f * TILE_PAD)).setYAlignOffset(-(TILE_PAD + name.getPosition().getHeight() + 2f));
                // what it does, under the picture
                LabelAPI desc = t.addPara(p.desc, on ? Misc.getTextColor() : Misc.getGrayColor(), 0f);
                desc.autoSizeToWidth(inner - TILE_PAD);
                float descTop = TILE_PAD + Math.max(IMG_H, name.getPosition().getHeight() + 2f + cl.getPosition().getHeight()) + 6f;
                desc.getPosition().rightOfTop(c, -(tileW - TILE_PAD)).setYAlignOffset(-descTop);
                float btnTop = descTop + desc.getPosition().getHeight() + 6f;
                ButtonAPI b = button(t, ticked ? "On" : "Off", "tick:" + p.id, can, TILE_BTN_W, 0f);
                b.getPosition().rightOfTop(c, -(tileW - TILE_PAD)).setYAlignOffset(-btnTop);
                rowH = Math.max(rowH, btnTop + TILE_BTN_H + TILE_PAD);
            }
            for (CustomPanelAPI panel : panels) panel.getPosition().setSize(tileW, rowH);
            CALog.endRow(t, first, TILE_GAP);
        }
    }

    /** A tile's background, border and picture: lit when the package is on, dim and gray when it's off, darker when it can't be had now. */
    static final class Tile extends BaseCustomUIPanelPlugin {
        final String sprite; final boolean on, locked;
        PositionAPI pos;

        Tile(String sprite, boolean on, boolean locked) { this.sprite = sprite; this.on = on; this.locked = locked; }

        @Override public void positionChanged(PositionAPI position) { pos = position; }

        @Override public void renderBelow(float alpha) {
            if (pos == null) return;
            float x = pos.getX(), y = pos.getY(), w = pos.getWidth(), h = pos.getHeight();
            Misc.renderQuadAlpha(x, y, w, h, on ? Misc.getDarkPlayerColor() : new Color(18, 18, 18), alpha * (on ? 0.6f : 0.85f));
            Color edge = on ? Misc.getBasePlayerColor() : locked ? new Color(55, 55, 55) : Misc.getGrayColor();
            float e = on ? 2f : 1f;
            Misc.renderQuadAlpha(x, y, w, e, edge, alpha);
            Misc.renderQuadAlpha(x, y + h - e, w, e, edge, alpha);
            Misc.renderQuadAlpha(x, y, e, h, edge, alpha);
            Misc.renderQuadAlpha(x + w - e, y, e, h, edge, alpha);
        }

        @Override public void render(float alpha) {
            if (pos == null || sprite == null) return;
            try {
                SpriteAPI s = Global.getSettings().getSprite(sprite);
                float sw = s.getWidth(), sh = s.getHeight();
                if (sw <= 0f || sh <= 0f) return;
                float k = Math.min(IMG_W / sw, IMG_H / sh), dw = sw * k, dh = sh * k;
                float bx = pos.getX() + TILE_PAD, by = pos.getY() + pos.getHeight() - TILE_PAD - IMG_H;
                s.setSize(dw, dh);
                s.setColor(on ? Color.WHITE : locked ? new Color(70, 70, 70) : new Color(125, 125, 125));
                s.setAlphaMult(alpha * (on ? 1f : 0.75f));
                s.render(bx + (IMG_W - dw) / 2f, by + (IMG_H - dh) / 2f);
            } catch (Throwable ignored) { }
        }
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
