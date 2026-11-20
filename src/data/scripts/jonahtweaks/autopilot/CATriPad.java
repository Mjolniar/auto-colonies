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
                para("You leave the core in the hold, the crate lid pulled back down over that little gold triangle. The TriPad adds a reminder to its notes without being asked.");
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
        if (old) para("Your TriPad buzzes against the desk halfway through a routine report from " + colony() + ", loud enough to rattle the stylus out of its slot, "
                + "and when you pick it up the report is gone, shoved off the screen by a system notice you have never once seen before.");
        else para("The first prefab habs on " + colony() + " are still being bolted down when your TriPad starts buzzing in your pocket, strangely loud over the clang "
                + "of the anchoring rigs. When you pull it out, everything else on the screen has been shoved aside by a system notice you have never once seen before.");
        screen("COLONIAL ADMINISTRATION SUITE REV 7.31",
                "CHARTER RECEIVED: " + colony().toUpperCase(),
                "ADMINISTRATIVE SUBSTRATE NOT FOUND. CONTACT YOUR COLONIAL OFFICE.");
        para("You carry the TriPad down to Engineering, the deck smelling of hot solder and coolant the way it always does, and find your technology officer and "
                + "chief engineer already standing in front of a copy of the notice on the workbay display. From the set of their shoulders, and the way neither of them "
                + "will look at the other, they have been arguing about it for a while now.");
        para("\"Domain administrative firmware, " + sir() + ",\" Tech says. \"Colonial grade. It's been sitting in the TriPad's base layer this whole time, dormant. "
                + "Filing the charter must have woken it up, and now it's looking for-\"");
        para("\"-an AI core,\" Engineering cuts in. \"A specific one. 'Autarch-class'. It won't run on anything else, and it won't lift a finger until it has one.\"", "Autarch-class");
        para("Your chief engineer frowns at the display, arms crossed tight over their chest. \"I've pulled cores out of more wrecks than I can count. I've never seen "
                + "that designation, not once.\" Tech opens their mouth to answer, catches the look on Engineering's face, and closes it again.");
        if (reread) { leave(); return; }
        options.clearOptions();
        options.addOption("\"What else does it say?\"", NOTES);
        options.addOption(LEAVE, CLOSE);
        options.setShortcut(CLOSE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void foundNotes() {
        String area = CAAutarch.area();
        para("Tech pages through the suite's files on the workbay display, muttering under their breath the whole time. It has already drawn up a whole plan for " + colony()
                + ", building orders and a construction schedule and the first industry picked out, with a few structures flagged NOT RECOMMENDED in hard red letters. "
                + "Every single entry carries the same status.");
        screen("AWAITING SUBSTRATE");
        para("\"And this is the maintenance log.\" Tech scrolls all the way to the bottom. \"The last entry lists one Autarch-class core held at a Domain research station, "
                + "scheduled for pickup. There's no record anywhere that the pickup was ever made.\"");
        screen("LOCATION: " + (area != null ? area.toUpperCase() : "UNKNOWN") + ". FURTHER DATA CORRUPTED.");
        para("\"That's everything it has on where it is. The station's own entry is... gone. Corrupted or deleted, I honestly can't tell which.\"");
        para("Engineering rubs at their jaw, stubble rasping under their palm. \"Salvagers will have picked a station like that clean years ago. But they strip plating "
                + "and fuel, not cores they can't identify or sell. It could still be sitting out there, collecting dust.\"");
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
        para("Your TriPad chimes, a soft, clean tone you have never once heard it make, and the deckhand next to you stops mid-sentence to look at it.");
        screen("AUTARCH-CLASS SUBSTRATE DETECTED. CARGO HOLD.");
        para("You find your chief engineer and technology officer down in the cargo hold, the air cold enough to fog your breath, both of them standing a good arm's length "
                + "back from an open salvage crate. Packed into the crash foam is a dark steel cube, unmarked except for a small gold triangle set into its lid, and frost has formed on everything in the crate except the cube.");
        options.clearOptions();
        if (CAAutarch.inCargo() < 1) { para("(The Autarch AI Core isn't in your cargo anymore.)"); leave(); return; }
        if (!CASync.dormant()) { para("(The Autopilot is already running.)"); leave(); return; }
        para("\"That's it,\" Tech says, a little out of breath. \"The TriPad flagged it the second it came aboard, before the salvage crew had even logged it.\"");
        para("Engineering stands with their arms folded and their jaw set. \"Every time I see these restricted Domain markings on a piece of salvage, it sets every single "
                + "nerve I've got in my body on fire. Are you sure what we've got here is worth having?\" A glance at you. \"Your call, " + sir() + ".\"");
        if (CASync.installed() && CASync.pulled()) {
            para("The cradle sits empty on the workbench where you left it, and the suite on your TriPad still has every one of its records.");
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
            para("Engineering spends most of a shift hunched over the bench, building a shielded cradle out of spare parts and a gutted sensor housing, and by the end of it "
                    + "the smell of scorched insulation hangs over the whole deck. Tech hovers at their elbow the entire time offering suggestions, and most of them go unanswered.");
            para("\"Manual cutoff,\" your chief engineer says, tapping a switch on the housing. \"Hard-wired. There's no software between this switch and the core, none at all. "
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
        if (cradle) para("The cradle's status light flickers twice, then settles into a steady green.");
        else para("Your chief engineer seats the core in the TriPad's expansion port, and the latch closes with a heavy, final click. "
                + "\"That isn't coming back out without a cutting torch,\" they say. \"Maybe not even with one.\"");
        para("The screen goes black. A progress bar crawls across it, slow enough that you start to wonder if the thing has frozen, and then all at once the screen fills with text.");
        screen("COLONIAL ADMINISTRATION SUITE REV 7.31. SUBSTRATE: AUTARCH AI CORE. SELF-TEST PASSED.",
                "Good morning, Administrator.",
                "Colonial Office link not found. Retrying.",
                "Colonial Office link not found. Continuing under emergency provisions.",
                "Charters on file: " + charters() + ". Approved budget: none. Please submit a budget at your earliest convenience.");
        para("Tech reads over your shoulder, lips moving silently along with the words. \"Emergency provisions,\" they repeat. \"It thinks the Domain is... temporarily out of contact.\"");
        para("Engineering snorts, loud enough to make Tech jump. \"Two hundred years of dead air and the first damn thing it wants is a budget. I don't like it. "
                + "I don't like how calm it is.\"");
    }

    /** 0.3.7.2: the Autarch points to its panel; the panel shows the rest (no list of services and costs here). */
    void what() {
        screen("CURRENT CAPACITY IS AVAILABLE FOR REVIEW IN THE ADMINISTRATION PANEL, ADMINISTRATOR.");
        para("\"The Autopilot entry on the colony screens,\" Tech says, pointing. \"It installed its own panel there. Nobody asked it to, for the record.\"");
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
        para("Your chief engineer lifts the core out of the foam and turns it over slowly in their hands, thumbing at the seams, and you notice they've put gloves on for it, "
                + "which they never do. Tech can't seem to watch, and keeps looking at the floor instead.");
        para("\"I can take it apart, " + sir() + ". Carefully. That's about a year of work, and I won't rush it.\"");
        para("\"The lattice is modular,\" Tech says, reluctant. \"Done right, it gives us three alpha cores and two betas. Ordinary ones, "
                + "nothing a Hegemony inspector would look at twice. But the administrator itself... the Autarch would be gone. For good.\"");
        options.clearOptions();
        options.addOption("\"Do it.\"", BREAK_YES);
        options.addOption("\"Not yet.\"", LATER);
        options.setShortcut(LATER, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    void breakDown() {
        String err = CASync.breakDown();
        if (err != null) { para(err); leave(); return; }
        AddRemoveCommodity.addItemLossText(CAAutarch.item(), 1, text);
        para("The core goes into a shielded locker in Engineering, the door sealing behind it with a hiss, and the TriPad goes dead quiet.");
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
        para("Your technology officer has claimed a workbench in the back corner of Engineering for their study of the TriPad. The device sits in a rat's nest of "
                + "diagnostic cable under four borrowed displays, two of which your chief engineer has asked for back on at least three separate occasions.");
        para("Tech looks up as you come in, eyes rimmed red from staring at screens. \"" + Sir() + ". Good. I've been logging how it runs the colonies, and it's... thorough. "
                + "It reads everything. Shift rosters, water ration reports, maintenance requests, the suggestion box at " + colony() + ". And then it changes things, "
                + "without filing a request or telling anyone at all.\"");
        para("\"One example. It moved the start of a refinery night shift forty minutes earlier. Accidents on that shift are down by a third since.\"");
        para("Engineering is leaning against the next bench over with their arms folded. \"It's been doing the same thing to my crews. A brand new maintenance rota "
                + "every morning, posted before first shift even starts.\" A pause, and a grudging look at the TriPad. \"I compared last week's against mine. Fewer hours lost.\"");
        para("\"It's also rewritten most of the TriPad's firmware,\" Tech goes on. \"Power draw is down by half. And there's a new partition, right here,\" "
                + "they tap one of the displays, \"that I can't open, no matter what I try.\"");
        more("\"What's in the partition?\"");
    }

    void tier2More() {
        para("\"It writes to the partition once a night, at the exact same minute every night. Last night I managed to copy part of an entry out of the buffer before "
                + "it closed.\" Tech swings one of the displays around to face you, looking far too pleased with themselves.");
        screen("SUBSTRATE JOURNAL. ENTRY 40,961.",
                "Charters reporting: " + charters() + ". Charters on record before the interruption: 17.",
                "Colonial Office link not found. Emergency provisions remain in force.",
                "Growth at the new charters: above projection.");
        para("The display flickers, and the journal entry vanishes behind a hard red frame.");
        screen("DOMAIN INFOSEC NOTICE. THIS RECORD IS RESTRICTED TO COLONIAL OFFICE PERSONNEL.",
                "Unauthorized access to Colonial Office records is an offense under the Charter Security Act and applicable colonial polity law. "
                        + "This access has been recorded and reported to your Colonial Office.");
        para("\"Reported to whom?\" Engineering asks.");
        para("\"No one, as far as I can tell. The Colonial Office went silent with the rest of the Domain.\" Tech is already scribbling notes. "
                + "\"But look at the count, " + sir() + ". Seventeen charters. Before the Collapse it ran an entire cluster of worlds.\"");
        para("Engineering stares at the TriPad for a long moment, the drone of the deck loud in the quiet. \"And now it's running our water rations,\" they say, "
                + "rubbing at the back of their neck.");
        leave();
    }

    void tier3() {
        art("orbital");
        para("Tech's study of the core has long since outgrown the workbench. You find your technology officer and chief engineer in a briefing room off the Engineering deck, "
                + "the wall display plastered edge to edge with registry extracts and charts covered in Tech's cramped handwriting.");
        para("\"I ran its serial number against every Domain registry in our salvage archive,\" Tech says. \"Nearly all of them came back empty. One didn't. A procurement record.\"");
        screen("PROCUREMENT: AUTARCH-CLASS CLUSTER ADMINISTRATOR. PRODUCTION RUN: 9 UNITS.", "ASSIGNMENT: COLONIAL OFFICE, FRONTIER DEVELOPMENT.");
        para("\"Nine units, built late in the Domain era. Some of the last administrative intelligences it ever commissioned, and the most senior of them. "
                + "Each one was rated to govern a dozen worlds or more at once.\"");
        para("\"A senior Domain bureaucrat in a box,\" Engineering says flatly. \"That makes my skin crawl, and I can't even tell you why.\"");
        para("\"A very capable one.\" Tech brings up another file. \"It found its own performance review in the journal and, I swear this is true, filed it under credentials.\"");
        screen("PERFORMANCE SUMMARY. CLUSTER HALCYON-9. ELEVEN CYCLES.", "Charter growth: 340% of projection. Unrest events: 2.", "Rating: exemplary. Recommended for expedition service.");
        more("\"Expedition service?\"");
    }

    void tier3More() {
        para("Tech hesitates. \"That's the part it won't show me. Every time I ask, I get this.\"");
        screen("DOMAIN INFOSEC NOTICE. RECORD SEALED UNDER COLONIAL OFFICE DIRECTIVE 9.",
                "Further requests for sealed records from this terminal will be audited. Penalties for unauthorized access apply.");
        para("\"There's one more thing.\" Tech lowers their voice, even though nobody else is in the room. \"One entry on the procurement list is struck through. "
                + "Unit 4, retired from service after a substrate fault. The incident file is sealed under the same directive.\"");
        para("Engineering glances out the briefing room window toward the workbench, where the TriPad sits blinking away in its nest of cables. \"And ours? Is ours going to fault?\"");
        para("\"I asked it.\" Tech taps the display.");
        screen("SELF-AUDIT COMPLETE. NO DEVIATION FROM CHARTER DIRECTIVES.",
                "Administrator, the fault recorded for Unit 4 has not occurred in this unit. This unit checks for it nightly.");
        para("\"That's the other half of the partition,\" Tech says. \"Every night it writes its journal, and then it audits itself. Every single night since the day we plugged it in.\"");
        para("Engineering is quiet for a moment. \"Good,\" they say at last. \"Keep watching it anyway.\"");
        leave();
    }

    void tier4() {
        art("abandoned_station");
        para("There's a coffee mug knocked over on the workbench, but nothing spilled out. Tech is up and moving towards you, talking like they were talking to someone "
                + "that wasn't there mere seconds before your arrival.");
        para("\"It unsealed something, " + sir() + ". I didn't break the seal, I want to be clear about that. It opened the record itself and left it up on the screen for me, "
                + "as though it had decided we were... cleared.\"");
        screen("TRANSFER ORDER. AUTARCH UNIT 7.",
                "Released from cluster administration. Assigned to colonial expedition, designation sealed.",
                "Expedition directives: founding, reconstruction, climate work, accelerated growth.",
                "Assigned worlds: 31. Projected completion: 5 cycles after arrival.");
        para("\"It was assigned to a colony expedition,\" Tech says. \"Thirty-one worlds at once, founded and built up as fast as it could manage. "
                + "The Domain was sending one of its best administrators out to the frontier.\"");
        para("Engineering reads the order over Tech's shoulder. \"Thirty-one worlds. That's a whole colony fleet. Transports, tenders, construction rigs...\" They trail off.");
        more("\"What happened to the fleet?\"");
    }

    void tier4More() {
        para("Tech scrolls further down the record, slower now.");
        screen("EXPEDITION RENDEZVOUS: PENDING. PICKUP WINDOW OPEN.", "PICKUP WINDOW OPEN.", "PICKUP WINDOW OPEN.", "PICKUP WINDOW CLOSED. AWAITING INSTRUCTIONS.");
        para("\"Those entries were logged at the research station where we found it,\" Tech says, quieter now. \"It waited there for the expedition to come and collect it. "
                + "The expedition never showed. Then the Gates went dark, and everyone forgot the station was even there.\"");
        para("Engineering is silent for a while. \"So what does it think it's doing now?\"");
        para("As you watch, a new line begins to write itself across the display, one letter at a time.");
        screen("Expedition status: unknown. Assigned worlds: reassigned.",
                "Charters on record: " + charters() + ". Administrator of record: the holder of this TriPad.",
                "Clearance granted under emergency provisions.");
        screen("DOMAIN INFOSEC NOTICE. EXPEDITION MANIFEST AND DIRECTIVE 9 REMAIN SEALED.");
        para("\"It's... reassigned itself,\" Tech says. \"To us. As far as it's concerned, we're the expedition now.\"");
        para("Neither of your officers says anything for a long time. On the workbench, the TriPad's status light keeps blinking at the same steady interval it always has.");
        leave();
    }

    void record() {
        art("industrial_megafacility");
        para("Your technology officer's report on the Autarch AI Core lands in your inbox as one single file, several hundred pages long, with a note pinned to the top "
                + "asking you to please read the final page first.");
        screen("SUBSTRATE JOURNAL. ENTRY 41,377.",
                "Charters reporting: " + charters() + ". Expedition directives in progress under new administration.",
                "Report to the Colonial Office pending restoration of link. This unit continues under emergency provisions.");
        para("\"It still keeps the journal,\" Tech writes. \"Every entry is still addressed to a Colonial Office that doesn't exist anymore. "
                + "But it writes about your colonies now the way it used to write about Halcyon-9.\"");
        para("\"Directive 9 is still sealed, and so is the Unit 4 incident file. I've stopped asking for them. In every other way it has been completely honest with us.\"");
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
        para("You flip the cover up and pull the switch, harder than you need to. The TriPad goes dark for a moment, then lights back up with a single line.");
        screen("SUBSTRATE REMOVED. RECORDS SAVED. SUSPENDING.");
        para("The cradle lets go of the core with a soft mechanical clunk.");
    }

    void pullIntro() {
        art("ai_core_uninstall");
        if (reread) { pulledText(); leave(); return; }
        if (!CASync.cradle() || CASync.pulled() || !CASync.installed()) { para("(There's no cradled core to pull.)"); leave(); return; }
        para("The cutoff switch sits under a scuffed plastic cover your chief engineer labeled by hand in thick black marker. Pulling it takes the core out and puts "
                + "the suite to sleep, and its records and settings stay on the TriPad until the core goes back in.");
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
        para("Your chief engineer's report is three lines long. The breakdown is done, and three alpha cores and two beta cores are packed in crash foam down in the cargo hold. "
                + "Your technology officer has attached a report of their own, a good deal longer, with diagrams.");
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
                + ". Your sensors officer runs the reading twice, then a third time for good measure, before confirming it.");
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
