# Patch notes

Every version, newest first. The mod started as a personal mod of small tweaks (battle size, combat autopilot). Colony Autopilot grew inside it. 0.2.1.0 is the first version on GitHub and 0.2.6.0 the first with a downloadable release. The balance tweaks moved to their own mod in 0.2.7.0.

The mod was called Colony Manager until 0.3.0.1. Older entries use the new name, Auto Colonies.

Version numbers start with 0 since 0.3.0.0, because the mod is not at 1.0. Earlier versions are listed with the 0 added: 2.45.2 is 0.2.45.2. Release tags keep their old names (v2.45.2).

## 0.3.0.2 - 2026-11-12

- Roider Dives and Union HQ: Roider Union 2.3.1 mines only the colony's own deposits after a save load. It rebuilds the list of star systems in range before the game has loaded the sector, so the list is empty until the building is built or upgraded again. Auto Colonies now values a Dives or Union HQ for the colony's own deposits, which is what Roider Union mines. Before, it also counted the deposits in range, and rated a Dives too high.
- The build note for a Dives lists each deposit its miners reach and where it is (this planet, or a star system and its distance).
- Mod watch: a Dives that mines only its colony's own deposits is reported as information with the cause, no longer as a formula warning.
- Starsector 0.98.5a: hiring an administrator uses the game's portrait pick in either game version. Its parameters changed in 0.98.5a, and the old call would fail there.

## 0.3.0.1 - 2026-11-12

- Renamed: Colony Manager is now Auto Colonies. The mod folder is now `Auto Colonies`. Delete the old Colony Manager folder in `mods/` before extracting, or the game finds the mod twice.
- The mod id is unchanged, so saves keep working.
- The GitHub repository is now github.com/Mjolniar/auto-colonies. Old links redirect.
- The release zip is now AutoColonies.zip, and the Version Checker file is AutoColonies.version.
- In-game text and logs say Auto Colonies. "CM spent" in the month lines is now "Auto Colonies spent".
- The supported mods list and links use Auto Outfitting's new name.

## 0.3.0.0 - 2026-11-11

Major version. A save made on 0.3.0 does not load in 0.2.45.x. Back up your save before updating.

Version Checker and TriOS compare version numbers, so they read 0.3.0.0 as lower than 2.45.2 and do not show it as an update. Download it from the release page.

### New

- **Settings menu.** 20 main rows in 6 groups: Playstyle, Money, Building, Items and people, Security and diplomacy, Interface. Every older setting is under Advanced (11 subgroups) or on a page. Ten main rows are presets that set several older settings; a row shows Custom when its settings match no preset. Each main row has one line of 20 words or fewer, with the detail in hover text. Settings screen text: 1,458 words (0.2.45: 2,980). See [Settings](settings.md).
- **First-time setup.** 5 rows: Spending, Extras, New colonies, War (Nexerelin) and Credit reserve. Everything else uses the recommended settings.
- **Spending presets.** Thrifty (30% of net income, 5% of savings, new upkeep up to 5% of net income), Normal (50%, 15%, 10%; the 0.2.45 recommendation), Lavish (80%, 30%, 20%). The per-project cap is Automatic: half, all or twice net income by preset.
- **New pages** in the empire panel: Expansion, War (Nexerelin) and Factions.
- **Build planner.** One ranked queue for all colonies: builds, upgrades, surplus terraforming, extra Research Facilities, wonder sites, stable-location crews and story point improvements. See [How it builds](how-it-builds.md).
  - A build must pay back its price, fee and build time within 36 months. The limit rises to 120 months as surplus credits reach 10,000,000.
  - A colony's construction slot goes to the build that earns the most per day of construction.
  - Money goes to the best return per credit across all colonies. A large build that waits for money is saved for, and smaller builds do not spend its savings.
  - A building Autopilot takes down is not built again at that colony for a year. What Autopilot paid for a building counts against taking it down.
- **Money tiers.** Tight, Surplus, Idle money, Rich, Endgame, from free cash (credits less the reserve, production still to pay and 2 months of upkeep and fleet costs), colony net income and idle months. Saved with the game. See [Money](money.md).
- **Money ladder.** From Surplus up, a share of free cash each month (20%, 35%, 60% or 80% by tier) goes to rungs in order: Stockpiles, Growth, Military, Shipyard, Expansion, Economy, Wonders. Presets: Balanced, Fleet, Expansion, Economy, Save. Each rung is On, Recommend only or Off.
- **Shipyard.** From Idle money, orders ships from your Doctrine priority list through the game's custom production, at the game's prices plus a 2% fee. One order a month. Stops at 6 unclaimed hulls. Default: Recommend only.
- **Stockpiles.** Buys goods for a colony's Local Resources when a shortage cuts industry output or patrols and the lost output is worth more than price, tariff and shipping. Up to 3 months of cover.
- **Military rung.** From Idle money, upgrades stations, Patrol HQ and Military Base at Auto colonies and buys military goods. New upkeep from it stays within 15% of colony net income.
- **Auto-expansion** (the New colonies row). Off, Ask first or Auto. Founds colonies by Nexerelin colony fleet or an Autopilot crew, at cost plus the fee, with an administrator when the colony limit needs one. Default: Off. See [New colonies](expansion.md).
- **War level** (Nexerelin). Off, Defend, Retaliate, Hold them down, Conquer, Scorched. Replaces the War fleets setting. Default: Retaliate, which is 0.2.45's Protect and strike back plus Retake (below). War fleets act whatever the colony modes are, Advisor included. See [War and security](war-and-security.md).
- **Faction stances.** Ally, Friendly, Neutral, Auto, Rival, Enemy, Destroy, set on the Factions page. A stance caps the War level used on that faction and sets how agents, IndEvo ambassadors and ceasefire offers treat it. Default: Auto (relations and offers as in 0.2.45).
- **Conquest** (War level Conquer). Invasions of Enemy and Destroy markets through Nexerelin's invasion fleets, one at a time. Conquered colonies get Nexerelin's autonomy taken back and the Security goal until stable. A conquered colony gets the mode from **Conquered colonies start in** (default Auto) and is kept above 0 stability only on Auto. On Approve or Advisor that is left to you.
- **Scorched** (War level Scorched). Saturation bombardment of a Destroy faction's capital after you authorize it on the War page. Auto Colonies applies the reputation penalties a bombardment in person has.
- **Retake** (War level Retaliate and up). Invades colonies that were yours, your lost capital first, with the fee. On for new games and for saves without Auto Colonies, off for saves from 0.2.45. A retaken colony keeps the mode it had before it was lost and is kept above 0 stability only on Auto.
- **Stand down** on the War page: War level to Defend, declarations and bombardment authorization cleared, operations still organizing cancelled with Nexerelin's full refund.
- **Fees on new war operations.** Retakes, raids and blockades from Hold them down, invasions and bombardments pay the fee level times 10% of Nexerelin's price. Relief, defense fleets, base strikes and strikes back pay no fee, as in 0.2.45.
- **Diagnostics.** A Performance section (time per step of the daily pass) and a load-time link check for Nexerelin, Industrial Evolution, Random Assortment of Things, Station Augments and Terraforming Made Easy.
- **On-screen log codes.** TIER, STOCK, SHIPYARD, EXPAND, WAR, DIPLO, SETUP.

### Changed

These rules also apply to saves from 0.2.45.

