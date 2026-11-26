package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 0.3.0.0 (WS-F2, research 06 section 4.13): the War page of the empire panel. A Nexerelin integration class like CAWar:
 * CAIntel lists the page only with Nexerelin on and checks isModEnabled again before drawing.
 *
 * Top: the War level and what it adds, the caps (Low profile, a commission), fleet request points and the share kept for
 * emergencies, infamy against the coalition line, where the money comes from, today's pass (CAWar.summary), the buttons
 * Stand down, Recall all and Authorize bombardment, and the War settings at a glance.
 * Then one block per faction at Rival or higher (Pick rivals automatically included) or with operations or attackers of
 * its own: CAWar.renderFaction (stance buttons, relation, Declare war or why not, operations under way), then up to
 * MAX_PLANNED lines with what Auto Colonies plans at that faction and why it waits (planned). The full stance table is
 * the Factions page. Then conquered colonies (CAConquest.render) and the markets marked for strikes back.
 *
 * Buttons are CAWar's {"war", action, arg} (CAWar.handle); Declare war, Authorize bombardment, Recall all and Stand down ask
 * first (CAIntel.doesButtonHaveConfirmDialog, CAWar.needsConfirm). Drawing launches, pays and changes nothing: the
 * planned lines use the same rules as the daily pass (CAConquest.targets and gate, CAWarPolicy.bombWait) on today's state.
 */
public final class CAWarPage {
    /** Planned lines per faction. */
    static final int MAX_PLANNED = 3;

    private CAWarPage() { }

    /** What each War level adds, one line. */
    static String levelWhat(int level, boolean retake) {
        switch (CAWarPolicy.clamp(level)) {
            case CAWarPolicy.OFF: return "It doesn't send war fleets.";
            case CAWarPolicy.DEFEND: return "It sends siege relief and defense fleets.";
            case CAWarPolicy.RETALIATE: return "It also raids attackers" + (retake ? " and retakes lost colonies." : ".");
            case CAWarPolicy.HOLD: return "It also raids and blockades Rival and up.";
            case CAWarPolicy.CONQUER: return "It also invades Enemy and Destroy.";
            default: return "It also bombards Destroy capitals, once authorized.";
        }
    }

    // ---------------- 0.3.27.0: goals (one per faction) ----------------

    /** What you want done to a faction; each is a stance (CAStance) and the War level it needs. */
    static final String[] GOALS = {"Leave alone", "Strike back", "Hold down", "Conquer", "Obliterate"};
    static final int[] GOAL_STANCE = {CAStance.NEUTRAL, CAStance.AUTO, CAStance.RIVAL, CAStance.ENEMY, CAStance.DESTROY};
    static final int[] GOAL_LEVEL = {CAWarPolicy.DEFEND, CAWarPolicy.RETALIATE, CAWarPolicy.HOLD, CAWarPolicy.CONQUER, CAWarPolicy.SCORCHED};
    /** One line each: what the goal does. */
    static final String[] GOAL_WHAT = {"No attacks; your fleets only defend.", "Raids back at whoever attacks you.",
            "Raids and blockades to keep them weak.", "Invades their colonies to take them.", "Invades, then bombards their capital once you authorize it."};

    /** Pure: the goal a stance stands for (Ally, Friendly and Neutral are Leave alone). */
    static int goalOf(int stance) {
        int st = CAStance.clamp(stance);
        if (st <= CAStance.NEUTRAL) return 0;
        if (st == CAStance.AUTO) return 1;
        if (st == CAStance.RIVAL) return 2;
        if (st == CAStance.ENEMY) return 3;
        return 4;
    }

    /** Pure: the War level after picking a goal: raised to what the goal needs, never lowered. */
    static int levelAfter(int level, int goal) {
        int g = Math.max(0, Math.min(GOALS.length - 1, goal));
        return g == 0 ? CAWarPolicy.clamp(level) : Math.max(CAWarPolicy.clamp(level), GOAL_LEVEL[g]);
    }

