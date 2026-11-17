# Automating Starsector colonies

## Quick version

- The daily logic runs from a transient `EveryFrameScript`, and each step sits in its own `try`/`catch`.

## Running code every day

- I add the script with `Global.getSector().addTransientScript(...)` in `BaseModPlugin.onGameLoad`. Transient scripts aren't saved ([JTModPlugin](../src/data/scripts/jonahtweaks/JTModPlugin.java), [CAScript](../src/data/scripts/jonahtweaks/autopilot/CAScript.java)).
- For the clock, `getElapsedDaysSince(0L)` returns `Float.MAX_VALUE`, so use `getTimestamp()` ([CADay](../src/data/scripts/jonahtweaks/autopilot/CADay.java)).
- Any exception that reaches the game crashes it, so guard and count each step ([CAStep](../src/data/scripts/jonahtweaks/autopilot/CAStep.java)).

## Colonies and buildings

- `Misc.getPlayerMarkets(false)` gets your colonies. Nexerelin outposts show up in it too, and they can't export.
- To ask about a building, use `getIndustrySpec(id).getNewPluginInstance(market)` and then `isAvailableToBuild()`, `getBuildCost()` and `getBuildTime()`.
- To build, call `market.addIndustry(id)` and `getIndustry(id).startBuilding()`, then take the cost out yourself.
- To remove one, use `market.removeIndustry(id, MarketInteractionMode.REMOTE, false)`. If you pass a `null` mode, it deletes the core and item inside.

## Money

- Upkeep is `getUpkeep()` in credits times `max(1, size - 2)` times `market.getUpkeepMult()`.
- For supply and demand, there's `getMaxSupply()`, `getMaxDemand()`, `getDeficitQuantity()` and `getExportIncome()` on `CommodityOnMarketAPI` ([CAEngine.calibrate](../src/data/scripts/jonahtweaks/autopilot/CAEngine.java)).

## Items, cores, administrators

- For items, the spec's `getParams()` lists the industries, and `ItemEffectsRepo.ITEM_EFFECTS.get(itemId)` gives you the effect.
- To install one, call `industry.setSpecialItem(data)` with the exact `SpecialItemData` from the cargo stack, then `market.reapplyIndustries()`. If you use a copy, it can crash the colony every update.
- AI cores go in with `industry.setAICoreId(id)`. Every core takes 1 off each demand, Alpha and Beta cut upkeep to 75%, and an Alpha adds 1 to each output.
- Hegemony inspections only go after colonies over 7 AI core points (Alpha + Beta + Gamma).
- For administrators, take the person from a market's `OfficerManagerEvent` list, set their post and remove them from the list. Then call `addAdmin(person)`, take the hiring cost and call `market.setAdmin(person)`.
- Story point improvements use `industry.getImproveStoryPoints()` (the cost doubles per improvement at that colony), `spendStoryPoints(...)` and `setImproved(true)` ([CAStory](../src/data/scripts/jonahtweaks/autopilot/CAStory.java)).

## Your own storage tab

- A row in `data/campaign/submarkets.csv` plus a `BaseSubmarketPlugin` gets you a tab on every cargo screen ([CAStoragePlugin](../src/data/scripts/jonahtweaks/autopilot/CAStoragePlugin.java)).


## UI

- A `BaseIntelPlugin` with its own tag gets its own filter in the intel panel, and `createLargeDescription` gets you a full panel. Wrap `buttonPressConfirmed` too ([CAIntel](../src/data/scripts/jonahtweaks/autopilot/CAIntel.java)).
- `addPara` text is a format string, so a literal `%` crashes the game. Write `%%`.
- There's no API for the colony screen button, so [CAScreenButton](../src/data/scripts/jonahtweaks/autopilot/CAScreenButton.java) walks the UI tree, and it'll need fixing whenever the UI changes.

## The script loader and other mods

- The script loader refuses `java.lang.reflect`, most of `java.io` and `java.nio.file`, so use `MethodHandles` ([CARefl](../src/data/scripts/jonahtweaks/autopilot/CARefl.java)).
- If you don't want to compile against a mod, use `Class.forName(name, true, getScriptClassLoader())` plus method handles. If something's missing, do nothing and log it once, and check at load that it exists ([CACompat](../src/data/scripts/jonahtweaks/autopilot/CACompat.java)).

## Saves

- Removing a field from a saved class breaks loading, so keep it (unused) or use `omitField`.
- Give every saved class a short alias in `BaseModPlugin.configureXStream` before your first release ([CAXStream](../src/data/scripts/jonahtweaks/autopilot/CAXStream.java)).

## Valuing buildings with no formula

- Build it on a fake colony from `createMarket(...)` with the real colony's conditions, call `reapplyIndustries()` and read the numbers before and after. Then remove the building in a cleanup block.

## Game clock and production

- `getTimestamp()` counts milliseconds from 1970, so game days come out negative. Use NaN for "never", and treat a stamp later than today as stale ([CATime](../src/data/scripts/jonahtweaks/autopilot/CATime.java)).
- For custom production, `getPlayerFaction().getProduction().addItem(...)` queues ships that get charged at month end whatever your credits are, so take items off when money runs short ([CAShipyard](../src/data/scripts/jonahtweaks/autopilot/CAShipyard.java)).

## Other mods

- For Nexerelin fleet requests, make the intel with `(faction, from, target, fp, daysToLaunch)`, then call `setPlayerFee`, `setPlayerSpawned(true)`, `init()` and `modifyFleetRequestStock(-fp)`. An aggressive request against a non-hostile faction will make it hostile ([CAWar](../src/data/scripts/jonahtweaks/autopilot/CAWar.java)).
- For Nexerelin bombardment, `SatBombIntel` only applies infamy, so you have to apply the reputation penalties yourself, once ([CAScorched](../src/data/scripts/jonahtweaks/autopilot/CAScorched.java)).
