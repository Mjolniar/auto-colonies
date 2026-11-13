package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Emergent Threats: IX Revival's Panopticon cores (0.2.39.3). They're AI cores with one home: its Surveillance Center. A
 * Panopticon Instance works there like an Alpha core and, by the mod's own description, projects Panopticon Monitoring
 * across the system; a Panopticon Core works like a Beta core and raises the center's stability bonus from 2 to 3. The
 * Hegemony counts neither for inspections (only alpha, beta and gamma cores, HegemonyAICoresActivityCause).
 * Autopilot Storage takes them (CAStoragePlugin.allowedCommodity), and once a day one goes into an Auto colony's
 * Surveillance Center that has nothing better: an Instance before a Core, the colony with the most of your colonies in
 * its system first. One stored at another colony is shipped there first (a fee and travel time) like any item. A regular
 * core it replaces goes back to that colony's Autopilot Storage. The AI core placer never touches a building holding a
 * Panopticon core (CAItems.coreSlot skips cores it doesn't know). Only ids are used, so nothing here needs the mod.
 */
public class CAPanopticon {
    public static final String INSTANCE = "ix_panopticon_instance", CORE = "ix_panopticon_core", CENTER = "ix_surveillance_center";

    /** The Panopticon cores, best first. */
    static final String[] BEST_FIRST = {INSTANCE, CORE};

    public static boolean isCore(String id) { return INSTANCE.equals(id) || CORE.equals(id); }

    /** 2 for an Instance, 1 for a Core, 0 for anything else (a regular core or none). Pure. */
    static int rank(String coreId) { return INSTANCE.equals(coreId) ? 2 : CORE.equals(coreId) ? 1 : 0; }

    static boolean enabled() {
        try { return Global.getSettings().getIndustrySpec(CENTER) != null; } catch (Throwable t) { return false; }
    }

    static String name(String id) {
        try { return Global.getSettings().getCommoditySpec(id).getName(); } catch (Throwable t) { return id; }
    }

    /** Your colonies in the market's star system (a Panopticon Instance covers the whole system). */
    static int coloniesInSystem(MarketAPI m) {
        if (m.getStarSystem() == null) return 1;
        int n = 0;
        for (MarketAPI o : Misc.getPlayerMarkets(false)) if (o.getStarSystem() == m.getStarSystem()) n++;
        return n;
    }

    /** One Panopticon core a day into a Surveillance Center. Returns whether it acted. */
    public static boolean run(CAData d, CASettings s, List<MarketAPI> markets) {
        if (!enabled()) return false;
        boolean any = false;
        for (String id : BEST_FIRST) if (CAStores.count(id) >= 1f) any = true;
        if (!any) return false;
        List<MarketAPI> centers = new ArrayList<MarketAPI>();
        for (MarketAPI m : markets) {
            Industry ind = m.getIndustry(CENTER);
            if (ind == null || !ind.isFunctional() || ind.isHidden() || d.colony(m.getId()).locks.contains(CENTER)) continue;
            if (rank(ind.getAICoreId()) >= 2) continue;   // holds an Instance already
            centers.add(m);
        }
        final Map<MarketAPI, Integer> sys = new HashMap<MarketAPI, Integer>();
        for (MarketAPI m : centers) sys.put(m, coloniesInSystem(m));
        Collections.sort(centers, new Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) {
                int c = Integer.compare(sys.get(b), sys.get(a));
                return c != 0 ? c : Integer.compare(b.getSize(), a.getSize());
            }
        });
        for (MarketAPI m : centers) {
            Industry ind = m.getIndustry(CENTER);
            int have = rank(ind.getAICoreId());
            CargoAPI here = CAStores.of(m);
            for (String id : BEST_FIRST) {   // one already stored here goes straight in
                if (rank(id) <= have) break;
                if (here.getCommodityQuantity(id) >= 1f) return install(s, m, ind, id, here);
            }
            boolean coming = false;
            for (String id : BEST_FIRST) if (rank(id) > have && CAStores.inTransit(id, m.getId()) > 0) coming = true;
            if (coming) continue;   // it goes in once it's here
            for (String id : BEST_FIRST) {   // stored at another colony: shipped here first
                if (rank(id) <= have) break;
                MarketAPI holder = CAStores.nearestHolding(id, m);
                if (holder == null || holder == m) continue;
                return CAStores.ship(holder, m, id, null, 1, "for " + ind.getCurrentName());
            }
        }
        return false;
    }

    static boolean install(CASettings s, MarketAPI m, Industry ind, String id, CargoAPI here) {
        float fee = CABudget.fee(s, CAItems.STANDARD_MOVE_FEE);
        if (!CAItems.canPay(s, fee)) return false;
        String old = ind.getAICoreId();
        try {
            ind.setAICoreId(id);
            here.removeCommodity(id, 1f);
            if (old != null) here.addCommodity(old, 1f);
            m.reapplyIndustries();
            CABudget.charge(s, 0f, fee, 0f);
            CALog.did(m.getName() + ": " + name(id) + " into " + ind.getCurrentName() + (fee > 0 ? " (" + Misc.getDGSCredits(fee) + " fee)" : "")
                    + CAStores.from(m, id) + "." + (old != null ? " " + name(old) + " to Autopilot Storage." : ""));
            return true;
        } catch (Throwable t) {
            CADiagnostics.quarantine(CENTER, "installing " + id, t);
            return false;
        }
    }
}
