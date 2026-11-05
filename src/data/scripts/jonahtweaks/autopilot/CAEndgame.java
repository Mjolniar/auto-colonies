package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Endgame economy (2.36.0). Once money is no longer the problem (spending freely, 10M or more over the surplus line, and last
 * month's net income at least the setting's level, 1M by default), what a building is worth changes at every colony that
 * spends the surplus:
 * - a credit counts MONEY (5%) instead of a quarter: income hardly matters any more;
 * - defense, fleets, ship quality, stability, access and growth count at least ENDGAME_WEIGHTS (Security and War production's
 *   highs, fleets and quality higher still), or the colony goal's own if higher;
 * - every commodity a building makes is valued as a production chain (chainValue): a good none of your colonies makes is
 *   worth NEW_CHAIN plus its units, units that cover your own colonies' shortfall count 1.5x, the rest count by how much of
 *   the sector's production isn't yours yet (toward a monopoly), and a second colony making a good only one makes adds
 *   SECOND_SOURCE, so production spreads over your worlds. War goods count 1.5x, Ashes of the Domain's advanced goods 2x;
 * - the plan only adds priority for what can't be had another way: Terraforming Made Easy's structures and special-world
 *   buildings, special project producers, maxed stations and defenses, the Bifrost Control Center. A money plan's Commerce
 *   or Light Industry is valued like anything else;
 * - with no free industry slot, an Auto colony replaces its weakest industry when the best one it has no slot for beats
 *   it by REPLACE_MARGIN (one a day across the empire, one a month per colony; never a plan or project building, a military
 *   one (fleets, ship quality, defense), a story point improvement, one you locked, one it built in the last year, nor a
 *   good's last producer while you'd be short).
 *   Its AI core and item go to Autopilot Storage, to be placed again;
 * - Research Facilities go up while any research is left, whatever the money says; colony items and AI cores are placed
 *   with these weights.
 * What it may cost in income: last month's net income may fall to KEEP (a third) of what it was when the endgame started
 * (3M a month keeps 1M); each month's changes that earn less draw on that room, and wait once it's used. It ends when the
 * surplus falls under 5M or net income goes below zero, and starts again while spending freely at the income it keeps.
 * Between 5M and 10M it stays on but, like spending freely, only the focus world spends.
 */
public class CAEndgame {
    /** Net income a month needed to start, by setting (the last one is Off). */
    public static final float[] ENTER_NET = {1000000f, 2000000f, 500000f};
    public static final String[] NAMES = {"From 1,000,000/month", "From 2,000,000/month", "From 500,000/month", "Off"};
    static final float KEEP = 1f / 3f;
    static final float MONEY = 0.05f;
    //                                          credits stab   access  defense fleet   quality growth
    static final float[] ENDGAME_WEIGHTS = {MONEY, 4000f, 10000f, 15000f, 40000f, 40000f, 2000f};
    static final float NEW_CHAIN = 40000f, SECOND_SOURCE = 10000f, PER_UNIT = 2000f, REPLACE_MARGIN = 10000f;
    static final float REPLACE_COLONY_DAYS = 30f, MEMORY_DAYS = 365f;
    static final Set<String> WAR_GOODS = new HashSet<String>(Arrays.asList("ships", "hand_weapons", "supplies", "fuel", "heavy_machinery",
            "metals", "rare_metals", "marines"));
    static float lastReplaceDay = -99f;
    static String summary;

    // ---------------- pure rules (JTEndgameCheck) ----------------

    /**
     * Whether the endgame runs. It starts while spending freely with net income at the setting's level, or, once it has run,
     * at the income it keeps (so the room it used doesn't stop it coming back). Once on, it stays on until the surplus is under
     * half the spending-freely line or net income goes below zero, so a surplus hovering around 10M doesn't flip it.
     */
    static boolean decide(boolean on, int mode, boolean rich, float amount, float net, float refNet) {
        if (mode < 0 || mode >= ENTER_NET.length) return false;
        if (on) return amount >= CASurplus.RICH / 2f && net >= 0f;
        float need = refNet > 0f ? Math.min(ENTER_NET[mode], floor(refNet)) : ENTER_NET[mode];
        return rich && net >= need;
    }

    /** Net income the endgame keeps: a third of what it was when it started. */
    static float floor(float refNet) { return KEEP * Math.max(0f, refNet); }

