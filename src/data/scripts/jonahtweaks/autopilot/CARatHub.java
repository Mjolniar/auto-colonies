package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.10.0: Random Assortment of Things' Expedition Hub (rat_expedition_hub, ExpeditionHubIndustry in RAT 3.4.1), valued
 * from the uninhabited planets its fleets can reach. Only the API and the industry's id are used, so no RAT class loads here.
 *
 * What RAT's code does (JTRatHubCheck reads these numbers from the mod's source):
 * - A fleet goes out every 2 to 5 days while fewer than clamp(size - 1, 3, 8) are out (+1 improved), and while the colony
 *   has its ships (clamp(size - 2, 1, 10)) and fuel (clamp(size - 3, 1, 10)) without a shortage. Every return or spawn
 *   starts the 2 to 5 days again.
 * - Each fleet picks an uninhabited planet (neutral, not a star or gas giant) in a system within 15 light-years (+10 with
 *   any AI core), weighted 0.5, +3 for two of farmland/ore/rare ore/organics (+5 for three or more; RAT's volatiles test
 *   never matches), +5 farmland, +7 ruins. Systems with a black hole, a pulsar or a Remnant base are left out while others
 *   are in range. It orbits 10 days and flies home.
 * - What it brings back counts for 90 days: farmland 2 to 5 food, ore and rare ore 1/1/2/2/3, organics and volatiles
 *   1/1/2/3, ruins metals 1/1/2/3 and heavy machinery 1/2/2/3. Each good is capped at clamp(size + 2, 0, 10).
 * - A return from a planet with ruins adds tech-mining finds to the monthly gathering point (RAT's code does this with or
 *   without a core): RAT's "basic" salvage worth 10,000 x (ruins factor + 0.2), plus random rolls (blueprints, cores,
 *   weapons) that aren't counted here.
 * - Alpha and Beta cores give the fleets +2 burn and the usual -25% upkeep. Gamma only adds the range. The hub never runs
 *   the game's supply and demand modifiers, so no core, improvement or administrator changes its output or demand.
 *
 * Auto Colonies' estimates: fleets fly at burn 7 (freighters and tankers, the slow end), a round trip is both legs through
 * the jump points plus the 10 days in orbit, and fleet throughput comes from a simulation of RAT's timer (rate()). The
 * expected supply per good is the mean of the capped sum over the expeditions back at a time (cappedMean), so values lean
 * low. Losses to raiders aren't counted.
 */
public final class CARatHub {
    private CARatHub() { }

    public static final String ID = "rat_expedition_hub", MOD = "assortment_of_things",
            PLUGIN = "assortment_of_things.relics.industry.ExpeditionHubIndustry";

    // ---------------- RAT 3.4.1's numbers (ExpeditionHubIndustry) ----------------
    static final float BASE_RANGE = 15f, CORE_RANGE = 10f, CORE_BURN = 2f, LASTING_DAYS = 90f, ORBIT_DAYS = 10f;
    static final float SPAWN_MIN = 2f, SPAWN_MAX = 5f;
    static final int FLEETS_MIN = 3, FLEETS_MAX = 8, CAP_MAX = 10;
    static final float TECH_VALUE = 10000f, TECH_ADD = 0.2f;

    // ---------------- Auto Colonies' estimates ----------------
    /** Expedition fleets: 40% combat, 50% freighters, 10% tankers (burn 7 to 9), taken at the slow end. */
    static final float FLEET_BURN = 7f;
    /** In-system leg from a planet to its system's jump point when the system has none listed (map units). */
    static final float SYSTEM_UNITS = 4000f;
    /** rate(): the most fleets RAT's timer sends, about one every 5 days once returns keep resetting it (simulated). */
    static final float SATURATED_DAYS = 5f;
    /** rate(): a day lost per cycle to returns that reset the timer while a fleet waits (simulated). */
    static final float SPAWN_OVERHEAD = 1f;
    /** Tech-mining finds are counted at half their value: what selling them gets. */
    static final float TECH_SALE = 0.5f;

