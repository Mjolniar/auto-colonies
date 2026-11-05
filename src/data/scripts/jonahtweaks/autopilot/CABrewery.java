package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.*;

/**
 * Substance.Abuse (SirHartley, 1.1.d): the Brewery takes an industry slot and makes nothing until a recipe is installed,
 * so the probe colony measures it as worthless. Autopilot builds one only when Autopilot Storage holds a recipe (or a
 * Recipe Book this colony is big enough for) and no Brewery is standing idle; the item placer installs the recipe once
 * it's built. A recipe makes the colony's size in units of its drink from the colony's size in each input. The drinks
 * sell to only a few factions and NPC Light Industry makes them too, so they're valued at a quarter of base price.
 * Other colonies' new demand for drinks carries no penalty, so it isn't counted as a shortage.
 */
public class CABrewery {
    public static final String MOD = "alcoholism", BREWERY = "alcohol_brewery", BOOK = "alcoholism_recipe_book";
    static final float VALUE_SHARE = 0.25f;
    /** Recipe -> its inputs (from AlcoholRepo). */
    static final Map<String, String[]> INPUTS = new LinkedHashMap<String, String[]>();
    static {
        INPUTS.put("stout", new String[]{"food", "organics"}); INPUTS.put("absynth", new String[]{"organics"});
        INPUTS.put("tears", new String[]{"food"}); INPUTS.put("fuel", new String[]{"fuel", "supplies"});
        INPUTS.put("blood", new String[]{"drugs", "organics"}); INPUTS.put("sunshine", new String[]{"food", "organics"});
        INPUTS.put("king", new String[]{"food", "organics"}); INPUTS.put("freedom", new String[]{"food", "organics"});
        INPUTS.put("water", new String[]{"drugs"}); INPUTS.put("tea", new String[]{"food"});
    }

    public static boolean enabled() { return Global.getSettings().getModManager().isModEnabled(MOD); }

    public static boolean has(String id) { return BREWERY.equals(id) && enabled(); }

    /** True for the mod's drinks: other colonies' demand for them has no penalty. */
    public static boolean isDrink(String commodity) { return commodity.startsWith("alcoholism_") && commodity.endsWith("_c"); }

    /** The smallest colony an item works in: a Recipe Book needs at least as many size points as recipes. -1 if it can't be read. */
    public static int minSize(SpecialItemData data) {
        if (!BOOK.equals(data.getId())) return 0;
        try {
            Object m = CARefl.method(data.getClass(), "getBaseList");
            Object l = CARefl.invoke(m, data);
            return l instanceof List ? Math.max(1, ((List<?>) l).size()) : -1;
        } catch (Throwable t) { return -1; }
    }

    /** Recipes Autopilot Storage holds that would work at this size: recipe ids (a book counts as all its recipes). */
    static List<String> recipes(int size) {
        List<String> out = new ArrayList<String>();
        for (CAStores.Held held : CAStores.stacks()) {   // any colony's Autopilot Storage: the recipe is shipped to the Brewery
            CargoStackAPI st = held.stack;
            if (!st.isSpecialStack() || st.getSpecialDataIfSpecial() == null) continue;
            String id = st.getSpecialDataIfSpecial().getId();
            if (id.startsWith("alcoholism_") && id.endsWith("_item")) out.add(id.substring("alcoholism_".length(), id.length() - "_item".length()));
            else if (BOOK.equals(id)) {
                int n = minSize(st.getSpecialDataIfSpecial());
                if (n > 0 && n <= size) out.addAll(INPUTS.keySet());   // a book splits the same output, so value it like its best recipe
            }
        }
        return out;
    }

    static boolean idleBrewery() {
        for (MarketAPI m : com.fs.starfarer.api.util.Misc.getPlayerMarkets(false)) {
            Industry b = m.getIndustry(BREWERY);
            if (b != null && b.getSpecialItem() == null) return true;
        }
        return false;
    }

    /** A Brewery's value with the best recipe in storage; false (don't build) without one, or while another Brewery waits for one. */
    public static boolean apply(MarketAPI m, CAEngine.Pick p, Map<String, Integer> deficit) {
        if (idleBrewery()) return false;
        int s = m.getSize();
        String best = null; float bestV = 0f;
        for (String r : recipes(s)) {
            String[] in = INPUTS.get(r);
            if (in == null || Global.getSettings().getCommoditySpec("alcoholism_" + r + "_c") == null) continue;
            float v = Global.getSettings().getCommoditySpec("alcoholism_" + r + "_c").getBasePrice() * CAEngine.unitValue * s * VALUE_SHARE;
            for (String c : in) { Integer d = deficit.get(c); if (d != null && d > 0) v -= Global.getSettings().getCommoditySpec(c).getBasePrice() * 30f * s; }
            if (best == null || v > bestV) { best = r; bestV = v; }
        }
        if (best == null || bestV <= 0f) return false;
        p.fx[0] += bestV;
        p.lines.add("Brews " + s + " " + Global.getSettings().getCommoditySpec("alcoholism_" + best + "_c").getName()
                + " with the recipe in Autopilot Storage: about " + com.fs.starfarer.api.util.Misc.getDGSCredits(bestV) + " per month");
        for (String c : INPUTS.get(best)) CAEngine.demand(p, c, s, deficit);
        return true;
    }
}
