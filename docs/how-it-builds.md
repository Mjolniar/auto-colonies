# How it builds

Every option is scored by what it earns a month minus its costs, and Approve and Auto only start options above 0. Recommended builds on the colony panel shows each score.

## Modes

- Off leaves the colony alone.
- Advisor (default) ranks options as Best, Good or Poor beside the build menu, and you do the building.
- Approve proposes one build at a time, and a rejected build is held for 180 days.
- Auto builds within your budget. Only Auto colonies use items, cores, administrators, policies and terraforming.
- Empire-wide settings use the Empire mode ([Settings](settings.md#mode-defaults)).
- Approvals and the colony panel's Build button pay the price plus a convenience fee from credits above the reserve. Convenience fees (Settings, Advanced, Budget) can be Off, Low, Standard (5%, at least 5,000) or High.
- Waiting decisions come up in one summary every 60 days (15 to 180 days or Off in Settings, Advanced, Interface), and they also wait in the colony panel.

## Plans

- Each colony's layout comes from its planet and its goal: Balanced, Money, Security, War production, Rapid growth or Self-sufficiency (it goes after what your colonies are short of). Special worlds need Terraforming Made Easy ([Terraforming](terraforming.md)).
- After fast buildout, a colony gets a station at size 4, a Battlestation from 6 and a Star Fortress from 8, and the capital gets each one a size sooner.
- It won't do anything that takes a colony below minimum stability (default 5, never lower than 3). If a colony sits at 0 for 20 days, it takes down the building costing the most stability, then one more every 10 days until it's off 0.

## How it picks

- An option's monthly value is its money after upkeep plus stability, accessibility, defense, fleets, ship quality and growth, weighted by the goal. Money above 300,000 a month is [tapered](money.md#money-taper).
- Each colony builds one new building at a time (2 or 3 with the [story perks](story-mode.md)), and upgrades run beside it. A colony can have up to 12 buildings.

## Production floors

- Production (Settings, Advanced, Production) can be Off, Light (recommended) or Full. Light asks for 1 hull source, 100,000 hull capacity a month, quality 0.8 (about 1 d-mod a hull) and no supplies or fuel shortage. Full asks for 2 hull sources, 300,000, quality 1.0 and supplies and fuel for your fleet.
- Builds that close an open floor rank first, and a missing producer becomes a plan step at the best colony, which proposes replacing its weakest income-only industry if it has no free slot.
- The Budget panel shows how far off each floor is.

## Taking buildings down

- With Take down buildings on Clean up (recommended), an industry that's worth only its output, and less than its upkeep, for 30 days is taken down, one per colony every 30 days. Locked, plan, military and stability buildings stay.
- Taken-down buildings, and what they were upgraded from, aren't rebuilt there for a year.

## Mod buildings

- Diagnostics rates mod buildings with no formula as Supported, Measured, Analyzed or Quarantined, and Quarantined ones are left alone. Measured mod buildings (Settings, Advanced, Extras) can be Build when best, Recommend only or Ignore.

## Stable locations

- A crew builds one Comm relay (+1 stability), Nav buoy (+2 burn) and Sensor array (+400 sensor range) in each system with your Auto colonies. Stable locations (Settings, Advanced, Extras) can also be Recommend only or Off.
- With Vaults of Knowledge's Janus Device analysis researched, a system with no free spot gets a new stable location when the objective it makes room for would pay. It's the same as the colony dialog's "Create a stable location": once per system, while it has 3 or fewer, for 500 fuel, 100 Electronic Components and 150 Domain-grade Machinery bought at base price, plus the fee and a 10-day crew.

## Your capital

- You can change the capital on a colony panel or in Capital (Settings, Items and people), and None turns it off.
- Capital does (Settings, Advanced, Capital and storage) can be Deliveries and commerce (default), Deliveries only or Off.
- Custom production arrives at the capital, [Shipyard](money.md#the-money-ladder) orders included. Every week, ships, weapons, wings, blueprints, specs, supplies, fuel, crew and marines in the storage tabs are sent there for a shipment cost plus 1,000 a ship, unless a [refit or sell hub](items-and-cores.md#storage-logistics) takes them.

### Trade buildings

- Once net income is over the Trade hub starts at setting (100,000 a month, or 50,000, 250,000 or Right away) and no fast buildout is running, it builds trade buildings, starting with the Spaceport.
- The costly ones need the Surplus tier, the [Economy rung](money.md#economy) On and upkeep within 5% of colony net.
- With Reverse Engineered Private Arsenal, the Reverse Engineering Hub is one of them, and it goes up at the capital only, one tier at a time.
- From Idle money with its slots full, the capital replaces its weakest ordinary industry when the swap pays, once per 90 days at most.
