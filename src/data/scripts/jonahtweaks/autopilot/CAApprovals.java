package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.3.0: every decision that waits for you, in one list (plans/2026-11-13-modes-rework.md section 3). Kinds: colony
 * builds and make-room swaps (CAData.Colony.pending*), the RAT settlement, the colony expedition, the money ladder's
 * Shipyard, Stockpile, Military goods and Military proposals, and the live asks the daily pass offers (hypershunt,
 * megastructures, TASC projects, station augments, autonomy). The summary pop-up (CAApprovalDialog), the Intel page and
 * the colony panel read it. Every approval checks the credit reserve first, then the kind's own handler, which keeps its
 * own checks (stability floor, slots, budget rules). Nothing is free: each pays its cost and fee.
 */
public final class CAApprovals {
    private CAApprovals() { }

    public static final String EMPIRE = "Empire";
    /** Days a rejected live ask is held. */
    static final float HOLD_DAYS = 30f;

    public interface Act { String run(); }

    public static final class Item {
        public String key, kind, marketId, place, title, why;
        public float cost, fee, monthly;
        public long since;
        /** Live asks: the game day the module last offered it. */
        int seen;
        Act approve, reject;
        public float total() { return cost + fee; }
    }

    // ---------------- live asks (session; the daily pass offers them again) ----------------

    static final Map<String, Item> LIVE = new LinkedHashMap<String, Item>();

    /** Days a live ask stays without being offered again (the augment and megastructure steps look weekly). */
    static final int LIVE_DAYS = 8;

    /** The daily pass starts: live asks not offered again for LIVE_DAYS go. */
    static void beginPass() {
        int today = CATime.today();
        for (Iterator<Map.Entry<String, Item>> i = LIVE.entrySet().iterator(); i.hasNext(); ) {
            int age = today - i.next().getValue().seen;
            if (age > LIVE_DAYS || age < 0) i.remove();
        }
    }

    /** A module offers a live ask (an Approve colony's recommendation). Held for 30 days after you reject it. */
    static void offer(String key, String kind, MarketAPI m, String title, float cost, float fee, String why, Act approve) {
        if (key == null || approve == null || held(key)) return;
        Item it = new Item();
        it.key = key; it.kind = kind; it.marketId = m == null ? null : m.getId(); it.place = m == null ? EMPIRE : m.getName();
        it.title = title; it.cost = cost; it.fee = fee; it.why = why;
        Item old = LIVE.get(key);
        it.since = old != null ? old.since : CAMark.now();
        it.seen = CATime.today();
        it.approve = approve;
        final String k = key;
        it.reject = new Act() { public String run() { CAMark.set("askno:" + k, CAMark.now()); LIVE.remove(k); return null; } };
        LIVE.put(key, it);
    }

    /** Whether you rejected this live ask within 30 days (an older mark is cleared). */
    static boolean held(String key) {
        Long at = CAMark.at("askno:" + key);
        if (at == null) return false;
        if (CAMark.days(at, CAMark.now()) < HOLD_DAYS) return true;
        CAMark.clear("askno:" + key);
        return false;
    }

    public static void reset() { LIVE.clear(); }

    // ---------------- the list ----------------

    /** Every decision waiting now, grouped by colony name (Empire last). */
    public static List<Item> collect() {
        List<Item> out = new ArrayList<Item>();
        CAData d;
        try { d = CAData.get(); } catch (Throwable t) { return out; }
        CASettings s = d.settings;
        try {
            for (MarketAPI m : Misc.getPlayerMarkets(false)) {
                CAData.Colony c = d.colonies.get(m.getId());
                if (c != null && c.pendingId != null) out.add(build(m, c, s));
            }
        } catch (Throwable t) { CAStep.warn("approvals", "colony proposals could not be read", t); }
        try { if (d.settlePendingKind != null) out.add(settlement(d, s)); } catch (Throwable t) { CAStep.warn("approvals", "settlement proposal could not be read", t); }
        try { if (d.expandPendingId != null) out.add(expedition(d)); } catch (Throwable t) { CAStep.warn("approvals", "expedition proposal could not be read", t); }
        try { ladder(out); } catch (Throwable t) { CAStep.warn("approvals", "money ladder proposals could not be read", t); }
        out.addAll(LIVE.values());
        sort(out);
        return out;
    }

