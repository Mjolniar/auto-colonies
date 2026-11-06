package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Objectives;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Stable locations (2.40.0): what goes up at the stable locations in systems with your Auto colonies, picked the way the
 * colony plans would, by what it adds at every colony of yours in the system, valued with each colony's goal weights.
 *
 * - Vanilla's makeshift comm relay (+1 stability at your colonies in the system), nav buoy (+2 burn for your fleets there)
 *   and sensor array (+400 sensor range). One of each kind counts (the best), so a kind you already own there isn't built
 *   again. Cost: vanilla's materials (15 heavy machinery, 30 metals, 5 transplutonics), bought at base price.
 * - Terraforming Made Easy's Domain-era upgrade of a makeshift one you own (twice the effect for the relay; +3 burn,
 *   +700 range for the others): its 50 crew, 1,000 metals and 200 transplutonics bought at base price, plus a Beta core
 *   from Autopilot Storage, only a spare one (two or more stored, or spending freely).
 * - Ashes of the Domain's Hypershunt Receiver (+2 industry slots for every colony in the system while a hypershunt of
 *   yours reaches it): once researched, with a Coronal Hypershunt Tap in Autopilot Storage, in a system your hypershunt
 *   reaches, for its 1,000 refined metal and 100 Domain heavy machinery at base price. While you own a hypershunt, one
 *   stable location per system without a receiver is kept free for one.
 *
 * Fair play, like everything else: nothing appears for free. A construction crew is sent from the system's biggest colony,
 * paid from the monthly budget (the materials plus a fee), and builds it 10 days later (a receiver 30); vanilla's own
 * build does the rest (Objectives.build, the same call the in-person option makes). If the spot's gone by then, the
 * materials and any core are given back. One order a day across the empire. Industrial Evolution's Watchtower comes with
 * its artillery and Secrets of the Frontier's habitat founds a colony, so both are left to you.
 */
public class CAObjectives {
    static final String STABLE = "stable_location", OBJECTIVE = "objective", MAKESHIFT = "makeshift";
    static final String COMM = "comm_relay", NAV = "nav_buoy", SENSOR = "sensor_array";
    static final String[] KINDS = {COMM, NAV, SENSOR};
    static final String RECEIVER = "aotd_hypershunt_receiver", TAP = "coronal_portal", TME = "terraformingmadeeasy";
    static final float CREW_DAYS = 10f, RECEIVER_DAYS = 30f, BASE_FEE = 5000f, PAYBACK_MONTHS = 36f;
    static final String[] MAKESHIFT_RES = {"heavy_machinery", "metals", "rare_metals"};
    static final int[] MAKESHIFT_QTY = {15, 30, 5};
    static final String[] UPGRADE_RES = {"crew", "metals", "rare_metals"};
    static final int[] UPGRADE_QTY = {50, 1000, 200};
    static final String[] RECEIVER_RES = {"refined_metal", "domain_heavy_machinery"};
    static final int[] RECEIVER_QTY = {1000, 100};
    /** A receiver's +2 industry slots, per colony: what Autopilot counts an extra industry slot at elsewhere (CAOmega). */
    static final float RECEIVER_PER_COLONY = 40000f;

    /** A crew on its way. */
    public static class Order {
        public String system, entityId, type, what, why;
        public long sent;
        public float days;
        public float paid;          // the materials, given back if the spot's gone
        public String core;         // a Beta core or a Coronal Hypershunt Tap it took, given back if the spot's gone
        public String coreFrom;     // the colony whose Autopilot Storage it came from
    }

    // ---------------- what each one does ----------------

    /** What one objective adds per colony of yours in its system, in CAEngine's effect units ([1] stability, [4] fleet). Pure. */
    static float[] perColony(String kind, boolean domain) {
        float[] fx = new float[7];
        if (COMM.equals(kind)) fx[1] = domain ? 2f : 1f;            // CommRelayCondition: +1 makeshift, +2 Domain-era
        else if (NAV.equals(kind)) fx[4] = domain ? 0.15f : 0.1f;   // +2 / +3 burn: patrols and defense fleets get there sooner
        else if (SENSOR.equals(kind)) fx[4] = domain ? 0.09f : 0.05f;   // +400 / +700 sensor range: raiders seen sooner
        return fx;
    }

    /** Credits a month the effects are worth at these colonies, with each one's goal weights. */
    static float worth(float[] fx, List<float[]> weights) {
        float v = 0f;
        for (float[] w : weights) for (int i = 0; i < 7; i++) v += w[i] * fx[i];
        return v;
    }

