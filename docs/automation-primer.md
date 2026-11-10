# Automating Starsector colonies

## Quick version

- Notes from writing this mod, for anyone automating colonies by hand or in their own mod. It covers Starsector 0.98a's API. None of it needs this mod.
- Run daily logic from a transient `EveryFrameScript`, with each step in its own `try`/`catch`.
- Ask a building's spec before you build, and check units before trusting a number.
- To see what the mod changes in your game, [Auditing the mod](audit.md) is shorter.

## Running code every day

- Add an `EveryFrameScript` in `BaseModPlugin.onGameLoad` with `Global.getSector().addTransientScript(...)`. Transient scripts aren't saved, so they can't break a save ([JTModPlugin](../src/data/scripts/jonahtweaks/JTModPlugin.java), [CAScript](../src/data/scripts/jonahtweaks/autopilot/CAScript.java)).
- Count days with `getClock().convertToDays(amount)` in `advance`.
- **Clock:** `getClock().getElapsedDaysSince(0L)` returns `Float.MAX_VALUE`. Work out the day number from `getTimestamp()` ([CADay](../src/data/scripts/jonahtweaks/autopilot/CADay.java)).
- An exception that reaches the game crashes it. Guard every step.

## Colonies and buildings

- Your colonies: `Misc.getPlayerMarkets(false)`. Nexerelin player outposts show up too but can't export, so this mod skips them.
- Ask without building: `getIndustrySpec(id).getNewPluginInstance(market)`, then `isAvailableToBuild()`, `getBuildCost()`, `getBuildTime()`.
- Build: `market.addIndustry(id)`, `getIndustry(id).startBuilding()`, and take the cost yourself. One new building at a time (`Misc.getCurrentlyBeingConstructed`). Upgrades use `startUpgrading()` and can run alongside ([CABuild](../src/data/scripts/jonahtweaks/autopilot/CABuild.java)).
- Slots: `Misc.getMaxIndustries` and `Misc.getNumIndustries`.
- Remove: `market.removeIndustry(id, MarketInteractionMode.REMOTE, false)`. With a `null` mode the game deletes the core and item inside.
- The colony screen shows 12 buildings. A 13th can't be managed.

## Money

- **Upkeep.** `getUpkeep()` is already in credits. A building's upkeep is that times `max(1, size - 2)` times `market.getUpkeepMult()`. This mod once multiplied by 500 twice and built whatever had the least upkeep.
- **Supply and demand.** `getMaxSupply()`, `getMaxDemand()`, `getDeficitQuantity()` and `getExportIncome()` on `CommodityOnMarketAPI`. Export income against supply across your colonies gives a credits-per-unit figure ([CAEngine.calibrate](../src/data/scripts/jonahtweaks/autopilot/CAEngine.java)).
- **Deposits.** `ResourceDepositsCondition.COMMODITY`, `MODIFIER`, `INDUSTRY` and `BASE_MODIFIER` are the game's tables. Mod deposits register there too.
- **Last month's net income.** `SharedData.getData().getPreviousReport()`, then `computeTotals()` ([CABudget](../src/data/scripts/jonahtweaks/autopilot/CABudget.java)).

## Items, cores, administrators

- **Items.** The spec's `getParams()` lists the industries; `ItemEffectsRepo.ITEM_EFFECTS.get(itemId)` gives the effect. Install with `industry.setSpecialItem(data)` using the **exact** `SpecialItemData` from the cargo stack, then `market.reapplyIndustries()`. A copy loses subclass state and can crash the colony every update.
- **Nanoforge pollution.** A polluting item in Heavy Industry on a habitable world pollutes the day it goes in and makes it permanent after 90 days. Upgrading the forge starts it again.
- **AI cores.** `industry.setAICoreId(id)`. Every core takes 1 off each demand, Alpha and Beta cut upkeep to 75%, and an Alpha adds 1 to each output.
- **Hegemony inspections.** It only picks a colony holding more AI core points than an Alpha, a Beta and a Gamma together (7). Stay at 7 or under and it never inspects.
- **Administrators.** The person comes from a market's `OfficerManagerEvent` list. Set their post, remove them from the list, `addAdmin(person)`, take the hiring cost, then `market.setAdmin(person)`.
- **Story points.** `industry.getImproveStoryPoints()` for the cost (it doubles per improvement at that colony), `spendStoryPoints(...)`, `setImproved(true)` ([CAStory](../src/data/scripts/jonahtweaks/autopilot/CAStory.java)).

