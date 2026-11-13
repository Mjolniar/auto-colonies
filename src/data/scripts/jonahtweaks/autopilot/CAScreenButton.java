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
 * The building tiles are the elements with a public getIndustryPanel(), which returns the game's IndustryListPanel (the
 * grid; its name isn't obfuscated). The grid isn't always in the child tree above its tiles, so it's found that way. The
 * colony is read from the grid's fields by type. The button opens the colony panel in the same dialog the
 * right-click menu uses: in 0.98a, ui.newui.super(width, height, delegate, core.getDialogParent(), listener).show(0.3,
 * 0.2), reached by name through method handles, with the grid as the listener (it ignores dismissals of dialogs it
 * didn't open). If that can't be built the log says so and the right-click option still works.
 *
 * Nothing about the layout is assumed. Every visible element's rectangle is read (a panel with no opacity is hidden, so
 * nothing under it counts), and the button goes in the first candidate spot that's on the screen and overlaps none of
 * them: to the right of the button under the building grid ("Add industry or structure"), under that button, under the
 * grid's right end, above it, under its left end, right of it. It's added to the lowest panel above the buildings that
 * holds that spot, so it moves, scrolls and hides with the colony screen rather than staying on whatever screen comes
 * next, and the spot is worked out again four times a second: when buildings are added or you switch colonies, it moves.
 * Only the colonies tab (Command) and the docked market screen are searched: on refit, fleet and the other screens the
 * button can't appear and nothing is walked. The current tab is searched first; the colony's own screen can sit outside
 * it, so the whole core UI is searched next. Past the spots above, it tries every free place along the rows under and
 * above the buildings and the columns beside them. With no free spot there's no button (logged once), and any error turns it
 * off for the session. Setting "Button on the colony screen". While it isn't showing, what it found is logged a few times
 * a session (report()), and so are the first few placements (where, in which panel, next to what).
 */
public class CAScreenButton implements EveryFrameScript {
    static final float W = 190f, H = 26f, GAP = 10f, STEP = 20f;
    static final String OPEN = "ca:screen:open";
    static final String GRID_CLASS = "com.fs.starfarer.campaign.ui.marketinfo.IndustryListPanel";
    static final String DIALOG_CLASS = "com.fs.starfarer.ui.newui.super", PARENT_CLASS = "com.fs.starfarer.ui.interfacenew",
            LISTENER_CLASS = "com.fs.starfarer.ui.oo0O$o";
    /** {dialog, parent, listener} per game version, tried in order: 0.98a-RC8, then 0.98.5a-RC4 (0.3.0.2). */
    static final String[][] DIALOG_SETS = {
            {DIALOG_CLASS, PARENT_CLASS, LISTENER_CLASS},
            {"com.fs.starfarer.ui.newui.O0oo", "com.fs.starfarer.ui.z", "com.fs.starfarer.ui.oo0O$o"}};
    static final Object NONE = new Object();
    static boolean noSpotLogged = false;

    // 0.3.0.0 (research 07 M10): the dialog's three classes have obfuscated names a game hotfix can change. They're resolved
    // once a session before any button is placed; when they're gone there's no button (one log line, the right-click option
    // still works) instead of a button that does nothing. JTScreenButtonLinkCheck runs the same lookup against the game's jar.
    static MethodHandle dialogNew, dialogShow;
    /** Null before the first try, "" when linked, else why not. */
    static String linkState;

    /** Looks up the game's custom dialog constructor and show() through `cl`, without running them; null when found, else why not. */
    static String link(ClassLoader cl) {
        String why = null;
        for (String[] set : DIALOG_SETS) {
            try {
                Class<?> dialog = Class.forName(set[0], false, cl), parentType = Class.forName(set[1], false, cl),
                        listenerType = Class.forName(set[2], false, cl);
                MethodHandles.Lookup l = MethodHandles.publicLookup();
                dialogNew = l.findConstructor(dialog, MethodType.methodType(void.class, float.class, float.class, CustomDialogDelegate.class, parentType, listenerType));
                dialogShow = l.findVirtual(dialog, "show", MethodType.methodType(void.class, float.class, float.class));
                return null;
            } catch (Throwable t) {
                dialogNew = dialogShow = null;
                if (why == null) why = t.getClass().getSimpleName() + ": " + t.getMessage();
            }
        }
        return why;
    }

