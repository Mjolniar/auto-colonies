# Getting started

## Quick version

- Extract the zip into `Starsector/mods/` and enable **Jonah's Colony Manager**.
- Every colony starts in **Advisor**, which only recommends. Nothing builds until you pick Approve or Auto.
- Found a colony, open its panel, pick a goal, then set the mode.

## Install

1. Download the [latest release](https://github.com/Mjolniar/jonahs-colony-manager/releases/latest) and extract it into `Starsector/mods/`.
2. Enable it in the launcher. Support for other mods turns on by itself.
3. Start a new game or load a save. Adding it mid-game is fine. Removing it from a save that used it breaks the save (Autopilot Storage is a tab on your markets).

## The two panels

- **Colony panel**: one colony. Open it with the **Colony Autopilot** button on the colony screen, or right click Population & Infrastructure.
- **Empire panel**: everything at once. Intel screen, **Colony Autopilot** filter. Pages: Colonies, Autopilot Storage, Colony crises, Mod features, Settings, Log, Diagnostics.

## Your first colony

1. **Found it.** You're asked **Full expansion** or **[Low profile](low-profile.md)**, then whether to do a [fast buildout](#fast-buildout).
2. **Pick a goal** in its panel (default Balanced): Money, Security, War production, Rapid growth or Self-sufficiency. Money or Balanced is fine to start. Details in [How it builds](how-it-builds.md).
3. **Read the plan.** It's the layout the colony is working toward. Steps show Done, Next or Planned.
4. **Build by hand if you like.** In Advisor, **Recommended builds** lists options with their value. Click one to build it.
5. **Hand it over.** Set the mode to **Approve** (one proposal at a time, you click Approve or Reject) or **Auto** (builds within your budget). The first time, setup opens with recommended settings filled in. Save it as is. **Use recommended settings** in the empire panel restores them.

## Fast buildout

When you found your first colony it asks whether to build fast, and how many credits to keep: 250,000, 100,000, 500,000 or 1,000,000. If you say yes:

- the colony goes to Auto;
- every day it starts whatever pays back fastest with everything above your floor, looking only at money;
- it stops after 30 days in a row of making money, then carries on in Auto with the normal budget.

The colony panel has buttons to change the floor or stop it. A colony losing money gets a **Start fast buildout** button.

## If Auto isn't building anything

Check the budget line on the colony panel. If it's zero, it's usually one of these, all under Settings, Budget and spending:

- **Budget type** is a share of net income and your net income is negative. Switch to **Automatic**.
- **Pause on loss** is on.
- You're under your **credit reserve**, which is never spent.

**Use recommended settings** fixes all three. The **Log** also says when something is waiting and why.

## More colonies

New colonies start as Balanced and Advisor. Use **Next goal** and **Next mode** in the empire panel's colony list. Past your colony limit, an administrator is placed or hired for you. Autonomy isn't used to get around the limit.

## If something looks wrong

- The **Log** and **Diagnostics** show what's waiting and what's switched off.
- For a bug, post in the [issues](https://github.com/Mjolniar/jonahs-colony-manager/issues) with `starsector.log`, `saves/common/colony_autopilot_diagnostics.json.data` if you can, and your modlist.

Next: [How it builds](how-it-builds.md), or [Settings](settings.md).

## Coming in 3.0

- Back up your save before updating. A save made in 3.0 can't be loaded in 2.45.x.
- Built, in testing: old settings carry over, and no setting becomes more aggressive on update.
- Built, in testing: a failing daily step no longer stops the other steps.
- Built, in testing: one ranked build queue for all colonies, with a 36-month payback limit.
- Planned: a shorter settings menu with a 5-row first-time setup.
- Planned: auto-expansion (off by default), a money ladder, and war and diplomacy settings.

Plans can still change before release.

Everything planned for 3.0: [roadmap](roadmap.md)
