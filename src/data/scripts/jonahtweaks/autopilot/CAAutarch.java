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

    /** One in the fleet's cargo or any storage tab in the sector. */
    public static boolean anywhere() {
        if (inCargo() > 0) return true;
        try {
            for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
                CargoAPI c = CASync.storageOf(m);
                if (c != null && c.getQuantity(CargoAPI.CargoItemType.SPECIAL, item()) > 0f) return true;
            }
        } catch (Throwable t) { return true; }   // can't tell: not lost
        return false;
    }

    /** Takes one from the fleet's cargo; false when there is none. */
    public static boolean take() {
        if (inCargo() < 1) return false;
        Global.getSector().getPlayerFleet().getCargo().removeItems(CargoAPI.CargoItemType.SPECIAL, item(), 1);
        return true;
    }

    /** Puts one in the fleet's cargo (the cradle's cutoff). */
    public static void give() { Global.getSector().getPlayerFleet().getCargo().addSpecial(item(), 1); }

    /** The system the TriPad points to, or null when there's no hint. */
    public static String system() { return CASync.text("autarchSystem"); }

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
        if (sys.getStar() != null) st.setCircularOrbit(sys.getStar(), r.nextFloat() * 360f, rad, rad / 10f);
        else st.getLocation().set(rad, 0f);
        st.setDiscoverable(true);
        CargoAPI loot = Global.getFactory().createCargo(true);
        loot.addSpecial(item(), 1);
        BaseSalvageSpecial.addExtraSalvage(st, loot);
        st.getMemoryWithoutUpdate().set("$autarch_core_site", true);
        CASync.text("autarchSystem", sys.getBaseName());
        CADiagnostics.LOG.info(CADiagnostics.TAG + "Autarch AI Core placed in a derelict research station in " + sys.getName());
        return true;
    }

    /** A procgen system with a star, no markets, not hidden or cut off, in the fringe band, not the named one. */
    static StarSystemAPI pick(Random rnd, String notIn) {
        WeightedRandomPicker<StarSystemAPI> p = new WeightedRandomPicker<StarSystemAPI>(rnd);
        for (StarSystemAPI s : Global.getSector().getStarSystems()) {
            if (!s.isProcgen() || s.getStar() == null || s.hasTag(Tags.THEME_HIDDEN) || s.hasTag(Tags.THEME_CORE)
                    || s.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER) || s.hasTag(Tags.THEME_SPECIAL)) continue;
            if (notIn != null && notIn.equals(s.getBaseName())) continue;
            if (!Misc.getMarketsInLocation(s).isEmpty()) continue;
            float d = s.getLocation().length();
            if (d < MIN_DIST || d > MAX_DIST) continue;
            p.add(s, 1f);
        }
        return p.pick();
    }
}
