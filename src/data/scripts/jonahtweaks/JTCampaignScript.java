package data.scripts.jonahtweaks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * Removes the retired jt_patronage condition (JTPatronageCondition) from any market that still has it. Nothing adds it
 * any more, so once per save load is enough; it used to scan every market in the sector every 5 seconds. It was only
 * ever a transient script, so no save refers to it.
 */
public class JTCampaignScript {
    public static void removeRetired() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.hasCondition(JTPatronageCondition.ID)) { market.removeCondition(JTPatronageCondition.ID); market.reapplyConditions(); }
        }
    }
}
