# Jonah Tweaks: Colony Autopilot

> [!WARNING]
> **AI-generated.** Mod, docs and build checks written by AI coding agents. Compiled and checked offline (bytecode
> verification against every combination of the optional mods); the newest features aren't tested in game yet. Expect
> bugs. Report them as [issues](https://github.com/Mjolniar/starsector-colony-autopilot/issues) with
> `starsector-core/starsector.log` and `saves/common/colony_autopilot_diagnostics.json.data`.

Colony management automation for Starsector 0.98a-RC8. Works alone or with any mix of the supported mods.

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

## Install

1. Download and extract into `Starsector/mods/`.
2. Enable **Jonah Tweaks** in the launcher. No required mods.

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
