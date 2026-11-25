package data.scripts.jonahtweaks.autopilot;

import java.util.*;

/**
 * What each Station Augments augment does in a fight, from its source (Station Augments 2.4.3), as a share of the
 * station's own combat power on this station: its fighter wings, guns, shields, drones and AI core (CAStationKit).
 * CAAugments turns the share into the engine's fleet effect, scaled by the station's tier and, while the colony is
 * threatened, by THREAT. Augments whose effect works against an AI-run station are never picked (NEVER), and Command
 * Center's command points count for nothing: a station defends itself without orders.
 * 0.3.18.4: values by the station's design line (CAStationKit.tech). Low tech (armored, few shields): K-Type Shield Shunt
 * (armor +40% on every section) and Bubble Shield. Midline: Axial Overclocking and Deep Shield Dissipators (midline only).
 * High tech and any gun station: Tactical Link, which reaches the station's own guns (+35% ballistic and energy range).
 * Fragment augments (FRAGMENT) count once the station has the Fragment Swarm (CAAugments.pick).
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
    /** Four SotF weapon platforms with beta cores, as a fleet effect whatever the station. 0.3.18.4: 0.2 (was 0.3, a third of a star fortress). */
    static final float PLATFORMS_FX = 0.2f;

    /** Never picked, with why: they work against a station the AI fights. */
    static final Map<String, String> NEVER = new LinkedHashMap<String, String>();
    static {
        NEVER.put("SA_movableStation", "Macro Thrusters let the AI fly the station into the enemy, where it dies");
        NEVER.put("SA_backlineStation", "Backline Deployment parks the station at the back of the field, away from the fight it's there for");
        NEVER.put("SA_safetyOverrides", "Safety Shunt starts the station's combat readiness falling at once, so long fights end in malfunctions");
        NEVER.put("SA_supportOutfit", "Support Outfit cuts point defense range to almost nothing and the rate of fire by a fifth");
    }

    /** 0.3.18.4: the Fragment Swarm: the other fragment augments need it on the station. */
    static final String SWARM = "SA_fragmentSwarm";

    /**
     * 0.3.18.4: the fragment augments Autopilot installs with an item from Autopilot Storage (Fragment Fabricator for the swarm
     * and Secondary Fabricator, Threat Processing Unit for the rest). Other augments that use an item (the Shrouded ones,
     * which the Luddic Path reacts to) are left to you.
     */
    static final Set<String> FRAGMENT = new HashSet<String>(Arrays.asList(SWARM, "SA_secondaryFabricator", "SA_fragmentCoordinator",
            "SA_kineticFragments", "SA_seekerFragments", "SA_attackSwarms", "SA_constructionSwarms", "SA_defabSwarms"));

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
        // 0.3.18.4: Bubble Shield counts most on an armored low-tech station, which has few shields of its own
        boolean bare = k.shielded == 0 || k.armored();
        if (id.equals("SA_bubbleShield")) return new Gain(bare ? 0.4f : 0.3f,
                "A 100,000-flux shield around the whole station stops shots before they reach it" + (k.shielded == 0 ? " (this station has no shields of its own)" : bare ? " (an armored station with few shields of its own)" : ""));
        if (id.equals("SA_defensePlatforms")) { Gain g = new Gain(0f, "Four weapon platforms with beta cores deploy around the station"); g.fx = PLATFORMS_FX; return g; }
        if (id.equals("SA_shieldShunt")) {
            // 0.3.18.4: armor +40% on every section, shielded or not (Station Augments' shieldShunt); shields become damper fields
            if (k.armored()) return new Gain(0.35f, "Armor +40% on every section, its few shields become damper fields: suits an armored low-tech station");
            if (k.midline()) return new Gain(0.08f, "Armor +40% on every section, but the shields a midline station keeps up become damper fields");
            return new Gain(0.12f, "Armor +40% on every section, its shields become damper fields");
        }
        // 0.3.18.4: Station Augments allows Deep Shield Dissipators on midline stations only
        if (id.equals("SA_fluxShunt")) return k.midline() ? new Gain(0.25f, "Shields dissipate hard flux at half rate, for a midline station whose shields never drop")
                : new Gain(-1f, "Midline stations only");
        // 0.3.11.6: only where its guns turn faster too; elsewhere the hull outruns the guns and beams lose their targets
        if (id.equals("SA_axialOverclocking")) return k.axialGuns() ? new Gain(0.3f,"Midline guns turn three times as fast, so they can track targets. The station turns four times as fast")
                : new Gain(-1f, "The station would spin four times as fast while its guns don't, so they'd lose their targets");
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
        // 0.3.18.4: Tactical Link reaches the station's own sections too (Station Augments' supportPackage skips no ship of its side)
        if (id.equals("SA_tacticalLink")) {
            float g = k.gunShare();
            return new Gain(0.35f * g + (patrols ? 0.05f : 0.01f), "+35% range for the station's ballistic and energy guns, " + Math.round(g * 100f)
                    + "% of its damage" + (patrols ? ", and for the patrols that fight beside it" : ""));
        }
        // 0.3.18.4: fragment augments (FRAGMENT). Each uses a Fragment Fabricator or Threat Processing Unit from Autopilot Storage
        if (id.equals(SWARM)) return new Gain(0f, "A swarm of 225 Threat fragments around the station, for the fragment augments");
        if (id.equals("SA_kineticFragments")) return new Gain(0.25f, "Fragment volleys that break shields, at 6,000 range");
        if (id.equals("SA_seekerFragments")) return new Gain(0.25f, "Fragment strike groups that break heavy armor, at 6,000 range");
        if (id.equals("SA_attackSwarms")) return new Gain(0.2f, "Fragment attack formations that reach hostile ships at any distance");
        if (id.equals("SA_defabSwarms")) return new Gain(0.1f, "Fragment swarms that tear apart ships with their shields down, at 9,000 range");
        if (id.equals("SA_constructionSwarms")) return new Gain(0.12f, "A small garrison of Threat ships built during battle. Raises the colony's hazard rating");
        if (id.equals("SA_fragmentCoordinator")) return new Gain(0.05f, "Fragment swarm 40% larger");
        if (id.equals("SA_secondaryFabricator")) return new Gain(0.05f, "Fragments replaced 60% faster");
        if (id.equals("SA_ecmPackage")) return new Gain(0.05f, "+15 ECM in battle: enemy weapons reach less far");
        if (id.equals("SA_navRelay")) return new Gain(0.02f, "+20 nav rating for friendly ships in battle");
        if (id.equals("SA_highExplosive")) return new Gain(0.03f, "Only matters once the station dies: a far bigger explosion");
        if (id.equals("SA_efficiencyOverhaul")) return new Gain(0.03f, "The station repairs 50% faster between fights");   // 0.3.11.2: hull counts in auto-resolve
        if (id.equals("SA_stationRepairUnit")) return new Gain(0.08f,"Disruption ends twice as fast, so the station is back sooner after a raid");
        if (id.equals("SA_highResSensors")) return new Gain(0f, "Sight range in battle, sensor range for your fleets nearby");
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

    /**
     * 0.3.11.4: away from the capital combat counts this many times while threatened. Station Augments adds its fight bonus
     * after the game totals each side's strength, so augments only decide fights you are in, which are mostly at the capital.
     */
    static final float THREAT_AWAY = 2f;

    /** Pure (0.3.11.4): how many times combat counts: 1 when calm, THREAT at the capital, THREAT_AWAY elsewhere. */
    public static float threatMult(boolean threatened, boolean capital) { return !threatened ? 1f : capital ? THREAT : THREAT_AWAY; }

    /** The engine's fleet effect of a gain on a station of this tier, times THREAT while threatened. */
    public static float fleetEffect(Gain g, int tier, boolean threatened) { return fleetEffect(g, tier, threatened ? THREAT : 1f); }

    /** 0.3.11.4: the fleet effect times mult (threatMult). */
    public static float fleetEffect(Gain g, int tier, float mult) {
        float fx = g.fx + Math.max(0f, g.share) * STATION_FX[Math.max(0, Math.min(4, tier))];
        return fx * mult;
    }

    /** What an AI core in a fighter-carrying station adds through AI Fighter Uplink, as a fleet effect (for CAItems). */
    public static float uplinkCoreFx(CAStationKit k, int coreIndex) {
        if (coreIndex < 0 || coreIndex > 2 || k.wings == 0) return 0f;
        return k.fighterShare() * UPLINK[coreIndex] * STATION_FX[Math.max(0, Math.min(4, k.tier))];
    }
}
