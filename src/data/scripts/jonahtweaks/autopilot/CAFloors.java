package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * 0.3.2.0 (production priorities, plan 2026-11-12, M2 and floors 1 to 9): the empire's production floors.
 * - Floors: 1 hull source, 2 hull capacity (vanilla custom production), 3 order goods (AoTD production orders), 4 production
 *   quality, 5 weapons, 6 Domain-grade Parts, 7 supplies, 8 fuel, 9 producer inputs.
 * - Measure: once a day and again after a settings change or a build (CACache epoch), only in a running game (armed by the
 *   daily pass). Offline checks build a State by hand; with no State every rule here is off.
 * - Value: an open floor adds credits a month to a pick, only for the part of the gap it closes (CAValue.add). A met floor adds
 *   nothing. Floors apply at every goal but Money (CASettings.floorMoneyGoal), never in fast buildout.
 * - Steps: an open floor gets its producer as a plan step at the best Approve or Auto colony (CAPlan.build), so research
 *   values the techs that open it.
 * - Make room: with no free slot, a floor producer may replace the weakest income-only industry (CASettings.makeRoom).
 * - Off (CASettings.productionMode): nothing here runs. The 0.3.1.0 picks are kept exactly.
 * Pure parts are checked by JTFloorsCheck, JTValueCheck and JTPlannerSimCheck.
 */
public class CAFloors {
    private CAFloors() { }

    // ---------------- ids ----------------

    public static final int HULLS = 0, CAPACITY = 1, ORDERS = 2, QUALITY = 3, WEAPONS = 4, PARTS = 5, SUPPLIES = 6, FUEL = 7, INPUTS = 8;
    public static final String[] NAMES = {"hull source", "hull capacity", "order goods", "production quality", "weapons",
            "Domain-grade Parts", "supplies", "fuel", "producer inputs"};
    /** Buildings that make ships (floor 1 and 3 producers). Any industry with a ships line counts too. */
    static final Set<String> SHIP_PRODUCERS = new HashSet<String>(Arrays.asList("heavyindustry", "orbitalworks", "aotd_civilian_heavy_prod",
            "triheavy", "hegeheavy", "yunru_heavyindustry"));
    /** AoTD order goods (floor 3). */
    static final String[] ORDER_GOODS = {"ships", "hand_weapons", "advanced_components"};
    /** Producer inputs (floor 9). */
    static final String[] INPUT_GOODS = {"metals", "rare_metals", "heavy_machinery"};
    /** Ship consumers and the ships they take a month (their demand counts against floor 3). */
    static final Map<String, Integer> SHIP_CONSUMERS = new LinkedHashMap<String, Integer>();
    static {
        SHIP_CONSUMERS.put("JYD_scrap", 6);
        SHIP_CONSUMERS.put("JYD_forge", 5);
        SHIP_CONSUMERS.put("yunru_shipbays", 4);
        SHIP_CONSUMERS.put("rat_expedition_hub", 4);
        SHIP_CONSUMERS.put("roider_shipworks", 3);
    }
    /** IndEvo Restoration Docks: removes d-mods from ships in its storage, up to the colony's size a month. */
    static final String DRY_DOCK = "IndEvo_dryDock";
    /** Defaults when the game has no setting: minDModRepairCostInParts, defaultDModRepairPartsBaseValueDivisor, qualityPerDMod. */
    static final int[] MIN_PARTS = {1, 2, 4, 8};
    static final float PARTS_DIVISOR = 15000f, QUALITY_PER_DMOD = 0.2f, DOCTRINE_PER_POINT = 0.125f;

    // ---------------- the state ----------------

    /** What the floors are today, and the settings they were measured with. */
    public static final class State {
        public int day = Integer.MIN_VALUE, epoch = Integer.MIN_VALUE;
        // settings
        public int mode = CATuning.PROD_LIGHT;
        public boolean newWeights = true, needsFirst = true, moneyGoal = false;
        public int makeRoom = CATuning.MAKE_ROOM_ASK;
        public boolean keepGoods = true;
        public float[] taper;
        public float taperNet;
        public int divCopies = CATuning.DIVERSITY_COPIES;
        public float divShare = CATuning.DIVERSITY_SHARE;
        public float valHull = CATuning.FLOOR_VAL_HULL, valCapacity = CATuning.FLOOR_VAL_CAPACITY, valQuality = CATuning.FLOOR_VAL_QUALITY,
                valPart = CATuning.FLOOR_VAL_PART, valOrders = CATuning.FLOOR_VAL_ORDERS, valGoods = CATuning.FLOOR_VAL_GOODS;
        public float unitValue = CATuning.UNIT_VALUE_DEFAULT;
        // floor 1
        public int hullSources, hullWant;
        public final Set<String> hullColonies = new HashSet<String>();
        // floor 2
        public boolean capacityOn = true;
        public float capacity, capacityWant, capacityPerUnit = CATuning.FLOOR_CAPACITY_PER_UNIT;
        // floor 3
        public boolean ordersOn;
        public final Map<String, Float> orderNeed = new LinkedHashMap<String, Float>(), orderMade = new LinkedHashMap<String, Float>();
        // floor 4
        public float qualityWant, bestQ = Float.NaN, secondQ = Float.NaN, qualityOffset;
        public String bestId;
        public final Map<String, Float> colonyQ = new HashMap<String, Float>();
        // floor 5, 7, 8, 9: units short a month
        public int weaponsGap, suppliesGap, fuelGap;
        public final Map<String, Integer> inputGap = new LinkedHashMap<String, Integer>();
        // floor 6
        public boolean partsOn;
        public int partsStock, partsLocal, partsColonies, partsNeed, partsReserve, fleetDmods;
        public boolean partsSource;
        // steps planned (colony id -> producer ids) and their text for the panel and the Shipyard note
        public final Map<String, List<String[]>> steps = new LinkedHashMap<String, List<String[]>>();

        public boolean on() { return mode == CATuning.PROD_LIGHT || mode == CATuning.PROD_FULL; }
        public float quality() { return Float.isNaN(bestQ) ? qualityOffset : bestQ + qualityOffset; }
        public float capacityGap() { return capacityOn ? Math.max(0f, capacityWant - capacity) : 0f; }
        public float qualityGap() { return Math.max(0f, qualityWant - quality()); }
        public int hullGap() { return Math.max(0, hullWant - hullSources); }
        public int partsWant() { return partsNeed + partsReserve; }
        public int partsGap() { return partsOn ? Math.max(0, partsWant() - partsStock) : 0; }
        public float orderGap(String g) {
            if (!ordersOn) return 0f;
            Float n = orderNeed.get(g), m = orderMade.get(g);
            return Math.max(0f, (n == null ? 0f : n) - (m == null ? 0f : m));
        }
        /** Any floor open. */
        public boolean open() {
            if (hullGap() > 0 || capacityGap() > 0f || qualityGap() > 0.001f || partsGap() > 0 || weaponsGap > 0 || suppliesGap > 0 || fuelGap > 0) return true;
            for (String g : ORDER_GOODS) if (orderGap(g) > 0f) return true;
            for (Integer v : inputGap.values()) if (v != null && v > 0) return true;
            return false;
        }
    }

