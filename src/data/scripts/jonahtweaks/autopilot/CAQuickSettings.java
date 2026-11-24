package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 0.3.16.9: the Settings page while LunaLib holds the settings (CALunaSync.active): the settings a player changes most, on
 * one screen without a scroll bar, three buttons to a row. Each button shows the setting and its value; a click moves to the
 * next value through CASetupUI.handle with the full page's keys, so the change is marked as yours and LunaLib's menu (F3)
 * shows it (CAModeTable.clickEnd, CALunaSync.sync). Hovering a button says what it does. A value an ability that's off holds
 * back in story mode shows as held (CASync.rowLock). LunaLib's menu keeps every setting, these included.
 */
public final class CAQuickSettings {
    private CAQuickSettings() { }

    static final int COLS = 3;
    static final float GAP = 6f, BTN_H = 24f;

    /** One button: its row key (CASetupUI), what it shows, its hover text, and its id (null: the row's next value). */
    static final class Cell {
        final String key, text; final String[] tip; final Object id;
        Cell(String key, String label, String value, String[] tip, Object id) {
            this.key = key; this.text = label + ": " + value; this.tip = tip; this.id = id;
        }
    }

    static String[] tip(String key, String... extra) {
        List<String> l = new ArrayList<String>();
        String d = CASetupUI.DESC.get(key);
        if (d != null) l.add(d);
        String[] h = CASetupUI.HOVER.get(key);
        if (h != null) for (String x : h) l.add(x);
        for (String x : extra) l.add(x);
        return l.toArray(new String[0]);
    }

    static Cell cell(String key, String label, String value, String... extra) { return new Cell(key, label, value, tip(key, extra), null); }

    static Cell preset(CASettings s, String key, String label) { return cell(key, label, CAPresets.name(s, CASetupUI.PRESET_KEYS.get(key))); }

    public static void render(TooltipMakerAPI t, CASettings s, float width) {
        CASetupUI.marked.clear();
        float w = Math.max(600f, width - CALog.SCROLL_ROOM);
        boolean nex = CASetupUI.nex();

        CATheme.heading(t, "Playstyle", 6f);
        List<Cell> play = new ArrayList<Cell>();
        play.add(cell("empire", "Empire mode", s.empire().label));
        play.add(cell("balance", "Balance", CABalance.name(s)));
        play.add(preset(s, "p_spend", "Spending"));
        play.add(preset(s, "p_cores", "AI cores"));
        play.add(cell("profile", "Profile", CAProfile.name(s)));
        play.add(preset(s, "p_newcol", "New colonies"));
        play.add(preset(s, "p_war", "War"));
        grid(t, play, w);
        reserve(t, s);

        CATheme.heading(t, "Colonies", 10f);
        List<Cell> col = new ArrayList<Cell>();
        col.add(preset(s, "p_station", "Stations"));
        col.add(preset(s, "p_takedown", "Take down buildings"));
        col.add(preset(s, "p_policy", "Colony policies"));
        col.add(preset(s, "p_admins", "Administrators"));
        col.add(cell("capid", "Capital", CACapital.on(s) ? CACapital.name(s) : "None"));
        col.add(cell("fleetlimit", "Fleet limit per system", CASetupUI.onOff(!s.noFleetLimit)));
        grid(t, col, w);

        CATheme.heading(t, "Storage", 10f);
        List<Cell> rest = new ArrayList<Cell>();
        rest.add(cell("logiall", "Gather everything at the capital", CASetupUI.onOff(s.logisticsAll)));
        rest.add(cell("techfinds", "Tech-Mining finds to Autopilot Storage", CASetupUI.onOff(!s.techFindsKeep)));
        rest.add(cell("logihub", "Refit hub", CALogistics.hubName(s.logisticsHub, true)));
        rest.add(cell("logisell", "Sell hub", CALogistics.hubName(s.logisticsSellHub, false)));
        rest.add(cell("screenlog", "On-screen log", CALog.SCREEN_NAMES[CASetupUI.clamp(s.screenLog, 3)]));
        if (nex && s.spyMode == 2) rest.add(cell("spytarget", "Offense target", CASetupUI.spyTargetName(s.spyTarget)));
        rest.add(new Cell("stances", "Faction stances", "Open", tip("stances"), CANav.PREFIX + CASetupUI.FACTIONS_PAGE));
        grid(t, rest, w);

        t.addPara(CALunaSync.MORE, Misc.getGrayColor(), 10f);
    }

    /** The cells, COLS to a row, each the same width. */
    static void grid(TooltipMakerAPI t, List<Cell> cells, float w) {
        float cw = (w - GAP * (COLS - 1)) / COLS;
        Color base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor(), gray = Misc.getGrayColor();
        CASettings s = CAData.settings();
        ButtonAPI first = null, prev = null;
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            if (i % COLS == 0 && first != null) { CALog.endRow(t, first); first = null; prev = null; }
            String lock = c.id == null ? CASync.rowLock(c.key, s) : null;
            boolean held = lock != null && CASync.rowWhole(c.key);
            Object id = held ? CASync.PREFIX + "none" : c.id != null ? c.id : CASetupUI.PREFIX + c.key + ":next";
            ButtonAPI b = t.addButton(c.text, id, held ? gray : base, dark, Alignment.LMID, CutStyle.TL_BR, cw, BTN_H, first == null ? GAP : 0f);
            if (prev != null) b.getPosition().rightOfTop(prev, GAP);
            String[] tip = lock == null ? c.tip : append(c.tip, lock);
            if (tip.length > 0) t.addTooltipToPrevious(CASetupUI.tip(tip), TooltipMakerAPI.TooltipLocation.BELOW);
            if (first == null) first = b;
            prev = b;
        }
        if (first != null) CALog.endRow(t, first);
    }

    static String[] append(String[] a, String x) {
        String[] out = new String[a.length + 1];
        System.arraycopy(a, 0, out, 0, a.length);
        out[a.length] = x;
        return out;
    }

    /** The credit reserve: four step buttons, then its value. */
    static void reserve(TooltipMakerAPI t, CASettings s) {
        Color base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        String[][] steps = {{"-500k", "--"}, {"-50k", "-"}, {"+50k", "+"}, {"+500k", "++"}};
        ButtonAPI first = null, prev = null;
        for (String[] st : steps) {
            ButtonAPI b = t.addButton(st[0], CASetupUI.PREFIX + "reserve:" + st[1], base, dark, Alignment.MID, CutStyle.TL_BR, 64f, BTN_H, first == null ? GAP : 0f);
            if (prev != null) b.getPosition().rightOfTop(prev, GAP);
            if (first == null) first = b;
            prev = b;
        }
        LabelAPI l = t.addPara("Credit reserve: %s.", 0f, Misc.getHighlightColor(), Misc.getDGSCredits(s.reserve));
        t.addTooltipToPrevious(CASetupUI.tip(tip("reserve")), TooltipMakerAPI.TooltipLocation.BELOW);
        l.getPosition().rightOfMid(prev, 12f);
        CALog.endRow(t, first);
    }
}
