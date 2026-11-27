package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.32.0 (plan 2026-11-25): the Garden of Elysium, ask first (Approvals, kind "wonder"), while Wonders and gates is on and
 * spending freely (CAWonders.on), as the Daedalus Array. Host: an Auto colony that meets its requirements (bountiful
 * farmland, habitable, no pollution) with its research done and a Grand Wonder slot free; a colony with a farm first, then
 * the biggest. After your approval a Grand Wonder site goes in the planner's queue; once it's built, the Garden is put on
 * it. When the Garden finishes, Vaults of Knowledge removes the colony's Monoculture Plots and Resort Center trees and their
 * AI cores and items are lost, so LEAVE_DAYS before it finishes they go to Autopilot Storage and nothing is put back in
 * (watch, at every colony Autopilot manages, a Garden you started too).
 */
public final class CAGarden {
    private CAGarden() { }

    static final String GARDEN = "aotd_garden_of_elysium";
    /** CAMark keys: your approval (also the ask's key), and a Grand Wonder site Autopilot built for the Garden. */
    static final String OK_MARK = "wonderok:" + GARDEN, SITE_MARK = "gardensite:";
    /** The trees the Garden replaces (GardensOfElysium.finishedConstruction). */
    static final String[] ROOTS = {"aotd_monoculture_plots", "aotd_resort_center"};
    /** Days before the Garden finishes that the cores and items of what it replaces go to Autopilot Storage. */
    static final float LEAVE_DAYS = 3f;
    static final String TREE = "data.kaysaar.aotd.vok.campaign.econ.synergies.IndustrySynergiesMisc";

    /** Buildings a Garden replaces within LEAVE_DAYS: "marketId" -> industry ids (watch, daily). */
    static final Map<String, Set<String>> LEAVING = new HashMap<String, Set<String>>();

    static boolean broken = false;

    static void reset() { LEAVING.clear(); broken = false; }

