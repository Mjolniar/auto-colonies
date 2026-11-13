# Auto Colonies

- Auto Colonies: Colony management automation (Colony Autopilot) for Starsector 0.98a.
- Balance: The mod makes no balance changes. It manages the player's colonies.
- Battle size and sticky combat autopilot: Not part of the mod.

## Install

1. Put the `Auto Colonies` folder in `Starsector/mods/`.
2. Enable Auto Colonies in the launcher. The mod id is `jonah_tweaks`. No dependencies are required.
3. Defaults for new saves are in `data/config/colony_autopilot.json`. Every option can also be changed in-game.
4. Do not remove the mod from a save that has used it. Autopilot Storage is a market tab in the player's colonies.

- Optional integrations: Each loads only when its mod is enabled. Any combination works. See "Building from source".
- Integrated mods: Terraforming Made Easy, Industrial Evolution, Nexerelin, Emergent Threats, Ashes of the Domain (Building Menu Overhaul; Vaults of Knowledge; Theory of Toolbox), Random Assortment of Things, Station Augments, Terraforming & Station Construction, Grand.Colonies, Substance.Abuse, Volkov Industrial Conglomerate, Roider Union, Planetary Shield: Access Control, Unknown Skies, What We Left Behind, Astral Ascension, Omega Core Everywhere, Astral Ascension Renewed, Niko's Scavengable Industries, Junk Yard Dogs, Carter's Freetraders, Vulpoids, Yunru Industries, Slightly Better Tech-Mining, Rogue Planets, Kadur Remnant.

## Using it

- Colony panel: The Colony Autopilot button on the colony screen, or right-click Population & Infrastructure, then Colony Autopilot... The button needs a free spot near the building grid (colonies tab and docked market screen). Setting Button on the colony screen turns it off.
- Empire panel: Intel screen, Colony Autopilot filter. Pages: Colonies, Autopilot Storage, Colony crises, Expansion (0.3.0.0), War (0.3.0.0, Nexerelin), Mod features, Settings, Factions (0.3.0.0), Log, Diagnostics.
- Settings: 20 main rows in 6 groups and an Advanced group with 11 subgroups (0.3.0.0). One group is open at a time.
- Modes per colony: Off, Advisor (default: a ranking beside the build menu, the player builds), Approve (proposals in the approval summary), Auto (builds within the budget).
- Empire mode (0.3.3.0, `CAModeTable`): Empire-wide settings follow one mode. Each mode has its own defaults. A setting the player changes is flagged "set by you" and keeps its value through mode changes.
- Approval summary (0.3.3.0, `CAApprovalDialog`): One dialog with every decision waiting, every 60 days by default, at a moment with no dialog, menu or battle. Approve all, Choose each, Reject all, Later. Every approval checks the credit reserve and pays the fee.
- Build menu hints (0.3.3.0, `CABuildHints`): A panel beside the game's build menu and Building Menu Overhaul's at Advisor colonies. On 0.98.5a also a tooltip line.
- Goals: Balanced, Money, Security, War production, Rapid growth, Self-sufficiency.
- Plan: Each colony gets a plan from its planet, hazards and resources.
- Plan with Terraforming Made Easy: The plan includes a reachable special world (Forge, Fortress, Paradise, Hive, Death, Waste). A world is picked only if its industries and the colony's other industries fit the slots at max size (1 to size 3, then 2, 3 and 4 at sizes 4 to 6, plus bonus slots).
- Plan with Terraforming Made Easy: A year of the world's end conditions (hazard, deposits) counts. With no reachable world the plan is plain hazard clean-up.
- Plan steps: A step blocked by research gives up its rank to the next buildable step. A new industry outside the plan counts 30,000 a month less while the free slots are all the plan's industries have left.
- Fast buildout: After the first colony is founded, a popup offers it. Auto starts the fastest-paying builds daily, with everything above a credit floor (100k, 250k, 500k or 1M; 50k-2M in the colony panel).
- Fast buildout ranking: Money goal weights, with ground defense, fleets and ship quality worth nothing. It builds no defenses, patrols or stations.
- Fast buildout end: After 30 days in a row of positive colony net income. The colony stays in Auto with its own goal.
- First switch to Approve or Auto: Opens the setup page (0.3.0.0, `CASetupUI.renderFirstTime`) with the recommended settings. Everything that pays for itself is on. Removals, story points and offense stay with the player. 5 rows: Spending, Extras, New colonies, War with Nexerelin, Credit reserve.
- Reset to recommended (`CASetupUI.resetToRecommended`): A Settings page button. It clears "set by you" and applies the empire mode's defaults. It keeps Expansion style, faction stances, the capital pick and the focus world.
- Orbital Fusion Lamp: Cancels Cold, Very Cold, Poor Light and Dark. Needs 10 volatiles.
- Orbital Fusion Lamp: On a world that is not cold it adds heat. Autopilot places it only where the cancelled cold or darkness is worth more than the heat, or where the plan needs Extreme Heat.
- Orbital Fusion Lamp: A lamp at an Auto colony that does neither goes back to Autopilot Storage, one a day.
- Low profile (0.2.43.0, `CALowProfile`, setting `lowProfile`): Picked in the first colony's dialog. `CACrisis.pointCost` x10.
- Low profile rules: Free ports are closed (`CAPolicies`). No attacker raids (`CAWar`). No offense agents (`CASpies.offense`). No Omega/IX cores. No RAT settlement by a Remnant nexus. Inspections are not resisted. Each rule lifts when that faction's crisis is over for good.
- Low profile daily run: Pulls items over the line and swaps an AI administrator for an idle human one.
- Update notifications (0.2.42.3): `AutoColonies.version` at the mod root, registered in `data/config/version/version_files.csv`, in Version Checker format. masterVersionFile is the GitHub main copy. directDownloadURL is the latest release's `AutoColonies.zip`. changelogURL is the patch notes. The build writes its version from mod_info.json.
- Update notifications (0.2.42.3): The release refuses a zip whose version file disagrees.
- Stability guard (0.2.42.2, `CAOverLimit`): The game decivilizes a colony at 0 stability for months. DecivTracker samples over 16 months.
- Stability guard, 0 stability: At 0 for 20 days, the building with the most negative own stability modifier is removed, one every 10 days. A shed capital step is held off the plan for a year.
- Stability guard, 0 stability: Every colony at 0 stability gets a monthly on-screen alert with its four worst modifiers. Capital trade steps wait under stability 3.
- Industry limit (0.2.42.1, `CABuild.takesSlot`, `CAOverLimit`): Setting Fix colonies over the industry limit, Yes by default.
- Industry limit, daily fix: An Auto colony over its limit takes down its weakest industry the engine can value, one per colony every 30 days. Its core and item go to Autopilot Storage.
- Industry limit, daily fix: Excluded: building or upgrading, improved, locked, a station, population, plan special steps, capital steps, contract feeders, and industries with defense, fleet, quality or ship output.
- Stations (CAStations, 0.2.42.0): Setting Station at every colony under Building and planning, Yes by default. A colony out of its fast buildout at size 4 or more (or 180 days old) gets a station plan step.
- Stations (CAStations, 0.2.42.0): Tier-2 from size 6 and tier-3 from size 8 (5 and 7 at the capital). The tier follows the colony's own station, else the Station line setting, else low tech.
- Capital (CACapital, 0.2.42.0): One colony is the player's home port. Settings are under Settings, Capital.
- Capital (CACapital, 0.2.42.0): It is picked once and kept. Score: the production gathering point 1,000; Storage contents up to 300 for mothballed ships and 200 for stacks; size x10; accessibility x20; free industry slots x5; Auto +20.
- Capital (CACapital, 0.2.42.0): Make this the capital on a colony panel, the Settings row, or moving the gathering point picks another.
- Capital plan steps: Once the colonies ramp up (no fast buildout at the capital and last month's net income past Commerce starts, 100,000 by default), the plan gets steps in front: Spaceport, Megaport, Commerce, Trade Outpost, Bazaar, Requisitions Centre, Union HQ, Salvage Yards, Underworld, Waystation. Each is added only if the game has it.
- Capital plan steps: They count as enablers and wait while net income is below zero. Yunru's Bazaar is scored only at the capital.
- Capital, Make room for trade buildings: With every slot taken, the capital takes down its weakest valued industry for one trade step, one every 90 days. Defenses, fleets, ship quality, ship output, mod buildings, locked, improved and other plans' special steps are excluded.
- Storage logistics (CALogistics, 0.2.39.0): Setting under Settings, Storage logistics. Off until switched on. Storage tabs are gathered in the refit hub (default: the production gathering point) and the optional sell hub.
- Refit hub: Gets from every Storage at a market not hostile to the player: mothballed ships (setting), weapons, fighter wings, unknown blueprints and hull mod specs (one copy each), and supplies, fuel, crew and marines (setting). Sell hub: Gets known blueprints and hull mod specs and spare copies of unknown ones.
- Storage logistics loads: Weekly, one load from each Storage to each hub, paid from the monthly budget (the Autopilot Storage shipment fee plus 1,000 a ship). Arrival is 2 days plus a day a light-year.
- Storage logistics loads: A load the budget cannot pay for waits. Colony items, AI cores and Autopilot Storage are not touched.
- Autopilot Storage (per colony since 0.2.37.0): A tab at every colony market holding that colony's own store. Colony items, AI cores, Antimatter Stabilizers, research databanks, VPC chips and recipes deposited there are the only ones the manager uses.
- Autopilot Storage use: The manager uses an item at the colony it is stored at. An item needed at another colony is shipped there first.
- Autopilot Storage use: Shipping costs 2,000 plus 300 a light-year, times the convenience fee level, from the monthly budget, and takes 2 days plus a day a light-year. Only one core is on its way to a colony at a time.
- Placement (0.2.39.0, `CAItems.placements`/`assign`): Every waiting item is matched with every free building it fits and the best-scoring pairs go first. Each building gets one item.
- Placement (0.2.39.0, `CAItems.placements`/`assign`): The copy already at that colony wins ties. A building whose item is being shipped is held for it.
- Placement (0.2.39.0, `CAItems.placements`/`assign`): VPC chips (`CAIndEvo.installVPC`) are placed the same way.

## 0.3.0.0

- Save compatibility: A save made on 0.3.0 does not load in 0.2.45.x.

### Foundation

- Steps (`CAStep`): Every daily and weekly step of `CAAuto.runPass` runs through `CAStep.run` (isolated, timed into `CAPerf`, stopped for the session after 3 failures in a row) or `CAStep.guard` (isolated and not stopped; covers the 0-stability safety net, siege check, emergency defense and the Nexerelin steps).
- Migration (`CAMigrate`, setting `settingsVersion`, data `schema`, `migratedAt`): Moves a save from before 0.3.0 forward once. War fleets 0/1/2 become War level Retaliate/Defend/Off.
- Migration (`CAMigrate`, setting `settingsVersion`, data `schema`, `migratedAt`): Faction priorities High/Normal/Ignore become Friendly/Auto/Rival. Retake is off.
- Migration (`CAMigrate`, setting `settingsVersion`, data `schema`, `migratedAt`): Nothing is raised above 0.2.45. The save is stamped before the steps run, so a failure is not retried.
- Migration (`CAMigrate`, setting `settingsVersion`, data `schema`, `migratedAt`): A What's new box shows on the Settings page for 30 days.

### Planner

- Value service (`CAValue`, `JTValueCheck`): `add` values a new build or upgrade (`CAEngine.effects` at the colony's weights, endgame chains, confidence). `keep` values an existing building (its live lines above the colony's other sources, the shortage it would leave, its upkeep, the VoK synergies it keeps).
- Value service (`CAValue`, `JTValueCheck`): `deficits` counts shortages with builds under way at the supply level they will reach. `boost` values more or fewer units on a line.
- Value service (`CAValue`, `JTValueCheck`): `npv` is the net present value.
- Horizon (`CAHorizon`, setting `horizonMonths`, hidden, 36): score = worth - (price + fee) / H - worth x days / 30 / H. H slides from 36 to 120 months (`CATuning.HORIZON_MAX`) as surplus credits reach 10,000,000. It falls only when they drop under 60% of what holds it.
- One queue (`CAPlanner`, `CAAction`): Covers every build, upgrade, surplus terraforming project, extra Research Facility, wonder site, stable-location crew and story point improvement.
- Queue per colony: New buildings go through the colony's one construction queue by NPV per construction day. A lesser one starts only when it finishes before the better one could start. Upgrades and TME projects run beside the queue, at most one on a building.
- Hidden switch `legacyPlanner`: In the saved settings. Console: `data.scripts.jonahtweaks.autopilot.CAData.settings().legacyPlanner = true;`.
- Hidden switch `legacyPlanner`: It runs the 0.2.45 build pass (`CAAuto.buildPass`). A planner that failed 3 days in a row also does, for the rest of the session.
- Hidden switch `legacyPlanner`: Checks: `JTPlannerCheck`, `JTPlannerSimCheck`.

### Money ladder

- Rung presets (`richPreset`): Balanced, Fleet, Expansion, Economy, Save. Rungs: Stockpiles, Growth, Military, Shipyard, Expansion, Economy, Wonders. The month's pool is F x 20% / 35% / 60% / 80% (T1-T4). A rung that is Off, below its opening tier or left out of the preset passes its share down.
- Money tiers (`CARich`, data `richTier`, `richTierSince`, `colonyNetHistory`, `idleMonths`, `rungSpent`): Worked out once a game day. `JTRichCheck`. Log code TIER.
- Free cash F: Credits - reserve - production still to pay - 2 months of colony upkeep and fleet costs (`CABudget.freeCash`). Colony net N: The 3-month median of colony net income (`CABudget.colonyNet`).
- Tiers: T0 Tight (base). T1 Surplus: F >= max(1M, 2N).
- Tiers: T2 Idle money: F >= max(3M, 5N), or T1 and 2 idle months. T3 Rich: F >= max(10M, 15N), or T2 by free cash and 4 idle months.
- Tiers: T4 Endgame: T3 and N at the endgame setting's line. A tier exits at 60% of the line that started it (a tier reached by idle months, 60% of the line below).
- T2 effects: A credit counts half in what a build is worth at Approve and Auto colonies (`CASurplus.moneyWeight`). The horizon slides on free cash.
- Rung shares: Rungs run in the preset's order, each up to its share (10, 20, 20, 25, 15, 10%; Wonders get what is left) plus what came down. A rung that is Off, below its opening tier, left out of the preset, or wants less passes its share down.
- Rung shares: Opening tiers: Stockpiles, Growth, Economy from T1; Military, Shipyard, Expansion from T2; Wonders from T3.
- Rung modes: On / Recommend only / Off. Default: Shipyard and Expansion Recommend only, the rest On.
- Shipyard (`CAShipyard`, settings `shipyardStop` 6, `shipyardFee` 0.02; data `shipyardOrders`): Active from T2. It adds Doctrine priority hulls the player knows (no stations or modules), fewest on hand first, to custom production with `FactionProductionAPI.addItem`. The game charges at month end and delivers to the gathering point's Storage. Fee 2% at order.
- Shipyard limits: The rung's room, the month's production capacity after what is queued, free cash, and 6 unclaimed hulls Auto Colonies ordered. One order a month.
- Shipyard limits: `guard` removes Auto Colonies' hulls from the list (`removeItem`) and refunds the fee while free cash is under 0. Log code SHIPYARD.
- Shipyard limits: `JTShipyardCheck`.
- Stockpiles (`CAStockpile`, data `stockpiles`): Active from T1, at Auto colonies (not outposts or under siege). A good is bought when Local Resources plus what is on the way covers under 1 month of draw, up to 3 months, and the shortage's cost over those months beats price, tariff and fee.
- Economy rung (`CAEconomy`): Surplus research, surplus terraforming and the capital's trade buildings at a loss start something only from T1 with the rung On (`buyerOpen`). They spend surplus credits and the month's budget outside the pool.
- Growth rung (`CAPolicies`): Hazard pay counts against the rung's 20%. While On, the budget is max(setting's budget, min(tier's share of colony net, the rung's room)) (`raisedCap`). Tier share: T1 40%, T2 60%, T3-T4 no tier cap. Spending freely has no cap.
- Spending freely (`CASurplus.rich`, `CAEndgame.decideTier`): Needs T3 or T4 and credits over half the surplus line (1,500,000 over 3,000,000). It stays on while the tier holds and those credits stay over a fifth of the line (600,000).
- Spending freely (`CASurplus.rich`, `CAEndgame.decideTier`): It is off with focus-world-only, surplus investment Off, or no credits over the line.
- Endgame: Starts at T4 while spending freely. Stays on while spending freely with colony net >= 0. Wonders and the endgame's replacements need the Wonders rung On, T3, and the Balanced or Economy preset.

