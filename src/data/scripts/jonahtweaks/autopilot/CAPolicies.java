package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionIntel;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Colony policies for Auto colonies: hazard pay and free port, switched on and off by profile, budget, stability,
 * threat and emergencies (negative income, punitive expeditions). Minimum time between changes avoids flip-flopping.
 * 0.3.0.0: a free port opens only where CAStability says the colony can take its full penalty, and closes at once under the
 * one stability floor, max(3, minStability).
 *
 * Hazard pay buys growth (CoreImmigrationPluginImpl): it adds the colony's hazard penalty back plus 5 growth points a month,
 * at about 1,000 credits a point, and a colony needs 300 x 2^(size-3) points for the next size. Every goal wants growth
 * (bigger colonies make more, get more industry slots and reach their plans). Colonies are ranked by how much faster it
 * makes them grow (its points against their own monthly growth; a colony that doesn't grow on its own counts as the
 * fastest gain), and the best ones get it, as long as it speeds them up by at least the "faster growth" setting
 * (hazardPayColonyShare) and all of it fits the empire share of net income before hazard pay (hazardPayEmpireShare).
 * The more you earn, the more colonies get it.
 *
 * 0.3.0.0 (WS-B5, research 01 sections 7 and 12): the hazard pay budget is a share of colony net (CABudget.netIncome: the
 * colonies' own net, without hazard pay, as a 3-month median), so one bad month no longer switches it off everywhere (24
 * switches in Jonah's game). A free port that's off opens again only after everything it needs (stability with its
 * penalty to spare, no threat, no crisis, the profile) has held for FREE_PORT_REOPEN_DAYS in a row, on top of the 90 days
 * since its last change: an urgent close (stability, threat, a punitive expedition) used to flip back the same week
 * (37 switches, Zenemosrae 13).
 *
 * 0.3.0.0 (WS-C3 growth rung, research 03 F9 and section 5.3): with the money ladder's Growth rung open (CARich: the money
 * tier at Surplus or higher, the rung not Off, the preset funds it), the hazard pay budget is the larger of your setting and
 * the tier's share of colony net: Surplus 40%, Idle money 60%, Rich and Endgame no cap. Below Rich (and not spending
 * freely), a colony gets hazard pay only while it reaches its next size within GROW_MONTHS (24) months with it, or
 * GROW_MONTHS_SLOT (36) when that size adds an industry slot (PopulationAndInfrastructure.getMaxIndustries): Wind paid 41,000
 * a month for about 35 months to reach size 6. One already paying keeps it with a 25% margin, so it doesn't flip. With the
 * hidden legacyPlanner switch hazard pay is 0.2.45.1's rule.
 * 0.3.0.0 (C3 fix): only the rung On raises the budget, and the raised budget stays within what the rung could hold this month
 * (CARich.room: its share of the pool plus what came down), never below your setting. On Recommend only your setting
 * holds, and the rung's note names the colonies the raise would add. Hazard pay a month at your Auto colonies is the Growth
 * rung's want (growthWant): the game pays it from income at month end, and the rung's share holds that much of the pool so
 * the rest goes down. Spending freely (CASurplus.rich) keeps 0.2.45.1's rule: no cap.
 */
public class CAPolicies {
    static final float HAZARD_PAY_MIN_DAYS = CATuning.HAZARD_PAY_MIN_DAYS, FREE_PORT_MIN_DAYS = CATuning.FREE_PORT_MIN_DAYS;   // 0.3.0.0 (A3): in CATuning
    /** 0.3.0.0 (B5): days a closed free port's reasons to open must hold in a row before it opens (research 01 section 12). */
    static final float FREE_PORT_REOPEN_DAYS = CATuning.FREE_PORT_REOPEN_DAYS;
    /** 0.3.0.0 (B5): CAMark key for when a closed port's reasons to open began holding, by market id. */
    static final String PORT_OK = "fpok:";
    /** The hazard pay budget from the last hazardPlan, for the log's reasons. */
    static float lastCap = 0f;
    /** 0.3.0.0 (C3): the share of colony net that budget was (the setting, or the money tier's when the Growth rung raises it). */
    static float lastShare = 0f;
    /** 0.3.0.0 (C3 fix): hazard pay a month at your Auto colonies as of the last plan (NaN: no plan this session), the Growth rung's want. */
    static float lastCost = Float.NaN;
    /** 0.3.0.0 (C3 fix): Recommend only: the colonies the raise would add, as last written to starsector.log (session). */
    static String recommended;