    /** The state in use (null: floors off, offline, or not measured yet this session). */
    static State current;
    /** Set by the daily pass: a running game, so state() may measure. Offline checks never set it. */
    static boolean armed = false;

    public static void reset() {
        current = null; armed = false; measuring = false; triedDay = Integer.MIN_VALUE; triedEpoch = Integer.MIN_VALUE;
        STEPS.clear(); fleetAt = Float.NaN; fleetNeed = 0; fleetDmodCount = 0; adviceKey = null;
        families = null; familiesDay = Integer.MIN_VALUE; familiesSig = 0L;
    }

    /** The floors today: measured when the day, the epoch or nothing kept says so. Null when production is Off or offline. */
    public static State state() {
        if (!armed) return current;
        int day = CAEngine.today(), ep = CACache.epoch();
        if (day == triedDay && ep == triedEpoch) return current;   // measured (or failed, or Off) for this day and epoch
        if (measuring) return current;
        triedDay = day; triedEpoch = ep;
        measuring = true;
        try {
            CASettings s = CAData.settings();
            current = s.productionOn() ? measure(s) : null;
            if (current != null) { current.day = day; current.epoch = ep; }
        } catch (Throwable t) {
            CAStep.warn("production floors", "production floor measure failed", t);
        } finally { measuring = false; }
        return current;
    }

    static boolean measuring = false;
    /** The day and epoch of the last measure (one a day and epoch, also when it failed or production is Off). */
    static int triedDay = Integer.MIN_VALUE, triedEpoch = Integer.MIN_VALUE;

    /** Production rules on now (a State exists and its mode is Light or Full). */
    public static boolean on() { State st = state(); return st != null && st.on(); }

    /** Floor actions rank first in the planner. */
    public static boolean needsFirst() { State st = state(); return st != null && st.on() && st.needsFirst; }

    // ---------------- pure rules (JTFloorsCheck) ----------------

    /** Pure: custom production quality by CoreScript: the best market's production plus fleet quality, less the doctrine's share, plus 4 doctrine points. */
    static float quality(float bestMarket, float doctrineContribution, float perPoint) { return bestMarket - doctrineContribution + 4f * perPoint; }

    /** Pure: average d-mods on a produced hull at this quality (DefaultFleetInflater: (1 - quality) / qualityPerDMod, never under 0). */
    static float avgDmods(float quality, float perDmod) { return Math.max(0f, (1f - quality) / (perDmod > 0f ? perDmod : QUALITY_PER_DMOD)); }

    /** Pure: parts to repair one d-mod on a hull: base value / divisor, at least the size's minimum (0 frigate .. 3 capital). */
    static int partsPerDmod(float baseValue, int size, int[] mins, float divisor) {
        int[] mn = mins == null || mins.length < 4 ? MIN_PARTS : mins;
        int s = Math.max(0, Math.min(3, size));
        float d = divisor > 0f ? divisor : PARTS_DIVISOR;
        return Math.max(mn[s], (int) Math.ceil(Math.max(0f, baseValue) / d - 1e-4f));
    }

    /** Pure: the parts a fleet needs to repair every d-mod: per ship, d-mods x partsPerDmod. */
    static int partsNeed(int[] dmods, float[] baseValues, int[] sizes, int[] mins, float divisor) {
        int n = 0;
        for (int i = 0; i < dmods.length; i++) if (dmods[i] > 0) n += dmods[i] * partsPerDmod(baseValues[i], sizes[i], mins, divisor);
        return n;
    }

    /** Pure: an AoTD order's remaining goods as a monthly need over `months`. */
    static float monthly(int remaining, int months) { return remaining <= 0 ? 0f : remaining / (float) Math.max(1, months); }

    /** Pure: the part of `add` that closes `gap` (never below 0). */
    static float capped(float add, float gap) { return Math.max(0f, Math.min(add, gap)); }

    /** Pure: a new hull source while fewer than wanted: the hull value, else 0. */
    static float hullValue(boolean newSource, int have, int want, float val) { return newSource && have < want ? val : 0f; }

    /** Pure: capacity a pick adds (units x capacity per unit), capped at the gap, at the value per credit. */
    static float capacityValue(float units, float perUnit, float gap, float val) { return capped(units * perUnit, gap) * val; }

    /** Pure: what a change of empire quality from before to after does to the quality floor: closing the gap is positive, opening it negative. */
    static float qualityValue(float before, float after, float want, float val) {
        return (Math.max(0f, want - before) - Math.max(0f, want - after)) * val;
    }

    /** Pure: empire quality after a colony's own quality changes by add (top two colonies known; offset: doctrine and points). */
    static float qualityAfter(String colony, float colonyQ, float add, String bestId, float bestQ, float secondQ, float offset) {
        float others = colony != null && colony.equals(bestId) ? (Float.isNaN(secondQ) ? Float.NEGATIVE_INFINITY : secondQ)
                : (Float.isNaN(bestQ) ? Float.NEGATIVE_INFINITY : bestQ);
        return Math.max(others, colonyQ + add) + offset;
    }

    /** Pure: goods closing a gap, at base price x unit value x factor. */
    static float goodsValue(float added, float gap, float base, float uv, float factor) { return capped(added, gap) * base * uv * factor; }

    /** Pure: the extra shortfall a ship consumer opens on an order gap: gap after its demand less gap before, as a loss. */
    static float consumerLoss(float need, float made, float consumed, float base, float uv, float factor) {
        float before = Math.max(0f, need - made), after = Math.max(0f, need - (made - Math.max(0f, consumed)));
        return -(after - before) * base * uv * factor;
    }

    /** Pure: floors count at this goal: every goal but Money (unless set), never in fast buildout. */
    static boolean applies(CAData.Profile p, boolean moneyGoal, boolean profitOnly) {
        if (profitOnly) return false;
        return p != CAData.Profile.INCOME || moneyGoal;
    }

    /** Pure: what a goal multiplies floor values by (Self-sufficiency counts them double). */
    static float goalMult(CAData.Profile p) { return p == CAData.Profile.SELF ? CATuning.FLOOR_SELF_MULT : 1f; }

    /** Pure: the diversity multiplier for the next copy of a family with `existing` copies: share per copy past `copies`; 1 when copies is 0 (off). */
    static float diversity(int existing, int copies, float share) {
        if (copies <= 0 || existing < 0) return 1f;
        int extra = existing + 1 - copies;
        return extra > 0 ? (float) Math.pow(Math.max(0f, Math.min(1f, share)), extra) : 1f;
    }

    /** The diversity family of a building, or null. */
    static String[] family(String id) {
        if (id == null) return null;
        for (String[] f : CATuning.DIVERSITY_FAMILIES) for (String x : f) if (x.equals(id)) return f;
        return null;
    }

    /** "3rd" for 3. */
    static String nth(int n) {
        int t = n % 100;
        String s = t >= 11 && t <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + s;
    }

    /** Goods trade contracts leave alone while floor 3 or 5 is open: what ships and wings are made of. */
    static final String[] WAR_GOODS = {"ships", "hand_weapons", "advanced_components", "domain_heavy_machinery"};

