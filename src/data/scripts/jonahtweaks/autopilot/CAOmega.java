package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreAdminPlugin;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Omega cores, with Omega Core Everywhere (OCE) and/or Astral Ascension Renewed (AAR) installed (0.2.38.0). Without either,
 * an Omega core only cuts a building's upkeep by a quarter, so Autopilot leaves them alone and Autopilot Storage refuses them.
 *
 * - OCE: an Omega core in a building does what OCE's table says (omegacoreeverywhere.effects.Effect/OmegaEffects 1.2.2):
 *   anything that makes goods makes twice as much from half the inputs at twice the upkeep; Commerce and other income
 *   buildings double the colony's income; military buildings double fleet size; and so on. The Hegemony counts no
 *   points for a core in a building (HegemonyAICoresActivityCause only counts Alpha, Beta and Gamma), so it fits any
 *   colony's limit. An inspection still takes every core at the colony it lands on, which the usual limit prevents.
 * - AAR: an Omega core can govern a colony: every building there makes twice as much from half the inputs, +3
 *   stability, +1 industry slot, colony income x1.25 and upkeep x0.75, and only it lets the colony grow past size 10.
 *   As an administrator it counts 10 inspection points, so it only goes where your Inspection risk setting allows that
 *   (the same limit as every other core). Once a colony governed by one is past size 10, taking it away starts AAR's
 *   Great Civil War; Autopilot never changes an AI core administrator anyway (CAItems' administrators skip them, and
 *   autonomy never picks a colony an AI core governs).
 *
 * One action a day, from Autopilot Storage: an Omega core at the colony goes straight in; one elsewhere is shipped there
 * first (CAStores, a fee and travel time). Placed cores aren't moved again. Nothing from OCE or AAR is compiled against:
 * the mod ids, condition ids and the administrator plugin come through the game's own API.
 */
public class CAOmega {
    public static final String CORE = "omega_core";
    static final String OCE = "omega_core_everywhere", AAR = "astral_ascension_renewed";

    static boolean on(String mod) { return Global.getSettings().getModManager().isModEnabled(mod); }

    /** Either mod is installed: Autopilot Storage takes Omega cores. */
    public static boolean enabled() { return on(OCE) || on(AAR); }

    /** OCE with its building effects on (its LunaLib switch, read live; on when it can't be read). */
    static boolean buildingEffects() {
        if (!on(OCE)) return false;
        try {
            Class<?> c = Class.forName("omegacoreeverywhere.Settings", true, Global.getSettings().getScriptClassLoader());
            Object v = CARefl.invoke(CARefl.method(c, "effectsEnabled"), null);
            return !(v instanceof Boolean) || (Boolean) v;
        } catch (Throwable t) { return true; }
    }

    /** OCE's settings class still has effectsEnabled() (Diagnostics' integration check). */
    static boolean linkOce() {
        try {
            Class<?> c = Class.forName("omegacoreeverywhere.Settings", true, Global.getSettings().getScriptClassLoader());
            return CARefl.method(c, "effectsEnabled") != null;
        } catch (Throwable t) { return false; }
    }

    /** AAR's administrator plugin for Omega cores, or null. */
    static AICoreAdminPlugin adminPlugin() {
        if (!on(AAR)) return null;
        try { return Misc.getAICoreAdminPlugin(CORE); } catch (Throwable t) { return null; }
    }

    /** AAR: an Omega core governs this colony. */
    public static boolean governs(MarketAPI m) { return m.getAdmin() != null && CORE.equals(m.getAdmin().getAICoreId()); }

    /** AAR: past size 10 under an Omega administrator; removing it now starts the Great Civil War. */
    public static boolean ascended(MarketAPI m) {
        return m.getSize() > 10 && (m.getMemoryWithoutUpdate().getBoolean("$aar_ascended") || governs(m));
    }

    // ---------------- what a core is worth ----------------

    static float price(String com) {
        try { return Global.getSettings().getCommoditySpec(com).getBasePrice(); } catch (Throwable t) { return 0f; }
    }

