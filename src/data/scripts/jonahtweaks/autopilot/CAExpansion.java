package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.PerseanLeagueMembership;
import com.fs.starfarer.api.impl.campaign.intel.events.RemnantHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.plugins.SurveyPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * WS-D (0.3.0.0, research 05 sections 6-7): Auto-expansion. Settings, New colonies: Off (every save starts here), Ask first
 * (a proposal you approve on the Expansion page) or Auto. No Nexerelin imports: the colony fleets are CAExpansionNex,
 * loaded only when Nexerelin is on.
 *
 * Each day (run, after the money tiers): expeditions under way arrive (deliver), administrators go to the new colonies
 * (seat), then, when every gate holds, the best planet in scope gets an expedition (Auto) or a proposal (Ask first).
 * Gates (gate): at least one Auto colony; spending not paused; no colony under siege; no colony crisis about to start; no
 * Nexerelin invasion on its way; money (below); pace (one expedition under way, two at the top tier; 120 days between
 * expeditions at Idle money, 60 when Rich, DECISIONS 6); the newest colony it founded has left its fast buildout or is 120
 * days old; room under the colony limit, or an administrator for the new colony (idle, on the way, or hired when the
 * expedition leaves). A new colony you'd govern also may not take a colony you govern under the stability floor
 * (CAStability.canGovernedLose); then it needs an administrator too.
 * Money: with the money line "Money ladder" (expandLine 0) the money tier must be Idle money or higher and the
 * expedition comes out of the money ladder's Expansion rung (CARich.allowance); the rung on Off stops Auto-expansion and on
 * Recommend only turns Auto into Ask first. With a fixed line, the credits over the surplus line must reach it and an
 * expedition may use 10% of them. Never below the credit reserve.
 * Targets (scan, kept 30 days): fully surveyed unclaimed planets in scope (your systems; then systems within 6 ly of a
 * colony; or within 15 ly of the capital; Low profile keeps it to your systems). Filters are vanilla's founding rules,
 * Nexerelin's colony rules, safety (never a Remnant nexus system) and claims. Value per month at size 5: deposits,
 * habitable, a reachable Terraforming Made Easy world, less hazard, Persean League crisis progress, an administrator past
 * the limit and nearby hostile markets. It must reach 10,000 a month and pay back within 60 months (or anything while
 * Rich). Ranked by value times location (same system x1.3, nearby x1.0, farther x0.7, less 2% per light-year from the
 * capital, at least x0.5).
 * Method: a Nexerelin colony fleet (Nexerelin's price and fleet request points) when Nexerelin is on and has the points,
 * else an Autopilot crew (the founding goods at base price, 40,000 plus 2,000 a light-year for transport, 15 days plus the
 * trip); the convenience fee on top. Charged only when it leaves. A crew founds the colony with the game's own founding
 * steps (found); a Nexerelin fleet founds it itself, and the colony is handed to you so it counts toward the limit.
 * Failures refund what came back (crew: goods and transport; Nexerelin: its own refund by stage), skip the planet or
 * system for 180 days, and after a lost Nexerelin fleet the next 180 days use crews.
 * Saved state: CAData.expandOrders (ids and numbers only), expandSkip, expandFounded, expandLast, expandPending*.
 */
public class CAExpansion {
    // ---------------- numbers ----------------

    /** Days between expeditions at Idle money (or a fixed line); half while Rich (DECISIONS 6). */
    static final float PACE_DAYS = 120f;
    /** The newest colony Auto-expansion founded must have left its fast buildout or be this many days old. */
    static final float NEWEST_DAYS = 120f;
    /** Days the candidate scan is kept before it's made again. */
    static final float SCAN_DAYS = 30f;
    /** Days a planet or system is skipped after a failed or rejected expedition. */
    static final float SKIP_DAYS = 180f;
    /** Value a planet must reach, credits a month at VALUE_SIZE. */
    static final float MIN_VALUE = 10000f;
    /** Months an expedition (and the plan's first two buildings) must pay back in, except while Rich. */
    static final float PAYBACK_MONTHS = 60f;
    /** Scope 1: systems within this many light-years of a colony; scope 2: within CAPITAL_LY of the capital. */
    static final float NEAR_LY = 6f, CAPITAL_LY = 15f;
    /** Location multipliers: same system as a colony, nearby, farther; less PER_LY per light-year from the capital, at least MIN_LOCATION. */
    static final float SAME_SYSTEM = 1.3f, NEAR = 1f, FAR = 0.7f, PER_LY = 0.02f, MIN_LOCATION = 0.5f;
    /** The colony size a planet is valued at. */
    static final int VALUE_SIZE = 5;
    /** Value parts: a habitable world (population, light industry, commerce), upkeep per +100% hazard, threat. */
    static final float HAB_BONUS = 15000f, UPKEEP_REF = 20000f, THREAT_REF = 30000f, THREAT_LY = 8f, THREAT_CAP = 0.6f;
    /** Share of CAConditions' world bonus counted for a reachable Terraforming Made Easy world: it's years of terraforming away. */
    static final float WORLD_SHARE = 0.25f;
    /** Share of a deposit's units counted (CAConditions.value's 0.8). */
    static final float DEPOSIT_SHARE = 0.8f;
    /** Autopilot crew: transport and escort charge, per light-year, and the days to set up before the trip. */
    static final float CREW_BASE = 40000f, CREW_PER_LY = 2000f, CREW_SETUP_DAYS = 15f;
    /** Nexerelin colony fleet size: 100 points, 150 while spending freely. */
    static final float NEX_FP = 100f, NEX_FP_RICH = 150f;
    /** Nexerelin's nex_fleetRequestOrganizeTimeMult default (the page's estimate; the launch reads Nexerelin's own). */
    static final float NEX_ORGANIZE_MULT = 0.75f;
    /** Days added to Nexerelin's organizing time for the arrival estimate: assembling (3 to 6) and landing. */
    static final float NEX_ASSEMBLE_DAYS = 4.5f, NEX_LAND_DAYS = 2f;
    /** With a fixed money line, an expedition may use this share of the credits over the surplus line. */
    static final float BUDGET_SHARE = 0.1f;
    /** Days a crew that arrived waits for an administrator before it turns back (refunded). */
    static final float ADMIN_WAIT_DAYS = 30f;
    /** A Nexerelin order with Nexerelin off is dropped after this many days. */
    static final float NEX_LOST_DAYS = 365f;
    /** A Nexerelin order whose intel is gone is treated as lost this many days after its expected arrival. */
    static final float NEX_MISSING_DAYS = 30f;
    /** The game's founding goods (SurveyPluginImpl.getOutpostConsumed), when the survey plugin can't be read. */
    static final int OUTPOST_CREW = 1000, OUTPOST_MACHINERY = 100, OUTPOST_SUPPLIES = 200;
    /** Candidates on the Expansion page. */
    static final int TOP = 5;
    static final String NEX = "nex", CREW = "crew";
    /** expandSkip key: no Nexerelin colony fleets until then (after one was lost). */
    static final String NO_NEX = "method:nex";
    /** The money ladder's Expansion rung (CARich.RUNGS). */
    static final int RUNG = rungIndex();
    static final long DAY_MS = 86_400_000L;
    /** CATime stamp of the candidate scan; CATime.reset clears it on every load, so a scan from another save is never read. */
    static final String SCAN_STAMP = "expand:scan";

    static int rungIndex() {
        for (int i = 0; i < CARich.RUNGS.length; i++) if ("Expansion".equals(CARich.RUNGS[i])) return i;
        return 5;
    }

    // ---------------- saved and passing data ----------------

    /**
     * An expedition under way (saved in CAData.expandOrders): ids, numbers and a timestamp only. method "nex" or "crew"; plan
     * and goal (a CAData.Profile name) for the colony; why, the one-line reason; sent, the game timestamp it left; days, the
     * expected trip; paid, the price without the fee (what a crew refunds); fee, Auto Colonies' fee; fp, a colony fleet's
     * points (0 for a crew).
     */
    public static class Order { public String planetId, planetName, sourceId, method, plan, goal, why; public long sent; public float days, paid, fee; public int fp; }

    /** A candidate planet (not saved; the scan is kept for SCAN_DAYS in SCAN). */
    public static class Prospect {
        public String planetId, planetName, systemId, systemName, plan, reason, goal, method, near;
        public float value, location, cost, fee, steps, ly, lyCapital, payback, deposits, crisis, admin, threat, hazard, world;
        public boolean sameSystem, pays;
        public int coloniesThere;
        float score() { return value * location; }
        float total() { return cost + fee; }
    }

    /** The scan and the page's status line; read only while CATime's SCAN_STAMP is fresh. Holds no game objects. */
    static final class Scan {
        List<Prospect> list;
        String key;
        Map<String, Integer> blocked = new LinkedHashMap<String, Integer>();
        String status;
        float statusDay = Float.NaN;
    }

    static final Scan SCAN = new Scan();

    /**
     * 0.3.0.9: CAReset.onLoad empties the scan and the status line, so nothing from another save is shown or acted on. Approve,
     * Found now and the "nothing pays" reason read the scan through cached(), which needs a fresh stamp (CATime).
     */
    static void reset() {
        SCAN.list = null;
        SCAN.key = null;
        SCAN.blocked.clear();
        SCAN.status = null;
        SCAN.statusDay = Float.NaN;
    }

    // ---------------- the daily step ----------------

    /** Daily from CAAuto (step "expansion"), after the money tiers; auto: your Auto colonies not under siege. */
    public static void run(CAData d, CASettings s, List<MarketAPI> auto) {
        if (Global.getSector() == null || CALog.whatIf()) return;
        deliver(d, s);   // even with the switch off: expeditions already paid for still arrive
        seat(d, s);
        if (s.expandMode > 0 && !s.expandConfirmed) notice(s);
        int mode = mode(s);
        if (mode == 0) {
            if (d.expandPendingId != null) clearPending(d, "Auto-expansion is off");
            status(offReason(s), -1f);   // says nothing to the ladder: the rung keeps its share unspent, as before 0.3.0
            return;
        }
        if (d.expandPendingId != null) {   // one proposal at a time, until you approve or reject it
            checkPending(d, s);
            if (d.expandPendingId != null) { status("Waiting for your approval: " + d.expandPendingName + ".", d.expandPendingCost); return; }
        }
        Money money = money(s, mode);
        String why = gate(d, s, auto, money);
        if (why != null) { status(why, 0f); return; }
        Prospect best = best(d, s, false);
        if (best == null) { status(nothingReason(s), 0f); return; }
        ExpansionCost c = costFor(d, s, best);
        if (c.why != null) { status(c.why, 0f); return; }
        String afford = afford(money, c.total, lineIndex(s) == 0);
        if (afford != null) { status(afford, c.total); return; }   // the rung keeps its share for this expedition
        if (mode == 1) { propose(d, s, best, c); status("Waiting for your approval: " + best.planetName + ".", c.total); return; }
        String err = launch(d, s, best, false);
        status(err == null ? "Expedition to " + best.planetName + " under way." : err, 0f);
    }

    /**
     * Remembers the day's status line for the Expansion page (and starsector.log when it changes), and tells the money
     * ladder what the Expansion rung still wants this month (CARich.want: 0 passes its share down to the next rung) with a
     * short note for the Budget panel. want below 0: Auto-expansion is off and tells the ladder nothing.
     */
    static void status(String why, float want) {
        float today = CATime.today();
        if (why != null && !why.equals(SCAN.status)) CALog.quiet("Auto-expansion: " + why);
        SCAN.status = why;
        SCAN.statusDay = today;
        try {
            if (want < 0f) { CARich.note(RUNG, null); return; }
            if (lineIndex(CAData.settings()) != 0) want = 0f;   // a fixed line pays from the credits over the surplus line
            CARich.want(RUNG, want);
            CARich.note(RUNG, why == null ? null : lower(why));
        } catch (Throwable t) { CAStep.warn("expansion", "money ladder note failed", t); }
    }

    /** The one-time line when Auto-expansion is first turned on (the settings page may show its own dialog first). */
    static void notice(CASettings s) {
        s.expandConfirmed = true;
        String nex = nexOn() && s.expandMethod == 0 ? "about " + Misc.getDGSCredits(nexCostEstimate(NEX_FP)) + " by Nexerelin colony fleet or " : "";
        CALog.did("Auto-expansion: on (" + modeName(s) + "). Expedition cost: " + nex + Misc.getDGSCredits(crewCost(0f, goods()))
                + " and up by Autopilot crew, plus the fee. Each new colony adds Persean League crisis progress. New colonies lose money in their first months. "
                + "Scope: " + scopeName(s) + ". Candidates and expeditions: Expansion page.", true);
    }

    // ---------------- modes, scope, money ----------------

    static int lineIndex(CASettings s) { return Math.max(0, Math.min(CASettings.EXPAND_LINES.length - 1, s.expandLine)); }

    /** The mode in effect: the setting, with the money ladder's Expansion rung when the money line is the ladder. */
    static int mode(CASettings s) { return mode(s.expandMode, lineIndex(s), s.rungMode(RUNG)); }

    /**
     * Pure: the mode in effect (0 Off, 1 Ask first, 2 Auto). With the money ladder line (0), the Expansion rung's mode
     * applies too: Off (0) stops it, Recommend only (2) makes Auto ask first. A fixed line leaves the setting as it is.
     */
    static int mode(int expandMode, int line, int rung) {
        int m = Math.max(0, Math.min(2, expandMode));
        if (m == 0 || line != 0) return m;
        if (rung == 0) return 0;
        if (rung == 2) return Math.min(m, 1);
        return m;
    }

    static String modeName(CASettings s) {
        int m = mode(s);
        String n = CASettings.EXPAND_NAMES[m];
        if (m == 1 && s.expandMode == 2) n += ", Expansion rung: Recommend only";
        return n;
    }

    static String offReason(CASettings s) {
        if (s.expandMode > 0 && lineIndex(s) == 0 && s.rungMode(RUNG) == 0) return "The money ladder's Expansion rung is Off.";
        return "Auto-expansion is off.";
    }

    /** The scope in effect: Low profile keeps it to the systems you hold. */
    static int scope(CASettings s) { return scope(s.expandScope, CALowProfile.on()); }

    /** Pure: 0 systems you hold, 1 those and systems within NEAR_LY of a colony, 2 also within CAPITAL_LY of the capital. */
    static int scope(int scope, boolean lowProfile) { return lowProfile ? 0 : Math.max(0, Math.min(2, scope)); }

    static String scopeName(CASettings s) {
        int sc = scope(s);
        return CASettings.EXPAND_SCOPE_NAMES[sc] + (sc != s.expandScope ? " (Low profile)" : "");
    }

    /** What the money rules allow today. */
    static final class Money {
        boolean rich, veryRich;
        /** Credits an expedition may use now, never below the credit reserve. */
        float available;
        /** Null when there's money to spend, else why not. */
        String why;
    }

    static Money money(CASettings s, int mode) {
        Money r = new Money();
        int line = lineIndex(s);
        float aboveReserve = Math.max(0f, CABuild.credits() - s.reserve);
        if (line == 0) {
            int tier = CARich.tier();
            r.rich = tier >= CARich.T3;
            r.veryRich = tier >= CARich.T4;
            if (tier < CARich.T2) { r.why = "Money tier: " + CARich.name(tier) + ". Auto-expansion starts at " + CARich.name(CARich.T2) + "."; return r; }
            // the rung's allowance this month; a rung on Recommend only still has one (Ask first proposes within it)
            float a = 0f;
            try { a = CARich.allowance(RUNG); } catch (Throwable ignored) { }
            r.available = Math.min(Math.max(0f, a), aboveReserve);
            return r;
        }
        float want = CASettings.EXPAND_LINES[line], amount = CASurplus.amount(s);
        r.rich = amount >= 2f * want || CASurplus.rich(s);
        r.veryRich = amount >= 3f * want;
        if (amount < want) {
            r.why = "Credits over the surplus line: " + Misc.getDGSCredits(amount) + " of the " + Misc.getDGSCredits(want) + " needed.";
            return r;
        }
        r.available = Math.min(BUDGET_SHARE * amount, aboveReserve);
        return r;
    }

    /** Null when the money covers this total, else why not. */
    static String afford(Money m, float total, boolean ladder) {
        if (m.why != null) return m.why;
        if (total <= m.available) return null;
        return ladder ? "The Expansion rung has " + Misc.getDGSCredits(m.available) + " now; the expedition costs " + Misc.getDGSCredits(total) + "."
                : "The expedition costs " + Misc.getDGSCredits(total) + "; it may use " + Misc.getDGSCredits(m.available) + " (10% of the credits over the surplus line).";
    }

    // ---------------- the gate ----------------

    /** Null when an expedition may start today (money for a given expedition is checked against it later), else why not. */
    static String gate(CAData d, CASettings s, List<MarketAPI> auto, Money money) {
        if (auto != null && auto.isEmpty()) return "No colony is on Auto.";
        if (CABudget.paused(s)) return "Spending is paused while net income is below 0.";
        for (MarketAPI m : colonies()) if (CASiege.held(m)) return m.getName() + " is under siege.";
        if (CACrisis.pending() != null) return "A colony crisis is about to start.";
        if (Global.getSettings().getModManager().isModEnabled("nexerelin") && nexLoaded()) {   // isModEnabled in each caller of CAExpansionNex (audit_combos)
            try { if (CAExpansionNex.invasionUnderWay()) return "A Nexerelin invasion is on its way to one of your colonies."; }
            catch (Throwable t) { CAStep.warn("expansion", "Nexerelin invasion check failed", t); }
        }
        if (money.why != null) return money.why;
        int max = CASettings.EXPAND_MAX[Math.max(0, Math.min(CASettings.EXPAND_MAX.length - 1, s.expandMax))];
        if (max > 0 && colonies().size() + d.expandOrders().size() >= max) return "At the colony maximum (" + max + ").";
        int under = d.expandOrders().size();
        float since = sinceLast(d);
        if (!paced(since, PACE_DAYS, under, money.rich, money.veryRich)) {
            if (under >= underWayLimit(money.veryRich)) return under == 1 ? "An expedition is under way." : under + " expeditions are under way.";
            return "Next expedition in " + Math.max(1, Math.round(pace(PACE_DAYS, money.rich) - since)) + " days (one per "
                    + Math.round(PACE_DAYS) + " days, " + Math.round(PACE_DAYS / 2f) + " at the Rich tier).";
        }
        String busy = newestBusy(d);
        if (busy != null) return busy;
        Room r = room(d, s);
        return r.why;
    }

    /** Pure: pacing. Under way: expeditions now; one at a time, two at the top tier; pace days between them, half while rich. */
    static boolean paced(float sinceLast, float pace, int underWay, boolean rich, boolean veryRich) {
        if (underWay >= underWayLimit(veryRich)) return false;
        return sinceLast >= pace(pace, rich);
    }

    static int underWayLimit(boolean veryRich) { return veryRich ? 2 : 1; }

    static float pace(float pace, boolean rich) { return rich ? pace / 2f : pace; }

    /** Days since the last expedition left (Float.MAX_VALUE for never). */
    static float sinceLast(CAData d) {
        if (d.expandLast == 0L) return Float.MAX_VALUE;
        return Global.getSector().getClock().getElapsedDaysSince(d.expandLast);
    }

    /** Why the newest colony Auto-expansion founded holds the next one back (its fast buildout, under NEWEST_DAYS old), or null. */
    static String newestBusy(CAData d) {
        if (d.expandFounded == null) return null;
        for (String pid : d.expandFounded) {
            MarketAPI m = colonyOn(pid);
            if (m == null) continue;
            CAData.Colony c = d.colonies.get(m.getId());
            if (c == null || !c.buildout) continue;
            float age = m.getDaysInExistence();
            if (age < NEWEST_DAYS) return m.getName() + " is still in its fast buildout (" + Math.round(NEWEST_DAYS - age) + " days at most).";
        }
        return null;
    }

    // ---------------- room under the colony limit ----------------

    /** The colony limit and the administrators for one more colony. */
    static final class Room {
        int governed, max, admins, maxAdmins, idle, pending;
        /** An administrator has to be hired when the expedition leaves. */
        boolean hire;
        String why;
    }

    /**
     * Pure: administrators missing for one more colony, once the colonies under way (pending) have landed: past the limit,
     * less the ones that govern nothing (idle or on their way).
     */
    static int adminsShort(int governed, int maxGoverned, int idle, int pending) {
        return Math.max(0, governed + pending + 1 - maxGoverned - idle);
    }

    /** Pure: room for one more colony, with administrators hired when needed (the 6-argument form JTExpansionCheck names). */
    static boolean room(int governed, int maxGoverned, int admins, int maxAdmins, int idle, int pending) {
        return room(governed, maxGoverned, admins, maxAdmins, idle, pending, true);
    }

    /**
     * Pure: room for one more colony that never puts the colonies you govern past the limit without an administrator.
     * False when the limit already needs administrators that aren't there; true when the new one fits under the limit or an
     * idle administrator covers it; otherwise only when one can be hired (canHire) and you employ fewer than you may.
     */
    static boolean room(int governed, int maxGoverned, int admins, int maxAdmins, int idle, int pending, boolean canHire) {
        if (governed + pending - maxGoverned - idle > 0) return false;
        if (adminsShort(governed, maxGoverned, idle, pending) == 0) return true;
        return canHire && admins < maxAdmins;
    }

    static Room room(CAData d, CASettings s) {
        Room r = new Room();
        r.max = CAItems.maxGoverned();
        r.governed = CAItems.overLimit() + r.max;
        r.admins = CAItems.adminsEmployed();
        r.maxAdmins = CAItems.adminMax();
        r.idle = CAItems.idleAdmins();
        r.pending = d.expandOrders().size() + d.tascOrders().size() + CAItems.comingBack(d);
        if (r.governed + r.pending - r.max - r.idle > 0) {
            r.why = "Colony limit: administrators needed. Auto-expansion waits for them.";
            return r;
        }
        int shortBy = adminsShort(r.governed, r.max, r.idle, r.pending);
        // a colony you'd govern under the limit costs every colony you govern its management bonus (CAStability)
        if (shortBy == 0 && r.idle == 0 && r.governed + r.pending + 1 <= r.max
                && !CAStability.canGovernedLose(-CAStability.oneMoreGoverned(), null)) shortBy = 1;
        if (shortBy == 0) return r;
        r.hire = true;
        if (!s.expandAdmins()) r.why = "New colony needs an administrator. Hiring one for it is off.";
        else if (r.admins >= r.maxAdmins) r.why = "New colony needs an administrator. Administrators at the maximum (" + r.maxAdmins + ").";
        return r;
    }

    // ---------------- the cost of one expedition ----------------

    /** How one expedition goes and what it costs now. */
    static final class ExpansionCost {
        String method, why;
        float price, fee, total, fp, days, ly;
        MarketAPI from;
        CAItems.ExpansionHire hire;
    }

    /** The method and the price for this candidate today, an administrator's hire included when the colony limit needs one. */
    static ExpansionCost costFor(CAData d, CASettings s, Prospect p) {
        ExpansionCost c = new ExpansionCost();
        PlanetAPI planet = planet(p.planetId);
        if (planet == null) { c.why = "That planet is gone."; return c; }
        Room r = room(d, s);
        if (r.why != null) { c.why = r.why; return c; }
        if (r.hire) {
            c.hire = CAItems.expansionHire(s);
            if (c.hire == null) { c.why = "New colony needs an administrator. None can be hired."; return c; }
        }
        boolean rich = CASurplus.rich(s);
        c.fp = rich ? NEX_FP_RICH : NEX_FP;
        if (Global.getSettings().getModManager().isModEnabled("nexerelin") && nexAllowed(d, s)) {
            try {
                if (CAExpansionNex.available(s, c.fp)) {
                    MarketAPI from = CAExpansionNex.source(planet);
                    if (from != null) {
                        c.method = NEX; c.from = from;
                        c.price = CAExpansionNex.cost(c.fp);
                        c.days = CAExpansionNex.days(c.fp, from, planet);
                    }
                }
            } catch (Throwable t) { CAStep.warn("expansion", "Nexerelin colony fleet check failed", t); c.method = null; }
        }
        if (c.method == null) crewCost(c, planet);
        if (c.from == null) { c.why = "No colony of yours can send an expedition."; return c; }
        c.fee = CABudget.constructionFee(s, c.price);
        c.total = c.price + c.fee + (c.hire == null ? 0f : c.hire.price + c.hire.fee);
        return c;
    }

    static void crewCost(ExpansionCost c, PlanetAPI planet) {
        c.method = CREW; c.fp = 0f;
        c.from = crewSource(planet, colonies());
        c.ly = c.from == null ? 0f : Misc.getDistanceLY(c.from.getPrimaryEntity(), planet);
        c.price = crewCost(c.ly, goods());
        c.days = crewDays(c.ly);
    }

    /** Pure: an Autopilot crew's price, the founding goods plus transport and escort. */
    static float crewCost(float ly, float goods) { return goods + CREW_BASE + CREW_PER_LY * Math.max(0f, ly); }

    /** Pure: an Autopilot crew's days, setting up plus the trip (CAStores.days). */
    static float crewDays(float ly) { return CREW_SETUP_DAYS + CAStores.days(ly); }

    /** Pure: Nexerelin's price for a colony fleet (Nex_FleetRequest.updateCost): fp x per point x 2 + the goods, rounded. */
    static float nexCost(float fp, float perFP, float goods) { return Math.round(fp * perFP * 2f + goods); }

    /** Pure: Nexerelin's organizing days for a colony fleet (getTimeToLaunch: (10 + fp/30) x 2 x the setting's multiplier). */
    static float nexLaunchDays(float fp, float mult) { return Math.max(0.1f, (10f + fp / 30f) * 2f * mult); }

    /** Pure: the share of its fee Nexerelin refunds when an expedition is cancelled at this stage (OffensiveFleetIntel.getCreditRefundMult). */
    static float nexRefundMult(int stage) {
        if (stage <= 1) return 1f;
        if (stage == 2) return 0.67f;
        if (stage == 3) return 0.33f;
        return 0f;
    }

    /** Nexerelin's price for the page and the notice, without loading Nexerelin's classes when it's off. */
    static float nexCostEstimate(float fp) {
        if (Global.getSettings().getModManager().isModEnabled("nexerelin") && nexLoaded()) { try { return CAExpansionNex.cost(fp); } catch (Throwable ignored) { } }
        return nexCost(fp, 400f, goods());
    }

    /**
     * The founding goods at base price: SurveyPlugin.getOutpostConsumed (1,000 crew, 100 heavy machinery, 200 supplies).
     * 0.3.0.9: plus the open market's tariff in survival mode (CASurvival.survivalTariff, 40%), as buying them there costs.
     */
    static float goods() {
        float v = 0f;
        for (Map.Entry<String, Integer> e : outpostConsumed().entrySet()) v += e.getValue() * basePrice(e.getKey());
        if (!(v > 0f)) v = OUTPOST_CREW * 50f + OUTPOST_MACHINERY * 150f + OUTPOST_SUPPLIES * 100f;
        return withTariff(v, CASurvival.survivalTariff());
    }

    /** Pure (0.3.0.9, JTExpansionCheck): goods worth `value` at base price, bought with this tariff (0.4 is 40%; under 0 or NaN is none). */
    static float withTariff(float value, float tariff) { return value * (1f + (tariff > 0f ? tariff : 0f)); }

    /** What founding a colony takes from your fleet in the game's own rules (the survey plugin), or the vanilla numbers. */
    static Map<String, Integer> outpostConsumed() {
        try {
            Object o = Global.getSettings().getNewPluginInstance("surveyPlugin");
            if (o instanceof SurveyPlugin) {
                Map<String, Integer> m = ((SurveyPlugin) o).getOutpostConsumed();
                if (m != null && !m.isEmpty()) return m;
            }
        } catch (Throwable ignored) { }
        Map<String, Integer> m = new LinkedHashMap<String, Integer>();
        m.put("crew", OUTPOST_CREW); m.put("heavy_machinery", OUTPOST_MACHINERY); m.put("supplies", OUTPOST_SUPPLIES);
        return m;
    }

    static float basePrice(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getBasePrice(); }
        catch (Throwable t) { return "crew".equals(com) ? 50f : "heavy_machinery".equals(com) ? 150f : "supplies".equals(com) ? 100f : 0f; }
    }

    /** Your nearest colony with a working spaceport, else your nearest colony; null with none. */
    static MarketAPI crewSource(PlanetAPI p, List<MarketAPI> colonies) {
        MarketAPI best = null, any = null;
        float bd = Float.MAX_VALUE, ad = Float.MAX_VALUE;
        for (MarketAPI m : colonies) {
            if (m.getPrimaryEntity() == null) continue;
            float ly = Misc.getDistanceLY(m.getPrimaryEntity(), p);
            if (ly < ad) { ad = ly; any = m; }
            if (ly < bd && spaceport(m)) { bd = ly; best = m; }
        }
        return best != null ? best : any;
    }

    static boolean spaceport(MarketAPI m) {
        for (Industry i : m.getIndustries()) {
            try { if (i.getSpec().hasTag(Industries.TAG_SPACEPORT) && i.isFunctional() && !i.isDisrupted()) return true; }
            catch (Throwable ignored) { }
        }
        return false;
    }

    // ---------------- Nexerelin ----------------

    /** Nexerelin is on and CAExpansionNex's calls into it resolve (CACompat): expeditions under way are followed. */
    static boolean nexLoaded() {
        try { return Global.getSettings().getModManager().isModEnabled("nexerelin") && CACompat.linked("CAExpansionNex"); }
        catch (Throwable t) { return false; }
    }

    /** As nexLoaded, and new colony fleets aren't stopped for the session (CABreaker, three failed starts in a row). */
    static boolean nexOn() {
        return nexLoaded() && CABreaker.allowed(BREAKER);
    }

    static final String BREAKER = "nexerelin:expansion";

    /** Nexerelin colony fleets may be used: the setting, Nexerelin on, and none lost in the last SKIP_DAYS. */
    static boolean nexAllowed(CAData d, CASettings s) {
        return s.expandMethod == 0 && nexOn() && !skipped(d, NO_NEX);
    }

    /**
     * 0.3.0.0 (WS-D, for CAWar): fleet request points a proposal waiting for your approval would use with a Nexerelin colony
     * fleet; 0 when there is none or it would go by crew.
     */
    public static float reservedFP() {
        try {
            CAData d = CAData.get();
            CASettings s = d.settings;
            if (d.expandPendingId == null || !nexAllowed(d, s)) return 0f;
            return CASurplus.rich(s) ? NEX_FP_RICH : NEX_FP;
        } catch (Throwable t) { return 0f; }
    }

    // ---------------- proposals (Ask first) ----------------

    static void propose(CAData d, CASettings s, Prospect p, ExpansionCost c) {
        d.expandPendingId = p.planetId;
        d.expandPendingName = p.planetName;
        d.expandPendingCost = c.total;
        CALog.advise(p.planetName + ": Colony expedition proposed. " + p.plan + ", goal " + goalLabel(p.goal) + ". About " + Misc.getDGSCredits(p.value)
                + " a month at size " + VALUE_SIZE + ". Cost " + Misc.getDGSCredits(c.total) + " by " + methodName(c.method)
                + (c.hire != null ? " with an administrator" : "") + ". Approve or reject it on the Expansion page.", true);
    }

    /** Drops a proposal whose planet can't be colonized any more. */
    static void checkPending(CAData d, CASettings s) {
        PlanetAPI p = planet(d.expandPendingId);
        String why = p == null ? "the planet is gone" : null;
        if (why == null) {
            String b = blocked(p, s, ctx(d, s), false);
            if (b != null) why = lower(b);
        }
        if (why != null) clearPending(d, why);
    }

    static void clearPending(CAData d, String why) {
        if (d.expandPendingId != null && why != null) CALog.quiet("Auto-expansion: proposal for " + d.expandPendingName + " dropped (" + why + ")");
        d.expandPendingId = null; d.expandPendingName = null; d.expandPendingCost = 0f;
    }

    // ---------------- launch ----------------

    /**
     * Sends an expedition to the candidate: checks the planet and the room again, starts a Nexerelin colony fleet (paid only
     * when Nexerelin keeps it running) or an Autopilot crew, hires the administrator the colony limit needs, pays, and
     * stores the order. player: you approved it or pressed Found now, so the money rules are yours (only the credit reserve
     * holds). Returns null when it left, else why not.
     */
    static String launch(CAData d, CASettings s, Prospect p, boolean player) {
        PlanetAPI planet = planet(p.planetId);
        if (planet == null) return "That planet is gone.";
        String b = blocked(planet, s, ctx(d, s), false);
        if (b != null) return b;
        ExpansionCost c = costFor(d, s, p);
        if (c.why != null) return c.why;
        Object intel = null;
        if (NEX.equals(c.method)) {
            boolean ok = false;
            try { intel = Global.getSettings().getModManager().isModEnabled("nexerelin") ? CAExpansionNex.start(planet, c.from, c.fp) : null; ok = true; }
            catch (Throwable t) { CAStep.warn("expansion", "starting a Nexerelin colony fleet failed", t); }
            CABreaker.check(BREAKER, ok, "a colony fleet to " + planet.getName());
            if (intel == null) {   // no jump point to it, or Nexerelin refused: a crew instead
                CALog.quiet("Auto-expansion: Nexerelin did not start a colony fleet to " + planet.getName() + "; an Autopilot crew goes instead");
                crewCost(c, planet);
                if (c.from == null) return "No colony of yours can send an expedition.";
                c.fee = CABudget.constructionFee(s, c.price);
                c.total = c.price + c.fee + (c.hire == null ? 0f : c.hire.price + c.hire.fee);
            }
        }
        String money = CABuild.credits() - c.total < s.reserve
                ? "The expedition costs " + Misc.getDGSCredits(c.total) + ", more than you have above your credit reserve." : null;
        if (money == null && !player) {
            int mode = mode(s);
            money = afford(money(s, mode), c.total, lineIndex(s) == 0);
        }
        if (money != null) {
            if (intel != null) CAExpansionNex.abort(intel);
            return money;
        }
        if (c.hire != null && !CAItems.hireForExpansion(d, s, c.hire, planet.getName())) {
            if (intel != null) CAExpansionNex.abort(intel);
            return "Hiring an administrator for the new colony failed; see starsector.log.";
        }
        CABudget.payFromPool(s, RUNG, null, c.price, c.fee);
        if (intel != null) {
            try { CAExpansionNex.commit(intel, c.price, c.fp); }
            catch (Throwable t) { CAStep.warn("expansion", "Nexerelin colony fleet fee failed", t); }
        }
        Order o = new Order();
        o.planetId = planet.getId(); o.planetName = planet.getName(); o.sourceId = c.from.getId();
        o.method = intel != null ? NEX : CREW;
        o.plan = p.plan; o.goal = p.goal; o.why = p.reason;
        o.sent = Global.getSector().getClock().getTimestamp();
        o.days = c.days; o.paid = c.price; o.fee = c.fee; o.fp = intel != null ? Math.round(c.fp) : 0;
        d.expandOrders().add(o);
        d.expandLast = o.sent;
        if (o.planetId.equals(d.expandPendingId)) clearPending(d, null);
        SCAN.list = null;   // the planet is taken by this order: scan again next time
        CALog.did(o.planetName + ": Colony expedition sent from " + c.from.getName() + " (" + methodName(o.method)
                + (intel != null ? ", " + o.fp + " points" : "") + "). About " + Math.round(o.days) + " days. " + Misc.getDGSCredits(c.price)
                + (c.fee > 0f ? " + " + Misc.getDGSCredits(c.fee) + " fee" : "") + ". " + p.plan + ", goal " + goalLabel(p.goal) + "."
                + (player ? " Ordered by you." : ""), true);
        return null;
    }

    // ---------------- arrivals ----------------

    /** Daily: crews that arrived found their colony; Nexerelin orders are checked against the intel and the planet. */
    static void deliver(CAData d, CASettings s) {
        if (d.expandOrders == null || d.expandOrders.isEmpty()) return;
        boolean nex = nexLoaded();
        for (Order o : new ArrayList<Order>(d.expandOrders)) {
            boolean done;
            try { done = CREW.equals(o.method) ? crewArrives(d, s, o) : nexStatus(d, s, o, nex); }
            catch (Throwable t) { CAStep.warn("expansion", "expedition to " + o.planetName + " failed", t); continue; }
            if (done) d.expandOrders.remove(o);
        }
    }

    /** A crew: true when the order is finished (founded, or turned back and refunded). */
    static boolean crewArrives(CAData d, CASettings s, Order o) {
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(o.sent);
        if (elapsed < o.days) return false;
        PlanetAPI p = planet(o.planetId);
        String why = p == null ? "the planet is gone" : null;
        if (why == null) {
            String b = blocked(p, s, ctx(d, s), true);
            if (b != null) why = lower(b);
        }
        if (why != null) { turnBack(d, s, o, why, true); return true; }
        if (needsAdminNow() && CAItems.idleAdmin(d) == null) {
            if (elapsed - o.days < ADMIN_WAIT_DAYS) {
                if (CALog.once("expand:adminwait:" + o.planetId))
                    CALog.think(o.planetName + ": Colony expedition waits up to "
                            + Math.round(ADMIN_WAIT_DAYS) + " days for an administrator.");
                return false;
            }
            turnBack(d, s, o, "no administrator arrived within " + Math.round(ADMIN_WAIT_DAYS) + " days", false);
            return true;
        }
        MarketAPI m = null;
        try { m = found(p); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "founding a colony on " + o.planetName + " failed", t); }
        if (m == null) { turnBack(d, s, o, "the founding failed; see starsector.log", true); return true; }
        CALog.forget("expand:adminwait:" + o.planetId);
        afterFounding(d, s, m, o);
        return true;
    }

    /** One more colony governed by you would go past the limit, or take a colony you govern under the stability floor. */
    static boolean needsAdminNow() {
        return CAItems.overLimit() + 1 > 0 || !CAStability.canGovernedLose(-CAStability.oneMoreGoverned(), null);
    }

    /** A crew that can't found its colony: the price comes back (not the fee), and the planet is skipped for SKIP_DAYS. */
    static void turnBack(CAData d, CASettings s, Order o, String why, boolean skipPlanet) {
        CABudget.refund(s, null, o.paid);
        if (skipPlanet) skip(d, o.planetId, SKIP_DAYS);
        CALog.did(o.planetName + ": Colony expedition turned back: " + why + ". " + Misc.getDGSCredits(o.paid) + " refunded. Fee kept.", true);
    }

    /** A Nexerelin colony fleet: true when the order is finished. */
    static boolean nexStatus(CAData d, CASettings s, Order o, boolean nex) {
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(o.sent);
        PlanetAPI p = planet(o.planetId);
        MarketAPI m = p == null ? null : p.getMarket();
        boolean colony = m != null && !m.isPlanetConditionMarketOnly() && m.isInEconomy();
        if (!nex || !Global.getSettings().getModManager().isModEnabled("nexerelin")) {
            if (colony && m.isPlayerOwned()) { afterFounding(d, s, m, o); return true; }
            if (elapsed < NEX_LOST_DAYS) return false;
            CALog.did(o.planetName + ": Colony expedition dropped. Nexerelin is off. No colony founded in "
                    + Math.round(NEX_LOST_DAYS) + " days. " + Misc.getDGSCredits(o.paid) + " lost.", true);
            return true;
        }
        if (colony && m.isPlayerOwned() && (CAExpansionNex.foundedByNex(m) || "success".equals(CAExpansionNex.status(o)))) {
            afterFounding(d, s, m, o);
            return true;
        }
        if (colony || p == null) {   // someone else got there first, or you founded it yourself
            float share = CAExpansionNex.cancel(o);
            String who = p == null ? "the planet is gone" : m.isPlayerOwned() ? "you founded a colony there yourself"
                    : "taken by " + (m.getFaction() == null ? "another faction" : m.getFaction().getDisplayName());
            if (p != null && !m.isPlayerOwned()) skip(d, o.planetId, SKIP_DAYS);
            CALog.did(o.planetName + ": Colony expedition cancelled: " + who + "." + (share > 0f ? " Nexerelin refunds "
                    + Math.round(share * 100f) + "% of " + Misc.getDGSCredits(o.paid) + "." : ""), true);
            return true;
        }
        String st = CAExpansionNex.status(o);
        if ("underway".equals(st)) {
            if (p != null && nexusIn(p.getStarSystem()) && CALog.once("expand:nexus:" + o.planetId))
                CALog.advise(o.planetName + ": Colony expedition under way. A Remnant nexus is now in the system.", true);
            return false;
        }
        if ("cancelled".equals(st)) {
            skip(d, o.planetId, SKIP_DAYS);
            CALog.did(o.planetName + ": Colony expedition cancelled by Nexerelin. Nexerelin refunds part of the "
                    + Misc.getDGSCredits(o.paid) + " by distance travelled.", true);
            return true;
        }
        if ("missing".equals(st) && elapsed < o.days + NEX_MISSING_DAYS) return false;
        // failed, missing long past its arrival, or "success" with no colony of yours there: lost, no refund
        String sys = p != null && p.getStarSystem() != null ? p.getStarSystem().getId() : null;
        if (sys != null) skip(d, sys, SKIP_DAYS);
        skip(d, NO_NEX, SKIP_DAYS);
        CALog.did(o.planetName + ": Colony expedition failed: " + ("missing".equals(st) ? "no word from the colony fleet" : "fleet lost, short of crew, or someone else first")
                + ". No Nexerelin refund. System blocked for " + Math.round(SKIP_DAYS) + " days. Autopilot crews only for the next "
                + Math.round(SKIP_DAYS) + " days.", true);
        return true;
    }

    /**
     * After a colony is founded: a Nexerelin colony goes to you (so it counts toward the colony limit), the colony goes on
     * Auto with the goal and a fast buildout above the expansion line, an administrator is seated when the limit needs one
     * (or hired at once when none is idle or on the way), and the colony is planned on the next daily pass.
     */
    static void afterFounding(CAData d, CASettings s, MarketAPI m, Order o) {
        if (NEX.equals(o.method) && Global.getSettings().getModManager().isModEnabled("nexerelin") && nexLoaded()) {
            try {
                if (CAExpansionNex.fixAdmin(m))
                    CALog.quiet(m.getName() + ": the governor Nexerelin's founding put in place was replaced by you, so the colony counts toward your colony limit");
            } catch (Throwable t) { CAStep.warn("expansion", "Nexerelin governor handover failed at " + m.getName(), t); }
        }
        d.expandFounded().add(o.planetId);
        CAData.Colony c = d.colony(m.getId());
        c.profile = profile(o.goal);
        CALog.did(m.getName() + ": Colony expedition founded the colony. " + o.plan + ", goal " + c.profile.label + ". On Auto.", true);
        CALog.quiet(m.getName() + ": founded by " + methodName(o.method) + " after " + Math.round(Global.getSector().getClock().getElapsedDaysSince(o.sent))
                + " days for " + Misc.getDGSCredits(o.paid) + " + " + Misc.getDGSCredits(o.fee) + " fee (" + o.why + ")");
        if (s.expandBuildout()) CABuildout.start(m, buildoutFloor(s));
        else c.mode = CAData.Mode.AUTO;
        if (!seatAt(d, m) && CAItems.overLimit() > 0 && CAItems.idleAdmins() == 0 && s.expandAdmins()) {
            // over the limit on arrival (an administrator left, a colony was captured): hire at once, CAOverLimit watches stability
            CAItems.ExpansionHire h = CAItems.expansionHire(s);
            if (h != null && CABuild.credits() - (h.price + h.fee) >= s.reserve) CAItems.hireForExpansion(d, s, h, m.getName());
        }
        CAAuto.nextPass.remove(m.getId());
        CACache.bump("new colony " + m.getName());
        SCAN.list = null;
    }

    /** The credits a fast buildout on a new colony keeps: the expansion line (the Idle money tier's exit line on the ladder), at least the reserve. */
    static float buildoutFloor(CASettings s) {
        int line = lineIndex(s);
        if (line > 0) return Math.max(s.reserve, Math.max(CASurplus.line(s), s.reserve) + CASettings.EXPAND_LINES[line]);
        float net = 0f, keep = 0f;
        try { net = CABudget.netIncome(s); keep = CABudget.committed() + CABudget.runway(s); } catch (Throwable ignored) { }
        return s.reserve + keep + CATuning.TIER_EXIT * CARich.lines(net)[CARich.T2];
    }

    /** Daily: an administrator who has arrived goes to a colony Auto-expansion founded, when the limit or the stability floor needs one. */
    static void seat(CAData d, CASettings s) {
        if (d.expandFounded == null || d.expandFounded.isEmpty()) return;
        for (String pid : new ArrayList<String>(d.expandFounded)) {
            PlanetAPI p = planet(pid);
            MarketAPI m = p == null ? null : p.getMarket();
            if (m == null || !m.isPlayerOwned() || m.isPlanetConditionMarketOnly()) { d.expandFounded.remove(pid); continue; }   // lost or gone
            if (seatAt(d, m)) return;   // one a day, like the other administrator moves
        }
    }

    static boolean seatAt(CAData d, MarketAPI m) {
        if (!CAItems.governedByYou(m)) return false;
        if (!(CAItems.overLimit() > 0 || governedBelowFloor())) return false;
        return CAItems.seatForExpansion(d, m);
    }

    static boolean governedBelowFloor() {
        for (MarketAPI m : colonies()) {
            try { if (CAItems.governedByYou(m) && CAStability.below(m)) return true; } catch (Throwable ignored) { }
        }
        return false;
    }

    // ---------------- founding (the game's own steps) ----------------

    /**
     * The game's founding steps (PlanetSurveyPanel, research 05 section 2.3, checked by JTFoundLinkCheck), for an Autopilot
     * crew: the planet keeps its name; no goods leave your fleet (the crew brought them, paid at launch). Null when the planet
     * isn't an unclaimed, fully surveyed condition market any more. Fires the game's "player colonized" listeners like the
     * game does.
     */
    static MarketAPI found(PlanetAPI p) {
        if (p == null || p.getMarket() == null) return null;
        MarketAPI m = p.getMarket();
        if (!m.isPlanetConditionMarketOnly() || m.isInEconomy() || m.getSurveyLevel() != MarketAPI.SurveyLevel.FULL) return null;
        p.setFaction(Factions.PLAYER);
        m.setDaysInExistence(0f);
        m.setPlanetConditionMarketOnly(false);
        m.setFactionId(Factions.PLAYER);
        m.addCondition(Conditions.POPULATION_3);
        m.addIndustry(Industries.POPULATION);
        m.setFreePort(false);
        if (m.hasCondition(Conditions.DECIVILIZED)) {
            m.removeCondition(Conditions.DECIVILIZED);
            m.addCondition(Conditions.DECIVILIZED_SUBPOP);
        }
        m.setSize(3);
        m.addSubmarket(Submarkets.LOCAL_RESOURCES);
        m.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        SubmarketAPI st = m.getSubmarket(Submarkets.SUBMARKET_STORAGE);
        if (st != null && st.getPlugin() instanceof StoragePlugin) ((StoragePlugin) st.getPlugin()).setPlayerPaidToUnlock(true);
        m.getTariff().modifyFlat("default_tariff", m.getFaction().getTariffFraction());
        // 10% to 20% of the crew stays at the colony, in its local resources (as the game does)
        Integer crew = outpostConsumed().get("crew");
        SubmarketAPI local = m.getSubmarket(Submarkets.LOCAL_RESOURCES);
        if (crew != null && local != null && local.getCargo() != null) local.getCargo().addCrew((int) (crew * (0.1f + 0.1f * (float) Math.random())));
        p.setMarket(m);
        m.setPrimaryEntity(p);
        m.setPlayerOwned(true);
        Global.getSector().getEconomy().addMarket(m, true);
        Global.getSector().getEconomy().tripleStep();
        m.advance(0f);
        m.getConstructionQueue().addToEnd(Industries.SPACEPORT, 0);
        ListenerUtil.reportPlayerColonizedPlanet(p);
        return m;
    }

    // ---------------- candidates ----------------

    /** What a scan reads once: your colonies and systems, the capital, the scope and lists. Not saved. */
    static final class Ctx {
        final List<MarketAPI> colonies = new ArrayList<MarketAPI>();
        final Map<StarSystemAPI, Integer> mine = new HashMap<StarSystemAPI, Integer>();
        final List<MarketAPI> hostile = new ArrayList<MarketAPI>();
        final Set<String> targeted = new HashSet<String>(), ordered = new HashSet<String>(), tasc = new HashSet<String>();
        MarketAPI capital;
        int scope;
        boolean nex, leagueCounts;
        String commission, settlement;
        CACrisis.Base meter;
        float adminMonthly;
        Map<String, Long> skip;
        long now;
    }

    static Ctx ctx(CAData d, CASettings s) {
        Ctx x = new Ctx();
        x.colonies.addAll(colonies());
        for (MarketAPI m : x.colonies) {
            StarSystemAPI sys = m.getStarSystem();
            if (sys == null) continue;
            Integer n = x.mine.get(sys);
            x.mine.put(sys, n == null ? 1 : n + 1);
        }
        try { x.capital = CACapital.capital(); } catch (Throwable ignored) { }
        if (x.capital == null && !x.colonies.isEmpty()) x.capital = x.colonies.get(0);
        x.scope = scope(s);
        x.nex = Global.getSettings().getModManager().isModEnabled("nexerelin") && nexLoaded();
        if (x.nex) {
            try { x.targeted.addAll(CAExpansionNex.targeted()); } catch (Throwable t) { CAStep.warn("expansion", "Nexerelin colony targets failed", t); }
        }
        for (Order o : d.expandOrders()) if (o.planetId != null) x.ordered.add(o.planetId);
        try { for (CATascStations.Order o : d.tascOrders()) if (o.gasGiantId != null) x.tasc.add(o.gasGiantId); } catch (Throwable ignored) { }
        try { x.commission = Misc.getCommissionFactionId(); } catch (Throwable ignored) { }
        x.settlement = d.settleExpeditionPlanet;
        x.skip = d.expandSkip();
        x.now = Global.getSector().getClock().getTimestamp();
        for (MarketAPI o : Global.getSector().getEconomy().getMarketsCopy()) {
            try {
                if (o.isPlayerOwned() || o.getFaction() == null || o.getPrimaryEntity() == null || o.isHidden()) continue;
                if (o.getFaction().isHostileTo(Factions.PLAYER)) x.hostile.add(o);
            } catch (Throwable ignored) { }
        }
        try { x.meter = CACrisis.meter(); } catch (Throwable ignored) { }
        x.leagueCounts = leagueCounts();
        // past the limit the new colony costs an administrator's salary, and the hire spread over three years
        Room r = room(d, s);
        if (r.hire || r.governed + r.pending + 1 > r.max) {
            CAItems.ExpansionHire h = r.hire ? CAItems.expansionHire(s) : null;
            float salary = 2500f;
            try { salary = Global.getSettings().getInt("adminSalaryTier0"); } catch (Throwable ignored) { }
            x.adminMonthly = (h != null ? h.salary : salary) + (h != null ? (h.price + h.fee) / 36f : 0f);
        }
        return x;
    }

    /** The Persean League's crisis counts colonies now (not a member, its blockade not beaten). */
    static boolean leagueCounts() {
        try { return !PerseanLeagueMembership.isLeagueMember() && !PerseanLeagueMembership.isDefeatedBlockadeOrPunEx(); }
        catch (Throwable t) { return true; }
    }

    /** Your colonies (not Nexerelin outposts). */
    static List<MarketAPI> colonies() {
        List<MarketAPI> out = new ArrayList<MarketAPI>();
        try {
            for (MarketAPI m : Misc.getPlayerMarkets(false)) if (!CAEngine.isOutpost(m)) out.add(m);
        } catch (Throwable ignored) { }
        return out;
    }

    /** The candidates, best first, from the scan kept SCAN_DAYS (refresh: scan now). */
    static List<Prospect> prospects(CASettings s, boolean refresh) {
        return prospects(CAData.get(), s, refresh);
    }

    static List<Prospect> prospects(CAData d, CASettings s, boolean refresh) {
        String key = scope(s) + ":" + s.expandGoal + ":" + s.expandMethod + ":" + colonies().size();
        if (!refresh && SCAN.list != null && key.equals(SCAN.key) && CATime.fresh(SCAN_STAMP, SCAN_DAYS)) return SCAN.list;
        SCAN.list = scan(d, s);
        SCAN.key = key;
        CATime.stamp(SCAN_STAMP);
        return SCAN.list;
    }

    /** The scan, or null when there is none kept (for the page, which never scans by itself). */
    static List<Prospect> cached() {
        return SCAN.list != null && CATime.fresh(SCAN_STAMP, SCAN_DAYS) ? SCAN.list : null;
    }

    /** The best candidate that pays, or null. */
    static Prospect best(CAData d, CASettings s, boolean refresh) {
        for (Prospect p : prospects(d, s, refresh)) if (p.pays && p.location > 0f) return p;
        return null;
    }

    static List<Prospect> scan(CAData d, CASettings s) {
        Ctx x = ctx(d, s);
        SCAN.blocked.clear();
        List<Prospect> out = new ArrayList<Prospect>();
        boolean rich = false;
        try { rich = money(s, Math.max(1, mode(s))).rich; } catch (Throwable ignored) { }
        for (StarSystemAPI sys : systemsInScope(x)) {
            for (PlanetAPI p : sys.getPlanets()) {
                if (p.isStar()) continue;
                try {
                    String b = blocked(p, s, x, false);
                    if (b != null) { count(b); continue; }
                    Prospect r = score(p, s, x, rich);
                    if (r != null) out.add(r);
                } catch (Throwable t) { CAStep.warn("expansion scan", "candidate check failed at " + p.getName(), t); }
            }
        }
        Collections.sort(out, new Comparator<Prospect>() {
            public int compare(Prospect a, Prospect b) {
                if (a.pays != b.pays) return a.pays ? -1 : 1;
                int c = Float.compare(b.score(), a.score());
                return c != 0 ? c : b.coloniesThere - a.coloniesThere;
            }
        });
        CALog.quiet("Auto-expansion: scanned " + scopeName(s) + ": " + out.size() + " candidates, " + total(SCAN.blocked) + " planets ruled out");
        return out;
    }

    static void count(String reason) {
        Integer n = SCAN.blocked.get(reason);
        SCAN.blocked.put(reason, n == null ? 1 : n + 1);
    }

    static int total(Map<String, Integer> m) {
        int n = 0;
        for (Integer v : m.values()) n += v;
        return n;
    }

    /** The systems in scope: yours; with scope 1 those within NEAR_LY of one; with scope 2 also those within CAPITAL_LY of the capital. */
    static Set<StarSystemAPI> systemsInScope(Ctx x) {
        Set<StarSystemAPI> out = new LinkedHashSet<StarSystemAPI>(x.mine.keySet());
        if (x.scope < 1) return out;
        StarSystemAPI capSys = x.capital == null ? null : x.capital.getStarSystem();
        for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
            if (out.contains(sys)) continue;
            boolean in = false;
            for (StarSystemAPI m : x.mine.keySet()) if (Misc.getDistanceLY(sys.getLocation(), m.getLocation()) <= NEAR_LY) { in = true; break; }
            if (!in && x.scope >= 2 && capSys != null && Misc.getDistanceLY(sys.getLocation(), capSys.getLocation()) <= CAPITAL_LY) in = true;
            if (in) out.add(sys);
        }
        return out;
    }

    /**
     * Null when the planet may get a colony expedition, else why not. arrival: the check when a crew arrives (the scope, the
     * lists and the order itself don't count then).
     */
    static String blocked(PlanetAPI p, CASettings s, Ctx x, boolean arrival) {
        if (p == null || p.isStar()) return "Not a planet.";
        MarketAPI m = p.getMarket();
        StarSystemAPI sys = p.getStarSystem();
        if (m == null) return "Not surveyed.";
        if (sys == null) return "Not in a star system.";
        // the game's own founding rules
        String v = vanillaBlock(m.getSurveyLevel() == MarketAPI.SurveyLevel.FULL, m.isPlanetConditionMarketOnly() && !m.isInEconomy(),
                sys.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER), sys.hasTag(Tags.SYSTEM_ABYSSAL), sys.isDeepSpace(), Misc.hasUnexploredRuins(m));
        if (v != null) return v;
        // Nexerelin's colony rules (ColonyTargetValuator), read without Nexerelin
        if (sys.getMemoryWithoutUpdate().getBoolean("$nex_do_not_colonize") || m.getMemoryWithoutUpdate().getBoolean("$nex_do_not_colonize"))
            return "Marked not to be colonized.";
        if (p.hasTag(Tags.NOT_RANDOM_MISSION_TARGET) || m.isInvalidMissionTarget()) return "A mission site.";
        if (Misc.doesMarketHaveMissionImportantPeopleOrIsMarketMissionImportant(p)) return "Part of a mission.";
        if (m.hasCondition("pre_collapse_facility") && !m.getMemoryWithoutUpdate().getBoolean("$aotd_defeated_pcf")) return "A pre-collapse facility is still active.";
        if (m.hasCondition("VIC_VBomb_scar")) return "Virus-bombed.";
        if (m.isHidden()) return "Hidden.";
        // safety
        boolean yours = x.mine.containsKey(sys);
        if (nexusIn(sys)) return "A Remnant nexus is in the system.";
        boolean inhabited = !Global.getSector().getEconomy().getMarkets(sys).isEmpty();
        if (!inhabited) {
            for (CampaignFleetAPI f : sys.getFleets()) {
                if (f.isPlayerFleet() || f.getFaction() == null || f.getFaction().isPlayerFaction() || CASurvival.supportFleet(f)) continue;   // 0.3.0.9: support fleets too
                if (f.isStationMode()) return "A station that isn't yours is in the system.";
                if (f.getFaction().isHostileTo(Factions.PLAYER) && f.getFleetPoints() > 25) return "Hostile fleets in the system.";
            }
        }
        if (!yours && sys.hasTag(Tags.THEME_UNSAFE)) return "An unsafe system.";
        if (!yours && sys.hasPulsar()) return "A pulsar system.";
        // claims
        FactionAPI claim = null;
        try { claim = Misc.getClaimingFaction(p); } catch (Throwable ignored) { }
        if (claim != null && !claim.isPlayerFaction() && !claim.getId().equals(x.commission)) return "Claimed by " + claim.getDisplayName() + ".";
        String owner = null;
        if (x.nex && Global.getSettings().getModManager().isModEnabled("nexerelin")) { try { owner = CAExpansionNex.systemOwner(sys); } catch (Throwable ignored) { } }
        else owner = biggestOwner(sys);
        if (owner != null && !Factions.PLAYER.equals(owner) && !owner.equals(x.commission) && !Factions.NEUTRAL.equals(owner))
            return "Another faction holds the system.";
        // Auto Colonies' own
        if (p.getId().equals(x.settlement) || settlementOn(p)) return "Holds your Frontiers settlement.";
        if (x.tasc.contains(p.getId())) return "A TASC siphon station is planned there.";
        // a Nexerelin colony expedition headed there (any faction's): founding first would make it invade or cost reputation
        if (x.targeted.contains(p.getId())) return "A colony expedition is already headed there.";
        if (arrival) return null;
        if (x.ordered.contains(p.getId())) return "A colony expedition is already headed there.";
        Long until = x.skip.get(p.getId());
        if (until != null && until == Long.MAX_VALUE) return "On your never list.";
        if (until != null && x.now < until) return "Skipped after a failed or rejected expedition.";
        Long sysUntil = x.skip.get(sys.getId());
        if (sysUntil != null && x.now < sysUntil) return "Its system is skipped after a lost colony fleet.";
        if (!inScope(p, x)) return "Outside the scope.";
        return null;
    }

    /** Pure: the game's founding rules (PlanetSurveyPanel's "Establish colony" button). Null when they allow a colony. */
    static String vanillaBlock(boolean full, boolean condOnly, boolean cutOff, boolean abyssal, boolean deep, boolean ruins) {
        if (!full) return "Not fully surveyed.";
        if (!condOnly) return "Already colonized.";
        if (cutOff) return "The system is cut off from hyperspace.";
        if (abyssal) return "In abyssal hyperspace.";
        if (deep) return "In deep space.";
        if (ruins) return "Unexplored ruins (explore them first).";
        return null;
    }

    static boolean nexusIn(StarSystemAPI sys) {
        try { return sys != null && RemnantHostileActivityFactor.getRemnantNexus(sys) != null; } catch (Throwable t) { return false; }
    }

    /** Without Nexerelin: the faction of the system's biggest market that isn't hidden, or null. */
    static String biggestOwner(StarSystemAPI sys) {
        MarketAPI best = null;
        for (MarketAPI m : Global.getSector().getEconomy().getMarkets(sys)) {
            if (m.isHidden() || m.getFaction() == null) continue;
            if (best == null || m.getSize() > best.getSize()) best = m;
        }
        return best == null ? null : best.getFactionId();
    }

    /** Whether a Random Assortment of Things settlement orbits this planet (its station entity, found without RAT's classes). */
    static boolean settlementOn(PlanetAPI p) {
        try {
            for (SectorEntityToken e : p.getContainingLocation().getAllEntities()) {
                if (!"rat_frontiers_settlement".equals(e.getCustomEntityType())) continue;
                if (e.getOrbitFocus() == p) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    static boolean inScope(PlanetAPI p, Ctx x) {
        if (x.mine.containsKey(p.getStarSystem())) return true;
        if (x.scope < 1) return false;
        if (lyToColony(p, x) <= NEAR_LY) return true;
        return x.scope >= 2 && lyToCapital(p, x) <= CAPITAL_LY;
    }

    static float lyToColony(PlanetAPI p, Ctx x) {
        float best = Float.MAX_VALUE;
        for (MarketAPI m : x.colonies) if (m.getPrimaryEntity() != null) best = Math.min(best, Misc.getDistanceLY(p, m.getPrimaryEntity()));
        return best;
    }

    static float lyToCapital(PlanetAPI p, Ctx x) {
        if (x.capital == null || x.capital.getPrimaryEntity() == null) return lyToColony(p, x);
        return Misc.getDistanceLY(p, x.capital.getPrimaryEntity());
    }

    /** The candidate's plan, goal, value, location and cost. */
    static Prospect score(PlanetAPI p, CASettings s, Ctx x, boolean rich) {
        MarketAPI m = p.getMarket();
        StarSystemAPI sys = p.getStarSystem();
        Prospect r = new Prospect();
        r.planetId = p.getId(); r.planetName = p.getName(); r.systemId = sys.getId(); r.systemName = sys.getBaseName();
        r.sameSystem = x.mine.containsKey(sys);
        Integer there = x.mine.get(sys);
        r.coloniesThere = there == null ? 0 : there;
        // plan and goal: the plain plan (CAPlan.buildOwn on the condition market), or a Terraforming Made Easy world in reach
        CAData.Profile fixed = fixedGoal(s.expandGoal);
        CAData.Profile look = fixed != null ? fixed : CAData.Profile.BALANCED;
        CAPlan.Plan plain = null, world = null;
        try { plain = CAPlan.buildOwn(m, look, true); } catch (Throwable t) { CAStep.warn("expansion scan", "plan failed at " + p.getName(), t); }
        try { world = CAWorldPlanner.plan(m, look); } catch (Throwable ignored) { }
        String planName = world != null ? world.name : plain != null && plain.name != null ? plain.name : "General Colony";
        boolean hive = world != null && ("tme_hive_world".equals(world.world) || "tme_paradise_world".equals(world.world));
        CAData.Profile goal = fixed != null ? fixed : bestFit(planName, hive);
        if (fixed == null && goal != look) {
            try { plain = CAPlan.buildOwn(m, goal, true); } catch (Throwable ignored) { }
        }
        r.plan = planName;
        r.goal = goal.name();
        boolean farms = false;
        if (plain != null) for (CAPlan.Step st : plain.steps) if ("farming".equals(st.id) || "aquaculture".equals(st.id)) farms = true;
        boolean hab = m.hasCondition(Conditions.HABITABLE);
        // value at size VALUE_SIZE
        List<String> got = new ArrayList<String>();
        r.deposits = deposits(m, hab || farms, got);
        r.world = world != null && world.world != null ? worldBonus(world.world, goal) : 0f;
        float[] w = CAEngine.WEIGHTS[goal.ordinal()];
        r.hazard = hazardCost(m.getHazardValue(), w[6]);
        r.crisis = x.leagueCounts ? crisisCost(x.meter, leagueDelta(VALUE_SIZE)) : 0f;
        r.admin = x.adminMonthly;
        int h = 0, size = 0;
        for (MarketAPI o : x.hostile) {
            if (Misc.getDistanceLY(p, o.getPrimaryEntity()) > THREAT_LY) continue;
            h++; size += o.getSize();
        }
        r.threat = threatCost(h, size);
        r.value = valueOf(r.deposits, hab, r.world, r.hazard, r.crisis, r.admin, r.threat, CASurvival.keptShare());   // 0.3.0.9: survival mode's corruption
        // location
        r.ly = lyToColony(p, x);
        r.lyCapital = lyToCapital(p, x);
        r.location = locationMult(r.sameSystem, r.ly, r.lyCapital, x.scope);
        MarketAPI near = nearest(p, x.colonies);
        r.near = near == null ? null : near.getName();
        // cost: the method it would use today
        r.method = CREW;
        r.cost = crewCost(r.ly, goods());
        if (x.nex && Global.getSettings().getModManager().isModEnabled("nexerelin") && CABreaker.allowed(BREAKER) && s.expandMethod == 0 && !skipped(x.skip, x.now, NO_NEX)) {
            try {
                float fp = CASurplus.rich(s) ? NEX_FP_RICH : NEX_FP;
                if (CAExpansionNex.available(s, fp) && CAExpansionNex.source(p) != null) { r.method = NEX; r.cost = CAExpansionNex.cost(fp); }
            } catch (Throwable ignored) { }
        }
        r.fee = CABudget.constructionFee(s, r.cost);
        r.steps = stepsCost(plain, 2);
        r.payback = r.value > 0f ? (r.total() + r.steps) / r.value : Float.MAX_VALUE;
        r.pays = pays(r.value, r.total() + r.steps, rich);
        // the reason, short
        StringBuilder why = new StringBuilder();
        for (int i = 0; i < Math.min(3, got.size()); i++) why.append(i == 0 ? "" : ", ").append(got.get(i));
        if (hab) why.append(why.length() == 0 ? "" : ", ").append("habitable");
        if (r.world > 0f) why.append(why.length() == 0 ? "" : ", ").append(world.name).append(" in reach");
        if (why.length() == 0) why.append("no deposits");
        why.append(r.sameSystem ? "; same system as " + r.near : "; " + Math.round(r.ly) + " ly from " + r.near);
        r.reason = why.toString();
        return r;
    }

    static MarketAPI nearest(PlanetAPI p, List<MarketAPI> colonies) {
        MarketAPI best = null;
        float bd = Float.MAX_VALUE;
        for (MarketAPI m : colonies) {
            if (m.getPrimaryEntity() == null) continue;
            float ly = Misc.getDistanceLY(p, m.getPrimaryEntity());
            if (ly < bd) { bd = ly; best = m; }
        }
        return best;
    }

    static CAData.Profile fixedGoal(int goal) {
        CAData.Profile[] all = CAData.Profile.values();
        return goal >= 1 && goal <= all.length ? all[goal - 1] : null;
    }

    static CAData.Profile profile(String name) {
        try { return name == null ? CAData.Profile.BALANCED : CAData.Profile.valueOf(name); } catch (Throwable t) { return CAData.Profile.BALANCED; }
    }

    static String goalLabel(String name) { return profile(name).label; }

    /**
     * Pure: the goal a plan fits best (research 05 section 6.3): Forge to War production; Fortress, Bastion and a Death World
     * to Security; Paradise to Money, or Rapid growth when it's a Terraforming Made Easy hive or paradise world; a Hive
     * World to Rapid growth; a Waste World to Self-sufficiency; Mining Colony, Fuel Depot and Trade Hub to Money; anything
     * else Balanced.
     */
    static CAData.Profile bestFit(String planName, boolean hive) {
        String n = planName == null ? "" : planName.toLowerCase(Locale.ROOT);
        if (n.contains("forge")) return CAData.Profile.WAR;
        if (n.contains("fortress") || n.contains("bastion") || n.contains("death")) return CAData.Profile.SECURITY;
        if (n.contains("hive")) return CAData.Profile.GROWTH;
        if (n.contains("paradise")) return hive ? CAData.Profile.GROWTH : CAData.Profile.INCOME;
        if (n.contains("waste")) return CAData.Profile.SELF;
        if (n.contains("mining") || n.contains("fuel") || n.contains("trade")) return CAData.Profile.INCOME;
        return CAData.Profile.BALANCED;
    }

    /** Credits a month of the planet's deposits at VALUE_SIZE (food only on a habitable world or with a farming plan). */
    static float deposits(MarketAPI m, boolean food, List<String> names) {
        float v = 0f;
        for (MarketConditionAPI c : m.getConditions()) {
            String id = c.getId();
            String com = CAConditions.depositCommodity(id);
            if (com == null) continue;
            if ("food".equals(com) && !food) continue;
            Integer u = depositUnitsAt(id, VALUE_SIZE);
            if (u == null || u <= 0) continue;
            v += u * basePrice(com) * CAEngine.unitValue * DEPOSIT_SHARE;
            if (names != null) names.add(CAConditions.name(id).toLowerCase(Locale.ROOT));
        }
        return v;
    }

    /** Units a deposit condition gives its industry at this colony size (CAConditions.depositUnits with a size); null if it isn't one. */
    static Integer depositUnitsAt(String cond, int size) {
        String com = ResourceDepositsCondition.COMMODITY.get(cond);
        Integer mod = ResourceDepositsCondition.MODIFIER.get(cond);
        if (com == null || mod == null) { com = CAEngine.DEPOSIT.get(cond); mod = CAEngine.DEPOSIT_MOD.get(cond); }
        if (com == null || mod == null) return null;
        Integer base = ResourceDepositsCondition.BASE_MODIFIER.get(com);
        if (base == null) base = CAEngine.BASE_MOD.containsKey(com) ? CAEngine.BASE_MOD.get(com) : 0;
        int sz = ResourceDepositsCondition.BASE_ZERO.contains(com) ? 0 : size;
        return sz + base + mod;
    }

    /** A reachable Terraforming Made Easy world: WORLD_SHARE of CAConditions' bonus (20,000, and 60,000 more when it fits the goal). */
    static float worldBonus(String world, CAData.Profile goal) {
        CAData.Profile[] fits = CAConditions.WORLD_FITS.get(world);
        if (fits == null) return 0f;
        float v = 20000f;
        for (CAData.Profile f : fits) if (f == goal) v += 60000f;
        return WORLD_SHARE * v;
    }

    /** Pure: hazard costs upkeep (UPKEEP_REF per +100%) and growth (as CAConditions.value); under 100% it's a gain. */
    static float hazardCost(float hazardValue, float growthWeight) {
        float over = hazardValue - 1f;
        return over * UPKEEP_REF + over * 10f * growthWeight / 2f;
    }

    /** Pure: the Persean League's monthly progress from one of your colonies at this size (StandardPerseanLeagueActivityCause). */
    static int leagueDelta(int size) {
        if (size <= 4) return size;
        if (size == 5) return size + 2;
        if (size == 6) return size + 4;
        return size + 6;
    }

    /** Credits a month that `delta` more monthly League progress costs (CACrisis.progressCost: the multiplier, the cap, Low profile's x10). */
    static float crisisCost(CACrisis.Base meter, int delta) {
        try {
            if (meter == null) return delta * CACrisis.pointCost();
            return CACrisis.progressCost(meter, meter.raw, meter.raw + delta);
        } catch (Throwable t) { return 0f; }
    }

    /** Pure: nearby hostile markets as a monthly cost, 0.1 each and 0.02 per size, at most THREAT_CAP, times THREAT_REF. */
    static float threatCost(int hostileMarkets, int totalSize) {
        return Math.min(THREAT_CAP, 0.1f * hostileMarkets + 0.02f * totalSize) * THREAT_REF;
    }

    /** Pure: credits a month at VALUE_SIZE. */
    static float valueOf(float deposits, boolean habitable, float world, float hazard, float crisis, float admin, float threat) {
        return valueOf(deposits, habitable, world, hazard, crisis, admin, threat, 1f);
    }

    /**
     * Pure (0.3.0.9, JTExpansionCheck): credits a month at VALUE_SIZE with survival mode's corruption: the income parts
     * (deposits, the habitable bonus, a Terraforming Made Easy world) count at `kept`, the share of one more credit of colony
     * income kept (CASurvival.keptShare, 1 outside survival mode); the costs (hazard upkeep and growth, crisis progress, the
     * administrator, threat) count in full. kept outside 0-1 or NaN counts as 1.
     */
    static float valueOf(float deposits, boolean habitable, float world, float hazard, float crisis, float admin, float threat, float kept) {
        float k = kept >= 0f && kept < 1f ? kept : 1f;
        return (deposits + (habitable ? HAB_BONUS : 0f) + world) * k - hazard - crisis - admin - threat;
    }

    /**
     * Pure: the location multiplier. Same system as a colony x1.3; within NEAR_LY of one x1.0 (scope 1 and 2); farther x0.7
     * (scope 2, within CAPITAL_LY of the capital); out of scope 0. Then less PER_LY per light-year from the capital, at
     * least MIN_LOCATION.
     */
    static float locationMult(boolean same, float lyToColony, float lyToCapital, int scope) {
        float base;
        if (same) base = SAME_SYSTEM;
        else if (scope >= 1 && lyToColony <= NEAR_LY) base = NEAR;
        else if (scope >= 2 && lyToCapital <= CAPITAL_LY) base = FAR;
        else return 0f;
        return base * Math.max(MIN_LOCATION, 1f - PER_LY * Math.max(0f, lyToCapital));
    }

    /**
     * Pure: pays back. At least MIN_VALUE a month and within PAYBACK_MONTHS; while rich any positive value, with the cost at RICH_PRICE.
     * 0.3.0.9: value is valueOf's, with survival mode's corruption taken off the income (so the 10,000 line and the payback
     * are on income you keep); cost is the expedition and the plan's first two buildings, which corruption doesn't touch.
     */
    static boolean pays(float value, float cost, boolean rich) {
        if (!(value > 0f)) return false;
        if (rich) return value * PAYBACK_MONTHS >= cost * CATuning.RICH_PRICE;
        return value >= MIN_VALUE && value * PAYBACK_MONTHS >= cost;
    }

    /** The first n buildings of the plan, at their build cost (the free Spaceport left out). */
    static float stepsCost(CAPlan.Plan plan, int n) {
        if (plan == null) return 0f;
        float v = 0f;
        int k = 0;
        for (CAPlan.Step st : plan.steps) {
            if (k >= n) break;
            if (st.kind != CAPlan.Kind.BUILD || "spaceport".equals(st.id) || "megaport".equals(st.id)) continue;
            try { v += Global.getSettings().getIndustrySpec(st.id).getCost(); k++; } catch (Throwable ignored) { }
        }
        return v;
    }

    // ---------------- lists ----------------

    static void skip(CAData d, String id, float days) {
        if (id == null) return;
        long until = days >= Float.MAX_VALUE ? Long.MAX_VALUE : Global.getSector().getClock().getTimestamp() + (long) (days * DAY_MS);
        d.expandSkip().put(id, until);
    }

    static boolean skipped(CAData d, String id) {
        try { return skipped(d.expandSkip(), Global.getSector().getClock().getTimestamp(), id); } catch (Throwable t) { return false; }
    }

    /** Pure: an id is skipped until its stored game timestamp (Long.MAX_VALUE: always). */
    static boolean skipped(Map<String, Long> skip, long now, String id) {
        Long until = skip == null ? null : skip.get(id);
        return until != null && now < until;
    }

    static PlanetAPI planet(String id) {
        if (id == null) return null;
        SectorEntityToken e = Global.getSector().getEntityById(id);
        return e instanceof PlanetAPI ? (PlanetAPI) e : null;
    }

    /** The colony of yours on this planet, or null. */
    static MarketAPI colonyOn(String planetId) {
        PlanetAPI p = planet(planetId);
        MarketAPI m = p == null ? null : p.getMarket();
        return m != null && m.isPlayerOwned() && !m.isPlanetConditionMarketOnly() ? m : null;
    }

    static String methodName(String method) { return NEX.equals(method) ? "Nexerelin colony fleet" : "Autopilot crew"; }

    static String lower(String reason) {
        String r = reason.endsWith(".") ? reason.substring(0, reason.length() - 1) : reason;
        return r.isEmpty() ? r : Character.toLowerCase(r.charAt(0)) + r.substring(1);
    }

    static String nothingReason(CASettings s) {
        List<Prospect> l = cached();   // 0.3.0.9: only a scan still fresh (SCAN_DAYS); the counts below are that scan's
        if (l != null && !l.isEmpty()) return "No planet pays back within " + Math.round(PAYBACK_MONTHS) + " months (best: " + l.get(0).planetName + ", "
                + Misc.getDGSCredits(l.get(0).value) + " a month).";
        String top = null;
        int n = 0;
        if (l != null) for (Map.Entry<String, Integer> e : SCAN.blocked.entrySet()) if (e.getValue() > n) { n = e.getValue(); top = e.getKey(); }
        return "No planet in scope (" + scopeName(s) + ") can take a colony" + (top != null ? "; mostly: " + lower(top) + " (" + n + ")" : "") + ".";
    }

    // ---------------- the Expansion page (wired by WS-F) ----------------

    /** The Expansion page: mode, status, colony limit, the proposal, expeditions under way, the best candidates. Never scans by itself. */
    public static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        t.addPara("Auto-expansion: %s.", 6f, hl, modeName(s));   // 0.3.0.0 (WS-F3 fix pass 2): scope, money and method are in Settings, Advanced
        String st = SCAN.status != null && SCAN.statusDay == CATime.today() ? SCAN.status : mode(s) == 0 ? offReason(s) : null;
        if (st != null) t.addPara(CALog.esc(st), gray, 4f);
        try {
            Room r = room(d, s);
            t.addPara("Colonies governed: %s of %s. Administrators: %s of %s, %s idle.", 4f, hl,
                    String.valueOf(r.governed), String.valueOf(r.max), String.valueOf(r.admins), String.valueOf(r.maxAdmins), String.valueOf(r.idle));
            if (Global.getSettings().getModManager().isModEnabled("nexerelin") && nexLoaded()) {
                int[] pts = CAExpansionNex.points();
                if (pts != null) t.addPara("Nexerelin fleet request points: %s of %s.", 2f, hl, String.valueOf(pts[0]), String.valueOf(pts[1]));
            }
        } catch (Throwable ignored) { }
        if (d.expandPendingId != null) {
            t.addPara("Waiting for approval: %s for %s.", 8f, hl, d.expandPendingName, Misc.getDGSCredits(d.expandPendingCost));
            ButtonAPI a = t.addButton("Approve", new String[]{"expand", "approve", d.expandPendingId}, hl, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 4f);
            ButtonAPI rj = t.addButton("Reject", new String[]{"expand", "reject", d.expandPendingId}, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 0f);
            rj.getPosition().rightOfTop(a, 6f);
            CALog.endRow(t, a);
        }
        if (!d.expandOrders().isEmpty()) {
            t.addPara("Expeditions under way:", 8f);
            for (Order o : d.expandOrders()) {
                float left = Math.max(0f, o.days - Global.getSector().getClock().getElapsedDaysSince(o.sent));
                t.addPara("%s: %s, about %s days left.", 2f, hl, o.planetName, methodName(o.method), String.valueOf(Math.round(left)));
            }
        }
        List<Prospect> list = cached();
        ButtonAPI scan = t.addButton(list == null ? "Scan for candidates" : "Scan again", new String[]{"expand", "scan", ""}, base, dark, Alignment.MID, CutStyle.TL_BR, 200f, 22f, 8f);
        CALog.endRow(t, scan);
        if (list == null) { t.addPara("Not scanned yet.", gray, 4f); }
        else if (list.isEmpty()) { t.addPara(CALog.esc(nothingReason(s)), gray, 4f); }
        else {
            t.addPara("Best candidates (value at size %s):", 6f, hl, String.valueOf(VALUE_SIZE));
            for (int i = 0; i < Math.min(TOP, list.size()); i++) {
                Prospect p = list.get(i);
                t.addPara("%s. %s, %s: %s, goal %s. %s a month; %s by %s (+ %s fee); payback %s months.", 4f, hl,
                        String.valueOf(i + 1), p.planetName, p.systemName, p.plan, goalLabel(p.goal), Misc.getDGSCredits(p.value),
                        Misc.getDGSCredits(p.cost), methodName(p.method), Misc.getDGSCredits(p.fee),
                        p.payback >= 1000f ? "over 1,000" : String.valueOf(Math.max(1, Math.round(p.payback))));
                t.addPara(CALog.esc(p.reason + (p.pays ? "" : ". Payback too long")), gray, 2f);
                ButtonAPI f = t.addButton("Found now", new String[]{"expand", "found", p.planetId}, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 20f, 2f);
                ButtonAPI n = t.addButton("Never this planet", new String[]{"expand", "never", p.planetId}, base, dark, Alignment.MID, CutStyle.TL_BR, 160f, 20f, 0f);
                n.getPosition().rightOfTop(f, 6f);
                f.setEnabled(CABuild.credits() - p.total() >= s.reserve);
                CALog.endRow(t, f);
            }
        }
        int never = 0;
        for (Long v : d.expandSkip().values()) if (v != null && v == Long.MAX_VALUE) never++;
        if (never > 0) {
            t.addPara("Planets on your never list: %s.", 8f, hl, String.valueOf(never));
            ButtonAPI c = t.addButton("Clear the never list", new String[]{"expand", "clearnever", ""}, base, dark, Alignment.MID, CutStyle.TL_BR, 200f, 20f, 2f);
            CALog.endRow(t, c);
        }
    }

    /**
     * The Expansion page's buttons {"expand", action, id}: approve, reject, found (a planet id), never (a planet id),
     * clearnever, scan, confirm (the settings page's first-time dialog). Logs what it did; returns null, or why the action
     * could not be carried out (for the caller to show).
     */
    public static String handle(String action, String id, CAData d, CASettings s) {
        if ("confirm".equals(action)) { s.expandConfirmed = true; return null; }
        if ("scan".equals(action)) { prospects(d, s, true); return null; }
        if ("clearnever".equals(action)) {
            int n = 0;
            for (Iterator<Map.Entry<String, Long>> it = d.expandSkip().entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Long> e = it.next();
                if (e.getValue() != null && e.getValue() == Long.MAX_VALUE) { it.remove(); n++; }
            }
            SCAN.list = null;
            CALog.did("Auto-expansion: never list cleared (" + n + (n == 1 ? " planet)." : " planets)."), false);
            return null;
        }
        if ("reject".equals(action)) {
            if (d.expandPendingId == null) return null;
            String name = d.expandPendingName;
            skip(d, d.expandPendingId, SKIP_DAYS);
            clearPending(d, null);
            SCAN.list = null;
            CALog.did(name + ": Colony expedition rejected. Not proposed again for " + Math.round(SKIP_DAYS) + " days.", false);
            return null;
        }
        if ("never".equals(action)) {
            PlanetAPI p = planet(id);
            if (p == null) return "That planet is gone.";
            d.expandSkip().put(id, Long.MAX_VALUE);
            if (id.equals(d.expandPendingId)) clearPending(d, null);
            SCAN.list = null;
            CALog.did(p.getName() + ": Colony expedition: on the never list (Expansion page).", false);
            return null;
        }
        if ("approve".equals(action) || "found".equals(action)) {
            String pid = "approve".equals(action) ? d.expandPendingId : id;
            if (pid == null) return null;
            Prospect p = null;
            List<Prospect> list = cached();   // 0.3.0.9: a stale scan is scored again below, as a planet not in it
            if (list != null) for (Prospect x : list) if (x.planetId.equals(pid)) p = x;
            if (p == null) {
                PlanetAPI planet = planet(pid);
                if (planet == null) return "That planet is gone.";
                Ctx x = ctx(d, s);
                String b = blocked(planet, s, x, false);
                if (b != null) return b;
                p = score(planet, s, x, false);
            }
            String err = launch(d, s, p, true);
            if (err != null && "approve".equals(action)) CALog.quiet("Auto-expansion: approval for " + p.planetName + " not carried out: " + err);
            return err;
        }
        return null;
    }
}
