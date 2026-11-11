# Money, surplus and the endgame

## Quick version

- The **Automatic** budget (recommended) grows with income.
- From the Surplus tier up, the **money ladder** spends part of free cash.
- Surplus credits go to one **focus world**, and at Rich to every Approve and Auto colony.
- The credit reserve is not spent.

## The budget

Settings, **Spending** and Advanced, **Budget** ([preset values](settings.md#main-rows)).

- **Automatic** spends a share of colony net income and of credits above the reserve.
- Unspent budget carries over six months, longer while the credits stay above the reserve.
- Projects over the **per-project cap** are proposed to you.
- Emergency defense fleets and administrators past the colony limit can go over budget, down to the reserve.

## Money tiers

- **Free cash:** credits less the reserve, unpaid custom production, and 2 months of colony upkeep and fleet costs.
- **N:** colony net income, a 3-month median without custom production, Ashes of the Domain orders and fleet draw on colony stockpiles.
- **Idle month:** at least half the Approve and Auto colonies had nothing worth starting, and under 25% of the budget was spent.

| Tier | Starts at (free cash) | Ladder spends |
|---|---|---|
| Tight | below Surplus | 0% |
| Surplus | max(1,000,000, 2 x N) | 20% |
| Idle money | max(3,000,000, 5 x N), or Surplus + 2 idle months | 35% |
| Rich | max(10,000,000, 15 x N), or Idle money (by free cash) + 4 idle months | 60% |
| Endgame | Rich, with N at the Endgame line | 80% |

A tier ends when free cash (Endgame: N) falls under 60% of its line. From Idle money, a credit counts half in build value at Approve and Auto colonies.

## The money ladder

- Each month the tier's share of free cash is split in **What to buy** order, each rung up to its share. Unused shares pass on.
- **Rung modes** (Settings, Advanced, Surplus and money ladder): On, Recommend only (proposals on the Budget panel; Reject holds one off 30 days), Off. Shipyard and Expansion default to Recommend only.
- The Expansion rung applies only while **Money for new colonies** is Money ladder ([New colonies](expansion.md#turning-it-on)).
- Paid first, outside the pool: 0-stability fixes, emergencies, colony-limit administrators, plan steps, ordered production.
- Credits saved for the planner's next build are not spent.

| Rung | Share | Opens at |
|---|---|---|
| Stockpiles | 10% | Surplus |
| Growth | 20% | Surplus |
| Military | 20% | Idle money |
| Shipyard | 25% | Idle money |
| [Expansion](expansion.md) | 15% | Idle money |
| Economy | 10% | Surplus |
| Wonders | the rest | Rich |

| What to buy | Order | Rest goes to |
|---|---|---|
| **Balanced** (recommended) | all, in the order above | Wonders |
| Fleet | Shipyard, Military, Growth, Stockpiles, Expansion, Economy | Shipyard |
| Expansion | Expansion, Growth, Military, Stockpiles, Shipyard, Economy | Expansion |
| Economy | Economy, Growth, Stockpiles, Wonders | Wonders |
| Save | Stockpiles, Growth | your account |

### Shipyard

- Orders known-blueprint hulls from your Doctrine priority list as custom production plus a 2% fee. One order a month, fewest on hand first.
- Ships arrive at the [capital](capital.md). Orders stop at **6 unclaimed hulls**.
- If free cash drops under 0 before month end, that month's hulls are cancelled and their fee refunded.

### Stockpiles

At Auto colonies, buys a good in a shortage when the output lost over the months bought is worth more than price, tariff and shipping. Buys at the nearest non-hostile market with stock, shipped as an [Autopilot Storage move](items-and-cores.md#autopilot-storage). Refills under 1 month of cover, up to 3 months. No 10% stockpile charge.

### Military

At Auto colonies, one upgrade a day: a station one tier up a colony size early (5 and 7; capital 4 and 6), Military Base to High Command, Patrol HQ to Military Base when a slot is free and nothing else is worth starting. New upkeep stays within 15% of colony net. Also buys military goods for colonies with a military building or station. Non-emergency defense fleets come from this rung or the budget, once per system every 45 days.

### Growth

- While On, the hazard pay budget rises to the tier's share of colony net (Surplus 40%, Idle money 60%, Rich and up uncapped), and stays at least your setting.
- Below Rich, in any mode, a colony gets hazard pay only if it reaches its next size with it within 24 months (36 if that size adds an industry slot), or 25% longer if already paying.

### Economy

Surplus research, surplus terraforming, and capital trade buildings that cost more than they earn start only with this rung On, and pay under their own rules outside the pool. See [Research](research.md#surplus-research), [Terraforming](terraforming.md#surplus-terraforming), [Your capital](capital.md#trade-buildings).

### Wonders

The Daedalus Array, Bifrost gates and endgame industry swaps need this rung On. A site's price must fit the rung's allowance.

## Surplus investment: the focus world

Credits over the **Surplus investment** line (recommended 3,000,000) go to one **focus world**, the Auto colony with the most to build. It may exceed the budget and per-project cap, add new upkeep up to 10% of net income a month, and pay terraforming from the surplus. **Make this the focus world** sets it. **Let Autopilot choose** hands it back.

## Spending freely

Starts at Rich when surplus credits reach half the line (1,500,000 of 3,000,000). Stays on at Rich or Endgame while they stay above a fifth of the line (600,000). Every Approve and Auto colony spends from the surplus.

- A credit counts a quarter in build value. Colonies are checked daily, up to three upgrades at once.
- Every colony maxes its station, Heavy Batteries, Planetary Shield, Megaport and Patrol HQ.
- More hazard pay, terraforming, Research Facilities and, with Nexerelin, [war fleets](war-and-security.md).

Off: **Over 10,000,000 of surplus** set to Focus world only.

## The endgame economy

Runs from the Endgame tier while spending freely, until spending freely ends or N falls below 0. **Endgame** row: 1,000,000 a month (recommended), 2,000,000, 500,000 or Off.

- A credit counts 5% in the value of a building, upgrade, core or item. Fleets, ship quality and defense count more.
- Net income may fall to a third of its endgame-start level.
- **Replacing industries:** with slots full, an Auto colony swaps its weakest industry for one worth 10,000 a month more. One a day empire-wide, one a month per colony. Kept: military, plan, project, research, wonder, story-point improved and locked buildings; the last producer of a good you need; anything under a year old or in a trade contract. The old core and item go to Autopilot Storage. Off: **Endgame replaces industries**.

## Autonomy (Nexerelin)

**Autonomy** (Settings, Advanced, People and diplomacy): Off, **Recommend** (recommended), Auto. An autonomous colony pays you a quarter of its profit or loss and is outside the colony limit. For colonies a year old or more with three straight losing months, or in a money emergency. Auto: one per 30 days at most. Not for new colonies or the colony limit. Mod features page: **Grant autonomy**, **Take back**.

Next: [New colonies](expansion.md).
