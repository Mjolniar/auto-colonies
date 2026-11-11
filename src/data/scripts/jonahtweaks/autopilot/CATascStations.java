package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/**
 * Terraforming & Station Construction's stations (0.2.40.1): a mining station in an asteroid belt or field, or a siphon
 * station over a gas giant, founded as a new colony of yours, the way TASC's own Construct abilities do it, only with a
 * construction crew instead of your fleet on site. Only when TASC is installed, its research for the station is done,
 * you're under your colony limit, and the system is one with your Auto colonies that's calm (none of them under a high
 * threat) or protected (CAThreat.protection: military presence there takes the fight), since a new size-3 station is
 * an economic outpost, not a fortress. The new colony starts in Auto with the Money goal.
 *
 * Value: the station market TASC creates has ore and transplutonic ore (mining, from the asteroid belts in the system) or
 * volatiles (siphon, from the gas giant) at the levels TASC gives it; Mining (or the siphon's own output) at size 4 is
 * counted, as the colony grows, less 10,000 a month of running costs, and it has to pay back within five years
 * (or anything while spending freely, where a price counts a twentieth). Cost: TASC's own settings (500,000 credits,
 * 1,000 crew, 250 heavy machinery, 1,000 metals and 250 transplutonics by default), the goods at base price, plus a fee;
 * the crew needs 60 days. If the site's gone or taken by then, everything comes back.
 *
 * Placement mirrors TASC 10.0.9's Construct_Mining_Station and Construct_Siphon_Station (read from its classes): the same
 * entity types, names, orbits and lights overlay, then TASC's own boggledTools.createMiningStationMarket /
 * createSiphonStationMarket makes the colony. Sites pass TASC's own checks (in the belt or field, not too close to a jump
 * point, the per-system cap, a gas giant no one else holds with no siphon station or blocking moon). The astropolis is
 * left to you: it's a habitat, not an economic station. Setting: Station construction, Recommend only by default.
 * Method handles only: TASC's boggledTools names an optional mod's class in one signature, so plain reflection fails.
 */
public class CATascStations {
    static final String MINING = "mining", SIPHON = "siphon";
    static final String MINE_ABILITY = "boggled_construct_mining_station", SIPHON_ABILITY = "boggled_construct_siphon_station";
    static final float CREW_DAYS = 60f, BASE_FEE = CATuning.TASC_BASE_FEE, RUNNING = 10000f, PAYBACK_MONTHS = CATuning.TASC_PAYBACK_MONTHS;   // 0.3.0.0 (A3): fee and payback in CATuning
    static final int GROWN_SIZE = 4;

    static MethodHandle intSetting, boolSetting, researchFor, researched, inBelt, inField, tooClose, beltFocus, fieldOrbit, fieldEntity,
            numTerrains, resourceString, createMining, createSiphon, siphonNew, siphonHas, siphonMoon;
    static boolean linked = false, broken = false;

    public static boolean enabled() { return CATasc.enabled(); }