    /** Credits a month for doubling what a building makes and halving what it needs (where the inputs are short). */
    static float doubled(Industry ind, Map<String, Integer> deficit) {
        float v = 0f;
        for (MutableCommodityQuantity q : ind.getAllSupply()) {
            int u = q.getQuantity().getModifiedInt();
            if (u <= 0) continue;
            Integer d = deficit.get(q.getCommodityId());
            v += price(q.getCommodityId()) * CAEngine.unitValue * u * (d != null && d > 0 ? 1.5f : 1f);
        }
        for (MutableCommodityQuantity q : ind.getAllDemand()) {
            int u = q.getQuantity().getModifiedInt();
            CommodityOnMarketAPI c = ind.getMarket().getCommodityData(q.getCommodityId());
            if (u <= 0 || c == null) continue;
            int short_ = Math.min(u / 2, Math.max(0, u - c.getAvailable()));   // only a shortage the halving closes counts
            v += price(q.getCommodityId()) * 30f * short_;
        }
        return v;
    }

    /** Monthly deliveries into the colony's storage, counted at half their base price (what they'd sell for). */
    static float delivered(int qty, String... coms) {
        float v = 0f;
        for (String c : coms) v += price(c) * qty * 0.5f;
        return v;
    }

    /**
     * An Omega core's effects in this building under OCE 1.2.2, in CAEngine's units (fx[0] credits a month, [1] stability,
     * [2] accessibility, [3] ground defense, [4] fleet, [5] quality, [6] growth), with the reason in why[0]. Null where it does
     * nothing but vanilla's -25% upkeep.
     */
    static float[] buildingFx(MarketAPI m, Industry ind, Map<String, Integer> deficit, String[] why) {
        float[] fx = new float[7];
        String id = ind.getId();
        float gross = Math.max(0f, m.getGrossIncome());
        if (id.equals("population") || id.equals("aotd_capital_complex")) {
            if (m.getSize() >= Misc.getMaxMarketSize(m)) return null;
            fx[6] += 2f; why[0] = "growth x2";
        } else if (id.equals("spaceport") || id.equals("megaport")) {
            fx[0] += delivered(1000, "crew"); why[0] = "1,000 crew a month into storage";
        } else if (id.equals("patrolhq") || id.equals("militarybase") || id.equals("highcommand") || id.equals("aotd_hexagon")) {
            fx[4] += 1f; why[0] = "fleet size x2";
        } else if (id.equals("grounddefenses") || id.equals("heavybatteries")) {
            fx[3] += 3f; fx[0] += delivered(500, "marines"); why[0] = "ground defense x10, 500 marines a month";
        } else if (id.equals("techmining")) {
            CAEngine.Pick p = CAEngine.effectsOf(m, "techmining", deficit);
            fx[0] += p == null ? 0f : Math.max(0f, p.fx[0]); why[0] = "tech-mining finds x2";
        } else if (id.equals("commerce") || id.equals("aotd_resort_center") || id.equals("aotd_trade_outpost") || id.equals("aotd_underworld")) {
            fx[0] += gross; why[0] = "colony income x2";
        } else if (id.equals("IndEvo_Museum")) {
            fx[0] += Math.max(0f, ind.getIncome().getModifiedValue()); why[0] = "museum income x2";
        } else if (id.equals("IndEvo_SupCom")) {
            float sys = 0f;
            for (MarketAPI o : Misc.getPlayerMarkets(false)) if (o.getStarSystem() != null && o.getStarSystem() == m.getStarSystem()) sys += Math.max(0f, o.getGrossIncome());
            fx[0] += 0.5f * sys; why[0] = "income x1.5 at every colony in the system";
        } else if (id.equals("waystation")) {
            fx[0] += delivered(1000, "fuel", "supplies", "heavy_machinery", "metals", "food"); why[0] = "1,000 fuel, supplies, heavy machinery, metals and food a month into storage";
        } else if (id.equals("planetaryshield")) {
            fx[3] += 0.5f; why[0] = "buildings recover from disruption at once";
        } else if (id.equals("aotd_terminus")) {
            fx[2] += 1f; why[0] = "accessibility topped up";
        } else if (id.equals("aotd_research_facility") || id.equals("aotd_blacksite")) {
            fx[0] += 10f * CAResearch.DATABANK_VALUE; why[0] = "research x4 and 10 databanks a month";
        } else if (id.equals("IndEvo_Academy")) {
            fx[0] += 20000f; why[0] = "officers to their top level, administrators learn Hypercognition";
        } else if (CAPlan.stationTier(ind.getSpec()) > 0) {
            fx[4] += 0.3f; why[0] = "station never disrupted, full combat readiness";
        } else {
            boolean makes = false;
            for (MutableCommodityQuantity q : ind.getAllSupply()) if (q.getQuantity().getModifiedInt() > 0) makes = true;
            if (!makes) return null;
            fx[0] += doubled(ind, deficit);
            // upkeep x2 instead of the x0.75 any other core gives
            float up = ind.getUpkeep().getModifiedValue();
            String cur = ind.getAICoreId();
            if ("alpha_core".equals(cur) || "beta_core".equals(cur)) up /= 0.75f;
            fx[0] -= up * 2f - ind.getUpkeep().getModifiedValue();
            why[0] = "output x2 from half the inputs, upkeep x2";
        }
        return fx;
    }

