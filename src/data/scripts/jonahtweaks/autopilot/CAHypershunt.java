package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Ashes of the Domain's Coronal Hypershunt colony (Vaults of Knowledge). AotD caps it at size 2 and allows no buildings;
 * its port, patrol HQ and star fortress are built in and hidden. Two things can be managed: restoring its two sections
 * (each restoration is a contract you issue: every month it takes what your exports can spare and bills 10% of base
 * price for it, so a shortfall only slows it down) and the Coronal Wormhole's range, which costs 20,000 credits and 2
 * purified transplutonics a month for every 10 light-years past the first. Hypershunt Receivers (+2 industry slots for
 * every colony in their system while in range) are built in person at a stable location, so they're only recommended.
 * Reflection only; if VoK can't be read, nothing is changed.
 */
public class CAHypershunt {
    public static final String MOD = "aotd_vok";
    static final String PKG = "data.kaysaar.aotd.vok.";
    static final String MEGA = "coronal_hypershunt", WORMHOLE = "coronal_wormhole", COLLECTOR = "coronal_collector";
    static final String RECEIVER = "aotd_hypershunt_receiver", TAP = "coronal_portal";
    static final String TECH_RESTORE = "aotd_tech_megastructure_analysis", TECH_RECEIVER = "aotd_tech_hp_distribution";
    static final float STEP_UPKEEP = 20000f, CONTRACT_CUT = 0.1f;
    static final int STEP_PTM = 2;
    static final String RESTORE = "starting megastructure restorations";

    static Object scriptOf, sections, research, researched, techSpec, techName;
    static Object sStart, sName, sDays, sProgress, sNeeds, sCan, sUpkeep, sRestored, wEffective;
    static Object sId, sRestoring, wRange, wMult, wMax;
    static Class<?> wormholeClass;
    static boolean linked = false, broken = false;

    /** What a restoration contract bills, as a share of base price: VoK's own constant when readable. */
    static float cut() { return CAConst.num("data.kaysaar.aotd.vok.campaign.econ.megastructures.tradecontracts.BaseRestorationContract", "MARGIN_OF_COMMODITY_PRICE", CONTRACT_CUT); }

    /** A Coronal Hypershunt colony: AotD tags its market and gives it this condition. Needs nothing from VoK. */
    public static boolean is(MarketAPI m) {
        return m != null && (m.hasTag("aotd_hypershunt") || m.hasCondition("aotd_coronal_market_cond"));
    }

