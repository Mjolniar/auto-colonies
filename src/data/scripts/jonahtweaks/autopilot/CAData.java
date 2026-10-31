package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import java.util.HashMap;
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
        // Coronal Hypershunt (AotD): the range Autopilot last set, and whether you set it yourself since
        public Integer hsRange;
        public boolean hsManual;
        // Pluto / Nidavelir (AotD): the settings Autopilot last set, whether you've changed them since, and when Nexus manpower last backed off
        public Map<String, Integer> megaSet;
        public boolean megaManual;
        public long megaHold;

        public Map<String, Integer> megaSet() { if (megaSet == null) megaSet = new HashMap<String, Integer>(); return megaSet; }

        public Set<String> augAuto() { if (augAuto == null) augAuto = new HashSet<String>(); return augAuto; }
        public Set<String> augSkip() { if (augSkip == null) augSkip = new HashSet<String>(); return augSkip; }
    }

    public Map<String, Colony> colonies = new HashMap<String, Colony>();
    public float minStability = 5f;
    public CASettings settings;
    public com.fs.starfarer.api.campaign.CargoAPI storage;
    public Map<String, Long> ambTransit;
    public Map<String, Long> adminTransit;
    public Set<String> noted;
    // Random Assortment of Things settlement proposal waiting for approval: kind "found" (planet id) or "facility" (facility id)
    public String settlePendingKind, settlePendingId, settlePendingName;
    public float settlePendingCost;

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

    /** The shared Autopilot Storage cargo. */
    public com.fs.starfarer.api.campaign.CargoAPI storage() {
        if (storage == null) storage = Global.getFactory().createCargo(true);
        if (storage.getMothballedShips() == null) storage.initMothballedShips("player");   // other mods iterate every submarket's ship list
        return storage;
    }

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
