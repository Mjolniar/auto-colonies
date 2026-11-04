package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Ashes of the Domain's Grand Wonders and Bifrost gates, while spending freely (2.34.0, CASurplus.rich). AotD charges for
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
 */
public class CAWonders {
    static final String SITE = "aotd_gw_construction_site", DAEDALUS = "aotd_daedalus_array", BFC = "aotd_bfc_ind";
    static final String GW = "data.kaysaar.aotd.tot.grandwonders.", TOT = "data.kaysaar.aotd.tot.";
    static final String BIFROST = "data.kaysaar.aotd.vok.campaign.econ.megastructures.impl.scripts.BifrostMegastructureManager";
    static boolean broken = false;
    static String summary;

    static boolean on(CASettings s) {
        return !broken && !s.noWonders && Global.getSettings().getModManager().isModEnabled(CAResearch.MOD) && CASurplus.rich(s);
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
        if (m == null || s.noWonders || !Global.getSettings().getModManager().isModEnabled(CAResearch.MOD) || !CASurplus.rich(s)
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
        try { daedalus(s, lines); }
        catch (Throwable t) { broken = true; CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Ashes of the Domain's Grand Wonders; they're left to you", t); return; }
        try { bifrost(lines); }
        catch (Throwable t) { broken = true; CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Ashes of the Domain's Bifrost network; it's left to you", t); return; }
        summary = lines.isEmpty() ? null : "Wonders and gates: " + CAContracts.join(lines) + ".";
    }

    // ---------------- Daedalus Array ----------------

    @SuppressWarnings("unchecked")
    static void daedalus(CASettings s, List<String> lines) throws Exception {
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
                if (CALog.once("wonder:wait:" + m.getId() + ":" + missing)) CALog.add(m.getName() + ": " + name(DAEDALUS) + " waits for " + CAContracts.join(missing) + " production");
                return;
            }
            if (site == null) {
                if (!Boolean.TRUE.equals(CARefl.invoke(slots, null, m))) continue;   // no Grand Wonder slot left here
                if (Misc.getCurrentlyBeingConstructed(m) != null) { lines.add(name(DAEDALUS) + " at " + m.getName() + " waits for the current construction"); return; }
                IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(SITE);
                Industry probe = sp.getNewPluginInstance(m);
                if (!probe.isAvailableToBuild()) continue;
                CAEngine.Pick p = new CAEngine.Pick();
                p.id = SITE; p.name = sp.getName(); p.verb = "Build"; p.cost = probe.getBuildCost(); p.upkeep = CAEngine.baseUpkeep(m, sp);
                String err = CABuild.start(m, p);
                if (err != null) { if (CALog.once("wonder:site:" + m.getId() + ":" + err)) CALog.add(m.getName() + ": Grand Wonder site not started: " + err); return; }
                CABudget.charge(s, 0f, CABudget.constructionFee(s, p.cost), p.upkeep);
                CALog.add(m.getName() + ": Grand Wonder site started for the " + name(DAEDALUS) + " (" + Misc.getDGSCredits(p.cost) + ")", true);
                lines.add("construction site for the " + name(DAEDALUS) + " going up at " + m.getName());
                return;
            }
            if (site.isBuilding()) { lines.add(name(DAEDALUS) + "'s construction site at " + m.getName() + " is being built"); return; }
            CARefl.invoke(assign, site, DAEDALUS);
            CALog.add(m.getName() + ": " + name(DAEDALUS) + " construction started (replaces the Megaport)", true);
            lines.add(name(DAEDALUS) + " started at " + m.getName());
            return;
        }
    }

    // ---------------- Bifrost ----------------

    @SuppressWarnings("unchecked")
    static void bifrost(List<String> lines) throws Exception {
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
                if (CALog.once("bifrost:wait:" + missing)) CALog.add("Bifrost gates wait for " + CAContracts.join(missing) + " production");
                return;
            }
            CARefl.invoke(add, mega, sys);
            CALog.add("Bifrost gate started in " + sys.getName() + " (90 days, then 20,000/month)", true);
            lines.add("Bifrost gate started in " + sys.getName());
            return;   // one a day
        }
        lines.add(have.size() + " Bifrost gate" + (have.size() == 1 ? "" : "s") + " in your systems");
    }
}
