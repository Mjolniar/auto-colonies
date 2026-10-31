package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * A "Colony Autopilot" button on your colony's own screen (from the Command tab, or docked at the colony). Starsector
 * has no API for adding buttons there, so the screen is read the way Building Menu Overhaul's tracker reads it: the
 * campaign UI's public getCore() (getCoreUI() on an open interaction dialog), the core's getCurrentTab(), and each
 * panel's getChildrenCopy(), all through CARefl. The building tiles on that screen implement the game's DialogCreatorUI,
 * the same object the right-click "Colony Autopilot..." option is handed, so the button opens the colony panel the same
 * way; the colony is read from the tile's Industry field, found by type rather than by its obfuscated name.
 *
 * Nothing about the layout is assumed. Every visible element's rectangle is read, and the button goes in the first
 * candidate spot that's on the screen and overlaps none of them: to the right of the buttons under the building grid,
 * under the grid's right end, above it, under its left end. With no free spot there's no button (logged once), and any
 * error turns it off for the session; the right-click option always works. Setting "Button on the colony screen".
 */
public class CAScreenButton implements EveryFrameScript {
    static final float W = 190f, H = 26f, GAP = 10f;
    static final String OPEN = "ca:screen:open";
    static final Object NONE = new Object();
    static boolean noSpotLogged = false;

