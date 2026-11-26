package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONObject;

import java.awt.Color;
import java.util.*;

/**
 * 0.3.23.0 (plan 2026-11-25, batch 2): the needs ledger. Once a day, per good, what your colonies make and use, what is short
 * at a colony, and what Theory of Toolbox's contracts ask of your exports each month, by kind: restorations, Grand Wonder
 * sites, Black Site projects and production orders (commitments), and sales contracts and your programs (trade). With ToT the
 * amounts are its raw cargo units, the same units its contracts ask in; without it, the game's supply and demand and no
 * contracts. It decides nothing yet: the Budget panel's Needs section, the report, a monthly starsector.log line and the
 * ac_needs console command show it.
 */
public final class CANeeds {
    private CANeeds() { }

    /** Contract kinds. RESTORATION, WONDER, PROJECT and ORDER are commitments; SALE and PROGRAM are trade. */
    static final String RESTORATION = "restoration", WONDER = "wonder", PROJECT = "project", ORDER = "order", SALE = "sale", PROGRAM = "program";
    static final List<String> COMMITMENTS = Arrays.asList(RESTORATION, WONDER, PROJECT, ORDER);
    /** Goods shown on the panel and in the log line at most. */
    static final int SHOWN = 10;

    /** One good's month. */
    public static final class Good {
        public String id;
        public int supply, demand, deficits;
        /** kind -> units a month the contracts of that kind ask. */
        public final Map<String, Integer> asks = new TreeMap<String, Integer>();
        public boolean raw;

        int commitments() { int n = 0; for (String k : COMMITMENTS) n += units(asks, k); return n; }
        int trade() { return units(asks, SALE) + units(asks, PROGRAM); }
        /** Short after colonies and commitments. */
        public int gap() { return shortWith(supply, demand, commitments()); }
        /** Short after trade too. */
        public int gapWithTrade() { return shortWith(supply, demand, commitments() + trade()); }
    }

    static Map<String, Good> last = null;
    static int lastDay = Integer.MIN_VALUE, loggedMonth = Integer.MIN_VALUE;
    static Object sdSup, sdDem;
    static boolean rawLinked = false, rawBroken = false;

    /** On load (CAReset). */
    static void reset() { last = null; lastDay = Integer.MIN_VALUE; loggedMonth = Integer.MIN_VALUE; rawLinked = false; rawBroken = false; sdSup = null; sdDem = null; }

    // ---------------- pure ----------------

    static int units(Map<String, Integer> m, String k) { Integer v = m.get(k); return v == null ? 0 : v; }

    /** Pure: short after the colonies' own use and extra asks. */
    static int shortWith(int supply, int demand, int asks) { return Math.max(0, demand + asks - supply); }

    /** Pure: a contract's kind from its class name and who issued it. */
    static String kind(String className, boolean byPlayer) {
        String n = className == null ? "" : className;
        if (n.contains("Restoration")) return RESTORATION;
        if (n.contains("Wonder")) return WONDER;
        if (n.contains("BlackSite")) return PROJECT;
        if (n.contains("Production")) return ORDER;
        return byPlayer ? PROGRAM : SALE;
    }

    /** Pure: the commitment kinds that ask for a good, in words ("restorations and projects"). */
    static String askers(Map<String, Integer> asks) {
        List<String> l = new ArrayList<String>();
        if (units(asks, RESTORATION) > 0) l.add("restorations");
        if (units(asks, WONDER) > 0) l.add("wonder sites");
        if (units(asks, PROJECT) > 0) l.add("special projects");
        if (units(asks, ORDER) > 0) l.add("production orders");
        return CAContracts.join(l);
    }

    /** Pure apart from number text: the panel's line for a good. */
    static String line(String name, Good g) {
        StringBuilder b = new StringBuilder(name).append(": your colonies make ").append(n(g.supply)).append(" a month and use ").append(n(g.demand));
        int c = g.commitments(), t = g.trade();
        if (c > 0) b.append(", ").append(askers(g.asks)).append(" ask ").append(n(c)).append(" more");
        if (t > 0) b.append(", sales contracts take ").append(n(t));
        int gap = g.gapWithTrade();
        if (gap > 0) b.append(", so it's ").append(n(gap)).append(" short.");
        else b.append(", and ").append(g.deficits > 0 ? n(g.deficits) + " is short at one or more colonies." : "nothing is short.");
        return b.toString();
    }

    static String n(int v) { return Misc.getWithDGS(v); }

    // ---------------- the game ----------------

    /** Daily (CAAuto, after the floors): the ledger, and once a month a starsector.log line. */
    public static void update() {
        int today = CATime.today();
        if (today == lastDay && last != null) return;
        lastDay = today;
        last = measure();
        int month = Global.getSector().getClock().getMonth();
        if (month != loggedMonth) {
            loggedMonth = month;
            List<String> top = new ArrayList<String>();
            for (Good g : shortest(last)) top.add(g.id + " " + n(g.gapWithTrade()) + " short (colonies " + n(Math.max(0, g.demand - g.supply))
                    + (g.commitments() > 0 ? ", " + askers(g.asks) + " " + n(g.commitments()) : "") + (g.trade() > 0 ? ", trade " + n(g.trade()) : "") + ")");
            CADiagnostics.LOG.info(CADiagnostics.TAG + "needs: " + (top.isEmpty() ? "nothing short" : CAContracts.join(top)));
        }
    }