    static final String[] GOODS = {"food", "ore", "rare_ore", "organics", "volatiles", "metals", "heavy_machinery"};
    static final int FOOD = 0, ORE = 1, RARE = 2, ORG = 3, VOL = 4, METALS = 5, HM = 6;

    /** Condition -> units per good it brings back (refreshResourceState). */
    static final Map<String, int[]> YIELD = new HashMap<String, int[]>();
    /** Ruins -> tech-mining factor (generateCargoForGatheringPoint, before + 0.2). */
    static final Map<String, Float> RUINS = new HashMap<String, Float>();
    static {
        y("farmland_poor", FOOD, 2); y("farmland_adequate", FOOD, 3); y("farmland_rich", FOOD, 4); y("farmland_bountiful", FOOD, 5);
        String[] ore = {"sparse", "moderate", "abundant", "rich", "ultrarich"};
        int[] oreU = {1, 1, 2, 2, 3};
        for (int i = 0; i < ore.length; i++) { y("ore_" + ore[i], ORE, oreU[i]); y("rare_ore_" + ore[i], RARE, oreU[i]); }
        y("organics_trace", ORG, 1); y("organics_common", ORG, 1); y("organics_abundant", ORG, 2); y("organics_plentiful", ORG, 3);
        y("volatiles_trace", VOL, 1); y("volatiles_diffuse", VOL, 1); y("volatiles_abundant", VOL, 2); y("volatiles_plentiful", VOL, 3);
        ruins("ruins_scattered", 1, 1, 0.5f); ruins("ruins_widespread", 1, 2, 0.75f);
        ruins("ruins_extensive", 2, 2, 0.8f); ruins("ruins_vast", 3, 3, 1f);
    }

    static void y(String cond, int good, int units) { int[] u = new int[GOODS.length]; u[good] = units; YIELD.put(cond, u); }

    static void ruins(String cond, int metals, int hm, float tech) {
        int[] u = new int[GOODS.length]; u[METALS] = metals; u[HM] = hm;
        YIELD.put(cond, u); RUINS.put(cond, tech);
    }

    // ---------------- the model (pure: JTRatHubCheck) ----------------

    /** One uninhabited planet a fleet can be sent to. */
    static final class Target {
        /** One way, in map units: the in-system legs plus the hyperspace leg. */
        float units;
        /** RAT's pick weight. */
        float weight;
        final int[] yield = new int[GOODS.length];
        /** Tech-mining factor (ruins + 0.2), 0 without ruins. */
        float tech;
    }

    /** One star system in hyperspace reach, with its planets a fleet can be sent to (maybe none). */
    static final class Sys {
        float ly;
        boolean blackHole, pulsar, remnant;
        final List<Target> planets = new ArrayList<Target>();
    }

    /** A planet with these conditions, `units` away one way. Pure. */
    static Target target(float units, Collection<String> conditions) {
        Target t = new Target();
        t.units = units;
        int count = 0;
        boolean farm = false, ruins = false;
        for (String c : conditions) {
            int[] u = YIELD.get(c);
            if (u != null) for (int g = 0; g < GOODS.length; g++) t.yield[g] += u[g];
            if (c.startsWith("farmland_") && YIELD.containsKey(c)) farm = true;
            Float r = RUINS.get(c);
            if (r != null) { ruins = true; t.tech += r; }
            // RAT counts farmland, ore, rare ore and organics; its volatiles test reads the industry's id, so it never matches
            if (u != null && r == null && u[VOL] == 0) count++;
        }
        if (ruins) t.tech += TECH_ADD;
        t.weight = 0.5f + (count == 2 ? 3f : count >= 3 ? 5f : 0f) + (farm ? 5f : 0f) + (ruins ? 7f : 0f);
        return t;
    }

