package data.scripts.jonahtweaks;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** Removes the retired jt_patronage condition (JTPatronageCondition) from any market that still has it, every 5 seconds. */
public class JTCampaignScript implements EveryFrameScript {
    private float timer = 0f;

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return false; }

    public void advance(float amount) {
        timer -= amount;
        if (timer > 0f) return;
        timer = 5f;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.hasCondition(JTPatronageCondition.ID)) { market.removeCondition(JTPatronageCondition.ID); market.reapplyConditions(); }
        }
    }
}
