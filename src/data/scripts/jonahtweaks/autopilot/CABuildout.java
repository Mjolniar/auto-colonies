package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Map;

/**
 * Fast buildout. Once your first colony is founded, a dialog offers to build it up fast: the colony goes to Auto, and
 * until it has earned money for 30 days in a row it builds what pays back fastest (value per credit), paid from
 * everything you hold above a credit floor you pick. It ranks by money only (CAEngine.evaluateForProfit): its own goal
 * and a high threat are ignored, and defenses, fleets and ship quality count for nothing, since they only add upkeep
 * to a colony that's losing money. The monthly budget, per-project cap, pause on
 * loss and new-upkeep cap don't apply to it; fees, the stability floor, locks and the measured-building approval do.
 * Any colony that loses money can start one from its colony panel. When it ends the colony stays in Auto under the
 * normal budget, with its own goal.
 * 0.3.0.0 (WS-B5, research 02 section 10): the buildout keeps the plan's industry slots. While the free slots are all the
 * plan's industries have left, a new industry the plan doesn't ask for must pay back per credit BUILDOUT_MARGIN times
 * as fast as the plan's own best industry pick (the engine's slot reserve, CAEngine.finish, already counts against it):
 * cheap Farming and Mining used to take a young Forge world's two slots for good, and Heavy Industry waited for size 6.
 * 0.3.0.0 (WS-F3, research 04 section 7): shorter text. The first-colony dialog reuses the Settings row's Expansion style text
 * (CASetupUI) and its fast buildout offer is 2 short paragraphs; the colony panel section moves its detail to hover text.
 * 0.3.0.0 (WS-F4): checkOffer also shows the one-time line after a save moved to the 0.3.0 menu (movedNotice).
 */
public class CABuildout {
    public static final String PREFIX = "ca:bo:";
    public static final float[] FLOORS = {50000f, 100000f, 250000f, 500000f, 1000000f, 2000000f};
    static final float[] OFFERED = {250000f, 100000f, 500000f, 1000000f};
    public static final float DEFAULT_FLOOR = 250000f, PROFIT_DAYS = 30f, OFFER_DAYS = 30f;
    /** 0.3.0.0 (B5): how much faster per credit an off-plan industry must pay back to take a slot the plan's industries need. */
    static final float BUILDOUT_MARGIN = CATuning.BUILDOUT_MARGIN;
    /** A buildout ends after this many days in a row with nothing that pays to build, or this long in all (0.2.38.0). */
    public static final float IDLE_DAYS = 60f, MAX_DAYS = 365f;
    static float checkTimer = 0f;

    public static float floor(CAData.Colony c) { return c.buildoutFloor > 0f ? c.buildoutFloor : DEFAULT_FLOOR; }

    /** Every frame from CAScript; at most once a second it looks for a new first colony to offer the buildout for. */
    public static void checkOffer(float amount) {
        checkTimer -= amount;
        if (checkTimer > 0f) return;
        checkTimer = 1f;
        checkMoved();   // 0.3.0.0 (WS-F4): this once-a-second check also carries the one-time 0.3.0 menu line
        CAData d = CAData.get();
        if (d.buildoutOffered) return;
        if (!CASync.has("buildout")) return;   // 0.3.6.0 story mode: the offer needs Fast buildout
        List<MarketAPI> mine = new java.util.ArrayList<MarketAPI>();
        for (MarketAPI x : Misc.getPlayerMarkets(false)) if (!CAEngine.isOutpost(x)) mine.add(x);   // Nexerelin outposts aren't colonies
        if (mine.isEmpty()) return;   // no colony yet: wait for the first
        MarketAPI m = mine.get(0);
        // not a newly founded first colony (a save from before this feature, or several colonies): never offer
        if (mine.size() > 1 || m.getDaysInExistence() > OFFER_DAYS || CAHypershunt.is(m) || m.getPrimaryEntity() == null) {
            d.buildoutOffered = true;
            return;
        }
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (ui.isShowingDialog() || ui.isShowingMenu() || ui.getCurrentCoreTab() != null) return;   // after founding closes
        if (ui.showInteractionDialog(new Offer(m), m.getPrimaryEntity())) d.buildoutOffered = true;
    }

    // ---------------- the 0.3.0 menu line (0.3.0.0, WS-F4) ----------------