    static boolean link() {
        if (linked || broken) return linked;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> tools = Class.forName("boggled.campaign.econ.boggledTools", true, cl);
            Class<?> siphon = Class.forName("boggled.campaign.econ.abilities.Construct_Siphon_Station", true, cl);
            MethodHandles.Lookup lk = MethodHandles.publicLookup();
            MethodType tok = MethodType.methodType(boolean.class, SectorEntityToken.class);
            intSetting = lk.findStatic(tools, "getIntSetting", MethodType.methodType(int.class, String.class));
            boolSetting = lk.findStatic(tools, "getBooleanSetting", MethodType.methodType(boolean.class, String.class));
            researchFor = lk.findStatic(tools, "getRequiredResearchForAbility", MethodType.methodType(String.class, String.class));
            researched = lk.findStatic(tools, "isResearched", MethodType.methodType(boolean.class, String.class));
            inBelt = lk.findStatic(tools, "playerFleetInAsteroidBelt", tok);
            inField = lk.findStatic(tools, "playerFleetInAsteroidField", tok);
            tooClose = lk.findStatic(tools, "playerFleetTooCloseToJumpPoint", tok);
            beltFocus = lk.findStatic(tools, "getFocusOfAsteroidBelt", MethodType.methodType(SectorEntityToken.class, SectorEntityToken.class));
            fieldOrbit = lk.findStatic(tools, "getAsteroidFieldOrbit", MethodType.methodType(OrbitAPI.class, SectorEntityToken.class));
            fieldEntity = lk.findStatic(tools, "getAsteroidFieldEntity", MethodType.methodType(SectorEntityToken.class, SectorEntityToken.class));
            numTerrains = lk.findStatic(tools, "getNumAsteroidTerrainsInSystem", MethodType.methodType(int.class, SectorEntityToken.class));
            resourceString = lk.findStatic(tools, "getMiningStationResourceString", MethodType.methodType(String.class, Integer.class));
            createMining = lk.findStatic(tools, "createMiningStationMarket", MethodType.methodType(MarketAPI.class, SectorEntityToken.class));
            createSiphon = lk.findStatic(tools, "createSiphonStationMarket", MethodType.methodType(MarketAPI.class, SectorEntityToken.class, SectorEntityToken.class));
            siphonNew = lk.findConstructor(siphon, MethodType.methodType(void.class));
            siphonHas = lk.findVirtual(siphon, "gasGiantAlreadyHasSiphonStationInOrbit", tok);
            siphonMoon = lk.findVirtual(siphon, "getMoonBlockingSiphonStation", MethodType.methodType(PlanetAPI.class, SectorEntityToken.class));
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Terraforming & Station Construction's station construction; stations are left to you", t);
        }
        return linked;
    }

    static int setting(String key, int dflt) {
        try { return (int) intSetting.invoke(key); } catch (Throwable t) { return dflt; }
    }

    static boolean unlocked(String ability) {
        try {
            String r = (String) researchFor.invoke(ability);
            return r == null || (boolean) researched.invoke(r);
        } catch (Throwable t) { return false; }
    }

    // ---------------- value ----------------

    /** Deposit level to Mining's output modifier (as the game's deposit conditions). Pure. */
    static int levelMod(String level) {
        if (level == null) return 0;
        switch (level) {
            case "trace": case "sparse": return -1;
            case "moderate": case "diffuse": return 0;
            case "abundant": return 1;
            case "rich": case "plentiful": return 2;
            case "ultrarich": return 3;
            default: return 0;
        }
    }

    /** Units a size-GROWN_SIZE station mines of each good at this deposit level: ore size + mod, transplutonic ore size - 2 + mod. Pure. */
    static Map<String, Integer> miningUnits(String level, int size) {
        Map<String, Integer> out = new LinkedHashMap<String, Integer>();
        out.put("ore", Math.max(0, size + levelMod(level)));
        out.put("rare_ore", Math.max(0, size - 2 + levelMod(level)));
        return out;
    }

    /** Volatiles a size-GROWN_SIZE siphon station makes at this level (size - 2 + mod, as Mining's volatiles). Pure. */
    static int siphonUnits(String level, int size) { return Math.max(0, size - 2 + levelMod(level)); }

    static float unitWorth(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getBasePrice() * CAEngine.unitValue; } catch (Throwable t) { return 0f; }
    }

    /** Monthly value of these units, less the station's running costs. Pure apart from base prices. */
    static float monthly(Map<String, Integer> units) {
        float v = -RUNNING;
        for (Map.Entry<String, Integer> e : units.entrySet()) v += e.getValue() * unitWorth(e.getKey());
        return v;
    }

    static float cost(String kind) {
        String p = MINING.equals(kind) ? "boggledMiningStation" : "boggledSiphonStation";
        float v = setting(p + "BuildCreditCost", 500000);
        v += goods("crew", setting(p + "BuildCrewCost", 1000)) + goods("heavy_machinery", setting(p + "BuildHeavyMachineryCost", 250))
                + goods("metals", setting(p + "BuildMetalCost", 1000)) + goods("rare_metals", setting(p + "BuildTransplutonicsCost", 250));
        return v;
    }

    static float goods(String com, int qty) {
        try { return Global.getSettings().getCommoditySpec(com).getBasePrice() * qty; } catch (Throwable t) { return 100f * qty; }
    }

    // ---------------- sites ----------------

    static class Site {
        StarSystemAPI sys;
        String kind, why, level;
        SectorEntityToken gasGiant;   // siphon
        float x, y;                   // mining: a point in the belt or field
        float monthly, cost;
    }

    /** A point in this system: TASC's checks take an entity, so a temporary token stands in for your fleet. */
    static SectorEntityToken token(StarSystemAPI sys, float x, float y) {
        SectorEntityToken t = sys.createToken(x, y);
        sys.addEntity(t);   // in the system, as your fleet would be, for TASC's terrain checks; removed again after
        return t;
    }

    static boolean call(MethodHandle h, SectorEntityToken t) {
        try { return (boolean) h.invoke(t); } catch (Throwable e) { return false; }
    }

    static int miningStationsIn(StarSystemAPI sys) {
        int n = 0;
        for (SectorEntityToken e : sys.getAllEntities()) if (e.hasTag("boggled_mining") && !"neutral".equals(e.getFaction().getId())) n++;
        return n;
    }

    /** The first point in an asteroid belt or field here that TASC would build a mining station at, or null. */
    static Site miningSite(StarSystemAPI sys) {
        if (miningStationsIn(sys) >= setting("boggledMaxNumMiningStationsPerSystem", 1)) return null;
        for (CampaignTerrainAPI t : sys.getTerrainCopy()) {
            String spec = t.getPlugin() == null || t.getPlugin().getSpec() == null ? null : t.getPlugin().getSpec().getId();
            List<float[]> points = new ArrayList<float[]>();
            if ("asteroid_belt".equals(spec) && t.getPlugin() instanceof com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain) {
                com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain ring = (com.fs.starfarer.api.impl.campaign.terrain.BaseRingTerrain) t.getPlugin();
                float r = ring.params.middleRadius;
                for (int k = 0; k < 16; k++) {
                    double a = Math.toRadians(k * 22.5);
                    points.add(new float[]{t.getLocation().x + (float) Math.cos(a) * r, t.getLocation().y + (float) Math.sin(a) * r});
                }
            } else if ("asteroid_field".equals(spec)) {
                points.add(new float[]{t.getLocation().x, t.getLocation().y});
            } else continue;
            for (float[] pt : points) {
                SectorEntityToken tk = token(sys, pt[0], pt[1]);
                try {
                    if (!(call(inBelt, tk) || call(inField, tk)) || call(tooClose, tk)) continue;
                    Site s = new Site();
                    s.sys = sys; s.kind = MINING; s.x = pt[0]; s.y = pt[1];
                    try { s.level = (String) resourceString.invoke(Integer.valueOf((int) numTerrains.invoke(tk))); } catch (Throwable e) { s.level = "moderate"; }
                    s.monthly = monthly(miningUnits(s.level, GROWN_SIZE));
                    s.cost = cost(MINING);
                    s.why = "ore and transplutonic ore (" + s.level + ") from the asteroids";
                    return s;
                } finally { sys.removeEntity(tk); }
            }
        }
        return null;
    }

    /** The best gas giant here for a siphon station that TASC would allow, or null. */
    static Site siphonSite(StarSystemAPI sys) {
        Object ability;
        try { ability = siphonNew.invoke(); } catch (Throwable t) { return null; }
        Site best = null;
        for (PlanetAPI p : sys.getPlanets()) {
            if (!p.isGasGiant() || p.isStar()) continue;
            MarketAPI owner = p.getMarket();
            if (owner != null && owner.getFactionId() != null && !Factions.PLAYER.equals(owner.getFactionId()) && !Factions.NEUTRAL.equals(owner.getFactionId())
                    && (owner.isPlanetConditionMarketOnly() == false)) continue;   // someone else's colony
            try {
                if ((boolean) siphonHas.invoke(ability, (SectorEntityToken) p) || siphonMoon.invoke(ability, (SectorEntityToken) p) != null) continue;
            } catch (Throwable t) { continue; }
            String level = "diffuse";
            if (owner != null) for (MarketConditionAPI c : owner.getConditions()) if (c.getId().startsWith("volatiles_")) level = c.getId().substring("volatiles_".length());
            Map<String, Integer> u = new LinkedHashMap<String, Integer>();
            u.put("volatiles", siphonUnits(level, GROWN_SIZE));
            Site s = new Site();
            s.sys = sys; s.kind = SIPHON; s.gasGiant = p; s.level = level;
            s.monthly = monthly(u);
            s.cost = cost(SIPHON);
            s.why = "volatiles (" + level + ") from " + p.getName();
            if (best == null || s.monthly > best.monthly) best = s;
        }
        return best;
    }

    // ---------------- daily ----------------

    public static class Order {
        public String system, kind, gasGiantId, what, why;
        public float x, y, paid;
        public long sent;
        public float days;
    }

    /** Pays back within five years, or anything while spending freely (where a price counts a twentieth). Pure. */
    static boolean pays(float monthly, float cost, boolean rich) {
        if (monthly <= 0f) return false;
        return monthly * PAYBACK_MONTHS >= cost * (rich ? CASurplus.RICH_PRICE : 1f);
    }

    /** A system TASC's economic stations make sense in: your Auto colonies there are calm, or it's protected. */
    static boolean safe(List<MarketAPI> colonies) {
        boolean protectedSys = false;
        for (MarketAPI m : colonies) {
            if (CASiege.held(m)) return false;
            if (CAThreat.protection(m, false) >= CAThreat.PROTECTED) protectedSys = true;
        }
        if (protectedSys) return true;
        for (MarketAPI m : colonies) if (CAThreat.isHigh(m)) return false;
        return true;
    }

    public static void run(CAData d, CASettings s, List<MarketAPI> auto) {
        int mode = CAPresets.tascMode(s);   // 0.3.0.0 (WS-F1 fix): the mode in effect; New colonies on Auto that asks first keeps it on Recommend only
        if (mode == 2 || !enabled() || !link() || Global.getSector() == null) return;
        deliver(d);
        if (auto.isEmpty() || CABudget.paused(s) || CAItems.overLimit() + d.expandOrders().size() >= 0 || !d.tascOrders().isEmpty()) return;   // one crew at a time, and room under the colony limit
        boolean mine = unlocked(MINE_ABILITY), siphon = unlocked(SIPHON_ABILITY);
        if (!mine && !siphon) return;
        boolean rich = CASurplus.rich(s);
        Map<StarSystemAPI, List<MarketAPI>> systems = new LinkedHashMap<StarSystemAPI, List<MarketAPI>>();
        for (MarketAPI m : auto) {
            StarSystemAPI sys = m.getStarSystem();
            if (sys == null || sys.hasTag("system_abyssal") || sys.getJumpPoints().isEmpty()) continue;
            List<MarketAPI> l = systems.get(sys);
            if (l == null) { l = new ArrayList<MarketAPI>(); systems.put(sys, l); }
            l.add(m);
        }
        Site best = null;
        for (Map.Entry<StarSystemAPI, List<MarketAPI>> e : systems.entrySet()) {
            if (!safe(e.getValue())) continue;
            List<Site> sites = new ArrayList<Site>();
            if (mine) { Site x = miningSite(e.getKey()); if (x != null) sites.add(x); }
            if (siphon) { Site x = siphonSite(e.getKey()); if (x != null) sites.add(x); }
            for (Site x : sites) if (pays(x.monthly, x.cost, rich) && (best == null || x.monthly > best.monthly)) best = x;
        }
        if (best == null) return;
        String what = (MINING.equals(best.kind) ? "mining station" : "siphon station") + " in " + best.sys.getBaseName();
        if (mode == 0) {
            if (CALog.once("tasc:station:" + best.sys.getId() + ":" + best.kind))
                CALog.add("Recommends a " + what + " (" + best.why + ", about " + Misc.getDGSCredits(best.monthly) + " a month once it grows; "
                        + Misc.getDGSCredits(best.cost) + "). " + (s.stationMode == 1 ? "Set the Expansion rung to On to have a crew build it"
                        : "Station construction, under Settings, set to Auto has a crew build it"));
            return;
        }
        float fee = CABudget.fee(s, BASE_FEE);
        if (!CAItems.canPay(s, best.cost + fee)) {
            if (CALog.once("tasc:station:budget:" + best.sys.getId())) CALog.add("A " + what + " waits for budget (" + Misc.getDGSCredits(best.cost + fee) + ")");
            return;
        }
        CABudget.pay(s, null, best.cost, fee, 0f);
        Order o = new Order();
        o.system = best.sys.getId(); o.kind = best.kind; o.x = best.x; o.y = best.y; o.paid = best.cost; o.what = what; o.why = best.why;
        o.gasGiantId = best.gasGiant == null ? null : best.gasGiant.getId();
        o.sent = Global.getSector().getClock().getTimestamp(); o.days = CREW_DAYS;
        d.tascOrders().add(o);
        CALog.add("Construction crew on its way to build a " + what + " (" + best.why + "), " + Math.round(CREW_DAYS) + " days, "
                + Misc.getDGSCredits(best.cost) + " + " + Misc.getDGSCredits(fee) + " fee. It becomes a new colony in Auto with the Money goal");
    }

    static void deliver(CAData d) {
        Iterator<Order> it = d.tascOrders().iterator();
        while (it.hasNext()) {
            Order o = it.next();
            if (Global.getSector().getClock().getElapsedDaysSince(o.sent) < o.days) continue;
            it.remove();
            StarSystemAPI sys = CAObjectives.system(o.system);
            MarketAPI made = null;
            try {
                if (sys != null && CAItems.overLimit() < 0) made = MINING.equals(o.kind) ? buildMining(sys, o) : buildSiphon(sys, o);
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "building a TASC " + o.kind + " station failed", t);
            }
            if (made == null) {
                CABudget.refund(CAData.settings(), null, o.paid);
                CALog.add("The " + o.what + " wasn't built (the site's gone, or you're at your colony limit); the " + Misc.getDGSCredits(o.paid) + " came back");
                continue;
            }
            CAData.Colony c = d.colony(made.getId());
            c.mode = CAData.Mode.AUTO;
            c.profile = CAData.Profile.INCOME;
            CALog.add("Built a " + o.what + ": " + made.getName() + " is a new colony, in Auto with the Money goal (" + o.why + ")");
        }
    }

    static MarketAPI buildMining(StarSystemAPI sys, Order o) throws Throwable {
        SectorEntityToken tk = token(sys, o.x, o.y);
        try {
            boolean belt = call(inBelt, tk), field = !belt && call(inField, tk);
            if ((!belt && !field) || call(tooClose, tk) || miningStationsIn(sys) >= setting("boggledMaxNumMiningStationsPerSystem", 1)) return null;
            com.fs.starfarer.api.campaign.CampaignClockAPI clock = Global.getSector().getClock();
            SectorEntityToken st = sys.addCustomEntity("boggled_mining_station" + clock.getCycle() + clock.getMonth() + clock.getDay(),
                    sys.getBaseName() + " Mining Station", "boggled_mining_station_small", Factions.PLAYER);
            if (belt) {
                SectorEntityToken focus = (SectorEntityToken) beltFocus.invoke(tk);
                float dist = Misc.getDistance(tk, focus);
                st.setCircularOrbitPointingDown(focus, Misc.getAngleInDegrees(focus.getLocation(), tk.getLocation()), dist, dist / 10f);
            } else {
                OrbitAPI orbit = (OrbitAPI) fieldOrbit.invoke(tk);
                if (orbit != null) {
                    SectorEntityToken f = orbit.getFocus();
                    st.setCircularOrbitWithSpin(f, Misc.getAngleInDegrees(f.getLocation(), tk.getLocation()), Misc.getDistance(tk, f), orbit.getOrbitalPeriod(), 5f, 10f);
                } else {
                    SectorEntityToken f = (SectorEntityToken) fieldEntity.invoke(tk);
                    st.setCircularOrbitWithSpin(f, Misc.getAngleInDegrees(f.getLocation(), tk.getLocation()), Misc.getDistance(tk, f), 40f, 5f, 10f);
                }
            }
            SectorEntityToken lights = sys.addCustomEntity("boggled_miningStationLights", "Mining Station Lights Overlay",
                    "boggled_mining_station_small_lights_overlay", Factions.PLAYER);
            lights.setOrbit(st.getOrbit().makeCopy());
            MarketAPI made = (MarketAPI) createMining.invoke(st);
            // as TASC does: an abandoned mining station here hands its storage over and goes
            CargoAPI store = made.getSubmarket("storage") == null ? null : made.getSubmarket("storage").getCargo();
            List<SectorEntityToken> old = new ArrayList<SectorEntityToken>();
            for (SectorEntityToken e : sys.getAllEntities()) if (e.hasTag("boggled_mining") && "neutral".equals(e.getFaction().getId())) old.add(e);
            for (SectorEntityToken e : old) {
                if (store != null && e.getMarket() != null && e.getMarket().getSubmarket("storage") != null) {
                    CargoAPI c = e.getMarket().getSubmarket("storage").getCargo();
                    if (!c.isEmpty()) store.addAll(c);
                }
                sys.removeEntity(e);
            }
            return made;
        } finally { sys.removeEntity(tk); }
    }

    static MarketAPI buildSiphon(StarSystemAPI sys, Order o) throws Throwable {
        SectorEntityToken gg = o.gasGiantId == null ? null : Global.getSector().getEntityById(o.gasGiantId);
        if (!(gg instanceof PlanetAPI)) return null;
        Object ability = siphonNew.invoke();
        if ((boolean) siphonHas.invoke(ability, gg) || siphonMoon.invoke(ability, gg) != null) return null;
        SectorEntityToken st = sys.addCustomEntity("boggled_siphon_station", gg.getName() + " Siphon Station", "boggled_siphon_station_small", Factions.PLAYER);
        float r = gg.getRadius() + 50f;
        st.setCircularOrbitPointingDown(gg, (float) (Math.random() * 360.0), r, r / 10f);
        SectorEntityToken lights = sys.addCustomEntity("boggled_siphonStationLights", "Siphon Station Lights Overlay",
                "boggled_siphon_station_small_lights_overlay", Factions.PLAYER);
        lights.setOrbit(st.getOrbit().makeCopy());
        return (MarketAPI) createSiphon.invoke(st, gg);
    }

    /** Crews on their way, for the panels. */
    public static List<String> inTransit(CAData d) {
        List<String> out = new ArrayList<String>();
        for (Order o : d.tascOrders()) {
            float left = Math.max(0f, o.days - Global.getSector().getClock().getElapsedDaysSince(o.sent));
            out.add(o.what + " (" + Math.round(left) + " days)");
        }
        return out;
    }
}
