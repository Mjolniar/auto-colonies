package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Action log: kept in the save (last 100 entries), shown in the empire panel, echoed to the game log. */
public class CALog {
    /** Escapes % so free text is safe in the game's formatted text calls. */
    public static String esc(String s) { return s == null ? "" : s.replace("%", "%%"); }

    /** Ends a row of side-by-side buttons so the next element starts back at the left edge, under the row. */
    public static void endRow(com.fs.starfarer.api.ui.TooltipMakerAPI t, com.fs.starfarer.api.ui.UIComponentAPI first) {
        t.addSpacer(0f).getPosition().belowLeft(first, 0f);
    }

    // ---------------- once: messages logged once, kept in the save with when (0.3.0.0) ----------------
    // 0.3.0.0 (research 07 M1): the old `noted` set was wiped all at once at 2,000 keys, which logged every once-message again
    // in one burst and gave a running inspection Autopilot's orders back. Keys now live in CAData.notedAt with the time they
    // were noted, and each expires by its kind (holdDays): a guard holds until forgotten, an event guard for a year, and
    // every other wait or recommendation can show again after WAIT_DAYS if it still holds.

    /** Days before a wait or recommendation can be logged again while it still holds. */
    static final float WAIT_DAYS = CATuning.NOTED_WAIT_DAYS;   // 0.3.0.0 (A3): in CATuning
    /** Days an event guard holds (an inspection looked at): far past the event's own end. */
    static final float YEAR_DAYS = CATuning.NOTED_EVENT_DAYS;   // 0.3.0.0 (A3): in CATuning
    /** Past this many keys the oldest expiring ones go first (guards never do); the old set was wiped whole at 2,000. */
    static final int CAP = CATuning.NOTED_CAP;   // 0.3.0.0 (A3): in CATuning
    /** A key starting with this never expires; for a once() that guards an action rather than a log line (B-E's new keys). */
    public static final String KEEP = "keep:";
    /**
     * Keys that are state, paired with forget() when what they report ends: a siege, a crisis, the capital, Low profile,
     * spending freely, 0 stability, over the industry limit, no research facility, an idle agent or administrator, a dead
     * plan step. They hold until forgotten: expiring one would log "under siege" again mid-siege, or lose the "over" line.
     */
    static final String[] GUARDS = {KEEP, "crisis:", "capital:", "lowprofile:", "surplus:", "stab:none:", "siege:", "overlimit:",
            "research:nofacility", "spy:yours", "spy:max", "floatfuel:", "floatalt:", "autonomy:rec:", "admin:idle:", "logi:nohub",
            "projorders:broken", "plan:dead:"};
    /** Event guards: held a year, then tidied (the event they name has long ended; their keys carry a counter or a cycle). */
    static final String[] EVENTS = {"inspect:", "omega:inspect:"};

    /** How long a once() key holds, in days; 0 means until forgotten. Pure. */
    static float holdDays(String key) {
        if (key == null) return 0f;
        for (String p : GUARDS) if (key.startsWith(p)) return 0f;
        for (String p : EVENTS) if (key.startsWith(p)) return YEAR_DAYS;
        return WAIT_DAYS;
    }

    /** Whether a key noted at `at` has expired by `now` (game timestamps, ms). Time running backwards never expires one. Pure. */
    static boolean expired(String key, long at, long now) {
        float hold = holdDays(key);
        return hold > 0f && now > at && (now - at) / 86400000.0 > hold;
    }

