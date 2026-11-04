package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Special project supply (2.30.0). Vaults of Knowledge's black site projects take goods every month from your own colonies'
 * production (a Theory of Toolbox trade contract), and a stage stops dead while any good it takes isn't delivered at all.
 * Several are AotD goods no faction makes (Electronic Components, Domain-grade machinery, Refined Metal, Enriched
 * Transplutonics): only VoK's upgraded industries make them, and research unlocks those. For the project you're running,
 * every good your colonies don't make gets a producer: the building (and the producers of the AotD goods it needs itself)
 * becomes a first plan step at the Approve or Auto colony best placed for it, so CAResearch works toward the techs that
 * unlock it and Auto builds or upgrades it, even if it earns less than it costs (CAEngine.finish). Where a colony has the
 * building it's upgraded from, that colony goes first; Domain-grade machinery prefers Orbital Works to Orbital Skunkworks
 * (it keeps making ships), and never takes the empire's only ship maker for Civilian Heavy Production. What Autopilot can't
 * do (no colony can host it, AI cores, colony items or ships a stage takes) is logged once and shown on the empire panel's
 * Autopilot Storage page. Setting: Settings, Items and AI cores. Reflection only; without VoK it does nothing.
 */
public class CAProjects {
    static final String MGR = "data.kaysaar.aotd.vok.scripts.specialprojects.BlackSiteProjectManager";

    /** A building that makes one of the goods special projects take: what it's upgraded from (and that one's own base), and the AotD goods it needs itself (VoK 5.0.7). */
    static class Producer {
        final String good, id, base, root;
        final int minSize;
        final String[] inputs;
        Producer(String good, String id, String base, String root, int minSize, String... inputs) {
            this.good = good; this.id = id; this.base = base; this.root = root; this.minSize = minSize; this.inputs = inputs;
        }
    }

    /** In order of preference for each good. */
    static final Producer[] PRODUCERS = {
            new Producer("advanced_components", "aotd_hightech_industry", "lightindustry", null, 3, "domain_heavy_machinery"),
            new Producer("domain_heavy_machinery", "triheavy", "orbitalworks", "heavyindustry", 6, "purified_rare_metal", "advanced_components"),
            new Producer("domain_heavy_machinery", "aotd_civilian_heavy_prod", "heavyindustry", null, 3, "refined_metal", "purified_rare_metal"),
            new Producer("refined_metal", "aotd_crystalizator", "refining", null, 3),
            new Producer("purified_rare_metal", "aotd_enrichment_facility", "refining", null, 3),
            new Producer("ships", "heavyindustry", null, null, 3),
            new Producer("hand_weapons", "heavyindustry", null, null, 3),
    };

    /** A colony as the planner sees it. */
    static class Host {
        String id, name;
        int size, freeSlots;
        boolean auto;
        Set<String> has = new HashSet<String>();        // building ids it has, exactly
        Set<String> occupied = new HashSet<String>();   // bases already upgraded into something else (Crystalizator for Refining), or kept by id for its plan
        Set<String> unlocked = new HashSet<String>();   // producers research has opened here
        int ships;                                       // ships it makes
    }

    /** One producer to add: at which colony, and how far it is from there. */
    static class Plan {
        String host, hostName, building, good;
        int route;          // 2 upgrade the base it has, 1 upgrade twice (Heavy Industry to Orbital Works to the producer), 0 build the base first
        boolean locked;     // research hasn't opened it here yet
    }

    static class Result {
        List<Plan> plans = new ArrayList<Plan>();
        List<String> unresolved = new ArrayList<String>();   // goods no colony can host a producer for
        Set<String> covered = new HashSet<String>();         // goods whose producer is built but not producing (its inputs are planned)
    }

    static List<Producer> producersOf(String good) {
        List<Producer> out = new ArrayList<Producer>();
        for (Producer p : PRODUCERS) if (p.good.equals(good)) out.add(p);
        return out;
    }