    /** Materials at base price. */
    static float materials(String[] res, int[] qty) {
        float v = 0f;
        for (int i = 0; i < res.length; i++) {
            try { v += Global.getSettings().getCommoditySpec(res[i]).getBasePrice() * qty[i]; }
            catch (Throwable t) { v += 100f * qty[i]; }
        }
        return v;
    }

    /** Worth building: it pays for itself within three years. Pure. */
    static boolean pays(float monthly, float cost) { return monthly > 0f && monthly * PAYBACK_MONTHS >= cost; }

    static String label(String kind, boolean domain) {
        String n = COMM.equals(kind) ? "comm relay" : NAV.equals(kind) ? "nav buoy" : "sensor array";
        return (domain ? "Domain-era " : "makeshift ") + n;
    }

    static String effect(String kind, boolean domain) {
        if (COMM.equals(kind)) return "+" + (domain ? 2 : 1) + " stability at your colonies there";
        if (NAV.equals(kind)) return "+" + (domain ? 3 : 2) + " burn for your fleets there";
        return "+" + (domain ? 700 : 400) + " sensor range for your fleets there";
    }

    // ---------------- reading a system ----------------

    static String kindOf(SectorEntityToken e) {
        for (String k : KINDS) if (e.hasTag(k)) return k;
        return null;
    }

    static boolean usable(SectorEntityToken e) {
        return e != null && !e.isExpired() && !e.hasTag("non_clickable") && !e.hasTag("fading_out_and_expiring");
    }

    static class Sys {
        StarSystemAPI sys;
        List<MarketAPI> colonies = new ArrayList<MarketAPI>();
        List<float[]> weights = new ArrayList<float[]>();
        MarketAPI base;   // where the crew leaves from: the biggest colony
        List<SectorEntityToken> free = new ArrayList<SectorEntityToken>();
        Map<String, SectorEntityToken> mine = new HashMap<String, SectorEntityToken>();   // kind -> your best one there
        Set<String> pending = new HashSet<String>();
        boolean receiver;
    }

