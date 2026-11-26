package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.28.0 (plan 2026-11-25, batch 5, part 2): Black Site special projects, ask first (Settings, Special projects). With a
 * working Black Site, no project running and colony net at the Production line, one discovered project that was never done
 * is proposed (Approvals, kind "project"): projects whose first stage has its credits, items and ships ready and whose goods
 * your colonies make, the ones with no short goods first, then the cheapest. Projects tagged dangerous (the Shroud ones and
 * the Shadowlance) are never proposed. Approving it starts the project and its first stage the way the Black Site's screen
 * does; after that Autopilot starts each next stage when the last one is done and its credits fit the budget, one stage at a
 * time. A stage you pause, or a project you stop, is left to you. Needs Vaults of Knowledge.
 */
public final class CABlackSite {
    private CABlackSite() { }

    static final String PROJECT = "data.kaysaar.aotd.vok.scripts.specialprojects.models.AoTDSpecialProject";
    static final String DANGEROUS = "dangerous";
    /** CAMark: a project Autopilot started after your approval, and a stage it started. */
    static final String STARTED = "bsproj:", STAGE = "bsstage:";

    static boolean broken = false;

    static void reset() { broken = false; }

    // ---------------- pure ----------------

    /** Pure: months a stage's goods are spread over (VoK: days left / 30, at least 1). */
    static int monthsOf(float days) { return Math.max(1, Math.round(days / 30f)); }

    /** Pure: a month's share of what a stage still takes. */
    static int monthly(int total, int months) { return total <= 0 ? 0 : (int) Math.ceil(total / (double) Math.max(1, months)); }

    /** Pure: what a new commitment can take of a good a month without leaving your colonies or other commitments short. */
    static int spare(int supply, int demand, int commitments) { return Math.max(0, supply - demand - commitments); }

    /** Pure: a project Autopilot may propose. */
    static boolean askable(Collection<String> tags, boolean discovered, boolean canDo, int completions, boolean startedBefore) {
        return discovered && canDo && completions == 0 && !startedBefore && (tags == null || !tags.contains(DANGEROUS));
    }

    /** Pure: the better of two proposals: no short goods first, then the cheaper first stage. */
    static boolean better(int shortA, float costA, int shortB, float costB) {
        if ((shortA == 0) != (shortB == 0)) return shortA == 0;
        return costA < costB;
    }

    // ---------------- the game ----------------

    static Class<?> cls(String name) throws Exception { return Class.forName(name, true, Global.getSettings().getScriptClassLoader()); }

    static Object mgr() throws Exception { return CARefl.invoke(CARefl.method(cls(CAProjects.MGR), "getInstance"), null); }