- **Spending freely.** Starts at the money tier Rich with credits over the surplus line of at least half the line (1,500,000 over the 3,000,000 line). Ends when the tier drops under Rich or the credits over the line fall under a fifth of the line (600,000). Off with no credits over the line. 0.2.45.2: started at 10,000,000 over the line and ended under 7,000,000.
- **Endgame economy.** Starts at the money tier Endgame (Rich, and colony net at the Endgame row's line) while spending freely. Ends when spending freely ends or colony net goes below 0. 0.2.45: ended when the credits over the surplus line fell under 5,000,000 or net income went below 0.
- **Hazard pay.** Below the money tier Rich, a colony gets hazard pay only if it reaches its next size within 24 months with it (36 months when that size adds an industry slot). A colony already paying keeps it up to 25% past that. This applies whatever the Growth rung's mode. With the Growth rung On, the hazard pay budget rises to the tier's share of colony net (Surplus 40%, Idle money 60%, Rich and Endgame no tier cap), up to the rung's room for the month, and stays at least your setting's budget.
- **Capital trade buildings.** A trade building that costs more than it earns is built only from the money tier Surplus up, with the Economy rung On, while the upkeep of all such buildings at the capital stays within 5% of colony net. Commerce and trade buildings already built are unchanged. The make-room swap (taking down an industry for a trade building) runs only from the money tier Idle money up.
- **Surplus research and surplus terraforming.** They start something only from the money tier Surplus up with the Economy rung On. On Recommend only or Off they start nothing.
- **Surplus research.** Below the money tier Idle money, another Research Facility must be worth more than its full upkeep (0.2.45: a quarter of it). From Idle money up, more than half its upkeep. Below Rich, surplus research builds at most 3 Research Facilities of its own. The endgame still builds them while research is left.
- **Shipments between colonies.** A shipment on a route with a load sent in the last 7 days and still on the way joins that load and pays no new fee. The whole load arrives once the new part has travelled its full time, at most 7 days later than the earlier part's planned arrival.

### Fixed

The 0.3.0 planning read 58 game months of one campaign's logs (versions 2.41 to 2.44). These were the largest wastes, with the 0.3.0 change for each:

| Found in the logs | 0.3.0 |
|---|---|
| 69 builds and upgrades for 17.77M; about 2.27M of that was later taken down | Taking down uses the same value math as building. A building taken down stays off that colony's list for a year. |
| The budget sat at its 6-month carry cap in 15 of 58 months, peak 11.59M saved; up to 19.9M of budget room discarded | Budget room past the cap is kept while the credits are in your account above the reserve. The money ladder spends idle money. |
| 95 of 133 "nothing worth starting" lines (71%) were a busy construction slot; two 360-day TME structures held a slot 11 months each while a Heavy Industry worth about 218,000 a month waited | The slot goes to the most value per day of construction. |
| Ming Adi: 1.525M paid to add Toxic Atmosphere, then the plan wanted to pay to remove it | A world plan that already paid for terraforming switches only to a plan worth at least twice as much. |
| 16 missed trade contract deliveries on 11 contracts | Auto Colonies counts its own contracts against spare goods until Theory of Toolbox catches up. One new contract per commodity a month. |
| Free port changed 37 times, hazard pay 24 | A closed free port reopens after its conditions hold 30 days. Hazard pay uses a 3-month median of colony net income. |
| One month at -188,000 net switched hazard pay off at every colony for 30+ days | Budgets, caps and hazard pay use the 3-month median of colony net income. Custom production, AotD orders and your fleet's draw on colony stockpiles are not in it. |
| Plan bonuses (60,000 / 40,000 / 20,000) and a flat +15,000 for upgrades outweighed the value math | Only enabler steps keep a plan bonus. Other plan steps are valued by what they earn, plus half of what they lead to. |
| 52 sieges, 13 of them over in under 5 seconds | A passing fleet starts a siege only when it can threaten the colony or stays a day. |

Other fixes:
- Shortages count buildings already under construction, so two colonies do not build for the same shortage.
- Every step of the daily pass runs on its own. A step that fails 3 days in a row stops for the session, with one log line and a Diagnostics entry. The 0-stability safety net and the other safety steps keep running.
- The 0-stability safety net runs before the day's building.
- One stability rule for every takedown, move, pull, policy and new action: the colony stays at or above the floor (minimum stability: 5 by default, at least 3).
- Timers run on the game calendar. Loading an earlier save clears every cache and timer.
- Auto Colonies' own errors no longer quarantine buildings. Vanilla buildings are not quarantined.
- The "logged once" list no longer resets at 2,000 entries.
- starsector.log gets a repeated error's stack trace once, then a short line at most once a week.
- The panels show the day's scores and re-score only on Refresh or after a change.
- Research spends databanks on filler techs (no payback within a year) only with idle money: spending freely or the Idle money tier.
- Credits move through one payment function. An offline check fails any other file that changes credits.
- Special worlds: when a building the special worlds need is out of reach at a colony (the Unification Center, which all of them need), every special world that needs it is dropped there for a year, with one log line. Before, each world was dropped on its own, with a new log line every 1 to 7 days.

### Saves from 0.2.45

- Settings carry over. War fleets "Protect and strike back" becomes War level Retaliate, "Protect" becomes Defend, Off stays Off.
- Faction priorities become stances: High to Friendly (Ally when Stop raising relations at is Never stop), Normal to Auto, Ignore to Rival. A stance from a priority keeps 0.2.45's diplomacy until you set it on the War page or the Factions page.
- Retake is off. Auto-expansion is Off. No faction is set to Enemy or Destroy. Bombardment is not authorized.
- Money ladder: Balanced. Shipyard and Expansion rungs: Recommend only. Stockpiles, Growth, Military, Economy and Wonders rungs: On.
- The per-project cap stays a Fixed number unless it equals the Automatic cap.
- The money tier starts at Tight and rises from there. A save that was spending freely starts at Rich.
- One on-screen line: "SETUP / Settings: moved to the 0.3.0 menu, nothing you set changed". A What's new in 0.3.0 box shows on the Settings page for 30 days.
- Removed settings: admin delay (hires always took 30 days) and four old fields that moved to Easy Sector in 0.2.37.0.
- The 0.2.45 build engine is in 0.3.0.x behind a hidden switch (`legacyPlanner`) in case the new planner misbehaves. If the planner fails 3 days in a row, the old engine runs for the rest of the session.

### Not tested in a running game yet

0.3.0.0 passed the offline checks (it compiles, its bytecode verifies against every combination of the optional mods, 75 check suites) and a short in-game test on one save from 0.2.45.2. These parts have not run in a game yet. They need a Nexerelin war, a commission or a new colony:
- Saturation bombardment by a player-faction fleet. Nexerelin has no fleet request type for it.
- Nexerelin colony fleets for the player: crew capacity, and the hand-over of the new colony to you.
- Conquered colonies: autonomy taken back, rebellions, a colony lost to rebels.
- An invasion while you hold a commission (blocked by design).
- Ceasefire offers accepted and rejected by stance.
- Stand down and Recall all refunds.
- An Autopilot crew founding a colony.

## 0.2.45.2 - 2026-11-09

Three fixes pulled forward from the 0.3.0 work, for the stuff that wastes the most money while 0.3.0 is still being built:

- **Nothing gets built, torn down and built again.** Whatever Autopilot takes down at a colony (dead weight, over the industry limit, 0 stability, an endgame swap) stays off that colony's build list for a year, and so does whatever it was upgraded from. That's the Junkyard Forge Wind rebuilt a month after its Scrapyard came down.
- **No more free +15k "follow-through" for upgrades.** An upgrade now has to be worth it on its own, so stuff like Heavy Batteries with a 25-year payback stops getting built.
- **Spending freely stops flip-flopping.** It still starts at 10M of surplus but now stays on until you drop under 7M, so a couple of big buys don't flip everything back the same week.

## 0.2.45.1 - 2026-11-06

A bug-fix release. I had a bunch of agents go through the whole mod and my own logs while planning 0.3.0, and these turned up first:

- **Shipping was charged twice.** Every item or AI core Autopilot shipped between colonies cost double what the log said. Recruiting agents had the same bug. Both pay once now.
- **One error could skip the rest of the day**, including the step that keeps colonies off 0 stability. Every colony and every build now gets its own error guard, and the 0-stability and dead-weight steps run even if something before them fails.
- **Colonies with something under construction were re-planned every single day.** Now it's weekly like everyone else, plus right away the day the build finishes. Saves a lot of work every day, and upgrades during construction may start up to a week later.
- **A build or upgrade that didn't actually start was still paid for.** Now nothing's charged unless it took, and a building that got half-added when something broke comes back off.
- **Weekly stuff stalled after loading a save** without restarting the game: calibration, storage logistics, the market check, the mod watch and augment re-plans sat out for as long as your previous session ran. Fixed.
- **The Capital row in Settings didn't work.** Clicking it bumped your per-project cap by 500k instead. It picks the capital now (the buttons on the colony screen always worked).

## 0.2.45.0 - 2026-11-06

- **Oddisz's mods are supported.** Oddisz stopped by the thread, so here's support for the colony side of [his mods](https://fractalsoftworks.com/forum/index.php?topic=36175.0), read straight from their source:
  - **Cloning**: the three cloning industries get built when faster growth is worth it, valued against how fast the colony grows on its own and using your LunaLib numbers for them. They cost stability equal to the colony's size, and that grows with it, so it only builds one where there's room for that to grow a size, and never at a colony that's already maxed out.
  - **Reverse Engineered Private Arsenal**: left to you. The hub only works on gear you feed it and taking it down wipes its markets, so the manager never builds, upgrades or takes it down, and never touches the core or Drone Replicator you put in it.
  - **Sol Renewed**: Sol has its own crisis that grows every month from your colonies and industries there and never goes down, with waves that raid and saturation-bomb. Industries in Sol are priced for what they add to it, and the Crises page shows the meter.
  - **Toolbox Economy Curves** needed nothing, the manager already plans with whatever curve you pick. **Coalesced Little Mods** gets the same handling as the standalone Cloning and Private Arsenal. The rest of his list (Gambling Den, Extra System Reloaded, Hullmods - Renewed, Intel Renewed, StopBloatingMe, StopStackingMe, StarterPack, Discord Rich Presence) checked out fine as is.
- Also: the over-limit cleanup and the new dead-weight cleanup from 0.2.44.1 never take down a building that's left to you (a Spy Bureau, a Bazaar, the Reverse Engineering Hub and so on), since you built those yourself.

## 0.2.44.1 - 2026-11-06

- **It cleans up the extra industries it builds.** Zenemosrae ended up with 5 industries on 3 slots and Ming Adi with 4 on 3, and it was the manager that did it. Ashes of the Domain lets a structure upgrade into an industry along more than one path (Biolab into Grey Industries, Junkyard Forge into Scrapyard, Trade Outpost into Commerce), and the game only counts the vanilla path against your slots while the upgrade is under way. So the manager thought it had room and started three of them in one go. Now upgrades into industries count against the limit the whole time they're under way, everywhere it checks for a free slot.
- **And when a colony is over the limit anyway, it actually fixes it.** It used to keep the extras if the weakest one earned more than the 5 stability was worth, or if none of them was one it knew how to value, and just tell you it was up to you. Now an Auto colony over its limit takes down its weakest industry, one a day until it's back within the limit. It goes for the ones a colony can do without first, never one that holds up its stability, and it still waits if the colony is about to grow into the room. Cores and items go to Autopilot Storage like always.
- **Industries that stopped paying get taken down.** If an industry's whole worth is what it makes, and it's been worth less than its upkeep for 30 days straight, it goes (one per colony every 30 days). That's the Roider Dives at Godecifica and Zenemosrae: Mining already makes more ore and organics there, so they only cost upkeep. It's valued the same way a build is, so it won't turn around and build it again. It never touches anything you locked, a plan step or anything that upgrades into one (Wind keeps its Dives for the capital's Union HQ), military stuff, defenses, ships, or anything that adds stability. On by default under **Settings, Building and planning, Take down industries that stopped paying**; lock a building on its colony panel to keep it no matter what.

## 0.2.44.0 - 2026-11-06

- **Colonies stop all making the same stuff.** The manager used to think every unit of anything was worth the same flat rate, so it kept putting the same producers everywhere: Mining at three colonies, Heavy Industry at three, the same forge at all four. But that's not how the game pays you. A colony's exports earn its share of that good's market across the whole sector, and the share is what it can ship times its accessibility, out of everyone's total. So your fifth colony making ore just splits the same pot the other four already hold, and a small extra producer can round down to 0% and earn literally nothing. Now every building is valued by what it actually adds to your share of each market: a good you barely make (or nobody makes) is worth a lot, more of something you already dominate is worth very little. On top of that, a building only counts what it makes above the colony's own biggest source of that good, only what the colony can actually ship off-world counts, and a building that's still under construction counts as already made, so two colonies don't both start the same producer a month apart. Covering a shortage your own colonies have still counts extra, like before. It's on by default; **Settings, Building and planning, Exports valued by market share** turns it off if you want the old flat rate back. The Diagnostics report gets a market section (each good's sector value, your share, which colonies export it, what one more unit would add), and once a week the log checks the model against the shares the game itself shows and says where you hold the most and the biggest markets you don't supply. More in [How it builds](how-it-builds.md#how-it-picks).

## 0.2.43.2 - 2026-11-06

- **The manager isn't in love with Roider Dives anymore.** It put a Roider Dives at every colony I had. Turns out it was counting everything the Dives would mine, but a colony's supply of something is its biggest single source, not all of them added up. Every one of those colonies already had Mining making more ore and organics than a Dives ever would, so the Dives added basically nothing and still looked like the best build whenever money piled up. Now a Dives (and the step up to Union HQ) only counts what it makes above what the colony already makes, the same way station augments already worked. The Dives you already have stay; take them down by hand if you want the slot and the upkeep back. Your capital still goes for a Union HQ as one of its trade buildings, and that needs a Dives first, so the capital can still get one.

## 0.2.43.1 - 2026-11-06

- **No more false Roider Union warning.** The mod watch kept flagging Roider Union with "1 warnings" in the log, on and off every few days. That was the check being wrong, not the manager. It compared everything a Roider Dives mines against Roider's formula and only knew about AI cores. But the game puts a building's whole supply bonus (an Industrial Planning administrator, an improvement, the Charter) on every Roider output with a positive line, even one that nets to 0, like transplutonic ore from abundant roids. With Wind's administrator that's 3 extra units, so it warned whenever Wind wasn't short on heavy machinery. Now it checks Roider's own supply lines against the formula, which is what changes when Roider rebalances, and just lists the bonus and any shortage next to it. Also: a mod watch warning now says what it's about right in the log line, not just how many there are, and void roids that are only sparse or trace no longer count as something Dives can mine (Roider doesn't count them either).

## 0.2.43.0 - 2026-11-06

- **Low profile.** A new way to expand. By default the manager goes all out and treats the attention that draws as just another cost, which means a steady stream of colony crises. Low profile keeps your colonies growing but stays under the radar: attention costs ten times as much, no AI cores in colonies above size 3, nothing that takes a colony to 7 Pather interest (where a cell comes), no free ports, no raids on other factions' colonies, no sabotage by agents, and Hegemony inspections are never resisted. Exports competing with Tri-Tachyon, fuel from 4 units and farming-only habitable colonies (Luddic majorities) are priced in. Each rule lifts once that faction's crisis is over for good, so beat the Hegemony's inspections and the cores come back. Pirates and the Persean League grow with colony size, so nothing avoids those; Low profile builds military structures sooner instead. It earns less. The manager asks which way you want to go when you found your first colony, and you can switch any time under **Settings, Expansion style**. All of it is in the new [Low profile](low-profile.md) guide.

## 0.2.42.4 - 2026-11-06

- **Research goes one tech at a time.** Auto research went down its whole list every time: for each tech short of databanks at the Research Facility it shipped databanks over and posted "X starts once they arrive", so you'd get five or six research lines on screen at once (Element Nucleosynthesis, Climate Reformation, Megastructure Engineering, Consumer Goods Production...) and databanks headed over for all of them. Then it started some cheap filler tech that used up the databanks it had just moved for the first one, and the next pass did it all again. Now it picks one tech: if its databanks have to come from another colony, they get shipped, you get one line, and nothing else gets shipped or started until they arrive and that tech starts.

## 0.2.42.3 - 2026-11-06

- **Update notifications.** The mod ships a Version Checker file now, so if you have MagicLib (it has Version Checker built in) or a mod manager like TriOS, you'll get told when there's a new version, with a link to the download and the patch notes. Mod managers can grab the update for you too. Nothing changes in game; this version just has to be installed once by hand, and from here on you'll hear about the next one.

## 0.2.42.2 - 2026-11-06

- **A colony can't be left sitting at 0 stability anymore.** This one's bad and it's on me. The game decivilizes a colony that stays at 0 stability for months (it warns you once, then it's a dice roll every month), and an Auto colony could sit at 0 forever: Wind had 3 industries on 2 slots from the bug 0.2.42.1 fixed, which costs 5 stability, and it decivilized. Now:
  - At 2 stability or less, a colony over its industry limit takes down an industry right away, whatever it earns (before it waited a month and compared income).
  - Still at 0 after 20 days, it takes down whatever building costs it the most stability, read straight from the game's own numbers, so mod buildings count too (Commerce is -3, Bazaars and pirate havens cost some too). One every 10 days until it's off 0. A capital trade building taken down this way stays off the capital's plan for a year.
  - Any colony at 0, Auto or not, gets an on-screen alert every month with its biggest stability drags, so you know what's going on.
  - The capital's trade buildings wait while its stability is under 3, since most of them cost stability.
- **Losing your capital doesn't move your stuff anymore.** When Wind got captured, Ming Adi became the capital for good, and once Wind was back the weekly gathering shipped Wind's whole Storage (853 weapons, 39 wings) to Ming Adi. Now a captured capital stays your capital: another colony only takes production deliveries until it's yours again, nothing gets gathered meanwhile, and it's the capital again the day you get it back. And with Storage logistics switched off, the gathering only ever ships to the capital, never to a hub you picked back when logistics was on.
- **Agents actually do security now.** With Agents on, if you had an idle agent of your own, Autopilot only told you to hand it over and never recruited one, so nothing was stopping Pather cells. Now it takes your idle agent over for security (Pather cells, rebellions, hidden pirate bases) and tells you; you can take it back on the Mod features page. Saves from before this get Agents on Defense once if they had them off.

## 0.2.42.1 - 2026-11-06

- **Never more industries than slots.** One weekly pass could start a new industry and an upgrade that turns a structure into an industry at the same time, and both counted the same free slot. That's how Wind ended up with 3 industries on 2 slots (the Junkyard Forge became a Scrapyard the same week Smelting went up). Now every build checks the slot count right before it starts, like the game's own build menu does.
- **Colonies already over the limit get fixed.** Going over costs a flat 5 stability, and under 5 stability every point cuts a fifth of the colony's income, so a colony sitting at 0 earns nothing from its industries at all. Wind was in exactly that spot. Now an Auto colony over its limit takes down its weakest industry (its AI core and item go to Autopilot Storage), unless that industry is worth more than the stability it costs, or the colony is about to grow a size that has room for everything. One per colony every 30 days, never defenses, fleets, ship-making, improved or locked buildings, or anything a plan needs. Switch it off with **Fix colonies over the industry limit** under Building and planning.

## 0.2.42.0 - 2026-11-06

- **Your capital.** One colony is now your home port, where all your stuff ends up and where you come back to sell loot and refit. The manager picks the colony where your things already are (your production gathering point first), or you pick it on a colony's panel, in Settings, or just by moving your gathering point. Details in [Your capital](capital.md).
- **Everything goes there.** Custom production gets delivered to the capital, and once a week the ships, weapons, wings, blueprints, supplies, fuel and crew in your other Storage tabs get shipped there (the usual storage logistics fee and travel time, one load per Storage). Colony items and AI cores still go where they do the most good.
- **It turns into a trade hub once you're making money.** After the fast buildout, with net income over 100,000 a month (you can change that), the capital's plan puts its trade buildings first: Spaceport and Megaport, Commerce (the Open Market on your own colony), and whatever your mods have: Ashes of the Domain's Trade Outpost and Underworld, Yunru's Bazaar, Industrial Evolution's Requisitions Centre and Salvage Yards, Roider Union's Union HQ. Then a Waystation. They hold industry slots and get built even when they don't pay for themselves, and if every slot's taken the capital takes down its weakest ordinary industry for one, at most one every 90 days (never defenses, fleets or ship production). It waits if your net income goes negative.
- Yunru's Bazaar used to be left to you everywhere. Now it gets built at the capital and nowhere else.
- **A station at every colony.** Stations hardly ever got built unless a colony was on Security or under threat. On Balanced a 250,000 Orbital Station worked out to about -2,000 a month, because its price spread over three years outweighed what its defense and fleet were worth. That's backwards: a station is how a colony fights off fleets, and a raid, bombardment or invasion has to get through it first. Now every colony plans one once its fast buildout is done and it's size 4 (or half a year old), a Battlestation from size 6 and a Star Fortress from size 8 (a size sooner at the capital). They follow your station line, and you can switch it off with **Station at every colony** under Building and planning.

## 0.2.41.2 - 2026-11-06

- **Administrators for the colony limit actually show up now.** Turns out the game only puts an administrator up for hire when you dock at a market (5% a visit, more at big ones), so friendly markets you never visit never have one, and the manager just kept saying "none for hire" month after month. Now, past your colony limit with nothing on offer for 90 days, it pays a recruiter: a basic administrator (no skills) for 250,000 on top of the normal hiring cost and fee, showing up 30 days later like any other hire. Expensive on purpose, and it still never dips under your reserve. The "Administrators needed" line now says how many days are left before that happens.

## 0.2.41.1 - 2026-11-06

- **Easier to read on screen.** 0.2.41.0 went overboard: the hash tags, the `::` and `//` everywhere, the clipped lines and the blue-ish text made the on-screen lines hard to follow. Now each one is just a short code, a slash, and the normal sentence: `BUILD / Hot Springs: Planetary Shield (750k¢)`, `RECEIVED / Ming Adi: Gamma Core`, `SIEGE / Umuzi: hostile fleets at the colony, defenses only`. Nothing gets cut off, and every line, pings too, is back to the game's normal white (yellow for the important ones). Pings get their own `SCAN` code, and a line it has no code for just shows the sentence. The panels and settings are unchanged.

## 0.2.41.0 - 2026-11-06

- **It talks like a machine now.** Everything the autopilot does still pops up on screen as it happens, but as a short readout instead of a sentence: `AP\\ #HOT_SPRINGS :: BUILD // Planetary Shield // 750k¢`. The colony gets a hash tag, the action gets a code (BUILD, INSTALL, SHIP, SIEGE, CLEAR, RESEARCH, LEDGER and so on), big numbers get shortened (750k, 1.25M) and long explanations get clipped. The Log page in the panel still has the full sentence for every entry, so nothing's lost. If you liked the old "Autopilot: ..." lines better, there's a new setting under Settings, Interface: **Announcement voice**, Machine or Plain.
- **It tells you more of what it's thinking.** With the machine voice and every log entry on screen, you also get little pings about stuff it's weighing in the background: a colony's scan when nothing's worth building right now (how many options it looked at and the best one), and inspection points after AI cores move. Each one shows at most once a week per colony, and they stay off the Log page.
- **New look for both panels.** The colony panel and the empire panel in Intel got a cleaner, techy look to go with it: a faint grid behind everything, thin frames with cut corners and bright corner brackets around the page list and the page, section headers like `//  BUDGET`, buttons in caps with cut corners, the open page marked with `>` in the page list, and a status readout under the title. Settings rows read `LABEL  ::  value   // rec ...` with a CYCLE button, and the groups open with `[+]`. It all uses the game's own UI colors for your faction, so it still looks like Starsector.

## 0.2.40.1 - 2026-11-06

- **Economic stations.** A colony in a system that's well defended (other stations and military bases there that aren't hostile to you, yours or anyone's) keeps its station economic even when its own threat is high: the Mining Station augment, Hydroponics and Economy Conversion over combat augments. A raid or blockade on its way still gets the combat loadout. This only changes the station's augments, not what the colony builds.
- Station Augments' **Mining Station** augment counts now, valued with that mod's own formula (the asteroid belts and rings near the station), only for what it mines above what the colony already makes.
- **Terraforming & Station Construction stations.** If you have TASC, Auto Colonies can found its mining stations (in an asteroid belt or field) and siphon stations (over a gas giant) as new colonies, in systems with your Auto colonies that are calm or well defended, once TASC's research for them is done and there's room under your colony limit. A crew builds it for TASC's own costs plus a fee, in 60 days, the same way TASC's Construct abilities place it, and the new colony runs in Auto with the Money goal. It's Recommend only out of the box (it just tells you the best site) until you switch it to Auto under Settings, **Station construction**. The astropolis is left to you, it's a habitat rather than an economic station.

## 0.2.40.0 - 2026-11-06

- **Stable locations.** The stable spots in a system (where comm relays, nav buoys and sensor arrays go) get used now. In any system with your Auto colonies, Auto Colonies works out what each one is worth to the colonies there, going by their goals, and sends a construction crew to build the best one: a comm relay first for most goals (+1 stability at every colony of yours in the system), then a nav buoy (+2 burn, worth more to Security colonies), then a sensor array. It costs vanilla's materials at base price plus a fee, takes 10 days, and only builds one of each kind per system since they don't stack. If the spot gets taken before the crew shows up, you get the materials back.
- With **Terraforming Made Easy**, makeshift ones you own get upgraded to Domain-era (twice the stability for the relay) when there's a spare Beta core in Autopilot Storage and it pays for itself.
- With **Ashes of the Domain**, once you've researched them and have a Coronal Hypershunt Tap in Autopilot Storage, it builds Hypershunt Receivers (+2 industry slots at every colony in the system) in systems your hypershunt reaches. While you own a hypershunt, one stable spot per system is kept free for one.
- New setting under Settings, **Stable locations**: Auto (default), Recommend only, or Off. Crews on their way show on the Autopilot Storage page.

## 0.2.39.3 - 2026-11-06

- **Panopticon cores** (Emergent Threats: IX Revival). The Panopticon Instance and Panopticon Core couldn't go in Autopilot Storage at all. Now they can, and once a day one goes into an Auto colony's Surveillance Center that doesn't already have a better one (the Instance first, and the colony with the most of your colonies in its system first), shipped over first if it's stored at another colony. A regular core it replaces goes back to storage. The Hegemony doesn't count either of them for inspections.

## 0.2.39.2 - 2026-11-06

- **Autopilot Storage only takes what it can use.** It used to take stuff the manager has no use for: UAF's Access Key Cards (the ones you turn in at the Aurorans for credits) went right in, and so did blueprint packages you'd already learned, Niko's building blueprints and so on. Now only colony items it can install, AI cores, databanks, Antimatter Stabilizers, VPC chips and recipes go in. Anything else already in there moves to that colony's normal Storage, with one log line.

## 0.2.39.1 - 2026-11-05

- **The Unification Center waits for the rest of its world.** By itself it's +1 stability for a pile of upkeep plus marines and supplies; its whole job is the final conversion (Hive World and so on), which needs every other building and condition in place first. It used to go up as soon as research unlocked it, which is way before the Geomorphology Station (Global Centralization comes before Surface Restructuring), then just sat there for months eating upkeep and hogging the colony's one construction slot. Now it waits until everything before it in the plan is done, or the last condition is already being made by a running project, so it's built during that last project and ready about when the world qualifies. The colony panel says "waits until the rest of the plan is ready" in the meantime.

## 0.2.39.0 - 2026-11-05

- **Storage logistics** (new, off until you switch it on, under Settings). Pick a refit hub (your production gathering point by default) and once a week everything you'd refit a fleet with gets shipped there from your other Storage tabs: mothballed ships, weapons, fighter wings, blueprints and hull mod specs you don't know yet, and supplies, fuel, crew and marines if you want. Pick a sell hub too and blueprints and specs you already know, plus spare copies, go there, so you can sell them all in one place. Fair play like everything else: each load costs a shipping fee (plus 1,000 a ship) from the monthly budget and takes travel time.
- **Production orders get their goods.** Ashes of the Domain's production orders (Auto Outfitting's big weapon orders, say) take goods from your colonies, and crawl when nobody makes one. Now Auto Colonies plans producers for any good your open orders need that none of your colonies make, the same way it already did for special projects. It also knows the plain vanilla producers now (Refining for metals, Heavy Industry for heavy machinery and supplies, and so on), not just AotD's.
- **Unspent budget is saved up now.** The monthly budget didn't carry over, so anything that cost more than one month's budget (a lot of buildings early on, when the budget's around 60k) just sat there waiting while your credits piled up, and the colony didn't build much of anything. Now whatever's left each month carries over, up to six months' worth, so it saves up and builds the thing. The budget lines show what's saved up.
- Mod watch only writes to the log when something changes, instead of ten identical lines every week.
- Fixed: once a colony got Luddic Path cells, every mod building came up "can't be measured". The test colony Auto Colonies uses to measure buildings copies your colony's conditions, and Pather cells (like pirate activity, a blockade and the other event conditions) break without the event behind them. Those are left off the test colony now, and if any other condition ever breaks it, the measurement goes ahead without it.
- **Colony items go to the best building first.** Items used to go in storage order, so a lesser item could grab a building a better one was waiting for and get swapped out a week later (an Auroran nanoforge into Heavy Industry, then the Tenebrian one), which costs a fee and a day each time. Now every waiting item is matched against every free building and the best pairs go first.
- Fixed: a second copy of an item could get shipped to a colony whose first copy had already arrived but wasn't installed yet (two Orbital Fusion Lamps and two Fullerene Spools to Umuzi). A copy that's already there now gets the building, and nothing else gets shipped for it.
- **Research Facility first.** With research on Auto and no Research Facility anywhere, one now goes first in your focus world's plan (or your biggest colony's). Before, spending freely put every defense structure first, so the Research Facility waited months behind a Planetary Shield and a Patrol HQ, nothing got researched, and plan steps that needed research gave up.
- Roider Dives and Union HQ: Roider takes a heavy machinery shortage off every mined good, so being 1 short turns 1+1+1+1 into nothing. Auto Colonies counts that now when it decides whether a Dives is worth building, and mod watch names the shortage instead of calling it a mismatch.
- The diagnostics report has a colonies section: each colony's plan and where each step stands, its buildings with their items and cores, the construction queue and what's in its Autopilot Storage.
- **Contracts are safe from the endgame.** The endgame took down Hot Springs's Farming for Light Production, and the big food contract Farming was feeding started missing deliveries. A building that makes something one of your trade contracts needs is never taken down while that contract runs now.
- Fixed: VPC chips for Industrial Evolution's Commodity Forge (and the other chip buildings) did the same double shipping as colony items, and worse: a different chip each day, every kind twice, for one forge. A chip that's already at the colony goes in first now, and nothing else is shipped while one's on its way.
- Mod watch: an Omega core in Patrol HQ or High Command doubles fleet size there, not output, so it no longer warns that the doubled output is missing. Roider's check counts AI cores now (an Alpha or Omega adds 1 to each mined good, and an Omega doubles it), and lists each good, mined against expected.

