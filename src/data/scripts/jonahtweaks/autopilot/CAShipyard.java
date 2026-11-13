package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionProductionAPI;
import com.fs.starfarer.api.campaign.FactionProductionAPI.ProductionItemType;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * WS-C3 rung 4: the shipyard. Research 03 F2 and sections 4a-4c; DECISIONS 7, 8 and 9.
 *
 * 0.3.0.0 (C3): with the money tier at Idle money or higher, Auto Colonies orders the ships on your Doctrine screen's priority
 * list through the game's own custom production, at the game's prices. It adds them to the production list
 * (FactionProductionAPI.addItem, after knowsShip); the game builds them at month end within its monthly capacity, charges them
 * in the monthly report (outside colony net, CABudget.NOT_COLONY) and delivers them to the gathering point's Storage, which
 * CACapital keeps at the capital. Auto Colonies takes a convenience fee when the order is placed (shipyardFee, 2% of the
 * price; none at the Off fee level).
 * Limits on each order: the Shipyard rung's allowance (CARich); the production capacity left after everything already on the
 * list (yours, Auto Outfitting's and Auto Colonies'); free cash; and shipyardStop unclaimed hulls (6): hulls Colony
 * Manager ordered that are still on the list or waiting in the gathering point's Storage (a hull you take out is claimed).
 * 0.3.0.0 (C3 fix): an order line stays saved until its hulls are claimed, however old it is (tidy: per hull, the oldest
 * lines come off first, down to what's on the list and in Storage), so the stop holds for hulls left in Storage for years.
 * Past MAX_ORDERS lines, each hull's lines are joined into one.
 * The game charges production at month end without looking at your reserve, so every day guard() takes Auto Colonies'
 * own hulls off the list again, fee refunded, while free cash is under 0. The cost multiplier and accrued production are
 * not touched.
 * Which hulls: the priority ships you know the blueprint for (stations and modules left out), the one with the fewest on
 * the list and in Storage first, cheaper first on a tie, one at a time, so an order is a mix ("2 Eagle and 1 Dominator").
 * With no priority ships set it orders nothing and says so on the Budget panel.
 * One order a month, looked at weekly and on the first day of a month. On Recommend only (the 0.3.0 default) the order is
 * proposed on the Budget panel with Approve and Reject; Reject holds proposals off for REJECT_DAYS. 0.3.0.0 (C3 fix 2): Approve
 * places it only while the rung is open and the order fits the rung's room (CARich.room); CARich.run drops the proposal
 * while the preset leaves the rung out or legacyPlanner is on.
 * Ashes of the Domain replaces the custom production screen, but the game's own list is still built every month (research
 * 03 section 4b; Auto Outfitting relies on the same), so the same orders work with it. AotD's own order path is not used:
 * it takes goods from your colonies' exports for every unit, which free cash can't see.
 * Saved: CAData.shipyardOrders (hull id, quantity, price per hull, and in `month` the game timestamp of the order). The
 * proposal, the weekly timer and the panel note are session only (reset through CARich.reset).
 */
public class CAShipyard {
    /** One order line Auto Colonies placed. month: the game timestamp it was placed at. cost: the price of one hull then. */
    public static class Order { public String hullId; public int qty; public float cost; public long month; }

    /** Days between looks, days a rejected proposal holds the next one off. */
    static final float LOOK_DAYS = 7f, REJECT_DAYS = 30f;
    /** Order lines kept before each hull's lines are joined into one (0.3.0.0, C3 fix: none is dropped while its hulls are unclaimed). */
    static final int MAX_ORDERS = 60;
    /** CAMark key: when you last rejected a proposal. */
    static final String REJECTED = "shipyardno";
    /** Budget panel buttons (CARich.handle routes them here). */
    static final String KEY = "jcm:shipyard:", APPROVE = KEY + "approve", REJECT = KEY + "reject";

    /** One hull it may order: id, name, price per hull, and how many are on the list and in Storage now. */
    static final class Hull {
        final String id, name; final float cost; final int have;
        Hull(String id, String name, float cost, int have) { this.id = id; this.name = name; this.cost = cost; this.have = have; }
    }

