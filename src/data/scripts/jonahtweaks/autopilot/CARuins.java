package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.10.7: United Aurora Federation's ruin restoration (the Restore ruins option on Population & Infrastructure), run by
 * the planner at Auto colonies (Mod projects; only with UAF on). The same rules as the option: widespread, extensive or vast
 * ruins, no restoration running, an administrator with UAF's Galactic Historian skill governing the colony, and 200,000,
 * 500,000 or 1,000,000 credits. UAF's own condition (uaf_ruins_restoration_<size>) does the rest: 150 to 180 days, halted
 * while the administrator lacks the skill, then one random result for the ruins size, which removes the ruins, Tech-Mining
 * and Domain Archeology.
 * - Worth (expected): the average of the size's results, each scored by the engine like a building's effects (CAValue.add)
 *   as they are with Fixes 1.0.27 (the upkeep results cut upkeep; the core facilities stop after 6 cores), less the
 *   Tech-Mining it removes. Ranked with every other action: its price and fee from the month's budget, within the horizon.
 * - Administrators: a Galactic Historian (UAF's Sophia, or the Historian core, CAHistorian) adds this worth where they could
 *   restore (CAItems.adminWorth), and they aren't moved while a restoration runs (CAItems.moveAdmin).
 * - Hiring Sophia (story mode, Tier III package): once you're Friendly with the UAF, for an administrator's hiring cost plus
 *   the fee, as UAF's own hire does (level 10, its admin tier 3); she arrives in 30 days.
 */
public final class CARuins {
    private CARuins() { }

    public static final String MOD = "uaf", SKILL = "uaf_galactic_historian", SOPHIA = "uaf_admin_sophia", SOURCE = "ruin restoration";
    static final String[] RUINS = {"ruins_widespread", "ruins_extensive", "ruins_vast"};
    static final String[] PROJECT = {"uaf_ruins_restoration_widespread", "uaf_ruins_restoration_extensive", "uaf_ruins_restoration_vast"};
    static final float[] PRICE = {200000f, 500000f, 1000000f};
    static final float DAYS = 165f;
    /** A restoration running: what keeps its Galactic Historian there (CAItems.adminWorth). */
    static final float HOLD = 1e6f;
    /** Fixes 1.0.27: the Beta Core Facility and the Black Site stop after this many cores. */
    static final int CORE_CAP = 6;
    static final float SOPHIA_LEVEL = 10, SOPHIA_TIER = 3;

    static boolean modOn() { return Global.getSettings().getModManager().isModEnabled(MOD); }

    /** Ruin restoration runs: UAF on, and its ability (Mod projects; always with story mode off). */
    static boolean on() { return modOn() && CASync.has("ruins"); }

    /** 0 widespread, 1 extensive, 2 vast; -1 none. */
    static int size(MarketAPI m) {
        for (int i = RUINS.length - 1; i >= 0; i--) if (m.hasCondition(RUINS[i])) return i;
        return -1;
    }

    public static boolean restoring(MarketAPI m) {
        if (m == null) return false;
        for (MarketConditionAPI c : m.getConditions()) if (c.getId() != null && c.getId().startsWith("uaf_ruins_restoration")) return true;
        return false;
    }

    static boolean restorable(MarketAPI m) { return m != null && size(m) >= 0 && !restoring(m); }

    public static boolean historian(PersonAPI p) {
        try { return p != null && p.getStats().getSkillLevel(SKILL) > 0f; } catch (Throwable t) { return false; }
    }

    static boolean auto(MarketAPI m) {
        try { return m.isPlayerOwned() && !CAEngine.isOutpost(m) && CAData.get().colony(m.getId()).mode == CAData.Mode.AUTO; } catch (Throwable t) { return false; }
    }

    // ---------------- worth ----------------

    /** Pure: the average of the results' worths (each equally likely). */
    static float average(float[] w) {
        if (w == null || w.length == 0) return 0f;
        float s = 0f;
        for (float x : w) s += x;
        return s / w.length;
    }

    /** Pure: what a core facility's cores are worth a month over the horizon, capped at CORE_CAP cores. */
    static float coresMonthly(float corePrice, int cores, float months) { return months <= 0f ? 0f : corePrice * Math.min(cores, CORE_CAP) / months; }

    static final Map<String, Float> worthCache = new HashMap<String, Float>();
    static float worthDay = -99f;

    /** The restoration's expected worth a month at m (0 without ruins), worked out once a day per colony. */
    static float expected(MarketAPI m) {
        float now = CADay.now();
        if (!CATime.fresh(worthDay, now, 1f)) { worthCache.clear(); worthDay = now; }
        Float c = worthCache.get(m.getId());
        if (c != null) return c;
        float v = 0f;
        try { v = compute(m); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "ruin restoration value failed at " + m.getName(), t); }
        worthCache.put(m.getId(), v);
        return v;
    }

    static float compute(MarketAPI m) {
        int z = size(m);
        if (z < 0) return 0f;
        CAData.Profile prof = CAData.get().colony(m.getId()).profile;
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        float upk = 0f;
        for (Industry ind : m.getIndustries()) if (!ind.isHidden()) upk += ind.getUpkeep().getModifiedValue();
        float h = CAHorizon.months();
        List<CAEngine.Pick> res = new ArrayList<CAEngine.Pick>();
        CAEngine.scoring(null, m);
        try {
            if (z == 0) {
                CAEngine.Pick a = pick(); more(a, m, "heavyindustry", 1, deficit); res.add(a);
                CAEngine.Pick b = pick(); more(b, m, "lightindustry", 1, deficit); res.add(b);
                CAEngine.Pick c = pick(); c.fx[2] += 0.05f; res.add(c);
                CAEngine.Pick d = pick(); d.fx[1] += 1f; res.add(d);
                CAEngine.Pick e = pick(); e.fx[4] += 0.1f; res.add(e);   // a derelict defense fleet
                CAEngine.Pick f = pick(); f.fx[0] += 0.1f * upk; res.add(f);   // -10% hazard: upkeep
                CAEngine.Pick g = pick(); g.fx[2] += 0.05f; res.add(g);   // Gamma Core Facility: +5% accessibility
            } else if (z == 1) {
                CAEngine.Pick a = pick(); more(a, m, null, 1, deficit); res.add(a);
                CAEngine.Pick b = pick(); b.fx[0] += 0.05f * upk; res.add(b);   // Fixes: -5% upkeep
                CAEngine.Pick c = pick(); c.fx[4] += 0.25f; res.add(c);
                CAEngine.Pick d = pick(); d.fx[3] += 0.5f; d.fx[0] -= 0.2f * upk; res.add(d);   // a military base: ground defense, +20% hazard
                CAEngine.Pick e = pick(); e.fx[0] += coresMonthly(price(Commodities.BETA_CORE), CORE_CAP, h); res.add(e);
            } else {
                CAEngine.Pick a = pick(); a.fx[0] += coresMonthly(price(Commodities.ALPHA_CORE), CORE_CAP, h); res.add(a);   // Black Site
                CAEngine.Pick b = pick(); more(b, m, null, 2, deficit); res.add(b);
                CAEngine.Pick c = pick(); c.fx[4] += 0.15f; res.add(c);   // a dormant defense fleet
                CAEngine.Pick d = pick(); CAEngine.supply(d, "organs", 3, deficit); res.add(d);
                CAEngine.Pick e = pick(); e.fx[0] += 0.2f * upk; res.add(e);   // Fixes: -20% upkeep
                res.add(pick());   // Emergency Fabricators: used by hand
            }
        } finally { CAEngine.scoring(null, null); }
        float[] w = new float[res.size()];
        for (int i = 0; i < w.length; i++) w[i] = CAValue.add(m, res.get(i), prof, false);
        return average(w) - lostTechMining(m);
    }

    static CAEngine.Pick pick() {
        CAEngine.Pick p = new CAEngine.Pick();
        p.id = "uaf_ruins"; p.name = "Ruin restoration";
        return p;
    }

    /** n more of everything the colony's industries make (those with the tag; every industry for null). */
    static void more(CAEngine.Pick p, MarketAPI m, String tag, int n, Map<String, Integer> deficit) {
        for (Industry ind : m.getIndustries()) {
            if (!ind.isIndustry() || ind.isHidden() || (tag != null && !ind.getSpec().hasTag(tag))) continue;
            for (MutableCommodityQuantity q : ind.getAllSupply()) if (q.getQuantity().getModifiedInt() > 0) CAEngine.supply(p, q.getCommodityId(), n, deficit);
        }
    }

    static float price(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getBasePrice(); } catch (Throwable t) { return 0f; }
    }

    /** Tech-Mining's worth a month here, as the engine scores it: the restoration takes it down. */
    static float lostTechMining(MarketAPI m) {
        if (!m.hasIndustry("techmining")) return 0f;
        int r = m.hasCondition("ruins_vast") ? 4 : m.hasCondition("ruins_extensive") ? 3 : m.hasCondition("ruins_widespread") ? 2 : 1;
        float left = m.getMemoryWithoutUpdate().contains("$core_techMiningMult") ? m.getMemoryWithoutUpdate().getFloat("$core_techMiningMult") : 1f;
        return (CAMods.sbtm() ? CAMods.sbtmMonthly(m, left) : 4000f * r * left) * CADevelopment.techMiningMult(m);
    }

    /**
     * What a Galactic Historian adds at m, for CAItems.adminWorth: HOLD while a restoration runs there, the restoration's worth
     * at an Auto colony that could start one, else 0.
     */
    public static float historianWorth(MarketAPI m, PersonAPI admin) {
        if (m == null || !historian(admin) || !on()) return 0f;
        if (restoring(m)) return HOLD;
        if (!restorable(m) || !auto(m)) return 0f;
        return Math.max(0f, expected(m));
    }

    // ---------------- the planner's action ----------------

    /** One action at each Auto colony that could start a restoration now. */
    public static List<CAAction> offer(List<MarketAPI> auto) {
        List<CAAction> out = new ArrayList<CAAction>();
        if (!on()) return out;
        CASettings s = CAData.settings();
        float h = CAHorizon.months();
        for (MarketAPI m : auto) {
            if (!restorable(m) || !historian(m.getAdmin())) continue;
            int z = size(m);
            float worth = expected(m);
            CAAction a = new CAAction();
            a.kind = CAAction.Kind.PROJECT; a.source = SOURCE; a.market = m; a.marketId = m.getId();
            a.id = PROJECT[z]; a.name = "Ruin restoration"; a.why = "a Revitalized Ruins result";
            a.cost = PRICE[z]; a.fee = CABudget.constructionFee(s, PRICE[z]); a.days = DAYS; a.upkeep = 0f;
            a.worth = worth;
            a.npv = CAValue.npv(worth, h, DAYS, a.cost, a.fee);
            a.usesQueue = false;
            a.building = "uaf_ruins";
            a.fromBudget = true; a.fromSurplus = CASurplus.spends(m); a.chargeAll = true;
            a.upkeepCap = CASurplus.upkeepCap(m, s);
            a.starter = (x) -> start(x);
            out.add(a);
        }
        return out;
    }

    /** Starts it as UAF's own option does: the price, then its restoration condition. Null on success. */
    static String start(CAAction a) {
        MarketAPI m = a.market;
        if (!restorable(m)) return "Nothing to restore here";
        if (!historian(m.getAdmin())) return "No administrator with Galactic Historian";
        CASettings s = CAData.settings();
        if (CABuild.credits() - (a.cost + a.fee) < s.reserve) return "Not enough credits above the reserve";
        int z = size(m);
        try {
            m.addCondition(PROJECT[z]);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "ruin restoration start failed at " + m.getName(), t);
            return "UAF refused the restoration";
        }
        CABudget.pay(s, m.getId(), a.cost, a.fee, 0f);
        CALog.did(m.getName() + ": Ruin restoration started for " + Misc.getDGSCredits(a.cost) + ". It takes 150 to 180 days.");
        return null;
    }

    // ---------------- Sophia (story mode, Tier III) ----------------

    /** Daily: hires UAF's Sophia once you're Friendly with the UAF and an Auto colony has ruins to restore. */
    static void hire(CAData d, CASettings s) {
        if (!modOn() || !CASync.has("uafHistorian")) return;
        PersonAPI p = Global.getSector().getImportantPeople().getPerson(SOPHIA);
        if (p == null) return;
        for (com.fs.starfarer.api.characters.AdminData a : Global.getSector().getCharacterData().getAdmins())
            if (a.getPerson() != null && SOPHIA.equals(a.getPerson().getId())) return;   // already yours
        FactionAPI uaf = Global.getSector().getFaction(MOD);
        if (uaf == null || !uaf.getRelToPlayer().isAtWorst(com.fs.starfarer.api.campaign.RepLevel.FRIENDLY)) return;
        boolean ruins = false;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (restorable(m) && auto(m)) ruins = true;
        if (!ruins || CAItems.adminsEmployed() >= CAItems.adminMax()) return;
        float price = 40000f;
        try { price = Global.getSettings().getInt("adminHireTier1"); } catch (Throwable ignored) { }
        float fee = CABudget.fee(s, CAItems.HIRE_FEE);
        if (CABuild.credits() - s.reserve < price + fee) {
            if (CALog.once("ruins:sophia:money")) CALog.think("Administrators: Sophia waits for " + Misc.getDGSCredits(price + fee) + " above the credit reserve.");
            return;
        }
        try {
            p.getStats().setLevel((int) SOPHIA_LEVEL);
            p.getMemoryWithoutUpdate().set("$ome_adminTier", (int) SOPHIA_TIER);
            Global.getSector().getCharacterData().addAdmin(p);
            Global.getSector().getMemoryWithoutUpdate().set("$uaf_sophia_init", true);
            Global.getSector().getMemoryWithoutUpdate().set("$uaf_spoken_with_sophia", true);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "hiring Sophia failed", t);
            return;
        }
        float salary = Misc.getAdminSalary(p);
        CABudget.take(price);
        CABudget.charge(s, 0f, fee, salary);
        CALog.spent(null, price, 0f, 0f);
        d.adminTransit().put(p.getId(), Global.getSector().getClock().getTimestamp());
        CALog.did("Administrators: " + p.getNameString() + " hired from the United Aurora Federation for " + Misc.getDGSCredits(price)
                + (fee > 0 ? " + " + Misc.getDGSCredits(fee) + " fee" : "") + ", salary " + Misc.getDGSCredits(salary) + "/month. Arrives in 30 days.");
    }

    /** Daily (CAAuto): Sophia's hire. The restorations themselves are the planner's (offer). */
    public static void daily(CAData d, CASettings s) {
        try { hire(d, s); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Sophia's hire check failed", t); }
    }
}