    /**
     * Pure (M6, JTContractsCheck): the goods trade contracts may not take now: the war goods while order goods (floor 3) or
     * weapons (floor 5) are short, supplies and fuel while their floors (7, 8) are short. Empty when the setting is off.
     */
    static Set<String> keptGoods(State st) {
        Set<String> out = new HashSet<String>();
        if (st == null || !st.on() || !st.keepGoods) return out;
        boolean war = st.weaponsGap > 0;
        for (String g : ORDER_GOODS) if (st.orderGap(g) > 0f) war = true;
        if (war) out.addAll(Arrays.asList(WAR_GOODS));
        if (st.suppliesGap > 0) out.add("supplies");
        if (st.fuelGap > 0) out.add("fuel");
        return out;
    }

    /** Trade contracts leave this good alone today (CAContracts). */
    public static boolean keeps(String com) {
        try { return com != null && keptGoods(state()).contains(com); } catch (Throwable t) { return false; }
    }

    // ---------------- the value of a pick ----------------

    /** What a pick brings to the floors: its colony and what it adds. Built from a CAEngine.Pick (live) or by hand (checks). */
    static final class Input {
        String colony, id;
        int size;
        boolean managed = true, upgrade, alreadySource;
        float colonyQ = Float.NaN;
        float qualityAdd;
        float partsAdd;
        final Map<String, Integer> added = new HashMap<String, Integer>();
        float base(String com) { return CAFloors.base(com); }
    }

    /** One floor's share of a pick's value, for the pick's lines. */
    static final class Part {
        final int floor; final float value; final String have, want;
        Part(int floor, float value, String have, String want) { this.floor = floor; this.value = value; this.have = have; this.want = want; }
    }

