package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Approve and Auto modes: the daily pass (runPass). 0.3.0.0 (WS-B3): what gets built is the planner's (CAPlanner: one queue for
 * every colony, event-driven, NPV per construction day and per credit); the 0.2.45 build pass below (one pass per colony
 * every 7 days or when a build finishes, funded empire-wide by score per credit) only runs with the hidden legacyPlanner
 * switch, or when the planner stopped for the session. Starting a pick (execute), proposals and approvals stay here.
 */
public class CAAuto {
    /** Picks per colony a pass: the best upgrades and new builds, the rest standing by if the best has to wait. */
    static final int UPS = CATuning.PASS_UPGRADES, NEWS = CATuning.PASS_NEW;   // 0.3.0.0 (A3): in CATuning
    static final Map<String, Boolean> wasBuilding = new HashMap<String, Boolean>();
    static final Map<String, Float> nextPass = new HashMap<String, Float>();
    static float lastNow = 0f;

    static class Cand {
        MarketAPI m; CAData.Colony c; CAEngine.Pick p; float total;
        CAEndgame.Weakest remove;   // the endgame takes this industry down first, to free its slot
        float gain;                 // what ranks it: the score, less what a replaced industry was worth
    }

    /**
     * The pass's own state. 0.3.0.0 (A1): the other classes' caches and timers 0.2.45.1 cleared here (calibration, storage
     * logistics, the market check, the mod watch, augment re-plans, prune, the market model) are cleared by CAReset.onLoad,
     * with every other static, before this runs; the augment timers only when Station Augments is on.
     */
    public static void reset() {
        wasBuilding.clear(); nextPass.clear(); lastTrace.clear(); lastNow = 0f;
        rejectedId.clear(); rejectedUntil.clear();
        CAPlanner.reset();   // 0.3.0.0 (B3): the planner's offers, events, earmark and upkeep hold
        CAFloors.reset();    // 0.3.2.0: the production floors are measured again on the first pass after a load
    }

    /**
     * 0.3.0.0 (B3 fix): what you rejected, per colony: the pick's id and the day its hold ends (session only, cleared on load
     * like nextPass). The planner's colony events (a construction starting or finishing, a slot, a threat change) gave the
     * colony a fresh look within days and proposed the same pick again; 0.2.45.1 held it 30 days.
     */
    static final Map<String, String> rejectedId = new HashMap<String, String>();
    static final Map<String, Float> rejectedUntil = new HashMap<String, Float>();

    /** Remembers a rejection at a colony for REJECT_HOLD days. 0.3.3.0: also as a saved mark, so a load keeps the hold. */
    static void noteRejected(String marketId, String pickId, float now) {
        if (marketId == null || pickId == null) return;
        rejectedId.put(marketId, pickId);
        rejectedUntil.put(marketId, now + REJECT_HOLD);
        CAMark.set(REJECT_MARK + marketId + "|" + pickId, CAMark.now());
    }

    /** 0.3.3.0: the saved hold of a rejected pick: "buildno:<marketId>|<pickId>" (CAMark, per colony, tidied with the colony). */
    static final String REJECT_MARK = "buildno:";

    /** 0.3.3.0: days left of a saved rejection hold (0: none, or over; an old mark is cleared). */
    static float markedDays(String marketId, String pickId) {
        Long at = CAMark.at(REJECT_MARK + marketId + "|" + pickId);
        if (at == null) return 0f;
        float left = REJECT_HOLD - CAMark.days(at, CAMark.now());
        if (left > 0f) return left;
        CAMark.clear(REJECT_MARK + marketId + "|" + pickId);
        return 0f;
    }

    /** Pure: days left of your rejection of this pick at this colony, 0 when none (over, another pick, or a date from a later save). */
    static float rejectedDays(String marketId, String pickId, float now) {
        if (marketId == null || pickId == null) return 0f;
        if (!pickId.equals(rejectedId.get(marketId))) return markedDays(marketId, pickId);
        Float until = rejectedUntil.get(marketId);
        if (until == null) return 0f;
        float left = until - now;
        return left > 0f && left <= REJECT_HOLD + 1f ? left : 0f;
    }

    /** Longest wait Autopilot sets before a colony's next look: 30 days after you reject a proposal. */
    static final float MAX_WAIT = 30f;

    /** 0.3.6.4: days a rejected pick is held at its colony (180). The colony still looks again after MAX_WAIT. */
    static final float REJECT_HOLD = CATuning.ASK_HOLD_DAYS;

    /**
     * Pure cadence rule (07 H4; 0.3.0.0 pulled it out so JTStepCheck tests it): a colony gets a pass when a build finished
     * since the last look (0.2.45.1: not while one is still under way), when it has no date yet, when its date has come, or
     * when its date is further off than any wait Autopilot sets (the clock went back: an earlier save was loaded).
     */
    static boolean due(Boolean was, boolean building, Float next, float now) {
        if (Boolean.TRUE.equals(was) && !building) return true;
        if (next == null) return true;
        return now >= next || next - now > MAX_WAIT + 1f;
    }