    /**
     * The planets RAT's fleets pick from at this range (findSuitablePlanet): systems closer than the range; without a black
     * hole, a pulsar or a Remnant base when there are any such systems, else without a black hole or pulsar, else all of
     * them; then those systems' planets. Pure.
     */
    static List<Target> reach(List<Sys> systems, float range) {
        List<Sys> in = new ArrayList<Sys>();
        for (Sys s : systems) if (s.ly < range) in.add(s);
        List<Sys> use = new ArrayList<Sys>();
        for (Sys s : in) if (!s.blackHole && !s.pulsar && !s.remnant) use.add(s);
        if (use.isEmpty()) for (Sys s : in) if (!s.blackHole && !s.pulsar) use.add(s);
        if (use.isEmpty()) use.addAll(in);
        List<Target> out = new ArrayList<Target>();
        for (Sys s : use) out.addAll(s.planets);
        return out;
    }

    /**
     * Expeditions a day for this many fleets and this mean round trip (days). Pure. A soft minimum of the fleet limit
     * (fleets per round trip plus the 2 to 5 day wait and a day of resets) and RAT's timer at saturation (one every 5 days);
     * within about 8% of a simulation of RAT's spawn rules, on the low side.
     */
    static float rate(int fleets, float trip) {
        if (fleets <= 0 || trip <= 0f) return 0f;
        double x = (trip + (SPAWN_MIN + SPAWN_MAX) / 2f + SPAWN_OVERHEAD) / fleets;
        return (float) (1.0 / Math.cbrt(x * x * x + SATURATED_DAYS * SATURATED_DAYS * SATURATED_DAYS));
    }

    /**
     * The mean of min(cap, sum of n picks), each pick's units drawn from one[] (one[u] = chance of u units, u up to cap).
     * Fractional n mixes the two whole counts around it. Pure.
     */
    static float cappedMean(double[] one, double n, int cap) {
        if (cap <= 0 || n <= 0) return 0f;
        int whole = (int) Math.floor(n);
        double frac = n - whole;
        double[] d = new double[cap + 1];
        d[0] = 1;
        for (int k = 0; k < whole; k++) d = add(d, one, cap);
        double a = mean(d), b = frac > 0 ? mean(add(d, one, cap)) : a;
        return (float) ((1 - frac) * a + frac * b);
    }

    static double[] add(double[] d, double[] one, int cap) {
        double[] out = new double[cap + 1];
        for (int s = 0; s <= cap; s++) {
            if (d[s] == 0) continue;
            for (int u = 0; u < one.length; u++) if (one[u] != 0) out[Math.min(cap, s + u)] += d[s] * one[u];
        }
        return out;
    }

    static double mean(double[] d) { double m = 0; for (int s = 0; s < d.length; s++) m += s * d[s]; return m; }

    /** What a hub brings back, in steady state. */
    static final class Out {
        /** Expected supply per good (GOODS order), RAT's cap applied. */
        final float[] units = new float[GOODS.length];
        /** Tech-mining finds a month, credits (counted at half). */
        float tech;
        /** Expeditions a day, expeditions back at a time, mean round trip (days), planets in reach. */
        float perDay, active, trip;
        int targets;
    }

    /**
     * The model: the planets in reach at this range, the mean round trip at this burn (unitsPerDay: map units a day at that
     * burn), expeditions a day for the fleet limit, 90 days of them back at a time, and per good the mean capped supply. Pure.
     */
    static Out model(List<Sys> systems, float range, float unitsPerDay, int fleets, int cap) {
        Out o = new Out();
        List<Target> ts = reach(systems, range);
        o.targets = ts.size();
        if (ts.isEmpty() || unitsPerDay <= 0f) return o;
        double total = 0;
        for (Target t : ts) total += t.weight;
        if (total <= 0) return o;
        double trip = 0, tech = 0;
        for (Target t : ts) {
            double p = t.weight / total;
            trip += p * (2f * t.units / unitsPerDay + ORBIT_DAYS);
            tech += p * t.tech;
        }
        o.trip = (float) trip;
        o.perDay = rate(fleets, o.trip);
        o.active = o.perDay * LASTING_DAYS;
        for (int g = 0; g < GOODS.length; g++) {
            double[] one = new double[cap + 1];
            for (Target t : ts) one[Math.min(cap, t.yield[g])] += t.weight / total;
            o.units[g] = one[0] >= 1.0 - 1e-9 ? 0f : cappedMean(one, o.active, cap);
        }
        o.tech = (float) (o.perDay * 30.0 * tech * TECH_VALUE * TECH_SALE);
        return o;
    }

