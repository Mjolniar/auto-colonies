package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.VisualPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.*;

/**
 * 0.3.3.0: the approval summary (plans/2026-11-13-modes-rework.md section 3). Every decision waiting in one dialog, at most
 * once per interval (CASettings.approvalDays, default 60 days, -1 Off). The first one opens at the first safe moment after
 * a decision is waiting; a missed moment waits for the next safe one, never for the next interval. Safe: the campaign runs
 * (CAScript runs unpaused only), no dialog, menu, core screen or battle, the UI shown, not in a hyperspace jump or fast
 * advance, 3 seconds quiet and 10 seconds after a load. Grouped by colony, one line per decision; options Approve all,
 * Choose each (a panel with Approve and Reject per item), Reject all and Later. Approvals go through CAApprovals (credit
 * reserve, then the kind's own checks).
 */
public class CAApprovalDialog implements InteractionDialogPlugin {
    static final String APPROVE_ALL = "ca:sum:all", REJECT_ALL = "ca:sum:none", CHOOSE = "ca:sum:choose", LATER = "ca:sum:later",
            ONE_YES = "ca:sum:yes", ONE_NO = "ca:sum:no", ONE_NEXT = "ca:sum:next";
    /** Item lines in the dialog's text before "N more under Choose each". */
    static final int MAX_LINES = 12;
    /** 0.3.3.5: the first this many decisions show their detail lines in the summary text; Choose each shows them all. */
    static final int DETAILED = 4;
    static final float W = 760f, H = 520f;
    static final long LOAD_GRACE_MS = 10000L, SAFE_MS = 3000L;
    /** The interval's choices on the Settings page (-1 Off). */
    static final int[] DAYS = {-1, 15, 30, 45, 60, 90, 120, 180};
    static final int DEFAULT_DAYS = 60;

    // ---------------- trigger (session state) ----------------

    static float timer = 0f;
    static long safeSinceMs = 0L, loadedMs = 0L;

    public static void reset() { timer = 0f; safeSinceMs = 0L; loadedMs = 0L; }

    /** Pure: whether a summary is due: on, something waiting, and never shown or the interval passed (game timestamps). */
    static boolean due(int days, long last, long now, int count) {
        if (days <= 0 || count <= 0) return false;
        if (last == 0L) return true;
        if (now < last) return true;   // an earlier date than the last summary (a clock that went back): show it
        return CAMark.days(last, now) + 1e-3f >= days;
    }

    /** Pure: why now isn't a safe moment, or null. */
    static String unsafe(boolean campaign, boolean dialog, boolean menu, boolean coreTab, boolean hidden, boolean noFleet,
                         boolean battle, boolean transition, boolean fast) {
        if (!campaign) return "not in the campaign";
        if (dialog) return "a dialog is open";
        if (menu) return "a menu is open";
        if (coreTab) return "a screen is open";
        if (hidden) return "the UI is hidden";
        if (noFleet) return "no player fleet";
        if (battle) return "in a battle";
        if (transition) return "in a hyperspace jump";
        if (fast) return "fast advance";
        return null;
    }

    static String unsafe() {
        SectorAPI sec = Global.getSector();
        CampaignUIAPI ui = sec.getCampaignUI();
        CampaignFleetAPI f = sec.getPlayerFleet();
        return unsafe(Global.getCurrentState() == GameState.CAMPAIGN, ui.isShowingDialog() || ui.getCurrentInteractionDialog() != null,
                ui.isShowingMenu(), ui.getCurrentCoreTab() != null, ui.isHideUI(), f == null, f != null && f.getBattle() != null,
                f != null && f.isInHyperspaceTransition(), sec.isInFastAdvance() || sec.isInNewGameAdvance());
    }

