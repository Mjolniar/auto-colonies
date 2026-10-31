package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;

/**
 * Autopilot Storage: one shared storage shown as a tab at every player colony. Colony items, AI cores, Antimatter
 * Stabilizers and research databanks go in; with the storage cheat on, anything but ships and ambassadors does, and
 * colonies draw their shortages from it (CAStockpile). The manager uses only what is here, and returns anything it
 * removes to here.
 */
public class CAStoragePlugin extends BaseSubmarketPlugin {
    public static final String ID = "jt_ca_storage";

    @Override public CargoAPI getCargo() { return CAData.get().storage(); }
    @Override public CargoAPI getCargoNullOk() { return CAData.get().storage(); }
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
        return cheat() || "alpha_core".equals(id) || "beta_core".equals(id) || "gamma_core".equals(id) || "ix_antimatter_stabilizer".equals(id)
                || "research_databank".equals(id);   // Vaults of Knowledge research takes them from here
    }

    static boolean cheat() { return CAData.get().settings.storageCheat; }

    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        return action == TransferAction.PLAYER_SELL && !allowedCommodity(commodityId);
    }

    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (action != TransferAction.PLAYER_SELL) return false;
        if (stack.isSpecialStack()) return stack.getSpecialDataIfSpecial() != null && "IndEvo_amb".equals(stack.getSpecialDataIfSpecial().getId());
        if (stack.isCommodityStack()) return !allowedCommodity(stack.getCommodityId());
        return !cheat();   // weapons and fighter wings
    }

    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action) {
        if (stack.isSpecialStack() && stack.getSpecialDataIfSpecial() != null && "IndEvo_amb".equals(stack.getSpecialDataIfSpecial().getId()))
            return "Keep ambassadors in a colony's normal Storage; Autopilot installs them from there";
        return "Only colony items, AI cores and research databanks can go in Autopilot Storage (anything can with the storage cheat in Settings)";
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) { return true; }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "Ships can't be stored here";
    }
}
