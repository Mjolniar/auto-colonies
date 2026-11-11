package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Autopilot Storage, one per colony (0.2.37.0), and paid shipping between them. Before 0.2.37.0 it was one shared cargo shown
 * at every colony, so whatever went in at one colony could come out at any other, and the manager installed items and
 * cores anywhere: free transport across the sector. Now each colony's tab holds what was put in there; the manager uses
 * an item at the colony it's in, and anything it wants somewhere else is shipped: a fee (SHIP_BASE plus SHIP_PER_LY a
 * light-year, times the convenience fee level) and travel time (SHIP_DAYS plus a day a light-year), delivered into the
 * destination colony's Autopilot Storage. The same goes for research items and databanks taken from your colonies'
 * normal Storage or your fleet's cargo for a Research Facility somewhere else. Anything the manager takes out of a
 * building (a core it moves, an item it pulls back) goes into that colony's own Autopilot Storage, which is free.
 * A save from before 0.2.37.0 has its shared storage moved into the biggest colony's, once, with a log line.
 * 0.3.0.0 (WS-C4, research 03 F11): one fee per trip. A shipment on a route (the same place to the same colony) that had a
 * load sent within the last 7 days and still on the way joins that load: no new fee, and the whole load arrives once the
 * new part has travelled its full time (the earlier part waits up to 7 days more). Callers can ask what a shipment would
 * cost first (quote) and take sources nearest first (nearest, byDistance).
 * 0.3.0.0 fix (C4 review 2): a route is keyed on the source's market id (your fleet's id for its cargo) and the destination
 * colony (routeKey, Shipment.fromId), so one colony's Autopilot Storage, Storage and buildings are one trip and one fee.
 * The display name (Shipment.from) is for the log only; a shipment saved before this fix has no fromId and matches on it.
 */
public class CAStores {
    public static final float SHIP_BASE = CATuning.SHIP_BASE, SHIP_PER_LY = CATuning.SHIP_PER_LY, SHIP_DAYS = CATuning.SHIP_DAYS;   // 0.3.0.0 (A3): in CATuning

    /** Something on its way to a colony's Autopilot Storage. Saved with CAData. */
    public static class Shipment {
        public String commodity;          // a commodity id, or null for a special item
        public SpecialItemData special;   // the exact item (some mods keep state in it), or null
        public int qty;
        public String from, to;           // where it left from (a name, for the log) and the market id it goes to
        /** 0.3.0.0 fix (C4 review 2): the route's source key (routeKey: a market id, or your fleet's id); null in shipments saved before it. */
        public String fromId;
        public long sent;
        public float days;
        public String why;

        String name() {
            try {
                if (commodity != null) return Global.getSettings().getCommoditySpec(commodity).getName();
                return Global.getSettings().getSpecialItemSpec(special.getId()).getName();
            } catch (Throwable t) { return commodity != null ? commodity : special == null ? "?" : special.getId(); }
        }

        String id() { return commodity != null ? commodity : special == null ? null : special.getId(); }
    }

    // ---------------- pure rules (JTStoresCheck) ----------------

    /** Days on the way: a couple of days to load and unload, plus a day a light-year. */
    static float days(float ly) { return SHIP_DAYS + (float) Math.ceil(Math.max(0f, ly)); }

    /** Credits a shipment costs before the convenience fee level. */
    static float baseFee(float ly) { return SHIP_BASE + SHIP_PER_LY * Math.max(0f, ly); }

    /** 0.3.0.0 (C4, research 03 F11): a new shipment joins a load sent `elapsed` days ago that takes `loadDays`: sent within MERGE_DAYS and not arrived. */
    static boolean joins(float elapsed, float loadDays) { return elapsed >= 0f && elapsed <= CAEconomy.MERGE_DAYS && elapsed < loadDays; }

