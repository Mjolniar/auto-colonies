# Source guide

A tour of the code for anyone who wants to read it: how it's laid out, what happens each day in game, how a building gets its score, how the optional mod integrations stay optional, what goes in the save, and what every file does.

All the code is Java 17, compiled against Starsector 0.98a-RC8's `starfarer.api.jar`. It's about 9,600 lines in 52 files. Every class opens with a comment saying what it does and why; this page links them together. For what the mod does as a player sees it, read [Getting started](getting-started.md), [What each supported mod adds](supported-mods.md) and [details.md](details.md).

> The code was written by AI coding agents (see the README's warning). It's compiled and checked offline (see [Building and checking](#building-and-checking)), and parts are confirmed in game, but not all of it.

## Contents

- [Layout](#layout)
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
| `mod_info.json` | Mod id `jonah_tweaks` (kept from the old name so existing saves load), version, jar, mod plugin |
| `src/data/scripts/jonahtweaks/` | The mod plugin and two small leftovers from Jonah Tweaks 1.x |
| `src/data/scripts/jonahtweaks/autopilot/` | Everything else. `CA` stands for Colony Autopilot. |
| `data/config/colony_autopilot.json` | Default settings for a new save (commented) |
| `data/campaign/submarkets.csv` | Registers the Autopilot Storage tab |
| `data/campaign/market_conditions.csv` | Registers the retired `jt_patronage` condition so old saves load |
| `jars/jonahtweaks.jar` | The compiled code the game loads |

The package and jar names are still `jonahtweaks` for the same reason as the mod id.

## Where to start reading

1. [JTModPlugin](../src/data/scripts/jonahtweaks/JTModPlugin.java). On every save load it creates the saved data, runs the diagnostics scan, registers the colony menu option and the intel panel, and adds the daily script.
2. [CAScript](../src/data/scripts/jonahtweaks/autopilot/CAScript.java). Once per in-game day it calls `CAAuto.runPass`, and every frame it checks whether to offer the fast buildout.
3. [CAAuto](../src/data/scripts/jonahtweaks/autopilot/CAAuto.java). The daily pass: what's built, what's proposed, and every other automated step.
4. [CAEngine](../src/data/scripts/jonahtweaks/autopilot/CAEngine.java). Scores every building a colony could build or upgrade to.
5. [CAPlan](../src/data/scripts/jonahtweaks/autopilot/CAPlan.java). The target layout each colony works toward.
6. [CABuild](../src/data/scripts/jonahtweaks/autopilot/CABuild.java). Starts a build the way the build menu does.
7. [CAColonyDialog](../src/data/scripts/jonahtweaks/autopilot/CAColonyDialog.java) and [CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java). The two panels.

After those, any integration class can be read on its own.

## A day in game

`CAScript.advance` adds up in-game days and calls `CAAuto.runPass(now)` once a day. If Theory of Toolbox's background threads are reading markets, it waits, but never past a second day. The pass:

1. **Month and calibration.** `CABudget.rollMonth` starts a new month's budget when the month changes. `CACalibrate.run` compares predictions with your real buildings once a week.
2. **Each colony in Approve or Auto:**
   - A colony is re-planned every 7 days, or as soon as a build finishes.
   - **Fast buildout:** a colony with one running goes to `CABuildout.pass` instead. That pass plans for Money, picks by value per credit, pays from credits above the floor, and repeats daily.
   - **Scoring:** `CAEngine.evaluate(market, CAThreat.effective(market, goal), minStability)` returns the scored picks. `CAThreat.effective` swaps in Security while the colony's threat estimate is high.
   - **Approve:** the best pick becomes a proposal (`CAAuto.propose`), and the player approves or rejects it in a panel.
   - **Auto:** the best new build and the best upgrade become candidates.
3. **Funding Auto candidates.** All candidates across the empire are sorted by score per credit, so the budget goes where it does the most good. Each is checked in turn:
   - over the per-project cap → proposed instead;
   - a measured mod building while that setting asks for approval → proposed;
   - a low-confidence estimate costing more than half the cap → proposed;
   - not enough budget → waits (logged once);
   - over this month's new-upkeep cap → waits.

   Otherwise `CAAuto.execute` starts it through `CABuild.start`, charges the fee and logs it.
4. **Everything else**, each step in its own `try`/`catch` so one failing integration can't stop the rest:

   | Step | Class |
   |---|---|
   | Items, AI cores and administrators | `CAItems.run` |
   | Terraforming Made Easy projects | `CATerraform.run` |
   | Industrial Evolution, then Courier Port shipping | `CAIndEvo.run`, `CACourier.run` |
   | Nexerelin emergency fleets, then agents | `CADefense.run`, `CASpies.run` |
   | Hazard pay and free port | `CAPolicies.run` |
   | Story points | `CAStory.run` |
   | RAT settlement | `CASettlement.run` |
   | Station augments | `CAAugments.run` |
   | Trade contracts | `CAContracts.run` |
   | Hypershunt, megastructures, Floating City, research | `CAHypershunt`, `CAMegastructures`, `CADevelopment`, `CAResearch` |
   | TASC terraforming | `CATasc.run` |

   Each runs only when its mod is enabled.

Nothing in the pass spends below the credit reserve. Emergency defense fleets are the one exception to the monthly budget, and they still keep the reserve.

## How a building is scored

`CAEngine.evaluate` builds a list of `Pick`s, one for each building the colony could start now and each upgrade of a building it has.

**Outposts first.** A Nexerelin player outpost (`CAEngine.isOutpost`: Nexerelin's `$nex_playerOutpost` market flag or entity tag) is in an economy of its own, so `evaluate` returns nothing for it, and the item placer, fast buildout, calibration and shortages skip it. These are skipped:
- the colony already has it, or something upgraded from it (`CAPlan.hasOrBetter`);
- population, hidden or menu-less buildings;
- upgrade-only targets;
- a second station;
- anything the building's own `isAvailableToBuild()` refuses;
- with Building Menu Overhaul, anything its build menu would hide or lock (`CAAotD.allows`).

**Effects.** A `Pick` collects effects in `fx[7]`: credits per month, stability, accessibility, defense, fleet size, ship quality and growth. It also collects human-readable `lines` that the panels show as the reasons. The effects come from one of four places:
- **The game's formulas** (`CAEngine.effects`, `deposits`): vanilla buildings are scored from what their code does. Deposit industries read the same tables the game's `ResourceDepositsCondition` uses, so deposit conditions from other mods count automatically.
- **Estimates** (`CAEstimates`, `CAEngine.estimate`): hand-written effects for specific mod buildings (AotD starters, TASC buildings and others). Their lines are marked "(estimated)".
- **Measurements** (`CASimulator`): for mod buildings with no formula or estimate, the building is built on a throwaway probe colony and the difference is read (see below).
- **Integration adjusters**, applied on top:
  - `CADevelopment.adjust`: AotD management styles;
  - `CASynergies.score`: AotD industry synergies;
  - `CAMods.apply`: VIC, Roider and others;
  - `CAGrand.penalty`: Grand.Colonies' extra upkeep;
  - `CABrewery.apply`: Substance.Abuse.

**Shortages.** `CAEngine.empireDeficits()` adds up unmet demand across all your colonies, plus what AotD trade contracts are short of. Supplying a commodity you're short of is worth more than supplying one you already export (`supply`, `demand`).

**Trust.** `CAEngine.trust` sets a `confidence` for each pick:
- game formulas: 1;
- measurements: 0.9;
- estimates: 0.85, or 0.92 once checked against your colonies;
- 0.05 less when a mod is a version its integration wasn't written for.

Positive value is scaled by confidence, so an uncertain gain has to beat a known one by a margin.

**Upkeep.** `CAEngine.upkeep` predicts the building's upkeep the way the game's `BaseIndustry.applyIncomeAndUpkeep` does: the spec's upkeep (already in credits, since the game multiplies the CSV value by 500 when it loads), times max(1, size - 2), times the colony's upkeep multiplier. That's then multiplied by the calibration factor from `CACalibrate`. (Before 2.8.0 it multiplied by 500 a second time; `CACalibrate.migrate` clears the corrections older saves learned from that.)

**Weights.** `CAEngine.finish` combines `fx` with the goal's weights (the `WEIGHTS` table, one row per goal: Balanced, Money, Security, War production, Rapid growth, Self-sufficiency). It then applies:
- the stability floor;
- a pollution penalty (smaller for War production);
- the plan bonus from `CAPlan.bonusFor`, only for picks worth something on their own, so the plan orders good buildings but never makes a money-losing one look good;

A pick that breaks a rule (such as the stability floor) is dropped; everything else is ranked and shown, worthwhile or not.

**The result.** `Result` holds:
- the sorted picks;
- `blocked`: why nothing new can start, such as a build in progress or 12 buildings without Grand.Colonies;
- picks that only lack an industry slot;
- counts of locked and unsupported options, which the panels show.

Any exception thrown by a building's code during scoring quarantines that building (`CADiagnostics.quarantine`), so one broken mod building can't break planning.

## Plans

[CAPlan](../src/data/scripts/jonahtweaks/autopilot/CAPlan.java) gives each colony a target layout from its planet, hazards, resources and goal. Steps are builds, conditions to reach, or notes. `bonusFor` favours picks that advance the plan, the earliest open step most. A pick that a plan building is upgraded from also counts; with AotD, Monoculture Plots count on the way to Farming. `hasOrBetter` and `leadsTo` follow upgrade chains and AotD's downgrade links, so an upgraded version counts as done.

[CAWorldPlanner](../src/data/scripts/jonahtweaks/autopilot/CAWorldPlanner.java) adds a Terraforming Made Easy special world (Forge, Fortress, Paradise, Hive, Death, Waste) when one is reachable:
- It reads TME's requirement CSV and resolves each needAll/needOne requirement back through the projects that create it.
- It simulates TME removing a new condition's conflicting conditions.
- It reads only the CSV, so it never links against TME's code.

[CAConditions](../src/data/scripts/jonahtweaks/autopilot/CAConditions.java) values a condition change (hazard, deposits, habitability). Both terraforming planners use it.

## Mod buildings: the probe colony and diagnostics

**The probe colony.** [CASimulator](../src/data/scripts/jonahtweaks/autopilot/CASimulator.java) builds the mod building on a probe market. The probe has the colony's size and its vanilla conditions, but no planet or location, so the building's code can't place anything in the sector. The simulator reads:
- the building's own supply and demand;
- the market-level changes: stability, accessibility, income and upkeep multipliers, ground defense, fleet size and patrols, ship quality.

An upgrade is measured as both buildings on the same probe, and the difference is scored.

If the building's code throws, or registers sector scripts or intel while being built, it's reported as "can't be measured", and anything it added is removed. Effects that only happen over time (monthly items, spawned fleets) aren't seen, so measured values lean low, never high.

**The diagnostics scan.** [CADiagnostics](../src/data/scripts/jonahtweaks/autopilot/CADiagnostics.java) runs on every load. It rates every building, condition and item:
- **Supported:** a formula or estimate exists;
- **Measured:** scored from a probe measurement;
- **Analyzed:** can't be measured, so never acted on;
- **Quarantined:** its code failed during planning.

`CADiagnostics.usable(id)` is the single gate the engine uses. The scan also runs the integration health check and resets the per-session safeguards. Results go to the empire panel, `[ColonyAutopilot]` lines in `starsector.log`, and `saves/common/colony_autopilot_diagnostics.json.data`.

## How the optional mods stay optional

No other mod is required, and any mix works. Three patterns make that true.

**1. Compiled against the mod, called only when it's enabled.**
- **Classes:** `CATerraform` (Terraforming Made Easy), `CAIndEvo` and `CACourier` (Industrial Evolution), `CADefense` and `CASpies` (Nexerelin), `CASettlement` (Random Assortment of Things) and `CAAugments` (Station Augments).
- **Why it's safe:** each is compiled against that mod's jar and called only inside an `isModEnabled(...)` check. The JVM loads a class only when it's first used, so without the mod they're never loaded.
- **The `MOD` constants:** these classes' mod-id constants are compile-time constants. They're copied into the calling code, so checking `CAAugments.MOD` doesn't load `CAAugments`.
- **Rule:** never reference one of these classes outside its guard.

**2. Reflection only.**
- **Classes:** `CAAotD`, `CASynergies`, `CAContracts`, `CAHypershunt`, `CAMegastructures`, `CAResearch` and `CADevelopment` (Ashes of the Domain); `CATasc` (Terraforming & Station Construction); `CAGrand` (Grand.Colonies' LunaLib settings).
- **How:** they find the other mod's classes and members by name at runtime, so no jar is needed to compile.
- **On failure:** if a name can't be found, they fail closed. They do nothing, and the reason is logged once. `CAAotD` goes further: if it can't read Building Menu Overhaul's rules, nothing is built at all while BMO is enabled, since anything built might be locked.

**Why `CARefl` exists.** Starsector loads mod code through its own class loader, which refuses any class that refers to `java.lang.reflect` (or file classes) with "File access and reflection are not allowed to scripts". Plain reflection therefore compiles and passes every desktop test, then fails in game. [CARefl](../src/data/scripts/jonahtweaks/autopilot/CARefl.java) gets the same results through `java.lang.invoke` method handles:
- It holds `Method` and `Field` as plain `Object`s. `fieldValues` reads every field of a game object, private ones included, for objects whose field names are obfuscated; callers pick the value by type.
- It loads the reflection classes by name from the boot class loader.
- It uses a full-privilege `MethodHandles.lookup()`, because `getMethod`, `invoke`, `get` and `set` are caller-sensitive and `publicLookup()` refuses them.

`CATasc` uses `MethodHandles.publicLookup().findStatic` directly. That resolves one method without loading signatures that name Illustrated Entities' classes, which plain `getMethod` would need.

**3. Literal ids only.**
- **Classes:** `CAMods` (VIC, Planetary Shield, Roider Union, Unknown Skies, What We Left Behind, Astral Ascension) and `CABrewery` (Substance.Abuse).
- **How:** they compare building, condition and item ids as strings, and are inert without their mods.
- **Generic support:** deposits and colony items from any mod work the same way, through the game's own tables.

## Surviving mod updates

Five pieces keep a mod update from quietly breaking things:

| Class | What it does |
|---|---|
| [CACompat](../src/data/scripts/jonahtweaks/autopilot/CACompat.java) | Health check at load. Each integration lists its mod, the version it was written against, the ids it needs and a check that its reflected names resolve. Status: OK, Newer (still works, trusted a bit less), Partly off, or Off. |
| [CAConst](../src/data/scripts/jonahtweaks/autopilot/CAConst.java) | Reads numbers from a mod's own public static fields, with the original value as the fallback. A rebalance is picked up, and the change is logged. |
| [CACalibrate](../src/data/scripts/jonahtweaks/autopilot/CACalibrate.java) | Weekly, compares predicted upkeep and output with your real plain buildings. It smooths the ratio (70/30), clamps it to 0.5-2x, and applies it after two readings. Kept in the save. |
| [CABreaker](../src/data/scripts/jonahtweaks/autopilot/CABreaker.java) | Checks that an action into another mod took effect. Three failures in a row stop that action for the session. |
| Confidence (`CAEngine.trust`) | An untested mod version lowers trust, and Auto asks for approval on a big build that rests on a low-confidence estimate. |

## What's saved

[CAData](../src/data/scripts/jonahtweaks/autopilot/CAData.java) is stored under the persistent-data key `jt_colony_autopilot`. It holds only plain values:

| Field | Contents |
|---|---|
| `colonies` | One `Colony` per market id: goal, mode, locked buildings, the pending proposal, story points used, policy timestamps, threat state, augments added or skipped, hypershunt and megastructure settings, fast-buildout state |
| `settings` | A [CASettings](../src/data/scripts/jonahtweaks/autopilot/CASettings.java): budget, caps, fees, every switch, this month's spending, and the action log (last 100 entries). A new save is filled from `colony_autopilot.json` by `CASettingsFile`. |
| `storage` | The shared Autopilot Storage cargo |
| `noted` | Keys of messages already logged once (`CALog.once`) |
| `calOut`, `calUp` and their counts | Calibration ratios |
| `buildoutOffered`, `spyAgents` | Whether the first-colony offer was shown; ids of the agents the manager hired |

Rules that keep saves working:
- **New fields:** a field added in a later version is missing from older saves and loads as zero, false or null. So defaults are written to mean "off" or "default" at those values, and collections are created lazily by accessors such as `augAuto()` and `spyAgents()`.
- **No other mods' objects:** nothing from another mod is ever stored, only ids. A save made with an optional mod still loads after that mod is removed.
- **Exception:** Autopilot Storage is a market tab, so this mod itself can't be removed from a save that used it.

## The panels

| Class | What it does |
|---|---|
| [CAOptionProvider](../src/data/scripts/jonahtweaks/autopilot/CAOptionProvider.java) | Adds **Colony Autopilot...** to the right-click menu of Population & Infrastructure |
| [CAColonyDialog](../src/data/scripts/jonahtweaks/autopilot/CAColonyDialog.java) | The colony panel, a custom dialog: goal and mode, approvals, plan, recommended builds, fast buildout, integration sections and locks. `build()` redraws it after every button; `handle(id)` routes button ids (`ca:lock:...`, `ca:hs:...`, `ca:bo:...`, a `Pick`, a goal, a mode). The first switch to Approve or Auto shows the setup page. |
| [CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java) | The empire panel, an intel item under Colonies: budget, approvals, every colony, Autopilot Storage, each integration's section, settings, log and diagnostics |
| [CASetupUI](../src/data/scripts/jonahtweaks/autopilot/CASetupUI.java) | The settings rows, shared by the setup page and the empire panel. Each row shows its value and the recommendation. `applyRecommended` fills in the recommendations, which are chosen so that switching colonies to Auto and nothing else works out well; its comments give the reason for each. |
| `CABuildout.Offer` | The first-colony offer, an interaction dialog with the game's own option list |
| [CAStoragePlugin](../src/data/scripts/jonahtweaks/autopilot/CAStoragePlugin.java) | The Autopilot Storage tab: accepts only colony items and AI cores |

Everything uses the game's own colors and widgets, so it looks like the base game.

## Every file

### Core

| File | What it does |
|---|---|
| [JTModPlugin](../src/data/scripts/jonahtweaks/JTModPlugin.java) | Mod plugin: starts everything on each save load |
| [CAScript](../src/data/scripts/jonahtweaks/autopilot/CAScript.java) | The daily tick; offers the fast buildout |
| [CAAuto](../src/data/scripts/jonahtweaks/autopilot/CAAuto.java) | The daily pass: proposals, funding, building, then every other automated step |
| [CAEngine](../src/data/scripts/jonahtweaks/autopilot/CAEngine.java) | Scoring: candidates, effects, shortages, trust, upkeep, goal weights |
| [CAEstimates](../src/data/scripts/jonahtweaks/autopilot/CAEstimates.java) | Hand-written effects for mod buildings with no formula |
| [CAPlan](../src/data/scripts/jonahtweaks/autopilot/CAPlan.java) | Target layouts and the plan bonus |
| [CAThreat](../src/data/scripts/jonahtweaks/autopilot/CAThreat.java) | Threat estimate per colony, with the 70% / 65% / 60-day hysteresis |
| [CABuild](../src/data/scripts/jonahtweaks/autopilot/CABuild.java) | Starts a build or upgrade like the build menu |
| [CABudget](../src/data/scripts/jonahtweaks/autopilot/CABudget.java) | Monthly budget (Automatic: half of net income plus 15% of the credits above the reserve at the month's start; share of income; fixed), reserve, pause on loss, convenience fees |
| [CABuildout](../src/data/scripts/jonahtweaks/autopilot/CABuildout.java) | Fast buildout: the offer, the daily pass, the panel section |
| [CAItems](../src/data/scripts/jonahtweaks/autopilot/CAItems.java) | Colony items, AI cores (stacked on the best-defended world, inspection-aware), administrators, the Antimatter Stabilizer conversion |
| [CAPolicies](../src/data/scripts/jonahtweaks/autopilot/CAPolicies.java) | Hazard pay and free port, with minimum time between changes |
| [CAStory](../src/data/scripts/jonahtweaks/autopilot/CAStory.java) | Story point improvements under the chosen rule |
| [CALog](../src/data/scripts/jonahtweaks/autopilot/CALog.java) | The action log; `once`/`forget` log a message once per save; `esc` makes dynamic text safe in format strings |
| [CAData](../src/data/scripts/jonahtweaks/autopilot/CAData.java), [CASettings](../src/data/scripts/jonahtweaks/autopilot/CASettings.java), [CASettingsFile](../src/data/scripts/jonahtweaks/autopilot/CASettingsFile.java) | Saved data, settings, defaults file |

### Mod buildings and safeguards

| File | What it does |
|---|---|
| [CASimulator](../src/data/scripts/jonahtweaks/autopilot/CASimulator.java) | Measures a mod building on a probe colony |
| [CADiagnostics](../src/data/scripts/jonahtweaks/autopilot/CADiagnostics.java) | Load-time scan, quarantine, report |
| [CACompat](../src/data/scripts/jonahtweaks/autopilot/CACompat.java), [CAConst](../src/data/scripts/jonahtweaks/autopilot/CAConst.java), [CACalibrate](../src/data/scripts/jonahtweaks/autopilot/CACalibrate.java), [CABreaker](../src/data/scripts/jonahtweaks/autopilot/CABreaker.java) | The mod-update safeguards |
| [CARefl](../src/data/scripts/jonahtweaks/autopilot/CARefl.java) | Reflection through method handles |
| [CAConditions](../src/data/scripts/jonahtweaks/autopilot/CAConditions.java) | What a condition change is worth |

### Integrations

| File | Mod | What it does |
|---|---|---|
| [CATerraform](../src/data/scripts/jonahtweaks/autopilot/CATerraform.java), [CAWorldPlanner](../src/data/scripts/jonahtweaks/autopilot/CAWorldPlanner.java) | Terraforming Made Easy | Terraforming projects; special-world plans |
| [CAIndEvo](../src/data/scripts/jonahtweaks/autopilot/CAIndEvo.java), [CACourier](../src/data/scripts/jonahtweaks/autopilot/CACourier.java) | Industrial Evolution | Ambassadors, edicts, VPC chips, specialties; Courier Port shipping |
| [CADefense](../src/data/scripts/jonahtweaks/autopilot/CADefense.java), [CASpies](../src/data/scripts/jonahtweaks/autopilot/CASpies.java) | Nexerelin | Emergency defense fleets; agents |
| [CAAotD](../src/data/scripts/jonahtweaks/autopilot/CAAotD.java) | AotD: Building Menu Overhaul | Research locks, hidden buildings, upgrade paths |
| [CASynergies](../src/data/scripts/jonahtweaks/autopilot/CASynergies.java) | AotD: Vaults of Knowledge | Industry synergies in scoring |
| [CADevelopment](../src/data/scripts/jonahtweaks/autopilot/CADevelopment.java) | AotD: Vaults of Knowledge | Management styles; Floating City fuel guard |
| [CAResearch](../src/data/scripts/jonahtweaks/autopilot/CAResearch.java) | AotD: Vaults of Knowledge | Research ranking and starting |
| [CAHypershunt](../src/data/scripts/jonahtweaks/autopilot/CAHypershunt.java) | AotD: Vaults of Knowledge | Coronal Hypershunt range and restoration; also the shared restoration helpers |
| [CAMegastructures](../src/data/scripts/jonahtweaks/autopilot/CAMegastructures.java) | AotD: Vaults of Knowledge | Pluto and Nidavelir |
| [CAContracts](../src/data/scripts/jonahtweaks/autopilot/CAContracts.java) | AotD: Theory of Toolbox | Trade contracts |
| [CASettlement](../src/data/scripts/jonahtweaks/autopilot/CASettlement.java) | Random Assortment of Things | Frontiers settlement |
| [CAAugments](../src/data/scripts/jonahtweaks/autopilot/CAAugments.java) | Station Augments | Augment loadouts (a knapsack within the station's points) |
| [CATasc](../src/data/scripts/jonahtweaks/autopilot/CATasc.java) | Terraforming & Station Construction | Terraforming projects, prerequisites, estimates |
| [CAGrand](../src/data/scripts/jonahtweaks/autopilot/CAGrand.java) | Grand.Colonies | 12-building screen limit; extra upkeep past the threshold |
| [CABrewery](../src/data/scripts/jonahtweaks/autopilot/CABrewery.java) | Substance.Abuse | Brewery only with a recipe in storage |
| [CAMods](../src/data/scripts/jonahtweaks/autopilot/CAMods.java) | VIC, Planetary Shield, Roider Union, Unknown Skies, What We Left Behind, Astral Ascension | Small integrations by id |

### UI and leftovers

| File | What it does |
|---|---|
| [CAOptionProvider](../src/data/scripts/jonahtweaks/autopilot/CAOptionProvider.java), [CAScreenButton](../src/data/scripts/jonahtweaks/autopilot/CAScreenButton.java), [CAColonyDialog](../src/data/scripts/jonahtweaks/autopilot/CAColonyDialog.java), [CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java), [CASetupUI](../src/data/scripts/jonahtweaks/autopilot/CASetupUI.java), [CAStoragePlugin](../src/data/scripts/jonahtweaks/autopilot/CAStoragePlugin.java) | See [The panels](#the-panels) |
| [JTCampaignScript](../src/data/scripts/jonahtweaks/JTCampaignScript.java), [JTPatronageCondition](../src/data/scripts/jonahtweaks/JTPatronageCondition.java) | Remove the retired `jt_patronage` condition from old saves; it has no effect |

## Conventions

- **Fail closed.** If something can't be read, the manager does nothing there and logs why once. It never guesses.
- **Never fight the player.**
  - It doesn't touch locked or hidden buildings, items or augments you installed, your research queue, or agents you hired.
  - Settings you change yourself on megastructures and the hypershunt stay yours until you hand them back.
- **Do it the game's way.** Builds, projects, contracts, agent orders and settlements are started the same way the game or the mod's own UI starts them. Where a mod's UI charges credits, so does the manager.
- **One step can't break the others.** Every integration call in the daily pass is wrapped in `try`/`catch` and logged.
- **No `java.lang.reflect` or file classes** anywhere in mod code. Use `CARefl` or method handles.
- **Format strings.** `addPara` treats its text as a format string. A literal `%` must be written `%%`, and dynamic text goes through `CALog.esc`. A stray `%` crashed the game once, which is why the build checks for it.
- **Logging.** Waits and similar notes go through `CALog.once`, so a reload doesn't repeat them. Actions are logged when they happen.
- **Comments say why**, especially where a number or a rule comes from another mod's code (versions are named, such as "from VoK 5.0.7's code").

## Building and checking

The jar compiles with JDK 17. Put these on the classpath:
- the game's core jars: `starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `log4j-1.2.9.jar`, `json.jar`, `lwjgl.jar` and `lwjgl_util.jar`;
- the compiled-against optional mods' jars: Terraforming Made Easy, Industrial Evolution, Nexerelin, Random Assortment of Things and Station Augments, plus LazyLib's Kotlin runtime and LunaLib for the last two.

```
javac --release 17 -cp "<core jars>;<optional mod jars>" -d build <all .java files under src>
jar cf jars/jonahtweaks.jar -C build data
```

Before each release the author's build checks (local scripts, not in this repo) run:

1. **Format check:** no stray `%` in `addPara` text.
2. **Banned-class scan:** no compiled class refers to a class the game's script loader refuses.
3. **Bytecode verification** under `-Xverify:all`, with and without the optional mods' jars.
4. **Loader check:** the jar is loaded through Starsector's own script class loader, every class's members are resolved, and `CARefl` is exercised.
5. **Combination audit:** shows that only the expected classes reference each optional mod, and that every caller checks `isModEnabled`. It then links the jar against all 32 combinations of the compiled-against mods.
6. **Name checks:** every reflected name is checked against the AotD, TASC and LunaLib jars.
7. **Offline tests:** the augment knapsack against brute force, synergy effects, management-style math, and the colony screen button's placement (against fake screen layouts).
