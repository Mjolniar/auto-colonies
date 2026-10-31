# Jonah's Colony Manager

Colony management automation (Colony Autopilot) for Starsector 0.98a. No balance changes. Battle size, sticky combat autopilot and the other balance changes moved to the separate mod Jonah's Easy Sector (2.7.0).

## Install

1. Put the `Jonah's Colony Manager` folder in `Starsector/mods/`.
2. Enable **Jonah's Colony Manager** in the launcher. Its mod id is still `jonah_tweaks`, so saves that used the old name keep working. No required dependencies. Optional integrations load only when their mod is enabled: Terraforming Made Easy, Industrial Evolution, Nexerelin, Emergent Threats, Ashes of the Domain (Building Menu Overhaul; Vaults of Knowledge research, synergies, management styles, megastructures and items; Theory of Toolbox trade contracts), Random Assortment of Things (Frontiers settlement), Station Augments, Terraforming & Station Construction, Grand.Colonies, Substance.Abuse, Volkov Industrial Conglomerate, Roider Union, Planetary Shield: Access Control, Unknown Skies, What We Left Behind, Astral Ascension. Any combination works; see "Building from source".
3. Defaults for new saves are in `data/config/colony_autopilot.json`. Every option can also be changed in-game.
4. Don't remove the mod from a save that has used it: Autopilot Storage is a market tab in your colonies.

## Using it

- **Colony panel:** the **Colony Autopilot** button on the colony screen, or right-click **Population & Infrastructure**, then **Colony Autopilot...** The button finds a free spot near the building grid and doesn't appear if there's none; setting **Button on the colony screen** turns it off.
- **Empire panel:** Intel screen, **Colonies** tab, **Colony Autopilot**. Budget, approvals, every colony, storage, settings, log, diagnostics.
- **Modes per colony:** Off, Advisor (default: recommends, you click Build), Approve (one-click proposals), Auto (builds within your budget).
- **Goals:** Balanced, Money, Security, War production, Rapid growth, Self-sufficiency. Each colony gets a plan from its planet, hazards and resources. With Terraforming Made Easy, that includes a reachable special world (Forge, Fortress, Paradise, Hive, Death, Waste) resolved from TME's own requirements.
- **Fast buildout:** after the first colony is founded, a popup offers to build it up fast: Auto, planned for Money, starting the fastest-paying builds (value per credit) daily with everything above a credit floor (100k, 250k, 500k or 1M; 50k-2M in the colony panel). The monthly budget, per-project cap, pause on loss and new-upkeep cap don't apply; fees, the stability floor, locks and the measured-building approval do. It ends after 30 days in a row of positive colony net income, leaving the colony in Auto with its own goal. The colony panel can stop it, change the floor, or start one for any colony that loses money.
- **First switch to Approve or Auto** opens the setup page, pre-filled with the recommended settings: everything that pays for itself is on, and removals, story points and offense stay yours. **Use recommended settings** in the empire panel puts them back any time. Until setup runs, agents, research, contracts and the settlement do nothing.
- **Autopilot Storage:** a tab at every colony market. Colony items, AI cores and Antimatter Stabilizers deposited there are the only ones the manager uses.

## Rules it follows