    /** An order worked out: hull id -> quantity (in the order picked), price and fee in total, and where it's delivered. */
    static final class Plan {
        final Map<String, Integer> qty = new LinkedHashMap<String, Integer>();
        final Map<String, Float> unit = new HashMap<String, Float>();
        final Map<String, String> names = new HashMap<String, String>();
        float value, fee;
        String to;
        int count() { int n = 0; for (int q : qty.values()) n += q; return n; }
        float total() { return value + fee; }
        boolean empty() { return qty.isEmpty(); }
        String sig() { return qty.toString(); }
    }

    // ---------------- session state ----------------

    /** The proposal waiting for Approve or Reject (Recommend only), or null. */
    static Plan pending;
    /** Its signature as last logged, so a proposal is logged once. */
    static String loggedSig;
    /** The month key of the last look (a new month gets a look at once). */
    static int lookedMonth = Integer.MIN_VALUE;

    static void reset() { pending = null; loggedSig = null; lookedMonth = Integer.MIN_VALUE; CATime.forget("shipyard"); }

    // ---------------- pure rules (JTShipyardCheck) ----------------

    /** Pure: how many of a hull to order given capacity, queue, allowance, unit cost, unclaimed (the A0 stub's form: no fee, no free cash limit). */
    static int qtyFor(float capacity, float queued, float allowance, float unitCost, int unclaimed, int stop) {
        return qtyFor(capacity, queued, allowance, unitCost, 0f, unclaimed, stop, Float.MAX_VALUE);
    }

    /**
     * Pure: how many of one hull: within the capacity the list leaves (capacity - queued, in price), the money (the
     * allowance, capped at free cash; each hull costs its price plus the fee share), and the hulls left under the stop.
     */
    static int qtyFor(float capacity, float queued, float allowance, float unitCost, float feeShare, int unclaimed, int stop, float free) {
        if (!(unitCost > 0f)) return 0;
        float room = Math.max(0f, capacity - Math.max(0f, queued));
        float money = Math.max(0f, Math.min(allowance, free));
        int byCap = (int) Math.floor(room / unitCost + 1e-4f);
        int byMoney = (int) Math.floor(money / (unitCost * (1f + Math.max(0f, feeShare))) + 1e-4f);
        int bySlots = Math.max(0, stop - Math.max(0, unclaimed));
        return Math.max(0, Math.min(Math.min(byCap, byMoney), bySlots));
    }

    /**
     * Pure: an order across hulls, one at a time: the hull with the fewest on the list and in Storage first (cheaper on a tie,
     * then by id), while its price fits the capacity room and its price and fee fit the money, up to `slots` hulls.
     */
    static Plan plan(List<Hull> hulls, float capRoom, float money, float feeShare, int slots) {
        Plan p = new Plan();
        if (hulls == null || hulls.isEmpty()) return p;
        float fs = Math.max(0f, feeShare);
        Map<String, Integer> have = new HashMap<String, Integer>();
        for (Hull h : hulls) have.put(h.id, Math.max(0, h.have));
        Set<String> out = new HashSet<String>();
        float room = Math.max(0f, capRoom), cash = Math.max(0f, money);
        int left = Math.max(0, slots);
        while (left > 0) {
            Hull best = null;
            for (Hull h : hulls) {
                if (out.contains(h.id) || !(h.cost > 0f)) continue;
                if (h.cost > room + 1e-3f || h.cost * (1f + fs) > cash + 1e-3f) { out.add(h.id); continue; }
                if (best == null) { best = h; continue; }
                int c = Integer.compare(have.get(h.id), have.get(best.id));
                if (c == 0) c = Float.compare(h.cost, best.cost);
                if (c == 0) c = h.id.compareTo(best.id);
                if (c < 0) best = h;
            }
            if (best == null) break;
            Integer q = p.qty.get(best.id);
            p.qty.put(best.id, q == null ? 1 : q + 1);
            p.unit.put(best.id, best.cost);
            p.names.put(best.id, best.name);
            have.put(best.id, have.get(best.id) + 1);
            p.value += best.cost;
            p.fee += best.cost * fs;
            room -= best.cost;
            cash -= best.cost * (1f + fs);
            left--;
        }
        return p;
    }

