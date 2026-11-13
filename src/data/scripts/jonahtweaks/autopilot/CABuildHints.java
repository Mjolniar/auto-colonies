package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * 0.3.3.0: Advisor hints in the game's build menu (plans/2026-11-13-modes-rework.md section 4). When you open "Add
 * industry or structure" at a colony in the hints scope (CASettings.buildHints: Advisor colonies by default), a read-only
 * panel beside the menu lists Autopilot's ranking: Best and Good builds with what they earn, Poor ones with a short reason
 * (pollution, the Pather line, stability, money lost, payback). Both build menus: the game's own
 * (com.fs.starfarer.campaign.ui.marketinfo.IndustryPickerDialog, the same name on 0.98a and 0.98.5a) and Building Menu
 * Overhaul's (a CustomPanelAPI whose plugin is kaysaar.bmo.buildingmenu.MarketDialog, with a public MarketAPI field).
 * Both are direct children of the core UI, found by class name through CARefl. The panel is added to the core UI after the
 * menu, removed when the menu goes, hidden while something sits above it. It reads kept scores (CAEngine.kept) and never
 * scores while drawing; with hintScoreOnOpen it scores a colony once a day when the menu opens. No log lines, no messages.
 * Any error turns it off for the session (one diagnostics line); the colony panel's ranked list stays.
 */
public class CABuildHints implements EveryFrameScript {
    static final String VANILLA = "com.fs.starfarer.campaign.ui.marketinfo.IndustryPickerDialog";
    static final String BMO_DIALOG = "kaysaar.bmo.buildingmenu.MarketDialog";
    static final String BMO = "bmo";
    static final float W = 300f, GAP = 10f, EDGE = 4f;
    static final int BEST = 3, GOOD = 3;

    static boolean broken = false;
    static int reports = 0;
    /** Colonies scored on open today (market id to game day). */
    static final Map<String, Integer> scored = new HashMap<String, Integer>();

    public static void reset() { broken = false; reports = 0; scored.clear(); }

