package data.scripts.jonahtweaks.autopilot;

import com.thoughtworks.xstream.XStream;

/**
 * 0.3.0.0 (A0): stable short names for every Auto Colonies class stored in the save, and retired fields dropped, so 0.3.0
 * and later can move, split and rename classes without breaking saves (research 07 M11). Called from
 * JTModPlugin.configureXStream before any save is read or written.
 * Saves from before 0.3.0 still load: XStream falls back to the full class name when a name isn't an alias. Saves written
 * with these names don't load in 0.2.45 or older.
 * Rules: never rename or reuse an alias once released. When a class moves, point its alias at the new class and also
 * alias its old full name (x.alias("data.scripts.jonahtweaks.autopilot.Old", New.class)) for saves from before 0.3.0.
 * A class newly stored in CAData needs an alias here; JTSaveCompatCheck fails on one without.
 */
public final class CAXStream {
    private CAXStream() { }

    /** The alias prefix; JTSaveCompatCheck looks for it. */
    static final String PREFIX = "JCM-";

    interface Type { Class<?> get(); }

    public static void configure(XStream x) {
        if (x == null) return;
        // saved data (CAData and everything in it)
        alias(x, "JCM-Data", () -> CAData.class);
        alias(x, "JCM-Colony", () -> CAData.Colony.class);
        alias(x, "JCM-Profile", () -> CAData.Profile.class);
        alias(x, "JCM-Mode", () -> CAData.Mode.class);
        alias(x, "JCM-Settings", () -> CASettings.class);
        alias(x, "JCM-Shipment", () -> CAStores.Shipment.class);
        alias(x, "JCM-Load", () -> CALogistics.Load.class);
        alias(x, "JCM-ObjectiveOrder", () -> CAObjectives.Order.class);
        alias(x, "JCM-TascOrder", () -> CATascStations.Order.class);
        // 0.3.0 saved lists (FIELDS.md)
        alias(x, "JCM-Tombstone", () -> CATombstone.Mark.class);
        alias(x, "JCM-ShipOrder", () -> CAShipyard.Order.class);
        alias(x, "JCM-StockpileBuy", () -> CAStockpile.Buy.class);
        alias(x, "JCM-ExpandOrder", () -> CAExpansion.Order.class);
        // saved by the game itself: the empire panel (persistent intel) and the Autopilot Storage submarket on every colony
        alias(x, "JCM-Intel", () -> CAIntel.class);
        alias(x, "JCM-StoragePlugin", () -> CAStoragePlugin.class);

        // retired fields: new saves drop them, and older saves still have them. 0.3.0.0 (A3) deleted them from CASettings, so
        // these lines are what lets an older save load (XStream throws on a saved field the class doesn't have unless it's
        // omitted here): never remove one; a field retired later gets its own line before it's deleted
        omit(x, () -> CASettings.class, "adminDelay");      // never read (07 L2)
        omit(x, () -> CASettings.class, "storageCheat");    // moved to Easy Sector in 0.2.37.0
        omit(x, () -> CASettings.class, "haFleetCap");      // moved to Easy Sector in 0.2.37.0
        omit(x, () -> CASettings.class, "nexAttackGap");    // moved to Easy Sector in 0.2.37.0
        omit(x, () -> CASettings.class, "richIncomeRef");   // the old pause's reference, unused since 0.2.34.1
    }

    /** One alias; a class that fails to load costs only its own alias (its saves keep the full name). */
    static void alias(XStream x, String name, Type t) {
        try { x.alias(name, t.get()); }
        catch (Throwable e) { warn("alias " + name, e); }
    }

    static void omit(XStream x, Type t, String field) {
        try { x.omitField(t.get(), field); }
        catch (Throwable e) { warn("retired field " + field, e); }
    }

    static void warn(String what, Throwable e) {
        try { CADiagnostics.LOG.warn(CADiagnostics.TAG + "save names: " + what + " failed", e); } catch (Throwable ignored) { }
    }
}
