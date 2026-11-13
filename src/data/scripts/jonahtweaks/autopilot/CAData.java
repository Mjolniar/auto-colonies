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
        /** 0.3.2.0: the industry a pending make-room swap takes down on Approve (CAFloors.swap); null for every other proposal and in older saves. */
        public String pendingReplace;
        public boolean pendingUpgrade;
        public float pendingCost;
        public boolean pendingNotified;
        /** 0.3.3.0: the proposal's reason and first effect line, its money a month, and when it was proposed (older saves: null, 0). */
        public String pendingWhy;
        public float pendingMonthly;
        public long pendingSince;
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
        // 0.2.38.0: when it started (0: before 0.2.38.0, set on its next pass) and when it last found nothing that pays (0: it found something)
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
    /** 0.2.44.0 market model (CAMarket): buildings under construction, "market|building|replaced" -> each good's supply there once done, and since when. */
    public Map<String, Map<String, Integer>> marketPending;
    public Map<String, Float> marketPendingSince;
    /** 0.2.44.1: "market|building" -> what a building being upgraded becomes (AotD upgrades have several), for CABuild.usedSlots. */
    public Map<String, String> upgradeTargets;
    public Map<String, String> upgradeTargets() { if (upgradeTargets == null) upgradeTargets = new HashMap<String, String>(); return upgradeTargets; }
    public Map<String, Map<String, Integer>> marketPending() { if (marketPending == null) marketPending = new HashMap<String, Map<String, Integer>>(); return marketPending; }
    public Map<String, Float> marketPendingSince() { if (marketPendingSince == null) marketPendingSince = new HashMap<String, Float>(); return marketPendingSince; }
    /**
     * 0.3.0.0 (A3, 07 L3): retired; read CASettings.minStability. Kept so saves from before 0.3.0 load and bring theirs over
     * (CAData.get), and kept equal to the setting so anything still reading it gets the same number.
     */
    @Deprecated
    public float minStability = CATuning.MIN_STABILITY;
    public boolean buildoutOffered;   // the first-colony fast buildout dialog has been shown (or doesn't apply to this save)
    /** 0.3.3.0: when the approval summary last opened (0: never, also older saves). */
    public long approvalLast;
    public CASettings settings;
    public com.fs.starfarer.api.campaign.CargoAPI storage;
    public Map<String, Long> ambTransit;
    public Map<String, Long> adminTransit;
    // 0.2.41.2: when the colony limit first needed an administrator and none was on offer anywhere (0: not waiting); a recruiter
    // finds one after CAItems.RECRUIT_DAYS
    public long adminNeedSince;
    public Set<String> noted;
    // Random Assortment of Things settlement proposal waiting for approval: kind "found" (planet id) or "facility" (facility id)
    public String settlePendingKind, settlePendingId, settlePendingName;
    public float settlePendingCost;
    // a Random Assortment of Things settlement expedition on its way (0.2.37.0): the planet, when it left, its days, and what it cost
    public String settleExpeditionPlanet;
    public long settleExpeditionAt;
    public float settleExpeditionDays, settleExpeditionCost;
    // Nexerelin agents Autopilot hired (person ids); only these are given orders
    public Set<String> spyAgents;
    public Set<String> spyAgents() { if (spyAgents == null) spyAgents = new HashSet<String>(); return spyAgents; }
    // their roles (0.2.37.0: person id to security, relations or offense) and agents being recruited (role to when it began)
    public Map<String, String> spyRoles;
    public Map<String, Long> spyRecruits;
    public Map<String, String> spyRoles() { if (spyRoles == null) spyRoles = new HashMap<String, String>(); return spyRoles; }
    public Map<String, Long> spyRecruits() { if (spyRecruits == null) spyRecruits = new HashMap<String, Long>(); return spyRecruits; }
    // colonies Autopilot made autonomous in an emergency (Nexerelin): market id to the timestamp; only these are taken back
    public Map<String, Long> autonomous;
    public long lastAutonomyChange;
    public long researchWaitSince;   // when Auto research began holding databanks for a plan tech short of them (0: not waiting)
    public String researchStaging;   // 0.2.42.4: the tech whose databanks are on their way to the facility; nothing else starts meanwhile
    public Map<String, Long> autonomous() { if (autonomous == null) autonomous = new HashMap<String, Long>(); return autonomous; }
    // war fleets (CAWar): markets that sent raids, invasions or blockades at your colonies, to when they were last seen doing it
    public Map<String, Long> attackers;
    // station augment refit crews booked (CAAugments, 0.2.37.0): market id to when
    public Map<String, Long> augRefit;
    public Map<String, Long> augRefit() { if (augRefit == null) augRefit = new HashMap<String, Long>(); return augRefit; }
    public Map<String, Long> attackers() { if (attackers == null) attackers = new HashMap<String, Long>(); return attackers; }
    // endgame economy (CAEndgame), to timestamps: "marketId:industryId" built by it, "marketId:-industryId" taken down by it;
    // kept a year, so nothing it did is undone or redone sooner. 0.3.0.0: the other features' stamps live in overLimitAt, pruneAt and swapAt
    public Map<String, Long> endgameBuilt;
    public Map<String, Long> endgameBuilt() { if (endgameBuilt == null) endgameBuilt = new HashMap<String, Long>(); return endgameBuilt; }
    // Terraforming Made Easy structures surplus terraforming built ("marketId:structureId"); only these are taken down again
    // 0.2.38.0, plan steps with no way to them (CAPlan.track): "marketId:stepId" -> when first seen so; and special worlds
    // dropped for a year because their own steps can't be reached, "marketId:worldId" -> when
    public Map<String, Long> stepStuck, worldDead;
    // 0.2.38.0, CASiege: when hostiles only elsewhere in the system first made a colony's siege, marketId -> when
    public Map<String, Long> siegeSystem;
    // 0.2.39.0, storage logistics (CALogistics): loads on their way to the refit or sell hub
    public List<CATascStations.Order> tascOrders;   // construction crews on their way to found TASC stations (0.2.40.1)
    public List<CATascStations.Order> tascOrders() { if (tascOrders == null) tascOrders = new ArrayList<CATascStations.Order>(); return tascOrders; }
    public List<CAObjectives.Order> objectiveOrders;   // construction crews on their way to stable locations (0.2.40.0)
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

    /** Pre-0.3.0 once-keys only: CALog moves them into notedAt the first time and sets this to null. Nothing calls this any more (CALog.once/noted/forget). */
    public Set<String> noted() {
        if (noted == null) noted = new HashSet<String>();
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

    // Autopilot Storage per colony (CAStores, 0.2.37.0): market id to its cargo, and what's being shipped between them. The old
    // shared `storage` field stays so older saves load; CAStores.migrate empties it into the biggest colony's store once.
    public Map<String, com.fs.starfarer.api.campaign.CargoAPI> stores;
    public List<CAStores.Shipment> shipments;
    public Map<String, com.fs.starfarer.api.campaign.CargoAPI> stores() { if (stores == null) stores = new HashMap<String, com.fs.starfarer.api.campaign.CargoAPI>(); return stores; }
    public List<CAStores.Shipment> shipments() { if (shipments == null) shipments = new ArrayList<CAStores.Shipment>(); return shipments; }

    /** 0.3.1.0: the Log page's Thinking list (CALog.think), newest first, dated; older saves load it null. */
    public List<String> thinking;
    public List<String> thinking() { if (thinking == null) thinking = new ArrayList<String>(); return thinking; }
    /** 0.3.1.0: the Log page shows the Thinking list (its Show and Hide button); collapsed by default. */
    public boolean thinkingShown;

    // ---------------- 0.3.0.0 (A0): every new saved field of the 0.3.0 workstreams, added at once (plans/cm-3.0 FIELDS.md) ----------------
    // Ids, numbers and timestamps only (no MarketAPI, PlanetAPI or Nexerelin types); older saves load them null or 0, so the
    // lists, maps and sets have lazy getters. Every class stored here needs an alias in CAXStream.
    /** Saved-data layout version (CAMigrate.SCHEMA): new saves start current, every save from before 0.3.0 loads 0. */
    public int schema = CAMigrate.SCHEMA;
    /** When CAMigrate moved this save to 0.3.0 (0: never, the save started on 0.3.0); the Settings page's what's-new box reads it. */
    public long migratedAt;
    /** A: log keys with when they were noted, so a "once" message can expire instead of the whole set being wiped (07 M1). */
    public Map<String, Long> notedAt;
    /** B: removals the planner prices in for a year, so nothing is built, removed and rebuilt (CATombstone). */
    public List<CATombstone.Mark> tombstones;
    /** B: "marketId:industryId" -> credits paid for what stands there (sunk-cost memory for the log and teardown loss). */
    public Map<String, Float> sunk;
    /** C: the colonies' net income of the last months (newest last), for the 3-month median. */
    public List<Float> colonyNetHistory;
    /** C: months in a row with most Auto colonies idle and little of the budget spent. */
    public int idleMonths;
    /**
     * C: the money tier the empire is in (CARich.T0-T4), saved so the 60% exit line (C2 hysteresis) still holds after a
     * load; a static cache is cleared on every load (CAReset), which would drop a tier each reload between exit and entry.
     * Older saves load 0 (Tight), the safe start (0.3.0.0).
     */
    public int richTier;
    /** C: when richTier last changed (0: never), for the tier-change log line. */
    public long richTierSince;
    /** C: rung name -> credits it spent this month. */
    public Map<String, Float> rungSpent;
    /** C: custom production orders the shipyard placed, and goods bought for colonies' Local Resources. */
    public List<CAShipyard.Order> shipyardOrders;
    public List<CAStockpile.Buy> stockpiles;
    /** D: colony expeditions under way; planet or system id -> skip until (Long.MAX_VALUE: never); planets founded. */
    public List<CAExpansion.Order> expandOrders;
    public Map<String, Long> expandSkip;
    public Set<String> expandFounded;
    /** D: when the last expedition left, and the proposal waiting for approval under Ask first. */
    public long expandLast;
    public String expandPendingId, expandPendingName;
    public float expandPendingCost;
    /** E: faction id -> when CM declared war; "kind:marketId" -> when an operation was launched; conquered market id -> when. */
    public Map<String, Long> warDeclared, warOps, conquered;
    /** E: faction ids whose declaration or bombardment you confirmed this game; when you last stood down. */
    public Set<String> warAuthorized;
    public long warStandDown;
    /**
     * A (0.3.0.0 fix, 07 L7): endgameBuilt's other users get their own maps. An older save's keys move over by suffix the first
     * time each getter runs (CATidy.split), so no reader sees an empty map first. overLimitAt: CAOverLimit's ":zerostab",
     * ":zerosince", ":shed", ":shed:<id>", ":overlimit"; pruneAt: CAPrune's ":useless:<id>", ":pruned"; swapAt: ":replaced" (the
     * last make-room swap or forced takedown at a colony; CAEndgame, CACapital, CAOverLimit). endgameBuilt keeps CAEndgame's own.
     */
    public Map<String, Long> overLimitAt, pruneAt, swapAt;
    /** A (0.3.0.0, research 01 section 5): this month's Auto Colonies spending per colony, "marketId|spent", "|fees", "|upkeep" (CALog.spent); the monthly colony lines clear it. */
    public Map<String, Float> monthLedger;

    public Map<String, Long> notedAt() { if (notedAt == null) notedAt = new HashMap<String, Long>(); return notedAt; }
    public List<CATombstone.Mark> tombstones() { if (tombstones == null) tombstones = new ArrayList<CATombstone.Mark>(); return tombstones; }
    public Map<String, Float> sunk() { if (sunk == null) sunk = new HashMap<String, Float>(); return sunk; }
    public List<Float> colonyNetHistory() { if (colonyNetHistory == null) colonyNetHistory = new ArrayList<Float>(); return colonyNetHistory; }
    public Map<String, Float> rungSpent() { if (rungSpent == null) rungSpent = new HashMap<String, Float>(); return rungSpent; }
    public List<CAShipyard.Order> shipyardOrders() { if (shipyardOrders == null) shipyardOrders = new ArrayList<CAShipyard.Order>(); return shipyardOrders; }
    public List<CAStockpile.Buy> stockpiles() { if (stockpiles == null) stockpiles = new ArrayList<CAStockpile.Buy>(); return stockpiles; }
    public List<CAExpansion.Order> expandOrders() { if (expandOrders == null) expandOrders = new ArrayList<CAExpansion.Order>(); return expandOrders; }
    public Map<String, Long> expandSkip() { if (expandSkip == null) expandSkip = new HashMap<String, Long>(); return expandSkip; }
    public Set<String> expandFounded() { if (expandFounded == null) expandFounded = new HashSet<String>(); return expandFounded; }
    public Map<String, Long> warDeclared() { if (warDeclared == null) warDeclared = new HashMap<String, Long>(); return warDeclared; }
    public Map<String, Long> warOps() { if (warOps == null) warOps = new HashMap<String, Long>(); return warOps; }
    public Map<String, Long> conquered() { if (conquered == null) conquered = new HashMap<String, Long>(); return conquered; }
    public Set<String> warAuthorized() { if (warAuthorized == null) warAuthorized = new HashSet<String>(); return warAuthorized; }
    public Map<String, Long> overLimitAt() { if (overLimitAt == null) overLimitAt = CATidy.split(endgameBuilt, CATidy.OVERLIMIT); return overLimitAt; }
    public Map<String, Long> pruneAt() { if (pruneAt == null) pruneAt = CATidy.split(endgameBuilt, CATidy.PRUNE); return pruneAt; }
    public Map<String, Long> swapAt() { if (swapAt == null) swapAt = CATidy.split(endgameBuilt, CATidy.SWAP); return swapAt; }
    public Map<String, Float> monthLedger() { if (monthLedger == null) monthLedger = new HashMap<String, Float>(); return monthLedger; }

    public static CAData get() {
        Map<String, Object> pd = Global.getSector().getPersistentData();
        Object o = pd.get(KEY);
        if (!(o instanceof CAData)) { o = new CAData(); pd.put(KEY, o); }
        CAData d = (CAData) o;
        if (d.colonies == null) d.colonies = new HashMap<String, Colony>();
        if (d.settings == null) {
            d.settings = new CASettings();
            CASettingsFile.apply(d);
            CAMigrate.stances(d.settings);   // 0.3.0.0: faction priorities from colony_autopilot.json as stances
            CAModeTable.apply(d.settings, d.settings.empire());   // 0.3.3.0: a new save starts on the Advisor column
        }
        // 0.3.0.0 (A3, 07 L3): minStability is a setting now; a save from before 0.3.0 loads it 0 there and brings over the one
        // CAData kept (colony_autopilot.json's when the save started). fixNulls gives 0 the default, as CAData's 0 got before
        if (!(d.settings.minStability > 0f) && d.minStability > 0f) d.settings.minStability = d.minStability;
        d.settings.fixNulls();
        d.minStability = d.settings.minStability;
        // 0.3.0.0: a save from before 0.3.0 is moved forward once (CAMigrate never throws; a failed step still loads the save)
        if (d.schema < CAMigrate.SCHEMA || d.settings.settingsVersion < CAMigrate.SCHEMA) CAMigrate.run(d, d.settings);
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
        c.pendingReplace = null;   // 0.3.2.0
        c.pendingWhy = null; c.pendingMonthly = 0f; c.pendingSince = 0L;   // 0.3.3.0
    }

    /** 0.3.3.0: sets a colony's mode; a change clears its waiting proposal (the planner proposes again for the new mode). */
    public void setMode(Colony c, Mode mode) {
        if (c == null || mode == null) return;
        if (c.mode != mode) clearPending(c);
        c.mode = mode;
    }
}
