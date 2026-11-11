# Auditing the mod

This page lists everything the mod can change in your game, what each change costs, what it does not do, and how to check that in the code. File links go to the source on GitHub. For how the code fits together, see the [source guide](source-guide.md).

## The short version

- It manages **your own colonies** through the calls the game's UI, or the UI of the mod a feature comes from, uses. Every action charges what that UI charges, plus a convenience fee you can set to Off.
- It does not change any game setting, ship, weapon, faction, market condition or other mod's numbers. No balance changes, no cheats (the old storage cheat and the Derelict Empire attack settings were removed in 0.2.37.0; cheats are in Jonah's Easy Sector).
- No file access beyond reading its own default settings and its own class files through the game's loader, no network access, no `java.lang.reflect` (the game's script loader refuses it; other mods are reached through method handles).
- Nothing moves across the sector for free. Items, cores, databanks and goods that go from one place to another are shipped for a fee and travel time.
- With Nexerelin, 0.3.0 can raid, blockade, invade and bombard other factions' markets. Defaults: War level Retaliate (strikes back at attackers and retakes your lost colonies; 0.2.45 saves keep retake off), every faction stance Auto, bombardment not authorized. War fleets act whatever the colony modes are, Advisor included. War on a faction that is not hostile starts only from the War page's **Declare war** confirmation.
- Auto-expansion (founding colonies) is Off for every save until you turn it on.
- It's safe to add to a running save. It's not safe to remove from a save that used it, because the Autopilot Storage tab is a market submarket.

## Everything it can change in your game

