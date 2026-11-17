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
 * RETURN (a new core after a sale). 0.3.8.3: TIER2, TIER3, TIER4 (each tier opening) and RECORD (every Tier IV package
 * bought): your tech officer's study of the core and its history (CASync.nextBeat). They replace the summary that opened
 * after the Story page closed. 0.3.8.4: the crew scenes are written like the game's own Tech and Engineering scenes
 * (rules.csv, shroudedSubstrate); every dialog ends with Leave, as the game's do.
 */
public class CATriPad implements InteractionDialogPlugin {
    public static final String FOUND = "found", CHIME = "chime", INSTALL = "install", FEED = "feed", PULL = "pull", BROKEN = "broken", RETURN = "return";
    /** 0.3.8.3: the tier scenes, in story order (CASync.beatDue says when each is due). */
    public static final String TIER2 = "tier2", TIER3 = "tier3", TIER4 = "tier4", RECORD = "record";
    public static final String[] TIER_BEATS = {TIER2, TIER3, TIER4, RECORD};
    static final String CLOSE = "close", LATER = "later", SLOT = "slot", CRADLE = "cradle", BREAK = "break", BREAK_YES = "breakYes", RESEAT = "reseat",
            WHAT = "what", NOTES = "notes", HAND = "hand", KEEP = "keep", OUT = "out", MORE = "more";
    /** 0.3.8.4: the game's exit option for its dialogs. */
    static final String LEAVE = "Leave";

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

    void leave() { close(LEAVE); }

