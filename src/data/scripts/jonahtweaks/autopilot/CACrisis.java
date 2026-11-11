package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.PerseanLeagueMembership;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.EventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HAColonyDefensesFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HegemonyAICoresActivityCause;
import com.fs.starfarer.api.impl.campaign.intel.events.HegemonyHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.LuddicChurchHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.LuddicPathHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.PerseanLeagueHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.PirateHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.RemnantHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.SindrianDiktatHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.SindrianDiktatStandardActivityCause;
import com.fs.starfarer.api.impl.campaign.intel.events.TriTachyonHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.TriTachyonStandardActivityCause;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.HA_CMD;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Colony crises (vanilla Hostile Activity). Each month the crisis meter gains every faction's progress, times your best
 * military structure (HAColonyDefensesFactor: Patrol HQ x0.9, Military Base x0.7, High Command x0.5), capped at
 * ha_maxMonthlyProgress (60). At 500 the next crisis is picked (frequency x (0.1 + 0.9 x that faction's share of the
 * progress)) and at 600 it fires; then the meter falls to a random 0-400, a third of that on average. A month with no
 * progress at all empties the meter (BaseEventIntel.reportEconomyTick).
 *
 * Autopilot prices progress in credits so AI cores and buildings can weigh it: one point a month brings 1/467 of a crisis
 * a month, and a crisis is held to cost CRISIS_COST. Cores pay for the Hegemony progress and Pather interest they add,
 * a Military Base or High Command counts what it slows, and Fuel Production counts the Sindrian Diktat's progress. The
 * colony the coming crisis is likely to hit plans with Security (CAThreat), since an expedition that meets in-system
 * defenses about 1.34 times its strength while you're away aborts, which ends most crises for good; a coming Pather
 * attack also pulls cores from its target until its cell can dissolve, and a coming Nexerelin police raid closes free
 * ports. Deals, fights and memberships stay yours; the Crises page says what each one takes. Nothing here skips the
 * game's own rules (no avert helpers or flags).
 */
public class CACrisis {
    /** Rough cost of one crisis (weeks of disruption, stability, lost fleets); only used to price progress. */
    static final float CRISIS_COST = 300000f;
    /** Points between crises: the meter falls to 400/3 on average after one, and the next fires at 600. */
    static final float POINTS_PER_CRISIS = 600f - 400f / 3f;

    /** Credits a month one point of monthly crisis progress costs (about 640; ten times that in Low profile, 0.2.43.0). */
    public static float pointCost() { return CRISIS_COST / POINTS_PER_CRISIS * (CALowProfile.on() ? CALowProfile.PRICE_MULT : 1f); }

    public static HostileActivityEventIntel event() {
        try { return HostileActivityEventIntel.get(); } catch (Throwable t) { return null; }
    }

    /** Monthly progress before the military multiplier and the cap: every faction's own (not one-time events). */
    public static int rawMonthly(HostileActivityEventIntel ha) {
        int total = 0;
        for (EventFactor f : ha.getFactors()) if (!f.isOneTime()) total += f.getProgress(ha);
        return total;
    }

    /** The multiplier on all progress: your best military structure, and any mod's. */
    public static float allMult(HostileActivityEventIntel ha) {
        float mult = 1f;
        for (EventFactor f : ha.getFactors()) if (!f.isOneTime()) mult *= f.getAllProgressMult(ha);
        return mult;
    }

    /** Progress the meter really gets a month from raw progress: none at 0 or less (it empties), else raw x mult up to the cap. */
    static float effective(float raw, float mult, int cap) {
        return raw <= 0f ? 0f : Math.min(cap, raw * mult);
    }

    /** The game's diminishing returns for Hegemony and Pather progress (unit 10, x0.75 past each unit; the remainder is an int). */
    static int diminish(float raw, float unit, float mult) {
        int progress = Math.round(raw), rem = progress;
        float adjusted = 0f;
        while (rem > unit) { adjusted += unit; rem -= unit; rem = (int) (rem * mult); }
        adjusted += rem;
        int reduced = Math.round(adjusted);
        return progress > 0 && reduced < 1 ? 1 : reduced;
    }

    static float setting(String key, float dflt) {
        try { return Global.getSettings().getFloat(key); } catch (Throwable t) { return dflt; }
    }

