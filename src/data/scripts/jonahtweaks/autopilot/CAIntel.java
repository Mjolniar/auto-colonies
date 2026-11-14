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
import com.fs.starfarer.api.ui.LabelAPI;
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
 * 0.3.0.0 (WS-F2): three more pages. Expansion (CAExpansionPage, always listed), War (CAWarPage, with Nexerelin on; it
 * replaces the War section of Mod features) and Factions (CAFactionsPage, under Autopilot; the settings screen's Faction
 * stances link opens it). Buttons that ask first: the War page's (CAWar.needsConfirm), Reset all to Auto on the Factions
 * page and Found now on the Expansion page.
 */
public class CAIntel extends BaseIntelPlugin {
    static final String RESCAN = "jt_ca_rescan";
    /** 0.3.0.0 (A4 fix): the Colonies page's Refresh picks button (CAEngine.refreshAll). */
    static final String REFRESH = "jt_ca_refresh";
    /** 0.3.1.0: the Log page's Show and Hide button for the Thinking list. */
    static final String THINKING = "jt_ca_thinking";
    /** 0.3.3.0: Approve or Reject one decision by its key ({APPR, key}), or all of them. */
    static final String APPR = "appr", REJT = "rejt", APPR_ALL = "apprall", REJT_ALL = "rejtall";
    public static final String TAG = "Colony Autopilot";
    /** UI handles are never saved with the game. */
    transient TooltipMakerAPI lastTooltip;

    public static void ensure() {
        com.fs.starfarer.api.campaign.comm.IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (!mgr.hasIntelOfClass(CAIntel.class)) mgr.addIntel(new CAIntel(), true);
        // before 0.2.8.0 it was marked important; it has its own filter now
        com.fs.starfarer.api.campaign.comm.IntelInfoPlugin i = mgr.getFirstIntel(CAIntel.class);
        if (i != null && i.isImportant()) i.setImportant(false);
    }

