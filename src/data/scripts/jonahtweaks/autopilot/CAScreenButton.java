package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/**
 * A "Colony Autopilot" button on your colony's own screen (from the Command tab, or docked at the colony). Starsector
 * has no API for adding buttons there, so the screen is read the way Building Menu Overhaul's tracker reads it: the
 * campaign UI's public getCore() (getCoreUI() on an open interaction dialog), the core's getCurrentTab(), and each
 * panel's getChildrenCopy(), all through CARefl.
 *
 * The building grid is the game's IndustryListPanel (its name isn't obfuscated); getWidgets() gives the building tiles,
 * and the colony is read from the panel's fields by type. The button opens the colony panel in the same dialog the
 * right-click menu uses: in 0.98a, ui.newui.super(width, height, delegate, core.getDialogParent(), listener).show(0.3,
 * 0.2), reached by name through method handles, with the grid as the listener (it ignores dismissals of dialogs it
 * didn't open). If that can't be built the log says so and the right-click option still works.
 *
 * Nothing about the layout is assumed. Every visible element's rectangle is read, and the button goes in the first
 * candidate spot that's on the screen and overlaps none of them: to the right of the button under the building grid
 * ("Add industry or structure"), under that button, under the grid's right end, above it, under its left end, right of
 * it. With no free spot there's no button (logged once), and any error turns it off for the session. Setting "Button on
 * the colony screen". While it isn't showing, what it found is logged a few times a session (report()).
 */
public class CAScreenButton implements EveryFrameScript {
    static final float W = 190f, H = 26f, GAP = 10f;
    static final String OPEN = "ca:screen:open";
    static final String GRID_CLASS = "com.fs.starfarer.campaign.ui.marketinfo.IndustryListPanel";
    static final String DIALOG_CLASS = "com.fs.starfarer.ui.newui.super", PARENT_CLASS = "com.fs.starfarer.ui.interfacenew",
            LISTENER_CLASS = "com.fs.starfarer.ui.oo0O$o";
    static final Object NONE = new Object();
    static boolean noSpotLogged = false, placedLogged = false;

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

    /** What's on screen now: the core UI, the panel searched, every component in it, the building grid and tiles, the colony. */
    static class Screen {
        Object core, grid;
        UIPanelAPI tab;
        List<UIComponentAPI> all = new ArrayList<UIComponentAPI>(), leaves = new ArrayList<UIComponentAPI>(), tiles = new ArrayList<UIComponentAPI>();
        MarketAPI market;
        String note;
    }

    Screen read() throws Exception {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        InteractionDialogAPI dialog = ui.getCurrentInteractionDialog();
        if (ui.getCurrentCoreTab() == null && dialog == null) return null;   // plain campaign view: nothing to look at
        // the colony screen lives in the core UI: the campaign's own, or (docked) the open dialog's
        List<Object> cores = new ArrayList<Object>();
        if (dialog != null) cores.add(tryCall(dialog, "getCoreUI"));
        cores.add(tryCall(ui, "getCore"));
        Screen best = null;
        for (Object core : cores) {
            if (!(core instanceof UIPanelAPI)) continue;
            Object tab = tryCall(core, "getCurrentTab");
            Screen s = new Screen();
            s.core = core;
            s.tab = tab instanceof UIPanelAPI ? (UIPanelAPI) tab : (UIPanelAPI) core;
            if (!(tab instanceof UIPanelAPI)) s.note = "no current tab, searched the whole core UI";
            collect(s.tab, s, 0);
            for (UIComponentAPI c : s.all) if (c.getClass().getName().equals(GRID_CLASS)) { s.grid = c; break; }
            if (s.grid != null) {
                Object widgets = tryCall(s.grid, "getWidgets");
                if (widgets instanceof List) for (Object w : (List<?>) widgets) if (w instanceof UIComponentAPI) s.tiles.add((UIComponentAPI) w);
                if (s.tiles.isEmpty()) s.tiles.add((UIComponentAPI) s.grid);   // an empty grid still marks the spot
                s.market = marketOf(s.grid);
                for (int i = 0; s.market == null && i < s.tiles.size(); i++) s.market = marketOf(s.tiles.get(i));
            }
            if (s.market == null && s.grid != null && dialog != null && dialog.getInteractionTarget() != null)
                s.market = dialog.getInteractionTarget().getMarket();   // docked at the colony
            if (best == null || (s.grid != null && best.grid == null)) best = s;
            if (s.grid != null) break;
        }
        report(ui, dialog, best);
        return best == null || best.grid == null || best.market == null || !best.market.isPlayerOwned() ? null : best;
    }

