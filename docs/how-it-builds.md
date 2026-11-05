# How it builds

This is the core of the mod: how a colony decides what to build next, and how you stay in charge of it.

## Modes

Every colony has its own mode:

- **Off** - left completely alone.
- **Advisor** - the default. It shows what it would build and why, and you click to build.
- **Approve** - it proposes one thing at a time, and nothing happens until you approve it.
- **Auto** - it builds by itself within your budget. Most of the other automation (items, cores, administrators, policies, terraforming) only runs on Auto colonies too.

## Goals

The goal decides what a colony cares about besides money:

- **Balanced** - a bit of everything.
- **Money** - income and accessibility.
- **Security** - ground defense, fleets and stability.
- **War production** - ship quality and fleet size.
- **Rapid growth** - population growth and accessibility.
- **Self-sufficiency** - making what your colonies are short of.

A colony in danger gets planned as Security on its own until things calm down (see [War and security](war-and-security.md)), then goes back to its own goal.

## Plans

Each colony gets a plan: a target layout worked out from the planet itself and its goal. A barren world with ore and transplutonics becomes a Forge (mining, refining, heavy industry, orbital works). A habitable world on Security becomes a Fortress. A habitable world on Money or Growth becomes a Paradise. A cold world with volatiles becomes a Fuel Depot, and so on. Hazard clean-up gets added to any plan when the planet needs it.

With Terraforming Made Easy, a plan can aim for one of its special worlds instead (Forge, Fortress, Paradise, Hive and the rest), and the steps include the terraforming to get there. That's in [Terraforming](terraforming.md).

Plan steps get a priority boost, the next step most of all, but a plan never forces a bad building. If the next step is blocked by research or something else, the colony still builds whatever is worth the most on its own in the meantime. A step that research still locks shows what it needs, and with Vaults of Knowledge, research works toward it.

## How it picks

Every option the colony could build or upgrade gets a score:

- what it does a month: money after upkeep, plus everything else it adds (stability, accessibility, defense, fleets, ship quality, growth), weighted by the colony's goal;
- a bonus if it's a plan step;
- minus its price spread over three years.

Only things that pay for themselves within about three years ever get built on Approve or Auto. The colony panel's **Recommended builds** shows every option with how its score is made up, so you can always see why it picked what it picked.

The numbers come from the game's own formulas wherever there is one: deposits, output, upkeep, the colony's size. Mod buildings with their own effects are estimated, then those estimates get checked against what your real buildings do and corrected over time.

Each colony is looked at every 7 days, or right after something finishes building. Across the whole empire, the best value per credit goes first. A colony starts at most one new building and one upgrade at a time (more upgrades while spending freely), and if the best thing has to wait for budget, the next best goes instead so the colony doesn't just sit there.

A few things it avoids on its own:

- putting a nanoforge where it would pollute a clean habitable world (War production colonies mind a lot less);
- building past 12 buildings, since the colony screen can't show a 13th (Grand.Colonies changes this);
- taking an industry slot the plan still needs for something else.

## Mod buildings it doesn't know

A building from some mod with no formula gets measured. It's built on a throwaway test colony that nobody sees, its real effects are read off, and then it's scored like anything else. Anything that misbehaves on the test colony gets quarantined and never touched again.

The empire panel's **Diagnostics** page rates every building:

- **Supported** - formulas or estimates it trusts.
- **Measured** - scored from the test colony. The **Mod buildings with measured effects** setting decides whether Auto builds these on its own, only proposes them, or ignores them.
- **Analyzed** - looked at, but nothing it can value.
- **Quarantined** - misbehaved, left alone.

## Staying in control

- **Locks.** The colony panel's **Locks** page lists your buildings. A locked building is never upgraded, replaced, removed, or touched for items and cores.
- **Removals.** It doesn't remove buildings unless a feature needs to (terraforming structures it built, or the endgame replacing weak industries) or you turn on **Allow removals**.
- **Story points** are never spent unless you change **Story points** away from Player only. If you allow it, each improvement at a colony costs twice the last one there, so the cheapest ones go first.
- **Big builds.** Anything over the **Per-project cap** gets proposed for your approval instead of built.
- **Fees.** Approve and Auto add a small convenience fee to each project (Standard is 5% of the cost, at least 5,000). You can set fees to Off, Low, Standard or High.

## Stations

The **Station line** setting picks which station line new stations use: Pick by score, Low tech, Midline, High tech, or Imperium with Interstellar Imperium installed. Pick by score chooses by what each one actually does at that colony. Stations from other mods count like vanilla ones.

Once you're spending freely (see [Money, surplus and the endgame](money.md)), every colony maxes out its station, Heavy Batteries, Planetary Shield, Megaport and Patrol HQ even if they cost more than they earn.

Next: [Money, surplus and the endgame](money.md).
