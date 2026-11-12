# How it builds

## Quick version

- Scoring: Options are scored by monthly earnings less costs. Approve and Auto start only options scoring above 0.
- Queue: All colonies share one ranked queue. Recommended builds on the colony panel shows each score.

## Modes

- Off: The colony is left alone.
- Advisor (default): Shows what it would build. You click to build.
- Approve: Proposes one build at a time. A rejected build is not proposed again at that colony for 30 days.
- Auto: Builds within your budget. Items, cores, administrators, policies and terraforming run only on Auto colonies.

## Goals

- Goals: Balanced, Money, Security, War production, Rapid growth, Self-sufficiency (what your colonies are short of).

## Plans

- Target layout: Each colony gets one from its planet and goal. Special worlds need Terraforming Made Easy ([Terraforming](terraforming.md)).
- Slots: An off-plan industry must be worth more than the plan industry it would push out. A step blocked for 60 days, or worth nothing for a month, gives up its slot.
- Stations: After fast buildout, a station at size 4, Battlestation from 6, Star Fortress from 8. The [capital](capital.md) gets each one size sooner. Threatened and border colonies (hostile market within 8 light-years), the capital, and Security or War production goals build the station first.

## Stability

- Minimum: Anything that can cost stability must leave the colony at or above minimum stability (default 5, at least 3).
- After 20 days at 0: The building costing the most stability is taken down, then one every 10 days until the colony is off 0.

## How it picks

- Monthly value: Money after upkeep plus stability, accessibility, defense, fleets, ship quality and growth, weighted by the goal.
- Payback horizon: 36 months, rising to 120 as surplus credits reach 10,000,000.

## The one queue

- Scope: Buildings, upgrades, surplus terraforming, extra Research Facilities, the wonder site, stable-location crews and story point improvements.
- Construction: One new building at a time per colony, by value per day of construction. Upgrades run beside it. Limit: 12 buildings per colony.
- Money: Goes to the best value per credit. Other picks spend only what keeps the top pick on schedule, unless it is over six months of budget away.

## Taking buildings down

- Rebuild block: Taken-down buildings, and what they were upgraded from, are not rebuilt there for a year.
- Clean up (Take down buildings, recommended): An industry worth only its output and less than its upkeep for 30 days is taken down, one per colony every 30 days. Locked, plan, military and stability buildings stay.

## Mod buildings

- Diagnostics: Mod buildings with no formula are measured on a test colony and rated Supported, Measured, Analyzed or Quarantined. Quarantined ones are left alone.
- Measured mod buildings (Settings, Advanced, Extras): Build when best, Recommend only, or Ignore.

## Staying in control

- Fees (Settings, Advanced, Budget): Off, Low, Standard (5%, at least 5,000) or High.

## Stable locations

- Build: In systems with your Auto colonies, a crew builds one of each per system: Comm relay (+1 stability), Nav buoy (+2 burn), Sensor array (+400 sensor range).
- Stable locations (Settings, Advanced, Extras): Recommend only or Off.
