package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.util.Misc;
import indevo.industries.courierport.ShippingContract;
import indevo.industries.courierport.ShippingContractMemory;

import java.util.*;

/**
 * Supply shipping through Industrial Evolution's Courier Port: keeps a short Auto colony's Local Resources topped up
 * from another colony's stockpile with a monthly contract, and cancels the contract once the shortage is gone.
 * Industrial Evolution charges its normal shipping cost per delivery.
 */
public class CACourier {
    static final String PREFIX = "Autopilot: ";
    static final String LOCAL = "local_resources";
    static final Set<String> COMMODITIES = new HashSet<String>(Arrays.asList(
            "food", "organics", "domestic_goods", "luxury_goods", "drugs", "organs", "metals", "rare_metals",
            "heavy_machinery", "supplies", "fuel", "volatiles", "ore", "rare_ore", "hand_weapons", "marines", "crew",
            "parts", "IndEvo_parts"));   // 0.3.38.0: Domain-grade Parts (0.98.5a) and Industrial Evolution's Ship Components
    static final Set<String> refused = new HashSet<String>();

    static boolean havePort() {
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            Industry p = m.getIndustry("IndEvo_PrivatePort");
            if (p != null && p.isFunctional()) return true;
        }
        return false;
    }

    static CargoAPI local(MarketAPI m) {
        SubmarketAPI s = m.getSubmarket(LOCAL);
        return s == null ? null : s.getCargo();
    }

    static String key(String toId, String com) { return toId + ":" + com; }

    /**
     * 0.3.9.1 (H M2): the shortage without what Local Resources covers now (the game adds the whole deficit to what's
     * available while the stockpile lasts, so getDeficitQuantity reads 0 then).
     */
    static int rawDeficit(CommodityOnMarketAPI c) { return c == null ? 0 : Math.max(0, c.getMaxDemand() - CAStockpile.rawAvailable(c)); }

    public static void run(List<MarketAPI> auto) {
        CASettings s = CAData.settings();
        if (s.disableShipping || !havePort()) return;
        Map<String, ShippingContract> ours = new HashMap<String, ShippingContract>();
        boolean underReserve = CABuild.credits() < s.reserve;   // 0.3.9.1 (H M3): IndEvo charges each delivery from your credits
        int cut = 0;
        for (ShippingContract c : new ArrayList<ShippingContract>(ShippingContractMemory.getContractList())) {
            if (c.name == null || !c.name.startsWith(PREFIX)) continue;
            String com = c.name.substring(PREFIX.length()).split(" ")[0];
            MarketAPI to = CAAuto.market(c.toMarketId);
            // 0.3.9.7 (G8): an Auto colony under siege is out of the pass's list for now; its contract stays as it is
            if (to != null && CASiege.held(to) && CAData.get().colony(to.getId()).mode == CAData.Mode.AUTO) { ours.put(key(c.toMarketId, com), c); continue; }
            // 0.3.9.1 (H M2): only where stockpiles counter shortages, and the shortage read without the stockpile
            boolean managed = to != null && auto.contains(to) && to.isUseStockpilesForShortages();
            boolean stillShort = managed && rawDeficit(to.getCommodityData(com)) > 0;
            if (stillShort && underReserve) {
                ShippingContractMemory.removeContract(c);
                cut++;
            } else if (!stillShort) {
                ShippingContractMemory.removeContract(c);
                if (managed) CALog.did(to.getName() + ": shortage of " + com + " resolved. Supply contract cancelled.");
                else CALog.did((to == null ? "A colony" : to.getName()) + ": " + com + " supply contract cancelled.");   // 0.3.9.1 (H M2): left Auto, lost, or stockpiles off
            } else ours.put(key(c.toMarketId, com), c);
        }
        if (cut > 0) CALog.did("Courier Port: " + cut + (cut == 1 ? " supply contract" : " supply contracts") + " cancelled. Credits are under the reserve.");   // 0.3.9.1 (H M3)
        if (underReserve || CABudget.available(s) <= 0f) return;   // 0.3.9.1 (H M3): no new contract without budget above the reserve
        for (MarketAPI to : auto) {
            if (!to.isUseStockpilesForShortages()) continue;   // 0.3.9.1 (H M2): deliveries to Local Resources don't cover a shortage there
            for (CommodityOnMarketAPI c : to.getAllCommodities()) {
                String com = c.getId();
                int deficit = rawDeficit(c);   // 0.3.9.1 (H M2)
                if (deficit <= 0 || !COMMODITIES.contains(com) || ours.containsKey(key(to.getId(), com))) continue;
                MarketAPI from = null; float best = 0f;
                for (MarketAPI m : Misc.getPlayerMarkets(false)) {
                    if (m == to) continue;
                    CargoAPI lr = local(m);
                    float q = lr == null ? 0f : lr.getCommodityQuantity(com);
                    if (q > best) { best = q; from = m; }
                }
                if (from == null || best < 100f) continue;
                float amount = Math.min(best * 0.75f, 100f * deficit);
                ShippingContract sc = new ShippingContract();
                sc.name = PREFIX + com + " to " + to.getName();
                sc.fromMarketId = from.getId(); sc.toMarketId = to.getId();
                sc.fromSubmarketId = LOCAL; sc.toSubmarketId = LOCAL;
                sc.scope = ShippingContract.Scope.SPECIFIC_CARGO;
                CargoAPI cargo = Global.getFactory().createCargo(true);
                cargo.addCommodity(com, (int) amount);
                sc.targetCargo = cargo;
                sc.setRecurrentDays(30);
                sc.isActive = true;
                if (!sc.isValid()) {
                    String why = sc.getInvalidReason();
                    if (refused.add(key(to.getId(), com)))
                        CADiagnostics.addIssue("courier", "contract:" + com, "Supply contract for " + com, "IndEvo",
                                CADiagnostics.Level.ANALYZED, "Courier Port refused a Local Resources contract: " + why);
                    continue;
                }
                ShippingContractMemory.addOrReplaceContract(sc);
                CALog.did(to.getName() + ": Courier Port contract started: " + (int) amount + " "
                        + c.getCommodity().getName() + " a month from " + from.getName() + ". Covers a shortage.");
                return;   // at most one new contract per pass
            }
        }
    }
}