## Your own storage tab

A submarket gives a tab on every market's cargo screen: a row in `data/campaign/submarkets.csv` and a `BaseSubmarketPlugin`. Two traps:

- Create the cargo with `createCargo(true)` and call `initMothballedShips("player")`. Other mods crash on a null ship list.
- A save with your submarket can't load without your mod.

See [CAStoragePlugin](../src/data/scripts/jonahtweaks/autopilot/CAStoragePlugin.java).

## UI

- **Intel panel.** A `BaseIntelPlugin` with its own tag gets its own filter, and `createLargeDescription` gets a full panel. Wrap `buttonPressConfirmed` ([CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java)).
- **`addPara` text is a format string.** A literal `%` throws and crashes the game. Write `%%`.
- **Right-click option on a building:** `IndustryOptionProvider` through the listener manager ([CAOptionProvider](../src/data/scripts/jonahtweaks/autopilot/CAOptionProvider.java)).
- **Colony screen button:** no API. [CAScreenButton](../src/data/scripts/jonahtweaks/autopilot/CAScreenButton.java) walks the UI tree and places one in free space. Expect to fix it when the game's UI changes.

## The script loader and other mods

- **The loader refuses some classes**, including `java.lang.reflect`, most of `java.io` and `java.nio.file`. Use `MethodHandles` ([CARefl](../src/data/scripts/jonahtweaks/autopilot/CARefl.java)).
- **Optional mods.** Keep integration in its own class, call it only behind `isModEnabled(id)`, and share constants as `static final String` literals.
- **Without compiling against a mod:** `Class.forName(name, true, getScriptClassLoader())` plus method handles. If something's missing, do nothing and log once. Check at load that everything you reach still exists ([CACompat](../src/data/scripts/jonahtweaks/autopilot/CACompat.java)).

## Saves

- `getPersistentData()` is saved with XStream. Store plain values and ids, never another mod's objects.
- A field added later loads as zero, false or null in an older save, so make those mean "off".
- A field you remove breaks loading of saves that have it. Keep it, unused.

## Valuing buildings with no formula

Build them on a fake colony from `createMarket(...)`. Copy the real colony's conditions, add the building, `reapplyIndustries()`, and read the numbers before and after. Remove the building in `finally`. The fake colony's upkeep multiplier rose 25% whatever was built, so that number is ignored ([CASimulator](../src/data/scripts/jonahtweaks/autopilot/CASimulator.java)).

## Other mods

- **Nexerelin fleet requests.** Make the intel the dialog makes with `(faction, from, target, fp, daysToLaunch)`, then `setPlayerFee`, `setPlayerSpawned(true)`, `init()`, and take the points with `modifyFleetRequestStock(-fp)`. An aggressive request against a non-hostile faction makes it hostile ([CAWar](../src/data/scripts/jonahtweaks/autopilot/CAWar.java)).
- **Terraforming Made Easy.** Projects toggle a condition, so a stale plan can undo itself. Re-plan whenever conditions change.
- **Ashes of the Domain.** Ask Building Menu Overhaul what's locked and what upgrades into what ([CAAotD](../src/data/scripts/jonahtweaks/autopilot/CAAotD.java)).

## Coming in 3.0

- Built, in testing: saved classes use fixed XStream aliases, so classes can be moved or renamed without breaking saves.
- Built, in testing: each daily step runs separately. A step that fails 3 days in a row is stopped for the session. Safety steps are never stopped.
- Built, in testing: all timers use the game clock, and every check that can lower stability uses one shared rule.
- Built, in testing: new and existing buildings are valued by the same code, and all builds go through one ranked queue.

Plans can still change before release.

Everything planned for 3.0: [roadmap](roadmap.md)