    private float timer = 0f;
    private CustomPanelAPI panel;
    private UIPanelAPI parent;
    private Object menu;
    private String shownFor;
    private float[] lastRect;

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return true; }   // the colony screen pauses the game

    public void advance(float amount) {
        if (broken) return;
        timer -= amount;
        if (timer > 0f) return;
        timer = 0.1f;
        try { update(); }
        catch (Throwable t) {
            broken = true;
            remove();
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "build menu hints turned off for this session; the colony panel lists the same ranking", t);
        }
    }

    /** What sits in the core UI: the build menu, its colony, and whether it shows upgrades (Building Menu Overhaul). */
    static final class Found {
        UIComponentAPI menu;
        MarketAPI market;
        boolean upgrades;
    }

    void update() throws Exception {
        CASettings s = CAData.settings();
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (s.buildHints == 2 || !CAScreenButton.colonyTab(ui.getCurrentCoreTab())) { remove(); return; }
        InteractionDialogAPI dialog = ui.getCurrentInteractionDialog();
        Object core = dialog != null ? CAScreenButton.tryCall(dialog, "getCoreUI") : null;
        if (!(core instanceof UIPanelAPI)) core = CAScreenButton.tryCall(ui, "getCore");
        if (!(core instanceof UIPanelAPI)) { remove(); return; }
        List<UIComponentAPI> kids = children(core);
        Found f = find(kids);
        if (f == null || f.market == null || !inScope(f.market, s)) { remove(); return; }
        boolean onTop = top(kids) == f.menu;
        float[] r = CAScreenButton.rect(f.menu);
        String sig = f.market.getId() + (f.upgrades ? ":up" : ":new");
        if (panel != null && menu == f.menu && sig.equals(shownFor) && sameRect(lastRect, r)) {
            panel.setOpacity(onTop ? 1f : 0f);
            return;
        }
        if (lastRect == null || !sameRect(lastRect, r)) { lastRect = r; remove(); return; }   // placed once the menu stops growing
        remove();
        float[] at = spot(r, CAScreenButton.screen(true), CAScreenButton.screen(false));
        if (at == null) { log("no room beside the build menu " + CAScreenButton.box(r)); return; }
        CustomPanelAPI p = make(f, s, at[2]);
        if (p == null) return;
        UIPanelAPI c = (UIPanelAPI) core;
        PositionAPI cp = c.getPosition();
        c.addComponent(p).inBL(at[0] - cp.getX(), at[1] - cp.getY());
        p.setOpacity(onTop ? 1f : 0f);
        panel = p; parent = c; menu = f.menu; shownFor = sig;
    }

    static List<UIComponentAPI> children(Object panel) {
        List<UIComponentAPI> out = new ArrayList<UIComponentAPI>();
        Object kids = CAScreenButton.tryCall(panel, "getChildrenCopy");
        if (kids instanceof List) for (Object o : (List<?>) kids) if (o instanceof UIComponentAPI) out.add((UIComponentAPI) o);
        return out;
    }

    /** The last child that isn't one of these panels: what the player sees on top. */
    UIComponentAPI top(List<UIComponentAPI> kids) {
        for (int i = kids.size() - 1; i >= 0; i--) if (kids.get(i) != panel) return kids.get(i);
        return null;
    }

    /** The build menu among the core UI's children, the newest first, or null. */
    static Found find(List<UIComponentAPI> kids) {
        boolean bmo = Global.getSettings().getModManager().isModEnabled(BMO);
        for (int i = kids.size() - 1; i >= 0; i--) {
            UIComponentAPI k = kids.get(i);
            if (k.getOpacity() <= 0f) continue;
            if (k instanceof UIPanelAPI && VANILLA.equals(k.getClass().getName())) {
                Found f = new Found();
                f.menu = k;
                f.market = CAScreenButton.marketOf(k);
                return f;
            }
            if (bmo && k instanceof CustomPanelAPI) {
                Object plugin = ((CustomPanelAPI) k).getPlugin();
                if (plugin == null || !BMO_DIALOG.equals(plugin.getClass().getName())) continue;
                Found f = new Found();
                f.menu = k;
                f.market = CAScreenButton.marketOf(plugin);
                Object up = CAScreenButton.tryCall(plugin, "isInUpgradeMode");
                f.upgrades = Boolean.TRUE.equals(up);
                return f;
            }
        }
        return null;
    }

    /** Whether a colony gets hints: yours, its mode in the scope (0 Advisor, 1 any but Off), not an outpost or hypershunt. */
    static boolean inScope(MarketAPI m, CASettings s) {
        if (m == null || !m.isPlayerOwned() || s.buildHints == 2) return false;
        CAData.Mode mode = CAData.get().colony(m.getId()).mode;
        if (!scope(s.buildHints, mode)) return false;
        return !CAEngine.isOutpost(m) && !CAHypershunt.is(m);
    }

    /** Pure: the scope rule. */
    static boolean scope(int buildHints, CAData.Mode mode) {
        if (buildHints == 2 || mode == null || mode == CAData.Mode.OFF) return false;
        return buildHints == 1 || mode == CAData.Mode.ADVISOR;
    }

    static boolean sameRect(float[] a, float[] b) {
        if (a == null || b == null) return false;
        for (int i = 0; i < 4; i++) if (Math.abs(a[i] - b[i]) > 0.5f) return false;
        return true;
    }

    /** Pure: bottom-left corner and height of the panel beside the menu (right, else left), or null with no room. */
    static float[] spot(float[] menu, float sw, float sh) {
        float h = Math.max(120f, menu[3]);
        if (sh > 0f) h = Math.min(h, sh - 2f * EDGE);
        float y = menu[1] + menu[3] - h;
        if (sh > 0f) y = Math.max(EDGE, Math.min(y, sh - h - EDGE));
        float right = menu[0] + menu[2] + GAP, left = menu[0] - GAP - W;
        if (sw <= 0f || right + W <= sw - EDGE) return new float[]{right, y, h};
        if (left >= EDGE) return new float[]{left, y, h};
        return null;
    }

    void remove() {
        try { if (panel != null && parent != null) parent.removeComponent(panel); } catch (Throwable ignored) { }
        panel = null; parent = null; menu = null; shownFor = null;
    }

    static void log(String what) {
        if (reports >= 4) return;
        reports++;
        CADiagnostics.LOG.info(CADiagnostics.TAG + "build menu hints: " + what);
    }

    // ---------------- the panel ----------------

    /** The goal the colony's ranking uses: fast buildout's Money, Security while an Approve or Auto colony is threatened, else its own. */
    static CAData.Profile goal(MarketAPI m, CAData.Colony c) {
        boolean managed = c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO;
        boolean buildout = c.buildout && c.mode == CAData.Mode.AUTO;
        return buildout ? CAData.Profile.INCOME : managed ? CAThreat.effective(m, c.profile) : c.profile;
    }

    /** The kept ranking for the colony; with hintScoreOnOpen, scored first when out of date (once a colony a day). */
    static CAEngine.Kept ranking(MarketAPI m, CASettings s) {
        CAData.Colony c = CAData.get().colony(m.getId());
        boolean buildout = c.buildout && c.mode == CAData.Mode.AUTO;
        CAData.Profile g = goal(m, c);
        CAEngine.Kept k = CAEngine.kept(m, g, s.minStability, buildout);
        if (s.hintScoreOnOpen && (k == null || !k.current)) {
            int day = CATime.today();
            Integer last = scored.get(m.getId());
            if (last == null || last != day) {
                scored.put(m.getId(), day);
                try { CAEngine.refresh(m, g, s.minStability, buildout); k = CAEngine.kept(m, g, s.minStability, buildout); }
                catch (Throwable t) { CAStep.warn("build menu hints", "scoring " + m.getName() + " failed", t); }
            }
        }
        return k;
    }

    static CustomPanelAPI make(Found f, CASettings s, float h) {
        CAEngine.Kept k = ranking(f.market, s);
        CustomPanelAPI p = Global.getSettings().createCustom(W, h, new BaseCustomUIPanelPlugin());
        TooltipMakerAPI t = p.createUIElement(W, h, false);
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor(), pos = Misc.getPositiveHighlightColor();
        CAData.Colony c = CAData.get().colony(f.market.getId());
        t.addTitle("AUTOPILOT // " + f.market.getName().toUpperCase(Locale.ROOT));
        String head = goal(f.market, c).label + " goal" + (k == null ? ", not scored." : k.current ? "." : ", " + Math.max(1, k.days) + " days old.");
        t.addPara(CALog.esc(head), gray, 2f);
        if (k == null) { p.addUIElement(t).inTL(0f, 0f); return p; }
        List<Hint> hints = hints(k.result, f.upgrades, s, CAStability.now(f.market), CAStability.floor());
        int shown = 0;
        String lastTag = null;
        for (Hint x : hints) {
            if (t.getHeightSoFar() > h - 44f) break;
            if (!x.tag.equals(lastTag)) { t.addPara(x.tag, "Poor".equals(x.tag) ? bad : "Best".equals(x.tag) ? pos : hl, 8f); lastTag = x.tag; }
            t.addPara("%s: " + CALog.esc(x.reason), 2f, hl, x.name);
            shown++;
        }
        if (shown < hints.size()) t.addPara(CALog.esc((hints.size() - shown) + " more."), gray, 6f);
        for (String line : footer(k.result, f.upgrades)) if (t.getHeightSoFar() < h - 20f) t.addPara(CALog.esc(line), gray, 4f);
        p.addUIElement(t).inTL(0f, 0f);
        return p;
    }

    // ---------------- the ranking as lines (pure) ----------------

    static final class Hint {
        String tag, name, reason;
        Hint(String tag, String name, String reason) { this.tag = tag; this.name = name; this.reason = reason; }
    }

    /** Pure: every pick of the kind the menu shows (new builds, or upgrades), best first, tagged Best, Good or Poor. */
    static List<Hint> hints(CAEngine.Result r, boolean upgrades, CASettings s, float stabNow, float floor) {
        List<Hint> out = new ArrayList<Hint>();
        if (r == null) return out;
        int rank = 0;
        for (CAEngine.Pick p : r.picks) {
            if (p.upgrade != upgrades) continue;
            String[] tg = tag(p, ++rank, s, stabNow, floor);
            out.add(new Hint(tg[0], p.name, tg[1]));
        }
        return out;
    }

    /** Pure: {Best | Good | Poor, short reason} for a pick at its rank among the menu's kind. */
    static String[] tag(CAEngine.Pick p, int rank, CASettings s, float stabNow, float floor) {
        if (CAEngine.worthwhile(p)) return new String[]{rank <= BEST ? "Best" : "Good", goodReason(p, s)};
        return new String[]{"Poor", poorReason(p, s, stabNow, floor)};
    }

    static String goodReason(CAEngine.Pick p, CASettings s) {
        if (p.fx[0] > 0f) {
            int pay = payback(p, s);
            return "+" + num(p.fx[0]) + " a month" + (pay > 0 ? ", payback " + pay + " months" : "");
        }
        if (!p.lines.isEmpty()) return cut(p.lines.get(0), 50);
        return "score +" + num(p.score);
    }

    static String poorReason(CAEngine.Pick p, CASettings s, float stabNow, float floor) {
        if (p.pollution) return "pollution";
        if (p.lowProfile != null) return "crosses the Pather line";
        if (p.fx[1] < 0f && stabNow + p.fx[1] < floor) return "stability to " + Math.round(stabNow + p.fx[1]) + ", floor " + Math.round(floor);
        if (p.fx[0] < 0f) return "loses " + num(-p.fx[0]) + " a month";
        int pay = payback(p, s), h = Math.round(p.horizon > 0f ? p.horizon : CATuning.PAYBACK_MONTHS);
        if (pay > h) return "payback " + pay + " months";
        return "score below 0";
    }

    /** Pure: months for its money to pay back the price and fee, or -1 when it earns none. */
    static int payback(CAEngine.Pick p, CASettings s) {
        if (!(p.fx[0] > 0f)) return -1;
        float fee = s == null ? 0f : CABudget.constructionFee(s, p.cost);
        return (int) Math.ceil((p.cost + fee) / p.fx[0]);
    }

    static List<String> footer(CAEngine.Result r, boolean upgrades) {
        List<String> out = new ArrayList<String>();
        if (r == null) return out;
        if (!upgrades && r.blocked != null) out.add(cut(r.blocked, 80) + ".");
        if (!upgrades && r.noSlot > 0) out.add(r.noSlot + " need an industry slot" + (r.bestNoSlot != null ? ". Best: " + r.bestNoSlot.name : "") + ".");
        if (r.locked > 0) out.add(r.locked + " need research (AotD).");
        if (r.unsupportedBuildable > 0) out.add(r.unsupportedBuildable + " mod buildings not scored.");
        if (!upgrades) {
            for (CAEngine.Pick p : r.picks) if (p.upgrade && CAEngine.worthwhile(p)) { out.add("Best upgrade: " + p.name + "."); break; }
        }
        return out;
    }

    static String num(float v) { return String.format(Locale.ROOT, "%,d", Math.round(v)); }

    static String cut(String s, int n) { return s == null ? "" : s.length() <= n ? s : s.substring(0, n - 3).trim() + "..."; }

    // ---------------- the build menu row's tooltip (0.98.5a; CAOptionProvider) ----------------

    /** One line for the row's tooltip, or null when the colony is out of scope or not scored. */
    static String[] tooltip(Industry ind) {
        if (ind == null || ind.getMarket() == null) return null;
        MarketAPI m = ind.getMarket();
        CASettings s = CAData.settings();
        if (!inScope(m, s)) return null;
        CAData.Colony c = CAData.get().colony(m.getId());
        CAEngine.Kept k = CAEngine.kept(m, goal(m, c), s.minStability, c.buildout && c.mode == CAData.Mode.AUTO);
        if (k == null) return new String[]{"not scored here", ""};
        int rank = 0, of = 0;
        CAEngine.Pick hit = null;
        for (CAEngine.Pick p : k.result.picks) {
            if (p.upgrade) continue;
            of++;
            if (hit == null && p.id.equals(ind.getId())) { hit = p; rank = of; }
        }
        if (hit == null) return new String[]{"not scored here", ""};
        String[] tg = tag(hit, rank, s, CAStability.now(m), CAStability.floor());
        return new String[]{tg[0] + ", " + rank + " of " + of + " for the " + goal(m, c).label + " goal", tg[1]};
    }
}