    /** Income a month the endgame may still give up this month. */
    static float room(float net, float refNet, float lostThisMonth) { return Math.max(0f, net - floor(refNet) - Math.max(0f, lostThisMonth)); }

    /** Weights: the endgame's, or the colony goal's where higher; a credit counts MONEY. */
    static float[] weights(float[] goal) {
        float[] w = new float[7];
        w[0] = MONEY;
        for (int i = 1; i < 7; i++) w[i] = Math.max(goal[i], ENDGAME_WEIGHTS[i]);
        return w;
    }

    /**
     * What `units` more of a good are worth a month, given what your colonies make (mine) and need (demand), what the whole
     * sector makes (sector, yours included), how many of your colonies make it and whether this one does; tier 1, 1.5
     * (war goods) or 2 (advanced goods).
     */
    static float chainValue(int units, float mine, float demand, float sector, int producers, boolean producesHere, float tier) {
        if (units <= 0) return 0f;
        if (mine <= 0f) return tier * (NEW_CHAIN + units * PER_UNIT * 2f);
        float cover = Math.min(units, Math.max(0f, demand - mine));
        float rest = units - cover;
        float share = Math.min(1f, mine / Math.max(1f, sector));
        float v = cover * PER_UNIT * 1.5f + rest * PER_UNIT * (1f - share) * 0.5f;
        if (producers == 1 && !producesHere) v += SECOND_SOURCE;
        return tier * v;
    }

    /** Replace when the new industry's score beats what the old one is worth by the margin. */
    static boolean beats(float newScore, float keepValue) { return newScore - keepValue > REPLACE_MARGIN; }

    // ---------------- state ----------------

    public static boolean on(CASettings s) { return s.endgameOn; }

    /** The endgame's valuation applies here: it's on, and this colony spends the surplus. */
    public static boolean at(MarketAPI m) { return m != null && on(CAData.settings()) && CASurplus.spends(m); }

    static float floor(CASettings s) { return floor(s.endgameRefNet); }

    static float room(CASettings s) { return room(CABudget.netIncome(s), s.endgameRefNet, s.endgameLoss); }

    static boolean lossFits(CASettings s, float loss) { return loss <= 0f || loss <= room(s); }

    static void charge(CASettings s, float loss) { if (loss > 0f) s.endgameLoss += loss; }

    static float now() { return CADay.now(); }

    /** Daily: starts or ends the endgame, with a log line each way. */
    public static void daily(CASettings s) {
        boolean rich = CASurplus.rich(s);
        float net = CABudget.netIncome(s), amount = CASurplus.amount(s);
        boolean next = decide(s.endgameOn, s.endgameMode, rich, amount, net, s.endgameRefNet);
        if (next && !s.endgameOn) {
            s.endgameOn = true;
            s.endgameRefNet = Math.max(s.endgameRefNet, net);   // coming back doesn't lower what it keeps
            chains = null;
            tidy(CAData.get());
            CALog.add("Endgame economy: " + Misc.getDGSCredits(net) + "/month and " + Misc.getDGSCredits(CASurplus.amount(s))
                    + " surplus; now building for production chains, military, research. Keeps " + Misc.getDGSCredits(floor(s)) + "/month", true);
        } else if (!next && s.endgameOn) {
            s.endgameOn = false;
            chains = null;
            CALog.add("Endgame economy ended (" + (s.endgameMode >= ENTER_NET.length ? "turned off" : amount < CASurplus.RICH / 2f ? "surplus under "
                    + Misc.getDGSCredits(CASurplus.RICH / 2f) : "net income below zero") + ")", true);
        }
        summary = s.endgameOn ? summaryText(s) : null;
    }

    // ---------------- production chains ----------------

    static class Chains {
        float day;
        final Map<String, Float> mine = new HashMap<String, Float>(), demand = new HashMap<String, Float>(), sector = new HashMap<String, Float>();
        final Map<String, Set<String>> producers = new HashMap<String, Set<String>>();
        final Map<String, Map<String, Float>> byMarket = new HashMap<String, Map<String, Float>>();

        float get(Map<String, Float> m, String k) { Float v = m.get(k); return v == null ? 0f : v; }

        void addMine(String marketId, String com, float units) {
            if (units <= 0f) return;
            mine.put(com, get(mine, com) + units);
            sector.put(com, get(sector, com) + units);
            Set<String> p = producers.get(com);
            if (p == null) producers.put(com, p = new HashSet<String>());
            p.add(marketId);
            Map<String, Float> at = byMarket.get(marketId);
            if (at == null) byMarket.put(marketId, at = new HashMap<String, Float>());
            at.put(com, get(at, com) + units);
        }
    }

