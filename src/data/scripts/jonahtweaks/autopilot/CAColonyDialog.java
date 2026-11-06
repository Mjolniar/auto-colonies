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
 */
public class CAColonyDialog extends BaseCustomDialogDelegate {
    public static final float W = 860f, H = 620f;
    static final String APPROVE = "ca:approve", REJECT = "ca:reject", SAVE_SETUP = "ca:savesetup", BACK = "ca:back";
    static final String AUG_APPLY = "ca:aug:apply", AUG_UNSKIP = "ca:aug:unskip";
    static final String HS = "ca:hs:";   // Coronal Hypershunt actions: ca:hs:<action>:<arg>
    static final String MEGA = "ca:mega:";   // Pluto / Nidavelir actions: ca:mega:<action>:<arg>
    static final String TASC = "ca:tasc:";   // start a TASC terraforming project: ca:tasc:<project name>
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

    class Plugin extends BaseCustomUIPanelPlugin {
        @Override public void buttonPressed(Object id) {
            try { handle(id); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony panel button failed", t); message = "That didn't work; see starsector.log"; dirty = true; }
        }
        @Override public void advance(float amount) { if (dirty) { dirty = false; safeBuild(); } }
    }

    /** Builds the panel; an error shows a line instead of reaching the game's UI (2.37.0). */
    void safeBuild() {
        try { build(); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony panel failed to draw", t); }
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
        if (id instanceof CAData.Profile) { c.profile = (CAData.Profile) id; message = "Profile set to " + c.profile.label; }
        else if (id instanceof CAData.Mode) {
            CAData.Mode mo = (CAData.Mode) id;
            boolean needsSetup = (mo == CAData.Mode.APPROVE || mo == CAData.Mode.AUTO) && !d.settings.setupDone;
            if (needsSetup) { setupPage = true; setupMode = mo; CASetupUI.applyRecommended(d.settings); CASetupUI.open.clear(); CASetupUI.open.add("budget"); }
            else { c.mode = mo; message = "Mode set to " + mo.label; }
        }
        else if (SAVE_SETUP.equals(id)) {
            d.settings.setupDone = true; setupPage = false;
            if (setupMode != null) c.mode = setupMode;
            message = "Settings saved; mode set to " + c.mode.label;
            CALog.add("Setup saved; " + market.getName() + " switched to " + c.mode.label, true);
        }
        else if (BACK.equals(id)) { setupPage = false; message = null; }
        else if (APPROVE.equals(id)) { String err = CAAuto.approve(market); message = err == null ? "Approved and started" : err; }
        else if (REJECT.equals(id)) { CAAuto.reject(market); message = "Rejected; it won't propose again for 30 days"; }
        else if (CASurplus.handle(market, id) != null) message = CASurplus.handle(market, id);
        else if ((AUG_APPLY.equals(id) || AUG_UNSKIP.equals(id)) && Global.getSettings().getModManager().isModEnabled(CAAugments.MOD)) {
            try {
                if (AUG_UNSKIP.equals(id)) { CAAugments.unskip(market); message = "Autopilot may add those augments again"; }
                else { String err = CAAugments.applyNow(market); message = err == null ? "Station augments updated" : err; }
            } catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment action failed at " + market.getName(), e);
                message = "That failed; see starsector.log";
            }
        }
        else if (id instanceof String && ((String) id).startsWith(HS)) {
            String[] a = ((String) id).substring(HS.length()).split(":", 2);
            try { message = CAHypershunt.handle(market, a[0], a.length > 1 ? a[1] : ""); }
            catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt action failed at " + market.getName(), e);
                message = "That failed; see starsector.log";
            }
        }
        else if (id instanceof String && ((String) id).startsWith(CABuildout.PREFIX)) {
            message = CABuildout.handle(market, ((String) id).substring(CABuildout.PREFIX.length()));
        }
        else if (id instanceof String && ((String) id).startsWith(TASC)) {
            String err = CATasc.handle(market, ((String) id).substring(TASC.length()));
            message = err == null ? "Terraforming project started" : err;
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
            String err = CABuild.start(market, p);
            message = err == null ? "Started: " + p.verb + " " + p.name + " for " + Misc.getDGSCredits(p.cost) : err;
            if (err == null) CALog.add(market.getName() + ": you started " + p.verb.toLowerCase() + " " + p.name);
        }
        dirty = true;
    }

