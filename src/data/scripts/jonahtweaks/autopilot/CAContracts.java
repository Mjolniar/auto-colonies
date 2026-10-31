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
 */
public class CAContracts {
    public static final String MOD = "aotd_theory_of_toolbox";
    static final String PKG = "data.kaysaar.aotd.tot.";
    static final float MARGIN = 0.15f;
    static final int MAX_PER_DAY = 3;
    static final String ACCEPT = "accepting trade contracts";

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
        for (Object d : ((Map<String, Object>) CARefl.invoke(cData, c)).values()) {
            String com = String.valueOf(CARefl.invoke(dCom, d));
            int need = ((Number) CARefl.invoke(dReq, d)).intValue();
            if (need <= 0) continue;
            float base = Global.getSettings().getCommoditySpec(com).getBasePrice();
            int held = stuck(com);
            int spare = i(prod, com, "player") - i(demand, com, "player") - held;
            int out = Math.max(0, i(outside, com, "player"));
            float cut = ((Number) CARefl.invoke(exportCut, null, com, false)).floatValue();
            o.pay += need * base * ((Number) CARefl.invoke(dCut, d)).floatValue();
            o.lost += (Math.min(Math.max(0, spare), out) - Math.min(Math.max(0, spare - need), out)) * base * cut;
            o.lines.add(need + " " + commodityName(com) + " (" + Math.max(0, spare) + " spare)");
            if (spare - need < Math.ceil(MARGIN * need)) {
                o.feasible = false;
                o.why = "not enough spare " + commodityName(com) + " (" + Math.max(0, spare) + " of " + (int) Math.ceil(need * (1f + MARGIN)) + " needed"
                        + (held > 0 ? "; " + held + " more is at colonies under siege or without accessibility, which can't deliver" : "") + ")";
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
            CALog.add("Trade contract: " + who + " accepted " + o.name + ": " + join(o.lines) + " per month for " + o.months + " months, "
                    + Misc.getDGSCredits(o.pay) + " per month (" + Misc.getDGSCredits(o.gain) + " over exporting)"
                    + (o.rewards.isEmpty() ? "" : "; rewards: " + join(o.rewards)), true);
            cached = null;
            return null;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "accepting trade contract " + id + " failed", t);
            return "That failed; see starsector.log";
        }
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
        float now = Global.getSector().getClock().getElapsedDaysSince(0L);
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

    // ---------------- daily pass ----------------

    /** Auto: takes the best offers that fit and pay more than exporting. Any mode: logs a contract that misses a delivery. */
    @SuppressWarnings("unchecked")
    public static void run(CAData d, CASettings s) {
        if (s.contractMode == 2 || !active()) return;
        try {
            for (Object c : ((Map<String, Object>) CARefl.invoke(activeMap, mgr())).values()) {
                if (c == null || Boolean.TRUE.equals(CARefl.invoke(cPlayer, c)) || !live(c)) continue;
                int missed = ((Number) CARefl.invoke(cMissed, c)).intValue();
                if (missed > 0 && CALog.once("contract:" + CARefl.invoke(cId, c) + ":" + missed)) {
                    CALog.add("Trade contract with " + CARefl.invoke(cName, c) + " missed a delivery (" + missed + " of " + CARefl.invoke(cAllowed, c)
                            + " allowed); colonies that make what it needs now score higher", true);
                }
            }
            if (s.contractMode != 1) return;
            for (int n = 0; n < MAX_PER_DAY; n++) {
                Offer best = null;
                for (Offer o : offers()) if (o.feasible && o.gain > 0f) { best = o; break; }
                if (best == null || accept(best.id, "Auto") != null) return;
            }
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "trade contract step failed", t);
        }
    }

    // ---------------- empire panel ----------------

    @SuppressWarnings("unchecked")
    public static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        t.addPara("Mode: %s. Takes offers your colonies can fill with 15%% to spare that pay more than exporting the same goods; "
                + "three missed months end a contract and cost merchant renown.", 6f, hl, CASettings.CONTRACT_NAMES[Math.max(0, Math.min(2, s.contractMode))]);
        if (!active()) { t.addPara("Theory of Toolbox's trade economy has no data for your colonies yet (it runs at month end).", gray, 6f); return; }
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
                t.addPara("Short for held contracts: %s. Buildings that make these score as if your colonies lack them.", 6f, bad, join(l));
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
                    ButtonAPI b = t.addButton("Accept", new String[]{"contract", "accept", o.id}, base, dark, Alignment.MID, CutStyle.ALL, 120f, 22f, 4f);
                    b.setEnabled(o.gain > 0f);
                }
            }
        } catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "trade contract section failed", e);
            t.addPara("Trade contract info unavailable; see starsector.log.", gray, 6f);
        }
    }
}
