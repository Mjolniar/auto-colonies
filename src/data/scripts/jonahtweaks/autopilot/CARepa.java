package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.11.5: Reverse Engineered Private Arsenal research fed from your Storage (setting Reverse engineering, on by default;
 * CASettings.disableRepa). Once a week, one copy of each weapon (and, at a Tier 2 or 3 hub, each fighter wing) in your
 * colonies' normal Storage that the hub hasn't reverse-engineered yet goes into the hub's Reverse Engineering Storage, where
 * the hub researches it. RePA uses up one copy per item and skips anything it already did, so only one copy goes, and
 * nothing it already did. From the hub's own colony it moves at once for free; from another colony it travels as a Storage
 * logistics load (CALogistics: the shipment fee and travel time, a load waits until it's worth its fee or its route's last
 * load was 30 days ago) and arrives in the Reverse Engineering Storage. Your fleet's cargo and Autopilot Storage are never
 * touched. Read from RePA's own ids and memory keys only (no RePA class), the same as CAOddisz.
 * - Research done: RePA's sets in the sector's memory ($RePA_ProducedWeapons, $RePA_ProducedFighters).
 * - Queued: what's in the Reverse Engineering Storage or on its way there, and what was seen queued in the last QUEUED_DAYS
 *   (CAMark "repa:<id>", refreshed each week), which covers an item the hub took out to research.
 */
public final class CARepa {
    private CARepa() { }

    static final String SUB = "RePA_ReversEngSub";
    static final String WEAPONS = "$RePA_ProducedWeapons", FIGHTERS = "$RePA_ProducedFighters";
    static final String MARK = "repa:", WHY = "reverse engineering";
    /** Days an item seen queued stays queued: RePA's research takes 30 days with no core by default. */
    static final float QUEUED_DAYS = 60f;
    static float lastRun = Float.NaN;

    public static boolean on(CASettings s) {
        try { return !s.disableRepa && Global.getSettings().getModManager().isModEnabled(CAOddisz.ARSENAL_MOD); } catch (Throwable t) { return false; }
    }

    /** The hub's tier at this colony (3, 2, 1), 0 with none working. */
    static int tier(MarketAPI m) {
        for (int i = CAOddisz.HUB.length - 1; i >= 0; i--) {
            Industry ind = m.getIndustry(CAOddisz.HUB[i]);
            if (ind != null && ind.isFunctional()) return i + 1;
        }
        return 0;
    }

