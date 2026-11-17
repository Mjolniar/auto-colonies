package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.Random;

/**
 * 0.3.6.0 story mode: the Autarch AI Core, the special item that wakes the Autopilot. Defined in data/campaign/special_items.csv
 * (no colony_item tag, so no building takes it; base price 1,000,000). One is placed once per save in the salvage of a
 * derelict research station in a random uninhabited fringe system; the TriPad names that system once you have a colony.
 * After a sale (CASync.lifecycle) a new one is placed the same way in another system, and the TriPad names it. After a
 * breakdown, the next one turns up in salvage (CAAutarchListener) with no hint.
 * Story state keys: autarchPlaced, autarchSystem (system name).
 */
public final class CAAutarch {
    private CAAutarch() { }

    public static final String ID = "autarch_core";
    /** Portrait the core speaks with (CATriPad), loaded through data/config/settings.json. */
    public static final String PORTRAIT = "graphics/portraits/autarch_core.png";
    /** Sector-centre distance band for the system (light-years x 2000 units): the fringe, away from the core worlds. */
    static final float MIN_DIST = 14000f, MAX_DIST = 30000f;

    public static SpecialItemData item() { return new SpecialItemData(ID, null); }

    /** Autarch cores in the fleet's cargo. */
    public static int inCargo() {
        try { return (int) Global.getSector().getPlayerFleet().getCargo().getQuantity(CargoAPI.CargoItemType.SPECIAL, item()); }
        catch (Throwable t) { return 0; }
    }

