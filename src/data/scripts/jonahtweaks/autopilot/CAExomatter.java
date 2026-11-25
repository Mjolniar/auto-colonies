package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * 0.3.18.5: Vaults of Knowledge's Exomatter Processing (ExomatterProcessing, VoK 5.x). Once a batch of Shrouded Substrate
 * (4, or 2 improved) is in the Exomatter Processing tab of any of your colonies, it takes the batch and makes a Tenebrium
 * Cell in 35 days (15 with a Beta or Alpha core; an Alpha makes 2). The cells go to the storage of your production
 * gathering point, or of the facility's colony when none is set. It needs the Stabilizing Exotic Matter project done.
 * Autopilot's part, for substrate you put in Autopilot Storage:
 * - Building: the facility is valued by the cells that substrate makes (apply), so the planner builds one while at least
 *   MIN_BATCHES batches are there. Only one: a second is worth nothing while one is built or on the way.
 * - Feeding (run, Auto colonies): each day a facility's tab is topped up to one batch from Autopilot Storage: its own colony's
 *   first, else shipped from the nearest other colony's Autopilot Storage (CAStores.ship, with its fee and days).
 * - Cells: with no gathering point set, cells in the facility colony's storage are moved to the capital's storage.
 * - AI cores: CAItems counts the faster batches (coreGain) while substrate is left.
 */
public class CAExomatter {
    public static final String MOD = "aotd_vok";
    static final String ID = "aotd_exomatter_processing", TAB = "aotd_exomatter_processing";
    static final String SUBSTRATE = "aotd_shrouded_substrate", TENEBRIUM = "aotd_tenebrium";
    /** Batches of substrate in Autopilot Storage (and the tabs) before a facility is worth building: it takes 120 days to build. */
    static final int MIN_BATCHES = 3;
    /** Marines the facility takes (ExomatterProcessing.apply). */
    static final int MARINES = 6;

    static boolean on() {
        try { return Global.getSettings().getModManager().isModEnabled(MOD); } catch (Throwable t) { return false; }
    }

    public static boolean has(String id) { return ID.equals(id) && on(); }

    public static boolean is(Industry ind) { return ind != null && ID.equals(ind.getId()); }

    /** Substrate a batch takes. */
    static int batch(boolean improved) { return improved ? 2 : 4; }

    /** Days a batch takes, by AI core (null: none). */
    static float days(String core) { return "alpha_core".equals(core) || "beta_core".equals(core) ? 15f : 35f; }

    /** Cells a batch makes. */
    static int cells(String core) { return "alpha_core".equals(core) ? 2 : 1; }

    /** Pure: cells a month with this core. */
    static float perMonth(String core) { return cells(core) * 30f / days(core); }

    static float cellPrice() {
        try { return Global.getSettings().getSpecialItemSpec(TENEBRIUM).getBasePrice(); } catch (Throwable t) { return 35000f; }
    }

    static String itemName(String id) {
        try { return Global.getSettings().getSpecialItemSpec(id).getName(); } catch (Throwable t) { return id; }
    }

    static float count(CargoAPI c, String item) {
        return c == null ? 0f : c.getQuantity(CargoAPI.CargoItemType.SPECIAL, new SpecialItemData(item, null));
    }

