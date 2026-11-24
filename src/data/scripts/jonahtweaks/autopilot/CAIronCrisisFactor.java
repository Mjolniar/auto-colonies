package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;

/**
 * 0.3.16.4: Tier V Iron stability's half-speed colony crises: a factor on the game's crisis meter (HostileActivityEventIntel)
 * that multiplies all monthly progress by MULT while Iron stability is online, the way a Military Base does. CATierFive adds
 * it when Iron stability comes online and takes it off when it goes; Prepare for removal takes it off (CATierFive.removeAll).
 * With Iron stability offline it multiplies by 1 and doesn't show.
 */
public class CAIronCrisisFactor extends BaseEventFactor {
    public static final float MULT = 0.5f;

    static boolean on() {
        try { return CATierFive.online(CATierFive.IRON); } catch (Throwable t) { return false; }
    }

    @Override
    public float getAllProgressMult(BaseEventIntel intel) { return on() ? MULT : 1f; }

    @Override
    public boolean shouldShow(BaseEventIntel intel) { return on(); }

    @Override
    public String getDesc(BaseEventIntel intel) { return "Iron stability"; }

    @Override
    public String getProgressStr(BaseEventIntel intel) { return on() ? "x" + Misc.getRoundedValueMaxOneAfterDecimal(MULT) : ""; }

    @Override
    public Color getProgressColor(BaseEventIntel intel) { return Misc.getPositiveHighlightColor(); }

    @Override
    public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                tooltip.addPara("The Autarch AI Core's Iron stability slows colony crises to %s speed while it's online.", 0f,
                        Misc.getHighlightColor(), "half");
            }
        };
    }
}
