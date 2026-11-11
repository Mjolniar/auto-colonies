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
        if (!worth(pending, n, tier) && !(CAEndgame.on(s) && pending > 0f)) {   // the endgame researches while anything is left
            summary = "Surplus research: another Research Facility's +10% speed is worth about " + Misc.getDGSCredits(sv) + " a month (the research left "
                    + "adds " + CAEngine.valueText(pending) + " once done), not a quarter of its " + Misc.getDGSCredits(up) + " upkeep; " + n
                    + " colonies have one (" + speedNow + ").";
            return;
        }
        IndustrySpecAPI sp = spec();
        if (sp == null) return;
        List<MarketAPI> cands = new ArrayList<MarketAPI>();
        for (MarketAPI m : auto) {
            if (has(m) || CAHypershunt.is(m) || CAEngine.isOutpost(m) || m.hasTag("market_no_industries_allowed")) continue;
            if (d.colony(m.getId()).locks.contains(FACILITY) || !buildable(m, sp)) continue;
            cands.add(m);
        }
        Collections.sort(cands, new Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) {
                boolean pa = a.hasCondition("pre_collapse_facility"), pb = b.hasCondition("pre_collapse_facility");
                if (pa != pb) return pa ? -1 : 1;   // an Alpha there adds a research databank a month
                return b.getSize() - a.getSize();
            }
        });
        String head = "Surplus research: " + n + " of " + MAX + " colonies have a research building (" + speedNow + "); another's +10% is worth about "
                + Misc.getDGSCredits(sv) + " a month for " + Misc.getDGSCredits(up) + " upkeep";
        if (cands.isEmpty()) { summary = head + ", but no Auto colony without one can build it now."; return; }
        MarketAPI m = null;
        for (MarketAPI c : cands) if (Misc.getCurrentlyBeingConstructed(c) == null) { m = c; break; }   // one new building at a time per colony
        if (m == null) { summary = head + "; the colonies that could build it are busy building something else."; return; }
        float cost;
        try { cost = sp.getNewPluginInstance(m).getBuildCost(); } catch (Throwable t) { cost = sp.getCost(); }
        float fee = CABudget.constructionFee(s, cost), budget = CASurplus.amount(s);
        String key = "srs:" + m.getId();
        if (cost + fee > budget) {
            summary = head + "; the next goes up at " + m.getName() + " once the surplus covers " + Misc.getDGSCredits(cost + fee) + ".";
            return;
        }
        if (s.upkeepAdded + up > CASurplus.upkeepCap(m, s)) {
            summary = head + "; the next, at " + m.getName() + ", waits for next month's new-upkeep room.";
            if (CALog.once(key + ":upkeep")) CALog.add(m.getName() + ": surplus Research Facility waits for next month's upkeep room");
            return;
        }
        CAEngine.Pick p = new CAEngine.Pick();
        p.id = FACILITY; p.name = sp.getName(); p.verb = "Build"; p.cost = cost; p.upkeep = up;
        String err = CABuild.start(m, p);
        if (err != null) {
            summary = head + "; " + m.getName() + " couldn't start it: " + err + ".";
            if (CALog.once(key + ":" + err)) CALog.add(m.getName() + ": Research Facility not started: " + err);
            return;
        }
        CABudget.charge(s, 0f, fee, up);   // the price came from the surplus (CABuild.start took it), outside the monthly budget
        d.surplusResearch().add(m.getId());
        summary = head + "; building one at " + m.getName() + ".";
        CALog.add(m.getName() + ": Research Facility started (+10% research speed), " + Misc.getDGSCredits(cost) + ", " + Misc.getDGSCredits(up) + "/month");
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
            d.surplusResearch().remove(id);
            CALog.add(m.getName() + ": Research Facility removed, research done (-" + Misc.getDGSCredits(up) + "/month)");
        }
    }
}
