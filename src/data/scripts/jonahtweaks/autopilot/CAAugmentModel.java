package data.scripts.jonahtweaks.autopilot;

import java.util.*;

/**
 * What each Station Augments augment does in a fight, from its source (Station Augments 2.4.3), as a share of the
 * station's own combat power on this station: its fighter wings, guns, shields, drones and AI core (CAStationKit).
 * CAAugments turns the share into the engine's fleet effect, scaled by the station's tier and, while the colony is
 * threatened, by THREAT. Augments whose effect works against an AI-run station are never picked (NEVER), and Command
 * Center's command points count for nothing: a station defends itself without orders.
 */
public class CAAugmentModel {
    /** The station's own fleet effect by tier (CAPlan.stationTier): orbital station, battlestation, star fortress, a tier above. */
    static final float[] STATION_FX = {0f, 0.4f, 0.7f, 1.0f, 1.3f};
    /** Combat counts this many times as much while the colony is threatened (its plan profile is Security) or under siege. */
    static final float THREAT = 4f;
    /** What an AI core captaining a fighter adds to that fighter, alpha / beta / gamma (AI Fighter Uplink). */
    static final float[] UPLINK = {0.35f, 0.2f, 0.1f};
    /** Warmind Refit: the station's own AI core as a Dustkeeper warmind on every section, alpha / beta / gamma. */
    static final float[] WARMIND = {0.2f, 0.12f, 0.06f};
    /** Four SotF weapon platforms with beta cores, as a fleet effect whatever the station. */
    static final float PLATFORMS_FX = 0.3f;

    /** Never picked, with why: they work against a station the AI fights. */
    static final Map<String, String> NEVER = new LinkedHashMap<String, String>();
    static {
        NEVER.put("SA_movableStation", "Macro Thrusters let the AI fly the station into the enemy, where it dies");
        NEVER.put("SA_backlineStation", "Backline Deployment parks the station at the back of the field, away from the fight it's there for");
        NEVER.put("SA_safetyOverrides", "Safety Shunt starts the station's combat readiness falling at once, so long fights end in malfunctions");
        NEVER.put("SA_supportOutfit", "Support Outfit cuts point defense range to almost nothing and the rate of fire by a fifth");
    }

    static int core(String coreId) {
        if ("alpha_core".equals(coreId)) return 0;
        if ("beta_core".equals(coreId)) return 1;
        if ("gamma_core".equals(coreId)) return 2;
        return -1;
    }

    /** A share of combat power and the line that explains it; share < 0 means the augment does nothing worth having here. */
    public static class Gain {
        public float share;
        public float fx;        // a fixed fleet effect, whatever the station (weapon platforms)
        public String line;
        Gain(float share, String line) { this.share = share; this.line = line; }
    }

