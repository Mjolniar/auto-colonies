package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketConditionSpecAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Colony Autopilot diagnostics. Every building is classified: SUPPORTED (formula or estimate), MEASURED (its effects
 * were measured on a probe colony, see CASimulator), ANALYZED (can't be measured; never acted on) or QUARANTINED
 * (its code failed during planning). Results are logged and written to a report.
 */
public class CADiagnostics {
    public enum Level { SUPPORTED, MEASURED, ANALYZED, QUARANTINED }

    /** Buildings the manager may score and build: supported, or measured on a probe colony. */
    public static boolean usable(String id) { Level l = industryLevel(id); return l == Level.SUPPORTED || l == Level.MEASURED; }

    /** Buildings Auto actually builds: supported, or measured unless "Mod buildings with measured effects" is Ignore (0.2.38.0). */
    public static boolean autopilotBuilds(String id) {
        Level l = industryLevel(id);
        if (l == Level.SUPPORTED) return true;
        try { return l == Level.MEASURED && CAData.settings().measuredMode != 2; } catch (Throwable t) { return l == Level.MEASURED; }
    }

    public static class Issue {
        public String kind, id, name, mod, detail;
        public Level level;
        Issue(String kind, String id, String name, String mod, Level level, String detail) {
            this.kind = kind; this.id = id; this.name = name; this.mod = mod; this.level = level; this.detail = detail;
        }
    }

    public static final Logger LOG = Global.getLogger(CADiagnostics.class);
    public static final String TAG = "[ColonyAutopilot] ";
    public static final String REPORT = "colony_autopilot_diagnostics.json";

    /** Mods whose colony conditions are mapped in the design doc; their conditions are read through live stats. */
    static final Set<String> MAPPED_MODS = new HashSet<String>(Arrays.asList(
            "IndEvo", "terraformingmadeeasy", "GrandColonies", "nexerelin", "jonah_tweaks", "Imperium",
            "knights_of_ludd", "assortment_of_things", "EmergentThreats_IX_Revival", "EmergentThreats_Vice", "armaa", "secretsofthefrontier", "underworld"));

    static final Map<String, Level> industries = new HashMap<String, Level>();
    static final Map<String, Level> conditions = new HashMap<String, Level>();
    static final Map<String, Level> items = new HashMap<String, Level>();
    static final List<Issue> issues = new ArrayList<Issue>();
    static final Set<String> issueKeys = new HashSet<String>();
    static String lastScan = "never";

    public static Level industryLevel(String id) { Level l = industries.get(id); return l == null ? Level.QUARANTINED : l; }
    public static Level conditionLevel(String id) { Level l = conditions.get(id); return l == null ? Level.ANALYZED : l; }
    public static Level itemLevel(String id) { Level l = items.get(id); return l == null ? Level.ANALYZED : l; }
    public static List<Issue> getIssues() { return issues; }
    public static String getLastScan() { return lastScan; }

    public static int count(Level level) {
        int n = 0;
        for (Issue i : issues) if (i.level == level) n++;
        return n;
    }

    static String modOf(ModSpecAPI mod) { return mod == null ? "vanilla" : mod.getId(); }

    public static void runScan() {
        industries.clear(); conditions.clear(); items.clear(); issues.clear(); issueKeys.clear();
        CASimulator.reset();
        CAConst.reset();
        CABreaker.reset();   // a reload tries paused actions again
        pausedStarts.clear();   // 0.3.0.0: and vanilla buildings whose start failed (their levels were just cleared with the rest)
        try { CACompat.check(); }   // which mod integrations still find what they rely on
        catch (Throwable t) { LOG.warn(TAG + "integration health check failed", t); }
        List<MarketAPI> mine = Global.getSector() == null ? new ArrayList<MarketAPI>() : Misc.getPlayerMarkets(false);
        MarketAPI template = mine.isEmpty() ? null : mine.get(0);   // measurements use your first colony's size and deposits
        MarketAPI probe = null;
        try { probe = Global.getFactory().createMarket("jt_ca_probe", "Autopilot Probe", 6); }
        catch (Throwable t) { LOG.warn(TAG + "could not create probe market", t); }

        for (IndustrySpecAPI spec : Global.getSettings().getAllIndustrySpecs()) {
            String mod = modOf(spec.getSourceMod());
            if (CAEngine.isSupported(spec.getId())) {
                industries.put(spec.getId(), Level.SUPPORTED);
                continue;
            }
            if (spec.hasTag("parent_item")) { industries.put(spec.getId(), Level.ANALYZED); continue; }
            String left = CAMods.leftToYou(spec.getId());
            if (left != null) {   // never test-built: some of these throw or reach into the sector on a probe colony
                industries.put(spec.getId(), Level.ANALYZED);
                addIssue("building", spec.getId(), spec.getName(), mod, Level.ANALYZED, "Left to you: " + left);
                continue;
            }
            if (CATasc.unsafeToProbe(spec)) {   // spawns entities, removes itself, or fails without a planet: never test-built
                industries.put(spec.getId(), Level.ANALYZED);
                addIssue("building", spec.getId(), spec.getName(), mod, Level.ANALYZED, "Left to you: a TASC building whose effects need its planet or time");
                continue;
            }
            String detail = probeIndustry(spec, probe);
            Level level = detail.startsWith("ERROR") ? Level.QUARANTINED : Level.ANALYZED;
            if (level == Level.ANALYZED && measurable(spec) && Global.getSector() != null) {
                CASimulator.Effects ef = CASimulator.measure(template, spec.getId());
                detail = detail.replace("; effects not simulated (mod code is not run)", "") + "; " + CASimulator.report(ef);
                if (ef.error == null) level = Level.MEASURED;
            }
            industries.put(spec.getId(), level);
            addIssue("building", spec.getId(), spec.getName(), mod, level, detail);
        }
        for (MarketConditionSpecAPI spec : Global.getSettings().getAllMarketConditionSpecs()) {
            ModSpecAPI src = spec.getSourceMod();
            if (src == null || MAPPED_MODS.contains(src.getId())) { conditions.put(spec.getId(), Level.SUPPORTED); continue; }
            // a deposit registered in the game's own tables is scored like a vanilla one (CAEngine.deposits)
            if (com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition.COMMODITY.containsKey(spec.getId())
                    && com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition.MODIFIER.containsKey(spec.getId())) {
                conditions.put(spec.getId(), Level.SUPPORTED);
                continue;
            }
            conditions.put(spec.getId(), Level.ANALYZED);
            addIssue("condition", spec.getId(), spec.getName(), src.getId(), Level.ANALYZED,
                    "Unmapped condition. Read through the colony's live stats only");
        }
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            ModSpecAPI src = spec.getSourceMod();
            items.put(spec.getId(), src == null ? Level.SUPPORTED : Level.ANALYZED);
        }
        lastScan = Global.getSector() != null && Global.getSector().getClock() != null
                ? Global.getSector().getClock().getDateString() : new Date().toString();
        LOG.info(TAG + "scan done: " + industries.size() + " buildings, " + conditions.size() + " conditions, "
                + items.size() + " items; " + count(Level.MEASURED) + " measured, " + count(Level.ANALYZED) + " analyzed, " + count(Level.QUARANTINED) + " quarantined");
        try { CAModWatch.run(); }   // how each colony mod is doing in this game (0.2.38.0)
        catch (Throwable t) { LOG.warn(TAG + "mod watch failed", t); }
        writeReport();
    }

    /** Buildings worth measuring: ones a colony could build. Population, hidden and menu-less buildings are skipped. */
    static boolean measurable(IndustrySpecAPI spec) {
        return !spec.hasTag("population") && !spec.hasTag("do_not_show_in_build_dialog") && !spec.hasTag("hide_in_codex") && !spec.hasTag("parent_item");
    }

    /** Safe probe: reads costs, tags and build checks only. Never applies a mod building's effects. */
    static String probeIndustry(IndustrySpecAPI spec, MarketAPI probe) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("cost ").append(Misc.getDGSCredits(spec.getCost())).append(", ")
              .append((int) spec.getBuildTime()).append(" days, base upkeep ").append(Misc.getDGSCredits(spec.getUpkeep())).append(" (times size - 2)")
              .append(", tags ").append(spec.getTags());
            if (probe != null) {
                Industry ind = spec.getNewPluginInstance(probe);
                sb.append(ind.isIndustry() ? "; uses an industry slot" : "; structure");
            }
            List<String> buildable = new ArrayList<String>();
            if (Global.getSector() != null) {
                for (MarketAPI m : Misc.getPlayerMarkets(false)) {
                    Industry ind = spec.getNewPluginInstance(m);
                    if (ind.isAvailableToBuild() && CAAotD.allows(spec.getId(), m)) buildable.add(m.getName());
                }
            }
            sb.append(buildable.isEmpty() ? "; buildable at none of your colonies" : "; buildable at " + buildable);
            sb.append("; effects not simulated (mod code is not run)");
        } catch (Throwable t) {
            LOG.warn(TAG + "probe failed for " + spec.getId(), t);
            return "ERROR during probe: " + t;
        }
        return sb.toString();
    }

    static void addIssue(String kind, String id, String name, String mod, Level level, String detail) {
        String key = kind + ":" + id;
        if (issueKeys.contains(key)) {
            for (Issue i : issues) if ((i.kind + ":" + i.id).equals(key)) { i.level = level; i.detail = detail; }
            return;
        }
        issueKeys.add(key);
        issues.add(new Issue(kind, id, name, mod, level, detail));
        if (level == Level.QUARANTINED) LOG.warn(TAG + "QUARANTINED " + kind + " " + id + " (" + mod + "): " + detail);
        else LOG.info(TAG + level + " " + kind + " " + id + " (" + mod + "): " + detail);
    }

    /**
     * Runtime guard: an error from a mod building's own code (or the game's code running it) during planning quarantines
     * that building for the session.
     * 0.3.0.0 (research 07 H5): a vanilla building is never quarantined, and in a planning check (where says "check",
     * "reading", "scoring", "cost") neither is one whose error is Auto Colonies' own (its topmost frame outside the JDK is
     * CM's): it's logged once per building and kind of error for the session, with a count in the report, and the caller
     * skips the pick as before. Before, a CM null pointer in scoring took the building (farming included) off every
     * colony's list. A failed action on a mod building (installing, starting a project) still quarantines it whoever's
     * error it was: that's what stops the action being tried again every day. Nothing happens under a what-if valuation
     * (07 M2), and the report is written once a pass instead of once per quarantine (reportLater, flushReport).
     * 0.3.0.0 (fix): a vanilla building whose construction or upgrade failed to start (CABuild.start's "starting construction
     * at X") is paused for the session instead (pausesStart): Autopilot doesn't pick it again until the next load or rescan,
     * one line in starsector.log and a Diagnostics entry. Without that stop, the same pick won every pass, failed again and
     * put "could not start" in the panel log each time; 0.2.45.1 stopped it by quarantining the building.
     */
    public static void quarantine(String industryId, String where, Throwable t) {
        if (CALog.whatIf()) return;
        IndustrySpecAPI spec = null;
        try { spec = Global.getSettings().getIndustrySpec(industryId); } catch (Throwable ignored) { }
        boolean vanilla = (spec != null && spec.getSourceMod() == null) || CAEngine.isSupportedVanilla(industryId);
        if (pausesStart(vanilla, where)) { pauseStart(industryId, spec, where, t); return; }
        Boolean cm = cmFault(t);
        String kind = industryId + "|" + (t == null ? "?" : root(t).getClass().getName());
        boolean seen = cmSeen.contains(kind);
        if (Boolean.TRUE.equals(cm)) cmSeen.add(kind);
        if (!quarantines(vanilla, planning(where), cm, seen)) {
            String top = topFrame(t), key = kind + "|" + top;
            if (top.equals("?")) for (String k : cmErrors.keySet()) if (k.startsWith(kind + "|")) { key = k; break; }   // a stackless repeat counts with its first
            Integer n = cmErrors.get(key);
            cmErrors.put(key, n == null ? 1 : n + 1);
            if (n == null) LOG.warn(TAG + (vanilla ? "error with vanilla building " : "Auto Colonies error with ") + industryId + " in " + where
                    + "; not quarantined, logged once a session (" + (Boolean.TRUE.equals(cm) || seen ? "in Auto Colonies' own code" : "vanilla is never quarantined") + ")", t);
            return;
        }
        industries.put(industryId, Level.QUARANTINED);
        String name = spec == null ? industryId : spec.getName();
        String mod = spec == null ? "unknown" : modOf(spec.getSourceMod());
        LOG.warn(TAG + "runtime error in " + where + " for " + industryId, t);
        addIssue("building", industryId, name, mod, Level.QUARANTINED, "ERROR in " + where + ": " + t);
        reportLater();
    }

    /**
     * 0.3.0.0 (fix): vanilla buildings paused for the session after a failed start, id -> what failed (in the report as
     * pausedStarts). Session only: cleared on load (reset) and by every scan, so a reload or a rescan tries them again.
     */
    static final Map<String, String> pausedStarts = new LinkedHashMap<String, String>();

    /** Whether a vanilla building's construction or upgrade is paused for the session after it failed to start. */
    public static boolean startPaused(String industryId) { return pausedStarts.containsKey(industryId); }

    /** Whether an error pauses a building's construction for the session: a vanilla one whose start failed. Pure. */
    static boolean pausesStart(boolean vanilla, String where) {
        if (!vanilla || where == null || planning(where)) return false;
        return where.toLowerCase(java.util.Locale.ROOT).startsWith("starting construction");
    }

    /**
     * Pauses a vanilla building's construction for the session: it reads as quarantined, so the planner doesn't pick it and
     * CABuild.start refuses it (usable), at every colony. One starsector.log line per building a session; the Diagnostics
     * entry says why. Planning errors never do this (quarantines keeps vanilla out of those).
     */
    static void pauseStart(String industryId, IndustrySpecAPI spec, String where, Throwable t) {
        boolean first = !pausedStarts.containsKey(industryId);
        String what = t == null ? "unknown error" : String.valueOf(root(t));
        pausedStarts.put(industryId, where + ": " + what);
        industries.put(industryId, Level.QUARANTINED);
        if (first) try { CACache.bump("start paused"); } catch (Throwable ignored) { }   // evaluations kept today still offered it
        if (first) LOG.warn(TAG + "vanilla building " + industryId + " failed in " + where + "; not started again this session (a reload tries again)", t);
        addIssue("building", industryId, spec == null ? industryId : spec.getName(), "vanilla", Level.QUARANTINED,
                "Paused for this session: " + where + " failed (" + what + "). Vanilla buildings are not quarantined for planning errors. A failed start pauses one until the next load");
        reportLater();
    }

    /** 0.3.0.0: errors that quarantined nothing, "id|exception|top frame" -> how many this session (in the report as cmErrors). */
    static final Map<String, Integer> cmErrors = new LinkedHashMap<String, Integer>();
    /** 0.3.0.0: "id|exception" seen in CM's own code this session, so a repeat whose stack the JVM left out is still CM's. */
    static final Set<String> cmSeen = new HashSet<String>();

    /**
     * Whether an error quarantines the building: never vanilla; in a planning check never CM's own bug (or a stackless
     * repeat of one); a failed action on a mod building always (the session's stop for that action). Pure.
     */
    static boolean quarantines(boolean vanilla, boolean planning, Boolean cmFault, boolean seenAsCm) {
        if (vanilla) return false;
        if (!planning) return true;
        if (Boolean.TRUE.equals(cmFault)) return false;
        return !(cmFault == null && seenAsCm);
    }

    /**
     * Whether the caller's `where` names a planning check (reading, scoring, costs: "build check at X", "reading terraforming
     * projects", "improvement cost") rather than an action ("installing", "starting construction at X"). Pure.
     */
    static boolean planning(String where) {
        if (where == null) return false;
        String w = where.toLowerCase(java.util.Locale.ROOT);
        int at = w.indexOf(" at ");
        if (at >= 0) w = w.substring(0, at);   // never read a colony's name as the kind ("... at Check Point")
        return w.contains("check") || w.startsWith("reading") || w.startsWith("scoring") || w.endsWith(" cost");
    }

    /** The innermost cause (an InvocationTargetException's target, a wrapped mod error), at most 10 deep. */
    static Throwable root(Throwable t) {
        Throwable r = t;
        for (int i = 0; i < 10 && r.getCause() != null && r.getCause() != r; i++) r = r.getCause();
        return r;
    }

    /** Whether the error is Auto Colonies' own: true, false, or null when the JVM left out the stack trace. */
    static Boolean cmFault(Throwable t) {
        if (t == null) return null;
        return cmFault(root(t).getStackTrace());
    }

    /** The topmost frame outside the JDK decides: CM's package means CM's bug, anything else (the game, a mod) the building's. Pure. */
    static Boolean cmFault(StackTraceElement[] frames) {
        if (frames == null) return null;
        for (StackTraceElement f : frames) {
            String c = f.getClassName();
            if (c == null || jdk(c)) continue;
            return c.startsWith(CM_PACKAGE);
        }
        return null;
    }

    static final String CM_PACKAGE = "data.scripts.jonahtweaks.";

    static boolean jdk(String cls) {
        return cls.startsWith("java.") || cls.startsWith("javax.") || cls.startsWith("jdk.") || cls.startsWith("sun.") || cls.startsWith("com.sun.");
    }

    static String topFrame(Throwable t) {
        try {
            for (StackTraceElement f : root(t).getStackTrace()) if (!jdk(f.getClassName())) return f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber();
        } catch (Throwable ignored) { }
        return "?";
    }

    /** 0.3.0.0: the report needs writing (07 H5); flushReport writes it once, at the end of the pass. */
    static boolean dirty = false;

    /** Marks the report for writing at the next flushReport (end of the daily pass, the weekly mod watch, the next scan). */
    public static void reportLater() { dirty = true; }

    /** Writes the report if anything marked it since the last write. */
    public static void flushReport() { if (dirty) writeReport(); }

    /** 0.3.0.0 (07 H6): the session's error memory, cleared on load (CAReset.onLoad); the scan results are replaced by runScan. */
    public static void reset() { cmErrors.clear(); cmSeen.clear(); pausedStarts.clear(); dirty = false; }

    public static List<String> unsupportedConditions(MarketAPI market) {
        List<String> out = new ArrayList<String>();
        for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : market.getConditions()) {
            if (conditionLevel(c.getId()) != Level.SUPPORTED) out.add(c.getName());
        }
        return out;
    }

    public static void writeReport() {
        dirty = false;
        try {
            JSONObject root = new JSONObject();
            root.put("scan", lastScan);
            JSONArray mods = new JSONArray();
            for (ModSpecAPI mod : Global.getSettings().getModManager().getEnabledModsCopy()) {
                mods.put(mod.getId() + " " + mod.getVersion());
            }
            root.put("enabledMods", mods);
            JSONArray arr = new JSONArray();
            for (Issue i : issues) {
                JSONObject o = new JSONObject();
                o.put("kind", i.kind); o.put("id", i.id); o.put("name", i.name);
                o.put("mod", i.mod); o.put("level", i.level.name()); o.put("detail", i.detail);
                arr.put(o);
            }
            root.put("issues", arr);
            // 0.3.0.0: integration health with the link scan's findings, errors that quarantined nothing, paused actions, the
            // colony-screen button's link and per-step timing, so the report says why Diagnostics is red
            try { root.put("integrations", CACompat.json()); } catch (Throwable t) { LOG.warn(TAG + "integration report failed", t); }
            try { if (!cmErrors.isEmpty()) root.put("cmErrors", new JSONObject(cmErrors)); } catch (Throwable t) { LOG.warn(TAG + "error report failed", t); }
            try { if (!pausedStarts.isEmpty()) root.put("pausedStarts", new JSONObject(pausedStarts)); } catch (Throwable t) { LOG.warn(TAG + "paused starts report failed", t); }
            try { if (!CABreaker.tripped().isEmpty()) root.put("paused", new JSONObject(CABreaker.tripped())); } catch (Throwable t) { LOG.warn(TAG + "breaker report failed", t); }
            try { Map<String, String> sb = CAStep.broken(); if (!sb.isEmpty()) root.put("stoppedSteps", new JSONObject(sb)); } catch (Throwable t) { LOG.warn(TAG + "stopped steps report failed", t); }
            try { root.put("screenButton", CAScreenButton.linkText()); } catch (Throwable t) { LOG.warn(TAG + "screen button report failed", t); }
            try { Map<String, Float> st = CAStep.timings(); if (st != null && !st.isEmpty()) root.put("stepMs", new JSONObject(st)); } catch (Throwable t) { LOG.warn(TAG + "step timing report failed", t); }
            try { root.put("modWatch", CAModWatch.json()); } catch (Throwable t) { LOG.warn(TAG + "mod watch report failed", t); }
            try { if (Global.getSector() != null && CAMarket.on()) root.put("market", CAMarket.json()); } catch (Throwable t) { LOG.warn(TAG + "market report failed", t); }
            try { if (Global.getSector() != null) root.put("colonies", colonies()); } catch (Throwable t) { LOG.warn(TAG + "colony report failed", t); }
            Global.getSettings().writeTextFileToCommon(REPORT, root.toString(2));
        } catch (Throwable t) {
            LOG.warn(TAG + "could not write diagnostics report", t);
        }
    }

    /**
     * Each of your colonies as Autopilot sees it (0.2.39.0), so a check of how the plans are going doesn't need the save:
     * mode and goal, the plan and where each step stands, buildings with their items, cores and construction, the
     * construction queue and what's waiting in its Autopilot Storage. Read only: nothing is created for a colony Autopilot
     * hasn't seen.
     */
    static JSONArray colonies() throws Exception {
        JSONArray out = new JSONArray();
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            JSONObject o = new JSONObject();
            CAData.Colony c = d.colonies == null ? null : d.colonies.get(m.getId());
            o.put("name", m.getName()); o.put("id", m.getId()); o.put("size", m.getSize());
            o.put("mode", c == null || c.mode == null ? "none" : c.mode.name());
            if (c != null && c.profile != null) o.put("goal", c.profile.name());
            o.put("stability", Math.round(m.getStability().getModifiedValue()));
            o.put("netIncome", Math.round(m.getNetIncome()));
            // 0.3.0.0 (07 M12): CABuild.usedSlots is the one slot count (upgrades under way count as what they become); the game's own when it differs
            int used = Misc.getNumIndustries(m);
            try { used = CABuild.usedSlots(m); } catch (Throwable ignored) { }
            o.put("industrySlots", used + " of " + Misc.getMaxIndustries(m) + (used != Misc.getNumIndustries(m) ? " (the game counts " + Misc.getNumIndustries(m) + ")" : ""));
            JSONArray inds = new JSONArray();
            for (Industry ind : m.getIndustries()) {
                if (ind.isHidden()) continue;
                StringBuilder sb = new StringBuilder(ind.getId());
                if (ind.isBuilding()) sb.append(ind.isUpgrading() ? " [upgrading]" : " [building]");
                else if (!ind.isFunctional()) sb.append(" [not working]");
                if (ind.getSpecialItem() != null) sb.append(" item=").append(ind.getSpecialItem().getId());
                if (ind.getAICoreId() != null) sb.append(" core=").append(ind.getAICoreId());
                if (ind.isImproved()) sb.append(" improved");
                inds.put(sb.toString());
            }
            o.put("industries", inds);
            try {
                JSONArray q = new JSONArray();
                for (com.fs.starfarer.api.impl.campaign.econ.impl.ConstructionQueue.ConstructionQueueItem it : m.getConstructionQueue().getItems()) q.put(it.id);
                if (q.length() > 0) o.put("queue", q);
            } catch (Throwable ignored) { }
            if (c != null && c.profile != null) {
                try {
                    CAPlan.Plan plan = CAPlan.build(m, c.profile);
                    o.put("plan", plan.name);
                    if (plan.world != null) o.put("world", plan.world);
                    JSONArray steps = new JSONArray();
                    for (CAPlan.Step s : plan.steps) {
                        String at = s.done(m) ? "done" : CAPlan.BLOCKED.contains(m.getId() + ":" + s.id) ? "waits on research"
                                : CAPlan.dead(m, s.id) ? "out of reach" : CAPlan.held(m, plan, s) ? "waits for the rest of the plan" : "open";
                        steps.put(s.kind + " " + s.id + ": " + at + " (" + s.label + ")");
                    }
                    o.put("steps", steps);
                } catch (Throwable t) { o.put("plan", "failed: " + t); }
            }
            com.fs.starfarer.api.campaign.CargoAPI store = d.stores().get(m.getId());
            if (store != null) {
                JSONObject held = new JSONObject();
                for (com.fs.starfarer.api.campaign.CargoStackAPI st : store.getStacksCopy()) {
                    String id = st.isSpecialStack() && st.getSpecialDataIfSpecial() != null ? st.getSpecialDataIfSpecial().getId()
                            : st.isCommodityStack() ? st.getCommodityId() : st.isWeaponStack() ? "weapon " + st.getWeaponSpecIfWeapon().getWeaponId() : "other";
                    held.put(id, held.optInt(id, 0) + Math.round(st.getSize()));
                }
                o.put("autopilotStorage", held);
            }
            out.put(o);
        }
        return out;
    }
}
