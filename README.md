# Auto Colonies

> [!WARNING]
> It's AI-written, so expect bugs. Report them in [issues](https://github.com/Mjolniar/auto-colonies/issues) with `starsector-core/starsector.log`, `saves/common/colony_autopilot_diagnostics.json.data` and your modlist.

Auto Colonies runs your colonies for you. You set a colony to Auto and it builds, places your items and AI cores, hires administrators and sets policies, all within a budget you pick. It works on its own and doesn't need any other mod, and it supports a lot of them if you have them.

## Install

Extract the [latest release](https://github.com/Mjolniar/auto-colonies/releases/latest) into `Starsector/mods/`. When you update, delete the old folder first, and your saves will keep working.

## Opening it

Open one of your colonies and click the Colony Autopilot button, or right-click Population & Infrastructure. A new game asks you three things first: Story or Normal mode, how expensive it should be, and how fast your empire should grow.

## Modes

Every colony has its own mode. Off leaves it alone. Advisor shows you its best picks beside the build menu, and you do the building. Approve asks before each build. Auto just builds within your budget without asking.

## Settings that matter

With LunaLib, the settings are in its menu (F3) under Auto Colonies, and without it they're on the colony panel under Settings. Balance sets how expensive everything is, from Easy to Hard. Profile sets how your empire grows, from Low profile to High profile. Spending sets how much of your income it uses, and Credit reserve is money it'll never touch. Everything else is on the Advanced tab, and [Settings](docs/settings.md) lists all of it.

## Story mode

In Story mode the Autopilot starts dormant. You find its core out in the sector, then feed it AI cores for bandwidth and spend that bandwidth on what it can do, tier by tier. You can take every fed core back on the Story page whenever you want, and you can switch story mode off any time without losing your progress. Once everything in Tiers I to IV is bought, a hidden Tier V opens where you pick any three of its upgrades for free.

## Removing it

Turn on Prepare for removal in LunaLib (General tab), confirm the dialog in your campaign, then save, quit and remove the mod. Your items and cores go back into each colony's normal storage first. [What it changes](docs/audit.md) has the details.

## Guides

- [Settings](docs/settings.md)
- [Supported mods](docs/supported-mods.md)
- [What it changes](docs/audit.md)
- [Patch notes](docs/changelog.md)

## Permissions

You can use it in modpacks, reuploads and forks without asking. Selling it isn't allowed ([LICENSE](LICENSE)).
