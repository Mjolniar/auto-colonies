package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Ashes of the Domain research (Vaults of Knowledge). One tech at a time, measured in days, and only while you own a
 * working Research Facility or Black Site. A tech costs items once when it starts (mostly research databanks, from
 * the Research Facility storage tab) plus 10,000 x tier a month on every facility while it runs.
 * Each tech you can start is valued by what it unlocks, scored by the same engine that picks buildings: the candidate
 * techs are marked researched for one scoring pass over your colonies, so AotD's own gates (Building Menu Overhaul,
 * VoK's buildings, synergies) answer as they would afterwards, then put back. Known non-building effects (output +1,
 * hazard -10%, upkeep x0.9) are added, plus half the best tech each one leads to.
 * Each colony's plan comes first: every open plan step that research locks (BMO's gates, VoK's rewards, Terraforming Made
 * Easy's own tech ids) is traced to its tech and the whole chain of techs it still needs, and the step's plan value
 * (CAPlan.bonusFor) is shared along that chain by research days, so the first tech of a long chain counts too.
 * Auto starts the best tech whenever nothing is running or queued, only for techs that cost databanks alone (AI cores
 * and colony items are left to you). While a plan tech waits for databanks, the ones you have aren't spent on techs no
 * plan needs; the log says what's needed. Research upkeep is temporary, so plan techs aren't held back by the new-upkeep
 * cap. Databanks count from the Research Facility tab, Autopilot Storage, your colonies' Storage and your fleet's cargo. Autopilot Storage is research's storage too: whatever a tech is short of when it
 * starts (Auto or the Start button) is moved from there into the Research Facility tab, and databanks from your fleet's
 * cargo after that. The section is on the empire panel's Autopilot Storage page.
 * Reflection only; if VoK can't be read, research is left to you.
 */
public class CAResearch {
    public static final String MOD = "aotd_vok";
    static final String R = "data.kaysaar.aotd.vok.scripts.research.";
    static final String KEY = "$aotd_vok_manager", SUBMARKET = "researchfacil", DATABANK = "research_databank";
    static final float DATABANK_VALUE = 5000f, TIER_UPKEEP = 10000f;
    static final String START = "starting research";
    /** Vanilla buildings VoK locks through Building Menu Overhaul, and the Bifrost Control Center, by tech. */
    static final String[][] GATES = {{"aotd_tech_rudimentary_eq", "aquaculture"}, {"aotd_tech_agriculture", "farming"},
            {"aotd_tech_exoskeletons", "mining"}, {"aotd_tech_nanometal", "refining"}, {"aotd_tech_nanometal", "lightindustry"},
            {"aotd_tech_hull_manufacture", "heavyindustry"}, {"aotd_tech_orbital_assembly", "orbitalworks"},
            {"aotd_tech_antimatter_production", "fuelprod"}, {"aotd_tech_dual_trade_system", "commerce"},
            {"aotd_tech_spaceport_expansion", "megaport"}, {"aotd_tech_bifrost_gate", "aotd_bfc_ind"}};

    static Object instance, player, forPlayer, repo, canResearch, allReq, itemsOk, itemCount, facilities, focus, setFocus, pay, queue, queued, councilAdvance, daysFor;
    static Object fId, fName, fTier, fReq, fItems, fRewards, fDone, fDaysSpent, fOther, fOtherMet, fHead, fCouncil;
    static boolean linked = false, broken = false;

