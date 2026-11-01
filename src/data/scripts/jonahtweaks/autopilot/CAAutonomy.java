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
 * Emergency autonomy (Nexerelin; setting "Emergency autonomy", off until set). Nexerelin lets a colony be run autonomously:
 * you get 25% of its profit or loss (nex_autonomousIncomeMult), it no longer counts against your colony limit, and nothing
 * can be built there; taking it back adds 2 unrest (Nex_GrantAutonomy).
 *
 * Autonomy is the player's call: Autopilot never grants it (since 2.17.0). In a money emergency (net income negative, with
 * under RUNWAY months of it left above the credit reserve) Recommend logs the colony a year old or more (NEW_DAYS) that loses
 * the most, and the Mod features page's Grant autonomy button does it for you; colonies you make autonomous, by that button
 * or Nexerelin's dialog, are yours and never taken back. Going past the colony limit gets an administrator instead
 * (CAItems.adminsNeeded). Colonies an older Autopilot made autonomous (CAData.autonomous) are taken back, one a day, as
 * soon as there's no money emergency, taking one back wouldn't start one, and there's room under your colony limit or an
 * idle administrator to put there (installed in the same step), with Nexerelin's own no-unrest flag
 * (Nex_GrantAutonomy.setNoStabLossOnRevokeAutonomy), since they were never your choice.
 */
final class CAAutonomy {
    static final String MOD = "nexerelin";
    static final float RUNWAY = 3f, NEW_DAYS = 365f;
    static final int REVOKE_UNREST = 2;   // Nex_GrantAutonomy.REVOKE_UNREST

    private CAAutonomy() { }

    static final class Emergency {
        boolean money;
        String why;
    }

    static final class Cand {
        MarketAPI m;
        float saving;   // credits a month: money only in a money emergency, money and stability in a governance one
    }

    static float incomeMult() {
        try { return Global.getSettings().getFloat("nex_autonomousIncomeMult"); } catch (Throwable t) { return 0.25f; }
    }

    /** A colony's monthly profit or loss as Nexerelin counts it for an autonomous colony. */
    static float colonyNet(MarketAPI m) { return m.getIndustryIncome() + m.getExportIncome(false) - m.getIndustryUpkeep(); }

    static boolean moneyEmergency(float net, float credits, float reserve) { return net < 0f && credits - reserve < RUNWAY * -net; }

    /** Credits a month you stop gaining (or losing) when a colony with this net income becomes autonomous. */
    static float moneySaved(float colonyNet, float mult) { return -(1f - mult) * colonyNet; }