    // ---------------- 0.3.0.0 (C3): the growth rung ----------------

    /** Hazard pay's share of colony net by money tier while the Growth rung is open: Tight the setting, Surplus 40%, Idle money 60%. */
    static final float[] TIER_SHARE = {0f, 0.40f, 0.60f};
    /** Hazard pay goes where the next size comes within this many months with it, or GROW_MONTHS_SLOT when that size adds an industry slot. */
    static final float GROW_MONTHS = 24f, GROW_MONTHS_SLOT = 36f;
    /** A colony already paying keeps it up to this much past those months, so it doesn't flip. */
    static final float GROW_KEEP = 1.25f;

    /**
     * Pure: the hazard pay budget's share of colony net: the setting; with the Growth rung open, at least the tier's share
     * (Rich and Endgame: Float.MAX_VALUE, no cap).
     */
    static float hazardShare(float setting, int tier, boolean growthRung) {
        if (!growthRung || tier <= CARich.T0) return setting;
        if (tier >= CARich.T3) return Float.MAX_VALUE;
        return Math.max(setting, TIER_SHARE[Math.min(tier, TIER_SHARE.length - 1)]);
    }

    /**
     * Pure, 0.3.0.0 (C3 fix): the hazard pay budget with the Growth rung On: the tier's budget (tierCap; Float.MAX_VALUE: no cap)
     * within what the rung could hold this month (room), and never below your setting's budget (settingCap).
     */
    static float raisedCap(float settingCap, float tierCap, float room) {
        return Math.max(Math.max(0f, settingCap), Math.min(tierCap, Math.max(0f, room)));
    }

    /** Pure, 0.3.0.0 (C3 fix): a share of colony net as a budget; Float.MAX_VALUE stays no cap. */
    static float tierCap(float share, float colonyNet) {
        return share >= Float.MAX_VALUE / 2f ? Float.MAX_VALUE : hazardCap(share, colonyNet);
    }

    /**
     * 0.3.0.0 (C3 fix): the Growth rung's want: hazard pay a month at your Auto colonies (the last plan's, or what's on now
     * before the first plan this session); 0 while you set hazard pay by hand (no buyer on the rung then).
     */
    static float growthWant(CASettings s) {
        if (s == null || s.hazardPayMode != 0) return 0f;
        if (!Float.isNaN(lastCost)) return Math.max(0f, lastCost);
        float c = 0f;
        try {
            for (MarketAPI m : CAItems.autoMarkets()) if (m.isImmigrationIncentivesOn()) c += Math.max(0f, m.getImmigrationIncentivesCost());
        } catch (Throwable t) { return 0f; }
        return c;
    }

    /** Pure: the months limit for a colony: GROW_MONTHS, GROW_MONTHS_SLOT when the next size adds a slot, x GROW_KEEP while it pays. */
    static float growLimit(boolean addsSlot, boolean on) {
        return (addsSlot ? GROW_MONTHS_SLOT : GROW_MONTHS) * (on ? GROW_KEEP : 1f);
    }

    /** Pure: hazard pay is worth it by time: the next size comes within the limit with it (months -1: it never gets there). */
    static boolean growsInTime(float months, boolean addsSlot, boolean on) {
        return months >= 0f && months <= growLimit(addsSlot, on);
    }

    /** Whether the colony's next size adds an industry slot (the game's table by size). */
    static boolean nextSizeAddsSlot(MarketAPI m) {
        try {
            int s = m.getSize();
            return com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure.getMaxIndustries(s + 1)
                    > com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure.getMaxIndustries(s);
        } catch (Throwable t) { return false; }
    }

