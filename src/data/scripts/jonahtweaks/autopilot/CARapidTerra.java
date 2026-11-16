package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.util.Misc;

/**
 * 0.3.7.9: Rapid terraforming (story mode, Tier IV): a terraforming project at a managed colony (Approve or Auto), yours or
 * the Autopilot's, takes half the time. Once per project the days left are halved:
 * - Terraforming Made Easy: its terraforming structure counts BaseIndustry's buildProgress up to buildTime (startUpgrading
 *   sets both); an AI core's cut is taken from the days left and undone on its own, so the two stack.
 * - Terraforming and Station Construction: the running project counts daysCompleted up to requiredDaysToCompleteProject
 *   (CATasc.halve).
 * The project halved is kept in the colony's memory (per structure: TME's condition and build time, 0.3.9.2; TASC's
 * project name), so a load or a second look leaves it alone; a
 * project's conditions change when it finishes, so the same project does not run twice in a row.
 */
public final class CARapidTerra {
    private CARapidTerra() { }

    static final String KEY = "$ca_rapidTerra:";
    /** The share of the days left a project keeps. */
    static final float SHARE = 0.5f;

    /** Daily, from CASync.daily while story mode is on. */
    public static void daily() {
        if (!CASync.has("rapidTerra")) return;
        boolean tme = Global.getSettings().getModManager().isModEnabled("terraformingmadeeasy");
        boolean tasc = Global.getSettings().getModManager().isModEnabled(CATasc.MOD);
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!CASync.manages(m)) continue;
            if (tme) {
                try { tme(m); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "rapid terraforming failed at " + m.getName(), t); }
            }
            if (tasc) {
                try { tasc(m); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "rapid terraforming (TASC) failed at " + m.getName(), t); }
            }
        }
    }

    static void tme(MarketAPI m) throws Exception {
        MemoryAPI mem = m.getMemoryWithoutUpdate();
        for (Industry ind : m.getIndustries()) {
            if (!CATme.isTerraforming(ind)) continue;
            String key = KEY + ind.getId();
            CATme.Proj p = CATme.ongoing(ind) && ind.isBuilding() ? CATme.project(ind) : null;
            if (p == null) { mem.unset(key); continue; }
            // 0.3.9.2 (I L2): the condition and BaseIndustry's buildTime, which stay the same while it runs (TME 2.10.0's
            // name turns from "Add X" to "Remove X" when another project adds X first)
            String sig = p.id + "|" + buildTime(ind), was = mem.getString(key);
            if (sig.equals(was)) continue;
            mem.set(key, sig);
            if (oldSig(was, p.id)) continue;   // 0.3.9.2: halved under the key form before 0.3.9.2 (condition|name)
            int days = halve(ind);
            if (days >= 0) CALog.did(m.getName() + ": Rapid terraforming cut " + p.name + " to " + days + " days.");
        }
    }

    static void tasc(MarketAPI m) throws Exception {
        MemoryAPI mem = m.getMemoryWithoutUpdate();
        String key = KEY + "tasc";
        Object p = CATasc.currentProject(m);
        if (p == null) { mem.unset(key); return; }
        String name = CATasc.projectName(p);
        if (name.equals(mem.getString(key))) return;
        mem.set(key, name);
        int days = CATasc.halve(p, SHARE);
        if (days >= 0) CALog.did(m.getName() + ": Rapid terraforming cut " + name + " to " + days + " days.");
    }

    /** Pure (0.3.9.2, I L2): a key saved before 0.3.9.2 for this condition ("condition|name", the name not a number). */
    static boolean oldSig(String was, String condition) {
        if (was == null || !was.startsWith(condition + "|")) return false;
        try { Float.parseFloat(was.substring(condition.length() + 1)); return false; } catch (NumberFormatException e) { return true; }
    }

    /** 0.3.9.2 (I L2): BaseIndustry's buildTime (set when the project starts), or -1 when it can't be read. */
    static float buildTime(Industry ind) {
        try {
            Object time = ind instanceof BaseIndustry ? CARefl.declared(BaseIndustry.class, "buildTime") : null;
            return time == null ? -1f : CARefl.getFloat(time, ind);
        } catch (Throwable t) { return -1f; }
    }

    /**
     * TME: the days left halved through BaseIndustry's own fields. The days left after, or -1 when they can't be read or
     * nothing was cut (0.3.9.2, I L8).
     */
    static int halve(Industry ind) throws Exception {
        if (!(ind instanceof BaseIndustry)) return -1;
        Object time = CARefl.declared(BaseIndustry.class, "buildTime"), progress = CARefl.declared(BaseIndustry.class, "buildProgress");
        if (time == null || progress == null) return -1;
        float total = CARefl.getFloat(time, ind), done = CARefl.getFloat(progress, ind);
        if (total <= 0f) return -1;
        float next = progressAfter(total, done, SHARE);
        if (next <= done) return -1;   // 0.3.9.2 (I L8)
        CARefl.set(progress, ind, next);
        return (int) Math.ceil(total - next);
    }

    /** Pure: the progress that leaves `share` of the days left; never less than now. */
    static float progressAfter(float total, float done, float share) {
        return Math.max(done, total - Math.max(0f, total - done) * share);
    }

    /** Pure: whole days left after keeping `share` of `left`; at least 1 while any are left. */
    static int daysAfter(int left, float share) {
        if (left <= 1) return Math.max(0, left);
        return Math.max(1, Math.round(left * share));
    }
}