    static float base(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getBasePrice(); } catch (Throwable t) { return BASE_PRICES.containsKey(com) ? BASE_PRICES.get(com) : 0f; }
    }

    /** Vanilla base prices, for offline checks (commodities.csv). */
    static final Map<String, Float> BASE_PRICES = new HashMap<String, Float>();
    static {
        BASE_PRICES.put("ships", 300f); BASE_PRICES.put("hand_weapons", 500f); BASE_PRICES.put("supplies", 100f); BASE_PRICES.put("fuel", 25f);
        BASE_PRICES.put("metals", 30f); BASE_PRICES.put("rare_metals", 200f); BASE_PRICES.put("heavy_machinery", 150f);
        BASE_PRICES.put("advanced_components", 1000f);
    }

    static int units(Map<String, Integer> m, String com) { Integer u = m.get(com); return u == null ? 0 : Math.max(0, u); }

    /**
     * Pure: what a pick adds to the open floors a month, each floor's share in parts (null: not wanted). The goal gate and the
     * goal multiplier are the caller's (value()).
     */
    static float valueOf(State st, Input in, List<Part> parts) {
        if (st == null || !st.on() || in == null || !in.managed) return 0f;
        float v = 0f;
        String id = in.id;
        int ships = units(in.added, "ships");
        boolean producer = ships > 0 || SHIP_PRODUCERS.contains(id);
        // floor 1: a new hull source
        if (producer && !in.alreadySource && in.size >= CATuning.FLOOR_HULL_MIN_SIZE) {
            float x = hullValue(true, st.hullSources, st.hullWant, st.valHull);
            if (x != 0f) { v += x; add(parts, HULLS, x, "" + st.hullSources, "" + st.hullWant); }
        }
        // floor 2: capacity from ships units
        if (st.capacityOn && ships > 0) {
            float x = capacityValue(ships, st.capacityPerUnit, st.capacityGap(), st.valCapacity);
            if (x != 0f) { v += x; add(parts, CAPACITY, x, Misc0.k(st.capacity), Misc0.k(st.capacityWant)); }
        }
        // floor 3: order goods, and ship consumers against them
        if (st.ordersOn) {
            for (String g : ORDER_GOODS) {
                float gap = st.orderGap(g);
                int u = units(in.added, g);
                if (gap > 0f && u > 0) {
                    float x = goodsValue(u, gap, in.base(g), st.unitValue, st.valOrders);
                    if (x != 0f) { v += x; add(parts, ORDERS, x, g, null); }
                }
            }
            Integer eats = id == null ? null : SHIP_CONSUMERS.get(id);
            if (eats != null && !in.upgrade) {
                Float n = st.orderNeed.get("ships"), m = st.orderMade.get("ships");
                float x = consumerLoss(n == null ? 0f : n, m == null ? 0f : m, eats, in.base("ships"), st.unitValue, st.valOrders);
                if (x != 0f) { v += x; add(parts, ORDERS, x, "ships", null); }
            }
        }
        // floor 4: quality (both ways: Orbital Works closes it, Salvage Yards' -0.1 may open it)
        if (in.qualityAdd != 0f && !Float.isNaN(in.colonyQ)) {
            float before = st.quality();
            float after = qualityAfter(in.colony, in.colonyQ, in.qualityAdd, st.bestId, st.bestQ, st.secondQ, st.qualityOffset);
            float x = qualityValue(before, after, st.qualityWant, st.valQuality);
            if (x != 0f) { v += x; add(parts, QUALITY, x, f2(before), f2(st.qualityWant)); }
        }
        // floor 5: weapons covering the empire's shortage
        int hw = units(in.added, "hand_weapons");
        if (st.weaponsGap > 0 && hw > 0) {
            float x = goodsValue(hw, st.weaponsGap, in.base("hand_weapons"), st.unitValue, st.valGoods);
            if (x != 0f) { v += x; add(parts, WEAPONS, x, null, null); }
        }
        // floor 6: parts a month toward the fleet's repair need
        if (st.partsGap() > 0 && in.partsAdd > 0f) {
            float x = capped(in.partsAdd, st.partsGap()) * st.valPart;
            if (x != 0f) { v += x; add(parts, PARTS, x, Misc0.n(st.partsStock), Misc0.n(st.partsWant())); }
        }
        // floors 7 and 8
        int sup = units(in.added, "supplies"), fuel = units(in.added, "fuel");
        if (st.suppliesGap > 0 && sup > 0) { float x = goodsValue(sup, st.suppliesGap, in.base("supplies"), st.unitValue, st.valGoods); v += x; add(parts, SUPPLIES, x, null, null); }
        if (st.fuelGap > 0 && fuel > 0) { float x = goodsValue(fuel, st.fuelGap, in.base("fuel"), st.unitValue, st.valGoods); v += x; add(parts, FUEL, x, null, null); }
        // floor 9: inputs of the floor producers
        for (String g : INPUT_GOODS) {
            Integer gap = st.inputGap.get(g);
            int u = units(in.added, g);
            if (gap != null && gap > 0 && u > 0) { float x = goodsValue(u, gap, in.base(g), st.unitValue, st.valGoods); v += x; add(parts, INPUTS, x, g, null); }
        }
        return v;
    }

    static void add(List<Part> parts, int floor, float value, String have, String want) { if (parts != null) parts.add(new Part(floor, value, have, want)); }

    static String f2(float v) { return String.format(Locale.ROOT, "%.2f", v); }

    /** Number text without the game (offline checks): 40,000. */
    static final class Misc0 {
        static String k(float v) {
            try { return Misc.getDGSCredits(v); } catch (Throwable t) { return String.format(Locale.ROOT, "%,d", Math.round(v)); }
        }
        /** A count without the credit sign: 11,488. */
        static String n(float v) {
            try { return Misc.getWithDGS(Math.round(v)); } catch (Throwable t) { return String.format(Locale.ROOT, "%,d", Math.round(v)); }
        }
    }

    /** One line per floor a pick closes or opens: "Production floor: hull capacity 40,000 of 100,000. This adds 60,000 a month." */
    static String line(Part p) {
        String name = NAMES[p.floor];
        String what;
        if (p.floor == ORDERS || p.floor == INPUTS) what = name + " (" + goodName(p.have) + ")";
        else if (p.have != null && p.want != null) what = name + " " + p.have + " of " + p.want;
        else what = name;
        String v = Misc0.k(Math.abs(p.value));
        return "Production floor: " + what + ". " + (p.value >= 0f ? "This adds " + v + " a month" : "This takes " + v + " a month off");
    }

    static String goodName(String com) {
        if (com == null) return "";
        try { return Global.getSettings().getCommoditySpec(com).getName().toLowerCase(Locale.ROOT); } catch (Throwable t) { return com.replace('_', ' '); }
    }

    /**
     * What a pick adds to the open floors a month at m (0 when floors are off, at Money colonies unless set, in fast buildout,
     * or at a colony that isn't Approve or Auto). explain: the pick gets one line per floor. Sets p.floorValue.
     */
    static float value(MarketAPI m, CAEngine.Pick p, CAData.Profile profile, boolean explain) {
        if (p == null) return 0f;
        p.floorValue = 0f;
        State st = state();
        if (st == null || !st.on() || m == null || !applies(profile, st.moneyGoal, CAEngine.profitOnly)) return 0f;
        Input in = input(m, p);
        if (in == null) return 0f;
        List<Part> parts = explain ? new ArrayList<Part>() : null;
        float v = valueOf(st, in, parts) * goalMult(profile);
        if (!(Math.abs(v) >= 1f) || Float.isNaN(v)) return 0f;
        p.floorValue = v;
        if (parts != null) for (Part x : parts) if (Math.abs(x.value) >= 1f) p.lines.add(line(x));
        return v;
    }

    /** The pick's floor input from the game: its colony, what it adds, its quality change and parts. Null on a read failure. */
    static Input input(MarketAPI m, CAEngine.Pick p) {
        try {
            Input in = new Input();
            in.colony = m.getId(); in.id = p.id; in.size = m.getSize(); in.upgrade = p.upgrade;
            CAData.Mode mode = CAData.get().colony(m.getId()).mode;
            in.managed = mode == CAData.Mode.AUTO || mode == CAData.Mode.APPROVE;
            State st = current;
            in.alreadySource = st != null && st.hullColonies.contains(m.getId());
            in.added.putAll(CAValue.chainUnits(p));
            in.qualityAdd = p.fx[5];
            Float q = st == null ? null : st.colonyQ.get(m.getId());
            in.colonyQ = q != null ? q : colonyQuality(m);
            in.partsAdd = p.partsAdded;
            if (DRY_DOCK.equals(p.id) && st != null && st.fleetDmods > 0) in.partsAdd += dockParts(m.getSize(), st);
            return in;
        } catch (Throwable t) { return null; }
    }

    /** Restoration Docks: up to the colony's size d-mods a month, each worth the fleet's average parts per d-mod. */
    static float dockParts(int size, State st) {
        if (st.fleetDmods <= 0 || st.partsNeed <= 0) return 0f;
        float perDmod = st.partsNeed / (float) st.fleetDmods;
        return Math.min(size, st.fleetDmods) * perDmod;
    }

    // ---------------- keep side: what taking a floor producer down opens ----------------

    /**
     * What keeping a built industry is worth to the floors a month: the hull source value when taking it down drops the empire
     * under its hull source floor, and the quality value when it is the best market's Orbital Works. 0 when floors are off.
     * CAValue.keepDetail adds it, so CAPrune and the make-room and endgame swaps keep floor producers.
     */
    static float keepValue(MarketAPI m, Industry ind, CAData.Profile profile) {
        State st = state();
        if (st == null || !st.on() || m == null || ind == null || !applies(profile, st.moneyGoal, CAEngine.profitOnly)) return 0f;
        float v = 0f;
        try {
            String id = ind.getId();
            boolean makesShips = SHIP_PRODUCERS.contains(id) || supplies(ind, "ships");
            if (makesShips && st.hullColonies.contains(m.getId()) && st.hullSources <= st.hullWant && !otherShipMaker(m, ind)) v += st.valHull;
            if ("orbitalworks".equals(id) && m.getId().equals(st.bestId)) {
                float drop = CAEngine.ORBITAL_WORKS_QUALITY;
                float after = qualityAfter(m.getId(), st.bestQ, -drop, st.bestId, st.bestQ, st.secondQ, st.qualityOffset);
                v += -qualityValue(st.quality(), after, st.qualityWant, st.valQuality);
            }
        } catch (Throwable ignored) { }
        return v * goalMult(profile);
    }

    static boolean supplies(Industry ind, String com) {
        try { return ind.getSupply(com) != null && ind.getSupply(com).getQuantity().getModifiedInt() > 0; } catch (Throwable t) { return false; }
    }

    static boolean otherShipMaker(MarketAPI m, Industry skip) {
        for (Industry o : m.getIndustries()) if (o != skip && !o.isBuilding() && (SHIP_PRODUCERS.contains(o.getId()) || supplies(o, "ships"))) return true;
        return false;
    }

    /** A floor producer here: taking it down would open a floor (CAPrune, the endgame and make-room leave it). */
    public static boolean protects(MarketAPI m, Industry ind) {
        try { return keepValue(m, ind, CAValue.profileOf(m)) > 0f; } catch (Throwable t) { return false; }
    }

    // ---------------- diversity ----------------

    /** Family copies at Approve and Auto colonies, built and under construction, by family key (first id); per day and empire signature. */
    static Map<String, Integer> families;
    static int familiesDay = Integer.MIN_VALUE;
    static long familiesSig;

    static Map<String, Integer> familyCounts() {
        int day = CAEngine.today();
        long sig = day >= 0 ? CAEngine.empireSig() : 0L;
        if (families != null && day == familiesDay && sig == familiesSig) return families;
        Map<String, Integer> out = new HashMap<String, Integer>();
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Mode mode = d.colony(m.getId()).mode;
            if (mode != CAData.Mode.AUTO && mode != CAData.Mode.APPROVE) continue;
            for (Industry ind : m.getIndustries()) {
                String[] f = family(ind.getId());
                if (f != null) out.put(f[0], (out.containsKey(f[0]) ? out.get(f[0]) : 0) + 1);
            }
        }
        families = out; familiesDay = day; familiesSig = sig;
        return out;
    }

    /**
     * The diversity multiplier on a pick's money (1 when off or not in a family). An upgrade within its own family is not a
     * new copy. explain: the pick gets a line when its money counts less.
     */
    static float diversityMult(MarketAPI m, CAEngine.Pick p, boolean explain) {
        State st = state();
        if (st == null || !st.on() || st.divCopies <= 0 || p == null || CAEngine.profitOnly) return 1f;
        String[] f = family(p.id);
        if (f == null) return 1f;
        if (p.upgrade && p.existing != null && family(p.existing.getId()) == f) return 1f;
        try {
            Integer n = familyCounts().get(f[0]);
            int existing = n == null ? 0 : n;
            float mult = diversity(existing, st.divCopies, st.divShare);
            if (explain && mult < 1f) p.lines.add("Diversity: " + nth(existing + 1) + " " + (p.name != null ? p.name : p.id) + " in the empire. Its money counts "
                    + Math.round(mult * 100f) + "%");
            return mult;
        } catch (Throwable t) { return 1f; }
    }

    // ---------------- money taper ----------------

    /** The taper curve and the net it is read at, or null (off, floors off, the Money goal, fast buildout, the endgame). */
    static float tapered(float money, CAData.Profile profile, boolean endgame) {
        State st = state();
        if (st == null || !st.on() || st.taper == null || CAEngine.profitOnly || endgame || profile == CAData.Profile.INCOME) return money;
        return CAMoneyCurve.apply(money, st.taperNet, st.taper);
    }

    // ---------------- measure (live) ----------------

    static State measure(CASettings s) {
        State st = new State();
        st.mode = s.productionMode;
        st.newWeights = s.goalWeights == 0;
        st.needsFirst = s.needsFirst;
        st.moneyGoal = s.floorMoneyGoal;
        st.makeRoom = s.makeRoom;
        st.keepGoods = s.keepProductionGoods;
        st.taper = CAMoneyCurve.curve(s.moneyTaper, s.taperLow, s.taperHigh);
        st.divCopies = s.diversityCopies; st.divShare = s.diversityShare;
        st.valHull = s.floorValHull; st.valCapacity = s.floorValCapacity; st.valQuality = s.floorValQuality;
        st.valPart = s.floorValPart; st.valOrders = s.floorValOrders; st.valGoods = s.floorValGoods;
        st.unitValue = CAEngine.unitValue;
        st.hullWant = s.floorHullSources;
        st.qualityWant = s.floorQuality;
        st.partsReserve = s.floorPartsReserve;
        try { st.taperNet = Math.max(0f, CABudget.netIncome(s)) + Math.max(0f, CAContracts.monthlyPay()); } catch (Throwable t) { st.taperNet = 0f; }
        CAData d = CAData.get();
        List<MarketAPI> markets = Misc.getPlayerMarkets(false);
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        // floor 1 and the quality of each market. Pending-aware (as CAValue.deficits): a producer under construction counts as
        // a hull source and adds its ships line to the capacity, and an Orbital Works under way adds its quality, so two
        // colonies never build for one floor
        int shipUnits = 0, pendingUnits = 0;
        for (MarketAPI m : markets) {
            if (CAEngine.isOutpost(m)) continue;
            try {
                float q = colonyQuality(m) + pendingQuality(m);
                st.colonyQ.put(m.getId(), q);
                if (Float.isNaN(st.bestQ) || q > st.bestQ) { st.secondQ = st.bestQ; st.bestQ = q; st.bestId = m.getId(); }
                else if (Float.isNaN(st.secondQ) || q > st.secondQ) st.secondQ = q;
                CommodityOnMarketAPI c = m.getCommodityData("ships");
                if (c != null) shipUnits += Math.max(0, c.getMaxSupply());
                CAData.Mode mode = d.colony(m.getId()).mode;
                boolean managed = mode == CAData.Mode.AUTO || mode == CAData.Mode.APPROVE;
                for (Industry ind : m.getIndustries()) {
                    boolean pending = ind.isBuilding() && !ind.isUpgrading();
                    boolean producer = SHIP_PRODUCERS.contains(ind.getId()) || supplies(ind, "ships");
                    if (!producer) continue;
                    if (pending) pendingUnits += Math.max(0, m.getSize() - 2);
                    if (managed && m.getSize() >= CATuning.FLOOR_HULL_MIN_SIZE) st.hullColonies.add(m.getId());
                }
            } catch (Throwable t) { CAStep.warn("production floors", "floor read failed at " + m.getName(), t); }
        }
        st.hullSources = st.hullColonies.size();
        try {
            float doctrine = Global.getSector().getPlayerFaction().getDoctrine().getShipQualityContribution();
            float per = setting("doctrineFleetQualityPerPoint", DOCTRINE_PER_POINT);
            st.qualityOffset = quality(0f, doctrine, per);
        } catch (Throwable t) { st.qualityOffset = 4f * DOCTRINE_PER_POINT; }
        // floors 2 and 3: vanilla capacity, or AoTD's orders while Vaults of Knowledge runs production
        st.ordersOn = Global.getSettings().getModManager().isModEnabled(CAResearch.MOD);
        st.capacityOn = !st.ordersOn;
        try {
            com.fs.starfarer.api.campaign.FactionProductionAPI prod = Global.getSector().getPlayerFaction().getProduction();
            st.capacity = prod.getMonthlyProductionCapacity();
            st.capacityPerUnit = shipUnits > 0 && st.capacity > 0f ? st.capacity / shipUnits : CATuning.FLOOR_CAPACITY_PER_UNIT;
            st.capacity += pendingUnits * st.capacityPerUnit;   // producers under construction
            float want = s.floorCapacity;
            // M6: the production list over two months raises the floor; so do the Doctrine priority hulls the Shipyard rung would order
            want = Math.max(want, Math.max(0, prod.getTotalCurrentCost()) / 2f);
            if (CARich.mode(CARich.SHIPYARD) != CARich.OFF) want = Math.max(want, shipyardHulls(s) / 6f + s.floorCapacity);
            st.capacityWant = want;
        } catch (Throwable t) { st.capacityOn = false; }
        if (st.ordersOn) {
            try {
                CAProjects.readOrders();
                for (String g : ORDER_GOODS) {
                    Integer need = CAProjects.orders.get(g);
                    st.orderNeed.put(g, monthly(need == null ? 0 : need, s.floorOrderMonths));
                    st.orderMade.put(g, (float) Math.max(0, CAProjects.yours(g)));
                }
            } catch (Throwable t) { st.ordersOn = false; }
        }
        // floor 5, 7, 8: empire shortages; Full asks for the fleet's own use of supplies and fuel too
        st.weaponsGap = units(deficit, "hand_weapons");
        st.suppliesGap = goodsGap(s.floorSupplies, deficit, "supplies", markets);
        st.fuelGap = goodsGap(s.floorFuel, deficit, "fuel", markets);
        // floor 9: inputs short at a colony that runs a floor producer
        for (MarketAPI m : markets) {
            if (!st.hullColonies.contains(m.getId())) continue;
            for (String g : INPUT_GOODS) {
                try {
                    CommodityOnMarketAPI c = m.getCommodityData(g);
                    int lack = c == null ? 0 : Math.max(0, c.getDeficitQuantity());
                    if (lack > 0) st.inputGap.put(g, Math.max(lack, st.inputGap.containsKey(g) ? st.inputGap.get(g) : 0));
                } catch (Throwable ignored) { }
            }
        }
        // floor 6: parts
        st.partsOn = CASurvival.partsRepairs();
        if (st.partsOn) {
            for (MarketAPI m : markets) {
                try {
                    int local = parts(localCargo(m)), stored = parts(Misc.getStorageCargo(m));
                    st.partsLocal += local;
                    if (local > 0) st.partsColonies++;
                    st.partsStock += local + stored;
                    CommodityOnMarketAPI c = m.getCommodityData("supplies");
                    if (c != null && c.getMaxSupply() > 0) st.partsSource = true;
                } catch (Throwable ignored) { }
            }
            try { st.partsStock += parts(Global.getSector().getPlayerFleet().getCargo()); } catch (Throwable ignored) { }
            fleetNeed();
            st.partsNeed = fleetNeed;
            st.fleetDmods = fleetDmodCount;
        }
        plan(st, s);
        return st;
    }

    /** Units short of supplies or fuel: Off none; No shortage the empire's shortage; Fleet use also the fleet's monthly use above your own output. */
    static int goodsGap(int rule, Map<String, Integer> deficit, String com, List<MarketAPI> markets) {
        if (rule == CATuning.FLOOR_GOODS_OFF) return 0;
        int gap = units(deficit, com);
        if (rule == CATuning.FLOOR_GOODS_FLEET) {
            try {
                float unit = Global.getSettings().getCommoditySpec(com).getEconUnit();
                com.fs.starfarer.api.campaign.CampaignFleetAPI f = Global.getSector().getPlayerFleet();
                // supplies: the fleet's monthly use; fuel: half its tanks a month (the game has no monthly fuel use to read)
                float use = "supplies".equals(com) ? f.getLogistics().getTotalSuppliesPerDay() * 30f : f.getCargo().getMaxFuel() * 0.5f;
                int own = 0;
                for (MarketAPI m : markets) { CommodityOnMarketAPI c = m.getCommodityData(com); if (c != null) own += Math.max(0, c.getMaxSupply()); }
                int want = unit > 0f ? (int) Math.ceil(use / unit) : 0;
                gap = Math.max(gap, want - own);
            } catch (Throwable ignored) { }
        }
        return Math.max(0, gap);
    }

    /** The cost of shipyardStop Doctrine priority hulls (the cheapest ones the Shipyard rung would order). */
    static float shipyardHulls(CASettings s) {
        try {
            com.fs.starfarer.api.campaign.FactionAPI pf = Global.getSector().getPlayerFaction();
            com.fs.starfarer.api.campaign.FactionProductionAPI prod = pf.getProduction();
            List<Float> costs = new ArrayList<Float>();
            for (CAShipyard.Hull h : CAShipyard.hulls(pf, prod, new HashMap<String, Integer>(), new HashMap<String, Integer>())) costs.add((float) h.cost);
            Collections.sort(costs);
            float sum = 0f;
            for (int i = 0; i < costs.size() && i < Math.max(0, s.shipyardStop); i++) sum += costs.get(i);
            return sum;
        } catch (Throwable t) { return 0f; }
    }

    /** Production quality an Orbital Works under way here will add (an upgrade from Heavy Industry in progress). */
    static float pendingQuality(MarketAPI m) {
        try {
            Industry hi = m.getIndustry("heavyindustry");
            if (hi == null || !hi.isUpgrading()) return 0f;
            String to = CAData.get().upgradeTargets().get(m.getId() + "|heavyindustry");
            return to == null || "orbitalworks".equals(to) ? CAEngine.ORBITAL_WORKS_QUALITY : 0f;
        } catch (Throwable t) { return 0f; }
    }

    /** A market's production quality plus fleet quality (CoreScript's per-market term). */
    static float colonyQuality(MarketAPI m) {
        try {
            return m.getStats().getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.PRODUCTION_QUALITY_MOD).computeEffective(0f)
                    + m.getStats().getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.FLEET_QUALITY_MOD).computeEffective(0f);
        } catch (Throwable t) { return Float.NaN; }
    }

    static float setting(String key, float dflt) {
        try { return Global.getSettings().getFloat(key); } catch (Throwable t) { return dflt; }
    }

    static int[] minParts() {
        try {
            org.json.JSONArray a = Global.getSettings().getJSONArray("minDModRepairCostInParts");
            if (a != null && a.length() >= 4) return new int[]{a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3)};
        } catch (Throwable ignored) { }
        return MIN_PARTS;
    }

    static CargoAPI localCargo(MarketAPI m) {
        SubmarketAPI sm = m.getSubmarket("local_resources");
        return sm == null ? null : sm.getCargo();
    }

    static int parts(CargoAPI c) {
        if (c == null) return 0;
        try { return Math.max(0, Math.round(c.getCommodityQuantity("parts"))); } catch (Throwable t) { return 0; }
    }

    /** The fleet's d-mod repair need in parts and its d-mod count, read weekly. */
    static float fleetAt = Float.NaN;
    static int fleetNeed, fleetDmodCount;

    static void fleetNeed() {
        float now = CATime.now();
        if (!Float.isNaN(fleetAt) && CATime.fresh(fleetAt, now, CATuning.FLOOR_FLEET_DAYS)) return;
        fleetAt = now;
        int need = 0, count = 0;
        try {
            int[] mins = minParts();
            float div = setting("defaultDModRepairPartsBaseValueDivisor", PARTS_DIVISOR);
            for (FleetMemberAPI fm : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                int n = dmods(fm);
                if (n <= 0) continue;
                count += n;
                need += n * partsPerDmod(fm.getHullSpec().getBaseValue(), sizeIndex(fm), mins, div);
            }
        } catch (Throwable t) { CAStep.warn("production floors", "fleet d-mod read failed", t); }
        fleetNeed = need; fleetDmodCount = count;
    }

    /** D-mods on a ship that a repair can take off: its permanent d-mods less the hull's built-in ones. */
    static int dmods(FleetMemberAPI fm) {
        int n = 0;
        try {
            Collection<String> built = fm.getHullSpec().getBuiltInMods();
            for (String id : fm.getVariant().getPermaMods()) {
                com.fs.starfarer.api.loading.HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
                if (spec != null && spec.hasTag("dmod") && (built == null || !built.contains(id))) n++;
            }
        } catch (Throwable ignored) { }
        return n;
    }

    static int sizeIndex(FleetMemberAPI fm) {
        try {
            switch (fm.getHullSpec().getHullSize()) {
                case DESTROYER: return 1;
                case CRUISER: return 2;
                case CAPITAL_SHIP: return 3;
                default: return 0;
            }
        } catch (Throwable t) { return 0; }
    }

    // ---------------- floor steps (M2: generalizes CAProjects' wanted goods) ----------------

    /** Plan steps by colony id (CAPlan.build puts them in front of the colony's own plan). */
    static final Map<String, List<CAPlan.Step>> STEPS = new HashMap<String, List<CAPlan.Step>>();

    /** A colony as the floor planner sees it. */
    static final class Host {
        String id, name;
        int size, freeSlots;
        boolean auto, habitable;
        final Set<String> has = new HashSet<String>();
    }

    /**
     * Pure: the host for a producer: a colony with its base when it has one (an upgrade), else a new building at a colony of
     * size 4 or more with a free slot (or, with make room on, without one). Bigger, Auto, not habitable (no pollution risk)
     * first. exclude: colonies that already have it. Null when none fits.
     */
    static Host host(String building, String base, List<Host> hosts, boolean makeRoom, Set<String> claimed) {
        Host best = null;
        float bestScore = Float.NEGATIVE_INFINITY;
        for (Host h : hosts) {
            if (h.has.contains(building) || claimed.contains(h.id)) continue;
            boolean upgrade = base != null;
            if (upgrade && !h.has.contains(base)) continue;
            if (!upgrade && h.size < CATuning.FLOOR_HULL_MIN_SIZE) continue;
            if (!upgrade && h.freeSlots <= 0 && !makeRoom) continue;
            float sc = (upgrade ? 200f : 0f) + (h.freeSlots > 0 ? 100f : 0f) + h.size * 10f + (h.auto ? 30f : 0f) + (h.habitable ? 0f : 20f);
            if (sc > bestScore) { bestScore = sc; best = h; }
        }
        return best;
    }

    /**
     * Pure: the producers the open floors want, in order, as {building, base or null, floor name}: Heavy Industry for a hull
     * source, capacity, order ships and weapons, parts with no supplies source, supplies; Orbital Works for quality at a Heavy
     * Industry; Fuel Production for fuel; Refining for metals; High-tech Industry for AoTD's advanced components.
     */
    static List<String[]> wanted(State st) {
        List<String[]> out = new ArrayList<String[]>();
        if (st.hullGap() > 0) out.add(new String[]{"heavyindustry", null, NAMES[HULLS]});
        else if (st.capacityGap() > 0f) out.add(new String[]{"heavyindustry", null, NAMES[CAPACITY]});
        else if (st.orderGap("ships") > 0f || st.orderGap("hand_weapons") > 0f) out.add(new String[]{"heavyindustry", null, NAMES[ORDERS]});
        else if (st.partsGap() > 0 && !st.partsSource) out.add(new String[]{"heavyindustry", null, NAMES[PARTS]});
        else if (st.suppliesGap > 0) out.add(new String[]{"heavyindustry", null, NAMES[SUPPLIES]});
        if (st.qualityGap() > 0.001f) out.add(new String[]{"orbitalworks", "heavyindustry", NAMES[QUALITY]});
        if (st.orderGap("advanced_components") > 0f) out.add(new String[]{"aotd_hightech_industry", "lightindustry", NAMES[ORDERS]});
        if (st.fuelGap > 0) out.add(new String[]{"fuelprod", null, NAMES[FUEL]});
        Integer metals = st.inputGap.get("metals"), rare = st.inputGap.get("rare_metals");
        if ((metals != null && metals > 0) || (rare != null && rare > 0)) out.add(new String[]{"refining", null, NAMES[INPUTS]});
        return out;
    }

    /** Hosts from the game: Approve and Auto colonies where buildings go up. */
    static List<Host> hosts() {
        List<Host> hs = new ArrayList<Host>();
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if ((c.mode != CAData.Mode.AUTO && c.mode != CAData.Mode.APPROVE) || CAEngine.isOutpost(m) || CAHypershunt.is(m)
                    || m.hasTag("market_no_industries_allowed")) continue;
            Host h = new Host();
            h.id = m.getId(); h.name = m.getName(); h.size = m.getSize(); h.auto = c.mode == CAData.Mode.AUTO;
            h.habitable = m.hasCondition("habitable");
            h.freeSlots = Math.max(0, CABuild.freeSlots(m));
            for (Industry ind : m.getIndustries()) h.has.add(ind.getId());
            for (String l : c.locks) h.has.add(l);   // a locked slot's building stays: nothing replaces it
            hs.add(h);
        }
        return hs;
    }

    /** The producers the open floors want, each at its host: STEPS for CAPlan.build, and the state's list for the panel. */
    static void plan(State st, CASettings s) {
        STEPS.clear();
        if (!st.on()) return;
        List<Host> hosts = hosts();
        Set<String> claimed = new HashSet<String>();
        for (String[] w : wanted(st)) {
            if (CAPlan.spec(w[0]) == null) continue;   // a mod building that isn't installed
            Host h = host(w[0], w[1], hosts, s.makeRoom != CATuning.MAKE_ROOM_OFF, claimed);
            if (h == null) continue;
            if (w[1] == null) claimed.add(h.id);   // one new producer per colony
            List<CAPlan.Step> l = STEPS.get(h.id);
            if (l == null) { l = new ArrayList<CAPlan.Step>(); STEPS.put(h.id, l); }
            String name = buildingName(w[0]);
            l.add(new CAPlan.Step(CAPlan.Kind.BUILD, w[0], name + " (production floor: " + w[2] + ")"));
            List<String[]> sl = st.steps.get(h.id);
            if (sl == null) { sl = new ArrayList<String[]>(); st.steps.put(h.id, sl); }
            sl.add(new String[]{name, h.name, w[2]});
        }
    }

    static String buildingName(String id) {
        try { return Global.getSettings().getIndustrySpec(id).getName(); } catch (Throwable t) { return id; }
    }

    /** The floor steps at this colony (none while floors are off or not measured). */
    static List<CAPlan.Step> stepsFor(MarketAPI m) {
        if (m == null || STEPS.isEmpty() || current == null || !current.on()) return Collections.emptyList();
        List<CAPlan.Step> l = STEPS.get(m.getId());
        return l == null ? Collections.<CAPlan.Step>emptyList() : l;
    }

    /** A floor step at m, or on the way to one. */
    static boolean isStep(MarketAPI m, String industryId) {
        for (CAPlan.Step s : stepsFor(m)) if (s.id.equals(industryId) || CAPlan.leadsTo(industryId, s.id)) return true;
        return false;
    }

    /** The first planned floor producer, as "Heavy Industry planned at Togabru", or null (the Shipyard rung's note). */
    public static String plannedText() {
        State st = current;
        if (st == null) return null;
        for (List<String[]> l : st.steps.values()) for (String[] x : l) return x[0] + " planned at " + x[1];
        return null;
    }

    // ---------------- daily ----------------

    /** Daily from CAAuto: arms the measure, measures, and gives the parts advice line. */
    public static void update() {
        armed = true;
        CASettings s = CAData.settings();
        if (s.productionFilled) {   // an older save got the production defaults on load (CASettings.fixNulls): said once
            s.productionFilled = false;
            CALog.advise("Production: Light is on for this save. Colonies build ship production, quality and parts first. Settings, Advanced, Production: Off keeps the 0.3.1 picks.");
        }
        if (!s.productionOn()) { current = null; STEPS.clear(); return; }
        State st = state();
        if (st == null) return;
        partsAdvice(st);
    }

    static String adviceKey;

    /** One advice line where the parts are and what the fleet's d-mods need, when that changes (at most every 30 days for the same text). */
    static void partsAdvice(State st) {
        if (!st.partsOn || st.partsLocal <= 0 || st.fleetDmods <= 0) return;
        String key = "floor:parts:" + (st.partsLocal / 500) + ":" + (st.partsNeed / 50);
        if (key.equals(adviceKey)) return;
        adviceKey = key;
        float price = base("parts") * setting("stockpileCostMult", 1f);
        if (CALog.once(key))
            CALog.advise("Parts: " + Misc0.n(st.partsLocal) + " in Local Resources at " + st.partsColonies + (st.partsColonies == 1 ? " colony" : " colonies")
                    + ". Fleet d-mods need about " + Misc0.n(st.partsNeed) + ". Taking them costs " + Misc.getDGSCredits(price) + " a part.");
    }

    // ---------------- make room (Batch E) ----------------

    /**
     * The weakest income-only industry a floor producer may replace at m, or null: make room on, a floor pick, the colony the
     * floor planner picked for it (one with a free slot goes first), no free slot here. Never a plan or project building, a military one, a locked one, the capital's trade buildings, a floor
     * producer, one built within a year, or a swap that breaks the stability floor (CAEndgame.weakest and CAStability).
     */
    static CAEndgame.Weakest makeRoom(MarketAPI m, CAData.Colony col, CAEngine.Pick best, CAData.Profile profile) {
        State st = state();
        if (st == null || !st.on() || st.makeRoom == CATuning.MAKE_ROOM_OFF || best == null || best.measured || !(best.floorValue > 0f)) return null;
        // only at the colony the floor planner picked for it: a colony with a free slot goes first (host)
        if (!isStep(m, best.id) || CAEndgame.rebuildBlocked(m, best.id)) return null;
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        CAEndgame.Weakest wk = CAEndgame.weakest(m, col, plan, profile, best, true);
        if (wk == null || wk.ind == null) return null;
        String id = wk.ind.getId();
        if (CAEndgame.planStep(plan, id) || CAProjects.isStep(m, id) || CACapital.isStep(m, id) || protects(m, wk.ind)) return null;
        if (wk.built != null && wk.built.ageDays >= 0f && wk.built.ageDays < CATuning.MAKE_ROOM_BUILT_DAYS) return null;
        if (!CAEndgame.worthIt(best, wk)) return null;
        return wk;
    }

    /**
     * Approve on a make-room proposal (CAAuto.approve): takes the proposed industry down for the floor producer, after the same
     * checks as the planner (kept industries, credits, research, stability). Null when it came down; else why not.
     */
    static String swap(MarketAPI m, CAData.Colony c, CAEngine.Pick p, String industryId, CAData.Profile profile) {
        Industry ind = m.getIndustry(industryId);
        if (ind == null) return "The industry it replaces is gone";
        CAData d = CAData.get();
        CAPlan.Plan plan = CAPlan.build(m, c.profile);
        if (CAEndgame.protectedHere(m, c, plan, ind, d) || CAEndgame.planStep(plan, industryId) || CACapital.isStep(m, industryId) || protects(m, ind))
            return ind.getCurrentName() + " is kept now";
        String why = CAEndgame.cantStart(m, p);
        if (why != null) return "Not started: " + why;
        float h = CAHorizon.months();
        CATombstone.Built b = CATombstone.built(m, industryId, h, CATombstone.valuation(m));
        CAEndgame.Weakest wk = CAEndgame.of(ind, CAValue.keepDetail(m, ind, profile, CAEngine.empireDeficits()), b, h);
        if (!CAEndgame.takeDown(m, wk, p, CAPlanner.PickRef.FLOOR)) return "Stability holds the swap";
        return null;
    }

    // ---------------- panel ----------------

    /** The Budget panel's floor list: one line per floor, met or how far off, and the steps planned. */
    public static void render(TooltipMakerAPI t) {
        State st = current;
        Color gray = Misc.getGrayColor(), hl = Misc.getHighlightColor(), bad = Misc.getNegativeHighlightColor();
        CASettings s = CAData.settings();
        CATheme.heading(t, "Production floors", 12f);
        if (!s.productionOn()) { t.addPara("Production: Off. Floors, taper and diversity are not used.", gray, 4f); return; }
        if (st == null) { t.addPara("Production: " + CASettings.PRODUCTION_NAMES[s.productionMode] + ". Measured on the next daily pass.", gray, 4f); return; }
        for (String l : lines(st)) t.addPara(CALog.esc(l), l.endsWith("Met.") ? gray : hl, 3f);
        for (Map.Entry<String, List<String[]>> e : st.steps.entrySet())
            for (String[] x : e.getValue()) t.addPara(CALog.esc("Planned: " + x[0] + " at " + x[1] + " (" + x[2] + ")."), hl, 3f);
        if (st.partsOn && st.partsLocal > 0)
            t.addPara(CALog.esc("Parts: " + Misc0.n(st.partsLocal) + " in Local Resources at " + st.partsColonies + (st.partsColonies == 1 ? " colony." : " colonies.")),
                    st.partsGap() > 0 ? bad : gray, 3f);
    }

    /** Pure apart from number text: one line per floor in use. */
    static List<String> lines(State st) {
        List<String> out = new ArrayList<String>();
        if (st.hullWant > 0) out.add("Hull source: " + st.hullSources + " of " + st.hullWant + "." + (st.hullGap() > 0 ? "" : " Met."));
        if (st.capacityOn) out.add("Hull capacity: " + Misc0.k(st.capacity) + " of " + Misc0.k(st.capacityWant) + " a month." + (st.capacityGap() > 0f ? "" : " Met."));
        if (st.ordersOn) for (String g : ORDER_GOODS) {
            Float n = st.orderNeed.get(g);
            if (n == null || n <= 0f) continue;
            Float m = st.orderMade.get(g);
            out.add("Order goods, " + goodName(g) + ": " + Math.round(m == null ? 0f : m) + " of " + Math.round(n) + " a month." + (st.orderGap(g) > 0f ? "" : " Met."));
        }
        out.add("Production quality: " + f2(st.quality()) + " of " + f2(st.qualityWant) + ", about " + String.format(Locale.ROOT, "%.1f", avgDmods(st.quality(), QUALITY_PER_DMOD))
                + " d-mods a new hull." + (st.qualityGap() > 0.001f ? "" : " Met."));
        out.add("Weapons: " + (st.weaponsGap > 0 ? st.weaponsGap + " short." : "no shortage. Met."));
        if (st.partsOn) out.add("Domain-grade Parts: " + Misc0.n(st.partsStock) + " of " + Misc0.n(st.partsWant()) + " (fleet d-mods " + st.fleetDmods + ")."
                + (st.partsGap() > 0 ? "" : " Met."));
        out.add("Supplies: " + (st.suppliesGap > 0 ? st.suppliesGap + " short." : "Met."));
        out.add("Fuel: " + (st.fuelGap > 0 ? st.fuelGap + " short." : "Met."));
        int inputs = 0;
        for (Integer v : st.inputGap.values()) if (v != null) inputs += v;
        out.add("Producer inputs: " + (inputs > 0 ? inputs + " short." : "Met."));
        return out;
    }
}