    static boolean punitiveActive() {
        try {
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(PunitiveExpeditionIntel.class)) {
                if (!i.isEnded() && !i.isEnding()) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    static boolean canChange(long last, float minDays) {
        return last == 0L || Global.getSector().getClock().getElapsedDaysSince(last) >= minDays;   // 0.3.0.0 (A1 fix): 0 is never; a real stamp is negative
    }

    public static void run(List<MarketAPI> auto) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (!s.hazardLimitChecked) {   // saves from before 2.17 kept the old 10% default; 30% is the recommended limit since then
            s.hazardLimitChecked = true;
            if (Math.abs(s.hazardPayEmpireShare - 0.10f) < 0.005f) {
                s.hazardPayEmpireShare = CATuning.HAZARD_EMPIRE_SHARE;
                CALog.add("Hazard pay limit raised from the old default of 10% to " + Math.round(CATuning.HAZARD_EMPIRE_SHARE * 100f)
                        + "% of empire net income (Settings, Advanced, Colony policies, Hazard pay budget)", true);
            }
        }
        boolean emergency = CABudget.paused(s);
        boolean punitive = punitiveActive();
        long now = Global.getSector().getClock().getTimestamp();
        Map<MarketAPI, Growth> growth = new HashMap<MarketAPI, Growth>();
        // 0.3.0.0: a failed hazard pay plan leaves hazard pay as it is today, except the urgent emergency switch-off; the free port
        // checks still run
        Set<MarketAPI> hpWant = new HashSet<MarketAPI>();
        boolean hpPlanned = s.hazardPayMode != 0;
        try {
            if (s.hazardPayMode == 0) hpWant = hazardPlan(auto, s, emergency, growth);
            hpPlanned = true;
        } catch (Throwable t) { CAStep.warn("colony policy step", "hazard pay plan failed", t); }   // 0.3.0.0: a failed plan never skips the free-port checks
        for (MarketAPI m : auto) {
            try {   // 0.3.0.0: one colony's failure never blocks the urgent free port close at the others
                if (CAHypershunt.is(m)) continue;   // fixed at size 2 with no market to open, so neither policy helps
                CAData.Colony c = d.colony(m.getId());
                boolean threat = CAThreat.isHigh(m);
                CAData.Profile prof = threat ? CAData.Profile.SECURITY : c.profile;
                float stab = m.getStabilityValue();
                boolean siege = CASiege.held(m);   // its income is off while it lasts, so only urgent switch-offs

                // ---- hazard pay: buys growth with credits (hazardPlan picks the colonies) ----
                // without a plan only the urgent emergency switch-off runs; hazard pay is never turned off everywhere
                if (s.hazardPayMode == 0 && (hpPlanned || (emergency && m.isImmigrationIncentivesOn()))) {
                    try {
                        boolean on = m.isImmigrationIncentivesOn();
                        boolean want = hpWant.contains(m);
                        if (want != on) {
                            Growth g = growth.get(m);
                            boolean atMax = m.getSize() >= Misc.getMaxMarketSize(m);
                            String why = atMax ? "max size" : emergency ? "net income is negative" : CAMods.growthUnsafe(m) ? "Astral Ascension can't grow a colony without a planet past size 10"
                                    : g == null || g.points <= 0f ? "it adds no growth here"
                                    : g.speedup < s.hazardPayColonyShare * 0.8f ? "it would only speed growth up by " + pct(g.speedup) + ", under the " + pct(s.hazardPayColonyShare) + " setting"
                                    // 0.3.0.0 (C3): the months rule below Rich
                                    : g.slow ? slowText(m, g)
                                    : g.cost > lastCap * (on ? 1.1f : 1f) ? "the hazard pay budget is " + pct(lastShare) + " of colony net, "
                                            + Misc.getDGSCredits(lastCap) + ", and it costs " + Misc.getDGSCredits(g.cost) + " a month here; raise Hazard pay limit, all colonies, in Settings"
                                    : "the hazard pay budget (" + pct(lastShare) + " of colony net) goes to colonies it speeds up more";
                            boolean urgentOff = on && (atMax || emergency || why.startsWith("the hazard pay budget"));
                            if ((!siege && canChange(c.lastHazardPay, HAZARD_PAY_MIN_DAYS)) || urgentOff) {
                                m.setImmigrationIncentivesOn(want);
                                c.lastHazardPay = now;
                                CALog.status("hazard:" + m.getId(), m.getName() + ": hazard pay " + (want ? "on (" + growthText(m, g) + ")" : "off (" + why + ")"));
                            }
                        }
                    } catch (Throwable t) { CAStep.warn("colony policy step", "hazard pay failed at " + m.getName(), t); }
                }

                // ---- free port: accessibility and growth for stability and punitive-expedition risk ----
                if (s.freePortMode == 0) {
                    boolean on = m.isFreePort();
                    boolean luddic = m.hasCondition("luddic_majority");
                    // AotD's Underworld only works on a free port (it still costs upkeep without one), so it keeps the port open
                    boolean profileFits = m.hasIndustry("aotd_underworld") || prof == CAData.Profile.INCOME || prof == CAData.Profile.GROWTH || prof == CAData.Profile.BALANCED || prof == CAData.Profile.SELF;
                    // free port costs up to 3 stability (FreeMarket's penalty grows to that over a year); 0.3.0.0: opening one also asks
                    // CAStability, the one rule for anything that costs a colony stability
                    float margin = on ? 0f : com.fs.starfarer.api.impl.campaign.econ.FreeMarket.MAX_STABILITY_PENALTY;
                    boolean stableEnough = stab - margin >= d.settings.minStability + CATuning.FREE_PORT_STABILITY_MARGIN && (on || CAStability.canLose(m, -margin, null));
                    boolean shield = CAMods.shieldWantsClosedPort(m);   // Planetary Shield: Access Control keeps Pathers out only with the port closed
                    boolean police = CACrisis.policeComing() && CACrisis.policeScore(m) > 0;   // Nexerelin's police crisis is next: a closed port isn't a target
                    boolean low = CALowProfile.on();   // Low profile: a free port draws Nexerelin's police (0.2.43.0)
                    boolean want = profileFits && stableEnough && !luddic && !threat && !punitive && !emergency && !shield && !police && !low;
                    String why = low ? "low profile" : police ? "Nexerelin's police raid on free ports is the next colony crisis" : punitive ? "punitive expedition under way" : threat ? "threat is high" : luddic ? "Luddic majority" : !stableEnough ? "stability too low" : emergency ? "net income is negative" : shield ? "the planetary shield keeps Pather cells out only while the port is closed" : !profileFits ? "profile is " + prof.label : "";
                    boolean urgentOff = on && (punitive || threat || police || low || stab < CAStability.floor());   // 0.3.0.0: the one floor, max(3, minStability)
                    // 0.3.0.0 (B5, 01 section 12): a closed port opens only once its reasons have held FREE_PORT_REOPEN_DAYS in a row
                    boolean ready = true;
                    if (!on) {
                        String k = PORT_OK + m.getId();
                        if (!want) CAMark.clear(k);
                        else ready = held(CAMark.since(k, now), now, FREE_PORT_REOPEN_DAYS);
                    }
                    float sinceChange = c.lastFreePort == 0L ? Float.MAX_VALUE : Global.getSector().getClock().getElapsedDaysSince(c.lastFreePort);   // 0 is never
                    if (portChanges(on, want, urgentOff, ready, sinceChange, siege)) {
                        if (want) CAMark.clear(PORT_OK + m.getId());
                        m.setFreePort(want);
                        m.reapplyConditions();
                        c.lastFreePort = now;
                        CALog.status("freeport:" + m.getId(), m.getName() + ": free port " + (want ? "on" : "off (" + why + ")"));
                    }
                }
            } catch (Throwable t) { CAStep.warn("colony policy step", "policy check failed at " + m.getName(), t); }
        }
    }

    /** Pure, 0.3.0.0 (B5): a condition that has held since `since` (game timestamps) for `days` or more. */
    static boolean held(long since, long now, float days) { return CAMark.days(since, now) >= days; }

    /**
     * Pure, 0.3.0.0 (B5, research 01 section 12): whether a free port changes today. on/want: now and wanted; urgent: an urgent
     * close (bypasses every wait); ready: a closed port's reasons have held FREE_PORT_REOPEN_DAYS; sinceChange: days since its
     * last change; siege: the colony is under siege (only urgent closes then).
     */
    static boolean portChanges(boolean on, boolean want, boolean urgent, boolean ready, float sinceChange, boolean siege) {
        if (want == on) return false;
        if (urgent) return true;
        return !siege && (on || ready) && sinceChange >= FREE_PORT_MIN_DAYS;
    }

    static String pct(float f) { return Math.round(f * 100f) + "%"; }

    /**
     * What hazard pay buys at a colony: growth points a month, its own growth without them, the cost, and the speed-up.
     * 0.3.0.0 (C3): months to the next size with it (-1: never), whether that size adds an industry slot, and whether it fails
     * the months rule (slow).
     */
    static final class Growth {
        MarketAPI m;
        boolean on;
        float points, base, cost, speedup;
        float months = -1f;
        boolean addsSlot, slow;
    }

    /** 0.3.0.0 (C3): why a colony fails the months rule, for the hazard pay off line. */
    static String slowText(MarketAPI m, Growth g) {
        if (g.months < 0f) return "it doesn't reach size " + (m.getSize() + 1) + " even with it";
        return "size " + (m.getSize() + 1) + " would take about " + Math.round(g.months) + " months with it, over the " + Math.round(growLimit(g.addsSlot, false))
                + "-month line" + (g.addsSlot ? " for a size that adds an industry slot" : "");
    }

    /** Hazard pay points: the hazard penalty it cancels plus INCENTIVE_POINTS_EXTRA (CoreImmigrationPluginImpl.applyIncentives). */
    static float hazardPoints(MarketAPI m) {
        return -CoreImmigrationPluginImpl.getImmigrationHazardPenalty(m) + CoreImmigrationPluginImpl.INCENTIVE_POINTS_EXTRA;
    }

    /** The speed-up from points on top of base growth; a colony that doesn't grow on its own (base 0.5 or less) counts as 99x. */
    static float speedup(float points, float base) { return base <= 0.5f ? 99f : points / base; }

    static Growth growth(MarketAPI m) {
        Growth g = new Growth();
        g.m = m;
        g.on = m.isImmigrationIncentivesOn();
        g.points = hazardPoints(m);
        try {
            MutableStat w = m.getIncoming().getWeight();
            g.base = w.getModifiedValue();
            MutableStat.StatMod hp = w.getFlatStatMod("inc_incentives");
            if (hp != null) g.base -= hp.getValue();   // its growth without hazard pay
        } catch (Throwable ignored) { }
        g.cost = m.getImmigrationIncentivesCost();
        g.speedup = speedup(g.points, g.base);
        return g;
    }

    /** Months to the next size at this many growth points a month, or -1 if it doesn't get there. */
    static float monthsToGrow(MarketAPI m, float rate) {
        if (rate <= 0f) return -1f;
        float need = CoreImmigrationPluginImpl.getWeightForMarketSizeStatic(m.getSize() + 1) - m.getPopulation().getWeightValue();
        return Math.max(0f, need) / rate;
    }

    static String growthText(MarketAPI m, Growth g) {
        if (g == null) return "faster growth";
        float with = monthsToGrow(m, g.base + g.points), without = monthsToGrow(m, g.base);
        return "+" + Math.round(g.points) + " growth a month" + (g.base > 0.5f ? " on top of " + Math.round(g.base) + ", " + pct(g.speedup) + " faster" : "; it doesn't grow without it")
                + (with >= 0f ? "; size " + (m.getSize() + 1) + " in about " + Math.max(1, Math.round(with)) + " months" + (without >= 0f ? " instead of " + Math.round(without) : "") : "")
                + ", " + Misc.getDGSCredits(g.cost) + " a month";
    }

    /**
     * The colonies that should have hazard pay: those it speeds up the most, at least hazardPayColonyShare, until the
     * total reaches hazardPayEmpireShare of empire net income before hazard pay. One already paying keeps it with a
     * little leeway (80% of the speed-up, 110% of the budget), so it doesn't flip on and off. None in a money emergency.
     * 0.3.0.0 (B5): "net income before hazard pay" is colony net (CABudget.netIncome), which leaves hazard pay out already
     * and is a 3-month median; 0.2.45 added this month's hazard pay back to last month's whole net.
     */
    static Set<MarketAPI> hazardPlan(List<MarketAPI> auto, CASettings s, boolean emergency, Map<MarketAPI, Growth> info) {
        Set<MarketAPI> want = new HashSet<MarketAPI>();
        // spending freely (CASurplus.rich): wherever it speeds growth by 5% or more, paid for as long as the surplus lasts
        boolean rich = CASurplus.rich(s);
        // 0.3.0.0 (C3): the Growth rung raises the budget's share by money tier; the months rule applies below Rich
        boolean legacy = CABudget.legacy(s);
        int tier = legacy ? CARich.T0 : CARich.tier();
        boolean rung = !legacy && CARich.open(CARich.GROWTH);
        // 0.3.0.0 (C3 fix): only On raises it, within the rung's room this month; Recommend only keeps your setting and names
        // what the raise would add (the note)
        boolean raise = rung && CARich.mode(CARich.GROWTH) == CARich.ON;
        boolean recommend = rung && !raise;
        float net = CABudget.netIncome(s);
        float setCap = hazardCap(s.hazardPayEmpireShare, net);
        float share = legacy ? s.hazardPayEmpireShare : hazardShare(s.hazardPayEmpireShare, tier, raise);
        float cap = rich ? Float.MAX_VALUE : raise ? raisedCap(setCap, tierCap(share, net), CARich.room(CARich.GROWTH)) : setCap;
        boolean uncapped = cap >= Float.MAX_VALUE / 2f;
        float minShare = rich ? Math.min(0.05f, s.hazardPayColonyShare) : s.hazardPayColonyShare;
        boolean byTime = !legacy && !rich && tier < CARich.T3;
        lastCap = cap;
        lastShare = uncapped ? Float.MAX_VALUE : net > 0f ? cap / net : s.hazardPayEmpireShare;
        List<Growth> cands = new ArrayList<Growth>();
        for (MarketAPI m : auto) {
            if (CAHypershunt.is(m) || CAMods.growthUnsafe(m) || m.getSize() >= Misc.getMaxMarketSize(m)) continue;
            Growth g = growth(m);
            info.put(m, g);
            if (byTime) {
                g.addsSlot = nextSizeAddsSlot(m);
                g.months = monthsToGrow(m, g.base + g.points);
                g.slow = !growsInTime(g.months, g.addsSlot, g.on);
            }
            if (g.points <= 0f || g.speedup < minShare * (g.on ? 0.8f : 1f) || g.slow) continue;
            cands.add(g);
        }
        if (emergency) { lastCost = 0f; return want; }   // 0.3.0.0 (C3 fix): it goes off everywhere today
        Set<MarketAPI> picked = pick(cands, cap);
        // 0.3.0.0 (C3 fix): the Growth rung's want: what the plan pays a month, or what's on now while switches wait their days
        float on = 0f;
        for (Growth g : info.values()) if (g.on) on += Math.max(0f, g.cost);
        lastCost = Math.max(cost(picked, info), on);
        // 0.3.0.0 (C3 fix): Recommend only: the colonies the raise would add, for the note; nothing extra is paid
        String extra = null;
        if (recommend && !rich) {
            float would = raisedCap(setCap, tierCap(hazardShare(s.hazardPayEmpireShare, tier, true), net), CARich.room(CARich.GROWTH));
            if (would > cap + 1f) {
                Set<MarketAPI> more = pick(new ArrayList<Growth>(cands), would);
                more.removeAll(picked);
                if (!more.isEmpty()) {
                    List<String> names = new ArrayList<String>();
                    for (MarketAPI m : more) names.add(m.getName());
                    Collections.sort(names);
                    extra = "On would add " + CAContracts.join(names) + " (" + Misc.getDGSCredits(cost(more, info)) + " a month)";
                }
            }
            if (extra != null && !extra.equals(recommended)) CALog.quiet("Growth rung (Recommend only): " + extra);
            recommended = extra;
        }
        note(picked, info, uncapped ? -1f : lastShare, cap, extra);
        return picked;
    }

    /** 0.3.0.0 (C3 fix): hazard pay a month at these colonies. */
    static float cost(Set<MarketAPI> ms, Map<MarketAPI, Growth> info) {
        float c = 0f;
        for (MarketAPI m : ms) { Growth g = info.get(m); if (g != null) c += Math.max(0f, g.cost); }
        return c;
    }

    /**
     * 0.3.0.0 (C3): the Growth rung's Budget panel note: how many colonies get hazard pay, its cost a month, and the budget.
     * 0.3.0.0 (C3 fix): extra: on Recommend only, the colonies the raise would add (null: none).
     */
    static void note(Set<MarketAPI> picked, Map<MarketAPI, Growth> info, float share, float cap, String extra) {
        CARich.note(CARich.GROWTH, "hazard pay at " + picked.size() + (picked.size() == 1 ? " colony, " : " colonies, ") + Misc.getDGSCredits(cost(picked, info)) + " a month"
                + (share < 0f ? " (no cap at this money tier)" : " of " + Misc.getDGSCredits(cap) + " (" + pct(share) + " of colony net)")
                + (extra == null ? "" : "; Recommend only: " + extra));
    }

    /** Pure, 0.3.0.0 (B5): the empire's hazard pay budget: this share of colony net (which already leaves hazard pay out), never below 0. */
    static float hazardCap(float share, float colonyNet) { return share * Math.max(0f, colonyNet); }

    /** Greedy by speed-up (then cheaper first) within the budget; ones already paying get 10% leeway. */
    static Set<MarketAPI> pick(List<Growth> cands, float cap) {
        Collections.sort(cands, new Comparator<Growth>() {
            public int compare(Growth a, Growth b) { int c = Float.compare(b.speedup, a.speedup); return c != 0 ? c : Float.compare(a.cost, b.cost); }
        });
        Set<MarketAPI> want = new HashSet<MarketAPI>();
        float total = 0f;
        for (Growth g : cands) {
            if (total + g.cost > cap * (g.on ? 1.1f : 1f)) continue;
            total += g.cost;
            want.add(g.m);
        }
        return want;
    }
}