    /** Pure: colony groups by name, Empire last; inside a group, oldest first. */
    static void sort(List<Item> items) {
        Collections.sort(items, new Comparator<Item>() {
            public int compare(Item a, Item b) {
                boolean ea = EMPIRE.equals(a.place), eb = EMPIRE.equals(b.place);
                if (ea != eb) return ea ? 1 : -1;
                int c = String.valueOf(a.place).compareTo(String.valueOf(b.place));
                if (c != 0) return c;
                return Long.compare(a.since, b.since);
            }
        });
    }

    /** Pure: the items by place, in list order. */
    static LinkedHashMap<String, List<Item>> group(List<Item> items) {
        LinkedHashMap<String, List<Item>> out = new LinkedHashMap<String, List<Item>>();
        for (Item it : items) {
            List<Item> l = out.get(it.place);
            if (l == null) { l = new ArrayList<Item>(); out.put(it.place, l); }
            l.add(it);
        }
        return out;
    }

    static String buildKey(String marketId, String pickId, String replace) {
        return pickId == null ? null : "build:" + marketId + ":" + pickId + (replace == null ? "" : ":" + replace);
    }

    static Item build(final MarketAPI m, CAData.Colony c, CASettings s) {
        Item it = new Item();
        it.kind = "build"; it.marketId = m.getId(); it.place = m.getName();
        it.key = buildKey(m.getId(), c.pendingId, c.pendingReplace);
        String gone = null;
        if (c.pendingReplace != null) {
            Industry ind = m.getIndustry(c.pendingReplace);
            gone = ind == null ? c.pendingReplace : ind.getCurrentName();
        }
        it.title = c.pendingName + (gone != null ? ", takes down " + gone : "");
        it.cost = c.pendingCost; it.fee = CABudget.constructionFee(s, c.pendingCost);
        it.monthly = c.pendingMonthly; it.why = c.pendingWhy; it.since = c.pendingSince;
        final String key = it.key;
        it.approve = new Act() { public String run() {
            CAData.Colony now = CAData.get().colony(m.getId());
            if (!key.equals(buildKey(m.getId(), now.pendingId, now.pendingReplace))) return changed(now.pendingName);
            return CAAuto.approve(m);
        } };
        it.reject = new Act() { public String run() {
            CAData.Colony now = CAData.get().colony(m.getId());
            if (!key.equals(buildKey(m.getId(), now.pendingId, now.pendingReplace))) return changed(now.pendingName);
            CAAuto.reject(m);
            return null;
        } };
        return it;
    }

    static Item settlement(CAData d, CASettings s) {
        Item it = new Item();
        it.kind = "settle"; it.place = EMPIRE;
        it.key = "settle:" + d.settlePendingKind + ":" + d.settlePendingId;
        it.title = "Settlement: " + d.settlePendingName;
        it.cost = d.settlePendingCost; it.fee = CABudget.constructionFee(s, d.settlePendingCost);
        it.since = CAMark.now();
        final String key = it.key;
        it.approve = new Act() { public String run() {
            if (!Global.getSettings().getModManager().isModEnabled(CASettlement.MOD)) return "Random Assortment of Things is off";
            CAData dd = CAData.get();
            if (!key.equals("settle:" + dd.settlePendingKind + ":" + dd.settlePendingId)) return changed(dd.settlePendingName);
            return CASettlement.approve(dd, dd.settings);
        } };
        it.reject = new Act() { public String run() {
            if (!Global.getSettings().getModManager().isModEnabled(CASettlement.MOD)) return "Random Assortment of Things is off";
            CAData dd = CAData.get();
            CASettlement.handle("reject", "", dd, dd.settings);
            return null;
        } };
        return it;
    }

