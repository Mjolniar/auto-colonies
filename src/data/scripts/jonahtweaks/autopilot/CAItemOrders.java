package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ItemEffectsRepo;
import com.fs.starfarer.api.util.Misc;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/**
 * 0.3.19.0: colony items from Ashes of the Domain's production (Vaults of Knowledge's production orders, Theory of Toolbox's
 * production specs), while production priorities are on (Settings, Production) and the Items package is open.
 * - Ordering: one order at a time. Of the colony items your faction can produce (getLearnedSpecsForFaction), the one that
 *   pays back fastest in an Auto colony building with no item (CAItems.emptyTarget, valued as CAItems.itemScore x
 *   POINT_CREDITS) is ordered, one unit, when it pays back within PAYBACK_MONTHS and its production cost fits the monthly
 *   budget above the credit reserve. Items whose production takes other special items are left to you. An order's goods
 *   (Electronic Components and the like) come from your colonies' output through its trade contract; CAProjects gives a
 *   producer to any of them no colony makes.
 * - Delivery: AotD puts a finished item in your production gathering point's Storage (with no gathering point it gives
 *   nothing, so nothing is ordered then). The item Autopilot ordered is moved from there to Autopilot Storage, where
 *   CAItems installs it. An order you cancel is dropped.
 * All by name (CARefl, method handles); without Vaults of Knowledge it does nothing.
 */
public class CAItemOrders {
    static final String MOD = "aotd_vok";
    static final String SPEC_MGR = "data.kaysaar.aotd.tot.produciton.specs.AoTDProductionSpecManager";
    static final String SPEC = "data.kaysaar.aotd.tot.produciton.specs.AoTDProductionSpec";
    static final String TYPE = SPEC + "$AoTDProductionSpecType";
    static final String ORDER = "data.kaysaar.aotd.vok.campaign.econ.produciton.order.AoTDProductionOrderData";
    static final String SNAPSHOT = "data.kaysaar.aotd.vok.campaign.econ.produciton.order.AoTDProductionOrderSnapshot";
    /** An item is ordered only when it pays back its production cost within this many months. */
    static final float PAYBACK_MONTHS = 36f;

    static boolean on(CASettings s) {
        try { return Global.getSettings().getModManager().isModEnabled(MOD) && CASync.has("items") && s.productionOn(); }
        catch (Throwable t) { return false; }
    }

    static Class<?> cls(String name) throws Exception { return Class.forName(name, true, Global.getSettings().getScriptClassLoader()); }

    /** Pure: months an item pays back its cost in; infinite with no value. */
    static float payback(float cost, float perMonth) { return perMonth <= 0f ? Float.POSITIVE_INFINITY : Math.max(0f, cost) / perMonth; }

    /** Pure: "itemId|delivered". */
    static String entry(String item, int delivered) { return item + "|" + delivered; }

    static String itemOf(String entry) { int b = entry.lastIndexOf('|'); return b < 0 ? entry : entry.substring(0, b); }

    static int deliveredOf(String entry) {
        int b = entry.lastIndexOf('|');
        try { return b < 0 ? 0 : Integer.parseInt(entry.substring(b + 1)); } catch (NumberFormatException e) { return 0; }
    }

    static Object make(java.lang.invoke.MethodHandle h, Object... args) throws Exception {
        try { return h.invokeWithArguments(args); }
        catch (Exception e) { throw e; }
        catch (Throwable t) { throw new Exception(t); }
    }

