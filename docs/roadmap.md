# 0.3.0 overview

What 0.3.0.0 changed, where each part is described, and what is still open. Full list: [patch notes](changelog.md).

## Parts

| Part | Status | Guide |
|---|---|---|
| Save format, reliability, one stability rule, performance | In 0.3.0.0 | [Source guide](source-guide.md#whats-saved) |
| Build planner: one queue, payback horizon, removal marks | In 0.3.0.0 | [How it builds](how-it-builds.md) |
| Money tiers and the money ladder: stockpiles, growth, military, shipyard, economy, wonders | In 0.3.0.0 | [Money](money.md) |
| Auto-expansion (New colonies), off by default | In 0.3.0.0 | [New colonies](expansion.md) |
| War level, faction stances, diplomacy, conquest, scorched earth (Nexerelin) | In 0.3.0.0 | [War and security](war-and-security.md) |
| Settings menu: 20 main rows, Advanced, Factions, War and Expansion pages | In 0.3.0.0 | [Settings](settings.md) |

## Defaults

| Setting | New game | Save from 0.2.45 |
|---|---|---|
| War level | Retaliate | The level matching War fleets (Protect and strike back: Retaliate; Protect: Defend; Off: Off) |
| Retake your lost colonies | On | Off |
| Faction stances | All Auto | From faction priorities (High: Friendly, Normal: Auto, Ignore: Rival) |
| Bombardment | Not authorized | Not authorized |
| New colonies | Off | Off |
| What to buy | Balanced | Balanced |
| Shipyard and Expansion rungs | Recommend only | Recommend only |
| Payback horizon | 36 months, up to 120 | 36 months, up to 120 |

## Open items

- The plan's own TME projects and paid item, core and administrator moves still spend in their own steps. They check the planner's savings first. Moving them into the planner's queue is planned for 0.3.0.x.
- The 0.2.45 build engine stays behind a hidden switch (`legacyPlanner`) for 0.3.0.x.
- 0.3.0.0 passed the offline checks and a short in-game test on one save ([patch notes](changelog.md#not-tested-in-a-running-game-yet)). Not tested in a game yet (they need a Nexerelin war, a commission or a new colony): player-faction saturation bombardment, Nexerelin colony fleets for the player, conquered-colony rebellions, invasions while commissioned, ceasefire answers, Stand down refunds, Autopilot crew founding. Report problems in the [issues](https://github.com/Mjolniar/auto-colonies/issues).

## Updating

Back up your save before updating to 0.3.0. A save made in 0.3.0 can't be loaded in 0.2.45.x.

[Back to the README](../README.md)
