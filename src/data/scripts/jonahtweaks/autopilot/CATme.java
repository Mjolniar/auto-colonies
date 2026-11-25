package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.3.0.5: Terraforming Made Easy reached by name through CARefl, so Auto Colonies needs no TME class at compile time
 * and runs with TME 2.9.8 (Starsector 0.98a) or 2.10.0 (0.98.5a).
 *
 * | | 2.9.8 | 2.10.0 |
 * | terraforming structure | industries.BaseTerraformingIndustry | industries.TerraformingIndustry |
 * | structures with projects | industries.BaseDevelopmentIndustry | TerraformingIndustry, MegastructureIndustry |
 * | project | Utils$ProjectData (fields id, name, cost, buildTime) | loading.specs.TerraformingProjectSpec (getConditionId...) |
 * | can it run | ui.plugins.ProjectListPlugin.canBuild(industry, project) | Utils.canPerformProject(market, project) for an add; a removal always (0.3.9.2) |
 * | project table | data/campaign/tme_terraforming_options.csv | data/campaign/terraformingmadeeasy/terraforming_projects.csv |
 *
 * getProjects, getProject, setProject and hasOngoingProject keep their names in both. With neither version every
 * answer is "no TME structure here".
 */
public final class CATme {
    private CATme() { }

    /** One project: the condition it adds or removes (id), its name, base cost and days. raw is TME's own object. */
    public static final class Proj {
        public String id = "", name = "";
        public float cost, buildTime;
        Object raw;
    }

    private static boolean looked, v210;
    private static Class<?> terra, mega, dev, projCls;
    private static Object canRun, setProjectM, gCond, gName, gCost, gTime, fId, fName, fCost, fTime, buildMult, removeMult;   // 0.3.8.5: isUnlocked() not read
    /** 0.3.0.9: TME's Settings.BUILD_TIME_MULTIPLIER (2.9.8 and 2.10.0), read by name as the cost multipliers are. */
    private static Object timeMult;

    private static synchronized void look() {
        if (looked) return;
        looked = true;
        ClassLoader cl = CATme.class.getClassLoader();
        try {
            terra = Class.forName("terraformingmadeeasy.industries.TerraformingIndustry", false, cl);
            mega = Class.forName("terraformingmadeeasy.industries.MegastructureIndustry", false, cl);
            projCls = Class.forName("terraformingmadeeasy.loading.specs.TerraformingProjectSpec", false, cl);
            canRun = CARefl.method(Class.forName("terraformingmadeeasy.Utils", false, cl), "canPerformProject", MarketAPI.class, projCls);
            gCond = CARefl.method(projCls, "getConditionId");
            gName = CARefl.method(projCls, "getName", MarketAPI.class);
            gCost = CARefl.method(projCls, "getCost");
            gTime = CARefl.method(projCls, "getBuildTime");
            setProjectM = CARefl.method(terra, "setProject", projCls);
            v210 = true;
        } catch (Throwable t) {
            v210 = false;
            terra = mega = projCls = null;
        }
        if (!v210) {
            try {
                terra = Class.forName("terraformingmadeeasy.industries.BaseTerraformingIndustry", false, cl);
                dev = Class.forName("terraformingmadeeasy.industries.BaseDevelopmentIndustry", false, cl);
                projCls = Class.forName("terraformingmadeeasy.Utils$ProjectData", false, cl);
                canRun = CARefl.method(Class.forName("terraformingmadeeasy.ui.plugins.ProjectListPlugin", false, cl), "canBuild", dev, projCls);
                fId = CARefl.field(projCls, "id");
                fName = CARefl.field(projCls, "name");
                fCost = CARefl.field(projCls, "cost");
                fTime = CARefl.field(projCls, "buildTime");
                setProjectM = CARefl.method(dev, "setProject", projCls);
            } catch (Throwable t) {
                terra = dev = projCls = null;
            }
        }
        try {
            Class<?> st = Class.forName("terraformingmadeeasy.Settings", false, cl);
            buildMult = CARefl.field(st, "BUILD_COST_MULTIPLIER");
            removeMult = CARefl.field(st, "REMOVAL_COST_MULTIPLIER");
        } catch (Throwable t) {
            buildMult = removeMult = null;
        }
        try { timeMult = CARefl.field(Class.forName("terraformingmadeeasy.Settings", false, cl), "BUILD_TIME_MULTIPLIER"); }
        catch (Throwable t) { timeMult = null; }
    }

