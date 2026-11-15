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
    /** 0.3.7.4: what the core runs now, after the Story page changed it (CASync.summary); never read again. */
    public static final String UPDATE = "update";
    java.util.List<String> turnedOn = new java.util.ArrayList<String>(), turnedOff = new java.util.ArrayList<String>();
    int gained = 0;
    boolean capChanged = false, seatsChanged = false;
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
            p.setName(new FullName("Autarch AI Core", "", FullName.Gender.ANY));   // 0.3.7.5: the core, not a person named Autarch
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
        if (!reread && !CHIME.equals(beat) && !PULL.equals(beat) && !UPDATE.equals(beat)) CASync.see(beat);   // the chime is seen per core (CASync.nextBeat), the pull once it's done
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
        else if (UPDATE.equals(beat)) update();
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
            if (KEEP.equals(data)) { screen("CHARTER FILED: ADVISORY."); close("Put the TriPad away"); return; }
        } else if (CHIME.equals(beat)) {
            if (SLOT.equals(data) || CRADLE.equals(data)) { slot(CRADLE.equals(data)); return; }
            if (RESEAT.equals(data)) { reseat(); return; }
            if (WHAT.equals(data)) { what(); return; }
            if (BREAK.equals(data)) { breakAsk(); return; }
            if (BREAK_YES.equals(data)) { breakDown(); return; }
            if (LATER.equals(data)) {
                para("You leave the core in the hold. The TriPad adds a reminder to its notes.");
                screen("SUBSTRATE IN CARGO HOLD. AWAITING INSTRUCTIONS.");
                para("You can decide later from the Colony Autopilot entry in the intel's Story tab.");
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

    /** 0.3.6.6: "sir" or "ma'am", as the game's $playerSirOrMadam. */
    static String sir() {
        try { return Global.getSector().getPlayerPerson().getGender() == FullName.Gender.FEMALE ? "ma'am" : "sir"; }
        catch (Throwable t) { return "Captain"; }
    }

    void found() {
        art("orbital_construction");
        if (!CASync.dormant() && !reread) { foundOnline(); return; }
        boolean old = market != null && market.getDaysInExistence() > 60f;
        if (old) para("Your TriPad buzzes in the middle of a routine report from " + colony() + ". A system notice you've never seen before has pushed the report off the screen.");
        else para("The first prefab habs on " + colony() + " are still being anchored when your TriPad buzzes. A system notice you've never seen before has pushed everything else off the screen.");
        screen("COLONIAL ADMINISTRATION SUITE REV 7.31",
                "CHARTER RECEIVED: " + colony().toUpperCase(),
                "ADMINISTRATIVE SUBSTRATE NOT FOUND. CONTACT YOUR COLONIAL OFFICE.");
        para("You forward it to Engineering. A few minutes later your chief engineer and your tech officer are both on the comm, partway into an argument.");
        para("\"Domain administrative firmware,\" Tech says. \"Colonial grade. It's been in the TriPad's base layer the whole time, and the charter woke it up-\"");
        para("\"-and it can't do anything,\" Engineering cuts in. \"It wants an AI core to run on. Some model called 'Autarch-class'. It won't take anything else.\"", "Autarch-class");
        para("\"Never heard of it,\" your chief engineer adds. Tech starts to answer, then thinks better of it.");
        if (reread) { close("Put the TriPad away"); return; }
        options.clearOptions();
        options.addOption("\"What else does it say?\"", NOTES);
        options.addOption("Put the TriPad away", CLOSE);
        options.setShortcut(CLOSE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void foundNotes() {
        String area = CAAutarch.area();
        para("The suite has already drawn up a plan for " + colony() + ". Most of it is building orders, with the first industry picked out and a few "
                + "structures marked NOT RECOMMENDED. Every entry carries the same flag.");
        screen("AWAITING SUBSTRATE");
        para("At the bottom is a maintenance log. Its last entry lists one Autarch-class core at a Domain research station, scheduled for pickup. "
                + "Nothing says anyone came for it.");
        screen("LOCATION: " + (area != null ? area.toUpperCase() : "UNKNOWN") + ". FURTHER DATA CORRUPTED.");
        para("\"That's all it has on where,\" Tech says. \"The station's own entry is gone.\"");
        para("Engineering shrugs. \"Salvagers will have been through it. But they go for plating and fuel, not cores they can't identify. "
                + "It could still be sitting there.\"");
        note("New objective: find the Autarch AI Core" + (area != null ? " somewhere in the " + area + "." : "."));
        CAStoryIntel.showIn(text);   // 0.3.6.8: the objective's intel, as a mission shows when you take it (0.3.7.1: the story entry)
        close("Put the TriPad away");
    }

    void foundOnline() {
        portrait();
        screen("NEW CHARTER: " + colony().toUpperCase() + ".", "Shall I take it, Administrator?");
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
            screen("MANAGED COLONY LIMIT REACHED.");
            para(CASync.modeNote + ".");
        } else {
            screen("ACKNOWLEDGED. " + colony().toUpperCase() + ": " + c.mode.label.toUpperCase() + ".");
            gained(colony() + " is in " + c.mode.label + ", with the recommended settings.");
        }
        close("Put the TriPad away");
    }

    // ---------------- the chime: the core in your cargo ----------------

    void chime() {
        art("ai_core_uninstall");
        para("Your TriPad chimes with a tone you don't recognize.");
        screen("AUTARCH-CLASS SUBSTRATE DETECTED. CARGO HOLD.");
        para("You find your chief engineer and your tech officer down in the hold, standing over an open salvage crate. "
                + "Inside, packed in crash foam, is a dark steel cube with a small gold triangle on its lid.");
        options.clearOptions();
        if (CAAutarch.inCargo() < 1) { para("(The Autarch AI Core isn't in your cargo anymore.)"); close("Put the TriPad away"); return; }
        if (!CASync.dormant()) { para("(The Autopilot is already running.)"); close("Put the TriPad away"); return; }
        para("\"That's the one,\" Tech says. \"The TriPad knew it the moment it came aboard.\"");
        para("Engineering has their arms crossed. \"Restricted Domain markings on every face. Whatever this is, the Hegemony would want it.\" "
                + "They look at you. \"Your call, " + sir() + ".\"");
        if (CASync.installed() && CASync.pulled()) {
            para("The cradle is still empty, and the suite on your TriPad still has its records.");
            options.addOption("\"Put it back in the cradle.\"", RESEAT);
        } else {
            options.addOption("\"Plug it into the TriPad.\"", SLOT);
            options.addOption("\"Can you rig it so we can pull it back out?\"", CRADLE);
            SetStoryOption.set(dialog, 1, CRADLE, "jcm_cradle", "ui_char_spent_story_point_technology", "Built an isolation cradle for the Autarch AI Core");
            options.addOption("\"Can you break it down for parts?\"", BREAK);
        }
        options.addOption("\"Leave it in the hold for now.\"", LATER);
        options.setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void slot(boolean cradle) {
        String err = CASync.install(cradle);
        if (err != null) { para(err); close("Put the TriPad away"); return; }
        CASync.see(INSTALL);
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        if (cradle) {
            para("It takes Engineering most of a shift to build a shielded cradle out of spare parts. Tech hovers the whole time, offering suggestions that mostly go ignored.");
            para("\"Manual cutoff,\" your chief engineer says, tapping a switch on top. \"No software between this and the core. "
                    + "If it does anything you don't like, you flip it.\"");
        }
        portrait();
        installText(cradle);
        options.clearOptions();
        options.addOption("\"What can you do?\"", WHAT);
        options.addOption("Continue", CLOSE);
        options.setShortcut(CLOSE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void installText(boolean cradle) {
        if (cradle) para("The cradle's status light goes green.");
        else para("Your chief engineer seats the core in the TriPad's expansion port, and the latch clicks shut. "
                + "\"That's not coming back out without a cutting torch,\" they say. \"Maybe not with one.\"");
        para("After a few seconds the progress bar finishes and the screen fills with text.");
        screen("COLONIAL ADMINISTRATION SUITE REV 7.31. SUBSTRATE: AUTARCH AI CORE. SELF-TEST PASSED.",
                "Good morning, Administrator.",
                "Colonial Office link not found. Retrying.",
                "Colonial Office link not found. Continuing under emergency provisions.",
                "Charters on file: " + Math.max(1, Misc.getPlayerMarkets(false).size()) + ". Approved budget: none. Please submit a budget at your earliest convenience.");
        para("Tech reads it over your shoulder. \"Emergency provisions,\" they say. \"Well, it's not wrong.\"");
        para("\"It wants a budget,\" Engineering says. \"Of course it does.\"");
    }

    /** 0.3.7.2: the Autarch points to its panel; the panel shows the rest (no list of services and costs here). */
    void what() {
        screen("CURRENT CAPACITY IS AVAILABLE FOR REVIEW IN THE ADMINISTRATION PANEL, ADMINISTRATOR.");
        para("\"The Autopilot button on the colony screens,\" Tech says. \"That's its panel.\"");
        close("Put the TriPad away");
    }

    void reseat() {
        String err = CASync.install(false);
        if (err != null) { para(err); close("Put the TriPad away"); return; }
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        portrait();
        screen("SUBSTRATE RESTORED. RESUMING FROM LAST RECORD.", "Welcome back, Administrator.");
        gained("The Autopilot is running again with " + CASync.total() + " bandwidth and every ability it had.");
        close("Continue");
    }

    void breakAsk() {
        para("Your chief engineer turns the core over in their hands, frowning. Tech looks pained at the idea.");
        para("\"I can take it apart, " + sir() + ". Carefully. That's about a year of work, and I'm not rushing it.\"");
        para("\"The lattice is modular. Done right, we get three alpha cores and two betas out of it. Ordinary ones. "
                + "Nothing a Hegemony inspector would look at twice.\"");
        options.clearOptions();
        options.addOption("\"Do it.\"", BREAK_YES);
        options.addOption("\"Not yet.\"", LATER);
        options.setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void breakDown() {
        String err = CASync.breakDown();
        if (err != null) { para(err); close("Put the TriPad away"); return; }
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        para("The core goes into a shielded locker in Engineering, and your TriPad stops chiming.");
        note("In " + CASyncCatalog.BREAKDOWN_DAYS + " days: " + CASyncCatalog.cores(0, CASyncCatalog.BREAKDOWN_BETA, CASyncCatalog.BREAKDOWN_ALPHA) + ".");
        close("Put the TriPad away");
    }

    // ---------------- feeding, pulling, breakdown done, a new core ----------------

    void feed() {
        portrait();
        int[] f = CASync.fed();
        screen("SUBSTRATE ADDED: " + CASyncCatalog.cores(f[0], f[1], f[2]).toUpperCase() + ".", "Capacity increased. Thank you, Administrator.");
        gained("The Autopilot has " + CASync.total() + " bandwidth.");
        close("Put the TriPad away");
    }

    /** 0.3.7.4: the core's own summary of the changes made on the Story page: what runs now, what stopped, its bandwidth. */
    void update() {
        portrait();
        java.util.List<String> lines = new java.util.ArrayList<String>();
        lines.add("CAPABILITY UPDATE.");
        for (String id : turnedOn) {   // 0.3.7.5: the package's name, then what it does
            CASyncCatalog.Pack p = CASyncCatalog.pack(id);
            if (p == null) continue;
            lines.add("Now running: " + p.name + ".");
            if (!p.desc.isEmpty()) lines.add(p.desc);
        }
        for (String id : turnedOff) {
            CASyncCatalog.Pack p = CASyncCatalog.pack(id);
            if (p != null) lines.add("Suspended: " + p.name + ".");
        }
        if (capChanged) lines.add("Managed colonies: " + CASync.managedCap() + ".");
        if (seatsChanged && CASync.seats() > 0) lines.add("Colonies it governs: up to " + CASync.seats() + ".");   // 0.3.8.0
        lines.add("Bandwidth: " + CASync.total() + (gained > 0 ? " (+" + gained + ")" : "") + ". Free: " + Math.max(0, CASync.free()) + ".");
        screen(lines.toArray(new String[0]));
        close("Put the TriPad away");
    }

    void pulledText() {
        para("You flip the cover up and pull the switch. The TriPad goes dark for a moment, then shows one line.");
        screen("SUBSTRATE REMOVED. RECORDS SAVED. SUSPENDING.");
        para("The cradle releases the core.");
    }

    void pullIntro() {
        art("ai_core_uninstall");
        if (reread) { pulledText(); close("Put the TriPad away"); return; }
        if (!CASync.cradle() || CASync.pulled() || !CASync.installed()) { para("(There's no cradled core to pull.)"); close("Put the TriPad away"); return; }
        para("The cutoff switch sits under a plastic cover your chief engineer labeled by hand. Pulling it takes the core out and puts the suite to sleep. "
                + "Its records and settings stay on the TriPad until the core goes back in.");
        options.clearOptions();
        options.addOption("Pull the cutoff", OUT);
        options.addOption("Leave it in", LATER);
        options.setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void pullOut() {
        CASync.pull();
        CASync.see(PULL);
        pulledText();
        AddRemoveCommodity.addItemGainText(CAAutarch.item(), 1, text);
        close("Continue");
    }

    void broken() {
        art("ai_core_uninstall");
        para("Engineering sends up a short report. The breakdown is done, and three alpha cores and two beta cores are packed in crash foam in the cargo hold. "
                + "Tech has attached a much longer report of their own.");
        if (!reread) {
            AddRemoveCommodity.addCommodityGainText(CASyncCatalog.ALPHA, CASyncCatalog.BREAKDOWN_ALPHA, text);
            AddRemoveCommodity.addCommodityGainText(CASyncCatalog.BETA, CASyncCatalog.BREAKDOWN_BETA, text);
        }
        close("Put the TriPad away");
    }

    void returned() {
        art("abandoned_station");
        String area = CAAutarch.area();
        screen("AUTARCH-CLASS SUBSTRATE SIGNAL. SOURCE: " + (area != null ? area.toUpperCase() : "UNKNOWN") + ".");
        para("Your TriPad has picked up another Autarch-class core" + (area != null ? " somewhere in the " + area : "")
                + ". Your sensors officer checks the reading twice.");
        if (area != null) note("New objective: find the Autarch AI Core somewhere in the " + area + ".");
        CAStoryIntel.showIn(text);   // 0.3.6.8
        close("Put the TriPad away");
    }

    // ---------------- unused InteractionDialogPlugin parts ----------------

    public void optionMousedOver(String label, Object data) { }
    public void advance(float amount) { }
    public void backFromEngagement(EngagementResultAPI result) { }
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return null; }
}
