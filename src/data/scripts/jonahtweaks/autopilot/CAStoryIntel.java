package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 0.3.6.0 story mode: the Autopilot's storyline in the Intel screen's Story tab (the game's own tab for its main story): the
 * core's state, its processing power and what to do next. It appears once the TriPad has said something.
 */
public class CAStoryIntel extends BaseIntelPlugin {

    public static void ensure() {
        try {
            if (!CASync.seen(CATriPad.FOUND) && CASync.generation() == 0 && !CASync.installed() && CASync.phase().isEmpty()
                    && !CASync.seen(CATriPad.CHIME + ":0")) return;
            com.fs.starfarer.api.campaign.comm.IntelManagerAPI mgr = Global.getSector().getIntelManager();
            if (!mgr.hasIntelOfClass(CAStoryIntel.class)) mgr.addIntel(new CAStoryIntel(), false);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story intel failed", t); }
    }

    static String state() {
        if (CASync.installed() && !CASync.pulled()) return "online, " + CASync.total() + " PP";
        if (CASync.pulled()) return "dormant, core pulled";
        String ph = CASync.phase();
        if ("breaking".equals(ph)) return "core being broken down";
        if ("sold".equals(ph) || "broken".equals(ph) || "salvage".equals(ph)) return "no core";
        return "dormant";
    }

    /** The state as a sentence, for the description. */
    static String status() {
        if (CASync.installed() && !CASync.pulled()) return "The Colony Autopilot is online.";
        if (CASync.pulled()) return "The Colony Autopilot is dormant. Its core is out of the cradle.";
        String ph = CASync.phase();
        if ("breaking".equals(ph)) return "Engineering is breaking the Autarch AI Core down.";
        if ("sold".equals(ph) || "broken".equals(ph) || "salvage".equals(ph)) return "You have no Autarch AI Core.";
        return "The Colony Autopilot is dormant.";
    }

    @Override protected String getName() { return "Colony Autopilot: " + state(); }

    @Override public String getIcon() { return "graphics/icons/cargo/autarch_core.png"; }

    @Override public boolean hasSmallDescription() { return true; }
    @Override public boolean shouldRemoveIntel() { return false; }
    @Override public String getSortString() { return "Colony Autopilot"; }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>();
        tags.add(Tags.INTEL_STORY);
        tags.add(CAIntel.TAG);
        return tags;
    }

    /** The objective, one line. */
    static String objective() {
        String sys = CAAutarch.system();
        if (CASync.installed() && !CASync.pulled()) return "Feed it AI cores for more PP, and pick its abilities on the Story page.";
        if (CAAutarch.inCargo() > 0) return CASync.pulled() ? "Put the Autarch AI Core back in the cradle (the Story page)."
                : "Decide what to do with the Autarch AI Core (the Story page).";
        String ph = CASync.phase();
        if ("breaking".equals(ph)) return "Engineering is breaking the core down.";
        if ("broken".equals(ph) || "salvage".equals(ph)) return "None. Another Autarch-class core may turn up in salvage one day.";
        if ("sold".equals(ph)) return "None for now.";
        if (CASync.pulled()) return "Find an Autarch AI Core to put back in the cradle" + (sys != null ? ": the " + sys + " system." : ".");
        return sys != null && CASync.seen(CATriPad.FOUND) ? "Find the Autarch AI Core: a derelict research station in the " + sys + " system."
                : "Find the Autarch AI Core.";
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, IntelInfoPlugin.ListInfoMode mode) {
        Color c = getTitleColor(mode);
        info.addPara(getName(), c, 0f);
        try { info.addPara(CALog.esc(objective()), 3f, Misc.getGrayColor(), Misc.getHighlightColor()); }   // 0.3.6.2: guarded
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story intel line failed", t); }
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        try { describe(info); }   // 0.3.6.2: guarded, the game calls this outside any catch
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story intel failed", t); }
    }

    void describe(TooltipMakerAPI info) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        info.addPara("The Colonial Administration Suite on your TriPad runs on the Autarch AI Core. Every AI core you give it adds processing power (PP), "
                + "and you pick the abilities it runs with that PP.", 0f);
        info.addPara(CALog.esc(status()), hl, 10f);
        if (CASync.installed()) info.addPara("The core has %s PP. Your abilities use %s, and %s are free.", 4f, hl, "" + CASync.total(), "" + CASync.spent(), "" + Math.max(0, CASync.free()));
        info.addPara(CALog.esc(objective()), hl, 10f);
        int[] h = CASync.onHand();
        info.addPara("You have %s on hand, in your cargo and in Autopilot Storage.", 10f, hl, CASyncCatalog.cores(h[0], h[1], h[2]));
        info.addPara("The Story page of the Colony Autopilot intel has the abilities, the cores and the story so far.", gray, 10f);
    }
}
