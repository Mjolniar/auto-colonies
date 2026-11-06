# Terraforming

This needs Terraforming Made Easy (TME), or Boggled's Terraforming & Station Construction for the part near the bottom.

## Special worlds in the plan

With TME, a colony's plan can aim for one of TME's special worlds: Forge, Fortress, Paradise, Hive, Death or Waste. It only picks one the colony can actually reach, where the world's industries fit the slots the colony gets at max size, and it counts the hazard and deposits the world ends up with. The steps come straight from TME's own requirement table, worked back through the projects that create each condition, so the plan includes the structures to build and the terraforming to run, in order. TME's structures and the Unification Center get the plan's priority even though they cost more than they earn, because that's how the colony gets there. The Unification Center goes last though: it only runs the final conversion, so it waits until everything else in the plan is in place (or the last condition is already being made), and gets built during that last project instead of sitting around eating upkeep for months.

If no special world is reachable, the colony gets a normal plan for its goal, and hazard clean-up (toxic atmosphere, radiation, high gravity and so on) still goes in when the planet needs it, since that lets it grow.

## The plan's own terraforming

On Auto colonies, it runs TME projects using TME's own project lists and build checks, starting them the same way TME's dialog does. It looks one project ahead, and two ahead when swapping a deposit for a richer one. Side effects count: hazards a project adds are scored, and conditions TME removes as conflicts are simulated before a project is picked.

TME's projects toggle conditions on and off, so it re-plans as soon as a colony's conditions change and never starts something that would undo what the colony already is. Nothing removes what the plan needs, and nothing adds back a hazard the plan clears.

This part spends up to 40% of the monthly budget.

## Surplus terraforming

With credits over the surplus line, whatever the focus world doesn't use goes into TME projects at every Auto colony, best value per credit first: richer deposits, better farmland, harsh conditions cleared. It builds the structure when a colony doesn't have one, and takes it back down once the work is done (TME's changes stay).

It plans on TME's own rules, looking one or two projects ahead including TME's habitability updates, so it'll never trade a habitable world's farmland and organics for richer ore, and never undoes what the colony's plan needs.

The **Surplus terraforming** setting:

- **Max output** (recommended) - anything worth 1,000 a month or more after the structure's upkeep.
- **Pays back in 10 years** - only what pays for itself within ten years.
- **Off**.

While spending freely it goes harder: up to three projects at once per colony on different structures, planned from what the colony will be once the running ones finish, and it goes for the richest deposits rather than the cheapest gains. When a richer deposit needs a hazard first (abundant ore wants extreme tectonic activity, for example), it adds that hazard as a stepping stone and then the deposit.

With Vaults of Knowledge, structures that research still locks aren't built, but research values their techs by what they'd allow.

Pollution from a nanoforge isn't cleared while a Heavy Industry with one is being upgraded on a habitable world, since the new building just adds it back.

## Terraforming & Station Construction (Boggled)

Each project TASC offers is valued by the conditions it would leave behind. Auto starts the best one, and the colony panel lists the best four with a Start button. A running project is never cancelled. The Atmosphere Processor and Stellar Reflector Array get built when a worthwhile project is only waiting on them. Station construction is left to you, since it needs your fleet there.

Next: [Settings](settings.md).
