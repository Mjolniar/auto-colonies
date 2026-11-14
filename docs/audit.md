# Auditing the mod

- Scope: everything the mod can change in your game, what each change costs, what it does not do, and how to check it in the code.
- Code structure: [source guide](source-guide.md). All classes are in `src/data/scripts/jonahtweaks/autopilot/`.

## The short version

- Cost: every action charges what that UI charges, plus a convenience fee that can be set to Off.
- Game data: no game setting, ship, weapon, faction, market condition or other mod's numbers are changed, except three Ashes of the Domain values (see [What it does not do](#what-it-does-not-do)).
- War (Nexerelin): 0.3.0 can raid, blockade, invade and bombard other factions' markets. Defaults: War level Retaliate (retake is off in 0.2.45 saves), every stance Auto, bombardment not authorized.
- War (Nexerelin): War fleets act in every colony mode, Advisor included. War on a non-hostile faction starts only from the War page's Declare war confirmation.
- Adding the mod: safe in a running save. Removing it: a save made with Auto Colonies enabled does not load without it.
- Other mods: checked only to turn integrations on. No mod is disabled, blocked or changed for being installed.

## Everything it can change in your game

- Fee: Standard is 5% of a build's cost, at least 5,000, or a flat fee. Off makes it zero. Refunds return only what the mod took.
- Credits: every credit taken or given back goes through `CABudget` (`take`, `give`, called by `pay`, `charge`, `payPrice`, `payFromPool`, `refund`).

### Colonies and buildings

