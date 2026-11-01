package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionIntel;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

/**
 * Colony policies for Auto colonies: hazard pay and free port, switched on and off by profile, budget, stability,
 * threat and emergencies (negative income, punitive expeditions). Minimum time between changes avoids flip-flopping.
 */
public class CAPolicies {
    static final float HAZARD_PAY_MIN_DAYS = 30f, FREE_PORT_MIN_DAYS = 90f;

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
        boolean emergency = CABudget.paused(s);
        boolean punitive = punitiveActive();
        long now = Global.getSector().getClock().getTimestamp();
        float hpCap = s.hazardPayEmpireShare * Math.max(0f, CABudget.netIncome(s));
        float hpTotal = 0f;
        for (MarketAPI m : auto) if (m.isImmigrationIncentivesOn()) hpTotal += m.getImmigrationIncentivesCost();
        for (MarketAPI m : auto) {
            if (CAHypershunt.is(m)) continue;   // fixed at size 2 with no market to open, so neither policy helps
            CAData.Colony c = d.colony(m.getId());
            boolean threat = CAThreat.isHigh(m);
            CAData.Profile prof = threat ? CAData.Profile.SECURITY : c.profile;
            float stab = m.getStabilityValue();
            boolean siege = CASiege.held(m);   // its income is off while it lasts, so only urgent switch-offs

            // ---- hazard pay: buys growth with credits ----
            if (s.hazardPayMode == 0) {
                boolean on = m.isImmigrationIncentivesOn();
                boolean atMax = m.getSize() >= Misc.getMaxMarketSize(m);
                float cost = m.getImmigrationIncentivesCost();
                CAData.Profile own = c.profile;
                boolean unsafe = CAMods.growthUnsafe(m);   // Astral Ascension crashes planetless colonies past size 10
                boolean growthWanted = !unsafe && (own != CAData.Profile.WAR || prof == CAData.Profile.GROWTH);
                boolean unused = prof == CAData.Profile.GROWTH || prof == CAData.Profile.BALANCED || prof == CAData.Profile.INCOME || prof == CAData.Profile.SELF;
                float colonyNet = Math.max(0f, m.getNetIncome() + (on ? cost : 0f));   // judge against income before hazard pay
                boolean affordable = cost <= s.hazardPayColonyShare * colonyNet;
                boolean fitsEmpire = on || hpTotal + cost <= hpCap;
                boolean want = growthWanted && !atMax && affordable && !emergency && fitsEmpire;
                String why = atMax ? "max size" : emergency ? "net income is negative" : !fitsEmpire ? "all colonies together would pass " + Math.round(s.hazardPayEmpireShare * 100f) + "% of empire net income" : !affordable ? Misc.getDGSCredits(cost) + "/month is over " + Math.round(s.hazardPayColonyShare * 100f) + "% of its net income" : unsafe ? "Astral Ascension can't grow a colony without a planet past size 10" : !growthWanted ? "profile doesn't need growth" : "";
                if (want != on && ((!siege && canChange(c.lastHazardPay, HAZARD_PAY_MIN_DAYS)) || (on && (emergency || atMax)))) {
                    m.setImmigrationIncentivesOn(want);
                    hpTotal += want ? cost : -cost;
                    c.lastHazardPay = now;
                    CALog.add(m.getName() + ": hazard pay " + (want ? "on (" + Misc.getDGSCredits(cost) + "/month for faster growth)" : "off (" + why + ")"), true);
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
        if (s.hazardPayMode == 0) enforceEmpireCap(auto, s, now);
    }

    /** Keeps total hazard pay under the empire share of net income, dropping the costliest first. */
    static void enforceEmpireCap(List<MarketAPI> auto, CASettings s, long now) {
        float cap = s.hazardPayEmpireShare * Math.max(0f, CABudget.netIncome(s));
        java.util.List<MarketAPI> paying = new java.util.ArrayList<MarketAPI>();
        float total = 0f;
        for (MarketAPI m : auto) if (m.isImmigrationIncentivesOn()) { paying.add(m); total += m.getImmigrationIncentivesCost(); }
        if (total <= cap) return;
        java.util.Collections.sort(paying, new java.util.Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) { return Float.compare(b.getImmigrationIncentivesCost(), a.getImmigrationIncentivesCost()); }
        });
        for (MarketAPI m : paying) {
            if (total <= cap) break;
            float cost = m.getImmigrationIncentivesCost();
            m.setImmigrationIncentivesOn(false);
            CAData.get().colony(m.getId()).lastHazardPay = now;
            total -= cost;
            CALog.add(m.getName() + ": hazard pay off (all colonies together were over " + Math.round(s.hazardPayEmpireShare * 100f)
                    + "% of empire net income; this was the costliest at " + Misc.getDGSCredits(cost) + "/month)", true);
        }
    }
}
