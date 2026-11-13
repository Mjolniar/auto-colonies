# Source guide

- Language: Java 17, compiled against Starsector 0.98a-RC8's `starfarer.api.jar`.
- Size: about 41,000 lines in 118 files (0.3.0.0).
- Class comments: every class opens with a comment saying what it does and why.
- Player-facing documentation: [Getting started](getting-started.md), the guides linked from the README, and [details.md](details.md).
- [Auditing the mod](audit.md): every way the mod changes the game and what each one charges.
- [Automating Starsector colonies](automation-primer.md): game API reference for colonies, buildings, items, cores and the economy, with the traps found.

> The code was written by AI coding agents (see the README's warning). It is compiled and checked offline (see [Building and checking](#building-and-checking)). Parts are confirmed in game, and not all of it.

## Contents

- [Layout](#layout)
- [The shape of it in one paragraph](#the-shape-of-it-in-one-paragraph)
- [Where to start reading](#where-to-start-reading)
- [A day in game](#a-day-in-game)
- [How a building is scored](#how-a-building-is-scored)
- [Plans](#plans)
- [Mod buildings: the probe colony and diagnostics](#mod-buildings-the-probe-colony-and-diagnostics)
- [How the optional mods stay optional](#how-the-optional-mods-stay-optional)
- [Surviving mod updates](#surviving-mod-updates)
- [What's saved](#whats-saved)
- [The panels](#the-panels)
- [Every file](#every-file)
- [Conventions](#conventions)
- [Building and checking](#building-and-checking)

## Layout

| Path | What's there |
|---|---|
| `mod_info.json` | Mod id `jonah_tweaks`, version, jar, mod plugin |
| `src/data/scripts/jonahtweaks/` | The mod plugin and two small classes from the 1.x tweaks mod |
| `src/data/scripts/jonahtweaks/autopilot/` | All other classes. `CA` stands for Colony Autopilot. |
| `data/config/colony_autopilot.json` | Default settings for a new save (commented) |
| `data/campaign/submarkets.csv` | Registers the Autopilot Storage tab |
| `data/campaign/market_conditions.csv` | Registers the retired `jt_patronage` condition so old saves load |
| `jars/jonahtweaks.jar` | The compiled code the game loads |

- Package and jar names: `jonahtweaks`, the same as the mod id, so existing saves load.

## The shape of it in one paragraph

- Daily pass: a script runs one pass over the colonies once a day. Each step goes through `CAStep`, so one failure does not skip the rest.
- Scoring: for each Approve or Auto colony, the engine lists every building and upgrade it could start and values each in credits a month, weighted by the colony's goal.
- Planner (0.3.0): turns each value into a net present value over the payback horizon (36 months, up to 120). It queues every module's offers together and starts the best value per construction day and per credit.
- After the planner: money tiers and the money ladder, Auto-expansion, and independent steps that each run only when their mod is enabled.
- Actions: every action uses the same game calls as the game's UI (or the source mod's UI). It charges what that UI charges plus a convenience fee through `CABudget`, and is logged.
- Other mods: reached by reflection or class name, fail closed, and checked at load.

## Where to start reading

1. [JTModPlugin](../src/data/scripts/jonahtweaks/JTModPlugin.java): on every save load it creates the saved data, runs the diagnostics scan, registers the colony menu option and the intel panel, and adds the daily script.
2. [CAScript](../src/data/scripts/jonahtweaks/autopilot/CAScript.java): calls `CAAuto.runPass` once per in-game day and checks every frame whether to offer the fast buildout.
3. [CAAuto](../src/data/scripts/jonahtweaks/autopilot/CAAuto.java): the daily pass and the order of every step.
4. [CAEngine](../src/data/scripts/jonahtweaks/autopilot/CAEngine.java): scores every building a colony could build or upgrade to.
5. [CAValue](../src/data/scripts/jonahtweaks/autopilot/CAValue.java) and [CAHorizon](../src/data/scripts/jonahtweaks/autopilot/CAHorizon.java): one value for building and for keeping; the payback horizon.
6. [CAPlanner](../src/data/scripts/jonahtweaks/autopilot/CAPlanner.java): the one queue.
7. [CAPlan](../src/data/scripts/jonahtweaks/autopilot/CAPlan.java): the target layout per colony.
8. [CABuild](../src/data/scripts/jonahtweaks/autopilot/CABuild.java): starts a build the way the build menu does.
9. [CARich](../src/data/scripts/jonahtweaks/autopilot/CARich.java): money tiers and the money ladder.
10. [CAColonyDialog](../src/data/scripts/jonahtweaks/autopilot/CAColonyDialog.java) and [CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java): the two panels.

- Integration classes: readable on their own after those.

## A day in game

- `CAScript.advance`: calls `CAAuto.runPass(now)` once a game day, on the game's clock (`CATime`).
- Theory of Toolbox: if its background threads are reading markets, the pass waits, for at most a second day.
- `CAStep.run`: isolates and times a step and stops it for the session after 3 failed days in a row. `CAStep.guard` isolates without the breaker, for the safety steps.

The pass, in order:

1. Month and weekly steps: `CABudget.rollMonth`, the upkeep cap check, `CACalibrate.run`, `CAModWatch.weekly`, `CATidy.weekly`, `CAMarket.weekly`.
2. Daily state: `CASiege.update`, `CAStores.daily`, `CALogistics.daily`, `CACrisis.daily`, `CASurplus.daily`, `CACapital.daily`, `CAEndgame.daily`, `CAProjects.update`.
3. Colony lists (guarded): Auto colonies, those not under siege, every colony not Off.
4. 0-stability safety net (guarded): `CAOverLimit.run`, before anything is built.
5. Planner: `CAPlanner.run` collects `CAAction`s from builds and upgrades (`CAEngine`), surplus terraforming, surplus research, the wonder site, stable-location crews and story points. It scores each by NPV (`CAValue.npv`, `CAHorizon`).
   - Per colony: new buildings go through one construction queue by NPV per construction day. Upgrades run beside it.
   - Across colonies: money goes by NPV per credit. The first pick that waits for money has an earmark.
   - New-upkeep cap: counts only upkeep a building does not cover itself.
   - After each start: actions that share a colony, good, building or one-per-empire flag are re-scored.
   - Approve colonies: get the best pick as a proposal. A rejected one is held off for 30 days.
   - Removal marks (`CATombstone`): keep a building taken down in the last year out of that colony's list.
   - Fresh look: when something starts or finishes, a slot opens, threat or siege changes, the month rolls over or the money tier changes. Otherwise weekly (`CAAuto.wait`).
   - Fallback: with the hidden `legacyPlanner` switch, or after 3 failed planner days, the 0.2.45 build pass (`CAAuto.buildPass`) runs.
6. Money tiers and ladder: `CARich.run`, then the rungs in the preset's order (`CAStockpile`, `CAMilitary`, `CAShipyard`).
7. Auto-expansion: `CAExpansion.run`.
8. Removers and placement: `CAPrune`, `CALowProfile`, `CAItems.run` (guarded), `CAStoragePlugin.evict`.
9. Mod steps, each only when its mod is enabled: `CATerraform`, `CATerraformSurplus`, `CASurplusResearch`, `CAWonders`, `CAIndEvo`, `CACourier`, Nexerelin steps (`CADefense`, `CAWar`, `CASpies`, `CADiplomacy`, `CAConquest.tend`, `CAAutonomy`; all guarded), `CAPolicies` (guarded), `CAStory`, `CAObjectives`, `CATascStations`, `CASettlement`, `CAAugments`, `CAContracts`, `CAHypershunt`, `CAMegastructures`, `CADevelopment`, `CAResearch`, `CATasc`.
10. Panel scores and report: `CAEngine.fill` keeps every managed colony's score for the panels. `CADiagnostics.flushReport` writes the report once a pass.

- Credit reserve: nothing in the pass spends below it.
- Budget exceptions: emergency defense and relief fleets and an administrator for a colony past the colony limit may pass the monthly budget. They keep the reserve.
- Transfers between colonies: items, cores and research databanks go through `CAStores.ship` for a fee and travel time.

## How a building is scored

- `CAEngine.evaluate`: builds a list of `Pick`s, one per building the colony could start and each upgrade of a building it has.
- Outposts: `CAEngine.isOutpost` (Nexerelin's `$nex_playerOutpost` flag or tag). `evaluate` returns nothing for them.
- Skipped candidates: buildings the colony has or has an upgrade of (`CAPlan.hasOrBetter`), population, hidden or menu-less buildings, upgrade-only targets, a second station, anything `isAvailableToBuild()` refuses, and with Building Menu Overhaul anything its menu hides or locks (`CAAotD.allows`).
- `Pick.fx[7]`: credits per month, stability, accessibility, defense, fleet size, ship quality, growth. `Pick.lines`: the reasons the panels show.
- Source 1, game formulas (`CAEngine.effects`, `deposits`): vanilla buildings. Deposit industries read the tables `ResourceDepositsCondition` uses.
- Source 2, estimates (`CAEstimates`, `CAEngine.estimate`): hand-written effects for mod buildings, marked "(estimated)".
- Source 3, measurements (`CASimulator`): a building with no formula or estimate is built on a probe colony and the difference is read.
- Source 4, adjusters: `CADevelopment.adjust`, `CASynergies.score`, `CARogue.score`, `CAMods.apply`, `CAGrand.penalty`, `CABrewery.apply`.
- Shortages: `CAEngine.empireDeficits()` adds up unmet demand plus what AotD trade contracts lack. Supplying a short commodity is worth more.
- Trust (`CAEngine.trust`): confidence 1 for game formulas, 0.9 for measurements, 0.85 for estimates (0.92 once checked against the colonies), 0.05 less for an untested mod version. Positive value is scaled by confidence.
- Upkeep (`CAEngine.upkeep`): the spec's upkeep times max(1, size - 2) times the colony's upkeep multiplier, times the `CACalibrate` factor. `CACalibrate.migrate` clears corrections that saves from before 0.2.8.0 learned.
- Weights (`CAEngine.finish`): `fx` times the goal's row in `WEIGHTS` (Balanced, Money, Security, War production, Rapid growth, Self-sufficiency). The stability floor applies.
- Pollution: an upgrade that would pollute a habitable world is shown negative and not started. `CAItems.wouldPollute` blocks polluting items in its forge. `CAItems.pullPollutingForge` takes one back while reversible.
- Plan bonus (`CAPlan.bonusFor`): the flat bonus goes only to enabler steps. Other steps count what they earn plus half of what they lead to (`CATuning.DOWNSTREAM_SHARE`).
- Money weight: from the money tier Idle money, a credit counts half at Approve and Auto colonies, a quarter while spending freely (`CASurplus.moneyWeight`) and 5% in the endgame.
- Price side (0.3.0): score = worth - (price + fee) / H - worth x construction days / 30 / H (`CAHorizon.npvMonthly`, `CAValue.npv`).
- Horizon H: 36 months, rising to 120 as surplus credits reach 10,000,000. It falls only below 60% of what raised it (`CAHorizon.slide`).
- `CAValue`: `add` values new builds and `keep` values existing buildings, so builders and removers agree. `keep` is what the colony loses a month without the building, plus the shortage it leaves, plus the synergies it keeps. `CAValue.deficits` counts buildings under construction as supply.
- `CATombstone`: adds the teardown loss (the part of what Autopilot paid that has not paid back over H) and one-year removal marks.
- `Result`: `picks` (sorted), `blocked` (why nothing can start, such as a build in progress or 12 buildings without Grand.Colonies), picks lacking only an industry slot, and counts of locked and unsupported options.
- `CADiagnostics.quarantine`: an exception thrown by a building's code during scoring quarantines that building.

## Plans

- `CAPlan`: gives each colony a target layout from its planet, hazards, resources and goal. Steps are builds, conditions to reach, or notes.
- `bonusFor`: favours picks that advance the plan, the earliest open step most (enabler steps only).
- World plan: one that already paid for terraforming switches only to one worth at least twice as much. `CAMark` keeps the stamp.
- `hasOrBetter`, `leadsTo`: follow upgrade chains and AotD's downgrade links.
- `CAWorldPlanner`: adds a Terraforming Made Easy special world (Forge, Fortress, Paradise, Hive, Death, Waste) when reachable. It reads TME's requirement CSV, resolves needAll/needOne requirements through the projects that create them, and simulates TME removing conflicting conditions.
- `CAWorldPlanner`: It does not link against TME's code.
- `CAConditions`: values a condition change (hazard, deposits, habitability). Both terraforming planners use it.

## Mod buildings: the probe colony and diagnostics

- `CASimulator`: builds the mod building on a probe market with the colony's size and vanilla conditions and no planet or location.
- Copied conditions: one that fails without the intel it was added with (Pather cells, pirate activity, a blockade) is left off.
- Reads: the building's supply and demand, and market-level changes (stability, accessibility, income and upkeep multipliers, ground defense, fleet size and patrols, ship quality).
- Upgrades: measured as both buildings on the same probe.
- Failure: if the code throws or registers sector scripts or intel, the building is reported "can't be measured" and what it added is removed.
- Limits: effects that happen over time (monthly items, spawned fleets) are not seen, so measured values lean low.
- `CADiagnostics`: runs on every load and rates every building, condition and item: Supported (formula or estimate), Measured, Analyzed (cannot be measured; not acted on), Quarantined (code failed during planning). `CADiagnostics.usable(id)` is the engine's gate.
- Output: the empire panel, `[ColonyAutopilot]` lines in `starsector.log`, and `saves/common/colony_autopilot_diagnostics.json.data`.

## How the optional mods stay optional

- Requirement: no other mod is required, and any mix works. Three patterns provide this.

Pattern 1, compiled against the mod, called only when enabled:
- Classes: `CATerraform` (TME), `CAIndEvo` and `CACourier` (Industrial Evolution), `CADefense`, `CASpies`, `CAWar`, `CAConquest`, `CAScorched`, `CADiplomacy`, `CAWarListener`, `CAWarPage` and `CAExpansionNex` (Nexerelin), `CASettlement` (RAT), `CAAugments` (Station Augments).
- `CAWarListener`: transient, so a save loads without Nexerelin.
- Guard: each class is called only inside an `isModEnabled(...)` check. The JVM loads a class on first use.
- `MOD` constants: compile-time constants, so checking `CAAugments.MOD` does not load `CAAugments`.
- Rule: do not reference these classes outside their guard.

Pattern 2, reflection only:
- Classes: `CAAotD`, `CASynergies`, `CAContracts`, `CAHypershunt`, `CAMegastructures`, `CAResearch`, `CADevelopment` (Ashes of the Domain); `CATasc` (Terraforming & Station Construction); `CAGrand` (Grand.Colonies' LunaLib settings).
- Lookup: other mods' classes and members are found by name at runtime. If a name is not found, the class does nothing and logs the reason once.
- `CAAotD`: if it cannot read Building Menu Overhaul's rules, nothing is built while BMO is enabled.

`CARefl`:
- Reason: Starsector's script class loader refuses any class that refers to `java.lang.reflect` or file classes ("File access and reflection are not allowed to scripts"). Plain reflection compiles and passes desktop tests, then fails in game.
- Method: `CARefl` uses `java.lang.invoke` method handles and holds `Method` and `Field` as plain `Object`s. It loads the reflection classes by name from the boot class loader and uses a full-privilege `MethodHandles.lookup()` (`publicLookup()` refuses the caller-sensitive `getMethod`, `invoke`, `get`, `set`).
- `fieldValues`: reads every field of a game object, private ones included, for obfuscated names. Callers pick by type.
- `CATasc`: uses `MethodHandles.publicLookup().findStatic`. Plain `getMethod` needs Illustrated Entities' classes.

Pattern 3, literal ids only:
- Classes: `CAMods` (VIC, Planetary Shield, Roider Union, Unknown Skies, What We Left Behind, Astral Ascension), `CABrewery` (Substance.Abuse), `CARogue` (Rogue Planets: condition ids, memory keys and its public numbers by name through `CAConst`), `CAKadur` (Kadur Remnant) and `CAIndexMods` (Mod Index batch 1). They compare ids as strings and are inert without their mods.
- Generic support: deposits and colony items from any mod work through the game's tables.

## Surviving mod updates

| Class | What it does |
|---|---|
| `CACompat` | Health check at load. Each integration lists its mod, the version it was written against, the ids it needs and a check that reflected names resolve. Status: OK, Newer (trusted a bit less), Partly off, Off. |
| `CALinkScan` | 0.3.0: reads Auto Colonies' own class files from the class loader (constant pool) and looks up every name in Nexerelin's, Industrial Evolution's, RAT's, Station Augments' and TME's packages with method handles. A renamed method shows in Diagnostics at load. Only the part that calls it stops. |
| `CAStep` | 0.3.0: isolates and times every daily step. 3 failed days in a row stop it for the session. Safety steps use `guard`. |
| `CAConst` | Reads numbers from a mod's public static fields, with the original value as fallback. A rebalance is picked up and logged. |
| `CACalibrate` | Weekly, compares predicted upkeep and output with real plain buildings. Smooths the ratio (70/30), clamps to 0.5-2x, applies after two readings. Saved. |
| `CABreaker` | Checks that an action into another mod took effect. Three failures in a row stop it for the session. |
| `CAEngine.trust` | An untested mod version lowers trust. Auto asks for approval on a big build resting on a low-confidence estimate. |

## What's saved

- `CAData`: stored under the persistent-data key `jt_colony_autopilot`. Plain values only.
- `colonies`: one `Colony` per market id (goal, mode, locks, pending proposal, story points used, policy timestamps, threat state, augments, hypershunt and megastructure settings, fast-buildout state).
- `settings`: `CASettings` (budget, caps, fees, switches, this month's spending, the last 100 action log entries). A new save is filled from `colony_autopilot.json` by `CASettingsFile`.
- `stores`, `shipments`: each colony's Autopilot Storage cargo and shipments in transit.
- `noted`, `notedAt`: once-keys (`CALog.once`) with times. `keep:` keys are `CAMark` stamps that do not expire.
- 0.3.0 fields: `schema`, `migratedAt`, `tombstones`, `sunk`, `richTier`, `rungSpent`, `shipyardOrders`, `stockpiles`, `expandOrders` and related `expand*`, `warDeclared`, `warOps`, `conquered`, `warAuthorized`, `warStandDown`. `CASettings` lists the settings fields with defaults.
- Other fields: calibration ratios (`calOut`, `calUp`), spy agents and roles, `attackers`, `endgameBuilt`, `augRefit`, `settleExpedition*`, `surplusBuilt`, `surplusResearch`, `enablers`.

Rules for saves:
- Aliases (0.3.0): `CAXStream` gives every saved class a fixed alias (`JCM-Data`, `JCM-Colony`, `JCM-Settings`, `JCM-Tombstone`, `JCM-ShipOrder`). A released alias is not renamed or reused.
- Compatibility: saves from before 0.3.0 load. Saves written with aliases do not load in 0.2.45.
- Migration: `CAMigrate` moves an older save forward once, stamped before the steps run so a failure is not retried. War fleets become the War level, faction priorities become stances, retake is off, and the per-project cap is kept as Fixed when it differs from Automatic.
- Migration: Nothing is raised above 0.2.45 behavior.
- New fields: missing in older saves, they load as zero, false or null, so defaults mean "off" or "default". Fields that must default on use an inverted `noX` name. Collections are created lazily by accessors such as `augAuto()`.
- Removed fields: the game's save loader fails on a field the class lacks. A retired field is deleted from the class and listed in `CAXStream` with `omitField` (`adminDelay`, `storageCheat`, `haFleetCap`, `nexAttackGap`, `richIncomeRef`). The line stays.
- Other mods' objects: only ids, numbers and timestamps are stored.
- Exception: Autopilot Storage is a market tab, so the mod cannot be removed from a save that used it.

## The panels

| Class | What it does |
|---|---|
| `CAOptionProvider` | Adds Colony Autopilot... to the right-click menu of Population & Infrastructure |
| `CAColonyDialog` | The colony panel. Pages (`pages()`): Overview, Plan and builds, Locks, Mod features (`modSections()`, `renderMod()`). `build()` redraws; `handle(id)` routes buttons. |
| `CAIntel` | The empire panel, an intel item with its own filter. Pages: Colonies, Autopilot Storage, Colony crises, Expansion, War (Nexerelin), Mod features, Settings, Factions, Log, Diagnostics |
| `CANav` | The page list both panels draw |
| `CATheme` | Look: `heading`, `button`, `readout`, `Frame` (GL frames in `renderBelow`). Voice: `machine` turns a log line into `CODE / Place: what`. |
| `CASetupUI` | The Settings page (20 main rows in 6 groups, a collapsed Advanced group of 11 subgroups) and the 5-row first-time setup (`renderFirstTime`). Text is in `DESC` and `HOVER`. `applyRecommended`, `resetToRecommended`. |
| `CAPresets` | 0.3.0: preset rows (Spending, Extras, New colonies, War, Story points, Stations, Take down buildings, Colony policies, AI cores, Administrators). `apply` writes the fields. `match` returns Custom when they fit no value. |
| `CAFactionsPage` | 0.3.0: stance table, one row per faction. No Nexerelin classes. |
| `CAWarPage` | 0.3.0 (Nexerelin): War level, fleet request points, infamy, Stand down, Recall all, Authorize bombardment, per-faction operations, conquered colonies. Drawing changes nothing. |
| `CAExpansionPage` | 0.3.0: Auto-expansion's page, drawn by `CAExpansion.render`. Found now asks first. |
| `CABuildout.Offer` | The first-colony offer, an interaction dialog |
| `CAStoragePlugin`, `CAStores` | The Autopilot Storage tab, one store per colony (`CAStores.of`), shipping (`CAStores.ship`, delivered by `CAStores.daily`). Campaign consumables and blueprints are refused (`consumable`). |


## Every file

Core:

| File | What it does |
|---|---|
| `JTModPlugin` | Starts everything on each save load |
| `CAAuto` | The daily pass; `buildPass` behind `legacyPlanner` |
| `CAEngine` | Scoring: candidates, effects, shortages, trust, upkeep, goal weights |
| `CAPlan` | Target layouts and the plan bonus |
| `CAThreat` | Threat estimate per colony (70% / 65% / 60-day hysteresis, +0.4 for the likely crisis target). `protection` keeps a station's augments economic. |
| `CACrisis` | Colony crises (Hostile Activity): monthly progress priced in credits (`pointCost`), AI core costs, `military`, `diktat`, likely targets, Pather relief, Hegemony inspection orders, the Colony crises page |
| `CABuild` | Starts a build or upgrade like the build menu |
| `CABudget` | Monthly budget (Automatic: half of net income plus 15% of credits above the reserve at month start; share of income; fixed), reserve, pause on loss, fees |
| `CALowProfile` | Low profile: whose crisis can still come, the Pather line (`pathBlocked`, `pathLimit`), daily `run` (`pullItem`, `swapAdmin`) |
| `CAItems` | Colony items, AI cores (`coreValue` within `coreLimit`), administrators (`adminWorth`), Orbital Fusion Lamp (`lampValue`, `pullLamp`), Antimatter Stabilizer conversion, item swaps (`swapItem`) |
| `CALogWatch` | log4j appender on Mjolniar's mods' loggers for the Diagnostics page. Other mods' status comes from sector memory `$jonahs_status_<mod id>`. |
| `CASurplus` | Surplus investment into the focus world (`focus`, `pick`, `MARGIN`, `committed`); `extra`, `projectCap`, `upkeepCap`, `costWeight` |
| `CAEndgame` | Endgame economy: `decide`, `floor`, `room`, `chainValue`, `weakest`, `replacement`, `takeDown` |
| `CASurplusResearch` | More Research Facilities from surplus (3 below money tier Rich, up to the 8 Vaults of Knowledge counts), taken down once research is done |
| `CAWonders`, `CAProjects` | AotD by reflection: Daedalus Array and Bifrost gates while spending freely; producers of special-project goods as plan steps |
| `CAPolicies` | Hazard pay and free port with a minimum time between changes; raises an old save's 10% hazard pay limit to 30% once |
| `CAStory` | Story point improvements; monthly cap `spMonth`, cheapest first (`pick`) |
| `CALog` | The action log. `once`/`forget` log once per save. `esc` makes text safe in format strings. `ping` is a screen-only line, once a week per key. |

Foundation (0.3.0):
- `CATime`: one clock from the sector clock. `due` and `fresh` treat a later stamp as stale.
- `CAReset`: clears every static cache, timer and flag on load. `JTResetCheck` fails on a new static that is not reset or listed.
- `CAStability`: `canLose` lets an action go when it costs no stability or the colony stays at or above the floor (at least 3).
- `CATuning`: own numbers in one place. `JTTuningCheck` pins every value.
- `CAMigrate`, `CAXStream`, `CACache` (day caches), `CAPerf` (ms per step), `CATidy` (weekly saved-data tidy-up), `CAMark` (saved stamps).

Planner and money (0.3.0):

| File | What it does |
|---|---|
| `CAValue` | `add`, `keep`, `deficits`, `boost`, `npv` |
| `CAPlanner` | The one queue: slot by NPV per day, money by NPV per credit, earmark, upkeep hold, re-scoring, `setAside` |
| `CATombstone` | Removal marks, amounts paid, teardown loss, `forRemoval`, the 0.2.45.1 reading as a pruning floor |
| `CARich` | Money tiers T0-T4 with 60% exit lines, monthly pool, rung waterfall, rung modes, Budget ladder line, proposals |
| `CAShipyard` | Shipyard rung: Doctrine priority hulls added to custom production (`FactionProductionAPI.addItem`), 2% fee, 6-hull stop, free-cash guard |
| `CAStockpile` | Stockpiles rung and military goods: buys at the nearest non-hostile market and ships into Local Resources |
| `CAMilitary` | Military rung: station tiers, Military Base to High Command, Patrol HQ to Military Base; one a day, within 15% of colony net in added upkeep |

Expansion and war (0.3.0):

| File | Mod | What it does |
|---|---|---|
| `CAExpansion` | Any | Auto-expansion: gates, target scan, launch, crew founding (`found`), deliveries, administrators, refunds, page content |
| `CAExpansionNex` | Nexerelin | Colony fleets (`ColonyExpeditionIntel`), paid after `init()` leaves the expedition running; `fixAdmin` |
| `CAWarPolicy` | Any | War level rules: `effective`, allowed operations, fees, payers, infamy line, bombardment penalties, invasion scores. Pure. |
| `CADiplomacy` | Nexerelin | Ceasefire and peace answers, alliance offers, keep-hostile, `declareWar` |
| `CAConquest` | Nexerelin | Invasions and retakes (`InvasionIntel`), gates, conquered colonies (`tend`) |

Integrations:

| File | Mod | What it does |
|---|---|---|
| `CATerraform`, `CAWorldPlanner` | Terraforming Made Easy | Projects; special-world plans |
| `CATerraformSurplus`, `CATerraSim` | Terraforming Made Easy | Surplus terraforming at Auto colonies, planned on a simulation of TME's rules |
| `CAIndEvo`, `CACourier` | Industrial Evolution | Ambassadors, edicts, VPC chips, specialties; Courier Port shipping |
| `CADefense`, `CASpies`, `CAAutonomy` | Nexerelin | Emergency defense fleets; agents; autonomy (`Nex_GrantAutonomy`) |
| `CAWar` | Nexerelin | War fleets by War level and stance, one launch counter a day; Stand down and Recall all |
| `CAAotD` | AotD: Building Menu Overhaul | Research locks, hidden buildings, upgrade paths |
| `CASynergies`, `CADevelopment`, `CAResearch`, `CAHypershunt`, `CAMegastructures` | AotD: Vaults of Knowledge | Synergies; management styles and Floating City fuel guard; research; Coronal Hypershunt (and shared restoration helpers); Pluto and Nidavelir |
| `CAContracts` | AotD: Theory of Toolbox | Trade contracts |
| `CASettlement` | Random Assortment of Things | Frontiers settlement |
| `CAAugments`, `CAAugmentModel`, `CAStationKit` | Station Augments | Augment loadouts (knapsack within the station's points); what each augment does on a station |
| `CATasc`, `CATascStations` | Terraforming & Station Construction | Projects, prerequisites, estimates; mining and siphon stations founded by a crew |
| `CAGrand` | Grand.Colonies | 12-building limit; extra upkeep |
| `CABrewery` | Substance.Abuse | Brewery only with a recipe in storage |
| `CAPrune` | Any | Takes down an Auto colony's industry whose `keep` value is below 0 for 30 days |
| `CAMarket` | Any | Market model: `snap`, `pickGain`, `claim`, `prune`, `weekly` check against the game's shares |
| `CAMods` | VIC, Planetary Shield, Roider Union, Unknown Skies, What We Left Behind, Astral Ascension and others | Small integrations by id; buildings left to the player (`LEFT`) |
| `CARogue` | Rogue Planets | Condition table and rules: build values, upkeep, plan filter, stability floor, cores, administrators, reactor and vault guards |
| `CAIndexMods` | JaydeePiracy, Corrupt.Officials, Tahlan Shipworks, Gladiator Society, Scy Nation, CJY's Toy Box, AI-Retrofits, Blue, Oculian Armada | Condition effects on Refining, Fuel Production and Farming; Corrupt.Officials' kept income share; new colony skips; buildings left to the player; ship producers |
| `CACapital` | Any | Picks the capital, keeps the production gathering point on it, puts its trade buildings (`COMMERCE`) first in its plan, frees an industry slot (`replacement`, `CAEndgame.takeDown`) |
| `CALogistics` | Any | Storage logistics: ships, weapons, wings and unknown blueprints to the refit hub, known blueprints and spares to the sell hub |
| `CAObjectives` | Any (TME and AotD add) | Stable locations: comm relays, nav buoys, sensor arrays, TME's Domain-era upgrade, AotD's Hypershunt Receivers |
| `CAPanopticon` | Emergent Threats: IX Revival | Panopticon Instance and Core into the Surveillance Center |
| `CAOmega` | Omega Core Everywhere, Astral Ascension Renewed | Omega cores into buildings or as administrators |

Leftovers:
- `JTCampaignScript`, `JTPatronageCondition`: remove the retired `jt_patronage` condition from old saves. No other effect.
- UI classes: see [The panels](#the-panels).
- `CAModWatch`: per-mod verdict (real output, formula checks, items, conditions) in the report and Diagnostics. `CASimulator`, `CADiagnostics`: see the probe colony section.

## Conventions

- Fail closed: if something cannot be read, the manager does nothing there and logs why once.
- Player's items: locked or hidden buildings, items or augments the player installed, the research queue and agents the player hired are not touched.
- Player's settings: settings the player changes on megastructures and the hypershunt stay the player's until handed back.
- Game's way: builds, projects, contracts, agent orders and settlements start the way the game or the mod's UI starts them. Where a mod's UI charges credits, so does the manager.
- `CAStep`: every daily step goes through `CAStep.run` or `CAStep.guard`.
- Stability: anything that can cost a colony stability asks `CAStability.canLose` first.
- Payment: credits are taken and given back only in `CABudget` (`take`, `give`). Callers use `pay`, `charge`, `payPrice`, `payFromPool` or `refund`. An offline check fails any other file that changes credits.
- Save safety: saved data holds only ids, numbers and timestamps. A new saved class needs an alias in `CAXStream`.
- Reflection: no `java.lang.reflect` or file classes in mod code. Use `CARefl` or method handles.
- Format strings: `addPara` treats text as a format string. A literal `%` is written `%%`, and dynamic text goes through `CALog.esc`.
- Comments: they say why, and name the version where a number or rule comes from another mod's code (such as "from VoK 5.0.7's code").

## Building and checking

- Compiler: JDK 17.
- Classpath, core jars: `starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `log4j-1.2.9.jar`, `json.jar`, `lwjgl.jar`, `lwjgl_util.jar`.
- Classpath, optional mod jars: Terraforming Made Easy, Industrial Evolution, Nexerelin, Random Assortment of Things, Station Augments, plus LazyLib's Kotlin runtime and LunaLib for the last two.

```
javac --release 17 -cp "<core jars>;<optional mod jars>" -d build <all .java files under src>
jar cf jars/jonahtweaks.jar -C build data
```

Before each release, the author's build checks run. The scripts are local and are not in this repo.

1. Format check: no stray `%` in `addPara` text.
2. Banned-class scan: no compiled class refers to a class the game's script loader refuses.
3. Bytecode verification under `-Xverify:all`, with and without the optional mods' jars.
4. Loader check: the jar is loaded through Starsector's script class loader, every class's members are resolved, and `CARefl` is exercised.
5. Combination audit: only the expected classes reference each optional mod, every caller checks `isModEnabled`, and the jar is linked against all 32 combinations of the compiled-against mods.
6. Name checks: every reflected name is checked against the AotD, TASC and LunaLib jars.
7. Offline tests: 75 suites in 0.3.0.0. They cover the augment knapsack, synergies, management styles, the planner over a recorded colony state, money tiers and ladder, Shipyard and stockpile limits, Auto-expansion, stance and War level rules, a 2-year war simulation (infamy under 75, credits above the reserve, one invasion at a time), presets, the 0.2.45 migration, loading a 0.2.45 save, and a scan that fails any file outside `CABudget` that changes credits.
8. Nexerelin names: every Nexerelin class and method the war and expansion code calls is checked against the installed jar.
