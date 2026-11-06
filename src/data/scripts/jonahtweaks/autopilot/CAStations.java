package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * A station at every colony (2.42.0). A station fights next to your fleet and patrols, and a raid, bombardment or
 * invasion has to beat it first, so it's how a colony holds off fleets; ground defenses only matter once troops land.
 * The engine's money-weighted score left most colonies without one: on Balanced a 250,000 Orbital Station came to about
 * -2,000 a month (its price spread over three years outweighed its defense and fleet value), so only the Security goal,
 * a threat, a Fortress or Bastion plan or spending freely built one.
 *
 * Now every colony plans one once it's out of its fast buildout and has grown to size 4 (or is half a year old), and
 * bigger colonies plan the next tier: a Battlestation from size 6 (5 at the capital), a Star Fortress from size 8 (7 at
 * the capital). These steps earn their plan rank even when their upkeep outweighs what the goal's weights give them
 * (CAEngine.finish), and follow the station line under Settings, Building and planning. Setting: Station at every colony.
 */
public class CAStations {
    static final int TIER2_SIZE = 6, TIER3_SIZE = 8;
    static final float YOUNG_DAYS = 180f;

    static CAPlan.Step step(String id, String label) { return new CAPlan.Step(CAPlan.Kind.BUILD, id, label); }

    /** The colony's station steps, put in front of its own plan (CAPlan.build). */
    public static List<CAPlan.Step> stepsFor(MarketAPI m) {
        List<CAPlan.Step> out = new ArrayList<CAPlan.Step>();
        if (m == null || CAData.settings().noStationPlan || CAEngine.isOutpost(m) || CAHypershunt.is(m) || m.hasTag("market_no_industries_allowed")) return out;
        if (CAData.get().colony(m.getId()).buildout) return out;   // a fast buildout makes money first
        int size = m.getSize();
        if (size < 4 && m.getDaysInExistence() < YOUNG_DAYS) return out;
        out.add(step("station", "Orbital station (every colony)"));
        boolean cap = CACapital.is(m);
        int tier = size >= TIER3_SIZE || (cap && size >= TIER3_SIZE - 1) ? 3 : size >= TIER2_SIZE || (cap && size >= TIER2_SIZE - 1) ? 2 : 1;
        for (int t = 2; t <= tier; t++) {
            String id = tierId(m, t);
            if (id != null) out.add(step(id, CAWorldPlanner.industryName(id) + " (size " + size + (cap ? ", capital" : "") + ")"));
        }
        return out;
    }

    /** The station of this tier in the colony's line: its own station's upgrades, or the line you picked (low tech by default). */
    static String tierId(MarketAPI m, int tier) {
        String id = null;
        for (Industry ind : m.getIndustries()) if (CAPlan.stationTier(ind.getSpec()) > 0) id = ind.getId();
        if (id == null) id = CASettings.stationStarter(CAData.settings().stationLine);
        if (id == null) id = "orbitalstation";
        for (int k = 0; k < 6 && id != null; k++) {
            IndustrySpecAPI sp = CAPlan.spec(id);
            if (sp == null) return null;
            int t = CAPlan.stationTier(sp);
            if (t == tier) return id;
            if (t > tier) return null;   // already past it
            id = sp.getUpgrade();
        }
        return null;
    }

    /** A station step here, or what leads to one (an Orbital Station on the way to a Battlestation). */
    public static boolean isStep(MarketAPI m, String id) {
        if (id == null || m == null) return false;
        IndustrySpecAPI sp = CAPlan.spec(id);
        if (sp == null || CAPlan.stationTier(sp) <= 0) return false;
        for (CAPlan.Step s : stepsFor(m)) if (s.id.equals(id) || s.id.equals("station") || CAPlan.leadsTo(id, s.id)) return true;
        return false;
    }
}
