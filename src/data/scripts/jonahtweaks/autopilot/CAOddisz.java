package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.Map;

/**
 * Oddisz's colony mods (0.2.45.0), read from their source (github.com/andrzejsokolowski):
 * - Cloning (oddisz_cloning, also in Coalesced Little Mods with the same ids): three industries that add growth and cost
 *   stability equal to the colony's size. Valued by the growth they add against the colony's own (CAPolicies.growth), worth
 *   nothing at the size limit, and only built with room for the penalty to grow a size.
 * - Reverse Engineered Private Arsenal (re_private_arsenal, also in the pack): the Reverse Engineering Hub does nothing until
 *   you feed it gear, and taking it down deletes both of its markets with everything in them. Left to you (CAMods.LEFT):
 *   never built, never taken down, its AI core and Drone Replicator never moved.
 * - Sol Renewed (sol_renewed): "Discovering the Past", a crisis of its own (CrisisIntel, not a Hostile Activity factor).
 *   Every industry in Sol adds its build cost / 50,000 to the meter every month for good, and the waves bring raids and
 *   saturation bombardments. Priced into industries there.
 * - Toolbox Economy Curves: rescales AoTD Theory of Toolbox through the same supply and demand stats Autopilot reads live,
 *   so nothing here; registered in CACompat.
 */
public class CAOddisz {
    public static final String CLONING_MOD = "oddisz_cloning", PACK = "coalesced_little_mods", ARSENAL_MOD = "re_private_arsenal",
            CURVES_MOD = "toolbox_economy_curves", SOL_MOD = "sol_renewed";
    public static final String[] CLONING = {"oddisz_clone_vat_experiments", "oddisz_cloning", "oddisz_cloning_megafactory"};
    public static final String[] HUB = {"RePA_ReverseEngPhase1Ind", "RePA_ReverseEngPhase2Ind", "RePA_ReverseEngPhase3Ind"};
    static final float[] BASE_GROWTH = {50f, 125f, 250f};

    // ---------------- Cloning ----------------

    static boolean on(String mod) {
        try { return Global.getSettings().getModManager().isModEnabled(mod); } catch (Throwable t) { return false; }
    }

    static boolean cloningOn() { return on(CLONING_MOD) || on(PACK); }

    /** 0, 1 or 2 for the three Cloning tiers, -1 for anything else. */
    static int tier(String id) {
        for (int i = 0; i < CLONING.length; i++) if (CLONING[i].equals(id)) return i;
        return -1;
    }

    public static boolean has(String id) { return tier(id) >= 0 && cloningOn(); }

    /** Cloning's LunaLib numbers: its own mod's keys, or the pack's renamed ones. */
    static float setting(String own, String packed, float dflt) {
        Float v = on(CLONING_MOD) ? CALuna.f(CLONING_MOD, own) : on(PACK) ? CALuna.f(PACK, packed) : null;
        return v == null ? dflt : v;
    }

    static float baseGrowth(int tier) {
        return setting("baseGrowthTier" + (tier + 1), "cloning_base_growth_tier" + (tier + 1), BASE_GROWTH[tier]);
    }

    static float globalMult() { return setting("globalMult", "cloning_global_mult", 1f); }

    /**
     * Growth points a month from a Cloning tier, as BaseCloning: round(base x (1 + size x log10(size) x s) x mult), s being the
     * scaling factor with an Alpha or Omega core (twice it for an Omega) plus 0.10 with a Pristine Nanoforge; x1.25 with a
     * Biofactory Embryo.
     */
    static int growth(float base, int size, float s, float mult, boolean embryo) {
        float scale = s > 0f && size > 1 ? 1f + size * (float) Math.log10(size) * s : 1f;
        int w = Math.round(base * scale * mult);
        return embryo ? Math.round(w * 1.25f) : w;
    }

    /** What faster growth is worth on the engine's growth scale: twice the speed-up over the colony's own growth, at most 6. */
    static float growthFx(float added, float own) {
        if (added <= 0f) return 0f;
        return Math.min(6f, 2f * added / Math.max(10f, own));
    }

    /** Stability left once Cloning's penalty has grown with the colony by one size: it must stay at or over the floor. */
    static boolean roomForPenalty(float stability, int size, boolean newBuild, float floor) {
        if (!newBuild) return true;   // an upgrade keeps the same penalty
        return stability - size - 1 >= floor;
    }

    public static boolean apply(MarketAPI m, String id, CAEngine.Pick p, Map<String, Integer> deficit) {
        int t = tier(id), size = m.getSize();
        if (t < 0) return false;
        if (size >= Misc.getMaxMarketSize(m)) {
            p.lines.add("Cloning: already at its size limit (" + Misc.getMaxMarketSize(m) + "), so faster growth is worth nothing here");
            return false;
        }
        int was = p.upgrade && p.existing != null ? tier(p.existing.getId()) : -1;
        boolean fresh = was < 0;
        float floor = CAStability.floor();   // 0.3.0.0: the one stability rule (CAStability, WS-A2); A3: the one floor, max(3, minStability)
        if (!roomForPenalty(m.getStabilityValue(), size, fresh, floor) || (fresh && !CAStability.canLose(m, -size, true, null))) {
            p.lines.add("Cloning: costs " + size + " stability, one more each size it grows, and that would take this colony under "
                    + Math.round(floor) + " (" + Math.round(m.getStabilityValue()) + " now)");
            return false;
        }
        float mult = globalMult();
        int now = growth(baseGrowth(t), size, 0f, mult, false), before = fresh ? 0 : growth(baseGrowth(was), size, 0f, mult, false);
        int added = now - before;
        CAPolicies.Growth g = CAPolicies.growth(m);
        float own = g == null ? 0f : g.base;
        float fx = growthFx(added, own);
        p.fx[6] += fx;
        if (fresh) p.fx[1] -= size;
        float with = CAPolicies.monthsToGrow(m, own + added), without = CAPolicies.monthsToGrow(m, own);
        p.lines.add("Cloning: +" + added + " growth a month" + (own > 0.5f ? " on top of " + Math.round(own) : "")
                + (with >= 0f ? ", size " + (size + 1) + " in about " + Math.max(1, Math.round(with)) + " months" + (without >= 0f ? " instead of " + Math.round(without) : "") : "")
                + (fresh ? "; -" + size + " stability, one more each size it grows" : "") + " (estimated)");
        return fx > 0f;
    }

