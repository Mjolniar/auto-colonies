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
 * 0.3.0.0 (WS-B5, research 01 section 13): a hostile fleet at the colony makes a siege at once only when it's strong enough
 * to threaten it (NEAR_SHARE of its own defenses: station and patrols); a weaker one has to still be there at the next
 * daily look (NEAR_DAYS). 13 of 47 sieges in the author's game ended within seconds: a pirate passing through the gravity
 * well put the colony on defenses only and swapped its station augments back and forth. Blockades and raids, invasions
 * and blockades on their way still count at once.
 */
public class CASiege {
    static final float NEAR = 2000f;   // about a planet's gravity well; a fleet this close is at the colony
    static final float SYSTEM_FP = 150f;
    /** Days hostiles only somewhere in the system count as a siege before they're taken as part of the system (0.2.38.0). */
    static final float SYSTEM_DAYS = 30f;
    /** 0.3.0.0 (B5): hostiles at the colony with at least this share of its defense fleet points are a siege at once. */
    static final float NEAR_SHARE = CATuning.SIEGE_NEAR_SHARE;
    /** 0.3.0.0 (B5): days weaker hostiles must stay at the colony before they're a siege (the next daily look). */
    static final float NEAR_DAYS = CATuning.SIEGE_NEAR_DAYS;
    /** 0.3.0.0 (B5): CAData.siegeSystem key suffix ("marketId:near", so CATidy can tell whose it is) for when weaker hostiles were first seen at a colony. */
    static final String NEAR_KEY = ":near";
    static final String KEY = "siege:";
    static final Map<String, String> cache = new HashMap<String, String>();
    static float cachedDay = -1f;

    /** Why the colony is under siege, or null. */
    public static String why(MarketAPI m) {
        float day = CADay.now();
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
        float defense = CAItems.defenseFP(m);
        // 0.3.0.0 (B5, 01 section 13): a fleet passing through the gravity well isn't a siege unless it threatens the colony
        Map<String, Long> seen = CAData.get().siegeSystem();
        String nk = m.getId() + NEAR_KEY;
        if (near > 0f) {
            Long first = seen.get(nk);
            long now = Global.getSector().getClock().getTimestamp();
            if (first == null) { first = now; seen.put(nk, now); }
            float days = first == now ? 0f : Global.getSector().getClock().getElapsedDaysSince(first);
            if (nearSiege(near, defense, days)) return "hostile fleets at the colony";
        } else seen.remove(nk);
        if (all > Math.max(SYSTEM_FP, defense + friendlyFP(sys))) {
            // hostiles that live in the system (Remnant, IX patrols, a pirate base's fleets) made it a siege forever, and a
            // colony under siege only builds defenses (0.2.38.0): after SYSTEM_DAYS of only that, it's how the system is
            Map<String, Long> since = CAData.get().siegeSystem();
            Long t = since.get(m.getId());
            if (t == null) since.put(m.getId(), Global.getSector().getClock().getTimestamp());
            else if (Global.getSector().getClock().getElapsedDaysSince(t) >= SYSTEM_DAYS) return incoming(sys);
            return (int) all + " fleet points of hostiles in the system";
        }
        CAData.get().siegeSystem().remove(m.getId());
        return incoming(sys);
    }

    /**
     * Pure, 0.3.0.0 (B5): hostiles at the colony (near fleet points) make a siege when they threaten it (NEAR_SHARE of its
     * defense, so any at an undefended colony) or have stayed NEAR_DAYS.
     */
    static boolean nearSiege(float near, float defense, float daysThere) {
        if (near <= 0f) return false;
        return near >= NEAR_SHARE * Math.max(0f, defense) || daysThere >= NEAR_DAYS;
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
                CALog.status("siege:" + m.getId(), m.getName() + ": under siege (" + w + "), defenses only");
            } else if (w == null && CALog.noted(key)) {   // 0.3.0.0: once-keys live in CAData.notedAt (CALog)
                CALog.forget(key);
                CALog.status("siegeend:" + m.getId(), m.getName() + ": siege over");
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