    // ---------------- the page ----------------

    static ButtonAPI btn(TooltipMakerAPI t, String label, String[] id, boolean on, float w, float pad) {
        return t.addButton(label, id, on ? Misc.getBasePlayerColor() : Misc.getGrayColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, w, 22f, pad);
    }

    /** Buttons in rows of perRow, the one at index on lit. */
    static void choices(TooltipMakerAPI t, String[] labels, String action, String argPrefix, int on, float colW, int perRow, float pad) {
        float w = (colW - 4f * (perRow - 1)) / perRow;
        ButtonAPI first = null, prev = null;
        for (int i = 0; i < labels.length; i++) {
            boolean row = i % perRow == 0;
            ButtonAPI b = btn(t, labels[i], new String[]{"war", action, argPrefix + i}, i == on, w, row ? (i == 0 ? pad : 4f) : 0f);
            if (!row) b.getPosition().rightOfTop(prev, 4f);
            else { if (first != null) CALog.endRow(t, first); first = b; }
            prev = b;
        }
        if (first != null) CALog.endRow(t, first);
    }

    /** The page: your war on the left, every faction with its goal on the right. Each part catches its own failure. */
    static void render(TooltipMakerAPI t, CAData d, CASettings s, float width) {
        float gap = 12f, colW = (width - gap) / 2f;
        TooltipMakerAPI L = t.beginSubTooltip(colW);
        try { left(L, d, s, colW); }
        catch (Throwable e) { failed(L, "war page settings", e); }
        t.endSubTooltip();
        TooltipMakerAPI R = t.beginSubTooltip(colW);
        try { right(R, d, s, colW); }
        catch (Throwable e) { failed(R, "war page factions", e); }
        t.endSubTooltip();
        float h0 = t.getHeightSoFar();
        com.fs.starfarer.api.ui.UIComponentAPI lc = t.addCustom(L, 0f), rc = t.addCustom(R, 0f);
        rc.getPosition().rightOfTop(lc, gap);
        CALog.endRow(t, L.getHeightSoFar() >= R.getHeightSoFar() ? lc : rc, 4f);
        t.setHeightSoFar(h0 + Math.max(L.getHeightSoFar(), R.getHeightSoFar()) + 4f);
        try { CAConquest.render(t, d, s); }
        catch (Throwable e) { failed(t, "war page conquered colonies", e); }
        try { attackers(t, d); }
        catch (Throwable e) { failed(t, "war page attackers", e); }
    }

    static void failed(TooltipMakerAPI t, String what, Throwable e) {
        CADiagnostics.LOG.warn(CADiagnostics.TAG + what + " failed", e);
        t.addPara(CATheme.NOT_SHOWN, Misc.getGrayColor(), 4f);
    }

    /** Your war: the most you allow, the switches, the buttons, then points, infamy and money. */
    static void left(TooltipMakerAPI t, CAData d, CASettings s, float colW) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor();
        int level = CAWarPolicy.clamp(s.warLevel);
        t.addSectionHeading("Your war", Alignment.MID, 0f);
        t.addPara("The most you allow. Picking a goal on the right raises it when the goal needs more.", gray, 6f);
        choices(t, CAWarPolicy.LEVEL_NAMES, "level", "", level, colW, 3, 4f);
        t.addPara(levelWhat(level, s.warRetake()), hl, 4f);   // plain text: shown as is
        if (CALowProfile.on() && level > CAWarPolicy.DEFEND) t.addPara("Low profile holds everyone at Defend.", bad, 4f);
        FactionAPI comm = Misc.getCommissionFaction();
        if (comm != null && level > CAWarPolicy.DEFEND) t.addPara("Your commission from %s holds you at Hold them down.", 4f, bad, comm.getDisplayName());

