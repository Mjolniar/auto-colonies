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
        tooltip.addPara("Open this colony's Autopilot panel: goal profile, mode, and recommended builds.", 0f);
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id != OPEN) return;
        CAColonyDialog d = new CAColonyDialog(opt.ind.getMarket());
        ui.showDialog(CAColonyDialog.W, CAColonyDialog.H, d);
    }
}
