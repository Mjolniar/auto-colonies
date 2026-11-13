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

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 0.3.0.0 (WS-F2, research 04 section 3 "Factions page", DECISIONS 15-16): the Factions page of the empire panel, the one
 * place for the whole stance table. One row per faction that owns markets and shows in the intel tab, plus any faction
 * with a stance set (an eliminated Destroy faction stays listed): [<] friendlier, [>] more hostile, the stance in its
 * tone's colour (CAStance.tone: positive Ally and Friendly, negative Enemy and Destroy, highlight the rest), then the
 * relation, the markets and short notes. Sorted Ally to Destroy, then by name. "Reset all to Auto" asks first.
 *
 * No Nexerelin classes: the page also serves Industrial Evolution alone (ambassador priority). With Nexerelin on, each
 * row adds the War level its stance allows now (CAWarPage.attacks, called only after isModEnabled).
 *
 * Buttons (CAIntel): {"fac", "stance", faction id, "-" or "+"} and {"fac", "reset"}. Writes only CASettings.factionStance
 * (faction ids to numbers; Auto removes the entry) and CAStance.markSetByYou's CAMark stamp. A stance change does nothing
 * aggressive by itself: war on a faction that isn't hostile needs the War page's Declare war confirmation.
 */
public final class CAFactionsPage {
    /** The CANav page id; CASetupUI's Faction stances link opens it (CASetupUI.FACTIONS_PAGE is the same id). */
    public static final String PAGE = "factions";
    /** First element of this page's button ids. */
    static final String ID = "fac";
    static final String NEX = "nexerelin", INDEVO = "IndEvo";

    /** Hover text on the page's first line: what each stance does, one line each (DECISIONS 16, CAStance). */
    static final String[] MEANINGS = {
            "Ally: raised first; not attacked.",
            "Friendly: raised to your stop line; not attacked.",
            "Neutral: raised under 0; not attacked.",
            "Auto: as in 0.2.45.",
            "Rival: raids and blockades.",
            "Enemy: invasions; ceasefires rejected.",
            "Destroy: invasions and bombardment."};

    private CAFactionsPage() { }

    static boolean mod(String id) {
        try { return Global.getSettings().getModManager().isModEnabled(id); } catch (Throwable t) { return false; }
    }

