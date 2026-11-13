package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_GrantAutonomy;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Autonomy (Nexerelin; setting "Autonomy": Off, Recommend (default), Auto). Nexerelin lets a colony be run autonomously:
 * you get 25% of its profit or loss (nex_autonomousIncomeMult), it no longer counts against your colony limit, and nothing
 * can be built there; taking it back adds 2 unrest (Nex_GrantAutonomy).
 *
 * Only ever for money, never as a way past the colony limit (that gets an administrator, CAItems.adminsNeeded), and never
 * for a colony under a year old (NEW_DAYS), a fast buildout, an outpost, a hypershunt, a megastructure or an AI core
 * administrator. A colony that has lost money LOSS_MONTHS months in a row, or the one that saves the most in a money
 * emergency (net income negative with under RUNWAY months of it left above the credit reserve), is:
 * - Recommend: logged once and listed on the Mod features page with a Grant autonomy button;
 * - Auto (0.2.34.3): made autonomous, one every GAP_DAYS at most. Outside an emergency not while spending freely
 *   (CASurplus.rich): the pool pays for it, and an autonomous colony can't be built on.
 * Colonies made autonomous by you or by Auto stay that way until you take them back (a button per colony; Nexerelin's
 * unrest applies). Colonies an older Autopilot made autonomous (CAData.autonomous) are taken back, one a day, as soon as
 * there's no money emergency, taking one back wouldn't start one, and there's room under your colony limit or an idle
 * administrator to put there, with Nexerelin's usual unrest (0.2.37.0: it used to skip the unrest, which a player can't).
 */
final class CAAutonomy {
    static final String MOD = "nexerelin";
    static final float RUNWAY = 3f, NEW_DAYS = 365f, GAP_DAYS = 30f;
    static final int REVOKE_UNREST = 2;   // Nex_GrantAutonomy.REVOKE_UNREST
    /** Months in a row a colony must lose money before autonomy is suggested (or, on Auto, granted) outside an emergency. */
    static final int LOSS_MONTHS = 3;

    private CAAutonomy() { }

    static final class Emergency {
        boolean money;
        String why;
    }

    static final class Cand {
        MarketAPI m;
        float saving;   // credits a month you stop losing
    }

    static float incomeMult() {
        try { return Global.getSettings().getFloat("nex_autonomousIncomeMult"); } catch (Throwable t) { return 0.25f; }
    }

    /** A colony's monthly profit or loss as Nexerelin counts it for an autonomous colony. */
    static float colonyNet(MarketAPI m) { return m.getIndustryIncome() + m.getExportIncome(false) - m.getIndustryUpkeep(); }

    static boolean moneyEmergency(float net, float credits, float reserve) { return net < 0f && credits - reserve < RUNWAY * -net; }

    /** Credits a month you stop gaining (or losing) when a colony with this net income becomes autonomous. */
    static float moneySaved(float colonyNet, float mult) { return -(1f - mult) * colonyNet; }

    /** Outside an emergency: lost money long enough to suggest (or grant) autonomy. Pure. */
    static boolean longLoss(int streak) { return streak >= LOSS_MONTHS; }

    /** A money emergency, or null. Going past the colony limit isn't one: that gets an administrator (CAItems.adminsNeeded). */
    static Emergency emergency(CASettings s) {
        float net = CABudget.netIncome(s);
        if (!moneyEmergency(net, CABuild.credits(), s.reserve)) return null;
        Emergency e = new Emergency();
        e.money = true;
        e.why = "net income " + Misc.getDGSCredits(net) + " a month, under " + (int) RUNWAY + " months of reserve";
        return e;
    }

    static boolean eligible(CAData d, MarketAPI m, boolean autoOnly) {
        CAData.Colony c = d.colony(m.getId());
        if (autoOnly ? c.mode != CAData.Mode.AUTO : c.mode == CAData.Mode.OFF) return false;
        if (c.buildout) return false;   // a fast buildout loses money on purpose until it pays
        if (m.getDaysInExistence() < NEW_DAYS) return false;   // a new colony needs its stability, and taking it back costs 2
        if (CAEngine.isOutpost(m) || CAHypershunt.is(m) || CAMegastructures.has(m)) return false;
        return m.getAdmin() == null || !m.getAdmin().isAICore();   // an AI core you put there would stay behind
    }

