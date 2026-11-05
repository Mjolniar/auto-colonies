# Items, AI cores and administrators

## Autopilot Storage

Every colony you own has an **Autopilot Storage** tab. It's one shared storage, so whatever you put in at one colony is there at all of them. This is the only place the manager takes items and cores from. It never touches your normal Storage or your cargo for this, and it only places things at colonies on Auto.

What goes in:

- colony items (nanoforges, synchrotrons, cryoarithmetic engines and every mod's items that the game knows how to install);
- AI cores;
- research databanks (Vaults of Knowledge takes them from here);
- Antimatter Stabilizers (Emergent Threats), VPC chips (Industrial Evolution), recipes (Substance.Abuse).

What doesn't: ships, ambassadors (keep those in a colony's normal Storage, it installs them from there), and campaign consumables or blueprints (Industrial Evolution's Nanite Repair Module and its other consumables, ship and weapon blueprints, Random Assortment's Forge-Assisted Industry BP and anything else that's used up on right click). If any of those were already in there from an older version, they get moved to your biggest colony's normal Storage with a line in the log.

The empire panel's **Autopilot Storage** page lists what's in it and each colony's inspection points.

## Colony items

Each item goes where it adds the most, at an Auto colony that can take it. A few specifics:

- A better item swaps in for a lesser one of the same kind (a Pristine Nanoforge for a Corrupted one), and the lesser one goes back to storage.
- An item waiting in storage counts toward building whatever would use it, so a nanoforge sitting there can get a Heavy Industry built somewhere.
- The Orbital Fusion Lamp only goes to a cold or dark colony. Anywhere else it just adds heat.
- It's careful with nanoforges that would pollute a clean habitable world.
- A few items that do nothing useful (or hurt more than they help) are never installed. Those are listed in [details.md](details.md).

## AI cores

**Use AI cores** is on with the recommended settings. Each core goes where it adds the most for that colony's goal:

- an Alpha where its +1 output, or a structure's bonus (bigger fleets, station strength, ground defense), is worth the most;
- a Beta where upkeep is highest;
- a Gamma only where an input is short.

A better core replaces a lesser one, which goes back to storage. Cores also add colony crisis progress, and that cost is counted (see [War and security](war-and-security.md)).

**Alpha cores** can be **Only where removable** (recommended), Anywhere, or Never. **Alpha cores in research buildings** set to **First** puts Alphas into Vaults of Knowledge Research Facilities and Black Sites before anywhere else, since there they cut the research costs.

## Hegemony inspections

The Hegemony only inspects a colony with more than 7 inspection points (cores add points). **Inspection risk** decides how many a colony may hold:

- **Never inspected** (recommended) - every colony stays at 7 or under.
- **More where defended** - more where the colony's defenses beat the next inspection by a good margin. Only really worth it with inspection orders on Resist.
- **Small colonies only** - no cores in colonies above size 3, so they add no Hegemony progress at all.
- **No limit**.

A colony over its limit sends cores back to storage one a day. Once you've beaten the Hegemony's inspections, joined the League, or the Hegemony has no colonies left, there's no limit.

**Hegemony inspection orders**: the game's default is to comply, which hands over every core. **Advise** (recommended) logs each one coming so you can resist or bribe yourself. **Resist where defended** resists only where the defenses beat it, which turns the Hegemony hostile.

## Administrators

- **Administrator hiring** (on with the recommended settings) - hires when one is worth it.
- **Assign administrators optimally** - places and moves them where they add the most.
- **Arrival delay** - a newly hired administrator takes 30 days to show up, if you want that.

An administrator is only hired, placed or moved where they add more than they cost. What they add is their +1 to everything the colony's industries make, which is worth a lot more at a big productive colony than a small one, plus stability from your colony limit. That's set against their salary (2,500 a month, 20,000 with a skill) and the hiring cost.

Past your colony limit, one is always placed or hired whatever the money says, since every colony past the limit costs every colony you govern 2 stability. That hire can go over the monthly budget but never under the reserve. An idle administrator who isn't worth their salary anywhere just stays unassigned, and dismissing them is up to you.

## The storage cheat

Off unless you turn it on (Settings, **Cheats**). With it on, Autopilot Storage takes anything but ships, ambassadors and campaign consumables, and every colony that uses stockpiles draws its shortages from it for free. The recommended settings never turn this on.

Next: [War and security](war-and-security.md).