    /** 2 the colony has the producer's base, 1 its base's base, 0 neither (a new industry), -1 that chain is taken by another upgrade. */
    static int route(Host h, Producer p) {
        if (p.base == null) return h.occupied.contains(p.id) ? -1 : 0;
        if (h.occupied.contains(p.base) || (p.root != null && h.occupied.contains(p.root))) return -1;
        if (h.has.contains(p.base)) return 2;
        if (p.root != null && h.has.contains(p.root)) return 1;
        return 0;
    }

    /**
     * Producers for the goods nobody in your empire makes: for each, the best colony (the building it's upgraded from there
     * already, research open, bigger, Auto), then the AotD goods that producer needs itself if you don't make them either.
     * Pure, so it can be checked offline.
     */
    static Result plan(Collection<String> missing, List<Host> hosts, Set<String> builtAnywhere, Map<String, Integer> made, int empireShips) {
        Result r = new Result();
        Deque<String> queue = new ArrayDeque<String>(missing);
        Set<String> seen = new HashSet<String>();
        Map<String, Set<String>> claimed = new HashMap<String, Set<String>>();
        while (!queue.isEmpty()) {
            String good = queue.poll();
            if (!seen.add(good)) continue;
            List<Producer> ps = producersOf(good);
            if (ps.isEmpty()) { r.unresolved.add(good); continue; }
            Producer built = null;
            for (Producer p : ps) if (builtAnywhere.contains(p.id) && built == null) built = p;
            if (built != null) {   // built but not making it: its own inputs are what's missing
                r.covered.add(good);
                for (String in : built.inputs) if (!madeAny(made, in)) queue.add(in);
                continue;
            }
            Host bestH = null; Producer bestP = null; float best = -1e9f; int bestRoute = -1;
            for (int i = 0; i < ps.size(); i++) {
                Producer p = ps.get(i);
                for (Host h : hosts) {
                    if (h.size < p.minSize) continue;
                    Set<String> cl = claimed.get(h.id);
                    if (cl != null && (cl.contains(p.base) || (p.root != null && cl.contains(p.root)))) continue;
                    int rt = route(h, p);
                    if (rt < 0 || (rt == 0 && h.freeSlots <= 0)) continue;
                    float sc = rt * 100f + (h.unlocked.contains(p.id) ? 50f : 0f) + h.size * 5f + (h.auto ? 30f : 0f) + (ps.size() - i) * 10f;
                    if (p.id.equals("aotd_civilian_heavy_prod") && h.ships > 0 && empireShips - h.ships <= 0) continue;   // the only ship maker stays one
                    if (sc > best) { best = sc; bestH = h; bestP = p; bestRoute = rt; }
                }
            }
            if (bestH == null) { r.unresolved.add(good); continue; }
            Plan pl = new Plan();
            pl.host = bestH.id; pl.hostName = bestH.name; pl.building = bestP.id; pl.good = good; pl.route = bestRoute;
            pl.locked = !bestH.unlocked.contains(bestP.id);
            r.plans.add(pl);
            Set<String> cl = claimed.get(bestH.id);
            if (cl == null) { cl = new HashSet<String>(); claimed.put(bestH.id, cl); }
            if (bestP.base != null) cl.add(bestP.base);
            if (bestP.root != null) cl.add(bestP.root);
            if (bestRoute == 0) bestH.freeSlots--;
            for (String in : bestP.inputs) if (!madeAny(made, in)) queue.add(in);
        }
        return r;
    }

    static boolean madeAny(Map<String, Integer> made, String good) { Integer n = made.get(good); return n != null && n > 0; }

    // ---------------- the game ----------------

    /** Plan steps by colony id, worked out once a day; CAPlan.build puts them first. */
    static final Map<String, List<CAPlan.Step>> STEPS = new HashMap<String, List<CAPlan.Step>>();
    static float computedAt = -99f;
    static boolean computing = false;
    static String project;                                   // the running project's name, or null
    static Map<String, Integer> monthly = new LinkedHashMap<String, Integer>(), left = new LinkedHashMap<String, Integer>();
    static Map<String, Integer> yours = new HashMap<String, Integer>(), sector = new HashMap<String, Integer>();
    static List<String> other = new ArrayList<String>();     // AI cores, items and ships the stages still take
    static Result last;
    static boolean broken = false;