    // ---------------- the dialog ----------------

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        this.text = dialog.getTextPanel();
        this.options = dialog.getOptionPanel();
        dialog.setPromptText("");
        if (!reread && !CHIME.equals(beat) && !PULL.equals(beat)) CASync.see(beat);   // the chime is seen per core (CASync.shown), the pull once it's done
        if (!reread) CASync.shown(beat);   // 0.3.8.9: off the pending list; the chime is marked seen here, once it shows
        try { show(); }
        catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "story beat " + beat + " failed", t);
            para("(The TriPad's screen flickers. Something went wrong showing this; see starsector.log.)");
            leave();
        }
        CAStoryIntel.ensure();
    }

    void show() {
        if (FOUND.equals(beat)) found();
        else if (CHIME.equals(beat)) chime();
        else if (INSTALL.equals(beat)) { portrait(); installText(CASync.cradle()); leave(); }
        else if (FEED.equals(beat)) feed();
        else if (PULL.equals(beat)) pullIntro();
        else if (BROKEN.equals(beat)) broken();
        else if (RETURN.equals(beat)) returned();
        else if (TIER2.equals(beat)) tier2();
        else if (TIER3.equals(beat)) tier3();
        else if (TIER4.equals(beat)) tier4();
        else if (RECORD.equals(beat)) record();
        else leave();
    }

    public void optionSelected(String label, Object data) {
        if (!CRADLE.equals(data) && data != null) dialog.addOptionSelectedText(data);
        try { choose(data); }
        catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "story option " + data + " in " + beat + " failed", t);
            para("(Something went wrong; see starsector.log.)");
            leave();
        }
    }

    void choose(Object data) {
        if (CLOSE.equals(data)) { dialog.dismiss(); return; }
        if (MORE.equals(data)) {   // 0.3.8.3: a tier scene's second page
            if (TIER2.equals(beat)) tier2More();
            else if (TIER3.equals(beat)) tier3More();
            else if (TIER4.equals(beat)) tier4More();
            else leave();
            return;
        }
        if (FOUND.equals(beat)) {
            if (NOTES.equals(data)) { foundNotes(); return; }
            if (HAND.equals(data)) { handOver(); return; }
            // 0.3.8.9: no fast buildout when the hand-over was held back (managed colony limit)
            if (data instanceof Float) { handOver(); if (market != null && CASync.has("buildout") && CASync.modeNote == null) CABuildout.start(market, (Float) data); return; }
            if (KEEP.equals(data)) { screen("CHARTER FILED: ADVISORY."); leave(); return; }
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
                leave();
                return;
            }
        } else if (PULL.equals(beat)) {
            if (OUT.equals(data)) { pullOut(); return; }
            if (LATER.equals(data)) { para("You leave the cutoff alone."); leave(); return; }
        }
        dialog.dismiss();
    }

    // ---------------- the dormant firmware (first colony) ----------------

    /** 0.3.6.6: "sir" or "ma'am", as the game's $playerSirOrMadam. */
    static String sir() {
        try { return Global.getSector().getPlayerPerson().getGender() == FullName.Gender.FEMALE ? "ma'am" : "sir"; }
        catch (Throwable t) { return "Captain"; }
    }

    /** 0.3.8.4: "Sir" or "Ma'am" at the start of a sentence, as the game's $PlayerSirOrMadam. */
    static String Sir() {
        String s = sir();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    void found() {
        art("orbital_construction");
        if (!CASync.dormant() && !reread) { foundOnline(); return; }
        boolean old = market != null && market.getDaysInExistence() > 60f;
        if (old) para("Your TriPad buzzes in the middle of a routine report from " + colony() + ". A system notice you have never seen before has pushed the report off the screen.");
        else para("The first prefab habs on " + colony() + " are still being anchored when your TriPad buzzes. A system notice you have never seen before has pushed everything else off the screen.");
        screen("COLONIAL ADMINISTRATION SUITE REV 7.31",
                "CHARTER RECEIVED: " + colony().toUpperCase(),
                "ADMINISTRATIVE SUBSTRATE NOT FOUND. CONTACT YOUR COLONIAL OFFICE.");
        para("You take the TriPad down to Engineering. Your technology officer and chief engineer already have a copy of the notice up on the workbay display, "
                + "and from the look of them they have been arguing about it for some time.");
        para("\"Domain administrative firmware, " + sir() + ",\" Tech says. \"Colonial grade. It has been sitting in the TriPad's base layer all along, um, dormant. "
                + "Filing the charter appears to have woken it, and now it is looking for-\"");
        para("\"-an AI core,\" Engineering cuts in. \"A specific one. 'Autarch-class'. It won't run on anything else, and it won't do anything at all until it has one.\"", "Autarch-class");
        para("Your chief engineer frowns at the display. \"I've pulled cores out of a lot of wrecks. I've never seen that designation.\" "
                + "Tech starts to answer, catches Engineering's expression, and thinks better of it.");
        if (reread) { leave(); return; }
        options.clearOptions();
        options.addOption("\"What else does it say?\"", NOTES);
        options.addOption(LEAVE, CLOSE);
        options.setShortcut(CLOSE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void foundNotes() {
        String area = CAAutarch.area();
        para("Tech pages through the suite's files on the workbay display. It has already drawn up a plan for " + colony() + ": building orders, a construction schedule, "
                + "the first industry picked out, and a few structures flagged NOT RECOMMENDED. Every entry carries the same status.");
        screen("AWAITING SUBSTRATE");
        para("\"And this is the maintenance log.\" Tech scrolls to the bottom. \"The final entry lists a single Autarch-class core held at a Domain research station, "
                + "scheduled for pickup. There is no record that the pickup was ever made.\"");
        screen("LOCATION: " + (area != null ? area.toUpperCase() : "UNKNOWN") + ". FURTHER DATA CORRUPTED.");
        para("\"That is everything it has on the location. The station's own entry is, um... gone. Corrupted or deleted, I can't tell which.\"");
        para("Engineering rubs at their jaw. \"Salvagers will have picked over a station like that long ago. But they strip plating and fuel, "
                + "not cores they can't identify or sell. It could still be sitting there.\"");
        note("New objective: find the Autarch AI Core" + (area != null ? " somewhere in the " + area + "." : "."));
        CAStoryIntel.showIn(text);   // 0.3.6.8: the objective's intel, as a mission shows when you take it (0.3.7.1: the story entry)
        leave();
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
        if (market == null) { leave(); return; }
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
            para(CASync.modeNote.endsWith(".") ? CASync.modeNote : CASync.modeNote + ".");   // 0.3.9.8: the note ends with its own period
        } else {
            screen("ACKNOWLEDGED. " + colony().toUpperCase() + ": " + c.mode.label.toUpperCase() + ".");
            gained(colony() + " is in " + c.mode.label + ", with the recommended settings.");
        }
        leave();
    }

    // ---------------- the chime: the core in your cargo ----------------

    void chime() {
        art("ai_core_uninstall");
        para("Your TriPad chimes, a tone you don't recognize.");
        screen("AUTARCH-CLASS SUBSTRATE DETECTED. CARGO HOLD.");
        para("You find your chief engineer and technology officer in the cargo hold, standing over an open salvage crate. "
                + "Packed in crash foam inside is a dark steel cube, unmarked except for a small gold triangle set into its lid.");
        options.clearOptions();
        if (CAAutarch.inCargo() < 1) { para("(The Autarch AI Core isn't in your cargo anymore.)"); leave(); return; }
        if (!CASync.dormant()) { para("(The Autopilot is already running.)"); leave(); return; }
        para("\"That's it,\" Tech says, a little out of breath. \"The TriPad flagged it the moment it came aboard, before the salvage crew had even logged it.\"");
        para("Engineering stands with arms folded. \"Restricted Domain markings on every face. If the Hegemony found this aboard, they would take it, "
                + "and then they would want to know where we got it.\" A glance at you. \"Your call, " + sir() + ".\"");
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
        if (err != null) { para(err); leave(); return; }
        CASync.see(INSTALL);
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        if (cradle) {
            para("Engineering spends most of a shift building a shielded cradle out of spare parts and a gutted sensor housing. "
                    + "Tech stays at their elbow throughout, offering suggestions. Most go unanswered.");
            para("\"Manual cutoff,\" your chief engineer says, tapping a switch on the housing. \"Hard-wired. There is no software between this switch and the core. "
                    + "If it does anything you don't like, you flip it.\"");
        }
        portrait();
        installText(cradle);
        options.clearOptions();
        options.addOption("\"What can you do?\"", WHAT);
        options.addOption(LEAVE, CLOSE);
        options.setShortcut(CLOSE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void installText(boolean cradle) {
        if (cradle) para("The cradle's status light turns green.");
        else para("Your chief engineer seats the core in the TriPad's expansion port, and the latch closes with a heavy click. "
                + "\"That isn't coming back out without a cutting torch,\" they say. \"Possibly not with one.\"");
        para("The screen goes dark. A progress bar crawls across it, and then the screen fills with text.");
        screen("COLONIAL ADMINISTRATION SUITE REV 7.31. SUBSTRATE: AUTARCH AI CORE. SELF-TEST PASSED.",
                "Good morning, Administrator.",
                "Colonial Office link not found. Retrying.",
                "Colonial Office link not found. Continuing under emergency provisions.",
                "Charters on file: " + charters() + ". Approved budget: none. Please submit a budget at your earliest convenience.");
        para("Tech reads over your shoulder, lips moving. \"Emergency provisions,\" they repeat. \"It believes the Domain is, um... temporarily out of contact.\"");
        para("Engineering snorts. \"Two centuries of silence, and the first thing it asks for is a budget.\"");
    }

    /** 0.3.7.2: the Autarch points to its panel; the panel shows the rest (no list of services and costs here). */
    void what() {
        screen("CURRENT CAPACITY IS AVAILABLE FOR REVIEW IN THE ADMINISTRATION PANEL, ADMINISTRATOR.");
        para("\"The Autopilot entry on the colony screens,\" Tech says. \"It has, um, installed its panel there. Nobody asked it to.\"");
        leave();
    }

    void reseat() {
        String err = CASync.install(false);
        if (err != null) { para(err); leave(); return; }
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        portrait();
        screen("SUBSTRATE RESTORED. RESUMING FROM LAST RECORD.", "Welcome back, Administrator.");
        gained("The Autopilot is running again with " + CASync.total() + " bandwidth and every ability it had.");
        leave();
    }

    void breakAsk() {
        para("Your chief engineer lifts the core out of the foam and turns it over slowly, frowning at the seams. Tech watches with a pained expression.");
        para("\"I can take it apart, " + sir() + ". Carefully. That's about a year of work, and I won't rush it.\"");
        para("\"The lattice is modular,\" Tech says reluctantly. \"Done properly, it yields three alpha cores and two betas. Ordinary ones, "
                + "nothing a Hegemony inspector would look at twice. But the administrator itself, the, um... the Autarch would be gone.\"");
        options.clearOptions();
        options.addOption("\"Do it.\"", BREAK_YES);
        options.addOption("\"Not yet.\"", LATER);
        options.setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void breakDown() {
        String err = CASync.breakDown();
        if (err != null) { para(err); leave(); return; }
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        para("The core goes into a shielded locker in Engineering, and the TriPad falls silent.");
        note("In " + CASyncCatalog.BREAKDOWN_DAYS + " days: " + CASyncCatalog.cores(0, CASyncCatalog.BREAKDOWN_BETA, CASyncCatalog.BREAKDOWN_ALPHA) + ".");
        leave();
    }

    // ---------------- feeding, pulling, breakdown done, a new core ----------------

    void feed() {
        portrait();
        int[] f = CASync.fed();
        screen("SUBSTRATE ADDED: " + CASyncCatalog.cores(f[0], f[1], f[2]).toUpperCase() + ".", "Capacity increased. Thank you, Administrator.");
        gained("The Autopilot has " + CASync.total() + " bandwidth.");
        leave();
    }

    // ---------------- 0.3.8.3: the tier scenes (your tech officer's study of the core) ----------------
    // 0.3.8.4: written like the game's own Tech and Engineering scenes; the TriPad sits on Tech's workbench in Engineering.

    /** Charters the core reports: your colonies. */
    static int charters() { return Math.max(1, Misc.getPlayerMarkets(false).size()); }

    void tier2() {
        art("city_from_above");
        para("Your technology officer has claimed a workbench in a corner of Engineering for the study of the TriPad. The device sits in a nest of diagnostic cable "
                + "beneath four borrowed displays, two of which your chief engineer would like returned.");
        para("Tech looks up as you come in. \"" + Sir() + ". Good. I have been logging how it runs the colonies, and it is... thorough. It reads everything. "
                + "Shift rosters, water ration reports, maintenance requests, the suggestion box at " + colony() + ". And then it changes things, "
                + "without filing a request or, um, informing anyone.\"");
        para("\"One example. It moved the start of a refinery night shift forty minutes earlier. Accidents on that shift are down by a third since.\"");
        para("Engineering is leaning against the next bench with arms folded. \"It's been doing the same to my crews. A new maintenance rota every morning, "
                + "posted before first shift.\" A pause. \"I compared last week's against mine. Fewer hours lost.\"");
        para("\"It has also rewritten most of the TriPad's firmware,\" Tech goes on. \"Power draw is down by half. And there is a new partition, here,\" "
                + "a tap at one of the displays, \"which I cannot open.\"");
        more("\"What's in the partition?\"");
    }

    void tier2More() {
        para("\"It writes to the partition once a night, at the same minute every night. Last night I copied part of an entry out of the buffer before it closed.\" "
                + "Tech swings a display around to face you.");
        screen("SUBSTRATE JOURNAL. ENTRY 40,961.",
                "Charters reporting: " + charters() + ". Charters on record before the interruption: 17.",
                "Colonial Office link not found. Emergency provisions remain in force.",
                "Growth at the new charters: above projection.");
        para("The display flickers, and a red frame replaces the journal entry.");
        screen("DOMAIN INFOSEC NOTICE. THIS RECORD IS RESTRICTED TO COLONIAL OFFICE PERSONNEL.",
                "Unauthorized access to Colonial Office records is an offense under the Charter Security Act and applicable colonial polity law. "
                        + "This access has been recorded and reported to your Colonial Office.");
        para("\"Reported to whom?\" Engineering asks.");
        para("\"No one, as far as I can tell. The Colonial Office went silent with the rest of the Domain.\" Tech is already taking notes. "
                + "\"But look at the count, " + sir() + ". Seventeen charters. Before the Collapse it administered an entire cluster of worlds.\"");
        para("Engineering looks at the TriPad for a long moment. \"And now it's running our water rations.\"");
        leave();
    }

    void tier3() {
        art("orbital");
        para("Tech's study of the core has outgrown the workbench. You find your technology officer and chief engineer in a briefing room off the Engineering deck, "
                + "its wall display covered in registry extracts and annotated charts.");
        para("\"I ran its serial number against every Domain registry in our salvage archive,\" Tech says. \"Nearly all of them came back empty. One did not. A procurement record.\"");
        screen("PROCUREMENT: AUTARCH-CLASS CLUSTER ADMINISTRATOR. PRODUCTION RUN: 9 UNITS.", "ASSIGNMENT: COLONIAL OFFICE, FRONTIER DEVELOPMENT.");
        para("\"Nine units, built late in the Domain era. Among the last administrative intelligences it commissioned, and the most senior. "
                + "Each one was rated to govern a dozen worlds or more at once.\"");
        para("\"A senior Domain bureaucrat,\" Engineering says flatly. \"In a box.\"");
        para("\"A very capable one.\" Tech brings up another file. \"It found its own performance review in the journal and, um, filed it under credentials.\"");
        screen("PERFORMANCE SUMMARY. CLUSTER HALCYON-9. ELEVEN CYCLES.", "Charter growth: 340% of projection. Unrest events: 2.", "Rating: exemplary. Recommended for expedition service.");
        more("\"Expedition service?\"");
    }

    void tier3More() {
        para("Tech hesitates. \"That is the part it won't show me. Each time I ask, I receive this.\"");
        screen("DOMAIN INFOSEC NOTICE. RECORD SEALED UNDER COLONIAL OFFICE DIRECTIVE 9.",
                "Further requests for sealed records from this terminal will be audited. Penalties for unauthorized access apply.");
        para("\"There is one more thing.\" Tech lowers their voice, though no one else is in the room. \"One entry on the procurement list is struck through. "
                + "Unit 4, retired from service after a substrate fault. The incident file is sealed under the same directive.\"");
        para("Engineering glances through the briefing room window toward the workbench where the TriPad sits. \"And ours? Is it going to fault?\"");
        para("\"I asked it.\" Tech taps the display.");
        screen("SELF-AUDIT COMPLETE. NO DEVIATION FROM CHARTER DIRECTIVES.",
                "Administrator, the fault recorded for Unit 4 has not occurred in this unit. This unit checks for it nightly.");
        para("\"That is the other half of the partition,\" Tech says. \"Each night it writes its journal, and then it audits itself. Every night since the day we installed it.\"");
        para("Engineering is quiet for a moment. \"Good,\" they say at last. \"Keep watching it anyway.\"");
        leave();
    }

    void tier4() {
        art("abandoned_station");
        para("Your technology officer has not slept. Empty coffee bulbs are lined up along the edge of the workbench, and Tech starts talking before you are fully through the hatch.");
        para("\"It unsealed something, " + sir() + ". I didn't break the seal, I want to be clear about that. It opened the record itself and left it on the screen for me, "
                + "as though it had decided we were... cleared.\"");
        screen("TRANSFER ORDER. AUTARCH UNIT 7.",
                "Released from cluster administration. Assigned to colonial expedition, designation sealed.",
                "Expedition directives: founding, reconstruction, climate work, accelerated growth.",
                "Assigned worlds: 31. Projected completion: 5 cycles after arrival.");
        para("\"It was assigned to a colony expedition,\" Tech says. \"Thirty-one worlds at once, founded and built up as quickly as it could manage. "
                + "The Domain was sending one of its best administrators out to the frontier.\"");
        para("Engineering reads the order over Tech's shoulder. \"Thirty-one worlds. That's a whole colony fleet. Transports, tenders, construction rigs...\" They trail off.");
        more("\"What happened to the fleet?\"");
    }

    void tier4More() {
        para("Tech scrolls further down the record.");
        screen("EXPEDITION RENDEZVOUS: PENDING. PICKUP WINDOW OPEN.", "PICKUP WINDOW OPEN.", "PICKUP WINDOW OPEN.", "PICKUP WINDOW CLOSED. AWAITING INSTRUCTIONS.");
        para("\"Those entries were logged at the research station where we found it,\" Tech says, more quietly. \"It waited there for the expedition to collect it. "
                + "The expedition never arrived. Then the Gates went dark, and the station was forgotten.\"");
        para("Engineering is silent for a while. \"So what does it think it's doing now?\"");
        para("As you watch, a new line writes itself across the display.");
        screen("Expedition status: unknown. Assigned worlds: reassigned.",
                "Charters on record: " + charters() + ". Administrator of record: the holder of this TriPad.",
                "Clearance granted under emergency provisions.");
        screen("DOMAIN INFOSEC NOTICE. EXPEDITION MANIFEST AND DIRECTIVE 9 REMAIN SEALED.");
        para("\"It has, um... reassigned itself,\" Tech says. \"To us. As far as it is concerned, we are the expedition now.\"");
        para("Neither of your officers says anything for a time. On the workbench, the TriPad's status light blinks at its usual steady interval.");
        leave();
    }

    void record() {
        art("industrial_megafacility");
        para("Your technology officer's report on the Autarch AI Core arrives as a single file, several hundred pages long. A note at the top asks you to read the final page first.");
        screen("SUBSTRATE JOURNAL. ENTRY 41,377.",
                "Charters reporting: " + charters() + ". Expedition directives in progress under new administration.",
                "Report to the Colonial Office pending restoration of link. This unit continues under emergency provisions.");
        para("\"It still keeps the journal,\" Tech writes. \"Every entry is still addressed to a Colonial Office that no longer exists. "
                + "But it now writes about your colonies the way it once wrote about Halcyon-9.\"");
        para("\"Directive 9 remains sealed, as does the Unit 4 incident file. I have stopped requesting them. In every other respect it has been entirely forthright with us.\"");
        para("Below that is a two-line addendum from your chief engineer. \"No faults in any nightly audit to date. Recommend we leave it running.\"");
        leave();
    }

    /** One spoken option that continues the scene on a second page, and Leave. */
    void more(String ask) {
        options.clearOptions();
        options.addOption(ask, MORE);
        options.addOption(LEAVE, CLOSE);
        options.setShortcut(CLOSE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void pulledText() {
        para("You flip the cover up and pull the switch. The TriPad goes dark for a moment, then shows one line.");
        screen("SUBSTRATE REMOVED. RECORDS SAVED. SUSPENDING.");
        para("The cradle releases the core.");
    }

    void pullIntro() {
        art("ai_core_uninstall");
        if (reread) { pulledText(); leave(); return; }
        if (!CASync.cradle() || CASync.pulled() || !CASync.installed()) { para("(There's no cradled core to pull.)"); leave(); return; }
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
        leave();
    }

    void broken() {
        art("ai_core_uninstall");
        para("Your chief engineer's report is three lines long. The breakdown is finished, and three alpha cores and two beta cores are packed in crash foam in the cargo hold. "
                + "Your technology officer has attached a report of their own, considerably longer, with diagrams.");
        if (!reread) {
            AddRemoveCommodity.addCommodityGainText(CASyncCatalog.ALPHA, CASyncCatalog.BREAKDOWN_ALPHA, text);
            AddRemoveCommodity.addCommodityGainText(CASyncCatalog.BETA, CASyncCatalog.BREAKDOWN_BETA, text);
        }
        leave();
    }

    void returned() {
        art("abandoned_station");
        String area = CAAutarch.area();
        screen("AUTARCH-CLASS SUBSTRATE SIGNAL. SOURCE: " + (area != null ? area.toUpperCase() : "UNKNOWN") + ".");
        para("The TriPad has picked up another Autarch-class core" + (area != null ? " somewhere in the " + area : "")
                + ". Your sensors officer runs the reading twice before confirming it.");
        if (!reread) {   // 0.3.8.9: read again, no new objective
            if (area != null) note("New objective: find the Autarch AI Core somewhere in the " + area + ".");
            CAStoryIntel.showIn(text);   // 0.3.6.8
        }
        leave();
    }

    // ---------------- unused InteractionDialogPlugin parts ----------------

    public void optionMousedOver(String label, Object data) { }
    public void advance(float amount) { }
    public void backFromEngagement(EngagementResultAPI result) { }
    public Object getContext() { return null; }
    public Map<String, MemoryAPI> getMemoryMap() { return null; }
}
