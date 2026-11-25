package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.19.0: buildings you build, upgrade or keep by hand stay as you set them. Each day the industries of every colony of
 * yours are compared with the day before (CAData.industriesSeen):
 * - A new building Autopilot did not start or pay for (ours) is yours: it is locked (Colony.locks), so Autopilot doesn't
 *   upgrade, replace or take it down, or change its item or AI core.
 * - An upgrade Autopilot started that you cancelled locks the building it stayed (CAData.upgradeTargets still names it).
 * - A building of Autopilot's that you took down is not built there again for a year (CATombstone, YOU).
 * A lock made here (CAMark AUTO) comes off when its building is gone; the colony panel unlocks it as before. A colony seen
 * last more than STALE_DAYS ago (lost and retaken, or a new colony) is read again without locking anything.
 */
public class CAManual {
    static final String AUTO = "autolock:", OURS = "acstart:", GONE = "acremoved:";
    static final float STALE_DAYS = 3f;

    /** CABuild.start, when a build or upgrade took effect: this building is Autopilot's. */
    public static void started(MarketAPI m, String industryId) {
        if (m != null && industryId != null) CAMark.set(OURS + m.getId() + "|" + industryId, CAMark.now());
    }

    /** Autopilot took this building down without a removal mark (it replaced it). */
    public static void removed(MarketAPI m, String industryId) {
        if (m != null && industryId != null) CAMark.set(GONE + m.getId() + "|" + industryId, CAMark.now());
    }

    /** Autopilot started or paid for this building here (its own record, or its sunk-cost record from before 0.3.19.0). */
    static boolean ours(MarketAPI m, String id, Map<String, String> targets) {
        if (CAMark.at(OURS + m.getId() + "|" + id) != null) return true;
        if (CATombstone.sunk(m.getId(), id) > 0f) return true;
        String prefix = m.getId() + "|";
        for (Map.Entry<String, String> e : targets.entrySet()) if (e.getKey().startsWith(prefix) && id.equals(e.getValue())) return true;
        return false;
    }

    /** The colony's buildings as you see them: not hidden, not Population. */
    static Set<String> visible(MarketAPI m) {
        Set<String> out = new TreeSet<String>();
        for (Industry ind : m.getIndustries()) {
            if (ind.isHidden() || ind.getSpec().hasTag("population")) continue;
            out.add(ind.getId());
        }
        return out;
    }

    /** Pure: "day|id,id,...". */
    static String code(float day, Set<String> ids) {
        StringBuilder sb = new StringBuilder().append(day).append('|');
        boolean first = true;
        for (String id : ids) { if (!first) sb.append(','); sb.append(id); first = false; }
        return sb.toString();
    }

    /** Pure: the day of a code, or null when it can't be read. */
    static Float day(String code) {
        int bar = code == null ? -1 : code.indexOf('|');
        if (bar <= 0) return null;
        try { return Float.parseFloat(code.substring(0, bar)); } catch (NumberFormatException e) { return null; }
    }

    /** Pure: the ids of a code. */
    static Set<String> ids(String code) {
        Set<String> out = new TreeSet<String>();
        int bar = code == null ? -1 : code.indexOf('|');
        if (bar < 0) return out;
        for (String s : code.substring(bar + 1).split(",")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    /** Pure: new ids (in now, not before). */
    static Set<String> added(Set<String> before, Set<String> now) {
        Set<String> out = new TreeSet<String>(now);
        out.removeAll(before);
        return out;
    }

    static String name(MarketAPI m, String id) {
        Industry ind = m.getIndustry(id);
        if (ind != null) return ind.getCurrentName();
        try { return com.fs.starfarer.api.Global.getSettings().getIndustrySpec(id).getName(); } catch (Throwable t) { return id; }
    }

    static void lock(CAData.Colony c, MarketAPI m, String id, String why) {
        if (c.locks.contains(id)) return;
        c.locks.add(id);
        CAMark.set(AUTO + m.getId() + "|" + id, CAMark.now());
        CALog.did(m.getName() + ": " + name(m, id) + " locked. " + why);
    }

    /** The daily look (CAAuto). Nothing changes under a what-if. */
    public static void run(CAData d) {
        if (CALog.whatIf()) return;
        float today = CADay.now();
        Map<String, String> targets = d.upgradeTargets();
        Map<String, String> seen = d.industriesSeen();
        Set<String> live = new HashSet<String>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            live.add(m.getId());
            try { look(d, m, today, targets, seen); }
            catch (Throwable t) { CAStep.warn("manual changes", "reading the buildings failed at " + m.getName(), t); }
        }
        seen.keySet().retainAll(live);
    }

    static void look(CAData d, MarketAPI m, float today, Map<String, String> targets, Map<String, String> seen) {
        CAData.Colony c = d.colony(m.getId());
        Set<String> now = visible(m);
        String was = seen.get(m.getId());
        seen.put(m.getId(), code(today, now));
        Float at = day(was);
        if (at == null || today - at > STALE_DAYS || today < at) return;   // first look, or not seen for days: read only
        Set<String> before = ids(was);
        // built or upgraded by you
        for (String id : added(before, now)) {
            if (ours(m, id, targets)) continue;
            Industry ind = m.getIndustry(id);
            boolean upgrade = false;
            for (String b : before) if (!now.contains(b) && CAPlan.leadsTo(b, id)) upgrade = true;
            lock(c, m, id, upgrade ? "You upgraded it." : ind != null && ind.isBuilding() ? "You are building it." : "You built it.");
        }
        // an upgrade of Autopilot's that you cancelled
        String prefix = m.getId() + "|";
        for (Iterator<Map.Entry<String, String>> it = targets.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> e = it.next();
            if (!e.getKey().startsWith(prefix)) continue;
            String from = e.getKey().substring(prefix.length());
            Industry ind = m.getIndustry(from);
            if (ind == null || ind.isUpgrading() || m.hasIndustry(e.getValue())) continue;
            it.remove();
            lock(c, m, from, "You cancelled its upgrade.");
        }
        // gone: a lock made here comes off; one of Autopilot's that you took down isn't built again for a year
        for (String id : before) {
            if (now.contains(id)) continue;
            String auto = AUTO + m.getId() + "|" + id;
            if (CAMark.at(auto) != null) { c.locks.remove(id); CAMark.clear(auto); }
            boolean upgraded = false;
            for (String n : now) if (CAPlan.leadsTo(id, n)) upgraded = true;
            String gone = GONE + m.getId() + "|" + id;
            if (CAMark.at(gone) != null) { CAMark.clear(gone); upgraded = true; }   // Autopilot replaced it
            if (upgraded || CATombstone.blocked(m.getId(), id) > 0f) continue;   // an upgrade, or Autopilot took it down
            if (CAMark.at(OURS + m.getId() + "|" + id) == null && CATombstone.sunk(m.getId(), id) <= 0f) continue;   // not Autopilot's
            CATombstone.add(m.getId(), id, CATombstone.YOU, CATombstone.sunk(m.getId(), id));
            CAMark.clear(OURS + m.getId() + "|" + id);
        }
    }
}
