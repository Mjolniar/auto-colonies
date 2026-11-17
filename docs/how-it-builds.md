# How it builds

## Quick version

- Every option gets scored by monthly earnings minus costs, and Approve and Auto only start ones above 0.
- All your colonies share one ranked queue, and Recommended builds on the colony panel shows each score.

## Modes

- Off leaves the colony alone.
- Advisor (default) puts a ranking beside the game's build menu, with Best, Good and Poor and a reason for each. The colony panel lists every option with a Build button, but you do the building.
- Approve proposes one build at a time, and it waits in the approval summary and the colony panel. If you reject it, it's held for 180 days, and that carries through loads.
- Auto builds within your budget. Items, cores, administrators, policies and terraforming only run on Auto colonies.
- Empire-wide settings follow a mode of their own, the Empire mode ([Settings](settings.md#mode-defaults)).
- Approvals and the colony panel's Build button pay the price plus the convenience fee, and it comes out of credits above the reserve.

## Approval summary

- It's one dialog with every waiting decision, grouped by colony. You can pick Approve all, Choose each, Reject all or Later.
- It comes up every 60 days by default (Settings, Advanced, Interface), and you can set it from 15 to 180 days or Off.
- It only opens when there's no dialog, menu, screen or battle up. Between summaries, decisions wait in the colony panel.
- It covers builds and make room, the RAT settlement, colony expeditions, the money ladder's proposals, hypershunt, megastructures, TASC projects, station augments and autonomy.

## Build menu hints

- The hints show up beside the game's build menu and Building Menu Overhaul's. On 0.98.5a, there's also one line in each row's tooltip.
- By default they're on Advisor colonies (Settings, Advanced, Interface), and the options are Advisor colonies, Every colony not Off, and Off.
- Advisor colonies don't get messages or pop-ups.

## Goals

- The goals are Balanced, Money, Security, War production, Rapid growth and Self-sufficiency (it goes after what your colonies are short of).

## Plans

- Each colony gets a target layout from its planet and goal. Special worlds need Terraforming Made Easy ([Terraforming](terraforming.md)).
- An off-plan industry has to be worth more than the plan industry it'd push out. If a step's blocked for 60 days, or it's worth nothing for a month, it gives up its slot.
- After fast buildout, a colony gets a station at size 4, a Battlestation from 6 and a Star Fortress from 8. The [capital](capital.md) gets each one a size sooner.
- Threatened and border colonies (a hostile market within 8 light-years), the capital, and colonies on the Security or War production goals get their station first.

## Stability

- Anything that can cost stability has to leave the colony at or above minimum stability (default 5, and it can't go lower than 3).
- If a colony sits at 0 for 20 days, it takes down the building costing the most stability, then one every 10 days until the colony's off 0.

## How it picks

- An option's monthly value is the money after upkeep plus stability, accessibility, defense, fleets, ship quality and growth, all weighted by the goal.
- Money counts in full up to 300,000 a month of income, then 60% up to 1,000,000 and 35% above that ([Money](money.md#money-taper)).
- For diversity, the 3rd and later copy of a building family counts 75% of its money per extra copy.
- The payback horizon is 36 months, and it rises to 120 as surplus credits reach 10,000,000.

## Production floors

- The Production setting (Settings, Advanced, Production) has Off, Light (recommended) and Full.
- The floors are hull source, hull capacity (custom production), AoTD order goods, production quality, weapons, Domain-grade Parts, supplies, fuel and producer inputs.
- Light asks for 1 hull source, 100,000 capacity a month, quality 0.8 (about 1 d-mod a hull) and no supplies or fuel shortage. Full asks for 2 hull sources, 300,000, quality 1.0 and supplies and fuel for your fleet.
- An open floor adds value for the part of the gap a build closes. Floor builds rank first, and they keep their industry slot.
- An open floor's producer (Heavy Industry, Orbital Works, Fuel Production, Refining, High-tech Industry) becomes a plan step at the best colony, and a colony with a free slot goes first.
- If there's no free slot, the planned colony proposes to make room by replacing its weakest income-only industry (Ask first).
- The Budget panel shows each floor, whether it's met or how far off it is, and the producers that are planned.

## The one queue

- The queue holds buildings, upgrades, surplus terraforming, extra Research Facilities, the wonder site, stable-location crews and story point improvements.
- Each colony builds one new building at a time (2 or 3 with the [story perks](story-mode.md)), picked by value per day of construction. Upgrades run beside it, and the limit is 12 buildings per colony.
- Money goes to the best value per credit. Other picks only spend what keeps the top pick on schedule, unless it's over six months of budget away.

## Taking buildings down

- Once a building's taken down, it isn't rebuilt there for a year, and neither is what it was upgraded from.
- With Take down buildings on Clean up (recommended), an industry that's worth only its output and less than its upkeep for 30 days gets taken down, one per colony every 30 days. Locked, plan, military and stability buildings stay.

## Mod buildings

- Mod buildings with no formula get measured on a test colony, and Diagnostics rates them Supported, Measured, Analyzed or Quarantined. Quarantined ones are left alone.
- The Measured mod buildings setting (Settings, Advanced, Extras) can be Build when best, Recommend only or Ignore.

## Staying in control

- Fees (Settings, Advanced, Budget) can be Off, Low, Standard (5%, at least 5,000) or High. The fees are there to balance the fact that it's all automatic.

## Stable locations

- In systems with your Auto colonies, a crew builds one of each per system: a Comm relay (+1 stability), a Nav buoy (+2 burn) and a Sensor array (+400 sensor range).
- You can also set Stable locations (Settings, Advanced, Extras) to Recommend only or Off.