    /** The daily step (CAAuto): delivers what is finished, then orders the next item when none is open. */
    public static void run(List<MarketAPI> auto) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (!on(s) || CALog.whatIf()) return;
        try { deliver(d); }
        catch (Throwable t) { CAStep.warn("item orders", "reading production orders failed", t); return; }
        if (!d.itemOrders().isEmpty()) return;
        MarketAPI gp = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint();
        if (gp == null) {
            if (CALog.once("itemorders:nogp")) CALog.quiet("Item orders wait: no production gathering point is set, and Ashes of the Domain delivers items there");
            return;
        }
        try { order(d, s, auto); }
        catch (Throwable t) { CAStep.warn("item orders", "ordering a colony item failed", t); }
    }

    /** This snapshot of the production manager's open orders, or null when it's gone (finished or cancelled). */
    @SuppressWarnings("unchecked")
    static Object snapshot(String id) throws Exception {
        Object mgr = CARefl.invoke(CARefl.method(cls(CASurplus.AOTD_PRODUCTION), "getInstance"), null);
        Object snaps = mgr == null ? null : CARefl.call(mgr, "getCurrentSnapshots");
        if (!(snaps instanceof List)) return null;
        for (Object snap : (List<Object>) snaps) if (id.equals(CARefl.call(snap, "getId"))) return snap;
        return null;
    }

    /** Units the snapshot's lines have finished. */
    @SuppressWarnings("unchecked")
    static int rewarded(Object snap) throws Exception {
        Object lines = CARefl.get(CARefl.field(snap.getClass(), "productionData"), snap);
        int n = 0;
        if (lines instanceof List) for (Object o : (List<Object>) lines) n += CARefl.getInt(CARefl.field(o.getClass(), "unitsRewarded"), o);
        return n;
    }

    static void deliver(CAData d) throws Exception {
        MarketAPI gp = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint();
        for (Iterator<Map.Entry<String, String>> it = d.itemOrders().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> e = it.next();
            String item = itemOf(e.getValue());
            int delivered = deliveredOf(e.getValue());
            Object snap = snapshot(e.getKey());
            int done = snap == null ? 1 : rewarded(snap);
            boolean core = CAItems.tier(item) >= 0;   // 0.3.30.0: an AI core is a commodity
            if (done > delivered && gp != null) {
                CargoAPI from = Misc.getStorageCargo(gp);
                SpecialItemData data = new SpecialItemData(item, null);
                int n = from == null ? 0 : (int) Math.min(done - delivered, core ? from.getCommodityQuantity(item) : from.getQuantity(CargoAPI.CargoItemType.SPECIAL, data));
                if (n > 0) {
                    CargoAPI to = CAStores.holds(gp) ? CAStores.of(gp) : CAStores.nearest(gp).isEmpty() ? null : CAStores.nearest(gp).get(0).cargo;
                    if (to != null) {
                        if (core) { from.removeCommodity(item, n); to.addCommodity(item, n); }
                        else { from.removeItems(CargoAPI.CargoItemType.SPECIAL, data, n); to.addSpecial(data, n); }
                        delivered += n;
                        CALog.did(gp.getName() + ": " + (core ? CAItems.coreName(CAItems.tier(item)) : CAItems.name(item)) + " produced. Moved to Autopilot Storage.");
                    }
                }
            }
            if (snap == null || delivered >= 1) it.remove();   // one unit an order; a cancelled order is dropped
            else e.setValue(entry(item, delivered));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void order(CAData d, CASettings s, List<MarketAPI> auto) throws Exception {
        Class<?> type = cls(TYPE);
        Object special = Enum.valueOf((Class) type, "SPECIAL_ITEM");
        FactionAPI player = Global.getSector().getPlayerFaction();
        Object learned = CARefl.invoke(CARefl.method(cls(SPEC_MGR), "getLearnedSpecsForFaction", type, FactionAPI.class), null, special, player);
        if (!(learned instanceof List)) return;
        Object bestSpec = null;
        String bestItem = null;
        MarketAPI bestM = null;
        Industry bestInd = null;
        float bestPay = Float.POSITIVE_INFINITY, bestValue = 0f, bestCost = 0f;
        Object alphaSpec = null;
        for (Object spec : (List<Object>) learned) {
            String id = (String) CARefl.call(spec, "getId");
            if (CAItems.CORES[0].equals(id)) { alphaSpec = spec; continue; }   // 0.3.30.0: AI cores (the Turing Engine), below
            SpecialItemSpecAPI ss = Global.getSettings().getSpecialItemSpec(id);
            if (ss == null || ss.getParams() == null || CAItems.NEVER.contains(id) || CAItems.LAMP.equals(id) || id.startsWith("IndEvo_vpc_")) continue;
            CAItems.Idle it = new CAItems.Idle();
            it.data = new SpecialItemData(id, null); it.spec = ss; it.effect = ItemEffectsRepo.ITEM_EFFECTS.get(id);
            if (it.effect == null) continue;
            if (CAStores.count(id) >= 1f) continue;   // one waits in Autopilot Storage already
            boolean shipping = false;
            for (MarketAPI m : auto) if (CAStores.inTransit(id, m.getId()) > 0) shipping = true;
            if (shipping) continue;
            Map<String, Integer> need = (Map<String, Integer>) CARefl.call(spec, "getMapOfResourcesNeeded");
            boolean takesItems = false;
            if (need != null) for (String r : need.keySet()) if (Global.getSettings().getCommoditySpec(r) == null) takesItems = true;
            if (takesItems) continue;   // production that uses up other special items is left to you
            boolean reserved = false;   // 0.3.28.0 (batch 5): no new order while its goods are short for restorations, projects or wonders
            if (need != null) for (Map.Entry<String, Integer> g : need.entrySet()) if (g.getValue() != null && CANeeds.reserved(g.getKey(), g.getValue())) reserved = true;
            if (reserved) {
                if (CALog.once("itemorders:reserved:" + id)) CADiagnostics.LOG.info(CADiagnostics.TAG + "item order for " + id + " waits: its goods are short for commitments");
                continue;
            }
            for (String p : ss.getParams().split(",")) it.targets.add(p.trim());
            float cost = ((Number) CARefl.call(spec, "getProductionCost")).floatValue();
            for (MarketAPI m : auto) {
                if (CAHypershunt.is(m) || !CAMods.itemFits(id, m) || CASiege.held(m)) continue;
                Industry ind = CAItems.emptyTarget(it, m);
                if (ind == null) continue;
                CAData.Colony c = d.colony(m.getId());
                float value = CAItems.itemScore(id, m, CAThreat.effective(m, c.profile)) * CAItems.POINT_CREDITS;
                float pay = payback(cost, value);
                if (pay > PAYBACK_MONTHS || pay >= bestPay) continue;
                bestPay = pay; bestSpec = spec; bestItem = id; bestM = m; bestInd = ind; bestValue = value; bestCost = cost;
            }
        }
        // 0.3.30.0 (plan 2026-11-25, batch 7): an Alpha Core when core placement has room for one that pays back sooner
        if (alphaSpec != null && CAItems.alphaWant > 0f && CAStores.count(CAItems.CORES[0]) < 1f) {
            float cost = ((Number) CARefl.call(alphaSpec, "getProductionCost")).floatValue();
            float pay = payback(cost, CAItems.alphaWant);
            Map<String, Integer> need = (Map<String, Integer>) CARefl.call(alphaSpec, "getMapOfResourcesNeeded");
            boolean ok = true;
            if (need != null) for (Map.Entry<String, Integer> g : need.entrySet())
                if (Global.getSettings().getCommoditySpec(g.getKey()) == null || (g.getValue() != null && CANeeds.reserved(g.getKey(), g.getValue()))) ok = false;
            if (ok && pay <= PAYBACK_MONTHS && pay < bestPay) {
                bestPay = pay; bestSpec = alphaSpec; bestItem = CAItems.CORES[0]; bestInd = null; bestValue = CAItems.alphaWant; bestCost = cost;
                bestM = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint();
            }
        }
        if (bestSpec == null || bestM == null) return;
        String forWhat = bestInd != null ? bestInd.getCurrentName() : CAItems.alphaWantAt;
        String itemName = CAItems.tier(bestItem) >= 0 ? CAItems.coreName(CAItems.tier(bestItem)) : CAItems.name(bestItem);
        if (!CAItems.canMove(s, bestCost) || CABuild.credits() - bestCost < s.reserve) {   // 0.3.21.0: and what the planner saves up for its pick
            if (CALog.once("itemorders:budget:" + bestItem + ":" + Global.getSector().getClock().getMonth()))
                CALog.think(bestM.getName() + ": " + itemName + " for " + forWhat + " waits for budget ("
                        + Misc.getDGSCredits(bestCost) + ").");
            return;
        }
        Class<?> specCls = cls(SPEC), orderCls = cls(ORDER), snapCls = cls(SNAPSHOT);
        MethodHandles.Lookup l = MethodHandles.publicLookup();
        Object line = make(l.findConstructor(orderCls, MethodType.methodType(void.class, String.class, specCls, int.class)), bestItem, bestSpec, 1);
        Object snap = make(l.findConstructor(snapCls, MethodType.methodType(void.class)));
        ((List<Object>) CARefl.get(CARefl.field(snapCls, "productionData"), snap)).add(line);
        CARefl.invoke(CARefl.method(snapCls, "initSnapshotConsumption"), snap);
        d.itemOrders().put((String) CARefl.call(snap, "getId"), entry(bestItem, 0));
        CABudget.charge(s, bestM.getId(), bestCost, 0f, 0f);   // 0.3.21.0: counted against the month's budget; AotD bills the credits as the unit finishes
        StringBuilder goods = new StringBuilder();
        Map<String, Integer> need = (Map<String, Integer>) CARefl.call(bestSpec, "getMapOfResourcesNeeded");
        if (need != null) for (Map.Entry<String, Integer> g : need.entrySet()) {
            if (g.getValue() == null || g.getValue() <= 0) continue;
            if (goods.length() > 0) goods.append(", ");
            goods.append(g.getValue()).append(' ').append(Global.getSettings().getCommoditySpec(g.getKey()).getName());
        }
        CALog.did(bestM.getName() + ": " + itemName + " ordered from production for " + forWhat + ". "
                + Misc.getDGSCredits(bestCost) + (goods.length() > 0 ? " and " + goods : "") + "; about " + Misc.getDGSCredits(bestValue) + " a month in value.");
    }
}
