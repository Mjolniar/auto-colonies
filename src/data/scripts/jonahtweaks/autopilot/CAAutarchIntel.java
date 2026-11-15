package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 0.3.6.8 story mode: the search for the Autarch AI Core as its own objective, as bounty intel works. The map marker sits on
 * the constellation's label, never on the system. The description gives two loose clues about the system, read once from
 * the station's system when the objective starts: its star (and a companion star) and where the station orbits among the
 * planets. One objective per station (siteId): a new station after a sale starts a new one. It ends as recovered when the
 * core is in your cargo, a storage tab or the cradle, and ends quietly when story mode goes off or the station is gone.
 * Checked daily (CASync.daily) and whenever the Story tab is checked (CAStoryIntel.ensure).
 */
public class CAAutarchIntel extends BaseIntelPlugin {
    final String siteId;
    final String area;    // "Gamma Ceti constellation" or "fringe near the ...", used after "the"
    final String star;    // "a red dwarf" or "a yellow star with a companion star"
    final String orbit;   // "inside the innermost planet's orbit", "among the planets", "past the outermost planet", or null
    final boolean signal; // a core after a sale: the TriPad picked up its signal (generation above 0)
    boolean recovered;

    CAAutarchIntel(String siteId, StarSystemAPI sys, SectorEntityToken site) {
        this.siteId = siteId;
        this.area = CAAutarch.area();
        this.star = starOf(sys);
        this.orbit = orbitOf(sys, site);
        this.signal = CASync.generation() > 0;
    }

    // ---------------- when it runs ----------------

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

    /** Recovered: the core is in your cargo, a storage tab or the cradle. */
    static boolean recovered() {
        try { return CAAutarch.anywhere() || (CASync.installed() && !CASync.pulled()); } catch (Throwable t) { return false; }
    }

    /** Starts, ends or keeps the objective. */
    public static void update() {
        try {
            IntelManagerAPI mgr = Global.getSector().getIntelManager();
            CAAutarchIntel live = live();
            String site = active() ? CASync.text("autarchSite") : null;
            if (live != null && (site == null || !site.equals(live.siteId))) {
                if (recovered()) { live.recovered = true; live.endAfterDelay(); }
                else live.endImmediately();
                live = null;
            }
            if (site == null || live != null) return;
            SectorEntityToken e = Global.getSector().getEntityById(site);
            if (e == null || !(e.getContainingLocation() instanceof StarSystemAPI)) return;
            CAAutarchIntel n = new CAAutarchIntel(site, (StarSystemAPI) e.getContainingLocation(), e);
            n.setImportant(true);
            mgr.addIntel(n, true);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core objective failed", t); }
    }

    /** The objective that's running, or null. */
    static CAAutarchIntel live() {
        for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(CAAutarchIntel.class)) {
            CAAutarchIntel a = (CAAutarchIntel) i;
            if (!a.isEnding() && !a.isEnded()) return a;
        }
        return null;
    }

    /** Shows the running objective in a TriPad dialog, as a mission shows when you take it. False when there's none. */
    static boolean showIn(TextPanelAPI panel) {
        try {
            update();
            CAAutarchIntel a = live();
            if (a == null || panel == null) return false;
            Global.getSector().getIntelManager().addIntelToTextPanel(a, panel);
            return true;
        } catch (Throwable t) { return false; }
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

    /** The station's system, or null once the station is gone. */
    StarSystemAPI system() {
        SectorEntityToken e = Global.getSector().getEntityById(siteId);
        return e != null && e.getContainingLocation() instanceof StarSystemAPI ? (StarSystemAPI) e.getContainingLocation() : null;
    }

    // ---------------- the intel ----------------

    @Override protected String getName() { return recovered ? "Autarch AI Core recovered" : "Find the Autarch AI Core"; }
    @Override public String getIcon() { return "graphics/icons/cargo/autarch_core.png"; }
    @Override public boolean hasSmallDescription() { return true; }
    @Override public String getSortString() { return "Colony Autopilot core"; }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>();
        tags.add(Tags.INTEL_STORY);
        tags.add(Tags.INTEL_EXPLORATION);
        tags.add(Tags.INTEL_ACCEPTED);
        tags.add(CAIntel.TAG);
        return tags;
    }

    /** The constellation's label, as bounty intel marks a target's area. A lone system: the closest constellation. */
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        try {
            StarSystemAPI sys = system();
            if (sys == null) return null;
            Constellation c = CAAutarch.constellationOf(sys);
            if (c == null) return sys.getHyperspaceAnchor();
            if (map != null) {
                SectorEntityToken label = map.getConstellationLabelEntity(c);
                if (label != null) return label;
            }
            return Global.getSector().getHyperspace().createToken(c.getLocation().x, c.getLocation().y);
        } catch (Throwable t) { return null; }
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, IntelInfoPlugin.ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        Color tc = getBulletColorForMode(mode);
        bullet(info);
        if (recovered) info.addPara("Recovered", 3f, tc);
        else if (area != null) info.addPara("Somewhere in the %s", 3f, tc, Misc.getHighlightColor(), area);
        unindent(info);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        try { describe(info, width); }   // the game calls this outside any catch
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core objective text failed", t); }
    }

    void describe(TooltipMakerAPI info, float width) {
        Color hl = Misc.getHighlightColor();
        info.addImage(CAAutarch.PORTRAIT, width, 128f, 10f);
        if (recovered) return;
        if (signal) info.addPara("Your TriPad picked up the core's signal from the %s.", 10f, hl, area);
        else info.addPara("The maintenance log on your TriPad places the core at a derelict research station in the %s.", 10f, hl, area);
        String where = orbit == null ? "" : ", with the " + (signal ? "core" : "station") + " orbiting " + orbit;
        if (signal) info.addPara("Your sensors officer reads the source as an uncolonized system around %s" + where + ".", 10f, hl, star);
        else info.addPara("The rest of the entry is damaged. It describes an uncolonized system around %s" + where + ".", 10f, hl, star);
    }
}
