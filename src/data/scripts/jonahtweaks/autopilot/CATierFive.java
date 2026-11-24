package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.3.15.0: Tier V (story mode). It stays hidden until every available Tier I to IV package is bought and running; then the
 * Story page shows it in red. Any three of its picks, free (no bandwidth), switched at will within two rules: a pick stays
 * LOCK_DAYS before it can be dropped, and a new pick comes online SPINUP_DAYS after it's picked. Every pick pauses while
 * Tier V isn't open (a lower tier falls short). State in the story state: "t5:<id>" = when it was picked (0 = not picked),
 * "t5drop:<id>" = when it was dropped (for the penalties), "t5print" = the Core printer's last core. Vanilla only.
 * Built: Instant construction, Iron stability, Core printer; 0.3.16.0 Domain forge and Autarch armada; 0.3.16.1 Autarch
 * ascendant and Planet forging.
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
            "Managed colonies with Heavy Industry or Orbital Works get a Pristine Nanoforge's effect for 25,000 credits a month each.",
            "The Autarch governs every managed colony, each with two more industry slots and 40% less upkeep.",
            "The Autarch makes one Alpha Core every 60 days, and you pay 250,000 credits for its materials.",
            "Once a year each managed colony loses a bad condition or gains a richer resource for 1,500,000 credits.",
            "A permanent 600-point fleet patrols your systems and answers sieges for 200,000 credits a month.",
            "Managed colonies never drop below stability 8, for 10,000 credits a colony each month."};
    static final String[] ART = {"i:orbitalworks", "s:pristine_nanoforge", "c:alpha_core", "c:alpha_core", "s:soil_nanites",
            "i:highcommand", "i:patrolhq"};
    /** The picks built so far; the rest show as coming later. */
    static final String[] BUILT = {INSTANT, PRINTER, IRON, FORGE, ARMADA, ASCENDANT, FORGING};
    public static final int MAX = 3;
    public static final float LOCK_DAYS = 90f, SPINUP_DAYS = 30f;
    static final float PRINT_DAYS = 60f, PRINT_COST = 250000f, IRON_FLOOR = 8f, IRON_UPKEEP = 10000f, IRON_DROP = 2f, IRON_DROP_DAYS = 30f;
    static final String IRON_MOD = "ca_t5_iron", IRON_DROP_MOD = "ca_t5_iron_drop";
    /** 0.3.16.0: Domain forge: the monthly price per colony it works at, and how long it fades after a drop. */
    static final float FORGE_UPKEEP = 25000f, FORGE_FADE_DAYS = 30f;
    static final String FORGE_MOD = "ca_t5_forge", FORGE_DESC = "Domain forge (Autarch AI Core)";
    /** 0.3.16.0: Autarch armada: its size, monthly price, days before a lost one is raised again, and each duty's length. */
    static final float ARMADA_FP = 600f, ARMADA_UPKEEP = 200000f, ARMADA_REBUILD_DAYS = 30f, ARMADA_DUTY_DAYS = 30f;
    static final String ARMADA_MEM = "$ca_t5armada", ARMADA_NAME = "Autarch Armada";
    /** 0.3.16.1: Autarch ascendant: industry slots, upkeep multiplier, and the stability drop after it's dropped. */
    static final float ASC_SLOTS = 2f, ASC_UPKEEP = 0.6f, ASC_DROP = 3f, ASC_DROP_DAYS = 60f;
    static final String ASC_MOD = "ca_t5_ascendant", ASC_DROP_MOD = "ca_t5_ascendant_drop", ASC_DESC = "Autarch ascendant (Autarch AI Core)";
    /** 0.3.16.1: Planet forging: days between rounds and the price of each change. */
    static final float FORGING_DAYS = 365f, FORGING_COST = 1500000f;

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

    /** 0.3.16.1: picked and not online yet: it can be cancelled at once, with no penalty; picking it again starts the spin-up over. */
    public static boolean spinning(String id) { return spinupLeft(id) > 0; }

    /** 0.3.16.1: a picked pick can be dropped: still spinning up, or past its lock-in. */
    public static boolean canDrop(String id) { return picked(id) && (spinning(id) || lockLeft(id) == 0); }

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
            boolean early = spinning(id);   // 0.3.16.1: cancelled before it came online: no lock-in, no drop penalties
            int left = lockLeft(id);
            if (!early && left > 0) return NAMES[i] + " is locked in for " + left + " more days.";
            CASync.stamp("t5:" + id, 0L);
            if (!early) CASync.stamp("t5drop:" + id, CASync.now());
            if (PRINTER.equals(id)) CASync.stamp("t5print", 0L);
            CALog.did("Autopilot: " + NAMES[i] + (early ? " cancelled before it came online." : " is off."), true);
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
        // 0.3.16.0: Domain forge, full while online, fading over FORGE_FADE_DAYS after a drop
        boolean forgeOn = online(FORGE);
        float forgeDrop = dropAge(FORGE), fade = forgeOn ? 1f : forgeDrop < FORGE_FADE_DAYS ? 1f - forgeDrop / FORGE_FADE_DAYS : 0f;
        int forged = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            boolean managed = CASync.on() && CASync.manages(m);
            try { if (forge(m, managed ? fade : 0f) && forgeOn) forged++; }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "domain forge failed at " + m.getName(), t); }
        }
        if (forgeOn && newMonth && forged > 0) CABudget.pay(s, null, forged * FORGE_UPKEEP, 0f, 0f);
        try { armada(s, newMonth); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "autarch armada failed", t); }
        // 0.3.16.1: Autarch ascendant at every managed colony; -ASC_DROP stability for ASC_DROP_DAYS after a drop
        boolean asc = online(ASCENDANT), ascDropped = dropAge(ASCENDANT) < ASC_DROP_DAYS;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            boolean managed = CASync.on() && CASync.manages(m);
            try { ascendant(m, asc && managed, ascDropped && managed); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "autarch ascendant failed at " + m.getName(), t); }
        }
        try { forging(s); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "planet forging failed", t); }
        try { if (online(PRINTER)) printer(s); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "core printer failed", t); }
    }

    /** Days since the pick was dropped, or the largest float when it never was. */
    static float dropAge(String id) {
        long at = CASync.stamp("t5drop:" + id);
        return at != 0L ? CASync.daysSince(at) : Float.MAX_VALUE;
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
            // 0.3.16.2: the population building can read as building (mods); finishing it did nothing and repeated every day
            if (ind.getSpec() != null && ind.getSpec().hasTag(Industries.TAG_POPULATION)) continue;
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

    // ---------------- 0.3.16.0: Domain forge ----------------

    /** The colony's heavy industry (Heavy Industry, Orbital Works, or a mod's with the heavy industry tag), or null. */
    static Industry heavy(MarketAPI m) {
        for (Industry i : m.getIndustries()) {
            if (i.getSpec() == null) continue;
            if (Industries.HEAVYINDUSTRY.equals(i.getId()) || Industries.ORBITALWORKS.equals(i.getId()) || i.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY)) return i;
        }
        return null;
    }

    /** Pure: the ship quality a Pristine Nanoforge adds over the nanoforge installed (none, corrupted or pristine), times the fade. */
    static float forgeQuality(String item, float fade) {
        float have = Items.PRISTINE_NANOFORGE.equals(item) ? ItemEffectsRepo.PRISTINE_NANOFORGE_QUALITY_BONUS
                : Items.CORRUPTED_NANOFORGE.equals(item) ? ItemEffectsRepo.CORRUPTED_NANOFORGE_QUALITY_BONUS : 0f;
        return Math.max(0f, ItemEffectsRepo.PRISTINE_NANOFORGE_QUALITY_BONUS - have) * Math.max(0f, Math.min(1f, fade));
    }

    /** Pure: the same for output. */
    static int forgeOutput(String item, float fade) {
        int have = Items.PRISTINE_NANOFORGE.equals(item) ? ItemEffectsRepo.PRISTINE_NANOFORGE_PROD
                : Items.CORRUPTED_NANOFORGE.equals(item) ? ItemEffectsRepo.CORRUPTED_NANOFORGE_PROD : 0;
        return Math.round(Math.max(0, ItemEffectsRepo.PRISTINE_NANOFORGE_PROD - have) * Math.max(0f, Math.min(1f, fade)));
    }

    /**
     * Gives the colony's heavy industry a Pristine Nanoforge's output and the colony its ship quality, less what an installed
     * nanoforge already gives, times the fade (0 takes it off). The output is the industry's transient bonus from others,
     * so it's put back every day. True when it works here.
     */
    static boolean forge(MarketAPI m, float fade) {
        Industry h = fade > 0f ? heavy(m) : null;
        for (Industry i : m.getIndustries()) if (i != h) i.getSupplyBonusFromOther().unmodifyFlat(FORGE_MOD);
        StatBonus quality = m.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD);
        if (h == null) { quality.unmodifyFlat(FORGE_MOD); return false; }
        String item = h.getSpecialItem() == null ? null : h.getSpecialItem().getId();
        float q = forgeQuality(item, fade);
        int out = forgeOutput(item, fade);
        if (out > 0) h.getSupplyBonusFromOther().modifyFlat(FORGE_MOD, out, FORGE_DESC);
        else h.getSupplyBonusFromOther().unmodifyFlat(FORGE_MOD);
        if (q > 0f) quality.modifyFlat(FORGE_MOD, q, FORGE_DESC);
        else quality.unmodifyFlat(FORGE_MOD);
        return true;
    }

    // ---------------- 0.3.16.0: Autarch armada ----------------

    /** The armada on duty (the fleet with ARMADA_MEM), or null. */
    static CampaignFleetAPI armadaFleet() {
        for (LocationAPI l : Global.getSector().getAllLocations())
            for (CampaignFleetAPI f : l.getFleets()) if (f.isAlive() && f.getMemoryWithoutUpdate().getBoolean(ARMADA_MEM)) return f;
        return null;
    }

    /** Where the armada is raised: your largest managed colony with a working spaceport. */
    static MarketAPI armadaHome() {
        MarketAPI best = null;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m.getPrimaryEntity() == null || !CASync.manages(m) || !CAOwnWar.spaceport(m)) continue;
            if (best == null || m.getSize() > best.getSize()) best = m;
        }
        return best;
    }

    static CampaignFleetAPI raiseArmada(MarketAPI home) {
        FleetParamsV3 p = new FleetParamsV3(home, home.getLocationInHyperspace(), Factions.PLAYER, null, FleetTypes.PATROL_LARGE,
                ARMADA_FP, 0f, 0f, 0f, 0f, 0f, 0f);
        p.ignoreMarketFleetSizeMult = true;
        CampaignFleetAPI f = FleetFactoryV3.createFleet(p);
        if (f == null || f.getFleetData() == null || f.getFleetData().getNumMembers() <= 0) return null;
        f.setName(ARMADA_NAME);
        f.getMemoryWithoutUpdate().set(ARMADA_MEM, true);
        SectorEntityToken at = home.getPrimaryEntity();
        at.getContainingLocation().addEntity(f);
        f.setLocation(at.getLocation().x, at.getLocation().y);
        return f;
    }

    /** Sends the armada to a colony: there, then DEFEND_LOCATION for a duty. */
    static void order(CampaignFleetAPI f, MarketAPI m, String what) {
        SectorEntityToken dest = m.getPrimaryEntity();
        f.clearAssignments();
        if (f.getContainingLocation() != dest.getContainingLocation())
            f.addAssignment(FleetAssignment.GO_TO_LOCATION, dest, 1000f, "travelling to " + m.getName());
        f.addAssignment(FleetAssignment.DEFEND_LOCATION, dest, ARMADA_DUTY_DAYS, what + m.getName());
        CASync.text("t5armadaAt", m.getId());
    }

    /** The armada leaves: home to the nearest colony of yours and despawns there (or at once without one). */
    static void dismiss(CampaignFleetAPI f) {
        f.getMemoryWithoutUpdate().unset(ARMADA_MEM);
        f.clearAssignments();
        MarketAPI home = null;
        float best = Float.MAX_VALUE;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m.getPrimaryEntity() == null) continue;
            float d = Misc.getDistanceLY(f, m.getPrimaryEntity());
            if (d < best) { best = d; home = m; }
        }
        if (home == null) { f.despawn(); return; }
        f.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, home.getPrimaryEntity(), 1000f, "returning to " + home.getName());
    }

    /**
     * Daily: while online the armada is kept (raised at armadaHome, again ARMADA_REBUILD_DAYS after it's lost), sent to a
     * colony that hostiles or a raid really target (CAOwnWar.realThreat), and otherwise moved to the next system with a
     * managed colony after each duty. Paid monthly. Offline (dropped, or Tier V paused), it leaves.
     */
    static void armada(CASettings s, boolean newMonth) {
        CampaignFleetAPI f = armadaFleet();
        if (!online(ARMADA)) {
            if (f != null) { dismiss(f); CALog.did("Autopilot: the " + ARMADA_NAME + " is leaving.", true); }
            CASync.flag("t5armadaUp", false);
            CASync.text("t5armadaAt", null);
            return;
        }
        if (f == null && CASync.flag("t5armadaUp")) {   // it was out and is gone: lost in battle
            CASync.flag("t5armadaUp", false);
            CASync.stamp("t5armadaLost", CASync.now());
            CALog.did("Autopilot: the " + ARMADA_NAME + " was lost. A new one is raised in " + (int) ARMADA_REBUILD_DAYS + " days.", true);
        }
        if (f == null) {
            long lost = CASync.stamp("t5armadaLost");
            if (lost != 0L && CASync.daysSince(lost) < ARMADA_REBUILD_DAYS) return;
            MarketAPI home = armadaHome();
            if (home == null) return;   // no managed colony with a spaceport: it waits
            f = raiseArmada(home);
            if (f == null) return;
            CASync.flag("t5armadaUp", true);
            CASync.stamp("t5armadaLost", 0L);
            CASync.text("t5armadaAt", null);
            CALog.did("Autopilot: the " + ARMADA_NAME + " is raised at " + home.getName() + ".", true);
        }
        if (newMonth) CABudget.pay(s, null, ARMADA_UPKEEP, 0f, 0f);
        List<MarketAPI> managed = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (m.getPrimaryEntity() != null && CASync.manages(m)) managed.add(m);
        if (managed.isEmpty()) return;
        String at = CASync.text("t5armadaAt");
        for (MarketAPI m : managed) {
            if (!CAOwnWar.realThreat(m)) continue;
            if (!m.getId().equals(at)) {
                order(f, m, "answering the siege at ");
                CALog.did("Autopilot: the " + ARMADA_NAME + " is answering the siege at " + m.getName() + ".", true);
            }
            return;
        }
        if (f.getCurrentAssignment() != null) return;
        // the next system with a managed colony, in turn
        List<MarketAPI> stops = new ArrayList<MarketAPI>();
        java.util.Set<LocationAPI> seen = new java.util.HashSet<LocationAPI>();
        for (MarketAPI m : managed) if (seen.add(m.getPrimaryEntity().getContainingLocation())) stops.add(m);
        int next = Math.floorMod(CASync.num("t5armadaNext"), stops.size());
        CASync.num("t5armadaNext", next + 1);
        order(f, stops.get(next), "patrolling ");
    }

    // ---------------- 0.3.16.1: Autarch ascendant ----------------

    /** Seats for the Autarch administrator: every managed colony while Autarch ascendant is online, else the bought ones. */
    public static int seats(int bought) { return bought > 0 && online(ASCENDANT) ? Math.max(bought, 10000) : bought; }

    /** Two more industry slots and 40% less upkeep while on; the stability drop after a drop. Clears both otherwise. */
    static void ascendant(MarketAPI m, boolean on, boolean dropped) {
        StatBonus slots = m.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES);
        if (on) {
            slots.modifyFlat(ASC_MOD, ASC_SLOTS, ASC_DESC);
            m.getUpkeepMult().modifyMult(ASC_MOD, ASC_UPKEEP, ASC_DESC);
        } else {
            slots.unmodifyFlat(ASC_MOD);
            m.getUpkeepMult().unmodifyMult(ASC_MOD);
        }
        if (dropped && !on) m.getStability().modifyFlat(ASC_DROP_MOD, -ASC_DROP, "Autarch ascendant withdrawn");
        else m.getStability().unmodifyFlat(ASC_DROP_MOD);
    }

    // ---------------- 0.3.16.1: Planet forging ----------------

    /** Bad conditions, worst first, each to what it becomes (null: gone). */
    static final String[][] BAD = {
            {"very_hot", "hot"}, {"very_cold", "cold"}, {"extreme_tectonic_activity", "tectonic_activity"}, {"toxic_atmosphere", null},
            {"irradiated", null}, {"dense_atmosphere", null}, {"high_gravity", null}, {"meteor_impacts", null}, {"no_atmosphere", null},
            {"extreme_weather", null}, {"inimical_biosphere", null}, {"hot", null}, {"cold", null}, {"tectonic_activity", null},
            {"thin_atmosphere", null}, {"low_gravity", null}, {"pollution", null}, {"dark", "poor_light"}, {"poor_light", null}};
    /** Resource tiers, poorest first; the industry that uses each. */
    static final String[][] RICHER = {
            {"farmland_poor", "farmland_adequate", "farmland_rich", "farmland_bountiful"},
            {"ore_sparse", "ore_moderate", "ore_abundant", "ore_rich", "ore_ultrarich"},
            {"rare_ore_sparse", "rare_ore_moderate", "rare_ore_abundant", "rare_ore_rich", "rare_ore_ultrarich"},
            {"volatiles_trace", "volatiles_diffuse", "volatiles_abundant", "volatiles_plentiful"},
            {"organics_trace", "organics_common", "organics_abundant", "organics_plentiful"}};
    static final String[] RICHER_USER = {Industries.FARMING, Industries.MINING, Industries.MINING, Industries.MINING, Industries.MINING};

    /** Pure: the change for these conditions, {from, to} (to null: removed), or null. A bad condition first, then a resource a working industry uses, then any. */
    static String[] forgingStep(java.util.Collection<String> has, java.util.Collection<String> industries) {
        for (String[] b : BAD) if (has.contains(b[0])) return new String[]{b[0], b[1]};
        for (int pass = 0; pass < 2; pass++) {
            for (int r = 0; r < RICHER.length; r++) {
                if (pass == 0 && !industries.contains(RICHER_USER[r])) continue;
                String[] tiers = RICHER[r];
                for (int i = 0; i < tiers.length - 1; i++) if (has.contains(tiers[i])) return new String[]{tiers[i], tiers[i + 1]};
            }
        }
        return null;
    }

    /** Makes the change at a colony. Its text, or null when there's nothing to change. */
    static String forgePlanet(MarketAPI m) {
        List<String> has = new ArrayList<String>(), inds = new ArrayList<String>();
        for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : m.getConditions()) if (c.getId() != null) has.add(c.getId());
        for (Industry i : m.getIndustries()) if (i.isFunctional()) inds.add(i.getId());
        String[] step = forgingStep(has, inds);
        if (step == null) return null;
        String from = Global.getSettings().getMarketConditionSpec(step[0]).getName();
        m.removeCondition(step[0]);
        if (step[1] == null) return from + " removed";
        m.addCondition(step[1]);
        return from + " became " + Global.getSettings().getMarketConditionSpec(step[1]).getName();
    }

    /**
     * Daily: FORGING_DAYS after it comes online, a round starts: every managed colony in turn, one a day, each change paid
     * above the credit reserve (a colony waits for the money). Offline, the timer resets.
     */
    static void forging(CASettings s) {
        if (!online(FORGING)) { CASync.stamp("t5forgingAt", 0L); CASync.text("t5forgingQueue", null); return; }
        long at = CASync.stamp("t5forgingAt");
        if (at == 0L) { CASync.stamp("t5forgingAt", CASync.now()); return; }
        String q = CASync.text("t5forgingQueue");
        if (q == null) {
            if (CASync.daysSince(at) < FORGING_DAYS) return;
            StringBuilder sb = new StringBuilder();
            for (MarketAPI m : Misc.getPlayerMarkets(false)) if (CASync.manages(m)) sb.append(m.getId()).append(',');
            CASync.stamp("t5forgingAt", CASync.now());
            if (sb.length() == 0) return;
            q = sb.toString();
        }
        int comma = q.indexOf(',');
        String id = comma < 0 ? q : q.substring(0, comma), rest = comma < 0 ? "" : q.substring(comma + 1);
        MarketAPI m = Global.getSector().getEconomy().getMarket(id);
        if (m != null && m.isPlayerOwned() && CASync.manages(m)) {
            if (CABuild.credits() - FORGING_COST < s.reserve) { CASync.text("t5forgingQueue", q); return; }   // waits for the money
            String did = forgePlanet(m);
            if (did != null) {
                CABudget.pay(s, m.getId(), FORGING_COST, 0f, 0f);
                CALog.did(m.getName() + ": Planet forging: " + did + ", for " + Misc.getDGSCredits(FORGING_COST) + ".", true);
            }
        }
        CASync.text("t5forgingQueue", rest.isEmpty() ? null : rest);
    }

    /** Prepare for removal: the forge's and ascendant's bonuses off and the armada gone. */
    static void removeAll() {
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            m.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).unmodifyFlat(ASC_MOD);
            m.getUpkeepMult().unmodifyMult(ASC_MOD);
            m.getStability().unmodifyFlat(ASC_DROP_MOD);
            m.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodifyFlat(FORGE_MOD);
            for (Industry i : m.getIndustries()) i.getSupplyBonusFromOther().unmodifyFlat(FORGE_MOD);
        }
        for (LocationAPI l : Global.getSector().getAllLocations())
            for (CampaignFleetAPI f : new ArrayList<CampaignFleetAPI>(l.getFleets())) if (f.getMemoryWithoutUpdate().getBoolean(ARMADA_MEM)) f.despawn();
    }
}
