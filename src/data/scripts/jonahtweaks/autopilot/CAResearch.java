package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Ashes of the Domain research (Vaults of Knowledge). One tech at a time, measured in days, and only while you own a
 * working Research Facility or Black Site. A tech costs items once when it starts (mostly research databanks, from
 * the Research Facility storage tab) plus 10,000 x tier a month on every facility while it runs.
 * Each tech you can start is valued by what it unlocks, scored by the same engine that picks buildings: the candidate
 * techs are marked researched for one scoring pass over your colonies, so AotD's own gates (Building Menu Overhaul,
 * VoK's buildings, synergies) answer as they would afterwards, then put back. Known non-building effects (output +1,
 * hazard -10%, upkeep x0.9) are added, plus half the best tech each one leads to.
 * Each colony's plan comes first: every open plan step that research locks (BMO's gates, VoK's rewards, Terraforming Made
 * Easy's own tech ids) is traced to its tech and the whole chain of techs it still needs, and the step's plan value
 * (CAPlan.bonusFor) is shared along that chain by research days, so the first tech of a long chain counts too.
 * Auto starts the best tech whenever nothing is running or queued, only for techs that cost databanks and AI cores (the
 * cores from Autopilot Storage, counted at CORE_VALUE); colony items are left to you. When no tech pays for itself within a
 * year, the best of the rest goes anyway, so the facility doesn't sit idle; when nothing can start, the log says why. With surplus credits (CASurplus) the
 * focus world's plan counts twice and research upkeep a quarter. While a plan tech waits for databanks, the ones you have aren't spent on techs no
 * plan needs; the log says what's needed. Research upkeep is temporary, so plan techs aren't held back by the new-upkeep
 * cap. Databanks count from the Research Facility tab, Autopilot Storage, your colonies' Storage and your fleet's cargo. Autopilot Storage is research's storage too: whatever a tech is short of when it
 * starts (Auto or the Start button) is moved from there into the Research Facility tab, and databanks from your fleet's
 * cargo after that. The section is on the empire panel's Autopilot Storage page.
 * Reflection only; if VoK can't be read, research is left to you.
 * 0.3.0.0 (WS-B5, research 01 section 9, 03 F5): "the best of the rest" (a tech that doesn't pay back within a year and no
 * plan needs) only goes with idle money (spending freely, or the money ladder's Idle money tier) and never while a plan
 * tech waits for databanks; and research upkeep counts in full until spending freely, not a quarter whenever credits are
 * over the surplus line. The rest of this note is 0.2.45's.
 * 0.3.0.0 (WS-C4, research 03 F11): what a tech is short of is shipped from the nearest colony first (CAStores.nearest), each
 * source's whole share in one shipment, and a shipment whose fee is more than its databanks or AI cores are worth stays
 * put unless a colony's plan needs the tech (Zenemosrae shipped 1 databank, worth 5,000, 43 days for 14,177 while Wind
 * held them 2 days away). F5: the Research page shows what each tech costs with your facilities against one.
 * 0.3.0.0 fix (C4 review): a tech's sources are all planned before anything moves, and its shipments are judged together:
 * all of their fees against everything shipped for it. Either all of it moves and ships, or none of it does and the tech
 * waits for shipping costs.
 */
public class CAResearch {
    public static final String MOD = "aotd_vok";
    static final String R = "data.kaysaar.aotd.vok.scripts.research.";
    static final String KEY = "$aotd_vok_manager", SUBMARKET = "researchfacil", DATABANK = "research_databank";
    static final float DATABANK_VALUE = 5000f, TIER_UPKEEP = 10000f;
    /** AI cores a tech can use up from Autopilot Storage, at what they'd fetch: Auto counts them as part of the cost. */
    static final Map<String, Float> CORE_VALUE = new HashMap<String, Float>();
    static { CORE_VALUE.put("gamma_core", 2500f); CORE_VALUE.put("beta_core", 7500f); CORE_VALUE.put("alpha_core", 20000f); }
    /** Days Auto keeps databanks for a plan tech short of them before the best tech it can afford goes ahead anyway. */
    static final float WAIT_DAYS = 90f;
    static final String START = "starting research";

    /** AotD's Research Facility (and its Black Site, which counts as one): research needs at least one, working. */
    static final String FACILITY = "aotd_research_facility", BLACKSITE = "aotd_blacksite";

    /**
     * With research on Auto and no Research Facility anywhere, one goes first in a colony's plan (0.2.39.0): nothing can be
     * researched without one, and research opens other plan steps. It was left to its own score, and spending freely put
     * every defense structure first in the plan, so Hot Springs went months building Planetary Shield and Patrol HQ while
     * the Research Facility, its best option, waited and the plan's Bifrost step ran out of time. The host is the focus world,
     * else the biggest Auto colony.
     */
    static List<CAPlan.Step> stepsFor(MarketAPI m) {
        CASettings s = CAData.settings();
        if (m == null || s.researchMode != 1 || !Global.getSettings().getModManager().isModEnabled(MOD)) return Collections.emptyList();
        try { if (Global.getSettings().getIndustrySpec(FACILITY) == null) return Collections.emptyList(); }
        catch (Throwable t) { return Collections.emptyList(); }
        MarketAPI host = facilityHost();
        if (host == null || !host.getId().equals(m.getId())) return Collections.emptyList();
        return Collections.singletonList(new CAPlan.Step(CAPlan.Kind.BUILD, FACILITY,
                "Research Facility (research is on Auto and nothing can be researched without one)"));
    }

    static boolean isFacilityStep(MarketAPI m, String id) { return FACILITY.equals(id) && !stepsFor(m).isEmpty(); }

    static MarketAPI facilityHost() {
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (m.hasIndustry(FACILITY) || m.hasIndustry(BLACKSITE)) return null;   // one is built or on the way
        List<MarketAPI> auto = CAItems.autoMarkets();
        MarketAPI f = CASurplus.focus();
        if (f != null && auto.contains(f) && !CAHypershunt.is(f)) return f;
        MarketAPI best = null;
        for (MarketAPI m : auto) {
            if (CAHypershunt.is(m)) continue;
            if (best == null || m.getSize() > best.getSize()) best = m;
        }
        return best;
    }
    /** Vanilla buildings VoK locks through Building Menu Overhaul, and the Bifrost Control Center, by tech. */
    static final String[][] GATES = {{"aotd_tech_rudimentary_eq", "aquaculture"}, {"aotd_tech_agriculture", "farming"},
            {"aotd_tech_exoskeletons", "mining"}, {"aotd_tech_nanometal", "refining"}, {"aotd_tech_nanometal", "lightindustry"},
            {"aotd_tech_hull_manufacture", "heavyindustry"}, {"aotd_tech_orbital_assembly", "orbitalworks"},
            {"aotd_tech_antimatter_production", "fuelprod"}, {"aotd_tech_dual_trade_system", "commerce"},
            {"aotd_tech_spaceport_expansion", "megaport"}, {"aotd_tech_bifrost_gate", "aotd_bfc_ind"}};

    static Object instance, player, forPlayer, repo, canResearch, allReq, itemsOk, itemCount, facilities, focus, setFocus, pay, queue, queued, councilAdvance, daysFor;
    static Object fId, fName, fTier, fReq, fItems, fRewards, fDone, fDaysSpent, fOther, fOtherMet, fHead, fCouncil;
    static boolean linked = false, broken = false;