    /** RAT's fleet limit: clamp(size - 1, 3, 8), +1 improved. Pure. */
    static int fleets(int size, boolean improved) { return Math.max(FLEETS_MIN, Math.min(FLEETS_MAX, size - 1)) + (improved ? 1 : 0); }

    /** RAT's cap on each good: clamp(size + 2, 0, 10). Pure. */
    static int cap(int size) { return Math.max(0, Math.min(CAP_MAX, size + 2)); }

    /** Ships and fuel the hub needs: clamp(size - 2, 1, 10) and clamp(size - 3, 1, 10). Pure. */
    static int ships(int size) { return Math.max(1, Math.min(10, size - 2)); }
    static int fuel(int size) { return Math.max(1, Math.min(10, size - 3)); }

    /** Range with this AI core (getExpeditionRange: +10 for Gamma, Beta or Alpha). Pure. */
    static float range(String core) {
        return "alpha_core".equals(core) || "beta_core".equals(core) || "gamma_core".equals(core) ? BASE_RANGE + CORE_RANGE : BASE_RANGE;
    }

    /** Fleet burn with this AI core (spawnFleet: +2 for Alpha or Beta). Pure. */
    static float burn(String core) { return "alpha_core".equals(core) || "beta_core".equals(core) ? FLEET_BURN + CORE_BURN : FLEET_BURN; }

    // ---------------- the game ----------------

    static Boolean ratHub;

    /** RAT is on and the Expedition Hub is still its ExpeditionHubIndustry (read once; mods don't change while the game runs). */
    static boolean on() {
        Boolean b = ratHub;
        if (b != null) return b;
        try {
            b = Global.getSettings().getModManager().isModEnabled(MOD) && PLUGIN.equals(Global.getSettings().getIndustrySpec(ID).getPluginClass());
        } catch (Throwable t) { b = false; }
        ratHub = b;
        return b;
    }

    public static boolean has(String id) { return ID.equals(id) && on(); }

    public static boolean is(Industry ind) { return ind != null && ID.equals(ind.getId()) && on(); }

    /** The colony's reach out to 25 light-years, kept for the day (planets only change hands when someone settles). */
    static final CACache.Memo<List<Sys>> REACH = new CACache.Memo<List<Sys>>("expedition hub reach", 100);

    static List<Sys> systems(MarketAPI m) {
        int day = CAEngine.today(), ep = CACache.epoch();
        List<Sys> kept = day >= 0 ? REACH.get(m.getId(), day, 0L) : null;
        if (kept != null) return kept;
        List<Sys> out = scan(m);
        if (day >= 0) REACH.put(m.getId(), day, ep, 0L, out);
        return out;
    }

