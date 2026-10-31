package data.scripts.jonahtweaks;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/** Jonah's Colony Manager: only cleans up the retired colony condition. All balance changes were removed. */
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