    /**
     * Whether OCE doubles this building's output (its general case), rather than giving a building of its kind something
     * else: fleet size for Patrol HQ and High Command, income for Commerce, and so on (0.2.39.0: mod watch expected doubled
     * output from those and warned).
     */
    static boolean doublesOutput(MarketAPI m, Industry ind) {
        String[] why = new String[1];
        try {
            float[] fx = buildingFx(m, ind, new HashMap<String, Integer>(), why);
            return fx != null && why[0] != null && why[0].startsWith("output x2");
        } catch (Throwable t) { return true; }
    }

    /** What AAR's Omega administrator adds at this colony, in CAEngine's units. */
    static float[] adminFx(MarketAPI m, Map<String, Integer> deficit) {
        float[] fx = new float[7];
        for (Industry ind : m.getIndustries()) if (!ind.isHidden() && ind.isFunctional()) fx[0] += doubled(ind, deficit);
        fx[0] += 0.25f * Math.max(0f, m.getGrossIncome()) + 0.25f * Math.max(0f, m.getIndustryUpkeep());   // Delphic Optimiser
        fx[0] += 20000f;   // +1 industry slot
        fx[1] += 3f; fx[2] += 0.5f; fx[3] += 2f; fx[4] += 1f; fx[5] += 0.25f;   // Acumenious Oracle, Ethereal Enlightenment
        if (m.getSize() >= 9) fx[6] += 2f;   // the only way past size 10: Arcology, Ecumenopolis, Hyper-Ecumenopolis
        return fx;
    }

    static float weigh(float[] w, float[] fx) { float v = 0f; for (int i = 0; i < 7; i++) v += w[i] * fx[i]; return v; }

    // ---------------- daily ----------------

    static class Spot {
        MarketAPI m; Industry ind; float value; String why; int replaced = -1;
    }

    /** Places one Omega core a day from Autopilot Storage, as an administrator (AAR) or in a building (OCE). Returns whether it acted. */
    static boolean run(CAData d, CASettings s, List<MarketAPI> markets) {
        if (!enabled()) return false;
        warnInspections(markets);
        if (CAStores.count(CORE) < 1f) return false;
        String none = CAItems.noInspections();
        Set<MarketAPI> frozen = CAItems.inspected();
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        Set<String> incoming = new HashSet<String>();
        for (CAStores.Shipment sh : d.shipments()) if (CORE.equals(sh.commodity)) incoming.add(sh.to);
        AICoreAdminPlugin admins = adminPlugin();
        boolean effects = buildingEffects();
        Spot best = null;
        for (MarketAPI m : markets) {
            if (frozen.contains(m) || incoming.contains(m.getId()) || CAEngine.isOutpost(m)) continue;
            CAData.Colony c = d.colony(m.getId());
            float[] w = CAItems.coreWeights(m, c);
            // 0.3.2.1: never at a Rogue Planets Trash King colony (he governs) or Pilgrim World (an AI core breaks its bonus)
            if (admins != null && (m.getAdmin() == null || !m.getAdmin().isAICore()) && !CARogue.adminLocked(m) && !CARogue.noCores(m)) {
                int pts = CAItems.aiPoints(m), lim = CAItems.coreLimit(s, m, none);
                float v = weigh(w, adminFx(m, deficit));
                if (pts + adminPoints() <= lim) {
                    if (best == null || v > best.value) { best = new Spot(); best.m = m; best.value = v; best.why = "governs it"; }
                } else if (v > 0f && CALog.once("omega:admin:limit:" + m.getId() + ":" + lim))
                    CALog.advise(m.getName() + ": an Omega core could govern it (Astral Ascension Renewed). As an administrator it counts "
                            + adminPoints() + " inspection points (" + CAItems.limitWhy(s, m, none, lim) + "). Put it in yourself to have it there.");
            }
            if (!effects || CARogue.noCores(m)) continue;   // 0.3.2.1: Pilgrim World
            for (Industry ind : m.getIndustries()) {
                try {
                    if (!ind.isFunctional() || ind.isHidden() || c.locks.contains(ind.getId()) || !ind.canInstallAICores()) continue;
                    String cur = ind.getAICoreId();
                    int tier = CAItems.tier(cur);
                    if (cur != null && tier < 0) continue;   // an Omega core or a mod's core is already in
                    String[] why = new String[1];
                    float[] fx = buildingFx(m, ind, deficit, why);
                    if (fx == null) continue;
                    float v = weigh(w, fx) - (tier >= 0 ? CAItems.coreValue(m, ind, tier, w, deficit, null) : 0f);
                    if (best == null || v > best.value) { best = new Spot(); best.m = m; best.ind = ind; best.value = v; best.why = why[0]; best.replaced = tier; }
                } catch (Throwable t) { CADiagnostics.quarantine(ind.getId(), "Omega core check", t); }
            }
        }
        if (best == null || best.value <= MIN_GAIN) return false;
        return place(d, s, best, admins);
    }

