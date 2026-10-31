# Jonah's Colony Manager

> [!WARNING]
> **AI-generated.** Mod, docs and build checks written by AI coding agents. Compiled and checked offline (bytecode
> verification against every combination of the optional mods); the newest features aren't tested in game yet. Expect
> bugs. Report them as [issues](https://github.com/Mjolniar/jonahs-colony-manager/issues) with
> `starsector-core/starsector.log` and `saves/common/colony_autopilot_diagnostics.json.data`.

This is a mod I had Claude Code create for me due to always liking to have tons of colonies with lots of buildings, limits off, etc, but always getting way in over my head with management. 
The goal of the mod is to let you simply flick the automatic switch on colonies and have them take care of themselves, so long as you have the cash to keep them improving. There's several modes to choose from, tons of options involving governors, buildings, how you want the colony to develop, and so on!

## Features

- **Modes per colony:** Off, Advisor, Approve, Auto. Auto builds within your budget.
- **Goals:** Balanced, Money, Security, War production, Rapid growth, Self-sufficiency.
- **Plans:** target layout from planet, hazards, resources.
- **Budget:** monthly limit, per-project cap, credit reserve, convenience fees.
- **Threat response:** Security at 70% threat; back below 65% after 60 days.
- **Items and AI cores:** placed from Autopilot Storage only.
- **Administrators:** idle ones assigned; hiring optional.
- **Mod buildings:** measured on a probe colony, then scored like vanilla.
- **Diagnostics:** every building rated Supported, Measured, Analyzed or Quarantined.
- **Sticky autopilot:** U keeps combat autopilot on.
- **Battle size:** fixed at 2000.

## Integrations

All optional. Each loads only when its mod is enabled.

| Mod | Adds |
|---|---|
| Terraforming Made Easy | Special-world plans, terraforming |
| Industrial Evolution | Ambassadors, edicts, VPC chips, courier shipping |
| Nexerelin | Emergency defense fleets |
| Emergent Threats | Antimatter Stabilizer conversion |
| Ashes of the Domain | Research choices, research locks, upgrade paths, starter buildings, industry synergies, management styles, trade contracts, megastructures (Coronal Hypershunt, Pluto, Nidavelir), AotD colony items |
| Random Assortment of Things | Frontiers settlement: found and develop |
| Station Augments | Best augment loadout per station, within its AP |
| Terraforming & Station Construction | Terraforming projects, prerequisite buildings, building estimates |
| Grand.Colonies | Cramped-upkeep cost on builds past the limit |
| Substance.Abuse | Brewery built for recipes in storage |
| Volkov Industrial Conglomerate | Viral contamination treatment, soil nanites |
| Roider Union | Dives and Union HQ remote mining |
| Planetary Shield: Access Control | Shield value, closed-port rule |
| Unknown Skies, What We Left Behind, Astral Ascension | Mining bonuses, water worlds, growth limit |
| Any mod | Deposit conditions and colony items through the game's own tables; 12-building screen limit |

## Install

1. Download and extract into `Starsector/mods/`.
2. Enable **Jonah's Colony Manager** in the launcher. No required mods. (Formerly Jonah Tweaks; same mod id, so existing saves keep working.)

## Use

- **Colony:** colony screen, right-click Population & Infrastructure, **Colony Autopilot...**
- **Empire:** Intel, Colonies, **Colony Autopilot**.

## Notes

- Advisor is the default; nothing is built until you choose Approve or Auto.
- Don't remove from a save that used it (Autopilot Storage is a market tab).
- Full rules: [docs/details.md](docs/details.md).

## Build

JDK 17, sources in `src/`:

```
javac --release 17 -cp "<core jars>;<optional mod jars>" -d build <all .java files under src>
jar cf jars/jonahtweaks.jar -C build data
```

Core jars: `starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `log4j-1.2.9.jar`, `json.jar`, `lwjgl.jar`, `lwjgl_util.jar`. Optional: Terraforming Made Easy, Industrial Evolution, Nexerelin, Random Assortment of Things, Station Augments (plus LazyLib's Kotlin runtime and LunaLib).
