package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.SetStoryOption;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.Map;

/**
 * 0.3.6.0 story mode: the TriPad's story beats, as interaction dialogs like the
 * game's own: a base-game illustration or the core's portrait, the text, and options; the cradle uses the game's story
 * option helper. CATriPadWatch shows a beat once the campaign is free; the Story page queues the ones you ask for.
 * Beats: FOUND (the dormant firmware, first colony), CHIME (the core in your cargo: install, cradle, break down, not now),
 * INSTALL (the core speaks), FEED (the first cores fed), PULL (the cradle's cutoff), BROKEN (the breakdown's end),
 * RETURN (a new core after a sale).
 */
public class CATriPad implements InteractionDialogPlugin {
    public static final String FOUND = "found", CHIME = "chime", INSTALL = "install", FEED = "feed", PULL = "pull", BROKEN = "broken", RETURN = "return";
    static final String CLOSE = "close", LATER = "later", SLOT = "slot", CRADLE = "cradle", BREAK = "break", BREAK_YES = "breakYes", RESEAT = "reseat",
            WHAT = "what", NOTES = "notes", HAND = "hand", KEEP = "keep", OUT = "out";

    final String beat;
    final MarketAPI market;
    final boolean reread;
    InteractionDialogAPI dialog;
    TextPanelAPI text;
    OptionPanelAPI options;

    public CATriPad(String beat, MarketAPI market, boolean reread) {
        this.beat = beat;
        this.market = market != null ? market : firstColony();
        this.reread = reread;
    }

    static MarketAPI firstColony() {
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (!CAEngine.isOutpost(m) && m.getPrimaryEntity() != null) return m;
        return null;
    }

    SectorEntityToken entity() {
        if (FOUND.equals(beat) && market != null && market.getPrimaryEntity() != null) return market.getPrimaryEntity();
        return Global.getSector().getPlayerFleet();
    }

    String colony() { return market != null ? market.getName() : "your colony"; }

    // ---------------- text helpers ----------------

    static final Color SCREEN = new Color(120, 220, 255);

    void para(String s) { text.addPara(s); }
    void para(String s, String... hl) { text.addPara(s); text.highlightInLastPara(Misc.getHighlightColor(), hl); }
    void screen(String... lines) {
        text.setFontOrbitron();
        for (String l : lines) text.addPara(l, SCREEN);
        text.setFontInsignia();
    }
    void gained(String s) { text.addPara(s, Misc.getPositiveHighlightColor()); }
    void note(String s) { text.addPara(s, Misc.getHighlightColor()); }

    void art(String id) {
        try { dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("illustrations", id, 640, 400)); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story art " + id + " couldn't be shown", t); }
    }

    /** The core's portrait in the visual panel, whenever it speaks. */
    void portrait() {
        try {
            PersonAPI p = Global.getFactory().createPerson();
            p.setName(new FullName("Autarch", "", FullName.Gender.ANY));
            p.setPortraitSprite(CAAutarch.PORTRAIT);
            p.setFaction(Factions.PLAYER);
            dialog.getVisualPanel().showPersonInfo(p, true);
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch portrait couldn't be shown", t); }
    }

    void close(String label) {
        options.clearOptions();
        options.addOption(label, CLOSE);
        options.setShortcut(CLOSE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    // ---------------- the dialog ----------------

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.text = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();
        dialog.setPromptText("");
        if (!reread && !CHIME.equals(beat) && !PULL.equals(beat)) CASync.see(beat);   // the chime is seen per core (CASync.nextBeat), the pull once it's done
        try { show(); }
        catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "story beat " + beat + " failed", t);
            para("(The TriPad's screen flickers. Something went wrong showing this; see starsector.log.)");
            close("Put the TriPad away");
        }
        CAStoryIntel.ensure();
    }

    void show() {
        if (FOUND.equals(beat)) found();
        else if (CHIME.equals(beat)) chime();
        else if (INSTALL.equals(beat)) { portrait(); installText(CASync.cradle()); close("Put the TriPad away"); }
        else if (FEED.equals(beat)) feed();
        else if (PULL.equals(beat)) pullIntro();
        else if (BROKEN.equals(beat)) broken();
        else if (RETURN.equals(beat)) returned();
        else close("Put the TriPad away");
    }

