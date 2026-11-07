package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Saved Colony Autopilot data: plain values only, stored in the save's persistent data. */
public class CAData {
    public static final String KEY = "jt_colony_autopilot";

    public enum Profile {
        BALANCED("Balanced"), INCOME("Money"), SECURITY("Security"), WAR("War production"),
        GROWTH("Rapid growth"), SELF("Self-sufficiency");
        public final String label;
        Profile(String label) { this.label = label; }
    }

    public enum Mode {
        OFF("Off"), ADVISOR("Advisor"), APPROVE("Approve"), AUTO("Auto");
        public final String label;
        Mode(String label) { this.label = label; }
    }

    public static class Colony {
        public Profile profile = Profile.BALANCED;
        public Mode mode = Mode.ADVISOR;
        public Set<String> locks = new HashSet<String>();
        public String pendingId, pendingName;
        public boolean pendingUpgrade;
        public float pendingCost;
        public boolean pendingNotified;
        public int spUsed = 0;
        public long lastHazardPay = 0L, lastFreePort = 0L;
        public boolean threatHigh = false;
        public long threatSince = 0L;
        // Station Augments: ids Autopilot installed here, and ids you removed that it won't add back
        public Set<String> augAuto, augSkip;
        public boolean augDefense;   // Autopilot last set its augments for defense while the threat was high
        // Coronal Hypershunt (AotD): the range Autopilot last set, and whether you set it yourself since
        public Integer hsRange;
        public boolean hsManual;
        // Pluto / Nidavelir (AotD): the settings Autopilot last set, whether you've changed them since, and when Nexus manpower last backed off
        public Map<String, Integer> megaSet;
        public boolean megaManual;
        public long megaHold;
        // Fast buildout (CABuildout): running, the credit floor (0 = default), and when net income last turned positive (0 = it isn't)
        public boolean buildout;
        public float buildoutFloor;
        public long buildoutProfitSince;
        // 2.38.0: when it started (0: before 2.38.0, set on its next pass) and when it last found nothing that pays (0: it found something)
        public long buildoutSince, buildoutIdleSince;
        // Autonomy (CAAutonomy): months in a row the colony has lost money, and the month last counted (year * 12 + month)
        public int lossStreak, lossMonth;

        public Map<String, Integer> megaSet() { if (megaSet == null) megaSet = new HashMap<String, Integer>(); return megaSet; }

        public Set<String> augAuto() { if (augAuto == null) augAuto = new HashSet<String>(); return augAuto; }
        public Set<String> augSkip() { if (augSkip == null) augSkip = new HashSet<String>(); return augSkip; }
    }