| What | Where in the code | What it costs |
|---|---|---|
| Starts a building or upgrade | `CABuild.start` (`addIndustry` + `startBuilding`, or `startUpgrading`), ranked by `CAPlanner` | Game build cost and time plus the fee. One new building at a time per colony. A build that would take the colony under the stability floor does not start (`CAStability`). |
| Ashes of the Domain upgrade paths | `CAAotD.startUpgrade` | As above. The upgrade pointer is restored in `finally`. |
| Takes a building down | `CAPrune`, `CAOverLimit`, `CAEndgame.takeDown`, `CACapital`, `CATerraformSurplus`, `CASurplusResearch` | Free. Targets: industries unprofitable for 30 days; an industry over the limit; the building costing the most stability after 20 days at 0; endgame and capital swaps; structures it built. Locked and improved buildings, defenses, fleets and ship production stay. The AI core and item go to Autopilot Storage. Each removal leaves a one-year mark (`CATombstone`). |
| Installs or swaps a colony item, installs or moves an AI core | `CAItems` (`placeItem`, `swapItem`, `placeCore`) | A small fee. Only from Autopilot Storage at the colony it is stored at, else shipped first. Replaced items and cores go back to storage. A polluting nanoforge does not go on a habitable world. |
| Moves the production gathering point to the capital (on by default) | `CACapital` | Free. Moving it to another colony yourself makes that colony the capital. |
| Ships Storage to a refit or sell hub (Storage logistics, off by default; capital's weekly gathering, on by default) | `CALogistics` | Shipment cost plus 1,000 a ship, from the monthly budget. Only Storage at non-hostile markets. Colony items and cores are not shipped. |
| Comm relay, nav buoy, sensor array, Hypershunt Receiver at a stable location; Domain-era upgrade (TME) | `CAObjectives` | Materials at base price plus a fee; 10 days (30 for a receiver). The upgrade takes a spare Beta core, a receiver a Coronal Hypershunt Tap, from Autopilot Storage; both come back if the spot is gone. |
| Panopticon Instance or Core into a Surveillance Center (IX Revival) | `CAPanopticon` | The install fee. Shipped first. |
| Omega core (Omega Core Everywhere / Astral Ascension Renewed) | `CAOmega` | A small fee, from Autopilot Storage. As an administrator only where your inspection limit allows its 10 points. Not moved once placed. |
| Ships between colonies | Hires and places administrators | `CAItems` (`hireAdmin`, `assignAdmin`, `moveAdmin`) | Hiring cost and salary plus the fee; 30 days to arrive. A move that would take a colony under the floor waits. |
| Hazard pay, free port, story point improvements, Hegemony inspection orders (Resist only, with "Resist where defended") | `CAPolicies`, `CAStory`, `CACrisis` | The game's charges. Story points off unless allowed. With the Growth rung On, hazard pay can rise to the money tier's share of colony net income. |
| Low profile (off by default): pulls a colony item, swaps an AI core administrator for a human | `CALowProfile` (`pullItem`, `swapAdmin`) | Free. Only at colonies at or above the floor (stability 4 for administrators). Omega core administrators are not touched. |
| Research (Vaults of Knowledge) | `CAResearch` | VoK's cost (`payForResearch`). Needed items move into the Research Facility tab. While valuing techs it marks candidates researched for one scoring pass and restores them in `finally`. |
| Megastructures, wonders, Bifrost gates (AotD) | `CAHypershunt`, `CAMegastructures`, `CAWonders` | AotD's contracts, paid as AotD charges. Wonders need the Wonders rung On and money tier Rich. |
| RAT Frontiers settlement | `CASettlement` | RAT's costs. From afar an expedition (10 days plus a day a light-year), refunded if it cannot found. |
| TASC mining and siphon stations | `CATascStations` | TASC's costs plus a fee, 60 days. Refunded if the site is gone or you are at the colony limit. Recommend only by default. |
| Station augments | `CAAugments` | Station Augments' install and removal (free there). A refit crew applies it after 7 days, at once if your fleet is in the system. |
| Industrial Evolution, TME and TASC terraforming, ToT trade contracts, Antimatter Stabilizer conversion | `CAIndEvo`, `CACourier`, `CATerraform`, `CATerraformSurplus`, `CATasc`, `CAContracts`, `CAItems.convertFuel` | Each mod's own costs, started as its UI does. IndEvo ambassadors cost a fee and 30 days (none for Rival, Enemy, Destroy). ToT: one new contract per commodity a month. Stabilizer conversion needs Allow removals on. |

### The money ladder (0.3.0)

| What | Where in the code | What it costs |
|---|---|---|
| Orders ships through custom production (Shipyard rung; Recommend only by default) | `CAShipyard` (`FactionProductionAPI.addItem`) | The game's production price at month end plus a 2% fee at order. Only known hulls on your Doctrine priority list. Stops at 6 unclaimed hulls. While free cash is under 0, its hulls come off the list (`removeItem`) and the fee is refunded. |
| Buys goods for a colony's Local Resources (Stockpiles rung; military goods) | `CAStockpile` | The selling market's price plus tariff, shipped for the storage fee and travel time. Only from non-hostile markets. |
| Upgrades a station, Patrol HQ or Military Base (Military rung) | `CAMilitary`, through `CABuild.start` | Upgrade cost and time plus the fee. One a day. New upkeep stays within 15% of colony net income. |

- Payment: from the ladder's monthly pool, a share of free cash (credits less the reserve, production still to pay and 2 months of upkeep and fleet costs).
- Recommend only: the rung proposes and waits for your Approve.

### Auto-expansion (0.3.0, Off by default)

| What | Where in the code | What it costs |
|---|---|---|
| Founds a colony with an Autopilot crew | `CAExpansion.found` (the game's founding steps, `createMarket`, `addMarket`) | Founding goods at base price (85,000) plus 40,000 and 2,000 a light-year, plus the fee, taken when the crew leaves. 15 days plus travel. Refunded (fee kept) if it cannot found. |
| Sends a Nexerelin colony fleet | `CAExpansionNex` (`ColonyExpeditionIntel`) | Nexerelin's price (165,000 at 100 fleet points, 205,000 at 150), fleet request points and the fee, paid after `init()` leaves the expedition running. |
| Hands a Nexerelin-founded colony to you | `CAExpansionNex.fixAdmin` (`ColonyManager.reassignAdminIfNeeded`) | Free. The colony then counts like one you founded. |
| Hires an administrator for a colony past the limit | `CAItems` | The cheapest on offer, or a recruiter at 250,000 plus the hiring bonus and fee. |

### War and diplomacy (0.3.0, Nexerelin)

| What | Where in the code | What it costs |
|---|---|---|
| Fleet requests: relief, defense, base strikes, raids, blockades, invasions, retakes | `CAWar`, `CAConquest` (`InvasionIntel`, `NexRaidIntel`, `BlockadeWrapperIntel`, `BaseStrikeIntel`, `ReliefFleetIntelAlt`) | Nexerelin's price and fleet request points. Retakes, raids, blockades from Hold them down and invasions add the fee level times 10% of the price. 40% of points kept for emergencies (10% while rich). Offensive operations only against hostile factions allowed by War level and stance. |
| Saturation bombardment of a Destroy capital (War level Scorched, authorized) | `CAScorched` (`SatBombIntel`) | Fleet points x 400 plus fuel plus the fee, from surplus credits only. One at a time, 120 days apart. |
| Reputation penalties after a bombardment | `CAScorched.applyAtrocity` | A bombardment in person's penalties: victim -0.04 x size (at best Vengeful); every faction that cares about atrocities -0.02 x size (at best Hostile); permanent relation cap. Nexerelin adds infamy. |
| Answers ceasefire and peace offers | `CADiplomacy` (`CeasefirePromptIntel.accept`, `reject`) | Free. Accepted from Ally, Friendly and Neutral factions. Rejected from Enemy and Destroy factions at War level Retaliate and up. |
| Declares war | `CADiplomacy.declareWar`, only from the War page confirmation | Relations set to Hostile. Enemy and Destroy stances only, one in 30 days. Blocked while you hold a commission or use Low profile. |
| Takes back autonomy at a conquered colony | `CAConquest` (`Nex_GrantAutonomy.revokeAutonomy`) | Nexerelin's revoke with its unrest. Waits while unrest would put a colony under the floor. |
| Stand down, Recall all | `CAWar`, `CAWarPage` | Cancels its own operations. Nexerelin refunds by stage (100% organizing, 67% travelling, 33% in action). Fleet request points and the fee are not refunded. |
| Nexerelin agents | `CASpies` | Recruiting 12,000 plus the fee, 60 days. Operations at Nexerelin's cost. Sabotage, rebellion and lower relations only against hostile factions. |
| Nexerelin autonomy | `CAAutonomy` | Nexerelin's grant and revoke with its unrest. Recommend only by default. Auto is money-only and does not apply to new colonies. |

## What it does not do

- Game data: change `Global.getSettings()` values, specs, conditions or other mods' data. Exceptions, Ashes of the Domain only: the hypershunt range (the setting its panel offers), research flags marked and put back within one check (`CAResearch.valueBuildings`), and the R&D council order before paying for research.
- Creation: create goods, credits, story points, items, cores or ships.
- Your choices: touch buildings, items or augments you locked or installed, your research queue, or agents you did not hand over.
- Files and network: open network connections, start threads or processes, or write any file except its Diagnostics report (`saves/common/colony_autopilot_diagnostics.json.data`).
- Attacks: attack Ally, Friendly or Neutral factions, allies, your commissioner, or a faction with a ceasefire.
- War: start a war without the War page confirmation, bombard without Authorize bombardment, or invade while you hold a commission.
- Settings: raise the War level, set a stance, turn on Auto-expansion or authorize bombardment by itself.
- Colony limit: found a colony past it without an administrator, or use Nexerelin autonomy to get around it.

## The test colony

- `CASimulator`: measures a mod building on a throwaway market outside the economy and sector (`Global.getFactory().createMarket`), then removes it in `finally`. Nothing is saved.

## What's saved

- One persistent-data key, `jt_colony_autopilot`, holding plain values and ids. Other mods' data is stored as ids only. Full list: [source guide](source-guide.md#whats-saved).
- Story mode: a second key, `ac_story`, holding plain values (`CASync`). With story mode on, one derelict research station holding the Autarch AI Core is added to an uninhabited fringe system (`CAAutarch`). It stays in the sector if story mode is switched off.

## How to check it yourself

Searches across `src/`:

| Search for | What you'll find |
|---|---|
| `getCredits().subtract`, `getCredits().add` | Only `CABudget.take` and `CABudget.give` |
| `addIndustry`, `removeIndustry`, `startUpgrading` | Every building change |
| `InvasionIntel`, `SatBombIntel`, `NexRaidIntel`, `BlockadeWrapperIntel` | Every offensive fleet request |
| `java.io`, `java.net`, `java.lang.reflect` | `java.io` once, in `CALinkScan.read`: an `InputStream` from `getResourceAsStream` reading the mod's own class files for the link check; nothing is written. `java.lang.reflect` only as class name strings in `CARefl` (`Class.forName`) and in a comment in `CALuna`. No `java.net`. |
| `CAStores.ship` | Shipping between colonies |

- Build checks (before every release; outside the repo; see the [source guide](source-guide.md#building-and-checking)): banned-class scan, bytecode verification for every mod combination, script-loader load, `%` check, and 86 offline test suites (including a scan that fails any file outside `CABudget` that changes credits).

## Removing it

- Saves: do not remove it from a save made with it enabled. The save needs its saved data and the Autopilot Storage tab.
- Stopping it:

1. Set every colony to Off, and War, New colonies, Extras and every money ladder rung to Off.
2. Set Capital to None (Settings, Items and people). A capital gets your production gathering point and weekly Storage gathering, also with Storage logistics Off.
3. Set every faction stance to Auto (Factions page). At any War level it accepts ceasefire and peace offers from Ally, Friendly and Neutral factions.
4. Turn off agents, autonomy, emergency defense fleets and Storage logistics.

- After the steps: storage tabs stay, shipments on the way still arrive, and nothing new starts.
