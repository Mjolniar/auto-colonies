package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Ashes of the Domain's Grand Wonders and Bifrost gates, while spending freely (0.2.34.0, CASurplus.rich). AotD charges for
 * them with a trade contract that takes goods from your colonies' exports every month (10% of their price), and a build
 * stops while any good isn't delivered at all, so each starts only once your colonies make every good it takes (the
 * producers of AotD's own goods are planned by CAProjects while rich).
 * - The Daedalus Array (Sky Tether): replaces the Megaport, accessibility +80% to +200%, one per faction. Built the way
 *   ToT's dialog does it: a Grand Wonder construction site (aotd_gw_construction_site), then
 *   AoTDConstructionSite.setAssignedWonder. Never touches a site that already has a wonder, and needs the colony's port
 *   (the wonder replaces it when it finishes). The Garden of Elysium (it destroys the AI cores and items in the farms and
 *   resorts it replaces) and Star Citadels (raided schematics) stay your call.
 * - Bifrost: the Bifrost Control Center (one per faction) is a plan step at the focus world (or the biggest colony), so
 *   research works toward Bifrost Gates; once it works, a gate goes up in each of your systems that has none, one a day
 *   (BifrostMegastructure.addNewBifrostGate, as its dialog does), each 20,000 a month on the Control Center once built.
 * Reflection only; without AotD it does nothing. Setting: Settings, Budget and spending.
 * 0.3.0.0 (WS-B3): the Grand Wonder site is an action in the planner's one queue (offer), so it never takes a colony's
 * construction queue ahead of a build that pays.
 * 0.3.0.0 (C3 fix): wonders start only while CARich.wondersOpen (the Wonders rung On, the tier Rich, the Balanced or Economy
 * preset); a site's price must fit the rung's allowance and is recorded on it.
 */
public class CAWonders {
    static final String SITE = "aotd_gw_construction_site", DAEDALUS = "aotd_daedalus_array", BFC = "aotd_bfc_ind";
    static final String GW = "data.kaysaar.aotd.tot.grandwonders.", TOT = "data.kaysaar.aotd.tot.";
    static final String BIFROST = "data.kaysaar.aotd.vok.campaign.econ.megastructures.impl.scripts.BifrostMegastructureManager";
    static boolean broken = false;
    /** 0.3.9.7 (G4): CAMark key of a Grand Wonder site Autopilot built at a colony. */
    static final String SITE_MARK = "wondersite:";
    /** 0.3.9.7 (G5): upkeep each Bifrost gate adds (VoK BifrostControlCenter.costUpkeepForEachGate). */
    static final float BIFROST_UPKEEP = 20000f;
    static String summary;
    /** 0.3.28.0 (plan 2026-11-25, batch 5): CAMark key of your approval of the Daedalus Array, and its ask's key. */
    static final String OK_MARK = "wonderok:" + DAEDALUS;

    /**
     * 0.3.28.0: the Daedalus Array's site waits for your approval (Approvals, kind "wonder"). The ask is offered again each
     * day it could start; a rejection holds it 180 days.
     */
    static boolean approved(MarketAPI m, float cost, Map<?, ?> goods, List<String> lines) {
        if (CAMark.at(OK_MARK) != null) return true;
        List<String> names = new ArrayList<String>();
        if (goods != null) for (Object k : goods.keySet()) names.add(CAProjects.name(String.valueOf(k)));
        Industry port = m.getIndustry("megaport") != null ? m.getIndustry("megaport") : m.getIndustry("spaceport");
        CAApprovals.offer(OK_MARK, "wonder", m, "Build the " + name(DAEDALUS), cost, CABudget.constructionFee(CAData.settings(), cost), null,
                () -> { CAMark.set(OK_MARK, CAMark.now()); return null; },
                "A Grand Wonder site will be built at " + m.getName() + ", and then the " + name(DAEDALUS) + " goes up on it.",
                names.isEmpty() ? null : "While it's being built, it takes " + CAContracts.join(names) + " from your exports, and you pay 10% of their price.",
                "When it's done, it replaces the " + (port == null ? "port" : port.getCurrentName()) + " and keeps its AI core and improvement.",
                "If you reject it, it won't be offered again for " + CAApprovals.holdText("wonder") + ".");
        lines.add(name(DAEDALUS) + " at " + m.getName() + " waits for your approval");
        return false;
    }