    /** The Wonders step and the planner's offer; a missing class or method turns the Garden off for the session. */
    static void run(CASettings s, List<String> lines, List<CAAction> offers, boolean act) {
        if (broken) return;
        try { step(s, lines, offers, act); }
        catch (Throwable t) {
            boolean lookup = t instanceof ClassNotFoundException || t instanceof NoSuchMethodException || t instanceof NoSuchFieldException || t instanceof LinkageError;
            if (lookup) { broken = true; CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read the Garden of Elysium; it's left to you", t); }
            else if (offers == null) CAStep.warn("wonders", "the Garden of Elysium step failed today", t);
        }
    }

    // ---------------- pure ----------------

    /** Pure: the better host: a colony with a farm first, then the bigger one. */
    static boolean better(boolean farmA, int sizeA, boolean farmB, int sizeB) { return farmA != farmB ? farmA : sizeA > sizeB; }

    /** Pure: the Garden is close enough to finishing that what it replaces is emptied. */
    static boolean leaving(float daysLeft) { return daysLeft >= 0f && daysLeft <= LEAVE_DAYS; }

    // ---------------- the game ----------------

    /** A building the Garden replaces within LEAVE_DAYS: no item or core is put in it. */
    public static boolean leaving(MarketAPI m, Industry ind) {
        Set<String> s = m == null || ind == null ? null : LEAVING.get(m.getId());
        return s != null && s.contains(ind.getId());
    }

    /** The ids of the trees the Garden replaces: VoK's own tree helper, else each root and what's upgraded from it. */
    @SuppressWarnings("unchecked")
    static Set<String> treeIds(MarketAPI m) {
        Set<String> out = new HashSet<String>();
        for (String root : ROOTS) {
            out.add(root);
            try {
                Object l = CARefl.invoke(CARefl.method(Class.forName(TREE, true, CAWonders.cl()), "getIdsOfTreeFromIndustry", String.class), null, root);
                if (l instanceof Collection) for (Object o : (Collection<Object>) l) out.add(String.valueOf(o));
            } catch (Throwable ignored) { }
            for (Industry ind : m.getIndustries()) if (CAPlan.leadsTo(root, ind.getId())) out.add(ind.getId());
        }
        return out;
    }

    static List<Industry> replaced(MarketAPI m) {
        Set<String> ids = treeIds(m);
        List<Industry> out = new ArrayList<Industry>();
        for (Industry ind : m.getIndustries()) if (ids.contains(ind.getId())) out.add(ind);
        return out;
    }

    /**
     * Daily, before the Wonders step's money gate: Gardens about to finish at your colonies; their farms and resorts lose
     * their cores and items to Autopilot Storage.
     */
    static void watch() {
        LEAVING.clear();
        if (Global.getSettings().getIndustrySpec(GARDEN) == null) return;
        try {
            Class<?> siteC = Class.forName(CAWonders.TOT + "industries.AoTDConstructionSite", true, CAWonders.cl());
            Object assigned = CARefl.method(siteC, "getAssignedWonder"), daysLeft = CARefl.method(siteC, "getDaysLeft");
            for (MarketAPI m : CAItems.autoMarkets()) {
                for (Industry ind : m.getIndustries()) {
                    if (!siteC.isInstance(ind) || !GARDEN.equals(CARefl.invoke(assigned, ind))) continue;
                    if (!leaving(((Number) CARefl.invoke(daysLeft, ind)).floatValue())) continue;
                    Set<String> ids = new HashSet<String>();
                    for (Industry r : replaced(m)) ids.add(r.getId());
                    LEAVING.put(m.getId(), ids);
                    pull(m);
                }
            }
        } catch (Throwable t) { CAStep.warn("wonders", "the Garden of Elysium check failed today", t); }
    }

    static void pull(MarketAPI m) {
        CargoAPI to = CAStores.holds(m) ? CAStores.of(m) : CAStores.nearest(m).isEmpty() ? null : CAStores.nearest(m).get(0).cargo;
        if (to == null) return;
        List<String> moved = new ArrayList<String>();
        for (Industry ind : replaced(m)) {
            String core = ind.getAICoreId();
            if (core != null) { to.addCommodity(core, 1); ind.setAICoreId(null); moved.add(CAItems.coreName(Math.max(0, CAItems.tier(core)))); }
            SpecialItemData item = ind.getSpecialItem();
            if (item != null) { to.addSpecial(item, 1); ind.setSpecialItem(null); moved.add(CAItems.name(item.getId())); }
        }
        if (!moved.isEmpty()) CALog.did(m.getName() + ": " + CAContracts.join(moved) + " moved to Autopilot Storage before the Garden of Elysium replaces the farms and resorts.");
    }

    /**
     * The Wonders step (CAWonders.run, act) and the planner's offer (offers): the Garden's site at its host after your
     * approval, then the Garden on a site Autopilot built for it.
     */
    @SuppressWarnings("unchecked")
    static void step(CASettings s, List<String> lines, List<CAAction> offers, boolean act) throws Exception {
        IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(GARDEN);
        if (spec == null || Global.getSettings().getIndustrySpec(CAWonders.SITE) == null) return;
        Class<?> mgrC = Class.forName(CAWonders.GW + "GrandWonderManager", true, CAWonders.cl()), apiC = Class.forName(CAWonders.GW + "GrandWonderAPI", true, CAWonders.cl());
        Class<?> siteC = Class.forName(CAWonders.TOT + "industries.AoTDConstructionSite", true, CAWonders.cl());
        Object mgr = CARefl.invoke(CARefl.method(mgrC, "getInstance"), null);
        int built = ((Number) CARefl.invoke(CARefl.method(mgrC, "getBuiltSoFar", String.class), mgr, GARDEN)).intValue();
        Object assigned = CARefl.method(siteC, "getAssignedWonder");
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m.hasIndustry(GARDEN)) built++;
            for (Industry ind : m.getIndustries()) if (siteC.isInstance(ind) && GARDEN.equals(CARefl.invoke(assigned, ind))) built++;
        }
        if (built > 0) { lines.add(CAWonders.name(GARDEN) + " built or under way"); return; }
        Object slots = CARefl.method(mgrC, "canBuiltAdditionalWonderDueToSlots", MarketAPI.class);
        Object reqs = CARefl.method(apiC, "getRequirementsToBuildWonder"), met = CARefl.method(apiC, "hasReqBeenMetOnMarket", String.class);
        Object shown = CARefl.method(apiC, "shouldShowInListOfWonders", MarketAPI.class), demand = CARefl.method(apiC, "getDemandCostForRestoration");
        Object assign = CARefl.method(siteC, "setAssignedWonder", String.class);
        MarketAPI host = null;
        Industry hostSite = null, hostWonder = null;
        boolean hostFarm = false;
        // 0.3.33.1: the colony with the site Autopilot built for the Garden is the only host, so a second site is never built
        MarketAPI marked = null;
        for (MarketAPI x : Misc.getPlayerMarkets(false)) {
            if (CAMark.at(SITE_MARK + x.getId()) == null) continue;
            for (Industry ind : x.getIndustries()) if (siteC.isInstance(ind) || CAWonders.SITE.equals(ind.getId())) marked = x;
            if (marked != null) break;
        }
        for (MarketAPI m : CAItems.autoMarkets()) {
            if (marked != null && m != marked) continue;
            if (!CASurplus.spends(m) || CASiege.held(m) || CAHypershunt.is(m)) continue;
            Industry w = spec.getNewPluginInstance(m);
            if (!apiC.isInstance(w) || !w.isAvailableToBuild() || !Boolean.TRUE.equals(CARefl.invoke(shown, w, m))) continue;   // research done
            boolean ok = true;
            Object rq = CARefl.invoke(reqs, w);
            if (rq instanceof Map) for (Object k : ((Map<Object, Object>) rq).keySet()) if (!Boolean.TRUE.equals(CARefl.invoke(met, w, String.valueOf(k)))) ok = false;
            if (!ok || (CAAotD.enabled() && !CAAotD.allows(GARDEN, m))) continue;
            Industry site = null;
            for (Industry ind : m.getIndustries()) if (siteC.isInstance(ind)) site = ind;
            if (site != null && (CARefl.invoke(assigned, site) != null || CAMark.at(CAWonders.SITE_MARK + m.getId()) != null)) continue;   // another wonder's site
            if (site == null && !Boolean.TRUE.equals(CARefl.invoke(slots, null, m))) continue;
            boolean farm = false;
            for (Industry ind : m.getIndustries()) if (ind.getId().equals(ROOTS[0]) || CAPlan.leadsTo(ROOTS[0], ind.getId())) farm = true;
            if (host == null || better(farm, m.getSize(), hostFarm, host.getSize())) { host = m; hostSite = site; hostWonder = w; hostFarm = farm; }
        }
        if (host == null) return;
        MarketAPI m = host;
        List<String> missing = CAWonders.unmade((Map<?, ?>) CARefl.invoke(demand, hostWonder));
        if (!missing.isEmpty()) {
            lines.add(CAWonders.name(GARDEN) + " at " + m.getName() + " waits for " + CAContracts.join(missing));
            return;
        }
        if (hostSite != null) {
            if (hostSite.isBuilding()) { lines.add(CAWonders.name(GARDEN) + "'s construction site at " + m.getName() + " is being built"); return; }
            if (!act || CAMark.at(SITE_MARK + m.getId()) == null || CAData.get().colony(m.getId()).locks.contains(hostSite.getId())) return;   // a site you built is yours
            CARefl.invoke(assign, hostSite, GARDEN);
            CALog.did(m.getName() + ": " + CAWonders.name(GARDEN) + " construction started. It replaces the farms and resorts when it's done.", true);
            lines.add(CAWonders.name(GARDEN) + " started at " + m.getName());
            return;
        }
        if (CABuild.buildsFull(m)) { lines.add(CAWonders.name(GARDEN) + " at " + m.getName() + " waits for the current construction"); return; }
        IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(CAWonders.SITE);
        Industry probe = sp.getNewPluginInstance(m);
        if (!probe.isAvailableToBuild()) return;
        if (!approved(m, probe.getBuildCost(), (Map<?, ?>) CARefl.invoke(demand, hostWonder), lines)) return;
        if (offers == null) { lines.add("construction site for the " + CAWonders.name(GARDEN) + " at " + m.getName() + " waits its turn in the build queue"); return; }
        if (!CARich.wondersFit(s, probe.getBuildCost())) { lines.add(CAWonders.name(GARDEN) + " at " + m.getName() + " waits for the Wonders rung's share of the pool"); return; }
        CAAction a = new CAAction();
        a.kind = CAAction.Kind.BUILD; a.source = CAWonders.SOURCE; a.market = m; a.marketId = m.getId();
        a.id = CAWonders.SITE; a.name = sp.getName(); a.why = "for the " + CAWonders.name(GARDEN);
        a.cost = probe.getBuildCost(); a.fee = CABudget.constructionFee(s, a.cost); a.days = probe.getBuildTime();
        a.rapid = CABudget.rapidFee(a.cost);
        a.upkeep = CAEngine.baseUpkeep(m, sp);
        a.byRule = true;   // you approved it; it earns nothing a month the engine can count
        a.usesQueue = true; a.once = CAPlanner.ONCE_WONDER;
        a.fromBudget = false; a.fromSurplus = true; a.chargeAll = false;
        a.upkeepCap = CASurplus.upkeepCap(m, s);
        a.starter = (x) -> startSite(x);
        offers.add(a);
    }

    /** The site for the planner; null when it started, else why not. */
    static String startSite(CAAction a) {
        MarketAPI m = a.market;
        if (CABuild.buildsFull(m)) return "a new building is already under construction here";
        CAEngine.Pick p = new CAEngine.Pick();
        p.id = CAWonders.SITE; p.name = a.name; p.verb = "Build"; p.cost = a.cost; p.upkeep = a.upkeep;
        String err = CABuild.start(m, p);
        if (err != null) return err;
        CABudget.charge(CAData.settings(), m.getId(), 0f, a.fee, a.upkeep);
        CAMark.set(SITE_MARK + m.getId(), CAMark.now());
        CARich.spent(CARich.WONDERS, a.cost);
        CALog.did(m.getName() + ": Grand Wonder site started for the " + CAWonders.name(GARDEN) + " (" + Misc.getDGSCredits(a.cost) + ").", true);
        return null;
    }

    /** The Garden's site waits for your approval; the ask is offered again each day it could start, a rejection holds 180 days. */
    static boolean approved(MarketAPI m, float cost, Map<?, ?> goods, List<String> lines) {
        if (CAMark.at(OK_MARK) != null) return true;
        List<String> names = new ArrayList<String>();
        if (goods != null) for (Object k : goods.keySet()) names.add(CAProjects.name(String.valueOf(k)));
        List<String> gone = new ArrayList<String>();
        for (Industry ind : replaced(m)) gone.add(ind.getCurrentName());
        CAApprovals.offer(OK_MARK, "wonder", m, "Build the " + CAWonders.name(GARDEN), cost, CABudget.constructionFee(CAData.settings(), cost), null,
                () -> { CAMark.set(OK_MARK, CAMark.now()); return null; },
                "A Grand Wonder site will be built at " + m.getName() + ", and then the " + CAWonders.name(GARDEN) + " goes up on it over 180 days.",
                names.isEmpty() ? null : "While it's being built, it takes " + CAContracts.join(names) + " from your exports, and you pay 10% of their price.",
                "It makes food and luxury goods, and your fleet can rest there for a combat readiness boost.",
                gone.isEmpty() ? "When it's done, no farm or resort can be built there any more."
                        : "When it's done, it replaces " + CAContracts.join(gone) + ", and their AI cores and items go to Autopilot Storage first.",
                "If you reject it, it won't be offered again for " + CAApprovals.holdText("wonder") + ".");
        lines.add(CAWonders.name(GARDEN) + " at " + m.getName() + " waits for your approval");
        return false;
    }
}
