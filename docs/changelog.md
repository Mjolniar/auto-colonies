# Patch notes

## 0.3.16.9 - 2026-11-24

- Settings page: With LunaLib, the Autopilot panel's Settings page shows the main settings on one screen, three buttons to a row: empire mode, Balance, Spending, AI cores, Profile, New colonies, War, credit reserve, Stations, Take down buildings, Colony policies, Administrators, Capital, Fleet limit per system, Gather everything at the capital, Tech-Mining finds, Refit hub, Sell hub, On-screen log and Faction stances. A click changes the value and LunaLib's menu (F3) shows the change. Hover text on each button.

## 0.3.16.8 - 2026-11-24

- Storage logistics: Added Gather everything at the capital, off by default. Once a week every Storage you can reach sends everything to the capital's Storage: ships, weapons, wings, commodities, crew, marines, cores, colony items and blueprints. Each load costs 2,000 plus 300 a light-year and 1,000 a ship, times the fee level. Story mode: part of Supply and items.

## 0.3.16.7 - 2026-11-24

- Tech-Mining: Added Tech-Mining finds to Autopilot Storage, on by default. Each month the colony items in Tech-Mining's finds move from the gathering point's Storage to its Autopilot Storage, where they get installed. Off keeps them in Storage. Blueprints, weapons and cores stay in Storage.

## 0.3.16.6 - 2026-11-24

- Slightly Better Tech-Mining: Tech-Mining is a plan step at the Auto colony with the best ruins (ruins size times what is left of them). The endgame, pruning and the over-limit cleanup leave it in place, and an earlier takedown there does not block the rebuild.

## 0.3.16.5 - 2026-11-23

- Fleet limit: Added Fleet limit per system, on by default. Systems with your colonies hold 30 fleets. Past that, Roider nomads and miners, traders, scavengers, pilgrims, mercenaries and other factions' patrols are sent home daily, farthest from you first. Your fleets, hostiles, war and mission fleets stay.
- Defense fleets: A system at the fleet limit gets no standing guard or threat-high defense fleet. Emergency defense still goes.

## 0.3.16.4 - 2026-11-23

- Tier V: Iron stability makes colony crises build at half speed while it is online. The crisis intel shows it as Iron stability x0.5.

## 0.3.16.3 - 2026-11-23

- Story mode: Each fed core's bandwidth is rounded on its own. Feeding one kind of core does not change what the others give.
- Story mode: After the falloff, a fed Gamma gives at least 1 bandwidth, a Beta at least 2 and an Alpha at least 6.

## 0.3.16.2 - 2026-11-23

- Instant construction: Fixed Population & Infrastructure finishing at once for 0 credits every day with a message each time. The population building is skipped.

## 0.3.16.1 - 2026-11-23

- Tier V: Added Autarch ascendant. The Autarch governs every managed colony, and each gets 2 more industry slots and 40% less upkeep. After it is dropped, the extra slots go and managed colonies get -3 stability for 60 days.
- Tier V: Added Planet forging. A year after it comes online, each managed colony in turn loses one bad condition or gets a richer resource, for 1,500,000 credits each. Rounds repeat yearly. Dropping it resets the timer.
- Tier V: A pick can be cancelled before it comes online. There is no lock-in or penalty, and picking it again starts the 30 days over.
- Rapid construction: Fixed the Reverse Engineering Hub reading finished while its tier was still under construction.

## 0.3.16.0 - 2026-11-23

- Tier V: Added Domain forge. Managed colonies with Heavy Industry or Orbital Works get a Pristine Nanoforge's output and ship quality, less what an installed nanoforge gives. 25,000 credits a colony each month. It fades over 30 days after it is dropped.
- Tier V: Added Autarch armada. A 600-point fleet is raised at your largest managed colony with a spaceport. It answers sieges and raids at managed colonies and patrols each of their systems in turn. 200,000 credits a month. A lost armada is raised again after 30 days. It leaves when dropped.

## 0.3.15.9 - 2026-11-23

- Capital: The Reverse Engineering Hub is part of the capital build-up, I to III. It is built at any money tier and stability, and its upkeep does not hold back other trade buildings.

## 0.3.15.8 - 2026-11-23

- Story mode: Removed the Historian hire package. A save that bought it gets its 10 bandwidth back. The Historian core stays.
- Ruin restoration: Restoration starts at an Auto colony with ruins where Sophia, hired through UAF, is administrator.

## 0.3.15.7 - 2026-11-23

- Panel: The main page has the credit reserve with buttons for 50,000 and 500,000 up or down.

## 0.3.15.6 - 2026-11-23

- Budget: Saved up is capped at your credits above the credit reserve. With credits under the reserve, nothing is saved up.

## 0.3.15.5 - 2026-11-23

- Station outfitting: Fixed station modules losing their weapons in battle. Each station's modules are fitted as their own copies. The game's shared module designs stay unchanged. Other stations with the same modules were affected too until a restart.
- Station outfitting: A fit waiting for budget is planned once. It is planned again when the station or your known weapons, wings or hullmods change.

## 0.3.15.4 - 2026-11-23

- Credit reserve: Capped at 3,000,000. A save or LunaLib value above it reads as 3,000,000.

## 0.3.15.3 - 2026-11-23

- Colony screen: The building grid's first empty slot shows the Autopilot's next build. Advisor and Approve read Suggested with a Build button, and Auto reads Next to build.
- Panel: A red line shows when your credits are under the credit reserve, since nothing is built then.
- Story mode: The Autarchy skill and the Autarch administrator package say what Autarchy gives: one more industry slot, 20% less upkeep, +1 stability, and the Hegemony and the Pathers don't count the core.

## 0.3.15.2 - 2026-11-23

- Panel: Settings, Colonies, Log, War and Factions are at the top. The page keeps its scroll when you come back from one of them.
- Panel: Advisor, Approve and Auto are smaller buttons, each with its icon beside it.
- Story page: The core and its bandwidth sit in the left column, and managed colonies and the Autarch administrator in the right.
- Panel: Fixed the scroll area running far past the last row.

## 0.3.15.1 - 2026-11-23

- Panel: One page with no list on the left. It opens on "How do you want your empire run?" with Advisor, Approve and Auto as picture tiles, then the Story page in story mode, then buttons for Settings, Colonies, Log, and War and Factions with Nexerelin.
- Panel: Picking Advisor, Approve or Auto sets the empire mode and every colony's mode at once, and new colonies follow it.
- Panel: Sized to the screen, up to 1380 by 900.
- New game: Starts on Auto with the recommended settings and the picked Profile.
- Panel: Removed the Colony and More pages. Their screens open from the buttons with Back at the top.

## 0.3.15.0 - 2026-11-23

- Story mode: Added Tier V. It shows in red once every Tier I to IV package is bought and running.
- Tier V: Pick any three of seven, for free. A pick stays 90 days before it can be dropped, and a new pick comes online after 30 days. Picks pause while a lower tier falls short.
- Tier V: Instant construction finishes every build and upgrade at a managed colony the next day, for its price a second time.
- Tier V: Iron stability keeps managed colonies at stability 8 or more, for 10,000 credits a colony each month. Dropping it gives -2 stability for 30 days.
- Tier V: Core printer makes one Alpha Core every 60 days for 250,000 credits.
- Tier V: Domain forge, Autarch ascendant, Planet forging and Autarch armada are listed and come in a later version.

## 0.3.14.1 - 2026-11-23

- Colony tab: Redesigned. The top shows the planet's picture with its name, size, mode and goal, the mode and goal as picture tiles, four numbers (Stability, Net income, Threat, Budget left) and the next build as one card with its button.
- Colony tab: Details, Money and capital, Plan and builds, Locks and Mod features open on a click, in both modes.
- Colony tab: Removed the Low profile button. Profile in Settings sets it.

## 0.3.14.0 - 2026-11-23

- New game: The pop-up asks Story or Normal mode, then Balance, then Profile, with one sentence for each option.
- Balance: Added Easy, Normal and Hard. Hard is the current prices. Normal and Easy lower story bandwidth prices to 75% and 50%, cut the core-heavy mod surcharge by half and all, raise fed core bandwidth by 25% and 50%, and set convenience fees to Low.
- Profile: Added Low profile, Normal and High profile. Each sets Low profile, New colonies, AI cores, War and where new colonies go at once.
- Settings: The LunaLib General tab holds Story mode, Balance, Profile, Empire mode, Spending, War, Credit reserve, On-screen log, Reset and Removal. The other main settings moved to the Advanced tab under Colony behavior.
- Story mode: Added Give back fed cores. It returns every fed AI core, the same kinds and counts, and the bandwidth they gave goes with them.
- Fast buildout: The first-colony offer asks for a Profile, and skips the question when the new-game pop-up already set one.

## 0.3.13.2 - 2026-11-19

- Story mode: The TriPad and crew scenes are rewritten.
- Settings: Every setting description in LunaLib and on the Settings page is rewritten in the Autopilot's own voice.

## 0.3.13.1 - 2026-11-19

- Story mode: A tier runs only while every tier below it has 2/3 of its packages on. Switching lower packages off pauses the packages above, and they keep their bandwidth.
- Story mode: Packages in a paused tier can't be bought until the tiers below have enough packages on again.
- Story page: Packages show as picture tiles, two or three columns by tier, lit when on.

## 0.3.13.0 - 2026-11-19

- Defense fleets: Without Nexerelin, Auto Colonies raises its own defense fleets for your faction, at 400 credits a fleet point.
- Defense fleets: Emergency defense works without Nexerelin. War level Defend adds relief for colonies under siege, defense before a crisis or at high threat, and standing guards while spending freely.
- Defense fleets: Your colonies can field 100 fleet points at once, plus 50 per Patrol HQ, 150 per Military Base and 250 per High Command. A fleet defends for 60 days.
- Settings: Emergency defense fleets and the War row show without Nexerelin. War levels past Defend still need Nexerelin.
- Story mode: Emergency defense no longer needs Nexerelin.
- Story mode: Station outfitting no longer needs Auto Outfitting. Without it, the game's own autofit fits the stations with the best weapons you know.

## 0.3.12.2 - 2026-11-19

- Compatibility: Updated for Starsector 0.98.5a-RC8.
- Colony screen button: Fixed on 0.98.5a-RC8. It opens the colony panel again.

## 0.3.12.1 - 2026-11-19

- Colony panel: Build buttons stay on when the score is out of date. The click checks slots, stability and the building again.
- Colony panel: With a building already under construction, a new building shows Queue and goes into the game's construction queue, paid when queued. Slots count the queue.
- Colony panel: Every option under More has a button when you can pay for it, worth it or not.
- Colony panel: Your own Build click spends the credits you have. The credit reserve only holds back Autopilot's own builds.
- Story mode: Advisor, Approve and Auto mode are one Tier I package, Modes (12 bandwidth). Colony upkeep and Supply and items need it. Saves that bought any of the three have Modes, paid what they cost.
- Planner: Never starts a building that's in the game's construction queue.

## 0.3.12.0 - 2026-11-18

- Stable locations: With Vaults of Knowledge's Janus Device analysis researched, a system with your colonies and no free stable location gets a new one when the comm relay, nav buoy or sensor array it makes room for would pay for both.
- Stable locations: Same rules as VoK's "Create a stable location": once per system, only while it has 3 or fewer. 500 fuel, 100 Electronic Components and 150 Domain-grade Machinery at base price, plus the fee and a 10-day crew from the system's biggest colony. One crew a day.
- Stable locations: Follows the Stable locations setting (Auto, Recommend only, Off). Each one is logged, and the objective is built on it the usual way.

## 0.3.11.9 - 2026-11-18

- Special project supply: A good the running black site project takes, which your colonies make but use up themselves, counts as a shortage of the project's monthly need (it counted as 1). Producers, AI cores and improvements that add it rank higher.
- Special project supply: Such a good gets one more producer, planned at a colony that doesn't have one (another Enrichment Facility for Purified Rare Metal). It waits while one is being built.
- Special project supply: The shortfall is logged once and the good shows red on the special projects page. Your own buildings are never taken down to free supply.

## 0.3.11.8 - 2026-11-18

- Performance: Each building's supply is read once a frame while a colony is scored. It was read again for every good and every pick.
- Performance: Research building values (every colony scored under a what-if) are kept 7 days while the same techs are open. Refresh on the Research page redoes them.
- Performance: Surplus terraforming plans are kept 7 days while the colony's conditions, busy structures, money state and setting stay the same.
- Log: Numbered copies of one mod building or condition (like `_1` to `_12`) get one line per scan. Diagnostics still lists each.

## 0.3.11.7 - 2026-11-18