    /** Substrate in every Exomatter Processing tab of yours. */
    static float inTabs() {
        float n = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) n += count(tab(m), SUBSTRATE);
        return n;
    }

    static CargoAPI tab(MarketAPI m) {
        SubmarketAPI sub = m == null ? null : m.getSubmarket(TAB);
        return sub == null ? null : sub.getCargo();
    }

    /** Substrate Autopilot may process: every Autopilot Storage, the tabs, and what is on its way. */
    static float stock() {
        float n = inTabs() + CAStores.count(SUBSTRATE);
        for (MarketAPI m : Misc.getPlayerMarkets(false)) n += CAStores.inTransit(SUBSTRATE, m.getId());
        return n;
    }

    /** Another colony of yours has the facility, or is building it. */
    static boolean elsewhere(MarketAPI m) {
        for (MarketAPI o : Misc.getPlayerMarkets(false)) if (o != m && o.hasIndustry(ID)) return true;
        return false;
    }

    /** Pure: the building's worth a month: the cells `stock` substrate makes, none below MIN_BATCHES batches. */
    static float monthly(float stock, int batch, String core, float price) {
        if (stock < batch * MIN_BATCHES) return 0f;
        return perMonth(core) * price;
    }

    /** The planner's value of the facility here (CAMods.apply); false when it isn't worth building. */
    public static boolean apply(MarketAPI m, CAEngine.Pick p, Map<String, Integer> deficit) {
        if (elsewhere(m)) { p.lines.add("Another colony of yours processes the Shrouded Substrate"); return false; }
        Industry ind = m.getIndustry(ID);
        boolean improved = ind != null && ind.isImproved();
        String core = ind == null ? null : ind.getAICoreId();
        float stock = stock();
        float v = monthly(stock, batch(improved), core, cellPrice());
        CAEngine.demand(p, "marines", MARINES, deficit);
        if (v <= 0f) {
            p.lines.add("Needs " + batch(improved) * MIN_BATCHES + " " + itemName(SUBSTRATE) + " in Autopilot Storage; " + Math.round(stock) + " there");
            return false;
        }
        p.fx[0] += v;
        p.lines.add("Turns the " + Math.round(stock) + " " + itemName(SUBSTRATE) + " in Autopilot Storage into " + itemName(TENEBRIUM) + ": "
                + cells(core) + " every " + Math.round(days(core)) + " days, about " + Misc.getDGSCredits(v) + " a month in value");
        return true;
    }

    /** Credits a month core k (0 alpha, 1 beta, 2 gamma) adds over no core, while substrate is left (CAItems.coreValue). */
    public static float coreGain(Industry ind, int k) {
        String core = k == 0 ? "alpha_core" : k == 1 ? "beta_core" : "gamma_core";
        float stock = stock();
        int b = batch(ind.isImproved());
        if (stock < b) return 0f;
        return (perMonth(core) - perMonth(null)) * cellPrice();
    }

    /** The daily step: feeds each facility at an Auto colony, then moves cells to the capital when no gathering point is set. */
    public static void run(List<MarketAPI> auto) {
        if (!on() || !CASync.has("items")) return;
        for (MarketAPI m : auto) {
            Industry ind = m.getIndustry(ID);
            if (ind == null || !ind.isFunctional() || !CAStores.holds(m)) continue;
            try { feed(m, ind); }
            catch (Throwable t) { CAStep.warn("exomatter step", "feeding Exomatter Processing failed at " + m.getName(), t); }
        }
        try { deliver(); }
        catch (Throwable t) { CAStep.warn("exomatter step", "moving Tenebrium Cells failed", t); }
    }

    /** Tops the facility's tab up to one batch: from this colony's Autopilot Storage, else shipped from another's. */
    static void feed(MarketAPI m, Industry ind) {
        CargoAPI tab = tab(m);
        if (tab == null) return;   // VoK adds the tab when the building applies
        int need = batch(ind.isImproved()) - (int) inTabs();
        if (need <= 0) return;
        SpecialItemData sub = new SpecialItemData(SUBSTRATE, null);
        CargoAPI own = CAStores.of(m);
        int here = Math.min(need, (int) count(own, SUBSTRATE));
        if (here > 0) {
            own.removeItems(CargoAPI.CargoItemType.SPECIAL, sub, here);
            tab.addSpecial(sub, here);
            CALog.quiet(m.getName() + ": " + here + " " + itemName(SUBSTRATE) + " moved from Autopilot Storage to Exomatter Processing");
            need -= here;
        }
        need -= CAStores.inTransit(SUBSTRATE, m.getId());
        if (need <= 0) return;
        for (CAStores.Store s : CAStores.nearest(m)) {
            if (s.market == m) continue;
            int n = Math.min(need, (int) count(s.cargo, SUBSTRATE));
            if (n <= 0) continue;
            for (int i = 0; i < n; i++)
                if (!CAStores.ship(s.cargo, s.market.getPrimaryEntity(), s.market.getName(), m, null, sub, 1, "for Exomatter Processing")) return;
            need -= n;
            if (need <= 0) return;
        }
    }

    /** With no production gathering point, cells land in the facility colony's storage: they are moved to the capital's. */
    static void deliver() {
        if (Global.getSector().getPlayerFaction().getProduction().getGatheringPoint() != null) return;
        MarketAPI cap = CACapital.capital();
        CargoAPI to = cap == null ? null : Misc.getStorageCargo(cap);
        if (to == null) return;
        SpecialItemData cell = new SpecialItemData(TENEBRIUM, null);
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m == cap || !m.hasIndustry(ID)) continue;
            CargoAPI from = Misc.getStorageCargo(m);
            int n = (int) count(from, TENEBRIUM);
            if (n <= 0) continue;
            from.removeItems(CargoAPI.CargoItemType.SPECIAL, cell, n);
            to.addSpecial(cell, n);
            CALog.did(m.getName() + ": " + n + " " + itemName(TENEBRIUM) + " moved to " + cap.getName() + " storage.", true);
        }
    }
}
