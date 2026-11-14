# Source guide

- Language: Java 17. Compiled against Starsector 0.98.5a-RC4's core jars. The same jar runs on 0.98a and 0.98.5a.
- Size: about 47,800 lines in 131 files (0.3.3.4).
- Class comments: every class opens with a comment saying what it does and why.
- Player docs: [Getting started](getting-started.md), the guides linked from the README, and [details.md](details.md).
- [Auditing the mod](audit.md): every way the mod changes the game and what each one charges.
- [Automating Starsector colonies](automation-primer.md): game API reference for colonies, buildings, items, cores and the economy, with the traps found.

> The code was written by AI coding agents (see the README's warning). It is compiled and checked offline (see [Building and checking](#building-and-checking)). Parts are confirmed in game, and not all of it.

## Contents

- [Layout](#layout)
- [Overview](#overview)
- [Where to start reading](#where-to-start-reading)
- [A day in game](#a-day-in-game)
- [How a building is scored](#how-a-building-is-scored)
- [Modes and approvals](#modes-and-approvals)
- [Plans](#plans)
- [Mod buildings: the probe colony and diagnostics](#mod-buildings-the-probe-colony-and-diagnostics)
- [How the optional mods stay optional](#how-the-optional-mods-stay-optional)
- [Surviving game and mod updates](#surviving-game-and-mod-updates)
- [What's saved](#whats-saved)
- [Files by area](#files-by-area)
- [Conventions](#conventions)
- [Building and checking](#building-and-checking)

## Layout

| Path | What's there |
|---|---|
| `mod_info.json` | Mod id `jonah_tweaks`, version, jar, mod plugin |
| `src/data/scripts/jonahtweaks/` | The mod plugin and two leftover classes from the 1.x tweaks mod |
| `src/data/scripts/jonahtweaks/autopilot/` | All other classes. `CA` stands for Colony Autopilot. |
| `data/config/colony_autopilot.json` | Default settings for a new save (commented) |
| `data/campaign/submarkets.csv` | Registers the Autopilot Storage tab |
| `data/campaign/market_conditions.csv` | Registers the retired `jt_patronage` condition so old saves load |
| `jars/autocolonies.jar` | The compiled code the game loads |

- Package name: `jonahtweaks`, kept from the old mod id so existing saves load.

## Overview

- Daily pass: one pass over the colonies once a game day. Each step runs through `CAStep`, so one failure does not stop the rest.
- Scoring: for each Approve or Auto colony, `CAEngine` lists every building and upgrade it could start and values each in credits a month, weighted by the colony's goal.
- Planner: `CAPlanner` turns each value into a net present value over the payback horizon (36 to 120 months), queues every module's offers together, and starts the best value per construction day and per credit.
- After the planner: money tiers and the money ladder, Auto-expansion, removers, item placement, then one step per optional mod.
- Actions: each uses the same game call as the game's UI or the source mod's UI. It charges what that UI charges plus a convenience fee through `CABudget`, and is logged.
- Other mods: reached by class, by name or by id. Each fails closed and is checked at load.

## Where to start reading

1. [JTModPlugin](../src/data/scripts/jonahtweaks/JTModPlugin.java): on every save load, clears caches, loads and migrates the saved data, adds the scripts, the colony menu option and the intel panel, and runs the diagnostics scan.
2. [CAScript](../src/data/scripts/jonahtweaks/autopilot/CAScript.java): every frame, checks the fast buildout offer and the approval summary. Once a game day, calls `CAAuto.runPass`.
3. [CAAuto](../src/data/scripts/jonahtweaks/autopilot/CAAuto.java): the daily pass and the order of every step.
4. [CAEngine](../src/data/scripts/jonahtweaks/autopilot/CAEngine.java): scores every building a colony could build or upgrade to.
5. [CAValue](../src/data/scripts/jonahtweaks/autopilot/CAValue.java) and [CAHorizon](../src/data/scripts/jonahtweaks/autopilot/CAHorizon.java): one value for building and for keeping; the payback horizon.
6. [CAPlanner](../src/data/scripts/jonahtweaks/autopilot/CAPlanner.java): the one queue.
7. [CAPlan](../src/data/scripts/jonahtweaks/autopilot/CAPlan.java): the target layout per colony.
8. [CABuild](../src/data/scripts/jonahtweaks/autopilot/CABuild.java): starts a build the way the build menu does.
9. [CARich](../src/data/scripts/jonahtweaks/autopilot/CARich.java): money tiers and the money ladder.
10. [CAColonyDialog](../src/data/scripts/jonahtweaks/autopilot/CAColonyDialog.java) and [CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java): the colony panel and the empire panel.

- Integration classes: readable on their own after those.

## A day in game

- Scripts added on load (transient): `CAScript`, `CAScreenButton`, `CABuildHints`, `CAEnforcers`, and `CAWarListener` with Nexerelin.
- `CAScript.advance`: calls `CAAuto.runPass(now)` once a game day on the game's clock (`CATime`). While Theory of Toolbox's threads read markets, the pass waits, at most one more day.
- `CAStep.run`: isolates and times a step and stops it for the session after 3 failed days in a row. `CAStep.guard` isolates without the stop, for safety steps.

The pass, in order:

1. Month and week: `CABudget.rollMonth`, the upkeep cap check, `CACalibrate.run`, `CAModWatch.weekly`, `CATidy.weekly`, `CAMarket.weekly`.
2. Daily state: `CASiege.update` (guarded), `CAStores.daily`, `CALogistics.daily`, `CACrisis.daily`, `CASurplus.daily`, `CACapital.daily`, `CAEndgame.daily`, `CAProjects.update`, `CAFloors.update`.
3. Colony lists (guarded): Auto colonies, those not under siege, every colony not Off.
4. Safety (guarded): `CAOverLimit.run` (industry limit and 0 stability), `CARogue.daily`, before anything is built.
5. Planner: `CAPlanner.run`. Details below.
6. Money tiers and ladder: `CARich.run`, then the rungs in the preset's order (`CAStockpile`, `CAMilitary`, `CAShipyard`).
7. Auto-expansion: `CAExpansion.run`.
8. Removers and placement: `CAPrune`, `CALowProfile`, `CAItems.ensureStorageTabs`, `CAItems.run` (guarded), `CAStoragePlugin.evict`.
9. Mod steps, each only when its mod is enabled: `CATerraform`, `CATerraformSurplus`, `CASurplusResearch`, `CAWonders`, `CAIndEvo`, `CACourier`, Nexerelin steps (`CADefense`, `CAWar`, `CASpies`, `CADiplomacy`, `CAConquest.tend`, `CAAutonomy`; guarded), `CAPolicies` (guarded), `CAStory`, `CAObjectives`, `CATascStations`, `CASettlement`, `CAAugments`, `CAContracts`, `CAHypershunt`, `CAMegastructures`, `CADevelopment`, `CAResearch`, `CATasc`.
10. Panels and report: `CAEngine.fill` keeps every managed colony's scores for the panels. `CADiagnostics.flushReport` writes the report.

The planner:
- Offers: `CAAction`s from builds and upgrades (`CAEngine`), surplus terraforming, surplus research, the wonder site, stable-location crews and story points, each scored by NPV (`CAValue.npv`, `CAHorizon`).
- Per colony: new buildings share one construction queue, ranked by NPV per construction day. Upgrades run beside it.
- Across colonies: money goes by NPV per credit. The first pick that waits for money gets an earmark.
- New-upkeep cap: counts only upkeep a building does not cover itself.
- After each start: actions sharing a colony, good, building or one-per-empire flag are re-scored.
- Fast buildout: an Auto colony in its fast buildout gets `CABuildout.pass` in place of its normal offers. It ranks by money only.
- Approve colonies: the best pick becomes a proposal (see [Modes and approvals](#modes-and-approvals)). A rejected one waits 180 days.
- Removal marks (`CATombstone`): a building taken down in the last year stays off that colony's list.
- Fresh look: when something starts or finishes, a slot opens, threat or siege changes, the month rolls over or the money tier changes. Otherwise weekly (`CAAuto.wait`).
- Fallback: with the hidden `legacyPlanner` switch, or after 3 failed planner days, the old build pass (`CAAuto.buildPass`) runs.

Rules for every step:
- Credit reserve: nothing in the pass spends below it.
- Budget exceptions: emergency defense, relief fleets and an administrator for a colony past the colony limit may pass the monthly budget. They keep the reserve.
- Transfers between colonies: items, cores and research databanks go through `CAStores.ship` for a fee and travel time.

## How a building is scored

- `CAEngine.evaluate`: builds a list of `Pick`s, one per building the colony could start and each upgrade of a building it has.
- Outposts: `CAEngine.isOutpost` (Nexerelin's outpost flag or tag) returns nothing.
- Skipped: buildings the colony has or has an upgrade of (`CAPlan.hasOrBetter`), population, hidden or menu-less buildings, upgrade-only targets, a second station, anything `isAvailableToBuild()` refuses, and anything Building Menu Overhaul hides or locks (`CAAotD.allows`).
- `Pick.fx[7]`: credits a month, stability, accessibility, defense, fleet size, ship quality, growth. `Pick.lines`: the reasons the panels show.
- Source 1, game formulas (`CAEngine.effects`, `deposits`): vanilla buildings. Deposit industries read the tables `ResourceDepositsCondition` uses.
- Source 2, estimates (`CAEstimates`, `CAEngine.estimate`): hand-written effects for mod buildings, marked "(estimated)".
- Source 3, measurements (`CASimulator`): a building with no formula or estimate is built on a probe colony and the difference is read.
- Source 4, adjusters: `CADevelopment.adjust`, `CASynergies.score`, `CARogue.score`, `CAMods.apply`, `CAOddisz.score`, `CAGrand.penalty`, `CABrewery.apply`.
- Shortages: `CAEngine.empireDeficits()` adds up unmet demand plus what AotD trade contracts lack. Supplying a short good is worth more.
- Production floors (`CAFloors`): hull source, hull capacity, order goods, production quality, weapons, Domain-grade Parts, supplies, fuel, producer inputs. An open floor adds value for the part of the gap a pick closes (`CAValue.add`). Off at the Money goal unless `floorMoneyGoal` is set.
- Money taper (`CAMoneyCurve`): credits count in full up to a share of net income, less above it. `CAValue.add` and `keepDetail` both use it, so builders and removers agree.
- Diversity: past 2 copies of one industry family, each further copy counts 75% (`CATuning.DIVERSITY_*`).
- Trust (`CAEngine.trust`): 1 for game formulas, 0.9 for measurements, 0.85 for estimates (0.92 once checked against the colonies), 0.05 less for an untested mod version. Positive value is scaled by trust.
- Upkeep (`CAEngine.upkeep`): the spec's upkeep times max(1, size - 2) times the colony's upkeep multiplier, times the `CACalibrate` factor.
- Weights (`CAEngine.finish`): `fx` times the goal's row in `WEIGHTS` (Balanced, Money, Security, War production, Rapid growth, Self-sufficiency). The stability floor applies.
- Pollution: an upgrade that would pollute a habitable world scores negative and is not started. `CAItems.wouldPollute` keeps polluting items out of forges. `CAItems.pullPollutingForge` takes one back while it can still be undone.
- Plan bonus (`CAPlan.bonusFor`): the flat bonus goes only to enabler steps. Other steps count what they earn plus half of what they lead to (`CATuning.DOWNSTREAM_SHARE`).
- Money tiers: from Idle money, a credit counts half at Approve and Auto colonies, a quarter while spending freely (`CASurplus.moneyWeight`) and 5% in the endgame.
- Price: score = worth - (price + fee) / H - worth x construction days / 30 / H (`CAHorizon.npvMonthly`, `CAValue.npv`).
- Horizon H: 36 months, rising to 120 as surplus credits reach 10,000,000. It falls only below 60% of what raised it (`CAHorizon.slide`).
- `CAValue`: `add` values a new build and `keep` values an existing one. `keep` is what the colony loses a month without it, plus the shortage it leaves, plus the synergies it keeps. Buildings under construction count as supply.
- `CATombstone`: adds the teardown loss (what Autopilot paid that has not paid back over H) and the one-year removal marks.
- `Result`: sorted `picks`, `blocked` (why nothing can start), picks lacking only an industry slot, and counts of locked and unsupported options.
- Quarantine: an exception thrown by a building's code during scoring quarantines that building (`CADiagnostics.quarantine`).

## Modes and approvals

- Colony modes (`CAData.Colony.mode`): Off (left alone), Advisor (hints only, the default), Approve (decisions wait for you), Auto (acts within the budget).
- Empire mode (`CASettings.empireMode`): picks the default for every empire-wide setting through `CAModeTable`.
- `CAModeTable` kinds: EMPIRE (follows the empire mode), COLONY (the value Auto colonies use), SAME (one value in every mode), PICK (a player choice with no default), STATE (bookkeeping).
- Player-set settings: a setting the player changed is listed in `CASettings.playerSet` and is never changed by a mode switch or a reset.
- `CAApprovals`: one list of every decision waiting: colony builds and make-room swaps, the RAT settlement, colony expeditions, the money ladder's proposals, and the hypershunt, megastructure, TASC, station augment and autonomy asks.
- Approving: checks the credit reserve, then the kind's own handler with its own checks (stability floor, slots, budget). Each pays its cost and fee.
- `CAApprovalDialog`: one summary dialog, grouped by colony, at most once per `approvalDays` (60 by default, Off possible). Opens only at a safe moment: campaign running, no dialog, menu, screen or battle, not jumping. Options: Approve all, Choose each, Reject all, Later.
- `CABuildHints`: Advisor hints. A read-only panel beside the game's build menu or Building Menu Overhaul's ranks the options: Best and Good with what they earn, Poor with a short reason. Scope: `CASettings.buildHints`.
- `CAOptionProvider`: adds "Colony Autopilot..." to the Population & Infrastructure menu and one Autopilot line to build menu tooltips at hinted colonies.

## Plans

- `CAPlan`: a target layout per colony from its planet, hazards, resources and goal. Steps are builds, conditions to reach, or notes.
- `bonusFor`: favours picks that advance the plan, the earliest open step most.
- World plans: one that already paid for terraforming switches only to one worth at least twice as much. `CAMark` keeps the stamp.
- `hasOrBetter`, `leadsTo`: follow upgrade chains and AotD's downgrade links.
- `CAStations`: every colony plans a station from size 4 (or half a year old), and the next tier as it grows.
- `CAWorldPlanner`: adds a Terraforming Made Easy special world (Forge, Fortress, Paradise, Hive, Death, Waste) when reachable. It reads TME's requirement CSV and simulates TME removing conflicting conditions.
- `CAConditions`: values a condition change (hazard, deposits, habitability). Both terraforming planners use it.

## Mod buildings: the probe colony and diagnostics

- `CASimulator`: builds the mod building on a probe market with the colony's size and vanilla conditions.
- Copied conditions: one that fails without the intel it came with (Pather cells, pirate activity, a blockade) is left off.
- Reads: supply and demand, and market changes (stability, accessibility, income and upkeep multipliers, ground defense, fleet size and patrols, ship quality).
- Upgrades: measured as both buildings on the same probe.
- Failure: if the code throws or registers sector scripts or intel, the building is "can't be measured" and what it added is removed.
- Limits: effects over time (monthly items, spawned fleets) are not seen, so measured values lean low.
- `CADiagnostics`: on every load, rates every building, condition and item: Supported (formula or estimate), Measured, Analyzed (not acted on), Quarantined. `CADiagnostics.usable(id)` is the engine's gate.
- Output: the empire panel's Diagnostics page, `[ColonyAutopilot]` lines in `starsector.log`, and `saves/common/colony_autopilot_diagnostics.json.data`.

## How the optional mods stay optional

- Requirement: no other mod is required, and any mix works.

Pattern 1, compiled against the mod, called only when it is enabled:
- Classes: `CAIndEvo`, `CACourier` (Industrial Evolution); `CADefense`, `CASpies`, `CAAutonomy`, `CAWar`, `CAExpansionNex`, `CADiplomacy`, `CAConquest`, `CAScorched`, `CAWarListener`, `CAWarPage` (Nexerelin); `CASettlement` (RAT); `CAAugments` (Station Augments).
- Guard: each is called only inside an `isModEnabled(...)` check. The JVM loads a class on first use.
- `MOD` constants: compile-time constants, so reading `CAAugments.MOD` does not load `CAAugments`.
- Rule: never reference these classes outside their guard. `CAWarListener` is transient, so a save loads without Nexerelin.

Pattern 2, by name at run time:
- Classes: `CATme` (Terraforming Made Easy 2.9.8 and 2.10.0); `CAAotD`, `CASynergies`, `CAContracts`, `CAHypershunt`, `CAMegastructures`, `CAResearch`, `CADevelopment`, `CAWonders`, `CAProjects` (Ashes of the Domain); `CATasc` (Terraforming & Station Construction); `CAGrand`, `CALuna` (other mods' LunaLib settings); `CAScreenButton`, `CABuildHints` (the game's own UI).
- Missing names: the class does nothing and logs the reason once.
- `CAAotD`: if it cannot read Building Menu Overhaul's rules, nothing is built while BMO is enabled.

`CARefl`:
- Reason: the game's script class loader refuses any class that refers to `java.lang.reflect` or file classes. Plain reflection passes desktop tests and fails in game.
- Method: `java.lang.invoke` method handles, with `Method` and `Field` held as plain `Object`s and a full-privilege `MethodHandles.lookup()`.
- `fieldValues`: reads every field of a game object, private ones included, for obfuscated names. Callers pick by type.

Pattern 3, ids only:
- Classes: `CAMods` (VIC, Planetary Shield, Roider Union, Unknown Skies, What We Left Behind, Astral Ascension and others), `CABrewery` (Substance.Abuse), `CARogue` (Rogue Planets), `CAKadur` (Kadur Remnant), `CAIndexMods` (the Mod Index batch), `CAOddisz` (Oddisz's mods), `CAPanopticon` (Emergent Threats), `CAOmega` (Omega Core Everywhere, Astral Ascension Renewed).
- Behavior: compare condition, industry and item ids as strings. Inert without their mods.
- Any other mod: deposits and colony items work through the game's tables.

## Surviving game and mod updates

| Class | What it does |
|---|---|
| `CASurvival` | 0.98.5a rules (survival mode corruption, Waystation cores, parts-based d-mod repairs) through method handles. On 0.98a every answer is the 0.98a one. |
| `CACompat` | Health check at load. Each integration lists its mod, the version it was written against, the ids it needs and a check that reflected names resolve. Status: OK, Newer (trusted a bit less), Partly off, Off. |
| `CALinkScan` | Reads Auto Colonies' own class files and looks up every name it uses in Nexerelin, Industrial Evolution, RAT, Station Augments and TME. A renamed method shows in Diagnostics, and only the part that calls it stops. |
| `CAStep` | Isolates and times every daily step. 3 failed days in a row stop it for the session. |
| `CAConst` | Reads numbers from a mod's public static fields, with the original value as fallback. |
| `CACalibrate` | Weekly, compares predicted upkeep and output with real buildings. Smooths 70/30, clamps to 0.5-2x, applies after two readings. Saved. |
| `CABreaker` | Checks that an action into another mod took effect. Three failures in a row stop it for the session. |
| `CAEngine.trust` | An untested mod version lowers trust. Auto asks for approval on a big build that rests on a low-trust estimate. |

## What's saved

- `CAData`: under the persistent-data key `jt_colony_autopilot`. Plain values only.
- `colonies`: one `Colony` per market id: goal, mode, locks, pending proposal, story points used, policy times, threat state, augments, hypershunt and megastructure settings, fast-buildout state.
- `settings`: `CASettings` (budget, caps, fees, switches, mode defaults, floors, enforcer patrols, approval interval, the action log). A new save is filled from `colony_autopilot.json` by `CASettingsFile`.
- `stores`, `shipments`: each colony's Autopilot Storage cargo and shipments in transit.
- `noted`, `notedAt`: once-keys (`CALog.once`) with times. `keep:` keys are `CAMark` stamps that do not expire.
- Other fields: money tier and rung spending, tombstones, orders (ships, stockpiles, expansion, objectives, TASC), war state, calibration ratios, agents. `CASettings` lists every setting with its default.
- Story mode (`CASync`): under the persistent-data key `ac_story`. The save's mode, the core's state and timers, PP, fed cores, abilities bought, colonies bought, remembered settings and colony modes, beats seen. Costs: `data/config/colony_autopilot_story.json` (`CASyncCatalog`).

Rules for saves:
- Schema: `CAMigrate.SCHEMA` is 5. `CAMigrate` moves an older save forward once, stamped first so a failure is not retried. Nothing is raised above an older version's behavior, except settings the player never changed, which follow the new mode defaults.
- Aliases: `CAXStream` gives every saved class a fixed `JCM-` alias. A released alias is never renamed or reused.
- New fields: load as zero, false or null in older saves, so defaults mean off. Fields that must default on use an inverted `noX` name. Collections are created on first use.
- Removed fields: the game's save loader fails on a field the class lacks, so a retired field stays listed in `CAXStream` with `omitField`.
- Other mods' objects: only ids, numbers and timestamps are stored.
- Removal: a save made with the mod enabled does not load without it (saved classes and the Autopilot Storage tab).

## Files by area

Core:

| File | What it does |
|---|---|
| `JTModPlugin` | Starts everything on each save load |
| `CAScript`, `CAAuto` | Frame checks and the daily pass; `buildPass` behind `legacyPlanner` |
| `CAEngine`, `CAEstimates` | Scoring: candidates, effects, estimates, shortages, trust, upkeep, goal weights |
| `CAPlan`, `CAStations` | Target layouts, the plan bonus, station tiers |
| `CABuild`, `CABuildout` | Starting builds like the build menu; the first-colony offer and fast buildout |
| `CABudget` | Monthly budget, reserve, pause on loss, fees. The only file that changes credits. |
| `CAThreat`, `CASiege` | Threat per colony with hysteresis; siege detection |
| `CACrisis` | Colony crises: progress priced in credits, AI core costs, likely targets, Pather relief, inspection orders, the Colony crises page |
| `CALowProfile` | Low profile: whose crisis can still come, the Pather line, pulling items and swapping administrators |
| `CAItems`, `CAStores`, `CAStoragePlugin`, `CALogistics` | Colony items, AI cores, administrators, lamp, item swaps; Autopilot Storage and shipping; storage logistics to the refit and sell hubs |
| `CAOverLimit`, `CAStability` | Industry limit and the 0-stability net; `canLose` keeps every colony at or above the floor |
| `CASurplus`, `CAEndgame`, `CACapital` | Surplus investment into the focus world; endgame economy; the capital and its gathering point |
| `CAPolicies`, `CAStory`, `CAObjectives`, `CAPrune` | Hazard pay and free port; story point improvements; stable locations; taking down industries that stopped paying |
| `CALog`, `CALogFilter` | The action log. `did` (actions) and `advise` (advice) show on screen; `think` (what was weighed) goes to the Thinking list and `starsector.log`. `CALogFilter` sorts lines from older call sites. |
| `CAEnforcers` | Enforcer patrols: colony patrols attack enforcement fleets in your systems. Every setting off by default. |

Planner and money:

| File | What it does |
|---|---|
| `CAValue`, `CAHorizon`, `CAMoneyCurve` | `add`, `keep`, `deficits`, `npv`; the payback horizon; the money taper |
| `CAPlanner`, `CAAction` | The one queue: slot by NPV per day, money by NPV per credit, earmark, upkeep hold, re-scoring |
| `CAFloors` | The nine production floors and what each pick adds toward them |
| `CATombstone` | Removal marks, amounts paid, teardown loss |
| `CARich`, `CAEconomy` | Money tiers T0-T4, monthly pool, rung order and modes, proposals; the Economy rung's rules |
| `CAShipyard`, `CAStockpile`, `CAMilitary` | Ladder rungs: Doctrine hulls into custom production; stockpiles and military goods; station and military building upgrades |

Modes, panels and settings:

| File | What it does |
|---|---|
| `CAModeTable`, `CAPresets` | Per-mode defaults and player-set tracking; preset rows (`apply`, `match`) |
| `CAApprovals`, `CAApprovalDialog` | The list of waiting decisions; the summary dialog |
| `CABuildHints`, `CAOptionProvider`, `CAScreenButton` | Advisor panel beside the build menu; the colony menu option and tooltip line; the button on the colony screen |
| `CAColonyDialog`, `CAIntel`, `CANav` | Colony panel (Overview, Plan and builds, Locks, Mod features); empire panel (Colonies, Storage, Colony crises, Expansion, War, Mod features, Settings, Factions, Log, Diagnostics); the page list |
| `CASetupUI`, `CASettings`, `CASettingsFile` | The Settings page and first-time setup (text in `DESC` and `HOVER`); every setting; defaults from the config file |
| `CAFactionsPage`, `CAStance`, `CAWarPage`, `CAExpansionPage` | Faction stances (pure, no Nexerelin classes); War page; Expansion page |
| `CATheme` | Look (`heading`, `button`, `readout`, frames) and the log voice (`CODE / Place: what`) |

Expansion and war:

| File | Mod | What it does |
|---|---|---|
| `CAExpansion` | Any | Auto-expansion: gates, target scan, launch, crew founding, deliveries, administrators, refunds |
| `CAExpansionNex` | Nexerelin | Colony fleets, paid once the expedition is running |
| `CAWarPolicy` | Any | War level rules, allowed operations, fees, infamy, bombardment and invasion scores. Pure. |
| `CAWar`, `CADefense`, `CADiplomacy`, `CAConquest`, `CAScorched`, `CASpies`, `CAAutonomy` | Nexerelin | War fleets; emergency defense; ceasefire, peace and alliances; invasions, retakes and conquered colonies; saturation bombardment at War level Scorched, once authorized; agents; emergency autonomy |

Integrations:

| File | Mod | What it does |
|---|---|---|
| `CATme`, `CATerraform`, `CAWorldPlanner`, `CATerraformSurplus`, `CATerraSim`, `CAConditions` | Terraforming Made Easy | TME by name; projects; special-world plans; surplus terraforming on a simulation of TME's rules; condition values |
| `CAIndEvo`, `CACourier` | Industrial Evolution | Ambassadors, edicts, VPC chips, specialties; Courier Port shipping |
| `CAAotD`, `CASynergies`, `CADevelopment`, `CAResearch`, `CAHypershunt`, `CAMegastructures`, `CAWonders`, `CAProjects`, `CASurplusResearch` | Ashes of the Domain | BMO locks and upgrade paths; synergies; management styles; research; Coronal Hypershunt; Pluto and Nidavelir; wonders and gates; special project supply; surplus Research Facilities |
| `CAContracts` | AotD: Theory of Toolbox | Trade contracts |
| `CASettlement` | Random Assortment of Things | Frontiers settlement |
| `CAAugments`, `CAAugmentModel`, `CAStationKit` | Station Augments | Augment loadouts within the station's points; what each augment does |
| `CATasc`, `CATascStations` | Terraforming & Station Construction | Projects; mining and siphon stations founded by a crew |
| `CAGrand` | Grand.Colonies | 12-building limit; extra upkeep |
| `CABrewery` | Substance.Abuse | Brewery only with a recipe in storage |
| `CAPanopticon`, `CAOmega` | Emergent Threats; Omega Core Everywhere, Astral Ascension Renewed | Panopticon cores; Omega core placement |
| `CAMods`, `CARogue`, `CAKadur`, `CAIndexMods`, `CAOddisz` | See [Supported mods](supported-mods.md) | Rules by id; buildings left to the player (`CAMods.LEFT`) |
| `CAMarket` | Any | Market model: share gained by a new producer, weekly check against the game |

Foundation:

| File | What it does |
|---|---|
| `CAData`, `CAMigrate`, `CAXStream` | Saved data, moving old saves forward, save aliases |
| `CAReset` | Clears every static cache, timer and flag on load |
| `CATime`, `CADay` | One clock from the sector clock; day numbers for daily caches |
| `CATuning` | Own numbers in one place |
| `CACache`, `CAPerf`, `CATidy`, `CAMark` | Day caches; time per step; weekly tidy-up of saved data; saved stamps |
| `CAStep`, `CABreaker`, `CACompat`, `CALinkScan`, `CAConst`, `CACalibrate`, `CASurvival` | See [Surviving game and mod updates](#surviving-game-and-mod-updates) |
| `CASimulator`, `CADiagnostics`, `CAModWatch`, `CALogWatch` | Probe colony; ratings and the report; per-mod verdicts; log lines for the Diagnostics page |
| `CARefl`, `CALuna` | Reflection without `java.lang.reflect`; other mods' LunaLib settings |
| `JTCampaignScript`, `JTPatronageCondition` | Remove the retired `jt_patronage` condition from old saves |

## Conventions

- Fail closed: if something cannot be read, nothing is done there and the reason is logged once.
- Player's things: locked or hidden buildings, items or augments the player installed, the research queue and agents the player hired are not touched.
- Game's way: builds, projects, contracts, agent orders and settlements start the way the game's or the mod's UI starts them, and cost what that UI charges.
- Steps: every daily step goes through `CAStep.run` or `CAStep.guard`.
- Stability: anything that can cost stability asks `CAStability.canLose` first.
- Payment: credits move only in `CABudget` (`take`, `give`). Callers use `pay`, `charge`, `payPrice`, `payFromPool` or `refund`. An offline check fails any other file that changes credits.
- Log: new lines use `CALog.did`, `advise` or `think`.
- Save safety: saved data holds only ids, numbers and timestamps. A new saved class needs an alias in `CAXStream`.
- Reflection: no `java.lang.reflect` or file classes in mod code. Use `CARefl` or method handles.
- Format strings: `addPara` treats text as a format string. A literal `%` is written `%%`, and dynamic text goes through `CALog.esc`.
- Comments: say why, and name the version a number or rule comes from (such as "from VoK 5.0.7's code").

## Building and checking

- Compiler: JDK 17, `--release 17`.
- Core jars: `starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `log4j-1.2.9.jar`, `json.jar`, `lwjgl.jar`, `lwjgl_util.jar`.
- Optional mod jars: Industrial Evolution, Nexerelin, Random Assortment of Things, Station Augments, plus LazyLib's Kotlin runtime and LunaLib for the last two.

```
javac --release 17 -cp "<core jars>;<optional mod jars>" -d build <all .java files under src>
jar cf jars/autocolonies.jar -C build data
```

The author's checks run before each release. The scripts are local and are not in this repo.

1. Format check: no stray `%` in `addPara` text.
2. Banned-class scan: no class refers to anything the game's script loader refuses.
3. Bytecode verification under `-Xverify:all`, with and without the optional jars, on Java 17 and on the Java 28 the game runs on.
4. Loader check: the jar loads through the game's script class loader, every class resolves, and `CARefl` is exercised.
5. Combination audit: only the expected classes reference each compiled-in mod, every caller checks `isModEnabled`, and the jar links against all 16 combinations of the four mods.
6. Name checks: reflected names are checked against the AotD, TASC, LunaLib and Nexerelin jars.
7. Offline tests: 86 suites in 0.3.3.4, including the planner over a recorded colony state, money tiers and ladder, production floors, modes and approvals, Auto-expansion, stances and War levels, a 2-year war simulation, save migration, and the credits scan.