    static int hegemonyProgress(float points) { return diminish(points, setting("hegemonyProgressUnit", 10f), setting("hegemonyProgressMult", 0.75f)); }

    static int pathProgress(float interest) { return diminish(interest, setting("patherProgressUnit", 10f), setting("patherProgressMult", 0.75f)); }

    /**
     * The empire's crisis state as AI core pricing needs it, read once per pass: the meter's progress, multiplier and cap,
     * whether the Hegemony and the Pathers count, the Hegemony's points (colonies above size 3), and each colony's Pather
     * interest with its cell multiplier, as modelled by cellMult.
     */
    public static class Base {
        public boolean live, heg = true, path = true;
        public float raw, mult = 1f;
        public int cap = 60, hegPoints;
        public float pathRaw;
        public final Map<MarketAPI, Float> interest = new HashMap<MarketAPI, Float>();
        public final Set<MarketAPI> sleeper = new HashSet<MarketAPI>();
        public float minInterest = 7f, sleeperMult = 0.25f, activeMult = 1f;
    }

    static float meterDay = -1f, meterRaw, meterMult = 1f;
    static int meterCap = 60;
    static boolean meterLive;

    /** Just the meter: progress, multiplier and cap, read once a day (every faction's progress takes a scan of the sector). */
    public static Base meter() {
        float day = -1f;
        try { day = CADay.now(); } catch (Throwable ignored) { }
        if (!CATime.fresh(meterDay, day, 1f)) {   // 0.3.0.0 (A1 fix): day < 0 held on the game's negative clock, so it rescanned the sector every call
            meterDay = day;
            meterLive = false;
            HostileActivityEventIntel ha = event();
            if (ha != null) {
                try { meterRaw = rawMonthly(ha); meterMult = allMult(ha); meterCap = ha.getMaxMonthlyProgress(); meterLive = true; }
                catch (Throwable t) { meterLive = false; }
            }
        }
        Base b = new Base();
        b.live = meterLive; b.raw = meterRaw; b.mult = meterMult; b.cap = meterCap;
        return b;
    }

