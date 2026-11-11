package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * WS-C3 rung 1 (shortage stockpiles) and the military goods of rung 3. Research 03 section 4d.
 *
 * 0.3.0.0 (C3): buys goods for a colony's Local Resources, which the game draws on to cover a shortage
 * (LocalResourcesSubmarketPlugin: while stock lasts the colony counts as having what it demands, and it draws
 * (demand - available) x the good's economy unit a month). Colony Manager buys at the nearest market that isn't hostile to
 * you and has the goods in its open market, at that market's price plus its tariff, takes the goods out of that market, and
 * ships them for the Autopilot Storage shipping fee and travel time (CAStores: 2,000 plus 300 a light-year at your fee level;
 * 2 days plus a day a light-year). They arrive as goods you put there yourself (the plugin's "left" cargo), so the game
 * doesn't charge its 10% stockpile cost on top of the price paid.
 * A good is bought for a colony only:
 * - at an Auto colony (not an outpost, not under siege) that uses its stockpiles for shortages;
 * - when what Local Resources holds plus what's on the way covers under REFILL_MONTHS of draw; it then buys up to MONTHS
 *   (3) of draw;
 * - when it's worth it (worth): what the shortage costs the colony over the months bought is more than the price, tariff
 *   and fee. The cost of a shortage: every output of an industry falls by its largest input shortage (vanilla), so covering
 *   one input brings back that industry's outputs by the difference, valued as the planner values a unit of shortage relief
 *   (base price x CAEngine.unitValue a month); at military buildings and stations, the share of their upkeep paid for
 *   patrols and station strength the shortage of supplies, fuel or ships takes away. Stability effects (food, domestic
 *   goods) aren't valued. 0.3.0.0 (C3 fix 2): the output cut comes from the raw shortages (Local Resources left out) and the
 *   outputs' base quantities, never from the outputs' current quantities, which show no cut while Local Resources covers
 *   the shortage; so a refill comes under a month of cover, before the stock runs out.
 * Military goods (supplies, fuel, ships, heavy armaments) at a colony with a military building or station are paid from
 * the Military rung's allowance, everything else from the Stockpiles rung's. Looked at weekly and on the first day of a
 * month, at most MAX_BUYS purchases a look, within the rung's allowance, and each asks the planner (CAPlanner.setAside)
 * first. On Recommend only the best purchase is proposed on the Budget panel (Approve, Reject); Reject holds that good at
 * that colony off for REJECT_DAYS.
 * 0.3.0.0 (C3 fix): the rung's allowance here is its room (CARich.room: its share of the pool plus what came down, less its
 * spending). After each look a kind says what it still wants (lastWant): the purchases that fit the room and weren't
 * bought; the Stockpiles rung tells CARich itself, and CARich adds the military goods into the Military rung's one want.
 * A purchase waits when its price and fee are more than free cash. The two kinds keep their own proposal and log key, so
 * both can wait for Approve at once, each with its own buttons.
 * Saved: CAData.stockpiles, one Buy per shipment (ids, numbers and a timestamp). A colony lost on the way: the goods go to
 * the biggest colony's Autopilot Storage.
 */
public class CAStockpile {
    /**
     * One purchase on its way. marketId: the colony; commodity; qty; cost: price and tariff paid; sent: game timestamp.
     * 0.3.0.0 (C3) adds: days on the way, the shipping fee, and the market id it was bought at.
     */
    public static class Buy {
        public String marketId, commodity; public int qty; public float cost; public long sent;
        public float days, fee;
        public String from;
    }

    /** Months of draw bought at most, and the cover under which it buys again. */
    static final float MONTHS = 3f, REFILL_MONTHS = 1f;
    /** Days between looks; purchases a look at most; days a rejected proposal holds that good at that colony off. */
    static final float LOOK_DAYS = 7f, REJECT_DAYS = 30f;
    static final int MAX_BUYS = 3;
    /** Sources looked at per good (nearest first). */
    static final int MAX_SOURCES = 12;
    /** Goods military buildings and stations need for patrols and strength; at a colony with one they're the Military rung's. */
    static final Set<String> MILITARY_GOODS = new HashSet<String>(Arrays.asList(Commodities.SUPPLIES, Commodities.FUEL, Commodities.SHIPS, Commodities.HAND_WEAPONS));
    /** Of those, the ones whose shortage cuts patrols and station strength (MilitaryBase, OrbitalStation). */
    static final Set<String> FLEET_GOODS = new HashSet<String>(Arrays.asList(Commodities.SUPPLIES, Commodities.FUEL, Commodities.SHIPS));
    /** CAMark key prefix: a rejected proposal, "stockno:<marketId>|<commodity>". */
    static final String REJECTED = "stockno:";
    /**
     * Budget panel buttons (CARich.handle routes them here). 0.3.0.0 (C3 fix): the kind follows, APPROVE + 0 for stockpiles
     * and APPROVE + 1 for military goods.
     */
    static final String KEY = "jcm:stock:", APPROVE = KEY + "approve:", REJECT = KEY + "reject:";
    /** The two kinds: index 0 stockpiles (Stockpiles rung), 1 military goods (Military rung). */
    static final String[] KIND_NAMES = {"Stockpile", "Military goods"};

    /** One purchase worked out. */
    static final class Need {
        MarketAPI to, src;
        String com, name;
        int qty;
        float price, fee, days, lost, draw, ly;
        boolean military;
        float total() { return price + fee; }
        /** The log line (short: it's also the on-screen line). */
        String line() {
            return to.getName() + ": stockpile of " + Misc.getWithDGS(qty) + " " + name + " from " + src.getName() + ", " + Misc.getDGSCredits(total())
                    + " with shipping, " + Math.round(days) + " days (the shortage costs " + Misc.getDGSCredits(lost) + " a month)";
        }

        /** The numbers behind it, starsector.log only. */
        String detail() {
            return "Stockpile detail: " + to.getName() + ", " + Misc.getWithDGS(qty) + " " + name + " at " + src.getName() + " for " + Misc.getDGSCredits(price)
                    + " (tariff included) plus " + Misc.getDGSCredits(fee) + " shipping over " + String.format(Locale.ROOT, "%.1f", ly) + " ly; covers "
                    + String.format(Locale.ROOT, "%.1f", qty / Math.max(1f, draw)) + " months of draw (" + Misc.getWithDGS(Math.round(draw)) + " a month); "
                    + (military ? "Military" : "Stockpiles") + " rung";
        }
    }

    // ---------------- session state ----------------

    /** Per kind (0 stockpiles, 1 military goods): the proposal waiting for Approve or Reject (Recommend only), or null. */
    static final Need[] pending = new Need[2];
    /** Per kind: the proposal as last logged, so each is logged once. */
    static final String[] loggedKey = new String[2];
    /** The month key of each kind's last look (0 stockpiles, 1 military goods). */
    static final int[] lookedMonth = {Integer.MIN_VALUE, Integer.MIN_VALUE};
    /** 0.3.0.0 (C3 fix): per kind, what it still wanted at its last look (credits; CARich.militaryWant reads the military one). */
    static final float[] lastWant = new float[2];

    static void reset() {
        Arrays.fill(pending, null); Arrays.fill(loggedKey, null); Arrays.fill(lastWant, 0f);
        lookedMonth[0] = Integer.MIN_VALUE; lookedMonth[1] = Integer.MIN_VALUE;
        CATime.forget("stockpile"); CATime.forget("stockpile military");
    }

    static int kind(boolean military) { return military ? 1 : 0; }

    // ---------------- pure rules (JTStockpileCheck) ----------------

    /** Pure value test: the shortage's loss over the months covered is more than what qty units cost (price, tariff and fee per unit). */
    static boolean worth(float lostPerMonth, float unitPrice, int qty, int monthsCovered) {
        return worth(lostPerMonth, unitPrice, qty, (float) monthsCovered);
    }

    static boolean worth(float lostPerMonth, float unitPrice, int qty, float monthsCovered) {
        if (qty <= 0 || !(lostPerMonth > 0f) || !(monthsCovered > 0f) || Float.isNaN(unitPrice) || unitPrice < 0f) return false;
        return lostPerMonth * Math.min(MONTHS, monthsCovered) > unitPrice * qty;
    }

    /**
     * Pure: units to buy: none while what's held and on the way covers REFILL_MONTHS of draw; else up to MONTHS of draw.
     */
    static int toBuy(float drawPerMonth, float have, float inTransit) {
        if (!(drawPerMonth > 0f)) return 0;
        float got = Math.max(0f, have) + Math.max(0f, inTransit);
        if (got >= REFILL_MONTHS * drawPerMonth) return 0;
        return (int) Math.ceil(MONTHS * drawPerMonth - got - 1e-3f);
    }

    /** Pure: an industry's output units a shortage of one input cuts: its largest input shortage less the largest without it. */
    static int restored(int[] inputShortages, int index) {
        int now = 0, without = 0;
        for (int i = 0; i < inputShortages.length; i++) {
            int v = Math.max(0, inputShortages[i]);
            now = Math.max(now, v);
            if (i != index) without = Math.max(without, v);
        }
        return now - without;
    }

    /**
     * Pure, 0.3.0.0 (C3 fix 2): output units an input's shortage cuts: what covering it restores (restored), at most the
     * output's base quantity (the game's deficit cut doesn't take an output under 0).
     */
    static float outputCut(int restored, float base) {
        return Math.max(0f, Math.min((float) restored, base));
    }

    /**
     * 0.3.0.0 (C3 fix 2): an output's base quantity: its positive flat modifiers. The game's deficit cut is a negative flat
     * modifier, so it's left out, and the current (modified) quantity isn't read.
     */
    static float baseQuantity(MutableCommodityQuantity sq) {
        float base = 0f;
        try {
            for (MutableStat.StatMod mod : sq.getQuantity().getFlatMods().values()) if (mod != null && mod.value > 0f) base += mod.value;
        } catch (Throwable ignored) { }
        return base;
    }

    /** Pure: the share of a military building's patrols or a station's strength a shortage takes away (shortage / demand, 0 to 1). */
    static float fleetShare(int shortage, int demand) {
        if (demand <= 0 || shortage <= 0) return 0f;
        return Math.min(1f, shortage / (float) demand);
    }

    // ---------------- the game ----------------

    /** What the colony has of a good without what Local Resources covers right now (that stock runs out). */
    static int rawAvailable(CommodityOnMarketAPI c) {
        int avail = c.getAvailable();
        try {
            MutableStat.StatMod mod = c.getAvailableStat().getFlatMods().get(Submarkets.LOCAL_RESOURCES);
            if (mod != null) avail -= (int) mod.value;
        } catch (Throwable ignored) { }
        return Math.max(0, avail);
    }

    static int demandOf(Industry ind, String com) {
        try {
            MutableCommodityQuantity q = ind.getDemand(com);
            return q == null ? 0 : (int) q.getQuantity().getModifiedValue();
        } catch (Throwable t) { return 0; }
    }

    /** The industry's shortage of a good: its demand less what the colony has (BaseIndustry.getMaxDeficit), Local Resources left out. */
    static int shortage(MarketAPI m, Industry ind, String com) {
        int d = demandOf(ind, com);
        if (d <= 0) return 0;
        CommodityOnMarketAPI c = m.getCommodityData(com);
        return c == null ? 0 : Math.max(0, d - rawAvailable(c));
    }

    static boolean economic(String com) {
        try {
            CommoditySpecAPI sp = Global.getSettings().getCommoditySpec(com);
            return sp != null && !sp.isMeta() && !sp.isNonEcon();
        } catch (Throwable t) { return false; }
    }

    /** A military building (Patrol HQ, Military Base, High Command, or a mod's with their tags) or a station. */
    static boolean military(Industry ind) {
        try {
            if (ind.getSpec().hasTag("station")) return true;
            return ind.getSpec().hasTag("patrol") || ind.getSpec().hasTag("military") || ind.getSpec().hasTag("command");
        } catch (Throwable t) { return false; }
    }

    static boolean hasMilitary(MarketAPI m) {
        for (Industry ind : m.getIndustries()) if (military(ind) && !(ind.isBuilding() && !ind.isUpgrading())) return true;
        return false;
    }

    /**
     * What a colony loses a month to its shortage of a good (credits), Local Resources left out. 0.3.0.0 (C3 fix 2): worked
     * out from the raw shortages only (shortage, outputCut), so the loss counts while Local Resources still covers it.
     */
    static float lostPerMonth(MarketAPI m, String com) {
        float uv = CAEngine.unitValue, lost = 0f;
        for (Industry ind : m.getIndustries()) {
            if (ind.isBuilding() && !ind.isUpgrading()) continue;   // not working yet
            if (demandOf(ind, com) <= 0) continue;
            List<MutableCommodityQuantity> dem = ind.getAllDemand();
            int[] sh = new int[dem.size()];
            int idx = -1;
            for (int i = 0; i < dem.size(); i++) {
                String c = dem.get(i).getCommodityId();
                sh[i] = shortage(m, ind, c);
                if (com.equals(c)) idx = i;
            }
            int back = idx < 0 ? 0 : restored(sh, idx);
            if (back > 0) {
                for (MutableCommodityQuantity sq : ind.getAllSupply()) {
                    String o = sq.getCommodityId();
                    if (o == null || !economic(o)) continue;
                    // 0.3.0.0 (C3 fix 2): the cut from the raw shortages, at most the output's base quantity. While Local
                    // Resources covers a shortage the game adds it to availability and cuts no output, so the output's
                    // quantity now showed no loss and the refill under REFILL_MONTHS never came before the stock ran out
                    float cut = outputCut(back, baseQuantity(sq));
                    if (cut > 0f) lost += CAValue.price(o) * uv * cut;
                }
            }
            if (military(ind) && FLEET_GOODS.contains(com)) {
                float upkeep = 0f;
                try { upkeep = Math.max(0f, ind.getUpkeep().getModifiedValue()); } catch (Throwable ignored) { }
                lost += upkeep * fleetShare(shortage(m, ind, com), demandOf(ind, com));
            }
        }
        return lost;
    }

    /** Units of a good on their way to a colony. */
    static int inTransit(CAData d, String marketId, String com) {
        int n = 0;
        for (Buy b : d.stockpiles()) if (b != null && marketId.equals(b.marketId) && com.equals(b.commodity)) n += Math.max(0, b.qty);
        return n;
    }

    static CargoAPI localResources(MarketAPI m) {
        try { return Misc.getLocalResourcesCargo(m); } catch (Throwable t) { return null; }
    }

    /** Markets to buy at: not hidden, not hostile to you, with an open market; nearest to `to` first. */
    static List<MarketAPI> sources(final MarketAPI to) {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI s : Global.getSector().getEconomy().getMarketsCopy()) {
            try {
                if (s == null || s == to || s.getId().equals(to.getId()) || s.isHidden() || s.getPrimaryEntity() == null) continue;
                if (s.getFaction() == null || s.getFaction().isHostileTo(Factions.PLAYER)) continue;
                if (s.getSubmarket(Submarkets.SUBMARKET_OPEN) == null) continue;
                out.add(s);
            } catch (Throwable ignored) { }
        }
        final Map<String, Float> ly = new HashMap<String, Float>();
        for (MarketAPI s : out) ly.put(s.getId(), CAStores.ly(s, to));
        Collections.sort(out, new Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) { return Float.compare(ly.get(a.getId()), ly.get(b.getId())); }
        });
        return out.size() > MAX_SOURCES ? new ArrayList<MarketAPI>(out.subList(0, MAX_SOURCES)) : out;
    }

    /** The colonies it buys for: Auto colonies, not outposts, not under siege, using their stockpiles for shortages. */
    static List<MarketAPI> colonies() {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        for (MarketAPI m : CAItems.autoMarkets()) {
            try {
                if (CAHypershunt.is(m) || CASiege.held(m) || !m.isUseStockpilesForShortages() || localResources(m) == null) continue;
                out.add(m);
            } catch (Throwable ignored) { }
        }
        return out;
    }

    /** Every purchase worth making now for one kind (military goods at military colonies, or the rest), best value per credit first. */
    static List<Need> needs(CAData d, CASettings s, boolean military) {
        List<Need> out = new ArrayList<Need>();
        long now = CAMark.now();
        for (MarketAPI m : colonies()) out.addAll(needsAt(d, s, m, military, now));
        Collections.sort(out, new Comparator<Need>() {
            public int compare(Need a, Need b) { return Float.compare(b.lost * b.qty / Math.max(1f, b.draw) / Math.max(1f, b.total()), a.lost * a.qty / Math.max(1f, a.draw) / Math.max(1f, a.total())); }
        });
        return out;
    }

    /**
     * The purchases worth making now at one colony for one kind, unsorted. 0.3.0.0 (C3 fix 2): split out of needs() so
     * JTStockpileCheck can run one colony's look.
     */
    static List<Need> needsAt(CAData d, CASettings s, MarketAPI m, boolean military, long now) {
        List<Need> out = new ArrayList<Need>();
        boolean mil = hasMilitary(m);
        CargoAPI lr = localResources(m);
        for (CommodityOnMarketAPI c : m.getAllCommodities()) {
            try {
                String com = c.getId();
                if (c.isNonEcon() || !economic(com) || CABrewery.isDrink(com)) continue;
                if ((mil && MILITARY_GOODS.contains(com)) != military) continue;
                if (c.getMaxDemand() - rawAvailable(c) <= 0) continue;
                if (rejected(m.getId(), com, now)) continue;
                float draw = LocalResourcesSubmarketPlugin.getDeficitMonthlyCommodityUnits(c);
                int qty = toBuy(draw, lr.getCommodityQuantity(com), inTransit(d, m.getId(), com));
                if (qty <= 0) continue;
                float lost = lostPerMonth(m, com);
                if (!(lost > 0f)) continue;
                Need n = best(s, m, com, qty, draw, lost);
                if (n != null) { n.military = military; out.add(n); }
            } catch (Throwable t) { CAStep.warn("money tiers", "stockpile look failed at " + m.getName(), t); }
        }
        return out;
    }

    /** The nearest source that has at least a month's draw (or all that's wanted) and passes the value test; null if none. */
    static Need best(CASettings s, MarketAPI to, String com, int want, float draw, float lost) {
        String name;
        try { name = Global.getSettings().getCommoditySpec(com).getName(); } catch (Throwable t) { name = com; }
        int least = Math.max(1, Math.min(want, (int) Math.ceil(draw)));
        for (MarketAPI src : sources(to)) {
            SubmarketAPI open = src.getSubmarket(Submarkets.SUBMARKET_OPEN);
            CargoAPI cargo = open == null ? null : open.getCargo();
            if (cargo == null) continue;
            int qty = Math.min(want, (int) Math.floor(cargo.getCommodityQuantity(com)));
            if (qty < least) continue;
            float price = src.getSupplyPrice(com, qty, true);
            if (!(price > 0f)) continue;
            price *= 1f + Math.max(0f, open.getTariff());
            float ly = CAStores.ly(src, to), fee = CAStores.fee(s, ly);
            if (!worth(lost, (price + fee) / qty, qty, qty / Math.max(1f, draw))) continue;
            Need n = new Need();
            n.to = to; n.src = src; n.com = com; n.name = name; n.qty = qty; n.price = price; n.fee = fee;
            n.days = CAStores.days(ly); n.lost = lost; n.draw = draw; n.ly = ly;
            return n;
        }
        return null;
    }

    static boolean rejected(String marketId, String com, long now) {
        Long at = CAMark.at(REJECTED + marketId + "|" + com);
        if (at == null) return false;
        if (now >= at && CAMark.days(at, now) < REJECT_DAYS) return true;
        CAMark.clear(REJECTED + marketId + "|" + com);
        return false;
    }

    // ---------------- daily ----------------

    /** Daily from CARich.run: the Stockpiles rung's look. */
    public static void run(CAData d, CASettings s, float allowance) { run(d, s, allowance, false); }

    /**
     * Daily from CARich.run, once for the Stockpiles rung and once for the Military rung's goods (military true): a weekly
     * look (and one on the first day of a month) while the rung is open; buys what's worth it within the allowance, or
     * proposes the best one on Recommend only. 0.3.0.0 (C3 fix): allowance is the rung's room (CARich.room); after the look
     * it says what it still wants (setWant).
     */
    public static void run(CAData d, CASettings s, float allowance, boolean military) {
        int rung = military ? CARich.MILITARY : CARich.STOCKPILES, k = kind(military);
        if (!CARich.open(rung)) {
            pending[k] = null;
            lastWant[k] = 0f;
            return;
        }
        int month = CARich.monthKey();
        String timer = military ? "stockpile military" : "stockpile";
        if (month == lookedMonth[k] && !CATime.due(timer, LOOK_DAYS)) return;
        if (month != lookedMonth[k]) CATime.stamp(timer);
        lookedMonth[k] = month;
        List<Need> needs = needs(d, s, military);
        if (needs.isEmpty()) {
            pending[k] = null;
            setWant(k, 0f);
            if (!military) CARich.note(CARich.STOCKPILES, "no shortage worth buying for this month");
            return;
        }
        if (CARich.mode(rung) == CARich.RECOMMEND) {
            // 0.3.0.0 (C3 fix): the best purchase that fits the room is proposed, and the rung holds what it needs
            Need n = null;
            for (Need x : needs) if (x.total() <= allowance) { n = x; break; }
            if (n == null) {
                pending[k] = null;
                setWant(k, 0f);
                CARich.note(rung, "a stockpile proposal waits for its share of the pool (" + Misc.getDGSCredits(allowance) + " now; "
                        + needs.get(0).name + " for " + needs.get(0).to.getName() + " costs " + Misc.getDGSCredits(needs.get(0).total()) + ")");
                return;
            }
            propose(k, rung, n);
            setWant(k, n.total());
            return;
        }
        // 0.3.0.0 (C3 fix): what it wants after this look: the purchases that fit the room, less what it bought and what's gone
        float want = 0f;
        for (Need n : needs) if (n.total() <= allowance) want += n.total();
        float left = allowance;
        int bought = 0;
        for (Need n : needs) {
            if (bought >= MAX_BUYS) break;
            if (n.total() > left) continue;
            String err = buy(d, s, n);
            if (err == null) { left -= n.total(); bought++; want -= n.total(); }
            else if (err.startsWith("waits")) { CARich.note(rung, n.name + " for " + n.to.getName() + " " + err); break; }
            else want -= n.total();   // the goods are gone from that market: not wanted until the next look
        }
        setWant(k, want);
        if (bought == 0 && !military) CARich.note(CARich.STOCKPILES, "waits for its share of the pool (" + Misc.getDGSCredits(allowance) + " now; "
                + needs.get(0).name + " for " + needs.get(0).to.getName() + " costs " + Misc.getDGSCredits(needs.get(0).total()) + ")");
    }

    /**
     * 0.3.0.0 (C3 fix): what a kind still wants this month. The Stockpiles rung tells CARich (want()); the military goods are
     * one part of the Military rung's want, which CARich.run adds up (militaryWant).
     */
    static void setWant(int k, float credits) {
        lastWant[k] = Math.max(0f, credits);
        if (k == 0) CARich.want(CARich.STOCKPILES, lastWant[k]);
    }

    /** 0.3.0.0 (C3 fix): a kind's proposal (Recommend only), logged once per kind; each kind keeps its own. */
    static void propose(int k, int rung, Need n) {
        pending[k] = n;
        String key = n.to.getId() + "|" + n.com + "|" + n.qty;
        if (!key.equals(loggedKey[k])) {
            loggedKey[k] = key;
            CALog.add(n.to.getName() + ": recommends a stockpile of " + Misc.getWithDGS(n.qty) + " " + n.name + " from " + n.src.getName() + " for "
                    + Misc.getDGSCredits(n.total()) + " with shipping (" + Misc.getDGSCredits(n.lost) + " a month of output). Approve on the Budget panel");
        }
        CARich.note(rung, "proposes " + Misc.getWithDGS(n.qty) + " " + n.name + " for " + n.to.getName() + "; Approve or Reject on this panel");
    }

    /**
     * Buys and ships one purchase. Null when done, else why not (nothing is taken or charged then). 0.3.0.0 (C3 fix): waits
     * when price and fee are more than free cash, so the reserve, production still to pay and the runway stay.
     */
    static String buy(CAData d, CASettings s, Need n) {
        if (CALog.whatIf()) return "not during a what-if valuation";
        String held = CAPlanner.setAside(n.total(), 0f, 0f, null);
        if (held != null) return "waits: " + held;
        if (CABuild.credits() - n.total() < s.reserve) return "waits: it would go into your credit reserve";
        float free = CABudget.freeCash(s);
        if (!(n.total() <= free)) return "waits: free cash (" + Misc.getDGSCredits(Math.max(0f, free)) + ") doesn't cover it";
        SubmarketAPI open = n.src.getSubmarket(Submarkets.SUBMARKET_OPEN);
        CargoAPI cargo = open == null ? null : open.getCargo();
        if (cargo == null || cargo.getCommodityQuantity(n.com) < n.qty) return "the goods are gone from " + n.src.getName();
        cargo.removeCommodity(n.com, n.qty);
        int rung = n.military ? CARich.MILITARY : CARich.STOCKPILES;
        CABudget.payFromPool(s, rung, n.to.getId(), n.price, n.fee);
        Buy b = new Buy();
        b.marketId = n.to.getId(); b.commodity = n.com; b.qty = n.qty; b.cost = n.price; b.fee = n.fee;
        b.sent = CAMark.now(); b.days = n.days; b.from = n.src.getId();
        d.stockpiles().add(b);
        if (pending[kind(n.military)] == n) pending[kind(n.military)] = null;
        CARich.note(rung, Misc.getWithDGS(n.qty) + " " + n.name + " bought for " + n.to.getName());
        CALog.add(n.line());
        CALog.quiet(n.detail());
        return null;
    }

    /** Daily: purchases that have arrived go into Local Resources (to the biggest colony's Autopilot Storage if the colony is gone). */
    public static void deliver(CAData d) {
        if (d.stockpiles == null || d.stockpiles.isEmpty() || CALog.whatIf()) return;
        long now = CAMark.now();
        for (Iterator<Buy> it = d.stockpiles.iterator(); it.hasNext(); ) {
            Buy b = it.next();
            if (b == null || b.commodity == null || b.qty <= 0) { it.remove(); continue; }
            if (CAMark.days(b.sent, now) < b.days && now >= b.sent) continue;
            String name;
            try { name = Global.getSettings().getCommoditySpec(b.commodity).getName(); } catch (Throwable t) { name = b.commodity; }
            MarketAPI to = CAAuto.market(b.marketId);
            CargoAPI lr = to != null && to.isPlayerOwned() ? localResources(to) : null;
            if (lr != null) {
                lr.addCommodity(b.commodity, b.qty);
                left(to, b.commodity, b.qty);
                it.remove();
                CALog.add(to.getName() + ": " + Misc.getWithDGS(b.qty) + " " + name + " arrived in Local Resources");
                continue;
            }
            List<MarketAPI> ms = CAStores.markets();
            if (ms.isEmpty()) continue;   // nowhere to deliver yet; it waits
            MarketAPI alt = ms.get(0);
            CAStores.of(alt).addCommodity(b.commodity, b.qty);
            it.remove();
            CALog.add(alt.getName() + ": " + Misc.getWithDGS(b.qty) + " " + name + " arrived in Autopilot Storage ("
                    + (to == null || !to.isPlayerOwned() ? "the stockpile's colony is no longer yours" : to.getName() + " has no Local Resources now") + ")");
        }
    }

    /** Marks delivered goods as put there by you (LocalResourcesSubmarketPlugin.getLeft), so drawing them costs nothing more. */
    static void left(MarketAPI m, String com, int qty) {
        try {
            SubmarketAPI sub = m.getSubmarket(Submarkets.LOCAL_RESOURCES);
            Object p = sub == null ? null : sub.getPlugin();
            if (p instanceof LocalResourcesSubmarketPlugin) ((LocalResourcesSubmarketPlugin) p).getLeft().addCommodity(com, qty);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "stockpile: marking " + com + " at " + m.getName() + " failed", t); }
    }

    // ---------------- panel (wired by WS-F) ----------------

    /**
     * The Budget panel's stockpile part: what's on its way, and each kind's proposal with its own Approve and Reject
     * (0.3.0.0, C3 fix: button ids APPROVE + kind, REJECT + kind).
     */
    public static void render(TooltipMakerAPI t) {
        if (t == null) return;
        Color hl = Misc.getHighlightColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        CAData d;
        try { d = CAData.get(); } catch (Throwable e) { return; }
        int n = 0;
        float v = 0f;
        for (Buy b : d.stockpiles()) if (b != null) { n++; v += b.cost + b.fee; }
        if (n > 0) t.addPara("Stockpiles: %s shipments on their way to Local Resources (%s paid).", 4f, hl, String.valueOf(n), Misc.getDGSCredits(v));
        for (int k = 0; k < pending.length; k++) {
            Need p = pending[k];
            if (p == null) continue;
            t.addPara(KIND_NAMES[k] + " proposal: %s " + CALog.esc(p.name) + " for " + CALog.esc(p.to.getName()) + " from " + CALog.esc(p.src.getName()) + ", %s with shipping.", 4f, hl,
                    Misc.getWithDGS(p.qty), Misc.getDGSCredits(p.total()));
            t.addButton("Approve", APPROVE + k, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 4f);
            t.addButton("Reject", REJECT + k, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 4f);
        }
    }

    /** Pure, 0.3.0.0 (C3 fix): the kind a button id names (APPROVE + 0 or 1, REJECT + 0 or 1), or -1. */
    static int kindOf(String key, String prefix) {
        if (key == null || !key.startsWith(prefix) || key.length() != prefix.length() + 1) return -1;
        char c = key.charAt(prefix.length());
        return c == '0' ? 0 : c == '1' ? 1 : -1;
    }

    /**
     * Approve or Reject a kind's proposal. Returns a message for the log, "" when it was handled and logged already, or null
     * when the key isn't one of these.
     */
    public static String handle(String key) {
        int k = kindOf(key, APPROVE);
        if (k >= 0) {
            Need n = pending[k];
            if (n == null) return KIND_NAMES[k] + ": no proposal is waiting";
            CAData d = CAData.get();
            // 0.3.0.0 (C3 fix): the rung's room, which its own buyer spends from
            if (n.total() > CARich.room(n.military ? CARich.MILITARY : CARich.STOCKPILES) + 1f) {
                pending[k] = null;
                return KIND_NAMES[k] + ": the proposal no longer fits its share of the pool; a new one comes at the next look";
            }
            String err = buy(d, d.settings, n);
            return err != null ? n.to.getName() + ": stockpile " + err : "";
        }
        k = kindOf(key, REJECT);
        if (k >= 0) {
            Need n = pending[k];
            pending[k] = null;
            if (n == null) return KIND_NAMES[k] + ": no proposal is waiting";
            CAMark.set(REJECTED + n.to.getId() + "|" + n.com, CAMark.now());
            setWant(k, 0f);
            return n.to.getName() + ": stockpile of " + n.name + " rejected; not proposed again for " + Math.round(REJECT_DAYS) + " days";
        }
        return null;
    }
}