    static boolean on(CASettings s) {
        return !broken && !s.noWonders && Global.getSettings().getModManager().isModEnabled(CAResearch.MOD) && CASurplus.rich(s)
                && CARich.wondersOpen(s);   // 0.3.0.0 (C3 fix): the Wonders rung On, the tier Rich, a preset that funds it
    }

    static ClassLoader cl() { return Global.getSettings().getScriptClassLoader(); }

    static String name(String id) { try { return Global.getSettings().getIndustrySpec(id).getName(); } catch (Throwable t) { return id; } }

    /** Goods your colonies don't make at all, from a demand map: a build waits for them. */
    static List<String> unmade(Map<?, ?> goods) {
        List<String> out = new ArrayList<String>();
        if (goods == null) return out;
        for (Object k : goods.keySet()) {
            String g = String.valueOf(k);
            Integer n = CAContracts.production(g);
            if (n == null) n = CAProjects.yours(g);
            if (n <= 0) out.add(CAProjects.name(g));
        }
        return out;
    }

    /** The colony that should hold the Bifrost Control Center: the focus world if it spends, else the biggest Auto colony. */
    static MarketAPI bfcHost() {
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (m.hasIndustry(BFC)) return null;   // one per faction, there already
        MarketAPI f = CASurplus.focus();
        if (f != null && CASurplus.spends(f)) return f;
        MarketAPI best = null;
        for (MarketAPI m : CAItems.autoMarkets()) {
            if (CAHypershunt.is(m) || !CASurplus.spends(m)) continue;
            if (best == null || m.getSize() > best.getSize()) best = m;
        }
        return best;
    }

    /** Plan step: the Bifrost Control Center at its host while spending freely (research traces it to Bifrost Gates). */
    static List<CAPlan.Step> stepsFor(MarketAPI m) {
        CASettings s = CAData.settings();
        if (m == null || s.noWonders || !Global.getSettings().getModManager().isModEnabled(CAResearch.MOD) || !CASurplus.rich(s) || !CARich.wondersOpen(s)
                || Global.getSettings().getIndustrySpec(BFC) == null) return Collections.emptyList();
        MarketAPI host = bfcHost();
        if (host == null || !host.getId().equals(m.getId())) return Collections.emptyList();
        return Collections.singletonList(new CAPlan.Step(CAPlan.Kind.BUILD, BFC, name(BFC) + " (surplus credits: Bifrost network)"));
    }

    public static void run() {
        CASettings s = CAData.settings();
        summary = null;
        if (!on(s)) return;
        List<String> lines = new ArrayList<String>();
        try { daedalus(s, lines, null, true); }
        catch (Throwable t) { fail(t, "can't read Ashes of the Domain's Grand Wonders; they're left to you"); return; }
        try { bifrost(s, lines); }
        catch (Throwable t) { fail(t, "can't read Ashes of the Domain's Bifrost network; it's left to you"); return; }
        summary = lines.isEmpty() ? null : "Wonders and gates: " + CAContracts.join(lines) + ".";
    }

    // ---------------- 0.3.0.0 (WS-B3): the planner's actions ----------------

    /** The planner's name for these actions. */
    static final String SOURCE = "wonders";

    /**
     * 0.3.0.0 (WS-B3, research 02 section 13): the Daedalus Array's Grand Wonder site as an action in the planner's one queue,
     * so it takes a colony's construction queue only when nothing that pays waits there. Spending freely builds it (the rule
     * you set with Wonders and gates), ranked after everything that pays, from surplus credits, only its fee against the
     * month's budget, as in 0.2.45.1. Putting the wonder on a finished site costs no credits (Ashes of the Domain's contract
     * takes goods), so run() still does that, and the Bifrost gates.
     */
    public static List<CAAction> offer() {
        List<CAAction> out = new ArrayList<CAAction>();
        CASettings s = CAData.settings();
        if (!on(s)) return out;
        try { daedalus(s, new ArrayList<String>(), out, false); }
        catch (Throwable t) { out.clear(); }   // run() marks it broken and logs it
        return out;
    }