    static List<Sys> scan(MarketAPI m) {
        List<Sys> out = new ArrayList<Sys>();
        org.lwjgl.util.vector.Vector2f at = m.getLocationInHyperspace();
        if (at == null || Global.getSector() == null) return out;
        StarSystemAPI home = m.getStarSystem();
        SectorEntityToken from = m.getPrimaryEntity();
        float homeLeg = home == null || from == null ? 0f : jumpLeg(home, from);
        float max = BASE_RANGE + CORE_RANGE, perLy = Misc.getUnitsPerLightYear();
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            float ly = Misc.getDistanceLY(at, s.getLocation());
            if (!(ly < max) || s.hasTag(Tags.THEME_HIDDEN) || s.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            Sys y = new Sys();
            y.ly = ly; y.blackHole = s.hasBlackHole(); y.pulsar = s.hasPulsar(); y.remnant = s.hasTag(Tags.THEME_REMNANT_MAIN);
            for (PlanetAPI pl : s.getPlanets()) {
                if (pl.isStar() || pl.isGasGiant() || pl.getFaction() == null || !Factions.NEUTRAL.equals(pl.getFaction().getId())) continue;
                MarketAPI pm = pl.getMarket();
                if (pm == null || pm.getConditions() == null) continue;
                List<String> ids = new ArrayList<String>();
                for (MarketConditionAPI c : pm.getConditions()) ids.add(c.getId());
                float units = s == home && from != null ? Misc.getDistance(from, pl) : homeLeg + ly * perLy + jumpLeg(s, pl);
                y.planets.add(target(units, ids));
            }
            out.add(y);
        }
        return out;
    }

    /** Map units from an entity to its system's nearest jump point. */
    static float jumpLeg(StarSystemAPI s, SectorEntityToken e) {
        float best = Float.MAX_VALUE;
        try { for (SectorEntityToken jp : s.getJumpPoints()) best = Math.min(best, Misc.getDistance(e, jp)); } catch (Throwable ignored) { }
        return best == Float.MAX_VALUE ? SYSTEM_UNITS : best;
    }

    /** Map units a day at this burn (Misc.getSpeedForBurnLevel, 10 seconds a day). */
    static float unitsPerDay(float burn) {
        float spd;
        try { spd = Misc.getSpeedForBurnLevel(burn); } catch (Throwable t) { spd = 20f * burn; }
        float sec = 10f;
        try { sec = Global.getSector().getClock().getSecondsPerDay(); } catch (Throwable ignored) { }
        return spd * sec;
    }

    /** What a hub at m brings back with this core. */
    static Out output(MarketAPI m, String core, boolean improved) {
        return model(systems(m), range(core), unitsPerDay(burn(core)), fleets(m.getSize(), improved), cap(m.getSize()));
    }

    /**
     * Whether the hub can't send fleets: the colony's ships or fuel are short (RAT checks getMaxDeficit before each fleet).
     * A good the colony already needs is read there; otherwise whether your colonies lack it.
     */
    static boolean stalled(MarketAPI m, Map<String, Integer> deficit) {
        return shortOf(m, "ships", deficit) || shortOf(m, "fuel", deficit);
    }

    static boolean shortOf(MarketAPI m, String com, Map<String, Integer> deficit) {
        try {
            CommodityOnMarketAPI c = m.getCommodityData(com);
            if (c != null && c.getMaxDemand() > 0) return c.getDeficitQuantity() > 0;
        } catch (Throwable ignored) { }
        return CAValue.lack(deficit, com) > 0;
    }

