package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Locale;

/**
 * The search for the Autarch AI Core. 0.3.6.8 had it as its own intel entry; 0.3.7.1 folds it into the one story entry
 * (CAStoryIntel): this class keeps when the search runs and the loose clues (the star, the station's orbit among the
 * planets), and takes the old entries out of the intel list. The class and its fields stay so 0.3.6.8 to 0.3.7.0 saves load.
 */
public class CAAutarchIntel extends BaseIntelPlugin {
    // the 0.3.6.8 entry's saved fields (read by older saves only)
    String siteId, area, star, orbit;
    boolean signal, recovered;

    /** 0.3.7.1: takes the 0.3.6.8 to 0.3.7.0 entries out of the intel list. */
    static void clearOld() {
        try {
            IntelManagerAPI mgr = Global.getSector().getIntelManager();
            for (IntelInfoPlugin i : new ArrayList<IntelInfoPlugin>(mgr.getIntel(CAAutarchIntel.class))) mgr.removeIntel(i);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "old Autarch AI Core entries not removed", t); }
    }

    // ---------------- when the search runs ----------------

    /** The search is on: story mode on, no core yet, a station and its hint. Pure inputs in active(...). */
    static boolean active() {
        try {
            return active(CASync.on(), CASync.phase(), CASync.installed(), CASync.pulled(), CAAutarch.area() != null,
                    CASync.generation(), CASync.seen(CATriPad.FOUND), CAAutarch.siteExists(), CAAutarch.anywhere());
        } catch (Throwable t) { return false; }
    }

    static boolean active(boolean on, String phase, boolean installed, boolean pulled, boolean hasArea, int generation,
                          boolean foundSeen, boolean siteExists, boolean coreAnywhere) {
        if (!on || !phase.isEmpty()) return false;               // story mode off, or a breakdown or sale under way
        if (installed && !pulled) return false;                  // the core is in the cradle
        if (!hasArea || !siteExists || coreAnywhere) return false;
        return generation > 0 || foundSeen;                      // the first core: once the TriPad's founding note has shown
    }

    /** The derelict station holding the core, or null. */
    static SectorEntityToken site() {
        String id = CASync.text("autarchSite");
        return id == null || "gone".equals(id) ? null : Global.getSector().getEntityById(id);
    }

    /** The station's system, or null once the station is gone. */
    static StarSystemAPI siteSystem() {
        SectorEntityToken e = site();
        return e != null && e.getContainingLocation() instanceof StarSystemAPI ? (StarSystemAPI) e.getContainingLocation() : null;
    }

    // ---------------- the clues ----------------

    /** "a red dwarf", "an orange star with a companion star". */
    static String starOf(StarSystemAPI sys) {
        try {
            PlanetAPI s = sys.getStar();
            String name = s == null || s.getSpec() == null ? "star" : s.getSpec().getName().toLowerCase(Locale.ROOT);
            return article(name) + (sys.getSecondary() != null ? " with a companion star" : "");
        } catch (Throwable t) { return "a star"; }
    }

    static String article(String noun) { return ("aeiou".indexOf(noun.isEmpty() ? 'x' : noun.charAt(0)) >= 0 ? "an " : "a ") + noun; }

    /** Where the station orbits among the planets; null when the system has none. */
    static String orbitOf(StarSystemAPI sys, SectorEntityToken site) {
        try {
            PlanetAPI s = sys.getStar();
            if (s == null || site == null) return null;
            float min = Float.MAX_VALUE, max = 0f;
            for (PlanetAPI p : sys.getPlanets()) {
                if (p.isStar()) continue;
                float d = Misc.getDistance(p.getLocation(), s.getLocation());
                min = Math.min(min, d); max = Math.max(max, d);
            }
            return orbitBand(Misc.getDistance(site.getLocation(), s.getLocation()), min, max);
        } catch (Throwable t) { return null; }
    }

    /** Pure: the band for an orbit radius, given the innermost and outermost planets' radii (max 0: no planets). */
    static String orbitBand(float r, float innermost, float outermost) {
        if (outermost <= 0f) return null;
        if (r < innermost) return "inside the innermost planet's orbit";
        if (r > outermost) return "past the outermost planet";
        return "among the planets";
    }

    // ---------------- an old entry, until clearOld takes it out ----------------

    @Override protected String getName() { return "Find the Autarch AI Core"; }
    @Override public boolean shouldRemoveIntel() { return true; }
}
