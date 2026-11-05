# Supported mods

None of these are required. Each one turns on by itself when its mod is enabled, and any mix of them works. The empire panel's **Diagnostics** page shows how each one is doing (OK, newer version, partly off, off).

Unless it says otherwise, the manager only acts on colonies in Auto. Approve colonies get a proposal and Advisor colonies a recommendation, and everything follows your budget.

If a mod you use isn't here and you'd like it supported, throw it in the [issues](https://github.com/Mjolniar/jonahs-colony-manager/issues).

## Terraforming Made Easy

Special-world plans (Forge, Fortress, Paradise, Hive, Death, Waste) built from TME's own requirement table, the plan's terraforming, and surplus terraforming at every Auto colony. All in [Terraforming](terraforming.md).

## Industrial Evolution

- **Ambassadors** - hires one for an empty Embassy and installs one waiting in a colony's normal Storage, with a focus (worst relations first and so on), a priority per faction, and a level where it stops. One hired from afar takes 30 days to arrive.
- **Senate edicts** - enacts the one that suits the colony. The harsh ones (forced labour, cleansing, single-child, labour limits) are never automated.
- **VPC chips** - installs chips from Autopilot Storage.
- **Specialized Mining and Refining** - switches to whichever specialty is worth the most.
- **Supply shipping** - when a colony runs short, it sets up a monthly Courier Port contract from another colony's stockpile and cancels it once the shortage is gone. Only the best-connected colony gets a Courier Port.
- Its campaign consumables aren't allowed in Autopilot Storage.

## Nexerelin

Emergency defense fleets, war fleets (relief, defense, pirate base strikes, raids on whoever attacked you, never invasions), agents, the threat estimate, sieges, the police crisis, and the Derelict Empire start. All in [War and security](war-and-security.md). Autonomy is in [Money](money.md#autonomy-nexerelin). Nexerelin's player outposts are left alone completely, since they sit outside your economy.

## Ashes of the Domain

Covers Building Menu Overhaul, Vaults of Knowledge and Theory of Toolbox.

- **Research locks and upgrade paths** - it asks Building Menu Overhaul the same questions the build menu does, so nothing locked or hidden ever gets built, and AotD's upgrades (Monoculture Plots to Farming and so on) are started the way the menu does it.
- **Starter buildings** and **industry synergies** are scored, and the colony panel shows active synergies and ones a building away.
- **Management styles** - new buildings are scored with the style's real effects. The style itself is never changed. On a Floating City nothing is built while fuel would run short.
- **Research**, **special project supply**, **wonders and gates** - see [Research](research.md).
- **Trade contracts** - takes an offer only when ToT's own numbers leave 15% to spare and it pays more than exporting would.
- **Megastructures** - the Coronal Hypershunt (wormhole range kept to what reaches your receivers, the collector and wormhole restored when you can afford it), Pluto Mining Station and the Nidavelir Complex. Anything you change yourself is left alone until you click **Let Autopilot manage it**.
- **Colony items** - AotD's items are placed like vanilla ones.
- **Left to you**: Grand Wonders other than the Daedalus Array, Star Citadels, custom production, and picking a management style.

## Emergent Threats (IX Revival)

With an Antimatter Stabilizer in Autopilot Storage, it upgrades Fuel Production to Advanced Fuel Production the way the mod's portmaster does, keeping the core, item and improvement. This replaces a building, so it needs **Allow removals** on.

## Random Assortment of Things

Founds your one Frontiers settlement on the best surveyed site once it would make at least 20,000 a month (by sending an expedition, which gets there 10 days plus a day a light-year after you pay; it's refunded if the site's gone by then), then builds Refining and a Trading Post there when they pay back within two years. The other facilities change how you play, so they're yours. Its Forge-Assisted Industry BP isn't allowed in Autopilot Storage.

## Station Augments

Picks the best set of augments each station can fit, scored by what each one actually does on that station (its own guns, fighters, shields and AI core). Output augments while things are calm, combat when the colony is threatened. Augments you installed yourself are never changed. Station Augments only lets you change them while docked, so from afar a refit crew makes the change 7 days later (at once while your fleet is in the system). Works on any mod's stations.

## Stations from other mods

Interstellar Imperium, Knights of Ludd and Ashes of the Domain stations count like vanilla ones everywhere (defense, cores, the plan's station step). With Interstellar Imperium you can pick the Imperium station line.

## Terraforming & Station Construction (Boggled)

Its terraforming projects are valued and started, with the prerequisite buildings built when needed. Some of its buildings are scored from estimates. See [Terraforming](terraforming.md#terraforming--station-construction-boggled).

## Grand.Colonies

Without it nothing is built past 12 buildings, since the colony screen can't show a 13th. With it, the extra upkeep past its limit is counted in each build's cost.

## Substance.Abuse

A Brewery is built only when Autopilot Storage holds a recipe (or a Recipe Book the colony is big enough for) and no Brewery is idle, then the recipe goes in.

## Volkov Industrial Conglomerate

Viral contamination treatment comes first, tier by tier, to stop a colony shrinking. Soil nanites only go where the farmland is vanilla, since other farmland crashes them.

## Roider Union

Dives and the Union HQ are valued by the deposits they can mine nearby.

## Planetary Shield: Access Control

The shield is valued as ground defense plus lower Pather interest, and a colony with a working shield and Pather cells keeps its port closed, since that's how the shield works.

## Unknown Skies, What We Left Behind, Astral Ascension

Their extra mining conditions count toward Mining, their water worlds count as water worlds, and planetless colonies (Astral Ascension) never get pushed past size 10 by hazard pay, since they crash there.

## United Aurora Federation

Its colony items are placed from what they actually do: the Dimensional Nanoforge, Servosync Pump, Modular Purifier and Garrison Transmitter. The Rice Cooker, Dimensional Stove and Access Router are never installed, since they cost more than they add or do nothing.

## Any other mod

- Deposits a mod registers in the game's own tables are scored like vanilla ones.
- Any mod's colony item the game knows how to install gets placed from Autopilot Storage.
- Mod buildings with no formula are measured on a throwaway test colony and scored from what they really do. See [How it builds](how-it-builds.md#mod-buildings-it-doesnt-know).
- Anything it can't support is listed in Diagnostics and left alone.

## Checked, nothing needed

Better Colonies, Supply Forging, LOST_SECTOR, Kon's Player Faction Bundles, Galactic Constellate, Tri-Tachyon Expansion, Luddic Enhancement, Hillock, Hazard Mining, Hiver Swarm and Adversary all work as they are.