    /**
     * The engine's estimate (CAMods.apply): its expected supply lines, tech-mining finds and the ships and fuel it needs. A new
     * hub needs a planet in reach, and while there are fewer Expedition Hub BPs than colonies that could use one, it only
     * goes to the colonies where it brings back the most (sites). A hub that's there is read with its own core and improvement.
     */
    public static boolean apply(MarketAPI m, CAEngine.Pick p, Map<String, Integer> deficit) {
        Industry here = m.getIndustry(ID);
        boolean built = here != null;
        if (!built && !site(m)) return false;
        Out o = output(m, built ? here.getAICoreId() : null, built && here.isImproved());
        if (o.targets == 0 && !built) return false;
        int s = m.getSize();
        float range = range(built ? here.getAICoreId() : null);
        if (stalled(m, deficit)) {
            p.lines.add("Expeditions: ships or fuel are short here, and it doesn't send fleets out while they are, so it's counted as making nothing");
        } else {
            boolean nets = CAEngine.callerNets();
            // the market model gives a new line the administrator's supply bonus; the hub never gets it (it skips the game's
            // supply modifiers), so that's taken off what's passed
            int admin = CAEngine.supplierAt != null && CAMarket.on() ? CAMods.adminBonus(m) : 0;
            for (int g = 0; g < GOODS.length; g++) {
                int u = Math.round(o.units[g]);
                if (u <= 0) continue;
                if (nets) u = CAMods.above(u, CAMods.otherSupply(m, ID, GOODS[g]));
                u -= admin;
                if (u > 0) CAEngine.supply(p, GOODS[g], u, deficit);
            }
            if (admin > 0) p.lines.add("Your administrator's supply bonus doesn't apply to the hub's output");
            if (o.tech > 1f) {
                p.fx[0] += o.tech;
                p.lines.add("Tech-mining on ruins: about " + Misc.getDGSCredits(o.tech) + " per month in salvage, counted at half since you'd sell it (rare finds aren't counted)");
            }
        }
        CAEngine.demand(p, "ships", ships(s), deficit);
        CAEngine.demand(p, "fuel", fuel(s), deficit);
        p.lines.add(o.targets == 0 ? "Expeditions: there's no uninhabited planet it can reach within " + Math.round(range) + " light-years"
                : "Expeditions: it can reach " + o.targets + " uninhabited planet" + (o.targets == 1 ? "" : "s") + " within " + Math.round(range)
                  + " light-years, and about " + Math.round(o.active) + " expeditions are back at any time (a round trip takes about " + Math.round(o.trip) + " days)");
        return true;
    }

    /**
     * Credits a month AI core k (0 Alpha, 1 Beta, 2 Gamma) adds to a hub over no core: 10 more light-years of reach (any
     * core) and +2 burn (Alpha, Beta). More reach can lower the output when it adds far, weak planets to the picks, so this
     * can be below 0. The -25% upkeep is counted by the caller (CAItems.coreValue); no core changes the hub's demand.
     */
    public static float coreGain(MarketAPI m, Industry ind, int k, Map<String, Integer> deficit) {
        if (stalled(m, deficit)) return 0f;
        String core = k == 0 ? "alpha_core" : k == 1 ? "beta_core" : "gamma_core";
        boolean imp = ind.isImproved();
        Out without = output(m, null, imp), with = output(m, core, imp);
        float v = with.tech - without.tech;
        for (int g = 0; g < GOODS.length; g++) {
            int a = Math.round(without.units[g]), b = Math.round(with.units[g]);
            if (a != b) v += CAValue.boostLine(m, ind, a, GOODS[g], b - a, deficit);
        }
        return v;
    }

    // ---------------- where a BP goes ----------------

    /** Colonies a new hub may go to today: null when every colony that could build one has a BP for it. */
    static final CACache.Memo<String[]> SITES = new CACache.Memo<String[]>("expedition hub sites", 4);
    static final String[] ANY = new String[0];

    static boolean site(MarketAPI m) {
        String[] best = sites();
        if (best == ANY) return true;
        for (String id : best) if (id.equals(m.getId())) return true;
        return false;
    }

