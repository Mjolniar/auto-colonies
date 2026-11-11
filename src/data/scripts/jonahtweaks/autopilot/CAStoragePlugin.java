package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;

/**
 * Autopilot Storage: a tab at every player colony holding that colony's own store (CAStores, per colony since 0.2.37.0;
 * before, one shared cargo let anything put in at one colony come out at any other). Colony items, AI cores, Antimatter
 * Stabilizers, research databanks, VPC chips and recipes go in, and nothing else (usable, 0.2.39.2). The manager uses only what is in these, ships what it
 * needs elsewhere for a fee and travel time, and returns anything it takes out of a building to that colony's store.
 * (The storage cheat that let anything in and fed colonies' shortages moved to Jonah's Easy Sector in 0.2.37.0.)
 */
public class CAStoragePlugin extends BaseSubmarketPlugin {
    public static final String ID = "jt_ca_storage";

    static CargoAPI none;   // a tab with no colony (never expected) shows an empty cargo

    @Override public CargoAPI getCargo() {
        if (market != null) return CAStores.of(market);
        if (none == null) none = com.fs.starfarer.api.Global.getFactory().createCargo(true);
        return none;
    }
    @Override public CargoAPI getCargoNullOk() { return getCargo(); }
    @Override public boolean isFreeTransfer() { return true; }
    @Override public float getTariff() { return 0f; }
    @Override public boolean isParticipatesInEconomy() { return false; }
    @Override public PlayerEconomyImpactMode getPlayerEconomyImpactMode() { return PlayerEconomyImpactMode.NONE; }
    @Override public boolean showInFleetScreen() { return false; }
    @Override public boolean showInCargoScreen() { return true; }
    @Override public String getBuyVerb() { return "Take"; }
    @Override public String getSellVerb() { return "Store"; }
    @Override public boolean isEnabled(CoreUIAPI ui) { return true; }
    @Override public void updateCargoPrePlayerInteraction() { }

    public static boolean allowedCommodity(String id) {
        return "alpha_core".equals(id) || "beta_core".equals(id) || "gamma_core".equals(id) || "ix_antimatter_stabilizer".equals(id)
                || "research_databank".equals(id)   // Vaults of Knowledge research takes them from here
                || (CAOmega.CORE.equals(id) && CAOmega.enabled())   // Omega Core Everywhere / Astral Ascension Renewed (0.2.38.0)
                || CAPanopticon.isCore(id);   // IX Revival's Panopticon Instance and Core, for its Surveillance Center (0.2.39.3)
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        return action == TransferAction.PLAYER_SELL && !allowedCommodity(commodityId);
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (action != TransferAction.PLAYER_SELL) return false;
        if (stack.isSpecialStack()) return !usable(stack);
        if (stack.isCommodityStack()) return !allowedCommodity(stack.getCommodityId());
        return true;   // weapons and fighter wings
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        if (stack.isSpecialStack() && stack.getSpecialDataIfSpecial() != null && "IndEvo_amb".equals(stack.getSpecialDataIfSpecial().getId()))
            return "Keep ambassadors in a colony's normal Storage; Autopilot installs them from there";
        if (stack.isSpecialStack())
            return "Autopilot has no use for this, so it stays out of Autopilot Storage: only colony items it can install, VPC chips and recipes go in. "
                    + "Keep blueprints, key cards and the like in your cargo or a colony's normal Storage";
        return "Only colony items, AI cores and research databanks can go in Autopilot Storage";
    }

    // ---------------- what goes in (0.2.39.2) ----------------

    /**
     * A special item Autopilot can use from here: any colony item the game can install (its item effects table, every mod's
     * included), the items it values by hand, Industrial Evolution's VPC chips and Substance.Abuse's recipes and Recipe Book.
     * Nothing else has a use here. Special items used to go in unless they were campaign consumables, so UAF's Access Key
     * Cards (turned in at the Aurorans for credits) and blueprint packages whose blueprints you already know piled up in it.
     * Research only takes databanks, AI cores and colony items, so it loses nothing. Pure.
     */
    static boolean usable(String id, boolean installable) {
        if (id == null || "IndEvo_amb".equals(id)) return false;   // ambassadors are installed from normal Storage
        return installable || CAItems.ITEM_VALUE.containsKey(id) || id.startsWith("IndEvo_vpc_")
                || CABrewery.BOOK.equals(id) || (id.startsWith("alcoholism_") && id.endsWith("_item"));
    }