    /**
     * Drops expired keys, then, past `cap`, the oldest expiring ones until `cap` is left; guards are never dropped. Returns
     * how many were removed. Pure (works on the map it's given).
     */
    static int prune(Map<String, Long> notedAt, long now, int cap) {
        if (notedAt == null) return 0;
        int n = 0;
        for (Iterator<Map.Entry<String, Long>> it = notedAt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (e.getKey() == null || e.getValue() == null || expired(e.getKey(), e.getValue(), now)) { it.remove(); n++; }
        }
        if (notedAt.size() <= cap) return n;
        List<Map.Entry<String, Long>> old = new java.util.ArrayList<Map.Entry<String, Long>>();
        for (Map.Entry<String, Long> e : notedAt.entrySet()) if (holdDays(e.getKey()) > 0f) old.add(e);
        java.util.Collections.sort(old, new java.util.Comparator<Map.Entry<String, Long>>() {
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) { return Long.compare(a.getValue(), b.getValue()); }
        });
        java.util.List<String> drop = new java.util.ArrayList<String>();
        for (int i = 0; i < old.size() && notedAt.size() - drop.size() > cap; i++) drop.add(old.get(i).getKey());
        for (String k : drop) notedAt.remove(k);
        return n + drop.size();
    }

    /** The game clock's timestamp, or 0 with no sector. */
    static long clock() {
        try { return Global.getSector().getClock().getTimestamp(); } catch (Throwable t) { return 0L; }
    }

    /**
     * The save's once-keys. A save from before 0.3.0 has its old `noted` set moved in here the first time (each key as noted
     * now, so nothing logged once logs again at once), and the set is dropped from the save.
     */
    static Map<String, Long> notedMap() {
        CAData d = CAData.get();
        Map<String, Long> m = d.notedAt();
        if (d.noted != null && !d.noted.isEmpty()) {
            long now = clock();
            for (String k : d.noted) if (k != null && !m.containsKey(k)) m.put(k, now);
            d.noted = null;
        }
        return m;
    }

    /**
     * True when a what-if valuation is running (CAResearch values techs by marking them researched and evaluating every
     * colony): nothing then is logged or written to the save (0.3.0.0, research 07 M2).
     */
    public static boolean whatIf() {
        try { return CAEngine.noProbe; } catch (Throwable t) { return false; }
    }

    /** True the first time a key is seen in this save (or once it has expired, see holdDays); for messages logged once, not after every load. */
    public static boolean once(String key) {
        if (whatIf()) return false;
        Map<String, Long> m = notedMap();
        long now = clock();
        Long at = m.get(key);
        if (at != null && !expired(key, at, now)) return false;
        m.put(key, now);
        return true;
    }

    /** Whether a once() key is held now (0.3.0.0: replaces reading CAData.noted() directly). */
    public static boolean noted(String key) {
        Map<String, Long> m = notedMap();
        Long at = m.get(key);
        return at != null && !expired(key, at, clock());
    }

    /** Lets a once() message be logged again, after the thing it reported has happened. */
    public static void forget(String key) {
        if (whatIf()) return;
        notedMap().remove(key);
    }

    /** Forgets every once() key with this prefix except `keep` (may be null); 0.3.0.0, for CACrisis's crisis keys. */
    public static void forgetAll(String prefix, String keep) {
        if (whatIf() || prefix == null) return;
        for (Iterator<String> it = notedMap().keySet().iterator(); it.hasNext(); ) {
            String k = it.next();
            if (k != null && k.startsWith(prefix) && !k.equals(keep)) it.remove();
        }
    }

    /** Weekly (CATidy): expired once-keys out of the save. Returns how many went. */
    public static int tidy() {
        if (whatIf()) return 0;
        return prune(notedMap(), clock(), CAP);
    }

    // ---------------- the log ----------------

    public static void add(String text) { add(text, false); }

    /** Days a flipping status (siege, threat, station augments, policy toggles) waits before its next line in the panel log. */
    static final float STATUS_DAYS = CATuning.STATUS_DAYS;   // 0.3.0.0 (A3): in CATuning
    static final java.util.Map<String, Float> statusAt = new java.util.HashMap<String, Float>();

    /** A status that can flip back and forth: in the panel log at most once per key every STATUS_DAYS, always in starsector.log. */
    public static void status(String key, String text) {
        if (whatIf()) return;
        float now = CADay.now();
        Float at = statusAt.get(key);
        if (at != null && now - at < STATUS_DAYS && now >= at) { quiet(text); return; }
        statusAt.put(key, now);
        add(text);
    }

    /**
     * The game date for starsector.log lines, "c206.03.14 " (0.3.0.0, research 01 section 5: without it, how long anything
     * waited could only be counted in Month lines). Empty with no sector.
     */
    static String stamp() {
        try {
            com.fs.starfarer.api.campaign.CampaignClockAPI c = Global.getSector().getClock();
            if (c.getCycle() <= 0) return "";   // no real clock (a test harness, or before the sector starts)
            return "c" + c.getCycle() + "." + two(c.getMonth()) + "." + two(c.getDay()) + " ";
        } catch (Throwable t) { return ""; }
    }

    static String two(int n) { return n < 10 ? "0" + n : String.valueOf(n); }

    /** starsector.log only: routine detail that doesn't belong in the panel log. */
    public static void quiet(String text) {
        if (whatIf()) return;
        CADiagnostics.LOG.info(CADiagnostics.TAG + stamp() + text);
    }

    /** Days before the same ping shows again. */
    static final float PING_DAYS = CATuning.PING_DAYS;
    static final java.util.Map<String, Float> pingAt = new java.util.HashMap<String, Float>();

    /**
     * A ping (0.2.41.0): a short machine line on screen about what the autopilot is weighing or doing in the background, at
     * most once per key a week. Only with the Machine voice and every entry on screen; never in the panel log.
     */
    public static void ping(String key, String text) {
        if (whatIf()) return;
        try {
            CASettings s = CAData.settings();
            if (s.voice != 0 || s.screenLog != 0) return;
            float now = CADay.now();
            Float at = pingAt.get(key);
            if (at != null && now - at < PING_DAYS && now >= at) return;
            pingAt.put(key, now);
            Global.getSector().getCampaignUI().addMessage(CATheme.machine(text), Misc.getTextColor());
        } catch (Throwable ignored) { }
    }

    /**
     * A log entry: the panel log, starsector.log, and on screen as a short line (Settings, Interface: every entry, the
     * default; important ones only; or none). important marks what shows in "important only": emergencies, defense
     * fleets, wonders and gates, things that are yours to do.
     */
    public static void add(String text, boolean important) {
        if (whatIf()) return;   // 0.3.0.0: a what-if valuation never reaches the log (07 M2)
        if (!important && CALogFilter.notActing(text)) { quiet(text); return; }   // 0.3.0.7: not-acting lines go to starsector.log only
        CASettings s = CAData.settings();
        List<String> log = s.log;
        String date = Global.getSector().getClock().getDateString();
        log.add(0, date + ": " + text);
        while (log.size() > CATuning.LOG_LINES) log.remove(log.size() - 1);
        CADiagnostics.LOG.info(CADiagnostics.TAG + stamp() + text);
        if (s.screenLog == 0 || (s.screenLog == 1 && important)) {
            try {
                if (s.voice == 0) Global.getSector().getCampaignUI().addMessage(CATheme.machine(text), important ? Misc.getHighlightColor() : Misc.getTextColor());
                else Global.getSector().getCampaignUI().addMessage("Autopilot: " + text, important ? Misc.getHighlightColor() : Misc.getTextColor());
            }
            catch (Throwable ignored) { }
        }
    }

    // ---------------- the month's money per colony (0.3.0.0, research 01 section 5 item 3) ----------------
    // What Auto Colonies spent for each colony this month, kept in the save (CAData.monthLedger: "marketId|spent", "|fees",
    // "|upkeep"), so a reload mid-month doesn't lose it. CABudget.charge books every charge here, under the colony when the
    // caller names it and under EMPIRE otherwise (logistics, agents, items moved between colonies). The monthly colony lines
    // read it and clear it; before, the month's spending was reset before any line could show it.

    static final String SPENT = "|spent", FEES = "|fees", UPKEEP = "|upkeep";
    /** The ledger's id for charges not tied to one colony. */
    static final String EMPIRE = "";

    /** Books a charge for one colony this month (marketId null: not one colony): credits paid with the fee, the fee part, new monthly upkeep. */
    public static void spent(String marketId, float credits, float fee, float upkeep) {
        if (whatIf()) return;
        try { book(CAData.get().monthLedger(), marketId, credits, fee, upkeep); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "month ledger failed", t); }
    }

    /** Adds a charge to a ledger map. Pure (works on the map it's given). */
    static void book(Map<String, Float> l, String marketId, float credits, float fee, float upkeep) {
        if (l == null) return;
        String id = marketId == null ? EMPIRE : marketId;
        addTo(l, id + SPENT, credits);
        addTo(l, id + FEES, fee);
        addTo(l, id + UPKEEP, upkeep);
    }

    static void addTo(Map<String, Float> l, String key, float v) {
        if (v == 0f || Float.isNaN(v) || Float.isInfinite(v)) return;
        Float o = l.get(key);
        l.put(key, (o == null ? 0f : o) + v);
    }

    /** One colony's (or EMPIRE's) figure from a ledger map, 0 when none. Pure. */
    static float got(Map<String, Float> l, String id, String part) {
        Float v = l == null ? null : l.get(id + part);
        return v == null ? 0f : v;
    }

    /** Monthly colony lines without the month's totals (0.2.45-era call); the empire line then sums the ledger. */
    public static void colonyMonth() { colonyMonth(-1f, -1f, -1f); }

    /**
     * Monthly, after the Month line (0.3.0.0, research 01 section 5): one starsector.log line per colony with its own money and
     * what Auto Colonies spent there, so a colony that loses money every month, or takes most of the spending, shows up
     * without the weekly diagnostics; then one line for the whole month's spending, with what wasn't tied to one colony.
     * `spent`, `fees` and `upkeepAdded` are last month's budget totals read before the reset (CABudget.rollMonth); below 0,
     * the ledger's own sums stand in. Clears the ledger for the new month. Log only, never on screen.
     */
    public static void colonyMonth(float spent, float fees, float upkeepAdded) {
        if (whatIf()) return;
        Map<String, Float> l = null;
        try {
            CAData d = CAData.get();
            l = d.monthLedger();
            float atColonies = 0f;
            for (com.fs.starfarer.api.campaign.econ.MarketAPI m : Misc.getPlayerMarkets(false)) {
                try {
                    CAData.Colony c = d.colonies == null ? null : d.colonies.get(m.getId());
                    float gross = m.getGrossIncome(), net = m.getNetIncome();
                    float cm = got(l, m.getId(), SPENT);
                    atColonies += cm;
                    quiet(monthLine(m.getName(), m.getSize(), c == null || c.mode == null ? "none" : c.mode.label, gross, gross - net, net,
                            Math.round(m.getStabilityValue()), cm, got(l, m.getId(), FEES), got(l, m.getId(), UPKEEP)));
                } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "month line failed at " + m.getName(), t); }
            }
            float[] sum = sums(l);
            quiet(spendLine(spent >= 0f ? spent : sum[0], fees >= 0f ? fees : sum[1], upkeepAdded >= 0f ? upkeepAdded : sum[2], atColonies));
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony month lines failed", t); }
        finally { try { if (l != null) l.clear(); } catch (Throwable ignored) { } }
    }

    /** The ledger's totals over every id: {spent, fees, upkeep}. Pure. */
    static float[] sums(Map<String, Float> l) {
        float[] s = new float[3];
        if (l == null) return s;
        for (Map.Entry<String, Float> e : l.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            if (e.getKey().endsWith(SPENT)) s[0] += e.getValue();
            else if (e.getKey().endsWith(FEES)) s[1] += e.getValue();
            else if (e.getKey().endsWith(UPKEEP)) s[2] += e.getValue();
        }
        return s;
    }

    /**
     * "Month at Wind (size 6, Auto): net -122k (income 300k, upkeep 422k), stability 7; Auto Colonies spent 250k (fees 12k), upkeep
     * added 8k". Pure.
     */
    static String monthLine(String name, int size, String mode, float income, float upkeep, float net, int stability,
                            float cmSpent, float cmFees, float cmUpkeep) {
        return "Month at " + name + " (size " + size + ", " + mode + "): net " + (net < 0f ? "-" : "+") + k(Math.abs(net))
                + " (income " + k(income) + ", upkeep " + k(upkeep) + "), stability " + stability + "; " + cmPart(cmSpent, cmFees, cmUpkeep);
    }

    /** "Auto Colonies spent 250k (fees 12k), upkeep added 8k", or "Auto Colonies spent nothing". Pure. */
    static String cmPart(float spent, float fees, float upkeep) {
        if (spent <= 0f && upkeep <= 0f) return "Auto Colonies spent nothing";
        return "Auto Colonies spent " + k(spent) + (fees > 0f ? " (fees " + k(fees) + ")" : "") + (upkeep > 0f ? ", upkeep added " + k(upkeep) : "");
    }

    /** "Month's Auto Colonies spending: 1.2M (fees 30k), upkeep added 45k; 200k not tied to one colony". Pure. */
    static String spendLine(float spent, float fees, float upkeep, float atColonies) {
        float other = Math.max(0f, spent - atColonies);
        return "Month's Auto Colonies spending: " + (spent <= 0f && upkeep <= 0f ? "nothing" : k(spent) + (fees > 0f ? " (fees " + k(fees) + ")" : "")
                + (upkeep > 0f ? ", upkeep added " + k(upkeep) : "")) + (other >= 500f ? "; " + k(other) + " not tied to one colony" : "");
    }

    /** Credits as "12k" / "1.2M" (no % or game formatting, for log lines). Pure. */
    static String k(float v) {
        if (v >= 999500f) return String.format(java.util.Locale.ROOT, "%.1fM", v / 1e6f);
        return Math.round(v / 1000f) + "k";
    }

    /** 0.3.0.0 (07 H6): the session's debounce timers, cleared on load (CAReset.onLoad) so another save's dates don't hold lines back. */
    public static void reset() { statusAt.clear(); pingAt.clear(); }

    public static final String[] SCREEN_NAMES = {"Every entry", "Important only", "Off"};
}
