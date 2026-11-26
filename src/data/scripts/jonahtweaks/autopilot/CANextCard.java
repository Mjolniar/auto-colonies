package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 0.3.15.3: the next-build card on the game's colony screen. It sits in the building grid's first empty slot, sized like a
 * tile, with the Autopilot's top pick (CAEngine.kept, as the colony panel's Build button reads it). On Advisor and Approve it
 * reads "Suggested", on Auto "Next to build". Hidden when the colony is Off, an outpost, or nothing is worth building.
 * CAScreenButton updates it with its button and removes it with it.
 * 0.3.17.2: a Build button in every mode, with the whole price (CABudget.buildTotal), starts it now (CAApprovals.startByPlayer:
 * queued when a build is running). Medium gray when the click can start it; dark, with the reason and the button off, when
 * CABuild.blocked says it can't. Once started, the game's own tile shows it.
 * 0.3.20.4: the picture draws in render() with the sprite's own color, as the Story page tiles do; no GL calls (Fast
 * Rendering drew the old way as a gray box). With the grid full (12 buildings) the card is a strip right of the Add industry
 * button and the Colony Autopilot button, to the grid's right edge.
 */
final class CANextCard {
    static final String BUILD = "ca:nextcard:build";
    CustomPanelAPI card;
    UIPanelAPI parent;
    String shownKey;
    static boolean noStripLogged;

    /** The colony's top pick worth building, or null. */
    static CAEngine.Pick pick(MarketAPI m) {
        if (m == null) return null;
        CAData d = CAData.get();
        CAData.Colony c = d.colony(m.getId());
        if (c.mode == CAData.Mode.OFF || CAEngine.isOutpost(m) || CAHypershunt.is(m)) return null;
        CAEngine.drawing(true);   // read the kept result; nothing is evaluated here
        try {
            CAEngine.Kept k = CAEngine.kept(m, CABuildHints.goal(m, c), d.settings.minStability, CAColonyDialog.buildoutRanking(c));
            if (k == null || k.result.picks.isEmpty() || !CAEngine.worthwhile(k.result.picks.get(0))) return null;
            return k.result.picks.get(0);
        } catch (Throwable t) { return null; }
        finally { CAEngine.drawing(false); }
    }

    /** The first empty slot of the building grid, {x, y, w, h} in screen coordinates (y up), or null. */
    static float[] slot(CAScreenButton.Screen s, ButtonAPI add) {
        List<float[]> r = new ArrayList<float[]>();
        for (UIComponentAPI t : s.tiles) if (t != s.grid) r.add(CAScreenButton.rect(t));
        if (r.isEmpty()) return null;
        float w = r.get(0)[2], h = r.get(0)[3];
        float topY = Float.NEGATIVE_INFINITY, lowY = Float.POSITIVE_INFINITY;
        for (float[] b : r) { topY = Math.max(topY, b[1]); lowY = Math.min(lowY, b[1]); }
        List<float[]> top = new ArrayList<float[]>(), low = new ArrayList<float[]>();
        for (float[] b : r) { if (Math.abs(b[1] - topY) < 2f) top.add(b); if (Math.abs(b[1] - lowY) < 2f) low.add(b); }
        float minX = Float.POSITIVE_INFINITY, topMaxX = Float.NEGATIVE_INFINITY, lowMaxX = Float.NEGATIVE_INFINITY, secondX = Float.POSITIVE_INFINITY;
        for (float[] b : r) minX = Math.min(minX, b[0]);
        for (float[] b : top) topMaxX = Math.max(topMaxX, b[0]);
        for (float[] b : low) lowMaxX = Math.max(lowMaxX, b[0]);
        for (float[] b : top) if (b[0] > minX + 1f) secondX = Math.min(secondX, b[0]);
        float gapX = secondX < Float.POSITIVE_INFINITY ? secondX - minX - w : 12f;
        int cols = Math.max(4, top.size());
        float gapY = topY > lowY + 1f ? Math.max(0f, (topY - lowY) / Math.max(1, Math.round((topY - lowY) / (h + gapX))) - h) : gapX + 18f;
        float[] at = low.size() < cols ? new float[]{lowMaxX + w + gapX, lowY, w, h} : new float[]{minX, lowY - h - gapY, w, h};
        if (add != null) {   // never over or below the Add industry button
            float[] a = CAScreenButton.rect(add);
            if (at[1] < a[1] + a[3] + 4f) return null;
        }
        return at;
    }

    /** 0.3.20.4: the strip with the grid full: its size limits and the gap after the buttons. */
    static final float STRIP_MAX_W = 460f, STRIP_MIN_W = 300f, STRIP_MIN_H = 60f, STRIP_GAP = 8f;

