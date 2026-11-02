package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The empire panel: every colony's profile, mode and top pick, Autopilot Storage, one page per mod feature in use,
 * settings, the log and Diagnostics, one page at a time from the list on the left (CANav). It has its own filter in the Intel
 * screen, "Colony Autopilot" (a tag of its own, as mods like Treasure Hunt do), rather than sitting among colony threats
 * or important intel.
 */
public class CAIntel extends BaseIntelPlugin {
    static final String RESCAN = "jt_ca_rescan";
    public static final String TAG = "Colony Autopilot";
    /** UI handles are never saved with the game. */
    transient TooltipMakerAPI lastTooltip;

    public static void ensure() {
        com.fs.starfarer.api.campaign.comm.IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (!mgr.hasIntelOfClass(CAIntel.class)) mgr.addIntel(new CAIntel(), true);
        // before 2.8.0 it was marked important; it has its own filter now
        com.fs.starfarer.api.campaign.comm.IntelInfoPlugin i = mgr.getFirstIntel(CAIntel.class);
        if (i != null && i.isImportant()) i.setImportant(false);
    }

    @Override protected String getName() { return "Colony Autopilot"; }
    @Override public String getIcon() { return "graphics/icons/markets/headquarters.png"; }
    @Override public boolean hasLargeDescription() { return true; }
    @Override public boolean hasSmallDescription() { return false; }
    @Override public boolean shouldRemoveIntel() { return false; }
    @Override public boolean doesButtonHaveConfirmDialog(Object buttonId) { return false; }
    @Override public String getSortString() { return "Colony Autopilot"; }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>();
        tags.add(TAG);
        return tags;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, IntelInfoPlugin.ListInfoMode mode) {
        Color c = getTitleColor(mode);
        info.addPara(getName(), c, 0f);
        info.addPara("%s colonies, %s diagnostics issues", 3f, Misc.getGrayColor(), Misc.getHighlightColor(),
                "" + Misc.getPlayerMarkets(false).size(),
                "" + (CADiagnostics.count(CADiagnostics.Level.ANALYZED) + CADiagnostics.count(CADiagnostics.Level.QUARANTINED)));
    }

    /** The page shown (UI state only, not saved). */
    static String page = "colonies";
    transient String lastPage;

    /** The pages in the list on the left: always Colonies, Storage, Settings, Log and Diagnostics, plus Mod features when one is in use. */
    static List<String[]> pages(CAData data, CASettings s, List<MarketAPI> markets) {
        List<String[]> p = new ArrayList<String[]>();
        int pending = 0;
        for (MarketAPI m : markets) if (data.colony(m.getId()).pendingId != null) pending++;
        p.add(new String[]{"colonies", pending > 0 ? "Colonies (" + pending + " to approve)" : "Colonies"});
        p.add(new String[]{"storage", "Autopilot Storage"});
        p.add(new String[]{"crises", CACrisis.pending() != null ? "Colony crises (one coming)" : "Colony crises"});
        if (!modSections(s, markets).isEmpty()) p.add(new String[]{"mods", "Mod features"});
        int issues = CADiagnostics.count(CADiagnostics.Level.ANALYZED) + CADiagnostics.count(CADiagnostics.Level.QUARANTINED);
        p.add(new String[]{null, "Autopilot"});
        p.add(new String[]{"settings", "Settings"});
        p.add(new String[]{"log", "Log"});
        p.add(new String[]{"diag", issues > 0 ? "Diagnostics (" + issues + ")" : "Diagnostics"});
        return p;
    }

    /** The mod features in use, in the order the Mod features page shows them. */
    static List<String> modSections(CASettings s, List<MarketAPI> markets) {
        boolean anyShunt = false, anyMega = false;
        for (MarketAPI m : markets) {
            if (CAHypershunt.is(m)) anyShunt = true;
            if (CAMegastructures.has(m)) anyMega = true;
        }
        List<String> l = new ArrayList<String>();
        com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
        if (mm.isModEnabled(CASettlement.MOD)) l.add("settle");
        if (mm.isModEnabled(CASpies.MOD)) l.add("agents");
        if (mm.isModEnabled(CAAutonomy.MOD) && (s.autonomyMode != 0 || !CAData.get().autonomous().isEmpty())) l.add("autonomy");
        if (mm.isModEnabled(CAAugments.MOD) && s.augmentMode != 2) l.add("augments");
        if (mm.isModEnabled(CAContracts.MOD) && s.contractMode != 2) l.add("contracts");
        if (anyMega && mm.isModEnabled(CAHypershunt.MOD) && s.hypershuntMode != 2) l.add("mega");
        if (anyShunt && mm.isModEnabled(CAHypershunt.MOD) && s.hypershuntMode != 2) l.add("shunt");
        return l;
    }

    /** One section of the Mod features page. Each checks its mod again so no optional class loads without it. */
    static void renderMod(TooltipMakerAPI t, String id, CAData data, CASettings s) {
        com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
        if (id.equals("settle") && mm.isModEnabled(CASettlement.MOD)) {
            t.addSectionHeading("Settlement (Random Assortment of Things)", Alignment.MID, 0f);
            CASettlement.render(t, data, s);
        } else if (id.equals("agents") && mm.isModEnabled(CASpies.MOD)) {
            CASpies.render(t, data, s);
        } else if (id.equals("autonomy") && mm.isModEnabled(CAAutonomy.MOD)) {
            CAAutonomy.render(t, data, s);
        } else if (id.equals("augments") && mm.isModEnabled(CAAugments.MOD)) {
            t.addSectionHeading("Station augments", Alignment.MID, 0f);
            CAAugments.renderEmpire(t, data, s);
        } else if (id.equals("contracts") && mm.isModEnabled(CAContracts.MOD)) {
            t.addSectionHeading("Trade contracts (Ashes of the Domain)", Alignment.MID, 0f);
            CAContracts.render(t, data, s);
        } else if (id.equals("mega") && mm.isModEnabled(CAHypershunt.MOD)) {
            t.addSectionHeading("Pluto and Nidavelir (Ashes of the Domain)", Alignment.MID, 0f);
            CAMegastructures.renderEmpire(t, data, s);
        } else if (id.equals("shunt") && mm.isModEnabled(CAHypershunt.MOD)) {
            t.addSectionHeading("Coronal Hypershunt (Ashes of the Domain)", Alignment.MID, 0f);
            CAHypershunt.renderEmpire(t, data, s);
        }
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        CAData data = CAData.get();
        CASettings s = data.settings;
        CABudget.rollMonth(s);
        List<MarketAPI> markets = Misc.getPlayerMarkets(false);
        List<String[]> pages = pages(data, s, markets);
        page = CANav.valid(pages, page);
        float scrollY = 0f;
        try { if (page.equals(lastPage) && lastTooltip != null && lastTooltip.getExternalScroller() != null) scrollY = lastTooltip.getExternalScroller().getYOffset(); }
        catch (Throwable ignored) { }

        TooltipMakerAPI nav = panel.createUIElement(CANav.W, height, false);
        nav.addTitle("Colony Autopilot");
        CANav.render(nav, pages, page);
        panel.addUIElement(nav).inTL(0f, 0f);

        TooltipMakerAPI t = panel.createUIElement(width - CANav.W - CANav.GAP, height, true);
        try { renderPage(t, data, s, markets, width - CANav.W - CANav.GAP); }
        catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "empire panel page " + page + " failed", e);
            t.addPara("This page couldn't be shown; see starsector.log.", Misc.getGrayColor(), 6f);
        }
        panel.addUIElement(t).inTL(CANav.W + CANav.GAP, 0f);
        try { if (t.getExternalScroller() != null) t.getExternalScroller().setYOffset(scrollY); } catch (Throwable ignored) { }
        lastTooltip = t;
        lastPage = page;
    }

    void renderPage(TooltipMakerAPI t, CAData data, CASettings s, List<MarketAPI> markets, float width) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();

        if (page.equals("colonies")) {
            t.addPara("Every colony starts in Advisor mode. Open a colony's own panel with the Colony Autopilot button on its colony "
                    + "screen, or by right-clicking its Population & Infrastructure.", gray, 0f);
            t.addSectionHeading("Budget", Alignment.MID, 12f);
            t.addPara("This month: %s of %s spent (%s in fees), %s available. Never spends below %s.", 6f, hl,
                    Misc.getDGSCredits(s.spent), Misc.getDGSCredits(CABudget.limit(s)), Misc.getDGSCredits(s.fees),
                    Misc.getDGSCredits(CABudget.available(s)), Misc.getDGSCredits(s.reserve));
            if (CABudget.paused(s)) t.addPara("Spending is paused: net income is negative.", Misc.getNegativeHighlightColor(), 4f);
            try { CASurplus.renderEmpire(t, s); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus line failed", e); }

            boolean anyPending = false;
            for (MarketAPI m : markets) if (data.colony(m.getId()).pendingId != null) anyPending = true;
            if (anyPending) {
                t.addSectionHeading("Waiting for your approval", Alignment.MID, 12f);
                for (MarketAPI m : markets) {
                    CAData.Colony c = data.colony(m.getId());
                    if (c.pendingId == null) continue;
                    t.addPara("%s: %s for %s", 8f, hl, m.getName(), c.pendingName, Misc.getDGSCredits(c.pendingCost));
                    ButtonAPI a = t.addButton("Approve", new String[]{"approve", m.getId()}, hl, dark, Alignment.MID, CutStyle.ALL, 120f, 22f, 4f);
                    ButtonAPI r = t.addButton("Reject", new String[]{"reject", m.getId()}, base, dark, Alignment.MID, CutStyle.ALL, 120f, 22f, 0f);
                    r.getPosition().rightOfTop(a, 6f);
                    CALog.endRow(t, a);
                }
            }

            t.addSectionHeading("Colonies", Alignment.MID, 12f);
            if (markets.isEmpty()) t.addPara("You have no colonies yet.", gray, 8f);
            for (MarketAPI m : markets) {
                CAData.Colony c = data.colony(m.getId());
                String top = "nothing buildable";
                try {
                    if (CAEngine.isOutpost(m)) top = "Nexerelin outpost, left alone";
                    else if (c.mode == CAData.Mode.OFF) top = "autopilot off";
                    else if (CAHypershunt.is(m)) top = CAHypershunt.summary(m);
                    else {
                        CAEngine.Result r = c.buildout && c.mode == CAData.Mode.AUTO ? CAEngine.evaluateForProfit(m, data.minStability)
                                : CAEngine.evaluate(m, c.profile, data.minStability);
                        CAEngine.Pick p = null;
                        for (CAEngine.Pick q : r.picks) if (CAEngine.worthwhile(q)) { p = q; break; }
                        if (p != null) top = p.verb + " " + p.name + " (" + CAEngine.signed(p.fx[0]) + " a month after upkeep)";
                        else if (!r.picks.isEmpty()) top = "nothing pays for itself yet";
                        if (r.blocked != null) top = r.blocked.toLowerCase() + "; next: " + top;
                    }
                } catch (Throwable e) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "evaluate failed for " + m.getName(), e);
                    top = "error, see Diagnostics";
                }
                String flags = (c.buildout ? ", fast buildout" : "") + (c.threatHigh ? ", threatened" : "") + (CASiege.held(m) ? ", under siege" : "");
                t.addPara("%s (size %s): %s, %s" + CALog.esc(flags), 12f, hl, m.getName(), "" + m.getSize(), c.profile.label, c.mode.label);
                t.addPara("Next: " + CALog.esc(top), gray, 2f);
                ButtonAPI b1 = t.addButton("Next goal", new String[]{"profile", m.getId()}, base, dark, Alignment.MID, CutStyle.ALL, 140f, 22f, 4f);
                ButtonAPI b2 = t.addButton("Next mode", new String[]{"mode", m.getId()}, base, dark, Alignment.MID, CutStyle.ALL, 140f, 22f, 0f);
                b2.getPosition().rightOfTop(b1, 6f);
                CALog.endRow(t, b1);
            }
        } else if (page.equals("storage")) {
            t.addSectionHeading("Autopilot Storage", Alignment.MID, 0f);
            t.addPara("Dock at any of your colonies and open the Autopilot Storage tab to deposit colony items, AI cores and research "
                    + "databanks" + (s.storageCheat ? " (anything, with the storage cheat on)" : "") + ". Auto-mode colonies take items and cores "
                    + "from it, and research takes what a tech needs. Nothing in your normal storage is used.", 6f);
            com.fs.starfarer.api.campaign.CargoAPI st = data.storage();
            if (st.isEmpty()) t.addPara("Empty.", gray, 4f);
            for (com.fs.starfarer.api.campaign.CargoStackAPI stack : st.getStacksCopy()) {
                t.addPara("%s x%s", 3f, hl, CALog.esc(stack.getDisplayName()), "" + (int) stack.getSize());
            }
            t.addPara("AI cores: %s, inspection risk %s.", 6f, hl, s.useAICores ? "used" : "not used",
                    CASettings.CORE_RISK_NAMES[Math.max(0, Math.min(3, s.coreRisk))].toLowerCase());
            String none = CAItems.noInspections();
            if (none != null) t.addPara("No Hegemony inspection can come now: %s. No limit on cores.", 4f, hl, none);
            else t.addPara("The Hegemony inspects only a colony with more than %s inspection points, but counts every core at a colony above "
                    + "size 3 toward its crisis (%s points now). Next inspection: %s fleet points. See Colony crises.", 4f, hl,
                    "" + CAItems.safePoints(), "" + CACrisis.base().hegPoints, "" + (int) CAItems.nextInspectionFP());
            java.util.Set<MarketAPI> inspected = CAItems.inspected();
            for (MarketAPI m : CAItems.autoMarkets()) {
                int pts = CAItems.aiPoints(m), lim = CAItems.coreLimit(s, m, none);
                boolean coming = inspected.contains(m);
                if (pts <= 0 && !coming) continue;
                String why = coming ? "inspection coming: left as it is" : CAItems.limitWhy(s, m, none, lim);
                t.addPara("%s: %s inspection points, %s", 3f, coming || pts > lim ? Misc.getNegativeHighlightColor() : hl,
                        CALog.esc(m.getName()), "" + pts, why);
            }
            if (s.storageCheat) {
                java.util.List<String> cov = CAStockpile.covering();
                t.addPara("Storage cheat on: colonies draw their shortages from here. " + (cov.isEmpty() ? "No shortage it can cover right now."
                        : "Covering " + CALog.esc(CAContracts.join(cov)) + "."), 6f);
            }
            if (Global.getSettings().getModManager().isModEnabled(CAResearch.MOD) && s.researchMode != 2) {
                t.addSpacer(16f);
                t.addSectionHeading("Research (Ashes of the Domain)", Alignment.MID, 0f);
                try { CAResearch.render(t, data, s); }
                catch (Throwable e) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "research section failed", e);
                    t.addPara("This section couldn't be shown; see starsector.log.", gray, 6f);
                }
            }
        } else if (page.equals("crises")) {
            CACrisis.render(t, data, s);
        } else if (page.equals("mods")) {
            boolean first = true;
            for (String id : modSections(s, markets)) {
                if (!first) t.addSpacer(16f);
                first = false;
                // one section failing leaves the others on the page
                try { renderMod(t, id, data, s); }
                catch (Throwable e) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "mod feature section " + id + " failed", e);
                    t.addPara("This section couldn't be shown; see starsector.log.", gray, 6f);
                }
            }
        } else if (page.equals("settings")) {
            t.addSectionHeading("Settings", Alignment.MID, 0f);
            if (!s.setupDone) t.addPara("First-time setup hasn't run yet; it opens when you first switch a colony to Approve or Auto.", gray, 6f);
            t.addPara("Click a group to open it.", gray, 6f);
            CASetupUI.render(t, s, width);
        } else if (page.equals("log")) {
            t.addSectionHeading("Log", Alignment.MID, 0f);
            if (s.log.isEmpty()) t.addPara("Nothing yet.", gray, 6f);
            for (int i = 0; i < Math.min(40, s.log.size()); i++) t.addPara(CALog.esc(s.log.get(i)), gray, 3f);
        } else if (page.equals("diag")) {
            renderDiagnostics(t);
        }
    }

    static void renderDiagnostics(TooltipMakerAPI t) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        t.addSectionHeading("Diagnostics", Alignment.MID, 0f);
        t.addPara("Last scan: %s. Supported content is planned with; %s mod buildings were measured on a probe colony and are scored "
                        + "from those measurements; %s analyzed and %s quarantined entries are never acted on. "
                        + "Full report: saves/common/" + CADiagnostics.REPORT + ".data", 8f, hl,
                CADiagnostics.getLastScan(), "" + CADiagnostics.count(CADiagnostics.Level.MEASURED),
                "" + CADiagnostics.count(CADiagnostics.Level.ANALYZED), "" + CADiagnostics.count(CADiagnostics.Level.QUARANTINED));
        t.addButton("Rescan now", RESCAN, base, dark, Alignment.MID, CutStyle.ALL, 140f, 22f, 6f);
        // mod integrations: does each still find what it relies on after mod updates
        List<CACompat.Entry> ints = CACompat.installed();
        if (!ints.isEmpty()) {
            int ok = 0;
            for (CACompat.Entry e : ints) if (e.status == CACompat.Status.OK) ok++;
            t.addPara("Mod integrations: %s of %s exactly as written for; the rest below.", 8f, hl, "" + ok, "" + ints.size());
            for (CACompat.Entry e : ints) {
                if (e.status == CACompat.Status.OK) continue;
                Color lc = e.status == CACompat.Status.NEWER ? hl : Misc.getNegativeHighlightColor();
                t.addPara("%s " + CALog.esc(e.version) + ": " + CALog.esc(CACompat.describe(e)), 4f, lc, e.name);
            }
        }
        for (Map.Entry<String, String> e : CABreaker.tripped().entrySet())
            t.addPara("Paused this session: %s (didn't take effect three times; last at " + CALog.esc(e.getValue()) + ")", 4f, Misc.getNegativeHighlightColor(), e.getKey());
        if (!CAConst.drift.isEmpty()) t.addPara("Mod values that changed since Autopilot was written (the new ones are used): " + CALog.esc(String.join("; ", CAConst.drift)), gray, 4f);
        List<String> cal = CACalibrate.notable();
        if (!cal.isEmpty()) t.addPara("Corrected from your colonies: " + CALog.esc(String.join(", ", cal.subList(0, Math.min(12, cal.size())))), gray, 4f);
        int shown = 0;
        for (CADiagnostics.Level lvl : new CADiagnostics.Level[]{CADiagnostics.Level.QUARANTINED, CADiagnostics.Level.MEASURED, CADiagnostics.Level.ANALYZED}) {
            for (CADiagnostics.Issue i : CADiagnostics.getIssues()) {
                if (i.level != lvl || shown >= 60) continue;
                shown++;
                Color lc = lvl == CADiagnostics.Level.QUARANTINED ? Misc.getNegativeHighlightColor() : hl;
                t.addPara("%s  %s %s (%s)", 6f, lc, lvl.name(), i.kind, i.name, i.mod);
                String d = i.detail == null ? "" : i.detail;
                t.addPara("    " + CALog.esc(d.length() > 220 ? d.substring(0, 220) + "..." : d), gray, 2f);
            }
        }
        int total = CADiagnostics.getIssues().size();
        if (total > shown) t.addPara((total - shown) + " more in the report file.", gray, 6f);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        CAData d = CAData.get();
        if (CANav.page(buttonId) != null) {
            page = CANav.page(buttonId);
        } else if (RESCAN.equals(buttonId)) {
            CADiagnostics.runScan();
        } else if (CASetupUI.handle(buttonId, d.settings)) {
            if (!String.valueOf(buttonId).contains(":group:")) d.settings.setupDone = true;
        } else if (buttonId instanceof String[] && "agent".equals(((String[]) buttonId)[0])) {
            String[] a = (String[]) buttonId;
            if (Global.getSettings().getModManager().isModEnabled(CASpies.MOD)) {
                try { CASpies.handle(d, a[1], a[2]); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "agent action failed", t); CALog.add("Agents: that failed; see starsector.log", true); }
            }
        } else if (buttonId instanceof String[] && "auton".equals(((String[]) buttonId)[0])) {
            String[] a = (String[]) buttonId;
            if (Global.getSettings().getModManager().isModEnabled(CAAutonomy.MOD)) {
                try { CAAutonomy.handle(d, a[1], a[2]); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "autonomy action failed", t); CALog.add("Autonomy: that failed; see starsector.log", true); }
            }
        } else if (buttonId instanceof String[] && "settle".equals(((String[]) buttonId)[0])) {
            String[] a = (String[]) buttonId;
            if (Global.getSettings().getModManager().isModEnabled(CASettlement.MOD)) {
                try { CASettlement.handle(a[1], a[2], d, d.settings); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "settlement action failed", t); CALog.add("Settlement: that failed; see starsector.log", true); }
            }
        } else if (buttonId instanceof String[] && "contract".equals(((String[]) buttonId)[0])) {
            if (Global.getSettings().getModManager().isModEnabled(CAContracts.MOD)) {
                String err = CAContracts.accept(((String[]) buttonId)[2], "you");
                if (err != null) CALog.add("Trade contract: " + err, true);
            }
        } else if (buttonId instanceof String[] && "research".equals(((String[]) buttonId)[0])) {
            if (Global.getSettings().getModManager().isModEnabled(CAResearch.MOD)) {
                String err = CAResearch.handle(((String[]) buttonId)[1], ((String[]) buttonId)[2]);
                if (err != null) CALog.add("Research: " + err, true);
            }
        } else if (buttonId instanceof String[] && "hs".equals(((String[]) buttonId)[0])) {
            String[] a = (String[]) buttonId;
            MarketAPI m = CAAuto.market(a[2]);
            if (m != null && Global.getSettings().getModManager().isModEnabled(CAHypershunt.MOD)) {
                try { String msg = CAHypershunt.handle(m, a[1], a[3]); CALog.add(m.getName() + ": " + msg); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt action failed", t); CALog.add(m.getName() + ": hypershunt: that failed; see starsector.log", true); }
            }
        } else if (buttonId instanceof String[] && "aug".equals(((String[]) buttonId)[0])) {
            MarketAPI m = CAAuto.market(((String[]) buttonId)[2]);
            if (m != null && Global.getSettings().getModManager().isModEnabled(CAAugments.MOD)) {
                try { String err = CAAugments.applyNow(m); if (err != null) CALog.add(m.getName() + ": station augments: " + err); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment action failed", t); CALog.add(m.getName() + ": station augments: that failed; see starsector.log", true); }
            }
        } else if (buttonId instanceof String[]) {
            String[] a = (String[]) buttonId;
            MarketAPI m = CAAuto.market(a[1]);
            CAData.Colony c = d.colony(a[1]);
            if ("profile".equals(a[0])) {
                CAData.Profile[] all = CAData.Profile.values();
                c.profile = all[(c.profile.ordinal() + 1) % all.length];
            } else if ("mode".equals(a[0])) {
                CAData.Mode[] all = CAData.Mode.values();
                c.mode = all[(c.mode.ordinal() + 1) % all.length];
                if ((c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO) && !d.settings.setupDone) {
                    CASetupUI.applyRecommended(d.settings);
                    d.settings.setupDone = true;
                    CALog.add("Recommended settings applied; review them on the Settings page of the Colony Autopilot intel", true);
                }
            } else if ("approve".equals(a[0]) && m != null) {
                String err = CAAuto.approve(m);
                if (err != null) CALog.add(m.getName() + ": " + err, true);
            } else if ("reject".equals(a[0]) && m != null) {
                CAAuto.reject(m);
            }
        }
        ui.updateUIForItem(this);
    }
}