    public Map<String, Colony> colonies = new HashMap<String, Colony>();
    // calibration against your colonies (CACalibrate): building id -> smoothed actual/predicted ratio, and readings taken
    public Map<String, Float> calOut, calUp;
    public Map<String, Integer> calOutN, calUpN;
    public int calVersion;   // 1: upkeep corrections measured against the fixed prediction (CACalibrate.migrate)
    public Map<String, Float> calOut() { if (calOut == null) calOut = new HashMap<String, Float>(); return calOut; }
    public Map<String, Float> calUp() { if (calUp == null) calUp = new HashMap<String, Float>(); return calUp; }
    public Map<String, Integer> calOutN() { if (calOutN == null) calOutN = new HashMap<String, Integer>(); return calOutN; }
    public Map<String, Integer> calUpN() { if (calUpN == null) calUpN = new HashMap<String, Integer>(); return calUpN; }
    /** 2.44.0 market model (CAMarket): buildings under construction, "market|building|replaced" -> each good's supply there once done, and since when. */
    public Map<String, Map<String, Integer>> marketPending;
    public Map<String, Float> marketPendingSince;
    /** 2.44.1: "market|building" -> what a building being upgraded becomes (AotD upgrades have several), for CABuild.usedSlots. */
    public Map<String, String> upgradeTargets;
    public Map<String, String> upgradeTargets() { if (upgradeTargets == null) upgradeTargets = new HashMap<String, String>(); return upgradeTargets; }
    public Map<String, Map<String, Integer>> marketPending() { if (marketPending == null) marketPending = new HashMap<String, Map<String, Integer>>(); return marketPending; }
    public Map<String, Float> marketPendingSince() { if (marketPendingSince == null) marketPendingSince = new HashMap<String, Float>(); return marketPendingSince; }
    public float minStability = 5f;
    public boolean buildoutOffered;   // the first-colony fast buildout dialog has been shown (or doesn't apply to this save)
    public CASettings settings;
    public com.fs.starfarer.api.campaign.CargoAPI storage;
    public Map<String, Long> ambTransit;
    public Map<String, Long> adminTransit;
    // 2.41.2: when the colony limit first needed an administrator and none was on offer anywhere (0: not waiting); a recruiter
    // finds one after CAItems.RECRUIT_DAYS
    public long adminNeedSince;
    public Set<String> noted;
    // Random Assortment of Things settlement proposal waiting for approval: kind "found" (planet id) or "facility" (facility id)
    public String settlePendingKind, settlePendingId, settlePendingName;
    public float settlePendingCost;
    // a Random Assortment of Things settlement expedition on its way (2.37.0): the planet, when it left, its days, and what it cost
    public String settleExpeditionPlanet;
    public long settleExpeditionAt;
    public float settleExpeditionDays, settleExpeditionCost;
    // Nexerelin agents Autopilot hired (person ids); only these are given orders
    public Set<String> spyAgents;
    public Set<String> spyAgents() { if (spyAgents == null) spyAgents = new HashSet<String>(); return spyAgents; }
    // their roles (2.37.0: person id to security, relations or offense) and agents being recruited (role to when it began)
    public Map<String, String> spyRoles;
    public Map<String, Long> spyRecruits;
    public Map<String, String> spyRoles() { if (spyRoles == null) spyRoles = new HashMap<String, String>(); return spyRoles; }
    public Map<String, Long> spyRecruits() { if (spyRecruits == null) spyRecruits = new HashMap<String, Long>(); return spyRecruits; }
    // colonies Autopilot made autonomous in an emergency (Nexerelin): market id to the timestamp; only these are taken back
    public Map<String, Long> autonomous;
    public long lastAutonomyChange;
    public long researchWaitSince;   // when Auto research began holding databanks for a plan tech short of them (0: not waiting)
    public String researchStaging;   // 2.42.4: the tech whose databanks are on their way to the facility; nothing else starts meanwhile
    public Map<String, Long> autonomous() { if (autonomous == null) autonomous = new HashMap<String, Long>(); return autonomous; }
    // war fleets (CAWar): markets that sent raids, invasions or blockades at your colonies, to when they were last seen doing it
    public Map<String, Long> attackers;
    // station augment refit crews booked (CAAugments, 2.37.0): market id to when
    public Map<String, Long> augRefit;
    public Map<String, Long> augRefit() { if (augRefit == null) augRefit = new HashMap<String, Long>(); return augRefit; }
    public Map<String, Long> attackers() { if (attackers == null) attackers = new HashMap<String, Long>(); return attackers; }
    // endgame economy (CAEndgame), to timestamps: "marketId:industryId" built by it, "marketId:-industryId" taken down by it,
    // "marketId:replaced" its last replacement there; kept a year, so nothing it did is undone or redone sooner
    public Map<String, Long> endgameBuilt;
    public Map<String, Long> endgameBuilt() { if (endgameBuilt == null) endgameBuilt = new HashMap<String, Long>(); return endgameBuilt; }
    // Terraforming Made Easy structures surplus terraforming built ("marketId:structureId"); only these are taken down again
    // 2.38.0, plan steps with no way to them (CAPlan.track): "marketId:stepId" -> when first seen so; and special worlds
    // dropped for a year because their own steps can't be reached, "marketId:worldId" -> when
    public Map<String, Long> stepStuck, worldDead;
    // 2.38.0, CASiege: when hostiles only elsewhere in the system first made a colony's siege, marketId -> when
    public Map<String, Long> siegeSystem;
    // 2.39.0, storage logistics (CALogistics): loads on their way to the refit or sell hub
    public List<CATascStations.Order> tascOrders;   // construction crews on their way to found TASC stations (2.40.1)
    public List<CATascStations.Order> tascOrders() { if (tascOrders == null) tascOrders = new ArrayList<CATascStations.Order>(); return tascOrders; }
    public List<CAObjectives.Order> objectiveOrders;   // construction crews on their way to stable locations (2.40.0)
    public List<CAObjectives.Order> objectiveOrders() { if (objectiveOrders == null) objectiveOrders = new ArrayList<CAObjectives.Order>(); return objectiveOrders; }
    public List<CALogistics.Load> loads;
    public List<CALogistics.Load> loads() { if (loads == null) loads = new ArrayList<CALogistics.Load>(); return loads; }
    public Map<String, Long> siegeSystem() { if (siegeSystem == null) siegeSystem = new HashMap<String, Long>(); return siegeSystem; }
    public Map<String, Long> stepStuck() { if (stepStuck == null) stepStuck = new HashMap<String, Long>(); return stepStuck; }
    public Map<String, Long> worldDead() { if (worldDead == null) worldDead = new HashMap<String, Long>(); return worldDead; }