    static Sys read(StarSystemAPI sys, CAData d) {
        Sys x = new Sys();
        x.sys = sys;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m.getStarSystem() != sys || CAEngine.isOutpost(m)) continue;
            x.colonies.add(m);
            CAData.Colony c = d.colonies == null ? null : d.colonies.get(m.getId());
            x.weights.add(c != null ? CAItems.coreWeights(m, c) : CAEngine.WEIGHTS[CAData.Profile.BALANCED.ordinal()]);
            if (x.base == null || m.getSize() > x.base.getSize()) x.base = m;
        }
        Set<String> claimed = new HashSet<String>();
        for (Order o : d.objectiveOrders()) {
            if (!sys.getId().equals(o.system)) continue;
            claimed.add(o.entityId);
            x.pending.add(o.type.replace("_makeshift", ""));
        }
        for (SectorEntityToken e : sys.getEntitiesWithTag(STABLE)) if (usable(e) && !claimed.contains(e.getId())) x.free.add(e);
        for (SectorEntityToken e : sys.getEntitiesWithTag(OBJECTIVE)) {
            if (!usable(e)) continue;
            if (e.hasTag(RECEIVER)) { x.receiver = true; continue; }
            String k = kindOf(e);
            if (k == null || !Factions.PLAYER.equals(e.getFaction().getId())) continue;
            if (e.getMemoryWithoutUpdate().getBoolean(MemFlags.OBJECTIVE_NON_FUNCTIONAL)) continue;
            SectorEntityToken had = x.mine.get(k);
            if (had == null || (had.hasTag(MAKESHIFT) && !e.hasTag(MAKESHIFT))) x.mine.put(k, e);
        }
        return x;
    }

    // ---------------- picking ----------------

    static class Plan {
        Sys at;
        SectorEntityToken spot;
        String type, what, why, core;
        float monthly, cost, days;
    }

    /** Reachable systems for a Hypershunt Receiver, or empty: researched, a tap stored, your hypershunt reaching them. */
    static Set<String> receiverSystems(boolean[] reserve) {
        Set<String> out = new HashSet<String>();
        if (!Global.getSettings().getModManager().isModEnabled(CAHypershunt.MOD)) return out;
        try {
            List<CAHypershunt.Shunt> shunts = CAHypershunt.all();
            if (shunts.isEmpty()) return out;
            reserve[0] = true;   // you own a hypershunt: a spot stays free for a receiver in every system without one
            if (!CAHypershunt.researched(CAHypershunt.TECH_RECEIVER) || CAStores.count(TAP) < 1f) return out;
            for (CAHypershunt.Spot sp : CAHypershunt.spots(shunts)) if (sp.reachable) out.add(sp.system);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt receiver check failed", t); }
        return out;
    }

    static boolean spareBeta(CASettings s) {
        float n = CAStores.count("beta_core");
        return n >= 2f || (n >= 1f && CASurplus.rich(s));
    }

    static Plan best(CAData d, CASettings s, List<MarketAPI> auto) {
        Set<StarSystemAPI> systems = new LinkedHashSet<StarSystemAPI>();
        for (MarketAPI m : auto) if (m.getStarSystem() != null) systems.add(m.getStarSystem());
        boolean[] reserve = new boolean[1];
        Set<String> receivers = receiverSystems(reserve);
        boolean tme = Global.getSettings().getModManager().isModEnabled(TME), beta = spareBeta(s);
        float makeshiftCost = materials(MAKESHIFT_RES, MAKESHIFT_QTY), upgradeCost = materials(UPGRADE_RES, UPGRADE_QTY) + 7500f;   // a Beta core at what research counts it
        float receiverCost = materials(RECEIVER_RES, RECEIVER_QTY);
        Plan best = null;
        for (StarSystemAPI sys : systems) {
            Sys x = read(sys, d);
            if (x.colonies.isEmpty() || x.base == null) continue;
            boolean keep = reserve[0] && !x.receiver && !x.pending.contains(RECEIVER);
            // a Hypershunt Receiver first: +2 industry slots at every colony there
            if (keep && receivers.contains(sys.getBaseName()) && !x.free.isEmpty()) {
                Plan p = plan(x, x.free.get(0), RECEIVER, "Hypershunt Receiver", "+2 industry slots at every colony of yours there",
                        RECEIVER_PER_COLONY * x.colonies.size(), receiverCost, RECEIVER_DAYS);
                p.core = TAP;
                if (pays(p.monthly, p.cost) && (best == null || p.monthly > best.monthly)) best = p;
                continue;   // one plan per system a day; the rest waits
            }
            int open = x.free.size() - (keep ? 1 : 0);
            for (String k : KINDS) {
                if (x.pending.contains(k)) continue;
                SectorEntityToken have = x.mine.get(k);
                if (have == null && open > 0) {
                    float v = worth(perColony(k, false), x.weights);
                    Plan p = plan(x, x.free.get(0), k + "_makeshift", label(k, false), effect(k, false), v, makeshiftCost, CREW_DAYS);
                    if (pays(p.monthly, p.cost) && (best == null || p.monthly > best.monthly)) best = p;
                } else if (have != null && have.hasTag(MAKESHIFT) && tme && beta) {
                    float v = worth(perColony(k, true), x.weights) - worth(perColony(k, false), x.weights);
                    Plan p = plan(x, have, k, label(k, true), effect(k, true), v, upgradeCost, CREW_DAYS);
                    p.core = "beta_core";
                    p.cost -= 7500f;   // the core's value counted in the payback, not charged
                    if (pays(p.monthly, p.cost + 7500f) && (best == null || p.monthly > best.monthly)) best = p;
                }
            }
        }
        return best;
    }

    static Plan plan(Sys x, SectorEntityToken spot, String type, String what, String why, float monthly, float cost, float days) {
        Plan p = new Plan();
        p.at = x; p.spot = spot; p.type = type; p.what = what; p.why = why; p.monthly = monthly; p.cost = cost; p.days = days;
        return p;
    }

    // ---------------- daily ----------------

    /** Daily: delivers crews that have arrived, then sends at most one new one (Auto) or recommends it once (Recommend only). */
    public static void run(CAData d, CASettings s, List<MarketAPI> auto) {
        if (s.objectiveMode == 2 || Global.getSector() == null) return;
        deliver(d);
        if (CABudget.paused(s) || auto.isEmpty()) return;
        Plan p = best(d, s, auto);
        if (p == null) return;
        String where = p.at.sys.getBaseName();
        if (s.objectiveMode == 1) {
            if (CALog.once("objective:" + p.at.sys.getId() + ":" + p.type))
                CALog.add(where + ": recommends a " + p.what + " at a stable location (" + p.why + "; " + Misc.getDGSCredits(p.cost) + " in materials). Stable locations, under Settings, set to Auto builds it");
            return;
        }
        float fee = CABudget.fee(s, BASE_FEE);
        if (!CAItems.canPay(s, p.cost + fee)) {
            if (CALog.once("objective:budget:" + p.at.sys.getId() + ":" + p.type))
                CALog.add(where + ": a " + p.what + " waits for budget (" + Misc.getDGSCredits(p.cost + fee) + ")");
            return;
        }
        String from = null;
        if (p.core != null) {
            from = takeCore(p.core, p.at.base);
            if (from == null) return;
        }
        Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(p.cost);
        CABudget.charge(s, p.cost, fee, 0f);
        Order o = new Order();
        o.system = p.at.sys.getId(); o.entityId = p.spot.getId(); o.type = p.type; o.what = p.what; o.why = p.why;
        o.paid = p.cost; o.core = p.core; o.coreFrom = from;
        o.sent = Global.getSector().getClock().getTimestamp(); o.days = p.days;
        d.objectiveOrders().add(o);
        CALog.forget("objective:budget:" + p.at.sys.getId() + ":" + p.type);
        CALog.add(where + ": construction crew on its way from " + p.at.base.getName() + " to build a " + p.what + " (" + p.why + "), "
                + Math.round(p.days) + " days, " + Misc.getDGSCredits(p.cost) + " in materials + " + Misc.getDGSCredits(fee) + " fee"
                + (p.core != null ? ", and a " + coreName(p.core) + " from Autopilot Storage" : ""));
    }

    /** Takes one core or tap from Autopilot Storage, the given colony's first; returns the colony id it came from, or null. */
    static String takeCore(String id, MarketAPI prefer) {
        List<CAStores.Held> held = CAStores.stacks();
        CAStores.Held pick = null;
        for (CAStores.Held h : held) {
            if (!has(h.stack, id)) continue;
            if (pick == null || h.market == prefer) pick = h;
        }
        if (pick == null) return null;
        CargoAPI c = CAStores.of(pick.market);
        if (pick.stack.isSpecialStack()) c.removeItems(CargoAPI.CargoItemType.SPECIAL, pick.stack.getSpecialDataIfSpecial(), 1f);
        else c.removeCommodity(id, 1f);
        return pick.market.getId();
    }

    static boolean has(CargoStackAPI st, String id) {
        if (st.getSize() < 1f) return false;
        if (st.isCommodityStack()) return id.equals(st.getCommodityId());
        return st.isSpecialStack() && st.getSpecialDataIfSpecial() != null && id.equals(st.getSpecialDataIfSpecial().getId());
    }

    static StarSystemAPI system(String id) {
        if (id == null) return null;
        for (StarSystemAPI sys : Global.getSector().getStarSystems()) if (id.equals(sys.getId())) return sys;
        return null;
    }

    static void giveBack(Order o) {
        Global.getSector().getPlayerFleet().getCargo().getCredits().add(o.paid);
        if (o.core == null || o.coreFrom == null) return;
        CargoAPI c = CAStores.of(o.coreFrom);
        if (TAP.equals(o.core)) c.addSpecial(new SpecialItemData(TAP, null), 1f);
        else c.addCommodity(o.core, 1f);
    }

    static String coreName(String id) {
        try {
            if (TAP.equals(id)) return Global.getSettings().getSpecialItemSpec(id).getName();
            return Global.getSettings().getCommoditySpec(id).getName();
        } catch (Throwable t) { return id; }
    }

    static void deliver(CAData d) {
        Iterator<Order> it = d.objectiveOrders().iterator();
        while (it.hasNext()) {
            Order o = it.next();
            if (Global.getSector().getClock().getElapsedDaysSince(o.sent) < o.days) continue;
            it.remove();
            SectorEntityToken e = Global.getSector().getEntityById(o.entityId);
            StarSystemAPI sys = system(o.system);
            String where = sys != null ? sys.getBaseName() : "A system";
            boolean upgrade = !o.type.endsWith("_makeshift") && !RECEIVER.equals(o.type);
            boolean ok = usable(e) && (upgrade ? e.hasTag(MAKESHIFT) && Factions.PLAYER.equals(e.getFaction().getId()) : e.hasTag(STABLE));
            if (!ok) {
                giveBack(o);
                CALog.add(where + ": the " + o.what + " wasn't built (the spot was taken or gone); the materials" + (o.core != null ? " and the " + coreName(o.core) : "") + " came back");
                continue;
            }
            try {
                new Objectives(e).build(o.type, Factions.PLAYER);
                CALog.add(where + ": built a " + o.what + " (" + o.why + ")");
            } catch (Throwable t) {
                giveBack(o);
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "building " + o.type + " failed", t);
                CALog.add(where + ": the " + o.what + " couldn't be built; the materials came back");
            }
        }
    }

    /** Crews on their way, for the panels: "makeshift comm relay in Corvus (3 days)". */
    public static List<String> inTransit(CAData d) {
        List<String> out = new ArrayList<String>();
        for (Order o : d.objectiveOrders()) {
            StarSystemAPI sys = system(o.system);
            float days = Math.max(0f, o.days - Global.getSector().getClock().getElapsedDaysSince(o.sent));
            out.add(o.what + " in " + (sys != null ? sys.getBaseName() : "?") + " (" + Math.round(days) + " days)");
        }
        return out;
    }
}
