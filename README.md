# Auto Colonies

> [!WARNING]
> It's AI-written, so expect bugs. Report them in [issues](https://github.com/Mjolniar/auto-colonies/issues) with `starsector-core/starsector.log`, `saves/common/colony_autopilot_diagnostics.json.data` and your modlist.

- Auto Colonies is colony management for Starsector 0.98a and 0.98.5a. Set a colony to Auto and it'll build, place items and AI cores and handle policies, all within your budget.

## Install

- To install, extract the [latest release](https://github.com/Mjolniar/auto-colonies/releases/latest) zip into `Starsector/mods/` and enable it.
- When you update, delete the old Colony Manager folder in `mods/` first. Your saves will keep working.
- Saves made on 0.3.0 won't load in 0.2.45.x.
- To take it out of a save, turn on Prepare for removal (LunaLib or the Mod features page), save, and then pull the mod ([Removing it](docs/audit.md#removing-it)).
- MagicLib and TriOS don't show 0.3.0.0 as an update, so grab it from the latest release link.

## Quick start

1. Open a colony and click Colony Autopilot.
2. Pick a goal, or keep Balanced.
3. Set the mode to Auto and save the 5-row setup.
4. Put your items and AI cores into Autopilot Storage.

## What it does

- It runs a ranked build queue and handles items, AI cores, administrators, policies and defense, and it can found new colonies too (optional).
- It supports 45+ mods, and none of them are required.
- Story mode is optional. The Autopilot starts dormant, and then it runs the abilities you buy with the AI cores you give it ([Story mode](docs/story-mode.md)).

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
- [Story mode](docs/story-mode.md)
- [Supported mods](docs/supported-mods.md)
- [0.3.0 overview](docs/roadmap.md)
- [Patch notes](docs/changelog.md)

Every rule is in the pages listed above. For the code, there's [Auditing the mod](docs/audit.md), [Source guide](docs/source-guide.md), [Building and checking](docs/source-guide.md#building-and-checking) and [Automating Starsector colonies](docs/automation-primer.md).

## Permissions

- You're free to use it in modpacks, reuploads and forks.
- You don't need to ask permission or give credit.
- Selling it isn't allowed.
- The license is in [LICENSE](LICENSE).