    static String[] sites() {
        int day = CAEngine.today(), ep = CACache.epoch();
        String[] kept = day >= 0 ? SITES.get("sites", day, 0L) : null;
        if (kept != null) return kept;
        String[] out = ANY;
        try { out = pickSites(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Expedition Hub site check failed", t); }
        if (day >= 0) SITES.put("sites", day, ep, 0L, out);
        return out;
    }

    /** Colonies set to Auto or Approve with a free industry slot whose build menu offers the hub, best first, as many as there are BPs. */
    static String[] pickSites() {
        IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(ID);
        final Map<String, Float> worth = new HashMap<String, Float>();
        List<String> cand = new ArrayList<String>();
        for (MarketAPI c : Misc.getPlayerMarkets(false)) {
            if (CAEngine.isOutpost(c) || c.hasIndustry(ID) || !CAEngine.planned(c) || CABuild.freeSlots(c) <= 0) continue;
            if (!CARatForge.available(c, spec.getNewPluginInstance(c), spec)) continue;
            cand.add(c.getId());
            worth.put(c.getId(), rawWorth(output(c, null, false)));
        }
        int n = bps();
        if (cand.size() <= n) return ANY;
        Collections.sort(cand, new Comparator<String>() {
            public int compare(String a, String b) { return Float.compare(worth.get(b), worth.get(a)); }
        });
        return cand.subList(0, Math.max(0, n)).toArray(new String[0]);
    }

    /** A site's raw worth for ranking: expected units at base price, plus tech-mining finds. Pure. */
    static float rawWorth(Out o) {
        float v = o.tech;
        for (int g = 0; g < GOODS.length; g++) {
            float price = 0f;
            try { price = Global.getSettings().getCommoditySpec(GOODS[g]).getBasePrice(); } catch (Throwable ignored) { }
            v += o.units[g] * price;
        }
        return v;
    }

    /** Expedition Hub BPs you hold: the fleet's cargo, every colony's Storage and every Autopilot Storage. */
    static int bps() {
        float n = 0f;
        try { n += count(Global.getSector().getPlayerFleet().getCargo()); } catch (Throwable ignored) { }
        for (MarketAPI c : Misc.getPlayerMarkets(false)) try { n += count(Misc.getStorageCargo(c)); } catch (Throwable ignored) { }
        try { for (CAStores.Store st : CAStores.all()) n += count(st.cargo); } catch (Throwable ignored) { }
        return Math.round(n);
    }

    static float count(CargoAPI c) { return c == null ? 0f : c.getQuantity(CargoAPI.CargoItemType.SPECIAL, CARatForge.item(ID)); }

    // ---------------- Diagnostics ----------------

    /** Where a Forge-Assisted BP for this industry comes from in RAT, or null for one that doesn't need one. */
    static String bpSource(String id) {
        if (ID.equals(id)) return "The Expedition Hub's comes from a Damaged Cryosleeper";
        if ("rat_asteroid_mining".equals(id)) return "Advanced Asteroid Mining's is sold at the Exotech shop";
        return "";
    }

    /** Why a Forge-Assisted building is buildable nowhere (RAT's BaseConsumeableIndustry.isAvailableToBuild). */
    static String bpReason(String id) {
        String src = bpSource(id);
        return "it needs a Forge-Assisted Industry BP for it in your cargo, the colony's Storage or Autopilot Storage, and building it uses the BP up (taking it down gives it back)"
                + (src.isEmpty() ? "" : ". " + src);
    }

    /** The hub's Diagnostics line: how it's valued, where it can go, and each colony's reach. */
    static String diagnostic(IndustrySpecAPI spec) {
        StringBuilder sb = new StringBuilder("Valued from the uninhabited planets its fleets can reach from each colony (Auto Colonies' expedition model); cost ")
                .append(Misc.getDGSCredits(spec.getCost())).append(", ").append((int) spec.getBuildTime()).append(" days");
        List<String> buildable = new ArrayList<String>(), reach = new ArrayList<String>();
        if (Global.getSector() != null) {
            for (MarketAPI m : Misc.getPlayerMarkets(false)) {
                if (CAEngine.isOutpost(m)) continue;
                try {
                    if (CARatForge.available(m, spec.getNewPluginInstance(m), spec)) buildable.add(m.getName());
                    reach.add(m.getName() + " " + output(m, null, false).targets);
                } catch (Throwable t) { reach.add(m.getName() + " ?"); }
            }
        }
        sb.append(buildable.isEmpty() ? "; buildable at none of your colonies: " + bpReason(spec.getId()) : "; buildable at " + buildable);
        if (!reach.isEmpty()) sb.append("; planets in reach (15 light-years): ").append(CAContracts.join(reach));
        return sb.toString();
    }
}
