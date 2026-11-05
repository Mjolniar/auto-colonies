# Research

All of this needs Ashes of the Domain (Vaults of Knowledge). The settings are under **Ashes of the Domain**, and the Research section lives on the empire panel's Autopilot Storage page.

## How it picks research

The **Research** setting is Advisor before setup, **Auto** with the recommended settings, or Off.

Each tech you can start is valued by what it would unlock at your colonies. It does that by pretending the tech is done for a moment and scoring your colonies' builds the same way it scores anything else, then putting it back. On top of that come the tech's other effects (more output, lower hazard, lower upkeep) and half the value of the best tech it leads to.

Your colonies' plans come first. If a plan step is locked by research, the whole chain of techs it still needs gets traced, and the value of that step is shared along the chain. So the first tech in a three-tech chain toward something a colony actually wants still gets picked.

Auto starts the best tech whenever nothing is running or queued. If you have something queued yourself it waits, it never touches your queue. It also puts Sophia Anderson at the head of R&D before starting if you have her, since she knocks a databank off every tech.

Databanks are usually the bottleneck. While the best plan tech is short of them, the ones you have only go to techs some plan needs, and the log says what's next and how many it needs. After 90 days of that, the best thing you can afford goes ahead anyway so research doesn't sit idle forever. Same if nothing pays for itself within a year: it starts the best of the rest rather than stopping.

When a tech starts, whatever the Research Facility tab is short of gets moved in from Autopilot Storage, then databanks from your colonies' normal Storage, then from your fleet's cargo. Nothing moves unless everything the tech needs is there.

## Techs that take colony items

Some techs eat colony items (a Pristine Nanoforge, a Synchrotron Core and so on). By default those are yours to start. The Research section lists each one with what it takes, how many you have, and where each item comes from.

If you'd rather it handled those too, turn on **Research spends colony items (surplus only)**:

- **From storage** - while your credits are over the surplus line, Auto research can take the items from Autopilot Storage and your colonies' Storage tabs.
- **From storage and buildings** - same, but it can also pull them out of buildings, starting with the one where the item does the least. Buildings you've locked are never touched.

Either way, nothing gets pulled until everything the tech needs is on hand, so you won't lose a nanoforge to a tech that can't start anyway. This is off by default.

## AI cores in research buildings

A core in a Research Facility or Black Site doesn't speed up research. Vaults of Knowledge uses it to cut the building's own costs: half for an Alpha, a quarter for a Beta, a tenth for a Gamma. An Alpha also adds a databank a month on a world with a pre-collapse facility. With **Alpha cores in research buildings** on **First** (recommended), Alphas go there before anywhere else.

## Surplus research

With credits over the surplus line, more Research Facilities go up at Auto colonies, one at a time, up to the 8 that Vaults of Knowledge counts (each past the first is +10% research speed). Worlds with a pre-collapse facility go first. They only go up while the research left is worth it, or always during the [endgame economy](money.md#the-endgame-economy) as long as anything's left to research. The ones it built come down once there's nothing left. **Surplus research** under Budget and spending turns this off.

## Special project supply

A black site project takes goods every month from your own colonies' production, and a stage stops if any of them isn't delivered at all. Some of those goods (Electronic Components, Domain-grade machinery, Refined Metal, Enriched Transplutonics) aren't made by any faction, so without your own producer the project just never moves.

**Special project supply** on **Auto** (recommended) looks at the project you're running and, for each good none of your colonies make, puts its producer in as a first plan step at the best colony for it, along with the producers of that building's own inputs. Research then works toward it and it gets built. **Warn only** just tells you, and **Off** leaves it alone. Cores, items and ships a stage takes are always yours.

While spending freely, it goes further and builds producers for all of those advanced goods even with no project running.

## Wonders and gates

While spending freely, with **Wonders and gates while spending freely** on:

- **The Daedalus Array** goes up at a colony that qualifies.
- **The Bifrost network**: the Bifrost Control Center becomes a plan step (so research works toward it), then a gate goes up in each of your systems.

Ashes of the Domain takes their construction goods out of your exports every month, so each one only starts once your colonies make every good it needs. The Garden of Elysium and Star Citadels are left to you.

Next: [Terraforming](terraforming.md).
