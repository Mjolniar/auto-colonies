package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.3.15.0: Tier V (story mode). It stays hidden until every available Tier I to IV package is bought and running; then the
 * Story page shows it in red. Any three of its picks, free (no bandwidth), switched at will within two rules: a pick stays
 * LOCK_DAYS before it can be dropped, and a new pick comes online SPINUP_DAYS after it's picked. Every pick pauses while
 * Tier V isn't open (a lower tier falls short). State in the story state: "t5:<id>" = when it was picked (0 = not picked),
 * "t5drop:<id>" = when it was dropped (for the penalties), "t5print" = the Core printer's last core. Vanilla only.
 * Built so far: Instant construction, Iron stability, Core printer. The other four show as coming later and can't be picked.
 */
public final class CATierFive {
    private CATierFive() { }

    public static final String INSTANT = "instant5", FORGE = "forge5", ASCENDANT = "ascendant5", PRINTER = "printer5",
            FORGING = "forging5", ARMADA = "armada5", IRON = "iron5";
    public static final String[] IDS = {INSTANT, FORGE, ASCENDANT, PRINTER, FORGING, ARMADA, IRON};
    public static final String[] NAMES = {"Instant construction", "Domain forge", "Autarch ascendant", "Core printer", "Planet forging",
            "Autarch armada", "Iron stability"};
    /** One sentence each, for the tiles. */
    public static final String[] DESC = {
            "Every build and upgrade at a managed colony finishes the next day, and each one costs its price a second time.",
            "Auto colonies with Heavy Industry or Orbital Works work as if they had a Pristine Nanoforge.",
            "The Autarch governs every managed colony, each with two more industry slots and less upkeep.",
            "The Autarch makes one Alpha Core every 60 days, and you pay 250,000 credits for its materials.",
            "Once a year each managed colony loses one bad condition or gains a richer resource.",
            "A permanent 600-point fleet patrols your systems and answers any siege.",
            "Managed colonies never drop below stability 8, for 10,000 credits a colony each month."};
    static final String[] ART = {"i:orbitalworks", "s:pristine_nanoforge", "c:alpha_core", "c:alpha_core", "s:soil_nanites",
            "i:highcommand", "i:patrolhq"};
    /** The picks built so far; the rest show as coming later. */
    static final String[] BUILT = {INSTANT, PRINTER, IRON};
    public static final int MAX = 3;
    public static final float LOCK_DAYS = 90f, SPINUP_DAYS = 30f;
    static final float PRINT_DAYS = 60f, PRINT_COST = 250000f, IRON_FLOOR = 8f, IRON_UPKEEP = 10000f, IRON_DROP = 2f, IRON_DROP_DAYS = 30f;
    static final String IRON_MOD = "ca_t5_iron", IRON_DROP_MOD = "ca_t5_iron_drop";

    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    static boolean built(String id) {
        for (String b : BUILT) if (b.equals(id)) return true;
        return false;
    }

    /** Every available Tier I to IV package is bought, and all four tiers run. */
    public static boolean open() {
        try {
            if (!CASync.on() || CASync.dormant()) return false;
            int last = CASyncCatalog.TIERS.length - 1;
            if (CASync.runningTier() < last) return false;
            for (int t = 0; t <= last; t++) for (CASyncCatalog.Pack p : CASyncCatalog.inTier(t)) if (!CASync.ticked(p.id)) return false;
            return true;
        } catch (Throwable t) { return false; }
    }

    static long pickedAt(String id) { return CASync.stamp("t5:" + id); }

    public static boolean picked(String id) { return pickedAt(id) != 0L; }

    public static List<String> picks() {
        List<String> out = new ArrayList<String>();
        for (String id : IDS) if (picked(id)) out.add(id);
        return out;
    }

    /** The pick is open, picked and past its spin-up. */
    public static boolean online(String id) {
        return open() && picked(id) && CASync.daysSince(pickedAt(id)) >= SPINUP_DAYS;
    }

    /** Days until a picked pick comes online (0 when it is). */
    public static int spinupLeft(String id) {
        return picked(id) ? (int) Math.ceil(Math.max(0f, SPINUP_DAYS - CASync.daysSince(pickedAt(id)))) : 0;
    }

    /** Days a picked pick is still locked in (0 when it can be dropped). */
    public static int lockLeft(String id) {
        return picked(id) ? (int) Math.ceil(Math.max(0f, LOCK_DAYS - CASync.daysSince(pickedAt(id)))) : 0;
    }