    static Item expedition(CAData d) {
        Item it = new Item();
        it.kind = "expand"; it.place = EMPIRE;
        it.key = "expand:" + d.expandPendingId;
        it.title = "Colony expedition to " + d.expandPendingName;
        it.cost = d.expandPendingCost; it.fee = 0f;   // the expedition's total has its fee in it (CAExpansion.propose)
        it.since = CAMark.now();
        final String id = d.expandPendingId;
        it.approve = new Act() { public String run() {
            CAData dd = CAData.get();
            if (!id.equals(dd.expandPendingId)) return changed(dd.expandPendingName);
            return CAExpansion.handle("approve", id, dd, dd.settings);
        } };
        it.reject = new Act() { public String run() {
            CAData dd = CAData.get();
            if (!id.equals(dd.expandPendingId)) return changed(dd.expandPendingName);
            return CAExpansion.handle("reject", id, dd, dd.settings);
        } };
        return it;
    }

    /** The money ladder's proposals (session: their buyers propose again after a load). */
    static void ladder(List<Item> out) {
        final CAShipyard.Plan p = CAShipyard.pending;
        if (p != null && !p.empty()) {
            Item it = new Item();
            it.kind = "shipyard"; it.place = EMPIRE; it.key = "shipyard:" + p.sig();
            it.title = "Shipyard order: " + CAShipyard.text(p.qty, p.names);
            it.cost = p.value; it.fee = p.fee; it.since = CAMark.now();
            it.approve = new Act() { public String run() {
                if (CAShipyard.pending != p) return "Changed since the list was made";
                return ladderResult(CAShipyard.handle(CAShipyard.APPROVE), CAShipyard.pending == null || CAShipyard.pending.empty());
            } };
            it.reject = new Act() { public String run() { note(CAShipyard.handle(CAShipyard.REJECT)); return null; } };
            out.add(it);
        }
        for (int k = 0; k < CAStockpile.pending.length; k++) {
            final CAStockpile.Need n = CAStockpile.pending[k];
            if (n == null || n.to == null) continue;
            final int kk = k;
            Item it = new Item();
            it.kind = "stock"; it.marketId = n.to.getId(); it.place = n.to.getName(); it.key = "stock:" + k + ":" + n.to.getId() + "|" + n.com;
            it.title = CAStockpile.KIND_NAMES[k] + ": " + Misc.getWithDGS(n.qty) + " " + n.name + (n.src == null ? "" : " from " + n.src.getName());
            it.cost = n.price; it.fee = n.fee; it.since = CAMark.now();
            it.approve = new Act() { public String run() {
                if (CAStockpile.pending[kk] != n) return "Changed since the list was made";
                return ladderResult(CAStockpile.handle(CAStockpile.APPROVE + kk), CAStockpile.pending[kk] == null);
            } };
            it.reject = new Act() { public String run() { note(CAStockpile.handle(CAStockpile.REJECT + kk)); return null; } };
            out.add(it);
        }
        final CAMilitary.Up u = CAMilitary.pending;
        if (u != null && u.m != null) {
            Item it = new Item();
            it.kind = "military"; it.marketId = u.m.getId(); it.place = u.m.getName(); it.key = "military:" + u.m.getId() + "|" + u.to;
            it.title = "Military: upgrade to " + u.name;
            it.cost = u.cost; it.fee = u.fee; it.monthly = -u.upkeep; it.since = CAMark.now();
            it.approve = new Act() { public String run() {
                if (CAMilitary.pending != u) return "Changed since the list was made";
                return ladderResult(CAMilitary.handle(CAMilitary.APPROVE), CAMilitary.pending == null);
            } };
            it.reject = new Act() { public String run() { note(CAMilitary.handle(CAMilitary.REJECT)); return null; } };
            out.add(it);
        }
    }

    /** Pure: a ladder handler's answer as an approval result: "" with the proposal gone is done (null); else its message. */
    static String ladderResult(String r, boolean gone) {
        if (r != null && r.isEmpty() && gone) return null;
        if (r == null || r.isEmpty()) return "Not started";
        return r;
    }

    static void note(String r) { if (r != null && !r.isEmpty()) CALog.did(r, false); }

    static String changed(String now) { return now == null ? "Changed: the proposal is gone" : "Changed: now " + now; }

    // ---------------- approve and reject ----------------

    /** Pure: credits short of what is needed above the reserve (0 when it fits). */
    static float shortfall(float credits, float reserve, float need) {
        if (!(need > 0f)) return 0f;
        float above = credits - Math.max(0f, reserve);
        return above + 0.5f >= need ? 0f : need - Math.max(0f, above);
    }