    /** A money emergency, or null. Going past the colony limit isn't one: that gets an administrator (CAItems.adminsNeeded). */
    static Emergency emergency(CASettings s) {
        float net = CABudget.netIncome(s);
        if (!moneyEmergency(net, CABuild.credits(), s.reserve)) return null;
        Emergency e = new Emergency();
        e.money = true;
        e.why = "net income " + Misc.getDGSCredits(net) + " a month, with under " + (int) RUNWAY + " months of it left above the credit reserve";
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

    /** The colony whose autonomy saves the most in this emergency (one that loses money, a year old or more), or null. */
    static Cand best(CAData d, Emergency e, boolean autoOnly) {
        float mult = incomeMult();
        Cand best = null;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!eligible(d, m, autoOnly)) continue;
            float v = moneySaved(colonyNet(m), mult);
            if (v <= 0f) continue;
            if (best == null || v > best.saving) { best = new Cand(); best.m = m; best.saving = v; }
        }
        return best;
    }

    /** What keeps a colony Autopilot made autonomous from being taken back, or null if nothing does. */
    static String waitReason(CAData d, CASettings s, MarketAPI m) {
        if (emergency(s) != null) return "the money emergency isn't over";
        float after = CABudget.netIncome(s) + (1f - incomeMult()) * colonyNet(m);
        if (moneyEmergency(after, CABuild.credits(), s.reserve)) return "net income would be " + Misc.getDGSCredits(after) + " a month with it, a money emergency";
        if (CAItems.overLimit() + 1 > 0 && CAItems.idleAdmin(d) == null)
            return "it would put you over your colony limit; Autopilot is getting an administrator to make room";
        return null;
    }

    /** Forgets colonies that were taken back by hand, lost, or are gone. */
    static void tidy(CAData d) {
        for (String id : new ArrayList<String>(d.autonomous().keySet())) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null || m.isPlayerOwned() || m.getFaction() == null || !m.getFaction().isPlayerFaction()) d.autonomous().remove(id);
        }
    }

    public static void run(CAData d, CASettings s) {
        tidy(d);
        // colonies an older Autopilot made autonomous come back as soon as they can, one a day, whatever the setting
        for (String id : new ArrayList<String>(d.autonomous().keySet())) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null || waitReason(d, s, m) != null) continue;
            reclaim(d, m, "there's room for it");
            return;
        }
        if (s.autonomyMode == 0) return;
        Emergency e = emergency(s);
        if (e == null) return;
        Cand k = best(d, e, false);
        if (k == null) {
            if (CALog.once("autonomy:none:" + Global.getSector().getClock().getMonth()))
                CALog.add("Emergency (" + e.why + "), and no colony a year old or more loses money, so autonomy wouldn't help", true);
            return;
        }
        if (CALog.once("autonomy:rec:" + k.m.getId()))
            CALog.add("Emergency (" + e.why + "): making " + k.m.getName() + " autonomous would save about " + Misc.getDGSCredits(k.saving)
                    + " a month. That's your call: Grant autonomy is on the Colony Autopilot intel's Mod features page", true);
    }

    /** Your order from the Mod features page. The colony is yours from then on: Autopilot doesn't track it or take it back. */
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
        CALog.add(m.getName() + ": granted autonomy (" + why + ")" + (saving > 0f ? ", saving about " + Misc.getDGSCredits(saving) + " a month" : "")
                + ". You get " + Math.round(incomeMult() * 100f) + " percent of its profit or loss and it doesn't count against your colony limit"
                + (a != null ? "; its administrator " + a.getPerson().getNameString() + " is free for another colony" : "")
                + ". It's yours to take back", true);
    }

    static void reclaim(CAData d, MarketAPI m, String why) {
        Nex_GrantAutonomy.setNoStabLossOnRevokeAutonomy(m);   // Autopilot made it autonomous, not you: no unrest for taking it back
        Nex_GrantAutonomy.revokeAutonomy(m);
        d.autonomous().remove(m.getId());
        d.lastAutonomyChange = Global.getSector().getClock().getTimestamp();
        CALog.forget("autonomy:back:" + m.getId());
        // past the colony limit now: the idle administrator that made room for it governs it
        AdminData a = CAItems.overLimit() > 0 ? CAItems.idleAdmin(d) : null;
        if (a != null) {
            m.setAdmin(a.getPerson());
            a.setMarket(m);
        }
        CALog.add(m.getName() + ": taken back from autonomy (" + why + "), without unrest since Autopilot made it autonomous"
                + (a != null ? ". Administrator " + a.getPerson().getNameString() + " governs it" : ""), true);
    }

    /** Buttons on the Mod features page: grant autonomy to a colony, or take one back now. */
    static void handle(CAData d, String action, String marketId) {
        MarketAPI m = Global.getSector().getEconomy().getMarket(marketId);
        if (m == null || m.getFaction() == null || !m.getFaction().isPlayerFaction()) return;
        if (action.equals("grant") && m.isPlayerOwned()) grant(d, m, "your order", 0f);
        else if (action.equals("back") && !m.isPlayerOwned()) reclaim(d, m, "your order");
    }

    /** The Emergency autonomy section of the empire panel's Mod features page. */
    static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        t.addSectionHeading("Emergency autonomy (Nexerelin)", Alignment.MID, 0f);
        t.addPara("Mode: %s. Autonomy is your call: an autonomous colony gives you %s of its profit or loss and no longer counts against "
                + "your colony limit; taking it back costs %s unrest. Autopilot never grants it. In a money emergency it names the colony "
                + "that would save the most.", 6f, hl,
                CASettings.AUTONOMY_NAMES[Math.max(0, Math.min(2, s.autonomyMode))], Math.round(incomeMult() * 100f) + " percent", "" + REVOKE_UNREST);
        tidy(d);
        Emergency e = emergency(s);
        if (e == null) t.addPara("No emergency now.", gray, 6f);
        else {
            t.addPara("Emergency: " + CALog.esc(e.why) + ".", Misc.getNegativeHighlightColor(), 6f);
            Cand k = best(d, e, false);
            if (k == null) t.addPara("No colony a year old or more loses money, so autonomy wouldn't help.", gray, 4f);
            else {
                t.addPara("Best choice: %s, saving about %s a month.", 4f, hl, k.m.getName(), Misc.getDGSCredits(k.saving));
                t.addButton("Grant autonomy", new String[]{"auton", "grant", k.m.getId()}, base, dark, Alignment.MID, CutStyle.ALL, 180f, 22f, 4f);
            }
        }
        for (String id : new ArrayList<String>(d.autonomous().keySet())) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null) continue;
            String why = waitReason(d, s, m);
            t.addPara("%s was made autonomous by an older Autopilot (you get %s a month). " + (why == null ? "It's taken back today, without unrest."
                    : "Taken back without unrest once it can: " + CALog.esc(why) + "."), 8f, hl,
                    m.getName(), Misc.getDGSCredits(incomeMult() * colonyNet(m)));
            t.addButton("Take it back now", new String[]{"auton", "back", id}, base, dark, Alignment.MID, CutStyle.ALL, 180f, 22f, 4f);
        }
    }
}
