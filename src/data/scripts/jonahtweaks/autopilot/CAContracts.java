package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Ashes of the Domain trade contracts (Theory of Toolbox): monthly deliveries from your colonies' surplus, paid at 2-5x
 * the export cut. Three missed months end a contract and cost merchant renown. An offer is only taken when ToT's own
 * figures (your production against your demand plus every contract you hold) show 15% to spare, and it pays more than
 * the exports those goods would have earned. Accepting repeats what ToT's contract browser does. Programs you issue
 * yourself are left to you. Reflection only; if ToT can't be read, nothing is accepted.
 */
public class CAContracts {
    public static final String MOD = "aotd_theory_of_toolbox";
    static final String PKG = "data.kaysaar.aotd.tot.";
    static final float MARGIN = 0.15f;
    static final int MAX_PER_DAY = 3;

    static Method mgrInstance, browser, activeMap, addContract, tradeInstance, tradeData;
    static Method cId, cName, cSub, cMonths, cData, cRewards, cPlayer, cExpired, cTerminated, cFrozen, cMissed, cAllowed, cTaken, cSetTaken;
    static Method dCom, dReq, dCut, prod, demand, outside, exportCut;
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
            mgrInstance = mgr.getMethod("getInstance");
            browser = mgr.getMethod("getCurrentlyGeneratedInBrowser");
            activeMap = mgr.getMethod("getActiveContracts");
            addContract = mgr.getMethod("addContract", c);
            tradeInstance = tm.getMethod("getInstance");
            tradeData = tm.getMethod("getFactionTradeData", String.class);
            cId = c.getMethod("getId"); cName = c.getMethod("getNameOfContract"); cSub = c.getMethod("getSubTypeOfContractString");
            cMonths = c.getMethod("getMonthsRemaining"); cData = c.getMethod("getContractData"); cRewards = c.getMethod("getRewards");
            cPlayer = c.getMethod("isIssuedByPlayer"); cExpired = c.getMethod("isExpired"); cTerminated = c.getMethod("isTerminated");
            cFrozen = c.getMethod("isContractFrozen"); cMissed = c.getMethod("getMissedTimes"); cAllowed = c.getMethod("getAllowedMissedTimes");
            cTaken = c.getMethod("itWasTaken"); cSetTaken = c.getMethod("setWasTaken", boolean.class);
            dCom = d.getMethod("getCommodityId"); dReq = d.getMethod("getReqMonthly"); dCut = d.getMethod("getCutFromBasePrice");
            prod = u.getMethod("getTotalProductionFromFaction", String.class, String.class);
            demand = u.getMethod("getTotalDemandFromFaction", String.class, String.class);
            outside = u.getMethod("getTotalEffectiveDemandFromSectorOutsideFromFactionIgnoreContracts", String.class, String.class);
            exportCut = e.getMethod("getCutForCommodity", String.class, boolean.class);
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Theory of Toolbox's trade contracts; none are accepted", t);
        }
        return linked;
    }

    static Object mgr() throws Exception { return mgrInstance.invoke(null); }

    /** ToT's trade economy has data for your faction (it does once you have a colony and a month has run). */
    public static boolean active() {
        if (!link()) return false;
        try { Object tm = tradeInstance.invoke(null); return tm != null && tradeData.invoke(tm, "player") != null; }
        catch (Throwable t) { return false; }
    }

    static int i(Method m, Object... a) throws Exception { return ((Number) m.invoke(null, a)).intValue(); }

    static String commodityName(String id) {
        try { return Global.getSettings().getCommoditySpec(id).getName(); } catch (Throwable t) { return id; }
    }

    static boolean live(Object c) throws Exception {
        return !Boolean.TRUE.equals(cExpired.invoke(c)) && !Boolean.TRUE.equals(cTerminated.invoke(c)) && !Boolean.TRUE.equals(cFrozen.invoke(c));
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
        o.id = String.valueOf(cId.invoke(c));
        Object sub = cSub.invoke(c);
        o.name = cName.invoke(c) + (sub != null ? " (" + sub + ")" : "");
        o.months = ((Number) cMonths.invoke(c)).intValue();
        for (Object d : ((Map<String, Object>) cData.invoke(c)).values()) {
            String com = String.valueOf(dCom.invoke(d));
            int need = ((Number) dReq.invoke(d)).intValue();
            if (need <= 0) continue;
            float base = Global.getSettings().getCommoditySpec(com).getBasePrice();
            int spare = i(prod, com, "player") - i(demand, com, "player");
            int out = Math.max(0, i(outside, com, "player"));
            float cut = ((Number) exportCut.invoke(null, com, false)).floatValue();
            o.pay += need * base * ((Number) dCut.invoke(d)).floatValue();
            o.lost += (Math.min(Math.max(0, spare), out) - Math.min(Math.max(0, spare - need), out)) * base * cut;
            o.lines.add(need + " " + commodityName(com) + " (" + Math.max(0, spare) + " spare)");
            if (spare - need < Math.ceil(MARGIN * need)) {
                o.feasible = false;
                o.why = "not enough spare " + commodityName(com) + " (" + Math.max(0, spare) + " of " + (int) Math.ceil(need * (1f + MARGIN)) + " needed)";
            }
        }
        o.gain = o.pay - o.lost;
        for (Object r : ((Map<String, Object>) cRewards.invoke(c)).values()) { String n = rewardName(r); if (n != null && !o.rewards.contains(n)) o.rewards.add(n); }
        return o;
    }

    /** Offers in ToT's contract browser, best gain first. Excludes ones already taken and programs you issue. */
    @SuppressWarnings("unchecked")
    public static List<Offer> offers() throws Exception {
        List<Offer> out = new ArrayList<Offer>();
        for (Object c : ((Map<String, Object>) browser.invoke(mgr())).values()) {
            if (c == null || Boolean.TRUE.equals(cTaken.invoke(c)) || Boolean.TRUE.equals(cPlayer.invoke(c))) continue;
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
            Map<String, Object> b = (Map<String, Object>) browser.invoke(mgr());
            Object c = b.get(id);
            if (c == null) return "That offer is gone";
            Offer o = evaluate(c);
            if (!o.feasible) return "Can't fill it: " + o.why;
            b.remove(id);
            cSetTaken.invoke(c, true);
            addContract.invoke(mgr(), c);
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
                for (Object c : ((Map<String, Object>) activeMap.invoke(mgr())).values()) {
                    if (c == null || !live(c)) continue;
                    for (Object d : ((Map<String, Object>) cData.invoke(c)).values()) coms.add(String.valueOf(dCom.invoke(d)));
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
            for (Object c : ((Map<String, Object>) activeMap.invoke(mgr())).values()) {
                if (c == null || Boolean.TRUE.equals(cPlayer.invoke(c)) || !live(c)) continue;
                int missed = ((Number) cMissed.invoke(c)).intValue();
                if (missed > 0 && CALog.once("contract:" + cId.invoke(c) + ":" + missed)) {
                    CALog.add("Trade contract with " + cName.invoke(c) + " missed a delivery (" + missed + " of " + cAllowed.invoke(c)
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
            for (Object c : ((Map<String, Object>) activeMap.invoke(mgr())).values()) {
                if (c == null || !live(c)) continue;
                held++;
                int missed = ((Number) cMissed.invoke(c)).intValue();
                List<String> needs = new ArrayList<String>();
                for (Object x : ((Map<String, Object>) cData.invoke(c)).values()) needs.add(dReq.invoke(x) + " " + commodityName(String.valueOf(dCom.invoke(x))));
                // the format and its values are built together so they can't disagree
                String fmt = "%s: %s per month, ";
                List<String> args = new ArrayList<String>(Arrays.asList(String.valueOf(cName.invoke(c)), join(needs)));
                if (Boolean.TRUE.equals(cPlayer.invoke(c))) fmt += "ongoing";
                else { fmt += "%s months left"; args.add("" + cMonths.invoke(c)); }
                if (missed > 0) { fmt += ", missed %s"; args.add(missed + " of " + cAllowed.invoke(c)); }
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
