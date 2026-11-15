# Story mode

## Quick version

- Story mode: Optional. Off by default. The Colony Autopilot stays dormant until you find the Autarch AI Core, then runs the abilities you buy with its bandwidth.
- Setting: LunaLib's settings menu (F3), Auto Colonies, first row. Without LunaLib: the first row of the Autopilot panel's Settings page.
- New games: A pop-up asks Story or Normal mode once. Story mode is first and the default. Older saves never see it.
- Switching: Works in any save, at any time. Each save keeps its story progress.

## Story mode on

- Start: The Autopilot is dormant. Nothing at your colonies runs on its own. No build-menu hints, and the monthly ledger line goes to starsector.log only. The Autopilot panel shows only Story and More.
- The core: The Autarch AI Core is placed once per save in a derelict research station in an uninhabited fringe system. After your first colony, your TriPad names the constellation the station is in.
- Intel: One entry, Colony Autopilot, in the Story tab. While you search for the core, its map marker is on the constellation's label and it is marked important. Its text names the system's star and where the station orbits among the planets. It does not name the system.
- Finding it: Your TriPad asks what to do with the core. Install it, install it in an isolation cradle (1 story point, can be pulled out later), have Engineering break it down, or decide later.
- Break down: 365 days, then 3 Alpha and 2 Beta cores.
- Selling: A kept core can be sold at any market (base price 1,000,000).
- Bandwidth: The core gives 10 bandwidth. Each AI core you feed it on the Story page adds bandwidth: Alpha 10, Beta 3, Gamma 1. Fed cores are used up.
- Packages: 18 packages in 4 tiers, each with a bandwidth cost and a set of abilities, switched on and off on the Story page of the Autopilot panel. Costs are in `data/config/colony_autopilot_story.json` ("packs").
- Tiers: Tier I is open from the start. The next tier opens when you own 2/3 of the packages in the tier before it ("tierShare" in the config file), counting only packages one of whose mods you have. An open tier stays open. The Story page lists open tiers and what opens the next one.
- Panel: Story (open first), Colony, and More (Settings is a More page in story mode) (Colonies, Autopilot Storage, Colony crises, Expansion, War, Mod features, Factions, Log, Diagnostics, picked from a row of buttons).
- Colony page: The core's status for the colony, the waiting approval, the next plan step, the top build and the Goal and Mode buttons. Colony numbers, Money and capital, Plan and builds, Locks and Mod features open on a click.
- Modes: Every colony mode is bought. With nothing bought every colony is Off. Advisor mode (2) gives Advisor; Approve mode (4) gives one colony in Approve or Auto, plus one more for each 2 bandwidth; Auto mode (6) needs Approve mode. A colony past what is bought waits in the next mode down, and its mode comes back when the ability is bought.
- Settings: A setting an ability that's off covers is held at off. Its row says which ability it needs. Your value comes back when the ability is on.
- Story perks: Rapid construction (20 bandwidth: builds and upgrades take a third of the time, for 100,000 credits more each) and two or three builds at once (Parallel construction, 12 bandwidth) exist only in story mode. Both packages are in Tier IV.
- Summary: After you change packages, colonies or bandwidth on the Story page, the TriPad lists what runs now, what stopped and the bandwidth once you close the panel.
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

| Tier | Package (bandwidth): abilities |
|---|---|
| Tier I | Advisor mode (2): Advisor mode. Approve mode (4): Approve mode. Colony upkeep (5): Clean up, Stations, Colony policies, Crisis response, Low profile. Supply and items (5): Capital, Storage logistics, Stable locations, Colony items, Stockpiles |
| Tier II | Auto mode (6, needs Approve mode). Cores and staff (6): AI cores, Administrators, Story point improvements. Economy (5): Higher spending, Production floors, Trade contracts. Security (6): Enforcer patrols, Emergency defense, Agents: defense, Diplomacy, Autonomy. Mod projects (6): Terraforming, Research, Industrial Evolution, Station augments, Settlement |
| Tier III | Growth (9, needs Auto mode): Fast buildout, Surplus investment, Half fees. Industry (8): Military, Shipyard, Omega and IX cores. Expansion (8): Auto-expansion, Colony fleets. War (7, needs Security): War: Retaliate, Agents: offense. Large projects (8, needs Mod projects): Surplus terraforming, Surplus research, Megastructures |
| Tier IV | Endgame (15, needs Growth): Endgame economy, No fees, Wonders and gates. Conquest (15, needs War): War: Hold them down, War: Conquer, War: Scorched. Parallel construction (12): Two and Three builds at once. Rapid construction (20): a third of the build time, 100,000 more per build |

- Totals: Tier I is 16 bandwidth, Tier II 29, Tier III 40, Tier IV 62, with every supported mod.
- Mods: An ability for a mod you don't have doesn't show.
