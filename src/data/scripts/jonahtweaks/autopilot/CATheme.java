package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The autopilot's look and voice (0.2.41.0), styled as a colony AI's console.
 *
 * Voice: every log entry still goes to the panel log and starsector.log as the full sentence; on screen it starts with a
 * code, "BUILD / Hot Springs: Planetary Shield (750k)", in the game's normal text colors (0.2.41.1; 0.2.41.0's tags, ::
 * and // and player-color text were too hard to read). Settings, Interface: Announcement voice, Machine (default) or
 * Plain. Short ping lines report what it's weighing as it goes, at most one per thing a week, on screen only.
 *
 * Look: section headings are left-aligned "// TITLE" bars, buttons cut at two corners, and the empire and colony panels
 * sit on a faint grid with cut-corner neon frames around the page list and the page, all in your faction's UI colors.
 */
public final class CATheme {
    private CATheme() { }

    // ---------------- colors: your faction's UI colors, as the game's own panels use ----------------

    static Color glow() { return Misc.getBrightPlayerColor(); }
    static Color line() { return Misc.getBasePlayerColor(); }
    static Color dim() { return Misc.getDarkPlayerColor(); }

    // ---------------- headings and buttons ----------------

    /** A section heading: "// TITLE", left-aligned, bright on dark. */
    public static void heading(TooltipMakerAPI t, String title, float pad) {
        t.addSectionHeading("//  " + title.toUpperCase(Locale.ROOT), glow(), dim(), Alignment.LMID, pad);
    }

    /** A button with two cut corners and an upper-case label. */
    public static ButtonAPI button(TooltipMakerAPI t, String label, Object data, Color text, float w, float h, float pad) {
        return t.addButton(label.toUpperCase(Locale.ROOT), data, text, dim(), Alignment.MID, CutStyle.TL_BR, w, h, pad);
    }

