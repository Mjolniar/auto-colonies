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
        if (old) para("Your TriPad buzzes in the middle of a routine report from " + colony() + ". A system notice you've never seen before has pushed the report off the screen.");
        else para("The first prefab habs on " + colony() + " are still being anchored when your TriPad buzzes. A system notice you've never seen before has pushed everything else off the screen.");
        screen("COLONIAL ADMINISTRATION SUITE REV 7.31",
                "CHARTER RECEIVED: " + colony().toUpperCase(),
                "ADMINISTRATIVE SUBSTRATE NOT FOUND. CONTACT YOUR COLONIAL OFFICE.");
        para("You forward it to Engineering. Your chief engineer calls back a few minutes later.");
        para("\"It's old Domain firmware, Captain. Every TriPad has some buried in it, and most of it never runs. This one's a colony governor. "
                + "It'd run the whole place for you if it could.\"");
        para("They scroll through something on their end. \"Problem is, it wants a particular AI core to run on. 'Autarch-class.' Never heard of it.\"", "Autarch-class");
        if (reread) { close("Put the TriPad away"); return; }
        options.clearOptions();
        options.addOption("\"What else does it say?\"", NOTES);
        options.addOption("Put the TriPad away", CLOSE);
        options.setShortcut(CLOSE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void foundNotes() {
        String sys = CAAutarch.system();
        para("The suite has already drawn up a plan for " + colony() + ". Most of it is building orders, with the first industry picked out and a few "
                + "structures marked NOT RECOMMENDED. Every entry carries the same flag.");
        screen("AWAITING SUBSTRATE");
        para("At the bottom is a maintenance log. Its last entry lists one Autarch-class core at a Domain research station "
                + (sys != null ? "in the " + sys + " system" : "somewhere in the fringe") + ", scheduled for pickup, with no record that anyone came for it.",
                sys != null ? sys : "fringe");
        para("\"A Domain station that far out has probably been picked over,\" your chief engineer says. \"But salvagers go for plating and fuel. "
                + "They wouldn't know what that core is. Might be worth a look.\"");
        note("New objective: find the Autarch AI Core" + (sys != null ? " in the " + sys + " system." : "."));
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
            gained(colony() + " is in " + c.mode.label + ", with the recommended settings (the Colony Autopilot intel has them).");
        }
        close("Put the TriPad away");
    }

    // ---------------- the chime: the core in your cargo ----------------

    void chime() {
        art("ai_core_uninstall");
        para("Your TriPad chimes with a tone you don't recognize.");
        screen("AUTARCH-CLASS SUBSTRATE DETECTED. CARGO HOLD.");
        para("Your chief engineer is already down in the hold when you get there, running a scanner over a salvage crate. "
                + "Inside, packed in crash foam, is a dark steel cube with a small gold triangle on its lid.");
        options.clearOptions();
        if (CAAutarch.inCargo() < 1) { para("(The Autarch AI Core isn't in your cargo anymore.)"); close("Put the TriPad away"); return; }
        if (!CASync.dormant()) { para("(The Autopilot is already running.)"); close("Put the TriPad away"); return; }
        para("\"It's an AI core, Captain, but not a pattern I know. Domain restricted markings all over it.\" They nod at your TriPad. "
                + "\"That thing hasn't stopped pinging since we brought it aboard. What do you want done with it?\"");
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
            para("Your chief engineer and your tech officer spend most of a shift building a shielded cradle out of spare parts, with a manual cutoff on top.");
            para("\"If it does anything you don't like, you flip this,\" your chief engineer says, tapping the switch.");
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
                + "\"That's not coming back out without tools,\" they say. \"Maybe not with them, either.\"");
        para("After a few seconds the progress bar finishes and the screen fills with text.");
        screen("COLONIAL ADMINISTRATION SUITE REV 7.31. SUBSTRATE: AUTARCH. SELF-TEST PASSED.",
                "Good morning, Administrator.",
                "Colonial Office link not found. Retrying.",
                "Colonial Office link not found. Continuing under emergency provisions.",
                "Charters on file: " + Math.max(1, Misc.getPlayerMarkets(false).size()) + ". Approved budget: none. Please submit a budget at your earliest convenience.");
        para("Your tech officer reads it over your shoulder. \"Emergency provisions,\" they say. \"Well, it's not wrong.\"");
        gained("The Colony Autopilot is running with " + CASync.total() + " processing power (PP). Pick what it runs on the Story page of the Colony Autopilot intel.");
        note("Each core you give it adds PP: Alpha " + CASyncCatalog.ALPHA_PP + ", Beta " + CASyncCatalog.BETA_PP + ", Gamma " + CASyncCatalog.GAMMA_PP + ".");
    }

    void what() {
        screen("SERVICES AT CURRENT CAPACITY:",
                "Advice, all colonies. Approve mode, one colony. No charge.",
                "Auto mode: " + CASyncCatalog.get("auto").cost + " PP. Each additional colony: " + CASyncCatalog.COLONY_PP + " PP.",
                "Items, cores and administrators: " + CASyncCatalog.get("items").cost + " PP each.",
                "Expansion: " + CASyncCatalog.get("expansion").cost + " PP. War: " + CASyncCatalog.get("retaliate").cost + " PP and up.",
                "Construction without waiting: " + CASyncCatalog.get("instantBuild").cost + " PP. Without cost: " + CASyncCatalog.get("freeBuild").cost + " PP.",
                "Capacity: " + CASync.total() + " PP. Additional substrate accepted. Alpha-level preferred.");
        para("The full list is on the Story page of the Colony Autopilot intel.");
        close("Put the TriPad away");
    }

    void reseat() {
        String err = CASync.install(false);
        if (err != null) { para(err); close("Put the TriPad away"); return; }
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        portrait();
        screen("SUBSTRATE RESTORED. RESUMING FROM LAST RECORD.", "Welcome back, Administrator.");
        gained("The Autopilot is running again with " + CASync.total() + " PP and every ability it had.");
        close("Continue");
    }

    void breakAsk() {
        para("Your chief engineer turns the core over in their hands for a while before answering.");
        para("\"I can take it apart. Done carefully, it's about a year of work, and I'd want to do it carefully.\" They tap at their TriPad.");
        para("\"The lattice should give us three alpha cores and two betas. Ordinary ones. Nothing that would raise questions at a Hegemony inspection.\"");
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
        gained("The Autopilot has " + CASync.total() + " PP.");
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
        note("The Autopilot is dormant. Putting the core back in the cradle brings back every ability and all its PP.");
        close("Continue");
    }

    void broken() {
        art("ai_core_uninstall");
        para("Engineering sends up a short report. The breakdown is done, and three alpha cores and two beta cores are packed in crash foam in the cargo hold.");
        if (!reread) {
            AddRemoveCommodity.addCommodityGainText(CASyncCatalog.ALPHA, CASyncCatalog.BREAKDOWN_ALPHA, text);
            AddRemoveCommodity.addCommodityGainText(CASyncCatalog.BETA, CASyncCatalog.BREAKDOWN_BETA, text);
        }
        close("Put the TriPad away");
    }

    void returned() {
        art("abandoned_station");
        String sys = CAAutarch.system();
        screen("AUTARCH-CLASS SUBSTRATE SIGNAL. SOURCE: " + (sys != null ? sys.toUpperCase() : "UNKNOWN") + ".");
        para("Your TriPad has picked up another Autarch-class core" + (sys != null ? " in the " + sys + " system" : "")
                + ". Your sensors officer checks the reading twice and can't find anything wrong with it.");
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
