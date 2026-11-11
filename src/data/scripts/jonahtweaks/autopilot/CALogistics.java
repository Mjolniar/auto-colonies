package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.impl.items.BlueprintProviderItem;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Storage logistics (0.2.39.0, setting, off unless you switch it on): your Storage tabs gathered in two places, the way
 * custom production already delivers to one.
 * - The refit hub (a colony or market you pick; by default your production gathering point) gets what you refit a fleet
 *   with from every other Storage you have: mothballed ships, weapons, fighter wings, hull mod specs you don't know yet,
 *   blueprints you don't know yet (one of each), and, if you like, supplies, fuel, crew and marines.
 * - The sell hub (optional) gets what you'd only sell: blueprints and hull mod specs you already know, and spare copies of
 *   ones you don't.
 * Once a week, from each Storage with something to move, one load to each hub. Fair play: nothing teleports. A load costs
 * what an Autopilot Storage shipment does (2,000 plus 300 a light-year, times the fee level) plus 1,000 a ship, from the
 * monthly budget, and arrives 2 days plus a day a light-year later, in the hub's normal Storage. A load that can't be paid
 * for waits. Storage at a market whose faction is hostile to you is left alone. Colony items and AI cores are left where
 * they are (Autopilot Storage handles those), and so is anything in Autopilot Storage.
 */
public class CALogistics {
    public static class Load {
        public String from, fromName, to, toName, why;
        public CargoAPI cargo;
        public long sent;
        public float days;
    }

    static final float PER_SHIP = 1000f;
    static final Set<String> REFIT_GOODS = new HashSet<String>(Arrays.asList("supplies", "fuel", "crew", "marines"));
    static float lastRun = Float.NaN;   // 0.3.0.0 (A1 fix): never fired; the game's day is negative, so -99 read as a later date

    // ---------------- hubs ----------------

    static boolean access(MarketAPI m) {
        if (m == null) return false;
        try {
            if (m.getFaction() != null && m.getFaction().isHostileTo(Factions.PLAYER)) return false;
            return Misc.playerHasStorageAccess(m) && Misc.getStorageCargo(m) != null;
        } catch (Throwable t) { return false; }
    }

    static MarketAPI market(String id) { return id == null ? null : Global.getSector().getEconomy().getMarket(id); }

    /** Runs weekly when you switched it on, or for the capital (0.2.42.0: its Storage gathering, on with it by default). */
    static boolean active(CASettings s) { return s.logisticsMode == 1 || CACapital.gathers(s); }

    /**
     * The refit hub. With Storage logistics on: the market you picked, else the capital, else your production gathering
     * point. With it off, only the capital's own gathering runs, and it only ever sends to the capital (0.2.42.2: never to a
     * hub picked back when logistics was on, and never while a stand-in has the capital's deliveries).
     */
    public static MarketAPI refitHub(CASettings s) {
        MarketAPI m;
        if (s.logisticsMode == 1) {
            m = market(s.logisticsHub);
            if (m == null) m = CACapital.gatherTarget(s);
            if (m == null) try { m = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint(); } catch (Throwable ignored) { }
        } else m = CACapital.gatherTarget(s);
        return access(m) ? m : null;
    }

    /** The sell hub you picked (Storage logistics on), else the capital while it gathers your Storage, or null. */
    public static MarketAPI sellHub(CASettings s) {
        MarketAPI m = s.logisticsMode == 1 ? market(s.logisticsSellHub) : null;
        if (m == null) m = CACapital.gatherTarget(s);
        return access(m) ? m : null;
    }