    /** A status readout line under a panel title: "SYS // ONLINE  ::  MODE AUTO  ::  GOAL BALANCED". */
    public static void readout(TooltipMakerAPI t, float pad, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) { if (p == null || p.isEmpty()) continue; if (sb.length() > 0) sb.append("  ::  "); sb.append(p.toUpperCase(Locale.ROOT)); }
        t.addPara(CALog.esc(sb.toString()), line(), pad);
    }

    /**
     * 0.3.0.0 (WS-F3 fix, research 04 section 7 quick trims): a feature's mode as a readout line under its section heading,
     * "MODE  AUTO". It replaces the "Mode: %s (Settings, X)." lead-in each Mod features paragraph started with.
     */
    public static void mode(TooltipMakerAPI t, String mode) {
        if (mode != null && !mode.isEmpty()) readout(t, 2f, "mode  " + mode);
    }

    /** 0.3.0.0 (WS-F3 fix pass 2): the line a page or section shows when drawing it failed; one text for every panel. */
    static final String NOT_SHOWN = "This part couldn't be shown; see starsector.log.";
    /** 0.3.0.0 (WS-F3 fix pass 3): lines the colony panel and the empire panel both show, one text each. */
    static final String PAUSED = "Spending paused: colony net is negative.", APPROVAL = "Waiting for approval";

    // ---------------- hover text (0.3.0.0, WS-F3) ----------------

    /** Width of a hover box. */
    static final float HOVER_W = 420f;

    /**
     * 0.3.0.0 (WS-F3, research 04 sections 2 and 7): hover text on the element added last (a paragraph or a button), one
     * paragraph per line, so a panel shows one short line and the detail moves to the hover. Null and empty lines are
     * skipped; nothing is added when none is left.
     */
    public static void hover(TooltipMakerAPI t, String... lines) {
        final List<String> l = hoverLines(lines);
        if (l.isEmpty()) return;
        t.addTooltipToPrevious(new TooltipMakerAPI.TooltipCreator() {
            public boolean isTooltipExpandable(Object o) { return false; }
            public float getTooltipWidth(Object o) { return HOVER_W; }
            public void createTooltip(TooltipMakerAPI tt, boolean expanded, Object o) {
                for (int i = 0; i < l.size(); i++) tt.addPara(CALog.esc(l.get(i)), i == 0 ? 0f : 6f);
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);
    }

    /** The lines a hover shows: the non-empty ones, in order. Pure. */
    static List<String> hoverLines(String... lines) {
        List<String> out = new ArrayList<String>();
        if (lines == null) return out;
        for (String s : lines) if (s != null && !s.trim().isEmpty()) out.add(s.trim());
        return out;
    }

    // ---------------- frames (GL) ----------------

    /**
     * Draws the panel backdrop: a faint grid over the whole panel, and cut-corner frames around the page list (left,
     * navW wide) and the page, with brighter corner brackets. Used by the colony panel's plugin and the empire panel's.
     */
    public static class Frame extends BaseCustomUIPanelPlugin {
        PositionAPI pos;
        float navW, gap;
        long lastDraw;
        public Frame(float navW, float gap) { this.navW = navW; this.gap = gap; }
        @Override public void positionChanged(PositionAPI position) { pos = position; }
        @Override public void renderBelow(float alphaMult) {
            if (pos == null) return;
            long now = System.nanoTime();
            if (now - lastDraw < 2000000L) return;   // one plugin on two panels (the colony dialog's): draw once a frame
            lastDraw = now;
            try { backdrop(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), navW, gap, alphaMult); } catch (Throwable ignored) { }
        }
    }

    static void backdrop(float x, float y, float w, float h, float navW, float gap, float alpha) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Color g = line(), b = glow();
        // grid
        color(g, 0.05f * alpha);
        GL11.glLineWidth(1f);
        GL11.glBegin(GL11.GL_LINES);
        for (float gx = x; gx <= x + w; gx += 32f) { GL11.glVertex2f(gx, y); GL11.glVertex2f(gx, y + h); }
        for (float gy = y; gy <= y + h; gy += 32f) { GL11.glVertex2f(x, gy); GL11.glVertex2f(x + w, gy); }
        GL11.glEnd();
        // frames: the page list and the page
        if (navW > 0f) frame(x - 4f, y - 4f, navW + 8f, h + 8f, 10f, alpha);
        frame(x + navW + gap - 4f, y - 4f, w - navW - gap + 8f, h + 8f, 14f, alpha);
        // a scan bar along the top of the page
        color(b, 0.35f * alpha);
        GL11.glLineWidth(2f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x + navW + gap + 14f, y + h + 4f); GL11.glVertex2f(x + navW + gap + 14f + Math.min(220f, w * 0.3f), y + h + 4f);
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    /** A rectangle with its top-left and bottom-right corners cut, and bright brackets at the other two. */
    static void frame(float x, float y, float w, float h, float cut, float alpha) {
        color(line(), 0.45f * alpha);
        GL11.glLineWidth(1f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x + cut, y + h);       // top edge, after the cut top-left corner
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y + cut);
        GL11.glVertex2f(x + w - cut, y);       // cut bottom-right corner
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h - cut);
        GL11.glEnd();
        color(glow(), 0.9f * alpha);
        GL11.glLineWidth(2f);
        float k = 12f;
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x + w - k, y + h); GL11.glVertex2f(x + w, y + h);   // top-right bracket
        GL11.glVertex2f(x + w, y + h); GL11.glVertex2f(x + w, y + h - k);
        GL11.glVertex2f(x, y + k); GL11.glVertex2f(x, y);                   // bottom-left bracket
        GL11.glVertex2f(x, y); GL11.glVertex2f(x + k, y);
        GL11.glEnd();
    }

    static void color(Color c, float a) {
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, Math.max(0f, Math.min(1f, a)));
    }

    // ---------------- voice ----------------

    /** What a log line is doing, by its wording: {pattern, CODE, strip}. The first match wins. */
    static final Object[][] CODES = {
            // 0.3.0.0 (WS-F4): "Settings: moved to the 0.3.0 menu, nothing you set changed" and the first-time setup's lines
            {"^settings: ", "SETUP", false},
            {"^setup saved", "SETUP", false},
            {"^shipyard: ", "SHIPYARD", true},
            {"^stockpile of ", "STOCK", true},
            {"^recommends a stockpile", "STOCK", false},   // 0.3.0.0 (WS-F3): CAStockpile's proposal, ahead of ADVISE
            {" arrived in local resources", "STOCK", false},
            {"missed a delivery", "ALERT", false},
            {"under siege,? ?", "SIEGE", true},
            {"siege over", "CLEAR", true},
            {"^capital: ", "CAPITAL", true},
            {"^low profile: ", "QUIET", true},
            {"expansion style", "QUIET", false},
            // 0.3.0.0 (WS-F3): diplomacy (CADiplomacy offers, CAWar's stance line, CAIndEvo ambassadors, CASpies relations
            // agents), ahead of BUILD, INSTALL and WAR, which their wording also matches
            // 0.3.1.0: a stance line may end with a period; an offer "in the intel, for you" (THE FORMAT's wording)
            {"^war: (?=.* stance [a-z]+\\.?$)", "DIPLO", true},
            {"offer (accepted|rejected|waits|in the intel)|alliance offer|war confirmation cleared|ambassador|raise relations", "DIPLO", false},
            {"(^| )building ", "BUILD", true},
            {"(^| )upgrading to ", "UPGRADE", true},
            {"(^| )installed ", "INSTALL", true},
            {"(^| )swapped ", "SWAP", true},
            {"^shipping ", "SHIP", true},
            {" arrived in autopilot storage", "RECEIVED", false},
            {"(^| )took down ", "DEMOLISH", true},
            {"decivilize", "ALERT", false},
            {"^built a ", "BUILT", true},
            {"(^| )recommends? ", "ADVISE", true},
            {"^suggested ", "ADVISE", true},
            {"(^| )threat (high|down)", "THREAT", false},
            {"colony crisis", "THREAT", false},
            {"hazard pay ", "POLICY", false},
            {"free port ", "POLICY", false},
            {"edict", "POLICY", false},
            {"^enacted ", "POLICY", true},
            {"^research: ", "RESEARCH", true},
            {"^scan: ", "SCAN", true},
            {"^month: ", "LEDGER", true},
            {"^money tier: ", "TIER", true},
            {"^settlement: ", "SETTLE", true},
            {"^auto-expansion: ", "EXPAND", true},
            {"colony expedition", "EXPAND", false},
            {"construction crew", "CREW", false},
            {"^war: ", "WAR", true},
            // 0.3.0.0 (WS-F3): "^captured" for a capture with no former owner (CAConquest: "Umuzi: captured. Mode Auto, ...")
            {"invasion|blockade|saturation bombardment|war declared|ceasefire|peace treaty|made hostile again|^captured|captured from|conquered colony|after capture|infamy", "WAR", false},
            {"relief fleet|defense fleet|base strike|-pt raid|war fleets", "FLEET", false},
            {"contract", "CONTRACT", false},
            {"spending freely|fast buildout|endgame", "MODE", false},
            {"^focus world", "FOCUS", false},
            {"administrator", "ADMIN", false},
            {"omega core|panopticon|alpha core|beta core|gamma core| core into ", "CORE", false},
            {"augment", "AUGMENT", false},
            {"inspection", "INSPECT", false},
            {"the plan's |plan goes on", "PLAN", false},
            {"autopilot storage to storage|storage logistics| moved from .* to autopilot storage", "STORE", false},   // 0.3.1.0: an item moved to storage
            {" waits| waiting", "HOLD", false},
    };
    static final Pattern CREDITS = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s?¢");

    /** The places a log line can be about: your colonies' names and their systems' names, refreshed daily. */
    static Set<String> places;
    static float placesDay = -1f;

    static Set<String> places() {
        float now = CADay.now();
        if (places != null && now - placesDay < 1f && now >= placesDay) return places;
        Set<String> out = new HashSet<String>();
        try {
            for (MarketAPI m : Misc.getPlayerMarkets(false)) {
                out.add(m.getName());
                StarSystemAPI sys = m.getStarSystem();
                if (sys != null) out.add(sys.getBaseName());
            }
        } catch (Throwable ignored) { }
        places = out; placesDay = now;
        return out;
    }

    /** The on-screen machine line for a log entry. */
    public static String machine(String text) { return machine(text, places()); }

    /**
     * Pure: the on-screen line, "CODE / Place: what", with the places a "Name: ..." prefix may name. 0.2.41.1: one code and
     * one slash, the rest in plain words with its own punctuation (0.2.41.0's "AP\\ #TAG :: CODE // ..." was hard to read).
     * A line with no code is the sentence as it is.
     */
    static String machine(String text, Set<String> places) {
        if (text == null) return "";
        String body = text.trim(), place = null;
        int colon = body.indexOf(": ");
        if (colon > 0 && colon <= 40) {
            String head = body.substring(0, colon);
            if (places.contains(head)) { place = head; body = body.substring(colon + 2); }
        }
        String code = null, lower = body.toLowerCase(Locale.ROOT);
        for (Object[] c : CODES) {
            Matcher m = Pattern.compile((String) c[0]).matcher(lower);
            if (!m.find()) continue;
            code = (String) c[1];
            if ((Boolean) c[2] && m.start() == 0) body = body.substring(m.end());
            break;
        }
        if ("RECEIVED".equals(code)) body = body.replaceAll("(?i) arrived in Autopilot Storage", "");
        body = compact(body);
        if (place != null) body = body.isEmpty() ? place : place + ": " + body;
        else if (!body.isEmpty()) body = Character.toUpperCase(body.charAt(0)) + body.substring(1);
        if (code == null) return body;
        return body.isEmpty() ? code : code + " / " + body;
    }

    /** Credits shortened, an aside the code made the first thing unwrapped, no trailing period. Pure. */
    static String compact(String s) {
        Matcher m = CREDITS.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(shortCredits(m.group(1)) + "¢"));
        m.appendTail(sb);
        String out = sb.toString().replaceAll("\\s+", " ").trim();
        if (out.startsWith("(")) {   // "under siege (a raid ...), defenses only" without its verb
            int close = out.indexOf(')');
            if (close > 0) out = (out.substring(1, close) + out.substring(close + 1)).trim();
        }
        while (out.endsWith(".")) out = out.substring(0, out.length() - 1);
        return out;
    }

    /** 750,000 -> 750k, 1,250,000 -> 1.25M, 94,426,712 -> 94.4M. Pure. */
    static String shortCredits(String digits) {
        long v;
        try { v = Long.parseLong(digits.replace(",", "")); } catch (NumberFormatException e) { return digits; }
        if (v >= 1000000L) {
            double mm = v / 1000000.0;
            String s = mm >= 10 ? String.format(Locale.ROOT, "%.1f", mm) : String.format(Locale.ROOT, "%.2f", mm);
            return trimZeros(s) + "M";
        }
        if (v >= 10000L) return (v / 1000L) + "k";
        if (v >= 1000L) return trimZeros(String.format(Locale.ROOT, "%.1f", v / 1000.0)) + "k";
        return String.valueOf(v);
    }

    static String trimZeros(String s) {
        if (!s.contains(".")) return s;
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }
}