    /** A colony with a working Black Site, or null. */
    static MarketAPI blackSite() {
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            Industry b = m.getIndustry(CASurplusResearch.BLACK_SITE);
            if (b != null && b.isFunctional()) return m;
        }
        return null;
    }

    /** A stage that can start now: its credits (0 when paid), goods a month, and its place in the project. */
    static final class Stage {
        Object raw;
        String id, name;
        int index;
        float credits;
        final Map<String, Integer> goods = new LinkedHashMap<String, Integer>();
    }

    static String projectId(Object p) throws Exception { return String.valueOf(CARefl.call(CARefl.call(p, "getProjectSpec"), "getId")); }

    static String projectName(Object p) throws Exception {
        Object nm = CARefl.call(p, "getNameOverride");
        return nm instanceof String && !((String) nm).isEmpty() ? (String) nm : String.valueOf(CARefl.call(CARefl.call(p, "getProjectSpec"), "getName"));
    }

    @SuppressWarnings("unchecked")
    static List<Object> stages(Object p) throws Exception {
        Object l = CARefl.call(p, "getStages");
        return l instanceof List ? (List<Object>) l : Collections.<Object>emptyList();
    }

    static String stageId(Object st) throws Exception { return String.valueOf(CARefl.call(CARefl.call(st, "getSpec"), "getId")); }

    static boolean done(Object st) throws Exception { return Boolean.TRUE.equals(CARefl.call(st, "isCompleted")); }

    /** The first stage VoK lets start now with its credits, items and ships ready, or null. */
    @SuppressWarnings("unchecked")
    static Stage nextStage(Object p) throws Exception {
        Object canAttempt = CARefl.method(cls(PROJECT), "canAttemptStage", String.class);
        int i = 0;
        for (Object st : stages(p)) {
            i++;
            if (done(st)) continue;
            String sid = stageId(st);
            if (!Boolean.TRUE.equals(CARefl.invoke(canAttempt, p, sid)) || !Boolean.TRUE.equals(CARefl.call(st, "haveMetCriteriaToStartOrResumeStage"))) continue;
            Object spec = CARefl.call(st, "getSpec");
            Stage s = new Stage();
            s.raw = st; s.id = sid; s.index = i;
            s.name = String.valueOf(CARefl.call(spec, "getName"));
            s.credits = Boolean.TRUE.equals(CARefl.call(st, "isPaidForStage")) ? 0f : ((Number) CARefl.call(spec, "getCreditCosts")).floatValue();
            int months = monthsOf(((Number) CARefl.call(st, "getDaysLeft")).floatValue());
            Object rem = CARefl.call(st, "getTotalGPCostFromStageAfterDelivery");
            if (rem instanceof Map) for (Map.Entry<String, Integer> e : ((Map<String, Integer>) rem).entrySet())
                if (e.getValue() != null && e.getValue() > 0) s.goods.put(e.getKey(), monthly(e.getValue(), months));
            return s;
        }
        return null;
    }

    /** Credits of the stages after the first, paid as each starts. */
    static float laterCredits(Object p, String firstId) throws Exception {
        float n = 0f;
        for (Object st : stages(p)) {
            if (done(st) || Boolean.TRUE.equals(CARefl.call(st, "isPaidForStage")) || stageId(st).equals(firstId)) continue;
            n += ((Number) CARefl.call(CARefl.call(st, "getSpec"), "getCreditCosts")).floatValue();
        }
        return n;
    }

    /** The reward in words: ships, weapons, items and commodities by name. Custom rewards have no name here. */
    @SuppressWarnings("unchecked")
    static List<String> rewards(Object p) {
        List<String> out = new ArrayList<String>();
        try {
            Object l = CARefl.call(CARefl.call(p, "getProjectSpec"), "getRewards");
            if (l instanceof List) for (Object r : (List<Object>) l) {
                String type = String.valueOf(CARefl.get(CARefl.field(r.getClass(), "type"), r)), id = String.valueOf(CARefl.get(CARefl.field(r.getClass(), "id"), r));
                String n = null;
                try {
                    if ("SHIP".equals(type)) n = "the " + Global.getSettings().getHullSpec(id).getHullName();
                    else if ("WEAPON".equals(type)) n = "the " + Global.getSettings().getWeaponSpec(id).getWeaponName();
                    else if ("ITEM".equals(type)) n = "a " + Global.getSettings().getSpecialItemSpec(id).getName();
                    else if ("COMMODITY".equals(type) || "AICORE".equals(type)) n = Global.getSettings().getCommoditySpec(id).getName();
                } catch (Throwable ignored) { }
                if (n != null) out.add(n);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** Daily (after the stalled restorations step). */
    public static void run() {
        CASettings s = CAData.settings();
        if (broken || CALog.whatIf() || s.projectAsks != 0 || !Global.getSettings().getModManager().isModEnabled(CAHypershunt.MOD)) return;
        try { step(s); }
        catch (Throwable t) {
            boolean lookup = t instanceof ClassNotFoundException || t instanceof NoSuchMethodException || t instanceof NoSuchFieldException || t instanceof LinkageError;
            if (lookup) { broken = true; CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Vaults of Knowledge's special projects; none are proposed", t); }
            else CAStep.warn("special projects", "special projects failed today", t);
        }
    }

    @SuppressWarnings("unchecked")
    static void step(CASettings s) throws Exception {
        MarketAPI site = blackSite();
        if (site == null) return;
        Object mgr = mgr();
        if (mgr == null) return;
        Object cur = CARefl.call(mgr, "getCurrentlyOnGoingProject");
        if (cur != null) { CAApprovals.dropLive("project:", null); next(s, mgr, cur, site); return; }
        if (!CANeeds.lineMet(s) || CANeeds.get() == null) return;
        Object best = null;
        Stage bestStage = null;
        List<String> bestShort = null;
        Object projects = CARefl.call(mgr, "getProjects");
        if (!(projects instanceof Map)) return;
        for (Object p : ((Map<String, Object>) projects).values()) {
            Object spec = CARefl.call(p, "getProjectSpec");
            if (spec == null) continue;
            String id = projectId(p);
            Object tags = CARefl.call(spec, "getTags");
            if (!askable(tags instanceof Collection ? (Collection<String>) tags : null, Boolean.TRUE.equals(CARefl.call(p, "wasEverDiscovered")),
                    Boolean.TRUE.equals(CARefl.call(p, "canDoProject")), ((Number) CARefl.call(p, "getCountOfCompletion")).intValue(), CAMark.at(STARTED + id) != null)) continue;
            Stage st = nextStage(p);
            if (st == null) {
                if (CALog.once("bsproj:wait:" + id)) CADiagnostics.LOG.info(CADiagnostics.TAG + "special project " + projectName(p) + " not proposed: no stage can start (credits, items or ships)");
                continue;
            }
            List<String> unmade = new ArrayList<String>(), shortGoods = new ArrayList<String>();
            for (Map.Entry<String, Integer> e : st.goods.entrySet()) {
                CANeeds.Good g = CANeeds.get().get(e.getKey());
                if (g == null || g.supply <= 0) unmade.add(CANeeds.name(e.getKey()));
                else if (spare(g.supply, g.demand, g.commitments()) < e.getValue()) shortGoods.add(CANeeds.name(e.getKey()));
            }
            if (!unmade.isEmpty()) {
                if (CALog.once("bsproj:unmade:" + id)) CADiagnostics.LOG.info(CADiagnostics.TAG + "special project " + projectName(p) + " not proposed: your colonies make no " + CAContracts.join(unmade));
                continue;
            }
            if (CAApprovals.reserveGate(st.credits) != null || !CAItems.canMove(s, st.credits)) continue;
            if (best == null || better(shortGoods.size(), st.credits, bestShort.size(), bestStage.credits)) { best = p; bestStage = st; bestShort = shortGoods; }
        }
        if (best != null) offer(best, bestStage, bestShort, site);
    }

    static String goodsText(Map<String, Integer> goods) {
        List<String> l = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : goods.entrySet()) l.add(Misc.getWithDGS(e.getValue()) + " " + CANeeds.name(e.getKey()));
        return CAContracts.join(l);
    }

    static void offer(Object p, Stage st, List<String> shortGoods, MarketAPI site) throws Exception {
        final String id = projectId(p);
        String name = projectName(p);
        int count = stages(p).size();
        float later = laterCredits(p, st.id);
        List<String> d = new ArrayList<String>();
        d.add("The Black Site at " + site.getName() + " will start " + name + ", which has " + count + (count == 1 ? " stage" : " stages") + ".");
        d.add("Stage " + st.index + " costs " + Misc.getDGSCredits(st.credits) + " now" + (st.goods.isEmpty() ? "." : ", then " + goodsText(st.goods) + " a month from your exports until it's done."));
        if (later > 0f) d.add("The other stages cost " + Misc.getDGSCredits(later) + " in all, paid as each one starts, and each one starts once the last is done.");
        List<String> r = rewards(p);
        if (!r.isEmpty()) d.add("When it's done you get " + CAContracts.join(r) + ".");
        if (!shortGoods.isEmpty()) d.add(CAContracts.join(shortGoods) + (shortGoods.size() == 1 ? " is" : " are") + " short at your colonies, so it'll go slower until there's more of it.");
        d.add("If you reject it, it won't be offered again for " + CAApprovals.holdText("project") + ".");
        CAApprovals.dropLive("project:", "project:" + id);
        CAApprovals.offer("project:" + id, "project", site, "Start the " + name + " special project", st.credits, 0f, null, () -> start(id), d.toArray(new String[0]));
    }

    /** The approval: the project, then its first stage that can start. */
    static String start(String id) {
        try {
            Object mgr = mgr();
            MarketAPI site = blackSite();
            if (mgr == null || site == null) return "There's no working Black Site";
            if (CARefl.call(mgr, "getCurrentlyOnGoingProject") != null) return "A project is already running at the Black Site";
            Object p = CARefl.invoke(CARefl.method(cls(CAProjects.MGR), "getProject", String.class), mgr, id);
            if (p == null) return "That project is gone";
            Stage st = nextStage(p);
            if (st == null) return "No stage can start now: credits, items or ships are missing";
            CARefl.invoke(CARefl.method(cls(CAProjects.MGR), "setCurrentlyOnGoingProject", cls(PROJECT)), mgr, p);
            CAMark.set(STARTED + id, CAMark.now());
            startStage(CAData.settings(), mgr, p, id, st, site);
            CALog.did("Black Site: " + projectName(p) + " started, stage " + st.index + " (" + st.name + ", " + Misc.getDGSCredits(st.credits) + ").", true);
            return null;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "starting special project " + id + " failed", t);
            return "That failed; see starsector.log";
        }
    }

    /** A stage, the way the Black Site's screen starts one (SpecialProjectStageWindow, start_stage). */
    @SuppressWarnings("unchecked")
    static void startStage(CASettings s, Object mgr, Object p, String projectId, Stage st, MarketAPI site) throws Exception {
        CARefl.call(st.raw, "payForStage");
        ((List<String>) CARefl.call(p, "getCurrentlyAttemptedStages")).add(st.id);
        Object contract = CARefl.call(mgr, "getContract");
        if (contract != null) {
            CARefl.call(contract, "reApplyChanges");
            if (CAContracts.link()) {
                Object active = CARefl.invoke(CAContracts.activeMap, CAContracts.mgr());
                String cid = String.valueOf(CARefl.invoke(CAContracts.cId, contract));
                if (active instanceof Map && !((Map<String, Object>) active).containsKey(cid)) CARefl.invoke(CAContracts.addContract, CAContracts.mgr(), contract);
            }
        }
        CAMark.set(STAGE + projectId + ":" + st.id, CAMark.now());
        CABudget.charge(s, site.getId(), st.credits, 0f, 0f);   // VoK takes the credits from your fleet; booked on the month's budget
    }

    /** A project Autopilot started: the next stage once none is running, unless you paused one of its stages. */
    @SuppressWarnings("unchecked")
    static void next(CASettings s, Object mgr, Object p, MarketAPI site) throws Exception {
        String id = projectId(p);
        if (CAMark.at(STARTED + id) == null) return;   // a project you started is yours
        List<String> attempted = (List<String>) CARefl.call(p, "getCurrentlyAttemptedStages");
        for (Object st : stages(p)) {
            if (done(st)) continue;
            String sid = stageId(st);
            if (attempted.contains(sid)) return;   // one is running
            if (CAMark.at(STAGE + id + ":" + sid) != null) {   // one Autopilot started is paused: you paused it
                if (CALog.once("bsproj:paused:" + id)) CADiagnostics.LOG.info(CADiagnostics.TAG + "special project " + projectName(p) + ": a stage was paused, so its next stages are left to you");
                return;
            }
        }
        Stage st = nextStage(p);
        if (st == null || CAApprovals.reserveGate(st.credits) != null || !CAItems.canMove(s, st.credits)) {
            if (CALog.once("bsproj:next:" + id + ":" + Global.getSector().getClock().getMonth()))
                CADiagnostics.LOG.info(CADiagnostics.TAG + "special project " + projectName(p) + ": the next stage waits for its credits, items or ships");
            return;
        }
        startStage(s, mgr, p, id, st, site);
        CALog.did("Black Site: " + projectName(p) + " stage " + st.index + " (" + st.name + ") started, " + Misc.getDGSCredits(st.credits) + ".");
    }
}
