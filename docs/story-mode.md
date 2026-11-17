# Story mode

## Quick version

- Story mode is optional and off by default. The Colony Autopilot stays dormant until you find the Autarch AI Core, and then it runs whatever abilities you buy with its bandwidth.
- The setting's in LunaLib's settings menu (F3), under Auto Colonies, in the first row. Without LunaLib, it's the first row of the Autopilot panel's Settings page.
- When you start a new game, a pop-up asks once whether you want Story or Normal mode, with Story first and picked by default. Older saves never see it.
- You can switch in any save at any time, and each save keeps its story progress.

## Story mode on

- It starts with the Autopilot dormant and your colonies running as they do in the base game. There are no build-menu hints, the monthly ledger line only goes to starsector.log and the Autopilot panel only shows Story and More.
- The Autarch AI Core gets placed once per save, in a derelict research station in an uninhabited fringe system. After your first colony, your TriPad tells you which constellation it's in.
- There's one intel entry, Colony Autopilot, in the Story tab. While you're looking for the core, its map marker sits on the constellation's label and it's marked important.
- Its text names the system's star and where the station orbits among the planets, but it doesn't name the system.
- When you find it, your TriPad asks what to do with the core. You can install it, install it in an isolation cradle (1 story point, removable later), have Engineering break it down or decide later.
- Breaking it down takes 365 days, and then you get 3 Alpha and 2 Beta cores.
- If you keep the core, you can sell it at any market (base price 1,000,000).
- The core gives 10 bandwidth, and each AI core you feed it on the Story page adds more (Alpha 10, Beta 3, Gamma 1). Fed cores are used up.
- There are 20 packages in 4 tiers, and each one has a bandwidth cost and a set of abilities. You switch them on and off on the Story page of the Autopilot panel, and the costs are in `data/config/colony_autopilot_story.json` ("packs").
- Tier I's open from the start, and the next tier opens once you own 2/3 of the packages in the tier before it ("tierShare" in the config file).
- Packages for mods you don't have don't count toward that. An open tier stays open, and the Story page lists the open tiers and what opens the next one.
- The panel has Story (open first), Colony and More. More's pages (Colonies, Autopilot Storage, Colony crises, Expansion, War, Mod features, Factions, Log, Diagnostics, and Settings in story mode) are picked from a row of buttons.
- The Colony page shows the core's status for the colony, the waiting approval, the next plan step, the top build and the Goal and Mode buttons. Colony numbers, Money and capital, Plan and builds, Locks and Mod features open when you click them.
- Every colony mode has to be bought, so with nothing bought every colony is Off. Advisor mode (2) gives you Advisor, and Approve mode (4) gives you one colony in Approve or Auto, plus one more for each 2 bandwidth.
- Auto mode (6) needs Approve mode. A colony past what you've bought waits in the next mode down, and its mode comes back once you buy the ability.
- If a setting's covered by an ability that's off, it's held at off and its row says which ability it needs. Your value comes back once the ability's on.
- The story perks are all Tier IV and story mode only. Rapid construction (20 bandwidth) makes builds and upgrades take a third of the time, for 100,000 credits more each.
- The others are two or three builds at once (Parallel construction, 12 bandwidth), Rapid terraforming (12 bandwidth), Rapid research (20 bandwidth, needs Vaults of Knowledge) and the Autarch administrator (10 bandwidth per colony).
- The Autarch administrator package (10) covers one colony. Each extra colony costs 10 more ("autarchSeat" in the config file) on the Story page, up to however many managed colonies you have.
- The core governs managed colonies (Approve or Auto), starting with the ones it adds the most to, as long as the colony stays at or over the stability floor.
- If it replaces a hired administrator, that administrator goes unassigned, and an AI core you placed as administrator stays where it is.
- The Autarch administrator has Industrial Planning and Hypercognition (an Alpha Core administrator's skills) and Autarchy (+1 industry limit, -20% upkeep, +1 stability). There's no AI core to take back, and the Hegemony and the Pathers don't count it.
- It leaves a colony that's no longer managed, and the one it adds least to when it holds more colonies than you bought. It leaves every colony when the package or story mode is off or the core's dormant.
- It leaves one colony a day, and never when that would take a colony under the stability floor (industry limit counted).
- If you place an administrator there or govern it yourself, that replaces it, and it stays away from that colony until the colony leaves management.
- With Rapid terraforming, Terraforming Made Easy's terraforming structure and Terraforming and Station Construction's projects take half the time at a managed colony. That covers projects you start too, and an AI core's time cut stacks with it.
- With Rapid research, Vaults of Knowledge research only takes 2/3 of its usual time. The items each project needs don't change, and it never goes into your save, so it's gone as soon as you lose the package or pull the mod.
- Each tier that opens brings a TriPad scene with your technology officer and chief engineer, and so does buying every Tier IV package. A save already past a tier gets its scene, and the Story page can replay each one.
- With Two or Three builds at once, each colony builds 2 or 3 new buildings at the same time. Without them it's one, like the base game, and upgrades always run alongside.

## Switching

- With story mode off, everything works like it was never on, and every held setting and colony mode comes back. The quest and the core's timers pause.
- When you turn it back on, the quest picks up where it stopped, and the timers don't count the days spent Off.
- It keeps the core's location, bandwidth, fed cores, abilities, bought colonies and the story beats you've seen.
- Bandwidth you spent and cores you fed aren't refunded.
- In LunaLib the setting's global, like every Auto Colonies setting, so each save follows it when it's loaded and when you save LunaLib's menu.
- A held setting keeps your value in LunaLib, so other saves never pick up a held value.
- If the station's already placed when you turn it off, the derelict station stays in the sector, and salvaging it while Off still gives you the core item.

## Costs

| Tier | Package (bandwidth): abilities |
|---|---|
| Tier I | Advisor mode (2): Advisor mode. Approve mode (4): Approve mode. Colony upkeep (5): Clean up, Stations, Colony policies, Crisis response, Low profile. Supply and items (5): Capital, Storage logistics, Stable locations, Colony items, Stockpiles |
| Tier II | Auto mode (6, needs Approve mode). Cores and staff (6): AI cores, Administrators, Story point improvements. Economy (5): Higher spending, Production floors, Trade contracts. Security (6): Enforcer patrols, Emergency defense, Agents: defense, Diplomacy, Autonomy. Mod projects (6): Terraforming, Research, Industrial Evolution, Station augments, Settlement |
| Tier III | Growth (9, needs Auto mode): Fast buildout, Surplus investment, Half fees. Industry (8): Military, Shipyard, Omega and IX cores. Expansion (8): Auto-expansion, Colony fleets. War (7, needs Security): War: Retaliate, Agents: offense. Large projects (8, needs Mod projects): Surplus terraforming, Surplus research, Megastructures |
| Tier IV | Endgame (15, needs Growth): Endgame economy, No fees, Wonders and gates. Conquest (15, needs War): War: Hold them down, War: Conquer, War: Scorched. Parallel construction (12): Two and Three builds at once. Rapid construction (20): a third of the build time, 100,000 more per build. Rapid terraforming (12): half the terraforming time. Rapid research (20, needs Vaults of Knowledge): research in 2/3 of the time. Autarch administrator (10 per colony, needs Cores and staff): the core governs managed colonies |

- With every supported mod, Tier I comes to 16 bandwidth, Tier II 29, Tier III 40 and Tier IV 104 with one Autarch colony.
- Abilities for mods you don't have don't show up.
