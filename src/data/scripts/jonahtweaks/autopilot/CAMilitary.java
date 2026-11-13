package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.0.0 (WS-C3 rung 3, research 03 sections 4f and 5.3): the Military rung's upgrades. New class.
 *
 * With the money tier at Idle money or higher, the Military rung's room upgrades military buildings that are already
 * there at Auto colonies:
 * - a station one tier up, a colony size before the colony plan's own line (CAStations: Battlestation from size 6, Star
 *   Fortress from 8, a size sooner at the capital; here 5 and 7, or 4 and 6 at the capital), along the station's own
 *   upgrade line;
 * - Military Base to High Command;
 * - Patrol HQ to Military Base, where the colony has a free industry slot and the planner found nothing worth starting
 *   there at its last look (CAPlanner.idle); a colony with a pick waiting keeps the slot for it.
 * Upgrades only, no new buildings, so a colony's one construction queue stays the planner's.
 * Limits: the rung's room (CARich.room: its share of the pool plus what came down, less its spending; price and the
 * construction fee); free cash; the planner's earmark and upkeep hold (CAPlanner.setAside); the stability floor and slot
 * count (CABuild.start); one upgrade a day across the empire; and the upkeep line. Auto colonies only (Approve colonies get
 * their builds proposed by the planner), not outposts, not under siege, not in a fast buildout. The price is taken by
 * CABuild.start; the fee is paid from the pool (CABudget.payFromPool) and both are recorded against the Military rung. Its
 * new upkeep is booked in the colony's monthly line; the rung's own 15% line caps it, so it doesn't use the planner's month
 * of new-upkeep room.
 * 0.3.0.0 (C3 fix, research 03 section 5.3 "new upkeep at most 15% of N"): the upkeep line counts the upkeep the rung's own
 * upgrades added (addedUpkeep), started ones included, plus the next one's, against UPKEEP_SHARE of colony net. Military
 * upkeep that was there before the ladder doesn't count. Each upgrade it starts leaves a saved CAMark stamp
 * "milup:<marketId>|<building now>|<building before the rung's first upgrade there>" (ids and a timestamp only); a later
 * upgrade of the same building carries the first one's start, and a stamp whose building is gone is cleared.
 * Each look says what the rung wants (lastWant: the cheapest upgrade that fits the upkeep line and the room), which
 * CARich adds into the Military rung's one want, so what the rung doesn't need goes down to the Shipyard.
 * On Recommend only it starts nothing: the upgrade it would start is proposed on the Budget panel (Approve, Reject) and
 * logged once; Reject holds that upgrade at that colony off for REJECT_DAYS.
 */
public final class CAMilitary {
    private CAMilitary() { }

    /** Days a rejected proposal holds that upgrade at that colony off. */
    static final float REJECT_DAYS = 30f;
    /** CAMark key prefix: a rejected proposal, "milno:<marketId>|<building id>". */
    static final String REJECTED = "milno:";
    /** 0.3.0.0 (C3 fix): CAMark key prefix: an upgrade the rung started, "milup:<marketId>|<building now>|<building it started from>". */
    static final String ADDED = "milup:";
    /** Budget panel buttons (CARich.handle routes them here). */
    static final String KEY = "jcm:military:", APPROVE = KEY + "approve", REJECT = KEY + "reject";

    /** The upkeep the rung's upgrades added stays at or under this share of colony net. */
    static final float UPKEEP_SHARE = 0.15f;
    /** Colony sizes from which the rung upgrades a station to tier 2 and 3 (the plan's line, CAStations, less one). */
    static final int TIER2_SIZE = CAStations.TIER2_SIZE - 1, TIER3_SIZE = CAStations.TIER3_SIZE - 1;

    /** One upgrade it could start. */
    static final class Up {
        MarketAPI m;
        Industry from;
        String to, name;
        float cost, fee, days, upkeep;
        float total() { return cost + fee; }
    }

    /** The game day of the last start (one a day), the last recommendation logged, and the proposal waiting. Session only. */
    static int startedDay = Integer.MIN_VALUE;
    static String recommended;
    static Up pending;
    /** 0.3.0.0 (C3 fix): what the rung's upgrades want this month as of the last look (CARich.militaryWant). Session only. */
    static float lastWant = 0f;