        ButtonAPI a = btn(t, "Retake lost colonies: " + onOff(s.warRetake()), new String[]{"war", "toggle", "retake"}, s.warRetake(), (colW - 4f) / 2f, 10f);
        ButtonAPI b = btn(t, "Pick a rival for me: " + onOff(s.warAutoRivals), new String[]{"war", "toggle", "rivals"}, s.warAutoRivals, (colW - 4f) / 2f, 0f);
        b.getPosition().rightOfTop(a, 4f);
        CALog.endRow(t, a);
        ButtonAPI c = btn(t, "Past the coalition line: " + onOff(s.warCoalitionOK), new String[]{"war", "toggle", "coalition"}, s.warCoalitionOK, (colW - 4f) / 2f, 4f);
        ButtonAPI bomb = s.warScorchedOK ? btn(t, "Bombardment: Allowed", new String[]{"war", "toggle", "unbomb"}, true, (colW - 4f) / 2f, 0f)
                : btn(t, "Allow bombardment", new String[]{"war", "scorched", ""}, false, (colW - 4f) / 2f, 0f);
        bomb.getPosition().rightOfTop(c, 4f);
        CALog.endRow(t, c);
        ButtonAPI stop = btn(t, "Stand down", new String[]{"war", "standdown", ""}, true, (colW - 4f) / 2f, 4f);
        ButtonAPI rec = btn(t, "Recall all", new String[]{"war", "recall", ""}, true, (colW - 4f) / 2f, 0f);
        rec.getPosition().rightOfTop(stop, 4f);
        CALog.endRow(t, stop);