    static boolean link() {
        if (linked || broken) return linked;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> main = Class.forName(R + "AoTDMainResearchManager", true, cl), fac = Class.forName(R + "AoTDFactionResearchManager", true, cl);
            Class<?> opt = Class.forName(R + "models.ResearchOption", true, cl), q = Class.forName(R + "ResearchQueueManager", true, cl);
            Class<?> util = Class.forName("data.kaysaar.aotd.vok.plugins.AoDUtilis", true, cl);
            instance = CARefl.method(main, "getInstance"); player = CARefl.method(main, "getManagerForPlayer"); forPlayer = CARefl.method(main, "isResearchedForPlayer", String.class);
            repo = CARefl.method(fac, "getResearchRepoOfFaction"); canResearch = CARefl.method(fac, "canResearch", String.class, boolean.class);
            allReq = CARefl.method(fac, "haveResearchedAllReq", String.class); itemsOk = CARefl.method(fac, "haveMetReqForItems", String.class);
            itemCount = CARefl.method(fac, "retrieveAmountOfItems", String.class); facilities = CARefl.method(fac, "getAmountOfResearchFacilities");
            focus = CARefl.method(fac, "getCurrentFocus"); setFocus = CARefl.method(fac, "setCurrentFocus", String.class); pay = CARefl.method(fac, "payForResearch", String.class);
            queue = CARefl.method(fac, "getQueueManager"); queued = CARefl.method(q, "getQueuedResearchOptions");
            councilAdvance = CARefl.method(fac, "executeResearchCouncilAdvance", float.class);
            daysFor = CARefl.method(util, "getDaysFromResearch", opt);
            fId = CARefl.field(opt, "Id"); fName = CARefl.field(opt, "Name"); fTier = CARefl.field(opt, "Tier"); fReq = CARefl.field(opt, "ReqTechsToResearchFirst");
            fItems = CARefl.field(opt, "ReqItemsToResearchFirst"); fRewards = CARefl.field(opt, "Rewards"); fDone = CARefl.field(opt, "isResearched");
            fDaysSpent = CARefl.field(opt, "daysSpentOnResearching"); fOther = CARefl.field(opt, "otherReq"); fOtherMet = CARefl.field(opt, "metOtherReq");
            fHead = CARefl.field(fac, "currentHeadOfCouncil"); fCouncil = CARefl.field(fac, "researchCouncil");
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Vaults of Knowledge's research; it's left to you", t);
        }
        return linked;
    }

    /** The player's research manager, or null before VoK has made one (a new game's first frames). */
    static Object mgr() throws Exception {
        if (!link() || Global.getSector().getPersistentData().get(KEY) == null) return null;
        Object main = CARefl.invoke(instance, null);
        return main == null ? null : CARefl.invoke(player, main);
    }

    static boolean forPlayer(String id) {
        try { Object main = CARefl.invoke(instance, null); return main != null && Boolean.TRUE.equals(CARefl.invoke(forPlayer, main, id)); }
        catch (Throwable t) { return false; }
    }

    // ---------------- reading ----------------

    public static class Tech {
        public String id, name;
        public int tier;
        public float days, spent;
        public Map<String, Integer> items = new LinkedHashMap<String, Integer>();
        public Set<String> industries = new LinkedHashSet<String>();
        public List<String> prereqs = new ArrayList<String>();
        public boolean available, needsItems, unlockedByCommission;
        public float own, next, value, cost, rank;
        public float plan;   // its share of the plan steps whose research chain it's on
        public List<String> planFor = new ArrayList<String>();
        public List<String> lines = new ArrayList<String>();
        Object raw;

        boolean databanksOnly() { for (String k : items.keySet()) if (!DATABANK.equals(k)) return false; return true; }
        int databanks() { Integer n = items.get(DATABANK); return n == null ? 0 : n; }
    }

    @SuppressWarnings("unchecked")
    static Tech tech(Object mgr, Object o) throws Exception {
        Tech t = new Tech();
        t.raw = o;
        t.id = String.valueOf(CARefl.get(fId, o));
        t.name = String.valueOf(CARefl.get(fName, o));
        Object tier = CARefl.get(fTier, o);
        t.tier = tier instanceof Enum ? ((Enum<?>) tier).ordinal() : 0;
        t.days = ((Number) CARefl.invoke(daysFor, null, o)).floatValue();
        t.spent = CARefl.getFloat(fDaysSpent, o);
        Map<String, Integer> it = (Map<String, Integer>) CARefl.get(fItems, o);
        if (it != null) for (Map.Entry<String, Integer> e : it.entrySet()) if (e.getValue() != null && e.getValue() > 0) t.items.put(e.getKey(), e.getValue());
        List<String> req = (List<String>) CARefl.get(fReq, o);
        if (req != null) t.prereqs.addAll(req);
        Map<String, Object> rw = (Map<String, Object>) CARefl.get(fRewards, o);
        if (rw != null) for (Map.Entry<String, Object> e : rw.entrySet()) {
            if (e.getValue() instanceof Enum && "INDUSTRY".equals(((Enum<?>) e.getValue()).name()) && Global.getSettings().getIndustrySpec(e.getKey()) != null) t.industries.add(e.getKey());
        }
        for (String[] g : GATES) if (g[0].equals(t.id)) t.industries.add(g[1]);
        boolean flagOk = CARefl.get(fOther, o) == null || CARefl.getBoolean(fOtherMet, o);
        t.available = Boolean.TRUE.equals(CARefl.invoke(canResearch, mgr, t.id, false));
        t.needsItems = !t.available && flagOk && Boolean.TRUE.equals(CARefl.invoke(allReq, mgr, t.id)) && !Boolean.TRUE.equals(CARefl.invoke(itemsOk, mgr, t.id));
        t.unlockedByCommission = forPlayer(t.id);
        return t;
    }

    static String commodityName(String id) {
        try { if (Global.getSettings().getCommoditySpec(id) != null) return Global.getSettings().getCommoditySpec(id).getName(); } catch (Throwable ignored) { }
        try { return Global.getSettings().getSpecialItemSpec(id).getName(); } catch (Throwable ignored) { }
        return id;
    }

    static String itemsText(Tech t) {
        List<String> l = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : t.items.entrySet()) l.add(e.getValue() + " " + commodityName(e.getKey()));
        return l.isEmpty() ? "nothing" : CAContracts.join(l);
    }

    // ---------------- valuing ----------------

    static List<Tech> cached;
    static float cachedAt = -99f;

    static float now() { return Global.getSector().getClock().getElapsedDaysSince(0L); }

    /** An open plan step that research locks, with the chain of techs it still needs. */
    public static class Need {
        public String step, colony;
        public float value;
        public List<String> chain = new ArrayList<String>();   // unresearched techs, prerequisites first
        public int databanks;
        public float days;
        public boolean manual;   // a tech on the way needs AI cores or colony items, which Auto leaves to you
    }

    static List<Need> needs = new ArrayList<Need>();

    /** Terraforming Made Easy's own research gate for one of its structures (BaseDevelopmentIndustry.getAOTDVOKTechId), or null. */
    static String tmeTech(MarketAPI m, String industryId) {
        try {
            com.fs.starfarer.api.loading.IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(industryId);
            if (sp == null) return null;
            Object r = CARefl.call(sp.getNewPluginInstance(m), "getAOTDVOKTechId");
            return r instanceof String && !((String) r).isEmpty() ? (String) r : null;
        } catch (Throwable t) { return null; }
    }

    /** The unresearched techs id still needs, prerequisites first, with id last. */
    static void closure(String id, Map<String, Tech> open, Set<String> seen, List<String> out) {
        if (!seen.add(id)) return;
        Tech t = open.get(id);
        if (t == null) return;   // researched, or unknown
        for (String p : t.prereqs) closure(p, open, seen, out);
        out.add(id);
    }

    /**
     * Each Approve or Auto colony's open plan steps that research locks: the step building, and the buildings it's
     * upgraded from, traced to the tech that unlocks each. The step's plan value is shared along the chain by days.
     */
    static List<Need> planNeeds(Map<String, Tech> open) {
        Map<String, String> byIndustry = new HashMap<String, String>();
        for (Tech t : open.values()) for (String ind : t.industries) if (!t.unlockedByCommission) byIndustry.put(ind, t.id);
        List<Need> out = new ArrayList<Need>();
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if ((c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) || CAEngine.isOutpost(m) || CAHypershunt.is(m)) continue;
            CAPlan.Plan plan;
            try { plan = CAPlan.build(m, c.profile); } catch (Throwable e) { continue; }
            for (CAPlan.Step st : plan.steps) {
                if (st.kind != CAPlan.Kind.BUILD || st.done(m)) continue;
                float value = CAPlan.bonusFor(m, plan, st.id);
                if (value <= 0f) continue;
                List<String> chain = new ArrayList<String>();
                Set<String> seen = new HashSet<String>();
                String cur = st.id;
                for (int k = 0; k < 6 && cur != null; k++) {   // the step, then what it's upgraded from
                    if (m.hasIndustry(cur)) break;
                    String tech = byIndustry.get(cur);
                    if (tech == null && cur.startsWith("tme_")) tech = tmeTech(m, cur);
                    if (tech != null) closure(tech, open, seen, chain);
                    com.fs.starfarer.api.loading.IndustrySpecAPI sp = Global.getSettings().getIndustrySpec(cur);
                    cur = sp == null ? null : sp.getDowngrade();
                }
                if (chain.isEmpty()) continue;
                Need n = new Need();
                n.step = st.label; n.colony = m.getName(); n.value = value; n.chain = chain;
                for (String id : chain) {
                    Tech t = open.get(id);
                    n.days += Math.max(1f, t.days - t.spent);
                    n.databanks += t.databanks();
                    if (!t.databanksOnly()) n.manual = true;
                }
                out.add(n);
            }
        }
        share(out, open);
        return out;
    }

    /** Shares each need's value along its chain by research days, and notes on each tech what it's on the way to. */
    static void share(List<Need> needs, Map<String, Tech> open) {
        for (Need n : needs) for (String id : n.chain) {
            Tech t = open.get(id);
            t.plan += n.value * Math.max(1f, t.days - t.spent) / Math.max(1f, n.days);
            String what = n.step + " at " + n.colony;
            if (!t.planFor.contains(what)) t.planFor.add(what);
        }
    }

    /** Every tech you could start now or once its items are in storage, valued and ranked, best first. Refreshed daily. */
    @SuppressWarnings("unchecked")
    public static List<Tech> ranked() throws Exception {
        if (cached != null && now() - cachedAt < 1f) return cached;
        Object mgr = mgr();
        List<Tech> out = new ArrayList<Tech>();
        if (mgr == null) return out;
        Set<String> done = new HashSet<String>();
        Map<String, Object> all = new LinkedHashMap<String, Object>();
        for (Object o : (List<Object>) CARefl.invoke(repo, mgr)) {
            all.put(String.valueOf(CARefl.get(fId, o)), o);
            if (CARefl.getBoolean(fDone, o)) done.add(String.valueOf(CARefl.get(fId, o)));
        }
        List<Tech> cands = new ArrayList<Tech>();
        Map<String, Tech> children = new LinkedHashMap<String, Tech>();
        Map<String, Tech> open = new LinkedHashMap<String, Tech>();
        for (Object o : all.values()) {
            if (CARefl.getBoolean(fDone, o)) continue;
            Tech t = tech(mgr, o);
            open.put(t.id, t);
            if (t.available || t.needsItems) cands.add(t);
        }
        try { needs = planNeeds(open); } catch (Throwable e) { needs = new ArrayList<Need>(); CADiagnostics.LOG.warn(CADiagnostics.TAG + "research plan needs failed", e); }
        // techs one step past a candidate, for the "leads to" share
        for (Tech t : open.values()) {
            if (t.available || t.needsItems) continue;
            for (Tech c : cands) {
                boolean ok = !t.prereqs.isEmpty();
                for (String p : t.prereqs) if (!done.contains(p) && !p.equals(c.id)) ok = false;
                if (ok) { children.put(t.id, t); break; }
            }
        }
        List<Tech> scored = new ArrayList<Tech>(cands);
        scored.addAll(children.values());
        valueBuildings(scored);
        for (Tech t : scored) t.own += effects(t);
        for (Tech t : cands) if (t.plan > 0f) {
            t.own += t.plan;
            t.lines.add(0, "On the way to " + CAContracts.join(t.planFor) + " (colony plans): " + CAEngine.valueText(t.plan));
        }
        int fac = Math.max(1, ((Number) CARefl.invoke(facilities, mgr)).intValue());
        for (Tech c : cands) {
            Tech bestChild = null;
            for (Tech ch : children.values()) {
                if (!ch.prereqs.contains(c.id)) continue;
                if (bestChild == null || ch.own > bestChild.own) bestChild = ch;
            }
            c.next = bestChild == null ? 0f : 0.5f * bestChild.own;
            if (bestChild != null && bestChild.own > 0f) c.lines.add("Leads to " + bestChild.name + " (" + CAEngine.valueText(bestChild.own) + ")");
            c.value = c.own + c.next;
            float daysLeft = Math.max(1f, c.days - c.spent);
            c.cost = c.tier * TIER_UPKEEP * fac * daysLeft / 30f + c.databanks() * DATABANK_VALUE;
            c.rank = (12f * c.value - c.cost) / daysLeft;   // a year of what it unlocks, less its cost, per research day
            out.add(c);
        }
        Collections.sort(out, new Comparator<Tech>() { public int compare(Tech a, Tech b) { return Float.compare(b.rank, a.rank); } });
        cached = out; cachedAt = now();
        return out;
    }

    /**
     * Scores the buildings each tech unlocks: marks the techs researched, asks the engine what every colony would build,
     * credits each pick to the tech that unlocks it, then puts the techs back exactly as they were.
     */
    static void valueBuildings(List<Tech> techs) {
        Map<String, Tech> byIndustry = new HashMap<String, Tech>();
        Map<Object, Boolean> was = new LinkedHashMap<Object, Boolean>();
        for (Tech t : techs) {
            if (t.unlockedByCommission) continue;   // already buildable through your commission
            for (String ind : t.industries) byIndustry.put(ind, t);
        }
        if (byIndustry.isEmpty()) return;
        CAData d = CAData.get();
        Map<Tech, Float> total = new HashMap<Tech, Float>();
        Map<Tech, String> where = new HashMap<Tech, String>();
        try {
            for (Tech t : techs) { was.put(t.raw, CARefl.getBoolean(fDone, t.raw)); CARefl.set(fDone, t.raw, true); }
            CAEngine.noProbe = true;
            for (MarketAPI m : Misc.getPlayerMarkets(false)) {
                if (CAHypershunt.is(m)) continue;
                CAEngine.Result r;
                try { r = CAEngine.evaluate(m, d.colony(m.getId()).profile, d.minStability); }
                catch (Throwable e) { continue; }
                Map<Tech, Float> best = new HashMap<Tech, Float>();
                Map<Tech, String> bestName = new HashMap<Tech, String>();
                List<CAEngine.Pick> ps = new ArrayList<CAEngine.Pick>(r.picks);
                if (r.bestNoSlot != null) ps.add(r.bestNoSlot);
                for (CAEngine.Pick p : ps) {
                    Tech t = byIndustry.get(p.id);
                    if (t == null) continue;
                    float v = p == r.bestNoSlot ? 0.5f * p.score : p.score;   // needs a slot freed first
                    if (v > 0f && (!best.containsKey(t) || v > best.get(t))) { best.put(t, v); bestName.put(t, p.name + " at " + m.getName()); }
                }
                for (Map.Entry<Tech, Float> e : best.entrySet()) {
                    Float cur = total.get(e.getKey());
                    total.put(e.getKey(), (cur == null ? 0f : cur) + e.getValue());
                    if (!where.containsKey(e.getKey())) where.put(e.getKey(), bestName.get(e.getKey()));
                }
            }
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "research building valuation failed", e);
        } finally {
            CAEngine.noProbe = false;
            for (Map.Entry<Object, Boolean> e : was.entrySet()) {
                try { CARefl.set(fDone, e.getKey(), e.getValue()); } catch (Throwable ignored) { }
            }
        }
        for (Map.Entry<Tech, Float> e : total.entrySet()) {
            e.getKey().own += e.getValue();
            e.getKey().lines.add("Unlocks buildings worth " + CAEngine.valueText(e.getValue()) + ", best " + where.get(e.getKey()));
        }
    }

    static int outputLines(boolean miningOnly) {
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) for (Industry ind : m.getIndustries()) {
            if (ind.isHidden() || (miningOnly && !ind.getSpec().hasTag("mining"))) continue;
            for (MutableCommodityQuantity q : ind.getAllSupply()) if (q.getQuantity().getModifiedInt() > 0) n++;
        }
        return n;
    }

    static float lineValue(boolean miningOnly) {
        float v = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) for (Industry ind : m.getIndustries()) {
            if (ind.isHidden() || (miningOnly && !ind.getSpec().hasTag("mining"))) continue;
            for (MutableCommodityQuantity q : ind.getAllSupply()) {
                if (q.getQuantity().getModifiedInt() <= 0) continue;
                try { v += Global.getSettings().getCommoditySpec(q.getCommodityId()).getBasePrice() * CAEngine.unitValue; } catch (Throwable ignored) { }
            }
        }
        return v;
    }

    static float upkeep(boolean waterOnly, float hazardCut) {
        float v = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            // the game's own water-world list (Unknown Skies and others add their types to it)
            if (waterOnly && (m.getPlanetEntity() == null || !com.fs.starfarer.api.impl.campaign.econ.impl.Farming.AQUA_PLANETS.contains(m.getPlanetEntity().getTypeId()))) continue;
            float u = 0f;
            for (Industry ind : m.getIndustries()) u += ind.getUpkeep().getModifiedValue();
            v += hazardCut > 0f ? u * hazardCut / Math.max(0.25f, m.getHazardValue()) : u;
        }
        return v;
    }

    /** Monthly value of a tech's effects that aren't buildings, from what VoK's code does (several tooltips claim more). */
    static float effects(Tech t) {
        float v = 0f;
        String what = null;
        if (t.id.equals("aotd_tech_streamlined_production")) { v = lineValue(false); what = "+1 to every output line (" + outputLines(false) + " lines)"; }
        else if (t.id.equals("aotd_tech_advanced_mining_equipment")) { v = lineValue(true); what = "+1 to every mining output line (" + outputLines(true) + " lines)"; }
        else if (t.id.equals("aotd_tech_hazmat_equipment")) { v = upkeep(false, 0.1f); what = "hazard -10% on every colony, so lower upkeep"; }
        else if (t.id.equals("aotd_tech_aquatic_agriculture")) { v = upkeep(true, 0.1f); what = "hazard -10% on water worlds"; }
        else if (t.id.equals("aotd_tech_domain_template_construct")) { v = 0.1f * upkeep(false, 0f); what = "upkeep x0.9 on every colony"; }
        else if (t.id.equals("aotd_tech_megastructure_analysis") || t.id.equals("aotd_tech_hp_distribution")) {
            boolean shunt = false;
            for (MarketAPI m : Misc.getPlayerMarkets(false)) if (CAHypershunt.is(m)) shunt = true;
            if (shunt) { v = 25000f; what = t.id.endsWith("analysis") ? "lets your Coronal Hypershunt be restored" : "Hypershunt Receivers: +2 industry slots in their systems"; }
        }
        if (what != null && v > 0f) t.lines.add(Character.toUpperCase(what.charAt(0)) + what.substring(1) + ": " + CAEngine.valueText(v));
        return v;
    }

    // ---------------- acting ----------------

    static String start(Tech t, String who) {
        try {
            Object mgr = mgr();
            if (mgr == null) return "Research isn't running yet";
            if (CARefl.invoke(focus, mgr) != null) return "Something is already being researched";
            sophiaFirst(mgr, t);
            if (!Boolean.TRUE.equals(CARefl.invoke(canResearch, mgr, t.id, false))) {
                String moved = topUp(mgr, t);
                if (!Boolean.TRUE.equals(CARefl.invoke(canResearch, mgr, t.id, false))) return "Not enough in the Research Facility tab (" + itemsText(t) + " needed)" + (moved == null ? "" : "; " + moved);
            }
            if (!CABreaker.allowed(START)) return "Paused for this session: starting research didn't take effect three times";
            CARefl.invoke(pay, mgr, t.id);
            CARefl.invoke(setFocus, mgr, t.id);
            cached = null;
            Object f = CARefl.invoke(focus, mgr);
            if (!CABreaker.check(START, f != null && t.id.equals(String.valueOf(CARefl.get(fId, f))), "research")) return "VoK didn't start it";
            CALog.add("Research: " + who + " started " + t.name + " (" + Math.round(Math.max(1f, t.days - t.spent)) + " days, " + itemsText(t)
                    + (t.tier > 0 ? ", +" + Misc.getDGSCredits(t.tier * TIER_UPKEEP) + " a month per facility while it runs" : "") + ")", true);
            return null;
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "starting research " + t.id + " failed", e);
            return "That failed; see starsector.log";
        }
    }

    /** Puts Sophia Anderson at the head of R&D before paying: she takes one databank off every tech. */
    @SuppressWarnings("unchecked")
    static void sophiaFirst(Object mgr, Tech t) throws Exception {
        if (t.databanks() <= 0) return;
        List<Object> council = (List<Object>) CARefl.get(fCouncil, mgr);
        if (council == null) return;
        for (Object s : council) {
            if (s == null || !s.getClass().getSimpleName().equals("SophiaAnderson") || s == CARefl.get(fHead, mgr)) continue;
            CARefl.set(fHead, mgr, s);
            CARefl.invoke(councilAdvance, mgr, 0f);
            CALog.add("Research: made Sophia Anderson head of R&D (one databank less per tech)");
            return;
        }
    }

    /** Research databanks in your fleet's cargo. */
    static float fleetDatabanks() {
        com.fs.starfarer.api.campaign.CampaignFleetAPI f = Global.getSector().getPlayerFleet();
        return f == null || f.getCargo() == null ? 0f : f.getCargo().getCommodityQuantity(DATABANK);
    }

    /** Research databanks in your colonies' Storage tabs (VoK puts a research site's databanks there while the facility is down). */
    static float colonyDatabanks() {
        float n = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) { CargoAPI c = Misc.getStorageCargo(m); if (c != null) n += c.getCommodityQuantity(DATABANK); }
        return n;
    }

    /** Databanks research can use: the Research Facility tabs, Autopilot Storage, your colonies' Storage and your fleet's cargo. */
    static float databanksOnHand(Object mgr) throws Exception {
        return ((Number) CARefl.invoke(itemCount, mgr, DATABANK)).floatValue() + CAData.get().storage().getCommodityQuantity(DATABANK) + colonyDatabanks() + fleetDatabanks();
    }

    static boolean isCommodity(String id) {
        try { return Global.getSettings().getCommoditySpec(id) != null; } catch (Throwable t) { return false; }
    }

    /** How many of item id a cargo holds: a commodity, or a special item such as a colony item. */
    static float count(CargoAPI c, String id) {
        if (c == null) return 0f;
        return isCommodity(id) ? c.getCommodityQuantity(id) : c.getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(id, null));
    }

    static void move(CargoAPI from, CargoAPI to, String id, int n) {
        if (n <= 0) return;
        if (isCommodity(id)) { from.removeCommodity(id, n); to.addCommodity(id, n); return; }
        SpecialItemData data = new SpecialItemData(id, null);
        from.removeItems(CargoAPI.CargoItemType.SPECIAL, data, n);
        to.addSpecial(data, n);
    }

    /** Whether the Research Facility tabs, Autopilot Storage and (databanks only) your fleet's cargo hold everything t needs. */
    static boolean covered(Object mgr, Tech t) throws Exception {
        CargoAPI st = CAData.get().storage();
        for (Map.Entry<String, Integer> e : t.items.entrySet()) {
            float have = ((Number) CARefl.invoke(itemCount, mgr, e.getKey())).floatValue() + count(st, e.getKey())
                    + (DATABANK.equals(e.getKey()) ? colonyDatabanks() + fleetDatabanks() : 0f);
            if (have < e.getValue()) return false;
        }
        return true;
    }

    /**
     * Moves everything a tech is short of into the Research Facility tab that holds the most databanks already: from
     * Autopilot Storage first, then databanks from your colonies' Storage, then from your fleet's cargo. Nothing moves
     * unless the whole shortfall is covered.
     */
    static String topUp(Object mgr, Tech t) throws Exception {
        CargoAPI st = CAData.get().storage();
        com.fs.starfarer.api.campaign.CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        Map<String, int[]> plan = new LinkedHashMap<String, int[]>();   // item -> {from storage, from your fleet's cargo}
        int fromColonies = 0;
        for (Map.Entry<String, Integer> e : t.items.entrySet()) {
            int need = e.getValue() - (int) ((Number) CARefl.invoke(itemCount, mgr, e.getKey())).floatValue();
            if (need <= 0) continue;
            int fromStorage = (int) Math.min(need, count(st, e.getKey()));
            if (DATABANK.equals(e.getKey())) fromColonies = (int) Math.min(need - fromStorage, colonyDatabanks());
            int fromFleet = need - fromStorage - (DATABANK.equals(e.getKey()) ? fromColonies : 0);
            if (fromFleet > 0 && (!DATABANK.equals(e.getKey()) || fleet == null || fleet.getCargo() == null || fleetDatabanks() < fromFleet)) return null;
            plan.put(e.getKey(), new int[]{fromStorage, fromFleet});
        }
        if (plan.isEmpty()) return null;
        SubmarketAPI best = null; float bestHas = -1f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            SubmarketAPI sm = m.getSubmarket(SUBMARKET);
            if (sm == null || sm.getCargo() == null) continue;
            float has = sm.getCargo().getCommodityQuantity(DATABANK);
            if (has > bestHas) { bestHas = has; best = sm; }
        }
        if (best == null) return null;
        List<String> what = new ArrayList<String>();
        boolean anyStorage = false, anyFleet = false;
        int left = fromColonies;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {   // databanks from your colonies' Storage
            CargoAPI c = Misc.getStorageCargo(m);
            if (left <= 0 || c == null) continue;
            int n = (int) Math.min(left, c.getCommodityQuantity(DATABANK));
            if (n <= 0) continue;
            move(c, best.getCargo(), DATABANK, n);
            left -= n;
        }
        if (fromColonies > 0) what.add(fromColonies + " " + commodityName(DATABANK) + " from your colonies' Storage");
        for (Map.Entry<String, int[]> e : plan.entrySet()) {
            int fromStorage = e.getValue()[0], fromFleet = e.getValue()[1];
            move(st, best.getCargo(), e.getKey(), fromStorage);
            if (fromFleet > 0) move(fleet.getCargo(), best.getCargo(), e.getKey(), fromFleet);
            anyStorage |= fromStorage > 0; anyFleet |= fromFleet > 0;
            what.add((fromStorage + fromFleet) + " " + commodityName(e.getKey()) + (fromStorage > 0 && fromFleet > 0 ? " (" + fromFleet + " from your fleet's cargo)" : ""));
        }
        String from = anyStorage && anyFleet ? "from Autopilot Storage and your fleet's cargo" : anyFleet ? "from your fleet's cargo" : "from Autopilot Storage";
        String msg = "moved " + CAContracts.join(what) + " " + from + " to " + best.getMarket().getName() + "'s Research Facility tab";
        CALog.add("Research: " + msg);
        return msg;
    }

    @SuppressWarnings("unchecked")
    static boolean idle(Object mgr) throws Exception {
        if (CARefl.invoke(focus, mgr) != null) return false;
        List<Object> q = (List<Object>) CARefl.invoke(queued, CARefl.invoke(queue, mgr));
        return q == null || q.isEmpty();
    }

    /** Auto: when nothing is being researched or queued, starts the best tech that costs only databanks and pays for itself. */
    public static void run(CAData d, CASettings s) {
        if (s.researchMode != 1) return;
        try {
            Object mgr = mgr();
            if (mgr == null || !idle(mgr)) return;
            if (((Number) CARefl.invoke(facilities, mgr)).intValue() <= 0) {
                if (CALog.once("research:nofacility")) CALog.add("Research: no working Research Facility, so nothing can be researched");
                return;
            }
            CALog.forget("research:nofacility");
            if (CABudget.paused(s)) return;
            int fac = ((Number) CARefl.invoke(facilities, mgr)).intValue();
            Tech waiting = null;   // the best plan tech that's short of databanks: the ones you have are kept for it
            float have = databanksOnHand(mgr);
            for (Tech t : ranked()) {
                if (t.rank <= 0f) break;
                if (!t.databanksOnly()) continue;
                if (t.needsItems && have < t.databanks()) { if (waiting == null && t.plan > 0f) waiting = t; continue; }
                if (waiting != null && t.plan <= 0f) continue;   // no plan needs it; keep the databanks
                float upkeep = t.tier * TIER_UPKEEP * fac;
                if (t.plan <= 0f && s.upkeepAdded + upkeep > s.newUpkeepCap) {   // research upkeep ends with the tech; plan techs go anyway
                    if (CALog.once("research:upkeep:" + t.id)) CALog.add("Research: " + t.name + " waits for next month (new upkeep cap reached)");
                    continue;
                }
                if (start(t, "Auto") == null) { CABudget.charge(s, 0f, 0f, upkeep); return; }
            }
            if (waiting != null && CALog.once("research:wait:" + waiting.id + ":" + (int) have))
                CALog.add("Research: next for your colonies' plans is " + waiting.name + " (for " + CAContracts.join(waiting.planFor) + "), which needs "
                        + waiting.databanks() + " research databanks; you have " + (int) have + ". Databanks come from salvage (research stations, derelicts) "
                        + "and from a Research Facility on a pre-collapse research site; Autopilot uses them from Autopilot Storage, colony Storage or your cargo", true);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "research step failed", t);
        }
    }

    /** A panel button: start <techId>. Returns a message. */
    public static String handle(String action, String id) {
        try {
            for (Tech t : ranked()) if (t.id.equals(id)) return "start".equals(action) ? start(t, "you") : "Unknown action";
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "research action failed", e);
            return "That failed; see starsector.log";
        }
        return "That tech can't be started now";
    }

    // ---------------- empire panel ----------------

    public static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        t.addPara("Mode: %s. Values each tech by the buildings it unlocks at your colonies (scored like any build), its other effects and "
                + "what it leads to, against its databanks and the facility upkeep it adds. Auto starts the best one when nothing is running "
                + "or queued, only if it costs databanks alone. Starting a tech, by Auto or with Start, moves whatever it's short of from "
                + "Autopilot Storage into the Research Facility tab (databanks also from your fleet's cargo).", 6f, hl, CASettings.CONTRACT_NAMES[Math.max(0, Math.min(2, s.researchMode))]);
        try {
            Object mgr = mgr();
            if (mgr == null) { t.addPara("Vaults of Knowledge's research hasn't started in this save yet.", gray, 6f); return; }
            int fac = ((Number) CARefl.invoke(facilities, mgr)).intValue();
            Object cur = CARefl.invoke(focus, mgr);
            if (cur != null) {
                Tech c = tech(mgr, cur);
                t.addPara("Researching %s: %s of %s days.", 6f, hl, c.name, "" + Math.round(c.spent), "" + Math.round(c.days));
            } else t.addPara("Nothing is being researched.", 6f);
            if (fac <= 0) t.addPara("No working Research Facility, so research doesn't advance.", Misc.getNegativeHighlightColor(), 4f);
            else t.addPara("%s research facilities. Databanks in their storage: %s; in Autopilot Storage: %s; in your fleet's cargo: %s.", 4f, hl, "" + fac,
                    "" + (int) ((Number) CARefl.invoke(itemCount, mgr, DATABANK)).floatValue(), "" + (int) d.storage().getCommodityQuantity(DATABANK),
                    "" + (int) fleetDatabanks());
            t.addPara("Databanks on hand: %s (Research Facility tab, Autopilot Storage, colony Storage and your fleet's cargo).", 4f, hl, "" + (int) databanksOnHand(mgr));
            List<Tech> rankedNow = ranked();
            if (!needs.isEmpty()) {
                t.addPara("Your colonies' plans wait on research:", 8f);
                int k = 0;
                for (Need n : needs) {
                    if (k++ >= 6) break;
                    List<String> names = new ArrayList<String>();
                    for (Tech x : rankedNow) if (n.chain.contains(x.id)) names.add(x.name);
                    t.addPara("    %s at %s: %s techs, %s databanks, about %s days" + (n.manual ? " (one uses items Auto won't spend)" : ""), 2f, hl,
                            CALog.esc(n.step), CALog.esc(n.colony), "" + n.chain.size(), "" + n.databanks, "" + Math.round(n.days));
                }
            }
            boolean idle = idle(mgr);
            int shown = 0;
            for (Tech x : rankedNow) {
                if (shown++ >= 6) break;
                t.addPara("%s (%s days, %s): %s a month" + (x.needsItems ? (covered(mgr, x) ? ", moved in from Autopilot Storage when started" : ", not enough on hand") : "") + (!x.databanksOnly() ? ", uses items Auto won't spend" : ""),
                        8f, x.rank > 0f ? hl : gray, x.name, "" + Math.round(Math.max(1f, x.days - x.spent)), itemsText(x), CAEngine.signed(x.value));
                for (int j = 0; j < Math.min(3, x.lines.size()); j++) t.addPara("    " + CALog.esc(x.lines.get(j)), gray, 2f);
                if (idle && s.researchMode != 2) {
                    ButtonAPI b = t.addButton("Start", new String[]{"research", "start", x.id}, base, dark, Alignment.MID, CutStyle.ALL, 120f, 22f, 4f);
                    b.setEnabled(fac > 0);
                }
            }
            if (shown == 0) t.addPara("No tech can be started: every remaining one needs another researched first.", gray, 6f);
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "research section failed", e);
            t.addPara("Research info unavailable; see starsector.log.", gray, 6f);
        }
    }
}
