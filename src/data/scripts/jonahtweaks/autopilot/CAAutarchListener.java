package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.listeners.ShowLootListener;

/**
 * 0.3.6.0 story mode: the Autarch AI Core's two listeners (transient, added on every load by CASync.onLoad).
 * - A sale at any market: the core leaves the market's stock (only one exists in the sector) and a new one is placed
 *   RETURN_DAYS later with a new hint.
 * - Salvage after a breakdown and its wait: an orbital habitat or cryosleeper may hold a new core (SALVAGE_CHANCE).
 */
public class CAAutarchListener implements ColonyInteractionListener, ShowLootListener {

    public void reportPlayerMarketTransaction(PlayerMarketTransaction tr) {
        try {
            if (!CASync.on()) return;   // story mode off: the quest waits
            CargoAPI sold = tr.getSold();
            if (sold == null) return;
            float n = sold.getQuantity(CargoAPI.CargoItemType.SPECIAL, CAAutarch.item());
            if (n <= 0f) return;
            // 0.3.6.2: a move into a Storage tab is reported the same way; it's not a sale (CAAutarch.anywhere counts Storage)
            if (tr.getSubmarket() != null && tr.getSubmarket().getPlugin() != null && tr.getSubmarket().getPlugin().isFreeTransfer()) return;
            if (tr.getSubmarket() != null && tr.getSubmarket().getCargo() != null)
                tr.getSubmarket().getCargo().removeItems(CargoAPI.CargoItemType.SPECIAL, CAAutarch.item(), n);
            CASync.sold(true);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core sale check failed", t); }
    }

    public void reportAboutToShowLootToPlayer(CargoAPI loot, InteractionDialogAPI dialog) {
        try {
            SectorEntityToken e = dialog == null ? null : dialog.getInteractionTarget();
            if (loot == null || e == null) return;
            if (CASync.salvageRoll(e.getCustomEntityType())) loot.addSpecial(CAAutarch.item(), 1);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core salvage check failed", t); }
    }

    public void reportPlayerOpenedMarket(MarketAPI market) { }
    public void reportPlayerClosedMarket(MarketAPI market) { }
    public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) { }
}