    /** 0.3.0.0 (C4): a joined load's days on the way: it arrives once the new part has travelled `travel` days from now. */
    static float joinedDays(float loadDays, float elapsed, float travel) { return Math.max(loadDays, Math.max(0f, elapsed) + Math.max(0f, travel)); }

    /**
     * Pure, 0.3.0.0 fix (C4 review 2): a shipment (shFromId, shFrom, shTo) is on the route from this source (fromId, fromName)
     * to this colony: the same destination and source key; a shipment saved before the key (shFromId null) matches on its
     * name. Keyed on names, one colony's Autopilot Storage and Storage were two routes with two fees for one trip.
     */
    static boolean sameRoute(String shFromId, String shFrom, String shTo, String fromId, String fromName, String toId) {
        if (toId == null || !toId.equals(shTo)) return false;
        if (shFromId != null) return shFromId.equals(fromId);
        return fromName != null && fromName.equals(shFrom);
    }

    /** 0.3.0.0 (C4): pure: the order of sources by distance, nearest first (ties keep their order). */
    static List<Integer> order(final List<Float> ly) {
        List<Integer> idx = new ArrayList<Integer>();
        for (int i = 0; i < ly.size(); i++) idx.add(i);
        Collections.sort(idx, new Comparator<Integer>() {
            public int compare(Integer a, Integer b) { return Float.compare(ly.get(a), ly.get(b)); }
        });
        return idx;
    }

    // ---------------- stores ----------------

    static boolean holds(MarketAPI m) { return m != null && m.isPlayerOwned() && !CAEngine.isOutpost(m); }

    /** A colony's own Autopilot Storage. */
    public static CargoAPI of(MarketAPI m) { return of(m.getId()); }

    public static CargoAPI of(String marketId) {
        Map<String, CargoAPI> all = CAData.get().stores();
        CargoAPI c = all.get(marketId);
        if (c == null) { c = Global.getFactory().createCargo(true); all.put(marketId, c); }
        if (c.getMothballedShips() == null) c.initMothballedShips("player");   // other mods iterate every submarket's ship list
        return c;
    }