    static void reset() { startedDay = Integer.MIN_VALUE; recommended = null; pending = null; lastWant = 0f; }

    // ---------------- pure rules (JTRichCheck, JTLadderCheck) ----------------

    /**
     * Pure: the upkeep line: the upkeep the rung's upgrades added so far plus this upgrade's at most UPKEEP_SHARE of colony
     * net (none with colony net 0 or less).
     */
    static boolean upkeepFits(float addedSoFar, float added, float colonyNet) {
        if (!(colonyNet > 0f)) return false;
        return Math.max(0f, addedSoFar) + Math.max(0f, added) <= UPKEEP_SHARE * colonyNet + 1e-3f;
    }

    /** Pure: whether a station of tier `next` may go up at this size (a size sooner at the capital). */
    static boolean stationSize(int next, int size, boolean capital) {
        int line = next >= 3 ? TIER3_SIZE : next == 2 ? TIER2_SIZE : Integer.MAX_VALUE;
        return size >= line - (capital ? 1 : 0);
    }

    /** Pure, 0.3.0.0 (C3 fix): the stamp key of an upgrade at a colony: the building now and the one the rung started from. */
    static String addedKey(String marketId, String to, String from) { return ADDED + marketId + "|" + to + "|" + from; }

    /** Pure, 0.3.0.0 (C3 fix): a stamp key's parts {marketId, building now, building started from}, or null. */
    static String[] addedParts(String key) {
        if (key == null || !key.startsWith(ADDED)) return null;
        String[] p = key.substring(ADDED.length()).split("\\|", -1);
        if (p.length != 3 || p[0].isEmpty() || p[1].isEmpty() || p[2].isEmpty()) return null;
        return p;
    }

    /**
     * Pure, 0.3.0.0 (C3 fix): where an upgrade from `from` at a colony starts its chain: the start of the rung's stamp whose
     * building is `from` (the rung upgraded it before), else `from` itself.
     */
    static String chainStart(Collection<String> keys, String marketId, String from) {
        for (String k : keys) {
            String[] p = addedParts(k);
            if (p != null && p[0].equals(marketId) && p[1].equals(from)) return p[2];
        }
        return from;
    }

    /**
     * Pure (0.3.0.9, JTLadderCheck): why the rung can't upgrade at a colony now, in words for the Approve line, or null. auto:
     * an Auto colony of yours (not an outpost); noBuildings: a Coronal Hypershunt or a colony that allows no buildings. The
     * automatic path (candidates) and Approve read this one rule.
     */
    static String colonyBlock(boolean auto, boolean buildout, boolean siege, boolean noBuildings) {
        if (!auto) return "is not an Auto colony of yours now";
        if (siege) return "is under siege";
        if (buildout) return "is in its fast buildout";
        if (noBuildings) return "allows no buildings";
        return null;
    }

    // ---------------- the game ----------------

    /** 0.3.0.9: colonyBlock for a colony as it is now. */
    static String colonyBlock(MarketAPI m) {
        boolean auto = false;
        try { auto = m.isPlayerOwned() && CAItems.autoMarkets().contains(m); } catch (Throwable ignored) { }
        CAData.Colony c = CAData.get().colony(m.getId());
        return colonyBlock(auto, c.buildout, auto && CASiege.held(m), CAHypershunt.is(m) || m.hasTag("market_no_industries_allowed"));
    }