## 0.2.38.0 - 2026-11-05

A bunch of new colony mods supported, a big fix for how mod buildings get measured, and a pass to make sure nothing ever gets stuck waiting on a mod (or a building) you don't have.

**New mods supported** (all optional, any mix works, details in [Supported mods](supported-mods.md)):
- **Omega Core Everywhere** and **Astral Ascension Renewed**: drop Omega cores in Autopilot Storage and they go where they do the most. In a building with Omega Core Everywhere (they don't count toward Hegemony inspections there), or governing a colony with Astral Ascension Renewed, but only where your Inspection risk setting allows its 10 inspection points. The manager never swaps out an AI core administrator, so it'll never set off that mod's civil war.
- **Niko's Scavengable Industries**: only blueprints you've learned get built, and the ones that do big things you'd want to decide on (Blast-Core Mining eating your deposits, the spy buildings, the Cryosanctum's crisis, the size-5 Planetary Defense Network and a few more) are left to you.
- **Junk Yard Dogs** and **Carter's Freetraders**: their forges and Biolab get built when they pay. Drugs and hand weapons your faction bans don't export without a free port now, from any mod's building, so they only count for what the colony needs itself.
- **Vulpoids**: Organ Farms and the Biotech Facility, the Midas Nanoforge (never polluting a habitable world), the Mangonut tree, the Nanofiltration Core where it actually clears something, the Lobster Bioforge only where it doesn't kill the organs, and Profecto administrators. The Vulpoid Bioforge itself is left to you, since it starts the whole sanctions and Knights of Ludd story.
- **Yunru Industries**, **Slightly Better Tech-Mining** and **Yunru's Worlds**: Secondary Drydocks valued by what it adds next to your Heavy Industry, Tech-Mining valued with Slightly Better Tech-Mining's much bigger numbers, Advent Outreach never improved (it does nothing), and the Bazaar left to you.
- **Roider Union** got rechecked against 2.3.1: a couple of Union HQ numbers were off, and the Historical Roider Charter's extra output and range count now.