    static boolean usable(CargoStackAPI stack) {
        try {
            com.fs.starfarer.api.campaign.SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) return false;
            boolean installable = com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo.ITEM_EFFECTS.containsKey(data.getId());
            return usable(data.getId(), installable) && !consumable(stack);
        } catch (Throwable t) { return false; }
    }

    // ---------------- campaign consumables (0.2.36.1) ----------------

    /**
     * A campaign consumable or blueprint, which Autopilot never uses: an item that is used up when right-clicked (blueprints,
     * Random Assortment's Forge-Assisted Industry BP), or that its mod marks as a consumable by tag, maker, plugin or id
     * (Industrial Evolution's Nanite Repair Module, locator, spike, scoop, decoy, spoofer and relay). Never a colony item
     * the game can install (those have an entry in its item effects table). Pure.
     */
    static boolean consumable(String id, java.util.Collection<String> tags, String maker, String pluginClass, boolean usedUpOnRightClick, boolean colonyItem) {
        if (colonyItem) return false;
        if (usedUpOnRightClick) return true;
        if (tags != null) for (String t : tags) if (t != null && consumableWord(t)) return true;
        return consumableWord(maker) || consumableWord(pluginClass) || consumableWord(id);
    }

    static boolean consumableWord(String s) {
        if (s == null) return false;
        String l = s.toLowerCase(java.util.Locale.ROOT);
        return l.contains("consumable") || l.contains("consumeable");
    }

    /** Whether a stack is a campaign consumable or blueprint (see above), read from its item spec and plugin. */
    static boolean consumable(CargoStackAPI stack) {
        try {
            com.fs.starfarer.api.campaign.SpecialItemData data = stack.getSpecialDataIfSpecial();
            com.fs.starfarer.api.campaign.SpecialItemSpecAPI spec = stack.getSpecialItemSpecIfSpecial();
            if (data == null || spec == null) return false;
            boolean colonyItem = com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo.ITEM_EFFECTS.containsKey(data.getId())
                    || CAItems.ITEM_VALUE.containsKey(data.getId()) || data.getId().startsWith("IndEvo_vpc_");
            boolean usedUp = false;
            com.fs.starfarer.api.campaign.SpecialItemPlugin plugin = stack.getPlugin();
            if (plugin != null) {
                try { usedUp = plugin.hasRightClickAction() && plugin.shouldRemoveOnRightClickAction(); } catch (Throwable ignored) { }
            }
            return consumable(data.getId(), spec.getTags(), spec.getManufacturer(), plugin == null ? null : plugin.getClass().getName(), usedUp, colonyItem);
        } catch (Throwable t) { return false; }
    }

    /**
     * Daily: special items Autopilot has no use for (see usable; campaign consumables and blueprints since 0.2.36.1) go from a
     * colony's Autopilot Storage to its normal Storage, with one short log line.
     */
    public static void evict() {
        for (CAStores.Store s : CAStores.all()) {
            CargoAPI dest = com.fs.starfarer.api.util.Misc.getStorageCargo(s.market);
            if (dest == null) continue;
            java.util.List<String> moved = new java.util.ArrayList<String>();
            int items = 0;
            for (CargoStackAPI stack : s.cargo.getStacksCopy()) {
                if (!stack.isSpecialStack() || usable(stack)) continue;
                float n = stack.getSize();
                String name = stack.getDisplayName();
                s.cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), n);
                dest.addSpecial(stack.getSpecialDataIfSpecial(), n);
                items += Math.round(n);
                moved.add((n > 1f ? (int) n + " " : "") + name);
            }
            if (moved.isEmpty()) continue;
            String what = moved.size() <= 3 ? CAContracts.join(moved)
                    : CAContracts.join(moved.subList(0, 3)) + " and " + (moved.size() - 3) + " more kinds (" + items + " items)";
            CALog.add(s.market.getName() + ": moved " + what + " from Autopilot Storage to Storage (Autopilot has no use for them)");
        }
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) { return true; }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "Ships can't be stored here";
    }
}
