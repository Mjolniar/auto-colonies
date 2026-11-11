# Items, AI cores and administrators

## Quick version

- Items and cores in **Autopilot Storage** go to the Auto colony where they add most, for a fee and a few days.
- **AI cores** on Safe keeps colonies at 7 AI core points or under, which avoids Hegemony inspections.
- Administrators are hired where they pay, and always past your colony limit.

## Autopilot Storage

The empire panel's **Autopilot Storage** page shows stock and shipments.

- **Goes in:** colony items, AI cores, research databanks, Antimatter Stabilizers, VPC chips, recipes.
- **Stays out:** blueprints, access keys, campaign consumables.
- **Moves:** 2,000 plus 300 a light-year (scaled by your convenience fee); a couple of days plus a day a light-year. Loads on one route within 7 days share one fee.

## Colony items

- An item goes where its extra output adds most to the colony's supply. A better item of the same kind replaces a lesser one.
- A nanoforge in storage can get a Heavy Industry built.
- Polluting nanoforges (Corrupted, Pristine) stay out of forges on habitable worlds (pollution is permanent after 90 days).
- A few useless items are not installed ([details.md](details.md)).

## AI cores

Settings, Items and people. The Hegemony inspects only colonies over 7 inspection points. Cores over a colony's limit go back, one a day.

| AI cores | Inspection risk | Hegemony inspections |
|---|---|---|
| Off | kept | kept |
| **Safe** (recommended) | Never inspected | Advise |
| Small colonies only | Small colonies only | Advise |
| Defended | More where defended | Resist where defended (makes the Hegemony hostile) |
| No limit | No limit | Advise |

- **Advise** logs each inspection so you can resist or bribe.
- Alpha: where +1 output or a structure bonus is worth most. Beta: highest upkeep. Gamma: only where an input is short.
- **Alpha cores** (Settings, Advanced, AI cores and items): **Only where removable** (recommended), Anywhere, Never.

### Storage logistics

Off by default. Set a **refit hub** and a **sell hub** (Settings, Advanced, Capital and storage). Weekly, refit gear (ships, weapons, wings, blueprints, specs; optionally supplies, fuel, crew, marines) goes from other Storage tabs to the refit hub. Known blueprints and specs go to the sell hub. Each load: a fee plus 1,000 a ship, from the budget.

### Omega cores

Stored only with **Omega Core Everywhere** or **Astral Ascension Renewed** ([Supported mods](supported-mods.md#omega-core-everywhere-and-astral-ascension-renewed)). A placed AI core administrator is not swapped out.

## Administrators

**Administrators** (Settings, Items and people): Off, Place only, or **Hire and place** (recommended).

- Each adds +1 to everything the colony's industries make, plus stability from your colony limit. Cost: 2,500 a month (20,000 with a skill) plus hiring. Hires take 30 days.
- A move that would take a colony under the stability floor waits. The management bonus a self-governed colony loses (under your limit) is counted.
- Past your colony limit (each extra colony costs all colonies 2 stability), one is always placed or hired right away. This can go over budget. It does not go under the reserve. Colonies Auto-expansion is founding count ([New colonies](expansion.md)).
- After 90 days past your limit with none on offer, a recruiter adds 250,000 to the normal cost.

Next: [War and security](war-and-security.md).
