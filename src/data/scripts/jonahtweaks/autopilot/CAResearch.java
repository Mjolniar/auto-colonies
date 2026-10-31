package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Ashes of the Domain research (Vaults of Knowledge). One tech at a time, measured in days, and only while you own a
 * working Research Facility or Black Site. A tech costs items once when it starts (mostly research databanks, from
 * the Research Facility storage tab) plus 10,000 x tier a month on every facility while it runs.
 * Each tech you can start is valued by what it unlocks, scored by the same engine that picks buildings: the candidate
 * techs are marked researched for one scoring pass over your colonies, so AotD's own gates (Building Menu Overhaul,
 * VoK's buildings, synergies) answer as they would afterwards, then put back. Known non-building effects (output +1,
 * hazard -10%, upkeep x0.9) are added, plus half the best tech each one leads to.
 * Auto starts the best tech whenever nothing is running or queued, only for techs that cost databanks alone (AI cores
 * and colony items are left to you), moving databanks from Autopilot Storage when the Research Facility tab is short.
 * Reflection only; if VoK can't be read, research is left to you.
 */
public class CAResearch {
    public static final String MOD = "aotd_vok";
    static final String R = "data.kaysaar.aotd.vok.scripts.research.";
    static final String KEY = "$aotd_vok_manager", SUBMARKET = "researchfacil", DATABANK = "research_databank";
    static final float DATABANK_VALUE = 5000f, TIER_UPKEEP = 10000f;
    /** Vanilla buildings VoK locks through Building Menu Overhaul, and the Bifrost Control Center, by tech. */
    static final String[][] GATES = {{"aotd_tech_rudimentary_eq", "aquaculture"}, {"aotd_tech_agriculture", "farming"},
            {"aotd_tech_exoskeletons", "mining"}, {"aotd_tech_nanometal", "refining"}, {"aotd_tech_nanometal", "lightindustry"},
            {"aotd_tech_hull_manufacture", "heavyindustry"}, {"aotd_tech_orbital_assembly", "orbitalworks"},
            {"aotd_tech_antimatter_production", "fuelprod"}, {"aotd_tech_dual_trade_system", "commerce"},
            {"aotd_tech_spaceport_expansion", "megaport"}, {"aotd_tech_bifrost_gate", "aotd_bfc_ind"}};

    static Method instance, player, forPlayer, repo, canResearch, allReq, itemsOk, itemCount, facilities, focus, setFocus, pay, queue, queued, councilAdvance, daysFor;
    static Field fId, fName, fTier, fReq, fItems, fRewards, fDone, fDaysSpent, fOther, fOtherMet, fHead, fCouncil;
    static boolean linked = false, broken = false;

    static boolean link() {
        if (linked || broken) return linked;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> main = Class.forName(R + "AoTDMainResearchManager", true, cl), fac = Class.forName(R + "AoTDFactionResearchManager", true, cl);
            Class<?> opt = Class.forName(R + "models.ResearchOption", true, cl), q = Class.forName(R + "ResearchQueueManager", true, cl);
            Class<?> util = Class.forName("data.kaysaar.aotd.vok.plugins.AoDUtilis", true, cl);
            instance = main.getMethod("getInstance"); player = main.getMethod("getManagerForPlayer"); forPlayer = main.getMethod("isResearchedForPlayer", String.class);
            repo = fac.getMethod("getResearchRepoOfFaction"); canResearch = fac.getMethod("canResearch", String.class, boolean.class);
            allReq = fac.getMethod("haveResearchedAllReq", String.class); itemsOk = fac.getMethod("haveMetReqForItems", String.class);
            itemCount = fac.getMethod("retrieveAmountOfItems", String.class); facilities = fac.getMethod("getAmountOfResearchFacilities");
            focus = fac.getMethod("getCurrentFocus"); setFocus = fac.getMethod("setCurrentFocus", String.class); pay = fac.getMethod("payForResearch", String.class);
            queue = fac.getMethod("getQueueManager"); queued = q.getMethod("getQueuedResearchOptions");
            councilAdvance = fac.getMethod("executeResearchCouncilAdvance", float.class);
            daysFor = util.getMethod("getDaysFromResearch", opt);
            fId = opt.getField("Id"); fName = opt.getField("Name"); fTier = opt.getField("Tier"); fReq = opt.getField("ReqTechsToResearchFirst");
            fItems = opt.getField("ReqItemsToResearchFirst"); fRewards = opt.getField("Rewards"); fDone = opt.getField("isResearched");
            fDaysSpent = opt.getField("daysSpentOnResearching"); fOther = opt.getField("otherReq"); fOtherMet = opt.getField("metOtherReq");
            fHead = fac.getField("currentHeadOfCouncil"); fCouncil = fac.getField("researchCouncil");
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
        Object main = instance.invoke(null);
        return main == null ? null : player.invoke(main);
    }

