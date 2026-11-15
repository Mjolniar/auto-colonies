# Story mode

## Quick version

- Story mode: Optional. Off by default. The Colony Autopilot stays dormant until you find the Autarch AI Core, then runs the abilities you buy with its bandwidth.
- Setting: LunaLib's settings menu (F3), Auto Colonies, first row. Without LunaLib: the first row of the Autopilot panel's Settings page.
- New games: A pop-up asks Normal or Story mode once. Older saves never see it.
- Switching: Works in any save, at any time. Each save keeps its story progress.

## Story mode on

- Start: The Autopilot is dormant. Nothing at your colonies runs on its own. No build-menu hints, and the monthly ledger line goes to starsector.log only. The Autopilot panel shows only Story and More.
- The core: The Autarch AI Core is placed once per save in a derelict research station in an uninhabited fringe system. After your first colony, your TriPad names the constellation the station is in.
- Intel: One entry, Colony Autopilot, in the Story tab. While you search for the core, its map marker is on the constellation's label and it is marked important. Its text names the system's star and where the station orbits among the planets. It does not name the system.
- Finding it: Your TriPad asks what to do with the core. Install it, install it in an isolation cradle (1 story point, can be pulled out later), have Engineering break it down, or decide later.
- Break down: 365 days, then 3 Alpha and 2 Beta cores.
- Selling: A kept core can be sold at any market (base price 1,000,000).
- Bandwidth: The core gives 20 bandwidth. Each AI core you feed it on the Story page adds bandwidth: Alpha 10, Beta 3, Gamma 1. Fed cores are used up.
- Abilities: 52 abilities in 4 tiers, each with a bandwidth cost, switched on and off on the Story page of the Autopilot panel. Costs are in `data/config/colony_autopilot_story.json`.
- Tiers: Tier I is open from the start. The next tier opens when you own 2/3 of the abilities in the tier before it ("tierShare" in the config file), counting only abilities whose mods you have. An open tier stays open. The Story page lists open tiers and what opens the next one.
- Panel: Story (open first), Colony (Overview, Plan and builds, Locks and the colony's mod features on one page), Settings, and More (Colonies, Autopilot Storage, Colony crises, Expansion, War, Mod features, Factions, Log, Diagnostics, picked from a row of buttons).
- Modes: Every colony mode is bought. With nothing bought every colony is Off. Advisor mode (1) gives Advisor; Approve mode (2) gives one colony in Approve or Auto, plus one more for each 2 bandwidth; Auto mode (4) needs Approve mode. A colony past what is bought waits in the next mode down, and its mode comes back when the ability is bought.
- Settings: A setting an ability that's off covers is held at off. Its row says which ability it needs. Your value comes back when the ability is on.
- Keep cores for feeding: On by default. Autopilot leaves enough cores in Autopilot Storage for your next ability.
- Story perks: Instant construction (15 bandwidth), Free construction (25 bandwidth), Two builds at once (6 bandwidth) and Three builds at once (8 bandwidth, needs Two) exist only in story mode. They are in Tier IV.
- Builds at once: Each colony builds 2 or 3 new buildings at the same time. Without the perks it builds one, as in the game. Upgrades always run beside them.

## Switching

- Off: Every feature works as with story mode never on. Every held setting and colony mode comes back. The quest and the core's timers pause.
- On again: The quest resumes where it stopped. Timers continue without the days spent Off.
- Kept: The core's location, bandwidth, fed cores, abilities, bought colonies and the story beats seen.
- Not refunded: bandwidth spent and cores fed.
- LunaLib: The setting is global, like every Auto Colonies setting. Each save follows it when loaded and when LunaLib's menu is saved.
- LunaLib values: A held setting keeps your value in LunaLib, so other saves never get a held value.
- Off with a placed station: The derelict station stays in the sector. Salvaging it while Off gives the core item.

## Costs

| Tier | Abilities (bandwidth) |
|---|---|
| Tier I | Advisor mode 1, Approve mode 2, Clean up 2, Stations 2, Colony policies 1, Capital 1, Storage logistics 1, Stable locations 1, Crisis response 2, Colony items 2, Stockpiles 1, Low profile 1 |
| Tier II | Auto mode 4, AI cores 2, Administrators 2, Story point improvements 3, Higher spending 2, Production floors 2, Enforcer patrols 3, Emergency defense 3, Agents: defense 2, Diplomacy 2, Autonomy 1, Terraforming 3, Research 3, Trade contracts 1, Industrial Evolution 2, Station augments 2, Settlement 2 |
| Tier III | Fast buildout 2, Omega and IX cores 3, Surplus investment 4, Military 3, Shipyard 3, Half fees 4, Auto-expansion 6, Colony fleets 2, War: Retaliate 3, Agents: offense 3, Surplus terraforming 4, Surplus research 3, Megastructures 4 |
| Tier IV | Endgame economy 5, No fees 8, War: Hold them down 5, War: Conquer 8, War: Scorched 5, Wonders and gates 5, Instant construction 15, Free construction 25, Two builds at once 6, Three builds at once 8 |

- Totals: Every vanilla ability except the story perks is 70 bandwidth. Every ability with every supported mod is 176 bandwidth.
- Mods: An ability for a mod you don't have doesn't show.