    /**
     * Pure: unclaimed hulls: per hull Auto Colonies ordered (its saved order lines), those still on the list (up to what it
     * ordered) plus those waiting in the gathering point's Storage (up to what it ordered and isn't still on the list).
     */
    static int unclaimed(Map<String, Integer> ordered, Map<String, Integer> queued, Map<String, Integer> stored) {
        int n = 0;
        for (Map.Entry<String, Integer> e : ordered.entrySet()) {
            int o = Math.max(0, e.getValue() == null ? 0 : e.getValue());
            int q = Math.min(o, get(queued, e.getKey()));
            int w = Math.min(o - q, get(stored, e.getKey()));
            n += q + w;
        }
        return n;
    }

    /** Pure: the convenience fee share: shipyardFee, or none at the Off fee level (DECISIONS 9, 11). */
    static float feeShare(int feeLevel, float shipyardFee) { return feeLevel <= 0 ? 0f : Math.max(0f, shipyardFee); }

    /**
     * Pure: how many of Auto Colonies' queued hulls come off the list to bring free cash back to 0 or more, most
     * expensive first. queuedPrices: the price of each hull on the list (one entry per hull).
     */
    static int toRemove(float free, List<Float> queuedPrices) {
        if (!(free < 0f) || queuedPrices == null || queuedPrices.isEmpty()) return 0;
        List<Float> l = new ArrayList<Float>(queuedPrices);
        Collections.sort(l, Collections.reverseOrder());
        float f = free;
        int n = 0;
        for (float p : l) {
            if (f >= 0f) break;
            f += p;
            n++;
        }
        return n;
    }