- **Budget:** spends within the monthly budget, per-project cap and credit reserve. Emergency defense fleets may pass the monthly budget, never the reserve. Budget types: **Automatic** (recommended: 50% of last month's net income plus 15% of the credits above the reserve at the start of the month, so a young colony that loses money still gets built and spending slows as savings fall), **Share of net income**, or **Fixed amount**. Recommended reserve: a quarter of your credits at setup, 50,000-500,000; **Pause on loss** recommended off.
- **Fees:** convenience fees are fixed vanilla-scale amounts. Off, Low, Standard or High.
- **Removals:** buildings are only removed during terraforming, or anywhere with **Allow removals** on. The Antimatter Stabilizer conversion counts as a removal.
- **Story points:** player only by default. Options: one per colony, or a monthly cap.
- **Terraforming:** hazards a project adds are scored, and side effects (TME removes a new condition's conflicts) are simulated in planning.
- **AI cores:** one switch, on in the recommended settings (only cores you put in Autopilot Storage are used). Cores stack on your best-defended world; other colonies stay at 7 or fewer Hegemony inspection points. Alpha cores only go where they can be removed cleanly (configurable) and are never moved without asking.
- **Administrators:** idle ones are assigned first; hiring is on in the recommended settings. The 30-day arrival delay is optional.
- **Ambassadors (Industrial Evolution):** focus setting (worst relations first, strongest factions, nearest neighbors, or priority list only), per-faction priority (High, Normal, Ignore), and a "stop raising at" reputation level.
- **Threat response:** a colony is planned as Security once its threat estimate reaches 70%. It goes back to its own goal only after the estimate falls below 65% and at least 60 days have passed, so free port, hazard pay and edicts don't flip back and forth at the line. The estimate uses hostile factions nearby (at most 60% on their own), the Hostile Activity crisis, and raid, blockade, Pather cell and rebellion conditions. The state is kept in the save.
- **Log:** "waits for budget", "waits for next month", unavailable edicts and similar notes are logged once per save, not after every load. They can be logged again after the thing they reported happens.
- **Agents (Nexerelin):** setting **Agents** (under **Agents (Nexerelin)**): Off before setup, Defense recommended, or Defense and offense. Only agents Autopilot hired get orders; yours are left alone.
  - Nexerelin has no counter-intelligence and no bonus for an agent parked at a colony, so defense runs the operations Nexerelin's own automatic mode runs, in its order: Counter-Insurgency on a rebellion, Infiltrate Cell on an active Pather cell, Find Pirate Base for a hidden base, each at the closest colony that needs it. Then Raise Relations with a faction that has operations running against your colonies while it isn't at war with you: above Neutral its Strategic AI can't start them and drops the running ones.
  - Offense only targets factions already hostile to you; never allies, a faction in a ceasefire, or your commissioner, since a detected operation can start a war. It sabotages the military industry of a market an invasion or raid against you comes from, else Nexerelin's own military pick, at 60 percent estimated success or better, alert 0.2 or lower, one operation per enemy faction.
  - Hiring (on unless turned off): a level-1 agent for 12,000, Nexerelin's own price for a chosen specialization. Hybrid first; a Saboteur when offense is on. Never past **Agents to keep** (Auto: 1, or 2 with offense) or Nexerelin's agent limit, and only while there's work for one. Nexerelin pays the 2,000+ monthly salary itself.
  - Operation costs and hiring come out of the monthly budget, never the credit reserve. The empire panel lists your agents and the enemy operations running against your colonies, which the game itself doesn't show. Only person ids are saved, so the save loads without Nexerelin.
- **Ashes of the Domain:** research locks and upgrade paths live in Building Menu Overhaul's build menu, so the manager asks it the same questions the menu does:
  - Nothing locked by research or hidden in the menu is built or upgraded. If those rules can't be read, nothing is built or upgraded while Building Menu Overhaul is enabled, and the log says why.
  - Buildings AotD reaches by upgrading (Farming from Monoculture Plots, Orbital Works from Heavy Industry, Megaport from Spaceport) are offered as upgrades, started the way the menu starts them. A structure that upgrades into an industry needs a free industry slot.
  - AotD's starter buildings are supported with estimated effects: Monoculture Plots, Extractive Operation, Smelting, Light Production, Trade Outpost. A plan step such as Farming credits the starter it upgrades from, and an upgraded version (Artisanal Farming) counts as done.
  - Buildings the Unification Center checks by exact id aren't upgraded into AotD versions while that plan is open.
  - Plan steps that are still locked show what they need, e.g. "(locked: ...)".
  - AI cores go to AotD versions of a building type with the same priority as the vanilla building.
- **Industry synergies (Ashes of the Domain, Vaults of Knowledge):** building combinations on one colony (Ore-To-Core: Refining with Mining; Defensive Network: star fortress, Heavy Batteries and High Command; Rapid Transportation: Spaceport with Waystation; and 16 more).
  - A build's estimated value includes the synergies it starts or ends, and for Spaceport, Waystation, Maglev and Daedalus Array the efficiency they add to synergies already running.
  - Effects are modelled from VoK 5.0.7's code. Most apply in full whatever the efficiency (+2 metals, x1.5 ground defense); only some scale with it (accessibility, Resort Center income, ship quality, Syndicate Line income). Pather interest counts as a small stability effect.
  - Only synergies researched at that colony count. The colony panel shows its efficiency, the active synergies and those one building away.
- **Trade contracts (Ashes of the Domain, Theory of Toolbox):** monthly deliveries from your surplus, paid at 2-5x the export cut. Three missed months end a contract and cost merchant renown. Mode Advisor (before setup), Auto (recommended) or Off.
  - An offer is only taken when ToT's own figures (your production against your demand plus every contract you hold) leave 15% to spare, and it pays more than the exports those goods would have earned. Up to 3 per day in Auto, best gain first.
  - The empire panel lists held contracts, what they're short of, and the month's offers with pay, gain over exporting and rewards; Advisor gives each fillable offer an Accept button.
  - Commodities held contracts are short of count as shortages when scoring buildings, so colonies that would make them score higher. A missed delivery is logged.
  - Programs you issue yourself, freezing and terminating are left to you.
- **Coronal Hypershunt colony (Ashes of the Domain, Vaults of Knowledge):** AotD caps it at size 2 and allows no buildings, so nothing is built, upgraded, placed or switched there (no items, free port or hazard pay). Its port, patrol HQ and star fortress are hidden.
  - **Range:** the Coronal Wormhole's range costs 20,000 credits and 2 purified transplutonics a month per 10 light-years past the first. Autopilot keeps it at the smallest that reaches every Hypershunt Receiver in a system with one of your colonies. If you set the range yourself, it's left alone until you click "Let Autopilot manage it".
  - **Restoration:** each section is restored through a contract you issue. Every month it takes what your exports can spare and bills 10% of base price for what it gets; a shortfall only slows it. The Coronal Collector comes first, once Hypershunt Energy Distribution (receivers) is researched. The Coronal Wormhole follows when a receiver serving your colonies is past 10 light-years.
  - Auto starts a restoration only when every resource it needs is in surplus, the whole cost fits the per-project cap, the first month fits the budget, and the section's upkeep fits the new-upkeep cap.
  - Setting **Coronal Hypershunt range and restoration** (under Ashes of the Domain): Follow colony mode (default: an Auto hypershunt acts, Approve logs the recommendation once), Recommend only, Off.
  - Receivers are built in person, so the empire panel lists systems where one would pay off: your colonies there, the distance, the range it needs, free stable locations, and how many Coronal Hypershunt Taps you hold.
- **Pluto Mining Station and Nidavelir Complex (Vaults of Knowledge):** claimed by colonizing their planets. Same setting as the hypershunt (**Megastructures**), and the same restoration gates. Settings you change yourself are left alone until you click "Let Autopilot manage it".
  - **Pluto:** the laser runs at full strength (each 10% step costs 5,000 a month for about 285,000 worth of ore). Every unit of ore it digs goes to a restored forge: the Purification Core, except what the Fusion Forge needs to cover your metals and transplutonics shortages. Auto restores the Optic Command Nexus, then the Purification Core, then the Fusion Forge.
  - **Nidavelir:** manpower goes into the Nexus Core one point at a time while its four inputs are fully supplied, and comes back off when they're short, since a shortage is taken off every restored section's output. There's a 30-day wait before adding again, and a cap of 20 (where upkeep reaches 0). Auto restores the Nexus Core. The other sections' restoration, manpower and automation are left to you.
- **Research (Vaults of Knowledge):** mode Advisor (before setup), Auto (recommended) or Off.
  - Each tech you can start is valued by what it unlocks. The buildings are scored by the same engine that picks builds: the candidate techs are marked researched for one scoring pass over your colonies, then put back. Non-building effects come from VoK's code: +1 output, hazard -10%, upkeep x0.9, and hypershunt access. Half the best tech each one leads to is added.
  - Ranked by a year of that value, less databanks and the facility upkeep it adds, per research day. The empire panel lists the best six with a Start button.
  - Auto starts the best one when nothing is running or queued. It only picks techs that cost databanks alone; techs that use AI cores or colony items are left to you.
  - When the Research Facility tab is short of databanks, Auto moves them from Autopilot Storage. Sophia Anderson, if you have her, becomes head of R&D before paying, since she takes one databank off each tech. Your queue is never touched.
- **Management styles (Vaults of Knowledge "Colony Development"):** VoK lets you pick one only when founding a colony and never changes it, so Autopilot doesn't either. It scores new buildings with the style's real effects, from VoK 5.0.7's code (several tooltips claim more):
  - Centralized Urban Core: upkeep x0.8 except military; non-extractive output +min(2, size/3) per line; farming and mining -min(3, size-2).
  - Distributed Regional Network: upkeep x1.5 (x1.3 with a working Maglev); farming and mining +min(3, size-2) per line; everything else -min(4, size-2).
  - Bore City: Mining and Extractive Operation output x1.7. Its x1.3 build cost hooks a Building Menu Overhaul method that this BMO version never calls, so no surcharge applies in your game.
  - Archeo-Site: Tech-Mining x1.7. Its build whitelist is enforced through BMO's hidden list.
  - Floating City: each building adds 3-5 thruster fuel demand, and a fuel shortage lowers the city until it's lost. Nothing is built while fuel would run short, and a falling city is logged once per stage.
  - The colony panel names the colony's style and what it does to builds.
- **AotD colony items:** the Tenebrian Nanoforge, Synchrotron, Atmospheric Driver and Catalytic Core, and the Turing Engine, are placed from Autopilot Storage like vanilla items. The Coronal Hypershunt Tap (AotD uses it to build receivers), the AquaOrb (no effect) and the Hyperdimensional Processor (research) never are.
- **Underworld (AotD):** it only works on a free port, so a colony with one keeps its free port, unless stability, a punitive expedition or high threat says otherwise.
- **Theory of Toolbox's economy threads:** the daily pass waits while ToT's background workers are reading markets, or a save is under way, for at most a day.
- **Left to you (AotD):** Bifrost gates (where to put them is a strategic call, and at default weights a gate rarely pays its 20,000 a month), Grand Wonders (one-off choices; Elysium deletes items and cores when it finishes), black-site projects and custom production (ships and items you pick), and changing a management style.
- **Any mod (generic):**
  - Deposit conditions a mod registers in the game's own deposit tables are scored like vanilla ones, for Mining, Farming and any other deposit industry.
  - Any mod's colony item that registers an effect in the game's item table is placed from Autopilot Storage. It's moved as the exact stack object, since some mods keep state in it. Items without a known value are valued by the output they add.
  - Never placed: the Coronal Hypershunt Tap and AquaOrb (AotD), the Hyperdimensional Processor, and JaydeePiracy's Planetkiller and Lobster Eggs (+1 lobster for +8 Pather interest).
  - Without Grand.Colonies, nothing new is built past 12 buildings, because the colony screen can't show a 13th.
  - Tech-Mining's value falls as its ruins deplete.
- **Terraforming & Station Construction (Boggled):** terraforming projects cost only time (200 days, or 400 for a planet type change), so each one TASC offers is valued by the conditions it leaves (hazard, deposits, habitability, as for Terraforming Made Easy).
  - Auto starts the best project when none is running; Approve logs it once; the colony panel lists the best four with Start. A running project is never cancelled.
  - The Atmosphere Processor and Stellar Reflector Array are built when a worthwhile project waits only for them. The Reflector is also valued for its solar array; Autopilot never demolishes one, since that removes the array for good.
  - Estimates for Domed Cities (hazards suppressed, ground defense x0.05), Genelab (pollution removal, lobster pens), CHAMELEON (decivilization removal), Mesozoic Park and the Kletka Simulator (AI cores).
  - Plain output buildings (Hydroponics, Cloning, Cryosanctum, Domain Archaeology, Harmonic Damper) are measured like other mod buildings. The rest is never test-built on the probe colony, because some of it spawns entities, removes itself, or fails without a planet, so it's left to you.
  - Station construction needs your fleet on site, so it's yours too. TASC's build checks are cached for a day, since with AotD on each one writes three log lines.
- **Grand.Colonies:** past its threshold (default 12 buildings), each new building multiplies all of the colony's upkeep by 1.1 (its setting, read from LunaLib). That cost is added to each such build.
- **Substance.Abuse:** a Brewery makes nothing without a recipe, so one is built only when Autopilot Storage holds a recipe, or a Recipe Book the colony is big enough for, and no Brewery stands idle. The item placer then installs the recipe.
  - Drinks are valued at a quarter of base price, since few factions buy them. Other colonies' demand for drinks has no penalty, so it isn't counted as a shortage.
- **Volkov Industrial Conglomerate:** viral contamination (from Nexerelin bombardments) shrinks a colony until it's lost. The anti-EVC treatment comes first: tier 1, then tier 2 to stop the shrinking, then tier 3 at +25 relations with VIC to cancel it. VIC's soil nanites go only where farmland is vanilla (other farmland crashes them) and genetech arrives.
- **Planetary Shield: Access Control:** its shield is valued as +300 ground defense and -4 Pather interest. A colony with a working shield and Pather cells keeps its port closed, because the shield only works that way.
- **Roider Union:** Dives and Union HQ mine deposits within 1 or 6 light-years (each system's void resource and the colony's own deposits), which a probe colony can't see.
- **Unknown Skies and What We Left Behind:** Luminescent Clouds (+3 volatiles) and Fractured World (+5 ore and transplutonic ore) add straight to Mining. Water worlds are read from the game's own list, which these mods extend.
- **Astral Ascension:** a colony without a planet crashes past size 10 with it, so hazard pay never pushes one there.
- **Checked, nothing to add:** Better Colonies (its slot works through the game's slot count), Underworld, Supply Forging, LOST_SECTOR, Kon's Player Faction Bundles, Galactic Constellate, Tri-Tachyon Expansion, Luddic Enhancement, Knights of Ludd, Interstellar Imperium, Hillock, Hazard Mining (the probe covers its building), Hiver Swarm and Adversary (their attacks come through the colony crisis and expeditions the threat estimate already counts).
- **Hidden buildings:** a building the game hides (megastructure defenses, the hypershunt's port) is never upgraded, improved or given an item or AI core, and isn't listed under Locks. A hidden station's augments are left to you.
- **Mod buildings:** a building with no formula or estimate is measured: it's built on a throwaway probe colony (your colony's size and vanilla deposits, no planet or location) and the difference is read: commodities supplied and demanded, stability, accessibility, income and upkeep multipliers, ground defense, fleet size and patrols, ship quality. It's then scored like any other building. Upgrades measure both buildings on the same probe and score the difference.
  - Effects that only happen over time (monthly items, spawned fleets) aren't seen, so measured buildings score low rather than high.
  - A building whose code fails on the probe, or that registers scripts or intel when built, is "can't be measured" and never acted on; anything it added is removed.
  - Setting **Mod buildings with measured effects**: Build when best (default), Recommend only (Auto proposes them for approval), Ignore.
- **Settlement (Random Assortment of Things, Frontiers on):** one per save. Mode Advisor (before setup), Approve, Auto (recommended) or Off.
  - Founds the settlement on the best surveyed site once it makes at least the threshold (default 20,000 per month), the same way RAT's "Establish the settlement" option does. Only unclaimed planets and your own colonies; a faction's world can revoke access.
  - Builds Refining and Trading Post when they pay back within 2 years. Other facilities change how you play, so they're yours to pick.
  - Founding and facilities use the same budget, per-project cap and fees as colonies.
- **Station augments (Station Augments):** installing costs nothing and can be undone, so the manager picks the set of known augments worth the most within each station's augment points (AP).
  - Scored with the colony's goal weights, like buildings: accessibility (Command Center, Logistics Drones, Jump Engine), upkeep (Efficiency Overhaul; Sanctified Station only with an active Luddic majority), output (Hydroponics, only above what the colony already makes), ground defense (ECM Package) and the mod's own combat rating for the rest. Economy Conversion is valued as the best industry waiting for a slot.
  - Setting **Station augments**: Follow colony mode (default: Auto colonies apply it within a week, Approve colonies get the recommendation in the log once), Recommend only, Off. Every managed colony's panel shows the recommendation with an Apply button; the empire panel lists each station.
  - Augments you installed are never changed. One Autopilot added that you remove isn't added back to that colony ("Allow again" in its panel).
  - Installed augments are only replaced when the new set is worth at least 10% (and 300 per month) more, so wobbling estimates don't swap them back and forth.
  - Left to you: augments that use up an item (Shrouded, Threat), ones that give AP back for a drawback (Extreme or Special Modifications), and Economy Conversion while the colony plans for Security. Economy Conversion is never removed once added, since its industry slot may be in use.
- **Unsupported content:** mod content the manager can't support or measure is never acted on. It's analyzed and reported (see Diagnostics).

## Diagnostics

- Every load scans all buildings, conditions and items, and rates each **Supported**, **Measured** (effects read on a probe colony; listed with what was measured), **Analyzed** or **Quarantined**.
- An error in any mod code during planning quarantines that building.
- Output goes to:
  - the empire panel's **Diagnostics** section
  - `[ColonyAutopilot]` lines in `starsector-core/starsector.log`
  - `saves/common/colony_autopilot_diagnostics.json.data` (full report with probe results and errors)

## Staying working through mod updates

So many mods are covered that some will update. These five safeguards keep an update from quietly breaking Autopilot:

1. **Health check at load.** Each integration knows its mod, the version it was written against, the ids it relies on and the names it reads. Diagnostics shows each one's status:
   - **OK**
   - **Newer version, all found:** it keeps working, and its estimates are trusted a little less.
   - **Partly off:** the parts whose ids are gone can't act.
   - **Off:** its code can't be read, so it does nothing.
2. **Mod values read live.** Where a mod exposes its numbers (Roider's mining tables and ranges, What We Left Behind's ore bonus, AotD's restoration rate), Autopilot uses the mod's own values. Its copies are only a fallback. A value that changed is logged once and listed in Diagnostics.
3. **Calibration from your colonies.** Once a week, every plain building you have (working, with no AI core, item or improvement) is compared with Autopilot's prediction: upkeep for every building type, and output for buildings scored from estimates. The ratio is smoothed (70% old, 30% new), capped at 0.5x to 2x, used once a type has two readings, and kept in the save. Estimates follow a mod's rebalance this way.
4. **Margins for uncertain picks.** The game's own formulas count in full. Probe measurements count at 90%, and estimates at 85% (92% once calibrated), minus 5% on a mod version an integration wasn't written for. So an estimate must beat a known option by about 18% to be picked. In Auto, a pick scored from an estimate that costs over half the per-project cap waits for your approval.
5. **Stopping actions that don't work.** After each action that reaches into the game or another mod (a build, an item install, a research or terraforming start, a megastructure setting, a restoration, a contract), Autopilot checks that it took effect. Three failures in a row pause that action for the session, with a log line and a Diagnostics entry. Reloading tries again.

## Building from source

Source is in `src/`. It compiles against the game and optional mod jars with JDK 17:

```
javac --release 17 -cp "<core>/starfarer.api.jar;<core>/starfarer_obf.jar;<core>/fs.common_obf.jar;<core>/log4j-1.2.9.jar;<core>/json.jar;<core>/lwjgl.jar;<core>/lwjgl_util.jar;<TME>/TerraformingMadeEasy.jar;<IndEvo>/jars/IndEvo.jar;<Nexerelin>/jars/ExerelinCore.jar" -d build (all .java files under src)
jar cf jars/jonahtweaks.jar -C build data
```


The game's script class loader refuses any mod class that names `java.lang.reflect` or file classes ("File access and reflection are not allowed to scripts"), and desktop checks don't see that. All reflection goes through `CARefl`, which reaches `getMethod`, `invoke`, `getField`, `get` and `set` through method handles and never names the reflection classes. `check_banned.ps1` fails the build if any class refers to a class the game's loader bans. `JTLoaderCheck` loads every class under that loader and runs `CARefl`.

`CATerraform`, `CAIndEvo`, `CACourier`, `CADefense`, `CASettlement` and `CAAugments` reference optional mods. They're only called when that mod is enabled, so the mod runs without them. `CASettlement` and `CAAugments` also need the Kotlin runtime (LazyLib) and LunaLib on the compile classpath.

The author's build checks prove any combination works: jdeps shows only those classes reference each optional mod, every caller checks `isModEnabled`, and the jar links under `-Xverify:all` against all 32 combinations of the compiled-in optional mods. `CASynergies`, `CAContracts`, `CAHypershunt`, `CAMegastructures`, `CAResearch` and `CADevelopment` reach Vaults of Knowledge and Theory of Toolbox through reflection only (via `CARefl`), like `CAAotD`; if a name can't be found they're left out and the log says why. `JTAotDEconLinkCheck.java` checks those names; run it with `-XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote` like the game's own launch flags, since some VoK classes refer to Sins of the Past classes that aren't installed. An offline check covers the augment choice (knapsack against brute force, swap margin, shrunk budget, incompatible pairs). `CAAotD` reaches Building Menu Overhaul through reflection only, so it needs no jar at compile time.

The colony-mod integrations (`CATasc`, `CAGrand`, `CABrewery`, `CAMods`) use literal ids, reflection or method handles only, so no jar is needed. TASC is reached through `MethodHandles.findStatic`, because plain reflection on its `boggledTools` needs Illustrated Entities' classes. `JTColonyModsLinkCheck.java` checks those names against the TASC and LunaLib jars.

## Test steps

1. Start or load a save with at least one colony. In `starsector.log`, check for the `[ColonyAutopilot] scan done` line and no `[ColonyAutopilot]` warnings.
2. Open the colony panel. Check that the profile, mode, plan and top 3 picks render, and that clicking a setting keeps the scroll position.
3. Switch one colony to **Auto**. The setup popup should open with all groups expanded and a station line item. Save it.
4. Deposit a colony item in **Autopilot Storage**, then advance time a few days. The log should show it being installed, and the budget should count the fee.
5. Open Intel, Colonies, Colony Autopilot. Check the budget numbers, the log entries and the Diagnostics list.
6. Optional: with Terraforming Made Easy, build a terraforming structure at an Auto colony, then check that a plan step starts on it within a few days.
7. Save and reload. The log should not repeat "threat is high", "waits for next month" or "Edict ... unavailable" lines from before the reload.
8. With Ashes of the Domain: open a colony's panel. Recommended builds should include its starter buildings (e.g. Smelting), never a building its build menu locks; locked plan steps show "(locked: ...)". After a starter finishes, the next pick should be its upgrade (e.g. Upgrade to Refining) once researched. `starsector.log` should have no "can't read Building Menu Overhaul's build rules" line.
9. Diagnostics: after a load, the scan line in `starsector.log` reports how many buildings were measured. Measured entries in the empire panel list what was read (e.g. "+3 metals, needs 2 ore"). Check that no "registered scripts or intel on the probe colony" warning repeats for the same building.
10. With Random Assortment of Things: the empire panel's Settlement section lists the 3 best surveyed sites. In Advisor mode, "Found on ..." founds it; the settlement should appear in RAT's intel with a Landing Pad and the chosen resource. Next, Refining (on a refinable resource) is offered.
11. With Station Augments: open the panel of a colony with an orbital station. The Station augments section shows AP used and the recommended changes. Click Apply; the station's augment menu (dock, station, augments) should list them, and the log says "you set station augments: added ...". Remove one in the augment menu, advance a day: the log says Autopilot won't add it there again, and the panel offers "Allow again".
12. With Vaults of Knowledge: a colony panel's Industry synergies section shows the efficiency and the active synergies, matching VoK's own synergy screen. A recommended build that completes one says "Starts the ... synergy".
13. With Theory of Toolbox, after a month has passed: the empire panel's Trade contracts section lists the month's offers. Accept one in Advisor; it should appear in ToT's contract screen as active. `starsector.log` should have no "can't read Theory of Toolbox's trade contracts" or "can't read Vaults of Knowledge's industry synergies" line.
14. With a Coronal Hypershunt colony (Vaults of Knowledge): its panel shows the Coronal Hypershunt section (sections, range, receivers) instead of builds, and its Locks list leaves out the hidden port, patrol HQ and station. In Auto, nothing is built there, no AI core or item lands in it, and `starsector.log` has no "can't read Vaults of Knowledge's hypershunt" line.
15. With Vaults of Knowledge research: the empire panel's Research section shows the current tech and up to six ranked techs with what they unlock. With nothing researching, Start begins one; VoK's research screen should show it running and the databanks gone from the Research Facility tab.
16. Management styles: a colony panel names the colony's style. On a Centralized or Distributed colony, a recommended Mining or Refining shows a "from Centralized Urban Core" (or Distributed) line changing its output.
17. With a Pluto or Nidavelir colony: its panel has a Megastructure section with sections, current and best settings, and Apply. After Apply, VoK's megastructure screen shows the same laser, ore or manpower numbers. Change one there and advance a day: the log says Autopilot leaves it to you.
18. `starsector.log` has no "can't read Vaults of Knowledge's research", "colony management styles" or "Pluto and Nidavelir" line.
19. With Terraforming & Station Construction: a planet colony's panel has a Terraforming section with valued projects. In Auto, with nothing running, TASC's own terraforming screen shows a project started by Autopilot. A colony that only needs an Atmosphere Processor gets one recommended.
20. Without Grand.Colonies, a colony with 12 buildings shows "The colony screen shows at most 12 buildings" and offers upgrades only. With it, the 13th build shows the extra upkeep line.
21. Deposit a mod's colony item (for example a Substance.Abuse recipe) in Autopilot Storage. With a Brewery-less colony and a free slot, a Brewery is recommended; once built, the recipe is installed.
22. `starsector.log` has no "can't read Terraforming & Station Construction" line, and diagnostics list TASC's self-removing buildings as "left to you".
23. `starsector.log` has no "File access and reflection are not allowed to scripts" line. With Ashes of the Domain, the colony panel shows research locks and synergies instead of "info unavailable".
24. New game, found the first colony: once the founding dialog closes, the fast buildout dialog appears. Pick 250,000: the colony switches to Auto, the log says "fast buildout started", and builds start daily without taking credits below 250,000. **Stop buildout** in the colony panel ends it; the colony stays in Auto. In a save that already has colonies the dialog never appears.
25. With Nexerelin, set **Agents** to Defense with a Pather cell or rebellion at a colony: within a day an agent is hired (log "Hired agent", 12,000) and sent there. The empire panel's **Agents (Nexerelin)** section lists it and any enemy operations against your colonies. Agents you hired yourself get no orders.
26. Open one of your colonies from the Command tab: a **Colony Autopilot** button sits near the building grid without covering anything, and opens the colony panel. Docked at the colony, the same. Not on other factions' markets. With **Button on the colony screen** off, it's gone.
27. New save: found a colony, decline the fast buildout, switch the colony to Auto and just click **Save and switch to Auto**. The budget line shows money available even while net income is negative (Automatic budget), and builds start within a few days.
