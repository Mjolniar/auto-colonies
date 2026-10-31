package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Storage cheat: your colonies use Autopilot Storage as their stockpile, the way vanilla's Local Resources counters a
 * shortage (LocalResourcesSubmarketPlugin.doShortageCountering). A colony short of a commodity the storage holds gets the
 * missing amount as available supply, and the storage is drawn down at vanilla's rate for that shortage. It's free, every
 * colony that uses stockpiles for shortages shares it, and it comes before the colony's own stockpile. Like vanilla's,
 * it's worked out again every frame. The bonus comes off before every save, so no save ever keeps it.
 */
public class CAStockpile implements EveryFrameScript {
    public static final String MOD_ID = "jt_ca_stockpile";
    /** The commodities carrying the bonus at each colony now, by market id. */
    static final Map<String, Set<String>> applied = new HashMap<String, Set<String>>();
    static boolean warned = false;

    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return false; }

    public void advance(float amount) {
        try { apply(Global.getSector().getClock().convertToDays(amount)); }
        catch (Throwable t) {
            if (!warned) CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autopilot Storage stockpile failed", t);
            warned = true;
        }
    }

    /** A new save or game: nothing carries the bonus. */
    public static void reset() { applied.clear(); warned = false; }

    /** Takes the bonus off every colony (before a save, or with the cheat off). */
    public static void clear() {
        for (MarketAPI m : Misc.getPlayerMarkets(false)) unmodify(m);
        applied.clear();
    }

    /** "Colony: commodity" for each shortage the storage covers now, for the empire panel. */
    public static List<String> covering() {
        List<String> out = new ArrayList<String>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            Set<String> mine = applied.get(m.getId());
            if (mine == null) continue;
            for (String c : mine) {
                CommodityOnMarketAPI com = m.getCommodityData(c);
                out.add(m.getName() + ": " + (com != null && com.getCommodity() != null ? com.getCommodity().getName() : c));
            }
        }
        return out;
    }

    static void unmodify(MarketAPI m) {
        Set<String> mine = applied.get(m.getId());
        if (mine == null) return;
        for (String c : mine) {
            CommodityOnMarketAPI com = m.getCommodityData(c);
            if (com != null) com.getAvailableStat().unmodifyFlat(MOD_ID);
        }
        mine.clear();
    }

    static void apply(float days) {
        CAData d = CAData.get();
        if (!d.settings.storageCheat) {
            if (!applied.isEmpty()) clear();
            return;
        }
        CargoAPI st = d.storage();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            unmodify(m);   // read demand and supply without last frame's bonus
            if (!m.isUseStockpilesForShortages()) continue;
            for (CargoStackAPI stack : st.getStacksCopy()) {
                if (!stack.isCommodityStack()) continue;
                String cid = stack.getCommodityId();
                float monthly = counter(m.getCommodityData(cid), st.getCommodityQuantity(cid));
                if (monthly <= 0f) continue;
                Set<String> mine = applied.get(m.getId());
                if (mine == null) applied.put(m.getId(), mine = new HashSet<String>());
                mine.add(cid);
                float draw = Math.min(st.getCommodityQuantity(cid), monthly * days / 30f);
                if (draw > 0f) st.removeCommodity(cid, draw);
            }
        }
    }

    /**
     * Covers com's shortage from have units in storage: adds the missing amount as available supply and returns the units
     * a month that costs, or 0 when there's no shortage to cover. The colony's own stockpile is left out of what's
     * available, so storage covers the shortage first and vanilla's stockpile stops drawing.
     */
    static float counter(CommodityOnMarketAPI com, float have) {
        if (com == null || have <= 0f || com.isNonEcon() || com.getCommodity() == null || com.getCommodity().isMeta()) return 0f;
        MutableStat avail = com.getAvailableStat();
        int demand = com.getMaxDemand();
        int available = com.getAvailable();
        MutableStat.StatMod local = avail.getFlatStatMod(Submarkets.LOCAL_RESOURCES);
        if (local != null) available -= (int) local.getValue();
        if (demand <= available) return 0f;
        float monthly = (BaseIndustry.getSizeMult(demand) - BaseIndustry.getSizeMult(available)) * com.getCommodity().getEconUnit();
        if (monthly <= 0f) return 0f;
        int daysLeft = Math.max(1, (int) (have / monthly * 30f));
        avail.modifyFlat(MOD_ID, demand - available, "Autopilot Storage (" + daysLeft + (daysLeft == 1 ? " day" : " days") + " left)");
        return monthly;
    }
}