    /**
     * Pure (0.3.20.4): the strip right of the Add industry button (a) and the Colony Autopilot button under it (b, may be
     * null), from b's bottom to a's top, up to the grid's (g) right edge. {x, y, w, h}, or null when it's too small.
     */
    static float[] strip(float[] a, float[] b, float[] g) {
        if (a == null || g == null || a[2] <= 0f || a[3] <= 0f) return null;
        float x = a[0] + Math.max(a[2], b == null ? 0f : b[2]) + STRIP_GAP;
        float y = b == null ? a[1] : Math.min(b[1], a[1]), h = a[1] + a[3] - y;
        float w = Math.min(STRIP_MAX_W, g[0] + g[2] - x);
        if (w < STRIP_MIN_W || h < STRIP_MIN_H) return null;
        return new float[]{x, y, w, h};
    }

    /** The strip on this screen, or null when it's off the panel or something the game draws is in it. */
    float[] strip(CAScreenButton.Screen s, ButtonAPI add) {
        if (add == null) return null;
        float[] r = strip(CAScreenButton.rect(add), CAScreenButton.place(s, add), CAScreenButton.gridBox(s));
        if (r == null) return null;
        float[] tab = CAScreenButton.rect(s.tab);
        if (!CAScreenButton.inside(r, tab)) return null;
        float big = 0.15f * tab[2] * tab[3];   // big backgrounds don't count, as in CAScreenButton.spot
        for (UIComponentAPI c : s.leaves) {
            float[] o = CAScreenButton.rect(c);
            if (c == add || o[2] < 2f || o[3] < 2f || o[2] * o[3] > big || !CAScreenButton.overlaps(r, o, 0f) || mineOrGrid(s, c)) continue;
            if (!noStripLogged) {
                noStripLogged = true;
                CADiagnostics.LOG.info(CADiagnostics.TAG + "next-build card: no room beside Add industry or structure at " + CAScreenButton.box(r)
                        + ", " + c.getClass().getName() + " at " + CAScreenButton.box(o));
            }
            return null;
        }
        return r;
    }

    /** True for this card's own parts and the grid's (tiles scrolled out of view still have their places). */
    boolean mineOrGrid(CAScreenButton.Screen s, UIComponentAPI c) {
        UIComponentAPI u = c;
        for (int i = 0; u != null && i < 40; i++, u = s.up.get(u)) if (u == card || u == s.grid || s.tiles.contains(u)) return true;
        return false;
    }

    /** 0.3.17.2: the card's two looks: medium gray when your click can start it, dark when it can't. */
    static final Color DARK_TEXT = new Color(90, 90, 90), DARK_BUTTON = new Color(25, 25, 25);
    /** 0.3.20.4: the picture's color and alpha (it was a wash quad over it). */
    static final Color MID_TINT = new Color(150, 150, 150), DARK_TINT = new Color(60, 60, 60);
    static final float MID_SPRITE = 0.8f, DARK_SPRITE = 0.5f;
    /** 0.3.18.1: the card's text on top (two lines, three with the reason) and its button at the bottom. */
    static final float TEXT_H = 40f, TEXT_H_DARK = 56f, BUTTON_H = 22f;

    /**
     * Pure (0.3.18.1): where the building's picture goes on a card at (x, y) of pw x ph: between the text and the button, as
     * large as fits at the picture's own shape (iw x ih), centered. {x, y, w, h}, or null when there's no room.
     */
    static float[] picture(float x, float y, float pw, float ph, float iw, float ih, float textH) {
        return fit(x + 4f, y + 4f + BUTTON_H + 4f, pw - 8f, ph - textH - (BUTTON_H + 12f), iw, ih);
    }

    /** Pure (0.3.20.4): the strip's picture box at its left end, 2:1 like the game's building pictures. */
    static float stripPictureW(float ph) { return 2f * (ph - 8f); }

    /** Pure (0.3.20.4): the picture on a strip at (x, y) of height ph, in its box, at its own shape. */
    static float[] stripPicture(float x, float y, float ph, float iw, float ih) {
        return fit(x + 4f, y + 4f, stripPictureW(ph), ph - 8f, iw, ih);
    }

    /** Pure: a picture of iw x ih as large as fits in the box, centered. Null when the box is under 8 px. */
    static float[] fit(float bx, float by, float bw, float bh, float iw, float ih) {
        if (bw < 8f || bh < 8f) return null;
        float aspect = iw > 0f && ih > 0f ? iw / ih : 2f;
        float w = Math.min(bw, bh * aspect), h = w / aspect;
        return new float[]{bx + (bw - w) / 2f, by + (bh - h) / 2f, w, h};
    }

