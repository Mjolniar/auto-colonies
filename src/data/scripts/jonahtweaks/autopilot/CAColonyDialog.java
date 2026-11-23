package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The colony panel, opened from the colony screen: profile, mode, approvals, plan and recommended builds, locks, and a
 * page per mod feature that applies here, one page at a time from the list on the left (CANav).
 * 0.3.0.0 (WS-F3, research 04 sections 2 and 7): shorter text. Each paragraph is 40 words or fewer; the score make-up of
 * each recommended build, the siege detail and the fast buildout detail are hover text (CATheme.hover). The first-time
 * setup shows the 5 rows of DECISIONS 17 (CASetupUI.renderFirstTime) in place of the whole settings screen.
 */
public class CAColonyDialog extends BaseCustomDialogDelegate {
    /** 0.3.15.1: sized to the screen, up to 1380 x 900 and never under 980 x 640 (callers read w() and h()). */
    final float W = w(), H = h();
    public static float w() { try { return Math.max(980f, Math.min(1380f, Global.getSettings().getScreenWidth() - 120f)); } catch (Throwable t) { return 980f; } }
    public static float h() { try { return Math.max(640f, Math.min(900f, Global.getSettings().getScreenHeight() - 160f)); } catch (Throwable t) { return 640f; } }
    /** 0.3.15.1: the empire run tiles (Advisor, Approve, Auto): every colony and the empire mode at once. */
    static final String RUN = "ca:run:";
    static final String APPROVE = "ca:approve", REJECT = "ca:reject", SAVE_SETUP = "ca:savesetup", BACK = "ca:back";
    /** 0.3.3.0: sets the empire mode to this colony's mode. */
    static final String EMPIRE = "ca:empire";
    static final String REFRESH = "ca:refresh";   // 0.3.0.0 (A4 fix): Recommended builds' Refresh (CAEngine.refresh)
    static final String HS = "ca:hs:";   // Coronal Hypershunt actions: ca:hs:<action>:<arg>
    static final String MEGA = "ca:mega:";   // Pluto / Nidavelir actions: ca:mega:<action>:<arg>
    static final String TASC = "ca:tasc:";   // start a TASC terraforming project: ca:tasc:<project name>
    /** 0.3.7.1: the Empire pages (the old intel tab's) carry this prefix in the page list; Confirm and Cancel for a button that asks first. */
    static final String EMP = "e:", CONFIRM = "ca:confirm", CANCEL = "ca:cancel", MORE = "ca:more:", LOWPROF = "ca:lowprofile";
    /** 0.3.7.1: an Empire page's button waiting for Confirm (the intel's confirmation dialog). */
    Object pending;
    /** 0.3.0.0 (WS-F3): the message after a button that threw. */
    static final String FAILED = "Failed. See starsector.log";
    final MarketAPI market;
    CustomPanelAPI root, content;
    String message = null;
    boolean dirty = false, setupPage = false;
    CAData.Mode setupMode = null;
    /** 0.3.8.7: the settings before the first-time setup page opened (CAModeTable.keep); put back on Back and Close, null after Save. */
    java.util.Map<String, Object> setupBefore;
    final Plugin plugin = new Plugin();
    TooltipMakerAPI lastTooltip;
    boolean lastSetupPage;
    /** The page shown; Overview each time the panel opens. */
    String page = "main", lastPage;   // 0.3.15.1: one page; the rest open from its buttons
    /** 0.3.7.3: the More page's sub-page (an old intel tab page id); the first one when null. */
    String morePage = null;
    /** 0.3.7.6: story mode's Colony page: the sections opened (numbers, money, plan, locks, mods); all closed when the panel opens. */
    final java.util.Set<String> openSec = new java.util.HashSet<String>();
    static final String SEC = "ca:sec:";
    static final String AUTARCH = "ca:autarch";   // 0.3.10.2: Seat Autarch (CAAutarchAdmin.seatByYou)

    /** Scroll position of the previous build, kept across rebuilds of the same page. */
    float savedScroll() {
        if (lastTooltip == null || lastSetupPage != setupPage || !page.equals(lastPage) || lastTooltip.getExternalScroller() == null) return 0f;
        return lastTooltip.getExternalScroller().getYOffset();
    }

    void restoreScroll(TooltipMakerAPI t, float y) {
        if (t.getExternalScroller() != null) t.getExternalScroller().setYOffset(y);
        lastTooltip = t;
        lastSetupPage = setupPage;
        lastPage = page;
    }

    public CAColonyDialog(MarketAPI market) { this.market = market; }

    class Plugin extends CATheme.Frame {   // the grid and neon frames behind the panel (0.2.41.0)
        Plugin() { super(CANav.W, CANav.GAP); }
        @Override public void buttonPressed(Object id) {
            CASettings s = CAData.settings();
            CAModeTable.clickStart(s);   // 0.3.3.0: a setting this click changes is yours
            try { handle(id); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony panel button failed", t); message = FAILED; dirty = true; }
            finally { CAModeTable.clickEnd(s, id); }
        }
        @Override public void advance(float amount) { if (dirty) { dirty = false; safeBuild(); } }
    }

    /** Builds the panel; an error shows a line instead of reaching the game's UI (0.2.37.0). */
    void safeBuild() {
        long t0 = System.nanoTime();
        CAEngine.drawing(true);   // 0.3.0.0 (A4 fix, 07 M6): nothing drawn here evaluates the colony (CAAugments' slot value included)
        try { build(); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony panel failed to draw", t); }
        finally { CAEngine.drawing(false); }
        CAPerf.record("colony panel: " + (setupPage ? "setup" : page), System.nanoTime() - t0);   // 0.3.0.0 (A4): what a page costs to draw
    }

    @Override public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) { root = panel; safeBuild(); }
    @Override public boolean hasCancelButton() { return false; }
    @Override public String getConfirmText() { return "Close"; }
    @Override public CustomUIPanelPlugin getCustomPanelPlugin() { return plugin; }
    @Override public void customDialogConfirm() { closed(); }
    @Override public void customDialogCancel() { closed(); }