- Story mode: Added Station outfitting (Tier IV, 25 bandwidth, needs Auto Outfitting 0.5.71 or later). Auto Outfitting's optimizer fits your star fortresses and tier 4 stations, core and every module, with the weapons, fighter wings and hullmods you know. No built-in hullmods, no story points.
- Station outfitting: The fit is put back each time the game fits the station again, after loads, rebuilds and upgrades. It's planned again when the station changes or you learn new weapons, wings or hullmods, one station a day at most.
- Station outfitting: Each refit pays the base price of the weapons and wings it adds (the first one over the station's own fit, later ones over the last fit), plus the usual fee, never below the credit reserve.

## 0.3.11.6 - 2026-11-18

- Station Augments: Axial Overclocking is picked only for the stations whose guns it turns faster (midline stations, read from Station Augments). It is never picked for high-tech, low-tech or tier 4 stations.
- Station Augments: An Axial Overclocking that Autopilot added to one of those stations is swapped for the next best augment. One you added yourself stays.

## 0.3.11.5 - 2026-11-18

- Reverse Engineered Private Arsenal: Once a week, one copy of each weapon in your colonies' Storage that the hub hasn't reverse-engineered goes into its Reverse Engineering Storage for research. Fighter wings go too at a Tier 2 or 3 hub.
- Reverse Engineered Private Arsenal: Items at the hub's own colony move at once. Items at other colonies travel as a Storage logistics load with its fee and travel time. Your fleet's cargo is never touched.
- Settings: Added Reverse engineering (Advanced, Extras), on by default.

## 0.3.11.4 - 2026-11-18

- Nexerelin police raid: When the raid is the coming crisis and your best defended Auto colony can beat it, that colony keeps its free port and the other free ports close, so the raid goes there. A beaten raid ends the police crisis for good.
- Station Augments: Combat counts x4 under threat at the capital and x2 at other colonies. Augments only change fights you are in.
- Station Augments: Station Repair Unit and Axial Overclocking on midline stations count for more.
- Upkeep: The Patrol HQ is charged at size 3, as the game does. Its stored upkeep correction is cleared once.
- Military Base: Counts its +1 stability, crew and marines. High Command counts its crew and marines, and the Patrol HQ its crew.
- Orbital Works: Counts +20% ship quality only while no other colony of yours has one, and no extra ship output.
- Spaceport: Counts its crew, and the Megaport's.
- Cryorevival: Its growth bonus falls with the distance to the nearest cryosleeper and halves while organics are short. It needs 10 organics.
- Heavy machinery: Aquaculture needs the colony's size in heavy machinery, and Refining and Fuel Production need size - 2.

## 0.3.11.3 - 2026-11-18

- Endgame economy: Builds a tier 4 station at one Auto colony at a time once its tier 3 station works, only while its system is calm: no siege, no raid on its way, not the coming crisis's target, threat not high, no hostile fleets there. Paid from surplus credits with the fee, never below the credit reserve.
- Tier 4 stations: An AotD Star Citadel goes up beside the old station and takes its place when done. Interstellar Imperium's Stella Castellum is an upgrade of the tier 3 station from any line. The old station keeps working until the new one is done.

## 0.3.11.2 - 2026-11-18

- Station Augments: Sanctified Station's Sacred Protector fleets count like a Patrol HQ's patrols and as combat under threat, so the station keeps it then. It counted for little and was swapped out.
- Station Augments: Combat augments the model doesn't know count x4 under threat, the same as the rest (was x10).
- Station Augments: Efficiency Overhaul counts its faster station repairs between fights.
- Upkeep: Orbital stations, battlestations and star fortresses (every line), the Planetary Shield, the Cryosanctum and Tech-Mining are charged at the game's fixed size, so their upkeep estimates match the game. Stored upkeep corrections are cleared once.
- Stability: Ground Defenses, the Patrol HQ and each station tier count their +1 stability.
- Commerce: Counts -3 stability, as the game does (was -2).
- Cryosanctum: Makes 6 organs at any size and needs 3 supplies and 3 organics, as the game does.

## 0.3.11.1 - 2026-11-18

- Story mode: Added Rapid repairs (Tier IV, 30 bandwidth). Disrupted buildings at your colonies recover in half the time, from raids, bombardment or anything else. Other factions' colonies are not affected.

## 0.3.11.0 - 2026-11-18

- What We Left Behind: The Aegis Missile System is a defense structure in the build ranking once you know its blueprint, at planet and station colonies alike. It counts ground defense x2, missile strikes on hostile fleets in the system valued like a Patrol HQ, and its supplies, hand weapons and marines.

## 0.3.10.9 - 2026-11-18

- Autopilot Storage: Fixed colony items that Autopilot took out of buildings being moved to normal Storage the next day, out of rotation. Any item that lists a building it installs in now stays in Autopilot Storage. This covers Industrial Evolution's, UAF's and AotD's colony items that aren't in the game's item effects table.
- Research Facility: When Autopilot takes one down, the AI cores, databanks and colony items in its research tab go to Autopilot Storage, no longer to normal Storage.
- Autopilot Storage: Blueprints, key cards and other items no building installs still go to normal Storage.

## 0.3.10.8 - 2026-11-18

- Reverse Engineered Private Arsenal: The Reverse Engineering Hub is built at the capital as a trade building, then upgraded to II and III. It waits for the money rules of trade buildings that don't earn: the Surplus tier, the Economy rung On and upkeep within 5% of colony net. Capital does set to Deliveries and commerce (the default).
- Reverse Engineered Private Arsenal: Still never built anywhere else and never taken down. Its AI core and item stay as you set them.
- AI cores: No core is placed in a building left to you, which covers the hub and Yunru's Bazaar.
- Story page: The Historian hire and Historian core descriptions are shorter.

## 0.3.10.7 - 2026-11-18

- United Aurora Federation: Auto colonies restore their ruins (Restore ruins) where an administrator with Galactic Historian governs. It has the same price (200,000, 500,000 or 1,000,000), the same 150 to 180 days and the same rules as the option. Part of Mod projects.
- Ruin restoration: Valued as the average of the results for the ruins size, as they are with Fixes, less the Tech-Mining it removes. Ranked with every other build.
- Administrators: An administrator with Galactic Historian adds the restoration's worth where they could start one, and isn't moved while one runs.
- Story mode: Added Historian hire (Tier III, 10 bandwidth, United Aurora Federation). It hires Sophia once you're Friendly with the UAF and an Auto colony has ruins, for an administrator's hiring cost plus the fee. She arrives in 30 days.
- Story mode: Added Historian core (Tier IV, 20 bandwidth, needs Autarch administrator and United Aurora Federation). One Autarch AI Core variant with Autarchy and Galactic Historian governs the Auto colony with ruins where a restoration is worth the most, and stays until it finishes.
- Prepare for removal: The Historian core is swapped for a plain administrator like the Autarch AI Core.

## 0.3.10.6 - 2026-11-18

- Coronal Hypershunt: Goods it is short of count as shortages, so buildings that make them score higher until it is supplied. This covers the section being restored, the section restored next and the wormhole's Enriched Transplutonics.
- Coronal Hypershunt: Special project supply plans a producer for any of those goods your colonies make none of. Special project supply set to Off turns both off.

## 0.3.10.5 - 2026-11-17

- Defense fleets: Raid groups aimed at your colonies get a Nexerelin defense fleet once their fleets are out, sized a quarter over the group's fleet points less your defenses there. This covers Remnant Retribution's strike, Adversary's bombardment campaign, vanilla crisis raids and Nexerelin blockades.
- Remnant Retribution: Added its crisis name and advice on the Colony crises page.
- Adversary: Added its crisis name and advice on the Colony crises page. Mutual Tenacity is no longer listed as an unmapped condition.
- Toolbox Economy Curves: New farms, Light Industries and (with that setting) Heavy Industries count the curve's extra upkeep. Income Guard is no longer listed as an unmapped condition. Supported version 1.1.1.
- Reverse Engineered Private Arsenal: Supported version 1.5.0.

## 0.3.10.4 - 2026-11-17

- Story mode: Each core-heavy mod that is on makes packages, managed colonies and Autarch seats cost 10% more, up to 50% more. The mods are Treasure Hunt, Seeker, Random Assortment of Things, Secrets of the Frontier, Knights of Ludd, Slightly Better Tech-Mining and Omega Core Everywhere ("coreMods" in the config file).
- Story mode: Packages already bought keep the price paid. A package turned off and on again costs today's price.
- Story mode: Fed cores give 75% of their bandwidth once 100 bandwidth is fed, and 50% from 250. Bandwidth already fed stays.
- Story mode: Rapid construction costs 110,000 more per build (was 100,000). The convenience fee is 10% higher in story mode.
- Story page: Shows how much more new purchases cost and the share fed cores give now. The feed buttons show the bandwidth each core gives now.

## 0.3.10.3 - 2026-11-17

- Surplus research: Upgrades one Research Facility to a Black Site once Vaults of Knowledge opens it and none is built. It needs an Auto colony at stability 10 or more and pays from surplus credits. One only.
- Story mode: Managed colonies past your colony limit plus your administrators raise your colony limit by one each.
- Story mode: Each managed colony bought costs 3 bandwidth more than the last (2, 5, 8 and so on). Each Autarch seat costs 5 more than the last (10, 15, 20 and so on). Colonies and seats already bought keep the price paid.
- Story mode: At most 30 managed colonies and 30 Autarch seats.
- Auto-expansion: In story mode with managed colonies free, the wait between expeditions is halved and one more expedition can be under way for every two free (two more at most). With two or more free, the last colony's fast buildout no longer holds the next one.
- Auto-expansion: Free Autarch seats count as administrators for a new colony.

## 0.3.10.2 - 2026-11-17

- Rapid research: Vaults of Knowledge research takes half its usual time (was 2/3).
- Rapid research: Each project needs 2/3 of its databanks, rounded, at least 1. AI cores and colony items cost the same.
- Autarch administrator: Added Seat the Autarch AI Core on the colony panel. It puts the Autarch AI Core back as administrator where you took it off, with Autarchy, while it has a free seat and the colony stays at or above the stability floor.
- Story page: Fixed package descriptions wrapping in a narrow column. They use the page's full width again.
- Settings page: Same fix.
- Colony panel: Two notes are shorter.

## 0.3.10.1 - 2026-11-17

- Story page: Fixed long package descriptions running past the panel's right edge and over the next row. Each row is now as tall as its text.
- Settings page: Rows wrap inside the panel the same way.

## 0.3.10.0 - 2026-11-17

- Random Assortment of Things: Added support for the Expedition Hub. It is valued by the uninhabited planets its fleets reach within 15 light-years.
- Expedition Hub: Each return counts for 90 days, up to RAT's cap of size + 2 per good. Tech-mining finds from ruins count at half value.
- Expedition Hub: Auto builds it when it beats the other picks. With fewer BPs than colonies that could use one, the BP goes to the colony where the Hub brings back the most.
- Expedition Hub: Gets an AI core when 10 more light-years of reach (+2 burn with an Alpha or Beta) are worth more than the core's inspection risk.
- Expedition Hub: Counts as making nothing while its colony is short of ships or fuel.
- Diagnostics: The Expedition Hub has its own line with each colony's planets in reach. Forge-Assisted buildings buildable nowhere say they need a Forge-Assisted Industry BP and where it comes from.
- Probe colony: A copied condition that throws while a mod building is measured is left off for the session and the building is measured again.
- Docs: Removed a line on the Supported mods page that said parts of Nexerelin support were not tested in game.

## 0.3.9.9 - 2026-11-17

- Story mode: Added Rapid research, a Tier IV package for 20 bandwidth. Vaults of Knowledge research takes 2/3 of its usual time. Shown only with Vaults of Knowledge.
- Story mode: The items a research project needs are unchanged.
- Story mode: The change is never written into a save. It ends when the package is lost, on Prepare for removal and when the mod is removed.
- Mod list: Added an icon for TriOS's mod list.

## 0.3.9.8 - 2026-11-17

- Text: LunaLib setting descriptions, Settings page hover lines, panel and page text, decision explanations, story mode tooltips and item descriptions are reworded in the author's voice. Facts, numbers and setting names are unchanged.
- Docs: The README and every GitHub docs page except the changelog are reworded in the same voice.
- LunaLib: Number settings give their range as "It goes from X to Y, in steps of Z."
- TriPad: Fixed a double period after a colony mode note.
- Docs: Fixed a broken table row on the audit page (shipping between colonies) and a dead link in the README.
- Docs: The research page names the Off option of Research uses colony items.

## 0.3.9.7 - 2026-11-17

- Auto-expansion: When the colony limit needs an administrator who has not arrived, an Autopilot crew goes instead of a Nexerelin colony fleet. The crew waits for the administrator on arrival.
- Auto-expansion: Invasions under way count toward the colony limit. Expeditions under way count for conquest.
- Agents: Lower Relations is not used against your allies, your commissioner, or factions at Neutral stance or friendlier.
- Conquest: A failed invasion no longer makes a later capture of that market count as Auto Colonies' conquest.
- Conquest: Autonomy you grant a conquered colony stays.
- War: Attacks older than 120 days no longer count for the automatic rival or on the War page.
- War: Military rung upgrades count Rapid construction's fee.
- War: A market no raid or blockade can reach is passed over for 30 days, and the faction's next market is tried.
- Industrial Evolution: Fixed Senate edicts never being enacted.
- TASC: Crews on the way arrive with TASC stations Off. Without TASC they are refunded.
- TASC: A mining station is never both built and refunded. A siphon station is not founded where another faction colonized the gas giant.
- TASC: No warning in starsector.log in games without TASC.
- Wonders: The Daedalus Array goes only on a Grand Wonder site Autopilot built, never on a locked one.
- Wonders: Bifrost gates wait for room under the month's new-upkeep cap, and their upkeep is booked.
- Wonders: A one-day error no longer turns wonders off for the session.
- Settlements: A failed settlement expedition refunds its fee too.
- Approvals: A rejected restoration or TASC project is not recommended again during its hold.
- Supply contracts: A colony under siege keeps its contract.
- AI cores: A core comes out of a building only when the colony stays at or above the stability floor.
- Omega cores: Placed only in supported buildings, never over an Alpha core that stays.
- Items: A failed install or swap leaves each item in one place.

## 0.3.9.6 - 2026-11-16

- Prepare for removal: Ships in a storage logistics load on the way are delivered with the load. They were lost.
- Prepare for removal: A RAT settlement expedition on the way is refunded.
- Prepare for removal: Autarch AI Cores in an abandoned station's Storage, a RAT settlement's storage and cargo pods become Alpha Cores too.
- Prepare for removal: Fleets charged by enforcer patrols keep their one charge when Auto Colonies is turned back on in the same session.
- Prepare for removal: Fixed a starsector.log error without Nexerelin. The Mod features page with Removal now opens while story mode is dormant.
- War: A raid goes only when every market it would hit in that system is of a faction your War level and stances allow. Nexerelin raids hit every hostile market in the system.
- War: No raids at pirate faction markets while Nexerelin's pirate invasions are off. Those raids found no target and the price was lost.
- Planner: A colony over its industry limit no longer takes an industry down for a swap it can't build.
- Planner: Another start at a colony no longer clears its waiting proposal.
- Planner: The stability cost of buildings under construction counts in every stability check until they finish.
- Planner: A plan step you rejected no longer holds an industry slot for the 180-day hold.
- Planner: The engine's stability floor is at least 3, as the planner's is.
- Planner: Outposts and colonies that allow no buildings no longer count as idle for the money ladder.
- Planner: The endgame's one replacement a day holds across a load.
- Fast buildout: Starts only when the colony is in Auto.
- Measured buildings: An upgrade that drops a good the old building made is charged for it.
- Settings: Reset to recommended also resets the enforcer, crisis kill, war option, ambassador, settlement, research item and logistics rows. Auto-expansion settings stay.
- Settings: A recommendation no longer shows when the value already matches it.
- Story mode: A lost Autarch AI Core that turns up again ends the wait for a new one. The search covers abandoned station storage and cargo pods.
- Story mode: The Autarch AI Core stops governing a colony that leaves your control.
- Stable locations: Crews on the way arrive with the setting Off and while the Autopilot is dormant.
- Administrators: Over the colony limit, administrators come before item and core moves.
- Research: Items are shipped only to a Research Facility at a colony Autopilot can deliver to.
- Surplus research: A removed Research Facility's AI core goes to Autopilot Storage. Colonies under inspection are left alone.
- Shipyard: Storage logistics leaves the Shipyard's hulls at the gathering point.
- Stockpiles: An approved proposal is dropped when goods of that kind are already on the way.
- Capital: The capital lines show again after a second loss or a move back.
- Special projects: Supply planning starts again after a load when it failed once.
- Autopilot Storage: The tab appears only at colonies Autopilot reads.
- Patrol kills: Battles with no fleet of your faction are skipped sooner.
- Text: Fixed the automatic upkeep floor, the Rapid construction fee in the build texts, the hazard pay setting name, a synergy line and the settings file header.
- Save size: Old logistics, rejection and Theory of Toolbox marks are removed.

## 0.3.9.5 - 2026-11-16

- Colony crises: Ships destroyed by Auto colony patrols and stations count toward the colony crisis, as your own kills do. Battles without your fleet only. The game's amounts.
- Colony crises: Pirate and Luddic Path base stations destroyed by Auto colony patrols count as the game counts them for you.
- Commerce raiding: Tri-Tachyon commerce raiders and trade fleets destroyed by Auto colony patrols count toward Tri-Tachyon commerce raiding.
- Nexerelin: Its colony crisis rules are followed. Outposts do not count as colonies, and any base station counts.
- Settings: Added Patrol kills count for crises (Advanced, War, and LunaLib). On by default, in Story and Normal mode.
- Crisis kills: Each lost ship counts once, across autoresolve rounds and saves. Ships the game counted in a battle you fought are not counted again.
- Enforcer patrols: Fixed a fleet being charged again when it was marked again after its mark ended. A fleet is charged once for its life.

## 0.3.9.4 - 2026-11-16

- Removal: Added Prepare for removal, in LunaLib (General tab) and on the Mod features page. A campaign dialog asks first.
- Removal: Everything in Autopilot Storage goes to the same colony's Storage, or to your cargo where a colony has no Storage. Shipments on the way arrive at once.
- Removal: Construction crews and crew expeditions on the way are refunded. The expedition fee is kept. Nexerelin expeditions and ship orders continue.
- Removal: Each Autarch AI Core becomes an Alpha Core. The core in the TriPad becomes an Alpha Core in your cargo. An unfound core is taken out of its station.
- Removal: Autarch AI Core administrators are replaced by an administrator with an Alpha Core administrator's skills and no Autarchy. Story progress and bandwidth packages end.
- Removal: Stops before changing anything when a colony would fall to 0 stability from losing Autarchy, and names the colony.
- Removal: Afterwards Auto Colonies does nothing in that save. Save, quit and remove the mod. Turning the setting off in the same session brings back the save's Auto Colonies data.

## 0.3.9.3 - 2026-11-16

- Station Augments: Augments you removed are held 180 days, then Autopilot may suggest them again.
- Settings: Reset to recommended asks before it clears your set-by-you marks.
- War: Sindrian Diktat raiders count toward sieges.
- Enforcer patrols: Sindrian Diktat raiders are handled like pirate raids at every colony, in Story and Normal mode. No story package is needed.
- Planner: A waiting proposal is replaced only by a pick at least 10% better.
- War: Relief fleets wait for Nexerelin's stabilize cooldown.

## 0.3.9.2 - 2026-11-16

- Terraforming: Fixed plan projects starting on a condition that a running project changes first.
- Terraforming: Fixed plan projects spending while Pause on loss holds spending.
- Terraforming: Fixed surplus-paid plan projects counting against the month's budget and the terraforming share.
- Terraforming: Fixed condition removals on TME 2.10.0 waiting for the requirements of adding the condition.
- Terraforming: Fixed TASC decisions staying in the approval list after the project started or a better one came up.
- Terraforming: Fixed the TASC section saying a planet is a station when TASC could not be read.
- Story mode: Fixed Rapid terraforming cutting a TME project twice when its name changed.
- Story mode: Fixed Rapid terraforming logging a cut when nothing was cut.
- TASC stations: Fixed a failed station refunding the price without the fee.
- TASC stations: Fixed station entities staying in the system when the station was not built.
- TASC stations: Changed the site search to once a week.
- Approvals: Fixed rejected station augment asks coming back when the suggested loadout changed.
- Station Augments: Fixed Autopilot removing its own augment when the augment could not be scored.
- Station Augments: Fixed a battle on the refit crew's arrival delaying the refit by 7 days or dropping an approved refit.
- Station Augments: Fixed an old refit booking letting a later change skip the crew delay.
- Station Augments: Removed the pointer to a colony panel section from the suggestion line.
- Substance.Abuse: Fixed the Brewery's input shortage being charged twice.
- Substance.Abuse: Fixed a Recipe Book being valued with recipes it does not hold.
- Roider Union: Fixed Dives being valued for their whole area in a save loaded without Fixes.
- Compatibility: Fixed versions such as 2.1.7 or 11.7 counting as a tested 1.7.
- Mod buildings: Fixed the probe colony check missing listeners and transient scripts a building leaves behind.

## 0.3.9.1 - 2026-11-16

- Shipyard: Fixed free cash not counting a ship order placed the same moment.
- Shipyard: Fixed your own mothballed ships at the gathering point counting as unclaimed hulls.
- Shipyard: A hull taken off the production list refunds the fee that was paid.
- Shipyard: Fixed hulls taken off the production list still counting as the month's spending.
- Story mode: Stockpile deliveries and the shipyard free cash check run while the Autopilot is dormant.
- Autopilot Storage: Fixed chained shipments on one route paying a single fee.
- Autopilot Storage: A shipment that fails to arrive is skipped and the others arrive.
- Storage logistics: Fixed Domain-grade Parts being sent again while a load is on its way.
- Storage logistics: Parts are not taken from Off colonies.
- Storage logistics: Parts and loads wait while the planner sets credits aside.
- Stockpiles: Fixed the rung note naming the wrong reason when a purchase waits.
- Stockpiles: Removed the colony lost sentence from the arrival line.
- Surplus research: Fixed Research Facilities being removed while the research list is empty.
- Budget: The upkeep cap line on old saves states the current rule.
- Megastructures: Auto raises Pluto's laser within the new upkeep cap and above the credit reserve.
- Megastructures: Approve shows and books the laser's added upkeep.
- Megastructures: Nexus Core steps go to starsector.log, with one line when it stops rising.
- Megastructures: Pluto's forge split changes only for moves of 4 units or more.
- Courier Port: Fixed contracts cancelling after one delivery while stockpiles cover the shortage.
- Courier Port: No contracts at colonies that do not use stockpiles for shortages.
- Courier Port: No new contracts under the credit reserve. Running ones are cancelled while credits are under it.
- Research: Auto starts no tech while credits are under the reserve.
- Research: Sophia Anderson is made head of R&D only when no head is picked.
- Research: Plan steps are not held for research while research is Off.
- Floating City: The fuel guard works when the colony styles can't be read.
- Industrial Evolution: A switched mining or refining specialty is kept for 90 days.
- Ashes of the Domain: A failed hidden-building check builds nothing at that colony.
- Performance: Fewer repeated reads of last month's report, market sources, AotD orders and upgrade links.

## 0.3.9.0 - 2026-11-16

- Compatibility: Starsector 0.98.5a-RC7. The colony screen button finds RC7's dialog classes.
- Auto-expansion: Fixed a dropped proposal's planet being proposed again the same day.
- Auto-expansion: Fixed a blocked planet staying first in line for up to 30 days in Auto.
- Auto-expansion: Fixed a colony founded while another mod's listener failed being refunded and left without its mode and administrator.
- Auto-expansion: Fixed a Nexerelin colony fleet being started and cancelled at once for lack of credits.
- Auto-expansion: Approve shows the cost again before paying when it rose since the proposal.
- Auto-expansion: Hostile fleets passing through hold a colony crew. The crew turns back only if they stay.
- Settlement: Found and Build clicks keep the credit reserve.
- Settlement: Fixed a colony with no primary entity removing every site.
- Settlement: Fixed a Frontiers site being picked where your colony expedition was headed.
- Settlement: Off drops a waiting proposal.
- War: Fixed strike back raiding factions that attacked other factions' markets or sent colony expeditions.
- War: Fixed strike back targeting hidden, story-critical and cut-off markets.
- War: Fixed the rebellion and isolation operations never running with Nexerelin's agent types.
- War: Fixed wait lines repeating every day when only their numbers changed.
- War: Fixed the emergency defense fleet cooldown resetting on every load.
- Military: Fixed the military recommendation repeating after every load.
- Performance: Changed war planning and scorched earth planning to use less CPU.

## 0.3.8.9 - 2026-11-16

- Autarch administrator: Fixed the core taking new colonies while it still governed one it could not leave.
- Autarch administrator: Fixed the core staying over its seats when the floor held the colony it adds least to.
- Autarch administrator: Fixed leaving a colony over the industry limit counting the limit's penalty twice.
- Autarch administrator: Fixed the core never returning after the administrator who replaced it moved away.
- Omega cores: Fixed an Omega administrator replacing the Autarch AI Core.
- Omega cores: Fixed an Omega administrator taking a colony under the stability floor or replacing a hired administrator who adds more.
- Autonomy: Fixed autonomy being offered and granted again to colonies already autonomous.
- Autonomy: Fixed items, AI cores, Omega cores and production floors going to Nexerelin-autonomous colonies.
- Story mode: Fixed an Autarch AI Core in an Industrial Evolution warehouse counting as lost and a second core being placed.
- Story mode: Fixed a new game with LunaLib placing the core's station before the Story or Normal answer.
- Story mode: Fixed bought managed colonies keeping their bandwidth after Approve mode is switched off.
- Story mode: Fixed removing a managed colony leaving Autarch administrator seats that govern nothing.
- Story mode: Fixed a lower bandwidth total removing packages whose mods are off.
- Story mode: Fixed the founding scene starting a fast buildout at a colony held back by the managed colony limit.
- Story mode: Fixed the Story tab and Story page pointing to the old station while the core is in a storage tab.
- Story mode: Fixed the breakdown and new core scenes being lost to a save or a Story page click.
- Story mode: Fixed reading "Another signature" again adding a new objective.
- Story mode: Changed the Rapid construction and Autarch administrator texts to show the fee and seat cost from the config file.
- Endgame economy: Fixed the summary counting replaced buildings older than a year.
- Performance: Changed the threat estimate to read hostile neighbors once a day per colony.

## 0.3.8.8 - 2026-11-16

- Planner: Fixed endgame and capital replacements that need approval being proposed again at every look.
- Planner: Fixed a building taken down or rejected since its colony's last look starting the same day.
- Planner: Fixed builds starting at a colony lost, or set to Off, Advisor or Approve, during the daily pass.
- Planner: Fixed plan steps after an Ashes of the Domain starter building counting the starter's output again.
- Planner: Fixed Refresh and the daily panel scores changing which plan steps hold rank and slots at Approve and Auto colonies.
- Planner: Fixed the fallback build pass rebuilding buildings taken down within the year and proposing rejected ones again.
- Money ladder: Fixed idle months counting colonies in fast buildout and colonies that are lost or Off.
- Fast buildout: Fixed a measured mod building waiting for approval stopping the buildout's other new buildings.
- Daily pass: Fixed pruning, Low profile, colony policies and the over-limit fix acting on a colony lost or set off Auto during the pass.
- Approvals: Fixed Reject on a settlement or money ladder item rejecting a newer proposal.
- Construction: Fixed an upgrade that failed while starting being left running unpaid.
- Capital: Fixed the capital plan holding a slot for AotD's Underworld under Low profile.
- Industrial Evolution: Fixed no colony being offered a Courier Port when an outpost or a hypershunt had the best accessibility.
- Terraforming: Fixed a new world commitment being recorded a day late when research valued the colony first that day.
- Log: Your expansion style, Low profile and capital pick lines moved to the Thinking list.
- Performance: Changed the planner, research rankings, the Colonies page and special project supply to use less CPU.

## 0.3.8.7 - 2026-11-16

- First-time setup: Fixed Back and Close keeping the recommended settings and the empire mode's defaults.
- Colony panel: Fixed Faction stances OPEN opening the Colony page.
- Approvals: Added a confirmation to Approve all and Reject all on the Colonies page and in Choose each.
- Approvals: Fixed the summary naming a page that no longer exists.
- Approvals: Fixed Approve and Reject buttons adding empty colony entries to the save.
- LunaLib: Fixed War, New colonies, Story points, Stations, Production and the Spending upkeep share not loading from LunaLib.
- LunaLib: Fixed changes saved on the title screen losing presets, Reset to recommended and the set-by-you marks.
- LunaLib: Fixed loading a save clearing its capital pick while Capital does is Off.
- Autopilot Storage: Fixed the weekly move of a lost colony's items failing.
- Stations: Fixed station builds skipped for up to a day after loading another save without Nexerelin.
- Megastructures: Fixed a paused hypershunt range change still running every day.
- Daily pass: Fixed a failed reset on load stopping the daily pass for the session.
- Log: Fixed repeated failures and slow passes writing to starsector.log every second or every day.
- Performance: Changed the colony screen button, build menu hints and on-screen log lines to use less CPU.

## 0.3.8.6 - 2026-11-16

- Enforcer patrols: Colony patrols attack Sindrian Diktat raiders. No credit charge or reputation change. Own setting, on by default.
- Defense fleets: Emergency defense fleets count Sindrian Diktat raiders and fleets made hostile to your faction as hostiles.
- Interface: Fixed percent signs showing as "%%" on the panels, hover text and the Log page.
- Log: Advice lines name the step to take. Removed "up to you" and "your decision" endings.
- Log: Lines for your own clicks (rejects, stances, story mode, setup saved) moved to the Thinking list.
- Log: Fixed lines that named pages and settings that do not exist.
- Enforcer patrols: Fixed credit amounts that read "¢ credits".

## 0.3.8.5 - 2026-11-16

- Story mode: Fixed builds and upgrades under Rapid construction spending its fee from the credit reserve.
- Story mode: Fixed colonies held at Off staying Off after story mode is switched off.
- Fast buildout: Fixed buildings you rejected being proposed or started again within 180 days.
- War: Fixed relief fleets going to besieged colonies with no unrest.
- War: Fixed the relief fleet cooldown resetting on every load.
- Approvals: Fixed rejected megastructure and hypershunt range asks coming back when their target values changed.
- Terraforming: Fixed TME 2.10.0 projects with a blank unlocked cell never starting.
- Station Augments: Fixed approved augment changes never finishing after the refit crew was booked.

## 0.3.8.4 - 2026-11-15

- Story mode: Rewrote the TriPad scenes with your technology officer and chief engineer in the style of the game's own dialogs.
- Story mode: TriPad scenes end with Leave.

## 0.3.8.3 - 2026-11-15

- Story mode: Removed the TriPad summary that opened after the Story page closed.
- Story mode: Added four TriPad scenes about the Autarch AI Core's past, with your tech officer and chief engineer. One plays when each tier opens and one when every Tier IV package is bought. Saves already past a tier get its scene.

## 0.3.8.2 - 2026-11-15

- Colony screen: The Colony Autopilot button is larger and sits under "Add industry or structure", in the same place on every colony. It no longer moves when buildings are added.
- Colony screen: Fixed the button showing on a docked colony's storage and trade screens.
- Story mode: The Autarch administrator also takes colonies with a hired administrator, who goes unassigned. An AI core you placed as administrator stays.
- Story mode: An administrator you place where the Autarch AI Core governed keeps that colony.

## 0.3.8.1 - 2026-11-15

- Autarch administrator: Fixed the colony screen showing the faction's administrator title before its name ("Secretary Autarch AI Core" with Nexerelin). Cores already seated lose the title on the next day.

## 0.3.8.0 - 2026-11-15

- Story mode: Autarch administrator moved to Tier IV. It costs 10 bandwidth per colony it governs: the package covers one, and each more is bought on the Story page.
- Story mode: Added Autarchy, the Autarch administrator's own skill: +1 industry limit, -20% upkeep, +1 stability at the colony it governs. Cores seated by 0.3.7.9 learn it.
- Story mode: With more colonies than bought, the core leaves the one it adds least to, one a day, never under the stability floor.

## 0.3.7.9 - 2026-11-15

- Story mode: Added Autarch administrator (Tier III, 8 bandwidth, needs Cores and staff). The core governs each managed colony you govern yourself where that adds more than you governing it. It has an Alpha Core administrator's skills, no AI core to take back, and no inspection or Pather weight.
- Story mode: Added Rapid terraforming (Tier IV, 12 bandwidth). Terraforming projects at managed colonies take half the time, for Terraforming Made Easy and Terraforming and Station Construction.
- Administrators: Hypercognition counts in an administrator's worth (+1 stability, +10% accessibility).
- Autonomy: Never picks a colony the Autarch AI Core governs.

## 0.3.7.8 - 2026-11-15

- Story mode: Rapid construction replaces Instant and Free construction (Tier IV, 20 bandwidth): builds and upgrades take a third of the time and cost 100,000 more each. Saves that bought Instant construction have Rapid construction.
- Story mode: The new-game pop-up lists Story mode first, and Escape picks it.
- Colony page: A Low profile button. In story mode it shows once Colony upkeep is bought.

## 0.3.7.7 - 2026-11-15

- Random Assortment of Things: Forge-Assisted Industry BPs go in Autopilot Storage. Autopilot counts one held there as available for its industry and builds it like any other pick; the build moves the BP to the colony's Storage, where Random Assortment uses it up.

## 0.3.7.6 - 2026-11-15

- Story mode: The Colony page shows the core's status for the colony, the waiting approval, the next plan step, the top build and the Goal and Mode buttons. Colony numbers, Money and capital, Plan and builds, Locks and Mod features open on a click.
- Story mode: Settings is a page under More. The capital stays automatic unless you pick one, and the refit and sell hubs follow it.
- Station augments: No colony section. They run on their own (Mod projects in story mode); the empire Mod features page still shows them.

## 0.3.7.5 - 2026-11-15

- Story mode: The TriPad's summary lists each package switched on once, with one line on what it does. Packages on the Story page show the same line.
- Story mode: The Story page shows the Alpha, Beta and Gamma cores on hand under the feed buttons.
- Story mode: The core's name in dialogs is Autarch AI Core.
- Story mode: Removed Keep cores for feeding. Autopilot no longer holds AI cores back in storage; feeding stays on the Story page.

## 0.3.7.4 - 2026-11-15

- Story mode: The 52 abilities are bought in 18 packages, 4 or 5 per tier, each with a higher cost. Saves keep what they bought, as the packages that hold it.
- Story mode: The core gives 10 bandwidth, down from 20. Tier I costs 16 in all, and opening Tier II takes 3 of its 4 packages.
- Story mode: Closing the panel after changes brings up one TriPad summary: what runs now, what stopped, managed colonies and bandwidth. Feeding cores no longer brings up its own dialog.
- Autopilot panel: The Story button says Story. The bandwidth no longer runs past the button.

## 0.3.7.3 - 2026-11-14

- Story mode: Abilities are in 4 tiers. Tier I is open from the start; the next tier opens when you own 2/3 of the tier before it, and stays open. Tier IV holds the story perks, Conquer, Scorched, No fees, Endgame economy and Wonders. Saves from before open up to the highest tier they own an ability in.
- Story mode: The Story page lists open tiers and what opens the next one. Later tiers don't show. The line about held settings is removed.
- Autopilot panel: Four pages. Story (open first in story mode), Colony (Overview, Plan and builds, Locks and the colony's mod features on one page), Settings, and More, where Colonies, Autopilot Storage, Colony crises, Expansion, War, Mod features, Factions, Log and Diagnostics are picked from a row of buttons.

## 0.3.7.2 - 2026-11-14

- Story mode: Advisor mode (1 bandwidth) and Approve mode (2 bandwidth) are abilities. With nothing bought every colony is Off and nothing runs. Auto mode needs Approve mode. Saves that had bought abilities get both once, since they were free before.
- Story mode: The install dialog no longer explains bandwidth or lists core values. "What can you do?" points to the Autopilot panel. The list of services and costs is removed.
- Story mode: The story entry no longer lists cores on hand or tells you to feed cores. It has no objective line while the core is online.
- Story mode: The Story page's bandwidth section is the bandwidth line, the feed buttons and Keep cores for feeding. The core counts and the explanations are removed. Managed colonies shows once Approve mode is bought.
- Text: "The Story page of a colony's Autopilot panel" is now "the Autopilot panel".

## 0.3.7.1 - 2026-11-14

- Intel: Removed the Colony Autopilot intel tab. Its pages are under Empire in each colony's Autopilot panel: Colonies, Autopilot Storage, Colony crises, Expansion, War, Mod features, Settings, Factions, Log and Diagnostics.
- Intel: Buttons that asked for confirmation in the intel show Confirm and Cancel at the top of the page.
- Story mode: One intel entry, Colony Autopilot, in the Story tab. The search for the Autarch AI Core is part of it: map marker on the constellation's label, the clues, marked important while the search runs.
- Story mode: The Story page is in the Autopilot panel. While the Autopilot is dormant the panel shows the Story page and Diagnostics. The story entry has a Decide on the core button when the core is in your cargo.
- Colony panel: Wider (980 by 640) for the Empire pages.

## 0.3.7.0 - 2026-11-14

- Story mode: Removed text that repeats what you already know. This covers the core objective's sensors line and recovered line, Tech's line about going out to look, the faint signal line, and the note after pulling the core.

## 0.3.6.9 - 2026-11-14

- Performance: The daily pass runs over several frames, about 4 ms of each frame and at least one step a frame. The planner looks at one colony per step. Before, the whole pass ran in one frame and took 50 to 150 ms, up to about 600 ms.
- Approvals: The approval summary opens after the day's pass is done.
- Diagnostics: The slow-pass log line gives the frames the pass used and its longest frame.

## 0.3.6.8 - 2026-11-14

- Story mode: Added the Find the Autarch AI Core objective (Story, Exploration and Accepted intel tabs). Its map marker is on the constellation's label. Its text names the system's star and where the station orbits among the planets. It does not name the system.
- Story mode: A core returned after a sale gets a new objective. The objective ends when the core is in your cargo, a storage tab or the cradle.
- Story mode: While the Autopilot is dormant, the monthly ledger line goes to starsector.log only, the month does not count toward the money tiers, and the build menu shows no hints.

## 0.3.6.7 - 2026-11-14

- Upgrades: At the Surplus money tier an upgrade's price counts 25% in its score, and from Idle money up 10%. Industry and station upgrades with a positive value start while the colony's new building is under way.
- Story mode: Added two Story perks. Two builds at once (6 bandwidth) and Three builds at once (8 bandwidth, needs Two) let each colony build that many new buildings at the same time.
- Story mode: Story perks run only in story mode. Without them a colony builds one new building at a time, as in the game.

## 0.3.6.6 - 2026-11-14

- Story mode: The TriPad names the constellation the Autarch AI Core is in. The system is not named.
- Story mode: Before the core is installed, the intel, the Story page, the new-save pop-up and the settings text do not mention feeding cores or abilities.
- Story mode: Processing power (PP) is renamed bandwidth.
- Story mode: Rewrote the chief engineer and tech officer lines.
- Enforcer patrols: Patrols attack while your fleet is in the system. The row is now Attack in your fleet's system, On by default. Older saves get it On once.
- Enforcer patrols: A Persean League blockade's escort fleets count as League enforcers.
- Enforcer patrols: A fleet the game made hostile to your fleet only is attacked.
- Stations: Strength counts shields and fighter wing OP and weights firepower 2 to 1 over durability. Armor counts 5x, down from 10x.
- Projects: Lines about buildings Autopilot can't build or goods it can't find go to starsector.log only.

## 0.3.6.5 - 2026-11-14

- Story mode: Rewrote the TriPad dialogue. The chief engineer and tech officer talk you through the core, and the Autarch speaks like a Domain administration program. Player choices are lines you say.
- Story mode: Rewrote the Autarch AI Core item description and the first line of the new-save pop-up.

## 0.3.6.4 - 2026-11-14

- Approvals: A rejected proposal is held 180 days, up from 30. This covers builds, settlements, Shipyard, Stockpiles, Military goods, Military upgrades, hypershunt restores, megastructures, station augments and TASC projects. Autonomy is still held a year. Expedition targets are still skipped 180 days.

## 0.3.6.3 - 2026-11-14

- Settings: Picking Mode default in a preset row sets that group to the empire mode's values. They follow the mode again and stop counting as set by you.
- Stations: Diable Avionics and Roider Union are lines in the Stations row. A line whose first station a colony can't build counts as Pick by score there.
- Stations: Stations are scored by their fleet's strength: hull, armor, weapons and fighter bays of the station and its modules, against the low-tech station of the same tier.
- Stations: Before the first station is built, a pop-up lists each line a colony can build with its strength, cost and upkeep. Your pick sets the Stations row. It asks once per save.
- Text: Shorter lines on the War, Megastructures, Autonomy, Settlement, Crisis, Research, Projects, Fast buildout and Diagnostics pages.

## 0.3.6.2 - 2026-11-14

- Parts: The parts and fleet d-mods line goes to the Thinking list and starsector.log only. It no longer shows on screen.
- Antimatter Stabilizer: Fixed a colony item in Fuel Production being deleted when it doesn't fit Advanced Fuel Production. It goes to Autopilot Storage.
- Fast buildout: Fixed it spending the credit reserve. It stops at the reserve or its own floor, whichever is higher.
- Approvals: A build approval checks the credit reserve against the price on the day you approve it.
- Settlement: Fixed a second founding while an expedition is on its way charging again. A founding that fails is undone and refunded.
- Make room: Fixed an industry being taken down for a building that then could not start.
- Autonomy: Conquered colonies are not offered autonomy.
- Autopilot Storage: What a decivilized colony's Autopilot Storage held moves to your biggest colony's.
- Building: A building that started is kept when a later record step fails.
- Story mode: Fixed moving the Autarch AI Core into a Storage tab counting as a sale.
- Story mode: A core left in its station's salvage, or sold while story mode was off, counts as lost or sold. A new one turns up as after a sale.
- Story mode: On a new game the core is placed after the pop-up's answer. Choosing Normal leaves no station.
- Story mode: The Story page has the Story mode row when LunaLib is off.
- Story mode: The TriPad's colony hand-over keeps LunaLib's settings.
- Story mode: No decisions are offered or run while the Colony Autopilot is dormant.
- Story mode: The first colony setup sets the empire mode you chose.
- Story mode: The core can be placed in sectors with no system in the fringe band.
- Crash safety: The intel screen, the approval summary, the fast buildout offer and the Story tab catch their own errors. The approval summary always has a Close option.

## 0.3.6.1 - 2026-11-14

- Story mode: Fixed the 0.3.6.0 download missing the Autarch AI Core's icon and portrait.

## 0.3.6.0 - 2026-11-14

- Story mode: Added. Off by default. The Colony Autopilot stays dormant until you find the Autarch AI Core, then runs the abilities you buy with processing power from the AI cores you feed it. Story mode.
- Story mode: 48 abilities with PP costs, a managed-colony limit, the Autarch AI Core item, TriPad dialogs, a Story page and a Story tab entry.
- Story mode: Instant construction and Free construction exist only in story mode.
- Settings: Story mode is the first row in LunaLib's menu (F3) and on the intel Settings page. Each save follows the setting when loaded and when LunaLib's menu is saved.
- Settings: Switching story mode off returns every held setting and colony mode. Switching it on again keeps the quest, PP, abilities and fed cores. Spent PP is not refunded.
- Settings: A setting story mode holds at off keeps your value in LunaLib, so other saves are not changed.
- New games: A pop-up asks Normal or Story mode once. Older saves never see it.

## 0.3.5.3 - 2026-11-14

- Approval summary: Decision details are whole sentences in the game's dialog style. The "What:", "Why:" and "If rejected:" labels are removed.
- Autonomy: The decision reads "Grant autonomy. +X a month." Its details use Nexerelin's own wording for autonomy.

## 0.3.5.2 - 2026-11-13

- War fleets: Fixed a relief fleet message appearing every day for a colony under siege while the relief waited for fleet points or credits. The relief is posted only when it launches.

## 0.3.5.1 - 2026-11-13

- Autonomy: A rejected autonomy offer for a colony returns after a year. Other rejected offers still return after 30 days.
- Autonomy: While a colony's offer is held, its Recommend line stays quiet. The next colony that qualifies is offered.

## 0.3.5.0 - 2026-11-13

- Settings page: Rows read "Label: value." A row off its recommendation adds "Recommended: X." A row you set adds "Set by you."
- Factions and War pages: Faction rows read "Faction: stance." followed by the relation and markets as a sentence.
- Panels: The line under each panel title reads "Mode: Auto. Goal: Balanced." The empire panel reads "Online. 3 of 5 Auto."
- Intel pages and colony panel: Clauses joined by semicolons are separate sentences (budget, surplus, expansion, war, hypershunt, megastructures, projects, agents, Low profile).
- Reason lines: Station augments, AotD synergies, TASC, Roider Union, Planetary Shield and Secondary Drydocks reasons use short sentences.
- Plan steps: Mining and Fuel Production steps read "(vanilla Mining only)" and "(vanilla Fuel Production only)" next to IndEvo's and IX's versions. Mild climate reads "started by you".
- Diagnostics: Integration status lines and left-to-you reasons use short sentences.

## 0.3.4.0 - 2026-11-13

- Settings: With LunaLib enabled, every Settings page option is in LunaLib's settings menu (F3 by default), Auto Colonies. General tab: the main rows. Advanced tab: one header per Advanced group.
- Settings: LunaLib values are global. The first save loaded after the update copies its settings into LunaLib, so that game does not change. Later loads read LunaLib into the save.
- Settings: Presets are radio options and write the same fields as the intel rows. Numbers keep their range and step.
- Settings: A field changed in LunaLib counts as set by you and keeps its value through empire mode changes.
- Settings: Reset to recommended is a tick box in LunaLib. It unticks itself after the save.
- Intel Settings page: With LunaLib it shows where the settings are, this save's picks (Capital, Refit hub, Sell hub, Offense target) and Faction stances.
- First-time setup: With LunaLib holding settings from a set-up save, a new save's first Approve or Auto colony keeps them.
- Compatibility: LunaLib is optional. Without it the intel Settings page has every row.

## 0.3.3.5 - 2026-11-13

- Approval summary: Each decision lists what it does, what it costs, why, and what rejecting does.
- Approval summary: Autonomy lists the 25% income share, the colony limit, no building while autonomous, and the 2 unrest to take it back.
- Approval summary: Shows the colony's world when every decision is at one colony, otherwise a vanilla illustration.
- Approval summary: Later says when the summary comes back. The Approve all and Reject all confirmations say what each does.

## 0.3.3.4 - 2026-11-13

- Compatibility: The game version in mod_info is 0.98.5a-RC4. TriOS no longer marks the mod incompatible. 0.98a still loads it.

## 0.3.3.3 - 2026-11-13

- Expansion style: Changed "Low profile avoids colony crises" to "Low profile slows colony crises". Shown in the first colony's dialog and in Settings.
- Log: The Low profile line says Autopilot slows each faction's crisis.

## 0.3.3.2 - 2026-11-13

- JaydeePiracy: Added support for JaydeePiracy 6.0.3. Quarry Lakes: a new Refining needs no ore or transplutonic ore and counts heavy machinery demand +2.
- JaydeePiracy: Domain-era Meshugatron: a new Fuel Production counts fuel +3 and volatiles demand +3.
- JaydeePiracy: Eutrophicated Biosphere: a new Farming or Aquaculture counts organics at half its food and heavy machinery demand +3.
- JaydeePiracy: The Planetkiller Launch Site is left to you. The planetkiller item is not installed.
- Corrupt.Officials: Added support for Corrupt.Officials 1.0.b. At a colony with net income above the cutoff, extra income counts at the share the colony keeps.
- Corrupt.Officials: The cutoff, exponent and on/off setting are read from LunaLib. Without LunaLib the mod's defaults apply (40,000 and -0.6).
- New colonies: Planets with Tahlan Shipworks' Kassadari Claim (accessibility -100%, hazard +50%) and Gladiator Society's The Gulf are skipped.
- Left to you: Scy Nation's Intelligence Command Center, CJY's Toy Box's Private Production Facility and both greenhouses, and AI-Retrofits' Population Replacement Center. Autopilot does not build or test-build them.
- Production floors: Blue's Lanestate Battleyards and Oculian Armada's Orbital Matrix count as ship producers.
- Diagnostics: Added JaydeePiracy, Corrupt.Officials, Tahlan Shipworks, Gladiator Society, Scy Nation and AI-Retrofits to the integration check.

## 0.3.3.1 - 2026-11-13

- Kadur Remnant: Added support for Kadur Remnant 3.2.4support3. Kadur Majority Faith's +1 output on industrial buildings counts in build values from size 3.
- Kadur Remnant: Virus Bombed's -2 on every output line counts in build values.
- Diagnostics: Added Kadur Remnant to the integration check.

## 0.3.3.0 - 2026-11-13

- Approval summary: Added one dialog with every decision waiting, grouped by colony. Options: Approve all, Choose each, Reject all, Later.
- Approval summary: Opens every 60 days by default, only with no dialog, menu, screen or battle up. Settings, Advanced, Interface: Off or 15 to 180 days.
- Approval summary: Covers builds and make room, the RAT settlement, colony expeditions, the money ladder's proposals, hypershunt, megastructures, TASC projects, station augments and autonomy.
- Approvals: Every approval checks the credit reserve first and pays the convenience fee.
- Approvals: The Intel page lists every kind of decision with Approve and Reject, plus Approve all and Reject all.
- Approvals: Proposal lines go to the Thinking list while the summary is on.
- Rejections: A rejected build or settlement is held 30 days through save loads.
- Advisor: Added a ranking beside the game's build menu and Building Menu Overhaul's: Best, Good and Poor with a reason. No messages or pop-ups for Advisor colonies.
- Advisor: On 0.98.5a each build menu row's tooltip gets one Autopilot line.
- Colony panel: Recommended builds lists every option with its tag. The Build button pays the price plus the fee from credits above the reserve.
- Colony screen button: Shows the number of decisions waiting at the colony.
- Modes: Changing a colony's mode clears its waiting proposal.
- Empire mode: Added Off, Advisor, Approve and Auto defaults for empire-wide settings. New saves start on Advisor. The first-time setup sets it.
- Empire mode: Auto turns on Auto-expansion, every money ladder rung, make room, TASC stations and storage logistics. Approve asks first. Advisor gives advice only.
- Settings: A setting you change shows "set by you" and keeps its value when the empire mode changes. Reset to recommended clears it.
- Settings: Storage logistics moved to Advanced, Capital and storage. Empire mode is a main row.
- Saves: Older saves get an empire mode from their colonies. Settings that differ from 0.3.2's recommended values count as set by you; the rest follow the empire mode, with one log line.

## 0.3.2.1 - 2026-11-13

- Rogue Planets: Added support for Rogue Planets 1.0.0. Its 31 conditions are read by id. Without the mod nothing changes.
- Rogue Planets: Synergy pairs count in build values when a building starts, ends or changes them: Degenerate Volatiles, Diamond Core, Radiolytic Catalyst, Hollowed Caldera Garrisons, Vacation Paradise & Resort, Smugglers' Haven, Viable Slave Labor, Magmatic Antimatter Forge, Ground-to-Space Missile Defense.
- Rogue Planets: Hydrocarbon Fractionation, Megalithic Outcrops, Clandestine Harvesting Syndicate and Pilgrim World count in build values. Geothermal Leylines, Chrono-Spatial Dilation, Closed-loop Hydroponic Matrix, Megalithic Outcrops and Forgotten Vault-World count in upkeep.
- Rogue Planets: Geographically Efficient Zoning halves the build wait. Supercritical Crust counts a new building at its share of working time. Chrono-Spatial Dilation counts fleet value at half.
- Rogue Planets: Fragile Biosphere colonies get no industry but Farming. The second of a Volatile Additive Reactor's Refining and Fuel Production is left to you. A running pair is not taken down, replaced or upgraded.
- Stability: Viable Slave Labor and Supercooled Cryo-Sinks colonies are held at stability 6 or more. At the second month of slave labor unrest, an Auto colony's Commerce is taken down when that lifts stability above 5.
- AI cores and administrators: No AI cores or AI administrator at a Pilgrim World. No administrator change, autonomy or Omega administrator at a Trash King colony.
- Policies: Hazard pay stays off while Diamond Core's pair runs. Opening the free port costs no stability while Smugglers' Haven's pair runs.
- Production floors: Degenerate Volatiles, Ship Graveyard and Magmatic Antimatter Forge colonies rank first as floor hosts. Fragile Biosphere colonies host none.
- New colonies: Antimatter Anomaly fuel, crustquake goods and the stability cost of Automated Shipyards and the Harvesting Syndicate count. Fragile Biosphere counts farmland only.
- Left to you: Forgotten Vault-World's Tech-Mining, the Trash King, the Violet Dwarf system and the Afterlight. Storage logistics does not ship the Afterlight.
- Colony panel: Mod features lists each Rogue Planets condition at the colony and how Autopilot handles it.
- Diagnostics: Added Rogue Planets to the integration check and the mod watch.

## 0.3.2.0 - 2026-11-13

- Production floors: Added floors for hull source, hull capacity, AoTD order goods, production quality, weapons, Domain-grade Parts, supplies, fuel and producer inputs. Measured daily. Shown on the Budget panel.
- Production floors: An open floor adds value to a build for the part of the gap it closes. A met floor adds nothing.
- Production floors: Floors apply at every goal except Money. Fast buildout skips them.
- Production floors: An open floor gets its producer as a plan step at the best Approve or Auto colony. Research values the techs it needs.
- Planner: Floor builds rank first, head their colony's queue, keep their industry slot and take the earmark from other picks. Siege defenses stay first.
- Make room: With no free slot, a floor producer may replace the weakest income-only industry at the colony planned for it. Ask first (default) proposes it. Options: Off, Ask first, Auto.
- Make room: Kept: plan, project, military, locked and capital trade buildings, floor producers and industries under a year old. Every swap passes the stability floor.
- Money taper: A build's money counts in full up to 300,000 a month of colony net and contract income, 60% up to 1,000,000 and 35% above. Options: Off, Gentle (default), Strong. Money colonies use no taper.
- Diversity: A 3rd and later copy of a building family in the empire counts 75% of its money per extra copy. Families: Roider Dives and Union HQ, mining, JYD forges, commerce, farming, light industry.
- Goal weights: Balanced, Self-sufficiency and War production count fleets and ship quality more. The 0.3.0 table is a setting.
- Trade contracts: Ships, weapons, components, Domain-grade machinery, supplies and fuel stay home while their floor is open.
- Shipyard: The Shipyard rung opens at Surplus. With no production capacity its note names the floor producer planned.
- Parts: Added an advice line with the parts in Local Resources and the fleet's d-mod repair need. Taking parts costs the game's Local Resources price.
- Parts: Added an option to move the parts the fleet needs from Local Resources to the capital's Storage, at the Local Resources price plus the shipment fee. Off by default.
- Supported mods: Restoration Docks count toward the parts floor. Salvage Yards' quality loss counts against the quality floor. JYD, Yunru, RAT and Roider ship consumers count against AoTD order goods.
- Settings: Added the Production preset (Off, Light, Full) and every production setting under Advanced, Production. Off keeps the 0.3.1 picks.
- Settings: Added a Thinking list row (Show, Hide) under Advanced, Interface.
- Saves: Older saves load with Light and one log line naming the setting.

## 0.3.1.0 - 2026-11-12

- Enforcer patrols: Added an option for colony patrols to attack Hegemony investigators, Hegemony AI inspections, League enforcers, Luddic sacred protectors, Tri-Tachyon commerce raiders and Nexerelin police in systems with Auto colonies. Off by default. Each fleet type has its own switch. Settings: Advanced, War.
- Enforcer patrols: Cost mode None, Reputation (default), Credits or Both. Reputation matches the game's penalty: -3, none for raiders and police. Credits: the fleet's points x 400, Nexerelin's fleet hire price. An attack starts only when its price fits above the credit reserve.
- Enforcer patrols: Skips your commissioning faction, allies, League members and ceasefires, in Low profile and while your fleet is in the system. Each skip is a setting, on by default.
- Autopilot log: Each line is an action, advice or thinking. Actions and advice show on screen and in the panel log. Thinking goes to starsector.log and the Thinking list on the Log page (Show/Hide).
- Autopilot log: Planning, scans, money tier changes, siege and threat status, war fleets waiting and item shipping are thinking.
- Autopilot log: An item or core move is one line with its effect, where it came from and the shipping fee.
- Autopilot log: "Yours to build" advice shows once per good every 90 days.
- Autopilot log: The Month line shows once a month.
- Proposals: A proposal reads "Proposal: X for Y. Approval needed: reason."
- Text: Log lines and colony panel reasons rewritten as short facts.

## 0.3.0.9 - 2026-11-12

- Stability: Fixed the 0-stability timer resetting when a colony touches stability 1. Low days count with a slow decay.
- Stability: Stability counts up to 5x more in scoring below stability 3.
- Expansion: Fixed a prospect from another save being used after a load.
- Expansion: Founding goods include the survival mode tariff. New colony value counts corruption in survival mode.
- Administrators: The recruiter follows survival mode's rarity: about 285 days and 791,000.
- War: Fixed the standing guard waiting for a different amount than it spends.
- War: Relief fleets can use the fleet-point reserve.
- War: Fixed invasions taking points held for an expansion proposal.
- War: Fixed another faction's defense fleet blocking yours.
- Military: Approve checks siege, fast buildout and ownership.
- Planner: Fixed actions with no valid score sorting first.
- Terraforming: Project days follow TME's build time setting. Spending is booked to the colony.
- Crisis: Diktat crisis fuel is priced only during the Askonia standoff (0.98.5a).
- Support fleets: Player support fleets do not count as threats or siege (0.98.5a).
- Domain-grade Parts: Heavy Industry and Orbital Works get a small value for the parts they add (0.98.5a).
- Agents: The rival check runs once a day.
- Autopilot log: Lines about waiting, skipping, failed starts, caps and status go to starsector.log. Actions and advice stay on screen.
- Autopilot log: Fixed proposals that need your approval being hidden.
- Autopilot log: Removed duplicate lines and lines that returned every month.

## 0.3.0.8 - 2026-11-12

- Survival mode: Fixed corruption being taken from net income. It is taken from gross income, as in the game, and upkeep counts in full.
- War: Fixed the fee being charged for an operation that ends at once (raids, strikes, blockades, defense fleets).
- Military rung: Fixed standing guard and proactive defense spending from the Military rung on Recommend only, and from the surplus with the rung Off. They use the month's budget then.

## 0.3.0.7 - 2026-11-12

- Autopilot log: Lines about waiting or not acting go to starsector.log only. Actions, spending and advice stay in the log.
- Compatibility: Updated for Terraforming Made Easy 2.10.0, Random Assortment of Things 3.4.0, Emergent Threats: IX Revival 1.4.4, Omega Core Everywhere 1.2.5 and Astral Ascension Renewed 1.0.4.

## 0.3.0.6 - 2026-11-12

- Docs: Rewrote all docs at half length.
- Mod info: Shorter description.

## 0.3.0.5 - 2026-11-12

- Terraforming Made Easy: Added support for TME 2.10.0. TME 2.9.8 still works.
- Terraforming Made Easy: Fixed terraforming plans being off with TME 2.10.0 installed.

## 0.3.0.4 - 2026-11-12

- Survival mode: Build and keep values count extra colony income after corruption (over 200,000 a month). Losses count in full.
- Waystation: Fixed AI cores counting as cutting demand on Starsector 0.98.5a.
- Drone Replicator: Item placement counts the Decivilized Subpopulation stability it cancels on Starsector 0.98.5a.

## 0.3.0.3 - 2026-11-12

- Colony screen: Fixed the Colony Autopilot button on Starsector 0.98.5a. Works on 0.98a and 0.98.5a.

## 0.3.0.2 - 2026-11-12

- Roider Union: Dives and Union HQ are valued for the colony's own deposits, which is what Roider Union 2.3.1 mines after a save load.
- Roider Union: The Dives build note lists each deposit in reach and where it is.
- Mod watch: A Dives that mines only its own colony's deposits is reported as information.
- Administrators: Fixed hiring on Starsector 0.98.5a (portrait pick).

## 0.3.0.1 - 2026-11-12

- Rename: Colony Manager is now Auto Colonies. The mod folder is `Auto Colonies`. Delete the old Colony Manager folder in `mods/` before extracting, or the game finds the mod twice.
- Rename: The mod id is unchanged. Saves keep working.
- Rename: The repository is github.com/Mjolniar/auto-colonies (old links redirect). The release zip is AutoColonies.zip. The Version Checker file is AutoColonies.version.
- Rename: In-game text and logs say Auto Colonies. The supported mods list and links use Auto Outfitting's new name.

## 0.3.0.0 - 2026-11-11

- Saves: A save made on 0.3.0 does not load in 0.2.45.x. Back up your save before updating.
- Updating: Version Checker and TriOS do not show 0.3.0.0 as an update to 2.45.2. Download it from the release page.
- Settings menu: Added 20 main rows in 6 groups (Playstyle, Money, Building, Items and people, Security and diplomacy, Interface). Older settings are under Advanced (11 subgroups) or on a page.
- Settings menu: Ten rows are presets and show Custom when settings match no preset. Settings text is 1,458 words (was 2,980).
- Settings menu: See [Settings](settings.md).
- First-time setup: Added 5 rows: Spending, Extras, New colonies, War (Nexerelin), Credit reserve.
- Spending presets: Added Thrifty (30% of net income, 5% of savings, new upkeep up to 5% of net income), Normal (50%, 15%, 10%) and Lavish (80%, 30%, 20%). The per-project cap is Automatic: half, all or twice net income by preset.
- Build planner: Added one ranked queue for all colonies: builds, upgrades, surplus terraforming, extra Research Facilities, wonder sites, stable-location crews and story point improvements. See How it builds.
- Build planner: A build must pay back within 36 months (price, fee, build time). The limit rises to 120 months as surplus credits reach 10,000,000.
- Build planner: A colony's construction slot goes to the build that earns the most per day of construction. Money goes to the best return per credit. A large build waiting for money is saved for.
- Build planner: A building Autopilot takes down is not built again at that colony for a year. What Autopilot paid for a building counts against taking it down.
- Money tiers: Added Tight, Surplus, Idle money, Rich and Endgame, from free cash (credits less the reserve, production still to pay and 2 months of upkeep and fleet costs), colony net income and idle months. See Money.
- Money ladder: Added. From Surplus up, a share of free cash each month (20%, 35%, 60% or 80% by tier) goes to rungs in order: Stockpiles, Growth, Military, Shipyard, Expansion, Economy, Wonders. Presets: Balanced, Fleet, Expansion, Economy, Save. Each rung is On, Recommend only or Off.
- Shipyard: Added. From Idle money, orders ships from your Doctrine priority list through custom production at the game's prices plus a 2% fee. One order a month, up to 6 unclaimed hulls. Default: Recommend only.
- Stockpiles: Added. Buys goods for a colony's Local Resources when a shortage cuts industry output or patrols and the lost output is worth more than price, tariff and shipping. Up to 3 months of cover.
- Military rung: Added. From Idle money, upgrades stations, Patrol HQ and Military Base at Auto colonies and buys military goods. New upkeep from it stays within 15% of colony net income.
- Auto-expansion: Added as the New colonies row: Off (default), Ask first or Auto. Founds colonies by Nexerelin colony fleet or an Autopilot crew at cost plus the fee, with an administrator when the colony limit needs one. See New colonies.
- War level (Nexerelin): Added Off, Defend, Retaliate (default), Hold them down, Conquer and Scorched. Replaces the War fleets setting. War fleets act whatever the colony modes are. See War and security.
- Faction stances: Added Ally, Friendly, Neutral, Auto (default), Rival, Enemy and Destroy on the Factions page. A stance caps the War level used on that faction and sets how agents, IndEvo ambassadors and ceasefire offers treat it.
- Conquest (Conquer): Added invasions of Enemy and Destroy markets through Nexerelin's invasion fleets, one at a time. Conquered colonies get autonomy taken back and the Security goal until stable, take the mode from Conquered colonies start in (default Auto) and are kept above 0 stability only on Auto.
- Scorched: Added saturation bombardment of a Destroy faction's capital after you authorize it on the War page, with the reputation penalties of a bombardment in person.
- Retake (Retaliate and up): Added invasions of colonies that were yours, lost capital first, with the fee. On for new games and saves without Auto Colonies, off for saves from 0.2.45. A retaken colony keeps its earlier mode.
- Stand down: Added on the War page. Sets War level to Defend, clears declarations and bombardment authorization, and cancels operations still organizing with Nexerelin's full refund.
- War fees: Retakes, raids, blockades, invasions and bombardments pay the fee level times 10% of Nexerelin's price. Relief, defense fleets, base strikes and strikes back pay no fee.
- Spending freely: Changed (also for 0.2.45 saves). Starts at money tier Rich with credits over the 3,000,000 surplus line of at least half the line. Ends when the tier drops under Rich or credits over the line fall under a fifth of the line. (was 10,000,000 to start, under 7,000,000 to end).
- Endgame economy: Changed. Starts at money tier Endgame while spending freely. Ends when spending freely ends or colony net goes below 0. (was credits over the line under 5,000,000).
- Hazard pay: Changed. Below Rich, a colony gets it only if it reaches its next size within 24 months with it (36 when that size adds an industry slot) and keeps it up to 25% past that.
- Hazard pay: With the Growth rung On, the budget rises to the tier's share of colony net (Surplus 40%, Idle money 60%, Rich and Endgame no cap).
- Capital trade buildings: Changed. A trade building that costs more than it earns is built only from Surplus up, with the Economy rung On, while its upkeep at the capital stays within 5% of colony net. The make-room swap runs only from Idle money up.
- Surplus research and terraforming: Changed. They start something only from Surplus up with the Economy rung On. Another Research Facility must be worth more than its full upkeep below Idle money (was a quarter) and more than half of it above. Below Rich, at most 3 of its own.
- Terraforming: Fixed Ming Adi paying 1.525M to add Toxic Atmosphere and then planning to remove it. A world plan that already paid for terraforming switches only to a plan worth at least twice as much.
- Trade contracts: Fixed 16 missed deliveries on 11 contracts. Own contracts count against spare goods until Theory of Toolbox catches up. One new contract per commodity a month.
- Free port and hazard pay: Fixed free port changing 37 times and hazard pay 24. A closed free port reopens after its conditions hold 30 days.
- Net income: Budgets, caps and hazard pay use the 3-month median of colony net income. Custom production, AotD orders and your fleet's draw on colony stockpiles are not in it.
- Daily pass: Every step runs on its own. A step that fails 3 days in a row stops for the session, with one log line and a Diagnostics entry. The 0-stability safety net runs before the day's building.
- Saves from 0.2.45: Settings carry over. War fleets "Protect and strike back" becomes Retaliate, "Protect" becomes Defend, Off stays Off. Priorities become stances: High to Friendly (Ally when Stop raising relations at is Never stop), Normal to Auto, Ignore to Rival.
- Saves from 0.2.45: Retake is off, Auto-expansion is Off, no faction is Enemy or Destroy, bombardment is not authorized. Money ladder is Balanced. Shipyard and Expansion rungs are Recommend only, the other rungs On.
- Legacy planner: The 0.2.45 build engine is in 0.3.0.x behind a hidden switch (`legacyPlanner`). If the planner fails 3 days in a row, the old engine runs for the rest of the session.

## 0.2.45.2 - 2026-11-09

- Takedowns: Whatever Autopilot takes down at a colony (dead weight, over the industry limit, 0 stability, an endgame swap) stays off that colony's build list for a year, with whatever it was upgraded from.
- Upgrades: Removed the free +15,000 follow-through. An upgrade has to be worth it on its own.
- Spending freely: Starts at 10M of surplus and stays on until credits drop under 7M.

## 0.2.45.1 - 2026-11-06

- Shipping: Fixed items and AI cores shipped between colonies costing double the logged price. Recruiting agents had the same bug.
- Daily pass: Fixed one error skipping the rest of the day, including the 0-stability step. Every colony and build has its own error guard.
- Planning: Colonies with something under construction are re-planned weekly and the day the build finishes (was every day). Upgrades during construction may start up to a week later.
- Builds: Fixed a build or upgrade that did not start being paid for. A half-added building is removed.

## 0.2.45.0 - 2026-11-06

- Oddisz's mods: Added support for the colony side of [his mods](https://fractalsoftworks.com/forum/index.php?topic=36175.0). Suggested by Oddisz.
- Cloning: The three cloning industries are built when faster growth is worth it. They cost stability equal to the colony's size, so one is built only where there is room for a size of growth.
- Reverse Engineered Private Arsenal: Left to you. The hub, its core and Drone Replicator are not touched.
- Sol Renewed: Industries in Sol are priced for what they add to Sol's crisis meter. The Crises page shows the meter.
- Cleanup: The over-limit and dead-weight cleanups do not take down a building left to you (Spy Bureau, Bazaar, Reverse Engineering Hub and similar).

## 0.2.44.1 - 2026-11-06

- Industry limit: Fixed Zenemosrae having 5 industries on 3 slots and Ming Adi 4 on 3. Upgrades into industries (Ashes of the Domain allows several paths) count against the limit while under way.
- Over-limit colonies: An Auto colony over its limit takes down its weakest industry, one a day, until within the limit. Industries that hold up stability are kept, and it waits if the colony is about to grow into the room. Cores and items go to Autopilot Storage.
- Dead weight: Added takedown of an industry whose whole worth is what it makes and that has been worth less than its upkeep for 30 days (one per colony every 30 days), such as Roider Dives at Godecifica and Zenemosrae. Locked buildings, plan steps, anything that upgrades into one, military, defenses, ships and stability buildings are kept.
- Dead weight: Setting Settings, Building and planning, Take down industries that stopped paying (on by default). Lock a building on its colony panel to keep it.

## 0.2.44.0 - 2026-11-06

- Export value: Changed. A building is valued by what it adds to your share of each market. A colony's exports earn its share of that good's market across the sector: what it can ship times its accessibility, out of everyone's total. See How it builds.
- Export value: A building counts only what it makes above the colony's biggest source of that good, only what can ship off-world, and a building under construction counts as made. Covering your own colonies' shortage counts extra.
- Export value: On by default. Settings, Building and planning, Exports valued by market share turns it off for the flat rate.

## 0.2.43.2 - 2026-11-06

- Roider Dives: Fixed a Dives being built at every colony. A Dives (and the step up to Union HQ) counts only what it makes above what the colony already makes. Existing Dives stay. The capital still gets a Union HQ as a trade building.

## 0.2.43.1 - 2026-11-06

- Mod watch: Fixed false Roider Union "1 warnings". The check compares Roider's own supply lines with the formula.

## 0.2.43.0 - 2026-11-06

- Low profile: Added an expansion style. Colonies keep growing with attention costing ten times as much, no AI cores in colonies above size 3, nothing that takes a colony to 7 Pather interest, no free ports, no raids on other factions' colonies, no sabotage by agents, and Hegemony inspections not resisted.
- Low profile: Exports competing with Tri-Tachyon, fuel from 4 units and farming-only habitable colonies are priced in. Each rule lifts when that faction's crisis is over. Military structures are built sooner. It earns less.
- Low profile: Asked when you found your first colony. Switch under Settings, Expansion style. See [Low profile](low-profile.md).

## 0.2.42.4 - 2026-11-06

- Research: Auto research picks one tech at a time. If its databanks must come from another colony, they are shipped, and nothing else ships or starts until they arrive.

## 0.2.42.3 - 2026-11-06

- Update notifications: Added a Version Checker file. MagicLib and mod managers like TriOS show new versions with the download and patch notes link. Install this version once by hand.

## 0.2.42.2 - 2026-11-06

- Stability: Fixed Auto colonies sitting at 0 stability (the game decivilizes them over months). At 2 or less, a colony over its industry limit takes down an industry at once.
- Stability: Still at 0 after 20 days, a colony takes down the building costing the most stability (mod buildings included), one every 10 days. A capital trade building taken down this way stays off the plan for a year.
- Capital: A captured capital stays the capital. Another colony takes only production deliveries until it is yours again, and nothing is gathered meanwhile.

## 0.2.42.1 - 2026-11-06

- Industry slots: Fixed a weekly pass starting a new industry and an upgrade into an industry on the same free slot. Every build checks the slot count right before it starts.
- Over-limit colonies: An Auto colony over its limit takes down its weakest industry (core and item to Autopilot Storage) unless it is worth more than the 5 stability it costs or the colony is about to grow a size with room. One per colony every 30 days.
- Over-limit colonies: Defenses, fleets, ship-making, improved or locked buildings and plan needs are kept. Setting: Fix colonies over the industry limit.

## 0.2.42.0 - 2026-11-06

- Capital: Added one home colony. The manager picks the colony where your things are (production gathering point first), or you pick it on a colony's panel, in Settings, or by moving your gathering point. See Your capital.
- Capital: Custom production is delivered there. Weekly, ships, weapons, wings, blueprints, supplies, fuel and crew in other Storage tabs ship there (storage logistics fee, travel time). Colony items and AI cores go where they do the most good.
- Capital: After the fast buildout, with net income over 100,000 a month (changeable), its plan puts trade buildings first: Spaceport and Megaport, Commerce, mod trade buildings (Trade Outpost, Underworld, Yunru's Bazaar, Requisitions Centre, Salvage Yards, Union HQ), then a Waystation. They are built even when they do not pay for themselves.
- Capital: With every slot taken, it takes down its weakest ordinary industry for one, at most every 90 days. It waits if net income goes negative. Yunru's Bazaar is built at the capital only.
- Stations: Added a station at every colony after its fast buildout at size 4 (or half a year old), a Battlestation from size 6 and a Star Fortress from size 8 (a size sooner at the capital). Setting: Station at every colony.

## 0.2.41.2 - 2026-11-06

- Administrators: Fixed colony-limit administrators not appearing (the game offers one only when you dock). Past your colony limit with nothing on offer for 90 days, a recruiter is paid: a basic administrator for 250,000 plus the normal hiring cost and fee, arriving 30 days later.
- Administrators: The reserve is kept.

## 0.2.41.1 - 2026-11-06

- On-screen lines: Changed to a short code, a slash and the normal sentence, such as `BUILD / Hot Springs: Planetary Shield (750k¢)`. Nothing is clipped. Lines are normal white (yellow for important ones). Pings use the code `SCAN`.

## 0.2.41.0 - 2026-11-06

- On-screen lines: Added a short readout such as `AP\\ #HOT_SPRINGS :: BUILD // Planetary Shield // 750k¢`. The Log page keeps the full sentence. Setting: Settings, Interface, Announcement voice (Machine or Plain).

## 0.2.40.1 - 2026-11-06

- Station augments: A colony in a well-defended system keeps its station economic when its own threat is high (Mining Station augment, Hydroponics and Economy Conversion over combat augments). A raid or blockade on its way gets the combat loadout.
- Terraforming & Station Construction: Auto Colonies founds TASC mining stations (asteroid belt or field) and siphon stations (over a gas giant) as new colonies in systems with your Auto colonies that are calm or well defended, once TASC's research is done and the colony limit has room. A crew builds it for TASC's costs plus a fee in 60 days.
- Terraforming & Station Construction: The colony runs in Auto with the Money goal.

## 0.2.40.0 - 2026-11-06

- Stable locations: In systems with your Auto colonies, a construction crew builds the best stable spot by the colonies' goals: comm relay first (+1 stability at every colony of yours in the system), then nav buoy (+2 burn), then sensor array. Cost is vanilla's materials at base price plus a fee.
- Stable locations: It takes 10 days. One of each kind per system.
- Stable locations: Materials are refunded if the spot is taken.
- Stable locations: With Terraforming Made Easy, owned makeshift ones upgrade to Domain-era with a spare Beta core in Autopilot Storage when it pays for itself. With Ashes of the Domain, Hypershunt Receivers (+2 industry slots at every colony in the system) are built in systems your hypershunt reaches, and one stable spot per system stays free for one.

## 0.2.39.3 - 2026-11-06

- Panopticon cores (Emergent Threats: IX Revival): Fixed the Panopticon Instance and Panopticon Core not fitting in Autopilot Storage. Once a day one goes into an Auto colony's Surveillance Center without a better one.
- Panopticon cores (Emergent Threats: IX Revival): A replaced regular core goes to storage. The Hegemony does not count either for inspections.

## 0.2.39.2 - 2026-11-06

- Autopilot Storage: Takes only colony items it can install, AI cores, databanks, Antimatter Stabilizers, VPC chips and recipes. Anything else already in it moves to that colony's normal Storage with one log line.

## 0.2.39.1 - 2026-11-05

- Unification Center: Built only after everything before it in the plan is done, or when the last condition is already being made by a running project. The colony panel shows "waits until the rest of the plan is ready".

## 0.2.39.0 - 2026-11-05

- Storage logistics: Added, off by default. Weekly, mothballed ships, weapons, wings, unknown blueprints and hull mod specs (and optionally supplies, fuel, crew and marines) ship from other Storage tabs to a refit hub (production gathering point by default).
- Storage logistics: A sell hub receives known blueprints and specs plus spares. Each load costs a shipping fee (plus 1,000 a ship) from the monthly budget and takes travel time.
- Production orders: Plans producers for any good open Ashes of the Domain production orders need that none of your colonies make. It knows the vanilla producers (Refining for metals, Heavy Industry for heavy machinery and supplies).
- Test colony: Fixed every mod building reading "can't be measured" after a colony got Luddic Path cells. Event conditions are left off the test colony.
- Colony items: Every waiting item is matched against every free building and the best pairs go first. Fixed a second copy shipping to a colony whose first copy had arrived but was not installed.
- Research Facility: With research on Auto and none anywhere, one goes first in the focus world's plan (or the biggest colony's).
- Trade contracts: A building that makes something a running trade contract needs is not taken down by the endgame.
- VPC chips: Fixed double shipping for Industrial Evolution's Commodity Forge and other chip buildings. A chip already at the colony goes in first, and nothing ships while one is on its way.

## 0.2.38.0 - 2026-11-05

- Omega Core Everywhere and Astral Ascension Renewed: Omega cores in Autopilot Storage go to a building with Omega Core Everywhere, or govern a colony with Astral Ascension Renewed, only where Inspection risk allows its 10 inspection points. An AI core administrator is not swapped out.
- Niko's Scavengable Industries: Only learned blueprints are built. Big decisions (Blast-Core Mining, spy buildings, the Cryosanctum's crisis, the size-5 Planetary Defense Network) are left to you.
- Junk Yard Dogs and Carter's Freetraders: Their forges and Biolab are built when they pay. Drugs and hand weapons your faction bans do not export without a free port, from any mod's building.
- Vulpoids: Organ Farms, the Biotech Facility, the Midas Nanoforge (not placed where it pollutes a habitable world), the Mangonut tree, the Nanofiltration Core, the Lobster Bioforge and Profecto administrators are handled. The Vulpoid Bioforge is left to you.
- Yunru Industries, Slightly Better Tech-Mining and Yunru's Worlds: Secondary Drydocks is valued by what it adds next to Heavy Industry. Tech-Mining uses Slightly Better Tech-Mining's numbers. Advent Outreach is not improved. Bazaar is left to you.
- Roider Union: Rechecked against 2.3.1 and fixed a couple of Union HQ numbers. The Historical Roider Charter's extra output and range count. See [Supported mods](supported-mods.md).
- Fast buildout: Fixed it not ending for a colony that cannot turn a profit. It ends after 60 days with nothing that pays, or a year in total.
- Plan steps: A step with no way to be built or unlocked by research for 60 days stops holding the plan's top spot and slot, with a log line. A TME world whose own buildings are out of reach is dropped for a year.
- Plan steps: A world needing Mining or Farming is not picked where those cannot go. The slot a plan keeps free counts one per upgrade chain.

## 0.2.37.0 - 2026-11-05

- Autopilot Storage: Changed to per-colony storage. Items and cores needed elsewhere ship for a fee and travel time (a couple of days plus a day per light-year). The old shared storage moved to the biggest colony's.
- Agents: Recruiting costs 12,000 plus the fee and takes 60 days. Roles: the first on security, the second raising relations with preferred factions, and with offense a third that sabotages your chosen target.
- Remote actions: Administrators and IndEvo ambassadors take 30 days to arrive. The RAT settlement is founded by an expedition. Station augment changes from afar need a refit crew (7 days, immediate with your fleet in the system). Research items and databanks ship to the Research Facility.
- Removed: The storage cheat and the Derelict Empire attack settings.
- Fixed: Taking back an autonomous colony costs the normal unrest. Surplus terraforming deleting a core or item when taking a structure down. An extra 25% of colony upkeep charged to measured mod buildings.

## 0.2.36.3 - 2026-11-05

- Storage cheat: Changed its description to the commodities, weapons and fighters it adds.

## 0.2.36.2 - 2026-11-05

- Nanoforges: A polluting nanoforge is not placed in a forge on a habitable world, whatever the goal, threat or endgame. Waiting items skip those forges.

## 0.2.36.1 - 2026-11-05

- Autopilot Storage: Industrial Evolution's consumables, blueprints, Random Assortment's Forge-Assisted Industry BP and other right-click items cannot go in. Items already in move to the biggest colony's normal Storage.
- Research spends colony items: Added, off by default. With credits over the surplus line, Auto research spends colony items on techs that need them once everything the tech needs is on hand.
- Research spends colony items: From storage takes from storage. From storage and buildings also pulls from buildings (least useful use first, not a locked one).

## 0.2.36.0 - 2026-11-05

- Endgame economy: When spending freely (10M+ over the surplus line) with 1M a month or more, credits count 5%, and fleets, ship quality and defense count more. It stays on while 5M+ over the line and not losing money. Setting: Endgame economy.
- Weak industries: With no free slot, an Auto colony can take down its weakest non-military industry for one worth at least 10,000 a month more (one a day across the empire, one a month per colony). Ship producers and improved, locked or plan buildings stay. Setting: Endgame replaces industries.

## 0.2.35.0 - 2026-11-05

- War fleets (Nexerelin): Added under Colony policies: Protect and strike back (default), Protect or Off. Fleets are ordered through Nexerelin. No invasions or colony expeditions. Aggressive actions target only factions already hostile to you.
- War fleets: Relief fleets for colonies under siege, defense fleets where threat is high or a crisis is coming, base strikes on known pirate and Pather bases within 15 light-years, and on Protect and strike back, raids on whoever attacked your colonies in the last 120 days.
- War fleets: Normally one a day, keeping 40% of fleet request points for emergencies. Relief and defense fleets are paid like emergencies (past the budget if needed, not under your reserve).
- War fleets: While spending freely, up to three a day, 1.5x bigger, against every known base at any distance, plus a standing defense fleet at every colony.

## 0.2.34.3 - 2026-11-05

- Autonomy: Off, Recommend or Auto. A colony at least a year old that lost money 3 months in a row, or the best pick in a money emergency, is logged on Recommend or made autonomous on Auto (one every 30 days, none while spending freely unless it is an emergency).
- Autonomy: Taking one back costs Nexerelin's normal unrest.

## 0.2.34.2 - 2026-11-05

- Log: Every log entry shows on screen as one short line. Setting Log entries on screen, under Interface: Every entry (recommended), Important only or Off.

## 0.2.34.1 - 2026-11-03

- Spending freely: Removed the pause when net income fell under half its best month. It ends only when you drop back under 10M over the line.
- New upkeep: Each month's new upkeep can reach a sixtieth of the surplus (was a fifth of net income).

## 0.2.34.0 - 2026-11-03

- Wonders and gates (Ashes of the Domain): While spending freely, the Daedalus Array is built once your colonies make every good it needs. The Bifrost Control Center becomes a plan step at the focus world, then a Bifrost gate goes up in each of your systems, one a day, once its goods are made.
- Wonders and gates (Ashes of the Domain): Setting: Wonders and gates while spending freely.
- Log: Sieges, threat, free port, hazard pay and station augments log at most once every 10 days per colony. The day's trade contracts are one line. Other lines are shorter. Routine lines are off the screen (reversed in 0.2.34.2).

## 0.2.33.0 - 2026-11-03

- Spending freely: Build prices count 5% (was a quarter). Colonies re-plan every day (was 7 days), with up to three upgrades at once, at most one per building.
- Defenses: Every colony maxes its defenses: station to the top of its line, Heavy Batteries, Planetary Shield, Megaport and Patrol HQ go first in its plan.
- Surplus terraforming: Ranks by total gain (was gain per credit). A deposit needing tectonic activity gets it first, and the plan's hazard cleanup leaves it alone until the deposit is in (or 540 days).
- Ashes of the Domain: Goods producers (Crystalizator, Enrichment Facility, High-tech Industry, Skunkworks, Civilian Heavy Production) are planned with no special project running.

## 0.2.32.0 - 2026-11-03

- Spending freely: At 10M past the surplus line, every Approve and Auto colony spends from the surplus (was the focus world only), past the monthly budget and per-project cap. Prices count a quarter, credits count a quarter in a building's worth, and new upkeep can reach a fifth of net income a month.
- Terraforming: The plan's best-value terraforming is paid from the surplus everywhere. Surplus terraforming runs up to three projects a day per colony.
- Setting: Over 10,000,000 of surplus: Every colony spends it, or Focus world only.

## 0.2.31.0 - 2026-11-03

- United Aurora Federation: Colony items are valued from UAF's code. The Dimensional Nanoforge (+2 output, better ship quality, clears pollution) goes to habitable worlds' forges ahead of polluting nanoforges.
- United Aurora Federation: The Servosync Pump, Modular Purifier and Garrison Transmitter are placed by value. The Rice Cooker, Dimensional Stove and Access Router are not installed.

## 0.2.30.0 - 2026-11-03

- Daily checks: Fixed surplus terraforming plans, research ranking, the focus world, siege and crisis checks, TASC lists and trade contract shortfalls refreshing once per loaded save. They refresh daily.
- Surplus terraforming: Fixed TME toggle projects being started in the wrong direction, which put pollution and high gravity back repeatedly (about 7.3M). It re-plans when conditions change.
- Special project supply (Vaults of Knowledge): Goods the running black site project needs that no colony makes get their producer built as a first plan step, with producers for its inputs, then research. Setting: Special project supply: Auto, Warn only or Off.

## 0.2.29.0 - 2026-11-03

- Research buildings (Vaults of Knowledge): AI cores in the Research Facility and Black Site cut research, maintenance and cover-up costs (Alpha by half, Beta by a quarter, Gamma by a tenth), and an Alpha on a pre-collapse facility world adds a databank a month. Black Sites take cores.
- Alpha cores in research buildings: Setting First (default) or By value. On First, Alphas go to research buildings before anywhere else, and a colony at its inspection limit sends its least useful other core to storage.
- Surplus research: With credits over the surplus line, more Research Facilities are built at Auto colonies, up to the 8 Vaults of Knowledge counts, while remaining research is worth more than a quarter of their upkeep. They come down when nothing is left to research. Setting: Surplus research.

## 0.2.28.0 - 2026-11-03

- Station augments: Each augment is valued as a share of that station's power from its own ships (fighter wings, guns, point defense, shields, drones) and AI core. The colony panel shows the loadout.
- Station augments: Bubble Shield and the SotF weapon platforms are core defensive picks. Midline stations get Axial Overclocking and Deep Shield Dissipators.
- Station augments: Warmind Refit is not used while the Dustkeepers are hostile. Macro Thrusters, Backline Deployment, Safety Shunt and Support Outfit are not picked.
- Station augments: K-Type Shield Shunt and Deep Shield Dissipators are not installed together.

## 0.2.27.0 - 2026-11-02

- Mod stations: Interstellar Imperium's stations (and Knights of Ludd's, Ashes of the Domain's Star Citadel) count by their station tags in the defense estimate behind Hegemony inspections and AI core risk. A core in one counts for fighting strength.

## 0.2.26.0 - 2026-11-02

- Surplus terraforming: The focus world's leftover surplus goes into Terraforming Made Easy projects at every Auto colony, most value per credit first: richer deposits and farmland, harsh conditions cleared, the structure built first when the colony has none.
- Surplus terraforming: Planned one or two projects ahead on TME's rules. It keeps a habitable world's farmland and organics and what the colony's plan needs. A structure it builds is taken down when its work is done.

## 0.2.25.0 - 2026-11-02

- Auto research: After techs that pay within a year, it starts the best of the rest, with the same databank hold for plan techs and the same upkeep room.

## 0.2.24.0 - 2026-11-02

- New upkeep cap per month: Automatic by default: 10% of last month's net income, at least 20,000. Older saves switch once. Fixed is in Settings.

## 0.2.23.0 - 2026-11-02

- Scope: Removed the fix for Industrial Evolution's unnamed Income report rows. Fixes for other mods live in their own mod.
- Diagnostics: The Mjolniar's mods section lists only installed mods.

## 0.2.22.0 - 2026-11-02

- Colony screen button: Searches for its spot only on the colonies tab and the docked colony screen, away from Auto Outfitting's button. With a full building grid it tries every free spot along the rows and beside the buildings.
- Diagnostics: Added a Mjolniar's mods section with each mod's version, warnings, errors and status line.

## 0.2.21.0 - 2026-11-02

- Surplus investment: Credits over a line (3M recommended) go into one focus world, past the monthly budget and per-project cap, with upkeep up to 10% of net income, prices counted at a quarter and its best terraforming paid from the surplus. The manager picks the Auto colony with the most to build on, or you pick it on the colony panel.
- Items: A better colony item replaces a lesser one. An item with nowhere to go makes a building that would take it worth what the item adds.
- Research: Can spend AI cores from Autopilot Storage on Auto.

## 0.2.20.1 - 2026-11-01

- Story points: Fixed the monthly cap counting improvements, which cost double each at one colony (a cap of 4 spent 30 story points). It counts story points, and the cheapest improvement anywhere goes first.

## 0.2.20.0 - 2026-11-01

- Orbital Fusion Lamp: Placed only where what it cancels (Cold, Very Cold, Poor Light, Dark) beats the heat it adds, or where the plan wants Extreme Heat. A lamp that only adds heat goes to Autopilot Storage.

## 0.2.19.0 - 2026-11-01

- Research: After 90 days holding databanks for a plan tech it cannot afford, the best affordable tech goes ahead. Research upkeep counts AI cores in your facilities (Alpha halves, Beta takes a quarter off, Gamma a tenth).
- Plan steps: A step research blocks gives up its top spot. A new industry outside the plan counts 30,000 a month less while free slots are all the plan's industries have left.

## 0.2.18.1 - 2026-11-01

- Research Facility: Vaults of Knowledge's Research Facility is worth half of what your colony plans wait on in research when you have none. Auto builds one when research is the holdup.

## 0.2.18.0 - 2026-11-01

- Research: Traces every open research-locked plan step back through its whole tech chain and shares the step's value along it.
- Databanks: While the best plan tech is short of databanks, databanks go only to plan techs, and the log says what is next. Databanks in colony Storage count. Plan techs are not held back by the new upkeep cap.
- Special-world plans: A TME world is picked only if its industries plus the colony's others fit the slots at max size. TME structures and the Unification Center get plan priority. A colony with no reachable world keeps only hazard cleanup.

## 0.2.17.0 - 2026-11-01

- Hazard pay: Colonies are ranked by how much faster it makes them grow. The best get it while the speed-up is at least Hazard pay where growth is at least (25%) and all of it fits in Hazard pay limit, all colonies (30% of net income recommended).
- Hazard pay: The log gives months to the next size with and without it.
- Autonomy: The manager does not grant it. The old Auto setting recommends. Colonies an older version made autonomous are taken back when there is room.

## 0.2.16.1 - 2026-11-01

- Autonomous colonies: A colony the manager made autonomous is taken back once there is no money emergency, taking it back does not start one, and there is room under the colony limit or an idle administrator.

## 0.2.16.0 - 2026-11-01

- Colony limit: Going past the limit puts an idle administrator in charge or hires one, whatever the money says, past the monthly budget if needed and not under your reserve.

## 0.2.15.0 - 2026-10-31

- Station augments: While a colony plans for Security (high threat, or that goal), combat augments count ten times as much and take augment points from output augments. The output ones return when the threat drops. The swap happens the day the threat changes.

## 0.2.14.1 - 2026-10-31

- Colony screen button: Sits in the panel above the buildings, its spot is recalculated four times a second, hidden screens are skipped, and it is found from the colonies and docked screens.

## 0.2.14.0 - 2026-10-31

- Crises: Crisis progress costs about 640 a month per point. An AI core pays for the Hegemony progress and Pather interest it adds (size 3 and under is free from the Hegemony, 7 Pather interest at size 4+ means a Pather cell).
- Crises: Patrol HQ, Military Base and High Command count how much they slow crises. Fuel Production counts the Sindrian Diktat progress it adds.
- Crises: The colony the next crisis is likely to hit plans for Security until it is over. A colony a Pather attack is coming for sheds AI cores. A coming Nexerelin police raid closes free ports.
- Hegemony inspection orders: Setting Advise (logs each inspection coming for an Auto colony), Resist where defended or Off. The game's default is to comply, which hands over every core there.
- Inspection risk: Never inspected is recommended. Added Small colonies only (no cores in colonies above size 3).

## 0.2.13.1 - 2026-10-31

- Attack settings: The Attacks on your colonies settings show and apply only in a Nexerelin Derelict Empire (or V2) start. There the recommended settings turn them on (at most 4 pirate and crisis fleets per system, Nexerelin attacks 90 days apart).

## 0.2.13.0 - 2026-10-31

- Attacks on your colonies: Added a settings group, off unless picked and outside the recommended settings (removed in 0.2.37.0).
- Nexerelin attacks: Invasions, raids and blockades come one at a time, 30 to 180 days apart, by holding Nexerelin's attack cooldown on your faction while any attack is on its way or under way. IX Revival's blockades are covered.

## 0.2.12.0 - 2026-10-31

- Sieges: A colony is under siege when blockaded, with hostile fleets at the planet, with hostiles in its system that outweigh its defenses, or with a raid, invasion or blockade headed to it. It gets only defense builds (none during a fast buildout), and its items, AI cores, administrators, policies, station augments and TASC projects are left alone.
- Trade contracts: Skip goods that cannot ship. Theory of Toolbox delivers only from colonies with a spaceport and accessibility above 0. Surplus of colonies that cannot deliver or are under siege does not count.

## 0.2.11.0 - 2026-10-31

- Research (Vaults of Knowledge): Starting a tech (Auto or Start button) moves what the Research Facility is short of from Autopilot Storage (databanks, an Alpha core, colony items), then databanks from your fleet's cargo. Autopilot Storage takes research databanks.
- Storage cheat: Added in a Cheats group, off by default (removed in 0.2.37.0).

## 0.2.10.0 - 2026-10-31

- AI cores: Placed, upgraded, moved and returned to storage by what they add in each building, against Hegemony inspection risk. Setting Inspection risk: More where defended (default), Never inspected or No limit.
- AI cores: There is no limit once you have beaten the inspections, while in the Persean League, or when the Hegemony has no colonies.
- Administrators: Hired, placed and moved only where Industrial Planning's output and the colony limit's stability are worth more than salary and hiring cost. Each colony's Overview shows salary against what the administrator adds.
- Emergency autonomy (Nexerelin): Setting Off, Recommend or Auto. In a money or colony limit emergency, the colony whose autonomy saves the most is made autonomous and taken back when the emergency ends.

## 0.2.9.0 - 2026-10-31

- Agents page: Shows every agent you have (Nexerelin) with type, level and job. Let Autopilot use it hands one over, Take it back returns it. The manager does not hire another agent while one of yours that could do the job is idle.

## 0.2.8.0 - 2026-10-30

- Colony screen: Added a Colony Autopilot button on the colony's own screen, with a setting to turn it off. The empire panel has its own Colony Autopilot filter in Intel.
- Recommended settings: Automatic budget (half of last month's net income plus 15% of credits above the reserve), pause on loss off, reserve a quarter of credits (50k-500k), AI cores and administrator hiring on, research, trade contracts and the RAT settlement on Auto, agents on Defense.
- Upkeep: Fixed every building's upkeep being multiplied by 500 twice, which made Auto build Research Facilities that did nothing. Bad upkeep corrections in older saves are cleared once.
- Builds: Approve, Auto and fast buildout start only builds that pay back within about 3 years. The colony panel shows money a month after upkeep and a Ranked line with each pick's score. Nexerelin outposts are left alone.
- Fixed: Ashes of the Domain research can use databanks from your fleet's cargo. Sorting Command, Income with Industrial Evolution crashed.

## 0.2.7.0 - 2026-10-30

- Fast buildout: After you found your first colony, a popup offers it. The colony goes to Auto with the Money goal and starts the fastest-paying builds every day with everything above a credit floor you pick (250,000 default), outside the monthly budget, until it has made money for 30 days in a row.
- Fast buildout: The colony panel can stop it, change the floor, or start one for a colony losing money.
- Nexerelin agents: Off by default. On Defense, agents run Nexerelin's counter-insurgency, Pather cell and pirate base operations at colonies that need them, then raise relations with factions running operations against you.
- Nexerelin agents: Defense and offense adds sabotage of military industries, only against factions already hostile to you.
- Nexerelin agents: Hires level-1 agents (12,000 each) up to Agents to keep, only while there is work, and orders only agents it hired. The empire panel lists your agents and enemy operations against your colonies.
- Balance changes: Battle size and sticky combat autopilot moved to a separate mod. This mod changes no game settings.

## 0.2.6.0 - 2026-10-30

- Release: First downloadable release. Extract the zip into `Starsector/mods/`.
- Ashes of the Domain and other reflected integrations: Fixed failing in a real game because the script loader refuses Java reflection classes. They use a route the loader allows, and the build checks for it.
- Mod updates: A load-time health check per integration (OK, newer version, partly off, off; on the Diagnostics page), mod values read live where exposed, a weekly calibration of estimates against real buildings, and a breaker that pauses an action for the session after 3 failures in a row.

## 0.2.5.0 - 2026-10-30

- Mod support: Added Terraforming & Station Construction, Grand.Colonies, Substance.Abuse, Volkov Industrial Conglomerate, Roider Union, Planetary Shield: Access Control, Unknown Skies, What We Left Behind and Astral Ascension.
- Mod details: Roider Dives and Union HQ count deposits in range. A Brewery is built only with a recipe. A colony with viral contamination gets Volkov's anti-EVC treatment first. With Astral Ascension, hazard pay does not push a planetless colony past size 10.
- Deposits and items: Deposits a mod adds to the game's tables score like vanilla ones. Any mod's colony item can be placed through the game's item effect table.
- Building limit: No builds past 12 buildings without Grand.Colonies.

## 0.2.4.1 - 2026-10-30

- Name: The mod has its own name. The mod id is unchanged. Saves keep working.

## 0.2.4.0 - 2026-10-30

- Research (Ashes of the Domain: Vaults of Knowledge): Each startable tech is valued by what it unlocks for your colonies. On Auto it starts techs that need only databanks, moving databanks from Autopilot Storage when short. Your queue is not touched.
- Megastructures: Setting Megastructures (Hypershunt, Pluto, Nidavelir). A Coronal Hypershunt colony gets nothing built or placed.
- Megastructures: Wormhole range is kept at the smallest reaching the receivers serving your colonies. The empire panel lists where receivers would pay off.
- Megastructures: Pluto's laser runs at full strength with ore to the forges. Nidavelir's Nexus Core gets manpower while inputs are supplied.
- AotD colony items: Tenebrian Nanoforge, Synchrotron, Atmospheric Driver, Catalytic Core and Turing Engine are placed from Autopilot Storage like vanilla ones.
- Hidden buildings: Megastructure defenses and the hypershunt's port are not touched. Bifrost gates, Grand Wonders, black site projects and custom production are left to you.

## 0.2.3.0 - 2026-10-30

- Industry synergies (Ashes of the Domain: Vaults of Knowledge): A build's value includes synergies it starts, ends or strengthens, such as Ore-To-Core or Rapid Transportation, 19 in all. Only synergies researched at that colony count. The colony panel shows active ones and those one building away.
- Trade contracts (Theory of Toolbox): Offers are listed with pay and gain over exporting. One is taken only when Theory of Toolbox's figures leave 15% of production to spare and it pays more than exports: through an Accept button on Advisor, or up to 3 a day on Auto.
- Trade contracts (Theory of Toolbox): Goods your contracts are short of count as shortages.

## 0.2.2.0 - 2026-10-30

- Station Augments: The manager picks the set of known augments worth the most within each station's augment points, weighted by goal. Auto colonies apply it, others get an Apply button.
- Station Augments: Augments you installed are not changed, and one you removed is not put back. Setting Station augments: Follow colony mode, Recommend only or Off.

## 0.2.1.1 - 2026-10-30

- Colony panel: Fixed a crash from a stray percent sign in the threat line text when a colony on Approve or Auto planned for Security.

## 0.2.1.0 - 2026-10-30

- Release: First version on GitHub: Colony Autopilot for Starsector 0.98a.
- Modes and goals: Each colony runs Off, Advisor (default), Approve or Auto, with a goal: Balanced, Money, Security, War production, Rapid growth or Self-sufficiency. Auto builds within a monthly budget, a per-project cap and a credit reserve.
- Items, cores, administrators, threat: Colony items and AI cores in Autopilot Storage are placed, idle administrators assigned, and a colony switches to Security at 70% threat (back below 65% after 60 days).
- Unknown mod buildings: Measured on a throwaway probe colony and scored like vanilla ones. Diagnostics rates every building Supported, Measured, Analyzed or Quarantined.
- Optional support: Terraforming Made Easy, Industrial Evolution, Nexerelin (emergency defense fleets), Emergent Threats (Antimatter Stabilizer conversion), Ashes of the Domain and Random Assortment of Things (Frontiers settlement).
