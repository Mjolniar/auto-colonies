package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 0.3.6.0 story mode: the Autopilot's storyline, one entry in the Intel screen's Story tab: the core's state, its bandwidth
 * and what to do next. It appears once the TriPad has said something and goes while story mode is off.
 * 0.3.7.1: the only story entry. The search for the core (0.3.6.8's own objective, CAAutarchIntel) is part of it: while
 * the search runs, the map marker sits on the constellation's label and the description gives the loose clues; the entry
 * is marked important once per station. The Story page with the abilities is in the colony panel (CAColonyDialog).
 */
public class CAStoryIntel extends BaseIntelPlugin {
    /** The station the entry was last marked important for; the mark comes off when that search ends. */
    String markedSite;

    /** Adds, keeps or removes the entry and its search mark. Daily (CASync.daily), on load and with every TriPad beat. */
    public static void ensure() {
        CAAutarchIntel.clearOld();   // 0.3.7.1: the 0.3.6.8 objective entries are part of this one now
        try {
            IntelManagerAPI mgr = Global.getSector().getIntelManager();
            CAStoryIntel live = (CAStoryIntel) mgr.getFirstIntel(CAStoryIntel.class);
            if (!CASync.on()) { if (live != null) mgr.removeIntel(live); return; }
            if (live == null) {
                if (!CASync.seen(CATriPad.FOUND) && CASync.generation() == 0 && !CASync.installed() && CASync.phase().isEmpty()
                        && !CASync.seen(CATriPad.CHIME + ":0")) return;
                live = new CAStoryIntel();
                mgr.addIntel(live, true);
            }
            live.mark();
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story intel failed", t); }
    }

    /** Marked important once when a search starts; unmarked when the search it was marked for ends. */
    void mark() {
        String site = CAAutarchIntel.active() ? CASync.text("autarchSite") : null;
        if (site != null && !site.equals(markedSite)) { markedSite = site; setImportant(true); }
        else if (site == null && markedSite != null) { markedSite = null; setImportant(false); }
    }

    /** Shows the entry in a TriPad dialog, as a mission shows when you take it. */
    static void showIn(TextPanelAPI panel) {
        try {
            ensure();
            IntelInfoPlugin i = Global.getSector().getIntelManager().getFirstIntel(CAStoryIntel.class);
            if (i != null && panel != null) Global.getSector().getIntelManager().addIntelToTextPanel(i, panel);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story intel in the dialog failed", t); }
    }

    static String state() {
        if (CASync.installed() && !CASync.pulled()) return "online, " + CASync.total() + " bandwidth";
        if (CASync.pulled()) return "dormant, core pulled";
        String ph = CASync.phase();
        if ("breaking".equals(ph)) return "core being broken down";
        if ("sold".equals(ph) || "broken".equals(ph) || "salvage".equals(ph)) return "no core";
        return "dormant";
    }

    /** The state as a sentence, for the description. */
    static String status() {
        if (CASync.installed() && !CASync.pulled()) return "The Colony Autopilot is online.";
        if (CASync.pulled()) return "The core's out of its cradle, so the Colony Autopilot is dormant.";
        String ph = CASync.phase();
        if ("breaking".equals(ph)) return "Engineering is breaking the Autarch AI Core down.";
        if ("sold".equals(ph) || "broken".equals(ph) || "salvage".equals(ph)) return "You have no Autarch AI Core.";
        return "The Colony Autopilot is dormant.";
    }

    @Override protected String getName() { return "Colony Autopilot: " + state(); }

    @Override public String getIcon() { return "graphics/icons/cargo/autarch_core.png"; }

    @Override public boolean hasSmallDescription() { return true; }
    @Override public boolean shouldRemoveIntel() { return false; }
    @Override public String getSortString() { return "Colony Autopilot"; }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<String>();
        tags.add(Tags.INTEL_STORY);
        return tags;
    }

    /** While the search runs: the constellation's label, as bounty intel marks a target's area; a lone system: the closest constellation. */
    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        try {
            if (!CAAutarchIntel.active()) return null;
            StarSystemAPI sys = CAAutarchIntel.siteSystem();
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

    /** The objective, one line. While the search runs, the description's clues say where instead. */
    static String objective() {
        String area = CAAutarch.area();
        if (CASync.installed() && !CASync.pulled()) return null;   // 0.3.7.2: online, nothing to point at
        // 0.3.8.9: a core in a storage tab is yours too (no pointer to the old station)
        if (CAAutarch.inCargo() > 0 || CAAutarch.anywhere()) return CASync.pulled() ? "Put the Autarch AI Core back in the cradle." : "Decide what to do with the Autarch AI Core.";
        String ph = CASync.phase();
        if ("breaking".equals(ph)) return "Engineering is breaking the core down.";
        if ("broken".equals(ph) || "salvage".equals(ph)) return "None. Another Autarch-class core may turn up in salvage one day.";
        if ("sold".equals(ph)) return "None for now.";
        if (CASync.pulled()) return "Find an Autarch AI Core to put back in the cradle" + (area != null ? ", somewhere in the " + area + "." : ".");
        return area != null && CASync.seen(CATriPad.FOUND) ? "Find the Autarch AI Core somewhere in the " + area + "." : "Find the Autarch AI Core.";
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, IntelInfoPlugin.ListInfoMode mode) {
        Color c = getTitleColor(mode);
        info.addPara(getName(), c, 0f);
        try { String o = objective(); if (o != null) info.addPara(CALog.esc(o), 3f, Misc.getGrayColor(), Misc.getHighlightColor()); }   // 0.3.6.2: guarded
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story intel line failed", t); }
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        try { describe(info, width); }   // 0.3.6.2: guarded, the game calls this outside any catch
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story intel failed", t); }
    }

    void describe(TooltipMakerAPI info, float width) {
        Color hl = Misc.getHighlightColor();
        info.addImage(CAAutarch.PORTRAIT, width, 128f, 0f);
        // 0.3.6.6: before the core is installed, nothing about feeding cores or abilities (the story tells it)
        if (CASync.installed()) info.addPara("The Colonial Administration Suite on your TriPad runs on the Autarch AI Core.", 10f);
        else info.addPara("The Colonial Administration Suite on your TriPad is an old Domain program for running colonies. It needs an Autarch-class AI core to run.", 10f);
        info.addPara(status(), hl, 10f);   // 0.3.8.6: no CALog.esc: plain addPara text and highlight values show as is
        if (CASync.installed()) info.addPara("The core has %s bandwidth. Your abilities use %s, and %s are free.", 4f, hl, "" + CASync.total(), "" + CASync.spent(), "" + Math.max(0, CASync.free()));
        if (CAAutarchIntel.active()) clues(info, hl);
        else if (objective() != null) info.addPara(objective(), hl, 10f);   // 0.3.8.6: shown as is
        if (CASync.dormant() && CAAutarch.inCargo() > 0)   // 0.3.7.1: the Story page's button, here too (no colony needed)
            info.addButton(CASync.pulled() ? "Reseat the core" : "Decide on the core", CASync.PREFIX + "core", Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, 200f, 24f, 10f);
    }

    /** 0.3.6.8: where the station is, loosely: its constellation, its star and the station's orbit among the planets. */
    static void clues(TooltipMakerAPI info, Color hl) {
        String area = CAAutarch.area();
        boolean signal = CASync.generation() > 0;
        StarSystemAPI sys = CAAutarchIntel.siteSystem();
        SectorEntityToken site = CAAutarchIntel.site();
        String star = sys == null ? "a star" : CAAutarchIntel.starOf(sys);
        String orbit = sys == null ? null : CAAutarchIntel.orbitOf(sys, site);
        if (signal) info.addPara("Your TriPad picked up the core's signal from the %s.", 10f, hl, area);
        else info.addPara("The maintenance log on your TriPad places the core at a derelict research station in the %s.", 10f, hl, area);
        String where = orbit == null ? "" : ", with the " + (signal ? "core" : "station") + " orbiting " + orbit;
        // 0.3.8.6: no sensors framing; the orbit's name is joined into a format, so it is escaped
        if (signal) info.addPara("The source is an uncolonized system around %s" + CALog.esc(where) + ".", 10f, hl, star);
        else info.addPara("The rest of the entry is damaged. It describes an uncolonized system around %s" + CALog.esc(where) + ".", 10f, hl, star);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        try { CASync.handle(buttonId); }   // "core": the TriPad brings the core's dialog up once the intel screen closes
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story intel button failed", t); }
        ui.updateUIForItem(this);
    }
}
