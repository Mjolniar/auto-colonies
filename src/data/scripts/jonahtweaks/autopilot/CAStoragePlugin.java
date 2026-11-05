package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;

/**
 * Autopilot Storage: a tab at every player colony holding that colony's own store (CAStores, per colony since 2.37.0;
 * before, one shared cargo let anything put in at one colony come out at any other). Colony items, AI cores, Antimatter
 * Stabilizers, research databanks, VPC chips and recipes go in. The manager uses only what is in these, ships what it
 * needs elsewhere for a fee and travel time, and returns anything it takes out of a building to that colony's store.
 * (The storage cheat that let anything in and fed colonies' shortages moved to Jonah's Easy Sector in 2.37.0.)
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
                || "research_databank".equals(id);   // Vaults of Knowledge research takes them from here
    }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        return action == TransferAction.PLAYER_SELL && !allowedCommodity(commodityId);
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (action != TransferAction.PLAYER_SELL) return false;
        if (stack.isSpecialStack()) return stack.getSpecialDataIfSpecial() != null && ("IndEvo_amb".equals(stack.getSpecialDataIfSpecial().getId()) || consumable(stack));
        if (stack.isCommodityStack()) return !allowedCommodity(stack.getCommodityId());
        return true;   // weapons and fighter wings
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        if (stack.isSpecialStack() && stack.getSpecialDataIfSpecial() != null && "IndEvo_amb".equals(stack.getSpecialDataIfSpecial().getId()))
            return "Keep ambassadors in a colony's normal Storage; Autopilot installs them from there";
        if (stack.isSpecialStack() && consumable(stack))
            return "Campaign consumables and blueprints don't go in Autopilot Storage; keep them in your cargo or a colony's normal Storage";
        return "Only colony items, AI cores and research databanks can go in Autopilot Storage";
    }

    // ---------------- campaign consumables (2.36.1) ----------------

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
     * Daily: campaign consumables and blueprints in a colony's Autopilot Storage (from before 2.36.1) go to that colony's
     * normal Storage, with a log line.
     */
    public static void evict() {
        for (CAStores.Store s : CAStores.all()) {
            CargoAPI dest = com.fs.starfarer.api.util.Misc.getStorageCargo(s.market);
            if (dest == null) continue;
            java.util.List<String> moved = new java.util.ArrayList<String>();
            for (CargoStackAPI stack : s.cargo.getStacksCopy()) {
                if (!stack.isSpecialStack() || !consumable(stack)) continue;
                float n = stack.getSize();
                s.cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, stack.getSpecialDataIfSpecial(), n);
                dest.addSpecial(stack.getSpecialDataIfSpecial(), n);
                moved.add((n > 1f ? (int) n + " " : "") + stack.getDisplayName());
            }
            if (!moved.isEmpty()) CALog.add(s.market.getName() + ": moved " + CAContracts.join(moved) + " from Autopilot Storage to Storage (campaign consumables and blueprints don't go there)");
        }
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) { return true; }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "Ships can't be stored here";
    }
}
