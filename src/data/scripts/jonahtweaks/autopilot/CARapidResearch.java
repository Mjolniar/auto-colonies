package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableStat;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.3.9.9: the Rapid research package (story mode, Tier IV, 20 bandwidth, needs Ashes of the Domain: Vaults of Knowledge).
 * VoK's research takes timeToResearch x its speed setting / (1 + the player manager's researchSpeedBonus)
 * (AoDUtilis.getDaysFromResearch, VoK 5.0.7). The package adds KEY to that MutableStat: +1/2 flat, then x2, so
 * (1 + bonus) becomes 2 x (1 + bonus) and every project takes half its time (0.3.10.2; 2/3 before). The items a project needs
 * (payForResearch: databanks, cores, colony items) are left as they are.
 * - Never in a save: remove() runs before every save (JTModPlugin.beforeGameSave) and sync() after it and on load, so a
 *   save without Auto Colonies has no trace of it. Prepare for removal takes it off too (CARemoval.prepare).
 * - sync(): on while the package's ability runs (CASync.has: story mode on, not dormant, the package bought), off otherwise;
 *   daily (CASync.daily) and on load.
 */
public final class CARapidResearch {
    private CARapidResearch() { }

    public static final String ABILITY = "rapidResearch", KEY = "ac_rapid_research";
    /** Research takes this share of its time with the package. */
    static final float TIME = 1f / 2f;
    static final float FLAT = 1f / 2f, MULT = 2f;   // 0.3.10.2: half the time (was 2/3: +1/3, x1.5)

    /** 0.3.10.2: the share of a project's databanks it needs with the package. */
    static final float DATABANK_SHARE = 2f / 3f;
    static final String CSV = "data/campaign/aotd_tech_options.csv", MOD = "aotd_vok";

    /** Pure (0.3.10.2): a project's databanks with the package: 2/3, rounded, at least 1 where it needs any. */
    static int databanks(int n) { return n <= 0 ? n : Math.max(1, Math.round(n * DATABANK_SHARE)); }

    /** Pure (0.3.10.2): the databank count in one itemReqToResearch cell ("id:count,id:count"), or -1 when it has none. */
    static int databanksIn(String cell) {
        if (cell == null) return -1;
        for (String part : cell.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length == 2 && CAResearch.DATABANK.equals(kv[0].trim())) {
                try { return Integer.parseInt(kv[1].trim()); } catch (NumberFormatException e) { return -1; }
            }
        }
        return -1;
    }

    /** 0.3.10.2: each project's databanks as VoK's CSV has them (every load starts from these); empty when it can't be read. */
    static Map<String, Integer> pristine() {
        Map<String, Integer> out = new HashMap<String, Integer>();
        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetDataForMod("id", CSV, MOD);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.getJSONObject(i);
                int n = databanksIn(r.optString("itemReqToResearch", null));
                String id = r.optString("id", null);
                if (id != null && n > 0) out.put(id, n);
            }
        } catch (Throwable t) { out.clear(); }
        return out;
    }

    /**
     * 0.3.10.2: every player project's databanks, the package's 2/3 (on) or the CSV's count (off), in the project's map and in
     * its spec's: VoK's research council (Sophia) sets the project's map from the spec's every half second.
     */
    @SuppressWarnings("unchecked")
    static void databanks(boolean on) {
        if (!CASyncCatalog.modOn(MOD)) return;
        try {
            Object mgr = CAResearch.mgr();
            if (mgr == null) return;
            Map<String, Integer> base = pristine();
            if (base.isEmpty()) return;
            for (Object o : (List<Object>) CARefl.invoke(CAResearch.repo, mgr)) {
                Integer n = base.get(String.valueOf(CARefl.get(CAResearch.fId, o)));
                if (n == null) continue;
                Integer v = on ? Integer.valueOf(databanks(n)) : n;
                put((Map<String, Integer>) CARefl.get(CAResearch.fItems, o), v);
                Object spec = CARefl.call(o, "getSpec");
                if (spec != null) put((Map<String, Integer>) CARefl.call(spec, "getReqItemsToResearchFirst"), v);
            }
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "rapid research databanks failed", t); }
    }

    static void put(Map<String, Integer> m, Integer v) { if (m != null && m.containsKey(CAResearch.DATABANK)) m.put(CAResearch.DATABANK, v); }

    /** Pure: the speed bonus with the package, from the bonus without it. */
    static float withPackage(float bonus) { return (bonus + FLAT) * MULT; }

    /** Pure: the share of its time a project takes, from the bonus without and with the package (VoK's days formula). */
    static float timeShare(float without, float with) { return (1f + without) / (1f + with); }

    /** VoK's player research speed stat, or null without VoK or when it can't be read. */
    static MutableStat stat() {
        if (!CASyncCatalog.modOn("aotd_vok") || !CAResearch.link()) return null;
        try {
            Object mgr = CARefl.invoke(CAResearch.player, CARefl.invoke(CAResearch.instance, null));
            Object st = mgr == null ? null : CARefl.call(mgr, "getResearchSpeedBonus");
            return st instanceof MutableStat ? (MutableStat) st : null;
        } catch (Throwable t) { return null; }
    }

    /** On while the package runs, off otherwise. */
    public static void sync() {
        boolean on;
        try { on = !CARemoval.off && CASync.has(ABILITY); } catch (Throwable t) { on = false; }
        if (on) apply(); else remove();
    }

    static void apply() { apply(stat()); databanks(true); }

    /** Puts the package's change on st (JTRapidResearchCheck runs it on a plain MutableStat). */
    static void apply(MutableStat st) {
        if (st == null) return;
        st.modifyFlat(KEY, FLAT, "Rapid research");
        st.modifyMult(KEY, MULT, "Rapid research");
    }

    /** Takes the package's change off VoK's stat (before a save, on Prepare for removal, when the package is gone). */
    public static void remove() { remove(stat()); databanks(false); }

    static void remove(MutableStat st) {
        if (st == null) return;
        st.unmodifyFlat(KEY);
        st.unmodifyMult(KEY);
    }
}
