# Auditing the mod

If you want to check this mod is safe and fair before you put it in your game or a modpack, this page is the short way to do it. It lists everything the mod can change in your game, what each of those costs, what it never does, and how to check all of that yourself in the code. File links go to the source on GitHub. For how the code fits together in general, see the [source guide](source-guide.md).

## The short version

- It only manages **your own colonies**, the way you could through the game's UI, or the UI of the mod a feature comes from. Every action charges what that UI charges, plus a small convenience fee you can turn off.
- It doesn't change any game setting, ship, weapon, faction, market condition or other mod's numbers. No balance changes, no cheats (the old storage cheat and the Derelict Empire attack settings were removed in 2.37.0).
- No file access beyond reading its own default settings through the game's loader, no network access, no `java.lang.reflect` (the game's script loader refuses it anyway; other mods are reached through method handles).
- Nothing moves across the sector for free. Items, cores and databanks that go from one colony to another are shipped for a fee and travel time.
- It never invades, never declares war, and never targets a faction you're not already at war with.
- It's safe to add to a running save. It's not safe to remove from a save that used it, because the Autopilot Storage tab is a market submarket.

## Everything it can change in your game

Each row is a kind of change, where it happens, and what it costs. "Fee" means the convenience fee (Standard: 5% of a build's cost, at least 5,000, or a flat fee for small actions; Off makes it zero).

| What | Where in the code | What it costs |
|---|---|---|
| Starts a building or an upgrade | [CABuild.start](../src/data/scripts/jonahtweaks/autopilot/CABuild.java) (`addIndustry` + `startBuilding`, or `startUpgrading`) | The game's build cost and build time, plus the fee. One new building at a time per colony, like the game. |
| Ashes of the Domain upgrade paths | [CAAotD.startUpgrade](../src/data/scripts/jonahtweaks/autopilot/CAAotD.java) | Same as above. It points the building's upgrade at the AotD child for the one call and restores it in `finally`, the way BMO's menu does it. |
| Takes a building down | [CAEndgame.takeDown](../src/data/scripts/jonahtweaks/autopilot/CAEndgame.java), [CATerraformSurplus](../src/data/scripts/jonahtweaks/autopilot/CATerraformSurplus.java), [CASurplusResearch](../src/data/scripts/jonahtweaks/autopilot/CASurplusResearch.java) | Free, like the game. Only buildings it built itself (terraforming, research), in the endgame a weak non-military industry, at the capital (2.42.0) its weakest ordinary industry to make room for a trade building, one every 90 days, or (2.42.1) the weakest industry of a colony over its industry limit, one every 30 days; never locked or improved ones, defenses, fleets or ship production. Its AI core and item go to that colony's Autopilot Storage first. |
| Installs or swaps a colony item, installs or moves an AI core | [CAItems](../src/data/scripts/jonahtweaks/autopilot/CAItems.java) (`placeItem`, `swapItem`, `placeCore`) | A small fee. Only from Autopilot Storage, and only at the colony it's stored at; anywhere else it's shipped first. Replaced items and cores go back to storage. Never a polluting nanoforge on a habitable world. |
| Moves your production gathering point to the capital (2.42.0, on by default) | [CACapital](../src/data/scripts/jonahtweaks/autopilot/CACapital.java) | Free, like changing it yourself in the custom production screen. If you move it to another colony yourself, that colony becomes the capital instead. |
| Ships your Storage to a refit or sell hub (Storage logistics, off unless you switch it on; the capital's weekly gathering, on by default) | [CALogistics](../src/data/scripts/jonahtweaks/autopilot/CALogistics.java) | What an Autopilot Storage shipment costs plus 1,000 a ship, from the monthly budget, and the same travel time. Only Storage at markets that aren't hostile to you; never colony items, AI cores or Autopilot Storage. |
| Builds a comm relay, nav buoy, sensor array or Hypershunt Receiver at a stable location, or upgrades one to Domain-era (TME) | [CAObjectives](../src/data/scripts/jonahtweaks/autopilot/CAObjectives.java) | The materials at base price plus a fee, from the monthly budget, and 10 days for the crew (30 for a receiver). A Domain-era upgrade takes a spare Beta core and a receiver a Coronal Hypershunt Tap from Autopilot Storage. Both come back if the spot's gone. |
| Puts a Panopticon Instance or Core in a Surveillance Center (IX Revival) | [CAPanopticon](../src/data/scripts/jonahtweaks/autopilot/CAPanopticon.java) | The standard install fee; shipped there first from another colony. |
| Places an Omega core (Omega Core Everywhere / Astral Ascension Renewed) | [CAOmega](../src/data/scripts/jonahtweaks/autopilot/CAOmega.java) | A small fee, from Autopilot Storage like other cores (shipped first if it's at another colony). In a building with `setAICoreId`; as an administrator through Astral Ascension Renewed's own AI core administrator plugin, only where your inspection limit allows its 10 points. Never moved or removed once placed. |
| Ships something between colonies | [CAStores.ship](../src/data/scripts/jonahtweaks/autopilot/CAStores.java) | 2,000 plus 300 a light-year (times the fee level), and 2 days plus a day a light-year before it arrives. |
| Hires and places administrators | [CAItems](../src/data/scripts/jonahtweaks/autopilot/CAItems.java) (`hireAdmin`, `assignAdmin`, `moveAdmin`) | The game's hiring cost and salary, plus the fee. Hired from another market's comm directory, so they take 30 days to arrive. |
| Hazard pay, free port | [CAPolicies](../src/data/scripts/jonahtweaks/autopilot/CAPolicies.java) | What the game charges for them. The same toggles as the colony screen. |
| Story point improvements | [CAStory](../src/data/scripts/jonahtweaks/autopilot/CAStory.java) | The game's story point cost. Off unless you allow it. |
| Hegemony inspection orders | [CACrisis](../src/data/scripts/jonahtweaks/autopilot/CACrisis.java) | Only sets Resist, and only if you choose "Resist where defended". The default only advises. |
| Terraforming projects (TME, TASC) | [CATerraform](../src/data/scripts/jonahtweaks/autopilot/CATerraform.java), [CATerraformSurplus](../src/data/scripts/jonahtweaks/autopilot/CATerraformSurplus.java), [CATasc](../src/data/scripts/jonahtweaks/autopilot/CATasc.java) | The mod's own project cost, started the way its dialog does. |
| Research (Vaults of Knowledge) | [CAResearch](../src/data/scripts/jonahtweaks/autopilot/CAResearch.java) | VoK's own cost (`payForResearch`). Items it needs are moved into the Research Facility tab at that colony, or shipped there first. While valuing techs it marks candidates researched for one scoring pass and always puts them back (`finally`, retried and logged if that ever fails). |
| Trade contracts (Theory of Toolbox) | [CAContracts](../src/data/scripts/jonahtweaks/autopilot/CAContracts.java) | Accepted the way ToT's UI does; ToT handles payment. |
| Megastructures, wonders, Bifrost gates (AotD) | [CAHypershunt](../src/data/scripts/jonahtweaks/autopilot/CAHypershunt.java), [CAMegastructures](../src/data/scripts/jonahtweaks/autopilot/CAMegastructures.java), [CAWonders](../src/data/scripts/jonahtweaks/autopilot/CAWonders.java) | AotD's own settings and restoration or construction contracts, paid the way AotD charges them (goods from your exports). |
| Nexerelin fleet requests | [CADefense](../src/data/scripts/jonahtweaks/autopilot/CADefense.java), [CAWar](../src/data/scripts/jonahtweaks/autopilot/CAWar.java) | Nexerelin's own price and fleet request points, launched the way its fleet request dialog does. Never invasions or colony expeditions; aggressive fleets only against factions already hostile to you. |
| Nexerelin agents | [CASpies](../src/data/scripts/jonahtweaks/autopilot/CASpies.java) | Recruiting: 12,000 plus the fee, 60 days before the agent reports. Operations: Nexerelin's own cost, ordered through its own agent API. Offense only against factions already at war with you. |
| Nexerelin autonomy | [CAAutonomy](../src/data/scripts/jonahtweaks/autopilot/CAAutonomy.java) | Nexerelin's own grant and revoke, including its unrest. Recommend only by default; Auto is money-only and never for new colonies. |
| RAT Frontiers settlement | [CASettlement](../src/data/scripts/jonahtweaks/autopilot/CASettlement.java) | RAT's founding and facility costs, built the way RAT's dialog builds it. Founding from afar sends an expedition that takes 10 days plus a day a light-year; refunded if it can't found it. |
| Station augments | [CAAugments](../src/data/scripts/jonahtweaks/autopilot/CAAugments.java) | Station Augments' own install and removal (free there). From afar, a refit crew makes the change 7 days later; at once if your fleet is in the system. |
| Industrial Evolution | [CAIndEvo](../src/data/scripts/jonahtweaks/autopilot/CAIndEvo.java), [CACourier](../src/data/scripts/jonahtweaks/autopilot/CACourier.java) | Ambassadors (fee, 30 days to arrive), edicts (never the harsh ones), VPC chips (fee, shipped if needed), specialties, and Courier Port contracts at IndEvo's shipping price. |
| Antimatter Stabilizer (Emergent Threats) | [CAItems.convertFuel](../src/data/scripts/jonahtweaks/autopilot/CAItems.java) | The stabilizer and a fee, the way ET's portmaster does it. Only with Allow removals on. |

The only place it ever **adds** credits is a refund: a settlement expedition that can't found its settlement gives back what it took.

## What it never does

- Changes `Global.getSettings()` values, specs, conditions or other mods' data. (The one exception, the AotD upgrade pointer above, is put back in the same call.)
- Creates goods, credits, story points, items, cores or ships.
- Moves anything between colonies without shipping it.
- Uses information the game doesn't show you (for example, enemy agent operations against you; that was removed in 2.37.0).
- Touches buildings, items or augments you locked or installed yourself, your research queue, or agents you didn't hand over.
- Reads or writes files, or opens network connections.

## The test colony

To value mod buildings it has no formula for, [CASimulator](../src/data/scripts/jonahtweaks/autopilot/CASimulator.java) creates a throwaway market (`Global.getFactory().createMarket`) that's never added to the economy or the sector, copies the colony's vanilla conditions onto it, adds the building, reads the effects and removes the building in `finally`. Any sector script or intel the building registers while it's there is removed and the building is marked as not measurable. Nothing about it is saved.

## What's saved

Everything lives under one persistent-data key, `jt_colony_autopilot`, as plain values and ids: settings, per-colony state, the log, each colony's Autopilot Storage cargo and shipments. Nothing from another mod is stored, only ids, so removing an optional mod doesn't break a save. The full list is in the [source guide](source-guide.md#whats-saved).

## How to check it yourself

The quickest audit is a handful of searches across `src/`:

| Search for | What you'll find |
|---|---|
| `getCredits().subtract` | Every place it spends your credits (build costs, fees, shipping, hires, fleet requests, agent operations, research) |
| `getCredits().add` | The one refund |
| `addIndustry`, `removeIndustry`, `startUpgrading` | Every building change |
| `setSpecialItem`, `setAICoreId` | Every item and core move |
| `Global.getSettings().set` | Nothing (no game setting is changed) |
| `java.io`, `java.net`, `java.lang.reflect` | Nothing |
| `CARefl` | Every reflective call into another mod, all through method handles |
| `spendStoryPoints` | Story point use (only with your setting) |
| `CABudget.charge`, `CAStores.ship` | Spending against the budget, and shipping |

The build checks that run before every release (they live outside the repo, but they're described in the [source guide](source-guide.md#building-and-checking)):
- a scan for banned classes (`java.lang.reflect`, file I/O) in the compiled jar;
- bytecode verification of every class with and without the optional mods' jars, for every combination of the optional mods;
- loading every class under the game's own script loader, which refuses what the game doesn't allow;
- a check for stray `%` in UI text (a crash in this game);
- about 35 offline test suites for the rules on this page and the scoring.

## Removing it

Don't remove it from a save that used it. The Autopilot Storage tab is a submarket on every colony, and the save won't load without it. To stop it doing anything, set every colony to Off in the empire panel, and turn off the empire-wide features in Settings (research, trade contracts, settlement, agents, war and emergency defense fleets, autonomy, megastructures, station augments). It then does nothing except keep its storage tabs.
