package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.util.ArrayList;
import java.util.List;

/** Adds "Colony Autopilot..." to the right-click menu of Population & Infrastructure on your colonies. */
public class CAOptionProvider extends BaseIndustryOptionProvider {
    public static final Object OPEN = new Object();

    public static void register() {
        ListenerManagerAPI l = Global.getSector().getListenerManager();
        if (!l.hasListenerOfClass(CAOptionProvider.class)) l.addListener(new CAOptionProvider(), true);
    }

    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (ind == null || ind.getMarket() == null || !ind.getMarket().isPlayerOwned()) return null;
        if (!ind.getSpec().hasTag("population")) return null;
        List<IndustryOptionData> out = new ArrayList<IndustryOptionData>();
        out.add(new IndustryOptionData("Colony Autopilot...", OPEN, ind, this));
        return out;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        tooltip.addPara("Open this colony's Autopilot panel.", 0f);   // 0.3.0.0 (WS-F3 fix pass 3): shorter
    }

    /**
     * 0.3.3.0: one Autopilot line at the end of a build menu row's tooltip (Advisor colonies by default; CABuildHints). The
     * game calls this for an industry that isn't built yet on 0.98.5a; 0.98a has no such call, so nothing shows there (the
     * panel beside the menu does). No @Override: the 0.98a interface lacks the method. Reads kept scores only.
     */
    public void addToIndustryTooltipIfIndustryNotPresent(com.fs.starfarer.api.campaign.econ.Industry ind,
            com.fs.starfarer.api.campaign.econ.Industry.IndustryTooltipMode mode, TooltipMakerAPI tooltip, float width, boolean expanded) {
        if (mode != com.fs.starfarer.api.campaign.econ.Industry.IndustryTooltipMode.ADD_INDUSTRY || tooltip == null) return;
        try {
            String[] line = CABuildHints.tooltip(ind);
            if (line == null) return;
            tooltip.addPara("Autopilot: %s. %s", 10f, com.fs.starfarer.api.util.Misc.getHighlightColor(), line[0], line[1]);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "build menu tooltip line failed", t); }
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id != OPEN) return;
        try {
            CAColonyDialog d = new CAColonyDialog(opt.ind.getMarket());
            ui.showDialog(CAColonyDialog.W, CAColonyDialog.H, d);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "opening the colony panel failed", t); }
    }
}
