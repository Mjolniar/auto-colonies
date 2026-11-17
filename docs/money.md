# Money, surplus and the endgame

## The budget

- You set the budget with Spending and with Advanced, Budget in Settings ([preset values](settings.md#main-rows)).

- Automatic (recommended) spends a share of colony net income and of credits above the reserve, and unspent budget carries over for six months.
- Emergency defense fleets and colony-limit administrators can go over budget, down to the reserve.

## Money tiers

- Free cash is your credits minus the reserve, unpaid custom production and 2 months of upkeep and fleet costs.
- N is colony net income, taken as a 3-month median.

| Tier | Starts at (free cash) | Ladder spends |
|---|---|---|
| Tight | below Surplus | 0% |
| Surplus | max(1,000,000, 2 x N) | 20% |
| Idle money | max(3,000,000, 5 x N), or Surplus + 2 idle months | 35% |
| Rich | max(10,000,000, 15 x N), or Idle money + 4 idle months | 60% |
| Endgame | Rich, with N at the Endgame line | 80% |

- The credit value changes with the tier, and from Idle money a credit counts half in build value.
- At Surplus, an upgrade's price counts 25% in its score, then 10% from Idle money up. Industry and station upgrades can start beside a new building that's under construction.

## Money taper

- A build's money counts in full up to 300,000 a month of colony net and contract income, then 60% up to 1,000,000 and 35% above that. A loss runs the same curve going down.
- The options (Settings, Advanced, Production) are Off, Gentle (recommended) and Strong (200,000 and 600,000, 50% and 25%). Money colonies, fast buildout and the endgame don't use it.
- The tier's credit value multiplies on top of the taper.

## The money ladder

- Each month, the tier's share of free cash gets split over the rungs in What to buy order, and any unused share passes on.
- Each rung (Settings, Advanced, Surplus and money ladder) can be On, Recommend only or Off. Shipyard and Expansion default to Recommend only.
- Before the rungs, it pays for 0-stability fixes, emergencies, colony-limit administrators, plan steps and ordered production.
- The shares are Stockpiles 10%, Growth 20%, Military 20%, Shipyard 25%, [Expansion](expansion.md) 15%, Economy 10% and Wonders the rest.
- Military and Expansion open at Idle money and Wonders at Rich. Shipyard opens at Surplus with Production on, or at Idle money with it Off.
- If you've got no production capacity, the Shipyard note names the Heavy Industry that's planned for the hull capacity floor.
- Shipyard orders known-blueprint hulls from your Doctrine priority list as custom production, plus a 2% fee. It does one a month, up to 6 unclaimed, and they're delivered to the [capital](capital.md).
- Military does one upgrade a day at Auto colonies and builds stations a size early, with new upkeep kept within 15% of colony net. It also pays for non-emergency defense fleets, once per system every 45 days.

### Economy

- Surplus research, surplus terraforming and costly capital trade buildings need this rung On ([Research](research.md#surplus-research), [Terraforming](terraforming.md#surplus-terraforming), [Your capital](capital.md#trade-buildings)).

### Wonders

- The Daedalus Array, Bifrost gates and endgame industry swaps need this rung On, and they stay within the rung's allowance.

## Surplus investment: the focus world

- Credits over the Surplus investment line (recommended 3,000,000) go to the Auto colony with the most to build, and that's the focus world.

## Spending freely

- It starts at Rich, once surplus credits reach half the line (1,500,000), and it stays on at Rich or Endgame as long as you're above 600,000.
- While it's on, every Approve and Auto colony spends from the surplus and a credit counts a quarter in build value. It can run up to three upgrades at once.

## The endgame economy

- It runs from the Endgame tier while you're spending freely, until it ends or N falls below 0.
- The Endgame row can be 1,000,000 a month (recommended), 2,000,000, 500,000 or Off.
- With its slots full, an Auto colony swaps its weakest industry for one worth 10,000 a month more. That's one a day empire-wide and one a month per colony.
- It won't replace military, plan, project, research, wonder, story-point improved or locked buildings, the last producer of a needed good, or anything under a year old or in a trade contract.

## Autonomy (Nexerelin)

- The setting (Settings, Advanced, People and diplomacy) can be Off, Recommend (recommended) or Auto.
- An autonomous colony pays you a quarter of its profit or loss, and it sits outside the colony limit.
- It's used for colonies a year old or more with three straight losing months, or in a money emergency, and only one per 30 days at most.
- If you reject an offer, that colony isn't offered again for a year, and its Recommend line stays quiet that year too.