    /** Factions with markets you can see in the intel tab, plus any with a stance set; Ally first, Destroy last, then by name. */
    static List<FactionAPI> factions(final CASettings s) {
        Set<String> seen = new HashSet<String>();
        List<FactionAPI> out = new ArrayList<FactionAPI>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            FactionAPI f = m.getFaction();
            if (f == null || m.isPlayerOwned() || f.isPlayerFaction() || !f.isShowInIntelTab() || !seen.add(f.getId())) continue;
            out.add(f);
        }
        if (s.factionStance != null) for (Map.Entry<String, Integer> e : s.factionStance.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() == CAStance.AUTO || seen.contains(e.getKey())) continue;
            FactionAPI f = Global.getSector().getFaction(e.getKey());
            if (f == null || f.isPlayerFaction()) continue;
            seen.add(f.getId());
            out.add(f);
        }
        Collections.sort(out, new Comparator<FactionAPI>() {
            public int compare(FactionAPI a, FactionAPI b) {
                int c = Integer.compare(s.stanceOf(a.getId()), s.stanceOf(b.getId()));
                return c != 0 ? c : a.getDisplayName().compareTo(b.getDisplayName());
            }
        });
        return out;
    }

    /** Markets of a faction that are in the economy and not hidden. */
    static int markets(FactionAPI f) {
        int n = 0;
        for (MarketAPI m : Misc.getFactionMarkets(f)) if (!m.isHidden() && m.isInEconomy()) n++;
        return n;
    }

    /** "hostile (-62)" or "welcoming (31)": the relation as the game names it, and its number. */
    static String relation(FactionAPI f) {
        float rel = Global.getSector().getPlayerFaction().getRelationship(f.getId());
        String name = f.isHostileTo(Factions.PLAYER) ? "hostile" : f.getRelationshipLevel(Factions.PLAYER).getDisplayName().toLowerCase(Locale.ROOT);
        return name + " (" + Math.round(rel * 100f) + ")";
    }

    static Color tone(int stance) {
        int t = CAStance.tone(stance);
        return t > 0 ? Misc.getPositiveHighlightColor() : t < 0 ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
    }

    /** Stances set to something other than Auto. */
    static int set(CASettings s) {
        int n = 0;
        if (s.factionStance != null) for (Integer v : s.factionStance.values()) if (v != null && v != CAStance.AUTO) n++;
        return n;
    }

    /** The page. */
    static void render(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        boolean nex = mod(NEX), indevo = mod(INDEVO);
        CATheme.heading(t, "Factions", 0f);
        t.addPara(CALog.esc(CASetupUI.DESC.get("stances") + " [<] friendlier, [>] more hostile."), gray, 6f);   // 0.3.0.0 (WS-F3 fix pass 2): the Settings row's line, said once
        t.addTooltipToPrevious(tip(MEANINGS), TooltipMakerAPI.TooltipLocation.BELOW);
        if (!nex && !indevo) t.addPara("Stances need Nexerelin or Industrial Evolution; neither is on.", hl, 4f);
        else if (!nex) t.addPara("Without Nexerelin, stances set ambassador priority only.", gray, 4f);
        else if (CALowProfile.on()) t.addPara("Low profile: no raids, sabotage or declarations.", gray, 4f);

        String rival = null;
        if (nex) rival = rivalOf(d, s);
        List<FactionAPI> list = factions(s);
        t.addPara("%s factions, %s not on Auto.", 6f, hl, String.valueOf(list.size()), String.valueOf(set(s)));
        for (FactionAPI f : list) {
            try { row(t, d, s, f, nex ? rival : null, nex); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "faction row failed for " + f.getId(), e); }
        }
        ButtonAPI reset = t.addButton("Reset all to Auto", new String[]{ID, "reset"}, base, dark, Alignment.MID, CutStyle.TL_BR, 200f, 22f, 10f);
        reset.setEnabled(set(s) > 0);
    }

    /** "Pick rivals automatically": the hostile Auto faction counted as Rival (Nexerelin; isModEnabled first). */
    static String rivalOf(CAData d, CASettings s) {
        if (!Global.getSettings().getModManager().isModEnabled(NEX)) return null;
        try { return CAWarPage.autoRival(d, s); } catch (Throwable t) { return null; }
    }

    /** One faction's row: [<] [>], NAME :: STANCE, then relation, markets and notes. */
    static void row(TooltipMakerAPI t, CAData d, CASettings s, FactionAPI f, String rival, boolean nex) {
        Color base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        String fid = f.getId();
        int st = s.stanceOf(fid);
        ButtonAPI lower = t.addButton("<", new String[]{ID, "stance", fid, "-"}, base, dark, Alignment.MID, CutStyle.TL_BR, 30f, 20f, 6f);
        ButtonAPI higher = t.addButton(">", new String[]{ID, "stance", fid, "+"}, base, dark, Alignment.MID, CutStyle.TL_BR, 30f, 20f, 0f);
        higher.getPosition().rightOfTop(lower, 4f);
        lower.setEnabled(st > CAStance.ALLY);
        higher.setEnabled(st < CAStance.DESTROY);
        int n = markets(f);
        StringBuilder notes = new StringBuilder(relation(f)).append(", ").append(n == 0 ? "no markets" : n == 1 ? "1 market" : n + " markets");
        if (CAStance.fromOldPriority(s, d, fid)) notes.append(", from your 0.2.45 priority");
        String war = nex ? attacks(d, s, f, rival) : null;
        if (war != null) notes.append(", ").append(war);
        else if (!nex && mod(INDEVO)) notes.append(", ambassador priority ").append(CASettings.PRIORITY_NAMES[CAStance.ambassadorPriority(st)]);
        LabelAPI l = t.addPara("%s  ::  %s   // " + CALog.esc(notes.toString()), 0f, new Color[]{Misc.getHighlightColor(), tone(st)},
                f.getDisplayName().toUpperCase(Locale.ROOT), CAStance.name(st).toUpperCase(Locale.ROOT));
        l.getPosition().rightOfMid(higher, 10f);
        CALog.endRow(t, lower);
    }

    /** The War level a faction's stance allows now, or why it isn't attacked (Nexerelin; isModEnabled first). */
    static String attacks(CAData d, CASettings s, FactionAPI f, String rival) {
        if (!Global.getSettings().getModManager().isModEnabled(NEX)) return null;
        try { return CAWarPage.attacks(d, s, f, rival); } catch (Throwable t) { return null; }
    }

    // ---------------- buttons ----------------

    /** Reset all to Auto asks first (CAIntel's confirmation). */
    static boolean needsConfirm(String action) { return "reset".equals(action); }

    static void confirmPrompt(TooltipMakerAPI t, String action, CASettings s) {
        if (!"reset".equals(action)) return;
        t.addPara("Set every faction's stance to Auto? %s stances change.", 0f, Misc.getHighlightColor(), String.valueOf(set(s)));
    }

    /** A button of this page: a stance step or Reset all to Auto. Logs what changed. */
    static void handle(String[] a, CAData d) {
        CASettings s = d.settings;
        if (a.length >= 4 && "stance".equals(a[1]) && a[2] != null && !a[2].isEmpty()) {
            String fid = a[2];
            int was = s.stanceOf(fid);
            int st = CAStance.step(was, "-".equals(a[3]) ? -1 : 1);
            if (st == was) return;
            if (st == CAStance.AUTO) s.factionStance().remove(fid); else s.factionStance().put(fid, st);
            CAStance.markSetByYou(fid);   // a stance you set acts fully in diplomacy (CAStance.fromOldPriority)
            FactionAPI f = Global.getSector().getFaction(fid);
            // the War page's wording (CAWar.handle), which CATheme shows as "DIPLO / Hegemony stance Enemy"
            CALog.did("War: " + (f != null ? f.getDisplayName() : fid) + " stance " + CAStance.name(st) + ".");
        } else if ("reset".equals(a[1])) {
            int n = set(s);
            for (String fid : new ArrayList<String>(s.factionStance().keySet())) CAStance.markSetByYou(fid);
            s.factionStance().clear();
            CALog.quiet("Factions page: Reset all to Auto, " + n + " stances changed");
            CALog.did("War: all factions stance Auto.");
        }
    }

    static TooltipMakerAPI.TooltipCreator tip(final String[] lines) {
        return new TooltipMakerAPI.TooltipCreator() {
            public boolean isTooltipExpandable(Object o) { return false; }
            public float getTooltipWidth(Object o) { return 460f; }
            public void createTooltip(TooltipMakerAPI tt, boolean expanded, Object o) {
                for (int i = 0; i < lines.length; i++) tt.addPara(CALog.esc(lines[i]), i == 0 ? 0f : 6f);
            }
        };
    }
}
