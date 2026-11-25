package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 0.3.15.3: the next-build card on the game's colony screen. It sits in the building grid's first empty slot, sized like a
 * tile, with the Autopilot's top pick (CAEngine.kept, as the colony panel's Build button reads it). On Advisor and Approve it
 * reads "Suggested", on Auto "Next to build". Hidden when the colony is Off, an outpost, the grid has no free slot above
 * the Add industry button, or nothing is worth building. CAScreenButton updates it with its button and removes it with it.
 * 0.3.17.2: a Build button in every mode, with the whole price (CABudget.buildTotal), starts it now (CAApprovals.startByPlayer:
 * queued when a build is running). Medium gray when the click can start it; dark, with the reason and the button off, when
 * CABuild.blocked says it can't. Once started, the game's own tile shows it.
 */
final class CANextCard {
    static final String BUILD = "ca:nextcard:build";
    CustomPanelAPI card;
    UIPanelAPI parent;
    String shownKey;

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

    /** 0.3.17.2: the card's two looks: medium gray when your click can start it, dark when it can't. */
    static final Color MID_WASH = new Color(110, 110, 110), DARK_TEXT = new Color(90, 90, 90), DARK_BUTTON = new Color(25, 25, 25);
    static final float MID_SPRITE = 0.8f, MID_WASH_A = 0.45f, DARK_SPRITE = 0.4f, DARK_WASH_A = 0.75f;
    /** 0.3.18.1: the card's text on top (two lines, three with the reason) and its button at the bottom. */
    static final float TEXT_H = 40f, TEXT_H_DARK = 56f, BUTTON_H = 22f;

    /**
     * Pure (0.3.18.1): where the building's picture goes on a card at (x, y) of pw x ph: between the text and the button, as
     * large as fits at the picture's own shape (iw x ih), centered. {x, y, w, h}, or null when there's no room.
     */
    static float[] picture(float x, float y, float pw, float ph, float iw, float ih, float textH) {
        float bx = x + 4f, by = y + 4f + BUTTON_H + 4f, bw = pw - 8f, bh = ph - textH - (BUTTON_H + 12f);
        if (bw < 8f || bh < 8f) return null;
        float aspect = iw > 0f && ih > 0f ? iw / ih : 2f;
        float w = Math.min(bw, bh * aspect), h = w / aspect;
        return new float[]{bx + (bw - w) / 2f, by + (bh - h) / 2f, w, h};
    }

    void update(CAScreenButton.Screen s, ButtonAPI add) {
        final MarketAPI m = s.market;
        final CAEngine.Pick p = pick(m);
        float[] at = p == null ? null : slot(s, add);
        if (p == null || at == null) { remove(); return; }
        final CAData.Colony c = CAData.get().colony(m.getId());
        final String why = CABuild.blocked(m, p);   // 0.3.17.2: null when your click can start or queue it
        final boolean can = why == null;
        float total = CABudget.buildTotal(CAData.settings(), p.cost);   // 0.3.17.2: what the click takes: price, Rapid construction's fee, construction fee
        String key = m.getId() + "|" + p.id + "|" + c.mode + "|" + Math.round(at[0]) + "," + Math.round(at[1]) + "|" + Math.round(total) + "|" + why;
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
                float x = pos.getX(), y = pos.getY(), pw = pos.getWidth(), ph = pos.getHeight();
                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);   // 0.3.18.1: the quads turn texturing off; the game's state comes back after
                try {
                    Misc.renderQuadAlpha(x, y, pw, ph, can ? Misc.getDarkPlayerColor() : Color.BLACK, alpha * (can ? 0.55f : 0.85f));
                    if (sprite != null) {
                        // the game's sprites are shared: size, alpha and color go back as they were after drawing
                        com.fs.starfarer.api.graphics.SpriteAPI sp = Global.getSettings().getSprite(sprite);
                        float ow = sp.getWidth(), oh = sp.getHeight(), oa = sp.getAlphaMult();
                        // 0.3.19.3: no picture and no wash when the texture isn't loaded (it was a gray box)
                        float[] r = sp.getTextureId() == 0 ? null : picture(x, y, pw, ph, ow, oh, can ? TEXT_H : TEXT_H_DARK);
                        if (r != null) {
                            GL11.glEnable(GL11.GL_TEXTURE_2D);   // 0.3.18.1: Misc.renderQuadAlpha left it off, so the picture drew as a flat box
                            sp.setSize(r[2], r[3]);
                            sp.setAlphaMult(alpha * (can ? MID_SPRITE : DARK_SPRITE));
                            sp.render(r[0], r[1]);
                            sp.setSize(ow, oh);
                            sp.setAlphaMult(oa);
                            // the wash over the picture: medium gray when it can start, near black when it can't
                            Misc.renderQuadAlpha(r[0], r[1], r[2], r[3], can ? MID_WASH : Color.BLACK, alpha * (can ? MID_WASH_A : DARK_WASH_A));
                        }
                    }
                } catch (Throwable ignored) { }
                finally { GL11.glPopAttrib(); }
            }
            @Override public void buttonPressed(Object id) {
                if (!BUILD.equals(id)) return;
                String err = CAApprovals.startByPlayer(m, p);
                CALog.did(m.getName() + ": " + (err == null ? p.verb + " " + p.name + " started." : err), true);
                shownKey = null;
            }
        });
        TooltipMakerAPI t = panel.createUIElement(w - 8f, can ? TEXT_H : TEXT_H_DARK, false);
        t.addPara(auto ? "Next to build" : "Suggested", can ? Misc.getGrayColor() : DARK_TEXT, 2f);
        t.addPara(p.name, can ? Misc.getGrayColor().brighter() : DARK_TEXT, 0f);
        if (!can) t.addPara(why, DARK_TEXT, 0f);
        panel.addUIElement(t).inTL(4f, 0f);
        // 0.3.17.2: the Build button in every mode, with the whole price; dark and off when the click can't start it.
        // 0.3.18.1: in an element of its own at the card's bottom (inside the text element it sat under the name)
        TooltipMakerAPI bt = panel.createUIElement(w - 8f, BUTTON_H, false);
        ButtonAPI b = bt.addButton(p.verb + " " + Misc.getDGSCredits(total), BUILD, can ? Misc.getBasePlayerColor() : DARK_TEXT,
                can ? Misc.getDarkPlayerColor() : DARK_BUTTON, Alignment.MID, CutStyle.TL_BR, w - 8f, BUTTON_H, 0f);
        b.setEnabled(can);
        panel.addUIElement(bt).inBL(4f, 4f);
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
