# Patch notes

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

- Story mode: Added. Off by default. The Colony Autopilot stays dormant until you find the Autarch AI Core, then runs the abilities you buy with processing power from the AI cores you feed it. [Story mode](story-mode.md).
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
- Build planner: Added one ranked queue for all colonies: builds, upgrades, surplus terraforming, extra Research Facilities, wonder sites, stable-location crews and story point improvements. See [How it builds](how-it-builds.md).
- Build planner: A build must pay back within 36 months (price, fee, build time). The limit rises to 120 months as surplus credits reach 10,000,000.
- Build planner: A colony's construction slot goes to the build that earns the most per day of construction. Money goes to the best return per credit. A large build waiting for money is saved for.
- Build planner: A building Autopilot takes down is not built again at that colony for a year. What Autopilot paid for a building counts against taking it down.
- Money tiers: Added Tight, Surplus, Idle money, Rich and Endgame, from free cash (credits less the reserve, production still to pay and 2 months of upkeep and fleet costs), colony net income and idle months. See [Money](money.md).
- Money ladder: Added. From Surplus up, a share of free cash each month (20%, 35%, 60% or 80% by tier) goes to rungs in order: Stockpiles, Growth, Military, Shipyard, Expansion, Economy, Wonders. Presets: Balanced, Fleet, Expansion, Economy, Save. Each rung is On, Recommend only or Off.
- Shipyard: Added. From Idle money, orders ships from your Doctrine priority list through custom production at the game's prices plus a 2% fee. One order a month, up to 6 unclaimed hulls. Default: Recommend only.
- Stockpiles: Added. Buys goods for a colony's Local Resources when a shortage cuts industry output or patrols and the lost output is worth more than price, tariff and shipping. Up to 3 months of cover.
- Military rung: Added. From Idle money, upgrades stations, Patrol HQ and Military Base at Auto colonies and buys military goods. New upkeep from it stays within 15% of colony net income.
- Auto-expansion: Added as the New colonies row: Off (default), Ask first or Auto. Founds colonies by Nexerelin colony fleet or an Autopilot crew at cost plus the fee, with an administrator when the colony limit needs one. See [New colonies](expansion.md).
- War level (Nexerelin): Added Off, Defend, Retaliate (default), Hold them down, Conquer and Scorched. Replaces the War fleets setting. War fleets act whatever the colony modes are. See [War and security](war-and-security.md).
- Faction stances: Added Ally, Friendly, Neutral, Auto (default), Rival, Enemy and Destroy on the Factions page. A stance caps the War level used on that faction and sets how agents, IndEvo ambassadors and ceasefire offers treat it.
- Conquest (Conquer): Added invasions of Enemy and Destroy markets through Nexerelin's invasion fleets, one at a time. Conquered colonies get autonomy taken back and the Security goal until stable, take the mode from Conquered colonies start in (default Auto) and are kept above 0 stability only on Auto.
- Scorched: Added saturation bombardment of a Destroy faction's capital after you authorize it on the War page, with the reputation penalties of a bombardment in person.
- Retake (Retaliate and up): Added invasions of colonies that were yours, lost capital first, with the fee. On for new games and saves without Auto Colonies, off for saves from 0.2.45. A retaken colony keeps its earlier mode.
- Stand down: Added on the War page. Sets War level to Defend, clears declarations and bombardment authorization, and cancels operations still organizing with Nexerelin's full refund.
- War fees: Retakes, raids, blockades, invasions and bombardments pay the fee level times 10% of Nexerelin's price. Relief, defense fleets, base strikes and strikes back pay no fee.
- Spending freely: Changed (also for 0.2.45 saves). Starts at money tier Rich with credits over the 3,000,000 surplus line of at least half the line. Ends when the tier drops under Rich or credits over the line fall under a fifth of the line. (was 10,000,000 to start, under 7,000,000 to end)
- Endgame economy: Changed. Starts at money tier Endgame while spending freely. Ends when spending freely ends or colony net goes below 0. (was credits over the line under 5,000,000)
- Hazard pay: Changed. Below Rich, a colony gets it only if it reaches its next size within 24 months with it (36 when that size adds an industry slot) and keeps it up to 25% past that.
- Hazard pay: With the Growth rung On, the budget rises to the tier's share of colony net (Surplus 40%, Idle money 60%, Rich and Endgame no cap).
- Capital trade buildings: Changed. A trade building that costs more than it earns is built only from Surplus up, with the Economy rung On, while their upkeep at the capital stays within 5% of colony net. The make-room swap runs only from Idle money up.
- Surplus research and terraforming: Changed. They start something only from Surplus up with the Economy rung On. Another Research Facility must be worth more than its full upkeep below Idle money (was a quarter) and more than half of it above. Below Rich, at most 3 of its own.
- Terraforming: Fixed Ming Adi paying 1.525M to add Toxic Atmosphere and then planning to remove it. A world plan that already paid for terraforming switches only to a plan worth at least twice as much.
- Trade contracts: Fixed 16 missed deliveries on 11 contracts. Own contracts count against spare goods until Theory of Toolbox catches up. One new contract per commodity a month.
- Free port and hazard pay: Fixed free port changing 37 times and hazard pay 24. A closed free port reopens after its conditions hold 30 days.
- Net income: Budgets, caps and hazard pay use the 3-month median of colony net income. Custom production, AotD orders and your fleet's draw on colony stockpiles are not in it.
- Daily pass: Every step runs on its own. A step that fails 3 days in a row stops for the session, with one log line and a Diagnostics entry. The 0-stability safety net runs before the day's building.
- Saves from 0.2.45: Settings carry over. War fleets "Protect and strike back" becomes Retaliate, "Protect" becomes Defend, Off stays Off. Priorities become stances: High to Friendly (Ally when Stop raising relations at is Never stop), Normal to Auto, Ignore to Rival.
- Saves from 0.2.45: Retake is off, Auto-expansion is Off, no faction is Enemy or Destroy, bombardment is not authorized. Money ladder is Balanced. Shipyard and Expansion rungs are Recommend only, the other rungs On.
- Legacy planner: The 0.2.45 build engine is in 0.3.0.x behind a hidden switch (`legacyPlanner`). If the planner fails 3 days in a row, the old engine runs for the rest of the session.
- Untested in a running game: saturation bombardment by a player-faction fleet; Nexerelin colony fleets for the player; conquered colonies (autonomy taken back, rebellions); an invasion while holding a commission; ceasefire offers by stance; Stand down and Recall all refunds; an Autopilot crew founding a colony.

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

- Export value: Changed. A building is valued by what it adds to your share of each market. A colony's exports earn its share of that good's market across the sector: what it can ship times its accessibility, out of everyone's total. See [How it builds](how-it-builds.md#how-it-picks).
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

- Capital: Added one home colony. The manager picks the colony where your things are (production gathering point first), or you pick it on a colony's panel, in Settings, or by moving your gathering point. See [Your capital](capital.md).
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
- Panopticon cores (Emergent Threats: A replaced regular core goes to storage. The Hegemony does not count either for inspections.

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
