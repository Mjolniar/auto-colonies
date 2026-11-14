# Story mode

## Quick version

- Story mode: Optional. Off by default. The Colony Autopilot stays dormant until you find the Autarch AI Core, then runs the abilities you buy with processing power (PP).
- Setting: LunaLib's settings menu (F3), Auto Colonies, first row. Without LunaLib: the first row of the intel Settings page.
- New games: A pop-up asks Normal or Story mode once. Older saves never see it.
- Switching: Works in any save, at any time. Each save keeps its story progress.

## Story mode on

- Start: The Autopilot is dormant. Nothing at your colonies runs on its own. The Story page and Diagnostics are the only intel pages.
- The core: The Autarch AI Core is placed once per save in a derelict research station in an uninhabited fringe system. After your first colony, your TriPad names the system.
- Finding it: Your TriPad asks what to do with the core. Install it, install it in an isolation cradle (1 story point, can be pulled out later), have Engineering break it down, or decide later.
- Break down: 365 days, then 3 Alpha and 2 Beta cores.
- Selling: A kept core can be sold at any market (base price 1,000,000).
- Processing power: The core gives 20 PP. Each AI core you feed it on the Story page adds PP: Alpha 10, Beta 3, Gamma 1. Fed cores are used up.
- Abilities: 48 abilities, each with a PP cost, switched on and off on the Story page. Costs are in `data/config/colony_autopilot_story.json`.
- Managed colonies: One colony in Approve or Auto, plus one more for each 2 PP. Others wait in Advisor.
- Settings: A setting an ability that's off covers is held at off. Its row says which ability it needs. Your value comes back when the ability is on.
- Keep cores for feeding: On by default. Autopilot leaves enough cores in Autopilot Storage for your next ability.
- Story perks: Instant construction (15 PP) and Free construction (25 PP) exist only in story mode.

## Switching

- Off: Every feature works as with story mode never on. Every held setting and colony mode comes back. The quest and the core's timers pause.
- On again: The quest resumes where it stopped. Timers continue without the days spent Off.
- Kept: The core's location, PP, fed cores, abilities, bought colonies and the story beats seen.
- Not refunded: PP spent and cores fed.
- LunaLib: The setting is global, like every Auto Colonies setting. Each save follows it when loaded and when LunaLib's menu is saved.
- LunaLib values: A held setting keeps your value in LunaLib, so other saves never get a held value.
- Off with a placed station: The derelict station stays in the sector. Salvaging it while Off gives the core item.

## Costs

| Group | Abilities (PP) |
|---|---|
| Colony management | Auto mode 4, Fast buildout 2, Clean up 2, Stations 2, Colony policies 1, Story point improvements 3, Capital 1, Storage logistics 1, Stable locations 1, Crisis response 2 |
| Items and people | Colony items 2, AI cores 2, Administrators 2, Omega and IX cores 3 |
| Money | Higher spending 2, Surplus investment 4, Production floors 2, Stockpiles 1, Military 3, Shipyard 3, Endgame economy 5, Half fees 4, No fees 8 |
| Expansion | Auto-expansion 6, Colony fleets 2 |
| Security and war | Low profile 1, Enforcer patrols 3, Emergency defense 3, Agents: defense 2, Agents: offense 3, Diplomacy 2, Autonomy 1, War: Retaliate 3, War: Hold them down 5, War: Conquer 8, War: Scorched 5 |
| Mod features | Terraforming 3, Surplus terraforming 4, Research 3, Surplus research 3, Megastructures 4, Wonders and gates 5, Trade contracts 1, Industrial Evolution 2, Station augments 2, Settlement 2 |
| Story perks | Instant construction 15, Free construction 25 |

- Totals: Every vanilla ability except the story perks is 67 PP. Every ability with every supported mod is 173 PP.
- Mods: An ability for a mod you don't have doesn't show.
