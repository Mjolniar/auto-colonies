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
            case CAWarPolicy.OFF: return "No war fleets.";
            case CAWarPolicy.DEFEND: return "Siege relief and defense fleets.";
            case CAWarPolicy.RETALIATE: return "Plus raids on attackers" + (retake ? " and retakes." : ".");
            case CAWarPolicy.HOLD: return "Plus raids and blockades on Rival and up.";
            case CAWarPolicy.CONQUER: return "Plus invasions of Enemy and Destroy.";
            default: return "Plus authorized bombardment of Destroy capitals.";
        }
    }

    /** The page. Each part catches its own failure, so one part failing leaves the rest on the page. */
    static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        CATheme.heading(t, "War (Nexerelin)", 0f);
        int level = CAWarPolicy.clamp(s.warLevel);
        t.addPara("War level: %s. " + CALog.esc(levelWhat(level, s.warRetake())), 6f, hl, CAWarPolicy.levelName(level));
        // 0.3.0.0 (WS-F3 fix pass 2): where the level is set and that stances cap it is the Settings row's line
        try { header(t, d, s, level); }
        catch (Throwable e) { failed(t, "war page header", e); }
        try { buttons(t, s, level); }
        catch (Throwable e) { failed(t, "war page buttons", e); }
        try { factions(t, d, s); }
        catch (Throwable e) { failed(t, "war page factions", e); }
        try { CAConquest.render(t, d, s); }
        catch (Throwable e) { failed(t, "war page conquered colonies", e); }
        try { attackers(t, d); }
        catch (Throwable e) { failed(t, "war page attackers", e); }
    }

    static void failed(TooltipMakerAPI t, String what, Throwable e) {
        CADiagnostics.LOG.warn(CADiagnostics.TAG + what + " failed", e);
        t.addPara(CATheme.NOT_SHOWN, Misc.getGrayColor(), 4f);
    }

    /** Caps, fleet request points, infamy, money, today's pass. */
    static void header(TooltipMakerAPI t, CAData d, CASettings s, int level) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor();
        boolean rich = CASurplus.rich(s);
        if (CALowProfile.on() && level > CAWarPolicy.DEFEND)
            t.addPara("Low profile: every faction capped at Defend.", bad, 4f);
        FactionAPI comm = Misc.getCommissionFaction();
        if (comm != null && level > CAWarPolicy.DEFEND)
            t.addPara("Commission from %s: capped at Hold them down.",
                    4f, bad, comm.getDisplayName());

        InvasionFleetManager mgr = InvasionFleetManager.getManager();
        if (mgr != null) {
            int cap = mgr.getFleetRequestCapacity();
            float share = rich ? CAWar.RICH_RESERVE : CAWar.RESERVE;
            t.addPara("Fleet request points: %s of %s. %s kept (%s percent" + (rich ? ", rich" : "") + ").", 4f, hl,
                    String.valueOf((int) mgr.getFleetRequestStock()), String.valueOf(cap), String.valueOf(Math.round(share * cap)),
                    String.valueOf(Math.round(share * 100f)));
            float exp = CAExpansion.reservedFP();
            if (exp > 0f) t.addPara("Held for a colony fleet: %s points.", 2f, hl, String.valueOf(Math.round(exp)));
        }

        float badboy = CAConquest.badboy(), dom = CAConquest.dominance();
        float pend = CAConquest.pendingInfamy() + CAScorched.pendingInfamy();
        float now = CAWarPolicy.infamy(badboy, dom), after = CAWarPolicy.infamy(badboy + pend, dom);
        Color ic = after >= CAWarPolicy.INFAMY_NEAR ? bad : hl;
        if (pend > 0f) t.addPara("Infamy: %s of %s (coalition line). %s after operations under way.", 4f, ic,
                String.valueOf(Math.round(now)), String.valueOf(Math.round(CAWarPolicy.INFAMY_LINE)), String.valueOf(Math.round(after)));
        else t.addPara("Infamy: %s of %s (coalition line).", 4f, ic, String.valueOf(Math.round(now)), String.valueOf(Math.round(CAWarPolicy.INFAMY_LINE)));
        t.addTooltipToPrevious(CAFactionsPage.tip(new String[]{
                "Coalition at 100 infamy, ends under 75; base infamy drops 3 a month."}),
                TooltipMakerAPI.TooltipLocation.BELOW);
        t.addPara(s.warCoalitionOK ? "Infamy past the line: allowed." : "Offensives stop at the line.", gray, 2f);

        float rung = 0f;
        try { rung = CARich.allowance(CAWar.militaryRung()); } catch (Throwable ignored) { }
        t.addPara("Money: %s budget left, %s surplus, %s Military rung, reserve %s.", 4f, hl,
                Misc.getDGSCredits(CABudget.available(s)), Misc.getDGSCredits(CASurplus.amount(s)), Misc.getDGSCredits(rung), Misc.getDGSCredits(s.reserve));
        t.addPara("Raids and retakes: budget. Invasions and bombardment: surplus. Fee applies.", gray, 2f);
        if (CAWar.summary != null) t.addPara("Today: " + CALog.esc(CAWar.summary), gray, 4f);
    }

    /** Stand down, Recall all, Authorize bombardment (at Scorched), then the War settings at a glance. */
    static void buttons(TooltipMakerAPI t, CASettings s, int level) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        // 0.3.0.0 (WS-F3 fix pass 3): no hover on these two; each opens a confirmation that says what it does (CAWar.confirmPrompt)
        ButtonAPI stop = t.addButton("Stand down", new String[]{"war", "standdown", ""}, base, dark, Alignment.MID, CutStyle.TL_BR, 160f, 22f, 8f);
        ButtonAPI rec = t.addButton("Recall all", new String[]{"war", "recall", ""}, base, dark, Alignment.MID, CutStyle.TL_BR, 160f, 22f, 0f);
        rec.getPosition().rightOfTop(stop, 6f);
        if (level >= CAWarPolicy.SCORCHED && !s.warScorchedOK) {
            ButtonAPI sb = t.addButton("Authorize bombardment", new String[]{"war", "scorched", ""}, base, dark, Alignment.MID, CutStyle.TL_BR, 200f, 22f, 0f);
            sb.getPosition().rightOfTop(rec, 6f);
        }
        CALog.endRow(t, stop);
        if (level >= CAWarPolicy.SCORCHED)
            t.addPara("Saturation bombardment: %s.", 4f, s.warScorchedOK ? Misc.getNegativeHighlightColor() : hl, s.warScorchedOK ? "authorized" : "not authorized");
        int cm = Math.max(0, Math.min(CASettings.CONQUERED_NAMES.length - 1, s.conqueredMode));
        int sc = Math.max(0, Math.min(CASettings.SCORCHED_TARGET_NAMES.length - 1, s.scorchedTargets));
        t.addPara("Retake: " + onOff(s.warRetake()) + ". Auto rivals: " + onOff(s.warAutoRivals) + ".", gray, 4f);   // the rest: Settings, Advanced, War
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
        if (st <= CAStance.NEUTRAL) return "never attacked";
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
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
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
            try { for (String line : planned(p, f)) t.addPara("  " + CALog.esc(line), gray, 2f); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "war page plan failed for " + f.getId(), e); }
        }
        ButtonAPI open = t.addButton("OPEN", CANav.PREFIX + CAFactionsPage.PAGE, base, dark, Alignment.MID, CutStyle.TL_BR, 86f, 20f, 10f);
        LabelAPI l = t.addPara("All stances: the Factions page.", gray, 0f);
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
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
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
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m != null) names.add(m.getName());
        }
        if (!names.isEmpty()) t.addPara("Strikes back due (attacked within " + Math.round(CAWar.ATTACK_DAYS) + " days): "
                + CALog.esc(CAContracts.join(names)) + ".", Misc.getGrayColor(), 8f);
    }

}
