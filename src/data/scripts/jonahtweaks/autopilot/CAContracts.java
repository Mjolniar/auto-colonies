package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Ashes of the Domain trade contracts (Theory of Toolbox): monthly deliveries from your colonies' surplus, paid at 2-5x
 * the export cut. Three missed months end a contract and cost merchant renown. An offer is only taken when ToT's own
 * figures (your production against your demand plus every contract you hold) show 15% to spare, and it pays more than
 * the exports those goods would have earned. Accepting repeats what ToT's contract browser does. Programs you issue
 * yourself are left to you. Reflection only; if ToT can't be read, nothing is accepted.
 * ToT delivers only from colonies with a spaceport and accessibility above 0 (a blockade takes up to 60% off), so the
 * surplus of a colony that can't deliver, or is under siege (CASiege), doesn't count as spare.
 *
 * 0.3.0.0 (WS-B5, research 01 section 5): 11 of 64 contracts missed deliveries. ToT 1.0.13 itself counts every contract at
 * once: its demand adds each held, running contract's monthly need, read live (AoTDSectorProductionDemandDataUtils
 * .getTotalDemandFromFaction), and at month end it fills them in order from your colonies with a spaceport and
 * accessibility above 0, each giving its net (supply less its own demand) less what it sent to your short colonies; a
 * contract left short gets a miss. The author's logs show that right after a signature the figures are right (12,949 Ore: 33,000
 * spare, then 20,051 in the same pass), but by a later pass ToT's colony figures had come back up by about the contract
 * (20,445 Ore spare, then 20,428 after a 12,572 contract; 31,975 Fuel, a month later 30,814 after a 24,920 one), and the
 * contracts signed on that spare missed. So CM no longer trusts spare above what was left right after its own signatures:
 * the ledger (CAMark "tot:" stamps in the save: need, base, commodity, contract id) records, per commodity, the spare at
 * signing plus what CM's contracts already running took (base), and while any of them runs spare is at most the lowest base
 * less every running one's need (ceiling, worked out jointly per commodity, so a contract that ends gives its need back).
 * An entry stays for the contract's whole life and goes when ToT no longer holds it running. An offer needs 15% of what it
 * asks on top, and Auto takes one new contract per commodity a month.
 * B5 fix: a contract CM didn't sign (held before 0.3.0, or taken in ToT's own browser) gets an entry the first day CM sees
 * it (seedHeld), its base the spare seen then, so its need is counted against that spare too (conservative); stamped that
 * day, it also counts as that commodity's contract for the month.
 */
public class CAContracts {
    public static final String MOD = "aotd_theory_of_toolbox";
    static final String PKG = "data.kaysaar.aotd.tot.";
    static final float MARGIN = 0.15f;
    static final int MAX_PER_DAY = 3;
    static final String ACCEPT = "accepting trade contracts";
    /** 0.3.0.0 (B5): CAMark prefix of the signed-contract ledger (an entry lives as long as its contract runs). */
    static final String LEDGER = "tot:";
    /**
     * 0.3.0.0 (B5): no longer an expiry. Entries ran out after these days while their contract still ran, and overcommitment
     * came back from the third month; kept only because JTTuningCheck reads it (CATuning.CONTRACT_LEDGER_DAYS).
     */
    static final float LEDGER_DAYS = CATuning.CONTRACT_LEDGER_DAYS;

    static Object mgrInstance, browser, activeMap, addContract, tradeInstance, tradeData;
    static Object cId, cName, cSub, cMonths, cData, cRewards, cPlayer, cExpired, cTerminated, cFrozen, cMissed, cAllowed, cTaken, cSetTaken;
    static Object dCom, dReq, dCut, prod, demand, outside, exportCut, comOn, sdData, sdNet;
    static boolean linked = false, broken = false;

