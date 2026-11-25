package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.3.20.0: Materials colonies. The Materials goal (CAData.Profile.MATERIALS) values goods for your faction over money: money
 * at 0.4 (CAEngine weights), production floors x4 (CAFloors.goalMult) and every good your production orders take
 * (CAFloors otherNeed). Its plan mines and refines (Forge on ore, habitable too; Industrial Hub without deposits).
 * Setting Production, Materials colonies (CASettings.materialsMode: Off, Ask first, Auto; Off in every save):
 * - Weekly (LOOK_DAYS): what's short (shortNow): goods Ashes of the Domain's production orders take a month (spread over
 *   floorOrderMonths) past what your colonies make. While something is short, one Auto colony on Balanced turns to
 *   Materials, up to allowed() of them: the best fit (fit(): deposits, then the industries that work materials), not the
 *   capital.
 * - Once nothing has been short for BACK_DAYS, the colonies it turned go back to Balanced (CAMark MARK, saved). A goal you
 *   set yourself is never changed: only Balanced colonies are turned, and only the ones it turned (still on Materials) go back.
 * - Ask first: a decision (kind "materials", held 180 days after a rejection).
 */
public final class CAMaterials {
    private CAMaterials() { }

    public static final String[] NAMES = {"Off", "Ask first", "Auto"};
    /** Days between looks; days with nothing short before the colonies it turned go back. */
    static final float LOOK_DAYS = 7f, BACK_DAYS = 120f;
    /** One Materials colony per this many colonies, at least one. */
    static final int PER = 5;
    /** CAMark keys: a colony it turned (MARK + market id), and the last day something was short. */
    static final String MARK = "materials:c:", SHORT = "materials:short", KIND = "materials", KEY = "materials:";
    /** Industries that work materials, for fit(). */
    static final Set<String> WORKS = new HashSet<String>(Arrays.asList("mining", "refining", "heavyindustry", "orbitalworks", "fuelprod", "lightindustry"));

    /** The game day of the last look this session; -99 none. CAReset sets it back. */
    static float lastLook = -99f;

    // ---------------- pure ----------------

    /** Pure: Materials colonies allowed with this many colonies: one per PER, at least one. */
    static int allowed(int colonies) { return Math.max(1, colonies / PER); }

