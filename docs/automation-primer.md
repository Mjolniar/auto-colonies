# Automating Starsector colonies

What we learned writing this mod, for anyone who wants to automate colonies themselves, by hand or in their own mod. It's about Starsector 0.98a's API and how the game actually behaves, with pointers to where this mod does each thing. None of it needs this mod.

If you only want to know what this mod changes in your game, [Auditing the mod](audit.md) is the shorter read.

## Running code every day

- A mod plugin (`BaseModPlugin`) gets `onGameLoad(newGame)` on every save load. Add your daily logic there as an `EveryFrameScript` with `Global.getSector().addTransientScript(...)`. Transient scripts aren't saved, so they can't break a save if your mod changes; you just add them again each load. See [JTModPlugin](../src/data/scripts/jonahtweaks/JTModPlugin.java) and [CAScript](../src/data/scripts/jonahtweaks/autopilot/CAScript.java).
- Count days with `Global.getSector().getClock().convertToDays(amount)` in `advance`. Don't do heavy work every frame.
- **Trap:** `getClock().getElapsedDaysSince(0L)` returns `Float.MAX_VALUE`, not the days since the start. If you cache something "once a day" by comparing against that, it only ever runs once per loaded save. Work out today's day number from `getTimestamp()` yourself ([CADay](../src/data/scripts/jonahtweaks/autopilot/CADay.java)).
- Wrap each step of your daily pass in its own `try`/`catch`. One mod's integration throwing shouldn't stop everything else, and an exception that reaches the game crashes it.

## Colonies and buildings

- Your colonies: `Misc.getPlayerMarkets(false)`. Nexerelin's player outposts show up there too; they sit in an economy of their own (`$nex_playerOutpost` in market memory) and can't export, so this mod leaves them alone.
- To ask about a building without building it: `Global.getSettings().getIndustrySpec(id).getNewPluginInstance(market)`, then `isAvailableToBuild()`, `getBuildCost()`, `getBuildTime()`. That's how the build menu decides too. Vanilla's default `isAvailableToBuild` is just "the colony has Population & Infrastructure"; some buildings add their own rule (Military Base needs a working spaceport).
- To build: `market.addIndustry(id)`, then `market.getIndustry(id).startBuilding()`, and take the cost from the player's credits yourself. The game allows one new building at a time: check `Misc.getCurrentlyBeingConstructed(market)`. Upgrades are `industry.startUpgrading()` and can run alongside. See [CABuild](../src/data/scripts/jonahtweaks/autopilot/CABuild.java).
- Industry slots: `Misc.getMaxIndustries(market)` and `Misc.getNumIndustries(market)`. Only specs tagged `industry` take a slot.
- To remove one: `market.removeIndustry(id, MarketInteractionMode.REMOTE, false)`. With a `null` mode the game deletes the AI core and item inside instead of returning them, so take them out first or pass a mode.
- The colony screen can only show 12 buildings. Build a 13th and you can't see or manage it.

## Money

- **Upkeep:** a spec's `getUpkeep()` is already in credits (the game multiplies the CSV value by 500 when it loads). A building's upkeep is that times `max(1, size - 2)` times `market.getUpkeepMult()`. This mod once multiplied by 500 a second time and decided every building was ruinous, so it built whatever had the least upkeep.
- **What a colony makes:** `CommodityOnMarketAPI.getMaxSupply()` is local production, `getMaxDemand()` is what it needs, `getDeficitQuantity()` is the shortfall, `getExportIncome()` is what exports earn. Comparing export income to supply over all your colonies gives a good credits-per-unit figure for valuing new output ([CAEngine.calibrate](../src/data/scripts/jonahtweaks/autopilot/CAEngine.java)).
- **Deposits:** `ResourceDepositsCondition.COMMODITY`, `MODIFIER`, `INDUSTRY` and `BASE_MODIFIER` are the game's own tables; output is size + base modifier + the deposit's modifier. Mods that add deposits register in the same tables, so using them gets you mod deposits for free.
- **Last month's net income:** `SharedData.getData().getPreviousReport()`, then `computeTotals()` and `getRoot().totalIncome - totalUpkeep` ([CABudget](../src/data/scripts/jonahtweaks/autopilot/CABudget.java)).