### Expansion

- Auto-expansion (`CAExpansion`, no Nexerelin imports): Settings `expandMode` Off/Ask first/Auto (default Off, every save), `expandLine`, `expandScope`, `expandGoal`, `expandMethod`, `expandMax`, `noExpandBuildout`, `noExpandAdmins`, `expandConfirmed`. Data: `expandOrders`, `expandSkip`, `expandFounded`, `expandLast`, `expandPending*`.
- Auto-expansion schedule: Daily after the money tiers: deliveries, administrators seated, then one expedition (Auto) or proposal (Ask first) when every gate holds.
- Expansion gates: An Auto colony. Spending not paused. No siege, imminent colony crisis or Nexerelin invasion on its way. The newest founded colony is out of fast buildout or 120 days old. One expedition under way, two at T4; 120 days apart at T2, 60 at T3.
- Expansion gate, money: With the money ladder, T2 or higher from the Expansion rung (Off stops expansion; Recommend only makes Auto ask first). With a fixed line, 10% of the credits over the surplus line once they reach it.
- Expansion gate, administrator: Colony-limit room or an administrator (idle, on the way, or hired at launch for 250,000 plus the hiring bonus). A new colony the player governs may not take another under the floor (`CAStability.canGovernedLose`).
- Expansion targets: Fully surveyed unclaimed planets in systems the player holds; then within 6 ly of a colony; or within 15 ly of the capital (with Low profile, systems the player holds). Vanilla's founding rules and Nexerelin's colony rules apply.
- Expansion targets: Excluded: Remnant nexus systems, claimed or foreign systems, unexplored ruins, mission sites and the RAT settlement.

### War and diplomacy (Nexerelin)