    /** The ledger now (null before the first daily pass). */
    public static Map<String, Good> get() { return last; }

    static boolean linkRaw() {
        if (rawLinked || rawBroken) return rawLinked;
        try {
            if (!CAContracts.link()) { rawBroken = true; return false; }
            Class<?> sd = Class.forName(CAContracts.PKG + "scripts.commoditydata.AoTDSupplyDemandData", true, Global.getSettings().getScriptClassLoader());
            sdSup = CARefl.method(sd, "getTotalRawUnitsFromSupply");
            sdDem = CARefl.method(sd, "getTotalRawUnitsFromDemand");
            rawLinked = true;
        } catch (Throwable t) {
            rawBroken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Theory of Toolbox's raw supply and demand; the needs ledger uses the game's numbers", t);
        }
        return rawLinked;
    }

    /** {supply, demand} in ToT's raw cargo units at a colony, or null without ToT. */
    static int[] raw(MarketAPI m, String good) {
        if (!rawLinked) return null;
        try {
            Object on = CARefl.invoke(CAContracts.comOn, null, m, good);
            if (on == null) return null;
            Object sd = CARefl.invoke(CAContracts.sdData, on);
            return new int[]{((Number) CARefl.invoke(sdSup, sd)).intValue(), ((Number) CARefl.invoke(sdDem, sd)).intValue()};
        } catch (Throwable t) { return null; }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Good> measure() {
        Map<String, Good> out = new TreeMap<String, Good>();
        boolean tot = Global.getSettings().getModManager().isModEnabled("aotd_theory_of_toolbox") && CAContracts.active() && linkRaw();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            for (CommodityOnMarketAPI c : m.getCommoditiesCopy()) {
                String id = c.getId();
                int[] r = tot ? raw(m, id) : null;
                int sup = r != null ? r[0] : c.getMaxSupply(), dem = r != null ? r[1] : c.getMaxDemand(), def = Math.max(0, c.getDeficitQuantity());
                if (sup <= 0 && dem <= 0 && def <= 0) continue;
                Good g = good(out, id);
                g.supply += Math.max(0, sup); g.demand += Math.max(0, dem); g.deficits += def; g.raw |= r != null;
            }
        }
        if (tot) {
            try {
                for (Object c : ((Map<String, Object>) CARefl.invoke(CAContracts.activeMap, CAContracts.mgr())).values()) {
                    if (!CAContracts.live(c)) continue;
                    String k = kind(c.getClass().getSimpleName(), Boolean.TRUE.equals(CARefl.invoke(CAContracts.cPlayer, c)));
                    for (Object d : ((Map<String, Object>) CARefl.invoke(CAContracts.cData, c)).values()) {
                        String id = String.valueOf(CARefl.invoke(CAContracts.dCom, d));
                        int n = ((Number) CARefl.invoke(CAContracts.dReq, d)).intValue();
                        if (n <= 0) continue;
                        Good g = good(out, id);
                        g.asks.put(k, units(g.asks, k) + n);
                    }
                }
            } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "needs ledger: reading the contracts failed", t); }
        }
        return out;
    }

    static Good good(Map<String, Good> out, String id) {
        Good g = out.get(id);
        if (g == null) { g = new Good(); g.id = id; out.put(id, g); }
        return g;
    }

    /** The goods short after trade or at a colony, the biggest gap by value first, at most SHOWN. */
    static List<Good> shortest(Map<String, Good> l) {
        List<Good> out = new ArrayList<Good>();
        if (l == null) return out;
        for (Good g : l.values()) if (g.gapWithTrade() > 0 || g.deficits > 0) out.add(g);
        Collections.sort(out, new Comparator<Good>() {
            public int compare(Good a, Good b) { return Float.compare(worth(b), worth(a)); }
        });
        return out.size() > SHOWN ? out.subList(0, SHOWN) : out;
    }

    static float worth(Good g) { return Math.max(g.gapWithTrade(), g.deficits) * CAValue.price(g.id); }

    static String name(String id) {
        try { return Global.getSettings().getCommoditySpec(id).getName(); } catch (Throwable t) { return id; }
    }

    /** The panel and the console: one line a good that's short. */
    public static List<String> lines() {
        List<String> out = new ArrayList<String>();
        for (Good g : shortest(last)) out.add(line(name(g.id), g));
        return out;
    }

    /** The Budget panel's Needs section, only while something is short. */
    public static void render(TooltipMakerAPI t) {
        Color bad = Misc.getNegativeHighlightColor();
        List<String> l = lines();
        if (l.isEmpty()) return;
        CATheme.heading(t, "Needs", 12f);
        for (String s : l) t.addPara(s, bad, 3f);   // shown as is
    }

    /** For the report: every good with its numbers. */
    static JSONObject json() throws Exception {
        JSONObject o = new JSONObject();
        if (last == null) return o;
        for (Good g : last.values()) {
            JSONObject x = new JSONObject();
            x.put("supply", g.supply); x.put("demand", g.demand);
            if (g.deficits > 0) x.put("shortAtColonies", g.deficits);
            for (Map.Entry<String, Integer> e : g.asks.entrySet()) x.put(e.getKey(), e.getValue());
            x.put("short", g.gapWithTrade());
            x.put("units", g.raw ? "ToT raw" : "game");
            o.put(g.id, x);
        }
        return o;
    }
}
