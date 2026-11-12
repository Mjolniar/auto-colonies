# Source guide

A tour of the code for anyone who wants to read it: how it's laid out, what happens each day in game, how a building gets its score, how the optional mod integrations stay optional, what goes in the save, and what every file does.

All the code is Java 17, compiled against Starsector 0.98a-RC8's `starfarer.api.jar`. In 0.3.0.0 it's about 41,000 lines in 118 files. Every class opens with a comment saying what it does and why; this page links them together. For what the mod does as a player sees it, read [Getting started](getting-started.md), the other guides linked from the README, and [details.md](details.md).

Two companion pages:
- **[Auditing the mod](audit.md)** lists every way the mod changes your game, what each one charges, and what it never does, so you can check it's safe without reading all of this.
- **[Automating Starsector colonies](automation-primer.md)** is the game API knowledge behind it: how colonies, buildings, items, cores and the economy work from code, and the traps we hit. Useful if you're writing your own automation.

> The code was written by AI coding agents (see the README's warning). It's compiled and checked offline (see [Building and checking](#building-and-checking)), and parts are confirmed in game, but not all of it.

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
| `mod_info.json` | Mod id `jonah_tweaks` (kept from the old name so existing saves load), version, jar, mod plugin |
| `src/data/scripts/jonahtweaks/` | The mod plugin and two small leftovers from the earlier tweaks mod (1.x) |
| `src/data/scripts/jonahtweaks/autopilot/` | Everything else. `CA` stands for Colony Autopilot. |
| `data/config/colony_autopilot.json` | Default settings for a new save (commented) |
| `data/campaign/submarkets.csv` | Registers the Autopilot Storage tab |
| `data/campaign/market_conditions.csv` | Registers the retired `jt_patronage` condition so old saves load |
| `jars/jonahtweaks.jar` | The compiled code the game loads |

The package and jar names are still `jonahtweaks` for the same reason as the mod id.

## The shape of it in one paragraph

Once a day a script runs one pass over your colonies, each step through `CAStep` so one failure can't skip the rest. For each colony on Approve or Auto, a scoring engine lists every building and upgrade the colony could start and values each one in credits a month (money after upkeep, plus everything else it does weighted by the colony's goal). The 0.3.0 planner turns that into a net present value over the payback horizon (36 months, up to 120 as surplus credits grow), puts it in one queue with every other module's offers (terraforming, research facilities, wonders, crews, story points), and starts the best value per construction day and per credit across the empire. After the planner come the money tiers and the money ladder (stockpiles, military upgrades, ship orders), Auto-expansion, and a row of independent steps, each only when its mod is enabled: items and cores, administrators, policies, terraforming, research, Nexerelin fleets, agents, diplomacy and conquered colonies, and so on. Every action goes through the same game calls the game's own UI (or the source mod's UI) uses, charges what that UI charges plus a convenience fee through `CABudget`, and is written to the log. Anything that touches another mod is done by reflection or by class name, fails closed, and is checked when the game loads.

## Where to start reading

1. [JTModPlugin](../src/data/scripts/jonahtweaks/JTModPlugin.java). On every save load it creates the saved data, runs the diagnostics scan, registers the colony menu option and the intel panel, and adds the daily script.
2. [CAScript](../src/data/scripts/jonahtweaks/autopilot/CAScript.java). Once per in-game day it calls `CAAuto.runPass`, and every frame it checks whether to offer the fast buildout.
3. [CAAuto](../src/data/scripts/jonahtweaks/autopilot/CAAuto.java). The daily pass: the order of every step.
4. [CAEngine](../src/data/scripts/jonahtweaks/autopilot/CAEngine.java). Scores every building a colony could build or upgrade to.
5. [CAValue](../src/data/scripts/jonahtweaks/autopilot/CAValue.java) and [CAHorizon](../src/data/scripts/jonahtweaks/autopilot/CAHorizon.java). One value for building and for keeping; the payback horizon.
6. [CAPlanner](../src/data/scripts/jonahtweaks/autopilot/CAPlanner.java). The one queue: what starts, what waits, what's saved up for.
7. [CAPlan](../src/data/scripts/jonahtweaks/autopilot/CAPlan.java). The target layout each colony works toward.
8. [CABuild](../src/data/scripts/jonahtweaks/autopilot/CABuild.java). Starts a build the way the build menu does.
9. [CARich](../src/data/scripts/jonahtweaks/autopilot/CARich.java). Money tiers and the money ladder.
10. [CAColonyDialog](../src/data/scripts/jonahtweaks/autopilot/CAColonyDialog.java) and [CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java). The two panels.

After those, any integration class can be read on its own.

## A day in game

`CAScript.advance` calls `CAAuto.runPass(now)` once a game day, on the game's own clock (`CATime`; before 0.3.0 it counted days since the session started). If Theory of Toolbox's background threads are reading markets, it waits, but not past a second day. Every step goes through [CAStep](../src/data/scripts/jonahtweaks/autopilot/CAStep.java): `CAStep.run` isolates and times the step and stops it for the session after 3 failed days in a row; `CAStep.guard` isolates without the breaker, for the safety steps. The pass, in order:

1. **Month and weekly steps.** `CABudget.rollMonth` (new month's budget), the upkeep cap check, `CACalibrate.run`, `CAModWatch.weekly`, `CATidy.weekly` (saved-data tidy-up), `CAMarket.weekly`.
2. **Daily state.** `CASiege.update` (guarded), `CAStores.daily` (shipments arrive), `CALogistics.daily`, `CACrisis.daily`, `CASurplus.daily` (focus world), `CACapital.daily`, `CAEndgame.daily`, `CAProjects.update`.
3. **Colony lists** (guarded): Auto colonies, those not under siege, and every colony not Off.
4. **The 0-stability safety net** (guarded): `CAOverLimit.run`, before anything is built.
5. **Planner.** `CAPlanner.run` collects `CAAction`s from every module (builds and upgrades from `CAEngine`, surplus terraforming, surplus research, the wonder site, stable-location crews, story points), scores each by NPV over the horizon (`CAValue.npv`, `CAHorizon`), then:
   - per colony, new buildings go through its one construction queue by NPV per construction day; upgrades run beside it;
   - across colonies, money goes by NPV per credit, with an earmark for the first pick that waits for money, so cheaper picks can't spend its savings;
   - the new-upkeep cap counts only upkeep a building doesn't cover itself;
   - after each start, actions that share a colony, a good, a building or a one-per-empire flag are re-scored;
   - Approve colonies get the best pick as a proposal; a rejected one is held off for 30 days;
   - removal marks (`CATombstone`) keep a building taken down in the last year out of that colony's list.
   A colony gets a fresh look when something at it starts or finishes, a slot opens, its threat or siege changes, the month rolls over (picks that waited for money) or the money tier changes; otherwise weekly (`CAAuto.wait`). With the hidden `legacyPlanner` switch, or after 3 failed planner days, the 0.2.45 build pass (`CAAuto.buildPass`) runs.
6. **Money tiers and the ladder.** `CARich.run`: the tier, stockpile deliveries, the Shipyard's free-cash guard, then the rungs in the preset's order (`CAStockpile`, `CAMilitary`, `CAShipyard`).
7. **Auto-expansion.** `CAExpansion.run`: deliveries, administrators seated, then one expedition or proposal when every gate holds.
8. **Removers and placement.** `CAPrune`, `CALowProfile`, then `CAItems.run` (guarded) and `CAStoragePlugin.evict`.
9. **Mod steps**, each only when its mod is enabled:

   | Step | Class |
   |---|---|
   | Terraforming Made Easy projects, surplus terraforming | `CATerraform.run`, `CATerraformSurplus.run` |
   | Surplus research, wonders and gates | `CASurplusResearch.run`, `CAWonders.run` |
   | Industrial Evolution, then Courier Port shipping | `CAIndEvo.run`, `CACourier.run` |
   | Nexerelin: emergency fleets, war fleets, agents, diplomacy, conquered colonies, autonomy (all guarded) | `CADefense.run`, `CAWar.run`, `CASpies.run`, `CADiplomacy.run`, `CAConquest.tend`, `CAAutonomy.run` |
   | Hazard pay and free port (guarded) | `CAPolicies.run` |
   | Story points, stable locations, TASC stations | `CAStory.run`, `CAObjectives.run`, `CATascStations.run` |
   | RAT settlement, station augments, trade contracts | `CASettlement.run`, `CAAugments.run`, `CAContracts.run` |
   | Hypershunt, megastructures, Floating City, research | `CAHypershunt`, `CAMegastructures`, `CADevelopment`, `CAResearch` |
   | TASC terraforming | `CATasc.run` |

10. **Panel scores and the report.** `CAEngine.fill` keeps every managed colony's score for the panels (they never evaluate while drawing); `CADiagnostics.flushReport` writes the report once a pass.

Nothing in the pass spends below the credit reserve. A few emergencies may pass the monthly budget (emergency defense and relief fleets, an administrator for a colony past the colony limit), and they still keep the reserve. Anything that moves between colonies (an item, a core, research databanks) goes through `CAStores.ship`, which charges a fee and takes travel time; nothing is moved across the sector for free.

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

**Upkeep.** `CAEngine.upkeep` predicts the building's upkeep the way the game's `BaseIndustry.applyIncomeAndUpkeep` does: the spec's upkeep (already in credits, since the game multiplies the CSV value by 500 when it loads), times max(1, size - 2), times the colony's upkeep multiplier. That's then multiplied by the calibration factor from `CACalibrate`. (Before 0.2.8.0 it multiplied by 500 a second time; `CACalibrate.migrate` clears the corrections older saves learned from that.)

**Weights.** `CAEngine.finish` combines `fx` with the goal's weights (the `WEIGHTS` table, one row per goal: Balanced, Money, Security, War production, Rapid growth, Self-sufficiency). It then applies:
- the stability floor;
- pollution: an upgrade that would pollute a habitable world again is shown with a negative score, so it's never started (the same for every goal). Polluting items never go into a habitable world's forge at all (`CAItems.wouldPollute`), and `CAItems.pullPollutingForge` takes one back while its pollution is still reversible;
- the plan bonus from `CAPlan.bonusFor`. In 0.3.0 the flat bonus goes only to enabler steps (terraforming structures and special-world buildings, stations, project producers, the capital's trade buildings, the Research Facility, spending-freely structures, the Bifrost). Other plan steps count what they earn plus half of what they lead to (`CATuning.DOWNSTREAM_SHARE`);
- the money weight: from the money tier Idle money a credit counts half at Approve and Auto colonies, a quarter while spending freely (`CASurplus.moneyWeight`), 5% in the endgame.

A pick that breaks a rule (such as the stability floor) is dropped; everything else is ranked and shown, worthwhile or not.

**The price side (0.3.0).** A pick's score is its net present value over the payback horizon H, as a monthly figure: score = worth - (price + fee) / H - worth x construction days / 30 / H (`CAHorizon.npvMonthly`, `CAValue.npv`). H is 36 months and slides up to 120 as surplus credits reach 10,000,000, falling only when they drop under 60% of what raised it (`CAHorizon.slide`). This replaced 0.2.45's three-year spread, the focus world's quarter price, the spending-freely 5% price and the +15,000 upgrade follow-through. The 0.2.45 score is still in `CAEngine` for the hidden `legacyPlanner` switch.

**Keeping a building.** [CAValue](../src/data/scripts/jonahtweaks/autopilot/CAValue.java) values new builds (`add`) and existing buildings (`keep`) with one set of functions, so builders and removers agree. `keep` is the build math backwards: what the colony loses a month without the building, its live output above the colony's other sources, priced by export share, plus the shortage it would leave, plus the synergies it keeps going. [CATombstone](../src/data/scripts/jonahtweaks/autopilot/CATombstone.java) adds the teardown loss (the part of what Autopilot paid that hasn't paid back over H) and the one-year removal marks. `CAValue.deficits` counts buildings under construction as supply.

**The result.** `Result` holds:
- the sorted picks;
- `blocked`: why nothing new can start, such as a build in progress or 12 buildings without Grand.Colonies;
- picks that only lack an industry slot;
- counts of locked and unsupported options, which the panels show.

Any exception thrown by a building's code during scoring quarantines that building (`CADiagnostics.quarantine`), so one broken mod building can't break planning.

## Plans

[CAPlan](../src/data/scripts/jonahtweaks/autopilot/CAPlan.java) gives each colony a target layout from its planet, hazards, resources and goal. Steps are builds, conditions to reach, or notes. `bonusFor` favours picks that advance the plan, the earliest open step most (enabler steps only, since 0.3.0). A step worth nothing for 30 days (`CATuning.WORTHLESS_DAYS`) gives up its slot and rank until it pays again. A world plan that already paid for terraforming switches only to one worth at least twice as much ([CAMark](../src/data/scripts/jonahtweaks/autopilot/CAMark.java) keeps the stamp). A pick that a plan building is upgraded from also counts; with AotD, Monoculture Plots count on the way to Farming. `hasOrBetter` and `leadsTo` follow upgrade chains and AotD's downgrade links, so an upgraded version counts as done.

[CAWorldPlanner](../src/data/scripts/jonahtweaks/autopilot/CAWorldPlanner.java) adds a Terraforming Made Easy special world (Forge, Fortress, Paradise, Hive, Death, Waste) when one is reachable:
- It reads TME's requirement CSV and resolves each needAll/needOne requirement back through the projects that create it.
- It simulates TME removing a new condition's conflicting conditions.
- It reads only the CSV, so it never links against TME's code.

[CAConditions](../src/data/scripts/jonahtweaks/autopilot/CAConditions.java) values a condition change (hazard, deposits, habitability). Both terraforming planners use it.

## Mod buildings: the probe colony and diagnostics

**The probe colony.** [CASimulator](../src/data/scripts/jonahtweaks/autopilot/CASimulator.java) builds the mod building on a probe market. The probe has the colony's size and its vanilla conditions, but no planet or location, so the building's code can't place anything in the sector. A copied condition that fails without the intel it was added with (Pather cells, pirate activity, a blockade) is left off. The simulator reads:
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
- **Classes:** `CATerraform` (Terraforming Made Easy), `CAIndEvo` and `CACourier` (Industrial Evolution), `CADefense`, `CASpies`, `CAWar`, `CAConquest`, `CAScorched`, `CADiplomacy`, `CAWarListener`, `CAWarPage` and `CAExpansionNex` (Nexerelin), `CASettlement` (Random Assortment of Things) and `CAAugments` (Station Augments).
- **0.3.0's pure halves:** `CAStance`, `CAWarPolicy` and `CAExpansion` hold the rules and settings with no Nexerelin imports, so the settings screen, the Factions page and Auto-expansion's crews load without Nexerelin. `CAWarListener` is transient (never saved), so a save loads without Nexerelin.
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

These pieces keep a mod update from quietly breaking things:

| Class | What it does |
|---|---|
| [CACompat](../src/data/scripts/jonahtweaks/autopilot/CACompat.java) | Health check at load. Each integration lists its mod, the version it was written against, the ids it needs and a check that its reflected names resolve. Status: OK, Newer (still works, trusted a bit less), Partly off, or Off. |
| [CALinkScan](../src/data/scripts/jonahtweaks/autopilot/CALinkScan.java) | 0.3.0: reads Auto Colonies' own class files from the class loader (the constant pool: every class, method and field each class calls), keeps the names in Nexerelin's, Industrial Evolution's, RAT's, Station Augments' and TME's packages, and looks each one up with method handles without running anything. A renamed method shows in Diagnostics at load; only the part that calls it stops. |
| [CAStep](../src/data/scripts/jonahtweaks/autopilot/CAStep.java) | 0.3.0: isolates and times every step of the daily pass. A step that fails 3 days in a row stops for the session with one log line and a Diagnostics entry; the safety steps use `guard` and keep running. |
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
| `stores`, `shipments` | Each colony's Autopilot Storage cargo by market id, and what's being shipped between them (0.2.37.0). The old shared `storage` field stays so older saves load; it's emptied into the biggest colony's store once. |
| `noted` | Keys of messages already logged once (`CALog.once`) |
| `calOut`, `calUp` and their counts | Calibration ratios |
| `buildoutOffered`, `spyAgents`, `spyRoles`, `spyRecruits` | Whether the first-colony offer was shown; ids of the agents the manager gives orders, their roles, and agents being recruited |
| `attackers`, `endgameBuilt`, `augRefit`, `settleExpedition*`, `surplusBuilt`, `surplusResearch`, `enablers` | War fleets' strike-back targets, what the endgame built or took down (and, since 0.3.0, `CATombstone`'s build stamps `marketId:industryId@money\|rich\|endgame`), booked station refit crews, a settlement expedition on its way, and what surplus terraforming and research built (so only those come down again) |
| `schema`, `migratedAt` | 0.3.0: the layout version, and when `CAMigrate` moved the save to 0.3.0 (the What's new box shows for 30 days after) |
| `notedAt`, `overLimitAt`, `pruneAt`, `swapAt`, `monthLedger` | 0.3.0: once-keys with their time (`CALog`; `keep:` keys are `CAMark` stamps that don't expire), the over-limit, prune and make-room timers split out of `endgameBuilt`, and this month's spending per colony |
| `tombstones`, `sunk` | 0.3.0: removal marks (market, building, until, why, what it cost) and what Autopilot paid per building |
| `colonyNetHistory`, `idleMonths`, `richTier`, `richTierSince`, `rungSpent` | 0.3.0: the 3-month colony net, idle months, the money tier and when it last changed, and this month's spending per rung |
| `shipyardOrders`, `stockpiles` | 0.3.0: the Shipyard's order lines (hull id, quantity, price, timestamp) and stockpile shipments (market id, commodity, quantity, cost, timestamps, fee, source) |
| `expandOrders`, `expandSkip`, `expandFounded`, `expandLast`, `expandPending*` | 0.3.0: Auto-expansion's expeditions (planet and source ids, method, plan, numbers), skipped planets, founded colonies, the last launch and the proposal |
| `warDeclared`, `warOps`, `conquered`, `warAuthorized`, `warStandDown` | 0.3.0: war declarations, operations launched (`kind:marketId` to time), conquered colonies, factions you declared war on, and the last Stand down |

New settings fields for 0.3.0 (War level, stances, rung modes, Auto-expansion and the rest) are listed in `CASettings` with their defaults for new games and for older saves.

Rules that keep saves working:
- **Short names (0.3.0).** [CAXStream](../src/data/scripts/jonahtweaks/autopilot/CAXStream.java) gives every saved class a fixed alias (`JCM-Data`, `JCM-Colony`, `JCM-Settings`, `JCM-Tombstone`, `JCM-ShipOrder` and so on), so a class can move or be renamed without breaking saves. An alias is not renamed or reused once released. Saves from before 0.3.0 still load (XStream falls back to the full class name); saves written with the aliases don't load in 0.2.45.
- **One migration (0.3.0).** [CAMigrate](../src/data/scripts/jonahtweaks/autopilot/CAMigrate.java) moves a save from before 0.3.0 forward once: War fleets to the War level, faction priorities to stances, retake off, the per-project cap kept as Fixed when it differs from the Automatic one. It does not raise anything above what 0.2.45 did. The save is stamped as moved before the steps run, so a failure loads the save and is not retried.
- **New fields:** a field added in a later version is missing from older saves and loads as zero, false or null. So defaults are written to mean "off" or "default" at those values (fields that must default on use an inverted `noX` name), and collections are created lazily by accessors such as `augAuto()` and `spyAgents()`.
- **Removed fields:** the game's save loader fails on a field the class no longer has. Since 0.3.0, a retired field is deleted from the class and listed in `CAXStream` with `omitField` (`adminDelay`, `storageCheat`, `haFleetCap`, `nexAttackGap`, `richIncomeRef`); the line stays for good.
- **No other mods' objects:** nothing from another mod is ever stored, only ids, numbers and timestamps. A save made with an optional mod still loads after that mod is removed.
- **Exception:** Autopilot Storage is a market tab, so this mod itself can't be removed from a save that used it.

## The panels

| Class | What it does |
|---|---|
| [CAOptionProvider](../src/data/scripts/jonahtweaks/autopilot/CAOptionProvider.java) | Adds **Colony Autopilot...** to the right-click menu of Population & Infrastructure |
| [CAColonyDialog](../src/data/scripts/jonahtweaks/autopilot/CAColonyDialog.java) | The colony panel, a custom dialog with a page list on the left (`pages()`): Overview (stats, approval, goal, mode, fast buildout), Plan and builds, Locks, and Mod features, one page with a section per integration that applies (`modSections()`, `renderMod()`). `build()` redraws the current page after every button; `handle(id)` routes button ids (`ca:page:...`, `ca:lock:...`, `ca:hs:...`, `ca:bo:...`, a `Pick`, a goal, a mode). The first switch to Approve or Auto shows the setup page. |
| [CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java) | The empire panel, an intel item with its own **Colony Autopilot** filter (a tag of its own; older saves have its "important" flag cleared). Pages (`pages()`): Colonies (budget with the money tier and ladder line, approvals, every colony with its next build), Autopilot Storage, Colony crises, Expansion, War (Nexerelin), Mod features (a section per integration in use: `modSections()`, `renderMod()`), Settings, Factions, Log and Diagnostics |
| [CANav](../src/data/scripts/jonahtweaks/autopilot/CANav.java) | The page list both panels draw on the left, so one section shows at a time instead of one long scroll |
| [CATheme](../src/data/scripts/jonahtweaks/autopilot/CATheme.java) | The look and the voice (0.2.41.0). Look: `heading` ("//  TITLE" bars), `button` (cut corners, caps), `readout`, and `Frame`, the panel plugin that draws the grid and cut-corner frames with GL in `renderBelow` (once a frame). Voice: `machine` turns a log line into `CODE / Place: what` (the colony or system from `places()`, the code from `CODES`, credits through `shortCredits`; a line with no code stays as it is) |
| [CASetupUI](../src/data/scripts/jonahtweaks/autopilot/CASetupUI.java) | The Settings page (0.3.0: 20 main rows in 6 groups, a 5-line glossary, a collapsed Advanced group with 11 subgroups, one open at a time) and the 5-row first-time setup (`renderFirstTime`). Gray lines and hover text are in the `DESC` and `HOVER` tables. `applyRecommended` fills in the recommendations, with a comment giving the reason for each; `resetToRecommended` keeps Expansion style, War, New colonies, stances and the capital. |
| [CAPresets](../src/data/scripts/jonahtweaks/autopilot/CAPresets.java) | 0.3.0: the preset rows (Spending, Extras, New colonies, War, Story points, Stations, Take down buildings, Colony policies, AI cores, Administrators). `apply` writes the older fields; `match` reads them back on every draw and returns Custom when they fit no value. Only enabled mods' fields count. |
| [CAFactionsPage](../src/data/scripts/jonahtweaks/autopilot/CAFactionsPage.java) | 0.3.0: the stance table, one row per faction with `<` `>` buttons, relation, markets and (with Nexerelin) the War level its stance allows. No Nexerelin classes. |
| [CAWarPage](../src/data/scripts/jonahtweaks/autopilot/CAWarPage.java) | 0.3.0 (Nexerelin): War level, fleet request points, infamy, money, Stand down, Recall all, Authorize bombardment, a block per faction with planned operations and what each waits for, conquered colonies. Drawing changes nothing. |
| [CAExpansionPage](../src/data/scripts/jonahtweaks/autopilot/CAExpansionPage.java) | 0.3.0: Auto-expansion's page, drawn by `CAExpansion.render`. Found now asks first with the price, fee, days and administrator hire. |
| `CABuildout.Offer` | The first-colony offer, an interaction dialog with the game's own option list |
| [CAStoragePlugin](../src/data/scripts/jonahtweaks/autopilot/CAStoragePlugin.java), [CAStores](../src/data/scripts/jonahtweaks/autopilot/CAStores.java) | The Autopilot Storage tab, one store per colony (`CAStores.of`), and shipping between them (`CAStores.ship`: a fee and travel days, delivered by `CAStores.daily`); campaign consumables and blueprints are refused (`consumable`) |

Everything uses the game's own colors and widgets, so it looks like the base game.

## Every file

### Core

| File | What it does |
|---|---|
| [JTModPlugin](../src/data/scripts/jonahtweaks/JTModPlugin.java) | Mod plugin: starts everything on each save load |
| [CAScript](../src/data/scripts/jonahtweaks/autopilot/CAScript.java) | The daily tick; offers the fast buildout |
| [CAAuto](../src/data/scripts/jonahtweaks/autopilot/CAAuto.java) | The daily pass: the order of every step; the 0.2.45 build pass (`buildPass`) behind `legacyPlanner` |
| [CAEngine](../src/data/scripts/jonahtweaks/autopilot/CAEngine.java) | Scoring: candidates, effects, shortages, trust, upkeep, goal weights |
| [CAEstimates](../src/data/scripts/jonahtweaks/autopilot/CAEstimates.java) | Hand-written effects for mod buildings with no formula |
| [CAPlan](../src/data/scripts/jonahtweaks/autopilot/CAPlan.java) | Target layouts and the plan bonus |
| [CAThreat](../src/data/scripts/jonahtweaks/autopilot/CAThreat.java) | Threat estimate per colony, with the 70% / 65% / 60-day hysteresis; the likely target of the coming colony crisis adds 0.4; `protection` (stations and military bases in the system that aren't hostile) keeps a station's augments economic |
| [CACrisis](../src/data/scripts/jonahtweaks/autopilot/CACrisis.java) | Colony crises (Hostile Activity): prices monthly progress in credits (`pointCost`, after the military multiplier and the 60 cap), the game's diminishing returns for Hegemony and Pather progress, AI core costs, what a Patrol HQ/Military Base/High Command saves (`military`), Diktat fuel (`diktat`), the coming crisis and its likely targets, Pather relief, Nexerelin's police score, Hegemony inspection orders, and the Colony crises page |
| [CASiege](../src/data/scripts/jonahtweaks/autopilot/CASiege.java) | Which colonies are under siege (blockaded, hostiles at the planet or outweighing its defenses, an attack on its way), checked daily |
| [CABuild](../src/data/scripts/jonahtweaks/autopilot/CABuild.java) | Starts a build or upgrade like the build menu |
| [CABudget](../src/data/scripts/jonahtweaks/autopilot/CABudget.java) | Monthly budget (Automatic: half of net income plus 15% of the credits above the reserve at the month's start; share of income; fixed), reserve, pause on loss, convenience fees |
| [CABuildout](../src/data/scripts/jonahtweaks/autopilot/CABuildout.java) | Fast buildout: the offer, the daily pass, the panel section |
| [CALowProfile](../src/data/scripts/jonahtweaks/autopilot/CALowProfile.java) | Low profile (0.2.43.0): whose crisis can still come (`hegemony`, `pather`, `church`, `triTachyon`, `diktat`), the Pather line (`pathBlocked`, `pathLimit`, used by `CAItems.coreLimit` and item placement), a pick's part (`score`: the line, Tri-Tachyon's competing exports by `ttProgress`, fuel, Luddic majorities), the daily `run` (`pullItem`, `swapAdmin`, the League's one line), the first colony's choice (`set`) and its section of the Colony crises page |
| [CAItems](../src/data/scripts/jonahtweaks/autopilot/CAItems.java) | Colony items, AI cores (placed, upgraded, moved and pulled back by `coreValue`, what each core does in a building with the colony's goal weights less the Hegemony and Pather crisis progress it adds (`CACrisis.hegemonyCost`, `CACrisis.pathCost`), within `coreLimit`: the Hegemony's 7-point threshold, 0 above size 3 with Small colonies only, or no limit where `defended`, with No limit, or when `noInspections`, and lower while a Pather attack targets the colony (`CACrisis.pathReliefLimit`); colonies in `inspected` are left alone), administrators (hired, placed and moved only where `adminWorth`, Industrial Planning output plus colony-limit stability valued by `stabilityWorth`, beats the salary), the Orbital Fusion Lamp (`lampValue`: the cold or darkness it cancels less the heat it adds; `pullLamp` takes it back where that's negative unless `planNeedsHeat`), the Antimatter Stabilizer conversion; items: `swapItem` swaps a waiting item in for one a quarter less valuable (`better`), `idleItems` are the ones no Auto colony has a building for, and `idleBonus` adds what one would add to a new building that takes it |
| [CALogWatch](../src/data/scripts/jonahtweaks/autopilot/CALogWatch.java) | A log4j appender on the package loggers of Mjolniar's mods (`MODS`): warnings and errors this session, with the latest, for the Diagnostics page's Mjolniar's mods section (the other mods' status lines come from sector memory `$jonahs_status_<mod id>`) |
| [CASurplus](../src/data/scripts/jonahtweaks/autopilot/CASurplus.java) | Surplus investment: credits over the line, less `committed` (custom production still to build plus Ashes of the Domain's orders, `aotdOrders`, by reflection), (`amount`) go into the focus world (`focus`: yours, or the best `score` among Auto colonies, kept by `pick` unless another beats it by `MARGIN`); `extra`, `projectCap`, `upkeepCap` and `costWeight` are what the build pass, scoring, terraforming and research use |
| [CAEndgame](../src/data/scripts/jonahtweaks/autopilot/CAEndgame.java) | The endgame economy: when it runs (`decide`), the income it keeps (`floor`, `room`), its weights, production-chain values (`chainValue` over a daily `Chains` snapshot of your colonies' and the sector's supply), and replacing the weakest industry (`weakest`, `replacement`, `takeDown`) |
| [CASurplusResearch](../src/data/scripts/jonahtweaks/autopilot/CASurplusResearch.java) | More Research Facilities from the surplus (3 of its own below the money tier Rich, up to the 8 Vaults of Knowledge counts), and taking them down once research is done |
| [CAWonders](../src/data/scripts/jonahtweaks/autopilot/CAWonders.java), [CAProjects](../src/data/scripts/jonahtweaks/autopilot/CAProjects.java) | Ashes of the Domain, by reflection: the Daedalus Array and Bifrost gates while spending freely; producers of the goods a special project needs as plan steps |
| [CADay](../src/data/scripts/jonahtweaks/autopilot/CADay.java) | Today as a day number from the game clock, for daily caches (never `getElapsedDaysSince(0L)`) |
| [CAPolicies](../src/data/scripts/jonahtweaks/autopilot/CAPolicies.java) | Hazard pay and free port, with minimum time between changes; raises an old save's 10% hazard pay limit to 30% once |
| [CAStory](../src/data/scripts/jonahtweaks/autopilot/CAStory.java) | Story point improvements under the chosen rule; the monthly cap counts story points spent (`spMonth`), cheapest improvement first (`pick`) |
| [CALog](../src/data/scripts/jonahtweaks/autopilot/CALog.java) | The action log; `once`/`forget` log a message once per save; `esc` makes dynamic text safe in format strings; on screen in the voice the settings pick (`CATheme.machine` or "Autopilot: "); `ping` is a screen-only machine line, once a week per key |
| [CAData](../src/data/scripts/jonahtweaks/autopilot/CAData.java), [CASettings](../src/data/scripts/jonahtweaks/autopilot/CASettings.java), [CASettingsFile](../src/data/scripts/jonahtweaks/autopilot/CASettingsFile.java) | Saved data, settings, defaults file |

### Added in 0.3.0: foundation

| File | What it does |
|---|---|
| [CAStep](../src/data/scripts/jonahtweaks/autopilot/CAStep.java) | Runs, times and isolates every step of the daily pass. `run` stops a step after 3 failed days in a row for the session; `guard` never stops (safety net, siege check, emergencies). A failure's stack trace goes to starsector.log once, then a short line at most weekly. |
| [CATime](../src/data/scripts/jonahtweaks/autopilot/CATime.java) | One clock: game days from the sector clock. `due` and `fresh` treat a stamp later than today as stale (an earlier save was loaded). The game clock's days are negative, so "never fired" is NaN. |
| [CAReset](../src/data/scripts/jonahtweaks/autopilot/CAReset.java) | Clears every cache, timer and session flag kept in static fields, first thing on every load. `JTResetCheck` fails on a new static that isn't reset or listed. |
| [CAStability](../src/data/scripts/jonahtweaks/autopilot/CAStability.java) | The one stability rule: `canLose` lets an action go when it costs no stability or the colony stays at or above the floor (minimum stability, at least 3) afterwards. Every takedown, move, pull, policy and new action asks it. |
| [CATuning](../src/data/scripts/jonahtweaks/autopilot/CATuning.java) | Auto Colonies' own numbers in one place (bonuses, fees, day counts, caps, stability margins, the horizon, tier lines). `JTTuningCheck` pins every value. |
| [CAMigrate](../src/data/scripts/jonahtweaks/autopilot/CAMigrate.java) | Moves a save from before 0.3.0 forward once (`to3`). Translates old settings; raises nothing. |
| [CAXStream](../src/data/scripts/jonahtweaks/autopilot/CAXStream.java) | Short aliases for saved classes, and `omitField` lines for retired fields. Called from `JTModPlugin.configureXStream`. |
| [CACache](../src/data/scripts/jonahtweaks/autopilot/CACache.java) | Day caches behind the engine: an answer is kept with the game day, an empire-wide change counter and a signature of the colonies it read; any change is a miss. |
| [CAPerf](../src/data/scripts/jonahtweaks/autopilot/CAPerf.java) | Milliseconds per step, shown in Diagnostics' Performance section; a pass over the slow line writes one quiet log line. |
| [CATidy](../src/data/scripts/jonahtweaks/autopilot/CATidy.java) | Weekly tidy-up of saved data: expired once-keys, year-old timestamps nothing reads, leftovers of colonies that are gone (captured colonies keep everything). Splits the timers that shared `endgameBuilt`. |
| [CAMark](../src/data/scripts/jonahtweaks/autopilot/CAMark.java) | Small saved stamps for decisions that hold across a load (a free port waiting to reopen, a world plan money went into, contracts signed, stances you set), kept as `keep:` once-keys. |

### Added in 0.3.0: planner and money

| File | What it does |
|---|---|
| [CAValue](../src/data/scripts/jonahtweaks/autopilot/CAValue.java) | One value service: `add` (a new build or upgrade), `keep` (an existing building, the build math backwards), `deficits` (shortages, builds under way counted), `boost` (more units on a line, for cores, items, story points, synergies), `npv`. |
| [CAAction](../src/data/scripts/jonahtweaks/autopilot/CAAction.java) | One thing Autopilot could do this pass, offered to the planner: worth, price, fee, days, upkeep, NPV, what it ties up (queue, building, slot, stability room, story points) and where its money comes from. |
| [CAPlanner](../src/data/scripts/jonahtweaks/autopilot/CAPlanner.java) | The one queue: per-colony construction slot by NPV per construction day, money by NPV per credit, the earmark for a pick that saves up, the upkeep hold, re-scoring after each start, event-driven looks, `setAside` for spending outside the pass. |
| [CAHorizon](../src/data/scripts/jonahtweaks/autopilot/CAHorizon.java) | The payback horizon: 36 months sliding to 120 with surplus credits, with 60% hysteresis; the price side of a score. |
| [CATombstone](../src/data/scripts/jonahtweaks/autopilot/CATombstone.java) | Removal marks (a year), what Autopilot paid per building, the teardown loss, `forRemoval` (what keeping a building is worth to a remover), and the 0.2.45.1 reading as a floor for pruning. |
| [CARich](../src/data/scripts/jonahtweaks/autopilot/CARich.java) | Money tiers T0-T4 with 60% exit lines and idle months, the monthly pool, the rung waterfall in the preset's order, rung modes, the Budget panel's ladder line, proposals' Approve and Reject. |
| [CAShipyard](../src/data/scripts/jonahtweaks/autopilot/CAShipyard.java) | The Shipyard rung: Doctrine priority hulls added to custom production (`FactionProductionAPI.addItem`), the 2% fee, the 6-hull stop, and the daily free-cash guard that takes its own hulls off the list. |
| [CAStockpile](../src/data/scripts/jonahtweaks/autopilot/CAStockpile.java) | The Stockpiles rung and military goods: buys goods at the nearest non-hostile market and ships them into a colony's Local Resources when a shortage's lost output is worth more. |
| [CAMilitary](../src/data/scripts/jonahtweaks/autopilot/CAMilitary.java) | The Military rung's upgrades: station tiers, Military Base to High Command, Patrol HQ to Military Base; one a day, within 15% of colony net in added upkeep. |
| [CAEconomy](../src/data/scripts/jonahtweaks/autopilot/CAEconomy.java) | The Economy rung's rules: surplus research and terraforming gates, capital trade buildings at a loss, hazard pay a project saves, shipping worth its fee. |

### Added in 0.3.0: expansion and war

| File | Mod | What it does |
|---|---|---|
| [CAExpansion](../src/data/scripts/jonahtweaks/autopilot/CAExpansion.java) | Any | Auto-expansion: gates (money, pace, colony limit, sieges, crises), the target scan and value, launch, crew founding with the game's founding steps (`found`), deliveries, administrators, refunds, the Expansion page's content. No Nexerelin imports. |
| [CAExpansionNex](../src/data/scripts/jonahtweaks/autopilot/CAExpansionNex.java) | Nexerelin | Colony fleets (`ColonyExpeditionIntel`), paid only after `init()` leaves the expedition running; hands the new colony to you (`fixAdmin`). |
| [CAStance](../src/data/scripts/jonahtweaks/autopilot/CAStance.java) | Any | The stance scale (Ally -3 to Destroy +3): War level cap, relations rule, ceasefire answer. Pure. |
| [CAWarPolicy](../src/data/scripts/jonahtweaks/autopilot/CAWarPolicy.java) | Any | The War level and war rules: `effective` (the level used on one faction), which operations each level allows, fees, payers, infamy line, bombardment penalties and invasion scores. Pure. |
| [CADiplomacy](../src/data/scripts/jonahtweaks/autopilot/CADiplomacy.java) | Nexerelin | Ceasefire and peace answers by stance, alliance offer lines, keep-hostile, `declareWar` from the War page. |
| [CAConquest](../src/data/scripts/jonahtweaks/autopilot/CAConquest.java) | Nexerelin | Invasions and retakes (`InvasionIntel`, 1.25x fleet and marines), their gates, and the conquered colonies (`tend`: autonomy, mode, goal, relief). |
| [CAScorched](../src/data/scripts/jonahtweaks/autopilot/CAScorched.java) | Nexerelin | Saturation bombardment of Destroy capitals (player-faction `SatBombIntel`) and `applyAtrocity`, the reputation penalties applied once per bombardment. |
| [CAWarListener](../src/data/scripts/jonahtweaks/autopilot/CAWarListener.java) | Nexerelin | Transient listener for captures and bombardments; daily scans catch what it missed. |

### Mod buildings and safeguards

| File | What it does |
|---|---|
| [CASimulator](../src/data/scripts/jonahtweaks/autopilot/CASimulator.java) | Measures a mod building on a probe colony |
| [CADiagnostics](../src/data/scripts/jonahtweaks/autopilot/CADiagnostics.java) | Load-time scan, quarantine, report |
| [CAModWatch](../src/data/scripts/jonahtweaks/autopilot/CAModWatch.java) | Mod watch: how each colony mod with an integration is doing in your game (measured against real output, formula checks, its items and conditions), a verdict per mod in the report and on the Diagnostics page |
| [CACompat](../src/data/scripts/jonahtweaks/autopilot/CACompat.java), [CAConst](../src/data/scripts/jonahtweaks/autopilot/CAConst.java), [CACalibrate](../src/data/scripts/jonahtweaks/autopilot/CACalibrate.java), [CABreaker](../src/data/scripts/jonahtweaks/autopilot/CABreaker.java), [CALinkScan](../src/data/scripts/jonahtweaks/autopilot/CALinkScan.java) | The mod-update safeguards |
| [CARefl](../src/data/scripts/jonahtweaks/autopilot/CARefl.java) | Reflection through method handles |
| [CAConditions](../src/data/scripts/jonahtweaks/autopilot/CAConditions.java) | What a condition change is worth |

### Integrations

| File | Mod | What it does |
|---|---|---|
| [CATerraform](../src/data/scripts/jonahtweaks/autopilot/CATerraform.java), [CAWorldPlanner](../src/data/scripts/jonahtweaks/autopilot/CAWorldPlanner.java) | Terraforming Made Easy | Terraforming projects; special-world plans |
| [CATerraformSurplus](../src/data/scripts/jonahtweaks/autopilot/CATerraformSurplus.java), [CATerraSim](../src/data/scripts/jonahtweaks/autopilot/CATerraSim.java) | Terraforming Made Easy | Surplus terraforming at every Auto colony, planned on a simulation of TME's own rules (liked and hated conditions, habitability) |
| [CAIndEvo](../src/data/scripts/jonahtweaks/autopilot/CAIndEvo.java), [CACourier](../src/data/scripts/jonahtweaks/autopilot/CACourier.java) | Industrial Evolution | Ambassadors, edicts, VPC chips, specialties; Courier Port shipping |
| [CADefense](../src/data/scripts/jonahtweaks/autopilot/CADefense.java), [CASpies](../src/data/scripts/jonahtweaks/autopilot/CASpies.java), [CAAutonomy](../src/data/scripts/jonahtweaks/autopilot/CAAutonomy.java) | Nexerelin | Emergency defense fleets; agents; autonomy (`Nex_GrantAutonomy`) |
| [CAWar](../src/data/scripts/jonahtweaks/autopilot/CAWar.java) | Nexerelin | War fleets by War level and stance, one launch counter a day: relief, proactive defense, retake, base strikes, strikes back, respawn watch, raids and blockades, invasions, bombardment, standing guard; Stand down and Recall all |
| [CAAotD](../src/data/scripts/jonahtweaks/autopilot/CAAotD.java) | AotD: Building Menu Overhaul | Research locks, hidden buildings, upgrade paths |
| [CASynergies](../src/data/scripts/jonahtweaks/autopilot/CASynergies.java) | AotD: Vaults of Knowledge | Industry synergies in scoring |
| [CADevelopment](../src/data/scripts/jonahtweaks/autopilot/CADevelopment.java) | AotD: Vaults of Knowledge | Management styles; Floating City fuel guard |
| [CAResearch](../src/data/scripts/jonahtweaks/autopilot/CAResearch.java) | AotD: Vaults of Knowledge | Research ranking and starting |
| [CAHypershunt](../src/data/scripts/jonahtweaks/autopilot/CAHypershunt.java) | AotD: Vaults of Knowledge | Coronal Hypershunt range and restoration; also the shared restoration helpers |
| [CAMegastructures](../src/data/scripts/jonahtweaks/autopilot/CAMegastructures.java) | AotD: Vaults of Knowledge | Pluto and Nidavelir |
| [CAContracts](../src/data/scripts/jonahtweaks/autopilot/CAContracts.java) | AotD: Theory of Toolbox | Trade contracts |
| [CASettlement](../src/data/scripts/jonahtweaks/autopilot/CASettlement.java) | Random Assortment of Things | Frontiers settlement |
| [CAAugments](../src/data/scripts/jonahtweaks/autopilot/CAAugments.java) | Station Augments | Augment loadouts (a knapsack within the station's points) |
| [CAAugmentModel](../src/data/scripts/jonahtweaks/autopilot/CAAugmentModel.java), [CAStationKit](../src/data/scripts/jonahtweaks/autopilot/CAStationKit.java) | Station Augments | What each augment does on a given station, read from the station's own ships, weapons, fighters and AI core |
| [CATasc](../src/data/scripts/jonahtweaks/autopilot/CATasc.java) | Terraforming & Station Construction | Terraforming projects, prerequisites, estimates |
| [CAGrand](../src/data/scripts/jonahtweaks/autopilot/CAGrand.java) | Grand.Colonies | 12-building screen limit; extra upkeep past the threshold |
| [CABrewery](../src/data/scripts/jonahtweaks/autopilot/CABrewery.java) | Substance.Abuse | Brewery only with a recipe in storage |
| [CAPrune](../src/data/scripts/jonahtweaks/autopilot/CAPrune.java) | Industries that stopped paying (0.2.44.1): an Auto colony's industry whose worth is all in what it makes, valued live like a build (`keep`: above the colony's other sources, its market share, what your colonies would be short of without it), below 0 for 30 days, is taken down, one per colony every 30 days |
| [CAMarket](../src/data/scripts/jonahtweaks/autopilot/CAMarket.java) | The market model (0.2.44.0): each good's sector market as the game counts it (`snap`: value, every exporter's weight = what it can ship x accessibility, your colonies), what a pick's line adds to your share (`pickGain`, the colony's largest single source, `gain`), buildings under construction counted as made (`claim`, `prune`), the same-pass check (`claimedPart`), the Diagnostics market section and the weekly check against the game's shares (`weekly`) |
| [CAMods](../src/data/scripts/jonahtweaks/autopilot/CAMods.java) | VIC, Planetary Shield, Roider Union, Unknown Skies, What We Left Behind, Astral Ascension, Yunru Industries, Slightly Better Tech-Mining, Niko's Scavengable Industries | Small integrations by id; the buildings left to you (`LEFT`) |
| [CACapital](../src/data/scripts/jonahtweaks/autopilot/CACapital.java) | Any | The capital (0.2.42.0): picks it (gathering point, Storage contents, size), keeps the production gathering point on it, puts its trade buildings (`COMMERCE`) in front of its plan once colonies ramp up, scores the mod ones only there, and frees an industry slot for them (`replacement`, through `CAEndgame.takeDown`) |
| [CALogistics](../src/data/scripts/jonahtweaks/autopilot/CALogistics.java) | Any | Storage logistics (also the capital's weekly gathering, where both hubs default to the capital and share one load): ships, weapons, wings and unknown blueprints to the refit hub, known blueprints and spares to the sell hub, shipped for a fee and travel time |
| [CAObjectives](../src/data/scripts/jonahtweaks/autopilot/CAObjectives.java) | Any (TME and AotD add to it) | Stable locations: comm relays, nav buoys, sensor arrays, TME's Domain-era upgrade, AotD's Hypershunt Receivers, valued by the colonies in the system |
| [CATascStations](../src/data/scripts/jonahtweaks/autopilot/CATascStations.java) | Terraforming & Station Construction | Mining and siphon stations founded as new colonies by a crew, placed the way TASC's Construct abilities do |
| [CAPanopticon](../src/data/scripts/jonahtweaks/autopilot/CAPanopticon.java) | Emergent Threats: IX Revival | Panopticon Instance and Core into the Surveillance Center |
| [CAOmega](../src/data/scripts/jonahtweaks/autopilot/CAOmega.java) | Omega Core Everywhere, Astral Ascension Renewed | Omega cores into buildings (valued from OCE's table) or as administrators (AAR) |

### UI and leftovers

| File | What it does |
|---|---|
| [CAOptionProvider](../src/data/scripts/jonahtweaks/autopilot/CAOptionProvider.java), [CAScreenButton](../src/data/scripts/jonahtweaks/autopilot/CAScreenButton.java), [CANav](../src/data/scripts/jonahtweaks/autopilot/CANav.java), [CATheme](../src/data/scripts/jonahtweaks/autopilot/CATheme.java), [CAColonyDialog](../src/data/scripts/jonahtweaks/autopilot/CAColonyDialog.java), [CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java), [CASetupUI](../src/data/scripts/jonahtweaks/autopilot/CASetupUI.java), [CAPresets](../src/data/scripts/jonahtweaks/autopilot/CAPresets.java), [CAFactionsPage](../src/data/scripts/jonahtweaks/autopilot/CAFactionsPage.java), [CAWarPage](../src/data/scripts/jonahtweaks/autopilot/CAWarPage.java), [CAExpansionPage](../src/data/scripts/jonahtweaks/autopilot/CAExpansionPage.java), [CAStoragePlugin](../src/data/scripts/jonahtweaks/autopilot/CAStoragePlugin.java) | See [The panels](#the-panels) |
| [JTCampaignScript](../src/data/scripts/jonahtweaks/JTCampaignScript.java), [JTPatronageCondition](../src/data/scripts/jonahtweaks/JTPatronageCondition.java) | Remove the retired `jt_patronage` condition from old saves; it has no effect |

## Conventions

- **Fail closed.** If something can't be read, the manager does nothing there and logs why once. It never guesses.
- **Never fight the player.**
  - It doesn't touch locked or hidden buildings, items or augments you installed, your research queue, or agents you hired.
  - Settings you change yourself on megastructures and the hypershunt stay yours until you hand them back.
- **Do it the game's way.** Builds, projects, contracts, agent orders and settlements are started the same way the game or the mod's own UI starts them. Where a mod's UI charges credits, so does the manager.
- **One step can't break the others.** Every step of the daily pass goes through `CAStep.run` or `CAStep.guard`.
- **One stability rule.** Anything that can cost a colony stability asks `CAStability.canLose` first (0.3.0).
- **One payment path.** Credits are taken and given back only in `CABudget` (`take`, `give`); callers use `pay`, `charge`, `payPrice`, `payFromPool` or `refund` (0.3.0). An offline check fails any other file that changes credits.
- **Numbers in one place.** Auto Colonies' own tuning numbers live in `CATuning` (0.3.0).
- **Save safety.** Saved data holds only ids, numbers and timestamps; a newly saved class needs an alias in `CAXStream` (0.3.0).
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
7. **Offline tests:** 75 suites in 0.3.0.0. Among them: the augment knapsack against brute force, synergy effects, management-style math, the colony screen button's placement, the planner over a recorded colony state (no duplicate producers, no build-then-remove inside a year), the money tiers and ladder waterfall, the Shipyard and stockpile limits, Auto-expansion costs and pacing, the stance and War level rules, a 2-year war simulation (infamy under 75, credits above the reserve, one invasion at a time), the presets round trip, the menu word budgets, the 0.2.45 migration, a 0.2.45 save loading with every value kept, and a scan that fails any file outside `CABudget` that changes credits.
8. **Nexerelin names:** every Nexerelin class and method the war and expansion code calls is checked against the installed jar.