    /** CAScript, every frame while the campaign runs: at most once a second. */
    public static void check(float amount) {
        timer -= amount;
        if (timer > 0f) return;
        timer = 1f;
        long ms = System.currentTimeMillis();
        if (loadedMs == 0L) loadedMs = ms;
        CAData d = CAData.get();
        CASettings s = d.settings;
        if (s.approvalDays <= 0) return;
        if (unsafe() != null) { safeSinceMs = 0L; return; }
        if (safeSinceMs == 0L) safeSinceMs = ms;
        if (ms - loadedMs < LOAD_GRACE_MS || ms - safeSinceMs < SAFE_MS) return;
        long now = CAMark.now();
        if (!due(s.approvalDays, d.approvalLast, now, 1)) { timer = 30f; return; }
        List<CAApprovals.Item> items = CAApprovals.collect();
        if (items.isEmpty()) { timer = 10f; return; }
        if (Global.getSector().getCampaignUI().showInteractionDialog(new CAApprovalDialog(items), Global.getSector().getPlayerFleet())) {
            d.approvalLast = now;
            CALog.think("Approvals: summary shown, " + items.size() + " decisions.");
        }
    }

    /** The Settings row's next interval. */
    static int nextDays(int cur) {
        for (int i = 0; i < DAYS.length; i++) if (DAYS[i] == cur) return DAYS[(i + 1) % DAYS.length];
        return DEFAULT_DAYS;
    }

    static String daysName(int days) { return days <= 0 ? "Off" : "Every " + days + " days"; }

    // ---------------- the dialog ----------------

    final List<CAApprovals.Item> items;
    /** What happened to each decided item: "Approved", "Rejected" or the reason it didn't start. */
    final Map<String, String> result = new LinkedHashMap<String, String>();
    final Set<String> done = new HashSet<String>();
    InteractionDialogAPI dialog;
    int one = -1;   // the item shown in the one-at-a-time fallback

    CAApprovalDialog(List<CAApprovals.Item> items) { this.items = new ArrayList<CAApprovals.Item>(items); }

    List<CAApprovals.Item> open() {
        List<CAApprovals.Item> out = new ArrayList<CAApprovals.Item>();
        for (CAApprovals.Item it : items) if (!done.contains(it.key)) out.add(it);
        return out;
    }

