# How it builds

## Quick version

- Each colony has a mode (Off, Advisor, Approve, Auto) and a goal.
- Every option is scored by what it makes a month minus its price spread over three years. Only things that pay back in about that time get built on Approve or Auto.
- **Recommended builds** on the colony panel shows every option and why it ranks where it does.
- Locks, the per-project cap and the Story points setting limit what it can do.

## Modes

- **Off**: left alone.
- **Advisor** (default): shows what it would build and why. You click to build.
- **Approve**: proposes one thing at a time and waits for you.
- **Auto**: builds within your budget. Items, cores, administrators, policies and terraforming also only run on Auto colonies.

## Goals

- **Balanced**: a bit of everything.
- **Money**: income and accessibility.
- **Security**: ground defense, fleets, stability.
- **War production**: ship quality and fleet size.
- **Rapid growth**: population growth and accessibility.
- **Self-sufficiency**: making what your colonies are short of.

A colony in danger is planned as Security until things calm down (see [War and security](war-and-security.md)).

## Plans

Each colony gets a target layout from its planet and goal. A barren world with ore and transplutonics becomes a Forge. A habitable world becomes a Fortress on Security, or a Paradise on Money or Growth. With Terraforming Made Easy a plan can aim for a special world ([Terraforming](terraforming.md)).

- **Stations.** After fast buildout, a colony plans a station at size 4 (or half a year old), a Battlestation from size 6 and a Star Fortress from size 8. That's a size sooner at your [capital](capital.md). They cost more than they earn but anything attacking has to beat one first. At calm colonies the station waits until after the first economy step.
- **Slots.** It builds only as many industries as the colony has slots for. A colony that ends up over takes down its weakest, since being over costs 5 stability.
- **Stability.** The game decivilizes a colony that stays at 0, so it keeps colonies off 0. At 2 or less, a colony over its limit sheds an industry right away. After 20 days at 0 it takes down the building costing the most stability, one every 10 days until it's off 0. You get a monthly on-screen alert.
- **Blocked steps.** If the next step is blocked, the colony builds the best thing available. A step with no way to it for 60 days stops holding a slot until a way opens.

## How it picks

Each option scores:

- what it does a month: money after upkeep plus stability, accessibility, defense, fleets, ship quality and growth, weighted by the goal;
- a bonus if it's a plan step;
- minus its price spread over three years.

Upgrades get no free bonus for leading somewhere.

**Exports are valued by market share.** The game pays exports as your share of each good's sector market, so a good you barely make is worth the most, and more of something you already dominate is worth very little. Shortages your own colonies have count extra, which spreads colonies across different goods. Turn it off under Settings, Building and planning, **Exports valued by market share**. Diagnostics has a market section.

**When it looks.** Each colony every 7 days, or right after something finishes. The best value per credit goes first. A colony starts at most one new building and one upgrade at a time.

**What it avoids:**

- Putting a polluting nanoforge in a forge on a habitable world.
- Building past 12 buildings (the colony screen can't show a 13th).
- Taking a slot the plan still needs.
- Rebuilding what it took down. Anything it removes at a colony stays off that colony's list for a year.

## Mod buildings it doesn't know

A mod building with no formula is measured on a throwaway test colony, then scored like anything else. **Diagnostics** rates every building: **Supported**, **Measured**, **Analyzed** or **Quarantined** (misbehaved, left alone). **Mod buildings with measured effects** decides whether Auto builds Measured ones, only proposes them, or ignores them.

## Staying in control

- **Locks.** The **Locks** page lists your buildings. A locked one is never upgraded, replaced, removed, or touched for items and cores.
- **Removals.** It only removes buildings when a feature needs to (terraforming structures it built, endgame replacements) or you turn on **Allow removals**.
- **Story points** are never spent unless you change **Story points** from Player only. Each improvement at a colony costs twice the last.
- **Big builds.** Anything over the **Per-project cap** is proposed to you.
- **Fees.** Approve and Auto add a convenience fee (Standard is 5%, at least 5,000). Set it to Off, Low, Standard or High.

## Stations

The **Station line** setting picks the line: Pick by score, Low tech, Midline, High tech, or Imperium. While you're spending freely ([Money](money.md)), every colony maxes its station, Heavy Batteries, Planetary Shield, Megaport and Patrol HQ.

## Stable locations

In systems with your Auto colonies, a construction crew builds the best of these at a stable spot:

- **Comm relay**: +1 stability at every colony of yours there. Usually first.
- **Nav buoy**: +2 burn for your fleets. Security colonies care more.
- **Sensor array**: +400 sensor range.

One of each per system. It costs 15 heavy machinery, 30 metals and 5 transplutonics at base price plus a fee, and goes up 10 days after the crew leaves. The **Stable locations** setting turns this to Recommend only or Off.

Next: [Your capital](capital.md).

## Coming in 0.3.0

- Built, in testing: builds, upgrades, terraforming, Research Facilities, wonder sites, crews and story point improvements are ranked in one queue. Money goes to the option with the best return per credit.
- Built, in testing: a build has to pay back within 36 months (planned default). The limit grows to 120 months as spare credits increase. A 360-day structure can't take the construction slot while a better build is waiting.
- Built, in testing: removals use the same calculation as builds, and removed buildings aren't rebuilt at that colony for a year.
- Built, in testing: spending freely, free ports and hazard pay need a margin before they switch back. A colony that already paid for terraforming toward one world plan only switches to a plan worth at least twice as much.
- Planned: auto-expansion, off by default.

Plans can still change before release.

Everything planned for 0.3.0: [roadmap](roadmap.md)