    static Object tryCall(Object target, String name) {
        try { return CARefl.call(target, name); } catch (Throwable t) { return null; }
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

    /** The colony an object belongs to: a MarketAPI field, or an Industry field's market. Read by type, not by name. */
    static MarketAPI marketOf(Object o) {
        MarketAPI viaIndustry = null;
        for (Object v : CARefl.fieldValues(o)) {
            if (v instanceof MarketAPI) return (MarketAPI) v;
            if (viaIndustry == null && v instanceof Industry && ((Industry) v).getMarket() != null) viaIndustry = ((Industry) v).getMarket();
        }
        return viaIndustry;
    }

    /** Diagnostics while the button isn't showing, logged when the screen changes, a few times a session. */
    static String lastReport;
    static int reports = 0;

    void report(CampaignUIAPI ui, InteractionDialogAPI dialog, Screen s) {
        if (reports >= 8 || (s != null && button != null && parent == s.tab)) return;
        StringBuilder b = new StringBuilder("colony screen button: core tab ").append(ui.getCurrentCoreTab())
                .append(dialog != null ? ", dialog open" : ", no dialog");
        if (s == null) b.append(", no core UI found");
        else b.append(", searched ").append(s.tab.getClass().getName()).append(s.note != null ? " (" + s.note + ")" : "")
                .append(": ").append(s.all.size()).append(" elements, building grid ").append(s.grid != null ? "found with " + s.tiles.size() + " tiles" : "not found")
                .append(", colony ").append(s.market == null ? "not found" : s.market.getName() + (s.market.isPlayerOwned() ? "" : " (not yours)"));
        String r = b.toString();
        if (r.equals(lastReport)) return;
        lastReport = r;
        reports++;
        CADiagnostics.LOG.info(CADiagnostics.TAG + r);
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
        if (!placedLogged) {
            placedLogged = true;
            CADiagnostics.LOG.info(CADiagnostics.TAG + "colony screen button placed at " + Math.round(at[0]) + ", " + Math.round(at[1])
                    + " on " + s.market.getName() + " (screen " + Math.round(tp.getWidth()) + "x" + Math.round(tp.getHeight()) + ")");
        }
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

    /** Opens the colony panel in the game's own custom dialog, as the right-click menu does. */
    void open() {
        try {
            Screen s = read();
            if (s == null) return;
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> dialog = Class.forName(DIALOG_CLASS, true, cl), parentType = Class.forName(PARENT_CLASS, false, cl),
                    listenerType = Class.forName(LISTENER_CLASS, false, cl);
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            MethodHandle make = l.findConstructor(dialog, MethodType.methodType(void.class, float.class, float.class,
                    CustomDialogDelegate.class, parentType, listenerType));
            MethodHandle show = l.findVirtual(dialog, "show", MethodType.methodType(void.class, float.class, float.class));
            Object dialogParent = CARefl.call(s.core, "getDialogParent");
            Object d = make.invokeWithArguments(CAColonyDialog.W, CAColonyDialog.H, new CAColonyDialog(s.market), dialogParent, s.grid);
            show.invokeWithArguments(d, 0.3f, 0.2f);
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
        float[] below = null;   // the nearest button under the grid ("Add industry or structure")
        for (UIComponentAPI c : s.leaves) {
            if (!(c instanceof ButtonAPI)) continue;
            float[] r = rect(c);
            boolean under = r[1] + r[3] <= minY + 2f && r[1] + r[3] >= minY - 300f && r[0] < maxX && r[0] + r[2] > minX;
            if (under && (below == null || r[1] + r[3] > below[1] + below[3])) below = r;
        }
        if (below != null) {
            candidates.add(new float[]{below[0] + below[2] + GAP, below[1] + (below[3] - H) / 2f});   // right of it
            candidates.add(new float[]{below[0], below[1] - GAP - H});                                 // under it
        }
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