    static boolean link() {
        if (linked || broken) return linked;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> main = Class.forName(R + "AoTDMainResearchManager", true, cl), fac = Class.forName(R + "AoTDFactionResearchManager", true, cl);
            Class<?> opt = Class.forName(R + "models.ResearchOption", true, cl), q = Class.forName(R + "ResearchQueueManager", true, cl);
            Class<?> util = Class.forName("data.kaysaar.aotd.vok.plugins.AoDUtilis", true, cl);
            instance = CARefl.method(main, "getInstance"); player = CARefl.method(main, "getManagerForPlayer"); forPlayer = CARefl.method(main, "isResearchedForPlayer", String.class);
            repo = CARefl.method(fac, "getResearchRepoOfFaction"); canResearch = CARefl.method(fac, "canResearch", String.class, boolean.class);
            allReq = CARefl.method(fac, "haveResearchedAllReq", String.class); itemsOk = CARefl.method(fac, "haveMetReqForItems", String.class);
            itemCount = CARefl.method(fac, "retrieveAmountOfItems", String.class); facilities = CARefl.method(fac, "getAmountOfResearchFacilities");
            focus = CARefl.method(fac, "getCurrentFocus"); setFocus = CARefl.method(fac, "setCurrentFocus", String.class); pay = CARefl.method(fac, "payForResearch", String.class);
            queue = CARefl.method(fac, "getQueueManager"); queued = CARefl.method(q, "getQueuedResearchOptions");
            councilAdvance = CARefl.method(fac, "executeResearchCouncilAdvance", float.class);
            daysFor = CARefl.method(util, "getDaysFromResearch", opt);
            fId = CARefl.field(opt, "Id"); fName = CARefl.field(opt, "Name"); fTier = CARefl.field(opt, "Tier"); fReq = CARefl.field(opt, "ReqTechsToResearchFirst");
            fItems = CARefl.field(opt, "ReqItemsToResearchFirst"); fRewards = CARefl.field(opt, "Rewards"); fDone = CARefl.field(opt, "isResearched");
            fDaysSpent = CARefl.field(opt, "daysSpentOnResearching"); fOther = CARefl.field(opt, "otherReq"); fOtherMet = CARefl.field(opt, "metOtherReq");
            fHead = CARefl.field(fac, "currentHeadOfCouncil"); fCouncil = CARefl.field(fac, "researchCouncil");
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Vaults of Knowledge's research; it's left to you", t);
        }
        return linked;
    }

    /** The player's research manager, or null before VoK has made one (a new game's first frames). */
    static Object mgr() throws Exception {
        if (!link() || Global.getSector().getPersistentData().get(KEY) == null) return null;
        Object main = CARefl.invoke(instance, null);
        return main == null ? null : CARefl.invoke(player, main);
    }

    static boolean forPlayer(String id) {
        try { Object main = CARefl.invoke(instance, null); return main != null && Boolean.TRUE.equals(CARefl.invoke(forPlayer, main, id)); }
        catch (Throwable t) { return false; }
    }

    // ---------------- reading ----------------

    public static class Tech {
        public String id, name;
        public int tier;
        public float days, spent;
        public Map<String, Integer> items = new LinkedHashMap<String, Integer>();
        public Set<String> industries = new LinkedHashSet<String>();
        public List<String> prereqs = new ArrayList<String>();
        public boolean available, needsItems, unlockedByCommission;
        public float own, next, value, cost, rank;
        public float plan;   // its share of the plan steps whose research chain it's on
        public String otherText;   // a requirement that isn't an item, not met yet (VoK's text: a Hyperdimensional Processor in a Research Facility)
        public List<String> planFor = new ArrayList<String>();
        public List<String> lines = new ArrayList<String>();
        Object raw;

        boolean databanksOnly() { for (String k : items.keySet()) if (!DATABANK.equals(k)) return false; return true; }
        /** Auto may start it: it costs databanks and AI cores only (cores come from Autopilot Storage); colony items are yours. */
        boolean autoOk() { for (String k : items.keySet()) if (!DATABANK.equals(k) && !CORE_VALUE.containsKey(k) && !spendable(k)) return false; return true; }
        int databanks() { Integer n = items.get(DATABANK); return n == null ? 0 : n; }
        /** What its AI cores are worth, counted as part of its cost. */
        float coreCost() { float v = 0f; for (Map.Entry<String, Integer> e : items.entrySet()) { Float c = CORE_VALUE.get(e.getKey()); if (c != null) v += c * e.getValue(); } return v; }
    }

    @SuppressWarnings("unchecked")
    static Tech tech(Object mgr, Object o) throws Exception {
        Tech t = new Tech();
        t.raw = o;
        t.id = String.valueOf(CARefl.get(fId, o));
        t.name = String.valueOf(CARefl.get(fName, o));
        Object tier = CARefl.get(fTier, o);
        t.tier = tier instanceof Enum ? ((Enum<?>) tier).ordinal() : 0;
        t.days = ((Number) CARefl.invoke(daysFor, null, o)).floatValue();
        t.spent = CARefl.getFloat(fDaysSpent, o);
        Map<String, Integer> it = (Map<String, Integer>) CARefl.get(fItems, o);
        if (it != null) for (Map.Entry<String, Integer> e : it.entrySet()) if (e.getValue() != null && e.getValue() > 0) t.items.put(e.getKey(), e.getValue());
        List<String> req = (List<String>) CARefl.get(fReq, o);
        if (req != null) t.prereqs.addAll(req);
        Map<String, Object> rw = (Map<String, Object>) CARefl.get(fRewards, o);
        if (rw != null) for (Map.Entry<String, Object> e : rw.entrySet()) {
            if (e.getValue() instanceof Enum && "INDUSTRY".equals(((Enum<?>) e.getValue()).name()) && Global.getSettings().getIndustrySpec(e.getKey()) != null) t.industries.add(e.getKey());
        }
        for (String[] g : GATES) if (g[0].equals(t.id)) t.industries.add(g[1]);
        Object other = CARefl.get(fOther, o);
        boolean flagOk = other == null || CARefl.getBoolean(fOtherMet, o);
        if (!flagOk) try { Object two = CARefl.get(CARefl.field(other.getClass(), "two"), other); if (two != null) t.otherText = String.valueOf(two); } catch (Throwable ignored) { }
        t.available = Boolean.TRUE.equals(CARefl.invoke(canResearch, mgr, t.id, false));
        t.needsItems = !t.available && flagOk && Boolean.TRUE.equals(CARefl.invoke(allReq, mgr, t.id)) && !Boolean.TRUE.equals(CARefl.invoke(itemsOk, mgr, t.id));
        t.unlockedByCommission = forPlayer(t.id);
        return t;
    }

    static String commodityName(String id) {
        try { if (Global.getSettings().getCommoditySpec(id) != null) return Global.getSettings().getCommoditySpec(id).getName(); } catch (Throwable ignored) { }
        try { return Global.getSettings().getSpecialItemSpec(id).getName(); } catch (Throwable ignored) { }
        return id;
    }

    static String itemsText(Tech t) {
        List<String> l = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : t.items.entrySet()) l.add(e.getValue() + " " + commodityName(e.getKey()));
        return l.isEmpty() ? "nothing" : CAContracts.join(l);
    }

    // ---------------- valuing ----------------

    static List<Tech> cached;
    static float cachedAt = -99f;
    static int openCount;   // unresearched techs at the last ranking, startable or not
    static final List<Tech> itemTechs = new ArrayList<Tech>();   // unresearched techs that take colony items, which Auto leaves to you

    /** Items Auto may spend on research while spending freely (CASurplus.rich), from any Storage: AotD's production makes Fullerene Spools. */
    static final Set<String> SPENDABLE = new HashSet<String>(Arrays.asList("fullerene_spool"));
    static boolean spendItems = false;   // set each day: spending freely

    /**
     * Research spends colony items (0.2.36.1; Settings, Research spends colony items): while credits are over the surplus line,
     * 1 from Autopilot Storage and your colonies' Storage tabs, 2 also out of buildings. 0 otherwise. Set each day, and when
     * the setting changes.
     */
    static int itemMode = 0;

    static void updateItemMode(CASettings s) { itemMode = CASurplus.on(s) ? Math.max(0, Math.min(2, s.researchItemMode)) : 0; }

    /** A colony item (any special item that isn't an AI core), as opposed to a commodity. */
    static boolean colonyItem(String id) { return !CORE_VALUE.containsKey(id) && !isCommodity(id); }

    static boolean spendable(String id) { return (spendItems && SPENDABLE.contains(id)) || (itemMode > 0 && colonyItem(id)); }

    /** How many of an item are installed in your colonies' buildings that Autopilot may take it from (not locked ones). */
    static float installedCount(String id) {
        float n = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = CAData.get().colony(m.getId());
            for (Industry ind : m.getIndustries())
                if (ind.getSpecialItem() != null && id.equals(ind.getSpecialItem().getId()) && !c.locks.contains(ind.getId())) n++;
        }
        return n;
    }

    /** Takes up to n of an item out of buildings at colony `only`, the one where it does least first, into cargo `to`; returns how many, and where in `where`. */
    static int pullFromBuildings(String id, int n, CargoAPI to, List<String> where, MarketAPI only) {
        List<Object[]> spots = new ArrayList<Object[]>();   // {market, industry, score}
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (only != null && m != only) continue;
            CAData.Colony c = CAData.get().colony(m.getId());
            for (Industry ind : m.getIndustries()) {
                if (ind.getSpecialItem() == null || !id.equals(ind.getSpecialItem().getId()) || c.locks.contains(ind.getId())
                        || CAMods.leftToYou(ind.getId()) != null) continue;   // left to you: its item stays (0.2.45.0)
                float sc;
                try { sc = CAItems.itemScore(id, m, c.profile, ind); } catch (Throwable t) { sc = 0f; }
                spots.add(new Object[]{m, ind, sc});
            }
        }
        Collections.sort(spots, new Comparator<Object[]>() {
            public int compare(Object[] a, Object[] b) { return Float.compare((Float) a[2], (Float) b[2]); }
        });
        int got = 0;
        for (Object[] sp : spots) {
            if (got >= n) break;
            Industry ind = (Industry) sp[1];
            SpecialItemData data = ind.getSpecialItem();
            ind.setSpecialItem(null);
            to.addSpecial(data, 1f);
            got++;
            where.add(((MarketAPI) sp[0]).getName() + "'s " + ind.getCurrentName());
        }
        return got;
    }

    /** How many of an item your colonies' Storage tabs hold. */
    static float colonyCount(String id) {
        float n = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) n += count(Misc.getStorageCargo(m), id);
        return n;
    }

    /** Where a research item comes from, for the panel. */
    static String whereFrom(String id) {
        if (DATABANK.equals(id)) return "Research Facilities on pre-collapse facility worlds, ruins and salvage";
        if (CORE_VALUE.containsKey(id)) return "salvage";
        if (id.equals("fullerene_spool")) return "AotD's custom production (Sky Tether research)";
        if (id.equals("corrupted_nanoforge")) return "the Nanoforge Corruption Analysis special project";
        return "exploration: ruins, derelicts, research stations";
    }

    static float now() { return CADay.now(); }

    /** An open plan step that research locks, with the chain of techs it still needs. */
    public static class Need {
        public String step, colony;
        public float value;
        public List<String> chain = new ArrayList<String>();   // unresearched techs, prerequisites first
        public int databanks;
        public float days;
        public boolean manual;   // a tech on the way needs AI cores or colony items, which Auto leaves to you
    }

    static List<Need> needs = new ArrayList<Need>();

    /** Terraforming Made Easy's own research gate for one of its structures (BaseDevelopmentIndustry.getAOTDVOKTechId), or null. */
    static String tmeTech(MarketAPI m, String industryId) {
        try {
            com.fs.starfarer.api.loading.IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(industryId);
            if (sp == null) return null;
            Object r = CARefl.call(sp.getNewPluginInstance(m), "getAOTDVOKTechId");
            return r instanceof String && !((String) r).isEmpty() ? (String) r : null;
        } catch (Throwable t) { return null; }
    }

    /** The unresearched techs id still needs, prerequisites first, with id last. */
    static void closure(String id, Map<String, Tech> open, Set<String> seen, List<String> out) {
        if (!seen.add(id)) return;
        Tech t = open.get(id);
        if (t == null) return;   // researched, or unknown
        for (String p : t.prereqs) closure(p, open, seen, out);
        out.add(id);
    }

    /**
     * Each Approve or Auto colony's open plan steps that research locks: the step building, and the buildings it's
     * upgraded from, traced to the tech that unlocks each. The step's plan value is shared along the chain by days.
     */
    static List<Need> planNeeds(Map<String, Tech> open) {
        Set<String> blocked = new HashSet<String>();
        try { return needsFor(open, blocked); }
        finally { CAPlan.BLOCKED.clear(); CAPlan.BLOCKED.addAll(blocked); }
    }

    static List<Need> needsFor(Map<String, Tech> open, Set<String> blocked) {
        Map<String, String> byIndustry = new HashMap<String, String>();
        for (Tech t : open.values()) for (String ind : t.industries) if (!t.unlockedByCommission) byIndustry.put(ind, t.id);
        List<Need> out = new ArrayList<Need>();
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if ((c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) || CAEngine.isOutpost(m) || CAHypershunt.is(m)) continue;
            CAPlan.Plan plan;
            try { plan = CAPlan.build(m, c.profile); } catch (Throwable e) { continue; }
            for (CAPlan.Step st : plan.steps) {
                if (st.kind != CAPlan.Kind.BUILD || st.done(m)) continue;
                float value = CAPlan.bonusFor(m, plan, st.id, false) * (CASurplus.isFocus(m) ? 2f : 1f);   // the focus world's plan counts twice
                if (value <= 0f) continue;
                List<String> chain = new ArrayList<String>();
                Set<String> seen = new HashSet<String>();
                String cur = st.id;
                boolean nextLocked = false;   // the building the colony must add next on the way: the last one before what it has
                for (int k = 0; k < 6 && cur != null; k++) {   // the step, then what it's upgraded from
                    if (m.hasIndustry(cur)) break;
                    String tech = byIndustry.get(cur);
                    if (tech == null && cur.startsWith("tme_")) tech = tmeTech(m, cur);
                    if (tech != null) closure(tech, open, seen, chain);
                    nextLocked = tech != null && open.containsKey(tech);   // TME names its tech whether or not it's researched
                    com.fs.starfarer.api.loading.IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(cur);
                    cur = sp == null ? null : sp.getDowngrade();
                }
                if (nextLocked) blocked.add(m.getId() + ":" + st.id);
                if (chain.isEmpty()) continue;
                Need n = new Need();
                n.step = st.label; n.colony = m.getName(); n.value = value; n.chain = chain;
                for (String id : chain) {
                    Tech t = open.get(id);
                    n.days += Math.max(1f, t.days - t.spent);
                    n.databanks += t.databanks();
                    if (!t.autoOk()) n.manual = true;
                }
                out.add(n);
            }
        }
        share(out, open);
        return out;
    }

    /** Shares each need's value along its chain by research days, and notes on each tech what it's on the way to. */
    static void share(List<Need> needs, Map<String, Tech> open) {
        for (Need n : needs) for (String id : n.chain) {
            Tech t = open.get(id);
            t.plan += n.value * Math.max(1f, t.days - t.spent) / Math.max(1f, n.days);
            String what = n.step + " at " + n.colony;
            if (!t.planFor.contains(what)) t.planFor.add(what);
        }
    }

    /** Every tech you could start now or once its items are in storage, valued and ranked, best first. Refreshed daily. */
    @SuppressWarnings("unchecked")
    public static List<Tech> ranked() throws Exception {
        if (cached != null && now() - cachedAt < 1f) return cached;
        // 0.3.0.0 (A4 fix, 07 section 2 item 5): never ranks while a page draws (the Research page is drawn inside CAIntel.renderPage):
        // ranking evaluates every colony under a what-if. The page shows the last ranking and its age; the daily pass and Refresh rank
        if (CAEngine.drawing > 0) return cached != null ? cached : new ArrayList<Tech>();
        Object mgr = mgr();
        List<Tech> out = new ArrayList<Tech>();
        if (mgr == null) return out;
        Set<String> done = new HashSet<String>();
        Map<String, Object> all = new LinkedHashMap<String, Object>();
        for (Object o : (List<Object>) CARefl.invoke(repo, mgr)) {
            all.put(String.valueOf(CARefl.get(fId, o)), o);
            if (CARefl.getBoolean(fDone, o)) done.add(String.valueOf(CARefl.get(fId, o)));
        }
        List<Tech> cands = new ArrayList<Tech>();
        Map<String, Tech> children = new LinkedHashMap<String, Tech>();
        Map<String, Tech> open = new LinkedHashMap<String, Tech>();
        for (Object o : all.values()) {
            if (CARefl.getBoolean(fDone, o)) continue;
            Tech t = tech(mgr, o);
            open.put(t.id, t);
            if (t.available || t.needsItems) cands.add(t);
        }
        openCount = open.size();
        itemTechs.clear();
        for (Tech t : open.values()) if (!t.autoOk()) itemTechs.add(t);
        try { needs = planNeeds(open); } catch (Throwable e) { needs = new ArrayList<Need>(); CADiagnostics.LOG.warn(CADiagnostics.TAG + "research plan needs failed", e); }
        // techs one step past a candidate, for the "leads to" share
        for (Tech t : open.values()) {
            if (t.available || t.needsItems) continue;
            for (Tech c : cands) {
                boolean ok = !t.prereqs.isEmpty();
                for (String p : t.prereqs) if (!done.contains(p) && !p.equals(c.id)) ok = false;
                if (ok) { children.put(t.id, t); break; }
            }
        }
        List<Tech> scored = new ArrayList<Tech>(cands);
        scored.addAll(children.values());
        valueBuildings(scored);
        for (Tech t : scored) t.own += effects(t);
        for (Tech t : cands) if (t.plan > 0f) {
            t.own += t.plan;
            t.lines.add(0, "On the way to " + CAContracts.join(t.planFor) + " (colony plans): " + CAEngine.valueText(t.plan));
        }
        float fac = facilityUpkeepMult();
        // 0.3.0.0 (B5, research 03 F5): research upkeep is a recurring cost, so it counts in full below spending freely (0.2.45 counted
        // it at a quarter whenever credits were over the surplus line: a one-time stock of credits discounted it forever)
        float money = CASurplus.rich(CAData.settings()) ? CASurplus.CHEAP : 1f;
        for (Tech c : cands) {
            Tech bestChild = null;
            for (Tech ch : children.values()) {
                if (!ch.prereqs.contains(c.id)) continue;
                if (bestChild == null || ch.own > bestChild.own) bestChild = ch;
            }
            c.next = bestChild == null ? 0f : 0.5f * bestChild.own;
            if (bestChild != null && bestChild.own > 0f) c.lines.add("Leads to " + bestChild.name + " (" + CAEngine.valueText(bestChild.own) + ")");
            c.value = c.own + c.next;
            float daysLeft = Math.max(1f, c.days - c.spent);
            c.cost = c.tier * TIER_UPKEEP * fac * daysLeft / 30f * money + c.databanks() * DATABANK_VALUE + c.coreCost();
            c.rank = (12f * c.value - c.cost) / daysLeft;   // a year of what it unlocks, less its cost, per research day
            out.add(c);
        }
        Collections.sort(out, new Comparator<Tech>() { public int compare(Tech a, Tech b) { return Float.compare(b.rank, a.rank); } });
        cached = out; cachedAt = now();
        return out;
    }

    /**
     * Scores the buildings each tech unlocks: marks the techs researched, asks the engine what every colony would build,
     * credits each pick to the tech that unlocks it, then puts the techs back exactly as they were.
     */
    static void valueBuildings(List<Tech> techs) {
        Map<String, Tech> byIndustry = new HashMap<String, Tech>();
        Map<Object, Boolean> was = new LinkedHashMap<Object, Boolean>();
        for (Tech t : techs) {
            if (t.unlockedByCommission) continue;   // already buildable through your commission
            for (String ind : t.industries) byIndustry.put(ind, t);
        }
        if (byIndustry.isEmpty()) return;
        CAData d = CAData.get();
        Map<Tech, Float> total = new HashMap<Tech, Float>();
        Map<Tech, String> where = new HashMap<Tech, String>();
        try {
            for (Tech t : techs) { was.put(t.raw, CARefl.getBoolean(fDone, t.raw)); CARefl.set(fDone, t.raw, true); }
            CAEngine.noProbe = true;
            for (MarketAPI m : Misc.getPlayerMarkets(false)) {
                if (CAHypershunt.is(m)) continue;
                CAEngine.Result r;
                try { r = CAEngine.evaluate(m, d.colony(m.getId()).profile, d.settings.minStability); }
                catch (Throwable e) { continue; }
                Map<Tech, Float> best = new HashMap<Tech, Float>();
                Map<Tech, String> bestName = new HashMap<Tech, String>();
                List<CAEngine.Pick> ps = new ArrayList<CAEngine.Pick>(r.picks);
                if (r.bestNoSlot != null) ps.add(r.bestNoSlot);
                for (CAEngine.Pick p : ps) {
                    Tech t = byIndustry.get(p.id);
                    if (t == null) continue;
                    float v = p == r.bestNoSlot ? 0.5f * p.score : p.score;   // needs a slot freed first
                    if (v > 0f && (!best.containsKey(t) || v > best.get(t))) { best.put(t, v); bestName.put(t, p.name + " at " + m.getName()); }
                }
                for (Map.Entry<Tech, Float> e : best.entrySet()) {
                    Float cur = total.get(e.getKey());
                    total.put(e.getKey(), (cur == null ? 0f : cur) + e.getValue());
                    if (!where.containsKey(e.getKey())) where.put(e.getKey(), bestName.get(e.getKey()));
                }
            }
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "research building valuation failed", e);
        } finally {
            CAEngine.noProbe = false;
            // put every tech back as it was; a failure is retried and logged, never swallowed (a tech left marked researched
            // would be research for free)
            for (Map.Entry<Object, Boolean> e : was.entrySet()) {
                try { CARefl.set(fDone, e.getKey(), e.getValue()); }
                catch (Throwable first) {
                    try { CARefl.set(fDone, e.getKey(), e.getValue()); }
                    catch (Throwable again) { CADiagnostics.LOG.error(CADiagnostics.TAG + "couldn't put a tech's researched flag back after valuing it", again); }
                }
            }
        }
        for (Map.Entry<Tech, Float> e : total.entrySet()) {
            e.getKey().own += e.getValue();
            e.getKey().lines.add("Unlocks buildings worth " + CAEngine.valueText(e.getValue()) + ", best " + where.get(e.getKey()));
        }
    }

    static int outputLines(boolean miningOnly) {
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) for (Industry ind : m.getIndustries()) {
            if (ind.isHidden() || (miningOnly && !ind.getSpec().hasTag("mining"))) continue;
            for (MutableCommodityQuantity q : ind.getAllSupply()) if (q.getQuantity().getModifiedInt() > 0) n++;
        }
        return n;
    }

    static float lineValue(boolean miningOnly) {
        float v = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) for (Industry ind : m.getIndustries()) {
            if (ind.isHidden() || (miningOnly && !ind.getSpec().hasTag("mining"))) continue;
            for (MutableCommodityQuantity q : ind.getAllSupply()) {
                if (q.getQuantity().getModifiedInt() <= 0) continue;
                try { v += Global.getSettings().getCommoditySpec(q.getCommodityId()).getBasePrice() * CAEngine.unitValue; } catch (Throwable ignored) { }
            }
        }
        return v;
    }

    static float upkeep(boolean waterOnly, float hazardCut) {
        float v = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            // the game's own water-world list (Unknown Skies and others add their types to it)
            if (waterOnly && (m.getPlanetEntity() == null || !com.fs.starfarer.api.impl.campaign.econ.impl.Farming.AQUA_PLANETS.contains(m.getPlanetEntity().getTypeId()))) continue;
            float u = 0f;
            for (Industry ind : m.getIndustries()) u += ind.getUpkeep().getModifiedValue();
            v += hazardCut > 0f ? u * hazardCut / Math.max(0.25f, m.getHazardValue()) : u;
        }
        return v;
    }

    /** Monthly value of a tech's effects that aren't buildings, from what VoK's code does (several tooltips claim more). */
    static float effects(Tech t) {
        float v = 0f;
        String what = null;
        if (t.id.equals("aotd_tech_streamlined_production")) { v = lineValue(false); what = "+1 to every output line (" + outputLines(false) + " lines)"; }
        else if (t.id.equals("aotd_tech_advanced_mining_equipment")) { v = lineValue(true); what = "+1 to every mining output line (" + outputLines(true) + " lines)"; }
        else if (t.id.equals("aotd_tech_hazmat_equipment")) { v = upkeep(false, 0.1f) + growthValue(false, 0.1f); what = "hazard -10% on every colony: lower upkeep and faster growth"; }
        else if (t.id.equals("aotd_tech_aquatic_agriculture")) { v = upkeep(true, 0.1f) + growthValue(true, 0.1f); what = "hazard -10% on water worlds: lower upkeep and faster growth"; }
        else if (t.id.equals("aotd_tech_domain_template_construct")) { v = 0.1f * upkeep(false, 0f); what = "upkeep x0.9 on every colony"; }
        else if (t.id.equals("aotd_tech_megastructure_analysis") || t.id.equals("aotd_tech_hp_distribution")) {
            boolean shunt = false;
            for (MarketAPI m : Misc.getPlayerMarkets(false)) if (CAHypershunt.is(m)) shunt = true;
            if (shunt) { v = 25000f; what = t.id.endsWith("analysis") ? "lets your Coronal Hypershunt be restored" : "Hypershunt Receivers: +2 industry slots in their systems"; }
        }
        if (what != null && v > 0f) t.lines.add(Character.toUpperCase(what.charAt(0)) + what.substring(1) + ": " + CAEngine.valueText(v));
        // a Terraforming Made Easy structure surplus terraforming would use (CATerraSim.lockedValue: its best plan at each colony)
        float tf = 0f;
        for (String ind : t.industries) { Float x = CATerraSim.lockedValue.get(ind); if (x != null) tf += x; }
        if (tf > 0f) { v += tf; t.lines.add("Opens surplus terraforming: " + CAEngine.valueText(tf)); }
        return v;
    }

    /**
     * Research upkeep multiplier summed over your facilities: VoK charges each 10,000 x tier a month while a tech runs,
     * times its AI core's multiplier (alpha 0.5, beta 0.75, gamma 0.9). At least 1, so a missing facility isn't free.
     */
    static float facilityUpkeepMult() {
        float sum = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) for (Industry ind : m.getIndustries()) {
            if (!ind.isFunctional() || !(ind.getId().equals("aotd_research_facility") || ind.getId().equals("aotd_blacksite"))) continue;
            String core = ind.getAICoreId();
            sum += "alpha_core".equals(core) ? 0.5f : "beta_core".equals(core) ? 0.75f : "gamma_core".equals(core) ? 0.9f : 1f;
        }
        return Math.max(1f, sum);
    }

    /** The tier of the tech running now (VoK charges 10,000 x tier a month on every facility while it runs), 0 for none. */
    static int runningTier() {
        try {
            Object mgr = mgr();
            Object f = mgr == null ? null : CARefl.invoke(focus, mgr);
            Object tier = f == null ? null : CARefl.get(fTier, f);
            return tier instanceof Enum ? ((Enum<?>) tier).ordinal() : 0;
        } catch (Throwable t) { return 0; }
    }

    /** Hazard cut a tech gives everywhere (Hazmat) or on water worlds (Aquatic Agriculture), as growth: 0.1 hazard is 2 growth a month, x(1 + 0.5 per size past 3). */
    static float growthValue(boolean waterOnly, float hazardCut) {
        float v = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m.getSize() >= Misc.getMaxMarketSize(m)) continue;   // a colony at max size doesn't need it
            if (waterOnly && (m.getPlanetEntity() == null || !com.fs.starfarer.api.impl.campaign.econ.impl.Farming.AQUA_PLANETS.contains(m.getPlanetEntity().getTypeId()))) continue;
            float w6 = CAEngine.WEIGHTS[CAData.get().colony(m.getId()).profile.ordinal()][6];
            v += hazardCut * 10f * w6 / 2f;   // as CAConditions.value counts a condition's hazard against growth
        }
        return v;
    }

    // ---------------- acting ----------------

    static String start(Tech t, String who) {
        try {
            Object mgr = mgr();
            if (mgr == null) return "Research isn't running yet";
            if (CARefl.invoke(focus, mgr) != null) return "Something is already being researched";
            sophiaFirst(mgr, t);
            if (!Boolean.TRUE.equals(CARefl.invoke(canResearch, mgr, t.id, false))) {
                String moved = topUp(mgr, t);
                if (!Boolean.TRUE.equals(CARefl.invoke(canResearch, mgr, t.id, false))) return "Not enough in the Research Facility tab (" + itemsText(t) + " needed)" + (moved == null ? "" : "; " + moved);
            }
            if (!CABreaker.allowed(START)) return "Paused for this session: starting research didn't take effect three times";
            CARefl.invoke(pay, mgr, t.id);
            CARefl.invoke(setFocus, mgr, t.id);
            cached = null;
            Object f = CARefl.invoke(focus, mgr);
            if (!CABreaker.check(START, f != null && t.id.equals(String.valueOf(CARefl.get(fId, f))), "research")) return "VoK didn't start it";
            CALog.add("Research: " + who + " started " + t.name + " (" + Math.round(Math.max(1f, t.days - t.spent)) + " days, " + itemsText(t)
                    + (t.tier > 0 ? ", +" + Misc.getDGSCredits(t.tier * TIER_UPKEEP) + " a month per facility while it runs" : "") + ")");
            return null;
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "starting research " + t.id + " failed", e);
            return "That failed; see starsector.log";
        }
    }

    /** Puts Sophia Anderson at the head of R&D before paying: she takes one databank off every tech. */
    @SuppressWarnings("unchecked")
    static void sophiaFirst(Object mgr, Tech t) throws Exception {
        if (t.databanks() <= 0) return;
        List<Object> council = (List<Object>) CARefl.get(fCouncil, mgr);
        if (council == null) return;
        for (Object s : council) {
            if (s == null || !s.getClass().getSimpleName().equals("SophiaAnderson") || s == CARefl.get(fHead, mgr)) continue;
            CARefl.set(fHead, mgr, s);
            CARefl.invoke(councilAdvance, mgr, 0f);
            CALog.add("Research: made Sophia Anderson head of R&D (one databank less per tech)");
            return;
        }
    }

    /** Research databanks in your fleet's cargo. */
    static float fleetDatabanks() {
        com.fs.starfarer.api.campaign.CampaignFleetAPI f = Global.getSector().getPlayerFleet();
        return f == null || f.getCargo() == null ? 0f : f.getCargo().getCommodityQuantity(DATABANK);
    }

    /** Research databanks in your colonies' Storage tabs (VoK puts a research site's databanks there while the facility is down). */
    static float colonyDatabanks() {
        float n = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) { CargoAPI c = Misc.getStorageCargo(m); if (c != null) n += c.getCommodityQuantity(DATABANK); }
        return n;
    }

    /** Databanks research can use: the Research Facility tabs, every colony's Autopilot Storage (and what's on its way), your colonies' Storage and your fleet's cargo. */
    static float databanksOnHand(Object mgr) throws Exception {
        return ((Number) CARefl.invoke(itemCount, mgr, DATABANK)).floatValue() + CAStores.count(DATABANK) + CAStores.inTransit(DATABANK, null)
                + colonyDatabanks() + fleetDatabanks();
    }

    static boolean isCommodity(String id) {
        try { return Global.getSettings().getCommoditySpec(id) != null; } catch (Throwable t) { return false; }
    }

    /** How many of item id a cargo holds: a commodity, or a special item such as a colony item. */
    static float count(CargoAPI c, String id) {
        if (c == null) return 0f;
        return isCommodity(id) ? c.getCommodityQuantity(id) : c.getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(id, null));
    }

    static void move(CargoAPI from, CargoAPI to, String id, int n) {
        if (n <= 0) return;
        if (isCommodity(id)) { from.removeCommodity(id, n); to.addCommodity(id, n); return; }
        SpecialItemData data = new SpecialItemData(id, null);
        from.removeItems(CargoAPI.CargoItemType.SPECIAL, data, n);
        to.addSpecial(data, n);
    }

    /**
     * Whether everything t needs is somewhere research may take it from: the Research Facility tabs, any colony's Autopilot
     * Storage or on its way there, databanks (and, while spending freely or with Research spends colony items, colony
     * items) in your colonies' Storage, databanks in your fleet's cargo, and with From storage and buildings, buildings.
     */
    static boolean covered(Object mgr, Tech t) throws Exception {
        for (Map.Entry<String, Integer> e : t.items.entrySet()) {
            String id = e.getKey();
            float have = ((Number) CARefl.invoke(itemCount, mgr, id)).floatValue() + CAStores.count(id) + CAStores.inTransit(id, null)
                    + (DATABANK.equals(id) ? colonyDatabanks() + fleetDatabanks() : spendable(id) ? colonyCount(id)
                    + (itemMode == 2 && colonyItem(id) ? installedCount(id) : 0f) : 0f);
            if (have < e.getValue()) return false;
        }
        return true;
    }

    /** The Research Facility tab research takes its items from: the one holding the most databanks already. */
    static SubmarketAPI facilityTab() {
        SubmarketAPI best = null; float bestHas = -1f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            SubmarketAPI sm = m.getSubmarket(SUBMARKET);
            if (sm == null || sm.getCargo() == null) continue;
            float has = sm.getCargo().getCommodityQuantity(DATABANK);
            if (has > bestHas) { bestHas = has; best = sm; }
        }
        return best;
    }

    /**
     * Gets everything a tech is short of into the Research Facility tab (facilityTab), never for free across the sector
     * (0.2.37.0): what's at that colony (its Autopilot Storage, databanks in its Storage, colony items there when allowed,
     * your fleet's databanks while it's in that system) goes straight in; anything elsewhere (another colony's Autopilot
     * Storage or Storage, a building there with From storage and buildings, your fleet's databanks away from it) is shipped
     * to that colony's Autopilot Storage for a fee and travel time, and the tech starts once it has all arrived. Nothing
     * moves unless the whole tech is covered. Returns what it did, or null if nothing.
     * 0.3.0.0 fix (C4 review, research 03 F11): every source for the tech is planned first (planFrom); then all of it moves or none
     * of it does. Nothing moves when the plan can't cover the whole tech at the facility, when its shipments' fees together
     * are more than everything shipped is worth and no colony's plan needs the tech (CAEconomy.techShippingWorth), or when
     * the month's budget or the reserve can't pay those fees. Judged per shipment, the near databanks were shipped and paid
     * for while a far one stayed, the tech still couldn't start, and another tech used what was moved.
     * 0.3.0.0 fix (C4 review 2): the fees are one per source colony (Part.fromId, CAStores.routeKey). Keyed on names, databanks
     * from one colony's Autopilot Storage and its Storage paid two fees for one trip (Zenemosrae: 28,354 for 15,000 of
     * databanks, so the tech didn't ship; with one fee, 14,177, it does).
     */
    /** Set by topUp: some of what the tech needs is on its way to the facility (shipped now or earlier). */
    static boolean topUpPending = false;

    /** 0.3.0.0 fix (C4 review): one planned source of a tech's items: moved straight into the tab, or shipped to the facility's colony. */
    static final class Part {
        String id;
        int qty;
        /** The cargo it comes out of; for a pull from buildings elsewhere, that colony's Autopilot Storage it goes through. */
        CargoAPI from;
        /** Where a shipment leaves from (null: moved straight into the tab) and its name for the log (CAStores.Shipment.from). */
        com.fs.starfarer.api.campaign.SectorEntityToken at;
        String fromName;
        /**
         * 0.3.0.0 fix (C4 review 2): the route's source key (CAStores.Shipment.fromId): the source colony's market id, or your
         * fleet's id. A colony's Autopilot Storage, Storage and buildings share it: one trip, one fee.
         */
        String fromId;
        /** Taken out of buildings at this colony first (null: from cargo). */
        MarketAPI pullAt;
        float fee, value;
        boolean ships() { return at != null; }
    }

    /** 0.3.0.0 fix (C4 review): plans up to `need` of an item from one source holding `have`; returns how many. fromId: the route key (C4 review 2). */
    static int planFrom(List<Part> parts, String id, int need, float have, CargoAPI from, com.fs.starfarer.api.campaign.SectorEntityToken at,
                    String fromId, String fromName, MarketAPI pullAt) {
        int k = (int) Math.min(need, Math.max(0f, have));
        if (k <= 0) return 0;
        Part p = new Part();
        p.id = id; p.qty = k; p.from = from; p.at = at; p.fromId = fromId; p.fromName = fromName; p.pullAt = pullAt;
        parts.add(p);
        return k;
    }

    /** 0.3.0.0 fix (C4 review): items of this id in buildings at this colony that Autopilot may take out (pullFromBuildings' rule). */
    static int buildingCount(String id, MarketAPI m) {
        int n = 0;
        CAData.Colony c = CAData.get().colony(m.getId());
        for (Industry ind : m.getIndustries())
            if (ind.getSpecialItem() != null && id.equals(ind.getSpecialItem().getId()) && !c.locks.contains(ind.getId()) && CAMods.leftToYou(ind.getId()) == null) n++;
        return n;
    }

    /**
     * 0.3.0.0 (C4, research 03 F11): what qty of an item is worth when shipped: databanks 5,000 each, AI cores at CORE_VALUE.
     * Anything else (colony items) counts as worth any fee, as before.
     */
    static float shippedValue(String id, int qty) {
        Float each = DATABANK.equals(id) ? Float.valueOf(DATABANK_VALUE) : CORE_VALUE.get(id);
        return each == null ? Float.POSITIVE_INFINITY : each * Math.max(0, qty);
    }

    static String topUp(Object mgr, Tech t) throws Exception {
        topUpPending = false;
        if (!covered(mgr, t)) return null;
        SubmarketAPI tabSm = facilityTab();
        if (tabSm == null) return null;
        MarketAPI f = tabSm.getMarket();
        CargoAPI tab = tabSm.getCargo(), local = CAStores.of(f), localStorage = Misc.getStorageCargo(f);
        com.fs.starfarer.api.campaign.CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        boolean fleetHere = fleet != null && fleet.getCargo() != null && f.getPrimaryEntity() != null
                && fleet.getContainingLocation() == f.getPrimaryEntity().getContainingLocation();
        // 0.3.0.0 fix (C4 review): plan every source first; nothing moves yet
        List<Part> parts = new ArrayList<Part>();
        int coming = 0;
        boolean shortNow = false;
        for (Map.Entry<String, Integer> e : t.items.entrySet()) {
            String id = e.getKey();
            int need = e.getValue() - (int) ((Number) CARefl.invoke(itemCount, mgr, id)).floatValue();
            if (need <= 0) continue;
            // at the facility's colony: straight in
            need -= planFrom(parts, id, need, count(local, id), local, null, null, null, null);
            if (need > 0 && (DATABANK.equals(id) || spendable(id))) need -= planFrom(parts, id, need, count(localStorage, id), localStorage, null, null, null, null);
            if (need > 0 && itemMode == 2 && colonyItem(id)) need -= planFrom(parts, id, need, buildingCount(id, f), tab, null, null, null, f);
            if (need > 0 && DATABANK.equals(id) && fleetHere) need -= planFrom(parts, id, need, count(fleet.getCargo(), id), fleet.getCargo(), null, null, null, null);
            if (need <= 0) continue;
            // already on its way there
            int onWay = CAStores.inTransit(id, f.getId());
            coming += Math.min(need, onWay);
            need -= onWay;
            // from elsewhere: shipped to the facility's colony. 0.3.0.0 (C4, research 03 F11): the nearest source first, each
            // source's whole share in one shipment
            for (CAStores.Store sto : CAStores.nearest(f)) {
                if (need <= 0) break;
                if (sto.market == f) continue;
                need -= planFrom(parts, id, need, count(sto.cargo, id), sto.cargo, sto.market.getPrimaryEntity(), sto.market.getId(), sto.market.getName() + "'s Autopilot Storage", null);
            }
            if (need > 0 && (DATABANK.equals(id) || spendable(id))) for (MarketAPI m : CAStores.byDistance(Misc.getPlayerMarkets(false), f)) {
                if (need <= 0) break;
                if (m == f) continue;
                CargoAPI c = Misc.getStorageCargo(m);
                need -= planFrom(parts, id, need, count(c, id), c, m.getPrimaryEntity(), m.getId(), m.getName() + "'s Storage", null);
            }
            if (need > 0 && itemMode == 2 && colonyItem(id)) for (MarketAPI m : CAStores.byDistance(Misc.getPlayerMarkets(false), f)) {
                if (need <= 0) break;
                if (m == f) continue;
                need -= planFrom(parts, id, need, buildingCount(id, m), CAStores.of(m), m.getPrimaryEntity(), m.getId(), m.getName(), m);
            }
            if (need > 0 && DATABANK.equals(id) && !fleetHere && fleet != null && fleet.getCargo() != null)
                need -= planFrom(parts, id, need, count(fleet.getCargo(), id), fleet.getCargo(), fleet, CAStores.routeKey(fleet, "your fleet's cargo"), "your fleet's cargo", null);
            if (need > 0) shortNow = true;
        }
        // 0.3.0.0 fix (C4 review): the facility can't get all of it now (some of it is on its way somewhere else): nothing moves
        if (shortNow) return null;
        // 0.3.0.0 fix (C4 review): the shipments' fees, one per route (later parts on a route join the first's load). 0.3.0.0 fix
        // (C4 review 2): a route is the source's key (a colony's market id), so its Autopilot Storage, Storage and buildings
        // are one trip with one fee
        List<Part> ships = new ArrayList<Part>();
        for (Part p : parts) if (p.ships()) ships.add(p);
        String[] routes = new String[ships.size()];
        float[] quotes = new float[ships.size()], values = new float[ships.size()];
        for (int i = 0; i < ships.size(); i++) {
            Part p = ships.get(i);
            routes[i] = p.fromId;
            quotes[i] = CAStores.quote(p.at, p.fromId, p.fromName, f);
            values[i] = p.value = shippedValue(p.id, p.qty);
        }
        float[] fees = CAEconomy.routeFees(routes, quotes);
        float feeTotal = 0f, valueTotal = 0f;
        List<String> wheres = new ArrayList<String>();
        for (int i = 0; i < ships.size(); i++) {
            Part p = ships.get(i);
            p.fee = fees[i];
            feeTotal += fees[i];
            valueTotal += values[i];
            wheres.add(p.qty + " " + commodityName(p.id) + " at " + p.fromName);
        }
        if (!CAEconomy.techShippingWorth(fees, values, t.plan > 0f))
            return "waits for shipping costs: " + Misc.getDGSCredits(feeTotal) + " in fees for " + Misc.getDGSCredits(valueTotal) + " of cargo ("
                    + CAContracts.join(wheres) + "); nothing moves until a colony's plan needs it or the fees are lower";
        if (feeTotal > 0f) {
            CASettings s = CAData.settings();
            if (feeTotal > CABudget.available(s) || CABuild.credits() - feeTotal < s.reserve)
                return "shipping it to " + f.getName() + " waits for budget (" + Misc.getDGSCredits(feeTotal) + " in fees); nothing moves yet";
        }
        // all of it moves: straight in first, then the shipments
        Map<String, Integer> movedById = new LinkedHashMap<String, Integer>();
        List<String> pulled = new ArrayList<String>();
        int shipped = 0;
        for (Part p : parts) {
            int q = p.qty;
            if (p.pullAt != null) q = pullFromBuildings(p.id, p.qty, p.from, pulled, p.pullAt);
            else if (!p.ships()) move(p.from, tab, p.id, q);
            if (q <= 0) continue;
            if (!p.ships()) {
                Integer was = movedById.get(p.id);
                movedById.put(p.id, (was == null ? 0 : was) + q);
            } else if (ship(p.from, p.at, p.fromId, p.fromName, f, p.id, q)) shipped += q;
        }
        List<String> moved = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : movedById.entrySet()) moved.add(e.getValue() + " " + commodityName(e.getKey()));
        topUpPending = shipped + coming > 0;
        if (moved.isEmpty() && shipped == 0 && coming == 0) return null;
        String msg = (moved.isEmpty() ? "" : "moved " + CAContracts.join(moved) + " into " + f.getName() + "'s Research Facility tab"
                + (pulled.isEmpty() ? "" : " (taken out of " + CAContracts.join(pulled) + ")"))
                + (shipped + coming > 0 ? (moved.isEmpty() ? "" : "; ") + (shipped + coming) + " more on the way to " + f.getName() + ", "
                + t.name + " starts once they arrive" : "");
        if (!moved.isEmpty() || shipped > 0) CALog.add("Research: " + msg);
        return msg;
    }

    /** Ships qty of a commodity or (generic) research item to a colony's Autopilot Storage. fromId: the route key (0.3.0.0 fix, C4 review 2). */
    static boolean ship(CargoAPI from, com.fs.starfarer.api.campaign.SectorEntityToken at, String fromId, String fromName, MarketAPI to, String id, int qty) {
        if (isCommodity(id)) return CAStores.ship(from, at, fromId, fromName, to, id, null, qty, "for research");
        return CAStores.ship(from, at, fromId, fromName, to, null, new SpecialItemData(id, null), qty, "for research");
    }

    @SuppressWarnings("unchecked")
    static boolean idle(Object mgr) throws Exception {
        if (CARefl.invoke(focus, mgr) != null) return false;
        List<Object> q = (List<Object>) CARefl.invoke(queued, CARefl.invoke(queue, mgr));
        return q == null || q.isEmpty();
    }

    /** What Auto does with the techs as ranked: the ones to try starting, in order, and why the others can't go. */
    static class Choice {
        final List<Tech> tries = new ArrayList<Tech>();
        final Set<Tech> filler = new HashSet<Tech>();   // tries that don't pay for themselves within a year: they only keep research going
        final Set<Tech> pastWait = new HashSet<Tech>();   // tries no plan needs, let through because the plan tech has waited WAIT_DAYS
        Tech waiting;   // the best plan tech that's short of databanks: the ones you have are kept for it
        Tech capped;    // the best tech that waits for next month's new-upkeep room
        final List<Tech> shortDatabanks = new ArrayList<Tech>(), shortCores = new ArrayList<Tech>(), manual = new ArrayList<Tech>();
        /** 0.3.0.0 (B5): techs no plan needs that don't pay for themselves within a year, held back (fillers only with idle money). */
        final List<Tech> noPay = new ArrayList<Tech>();
    }

    /**
     * 0.3.0.0 (WS-B5, research 01 section 9, 03 F5): whether Auto may start a tech that doesn't pay for itself within a year and
     * no plan needs ("the best of the rest"): only with idle money (spending freely, or the money ladder's Idle money tier),
     * and never while a plan tech waits for databanks. 9 of 22 starts in the author's game were such fillers: they burned 45 of
     * 143 databanks, and then the plan's Antimatter Production never had the 17 it needed.
     */
    static boolean fillersAllowed(CASettings s) {
        try { return CASurplus.rich(s) || CARich.tier() >= CARich.T2; } catch (Throwable t) { return false; }
    }

    /** Pure, 0.3.0.0 (B5): a plan tech Auto could start is short of databanks (fillers never go then, 03 F5). */
    static boolean planWaiting(List<Tech> ranked, float have) {
        for (Tech t : ranked) if (t.plan > 0f && t.autoOk() && t.needsItems && have < t.databanks()) return true;
        return false;
    }

    /** 0.2.25.0's choice, with fillers (the offline check of that rule). */
    static Choice choose(List<Tech> ranked, float have, boolean waitedLong, float upkeepRoom, float fac, Set<String> covered) {
        return choose(ranked, have, waitedLong, upkeepRoom, fac, covered, true);
    }

    /**
     * Techs that pay for themselves within a year come first, best first. After them come the rest, best first, so the
     * Research Facility doesn't sit idle while there are databanks for something: before 0.2.25.0 Auto stopped at the first
     * tech that didn't pay, and when no colony had room for what the remaining techs unlock, research sat idle without a word.
     * Databanks are held for a plan tech that's short of them (waiting) until it has waited WAIT_DAYS; a tech no plan needs
     * waits while its upkeep would pass this month's new-upkeep room. covered: the ids of techs short of items whose items
     * are all on hand. 0.3.0.0 (B5): fillers false: of the techs that don't pay, only plan techs go (fillersAllowed).
     */
    static Choice choose(List<Tech> ranked, float have, boolean waitedLong, float upkeepRoom, float fac, Set<String> covered, boolean fillers) {
        Choice c = new Choice();
        for (int pass = 0; pass < 2; pass++) {
            for (Tech t : ranked) {
                if ((t.rank > 0f) != (pass == 0)) continue;
                if (!t.autoOk()) { c.manual.add(t); continue; }
                if (pass == 1 && t.plan <= 0f && !fillers) { c.noPay.add(t); continue; }   // 0.3.0.0 (B5): no filler without idle money
                if (t.needsItems && have < t.databanks()) { if (c.waiting == null && t.plan > 0f) c.waiting = t; c.shortDatabanks.add(t); continue; }
                if (t.needsItems && !covered.contains(t.id)) { c.shortCores.add(t); continue; }   // AI cores it needs aren't in Autopilot Storage
                if (c.waiting != null && t.plan <= 0f && !waitedLong) continue;   // no plan needs it; keep the databanks
                if (t.plan <= 0f && t.tier * TIER_UPKEEP * fac > upkeepRoom) {   // research upkeep ends with the tech; plan techs go anyway
                    if (c.capped == null) c.capped = t;
                    continue;
                }
                c.tries.add(t);
                if (pass == 1) c.filler.add(t);
                if (c.waiting != null && t.plan <= 0f) c.pastWait.add(t);
            }
        }
        return c;
    }

    /** Why Auto has nothing to start, for the log. */
    static String idleReason(Choice c, float have, boolean anyOpen) {
        List<String> why = new ArrayList<String>();
        if (c.capped != null) why.add(c.capped.name + " waits for next month: its upkeep would pass this month's new-upkeep room");
        if (!c.shortDatabanks.isEmpty()) {
            Tech cheapest = null;
            for (Tech t : c.shortDatabanks) if (cheapest == null || t.databanks() < cheapest.databanks()) cheapest = t;
            why.add((c.shortDatabanks.size() == 1 ? cheapest.name + " needs" : c.shortDatabanks.size() + " techs need") + " more research databanks than the "
                    + (int) have + " you have" + (c.shortDatabanks.size() == 1 ? " (" + cheapest.databanks() + ")" : " (cheapest: " + cheapest.name + ", " + cheapest.databanks() + ")")
                    + ". Databanks come from salvage (research stations, derelicts) and from a Research Facility on a pre-collapse research site");
        }
        if (!c.shortCores.isEmpty()) why.add(names(c.shortCores) + " need AI cores that aren't in Autopilot Storage");
        if (!c.manual.isEmpty()) why.add(names(c.manual) + (c.manual.size() == 1 ? " uses" : " use") + " colony items, which Auto leaves to you");
        // 0.3.0.0 (B5): fillers held back without idle money
        if (!c.noPay.isEmpty()) why.add(names(c.noPay) + (c.noPay.size() == 1 ? " doesn't" : " don't") + " pay back within a year, and no plan needs "
                + (c.noPay.size() == 1 ? "it" : "them") + " (Auto starts those only with idle money)");
        if (why.isEmpty()) why.add(anyOpen ? "every remaining tech needs another researched first" : "everything is researched");
        return String.join("; ", why);
    }

    static String names(List<Tech> techs) {
        List<String> n = new ArrayList<String>();
        for (Tech t : techs) { if (n.size() == 3) { n.add((techs.size() - 3) + " more"); break; } n.add(t.name); }
        return CAContracts.join(n);
    }

    /**
     * Auto: when nothing is being researched or queued, starts the best tech that costs only databanks and AI cores, or,
     * when none pays for itself within a year, the best of the rest. When nothing can start, the log says why.
     */
    public static void run(CAData d, CASettings s) {
        spendItems = CASurplus.rich(s);
        int was = itemMode;
        updateItemMode(s);
        if (was != itemMode) cached = null;   // which techs Auto may start changed
        // 0.3.0.0 (A4 fix): the Research page never ranks while it draws, so the daily pass keeps one ranking a day for it while the page shows (research not Off)
        if (s.researchMode != 2) {
            try { ranked(); } catch (Throwable t) { CAStep.warn("research step", "research ranking failed", t); }
        }
        if (s.researchMode != 1) return;
        try {
            Object mgr = mgr();
            if (mgr == null || !idle(mgr)) return;
            if (((Number) CARefl.invoke(facilities, mgr)).intValue() <= 0) {
                if (CALog.once("research:nofacility")) CALog.quiet("Research: no working Research Facility, so nothing can be researched");   // 0.3.0.9: starsector.log only
                return;
            }
            CALog.forget("research:nofacility");
            if (CABudget.paused(s)) return;
            float fac = facilityUpkeepMult();
            float have = databanksOnHand(mgr);
            // after WAIT_DAYS of holding them, the best tech you can afford goes ahead anyway, rather than research sitting idle
            boolean waitedLong = d.researchWaitSince != 0L && Global.getSector().getClock().getElapsedDaysSince(d.researchWaitSince) >= WAIT_DAYS;
            List<Tech> all = ranked();
            Set<String> covered = new HashSet<String>();
            for (Tech t : all) if (t.needsItems && covered(mgr, t)) covered.add(t.id);
            // 0.3.0.0 (B5, 01 section 9): a tech that doesn't pay back within a year and no plan needs only goes with idle money,
            // and never while a plan tech waits for databanks
            boolean fillers = fillersAllowed(s) && !planWaiting(all, have);
            Choice c = choose(all, have, waitedLong, CABudget.upkeepCap(s) - s.upkeepAdded, fac, covered, fillers);
            Tech waiting = c.waiting;
            // 0.2.42.4: one tech at a time. Before, each pass went down the list: every tech short of databanks at the facility
            // had them shipped over ("starts once they arrive"), five or six at once on screen, and then a cheap filler
            // started and used the databanks just moved for the first. Now the tech whose databanks are on their way goes
            // first, and once a tech's databanks are shipped, nothing else is shipped or started until they've arrived
            List<Tech> order = new ArrayList<Tech>(c.tries);
            Tech staged = null;
            if (d.researchStaging != null) for (Tech t : order) if (t.id.equals(d.researchStaging)) { staged = t; break; }
            if (staged != null) { order.remove(staged); order.add(0, staged); }
            else d.researchStaging = null;   // gone from the list (researched, locked): nothing is held for it
            for (Tech t : order) {
                String who = c.pastWait.contains(t) ? "Auto (" + waiting.name + " has waited " + (int) WAIT_DAYS + " days for databanks)"
                        : c.filler.contains(t) ? "Auto (no tech pays for itself within a year yet, so the best of the rest keeps research going)" : "Auto";
                topUpPending = false;
                String failed = start(t, who);
                if (failed == null) {
                    CABudget.charge(s, 0f, 0f, t.tier * TIER_UPKEEP * fac);
                    d.researchWaitSince = 0L;
                    d.researchStaging = null;
                    return;
                }
                if (topUpPending) {   // its databanks are on the way (topUp said so on screen once): wait for them, nothing else
                    d.researchStaging = t.id;
                    return;
                }
                if (CALog.once("research:fail:" + t.id + ":" + failed)) CALog.quiet("Research: Auto couldn't start " + t.name + ": " + failed);   // 0.3.0.9: starsector.log only
            }
            if (waiting == null) d.researchWaitSince = 0L;
            else if (d.researchWaitSince == 0L) d.researchWaitSince = Global.getSector().getClock().getTimestamp();
            if (waiting != null) {
                // 0.3.0.9: the advice on screen, where databanks come from in starsector.log; once per tech (the count on hand
                // was in the key, which logged it again each time the count changed)
                if (CALog.once("research:wait:" + waiting.id)) {
                    CALog.add("Research: " + waiting.name + " is next for " + CAContracts.join(waiting.planFor) + ". Needs " + waiting.databanks()
                            + " databanks. On hand: " + (int) have + ".");
                    CALog.quiet("Research: databanks come from salvage (research stations, derelicts) and from a Research Facility on a pre-collapse "
                            + "research site; Autopilot uses them from Autopilot Storage, colony Storage or your cargo");
                }
            } else if (c.tries.isEmpty()) {
                String why = idleReason(c, have, openCount > 0);
                if (CALog.once("research:idle:" + why)) CALog.quiet("Research: Auto has nothing it can start: " + why);   // 0.3.0.9: starsector.log only
            }
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "research step failed", t);
        }
    }

    /** A panel button: start <techId>. Returns a message. */
    public static String handle(String action, String id) {
        try {
            if ("refresh".equals(action)) { cached = null; ranked(); return null; }   // 0.3.0.0 (A4 fix): the Research page's Refresh ranking button (runs outside drawing)
            for (Tech t : ranked()) if (t.id.equals(id)) return "start".equals(action) ? start(t, "you") : "Unknown action";
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "research action failed", e);
            return "That failed; see starsector.log";
        }
        return "That tech can't be started now";
    }

    // ---------------- empire panel ----------------

    public static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        // 0.3.0.0 (WS-F3 fix, research 04 section 7 #3): the mode as a readout, one short line and the rules on hover (was one 149-word paragraph)
        CATheme.mode(t, CASettings.CONTRACT_NAMES[Math.max(0, Math.min(2, s.researchMode))]);
        t.addPara("Auto starts the best-scored tech when nothing is queued.", 4f);
        CATheme.hover(t, "Techs that use colony items are yours.");
        try {
            Object mgr = mgr();
            if (mgr == null) { t.addPara("Research hasn't started in this save.", gray, 6f); return; }
            int fac = ((Number) CARefl.invoke(facilities, mgr)).intValue();
            Object cur = CARefl.invoke(focus, mgr);
            if (cur != null) {
                Tech c = tech(mgr, cur);
                t.addPara("Researching %s: %s of %s days.", 6f, hl, c.name, "" + Math.round(c.spent), "" + Math.round(c.days));
            } else t.addPara("Nothing is being researched.", 6f);
            if (fac <= 0) t.addPara("No working Research Facility.", Misc.getNegativeHighlightColor(), 4f);
            else t.addPara("%s facilities. Databanks: %s there, %s in Storage, %s in your fleet.", 4f, hl, "" + fac,
                    "" + (int) ((Number) CARefl.invoke(itemCount, mgr, DATABANK)).floatValue(), "" + (int) CAStores.count(DATABANK),
                    "" + (int) fleetDatabanks());
            t.addPara("Databanks on hand: %s.", 4f, hl, "" + (int) databanksOnHand(mgr));
            // 0.3.0.0 (C4, research 03 F5): credits per tech. Every facility pays the running tech's upkeep; each past the first adds 10% speed
            if (fac > 1) {
                float per = CAEconomy.costPerTech(facilityUpkeepMult(), fac);
                t.addPara("Upkeep: %s a month per tech tier and facility. %s facilities: %s faster, %s times the cost.",
                        4f, gray, hl, Misc.getDGSCredits(TIER_UPKEEP), "" + fac, Math.round((CASurplusResearch.speed(fac) - 1f) * 100f) + " percent",
                        String.format(java.util.Locale.ROOT, "%.2f", per));
            }
            List<Tech> rankedNow = ranked();
            // 0.3.0.0 (A4 fix): the ranking is the daily pass's (or Refresh's); drawing the page never ranks
            ButtonAPI rr = t.addButton("Refresh ranking", new String[]{"research", "refresh", ""}, base, dark, Alignment.MID, CutStyle.TL_BR, 160f, 22f, 8f);
            float age = now() - cachedAt;
            com.fs.starfarer.api.ui.LabelAPI rn = t.addPara(cached == null ? "Not ranked yet."
                    : age < 1f ? "Ranked today." : "Ranked " + (int) age + ((int) age == 1 ? " day ago." : " days ago."), gray, 0f);
            rn.getPosition().rightOfMid(rr, 10f);
            CALog.endRow(t, rr);
            if (!needs.isEmpty()) {
                t.addPara("Plans waiting on research:", 8f);
                int k = 0;
                for (Need n : needs) {
                    if (k++ >= 6) break;
                    List<String> names = new ArrayList<String>();
                    for (Tech x : rankedNow) if (n.chain.contains(x.id)) names.add(x.name);
                    t.addPara("    %s at %s: %s techs, %s databanks, about %s days" + (n.manual ? " (needs items)" : ""), 2f, hl,
                            CALog.esc(n.step), CALog.esc(n.colony), "" + n.chain.size(), "" + n.databanks, "" + Math.round(n.days));
                }
            }
            boolean idle = idle(mgr);
            if (!idle && s.researchMode == 1) t.addPara("Auto waits for an empty queue.", gray, 4f);
            int shown = 0;
            for (Tech x : rankedNow) {
                if (shown++ >= 6) break;
                t.addPara("%s (%s days, %s): %s a month" + (x.needsItems ? (covered(mgr, x) ? ", items in Storage" : ", items short") : "") + (!x.autoOk() ? ", items: yours" : ""),
                        8f, x.rank > 0f ? hl : gray, x.name, "" + Math.round(Math.max(1f, x.days - x.spent)), itemsText(x), CAEngine.signed(x.value));
                for (int j = 0; j < Math.min(3, x.lines.size()); j++) t.addPara("    " + CALog.esc(x.lines.get(j)), gray, 2f);
                if (idle && s.researchMode != 2) {
                    ButtonAPI b = t.addButton("Start", new String[]{"research", "start", x.id}, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 4f);
                    b.setEnabled(fac > 0);
                }
            }
            if (shown == 0) t.addPara("No tech can be started yet.", gray, 6f);
            if (!itemTechs.isEmpty()) {
                t.addPara("Techs that take colony items (yours):", 8f);
                int k = 0;
                for (Tech x : itemTechs) {
                    if (k++ >= 8) break;
                    List<String> parts = new ArrayList<String>();
                    for (Map.Entry<String, Integer> e : x.items.entrySet()) {
                        if (DATABANK.equals(e.getKey())) continue;
                        int have = 0;
                        try { have = (int) ((Number) CARefl.invoke(itemCount, mgr, e.getKey())).floatValue(); } catch (Throwable ignored) { }
                        parts.add(e.getValue() + " " + commodityName(e.getKey()) + " (have " + have + "; from " + whereFrom(e.getKey()) + ")");
                    }
                    t.addPara("    %s: " + CALog.esc(CAContracts.join(parts)) + (x.otherText != null ? ". Also: " + CALog.esc(x.otherText) : ""), 2f, hl, x.name);
                }
            }
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "research section failed", e);
            t.addPara(CATheme.NOT_SHOWN, gray, 6f);
        }
    }
}