    public static void reset() { STEPS.clear(); computedAt = -99f; project = null; last = null; monthly.clear(); left.clear(); other.clear(); }

    static boolean on() {
        return !broken && Global.getSettings().getModManager().isModEnabled(CAResearch.MOD) && CAData.settings().projectSupplyMode != 2;
    }

    /** The plan steps special projects add at this colony (none while they're being worked out). */
    static List<CAPlan.Step> stepsFor(MarketAPI m) {
        if (computing || m == null || STEPS.isEmpty()) return Collections.emptyList();
        List<CAPlan.Step> l = STEPS.get(m.getId());
        return l == null ? Collections.<CAPlan.Step>emptyList() : l;
    }

    /** Whether this pick is a special project's step at m, or on the way to one: it's built even if it earns less than it costs. */
    static boolean isStep(MarketAPI m, String industryId) {
        for (CAPlan.Step s : stepsFor(m)) if (s.id.equals(industryId) || CAPlan.leadsTo(industryId, s.id)) return true;
        return false;
    }

    static String name(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getName(); } catch (Throwable t) { return com; }
    }

    static String buildingName(String id) {
        try { return Global.getSettings().getIndustrySpec(id).getName(); } catch (Throwable t) { return id; }
    }

    /** Units a month your colonies make: ToT's figure (what its contracts deliver from), else the game's supply. */
    static int yours(String com) {
        Integer tot = CAContracts.production(com);
        if (tot != null) return tot;
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) n += supply(m, com);
        return n;
    }

    static int supply(MarketAPI m, String com) {
        try { CommodityOnMarketAPI c = m.getCommodityData(com); return c == null ? 0 : c.getMaxSupply(); } catch (Throwable t) { return 0; }
    }

    /** Units a month every other market in the sector makes. */
    static int others(String com) {
        int n = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) if (!m.isPlayerOwned()) n += supply(m, com);
        return n;
    }

    /** Reads the running project: what its unfinished stages still take, by good, and this month's share. */
    @SuppressWarnings("unchecked")
    static boolean read() throws Exception {
        monthly.clear(); left.clear(); other.clear(); project = null;
        Class<?> c = Class.forName(MGR, true, Global.getSettings().getScriptClassLoader());
        Object mgr = CARefl.invoke(CARefl.method(c, "getInstance"), null);
        Object p = mgr == null ? null : CARefl.call(mgr, "getCurrentlyOnGoingProject");
        if (p == null) return false;
        Object spec = CARefl.call(p, "getProjectSpec");
        Object nm = CARefl.call(p, "getNameOverride");
        project = nm instanceof String && !((String) nm).isEmpty() ? (String) nm : String.valueOf(CARefl.call(spec, "getName"));
        Object now = CARefl.call(p, "getGpCostFromStages");
        if (now instanceof Map) for (Map.Entry<String, Integer> e : ((Map<String, Integer>) now).entrySet()) if (e.getValue() != null && e.getValue() > 0) monthly.put(e.getKey(), e.getValue());
        Object stages = CARefl.call(p, "getStages");
        if (stages instanceof List) for (Object st : (List<Object>) stages) {
            if (Boolean.TRUE.equals(CARefl.call(st, "isCompleted"))) continue;
            Object rem = CARefl.call(st, "getTotalGPCostFromStageAfterDelivery");
            if (rem instanceof Map) for (Map.Entry<String, Integer> e : ((Map<String, Integer>) rem).entrySet()) {
                if (e.getValue() == null || e.getValue() <= 0) continue;
                Integer cur = left.get(e.getKey());
                left.put(e.getKey(), (cur == null ? 0 : cur) + e.getValue());
            }
            if (Boolean.TRUE.equals(CARefl.call(st, "isPaidForStage"))) continue;   // its items were taken when it started
            Object costs = CARefl.call(CARefl.call(st, "getSpec"), "getOtherCosts");
            if (costs instanceof List) for (Object oc : (List<Object>) costs) {
                String id = String.valueOf(CARefl.call(oc, "getId"));
                int n = ((Number) CARefl.call(oc, "getAmount")).intValue();
                String type = String.valueOf(CARefl.call(oc, "getCostType"));
                other.add(n + " " + itemName(id, type));
            }
        }
        return true;
    }

    static String itemName(String id, String type) {
        try {
            if ("COMMODITY".equals(type)) return Global.getSettings().getCommoditySpec(id).getName();
            if ("ITEM".equals(type)) return Global.getSettings().getSpecialItemSpec(id).getName();
            if ("SHIP".equals(type)) return Global.getSettings().getHullSpec(id).getHullName() + " (ship)";
        } catch (Throwable ignored) { }
        return id;
    }

    /** The colonies that may take a producer: Approve and Auto ones where buildings go up. */
    static List<Host> hosts() {
        List<Host> hs = new ArrayList<Host>();
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if ((c.mode != CAData.Mode.AUTO && c.mode != CAData.Mode.APPROVE) || CAEngine.isOutpost(m) || CAHypershunt.is(m)
                    || m.hasTag("market_no_industries_allowed")) continue;
            Host h = new Host();
            h.id = m.getId(); h.name = m.getName(); h.size = m.getSize(); h.auto = c.mode == CAData.Mode.AUTO;
            h.freeSlots = Math.max(0, Misc.getMaxIndustries(m) - Misc.getNumIndustries(m));
            h.ships = supply(m, "ships");
            for (Industry ind : m.getIndustries()) h.has.add(ind.getId());
            Set<String> exact = new HashSet<String>();
            try { exact = CAPlan.build(m, c.profile).exact; } catch (Throwable ignored) { }
            for (Producer p : PRODUCERS) {
                if (p.base != null) for (Industry ind : m.getIndustries()) {
                    String id = ind.getId();
                    // the base, or what it's upgraded from, already became something else
                    if (!id.equals(p.base) && !id.equals(p.id) && CAPlan.leadsTo(p.base, id)) h.occupied.add(p.base);
                }
                if (exact.contains(p.base) || c.locks.contains(p.base) || (p.root != null && c.locks.contains(p.root))) h.occupied.add(p.base);
                try {
                    IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(p.id);
                    if (sp != null && sp.getNewPluginInstance(m).isAvailableToBuild() && (!CAAotD.enabled() || CAAotD.allows(p.id, m))) h.unlocked.add(p.id);
                } catch (Throwable ignored) { }
            }
            hs.add(h);
        }
        return hs;
    }

    /** Daily: reads the running project and plans producers for the goods your colonies don't make. */
    public static void update() {
        if (!on()) { STEPS.clear(); project = null; return; }
        float now = CADay.now();
        if (now - computedAt < 1f) return;
        computedAt = now;
        STEPS.clear();
        last = null;
        computing = true;
        try {
            if (!read()) return;
            yours.clear(); sector.clear();
            Set<String> goods = new LinkedHashSet<String>(left.keySet());
            goods.addAll(monthly.keySet());
            for (Producer p : PRODUCERS) { goods.add(p.good); for (String in : p.inputs) goods.add(in); }
            Map<String, Integer> made = new HashMap<String, Integer>();
            for (String g : goods) { int y = yours(g); yours.put(g, y); made.put(g, y); sector.put(g, others(g)); }
            List<String> missing = new ArrayList<String>();
            for (String g : left.keySet()) if (!madeAny(made, g)) missing.add(g);
            int ships = Math.max(0, yours.get("ships") == null ? 0 : yours.get("ships"));
            Set<String> built = new HashSet<String>();
            for (MarketAPI m : Misc.getPlayerMarkets(false)) for (Industry ind : m.getIndustries()) built.add(ind.getId());
            last = plan(missing, hosts(), built, made, ships);
            CASettings s = CAData.settings();
            for (Plan pl : last.plans) {
                if (s.projectSupplyMode == 0) {
                    List<CAPlan.Step> l = STEPS.get(pl.host);
                    if (l == null) { l = new ArrayList<CAPlan.Step>(); STEPS.put(pl.host, l); }
                    l.add(new CAPlan.Step(CAPlan.Kind.BUILD, pl.building, buildingName(pl.building) + " (" + name(pl.good) + " for " + project + ")"));
                }
                if (CALog.once("proj:" + project + ":" + pl.good + ":" + pl.host + ":" + pl.building))
                    CALog.add(project + " (special project) takes " + name(pl.good) + " every month, and none of your colonies make it"
                            + (sector.get(pl.good) > 0 ? " (other factions do, but projects only take your own production)" : " (nobody in the sector does)") + ": "
                            + (s.projectSupplyMode == 0 ? buildingName(pl.building) + " at " + pl.hostName + " is a first plan step now" : "build " + buildingName(pl.building) + " at " + pl.hostName)
                            + " (" + routeText(pl) + ")", true);
            }
            for (String g : last.unresolved) if (CALog.once("projx:" + project + ":" + g))
                CALog.add(project + " (special project) takes " + name(g) + " every month, none of your colonies make it, and no Approve or Auto colony can host what makes it ("
                        + hostText(g) + "): that's yours to build", true);
            if (!other.isEmpty() && CALog.once("projo:" + project + ":" + other))
                CALog.add(project + " (special project): its next stages also take " + CAContracts.join(other) + ", which Autopilot doesn't make; they're yours to find", false);
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Vaults of Knowledge's special projects; their supply is left to you", t);
        } finally { computing = false; }
    }

    static String routeText(Plan pl) {
        Producer p = null;
        for (Producer x : PRODUCERS) if (x.id.equals(pl.building) && x.good.equals(pl.good)) p = x;
        String how = p == null || p.base == null ? "a new building" : pl.route == 2 ? "an upgrade of its " + buildingName(p.base)
                : pl.route == 1 ? "upgrading its " + buildingName(p.root) + " to " + buildingName(p.base) + " first" : "building " + buildingName(p.base) + " first";
        return how + (pl.locked ? "; research unlocks it first, which research now works toward" : "");
    }

    static String hostText(String good) {
        List<String> l = new ArrayList<String>();
        for (Producer p : producersOf(good)) l.add(buildingName(p.id) + (p.base == null ? "" : ", from " + buildingName(p.base)) + ", size " + p.minSize + "+");
        return l.isEmpty() ? "no building Autopilot knows makes it" : CAContracts.join(l);
    }

    // ---------------- panel ----------------

    public static void render(TooltipMakerAPI t, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor();
        t.addPara("Mode: %s. A special project takes goods every month from your own colonies' production, and a stage stops while any of "
                + "them isn't delivered at all. For the one you're running, each good no colony of yours makes gets a producer as a first plan "
                + "step at the colony best placed for it (research works toward it first if it's locked), along with whatever AotD goods that "
                + "producer needs itself.", 6f, hl, CASettings.PROJECT_SUPPLY_NAMES[Math.max(0, Math.min(2, s.projectSupplyMode))]);
        if (project == null) { t.addPara("No special project is running.", gray, 4f); return; }
        t.addPara("Running: %s.", 6f, hl, project);
        for (Map.Entry<String, Integer> e : left.entrySet()) {
            String g = e.getKey();
            Integer y = yours.get(g), o = sector.get(g), mo = monthly.get(g);
            boolean none = y == null || y <= 0;
            t.addPara("%s: %s still to deliver" + (mo != null ? ", %s this month" : "%s") + "; your colonies make %s a month, other factions %s", 3f,
                    none ? bad : hl, name(g), "" + e.getValue(), mo != null ? "" + mo : "", "" + (y == null ? 0 : y), "" + (o == null ? 0 : o));
        }
        if (last != null) {
            for (Plan pl : last.plans) t.addPara("    " + CALog.esc(buildingName(pl.building) + " at " + pl.hostName + " for " + name(pl.good) + ": " + routeText(pl)), gray, 2f);
            for (String g : last.unresolved) t.addPara("    " + CALog.esc(name(g) + ": no Approve or Auto colony can host what makes it (" + hostText(g) + "); yours to build"), bad, 2f);
        }
        if (!other.isEmpty()) t.addPara("Its stages also take " + CALog.esc(CAContracts.join(other)) + " (yours to find).", gray, 4f);
    }
}
