package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionProductionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * The capital (2.42.0): your home port. One colony, yours to pick or Autopilot's, where everything you own ends up and
 * where you come back to trade.
 *
 * Deliveries: the game's production gathering point follows the capital, so custom production (ships, weapons, wings)
 * is delivered there, and storage logistics (CALogistics) sends the ships, weapons, wings and blueprints sitting in your
 * other Storage tabs there once a week, at its usual fee and travel time. Move the gathering point yourself and the
 * capital follows you.
 *
 * Commerce: once your colonies have ramped up (no fast buildout running at the capital, and last month's net income past
 * the line you pick), the capital's plan gets its commerce steps in front of everything else: a Spaceport and Megaport,
 * Commerce, every installed mod's building that lets you buy or sell there (COMMERCE), and a Waystation. They take
 * industry slots like any plan step (reserved ahead of other industries), earn their plan rank even where they cost more
 * than they make, and with every industry slot taken the capital may take down its weakest ordinary industry for one
 * (one every 90 days; never a defense, fleet or ship-making building, a mod building, or another plan's special step).
 * In a money emergency (net income below zero) the commerce steps wait.
 */
public class CACapital {
    /** capitalMode: 0 deliveries and commerce (default, also older saves), 1 deliveries only, 2 off. */
    public static final String[] MODE_NAMES = {"Deliveries and commerce", "Deliveries only", "Off"};
    /** capitalStart: when the commerce build-up starts. Index 0 is the recommended one, so older saves get it. */
    public static final String[] START_NAMES = {"Net income over 100,000", "Net income over 50,000", "Net income over 250,000", "Right away"};
    static final float[] START_NET = {100000f, 50000f, 250000f, -Float.MAX_VALUE};
    /** Days between two industries the capital takes down to make room for commerce. */
    static final float ROOM_DAYS = 90f;

    /**
     * Buildings that make the capital a place to trade, in the order they're wanted, from a scan of what each one does on a
     * player colony (2.42.0). A step whose building the game doesn't have is dropped (CAPlan.exists), and one that can't be
     * reached here (research, a blueprint, one per system) stops counting after 60 days (CAPlan.track). Column 4 "only":
     * nothing else scores it, so Autopilot builds it at the capital and nowhere else. Left out: Military Base and Free Port
     * (no market on a player colony), Interstellar Imperium's Bazaar (can't be built), Industrial Evolution's Animal
     * Center (pets) and the Senate's Open Market edict (Commerce already gives one).
     */
    static final String[][] COMMERCE = {
            // id, label, what you can do there, "only" when nothing else scores it
            {"commerce", "Commerce", "an Open Market to buy and sell at, and +25% income", ""},   // vanilla TradeCenter, IndEvo's too
            {"aotd_trade_outpost", "Trade Outpost", "a no-tariff market for legal goods, open the first 7 days of each month", ""},
            {"yunru_bazaar", "Bazaar", "an Open Market, plus a black market while it's a free port", "only"},
            {"IndEvo_ReqCenter", "Requisitions Centre", "weapons to buy from factions that like you", ""},
            {"roider_union_hq", "Union HQ", "the Roiders Market: military hardware, and it buys goods that are illegal elsewhere", ""},
            {"IndEvo_ScrapYard", "Salvage Yards", "ships built to order from scrap, and hulls scrapped for credit", ""},
            {"aotd_underworld", "Underworld", "a black market (needs a free port)", ""},
    };
    static final Map<String, String> WHAT = new HashMap<String, String>();
    static final Set<String> MOD_IDS = new HashSet<String>();
    static {
        for (String[] c : COMMERCE) { WHAT.put(c[0], c[2]); if ("only".equals(c[3])) MOD_IDS.add(c[0]); }
    }

    static String pickedId;
    static float pickedAt = -99f;
    /**
     * 2.42.2: true while the capital (yours or Autopilot's pick) is still a market but not yours (captured): a stand-in
     * colony takes production deliveries, nothing is gathered anywhere, and the capital is the old one again the day it's
     * yours. In 2.42.0 a captured Wind made Ming Adi the capital for good, and once Wind was retaken its whole Storage (853
     * weapons, 39 wings) was shipped to Ming Adi.
     */
    static boolean standIn = false;

    public static void reset() { pickedId = null; pickedAt = -99f; standIn = false; }

    static MarketAPI market(String id) { return id == null ? null : CAAuto.market(id); }

    /** A colony that can be the capital: yours, a real colony (not a Nexerelin outpost or a Coronal Hypershunt), with a Storage tab. */
    static boolean valid(MarketAPI m) {
        try {
            return m != null && m.isPlayerOwned() && !CAEngine.isOutpost(m) && !CAHypershunt.is(m) && m.getPrimaryEntity() != null
                    && Misc.getStorageCargo(m) != null;
        } catch (Throwable t) { return false; }
    }

    public static boolean on(CASettings s) { return s.capitalMode != 2; }

    /** The capital, or null (no colony yet, or the feature is off). Worked out once a day. */
    public static MarketAPI capital() {
        CASettings s = CAData.settings();
        if (!on(s)) return null;
        float now = CADay.now();
        if (now - pickedAt >= 1f || now < pickedAt) compute(s, now);
        return market(pickedId);
    }

    public static boolean is(MarketAPI m) {
        if (m == null) return false;
        MarketAPI c = capital();
        return c != null && c.getId().equals(m.getId());
    }

    static void compute(CASettings s, float now) {
        pickedAt = now;
        standIn = false;
        MarketAPI mine = market(s.capitalId);
        if (valid(mine)) { pickedId = mine.getId(); return; }
        MarketAPI last = market(s.capitalAuto);
        if (valid(last)) { pickedId = last.getId(); return; }   // Autopilot's pick stays put: moving home is your call
        // the capital still exists but isn't yours (captured): a stand-in for deliveries only, and the capital stays put
        boolean lost = (mine != null && mine.getPrimaryEntity() != null) || (s.capitalId == null && last != null && last.getPrimaryEntity() != null);
        MarketAPI best = null;
        float bestScore = -Float.MAX_VALUE;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!valid(m)) continue;
            float sc = score(m);
            if (sc > bestScore) { bestScore = sc; best = m; }
        }
        pickedId = best == null ? null : best.getId();
        if (lost) { standIn = best != null; return; }
        if (best != null) s.capitalAuto = best.getId();   // gone for good (decivilized, abandoned) or never picked: a new capital
    }

    /** The capital that's lost for now (captured), or null. */
    static MarketAPI lostCapital(CASettings s) {
        if (!standIn) return null;
        MarketAPI m = market(s.capitalId);
        return m != null ? m : market(s.capitalAuto);
    }

    /** Where your things already are counts most: the production gathering point, then what's in Storage; then size and reach. */
    static float score(MarketAPI m) {
        float sc = m.getSize() * 10f + Math.max(0f, m.getAccessibilityMod().computeEffective(0f)) * 20f
                + Math.max(0, CABuild.freeSlots(m)) * 5f;
        try {
            MarketAPI g = Global.getSector().getPlayerFaction().getProduction().getGatheringPoint();
            if (g != null && g.getId().equals(m.getId())) sc += 1000f;
        } catch (Throwable ignored) { }
        try {
            CargoAPI st = Misc.getStorageCargo(m);
            if (st != null) {
                sc += Math.min(200f, st.getStacksCopy().size() * 2f);
                if (st.getMothballedShips() != null) sc += Math.min(300f, st.getMothballedShips().getNumMembers() * 10f);
            }
        } catch (Throwable ignored) { }
        if (CAData.get().colony(m.getId()).mode == CAData.Mode.AUTO) sc += 20f;
        return sc;
    }

    // ---------------- daily ----------------

    public static void daily(CAData d, CASettings s) {
        MarketAPI cap = capital();
        if (cap == null) return;
        MarketAPI lost = lostCapital(s);
        if (lost != null) {
            if (CALog.once("capital:lost:" + lost.getId()))
                CALog.add(lost.getName() + ": capital: no longer yours. Production deliveries go to " + cap.getName() + " until it is again; "
                        + "nothing is gathered anywhere meanwhile, and it's the capital again the day you get it back", true);
            deliveries(s, cap);
            return;
        }
        if (CALog.once("capital:" + cap.getId()))
            CALog.add(cap.getName() + ": capital: your home port. Production deliveries come here" + (gathers(s) ? ", your other Storage gathers here weekly" : "")
                    + (s.capitalMode == 0 ? ", and once your colonies have ramped up it builds itself into a trade hub" : "")
                    + (cap.getId().equals(s.capitalId) ? " (your pick)" : ""));
        deliveries(s, cap);
        cap = capital();   // the gathering point may have moved it
        if (cap == null || s.capitalMode != 0 || s.capitalStarted) return;
        CAData.Colony c = d.colony(cap.getId());
        float line = START_NET[Math.max(0, Math.min(START_NET.length - 1, s.capitalStart))];
        boolean ramped = !c.buildout && (line == -Float.MAX_VALUE || (s.lastNetKnown && s.lastNet >= line));
        if (!ramped) return;
        s.capitalStarted = true;
        List<String> left = stepLabels(cap);
        CALog.add(cap.getName() + ": capital: commerce build-up starts (" + (line == -Float.MAX_VALUE ? "right away" : "net income "
                + Misc.getDGSCredits(s.lastNet) + " a month") + "): " + (left.isEmpty() ? "every trade building is already here"
                : CAContracts.join(left) + " go first"), true);
    }

    /** The gathering point follows the capital; if you moved it to another colony yourself, the capital follows you instead. */
    static void deliveries(CASettings s, MarketAPI cap) {
        FactionProductionAPI prod;
        try { prod = Global.getSector().getPlayerFaction().getProduction(); } catch (Throwable t) { return; }
        if (prod == null) return;
        MarketAPI g = prod.getGatheringPoint();
        if (g != null && s.capitalGatherSet != null && !g.getId().equals(s.capitalGatherSet) && !g.getId().equals(cap.getId()) && valid(g)) {
            s.capitalId = g.getId();
            s.capitalGatherSet = g.getId();
            pickedAt = -99f;
            CALog.add(g.getName() + ": capital: you moved production deliveries here, so this is the capital now", true);
            return;
        }
        if (g != null && g.getId().equals(cap.getId())) { s.capitalGatherSet = cap.getId(); return; }
        try {
            prod.setGatheringPoint(cap);
            s.capitalGatherSet = cap.getId();
            CALog.add(cap.getName() + ": capital: production deliveries now come here" + (g != null ? " (were going to " + g.getName() + ")" : ""));
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "couldn't move the production gathering point to " + cap.getName(), t); }
    }

    /** Whether storage logistics runs for the capital even with Storage logistics itself off: never while a stand-in has it. */
    public static boolean gathers(CASettings s) { return on(s) && !s.capitalNoGather && capital() != null && !standIn; }

    /** Where the capital's Storage gathering sends things: the capital, or null (off, none, or a stand-in). */
    public static MarketAPI gatherTarget(CASettings s) { return gathers(s) ? capital() : null; }

    /** Below this stability the capital's trade buildings wait: most of them cost stability, and at 0 a colony can decivilize. */
    static final float COMMERCE_MIN_STABILITY = 3f;

    /** Whether the commerce build-up is running now: started, and not in a money emergency. */
    public static boolean commerce(CASettings s) {
        return s.capitalMode == 0 && s.capitalStarted && !standIn && !(s.lastNetKnown && s.lastNet < 0f);
    }

    // ---------------- the plan ----------------

    static List<String[]> wanted() {
        List<String[]> out = new ArrayList<String[]>();
        out.add(new String[]{"spaceport", "Spaceport", "traders can dock"});
        out.add(new String[]{"megaport", "Megaport", "+30% accessibility"});
        out.addAll(Arrays.asList(COMMERCE));
        out.add(new String[]{"waystation", "Waystation", "fuel and supplies stockpiled at home"});
        return out;
    }

    /** The capital's commerce steps, put in front of its own plan (CAPlan.build) while the build-up runs. */
    public static List<CAPlan.Step> stepsFor(MarketAPI m) {
        List<CAPlan.Step> out = new ArrayList<CAPlan.Step>();
        if (m == null || !commerce(CAData.settings()) || !is(m)) return out;
        if (m.getStabilityValue() < COMMERCE_MIN_STABILITY) {   // 2.42.2: Commerce and the Bazaars cost stability
            if (CALog.once("capital:lowstab:" + m.getId()))
                CALog.add(m.getName() + ": capital: trade buildings wait while stability is under " + Math.round(COMMERCE_MIN_STABILITY) + " (most of them cost stability)");
            return out;
        }
        CALog.forget("capital:lowstab:" + m.getId());
        for (String[] w : wanted()) {
            if (CAOverLimit.shedRecently(m, w[0])) continue;   // taken down at 0 stability this year: not built again yet
            out.add(new CAPlan.Step(CAPlan.Kind.BUILD, w[0], w[1] + " (capital)"));
        }
        return out;
    }

    static List<String> stepLabels(MarketAPI m) {
        List<String> out = new ArrayList<String>();
        for (String[] w : wanted()) if (CAPlan.spec(w[0]) != null && !CAPlan.hasOrBetter(m, w[0])) out.add(w[1]);
        return out;
    }

    /** A commerce step at the capital, or what leads to one (Ashes of the Domain's Trade Outpost before Commerce). */
    public static boolean isStep(MarketAPI m, String id) {
        if (id == null || m == null || !commerce(CAData.settings()) || !is(m)) return false;
        for (String[] w : wanted()) if (w[0].equals(id) || CAPlan.leadsTo(id, w[0])) return true;
        return false;
    }

    // ---------------- mod buildings Autopilot only knows as trade buildings ----------------

    /** A mod building in COMMERCE that no other part of the engine scores. */
    public static boolean has(String id) { return MOD_IDS.contains(id) && !CAEstimates.has(id) && !CAMods.has(id); }

    /** Its effect: a place to trade at the capital, worth its plan rank there; elsewhere Autopilot doesn't build it. */
    static boolean apply(MarketAPI m, String id, CAEngine.Pick p, Map<String, Integer> deficit) {
        if (!is(m) || !commerce(CAData.settings())) return false;
        String what = WHAT.get(id);
        p.lines.add("Capital trade building" + (what != null ? ": " + what : "") + ". Its value is having a place to trade at home, so it counts as a capital plan step");
        return true;
    }

    // ---------------- making room ----------------

    /** At the capital with every industry slot taken: its weakest ordinary industry, if the commerce pick beats it. */
    static CAEndgame.Weakest replacement(MarketAPI m, CAData.Colony col, CAEngine.Pick best, CAData.Profile profile) {
        CASettings s = CAData.settings();
        if (best == null || s.capitalNoRoom || !isStep(m, best.id) || col.mode != CAData.Mode.AUTO) return null;
        Long last = CAData.get().endgameBuilt().get(m.getId() + ":replaced");
        if (last != null && Global.getSector().getClock().getElapsedDaysSince(last) < ROOM_DAYS) return null;
        CAPlan.Plan plan = CAPlan.build(m, col.profile);
        CAData d = CAData.get();
        float[] w = CAEngine.WEIGHTS[profile.ordinal()];
        Map<String, Integer> deficit = CAEngine.empireDeficits();
        CAEndgame.Weakest out = null;
        for (Industry ind : m.getIndustries()) {
            if (CAEndgame.protectedHere(m, col, plan, ind, d) || isStep(m, ind.getId())) continue;
            CAEngine.Pick p = CAEngine.effectsOf(m, ind.getId(), deficit);
            if (p == null) continue;
            // the home port keeps its defenses, fleets and what builds your ships
            if (p.fx[3] > 0f || p.fx[4] > 0f || p.fx[5] > 0f || p.supplied.containsKey("ships")) continue;
            CAEngine.upkeep(m, ind.getSpec(), null, p);
            float v = 0f;
            for (int i = 0; i < 7; i++) v += w[i] * p.fx[i];
            if (out == null || v < out.keep) { out = new CAEndgame.Weakest(); out.ind = ind; out.keep = v; out.money = p.fx[0]; out.upkeep = p.upkeep; }
        }
        if (out == null || !CAEndgame.beats(best.score, out.keep)) return null;
        return out;
    }

    // ---------------- panels ----------------

    static final String SET = "ca:capital:set", AUTO = "ca:capital:auto";

    static int doneSteps(MarketAPI m) {
        int n = 0;
        for (String[] w : wanted()) if (CAPlan.spec(w[0]) != null && CAPlan.hasOrBetter(m, w[0])) n++;
        return n;
    }

    static int allSteps() {
        int n = 0;
        for (String[] w : wanted()) if (CAPlan.spec(w[0]) != null) n++;
        return n;
    }

    static String status(CASettings s, MarketAPI cap) {
        if (s.capitalMode == 1) return "deliveries only";
        if (commerce(s)) return "commerce " + doneSteps(cap) + " of " + allSteps() + " built";
        if (s.capitalStarted) return "commerce waits (net income below zero)";
        return "commerce starts at " + START_NAMES[Math.max(0, Math.min(START_NAMES.length - 1, s.capitalStart))].toLowerCase(Locale.ROOT);
    }

    /** The empire panel's line. */
    public static void renderEmpire(TooltipMakerAPI t, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        if (!on(s)) { t.addPara("Capital: off (Settings, Capital).", gray, 4f); return; }
        MarketAPI cap = capital();
        if (cap == null) { t.addPara("Capital: none yet (no colony with a Storage tab).", gray, 4f); return; }
        t.addPara("Capital: %s" + (cap.getId().equals(s.capitalId) ? " (your pick)" : "") + ". Production deliveries come here"
                + (gathers(s) ? ", other Storage gathers here weekly" : "") + "; " + CALog.esc(status(s, cap)) + ".", 4f, hl, cap.getName());
    }

    /** The colony panel's section. */
    public static void renderColony(TooltipMakerAPI t, MarketAPI m, CASettings s, float pad) {
        if (!on(s) || !valid(m)) return;
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        CATheme.heading(t, "Capital", pad);
        MarketAPI cap = capital();
        boolean here = cap != null && cap.getId().equals(m.getId()), mine = m.getId().equals(s.capitalId);
        if (here) {
            t.addPara("This is your capital" + (mine ? " (your pick)" : " (Autopilot's pick)") + ": production deliveries come here"
                    + (gathers(s) ? " and your other Storage gathers here weekly" : "") + ". " + CALog.esc(Character.toUpperCase(status(s, cap).charAt(0)) + status(s, cap).substring(1)) + ".", 6f);
            if (s.capitalMode == 0) {
                List<String> left = stepLabels(m);
                if (!left.isEmpty()) t.addPara("Still to build here: " + CALog.esc(CAContracts.join(left)) + ".", gray, 2f);
            }
        } else {
            t.addPara("Your capital is %s. Making this one the capital moves production deliveries and your gathered Storage here.", 6f, hl,
                    cap == null ? "not set" : cap.getName());
        }
        if (mine) t.addButton("Let Autopilot choose", AUTO, base, dark, Alignment.MID, CutStyle.TL_BR, 220f, 24f, 6f);
        else if (!here) t.addButton("Make this the capital", SET, base, dark, Alignment.MID, CutStyle.TL_BR, 220f, 24f, 6f);
    }

    /** A colony panel button; returns a message, or null if the id isn't one of these. */
    public static String handle(MarketAPI m, Object id) {
        CASettings s = CAData.settings();
        if (SET.equals(id)) { s.capitalId = m.getId(); s.capitalAuto = m.getId(); pickedAt = -99f; return m.getName() + " is the capital"; }
        if (AUTO.equals(id)) { s.capitalId = null; pickedAt = -99f; return "Autopilot keeps " + m.getName() + " as the capital until you pick another"; }
        return null;
    }

    /** Settings: the next capital choice, Autopilot's pick (null) then each colony. */
    public static String next(String cur) {
        List<String> order = new ArrayList<String>();
        order.add(null);
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (valid(m)) order.add(m.getId());
        int i = order.indexOf(cur);
        return order.get((i + 1) % order.size());
    }

    public static String name(CASettings s) {
        MarketAPI m = market(s.capitalId);
        if (m != null) return m.getName();
        MarketAPI c = on(s) ? capital() : null;
        return "Autopilot picks" + (c != null ? " (" + c.getName() + ")" : "");
    }
}