    /** 0.3.8.7: the panel closed. A first-time setup page that was not saved puts the settings back as they were. */
    void closed() {
        try {
            if (!setupPage || setupBefore == null) return;
            CAModeTable.putBack(CAData.settings(), setupBefore);
            setupBefore = null;
            setupPage = false;
            CALunaSync.sync();
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony panel close failed", t); }
    }

    void handle(Object id) {
        CAData d = CAData.get();
        CAData.Colony c = d.colony(market.getId());
        String handled;
        // 0.3.7.1: the Empire pages' buttons go to their own handler; a button that asks first waits for Confirm
        if (CONFIRM.equals(id)) { Object p = pending; pending = null; if (p != null) CAIntel.pressEmpire(p); dirty = true; return; }
        if (CANCEL.equals(id)) { pending = null; dirty = true; return; }
        if (id instanceof String && ((String) id).startsWith(SEC)) {   // 0.3.7.6
            String sec = ((String) id).substring(SEC.length());
            if (!openSec.remove(sec)) openSec.add(sec);
            dirty = true;
            return;
        }
        if (id instanceof String && ((String) id).startsWith(MORE)) { morePage = ((String) id).substring(MORE.length()); pending = null; message = null; dirty = true; return; }
        if (CANav.page(id) == null && (page.startsWith(EMP) || page.equals("more"))) {
            if (CAIntel.needsConfirm(id)) pending = id;
            else { pending = null; CAIntel.pressEmpire(id); }
            message = null;
            dirty = true;
            return;
        }
        if (CANav.page(id) != null) pending = null;
        if (id instanceof String && ((String) id).startsWith(RUN)) {   // 0.3.15.1
            try { runEmpire(CAData.Mode.valueOf(((String) id).substring(RUN.length()))); } catch (IllegalArgumentException ignored) { }
            dirty = true;
            return;
        }
        if (CASync.handle(id)) { dirty = true; return; }   // 0.3.6.0 story mode
        if (CASetupUI.handle(id, d.settings)) { dirty = true; return; }
        if (CANav.page(id) != null) {
            page = CANav.page(id);
            // 0.3.8.7: a link to a page the list does not show (Faction stances' OPEN) opens it under More
            if (!listed(pages(c, d.settings, CAHypershunt.is(market), CAEngine.isOutpost(market)), page)) page = EMP + page;   // 0.3.15.1: an Empire page
            message = null; dirty = true; return;
        }
        if (id instanceof CAData.Profile) { c.profile = (CAData.Profile) id; message = "Goal set to " + c.profile.label; }
        else if (id instanceof CAData.Mode) {
            CAData.Mode mo = (CAData.Mode) id;
            boolean needsSetup = (mo == CAData.Mode.APPROVE || mo == CAData.Mode.AUTO) && !d.settings.setupDone;
            if (needsSetup && CALunaSync.keepsSetup()) { d.settings.setupDone = true; needsSetup = false; }   // 0.3.4.0: LunaLib's settings stay
            // 0.3.0.0 (WS-F3): the Settings page opens on Playstyle afterwards (CASetupUI's groups since F1)
            if (needsSetup) {
                if (setupBefore == null) setupBefore = CAModeTable.keep(d.settings);   // 0.3.8.7: Back and Close put these back
                setupPage = true; setupMode = mo; CASetupUI.applyRecommended(d.settings); CASetupUI.open.clear(); CASetupUI.open.add("playstyle");
                // 0.3.3.0: the setup rows show the chosen mode's own defaults, so what you see is what Save keeps
                d.settings.empireMode = mo;
                CAModeTable.apply(d.settings, mo);
            }
            else { d.setMode(c, mo); message = CASync.modeNote != null ? CASync.modeNote : "Mode set to " + mo.label; }   // 0.3.6.0 story mode: held back
        }
        else if (SAVE_SETUP.equals(id)) {
            d.settings.setupDone = true; setupPage = false; setupBefore = null;   // 0.3.8.7: saved, nothing to put back
            if (setupMode != null) d.setMode(c, setupMode);
            // 0.3.3.0: the first setup sets the empire mode. 0.3.6.2: the mode you chose, even when story mode holds this colony back
            CAModeTable.setEmpire(d.settings, setupMode != null ? setupMode : c.mode);
            message = "Saved. Mode: " + c.mode.label;
            CALog.think(market.getName() + ": setup saved, mode " + c.mode.label + ".");   // 0.3.8.6: your own click; the Thinking list only
        }
        else if (BACK.equals(id)) {   // 0.3.8.7: the settings go back to what they were before the setup page opened
            if (setupBefore != null) CAModeTable.putBack(d.settings, setupBefore);
            setupBefore = null; setupPage = false; message = null;
        }
        else if (EMPIRE.equals(id)) { CAModeTable.setEmpire(d.settings, c.mode); message = "Empire mode set to " + c.mode.label; }
        else if (LOWPROF.equals(id)) CALowProfile.set(d.settings, !d.settings.lowProfile, "Colony panel");   // 0.3.7.8
        else if (AUTARCH.equals(id)) { String err = CAAutarchAdmin.seatByYou(market); message = err; }   // 0.3.10.2
        else if (REFRESH.equals(id)) {   // 0.3.0.0 (A4 fix, 07 M6): scored on a click, never while the panel draws; no plan tracking
            CAEngine.refresh(market, planWith(c), d.settings.minStability, buildoutRanking(c));
            message = null;
        }
        else if (APPROVE.equals(id)) { String err = CAAuto.approve(market); message = err == null ? "Approved and started" : err; }
        else if (REJECT.equals(id)) { CAAuto.reject(market); message = "Rejected for 180 days"; }
        else if ((handled = CASurplus.handle(market, id)) != null) message = handled;   // 0.3.8.7: each handler runs once a click
        else if ((handled = CACapital.handle(market, id)) != null) message = handled;
        else if (id instanceof String && ((String) id).startsWith(HS)) {
            String[] a = ((String) id).substring(HS.length()).split(":", 2);
            try { message = CAHypershunt.handle(market, a[0], a.length > 1 ? a[1] : ""); }
            catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt action failed at " + market.getName(), e);
                message = FAILED;
            }
        }
        else if (id instanceof String && ((String) id).startsWith(CABuildout.PREFIX)) {
            message = CABuildout.handle(market, ((String) id).substring(CABuildout.PREFIX.length()));
        }
        else if (id instanceof String && ((String) id).startsWith(TASC)) {
            String err = CATasc.handle(market, ((String) id).substring(TASC.length()));
            message = err == null ? "Project started" : err;
        }
        else if (id instanceof String && ((String) id).startsWith(MEGA)) {
            String[] a = ((String) id).substring(MEGA.length()).split(":", 2);
            message = CAMegastructures.handle(market, a[0], a.length > 1 ? a[1] : "");
        }
        else if (id instanceof String && ((String) id).startsWith("ca:lock:")) {
            String ind = ((String) id).substring("ca:lock:".length());
            if (!c.locks.remove(ind)) c.locks.add(ind);
        }
        else if (id instanceof CAEngine.Pick) {
            CAEngine.Pick p = (CAEngine.Pick) id;
            // 0.3.3.0: the game's price plus the construction fee, from credits above the reserve (nothing is free)
            float fee = CABudget.buildTotal(d.settings, p.cost) - p.cost;   // 0.3.9.6 (F8): with Rapid construction's fee
            boolean queues = !p.upgrade && CABuild.buildsFull(market);   // 0.3.12.1: into the game's construction queue
            String err = CAApprovals.startByPlayer(market, p);
            message = err == null ? (queues ? "Queued: " : "Started: ") + p.verb + " " + p.name + " for " + Misc.getDGSCredits(p.cost) + " plus " + Misc.getDGSCredits(fee) + " fee" : err;
        }
        dirty = true;
    }

    static boolean buildoutRanking(CAData.Colony c) { return c.buildout && c.mode == CAData.Mode.AUTO; }

    /** The goal the plan and Recommended builds use: fast buildout's Money, Security while an Approve or Auto colony is threatened, else its own (0.3.0.0: one place, so Refresh scores what's shown). */
    CAData.Profile planWith(CAData.Colony c) { return CABuildHints.goal(market, c); }   // 0.3.8.7: the build menu hints' rule, one copy