    public Set<String> surplusBuilt;
    public Set<String> surplusBuilt() { if (surplusBuilt == null) surplusBuilt = new HashSet<String>(); return surplusBuilt; }
    // an enabler hazard surplus terraforming added on the way to a deposit that needs it ("marketId:condition" -> the deposit),
    // and when: the plan doesn't clean it up until the deposit is in (or 540 days pass)
    public Map<String, String> enablers;
    public Map<String, Long> enablerAt;
    public Map<String, String> enablers() { if (enablers == null) enablers = new HashMap<String, String>(); return enablers; }
    public Map<String, Long> enablerAt() { if (enablerAt == null) enablerAt = new HashMap<String, Long>(); return enablerAt; }

    /** Whether surplus terraforming added this condition at the colony on the way to a deposit that isn't in yet. */
    public static boolean enabler(com.fs.starfarer.api.campaign.econ.MarketAPI m, String cond) {
        try { return get().enablers().containsKey(m.getId() + ":" + cond); } catch (Throwable t) { return false; }
    }

    // colonies whose Research Facility surplus research built (market ids); only these come down again
    public Set<String> surplusResearch;
    public Set<String> surplusResearch() { if (surplusResearch == null) surplusResearch = new HashSet<String>(); return surplusResearch; }

    /** Keys of log messages already written once (waits, unavailable edicts), so a reload doesn't repeat them. */
    public Set<String> noted() {
        if (noted == null || noted.size() > 2000) noted = new HashSet<String>();
        return noted;
    }

    /** Administrators hired remotely who are still travelling: person id to hire timestamp. */
    public Map<String, Long> adminTransit() {
        if (adminTransit == null) adminTransit = new HashMap<String, Long>();
        return adminTransit;
    }

    /** Ambassadors hired by the autopilot that are still travelling: person id to hire timestamp. */
    public Map<String, Long> ambTransit() {
        if (ambTransit == null) ambTransit = new HashMap<String, Long>();
        return ambTransit;
    }

    // Autopilot Storage per colony (CAStores, 2.37.0): market id to its cargo, and what's being shipped between them. The old
    // shared `storage` field stays so older saves load; CAStores.migrate empties it into the biggest colony's store once.
    public Map<String, com.fs.starfarer.api.campaign.CargoAPI> stores;
    public List<CAStores.Shipment> shipments;
    public Map<String, com.fs.starfarer.api.campaign.CargoAPI> stores() { if (stores == null) stores = new HashMap<String, com.fs.starfarer.api.campaign.CargoAPI>(); return stores; }
    public List<CAStores.Shipment> shipments() { if (shipments == null) shipments = new ArrayList<CAStores.Shipment>(); return shipments; }

    public static CAData get() {
        Map<String, Object> pd = Global.getSector().getPersistentData();
        Object o = pd.get(KEY);
        if (!(o instanceof CAData)) { o = new CAData(); pd.put(KEY, o); }
        CAData d = (CAData) o;
        if (d.colonies == null) d.colonies = new HashMap<String, Colony>();
        if (d.settings == null) { d.settings = new CASettings(); CASettingsFile.apply(d); }
        d.settings.fixNulls();
        if (d.minStability <= 0f) d.minStability = 5f;
        return d;
    }

    public static CASettings settings() { return get().settings; }

    public Colony colony(String marketId) {
        Colony c = colonies.get(marketId);
        if (c == null) { c = new Colony(); colonies.put(marketId, c); }
        if (c.profile == null) c.profile = Profile.BALANCED;
        if (c.mode == null) c.mode = Mode.ADVISOR;
        if (c.locks == null) c.locks = new HashSet<String>();
        return c;
    }

    public void clearPending(Colony c) {
        c.pendingId = null; c.pendingName = null; c.pendingCost = 0f; c.pendingUpgrade = false; c.pendingNotified = false;
    }
}
