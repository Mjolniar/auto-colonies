package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 0.3.7.7: Random Assortment of Things' Forge-Assisted Industry BP ("rat_consumeable_industry", its data the industry id).
 * RAT offers an industry that needs one while one is in your fleet's cargo or the colony's Storage; starting the build uses
 * it up, and demolishing puts it back in the colony's Storage. Autopilot Storage holds them too: the engine counts one held
 * there as available for its industry (the build menu is asked with it put in the colony's Storage for that moment), and a
 * build moves it to the colony's Storage first, where RAT uses it up. Only the item id, the API and Misc are used, so no
 * RAT class loads here.
 */
public final class CARatForge {
    private CARatForge() { }

    public static final String ITEM = "rat_consumeable_industry", MOD = "assortment_of_things";

    static boolean on() {
        try { return Global.getSettings().getModManager().isModEnabled(MOD); } catch (Throwable t) { return false; }
    }

    static SpecialItemData item(String industry) { return new SpecialItemData(ITEM, industry); }

    static boolean has(CargoAPI c, String industry) {
        return c != null && c.getQuantity(CargoAPI.CargoItemType.SPECIAL, item(industry)) >= 1f;
    }

    /** The Autopilot Storage holding a BP for this industry, the colony's own first; null when none does. */
    static CargoAPI holder(MarketAPI m, String industry) {
        if (industry == null) return null;
        CAStores.Store mine = null;
        for (CAStores.Store st : CAStores.all()) if (st.market == m) mine = st;
        if (mine != null && has(mine.cargo, industry)) return mine.cargo;
        for (CAStores.Store st : CAStores.all()) if (has(st.cargo, industry)) return st.cargo;
        return null;
    }

    /** Whether the build menu offers this here: as asked; else, with a BP held in Autopilot Storage, asked again with it in the colony's Storage. */
    public static boolean available(MarketAPI m, Industry inst, IndustrySpecAPI spec) {
        boolean open = inst.isAvailableToBuild();
        if (open || !on() || holder(m, spec.getId()) == null) return open;
        CargoAPI st = Misc.getStorageCargo(m);
        if (st == null) return false;
        SpecialItemData it = item(spec.getId());
        st.addSpecial(it, 1f);
        try { return inst.isAvailableToBuild(); }
        finally { st.removeItems(CargoAPI.CargoItemType.SPECIAL, it, 1f); }
    }

    /** Before a build: one BP moves from Autopilot Storage to the colony's Storage, unless the fleet's cargo or that Storage has one. */
    public static void stage(MarketAPI m, String industry) {
        if (!on()) return;
        CargoAPI st = Misc.getStorageCargo(m);
        if (st == null || has(st, industry)) return;
        try { if (has(Global.getSector().getPlayerFleet().getCargo(), industry)) return; } catch (Throwable ignored) { }
        CargoAPI from = holder(m, industry);
        if (from == null) return;
        from.removeItems(CargoAPI.CargoItemType.SPECIAL, item(industry), 1f);
        st.addSpecial(item(industry), 1f);
    }
}