    /** Days to the next look: under siege daily so it carries on as soon as it ends; spending freely daily too, so the surplus keeps moving. */
    static float wait(boolean siege, boolean rich) { return siege || rich ? 1f : 7f; }

    /**
     * The daily pass. 0.3.0.0 (A0): every step runs through CAStep, in a fixed order with a slot for each 0.3.0 engine, so the
     * workstreams filling the stubs never reorder this method: daily upkeep and the siege check, the industry limit and
     * 0-stability safety net, the planner (B) next to the 0.2.45 build pass, the money tiers (C), Auto-expansion (D), then the
     * steps as in 0.2.45, with diplomacy and conquest (E) after the war fleets. A step that throws never skips the steps after it.
     * 0.3.0.0 (A1): `now` is game days from the sector clock (CATime); CAStep times every step, logs a failure's stack trace
     * once, and stops a step that failed three times in a row for the session (CAStep.run). The safety net, the colony lists
     * it reads and the steps CABreaker already stops use CAStep.guard, which never stops; so do the steps with stability or
     * emergency duties (administrators past the colony limit, emergency defense and autonomy, the free port close below the
     * stability floor, hazard pay off in an emergency, the siege check): 0.3.0.0 (A0 fix) a daily step hit the breaker after three bad days and
     * then left them undone for the session, against "never 0 stability". The month roll-over and the colony
     * lists are steps too (07 H3: they sat outside any try, so one throw there skipped the whole day).
     * Steps that call an optional mod's integration class check isModEnabled inside their own lambda (audit_combos.ps1).
     */
    public static void runPass(final float now) {
        lastNow = now;
        final CAData d = CAData.get();
        final CASettings s = d.settings;
        final com.fs.starfarer.api.ModManagerAPI mods = Global.getSettings().getModManager();
        CAApprovals.beginPass();   // 0.3.3.0: today's steps offer their live asks again
        CAStep.guard("story", () -> CASync.daily());   // 0.3.6.0 story mode: the Autarch AI Core's timers, the settings holds and the colony modes
        CAStep.run("month roll-over", () -> CABudget.rollMonth(s));
        CAStep.run("upkeep cap check", () -> CABudget.migrateUpkeepCap(s));
        CAStep.run("calibration", () -> CACalibrate.run(now));   // weekly: compare estimates with what your buildings really do
        CAStep.run("mod watch", () -> CAModWatch.weekly(now));   // weekly: how each colony mod is doing, into the Diagnostics report (0.2.38.0)
        CAStep.run("saved data tidy-up", () -> CATidy.weekly(d));   // weekly: expired log keys, year-old stamps, leftovers of colonies that are gone (0.3.0.0, 07 L7/L8/M1)
        CAStep.run("market model check", () -> CAMarket.weekly(now));   // weekly: the market model against the game's own shares, and where you hold most (0.2.44.0)
        // 0.3.0.0 (A1 fix): guarded, never stopped for the session: the siege start and end lines are how you learn a colony
        // is held to defenses only, and 0.2.45.1 retried them every day
        CAStep.guard("siege check", () -> CASiege.update(d));
        CAStep.guard("station line ask", () -> CAStationAsk.daily());   // 0.3.6.3: once, before the first station
        CAStep.run("Autopilot Storage shipping", () -> CAStores.daily());   // shipments between colonies' Autopilot Storage arrive
        if (CASync.dormant()) return;   // 0.3.6.0 story mode: nothing else runs while the Autopilot is dormant
        CAStep.run("storage logistics", () -> CALogistics.daily(d, s, now));   // storage logistics: loads to the refit and sell hubs (0.2.39.0)
        CAStep.run("colony crisis step", () -> CACrisis.daily(d, s));   // inspection orders, and a log line when a colony crisis is picked
        CAStep.run("surplus investment step", () -> CASurplus.daily(s));   // the focus world for credits over the surplus line
        CAStep.run("capital step", () -> CACapital.daily(d, s));   // the capital: deliveries there, and its commerce build-up once colonies ramp up (0.2.42.0)
        CAStep.run("endgame economy step", () -> CAEndgame.daily(s));   // after spending freely is known: the endgame economy starts or ends
        CAStep.run("special project supply step", () -> CAProjects.update());   // before any plan is read: a special project's producers are plan steps
        CAStep.run("production floors step", () -> CAFloors.update());   // 0.3.2.0: before any plan is read: the floors, their producers as plan steps, the parts advice
        // the same lists as 0.2.45 made after the build pass (nothing in it changes a colony's mode or siege). 0.3.0.0 (A1): in a
        // guarded step, so a throw here can't skip the day; if it does throw, the lists stay empty and nothing is built today
        final List<MarketAPI> allAuto = new ArrayList<MarketAPI>(), auto = new ArrayList<MarketAPI>();
        final List<MarketAPI> managed = new ArrayList<MarketAPI>();   // every colony not Off, for the Nexerelin and Floating City steps
        CAStep.guard("colony lists", () -> {
            allAuto.addAll(CAItems.autoMarkets());
            auto.addAll(CASiege.free(allAuto));   // colonies under siege are left as they are
            for (MarketAPI m : Misc.getPlayerMarkets(false)) if (d.colony(m.getId()).mode != CAData.Mode.OFF) managed.add(m);
        });
        // more industries than slots (-5 stability), and colonies at 0 stability, sieges included (0.2.42.2). 0.3.0.0 (A1, 07 H3):
        // before anything is built today, after the steps its ranking reads (plans, special projects, the capital, the
        // endgame), and never stopped for the session: it's the "never 0 stability" safety net
        CAStep.guard("industry limit / 0-stability step", () -> CAOverLimit.run(allAuto));
        // 0.3.2.1: Rogue Planets' Viable Slave Labor: unrest months and the rebellion that takes the colony (guarded: a safety net too)
        if (mods.isModEnabled(CARogue.MOD)) CAStep.guard("Rogue Planets step", () -> CARogue.daily(d, allAuto));
        // 0.3.0.0 slots: the planner (WS-B), the money tiers and their rungs (WS-C, monthly inside), Auto-expansion (WS-D).
        // 0.3.0.0 (B3): the planner starts every build, upgrade, surplus terraforming project, Research Facility, wonder site,
        // stable-location crew and story point improvement from one queue (CAPlanner); the modules' own steps below keep
        // their summaries, clean-ups and deliveries. With the hidden legacyPlanner switch, or once the planner stopped for the
        // session after three failed days, the 0.2.45 build pass and the modules' own spending take over again (inCharge).
        // 0.3.0.0 (B3 fix): the plan's own TME projects and the paid core, item and administrator moves below don't offer
        // actions yet (0.3.0.x); they ask CAPlanner.setAside before they spend, so they keep to what the planner saves up and keeps
        CAStep.run("planner", () -> CAPlanner.run(d, s, auto));
        CAStep.run("build pass", () -> { if (!CAPlanner.inCharge()) buildPass(d, s, now); });
        CAStep.run("money tiers", () -> CARich.run(d, s));
        CAStep.run("expansion", () -> CAExpansion.run(d, s, auto));
        CAStep.run("prune step", () -> CAPrune.run(auto));   // industries that stopped paying for 30 days, not under siege (0.2.44.1)
        CAStep.run("low profile step", () -> CALowProfile.run(d, s, auto));   // Low profile: items and AI administrators over its lines (0.2.43.0)
        // 0.3.0.0 (A0 fix): administrators for colonies past the governed limit (a stability penalty without one), so a guarded
        // step that's never stopped for the session; the storage tabs are their own step, so a failure there can't skip it
        CAStep.run("Autopilot Storage tabs", () -> CAItems.ensureStorageTabs());
        CAStep.guard("item, core or administrator placement", () -> CAItems.run(auto));
        CAStep.run("Autopilot Storage clean-up", () -> CAStoragePlugin.evict());   // campaign consumables and blueprints don't belong in Autopilot Storage
        if (!auto.isEmpty() && mods.isModEnabled("terraformingmadeeasy")) {
            CAStep.run("terraforming planner", () -> { if (mods.isModEnabled("terraformingmadeeasy") && CACompat.linked("CATerraform")) CATerraform.run(auto); });
            // after the plan's own projects, so it only takes structures they leave idle
            CAStep.run("surplus terraforming", () -> { if (mods.isModEnabled("terraformingmadeeasy") && CACompat.linked("CATerraformSurplus")) CATerraformSurplus.run(auto); });
        }
        if (mods.isModEnabled(CAResearch.MOD)) {
            CAStep.run("surplus research", () -> CASurplusResearch.run(auto));
            CAStep.run("wonders and gates step", () -> CAWonders.run());
        }
        if (!auto.isEmpty() && mods.isModEnabled("IndEvo")) {
            CAStep.run("Industrial Evolution automation", () -> { if (mods.isModEnabled("IndEvo") && CACompat.linked("CAIndEvo")) CAIndEvo.run(auto); });
            CAStep.run("Courier Port shipping", () -> { if (mods.isModEnabled("IndEvo") && CACompat.linked("CACourier")) CACourier.run(auto); });
        }
        if (mods.isModEnabled("nexerelin")) {
            // 0.3.0.0 (A0 fix): emergencies keep trying every day (guarded, never stopped for the session)
            CAStep.guard("emergency defense step", () -> { if (mods.isModEnabled("nexerelin") && CACompat.linked("CADefense")) CADefense.run(managed); });
            // 0.3.0.0 (A1): CABreaker already stops these after three failures in a row, so they use CAStep.guard (timing and
            // quiet logs, no second breaker and no second "stopped" line)
            if (CABreaker.allowed(CAWar.MOD + ":war")) {   // after emergencies, so they get fleet request points first
                boolean ok = CAStep.guard("war fleet step", () -> { if (mods.isModEnabled("nexerelin") && CACompat.linked("CAWar")) CAWar.run(managed); });
                CABreaker.check(CAWar.MOD + ":war", ok, "Nexerelin war fleets");
            }
            if (CABreaker.allowed(CASpies.MOD + ":agents")) {
                boolean ok = CAStep.guard("agent step", () -> { if (mods.isModEnabled("nexerelin") && CACompat.linked("CASpies")) CASpies.run(d, s); });
                CABreaker.check(CASpies.MOD + ":agents", ok, "Nexerelin agents");
            }
            // 0.3.0.0 slots (WS-E, stubs): ceasefire and peace answers by stance, then looking after conquered colonies
            if (CABreaker.allowed("nexerelin:diplomacy")) {
                boolean ok = CAStep.guard("diplomacy step", () -> { if (mods.isModEnabled("nexerelin") && CACompat.linked("CADiplomacy")) CADiplomacy.run(d, s); });
                CABreaker.check("nexerelin:diplomacy", ok, "Nexerelin diplomacy");
            }
            // 0.3.0.0 (fix): its own breaker key; "nexerelin:conquest" is CAWar's retake and conquer (CAWar.CONQUERED_KEY)
            if (CABreaker.allowed("nexerelin:conquered")) {
                boolean ok = CAStep.guard("conquered colonies step", () -> { if (mods.isModEnabled("nexerelin") && CACompat.linked("CAConquest")) CAConquest.tend(d, s); });
                CABreaker.check("nexerelin:conquered", ok, "Nexerelin conquered colonies");
            }
            CAStep.guard("emergency autonomy step", () -> { if (mods.isModEnabled("nexerelin") && CACompat.linked("CAAutonomy")) CAAutonomy.run(d, s); });
        }
        // holds changes under siege itself, but still switches off in an emergency. 0.3.0.0 (A0 fix): guarded, never stopped for
        // the session: it closes free ports below the stability floor and stops hazard pay in a cash emergency
        CAStep.guard("colony policy step", () -> CAPolicies.run(allAuto));
        CAStep.run("story point step", () -> CAStory.run(auto));
        CAStep.run("stable location step", () -> CAObjectives.run(d, s, auto));   // stable locations: comm relays, nav buoys, sensor arrays, receivers (0.2.40.0)
        if (mods.isModEnabled(CATasc.MOD)) {
            CAStep.run("TASC station step", () -> CATascStations.run(d, s, auto));   // TASC mining and siphon stations (0.2.40.1)
        }
        if (mods.isModEnabled(CASettlement.MOD)) {
            CAStep.run("settlement step", () -> { if (mods.isModEnabled(CASettlement.MOD) && CACompat.linked("CASettlement")) CASettlement.run(d, s); });
        }
        if (mods.isModEnabled(CAAugments.MOD)) {
            CAStep.run("station augment step", () -> { if (mods.isModEnabled(CAAugments.MOD) && CACompat.linked("CAAugments")) CAAugments.run(d, s, now); });
        }
        if (mods.isModEnabled(CAContracts.MOD)) {
            CAStep.run("trade contract step", () -> CAContracts.run(d, s));
        }
        if (mods.isModEnabled(CAHypershunt.MOD)) {
            CAStep.run("hypershunt step", () -> CAHypershunt.run(d, s));
            CAStep.run("megastructure step", () -> CAMegastructures.run(d, s));
            CAStep.run("Floating City check", () -> CADevelopment.run(managed));
            CAStep.run("research step", () -> CAResearch.run(d, s));
        }
        if (CATasc.enabled()) {
            CAStep.run("TASC terraforming step", () -> CATasc.run(CASiege.free(Misc.getPlayerMarkets(false))));
        }
        CAStep.run("panel scores", () -> CAEngine.fill(d));   // 0.3.0.0 (A4 fix, 07 M6): the panels show what's kept and never evaluate, so every colony not Off gets its Colonies-page score kept here (none kept yet: always; out of date: the 3 oldest a day); it also tracks Advisor colonies' plan steps once a day, since the build pass never evaluates them (A4 fix)
        CAStep.run("diagnostics report", () -> CADiagnostics.flushReport());   // a quarantine marks the report; it's written once a pass (0.3.0.0, 07 H5)
    }

