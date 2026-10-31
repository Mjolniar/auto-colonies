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
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;

/** The colony panel: profile, mode, approvals, recommended builds and locks; opened from the colony screen. */
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

    /** Scroll position of the previous build, kept across rebuilds of the same page. */
    float savedScroll() {
        if (lastTooltip == null || lastSetupPage != setupPage || lastTooltip.getExternalScroller() == null) return 0f;
        return lastTooltip.getExternalScroller().getYOffset();
    }

    void restoreScroll(TooltipMakerAPI t, float y) {
        if (t.getExternalScroller() != null) t.getExternalScroller().setYOffset(y);
        lastTooltip = t;
        lastSetupPage = setupPage;
    }

    public CAColonyDialog(MarketAPI market) { this.market = market; }

    class Plugin extends BaseCustomUIPanelPlugin {
        @Override public void buttonPressed(Object id) { handle(id); }
        @Override public void advance(float amount) { if (dirty) { dirty = false; build(); } }
    }

    @Override public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) { root = panel; build(); }
    @Override public boolean hasCancelButton() { return false; }
    @Override public String getConfirmText() { return "Close"; }
    @Override public CustomUIPanelPlugin getCustomPanelPlugin() { return plugin; }

    void handle(Object id) {
        CAData d = CAData.get();
        CAData.Colony c = d.colony(market.getId());
        if (CASetupUI.handle(id, d.settings)) { dirty = true; return; }
        if (id instanceof CAData.Profile) { c.profile = (CAData.Profile) id; message = "Profile set to " + c.profile.label; }
        else if (id instanceof CAData.Mode) {
            CAData.Mode mo = (CAData.Mode) id;
            boolean needsSetup = (mo == CAData.Mode.APPROVE || mo == CAData.Mode.AUTO) && !d.settings.setupDone;
            if (needsSetup) { setupPage = true; setupMode = mo; CASetupUI.applyRecommended(d.settings); CASetupUI.openAll(); }
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

    static ButtonAPI rowButton(TooltipMakerAPI t, ButtonAPI prev, String label, Object id, boolean on, float w) {
        Color hl = Misc.getHighlightColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        ButtonAPI b = t.addButton(label, id, on ? hl : base, dark, Alignment.MID, CutStyle.ALL, w, 24f, prev == null ? 8f : 0f);
        if (prev != null) b.getPosition().rightOfTop(prev, 6f);
        return b;
    }

    void build() {
        float scrollY = savedScroll();
        if (content != null) root.removeComponent(content);
        content = root.createCustomPanel(W, H, plugin);
        TooltipMakerAPI t = content.createUIElement(W, H, true);
        CAData data = CAData.get();
        CASettings s = data.settings;
        CAData.Colony c = data.colony(market.getId());
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();

        if (setupPage) {
            t.addTitle("Colony Autopilot: first-time setup");
            t.addPara("Every setting below is pre-filled with a recommendation from your current income. "
                    + "All of them can be changed later in Colony Autopilot under Colonies in the Intel screen.", 6f);
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

        t.addTitle("Colony Autopilot: " + market.getName());
        t.addPara("Size %s, stability %s, hazard %s, accessibility %s, industries %s of %s. Credits: %s.", 6f, hl,
                "" + market.getSize(), "" + (int) market.getStabilityValue(),
                Math.round(market.getHazardValue() * 100f) + "%",
                Math.round(market.getAccessibilityMod().computeEffective(0f) * 100f) + "%",
                "" + Misc.getNumIndustries(market), "" + Misc.getMaxIndustries(market), Misc.getDGSCredits(CABuild.credits()));
        if (c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO) {
            t.addPara("Budget this month: %s spent of %s, %s available. Fees: %s.", 4f, hl, Misc.getDGSCredits(s.spent),
                    Misc.getDGSCredits(CABudget.limit(s)), Misc.getDGSCredits(CABudget.available(s)), CASettings.FEE_NAMES[s.feeLevel]);
            if (CABudget.paused(s)) t.addPara("Spending is paused: net income is negative.", Misc.getNegativeHighlightColor(), 4f);
        }
        float threat = CAThreat.estimate(market);
        CAData.Profile planWith = (c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO) ? CAThreat.effective(market, c.profile) : c.profile;
        t.addPara("Threat estimate: %s" + (planWith != c.profile ? " (planning with Security until it drops below "
                        + Math.round(CAThreat.CLEAR * 100f) + "%%, at least " + Math.round(CAThreat.MIN_DAYS) + " days)" : ""), 4f,
                threat >= CAThreat.THRESHOLD || c.threatHigh ? Misc.getNegativeHighlightColor() : hl, Math.round(threat * 100f) + "%");
        if (Global.getSettings().getModManager().isModEnabled(CADevelopment.MOD) && !CAHypershunt.is(market)) {
            try { CADevelopment.render(t, market); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "management style line failed at " + market.getName(), e); }
        }
        Industry imp = CAStory.bestImprovement(market, c);
        if (imp != null) t.addPara("Best story point improvement: %s" + (s.storyPointRule == 0 ? " (you improve it; Story points is set to Player only)" : ""), 4f, hl, imp.getCurrentName());
        if (message != null) t.addPara(CALog.esc(message), hl, 6f);

        if (c.pendingId != null) {
            t.addSectionHeading("Waiting for your approval", Alignment.MID, 10f);
            t.addPara("%s for %s (plus a %s fee)", 6f, hl, c.pendingName, Misc.getDGSCredits(c.pendingCost),
                    Misc.getDGSCredits(CABudget.constructionFee(s, c.pendingCost)));
            ButtonAPI a = t.addButton("Approve", APPROVE, hl, dark, Alignment.MID, CutStyle.ALL, 140f, 24f, 6f);
            ButtonAPI r = t.addButton("Reject", REJECT, base, dark, Alignment.MID, CutStyle.ALL, 140f, 24f, 0f);
            r.getPosition().rightOfTop(a, 6f);
            CALog.endRow(t, a);
        }

        t.addSectionHeading("Goal profile", Alignment.MID, 12f);
        ButtonAPI prev = null, first = null;
        for (CAData.Profile p : CAData.Profile.values()) { prev = rowButton(t, prev, p.label, p, p == c.profile, 132f); if (first == null) first = prev; }
        CALog.endRow(t, first);
        t.addSectionHeading("Mode", Alignment.MID, 12f);
        prev = null; first = null;
        for (CAData.Mode mo : CAData.Mode.values()) { prev = rowButton(t, prev, mo.label, mo, mo == c.mode, 132f); if (first == null) first = prev; }
        CALog.endRow(t, first);
        t.addPara("Advisor recommends and you click Build. Approve proposes one project at a time for one click. "
                + "Auto builds within your budget.", gray, 6f);
        CABuildout.render(t, market, c);

        CAPlan.Plan plan = CAPlan.build(market, planWith);
        t.addSectionHeading("Plan: " + plan.name + " (" + plan.doneCount(market) + " of " + plan.steps.size() + " done)", Alignment.MID, 12f);
        boolean nextShown = false;
        for (CAPlan.Step st : plan.steps) {
            boolean done = st.done(market);
            String tag = done ? "Done" : (!nextShown ? "Next" : "Planned");
            if (!done && !nextShown) nextShown = true;
            String lock = !done && st.kind == CAPlan.Kind.BUILD ? CAAotD.lockReason(st.id, market) : null;
            t.addPara("%s  " + CALog.esc(st.label + (lock != null ? " (locked: " + lock + ")" : "")), 2f, done ? gray : (tag.equals("Next") ? hl : base), tag);
        }

        boolean shunt = CAHypershunt.is(market);
        if (shunt) {
            t.addSectionHeading("Coronal Hypershunt (Ashes of the Domain)", Alignment.MID, 12f);
            try { CAHypershunt.renderColony(t, market, c, s); }
            catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt section failed at " + market.getName(), e);
                t.addPara("Hypershunt info unavailable; see starsector.log.", gray, 6f);
            }
        } else t.addSectionHeading("Recommended builds", Alignment.MID, 12f);
        if (shunt) { /* nothing can be built */ }
        else if (c.mode == CAData.Mode.OFF) {
            t.addPara("Autopilot is off for this colony.", gray, 8f);
        } else {
            CAEngine.Result r = CAEngine.evaluate(market, planWith, data.minStability);
            if (r.blocked != null) t.addPara(CALog.esc(r.blocked) + "; new buildings wait for it, but upgrades can start now.", hl, 8f);
            List<CAEngine.Pick> picks = r.picks;
            if (picks.isEmpty()) t.addPara("Nothing can be built here right now: every option is already built, blocked by the game, or has no free industry slot.", 8f);
            for (int i = 0; i < Math.min(3, picks.size()); i++) {
                CAEngine.Pick p = picks.get(i);
                t.addPara("%s. %s %s: estimated value %s, cost %s, %s days", 12f, hl, "" + (i + 1), p.verb, p.name,
                        CAEngine.valueText(p.score), Misc.getDGSCredits(p.cost), "" + (int) p.days);
                for (int j = 0; j < Math.min(5, p.lines.size()); j++) t.addPara("    " + CALog.esc(p.lines.get(j)), gray, 2f);
                ButtonAPI b = t.addButton(p.verb + " (" + Misc.getDGSCredits(p.cost) + ")", p, base, dark,
                        Alignment.MID, CutStyle.ALL, 240f, 24f, 6f);
                b.setEnabled((p.upgrade || r.blocked == null) && CABuild.credits() >= p.cost);
                if (p.upgrade) t.addPara("    Runs alongside other construction", gray, 2f);
            }
            if (r.noSlot > 0) t.addPara(r.noSlot + " more industries could be built if an industry slot were free.", gray, 10f);
            if (r.locked > 0) t.addPara(r.locked + " supported buildings are locked here until researched (Ashes of the Domain).", gray, 6f);
            if (r.unsupportedBuildable > 0) t.addPara(r.unsupportedBuildable
                    + " buildings from mods could be built here but aren't supported yet; see Diagnostics in the Intel screen.", gray, 6f);
        }

        if (CATasc.enabled() && !shunt && c.mode != CAData.Mode.OFF) {
            t.addSectionHeading("Terraforming (Terraforming & Station Construction)", Alignment.MID, 12f);
            try { CATasc.renderColony(t, market, c); }
            catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "TASC section failed at " + market.getName(), e);
                t.addPara("Terraforming info unavailable; see starsector.log.", gray, 6f);
            }
        }

        if (Global.getSettings().getModManager().isModEnabled(CAHypershunt.MOD) && CAMegastructures.has(market) && c.mode != CAData.Mode.OFF) {
            t.addSectionHeading("Megastructure (Ashes of the Domain)", Alignment.MID, 12f);
            try { CAMegastructures.renderColony(t, market, c, s); }
            catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "megastructure section failed at " + market.getName(), e);
                t.addPara("Megastructure info unavailable; see starsector.log.", gray, 6f);
            }
        }

        if (Global.getSettings().getModManager().isModEnabled(CAAugments.MOD) && s.augmentMode != 2 && c.mode != CAData.Mode.OFF && !shunt) {
            t.addSectionHeading("Station augments", Alignment.MID, 12f);
            try { CAAugments.renderColony(t, market, c, s, planWith); }
            catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment section failed at " + market.getName(), e);
                t.addPara("Station augment info unavailable; see starsector.log.", gray, 6f);
            }
        }

        if (Global.getSettings().getModManager().isModEnabled(CASynergies.MOD) && c.mode != CAData.Mode.OFF && !shunt) {
            t.addSectionHeading("Industry synergies (Ashes of the Domain)", Alignment.MID, 12f);
            try { CASynergies.render(t, market); }
            catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "industry synergy section failed at " + market.getName(), e);
                t.addPara("Synergy info unavailable; see starsector.log.", gray, 6f);
            }
        }

        t.addSectionHeading("Locks", Alignment.MID, 12f);
        t.addPara("A locked building is never upgraded, replaced or removed by Autopilot.", gray, 6f);
        for (Industry ind : market.getIndustries()) {
            if (ind.isHidden()) continue;   // never touched anyway
            boolean locked = c.locks.contains(ind.getId());
            t.addPara("%s: %s", 6f, locked ? hl : gray, ind.getCurrentName(), locked ? "locked" : "unlocked");
            t.addButton(locked ? "Unlock" : "Lock", "ca:lock:" + ind.getId(), base, dark, Alignment.MID, CutStyle.ALL, 100f, 20f, 2f);
        }

        List<String> odd = CADiagnostics.unsupportedConditions(market);
        if (!odd.isEmpty() && !shunt) t.addPara(CALog.esc("Unmapped conditions on this colony (scores may be off): " + odd), gray, 10f);

        content.addUIElement(t).inTL(0f, 0f);
        root.addComponent(content).inTL(0f, 0f);
        restoreScroll(t, scrollY);
    }
}
