package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.0.0 (WS-B4, research 01 section 3 item 4, 02 section 8 and "Construction 0.3.0", 03 F12): removal is the same math as
 * building, run backwards, and it remembers.
 *
 * - Marks: every removal (pruning, the industry limit, 0-stability shedding, the endgame's and the capital's swaps, and
 *   whatever else takes a building down and calls add) leaves a mark for a year: market, building, until, why and what it
 *   had cost. The planner won't build it there again while the mark holds (CAPlanner.tombstoneDays, which also counts a
 *   mark on what a pick upgrades into: Wind built a Junkyard Forge a month after its Scrapyard came down). 0-stability
 *   shedding writes a mark too but never reads one: it keeps its own rules.
 * - Sunk-cost memory: what Autopilot paid for each building (CAData.sunk, "marketId:industryId"; an upgrade carries what
 *   its base hadn't paid back yet, 0.3.0.0 fix), written by CABuild.start, and when and under which valuation it was built (CAData.endgameBuilt,
 *   "marketId:industryId@money|rich|endgame"; the endgame's own "marketId:industryId" from before 0.3.0 counts as endgame).
 *   It's in the removal log ("built 3 months ago for 375,000") and in the teardown loss.
 * - Teardown loss: the part of the price a building hasn't paid back yet over the horizon, paid x (1 - months since built
 *   / H). A replacement goes ahead when NPV(new) - keep x H - teardown loss > margin; a building is pruned when keep x H +
 *   teardown loss < 0. So nothing is built and taken down again within months because the numbers wobbled.
 * - Valuations: anything built while money counted one way (normally, spending freely, the endgame economy) isn't taken
 *   down by a remover that values it another way for a year (02 section 8: a building the endgame put up could be pruned the
 *   first day spending freely stopped, and built again at full price when it came back).
 *
 * Saved data is ids, numbers and timestamps only (Mark, the sunk map, endgameBuilt's stamps). Marks expire a year on and
 * are tidied weekly (weekly(), from CAPrune.run), as are sunk-cost entries of buildings that are gone.
 * 0.3.0.0 (fix): a build stamp counts for the teardown loss as long as the horizon runs (up to HORIZON_MAX, 120 months), so it
 * isn't dropped at 400 days like the year-long stamps (CATidy leaves "@" stamps alone); tidySunk drops it once the building
 * or the colony is gone, or once it's past every horizon and the year's protection (STAMP_DAYS).
 * 0.3.0.0 (fix): forRemoval, what keeping a building is worth to a remover: CAValue.keep without the build side's charge for
 * inputs your colonies lack, and with the shortage it would leave read against today's supply, builds under way left out
 * (every remover reads it: CAPrune, CAOverLimit, CAEndgame and CACapital's swap).
 * 0.3.0.0 (fix, B4 review): and two removers that 0.2.45.1 read more kindly get 0.2.45.1's reading as a floor, so an existing save
 * sees no more of them: pruning never counts less relief per line than 0.2.45.1's empire-wide reading (reliefFloor: your
 * colonies' summed demand against their summed supply, at the full rate, legal or not), and the capital's make-room swap
 * counts the old building's money at weight 1 outside the endgame, as 0.2.45.1 did (spending freely counted it a quarter
 * against Commerce's unweighted plan bonus, so a Refining earning 50,000 a month was swapped out).
 */
public class CATombstone {
    /** A removal: market and building ids, why (a short phrase), until (game timestamp), and what Autopilot had paid for it (0: unknown). */
    public static class Mark { public String market, id, why; public long until; public float paid; }

    /** How long a removal blocks building the same thing at the same colony again (365 days). */
    static final float DAYS = CATuning.TOMBSTONE_DAYS;
    /** How long a building is protected from removal under another valuation than the one it was built under (365 days). */
    static final float PROTECT_DAYS = CATuning.ENDGAME_MEMORY_DAYS;
    /** The game clock's timestamp is in milliseconds (CADay). */
    static final long DAY_MS = 86400000L;
    /** Safety caps on the saved data: far more than a game produces (0.2.45's real save had six removals in 58 months). */
    static final int MAX_MARKS = 400, MAX_SUNK = 2000;
    /**
     * 0.3.0.0 (fix): days a build stamp is read at most: past the longest horizon (no teardown loss left at any H) and the
     * year's protection. 3,600 days.
     */
    static final float STAMP_DAYS = Math.max(PROTECT_DAYS, CATuning.HORIZON_MAX * 30f);

    /** Why a building came down (Mark.why), short for the panel and log. */
    public static final String PRUNE = "stopped paying", OVERLIMIT = "over the industry limit", SHED = "0 stability",
            ENDGAME = "endgame swap", CAPITAL = "room for the capital's trade", TERRAFORM = "idle terraforming structure",
            RESEARCH = "research done", FLOOR = "room for a production floor producer";

    /** The valuation a building was built or is judged under. */
    public static final String MONEY = "money", RICH = "rich", ENDGAME_V = "endgame";
    static final String[] VALUATIONS = {MONEY, RICH, ENDGAME_V};

    // ---------------- the math (pure, JTTombstoneCheck) ----------------

    /** Days a mark until `until` still holds at `now` (both game timestamps): 0 once past, never more than DAYS. */
    static float remaining(long until, long now) {
        if (until <= now) return 0f;
        return Math.min(DAYS, (float) ((until - now) / (double) DAY_MS));
    }

    /**
     * What taking a building down throws away of what was paid for it: the part not yet paid back over the horizon,
     * paid x (1 - months since built / H). Nothing when the price isn't known (built before 0.3.0, or by you) or the horizon has
     * passed; all of it when it's brand new (or its stamp is from a later date than today).
     */
    static float teardownLoss(float paid, float ageDays, float horizonMonths) {
        if (paid <= 0f) return 0f;
        float h = Math.max(1f, horizonMonths);
        float age = Math.max(0f, ageDays) / 30f;
        return paid * Math.max(0f, 1f - age / h);
    }

    /**
     * Whether replacing a building is worth it (research 02, "removing is the same math backwards"): the new one's net present
     * value over the horizon, less what the old one would earn over the horizon left from today (keep x H: it would stand for
     * all of it), less the teardown loss, beats the margin. All in credits. With keep and the loss spread over H this is
     * CAEndgame.beats(score, keep + loss / H) with the margin a month.
     */
    static boolean worthReplacing(float npvNew, float keep, float horizonMonths, float loss, float margin) {
        return npvNew - keep * horizonMonths - Math.max(0f, loss) > margin;
    }

    /** What taking a building down gains over the horizon: the keep it no longer costs (when it loses money), less the teardown loss. Positive: worth taking down. */
    static float removeGain(float keep, float horizonMonths, float loss) {
        return -keep * Math.max(0f, horizonMonths) - Math.max(0f, loss);
    }

    /**
     * Whether a building built under one valuation (builtUnder: money, rich or endgame; null when unknown) is protected from
     * a remover judging it under another (now), ageDays after it was built: for PROTECT_DAYS. A stamp from a later date than
     * today (a clock that went back) counts as new. The same valuation never protects (the teardown loss still counts).
     */
    static boolean protectedFrom(String builtUnder, float ageDays, String now) {
        if (builtUnder == null || now == null || builtUnder.equals(now)) return false;
        return ageDays < PROTECT_DAYS;
    }

    /** Pure: the latest of a building's build stamps (key "marketId:industryId"), as {valuation, timestamp}; null when there's none. */
    static Object[] latest(Map<String, Long> stamps, String key) {
        if (stamps == null || key == null) return null;
        String bestV = null;
        long bestT = Long.MIN_VALUE;
        for (String v : VALUATIONS) {
            Long t = stamps.get(key + "@" + v);
            if (t != null && t > bestT) { bestT = t; bestV = v; }
        }
        Long old = stamps.get(key);   // the endgame's own record (CAEndgame.built), the only one before 0.3.0
        if (old != null && old > bestT) { bestT = old; bestV = ENDGAME_V; }
        return bestV == null ? null : new Object[]{bestV, bestT};
    }

    /**
     * Pure (0.3.0.0 fix): a build stamp's market and building ("m:mining@rich" -> {m, mining}); null for any other key (the
     * endgame's own "m:mining" and "m:-mining", the purpose maps' stamps). CATidy leaves the keys this reads alone by age.
     */
    static String[] stampKey(String k) {
        if (k == null) return null;
        int v = k.lastIndexOf('@');
        if (v <= 0) return null;
        String val = k.substring(v + 1);
        if (!MONEY.equals(val) && !RICH.equals(val) && !ENDGAME_V.equals(val)) return null;
        String base = k.substring(0, v);
        int at = base.lastIndexOf(':');
        if (at <= 0 || at == base.length() - 1) return null;
        return new String[]{base.substring(0, at), base.substring(at + 1)};
    }

    /** Pure (0.3.0.0 fix): a build stamp no reader uses any more: past STAMP_DAYS at now. A stamp from a later date (clock back) never is. */
    static boolean stale(Long stamp, long now) {
        if (stamp == null) return true;
        return now != Long.MIN_VALUE && now > stamp && (now - stamp) / (double) DAY_MS > STAMP_DAYS;
    }

    /** Pure: drops the marks no longer holding at now (and keeps at most MAX_MARKS, the soonest to expire going first). Returns how many went. */
    static int expire(List<Mark> marks, long now) {
        if (marks == null) return 0;
        int n = 0;
        for (Iterator<Mark> it = marks.iterator(); it.hasNext(); ) {
            Mark k = it.next();
            if (k == null || k.market == null || k.id == null || remaining(k.until, now) <= 0f) { it.remove(); n++; }
        }
        if (marks.size() > MAX_MARKS) {
            Collections.sort(marks, new Comparator<Mark>() {
                public int compare(Mark a, Mark b) { return Long.compare(b.until, a.until); }
            });
            while (marks.size() > MAX_MARKS) { marks.remove(marks.size() - 1); n++; }
        }
        return n;
    }

    /** Pure: puts a year's mark for market and building, replacing an older one for the same pair (one mark each). */
    static Mark put(List<Mark> marks, String marketId, String industryId, String why, float paid, long now) {
        for (Iterator<Mark> it = marks.iterator(); it.hasNext(); ) {
            Mark k = it.next();
            if (k != null && marketId.equals(k.market) && industryId.equals(k.id)) it.remove();
        }
        Mark k = new Mark();
        k.market = marketId; k.id = industryId; k.why = why; k.paid = Math.max(0f, paid);
        k.until = now + Math.round(DAYS * (double) DAY_MS);   // in doubles: a float product of these is off by up to a second
        marks.add(k);
        return k;
    }

    /** Pure: days a mark in the list blocks this building here (0: none). */
    static float blocked(List<Mark> marks, String marketId, String industryId, long now) {
        float days = 0f;
        if (marks == null || marketId == null || industryId == null) return 0f;
        for (Mark k : marks) {
            if (k == null || !marketId.equals(k.market) || !industryId.equals(k.id)) continue;
            days = Math.max(days, remaining(k.until, now));
        }
        return days;
    }

    /**
     * Pure (JTTombstoneCheck): records a paid build in the data given: what it cost (an upgrade adds what its base hasn't paid
     * back yet, from) and when, under which valuation (the stamp replaces the building's older ones). h: the horizon now.
     * 0.3.0.0 (fix, B4 review): an upgrade used to carry its base's whole price, and since the stamp is today's that read as
     * brand new: a Heavy Industry paid back 40 months ago, upgraded to Orbital Works for 400,000, read a 600,000 teardown
     * loss. Now only the base's own teardown loss today (its price, its own stamp, horizon h) carries over; from then on it's
     * paid back over the upgrade's horizon (a little slower than the base alone would have been, never its whole price again).
     */
    static void record(CAData d, String marketId, String id, String from, float credits, String valuation, long now, float h) {
        if (d == null || marketId == null || id == null) return;
        Map<String, Float> sunk = d.sunk();
        String k = key(marketId, id);
        float base = from != null ? built(d, marketId, from, h, null, now).loss : 0f;   // 0 without a price or a stamp, as a remover reads it
        sunk.put(k, Math.max(0f, credits) + Math.max(0f, base));
        if (now == Long.MIN_VALUE) return;
        Map<String, Long> stamps = d.endgameBuilt();
        for (String v : VALUATIONS) stamps.remove(k + "@" + v);
        stamps.put(k + "@" + (valuation == null ? MONEY : valuation), now);
    }

    /** Pure (JTTombstoneCheck): a building's build record from the data given, at horizon h, judged under valuation `now` at time t. */
    static Built built(CAData d, String marketId, String industryId, float h, String now, long t) {
        Built b = new Built();
        if (d == null || marketId == null || industryId == null) return b;
        String k = key(marketId, industryId);
        Float p = d.sunk == null ? null : d.sunk.get(k);
        b.paid = p == null ? 0f : p;
        Object[] last = latest(d.endgameBuilt, k);
        if (last != null && t != Long.MIN_VALUE) {
            b.under = (String) last[0];
            b.ageDays = (float) ((t - (Long) last[1]) / (double) DAY_MS);
            b.loss = teardownLoss(b.paid, b.ageDays, h);
        }
        if (protectedFrom(b.under, b.ageDays, now)) b.protect = "built " + valuationText(b.under) + " " + ago(b.ageDays);
        return b;
    }

    // ---------------- the game ----------------

    static long now() {
        try { return Global.getSector().getClock().getTimestamp(); } catch (Throwable t) { return Long.MIN_VALUE; }
    }

    static boolean live() {
        if (Global.getSector() == null) return false;
        try { return !CALog.whatIf(); } catch (Throwable t) { return true; }   // a what-if valuation never records anything
    }

    static String key(String marketId, String industryId) { return marketId + ":" + industryId; }

    /**
     * Marks a removal: the planner won't build it at this colony again for a year (DAYS). why: one of the constants above;
     * paid: what Autopilot had paid for it (sunk), for the log. Its sunk-cost entry and build stamps go with it, so a later
     * rebuild starts fresh.
     */
    public static void add(String marketId, String industryId, String why, float paid) {
        if (marketId == null || industryId == null || !live()) return;
        long now = now();
        if (now == Long.MIN_VALUE) return;
        CAData d = CAData.get();
        List<Mark> marks = d.tombstones();
        expire(marks, now);
        put(marks, marketId, industryId, why, paid, now);
        String k = key(marketId, industryId);
        d.sunk().remove(k);
        for (String v : VALUATIONS) d.endgameBuilt().remove(k + "@" + v);
        CALog.quiet("removal mark: " + industryId + " at " + marketId + " (" + why + "), not built there again for " + Math.round(DAYS) + " days");
    }

    /** Remaining days of the mark, 0 when none. */
    public static float blocked(String marketId, String industryId) {
        if (marketId == null || industryId == null || Global.getSector() == null) return 0f;
        try {
            CAData d = CAData.get();
            if (d.tombstones == null || d.tombstones.isEmpty()) return 0f;
            return blocked(d.tombstones, marketId, industryId, now());
        } catch (Throwable t) { return 0f; }
    }

    /** Every mark still holding (expired ones dropped first); never null. */
    static List<Mark> all(CAData d) {
        if (d == null) return new ArrayList<Mark>();
        List<Mark> marks = d.tombstones();
        long now = now();
        if (now != Long.MIN_VALUE) expire(marks, now);
        return marks;
    }

    // ---------------- sunk-cost memory and valuations ----------------

    /** The valuation a colony's buildings are judged under now: the endgame economy's, spending freely's, or plain money. */
    public static String valuation(MarketAPI m) {
        if (m == null) return MONEY;
        try { if (CAEngine.profitOnly) return MONEY; } catch (Throwable ignored) { }   // fast buildout: money only
        try { if (CAEndgame.at(m)) return ENDGAME_V; } catch (Throwable ignored) { }
        try { if (CASurplus.moneyWeight(m) < 1f) return RICH; } catch (Throwable ignored) { }
        return MONEY;
    }

    /** "normally", "while spending freely", "in the endgame economy", for log lines. */
    static String valuationText(String v) {
        return RICH.equals(v) ? "while spending freely" : ENDGAME_V.equals(v) ? "in the endgame economy" : "normally";
    }

    /**
     * A build or upgrade Autopilot paid for (CABuild.start, once it took effect): remembers what it cost, when, and under
     * which valuation. from: the building an upgrade started from (what it hasn't paid back yet carries over, 0.3.0.0 fix), or
     * null for a new building.
     */
    public static void paid(MarketAPI m, String id, String from, float credits) {
        if (m == null || id == null || !live()) return;
        record(CAData.get(), m.getId(), id, from, credits, valuation(m), now(), CAHorizon.months());
    }

    /** What Autopilot paid for this building here (an upgrade with what its base hadn't paid back), 0 when unknown. */
    public static float sunk(String marketId, String industryId) {
        if (marketId == null || industryId == null || Global.getSector() == null) return 0f;
        try {
            CAData d = CAData.get();
            if (d.sunk == null) return 0f;
            Float v = d.sunk.get(key(marketId, industryId));
            return v == null ? 0f : v;
        } catch (Throwable t) { return 0f; }
    }

    /** One building's build record as a remover reads it. */
    public static final class Built {
        /** What Autopilot paid for it (0: unknown), days since it was built (-1: unknown), the valuation it was built under (null: unknown). */
        public float paid, ageDays = -1f;
        public String under;
        /** The teardown loss at the horizon it was read with (0 when the price or the date isn't known). */
        public float loss;
        /** Why it's protected from the remover's valuation ("built while spending freely 3 months ago"), or null. */
        public String protect;
    }

    /** The build record of a building here, its teardown loss at horizon h, and whether a remover judging it under `now` must leave it. */
    public static Built built(MarketAPI m, String industryId, float h, String now) {
        if (m == null || industryId == null || Global.getSector() == null) return new Built();
        try { return built(CAData.get(), m.getId(), industryId, h, now, now()); } catch (Throwable t) { return new Built(); }
    }

    /** "3 months ago", "12 days ago", for log lines. */
    static String ago(float days) {
        if (days < 0f) return "lately";
        int d = Math.max(1, Math.round(days));
        if (days < 45f) return d + " day" + (d == 1 ? "" : "s") + " ago";
        return Math.round(days / 30f) + " months ago";
    }

    /** "; built 3 months ago for 375,000", or "" when Autopilot doesn't know what it paid or it was a year ago or more. */
    static String paidText(Built b) {
        if (b == null || b.paid <= 0f || b.ageDays < 0f || b.ageDays >= DAYS) return "";
        return "; built " + ago(b.ageDays) + " for " + Misc.getDGSCredits(b.paid);
    }

    // ---------------- what a building is worth to a remover (0.3.0.0 fix) ----------------

    /**
     * Pure (JTTombstoneCheck): a remover's credits a month for a building: export + relief - upkeep, and the engine's other
     * money only when it's positive (salvage, income), as 0.2.45.1's pruning counted it. What's negative there is mostly
     * CAEngine.demand()'s charge for inputs your colonies lack, a build's price: a size-6 Mining read -36,000 a month while
     * any colony was short of drugs (and Fuel Production -45,000 on volatiles), though the live output lines already show any
     * input it's short of.
     */
    static float removalMoney(float export, float relief, float other, float upkeep) {
        return export + relief + Math.max(0f, other) - upkeep;
    }

    /** Pure (JTTombstoneCheck): CAValue's keep (value, its money term at weight wm) with that money swapped for a remover's. */
    static float removalValue(float value, float money, float removal, float wm) {
        return value + wm * (removal - money);
    }

    /**
     * Pure (0.3.0.0 fix, B4 review; JTTombstoneCheck): as above, the remover's money counted at weight wmNow instead of the
     * wmWas CAValue counted it at: the capital's make-room swap counts it at weight 1 outside the endgame, as 0.2.45.1 did.
     */
    static float removalValue(float value, float money, float removal, float wmWas, float wmNow) {
        return value - wmWas * money + wmNow * removal;
    }

    /**
     * Pure (0.3.0.0 fix, B4 review; JTTombstoneCheck): the units 0.2.45.1's prune counted a line as adding: with the market
     * model on, what it makes over the colony's biggest other source today (builds under way not counted); off, all of it.
     */
    static int added2451(int own, int other, boolean model) {
        int o = Math.max(0, own), x = Math.max(0, other);
        return model ? Math.max(0, Math.max(o, x) - x) : o;
    }

    /**
     * Pure (0.3.0.0 fix, B4 review; JTTombstoneCheck): 0.2.45.1's prune relief for one line, credits a month: base price x unit
     * value x the units your colonies would be short of without its added units, read on your colonies' summed demand and
     * summed supply (CAPrune.coverIfRemoved), at the full rate, legal or not. Six colonies wanting 6 food each (36) against
     * two Farmings' 14 is short either way, so a farm's whole output counted, though each colony on its own was covered.
     */
    static float reliefFloor(float base, float uv, int empireDemand, int empireSupply, int added) {
        return base * uv * CAPrune.coverIfRemoved(empireDemand, empireSupply, added);
    }

    /**
     * Pure (JTTombstoneCheck): units your colonies would be short of without a line at `level` at colony `here`, read against
     * today's supply: CAValue.reliefKeep with only the colony's other sources (other) and your other colonies' levels as what's
     * left, builds under way anywhere not counted (CAValue.keepRelief also counts their level, so a producer read as relieving
     * nothing the day a replacement was started, and was pruned before that replacement made anything). localOnly: an illegal
     * good, which only covers the colony's own.
     */
    static int reliefNow(String[] ids, int[] demand, int[] avail, int[] levels, String here, int level, int other, int lost, boolean localOnly) {
        if (lost <= 0 || ids == null || demand == null || avail == null) return 0;
        int without = Math.max(0, other);
        if (localOnly) {
            int i = CAValue.indexOf(ids, here);
            if (i < 0) return 0;
            return CAValue.reliefKeep(new int[]{demand[i]}, new int[]{avail[i]}, level, without, lost);
        }
        if (levels != null) for (int i = 0; i < ids.length; i++) if (!ids[i].equals(here)) without = Math.max(without, levels[i]);
        return CAValue.reliefKeep(demand, avail, level, without, lost);
    }

    /**
     * What keeping a building is worth to a remover, from CAValue.keepDetail's reading of it at this profile (kv, changed in
     * place and returned): the relief read against today's supply (reliefNow, builds under way left out), the engine's other
     * money only when positive (removalMoney), and money and value worked out again at the weights keepDetail used. Every
     * remover reads it (CAPrune, CAOverLimit's ranking, CAEndgame's and the capital's swap). Should a part fail, CAValue's own
     * relief stays (the rest still applies).
     */
    public static CAValue.Kept forRemoval(MarketAPI m, CAValue.Kept kv, CAData.Profile profile) { return forRemoval(m, kv, profile, false, false); }

    /**
     * As above. 0.3.0.0 (fix, B4 review), so an existing save sees no remover more eager than 0.2.45.1's:
     * pruneFloor (CAPrune): each line's relief is at least 0.2.45.1's prune's reading of it (reliefFloor, empire-wide, at the
     * full rate, legal or not). The per-colony reading alone gave a second Farming 0 where 0.2.45.1 gave it its whole output,
     * and counted the flat rate's half premium with the market model off, so a farm 0.2.45.1 kept was pruned and blocked a year.
     * fullMoney (the capital's make-room swap): outside the endgame its money counts at weight 1, as 0.2.45.1's
     * CACapital.replacement counted it; spending freely's quarter set a capital Refining earning 50,000 a month against
     * Commerce's unweighted plan bonus, and it was swapped out.
     */
    public static CAValue.Kept forRemoval(MarketAPI m, CAValue.Kept kv, CAData.Profile profile, boolean pruneFloor, boolean fullMoney) {
        if (m == null || kv == null || kv.p == null) return kv;
        float relief = kv.relief;
        try { relief = Math.max(kv.relief, reliefNow(m, kv, pruneFloor)); }   // builds under way left out, it never relieves less than CAValue read
        catch (Throwable t) { relief = kv.relief; }
        float money = removalMoney(kv.export, relief, kv.other, kv.upkeep);
        boolean eg = CAValue.endgame(m);
        float w0 = CAValue.weights(profile, eg)[0], mw = CAValue.moneyWeight(m, eg);
        kv.value = removalValue(kv.value, kv.money, money, w0 * mw, w0 * (fullMoney && !eg ? 1f : mw));
        kv.relief = relief;
        kv.other = Math.max(0f, kv.other);
        kv.money = money;
        return kv;
    }

    /**
     * The relief of a building's lines (credits a month, as keepDetail prices it) against today's supply; CAValue's when
     * there's no reading today. floor (0.3.0.0 fix, B4 review): each line at least 0.2.45.1's prune's reading (reliefFloor); a
     * line whose own reading fails still gets its floor.
     */
    static float reliefNow(MarketAPI m, CAValue.Kept kv, boolean floor) {
        if (kv.ind == null || kv.lost.isEmpty() && !floor) return 0f;
        CAValue.Econ e = null;
        try { e = CAValue.econNow(); } catch (Throwable ignored) { }
        if (e == null && !floor) return kv.relief;
        boolean model = CAMarket.on();
        float uv = CAEngine.unitValue, sum = 0f;
        for (MutableCommodityQuantity q : kv.ind.getAllSupply()) {
            String com = q.getCommodityId();
            Integer lost = kv.lost.get(com);
            int own = Math.max(0, q.getQuantity().getModifiedInt());
            boolean now = e != null && lost != null && lost > 0, low = floor && own > 0;
            if (!now && !low) continue;
            int other = otherNow(m, kv.ind, com);
            float base = CAValue.price(com), line = 0f;
            if (now) {
                try {
                    boolean legal = CAValue.legal(m, kv.ind, com);
                    CAValue.View v = CAValue.view(com, e, null);   // no pending map: what your colonies get today
                    int r = v == null ? 0 : reliefNow(v.ids, v.demand, v.avail, v.level, m.getId(), Math.max(own, other), other, lost, !legal);
                    line = base * uv * r * (!legal ? CATuning.SHORTAGE_FACTOR : model ? 1f : CATuning.SHORTAGE_FACTOR - 1f);
                } catch (RuntimeException t) { if (!floor) throw t; }   // with the floor, a line that can't be read today keeps its floor
            }
            if (low) line = Math.max(line, reliefFloor(base, uv, empireDemand(com), empireSupply(com), added2451(own, other, model)));
            sum += line;
        }
        return sum;
    }

    /** 0.3.0.0 (fix, B4 review): 0.2.45.1's CAPrune.empireDemand, your colonies' summed demand for a good (max demand, every colony). */
    static int empireDemand(String com) {
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) { CommodityOnMarketAPI c = m.getCommodityData(com); if (c != null) n += c.getMaxDemand(); }
        return n;
    }

    /** 0.3.0.0 (fix, B4 review): 0.2.45.1's CAPrune.empireSupply, your colonies' summed supply of a good (max supply, every colony). */
    static int empireSupply(String com) {
        int n = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) { CommodityOnMarketAPI c = m.getCommodityData(com); if (c != null) n += c.getMaxSupply(); }
        return n;
    }

    /** The colony's largest other source of a good today: CAMarket.largestOther without the builds under way there. */
    static int otherNow(MarketAPI m, Industry skip, String com) {
        int best = 0;
        for (Industry o : m.getIndustries()) if (o != skip) best = Math.max(best, CAMarket.own(o, com));
        return best;
    }

    /**
     * Whether a build under way at this colony (an Autopilot claim, CAMarket.pendingAt) will out-make one of this building's
     * lines that it's the colony's biggest source of today: CAValue reads that line as a duplicate already, before the new
     * building makes anything. CAPrune's 30-day clock waits for it to finish.
     */
    public static boolean outmadeSoon(MarketAPI m, Industry ind) {
        if (m == null || ind == null) return false;
        for (MutableCommodityQuantity q : ind.getAllSupply()) {
            int own = Math.max(0, q.getQuantity().getModifiedInt());
            if (own <= 0) continue;
            String com = q.getCommodityId();
            int other = otherNow(m, ind, com);
            if (own > other && CAMarket.pendingAt(m, com, ind.getId()) > other) return true;
        }
        return false;
    }

    // ---------------- tidying (weekly) ----------------

    /**
     * Weekly (from CAPrune.run, whatever its setting): marks past their year go, and sunk-cost entries and build stamps of
     * buildings that are gone (0.3.0.0 fix: and stamps past every horizon).
     */
    public static void weekly(CAData d) {
        if (d == null || !live() || !CATime.due("tombstone:tidy", 7f)) return;
        long now = now();
        int marks = now == Long.MIN_VALUE ? 0 : expire(d.tombstones, now);
        int sunk = tidySunk(d, now);
        if (marks + sunk > 0) CALog.quiet("removal marks tidied: " + marks + " expired, " + sunk + " cost and build records of buildings no longer there or past every horizon");
    }

    /** A colony as tidySunk reads it (0.3.0.0 fix: one lookup for the cost records and the build stamps). */
    static final int GONE = 0, LEAVE = 1, READ = 2;

    /**
     * GONE: no market with that id in the economy (decivilized, abandoned); LEAVE: not yours now (captured: it comes back with
     * its buildings), an upgrade under way (what it's becoming isn't there yet), or it couldn't be read; READ: in markets.
     */
    static int colony(String mid, Map<String, MarketAPI> markets, Set<String> leave, Set<String> gone) {
        if (gone.contains(mid)) return GONE;
        if (leave.contains(mid)) return LEAVE;
        if (markets.containsKey(mid)) return READ;
        MarketAPI m = null;
        boolean read = false;
        try { m = Global.getSector().getEconomy().getMarket(mid); read = true; } catch (Throwable ignored) { }
        if (read && m == null) { gone.add(mid); return GONE; }   // 0.3.0.0 (B4): the colony is gone
        if (m == null || !m.isPlayerOwned() || upgrading(m)) { leave.add(mid); return LEAVE; }
        markets.put(mid, m);
        return READ;
    }

    /**
     * Drops sunk-cost entries and build stamps of buildings no longer at their colony, and every one of a colony that's gone
     * from the economy (decivilized, abandoned); a colony with an upgrade under way is left alone, as is one that isn't yours
     * now or that couldn't be read (colony()). 0.3.0.0 (fix): build stamps past STAMP_DAYS go too: no horizon reads them. Never
     * more than MAX_SUNK cost entries, the oldest builds going first.
     */
    static int tidySunk(CAData d, long now) {
        int n = 0;
        Map<String, MarketAPI> markets = new HashMap<String, MarketAPI>();
        Set<String> leave = new HashSet<String>(), gone = new HashSet<String>();
        if (d.sunk != null) for (Iterator<Map.Entry<String, Float>> it = d.sunk.entrySet().iterator(); it.hasNext(); ) {
            String k = it.next().getKey();
            int at = k == null ? -1 : k.lastIndexOf(':');
            if (at <= 0) { it.remove(); n++; continue; }
            String mid = k.substring(0, at), id = k.substring(at + 1);
            int st = colony(mid, markets, leave, gone);
            if (st == GONE || st == READ && !markets.get(mid).hasIndustry(id)) { it.remove(); n++; }
        }
        if (d.endgameBuilt != null) for (Iterator<Map.Entry<String, Long>> it = d.endgameBuilt.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            String[] p = stampKey(e.getKey());
            if (p == null) continue;   // not a build stamp: CAEndgame's own and CATidy's
            if (stale(e.getValue(), now)) { it.remove(); n++; continue; }
            int st = colony(p[0], markets, leave, gone);
            if (st == GONE || st == READ && !markets.get(p[0]).hasIndustry(p[1])) { it.remove(); n++; }
        }
        if (d.sunk != null && d.sunk.size() > MAX_SUNK) {   // a safety cap only: the most recent builds stay
            List<String> keys = new ArrayList<String>(d.sunk.keySet());
            final Map<String, Long> stamps = d.endgameBuilt();
            Collections.sort(keys, new Comparator<String>() {
                public int compare(String a, String b) { return Long.compare(stamp(stamps, a), stamp(stamps, b)); }
            });
            for (int i = 0; i < keys.size() - MAX_SUNK; i++) { d.sunk.remove(keys.get(i)); n++; }
        }
        return n;
    }

    static long stamp(Map<String, Long> stamps, String key) {
        Object[] l = latest(stamps, key);
        return l == null ? Long.MIN_VALUE : (Long) l[1];
    }

    static boolean upgrading(MarketAPI m) {
        try { for (Industry ind : m.getIndustries()) if (ind.isUpgrading()) return true; } catch (Throwable t) { return true; }
        return false;
    }
}
