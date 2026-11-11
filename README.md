# Jonah's Colony Manager

> [!WARNING]
> **AI-generated.** The mod, docs and build checks were written by AI coding agents and checked offline (bytecode
> verification against every combination of the optional mods). Expect bugs. Report them as [issues](https://github.com/Mjolniar/jonahs-colony-manager/issues),
> ideally with `starsector-core/starsector.log`, `saves/common/colony_autopilot_diagnostics.json.data` and your modlist. If not, post the problem anyway.

Colony management for Starsector 0.98a. A colony set to **Auto** builds, places items and AI cores and handles policies within your budget.

## Quick version

- Each action costs the game's price plus a small fee. Remote actions take travel time. Game balance is unchanged.
- Settings: 20 main rows on the intel screen's Colony Autopilot tab, editable any time.
- Optional: founding new colonies (off by default) and, with Nexerelin, war up to invasions.
- Supports 20+ colony mods, none required. Ask for mod support in issues.

## Install

Extract the [latest release](https://github.com/Mjolniar/jonahs-colony-manager/releases/latest) zip into `Starsector/mods/` and enable **Jonah's Colony Manager** in the launcher.

Can be added to an existing save. Removing it breaks saves that used it (Autopilot Storage is on your markets). MagicLib or TriOS show updates.

Updating from 0.2.45.x: back up your save. A 0.3.0 save does not load in 0.2.45.x. Settings carry over ([patch notes](docs/changelog.md)). Version numbers start with 0 since 0.3.0.0, so MagicLib and TriOS do not show 0.3.0.0 as an update to 2.45.2. Get it from the latest release.

## Quick start

1. Open a colony and click **Colony Autopilot** (or right click Population & Infrastructure).
2. Pick a goal, or keep Balanced.
3. Set the mode to **Auto** and save the 5-row setup as is (recommended settings).
4. Put items and AI cores into **Autopilot Storage**. It ships them where needed.

More: [Getting started](docs/getting-started.md).

## What it does

- **Building**: one ranked queue for all colonies. A build must pay back within 36 months (up to 120 as surplus grows). A building taken down is not rebuilt there for a year.
- **Items and people**: keeps colonies under the Hegemony's inspection line. Hires and places administrators.
- **Money ladder**: money tiers, Tight to Endgame. Spare money buys, in your order: stockpiles, hazard pay, military upgrades, Doctrine ships (Recommend only by default), new colonies, economy buildings, wonders.
- **New colonies** (optional): founded by Nexerelin colony fleet or Autopilot crew, at cost, with an administrator past the colony limit.
- **War** (Nexerelin): War level Off to Scorched, default Retaliate: 0.2.45's strike back plus Retake of colonies that were yours (off on saves from 0.2.45). Faction stances Ally to Destroy, default Auto. War fleets act in every colony mode.
- **Capital**: home port; trade hub once you make money.
- **Security**: plans for threats and colony crises. Keeps Auto colonies off 0 stability.
- **Low profile** (optional): fewer inspections, Pather cells and mercenaries. Earns less.
- **Mods**: research, terraforming, trade contracts and megastructures.

## Screenshots

<!-- Screenshot placeholders for 0.3.0.0. Add the files to docs/images/ and remove the comment markers.
![Settings page with the 20 main rows](docs/images/settings-0.3.0.png)
![Colony panel, Plan and builds, with scores](docs/images/colony-builds-0.3.0.png)
![Budget panel with the money tier and the ladder line](docs/images/budget-ladder-0.3.0.png)
![Expansion page](docs/images/expansion-0.3.0.png)
![War page](docs/images/war-0.3.0.png)
![Factions page](docs/images/factions-0.3.0.png)
-->

## Guides

- [Getting started](docs/getting-started.md)
- [How it builds](docs/how-it-builds.md)
- [Low profile](docs/low-profile.md)
- [Your capital](docs/capital.md)
- [Money, surplus and the endgame](docs/money.md)
- [New colonies](docs/expansion.md)
- [Items, AI cores and administrators](docs/items-and-cores.md)
- [War and security](docs/war-and-security.md)
- [Research](docs/research.md)
- [Terraforming](docs/terraforming.md)
- [Settings](docs/settings.md)
- [Supported mods](docs/supported-mods.md)
- [0.3.0 overview](docs/roadmap.md)
- [Patch notes](docs/changelog.md)

Every rule: [details.md](docs/details.md). Code: [Auditing the mod](docs/audit.md), [Source guide](docs/source-guide.md), [Building and checking](docs/source-guide.md#building-and-checking), [Automating Starsector colonies](docs/automation-primer.md).

## Permissions

Use it however you want: modpacks, reuploads, forks, no need to ask or credit me. Just don't sell it. See [LICENSE](LICENSE).