    static final String[] MODE_TEXT = {
            "Off: Autopilot does nothing at this colony.",
            "Advisor: recommends builds below; you click Build.",
            "Approve: proposes one project at a time; you approve or reject it.",
            "Auto: builds, places items and runs policies within your budget."};

    static ButtonAPI rowButton(TooltipMakerAPI t, ButtonAPI prev, String label, Object id, boolean on, float w) {
        Color hl = Misc.getHighlightColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        ButtonAPI b = t.addButton(label, id, on ? hl : base, dark, Alignment.MID, CutStyle.ALL, w, 24f, prev == null ? 8f : 0f);
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
        return l;
    }

    /** One section of the Mod features page; a section that fails leaves the others on the page. */
    void renderMod(TooltipMakerAPI t, String id, CAData.Colony c, CASettings s, CAData.Profile planWith, float pad) {
        com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
        String title = id.equals("tasc") ? "Terraforming (Terraforming & Station Construction)" : id.equals("mega") ? "Megastructure (Ashes of the Domain)"
                : id.equals("augments") ? "Station augments" : "Industry synergies (Ashes of the Domain)";
        t.addSectionHeading(title, Alignment.MID, pad);
        try {
            if (id.equals("tasc")) CATasc.renderColony(t, market, c);
            else if (id.equals("mega") && mm.isModEnabled(CAHypershunt.MOD)) CAMegastructures.renderColony(t, market, c, s);
            else if (id.equals("augments") && mm.isModEnabled(CAAugments.MOD)) CAAugments.renderColony(t, market, c, s, planWith);
            else if (id.equals("synergies") && mm.isModEnabled(CASynergies.MOD)) CASynergies.render(t, market);
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + id + " section failed at " + market.getName(), e);
            t.addPara("This section couldn't be shown; see starsector.log.", Misc.getGrayColor(), 6f);
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
            TooltipMakerAPI t = content.createUIElement(W, H, true);
            t.addTitle("Colony Autopilot: first-time setup");
            t.addPara("Every setting below is pre-filled with a recommendation from your current income. Click a group to open it. "
                    + "All of them can be changed later in the Intel screen, under its Colony Autopilot filter.", 6f);
            t.addSectionHeading("Settings", Alignment.MID, 10f);
            CASetupUI.render(t, s, W);
            ButtonAPI save = t.addButton("Save and switch to " + (setupMode == null ? "Auto" : setupMode.label), SAVE_SETUP, hl, dark,
                    Alignment.MID, CutStyle.ALL, 260f, 26f, 14f);
            ButtonAPI back = t.addButton("Back", BACK, base, dark, Alignment.MID, CutStyle.ALL, 120f, 26f, 0f);
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
        TooltipMakerAPI nav = content.createUIElement(CANav.W, H, false);
        nav.addTitle(market.getName());
        nav.addPara("Colony Autopilot", gray, 2f);
        CANav.render(nav, pages, page);
        content.addUIElement(nav).inTL(0f, 0f);

        float cw = W - CANav.W - CANav.GAP;
        TooltipMakerAPI t = content.createUIElement(cw, H, true);
        if (message != null) t.addPara(CALog.esc(message), hl, 0f);

        boolean managed = c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO;
        boolean buildout = c.buildout && c.mode == CAData.Mode.AUTO;   // fast buildout ranks by money only (CABuildout)
        CAData.Profile planWith = buildout ? CAData.Profile.INCOME : managed ? CAThreat.effective(market, c.profile) : c.profile;

        if (page.equals("overview")) {
            float threat = CAThreat.estimate(market);
            boolean threatened = threat >= CAThreat.THRESHOLD || c.threatHigh;
            float cell = (cw - 30f) / 4f;
            t.beginGrid(cell, 4);
            t.addToGrid(0, 0, "Size", "" + market.getSize());
            t.addToGrid(1, 0, "Stability", "" + (int) market.getStabilityValue(), market.getStabilityValue() < data.minStability ? Misc.getNegativeHighlightColor() : hl);
            t.addToGrid(2, 0, "Hazard", Math.round(market.getHazardValue() * 100f) + "%");
            t.addToGrid(3, 0, "Access", Math.round(market.getAccessibilityMod().computeEffective(0f) * 100f) + "%");
            t.addToGrid(0, 1, "Industries", Misc.getNumIndustries(market) + " of " + Misc.getMaxIndustries(market));
            t.addToGrid(1, 1, "Net income", Misc.getDGSCredits(market.getNetIncome()), market.getNetIncome() < 0f ? Misc.getNegativeHighlightColor() : hl);
            t.addToGrid(2, 1, "Threat", Math.round(threat * 100f) + "%", threatened ? Misc.getNegativeHighlightColor() : hl);
            t.addToGrid(3, 1, "Credits", Misc.getDGSCredits(CABuild.credits()));
            t.addGrid(message != null ? 8f : 0f);
            if (managed) {
                t.addPara("Budget this month: %s of %s spent, %s saved up, %s available (fees %s).", 6f, hl, Misc.getDGSCredits(s.spent),
                        Misc.getDGSCredits(CABudget.limit(s)), Misc.getDGSCredits(CABudget.saved(s)), Misc.getDGSCredits(CABudget.available(s)),
                        CASettings.FEE_NAMES[s.feeLevel].toLowerCase());
                if (CABudget.paused(s)) t.addPara("Spending is paused: net income is negative.", Misc.getNegativeHighlightColor(), 4f);
            }
            if (!buildout && planWith != c.profile) t.addPara("Planning with Security until the threat drops below %s and has been high for %s days.", 4f,
                    Misc.getNegativeHighlightColor(), Math.round(CAThreat.CLEAR * 100f) + "%", "" + Math.round(CAThreat.MIN_DAYS));
            String siege = managed ? CASiege.why(market) : null;
            if (siege != null) t.addPara("Under siege (%s): " + (buildout ? "the buildout waits" : "only defenses are built")
                    + "; items, AI cores, policies and station augments are left as they are, and trade contracts don't count on its goods.", 4f,
                    Misc.getNegativeHighlightColor(), siege);
            com.fs.starfarer.api.characters.AdminData ad = CAItems.adminAt(market);
            if (ad != null && ad.getPerson() != null && !ad.getPerson().isAICore()) {
                float salary = Misc.getAdminSalary(ad.getPerson()), worth = CAItems.adminWorth(market, ad.getPerson());
                t.addPara("Administrator %s: salary %s a month, adds about %s a month here.", 4f, worth < salary ? Misc.getNegativeHighlightColor() : hl,
                        ad.getPerson().getNameString(), Misc.getDGSCredits(salary), Misc.getDGSCredits(worth));
            }
            if (c.mode == CAData.Mode.AUTO && s.useAICores && !CAEngine.isOutpost(market)) {
                String none = CAItems.noInspections();
                int pts = CAItems.aiPoints(market), lim = CAItems.coreLimit(s, market, none);
                boolean coming = CAItems.inspected().contains(market);
                String why = coming ? "an inspection is coming, so its cores are left as they are" : CAItems.limitWhy(s, market, none, lim);
                t.addPara("AI cores: %s inspection points, %s.", 4f, coming || pts > lim ? Misc.getNegativeHighlightColor() : hl, "" + pts, why);
                String crisis = CACrisis.coreLine(market);
                if (crisis != null) t.addPara(CALog.esc(crisis), Misc.getGrayColor(), 2f);
            }
            if (Global.getSettings().getModManager().isModEnabled(CADevelopment.MOD) && !shunt) {
                try { CADevelopment.render(t, market); }
                catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "management style line failed at " + market.getName(), e); }
            }
            Industry imp = CAStory.bestImprovement(market, c);
            if (imp != null) t.addPara("Best story point improvement: %s" + (s.storyPointRule == 0 ? " (yours to do: story points are set to Player only)" : ""), 4f, hl, imp.getCurrentName());

