# Items, AI cores and administrators

## Autopilot Storage

Every colony you own has its own **Autopilot Storage** tab. This is the only place the manager takes items and cores from. It never touches your normal Storage or your cargo for this, and it only places things at colonies on Auto.

It uses an item at the colony it's stored at. If the best spot for it is at another colony, it ships it there first: that costs a fee (2,000 plus 300 a light-year, scaled by your convenience fee setting) and takes a couple of days plus a day a light-year, then it goes in once it arrives. Same for an AI core moved from one colony's building to another's. Nothing jumps across the sector for free. Whatever the manager takes out of a building goes into that colony's own Autopilot Storage.

Only stuff the manager can actually use goes in: colony items it can install, AI cores, research databanks, Antimatter Stabilizers, VPC chips and recipes. Blueprints, blueprint packages, UAF's Access Key Cards and the like stay out (keep them in your cargo or the normal Storage), and anything like that already in there gets moved over to the colony's normal Storage.

If you're coming from a version before 2.37.0, where it was one shared storage, everything that was in it gets moved into your biggest colony's Autopilot Storage once, and the log says so.

What goes in:

- colony items (nanoforges, synchrotrons, cryoarithmetic engines and every mod's items that the game knows how to install);
- AI cores;
- research databanks (Vaults of Knowledge takes them from here);
- Antimatter Stabilizers (Emergent Threats), VPC chips (Industrial Evolution), recipes (Substance.Abuse).

What doesn't: campaign consumables and blueprints (Industrial Evolution's Nanite Repair Module and its other consumables, ship and weapon blueprints, Random Assortment's Forge-Assisted Industry BP and anything else that's used up on right click). If any of those were already in there from an older version, they get moved to your biggest colony's normal Storage with a line in the log.

The empire panel's **Autopilot Storage** page lists what's in each colony's store, what's on its way where, and each colony's inspection points.

## Colony items

Each item goes where it adds the most, at an Auto colony that can take it. When a few items want the same building, the best one gets it, and a copy that's already at that colony goes in before another one gets shipped over. A few specifics:

- A better item swaps in for a lesser one of the same kind (a Pristine Nanoforge for a Corrupted one), and the lesser one goes back to storage.
- An item waiting in storage counts toward building whatever would use it, so a nanoforge sitting there can get a Heavy Industry built somewhere.
- The Orbital Fusion Lamp only goes to a cold or dark colony. Anywhere else it just adds heat.
- A nanoforge that pollutes (Corrupted, Pristine, anything that isn't marked as clean) never goes into a forge on a habitable world, no matter the colony's goal, threat or the endgame. The pollution from one is permanent after 90 days, so it's just not worth the risk. It waits for a forge on a world that isn't habitable instead, and it won't get a forge built on a habitable world either. The Dimensional Nanoforge (UAF) and Ashes of the Domain's clean ones are fine anywhere.
- If a polluting nanoforge is already in a forge on a habitable world at an Auto colony and its pollution can still be undone (under 90 days, and the world wasn't polluted before), it comes back out to Autopilot Storage and the log says how many days were left. Lock the building if you put it there on purpose and want it kept.
- A few items that do nothing useful (or hurt more than they help) are never installed. Those are listed in [details.md](details.md).

## AI cores

**Use AI cores** is on with the recommended settings. Each core goes where it adds the most for that colony's goal:

- an Alpha where its +1 output, or a structure's bonus (bigger fleets, station strength, ground defense), is worth the most;
- a Beta where upkeep is highest;
- a Gamma only where an input is short.

A better core replaces a lesser one, which goes back to storage. Cores also add colony crisis progress, and that cost is counted (see [War and security](war-and-security.md)).

**Alpha cores** can be **Only where removable** (recommended), Anywhere, or Never. **Alpha cores in research buildings** set to **First** puts Alphas into Vaults of Knowledge Research Facilities and Black Sites before anywhere else, since there they cut the research costs.

### Storage logistics

Off until you switch it on, under Settings. Pick a **refit hub** (your production gathering point unless you choose another) and once a week Colony Manager ships everything you'd refit a fleet with there from your other Storage tabs: mothballed ships, weapons, fighter wings, blueprints and hull mod specs you don't know yet, and supplies, fuel, crew and marines if you want those too. Pick a **sell hub** as well and the blueprints and specs you already know (and spare copies of ones you don't) go there instead, so selling them is one stop.

Nothing teleports: every load pays a shipping fee (plus 1,000 a ship) out of the monthly budget and takes a couple of days plus a day a light-year. Colony items and AI cores stay where they are, Autopilot Storage handles those.

### Panopticon cores

With **Emergent Threats: IX Revival**, the Panopticon Instance and Panopticon Core go in Autopilot Storage too. They only fit that mod's Surveillance Center, so that's where they go: the Instance first, at the colony with the most of your colonies in its system. Neither counts for Hegemony inspections.

### Omega cores

With **Omega Core Everywhere** or **Astral Ascension Renewed** installed, you can drop Omega cores in Autopilot Storage too. Without either one, an Omega core barely does anything in a colony, so storage won't take them.

- With Omega Core Everywhere, one goes into whichever building it does the most in (that mod doubles a lot of things: output, income, fleets). A core in a building doesn't count toward inspections at all.
- With Astral Ascension Renewed, one can govern a colony instead, which is usually the better spot by a mile. As an administrator it counts 10 inspection points though, so it only happens where your **Inspection risk** setting allows that. Otherwise the log tells you it's your call.
- Once placed, it stays put. The manager never swaps out an AI core administrator, which matters a lot with Astral Ascension Renewed: past size 10, taking that core away starts a civil war.

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

An administrator is only hired, placed or moved where they add more than they cost. What they add is their +1 to everything the colony's industries make, which is worth a lot more at a big productive colony than a small one, plus stability from your colony limit. That's set against their salary (2,500 a month, 20,000 with a skill) and the hiring cost.

A hire always takes 30 days to show up, since they're hired from another market's comm directory and have to travel.

Past your colony limit, one is always placed or hired whatever the money says, since every colony past the limit costs every colony you govern 2 stability. That hire can go over the monthly budget but never under the reserve. An idle administrator who isn't worth their salary anywhere just stays unassigned, and dismissing them is up to you.

Heads up on where they come from: the game only puts an administrator up for hire when you dock at a market, and not often (5% a visit, plus 5% for every size over 3, and they leave after 2 to 4 months). Friendly markets you never visit never get one. So if you're past your colony limit and nothing's been on offer for 90 days, the manager pays a recruiter to find one: a basic administrator (no skill, 2,500 a month) for 250,000 on top of the normal hiring cost and fee. Pricey on purpose. Docking around still gets you a cheaper one, and a skilled one if you're lucky.

Next: [War and security](war-and-security.md).