    /** The once-key (CAMark, kept in the save) of the line below. */
    static final String MOVED_MARK = "menu3:notice";
    /** On screen: "SETUP / Settings: moved to the 0.3.0 menu, nothing you set changed" (CATheme). */
    static final String MOVED_LINE = "Settings: moved to the 0.3.0 menu. Values unchanged.";

    /**
     * What to do about the line for a save moved to 0.3.0 (CAMigrate stamps CAData.migratedAt; a save started on 0.3.0 has 0):
     * 0 nothing (no move, or done already), 1 show it, 2 only mark it done (30 days or more after the move, when the
     * Settings page's What's new box is gone too). Pure.
     */
    static int movedNotice(long migratedAt, boolean done, float daysSince) {
        if (migratedAt == 0L || done) return 0;
        return daysSince < CASetupUI.WHATS_NEW_DAYS ? 1 : 2;
    }

    /** Shows the line once per save, logged like every other entry; a failure leaves the game alone. */
    static void checkMoved() {
        try {
            CAData d = CAData.get();
            if (d.migratedAt == 0L || CAMark.at(MOVED_MARK) != null) return;
            CampaignClockAPI clock = Global.getSector().getClock();
            int act = movedNotice(d.migratedAt, false, clock.getElapsedDaysSince(d.migratedAt));
            if (act == 0) return;
            CAMark.set(MOVED_MARK, clock.getTimestamp());
            if (act == 1) CALog.did(MOVED_LINE, true);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "0.3.0 menu line failed", t);
            try { CAMark.set(MOVED_MARK, 0L); } catch (Throwable ignored) { }   // tried once; not again every second
        }
    }

    // ---------------- text (0.3.0.0, WS-F3: each paragraph 40 words or fewer, JTScreenButtonCheck) ----------------

    static final String STYLE_ASK = "is your first colony. Pick an expansion style.",
            STYLE_LATER = "Change it in Settings, Expansion style.";   // 0.3.0.0 fix pass 3: shorter
    /** The fast buildout offer, before "You have X now": what it does, then what follows (gray). */
    static final String[] OFFER_TEXT = {   // 0.3.0.0 (WS-F3 fix pass 2): shorter, for the mod-wide word budget
            "Fast buildout: Auto, and credits above a floor go to the fastest payback until it earns money 30 days running.",
            "Fees apply."};

    /**
     * The expansion style choice: the Settings row's line and its hover text (CASetupUI, Expansion style), so the two say
     * the same thing once (research 04 section 7, item 5). A missing entry is left out.
     */
    static String[] styleText() {
        List<String> out = new java.util.ArrayList<String>();
        String desc = CASetupUI.DESC.get("lowprofile");
        String[] hover = CASetupUI.HOVER.get("lowprofile");
        if (desc != null) out.add(desc);
        if (hover != null && hover.length > 0 && hover[0] != null && !hover[0].isEmpty())
            out.add("Low profile: " + Character.toLowerCase(hover[0].charAt(0)) + hover[0].substring(1));
        return out.toArray(new String[0]);
    }

    /** The colony panel section's hover: how the ranking works and when it ends (from IDLE_DAYS and MAX_DAYS). Pure. */
    static String[] hoverText() {
        return new String[]{
                "Ranks by money only; defenses, fleets and the goal wait.",
                "Ends after " + Math.round(PROFIT_DAYS) + " days of earning money, " + Math.round(IDLE_DAYS)
                        + " days with nothing worth building, or " + Math.round(MAX_DAYS) + " days in all."};
    }

    static class Offer implements InteractionDialogPlugin {
        final MarketAPI m;
        InteractionDialogAPI dialog;

        Offer(MarketAPI m) { this.m = m; }

        public void init(InteractionDialogAPI dialog) {
            this.dialog = dialog;
            dialog.setPromptText("Colony Autopilot");
            // 0.2.43.0: first the expansion style (CALowProfile), then the buildout
            // 0.3.0.0 (WS-F3): the Settings row's text (styleText) in place of 3 paragraphs of 39, 58 and 13 words
            TextPanelAPI t = dialog.getTextPanel();
            t.addPara("Colony Autopilot: " + m.getName() + " " + STYLE_ASK);
            for (String p : styleText()) t.addPara(p);
            t.addPara(STYLE_LATER, Misc.getGrayColor());
            OptionPanelAPI o = dialog.getOptionPanel();
            o.addOption("Full expansion", STYLE_FULL);
            o.addOption("Low profile", STYLE_LOW);
        }

        static final String STYLE_FULL = "style:full", STYLE_LOW = "style:low";

        void offerBuildout() {
            TextPanelAPI t = dialog.getTextPanel();
            t.addPara(OFFER_TEXT[0]);
            t.addPara(OFFER_TEXT[1], Misc.getGrayColor());
            t.addPara("You have %s now.", Misc.getHighlightColor(), Misc.getDGSCredits(CABuild.credits()));
            OptionPanelAPI o = dialog.getOptionPanel();
            for (float f : OFFERED) o.addOption("Fast buildout, keep at least " + Misc.getDGSCredits(f), f);
            o.addOption("No fast buildout", "no");
            o.setShortcut("no", Keyboard.KEY_ESCAPE, false, false, false, true);
        }

        public void optionSelected(String text, Object data) {
            try { select(data); }
            catch (Throwable t) {   // 0.3.6.2: no throw reaches the game from this dialog
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "fast buildout offer failed", t);
                dialog.dismiss();
            }
        }

        void select(Object data) {
            if (STYLE_FULL.equals(data) || STYLE_LOW.equals(data)) {
                boolean low = STYLE_LOW.equals(data);
                CALowProfile.set(CAData.settings(), low, "your choice");
                CALog.did(m.getName() + ": Expansion style: " + (low ? "Low profile" : "Full expansion") + " (Settings, Playstyle).", true);
                dialog.getOptionPanel().clearOptions();
                offerBuildout();
                return;
            }
            if (data instanceof Float) start(m, (Float) data);
            else CALog.did(m.getName() + ": Fast buildout declined. The colony panel offers it while the colony loses money.", true);
            dialog.dismiss();
        }

        public void optionMousedOver(String text, Object data) { }
        public void advance(float amount) { }
        public void backFromEngagement(EngagementResultAPI result) { }
        public Object getContext() { return null; }
        public Map<String, MemoryAPI> getMemoryMap() { return null; }
    }

    public static void start(MarketAPI m, float floor) {
        CAData d = CAData.get();
        CAData.Colony c = d.colony(m.getId());
        boolean first = !d.settings.setupDone;
        if (first && CALunaSync.keepsSetup()) { d.settings.setupDone = true; first = false; }   // 0.3.4.0: LunaLib's settings stay
        if (first) {
            CASetupUI.applyRecommended(d.settings);
            d.settings.setupDone = true;
            CALog.did("Settings: recommended values applied (Intel, Colony Autopilot, Settings).", true);   // 0.3.0.0 (WS-F3): SETUP on screen
        }
        d.setMode(c, CAData.Mode.AUTO);
        if (first) CAModeTable.setEmpire(d.settings, CAData.Mode.AUTO);   // 0.3.3.0: the first setup sets the empire mode
        c.buildout = true;
        c.buildoutFloor = floor;
        c.buildoutProfitSince = 0L;
        c.buildoutSince = Global.getSector().getClock().getTimestamp();
        c.buildoutIdleSince = 0L;
        CAAuto.nextPass.remove(m.getId());
        CALog.did(m.getName() + ": Fast buildout started. Spends down to " + Misc.getDGSCredits(floor)
                + " until the colony earns money for 30 days.", true);
    }

    static void stop(MarketAPI m, CAData.Colony c, String why) {
        c.buildout = false;
        c.buildoutProfitSince = 0L;
        c.buildoutSince = 0L;
        c.buildoutIdleSince = 0L;
        CALog.did(m.getName() + ": Fast buildout ended: " + why + ". Normal Auto budget applies.", true);
    }

    /** Ends the buildout once the colony has made money for PROFIT_DAYS in a row. True if it ended. */
    static boolean finished(MarketAPI m, CAData.Colony c) {
        CampaignClockAPI clock = Global.getSector().getClock();
        float net = m.getNetIncome();
        if (net <= 0f) { c.buildoutProfitSince = 0L; return false; }
        if (c.buildoutProfitSince == 0L) { c.buildoutProfitSince = clock.getTimestamp(); return false; }
        if (clock.getElapsedDaysSince(c.buildoutProfitSince) < PROFIT_DAYS) return false;
        stop(m, c, "earned money for 30 days, " + Misc.getDGSCredits(net) + " a month now");
        return true;
    }

    /**
     * One buildout pass for an Auto colony, instead of the normal one; runs daily. Returns false once the buildout has
     * ended, so the colony gets the normal pass.
     */
    static boolean pass(MarketAPI m, CAData.Colony c, float now, boolean building) {
        if (finished(m, c)) return false;
        // a colony that can't turn a profit never ended its buildout, and the buildout skipped its plan, defenses and the
        // endgame for good (0.2.38.0): a day with nothing that pays runs the normal pass, and the buildout ends after
        // IDLE_DAYS of that, or after MAX_DAYS whatever happens
        CampaignClockAPI clock = Global.getSector().getClock();
        if (c.buildoutSince == 0L) c.buildoutSince = clock.getTimestamp();
        if (clock.getElapsedDaysSince(c.buildoutSince) >= MAX_DAYS) { stop(m, c, "a year without 30 days of income in a row. Its plan takes over"); return false; }
        CAData d = CAData.get();
        CASettings s = d.settings;
        CAAuto.nextPass.put(m.getId(), now + 1f);
        CAEngine.Result r = CAEngine.evaluateForProfit(m, d.settings.minStability);
        CAEngine.Pick bestUp = null, bestNew = null, bestPlanInd = null;
        float upRatio = 0f, newRatio = 0f, planRatio = 0f;
        for (CAEngine.Pick q : r.picks) {
            if (q.score <= 0f) continue;
            // 0.3.0.0 (B4 fix): taken down here within the year (CATombstone's mark, or one on what it upgrades into): the buildout
            // doesn't build it back either (it ranks CAEngine.evaluateForProfit picks and starts them through CAAuto.execute,
            // which never read the marks, so a pruned or over-limit building could come back the day a slot opened)
            if (CAEndgame.rebuildBlocked(m, q.id)) continue;
            float ratio = q.score / Math.max(1f, q.cost + CABudget.constructionFee(s, q.cost));
            if (q.upgrade) { if (bestUp == null || ratio > upRatio) { bestUp = q; upRatio = ratio; } }
            else if (!building && (bestNew == null || ratio > newRatio)) { bestNew = q; newRatio = ratio; }
            if (!q.upgrade && !building && q.planStep && CAEngine.isIndustry(q.id) && (bestPlanInd == null || ratio > planRatio)) { bestPlanInd = q; planRatio = ratio; }
        }
        // 0.3.0.0 (B5, 02 section 10): an off-plan industry takes a slot the plan's industries need only when clearly better
        if (bestNew != null && bestPlanInd != null && bestNew != bestPlanInd && !bestNew.planStep && CAEngine.isIndustry(bestNew.id)) {
            boolean tight = true;
            try { tight = CABuild.freeSlots(m) <= CAPlan.openIndustrySteps(m, CAPlan.build(m, c.profile)); } catch (Throwable ignored) { }
            if (tight && !offPlanBeats(newRatio, planRatio)) { bestNew = bestPlanInd; newRatio = planRatio; }
        }
        if (bestUp == null && bestNew == null && !building) {
            if (c.buildoutIdleSince == 0L) c.buildoutIdleSince = clock.getTimestamp();
            else if (clock.getElapsedDaysSince(c.buildoutIdleSince) >= IDLE_DAYS) {
                stop(m, c, "nothing worth building for " + Math.round(IDLE_DAYS) + " days. Its plan takes over");
            }
            return false;   // nothing that pays today: the normal pass runs
        }
        c.buildoutIdleSince = 0L;
        CAEngine.Pick[] order = upRatio >= newRatio ? new CAEngine.Pick[]{bestUp, bestNew} : new CAEngine.Pick[]{bestNew, bestUp};
        float floor = Math.max(floor(c), s.reserve);   // 0.3.6.2: the credit reserve is not spent, buildout or not
        for (CAEngine.Pick p : order) {
            if (p == null) continue;
            if (p.measured && s.measuredMode == 1) { CAAuto.propose(m, c, p, "Approval needed: mod building scored from measurements"); continue; }
            float total = p.cost + CABudget.constructionFee(s, p.cost);
            if (CABuild.credits() - total < floor) {
                if (CALog.once(m.getId() + ":" + p.id + ":floor")) CALog.think(m.getName() + ": Fast buildout: " + p.name + " waits. "
                        + Misc.getDGSCredits(total) + " would go below the " + Misc.getDGSCredits(floor) + " floor.");
                continue;
            }
            if (CAAuto.execute(m, c, p, "Fast buildout") == null) CALog.forget(m.getId() + ":" + p.id + ":floor");
        }
        return true;
    }

    /** Pure, 0.3.0.0 (B5): an off-plan industry paying back offRatio per credit beats the plan's best industry at planRatio. */
    static boolean offPlanBeats(float offRatio, float planRatio) { return planRatio <= 0f || offRatio >= BUILDOUT_MARGIN * planRatio; }

    /** The colony panel's section: status and controls while it runs, a start button while the colony loses money. */
    static void render(TooltipMakerAPI t, MarketAPI m, CAData.Colony c) {
        if (CAHypershunt.is(m) || CAEngine.isOutpost(m)) return;
        boolean losing = m.getNetIncome() < 0f;
        if (!c.buildout && !losing) return;
        CATheme.heading(t, "Fast buildout", 12f);
        // 0.3.0.0 (WS-F3, research 04 section 7): one short line each; how it ranks and when it ends are hover text (hoverText).
        // 0.3.0.0 fix pass 3: shorter lines, for the mod-wide word budget (JTScreenButtonCheck)
        if (!c.buildout && !CASync.has("buildout")) { CASyncPage.buildoutOff(t, m); return; }   // 0.3.6.0 story mode
        if (!c.buildout) {
            t.addPara("Loses %s a month. Fast buildout spends credits above a floor here.", 6f,
                    Misc.getNegativeHighlightColor(), Misc.getDGSCredits(-m.getNetIncome()));
            CATheme.hover(t, hoverText());
            t.addButton("Start fast buildout, keep at least " + Misc.getDGSCredits(DEFAULT_FLOOR), PREFIX + "start",
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, com.fs.starfarer.api.ui.CutStyle.TL_BR, 360f, 24f, 6f);
            return;
        }
        float days = c.buildoutProfitSince == 0L ? 0f : Global.getSector().getClock().getElapsedDaysSince(c.buildoutProfitSince);
        t.addPara("Floor %s. Net income %s a month" + (days > 0f ? ". %s of 30 days earning." : "."), 6f,
                Misc.getHighlightColor(), Misc.getDGSCredits(floor(c)), Misc.getDGSCredits(m.getNetIncome()), "" + (int) days);
        CATheme.hover(t, hoverText());
        if (CAThreat.isHigh(m)) t.addPara("High threat: defenses wait until it ends.",
                Misc.getNegativeHighlightColor(), 4f);
        ButtonAPI first = CAColonyDialog.rowButton(t, null, "Lower floor", PREFIX + "down", false, 140f);
        ButtonAPI prev = CAColonyDialog.rowButton(t, first, "Raise floor", PREFIX + "up", false, 140f);
        CAColonyDialog.rowButton(t, prev, "Stop buildout", PREFIX + "stop", false, 140f);
        CALog.endRow(t, first);
    }

    /** Colony panel buttons; returns the message to show. */
    static String handle(MarketAPI m, String action) {
        CAData.Colony c = CAData.get().colony(m.getId());
        if (action.equals("start")) {
            if (!CASync.has("buildout")) return "Fast buildout is off. It costs " + CASyncCatalog.get("buildout").cost + " PP on the Story page.";   // 0.3.6.0 story mode
            start(m, DEFAULT_FLOOR);
            return "Fast buildout started";
        }
        if (!c.buildout) return null;
        if (action.equals("stop")) { stop(m, c, "stopped by you"); return "Fast buildout stopped. Mode: Auto"; }
        float f = floor(c), n = f;
        if (action.equals("down")) { n = FLOORS[0]; for (float x : FLOORS) if (x < f) n = x; }   // next step below, or the lowest
        else if (action.equals("up")) { n = FLOORS[FLOORS.length - 1]; for (int k = FLOORS.length - 1; k >= 0; k--) if (FLOORS[k] > f) n = FLOORS[k]; }
        c.buildoutFloor = n;
        return "Floor set to " + Misc.getDGSCredits(n);
    }
}
