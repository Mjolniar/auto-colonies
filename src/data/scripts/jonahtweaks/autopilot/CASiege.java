package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.util.Misc;

import java.util.HashMap;
import java.util.Map;

/**
 * A colony under siege: blockaded, hostile fleets at the planet, hostiles in the system that outweigh its defenses (the
 * rule CADefense calls a defense fleet on), or a hostile raid, invasion or blockade on its way to the system. While it
 * lasts Autopilot only builds defenses there (nothing at all during a fast buildout), and leaves its items, AI cores,
 * policies, station augments and projects as they are; trade contracts don't count on its goods, since Theory of
 * Toolbox only delivers from colonies with accessibility above 0, which a blockade takes away. Checked once a day.
 */
public class CASiege {
    static final float NEAR = 2000f;   // about a planet's gravity well; a fleet this close is at the colony
    static final float SYSTEM_FP = 150f;
    static final String KEY = "siege:";
    static final Map<String, String> cache = new HashMap<String, String>();
    static float cachedDay = -1f;

    /** Why the colony is under siege, or null. */
    public static String why(MarketAPI m) {
        float day = Global.getSector().getClock().getElapsedDaysSince(0L);
        if (day - cachedDay >= 1f || day < cachedDay) { cache.clear(); cachedDay = day; }
        if (cache.containsKey(m.getId())) return cache.get(m.getId());
        String w = null;
        try { w = compute(m); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "siege check failed at " + m.getName(), t); }
        cache.put(m.getId(), w);
        return w;
    }

    public static boolean held(MarketAPI m) { return why(m) != null; }

    static String compute(MarketAPI m) {
        if (m.hasCondition(Conditions.BLOCKADED)) return "blockaded";
        StarSystemAPI sys = m.getStarSystem();
        if (sys == null || m.getPrimaryEntity() == null) return null;
        float near = 0f, all = 0f;
        for (CampaignFleetAPI f : sys.getFleets()) {
            if (!hostile(f)) continue;
            all += f.getFleetPoints();
            if (Misc.getDistance(f.getLocation(), m.getPrimaryEntity().getLocation()) < NEAR) near += f.getFleetPoints();
        }
        if (near > 0f) return "hostile fleets at the colony";
        if (all > Math.max(SYSTEM_FP, CAItems.defenseFP(m) + friendlyFP(sys))) return (int) all + " fleet points of hostiles in the system";
        return incoming(sys);
    }

    static boolean hostile(CampaignFleetAPI f) {
        return !f.isPlayerFleet() && !f.isStationMode() && f.getFaction() != null && f.getFaction().isHostileTo(Factions.PLAYER);
    }

    static float friendlyFP(StarSystemAPI sys) {
        float fp = 0f;
        for (CampaignFleetAPI f : sys.getFleets()) {
            if (f.isPlayerFleet() || f.getFaction() == null || !Factions.PLAYER.equals(f.getFaction().getId())) continue;
            fp += f.getFleetPoints();
        }
        return fp;
    }

    /** A hostile raid or invasion (RaidIntel) or a raid or blockade group (GenericRaidFGI, which Nexerelin's blockades use) headed for the system. */
    static String incoming(StarSystemAPI sys) {
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel()) {
            if (i.isEnded() || i.isEnding()) continue;
            if (i instanceof RaidIntel) {
                RaidIntel r = (RaidIntel) i;
                if (r.getSystem() == sys && r.getFaction() != null && r.getFaction().isHostileTo(Factions.PLAYER)) return "a raid or invasion is on its way";
            } else if (i instanceof GenericRaidFGI) {
                GenericRaidFGI g = (GenericRaidFGI) i;
                if (!g.isPlayerTargeted() || g.isAborted() || g.isFailed() || g.isSucceeded()) continue;
                if (g.getRaidAction() != null && g.getRaidAction().getWhere() == sys) return "a raid or blockade is on its way";
            }
        }
        return null;
    }

    /** Daily: logs when a managed colony comes under siege and when it ends (once each, kept in the save). */
    public static void update(CAData d) {
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if (c.mode == CAData.Mode.OFF || CAEngine.isOutpost(m)) continue;
            String w = why(m), key = KEY + m.getId();
            if (w != null && CALog.once(key)) {
                CALog.add(m.getName() + ": under siege (" + w + "); only defenses are built there, and its items, AI cores, policies, "
                        + "station augments and trade contract goods wait until it's over");
            } else if (w == null && d.noted().contains(key)) {
                CALog.forget(key);
                CALog.add(m.getName() + ": siege over; back to normal");
            }
        }
    }

    /** The markets in the list that aren't under siege. */
    public static java.util.List<MarketAPI> free(java.util.List<MarketAPI> markets) {
        java.util.List<MarketAPI> out = new java.util.ArrayList<MarketAPI>();
        for (MarketAPI m : markets) if (!held(m)) out.add(m);
        return out;
    }

    /** A defense pick: ground defense, patrols or a station. */
    public static boolean defensive(CAEngine.Pick p) { return p != null && (p.fx[3] > 0f || p.fx[4] > 0f); }
}