    /** Pure: "2 Eagle and 1 Dominator". */
    static String text(Map<String, Integer> qty, Map<String, String> names) {
        List<String> parts = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : qty.entrySet()) {
            String n = names == null ? null : names.get(e.getKey());
            parts.add(e.getValue() + " " + (n != null ? n : e.getKey()));
        }
        if (parts.isEmpty()) return "nothing";
        if (parts.size() == 1) return parts.get(0);
        return CAContracts.join(parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    static int get(Map<String, Integer> m, String k) {
        Integer v = m == null ? null : m.get(k);
        return v == null ? 0 : Math.max(0, v);
    }

    // ---------------- the game ----------------

    static FactionProductionAPI production() {
        try { return Global.getSector().getPlayerFaction().getProduction(); } catch (Throwable t) { return null; }
    }

    /** Pure, 0.3.0.0 (C3 fix): hull id -> quantity on these order lines (empty lines and lines without a hull left out). */
    static Map<String, Integer> ordered(List<Order> lines) {
        Map<String, Integer> out = new HashMap<String, Integer>();
        if (lines == null) return out;
        for (Order o : lines) {
            if (o == null || o.hullId == null || o.qty <= 0) continue;
            Integer v = out.get(o.hullId);
            out.put(o.hullId, (v == null ? 0 : v) + o.qty);
        }
        return out;
    }

    /** Hull id -> quantity Auto Colonies ordered and hasn't seen claimed (its saved order lines; reading changes nothing). */
    static Map<String, Integer> ordered(CAData d) { return ordered(d.shipyardOrders()); }

    /**
     * Pure, 0.3.0.0 (C3 fix): order lines whose hulls are claimed come off. Per hull, the lines keep at most `held` hulls (on
     * the production list and in the gathering point's Storage); the oldest lines give up their hulls first, since the game
     * builds and delivers in order. Empty lines go. Age alone never drops a line.
     */
    static void trim(List<Order> lines, Map<String, Integer> held) {
        if (lines == null) return;
        Map<String, Integer> excess = new HashMap<String, Integer>();
        for (Map.Entry<String, Integer> e : ordered(lines).entrySet()) excess.put(e.getKey(), e.getValue() - get(held, e.getKey()));
        for (Iterator<Order> it = lines.iterator(); it.hasNext(); ) {
            Order o = it.next();
            if (o == null || o.hullId == null || o.qty <= 0) { it.remove(); continue; }
            int x = excess.containsKey(o.hullId) ? excess.get(o.hullId) : 0;
            if (x <= 0) continue;
            int cut = Math.min(x, o.qty);
            o.qty -= cut;
            excess.put(o.hullId, x - cut);
            if (o.qty <= 0) it.remove();
        }
    }

    /**
     * Pure, 0.3.0.0 (C3 fix): past `max` lines, each hull's lines are joined into its newest line (all the quantity, the
     * newest timestamp), so the save stays small and no unclaimed hull is lost.
     */
    static void compact(List<Order> lines, int max) {
        if (lines == null || lines.size() <= max) return;
        Map<String, Order> newest = new LinkedHashMap<String, Order>();
        Map<String, Integer> qty = new HashMap<String, Integer>();
        for (Order o : lines) {
            if (o == null || o.hullId == null || o.qty <= 0) continue;
            Order n = newest.get(o.hullId);
            if (n == null || o.month >= n.month) newest.put(o.hullId, o);
            Integer v = qty.get(o.hullId);
            qty.put(o.hullId, (v == null ? 0 : v) + o.qty);
        }
        lines.clear();
        for (Order o : newest.values()) { o.qty = qty.get(o.hullId); lines.add(o); }
    }

    /**
     * 0.3.0.0 (C3 fix): drops the order lines whose hulls are claimed (none on the list or in the gathering point's Storage
     * counted against them), and joins lines past MAX_ORDERS. Nothing changes during a what-if valuation.
     */
    static void tidy(CAData d, FactionProductionAPI prod, MarketAPI gp) {
        if (CALog.whatIf() || d.shipyardOrders == null || d.shipyardOrders.isEmpty() || prod == null || gp == null) return;
        List<Order> l = d.shipyardOrders();
        Map<String, Integer> ord = ordered(l), q = queued(prod, ord.keySet()), st = stored(gp), held = new HashMap<String, Integer>();
        for (String h : ord.keySet()) held.put(h, get(q, h) + get(st, h));
        trim(l, held);
        compact(l, MAX_ORDERS);
    }

    /** Hull id -> ships of that hull in a colony's Storage (the gathering point's). */
    static Map<String, Integer> stored(MarketAPI gp) {
        Map<String, Integer> out = new HashMap<String, Integer>();
        try {
            CargoAPI st = gp == null ? null : Misc.getStorageCargo(gp);
            if (st == null || st.getMothballedShips() == null) return out;
            for (FleetMemberAPI f : st.getMothballedShips().getMembersListCopy()) {
                String h = f.getHullId();
                if (h == null) continue;
                Integer v = out.get(h);
                out.put(h, (v == null ? 0 : v) + 1);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** Hull id -> how many of it are on the production list now. */
    static Map<String, Integer> queued(FactionProductionAPI prod, Collection<String> hulls) {
        Map<String, Integer> out = new HashMap<String, Integer>();
        for (String h : hulls) {
            try { out.put(h, Math.max(0, prod.getCount(ProductionItemType.SHIP, h))); } catch (Throwable ignored) { }
        }
        return out;
    }

    /** The priority ships you can order: known, priced, not a station or a module. */
    static List<Hull> hulls(FactionAPI pf, FactionProductionAPI prod, Map<String, Integer> queued, Map<String, Integer> stored) {
        List<Hull> out = new ArrayList<Hull>();
        Set<String> ids;
        try { ids = pf.getPriorityShips(); } catch (Throwable t) { return out; }
        if (ids == null) return out;
        for (String id : new TreeSet<String>(ids)) {
            try {
                if (id == null || !pf.knowsShip(id)) continue;
                ShipHullSpecAPI spec = Global.getSettings().getHullSpec(id);
                if (spec == null || spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)
                        || spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.MODULE)) continue;
                int cost = prod.getUnitCost(ProductionItemType.SHIP, id);
                if (cost <= 0) continue;
                out.add(new Hull(id, spec.getHullName(), cost, get(queued, id) + get(stored, id)));
            } catch (Throwable ignored) { }
        }
        return out;
    }

    // ---------------- daily ----------------

    /**
     * Daily from CARich.run: a weekly look (and one on the first day of a month) while the Shipyard rung is open. Works out
     * the order, tells the waterfall what it wants, and places it (On) or proposes it (Recommend only).
     */
    public static void run(CAData d, CASettings s, float allowance) {
        if (!CARich.open(CARich.SHIPYARD)) {
            pending = null;
            CARich.note(CARich.SHIPYARD, null);
            return;
        }
        int month = CARich.monthKey();
        if (month == lookedMonth && !CATime.due("shipyard", LOOK_DAYS)) return;
        if (month != lookedMonth) CATime.stamp("shipyard");
        lookedMonth = month;
        look(d, s, allowance);
    }

    static void look(CAData d, CASettings s, float allowance) {
        FactionProductionAPI prod = production();
        FactionAPI pf = Global.getSector().getPlayerFaction();
        if (prod == null || pf == null) { CARich.want(CARich.SHIPYARD, 0f); return; }
        long now = CAMark.now();
        MarketAPI gp = prod.getGatheringPoint();
        if (gp == null) {
            say("no production gathering point is set, so the game builds nothing; set one on the custom production screen");
            return;
        }
        tidy(d, prod, gp);   // 0.3.0.0 (C3 fix): claimed lines go; unclaimed ones stay however old
        Map<String, Integer> ordered = ordered(d), stored = stored(gp);
        if (orderedThisMonth(d)) {
            say("this month's order is placed; built at month end and delivered to " + gp.getName());
            return;
        }
        Set<String> ids = new HashSet<String>(ordered.keySet());
        try { if (pf.getPriorityShips() != null) ids.addAll(pf.getPriorityShips()); } catch (Throwable ignored) { }
        Map<String, Integer> queued = queued(prod, ids);
        List<Hull> hulls = hulls(pf, prod, queued, stored);
        if (hulls.isEmpty()) {
            say("no priority ships you can build; set them on the Doctrine screen");
            return;
        }
        int stop = Math.max(0, s.shipyardStop), un = unclaimed(ordered, queued, stored);
        if (un >= stop) {
            say(un + " hulls it ordered are unclaimed (on the production list or in " + gp.getName() + "'s Storage); it stops at " + stop);
            return;
        }
        float capRoom = Math.max(0f, prod.getMonthlyProductionCapacity() - Math.max(0, prod.getTotalCurrentCost()));
        float free = CABudget.freeCash(s), fs = feeShare(s.feeLevel, s.shipyardFee);
        Plan wanted = plan(hulls, capRoom, free, fs, stop - un);
        if (wanted.empty()) {
            say(!(capRoom > 0f) ? "the production list already fills this month's capacity" : "free cash doesn't cover one hull now");
            return;
        }
        Plan p = plan(hulls, capRoom, Math.min(allowance, free), fs, stop - un);
        p.to = gp.getName();
        if (CARich.mode(CARich.SHIPYARD) == CARich.RECOMMEND) {
            if (rejectedRecently(now)) { say("you rejected the last proposal; the next one comes after " + Math.round(REJECT_DAYS) + " days"); return; }
            if (p.empty()) {
                pending = null;
                CARich.want(CARich.SHIPYARD, wanted.total());
                CARich.note(CARich.SHIPYARD, "waits for its share of the pool (" + Misc.getDGSCredits(allowance) + " now)");
                return;
            }
            pending = p;
            CARich.want(CARich.SHIPYARD, p.total());
            CARich.note(CARich.SHIPYARD, "proposes " + text(p.qty, p.names) + " for " + Misc.getDGSCredits(p.value) + ", delivered to " + p.to
                    + " at month end; Approve or Reject on this panel");
            if (!p.sig().equals(loggedSig)) {
                loggedSig = p.sig();
                CALog.advise("Shipyard: recommends " + text(p.qty, p.names) + " for " + Misc.getDGSCredits(p.value) + " plus a " + Misc.getDGSCredits(p.fee)
                        + " fee, built at month end and delivered to " + p.to + ". Approve on the Budget panel.", true);
            }
            return;
        }
        CARich.want(CARich.SHIPYARD, wanted.total());
        if (p.empty()) { CARich.note(CARich.SHIPYARD, "waits for its share of the pool (" + Misc.getDGSCredits(allowance) + " now)"); return; }
        String err = order(d, s, prod, p);
        if (err != null) CARich.note(CARich.SHIPYARD, err);
    }

    /** Nothing more this month: no want, the panel note, no proposal. */
    static void say(String note) {
        pending = null;
        CARich.want(CARich.SHIPYARD, 0f);
        CARich.note(CARich.SHIPYARD, note);
    }

    /**
     * 0.3.0.0 (C3 fix 2): an Approve that no longer fits: no proposal, no want held on the rung, and the next daily run looks
     * again (a smaller order that fits the room now, or nothing while the rung is closed).
     */
    static void drop() {
        pending = null;
        CARich.want(CARich.SHIPYARD, 0f);
        CARich.note(CARich.SHIPYARD, null);
        CATime.forget("shipyard");
    }

    static boolean rejectedRecently(long now) {
        Long at = CAMark.at(REJECTED);
        return at != null && now >= at && CAMark.days(at, now) < REJECT_DAYS;
    }

    /** Whether Auto Colonies already placed an order this game month. */
    static boolean orderedThisMonth(CAData d) { return !thisMonth(d).isEmpty(); }

    /**
     * Hull id -> quantity Auto Colonies ordered this game month: hulls the game hasn't built yet (it builds at month end), so
     * guard() takes off only these, and your own hulls of the same type stay on the list.
     */
    static Map<String, Integer> thisMonth(CAData d) {
        Map<String, Integer> out = new HashMap<String, Integer>();
        try {
            com.fs.starfarer.api.campaign.CampaignClockAPI c = Global.getSector().getClock();
            for (Order o : d.shipyardOrders()) {
                if (o == null || o.month == 0L || o.hullId == null || o.qty <= 0) continue;
                com.fs.starfarer.api.campaign.CampaignClockAPI oc = c.createClock(o.month);
                if (oc.getCycle() != c.getCycle() || oc.getMonth() != c.getMonth()) continue;
                Integer v = out.get(o.hullId);
                out.put(o.hullId, (v == null ? 0 : v) + o.qty);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /**
     * Places an order: adds each hull to the production list, takes the fee from the pool, records the price against the
     * Shipyard rung. Null when placed, else why not (nothing is charged for a line the game refused).
     */
    static String order(CAData d, CASettings s, FactionProductionAPI prod, Plan p) {
        if (p == null || p.empty()) return "nothing to order";
        if (CALog.whatIf()) return "not during a what-if valuation";
        String held = CAPlanner.setAside(p.total(), 0f, 0f, null);
        if (held != null) return "waits: " + held;
        long now = CAMark.now();
        Map<String, Integer> placed = new LinkedHashMap<String, Integer>();
        float value = 0f, fee = 0f, fs = p.value > 0f ? p.fee / p.value : 0f;
        for (Map.Entry<String, Integer> e : p.qty.entrySet()) {
            boolean ok;
            try { ok = prod.addItem(ProductionItemType.SHIP, e.getKey(), e.getValue()); }
            catch (Throwable t) { ok = false; CADiagnostics.LOG.warn(CADiagnostics.TAG + "shipyard: adding " + e.getKey() + " to the production list failed", t); }
            if (!ok) continue;
            float unit = p.unit.containsKey(e.getKey()) ? p.unit.get(e.getKey()) : 0f;
            Order o = new Order();
            o.hullId = e.getKey(); o.qty = e.getValue(); o.cost = unit; o.month = now;
            d.shipyardOrders().add(o);
            placed.put(e.getKey(), e.getValue());
            value += unit * e.getValue();
            fee += unit * e.getValue() * fs;
        }
        if (placed.isEmpty()) return "the game refused the order";
        MarketAPI gp = prod.getGatheringPoint();
        String to = gp == null ? p.to : gp.getName();
        // the fee now, from the pool; the price is the game's to charge at month end, recorded against the rung now
        CABudget.payFromPool(s, CARich.SHIPYARD, gp == null ? null : gp.getId(), 0f, fee);
        CARich.spent(CARich.SHIPYARD, value);
        CARich.want(CARich.SHIPYARD, 0f);
        pending = null;
        String what = text(placed, p.names);
        CARich.note(CARich.SHIPYARD, what + " ordered for " + Misc.getDGSCredits(value) + ", delivered to " + to + " at month end");
        CALog.did("Shipyard: ordered " + what + " for " + Misc.getDGSCredits(value) + " plus a " + Misc.getDGSCredits(fee)
                + " fee, built at month end and delivered to " + to + "'s Storage.");
        return null;
    }

    /**
     * Daily: the game charges production at month end without looking at your reserve. While free cash is under 0, the hulls
     * Auto Colonies ordered this month that are still on the list come off it, most expensive first, until free cash is 0
     * or more; the fee for each comes back.
     */
    public static void guard(CAData d, CASettings s) {
        if (d.shipyardOrders == null || d.shipyardOrders.isEmpty() || CALog.whatIf()) return;
        FactionProductionAPI prod = production();
        if (prod == null) return;
        float free = CABudget.freeCash(s);
        if (!(free < 0f)) return;
        Map<String, Integer> ordered = thisMonth(d);
        if (ordered.isEmpty()) return;
        Map<String, Integer> queued = queued(prod, ordered.keySet());
        List<Float> prices = new ArrayList<Float>();
        List<String> hullOf = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : ordered.entrySet()) {
            int q = Math.min(e.getValue(), get(queued, e.getKey()));
            float unit = 0f;
            try { unit = prod.getUnitCost(ProductionItemType.SHIP, e.getKey()); } catch (Throwable ignored) { }
            if (!(unit > 0f)) continue;
            for (int i = 0; i < q; i++) { prices.add(unit); hullOf.add(e.getKey()); }
        }
        int n = toRemove(free, prices);
        if (n <= 0) return;
        // most expensive first, as toRemove counted them
        Integer[] idx = new Integer[prices.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        final List<Float> pr = prices;
        Arrays.sort(idx, new Comparator<Integer>() { public int compare(Integer a, Integer b) { return Float.compare(pr.get(b), pr.get(a)); } });
        Map<String, Integer> removed = new LinkedHashMap<String, Integer>();
        Map<String, String> names = new HashMap<String, String>();
        float back = 0f, fs = feeShare(s.feeLevel, s.shipyardFee);
        for (int i = 0; i < n; i++) {
            String h = hullOf.get(idx[i]);
            try { prod.removeItem(ProductionItemType.SHIP, h, 1); } catch (Throwable t) { continue; }
            Integer v = removed.get(h);
            removed.put(h, (v == null ? 0 : v) + 1);
            try { names.put(h, Global.getSettings().getHullSpec(h).getHullName()); } catch (Throwable ignored) { }
            back += prices.get(idx[i]) * fs;
            takeOff(d, h);
        }
        if (removed.isEmpty()) return;
        MarketAPI gp = prod.getGatheringPoint();
        if (back > 0f) CABudget.refund(s, gp == null ? null : gp.getId(), back);
        CALog.did("Shipyard: took " + text(removed, names) + " off the production list" + (back > 0f ? ", " + Misc.getDGSCredits(back) + " fee back" : "")
                + ". Free cash was under 0. Reserve and runway kept.", true);
    }

    /** One hull of an order comes off: the newest order line of that hull loses one. */
    static void takeOff(CAData d, String hull) {
        List<Order> l = d.shipyardOrders();
        for (int i = l.size() - 1; i >= 0; i--) {
            Order o = l.get(i);
            if (o == null || !hull.equals(o.hullId) || o.qty <= 0) continue;
            o.qty--;
            if (o.qty <= 0) l.remove(i);
            return;
        }
    }

    // ---------------- panel (wired by WS-F) ----------------

    /** The Budget panel's shipyard part: the proposal with Approve and Reject, what's on the list and waiting in Storage. */
    public static void render(TooltipMakerAPI t) {
        if (t == null) return;
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        CAData d;
        try { d = CAData.get(); } catch (Throwable e) { return; }
        FactionProductionAPI prod = production();
        if (prod == null) return;
        MarketAPI gp = prod.getGatheringPoint();
        Map<String, Integer> ordered = ordered(d);
        if (!ordered.isEmpty()) {
            Map<String, Integer> queued = queued(prod, ordered.keySet()), stored = stored(gp);
            int q = 0;
            for (Map.Entry<String, Integer> e : ordered.entrySet()) q += Math.min(e.getValue(), get(queued, e.getKey()));
            int un = unclaimed(ordered, queued, stored);
            t.addPara("Shipyard: %s hulls ordered, %s waiting in " + (gp == null ? "Storage" : CALog.esc(gp.getName()) + "'s Storage")
                    + " (stops at %s).", 4f, hl, String.valueOf(q), String.valueOf(un - q), String.valueOf(Math.max(0, d.settings.shipyardStop)));
        }
        Plan p = pending;
        if (p != null && !p.empty()) {
            t.addPara("Shipyard proposal: %s for %s plus a %s fee, delivered to " + CALog.esc(p.to == null ? "the gathering point" : p.to) + ".", 4f, hl,
                    text(p.qty, p.names), Misc.getDGSCredits(p.value), Misc.getDGSCredits(p.fee));
            t.addButton("Approve", APPROVE, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 4f);
            t.addButton("Reject", REJECT, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 4f);
        } else if (ordered.isEmpty()) {
            t.addPara("Shipyard: orders your Doctrine priority ships.", gray, 4f);
        }
    }

    /**
     * Approve or Reject the proposal. Returns a message for the log, "" when it was handled and logged already, or null when
     * the key isn't one of these.
     */
    public static String handle(String key) {
        if (APPROVE.equals(key)) {
            Plan p = pending;
            if (p == null || p.empty()) return "Shipyard: no proposal to approve";
            CAData d = CAData.get();
            CASettings s = d.settings;
            FactionProductionAPI prod = production();
            if (prod == null) return "Shipyard: the production list can't be reached";
            // 0.3.0.0 (C3 fix 2): the rung must still be open (preset, money tier, its setting, legacyPlanner) and the order must
            // fit its room, as CAMilitary and CAStockpile check on Approve. Earlier rungs may want more since the weekly look
            if (!CARich.open(CARich.SHIPYARD)) {
                drop();
                return "Shipyard: rung closed (preset, money tier or setting). Proposal dropped";
            }
            if (p.total() > CARich.room(CARich.SHIPYARD) + 1f) {
                drop();
                return "Shipyard: proposal over its share of the pool. Dropped. A new one comes at the next look";
            }
            // checked again now: the money and capacity may have moved since it was proposed
            float free = CABudget.freeCash(s), capRoom = Math.max(0f, prod.getMonthlyProductionCapacity() - Math.max(0, prod.getTotalCurrentCost()));
            if (p.total() > free + 1f || p.value > capRoom + 1f) {
                pending = null;
                return "Shipyard: proposal over free cash or this month's capacity. Dropped. A new one comes at the next look";
            }
            String err = order(d, s, prod, p);
            return err != null ? "Shipyard: " + err : "";
        }
        if (REJECT.equals(key)) {
            pending = null;
            CAMark.set(REJECTED, CAMark.now());
            CARich.want(CARich.SHIPYARD, 0f);
            CARich.note(CARich.SHIPYARD, "you rejected the proposal; the next one comes after " + Math.round(REJECT_DAYS) + " days");
            return "Shipyard: proposal rejected. Next one after " + Math.round(REJECT_DAYS) + " days";
        }
        return null;
    }
}
