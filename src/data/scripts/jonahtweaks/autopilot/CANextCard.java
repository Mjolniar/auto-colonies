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

import java.util.ArrayList;
import java.util.List;

/**
 * 0.3.15.3: the next-build card on the game's colony screen. It sits in the building grid's first empty slot, sized like a
 * tile, with the Autopilot's top pick (CAEngine.kept, as the colony panel's Build button reads it). On Advisor and Approve it
 * reads "Suggested" with a Build button (CAApprovals.startByPlayer: price plus fee, queued when a build is running); on
 * Auto it reads "Next to build" with no button. Hidden when the colony is Off, an outpost, the grid has no free slot above
 * the Add industry button, or nothing is worth building. CAScreenButton updates it with its button and removes it with it.
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

    void update(CAScreenButton.Screen s, ButtonAPI add) {
        final MarketAPI m = s.market;
        final CAEngine.Pick p = pick(m);
        float[] at = p == null ? null : slot(s, add);
        if (p == null || at == null) { remove(); return; }
        final CAData.Colony c = CAData.get().colony(m.getId());
        String key = m.getId() + "|" + p.id + "|" + c.mode + "|" + Math.round(at[0]) + "," + Math.round(at[1]);
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
                Misc.renderQuadAlpha(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), Misc.getDarkPlayerColor(), alpha * 0.55f);
                if (sprite != null) try {
                    com.fs.starfarer.api.graphics.SpriteAPI sp = Global.getSettings().getSprite(sprite);
                    sp.setSize(pos.getWidth() - 8f, pos.getHeight() - 30f);
                    sp.setAlphaMult(alpha * 0.45f);
                    sp.render(pos.getX() + 4f, pos.getY() + 4f);
                } catch (Throwable ignored) { }
            }
            @Override public void buttonPressed(Object id) {
                if (!BUILD.equals(id)) return;
                String err = CAApprovals.startByPlayer(m, p);
                CALog.did(m.getName() + ": " + (err == null ? p.verb + " " + p.name + " started." : err), true);
                shownKey = null;
            }
        });
        TooltipMakerAPI t = panel.createUIElement(w, h, false);
        t.addPara(auto ? "Next to build" : "Suggested", Misc.getGrayColor(), 2f);
        t.addPara(p.name, Misc.getHighlightColor(), 0f);
        if (!auto) {
            ButtonAPI b = t.addButton(p.verb + " (" + Misc.getDGSCredits(p.cost) + ")", BUILD, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.TL_BR, w - 8f, 22f, 0f);
            b.getPosition().inBL(4f, 4f);
        }
        panel.addUIElement(t).inTL(4f, 0f);
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