    /** One line per item: "Build Heavy Industry: 420,000 with fee. +12,000 a month." Pure apart from credit formatting. */
    static String line(CAApprovals.Item it) {
        StringBuilder b = new StringBuilder(it.title == null ? it.kind : it.title);
        if (it.total() > 0f) b.append(": ").append(Misc.getDGSCredits(it.total())).append(it.fee > 0f ? " with fee" : "");
        b.append('.');
        if (it.monthly > 0f) b.append(" +").append(Misc.getDGSCredits(it.monthly)).append(" a month.");
        return b.toString();
    }

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.setPromptText("Colony Autopilot");
        try {
            showVisual();
            summary();
            options();
        } catch (Throwable t) {   // 0.3.6.2: a throw here left the dialog open with no way out
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "approval summary failed to open", t);
            dialog.getOptionPanel().clearOptions();
            dialog.getTextPanel().addPara("Summary unavailable.");
            dialog.getOptionPanel().addOption("Close", LATER);
            dialog.getOptionPanel().setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
        }
    }

    // ---------------- 0.3.3.5: the picture, like a vanilla event ----------------

    /** Pure: the vanilla illustration for a kind of decision (all present in 0.98a and 0.98.5a). */
    static String imageFor(String kind) {
        if (kind == null) return "orbital_construction";
        switch (kind) {
            case "autonomy": return "city_from_above";
            case "expand": case "settle": return "vacuum_colony";
            case "hypershunt": case "restore": case "megastructure": return "terran_orbit";
            case "augments": return "orbital";
            case "shipyard": case "stock": return "industrial_megafacility";
            default: return "orbital_construction";
        }
    }

    /** Pure: the illustration for most of these decisions. */
    static String illustration(List<CAApprovals.Item> items) {
        Map<String, Integer> n = new HashMap<String, Integer>();
        String best = "orbital_construction";
        int top = 0;
        for (CAApprovals.Item it : items) {
            String id = imageFor(it.kind);
            int c = n.containsKey(id) ? n.get(id) + 1 : 1;
            n.put(id, c);
            if (c > top) { top = c; best = id; }
        }
        return best;
    }

    /** Pure: the market id when every decision is at that one colony, else null. */
    static String oneColony(List<CAApprovals.Item> items) {
        String id = null;
        for (CAApprovals.Item it : items) {
            if (it.marketId == null) return null;
            if (id == null) id = it.marketId;
            else if (!id.equals(it.marketId)) return null;
        }
        return id;
    }

    /** The colony's world when every decision is at one planet, else a vanilla illustration; no picture if both fail. */
    void showVisual() {
        try {
            VisualPanelAPI v = dialog.getVisualPanel();
            List<CAApprovals.Item> open = open();
            String only = oneColony(open);
            MarketAPI m = only == null ? null : Global.getSector().getEconomy().getMarket(only);
            if (m != null && m.getPrimaryEntity() instanceof PlanetAPI) { v.showPlanetInfo(m.getPrimaryEntity()); return; }
            v.showImagePortion("illustrations", illustration(open), 640, 400, 0, 0, 480, 300);
        } catch (Throwable t) {
            try { dialog.hideVisualPanel(); } catch (Throwable ignored) { }
        }
    }

    void summary() {
        TextPanelAPI t = dialog.getTextPanel();
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        CASettings s = CAData.settings();
        List<CAApprovals.Item> open = open();
        t.addPara("Decisions: %s. Credits %s. Reserve %s.", hl, String.valueOf(open.size()), Misc.getDGSCredits(CABuild.credits()),
                Misc.getDGSCredits(s.reserve));
        para(t, "Nothing changes until you choose, and Autopilot never spends below the reserve. Later keeps every decision open: this "
                + "summary comes back in " + Math.max(1, s.approvalDays) + " days, and the list stays on the Intel screen (Colony Autopilot, "
                + "Colonies page, Waiting for approval).", gray);
        int lines = 0, detailed = 0;
        for (Map.Entry<String, List<CAApprovals.Item>> g : CAApprovals.group(open).entrySet()) {
            if (lines >= MAX_LINES) break;
            para(t, g.getKey() + ":", hl);
            for (CAApprovals.Item it : g.getValue()) {
                if (lines++ >= MAX_LINES) break;
                para(t, "  " + line(it), Misc.getTextColor());
                if (detailed < DETAILED) {
                    detailed++;
                    for (String d : it.details) para(t, "    " + d, gray);
                }
            }
        }
        if (open.size() > MAX_LINES) para(t, (open.size() - MAX_LINES) + " more under Choose each.", gray);
        if (open.size() > DETAILED) para(t, "Choose each shows the details of every decision.", gray);
    }

    void options() {
        OptionPanelAPI o = dialog.getOptionPanel();
        o.clearOptions();
        List<CAApprovals.Item> open = open();
        if (!open.isEmpty()) {
            float total = 0f;
            for (CAApprovals.Item it : open) total += it.total();
            o.addOption("Approve all (" + open.size() + ", " + Misc.getDGSCredits(total) + ")", APPROVE_ALL);
            o.addOptionConfirmation(APPROVE_ALL, "Approve all " + open.size() + " for " + Misc.getDGSCredits(total) + " with fees? One that doesn't fit the reserve stays open.", "Approve all", "Back");
            o.addOption("Choose each (" + open.size() + ")", CHOOSE);
            o.addOption("Reject all", REJECT_ALL);
            o.addOptionConfirmation(REJECT_ALL, "Reject all " + open.size() + "? They come back after 30 days (expeditions 180).", "Reject all", "Back");
        }
        int days = Math.max(1, CAData.settings().approvalDays);
        o.addOption(open.isEmpty() ? "Close" : "Later (back in " + days + " days)", LATER);
        o.setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    public void optionSelected(String text, Object data) {
        try { select(data); }
        catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "approval summary option failed", t);
            dialog.dismiss();
        }
    }

    void select(Object data) {
        TextPanelAPI t = dialog.getTextPanel();
        if (LATER.equals(data)) { dialog.dismiss(); return; }
        if (APPROVE_ALL.equals(data)) {
            List<CAApprovals.Item> open = open();
            Map<String, String> fail = CAApprovals.approveAll(open);
            for (CAApprovals.Item it : open) { done.add(it.key); result.put(it.key, fail.containsKey(it.key) ? fail.get(it.key) : "Approved"); }
            report(t, open);
            options();
            return;
        }
        if (REJECT_ALL.equals(data)) {
            List<CAApprovals.Item> open = open();
            Map<String, String> fail = CAApprovals.rejectAll(open);
            for (CAApprovals.Item it : open) { done.add(it.key); result.put(it.key, fail.containsKey(it.key) ? fail.get(it.key) : "Rejected"); }
            report(t, open);
            options();
            return;
        }
        if (CHOOSE.equals(data)) {
            try { dialog.showCustomDialog(W, H, new Panel(this)); }
            catch (Throwable e) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "approval panel failed to open; one decision at a time", e);
                one = 0;
                oneByOne();
            }
            return;
        }
        if (ONE_YES.equals(data) || ONE_NO.equals(data) || ONE_NEXT.equals(data)) {
            List<CAApprovals.Item> open = open();
            if (one >= 0 && one < open.size()) {
                CAApprovals.Item it = open.get(one);
                if (ONE_YES.equals(data)) decide(it, true);
                else if (ONE_NO.equals(data)) decide(it, false);
                else one++;
                para(t, it.place + ": " + (it.title == null ? it.kind : it.title) + ": "
                        + (result.containsKey(it.key) ? result.get(it.key) : "next") + ".", Misc.getTextColor());
            }
            oneByOne();
        }
    }

    /** Approves or rejects one item and records what happened. */
    void decide(CAApprovals.Item it, boolean approve) {
        String r = approve ? CAApprovals.approve(it) : CAApprovals.reject(it);
        done.add(it.key);
        result.put(it.key, r == null ? (approve ? "Approved" : "Rejected") : r);
    }

    void report(TextPanelAPI t, List<CAApprovals.Item> list) {
        for (CAApprovals.Item it : list) {
            String r = result.get(it.key);
            boolean ok = "Approved".equals(r) || "Rejected".equals(r);
            para(t, it.place + ": " + (it.title == null ? it.kind : it.title) + ": " + r + ".", ok ? Misc.getTextColor() : Misc.getNegativeHighlightColor());
        }
    }

    /** The fallback without the panel: one decision at a time with Approve, Reject and Next. */
    void oneByOne() {
        OptionPanelAPI o = dialog.getOptionPanel();
        o.clearOptions();
        List<CAApprovals.Item> open = open();
        if (one < 0 || one >= open.size()) { one = -1; options(); return; }
        CAApprovals.Item it = open.get(one);
        para(dialog.getTextPanel(), (one + 1) + " of " + open.size() + ". " + it.place + ": " + line(it), Misc.getHighlightColor());
        for (String d : it.details) para(dialog.getTextPanel(), "    " + d, Misc.getGrayColor());
        o.addOption("Approve", ONE_YES);
        o.addOption("Reject", ONE_NO);
        o.addOption("Next", ONE_NEXT);
        o.addOption("Later", LATER);
        o.setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    /** The per-item panel closed: what was decided goes into the text, then the options again. */
    void panelClosed() {
        List<CAApprovals.Item> decided = new ArrayList<CAApprovals.Item>();
        for (CAApprovals.Item it : items) if (done.contains(it.key) && !reported.contains(it.key)) decided.add(it);
        reported.addAll(done);
        if (!decided.isEmpty()) report(dialog.getTextPanel(), decided);
        options();
    }

    final Set<String> reported = new HashSet<String>();

    /** A text panel line in one colour; the text goes in as an argument, so a % in it is shown as it is. */
    static void para(TextPanelAPI t, String text, Color c) { t.addPara("%s", c, c, text == null ? "" : text); }

    public void optionMousedOver(String text, Object data) { }
    public void advance(float amount) { }
    public void backFromEngagement(EngagementResultAPI result) { }
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return null; }

    // ---------------- Choose each: one row per decision ----------------

    static final class Panel extends BaseCustomDialogDelegate {
        final CAApprovalDialog owner;
        CustomPanelAPI root, content;
        boolean dirty = false;
        final CATheme.Frame plugin = new CATheme.Frame(0f, 0f) {
            @Override public void buttonPressed(Object id) {
                try { pressed(id); }
                catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "approval panel button failed", t); }
                dirty = true;
            }
            @Override public void advance(float amount) { if (dirty) { dirty = false; build(); } }
        };

        Panel(CAApprovalDialog owner) { this.owner = owner; }

        @Override public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) { root = panel; build(); }
        @Override public boolean hasCancelButton() { return false; }
        @Override public String getConfirmText() { return "Close"; }
        @Override public CustomUIPanelPlugin getCustomPanelPlugin() { return plugin; }
        @Override public void customDialogConfirm() { closed(); }
        @Override public void customDialogCancel() { closed(); }

        void closed() {   // 0.3.6.2: guarded, these are called by the game
            try { owner.panelClosed(); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "approval summary panel close failed", t); owner.dialog.dismiss(); }
        }

        void pressed(Object id) {
            if (APPROVE_ALL.equals(id)) {
                List<CAApprovals.Item> open = owner.open();
                Map<String, String> fail = CAApprovals.approveAll(open);
                for (CAApprovals.Item it : open) { owner.done.add(it.key); owner.result.put(it.key, fail.containsKey(it.key) ? fail.get(it.key) : "Approved"); }
            } else if (REJECT_ALL.equals(id)) {
                List<CAApprovals.Item> open = owner.open();
                Map<String, String> fail = CAApprovals.rejectAll(open);
                for (CAApprovals.Item it : open) { owner.done.add(it.key); owner.result.put(it.key, fail.containsKey(it.key) ? fail.get(it.key) : "Rejected"); }
            } else if (id instanceof String[]) {
                String[] a = (String[]) id;
                for (CAApprovals.Item it : owner.items) {
                    if (!it.key.equals(a[1]) || owner.done.contains(it.key)) continue;
                    owner.decide(it, "a".equals(a[0]));
                }
            }
        }

        void build() {
            try { draw(); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "approval panel failed to draw", t); }
        }

        void draw() {
            if (content != null) root.removeComponent(content);
            content = root.createCustomPanel(W, H, plugin);
            Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor(),
                    bad = Misc.getNegativeHighlightColor();
            TooltipMakerAPI t = content.createUIElement(W, H, true);
            t.addTitle("DECISIONS");
            CASettings s = CAData.settings();
            List<CAApprovals.Item> open = owner.open();
            t.addPara("Credits %s. Reserve %s.", 6f, hl, Misc.getDGSCredits(CABuild.credits()), Misc.getDGSCredits(s.reserve));
            if (!open.isEmpty()) {
                ButtonAPI all = t.addButton("Approve all", APPROVE_ALL, hl, dark, Alignment.MID, CutStyle.TL_BR, 160f, 22f, 8f);
                ButtonAPI none = t.addButton("Reject all", REJECT_ALL, base, dark, Alignment.MID, CutStyle.TL_BR, 160f, 22f, 0f);
                none.getPosition().rightOfTop(all, 6f);
                CALog.endRow(t, all);
            }
            for (Map.Entry<String, List<CAApprovals.Item>> g : CAApprovals.group(owner.items).entrySet()) {
                CATheme.heading(t, g.getKey(), 10f);
                for (CAApprovals.Item it : g.getValue()) {
                    String r = owner.result.get(it.key);
                    if (r != null) {
                        boolean ok = "Approved".equals(r) || "Rejected".equals(r);
                        t.addPara(CALog.esc(line(it) + " " + r + "."), ok ? gray : bad, 4f);
                        continue;
                    }
                    ButtonAPI y = t.addButton("Approve", new String[]{"a", it.key}, hl, dark, Alignment.MID, CutStyle.TL_BR, 90f, 20f, 4f);
                    ButtonAPI n = t.addButton("Reject", new String[]{"r", it.key}, base, dark, Alignment.MID, CutStyle.TL_BR, 90f, 20f, 0f);
                    n.getPosition().rightOfTop(y, 4f);
                    LabelAPI l = t.addPara(CALog.esc(line(it)), 0f);
                    l.getPosition().rightOfMid(n, 10f);
                    CALog.endRow(t, y);
                    for (String d : it.details) t.addPara(CALog.esc(d), gray, 2f);   // 0.3.3.5: what it does, costs, why
                }
            }
            content.addUIElement(t).inTL(0f, 0f);
            root.addComponent(content).inTL(0f, 0f);
        }
    }
}