    /** 0.3.8.7: whether a page id is in the page list. */
    static boolean listed(List<String[]> pages, String id) {
        for (String[] p : pages) if (p[0] != null && p[0].equals(id)) return true;
        return false;
    }

    // 0.3.0.0 (WS-F3 fix pass 2): every text on this panel shorter, for the mod-wide word budget (JTScreenButtonCheck)
    static final String[] MODE_TEXT = {
            "Off does nothing.",
            "Advisor ranks builds.",
            "Approve asks you first.",
            "Auto builds and sets policies."};

    // ---------------- text (0.3.0.0, WS-F3: each paragraph 40 words or fewer, JTScreenButtonCheck) ----------------

    /** The first-time setup's line above its rows. 0.3.0.0 fix pass 3: shorter (mod-wide word budget, JTScreenButtonCheck). */
    static final String SETUP_INTRO = "We recommend these for your income.";
    /** Under siege: the line on Overview, and its hover. */
    static final String SIEGE_LINE = "Under siege (%s): %s.",
            SIEGE_HOVER = "Trade contracts skip its goods.";
    /** A Nexerelin outpost's Overview line (CAEngine.OUTPOST_TEXT in short). */
    static final String OUTPOST_LINE = "Autopilot skips resupply bases.";
    static final String LOCKS_LINE = "Click to lock one, and Autopilot won't touch it.";
    static final String PICKS_HOVER_NOTE = "Hover for scores.";

    static ButtonAPI rowButton(TooltipMakerAPI t, ButtonAPI prev, String label, Object id, boolean on, float w) {
        Color hl = Misc.getHighlightColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        ButtonAPI b = t.addButton(label, id, on ? hl : base, dark, Alignment.MID, CutStyle.TL_BR, w, 24f, prev == null ? 8f : 0f);
        if (prev != null) b.getPosition().rightOfTop(prev, 6f);
        return b;
    }

    /**
     * The pages in the list on the left: Overview, Plan and builds, Locks, plus Mod features when one applies here; then
     * 0.3.7.1 the Empire pages (the old intel tab's). While story mode's Autopilot is dormant: the Empire pages only.
     */
    List<String[]> pages(CAData.Colony c, CASettings s, boolean shunt, boolean outpost) {
        // 0.3.7.3: four pages. Story first in story mode (the panel opens on it); Colony is Overview, Plan and builds, Locks
        // and Mod features on one page; More holds the rest of the old intel tab's pages behind one row of buttons
        // 0.3.15.1: one page (main) with no list on the left; the Empire pages open from its buttons with Back. The old Colony
        // page stays listed (never shown as a button) so the offline checks can still draw it.
        List<String[]> p = new ArrayList<String[]>();
        p.add(new String[]{"main", "Autopilot"});
        p.add(new String[]{"colony", "Colony"});
        List<String[]> emp;
        try { emp = CAIntel.empirePages(); } catch (Throwable e) { emp = new ArrayList<String[]>(); }
        for (String[] e : emp) if (e[0] != null) p.add(new String[]{EMP + e[0], e[1]});
        return p;
    }