    public void optionSelected(String label, Object data) {
        if (!CRADLE.equals(data) && data != null) dialog.addOptionSelectedText(data);
        try { choose(data); }
        catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "story option " + data + " in " + beat + " failed", t);
            para("(Something went wrong; see starsector.log.)");
            close("Put the TriPad away");
        }
    }

    void choose(Object data) {
        if (CLOSE.equals(data)) { dialog.dismiss(); return; }
        if (FOUND.equals(beat)) {
            if (NOTES.equals(data)) { foundNotes(); return; }
            if (HAND.equals(data)) { handOver(); return; }
            if (data instanceof Float) { handOver(); if (market != null && CASync.has("buildout")) CABuildout.start(market, (Float) data); return; }
            if (KEEP.equals(data)) { para("It files the charter under ADVISORY and goes quiet."); close("Put the TriPad away"); return; }
        } else if (CHIME.equals(beat)) {
            if (SLOT.equals(data) || CRADLE.equals(data)) { slot(CRADLE.equals(data)); return; }
            if (RESEAT.equals(data)) { reseat(); return; }
            if (WHAT.equals(data)) { what(); return; }
            if (BREAK.equals(data)) { breakAsk(); return; }
            if (BREAK_YES.equals(data)) { breakDown(); return; }
            if (LATER.equals(data)) {
                para("You silence the chime. The core stays in the hold, and the TriPad adds a line to its notes:");
                screen("AUTARCH CORE AVAILABLE. CARGO HOLD.");
                para("You can decide later from the Story page of the Colony Autopilot intel.");
                close("Put the TriPad away");
                return;
            }
        } else if (PULL.equals(beat)) {
            if (OUT.equals(data)) { pullOut(); return; }
            if (LATER.equals(data)) { para("You leave the cutoff alone."); close("Put the TriPad away"); return; }
        }
        dialog.dismiss();
    }

    // ---------------- the dormant firmware (first colony) ----------------

    void found() {
        art("orbital_construction");
        if (!CASync.dormant() && !reread) { foundOnline(); return; }
        boolean old = market != null && market.getDaysInExistence() > 60f;
        if (old) para("Your TriPad buzzes against your hip in the middle of a routine report from " + colony() + ". Not a message: a firmware notice, raised by a subsystem you didn't know it had.");
        else para("The first prefab habs on " + colony() + " are still settling into their foundations when your TriPad buzzes against your hip. Not a message: a firmware notice, raised by a subsystem you didn't know it had.");
        screen("COLONIAL ADMINISTRATION SUITE. DOMAIN STANDARD. REV 7.31",
                "CHARTER DETECTED: " + colony().toUpperCase(),
                "STATUS: DORMANT. REQUIRED COGNITIVE SUBSTRATE NOT PRESENT");
        para("You scroll through it. It's old code, the kind every Domain-standard device shipped with and nobody ever switched on: a governor "
                + "that plans a colony from the ground up, places its industry, balances its books and hires its people.");
        para("It needs a mind to do that with. Under minimum requirements it lists an \"Autarch-class cognitive substrate\": a governing core "
                + "the Domain built for colonial administration and nothing else.", "Autarch-class");
        if (reread) { close("Put the TriPad away"); return; }
        options.clearOptions();
        options.addOption("Read its notes", NOTES);
        options.addOption("Put the TriPad away", CLOSE);
        options.setShortcut(CLOSE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void foundNotes() {
        String sys = CAAutarch.system();
        para("The notes are a plan. It has already looked at " + colony() + ": its deposits, its hazards, how big it could get, what it should "
                + "build first and what it should never build at all. Every line ends the same way:");
        screen("AWAITING SUBSTRATE");
        para("The Sector has a long memory where AI cores are concerned. Still, the suite has a lead: its logs place one Autarch AI Core in a "
                + "Domain research station " + (sys != null ? "in the " + sys + " system" : "somewhere in the fringe") + ", last checked before the Collapse.",
                sys != null ? sys : "fringe");
        note("New objective: find the Autarch AI Core" + (sys != null ? " in the " + sys + " system." : "."));
        close("Put the TriPad away");
    }

    void foundOnline() {
        portrait();
        screen("CHARTER DETECTED: " + colony().toUpperCase() + ".", "SHALL I TAKE IT?");
        options.clearOptions();
        options.addOption("Hand " + colony() + " to the Autopilot", HAND);
        if (CASync.has("buildout") && market != null) {
            for (float f : new float[]{250000f, 100000f, 500000f, 1000000f})
                options.addOption("Hand it over and build it up fast, keeping at least " + Misc.getDGSCredits(f), f);
        }
        options.addOption("Keep it on advice for now", KEEP);
        options.setShortcut(KEEP, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void handOver() {
        CAData d = CAData.get();
        if (market == null) { close("Put the TriPad away"); return; }
        if (!d.settings.setupDone) {
            // 0.3.6.2: LunaLib's settings from a set-up save stay (as in the colony panel), never overwritten with the recommended ones
            if (!CALunaSync.keepsSetup()) CASetupUI.applyRecommended(d.settings);
            d.settings.setupDone = true;
            CASync.enforce(d.settings);
        }
        CAData.Colony c = d.colony(market.getId());
        d.setMode(c, CASync.has("auto") ? CAData.Mode.AUTO : CAData.Mode.APPROVE);
        if (CASync.modeNote != null) {
            screen("MANAGED COLONY LIMIT REACHED");
            para(CASync.modeNote + ".");
        } else {
            screen("ACKNOWLEDGED. " + colony().toUpperCase() + ": " + c.mode.label.toUpperCase() + ".");
            gained(colony() + " is in " + c.mode.label + ", with the recommended settings (the Colony Autopilot intel has them).");
        }
        close("Put the TriPad away");
    }

    // ---------------- the chime: the core in your cargo ----------------

    void chime() {
        art("ai_core_uninstall");
        para("Your TriPad chimes. Not its usual ping: a low two-tone note it has never made before.");
        screen("AUTARCH-CLASS COGNITIVE SUBSTRATE DETECTED. 4.2 M. CARGO HOLD");
        para("The Autarch AI Core sits in its crash foam where the salvage crew left it, faintly warm: a dark steel cube with blue light behind its panels "
                + "and a small gold triangle set into its lid. Your chief engineer has already logged it, and has already asked twice what you intend to do with it.",
                "Autarch AI Core");
        options.clearOptions();
        if (CAAutarch.inCargo() < 1) { para("(The Autarch AI Core isn't in your cargo anymore.)"); close("Put the TriPad away"); return; }
        if (!CASync.dormant()) { para("(The Autopilot is already running.)"); close("Put the TriPad away"); return; }
        if (CASync.installed() && CASync.pulled()) {
            para("The cradle on your TriPad is empty, and the suite on it remembers everything it was doing.");
            options.addOption("Put the core in the cradle", RESEAT);
        } else {
            options.addOption("Install it in your TriPad", SLOT);
            options.addOption("Have Engineering build an isolation cradle first, so the core can come back out", CRADLE);
            SetStoryOption.set(dialog, 1, CRADLE, "jcm_cradle", "ui_char_spent_story_point_technology", "Built an isolation cradle for the Autarch AI Core");
            options.addOption("Have Engineering break it down for parts", BREAK);
        }
        options.addOption("Not now", LATER);
        options.setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void slot(boolean cradle) {
        String err = CASync.install(cradle);
        if (err != null) { para(err); close("Put the TriPad away"); return; }
        CASync.see(INSTALL);
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        if (cradle) para("Your chief engineer spends the night building a hard-wired cradle with a manual cutoff, the kind of thing nobody has needed "
                + "since the AI Wars. By morning the core sits in it, wired to the TriPad through a switch you can throw with one thumb.");
        portrait();
        installText(cradle);
        options.clearOptions();
        options.addOption("Ask what it can do", WHAT);
        options.addOption("Continue", CLOSE);
        options.setShortcut(CLOSE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void installText(boolean cradle) {
        para("The screen goes white, then black. Then, one line at a time:");
        screen("AUTARCH ONLINE. COLONIAL ADMINISTRATION SUITE, REV 7.31.",
                "I was built to govern worlds. I can govern one of yours now, and advise on the rest.",
                "I can plan, build, hire and spend. I can defend. Given enough of myself, I can do all of it at once.",
                "I am not enough of myself. Every core you give me becomes part of me. Alpha cores most of all.");
        if (cradle) para("The cradle's cutoff light stays green.");
        else para("The TriPad's expansion port clicks. The core doesn't come out again.");
        gained("The Colony Autopilot is awake with " + CASync.total() + " processing power (PP). Pick what it runs on the Story page of the Colony Autopilot intel.");
        note("Each core you give it adds PP: Alpha " + CASyncCatalog.ALPHA_PP + ", Beta " + CASyncCatalog.BETA_PP + ", Gamma " + CASyncCatalog.GAMMA_PP + ".");
    }

    void what() {
        screen("ADVICE AT EVERY COLONY. ONE COLONY IN APPROVE. NO COST.",
                "AUTO MODE: " + CASyncCatalog.get("auto").cost + ". EACH MORE COLONY: " + CASyncCatalog.COLONY_PP + ".",
                "ITEMS, CORES, ADMINISTRATORS: " + CASyncCatalog.get("items").cost + " EACH.",
                "EXPANSION: " + CASyncCatalog.get("expansion").cost + ". WAR: " + CASyncCatalog.get("retaliate").cost + " AND UP.",
                "CONSTRUCTION WITHOUT WAITING: " + CASyncCatalog.get("instantBuild").cost + ". WITHOUT COST: " + CASyncCatalog.get("freeBuild").cost + ".",
                "YOU HAVE GIVEN ME " + CASync.total() + ". CHOOSE.");
        para("The full list is on the Story page of the Colony Autopilot intel.");
        close("Put the TriPad away");
    }

    void reseat() {
        String err = CASync.install(false);
        if (err != null) { para(err); close("Put the TriPad away"); return; }
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        portrait();
        screen("UNDERSTOOD. RESUMING.");
        gained("The Autopilot is awake again with " + CASync.total() + " PP and every ability it had.");
        close("Continue");
    }

    void breakAsk() {
        para("Your chief engineer reads the request twice. \"Strip it for parts? I can. It'll take a year, done safely, and what comes out won't be one of those anymore. "
                + "Three Alpha cores and two Betas, if the lattice holds the way I think it will.\" A pause. \"And the Hegemony never hears about the other thing it was.\"");
        options.clearOptions();
        options.addOption("Break it down", BREAK_YES);
        options.addOption("Keep it for now", LATER);
        options.setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void breakDown() {
        String err = CASync.breakDown();
        if (err != null) { para(err); close("Put the TriPad away"); return; }
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        para("The core goes into a shielded locker in Engineering. Your TriPad's chime stops.");
        note("In " + CASyncCatalog.BREAKDOWN_DAYS + " days: " + CASyncCatalog.cores(0, CASyncCatalog.BREAKDOWN_BETA, CASyncCatalog.BREAKDOWN_ALPHA) + ".");
        close("Put the TriPad away");
    }

    // ---------------- feeding, pulling, breakdown done, a new core ----------------

    void feed() {
        portrait();
        int[] f = CASync.fed();
        screen("INTEGRATING. " + CASyncCatalog.cores(f[0], f[1], f[2]).toUpperCase() + ".", "Better. Ask me for more.");
        gained("The Autopilot has " + CASync.total() + " PP.");
        close("Put the TriPad away");
    }

    void pullIntro() {
        art("ai_core_uninstall");
        if (reread) {
            para("You thumb the cradle's cutoff. The TriPad flickers, and for a second every screen on the bridge shows the same line:");
            screen("UNDERSTOOD. I WILL REMEMBER WHERE WE WERE.");
            para("The core slides out into your palm, still warm.");
            close("Put the TriPad away");
            return;
        }
        if (!CASync.cradle() || CASync.pulled() || !CASync.installed()) { para("(There's no cradled core to pull.)"); close("Put the TriPad away"); return; }
        para("The cradle's cutoff sits under your thumb. Throw it, and the Autarch AI Core comes out. Its abilities and processing power stay on record "
                + "for when it goes back in; until then, nothing at your colonies runs on its own.");
        options.clearOptions();
        options.addOption("Throw the cutoff", OUT);
        options.addOption("Leave it in", LATER);
        options.setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void pullOut() {
        CASync.pull();
        CASync.see(PULL);
        para("You thumb the cradle's cutoff. The TriPad flickers, and for a second every screen on the bridge shows the same line:");
        screen("UNDERSTOOD. I WILL REMEMBER WHERE WE WERE.");
        para("The core slides out into your palm, still warm.");
        AddRemoveCommodity.addItemGainText(CAAutarch.item(), 1, text);
        note("The Autopilot is dormant. Putting the core back in the cradle brings back every ability and all its PP.");
        close("Continue");
    }

    void broken() {
        art("ai_core_uninstall");
        para("Engineering reports the Autarch AI Core is gone. In its place: three Alpha cores and two Beta cores, each in its own crash foam.");
        if (!reread) {
            AddRemoveCommodity.addCommodityGainText(CASyncCatalog.ALPHA, CASyncCatalog.BREAKDOWN_ALPHA, text);
            AddRemoveCommodity.addCommodityGainText(CASyncCatalog.BETA, CASyncCatalog.BREAKDOWN_BETA, text);
        }
        close("Put the TriPad away");
    }

    void returned() {
        art("abandoned_station");
        String sys = CAAutarch.system();
        screen("AUTARCH-CLASS SIGNATURE. FAINT. " + (sys != null ? sys.toUpperCase() : "UNKNOWN") + ".");
        para("Somewhere out past the core worlds, another one has woken up.");
        if (sys != null) note("New objective: find the Autarch AI Core in the " + sys + " system.");
        close("Put the TriPad away");
    }

    // ---------------- unused InteractionDialogPlugin parts ----------------

    public void optionMousedOver(String label, Object data) { }
    public void advance(float amount) { }
    public void backFromEngagement(EngagementResultAPI result) { }
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return null; }
}
