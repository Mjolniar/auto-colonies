# Story mode

## Quick version

- Story mode is off by default. The Colony Autopilot is dormant until you find the Autarch AI Core, and then it runs the abilities you buy with its bandwidth.
- The setting is the first row of Auto Colonies in LunaLib (F3) or the Autopilot panel's Settings page, and every save follows it. A new game asks once whether you want Story or Normal, with Story picked by default.
- You can switch at any time, and each save keeps its story progress. With story mode off, everything works like it was never on, and the quest and the core's timers pause. Spent bandwidth and fed cores aren't refunded.

## The core

- There's one per save, in a derelict research station in an uninhabited fringe system. After your first colony, your TriPad names the constellation, and the Colony Autopilot intel entry names the star and where the station orbits.
- You can install it, install it in an isolation cradle (1 story point, removable later), have Engineering break it down (365 days, for 3 Alpha and 2 Beta cores), sell it (base price 1,000,000) or decide later.
- It gives 10 bandwidth, and each AI core you feed it on the Story page adds more (Alpha 10, Beta 3, Gamma 1). Fed cores are used up.
- Once you've fed 100 bandwidth, each core gives 75% of that, and from 250 it gives 50%. What you already fed stays.
- Mods that hand out a lot of AI cores (Treasure Hunt, Seeker, Random Assortment of Things, Secrets of the Frontier, Knights of Ludd, Slightly Better Tech-Mining and Omega Core Everywhere) make packages, colonies and seats cost 10% more each, up to 50% more. Packages you already own keep the price you paid.
- Each tier opens once you own 2/3 of the packages in the tier before it, not counting packages for mods you don't have, and an open tier stays open.
- Each new tier, and buying every Tier IV package, brings a TriPad scene that the Story page can replay.

## Colony modes

- Every colony mode has to be bought, so with nothing bought every colony is Off. Approve mode gives you one colony in Approve or Auto.
- The first colony you add on the Story page costs 2 bandwidth, and each one after that costs 3 more than the one before it (2, 5, 8 and so on), up to 30 managed colonies. Colonies you already bought keep the price you paid.
- Once you have more managed colonies than your colony limit plus your administrators (2 and 2 at the start), each extra one raises your colony limit by one. So 5 managed colonies let you hold 5 colonies without the over-limit penalty, however many seats the Autarch administrator has.
- A colony past what you've bought waits in the next mode down, and a setting covered by an ability that's off is held at off.

## Autarch administrator

- The core governs one managed colony per seat. The first seat costs 10 bandwidth, and each one after that costs 5 more than the one before it (10, 15, 20 and so on), up to 30 and never more than you have managed colonies.
- It has Industrial Planning, Hypercognition and Autarchy (+1 industry limit, -20% upkeep, +1 stability), and the Hegemony and the Pathers don't count it.
- It takes the colonies it adds the most to first. It leaves a colony that's no longer managed, the one it adds least to when it has more colonies than seats, and every colony when the package or story mode is off.
- It leaves one colony a day, and it never takes or leaves a colony if that drops it under the stability floor.
- A hired administrator it replaces goes unassigned, and an AI core administrator you placed stays. If you place an administrator or govern the colony yourself, it stays away until the colony leaves management.
- If you take it off a colony by mistake, the colony panel's Seat the Autarch AI Core button puts it back with Autarchy, while it has a free seat and the colony stays at or above the floor.

## Costs

| Tier | Package (bandwidth): abilities |
|---|---|
| Tier I | Modes (12): Advisor, Approve and Auto mode. Colony upkeep (5, needs Modes): Clean up, Stations, Colony policies, Crisis response, Low profile. Supply and items (5, needs Modes): Capital, Storage logistics, Stable locations, Colony items, Stockpiles |
| Tier II | Cores and staff (6): AI cores, Administrators, Story point improvements. Economy (5): Higher spending, Production floors, Trade contracts. Security (6): Enforcer patrols, Emergency defense, Agents: defense, Diplomacy, Autonomy. Mod projects (6): Terraforming, Research, Industrial Evolution, Station augments, Settlement, Ruin restoration |
| Tier III | Growth (9, needs Modes): Fast buildout, Surplus investment, Half fees. Industry (8): Military, Shipyard, Omega and IX cores. Expansion (8): Auto-expansion, Colony fleets. War (7, needs Security): War: Retaliate, Agents: offense. Large projects (8, needs Mod projects): Surplus terraforming, Surplus research, Megastructures. Historian hire (10, needs Cores and staff and United Aurora Federation): hires Sophia once you're Friendly with the UAF |
| Tier IV | Endgame (15, needs Growth): Endgame economy, No fees, Wonders and gates. Conquest (15, needs War): War: Hold them down, War: Conquer, War: Scorched. Parallel construction (12): Two and Three builds at once. Rapid construction (20): a third of the build time, 110,000 more per build. Rapid terraforming (12): half the terraforming time. Rapid research (20, needs Vaults of Knowledge): research in half the time, with about 2/3 of the databanks. Rapid repairs (30): disrupted buildings at your colonies recover in half the time. Station outfitting (25, needs Auto Outfitting 0.5.71 or later): Auto Outfitting fits your star fortresses and tier 4 stations with the weapons you know, and you pay for the weapons it adds. Autarch administrator (10 per colony, needs Cores and staff): the core governs managed colonies. Historian core (20, needs Autarch administrator and United Aurora Federation): one Autarch AI Core variant with Galactic Historian governs a colony with ruins |

- With every supported mod, Tier I comes to 22 bandwidth, Tier II 23, Tier III 50 and Tier IV 179 with one Autarch colony. Abilities for mods you don't have don't show up.
- Parallel construction, the Rapid packages, Station outfitting, the Autarch administrator and the two Historian packages are story mode only. Upgrades always run alongside new buildings.
- Rapid terraforming works at managed colonies, including on projects you start, and it stacks with an AI core's time cut.
- With Rapid research, each project needs about 2/3 of its databanks (rounded, at least one), while AI cores and colony items cost the same. It ends as soon as you lose the package or pull the mod.