    /**
     * 0.3.0.0 (C3 fix): upkeep a month the rung's own upgrades added at your colonies: per stamp, the building's base upkeep
     * now less the one it started from. Upgrades still under way count. A stamp whose colony is no longer yours, or whose
     * building is gone (removed, or the upgrade cancelled), is cleared.
     */
    static float addedUpkeep() {
        float u = 0f;
        for (String k : CAMark.keys(ADDED)) {
            try {
                String[] p = addedParts(k);
                if (p == null) { CAMark.clear(k); continue; }
                MarketAPI m = CAAuto.market(p[0]);
                if (m == null || !m.isPlayerOwned() || !present(m, p[1])) { CAMark.clear(k); continue; }
                IndustrySpecAPI to = CAPlan.spec(p[1]), from = CAPlan.spec(p[2]);
                if (to == null || from == null) continue;
                u += Math.max(0f, CAEngine.baseUpkeep(m, to) - CAEngine.baseUpkeep(m, from));
            } catch (Throwable t) { CAStep.warn("money tiers", "military rung upkeep read failed", t); }
        }
        return u;
    }

    /**
     * 0.3.0.0 (C3 fix): whether the building `id` stands at the colony: there, an upgrade to it under way, or a building
     * upgraded past it (its downgrade line leads to it: a Military Base the rung started, upgraded to High Command since).
     */
    static boolean present(MarketAPI m, String id) {
        for (Industry ind : m.getIndustries()) {
            try {
                IndustrySpecAPI sp = ind.getSpec();
                if (id.equals(ind.getId())) return true;
                if (ind.isUpgrading() && id.equals(sp.getUpgrade())) return true;
                String down = sp.getDowngrade();
                for (int i = 0; down != null && i < 6; i++) {
                    if (id.equals(down)) return true;
                    IndustrySpecAPI ds = CAPlan.spec(down);
                    down = ds == null ? null : ds.getDowngrade();
                }
            } catch (Throwable ignored) { }
        }
        return false;
    }

    /** 0.3.0.0 (C3 fix): stamps an upgrade the rung started, carrying the start of an earlier stamp of the same building. */
    static void stamp(MarketAPI m, String from, String to) {
        List<String> keys = CAMark.keys(ADDED + m.getId() + "|");
        String start = chainStart(keys, m.getId(), from);
        for (String k : keys) {
            String[] p = addedParts(k);
            if (p != null && p[1].equals(from)) CAMark.clear(k);
        }
        CAMark.set(addedKey(m.getId(), to, start), CAMark.now());
    }

    /** The upgrades it could start now, cheapest first. */
    static List<Up> candidates(CASettings s, List<MarketAPI> auto) {
        List<Up> out = new ArrayList<Up>();
        for (MarketAPI m : auto) {
            try {
                CAData.Colony c = CAData.get().colony(m.getId());
                // 0.3.0.9: the one rule Approve reads too (colonyBlock); auto holds: these come from CAItems.autoMarkets
                if (colonyBlock(true, c.buildout, CASiege.held(m), CAHypershunt.is(m) || m.hasTag("market_no_industries_allowed")) != null) continue;
                for (Industry ind : m.getIndustries()) {
                    if (ind.isBuilding() || ind.isUpgrading() || !CAStockpile.military(ind)) continue;
                    String next = ind.getSpec().getUpgrade();
                    if (next == null) continue;
                    IndustrySpecAPI spec = CAPlan.spec(next);
                    if (spec == null || !CADiagnostics.usable(next)) continue;
                    boolean station = ind.getSpec().hasTag("station");
                    if (station) {
                        int t = CAPlan.stationTier(spec);
                        if (t <= 0 || !stationSize(t, m.getSize(), CACapital.is(m))) continue;
                    } else if (ind.getSpec().hasTag("patrol")) {
                        // Patrol HQ to Military Base becomes an industry: a free slot, and nothing worth starting there
                        if (CABuild.freeSlots(m) <= 0 || !Boolean.TRUE.equals(CAPlanner.idle.get(m.getId()))) continue;
                    } else if (!ind.getSpec().hasTag("military")) {
                        continue;   // High Command and mod buildings past it: nothing further here
                    }
                    if (!ind.canUpgrade()) continue;
                    Industry inst = spec.getNewPluginInstance(m);
                    Up u = new Up();
                    u.m = m; u.from = ind; u.to = next; u.name = spec.getName();
                    u.cost = inst.getBuildCost(); u.days = inst.getBuildTime();
                    u.fee = CABudget.constructionFee(s, u.cost);
                    u.upkeep = Math.max(0f, CAEngine.baseUpkeep(m, spec) - CAEngine.baseUpkeep(m, ind.getSpec()));
                    if (u.cost > 0f) out.add(u);
                }
            } catch (Throwable t) { CAStep.warn("money tiers", "military rung look failed at " + m.getName(), t); }
        }
        Collections.sort(out, new Comparator<Up>() { public int compare(Up a, Up b) { return Float.compare(a.total(), b.total()); } });
        return out;
    }

