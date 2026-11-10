# Jonah's Colony Manager

> [!WARNING]
> **AI-generated.** The mod, docs and build checks were written by AI coding agents and checked offline (bytecode
> verification against every combination of the optional mods). Expect bugs. Report them as [issues](https://github.com/Mjolniar/jonahs-colony-manager/issues),
> ideally with `starsector-core/starsector.log`, `saves/common/colony_autopilot_diagnostics.json.data` and your modlist. If not, post the problem anyway.

I like lots of colonies with the limits off, and I kept getting in over my head managing them. So I had Claude Code make this. Flip a colony to **Auto** and it looks after itself, as long as you have the cash. The **Colony Autopilot** button is on the colony screen (or right click Population & Infrastructure).

## Quick version

- It builds, upgrades, places items and AI cores, hires administrators and handles policies, for a small fee per action.
- Every action costs something, and the mod leaves game balance alone.
- Settings are editable on the fly from the Colony Autopilot tab in the intel screen.
- Works with 20+ colony mods. None are required. Want one added? Put it in issues.

## Install

1. Grab the zip from the [latest release](https://github.com/Mjolniar/jonahs-colony-manager/releases/latest) and extract it into `Starsector/mods/`.
2. Enable **Jonah's Colony Manager** in the launcher.

Fine to add to an existing save. Don't remove it from a save that used it, because Autopilot Storage lives on your markets. MagicLib or TriOS will tell you about updates.

## Quick start

1. Open a colony and hit **Colony Autopilot**.
2. Pick a goal, or leave it on Balanced.
3. Set the mode to **Auto** and save the recommended setup.
4. Drop items and AI cores into **Autopilot Storage**. It ships them where they're needed, for a fee and travel time.

The [Getting started](docs/getting-started.md) guide covers it properly.

## What it does

- Builds and upgrades colonies within a budget you control.
- Places items and AI cores, and keeps colonies under the Hegemony's inspection radar.
- **Low profile** mode avoids inspections, Pather cells and mercenaries.
- Builds one colony into your capital, and a trade hub once you're making money.
- Plans for security and prices colony crises into what it builds.
- Spends big surpluses, then switches to an endgame economy.
- Runs research, terraforming, trade contracts and megastructures when their mods are installed.
- With Nexerelin it handles defense fleets and pirate strikes. Invasions are left to you.

## Coming in 3.0

- **Already built, being tested:** a smarter planner. One build queue for all your colonies that saves up for big builds and builds once per shortage.
- **Planned:** a money ladder for spare cash, auto-expansion, war and diplomacy, and a much shorter settings menu.
- A save made on 3.0 won't load in 2.45.x, so keep a backup.

Plans can still change. Everything planned: [roadmap](docs/roadmap.md).

## Guides

- **[Getting started](docs/getting-started.md)** - your first colony.
- **[How it builds](docs/how-it-builds.md)** - goals, plans, keeping control.
- **[Low profile](docs/low-profile.md)** - expanding quietly, and the cost.
- **[Your capital](docs/capital.md)** - the home port and trade hub.
- **[Money, surplus and the endgame](docs/money.md)** - budget, spending freely, autonomy.
- **[Items, AI cores and administrators](docs/items-and-cores.md)** - storage, inspections, governors.
- **[War and security](docs/war-and-security.md)** - threats, sieges, fleets, agents.
- **[Research](docs/research.md)** - research, projects, wonders, gates.
- **[Terraforming](docs/terraforming.md)** - special-world plans and surplus terraforming.
- **[Settings](docs/settings.md)** - every setting and what's recommended.
- **[Supported mods](docs/supported-mods.md)** - what it does with each mod.
- **[Roadmap](docs/roadmap.md)** - what's coming in 3.0.
- **[Patch notes](docs/changelog.md)** - every version, back to Jonah Tweaks.

Every rule is in [details.md](docs/details.md). It's long. For the code side: [Auditing the mod](docs/audit.md), the [Source guide](docs/source-guide.md) and [Automating Starsector colonies](docs/automation-primer.md).

## Permissions

Use it however you want: modpacks, reuploads, forks, no need to ask or credit me. Just don't sell it. See [LICENSE](LICENSE).

## Building it yourself

JDK 17, sources in `src/`. Compile with `javac --release 17` against the core jars (`starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `log4j-1.2.9.jar`, `json.jar`, `lwjgl.jar`, `lwjgl_util.jar`) plus any optional mod jars, then `jar cf jars/jonahtweaks.jar -C build data`.