    /**
     * Pure: which of a batch fit, approved in order, each paid from credits above the reserve (what Approve all does with the
     * live balance: an item that doesn't fit stays open, the next may still fit).
     */
    static boolean[] fits(float credits, float reserve, float[] totals) {
        boolean[] out = new boolean[totals.length];
        float c = credits;
        for (int i = 0; i < totals.length; i++) {
            out[i] = shortfall(c, reserve, totals[i]) <= 0f;
            if (out[i]) c -= Math.max(0f, totals[i]);
        }
        return out;
    }

    /** Why credits above the reserve don't cover this, or null. */
    public static String reserveGate(float need) {
        float credits = CABuild.credits(), reserve = CAData.settings().reserve;
        if (shortfall(credits, reserve, need) <= 0f) return null;
        return "Needs " + Misc.getDGSCredits(need) + " above the credit reserve (" + Misc.getDGSCredits(Math.max(0f, credits - reserve)) + " above it now)";
    }

    /** Approves one item: the reserve, then the kind's own handler. Returns null when it started, else the reason. */
    public static String approve(Item it) {
        if (it == null || it.approve == null) return "Not available";
        try {
            String gate = reserveGate(it.total());
            if (gate != null) return gate;
            String r = it.approve.run();
            if (r == null) LIVE.remove(it.key);
            return r;
        } catch (Throwable t) {
            CAStep.warn("approvals", "approving " + it.title + " failed", t);
            return "Failed. See starsector.log";
        }
    }

    /** Rejects one item (each kind holds it: builds and live asks 30 days, expeditions 180). Returns null or the reason. */
    public static String reject(Item it) {
        if (it == null || it.reject == null) return "Not available";
        try { return it.reject.run(); }
        catch (Throwable t) {
            CAStep.warn("approvals", "rejecting " + it.title + " failed", t);
            return "Failed. See starsector.log";
        }
    }

    /** Approves each item in list order; returns the reason per key for the ones that did not start (one did line). */
    public static Map<String, String> approveAll(List<Item> items) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        int ok = 0;
        for (Item it : items) {
            String r = approve(it);
            if (r == null) ok++; else out.put(it.key, r);
        }
        if (!items.isEmpty()) CALog.did("Approvals: " + ok + " approved" + (out.isEmpty() ? "." : ", " + out.size() + " still open."), true);
        return out;
    }

    /** Rejects each item; returns the reason per key for the ones that failed (one did line). */
    public static Map<String, String> rejectAll(List<Item> items) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Item it : items) { String r = reject(it); if (r != null) out.put(it.key, r); }
        if (!items.isEmpty()) CALog.did("Approvals: " + (items.size() - out.size()) + " rejected.", true);
        return out;
    }

    // ---------------- quiet lines and your own builds ----------------

    /** Proposal and recommendation lines go to the Thinking list while the summary is on, or the empire mode is Advisor. */
    static boolean quiet() {
        try {
            CASettings s = CAData.settings();
            return s.approvalDays > 0 || s.empire() == CAData.Mode.ADVISOR;
        } catch (Throwable t) { return false; }
    }

    /** A proposal or recommendation line: the Thinking list when quiet(), else on screen as advice. */
    static void say(String text, boolean important) {
        if (quiet()) CALog.think(text); else CALog.advise(text, important);
    }

    /**
     * The colony panel's Build button: the game's price plus the construction fee, from credits above the reserve, through
     * the same start as Autopilot's own builds (stability floor, slot and construction checks). Returns null or the reason.
     */
    public static String startByPlayer(MarketAPI m, CAEngine.Pick p) {
        if (m == null || p == null) return "Not available";
        CASettings s = CAData.settings();
        float fee = CABudget.constructionFee(s, p.cost);
        String gate = reserveGate(p.cost + fee);
        if (gate != null) return gate;
        CAData.Colony c = CAData.get().colony(m.getId());
        String keepId = c.pendingId;
        String err = CAAuto.execute(m, c, p, "Started by you", true);
        if (err == null && keepId != null && !keepId.equals(p.id)) CALog.quiet(m.getName() + ": your build replaced the waiting proposal");
        return err;
    }
}
