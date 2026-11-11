# How it builds

## Quick version

- Each colony has a mode and a goal.
- Options are scored by monthly earnings less costs. Approve and Auto start only options scoring above 0.
- All colonies share one ranked queue.
- A building Autopilot takes down is not built again at that colony for a year.
- **Recommended builds** on the colony panel shows each score. Hover a pick for its breakdown.

## Modes

- **Off**: left alone.
- **Advisor** (default): shows what it would build and why. You click to build.
- **Approve**: proposes one build at a time. A rejected one is not proposed again at that colony for 30 days.
- **Auto**: builds within your budget. Items, cores, administrators, policies and terraforming run only on Auto colonies.

## Goals

- **Balanced**: a bit of everything.
- **Money**: income, accessibility.
- **Security**: ground defense, fleets, stability.
- **War production**: ship quality, fleet size.
- **Rapid growth**: population growth, accessibility.
- **Self-sufficiency**: what your colonies are short of.

A colony in danger is planned as Security until calm ([War and security](war-and-security.md)).

## Plans

Each colony gets a target layout from its planet and goal. Barren with ore and transplutonics: Forge. Habitable: Fortress on Security, Paradise on Money or Growth. Special worlds need Terraforming Made Easy ([Terraforming](terraforming.md)).

- **Slots.** An off-plan industry must be worth more than the plan industry it would push out.
- **Blocked steps.** The best available build goes instead. A step blocked for 60 days, or worth nothing for a month, gives up its slot until that changes.
- **Stations.** After fast buildout: a station at size 4 (or half a year old), Battlestation from 6, Star Fortress from 8; a size sooner at the [capital](capital.md). Threatened and border colonies (a hostile market within 8 light-years), the capital, and Security or War production goals build it first. Others build what earns first.
- **Station line.** The **Stations** row: Pick by score, Low tech, Midline, High tech, Imperium, or Only where they pay.

## Stability

The game decivilizes a colony that stays at 0.

- Anything that can cost stability must leave the colony at or above minimum stability (default 5, at least 3).
- At 2 or less, a colony over its industry limit sheds an industry right away.
- After 20 days at 0, the building costing the most stability is taken down, then one every 10 days until off 0. A monthly on-screen alert is shown.

## How it picks

Monthly value: money after upkeep plus stability, accessibility, defense, fleets, ship quality and growth, weighted by the goal. Enabler steps (such as stations) get a plan bonus. Other plan steps add half of what they lead to. Price, fee and construction time are spread over the payback horizon.

- **Payback horizon.** 36 months, rising to 120 as surplus credits reach 10,000,000. From the money tier Idle money up, a credit counts half ([Money](money.md#money-tiers)).
- **Exports valued by market share** (Settings, Advanced, Building; can be turned off). Exports earn your share of each good's sector market: a good you barely make is worth the most, more of one you dominate very little. Your colonies' shortages count extra where the new supply reaches. Buildings under construction count as made.

## The one queue

Buildings, upgrades, surplus terraforming, extra Research Facilities, the wonder site, stable-location crews and story point improvements go through one planner.

- **Construction.** One new building at a time per colony, picked by value per day of construction. Upgrades run beside it, several at once when budget and upkeep room allow, one per building.
- **Money** goes to the best value per credit across all colonies.
- **Saving up.** Other picks spend only what keeps the top pick on schedule for the month its money comes in. Its colony holds an industry slot and stability room. Nothing is held for a pick over six months of budget away.
- **Timing.** A colony is checked when something there changes, otherwise weekly.
- **Other spending.** Plan terraforming and paid item, core and administrator moves check the planner's savings first.
- **Limit.** 12 buildings per colony (the colony screen cannot show a 13th).

## Taking buildings down

Taken-down buildings, and what they were upgraded from, are not rebuilt there for a year.

- **Dead weight.** With **Take down buildings** on Clean up (recommended), an industry worth only its output, and worth less than its upkeep for 30 days, is taken down, one per colony every 30 days. Locked buildings, plan steps, military buildings and anything adding stability stay.
- **Other removals.** Only where a feature needs them (terraforming structures it built, endgame replacements), or on Free hand (Allow removals).
- **Kept.** The unpaid part of a build's price counts against removing it. A producer stays while its replacement is built.

## Mod buildings it doesn't know

Mod buildings with no formula are measured on a throwaway test colony. Measurements last the session and are retaken after three to four months.

- **Diagnostics** rates each building **Supported**, **Measured**, **Analyzed** or **Quarantined** (its own code failed; left alone). Vanilla buildings are not quarantined.
- **Measured mod buildings** (Settings, Advanced, Extras): Build when best, Recommend only, or Ignore.

## Staying in control

- **Locks.** A building locked on the **Locks** page is not upgraded, replaced, removed, or touched for items and cores.
- **Story points** are not spent while **Story points** is Yours only. Each improvement at a colony costs twice the last.
- **Fees.** Approve and Auto add a convenience fee: Off, Low, Standard (5%, at least 5,000) or High (Settings, Advanced, Budget).
- **Old engine.** If the planner fails 3 days in a row, the 0.2.45 engine runs for the rest of the session ([details.md](details.md)).

## Stable locations

In systems with your Auto colonies, a crew builds the best of these at a stable spot, one of each per system:

- **Comm relay**: +1 stability at your colonies in the system. Usually first.
- **Nav buoy**: +2 burn for your fleets. Security colonies weight it higher.
- **Sensor array**: +400 sensor range.

Cost: 15 heavy machinery, 30 metals and 5 transplutonics at base price, plus a fee. It goes up 10 days after the crew leaves. **Stable locations** (Settings, Advanced, Extras): Recommend only or Off.

Next: [Your capital](capital.md).
