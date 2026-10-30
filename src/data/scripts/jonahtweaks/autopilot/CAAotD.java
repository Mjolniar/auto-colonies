package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Ashes of the Domain support. AotD locks buildings behind research and adds upgrade paths through Building Menu
 * Overhaul (BMO), and only BMO's build menu enforces them; market.addIndustry() doesn't. So the autopilot asks BMO the
 * same questions its menu does. Reflection only, so the mod runs without either mod. If BMO can't be asked, nothing
 * is built or upgraded while it's enabled (fails closed) and the reason is logged once.
 */
public class CAAotD {
    static Method reqInstance, reqSet, metCriteria, reason, affected, hiddenSet;
    static boolean linked = false, broken = false;

    public static boolean enabled() { return Global.getSettings().getModManager().isModEnabled("bmo"); }

    static boolean link() {
        if (linked || broken) return linked;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> mgr = Class.forName("kaysaar.bmo.buildingmenu.additionalreq.AdditionalReqManager", true, cl);
            Class<?> req = Class.forName("kaysaar.bmo.buildingmenu.additionalreq.BaseAdditionalReq", true, cl);
            Class<?> listener = Class.forName("kaysaar.bmo.BmoListener", true, cl);
            reqInstance = mgr.getMethod("getInstance");
            reqSet = mgr.getMethod("getReqLinkedHashMap");
            metCriteria = req.getMethod("metCriteria", MarketAPI.class, String.class);
            reason = req.getMethod("getReason", MarketAPI.class, String.class);
            affected = req.getMethod("getIndustriesAffected");
            hiddenSet = listener.getMethod("aboutToCreateBuildingMenu", MarketAPI.class);
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Building Menu Overhaul's build rules; nothing is built or upgraded while it's enabled", t);
        }
        return linked;
    }

    /** BMO requirements (AotD research and others) that cover this building and aren't met at this colony. */
    static List<Object> unmet(String id, MarketAPI m) throws Exception {
        List<Object> out = new ArrayList<Object>();
        for (Object r : (Collection<?>) reqSet.invoke(reqInstance.invoke(null))) {
            Collection<?> ids = (Collection<?>) affected.invoke(r);
            if (ids != null && ids.contains(id) && !Boolean.TRUE.equals(metCriteria.invoke(r, m, id))) out.add(r);
        }
        return out;
    }

    /** Buildings BMO hides from this colony's build menu right now (AotD hides some until researched). */
    @SuppressWarnings("unchecked")
    public static Set<String> hidden(MarketAPI m) {
        if (!enabled() || !link()) return Collections.emptySet();
        try {
            Object s = hiddenSet.invoke(null, m);
            return s == null ? Collections.<String>emptySet() : new HashSet<String>((Collection<String>) s);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "Building Menu Overhaul's hidden-building check failed at " + m.getName(), t);
            return Collections.emptySet();
        }
    }

    /** True when the build menu would offer this building here: not hidden and every requirement met. */
    public static boolean allows(String id, MarketAPI m, Set<String> hidden) {
        if (!enabled()) return true;
        if (!link()) return false;
        if (hidden.contains(id)) return false;
        try { return unmet(id, m).isEmpty(); }
        catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "Building Menu Overhaul's requirement check failed for " + id + " at " + m.getName(), t);
            return false;
        }
    }

    public static boolean allows(String id, MarketAPI m) { return allows(id, m, hidden(m)); }

    /** Why the build menu locks this building here (for AotD, the research it needs), or null when it's open. */
    public static String lockReason(String id, MarketAPI m) {
        if (!enabled()) return null;
        if (!link()) return "Building Menu Overhaul's rules can't be read";
        try {
            List<String> why = new ArrayList<String>();
            for (Object r : unmet(id, m)) {
                Object text = reason.invoke(r, m, id);
                if (text != null && !String.valueOf(text).trim().isEmpty()) why.add(String.valueOf(text).trim());
            }
            if (!why.isEmpty()) return join(why);
            if (hidden(m).contains(id)) return "hidden in the build menu here";
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "Building Menu Overhaul's requirement text failed for " + id, t);
        }
        return null;
    }

    static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) { if (sb.length() > 0) sb.append("; "); sb.append(p); }
        return sb.toString();
    }

    /** Buildings that upgrade from this one in the build menu: every spec whose downgrade is this building. */
    public static List<String> children(String id) {
        List<String> out = new ArrayList<String>();
        for (IndustrySpecAPI s : Global.getSettings().getAllIndustrySpecs()) if (id.equals(s.getDowngrade())) out.add(s.getId());
        return out;
    }

    /** True when this building is reached by upgrading another one (AotD sets these links on vanilla buildings too). */
    public static boolean upgradeOnly(IndustrySpecAPI spec) { return enabled() && spec.getDowngrade() != null; }

    /**
     * Starts an upgrade to a child building the way BMO's upgrade menu does: the spec points at the target only while
     * the upgrade starts (the game stores the target on the building), then goes back to what it was.
     */
    public static void startUpgrade(Industry ind, String target) {
        IndustrySpecAPI spec = ind.getSpec();
        String old = spec.getUpgrade();
        try {
            spec.setUpgrade(target);
            ind.startUpgrading();
        } finally {
            spec.setUpgrade(old);
        }
    }
}
