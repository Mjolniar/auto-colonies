package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.3.11.3: late-game tier 4 stations, part of the endgame economy (its setting: Endgame economy On). One colony at a time,
 * an Auto colony whose tier 3 station is working gets the best tier 4 station you can build, only while its system is calm:
 * no siege, no raid group or raid on its way, not the coming crisis's target, threat not high, no hostile fleets there.
 * - AotD Vaults of Knowledge's Star Citadels (aotd_citadel_hightech, aotd_citadel; Citadel Schematics): built beside the
 *   old station, which the Citadel takes over (its core, improvement and item) and removes when it's done.
 * - Interstellar Imperium's Stella Castellum (ii_stellacastellum; Fixes 1.0.29 opens it with Orbital Fleetwork research):
 *   an upgrade of the tier 3 station, from any line, started the way the build menu's upgrade does (CAAotD.startUpgrade).
 * Either way the old station keeps working until the new one is done, so the colony never goes without one. Paid at the
 * game's price plus the fee from surplus credits, by rule (its worth is defense the probe can't measure), ranked after
 * everything that pays.
 */
public final class CAStationTier4 {
    private CAStationTier4() { }

    static final String SOURCE = "tier 4 station", CASTELLUM = "ii_stellacastellum";
    static final String[] CITADELS = {"aotd_citadel_hightech", "aotd_citadel"};

    /** Pure: a system is calm enough to start: nothing hostile on its way or there, not the crisis target, threat not high. */
    static boolean calm(boolean siege, boolean raidComing, boolean crisisTarget, boolean threatHigh, float hostileFP) {
        return !siege && !raidComing && !crisisTarget && !threatHigh && hostileFP <= 0f;
    }

    /** Hostile fleets anywhere in the system (CASiege.hostile; CADefense is Nexerelin's, so not used here). */
    static float hostileFP(StarSystemAPI sys) {
        float fp = 0f;
        try { for (CampaignFleetAPI f : sys.getFleets()) if (CASiege.hostile(f)) fp += f.getFleetPoints(); }
        catch (Throwable t) { return 1f; }   // unknown counts as not calm
        return fp;
    }

    static int tier(Industry st) {
        try { return CAPlan.stationTier(st.getSpec()); } catch (Throwable t) { return 0; }
    }

    /** A tier 4 station under way anywhere (one at a time). */
    static boolean underWay() {
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            for (String c : CITADELS) if (m.hasIndustry(c) && m.getIndustry(c).isBuilding()) return true;
            Industry st = Misc.getStationIndustry(m);
            if (st != null && st.isUpgrading()) return true;
        }
        return false;
    }

    static IndustrySpecAPI spec(String id) {
        try { return Global.getSettings().getIndustrySpec(id); } catch (Throwable t) { return null; }
    }

    static boolean buildable(MarketAPI m, String id) {
        IndustrySpecAPI sp = spec(id);
        if (sp == null || m.hasIndustry(id)) return false;
        try { Industry probe = sp.getNewPluginInstance(m); return probe != null && probe.isAvailableToBuild(); } catch (Throwable t) { return false; }
    }

    public static List<CAAction> offer(List<MarketAPI> auto) {
        List<CAAction> out = new ArrayList<CAAction>();
        CASettings s = CAData.settings();
        if (!CAEndgame.on(s) || !CAEconomy.buyerOpen(s) || underWay()) return out;
        CAData d = CAData.get();
        for (MarketAPI m : auto) {
            Industry st = Misc.getStationIndustry(m);
            if (st == null || !st.isFunctional() || st.isBuilding() || st.isUpgrading() || tier(st) != 3) continue;
            StarSystemAPI sys = m.getStarSystem();
            if (sys == null) continue;
            boolean crisis = false;
            try { crisis = CACrisis.targets().contains(m); } catch (Throwable ignored) { }
            if (!calm(CASiege.why(m) != null, CASiege.incoming(sys) != null, crisis, d.colony(m.getId()).threatHigh, hostileFP(sys))) continue;
            String pick = null;
            for (String c : CITADELS) if (pick == null && buildable(m, c)) pick = c;
            boolean upgrade = false;
            if (pick == null && buildable(m, CASTELLUM)) { pick = CASTELLUM; upgrade = true; }
            if (pick == null) continue;
            IndustrySpecAPI sp = spec(pick);
            float cost, days;
            try { Industry inst = sp.getNewPluginInstance(m); cost = inst.getBuildCost(); days = inst.getBuildTime(); }
            catch (Throwable t) { cost = sp.getCost(); days = sp.getBuildTime(); }
            CAAction a = new CAAction();
            a.kind = upgrade ? CAAction.Kind.UPGRADE : CAAction.Kind.BUILD; a.source = SOURCE; a.market = m; a.marketId = m.getId();
            a.id = pick; a.name = sp.getName(); a.why = "a tier 4 station";
            a.cost = cost; a.fee = CABudget.constructionFee(s, cost); a.days = days; a.upkeep = 0f;
            a.rapid = CABudget.rapidFee(cost);
            a.npv = CAValue.npv(0f, CAHorizon.months(), days, cost, a.fee);
            a.byRule = true;
            a.usesQueue = !upgrade;
            a.building = st.getId();
            a.fromBudget = false; a.fromSurplus = true; a.chargeAll = false;
            a.upkeepCap = CASurplus.upkeepCap(m, s);
            final boolean up = upgrade;
            a.starter = (x) -> start(x, up);
            out.add(a);
            break;   // one colony at a time
        }
        return out;
    }

    static String start(CAAction a, boolean upgrade) {
        MarketAPI m = a.market;
        CASettings s = CAData.settings();
        if (underWay()) return "A tier 4 station is already under way";
        if (CABuild.credits() - CABudget.buildTotal(s, a.cost) < s.reserve) return "Not enough credits above the reserve";
        Industry st = Misc.getStationIndustry(m);
        if (st == null) return "No station here";
        try {
            if (upgrade) CAAotD.startUpgrade(st, a.id);
            else {
                CAEngine.Pick p = new CAEngine.Pick();
                p.id = a.id; p.name = a.name; p.verb = "Build"; p.cost = a.cost;
                String err = CABuild.start(m, p);
                if (err != null) return err;
                CABudget.charge(s, m.getId(), 0f, a.fee, 0f);
                CALog.did(m.getName() + ": " + a.name + " started beside the " + st.getCurrentName() + ", which it replaces when done. " + Misc.getDGSCredits(a.cost) + ".");
                return null;
            }
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "tier 4 station start failed at " + m.getName(), t);
            return "The game refused the upgrade";
        }
        if (!st.isUpgrading()) return "The game refused the upgrade";
        CAManual.started(m, a.id);   // 0.3.25.1: Autopilot's upgrade; CAManual locked it as yours once it finished
        CABudget.pay(s, m.getId(), CABudget.buildTotal(s, a.cost) - a.fee, a.fee, 0f);
        CALog.did(m.getName() + ": " + st.getCurrentName() + " upgrading to the " + a.name + ". " + Misc.getDGSCredits(a.cost) + ".");
        return null;
    }
}