    /** Colonies autonomy would save money at, biggest saving first: losing money now, a year old or more. */
    static List<Cand> losers(CAData d, boolean autoOnly) {
        float mult = incomeMult();
        List<Cand> out = new ArrayList<Cand>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!eligible(d, m, autoOnly)) continue;
            if (CARogue.adminLocked(m)) continue;   // 0.3.2.1: autonomy would unseat Rogue Planets' Trash King
            float v = moneySaved(colonyNet(m), mult);
            if (v <= 0f) continue;
            Cand k = new Cand(); k.m = m; k.saving = v;
            out.add(k);
        }
        java.util.Collections.sort(out, new java.util.Comparator<Cand>() { public int compare(Cand a, Cand b) { return Float.compare(b.saving, a.saving); } });
        return out;
    }

    /** What keeps a colony an older Autopilot made autonomous from being taken back, or null if nothing does. */
    static String waitReason(CAData d, CASettings s, MarketAPI m) {
        if (emergency(s) != null) return "the money emergency is over";
        float after = CABudget.netIncome(s) + (1f - incomeMult()) * colonyNet(m);
        if (moneyEmergency(after, CABuild.credits(), s.reserve)) return "net income allows it";
        if (CAItems.overLimit() + 1 > 0 && CAItems.idleAdmin(d) == null)
            return "an administrator is free";
        // 0.3.0.0 (WS-E fix): one more colony governed by you costs every colony you govern CAStability.oneMoreGoverned() (2), under
        // the colony limit too; an idle administrator placed there covers it (reclaim)
        if (CAItems.idleAdmin(d) == null && !CAStability.canGovernedLose(-CAStability.oneMoreGoverned(), null))
            return "your colonies can take the stability loss";
        // 0.3.0.0 (WS-E fix): never 0 stability. Taking a colony back adds REVOKE_UNREST unless Nexerelin set its no-loss flag
        if (!m.getMemoryWithoutUpdate().getBoolean(Nex_GrantAutonomy.MEMORY_KEY_NO_STAB_LOSS) && !CAStability.canLose(m, -REVOKE_UNREST, null))
            return "its stability can take " + REVOKE_UNREST + " unrest";
        return null;
    }

    /** Forgets colonies that were taken back by hand, lost, or are gone. */
    static void tidy(CAData d) {
        for (String id : new ArrayList<String>(d.autonomous().keySet())) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null || m.isPlayerOwned() || m.getFaction() == null || !m.getFaction().isPlayerFaction()) d.autonomous().remove(id);
        }
    }

    /** Counts each colony's months in a row of losing money, once a month. */
    static void countLosses(CAData d) {
        int month = Global.getSector().getClock().getCycle() * 12 + Global.getSector().getClock().getMonth();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if (c.lossMonth == month) continue;
            c.lossMonth = month;
            c.lossStreak = colonyNet(m) < 0f ? c.lossStreak + 1 : 0;
        }
    }

    public static void run(CAData d, CASettings s) {
        tidy(d);
        countLosses(d);
        // colonies an older Autopilot made autonomous come back as soon as they can, one a day, whatever the setting
        for (String id : new ArrayList<String>(d.autonomous().keySet())) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null || waitReason(d, s, m) != null) continue;
            reclaim(d, m, "there's room for it");
            return;
        }
        if (s.autonomyMode == 0) return;
        Emergency e = emergency(s);
        boolean rich = CASurplus.rich(s);
        Cand pick = null;
        for (Cand k : losers(d, s.autonomyMode == 2)) {
            if (e == null && (rich || !longLoss(d.colony(k.m.getId()).lossStreak))) continue;   // outside an emergency: a long loss, and no pool paying for it
            pick = k;
            break;
        }
        if (pick == null) {
            // 0.3.0.9: not acting, starsector.log only; one key (the month was in it)
            if (e != null && CALog.once("autonomy:none"))
                CALog.quiet("Emergency (" + e.why + "): no colony a year old loses money, so autonomy wouldn't help");
            return;
        }
        String why = e != null ? "money emergency" : "lost money " + d.colony(pick.m.getId()).lossStreak + " months in a row";
        if (s.autonomyMode == 2) {
            if (Global.getSector().getClock().getElapsedDaysSince(d.lastAutonomyChange) < GAP_DAYS && d.lastAutonomyChange != 0L) return;
            grant(d, pick.m, "Auto: " + why, pick.saving);
            return;
        }
        if (CALog.once("autonomy:rec:" + pick.m.getId()))
            CAApprovals.say(pick.m.getName() + ": autonomy would save " + Misc.getDGSCredits(pick.saving) + "/month (" + why + "). Grant on the Mod features page.", true);
        // 0.3.3.0: a decision in the approval summary
        final MarketAPI mm = pick.m; final float saving = pick.saving;
        CAApprovals.offer("autonomy:" + pick.m.getId(), "autonomy", pick.m, "Grant autonomy (saves " + Misc.getDGSCredits(saving) + " a month)", 0f, 0f, why, () -> {
            if (!Global.getSettings().getModManager().isModEnabled(MOD)) return "Nexerelin is off";
            if (!mm.isPlayerOwned()) return "Not your colony now";
            grant(CAData.get(), mm, "your approval", saving);
            return null;
        });
    }

    /** Your order, or Auto's. The colony stays autonomous until you take it back. */
    static void grant(CAData d, MarketAPI m, String why, float saving) {
        // a hired administrator would be replaced by a local one and keep drawing full salary, so free them first
        AdminData a = CAItems.adminAt(m);
        if (a != null) {
            a.setMarket(null);
            m.setAdmin(Global.getSector().getPlayerPerson());
        }
        Nex_GrantAutonomy.grantAutonomy(m);
        d.clearPending(d.colony(m.getId()));   // nothing can be built there now
        d.lastAutonomyChange = Global.getSector().getClock().getTimestamp();
        CALog.forget("autonomy:rec:" + m.getId());
        CALog.did(m.getName() + ": autonomous (" + why + ")" + (saving > 0f ? ", saves " + Misc.getDGSCredits(saving) + "/month" : "") + "."
                + (a != null ? " " + a.getPerson().getNameString() + " is free." : ""), true);
    }

    /** Taking one back, with Nexerelin's usual unrest (0.2.37.0: no exception for colonies an older Autopilot made autonomous). */
    static void reclaim(CAData d, MarketAPI m, String why) {
        boolean legacy = d.autonomous().containsKey(m.getId());
        // 0.3.0.0 (WS-E fix): decided before it comes back: past the colony limit, or the colonies you govern can't take one more
        // governed colony (CAStability.oneMoreGoverned), an idle administrator governs it
        boolean needAdmin = CAItems.overLimit() + 1 > 0 || !CAStability.canGovernedLose(-CAStability.oneMoreGoverned(), null);
        Nex_GrantAutonomy.revokeAutonomy(m);
        d.autonomous().remove(m.getId());
        d.lastAutonomyChange = Global.getSector().getClock().getTimestamp();
        CALog.forget("autonomy:back:" + m.getId());
        AdminData a = needAdmin ? CAItems.idleAdmin(d) : null;
        if (a != null) {
            m.setAdmin(a.getPerson());
            a.setMarket(m);
        }
        CALog.did(m.getName() + ": taken back from autonomy (" + why + ")."
                + (a != null ? " " + a.getPerson().getNameString() + " governs it." : ""), true);
    }

    /** Buttons on the Mod features page: grant autonomy to a colony, or take one back now. */
    static void handle(CAData d, String action, String marketId) {
        MarketAPI m = Global.getSector().getEconomy().getMarket(marketId);
        if (m == null || m.getFaction() == null || !m.getFaction().isPlayerFaction()) return;
        if (action.equals("grant") && m.isPlayerOwned()) grant(d, m, "your order", moneySaved(colonyNet(m), incomeMult()));
        else if (action.equals("back") && !m.isPlayerOwned()) reclaim(d, m, "your order");
    }

    /** The Autonomy section of the empire panel's Mod features page. */
    static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        CATheme.heading(t, "Autonomy (Nexerelin)", 0f);
        // 0.3.0.0 (WS-F3 fix, research 04 section 7 #6): the mode as a readout, one short line and who qualifies on hover (was 60 words)
        CATheme.mode(t, CASettings.AUTONOMY_NAMES[Math.max(0, Math.min(2, s.autonomyMode))]);
        t.addPara("Autonomous colonies give %s of profit or loss, outside the colony limit; taking one back costs %s unrest.",
                4f, hl, Math.round(incomeMult() * 100f) + "%", "" + REVOKE_UNREST);
        tidy(d);
        Emergency e = emergency(s);
        if (e != null) t.addPara("Money emergency: " + CALog.esc(e.why) + ".", Misc.getNegativeHighlightColor(), 6f);
        List<Cand> losers = losers(d, false);
        if (losers.isEmpty()) t.addPara("No colony a year old loses money.", gray, 6f);
        int shown = 0;
        for (Cand k : losers) {
            if (shown++ >= 6) break;
            int streak = d.colony(k.m.getId()).lossStreak;
            t.addPara("%s loses %s/month (%s months in a row): autonomy saves %s/month.", 6f, hl, k.m.getName(),
                    Misc.getDGSCredits(-colonyNet(k.m)), "" + streak, Misc.getDGSCredits(k.saving));
            t.addButton("Grant autonomy: " + k.m.getName(), new String[]{"auton", "grant", k.m.getId()}, base, dark, Alignment.MID, CutStyle.TL_BR, 260f, 22f, 4f);
        }
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (m.isPlayerOwned() || m.getFaction() == null || !m.getFaction().isPlayerFaction() || m.isHidden()) continue;
            boolean legacy = d.autonomous().containsKey(m.getId());
            String why = legacy ? waitReason(d, s, m) : null;
            t.addPara("%s is autonomous (you get %s/month)." + (legacy ? (why == null ? " Taken back today." : " Taken back once " + CALog.esc(why) + ".") : ""),
                    8f, hl, m.getName(), Misc.getDGSCredits(incomeMult() * colonyNet(m)));
            t.addButton("Take back: " + m.getName() + " (" + REVOKE_UNREST + " unrest)", new String[]{"auton", "back", m.getId()}, base, dark,
                    Alignment.MID, CutStyle.TL_BR, 260f, 22f, 4f);
        }
    }
}