**Mod watch.** The Diagnostics page (and the report file) now says how each of those mods is doing in your game, weekly: OK, WARN or FAIL, what's built where, whether measured buildings really make what the test colony said, and checks of its own for each mod (is the Omega core's double output actually applied, does the Omega-governed colony's size limit really go to 15, and so on).

**Fixed:**
- Mod buildings measured on the test colony were getting their output cut by fake shortages, since the test colony isn't in the economy and nothing's ever for sale there. Anything that needs inputs measured as making little or nothing (AotD's Artisanal Farming read zero food). Now the test colony has everything on hand, and inputs are counted separately like they always were.
- The Paradise plan was missing Light Industry, Commerce and Waystation since 0.2.30.0 (they'd slipped into a code comment).
- If Building Menu Overhaul's code ever can't be read (after an update, say), the manager used to build nothing at all anywhere. Now only what research would lock stays locked.
- Fast buildout never ended for a colony that couldn't turn a profit, and that colony never got its plan, defenses or anything else. Now a day with nothing that pays runs the normal plan, and the buildout ends after 60 days of that or a year in total.
- Hostile fleets that just live in a system (Remnant, IX patrols, a pirate base) kept a colony "under siege" forever, building nothing but defenses. After 30 days of only that, it's treated as how the system is. Fleets at the colony, blockades and raids still count.
- The Bifrost Control Center step could never be built, so the Bifrost plan waited forever.
- On a Floating City short on fuel, even the Fuel Production that would fix it was blocked.
- A station line whose first station needs a blueprint you don't have meant no station at all; that colony picks by score now.
- Special project producers the manager doesn't build don't sit in the plan anymore; you get a log line instead.
- Surplus terraforming's markers on hazards it wants to keep now expire even with surplus terraforming off.
- With Vaults of Knowledge, the first Research Facility is worth building when your plans have research-locked steps, even before research has ever run.

**Nothing gets stuck anymore.** Every plan step gets checked each day for a way to it: can it (or something on the way to it) be built here, or will research the manager is running unlock it? A step with no way for 60 days stops holding the plan's top spot and its industry slot, and the log tells you. It counts again as soon as a way opens. A Terraforming Made Easy world whose own buildings are out of reach gets dropped for a year and the colony follows the plain plan for its goal. A world that needs Mining or Farming isn't picked where those can never go. And the slot a plan keeps free now counts one per upgrade chain (Heavy Industry and Orbital Works used to reserve two).

## 0.2.37.0 - 2026-11-05

The fair play release. A review went through everything the mod does and anything that gave you something for free, or changed the game's rules, got fixed or removed.

- **Autopilot Storage is per colony now.** It used to be one shared storage at every colony, which meant items and cores basically teleported across the sector. Now each colony has its own, and anything the manager needs somewhere else gets shipped there for a fee and some travel time (a couple of days plus a day a light-year). Your old shared storage gets moved into your biggest colony's.
- **Agents are recruited, not conjured.** They used to be created on the spot. Now recruiting one costs 12,000 plus the fee and takes 60 days. They also get roles: the first one on security, the second raising relations with your preferred factions, and with offense on a third that sabotages whoever you set as the target (whoever attacks you, a random enemy, or a faction you pick). Enemy operations the game hides from you aren't shown or used anymore.
- **Remote stuff takes time.** Administrators and IndEvo ambassadors always take 30 days to arrive (that used to be optional). The RAT settlement gets founded by an expedition that has to get there. Station augment changes from afar need a refit crew, 7 days, or right away if your fleet's in the system. Research items and databanks from other colonies or your fleet get shipped to the Research Facility.
- **Removed:** the storage cheat and the Derelict Empire attack settings. Both changed the game's rules, so they're not in this mod anymore.
- **Fixed:** relief fleets were way underpriced, now they cost what Nexerelin charges. Taking back a colony an old version made autonomous now costs the normal unrest. Surplus terraforming could delete a core or item when it took a structure down. Every measured mod building was being charged an extra 25% of the colony's upkeep because of a test colony quirk. A few places where an error could have reached the game's UI are now caught.

## 0.2.36.3 - 2026-11-05

- **Storage cheat wording.** Its description said Autopilot Storage "takes anything but ships and ambassadors", which read oddly, since neither of those ever moved through storage like items do. Now it just says what the cheat adds: commodities, weapons and fighters. Nothing else changed.

## 0.2.36.2 - 2026-11-05

- **No polluting nanoforge on a habitable world, ever.** A nanoforge that pollutes never goes into a forge on a habitable world now, whatever the colony's goal, threat or the endgame. Before, a habitable world only knocked a quarter off the item's score, and War production doubles what a nanoforge is worth, so a War colony could still take one.
- **Waiting items skip those forges too.** An item sitting in Autopilot Storage doesn't count a habitable world's forge as a place to go, and no forge gets built there just to hold one.
- **Ones already in come back out while they still can.** At an Auto colony, if the pollution can still be undone (it turns permanent after 90 days), the nanoforge goes back to Autopilot Storage. Lock the building to keep it.
- **Forge upgrades that would pollute again are shown, never started.** Same for every goal.

## 0.2.36.1 - 2026-11-05

- **Campaign consumables stay out of Autopilot Storage.** Industrial Evolution's consumables, ship and weapon blueprints, Random Assortment's Forge-Assisted Industry BP, and anything else that gets used up on right click can't go in anymore, even with the storage cheat on. Any already in there get moved to your biggest colony's normal Storage.
- **New option, Research spends colony items** (off by default). With credits over your surplus line, Auto research can spend colony items like Pristine Nanoforges on techs that need them. **From storage** only takes them out of storage; **From storage and buildings** can also pull them out of buildings, the one where the item does least first, never a locked one. Either way it only takes anything once everything the tech needs is on hand.

## 0.2.36.0 - 2026-11-05

- **Endgame economy.** Once you're spending freely (10M+ over your surplus line) and making 1M a month or more, money stops being the goal at every colony that spends the surplus. Credits count 5%, and fleets, ship quality and defense count a lot more. It stays on while you've got 5M+ over the line and aren't losing money. Move the line to 2M or 500k, or switch it off, with **Endgame economy**.
- **Production chains instead of profit.** What a building makes is valued by what it does for your whole economy: goods none of your colonies make come first, then your own shortages, then grabbing more of the sector's production, with a second producer on another world worth extra. War goods count 1.5x and Ashes of the Domain's advanced goods 2x.
- **Weak industries get replaced.** With no free slot, an Auto colony can take down its weakest non-military industry for one worth at least 10,000 a month more. Slowly: one a day across your empire, one a month per colony. Its AI core and item go to Autopilot Storage, and ship producers or anything improved, locked or in the plan never get replaced. **Endgame replaces industries** turns this off.
- **Income can drop, but only so far.** It keeps at least a third of the net income you had when the endgame started.
- **Research and items follow along.** Research Facilities keep going up while there's research left, and AI cores and colony items are placed with the endgame's weights too.

## 0.2.35.0 - 2026-11-05

- **War fleets** (Nexerelin). New setting **War fleets** under Colony policies: Protect and strike back (the default), Protect, or Off. It orders fleets through Nexerelin, never invasions or colony expeditions, and anything aggressive only goes after factions already hostile to you.
- **What it sends.** Relief fleets for colonies under siege, defense fleets where the threat is high or a crisis is coming, base strikes on known pirate and Pather bases within 15 light-years of your colonies, and on Protect and strike back, raids on whoever attacked your colonies in the last 120 days.
- **How much.** Normally one a day, keeping 40% of your fleet request points for emergencies. Relief and defense fleets are paid like emergencies: past the budget if they have to be, never under your reserve.
- **Past 10M it shotguns them out.** While spending freely it's up to three a day, 1.5x bigger, against every known base however far, plus a standing defense fleet at every colony, all paid from the surplus.
- **War fleets section** on the Mod features page: fleet points, what's waiting, fleets on their way, and who's marked as an attacker.

## 0.2.34.3 - 2026-11-05

- **Autonomy recommendations always show now** (Nexerelin). They only appeared in a money emergency, so with a big surplus you'd never see one. The Mod features page always has the Autonomy section: colonies losing money with a Grant button, autonomous ones with Take back.
- **Autonomy is Off, Recommend or Auto.** A colony at least a year old that's lost money 3 months in a row, or the best pick in a money emergency, gets logged on Recommend or made autonomous on Auto (one every 30 days, and not while spending freely unless it's an emergency). It's about money only, never a way around the colony limit.
- **Taking one back** costs Nexerelin's normal unrest, unless an older version of the manager made it autonomous.

## 0.2.34.2 - 2026-11-05

- **Every log entry shows on screen again, as one short line.** 0.2.34.0 took most of them off the screen, which went too far: the point was shorter entries, not fewer. New setting **Log entries on screen** under Interface: Every entry (recommended), Important only, or Off.
- **A few lines are back.** Budget, upkeep and surplus terraforming waits show up in the log again (once each), and so do siege and threat endings, held to once every 10 days per colony like their starts.

## 0.2.34.1 - 2026-11-03

- **Spending freely runs on the pool, not on income.** It used to pause whenever net income fell under half its best month, so one weak month stopped all spending with 60M sitting in the bank. That pause is gone. Spending freely only ends when you drop back under 10M over the line.
- **New upkeep follows the pool too.** It was capped at a fifth of net income a month. Now each month's new upkeep can reach a sixtieth of the surplus (so the pool covers five years of it), shrinking as the pool does.

## 0.2.34.0 - 2026-11-03

- **Wonders and gates** (Ashes of the Domain), while spending freely. The Daedalus Array gets built once your colonies make every good its construction needs. The Bifrost Control Center becomes a plan step at your focus world (research follows it), and after that a Bifrost gate goes up in each of your systems, one a day, once its goods are made too. Setting **Wonders and gates while spending freely**.
- **Research can spend Fullerene Spools** from any colony's Storage while spending freely.
- **Much shorter log.** Things that flip back and forth (sieges, threat, free port, hazard pay, station augments) log at most once every 10 days per colony, the day's trade contracts are one line, and build, core, administrator, terraforming and research lines got cut down. Routine lines also stopped popping up on screen, which 0.2.34.2 undid.

## 0.2.33.0 - 2026-11-03

- **Prices barely count.** While spending freely, build prices count 5% (they still counted a quarter), so anything that adds something gets built.
- **Every colony maxes its defenses.** Its station to the top of its line (up to a star fortress), Heavy Batteries, Planetary Shield, Megaport and Patrol HQ go first in its plan.
- **Faster.** Colonies re-plan every day instead of every 7 days, with up to three upgrades at once (never two on the same building).
- **Terraforming goes for the richest deposits.** Surplus terraforming ranks by how much it gains instead of gain per credit, so it goes for plentiful and ultrarich instead of cheap trace ones. When a rich deposit needs tectonic activity first, it adds that first, and the plan's hazard cleanup leaves it alone until the deposit's in (or 540 days).
- **Ashes of the Domain's goods producers** (Crystalizator, Enrichment Facility, High-tech Industry, Skunkworks or Civilian Heavy Production) get planned even with no special project running.

## 0.2.32.0 - 2026-11-03

- **Spending freely.** Once you're 10M past your surplus line, every Approve and Auto colony spends from the surplus, not just the focus world. Before, only the focus world got anything, and with over 60M past the line every other colony sat at "nothing worth starting".
- **Money counts less, so useful stuff goes up.** Those colonies can go past the monthly budget and per-project cap, prices count a quarter, and credits count a quarter in what a building is worth, so defense, stability, growth and access buildings get built even when they cost more than they earn. New upkeep can reach a fifth of net income a month.
- **Hazard pay wherever it helps,** with no limit, at any colony it speeds up by 5% or more.
- **More terraforming.** The plan's best-value terraforming is paid from the surplus everywhere, and surplus terraforming runs up to three projects a day per colony, planned around the ones already running.
- **New setting, Over 10,000,000 of surplus:** Every colony spends it, or Focus world only. It paused while net income was under half its best since it started (0.2.34.1 took that out).

## 0.2.31.0 - 2026-11-03

- **United Aurora Federation colony items,** valued from what they actually do in UAF's code. The Dimensional Nanoforge (+2 output, better ship quality, and it clears pollution instead of causing it) goes to habitable worlds' forges ahead of the polluting nanoforges. The Servosync Pump (+2 fuel), Modular Purifier (+2 metals and transplutonics) and Garrison Transmitter (a port before ground defenses) are placed by value too.
- **Three are never installed.** The Rice Cooker adds heavy machinery and transplutonics demand, the Dimensional Stove only helps a bakery, and the Access Router does nothing in 0.98a.

## 0.2.30.0 - 2026-11-03

- **Daily checks run daily again.** A bunch of things meant to refresh every day (surplus terraforming plans, research ranking, the focus world, siege and crisis checks, TASC lists, trade contract shortfalls) only got worked out once per loaded save, because of how the game answers "days since the start". They refresh every day now.
- **Surplus terraforming stopped undoing its own work.** TME projects toggle a condition: the same project removes it if it's there and adds it if it isn't. An old plan kept starting "remove" projects on worlds that had just been cleaned, which put pollution (Gargor) and high gravity (Hiverne, Zendia) right back, over and over, about 7.3M worth. Now it re-plans whenever conditions change and never starts a project that goes the wrong way.
- **One set of rules for both kinds of terraforming.** The plan's terraforming and surplus terraforming agree on what to keep and what to clear, projects are only charged once they start, and pollution isn't removed while a nanoforge forge is still upgrading (it would come right back).
- **Special project supply** (Vaults of Knowledge). Goods the running black site project needs that none of your colonies make get their producer built as a first plan step at the best colony, along with producers for its own inputs, and research follows. Whatever it can't cover gets logged. Setting **Special project supply**: Auto, Warn only, or Off.
- **Smaller fixes.** Plans use the colony's own goal everywhere, Paradise plans only add Farming with farmland, Forge plans skip the Terrestrial Engine without a TME world, and a research-blocked plan step keeps its value (it counted for nothing every other day). The Research section lists the techs that take colony items and where those items come from.

## 0.2.29.0 - 2026-11-03

- **AI cores in research buildings are valued by what they really do.** In Vaults of Knowledge's Research Facility and Black Site, a core doesn't speed up research: it cuts the building's research, maintenance and cover-up costs (an Alpha by half, a Beta by a quarter, a Gamma by a tenth), and an Alpha on a pre-collapse facility world adds a databank a month. Black Sites take cores now; they never got one before.
- **Alpha cores go there first.** New setting **Alpha cores in research buildings**: First (default) or By value. On First, Alphas go into research buildings before anywhere else, even with **Alpha cores** on Only where removable, and a colony at its inspection limit sends its least useful other core back to storage to make room.
- **Surplus research.** With credits over your surplus line, more Research Facilities go up at Auto colonies, up to the 8 that Vaults of Knowledge counts toward research speed, while the research left is worth more than a quarter of their upkeep. They come back down once there's nothing left to research. Setting **Surplus research**.

## 0.2.28.0 - 2026-11-03

- **Station augments are valued by what they do on each station.** Before, every augment had one combat rating from Station Augments, the same on every station, so Macro Thrusters and Support Outfit could get picked, Command Center's command points counted in battle, and fighter augments went on gun stations. Now it reads each station's own ships (fighter wings, guns, point defense, shields, drones) and its AI core, and values each augment as a share of that station's power. The colony panel shows the station's loadout.
- **Better picks.** Bubble Shield and the SotF weapon platforms are the core defensive picks. AI Fighter Uplink and Fighter Temporal Cores go on stations as much as they rely on fighters, and with the uplink known, an AI core in a carrier station counts what it gives the fighters. Midline stations get Axial Overclocking and Deep Shield Dissipators, and Warmind Refit is never used while the Dustkeepers are hostile.
- **Never picked:** Macro Thrusters (the AI flies the station right into the enemy), Backline Deployment, Safety Shunt and Support Outfit. K-Type Shield Shunt and Deep Shield Dissipators never go on together.
- **Combat loadout before the fight.** When a raid or blockade is on its way, the combat loadout goes on before the station's engaged, instead of everything freezing for the siege. Nothing changes mid-battle.

## 0.2.27.0 - 2026-11-02

- **Stations from other mods count like vanilla's.** Interstellar Imperium's stations (and Knights of Ludd's, or AotD's Star Citadel if you've got one) count by their station tags now. Before, an Imperium star fortress counted as no station at all in the defense estimate behind Hegemony inspections and AI core risk.
- **AI cores in them are station cores.** A core in one counts for the station's fighting strength and gets the defensive priority under threat, instead of being valued as "+1 output".
- **Plans and station lines.** The plan's station step counts any station, and **Station line** has a new Imperium choice when Interstellar Imperium is installed. Station Augments already worked on these stations; its augment points come from the same tags.

## 0.2.26.0 - 2026-11-02

- **Surplus terraforming.** Whatever the focus world leaves of your surplus now goes into Terraforming Made Easy projects at every Auto colony, most value per credit first: richer deposits and farmland, harsh conditions cleared (a planet with none left turns habitable), building the structure first when the colony has none. Before, terraforming only ran on structures a colony already had and had to pay back in 3 years, so 15M of surplus just sat there.
- **Planned on TME's own rules,** one or two projects ahead, so it never trades a habitable world's farmland and organics for richer ore, and never undoes what the colony's plan needs.
- **Structures come back down.** One it builds is taken down once its work is done (TME's changes stay), so you only pay its upkeep while it's working.
- **New setting, Surplus terraforming,** under Budget and spending: Max output (default, anything worth 1,000 a month), Pays back in 10 years, or Off. Paid from the surplus only, outside the monthly budget. With Vaults of Knowledge, structures research still locks aren't built yet, but research values their techs by what they'd allow.

## 0.2.25.0 - 2026-11-02

- **Auto research doesn't sit idle anymore.** It used to stop at the first tech that didn't pay for itself within a year. When your colonies had no room for what the remaining techs unlock, research just sat there for months with nothing in the log. Techs that pay still go first, but after them it starts the best of the rest, with the same databank hold for plan techs and the same upkeep room.
- **It tells you why when nothing can start.** Once: too few databanks (how many you have, and the cheapest tech), AI cores missing from Autopilot Storage, colony items, or this month's upkeep room. A start that fails gets logged too.

## 0.2.24.0 - 2026-11-02

- **The new upkeep cap follows your income.** **New upkeep cap per month** is Automatic by default now: 10% of last month's net income, at least 20,000, so it keeps up as you grow. A fixed amount set once at setup went stale fast (20,000 against 760,000 a month of income in one save). Older saves switch to Automatic once, with a log line, and Fixed is still one click away in Settings.
- **What the cap is for:** a monthly brake on permanent upkeep, so a wrong estimate or a drop in income gets caught before a lot of fixed cost piles up. The Colonies page shows this month's new upkeep against it, and research and the Coronal Hypershunt use the same cap.

## 0.2.23.0 - 2026-11-02

- **Auto Colonies only does colony stuff.** The fix for Industrial Evolution's unnamed Income report rows (sorting Command, Income could crash) isn't part of it anymore. Fixes for other mods live in their own mod now.
- The Diagnostics page's Mjolniar's mods section only lists the ones you have installed.

## 0.2.22.0 - 2026-11-02

- **The colony screen button stays off the refit and fleet screens.** It only looks for its spot on the colonies tab and the docked colony screen now, so it never lands where Auto Outfitting has its button, and those screens aren't searched four times a second anymore. With a full building grid it tries every free spot along the rows and beside the buildings.
- **Surplus investment keeps back what's promised.** Credits already owed to custom production (the game's own, and Ashes of the Domain's orders) don't count as surplus.
- **Diagnostics for my other mods.** The Diagnostics page gets a Mjolniar's mods section with each one's version, warnings and errors this session, and status line.
- **Stuck colonies explain themselves.** Once a month, a colony with nothing worth starting writes its best options, and why they don't pay, to starsector.log.
- **Less work in the background.** An old cleanup for a retired condition runs once per load instead of going over every colony every 5 seconds.

## 0.2.21.0 - 2026-11-02

- **Surplus investment.** Credits over a line (3M recommended; older saves get it switched on once) go into one focus world. It can spend past the monthly budget and the per-project cap, add upkeep up to 10% of net income, counts prices at a quarter, pays for its best terraforming from the surplus, and research counts its plan twice. The manager picks the Auto colony with the most to build on, or you pick it from the colony panel.
- **Big plan steps stop waiting forever.** A plan step whose upkeep alone is over the new upkeep cap now goes as the month's first new upkeep. Before, it just waited (Altea's Geomorphology Station waited 7 months).
- **One held-back build doesn't stall a colony.** Each colony offers its best 3 new builds and 2 upgrades, so when the best has to wait, the next one goes.
- **Better items swap in.** A better colony item replaces a lesser one (a Pristine Nanoforge for a Corrupted one), and an item with nowhere to go makes a building that would take it worth what the item adds.
- **Research can spend AI cores** from Autopilot Storage on Auto, and the panel says when it's waiting on research you queued yourself.

## 0.2.20.1 - 2026-11-01

- **The story point monthly cap counts story points.** It counted improvements, and each improvement at a colony costs twice the last one there, so a cap of 4 spent 2 + 4 + 8 + 16 = 30 story points at one colony. Now it counts the points, and the cheapest improvement anywhere goes first.

## 0.2.20.0 - 2026-11-01

- **The Orbital Fusion Lamp only goes where it helps.** It cancels Cold, Very Cold, Poor Light and Dark, but it eats 10 volatiles and heats up any other world (Hot, or Extreme Heat on one that's already hot). It used to go in anywhere, and two hot desert worlds ended up at Extreme Heat. Now it's only placed where what it cancels beats the heat it adds, or where the colony's plan wants Extreme Heat. A lamp that only adds heat goes back to Autopilot Storage.
- **Old saves get the new hazard pay limit.** Saves still on the old 10% limit move to 30% once (the recommendation since 0.2.17.0). And when one colony's hazard pay costs more than the whole hazard pay budget, the log says so instead of blaming other colonies.

## 0.2.19.0 - 2026-11-01

- **Growth techs count.** Hazmat Equipment and Aquatic Agriculture are valued by the growth their hazard cut gives your colonies that aren't max size yet.
- **Research has a backup.** After 90 days holding databanks for a plan tech it can't afford yet, the best tech you can afford goes ahead anyway. Research upkeep also counts the AI cores in your facilities (an Alpha halves it, a Beta takes a quarter off, a Gamma a tenth).
- **Locked plan steps step aside.** A plan step research blocks for now gives up its spot at the top of the plan. And while the free industry slots are all the plan's industries have left, a new industry that isn't in the plan counts 30,000 a month less.
- **Plan terraforming can go past the caps.** Terraforming projects that are plan steps, the world's final project included, go past the per-project cap and the monthly terraforming budget, paid from credits above your reserve.

## 0.2.18.1 - 2026-11-01

- **Fallbacks for any mix of mods.** Plan steps for a building or condition your game doesn't have (its mod isn't installed) get dropped, so they never hold a plan's top spot. Plain plans get neutral names (Forge, Fortress, Paradise) instead of TME world names.
- **A first Research Facility when research is what's missing.** Vaults of Knowledge's Research Facility has a value now: half of what your colony plans are waiting on in research when you have none, nothing for a second one. So Auto builds one when research is the holdup.

## 0.2.18.0 - 2026-11-01

- **Research works toward whole chains.** It used to rank techs by what they unlock right now plus half of one step on, so a tech two or more steps from a plan step looked worthless, and Auto stopped at the first tech worth nothing. Now every open plan step that research locks is traced back through the whole chain of techs it needs, and the step's value is shared along the chain.
- **Databanks go to the plan first.** While the best plan tech is short of databanks, the ones you have only go to techs a plan needs, and the log says what's next and how many databanks it takes. Databanks in colony Storage count and get used. Plan techs aren't held back by the new upkeep cap, and one over the cap doesn't block cheaper ones anymore.
- **Special-world plans only when they fit.** A TME world is only picked if its industries plus the colony's others fit the slots it'll have at max size, and a year of its end conditions' value counts. TME structures and the Unification Center get the plan's priority even though they cost more than they earn (before, they never got built or researched). A colony with no reachable world keeps no world steps, only hazard cleanup.

## 0.2.17.0 - 2026-11-01

- **Hazard pay goes where it buys the most growth.** It used to switch on only when it cost under 25% of that colony's own net income, so new colonies (which gain the most) never got it, and in practice no colony had it. Now colonies are ranked by how much faster it makes them grow, and the best get it while the speed-up is at least **Hazard pay where growth is at least** (25%) and all of it fits in **Hazard pay limit, all colonies** (30% of net income recommended). The more you earn, the more colonies get it. Every goal wants growth, and the log gives the months to the next size with and without it.
- **Autonomy is your call.** The manager never grants it anymore (the old Auto setting just recommends now). Colonies an older version made autonomous get taken back as soon as there's room, without Nexerelin's unrest.

## 0.2.16.1 - 2026-11-01

- **Autonomous colonies come back as soon as there's room.** A colony the manager made autonomous waited for stability 7, so Nexerelin's 2 unrest from taking it back couldn't drop it under 5, and a small colony might never get there (Gargor sat at 6 with room to spare). Now it's taken back once there's no money emergency, taking it back wouldn't start one, and there's room under your colony limit or an idle administrator, who's put in charge of it right away.
- The log tells colonies past the limit apart from autonomous ones waiting to come back.

## 0.2.16.0 - 2026-11-01

- **The colony limit gets an administrator, never autonomy.** Founding a colony past your limit made it autonomous right away: a new colony always starts under stability 5, and that counted as a colony limit emergency. On top of that, the administrator check valued the limit's stability as money, so a 2,500 administrator looked like a loss. Now going past the limit puts an idle administrator in charge or hires one, whatever the money says, past the monthly budget if it has to but never under your reserve. The log says when nobody's for hire.
- **Emergency autonomy is for money emergencies only,** and never picks a colony under a year old. Taking one back costs 2 stability.
- **Idle agents explain themselves.** The Agents section says why an agent the manager runs is idle: its Defense work is the same as Nexerelin's Internal security mode, run daily within budget.

## 0.2.15.0 - 2026-10-31

- **Station augments swap with the threat.** While a colony plans for Security (high threat, or that's its goal), combat augments count ten times as much, so they take the augment points from Hydroponics Basins and the other output augments. When the threat drops, the output ones come back. Before, combat ratings were too small to beat output even under Security.
- **It swaps the day the threat changes,** not at the weekly turn, so the combat loadout's on before a siege blocks refits. The log and the colony panel say which way it swapped.

## 0.2.14.1 - 2026-10-31

- **The colony screen button stays with the colony screen.** It was put on the screen once and never moved, so it stayed put when the buildings changed or you switched colonies, and could even show up over other screens. Now it sits in the panel right above the buildings, its spot gets worked out again four times a second and the button follows, hidden screens are skipped, and it's found from the colonies and docked screens too.

## 0.2.14.0 - 2026-10-31

- **Colony crises are priced into everything.** Crisis progress costs about 640 a month per point now. An AI core pays for the Hegemony progress and Pather interest it adds (size 3 and under is free from the Hegemony, and 7 Pather interest at size 4+ means a Pather cell), a Patrol HQ, Military Base or High Command counts how much it slows crises, and Fuel Production counts the Sindrian Diktat progress it adds.
- **It gets ready for what's coming.** The colony the next crisis is likely to hit plans for Security until it's over, a colony a Pather attack is coming for sheds AI cores until its cell can dissolve, and a coming Nexerelin police raid closes free ports.
- **New setting, Hegemony inspection orders:** Advise (logs each inspection coming for an Auto colony with its strength and your defenses, so you can resist or bribe), Resist where defended, or Off. The game's default is to comply, which hands over every core there.
- **Never inspected is the recommended Inspection risk now,** and there's a new choice, Small colonies only, that keeps cores out of colonies above size 3.
- **Colony crises page** in the empire panel: the meter, the coming crisis and its likely target, your military multiplier, and each faction's monthly progress with what drives it.

## 0.2.13.1 - 2026-10-31

- **Attack settings only for a Derelict Empire start.** The Attacks on your colonies settings only show up and apply in a Nexerelin Derelict Empire (or Derelict Empire V2) start now, the one that hands every faction blockade points on day 0. There, the recommended settings turn them on (at most 4 pirate and crisis fleets per system, Nexerelin attacks 90 days apart). Any other game keeps the game's own values.

## 0.2.13.0 - 2026-10-31

- **New settings group, Attacks on your colonies.** Both are off unless you pick them, and never part of the recommended settings. (They were removed in 0.2.37.0.)
- **Pirate and crisis fleets per system.** The game keeps up to 11 in a system with your colonies; pick 8, 6, 4 or 2. The game's own value is kept and put back, and a lower value from another mod is never raised.
- **Nexerelin invasions, raids and blockades.** One at a time, 30 to 180 days apart, by holding Nexerelin's own attack cooldown on your faction while any attack on your colonies is on its way or under way. That covers IX Revival's blockades too, since they come through Nexerelin.

## 0.2.12.0 - 2026-10-31

- **Sieges.** A colony that's blockaded, has hostile fleets at the planet, has hostiles in its system that outweigh its defenses, or has a raid, invasion or blockade headed its way is under siege. Until it's over, it only gets defense builds (none during a fast buildout), and its items, AI cores, administrators, policies, station augments and TASC projects are left alone.
- **Trade contracts skip goods that can't ship.** Theory of Toolbox only delivers from colonies with a spaceport and accessibility above 0, and a blockade had Auto taking contracts that missed every delivery. Contracts don't count the surplus of colonies that can't deliver or are under siege anymore.

## 0.2.11.0 - 2026-10-31

- **Research moves to the Autopilot Storage page** (Vaults of Knowledge). Starting a tech (Auto or the Start button) moves whatever the Research Facility is short of from Autopilot Storage (databanks, an Alpha core, colony items), then databanks from your fleet's cargo. Autopilot Storage takes research databanks now.
- **Storage cheat** (new Cheats group, off unless you turn it on, never in the recommended settings). Autopilot Storage takes anything but ships, and your colonies cover shortages from it for free, like the game's own stockpiles. It was removed in 0.2.37.0.

## 0.2.10.0 - 2026-10-31

- **AI cores by value.** Cores get placed, upgraded, moved and sent back to storage by what they add in each building (output, upkeep, shortages, structure bonuses, weighted by the colony's goal), against Hegemony inspection risk. New setting **Inspection risk**: More where defended (default), Never inspected, or No limit. There's no limit at all once you've beaten the inspections, while you're in the Persean League, or when the Hegemony has no colonies, and a colony an inspection is coming for is left as it is.
- **Administrators by cost.** They're hired, placed and moved only where Industrial Planning's output and the colony limit's stability are worth more than their salary and hiring cost. Each colony's Overview shows the salary against what the administrator adds.
- **Emergency autonomy** (Nexerelin): new setting, Off, Recommend or Auto. In a money or colony limit emergency, the colony whose autonomy saves the most is made autonomous, and it's taken back once the emergency's over.
- **One Mod features page** in both panels instead of a page per mod.
- **Pather cells and mod buildings.** Mod buildings stopped being measured once a colony had Pather cells. This fix didn't actually hold in game; 0.2.39.0 fixed it for real.

## 0.2.9.0 - 2026-10-31

- **One page at a time.** The empire and colony panels were one long scroll with every mod's section in it. Now there's a page list down the left. Empire panel: Colonies, Autopilot Storage, one page per mod feature you use, Settings, Log and Diagnostics. Colony panel: Overview, Plan and builds, Locks, and one page per mod feature that applies. Settings groups open one at a time, the first-time setup page included.
- **The Agents page shows every agent you have** (Nexerelin), not just the ones the manager hired, with type (Negotiator, Saboteur, Hybrid), level and current job. **Let Autopilot use it** hands one over, **Take it back** gets it back. And it won't hire another agent while one of yours that could do the job sits idle.

## 0.2.8.0 - 2026-10-30

- **Colony Autopilot button on the colony screen.** It's right on your colony's own screen (Command tab or docked), in a spot that doesn't cover anything, with a setting to turn it off. The empire panel got its own **Colony Autopilot** filter in Intel too (it used to sit under "Colony threats"), and both panels got a cleanup.
- **Recommended settings that work if you only switch colonies to Auto.** New Automatic budget (recommended): half of last month's net income plus 15% of your credits above the reserve, so a young colony that loses money still gets built and spending slows as savings fall. The rest: pause on loss off, a reserve of a quarter of your credits (50k-500k), AI cores and administrator hiring on, research, trade contracts and the RAT settlement on Auto, agents on Defense.
- **Upkeep was predicted 500 times too high.** Every building's upkeep got multiplied by 500 a second time, so everything scored hugely negative (a Waystation at about -700k a month) and Auto built whatever had the least upkeep, which meant Research Facilities that did nothing. Fixed, and the bad upkeep corrections older saves learned from it are cleared once.
- **Only builds that pay for themselves.** Approve, Auto and fast buildout only start something that pays back within about 3 years, never the least-bad option. The colony panel shows money a month after upkeep and a "Ranked" line with how each pick's score is made up. Nexerelin outposts are left alone completely.
- **Fast buildout ranks by money only.** A threatened colony on fast buildout planned for Security and spent its buildout on Ground Defenses, Heavy Batteries and a Battlestation. Now the buildout ignores goal and threat and never builds defenses.
- **Also:** Ashes of the Domain research can use databanks from your fleet's cargo, and sorting Command, Income with Industrial Evolution doesn't crash anymore.

## 0.2.7.0 - 2026-10-30

- **Fast buildout for new colonies.** After you found your first colony, a popup offers to build it up fast: it goes to Auto, planned for Money, and starts the fastest-paying builds every day with everything above a credit floor you pick (250,000 by default), outside the monthly budget, until it's made money for 30 days in a row. The colony panel can stop it, change the floor, or start one for any colony that's losing money.
- **Nexerelin agents** (off by default). On Defense, agents run Nexerelin's own counter-insurgency, Pather cell and pirate base operations at the colonies that need them, then raise relations with factions running operations against you while you're not at war. Defense and offense adds sabotage of military industries, only against factions already hostile to you (never allies, ceasefires or the faction you're commissioned with). It hires level-1 agents (12,000 each) up to **Agents to keep**, only while there's work, and only gives orders to agents it hired. The empire panel lists your agents and enemy operations against your colonies.
- **Balance changes moved out.** Battle size and sticky combat autopilot went to a separate mod, so this one doesn't change any game settings anymore.

## 0.2.6.0 - 2026-10-30

- **First downloadable release.** Extract the zip into `Starsector/mods/`.
- **Ashes of the Domain and the other reflected integrations work in game now.** The game's script loader refuses mod code that uses Java's reflection classes, so every one of them failed in a real game (nothing got built under AotD at all). They go through a route the loader allows now, and the build checks for it so it can't come back. This fix was first built as 0.2.5.1, which only ever ran on my own game.
- **Staying working through mod updates.** A health check at load for each integration (OK, newer version, partly off, or off, shown on the Diagnostics page), mod values read live where a mod exposes them, a weekly calibration of estimates against your real buildings, and a breaker that pauses an action for the session after it fails three times in a row.
- **The Getting started guide,** with a first-colony walkthrough.

## 0.2.5.0 - 2026-10-30

- **Colony mods from the Mod Index.** Support for Terraforming & Station Construction, Grand.Colonies, Substance.Abuse, Volkov Industrial Conglomerate, Roider Union, Planetary Shield: Access Control, Unknown Skies, What We Left Behind and Astral Ascension.
- **What they get.** TASC's terraforming projects are valued by the conditions they leave behind, and its unsafe buildings are never test-built. Grand.Colonies' extra upkeep past its building threshold is counted on every build. A Brewery only goes up when you have a recipe for it. A colony with viral contamination gets Volkov's anti-EVC treatment first. Roider Dives and Union HQ count the deposits they mine in range, the Access Control shield counts as ground defense and less Pather interest, and with Astral Ascension, hazard pay never pushes a planetless colony past size 10, where it would crash.
- **Any mod's deposits and colony items.** Deposits a mod adds to the game's own tables score like vanilla ones, and any mod's colony item can be placed through the game's item effect table.
- **No builds past 12 buildings** without Grand.Colonies, since the colony screen can't show a 13th.

## 0.2.4.1 - 2026-10-30

- **New name.** The tweaks mod became a colony manager with its own name. Same mod id, so saves keep working.

## 0.2.4.0 - 2026-10-30

- **Research** (Ashes of the Domain: Vaults of Knowledge). Each tech you can start is valued by what it unlocks for your colonies, scored by the same engine that picks builds. On Auto it starts techs that only need databanks, moving databanks from Autopilot Storage when the facility is short. Your own queue is never touched.
- **Management styles** (Vaults of Knowledge's Colony Development). VoK only lets you pick one when founding a colony, so the manager never changes it, but it scores buildings with your style's real effects.
- **Megastructures.** A Coronal Hypershunt colony gets nothing built or placed (AotD doesn't allow it), its wormhole range is kept at the smallest that reaches the receivers serving your colonies, restorations start within the budget, and the empire panel lists where receivers would pay off. Pluto's laser runs at full strength with the ore going to the forges, and Nidavelir's Nexus Core gets manpower while its inputs are supplied. Setting **Megastructures (Hypershunt, Pluto, Nidavelir)**.
- **AotD colony items** (Tenebrian Nanoforge, Synchrotron, Atmospheric Driver, Catalytic Core, Turing Engine) are placed from Autopilot Storage like vanilla ones.
- **Hidden buildings are never touched** (megastructure defenses, the hypershunt's port). Left to you: Bifrost gates, Grand Wonders, black site projects and custom production.

## 0.2.3.0 - 2026-10-30

- **Industry synergies** (Ashes of the Domain: Vaults of Knowledge). A build's value includes the synergies it starts, ends or strengthens on that colony, like Ore-To-Core (Refining with Mining) or Rapid Transportation (Spaceport with Waystation), 19 in all. Only synergies researched at that colony count, and the colony panel shows the active ones and those one building away.
- **Trade contracts** (Theory of Toolbox). Offers are listed with their pay and what they gain over just exporting. One is only taken when Theory of Toolbox's own figures leave 15% of production to spare and it pays more than the exports would have: through an Accept button on Advisor, or up to 3 a day on Auto. Goods your contracts are short of count as shortages, so colonies that make them score higher.

## 0.2.2.0 - 2026-10-30

- **Station Augments support.** Installing augments is free and can be undone, so the manager picks the set of known augments worth the most within each station's augment points, weighted by the colony's goal. Auto colonies apply it, the others get an Apply button. Setting **Station augments**: Follow colony mode, Recommend only, or Off.
- **Your picks stay yours.** Augments you installed are never changed, and one the manager added that you remove isn't put back.

## 0.2.1.1 - 2026-10-30

- **No more crash in the colony panel.** A stray percent sign in the threat line's text crashed the game when a colony on Approve or Auto was planning for Security.

## 0.2.1.0 - 2026-10-30

- **First version on GitHub:** Colony Autopilot for Starsector 0.98a.
- **Modes and goals.** Each colony runs Off, Advisor, Approve or Auto (Advisor by default, so nothing gets built until you pick Approve or Auto), with a goal: Balanced, Money, Security, War production, Rapid growth or Self-sufficiency. Plans lay out a target for each planet from its hazards and resources, and Auto builds within a monthly budget, a per-project cap and a credit reserve.
- **Items, cores, administrators, threat.** Colony items and AI cores you put in Autopilot Storage get placed, idle administrators get assigned, and a colony switches to Security once its threat reaches 70% (and back below 65% after 60 days).
- **Mod buildings it doesn't know** get measured on a throwaway probe colony and scored like vanilla ones. Diagnostics rates every building Supported, Measured, Analyzed or Quarantined.
- **Optional support** for Terraforming Made Easy (special-world plans, terraforming), Industrial Evolution (ambassadors, edicts, VPC chips, courier shipping), Nexerelin (emergency defense fleets), Emergent Threats (Antimatter Stabilizer conversion), Ashes of the Domain (research locks, upgrade paths, starter buildings) and Random Assortment of Things (founding and running a Frontiers settlement).
