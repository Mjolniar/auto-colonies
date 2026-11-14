# Money, surplus and the endgame

## The budget

- Settings: Spending and Advanced, Budget ([preset values](settings.md#main-rows)).

- Automatic (recommended): Spends a share of colony net income and of credits above the reserve. Unspent budget carries over six months.
- Over budget: Emergency defense fleets and colony-limit administrators, down to the reserve.

## Money tiers

- Free cash: Credits less the reserve, unpaid custom production, and 2 months of upkeep and fleet costs.
- N: Colony net income, a 3-month median.

| Tier | Starts at (free cash) | Ladder spends |
|---|---|---|
| Tight | below Surplus | 0% |
| Surplus | max(1,000,000, 2 x N) | 20% |
| Idle money | max(3,000,000, 5 x N), or Surplus + 2 idle months | 35% |
| Rich | max(10,000,000, 15 x N), or Idle money + 4 idle months | 60% |
| Endgame | Rich, with N at the Endgame line | 80% |

- Credit value: From Idle money, a credit counts half in build value.

## Money taper

- Rule: A build's money counts in full up to 300,000 a month of colony net and contract income, 60% up to 1,000,000 and 35% above. A loss runs the same curve down.
- Options (Settings, Advanced, Production): Off, Gentle (recommended), Strong (200,000 and 600,000, 50% and 25%). Money colonies, fast buildout and the endgame use no taper.
- Stacking: The tier's credit value multiplies on top.

## The money ladder

- Split: Each month the tier's share of free cash is split over the rungs in What to buy order. Unused shares pass on.
- Rung modes (Settings, Advanced, Surplus and money ladder): On, Recommend only, Off. Shipyard and Expansion default to Recommend only.
- Paid first: 0-stability fixes, emergencies, colony-limit administrators, plan steps, ordered production.
- Shares: Stockpiles 10%, Growth 20%, Military 20%, Shipyard 25%, [Expansion](expansion.md) 15%, Economy 10%, Wonders the rest. Military and Expansion open at Idle money, Wonders at Rich. Shipyard opens at Surplus with Production on, Idle money with it Off.
- Shipyard capacity: With no production capacity, the Shipyard note names the Heavy Industry planned for the hull capacity floor.
- Shipyard: Known-blueprint hulls from your Doctrine priority list, as custom production plus a 2% fee. One a month, up to 6 unclaimed. Delivered to the [capital](capital.md).
- Military: One upgrade a day at Auto colonies, stations a size early. New upkeep within 15% of colony net. Non-emergency defense fleets, once per system every 45 days.

### Economy

- Needs the rung On: Surplus research, surplus terraforming, costly capital trade buildings ([Research](research.md#surplus-research), [Terraforming](terraforming.md#surplus-terraforming), [Your capital](capital.md#trade-buildings)).

### Wonders

- Needs the rung On: The Daedalus Array, Bifrost gates and endgame industry swaps, within the rung's allowance.

## Surplus investment: the focus world

- Line: Credits over the Surplus investment line (recommended 3,000,000) go to the Auto colony with the most to build.

## Spending freely

- Start: At Rich, when surplus credits reach half the line (1,500,000). Stays on at Rich or Endgame above 600,000.
- Scope: Every Approve and Auto colony spends from the surplus. A credit counts a quarter in build value. Up to three upgrades at once.

## The endgame economy

- Runs: From the Endgame tier while spending freely, until it ends or N falls below 0.
- Endgame row: 1,000,000 a month (recommended), 2,000,000, 500,000 or Off.
- Replacing industries: With slots full, an Auto colony swaps its weakest industry for one worth 10,000 a month more. One a day empire-wide, one a month per colony.
- Kept: Military, plan, project, research, wonder, story-point improved and locked buildings, the last producer of a needed good, anything under a year old or in a trade contract.

## Autonomy (Nexerelin)

- Setting (Settings, Advanced, People and diplomacy): Off, Recommend (recommended), Auto.
- Effect: An autonomous colony pays you a quarter of its profit or loss and is outside the colony limit.
- Used for: Colonies a year old or more with three straight losing months, or in a money emergency. One per 30 days at most.
- Rejected offer: That colony is not offered again for a year. Its Recommend line stays quiet for that year.
