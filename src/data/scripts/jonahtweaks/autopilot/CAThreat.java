package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * Threat estimate per colony (0 = calm, 1+ = serious) from hostile neighbors, the Hostile Activity crisis (and being
 * the likely target of the coming one, CACrisis.targets) and raid/blockade conditions. At THRESHOLD the colony is planned with the Security profile until it drops below CLEAR
 * and has stayed high for at least MIN_DAYS, so an estimate hovering at the line doesn't flip policies back and forth.
 */
public class CAThreat {
    public static final float THRESHOLD = 0.7f, CLEAR = 0.65f;   // neighbors alone cap at 0.6, so CLEAR stays above that
    static final float MIN_DAYS = 60f;
    /** The colony the coming colony crisis is likely to hit: with the meter at 500 or more (+0.33) that's past THRESHOLD. */
    static final float CRISIS_TARGET = 0.4f;

    public static float estimate(MarketAPI m) {
        float t = 0f;
        try {
            FactionAPI player = Global.getSector().getPlayerFaction();
            int hostile = 0, size = 0;
            for (MarketAPI o : Global.getSector().getEconomy().getMarketsCopy()) {
                if (o.isPlayerOwned() || o.getFaction() == null || !o.getFaction().isHostileTo(Factions.PLAYER)) continue;
                if (o.getPrimaryEntity() == null || m.getPrimaryEntity() == null) continue;
                if (Misc.getDistanceLY(m.getPrimaryEntity(), o.getPrimaryEntity()) > 8f) continue;
                hostile++; size += o.getSize();
            }
            t += Math.min(0.6f, 0.1f * hostile + 0.02f * size);
        } catch (Throwable ignored) { }
        try {
            HostileActivityEventIntel ha = HostileActivityEventIntel.get();
            if (ha != null && ha.getMaxProgress() > 0) t += 0.4f * ha.getProgress() / (float) ha.getMaxProgress();
        } catch (Throwable ignored) { }
        if (m.hasCondition("nex_raid_condition")) t += 0.3f;
        if (m.hasCondition("blockaded")) t += 0.3f;
        if (m.hasCondition("pather_cells")) t += 0.15f;
        if (m.hasCondition("nex_rebellion_condition")) t += 0.3f;
        try { if (CACrisis.targets().contains(m)) t += CRISIS_TARGET; } catch (Throwable ignored) { }
        return t;
    }

    /** Whether the colony is in its high-threat state; updates the saved state and logs the change. */
    public static boolean isHigh(MarketAPI m) {
        CAData.Colony c = CAData.get().colony(m.getId());
        float t = estimate(m);
        boolean security = c.profile == CAData.Profile.SECURITY;
        if (!c.threatHigh) {
            if (t < THRESHOLD) return false;
            c.threatHigh = true;
            c.threatSince = Global.getSector().getClock().getTimestamp();
            CALog.status("threat:" + m.getId(), m.getName() + ": threat high (" + Math.round(t * 100f) + "%)" + (security ? "" : ", defense first"));
            return true;
        }
        if (t >= CLEAR || Global.getSector().getClock().getElapsedDaysSince(c.threatSince) < MIN_DAYS) return true;
        c.threatHigh = false;
        CALog.quiet(m.getName() + ": threat down (" + Math.round(t * 100f) + "%)" + (security ? "" : ", back to " + c.profile.label));
        return false;
    }

    /** The profile to plan with: the colony's own, or Security while the threat is high. */
    public static CAData.Profile effective(MarketAPI m, CAData.Profile own) {
        if (own == CAData.Profile.SECURITY) return own;
        return isHigh(m) ? CAData.Profile.SECURITY : own;
    }
}
