# What each supported mod adds

Every integration is optional and turns on by itself when its mod is enabled. Any mix of these mods works. The empire panel's **Diagnostics** shows each integration's status (OK, newer version, partly off, off).

Unless a section says otherwise, the manager only acts on colonies in **Auto**. Approve colonies get a proposal, Advisor colonies a recommendation. All spending follows your budget, per-project cap and credit reserve.

Back to [Getting started](getting-started.md). The full rules are in [details.md](details.md).

---

## Terraforming Made Easy

- **Special worlds in the plan.** A colony's plan can aim for a reachable Forge, Fortress, Paradise, Hive, Death or Waste world. The steps come from TME's own requirement table, resolved back through the projects that create each condition.
- **Terraforming projects.** It uses TME's own project lists and build checks, and starts projects the way TME's dialog does. It plans one project ahead, plus a two-step look-ahead for swapping a deposit for a richer one.
- **Side effects are counted.** Hazards a project adds are scored, and conditions TME removes as conflicts are simulated before a project is picked.
- Terraforming may use up to **Terraforming share** of the monthly budget (default 40%).

## Industrial Evolution

- **Ambassadors.** Hires an ambassador for an empty Embassy the way IndEvo's dialog does, and installs one waiting in a colony's storage.
  - **Ambassador focus:** worst relations first, strongest factions, nearest neighbors, or priority list only.
  - **Per-faction priority:** High, Normal or Ignore.
  - **Stop raising at:** the reputation level where it stops.
- **Senate edicts.** Enacts the edict that suits the colony. Harsh edicts (forced labour, cleansing, single-child, labour limits) are never automated.
- **VPC chips.** Installs chips from Autopilot Storage. **Commodity Forge may use up chips** decides whether the Forge can consume them.
- **Specialized Mining and Refining.** Switches to the specialty worth the most, from the colony's deposits and your empire's shortages.
- **Supply shipping (Courier Port).** When an Auto colony runs short, it sets up a monthly Courier contract from another colony's stockpile and cancels it once the shortage is gone. IndEvo's normal shipping cost applies.
- Each part can be switched off in **Settings**.

## Nexerelin

- **Emergency defense fleets.** When an Auto colony's system is targeted by a hostile raid, invasion or crisis fleet, or hostile fleets there outweigh its defenses, it requests a Defense fleet the way Nexerelin's fleet request dialog does. This may go over the monthly budget, since it's an emergency, but never under the credit reserve.
- **Agents** (Defense in the recommended settings, off before setup; **Settings**, then **Agents (Nexerelin)**):
  - **Defense:** Counter-Insurgency on a rebellion, Infiltrate Cell on an active Pather cell, and Find Pirate Base for a hidden base, at the closest colony that needs it. Then Raise Relations with a faction running operations against your colonies while not at war with you. Above Neutral, its AI can't start new operations and drops the running ones.
  - **Defense and offense:** adds sabotage of military industries. It starts with the market an invasion or raid against you comes from. It only targets factions already at war with you; never allies, factions in a ceasefire, or your commissioner, because a detected operation can start a war. It needs 60% estimated success or better and runs one operation per enemy faction.
  - **Hiring:** a level-1 agent for 12,000, only while there's work for one, up to **Agents to keep** and Nexerelin's own limit. Hybrid first, then a Saboteur when offense is on. Nexerelin pays the salary.
  - Only agents the manager hired get orders. The empire panel lists them, plus the enemy operations running against your colonies, which the game doesn't show anywhere else.
- **Threat estimate.** Raids, invasions, rebellions and hostile neighbors raise a colony's threat; at 70% it's planned as Security.

## Emergent Threats (IX Revival)

- **Antimatter Stabilizer.** With a stabilizer in Autopilot Storage, it upgrades Fuel Production to Advanced Fuel Production the way the mod's portmaster does. The AI core, colony item and improvement carry over. This replaces a building, so it needs **Allow removals** on.

## Ashes of the Domain

Covers Building Menu Overhaul, Vaults of Knowledge and Theory of Toolbox.

- **Research locks and upgrade paths.** It asks Building Menu Overhaul the same questions the build menu does, so nothing locked or hidden is ever built.
  - Upgrades like Monoculture Plots to Farming, Heavy Industry to Orbital Works, and Spaceport to Megaport are started the way the menu starts them.
  - Locked plan steps show what they need.
- **Starter buildings.** Monoculture Plots, Extractive Operation, Smelting, Light Production and Trade Outpost are scored with estimated effects.
- **Industry synergies.** A build's value includes the synergies it starts or ends (Ore-To-Core, Defensive Network, Rapid Transportation and 16 more), modelled from VoK's code. The colony panel shows active synergies and those one building away.
- **Management styles** (Colony Development). New buildings are scored with the style's real effects: Centralized Urban Core, Distributed Regional Network, Bore City, Archeo-Site, Floating City. On a Floating City, nothing is built while fuel would run short. The style itself is never changed.
- **Research** (Advisor, Auto or Off; Auto in the recommended settings). Each tech is valued by what it unlocks and ranked by value per research day; the empire panel lists the best six.
  - Auto starts the best databank-only tech, moves databanks from Autopilot Storage, and makes Sophia Anderson head of R&D if you have her.
  - Your research queue is never touched.