    /** TME 2.10.0 or newer is loaded. */
    public static boolean v210() {
        look();
        return v210;
    }

    /** A TME structure that runs terraforming projects. */
    public static boolean isTerraforming(Industry ind) {
        look();
        return ind != null && terra != null && terra.isInstance(ind);
    }

    /** Any TME structure with projects: terraforming or megastructure. */
    public static boolean isDevelopment(Industry ind) {
        look();
        return isTerraforming(ind) || (ind != null && ((dev != null && dev.isInstance(ind)) || (mega != null && mega.isInstance(ind))));
    }

    /** The structure is running a project. */
    public static boolean ongoing(Industry ind) {
        try { return Boolean.TRUE.equals(CARefl.call(ind, "hasOngoingProject")); } catch (Throwable t) { return false; }
    }

    /** The project the structure runs, or null (none, or unreadable). */
    public static Proj project(Industry ind) {
        try {
            Object o = CARefl.call(ind, "getProject");
            return o == null ? null : wrap(o, ind.getMarket());
        } catch (Throwable t) {
            return null;
        }
    }

    /** The structure's projects; throws when TME's list can't be read, so callers can quarantine the structure. */
    public static List<Proj> projects(Industry ind) {
        List<Proj> out = new ArrayList<Proj>();
        try {
            Object l = CARefl.call(ind, "getProjects");
            if (l instanceof List) for (Object o : (List<?>) l) if (o != null) out.add(wrap(o, ind.getMarket()));
        } catch (Exception e) {
            throw new IllegalStateException("Terraforming Made Easy's project list", e);
        }
        return out;
    }

    static Proj wrap(Object o, MarketAPI m) throws Exception {
        Proj p = new Proj();
        p.raw = o;
        if (projCls == null || !projCls.isInstance(o)) {   // a megastructure project: only its id matters here
            Object id = CARefl.call(o, "getId");
            p.id = p.name = id == null ? "" : id.toString();
            return p;
        }
        if (v210) {
            Object c = CARefl.invoke(gCond, o), n = CARefl.invoke(gName, o, m);
            p.id = c == null ? "" : c.toString();
            p.name = n == null ? p.id : n.toString();
            p.cost = ((Number) CARefl.invoke(gCost, o)).floatValue();
            p.buildTime = buildDays(((Number) CARefl.invoke(gTime, o)).floatValue(), buildTimeMult());
        } else {
            Object c = CARefl.get(fId, o), n = CARefl.get(fName, o);
            p.id = c == null ? "" : c.toString();
            p.name = n == null ? p.id : n.toString();
            p.cost = CARefl.getFloat(fCost, o);
            p.buildTime = buildDays(CARefl.getFloat(fTime, o), buildTimeMult());
        }
        return p;
    }

    /**
     * Pure (0.3.0.9, JTTerraSimCheck): the days a project takes, as TME's structure counts them (TerraformingIndustry and
     * BaseDevelopmentIndustry: the project's buildTime x Settings.BUILD_TIME_MULTIPLIER). A multiplier of 0 or less, or NaN,
     * counts as 1. Proj.buildTime holds this.
     */
    static float buildDays(float raw, float mult) { return raw * (mult > 0f ? mult : 1f); }

    /** 0.3.0.9: TME's build time multiplier (its settings: fast 0.5, normal 1, slow 2, or custom), 1 when unreadable. */
    public static float buildTimeMult() {
        look();
        try { return timeMult == null ? 1f : CARefl.getFloat(timeMult, null); } catch (Throwable t) { return 1f; }
    }

    /**
     * TME's own check that the structure can run this project now (its requirements). 0.3.8.5: on 2.10.0 the project table's
     * `unlocked` column is not read (TME never calls isUnlocked(); a blank cell is false), as in TME's own dialog. 0.3.9.2
     * (I L1, C5): on 2.10.0 a removal has no requirements (TerraformingOptionsPlugin: conditionForRemoval or
     * canPerformProject), so a present condition can always be removed.
     */
    public static boolean canRun(Industry ind, Proj p) {
        look();
        if (canRun == null || p == null || p.raw == null) { lastError = canRun == null ? "TME's check wasn't found" : "no project"; return false; }
        try {
            if (v210) {
                if (projCls != null && projCls.isInstance(p.raw) && ind.getMarket().hasCondition(p.id)) return true;   // 0.3.9.2 (I L1)
                return Boolean.TRUE.equals(CARefl.invoke(canRun, null, ind.getMarket(), p.raw));
            }
            return Boolean.TRUE.equals(CARefl.invoke(canRun, null, ind, p.raw));
        } catch (Throwable t) {
            lastError = String.valueOf(t.getCause() != null ? t.getCause() : t);   // 0.3.17.8
            return false;
        }
    }