    /** Markets you can pick as a hub: your colonies, then every market where you have storage. */
    public static List<MarketAPI> choices() {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (access(m)) out.add(m);
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) if (!out.contains(m) && access(m)) out.add(m);
        return out;
    }

    /** Next refit hub choice: the gathering point (null), then each choice. */
    public static String next(String cur, boolean allowNone) {
        List<String> order = new ArrayList<String>();
        order.add(null);
        for (MarketAPI m : choices()) order.add(m.getId());
        int i = order.indexOf(cur);
        return order.get((i + 1) % order.size());
    }

    public static String hubName(String id, boolean refit) {
        MarketAPI m = market(id);
        if (m != null) return m.getName();
        MarketAPI cap = CACapital.capital();
        if (cap != null && (refit || CACapital.gathers(CAData.settings()))) return "Capital (" + cap.getName() + ")";
        if (!refit) return "None";
        MarketAPI g = null;
        try { g = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint(); } catch (Throwable ignored) { }
        return "Production gathering point" + (g != null ? " (" + g.getName() + ")" : "");
    }

    // ---------------- what goes where ----------------

    /** Whether you know everything this blueprint (or blueprint package) teaches; false when it teaches nothing readable. */
    static boolean known(BlueprintProviderItem b, FactionAPI f) {
        boolean any = false;
        try {
            if (b.getProvidedShips() != null) for (String id : b.getProvidedShips()) { any = true; if (!f.knowsShip(id)) return false; }
            if (b.getProvidedWeapons() != null) for (String id : b.getProvidedWeapons()) { any = true; if (!f.knowsWeapon(id)) return false; }
            if (b.getProvidedFighters() != null) for (String id : b.getProvidedFighters()) { any = true; if (!f.knowsFighter(id)) return false; }
            if (b.getProvidedIndustries() != null) for (String id : b.getProvidedIndustries()) { any = true; if (!f.knowsIndustry(id)) return false; }
        } catch (Throwable t) { return false; }
        return any;
    }

    /** "refit", "sell" or null (stays): where one stack goes. kept counts the unknown blueprints already headed for the refit hub. */
    static String destination(CargoStackAPI st, CASettings s, FactionAPI f, Map<String, Integer> kept) {
        if (st.isWeaponStack() || st.isFighterWingStack()) return "refit";
        if (st.isCommodityStack()) return s.logisticsSupplies && REFIT_GOODS.contains(st.getCommodityId()) ? "refit" : null;
        if (!st.isSpecialStack() || st.getSpecialDataIfSpecial() == null) return null;
        SpecialItemData d = st.getSpecialDataIfSpecial();
        boolean knows;
        if ("modspec".equals(d.getId())) knows = d.getData() != null && f.knowsHullMod(d.getData());
        else if (st.getPlugin() instanceof BlueprintProviderItem) knows = known((BlueprintProviderItem) st.getPlugin(), f);
        else return null;   // colony items and everything else stay
        if (knows) return "sell";
        String key = d.getId() + ":" + d.getData();
        Integer n = kept.get(key);
        return n != null && n >= 1 ? "sell" : "refit";   // one copy to learn, the spares to sell
    }

    // ---------------- daily ----------------

    public static void daily(CAData d, CASettings s, float now) {
        deliver(d);
        if (!active(s) || !CATime.due(lastRun, now, 7f)) return;   // 0.3.0.0 (A1 fix): on the game's own (negative) clock
        lastRun = now;
        MarketAPI refit = refitHub(s), sell = sellHub(s);
        if (refit == null && sell == null) {
            if (CALog.once("logi:nohub")) CALog.add("Storage logistics: no hub to send to (your refit hub has no Storage you can use); pick one in Settings");
            return;
        }
        FactionAPI f = Global.getSector().getPlayerFaction();
        for (MarketAPI m : choices()) {
            try { gather(d, s, f, m, refit, sell); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "storage logistics failed at " + m.getName(), t); }
        }
    }

    static CargoAPI newCargo() {
        CargoAPI c = Global.getFactory().createCargo(true);
        c.initMothballedShips(Factions.PLAYER);
        return c;
    }

    static void gather(CAData d, CASettings s, FactionAPI f, MarketAPI m, MarketAPI refit, MarketAPI sell) {
        CargoAPI st = Misc.getStorageCargo(m);
        if (st == null) return;
        Map<String, Integer> kept = new HashMap<String, Integer>();
        List<CargoStackAPI> toRefit = new ArrayList<CargoStackAPI>(), toSell = new ArrayList<CargoStackAPI>();
        Map<CargoStackAPI, Integer> splitKeep = new HashMap<CargoStackAPI, Integer>();   // unknown blueprints: one copy to the refit hub, the rest to sell
        boolean oneHub = sell != null && sell == refit;   // the capital is both (0.2.42.0): one load, one fee
        for (CargoStackAPI x : st.getStacksCopy()) {
            String where = destination(x, s, f, kept);
            if (where == null) continue;
            if (oneHub && where.equals("sell")) where = "refit";
            if (where.equals("refit") && x.isSpecialStack()) {
                SpecialItemData sd = x.getSpecialDataIfSpecial();
                kept.put(sd.getId() + ":" + sd.getData(), 1);
                if (x.getSize() > 1f && sell != null && sell != m && !oneHub) splitKeep.put(x, (int) x.getSize() - 1);
            }
            if (where.equals("refit") && refit != null && refit != m) toRefit.add(x);
            else if (where.equals("sell") && sell != null && sell != m) toSell.add(x);
        }
        List<FleetMemberAPI> ships = new ArrayList<FleetMemberAPI>();
        if (s.logisticsShips && refit != null && refit != m && st.getMothballedShips() != null) ships.addAll(st.getMothballedShips().getMembersListCopy());

        if (!toRefit.isEmpty() || !ships.isEmpty()) {
            Load l = load(m, refit, "refit hub");
            for (CargoStackAPI x : toRefit) {
                Integer spare = splitKeep.get(x);
                int n = spare == null ? (int) x.getSize() : (int) x.getSize() - spare;
                move(st, l.cargo, x, n);
            }
            for (FleetMemberAPI fm : ships) { st.getMothballedShips().removeFleetMember(fm); l.cargo.getMothballedShips().addFleetMember(fm); }
            send(d, s, l, st);
        }
        // spare copies of unknown blueprints, plus everything you know, to the sell hub
        if (sell != null && sell != m && (!toSell.isEmpty() || !splitKeep.isEmpty())) {
            Load l = load(m, sell, "sell hub");
            for (CargoStackAPI x : toSell) move(st, l.cargo, x, (int) x.getSize());
            for (Map.Entry<CargoStackAPI, Integer> e : splitKeep.entrySet()) move(st, l.cargo, e.getKey(), e.getValue());
            if (!l.cargo.isEmpty()) send(d, s, l, st);
        }
    }

    static Load load(MarketAPI from, MarketAPI to, String why) {
        Load l = new Load();
        l.from = from.getId(); l.fromName = from.getName(); l.to = to.getId(); l.toName = to.getName(); l.why = why;
        l.cargo = newCargo();
        return l;
    }

    static void move(CargoAPI from, CargoAPI to, CargoStackAPI x, int n) {
        if (n <= 0) return;
        to.addItems(x.getType(), x.getData(), n);
        from.removeItems(x.getType(), x.getData(), n);
    }

    /** Pays and sends the load, or puts everything back and waits for budget. */
    static void send(CAData d, CASettings s, Load l, CargoAPI back) {
        if (l.cargo.isEmpty() && l.cargo.getMothballedShips().getNumMembers() == 0) return;
        MarketAPI from = market(l.from), to = market(l.to);
        float ly = CAStores.ly(from, to);
        int ships = l.cargo.getMothballedShips().getNumMembers();
        float fee = CABudget.fee(s, CAStores.baseFee(ly) + PER_SHIP * ships);
        if (fee > CABudget.available(s) || CABuild.credits() < fee) {
            back.addAll(l.cargo);
            for (FleetMemberAPI fm : l.cargo.getMothballedShips().getMembersListCopy()) back.getMothballedShips().addFleetMember(fm);
            if (CALog.once("logi:wait:" + l.from + ":" + l.to + ":" + Global.getSector().getClock().getMonth()))
                CALog.add("Storage logistics: " + describe(l) + " from " + l.fromName + " to " + l.toName + " (" + l.why + ") waits for budget (" + Misc.getDGSCredits(fee) + ")");
            return;
        }
        // 0.3.0.0 (C4, research 03 F13): a load goes when it carries 10 times its fee in value, or 30 days after its route's last load
        String mark = "logi:last:" + l.from + ":" + l.to;
        Long last = CAMark.at(mark);
        float since = last == null ? Float.MAX_VALUE : CAMark.days(last, CAMark.now());
        if (!CAEconomy.loadWorthSending(CAEconomy.cargoValue(l.cargo), fee, since)) {
            back.addAll(l.cargo);
            for (FleetMemberAPI fm : l.cargo.getMothballedShips().getMembersListCopy()) back.getMothballedShips().addFleetMember(fm);
            return;
        }
        CAMark.set(mark, CAMark.now());
        l.days = CAStores.days(ly);
        l.sent = Global.getSector().getClock().getTimestamp();
        CABudget.charge(s, 0f, fee, 0f);   // takes the fee from your credits
        d.loads().add(l);
        CALog.add("Storage logistics: " + describe(l) + " from " + l.fromName + "'s Storage to " + l.toName + " (" + l.why + "), "
                + Math.round(l.days) + " days, " + Misc.getDGSCredits(fee));
    }

    static String describe(Load l) {
        int ships = l.cargo.getMothballedShips().getNumMembers(), weapons = 0, wings = 0, bps = 0, goods = 0;
        for (CargoStackAPI x : l.cargo.getStacksCopy()) {
            if (x.isWeaponStack()) weapons += (int) x.getSize();
            else if (x.isFighterWingStack()) wings += (int) x.getSize();
            else if (x.isSpecialStack()) bps += (int) x.getSize();
            else if (x.isCommodityStack()) goods += (int) x.getSize();
        }
        List<String> parts = new ArrayList<String>();
        if (ships > 0) parts.add(ships + (ships == 1 ? " ship" : " ships"));
        if (weapons > 0) parts.add(weapons + " weapons");
        if (wings > 0) parts.add(wings + " fighter wings");
        if (bps > 0) parts.add(bps + " blueprints and specs");
        if (goods > 0) parts.add(goods + " supplies, fuel and crew");
        return parts.isEmpty() ? "a load" : String.join(", ", parts);
    }

    /** Daily: loads that have arrived go into the hub's Storage (your biggest colony's if that Storage is gone). */
    static void deliver(CAData d) {
        if (d.loads == null || d.loads.isEmpty()) return;
        for (Iterator<Load> it = d.loads.iterator(); it.hasNext(); ) {
            Load l = it.next();
            if (Global.getSector().getClock().getElapsedDaysSince(l.sent) < l.days) continue;
            MarketAPI to = market(l.to);
            if (!access(to)) {
                to = null;
                for (MarketAPI m : Misc.getPlayerMarkets(false)) if (access(m) && (to == null || m.getSize() > to.getSize())) to = m;
            }
            if (to == null) continue;   // no Storage anywhere yet: it waits in transit
            CargoAPI st = Misc.getStorageCargo(to);
            st.addAll(l.cargo);
            if (st.getMothballedShips() == null) st.initMothballedShips(Factions.PLAYER);
            for (FleetMemberAPI fm : l.cargo.getMothballedShips().getMembersListCopy()) st.getMothballedShips().addFleetMember(fm);
            it.remove();
            CALog.add("Storage logistics: " + describe(l) + " delivered to " + to.getName() + "'s Storage" + (to.getId().equals(l.to) ? "" : " (" + l.toName + "'s Storage was gone)"));
        }
    }

    /** What's on its way, for the panel. */
    public static List<Load> inTransit() { CAData d = CAData.get(); return d.loads == null ? Collections.<Load>emptyList() : d.loads; }
}