            if (c.pendingId != null) {
                t.addSectionHeading("Waiting for your approval", Alignment.MID, 10f);
                t.addPara("%s for %s (plus a %s fee)", 6f, hl, c.pendingName, Misc.getDGSCredits(c.pendingCost),
                        Misc.getDGSCredits(CABudget.constructionFee(s, c.pendingCost)));
                ButtonAPI a = t.addButton("Approve", APPROVE, hl, dark, Alignment.MID, CutStyle.ALL, 140f, 24f, 6f);
                ButtonAPI r = t.addButton("Reject", REJECT, base, dark, Alignment.MID, CutStyle.ALL, 140f, 24f, 0f);
                r.getPosition().rightOfTop(a, 6f);
                CALog.endRow(t, a);
            }

            t.addSectionHeading("Goal", Alignment.MID, 12f);
            CAData.Profile[] goals = CAData.Profile.values();
            float goalW = (cw - 12f) / 3f;
            ButtonAPI prev = null, first = null;
            for (int i = 0; i < goals.length; i++) {
                if (i == 3) { CALog.endRow(t, first); prev = null; first = null; }   // two rows of three
                prev = rowButton(t, prev, goals[i].label, goals[i], goals[i] == c.profile, goalW);
                if (first == null) first = prev;
            }
            CALog.endRow(t, first);
            t.addSectionHeading("Mode", Alignment.MID, 12f);
            prev = null; first = null;
            for (CAData.Mode mo : CAData.Mode.values()) { prev = rowButton(t, prev, mo.label, mo, mo == c.mode, (cw - 18f) / 4f); if (first == null) first = prev; }
            CALog.endRow(t, first);
            t.addPara(MODE_TEXT[c.mode.ordinal()], gray, 6f);
            CABuildout.render(t, market, c);
            if (c.mode == CAData.Mode.AUTO && !outpost && !shunt) {
                try { CASurplus.renderColony(t, market, s, 12f); }
                catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus section failed at " + market.getName(), e); }
            }
            if (outpost) {
                t.addSectionHeading("Nexerelin outpost", Alignment.MID, 12f);
                t.addPara(CAEngine.OUTPOST_TEXT + ". Items, cores, policies and fast buildout skip it too, and its shortages don't count as your colonies'.", gray, 6f);
            }
        } else if (page.equals("builds")) {
            CAPlan.Plan plan = CAPlan.build(market, planWith);
            t.addSectionHeading("Plan: " + plan.name + " (" + plan.doneCount(market) + " of " + plan.steps.size() + " done)", Alignment.MID, message != null ? 8f : 0f);
            List<String> doneSteps = new ArrayList<String>();
            boolean nextShown = false;
            for (CAPlan.Step st : plan.steps) {
                if (st.done(market)) { doneSteps.add(st.label); continue; }
                String tag = !nextShown ? "Next" : "Planned";
                nextShown = true;
                String lock = st.kind == CAPlan.Kind.BUILD ? CAAotD.lockReason(st.id, market) : null;
                if (lock == null && CAPlan.held(market, plan, st)) lock = "waits until the rest of the plan is ready";
                t.addPara("%s  " + CALog.esc(st.label + (lock != null ? " (" + (lock.startsWith("waits") ? "" : "locked: ") + lock + ")" : "")), 2f, tag.equals("Next") ? hl : base, tag);
            }
            if (!nextShown) t.addPara("Every step is done.", hl, 4f);
            if (!doneSteps.isEmpty()) t.addPara(CALog.esc("Done: " + String.join(", ", doneSteps)), gray, 4f);

            if (shunt) {
                t.addSectionHeading("Coronal Hypershunt (Ashes of the Domain)", Alignment.MID, 12f);
                try { CAHypershunt.renderColony(t, market, c, s); }
                catch (Throwable e) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt section failed at " + market.getName(), e);
                    t.addPara("Hypershunt info unavailable; see starsector.log.", gray, 6f);
                }
            } else {
                t.addSectionHeading("Recommended builds", Alignment.MID, 12f);
                if (c.mode == CAData.Mode.OFF) t.addPara("Autopilot is off for this colony.", gray, 8f);
                else renderPicks(t, data, planWith, buildout);
            }
        } else if (page.equals("locks")) {
            t.addSectionHeading("Locks", Alignment.MID, message != null ? 8f : 0f);
            t.addPara("Click a building to lock it (highlighted): Autopilot never upgrades, replaces or removes a locked building.", gray, 6f);
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
            if (!odd.isEmpty() && !shunt) t.addPara(CALog.esc("Unmapped conditions on this colony (scores may be off): " + odd), gray, 10f);
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
        CAEngine.Result r = buildout ? CAEngine.evaluateForProfit(market, data.minStability) : CAEngine.evaluate(market, planWith, data.minStability);
        if (r.blocked != null) t.addPara(CALog.esc(r.blocked) + (r.picks.isEmpty() ? "." : "; new buildings wait for it, but upgrades can start now."), hl, 8f);
        List<CAEngine.Pick> picks = r.picks;
        if (picks.isEmpty()) t.addPara("Nothing can be built here right now: every option is already built, blocked by the game, or has no free industry slot.", 8f);
        else if (!CAEngine.worthwhile(picks.get(0))) t.addPara("Nothing here pays for itself right now, so Approve and Auto wait. The best options are listed anyway.", hl, 8f);
        for (int i = 0; i < Math.min(3, picks.size()); i++) {
            CAEngine.Pick p = picks.get(i);
            Color money = p.fx[0] < 0f ? Misc.getNegativeHighlightColor() : hl;
            t.addPara("%s. %s %s: %s a month after upkeep, costs %s, %s days" + (p.upgrade ? " (runs alongside other construction)" : ""), 12f,
                    new Color[]{hl, hl, hl, money, hl, hl}, "" + (i + 1), p.verb, p.name, CAEngine.signed(p.fx[0]), Misc.getDGSCredits(p.cost), "" + (int) p.days);
            t.addPara("    Ranked %s: money and other effects worth %s for " + CALog.esc(buildout ? "fast buildout (money only)" : "the " + planWith.label + " goal")
                            + (p.planBonus > 0f ? ", plan step +%s" : "%s") + (p.followThrough > 0f ? ", upgrade +%s" : "%s")
                            + ", minus %s a month for the build cost over 3 years", 2f, gray,
                    CAEngine.signed(p.score), CAEngine.signed(p.worth), p.planBonus > 0f ? Misc.getDGSCredits(p.planBonus) : "",
                    p.followThrough > 0f ? Misc.getDGSCredits(p.followThrough) : "", Misc.getDGSCredits(p.buildCost));
            if (!CAEngine.worthwhile(p)) t.addPara("    Not worth building now: it doesn't pay for itself within 3 years. Approve and Auto skip it.", Misc.getNegativeHighlightColor(), 2f);
            int shown = Math.min(3, p.lines.size());
            for (int j = 0; j < shown; j++) t.addPara("    " + CALog.esc(p.lines.get(j)), gray, 2f);
            if (p.lines.size() > shown) t.addPara("    and " + (p.lines.size() - shown) + " more effects", gray, 2f);
            boolean waits = !p.upgrade && r.blocked != null, poor = CABuild.credits() < p.cost;
            ButtonAPI b = t.addButton(p.verb + " (" + Misc.getDGSCredits(p.cost) + ")", p, base, dark,
                    Alignment.MID, CutStyle.ALL, 240f, 24f, 6f);
            b.setEnabled(!waits && !poor);
            if (waits || poor) {
                LabelAPI why = t.addPara(waits ? "Waits for the current construction" : "Not enough credits", gray, 0f);
                why.getPosition().rightOfMid(b, 10f);
                CALog.endRow(t, b);
            }
        }
        if (r.noSlot > 0) t.addPara(r.noSlot + " more industries could be built if an industry slot were free.", gray, 10f);
        if (r.locked > 0) t.addPara(r.locked + " supported buildings are locked here until researched (Ashes of the Domain).", gray, 6f);
        if (r.unsupportedBuildable > 0) t.addPara(r.unsupportedBuildable
                + " buildings from mods could be built here but aren't supported yet; see Diagnostics in the Intel screen.", gray, 6f);
    }
}