    /** Picks or drops one. Null on success, else why not. */
    public static String toggle(String id) {
        int i = index(id);
        if (i < 0) return "That isn't a Tier V pick.";
        if (!open()) return "Tier V isn't open.";
        if (picked(id)) {
            int left = lockLeft(id);
            if (left > 0) return NAMES[i] + " is locked in for " + left + " more days.";
            CASync.stamp("t5:" + id, 0L);
            CASync.stamp("t5drop:" + id, CASync.now());
            if (PRINTER.equals(id)) CASync.stamp("t5print", 0L);
            CALog.did("Autopilot: " + NAMES[i] + " is off.", true);
            return null;
        }
        if (!built(id)) return NAMES[i] + " comes in a later version.";
        if (picks().size() >= MAX) return "Three picks are already in use.";
        CASync.stamp("t5:" + id, CASync.now());
        CALog.did("Autopilot: " + NAMES[i] + " picked. It comes online in " + (int) SPINUP_DAYS + " days.", true);
        return null;
    }

    // ---------------- daily effects ----------------

    /** Daily, from CASync.daily, with story mode on or off: the online picks' effects, and the penalties after a drop. */
    public static void daily() {
        CASettings s = CAData.settings();
        boolean iron = online(IRON);
        float dropAge = CASync.stamp("t5drop:" + IRON) != 0L ? CASync.daysSince(CASync.stamp("t5drop:" + IRON)) : Float.MAX_VALUE;
        boolean newMonth = newMonth();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            boolean managed = CASync.on() && CASync.manages(m);
            try { if (online(INSTANT) && managed) instant(m, s); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "instant construction failed at " + m.getName(), t); }
            try { ironStability(m, iron && managed, dropAge < IRON_DROP_DAYS); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "iron stability failed at " + m.getName(), t); }
        }
        if (iron && newMonth) {
            int n = 0;
            for (MarketAPI m : Misc.getPlayerMarkets(false)) if (CASync.manages(m)) n++;
            if (n > 0) CABudget.pay(s, null, n * IRON_UPKEEP, 0f, 0f);
        }
        try { if (online(PRINTER)) printer(s); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "core printer failed", t); }
    }

    /** True once a month (the game's month changed since the last look). */
    static boolean newMonth() {
        try {
            int month = Global.getSector().getClock().getMonth(), last = CASync.num("t5month");
            if (month == last) return false;
            CASync.num("t5month", month);
            return last != 0;
        } catch (Throwable t) { return false; }
    }

    /** Instant construction: a building or upgrading industry finishes now, for its price again, above the credit reserve. */
    static void instant(MarketAPI m, CASettings s) {
        for (Industry ind : new ArrayList<Industry>(m.getIndustries())) {
            if (!ind.isBuilding() || ind.isDisrupted()) continue;
            float cost = ind.getBuildCost();
            if (CABuild.credits() - cost < s.reserve) continue;
            CABudget.pay(s, m.getId(), cost, 0f, 0f);
            ind.finishBuildingOrUpgrading();
            CALog.did(m.getName() + ": " + ind.getCurrentName() + " finished at once for " + Misc.getDGSCredits(cost) + " (Instant construction).", true);
        }
    }

    /** Iron stability: tops stability up to IRON_FLOOR; after a drop, -IRON_DROP for IRON_DROP_DAYS. Clears both otherwise. */
    static void ironStability(MarketAPI m, boolean on, boolean dropped) {
        m.getStability().unmodifyFlat(IRON_MOD);
        if (on) {
            float now = m.getStabilityValue();
            if (now < IRON_FLOOR) m.getStability().modifyFlat(IRON_MOD, IRON_FLOOR - now, "Iron stability (Autarch AI Core)");
        }
        if (dropped && !on) m.getStability().modifyFlat(IRON_DROP_MOD, -IRON_DROP, "Iron stability withdrawn");
        else m.getStability().unmodifyFlat(IRON_DROP_MOD);
    }

    /** Core printer: one Alpha Core every PRINT_DAYS, paid above the credit reserve. */
    static void printer(CASettings s) {
        long last = CASync.stamp("t5print");
        if (last == 0L) { CASync.stamp("t5print", CASync.now()); return; }
        if (CASync.daysSince(last) < PRINT_DAYS) return;
        if (CABuild.credits() - PRINT_COST < s.reserve) return;   // waits for the money
        CABudget.pay(s, null, PRINT_COST, 0f, 0f);
        Global.getSector().getPlayerFleet().getCargo().addCommodity(CASyncCatalog.ALPHA, 1);
        CASync.stamp("t5print", CASync.now());
        CALog.did("Autopilot: the Core printer made an Alpha Core for " + Misc.getDGSCredits(PRINT_COST) + ".", true);
    }
}
