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
 * An emergency is money: net income negative, with under RUNWAY months of it left above the credit reserve. In one, the
 * colony that loses the most is made autonomous the way Nexerelin's "Grant autonomy" dialog does it. Never a colony under
 * NEW_DAYS old, and never for the colony limit: taking a colony back costs it 2 stability, which a new colony can't spare,
 * so going past the limit gets an administrator instead (CAItems.adminsNeeded). Auto acts on Auto colonies; Recommend logs
 * the choice and the Mod features page has a button. Colonies Autopilot made autonomous are taken
 * back once there's no emergency, net income stays positive with them, they fit your colony limit, and they can take the
 * 2 unrest without going below 5. One change every GAP days, and a colony stays autonomous at least HOLD days. Colonies
 * you made autonomous yourself are never touched.
 */
final class CAAutonomy {
    static final String MOD = "nexerelin";
    static final float RUNWAY = 3f, GAP = 30f, HOLD = 60f, NEW_DAYS = 365f;
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
        Long since = d.autonomous().get(m.getId());
        float held = since == null ? HOLD : Global.getSector().getClock().getElapsedDaysSince(since);
        if (held < HOLD) return "autonomous " + (int) held + " of at least " + (int) HOLD + " days";
        if (emergency(s) != null) return "the emergency isn't over";
        float after = CABudget.netIncome(s) + (1f - incomeMult()) * colonyNet(m);
        if (after < 0f) return "net income would be " + Misc.getDGSCredits(after) + " a month with it";
        if (CAItems.overLimit() + 1 > 0) return "it would put you over your colony limit (Autopilot hires an administrator to make room)";
        float floor = Math.max(5f, d.minStability) + REVOKE_UNREST;
        if (m.getStabilityValue() < floor) return "its stability is under " + (int) floor + " (taking it back adds " + REVOKE_UNREST + " unrest)";
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
        if (s.autonomyMode == 0) return;
        tidy(d);
        if (Global.getSector().getClock().getElapsedDaysSince(d.lastAutonomyChange) < GAP) return;
        boolean auto = s.autonomyMode == 2;
        Emergency e = emergency(s);
        if (e != null) {
            Cand k = best(d, e, auto);
            if (k == null) {
                if (CALog.once("autonomy:none:" + Global.getSector().getClock().getMonth()))
                    CALog.add("Emergency (" + e.why + "), but no colony a year old or more loses money, so none is made autonomous", true);
                return;
            }
            if (auto) grant(d, k.m, "emergency: " + e.why, k.saving);
            else if (CALog.once("autonomy:rec:" + k.m.getId()))
                CALog.add("Emergency (" + e.why + "): making " + k.m.getName() + " autonomous would save about " + Misc.getDGSCredits(k.saving)
                        + " a month. Grant autonomy is on the Colony Autopilot intel's Mod features page", true);
            return;
        }
        for (String id : new ArrayList<String>(d.autonomous().keySet())) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null || waitReason(d, s, m) != null) continue;
            if (auto) { reclaim(d, m, "the emergency is over"); return; }
            if (CALog.once("autonomy:back:" + id))
                CALog.add(m.getName() + " can be taken back from autonomy: the emergency is over. Take it back on the Colony Autopilot intel's Mod features page", true);
        }
    }

    static void grant(CAData d, MarketAPI m, String why, float saving) {
        // a hired administrator would be replaced by a local one and keep drawing full salary, so free them first
        AdminData a = CAItems.adminAt(m);
        if (a != null) {
            a.setMarket(null);
            m.setAdmin(Global.getSector().getPlayerPerson());
        }
        Nex_GrantAutonomy.grantAutonomy(m);
        d.clearPending(d.colony(m.getId()));   // nothing can be built there now
        long now = Global.getSector().getClock().getTimestamp();
        d.autonomous().put(m.getId(), now);
        d.lastAutonomyChange = now;
        CALog.forget("autonomy:rec:" + m.getId());
        CALog.add(m.getName() + ": granted autonomy (" + why + ")" + (saving > 0f ? ", saving about " + Misc.getDGSCredits(saving) + " a month" : "")
                + ". You get " + Math.round(incomeMult() * 100f) + " percent of its profit or loss and it doesn't count against your colony limit"
                + (a != null ? "; its administrator " + a.getPerson().getNameString() + " is free for another colony" : "")
                + ". Autopilot takes it back once the emergency is over", true);
    }

    static void reclaim(CAData d, MarketAPI m, String why) {
        Nex_GrantAutonomy.revokeAutonomy(m);   // you govern it again; Nexerelin adds the unrest
        d.autonomous().remove(m.getId());
        d.lastAutonomyChange = Global.getSector().getClock().getTimestamp();
        CALog.forget("autonomy:back:" + m.getId());
        CALog.add(m.getName() + ": taken back from autonomy (" + why + "); Nexerelin adds " + REVOKE_UNREST + " unrest", true);
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
        t.addPara("Mode: %s. In an emergency a colony is made autonomous: you get %s of its profit or loss and it no longer counts against "
                + "your colony limit. Autopilot takes it back once the emergency is over; Nexerelin adds %s unrest for that.", 6f, hl,
                CASettings.AUTONOMY_NAMES[Math.max(0, Math.min(2, s.autonomyMode))], Math.round(incomeMult() * 100f) + " percent", "" + REVOKE_UNREST);
        tidy(d);
        Emergency e = emergency(s);
        if (e == null) t.addPara("No emergency now.", gray, 6f);
        else {
            t.addPara("Emergency: " + CALog.esc(e.why) + ".", Misc.getNegativeHighlightColor(), 6f);
            Cand k = best(d, e, s.autonomyMode == 2);
            if (k == null) t.addPara("Making a colony autonomous wouldn't help" + (s.autonomyMode == 2 ? " (Auto only considers Auto colonies)." : "."), gray, 4f);
            else {
                t.addPara("Best choice: %s, saving about %s a month.", 4f, hl, k.m.getName(), Misc.getDGSCredits(k.saving));
                t.addButton("Grant autonomy", new String[]{"auton", "grant", k.m.getId()}, base, dark, Alignment.MID, CutStyle.ALL, 180f, 22f, 4f);
            }
        }
        for (String id : new ArrayList<String>(d.autonomous().keySet())) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null) continue;
            String why = waitReason(d, s, m);
            t.addPara("%s is autonomous (you get %s a month). " + (why == null ? "It can be taken back now." : "Not taken back yet: " + CALog.esc(why) + "."), 8f, hl,
                    m.getName(), Misc.getDGSCredits(incomeMult() * colonyNet(m)));
            t.addButton("Take it back now", new String[]{"auton", "back", id}, base, dark, Alignment.MID, CutStyle.ALL, 180f, 22f, 4f);
        }
    }
}