    /**
     * The combat gain of augment id on this station, or null when the model has nothing to say (an augment it doesn't
     * know: CAAugments falls back to the mod's own combat rating). dustkeepersHostile: SotF's Dustkeepers would turn a
     * Warmind Refit against you. patrols: the colony has a patrol base, so friendly ships fight beside the station.
     */
    public static Gain gain(String id, CAStationKit k, String coreId, boolean patrols, boolean dustkeepersHostile) {
        int c = core(coreId);
        float f = k.fighterShare();
        if (NEVER.containsKey(id)) return new Gain(-1f, NEVER.get(id));
        if (id.equals("SA_bubbleShield")) return new Gain(k.shielded > 0 ? 0.3f : 0.4f,
                "A 100,000-flux shield around the whole station stops shots before they reach it" + (k.shielded > 0 ? "" : " (this station has no shields of its own)"));
        if (id.equals("SA_defensePlatforms")) { Gain g = new Gain(0f, "Four weapon platforms with beta cores deploy around the station"); g.fx = PLATFORMS_FX; return g; }
        if (id.equals("SA_shieldShunt")) {
            if (k.shielded == 0) return new Gain(0.04f, "Armor +40% and hull +10% only on its bare sections (it has no shields to swap)");
            return new Gain(0.05f + 0.15f * k.shielded / Math.max(1, k.modules), "Shields become damper fields, armor +40%, hull +10% on " + k.shielded + " shielded sections");
        }
        if (id.equals("SA_fluxShunt")) return new Gain(k.midline() ? 0.2f : 0.05f, "Shields dissipate hard flux at half rate, for a midline station whose shields never drop");
        if (id.equals("SA_axialOverclocking")) return k.midline() ? new Gain(0.2f, "Midline guns turn three times as fast, so they can track targets; the station turns four times as fast")
                : new Gain(0.03f, "The station turns four times as fast");
        if (id.equals("SA_aiFighterUplink")) {
            if (c < 0) return new Gain(-1f, "Needs an AI core in the station");
            if (k.wings == 0 && !k.drones) return new Gain(-1f, "No fighters or drones to captain");
            return new Gain(f * UPLINK[c] + (k.drones ? 0.02f : 0f), "Every fighter" + (k.drones ? " and drone" : "") + " gets a " + coreName(c)
                    + " captain: " + k.wings + " wings, " + Math.round(f * 100f) + "% of the station's damage");
        }
        if (id.equals("SA_fighterTimeflow")) {
            if (k.wings == 0 && !k.drones) return new Gain(-1f, "No fighters or drones");
            return new Gain(f * 0.3f + (k.drones ? 0.03f : 0f), "Fighters" + (k.drones ? " and drones" : "") + " act 30% faster: "
                    + k.wings + " wings, " + Math.round(f * 100f) + "% of the station's damage");
        }
        if (id.equals("SA_regenerativeDrones")) return k.drones ? new Gain(0.08f, "Drones launch at once and come back faster") : new Gain(-1f, "No drone system");
        if (id.equals("SA_droneAAF")) return k.drones ? new Gain(0.05f, "Drones get a weapon overdrive") : new Gain(-1f, "No drone system");
        if (id.equals("SA_tacticalLink")) return new Gain(patrols ? 0.1f : 0.03f, "Friendly ships near the station: +35% range, faster and nimbler"
                + (patrols ? " (patrols fight beside it here)" : " (few friendly ships fight beside it here)"));
        if (id.equals("SA_ecmPackage")) return new Gain(0.05f, "+15 ECM in battle: enemy weapons reach less far");
        if (id.equals("SA_navRelay")) return new Gain(0.02f, "+20 nav rating for friendly ships in battle");
        if (id.equals("SA_highExplosive")) return new Gain(0.03f, "Only matters once the station dies: a far bigger explosion");
        if (id.equals("SA_stationRepairUnit")) return new Gain(0.03f, "Disruption ends twice as fast, so the station is back sooner after a raid");
        if (id.equals("SA_highResSensors")) return new Gain(0f, "Sight range in battle; sensor range for your fleets nearby");
        if (id.equals("SA_commsCenter")) return new Gain(0f, "Command points don't matter when the station defends itself");
        if (id.equals("SA_logisticsDrones")) return new Gain(0f, "Combat readiness for your fleets near the station. The station's own is unchanged");
        if (id.equals("SA_warmindProtocols")) {
            if (c < 0) return new Gain(-1f, "Needs an AI core in the station");
            if (dustkeepersHostile) return new Gain(-1f, "The Dustkeepers are hostile: the warmind would turn the station against you");
            return new Gain(WARMIND[c], "The station's " + coreName(c) + " runs every section as a warmind");
        }
        return null;
    }

    static String coreName(int c) { return c == 0 ? "Alpha" : c == 1 ? "Beta" : "Gamma"; }

    /** The engine's fleet effect of a gain on a station of this tier, times THREAT while threatened. */
    public static float fleetEffect(Gain g, int tier, boolean threatened) {
        float fx = g.fx + Math.max(0f, g.share) * STATION_FX[Math.max(0, Math.min(4, tier))];
        return threatened ? fx * THREAT : fx;
    }

    /** What an AI core in a fighter-carrying station adds through AI Fighter Uplink, as a fleet effect (for CAItems). */
    public static float uplinkCoreFx(CAStationKit k, int coreIndex) {
        if (coreIndex < 0 || coreIndex > 2 || k.wings == 0) return 0f;
        return k.fighterShare() * UPLINK[coreIndex] * STATION_FX[Math.max(0, Math.min(4, k.tier))];
    }
}