    /**
     * One in the fleet's cargo or any storage tab in the sector. 0.3.8.9: every free-transfer tab counts (Industrial
     * Evolution's warehouses, Autopilot Storage), the test takeFromMarkets skips them by.
     */
    public static boolean anywhere() {
        if (inCargo() > 0) return true;
        try {
            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                CargoAPI c = CASync.storageOf(m);
                if (c != null && c.getQuantity(CargoAPI.CargoItemType.SPECIAL, item()) > 0f) return true;
                for (com.fs.starfarer.api.campaign.econ.SubmarketAPI sub : m.getSubmarketsCopy()) {
                    if (sub.getPlugin() == null || !sub.getPlugin().isFreeTransfer()) continue;
                    CargoAPI sc = sub.getCargo();
                    if (sc != null && sc.getQuantity(CargoAPI.CargoItemType.SPECIAL, item()) > 0f) return true;
                }
            }
            // 0.3.9.6 (C1): storage outside the economy (an abandoned station, a RAT settlement) and cargo pods in space
            for (com.fs.starfarer.api.campaign.LocationAPI loc : CARemoval.locations())
                for (com.fs.starfarer.api.campaign.SectorEntityToken e : loc.getAllEntities()) {
                    CargoAPI c = null;
                    if ("cargo_pods".equals(e.getCustomEntityType())) c = e.getCargo();
                    else if (e.getMarket() != null && !e.getMarket().isInEconomy()) c = Misc.getStorageCargo(e.getMarket());
                    if (c != null && c.getQuantity(CargoAPI.CargoItemType.SPECIAL, item()) > 0f) return true;
                }
        } catch (Throwable t) { return true; }   // can't tell: not lost
        return false;
    }

    /**
     * 0.3.6.2: the derelict station holding the core is still in the sector. Saves from 0.3.6.0 and 0.3.6.1 didn't record its
     * id: it's looked up once by its memory flag. Can't tell: true (never declared lost on a guess).
     */
    public static boolean siteExists() {
        try {
            String id = CASync.text("autarchSite");
            if (id == null) {
                for (StarSystemAPI s : Global.getSector().getStarSystems())
                    for (SectorEntityToken e : s.getAllEntities())
                        if (e.getMemoryWithoutUpdate().getBoolean("$autarch_core_site")) { CASync.text("autarchSite", e.getId()); return true; }
                CASync.text("autarchSite", "gone");
                return false;
            }
            return !"gone".equals(id) && Global.getSector().getEntityById(id) != null;
        } catch (Throwable t) { return true; }
    }

    /**
     * 0.3.6.2: a core sold at a market while story mode was off (the listener only runs while it's on): taken off that
     * market's shelves so it can't be bought back next to its replacement. True when one was found.
     */
    public static boolean takeFromMarkets() {
        boolean found = false;
        try {
            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                for (com.fs.starfarer.api.campaign.econ.SubmarketAPI sub : m.getSubmarketsCopy()) {
                    if (sub.getPlugin() != null && sub.getPlugin().isFreeTransfer()) continue;   // Storage tabs: anywhere() covers them (0.3.8.9: every free-transfer tab)
                    CargoAPI c = sub.getCargo();
                    float n = c == null ? 0f : c.getQuantity(CargoAPI.CargoItemType.SPECIAL, item());
                    if (n > 0f) { c.removeItems(CargoAPI.CargoItemType.SPECIAL, item(), n); found = true; }
                }
            }
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core market check failed", t); }
        return found;
    }

    /** Takes one from the fleet's cargo; false when there is none. */
    public static boolean take() {
        if (inCargo() < 1) return false;
        Global.getSector().getPlayerFleet().getCargo().removeItems(CargoAPI.CargoItemType.SPECIAL, item(), 1);
        return true;
    }

    /** Puts one in the fleet's cargo (the cradle's cutoff). */
    public static void give() { Global.getSector().getPlayerFleet().getCargo().addSpecial(item(), 1); }

    /** The system the core is in, or null when there's no hint. The TriPad names only its constellation (area). */
    public static String system() { return CASync.text("autarchSystem"); }

    /**
     * 0.3.6.6: the constellation the TriPad points to ("Gamma Ceti constellation", used after "the"), or null when there's no
     * hint. A save from before 0.3.6.6 gets it from the system once.
     */
    public static String area() {
        String a = CASync.text("autarchArea");
        if (a != null) return a;
        String sys = system();
        if (sys == null) return null;
        try {
            for (StarSystemAPI s : Global.getSector().getStarSystems()) {
                if (!sys.equals(s.getBaseName())) continue;
                a = areaOf(s);
                CASync.text("autarchArea", a);
                return a;
            }
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core constellation failed", t); }
        return null;
    }

    /** 0.3.6.6: the system's constellation as the map names it; a lone system: the fringe near the closest constellation. */
    static String areaOf(StarSystemAPI s) {
        com.fs.starfarer.api.impl.campaign.procgen.Constellation c = constellationOf(s);
        if (c == null) return "fringe";
        return s.isInConstellation() && c == s.getConstellation() ? c.getNameWithLowercaseType() : "fringe near the " + c.getNameWithLowercaseType();
    }

    /** 0.3.6.8: the system's constellation; a lone system: the closest one (the objective's map marker, CAAutarchIntel). Null: none. */
    static com.fs.starfarer.api.impl.campaign.procgen.Constellation constellationOf(StarSystemAPI s) {
        if (s.isInConstellation() && s.getConstellation() != null) return s.getConstellation();
        StarSystemAPI best = null;
        float bd = Float.MAX_VALUE;
        for (StarSystemAPI o : Global.getSector().getStarSystems()) {
            if (o == s || !o.isInConstellation() || o.getConstellation() == null) continue;
            float d = Misc.getDistance(o.getLocation(), s.getLocation());
            if (d < bd) { bd = d; best = o; }
        }
        return best != null ? best.getConstellation() : null;
    }

    /** On every load: places the first core once. */
    public static void ensurePlaced() {
        if (CASync.flag("autarchPlaced") || CASync.installed()) return;
        if (place(null)) CASync.flag("autarchPlaced", true);
    }

    /** Places a core in a new derelict research station, not in the named system. False when no system fits (tried again later). */
    public static boolean place(String notIn) {
        StarSystemAPI sys = pick(new Random(Misc.genRandomSeed()), notIn);
        if (sys == null) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core: no uninhabited fringe system found; trying again later");
            return false;
        }
        SectorEntityToken st = BaseThemeGenerator.addSalvageEntity(sys, "station_research", Factions.DERELICT);
        Random r = new Random();
        float rad = sys.getStar() == null ? 3000f : sys.getStar().getRadius() + 2500f + r.nextFloat() * 2500f;
        if (sys.getStar() != null) st.setCircularOrbit(sys.getStar(), r.nextFloat() * 360f, rad, rad / 10f);   // 0.3.8.9: pick() needs a star; the starless branch is removed
        st.setDiscoverable(true);
        CargoAPI loot = Global.getFactory().createCargo(true);
        loot.addSpecial(item(), 1);
        BaseSalvageSpecial.addExtraSalvage(st, loot);
        st.getMemoryWithoutUpdate().set("$autarch_core_site", true);
        CASync.text("autarchSite", st.getId());   // 0.3.6.2: siteExists
        CASync.text("autarchSystem", sys.getBaseName());
        CASync.text("autarchArea", areaOf(sys));   // 0.3.6.6: the TriPad names the constellation only
        CADiagnostics.LOG.info(CADiagnostics.TAG + "Autarch AI Core placed in a derelict research station in " + sys.getName());
        return true;
    }

    /** A procgen system with a star, no markets, not hidden or cut off, in the fringe band, not the named one. */
    static StarSystemAPI pick(Random rnd, String notIn) {
        WeightedRandomPicker<StarSystemAPI> p = new WeightedRandomPicker<StarSystemAPI>(rnd);
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (!usable(s, notIn)) continue;
            float d = s.getLocation().length();
            if (d < MIN_DIST || d > MAX_DIST) continue;
            p.add(s, s.isInConstellation() ? 1f : 0.05f);   // 0.3.6.6: the TriPad points to a constellation
        }
        if (p.isEmpty()) {   // 0.3.6.2: no system in the fringe band (small or modded sectors): any uninhabited procgen system
            for (StarSystemAPI s : Global.getSector().getStarSystems()) if (usable(s, notIn)) p.add(s, 1f);
        }
        return p.pick();
    }

    /** 0.3.8.9: one test for both of pick()'s loops: procgen, a star, no markets, not hidden, core, cut off or special, not the named one. */
    static boolean usable(StarSystemAPI s, String notIn) {
        if (!s.isProcgen() || s.getStar() == null || s.hasTag(Tags.THEME_HIDDEN) || s.hasTag(Tags.THEME_CORE)
                || s.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER) || s.hasTag(Tags.THEME_SPECIAL)) return false;
        if (notIn != null && notIn.equals(s.getBaseName())) return false;
        return Misc.getMarketsInLocation(s).isEmpty();
    }
}
