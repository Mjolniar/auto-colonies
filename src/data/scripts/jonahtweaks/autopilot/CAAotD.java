package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;

import java.util.*;

/**
 * Ashes of the Domain support. AotD locks buildings behind research and adds upgrade paths through Building Menu
 * Overhaul (BMO), and only BMO's build menu enforces them; market.addIndustry() doesn't. So the autopilot asks BMO the
 * same questions its menu does. Reflection only, so the mod runs without either mod. If BMO's code can't be linked,
 * fallbackAllows decides (since 0.2.38.0): other mods' buildings are open, AotD's own stay locked, and the vanilla ones
 * AotD gates open once their tech is researched; the reason is logged once. 0.3.9.1 (H C2): comment matches the code.
 */
public class CAAotD {
    static Object reqInstance, reqSet, metCriteria, reason, affected, hiddenSet;
    static boolean linked = false, broken = false;

    public static boolean enabled() { return Global.getSettings().getModManager().isModEnabled("bmo"); }

    static boolean link() {
        if (linked || broken) return linked;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> mgr = Class.forName("kaysaar.bmo.buildingmenu.additionalreq.AdditionalReqManager", true, cl);
            Class<?> req = Class.forName("kaysaar.bmo.buildingmenu.additionalreq.BaseAdditionalReq", true, cl);
            Class<?> listener = Class.forName("kaysaar.bmo.BmoListener", true, cl);
            reqInstance = CARefl.method(mgr, "getInstance");
            reqSet = CARefl.method(mgr, "getReqLinkedHashMap");
            metCriteria = CARefl.method(req, "metCriteria", MarketAPI.class, String.class);
            reason = CARefl.method(req, "getReason", MarketAPI.class, String.class);
            affected = CARefl.method(req, "getIndustriesAffected");
            hiddenSet = CARefl.method(listener, "aboutToCreateBuildingMenu", MarketAPI.class);
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Building Menu Overhaul's build rules; research-locked buildings stay locked, the rest is open", t);   // 0.3.9.1 (H C2)
        }
        return linked;
    }

    /** BMO requirements (AotD research and others) that cover this building and aren't met at this colony. */
    static List<Object> unmet(String id, MarketAPI m) throws Exception {
        List<Object> out = new ArrayList<Object>();
        for (Object r : (Collection<?>) CARefl.invoke(reqSet, CARefl.invoke(reqInstance, null))) {
            Collection<?> ids = (Collection<?>) CARefl.invoke(affected, r);
            if (ids != null && ids.contains(id) && !Boolean.TRUE.equals(CARefl.invoke(metCriteria, r, m, id))) out.add(r);
        }
        return out;
    }

    /** 0.3.9.1 (H L1): hidden() when BMO's list couldn't be read: allows() then lets nothing through (fails closed). */
    static final Set<String> UNREADABLE = Collections.unmodifiableSet(new HashSet<String>());

    /** Buildings BMO hides from this colony's build menu right now (AotD hides some until researched). */
    @SuppressWarnings("unchecked")
    public static Set<String> hidden(final MarketAPI m) {
        if (!enabled() || !link()) return Collections.emptySet();
        Set<String> k = CAMemo.kept("bmo-hidden", m, () -> hiddenNow(m));   // 0.3.20.1: every BMO listener runs on each read
        return k == UNREADABLE ? k : new HashSet<String>(k);
    }

    @SuppressWarnings("unchecked")
    static Set<String> hiddenNow(MarketAPI m) {
        try {
            Object s = CARefl.invoke(hiddenSet, null, m);
            return s == null ? Collections.<String>emptySet() : new HashSet<String>((Collection<String>) s);
        } catch (Throwable t) {
            CAStep.warn("BMO rules", "Building Menu Overhaul's hidden-building check failed at " + m.getName(), t);   // 0.3.0.0 (07 M8): one trace, then a short line weekly
            return UNREADABLE;   // 0.3.9.1 (H L1)
        }
    }

    /** True when the build menu would offer this building here: not hidden and every requirement met. */
    public static boolean allows(String id, MarketAPI m, Set<String> hidden) {
        if (!enabled()) return true;
        if (!link()) return fallbackAllows(id);
        if (hidden == UNREADABLE || hidden.contains(id)) return false;   // 0.3.9.1 (H L1): an unreadable hidden list allows nothing
        try { return unmet(id, m).isEmpty(); }
        catch (Throwable t) {
            CAStep.warn("BMO rules", "Building Menu Overhaul's requirement check failed for " + id + " at " + m.getName(), t);   // 0.3.0.0 (07 M8): one trace, then a short line weekly
            return false;
        }
    }

    public static boolean allows(String id, MarketAPI m) { return allows(id, m, hidden(m)); }

    /**
     * Building Menu Overhaul is on but its code can't be read (an update renamed something): instead of building nothing at
     * all (before 0.2.38.0), what research would lock stays locked and the rest is open. Without Vaults of Knowledge, BMO
     * locks nothing by research. With it, Ashes of the Domain's own buildings stay locked, and the vanilla ones it gates
     * (CAResearch.GATES) only open once their tech is researched; another mod's building is open.
     */
    static boolean fallbackAllows(String id) {
        if (!Global.getSettings().getModManager().isModEnabled("aotd_vok")) return true;
        if (id.startsWith("aotd_")) return false;
        CAResearch.link();
        for (String[] g : CAResearch.GATES) if (g[1].equals(id)) return CAResearch.forPlayer(g[0]);
        return true;
    }

    /** Why the build menu locks this building here (for AotD, the research it needs), or null when it's open. */
    public static String lockReason(String id, MarketAPI m) {
        if (!enabled()) return null;
        if (!link()) return "Building Menu Overhaul's rules can't be read";
        try {
            List<String> why = new ArrayList<String>();
            for (Object r : unmet(id, m)) {
                Object text = CARefl.invoke(reason, r, m, id);
                if (text != null && !String.valueOf(text).trim().isEmpty()) why.add(String.valueOf(text).trim());
            }
            if (!why.isEmpty()) return String.join("; ", why);   // 0.3.9.1 (H C3)
            if (hidden(m).contains(id)) return "hidden in the build menu here";
        } catch (Throwable t) {
            CAStep.warn("BMO rules", "Building Menu Overhaul's requirement text failed for " + id, t);   // 0.3.0.0 (07 M8): one trace, then a short line weekly
        }
        return null;
    }

    /** Buildings that upgrade from this one in the build menu: every spec whose downgrade is this building. 0.3.9.1 (H P2): a map built once per load (CAPlan.upgradesFrom). */
    public static List<String> children(String id) { return CAPlan.upgradesFrom(id); }

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
