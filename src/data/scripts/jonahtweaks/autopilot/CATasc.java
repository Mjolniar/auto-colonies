package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/**
 * Terraforming and Station Construction (Boggled, 10.0.9). Terraforming projects cost nothing but time (200 days, 400
 * for a planet type change) and need prerequisite buildings, so Autopilot values each project by the conditions it
 * leaves (CAConditions: hazard, deposits, habitability) and, in Auto, starts the best one when none is running, the way
 * TASC's own Start button does. A running project is never cancelled. The Atmosphere Processor and Stellar Reflector
 * Array are scored by the projects they unlock; a few other TASC buildings get estimates. The rest (station projects,
 * self-removing and one-shot buildings, the Remnant Station) are left to you, and none of TASC's buildings except
 * plain output ones is ever built on the probe colony: some spawn entities, remove themselves or fail without a planet.
 * Station construction needs your fleet on site, so it's yours too. Reflection only.
 */
public class CATasc {
    public static final String MOD = "Terraforming & Station Construction";
    static final String PROCESSOR = "BOGGLED_ATMOSPHERE_PROCESSOR", REFLECTOR = "BOGGLED_STELLAR_REFLECTOR_ARRAY";
    /** TASC buildings scored here. */
    static final Set<String> ESTIMATED = new HashSet<String>(Arrays.asList(PROCESSOR, REFLECTOR, "BOGGLED_DOMED_CITIES",
            "BOGGLED_GENELAB", "BOGGLED_CHAMELEON", "BOGGLED_MESOZOIC_PARK", "BOGGLED_KLETKA_SIMULATOR"));
    /** Plain output buildings that are safe to measure on the probe colony. */
    static final Set<String> PROBE_SAFE = new HashSet<String>(Arrays.asList("BOGGLED_HYDROPONICS", "BOGGLED_CLONING",
            "BOGGLED_CRYOSANCTUM", "BOGGLED_DOMAIN_ARCHAEOLOGY", "BOGGLED_HARMONIC_DAMPER"));
    /** Upkeep that doesn't scale with colony size. */
    static final Set<String> FIXED = new HashSet<String>(Arrays.asList(PROCESSOR, "BOGGLED_ISMARA_SLING", "BOGGLED_KLETKA_SIMULATOR",
            "BOGGLED_PERIHELION_PROJECT", "BOGGLED_PLANET_CRACKER", "BOGGLED_OUYANG_OPTIMIZER", "BOGGLED_REMNANT_STATION"));
    static final List<String> DOMED_SUPPRESSES = Arrays.asList("no_atmosphere", "thin_atmosphere", "dense_atmosphere", "toxic_atmosphere",
            "extreme_weather", "inimical_biosphere");
    static final float MIN_PROJECT = 1000f;
    static final String START = "starting TASC terraforming projects";

    // boggledTools names an optional mod's class (Illustrated Entities) in one signature, so plain reflection on it fails
    // without that mod; method handles resolve just the method asked for
    static MethodHandle projects, isStation, setting, controllerOf;
    static Object reqs, reqsMet, start, daysLeft, projName, after, current;
    static Object reqText, reqMet;
    static boolean linked = false, broken = false;

    public static boolean enabled() { return Global.getSettings().getModManager().isModEnabled(MOD); }