- **Trade contracts** (Advisor, Auto or Off; Auto in the recommended settings). It takes offers only when ToT's own figures leave 15% to spare, and only when they pay more than exporting would. Shortfalls count as shortages when scoring buildings.
- **Coronal Hypershunt colony.** Nothing is built there; AotD allows no buildings. The wormhole range is kept at the smallest that reaches your receivers. The Coronal Collector and Wormhole are restored when every resource is in surplus and the cost fits your budget. The empire panel lists where a receiver would pay off.
- **Pluto Mining Station.** The laser runs at full strength and ore goes to your restored forges. Sections are restored in order.
- **Nidavelir Complex.** Manpower goes into the Nexus Core one point at a time while its inputs are supplied. The Nexus Core is restored.
- Any megastructure setting you change yourself is left alone until you click **Let Autopilot manage it**.
- **Colony items.** The Tenebrian Nanoforge, Synchrotron, Atmospheric Driver, Catalytic Core and Turing Engine are placed like vanilla items.
- **Underworld.** A colony with one keeps its free port.
- **Left to you:** Bifrost gates, Grand Wonders, black-site projects, custom production, and choosing a management style.

## Random Assortment of Things (Frontiers)

- **Settlement** (Advisor, Approve, Auto or Off; Auto in the recommended settings). Founds your one settlement on the best surveyed site once it makes at least the threshold (default 20,000 a month), the way RAT's own option does.
- Builds Refining and Trading Post when they pay back within 2 years. The other facilities change how you play, so they're yours.

## Station Augments

- **Augment loadouts.** Picks the set of augments worth the most within each station's augment points. Installing is free and can be undone. Scored with the colony's goal: accessibility, upkeep, output, ground defense and the mod's combat rating.
- **Station augments** setting: follow colony mode, recommend only, or off. Each colony panel has an Apply button.
- Augments you installed are never changed, and one you remove isn't added back.
- **Left to you:** augments that use up an item, ones that trade a drawback for points, and Economy Conversion while the colony plans for Security.

## Terraforming & Station Construction (Boggled)

- **Terraforming projects.** Each project TASC offers is valued by the conditions it leaves. Auto starts the best one; the colony panel lists the best four with Start. A running project is never cancelled.
- **Prerequisites.** The Atmosphere Processor and Stellar Reflector Array are built when a worthwhile project waits only for them.
- **Building estimates.** Domed Cities, Genelab, CHAMELEON, Mesozoic Park and the Kletka Simulator are scored with estimates. Plain output buildings are measured on the probe colony.
- **Left to you:** station construction (it needs your fleet on site), and TASC buildings that can't be safely test-built.

## Grand.Colonies

- **More than 12 buildings.** Without Grand.Colonies nothing is built past 12, since the colony screen can't show a 13th. With it, the extra upkeep for each building past its threshold (read from its LunaLib settings) is added to that build's cost.

## Substance.Abuse

- **Brewery.** Built only when Autopilot Storage holds a recipe (or a Recipe Book the colony is big enough for) and no Brewery stands idle. The recipe is then installed. Drinks are valued at a quarter of base price, since few factions buy them.

## Volkov Industrial Conglomerate

- **Viral contamination.** The anti-EVC treatment comes first, tier by tier, to stop a contaminated colony shrinking.
- **Soil nanites** go only where the farmland is vanilla, since other farmland crashes them.

## Roider Union

- **Dives and Union HQ** are valued by the deposits they mine within 1 or 6 light-years, which a probe colony can't see.

## Planetary Shield: Access Control

- **The shield** is valued as +300 ground defense and -4 Pather interest. A colony with a working shield and Pather cells keeps its port closed, since the shield only works that way.

## Unknown Skies, What We Left Behind

- **Extra mining.** Luminescent Clouds (+3 volatiles) and Fractured World (+5 ore and transplutonic ore) count toward Mining. The new water worlds count as water worlds.

## Astral Ascension

- **Planetless colonies** crash past size 10, so hazard pay never pushes one there.

## Any other mod

- **Deposits.** Deposit conditions a mod registers in the game's own tables are scored like vanilla ones.
- **Colony items.** Any mod's item with an effect in the game's item table is placed from Autopilot Storage, moved as the exact stack.
- **Mod buildings.** Buildings with no known formula are measured on a throwaway probe colony and scored from what they really do. A building that misbehaves on the probe is quarantined and never touched.
- Anything the manager can't support is listed in Diagnostics and left alone.

## Checked, nothing needed

Better Colonies, Supply Forging, LOST_SECTOR, Kon's Player Faction Bundles, Galactic Constellate, Tri-Tachyon Expansion, Luddic Enhancement, Knights of Ludd, Interstellar Imperium, Hillock, Hazard Mining, Hiver Swarm and Adversary work as they are. Their buildings are measured, or their threats already count in the threat estimate.

---

## Companion mod: Jonah's Easy Sector

Balance changes aren't part of Colony Manager. Battle size, sticky combat autopilot, level cap, XP, fleet cap, upkeep, wages, bounties, reputation, topography and colony limit are in [Jonah's Easy Sector](https://github.com/Mjolniar/jonahs-easy-sector). Each change there has its own switch.