    static boolean link() {
        if (linked || broken) return linked;
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            Class<?> mgr = Class.forName(PKG + "scripts.trade.contracts.AoTDTradeContractManager", true, cl);
            Class<?> c = Class.forName(PKG + "scripts.trade.contracts.AoTDTradeContract", true, cl);
            Class<?> d = Class.forName(PKG + "scripts.trade.contracts.AoTDTradeContract$TradeContractData", true, cl);
            Class<?> u = Class.forName(PKG + "scripts.economy.AoTDSectorProductionDemandDataUtils", true, cl);
            Class<?> e = Class.forName(PKG + "plugins.AoTDCommodityEconSpecManager", true, cl);
            Class<?> tm = Class.forName(PKG + "scripts.trade.manager.AoTDTradeManager", true, cl);
            mgrInstance = CARefl.method(mgr, "getInstance");
            browser = CARefl.method(mgr, "getCurrentlyGeneratedInBrowser");
            activeMap = CARefl.method(mgr, "getActiveContracts");
            addContract = CARefl.method(mgr, "addContract", c);
            tradeInstance = CARefl.method(tm, "getInstance");
            tradeData = CARefl.method(tm, "getFactionTradeData", String.class);
            cId = CARefl.method(c, "getId"); cName = CARefl.method(c, "getNameOfContract"); cSub = CARefl.method(c, "getSubTypeOfContractString");
            cMonths = CARefl.method(c, "getMonthsRemaining"); cData = CARefl.method(c, "getContractData"); cRewards = CARefl.method(c, "getRewards");
            cPlayer = CARefl.method(c, "isIssuedByPlayer"); cExpired = CARefl.method(c, "isExpired"); cTerminated = CARefl.method(c, "isTerminated");
            cFrozen = CARefl.method(c, "isContractFrozen"); cMissed = CARefl.method(c, "getMissedTimes"); cAllowed = CARefl.method(c, "getAllowedMissedTimes");
            cTaken = CARefl.method(c, "itWasTaken"); cSetTaken = CARefl.method(c, "setWasTaken", boolean.class);
            dCom = CARefl.method(d, "getCommodityId"); dReq = CARefl.method(d, "getReqMonthly"); dCut = CARefl.method(d, "getCutFromBasePrice");
            prod = CARefl.method(u, "getTotalProductionFromFaction", String.class, String.class);
            demand = CARefl.method(u, "getTotalDemandFromFaction", String.class, String.class);
            outside = CARefl.method(u, "getTotalEffectiveDemandFromSectorOutsideFromFactionIgnoreContracts", String.class, String.class);
            exportCut = CARefl.method(e, "getCutForCommodity", String.class, boolean.class);
            Class<?> com = Class.forName(PKG + "scripts.commoditydata.AoTDCommodityOnMarket", true, cl);
            Class<?> sd = Class.forName(PKG + "scripts.commoditydata.AoTDSupplyDemandData", true, cl);
            comOn = CARefl.method(com, "getComMarketInstanceSave", MarketAPI.class, String.class);
            sdData = CARefl.method(com, "getSupplyDemandData");
            sdNet = CARefl.method(sd, "getRawNetExport");
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Theory of Toolbox's trade contracts; none are accepted", t);
        }
        return linked;
    }

    static Object mgr() throws Exception { return CARefl.invoke(mgrInstance, null); }

    /** ToT's trade economy has data for your faction (it does once you have a colony and a month has run). */
    public static boolean active() {
        if (!link()) return false;
        try { Object tm = CARefl.invoke(tradeInstance, null); return tm != null && CARefl.invoke(tradeData, tm, "player") != null; }
        catch (Throwable t) { return false; }
    }

    static int i(Object m, Object... a) throws Exception { return ((Number) CARefl.invoke(m, null, a)).intValue(); }

    static Object workers, barrier;
    static boolean workersLinked = false, workersBroken = false;

    /** True while ToT's background economy threads are reading markets or a save is under way; market changes should wait. */
    public static boolean busy() {
        if (!Global.getSettings().getModManager().isModEnabled(MOD)) return false;
        if (!workersLinked && !workersBroken) {
            try {
                Class<?> w = Class.forName(PKG + "scripts.economy.AoTDWorkerManager", true, Global.getSettings().getScriptClassLoader());
                workers = CARefl.method(w, "getRunningWorkers"); barrier = CARefl.method(w, "isSaveBarrierActive");
                workersLinked = true;
            } catch (Throwable t) { workersBroken = true; }
        }
        if (!workersLinked) return false;
        try { return ((Number) CARefl.invoke(workers, null)).intValue() > 0 || Boolean.TRUE.equals(CARefl.invoke(barrier, null)); }
        catch (Throwable t) { return false; }
    }

    /** Your production minus your demand (every contract you hold counted) by ToT's figures, or null when ToT can't say. */
    public static Integer spare(String com) {
        if (!active()) return null;
        try { return i(prod, com, "player") - i(demand, com, "player"); }
        catch (Throwable t) { return null; }
    }

    /** Your production of the commodity by ToT's figures (what its contracts deliver from), or null when ToT can't say. */
    public static Integer production(String com) {
        if (!Global.getSettings().getModManager().isModEnabled(MOD) || !active()) return null;
        try { return i(prod, com, "player"); }
        catch (Throwable t) { return null; }
    }

    /** ToT delivers from a colony only with a spaceport and accessibility above 0; one under siege may lose it any day. */
    static boolean delivers(MarketAPI m) {
        return m.hasSpaceport() && m.getAccessibilityMod().computeEffective(0f) > 0f && !CASiege.held(m);
    }

    /** Surplus of the commodity at your colonies that can't deliver it (ToT counts your markets by faction id, as here). */
    static int stuck(String com) throws Exception {
        int n = 0;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!"player".equals(m.getFactionId()) || delivers(m)) continue;
            Object on = CARefl.invoke(comOn, null, m, com);
            if (on == null) continue;
            n += Math.max(0, ((Number) CARefl.invoke(sdNet, CARefl.invoke(sdData, on))).intValue());
        }
        return n;
    }

    static String commodityName(String id) {
        try { return Global.getSettings().getCommoditySpec(id).getName(); } catch (Throwable t) { return id; }
    }

    static boolean live(Object c) throws Exception {
        return !Boolean.TRUE.equals(CARefl.invoke(cExpired, c)) && !Boolean.TRUE.equals(CARefl.invoke(cTerminated, c)) && !Boolean.TRUE.equals(CARefl.invoke(cFrozen, c));
    }

    // ---------------- offers ----------------

    public static class Offer {
        public String id, name;
        public int months;
        public float pay, lost, gain;
        public boolean feasible = true;
        public String why;
        public List<String> lines = new ArrayList<String>(), rewards = new ArrayList<String>();
        /** 0.3.0.0 (B5): what it asks a month by commodity, and the spare each had when it was scored (for the ledger). */
        public Map<String, Integer> needs = new LinkedHashMap<String, Integer>(), spare = new HashMap<String, Integer>();
    }

    // ---------------- 0.3.0.0 (WS-B5): the signed-contract ledger (research 01 section 5) ----------------

    /**
     * One ledger entry: a commodity a signed contract needs a month, and its base: the spare the commodity had when it was
     * signed plus the needs of CM's contracts for it already running then (what the colonies could give with none of them).
     */
    static final class Signed {
        int need, base;
        String com, contract;
        long at;
    }

    /** Pure: the ledger key for an entry (numbers first, so a contract id with any character in it parses back). */
    static String key(int need, int base, String com, String contract) { return LEDGER + need + "|" + base + "|" + com + "|" + contract; }

    /** Pure: an entry from its key and stamp, or null when the key isn't one. */
    static Signed parse(String key, long at) {
        if (key == null || !key.startsWith(LEDGER)) return null;
        String[] p = key.substring(LEDGER.length()).split("\\|", 4);
        if (p.length < 4) return null;
        try {
            Signed s = new Signed();
            s.need = Integer.parseInt(p[0]); s.base = Integer.parseInt(p[1]); s.com = p[2]; s.contract = p[3]; s.at = at;
            return s;
        } catch (NumberFormatException e) { return null; }
    }

    /**
     * Pure: the most spare this commodity may show while CM's signed contracts for it run: the lowest base among them less
     * all of their needs (jointly, so one that ends gives its need back). Null when none runs: ToT's figure stands.
     */
    static Integer ceiling(List<Signed> entries, String com) {
        long low = Long.MAX_VALUE, needs = 0L;
        boolean any = false;
        for (Signed s : entries) {
            if (s == null || !s.com.equals(com)) continue;
            any = true;
            low = Math.min(low, s.base);
            needs += s.need;
        }
        return any ? (int) Math.max(Integer.MIN_VALUE, low - needs) : null;
    }

    /** Pure: spare as CM counts it: ToT's figure, never above the ledger's ceiling. */
    static int allowed(int tot, Integer ceiling) { return ceiling == null ? tot : Math.min(tot, ceiling); }

    /** Pure: the needs of CM's contracts for this commodity running now. */
    static int running(List<Signed> entries, String com) {
        int n = 0;
        for (Signed s : entries) if (s != null && s.com.equals(com)) n += s.need;
        return n;
    }

    /** Pure: an offer fits when spare, less what signed contracts still take, covers its need and MARGIN of it on top. */
    static boolean fits(int spare, int committed, int need) { return spare - committed - need >= Math.ceil(MARGIN * need); }

    /** The ledger's entries (a copy); tidyLedger keeps only the ones whose contract still runs. */
    static List<Signed> ledger() {
        List<Signed> out = new ArrayList<Signed>();
        for (String k : CAMark.keys(LEDGER)) {
            Long at = CAMark.at(k);
            Signed s = parse(k, at == null ? 0L : at);
            if (s != null) out.add(s);
        }
        return out;
    }

    /** Records a contract just signed: each commodity's need and base (the spare it was scored on plus CM's contracts already running for it). */
    static void sign(Offer o) {
        long now = CAMark.now();
        List<Signed> l = ledger();
        for (Map.Entry<String, Integer> e : o.needs.entrySet()) {
            Integer spare = o.spare.get(e.getKey());
            if (spare == null) continue;   // not scored: nothing to hold it to
            CAMark.set(key(e.getValue(), spare + running(l, e.getKey()), e.getKey(), o.id), now);
        }
    }

    /**
     * Daily: drops the entries whose contract isn't held and running any more (live: the ids of contracts held and running;
     * null: ToT couldn't be read, so nothing goes). An entry counts for its contract's whole life.
     */
    static void tidyLedger(Set<String> live) {
        if (live == null) return;
        for (String k : CAMark.keys(LEDGER)) {
            Long at = CAMark.at(k);
            Signed s = parse(k, at == null ? 0L : at);
            if (s == null || !live.contains(s.contract)) CAMark.clear(k);
        }
    }

    // ---------------- 0.3.0.0 (WS-B5 fix): contracts held before the ledger ----------------
    // Contracts already held when a save moves to 0.3.0, and ones signed in ToT's own browser, had no entry, so their
    // commodity had no ceiling and CM trusted ToT's figure, the one that climbs back by about each contract's need (the m58
    // misses: the author's held Fuel contracts, Fox Colliot and Blair Pratt, left Fuel open to a third). Each such contract gets an
    // entry the first day CM sees it, its base the spare CM sees now: the ceiling then takes its need off that again, as if
    // ToT's figure didn't show it yet. Conservative: at worst its commodity takes no new contract until it ends.

    /** Pure: the ledger has an entry for this contract and commodity. */
    static boolean has(List<Signed> l, String contract, String com) {
        for (Signed s : l) if (s != null && s.contract.equals(contract) && s.com.equals(com)) return true;
        return false;
    }

    /**
     * Writes an entry for each of a held contract's commodities (needs: commodity to monthly need) that has none, its base
     * the spare CM sees now (spareNow; a commodity without one is skipped) plus CM's contracts running for it, as sign()
     * does. Returns how many it wrote.
     */
    static int seed(String contract, Map<String, Integer> needs, Map<String, Integer> spareNow, long at) {
        int n = 0;
        for (Map.Entry<String, Integer> e : needs.entrySet()) {
            String com = e.getKey();
            Integer need = e.getValue(), spare = spareNow.get(com);
            List<Signed> l = ledger();
            if (spare == null || need == null || need <= 0 || has(l, contract, com)) continue;
            CAMark.set(key(need, spare + running(l, com), com, contract), at);
            n++;
        }
        return n;
    }

    /** Spare as CM sees it now (evaluate's figure): ToT's, less what can't deliver, never above the ledger's ceiling. */
    static int seenSpare(String com, List<Signed> ledger) throws Exception {
        return allowed(i(prod, com, "player") - i(demand, com, "player") - stuck(com), ceiling(ledger, com));
    }

    /** Daily: seeds the ledger for held, running contracts from ToT's offers (not your own programs) that have no entry. */
    @SuppressWarnings("unchecked")
    static void seedHeld(List<Object> held) throws Exception {
        long now = CAMark.now();
        for (Object c : held) {
            String id = String.valueOf(CARefl.invoke(cId, c));
            Map<String, Integer> needs = new LinkedHashMap<String, Integer>();
            for (Object d : ((Map<String, Object>) CARefl.invoke(cData, c)).values()) {
                int need = ((Number) CARefl.invoke(dReq, d)).intValue();
                if (need <= 0) continue;
                String com = String.valueOf(CARefl.invoke(dCom, d));
                Integer was = needs.get(com);
                needs.put(com, (was == null ? 0 : was) + need);
            }
            List<Signed> l = ledger();
            Map<String, Integer> spare = new HashMap<String, Integer>();
            List<String> what = new ArrayList<String>();
            for (Map.Entry<String, Integer> e : needs.entrySet()) {
                if (has(l, id, e.getKey())) continue;
                int sp = seenSpare(e.getKey(), l);
                spare.put(e.getKey(), sp);
                what.add(e.getValue() + " " + commodityName(e.getKey()) + " (" + Math.max(0, sp) + " spare)");
            }
            if (!spare.isEmpty() && seed(id, needs, spare, now) > 0)
                CALog.quiet("trade contract " + CARefl.invoke(cName, c) + " was held before Auto Colonies counted it: " + join(what)
                        + "; spare for its goods is now held to that less its need while it runs");
        }
    }

    /** Pure: an offer's commodities include one already contracted this month. */
    static boolean overlaps(Set<String> coms, Set<String> month) {
        for (String c : coms) if (month.contains(c)) return true;
        return false;
    }

    /** Commodities a contract was signed for this game month (Auto takes one new contract per commodity a month). */
    static Set<String> signedThisMonth() {
        Set<String> out = new HashSet<String>();
        try {
            com.fs.starfarer.api.campaign.CampaignClockAPI clock = Global.getSector().getClock();
            for (Signed s : ledger()) {
                com.fs.starfarer.api.campaign.CampaignClockAPI then = clock.createClock(s.at);
                if (then.getCycle() == clock.getCycle() && then.getMonth() == clock.getMonth()) out.add(s.com);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    static String rewardName(Object r) {
        String n = r.getClass().getSimpleName();
        if (n.equals("AICoreReward")) return "AI cores";
        if (n.equals("BlueprintReward")) return "a blueprint";
        if (n.equals("SpecialItemReward")) return "a colony item";
        if (n.equals("MerchantReputationReward")) return "merchant renown";
        if (n.equals("FactionReputationReward")) return null;   // ToT shows it but doesn't apply it
        n = n.replace("Reward", "").replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        return n.isEmpty() ? null : n;
    }

    /** One offer scored against your colonies as they are now (every contract you hold already counted in demand). */
    @SuppressWarnings("unchecked")
    static Offer evaluate(Object c) throws Exception {
        Offer o = new Offer();
        o.id = String.valueOf(CARefl.invoke(cId, c));
        Object sub = CARefl.invoke(cSub, c);
        o.name = CARefl.invoke(cName, c) + (sub != null ? " (" + sub + ")" : "");
        o.months = ((Number) CARefl.invoke(cMonths, c)).intValue();
        List<Signed> ledger = ledger();
        for (Object d : ((Map<String, Object>) CARefl.invoke(cData, c)).values()) {
            String com = String.valueOf(CARefl.invoke(dCom, d));
            int need = ((Number) CARefl.invoke(dReq, d)).intValue();
            if (need <= 0) continue;
            float base = Global.getSettings().getCommoditySpec(com).getBasePrice();
            int held = stuck(com);
            int dem = i(demand, com, "player");
            int tot = i(prod, com, "player") - dem - held;
            // 0.3.0.0 (B5, 01 section 5): never more than was left right after CM's own signatures for it (the ledger's ceiling)
            int spare = allowed(tot, ceiling(ledger, com));
            int signed = Math.max(0, tot - spare);
            Integer had = o.spare.get(com);
            o.spare.put(com, had == null ? spare : Math.min(had, spare));
            int out = Math.max(0, i(outside, com, "player"));
            float cut = ((Number) CARefl.invoke(exportCut, null, com, false)).floatValue();
            o.pay += need * base * ((Number) CARefl.invoke(dCut, d)).floatValue();
            o.lost += (Math.min(Math.max(0, spare), out) - Math.min(Math.max(0, spare - need), out)) * base * cut;
            o.lines.add(need + " " + commodityName(com) + " (" + Math.max(0, spare) + " spare)");
            Integer was = o.needs.get(com);
            o.needs.put(com, (was == null ? 0 : was) + need);
            if (!fits(spare, 0, need)) {
                o.feasible = false;
                o.why = "not enough spare " + commodityName(com) + " (" + Math.max(0, spare) + " of " + (int) Math.ceil(need * (1f + MARGIN)) + " needed"
                        + (held > 0 ? "; " + held + " more is at colonies under siege or without accessibility, which can't deliver" : "")
                        + (signed > 0 ? "; ToT shows " + signed + " more, but that went to contracts already signed" : "") + ")";
            }
        }
        o.gain = o.pay - o.lost;
        for (Object r : ((Map<String, Object>) CARefl.invoke(cRewards, c)).values()) { String n = rewardName(r); if (n != null && !o.rewards.contains(n)) o.rewards.add(n); }
        return o;
    }

    /** Offers in ToT's contract browser, best gain first. Excludes ones already taken and programs you issue. */
    @SuppressWarnings("unchecked")
    public static List<Offer> offers() throws Exception {
        List<Offer> out = new ArrayList<Offer>();
        for (Object c : ((Map<String, Object>) CARefl.invoke(browser, mgr())).values()) {
            if (c == null || Boolean.TRUE.equals(CARefl.invoke(cTaken, c)) || Boolean.TRUE.equals(CARefl.invoke(cPlayer, c))) continue;
            try { out.add(evaluate(c)); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "trade contract offer could not be scored", t); }
        }
        Collections.sort(out, new Comparator<Offer>() { public int compare(Offer a, Offer b) { return Float.compare(b.gain, a.gain); } });
        return out;
    }

    /** Takes an offer the way ToT's browser does, after checking it still fits. Returns null on success. */
    @SuppressWarnings("unchecked")
    public static String accept(String id, String who) {
        if (!active()) return "Theory of Toolbox's trade economy isn't running yet";
        try {
            Map<String, Object> b = (Map<String, Object>) CARefl.invoke(browser, mgr());
            Object c = b.get(id);
            if (c == null) return "That offer is gone";
            Offer o = evaluate(c);
            if (!o.feasible) return "Can't fill it: " + o.why;
            if (!CABreaker.allowed(ACCEPT)) return "Paused for this session: accepting contracts didn't take effect three times";
            b.remove(id);
            CARefl.invoke(cSetTaken, c, true);
            CARefl.invoke(addContract, mgr(), c);
            if (!CABreaker.check(ACCEPT, ((Map<String, Object>) CARefl.invoke(activeMap, mgr())).containsKey(id), "trade contracts")) return "ToT didn't take the contract";
            sign(o);   // 0.3.0.0 (B5): spare for its goods stays at most what's left now, for as long as it runs
            if ("Auto".equals(who)) taken.add(o);   // one line for the day's contracts (run)
            else CALog.add("Contract taken: " + goods(o) + ", " + Misc.getDGSCredits(o.pay) + "/month for " + o.months + " months");
            CALog.quiet("trade contract " + o.name + ": " + join(o.lines) + ", " + o.months + " months, " + Misc.getDGSCredits(o.pay)
                    + "/month (" + Misc.getDGSCredits(o.gain) + " over exporting)" + (o.rewards.isEmpty() ? "" : "; rewards: " + join(o.rewards)));
            cached = null;
            return null;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "accepting trade contract " + id + " failed", t);
            return "That failed; see starsector.log";
        }
    }

    /** Contracts Auto took today, logged as one line. */
    static final List<Offer> taken = new ArrayList<Offer>();

    /** An offer's goods without the "(n spare)" detail: "1,258 Luxury Goods". */
    static String goods(Offer o) {
        List<String> l = new ArrayList<String>();
        for (String s : o.lines) l.add(s.replaceAll("\\s*\\([^)]*spare\\)", ""));
        return join(l);
    }

    static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append(", "); sb.append(s); }
        return sb.toString();
    }

    // ---------------- held contracts ----------------

    static Map<String, Integer> cached;
    static float cachedAt = -1f;

    /** Commodities your held contracts need more of than your colonies make, and by how much; refreshed daily. */
    @SuppressWarnings("unchecked")
    public static Map<String, Integer> shortfalls() {
        float now = CADay.now();
        if (cached != null && now - cachedAt < 1f) return cached;
        Map<String, Integer> out = new HashMap<String, Integer>();
        if (active()) {
            try {
                Set<String> coms = new HashSet<String>();
                for (Object c : ((Map<String, Object>) CARefl.invoke(activeMap, mgr())).values()) {
                    if (c == null || !live(c)) continue;
                    for (Object d : ((Map<String, Object>) CARefl.invoke(cData, c)).values()) coms.add(String.valueOf(CARefl.invoke(dCom, d)));
                }
                for (String com : coms) {
                    int gap = i(demand, com, "player") - i(prod, com, "player");
                    if (gap > 0) out.put(com, gap);
                }
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "trade contract shortfall check failed", t);
            }
        }
        cached = out; cachedAt = now;
        return out;
    }

    static Set<String> contractedCache;
    static float contractedAt = -1f;

    /** Commodities a trade contract you hold still delivers; refreshed daily. Empty without Theory of Toolbox. */
    @SuppressWarnings("unchecked")
    public static Set<String> contracted() {
        float now = CADay.now();
        if (contractedCache != null && now - contractedAt < 1f) return contractedCache;
        Set<String> out = new HashSet<String>();
        if (Global.getSettings().getModManager().isModEnabled(MOD) && active()) {
            try {
                for (Object c : ((Map<String, Object>) CARefl.invoke(activeMap, mgr())).values()) {
                    if (c == null || !live(c)) continue;
                    for (Object d : ((Map<String, Object>) CARefl.invoke(cData, c)).values()) out.add(String.valueOf(CARefl.invoke(dCom, d)));
                }
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "trade contract goods check failed", t);
            }
        }
        contractedCache = out; contractedAt = now;
        return out;
    }

    /**
     * What this building makes that a contract you hold needs, or null (0.2.39.0). Such a building isn't taken down while the
     * contract runs: the endgame took down Hot Springs's Farming (Omega core, Tenebrian Atmospheric Driver) for Light
     * Production, worth 43,000 a month more by itself, and the 207,000-a-month food contract it fed started missing deliveries.
     */
    public static String feeds(com.fs.starfarer.api.campaign.econ.Industry ind) {
        Set<String> need = contracted();
        if (need.isEmpty() || ind == null) return null;
        for (com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity q : ind.getAllSupply())
            if (q.getQuantity().getModifiedInt() > 0 && need.contains(q.getCommodityId())) return q.getCommodityId();
        return null;
    }

    // ---------------- daily pass ----------------

    /** Auto: takes the best offers that fit and pay more than exporting. Any mode: logs a contract that misses a delivery. */
    @SuppressWarnings("unchecked")
    public static void run(CAData d, CASettings s) {
        if (s.contractMode == 2 || !active()) return;
        try {
            Set<String> held = new HashSet<String>();
            List<Object> offered = new ArrayList<Object>();   // 0.3.0.0 (B5 fix): held contracts from ToT's offers, for seedHeld
            for (Object c : ((Map<String, Object>) CARefl.invoke(activeMap, mgr())).values()) {
                if (c == null || !live(c)) continue;
                held.add(String.valueOf(CARefl.invoke(cId, c)));
                if (Boolean.TRUE.equals(CARefl.invoke(cPlayer, c))) continue;
                offered.add(c);
                int missed = ((Number) CARefl.invoke(cMissed, c)).intValue();
                if (missed > 0 && CALog.once("contract:" + CARefl.invoke(cId, c) + ":" + missed))
                    CALog.add("Contract with " + CARefl.invoke(cName, c) + " missed a delivery (" + missed + " of " + CARefl.invoke(cAllowed, c) + ")");
            }
            try { tidyLedger(held); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "trade contract ledger tidy failed", t); }   // 0.3.0.0 (B5)
            // 0.3.0.0 (B5 fix): contracts held before the ledger, or signed in ToT's browser, get an entry too
            try { seedHeld(offered); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "trade contract ledger seeding failed", t); }
            if (s.contractMode != 1) return;
            taken.clear();
            try {
                for (int n = 0; n < MAX_PER_DAY; n++) {
                    Offer best = null;
                    // 0.3.0.0 (B5, 01 section 5): Auto takes one new contract per commodity a month
                    Set<String> month = signedThisMonth();
                    for (Offer o : offers()) if (o.feasible && o.gain > 0f && !overlaps(o.needs.keySet(), month)) { best = o; break; }
                    if (best == null || accept(best.id, "Auto") != null) break;
                }
            } finally {
                if (!taken.isEmpty()) {
                    float pay = 0f, gain = 0f;
                    List<String> what = new ArrayList<String>();
                    for (Offer o : taken) { pay += o.pay; gain += o.gain; what.add(goods(o)); }
                    CALog.add((taken.size() == 1 ? "Contract taken: " : taken.size() + " contracts taken: ") + join(what) + ", " + Misc.getDGSCredits(pay)
                            + "/month (+" + Misc.getDGSCredits(gain) + " over exporting)");
                    taken.clear();
                }
            }
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "trade contract step failed", t);
        }
    }

    // ---------------- empire panel ----------------

    @SuppressWarnings("unchecked")
    public static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        CATheme.mode(t, CASettings.CONTRACT_NAMES[Math.max(0, Math.min(2, s.contractMode))]);   // 0.3.0.0 (WS-F3 fix): the mode as a readout
        t.addPara("Takes offers that pay more than exports, with 15%% to spare.", 4f);
        if (!active()) { t.addPara("No trade data yet.", gray, 6f); return; }
        try {
            int held = 0;
            for (Object c : ((Map<String, Object>) CARefl.invoke(activeMap, mgr())).values()) {
                if (c == null || !live(c)) continue;
                held++;
                int missed = ((Number) CARefl.invoke(cMissed, c)).intValue();
                List<String> needs = new ArrayList<String>();
                for (Object x : ((Map<String, Object>) CARefl.invoke(cData, c)).values()) needs.add(CARefl.invoke(dReq, x) + " " + commodityName(String.valueOf(CARefl.invoke(dCom, x))));
                // the format and its values are built together so they can't disagree
                String fmt = "%s: %s per month, ";
                List<String> args = new ArrayList<String>(Arrays.asList(String.valueOf(CARefl.invoke(cName, c)), join(needs)));
                if (Boolean.TRUE.equals(CARefl.invoke(cPlayer, c))) fmt += "ongoing";
                else { fmt += "%s months left"; args.add("" + CARefl.invoke(cMonths, c)); }
                if (missed > 0) { fmt += ", missed %s"; args.add(missed + " of " + CARefl.invoke(cAllowed, c)); }
                t.addPara(fmt, 4f, missed > 0 ? bad : hl, args.toArray(new String[0]));
            }
            if (held == 0) t.addPara("No contracts held.", gray, 4f);
            Map<String, Integer> short_ = shortfalls();
            if (!short_.isEmpty()) {
                List<String> l = new ArrayList<String>();
                for (Map.Entry<String, Integer> e : short_.entrySet()) l.add(e.getValue() + " " + commodityName(e.getKey()));
                t.addPara("Short for held contracts: %s.", 6f, bad, join(l));
            }
            List<Offer> list = offers();
            t.addPara("Offers this month: %s", 8f, hl, "" + list.size());
            int shown = 0;
            for (Offer o : list) {
                if (shown++ >= 6) break;
                t.addPara("%s, %s months: " + CALog.esc(join(o.lines)) + ". Pays %s per month, %s over exporting" + (o.rewards.isEmpty() ? "." : "; rewards: " + CALog.esc(join(o.rewards)) + "."),
                        6f, o.feasible ? hl : gray, o.name, "" + o.months, Misc.getDGSCredits(o.pay), Misc.getDGSCredits(o.gain));
                if (!o.feasible) { t.addPara("    Can't fill: " + CALog.esc(o.why), gray, 2f); continue; }
                if (s.contractMode == 0) {
                    ButtonAPI b = t.addButton("Accept", new String[]{"contract", "accept", o.id}, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 4f);
                    b.setEnabled(o.gain > 0f);
                }
            }
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "trade contract section failed", e);
            t.addPara(CATheme.NOT_SHOWN, gray, 6f);
        }
    }
}
