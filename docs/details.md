# Jonah's Colony Manager

Colony management automation (Colony Autopilot) for Starsector 0.98a, plus sticky combat autopilot. Battle size is fixed at 2000 (`data/config/settings.json`); no other balance changes.

## Install

1. Put the `Jonah's Colony Manager` folder in `Starsector/mods/`.
2. Enable **Jonah's Colony Manager** in the launcher. Its mod id is still `jonah_tweaks`, so saves that used the old name keep working. No required dependencies. Optional integrations load only when their mod is enabled: Terraforming Made Easy, Industrial Evolution, Nexerelin, Emergent Threats, Ashes of the Domain (Building Menu Overhaul; Vaults of Knowledge research, synergies, management styles, megastructures and items; Theory of Toolbox trade contracts), Random Assortment of Things (Frontiers settlement), Station Augments. Any combination works; see "Building from source".
3. Defaults for new saves are in `data/config/colony_autopilot.json`. Every option can also be changed in-game.
4. Don't remove the mod from a save that has used it: Autopilot Storage is a market tab in your colonies.

## Using it

- **Colony panel:** colony screen, right-click **Population & Infrastructure**, then **Colony Autopilot...**
- **Empire panel:** Intel screen, **Colonies** tab, **Colony Autopilot**. Budget, approvals, every colony, storage, settings, log, diagnostics.
- **Modes per colony:** Off, Advisor (default: recommends, you click Build), Approve (one-click proposals), Auto (builds within your budget).
- **Goals:** Balanced, Money, Security, War production, Rapid growth, Self-sufficiency. Each colony gets a plan from its planet, hazards and resources. With Terraforming Made Easy, that includes a reachable special world (Forge, Fortress, Paradise, Hive, Death, Waste) resolved from TME's own requirements.
- **First switch to Auto** opens the setup popup: all settings, recommended values, and a station line item in the budget.
- **Autopilot Storage:** a tab at every colony market. Colony items, AI cores and Antimatter Stabilizers deposited there are the only ones the manager uses.

## Rules it follows

- **Budget:** spends within the monthly budget, per-project cap and credit reserve. Emergency defense fleets may pass the monthly budget, never the reserve.
- **Fees:** convenience fees are fixed vanilla-scale amounts. Off, Low, Standard or High.
- **Removals:** buildings are only removed during terraforming, or anywhere with **Allow removals** on. The Antimatter Stabilizer conversion counts as a removal.
- **Story points:** player only by default. Options: one per colony, or a monthly cap.
- **Terraforming:** hazards a project adds are scored, and side effects (TME removes a new condition's conflicts) are simulated in planning.
- **AI cores:** one switch, off by default. Cores stack on your best-defended world; other colonies stay at 7 or fewer Hegemony inspection points. Alpha cores only go where they can be removed cleanly (configurable) and are never moved without asking.
- **Administrators:** idle ones are assigned first; hiring is off by default. The 30-day arrival delay is optional.
- **Ambassadors (Industrial Evolution):** focus setting (worst relations first, strongest factions, nearest neighbors, or priority list only), per-faction priority (High, Normal, Ignore), and a "stop raising at" reputation level.
- **Threat response:** a colony is planned as Security once its threat estimate reaches 70%. It goes back to its own goal only after the estimate falls below 65% and at least 60 days have passed, so free port, hazard pay and edicts don't flip back and forth at the line. The estimate uses hostile factions nearby (at most 60% on their own), the Hostile Activity crisis, and raid, blockade, Pather cell and rebellion conditions. The state is kept in the save.
- **Log:** "waits for budget", "waits for next month", unavailable edicts and similar notes are logged once per save, not after every load. They can be logged again after the thing they reported happens.
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
- **Trade contracts (Ashes of the Domain, Theory of Toolbox):** monthly deliveries from your surplus, paid at 2-5x the export cut. Three missed months end a contract and cost merchant renown. Mode Advisor (default), Auto or Off.
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
- **Research (Vaults of Knowledge):** mode Advisor (default), Auto or Off.
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
- **Hidden buildings:** a building the game hides (megastructure defenses, the hypershunt's port) is never upgraded, improved or given an item or AI core, and isn't listed under Locks. A hidden station's augments are left to you.
- **Mod buildings:** a building with no formula or estimate is measured: it's built on a throwaway probe colony (your colony's size and vanilla deposits, no planet or location) and the difference is read: commodities supplied and demanded, stability, accessibility, income and upkeep multipliers, ground defense, fleet size and patrols, ship quality. It's then scored like any other building. Upgrades measure both buildings on the same probe and score the difference.
  - Effects that only happen over time (monthly items, spawned fleets) aren't seen, so measured buildings score low rather than high.
  - A building whose code fails on the probe, or that registers scripts or intel when built, is "can't be measured" and never acted on; anything it added is removed.
  - Setting **Mod buildings with measured effects**: Build when best (default), Recommend only (Auto proposes them for approval), Ignore.
- **Settlement (Random Assortment of Things, Frontiers on):** one per save. Mode Advisor (default), Approve, Auto or Off.
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

## Building from source

Source is in `src/`. It compiles against the game and optional mod jars with JDK 17:

```
javac --release 17 -cp "<core>/starfarer.api.jar;<core>/starfarer_obf.jar;<core>/fs.common_obf.jar;<core>/log4j-1.2.9.jar;<core>/json.jar;<core>/lwjgl.jar;<core>/lwjgl_util.jar;<TME>/TerraformingMadeEasy.jar;<IndEvo>/jars/IndEvo.jar;<Nexerelin>/jars/ExerelinCore.jar" -d build (all .java files under src)
jar cf jars/jonahtweaks.jar -C build data
```


`CATerraform`, `CAIndEvo`, `CACourier`, `CADefense`, `CASettlement` and `CAAugments` reference optional mods. They're only called when that mod is enabled, so the mod runs without them. `CASettlement` and `CAAugments` also need the Kotlin runtime (LazyLib) and LunaLib on the compile classpath.

The author's build checks prove any combination works: jdeps shows only those classes reference each optional mod, every caller checks `isModEnabled`, and the jar links under `-Xverify:all` against all 32 combinations of the compiled-in optional mods. `CASynergies`, `CAContracts`, `CAHypershunt`, `CAMegastructures`, `CAResearch` and `CADevelopment` reach Vaults of Knowledge and Theory of Toolbox through reflection only, like `CAAotD`; if a name can't be found they're left out and the log says why. `JTAotDEconLinkCheck.java` checks those names; run it with `-XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote` like the game's own launch flags, since some VoK classes refer to Sins of the Past classes that aren't installed. An offline check covers the augment choice (knapsack against brute force, swap margin, shrunk budget, incompatible pairs). `CAAotD` reaches Building Menu Overhaul through reflection only, so it needs no jar at compile time.

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