    /** Pure: the goods whose need a month is over what's made. */
    static List<String> shortOf(Map<String, Float> need, Map<String, Float> made) {
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Float> e : need.entrySet()) {
            Float m = made.get(e.getKey());
            if (e.getValue() != null && e.getValue() > (m == null ? 0f : m)) out.add(e.getKey());
        }
        return out;
    }

    /** Pure: a colony's fit: its deposit score (2 a deposit plus its richness) and 3 per industry that works materials. */
    static int fit(int deposits, int works) { return Math.max(0, deposits) + 3 * Math.max(0, works); }

    // ---------------- the weekly look ----------------

    /** Daily from CAAuto; looks every LOOK_DAYS. */
    static void run(CAData d, CASettings s, List<MarketAPI> auto) {
        if (s.materialsMode <= 0 || CALog.whatIf() || auto == null) return;
        float today = CATime.today();
        if (today - lastLook < LOOK_DAYS && today >= lastLook) return;
        lastLook = today;
        List<String> sh = shortNow(s);
        if (sh.isEmpty()) { back(d); return; }
        CAMark.set(SHORT, CAMark.now());
        int have = 0;
        for (MarketAPI m : CAExpansion.colonies()) if (d.colony(m.getId()).profile == CAData.Profile.MATERIALS) have++;
        if (have >= allowed(CAExpansion.colonies().size())) return;
        MarketAPI pick = best(d, auto);
        if (pick == null) return;
        String goods = names(sh);
        if (s.materialsMode == 1) { offer(pick, goods); return; }
        turn(d, pick, goods, "Auto");
    }

    /** Goods production orders take a month past what your colonies make; empty without Vaults of Knowledge. */
    static List<String> shortNow(CASettings s) {
        if (!Global.getSettings().getModManager().isModEnabled(CAResearch.MOD)) return new ArrayList<String>();
        CAProjects.readOrders();
        Map<String, Float> need = new LinkedHashMap<String, Float>(), made = new LinkedHashMap<String, Float>();
        for (Map.Entry<String, Integer> e : CAProjects.orders.entrySet()) {
            need.put(e.getKey(), CAFloors.monthly(e.getValue() == null ? 0 : e.getValue(), s.floorOrderMonths));
            made.put(e.getKey(), (float) Math.max(0, CAProjects.yours(e.getKey())));
        }
        return shortOf(need, made);
    }

    /** The best Auto colony on Balanced to turn, or null. */
    static MarketAPI best(CAData d, List<MarketAPI> auto) {
        MarketAPI cap = CACapital.capital(), best = null;
        int bestFit = 0;
        for (MarketAPI m : auto) {
            if (m == null || m == cap || CAEngine.isOutpost(m)) continue;
            if (d.colony(m.getId()).profile != CAData.Profile.BALANCED || CAApprovals.held(KEY + m.getId(), KIND)) continue;
            int deposits = 0, works = 0;
            for (MarketConditionAPI mc : m.getConditions()) {
                String com = CAEngine.DEPOSIT.get(mc.getId());
                if (com == null || "food".equals(com)) continue;
                Integer mod = CAEngine.DEPOSIT_MOD.get(mc.getId());
                deposits += 2 + (mod == null ? 0 : mod);
            }
            for (Industry ind : m.getIndustries()) if (WORKS.contains(ind.getSpec().getId())) works++;
            int f = fit(deposits, works);
            if (f > bestFit || (f == bestFit && f > 0 && best != null && m.getSize() > best.getSize())) { best = m; bestFit = f; }
        }
        return bestFit > 0 ? best : null;
    }

    static void turn(CAData d, MarketAPI m, String goods, String by) {
        d.colony(m.getId()).profile = CAData.Profile.MATERIALS;
        CAMark.set(MARK + m.getId(), CAMark.now());
        CALog.did(m.getName() + ": Goal set to Materials (" + by + "). Production orders are short of " + goods + ".", true);
    }

    static void offer(final MarketAPI m, final String goods) {
        final String id = m.getId();
        CAApprovals.offer(KEY + id, KIND, m, "Goal: Materials", 0f, 0f, null, () -> {
            CAData d = CAData.get();
            if (!m.isPlayerOwned()) return "Not your colony now";
            if (d.colony(id).profile != CAData.Profile.BALANCED) return "Not changed: its goal isn't Balanced now";
            turn(d, m, goods, "your approval");
            return null;
        },
                "Production orders are short of " + goods + ".",
                m.getName() + " would put mining, refining and industry for your faction ahead of money.",
                "It goes back to Balanced once the orders have what they need.",
                "If you reject it, it won't be offered again for 180 days.");
    }

    /** Nothing short: after BACK_DAYS, the colonies it turned and still on Materials go back to Balanced. */
    static void back(CAData d) {
        Long at = CAMark.at(SHORT);
        if (at != null && CAMark.days(at, CAMark.now()) < BACK_DAYS) return;
        for (String k : CAMark.keys(MARK)) {
            String id = k.substring(MARK.length());
            CAMark.clear(k);
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m == null || !m.isPlayerOwned()) continue;
            CAData.Colony c = d.colony(id);
            if (c.profile != CAData.Profile.MATERIALS) continue;   // you changed it: yours now
            c.profile = CAData.Profile.BALANCED;
            CALog.did(m.getName() + ": Goal back to Balanced. Production orders have what they need.", true);
        }
    }

    /** "Metals, Heavy Machinery and 2 more". */
    static String names(List<String> ids) {
        List<String> n = new ArrayList<String>();
        for (String id : ids) {
            try { n.add(Global.getSettings().getCommoditySpec(id).getName()); } catch (Throwable t) { n.add(id); }
        }
        return CAExpansion.roomText(n);
    }
}
