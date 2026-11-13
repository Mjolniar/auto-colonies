package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Surplus research (0.2.29.0): with credits over the surplus line, Vaults of Knowledge's Research Facility goes up at Auto
 * colonies that have no research building, one at a time, until 8 colonies have one (VoK counts 8 at most; each past the
 * first is +10% research speed). Only while the research left is worth it: +10% speed brings every tech still to come
 * forward, worth 0.1 / speed x what they add a month once done (CAResearch's ranking), which must beat a quarter of the new
 * facility's upkeep (surplus credits count a quarter, as everywhere else). Upkeep is 10,000 a month plus 10,000 x the tier
 * of the tech running (VoK charges both on every facility), half once an Alpha core is in, which goes there first
 * (CAItems). Paid from the surplus, outside the monthly budget, within the new-upkeep cap. Worlds with a pre-collapse
 * facility go first, where an Alpha adds a research databank a month. The facilities it built come down once there's
 * nothing left to research. Setting: Settings, Budget and spending.
 * 0.3.0.0 (WS-B3): the planner (CAPlanner) starts them from its one queue (offer): another facility must pay its whole
 * upkeep and its price within the horizon, and takes a colony's construction queue only when nothing better waits there.
 * 0.3.0.0 (WS-C4, research 03 F5): every facility pays tier x 10,000 a month for the same tech, so a tech costs 1.82 times
 * as much with 2 facilities, 3.08 times with 4 and 4.71 times with 8. Below the money tier Idle money another facility's
 * speed must beat its whole upkeep; from Idle money up, half of it (CAEconomy.researchUpkeepWeight). Below Rich, surplus
 * research builds at most 3 facilities of its own (CAEconomy.RESEARCH_CAP); the endgame still builds while research is
 * left. It's part of the money ladder's Economy rung: with that rung Off it builds none, and what it pays from surplus
 * credits is booked there (CAEconomy.spent). legacyPlanner keeps 0.2.45.1's test.
 * 0.3.0.0 fix (C4 review 2): surplus credits are outside the ladder's pool (DECISIONS 14b), so no rung allowance caps a
 * facility's price. It starts one only from the money tier Surplus up with the Economy rung On (CAEconomy.buyerOpen), in
 * the planner's offer and in run()'s own start (the planner stopped for the session). With the rung on Recommend only the
 * summary names the next facility and none starts.
 */
public class CASurplusResearch {
    static final String FACILITY = "aotd_research_facility", BLACK_SITE = "aotd_blacksite";
    /** VoK counts this many colonies with a research building at most (AoTDFactionResearchManager.getAmountOfResearchFacilities). */
    public static final int MAX = 8;
    static final float SPEED = 0.1f, MAINTENANCE = 10000f;
    static final String ACTION = "surplus research";
    public static String summary;

    static boolean has(MarketAPI m) { return m.hasIndustry(FACILITY) || m.hasIndustry(BLACK_SITE); }

    /** Research speed with n colonies counted: +10% for each past the first (VoK), before its research council's skills. */
    static float speed(int n) { return 1f + SPEED * Math.max(0, Math.min(MAX, n) - 1); }

    /** Credits a month another facility's +10% is worth while n colonies have one: research worth `pending` a month comes sooner. */
    static float speedValue(float pending, int n) {
        if (n >= MAX) return 0f;
        return n <= 0 ? 0f : SPEED / speed(n) * Math.max(0f, pending);   // with none, research doesn't run: the first is the plan's
    }

    /** A Research Facility's upkeep while a tech of this tier runs: 10,000 maintenance plus 10,000 x tier. */
    static float upkeep(int tier) { return MAINTENANCE + CAResearch.TIER_UPKEEP * Math.max(0, tier); }

    /** Whether another facility pays: its speed against its upkeep counted at a quarter. */
    static boolean worth(float pending, int n, int tier) { return speedValue(pending, n) > CASurplus.CHEAP * upkeep(tier); }

    static IndustrySpecAPI spec() {
        try { return Global.getSettings().getIndustrySpec(FACILITY); } catch (Throwable t) { return null; }
    }

    /** Whether the colony could start a Research Facility now: unlocked in the build menu and not refused by VoK. */
    static boolean buildable(MarketAPI m, IndustrySpecAPI sp) {
        try {
            if (!CADiagnostics.usable(FACILITY)) return false;
            if (CAAotD.enabled() && !CAAotD.allows(FACILITY, m)) return false;
            Industry probe = sp.getNewPluginInstance(m);
            return probe != null && probe.isAvailableToBuild();
        } catch (Throwable t) { return false; }
    }

    /** Daily. */
    public static void run(List<MarketAPI> auto) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        summary = null;
        if (s.noSurplusResearch || CASurplus.line(s) <= 0f) return;
        List<CAResearch.Tech> ranked;
        try {
            if (CAResearch.mgr() == null) return;   // VoK hasn't made its research manager yet
            ranked = CAResearch.ranked();
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus research couldn't read research", t); return; }
        if (CAResearch.openCount == 0) {
            takeDown(d);
            summary = "Surplus research: nothing is left to research.";
            return;
        }
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (has(m)) n++;
        float pending = 0f;
        int tier = CAResearch.runningTier();
        for (CAResearch.Tech t : ranked) pending += Math.max(0f, t.value);
        if (!ranked.isEmpty()) tier = Math.max(tier, ranked.get(0).tier);
        float up = upkeep(tier), sv = speedValue(pending, n);
        String speedNow = "+" + Math.round((speed(n) - 1f) * 100f) + "% research speed";
        if (n >= MAX) { summary = "Surplus research: " + n + " colonies have a research building, all Vaults of Knowledge counts (" + speedNow + ")."; return; }
        if (n == 0) { summary = "Surplus research: you have no research building yet; the colonies' plans build the first."; return; }
        // 0.3.0.0 (B3): with the planner in charge another facility must pay its whole upkeep and its price within the horizon,
        // ranked with every other build (offer); 0.2.45's own test (a quarter of its upkeep) stays for legacyPlanner
        boolean planner = CAPlanner.inCharge();
        // 0.3.0.0 (C4, research 03 F5): half its upkeep from the money tier Idle money up; below Rich at most RESEARCH_CAP of its own
        int money = CAEconomy.tier(s);
        float weight = CAEconomy.researchUpkeepWeight(money);
        boolean endgame = CAEndgame.on(s) && pending > 0f;
        // 0.3.0.0 fix (C4 review 2): the Economy buyers' one gate (CAEconomy.buyerOpen) for the planner and for run()'s own
        // start. Tight and Off stop here; Recommend only goes on to name the next facility and starts none
        String closed = CAEconomy.closedWhy(s);
        if (closed != null && CAEconomy.economyMode(s) != CAEconomy.RECOMMEND) {
            summary = "Surplus research: none starts (" + closed + "); " + n + " colonies have a research building (" + speedNow + ").";
            return;
        }
        if (planner && CAEconomy.researchCapped(built(d), money, endgame)) {
            summary = "Surplus research: it built " + built(d) + " Research Facilities, the most below the money tier Rich (now " + CARich.name(money) + "); "
                    + n + " colonies have a research building (" + speedNow + ").";
            return;
        }
        boolean pays = planner ? netWorth(pending, n, tier, weight) > 0f : worth(pending, n, tier);
        if (!pays && !endgame) {   // the endgame researches while anything is left
            summary = "Surplus research: another Research Facility's +10% speed is worth about " + Misc.getDGSCredits(sv) + " a month (the research left "
                    + "adds " + CAEngine.valueText(pending) + " once done); it has to beat " + (!planner ? "a quarter of its " : weight < 1f ? "half its " : "its ")
                    + Misc.getDGSCredits(up) + " upkeep. " + n + " colonies have one (" + speedNow + ").";
            return;
        }
        IndustrySpecAPI sp = spec();
        if (sp == null) return;
        List<MarketAPI> cands = candidates(auto, d, sp);
        String head = "Surplus research: " + n + " of " + MAX + " colonies have a research building (" + speedNow + "); another's +10% is worth about "
                + Misc.getDGSCredits(sv) + " a month for " + Misc.getDGSCredits(up) + " upkeep";
        if (cands.isEmpty()) { summary = head + ", but no Auto colony without one can build it now."; return; }
        MarketAPI m = null;
        for (MarketAPI c : cands) if (Misc.getCurrentlyBeingConstructed(c) == null) { m = c; break; }   // one new building at a time per colony
        if (m == null) { summary = head + "; the colonies that could build it are busy building something else."; return; }
        float cost;
        try { cost = sp.getNewPluginInstance(m).getBuildCost(); } catch (Throwable t) { cost = sp.getCost(); }
        float fee = CABudget.constructionFee(s, cost), budget = CASurplus.amount(s);
        if (closed != null) {   // 0.3.0.0 fix (C4 review 2): Recommend only: what would be bought, and none starts
            summary = head + "; the next would go up at " + m.getName() + " for " + Misc.getDGSCredits(cost + fee) + ", but none starts: " + closed + ".";
            return;
        }
        if (planner) {
            // 0.3.0.0 (B3 fix): the money wait stays here, as in 0.2.45.1, not one on-screen line per candidate colony (CAPlanner.logWait)
            summary = head + (cost + fee > budget ? "; the next goes up at " + m.getName() + " once the surplus covers " + Misc.getDGSCredits(cost + fee) + "."
                    : "; the next goes up at " + m.getName() + " or another candidate when the planner's queue gets to it.");
            return;
        }
        String key = "srs:" + m.getId();
        if (cost + fee > budget) {
            summary = head + "; the next goes up at " + m.getName() + " once the surplus covers " + Misc.getDGSCredits(cost + fee) + ".";
            return;
        }
        if (s.upkeepAdded + up > CASurplus.upkeepCap(m, s)) {
            summary = head + "; the next, at " + m.getName() + ", waits for next month's new-upkeep room.";
            if (CALog.once(key + ":upkeep")) CALog.think(m.getName() + ": Surplus Research Facility waits for next month's upkeep room.");
            return;
        }
        CAEngine.Pick p = new CAEngine.Pick();
        p.id = FACILITY; p.name = sp.getName(); p.verb = "Build"; p.cost = cost; p.upkeep = up;
        String err = CABuild.start(m, p);
        if (err != null) {
            summary = head + "; " + m.getName() + " couldn't start it: " + err + ".";
            if (CALog.once(key + ":" + err)) CALog.think(m.getName() + ": Research Facility not started: " + err);
            return;
        }
        CABudget.charge(s, 0f, fee, up);   // the price came from the surplus (CABuild.start took it), outside the monthly budget
        CAEconomy.spent(cost);   // 0.3.0.0 fix (C4 review): booked on the Economy rung, as the planner's start does
        d.surplusResearch().add(m.getId());
        summary = head + "; building one at " + m.getName() + ".";
        CALog.did(m.getName() + ": Research Facility started (+10% research speed). " + Misc.getDGSCredits(cost) + ", " + Misc.getDGSCredits(up) + "/month.");
    }

    // ---------------- 0.3.0.0 (WS-B3): the planner's actions ----------------

    /** The planner's name for these actions. */
    static final String SOURCE = "surplus research";

    /** 0.3.0.0 (B3): what another facility adds a month: its +10% speed less its whole upkeep (no surplus discount: the horizon replaced it, B2). */
    static float netWorth(float pending, int n, int tier) { return speedValue(pending, n) - upkeep(tier); }

    /** 0.3.0.0 (C4, research 03 F5): as above, with its upkeep counted at this weight (CAEconomy.researchUpkeepWeight: 1, or half from Idle money up). */
    static float netWorth(float pending, int n, int tier, float upkeepWeight) { return speedValue(pending, n) - upkeepWeight * upkeep(tier); }

    /** 0.3.0.0 (C4): Research Facilities surplus research built that are still there (CAEconomy.RESEARCH_CAP counts these). */
    static int built(CAData d) {
        int k = 0;
        for (String id : d.surplusResearch()) {
            MarketAPI m = CAAuto.market(id);
            if (m != null && m.isPlayerOwned() && m.hasIndustry(FACILITY)) k++;
        }
        return k;
    }

    /** The Auto colonies that could build one now, worlds with a pre-collapse facility first (an Alpha there adds a databank a month), then the biggest. */
    static List<MarketAPI> candidates(List<MarketAPI> auto, CAData d, IndustrySpecAPI sp) {
        List<MarketAPI> cands = new ArrayList<MarketAPI>();
        for (MarketAPI m : auto) {
            if (has(m) || CAHypershunt.is(m) || CAEngine.isOutpost(m) || m.hasTag("market_no_industries_allowed")) continue;
            if (d.colony(m.getId()).locks.contains(FACILITY) || !buildable(m, sp)) continue;
            cands.add(m);
        }
        Collections.sort(cands, new Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) {
                boolean pa = a.hasCondition("pre_collapse_facility"), pb = b.hasCondition("pre_collapse_facility");
                if (pa != pb) return pa ? -1 : 1;
                return b.getSize() - a.getSize();
            }
        });
        return cands;
    }

    /**
     * 0.3.0.0 (WS-B3, research 02 sections 13 and 14, 01 section 3 item 9): another Research Facility as an action at each
     * candidate colony, ranked in the planner's one queue with every other build: it takes the colony's construction queue,
     * must pay back its price, fee and whole upkeep within the horizon (or the endgame's rule: research while anything is
     * left), and one flag lets only one start a pass (the build engine's own Research Facility pick shares it). Paid from the
     * surplus, only its fee against the month's budget, as in 0.2.45.1. run() keeps the summary and the clean-up. 0.3.0.0 (B3 fix):
     * the candidates' waits share one once-key (CAPlanner.RESEARCH_WAIT): the money wait goes in run()'s summary and
     * starsector.log, an upkeep wait shows once on screen, as 0.2.45.1 did, not one line per candidate colony.
     */
    public static List<CAAction> offer(List<MarketAPI> auto) {
        List<CAAction> out = new ArrayList<CAAction>();
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (s.noSurplusResearch || CASurplus.line(s) <= 0f) return out;
        List<CAResearch.Tech> ranked;
        try {
            if (CAResearch.mgr() == null) return out;
            ranked = CAResearch.ranked();
        } catch (Throwable t) { return out; }   // run() logs it
        if (CAResearch.openCount == 0) return out;
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (has(m)) n++;
        if (n >= MAX || n == 0) return out;   // all VoK counts; or none yet, which the plans build first
        float pending = 0f;
        int tier = CAResearch.runningTier();
        for (CAResearch.Tech t : ranked) pending += Math.max(0f, t.value);
        if (!ranked.isEmpty()) tier = Math.max(tier, ranked.get(0).tier);
        boolean endgame = CAEndgame.on(s) && pending > 0f;
        // 0.3.0.0 (C4, research 03 F5): below Rich at most RESEARCH_CAP of its own; its upkeep counts half from the money tier
        // Idle money up. 0.3.0.0 fix (C4 review 2): none from the Economy rung below the tier Surplus, Off or Recommend only
        // (CAEconomy.buyerOpen, as run() and the other Economy buyers)
        int money = CAEconomy.tier(s);
        if (!CAEconomy.buyerOpen(s) || CAEconomy.researchCapped(built(d), money, endgame)) return out;
        float monthly = netWorth(pending, n, tier, CAEconomy.researchUpkeepWeight(money)), up = upkeep(tier);
        if (monthly <= 0f && !endgame) return out;
        IndustrySpecAPI sp = spec();
        if (sp == null) return out;
        float h = CAHorizon.months();
        for (MarketAPI m : candidates(auto, d, sp)) {
            float cost, days;
            try { Industry inst = sp.getNewPluginInstance(m); cost = inst.getBuildCost(); days = inst.getBuildTime(); }
            catch (Throwable t) { cost = sp.getCost(); days = sp.getBuildTime(); }
            CAAction a = new CAAction();
            a.kind = CAAction.Kind.BUILD; a.source = SOURCE; a.market = m; a.marketId = m.getId();
            a.id = FACILITY; a.name = sp.getName(); a.why = "+10% research speed";
            a.cost = cost; a.fee = CABudget.constructionFee(s, cost); a.days = days; a.upkeep = up;
            a.worth = monthly;
            a.npv = CAValue.npv(monthly, h, days, cost, a.fee);
            a.byRule = endgame;
            a.usesQueue = true;
            a.once = CAPlanner.ONCE_RESEARCH;
            a.tombstone = CAPlanner.tombstoneDays(m.getId(), FACILITY);
            a.fromBudget = false; a.fromSurplus = true; a.chargeAll = false;
            a.upkeepCap = CASurplus.upkeepCap(m, s);
            a.starter = (x) -> start(x);
            out.add(a);
        }
        return out;
    }

    /** Starts one for the planner: paid from the surplus (CABuild.start takes the price), the fee against the month's budget. */
    static String start(CAAction a) {
        MarketAPI m = a.market;
        CASettings s = CAData.settings();
        CAEngine.Pick p = new CAEngine.Pick();
        p.id = FACILITY; p.name = a.name; p.verb = "Build"; p.cost = a.cost; p.upkeep = a.upkeep;
        String err = CABuild.start(m, p);
        if (err != null) {
            if (CALog.once("srs:" + m.getId() + ":" + err)) CALog.think(m.getName() + ": Research Facility not started: " + err);
            return err;
        }
        CABudget.charge(s, m.getId(), 0f, a.fee, a.upkeep);
        CAEconomy.spent(a.cost);   // 0.3.0.0 (C4): the price came from surplus credits: the money ladder's Economy rung this month
        CAData.get().surplusResearch().add(m.getId());
        CALog.did(m.getName() + ": Research Facility started (+10% research speed). " + Misc.getDGSCredits(a.cost) + ", " + Misc.getDGSCredits(a.upkeep) + "/month.");
        return null;
    }

    /** Takes down the Research Facilities surplus research built: nothing is left to research. Ones you upgraded or locked stay. */
    static void takeDown(CAData d) {
        for (String id : new ArrayList<String>(d.surplusResearch())) {
            MarketAPI m = CAAuto.market(id);
            Industry ind = m == null ? null : m.getIndustry(FACILITY);
            if (ind == null || !m.isPlayerOwned()) { d.surplusResearch().remove(id); continue; }   // gone, upgraded to a Black Site, or lost
            if (ind.isBuilding() || ind.isUpgrading()) continue;
            if (d.colony(m.getId()).locks.contains(FACILITY)) { d.surplusResearch().remove(id); continue; }   // yours now
            float up = ind.getUpkeep().getModifiedValue();
            m.removeIndustry(FACILITY, MarketAPI.MarketInteractionMode.REMOTE, false);   // with a mode, VoK moves its tab's cargo to Storage
            CATombstone.add(m.getId(), FACILITY, CATombstone.RESEARCH, CATombstone.sunk(m.getId(), FACILITY));   // 0.3.0.0 (B4): every removal leaves a year's mark
            d.surplusResearch().remove(id);
            CALog.did(m.getName() + ": Research Facility removed (-" + Misc.getDGSCredits(up) + "/month). Research done.");
        }
    }
}
