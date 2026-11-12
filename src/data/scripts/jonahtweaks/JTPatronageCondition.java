package data.scripts.jonahtweaks;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/**
 * Retired colony condition from the earlier tweaks mod (1.x) ("Expanded Infrastructure"). It has no effect now. It stays registered
 * (data/campaign/market_conditions.csv) only so saves that still reference jt_patronage load, and JTCampaignScript
 * removes it from every market it finds.
 */
public class JTPatronageCondition extends BaseMarketConditionPlugin {
    public static final String ID = "jt_patronage";

    public void apply(String id) { }

    /** Clears every modifier the old version could have applied, so removing it leaves the market exactly vanilla. */
    public void unapply(String id) {
        market.getIncomeMult().unmodify(id);
        market.getUpkeepMult().unmodify(id);
        market.getStability().unmodify(id);
        market.getAccessibilityMod().unmodify(id);
        market.getHazard().unmodify(id);
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(id);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodify(id);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).unmodify(id);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).unmodify(id);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).unmodify(id);
        market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodify(id);
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodify(id);
        market.getStats().getDynamic().getMod(Stats.CUSTOM_PRODUCTION_MOD).unmodify(id);
        market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).unmodify(id);
        for (Industry ind : market.getIndustries()) ind.getSupplyBonusFromOther().unmodify(id);
    }

    public boolean hasCustomTooltip() { return true; }

    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        tooltip.addPara("No effect; this condition is being removed.", 10f);
    }
}