    static boolean link() {
        if (linked || broken) return linked;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> script = Class.forName(PKG + "campaign.econ.megastructures.models.BaseMegastructureScript", true, cl);
            Class<?> sec = Class.forName(PKG + "campaign.econ.megastructures.models.BaseMegastructureSection", true, cl);
            wormholeClass = Class.forName(PKG + "campaign.econ.megastructures.impl.sections.hypershunt.WormholeGenerator", true, cl);
            Class<?> rm = Class.forName(PKG + "scripts.research.AoTDMainResearchManager", true, cl);
            Class<?> spec = Class.forName(PKG + "scripts.research.models.ResearchOptionSpec", true, cl);
            scriptOf = CARefl.method(script, "getInstanceOfScriptFromEntityIfPresent", SectorEntityToken.class, String.class);
            sections = CARefl.method(script, "getMegaStructureSections");
            research = CARefl.method(rm, "getInstance");
            researched = CARefl.method(rm, "isResearchedForPlayer", String.class);
            techSpec = CARefl.method(rm, "getSpecForSpecificResearch", String.class);
            techName = CARefl.method(spec, "getName");
            sStart = CARefl.method(sec, "startRestoration"); sName = CARefl.method(sec, "getName"); sDays = CARefl.method(sec, "getDaysLeft");
            sProgress = CARefl.method(sec, "getProgressOfRestoration"); sNeeds = CARefl.method(sec, "getMonthlyResNeeded");
            sCan = CARefl.method(sec, "canRestoreSection"); sUpkeep = CARefl.method(sec, "getUpkeepOfSection"); sRestored = CARefl.method(sec, "isRestored");
            sId = CARefl.field(sec, "specID"); sRestoring = CARefl.field(sec, "isRestoring");
            wRange = CARefl.field(wormholeClass, "range"); wMult = CARefl.field(wormholeClass, "mult"); wMax = CARefl.field(wormholeClass, "sections");
            wEffective = CARefl.method(wormholeClass, "getEffectiveRange");
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Vaults of Knowledge's hypershunt; it's left to you", t);
        }
        return linked;
    }

    static boolean researched(String tech) {
        if (!link()) return false;
        try { Object r = CARefl.invoke(research, null); return r != null && Boolean.TRUE.equals(CARefl.invoke(researched, r, tech)); }
        catch (Throwable t) { return false; }
    }

    static String techName(String tech) {
        try {
            Object r = link() ? CARefl.invoke(research, null) : null;
            Object s = r == null ? null : CARefl.invoke(techSpec, r, tech);
            if (s != null) return String.valueOf(CARefl.invoke(techName, s));
        } catch (Throwable ignored) { }
        return tech;
    }

    // ---------------- reading ----------------

    public static class Section {
        public String id, name;
        public boolean restored, restoring, canStart;
        public float progress, daysLeft, upkeep, monthlyCredits;
        public Map<String, Integer> monthly = new LinkedHashMap<String, Integer>();
        Object raw;

        int months() { return Math.max(1, (int) Math.ceil(daysLeft / 30f)); }
        float total() { return monthlyCredits * months(); }
    }

    public static class Shunt {
        public MarketAPI m;
        public Section collector, wormhole;
        public int range = 1, effective = 1, steps = 7, mult = 10;
        public int needed = 1;   // smallest range that reaches every receiver serving your colonies
        public List<SectorEntityToken> serving = new ArrayList<SectorEntityToken>(), idle = new ArrayList<SectorEntityToken>(),
                beyond = new ArrayList<SectorEntityToken>();
        Object wormholeRaw;

        public int reachLY() { return effective * mult; }
        boolean collectorUp() { return collector != null && collector.restored; }
    }

    static String commodityName(String id) {
        CommoditySpecAPI c = Global.getSettings().getCommoditySpec(id);
        return c == null ? id : c.getName();
    }

    /** One megastructure section's state and what restoring the rest of it takes each month (any megastructure). */
    @SuppressWarnings("unchecked")
    static Section section(Object raw) throws Exception {
        Section s = new Section();
        s.raw = raw;
        s.id = String.valueOf(CARefl.get(sId, raw));
        s.name = String.valueOf(CARefl.invoke(sName, raw));
        s.restored = Boolean.TRUE.equals(CARefl.invoke(sRestored, raw));
        s.restoring = CARefl.getBoolean(sRestoring, raw);
        s.canStart = Boolean.TRUE.equals(CARefl.invoke(sCan, raw));
        s.progress = ((Number) CARefl.invoke(sProgress, raw)).floatValue();
        s.daysLeft = ((Number) CARefl.invoke(sDays, raw)).floatValue();
        s.upkeep = ((Number) CARefl.invoke(sUpkeep, raw)).floatValue();
        Map<String, Integer> needs = (Map<String, Integer>) CARefl.invoke(sNeeds, raw);
        if (needs != null) for (Map.Entry<String, Integer> e : needs.entrySet()) {
            CommoditySpecAPI c = Global.getSettings().getCommoditySpec(e.getKey());
            if (e.getValue() == null || e.getValue() <= 0 || c == null) continue;
            s.monthly.put(e.getKey(), e.getValue());
            s.monthlyCredits += c.getBasePrice() * e.getValue() * cut();
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    static Shunt read(MarketAPI m) throws Exception {
        if (!link() || m.getPrimaryEntity() == null) return null;
        Object script = CARefl.invoke(scriptOf, null, m.getPrimaryEntity(), MEGA);
        if (script == null) return null;
        Shunt h = new Shunt();
        h.m = m;
        for (Object raw : (List<Object>) CARefl.invoke(sections, script)) {
            if (raw == null) continue;
            Section s = section(raw);
            if (COLLECTOR.equals(s.id)) h.collector = s;
            else if (WORMHOLE.equals(s.id)) {
                h.wormhole = s;
                if (wormholeClass.isInstance(raw)) {
                    h.wormholeRaw = raw;
                    h.range = CARefl.getInt(wRange, raw);
                    h.mult = Math.max(1, CARefl.getInt(wMult, raw));
                    h.steps = Math.max(1, CARefl.getInt(wMax, raw));
                    h.effective = Math.max(1, ((Number) CARefl.invoke(wEffective, raw)).intValue());
                }
            }
        }
        return h;
    }

    /** Your colonies in this location, other than hypershunts (a receiver gives each +2 industry slots while in range). */
    static List<MarketAPI> colonies(LocationAPI loc) {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        if (loc == null) return out;
        for (MarketAPI m : Global.getSector().getEconomy().getMarkets(loc)) if (m.isPlayerOwned() && !is(m)) out.add(m);
        return out;
    }

    static int stepsTo(Shunt h, SectorEntityToken e) {
        float ly = Misc.getDistanceLY(h.m.getPrimaryEntity(), e);
        return Math.max(1, (int) Math.ceil(ly / h.mult - 0.0001f));
    }

    /** Every hypershunt colony you own, with each built receiver assigned to the nearest working one. */
    static List<Shunt> all() {
        List<Shunt> out = new ArrayList<Shunt>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!is(m)) continue;
            try { Shunt h = read(m); if (h != null) out.add(h); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt read failed at " + m.getName(), t); }
        }
        if (out.isEmpty()) return out;
        for (SectorEntityToken r : Global.getSector().getCustomEntitiesWithTag(RECEIVER)) {
            if (r == null || r.isExpired()) continue;
            Shunt best = null; float bestD = Float.MAX_VALUE;
            for (Shunt h : out) {
                float d = Misc.getDistanceLY(h.m.getPrimaryEntity(), r) - (h.collectorUp() ? 1000f : 0f);   // working ones first
                if (d < bestD) { bestD = d; best = h; }
            }
            if (best == null) continue;
            int steps = stepsTo(best, r);
            if (steps > best.steps) best.beyond.add(r);
            else if (colonies(r.getContainingLocation()).isEmpty()) best.idle.add(r);
            else { best.serving.add(r); best.needed = Math.max(best.needed, steps); }
        }
        return out;
    }

    /** The range Autopilot would set (the smallest that reaches every receiver serving your colonies), or -1 when there's nothing to set. */
    static int target(Shunt h) {
        if (h.wormholeRaw == null || h.wormhole == null || !h.wormhole.restored) return -1;
        return Math.max(1, Math.min(h.steps, h.needed));
    }

    /** The section worth restoring next, or null with the reason in why[0]. */
    static Section nextRestore(Shunt h, String[] why) {
        for (Section s : new Section[]{h.collector, h.wormhole}) {
            if (s != null && s.restoring) { why[0] = s.name + " is being restored (" + (int) Math.ceil(s.daysLeft) + " days left at full deliveries)"; return null; }
        }
        Section s = null;
        if (h.collector != null && !h.collector.restored) {
            if (!researched(TECH_RECEIVER)) {
                why[0] = "the " + h.collector.name + " only pays off through Hypershunt Receivers; research " + techName(TECH_RECEIVER) + " first";
                return null;
            }
            s = h.collector;
        } else if (h.wormhole != null && !h.wormhole.restored) {
            if (h.needed <= 1 && h.beyond.isEmpty()) {
                why[0] = "every receiver serving your colonies is within " + h.mult + " light-years, so the " + h.wormhole.name + " isn't needed yet";
                return null;
            }
            s = h.wormhole;
        }
        if (s == null) { why[0] = "fully restored"; return null; }
        if (!researched(TECH_RESTORE)) { why[0] = "restoring needs " + techName(TECH_RESTORE) + " researched"; return null; }
        if (!s.canStart) { why[0] = s.name + " can't be restored right now"; return null; }
        return s;
    }

    /** Commodities a restoration needs that your colonies have none of to spare, by ToT's figures; null when ToT can't say. */
    static List<String> unsupplied(Section s) {
        List<String> out = new ArrayList<String>();
        for (String com : s.monthly.keySet()) {
            Integer spare = CAContracts.spare(com);
            if (spare == null) return null;
            if (spare <= 0) out.add(commodityName(com));
        }
        return out;
    }

    // ---------------- actions ----------------

    static String start(Shunt h, Section s, String who) { return start(h.m, s, who); }

    /** Starts restoring any megastructure section the way VoK's restore button does, after the checks only that button makes. */
    static String start(MarketAPI m, Section s, String who) {
        if (s.restored) return s.name + " is already restored";
        if (s.restoring) return s.name + " is already being restored";
        if (!researched(TECH_RESTORE)) return "Needs " + techName(TECH_RESTORE) + " researched";
        if (!s.canStart) return s.name + " can't be restored yet (another section comes first)";
        if (!CABreaker.allowed(RESTORE)) return "Paused for this session: restorations didn't start three times";
        try {
            CARefl.invoke(sStart, s.raw);
            if (!CABreaker.check(RESTORE, CARefl.getBoolean(sRestoring, s.raw), m.getName())) return "VoK didn't start the restoration";
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "starting megastructure restoration failed at " + m.getName(), t);
            return "That failed; see starsector.log";
        }
        CAContracts.cached = null;
        CALog.add(m.getName() + ": " + who + " started restoring the " + s.name + ": about " + Misc.getDGSCredits(s.monthlyCredits)
                + " a month for " + s.months() + " months through a restoration contract that draws on your exports"
                + (s.upkeep > 0f ? ", then " + Misc.getDGSCredits(s.upkeep) + " a month upkeep" : ""), true);
        return null;
    }

    static String setRange(Shunt h, int n, String who) {
        if (h.wormholeRaw == null) return "The range can't be read";
        if (h.wormhole == null || !h.wormhole.restored) return "The " + (h.wormhole == null ? "wormhole" : h.wormhole.name) + " isn't restored yet";
        n = Math.max(1, Math.min(h.steps, n));
        if (n == h.range) return "The range is already " + n * h.mult + " light-years";
        try {
            CARefl.set(wRange, h.wormholeRaw, n);
            h.m.reapplyIndustries();
            if (!CABreaker.check("setting the hypershunt range", CARefl.getInt(wRange, h.wormholeRaw) == n, h.m.getName())) return "The range didn't take";
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "setting hypershunt range failed at " + h.m.getName(), t);
            return "That failed; see starsector.log";
        }
        int was = h.range;
        h.range = n;
        CALog.add(h.m.getName() + ": " + who + " set the hypershunt range from " + was * h.mult + " to " + n * h.mult + " light-years ("
                + (n > was ? "+" : "-") + Misc.getDGSCredits(Math.abs(n - was) * STEP_UPKEEP) + " a month upkeep, "
                + (n > was ? "+" : "-") + Math.abs(n - was) * STEP_PTM + " purified transplutonics demand)", true);
        return null;
    }

    static Shunt find(MarketAPI m) {
        for (Shunt h : all()) if (h.m == m) return h;
        return null;
    }

    /** A panel button: restore:sectionId, range:n, or manage (hand the range back to Autopilot). Returns a message. */
    public static String handle(MarketAPI m, String action, String arg) {
        Shunt h = find(m);
        if (h == null) return "The hypershunt can't be read";
        CAData.Colony c = CAData.get().colony(m.getId());
        if ("restore".equals(action)) {
            Section s = COLLECTOR.equals(arg) ? h.collector : WORMHOLE.equals(arg) ? h.wormhole : null;
            if (s == null) return "Unknown section";
            String err = start(h, s, "you");
            return err == null ? "Restoration started" : err;
        }
        if ("range".equals(action)) {
            int n;
            try { n = Integer.parseInt(arg); } catch (Throwable t) { return "Bad range"; }
            String err = setRange(h, n, "you");
            if (err == null) { c.hsRange = h.range; c.hsManual = false; }
            return err == null ? "Range set" : err;
        }
        if ("manage".equals(action)) { c.hsManual = false; c.hsRange = null; return "Autopilot manages the range again"; }
        return "Unknown action";
    }

    // ---------------- daily pass ----------------

    /**
     * Follow colony mode: an Auto hypershunt keeps its range at the smallest that reaches every receiver serving your
     * colonies and starts the next useful restoration when it fits the budget; Approve logs the recommendation once.
     */
    public static void run(CAData d, CASettings s) {
        if (s.hypershuntMode != 0) return;
        for (Shunt h : all()) {
            CAData.Colony c = d.colony(h.m.getId());
            if (c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) continue;
            boolean auto = c.mode == CAData.Mode.AUTO;
            try {
                range(h, c, s, auto);
                restore(h, c, s, auto);
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt step failed at " + h.m.getName(), t);
            }
        }
    }

    static void range(Shunt h, CAData.Colony c, CASettings s, boolean auto) {
        int t = target(h);
        if (t < 0) return;
        // a range that isn't the default and isn't what Autopilot set was chosen by you
        if (!c.hsManual && h.range != t && ((c.hsRange == null && h.range != 1) || (c.hsRange != null && c.hsRange != h.range))) {
            c.hsManual = true;
            CALog.add(h.m.getName() + ": you set the hypershunt range yourself; Autopilot leaves it (Let Autopilot manage it, in the colony panel)");
        }
        if (c.hsManual || t == h.range) return;
        String key = "hsrange:" + h.m.getId() + ":" + t;
        if (!auto) {
            if (CALog.once(key)) CALog.add(h.m.getName() + ": recommends a hypershunt range of " + t * h.mult + " light-years (now "
                    + h.range * h.mult + "); set it in the colony panel", true);
            return;
        }
        float more = (t - h.range) * STEP_UPKEEP;
        if (more > 0f && s.upkeepAdded + more > s.newUpkeepCap) {
            if (CALog.once(key + ":upkeep")) CALog.add(h.m.getName() + ": a hypershunt range of " + t * h.mult + " light-years waits for next month (new upkeep cap reached)");
            return;
        }
        if (setRange(h, t, "Auto") == null) {
            c.hsRange = h.range;
            if (more > 0f) CABudget.charge(s, 0f, 0f, more);
        }
    }

    static void restore(Shunt h, CAData.Colony c, CASettings s, boolean auto) {
        Section next = nextRestore(h, new String[1]);
        if (next != null) restore(h.m, next, s, auto);
    }

    /**
     * Auto starts a restoration only when every resource it needs is in surplus, the whole cost fits the per-project cap,
     * the first month fits the budget and its upkeep fits the new-upkeep cap; otherwise (and in Approve) it's logged once.
     */
    static void restore(MarketAPI m, Section next, CASettings s, boolean auto) {
        String key = "hsrestore:" + m.getId() + ":" + next.id;
        List<String> none = unsupplied(next);
        if (none == null || !none.isEmpty()) {
            if (CALog.once(key + ":supply")) CALog.add(m.getName() + ": restoring the " + next.name + " waits: "
                    + (none == null ? "Theory of Toolbox has no production figures yet" : "your colonies have no spare " + CAContracts.join(none)));
            return;
        }
        String cost = "about " + Misc.getDGSCredits(next.monthlyCredits) + " a month for " + next.months() + " months";
        if (!auto) {
            if (CALog.once(key)) CALog.add(m.getName() + ": recommends restoring the " + next.name + " (" + cost + "); Start in the colony panel", true);
            return;
        }
        if (next.total() > s.perProjectCap) {
            if (CALog.once(key + ":cap")) CALog.add(m.getName() + ": restoring the " + next.name + " (" + cost + ") is over the per-project cap; Start it in the colony panel", true);
            return;
        }
        if (next.monthlyCredits > CABudget.available(s)) {
            if (CALog.once(key + ":budget")) CALog.add(m.getName() + ": restoring the " + next.name + " waits for budget (" + cost + ")");
            return;
        }
        if (s.upkeepAdded + next.upkeep > s.newUpkeepCap) {
            if (CALog.once(key + ":upkeep")) CALog.add(m.getName() + ": restoring the " + next.name + " waits for next month (new upkeep cap reached)");
            return;
        }
        if (start(m, next, "Auto") == null) CABudget.charge(s, next.monthlyCredits, 0f, next.upkeep);
    }

    // ---------------- receiver spots ----------------

    public static class Spot {
        public String system;
        public int colonies, steps, stable, mult = 10;
        public float ly;
        public boolean reachable;
    }

    /** Systems with your colonies and no receiver, nearest hypershunt first by colonies served. */
    static List<Spot> spots(List<Shunt> shunts) {
        List<Spot> out = new ArrayList<Spot>();
        if (shunts.isEmpty()) return out;
        Set<LocationAPI> withReceiver = new HashSet<LocationAPI>();
        for (SectorEntityToken r : Global.getSector().getCustomEntitiesWithTag(RECEIVER)) if (r != null && !r.isExpired()) withReceiver.add(r.getContainingLocation());
        for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
            if (withReceiver.contains(sys)) continue;
            List<MarketAPI> mine = colonies(sys);
            if (mine.isEmpty() || mine.get(0).getPrimaryEntity() == null) continue;
            Spot sp = new Spot();
            sp.system = sys.getBaseName();
            sp.colonies = mine.size();
            sp.ly = Float.MAX_VALUE;
            for (Shunt h : shunts) {
                float ly = Misc.getDistanceLY(h.m.getPrimaryEntity(), mine.get(0).getPrimaryEntity());
                if (ly >= sp.ly) continue;
                sp.ly = ly; sp.mult = h.mult; sp.steps = stepsTo(h, mine.get(0).getPrimaryEntity()); sp.reachable = sp.steps <= h.steps;
            }
            sp.stable = sys.getEntitiesWithTag("stable_location").size();
            out.add(sp);
        }
        Collections.sort(out, new Comparator<Spot>() {
            public int compare(Spot a, Spot b) {
                if (a.reachable != b.reachable) return a.reachable ? -1 : 1;
                if (a.colonies != b.colonies) return b.colonies - a.colonies;
                return Float.compare(a.ly, b.ly);
            }
        });
        return out;
    }

    static int taps() {
        try {
            int n = (int) Global.getSector().getPlayerFleet().getCargo().getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(TAP, null));
            return n + (int) CAData.get().storage().getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(TAP, null));
        } catch (Throwable t) { return 0; }
    }

    // ---------------- panels ----------------

    /** One line for the empire panel's colony list. */
    public static String summary(MarketAPI m) {
        try {
            Shunt h = find(m);
            if (h == null) return "Coronal Hypershunt (can't be read)";
            String[] why = new String[1];
            Section next = nextRestore(h, why);
            if (next != null) return "Coronal Hypershunt: next, restore the " + next.name;
            return "Coronal Hypershunt: " + why[0] + "; range " + h.reachLY() + " ly";
        } catch (Throwable t) { return "Coronal Hypershunt"; }
    }

    static void section(TooltipMakerAPI t, Shunt h, Section s, boolean buttons, Object restoreId) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        if (s == null) return;
        if (s.restored) { t.addPara("%s: restored" + (s.upkeep > 0f ? ", " + Misc.getDGSCredits(s.upkeep) + " a month upkeep." : "."), 6f, hl, s.name); return; }
        if (s.restoring) {
            t.addPara("%s: restoring, %s done, %s days left at full deliveries, about %s a month.", 6f, hl, s.name,
                    Math.round(s.progress * 100f) + "%", "" + (int) Math.ceil(s.daysLeft), Misc.getDGSCredits(s.monthlyCredits));
        } else {
            t.addPara("%s: not restored. Restoring takes about %s a month for %s months.", 6f, hl, s.name, Misc.getDGSCredits(s.monthlyCredits), "" + s.months());
        }
        List<String> needs = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : s.monthly.entrySet()) {
            Integer spare = CAContracts.spare(e.getKey());
            needs.add(Misc.getWithDGS(e.getValue()) + " " + commodityName(e.getKey()) + (spare == null ? "" : " (" + Misc.getWithDGS(Math.max(0, spare)) + " spare)"));
        }
        if (!needs.isEmpty()) t.addPara("    Each month: " + CALog.esc(CAContracts.join(needs)), gray, 2f);
        if (buttons && !s.restoring) {
            com.fs.starfarer.api.ui.ButtonAPI b = t.addButton("Start restoration", restoreId, base, dark, Alignment.MID, CutStyle.ALL, 200f, 22f, 4f);
            b.setEnabled(s.canStart && researched(TECH_RESTORE));
        }
    }

    static String where(SectorEntityToken e) {
        LocationAPI loc = e.getContainingLocation();
        return loc instanceof StarSystemAPI ? ((StarSystemAPI) loc).getBaseName() : loc == null ? "?" : loc.getName();
    }

    static void receivers(TooltipMakerAPI t, Shunt h) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor();
        for (SectorEntityToken r : h.serving) {
            float ly = Misc.getDistanceLY(h.m.getPrimaryEntity(), r);
            boolean on = h.collectorUp() && ly <= h.reachLY();
            t.addPara("Receiver in %s, %s ly: %s, %s colonies get +2 industry slots.", 4f, on ? hl : bad, where(r), "" + Math.round(ly),
                    on ? "working" : !h.collectorUp() ? "idle until the Coronal Collector is restored" : "out of range", "" + colonies(r.getContainingLocation()).size());
        }
        for (SectorEntityToken r : h.idle) t.addPara("Receiver in %s: no colony of yours there yet.", 4f, gray, where(r));
        for (SectorEntityToken r : h.beyond) t.addPara("Receiver in %s: %s ly away, past the longest range (%s ly).", 4f, bad, where(r),
                "" + Math.round(Misc.getDistanceLY(h.m.getPrimaryEntity(), r)), "" + h.steps * h.mult);
    }

    /** The colony panel's section for a hypershunt colony. */
    public static void renderColony(TooltipMakerAPI t, MarketAPI m, CAData.Colony c, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        t.addPara("Ashes of the Domain caps this colony at size 2 and allows no buildings, so Autopilot builds nothing here. Its port, "
                + "patrol HQ and star fortress are built in and hidden; Autopilot never touches hidden buildings.", gray, 6f);
        if (!Global.getSettings().getModManager().isModEnabled(MOD)) return;
        Shunt h = find(m);
        if (h == null) { t.addPara("The hypershunt's state can't be read; see starsector.log.", gray, 6f); return; }
        boolean buttons = s.hypershuntMode != 2;
        section(t, h, h.collector, buttons, CAColonyDialog.HS + "restore:" + COLLECTOR);
        section(t, h, h.wormhole, buttons, CAColonyDialog.HS + "restore:" + WORMHOLE);
        if (!researched(TECH_RESTORE)) t.addPara("Restoring sections needs %s researched.", 4f, hl, techName(TECH_RESTORE));
        int tgt = target(h);
        t.addPara("Range: %s light-years (%s set), upkeep %s and %s purified transplutonics a month.", 8f, hl, "" + h.reachLY(),
                "" + h.range * h.mult, Misc.getDGSCredits((h.effective - 1) * STEP_UPKEEP), "" + (h.range - 1) * STEP_PTM);
        if (h.effective < h.range) t.addPara("Short of purified transplutonics, so the range is cut to %s light-years.", 4f, Misc.getNegativeHighlightColor(), "" + h.reachLY());
        if (tgt > 0 && tgt != h.range) {
            t.addPara("Receivers serving your colonies need %s light-years.", 4f, hl, "" + tgt * h.mult);
            if (buttons) t.addButton("Set range to " + tgt * h.mult + " ly", CAColonyDialog.HS + "range:" + tgt, base, dark, Alignment.MID, CutStyle.ALL, 200f, 22f, 4f);
        }
        if (c.hsManual) {
            t.addPara("You set the range yourself, so Autopilot leaves it.", gray, 4f);
            t.addButton("Let Autopilot manage it", CAColonyDialog.HS + "manage:x", base, dark, Alignment.MID, CutStyle.ALL, 200f, 22f, 4f);
        }
        receivers(t, h);
        String[] why = new String[1];
        Section next = nextRestore(h, why);
        t.addPara(next != null ? "Next: restore the " + CALog.esc(next.name) + "." : "Next: nothing to restore; " + CALog.esc(why[0]) + ".", hl, 8f);
        String when = s.hypershuntMode == 2 ? "Hypershunt management is off in Settings."
                : s.hypershuntMode == 1 ? "Hypershunt is set to Recommend only, so nothing changes until you click."
                : c.mode == CAData.Mode.AUTO ? "Auto sets the range and starts restorations within the budget."
                : "Switch this colony to Auto to have the range and restorations handled.";
        t.addPara(when, gray, 4f);
    }

    /** The empire panel's section: every hypershunt and where receivers would pay off. */
    public static void renderEmpire(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        List<Shunt> shunts = all();
        t.addPara("Mode: %s. Keeps each hypershunt's range at the smallest that reaches every receiver serving your colonies and "
                + "restores the next useful section within the budget.", 6f, hl, CASettings.AUGMENT_NAMES[Math.max(0, Math.min(2, s.hypershuntMode))]);
        for (Shunt h : shunts) {
            CAData.Colony c = d.colony(h.m.getId());
            String[] why = new String[1];
            Section next = nextRestore(h, why);
            t.addPara("%s (%s): range %s ly, %s receivers working. %s", 8f, hl, h.m.getName(), c.mode.label, "" + h.reachLY(),
                    "" + working(h), next != null ? "Next: restore the " + next.name + "." : "Next: " + why[0] + ".");
            if (next != null && s.hypershuntMode != 2 && !next.restoring)
                t.addButton("Start restoration", new String[]{"hs", "restore", h.m.getId(), next.id}, base, dark, Alignment.MID, CutStyle.ALL, 200f, 22f, 4f);
            int tgt = target(h);
            if (tgt > 0 && tgt != h.range && s.hypershuntMode != 2)
                t.addButton("Set range to " + tgt * h.mult + " ly", new String[]{"hs", "range", h.m.getId(), "" + tgt}, base, dark, Alignment.MID, CutStyle.ALL, 200f, 22f, 4f);
        }
        if (!researched(TECH_RECEIVER)) {
            t.addPara("Research %s to build Hypershunt Receivers: +2 industry slots for every colony in the receiver's system.", 8f, hl, techName(TECH_RECEIVER));
            return;
        }
        List<Spot> spots = spots(shunts);
        if (spots.isEmpty()) return;
        t.addPara("Where a receiver would pay off (build one in person at a stable location: 1 Coronal Hypershunt Tap, 1,000 refined metal, "
                + "100 Domain-era machinery; you have %s taps):", 8f, hl, "" + taps());
        int shown = 0;
        for (Spot sp : spots) {
            if (shown++ >= 5) break;
            String need = sp.steps <= 1 ? "in range now" : sp.reachable ? "needs a " + sp.steps * sp.mult + " ly range" : "too far";
            t.addPara("    %s: %s colonies, %s ly, %s, %s stable locations", 3f, sp.reachable ? hl : gray, sp.system, "" + sp.colonies,
                    "" + Math.round(sp.ly), need, "" + sp.stable);
        }
    }

    static int working(Shunt h) {
        if (!h.collectorUp()) return 0;
        int n = 0;
        for (SectorEntityToken r : h.serving) if (Misc.getDistanceLY(h.m.getPrimaryEntity(), r) <= h.reachLY()) n++;
        return n;
    }
}