## Colony items and AI cores

- Which buildings an item goes in: the item spec's `getParams()` (comma-separated industry ids). What it does: `ItemEffectsRepo.ITEM_EFFECTS.get(itemId)`, and `effect.getUnmetRequirements(industry)` says whether it can go in. Any mod that registers its item there works the same way.
- Install with `industry.setSpecialItem(data)` using the **exact** `SpecialItemData` object from the cargo stack, then `market.reapplyIndustries()`. Some mods keep state in a subclass of `SpecialItemData`; a copy loses it (Substance.Abuse's Recipe Book crashes the colony every update with a copy).
- **Nanoforge pollution:** vanilla's Heavy Industry (and anything built on it) with a polluting item on a habitable world adds pollution the day it goes in, makes it permanent after 90 days, and removes it if the item comes out before then (`HeavyIndustry.updatePollutionStatus`: `addedPollution`, `permaPollution`, `daysWithNanoforge`). Upgrading the forge makes a new building, which pollutes again.
- **AI cores:** `industry.setAICoreId(id)`. In vanilla every core takes 1 off each demand, Alpha and Beta cut upkeep to 75%, and an Alpha adds 1 to each output, or in a structure its own bonus instead (fleet size, station strength, ground defense and so on).
- **Hegemony inspections:** the Hegemony only picks a colony holding more AI core points than an Alpha, a Beta and a Gamma together (7). Below size 4 a colony adds nothing to its crisis. Staying at 7 or under per colony means it never inspects you.

## Administrators, policies, story points

- Hiring an administrator remotely the way the comm directory does: the person comes from a market's `OfficerManagerEvent` list; the game sets their post, removes them from the list, calls `getCharacterData().addAdmin(person)` and takes the hiring cost. Then `market.setAdmin(person)`.
- Hazard pay and free port are plain market toggles; the game charges for hazard pay itself.
- Story point improvements: `industry.getImproveStoryPoints()` for the cost (it doubles with each improvement at that colony), `getPlayerStats().spendStoryPoints(...)`, then `industry.setImproved(true)`. See [CAStory](../src/data/scripts/jonahtweaks/autopilot/CAStory.java).

## Your own storage tab

A submarket gives you a tab on every market's cargo screen: a row in `data/campaign/submarkets.csv` and a `BaseSubmarketPlugin`. Override `getCargo()` to return your own cargo, and `isIllegalOnSubmarket` to refuse what shouldn't go in. Two traps:
- Create the cargo with `Global.getFactory().createCargo(true)` and call `initMothballedShips("player")` on it, because other mods walk every submarket's ship list and crash on a null one.
- Once a save has your submarket on its markets, it can't load without your mod. Plan for that.

See [CAStoragePlugin](../src/data/scripts/jonahtweaks/autopilot/CAStoragePlugin.java) and [CAStores](../src/data/scripts/jonahtweaks/autopilot/CAStores.java).

## UI

- **An intel panel** is the easiest place for a big UI: a `BaseIntelPlugin` with your own tag in `getIntelTags` gives it its own filter on the Intel screen, and `createLargeDescription` gets a full panel. Buttons come back through `buttonPressConfirmed`; wrap everything in there, because an exception crashes the game ([CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java)).
- **`addPara` text is a format string.** A literal `%` in it (like "25%") throws and crashes the game. Write `%%`, and escape anything dynamic you put in the format part. This mod's build fails on a stray `%`.
- **A right-click option on a building** (Population & Infrastructure here): `IndustryOptionProvider`, registered through `Global.getSector().getListenerManager()` ([CAOptionProvider](../src/data/scripts/jonahtweaks/autopilot/CAOptionProvider.java)).
- **A button on the colony screen** has no API. [CAScreenButton](../src/data/scripts/jonahtweaks/autopilot/CAScreenButton.java) walks the UI tree (`getCore`, `getCurrentTab`, `getChildrenCopy`), finds the building grid, and places a button in free space, re-checking a few times a second. It works, but expect to fix it when the game's UI changes.

## The script loader and other mods

- **The game refuses some classes in mod code.** Its script loader throws for anything naming `java.lang.reflect` (except a few types), most of `java.io`, `java.nio.file` and a few others. So no ordinary reflection: use `java.lang.invoke.MethodHandles` ([CARefl](../src/data/scripts/jonahtweaks/autopilot/CARefl.java)). Compile-time success means nothing here; this mod's build loads the jar under the game's own loader to check.
- **Optional mods:** never let a class that loads without the other mod mention that mod's classes. Put the integration in its own class, only call it behind `Global.getSettings().getModManager().isModEnabled(id)`, and keep shared constants as `static final String` literals (they're copied into the caller at compile time, so the class isn't loaded). Then verify your jar's bytecode without the optional jars on the classpath.
- **Reaching a mod without compiling against it:** `Class.forName(name, true, Global.getSettings().getScriptClassLoader())` plus method handles. Fail closed: if a field or method isn't there, do nothing and log it once, rather than guessing. Check at load that everything you reach still exists, so a mod update shows up as "integration off" instead of a crash ([CACompat](../src/data/scripts/jonahtweaks/autopilot/CACompat.java), [CADiagnostics](../src/data/scripts/jonahtweaks/autopilot/CADiagnostics.java)).

## Saves

- Everything in `Global.getSector().getPersistentData()` is saved with XStream. Store plain values and ids, never another mod's objects, so removing that mod doesn't break the save.
- A field added later loads as zero, false or null in an older save: pick defaults that mean "off" at those values, and create collections lazily.
- **A field you remove breaks loading** of saves that have it. Keep removed fields, unused.

## Valuing buildings nobody wrote a formula for

Build them on a fake colony. `Global.getFactory().createMarket(...)` gives a market that isn't in the economy or the sector; copy the real colony's vanilla conditions onto it, add the building, `reapplyIndustries()`, and read supply, demand, stability, accessibility, defense, fleet size and quality before and after. Remove the building in `finally`, and remove any sector script or intel it registered while it was there, then don't trust that building's numbers. Expect some noise: in our games the fake colony's upkeep multiplier rose 25% whatever was built, so that number is ignored ([CASimulator](../src/data/scripts/jonahtweaks/autopilot/CASimulator.java)).

## Other mods, quickly

- **Nexerelin fleet requests:** make the intel the dialog makes (`DefenseFleetIntel`, `BaseStrikeIntel`, `NexRaidIntel`, `ReliefFleetIntelAlt.createEvent`) with `(faction, from, target, fp, daysToLaunch)`, then `setPlayerFee`, `setPlayerSpawned(true)`, `init()`, and take the points with `InvasionFleetManager.getManager().modifyFleetRequestStock(-fp)`. Price: fleet points times `NexConfig.fleetRequestCostPerFP`, except relief, which is twice `Nex_StabilizePackage.getNominalCost(target)`. An aggressive request against a faction that isn't hostile makes it hostile ([CAWar](../src/data/scripts/jonahtweaks/autopilot/CAWar.java)).
- **Nexerelin agents:** `CovertOpsManager` and `AgentIntel`; the defensive orders have their own calls (`issueCOINOrder`, `issueCellKillOrder`, `issueBaseFindOrder`), the rest is an action added to the agent ([CASpies](../src/data/scripts/jonahtweaks/autopilot/CASpies.java)).
- **Terraforming Made Easy:** projects toggle a condition (the same project adds it if it's missing and removes it if it's there), so a stale plan can undo itself. Re-plan whenever a colony's conditions change ([CATerraformSurplus](../src/data/scripts/jonahtweaks/autopilot/CATerraformSurplus.java), [CATerraSim](../src/data/scripts/jonahtweaks/autopilot/CATerraSim.java)).
- **Ashes of the Domain:** Building Menu Overhaul decides what's locked and what upgrades into what; ask it the same questions its build menu asks ([CAAotD](../src/data/scripts/jonahtweaks/autopilot/CAAotD.java)). Vaults of Knowledge's research manager is reachable by reflection; to value a tech, mark it researched for one scoring pass and put it back in `finally` ([CAResearch](../src/data/scripts/jonahtweaks/autopilot/CAResearch.java)).