    // ---------------- daily ----------------

    /**
     * Daily from CARich.run while the Military rung is open: one upgrade a day at most, within the rung's room (room) and the
     * upkeep line. 0.3.0.0 (C3 fix): says what it wants (lastWant): the cheapest upgrade that fits the upkeep line and the
     * room, 0 when none or once one started today.
     */
    public static void run(CAData d, CASettings s, float room) {
        if (CALog.whatIf()) return;
        if (!CARich.open(CARich.MILITARY)) { lastWant = 0f; pending = null; return; }
        int today = CATime.today();
        if (today == startedDay) return;
        List<MarketAPI> auto = CAItems.autoMarkets();
        if (auto.isEmpty()) { lastWant = 0f; pending = null; return; }
        float net = CABudget.netIncome(s), added = addedUpkeep();
        long now = CAMark.now();
        boolean recommend = CARich.mode(CARich.MILITARY) == CARich.RECOMMEND;
        for (Up u : candidates(s, auto)) {
            if (!upkeepFits(added, u.upkeep, net)) continue;
            if (recommend && rejected(u, now)) continue;
            if (u.total() > room) break;   // cheapest first: nothing after it fits this month's room either
            lastWant = u.total();
            if (recommend) {
                pending = u;
                String key = u.m.getId() + ":" + u.to;
                if (!key.equals(recommended)) {
                    recommended = key;
                    CAApprovals.say(u.m.getName() + ": Recommends upgrading to " + u.name + " (Military rung, " + Misc.getDGSCredits(u.total())
                            + " with the fee, " + Misc.getDGSCredits(u.upkeep) + " more upkeep a month). Approve on the Budget panel.", false);
                }
                CARich.note(CARich.MILITARY, "proposes " + u.name + " at " + u.m.getName() + "; Approve or Reject on this panel");
                return;
            }
            String err = start(s, u, net, added);
            if (err == null) { startedDay = today; lastWant = 0f; return; }
            if (err.startsWith("waits")) { CARich.note(CARich.MILITARY, u.name + " at " + u.m.getName() + " " + err); return; }
            lastWant = 0f;   // this one can't start here (slot, stability): the next one
        }
        lastWant = 0f;
        if (recommend) pending = null;
    }

    static String rejectKey(Up u) { return REJECTED + u.m.getId() + "|" + u.from.getId(); }

    static boolean rejected(Up u, long now) {
        Long at = CAMark.at(rejectKey(u));
        if (at == null) return false;
        if (now >= at && CAMark.days(at, now) < REJECT_DAYS) return true;
        CAMark.clear(rejectKey(u));
        return false;
    }

    // ---------------- panel (wired by WS-F) ----------------

    /** The Budget panel's military part: the proposal with Approve and Reject (Recommend only). */
    public static void render(com.fs.starfarer.api.ui.TooltipMakerAPI t) {
        Up u = pending;
        if (t == null || u == null) return;
        java.awt.Color hl = Misc.getHighlightColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        t.addPara("Military proposal: %s at " + CALog.esc(u.m.getName()) + " for %s with the fee, +%s upkeep a month.", 4f, hl,
                u.name, Misc.getDGSCredits(u.total()), Misc.getDGSCredits(u.upkeep));
        t.addButton("Approve", APPROVE, base, dark, com.fs.starfarer.api.ui.Alignment.MID, com.fs.starfarer.api.ui.CutStyle.TL_BR, 120f, 22f, 4f);
        t.addButton("Reject", REJECT, base, dark, com.fs.starfarer.api.ui.Alignment.MID, com.fs.starfarer.api.ui.CutStyle.TL_BR, 120f, 22f, 4f);
    }

