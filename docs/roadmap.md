# 0.3.0 roadmap

What the next major version changes, and how far along each part is. Current version: 0.2.45.2. 0.3.0 has no release date yet, and plans can still change before release.

## Status

| Part | Status |
|---|---|
| Save format, reliability, stability checks, performance | Built, in testing |
| Build planner | Built, in testing |
| Money ladder | Planned |
| Auto-expansion | Planned |
| War and diplomacy | Planned |
| Settings menu | Planned |

## Save format and reliability

Built, in testing.

- Saved data uses fixed short names, so later updates can rename code without breaking saves.
- Saves from 0.2.45.x load in 0.3.0. Saves made in 0.3.0 don't load in 0.2.45.x.
- Each daily step runs on its own. If one fails, it's logged in Diagnostics and the other steps still run.
- Anything that can lower stability first checks that the colony stays at or above the minimum stability setting (default 5, never under 3).
- Plans and scores are worked out once a day and reused until something changes.

## Build planner

Built, in testing. See [How it builds](how-it-builds.md).

- Builds, upgrades and projects for all colonies go into one ranked list.
- A colony's construction slot goes to the option that earns the most per day of construction.
- A build has to pay back its cost within 36 months. That grows to 120 months as spare credits go up.
- Buildings the mod removes aren't rebuilt at that colony for a year. This part is already in 0.2.45.2.
- Shortages count buildings already under construction, so two colonies don't both build for the same shortage.
- The budget uses the median colony income of the last 3 months.

## Money ladder

Planned. See [Money](money.md).

- Five money tiers, T0 (Tight) to T4 (Endgame), based on colony income and free credits.
- Spare money goes to a list of uses in order: stockpiles, growth, military, ships, expansion, economy. You can change the order.
- Ships are bought from your Doctrine list at normal prices. Purchases stop at 6 unclaimed hulls at the capital.
- Ship purchases and expansion start in Recommend mode: the mod proposes, and you approve.

## Auto-expansion

Planned. Needs Nexerelin.

- Uses Nexerelin's colony expeditions.
- One on/off setting, off by default. It asks for confirmation before the first expedition.
- Looks for targets in your own systems first. Normal costs and travel time apply.
- Doesn't go past the colony limit unless an administrator is available.
- Planned default rate: one new colony per 120 days at tier T2, one per 60 days at T3.

## War and diplomacy

Planned. Needs Nexerelin. See [War and security](war-and-security.md).

- One empire-wide war level: Off, Defend, Retaliate, Hold them down, Conquer, Scorched. Retaliate matches what 0.2.45.x does and is the default.
- A stance for each faction: Ally, Friendly, Neutral, Auto, Rival, Enemy, Destroy. Auto matches what 0.2.45.x does.
  - Enemy stays at war and turns down ceasefires.
  - Destroy invades until the faction has no markets left.
- Retaking your own lost colonies is on in new games and off in saves from 0.2.45.x.
- Bombarding capitals is off by default and needs a confirmation.
- A Stand down button sets the war level to Defend and cancels operations that haven't launched yet, with a full refund.
- Old saves carry over:
  - the old war mode becomes the matching war level;
  - faction priorities become stances (High to Friendly, Normal to Auto, Ignore to Rival).

## Settings menu

Planned. See [Settings](settings.md).

- About 20 main settings in 6 groups (14 without other mods), plus an Advanced section. 0.2.45.x has 76 rows.
- Shorter descriptions, with the details moved to hover text.
- New pages for Factions, War and Expansion.
- First-time setup is 5 rows. Existing settings carry over.

## Updating

Back up your save before updating to 0.3.0. A save made in 0.3.0 can't be loaded in 0.2.45.x.

[Back to the README](../README.md)