    /** Builds the Grand Wonder site for the planner; null when it started, else why not. */
    static String startSite(CAAction a) {
        MarketAPI m = a.market;
        CASettings s = CAData.settings();
        if (CABuild.buildsFull(m)) return "a new building is already under construction here";
        CAEngine.Pick p = new CAEngine.Pick();
        p.id = SITE; p.name = a.name; p.verb = "Build"; p.cost = a.cost; p.upkeep = a.upkeep;
        String err = CABuild.start(m, p);
        if (err != null) { if (CALog.once("wonder:site:" + m.getId() + ":" + err)) CALog.think(m.getName() + ": Grand Wonder site not started: " + err); return err; }
        CABudget.charge(s, m.getId(), 0f, a.fee, a.upkeep);
        CAMark.set(SITE_MARK + m.getId(), CAMark.now());   // 0.3.9.7 (G4): Autopilot's site
        CARich.spent(CARich.WONDERS, a.cost);   // 0.3.0.0 (C3 fix): the site's price counts against the Wonders rung this month
        CALog.did(m.getName() + ": Grand Wonder site started for the " + name(DAEDALUS) + " (" + Misc.getDGSCredits(a.cost) + ").", true);
        return null;
    }

    // ---------------- Daedalus Array ----------------

    /**
     * offers: where the planner's action for the site goes (offer()), or null. act: run() puts the wonder on a finished site,
     * and with the planner not in charge (legacyPlanner) builds the site itself as in 0.2.45.
     */
    @SuppressWarnings("unchecked")
    static void daedalus(CASettings s, List<String> lines, List<CAAction> offers, boolean act) throws Exception {
        IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(DAEDALUS);
        if (spec == null || Global.getSettings().getIndustrySpec(SITE) == null) return;
        Class<?> mgrC = Class.forName(GW + "GrandWonderManager", true, cl()), apiC = Class.forName(GW + "GrandWonderAPI", true, cl());
        Class<?> siteC = Class.forName(TOT + "industries.AoTDConstructionSite", true, cl());
        Object mgr = CARefl.invoke(CARefl.method(mgrC, "getInstance"), null);
        int built = ((Number) CARefl.invoke(CARefl.method(mgrC, "getBuiltSoFar", String.class), mgr, DAEDALUS)).intValue();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (m.hasIndustry(DAEDALUS)) built++;
        if (built > 0) { lines.add(name(DAEDALUS) + " built or under way"); return; }
        Object slots = CARefl.method(mgrC, "canBuiltAdditionalWonderDueToSlots", MarketAPI.class);
        Object reqs = CARefl.method(apiC, "getRequirementsToBuildWonder"), met = CARefl.method(apiC, "hasReqBeenMetOnMarket", String.class);
        Object demand = CARefl.method(apiC, "getDemandCostForRestoration");
        Object assigned = CARefl.method(siteC, "getAssignedWonder"), assign = CARefl.method(siteC, "setAssignedWonder", String.class);
        for (MarketAPI m : CAItems.autoMarkets()) {
            if (!CASurplus.spends(m) || CASiege.held(m) || !(m.hasIndustry("spaceport") || m.hasIndustry("megaport"))) continue;
            Industry w = spec.getNewPluginInstance(m);
            if (!apiC.isInstance(w) || !w.isAvailableToBuild()) continue;
            boolean ok = true;
            Object rq = CARefl.invoke(reqs, w);
            if (rq instanceof Map) for (Object k : ((Map<Object, Object>) rq).keySet()) if (!Boolean.TRUE.equals(CARefl.invoke(met, w, String.valueOf(k)))) ok = false;
            if (!ok || (CAAotD.enabled() && !CAAotD.allows(DAEDALUS, m))) continue;
            Industry site = null;
            for (Industry ind : m.getIndustries()) if (siteC.isInstance(ind)) site = ind;
            if (site != null && CARefl.invoke(assigned, site) != null) continue;   // a wonder of yours is going up there already
            List<String> missing = unmade((Map<?, ?>) CARefl.invoke(demand, w));
            if (!missing.isEmpty()) {
                lines.add(name(DAEDALUS) + " at " + m.getName() + " waits for " + CAContracts.join(missing));
                if (CALog.once("wonder:wait:" + m.getId() + ":" + missing)) CALog.think(m.getName() + ": " + name(DAEDALUS) + " waits for " + CAContracts.join(missing) + " production.");
                return;
            }
            if (site == null) {
                if (!Boolean.TRUE.equals(CARefl.invoke(slots, null, m))) continue;   // no Grand Wonder slot left here
                if (CABuild.buildsFull(m)) { lines.add(name(DAEDALUS) + " at " + m.getName() + " waits for the current construction"); return; }
                IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(SITE);
                Industry probe = sp.getNewPluginInstance(m);
                if (!probe.isAvailableToBuild()) continue;
                if (!approved(m, probe.getBuildCost(), (Map<?, ?>) CARefl.invoke(demand, w), lines)) return;   // 0.3.28.0: ask first
                if (offers != null) {   // 0.3.0.0 (B3): the planner's action for the site
                    CAAction a = new CAAction();
                    a.kind = CAAction.Kind.BUILD; a.source = SOURCE; a.market = m; a.marketId = m.getId();
                    a.id = SITE; a.name = sp.getName(); a.why = "for the " + name(DAEDALUS);
                    a.cost = probe.getBuildCost(); a.fee = CABudget.constructionFee(s, a.cost); a.days = probe.getBuildTime();
                    a.rapid = CABudget.rapidFee(a.cost);   // 0.3.8.5: CABuild.start adds Rapid construction's fee
                    a.upkeep = CAEngine.baseUpkeep(m, sp);
                    a.byRule = true;   // spending freely builds it; it earns nothing a month the engine can count
                    a.usesQueue = true; a.once = CAPlanner.ONCE_WONDER;
                    a.fromBudget = false; a.fromSurplus = true; a.chargeAll = false;
                    a.upkeepCap = CASurplus.upkeepCap(m, s);
                    // 0.3.0.0 (C3 fix): the site's price must fit the Wonders rung's allowance this month
                    if (!CARich.wondersFit(s, a.cost)) { lines.add(name(DAEDALUS) + " at " + m.getName() + " waits for the Wonders rung's share of the pool"); return; }
                    a.starter = (x) -> startSite(x);
                    offers.add(a);
                    return;
                }
                if (CAPlanner.inCharge()) { lines.add("construction site for the " + name(DAEDALUS) + " at " + m.getName() + " waits its turn in the build queue"); return; }
                // 0.3.0.0 (C3 fix): the site's price must fit the Wonders rung's allowance this month (always with legacyPlanner)
                if (!CARich.wondersFit(s, probe.getBuildCost())) { lines.add(name(DAEDALUS) + " at " + m.getName() + " waits for the Wonders rung's share of the pool"); return; }
                CAEngine.Pick p = new CAEngine.Pick();
                p.id = SITE; p.name = sp.getName(); p.verb = "Build"; p.cost = probe.getBuildCost(); p.upkeep = CAEngine.baseUpkeep(m, sp);
                String err = CABuild.start(m, p);
                if (err != null) { if (CALog.once("wonder:site:" + m.getId() + ":" + err)) CALog.think(m.getName() + ": Grand Wonder site not started: " + err); return; }
                CABudget.charge(s, 0f, CABudget.constructionFee(s, p.cost), p.upkeep);
                CAMark.set(SITE_MARK + m.getId(), CAMark.now());   // 0.3.9.7 (G4): Autopilot's site
                if (!CABudget.legacy(s)) CARich.spent(CARich.WONDERS, p.cost);   // 0.3.0.0 (C3 fix): the site's price counts against the Wonders rung this month
                CALog.did(m.getName() + ": Grand Wonder site started for the " + name(DAEDALUS) + " (" + Misc.getDGSCredits(p.cost) + ").", true);
                lines.add("construction site for the " + name(DAEDALUS) + " going up at " + m.getName());
                return;
            }
            if (site.isBuilding()) { lines.add(name(DAEDALUS) + "'s construction site at " + m.getName() + " is being built"); return; }
            if (!act) return;   // 0.3.0.0 (B3): the planner's offer only looks
            // 0.3.9.7 (G4): a site you built or locked is yours to assign (the Garden of Elysium, a Star Citadel)
            if (CAMark.at(SITE_MARK + m.getId()) == null || CAData.get().colony(m.getId()).locks.contains(site.getId())) continue;
            CARefl.invoke(assign, site, DAEDALUS);
            CALog.did(m.getName() + ": " + name(DAEDALUS) + " construction started. Replaces the Megaport.", true);
            lines.add(name(DAEDALUS) + " started at " + m.getName());
            return;
        }
    }

