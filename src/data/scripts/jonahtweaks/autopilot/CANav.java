package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

/**
 * The page list down the left side of the empire and colony panels, so one section shows at a time instead of one long
 * scroll. A page is {id, label}; an entry with a null id is a gray heading over the pages after it.
 */
final class CANav {
    static final float W = 180f, GAP = 10f;
    static final String PREFIX = "ca:page:";

    private CANav() { }

    static void render(TooltipMakerAPI nav, List<String[]> pages, String current) {
        for (String[] p : pages) {
            if (p[0] == null) { nav.addPara(CALog.esc(p[1].toUpperCase(java.util.Locale.ROOT)), Misc.getGrayColor(), 12f); continue; }
            boolean on = p[0].equals(current);
            nav.addButton((on ? "> " : "  ") + p[1].toUpperCase(java.util.Locale.ROOT), PREFIX + p[0], on ? Misc.getHighlightColor() : Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Alignment.LMID, CutStyle.TL_BR, W - 6f, 24f, 4f);
        }
    }

    /** The page a nav button opens, or null if the id isn't a nav button. */
    static String page(Object id) {
        return id instanceof String && ((String) id).startsWith(PREFIX) ? ((String) id).substring(PREFIX.length()) : null;
    }

    /** current if it's still listed (a mod can be switched off, a megastructure lost), else the first page. */
    static String valid(List<String[]> pages, String current) {
        for (String[] p : pages) if (p[0] != null && p[0].equals(current)) return current;
        for (String[] p : pages) if (p[0] != null) return p[0];
        return current;
    }
}
