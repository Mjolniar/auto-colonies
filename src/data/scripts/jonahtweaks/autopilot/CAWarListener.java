package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import exerelin.campaign.InvasionRound;
import exerelin.utilities.ColonyNPCHostileActListener;
import exerelin.utilities.InvasionListener;

import java.util.List;

/**
 * 0.3.0.0 (WS-E, research 06 section 2.9): a transient listener (never saved, so a save loads without Nexerelin and without
 * this class). It forwards market captures to CAConquest.onCaptured and saturation bombardments to CAScorched.onBombed.
 * ensure() adds it once per session. 0.3.0.0 (fix): called on game load (JTModPlugin), and daily from CAWar.run,
 * CAScorched.step and CAConquest.tend (its own breaker), so a load or a stopped war fleet step doesn't leave it out. Each
 * call is its own try: a failure here never reaches Nexerelin's own listener loop. CAConquest.tend's daily scans catch a
 * capture (CAConquest.catchMissed) or a bombardment (CAScorched.catchMissed) this missed.
 */
public class CAWarListener implements InvasionListener, ColonyNPCHostileActListener {

    /** Adds the listener unless this session has it (transient: addListener(x, true)). */
    public static void ensure() {
        try {
            ListenerManagerAPI lm = Global.getSector().getListenerManager();
            if (!lm.hasListenerOfClass(CAWarListener.class)) lm.addListener(new CAWarListener(), true);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "war listener could not be added", t);
        }
    }

    // ---------------- InvasionListener ----------------

    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved,
                                       boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {
        try { CAConquest.onCaptured(market, newOwner, oldOwner, playerInvolved, isCapture); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "capture report failed", t); }
    }

    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) { }

    @Override
    public void reportInvasionRound(InvasionRound.InvasionRoundResult result, CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) { }

    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market, float numRounds, boolean success) { }

    // ---------------- ColonyNPCHostileActListener ----------------

    @Override
    public void reportNPCSaturationBombardment(MarketAPI market, MarketCMD.TempData actionData) {
        try { CAScorched.onBombed(market, actionData); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "bombardment report failed", t); }
    }

    @Override
    public void reportNPCGenericRaid(MarketAPI market, MarketCMD.TempData actionData) { }

    @Override
    public void reportNPCIndustryRaid(MarketAPI market, MarketCMD.TempData actionData, Industry industry) { }

    @Override
    public void reportNPCTacticalBombardment(MarketAPI market, MarketCMD.TempData actionData) { }
}