    /** Your colony with the best working hub and its Reverse Engineering Storage, or null. */
    static MarketAPI hub() {
        MarketAPI best = null;
        int top = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            try {
                int t = tier(m);
                if (t > top && m.getSubmarket(SUB) != null) { best = m; top = t; }
            } catch (Throwable ignored) { }
        }
        return best;
    }

    /** RePA's set of what its hub has reverse-engineered (a copy; empty before the first). */
    @SuppressWarnings("unchecked")
    static Set<String> done(String key) {
        Set<String> out = new HashSet<String>();
        try {
            Object o = Global.getSector().getMemoryWithoutUpdate().get(key);
            if (o instanceof Collection) for (Object x : (Collection<Object>) o) if (x != null) out.add(x.toString());
        } catch (Throwable ignored) { }
        return out;
    }

    /** The weapon or fighter wing id of a stack (wings only when allowed), or null for anything else. */
    static String id(CargoStackAPI st, boolean wings) {
        try {
            if (st.isWeaponStack() && st.getWeaponSpecIfWeapon() != null) return st.getWeaponSpecIfWeapon().getWeaponId();
            if (wings && st.isFighterWingStack() && st.getFighterWingSpecIfWing() != null) return st.getFighterWingSpecIfWing().getId();
        } catch (Throwable ignored) { }
        return null;
    }

    /** Pure: an item goes to research when it has an id, the hub hasn't done it and it isn't queued already. */
    static boolean wanted(String id, Set<String> done, Set<String> queued) { return id != null && !done.contains(id) && !queued.contains(id); }

    /** Weekly, from CAAuto's day (after Storage logistics). */
    public static void daily(CAData d, CASettings s, float now) {
        if (!on(s) || !CATime.due(lastRun, now, 7f)) return;
        lastRun = now;
        MarketAPI hub = hub();
        if (hub == null) return;
        SubmarketAPI sub = hub.getSubmarket(SUB);
        if (sub == null || sub.getCargo() == null) return;
        CargoAPI re = sub.getCargo();
        boolean wings = tier(hub) >= 2;   // a Tier 1 hub takes weapons only (ReverseEngineeringSubmarketPlugin)
        Set<String> done = done(WEAPONS);
        done.addAll(done(FIGHTERS));
        long t = CAMark.now();
        Set<String> queued = new HashSet<String>();
        for (CargoStackAPI st : re.getStacksCopy()) { String id = id(st, true); if (id != null) queued.add(id); }
        for (CALogistics.Load l : d.loads()) {
            if (l == null || !SUB.equals(l.sub) || l.cargo == null) continue;
            for (CargoStackAPI st : l.cargo.getStacksCopy()) { String id = id(st, true); if (id != null) queued.add(id); }
        }
        for (String id : queued) CAMark.set(MARK + id, t);   // seen queued this week
        CAMark.forgetOld(MARK, QUEUED_DAYS);
        for (String k : CAMark.keys(MARK)) queued.add(k.substring(MARK.length()));   // includes ones the hub took out to research
        List<MarketAPI> from = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (CALogistics.access(m)) from.add(m);
        for (MarketAPI m : CAStores.byDistance(from, hub)) {
            try { feed(d, s, m, hub, re, wings, done, queued, t); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "reverse engineering feed failed at " + m.getName(), e); }
        }
    }

    static void feed(CAData d, CASettings s, MarketAPI m, MarketAPI hub, CargoAPI re, boolean wings, Set<String> done, Set<String> queued, long t) {
        CargoAPI st = Misc.getStorageCargo(m);
        if (st == null) return;
        boolean here = m == hub;
        CALogistics.Load l = here ? null : CALogistics.load(m, hub, WHY);
        if (l != null) l.sub = SUB;
        List<String> ids = new ArrayList<String>();
        int weapons = 0, fighters = 0;
        for (CargoStackAPI x : st.getStacksCopy()) {
            String id = id(x, wings);
            if (!wanted(id, done, queued) || x.getSize() < 1f) continue;
            if (here) { re.addItems(x.getType(), x.getData(), 1); st.removeItems(x.getType(), x.getData(), 1); }
            else CALogistics.move(st, l.cargo, x, 1);
            queued.add(id);
            ids.add(id);
            if (x.isWeaponStack()) weapons++; else fighters++;
        }
        if (ids.isEmpty()) return;
        String what = count(weapons, fighters);
        if (here) {
            for (String id : ids) CAMark.set(MARK + id, t);
            CALog.did(hub.getName() + ": " + what + " moved from Storage to Reverse Engineering Storage for research.");
            return;
        }
        CALogistics.send(d, s, l, st);
        if (!d.loads().contains(l)) { for (String id : ids) queued.remove(id); return; }   // waits for budget or a fuller load; it's back in Storage
        for (String id : ids) CAMark.set(MARK + id, t);
    }

    /** Pure: "3 weapons and 1 fighter wing". */
    static String count(int weapons, int fighters) {
        List<String> parts = new ArrayList<String>();
        if (weapons > 0) parts.add(weapons + (weapons == 1 ? " weapon" : " weapons"));
        if (fighters > 0) parts.add(fighters + (fighters == 1 ? " fighter wing" : " fighter wings"));
        return String.join(" and ", parts);
    }

    /**
     * A load for research that arrived at the hub: what the hub can still research goes into Reverse Engineering Storage,
     * the rest (done since it left, a fighter wing at a Tier 1 hub) into Storage. False when there's no hub there now
     * (CALogistics then delivers it all to Storage).
     */
    static boolean deliver(MarketAPI to, CALogistics.Load l) {
        SubmarketAPI sub = to == null ? null : to.getSubmarket(SUB);
        int tier = to == null ? 0 : tier(to);
        if (sub == null || sub.getCargo() == null || tier == 0) return false;
        CargoAPI re = sub.getCargo(), st = Misc.getStorageCargo(to);
        if (st == null) return false;
        Set<String> done = done(WEAPONS);
        done.addAll(done(FIGHTERS));
        for (CargoStackAPI x : l.cargo.getStacksCopy()) {
            String id = id(x, tier >= 2);
            CargoAPI into = id != null && !done.contains(id) ? re : st;
            into.addItems(x.getType(), x.getData(), x.getSize());
            l.cargo.removeItems(x.getType(), x.getData(), x.getSize());
        }
        st.addAll(l.cargo);   // anything left over (none expected)
        l.cargo.clear();
        return true;
    }
}
