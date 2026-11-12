# Automating Starsector colonies

## Quick version

- Daily logic: run from a transient `EveryFrameScript`, each step in its own `try`/`catch`.

## Running code every day

- Script: `Global.getSector().addTransientScript(...)` in `BaseModPlugin.onGameLoad`. Transient scripts are not saved ([JTModPlugin](../src/data/scripts/jonahtweaks/JTModPlugin.java), [CAScript](../src/data/scripts/jonahtweaks/autopilot/CAScript.java)).
- Clock: `getElapsedDaysSince(0L)` returns `Float.MAX_VALUE`. Use `getTimestamp()` ([CADay](../src/data/scripts/jonahtweaks/autopilot/CADay.java)).
- Exceptions: an exception that reaches the game crashes it. Guard and count each step ([CAStep](../src/data/scripts/jonahtweaks/autopilot/CAStep.java)).

## Colonies and buildings

- Your colonies: `Misc.getPlayerMarkets(false)`. Nexerelin outposts appear in it and cannot export.
- Ask: `getIndustrySpec(id).getNewPluginInstance(market)`, then `isAvailableToBuild()`, `getBuildCost()`, `getBuildTime()`.
- Build: `market.addIndustry(id)`, `getIndustry(id).startBuilding()`, take the cost yourself.
- Remove: `market.removeIndustry(id, MarketInteractionMode.REMOTE, false)`. A `null` mode deletes the core and item inside.

## Money

- Upkeep: `getUpkeep()` in credits times `max(1, size - 2)` times `market.getUpkeepMult()`.
- Supply and demand: `getMaxSupply()`, `getMaxDemand()`, `getDeficitQuantity()`, `getExportIncome()` on `CommodityOnMarketAPI` ([CAEngine.calibrate](../src/data/scripts/jonahtweaks/autopilot/CAEngine.java)).

## Items, cores, administrators

- Items: the spec's `getParams()` lists the industries. `ItemEffectsRepo.ITEM_EFFECTS.get(itemId)` gives the effect.
- Install: `industry.setSpecialItem(data)` with the exact `SpecialItemData` from the cargo stack, then `market.reapplyIndustries()`. A copy can crash the colony every update.
- AI cores: `industry.setAICoreId(id)`. Every core takes 1 off each demand. Alpha and Beta cut upkeep to 75%. An Alpha adds 1 to each output.
- Hegemony inspections: only a colony holding more than 7 AI core points (Alpha + Beta + Gamma).
- Administrators: take the person from a market's `OfficerManagerEvent` list, set their post, remove from the list, `addAdmin(person)`, take the hiring cost, `market.setAdmin(person)`.
- Story points: `industry.getImproveStoryPoints()` (doubles per improvement at that colony), `spendStoryPoints(...)`, `setImproved(true)` ([CAStory](../src/data/scripts/jonahtweaks/autopilot/CAStory.java)).

## Your own storage tab

- Storage tab: A row in `data/campaign/submarkets.csv` and a `BaseSubmarketPlugin` give a tab on every cargo screen ([CAStoragePlugin](../src/data/scripts/jonahtweaks/autopilot/CAStoragePlugin.java)).


## UI

- Intel panel: a `BaseIntelPlugin` with its own tag gets its own filter. `createLargeDescription` gets a full panel. Wrap `buttonPressConfirmed` ([CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java)).
- `addPara` text: a format string. A literal `%` crashes the game. Write `%%`.
- Colony screen button: no API. [CAScreenButton](../src/data/scripts/jonahtweaks/autopilot/CAScreenButton.java) walks the UI tree and needs fixing when the UI changes.

## The script loader and other mods

- Script loader: refuses `java.lang.reflect`, most of `java.io` and `java.nio.file`. Use `MethodHandles` ([CARefl](../src/data/scripts/jonahtweaks/autopilot/CARefl.java)).
- Without compiling against a mod: `Class.forName(name, true, getScriptClassLoader())` plus method handles. If something is missing, do nothing and log once. Check at load that it exists ([CACompat](../src/data/scripts/jonahtweaks/autopilot/CACompat.java)).

## Saves

- Removed fields: break loading. Keep them, unused, or `omitField`.
- Aliases: give every saved class a short alias in `BaseModPlugin.configureXStream` before the first release ([CAXStream](../src/data/scripts/jonahtweaks/autopilot/CAXStream.java)).

## Valuing buildings with no formula

- Method: build on a fake colony from `createMarket(...)` with the real colony's conditions, call `reapplyIndustries()`, read the numbers before and after, remove the building in a cleanup block.

## Game clock and production

- Timestamps: `getTimestamp()` counts milliseconds from 1970, so game days are negative. Use NaN for "never". A stamp later than today is stale ([CATime](../src/data/scripts/jonahtweaks/autopilot/CATime.java)).
- Custom production: `getPlayerFaction().getProduction().addItem(...)` queues ships charged at month end whatever your credits. Take items off when money runs short ([CAShipyard](../src/data/scripts/jonahtweaks/autopilot/CAShipyard.java)).

## Other mods

- Nexerelin fleet requests: make the intel with `(faction, from, target, fp, daysToLaunch)`, then `setPlayerFee`, `setPlayerSpawned(true)`, `init()`, `modifyFleetRequestStock(-fp)`. An aggressive request against a non-hostile faction makes it hostile ([CAWar](../src/data/scripts/jonahtweaks/autopilot/CAWar.java)).
- Nexerelin bombardment: `SatBombIntel` applies only infamy. Apply the reputation penalties once ([CAScorched](../src/data/scripts/jonahtweaks/autopilot/CAScorched.java)).
