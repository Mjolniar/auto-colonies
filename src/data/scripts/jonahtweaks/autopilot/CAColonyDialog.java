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
    public static final float W = 860f, H = 620f;
    static final String APPROVE = "ca:approve", REJECT = "ca:reject", SAVE_SETUP = "ca:savesetup", BACK = "ca:back";
    /** 0.3.3.0: sets the empire mode to this colony's mode. */
    static final String EMPIRE = "ca:empire";
    static final String REFRESH = "ca:refresh";   // 0.3.0.0 (A4 fix): Recommended builds' Refresh (CAEngine.refresh)
    static final String AUG_APPLY = "ca:aug:apply", AUG_UNSKIP = "ca:aug:unskip";
    static final String HS = "ca:hs:";   // Coronal Hypershunt actions: ca:hs:<action>:<arg>
    static final String MEGA = "ca:mega:";   // Pluto / Nidavelir actions: ca:mega:<action>:<arg>
    static final String TASC = "ca:tasc:";   // start a TASC terraforming project: ca:tasc:<project name>
    /** 0.3.0.0 (WS-F3): the message after a button that threw. */
    static final String FAILED = "Failed. See starsector.log";
    final MarketAPI market;
    CustomPanelAPI root, content;
    String message = null;
    boolean dirty = false, setupPage = false;
    CAData.Mode setupMode = null;
    final Plugin plugin = new Plugin();
    TooltipMakerAPI lastTooltip;
    boolean lastSetupPage;
    /** The page shown; Overview each time the panel opens. */
    String page = "overview", lastPage;

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

    void handle(Object id) {
        CAData d = CAData.get();
        CAData.Colony c = d.colony(market.getId());
        if (CASetupUI.handle(id, d.settings)) { dirty = true; return; }
        if (CANav.page(id) != null) { page = CANav.page(id); message = null; dirty = true; return; }
        if (id instanceof CAData.Profile) { c.profile = (CAData.Profile) id; message = "Goal set to " + c.profile.label; }
        else if (id instanceof CAData.Mode) {
            CAData.Mode mo = (CAData.Mode) id;
            boolean needsSetup = (mo == CAData.Mode.APPROVE || mo == CAData.Mode.AUTO) && !d.settings.setupDone;
            if (needsSetup && CALunaSync.keepsSetup()) { d.settings.setupDone = true; needsSetup = false; }   // 0.3.4.0: LunaLib's settings stay
            // 0.3.0.0 (WS-F3): the Settings page opens on Playstyle afterwards (CASetupUI's groups since F1)
            if (needsSetup) {
                setupPage = true; setupMode = mo; CASetupUI.applyRecommended(d.settings); CASetupUI.open.clear(); CASetupUI.open.add("playstyle");
                // 0.3.3.0: the setup rows show the chosen mode's own defaults, so what you see is what Save keeps
                d.settings.empireMode = mo;
                CAModeTable.apply(d.settings, mo);
            }
            else { d.setMode(c, mo); message = "Mode set to " + mo.label; }
        }
        else if (SAVE_SETUP.equals(id)) {
            d.settings.setupDone = true; setupPage = false;
            if (setupMode != null) d.setMode(c, setupMode);
            CAModeTable.setEmpire(d.settings, c.mode);   // 0.3.3.0: the first setup sets the empire mode
            message = "Saved. Mode: " + c.mode.label;
            CALog.did(market.getName() + ": setup saved, mode " + c.mode.label + ".", true);   // 0.3.0.0 (WS-F3): "SETUP / Place: ..." on screen
        }
        else if (BACK.equals(id)) { setupPage = false; message = null; }
        else if (EMPIRE.equals(id)) { CAModeTable.setEmpire(d.settings, c.mode); message = "Empire mode set to " + c.mode.label; }
        else if (REFRESH.equals(id)) {   // 0.3.0.0 (A4 fix, 07 M6): scored on a click, never while the panel draws; no plan tracking
            CAEngine.refresh(market, planWith(c), d.settings.minStability, buildoutRanking(c));
            message = null;
        }
        else if (APPROVE.equals(id)) { String err = CAAuto.approve(market); message = err == null ? "Approved and started" : err; }
        else if (REJECT.equals(id)) { CAAuto.reject(market); message = "Rejected for 30 days"; }
        else if (CASurplus.handle(market, id) != null) message = CASurplus.handle(market, id);
        else if (CACapital.handle(market, id) != null) message = CACapital.handle(market, id);
        else if ((AUG_APPLY.equals(id) || AUG_UNSKIP.equals(id)) && Global.getSettings().getModManager().isModEnabled(CAAugments.MOD)) {
            try {
                if (AUG_UNSKIP.equals(id)) { CAAugments.unskip(market); message = "Augments allowed again"; }
                else { String err = CAAugments.applyNow(market); message = err == null ? "Augments updated" : err; }
            } catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment action failed at " + market.getName(), e);
                message = FAILED;
            }
        }
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
            float fee = CABudget.constructionFee(d.settings, p.cost);
            String err = CAApprovals.startByPlayer(market, p);
            message = err == null ? "Started: " + p.verb + " " + p.name + " for " + Misc.getDGSCredits(p.cost) + " plus " + Misc.getDGSCredits(fee) + " fee" : err;
        }
        dirty = true;
    }

    static boolean buildoutRanking(CAData.Colony c) { return c.buildout && c.mode == CAData.Mode.AUTO; }

    /** The goal the plan and Recommended builds use: fast buildout's Money, Security while an Approve or Auto colony is threatened, else its own (0.3.0.0: one place, so Refresh scores what's shown). */
    CAData.Profile planWith(CAData.Colony c) {
        boolean managed = c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO;
        return buildoutRanking(c) ? CAData.Profile.INCOME : managed ? CAThreat.effective(market, c.profile) : c.profile;
    }

    // 0.3.0.0 (WS-F3 fix pass 2): every text on this panel shorter, for the mod-wide word budget (JTScreenButtonCheck)
    static final String[] MODE_TEXT = {
            "Off: does nothing.",
            "Advisor: ranks builds. You build.",
            "Approve: proposes. You approve.",
            "Auto: builds and sets policies within budget."};

    // ---------------- text (0.3.0.0, WS-F3: each paragraph 40 words or fewer, JTScreenButtonCheck) ----------------

    /** The first-time setup's line above its rows. 0.3.0.0 fix pass 3: shorter (mod-wide word budget, JTScreenButtonCheck). */
    static final String SETUP_INTRO = "Recommended values for your income.";
    /** Under siege: the line on Overview, and its hover. */
    static final String SIEGE_LINE = "Under siege (%s): %s.",
            SIEGE_HOVER = "Trade contracts skip its goods.";
    /** A Nexerelin outpost's Overview line (CAEngine.OUTPOST_TEXT in short). */
    static final String OUTPOST_LINE = "Resupply base. Autopilot leaves it alone.";
    static final String LOCKS_LINE = "Click to lock. Autopilot leaves locked buildings alone.";
    static final String PICKS_HOVER_NOTE = "Hover for the score.";

    static ButtonAPI rowButton(TooltipMakerAPI t, ButtonAPI prev, String label, Object id, boolean on, float w) {
        Color hl = Misc.getHighlightColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        ButtonAPI b = t.addButton(label, id, on ? hl : base, dark, Alignment.MID, CutStyle.TL_BR, w, 24f, prev == null ? 8f : 0f);
        if (prev != null) b.getPosition().rightOfTop(prev, 6f);
        return b;
    }

    /** The pages in the list on the left: Overview, Plan and builds, Locks, plus Mod features when one applies here. */
    List<String[]> pages(CAData.Colony c, CASettings s, boolean shunt, boolean outpost) {
        List<String[]> p = new ArrayList<String[]>();
        p.add(new String[]{"overview", c.pendingId != null ? "Overview (approval)" : "Overview"});
        if (!outpost) p.add(new String[]{"builds", shunt ? "Coronal Hypershunt" : "Plan and builds"});
        p.add(new String[]{"locks", "Locks"});
        if (!modSections(c, s, shunt).isEmpty()) p.add(new String[]{"mods", "Mod features"});
        return p;
    }

    /** The mod features that apply to this colony, in the order the Mod features page shows them. */
    List<String> modSections(CAData.Colony c, CASettings s, boolean shunt) {
        List<String> l = new ArrayList<String>();
        if (c.mode == CAData.Mode.OFF) return l;
        com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
        if (CATasc.enabled() && !shunt) l.add("tasc");
        if (mm.isModEnabled(CAHypershunt.MOD) && CAMegastructures.has(market)) l.add("mega");
        if (mm.isModEnabled(CAAugments.MOD) && s.augmentMode != 2 && !shunt) l.add("augments");
        if (mm.isModEnabled(CASynergies.MOD) && !shunt) l.add("synergies");
        if (mm.isModEnabled(CARogue.MOD) && CARogue.any(market)) l.add("rogue");   // 0.3.2.1: Rogue Planets' conditions here
        return l;
    }

    /** One section of the Mod features page; a section that fails leaves the others on the page. */
    void renderMod(TooltipMakerAPI t, String id, CAData.Colony c, CASettings s, CAData.Profile planWith, float pad) {
        com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
        String title = id.equals("tasc") ? "Terraforming (TASC)" : id.equals("mega") ? "Megastructure (AotD)"
                : id.equals("augments") ? "Station augments" : id.equals("rogue") ? "Planet conditions (Rogue Planets)" : "Industry synergies (AotD)";
        CATheme.heading(t, title, pad);
        try {
            if (id.equals("tasc")) CATasc.renderColony(t, market, c);
            else if (id.equals("mega") && mm.isModEnabled(CAHypershunt.MOD)) CAMegastructures.renderColony(t, market, c, s);
            else if (id.equals("augments") && mm.isModEnabled(CAAugments.MOD)) CAAugments.renderColony(t, market, c, s, planWith);
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
        plugin.navW = CANav.W;
        TooltipMakerAPI nav = content.createUIElement(CANav.W, H, false);
        nav.addTitle(market.getName().toUpperCase(java.util.Locale.ROOT));
        CATheme.readout(nav, 2f, "Mode: " + (c.mode == null ? CAData.Mode.ADVISOR.label : c.mode.label), c.profile == null ? null : "Goal: " + c.profile.label);
        CANav.render(nav, pages, page);
        content.addUIElement(nav).inTL(0f, 0f);

        float cw = W - CANav.W - CANav.GAP;
        TooltipMakerAPI t = content.createUIElement(cw, H, true);
        if (message != null) t.addPara(CALog.esc(message), hl, 0f);

        boolean managed = c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO;
        boolean buildout = buildoutRanking(c);   // fast buildout ranks by money only (CABuildout)
        CAData.Profile planWith = planWith(c);

        if (page.equals("overview")) {
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
            if (!buildout && planWith != c.profile) t.addPara("Security goal until threat is under %s.", 4f,
                    Misc.getNegativeHighlightColor(), Math.round(CAThreat.CLEAR * 100f) + "%", "" + Math.round(CAThreat.MIN_DAYS));
            String siege = managed ? CASiege.why(market) : null;
            if (siege != null) {   // 0.3.0.0 (WS-F3): the trade contract detail moved to the hover
                t.addPara(SIEGE_LINE, 4f, Misc.getNegativeHighlightColor(), siege, buildout ? "the buildout waits" : "defenses only");
                CATheme.hover(t, SIEGE_HOVER);
            }
            com.fs.starfarer.api.characters.AdminData ad = CAItems.adminAt(market);
            if (ad != null && ad.getPerson() != null && !ad.getPerson().isAICore()) {
                float salary = Misc.getAdminSalary(ad.getPerson()), worth = CAItems.adminWorth(market, ad.getPerson());
                t.addPara("Administrator %s: salary %s, adds %s a month.", 4f, worth < salary ? Misc.getNegativeHighlightColor() : hl,
                        ad.getPerson().getNameString(), Misc.getDGSCredits(salary), Misc.getDGSCredits(worth));
            }
            if (c.mode == CAData.Mode.AUTO && s.useAICores && !CAEngine.isOutpost(market)) {
                String none = CAItems.noInspections();
                int pts = CAItems.aiPoints(market), lim = CAItems.coreLimit(s, market, none);
                boolean coming = CAItems.inspected().contains(market);
                String why = coming ? "inspection coming, cores kept" : CAItems.limitWhy(s, market, none, lim);
                t.addPara("AI cores: %s inspection points, %s.", 4f, coming || pts > lim ? Misc.getNegativeHighlightColor() : hl, "" + pts, why);
                String crisis = CACrisis.coreLine(market);
                if (crisis != null) t.addPara(CALog.esc(crisis), Misc.getGrayColor(), 2f);
            }
            if (Global.getSettings().getModManager().isModEnabled(CADevelopment.MOD) && !shunt) {
                try { CADevelopment.render(t, market); }
                catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "management style line failed at " + market.getName(), e); }
            }
            Industry imp = CAStory.bestImprovement(market, c);
            if (imp != null) t.addPara("Best story point use: %s" + (s.storyPointRule == 0 ? " (yours)" : ""), 4f, hl, imp.getCurrentName());

            if (c.pendingId != null) {
                CATheme.heading(t, CATheme.APPROVAL, 10f);
                t.addPara("%s for %s, fee %s.", 6f, hl, c.pendingName, Misc.getDGSCredits(c.pendingCost),
                        Misc.getDGSCredits(CABudget.constructionFee(s, c.pendingCost)));
                if (c.pendingWhy != null) t.addPara(CALog.esc(c.pendingWhy), gray, 2f);   // 0.3.3.0
                ButtonAPI a = t.addButton("Approve", APPROVE, hl, dark, Alignment.MID, CutStyle.TL_BR, 140f, 24f, 6f);
                ButtonAPI r = t.addButton("Reject", REJECT, base, dark, Alignment.MID, CutStyle.TL_BR, 140f, 24f, 0f);
                r.getPosition().rightOfTop(a, 6f);
                CALog.endRow(t, a);
            }

            CATheme.heading(t, "Goal", 12f);
            CAData.Profile[] goals = CAData.Profile.values();
            float goalW = (cw - 12f) / 3f;
            ButtonAPI prev = null, first = null;
            for (int i = 0; i < goals.length; i++) {
                if (i == 3) { CALog.endRow(t, first); prev = null; first = null; }   // two rows of three
                prev = rowButton(t, prev, goals[i].label, goals[i], goals[i] == c.profile, goalW);
                if (first == null) first = prev;
            }
            CALog.endRow(t, first);
            CATheme.heading(t, "Mode", 12f);
            prev = null; first = null;
            for (CAData.Mode mo : CAData.Mode.values()) { prev = rowButton(t, prev, mo.label, mo, mo == c.mode, (cw - 18f) / 4f); if (first == null) first = prev; }
            CALog.endRow(t, first);
            t.addPara(MODE_TEXT[c.mode.ordinal()], gray, 6f);
            // 0.3.3.0: a colony above the empire mode: one line and a button, no pop-up
            CAData.Mode em = s.empire();
            if (c.mode.ordinal() > em.ordinal()) {
                ButtonAPI eb = t.addButton("Empire mode to " + c.mode.label, EMPIRE, base, dark, Alignment.MID, CutStyle.TL_BR, 220f, 22f, 6f);
                LabelAPI el = t.addPara(CALog.esc("Empire: " + em.label + "."), gray, 0f);
                el.getPosition().rightOfMid(eb, 10f);
                CALog.endRow(t, eb);
            }
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
        } else if (page.equals("builds")) {
            CAPlan.Plan plan = CAPlan.build(market, planWith);
            CATheme.heading(t, "Plan: " + plan.name + " (" + plan.doneCount(market) + " of " + plan.steps.size() + " done)", message != null ? 8f : 0f);
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
            if (!doneSteps.isEmpty()) t.addPara(CALog.esc("Done: " + String.join(", ", doneSteps)), gray, 4f);

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
        } else if (page.equals("locks")) {
            CATheme.heading(t, "Locks", message != null ? 8f : 0f);
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
            if (!odd.isEmpty() && !shunt) t.addPara(CALog.esc("Unmapped conditions: " + odd + ". Scores may be off."), gray, 10f);
        } else if (page.equals("mods")) {
            float pad = message != null ? 8f : 0f;
            for (String id : modSections(c, s, shunt)) {
                renderMod(t, id, c, s, planWith, pad);
                pad = 16f;
            }
        }

        content.addUIElement(t).inTL(CANav.W + CANav.GAP, 0f);
        root.addComponent(content).inTL(0f, 0f);
        restoreScroll(t, scrollY);
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
        if (r.blocked != null) t.addPara(CALog.esc(r.blocked) + (r.picks.isEmpty() ? "." : ". Upgrades only."), hl, 8f);
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
            if (!CAEngine.worthwhile(p)) t.addPara("    Not worth it. Payback over %s months.", 2f,
                    Misc.getNegativeHighlightColor(), "" + months(p));
            int shown = Math.min(3, p.lines.size());
            for (int j = 0; j < shown; j++) t.addPara("    " + CALog.esc(p.lines.get(j)), gray, 2f);
            if (p.lines.size() > shown) {   // 0.3.0.0 (WS-F3): the rest on hover, here and on the pick's line
                t.addPara("    " + (p.lines.size() - shown) + " more effects on hover.", gray, 2f);
                CATheme.hover(t, p.lines.subList(shown, p.lines.size()).toArray(new String[0]));
            }
            boolean waits = !p.upgrade && r.blocked != null, poor = CABuild.credits() < p.cost, old = !k.current;
            ButtonAPI b = t.addButton(p.verb + " (" + Misc.getDGSCredits(p.cost) + ")", p, base, dark,
                    Alignment.MID, CutStyle.TL_BR, 240f, 24f, 6f);
            b.setEnabled(!waits && !poor && !old);
            if (waits || poor || old) {
                LabelAPI why = t.addPara(old ? "Out of date: Refresh" : waits ? "Waits for construction" : "Not enough credits", gray, 0f);
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
                boolean ok = CAEngine.worthwhile(p) && !(!p.upgrade && r.blocked != null) && k.current;
                if (ok) {
                    ButtonAPI b = t.addButton(p.verb + " (" + Misc.getDGSCredits(p.cost) + ")", p, base, dark, Alignment.MID, CutStyle.TL_BR, 200f, 20f, 4f);
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