    static Chains chains;

    static boolean economic(CommoditySpecAPI c) {
        return c != null && !c.isMeta() && !c.isNonEcon() && !c.hasTag("nonecon");
    }

    /** What your colonies and the sector make and need, worked out once a day (and kept up to date as builds start). */
    static Chains chains() {
        if (chains != null && now() - chains.day < 1f && now() >= chains.day) return chains;
        Chains c = new Chains();
        c.day = now();
        Set<String> mineIds = new HashSet<String>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (CAEngine.isOutpost(m)) continue;
            mineIds.add(m.getId());
            for (CommodityOnMarketAPI com : m.getAllCommodities()) {
                if (!economic(com.getCommodity())) continue;
                c.addMine(m.getId(), com.getId(), com.getMaxSupply());
                if (com.getMaxDemand() > 0) c.demand.put(com.getId(), c.get(c.demand, com.getId()) + com.getMaxDemand());
            }
            // buildings under construction count as made already, so two colonies don't start the same missing good
            for (Industry ind : m.getIndustries()) {
                if (!ind.isBuilding() || ind.isUpgrading()) continue;
                try {
                    CAEngine.Pick p = CAEngine.effectsOf(m, ind.getId(), Collections.<String, Integer>emptyMap());
                    if (p != null) for (Map.Entry<String, Integer> e : p.supplied.entrySet()) c.addMine(m.getId(), e.getKey(), e.getValue());
                } catch (Throwable ignored) { }
            }
        }
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (mineIds.contains(m.getId()) || m.isHidden()) continue;
            for (CommodityOnMarketAPI com : m.getAllCommodities())
                if (com.getMaxSupply() > 0 && economic(com.getCommodity())) c.sector.put(com.getId(), c.get(c.sector, com.getId()) + com.getMaxSupply());
        }
        chains = c;
        return c;
    }

    /** A build started: its goods count as made from now on. */
    static void note(MarketAPI m, CAEngine.Pick p) {
        if (chains == null || m == null || p == null) return;
        for (Map.Entry<String, Integer> e : p.supplied.entrySet()) chains.addMine(m.getId(), e.getKey(), e.getValue());
    }

    static float tier(String com) {
        for (String g : CAProjects.AOTD_GOODS) if (g.equals(com)) return 2f;
        return WAR_GOODS.contains(com) ? 1.5f : 1f;
    }

    static String name(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getName(); } catch (Throwable t) { return com; }
    }

    /**
     * The production-chain value of what a pick makes (p.supplied), with a line each. removing: the value of the goods an
     * existing building makes here, as if it were gone (so a good's last producer is worth a new chain).
     */
    static float chainValue(MarketAPI m, CAEngine.Pick p, boolean removing) {
        if (p.supplied.isEmpty()) return 0f;
        Chains c = chains();
        float total = 0f;
        for (Map.Entry<String, Integer> e : p.supplied.entrySet()) {
            String com = e.getKey();
            int units = e.getValue();
            if (!economic(Global.getSettings().getCommoditySpec(com))) continue;
            float mine = c.get(c.mine, com), sector = c.get(c.sector, com);
            Set<String> prod = c.producers.get(com);
            int producers = prod == null ? 0 : prod.size();
            boolean here = prod != null && prod.contains(m.getId());
            if (removing) {
                mine = Math.max(0f, mine - units);
                sector = Math.max(0f, sector - units);
                Map<String, Float> at = c.byMarket.get(m.getId());
                float hereUnits = at == null ? 0f : c.get(at, com);
                if (here && hereUnits <= units) { producers--; here = false; }
            }
            float v = chainValue(units, mine, c.get(c.demand, com), sector, producers, here, tier(com));
            if (v <= 0f) continue;
            total += v;
            String what = mine <= 0f ? "a good none of your colonies make" : mine < c.get(c.demand, com) ? "your colonies are short"
                    : Math.round(100f * Math.min(1f, mine / Math.max(1f, sector))) + "% of the sector's is yours";
            p.lines.add("Endgame: " + name(com) + " (" + what + ") " + CAEngine.valueText(v));
        }
        return total;
    }

    /** Production chains none of your colonies make that the sector does, for the panel. */
    static List<String> missing() {
        Chains c = chains();
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Float> e : c.sector.entrySet()) if (e.getValue() > 0f && c.get(c.mine, e.getKey()) <= 0f) out.add(name(e.getKey()));
        Collections.sort(out);
        return out;
    }

    // ---------------- the plan ----------------

    /** In the endgame the plan only adds priority for what can't be had another way. */
    static boolean keepsPlanBonus(MarketAPI m, CAPlan.Plan plan, String id) {
        if (id == null) return false;
        if (id.startsWith("tme_") || CAPlan.WORLD_STEPS.contains(id) || (plan != null && plan.exact.contains(id))) return true;
        if (CAProjects.isStep(m, id) || CASurplus.isRichStep(m, id)) return true;
        return CAWonders.BFC.equals(id);
    }

    /** Open plan industries the endgame still keeps a slot for. */
    static int openKeptIndustrySteps(MarketAPI m, CAPlan.Plan plan) {
        int n = 0;
        for (CAPlan.Step st : plan.steps) {
            if (st.kind != CAPlan.Kind.BUILD || st.done(m) || !keepsPlanBonus(m, plan, st.id) || !CAEngine.isIndustry(st.id)) continue;
            n++;
        }
        return n;
    }

    // ---------------- replacing industries ----------------

    static class Weakest { Industry ind; float keep, money, upkeep; }

    /** Why the new industry couldn't start once the old one is down (checked first, so nothing is taken down for nothing); null if it can. */
    static String cantStart(MarketAPI m, CAEngine.Pick p) {
        CASettings s = CAData.settings();
        if (CABuild.credits() < p.cost + CABudget.constructionFee(s, p.cost)) return "the credits aren't there";
        if (!CADiagnostics.usable(p.id)) return "Autopilot can't build it";
        if (!CABreaker.allowed("building " + p.name)) return "building it is paused this session";
        if (CAAotD.enabled() && !CAAotD.allows(p.id, m)) return "research hasn't unlocked it";
        return null;
    }

    static boolean protectedHere(MarketAPI m, CAData.Colony col, CAPlan.Plan plan, Industry ind, CAData d) {
        String id = ind.getId();
        if (!ind.isIndustry() || ind.isHidden() || ind.isBuilding() || ind.isUpgrading() || ind.isImproved() || !ind.canShutDown()) return true;
        if (ind.getSpec().hasTag("population") || ind.getSpec().hasTag("station") || col.locks.contains(id)) return true;
        if (keepsPlanBonus(m, plan, id) || CASurplusResearch.FACILITY.equals(id) || CASurplusResearch.BLACK_SITE.equals(id) || CAWonders.SITE.equals(id)) return true;
        if (!CAEngine.isSupportedVanilla(id) && !CAEstimates.has(id)) return true;   // only what the engine can value for certain
        Long built = d.endgameBuilt().get(m.getId() + ":" + id);
        return built != null && Global.getSector().getClock().getElapsedDaysSince(built) < MEMORY_DAYS;
    }

    /** The industry here worth least in the endgame that may be replaced, or null. */
    static Weakest weakest(MarketAPI m, CAData.Colony col, CAPlan.Plan plan, CAData.Profile profile) {
        CAData d = CAData.get();
        float[] w = weights(CAEngine.WEIGHTS[profile.ordinal()]);
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        Weakest best = null;
        for (Industry ind : m.getIndustries()) {
            if (protectedHere(m, col, plan, ind, d)) continue;
            CAEngine.Pick p = CAEngine.effectsOf(m, ind.getId(), deficit);
            if (p == null) continue;
            // fleets, ship quality, defense and ship production are what the endgame builds up
            if (p.fx[3] > 0f || p.fx[4] > 0f || p.fx[5] > 0f || p.supplied.containsKey("ships")) continue;
            CAEngine.upkeep(m, ind.getSpec(), null, p);
            float v = 0f;
            for (int i = 0; i < 7; i++) v += w[i] * p.fx[i];
            v += chainValue(m, p, true);
            if (best == null || v < best.keep) { best = new Weakest(); best.ind = ind; best.keep = v; best.money = p.fx[0]; best.upkeep = p.upkeep; }
        }
        return best;
    }

    /** Whether a replacement may happen today here (one a day empire-wide, one a month per colony). */
    static boolean replaceAllowed(MarketAPI m, CASettings s) {
        if (s.endgameNoReplace || now() - lastReplaceDay < 1f && now() >= lastReplaceDay) return false;
        Long last = CAData.get().endgameBuilt().get(m.getId() + ":replaced");
        return last == null || Global.getSector().getClock().getElapsedDaysSince(last) >= REPLACE_COLONY_DAYS;
    }

    /** A replacement candidate for this colony: the weakest industry the best slot-less pick beats, or null. */
    static Weakest replacement(MarketAPI m, CAData.Colony col, CAEngine.Pick best, CAData.Profile profile) {
        if (best == null || best.measured || !at(m) || !replaceAllowed(m, CAData.settings())) return null;
        Long gone = CAData.get().endgameBuilt().get(m.getId() + ":-" + best.id);   // what it took down isn't built again there for a year
        if (gone != null && Global.getSector().getClock().getElapsedDaysSince(gone) < MEMORY_DAYS) return null;
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        Weakest wk = weakest(m, col, plan, profile);
        if (wk == null || !beats(best.score, wk.keep)) return null;
        return wk;
    }

    /** Takes the old industry down (its AI core and item to Autopilot Storage), to make room for the new one. */
    static void takeDown(MarketAPI m, Weakest wk, CAEngine.Pick p) {
        Industry ind = wk.ind;
        CAData d = CAData.get();
        String core = ind.getAICoreId();
        SpecialItemData item = ind.getSpecialItem();
        CAStores.emptyInto(m, ind);   // core and item to this colony's Autopilot Storage
        String oldName = ind.getCurrentName(), oldId = ind.getId();
        m.removeIndustry(oldId, MarketAPI.MarketInteractionMode.REMOTE, false);
        long ts = Global.getSector().getClock().getTimestamp();
        d.endgameBuilt().put(m.getId() + ":-" + oldId, ts);
        d.endgameBuilt().put(m.getId() + ":replaced", ts);
        lastReplaceDay = now();
        chains = null;
        CALog.add(m.getName() + ": took down " + oldName + " for " + p.name + " (endgame, worth " + CAEngine.valueText(p.score - wk.keep) + " more"
                + (core != null || item != null ? "; its " + (core != null && item != null ? "core and item" : core != null ? "core" : "item") + " to Autopilot Storage" : "") + ")", true);
    }

    /** A build the endgame started: not replaced again for a year. */
    static void built(MarketAPI m, CAEngine.Pick p) {
        CAData.get().endgameBuilt().put(m.getId() + ":" + p.id, Global.getSector().getClock().getTimestamp());
        note(m, p);
    }

    /** Forgets what's over a year old. */
    static void tidy(CAData d) {
        Iterator<Map.Entry<String, Long>> it = d.endgameBuilt().entrySet().iterator();
        while (it.hasNext()) if (Global.getSector().getClock().getElapsedDaysSince(it.next().getValue()) > MEMORY_DAYS) it.remove();
    }

    // ---------------- panel ----------------

    static String summaryText(CASettings s) {
        List<String> miss = missing();
        int n = 0;
        for (String k : CAData.get().endgameBuilt().keySet()) if (k.contains(":-")) n++;
        return "Endgame economy (net income over " + Misc.getDGSCredits(ENTER_NET[Math.max(0, Math.min(ENTER_NET.length - 1, s.endgameMode))])
                + " a month with " + Misc.getDGSCredits(CASurplus.RICH) + "+ surplus): credits count 5%; production chains, fleets, ship quality, defense "
                + "and research decide. Keeps net income over " + Misc.getDGSCredits(floor(s)) + " (a third of " + Misc.getDGSCredits(s.endgameRefNet)
                + "); " + Misc.getDGSCredits(room(s)) + " of income may still go this month. "
                + (miss.isEmpty() ? "Your colonies make every good the sector makes." : miss.size() + " goods the sector makes and you don't: "
                + CAContracts.join(miss.size() > 10 ? miss.subList(0, 10) : miss) + (miss.size() > 10 ? ", ..." : "") + ".")
                + (n > 0 ? " Replaced " + n + " building" + (n == 1 ? "" : "s") + " this year." : "");
    }

    static void render(TooltipMakerAPI t, CASettings s) {
        if (!on(s)) return;
        if (summary == null) summary = summaryText(s);
        Color gray = Misc.getGrayColor();
        t.addPara(CALog.esc(summary), gray, 2f);
    }
}