    static boolean forPlayer(String id) {
        try { Object main = instance.invoke(null); return main != null && Boolean.TRUE.equals(forPlayer.invoke(main, id)); }
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
        public List<String> lines = new ArrayList<String>();
        Object raw;

        boolean databanksOnly() { for (String k : items.keySet()) if (!DATABANK.equals(k)) return false; return true; }
        int databanks() { Integer n = items.get(DATABANK); return n == null ? 0 : n; }
    }

    @SuppressWarnings("unchecked")
    static Tech tech(Object mgr, Object o) throws Exception {
        Tech t = new Tech();
        t.raw = o;
        t.id = String.valueOf(fId.get(o));
        t.name = String.valueOf(fName.get(o));
        Object tier = fTier.get(o);
        t.tier = tier instanceof Enum ? ((Enum<?>) tier).ordinal() : 0;
        t.days = ((Number) daysFor.invoke(null, o)).floatValue();
        t.spent = fDaysSpent.getFloat(o);
        Map<String, Integer> it = (Map<String, Integer>) fItems.get(o);
        if (it != null) for (Map.Entry<String, Integer> e : it.entrySet()) if (e.getValue() != null && e.getValue() > 0) t.items.put(e.getKey(), e.getValue());
        List<String> req = (List<String>) fReq.get(o);
        if (req != null) t.prereqs.addAll(req);
        Map<String, Object> rw = (Map<String, Object>) fRewards.get(o);
        if (rw != null) for (Map.Entry<String, Object> e : rw.entrySet()) {
            if (e.getValue() instanceof Enum && "INDUSTRY".equals(((Enum<?>) e.getValue()).name()) && Global.getSettings().getIndustrySpec(e.getKey()) != null) t.industries.add(e.getKey());
        }
        for (String[] g : GATES) if (g[0].equals(t.id)) t.industries.add(g[1]);
        boolean flagOk = fOther.get(o) == null || fOtherMet.getBoolean(o);
        t.available = Boolean.TRUE.equals(canResearch.invoke(mgr, t.id, false));
        t.needsItems = !t.available && flagOk && Boolean.TRUE.equals(allReq.invoke(mgr, t.id)) && !Boolean.TRUE.equals(itemsOk.invoke(mgr, t.id));
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

    /** Every tech you could start now or once its items are in storage, valued and ranked, best first. Refreshed daily. */
    @SuppressWarnings("unchecked")
    public static List<Tech> ranked() throws Exception {
        if (cached != null && now() - cachedAt < 1f) return cached;
        Object mgr = mgr();
        List<Tech> out = new ArrayList<Tech>();
        if (mgr == null) return out;
        Set<String> done = new HashSet<String>();
        Map<String, Object> all = new LinkedHashMap<String, Object>();
        for (Object o : (List<Object>) repo.invoke(mgr)) {
            all.put(String.valueOf(fId.get(o)), o);
            if (fDone.getBoolean(o)) done.add(String.valueOf(fId.get(o)));
        }
        List<Tech> cands = new ArrayList<Tech>();
        Map<String, Tech> children = new LinkedHashMap<String, Tech>();
        for (Object o : all.values()) {
            if (fDone.getBoolean(o)) continue;
            Tech t = tech(mgr, o);
            if (t.available || t.needsItems) cands.add(t);
        }
        // techs one step past a candidate, for the "leads to" share
        for (Object o : all.values()) {
            if (fDone.getBoolean(o)) continue;
            Tech t = tech(mgr, o);
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
        int fac = Math.max(1, ((Number) facilities.invoke(mgr)).intValue());
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
            for (Tech t : techs) { was.put(t.raw, fDone.getBoolean(t.raw)); fDone.setBoolean(t.raw, true); }
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
                try { fDone.setBoolean(e.getKey(), e.getValue()); } catch (Throwable ignored) { }
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
            if (focus.invoke(mgr) != null) return "Something is already being researched";
            sophiaFirst(mgr, t);
            if (!Boolean.TRUE.equals(canResearch.invoke(mgr, t.id, false))) {
                String moved = topUp(mgr, t);
                if (!Boolean.TRUE.equals(canResearch.invoke(mgr, t.id, false))) return "Not enough in the Research Facility tab (" + itemsText(t) + " needed)" + (moved == null ? "" : "; " + moved);
            }
            pay.invoke(mgr, t.id);
            setFocus.invoke(mgr, t.id);
            cached = null;
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
        List<Object> council = (List<Object>) fCouncil.get(mgr);
        if (council == null) return;
        for (Object s : council) {
            if (s == null || !s.getClass().getSimpleName().equals("SophiaAnderson") || s == fHead.get(mgr)) continue;
            fHead.set(mgr, s);
            councilAdvance.invoke(mgr, 0f);
            CALog.add("Research: made Sophia Anderson head of R&D (one databank less per tech)");
            return;
        }
    }

    /** Moves research databanks from Autopilot Storage to the Research Facility tab that holds the most already. */
    static String topUp(Object mgr, Tech t) throws Exception {
        int need = t.databanks() - (int) ((Number) itemCount.invoke(mgr, DATABANK)).floatValue();
        if (need <= 0) return null;
        CargoAPI st = CAData.get().storage();
        if (st.getCommodityQuantity(DATABANK) < need) return null;
        SubmarketAPI best = null; float bestHas = -1f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            SubmarketAPI sm = m.getSubmarket(SUBMARKET);
            if (sm == null || sm.getCargo() == null) continue;
            float has = sm.getCargo().getCommodityQuantity(DATABANK);
            if (has > bestHas) { bestHas = has; best = sm; }
        }
        if (best == null) return null;
        st.removeCommodity(DATABANK, need);
        best.getCargo().addCommodity(DATABANK, need);
        String msg = "moved " + need + " research databanks from Autopilot Storage to " + best.getMarket().getName() + "'s Research Facility tab";
        CALog.add("Research: " + msg);
        return msg;
    }

    @SuppressWarnings("unchecked")
    static boolean idle(Object mgr) throws Exception {
        if (focus.invoke(mgr) != null) return false;
        List<Object> q = (List<Object>) queued.invoke(queue.invoke(mgr));
        return q == null || q.isEmpty();
    }

    /** Auto: when nothing is being researched or queued, starts the best tech that costs only databanks and pays for itself. */
    public static void run(CAData d, CASettings s) {
        if (s.researchMode != 1) return;
        try {
            Object mgr = mgr();
            if (mgr == null || !idle(mgr)) return;
            if (((Number) facilities.invoke(mgr)).intValue() <= 0) {
                if (CALog.once("research:nofacility")) CALog.add("Research: no working Research Facility, so nothing can be researched");
                return;
            }
            CALog.forget("research:nofacility");
            if (CABudget.paused(s)) return;
            int fac = ((Number) facilities.invoke(mgr)).intValue();
            for (Tech t : ranked()) {
                if (t.rank <= 0f) break;
                if (!t.databanksOnly()) continue;
                float upkeep = t.tier * TIER_UPKEEP * fac;
                if (s.upkeepAdded + upkeep > s.newUpkeepCap) {
                    if (CALog.once("research:upkeep:" + t.id)) CALog.add("Research: " + t.name + " waits for next month (new upkeep cap reached)");
                    return;
                }
                if (t.needsItems && CAData.get().storage().getCommodityQuantity(DATABANK) + ((Number) itemCount.invoke(mgr, DATABANK)).floatValue() < t.databanks()) continue;
                if (start(t, "Auto") == null) { CABudget.charge(s, 0f, 0f, upkeep); return; }
            }
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
                + "or queued, only if it costs databanks alone.", 6f, hl, CASettings.CONTRACT_NAMES[Math.max(0, Math.min(2, s.researchMode))]);
        try {
            Object mgr = mgr();
            if (mgr == null) { t.addPara("Vaults of Knowledge's research hasn't started in this save yet.", gray, 6f); return; }
            int fac = ((Number) facilities.invoke(mgr)).intValue();
            Object cur = focus.invoke(mgr);
            if (cur != null) {
                Tech c = tech(mgr, cur);
                t.addPara("Researching %s: %s of %s days.", 6f, hl, c.name, "" + Math.round(c.spent), "" + Math.round(c.days));
            } else t.addPara("Nothing is being researched.", 6f);
            if (fac <= 0) t.addPara("No working Research Facility, so research doesn't advance.", Misc.getNegativeHighlightColor(), 4f);
            else t.addPara("%s research facilities. Databanks in their storage: %s; in Autopilot Storage: %s.", 4f, hl, "" + fac,
                    "" + (int) ((Number) itemCount.invoke(mgr, DATABANK)).floatValue(), "" + (int) d.storage().getCommodityQuantity(DATABANK));
            boolean idle = idle(mgr);
            int shown = 0;
            for (Tech x : ranked()) {
                if (shown++ >= 6) break;
                t.addPara("%s (%s days, %s): %s a month" + (x.needsItems ? ", needs its items in storage" : "") + (!x.databanksOnly() ? ", uses items Auto won't spend" : ""),
                        8f, x.rank > 0f ? hl : gray, x.name, "" + Math.round(Math.max(1f, x.days - x.spent)), itemsText(x), CAEngine.valueText(x.value));
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