    static boolean link() {
        if (linked || broken) return linked;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> tools = Class.forName("boggled.campaign.econ.boggledTools", true, cl);
            Class<?> proj = Class.forName("boggled.terraforming.BoggledBaseTerraformingProject", true, cl);
            Class<?> req = Class.forName("boggled.terraforming.BoggledBaseTerraformingProject$TerraformingRequirementObject", true, cl);
            Class<?> ctl = Class.forName("boggled.campaign.econ.conditions.Terraforming_Controller", true, cl);
            MethodHandles.Lookup lk = MethodHandles.publicLookup();
            projects = lk.findStatic(tools, "getTerraformingProjects", MethodType.methodType(ArrayList.class, MarketAPI.class));
            isStation = lk.findStatic(tools, "marketIsStation", MethodType.methodType(boolean.class, MarketAPI.class));
            setting = lk.findStatic(tools, "getBooleanSetting", MethodType.methodType(boolean.class, String.class));
            controllerOf = lk.findStatic(tools, "getTerraformingControllerFromMarket", MethodType.methodType(ctl, MarketAPI.class));
            reqs = CARefl.method(proj, "getProjectRequirements");
            reqsMet = CARefl.method(proj, "requirementsMet", ArrayList.class);
            start = CARefl.method(proj, "startThisProject");
            daysLeft = CARefl.method(proj, "getDaysRemaining");
            projName = CARefl.method(proj, "getProjectName");
            after = CARefl.method(proj, "constructConditionsListAfterProjectCompletion");
            current = CARefl.method(ctl, "getCurrentProject");
            reqText = CARefl.field(req, "tooltipDisplayText");
            reqMet = CARefl.field(req, "requirementMet");
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Terraforming & Station Construction; its projects are left to you", t);
        }
        return linked;
    }

    static boolean setting(String key) {
        try { return Boolean.TRUE.equals(setting.invokeWithArguments(key)); } catch (Throwable t) { return false; }
    }

    static boolean planet(MarketAPI m) {
        try { return !Boolean.TRUE.equals(isStation.invokeWithArguments(m)); } catch (Throwable t) { return false; }
    }

    // ---------------- buildings ----------------

    public static boolean isTasc(IndustrySpecAPI spec) {
        return spec != null && spec.getSourceMod() != null && MOD.equals(spec.getSourceMod().getId());
    }

    /** TASC buildings never built on the probe colony (they spawn entities, remove themselves, or fail without a planet). */
    public static boolean unsafeToProbe(IndustrySpecAPI spec) { return isTasc(spec) && !PROBE_SAFE.contains(spec.getId()); }

    public static boolean has(String id) { return ESTIMATED.contains(id) && enabled(); }

    public static boolean fixedUpkeep(String id) { return FIXED.contains(id); }

    static final Map<String, Boolean> availCache = new HashMap<String, Boolean>();
    static int availDay = -1;

    /**
     * Build check for TASC buildings, cached for the day: with AotD on, each TASC check writes three log lines (the
     * whole research map), and the engine asks for every building at every colony.
     */
    public static boolean available(MarketAPI m, Industry inst) {
        int day = (int) Global.getSector().getClock().getElapsedDaysSince(0L);
        if (day != availDay) { availCache.clear(); availDay = day; }
        String key = m.getId() + "|" + inst.getId();
        Boolean b = availCache.get(key);
        if (b == null) {
            try { b = inst.isAvailableToBuild(); } catch (Throwable t) { b = false; }
            availCache.put(key, b);
        }
        return b;
    }

    static boolean artifactsMade() {
        for (MarketAPI o : Misc.getPlayerMarkets(false)) {
            try { if (o.getCommodityData("domain_artifacts").getMaxSupply() > 0) return true; } catch (Throwable ignored) { }
        }
        return false;
    }

    /** Effects of a TASC building Autopilot scores itself; false when it's worth nothing here (don't build). */
    public static boolean apply(MarketAPI m, String id, CAEngine.Pick p, Map<String, Integer> deficit) {
        CAData.Profile prof = CAData.get().colony(m.getId()).profile;
        int s = m.getSize();
        if (id.equals(PROCESSOR) || id.equals(REFLECTOR)) {
            Proj best = blockedOnlyBy(m, id, prof);
            float v = best == null ? 0f : 0.5f * best.value;   // the project itself still takes 200-400 days
            if (best != null && v > 0f) { p.fx[0] += v; p.lines.add("Lets TASC start " + best.name + " (" + CAEngine.valueText(best.value) + " once done)"); }
            if (id.equals(PROCESSOR)) {
                CAEngine.demand(p, "heavy_machinery", s, deficit);
                return v > 0f;
            }
            float own = 0f;
            if (!m.hasCondition("solar_array")) {
                own += CAConditions.value(m, "solar_array", prof);
                for (String c : new String[]{"hot", "cold", "poor_light"}) if (m.hasCondition(c)) own -= CAConditions.value(m, c, prof);
                if (m.hasIndustry("farming") || m.hasIndustry("aquaculture")) CAEngine.supply(p, "food", 2, deficit);
            }
            if (own > 0f) { p.fx[0] += own; p.lines.add("Solar array: hot, cold and poor light suppressed (" + CAEngine.valueText(own) + ")"); }
            if (!artifactsMade()) { p.fx[0] -= 4f * 4f * Math.max(1, s - 2) * 500f; p.lines.add("No Domain-era artifacts made: TASC charges 5x upkeep"); }
            return v + own > 0f;
        }
        if (id.equals("BOGGLED_DOMED_CITIES")) {
            if (m.getPlanetEntity() != null && m.getPlanetEntity().isGasGiant()) return false;   // Sky Cities: x3 cost, x6 upkeep; yours
            float v = 0f;
            for (String c : DOMED_SUPPRESSES) if (m.hasCondition(c)) v -= CAConditions.value(m, c, prof);
            boolean sea = m.hasCondition("water_surface");
            if (sea) v -= 0.25f * Math.max(0f, m.getGrossIncome() - m.getNetIncome());
            if (v <= 0f) return false;
            p.fx[0] += v; p.fx[6] += 1f;
            p.lines.add((sea ? "Seafloor cities" : "Domed cities") + ": suppresses this world's atmosphere and weather hazards (" + CAEngine.valueText(v) + "), more growth");
            if (!sea && setting("boggledDomedCitiesDefensePenaltyEnabled")) { p.fx[3] -= 2f; p.lines.add("Ground defense x0.05 under the domes"); }
            if (sea) { p.fx[3] += 2f; p.lines.add("Ground defense x6"); }
            return true;
        }
        if (id.equals("BOGGLED_GENELAB")) {
            float v = 0f;
            boolean nanoforge = false;
            for (String i : new String[]{"heavyindustry", "orbitalworks"}) { Industry x = m.getIndustry(i); if (x != null && x.getSpecialItem() != null && x.getSpecialItem().getId().contains("nanoforge")) nanoforge = true; }
            if (m.hasCondition("pollution") && !(m.hasCondition("habitable") && nanoforge)) { v -= 0.5f * CAConditions.value(m, "pollution", prof); p.lines.add("Removes pollution after 200 days without shortages"); }
            if ((m.hasCondition("water_surface") || m.hasCondition("US_hybrid")) && !m.hasCondition("volturnian_lobster_pens")) {
                v += 0.5f * CAConditions.value(m, "volturnian_lobster_pens", prof); p.lines.add("Adds lobster pens after 200 days");
            }
            if (v <= 0f) return false;
            p.fx[0] += v; p.fx[1] -= 0.5f;
            CAEngine.demand(p, "domain_artifacts", s - 2, deficit);
            if (!m.hasCondition("habitable")) CAEngine.demand(p, "organics", s - 2, deficit);
            return artifactsMade();
        }
        if (id.equals("BOGGLED_CHAMELEON")) {
            float v = 0f;
            for (String c : new String[]{"decivilized", "decivilized_subpop"}) if (m.hasCondition(c)) v -= CAConditions.value(m, c, prof);
            if (m.hasCondition("rogue_ai_core")) v += 5000f;
            if (v <= 0f || !artifactsMade()) return false;
            p.fx[0] += 0.5f * v;
            p.lines.add("Removes decivilized and rogue AI conditions after 200 days (" + CAEngine.valueText(v) + " once done)");
            CAEngine.demand(p, "domain_artifacts", s, deficit);
            return true;
        }
        if (id.equals("BOGGLED_MESOZOIC_PARK")) {
            float access = Math.max(0f, m.getAccessibilityMod().computeEffective(0f));
            float v = 1500f * s * access * Math.max(0f, m.getStabilityValue()) / 10f;
            v += CAConditions.value(m, "inimical_biosphere", prof);   // added for good when it's built
            if (v <= 0f) return false;
            p.fx[0] += v;
            p.lines.add("Park income scales with accessibility and stability; adds a permanent inimical biosphere");
            return true;
        }
        if (id.equals("BOGGLED_KLETKA_SIMULATOR")) {
            if (!artifactsMade()) return false;
            float beta = price("beta_core"), gamma = price("gamma_core");
            float v = 0.5f * (0.25f * beta + 0.25f * gamma);   // a beta and a gamma core a quarter of months each, at half price
            p.fx[0] += v;
            p.lines.add("AI cores each month: beta or gamma a quarter of the time each (" + CAEngine.valueText(v) + ")");
            CAEngine.demand(p, "domain_artifacts", 4, deficit);
            return true;
        }
        return false;
    }

    static float price(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getBasePrice(); } catch (Throwable t) { return 0f; }
    }

    // ---------------- terraforming projects ----------------

    public static class Proj {
        public String name;
        public float value, days;
        public boolean startable;
        public String blockedBy;   // the one missing TASC building that stops it, if that's all
        public List<String> lines = new ArrayList<String>();
        Object raw;
    }

    static final Map<String, List<Proj>> cache = new HashMap<String, List<Proj>>();
    static final Map<String, Integer> cacheDay = new HashMap<String, Integer>();

    /** Every project TASC offers here, valued, best first; cached for the day. Empty for stations or with terraforming off. */
    @SuppressWarnings("unchecked")
    static List<Proj> projects(MarketAPI m, CAData.Profile prof) {
        int day = (int) Global.getSector().getClock().getElapsedDaysSince(0L);
        Integer at = cacheDay.get(m.getId());
        if (at != null && at == day && cache.containsKey(m.getId())) return cache.get(m.getId());
        List<Proj> out = new ArrayList<Proj>();
        if (link() && setting("boggledTerraformingContentEnabled") && planet(m)) {
            try {
                for (Object raw : (List<Object>) projects.invokeWithArguments(m)) {
                    try {
                        Proj p = new Proj();
                        p.raw = raw;
                        p.name = String.valueOf(CARefl.invoke(projName, raw));
                        p.days = ((Number) CARefl.invoke(daysLeft, raw)).floatValue();
                        ArrayList<Object> rs = (ArrayList<Object>) CARefl.invoke(reqs, raw);
                        p.startable = Boolean.TRUE.equals(CARefl.invoke(reqsMet, raw, rs));
                        if (!p.startable) {
                            List<String> unmet = new ArrayList<String>();
                            for (Object r : rs) if (!CARefl.getBoolean(reqMet, r)) unmet.add(String.valueOf(CARefl.get(reqText, r)));
                            if (unmet.size() == 1 && unmet.get(0).endsWith("has an atmosphere processor")) p.blockedBy = PROCESSOR;
                            else if (unmet.size() == 1 && unmet.get(0).endsWith("has a stellar reflector array")) p.blockedBy = REFLECTOR;
                            else continue;   // blocked by something Autopilot doesn't build
                        }
                        p.value = CAConditions.change(m, (Collection<String>) CARefl.invoke(after, raw), prof, p.lines);
                        if (p.value > 0f) out.add(p);
                    } catch (Throwable t) {
                        CADiagnostics.LOG.warn(CADiagnostics.TAG + "a TASC project could not be read at " + m.getName(), t);
                    }
                }
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "TASC projects could not be listed at " + m.getName(), t);
            }
        }
        Collections.sort(out, new Comparator<Proj>() { public int compare(Proj a, Proj b) { return Float.compare(b.value / Math.max(1f, b.days), a.value / Math.max(1f, a.days)); } });
        cache.put(m.getId(), out); cacheDay.put(m.getId(), day);
        return out;
    }

    static Proj blockedOnlyBy(MarketAPI m, String building, CAData.Profile prof) {
        if (m.hasIndustry(building)) return null;
        Proj best = null;
        for (Proj p : projects(m, prof)) if (building.equals(p.blockedBy) && (best == null || p.value > best.value)) best = p;
        return best;
    }

    /** The project TASC is running here and its days left, or null. */
    static String running(MarketAPI m) {
        if (!link() || !m.hasCondition("terraforming_controller")) return null;
        try {
            Object ctl = controllerOf.invokeWithArguments(m);
            Object cur = ctl == null ? null : CARefl.invoke(current, ctl);
            if (cur == null) return null;
            return CARefl.invoke(projName, cur) + " (" + CARefl.invoke(daysLeft, cur) + " days left)";
        } catch (Throwable t) { return null; }
    }

    static String startProject(MarketAPI m, Proj p, String who) {
        if (running(m) != null) return "A TASC project is already running here";
        if (!CABreaker.allowed(START)) return "Paused for this session: starting TASC projects didn't take effect three times";
        try { CARefl.invoke(start, p.raw); }
        catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "starting TASC project failed at " + m.getName(), t);
            return "That failed; see starsector.log";
        }
        cache.remove(m.getId());
        if (!CABreaker.check(START, running(m) != null, m.getName())) return "TASC didn't start it";
        CALog.add(m.getName() + ": " + who + " started TASC's " + p.name + " (" + Math.round(p.days) + " days, " + CAEngine.valueText(p.value) + " once done)", true);
        return null;
    }

    /** Auto colonies: when nothing is running, starts the best project whose requirements are met. Approve colonies log it once. */
    public static void run(List<MarketAPI> markets) {
        CAData d = CAData.get();
        for (MarketAPI m : markets) {
            CAData.Colony c = d.colony(m.getId());
            if (c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) continue;
            try {
                if (running(m) != null) continue;
                Proj best = null;
                for (Proj p : projects(m, c.profile)) if (p.startable && p.value >= MIN_PROJECT) { best = p; break; }
                if (best == null) continue;
                if (c.mode == CAData.Mode.AUTO) startProject(m, best, "Auto");
                else if (CALog.once("tasc:" + m.getId() + ":" + best.name)) CALog.add(m.getName() + ": recommends TASC's " + best.name + "; start it in the colony panel", true);
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "TASC step failed at " + m.getName(), t);
            }
        }
    }

    /** A colony panel button: start <project name>. */
    public static String handle(MarketAPI m, String name) {
        for (Proj p : projects(m, CAData.get().colony(m.getId()).profile)) if (p.name.equals(name) && p.startable) return startProject(m, p, "you");
        return "That project can't be started now";
    }

    public static void renderColony(TooltipMakerAPI t, MarketAPI m, CAData.Colony c) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        if (!planet(m)) { t.addPara("Stations can't be terraformed.", gray, 6f); return; }
        String run = running(m);
        if (run != null) t.addPara("Running: %s", 6f, hl, run);
        List<Proj> list = projects(m, c.profile);
        if (list.isEmpty()) { t.addPara("No project would improve this world right now.", gray, 6f); return; }
        int shown = 0;
        for (Proj p : list) {
            if (shown++ >= 4) break;
            String why = p.startable ? "" : p.blockedBy.equals(PROCESSOR) ? ", needs an Atmosphere Processor" : ", needs a Stellar Reflector Array";
            t.addPara("%s (%s days" + why + "): %s once done", 6f, p.startable ? hl : gray, p.name, "" + Math.round(p.days), CAEngine.valueText(p.value));
            for (int j = 0; j < Math.min(3, p.lines.size()); j++) t.addPara("    " + CALog.esc(p.lines.get(j)), gray, 2f);
            if (p.startable && run == null) t.addButton("Start", CAColonyDialog.TASC + p.name, base, dark, Alignment.MID, CutStyle.ALL, 120f, 22f, 4f);
        }
        t.addPara(c.mode == CAData.Mode.AUTO ? "Auto starts the best one when nothing is running; a running project is never cancelled."
                : "Switch this colony to Auto to have projects started.", gray, 6f);
    }
}
