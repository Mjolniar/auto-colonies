# Terraforming

Needs Terraforming Made Easy (TME). Boggled's TASC is at the bottom.

## Quick version

- A colony's plan can aim for a TME special world (Forge, Fortress, Paradise, Hive, Death, Waste) and builds the steps in order.
- Auto colonies also run TME projects, looking one or two ahead.
- Credits over the surplus line go into terraforming at every Auto colony.

## Special worlds

It only picks a world the colony can reach, where the industries fit its max-size slots. Steps come from TME's own requirements. The Unification Center goes last, during the final project. With no reachable world, the colony gets a normal plan, plus hazard clean-up when the planet needs it.

## The plan's terraforming

Uses up to 40% of the monthly budget. Hazards a project adds are scored, and it re-plans when conditions change. It keeps whatever the plan needs.

## Surplus terraforming

Spare credits go into the best value per credit first: richer deposits, better farmland, cleared hazards. It builds the structure if missing and takes it down when done.

**Surplus terraforming** setting: **Max output** (recommended, 1,000 a month or more after upkeep), **Pays back in 10 years**, or **Off**.

Spending freely runs up to three projects per colony at once and goes for the richest deposits.

## Terraforming & Station Construction (Boggled)

Each project is valued by the conditions it leaves. Auto starts the best, and the colony panel lists the best four with Start buttons. Station construction is yours.

## Coming in 0.3.0

- Built, in testing: if a colony already paid for terraforming toward one world plan, it only switches to a plan that undoes it if the new plan is worth at least twice as much.
- Built, in testing: a 360-day TME structure only starts when no better build is waiting for the colony's construction slot.
- Planned: surplus terraforming can use idle budget, with a planned payback limit of 24 months. See [Money](money.md).

Everything planned for 0.3.0: [roadmap](roadmap.md)

Plans can still change before release.

Next: [Settings](settings.md).
