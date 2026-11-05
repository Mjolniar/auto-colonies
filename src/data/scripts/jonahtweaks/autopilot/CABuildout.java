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
 */
public class CABuildout {
    public static final String PREFIX = "ca:bo:";
    public static final float[] FLOORS = {50000f, 100000f, 250000f, 500000f, 1000000f, 2000000f};
    static final float[] OFFERED = {250000f, 100000f, 500000f, 1000000f};
    public static final float DEFAULT_FLOOR = 250000f, PROFIT_DAYS = 30f, OFFER_DAYS = 30f;
    /** A buildout ends after this many days in a row with nothing that pays to build, or this long in all (2.38.0). */
    public static final float IDLE_DAYS = 60f, MAX_DAYS = 365f;
    static float checkTimer = 0f;

    public static float floor(CAData.Colony c) { return c.buildoutFloor > 0f ? c.buildoutFloor : DEFAULT_FLOOR; }

    /** Every frame from CAScript; at most once a second it looks for a new first colony to offer the buildout for. */
    public static void checkOffer(float amount) {
        checkTimer -= amount;
        if (checkTimer > 0f) return;
        checkTimer = 1f;
        CAData d = CAData.get();
        if (d.buildoutOffered) return;
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

    static class Offer implements InteractionDialogPlugin {
        final MarketAPI m;
        InteractionDialogAPI dialog;

        Offer(MarketAPI m) { this.m = m; }

        public void init(InteractionDialogAPI dialog) {
            this.dialog = dialog;
            dialog.setPromptText("Colony Autopilot");
            TextPanelAPI t = dialog.getTextPanel();
            t.addPara("Colony Autopilot: " + m.getName() + " is your first colony. A new colony usually loses money for a while.");
            t.addPara("Fast buildout puts it in Auto and spends everything you have above a credit floor on the buildings that pay "
                    + "back fastest, until the colony has earned money for 30 days in a row. Then it stays in Auto with the normal "
                    + "monthly budget. Convenience fees apply. You can stop it, or change the floor, in the colony's Colony Autopilot panel.");
            t.addPara("You have %s now.", Misc.getHighlightColor(), Misc.getDGSCredits(CABuild.credits()));
            OptionPanelAPI o = dialog.getOptionPanel();
            for (float f : OFFERED) o.addOption("Build it up fast, keeping at least " + Misc.getDGSCredits(f), f);
            o.addOption("No thanks, I'll manage it", "no");
            o.setShortcut("no", Keyboard.KEY_ESCAPE, false, false, false, true);
        }

        public void optionSelected(String text, Object data) {
            if (data instanceof Float) start(m, (Float) data);
            else CALog.add(m.getName() + ": fast buildout declined; the colony panel can start one while it loses money", true);
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
        if (!d.settings.setupDone) {
            CASetupUI.applyRecommended(d.settings);
            d.settings.setupDone = true;
            CALog.add("Recommended settings applied; review them on the Settings page of the Colony Autopilot intel", true);
        }
        c.mode = CAData.Mode.AUTO;
        c.buildout = true;
        c.buildoutFloor = floor;
        c.buildoutProfitSince = 0L;
        c.buildoutSince = Global.getSector().getClock().getTimestamp();
        c.buildoutIdleSince = 0L;
        CAAuto.nextPass.remove(m.getId());
        CALog.add(m.getName() + ": fast buildout started; spends down to " + Misc.getDGSCredits(floor)
                + " until the colony has earned money for 30 days", true);
    }

    static void stop(MarketAPI m, CAData.Colony c, String why) {
        c.buildout = false;
        c.buildoutProfitSince = 0L;
        c.buildoutSince = 0L;
        c.buildoutIdleSince = 0L;
        CALog.add(m.getName() + ": fast buildout ended (" + why + "); the normal Auto budget applies now", true);
    }

    /** Ends the buildout once the colony has made money for PROFIT_DAYS in a row. True if it ended. */
    static boolean finished(MarketAPI m, CAData.Colony c) {
        CampaignClockAPI clock = Global.getSector().getClock();
        float net = m.getNetIncome();
        if (net <= 0f) { c.buildoutProfitSince = 0L; return false; }
        if (c.buildoutProfitSince == 0L) { c.buildoutProfitSince = clock.getTimestamp(); return false; }
        if (clock.getElapsedDaysSince(c.buildoutProfitSince) < PROFIT_DAYS) return false;
        stop(m, c, "it has earned money for 30 days, " + Misc.getDGSCredits(net) + " a month now");
        return true;
    }

    /**
     * One buildout pass for an Auto colony, instead of the normal one; runs daily. Returns false once the buildout has
     * ended, so the colony gets the normal pass.
     */
    static boolean pass(MarketAPI m, CAData.Colony c, float now, boolean building) {
        if (finished(m, c)) return false;
        // a colony that can't turn a profit never ended its buildout, and the buildout skipped its plan, defenses and the
        // endgame for good (2.38.0): a day with nothing that pays runs the normal pass, and the buildout ends after
        // IDLE_DAYS of that, or after MAX_DAYS whatever happens
        CampaignClockAPI clock = Global.getSector().getClock();
        if (c.buildoutSince == 0L) c.buildoutSince = clock.getTimestamp();
        if (clock.getElapsedDaysSince(c.buildoutSince) >= MAX_DAYS) { stop(m, c, "a year without earning money for 30 days in a row; its plan takes over"); return false; }
        CAData d = CAData.get();
        CASettings s = d.settings;
        CAAuto.nextPass.put(m.getId(), now + 1f);
        CAEngine.Result r = CAEngine.evaluateForProfit(m, d.minStability);
        CAEngine.Pick bestUp = null, bestNew = null;
        float upRatio = 0f, newRatio = 0f;
        for (CAEngine.Pick q : r.picks) {
            if (q.score <= 0f) continue;
            float ratio = q.score / Math.max(1f, q.cost + CABudget.constructionFee(s, q.cost));
            if (q.upgrade) { if (bestUp == null || ratio > upRatio) { bestUp = q; upRatio = ratio; } }
            else if (!building && (bestNew == null || ratio > newRatio)) { bestNew = q; newRatio = ratio; }
        }
        if (bestUp == null && bestNew == null && !building) {
            if (c.buildoutIdleSince == 0L) c.buildoutIdleSince = clock.getTimestamp();
            else if (clock.getElapsedDaysSince(c.buildoutIdleSince) >= IDLE_DAYS) {
                stop(m, c, "nothing that pays has been worth building for " + Math.round(IDLE_DAYS) + " days; its plan takes over");
            }
            return false;   // nothing that pays today: the normal pass runs
        }
        c.buildoutIdleSince = 0L;
        CAEngine.Pick[] order = upRatio >= newRatio ? new CAEngine.Pick[]{bestUp, bestNew} : new CAEngine.Pick[]{bestNew, bestUp};
        float floor = floor(c);
        for (CAEngine.Pick p : order) {
            if (p == null) continue;
            if (p.measured && s.measuredMode == 1) { CAAuto.propose(m, c, p, "a mod building scored from measurements, needs your approval"); continue; }
            float total = p.cost + CABudget.constructionFee(s, p.cost);
            if (CABuild.credits() - total < floor) {
                if (CALog.once(m.getId() + ":" + p.id + ":floor")) CALog.add(m.getName() + ": fast buildout: " + p.name + " waits ("
                        + Misc.getDGSCredits(total) + " would take you below the " + Misc.getDGSCredits(floor) + " floor)");
                continue;
            }
            if (CAAuto.execute(m, c, p, "Fast buildout") == null) CALog.forget(m.getId() + ":" + p.id + ":floor");
        }
        return true;
    }

    /** The colony panel's section: status and controls while it runs, a start button while the colony loses money. */
    static void render(TooltipMakerAPI t, MarketAPI m, CAData.Colony c) {
        if (CAHypershunt.is(m) || CAEngine.isOutpost(m)) return;
        boolean losing = m.getNetIncome() < 0f;
        if (!c.buildout && !losing) return;
        t.addSectionHeading("Fast buildout", Alignment.MID, 12f);
        if (!c.buildout) {
            t.addPara("This colony loses %s a month. Fast buildout puts it in Auto and spends everything above a credit floor on the "
                    + "buildings that pay back fastest, until it has earned money for 30 days.", 6f, Misc.getNegativeHighlightColor(),
                    Misc.getDGSCredits(-m.getNetIncome()));
            t.addButton("Start fast buildout, keeping at least " + Misc.getDGSCredits(DEFAULT_FLOOR), PREFIX + "start",
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, com.fs.starfarer.api.ui.CutStyle.ALL, 360f, 24f, 6f);
            return;
        }
        float days = c.buildoutProfitSince == 0L ? 0f : Global.getSector().getClock().getElapsedDaysSince(c.buildoutProfitSince);
        t.addPara("Spending everything above %s on the buildings that pay back fastest, ranked by money only: no defenses or "
                        + "fleets, whatever the goal. Net income %s a month"
                        + (days > 0f ? "; earning money for %s of 30 days." : "; it ends after 30 days of earning money."), 6f,
                Misc.getHighlightColor(), Misc.getDGSCredits(floor(c)), Misc.getDGSCredits(m.getNetIncome()), "" + (int) days);
        if (CAThreat.isHigh(m)) t.addPara("The threat is high. Defenses wait until the buildout ends; then the colony plans for Security.",
                Misc.getNegativeHighlightColor(), 4f);
        ButtonAPI first = CAColonyDialog.rowButton(t, null, "Lower floor", PREFIX + "down", false, 140f);
        ButtonAPI prev = CAColonyDialog.rowButton(t, first, "Raise floor", PREFIX + "up", false, 140f);
        CAColonyDialog.rowButton(t, prev, "Stop buildout", PREFIX + "stop", false, 140f);
        CALog.endRow(t, first);
    }

    /** Colony panel buttons; returns the message to show. */
    static String handle(MarketAPI m, String action) {
        CAData.Colony c = CAData.get().colony(m.getId());
        if (action.equals("start")) { start(m, DEFAULT_FLOOR); return "Fast buildout started"; }
        if (!c.buildout) return null;
        if (action.equals("stop")) { stop(m, c, "stopped by you"); return "Fast buildout stopped; the colony stays in Auto"; }
        float f = floor(c), n = f;
        if (action.equals("down")) { n = FLOORS[0]; for (float x : FLOORS) if (x < f) n = x; }   // next step below, or the lowest
        else if (action.equals("up")) { n = FLOORS[FLOORS.length - 1]; for (int k = FLOORS.length - 1; k >= 0; k--) if (FLOORS[k] > f) n = FLOORS[k]; }
        c.buildoutFloor = n;
        return "Floor set to " + Misc.getDGSCredits(n);
    }
}
