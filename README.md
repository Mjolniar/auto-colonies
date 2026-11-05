# Jonah's Colony Manager

> [!WARNING]
> **AI-generated.** Mod, docs and build checks written by AI coding agents. Compiled and checked offline (bytecode
> verification against every combination of the optional mods). Expect bugs. Report them as [issues](https://github.com/Mjolniar/jonahs-colony-manager/issues) with
> `starsector-core/starsector.log` and `saves/common/colony_autopilot_diagnostics.json.data` if you have the time/willingness to dig logs, otherwise it's chill to just throw it in issues anyway, whatever problem is occurring with the mod installed. It would be good to know your modlist if you do have an issue to see what is incompatible with the mod so I can get that fixed.

This is a mod I had Claude Code create for me due to always liking to have tons of colonies with lots of buildings, limits off, etc, but always getting way in over my head with management.

The goal of the mod is to let you simply flick the automatic switch on your colonies and have them take care of themselves, so long as you have the cash to keep them improving. There's a Colony Autopilot button right on the colony screen (or right click Population & Infrastructure, it's the bottom option). There's several modes to choose from, tons of options involving governors, buildings, how you want the colony to develop, and so on!

By default, most of the settings will try to optimize your colonies to protect them and make them profitable, for a small convenience fee per building placed and action taken. To have it add items to your colonies, there is a colony storage you can access at any of your owned colonies where you can put all colony augment items (nanoforges, AI cores, etc.) - this has extensive modded support as well. The manager will automatically work out a plan for each planet based on what it has to work with, and you can set each colony's goal (money, security, war production and so on) to steer it however you want. I highly suggest going through the options to your taste, and they're editable on the fly from the Colony Autopilot tab in the intel screen.

Once you're rich it doesn't just sit on the money either. Past a certain point it starts pouring the extra cash into your best worlds, then into every colony, and once you're making a stupid amount a month it stops caring about money entirely and builds for production, research and military instead. With Nexerelin it'll also handle defense fleets, pirate base strikes and hitting back at whoever raids you, without ever trying to invade anyone.

This mod is currently working with most of the colony mods I could find on the main Starsector mod page, but if you would like one added to its functionality please put it into the issues tab and I'll do it asap.

## Install

1. Grab the zip from the [latest release](https://github.com/Mjolniar/jonahs-colony-manager/releases/latest) and extract it into `Starsector/mods/`.
2. Enable **Jonah's Colony Manager** in the launcher. No required mods, everything else it supports is optional.

It's fine to add to an existing save. Don't remove it from a save that used it though, the Autopilot Storage tab lives on your markets.

## Quick start

1. Open a colony and hit the **Colony Autopilot** button.
2. Pick a goal (Balanced, Money, Security, War production, Rapid growth, Self-sufficiency) or leave it on Balanced.
3. Set the mode to **Auto**. The first time you do this it opens setup with the recommended settings already filled in. Just save it, they're meant to work without touching anything.
4. Drop any colony items and AI cores you want it to use into **Autopilot Storage** at any of your colonies.

That's really it. The [Getting started](docs/getting-started.md) guide walks through it properly, including the fast buildout it offers when you found your first colony.

## What it does

- Builds and upgrades your colonies on its own, within a budget you control, following a plan for each planet.
- Places your colony items and AI cores where they do the most, and keeps colonies under the Hegemony's inspection radar.
- Hires and places administrators, handles hazard pay, free ports and other policies.
- Reacts to threats: plans for security when a colony is in danger, only builds defenses during a siege, and prices colony crises into everything.
- Spends big surpluses for you instead of letting money pile up, and switches to an endgame economy once money stops mattering.
- Runs research, terraforming, trade contracts, megastructures and a lot more when the mods for them are installed.
- Works with mod buildings it doesn't know by measuring them on a throwaway test colony first.
- Doesn't change any balance. It only manages your colonies.

## Guides

- **[Getting started](docs/getting-started.md)** - install, the two panels, your first colony, and what to do if it isn't building anything.
- **[How it builds](docs/how-it-builds.md)** - goals, plans, how it picks what to build, and how to keep control.
- **[Money, surplus and the endgame](docs/money.md)** - the budget, the focus world, spending freely, the endgame economy and autonomy.
- **[Items, AI cores and administrators](docs/items-and-cores.md)** - Autopilot Storage, where things go, inspections and governors.
- **[War and security](docs/war-and-security.md)** - threat, sieges, colony crises, defense and war fleets, agents.
- **[Research](docs/research.md)** - Vaults of Knowledge research, special projects, wonders and gates.
- **[Terraforming](docs/terraforming.md)** - special-world plans and surplus terraforming.
- **[Settings](docs/settings.md)** - every setting, what it does and what's recommended.
- **[Supported mods](docs/supported-mods.md)** - what it does with each mod you have.
- **[Patch notes](docs/changelog.md)** - every version so far, all the way back to when it was Jonah Tweaks.

If you want every rule written down, that's in [details.md](docs/details.md). It's long. If you want to dig into the code, there's a [source guide](docs/source-guide.md).

## Building it yourself

JDK 17, sources in `src/`:

```
javac --release 17 -cp "<core jars>;<optional mod jars>" -d build <all .java files under src>
jar cf jars/jonahtweaks.jar -C build data
```

Core jars: `starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `log4j-1.2.9.jar`, `json.jar`, `lwjgl.jar`, `lwjgl_util.jar`. Optional: Terraforming Made Easy, Industrial Evolution, Nexerelin, Random Assortment of Things, Station Augments (plus LazyLib's Kotlin runtime and LunaLib).