    public static Base base() {
        Base b = meter();
        b.heg = hegemonyCounts();
        b.path = pathCounts();
        b.minInterest = setting("minInterestForPatherCells", 7f);
        b.sleeperMult = setting("patherProgressMultSleeperCells", 0.25f);
        b.activeMult = setting("patherProgressMultActiveCells", 1f);
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m.getSize() > HegemonyAICoresActivityCause.IGNORE_COLONY_THRESHOLD) b.hegPoints += CAItems.aiPoints(m);
            float i = 0f;
            try { i = LuddicPathBaseManager.getLuddicPathMarketInterest(m); } catch (Throwable ignored) { }
            b.interest.put(m, i);
            try { LuddicPathCellsIntel c = LuddicPathCellsIntel.getCellsForMarket(m); if (c != null && c.isSleeper()) b.sleeper.add(m); }
            catch (Throwable ignored) { }
        }
        for (MarketAPI m : b.interest.keySet()) b.pathRaw += b.interest.get(m) * cellMult(b, m, b.interest.get(m));
        return b;
    }

    /** Credits a month that monthly raw progress going from `from` to `to` costs, after the multiplier and the cap. */
    static float progressCost(Base b, float from, float to) {
        if (b == null || !b.live) return (to - from) * pointCost();   // no crises yet: they start with your first colony
        return (effective(b.raw - from + to, b.mult, b.cap) - effective(b.raw, b.mult, b.cap)) * pointCost();
    }

    // ---------------- the Hegemony ----------------

    /** Whether AI cores add Hegemony progress now: not after you've beaten its inspections, in the Persean League, or without a Hegemony military base. */
    public static boolean hegemonyCounts() {
        try {
            if (HegemonyHostileActivityFactor.isPlayerDefeatedHegemony()) return false;
            if (PerseanLeagueMembership.isLeagueMember()) return false;
            return BaseHostileActivityFactor.checkFactionExists(Factions.HEGEMONY, true);
        } catch (Throwable t) { return true; }
    }

    /** Credits a month a core of `pts` points at m costs in Hegemony progress, from the empire without `without` points there. Nothing at size 3 or less. */
    public static float hegemonyCost(Base b, MarketAPI m, int pts, int without) {
        if (pts <= 0 || !b.heg || m.getSize() <= HegemonyAICoresActivityCause.IGNORE_COLONY_THRESHOLD) return 0f;
        float from = Math.max(0, b.hegPoints - without);
        return progressCost(b, hegemonyProgress(from), hegemonyProgress(from + pts));
    }

    // ---------------- the Luddic Path ----------------

    /** Whether Pather interest adds progress: not with the tithe agreement or after their attack was beaten. */
    public static boolean pathCounts() {
        try { return !HA_CMD.playerHasPatherAgreement() && !LuddicPathHostileActivityFactor.isPlayerDefeatedPatherExpedition(); }
        catch (Throwable t) { return true; }
    }

    /**
     * How much of a colony's Pather interest counts (StandardLuddicPathActivityCause2): only a colony with a cell, and a
     * cell needs size 4 or more and minInterestForPatherCells (7). Modelled as: under that, none; a sleeper cell, 0.25;
     * otherwise an active cell, since a colony that qualifies is likely to get one.
     */
    static float cellMult(Base b, MarketAPI m, float interest) {
        if (m.getSize() < 4 || interest < b.minInterest) return 0f;
        return b.sleeper.contains(m) ? b.sleeperMult : b.activeMult;
    }

    /** Credits a month a core of `pts` interest at m costs in Pather progress, from the colony without `without` interest. */
    public static float pathCost(Base b, MarketAPI m, int pts, int without) {
        if (pts <= 0 || !b.path) return 0f;
        Float now = b.interest.get(m);
        float interest = now == null ? 0f : now;
        float i0 = Math.max(0f, interest - without), i1 = i0 + pts;
        float others = b.pathRaw - interest * cellMult(b, m, interest);
        return progressCost(b, pathProgress(others + i0 * cellMult(b, m, i0)), pathProgress(others + i1 * cellMult(b, m, i1)));
    }

    /**
     * The most AI core points the target of a coming Pather attack can keep so its interest falls under 7 and its cell
     * dissolves before the attack fires (cells go 10-30 days after their colony stops qualifying); -1 if m isn't it, or
     * if its items and buildings alone keep it at 7.
     */
    public static int pathReliefLimit(MarketAPI m) {
        MarketAPI t = pendingMarket(LuddicPathHostileActivityFactor.class);
        if (t != m) return -1;
        float interest = LuddicPathBaseManager.getLuddicPathMarketInterest(m);
        float other = interest - CAItems.aiPoints(m);   // Pather interest from cores is the same 10/4/2/1 the Hegemony counts
        return reliefLimit(other, setting("minInterestForPatherCells", 7f));
    }

    /** The most core points that keep interest under min with `other` interest from everything else; -1 if nothing can. */
    static int reliefLimit(float other, float min) {
        return other >= min ? -1 : (int) Math.ceil(min - other) - 1;
    }

    /** One line for a colony's overview: whether the Hegemony counts its cores, and its Pather interest. */
    public static String coreLine(MarketAPI m) {
        try {
            boolean small = m.getSize() <= HegemonyAICoresActivityCause.IGNORE_COLONY_THRESHOLD;
            String heg = !hegemonyCounts() ? "The Hegemony's crisis doesn't count cores now"
                    : small ? "At size " + m.getSize() + " the Hegemony ignores its cores" : "Above size 3 the Hegemony counts every core here toward its crisis";
            float i = LuddicPathBaseManager.getLuddicPathMarketInterest(m), min = setting("minInterestForPatherCells", 7f);
            String path = !pathCounts() ? "" : "; Pather interest " + Misc.getRoundedValueMaxOneAfterDecimal(i)
                    + (m.getSize() < 4 ? " (no cells below size 4)" : i >= min ? ", enough for a Pather cell" : ", a cell needs " + (int) min);
            int relief = pathReliefLimit(m);
            return heg + path + "." + (relief >= 0 ? " A Pather attack is coming for it: cores held to " + relief + " points so its cell can dissolve." : "");
        } catch (Throwable t) { return null; }
    }

    // ---------------- military structures ----------------

    /** Multiplier on all crisis progress a building gives (HAColonyDefensesFactor), or 1. */
    static float militaryMult(String id) {
        if (id.equals("highcommand")) return HAColonyDefensesFactor.HIGH_COMMAND_MULT;
        if (id.equals("militarybase")) return HAColonyDefensesFactor.MILITARY_BASE_MULT;
        if (id.equals("patrolhq")) return HAColonyDefensesFactor.PATROL_HQ_MULT;
        return 1f;
    }

    /** Your best military structure's multiplier now. */
    static float bestMilitaryMult() {
        try { return new HAColonyDefensesFactor().getDefenseData(null).mult; } catch (Throwable t) { return 1f; }
    }

    /** Credits a month a building saves in crises if it becomes your best military structure (from `now` to its multiplier). */
    static float militarySaving(Base b, String id, float now) {
        float to = militaryMult(id);
        if (to >= now || b == null || !b.live) return 0f;
        float others = b.mult / now;
        return (effective(b.raw, others * now, b.cap) - effective(b.raw, others * to, b.cap)) * pointCost();
    }

    /** Adds what a Patrol HQ, Military Base or High Command saves in crises to a build or upgrade pick. */
    public static void military(String id, CAEngine.Pick p) {
        float to = militaryMult(id);
        if (to >= 1f) return;
        float now = bestMilitaryMult();
        float saved = militarySaving(meter(), id, now);
        if (saved <= 0f) return;
        p.fx[0] += saved;
        p.lines.add("Your best military structure: colony crises build up x" + Misc.getRoundedValueMaxOneAfterDecimal(to)
                + " instead of x" + Misc.getRoundedValueMaxOneAfterDecimal(now) + ", worth about " + Misc.getDGSCredits(saved) + " a month");
    }

    // ---------------- the Sindrian Diktat ----------------

    /** Diktat progress from fuel (SindrianDiktatStandardActivityCause): the player's largest fuel output, once it's 4 or more and a producer is size 4 or more. */
    static int diktatProgress(int maxProd, int maxSize) {
        if (maxProd < SindrianDiktatStandardActivityCause.MIN_COMPETITOR_PRODUCTION || maxSize < SindrianDiktatStandardActivityCause.MIN_COMPETITOR_MARKET_SIZE) return 0;
        return Math.round(maxProd * setting("diktatProgressPerUnitProdMult", 1f));
    }

    /** Subtracts the Diktat progress a building making `fuel` units at m would add, when the Diktat is still counting fuel. */
    public static void diktat(MarketAPI m, int fuel, CAEngine.Pick p) {
        if (fuel <= 0) return;
        try {
            if (SindrianDiktatHostileActivityFactor.isMadeDeal() || SindrianDiktatHostileActivityFactor.isPlayerDefeatedDiktatAttack()) return;
            if (!BaseHostileActivityFactor.checkFactionExists(Factions.DIKTAT, true)) return;
            TriTachyonStandardActivityCause.CompetitorData d = SindrianDiktatStandardActivityCause.computeCompetitorData(Factions.DIKTAT, Factions.PLAYER, Commodities.FUEL);
            if (d.factionMaxProd < SindrianDiktatStandardActivityCause.MIN_DIKTAT_PRODUCTION) return;
            int before = diktatProgress(d.competitorMaxProd, d.competitorMaxMarketSize);
            int after = diktatProgress(Math.max(d.competitorMaxProd, fuel), Math.max(d.competitorMaxMarketSize, m.getSize()));
            if (after <= before) return;
            float cost = progressCost(meter(), before, after);
            p.fx[0] -= cost;
            p.lines.add("Competes with the Sindrian Diktat's fuel: +" + (after - before) + " colony crisis progress a month, about "
                    + Misc.getDGSCredits(cost) + " a month");
        } catch (Throwable ignored) { }
    }

    // ---------------- the coming crisis ----------------

    /** The crisis picked to fire at 600, or null. */
    public static HostileActivityEventIntel.HAERandomEventData pending() {
        HostileActivityEventIntel ha = event();
        if (ha == null) return null;
        try {
            HostileActivityEventIntel.HAERandomEventData r = ha.getRollDataForEvent();
            return r != null && r.factor != null ? r : null;
        } catch (Throwable t) { return null; }
    }

    /** The colony a coming crisis of that kind has picked (Hegemony, Path, Remnant), or null. */
    static MarketAPI pendingMarket(Class<?> kind) {
        HostileActivityEventIntel.HAERandomEventData r = pending();
        if (r == null || !kind.isInstance(r.factor)) return null;
        return marketOf(r.custom);
    }

    static MarketAPI marketOf(Object custom) {
        if (custom instanceof MarketAPI) return (MarketAPI) custom;
        if (custom instanceof LuddicPathHostileActivityFactor.HAPatherCellsEventData) return ((LuddicPathHostileActivityFactor.HAPatherCellsEventData) custom).market;
        return null;
    }

    static final Set<MarketAPI> targets = new HashSet<MarketAPI>();
    static float targetsDay = -1f;

    /**
     * Your colonies the coming crisis is likely to hit: the colony it picked when it names one, else the colonies in your
     * system where that faction's activity is strongest (pirate, Diktat, Tri-Tachyon and League targets follow it).
     */
    public static Set<MarketAPI> targets() {
        float day = CADay.now();
        if (day - targetsDay < 1f && day >= targetsDay) return targets;
        targetsDay = day;
        targets.clear();
        try {
            HostileActivityEventIntel.HAERandomEventData r = pending();
            if (r == null) return targets;
            MarketAPI m = marketOf(r.custom);
            if (m != null) { if (m.isPlayerOwned()) targets.add(m); return targets; }
            StarSystemAPI best = null;
            float top = 0f;
            for (StarSystemAPI sys : Misc.getPlayerSystems(false)) {
                float mag = r.factor.getEffectMagnitude(sys);
                if (mag > top) { top = mag; best = sys; }
            }
            if (best != null) for (MarketAPI o : Misc.getMarketsInLocation(best, Factions.PLAYER)) targets.add(o);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "crisis target check failed", t); }
        return targets;
    }

    /** True when the coming crisis is Nexerelin's police raid on free ports. */
    public static boolean policeComing() {
        HostileActivityEventIntel.HAERandomEventData r = pending();
        return r != null && isPolice(r.factor);
    }

    static boolean isPolice(Object factor) { return factor != null && factor.getClass().getName().endsWith("PoliceHostileActivityFactor"); }

    /** A free port's score for Nexerelin's police (PoliceHostileActivityFactor.getFreePortScore): size + drugs + weapons - 2, counted from 5. */
    public static int policeScore(MarketAPI m) {
        int score = m.getSize();
        try {
            int drugs = Math.max(m.getCommodityData(Commodities.DRUGS).getMaxSupply(), m.getCommodityData(Commodities.DRUGS).getMaxDemand() / 2);
            if (drugs > 1) score += drugs;
            int guns = m.getCommodityData(Commodities.HAND_WEAPONS).getMaxSupply();
            if (guns > 1) score += guns;
        } catch (Throwable ignored) { }
        score -= 2;
        return score < 5 ? 0 : score;
    }

    /** Daily: Hegemony inspection orders, and one log line when a crisis is picked. */
    public static void daily(CAData d, CASettings s) {
        try { inspections(d, s); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "inspection orders failed", t); }
        try {
            HostileActivityEventIntel.HAERandomEventData r = pending();
            String key = r == null ? null : "crisis:" + r.factor.getClass().getSimpleName();
            CALog.forgetAll("crisis:", key);   // 0.3.0.0: once-keys live in CAData.notedAt now (CALog, 07 M1)
            if (key != null && CALog.once(key)) {
                List<String> names = new ArrayList<String>();
                for (MarketAPI m : targets()) names.add(m.getName());
                CALog.add("Colony crisis coming: " + name(r.factor) + (names.isEmpty() ? "" : ", most likely at " + CAContracts.join(names))
                        + ". Auto colonies there plan with Security until it's over; see the Crises page.", true);
            }
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "crisis check failed", t); }
    }

    // ---------------- Hegemony inspections ----------------

    /** The orders Autopilot gives: resist only with Resist where defended, at a colony that beats the inspection, over the game's default of complying. */
    static HegemonyInspectionIntel.AntiInspectionOrders orders(int mode, boolean beat, HegemonyInspectionIntel.AntiInspectionOrders now) {
        if (mode == 1 && beat && now == HegemonyInspectionIntel.AntiInspectionOrders.COMPLY) return HegemonyInspectionIntel.AntiInspectionOrders.RESIST;
        return now;
    }

    /**
     * Inspections of Auto colonies, before the task force is in the system (the game's own orders screen locks then).
     * Complying, the game's default, hands over every core there. Advise logs what's coming; Resist where defended
     * resists at a colony whose defenses beat the inspection by CAItems.defended's margin: the Hegemony turns hostile,
     * and three beaten inspections end them for good (INSPECTIONS_TO_DEFEAT). Each inspection is looked at once, so
     * orders you change afterwards stand.
     */
    static void inspections(CAData d, CASettings s) {
        if (s.inspectionMode == 2) return;
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(HegemonyInspectionIntel.class)) {
            if (i.isEnded() || i.isEnding()) continue;
            HegemonyInspectionIntel hi = (HegemonyInspectionIntel) i;
            MarketAPI m = hi.getTarget();
            if (m == null || !m.isPlayerOwned() || hi.isEnteredSystem() || hi.getOutcome() != null) continue;
            if (d.colony(m.getId()).mode != CAData.Mode.AUTO || CAEngine.isOutpost(m)) continue;
            if (!CALog.once("inspect:" + m.getId() + ":" + HegemonyHostileActivityFactor.getInspectionAttempts())) continue;   // each inspection adds an attempt
            int fp = Math.round(CAItems.nextInspectionFP()), def = Math.round(CAItems.defenseFP(m));
            // Low profile never resists: resisting turns the Hegemony hostile (0.2.43.0)
            HegemonyInspectionIntel.AntiInspectionOrders was = hi.getOrders(), now = orders(CALowProfile.on() ? 0 : s.inspectionMode, CAItems.defended(m), was);
            if (now != was) {
                hi.setOrders(now);
                CALog.add(m.getName() + ": a Hegemony AI inspection is coming; its defenses (about " + def + " fleet points) beat the "
                        + fp + " sent, so it will resist. The Hegemony turns hostile when they arrive; beat three and they stop for good.", true);
            } else {
                CALog.add(m.getName() + ": a Hegemony AI inspection is coming (" + fp + " fleet points; defenses about " + def + "). "
                        + (was == HegemonyInspectionIntel.AntiInspectionOrders.COMPLY
                        ? "Complying hands over every core there; you can resist or bribe from the inspection's intel before it arrives."
                        : "Your orders stand."), true);
            }
        }
    }

    // ---------------- the Crises page ----------------

    static String name(HostileActivityFactor f) {
        if (isPolice(f)) return "Nexerelin's police raid on free ports";
        if (f instanceof PirateHostileActivityFactor) return "a pirate raid";
        if (f instanceof LuddicPathHostileActivityFactor) return "a Luddic Path attack";
        if (f instanceof PerseanLeagueHostileActivityFactor) return "a Persean League blockade";
        if (f instanceof SindrianDiktatHostileActivityFactor) return "a Sindrian Diktat attack";
        if (f instanceof TriTachyonHostileActivityFactor) return "a Tri-Tachyon mercenary raid";
        if (f instanceof LuddicChurchHostileActivityFactor) return "a Knights of Ludd takeover";
        if (f instanceof HegemonyHostileActivityFactor) return "a Hegemony AI inspection";
        if (f instanceof RemnantHostileActivityFactor) return "a Remnant attack";
        try { return f.getNameForThreatList(true); } catch (Throwable t) { return f.getClass().getSimpleName(); }
    }

    /** What drives a crisis, how it ends, and what Autopilot does about it. */
    static String advice(HostileActivityFactor f) {
        // 0.3.0.0 (WS-F3 fix pass 2): one line each, what drives it and how it ends (were 44-88 words); the numbers are in the guides
        if (isPolice(f)) return "Free ports scoring 5 or more. Beating the raid ends it.";
        if (f instanceof PirateHostileActivityFactor) return "Colony size, low stability and pirate bases nearby. Beating a major raid ends it.";
        if (f instanceof LuddicPathHostileActivityFactor) return "Pather interest of 7+ at size 4+ colonies. Beating the attack ends it.";
        if (f instanceof PerseanLeagueHostileActivityFactor) return "While you could join the League. Beat its Armada or join at Kazeron.";
        if (f instanceof SindrianDiktatHostileActivityFactor) return "A size 4+ colony making 4+ fuel. Beating the attack ends it.";
        if (f instanceof TriTachyonHostileActivityFactor) return "Goods made in competition with Tri-Tachyon. Raiding its commerce ends it.";
        if (f instanceof LuddicChurchHostileActivityFactor) return "Luddic-majority colonies. Beat the Knights' Armada or make the Church's deal.";
        if (f instanceof HegemonyHostileActivityFactor) return "AI cores above size 3. Three beaten inspections end it.";
        if (f instanceof RemnantHostileActivityFactor) return "A Remnant nexus in your system. Destroying it ends it.";
        return "Added by a mod.";
    }

    public static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor();
        CATheme.heading(t, "Colony crises", 0f);
        HostileActivityEventIntel ha = event();
        if (ha == null) { t.addPara("No crisis meter yet.", gray, 6f); return; }
        int raw = rawMonthly(ha), monthly = ha.getMonthlyProgress();
        float mult = allMult(ha);
        t.addPara("Meter: %s of %s, %s a month (%s before military x%s, max %s).", 6f, hl,
                "" + ha.getProgress(), "" + ha.getMaxProgress(), (monthly > 0 ? "+" : "") + monthly, "" + raw,
                Misc.getRoundedValueMaxOneAfterDecimal(mult), "" + ha.getMaxMonthlyProgress());
        // 0.3.0.0 (WS-F3 fix): one line (was 48 words); how the meter works and the price of a crisis on hover
        t.addPara("Each point a month is priced at %s a month.", 4f, gray, hl, Misc.getDGSCredits(pointCost()));   // 0.3.0.0 (WS-F3 fix pass 2): one line
        HostileActivityEventIntel.HAERandomEventData r = pending();
        if (r != null) {
            List<String> names = new ArrayList<String>();
            for (MarketAPI m : targets()) names.add(m.getName());
            t.addPara("Coming: %s" + (names.isEmpty() ? "." : ", most likely at %s; Auto colonies there plan with Security."), 8f, bad,
                    name(r.factor), CAContracts.join(names));
        }
        float best = bestMilitaryMult();
        t.addPara(best >= 1f ? "No military structure yet (they slow crises)."
                : "Your best military structure slows every crisis to x%s.", 4f, hl, Misc.getRoundedValueMaxOneAfterDecimal(best));
        CALowProfile.render(t, s);   // 0.2.43.0
        CAOddisz.render(t);   // Sol Renewed's own crisis, when it's running (0.2.45.0)

        CATheme.heading(t, "Where it comes from", 12f);
        boolean any = false;
        for (EventFactor f : ha.getFactors()) {
            if (!(f instanceof HostileActivityFactor) || f.isOneTime()) continue;
            int p = f.getProgress(ha);
            if (p == 0) continue;
            any = true;
            HostileActivityFactor hf = (HostileActivityFactor) f;
            String label;
            try { label = f.getDesc(ha); } catch (Throwable e) { label = name(hf); }
            t.addPara("%s: %s a month", 10f, p > 0 ? hl : Misc.getPositiveHighlightColor(), CALog.esc(label), (p > 0 ? "+" : "") + p);
            t.addPara(CALog.esc(advice(hf)), gray, 2f);
        }
        if (!any) t.addPara("Nothing is adding to it now.", gray, 6f);

        CATheme.heading(t, "Hegemony inspections", 12f);
        t.addPara("Inspection orders: %s. Sent: %s, beaten: %s of 3.", 6f, hl,
                CASettings.INSPECTION_NAMES[Math.max(0, Math.min(2, s.inspectionMode))].toLowerCase(),
                "" + HegemonyHostileActivityFactor.getInspectionAttempts(), "" + HegemonyHostileActivityFactor.getInspectionsDefeated());
        t.addPara("An inspection brings " + (int) CAItems.nextInspectionFP() + " fleet points.", gray, 4f);
    }
}