    @Override protected String getName() { return "Colony Autopilot"; }
    @Override public String getIcon() { return "graphics/icons/markets/headquarters.png"; }
    @Override public boolean hasLargeDescription() { return true; }
    @Override public boolean hasSmallDescription() { return false; }
    @Override public boolean shouldRemoveIntel() { return false; }
    @Override public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (!(buttonId instanceof String[]) || ((String[]) buttonId).length < 2) return false;
        String[] a = (String[]) buttonId;
        try {
            // 0.3.0.0 (WS-E): the War page's declarations, bombardment authorization, Recall all and Stand down ask first
            if ("war".equals(a[0])) return Global.getSettings().getModManager().isModEnabled(CAWar.MOD) && CAWar.needsConfirm(a[1]);
            // 0.3.0.0 (WS-F2): Reset all to Auto (Factions page) and Found now (Expansion page) ask first
            if (CAFactionsPage.ID.equals(a[0])) return CAFactionsPage.needsConfirm(a[1]);
            if ("expand".equals(a[0])) return CAExpansionPage.needsConfirm(a[1]);
        } catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "confirmation check failed", e); }
        return false;
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (!(buttonId instanceof String[]) || ((String[]) buttonId).length < 2) return;
        String[] a = (String[]) buttonId;
        try {
            if ("war".equals(a[0]) && Global.getSettings().getModManager().isModEnabled(CAWar.MOD)) CAWar.confirmPrompt(prompt, a[1], a.length > 2 ? a[2] : "");
            else if (CAFactionsPage.ID.equals(a[0])) CAFactionsPage.confirmPrompt(prompt, a[1], CAData.settings());
            else if ("expand".equals(a[0])) CAExpansionPage.confirmPrompt(prompt, a[1], a.length > 2 ? a[2] : null, CAData.get(), CAData.settings());
        } catch (Throwable e) {
            // 0.3.0.0 (WS-F2): a prompt that fails still opens, with a line instead of the details
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "confirmation text failed", e);
            prompt.addPara("Details: " + CATheme.NOT_SHOWN, 0f);
        }
    }
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

    /**
     * The pages in the list on the left: always Colonies, Storage, Colony crises, Expansion, Settings, Factions, Log and
     * Diagnostics, plus War with Nexerelin on and Mod features when one is in use.
     */
    static List<String[]> pages(CAData data, CASettings s, List<MarketAPI> markets) {
        List<String[]> p = new ArrayList<String[]>();
        // 0.3.6.0 story mode: the Story page first; while dormant, it and Diagnostics are all there is
        if (CASync.on()) p.add(new String[]{"story", CASyncPage.navLabel()});
        if (CASync.dormant()) {
            int iss = CADiagnostics.count(CADiagnostics.Level.ANALYZED) + CADiagnostics.count(CADiagnostics.Level.QUARANTINED);
            p.add(new String[]{"diag", iss > 0 ? "Diagnostics (" + iss + ")" : "Diagnostics"});
            return p;
        }
        int pending = CAApprovals.collect().size();   // 0.3.3.0: every kind of decision
        p.add(new String[]{"colonies", pending > 0 ? "Colonies (" + pending + " to approve)" : "Colonies"});
        p.add(new String[]{"storage", "Autopilot Storage"});
        p.add(new String[]{"crises", CACrisis.pending() != null ? "Colony crises (one coming)" : "Colony crises"});
        p.add(new String[]{CAExpansionPage.PAGE, CAExpansionPage.label(data)});   // 0.3.0.0 (WS-F2)
        // 0.3.0.0 (WS-F2): the War page, at every War level (Stand down and the stances are there); CAWarPolicy has no Nexerelin classes
        if (Global.getSettings().getModManager().isModEnabled(CAWar.MOD)) p.add(new String[]{"war", "War (" + CAWarPolicy.levelName(s.warLevel) + ")"});
        if (!modSections(s, markets).isEmpty()) p.add(new String[]{"mods", "Mod features"});
        int issues = CADiagnostics.count(CADiagnostics.Level.ANALYZED) + CADiagnostics.count(CADiagnostics.Level.QUARANTINED);
        p.add(new String[]{null, "Autopilot"});
        p.add(new String[]{"settings", "Settings"});
        p.add(new String[]{CAFactionsPage.PAGE, "Factions"});   // 0.3.0.0 (WS-F2): the stance table, also the settings screen's link
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
        if (mm.isModEnabled(CAAutonomy.MOD)) l.add("autonomy");   // always: its Grant and Take back buttons work in any mode
        // 0.3.0.0 (WS-F2): war has its own page now (pages, CAWarPage)
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
            CATheme.heading(t, "Settlement (RAT)", 0f);
            CASettlement.render(t, data, s);
        } else if (id.equals("agents") && mm.isModEnabled(CASpies.MOD)) {
            CASpies.render(t, data, s);
        } else if (id.equals("autonomy") && mm.isModEnabled(CAAutonomy.MOD)) {
            CAAutonomy.render(t, data, s);
        } else if (id.equals("augments") && mm.isModEnabled(CAAugments.MOD)) {
            CATheme.heading(t, "Station augments", 0f);
            CAAugments.renderEmpire(t, data, s);
        } else if (id.equals("contracts") && mm.isModEnabled(CAContracts.MOD)) {
            CATheme.heading(t, "Trade contracts (AotD)", 0f);
            CAContracts.render(t, data, s);
        } else if (id.equals("mega") && mm.isModEnabled(CAHypershunt.MOD)) {
            CATheme.heading(t, "Pluto and Nidavelir (AotD)", 0f);
            CAMegastructures.renderEmpire(t, data, s);
        } else if (id.equals("shunt") && mm.isModEnabled(CAHypershunt.MOD)) {
            CATheme.heading(t, "Coronal Hypershunt (AotD)", 0f);
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

        // the grid and neon frames (0.2.41.0): drawn by an empty panel added first, so it sits behind and the buttons stay the intel's
        CustomPanelAPI backdrop = panel.createCustomPanel(width, height, new CATheme.Frame(CANav.W, CANav.GAP));
        panel.addComponent(backdrop).inTL(0f, 0f);
        TooltipMakerAPI nav = panel.createUIElement(CANav.W, height, false);
        nav.addTitle("AUTOPILOT\\\\ CORE");
        int auto = 0;
        for (MarketAPI m : markets) if (data.colony(m.getId()).mode == CAData.Mode.AUTO) auto++;
        CATheme.readout(nav, 2f, "Online", auto + " of " + markets.size() + " Auto");
        CANav.render(nav, pages, page);
        panel.addUIElement(nav).inTL(0f, 0f);

        TooltipMakerAPI t = panel.createUIElement(width - CANav.W - CANav.GAP, height, true);
        long t0 = System.nanoTime();
        CAEngine.drawing(true);   // 0.3.0.0 (A4 fix, 07 M6): nothing drawn here evaluates a colony (CAAugments' slot value included)
        try { renderPage(t, data, s, markets, width - CANav.W - CANav.GAP); }
        catch (Throwable e) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "empire panel page " + page + " failed", e);
            t.addPara(CATheme.NOT_SHOWN, Misc.getGrayColor(), 6f);
        } finally { CAEngine.drawing(false); }
        CAPerf.record("empire panel: " + page, System.nanoTime() - t0);   // 0.3.0.0 (A4): what a page costs to draw
        panel.addUIElement(t).inTL(CANav.W + CANav.GAP, 0f);
        try { if (t.getExternalScroller() != null) t.getExternalScroller().setYOffset(scrollY); } catch (Throwable ignored) { }
        lastTooltip = t;
        lastPage = page;
    }

    void renderPage(TooltipMakerAPI t, CAData data, CASettings s, List<MarketAPI> markets, float width) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();

        if (CASync.dormant() && !page.equals("diag")) page = "story";   // 0.3.6.0 story mode
        if (!CASync.on() && page.equals("story")) page = "colonies";
        if (page.equals("story")) { CASyncPage.render(t, width); return; }
        if (page.equals("colonies")) {
            // 0.3.0.0 (WS-F2, research 04 section 7): shorter
            t.addPara("Colony panels: the Autopilot button on each colony screen.", gray, 0f);   // 0.3.0.0 (WS-F3 fix pass 3)
            CATheme.heading(t, "Budget", 12f);
            t.addPara("Budget: %s of %s spent (%s fees), %s saved, %s free. Reserve %s.", 6f, hl,
                    Misc.getDGSCredits(s.spent), Misc.getDGSCredits(CABudget.limit(s)), Misc.getDGSCredits(s.fees),
                    Misc.getDGSCredits(CABudget.saved(s)), Misc.getDGSCredits(CABudget.available(s)), Misc.getDGSCredits(s.reserve));
            if (CABudget.paused(s)) t.addPara(CATheme.PAUSED, Misc.getNegativeHighlightColor(), 4f);   // 0.3.0.0 (WS-F3 fix pass 3): one text with the colony panel
            // 0.3.0.0 (WS-F2): the share is the Spending preset's (CAPresets), so the line no longer names 10%
            t.addPara("New upkeep this month: %s of %s (%s).", 4f, hl, Misc.getDGSCredits(s.upkeepAdded), Misc.getDGSCredits(CABudget.upkeepCap(s)),
                    s.upkeepCapAuto ? "automatic, at least " + Misc.getDGSCredits(CABudget.UPKEEP_FLOOR) : "fixed");
            // 0.3.0.0 (B5, research 03 F4): colony net runs the budget; cash flow (the whole report) is only for what you can afford
            t.addPara("Colony net: %s a month (%s-month median). Cash flow: %s.", 4f, hl,
                    Misc.getDGSCredits(CABudget.netIncome(s)), String.valueOf(CABudget.NET_MONTHS), Misc.getDGSCredits(CABudget.cashFlow(s)));
            // 0.3.0.0 (B3): the planner's last pass, what it's saving up for and the upkeep room it keeps
            try { for (String line : CAPlanner.status()) t.addPara(CALog.esc(line), gray, 4f); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "planner status line failed", e); }
            try { CASurplus.renderEmpire(t, s); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "surplus line failed", e); }
            // 0.3.0.0 (C3): the money ladder: tier, pool and rung allowances, proposals with Approve and Reject
            try { CARich.render(t); CAShipyard.render(t); CAStockpile.render(t); CAMilitary.render(t); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "money ladder lines failed", e); }
            try { CACapital.renderEmpire(t, s); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "capital line failed", e); }
            // 0.3.2.0 (Batch A): the production floors: each one met or how far off, the producers planned, where the parts are
            try { CAFloors.render(t); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "production floor lines failed", e); }

            // 0.3.3.0: every decision waiting, of every kind, grouped by colony (CAApprovals); the summary pop-up shows the same list
            List<CAApprovals.Item> waiting = CAApprovals.collect();
            if (!waiting.isEmpty()) {
                CATheme.heading(t, CATheme.APPROVAL, 12f);   // 0.3.0.0 (WS-F3 fix pass 3): one text with the colony panel
                ButtonAPI all = t.addButton("Approve all", new String[]{APPR_ALL, ""}, hl, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 6f);
                ButtonAPI none = t.addButton("Reject all", new String[]{REJT_ALL, ""}, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 0f);
                none.getPosition().rightOfTop(all, 6f);
                CALog.endRow(t, all);
                for (Map.Entry<String, List<CAApprovals.Item>> g : CAApprovals.group(waiting).entrySet()) {
                    t.addPara("%s", 8f, hl, g.getKey());
                    for (CAApprovals.Item it : g.getValue()) {
                        ButtonAPI a = t.addButton("Approve", new String[]{APPR, it.key}, hl, dark, Alignment.MID, CutStyle.TL_BR, 90f, 20f, 4f);
                        ButtonAPI r = t.addButton("Reject", new String[]{REJT, it.key}, base, dark, Alignment.MID, CutStyle.TL_BR, 90f, 20f, 0f);
                        r.getPosition().rightOfTop(a, 4f);
                        LabelAPI l = t.addPara("%s", 0f, Misc.getTextColor(), CAApprovalDialog.line(it));
                        l.getPosition().rightOfMid(r, 10f);
                        CALog.endRow(t, a);
                    }
                }
            }

            CATheme.heading(t, "Colonies", 12f);
            if (markets.isEmpty()) t.addPara("No colonies yet.", gray, 8f);
            else {
                // 0.3.0.0 (A4 fix, 07 M6): the picks below are what the daily pass kept; this scores the out-of-date ones now
                ButtonAPI rb = t.addButton("Refresh picks", REFRESH, base, dark, Alignment.MID, CutStyle.TL_BR, 140f, 22f, 6f);
                com.fs.starfarer.api.ui.LabelAPI note = t.addPara("Scored daily.", gray, 0f);
                note.getPosition().rightOfMid(rb, 10f);
                CALog.endRow(t, rb);
            }
            for (MarketAPI m : markets) {
                CAData.Colony c = data.colony(m.getId());
                String top = "nothing buildable";
                try {
                    if (CAEngine.isOutpost(m)) top = "Nexerelin outpost, left alone";
                    else if (c.mode == CAData.Mode.OFF) top = "autopilot off";
                    else if (CAHypershunt.is(m)) top = CAHypershunt.summary(m);
                    else {
                        // 0.3.0.0 (A4 fix, 07 M6): what's kept for the colony's goal (the daily pass, or Refresh picks); drawing never evaluates
                        CAEngine.Kept k = CAEngine.kept(m, c.profile, data.settings.minStability, c.buildout && c.mode == CAData.Mode.AUTO);
                        if (k == null) top = CAEngine.scoreFailed(m) ? "not scored; see starsector.log"   // 0.3.0.0 (A4 fix)
                                : "not scored yet";
                        else {
                            CAEngine.Result r = k.result;
                            CAEngine.Pick p = null;
                            for (CAEngine.Pick q : r.picks) if (CAEngine.worthwhile(q)) { p = q; break; }
                            if (p != null) top = p.verb + " " + p.name + " (" + CAEngine.signed(p.fx[0]) + " a month)";
                            else if (!r.picks.isEmpty()) top = "nothing pays for itself yet";
                            if (r.blocked != null) top = r.blocked.toLowerCase() + "; next: " + top;
                            if (!k.current) top += " (out of date)";
                        }
                    }
                } catch (Throwable e) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "colony line failed for " + m.getName(), e);
                    top = "error, see Diagnostics";
                }
                String flags = (c.buildout ? ", fast buildout" : "") + (c.threatHigh ? ", threatened" : "") + (CASiege.held(m) ? ", under siege" : "");
                t.addPara("%s (size %s): %s, %s" + CALog.esc(flags), 12f, hl, m.getName(), "" + m.getSize(), c.profile.label, c.mode.label);
                t.addPara("Next: " + CALog.esc(top), gray, 2f);
                ButtonAPI b1 = t.addButton("Next goal", new String[]{"profile", m.getId()}, base, dark, Alignment.MID, CutStyle.TL_BR, 140f, 22f, 4f);
                ButtonAPI b2 = t.addButton("Next mode", new String[]{"mode", m.getId()}, base, dark, Alignment.MID, CutStyle.TL_BR, 140f, 22f, 0f);
                b2.getPosition().rightOfTop(b1, 6f);
                CALog.endRow(t, b1);
            }
        } else if (page.equals("storage")) {
            CATheme.heading(t, "Autopilot Storage", 0f);
            // 0.3.0.0 (WS-F2, research 04 section 7): 58 words down to 33
            t.addPara("Autopilot Storage: shipping costs a fee and days.", 6f);   // 0.3.0.0 (WS-F3 fix pass 3): shorter
            boolean any = false;
            for (CAStores.Store sto : CAStores.all()) {
                if (sto.cargo.isEmpty()) continue;
                any = true;
                java.util.List<String> parts = new java.util.ArrayList<String>();
                for (com.fs.starfarer.api.campaign.CargoStackAPI stack : sto.cargo.getStacksCopy())
                    parts.add(stack.getDisplayName() + (stack.getSize() > 1f ? " x" + (int) stack.getSize() : ""));
                t.addPara("%s: " + CALog.esc(CAContracts.join(parts)), 3f, hl, sto.market.getName());
            }
            if (!any) t.addPara("Empty at every colony.", gray, 4f);
            for (CAStores.Shipment sh : data.shipments()) {
                MarketAPI to = CAAuto.market(sh.to);
                float left = Math.max(0f, sh.days - Global.getSector().getClock().getElapsedDaysSince(sh.sent));
                t.addPara("On the way: %s to %s, %s days left.", 3f, hl, (sh.qty > 1 ? sh.qty + " " : "") + sh.name(),
                        to == null ? "your biggest colony" : to.getName(), "" + (int) Math.ceil(left));
            }
            for (String o : CAObjectives.inTransit(data))   // stable locations (0.2.40.0)
                t.addPara("Construction crew: %s.", 3f, hl, o);
            if (Global.getSettings().getModManager().isModEnabled(CATasc.MOD))
                for (String o : CATascStations.inTransit(data)) t.addPara("Station crew: %s.", 3f, hl, o);   // TASC stations (0.2.40.1)
            for (CALogistics.Load l : CALogistics.inTransit()) {   // storage logistics (0.2.39.0)
                float left = Math.max(0f, l.days - Global.getSector().getClock().getElapsedDaysSince(l.sent));
                t.addPara("Storage logistics: %s to %s's Storage (" + CALog.esc(l.why) + "), %s days left.", 3f, hl,
                        CALogistics.describe(l), l.toName, "" + (int) Math.ceil(left));
            }
            t.addPara("AI cores: %s, inspection risk %s.", 6f, hl, s.useAICores ? "used" : "not used",
                    CASettings.CORE_RISK_NAMES[Math.max(0, Math.min(3, s.coreRisk))].toLowerCase());
            String none = CAItems.noInspections();
            if (none != null) t.addPara("No inspection can come now: %s.", 4f, hl, none);
            else t.addPara("Inspection line: %s points. Crisis points from cores: %s. Next inspection: %s fleet points.", 4f, hl,
                    "" + CAItems.safePoints(), "" + CACrisis.base().hegPoints, "" + (int) CAItems.nextInspectionFP());
            java.util.Set<MarketAPI> inspected = CAItems.inspected();
            for (MarketAPI m : CAItems.autoMarkets()) {
                int pts = CAItems.aiPoints(m), lim = CAItems.coreLimit(s, m, none);
                boolean coming = inspected.contains(m);
                if (pts <= 0 && !coming) continue;
                String why = coming ? "inspection coming" : CAItems.limitWhy(s, m, none, lim);
                t.addPara("%s: %s inspection points, %s", 3f, coming || pts > lim ? Misc.getNegativeHighlightColor() : hl,
                        CALog.esc(m.getName()), "" + pts, why);
            }
            if (Global.getSettings().getModManager().isModEnabled(CAResearch.MOD) && s.researchMode != 2) {
                t.addSpacer(16f);
                CATheme.heading(t, "Research (AotD)", 0f);
                try { CAResearch.render(t, data, s); }
                catch (Throwable e) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "research section failed", e);
                    t.addPara(CATheme.NOT_SHOWN, gray, 6f);
                }
            }
            if (Global.getSettings().getModManager().isModEnabled(CAResearch.MOD) && s.projectSupplyMode != 2) {
                t.addSpacer(16f);
                CATheme.heading(t, "Special projects (AotD)", 0f);
                try { CAProjects.render(t, s); }
                catch (Throwable e) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "special projects section failed", e);
                    t.addPara(CATheme.NOT_SHOWN, gray, 6f);
                }
            }
        } else if (page.equals("crises")) {
            CACrisis.render(t, data, s);
        } else if (page.equals(CAExpansionPage.PAGE)) {   // 0.3.0.0 (WS-F2)
            CAExpansionPage.render(t, data, s);
        } else if (page.equals("war")) {   // 0.3.0.0 (WS-F2): Nexerelin's classes load only with it on
            if (Global.getSettings().getModManager().isModEnabled(CAWar.MOD)) CAWarPage.render(t, data, s);
        } else if (page.equals(CAFactionsPage.PAGE)) {   // 0.3.0.0 (WS-F2)
            CAFactionsPage.render(t, data, s);
        } else if (page.equals("mods")) {
            boolean first = true;
            for (String id : modSections(s, markets)) {
                if (!first) t.addSpacer(16f);
                first = false;
                // one section failing leaves the others on the page
                try { renderMod(t, id, data, s); }
                catch (Throwable e) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "mod feature section " + id + " failed", e);
                    t.addPara(CATheme.NOT_SHOWN, gray, 6f);
                }
            }
        } else if (page.equals("settings")) {
            CATheme.heading(t, "Settings", 0f);
            if (!s.setupDone) t.addPara("Setup opens when a colony goes to Approve or Auto.", gray, 6f);
            CASetupUI.render(t, s, width);
        } else if (page.equals("log")) {
            CATheme.heading(t, "Log", 0f);
            if (s.log.isEmpty()) t.addPara("Nothing yet.", gray, 6f);
            for (int i = 0; i < Math.min(40, s.log.size()); i++) t.addPara(CALog.esc(s.log.get(i)), gray, 3f);
            // 0.3.1.0: Autopilot's thinking (CALog.think), collapsed until shown
            List<String> th = data.thinking();
            CATheme.heading(t, "Thinking", 12f);
            t.addButton(s.showThinking ? "Hide" : "Show (" + th.size() + ")", THINKING, base, dark, Alignment.MID, CutStyle.TL_BR, 140f, 22f, 6f);
            if (s.showThinking) {   // 0.3.2.0: a setting (Settings, Interface), copied from CAData.thinkingShown once
                if (th.isEmpty()) t.addPara("Nothing yet.", gray, 6f);
                for (int i = 0; i < Math.min(40, th.size()); i++) t.addPara(CALog.esc(th.get(i)), gray, 3f);
            }
        } else if (page.equals("diag")) {
            renderDiagnostics(t);
        }
    }

    /**
     * Mjolniar's mods that are installed, at a glance: enabled and version, warnings and errors this session (CALogWatch),
     * and each one's own status line, which the others write to sector memory as "$jonahs_status_" + mod id.
     */
    static void renderAuthorMods(TooltipMakerAPI t) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), bad = Misc.getNegativeHighlightColor();
        com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
        CATheme.heading(t, "Mjolniar's mods", 0f);
        for (String[] m : CALogWatch.MODS) {
            String ver = null;
            try { ver = mm.getModSpec(m[2]).getVersion(); } catch (Throwable ignored) { }
            if (ver == null) continue;   // not installed: nothing to show
            if (!mm.isModEnabled(m[2])) { t.addPara("%s: installed, not enabled", 6f, gray, m[1]); continue; }
            int[] c = CALogWatch.counts(m[0]);
            t.addPara("%s " + CALog.esc(ver) + ": %s warnings, %s errors this session", 6f, c[0] + c[1] > 0 ? bad : hl, m[1], "" + c[0], "" + c[1]);
            String status;
            if (m[2].equals(CASync.MOD_ID)) {
                status = CAItems.autoMarkets().size() + " Auto colonies, " + (CABreaker.tripped().size() + CAStep.broken().size()) + " actions paused, "
                        + CADiagnostics.count(CADiagnostics.Level.QUARANTINED) + " quarantined, surplus " + CASurplus.lineName(CAData.settings()).toLowerCase();
            } else {
                Object o = null;
                try { o = Global.getSector().getMemoryWithoutUpdate().get("$jonahs_status_" + m[2]); } catch (Throwable ignored) { }
                status = o instanceof String ? (String) o : "no status yet";
            }
            t.addPara("    " + CALog.esc(status), gray, 2f);
            String last = CALogWatch.last(m[0]);
            if (last != null) t.addPara("    Latest: " + CALog.esc(last), bad, 2f);
        }
        float promised = CASurplus.committed();
        if (promised > 0f) t.addPara("Promised to production: %s.", 6f, hl, Misc.getDGSCredits(promised));
        t.addSpacer(12f);
    }

    static void renderDiagnostics(TooltipMakerAPI t) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        try { renderAuthorMods(t); }
        catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Mjolniar's mods section failed", e); }
        CATheme.heading(t, "Diagnostics", 0f);
        t.addPara("Last scan: %s. Probe-measured: %s. Not acted on: %s analyzed, %s quarantined. "
                        + "Report: saves/common/" + CADiagnostics.REPORT + ".data", 8f, hl,
                CADiagnostics.getLastScan(), "" + CADiagnostics.count(CADiagnostics.Level.MEASURED),
                "" + CADiagnostics.count(CADiagnostics.Level.ANALYZED), "" + CADiagnostics.count(CADiagnostics.Level.QUARANTINED));
        t.addButton("Rescan now", RESCAN, base, dark, Alignment.MID, CutStyle.TL_BR, 140f, 22f, 6f);
        try { renderPerf(t); }   // 0.3.0.0 (A4)
        catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "performance section failed", e); }
        // mod integrations: does each still find what it relies on after mod updates
        List<CACompat.Entry> ints = CACompat.installed();
        if (!ints.isEmpty()) {
            int ok = 0;
            for (CACompat.Entry e : ints) if (e.status == CACompat.Status.OK) ok++;
            t.addPara("Mod integrations: %s of %s OK.", 8f, hl, "" + ok, "" + ints.size());
            for (CACompat.Entry e : ints) {
                if (e.status == CACompat.Status.OK) continue;
                Color lc = e.status == CACompat.Status.NEWER ? hl : Misc.getNegativeHighlightColor();
                t.addPara("%s " + CALog.esc(e.version) + ": " + CALog.esc(CACompat.describe(e)), 4f, lc, e.name);
            }
        }
        // mod watch (0.2.38.0): each colony mod's verdict and its warnings in this game
        List<org.json.JSONObject> watch = CAModWatch.latest();
        if (!watch.isEmpty()) {
            t.addPara("Mod watch (weekly):", 8f);
            for (org.json.JSONObject o : watch) {
                String v = o.optString("verdict");
                Color lc = "OK".equals(v) ? Misc.getPositiveHighlightColor() : "WARN".equals(v) ? hl : Misc.getNegativeHighlightColor();
                t.addPara("%s " + CALog.esc(o.optString("name")) + ": " + CALog.esc(o.optString("summary")), 4f, lc, v);
                org.json.JSONArray cs = o.optJSONArray("checks");
                for (int i = 0; cs != null && i < cs.length(); i++) {
                    org.json.JSONObject c = cs.optJSONObject(i);
                    if (c == null || !("WARN".equals(c.optString("result")) || "FAIL".equals(c.optString("result")))) continue;
                    t.addPara("    " + CALog.esc(c.optString("text")), gray, 2f);
                }
            }
        }
        for (Map.Entry<String, String> e : CABreaker.tripped().entrySet())
            t.addPara("Paused: %s. No effect three times. Last: " + CALog.esc(e.getValue()) + ".", 4f, Misc.getNegativeHighlightColor(), e.getKey());
        for (Map.Entry<String, String> e : CAStep.broken().entrySet())
            t.addPara("Stopped this session: %s (" + CALog.esc(e.getValue()) + ")", 4f, Misc.getNegativeHighlightColor(), e.getKey());
        if (!CAConst.drift.isEmpty()) t.addPara("Mod values changed (new ones used): " + CALog.esc(String.join("; ", CAConst.drift)), gray, 4f);
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

    /**
     * 0.3.0.0 (A4, research 07 section 2 item 8): what the daily pass and its steps cost, measured this session (CAPerf), and
     * how often the day caches answered instead of working something out again.
     */
    static void renderPerf(TooltipMakerAPI t) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        CAPerf.Stat pass = CAPerf.get(CAPerf.PASS);
        List<CAPerf.Stat> top = CAPerf.top(8);
        if (pass == null && top.isEmpty()) return;
        CATheme.heading(t, "Performance", 12f);
        if (pass != null) t.addPara("Daily pass: %s ms last, %s average, %s max.", 6f,
                pass.max > CAPerf.SLOW_PASS_MS ? Misc.getNegativeHighlightColor() : hl, CAPerf.ms(pass.last), CAPerf.ms(pass.avg), CAPerf.ms(pass.max));
        for (CAPerf.Stat st : top)
            t.addPara("    %s: %s ms average, %s max, %s runs", 2f, gray, hl, st.name, CAPerf.ms(st.avg), CAPerf.ms(st.max), "" + st.runs);
        List<String> parts = new ArrayList<String>();
        for (CACache.Memo<?> mo : CACache.ALL) if (mo.hits + mo.misses > 0) parts.add(mo.name + " " + mo.hitPercent() + "%");
        if (CASimulator.hits + CASimulator.misses > 0)
            parts.add("probe " + Math.round(100.0 * CASimulator.hits / (CASimulator.hits + CASimulator.misses)) + "%");
        if (!parts.isEmpty()) t.addPara("Cache hits: " + CALog.esc(String.join(", ", parts)) + ".", gray, 4f);
        // 0.3.0.0 (A4 fix): pages only read kept scores; a colony scored while one drew is a bug, with its caller in starsector.log
        if (CAEngine.drawnEvals > 0) t.addPara("Scored while drawing: %s (should be 0).", 4f,
                Misc.getNegativeHighlightColor(), "" + CAEngine.drawnEvals);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        // every button goes through here: an error is logged and shown in the log instead of reaching the game (0.2.37.0)
        try { pressed(buttonId, ui); }
        catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "empire panel button failed", t);
            try { CALog.advise("Button failed. See starsector.log.", true); ui.updateUIForItem(this); } catch (Throwable ignored) { }
        }
    }

    /** 0.3.3.0: every click is tracked: a setting it changes is yours (CAModeTable), and keeps its value through mode changes. */
    void pressed(Object buttonId, IntelUIAPI ui) {
        CASettings s = CAData.settings();
        CAModeTable.clickStart(s);
        try { press(buttonId, ui); }
        finally { CAModeTable.clickEnd(s, buttonId); }
    }

    void press(Object buttonId, IntelUIAPI ui) {
        CAData d = CAData.get();
        String ladder = CARich.handle(buttonId);   // 0.3.0.0 (C3): the money ladder's Approve and Reject buttons
        if (ladder != null) {
            if (!ladder.isEmpty()) CALog.advise(ladder, true);
            ui.updateUIForItem(this);
            return;
        }
        if (CASync.handle(buttonId)) {
            // 0.3.6.0 story mode: the Story page's buttons; CASync keeps their state, the page redraws below
        } else if (CANav.page(buttonId) != null) {
            page = CANav.page(buttonId);
        } else if (THINKING.equals(buttonId)) {
            d.settings.showThinking = !d.settings.showThinking;   // 0.3.2.0: the setting the Settings page's Thinking list row shows
        } else if (RESCAN.equals(buttonId)) {
            CADiagnostics.runScan();
        } else if (REFRESH.equals(buttonId)) {
            CAEngine.refreshAll(d);   // 0.3.0.0 (A4 fix): a click scores the out-of-date colonies (no plan tracking); drawing never does
        } else if (CASetupUI.handle(buttonId, d.settings)) {
            if (!String.valueOf(buttonId).contains(":group:")) d.settings.setupDone = true;
        } else if (buttonId instanceof String[] && "agent".equals(((String[]) buttonId)[0])) {
            String[] a = (String[]) buttonId;
            if (Global.getSettings().getModManager().isModEnabled(CASpies.MOD)) {
                try { CASpies.handle(d, a[1], a[2]); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "agent action failed", t); CALog.advise("Agents: action failed. See starsector.log.", true); }
            }
        } else if (buttonId instanceof String[] && "auton".equals(((String[]) buttonId)[0])) {
            String[] a = (String[]) buttonId;
            if (Global.getSettings().getModManager().isModEnabled(CAAutonomy.MOD)) {
                try { CAAutonomy.handle(d, a[1], a[2]); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "autonomy action failed", t); CALog.advise("Autonomy: action failed. See starsector.log.", true); }
            }
        } else if (buttonId instanceof String[] && "settle".equals(((String[]) buttonId)[0])) {
            String[] a = (String[]) buttonId;
            if (Global.getSettings().getModManager().isModEnabled(CASettlement.MOD)) {
                try { CASettlement.handle(a[1], a[2], d, d.settings); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "settlement action failed", t); CALog.advise("Settlement: action failed. See starsector.log.", true); }
            }
        } else if (buttonId instanceof String[] && "contract".equals(((String[]) buttonId)[0])) {
            if (Global.getSettings().getModManager().isModEnabled(CAContracts.MOD)) {
                String err = CAContracts.accept(((String[]) buttonId)[2], "you");
                if (err != null) CALog.advise("Trade contract: " + err, true);
            }
        } else if (buttonId instanceof String[] && "research".equals(((String[]) buttonId)[0])) {
            if (Global.getSettings().getModManager().isModEnabled(CAResearch.MOD)) {
                String err = CAResearch.handle(((String[]) buttonId)[1], ((String[]) buttonId)[2]);
                if (err != null) CALog.advise("Research: " + err, true);
            }
        } else if (buttonId instanceof String[] && "hs".equals(((String[]) buttonId)[0])) {
            String[] a = (String[]) buttonId;
            MarketAPI m = CAAuto.market(a[2]);
            if (m != null && Global.getSettings().getModManager().isModEnabled(CAHypershunt.MOD)) {
                try { String msg = CAHypershunt.handle(m, a[1], a[3]); CALog.advise(m.getName() + ": " + msg); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "hypershunt action failed", t); CALog.advise(m.getName() + ": Hypershunt: action failed. See starsector.log.", true); }
            }
        } else if (buttonId instanceof String[] && "aug".equals(((String[]) buttonId)[0])) {
            MarketAPI m = CAAuto.market(((String[]) buttonId)[2]);
            if (m != null && Global.getSettings().getModManager().isModEnabled(CAAugments.MOD)) {
                try { String err = CAAugments.applyNow(m); if (err != null) CALog.advise(m.getName() + ": Station augments: " + err); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment action failed", t); CALog.advise(m.getName() + ": Station augments: action failed. See starsector.log.", true); }
            }
        } else if (buttonId instanceof String[] && "expand".equals(((String[]) buttonId)[0])) {
            String[] a = (String[]) buttonId;   // 0.3.0.0 (WS-D): Auto-expansion's buttons {"expand", action, id}
            try {
                String err = CAExpansion.handle(a[1], a[2], d, d.settings);
                if (err != null) CALog.advise("Auto-expansion: " + err, true);
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "expansion action failed", t);
                CALog.advise("Auto-expansion: action failed. See starsector.log.", true);
            }
        } else if (buttonId instanceof String[] && CAFactionsPage.ID.equals(((String[]) buttonId)[0])) {
            // 0.3.0.0 (WS-F2): the Factions page's stance steps and Reset all to Auto {"fac", ...}
            try { CAFactionsPage.handle((String[]) buttonId, d); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "stance change failed", t); CALog.advise("Factions: action failed. See starsector.log.", true); }
        } else if (buttonId instanceof String[] && "war".equals(((String[]) buttonId)[0])) {
            String[] a = (String[]) buttonId;   // 0.3.0.0 (WS-E): the War page's buttons {"war", action, arg}
            if (Global.getSettings().getModManager().isModEnabled(CAWar.MOD)) {
                try { CAWar.handle(d, a[1], a.length > 2 ? a[2] : ""); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "war action failed", t); CALog.advise("War: action failed. See starsector.log.", true); }
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
                d.setMode(c, all[(c.mode.ordinal() + 1) % all.length]);   // 0.3.3.0: a change clears the colony's waiting proposal
                if ((c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO) && !d.settings.setupDone && CALunaSync.keepsSetup()) {
                    d.settings.setupDone = true;   // 0.3.4.0: LunaLib's settings stay
                } else if ((c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO) && !d.settings.setupDone) {
                    CASetupUI.applyRecommended(d.settings);
                    d.settings.setupDone = true;
                    CALog.did("Settings: recommended values applied. Review them on the Settings page of the Colony Autopilot intel.", true);
                    CAModeTable.setEmpire(d.settings, c.mode);   // 0.3.3.0: the first setup sets the empire mode
                }
            } else if ("approve".equals(a[0]) && m != null) {
                String err = CAAuto.approve(m);
                if (err != null) CALog.advise(m.getName() + ": " + err, true);
            } else if ("reject".equals(a[0]) && m != null) {
                CAAuto.reject(m);
            } else if (APPR.equals(a[0]) || REJT.equals(a[0])) {   // 0.3.3.0: any kind of decision, by its key
                for (CAApprovals.Item it : CAApprovals.collect()) {
                    if (!it.key.equals(a[1])) continue;
                    String err = APPR.equals(a[0]) ? CAApprovals.approve(it) : CAApprovals.reject(it);
                    if (err != null) CALog.advise(it.place + ": " + err, true);
                }
            } else if (APPR_ALL.equals(a[0])) CAApprovals.approveAll(CAApprovals.collect());
            else if (REJT_ALL.equals(a[0])) CAApprovals.rejectAll(CAApprovals.collect());
        }
        ui.updateUIForItem(this);
    }
}