    /** 0.3.7.3: the More page's sub-pages: the old intel tab's pages but Story and Settings. */
    static List<String[]> moreSubs() {
        List<String[]> out = new ArrayList<String[]>();
        List<String[]> emp;
        try { emp = CAIntel.empirePages(); }
        catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "empire page list failed", e); emp = new ArrayList<String[]>(); emp.add(new String[]{"diag", "Diagnostics"}); }
        boolean story = CASync.on();
        for (String[] e : emp) if (e[0] != null && !e[0].equals("story") && (story || !e[0].equals("settings"))) out.add(e);
        return out;
    }

    /** The mod features that apply to this colony, in the order the Mod features page shows them. */
    List<String> modSections(CAData.Colony c, CASettings s, boolean shunt) {
        List<String> l = new ArrayList<String>();
        if (c.mode == CAData.Mode.OFF) return l;
        com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
        if (CATasc.enabled() && !shunt) l.add("tasc");
        if (mm.isModEnabled(CAHypershunt.MOD) && CAMegastructures.has(market)) l.add("mega");
        // 0.3.7.6: station augments run on their own (Mod projects in story mode); no colony section
        if (mm.isModEnabled(CASynergies.MOD) && !shunt) l.add("synergies");
        if (mm.isModEnabled(CARogue.MOD) && CARogue.any(market)) l.add("rogue");   // 0.3.2.1: Rogue Planets' conditions here
        return l;
    }

    /** One section of the Mod features page; a section that fails leaves the others on the page. */
    void renderMod(TooltipMakerAPI t, String id, CAData.Colony c, CASettings s, CAData.Profile planWith, float pad) {
        com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
        // 0.3.8.7: no augments section (modSections has not listed it since 0.3.7.6)
        String title = id.equals("tasc") ? "Terraforming (TASC)" : id.equals("mega") ? "Megastructure (AotD)"
                : id.equals("rogue") ? "Planet conditions (Rogue Planets)" : "Industry synergies (AotD)";
        CATheme.heading(t, title, pad);
        try {
            if (id.equals("tasc")) CATasc.renderColony(t, market, c);
            else if (id.equals("mega") && mm.isModEnabled(CAHypershunt.MOD)) CAMegastructures.renderColony(t, market, c, s);
            else if (id.equals("synergies") && mm.isModEnabled(CASynergies.MOD)) CASynergies.render(t, market);
            else if (id.equals("rogue") && mm.isModEnabled(CARogue.MOD)) CARogue.render(t, market);
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + id + " section failed at " + market.getName(), e);
            t.addPara(CATheme.NOT_SHOWN, Misc.getGrayColor(), 6f);
        }
    }

    void build() {
        float scrollY = savedScroll();
        if (content != null) root.removeComponent(content);
        content = root.createCustomPanel(W, H, plugin);
        CAData data = CAData.get();
        CASettings s = data.settings;
        CAData.Colony c = data.colony(market.getId());
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();

        if (setupPage) {
            plugin.navW = 0f;
            TooltipMakerAPI t = content.createUIElement(W, H, true);
            t.addTitle("AUTOPILOT // FIRST-TIME SETUP");
            t.addPara(SETUP_INTRO, 6f);
            CATheme.heading(t, "Settings", 10f);
            // 0.3.0.0 (WS-F3, DECISIONS 17): Spending, Extras, New colonies, War and the Credit reserve, then one line that the
            // rest is recommended (0.2.45 showed the whole 76-row settings screen here)
            CASetupUI.renderFirstTime(t, s, W);
            ButtonAPI save = t.addButton("Save and switch to " + (setupMode == null ? "Auto" : setupMode.label), SAVE_SETUP, hl, dark,
                    Alignment.MID, CutStyle.TL_BR, 260f, 26f, 14f);
            ButtonAPI back = t.addButton("Back", BACK, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 26f, 0f);
            back.getPosition().rightOfTop(save, 8f);
            CALog.endRow(t, save);
            content.addUIElement(t).inTL(0f, 0f);
            root.addComponent(content).inTL(0f, 0f);
            restoreScroll(t, scrollY);
            return;
        }

        boolean shunt = CAHypershunt.is(market), outpost = CAEngine.isOutpost(market);
        List<String[]> pages = pages(c, s, shunt, outpost);
        page = CANav.valid(pages, page);
        plugin.navW = 0f;   // 0.3.15.1: no list on the left
        float cw = W - 2f * CANav.GAP;
        TooltipMakerAPI t = content.createUIElement(cw, H, true);
        if (message != null) t.addPara(message, hl, 0f);   // 0.3.8.6: no CALog.esc: plain addPara text and highlight values show as is
        if (page.equals("main")) {
            renderMain(t, s, cw);
            content.addUIElement(t).inTL(CANav.GAP, 0f);
            root.addComponent(content).inTL(0f, 0f);
            restoreScroll(t, scrollY);
            return;
        }
        if (page.startsWith(EMP)) {
            ButtonAPI back = t.addButton("Back", CANav.PREFIX + "main", base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 24f, message != null ? 6f : 0f);
            CALog.endRow(t, back, 8f);
        }

        // 0.3.7.1: an Empire page (the old intel tab's), with a button that asks first shown above it. 0.3.7.3: More shows
        // one of them, picked from a row of buttons
        if (page.startsWith(EMP) || page.equals("more")) {
            String show = page.startsWith(EMP) ? page.substring(EMP.length()) : null;
            if (page.equals("more")) {
                List<String[]> subs = moreSubs();
                boolean known = false;
                for (String[] e : subs) if (e[0].equals(morePage)) known = true;
                if (!known) morePage = subs.isEmpty() ? null : subs.get(0)[0];
                ButtonAPI prev = null, first = null;
                int inRow = 0;
                float bw = (cw - 18f) / 4f;
                for (String[] e : subs) {
                    if (inRow == 4) { CALog.endRow(t, first); prev = null; first = null; inRow = 0; }
                    prev = rowButton(t, prev, e[1], MORE + e[0], e[0].equals(morePage), bw);
                    if (first == null) first = prev;
                    inRow++;
                }
                if (first != null) CALog.endRow(t, first);
                t.addSpacer(12f);
                show = morePage;
            }
            if (pending != null) {
                CATheme.heading(t, "Confirm", message != null ? 8f : 0f);
                try { CAIntel.confirmPrompt(t, pending); }
                catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "confirmation text failed", e); }
                ButtonAPI ok = t.addButton("Confirm", CONFIRM, hl, dark, Alignment.MID, CutStyle.TL_BR, 140f, 24f, 8f);
                ButtonAPI no = t.addButton("Cancel", CANCEL, base, dark, Alignment.MID, CutStyle.TL_BR, 140f, 24f, 0f);
                no.getPosition().rightOfTop(ok, 8f);
                CALog.endRow(t, ok);
                t.addSpacer(12f);
            }
            try { if (show != null) CAIntel.renderEmpire(t, show, cw); }
            catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "empire page " + page + " failed", e);
                t.addPara(CATheme.NOT_SHOWN, gray, 6f);
            }
            content.addUIElement(t).inTL(CANav.GAP, 0f);
            root.addComponent(content).inTL(0f, 0f);
            restoreScroll(t, scrollY);
            return;
        }

        boolean managed = c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO;
        boolean buildout = buildoutRanking(c);   // fast buildout ranks by money only (CABuildout)
        CAData.Profile planWith = planWith(c);

        boolean all = page.equals("colony");   // 0.3.7.3: the four colony sections on one page
        // 0.3.7.6: story mode: the core's status, the approval, the next step, the top build and the mode up top; the rest in
        // sections that open on a click
        boolean story = all;   // 0.3.14.0: both modes use the cards (renderCards) and the sections that open on a click
        if (story) renderCards(t, data, c, s, cw, planWith, buildout, shunt, outpost);
        if (story ? section(t, "numbers", "Details") : (all || page.equals("overview"))) {
            float threat = CAThreat.estimate(market);
            boolean threatened = threat >= CAThreat.THRESHOLD || c.threatHigh;
            float cell = (cw - 30f) / 4f;
            t.beginGrid(cell, 4);
            t.addToGrid(0, 0, "Size", "" + market.getSize());
            t.addToGrid(1, 0, "Stability", "" + (int) market.getStabilityValue(), market.getStabilityValue() < data.settings.minStability ? Misc.getNegativeHighlightColor() : hl);
            t.addToGrid(2, 0, "Hazard", Math.round(market.getHazardValue() * 100f) + "%");
            t.addToGrid(3, 0, "Access", Math.round(market.getAccessibilityMod().computeEffective(0f) * 100f) + "%");
            t.addToGrid(0, 1, "Industries", CABuild.usedSlots(market) + " of " + Misc.getMaxIndustries(market));
            t.addToGrid(1, 1, "Net income", Misc.getDGSCredits(market.getNetIncome()), market.getNetIncome() < 0f ? Misc.getNegativeHighlightColor() : hl);
            t.addToGrid(2, 1, "Threat", Math.round(threat * 100f) + "%", threatened ? Misc.getNegativeHighlightColor() : hl);
            t.addToGrid(3, 1, "Credits", Misc.getDGSCredits(CABuild.credits()));
            t.addGrid(message != null ? 8f : 0f);
            if (managed) {
                t.addPara("Budget: %s of %s spent, %s saved, %s available (fees %s).", 6f, hl, Misc.getDGSCredits(s.spent),
                        Misc.getDGSCredits(CABudget.limit(s)), Misc.getDGSCredits(CABudget.saved(s)), Misc.getDGSCredits(CABudget.available(s)),
                        CASettings.FEE_NAMES[s.feeLevel].toLowerCase());
                if (CABudget.paused(s)) t.addPara(CATheme.PAUSED, Misc.getNegativeHighlightColor(), 4f);   // 0.3.0.0 (WS-F3 fix pass 3): CAIntel's text
            }
            if (!buildout && planWith != c.profile) t.addPara("Security goal until the threat's under %s.", 4f,
                    Misc.getNegativeHighlightColor(), Math.round(CAThreat.CLEAR * 100f) + "%");   // 0.3.8.6: one value for one %s
            String siege = managed ? CASiege.why(market) : null;
            if (siege != null) {   // 0.3.0.0 (WS-F3): the trade contract detail moved to the hover
                t.addPara(SIEGE_LINE, 4f, Misc.getNegativeHighlightColor(), siege, buildout ? "the buildout waits" : "defenses only");
                CATheme.hover(t, SIEGE_HOVER);
            }
            if (CAAutarchAdmin.canOffer(market))   // 0.3.10.2: you took the Autarch AI Core off here; it can come back
                t.addButton("Seat the Autarch AI Core", AUTARCH, hl, Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, 140f, 22f, 4f);
            com.fs.starfarer.api.characters.AdminData ad = CAItems.adminAt(market);
            if (ad != null && ad.getPerson() != null && !ad.getPerson().isAICore()) {
                float salary = Misc.getAdminSalary(ad.getPerson()), worth = CAItems.adminWorth(market, ad.getPerson());
                t.addPara("Administrator %s: salary %s, adds %s monthly.", 4f, worth < salary ? Misc.getNegativeHighlightColor() : hl,
                        ad.getPerson().getNameString(), Misc.getDGSCredits(salary), Misc.getDGSCredits(worth));
            }
            if (c.mode == CAData.Mode.AUTO && s.useAICores && !CAEngine.isOutpost(market)) {
                String none = CAItems.noInspections();
                int pts = CAItems.aiPoints(market), lim = CAItems.coreLimit(s, market, none);
                boolean coming = CAItems.inspected().contains(market);
                String why = coming ? "inspection coming, cores kept" : CAItems.limitWhy(s, market, none, lim);
                t.addPara("AI cores: %s inspection points, %s.", 4f, coming || pts > lim ? Misc.getNegativeHighlightColor() : hl, "" + pts, why);
                String crisis = CACrisis.coreLine(market);
                if (crisis != null) t.addPara(crisis, Misc.getGrayColor(), 2f);   // 0.3.8.6: shown as is
            }
            if (Global.getSettings().getModManager().isModEnabled(CADevelopment.MOD) && !shunt) {
                try { CADevelopment.render(t, market); }
                catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "management style line failed at " + market.getName(), e); }
            }
            Industry imp = CAStory.bestImprovement(market, c);
            if (imp != null) t.addPara("Best story point use: %s" + (s.storyPointRule == 0 ? " (yours)" : ""), 4f, hl, imp.getCurrentName());
        }
        if (!story && (all || page.equals("overview"))) renderApproval(t, c, s);
        if (story ? section(t, "money", "Money and capital") : (all || page.equals("overview"))) {
            CABuildout.render(t, market, c);
            if (c.mode == CAData.Mode.AUTO && !outpost && !shunt) {
                try { CASurplus.renderColony(t, market, s, 12f); }
                catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus section failed at " + market.getName(), e); }
            }
            if (!outpost && !shunt) {
                try { CACapital.renderColony(t, market, s, 12f); }
                catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "capital section failed at " + market.getName(), e); }
            }
            if (outpost) {
                CATheme.heading(t, "Nexerelin outpost", 12f);
                t.addPara(OUTPOST_LINE, gray, 6f);   // 0.3.0.0 (WS-F3): 53 words down to 16
            }
        }
        if (story ? (!outpost && section(t, "plan", "Plan and builds")) : ((all && !outpost) || page.equals("builds"))) {
            CAPlan.Plan plan = CAPlan.build(market, planWith);
            CATheme.heading(t, "Plan: " + plan.name + " (" + plan.doneCount(market) + " of " + plan.steps.size() + " done)", all ? 16f : message != null ? 8f : 0f);
            List<String> doneSteps = new ArrayList<String>();
            boolean nextShown = false;
            for (CAPlan.Step st : plan.steps) {
                if (st.done(market)) { doneSteps.add(st.label); continue; }
                String tag = !nextShown ? "Next" : "Planned";
                nextShown = true;
                String lock = st.kind == CAPlan.Kind.BUILD ? CAAotD.lockReason(st.id, market) : null;
                if (lock == null && CAPlan.held(market, plan, st)) lock = "waits for the rest of the plan";
                t.addPara("%s  " + CALog.esc(st.label + (lock != null ? " (" + (lock.startsWith("waits") ? "" : "locked: ") + lock + ")" : "")), 2f, tag.equals("Next") ? hl : base, tag);
            }
            if (!nextShown) t.addPara("All steps done.", hl, 4f);
            if (!doneSteps.isEmpty()) t.addPara("Done: " + String.join(", ", doneSteps), gray, 4f);   // 0.3.8.6: shown as is

            if (shunt) {
                CATheme.heading(t, "Coronal Hypershunt (AotD)", 12f);
                try { CAHypershunt.renderColony(t, market, c, s); }
                catch (Throwable e) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt section failed at " + market.getName(), e);
                    t.addPara(CATheme.NOT_SHOWN, gray, 6f);
                }
            } else {
                CATheme.heading(t, "Recommended builds", 12f);
                if (c.mode == CAData.Mode.OFF) t.addPara("Autopilot is off here.", gray, 8f);
                else renderPicks(t, data, planWith, buildout);
            }
        }
        if (story ? section(t, "locks", "Locks") : (all || page.equals("locks"))) {
            if (!story) CATheme.heading(t, "Locks", all ? 16f : message != null ? 8f : 0f);
            t.addPara(LOCKS_LINE, gray, 6f);
            ButtonAPI lockPrev = null, lockFirst = null;
            int inRow = 0;
            float lockW = (cw - 12f) / 3f;
            for (Industry ind : market.getIndustries()) {
                if (ind.isHidden()) continue;   // never touched anyway
                boolean locked = c.locks.contains(ind.getId());
                if (inRow == 3) { CALog.endRow(t, lockFirst); lockPrev = null; lockFirst = null; inRow = 0; }
                lockPrev = rowButton(t, lockPrev, (locked ? "Locked: " : "") + ind.getCurrentName(), "ca:lock:" + ind.getId(), locked, lockW);
                if (lockFirst == null) lockFirst = lockPrev;
                inRow++;
            }
            if (lockFirst != null) CALog.endRow(t, lockFirst);
            List<String> odd = CADiagnostics.unsupportedConditions(market);
            if (!odd.isEmpty() && !shunt) t.addPara("Unmapped conditions: " + odd + ". Scores may be off.", gray, 10f);   // 0.3.8.6: shown as is
        }
        if (story ? (!modSections(c, s, shunt).isEmpty() && section(t, "mods", "Mod features")) : (all || page.equals("mods"))) {
            float pad = story ? 6f : all ? 16f : message != null ? 8f : 0f;
            for (String id : modSections(c, s, shunt)) {
                renderMod(t, id, c, s, planWith, pad);
                pad = 16f;
            }
        }

        content.addUIElement(t).inTL(CANav.GAP, 0f);
        root.addComponent(content).inTL(0f, 0f);
        restoreScroll(t, scrollY);
    }

    // ---------------- 0.3.15.1: the one page ----------------

    /** The empire run tiles, the Story page in story mode, then buttons for the rest. */
    void renderMain(TooltipMakerAPI t, CASettings s, float cw) {
        boolean dormant = CASync.on() && CASync.dormant();
        if (!dormant) {
            t.addSectionHeading("How do you want your empire run?", Alignment.MID, message != null ? 8f : 0f);
            CAData.Mode[] run = {CAData.Mode.ADVISOR, CAData.Mode.APPROVE, CAData.Mode.AUTO};
            String[] labels = new String[3], art = new String[3];
            Object[] ids = new Object[3];
            int sel = -1;
            for (int i = 0; i < 3; i++) {
                labels[i] = run[i].label; art[i] = MODE_ART[run[i].ordinal()]; ids[i] = RUN + run[i].name();
                if (run[i] == s.empire()) sel = i;
            }
            tiles(t, cw, labels, art, ids, sel);
            t.addPara(MODE_TEXT[s.empire().ordinal()], Misc.getGrayColor(), 4f);
        }
        if (CASync.on()) {
            try { CAIntel.renderEmpire(t, "story", cw); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story page failed", e); t.addPara(CATheme.NOT_SHOWN, Misc.getGrayColor(), 6f); }
        }
        t.addSpacer(10f);
        List<String[]> b = new ArrayList<String[]>();
        b.add(new String[]{"settings", "Settings"});
        if (!dormant) {
            int pending = 0;
            try { pending = CAApprovals.collect().size(); } catch (Throwable ignored) { }
            b.add(new String[]{"colonies", pending > 0 ? "Colonies (" + pending + ")" : "Colonies"});
            b.add(new String[]{"log", "Log"});
            if (Global.getSettings().getModManager().isModEnabled(CAWar.MOD)) {
                b.add(new String[]{"war", "War"});
                b.add(new String[]{CAFactionsPage.PAGE, "Factions"});
            }
        }
        float bw = (cw - 6f * (b.size() - 1)) / b.size();
        ButtonAPI prev = null, first = null;
        for (String[] e : b) {
            prev = rowButton(t, prev, e[1], CANav.PREFIX + EMP + e[0], false, bw);
            if (first == null) first = prev;
        }
        CALog.endRow(t, first, 8f);
    }

    /** An empire run tile: the empire mode and every colony's mode, with the recommended settings the first time. */
    void runEmpire(CAData.Mode mo) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (!s.setupDone && mo != CAData.Mode.ADVISOR) {
            if (!CALunaSync.keepsSetup()) CASetupUI.applyRecommended(s);
            s.setupDone = true;
        }
        CAModeTable.setEmpire(s, mo);
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (CAEngine.isOutpost(m)) continue;
            d.setMode(d.colony(m.getId()), mo);
            n++;
        }
        CASync.enforce(s);
        CALunaSync.sync();
        message = CASync.modeNote != null ? CASync.modeNote : n + (n == 1 ? " colony is" : " colonies are") + " on " + mo.label + ".";
    }

    /** 0.3.7.6: a story-mode section's toggle row; true when it's open. */
    boolean section(TooltipMakerAPI t, String id, String label) {
        boolean open = openSec.contains(id);
        Color base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        t.addButton((open ? "- " : "+ ") + label, SEC + id, base, dark, Alignment.LMID, CutStyle.TL_BR, 260f, 22f, 10f);
        return open;
    }

    /** The waiting approval with Approve and Reject. */
    void renderApproval(TooltipMakerAPI t, CAData.Colony c, CASettings s) {
        if (c.pendingId == null) return;
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        CATheme.heading(t, CATheme.APPROVAL, 10f);
        t.addPara("%s for %s, fee %s.", 6f, hl, c.pendingName, Misc.getDGSCredits(c.pendingCost),
                Misc.getDGSCredits(CABudget.buildTotal(s, c.pendingCost) - c.pendingCost));   // 0.3.9.6 (F8): with Rapid construction's fee
        if (c.pendingWhy != null) t.addPara(c.pendingWhy, gray, 2f);   // 0.3.3.0
        ButtonAPI a = t.addButton("Approve", APPROVE, hl, dark, Alignment.MID, CutStyle.TL_BR, 140f, 24f, 6f);
        ButtonAPI r = t.addButton("Reject", REJECT, base, dark, Alignment.MID, CutStyle.TL_BR, 140f, 24f, 0f);
        r.getPosition().rightOfTop(a, 6f);
        CALog.endRow(t, a);
    }

    // ---------------- 0.3.14.0: the Colony tab's cards, in both modes ----------------

    /** The art key of each mode's tile (Off has none): the AI core that matches how much it does. */
    static final String[] MODE_ART = {null, "c:gamma_core", "c:beta_core", "c:alpha_core"};

    /** The art key of a goal's tile: an industry that stands for it. */
    static String goalArt(CAData.Profile p) {
        String l = p == null ? "" : p.label;
        if (l.startsWith("Money")) return "i:commerce";
        if (l.startsWith("Security")) return "i:militarybase";
        if (l.startsWith("War")) return "i:orbitalworks";
        if (l.startsWith("Rapid")) return "i:farming";
        if (l.startsWith("Self")) return "i:lightindustry";
        return "i:population";
    }

    /**
     * The top of the Colony tab: the planet's picture with its name, size, mode and goal; the waiting approval; the mode and
     * goal as picture tiles; four numbers; the next build as one card with its button; the empire mode line. The rest of
     * the old page is in the sections below (Details, Money and capital, Plan and builds, Locks, Mod features).
     */
    void renderCards(TooltipMakerAPI t, CAData data, CAData.Colony c, CASettings s, float cw, CAData.Profile planWith,
                     boolean buildout, boolean shunt, boolean outpost) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        float pad = CASyncPage.TILE_PAD, imgW = CASyncPage.IMG_W, imgH = CASyncPage.IMG_H;
        // the header card
        String icon = null;
        try { if (market.getPrimaryEntity() instanceof PlanetAPI) icon = ((PlanetAPI) market.getPrimaryEntity()).getSpec().getIconTexture(); }
        catch (Throwable ignored) { }
        if (icon != null) try { Global.getSettings().loadTexture(icon); } catch (Throwable ignored) { icon = null; }
        CustomPanelAPI hp = Global.getSettings().createCustom(cw, imgH + 2f * pad, new CASyncPage.Tile(icon, true, false));
        UIComponentAPI hc = t.addCustom(hp, message != null ? 8f : 0f);
        t.setParaFont(Fonts.INSIGNIA_LARGE);
        LabelAPI nm = t.addPara(market.getName(), hl, 0f);
        t.setParaFontDefault();
        nm.getPosition().rightOfTop(hc, -(cw - imgW - 2f * pad)).setYAlignOffset(-pad);
        LabelAPI sub = t.addPara("Size " + market.getSize() + ", " + c.mode.label + ", " + c.profile.label
                + ".", gray, 0f);
        sub.getPosition().rightOfTop(hc, -(cw - imgW - 2f * pad)).setYAlignOffset(-(pad + nm.getPosition().getHeight() + 2f));
        CALog.endRow(t, hc, 4f);
        renderApproval(t, c, s);
        // mode and goal tiles
        CAData.Mode[] modes = CAData.Mode.values();
        String[] ml = new String[modes.length];
        for (int i = 0; i < modes.length; i++) ml[i] = modes[i].label;
        tiles(t, cw, ml, MODE_ART, modes, c.mode.ordinal());
        CAData.Profile[] goals = CAData.Profile.values();
        String[] gl = new String[goals.length], ga = new String[goals.length];
        for (int i = 0; i < goals.length; i++) { gl[i] = goals[i].label; ga[i] = goalArt(goals[i]); }
        tiles(t, cw, gl, ga, goals, c.profile.ordinal());
        // four numbers
        float threat = CAThreat.estimate(market);
        boolean managed = c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO;
        String[] sl = {"Stability", "Net income", "Threat", "Budget left"};
        String[] sv = {"" + (int) market.getStabilityValue(), Misc.getDGSCredits(market.getNetIncome()), Math.round(threat * 100f) + "%",
                managed ? Misc.getDGSCredits(CABudget.available(s)) : "None"};
        Color neg = Misc.getNegativeHighlightColor();
        Color[] sc = {market.getStabilityValue() < data.settings.minStability ? neg : hl, market.getNetIncome() < 0f ? neg : hl,
                threat >= CAThreat.THRESHOLD || c.threatHigh ? neg : hl, hl};
        float gap = CASyncPage.TILE_GAP, sw = (cw - gap * 3f) / 4f;
        UIComponentAPI first = null, prev = null;
        for (int i = 0; i < 4; i++) {
            CustomPanelAPI p = Global.getSettings().createCustom(sw, 54f, new CASyncPage.Tile(null, false, false));
            UIComponentAPI comp = t.addCustom(p, first == null ? 8f : 0f);
            if (prev != null) comp.getPosition().rightOfTop(prev, gap);
            if (first == null) first = comp;
            prev = comp;
            LabelAPI l = t.addPara(sl[i], gray, 0f);
            l.getPosition().rightOfTop(comp, -(sw - pad)).setYAlignOffset(-pad + 2f);
            t.setParaFont(Fonts.INSIGNIA_LARGE);
            LabelAPI v = t.addPara(sv[i], sc[i], 0f);
            t.setParaFontDefault();
            v.getPosition().rightOfTop(comp, -(sw - pad)).setYAlignOffset(-(pad + l.getPosition().getHeight()));
        }
        CALog.endRow(t, first, 4f);
        // the next build
        if (!outpost && !shunt && c.mode != CAData.Mode.OFF) nextCard(t, data, c, cw, planWith, buildout);
        empireLine(t, c, s);
        t.addSpacer(4f);
    }

    /** A row of picture tiles, each with its button; the selected one is lit. */
    void tiles(TooltipMakerAPI t, float cw, String[] labels, String[] art, Object[] ids, int sel) {
        int n = labels.length;
        float gap = CASyncPage.TILE_GAP, pad = CASyncPage.TILE_PAD, imgH = CASyncPage.IMG_H, btnH = 22f;
        float w = (cw - gap * (n - 1)) / n, h = pad + imgH + 4f + btnH + pad;
        UIComponentAPI first = null, prev = null;
        for (int i = 0; i < n; i++) {
            CustomPanelAPI p = Global.getSettings().createCustom(w, h, new CASyncPage.Tile(CASyncPage.sprite(art[i]), i == sel, false));
            UIComponentAPI comp = t.addCustom(p, first == null ? 8f : 0f);
            if (prev != null) comp.getPosition().rightOfTop(prev, gap);
            if (first == null) first = comp;
            prev = comp;
            ButtonAPI b = t.addButton(labels[i], ids[i], i == sel ? Misc.getHighlightColor() : Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.TL_BR, w - 2f * pad, btnH, 0f);
            b.getPosition().rightOfTop(comp, -(w - pad)).setYAlignOffset(-(pad + imgH + 4f));
        }
        CALog.endRow(t, first, 4f);
    }

    /** The next build as one card: its picture, what it is and costs, the plan's next step, and its button. */
    void nextCard(TooltipMakerAPI t, CAData data, CAData.Colony c, float cw, CAData.Profile planWith, boolean buildout) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        float pad = CASyncPage.TILE_PAD, imgW = CASyncPage.IMG_W, imgH = CASyncPage.IMG_H, bw = 140f;
        CAEngine.Pick p = null;
        boolean enabled = false;
        try {
            CAEngine.Kept k = CAEngine.kept(market, planWith, data.settings.minStability, buildout);
            if (k != null && !k.result.picks.isEmpty() && CAEngine.worthwhile(k.result.picks.get(0))) {
                p = k.result.picks.get(0);
                boolean waits = !p.upgrade && k.result.blocked != null, poor = CABuild.credits() < p.cost, old = !k.current;
                enabled = !waits && !poor && !old;
            }
        } catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "next build card failed at " + market.getName(), e); }
        String step = null;
        try {
            CAPlan.Plan plan = CAPlan.build(market, planWith);
            for (CAPlan.Step st : plan.steps) if (!st.done(market)) { step = st.label; break; }
        } catch (Throwable ignored) { }
        if (p == null && step == null) return;
        CustomPanelAPI panel = Global.getSettings().createCustom(cw, imgH + 2f * pad, new CASyncPage.Tile(p != null ? CASyncPage.sprite("i:" + p.id) : null, p != null, false));
        UIComponentAPI card = t.addCustom(panel, 8f);
        float textW = cw - imgW - 3f * pad - bw - pad;
        LabelAPI l1 = t.addPara(p != null ? p.verb + " " + p.name + " for " + Misc.getDGSCredits(p.cost) + "." : step, p != null ? hl : gray, 0f);
        l1.autoSizeToWidth(textW);
        l1.getPosition().rightOfTop(card, -(cw - imgW - 2f * pad)).setYAlignOffset(-pad);
        if (step != null && p != null) {
            LabelAPI l2 = t.addPara(step, gray, 0f);   // the plan's next step
            l2.autoSizeToWidth(textW);
            l2.getPosition().rightOfTop(card, -(cw - imgW - 2f * pad)).setYAlignOffset(-(pad + l1.getPosition().getHeight() + 2f));
        }
        if (p != null) {
            ButtonAPI b = t.addButton(p.verb, p, base, dark, Alignment.MID, CutStyle.TL_BR, bw, 24f, 0f);
            b.getPosition().rightOfTop(card, -(bw + pad)).setYAlignOffset(-(pad + (imgH - 24f) / 2f));
            b.setEnabled(enabled);
            CATheme.hover(t, rankLines(p, buildout ? "fast buildout (money only)" : "the " + planWith.label + " goal"));
        }
        CALog.endRow(t, card, 4f);
    }

    /** A colony above the empire mode: one button that raises the empire mode, with the empire mode beside it. */
    void empireLine(TooltipMakerAPI t, CAData.Colony c, CASettings s) {
        CAData.Mode em = s.empire();
        if (c.mode.ordinal() <= em.ordinal()) return;
        ButtonAPI eb = t.addButton("Empire mode to " + c.mode.label, EMPIRE, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.TL_BR, 220f, 22f, 6f);
        LabelAPI el = t.addPara("Empire: " + em.label + ".", Misc.getGrayColor(), 0f);
        el.getPosition().rightOfMid(eb, 10f);
        CALog.endRow(t, eb);
    }

    /** The Recommended builds list: the top three options with what they're worth and a button each. */
    void renderPicks(TooltipMakerAPI t, CAData data, CAData.Profile planWith, boolean buildout) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        // 0.3.0.0 (A4 fix, 07 M6): what's kept for this goal (the daily pass, or Refresh); drawing the panel never evaluates. A
        // result from before the latest change is shown with its Build buttons off until it's scored again
        CAEngine.Kept k = CAEngine.kept(market, planWith, data.settings.minStability, buildout);
        ButtonAPI rb = t.addButton("Refresh", REFRESH, base, dark, Alignment.MID, CutStyle.TL_BR, 140f, 24f, 8f);
        LabelAPI note = t.addPara(k == null ? (CAEngine.scoreFailed(market) ? "Not scored. See starsector.log."   // 0.3.0.0 (A4 fix)
                : "Not scored yet. Refresh.")
                : k.current ? "Scored today." : "Out of date. Refresh.", k != null && k.current ? gray : hl, 0f);
        note.getPosition().rightOfMid(rb, 10f);
        CALog.endRow(t, rb);
        if (k == null) return;
        CAEngine.Result r = k.result;
        if (r.blocked != null) t.addPara(r.blocked + (r.picks.isEmpty() ? "." : ". Upgrades only."), hl, 8f);   // 0.3.8.6: shown as is
        List<CAEngine.Pick> picks = r.picks;
        if (picks.isEmpty()) t.addPara("Nothing to build now.", 8f);
        else if (!CAEngine.worthwhile(picks.get(0))) t.addPara("Nothing pays for itself now.", hl, 8f);   // 0.3.0.0 (WS-F3 fix): shorter
        if (!picks.isEmpty()) t.addPara(PICKS_HOVER_NOTE, gray, 4f);
        String goal = buildout ? "fast buildout (money only)" : "the " + planWith.label + " goal";
        for (int i = 0; i < Math.min(3, picks.size()); i++) {
            CAEngine.Pick p = picks.get(i);
            Color money = p.fx[0] < 0f ? Misc.getNegativeHighlightColor() : hl, score = CAEngine.worthwhile(p) ? hl : Misc.getNegativeHighlightColor();
            t.addPara("%s. %s %s: %s a month, %s, %s days, score %s" + (p.upgrade ? " (runs alongside)" : ""), 12f,
                    new Color[]{hl, hl, hl, money, hl, hl, score}, "" + (i + 1), p.verb, p.name, CAEngine.signed(p.fx[0]), Misc.getDGSCredits(p.cost),
                    "" + (int) p.days, CAEngine.signed(p.score));
            // 0.3.0.0 (WS-F3, research 04 section 2): the score's make-up (30 words under each of three picks in 0.2.45) is hover text
            CATheme.hover(t, rankLines(p, goal));
            if (!CAEngine.worthwhile(p)) t.addPara("    Not worth it: %s-month payback.", 2f,
                    Misc.getNegativeHighlightColor(), "" + months(p));
            int shown = Math.min(3, p.lines.size());
            for (int j = 0; j < shown; j++) t.addPara("    " + p.lines.get(j), gray, 2f);   // 0.3.8.6: shown as is
            if (p.lines.size() > shown) {   // 0.3.0.0 (WS-F3): the rest on hover, here and on the pick's line
                t.addPara("    " + (p.lines.size() - shown) + " more effects on hover.", gray, 2f);
                CATheme.hover(t, p.lines.subList(shown, p.lines.size()).toArray(new String[0]));
            }
            // 0.3.12.1: your click builds in every mode while you have the credits: an out-of-date score no longer turns the button
            // off (the click checks slots, stability and the building itself again), and with construction under way a new
            // building goes into the game's construction queue
            boolean poor = CABuild.credits() < p.cost;
            ButtonAPI b = t.addButton(buildLabel(p, r) + " (" + Misc.getDGSCredits(p.cost) + ")", p, base, dark,
                    Alignment.MID, CutStyle.TL_BR, 240f, 24f, 6f);
            b.setEnabled(!poor);
            if (poor) {
                LabelAPI why = t.addPara("Not enough credits", gray, 0f);
                why.getPosition().rightOfMid(b, 10f);
                CALog.endRow(t, b);
            }
        }
        // 0.3.3.0: every other option, tagged as in the build menu hints (Best, Good, Poor and why), Build on the ones that pay
        if (picks.size() > 3) {
            t.addPara("More", hl, 12f);
            float stab = CAStability.now(market), floor = CAStability.floor();
            for (int i = 3; i < picks.size(); i++) {
                CAEngine.Pick p = picks.get(i);
                String[] tg = CABuildHints.tag(p, i + 1, data.settings, stab, floor);
                boolean ok = CABuild.credits() >= p.cost;   // 0.3.12.1: every option you can pay for, worth it or not (your call)
                if (ok) {
                    ButtonAPI b = t.addButton(buildLabel(p, r) + " (" + Misc.getDGSCredits(p.cost) + ")", p, base, dark, Alignment.MID, CutStyle.TL_BR, 200f, 20f, 4f);
                    LabelAPI l = t.addPara("%s: %s. " + CALog.esc(tg[1]) + ".", 0f, hl, tg[0], p.name);
                    l.getPosition().rightOfMid(b, 10f);
                    CALog.endRow(t, b);
                } else t.addPara("%s: %s. " + CALog.esc(tg[1]) + ".", 4f, "Poor".equals(tg[0]) ? Misc.getNegativeHighlightColor() : gray, tg[0], p.name);
            }
        }
        if (r.noSlot > 0) t.addPara(r.noSlot + " more need an industry slot.", gray, 10f);
        if (r.locked > 0) t.addPara(r.locked + " more need research (AotD).", gray, 6f);
        if (r.unsupportedBuildable > 0) t.addPara(r.unsupportedBuildable + " mod buildings not supported (Diagnostics).", gray, 6f);
    }

    /** 0.3.12.1: a pick's button word: Queue for a new building while construction is under way here, else its own verb. */
    String buildLabel(CAEngine.Pick p, CAEngine.Result r) {
        return !p.upgrade && (r.blocked != null && CABuild.buildsFull(market)) ? "Queue" : p.verb;
    }

    /** Months a pick's price is spread over: its payback horizon (CAHorizon, 0.3.0.0 B2), or the 36 of the legacy planner. Pure. */
    static int months(CAEngine.Pick p) {
        return Math.round(p != null && p.horizon > 0f ? p.horizon : CATuning.PAYBACK_MONTHS);
    }

    /**
     * 0.3.0.0 (WS-F3): a recommended build's hover: what its score is made of (CAEngine.finish / finishNpv: worth plus the plan
     * bonus, minus the price spread over the horizon and any industry slot the plan needs), then the effects past the three
     * shown on the page. goal: "the Balanced goal" or "fast buildout (money only)". Pure apart from credit formatting.
     */
    static String[] rankLines(CAEngine.Pick p, String goal) {
        List<String> out = new ArrayList<String>();
        StringBuilder b = new StringBuilder("Score ").append(CAEngine.signed(p.score)).append(" a month for ").append(goal)
                .append(": worth ").append(CAEngine.signed(p.worth));
        if (p.planBonus > 0f) b.append(", plan +").append(Misc.getDGSCredits(p.planBonus));
        if (p.followThrough > 0f) b.append(", upgrade +").append(Misc.getDGSCredits(p.followThrough));
        b.append(", minus ").append(Misc.getDGSCredits(p.buildCost)).append(p.fee > 0f ? " for the price, fee and build time" : " for the price and build time")
                .append(" over ").append(months(p)).append(" months");
        if (p.reserve > 0f) b.append(", minus ").append(Misc.getDGSCredits(p.reserve)).append(" for an industry slot the plan needs");
        out.add(b.append('.').toString());
        for (int j = 3; j < p.lines.size(); j++) out.add(p.lines.get(j));
        return out.toArray(new String[0]);
    }
}