    /** Whether the dialog is there to open (looked up once a session). */
    static boolean linked() {
        if (linkState == null) {
            String why;
            try { why = link(Global.getSettings().getScriptClassLoader()); } catch (Throwable t) { why = String.valueOf(t); }
            linkState = why == null ? "" : why;
            if (why != null) CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony screen button off for this session: the game's dialog classes changed ("
                    + why + "); the right-click option still works");
        }
        return linkState.isEmpty();
    }

    /** For the Diagnostics report: "ok", "not checked yet", or why it's off. */
    static String linkText() { return linkState == null ? "not checked yet" : linkState.isEmpty() ? "ok" : "off: " + linkState; }

    /** 0.3.0.0 (07 section 3): the session's diagnostics counters and the dialog lookup, cleared on load (CAReset.onLoad). */
    public static void reset() {
        noSpotLogged = false; placements = 0; reports = 0; lastReport = null;
        linkState = null; dialogNew = dialogShow = null;
    }

    private final Map<String, Object> methods = new HashMap<String, Object>();
    private boolean broken = false;
    private float timer = 0f;
    private CustomPanelAPI button;
    private UIPanelAPI parent;
    private float relX, relY;   // where the button is in its panel, to tell when the spot has moved
    static int placements = 0;

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
        /** Each visible component's panel, to find the panel the buildings sit in. */
        Map<UIComponentAPI, UIComponentAPI> up = new IdentityHashMap<UIComponentAPI, UIComponentAPI>();
        boolean sawButton;   // the button is still attached somewhere in what was searched
        MarketAPI market;
        String note;
    }

    Screen read() throws Exception {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        InteractionDialogAPI dialog = ui.getCurrentInteractionDialog();
        com.fs.starfarer.api.campaign.CoreUITabId coreTab = ui.getCurrentCoreTab();
        // the colony screen only shows under Command's colonies (OUTPOSTS) or, docked, the market screen (CARGO); on any
        // other screen (refit, fleet, map, intel, character) nothing is searched, so the button can't land on them and
        // their UI isn't walked four times a second
        if (!colonyTab(coreTab)) return null;
        // the colony screen lives in the core UI: the campaign's own, or (docked) the open dialog's
        List<Object> cores = new ArrayList<Object>();
        if (dialog != null) cores.add(tryCall(dialog, "getCoreUI"));
        cores.add(tryCall(ui, "getCore"));
        Screen best = null;
        for (Object core : cores) {
            if (!(core instanceof UIPanelAPI)) continue;
            Object tab = tryCall(core, "getCurrentTab");
            Screen s = search(core, tab instanceof UIPanelAPI ? (UIPanelAPI) tab : (UIPanelAPI) core);
            if (!(tab instanceof UIPanelAPI)) s.note = "no current tab, searched the whole core UI";
            else if (s.grid == null && tab != core) {
                // a colony opened from the colonies list, or while docked, can sit outside the current tab
                Screen whole = search(core, (UIPanelAPI) core);
                whole.note = "not in the current tab " + tab.getClass().getName() + ", searched the whole core UI";
                if (whole.grid != null) s = whole;
            }
            if (s.grid != null) {
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

    /** The core tabs a colony's screen can show under: Command's colonies, or the docked market screen. */
    static boolean colonyTab(com.fs.starfarer.api.campaign.CoreUITabId tab) {
        return tab == com.fs.starfarer.api.campaign.CoreUITabId.OUTPOSTS || tab == com.fs.starfarer.api.campaign.CoreUITabId.CARGO;
    }

    /** The visible components under root, the building tiles among them and the grid they belong to. */
    Screen search(Object core, UIPanelAPI root) throws Exception {
        Screen s = new Screen();
        s.core = core;
        s.tab = root;
        collect(root, null, s, 0);
        // the building tiles are the elements with a public getIndustryPanel(), which returns the grid they belong to;
        // the grid itself isn't always in the child tree above them
        float sw = screen(true), sh = screen(false);
        for (UIComponentAPI c : s.all) {
            Object m = method(c.getClass(), "getIndustryPanel");
            if (m == NONE) continue;
            Object grid = null;
            try { grid = CARefl.invoke(m, c); } catch (Throwable ignored) { }
            if (grid == null) continue;
            float[] r = rect(c);
            if (sw > 0f && (r[0] + r[2] < 0f || r[1] + r[3] < 0f || r[0] > sw || r[1] > sh)) continue;   // off the screen: not the one showing
            s.tiles.add(c);
            if (s.grid == null) s.grid = grid;
        }
        if (s.grid == null) for (UIComponentAPI c : s.all) if (c.getClass().getName().equals(GRID_CLASS)) { s.grid = c; s.tiles.add(c); break; }   // an empty grid
        return s;
    }

    /** The screen's width or height in UI units, or 0 when unknown. */
    static float screen(boolean width) {
        try { return width ? Global.getSettings().getScreenWidth() : Global.getSettings().getScreenHeight(); } catch (Throwable t) { return 0f; }
    }

    static Object tryCall(Object target, String name) {
        try { return CARefl.call(target, name); } catch (Throwable t) { return null; }
    }

    /** A public no-argument method of a class, cached (NONE when it has none). */
    Object method(Class<?> cls, String name) {
        String key = cls.getName() + "#" + name;
        Object m = methods.get(key);
        if (m == null) {
            try { m = CARefl.method(cls, name); } catch (Throwable t) { m = NONE; }
            methods.put(key, m);
        }
        return m;
    }

    /** Every visible component under c, with its panel; a hidden one (no opacity) and everything under it is skipped, and so is the button itself. */
    void collect(UIComponentAPI c, UIComponentAPI from, Screen s, int depth) throws Exception {
        if (c == button && button != null) { s.sawButton = true; return; }
        if (depth > 0 && c.getOpacity() <= 0f) return;
        s.all.add(c);
        if (from != null) s.up.put(c, from);
        Object m = method(c.getClass(), "getChildrenCopy");
        Object kids = m == NONE || depth > 16 ? null : CARefl.invoke(m, c);
        boolean any = false;
        if (kids instanceof List) for (Object o : (List<?>) kids) if (o instanceof UIComponentAPI) { any = true; collect((UIComponentAPI) o, c, s, depth + 1); }
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
        if (reports >= 8 || (s != null && button != null && s.sawButton)) return;
        // only screens a colony could be on: the colonies tab, or a docked market's screens
        com.fs.starfarer.api.campaign.CoreUITabId tab = ui.getCurrentCoreTab();
        if (tab != com.fs.starfarer.api.campaign.CoreUITabId.OUTPOSTS && tab != com.fs.starfarer.api.campaign.CoreUITabId.CARGO && (s == null || s.grid == null)) return;
        StringBuilder b = new StringBuilder("colony screen button: core tab ").append(tab)
                .append(dialog != null ? ", dialog open" : ", no dialog");
        if (s == null) b.append(", no core UI found");
        else b.append(", searched ").append(s.tab.getClass().getName()).append(s.note != null ? " (" + s.note + ")" : "")
                .append(": building grid ").append(s.grid != null ? "found with " + s.tiles.size() + " tiles" : "not found")
                .append(", colony ").append(s.market == null ? "not found" : s.market.getName() + (s.market.isPlayerOwned() ? "" : " (not yours)"));
        String r = b.toString();
        if (r.equals(lastReport)) return;
        lastReport = r;
        reports++;
        CADiagnostics.LOG.info(CADiagnostics.TAG + r);
    }

    void update() throws Exception {
        if (!CAData.settings().noScreenButton && !linked()) { broken = true; remove(); return; }   // 0.3.0.0: no dialog to open, no button
        Screen s = CAData.settings().noScreenButton ? null : read();
        if (s == null) { remove(); return; }
        float[] at = spot(s);
        if (at == null) {
            remove();
            if (!noSpotLogged) { noSpotLogged = true; CADiagnostics.LOG.info(CADiagnostics.TAG + "no free spot for the colony screen button; the right-click option still works"); }
            return;
        }
        UIPanelAPI in = container(s, at);
        PositionAPI cp = in.getPosition();
        float rx = at[0] - cp.getX(), ry = at[1] - cp.getY();
        int waiting = waiting(s.market);   // 0.3.3.0: decisions waiting at this colony, on the button
        if (button != null && parent == in && s.sawButton && Math.abs(rx - relX) < 1f && Math.abs(ry - relY) < 1f && waiting == shownWaiting) return;   // still in place
        remove();
        shownWaiting = waiting;
        button = make(waiting);
        in.addComponent(button).inBL(rx, ry);
        parent = in;
        relX = rx;
        relY = ry;
        if (placements < 6) {
            placements++;
            float[] g = gridBox(s), b = anchor(s, g);
            CADiagnostics.LOG.info(CADiagnostics.TAG + "colony screen button placed at " + Math.round(at[0]) + ", " + Math.round(at[1]) + " on "
                    + s.market.getName() + ", in " + in.getClass().getName() + " " + box(rect(in)) + "; buildings " + box(g)
                    + (b != null ? ", next to the button at " + box(b) : ", no button under them") + "; searched " + s.tab.getClass().getName()
                    + (s.note != null ? " (" + s.note + ")" : "") + ", screen " + Math.round(screen(true)) + "x" + Math.round(screen(false)));
        }
    }

    static String box(float[] r) {
        return r == null ? "none" : "(" + Math.round(r[0]) + ", " + Math.round(r[1]) + ", " + Math.round(r[2]) + "x" + Math.round(r[3]) + ")";
    }

    /**
     * The lowest panel above the buildings that holds the whole spot and all the buildings: the button then moves and
     * hides with the colony screen. Never the grid itself or a tile, which lay out their own children. The searched root
     * when there's none (or nothing is known about the panels).
     */
    static UIPanelAPI container(Screen s, float[] at) {
        float[] r = {at[0], at[1], W, H}, g = gridBox(s);
        if (g != null) {
            UIComponentAPI c = s.up.get(s.tiles.get(0));
            for (int i = 0; c != null && i < 40; i++, c = s.up.get(c)) {
                if (!(c instanceof UIPanelAPI) || c == s.grid || s.tiles.contains(c) || c.getClass().getName().equals(GRID_CLASS)) continue;
                float[] cr = rect(c);
                cr = new float[]{cr[0] - 2f, cr[1] - 2f, cr[2] + 4f, cr[3] + 4f};   // a tile can sit a pixel past its panel's edge
                if (inside(r, cr) && inside(g, cr)) return (UIPanelAPI) c;
            }
        }
        return s.tab;
    }

    void remove() {
        try { if (button != null && parent != null) parent.removeComponent(button); } catch (Throwable ignored) { }
        button = null;
        parent = null;
        relX = relY = Float.NaN;
    }

    private int shownWaiting = -1;

    /** 0.3.3.0: decisions waiting at this colony (its build proposal and the ladder's proposals there). */
    static int waiting(MarketAPI m) {
        if (m == null) return 0;
        int n = 0;
        try { for (CAApprovals.Item it : CAApprovals.collect()) if (m.getId().equals(it.marketId)) n++; } catch (Throwable ignored) { }
        return n;
    }

    CustomPanelAPI make() { return make(0); }

    CustomPanelAPI make(int waiting) {
        CustomPanelAPI p = Global.getSettings().createCustom(W, H, new BaseCustomUIPanelPlugin() {
            @Override public void buttonPressed(Object id) { if (OPEN.equals(id)) open(); }
        });
        TooltipMakerAPI t = p.createUIElement(W, H, false);
        ButtonAPI b = t.addButton(waiting > 0 ? "Colony Autopilot (" + waiting + ")" : "Colony Autopilot", OPEN, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, W, H, 0f);
        b.getPosition().inTL(0f, 0f);
        p.addUIElement(t).inTL(0f, 0f);
        return p;
    }

    /** Opens the colony panel in the game's own custom dialog, as the right-click menu does. */
    void open() {
        try {
            Screen s = read();
            if (s == null || !linked()) return;
            // 0.3.0.0: the handles looked up once a session (linked); invoking the constructor initializes the dialog class
            Object dialogParent = CARefl.call(s.core, "getDialogParent");
            Object d = dialogNew.invokeWithArguments(CAColonyDialog.W, CAColonyDialog.H, new CAColonyDialog(s.market), dialogParent, s.grid);
            dialogShow.invokeWithArguments(d, 0.3f, 0.2f);
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
    /** The buildings' bounding box (x, y, width, height), or null with none. */
    static float[] gridBox(Screen s) {
        if (s.tiles.isEmpty()) return null;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (UIComponentAPI t : s.tiles) {
            float[] r = rect(t);
            minX = Math.min(minX, r[0]); minY = Math.min(minY, r[1]); maxX = Math.max(maxX, r[0] + r[2]); maxY = Math.max(maxY, r[1] + r[3]);
        }
        return new float[]{minX, minY, maxX - minX, maxY - minY};
    }

    /** The nearest button under the buildings ("Add industry or structure"), or null. */
    static float[] anchor(Screen s, float[] g) {
        if (g == null) return null;
        float minX = g[0], minY = g[1], maxX = g[0] + g[2];
        float[] below = null;
        for (UIComponentAPI c : s.leaves) {
            if (!(c instanceof ButtonAPI)) continue;
            float[] r = rect(c);
            boolean under = r[1] + r[3] <= minY + 2f && r[1] + r[3] >= minY - 300f && r[0] < maxX && r[0] + r[2] > minX;
            if (under && (below == null || r[1] + r[3] > below[1] + below[3])) below = r;
        }
        return below;
    }

    static float[] spot(Screen s) {
        float[] tab = rect(s.tab), g = gridBox(s);
        if (g == null) return null;
        float minX = g[0], minY = g[1], maxX = g[0] + g[2], maxY = g[1] + g[3];
        float sw = screen(true), sh = screen(false);
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
        float[] below = anchor(s, g);   // the nearest button under the grid ("Add industry or structure")
        if (below != null) {
            candidates.add(new float[]{below[0] + below[2] + GAP, below[1] + (below[3] - H) / 2f});   // right of it
            candidates.add(new float[]{below[0], below[1] - GAP - H});                                 // under it
        }
        candidates.add(new float[]{maxX - W, minY - GAP - H});
        candidates.add(new float[]{maxX - W, maxY + GAP});
        candidates.add(new float[]{minX, minY - GAP - H});
        candidates.add(new float[]{maxX + GAP, maxY - H});
        // then any free place along the rows under and above the buildings and the columns beside them, right to left and
        // top to bottom: a full grid (12 or more buildings) leaves none of the spots above
        for (float x = maxX - W; x >= minX - W; x -= STEP) {
            candidates.add(new float[]{x, minY - GAP - H});
            candidates.add(new float[]{x, maxY + GAP});
            candidates.add(new float[]{x, minY - 2f * (GAP + H)});
        }
        for (float y = maxY - H; y >= minY - H; y -= STEP) {
            candidates.add(new float[]{maxX + GAP, y});
            candidates.add(new float[]{minX - GAP - W, y});
        }
        for (float[] at : candidates) {
            float[] r = {at[0], at[1], W, H};
            if (!inside(r, tab) || (sw > 0f && !inside(r, new float[]{0f, 0f, sw, sh}))) continue;
            boolean free = true;
            for (float[] o : taken) if (overlaps(r, o, 4f)) { free = false; break; }
            if (free) return at;
        }
        return null;
    }
}