    void update(CAScreenButton.Screen s, ButtonAPI add) {
        final MarketAPI m = s.market;
        final CAEngine.Pick p = pick(m);
        float[] at = p == null ? null : slot(s, add);
        boolean wide = false;
        if (p != null && at == null) { at = strip(s, add); wide = at != null; }   // 0.3.20.4: the grid is full
        if (p == null || at == null) { remove(); return; }
        final CAData.Colony c = CAData.get().colony(m.getId());
        final String why = CABuild.blocked(m, p);   // 0.3.17.2: null when your click can start or queue it
        final boolean can = why == null, strip = wide;
        float total = CABudget.buildTotal(CAData.settings(), p.cost);   // 0.3.17.2: what the click takes: price, Rapid construction's fee, construction fee
        String key = m.getId() + "|" + p.id + "|" + c.mode + "|" + Math.round(at[0]) + "," + Math.round(at[1]) + "," + Math.round(at[2]) + "|" + Math.round(total) + "|" + why;
        if (card != null && key.equals(shownKey)) return;
        remove();
        UIPanelAPI in = CAScreenButton.container(s, at, add);
        PositionAPI cp = in.getPosition();
        final String sprite = CASyncPage.sprite("i:" + p.id);
        final boolean auto = c.mode == CAData.Mode.AUTO;
        float w = at[2], h = at[3];
        CustomPanelAPI panel = Global.getSettings().createCustom(w, h, new BaseCustomUIPanelPlugin() {
            PositionAPI pos;
            @Override public void positionChanged(PositionAPI position) { pos = position; }
            @Override public void renderBelow(float alpha) {
                if (pos == null) return;
                Misc.renderQuadAlpha(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), can ? Misc.getDarkPlayerColor() : Color.BLACK, alpha * (can ? 0.55f : 0.85f));
            }
            // 0.3.20.4: the picture in render(), with the sprite's color for the look, as CASyncPage.Tile draws its pictures
            @Override public void render(float alpha) {
                if (pos == null || sprite == null) return;
                try {
                    SpriteAPI sp = Global.getSettings().getSprite(sprite);
                    if (sp.getTextureId() == 0) return;   // 0.3.19.3: not loaded, no picture
                    float x = pos.getX(), y = pos.getY(), pw = pos.getWidth(), ph = pos.getHeight();
                    // the game's sprites may be shared: size, color and alpha go back as they were after drawing
                    float ow = sp.getWidth(), oh = sp.getHeight(), oa = sp.getAlphaMult();
                    Color oc = sp.getColor();
                    float[] r = strip ? stripPicture(x, y, ph, ow, oh) : picture(x, y, pw, ph, ow, oh, can ? TEXT_H : TEXT_H_DARK);
                    if (r == null) return;
                    sp.setSize(r[2], r[3]);
                    sp.setColor(can ? MID_TINT : DARK_TINT);
                    sp.setAlphaMult(alpha * (can ? MID_SPRITE : DARK_SPRITE));
                    sp.render(r[0], r[1]);
                    sp.setSize(ow, oh);
                    sp.setColor(oc);
                    sp.setAlphaMult(oa);
                } catch (Throwable ignored) { }
            }
            @Override public void buttonPressed(Object id) {
                if (!BUILD.equals(id)) return;
                String err = CAApprovals.startByPlayer(m, p);
                CALog.did(m.getName() + ": " + (err == null ? p.verb + " " + p.name + " started." : err), true);
                shownKey = null;
            }
        });
        String label = auto ? "Next to build" : "Suggested";
        float left = strip ? 4f + stripPictureW(h) + 8f : 4f, textW = w - left - 4f;
        TooltipMakerAPI t = panel.createUIElement(textW, strip ? h - BUTTON_H - 8f : can ? TEXT_H : TEXT_H_DARK, false);
        if (strip) t.addPara(label + ": " + p.name, can ? Misc.getGrayColor().brighter() : DARK_TEXT, 2f);
        else {
            t.addPara(label, can ? Misc.getGrayColor() : DARK_TEXT, 2f);
            t.addPara(p.name, can ? Misc.getGrayColor().brighter() : DARK_TEXT, 0f);
        }
        if (!can) t.addPara(why, DARK_TEXT, 0f);
        panel.addUIElement(t).inTL(left, 0f);
        // 0.3.17.2: the Build button in every mode, with the whole price; dark and off when the click can't start it.
        // 0.3.18.1: in an element of its own at the card's bottom (inside the text element it sat under the name)
        float bw = strip ? Math.min(textW, 240f) : w - 8f;
        TooltipMakerAPI bt = panel.createUIElement(bw, BUTTON_H, false);
        ButtonAPI b = bt.addButton(p.verb + " " + Misc.getDGSCredits(total), BUILD, can ? Misc.getBasePlayerColor() : DARK_TEXT,
                can ? Misc.getDarkPlayerColor() : DARK_BUTTON, Alignment.MID, CutStyle.TL_BR, bw, BUTTON_H, 0f);
        b.setEnabled(can);
        panel.addUIElement(bt).inBL(left, 4f);
        in.addComponent(panel).inBL(at[0] - cp.getX(), at[1] - cp.getY());
        card = panel;
        parent = in;
        shownKey = key;
    }

    void remove() {
        try { if (card != null && parent != null) parent.removeComponent(card); } catch (Throwable ignored) { }
        card = null;
        parent = null;
        shownKey = null;
    }
}
