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
            if (CATasc.unsafeToProbe(spec)) {   // spawns entities, removes itself, or fails without a planet: never test-built
                industries.put(spec.getId(), Level.ANALYZED);
                addIssue("building", spec.getId(), spec.getName(), mod, Level.ANALYZED, "TASC building whose effects need its planet or time; left to you");
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
                    "Unmapped condition; read only through the colony's live stats");
        }
        for (SpecialItemSpecAPI spec : Global.getSettings().getAllSpecialItemSpecs()) {
            ModSpecAPI src = spec.getSourceMod();
            items.put(spec.getId(), src == null ? Level.SUPPORTED : Level.ANALYZED);
        }
        lastScan = Global.getSector() != null && Global.getSector().getClock() != null
                ? Global.getSector().getClock().getDateString() : new Date().toString();
        LOG.info(TAG + "scan done: " + industries.size() + " buildings, " + conditions.size() + " conditions, "
                + items.size() + " items; " + count(Level.MEASURED) + " measured, " + count(Level.ANALYZED) + " analyzed, " + count(Level.QUARANTINED) + " quarantined");
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
              .append((int) spec.getBuildTime()).append(" days, upkeep x").append(spec.getUpkeep())
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

    /** Runtime guard: any error from game or mod code during planning quarantines that building. */
    public static void quarantine(String industryId, String where, Throwable t) {
        industries.put(industryId, Level.QUARANTINED);
        IndustrySpecAPI spec = null;
        try { spec = Global.getSettings().getIndustrySpec(industryId); } catch (Throwable ignored) { }
        String name = spec == null ? industryId : spec.getName();
        String mod = spec == null ? "unknown" : modOf(spec.getSourceMod());
        LOG.warn(TAG + "runtime error in " + where + " for " + industryId, t);
        addIssue("building", industryId, name, mod, Level.QUARANTINED, "ERROR in " + where + ": " + t);
        writeReport();
    }

    public static List<String> unsupportedConditions(MarketAPI market) {
        List<String> out = new ArrayList<String>();
        for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : market.getConditions()) {
            if (conditionLevel(c.getId()) != Level.SUPPORTED) out.add(c.getName());
        }
        return out;
    }

    public static void writeReport() {
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
            Global.getSettings().writeTextFileToCommon(REPORT, root.toString(2));
        } catch (Throwable t) {
            LOG.warn(TAG + "could not write diagnostics report", t);
        }
    }
}
