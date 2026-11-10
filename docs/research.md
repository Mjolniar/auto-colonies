# Research

Needs Ashes of the Domain (Vaults of Knowledge). Settings are under **Ashes of the Domain**.

## Quick version

- **Research** on **Auto** starts the best tech whenever nothing is running or queued. Your own queue is left alone.
- Techs are valued by what they unlock at your colonies. Plan steps locked by research come first.
- Databanks are the usual bottleneck, so they go to techs a plan needs.

## How it picks research

Setting: Advisor before setup, **Auto** with recommended settings, or Off.

- **Value.** It pretends a tech is done and scores what that unlocks at your colonies, plus the tech's own effects and half the best tech it leads to.
- **Plan chains.** If a plan step is locked, the whole chain of techs it needs shares its value.
- **Research Facility.** If there is none, one goes first in your focus world's plan.
- **Sophia Anderson** is put at the head of R&D first, since she knocks a databank off every tech.
- **Databanks.** While the best plan tech is short, they only go to techs a plan needs. After 90 days of that, the best affordable tech goes anyway.
- **Moving things.** Whatever the Research Facility is short of is shipped in for a fee and a couple of days. Nothing starts until it all arrives.

## Techs that take colony items

Some techs eat colony items (a Pristine Nanoforge and so on). Those are yours to start by default.

**Research spends colony items (surplus only)** lets Auto take them while credits are over the surplus line:

- **From storage.** Autopilot Storage and Storage tabs.
- **From storage and buildings.** Also buildings, starting where the item does least. Locked buildings are safe.

Nothing is pulled until the tech has everything it needs. Off by default.

## AI cores in research buildings

A core there cuts the building's own costs (Alpha half, Beta a quarter, Gamma a tenth). **Alpha cores in research buildings** on **First** (recommended) puts Alphas there first.

## Surplus research

With credits over the surplus line, more Research Facilities go up at Auto colonies, one at a time, up to 8 (each past the first is +10% research speed). They go up while research left is worth it, or always in the [endgame economy](money.md#the-endgame-economy). **Surplus research** under Budget and spending turns it off.

## Special project supply

Black site projects take goods from your colonies each month, and some (Electronic Components, Refined Metal, Enriched Transplutonics) no faction makes. With **Special project supply** on **Auto** (recommended), a good none of your colonies make gets its producer planned as a first step at the best colony. Open production orders work the same way. **Warn only** just tells you. **Off** leaves it alone.

## Wonders and gates

While spending freely, with **Wonders and gates while spending freely** on, it builds the Daedalus Array and the Bifrost network (control center, then a gate per system). Each starts once your colonies make every good it needs.

## Coming in 3.0

- Built, in testing: databanks aren't spent on techs that don't pay back within a year, unless money is idle.
- Built, in testing: extra Research Facilities have to cover their full upkeep.
- Built, in testing: ranking techs no longer changes anything in your save.
- Planned: Research Facilities are funded through the money ladder. See [Money](money.md).

Everything planned for 3.0: [roadmap](roadmap.md)

Plans can still change before release.

Next: [Terraforming](terraforming.md).