    /**
     * The 0.2.45 build engine, unchanged: evaluates each Approve and Auto colony (every 7 days, or when a build finishes) and
     * starts the best picks empire-wide by score per credit. 0.3.0.0: its own step, so a failure here never skips the steps
     * after it; 0.3.0.0 (B3) only while the planner isn't in charge (legacyPlanner, or the planner stopped for the session,
     * CAPlanner.inCharge). 0.3.0.0 (A1): the cadence rule is due() and
     * wait(), and a colony's or a candidate's failure logs its stack trace once, then a short line weekly (CAStep.warn).
     */
    static void buildPass(CAData d, CASettings s, float now) {
        List<Cand> cands = new ArrayList<Cand>();
        boolean rich = CASurplus.rich(s);
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            try {   // 0.2.45.1: one colony's failure never skips the rest of the pass
                CAData.Colony c = d.colony(m.getId());
                if (c.buildout && c.mode != CAData.Mode.AUTO) CABuildout.stop(m, c, "the colony left Auto");
                if (c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) continue;
                boolean building = Misc.getCurrentlyBeingConstructed(m) != null;
                Boolean was = wasBuilding.put(m.getId(), building);
                // a build that finished since the last look gets a pass now; 0.2.45.1: one still under way isn't "just finished",
                // which had every colony with something under construction re-planned every day
                if (!due(was, building, nextPass.get(m.getId()), now)) continue;
                boolean siege = CASiege.held(m);
                nextPass.put(m.getId(), now + wait(siege, rich));
                if (c.buildout && c.mode == CAData.Mode.AUTO) {
                    if (siege) continue;   // a buildout builds no defenses, so it waits out the siege
                    try { if (CABuildout.pass(m, c, now, building)) continue; }
                    catch (Throwable t) { CAStep.warn("build pass", "fast buildout pass failed at " + m.getName(), t); continue; }
                }
                CAEngine.Result r;
                CAData.Profile used;
                try {
                    CAData.Profile prof = CAThreat.effective(m, c.profile);   // keeps the threat state current
                    used = siege ? CAData.Profile.SECURITY : prof;
                    r = CAEngine.evaluate(m, used, d.settings.minStability);
                }
                catch (Throwable t) { CAStep.warn("build pass", "pass failed at " + m.getName(), t); continue; }
                // the endgame economy: with every industry slot taken, the best industry it has no slot for may replace the weakest
                CAEndgame.Weakest wk = null;
                if (c.mode == CAData.Mode.AUTO && !building && !siege && r.blocked == null && CAEngine.worthwhile(r.bestNoSlot)) {
                    try { wk = CAEndgame.replacement(m, c, r.bestNoSlot, used); }
                    catch (Throwable t) { CAStep.warn("build pass", "endgame replacement check failed at " + m.getName(), t); }
                }
                // the capital: with every industry slot taken, a trade building may replace its weakest ordinary industry (0.2.42.0)
                if (wk == null && c.mode == CAData.Mode.AUTO && !building && !siege && r.blocked == null && CAEngine.worthwhile(r.bestNoSlot)) {
                    try { wk = CACapital.replacement(m, c, r.bestNoSlot, used); }
                    catch (Throwable t) { CAStep.warn("build pass", "capital room check failed at " + m.getName(), t); }
                }
                if (wk != null) {
                    Cand k = new Cand();
                    k.m = m; k.c = c; k.p = r.bestNoSlot; k.remove = wk; k.gain = r.bestNoSlot.score - wk.keep;
                    k.total = k.p.cost + CABudget.constructionFee(s, k.p.cost);
                    cands.add(k);
                }
                if (r.picks.isEmpty()) continue;
                // upgrades run alongside new construction; the next best of each stand by in case the best has to wait (budget,
                // caps), so one held-back build doesn't stop the colony
                List<CAEngine.Pick> ups = new ArrayList<CAEngine.Pick>(), news = new ArrayList<CAEngine.Pick>();
                for (CAEngine.Pick q : r.picks) {
                    if (!CAEngine.worthwhile(q)) continue;   // never the least-bad option: only what pays for itself
                    if (siege && !CASiege.defensive(q)) continue;   // under siege only defenses are built
                    if (q.upgrade) { if (ups.size() < (rich ? UPS + CATuning.PASS_RICH_UPGRADES : UPS)) ups.add(q); }
                    else if (!building && news.size() < NEWS) news.add(q);
                }
                if (ups.isEmpty() && news.isEmpty() && wk == null) trace(m, r, building, now);
                if (c.mode == CAData.Mode.APPROVE) {
                    CAEngine.Pick bestUp = ups.isEmpty() ? null : ups.get(0), bestNew = news.isEmpty() ? null : news.get(0);
                    CAEngine.Pick p = bestNew != null && (bestUp == null || bestNew.score >= bestUp.score) ? bestNew : bestUp;
                    if (p != null) propose(m, c, p, CAPlanner.APPROVE_MODE);
                    continue;
                }
                List<CAEngine.Pick> all = new ArrayList<CAEngine.Pick>(ups);
                all.addAll(news);
                for (CAEngine.Pick p : all) {
                    Cand k = new Cand();
                    k.m = m; k.c = c; k.p = p; k.total = p.cost + CABudget.constructionFee(s, p.cost); k.gain = p.score;
                    cands.add(k);
                }
                // 0.2.45.1: no daily re-check while a build is under way; the colony gets a pass the day it finishes (due() above)
            } catch (Throwable t) { CAStep.warn("build pass", "pass failed at " + m.getName(), t); }
        }
        Collections.sort(cands, new Comparator<Cand>() {
            public int compare(Cand a, Cand b) {
                return Float.compare(b.gain / Math.max(1f, b.total), a.gain / Math.max(1f, a.total));
            }
        });
        // a plan step whose upkeep is over the whole cap only fits as the month's first new upkeep, so it goes first
        List<Cand> big = new ArrayList<Cand>();
        for (Cand k : cands) if (k.p.planStep && k.p.upkeep > CASurplus.upkeepCap(k.m, s)) big.add(k);
        cands.removeAll(big);
        cands.addAll(0, big);
        // one new building and one upgrade per colony a pass; spending freely, up to three upgrades (never two on one building)
        Set<String> startedNew = new HashSet<String>();
        Map<String, Integer> startedUp = new HashMap<String, Integer>();
        Set<Object> upgrading = new HashSet<Object>();
        int maxUps = rich ? CATuning.PASS_RICH_MAX_UPGRADES : CATuning.PASS_MAX_UPGRADES;   // 0.3.0.0 (A3): in CATuning
        // 0.2.44.0: goods a colony started making this pass; another colony's pick that's mostly worth the same goods waits a day
        // and is scored again with that building counted (CAMarket), so two colonies don't start the same producer at once
        Map<String, String> claimedBy = new HashMap<String, String>();
        for (Cand k : cands) {
            try {   // 0.2.45.1: one candidate's failure never skips the rest (0.3.0.0: nor any step after the build pass)
                Integer ups = startedUp.get(k.m.getId());
                if (k.p.upgrade ? (ups != null && ups >= maxUps) || upgrading.contains(k.p.existing) : startedNew.contains(k.m.getId())) continue;
                if (!claimedBy.isEmpty() && k.remove == null) {
                    Set<String> theirs = new HashSet<String>();
                    for (Map.Entry<String, String> e : claimedBy.entrySet()) if (!e.getValue().equals(k.m.getId())) theirs.add(e.getKey());
                    if (!theirs.isEmpty() && CAMarket.claimedPart(k.p, theirs) >= 0.5f) {
                        CADiagnostics.LOG.info(CADiagnostics.TAG + k.m.getName() + ": " + k.p.name + " waits a day: another colony just started making most of what it's worth");
                        nextPass.put(k.m.getId(), now + 1f);
                        continue;
                    }
                }
                // 0.3.1.0: the same three rules as before, from CAPlanner.approval ("Approval needed: ..."); the focus world's cap there too
                String needs = CAPlanner.approval(k.m, k.p, s);
                if (needs != null) { propose(k.m, k.c, k.p, needs); continue; }
                String key = k.m.getId() + ":" + k.p.id;
                float avail = Math.min(Math.max(0f, CABuild.credits() - s.reserve), CABudget.available(s) + CASurplus.extra(k.m, s));
                if (k.total > avail) {
                    if (CALog.once(key)) CALog.think(k.m.getName() + ": " + k.p.name + " waits for budget (" + Misc.getDGSCredits(k.total) + ", "
                            + Misc.getDGSCredits(avail) + " left). Saving up.");
                    continue;
                }
                float upCap = CASurplus.upkeepCap(k.m, s);
                float addedUpkeep = k.p.upkeep - (k.remove != null ? k.remove.upkeep : 0f);   // a replaced industry's upkeep stops
                if (!CABudget.upkeepFits(s.upkeepAdded, addedUpkeep, upCap, k.p.planStep)) {
                    if (CALog.once(key + ":upkeep")) CALog.think(k.m.getName() + ": " + k.p.name + " waits for next month's upkeep room ("
                            + Misc.getDGSCredits(addedUpkeep) + " of " + Misc.getDGSCredits(upCap) + ").");
                    continue;
                }
                // the endgame economy gives up income only down to a third of what it was when it started
                boolean eg = CAEndgame.at(k.m);
                float loss = eg ? (k.remove != null ? k.remove.money : 0f) - k.p.fx[0] : 0f;
                if (eg && !CAEndgame.lossFits(s, loss)) {
                    if (CALog.once(key + ":endgame")) CALog.think(k.m.getName() + ": " + k.p.name + " waits. Income cost: " + Misc.getDGSCredits(loss)
                            + "/month. Endgame floor: " + Misc.getDGSCredits(CAEndgame.floor(s)) + "/month.");
                    continue;
                }
                if (k.remove != null) {
                    String why = CAEndgame.cantStart(k.m, k.p);
                    if (why != null) {
                        if (CALog.once(key + ":replace")) CALog.quiet(k.m.getName() + ": " + k.p.name + " would replace " + k.remove.ind.getCurrentName() + ", but " + why);   // 0.3.0.9: skipped, starsector.log only
                        continue;
                    }
                    try { if (!CAEndgame.takeDown(k.m, k.remove, k.p)) continue; }   // 0.3.0.0: CAStability held the swap (it logs why)
                    catch (Throwable t) { CAStep.warn("build pass", "endgame replacement failed at " + k.m.getName(), t); continue; }
                }
                if (execute(k.m, k.c, k.p, "Auto") == null) {
                    for (Map.Entry<String, Float> e : k.p.marketValue.entrySet()) if (e.getValue() > 0f && !claimedBy.containsKey(e.getKey())) claimedBy.put(e.getKey(), k.m.getId());
                    if (!k.p.upgrade) startedNew.add(k.m.getId());
                    else { startedUp.put(k.m.getId(), (ups == null ? 0 : ups) + 1); upgrading.add(k.p.existing); }
                    if (eg) { CAEndgame.charge(s, loss); CAEndgame.built(k.m, k.p); }
                }
            } catch (Throwable t) { CAStep.warn("build pass", "starting " + k.p.name + " failed at " + k.m.getName(), t); }
        }
    }

    static final Map<String, Float> lastTrace = new HashMap<String, Float>();
    static final float TRACE_DAYS = CATuning.TRACE_DAYS;

    /**
     * Why a colony starts nothing: its best three options with how each score is made up, and the best one that only lacks
     * an industry slot. Written to starsector.log (not the panel log) at most once a month per colony, so a stalled
     * colony can be explained from the log alone.
     */
    static void trace(MarketAPI m, CAEngine.Result r, boolean building, float now) {
        Float last = lastTrace.get(m.getId());
        if (last != null && !CATime.due(last, now, TRACE_DAYS)) return;   // 0.3.0.0: a stamp from a later date (an earlier save) never holds it
        lastTrace.put(m.getId(), now);
        StringBuilder b = new StringBuilder(m.getName()).append(": nothing worth starting");
        if (CASurplus.isFocus(m)) b.append(" (focus world, ").append(Misc.getDGSCredits(CASurplus.amount(CAData.settings()))).append(" surplus)");
        if (building) b.append("; a new building is under construction, so only upgrades count");
        if (r.blocked != null) b.append("; ").append(r.blocked);
        b.append("; ").append(r.picks.size()).append(" options");
        for (int i = 0; i < Math.min(3, r.picks.size()); i++) b.append(i == 0 ? ", best: " : "; ").append(scoreText(r.picks.get(i)));
        if (r.bestNoSlot != null) b.append("; with a free industry slot: ").append(scoreText(r.bestNoSlot)).append(" (").append(r.noSlot).append(" need a slot)");
        if (r.locked > 0) b.append("; ").append(r.locked).append(" locked by research");
        CADiagnostics.LOG.info(CADiagnostics.TAG + b);
        CALog.think(m.getName() + ": Scan: " + r.picks.size() + " options, holding." + (building ? " A build is under way." : "")
                + (r.picks.isEmpty() ? "" : " Best: " + r.picks.get(0).verb + " " + r.picks.get(0).name + "."));
    }

    static String scoreText(CAEngine.Pick p) {
        // 0.3.0.0 (B3): the follow-through only shows when it's there (legacyPlanner); (B2) the slot a plan industry holds when it costs one; the NPV over the horizon when there is one
        return p.verb + " " + p.name + " " + Math.round(p.score) + " (worth " + Math.round(p.worth) + ", plan " + Math.round(p.planBonus)
                + (p.followThrough != 0f ? ", follow-through " + Math.round(p.followThrough) : "") + (p.reserve != 0f ? ", slot -" + Math.round(p.reserve) : "") + ", price -" + Math.round(p.buildCost)
                + ", upkeep " + Math.round(p.upkeep) + "/month" + (p.horizon > 0f ? ", NPV " + Math.round(p.score * p.horizon) + " over " + Math.round(p.horizon) + " months" : "") + ")";
    }

    static void propose(MarketAPI m, CAData.Colony c, CAEngine.Pick p, String why) { propose(m, c, p, why, null); }

    /** 0.3.2.0: replace: the industry a make-room swap takes down on Approve (null: none). */
    static void propose(MarketAPI m, CAData.Colony c, CAEngine.Pick p, String why, String replace) {
        if (p.id.equals(c.pendingId) && (replace == null ? c.pendingReplace == null : replace.equals(c.pendingReplace))) return;
        c.pendingId = p.id; c.pendingName = p.verb + " " + p.name; c.pendingUpgrade = p.upgrade; c.pendingCost = p.cost;
        c.pendingReplace = replace;
        c.pendingNotified = true;
        // 0.3.3.0: the summary's reason line and money, and when it was proposed
        c.pendingWhy = why + (p.lines.isEmpty() ? "" : ". " + p.lines.get(0));
        c.pendingMonthly = p.fx[0];
        c.pendingSince = CAMark.now();
        // 0.3.3.0: on screen only with the summary Off; else the Thinking list, and the summary asks
        CAApprovals.say(m.getName() + ": Proposal: " + c.pendingName + " for " + Misc.getDGSCredits(p.cost) + ". " + why + ".", false);
    }

    /** Starts a pick, charging the fee and counting it against the budget. Returns null on success. */
    static String execute(MarketAPI m, CAData.Colony c, CAEngine.Pick p, String who) { return execute(m, c, p, who, false); }

    /** 0.3.3.0: quiet: your own Build click is logged to starsector.log only (the panel shows what started). */
    static String execute(MarketAPI m, CAData.Colony c, CAEngine.Pick p, String who, boolean quiet) {
        CASettings s = CAData.settings();
        float fee = CABudget.constructionFee(s, p.cost), price = CASync.buildPrice(p.cost);   // 0.3.6.0 story mode: Free construction
        if (CABuild.credits() < price + fee) return "Not enough credits: build plus " + Misc.getDGSCredits(fee) + " fee";
        String err = CABuild.start(m, p);
        if (err != null) {
            // 0.3.0.9: a failed start is starsector.log only. An approval you clicked gets the error back from approve(), and
            // the colony panel and the intel show it
            CALog.quiet(m.getName() + ": could not start " + p.name + ": " + err);
            return err;
        }
        CABudget.charge(s, m.getId(), price, fee, p.upkeep);   // 0.3.0.0: booked under this colony for the monthly colony lines
        try { CAMarket.claim(m, p); }   // what it'll make counts as made while it's under construction (0.2.44.0)
        catch (Throwable t) { CAStep.warn("build pass", "market model claim failed at " + m.getName(), t); }
        CALog.forget(m.getId() + ":" + p.id);
        CALog.forget(m.getId() + ":" + p.id + ":upkeep");
        CAData.get().clearPending(c);
        wasBuilding.put(m.getId(), true);
        String line = m.getName() + ": " + (p.upgrade ? "Upgrading to " : "Building ") + p.name + " (" + Misc.getDGSCredits(p.cost) + ")."
                + ("Auto".equals(who) ? "" : " " + (who.endsWith(":") ? who.substring(0, who.length() - 1) : who) + ".");
        if (quiet) CALog.quiet(line); else CALog.did(line);
        return null;
    }

    /** Approves a colony's pending proposal: re-checks it is still the right pick, then starts it. */
    public static String approve(MarketAPI m) {
        CAData d = CAData.get();
        CAData.Colony c = d.colony(m.getId());
        if (c.pendingId == null) return "Nothing to approve";
        // 0.3.3.0: the credit reserve is not spent, whoever approves
        String gate = CAApprovals.reserveGate(c.pendingCost + CABudget.constructionFee(d.settings, c.pendingCost));
        if (gate != null) return gate;
        CAData.Profile prof = CAThreat.effective(m, c.profile);
        CAEngine.Result r = CAEngine.evaluate(m, prof, d.settings.minStability);
        if (r.blocked != null && !c.pendingUpgrade) return r.blocked;
        // 0.3.2.0 (Batch E): a make-room swap: the industry comes down, then the floor producer goes up, each checked first
        if (c.pendingReplace != null) {
            for (CAEngine.Pick p : r.floorNoSlot) {
                if (!p.id.equals(c.pendingId) || p.upgrade != c.pendingUpgrade) continue;
                String now = reserveNow(d, p);   // 0.3.6.2: today's price, not the proposal's
                if (now != null) return now;
                String err = CAFloors.swap(m, c, p, c.pendingReplace, prof);
                if (err != null) return err;
                return execute(m, c, p, "Approved:");
            }
            d.clearPending(c);
            return "Proposal out of date. Cleared";
        }
        for (CAEngine.Pick p : r.picks) {
            if (!p.id.equals(c.pendingId) || p.upgrade != c.pendingUpgrade) continue;
            String now = reserveNow(d, p);   // 0.3.6.2: today's price, not the proposal's
            return now != null ? now : execute(m, c, p, "Approved:");
        }
        d.clearPending(c);
        return "Proposal out of date. Cleared";
    }

    /** 0.3.6.2: the reserve check on the price the pick has today (the proposal's price can be up to a summary interval old). */
    static String reserveNow(CAData d, CAEngine.Pick p) {
        float price = CASync.buildPrice(p.cost);
        return CAApprovals.reserveGate(price + CABudget.constructionFee(d.settings, p.cost));
    }

    public static void reject(MarketAPI m) {
        CAData d = CAData.get();
        CAData.Colony c = d.colony(m.getId());
        if (c.pendingName != null) CALog.did(m.getName() + ": Proposal rejected: " + c.pendingName + ".");
        String id = c.pendingId;
        d.clearPending(c);
        // 0.3.0.0 (A1): from today on the game clock; lastNow was 0 until the first pass after a load, so a rejection then wasn't held
        float now = CATime.now();
        nextPass.put(m.getId(), now + MAX_WAIT);
        noteRejected(m.getId(), id, now);   // the planner doesn't propose or start it there for REJECT_HOLD days (180), whatever wakes the colony
    }

    public static MarketAPI market(String id) {
        try { return Global.getSector().getEconomy().getMarket(id); } catch (Throwable t) { return null; }
    }
}