    // ---------------- Bifrost ----------------

    /** 0.3.9.7 (G11): a missing class or method turns wonders off for the session; anything else is one day's warning. */
    static void fail(Throwable t, String what) {
        boolean lookup = t instanceof ClassNotFoundException || t instanceof NoSuchMethodException || t instanceof NoSuchFieldException || t instanceof LinkageError;
        if (lookup) { broken = true; CADiagnostics.LOG.warn(CADiagnostics.TAG + what, t); }
        else CAStep.warn("wonders", "wonders and gates failed today", t);
    }

    @SuppressWarnings("unchecked")
    static void bifrost(CASettings s, List<String> lines) throws Exception {
        MarketAPI control = null;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) { Industry b = m.getIndustry(BFC); if (b != null && b.isFunctional()) control = m; }
        if (control == null) {
            MarketAPI host = bfcHost();
            if (host != null && Global.getSettings().getIndustrySpec(BFC) != null) lines.add(name(BFC) + " planned at " + host.getName() + " (research unlocks it first)");
            return;
        }
        Class<?> mgrC = Class.forName(BIFROST, true, cl());
        Object mega = CARefl.call(CARefl.invoke(CARefl.method(mgrC, "getInstance"), null), "getMegastructure");
        Set<StarSystemAPI> have = new HashSet<StarSystemAPI>();
        Object sections = CARefl.call(mega, "getSections");
        if (sections instanceof List) for (Object sec : (List<Object>) sections) { Object sys = CARefl.call(sec, "getStarSystemAPI"); if (sys instanceof StarSystemAPI) have.add((StarSystemAPI) sys); }
        Object script = CARefl.call(CARefl.call(mega, "getSectionSpec"), "getScript");
        List<String> missing = unmade((Map<?, ?>) CARefl.call(script, "getMonthlyResNeeded"));
        Object add = CARefl.method(mega.getClass(), "addNewBifrostGate", StarSystemAPI.class);
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            StarSystemAPI sys = m.getStarSystem();
            if (sys == null || have.contains(sys) || !sys.getEntitiesWithTag("bifrost").isEmpty()) continue;
            if (!missing.isEmpty()) {
                lines.add("Bifrost gates wait for " + CAContracts.join(missing));
                if (CALog.once("bifrost:wait:" + missing)) CALog.think("Bifrost gates wait for " + CAContracts.join(missing) + " production.");
                return;
            }
            // 0.3.9.7 (G5): each gate adds BIFROST_UPKEEP to the Control Center's upkeep: within the month's new-upkeep cap, and booked
            if (s.upkeepAdded + BIFROST_UPKEEP > CASurplus.upkeepCap(control, s)) {
                lines.add("Bifrost gates wait for next month's upkeep room");
                return;
            }
            CARefl.invoke(add, mega, sys);
            CABudget.charge(s, control.getId(), 0f, 0f, BIFROST_UPKEEP);
            CALog.did("Bifrost gate started in " + sys.getName() + ". 90 days, then 20,000/month.", true);
            lines.add("Bifrost gate started in " + sys.getName());
            return;   // one a day
        }
        lines.add(have.size() + " Bifrost gate" + (have.size() == 1 ? "" : "s") + " in your systems");
    }
}
