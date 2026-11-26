# Auditing the mod

- Every action costs what the game or the [other mod](supported-mods.md) charges, plus a convenience fee. On Standard it's 5% of a build's cost (at least 5,000) or a flat fee, 10% more in story mode, and Off makes it zero. Refunds only give back what the mod took.
- It's safe to add to a running save. A save made with it won't load without it, so use Prepare for removal before you take it out ([Removing it](#removing-it)).
- It only checks other mods to turn its integrations on, and it doesn't disable, block or change any mod for being installed.
- The source code is in the [repo](https://github.com/Mjolniar/auto-colonies).

## Colonies

- Buildings and upgrades cost the game's price and time plus the fee, one new building at a time per colony. A build that would take a colony under the stability floor doesn't start.
- Taking a building down is free. It takes down industries that lose money for 30 days, an industry over the limit, the building costing the most stability after 20 days at 0, endgame and capital swaps and structures it built. Locked and improved buildings, defenses, fleets and ship production stay.
- Items and AI cores only come from Autopilot Storage, for a small fee, and replaced or removed ones go back there. Shipping between colonies costs 2,000 plus 300 a light-year, times the fee level, and takes 2 days plus a day a light-year.
- Hiring an administrator costs its hiring cost and salary plus the fee, and they take 30 days to arrive. A move that would take a colony under the floor waits.
- Hazard pay, free port, story point improvements and Hegemony inspection orders (Resist only, with "Resist where defended") cost what the game charges, and story points are off unless you allow them.
- Moving the production gathering point to the capital is free and on by default.
- Storage logistics (off by default) and the capital's weekly gathering (on by default) ship Storage at non-hostile markets to a refit or sell hub for the shipment plus 1,000 a ship. Colony items and cores aren't shipped.
- Stable location objectives and TME's Domain-era upgrades cost materials at base price plus a fee and take 10 days (30 for a Hypershunt Receiver). A receiver uses a Coronal Hypershunt Tap and an upgrade uses a spare Beta core from Autopilot Storage.
- Kills by an Auto colony's patrols and stations without your fleet count toward the colony crisis and Tri-Tachyon commerce raiding (on by default).
- Low profile (off by default) pulls colony items and swaps AI core administrators, except Omega cores, for humans at colonies at or above the floor (stability 4 for administrators).
- IndEvo ambassadors cost a fee and 30 days, and TASC mining and siphon stations cost a fee and 60 days. The stations are Recommend only by default, and they're refunded if the site's gone or you're at the colony limit.
- An Omega core costs a small fee, and it's only made an administrator where your inspection limit allows its 10 points.

## The money ladder

- The ladder pays from a monthly share of free cash (Money), and a rung on Recommend only waits for your Approve.
- Shipyard (Recommend only by default) orders known hulls from your Doctrine priority list as custom production, at the game's price plus a 2% fee, and it stops at 6 unclaimed hulls. While free cash is under 0, its hulls come off the list and the fee is refunded.
- Stockpiles buys military goods for a colony's Local Resources from non-hostile markets, at their price plus tariff and the storage fee.
- Military upgrades stations, Patrol HQs and Military Bases for the upgrade cost and time plus the fee, one a day, and new upkeep stays within 15% of colony net income.

## Auto-expansion (Off by default)

- An Autopilot crew costs the founding goods at base price (85,000) plus 40,000 and 2,000 a light-year, plus the fee. It's refunded (fee kept) if it can't found.
- A Nexerelin colony fleet costs Nexerelin's price (165,000 at 100 fleet points, 205,000 at 150), fleet request points and the fee, and the colony is handed to you.
- For a colony past the limit, it hires the cheapest administrator on offer, or a recruiter at 250,000 plus the hiring bonus and fee.

## War and diplomacy (Nexerelin)

- It can raid, blockade, invade and bombard other factions' markets. By default the War level is Retaliate, every stance is Auto and bombardment isn't authorized. War fleets act in every colony mode, Advisor included.
- Fleet requests cost Nexerelin's price and fleet request points. Retakes, raids, blockades from Hold them down and invasions add the fee level times 10% of the price.
- Saturation bombardment of a Destroy capital (War level Scorched, authorized) costs fleet points x 400 plus fuel plus the fee, from surplus credits only, one at a time and 120 days apart. It has the same reputation penalties as one done in person, plus Nexerelin's infamy.
- It only declares war on factions with an Enemy or Destroy stance, one in 30 days, and not while you hold a commission or use Low profile.
- Stand down and Recall all cancel its own operations with Nexerelin's refund by stage, but fleet request points and the fee aren't refunded.
- Autonomy uses Nexerelin's grant and revoke with its unrest, and it's Recommend only by default. Taking it back at a conquered colony waits while the unrest would put the colony under the floor.

## What it does not do

- It doesn't change game settings, specs, conditions or other mods' data, except three Ashes of the Domain values: the hypershunt range (the setting its panel offers), the R&D council order before it pays for research, and research flags it sets for one check and then puts back.
- It doesn't create goods, credits, story points, items, cores or ships.
- It doesn't touch buildings, items or augments you locked or installed, your research queue, or agents you didn't hand over.
- It doesn't open network connections, start threads or processes, or write any file except its Diagnostics report (`saves/common/colony_autopilot_diagnostics.json.data`).
- With Console Commands installed it adds `ac_report`, `ac_why <colony>` and `ac_undo`, and all they do is read your colonies or write that same report.
- It doesn't attack Ally, Friendly or Neutral factions, allies, your commissioner, or a faction with a ceasefire. Offensives only go against hostile factions that the War level and stance allow, and agents only use sabotage, rebellion and lower relations on hostile factions.
- It doesn't start a war without the War page's Declare war confirmation, bombard without Authorize bombardment, or invade while you hold a commission.
- It doesn't raise the War level, set a stance, turn on Auto-expansion or authorize bombardment by itself.
- It doesn't found a colony past the colony limit without an administrator, or use Nexerelin autonomy to get around the limit.

## What's saved

- It saves one persistent-data key, `jt_colony_autopilot`, with plain values and ids, and other mods' data is only stored as ids.
- Story mode saves a second key, `ac_story`, with plain values. With story mode on, it adds one derelict research station holding the Autarch AI Core to an uninhabited fringe system, and the station stays if you switch story mode off.

## Removing it

- Turn on Prepare for removal in LunaLib (General tab, Removal) or on the Mod features page (More, Mod features), and a campaign dialog asks you first.
- Autopilot Storage goes to each colony's Storage, or to your cargo where there isn't any. Shipments, loads and stockpile buys on the way arrive there right away.
- Construction crews and crew expeditions on the way are refunded, but the expedition fee is kept. Nexerelin expeditions and ship orders keep going.
- Autarch AI Cores become Alpha Cores (the TriPad's goes to your cargo), and one you haven't found is taken out of its station's salvage. Story progress and bandwidth packages end.
- Autarch administrators are replaced by ones with an Alpha Core administrator's skills and no Autarchy. If a colony would fall to 0 stability from that, nothing changes, and the dialog names that colony.
- It takes out its data, intel, scripts and listeners, the Autopilot Storage tab, the retired condition, enforcer marks on fleets and market memory keys. Then the save is flagged (`$ac_removalDone`) and Auto Colonies does nothing in it, so just save, quit and remove the mod.
- To undo it, turn the setting off. In the same session, the save's data, replaced cores and administrators come back. After a load, it starts fresh with every colony Off, and without LunaLib you'll need to load an earlier save.

To stop it without removing it, set every colony, War, New colonies, Extras, every money ladder rung, agents, autonomy, emergency defense fleets and Storage logistics to Off, Capital to None and every faction stance to Auto. It still accepts ceasefire and peace offers from Ally, Friendly and Neutral factions.