    // ---------------- Reverse Engineered Private Arsenal ----------------

    /** The Reverse Engineering Hub: never built, taken down or touched by Autopilot (see CAMods.LEFT). */
    public static boolean hub(String id) {
        for (String h : HUB) if (h.equals(id)) return true;
        return false;
    }

    // ---------------- Sol Renewed ----------------

    /** Sol's crisis: about 550 points bring three waves (450, 700 and 1,000, then back to 450), so a wave every ~183. */
    static final float SOL_POINTS_PER_WAVE = 550f / 3f;
    /** A wave can saturation-bomb a colony, so one counts half again a vanilla crisis. */
    static final float SOL_WAVE_COST = 1.5f;
    /** CrisisIntel.CREDITS_PER_POINT: an industry adds its build cost / 50,000 a month. */
    static final float SOL_CREDITS_PER_POINT = 50000f;

    static boolean solOn() { return on(SOL_MOD); }

    /** The crisis class Autopilot reads (CACompat link): gone means Sol Renewed changed and the pricing needs a look. */
    static boolean solLink() {
        try { Class.forName("solrenewed.crisis.CrisisIntel", false, Global.getSettings().getScriptClassLoader()); return true; }
        catch (Throwable t) { return false; }
    }

    /** The pack's buildings: Cloning's three and the hub's three. */
    static String[] packIndustries() {
        String[] out = new String[CLONING.length + HUB.length];
        System.arraycopy(CLONING, 0, out, 0, CLONING.length);
        System.arraycopy(HUB, 0, out, CLONING.length, HUB.length);
        return out;
    }

    public static boolean inSol(MarketAPI m) {
        if (!solOn() || m == null) return false;
        StarSystemAPI sys = m.getStarSystem();
        try { return sys != null && sys.getMemoryWithoutUpdate().getBoolean("$sr_sol"); } catch (Throwable t) { return false; }
    }

    static boolean solCrisisOn() { Boolean b = CALuna.b(SOL_MOD, "sr_crisis"); return b == null || b; }

    static float solPace() { Float f = CALuna.f(SOL_MOD, "sr_crisis_pace"); return f == null ? 1f : f; }

    static float solStrength() { Float f = CALuna.f(SOL_MOD, "sr_crisis_strength"); return f == null ? 1f : f; }

    /** Meter points a month an industry costing this adds in Sol, for good. */
    static float solPoints(float cost, float pace) { return Math.max(0f, cost) / SOL_CREDITS_PER_POINT * pace; }

    /** Credits a month those points cost: their share of a wave, at Low profile's rate when it's on. */
    static float solCost(float points, float strength, boolean lowProfile) {
        return points * CACrisis.CRISIS_COST * SOL_WAVE_COST / SOL_POINTS_PER_WAVE * strength * (lowProfile ? CALowProfile.PRICE_MULT : 1f);
    }

    /** Called for every pick (CAEngine.evaluate): an industry in Sol pays for what it adds to Sol's crisis meter. */
    public static void score(MarketAPI m, Industry inst, CAEngine.Pick p) {
        try {
            if (!inSol(m) || !solCrisisOn() || inst == null || !inst.isIndustry() || p.cost <= 0f) return;
            float pts = solPoints(p.cost, solPace()), c = solCost(pts, solStrength(), CALowProfile.on());
            if (c <= 0f) return;
            p.fx[0] -= c;
            p.lines.add("Sol Renewed: adds " + Misc.getRoundedValueMaxOneAfterDecimal(pts) + " a month to Sol's crisis meter for good, "
                    + "and its waves raid and bombard colonies here: about " + Misc.getDGSCredits(c) + " a month");
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Sol crisis pricing failed at " + m.getName(), t); }
    }

    /** The Crises page: Sol's meter, when you have a colony there. */
    public static void render(TooltipMakerAPI t) {
        if (!solOn()) return;
        Object o = null;
        try { o = Global.getSector().getMemoryWithoutUpdate().get("$sr_crisis_ref"); } catch (Throwable ignored) { }
        if (!(o instanceof BaseEventIntel)) return;
        BaseEventIntel ev = (BaseEventIntel) o;
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        CATheme.heading(t, "Sol Renewed", 12f);
        try {
            // 0.3.0.0 (WS-F3 fix): one line (was 43 words); how the meter works on hover
            t.addPara("Discovering the Past: %s of %s, %s a month; a point is priced at %s.", 6f, hl,
                    "" + ev.getProgress(), "" + ev.getMaxProgress(), "+" + ev.getMonthlyProgress(),
                    Misc.getDGSCredits(solCost(1f, solStrength(), CALowProfile.on())));
            CATheme.hover(t, "Waves at 200, 450, 700 and 1,000; the meter does not go down.");
        } catch (Throwable e) {
            t.addPara("Discovering the Past is running in Sol.", gray, 6f);
        }
    }
}
