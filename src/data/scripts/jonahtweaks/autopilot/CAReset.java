package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * 0.3.0.0 (A1): clears every cache, timer and session flag Colony Manager keeps in static fields, first thing on every load
 * (JTModPlugin.onGameLoad), so nothing from the last save or the last session's clock carries over (07 H2, H6 and section 3
 * "Mutable statics"). Before 0.3.0 several weren't cleared: reload a save from 20 days earlier and research ranking, blocked
 * plan steps, contract shortfalls and settlement sites came from the other timeline, Nexerelin defense and relief fleets
 * for those systems stayed on cooldown, and their "can't send" warnings stayed quiet in every later save.
 *
 * Every static that holds state is reset here to the value it's declared with, except the weekly timers in
 * JTResetCheck.NEVER, which go to NaN ("never fired") on the game's negative clock; link caches (method handles into other
 * mods), constant tables and the few listed in JTResetCheck.KEEP stay for the session. JTResetCheck fails on any new static
 * that isn't reset here or listed there. Each class is its own try, so one failing never keeps the rest; an optional mod's
 * class is only touched when that mod is on (it's never loaded otherwise, so it has nothing to clear).
 */
public class CAReset {
    /** Runs every reset; returns how many failed (each is logged). */
    public static int onLoad() {
        int failed = 0;
        failed += run("pass clock and steps", () -> { CATime.reset(); CAStep.reset(); });
        failed += run("daily pass", () -> CAAuto.reset());
        failed += run("action breaker", () -> CABreaker.reset());
        failed += run("game constants", () -> CAConst.reset());
        failed += run("step timings", () -> CAPerf.reset());
        failed += run("probe measurements", () -> CASimulator.reset());
        failed += run("engine caches", () -> { CAEngine.resetCaches(); CAPlan.resetCaches(); });   // A4: the spec list, CACache memos, CAPlan's memos
        // its error memory; the scan results themselves are replaced by CADiagnostics.runScan at the end of the load (A5)
        failed += run("diagnostics", () -> CADiagnostics.reset());
        failed += run("integration health", () -> CACompat.brokenClasses.clear());   // CACompat.check fills it again in runScan
        failed += run("saved data tidy-up", () -> CATidy.reset());
        failed += run("industry limit", () -> CAOverLimit.reset());
        // the weekly timers 0.2.45 ran on the session's own clock (0.2.45.1 reset these in CAAuto.reset). 0.3.0.0 (A1 fix): back to
        // NaN, "never fired", not -99. The pass now hands them the game's own day, and the game clock counts from 1970, so a
        // c206+ day is about -640,000: `now - (-99) < 7` was true every day and storage logistics, calibration and the market
        // check never ran again. NaN fires the first time under that test and under CATime.due alike
        failed += run("calibration", () -> CACalibrate.last = Float.NaN);
        failed += run("storage logistics", () -> CALogistics.lastRun = Float.NaN);
        failed += run("market model", () -> { CAMarket.invalidate(); CAMarket.cacheDay = -99f; CAMarket.lastWeek = Float.NaN; });
        failed += run("mod watch", () -> CAModWatch.reset());
        if (on("niko_stationAugments")) failed += run("station augments", () -> { CAAugments.nextRun.clear(); CAAugments.lastSig.clear(); });
        // caches and cooldowns that held the other save's data (07 H6)
        failed += run("research ranking", () -> {
            CAResearch.cached = null; CAResearch.cachedAt = -99f; CAResearch.openCount = 0; CAResearch.itemTechs.clear();
            CAResearch.needs = new ArrayList<CAResearch.Need>(); CAResearch.topUpPending = false; CAResearch.spendItems = false; CAResearch.itemMode = 0;
        });
        failed += run("blocked plan steps", () -> { CAPlan.BLOCKED.clear(); CAPlan.lockedCache = 0; CAPlan.lockedDay = -1; });
        failed += run("trade contracts", () -> {
            CAContracts.cached = null; CAContracts.cachedAt = -1f; CAContracts.contractedCache = null; CAContracts.contractedAt = -1f; CAContracts.taken.clear();
        });
        if (on("assortment_of_things")) failed += run("settlement sites", () -> { CASettlement.cache = null; CASettlement.cachedAt = -999f; });
        if (on("nexerelin")) {
            failed += run("defense fleet cooldowns", () -> { CADefense.lastSent.clear(); CADefense.warned.clear(); });
            failed += run("relief fleet cooldowns", () -> { CAWar.lastRelief.clear(); CAWar.warned.clear(); CAWar.summary = null; });
        }
        failed += run("colony crises", () -> {
            CACrisis.meterDay = -1f; CACrisis.meterRaw = 0f; CACrisis.meterMult = 1f; CACrisis.meterCap = 60; CACrisis.meterLive = false;
            CACrisis.targets.clear(); CACrisis.targetsDay = -1f;   // it held the last save's colonies
        });
        // the rest, so a load starts exactly like a fresh session
        failed += run("surplus investment", () -> { CASurplus.reset(); CASurplus.committedCache = 0f; CASurplus.aotdWarned = false; });
        failed += run("special projects", () -> {
            CAProjects.reset(); CAProjects.yours.clear(); CAProjects.sector.clear(); CAProjects.computing = false;
        });
        failed += run("idle items", () -> { CAItems.idle = null; CAItems.idleAt = -99f; });
        failed += run("surplus research", () -> { CASurplusResearch.summary = null; });
        failed += run("capital", () -> CACapital.reset());
        failed += run("terraforming plans", () -> CATerraSim.reset());
        if (on("terraformingmadeeasy")) failed += run("surplus terraforming", () -> CATerraformSurplus.planConds.clear());
        failed += run("endgame economy", () -> { CAEndgame.lastReplaceDay = -99f; CAEndgame.summary = null; CAEndgame.chains = null; });
        failed += run("scoring", () -> {
            CAEngine.noProbe = false; CAEngine.profitOnly = false; CAEngine.supplier = null; CAEngine.supplierAt = null; CAEngine.unitValue = 100f;
            CAEngine.forDisplay = false; CAEngine.drawing = 0;   // 0.3.0.0 (A4 fix): the panel-score flags; SHOWN is cleared by resetCaches
        });
        failed += run("synergy scoring", () -> CASynergies.scoring = null);   // 0.3.0.0 (B1 fix): the pick CASynergies.score works out (null outside it)
        failed += run("low profile", () -> { CALowProfile.ttDay = -1f; CALowProfile.tt = new LinkedHashMap<String, int[]>(); });
        failed += run("prune", () -> CAPrune.looked.clear());
        failed += run("colony policies", () -> CAPolicies.lastCap = 0f);
        failed += run("fast buildout offer", () -> CABuildout.checkTimer = 0f);
        failed += run("siege check", () -> { CASiege.cache.clear(); CASiege.cachedDay = -1f; });
        failed += run("TASC", () -> { CATasc.availCache.clear(); CATasc.availDay = -1; CATasc.cache.clear(); CATasc.cacheDay.clear(); });
        failed += run("wonders", () -> CAWonders.summary = null);
        if (on("IndEvo")) failed += run("courier port", () -> CACourier.refused.clear());
        if (on("IndEvo")) failed += run("edicts held for stability", () -> CAIndEvo.edictHeld.clear());   // WS-A2: retried after a load
        failed += run("log", () -> CALog.reset());
        failed += run("log places", () -> { CATheme.places = null; CATheme.placesDay = -1f; });
        failed += run("colony screen button", () -> CAScreenButton.reset());   // its diagnostics counters and the dialog lookup (A5)
        failed += run("storage tab", () -> CAStoragePlugin.none = null);
        return failed;
    }

    static int run(String what, Runnable r) {
        try { r.run(); return 0; }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "reset on load failed: " + what, t); return 1; }
    }

    /** Whether an optional mod is on; with no game settings (offline checks), true, so everything is reset. */
    static boolean on(String modId) {
        try { return Global.getSettings().getModManager().isModEnabled(modId); }
        catch (Throwable t) { return true; }
    }
}