    /** 0.3.17.8: the last error TME's check threw (for CATerraform.worldNote), or null. */
    static String lastError;

    /**
     * 0.3.17.8: the required conditions and industries of a project (TME 2.10: likedConditions, likedIndustries) the market
     * doesn't have, as "Irradiated, Heavy Industry"; "" when it has them all; null when they can't be read.
     */
    static String missing(MarketAPI m, Proj p) {
        look();
        if (!v210 || p == null || p.raw == null || m == null) return null;
        try {
            List<String> out = new ArrayList<String>();
            for (String[] g : groups(String.valueOf(CARefl.call(p.raw, "getLikedConditions")))) {
                boolean any = false, all = true;
                for (String id : g[1].split("\\|")) { boolean has = m.hasCondition(id); any |= has; all &= has; if (!has && g[0].equals("all")) out.add(condName(id)); }
                if (g[0].equals("one") && !any) out.add(String.join(" or ", names(g[1], true)));
            }
            for (String[] g : groups(String.valueOf(CARefl.call(p.raw, "getLikedIndustries")))) {
                boolean any = false;
                for (String id : g[1].split("\\|")) { boolean has = m.hasIndustry(id); any |= has; if (!has && g[0].equals("all")) out.add(indName(id)); }
                if (g[0].equals("one") && !any) out.add(String.join(" or ", names(g[1], false)));
            }
            return String.join(", ", out);
        } catch (Throwable t) { return null; }
    }

    /** Pure (0.3.17.8): TME's "needAll:a|b,needOne:c|d" as {"all", "a|b"}, {"one", "c|d"}. A blank or "null" text has none. */
    static List<String[]> groups(String text) {
        List<String[]> out = new ArrayList<String[]>();
        if (text == null || text.trim().isEmpty() || text.equals("null")) return out;
        for (String e : text.split(",")) {
            String s = e.trim();
            if (s.startsWith("needAll:")) out.add(new String[]{"all", s.substring(8)});
            else if (s.startsWith("needOne:")) out.add(new String[]{"one", s.substring(8)});
            else if (!s.isEmpty()) out.add(new String[]{"all", s});
        }
        return out;
    }

    static List<String> names(String ids, boolean cond) {
        List<String> l = new ArrayList<String>();
        for (String id : ids.split("\\|")) l.add(cond ? condName(id) : indName(id));
        return l;
    }

    static String condName(String id) {
        try { return Global.getSettings().getMarketConditionSpec(id).getName(); } catch (Throwable t) { return id; }
    }

    static String indName(String id) {
        try { return Global.getSettings().getIndustrySpec(id).getName(); } catch (Throwable t) { return id; }
    }

    /** Sets the project TME starts on the structure's next startUpgrading(), as its dialog does. */
    public static void setProject(Industry ind, Proj p) {
        look();
        if (setProjectM == null) throw new IllegalStateException("Terraforming Made Easy's setProject not found");
        try { CARefl.invoke(setProjectM, ind, p.raw); }
        catch (Exception e) { throw new IllegalStateException("Terraforming Made Easy's setProject", e); }
    }

    /** TME's build cost multiplier (its settings), 1 when unreadable. */
    public static float buildCostMult() {
        look();
        try { return buildMult == null ? 1f : CARefl.getFloat(buildMult, null); } catch (Throwable t) { return 1f; }
    }

    /** TME's multiplier for removing a condition, 1 when unreadable. */
    public static float removalCostMult() {
        look();
        try { return removeMult == null ? 1f : CARefl.getFloat(removeMult, null); } catch (Throwable t) { return 1f; }
    }

    /** The project table: {path, id column, gas giant column}. */
    static String[] table() {
        return v210() ? new String[]{"data/campaign/terraformingmadeeasy/terraforming_projects.csv", "id", "changeGasGiants"}
                : new String[]{"data/campaign/tme_terraforming_options.csv", "conditionId", "canChangeGasGiants"};
    }
}