    private final Map<Class<?>, Object> childrenMethod = new HashMap<Class<?>, Object>();
    private boolean broken = false;
    private float timer = 0f;
    private CustomPanelAPI button;
    private UIPanelAPI parent;

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return true; }   // the colony screen pauses the game

    public void advance(float amount) {
        if (broken) return;
        timer -= amount;
        if (timer > 0f) return;
        timer = 0.25f;
        try { update(); }
        catch (Throwable t) {
            broken = true;
            remove();
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony screen button turned off for this session; the right-click option still works", t);
        }
    }

    /** What's on screen now: the current core tab, every component in it, the building tiles, and their colony. */
    static class Screen {
        UIPanelAPI tab;
        List<UIComponentAPI> all = new ArrayList<UIComponentAPI>(), leaves = new ArrayList<UIComponentAPI>(), tiles = new ArrayList<UIComponentAPI>();
        MarketAPI market;
    }

    Screen read() throws Exception {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.getCurrentCoreTab() == null) return null;
        InteractionDialogAPI dialog = ui.getCurrentInteractionDialog();
        Object core = dialog != null ? CARefl.call(dialog, "getCoreUI") : CARefl.call(ui, "getCore");
        if (core == null) return null;
        Object tab = CARefl.call(core, "getCurrentTab");
        if (!(tab instanceof UIPanelAPI)) return null;
        Screen s = new Screen();
        s.tab = (UIPanelAPI) tab;
        collect(s.tab, s, 0);
        for (UIComponentAPI c : s.all) {
            if (!(c instanceof DialogCreatorUI) || c == button) continue;
            s.tiles.add(c);
            if (s.market == null) s.market = marketOf(c);
        }
        return s.tiles.isEmpty() || s.market == null || !s.market.isPlayerOwned() ? null : s;
    }

    void collect(UIComponentAPI c, Screen s, int depth) throws Exception {
        s.all.add(c);
        Object m = childrenMethod.get(c.getClass());
        if (m == null) {
            try { m = CARefl.method(c.getClass(), "getChildrenCopy"); } catch (Throwable t) { m = NONE; }
            childrenMethod.put(c.getClass(), m);
        }
        Object kids = m == NONE || depth > 16 ? null : CARefl.invoke(m, c);
        boolean any = false;
        if (kids instanceof List) for (Object o : (List<?>) kids) if (o instanceof UIComponentAPI) { any = true; collect((UIComponentAPI) o, s, depth + 1); }
        if (!any) s.leaves.add(c);
    }

    static MarketAPI marketOf(Object tile) {
        for (Object v : CARefl.fieldValues(tile)) {
            if (v instanceof Industry && ((Industry) v).getMarket() != null) return ((Industry) v).getMarket();
            if (v instanceof MarketAPI) return (MarketAPI) v;
        }
        return null;
    }

    void update() throws Exception {
        Screen s = CAData.settings().noScreenButton ? null : read();
        if (s == null) { remove(); return; }
        if (button != null && parent == s.tab && s.all.contains(button)) return;   // still in place
        remove();
        float[] at = spot(s);
        if (at == null) {
            if (!noSpotLogged) { noSpotLogged = true; CADiagnostics.LOG.info(CADiagnostics.TAG + "no free spot for the colony screen button; the right-click option still works"); }
            return;
        }
        button = make();
        PositionAPI tp = s.tab.getPosition();
        s.tab.addComponent(button).inBL(at[0] - tp.getX(), at[1] - tp.getY());
        parent = s.tab;
    }

    void remove() {
        try { if (button != null && parent != null) parent.removeComponent(button); } catch (Throwable ignored) { }
        button = null;
        parent = null;
    }

    CustomPanelAPI make() {
        CustomPanelAPI p = Global.getSettings().createCustom(W, H, new BaseCustomUIPanelPlugin() {
            @Override public void buttonPressed(Object id) { if (OPEN.equals(id)) open(); }
        });
        TooltipMakerAPI t = p.createUIElement(W, H, false);
        ButtonAPI b = t.addButton("Colony Autopilot", OPEN, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, W, H, 0f);
        b.getPosition().inTL(0f, 0f);
        p.addUIElement(t).inTL(0f, 0f);
        return p;
    }

    /** Opens the colony panel through a building tile, exactly as the right-click option does. */
    void open() {
        try {
            Screen s = read();
            if (s == null) return;
            ((DialogCreatorUI) s.tiles.get(0)).showDialog(CAColonyDialog.W, CAColonyDialog.H, new CAColonyDialog(s.market));
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony screen button couldn't open the panel; use the right-click option", t);
        }
    }

    // ---- placement

    static float[] rect(UIComponentAPI c) {
        PositionAPI p = c.getPosition();
        return new float[]{p.getX(), p.getY(), p.getWidth(), p.getHeight()};
    }

    static boolean overlaps(float[] a, float[] b, float margin) {
        return a[0] < b[0] + b[2] + margin && b[0] < a[0] + a[2] + margin && a[1] < b[1] + b[3] + margin && b[1] < a[1] + a[3] + margin;
    }

    static boolean inside(float[] a, float[] outer) {
        return a[0] >= outer[0] && a[1] >= outer[1] && a[0] + a[2] <= outer[0] + outer[2] && a[1] + a[3] <= outer[1] + outer[3];
    }

    /** Bottom-left corner of the first free candidate spot, in screen coordinates, or null. */
    static float[] spot(Screen s) {
        float[] tab = rect(s.tab);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (UIComponentAPI t : s.tiles) {
            float[] r = rect(t);
            minX = Math.min(minX, r[0]); minY = Math.min(minY, r[1]); maxX = Math.max(maxX, r[0] + r[2]); maxY = Math.max(maxY, r[1] + r[3]);
        }
        // what's drawn: visible leaves (labels, buttons, images) and the tiles; big backgrounds and empty containers don't count
        List<float[]> taken = new ArrayList<float[]>();
        float big = 0.15f * tab[2] * tab[3];
        for (UIComponentAPI c : s.leaves) {
            float[] r = rect(c);
            if (r[2] < 2f || r[3] < 2f || r[2] * r[3] > big || c.getOpacity() <= 0f) continue;
            taken.add(r);
        }
        for (UIComponentAPI t : s.tiles) taken.add(rect(t));

        List<float[]> candidates = new ArrayList<float[]>();
        float rowRight = -1f, rowY = 0f;   // buttons in the row just under the grid
        for (UIComponentAPI c : s.leaves) {
            if (!(c instanceof ButtonAPI)) continue;
            float[] r = rect(c);
            boolean under = r[1] + r[3] <= minY + 2f && r[1] + r[3] >= minY - 90f && r[0] < maxX && r[0] + r[2] > minX;
            if (under && r[0] + r[2] > rowRight) { rowRight = r[0] + r[2]; rowY = r[1] + (r[3] - H) / 2f; }
        }
        if (rowRight > 0f) candidates.add(new float[]{rowRight + GAP, rowY});
        candidates.add(new float[]{maxX - W, minY - GAP - H});
        candidates.add(new float[]{maxX - W, maxY + GAP});
        candidates.add(new float[]{minX, minY - GAP - H});
        candidates.add(new float[]{maxX + GAP, maxY - H});
        for (float[] at : candidates) {
            float[] r = {at[0], at[1], W, H};
            if (!inside(r, tab)) continue;
            boolean free = true;
            for (float[] o : taken) if (overlaps(r, o, 4f)) { free = false; break; }
            if (free) return at;
        }
        return null;
    }
}