    /** Your colonies with an Autopilot Storage, biggest first. */
    public static List<MarketAPI> markets() {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (holds(m)) out.add(m);
        Collections.sort(out, new Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) { return b.getSize() - a.getSize(); }
        });
        return out;
    }

    /** A colony's store together with the colony, for loops over every stack. */
    public static class Store {
        public final MarketAPI market; public final CargoAPI cargo;
        Store(MarketAPI m, CargoAPI c) { market = m; cargo = c; }
    }

    public static List<Store> all() {
        List<Store> out = new ArrayList<Store>();
        for (MarketAPI m : markets()) out.add(new Store(m, of(m)));
        return out;
    }

    /** 0.3.0.0 (C4, research 03 F11): every colony's Autopilot Storage, the one nearest to `to` first (all(), biggest first, for ties). */
    public static List<Store> nearest(MarketAPI to) {
        List<Store> stores = all();
        List<Float> d = new ArrayList<Float>();
        for (Store s : stores) d.add(ly(s.market, to));
        List<Store> out = new ArrayList<Store>();
        for (int i : order(d)) out.add(stores.get(i));
        return out;
    }

    /** 0.3.0.0 (C4): these colonies, the one nearest to `to` first. */
    public static List<MarketAPI> byDistance(List<MarketAPI> markets, MarketAPI to) {
        List<Float> d = new ArrayList<Float>();
        for (MarketAPI m : markets) d.add(ly(m, to));
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (int i : order(d)) out.add(markets.get(i));
        return out;
    }

    static boolean isCommodity(String id) {
        try { return Global.getSettings().getCommoditySpec(id) != null; } catch (Throwable t) { return false; }
    }

    static float count(CargoAPI c, String id) {
        if (c == null) return 0f;
        return isCommodity(id) ? c.getCommodityQuantity(id) : c.getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(id, null));
    }

    /** How many of a commodity or item are in every colony's Autopilot Storage. */
    public static float count(String id) {
        float n = 0f;
        for (Store s : all()) n += count(s.cargo, id);
        return n;
    }

    /** How many are on their way to this colony. */
    public static int inTransit(String id, String toMarketId) {
        int n = 0;
        for (Shipment sh : CAData.get().shipments()) if (id.equals(sh.id()) && (toMarketId == null || toMarketId.equals(sh.to))) n += sh.qty;
        return n;
    }

    static float ly(SectorEntityToken a, SectorEntityToken b) {
        if (a == null || b == null) return 0f;
        try { return Misc.getDistanceLY(a, b); } catch (Throwable t) { return 0f; }
    }

    static float ly(MarketAPI a, MarketAPI b) { return a == null || b == null ? 0f : ly(a.getPrimaryEntity(), b.getPrimaryEntity()); }

    /** What a shipment from there to this colony costs, at the convenience fee level. */
    public static float fee(CASettings s, float ly) { return CABudget.fee(s, baseFee(ly)); }

    /** 0.3.0.0 (C4): days since a shipment left. */
    static float elapsed(Shipment sh) {
        try { return Global.getSector().getClock().getElapsedDaysSince(sh.sent); } catch (Throwable t) { return Float.MAX_VALUE; }
    }

    /**
     * 0.3.0.0 fix (C4 review 2): a source's route key: the market of the place it leaves from (a colony's primary entity), else
     * that place's own id (your fleet), else its name.
     */
    public static String routeKey(SectorEntityToken fromEntity, String fromName) {
        try {
            if (fromEntity != null) {
                MarketAPI m = fromEntity.getMarket();
                if (m != null && m.getId() != null && !(fromEntity instanceof com.fs.starfarer.api.campaign.CampaignFleetAPI)) return m.getId();
                if (fromEntity.getId() != null) return fromEntity.getId();
            }
        } catch (Throwable ignored) { }
        return fromName;
    }

    /**
     * 0.3.0.0 (C4, research 03 F11): the loads on this route a new shipment joins (from the same place to the same colony, sent
     * within MERGE_DAYS, still on the way). 0.3.0.0 fix (C4 review 2): the same place is the same source key (sameRoute).
     */
    static List<Shipment> route(String fromId, String fromName, String toId) {
        List<Shipment> out = new ArrayList<Shipment>();
        if ((fromId == null && fromName == null) || toId == null) return out;
        for (Shipment sh : CAData.get().shipments()) if (sameRoute(sh.fromId, sh.from, sh.to, fromId, fromName, toId) && joins(elapsed(sh), sh.days)) out.add(sh);
        return out;
    }

    /** 0.3.0.0 (C4): the fee a shipment from there to this colony would pay now: none when it joins a load on that route. */
    public static float quote(SectorEntityToken fromEntity, String fromName, MarketAPI to) {
        return quote(fromEntity, routeKey(fromEntity, fromName), fromName, to);
    }

    /** 0.3.0.0 fix (C4 review 2): as above, on the route from this source key (routeKey). */
    public static float quote(SectorEntityToken fromEntity, String fromId, String fromName, MarketAPI to) {
        if (to == null) return 0f;
        if (!route(fromId, fromName, to.getId()).isEmpty()) return 0f;
        return fee(CAData.settings(), ly(fromEntity, to.getPrimaryEntity()));
    }

    static boolean same(Shipment sh, String commodity, SpecialItemData special) {
        if (commodity != null) return commodity.equals(sh.commodity);
        if (special == null || sh.special == null || sh.commodity != null) return false;
        return eq(special.getId(), sh.special.getId()) && eq(special.getData(), sh.special.getData());
    }

    static boolean eq(Object a, Object b) { return a == null ? b == null : a.equals(b); }

    /**
     * Ships qty of a commodity (special null) or one exact item from cargo `from` (at `fromEntity`, named `fromName`) to the
     * colony's Autopilot Storage. Takes it out of `from` now and charges the fee from the monthly budget; false (nothing
     * moved) when the budget or the credit reserve can't cover it. 0.3.0.0 (C4, research 03 F11): a shipment on a route with a
     * load sent within MERGE_DAYS joins that load for no new fee, and the load arrives once this part has travelled.
     */
    public static boolean ship(CargoAPI from, SectorEntityToken fromEntity, String fromName, MarketAPI to, String commodity, SpecialItemData special,
                               int qty, String why) {
        return ship(from, fromEntity, routeKey(fromEntity, fromName), fromName, to, commodity, special, qty, why);
    }

    /** 0.3.0.0 fix (C4 review 2): as above, on the route from this source key (routeKey; fromName is for the log). */
    public static boolean ship(CargoAPI from, SectorEntityToken fromEntity, String fromId, String fromName, MarketAPI to, String commodity,
                               SpecialItemData special, int qty, String why) {
        if (from == null || to == null || qty <= 0) return false;
        CASettings s = CAData.settings();
        float ly = ly(fromEntity, to.getPrimaryEntity());
        List<Shipment> load = route(fromId, fromName, to.getId());
        float fee = load.isEmpty() ? fee(s, ly) : 0f;
        if (fee > 0f && (fee > CABudget.available(s) || CABuild.credits() - fee < s.reserve)) {
            if (CALog.once("ship:wait:" + to.getId() + ":" + (commodity != null ? commodity : special.getId())))
                CALog.add("Shipping to " + to.getName() + " waits for budget (" + Misc.getDGSCredits(fee) + ")");
            return false;
        }
        if (commodity != null) {
            if (from.getCommodityQuantity(commodity) < qty) return false;
            from.removeCommodity(commodity, qty);
        } else {
            if (from.getQuantity(CargoAPI.CargoItemType.SPECIAL, special) < qty) return false;
            from.removeItems(CargoAPI.CargoItemType.SPECIAL, special, qty);
        }
        float travel = days(ly);
        if (!load.isEmpty()) {
            // 0.3.0.0 (C4): joins the load on this route: one fee paid already; everything on it arrives once this part has travelled
            Shipment joined = null;
            float sentAgo = 0f;
            for (Shipment sh : load) {
                float el = elapsed(sh);
                sh.days = joinedDays(sh.days, el, travel);
                if (joined == null && same(sh, commodity, special)) joined = sh;
                sentAgo = Math.max(sentAgo, el);
            }
            String name;
            if (joined != null) { joined.qty += qty; name = joined.name(); }
            else {
                Shipment sh = shipment(commodity, special, qty, fromId, fromName, to, travel, why);
                CAData.get().shipments().add(sh);
                name = sh.name();
            }
            CALog.add("Shipping " + (qty > 1 ? qty + " " : "") + name + " from " + fromName + " to " + to.getName() + " with the load sent "
                    + Math.round(sentAgo) + (Math.round(sentAgo) == 1 ? " day" : " days") + " ago, " + Math.round(travel) + " days, no extra fee"
                    + (why != null ? " (" + why + ")" : ""));
            return true;
        }
        // CABudget.charge takes the fee from your credits; 0.2.45.1: it was also subtracted here, so every shipment cost twice its fee
        if (fee > 0f) CABudget.charge(s, 0f, fee, 0f);
        Shipment sh = shipment(commodity, special, qty, fromId, fromName, to, travel, why);
        CAData.get().shipments().add(sh);
        CALog.add("Shipping " + (qty > 1 ? qty + " " : "") + sh.name() + " from " + fromName + " to " + to.getName() + ", " + Math.round(sh.days)
                + " days, " + Misc.getDGSCredits(fee) + (why != null ? " (" + why + ")" : ""));
        return true;
    }

    static Shipment shipment(String commodity, SpecialItemData special, int qty, String fromId, String fromName, MarketAPI to, float days, String why) {
        Shipment sh = new Shipment();
        sh.commodity = commodity; sh.special = special; sh.qty = qty; sh.from = fromName; sh.to = to.getId();
        sh.fromId = fromId;   // 0.3.0.0 fix (C4 review 2): the route's source key
        sh.sent = Global.getSector().getClock().getTimestamp(); sh.days = days; sh.why = why;
        return sh;
    }

    /** Ships from one colony's Autopilot Storage to another's. */
    public static boolean ship(MarketAPI from, MarketAPI to, String commodity, SpecialItemData special, int qty, String why) {
        return ship(of(from), from.getPrimaryEntity(), from.getId(), from.getName() + "'s Autopilot Storage", to, commodity, special, qty, why);
    }

    /** The colony nearest to `to` whose Autopilot Storage holds this commodity or item, or null. */
    public static MarketAPI nearestHolding(String id, MarketAPI to) {
        MarketAPI best = null; float bestLy = Float.MAX_VALUE;
        for (Store s : all()) {
            if (count(s.cargo, id) < 1f) continue;
            float d = ly(s.market, to);
            if (d < bestLy) { bestLy = d; best = s.market; }
        }
        return best;
    }

    /** Takes an industry's AI core and item out into its own colony's Autopilot Storage (before it's removed). */
    public static void emptyInto(MarketAPI m, Industry ind) {
        CargoAPI st = of(m);
        String core = ind.getAICoreId();
        if (core != null) { ind.setAICoreId(null); st.addCommodity(core, 1); }
        SpecialItemData item = ind.getSpecialItem();
        if (item != null) { ind.setSpecialItem(null); st.addSpecial(item, 1); }
    }

    // ---------------- daily ----------------

    /** Delivers what has arrived, and moves an old save's shared storage into the biggest colony's (once). */
    public static void daily() {
        CAData d = CAData.get();
        migrate(d);
        Iterator<Shipment> it = d.shipments().iterator();
        while (it.hasNext()) {
            Shipment sh = it.next();
            if (Global.getSector().getClock().getElapsedDaysSince(sh.sent) < sh.days) continue;
            MarketAPI to = CAAuto.market(sh.to);
            if (!holds(to)) {   // the colony was lost or abandoned on the way: the biggest one you have takes it
                List<MarketAPI> ms = markets();
                if (ms.isEmpty()) continue;   // nowhere to deliver yet; it waits
                to = ms.get(0);
            }
            CargoAPI st = of(to);
            if (sh.commodity != null) st.addCommodity(sh.commodity, sh.qty);
            else st.addSpecial(sh.special, sh.qty);
            it.remove();
            CALog.add(to.getName() + ": " + (sh.qty > 1 ? sh.qty + " " : "") + sh.name() + " arrived in Autopilot Storage");
        }
    }

    static void migrate(CAData d) {
        if (d.storage == null || d.storage.isEmpty()) return;
        List<MarketAPI> ms = markets();
        if (ms.isEmpty()) return;
        MarketAPI to = ms.get(0);
        CargoAPI dest = of(to);
        dest.addAll(d.storage);
        d.storage.clear();
        CALog.add("Autopilot Storage is per colony now: what was in the old shared storage is in " + to.getName() + "'s. The manager ships "
                + "items and cores to other colonies when it needs them there, for a fee and travel time", true);
    }

    /** Every stack in every colony's Autopilot Storage, with its colony. */
    public static class Held {
        public final MarketAPI market; public final CargoStackAPI stack;
        Held(MarketAPI m, CargoStackAPI s) { market = m; stack = s; }
    }

    public static List<Held> stacks() {
        List<Held> out = new ArrayList<Held>();
        for (Store s : all()) for (CargoStackAPI st : s.cargo.getStacksCopy()) out.add(new Held(s.market, st));
        return out;
    }
}
