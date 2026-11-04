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
 *
 * Hazard pay buys growth (CoreImmigrationPluginImpl): it adds the colony's hazard penalty back plus 5 growth points a month,
 * at about 1,000 credits a point, and a colony needs 300 x 2^(size-3) points for the next size. Every goal wants growth
 * (bigger colonies make more, get more industry slots and reach their plans). Colonies are ranked by how much faster it
 * makes them grow (its points against their own monthly growth; a colony that doesn't grow on its own counts as the
 * fastest gain), and the best ones get it, as long as it speeds them up by at least the "faster growth" setting
 * (hazardPayColonyShare) and all of it fits the empire share of net income before hazard pay (hazardPayEmpireShare).
 * The more you earn, the more colonies get it.
 */
public class CAPolicies {
    static final float HAZARD_PAY_MIN_DAYS = 30f, FREE_PORT_MIN_DAYS = 90f;
    /** The hazard pay budget from the last hazardPlan, for the log's reasons. */
    static float lastCap = 0f;

    static boolean punitiveActive() {
        try {
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(PunitiveExpeditionIntel.class)) {
                if (!i.isEnded() && !i.isEnding()) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    static boolean canChange(long last, float minDays) {
        return last <= 0L || Global.getSector().getClock().getElapsedDaysSince(last) >= minDays;
    }

    public static void run(List<MarketAPI> auto) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (!s.hazardLimitChecked) {   // saves from before 2.17 kept the old 10% default; 30% is the recommended limit since then
            s.hazardLimitChecked = true;
            if (Math.abs(s.hazardPayEmpireShare - 0.10f) < 0.005f) {
                s.hazardPayEmpireShare = 0.30f;
                CALog.add("Hazard pay limit raised from the old default of 10% to 30% of empire net income (Settings, Hazard pay limit, all colonies)", true);
            }
        }
        boolean emergency = CABudget.paused(s);
        boolean punitive = punitiveActive();
        long now = Global.getSector().getClock().getTimestamp();
        Map<MarketAPI, Growth> growth = new HashMap<MarketAPI, Growth>();
        Set<MarketAPI> hpWant = s.hazardPayMode == 0 ? hazardPlan(auto, s, emergency, growth) : new HashSet<MarketAPI>();
        for (MarketAPI m : auto) {
            if (CAHypershunt.is(m)) continue;   // fixed at size 2 with no market to open, so neither policy helps
            CAData.Colony c = d.colony(m.getId());
            boolean threat = CAThreat.isHigh(m);
            CAData.Profile prof = threat ? CAData.Profile.SECURITY : c.profile;
            float stab = m.getStabilityValue();
            boolean siege = CASiege.held(m);   // its income is off while it lasts, so only urgent switch-offs

            // ---- hazard pay: buys growth with credits (hazardPlan picks the colonies) ----
            if (s.hazardPayMode == 0) {
                boolean on = m.isImmigrationIncentivesOn();
                boolean want = hpWant.contains(m);
                if (want != on) {
                    Growth g = growth.get(m);
                    boolean atMax = m.getSize() >= Misc.getMaxMarketSize(m);
                    String why = atMax ? "max size" : emergency ? "net income is negative" : CAMods.growthUnsafe(m) ? "Astral Ascension can't grow a colony without a planet past size 10"
                            : g == null || g.points <= 0f ? "it adds no growth here"
                            : g.speedup < s.hazardPayColonyShare * 0.8f ? "it would only speed growth up by " + pct(g.speedup) + ", under the " + pct(s.hazardPayColonyShare) + " setting"
                            : g.cost > lastCap * (on ? 1.1f : 1f) ? "the hazard pay budget is " + pct(s.hazardPayEmpireShare) + " of empire net income, "
                                    + Misc.getDGSCredits(lastCap) + ", and it costs " + Misc.getDGSCredits(g.cost) + " a month here; raise Hazard pay limit, all colonies, in Settings"
                            : "the hazard pay budget (" + pct(s.hazardPayEmpireShare) + " of empire net income) goes to colonies it speeds up more";
                    boolean urgentOff = on && (atMax || emergency || why.startsWith("the hazard pay budget"));
                    if ((!siege && canChange(c.lastHazardPay, HAZARD_PAY_MIN_DAYS)) || urgentOff) {
                        m.setImmigrationIncentivesOn(want);
                        c.lastHazardPay = now;
                        CALog.add(m.getName() + ": hazard pay " + (want ? "on (" + growthText(m, g) + ")" : "off (" + why + ")"), true);
                    }
                }
            }

            // ---- free port: accessibility and growth for stability and punitive-expedition risk ----
            if (s.freePortMode == 0) {
                boolean on = m.isFreePort();
                boolean luddic = m.hasCondition("luddic_majority");
                // AotD's Underworld only works on a free port (it still costs upkeep without one), so it keeps the port open
                boolean profileFits = m.hasIndustry("aotd_underworld") || prof == CAData.Profile.INCOME || prof == CAData.Profile.GROWTH || prof == CAData.Profile.BALANCED || prof == CAData.Profile.SELF;
                float margin = on ? 0f : 3f;   // free port costs up to 3 stability
                boolean stableEnough = stab - margin >= d.minStability + 1f;
                boolean shield = CAMods.shieldWantsClosedPort(m);   // Planetary Shield: Access Control keeps Pathers out only with the port closed
                boolean police = CACrisis.policeComing() && CACrisis.policeScore(m) > 0;   // Nexerelin's police crisis is next: a closed port isn't a target
                boolean want = profileFits && stableEnough && !luddic && !threat && !punitive && !emergency && !shield && !police;
                String why = police ? "Nexerelin's police raid on free ports is the next colony crisis" : punitive ? "punitive expedition under way" : threat ? "threat is high" : luddic ? "Luddic majority" : !stableEnough ? "stability too low" : emergency ? "net income is negative" : shield ? "the planetary shield keeps Pather cells out only while the port is closed" : !profileFits ? "profile is " + prof.label : "";
                boolean urgentOff = on && (punitive || threat || police || stab < d.minStability);
                if (want != on && (urgentOff || (!siege && canChange(c.lastFreePort, FREE_PORT_MIN_DAYS)))) {
                    m.setFreePort(want);
                    m.reapplyConditions();
                    c.lastFreePort = now;
                    CALog.add(m.getName() + ": free port " + (want ? "on (accessibility and growth build up over a year; up to -3 stability)" : "off (" + why + ")"), true);
                }
            }
        }
    }

    static String pct(float f) { return Math.round(f * 100f) + "%"; }

    /** What hazard pay buys at a colony: growth points a month, its own growth without them, the cost, and the speed-up. */
    static final class Growth {
        MarketAPI m;
        boolean on;
        float points, base, cost, speedup;
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
     */
    static Set<MarketAPI> hazardPlan(List<MarketAPI> auto, CASettings s, boolean emergency, Map<MarketAPI, Growth> info) {
        Set<MarketAPI> want = new HashSet<MarketAPI>();
        float paying = 0f;
        for (MarketAPI m : auto) if (m.isImmigrationIncentivesOn()) paying += m.getImmigrationIncentivesCost();
        // spending freely (CASurplus.rich): wherever it speeds growth by 5% or more, paid for as long as the surplus lasts
        boolean rich = CASurplus.rich(s);
        float cap = rich ? Float.MAX_VALUE : s.hazardPayEmpireShare * Math.max(0f, CABudget.netIncome(s) + paying);
        float minShare = rich ? Math.min(0.05f, s.hazardPayColonyShare) : s.hazardPayColonyShare;
        lastCap = cap;
        List<Growth> cands = new ArrayList<Growth>();
        for (MarketAPI m : auto) {
            if (CAHypershunt.is(m) || CAMods.growthUnsafe(m) || m.getSize() >= Misc.getMaxMarketSize(m)) continue;
            Growth g = growth(m);
            info.put(m, g);
            if (g.points <= 0f || g.speedup < minShare * (g.on ? 0.8f : 1f)) continue;
            cands.add(g);
        }
        if (emergency) return want;
        return pick(cands, cap);
    }

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