    /**
     * Approve or Reject the proposal. Returns a message for the log, "" when it was handled and logged already, or null when
     * the key isn't one of these.
     */
    public static String handle(String key) {
        if (APPROVE.equals(key)) {
            Up u = pending;
            if (u == null) return "Military: no proposal to approve";
            pending = null;
            CASettings s = CAData.settings();
            // checked again now: the building, the money and the upkeep line may have moved since it was proposed
            // 0.3.0.9: and the colony: still an Auto colony of yours, not under siege, not in a fast buildout (as candidates)
            String block = colonyBlock(u.m);
            if (block != null) return "Military: " + u.m.getName() + " " + block;
            if (u.from.isUpgrading() || u.from.isBuilding() || u.m.getIndustry(u.from.getId()) != u.from) return "Military: that building changed since the proposal";
            if (u.total() > CARich.room(CARich.MILITARY) + 1f) return "Military: proposal over its share of the pool";
            float net = CABudget.netIncome(s), added = addedUpkeep();
            if (!upkeepFits(added, u.upkeep, net)) return "Military: the upkeep the Military rung added would pass 15% of colony net now";
            String err = start(s, u, net, added);
            if (err != null) return u.m.getName() + ": " + u.name + " " + err;
            startedDay = CATime.today();
            lastWant = 0f;
            return "";
        }
        if (REJECT.equals(key)) {
            Up u = pending;
            pending = null;
            if (u == null) return "Military: no proposal to reject";
            CAMark.set(rejectKey(u), CAMark.now());
            lastWant = 0f;
            return u.m.getName() + ": " + u.name + " rejected; not proposed again for " + Math.round(REJECT_DAYS) + " days";
        }
        return null;
    }

    /**
     * Starts one upgrade and pays for it. Null when started, else why not (nothing charged then). added: the upkeep the
     * rung's upgrades added so far. 0.3.0.0 (C3 fix): waits when its price and fee are more than free cash (the reserve,
     * production still to pay and the runway stay), and stamps the upgrade for the upkeep line.
     */
    static String start(CASettings s, Up u, float net, float added) {
        String held = CAPlanner.setAside(u.total(), 0f, u.upkeep, u.m);
        if (held != null) return "waits: " + held;
        if (CABuild.credits() - u.total() < s.reserve) return "waits: it would go into your credit reserve";
        float free = CABudget.freeCash(s);
        if (!(u.total() <= free)) return "waits: free cash (" + Misc.getDGSCredits(Math.max(0f, free)) + ") doesn't cover it";
        CAEngine.Pick p = new CAEngine.Pick();
        p.id = u.to; p.name = u.name; p.verb = "Upgrade"; p.upgrade = true; p.existing = u.from;
        p.cost = u.cost; p.days = u.days; p.upkeep = u.upkeep;
        String fromId = u.from.getId();
        String err = CABuild.start(u.m, p);   // takes the price; the stability floor and the slot count are checked there
        if (err != null) return err;
        CABudget.payFromPool(s, CARich.MILITARY, u.m.getId(), 0f, u.fee);
        CARich.spent(CARich.MILITARY, u.cost);
        CALog.spent(u.m.getId(), u.cost, 0f, u.upkeep);
        stamp(u.m, fromId, u.to);
        CAPlanner.wake("slot:" + u.m.getId());
        CARich.note(CARich.MILITARY, "upgrading to " + u.name + " at " + u.m.getName());
        CALog.did(u.m.getName() + ": Upgrading to " + u.name + " (Military rung, " + Misc.getDGSCredits(u.total()) + " with the fee).");
        CALog.quiet("Military rung detail: " + u.m.getName() + ", " + u.name + " for " + Misc.getDGSCredits(u.cost) + " plus a " + Misc.getDGSCredits(u.fee)
                + " fee, " + Misc.getDGSCredits(u.upkeep) + " more upkeep a month; upkeep the Military rung added " + Misc.getDGSCredits(added + u.upkeep)
                + " of " + Misc.getDGSCredits(UPKEEP_SHARE * net) + " allowed (15% of colony net)");
        return null;
    }
}