"Fee" means the convenience fee (Standard: 5% of a build's cost, at least 5,000, or a flat fee for small actions; Off makes it zero). Every credit taken or given back goes through [CABudget](../src/data/scripts/jonahtweaks/autopilot/CABudget.java) (`take`, `give`, called by `pay`, `charge`, `payPrice`, `payFromPool` and `refund`).

### Colonies and buildings

| What | Where in the code | What it costs |
|---|---|---|
| Starts a building or an upgrade | [CABuild.start](../src/data/scripts/jonahtweaks/autopilot/CABuild.java) (`addIndustry` + `startBuilding`, or `startUpgrading`), ranked by [CAPlanner](../src/data/scripts/jonahtweaks/autopilot/CAPlanner.java) | The game's build cost and build time, plus the fee. One new building at a time per colony, like the game. A build that costs stability does not start if it takes the colony under the floor ([CAStability](../src/data/scripts/jonahtweaks/autopilot/CAStability.java)). |
| Ashes of the Domain upgrade paths | [CAAotD.startUpgrade](../src/data/scripts/jonahtweaks/autopilot/CAAotD.java) | Same as above. It points the building's upgrade at the AotD child for the one call and restores it in `finally`, the way BMO's menu does it. |
| Takes a building down | [CAPrune](../src/data/scripts/jonahtweaks/autopilot/CAPrune.java), [CAOverLimit](../src/data/scripts/jonahtweaks/autopilot/CAOverLimit.java), [CAEndgame.takeDown](../src/data/scripts/jonahtweaks/autopilot/CAEndgame.java), [CACapital](../src/data/scripts/jonahtweaks/autopilot/CACapital.java), [CATerraformSurplus](../src/data/scripts/jonahtweaks/autopilot/CATerraformSurplus.java), [CASurplusResearch](../src/data/scripts/jonahtweaks/autopilot/CASurplusResearch.java) | Free, like the game. Industries that stopped paying for 30 days; an industry over the industry limit; the building costing the most stability after 20 days at 0; an endgame swap; the capital's make-room swap (from the money tier Idle money); structures and research facilities it built itself. Locked and improved buildings, defenses, fleets and ship production stay. Its AI core and item go to that colony's Autopilot Storage first. Each removal leaves a one-year mark ([CATombstone](../src/data/scripts/jonahtweaks/autopilot/CATombstone.java)) so the building is not rebuilt there within the year. |
| Installs or swaps a colony item, installs or moves an AI core | [CAItems](../src/data/scripts/jonahtweaks/autopilot/CAItems.java) (`placeItem`, `swapItem`, `placeCore`) | A small fee. Only from Autopilot Storage, and only at the colony it's stored at; anywhere else it's shipped first. Replaced items and cores go back to storage. A polluting nanoforge does not go on a habitable world. |
| Moves your production gathering point to the capital (on by default) | [CACapital](../src/data/scripts/jonahtweaks/autopilot/CACapital.java) | Free, like changing it yourself in the custom production screen. If you move it to another colony yourself, that colony becomes the capital. |
| Ships your Storage to a refit or sell hub (Storage logistics, off unless you switch it on; the capital's weekly gathering, on by default) | [CALogistics](../src/data/scripts/jonahtweaks/autopilot/CALogistics.java) | What an Autopilot Storage shipment costs plus 1,000 a ship, from the monthly budget, and the same travel time. Only Storage at markets that aren't hostile to you; not colony items, AI cores or Autopilot Storage. |
| Builds a comm relay, nav buoy, sensor array or Hypershunt Receiver at a stable location, or upgrades one to Domain-era (TME) | [CAObjectives](../src/data/scripts/jonahtweaks/autopilot/CAObjectives.java) | The materials at base price plus a fee, and 10 days for the crew (30 for a receiver). A Domain-era upgrade takes a spare Beta core and a receiver a Coronal Hypershunt Tap from Autopilot Storage. Both come back if the spot's gone. |
| Puts a Panopticon Instance or Core in a Surveillance Center (IX Revival) | [CAPanopticon](../src/data/scripts/jonahtweaks/autopilot/CAPanopticon.java) | The standard install fee; shipped there first from another colony. |
| Places an Omega core (Omega Core Everywhere / Astral Ascension Renewed) | [CAOmega](../src/data/scripts/jonahtweaks/autopilot/CAOmega.java) | A small fee, from Autopilot Storage like other cores. In a building with `setAICoreId`; as an administrator through Astral Ascension Renewed's own plugin, only where your inspection limit allows its 10 points. Not moved or removed once placed. |
| Ships something between colonies | [CAStores.ship](../src/data/scripts/jonahtweaks/autopilot/CAStores.java) | 2,000 plus 300 a light-year (times the fee level), and 2 days plus a day a light-year. A shipment on a route with a load sent in the last 7 days joins that load and pays no new fee. |
| Hires and places administrators | [CAItems](../src/data/scripts/jonahtweaks/autopilot/CAItems.java) (`hireAdmin`, `assignAdmin`, `moveAdmin`) | The game's hiring cost and salary, plus the fee. Hired from another market's comm directory, so they take 30 days to arrive. A move that would take either colony under the stability floor waits. |
| Hazard pay, free port | [CAPolicies](../src/data/scripts/jonahtweaks/autopilot/CAPolicies.java) | What the game charges for them. The same toggles as the colony screen. With the money ladder's Growth rung On, the hazard pay budget can rise to the money tier's share of colony net income, up to the rung's room for the month. |
| Story point improvements | [CAStory](../src/data/scripts/jonahtweaks/autopilot/CAStory.java) | The game's story point cost. Off unless you allow it. |
| Low profile (off unless you pick it): takes a colony item back out, swaps an AI core administrator for a human one | [CALowProfile](../src/data/scripts/jonahtweaks/autopilot/CALowProfile.java) (`pullItem`, `swapAdmin`) | Free, like doing it yourself on the colony screen. Items only at colonies at or above the stability floor, and put back if pulling one cost stability the colony could not spare. An AI core administrator is swapped only at 4 stability or more; an Omega core administrator is not touched. Both go to that colony's Autopilot Storage. |
| Hegemony inspection orders | [CACrisis](../src/data/scripts/jonahtweaks/autopilot/CACrisis.java) | Only sets Resist, and only with "Resist where defended" (the AI cores preset Defended). The default only advises. |
| Terraforming projects (TME, TASC) | [CATerraform](../src/data/scripts/jonahtweaks/autopilot/CATerraform.java), [CATerraformSurplus](../src/data/scripts/jonahtweaks/autopilot/CATerraformSurplus.java), [CATasc](../src/data/scripts/jonahtweaks/autopilot/CATasc.java) | The mod's own project cost, started the way its dialog does. |
| Research (Vaults of Knowledge) | [CAResearch](../src/data/scripts/jonahtweaks/autopilot/CAResearch.java) | VoK's own cost (`payForResearch`). Items it needs are moved into the Research Facility tab at that colony, or shipped there first. While valuing techs it marks candidates researched for one scoring pass and puts them back in `finally`; nothing measured or logged during that pass is kept. |
| Trade contracts (Theory of Toolbox) | [CAContracts](../src/data/scripts/jonahtweaks/autopilot/CAContracts.java) | Accepted the way ToT's UI does; ToT handles payment. One new contract per commodity a month. |
| Megastructures, wonders, Bifrost gates (AotD) | [CAHypershunt](../src/data/scripts/jonahtweaks/autopilot/CAHypershunt.java), [CAMegastructures](../src/data/scripts/jonahtweaks/autopilot/CAMegastructures.java), [CAWonders](../src/data/scripts/jonahtweaks/autopilot/CAWonders.java) | AotD's own settings and restoration or construction contracts, paid the way AotD charges them (goods from your exports). Wonders need the money ladder's Wonders rung On and the money tier Rich. |
| RAT Frontiers settlement | [CASettlement](../src/data/scripts/jonahtweaks/autopilot/CASettlement.java) | RAT's founding and facility costs, built the way RAT's dialog builds it. Founding from afar sends an expedition that takes 10 days plus a day a light-year; refunded if it can't found it. |
| TASC mining and siphon stations | [CATascStations](../src/data/scripts/jonahtweaks/autopilot/CATascStations.java) | TASC's costs plus a fee and 60 days for the crew; refunded if the site is gone or you're at your colony limit. Recommend only by default. |
| Station augments | [CAAugments](../src/data/scripts/jonahtweaks/autopilot/CAAugments.java) | Station Augments' own install and removal (free there). From afar, a refit crew makes the change 7 days later; at once if your fleet is in the system. |
| Industrial Evolution | [CAIndEvo](../src/data/scripts/jonahtweaks/autopilot/CAIndEvo.java), [CACourier](../src/data/scripts/jonahtweaks/autopilot/CACourier.java) | Ambassadors (fee, 30 days to arrive; priority by faction stance: Rival, Enemy and Destroy get none), edicts (not the harsh ones), VPC chips (fee, shipped if needed), specialties, and Courier Port contracts at IndEvo's shipping price. |
| Antimatter Stabilizer (Emergent Threats) | [CAItems.convertFuel](../src/data/scripts/jonahtweaks/autopilot/CAItems.java) | The stabilizer and a fee, the way ET's portmaster does it. Only with Allow removals on. |

### The money ladder (0.3.0)

| What | Where in the code | What it costs |
|---|---|---|
| Orders ships through custom production (Shipyard rung; Recommend only by default) | [CAShipyard](../src/data/scripts/jonahtweaks/autopilot/CAShipyard.java) (`FactionProductionAPI.addItem`) | The game's production price, charged by the game at month end, plus a 2% fee when the order is placed. Only hulls on your Doctrine priority list that you know. Stops at 6 unclaimed hulls. While free cash is under 0, Colony Manager's own hulls come off the list again (`removeItem`) and the fee is refunded. |
| Buys goods for a colony's Local Resources (Stockpiles rung; military goods on the Military rung) | [CAStockpile](../src/data/scripts/jonahtweaks/autopilot/CAStockpile.java) | The selling market's price plus its tariff; the goods leave that market's open market. Shipped for the Autopilot Storage fee and travel time. Only from markets not hostile to you. |
| Upgrades a station, Patrol HQ or Military Base (Military rung) | [CAMilitary](../src/data/scripts/jonahtweaks/autopilot/CAMilitary.java), through `CABuild.start` | The game's upgrade cost and time plus the fee. Upgrades only, one a day. The rung's new upkeep stays within 15% of colony net income. |

All three are paid from the ladder's monthly pool, a share of free cash (credits less the reserve, production still to pay and 2 months of upkeep and fleet costs). On Recommend only they propose and wait for your Approve.

### Auto-expansion (0.3.0, Off by default)

| What | Where in the code | What it costs |
|---|---|---|
| Founds a colony with an Autopilot crew | [CAExpansion](../src/data/scripts/jonahtweaks/autopilot/CAExpansion.java) (`found`: the game's founding steps, `createMarket`, `addMarket`) | The founding goods at base price (85,000) plus 40,000 and 2,000 a light-year, plus the fee, taken when the crew leaves. 15 days plus travel. Refunded (fee kept) if it can't found the colony. |
| Sends a Nexerelin colony fleet | [CAExpansionNex](../src/data/scripts/jonahtweaks/autopilot/CAExpansionNex.java) (`ColonyExpeditionIntel`, as Nexerelin's fleet request dialog) | Nexerelin's price (165,000 at 100 fleet points, 205,000 at 150) and fleet request points, plus the fee. Paid only after Nexerelin's `init()` leaves the expedition running. Nexerelin refunds a cancelled expedition by stage. |
| Hands a Nexerelin-founded colony to you | `CAExpansionNex.fixAdmin` (`ColonyManager.reassignAdminIfNeeded`) | Free. Nexerelin puts a random person in charge, so the colony would not count toward your colony limit; this makes it count like one you founded. |
| Hires an administrator for the new colony past the colony limit | [CAItems](../src/data/scripts/jonahtweaks/autopilot/CAItems.java) | The cheapest on offer, or a recruiter at 250,000 plus the hiring bonus and fee. |

### War and diplomacy (0.3.0, Nexerelin)

| What | Where in the code | What it costs |
|---|---|---|
| Fleet requests: relief, defense, base strikes, raids, blockades, invasions, retakes | [CAWar](../src/data/scripts/jonahtweaks/autopilot/CAWar.java), [CAConquest](../src/data/scripts/jonahtweaks/autopilot/CAConquest.java) (Nexerelin's `InvasionIntel`, `NexRaidIntel`, `BlockadeWrapperIntel`, `BaseStrikeIntel`, `ReliefFleetIntelAlt`) | Nexerelin's own price and fleet request points, launched the way its fleet request dialog does. Retakes, raids and blockades from Hold them down and invasions add the fee level times 10% of Nexerelin's price. 40% of fleet request points kept for emergencies (10% while rich). Offensive operations only against factions hostile to you and allowed by the War level and the faction's stance. |
| Saturation bombardment of a Destroy capital (War level Scorched, after you authorize it) | [CAScorched](../src/data/scripts/jonahtweaks/autopilot/CAScorched.java) (a player-faction `SatBombIntel`) | Fleet points x 400 plus the fuel at base price plus the fee, from surplus credits only. One at a time, 120 days apart. |
| Reputation penalties after Colony Manager's bombardment lands | `CAScorched.applyAtrocity`, called once per bombardment from [CAWarListener](../src/data/scripts/jonahtweaks/autopilot/CAWarListener.java) or a daily scan | The penalties a bombardment in person has, which Nexerelin's fleet path leaves out: the victim -0.04 x size (at best Vengeful); every faction that cares about atrocities -0.02 x size (at best Hostile); the permanent relation cap; the atrocity counts. Nexerelin adds the infamy. |
| Answers ceasefire and peace offers | [CADiplomacy](../src/data/scripts/jonahtweaks/autopilot/CADiplomacy.java) (`CeasefirePromptIntel.accept`, `reject`) | Free, as clicking it in the intel. Accepted from Ally, Friendly and Neutral factions; rejected from Enemy and Destroy factions at War level Retaliate and up; the rest wait for you. |
| Declares war | `CADiplomacy.declareWar`, only from the War page's confirmation | Relations set to Hostile. Enemy and Destroy stances only, one in 30 days. Blocked while you hold a commission or use Low profile. An Enemy or Destroy faction you declared war on is made hostile again at most once in 30 days. |
| Takes back Nexerelin autonomy at a colony its invasion conquered | `CAConquest` (`Nex_GrantAutonomy.revokeAutonomy`) | Nexerelin's revoke, including its unrest. Waits while the unrest or the colony-limit penalty would put a colony under the stability floor. |
| Stand down, Recall all | [CAWar](../src/data/scripts/jonahtweaks/autopilot/CAWar.java), [CAWarPage](../src/data/scripts/jonahtweaks/autopilot/CAWarPage.java) | Cancels Colony Manager's own operations; Nexerelin refunds by stage (100% organizing, 67% travelling, 33% in action). Fleet request points and the fee are not refunded. |
| Nexerelin agents | [CASpies](../src/data/scripts/jonahtweaks/autopilot/CASpies.java) | Recruiting: 12,000 plus the fee, 60 days before the agent reports. Operations: Nexerelin's own cost, through its agent API. Raise relations by stance; sabotage, rebellion and lower relations only against factions hostile to you, by stance and War level. |
| Nexerelin autonomy | [CAAutonomy](../src/data/scripts/jonahtweaks/autopilot/CAAutonomy.java) | Nexerelin's own grant and revoke, including its unrest. Recommend only by default; Auto is money-only and not for new colonies. |

### Credits given back

Credits are added back only as refunds of what Colony Manager took: a RAT settlement expedition, a stable-location crew or a TASC station crew whose site is gone, an Auto-expansion crew that can't found its colony (the fee is kept), and the Shipyard's fee for hulls it takes off the production list. Nexerelin's stage refunds for cancelled fleets are paid by Nexerelin.

## What it does not do

- Change `Global.getSettings()` values, specs, conditions or other mods' data. (The AotD upgrade pointer above is put back in the same call.)
- Create goods, credits, story points, items, cores or ships.
- Move anything between colonies without shipping it.
- Use information the game doesn't show you.
- Touch buildings, items or augments you locked or installed yourself, your research queue, or agents you didn't hand over.
- Write files, read files other than its own default settings and class files (both through the game's loader), or open network connections.
- Attack Ally, Friendly or Neutral factions, your allies, your commissioner, or a faction with a ceasefire.
- Start a war without the War page confirmation, or bombard without **Authorize bombardment**.
- Invade while you hold a commission.
- Turn on Nexerelin's follower invasions or the player strategic AI, create ceasefires, peace treaties or alliances, refund fleet request points, or remove infamy.
- Raise the War level, set a stance, turn on Auto-expansion or authorize bombardment by itself. A save from 0.2.45 keeps its War fleets behavior as the matching War level, with retake off.
- Use Nexerelin autonomy to get around the colony limit.
- Found a colony past the colony limit without an administrator for it.

## The test colony

To value mod buildings it has no formula for, [CASimulator](../src/data/scripts/jonahtweaks/autopilot/CASimulator.java) creates a throwaway market (`Global.getFactory().createMarket`) that's not added to the economy or the sector, copies the colony's vanilla conditions onto it, adds the building, reads the effects and removes the building in `finally`. Any sector script or intel the building registers while it's there is removed and the building is marked as not measurable. Nothing about it is saved.

## What's saved

Everything lives under one persistent-data key, `jt_colony_autopilot`, as plain values and ids: settings, per-colony state, the log, each colony's Autopilot Storage cargo and shipments, and the 0.3.0 records (removal marks, ship orders, stockpile shipments, expedition orders, war operations). Saved classes use short XStream names (`JCM-Data`, `JCM-Settings` and so on, [CAXStream](../src/data/scripts/jonahtweaks/autopilot/CAXStream.java)). Nothing from another mod is stored, only ids, so removing an optional mod doesn't break a save. The full list is in the [source guide](source-guide.md#whats-saved).

## How to check it yourself

The quickest audit is a handful of searches across `src/`:

| Search for | What you'll find |
|---|---|
| `getCredits().subtract`, `getCredits().add` | Only `CABudget.take` and `CABudget.give`. Every payment and refund goes through them. |
| `CABudget.pay`, `charge`, `payPrice`, `payFromPool`, `refund` | Every place it spends your credits, and every refund |
| `addIndustry`, `removeIndustry`, `startUpgrading` | Every building change |
| `setSpecialItem`, `setAICoreId` | Every item and core move |
| `getProduction().addItem`, `removeItem` | Shipyard orders |
| `InvasionIntel`, `SatBombIntel`, `NexRaidIntel`, `BlockadeWrapperIntel` | Every offensive fleet request |
| `CeasefirePromptIntel`, `declareWar`, `makeHostile` | Every diplomacy action |
| `CAStability.canLose` | The stability check every takedown, move, pull and new action asks first |
| `Global.getSettings().set` | Nothing (no game setting is changed) |
| `java.io`, `java.net`, `java.lang.reflect` | `java.io` once, in [CALinkScan](../src/data/scripts/jonahtweaks/autopilot/CALinkScan.java) `read`: an `InputStream` from `getResourceAsStream` that reads Colony Manager's own class files through the class loader for the load-time link check. It writes nothing. `java.lang.reflect` only as class name strings in [CARefl](../src/data/scripts/jonahtweaks/autopilot/CARefl.java) (`Class.forName`, for the method-handle bridge) and in a comment in `CALuna`. No `java.net`. |
| `CARefl` | Every reflective call into another mod, all through method handles |
| `spendStoryPoints` | Story point use (only with your setting) |
| `CAStores.ship` | Shipping between colonies |

The build checks that run before every release (they live outside the repo, described in the [source guide](source-guide.md#building-and-checking)):
- a scan for banned classes (`java.lang.reflect`, file I/O classes outside the game loader's short allowed list) in the compiled jar;
- bytecode verification of every class with and without the optional mods' jars, for every combination of the optional mods;
- loading every class under the game's own script loader, which refuses what the game doesn't allow;
- a check for stray `%` in UI text (a crash in this game);
- 75 offline test suites for the rules on this page and the scoring. Among them: one that fails any file outside `CABudget` that changes credits, a 2-year war simulation (infamy under 75, credits above the reserve, one invasion at a time) and a save-compatibility check against a 0.2.45 save.

## Removing it

Don't remove it from a save that used it. The Autopilot Storage tab is a submarket on every colony, and the save won't load without it. To stop it doing anything:

- Set every colony to Off in the empire panel.
- Set War, New colonies and Extras to Off.
- Set every money ladder rung to Off (Settings, Advanced, Surplus and money ladder).
- Set Capital to None (Settings, Items and people). With a capital, Colony Manager moves your production gathering point there and gathers Storage there weekly, for the shipping fee plus 1,000 a ship, also with Storage logistics Off.
- Set every faction stance to Auto (Factions page). At any War level, Off included, Colony Manager accepts ceasefire and peace offers from Ally, Friendly and Neutral factions.
- Turn off agents, autonomy, emergency defense fleets and Storage logistics.

After that it keeps its storage tabs, and shipments already on the way still arrive. It starts nothing new.