- Stance War level cap: Defend for Ally/Friendly/Neutral, Retaliate for Auto, Hold them down for Rival, Conquer for Enemy, Scorched for Destroy. Enemy and Destroy act only on a hostile faction, and as Rival until War page Declare war.
- Stances (`CAStance`, setting `factionStance`): Ally, Friendly, Neutral, Auto, Rival, Enemy, Destroy. Each caps the War level used on that faction. Ally, Friendly and Neutral are raised in relations. Rival and up are not. `JTStanceCheck`.
- War level (`CAWarPolicy`, setting `warLevel`, default 2 Retaliate): Off; Defend; Retaliate; Hold them down; Conquer; Scorched. Each level adds to the one before.
- War level Defend: Relief, proactive defense, pirate and Pather base strikes, standing guard while rich. Retaliate: Adds strike back and retake (setting `noWarRetake` inverted, on for new games, off for 0.2.45 saves).
- War level Defend: Hold them down: Adds raids on military markets and blockades of Rival, Enemy and Destroy markets within 15 ly (any while rich), 2 per faction, 150 days between blockades of one market. Conquer: Adds invasions.
- War level Defend: Scorched: Adds saturation bombardment once authorized (`warScorchedOK`).
- War fee: On retake, raids, blockades, invasions and bombardment, the fee level x 10% of Nexerelin's price (`FEE_SHARE`). Payers (`payer`): Emergencies and relief from the budget above the reserve.
- War fee: Defense and guard from the Military rung, the surplus while rich, else the budget. Invasions from the surplus or a budget holding twice the cost.
- War fee: Bombardment from the surplus.
- War fleets (`CAWar`): Once a day, one launch counter, in order: relief, proactive defense, retake, base strikes, strike back, respawn watch (Destroy), raids and blockades, Conquer, Scorched, standing guard (rich). `CAWar.run` does not check colony modes: relief and defense go to every colony not Off.
- Scorched (`CAScorched`): A player-faction `SatBombIntel` from the surplus only. It has not been run in a game yet. Fallback: invade capitals.
- Diplomacy (`CADiplomacy`): Daily after the agents. It answers Nexerelin's `CeasefirePromptIntel` offers by stance. `declareWar` runs only from the War page's confirmation, one in 30 days. Log code DIPLO.
- Conquest (`CAConquest`; data `conquered`): Invasions as Nexerelin's fleet request (`InvasionIntel`, fleet points x 400 + marines x 50, plus the fee) at 1.25x Nexerelin's fleet and marines. Targets: Retake (a market that was the player's, the lost capital first), enemy markets when one costs at most a quarter of the surplus, every Destroy market (capital last).
- Conquest (`CAConquest`; data `conquered`): One under way (two while rich).

### Menu

- Presets (`CAPresets`): Spending (Thrifty / Normal / Lavish), Extras, New colonies, War, Story points, Stations, Take down buildings, Colony policies, AI cores, Administrators.
- Spending preset: Budget 30/50/80% of net income and 5/15/30% of savings. New upkeep 5/10/20% with floors 10,000/20,000/40,000. Automatic per-project cap 0.5x/1x/2x net income within 500k-2.5M / 1M-5M / 2M-10M. Thrifty sets surplus terraforming to Pays back in 10 years unless Off.
- New colonies preset: Auto also sets TASC stations to Auto. `tascMode` keeps them on Recommend only while Auto asks first.
- Settings page (`CASetupUI`): Groups: Playstyle (Expansion style, Spending, Extras, New colonies, War). Money (Credit reserve, Surplus investment, What to buy, Endgame, Story points).
- Settings page (`CASetupUI`): Building (Stations, Take down buildings, Colony policies). Items and people (AI cores, Administrators, Capital, Storage logistics).
- Settings page (`CASetupUI`): Security and diplomacy (Agents, Ambassadors, Faction stances link). Interface (On-screen log).
- War page (`CAWarPage`, with Nexerelin): Level, fleet request points, infamy, money, Stand down, Recall all, Authorize bombardment with confirmations, a block per faction at Rival or higher with up to 3 planned operations, conquered colonies.

## Any mix of mods

- Fallbacks: Every part has a fallback. The manager works with any combination of the supported mods, or none.

| Part | With the mod | Without it |
|---|---|---|
| Colony plans (Terraforming Made Easy) | A reachable special world fitting the slots at max size. TME's structures and the Unification Center get the plan's priority | A plain plan for the goal (Forge, Fortress, Paradise, Bastion, Fuel Depot, Mining Colony, Trade Hub), vanilla buildings plus hazard clean-up |
| Plan steps | Any step whose building or condition the game has | A step from a mod that is not installed is dropped and takes no plan rank |
| Research locks (Ashes of the Domain, Building Menu Overhaul) | Locked steps are worked toward through research | Every vanilla building is open. Nothing waits on research |
| Research (Vaults of Knowledge) | Techs ranked by what colony plans need, then by what they unlock. A Research Facility is built when plans wait on research and the player has none (0.2.39.0, `CAResearch.stepsFor`). Alpha cores into research buildings first | Skipped |
| Terraforming (TME, Boggled's TASC) | Projects scored by what they add or remove, plan conditions first | Skipped. Hazard clean-up steps drop out with their buildings |
| Growth | Hazard pay for the colonies it speeds up most, within a share of income | Same (vanilla) |
| Values | Formulas for vanilla buildings, estimates for known mod buildings, probe-colony measurements for the rest | A building that cannot be valued is listed in Diagnostics and is not built by Auto |

- Plan blocks: Whatever a colony's plan is blocked on, the colony builds whatever is worth most on its own. The plan only adds priority.
- Unreachable plan step (0.2.38.0): A step with no way to build it, and no research Autopilot is running that would open it, stops counting after 60 days. It gets no plan rank and no reserved industry slot. It counts again the day a way opens.
- Unreachable TME world: A world whose own buildings are out of reach is dropped for a year. A world needing Mining or Farming is not picked where they cannot go.
- Building Menu Overhaul unreadable: Only what research would lock stays locked.

## Rules it follows

- Worthwhile builds: Approve and Auto start a build only when its effects, valued with the colony goal's weights, beat its build cost spread over the payback horizon (36 to 120 months). A building with nothing to do (a Research Facility with no research) is not built.
- Worthwhile builds: The colony panel shows each option's money per month after upkeep and marks options not worth building. See "0.3.0.0, Planner".
- Nexerelin outposts: Nothing is built, upgraded or placed at forward bases.
- Budget: Spending stays within the monthly budget, the per-project cap and the credit reserve. Emergency defense fleets may pass the monthly budget and not the reserve.
- Budget (0.3.0.0): The Spending preset sets the shares: Thrifty 30%/5%, Normal 50%/15%, Lavish 80%/30%. Net income is the 3-month median of colony net (`CABudget.colonyNet`). The per-project cap is Automatic by default (half, all or twice net income by preset).
- Budget types: Automatic (recommended), Share of net income, Fixed amount. A young colony that loses money still gets built, and spending slows as savings fall.
- Saved budget (0.2.39.0): The unspent part of a month's budget is saved, up to six months' worth. A building that costs more than one month's budget is built once enough is saved. Emergencies over budget come out of the saved amount.
- Reserve: Recommended reserve is a quarter of the credits at setup, 50,000-500,000. Pause on loss is recommended off.
- New upkeep cap per month: Limits the new monthly upkeep Auto takes on in a month. Automatic is the default (0.2.24.0). Fixed is an amount you set. Since 0.3.0.0 the Spending preset sets it to 5%, 10% or 20% of net income, counting only upkeep a building does not cover.
- New upkeep cap per month: A plan step whose upkeep alone is over the cap (a Terraforming Made Easy structure at a big colony) goes as the month's first new upkeep. It waits a month at most (0.2.21.0).
- Surplus investment: Setting, recommended over 3,000,000. Credits over the line go into one focus world, past the monthly budget and the per-project cap.
- Surplus investment: Its new upkeep cap is 10% of net income. Its builds and terraforming count their price at a quarter.
- Surplus investment: Research counts its plan twice and facility upkeep at a quarter.
- Focus world: Autopilot picks the Auto colony with the most to build on (size, free industry slots, deposits, habitable, hazard, waiting colony items, income, a reachable special world) and keeps it unless another is clearly better. Make this the focus world on the colony panel's Overview sets it by hand.
- Spending freely (0.2.32.0): Setting. Options: Every colony spends it (recommended), Focus world only. Since 0.3.0.0 it starts at the money tier Rich with half the surplus line over it and ends under a fifth of the line. A weak month does not stop it (0.2.34.1). See "0.3.0.0, Money ladder".
- Spending freely: Every Approve and Auto colony spends from the surplus, past the monthly budget and the per-project cap. The month's new upkeep may reach a sixtieth of the surplus. A credit counts a quarter in what a building or upgrade is worth.
- Endgame economy (0.2.36.0): Money stops being the goal at every colony that spends the surplus. Since 0.3.0.0 it starts at the money tier Endgame and stays on while colony net is 0 or more. See "0.3.0.0, Money ladder".
- Endgame economy: Setting Endgame replaces industries, default On. Replacements need the Wonders rung. A credit counts 5%. Stability, access, defense, fleets, ship quality and growth count at least 4,000 / 10,000 / 15,000 / 40,000 / 40,000 / 2,000, or the colony goal's own weight if higher.
- Endgame economy, goods: A good none of the player's colonies make is worth 40,000 plus 4,000 a unit. Units that cover the player's shortfall are worth 3,000 each.
- Endgame economy, goods: The rest are worth 1,000 a unit times the share of the sector's production that is not the player's. A second source adds 10,000 (since 0.3.0.0 only for war goods or a threatened single producer).
- Endgame economy, goods: War goods count 1.5x, AotD's advanced goods 2x.
- Endgame economy, replacing: With every industry slot taken, an Auto colony takes down its weakest industry when the best one it has no slot for is worth 10,000 a month more, then builds that one. One replacement a day across the empire, one a month per colony.
- Endgame economy, replacing: A building taken down is not built there again for a year. Its AI core and item go to Autopilot Storage.
- Endgame economy, replacing: Never replaced: military industries, plan, project, research and wonder buildings, story-point improvements, locked buildings, anything built in the last year, a good's last producer while the colonies would be short, and a building making a good a trade contract needs (0.2.39.0, `CAContracts.feeds`).
- Going all out (0.2.33.0): A build's price counts 5%. Every colony maxes its structures.
- Going all out (0.2.33.0): Its station goes to the top of its line (up to a star fortress). Heavy Batteries, a Planetary Shield, a Megaport and a Patrol HQ are first plan steps, built even when they earn less than they cost.
- Going all out (0.2.33.0): Colonies re-plan daily and start up to three upgrades at once.
- Surplus terraforming: Terraforming Made Easy setting, recommended Max output. Max output funds anything worth 1,000 a month or more. Pays back in 10 years funds only what earns its price back by then. Off funds nothing.
- Surplus terraforming (0.3.0.0): Goes through the planner's queue and the Economy rung's gate (`CAEconomy`). It may use the month's budget for a sequence that pays back within 24 months.
- Surplus terraforming: What the focus world leaves of the surplus goes into TME projects at every Auto colony, most value per credit first. Targets are richer deposits, farmland and harsh conditions cleared.
- Surplus terraforming: The structure is built first when the colony has none. Plans look one or two projects ahead, so an enabler (tectonic activity before richer transplutonics) counts with what it allows.
- Surplus terraforming: A project that loses more than it gains does not start. Nothing the colony's plan needs is undone and nothing it wants gone is added.
- Surplus terraforming: A structure it built is taken down once nothing worth doing is left for it. Structures locked by research (Mineral Replicator, Element Synthesizer, Agricultural Laboratory, Terrestrial Engine with Vaults of Knowledge), the Stellar Manufactory and the Unification Center are not built.
- Surplus research: Vaults of Knowledge setting, recommended On. Since 0.3.0.0 it goes through the planner's queue and `CAEconomy`'s gate. Full upkeep counts below Idle money, half from Idle money. At most 3 of its own are built below Rich.
- Surplus research: Builds Research Facilities at Auto colonies with no research building, one at a time, up to the 8 colonies VoK counts (each past the first is +10% research speed). Only while the speed gain on the ranked techs beats a quarter of the new facility's upkeep (10,000 a month plus 10,000 x the running tech's tier; half with an Alpha core).
- Story points: Player only by default. Options: one improvement per colony, or a monthly cap on story points spent. Each improvement at a colony costs twice the last one there (2, 4, 8, 16...).
- Terraforming: Hazards a project adds are scored, and side effects (TME removes a new condition's conflicts) are simulated. A project that is a step of the colony's plan, the special world's final project included, goes past the per-project cap and the monthly terraforming budget.
- Terraforming: It is paid from the credits above the reserve and waits (logged) until available.
- Terraforming, what to leave alone (0.2.30.0): Shared with surplus terraforming. Nothing removes a condition the colony's plan needs.
- Terraforming, what to leave alone (0.2.30.0): Nothing adds what the plan wants gone: its removal steps, what the target world hates and, on a plain plan, the hazards it clears (toxic or dense atmosphere, radiation, pollution, hostile biosphere, high gravity, and extreme tectonic activity except on a Forge).
- Colony items: Each item in Autopilot Storage goes into the Auto colony building where it scores best, one a day with the standard fee. With no empty building, an item swaps in for an item at least a quarter less valuable in the same kind of building.
- Colony items: An item no Auto colony has a building for makes a new building that would take it worth that much more.
- Polluting items (0.2.36.2): A polluting item (vanilla's Corrupted and Pristine Nanoforges, anything not tagged no_pollution) does not go into a Heavy Industry or any forge on a habitable world, for every colony goal, threat and the endgame. It waits for a forge on a world that is not habitable.
- AI cores: One switch, on in the recommended settings. Only cores the player puts in Autopilot Storage are used.
- AI cores: Each core goes where it adds most for the colony's goal, valued the way buildings are. A better core replaces a lesser one.
- AI cores: A core moves to another building only for 3,000 a month more. One change a day, with the standard fee.
- AI cores, value: Vanilla gives every core -1 demand. Alpha and Beta give -25% upkeep. An Alpha gives +1 to each output, or in a structure its own bonus (fleet size, station strength, ground defense, accessibility, Commerce income, growth).
- AI cores, cost: The colony crisis progress the core adds is subtracted. The Hegemony counts every core at a colony above size 3 (Alpha 4, Beta 2, Gamma 1; size 3 or less is free). Pather interest from cores is the same 4/2/1 and gives a colony of size 4 or more a Pather cell once it reaches 7.
- Research buildings (Vaults of Knowledge): A core in a Research Facility or Black Site cuts the building's own costs by half (Alpha), a quarter (Beta) or a tenth (Gamma). An Alpha also adds a research databank a month on a world with a pre-collapse facility.
- Alpha cores in research buildings: Setting, recommended First. First puts Alpha cores there before anywhere else. By value weighs them like any other building. Only where removable puts them in research buildings.
- Inspection risk: Setting. The Hegemony inspects a colony only with more than 7 inspection points.
- Inspection risk: Never inspected (recommended, 0.2.14.0) keeps every colony at 7 or less. Small colonies only keeps cores out of colonies above size 3.
- Inspection risk: More where defended allows more where the defenses beat the next inspection by 1.5 times (2 times under high threat) and pays only with Hegemony inspection orders on Resist where defended. No limit ignores inspections.
- Hegemony inspection orders: Setting. The game's default is to comply, which hands over every core at that colony.
- Hegemony inspection orders: Advise (recommended) logs each inspection coming for an Auto colony with its fleet points and the colony's defenses. Resist where defended resists where defenses beat the inspection by the margin above.
- Hegemony inspection orders: Three beaten inspections (150, 600, then 1,400 fleet points) end them. Off does nothing.
- Administrators: Hired, placed or moved only where they add more than they cost. Hiring is on in the recommended settings.
- Administrators: Value at a colony is Industrial Planning's +1 to everything its industries make, plus the stability from the colony limit (each colony past the limit costs every colony 2 stability; stability is worth money only below 5, 20% of income a point).
- Administrators, cost: Salary is vanilla's 2,500 a month unskilled, 20,000 with a skill, 10% while unassigned. When hiring, the hiring cost spread over three years is added.
- Administrators, cost: An idle administrator worth less than their salary stays unassigned and the log says so. One is moved to another Auto colony for at least 5,000 a month (or a quarter of their salary) more.
- Administrators, colony limit: While the player governs more colonies than the limit personally, an idle administrator is placed or one is hired regardless of the money. That hire may go past the monthly budget and not below the credit reserve.
- Administrators, hiring from another market's comm directory (0.2.37.0): The hire takes 30 days to arrive.
- Recruiter (0.2.41.2): Past the colony limit with none on offer anywhere for 90 days, a recruiter finds a basic administrator (no skills, 2,500 a month) for 250,000 on top of the 5,000 hiring bonus and the fee. Arrival takes 30 days.
- Recruiter (0.2.41.2): The credit reserve is not spent. One a day until enough are on the way.
- Siege (0.3.0.0): A colony is under siege until it ends (checked daily) when it is blockaded, has hostile fleets at the planet, has hostiles in its system that outweigh its defenses, or has a hostile raid, invasion or blockade on its way. Autopilot builds only defenses there.
- Siege (0.3.0.0): Items, AI cores, administrators, policies, station augments and terraforming stay as they are. Hazard pay and free port still switch off in an emergency.
- Colony crises: The game's crisis meter (Hostile Activity) gains every faction's progress each month, at most 60, times the player's best military structure (Patrol HQ x0.9, Military Base x0.7, High Command x0.5). At 500 the next crisis is picked. At 600 it fires and the meter falls back to 0-400.
- Colony crises, pricing: Autopilot prices progress at about 640 a month per point. AI cores pay for the Hegemony progress and Pather interest they add. A military structure that would become the best counts what it slows. Fuel Production counts the Sindrian Diktat progress it adds.
- Colony crises, target colony: The colony the coming crisis is likely to hit plans with Security until it is over (stations, patrols, ground defense). Raids that meet in-system defenses about 1.34 times their strength while the player is away abort, which ends pirate, Pather, Diktat and Tri-Tachyon crises for good.
- Hazard pay (0.3.0.0): The budget comes from the 3-month median of colony net. The money ladder's Growth rung can raise it. It adds a colony's hazard penalty back plus 5 growth points a month, at 1,000 credits a point.
- Hazard pay: Auto colonies are ranked by how much faster it makes them grow. The best get it while it speeds them up by at least Hazard pay where growth is at least (25%).
- Hazard pay: All of it fits within Hazard pay limit, all colonies (30% of net income). It goes off in a money emergency, at max size, or when the budget goes to colonies it speeds up more.
- Threat response: A colony is planned as Security once its threat estimate reaches 70%. It returns to its own goal after the estimate falls below 65% and 60 days pass.
- Threat response: The estimate uses hostile factions nearby (at most 60% on their own), the Hostile Activity crisis, and raid, blockade, Pather cell and rebellion conditions.
- Agents (Nexerelin, 0.3.0.0): Relations and offense follow the faction stances and the War level (see "0.3.0.0, War and diplomacy"). Setting Agents: Off before setup, Defense (recommended), Defense and offense. Each agent gets only operations its type can run (Nexerelin's agentConfig).
- Agents, Offense (Defense and offense only): Sabotage Industry on a military industry of the Offense target. Options: Whoever attacks you (default), A random enemy, or a hostile faction the player picks.
- Agents, Offense (Defense and offense only): Requires 60 percent estimated success or better, alert 0.2 or lower, one operation per faction. Allies, a ceasefire and the commissioner are excluded.
- Agents, Recruiting (on unless turned off): Costs 12,000 plus the convenience fee. The agent reports 60 days later at the player's biggest colony.
- Agents, Recruiting (on unless turned off): A Hybrid for security first, a Negotiator for relations and a Saboteur for offense once the month's budget has twice the cost to spare. Up to Agents to keep (Auto: 2, or 3 with offense) and Nexerelin's agent limit.
- Agents: Only agents Autopilot recruited, or ones the player hands over with Let Autopilot use it on the Mod features page, get orders. Costs come out of the monthly budget and not the credit reserve. Only person ids are saved, so the save loads without Nexerelin.
- Autonomy (Nexerelin): Setting: Off before setup, Recommend (recommended), Auto (0.2.34.3). An autonomous colony gives the player 25% of its profit or loss, does not count against the colony limit and cannot be built on. Taking it back costs 2 unrest.
- Autonomy: Used only for money, and not for a colony under a year old. Candidates are a colony that lost money 3 months in a row, or the one that saves the most in a money emergency. Recommend logs it once. Auto acts, one every 30 days at most, and outside an emergency not while spending freely.
- War fleets (Nexerelin, 0.2.35.0): Since 0.3.0.0 the setting is the War level (see "0.3.0.0, War and diplomacy"). The rules below are the Defend and Retaliate levels.
- War fleets (Nexerelin, 0.2.35.0): Requests are made as Nexerelin's fleet request dialog makes them, for security only. An aggressive fleet goes only against a faction already hostile to the player.
- War fleets (Nexerelin, 0.2.35.0): Checks run daily, after the emergency defense fleet.
- War fleets: Relief fleet to a colony under siege, one per colony every 60 days, at twice the colony's stabilize package. Defense fleet to a colony whose threat is high or that a crisis targets.
- War fleets: Base strikes on pirate and Pather bases within 15 light-years of a colony, at twice their station's fleet points plus 100 (at least 200). Raids (Retaliate) on markets that raided or blockaded the player in the last 120 days while hostile.
- War fleets, limits: Normally one a day, keeping 40% of Nexerelin's fleet request points for emergencies. Relief and defense may go over the monthly budget. Strikes and raids come out of the budget or the surplus. Nothing goes below the credit reserve.
- War fleets, Spending freely: Up to 3 a day, keeping 10% of the points, fleets 1.5 times bigger, every known base at any distance, and a standing Defense fleet at every colony without one. All are paid from the surplus.
- Ashes of the Domain: Research locks and upgrade paths live in Building Menu Overhaul's build menu, so the mod asks it the same questions. Nothing locked or hidden is built or upgraded.
- Ashes of the Domain: If those rules cannot be read, nothing is built or upgraded while Building Menu Overhaul is enabled, and the log says why. Locked plan steps show "(locked: ...)".
- Ashes of the Domain, upgrades: Buildings AotD reaches by upgrading (Farming from Monoculture Plots, Orbital Works from Heavy Industry, Megaport from Spaceport) are offered as upgrades. A structure that upgrades into an industry needs a free industry slot.
- Ashes of the Domain, starter buildings: Supported with estimated effects: Monoculture Plots, Extractive Operation, Smelting, Light Production, Trade Outpost. A plan step such as Farming credits the starter it upgrades from. AotD versions are valued like any building that makes things.
- Unification Center (0.2.39.1, `CAPlan.held`): Waits for the rest of its world. It gets no plan rank until every step of the colony's own plan before it is done, out of reach, or a condition a running TME project is making (`CATerraform.producing`).
- Unification Center (0.2.39.1, `CAPlan.held`): Buildings it checks by exact id are not upgraded into AotD versions while that plan is open.
- Industry synergies (Ashes of the Domain, Vaults of Knowledge): Building combinations on one colony (Ore-To-Core: Refining with Mining; Defensive Network: star fortress, Heavy Batteries and High Command; Rapid Transportation: Spaceport with Waystation; and 16 more). A build's value includes the synergies it starts or ends.
- Industry synergies (Ashes of the Domain, Vaults of Knowledge): Effects are modelled from VoK 5.0.7's code. Only synergies researched at that colony count.
- Industry synergies (Ashes of the Domain, Vaults of Knowledge): The colony panel shows its efficiency, active synergies and those one building away.
- Trade contracts (Ashes of the Domain, Theory of Toolbox): Monthly deliveries from the player's surplus, paid at 2-5x the export cut. Three missed months end a contract and cost merchant renown. Modes: Advisor (before setup), Auto (recommended), Off.
- Trade contracts: An offer is taken only when ToT's own figures leave 15% to spare and it pays more than exporting. Up to 3 per day in Auto, best gain first.
- Trade contracts: Advisor gives each fillable offer an Accept button. ToT delivers only from colonies with a spaceport and accessibility above 0.
- Trade contracts: A blockade takes up to 60% off.
- Coronal Hypershunt colony (Ashes of the Domain, Vaults of Knowledge): AotD caps it at size 2 and allows no buildings. Nothing is built, upgraded, placed or switched there.
- Coronal Hypershunt, range: The Coronal Wormhole's range costs 20,000 credits and 2 purified transplutonics a month per 10 light-years past the first. Autopilot keeps it at the smallest range that reaches every Hypershunt Receiver in a system with one of the player's colonies.
- Coronal Hypershunt, range: A range the player sets is left alone until "Let Autopilot manage it" is clicked.
- Coronal Hypershunt, restoration: Each section is restored through a contract the player issues, taking what the exports can spare and billing 10% of base price. The Coronal Collector comes first, once Hypershunt Energy Distribution is researched.
- Coronal Hypershunt, restoration: The Coronal Wormhole follows when a receiver serving the player's colonies is past 10 light-years. Auto starts one only when every resource is in surplus, the cost fits the per-project cap, the first month fits the budget and the upkeep fits the new-upkeep cap.
- Coronal Hypershunt: Setting (under Ashes of the Domain). Options: Follow colony mode (default), Recommend only, Off. Receivers are built in person. The empire panel lists systems where one would pay off.
- Pluto Mining Station and Nidavelir Complex (Vaults of Knowledge): Claimed by colonizing their planets. They use the Megastructures setting and the hypershunt's restoration gates.
- Pluto: The laser runs at full strength. Ore goes to a restored forge: the Purification Core, except what the Fusion Forge needs for the player's metals and transplutonics shortages. Auto restores the Optic Command Nexus, then the Purification Core, then the Fusion Forge.
- Nidavelir: Manpower goes into the Nexus Core one point at a time while its four inputs are fully supplied, with a 30-day wait before adding again and a cap of 20. Auto restores the Nexus Core. Other sections are left to the player.
- Research (Vaults of Knowledge): Modes: Advisor (before setup), Auto (recommended), Off.
- Research, tech value: Each tech the player can start is valued by what it unlocks. Buildings are scored by the build engine with the candidate techs marked researched for one pass.
- Research, tech value: Non-building effects come from VoK's code (+1 output, hazard -10%, upkeep x0.9, hypershunt access). Half the best tech each one leads to is added.
- Research, ranking: Ranked by a year of that value, less databanks and the facility upkeep it adds, per research day. The Autopilot Storage page lists what each colony's plan waits on and the best six techs with a Start button.
- Research, Auto start: Auto starts the best tech when nothing is running or queued. While the player has research queued it waits.
- Research, costs: Auto picks techs that cost databanks and AI cores. The cores come from Autopilot Storage and count at 2,500/7,500/20,000 for Gamma/Beta/Alpha. Techs that use colony items are left to the player.
- Research spends colony items (0.2.36.1): Setting under Ashes of the Domain, Off by default. Applies when credits are over the surplus line.
- Research spends colony items (0.2.36.1): From storage takes items from Autopilot Storage and the colonies' Storage tabs. From storage and buildings also takes them out of buildings (never a locked one).
- Research, storage: When a tech starts, what the Research Facility tab is short of (databanks, an Alpha core, colony items) is moved in from Autopilot Storage, then databanks from the colonies' Storage tabs, then the fleet's cargo. Nothing moves unless that covers the whole shortfall.
- Research, storage: When nothing can start, the log says why.
- Special project supply (Vaults of Knowledge): Setting under Items and AI cores. Options: Auto (recommended), Warn only, Off. A black-site project takes goods every month from the player's own colonies' production (a Theory of Toolbox contract). A stage stops while any is not delivered at all.
- Special project supply, AotD goods: Made only by VoK's upgraded industries. Electronic Components: High-tech Industry (from Light Industry).
- Special project supply, AotD goods: Domain-grade machinery: Orbital Skunkworks Facility (from Orbital Works at size 6+) or Civilian Heavy Production (from Heavy Industry). Refined Metal: Crystalizator (from Refining).
- Special project supply, AotD goods: Enriched Transplutonics: Enrichment Facility (from Refining).
- Special project supply, producers: For the project being run, every good no colony of the player's makes gets its producer as a first plan step at the Approve or Auto colony best placed for it, plus the producers of that building's own AotD inputs. Research works toward the techs that unlock them.
- Special project supply, producers: Auto builds them even when they earn less than they cost.
- Production orders (0.2.39.0): AotD's open production orders take goods per unit (AoTDProductionOrderData.reqResourcesPerUnit) from the player's colonies' output. Every good they need that no colony makes gets a producer the same way, logged "for production orders".
- Ambassadors (Industrial Evolution, 0.3.0.0): Priority is the faction stance (`CASettings.priorityOf`). Per-faction options: High, Normal, Ignore.

- Distributed Regional Network: Upkeep x1.5 (x1.3 with a working Maglev). Farming and mining get +min(3, size-2) per line. Everything else gets -min(4, size-2).
- Bore City: Mining and Extractive Operation output x1.7. The x1.3 build cost hooks a BMO method this BMO version does not call, so no surcharge applies.
- United Aurora Federation colony items (0.2.31.0): Effects are read from UAF 0.8.4c. Dimensional Nanoforge gives +2 to every output and +50% ship and weapon quality, and clears the colony's pollution. It is the only nanoforge placed in a habitable world's forge.
- UAF items: Servosync Pump +2 fuel. Modular Purifier +2 metals and transplutonics. Garrison Transmitter +10% accessibility in a port, ground defense x1.3 in Ground Defenses or Heavy Batteries (ports first).
- AotD colony items: Tenebrian Nanoforge, Synchrotron, Atmospheric Driver, Catalytic Core and Turing Engine are placed from Autopilot Storage like vanilla items. Not placed: Coronal Hypershunt Tap, AquaOrb, Hyperdimensional Processor.
- Wonders and gates while spending freely (0.2.34.0, Vaults of Knowledge): Setting under Budget and spending, recommended On. AotD builds them with a trade contract that takes 10% of the price of goods from your colonies' exports every month.
- Wonders and gates while spending freely (0.2.34.0, Vaults of Knowledge): Each starts only once your colonies make every good it takes (producers are planned, see Special project supply).
- Bifrost: The Control Center is a plan step at the focus world (or the biggest colony). Hyperspace Stabilization comes first and takes a Fullerene Spool. Once the Control Center works, a gate goes up in each of your systems without one, one a day. Each gate adds 20,000 a month.
- Any mod (generic): Deposit conditions in the game's deposit tables are scored like vanilla ones. Colony items registered in the game's item table are placed from Autopilot Storage as the exact stack object.
- Any mod (generic): Items without a known value are valued by the output they add. Never placed: Coronal Hypershunt Tap, AquaOrb, Hyperdimensional Processor, JaydeePiracy's Planetkiller and Lobster Eggs.
- Grand.Colonies: Past its threshold (default 12 buildings) each new building multiplies all colony upkeep by 1.1 (its LunaLib setting). That cost is added to each such build. Without it nothing is built past 12 buildings.
- Terraforming & Station Construction (Boggled): Projects cost only time (200 days, 400 for a planet type change) and are valued by the conditions they leave. Auto starts the best project when none is running. The colony panel lists the best four with Start. A running project is not cancelled.
- Terraforming & Station Construction: Estimates exist for Domed Cities, Genelab, CHAMELEON, Mesozoic Park and the Kletka Simulator. Plain output buildings are measured like other mod buildings. The rest and station construction are left to you.
- Substance.Abuse: A Brewery is built only when Autopilot Storage holds a recipe (or a Recipe Book the colony is big enough for) and no Brewery stands idle. The item placer installs the recipe. Drinks are valued at a quarter of base price.
- Volkov Industrial Conglomerate: Viral contamination shrinks a colony until it is lost. The anti-EVC treatment comes first: tier 1, tier 2, then tier 3 at +25 relations with VIC. Soil nanites go only where farmland is vanilla.
- Roider Union (0.2.38.0, checked against 2.3.1): Dives and Union HQ mine deposits within 1 or 6 light-years. Output is Roider's flat table.
- Roider Union (0.2.38.0, checked against 2.3.1): The Union HQ adds size - 3 crew, size - 3 heavy machinery, size - 2 supplies and size - 2 fuel over the Dives. Both need 3 drugs (none on Unknown Skies' Psychoactive or Shrooms worlds).
- Roider Union (0.2.38.0, checked against 2.3.1): Psychoactive takes 1 off each output.
- TASC stations (0.2.40.1, `CATascStations`): Setting Station construction has Recommend only (also older saves), Auto and Off. Needs Terraforming & Station Construction.
- TASC stations (0.2.40.1, `CATascStations`): Mining stations (belt or field) and siphon stations (gas giant) are founded as new colonies by a crew, through `boggledTools.createMiningStationMarket` / `createSiphonStationMarket`.
- TASC stations: Sites pass TASC's own checks. Only in systems with Auto colonies that are calm or protected, with the research done and room under the colony limit, one crew at a time.
- TASC stations: Valued at size 4, less 10,000 a month, paying back within five years (a twentieth of the price while spending freely). Cost is TASC's settings (500,000 credits, 1,000 crew, 250 heavy machinery, 1,000 metals, 250 transplutonics by default) at base price plus a fee.
- TASC stations: Build time 60 days. Refunded if the site is gone or the colony limit is full.
- TASC stations: The new colony runs Auto with the Money goal. Astropolis stations are left to you.
- TASC stations: `JTTascStationLinkCheck` resolves every TASC call against its jar.
- Protected systems (0.2.40.1, `CAThreat.protection`): Markets in the system not hostile to you count their military: Military Base or High Command 0.5, Patrol HQ 0.25, battlestation 0.5, star fortress or bigger 0.75, orbital station 0.25. A total of 1 or more is protected.
- Protected systems (0.2.40.1, `CAThreat.protection`): A protected colony's station keeps its economic augments under a high threat. A raid or blockade on its way still gets the combat loadout.
- Stable locations (0.2.40.0, `CAObjectives`): Setting Stable locations has Auto (also older saves), Recommend only and Off. In systems with Auto colonies, one crew a day is sent for the best option.
- Stable locations, vanilla: A makeshift comm relay, nav buoy or sensor array, valued per colony in the system by goal weights (+1 stability; +0.1 / +0.05 fleet). Only a kind you do not own there.
- Stable locations, vanilla: Cost 15 heavy machinery, 30 metals, 5 transplutonics at base price plus a fee. Built 10 days later with `Objectives.build`.
- Stable locations, Terraforming Made Easy: Domain-era upgrade of a makeshift one you own. Cost 50 crew, 1,000 metals, 200 transplutonics and a Beta core from Autopilot Storage. Only with two or more Beta cores stored or while spending freely, and when it pays within three years.
- Stable locations, Ashes of the Domain: A Hypershunt Receiver (+2 industry slots per colony, counted at 40,000 a month each) once researched, with a Coronal Hypershunt Tap in Autopilot Storage, in a system your hypershunt reaches (`CAHypershunt.spots`). Cost 1,000 refined metal and 100 Domain heavy machinery.
- Stable locations, Ashes of the Domain: Built 30 days later.
- Panopticon cores (0.2.39.3, `CAPanopticon`): With Emergent Threats: IX Revival, Autopilot Storage takes the Panopticon Instance and Core. One a day goes into an Auto colony's functional Surveillance Center that holds nothing better, shipped first from another colony.
- Panopticon cores (0.2.39.3, `CAPanopticon`): The Instance works like an Alpha core, the Core like a Beta (center stability bonus 3, was 2). Neither counts Hegemony inspection points.
- Omega cores (0.2.38.0, `CAOmega`): With Omega Core Everywhere or Astral Ascension Renewed, Autopilot Storage takes Omega cores. The item pass places one a day after the other cores, into an empty building or replacing a vanilla core.
- Omega cores (0.2.38.0, `CAOmega`): An Omega core is not moved once placed and counts no Hegemony points in a building.
- Omega core effects in a building (Omega Core Everywhere, building effects on in LunaLib; values from its table 1.2.2): Goods buildings x2 output from half the inputs at x2 upkeep. Commerce, Resort, Trade Outpost, Underworld x2 colony income. Patrol HQ, Military Base, High Command x2 fleet size. Ground Defenses and Heavy Batteries x10 ground defense and 500 marines a month.
- Omega core effects in a building: Spaceport and Megaport 1,000 crew a month. Waystation 1,000 each of fuel, supplies, heavy machinery, metals and food a month. Tech-Mining x2 finds. IndEvo Supercomputer x1.5 and Museum x2 income in the system. VoK research buildings 10 databanks a month.
- Omega cores as an administrator (Astral Ascension Renewed): x2 output and half the inputs in every building, +3 stability, +1 industry slot, income x1.25, upkeep x0.75, +50% accessibility, fleets x2, ground defense x3, +25% quality. The only way past size 10 (up to 15).
- Omega administrator placement: Through the mod's AI core administrator plugin, at a colony you govern or one with a hired administrator (who goes back to the pool). Counts 10 inspection points, so it is placed only where the colony's core limit allows (Inspection risk).
- Omega administrator placement: Otherwise a log line says it is yours to place. Past size 10, removing it starts the mod's Great Civil War.
- Omega administrator placement: Autopilot does not change an AI core administrator.
- Niko's Scavengable Industries (0.2.38.0): Only learned blueprints are built. Left to you: Blast-Core Mining, Spy Bureau, Spy Outpost, Cryosanctum, Planetary Defense Network, Artificial Shrine (needs Patchlib), Orbital Flak Cannons, Innovation Shipworks.
- Junk Yard Dogs, Carter's Freetraders (0.2.38.0): Their forges, Biolab and Grey Industries are measured like any mod building. Output your faction bans (drugs, hand weapons, organs) exports nothing unless the colony is a free port and counts only for the colony's own shortage (`CAEngine.illegalUnits`).
- Yunru Industries, Secondary Drydocks (0.2.38.0): Valued by what it adds over a working Heavy Industry or Orbital Works next to it, or as a Heavy Industry that does not pollute. It takes no nanoforge and takes that building's improvement, Alpha core and nanoforge bonuses x1.5.
- Yunru Industries, Secondary Drydocks (0.2.38.0): Auxiliary Shipbays and Advent Outreach are measured. Advent's improvement does nothing.
- Yunru Industries, Secondary Drydocks (0.2.38.0): The Bazaar is left to you.
- Slightly Better Tech-Mining (0.2.38.0): Tech-Mining is worth half its find budget a month: TechMineValue (default 10,000) x 2.5/1.5/1/0.5 for vast/extensive/widespread/scattered ruins x colony size x what is left of the ruins. Value falls as ruins deplete.
- Stations from other mods (0.2.27.0): Stations count by their tags (station, battlestation, starfortress): Interstellar Imperium's, Knights of Ludd's Libra stations, AotD's Star Citadels. The tags feed the inspection and AI core defense estimate, AI core placement, the plan's Orbital station step and Station Augments loadouts.
- Stations from other mods (0.2.27.0): The Station line has an Imperium choice with Interstellar Imperium. Stella Castellum, Libra stations and Star Citadels are counted when you have one and are not built.
- Hidden buildings: A building the game hides (megastructure defenses, the hypershunt's port) is not upgraded, improved or given an item or AI core, and is not listed under Locks. A hidden station's augments are left to you.
- Mod buildings: A building with no formula or estimate is built on a throwaway probe colony (your colony's size and vanilla deposits, no planet, every commodity on hand, outside the economy) and the difference is read: commodities, stability, accessibility, income and upkeep multipliers, ground defense, fleet size, ship quality. Upgrades measure both buildings.
- Mod buildings: Effects over time (monthly items, spawned fleets) are not seen.
- Settlement (Random Assortment of Things, Frontiers on): One per save. Modes: Advisor (before setup), Approve, Auto (recommended), Off. Founds on the best surveyed site once it makes at least the threshold (default 20,000 per month). Only unclaimed planets and your own colonies are used.
- Settlement (0.2.37.0): Founding from afar sends an expedition. RAT's cost is paid at once and refunded if the site cannot take it on arrival (10 days plus a day a light-year from your nearest colony).
- Settlement (0.2.37.0): Builds Refining and Trading Post when they pay back within 2 years. Founding and facilities use the same budget, per-project cap and fees as colonies.
- Station augments (Station Augments): Installing is free and reversible. The manager picks the known augments worth the most within each station's augment points (AP), scored by the colony's goal weights.
- Station augments (Station Augments): Station Augments only changes augments while docked. From afar Autopilot books a refit crew: the change happens 7 days later, or at once while your fleet is in the system (0.2.37.0).
- Station augments, economic: Accessibility (Command Center, Logistics Drones, Jump Engine). Upkeep (Efficiency Overhaul; Sanctified Station only with an active Luddic majority).
- Station augments, economic: Output (Hydroponics Basins +2 food and +1 organics, counted above what the colony already makes). Ground defense (ECM Package).
- Station augments, economic: Economy Conversion is valued as the best industry waiting for a slot.
- Station augments, combat (0.2.28.0): The manager reads the station's ships (fighter wings, guns by size, point defense, shields, drones) and its AI core. Each augment is valued as a share of the station's fighting power.
- Station augments, combat (0.2.28.0): Bubble Shield: 100,000-flux shield, worth most on stations without shields. Aftermarket Defense Platforms: four weapon platforms with beta cores.
- Station augments, combat (0.2.28.0): AI Fighter Uplink and Fighter Temporal Cores (+30% fighter speed): carrier stations.
- Station augments, threat: While a colony plans with Security, or a raid or blockade is on its way, combat counts four times as much. The combat loadout takes AP from the output augments, and they return when the threat drops.
- Station augments, threat: A threat change re-plans at once. Nothing changes during a battle.
- Station augments, threat: Log: "(threat is high: defense first)", "(a raid or blockade is on its way: defense first)", "(threat over: output again)".
- Station augments (setting): Follow colony mode (default), Recommend only, Off. Auto colonies apply within a week. Approve colonies get the recommendation in the log once. Each managed colony's panel shows it with an Apply button.
- Station augments: Augments you installed are not changed. One Autopilot added that you remove is not added back ("Allow again" in the panel). Installed augments are replaced only when the new set is worth at least 10% (and 300 per month) more.
- Log (0.2.34.0): One short line per entry, for example "Gargor: building Waystation (100,000)". Setting Log entries on screen (Interface): Every entry (default), Important only, Off.
- Log (0.2.34.0): States that flip back and forth show at most once every 10 days per colony. Trade contracts are one line a day.
- Log (0.2.34.0): Details go to `starsector.log`.
- Voice and look (0.2.41.0, `CATheme`): Setting Announcement voice (Interface): Machine (default), Plain. On screen each entry starts with a code, for example `BUILD / Hot Springs: Planetary Shield (750k¢)` (BUILD, UPGRADE, INSTALL, SWAP, SHIP, RECEIVED, DEMOLISH, ADVISE, SIEGE, CLEAR, THREAT, POLICY, RESEARCH, LEDGER, CONTRACT, SCAN).
- Voice and look (0.2.41.0, `CATheme`): A line with no code is the plain sentence. Credits are shortened.
- Voice and look (0.2.41.0, `CATheme`): The panel log and `starsector.log` keep the full sentence.

## Diagnostics

- Load scan: Every load rates all buildings, conditions and items Supported, Measured, Analyzed or Quarantined. An error in mod code during planning quarantines that building.
- Output: The empire panel's Diagnostics section, `[ColonyAutopilot]` lines in `starsector-core/starsector.log`, and `saves/common/colony_autopilot_diagnostics.json.data`.
- Mjolniar's mods (top of the Diagnostics page): Lists Auto Colonies and each installed mod of Mjolniar's with version, warnings and errors this session and status line. Other mods write their status to sector memory as `$jonahs_status_<mod id>` on every load.
- Mjolniar's mods (top of the Diagnostics page): The page also shows credits promised to production that surplus investment keeps back.
- Mod watch (0.2.38.0, `CAModWatch`): Weekly and at every scan, for each integrated colony mod (Omega Core Everywhere, Astral Ascension Renewed, Niko's Scavengable Industries, Roider Union, Junk Yard Dogs, Carter's Freetraders, Vulpoids, Yunru Industries, Slightly Better Tech-Mining, Yunru's Worlds, Rogue Planets). Reports integration health, building ratings, probe output against real output, estimate corrections, and own checks (Omega x2, Omega administrator size limit, Roider and Drydocks formulas, Midas pollution, vulpoid sanctions, illegal output, Tech-Mining worth).
- Mod watch: A verdict per mod (OK, WARN, FAIL) on the Diagnostics page, a `[ColonyAutopilot] mod watch` line per mod in `starsector.log`, and a `modWatch` report section. The author's `moddiag.py` (not shipped) reads both plus every log.
- Why nothing is built: An Auto colony with nothing worth starting writes one `[ColonyAutopilot] <colony>: nothing worth starting` line a month with its best three options, their score parts (worth, plan, follow-through, price, upkeep) and the best one that only lacks a free slot.
- `jtdiag.ps1` (the author's, unshipped): Checks built vs installed jars, repos and releases, enabled mods and dependencies, `starsector.log` lines and the newest save.

## Staying working through mod updates

1. Health check at load: Each integration knows its mod, the version it was written against, the ids it relies on and the names it reads. Status in Diagnostics: OK; Newer version, all found (works, estimates trusted less); Partly off (parts with missing ids cannot act); Off (does nothing).
2. Mod values read live: Where a mod exposes its numbers (Roider's tables and ranges, What We Left Behind's ore bonus, AotD's restoration rate), Autopilot uses them, with its copies as fallback. A changed value is logged once and listed in Diagnostics.
3. Calibration: Weekly, every plain building (working, no core, item or improvement) is compared with the prediction (upkeep for all types, output for estimated ones). The ratio is smoothed (70% old, 30% new), capped 0.5x to 2x, used after two readings, and saved.
4. Margins: Game formulas count in full. Probe measurements 90%, estimates 85% (92% once calibrated), minus 5% on a mod version not written for. An estimate must beat a known option by about 18%. In Auto, an estimate-scored pick costing over half the per-project cap waits for approval.
5. Failed actions: After each action that reaches into the game or another mod, Autopilot checks it took effect. Three failures in a row pause that action for the session, with a log line and a Diagnostics entry. Reloading tries again.

## Building from source

- Source: In `src/`. It compiles against the game and optional mod jars with JDK 17.

```
javac --release 17 -cp "<core>/starfarer.api.jar;<core>/starfarer_obf.jar;<core>/fs.common_obf.jar;<core>/log4j-1.2.9.jar;<core>/json.jar;<core>/lwjgl.jar;<core>/lwjgl_util.jar;<TME>/TerraformingMadeEasy.jar;<IndEvo>/jars/IndEvo.jar;<Nexerelin>/jars/ExerelinCore.jar" -d build (all .java files under src)
jar cf jars/jonahtweaks.jar -C build data
```

- Script class loader: Refuses any mod class that names `java.lang.reflect` or file classes. `CARefl` does all reflection through method handles. `check_banned.ps1` fails the build on a banned class reference. `JTLoaderCheck` loads every class under that loader and runs `CARefl`.
- Optional-mod classes: `CATerraform`, `CAIndEvo`, `CACourier`, `CADefense`, `CASettlement` and `CAAugments` reference optional mods and are called only when that mod is enabled. `CASettlement` and `CAAugments` also need the Kotlin runtime (LazyLib) and LunaLib on the compile classpath.
- Combinations: The author's build checks prove any combination works: only those classes reference each optional mod, every caller checks `isModEnabled`, and the jar links under `-Xverify:all` against all 32 combinations.
- Reflection-only classes: `CASynergies`, `CAContracts`, `CAHypershunt`, `CAMegastructures`, `CAResearch`, `CADevelopment` and `CAAotD` reach Vaults of Knowledge and Theory of Toolbox through `CARefl`. A name that cannot be found is left out and the log says why.
- Reflection-only classes: `JTAotDEconLinkCheck.java` checks those names; run it with `-XX:+UnlockDiagnosticVMOptions -XX:-BytecodeVerificationRemote`.
- Augment check: An offline check covers the augment choice (knapsack against brute force, swap margin, shrunk budget, incompatible pairs).
- `CAAotD`: Reaches Building Menu Overhaul through reflection only.
- Colony-mod integrations: `CATasc`, `CAGrand`, `CABrewery` and `CAMods` use literal ids, reflection or method handles only. TASC is reached through `MethodHandles.findStatic`, since plain reflection on `boggledTools` needs Illustrated Entities' classes.
- Colony-mod integrations: `JTColonyModsLinkCheck.java` checks the names against the TASC and LunaLib jars.

## Test steps

1. Load a save with a colony. `starsector.log` has `[ColonyAutopilot] scan done`, no `[ColonyAutopilot]` warnings and no "can't read ..." or "File access and reflection are not allowed to scripts" line.
2. Colony panel opens on Overview. Plan and builds shows the plan and top 3 picks. A button click keeps the page and scroll position.
3. Intel, Colony Autopilot filter: budget numbers, log entries and Diagnostics show. The empire panel page list is Colonies, Autopilot Storage, Mod features (only with a supported mod), Settings, Log, Diagnostics. Mod features has one section per feature in use.
4. With Ashes of the Domain: recommended builds include starters (Smelting) and none its build menu locks. Locked steps show "(locked: ...)". The upgrade is next after a starter. Research locks and synergies show in place of "info unavailable".
5. Diagnostics: the scan line reports measured buildings. Measured entries list what was read (for example "+3 metals, needs 2 ore"). Rescan lists outputs of buildings with inputs (Artisanal Farming, Junkyard Resource Forge, Organ Farm).
6. With Station Augments: a calm Auto colony with a station and no farm gets Hydroponics Basins. At high threat, within a day the augments change to combat ones ("Auto set (threat is high: defense first) station augments: ..."). They change back after the threat drops.
7. Station augments panel: shows AP used, "What it fights with" and recommended changes. Apply installs them (log "you set station augments"). Remove one and advance a day: the log says Autopilot will not add it again and the panel offers "Allow again". Macro Thrusters, Backline Deployment, Safety Shunt and Support Outfit are not added.
8. With Vaults of Knowledge: Industry synergies matches VoK's screen. Research shows the current tech and up to six ranked techs, and Start begins one. A Coronal Hypershunt colony shows the Coronal Hypershunt section, nothing is built or installed there. A Pluto or Nidavelir colony shows the Megastructure section with Apply.
9. With Terraforming & Station Construction: the Terraforming section lists valued projects. In Auto with nothing running, TASC's screen shows a project started by Autopilot. A colony needing only an Atmosphere Processor gets one recommended.
10. New game, first colony: the fast buildout dialog appears. Pick 250,000: the colony switches to Auto, the log says "fast buildout started", and builds start daily without taking credits below 250,000. Stop buildout ends it. A save with colonies shows no dialog. At threat 70% or more defenses wait and builds are ranked "for fast buildout (money only)".
11. New save, decline fast buildout, Save and switch to Auto: the budget line shows money available with negative net income (Automatic budget). Builds start within days. Each recommended build shows money a month after upkeep and a "Ranked" line. Options that do not pay for themselves are marked and not started.
12. With Nexerelin, Agents on Defense with a Pather cell or rebellion: within a day an agent is hired (log "Hired agent", 12,000). The Agents section lists it. Your own agents get no orders until Let Autopilot use it. A Nexerelin outpost is listed "Nexerelin outpost, left alone".
13. Colony Autopilot button: near the building grid on the colony screen (Command tab or docked), covers nothing, opens the colony panel, and does not show on other factions' markets, Refit, Fleet or with Button on the colony screen off. A colony with 12 or more buildings gets it with no "no free spot" line.
14. Administrators: a 3rd colony with the base limit of 2: within a day the log says an administrator was hired ("you govern 1 more colony than your colony limit") or none is for hire. No colony is made autonomous. Arrivals are placed. An idle unskilled administrator stays unassigned for a small colony under the limit. A skilled one goes to a big colony with several industries.
15. With Nexerelin, Autonomy section: money-losing colonies a year old or more show Grant autonomy, autonomous ones Take back. On Recommend, a colony that lost money 3 months in a row is logged once. On Auto it is made autonomous, one every 30 days at most, and not while spending freely outside an emergency.
16. AI cores: an Alpha, a Beta and two Gammas in Storage, a colony with a Heavy Industry, no shortages and under 7 inspection points. The Alpha goes where output is worth most, the Beta where upkeep is highest, the Gammas only where an input is short. Log lines give what each adds and the points. Overview shows whether the Hegemony counts its cores (above size 3) and its Pather interest.
17. AI core limits: Inspection risk More where defended allows cores past 7 points when defenses beat the next inspection by 1.5 times. Never inspected returns one core a day to Storage until 7. Small colonies only returns cores one a day above size 3. Cores do not change while an inspection is planned.
18. Siege: a colony under blockade (or with hostile fleets at the planet) is logged and its panel says so. Autopilot starts only defenses and changes none of its items, cores, policies or augments. In Auto no trade contract is accepted for goods only it makes. The log says when it ends.
19. Colony crises page: the meter matches the game's intel, with faction progress and your best military structure's multiplier. Once the meter passes 500 the log says once "Colony crisis coming: ..." and an Auto colony there switches to Security planning. Pather attack: target cores return to Storage one a day until Pather interest is under 7. Nexerelin police raid: Auto colonies' free ports close.
20. Surplus investment: a save from before 0.2.21.0 with over 3,000,000 credits and an Auto colony: the log says on the first day it is on, with the focus world and the amount over the line. Overview of the focus world says so. Make this the focus world moves it. Let Autopilot choose hands it back.
21. Upkeep cap: a plan step with upkeep over New upkeep cap per month starts in the first pass of a month with no other new upkeep. An off-plan build waits until 10% of net income covers it (Automatic) or you raise the Fixed cap. A save from before 0.2.24.0 logs Automatic on the first day (10% of last month's net income, at least 20,000).
22. Items: a Pristine Nanoforge in Storage with a Corrupted one in an Auto colony's Heavy Industry or Orbital Works and no empty one: swapped within a day. An item no Auto colony has a building for shows "Uses the ... waiting in Autopilot Storage" on that building's option. A tech needing databanks and an Alpha core (Orbital Skunkworks) is startable with an Alpha core in Storage.
23. Diagnostics, Mjolniar's mods: lists Auto Colonies and each other installed Mjolniar mod with versions, 0 warnings, 0 errors and a status line. Mod watch lists each installed colony mod as OK / WARN / FAIL after a week. `python -I jtbuild/moddiag.py` shows the same plus log errors.
24. Research keeps running: on Auto with nothing running and databanks on hand, a tech starts within a day even when none pays back within a year. With too few databanks the log says once "Auto has nothing it can start". Research one at a time (0.2.42.4): databanks at another colony give one "... starts once they arrive" line.
25. Surplus terraforming (Terraforming Made Easy, Max output, credits over the surplus line): the Colonies page shows the best move. Within a day the log says "surplus terraforming started ..." paid from the surplus. No habitable colony loses habitability, farmland or organics. After a Geomorphology Station removes pollution, "remove Pollution" is not started there again.
26. Surplus research: the Colonies page shows "Surplus research: N of 8 colonies have a research building ...". When it pays the log says "surplus research started building a Research Facility". With nothing left to research the facilities come down. An Alpha core in Storage goes into a Research Facility or Black Site first ("research buildings get Alpha cores first").
27. Spending freely: over 10,000,000 above the surplus line with positive net income: within a day the log says "Surplus: ... every Auto colony spends it now" and the Colonies page says "Spending freely". Defense and access buildings score above zero and get built. Panels show the station's top tier, Heavy Batteries, Planetary Shield, Megaport and Patrol HQ as "(surplus credits)" steps.
28. Wonders and gates: spending freely with Refined Metal, Metals, Heavy Machinery and Supplies all made by your colonies, the log says "Grand Wonder site started for the Daedalus Array", then "Daedalus Array construction started". Without one of those goods it says "waits for ... production" once. The focus world shows the Bifrost Control Center as a "(surplus credits: Bifrost network)" step.
29. War fleets, with Nexerelin: the War fleets section shows request points. With a known pirate base within 15 light-years and points above 40% of capacity, within a day the log says "<base>: N-pt base strike from <colony>, <cost> (Pirates base)". High threat gets a defense fleet. After a hostile raid, its source market is "Marked for strikes back". While spending freely up to 3 go out a day and every colony gets a "standing guard" fleet. No invasion is requested.
30. Endgame economy: spending freely with last month's net income at 1,000,000 or more: the log says "Endgame economy: ... Keeps X/month" (a third of that income). Panels show "Endgame economy: its money counts 5%". At a colony with no free slot an industry is taken down for another (never a Military Base, Heavy Industry or Orbital Works) and its core returns to Storage.
31. Pollution: a Pristine Nanoforge in Storage is not installed in a War production colony on a habitable world. One you install there is removed the next day (log "took the Pristine Nanoforge out of Heavy Industry before its pollution became permanent"). A locked building keeps it.
32. Fair play (0.2.37.0): a Gamma core in one colony's Storage and a building at another that wants it: the log says "Shipping Gamma Core from <colony>'s Autopilot Storage to <colony>, N days, <fee>", then "arrived in Autopilot Storage". Each colony's Storage tab shows only its own items. With Agents on Defense and none hired, "recruiting one for security" and 60 days later "reported for duty". Settings has no Cheats group, Arrival delay row or Attacks on your colonies group. A remote augment change logs "refit crew booked" and happens 7 days later.
33. Omega cores: with Omega Core Everywhere an Omega core in Storage goes into a building within a day ("Omega core into <building> (output x2 from half the inputs, upkeep x2, ...)") and Mod watch says the x2 is applied. With Astral Ascension Renewed at default Inspection risk the log says an Omega core could govern a colony and counts 10 inspection points. With No limit it governs, the size limit reads 15 and Mod watch passes. Autopilot does not change that administrator.
34. Left to you: with Niko's Scavengable Industries, Diagnostics lists Blast-Core Mining, Spy Bureau, Spy Outpost, Cryosanctum, Planetary Defense Network, Orbital Flak Cannons, Innovation Shipworks and Artificial Shrine as "Left to you". Yunru's Bazaar is the same. A learned Training Grounds at a colony with a Military Base is valued.
35. Mod items: a Biolab's pick at a colony without a free port reads "illegal here without a free port, so it exports nothing". A Vulpoid Bioforge is not installed. A Lobster Bioforge goes only into a Biotech Facility. A Midas Nanoforge does not go into a habitable world's forge. A Heavy Industry colony shows Secondary Drydocks "next to Heavy Industry". Slightly Better Tech-Mining values a size 6 vast ruins pick at tens of thousands a month. Advent Outreach is not improved.
36. Machine voice (0.2.41.0): a build shows `BUILD / <colony>: <building> (<cost>)` on screen. The Log page shows the full sentence. Voice Plain reads "Autopilot: <colony>: building ...". A colony with nothing worth starting shows `SCAN / <colony>: N options, holding ...` at most once a week. Important only or Plain show no pings.
37. Capital (0.2.42.0): the log says "<colony>: capital: your home port..." and the custom production gathering point is that colony. Moving it makes the log restore it next day. With net income over 100,000 the log says "capital: commerce build-up starts" and "(capital)" steps lead Builds. With every slot full an industry is taken down for the trade building at most once in 90 days. A weapon in another colony's Storage ships to the capital within a week ("Storage logistics: ...").
38. Industry limit (0.2.42.1): an Auto colony with 3 industries on 2 slots: within a day the log says "took down <industry>: ... cost 5 stability", its core or item appears in Storage and stability rises by 5. Stability guard (0.2.42.2): at 0 stability the screen shows "ALERT / <colony>: stability is at 0..." with its worst modifiers. Over the limit it takes an industry down the same day. Still at 0 after 20 days, it takes down its most stability-costing building every 10 days until above 0. A captured capital keeps the Capital setting and is the capital again once retaken.
39. Low profile (0.2.43.0): pick it for a first colony or under Settings, Expansion style. The log says it is on and the Crises page lists its rules. An Auto colony above size 3 gets no AI cores (existing ones return to Storage one a day). A build that takes Pather interest to 7 scores negative. Free ports close.
40. Market model (0.2.44.0): Recommended builds show lines like "+3 Ore: about X per month in exports (your share of the sector's ore 41% -> 44%)". A second producer of a good the colony makes says it adds nothing. The Diagnostics report has a "market" section. `starsector.log` has a weekly "market model: N of M colony exports within 2 points" line.
41. Save from 0.2.45 (0.3.0.0): Settings show the 0.3.0 menu with presets on their old values (Spending Normal, War Retaliate or the level matching War fleets, New colonies Off, Retake your lost colonies Off). Every faction stance is Auto or the one its priority gave. One `SETUP / Settings: moved to the 0.3.0 menu, nothing you set changed` line shows, with the What's new box.
42. Planner (0.3.0.0): with a free slot and a waiting 360-day TME structure, the build that earns more per construction day starts first. Within a month no two colonies start the same producer for one shortage. Nothing Autopilot builds is taken down within the year and a building it takes down is not rebuilt there for a year.
43. Money ladder (0.3.0.0): the Budget panel shows the money tier and ladder line, and the log has a `TIER / Money tier: ...` line on each change. At Idle money with Doctrine priority ships set, the Shipyard proposes an order (Recommend only). Approve adds it to the custom production list and charges the fee. Reject holds proposals off 30 days.
44. Auto-expansion (0.3.0.0, dev save): New colonies on Ask first. Expansion shows the best 5 planets with value, cost, plan and reason. Found now asks with method, price, fee and days. After the travel time the colony is founded on Auto, with an administrator when past the limit. Colonizing the target yourself during the expedition refunds the crew's price (fee kept).
45. War (0.3.0.0, dev save, Nexerelin): Retaliate behaves as 0.2.45's Protect and strike back, plus retakes while Retake your lost colonies is on (on in a new game). At Conquer an Enemy market you declared war on gets an invasion. At Scorched with Authorize bombardment a Destroy capital is bombarded once. Stand down sets Defend and refunds operations still organizing. A conquered Auto colony stays above 0 stability. On Approve or Advisor it gets the monthly 0-stability alert.