        t.addSectionHeading("Where it stands", Alignment.MID, 12f);
        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        boolean rich = CASurplus.rich(s);
        if (mgr != null) {
            int cap = mgr.getFleetRequestCapacity();
            float share = rich ? CAWar.RICH_RESERVE : CAWar.RESERVE;
            t.addPara("Fleet points: %s of %s, and %s are kept back for emergencies.", 6f, hl,
                    String.valueOf((int) mgr.getFleetRequestStock()), String.valueOf(cap), String.valueOf(Math.round(share * cap)));
        }
        float badboy = CAConquest.badboy(), dom = CAConquest.dominance();
        float pend = CAConquest.pendingInfamy() + CAScorched.pendingInfamy();
        float now = CAWarPolicy.infamy(badboy, dom), after = CAWarPolicy.infamy(badboy + pend, dom);
        t.addPara("Infamy: %s of %s" + (pend > 0f ? ", %s once what's under way lands." : "."), 4f, after >= CAWarPolicy.INFAMY_NEAR ? bad : hl,
                String.valueOf(Math.round(now)), String.valueOf(Math.round(CAWarPolicy.INFAMY_LINE)), String.valueOf(Math.round(after)));
        t.addTooltipToPrevious(CAFactionsPage.tip(new String[]{"A coalition forms against you at 100 infamy and ends under 75. Infamy drops 3 a month."}),
                TooltipMakerAPI.TooltipLocation.BELOW);
        t.addPara("Money: %s left in the budget and %s surplus. Raids and retakes come out of the budget, invasions and bombardment out of the surplus.",
                4f, hl, Misc.getDGSCredits(CABudget.available(s)), Misc.getDGSCredits(CASurplus.amount(s)));
        if (CAWar.summary != null) t.addPara("Today: " + CAWar.summary, gray, 4f);   // shown as is
    }

    /** Every faction with markets you can see, the ones you're fighting first, each with its goal buttons and plans. */
    static void right(TooltipMakerAPI t, CAData d, CASettings s, float colW) {
        Color gray = Misc.getGrayColor(), hl = Misc.getHighlightColor();
        final String rival = CAWar.autoRival(d, s);
        t.addSectionHeading("Factions", Alignment.MID, 0f);
        t.addPara("Pick what you want done to each faction.", gray, 6f);
        List<FactionAPI> list = new ArrayList<FactionAPI>(CAWar.factions());
        Set<String> ids = new HashSet<String>();
        for (FactionAPI f : list) ids.add(f.getId());
        for (FactionAPI f : shown(d, s, rival)) if (ids.add(f.getId())) list.add(f);
        Collections.sort(list, new Comparator<FactionAPI>() {
            public int compare(FactionAPI x, FactionAPI y) {
                int c = Integer.compare(CAWar.stance(s, y.getId(), rival), CAWar.stance(s, x.getId(), rival));
                if (c != 0) return c;
                c = Boolean.compare(y.isHostileTo(Factions.PLAYER), x.isHostileTo(Factions.PLAYER));
                return c != 0 ? c : x.getDisplayName().compareTo(y.getDisplayName());
            }
        });
        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        CAWar.Pass p = mgr == null ? null : new CAWar.Pass(d, s, mgr, CASurplus.rich(s));
        for (FactionAPI f : list) {
            try { faction(t, d, s, f, rival, p, colW); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "war page faction failed for " + f.getId(), e); }
        }
    }

    static void faction(TooltipMakerAPI t, CAData d, CASettings s, FactionAPI f, String rival, CAWar.Pass p, float colW) {
        Color gray = Misc.getGrayColor(), hl = Misc.getHighlightColor(), bad = Misc.getNegativeHighlightColor();
        String fid = f.getId();
        int st = CAWar.stance(s, fid, rival), goal = goalOf(st);
        boolean hostile = f.isHostileTo(Factions.PLAYER);
        int markets = 0;
        for (MarketAPI m : Misc.getFactionMarkets(f)) if (!m.isHidden() && m.isInEconomy()) markets++;
        String state = (hostile ? "at war with you" : f.getRelationshipLevel(Factions.PLAYER).getDisplayName().toLowerCase(java.util.Locale.ROOT))
                + ", " + markets + (markets == 1 ? " colony" : " colonies") + (st != s.stanceOf(fid) ? ", picked as your rival" : "");
        t.addPara("%s, " + CALog.esc(state) + ".", 10f, new Color[]{f.getBaseUIColor(), gray}, f.getDisplayName());
        choices(t, GOALS, "goal", fid + ":", goal, colW, GOALS.length, 4f);
        int eff = CAWar.effective(d, s, f, rival), cap = CAStance.cap(st);
        if (goal > 0 && eff < cap) t.addPara("Held at " + CAWarPolicy.levelName(eff) + ": " + limitWhy(s, f, st) + ".", bad, 2f);   // shown as is
        if (CAStance.wantsWar(st) && !hostile) {
            String why = CADiplomacy.declareWhy(d, s, f);
            if (why == null) t.addButton("Declare war on " + f.getDisplayName(), new String[]{"war", "declare", fid}, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.TL_BR, colW, 20f, 2f);
            else t.addPara("Not at war yet: " + why + ".", gray, 2f);   // shown as is
        }
        if (p != null && goal > 0 && hostile) {
            int n = 0;
            for (String line : planned(p, f)) { if (n++ >= 2) break; t.addPara(line, gray, 2f); }   // shown as is
        }
    }

    static String onOff(boolean on) { return on ? "On" : "Off"; }

    // ---------------- factions ----------------

    /** The faction's stance with Pick rivals automatically (for CAFactionsPage, which has no Nexerelin classes). */
    static String autoRival(CAData d, CASettings s) { return CAWar.autoRival(d, s); }

    /**
     * For a row of the Factions page: the War level the stance allows against this faction now ("war: Conquer"), or why it
     * isn't attacked; null when there's nothing to add (a faction at Auto or below that isn't hostile).
     */
    static String attacks(CAData d, CASettings s, FactionAPI f, String rival) {
        int st = CAWar.stance(s, f.getId(), rival);
        String picked = st != s.stanceOf(f.getId()) ? "picked as Rival, " : "";
        if (st <= CAStance.NEUTRAL) return "not attacked";   // 0.3.8.6: plain wording
        if (!f.isHostileTo(Factions.PLAYER)) {
            if (!CAStance.wantsWar(st)) return picked.isEmpty() ? null : picked + "not hostile";
            return d.warAuthorized().contains(f.getId()) ? "war confirmed" : "not at war: Declare war on the War page";
        }
        int eff = CAWar.effective(d, s, f, rival);
        if (eff <= CAWarPolicy.DEFEND) return picked + "no attacks (" + limitWhy(s, f, st) + ")";
        return picked + "war: " + CAWarPolicy.levelName(eff);
    }

    /** Why a hostile faction is held at Defend: the level, an alliance, a ceasefire, your commissioner, Low profile or the stance. */
    static String limitWhy(CASettings s, FactionAPI f, int st) {
        int level = CAWarPolicy.clamp(s.warLevel);
        if (level <= CAWarPolicy.DEFEND) return "War level " + CAWarPolicy.levelName(level);
        if (CAWar.allied(f)) return "allied with you";
        if (CAWar.ceasefire(f)) return "a ceasefire is in force";
        if (CAWar.commissionerSide(f)) return "your commissioner's side";
        if (CALowProfile.on()) return "Low profile";
        if (CAStance.cap(st) <= CAWarPolicy.DEFEND) return CAStance.name(st) + " stance";
        return "War level " + CAWarPolicy.levelName(level);
    }

    /**
     * Factions with a block on this page: Rival and up (the automatic rival too), or with an operation of yours at their
     * markets, or with a market marked for strike back. Destroy first, then by name.
     */
    static List<FactionAPI> shown(final CAData d, final CASettings s, final String rival) {
        Set<String> ids = new HashSet<String>();
        List<FactionAPI> out = new ArrayList<FactionAPI>();
        for (FactionAPI f : CAWar.factions()) if (CAWar.stance(s, f.getId(), rival) >= CAStance.RIVAL && ids.add(f.getId())) out.add(f);
        for (String fid : s.factionStance().keySet()) {   // an eliminated Rival, Enemy or Destroy faction stays listed
            FactionAPI f = Global.getSector().getFaction(fid);
            if (f != null && !f.isPlayerFaction() && s.stanceOf(fid) >= CAStance.RIVAL && ids.add(fid)) out.add(f);
        }
        for (OffensiveFleetIntel o : CAWar.offensives()) {
            if (o instanceof DefenseFleetIntel || !CAWar.yours(o) || o.getTarget() == null) continue;
            FactionAPI f = o.getTarget().getFaction();
            if (f != null && !f.isPlayerFaction() && ids.add(f.getId())) out.add(f);
        }
        for (String id : d.attackers().keySet()) {
            MarketAPI m = CAWar.attacker(id);   // 0.3.9.0: the attackers key holds the faction that attacked
            FactionAPI f = m == null ? null : m.getFaction();
            if (f != null && !f.isPlayerFaction() && f.isHostileTo(Factions.PLAYER) && ids.add(f.getId())) out.add(f);
        }
        Collections.sort(out, new Comparator<FactionAPI>() {
            public int compare(FactionAPI a, FactionAPI b) {
                int c = Integer.compare(CAWar.stance(s, b.getId(), rival), CAWar.stance(s, a.getId(), rival));
                return c != 0 ? c : a.getDisplayName().compareTo(b.getDisplayName());
            }
        });
        return out;
    }

    static void factions(TooltipMakerAPI t, CAData d, CASettings s) {
        Color gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        String rival = CAWar.autoRival(d, s);
        List<FactionAPI> list = shown(d, s, rival);
        CATheme.heading(t, "Rival and hostile factions", 12f);
        if (list.isEmpty()) t.addPara("No faction at Rival or up or at war.", gray, 6f);
        else t.addPara("Rival and up, and factions at war with you.", gray, 6f);
        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        CAWar.Pass p = mgr == null ? null : new CAWar.Pass(d, s, mgr, CASurplus.rich(s));
        for (FactionAPI f : list) {
            try { CAWar.renderFaction(t, d, s, f, rival); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "war page faction failed for " + f.getId(), e); continue; }
            if (p == null) continue;
            try { for (String line : planned(p, f)) t.addPara("  " + line, gray, 2f); }   // 0.3.8.6: shown as is
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "war page plan failed for " + f.getId(), e); }
        }
        ButtonAPI open = t.addButton("OPEN", CANav.PREFIX + CAFactionsPage.PAGE, base, dark, Alignment.MID, CutStyle.TL_BR, 86f, 20f, 10f);
        LabelAPI l = t.addPara("The Factions page has every stance.", gray, 0f);
        l.getPosition().rightOfMid(open, 12f);
        CALog.endRow(t, open);
    }

    /**
     * What Auto Colonies plans at this faction today, up to MAX_PLANNED lines, biggest operation first: bombardment of the
     * capital (Destroy at Scorched), the best invasion (Enemy and Destroy at Conquer), the nearest raid or blockade (Rival
     * and up at Hold them down), retakes and strikes back (Retaliate). Each with what it waits for, by the daily pass's own
     * checks. The daily pass shares one launch a day (two while rich) among every faction, in its own order.
     */
    static List<String> planned(CAWar.Pass p, FactionAPI f) {
        List<String> out = new ArrayList<String>();
        int st = p.stance(f), eff = p.eff(f);
        if (st <= CAStance.NEUTRAL) return out;   // never attacked; CAWar.renderFaction says what it allows
        if (!f.isHostileTo(Factions.PLAYER)) return out;   // Enemy and Destroy: Declare war or why not, from renderFaction
        if (eff <= CAWarPolicy.DEFEND) { out.add("No attacks: " + limitWhy(p.s, f, st) + "."); return out; }
        if (eff >= CAWarPolicy.SCORCHED && st >= CAStance.DESTROY) bombardment(p, f, out);
        if (eff >= CAWarPolicy.CONQUER && st >= CAStance.ENEMY && out.size() < MAX_PLANNED) invasion(p, f, st, out);
        if (eff >= CAWarPolicy.HOLD && st >= CAStance.RIVAL && out.size() < MAX_PLANNED) holdDown(p, f, out);
        if (out.size() < MAX_PLANNED) retaliate(p, f, out);
        if (out.isEmpty()) out.add("Nothing planned today.");
        return out;
    }

    static void bombardment(CAWar.Pass p, FactionAPI f, List<String> out) {
        MarketAPI cap = CAScorched.capital(f);
        if (cap == null || CAWar.oursAt(cap)) return;
        if (CAScorched.bombedRecently(p.d, cap)) { out.add("Bombardment: " + cap.getName() + " bombarded within a year; invasion first."); return; }
        if (!p.s.warScorchedOK) { out.add("Bombardment of " + cap.getName() + ": waits for Authorize bombardment."); return; }
        CAScorched.Plan pl = CAScorched.plan(p.s, cap, "");
        String payer = CAWar.payerFor(p.s, CAWarPolicy.SATBOMB, p.rich, pl.cost + pl.fee);
        int[] sz = CAConquest.sizes();
        float dom = CAWarPolicy.projectedDominance(CAConquest.dominance(), sz[0], CAConquest.pendingGain(), sz[1], CAScorched.pendingLoss() + CAScorched.loss(cap));
        String wait = CAWarPolicy.bombWait(true, CAWar.bombsUnderWay(), CAScorched.daysSinceLast(p.d), CAConquest.badboy(), dom,
                CAScorched.pendingInfamy() + CAConquest.pendingInfamy(), CAWarPolicy.bombInfamy(cap.getSize(), CAScorched.limited(cap)),
                p.s.warCoalitionOK, p.spare(), pl.fp, payer);
        out.add("Bombardment of " + cap.getName() + ": " + Math.round(pl.fp) + " points, " + CAWar.money(pl.cost, pl.fee) + "; " + waitText(wait) + ".");
    }

    static void invasion(CAWar.Pass p, FactionAPI f, int st, List<String> out) {
        if (p.commissioned) { out.add("Invasions: none while you hold a commission."); return; }
        List<CAConquest.Plan> l = CAConquest.targets(p.d, p.s, f, st);
        if (l.isEmpty()) {
            out.add(st >= CAStance.DESTROY ? "Invasions: no market can be invaded now."
                    : "Invasions: none under a quarter of the surplus (Enemy).");
            return;
        }
        CAConquest.Plan pl = l.get(0);
        String wait = CAConquest.gate(p, pl);
        out.add("Invasion of " + pl.t.getName() + ": " + Math.round(pl.fp) + " points, " + pl.marines + " marines, " + CAWar.money(pl.cost, pl.fee)
                + "; " + waitText(wait) + ".");
    }

    static void holdDown(CAWar.Pass p, FactionAPI f, List<String> out) {
        int raids = CAWar.raidsAt(f);
        if (raids >= CAWarPolicy.RAIDS_PER_FACTION) { out.add("Raids and blockades: " + raids + " under way (the most)."); return; }
        MarketAPI best = null;
        float bd = Float.MAX_VALUE;
        for (MarketAPI m : Misc.getFactionMarkets(f)) {
            if (!CAWar.targetable(m) || CAWar.oursAt(m) || CAWar.daysSince(p.d, CAWarPolicy.BLOCKADE + ":" + m.getId()) < CAWarPolicy.BLOCKADE_DAYS) continue;
            float ly = CAWar.nearestColonyLY(m);
            if (!p.rich && ly > CAWar.BASE_LY) continue;
            if (ly < bd) { bd = ly; best = m; }
        }
        if (best == null) out.add("Raids and blockades: no market within " + Math.round(CAWar.BASE_LY) + " ly.");
        else out.add((CAWar.military(best) ? "Raid on " : "Blockade of ") + best.getName() + ": " + Math.round(bd) + " ly away.");
    }

    static void retaliate(CAWar.Pass p, FactionAPI f, List<String> out) {
        if (p.s.warRetake() && !p.commissioned && p.allows(CAWarPolicy.RETAKE, f)) {
            for (MarketAPI m : Misc.getFactionMarkets(f)) {
                if (!CAConquest.wasYours(m) || !CAConquest.invadable(m)) continue;
                out.add("Retake of " + m.getName() + ": it was yours.");
                break;
            }
        }
        if (out.size() >= MAX_PLANNED || !p.allows(CAWarPolicy.RAID, f)) return;
        List<String> hit = new ArrayList<String>();
        for (String id : p.d.attackers().keySet()) {
            MarketAPI m = CAWar.attacker(id);   // 0.3.9.0: the attackers key holds the faction that attacked
            if (m != null && m.getFaction() == f && !CAWar.underWay(exerelin.campaign.intel.raid.NexRaidIntel.class, m)) hit.add(m.getName());
        }
        if (!hit.isEmpty()) out.add("Strike back at " + CAContracts.join(hit) + ": attacked you in the last " + Math.round(CAWar.ATTACK_DAYS) + " days.");
    }

    static String waitText(String wait) { return wait == null ? "can go on the next daily pass" : wait; }

    /** The markets marked for strikes back, as the 0.2.45 War fleets section showed them. */
    static void attackers(TooltipMakerAPI t, CAData d) {
        if (d.attackers().isEmpty()) return;
        List<String> names = new ArrayList<String>();
        for (String id : d.attackers().keySet()) {
            MarketAPI m = CAWar.attacker(id);   // 0.3.9.0: the attackers key holds the faction that attacked
            if (m != null) names.add(m.getName());
        }
        if (!names.isEmpty()) t.addPara("Strikes back due (attacked within " + Math.round(CAWar.ATTACK_DAYS) + " days): "
                + CAContracts.join(names) + ".", Misc.getGrayColor(), 8f);   // 0.3.8.6: shown as is
    }

}
