package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The empire panel: every colony's profile, mode and top pick, plus Diagnostics. Lives under Colonies in the Intel screen. */
public class CAIntel extends BaseIntelPlugin {
    static final String RESCAN = "jt_ca_rescan";
    /** UI handles are never saved with the game. */
    transient TooltipMakerAPI lastTooltip;

    public static void ensure() {
        if (!Global.getSector().getIntelManager().hasIntelOfClass(CAIntel.class)) {
            CAIntel i = new CAIntel();
            Global.getSector().getIntelManager().addIntel(i, true);
            i.setImportant(true);
        }
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
        tags.add(Tags.INTEL_COLONIES);
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

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float scrollY = 0f;
        try { if (lastTooltip != null && lastTooltip.getExternalScroller() != null) scrollY = lastTooltip.getExternalScroller().getYOffset(); }
        catch (Throwable ignored) { }
        TooltipMakerAPI t = panel.createUIElement(width, height, true);
        CAData data = CAData.get();
        CASettings s = data.settings;
        CABudget.rollMonth(s);
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();

        t.addTitle("Colony Autopilot");
        t.addPara("Every colony starts in Advisor mode. Open a colony's own panel by right-clicking its Population & Infrastructure "
                + "on the colony screen.", 6f);

        t.addSectionHeading("Budget", Alignment.MID, 12f);
        t.addPara("This month: %s spent of %s (%s in fees). Available now: %s. Reserve kept: %s.", 6f, hl,
                Misc.getDGSCredits(s.spent), Misc.getDGSCredits(CABudget.limit(s)), Misc.getDGSCredits(s.fees),
                Misc.getDGSCredits(CABudget.available(s)), Misc.getDGSCredits(s.reserve));
        if (CABudget.paused(s)) t.addPara("Spending is paused: net income is negative.", Misc.getNegativeHighlightColor(), 4f);

        List<MarketAPI> markets = Misc.getPlayerMarkets(false);
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
                if (c.mode == CAData.Mode.OFF) top = "autopilot off";
                else {
                    CAEngine.Result r = CAEngine.evaluate(m, c.profile, data.minStability);
                    if (!r.picks.isEmpty()) {
                        CAEngine.Pick p = r.picks.get(0);
                        top = p.verb + " " + p.name + " (" + CAEngine.valueText(p.score) + ")";
                    }
                    if (r.blocked != null) top = r.blocked.toLowerCase() + "; next: " + top;
                }
            } catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "evaluate failed for " + m.getName(), e);
                top = "error, see Diagnostics";
            }
            CAPlan.Plan plan = CAPlan.build(m, c.profile);
            t.addPara("%s (size %s): %s, %s. Plan: %s, %s of %s done. Top pick: %s", 10f, hl, m.getName(), "" + m.getSize(),
                    c.profile.label, c.mode.label, plan.name, "" + plan.doneCount(m), "" + plan.steps.size(), top);
            ButtonAPI b1 = t.addButton("Next profile", new String[]{"profile", m.getId()}, base, dark, Alignment.MID, CutStyle.ALL, 140f, 22f, 4f);
            ButtonAPI b2 = t.addButton("Next mode", new String[]{"mode", m.getId()}, base, dark, Alignment.MID, CutStyle.ALL, 140f, 22f, 0f);
            b2.getPosition().rightOfTop(b1, 6f);
            CALog.endRow(t, b1);
        }

        t.addSectionHeading("Autopilot Storage", Alignment.MID, 16f);
        t.addPara("Dock at any of your colonies and open the Autopilot Storage tab to deposit colony items and AI cores. "
                + "Only Auto-mode colonies draw from it; nothing in your cargo or normal storage is ever used.", 6f);
        com.fs.starfarer.api.campaign.CargoAPI st = data.storage();
        if (st.isEmpty()) t.addPara("Empty.", gray, 4f);
        for (com.fs.starfarer.api.campaign.CargoStackAPI stack : st.getStacksCopy()) {
            t.addPara("%s x%s", 3f, hl, CALog.esc(stack.getDisplayName()), "" + (int) stack.getSize());
        }
        MarketAPI fort = CAItems.fortress(CAItems.autoMarkets());
        t.addPara("AI cores: %s. Next Hegemony inspection strength: %s fleet points. Fortress world: %s.", 6f, hl,
                s.useAICores ? "used" : "not used", "" + (int) CAItems.nextInspectionFP(),
                fort == null ? "none strong enough (cores capped at 7 points per colony)" : fort.getName() + " (" + (int) CAItems.defenseFP(fort) + " estimated)");

        if (Global.getSettings().getModManager().isModEnabled(CASettlement.MOD)) {
            t.addSectionHeading("Settlement (Random Assortment of Things)", Alignment.MID, 16f);
            try { CASettlement.render(t, data, s); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "settlement section failed", e); t.addPara("Settlement info unavailable; see starsector.log.", gray, 6f); }
        }
        if (Global.getSettings().getModManager().isModEnabled(CAAugments.MOD) && s.augmentMode != 2) {
            t.addSectionHeading("Station augments", Alignment.MID, 16f);
            try { CAAugments.renderEmpire(t, data, s); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment section failed", e); t.addPara("Station augment info unavailable; see starsector.log.", gray, 6f); }
        }
        if (Global.getSettings().getModManager().isModEnabled(CAContracts.MOD) && s.contractMode != 2) {
            t.addSectionHeading("Trade contracts (Ashes of the Domain)", Alignment.MID, 16f);
            CAContracts.render(t, data, s);
        }

        t.addSectionHeading("Settings", Alignment.MID, 16f);
        if (!s.setupDone) t.addPara("First-time setup hasn't run yet; it opens when you first switch a colony to Approve or Auto.", gray, 6f);
        CASetupUI.render(t, s, width);

        t.addSectionHeading("Log", Alignment.MID, 16f);
        if (s.log.isEmpty()) t.addPara("Nothing yet.", gray, 6f);
        for (int i = 0; i < Math.min(20, s.log.size()); i++) t.addPara(CALog.esc(s.log.get(i)), gray, 3f);

        t.addSectionHeading("Diagnostics", Alignment.MID, 16f);
        t.addPara("Last scan: %s. Supported content is planned with; %s mod buildings were measured on a probe colony and are scored "
                        + "from those measurements; %s analyzed and %s quarantined entries are never acted on. "
                        + "Full report: saves/common/" + CADiagnostics.REPORT + ".data", 8f, hl,
                CADiagnostics.getLastScan(), "" + CADiagnostics.count(CADiagnostics.Level.MEASURED),
                "" + CADiagnostics.count(CADiagnostics.Level.ANALYZED), "" + CADiagnostics.count(CADiagnostics.Level.QUARANTINED));
        t.addButton("Rescan now", RESCAN, base, dark, Alignment.MID, CutStyle.ALL, 140f, 22f, 6f);
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

        panel.addUIElement(t).inTL(0f, 0f);
        try { if (t.getExternalScroller() != null) t.getExternalScroller().setYOffset(scrollY); } catch (Throwable ignored) { }
        lastTooltip = t;
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        CAData d = CAData.get();
        if (RESCAN.equals(buttonId)) {
            CADiagnostics.runScan();
        } else if (CASetupUI.handle(buttonId, d.settings)) {
            if (!String.valueOf(buttonId).contains(":group:")) d.settings.setupDone = true;
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
                    CALog.add("Recommended settings applied; review them in the Settings section", true);
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