    /** An Omega core goes in only where it adds at least this much a month. */
    static final float MIN_GAIN = 5000f;

    static int adminPoints() {
        try { return Math.round(Global.getSettings().getFloat("hegemonyPointsAdmin")); } catch (Throwable t) { return 10; }
    }

    static boolean place(CAData d, CASettings s, Spot best, AICoreAdminPlugin admins) {
        CargoAPI here = CAStores.of(best.m);
        String target = best.ind == null ? "administrator" : best.ind.getCurrentName();
        if (here.getCommodityQuantity(CORE) < 1f) {
            MarketAPI holder = CAStores.nearestHolding(CORE, best.m);
            return holder != null && CAStores.ship(holder, best.m, CORE, null, 1, "Omega core for " + target);
        }
        float fee = CABudget.fee(s, CAItems.STANDARD_MOVE_FEE);
        if (!CAItems.canPay(s, fee)) return false;
        try {
            if (best.ind == null) {
                PersonAPI p = admins.createPerson(CORE, Factions.PLAYER, new Random().nextLong());
                if (p == null) return false;
                com.fs.starfarer.api.characters.AdminData was = CAItems.adminAt(best.m);
                if (was != null) was.setMarket(null);   // a hired administrator goes back to the pool and is placed elsewhere
                best.m.setAdmin(p);
                here.removeCommodity(CORE, 1f);
                CALog.did(best.m.getName() + ": Omega core administrator installed" + CAStores.from(best.m, CORE) + ". About +" + Misc.getDGSCredits(best.value)
                        + " a month." + (was != null ? " " + was.getPerson().getNameString() + " is free." : ""));
            } else {
                best.ind.setAICoreId(CORE);
                here.removeCommodity(CORE, 1f);
                if (best.replaced >= 0) here.addCommodity(CAItems.CORES[best.replaced], 1f);
                CALog.did(best.m.getName() + ": Omega core into " + best.ind.getCurrentName() + " (" + best.why + ", about +"
                        + Misc.getDGSCredits(best.value) + "/month)" + CAStores.from(best.m, CORE) + "."
                        + (best.replaced >= 0 ? " " + CAItems.coreName(best.replaced) + " to Autopilot Storage." : ""));
            }
            CABudget.charge(s, 0f, fee, 0f);
            return true;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "Omega core placement failed at " + best.m.getName(), t);
            return false;
        }
    }

    /** A Hegemony inspection coming for a colony an Omega core governs: complying or losing takes the administrator. */
    static void warnInspections(List<MarketAPI> markets) {
        Set<MarketAPI> coming = CAItems.inspected();
        for (MarketAPI m : markets) {
            if (!coming.contains(m) || !governs(m)) continue;
            if (CALog.once("omega:inspect:" + m.getId() + ":" + Global.getSector().getClock().getCycle()))
                CALog.advise(m.getName() + ": Hegemony inspection coming for its Omega core administrator. Complying, or losing a resistance, "
                        + "takes the administrator." + (ascended(m) ? " Past size 10 that starts Astral Ascension Renewed's Great Civil War (size 8, lasting stability loss)." : "")
                        + " Inspection orders: your decision.");
        }
    }
}
