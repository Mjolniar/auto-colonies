# Getting started

## Quick version

- Colonies start in **Advisor** (recommendations only). **Approve**: one proposal at a time to approve or reject. **Auto**: builds within your budget.
- The first switch to Approve or Auto opens a 5-row setup. The rest uses recommended settings.
- With Nexerelin, emergency defense fleets, agents and war fleets act in every colony mode. Settings page: **War** (Playstyle), **Agents** (Security and diplomacy), **Emergency defense fleets** (Advanced, War).

## Install

[Latest release](https://github.com/Mjolniar/jonahs-colony-manager/releases/latest). Install steps: [README](../README.md#install). Back up 0.2.45.x saves first. From 0.2.45.x, War keeps the level matching your War fleets setting; Retake and Auto-expansion start off ([Saves from 0.2.45](changelog.md#saves-from-0245)).

## Panels

- **Colony panel** (one colony): **Colony Autopilot** button on the colony screen, or right click Population & Infrastructure.
- **Empire panel** (all colonies): Intel screen, **Colony Autopilot** filter. Pages: Colonies, Autopilot Storage, Colony crises, Expansion, War (Nexerelin), Mod features, Settings, Factions, Log, Diagnostics.

## First colony

1. On founding, pick **Full expansion** or **[Low profile](low-profile.md)**, and whether to do a [fast buildout](#fast-buildout).
2. Pick a goal: Balanced (default), Money, Security, War production, Rapid growth or Self-sufficiency ([How it builds](how-it-builds.md)).
3. Plan steps show Done, Next or Planned.
4. In Advisor, build by hand from **Recommended builds**.

## First-time setup

| Row | Recommended |
|---|---|
| Spending | Normal: 50% of net income and 15% of savings a month |
| Extras | Auto: mod buildings and features |
| New colonies | Off ([New colonies](expansion.md)) |
| War (Nexerelin) | Retaliate ([War and security](war-and-security.md)) |
| Credit reserve | A quarter of your credits, 50,000-500,000. Not spent by Autopilot. |

All rows and choices: [Settings](settings.md).

## Fast buildout

Offered at your first colony, with a credit floor: 250,000, 100,000, 500,000 or 1,000,000. If accepted, the colony goes to Auto and each day starts the fastest payback (money only) with credits above the floor. After 30 days in a row of profit it returns to the normal budget.

The colony panel can change the floor or stop it. Colonies losing money get a **Start fast buildout** button.

## More colonies

New colonies start as Balanced and Advisor. Change them with **Next goal** and **Next mode** (empire panel, colony list). Past the colony limit, an administrator is placed or hired. Autonomy is not used. Auto-expansion: [New colonies](expansion.md).

## Troubleshooting

If Auto builds nothing, check the budget line on the Colonies page. Usual causes of a zero line (Settings, Advanced, Budget):

- **Budget type** is Share of net income and net income is negative. Use **Automatic**.
- **Pause on loss** is on.
- Credits are under the **credit reserve**.

**Reset to recommended** fixes all three. Builds that miss the payback months (36 normally) score 0 or less and are not started.

**Log** and **Diagnostics** show what waits, why, and what is off. Bug reports: [issues](https://github.com/Mjolniar/jonahs-colony-manager/issues), with the files the [README](../README.md) lists.

Next: [How it builds](how-it-builds.md), or [Settings](settings.md).
