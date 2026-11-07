# Getting started

This covers installing it, finding your way around the two panels, and getting your first colony running. Once you're comfortable, the other guides go deeper on each part.

## Install

1. Download the zip from the [latest release](https://github.com/Mjolniar/jonahs-colony-manager/releases/latest) and extract it into `Starsector/mods/`, so you end up with `Starsector/mods/Jonah's Colony Manager/mod_info.json`.
2. Enable **Jonah's Colony Manager** in the launcher. Nothing else is required. Anything it supports from other mods just turns on by itself when that mod is enabled.
3. Start a new game or load your save. Adding it to a save mid-game is fine. Taking it out of a save that used it isn't, since Autopilot Storage is a tab on your markets.

Nothing gets built on its own after installing. Every colony starts in **Advisor** mode, which only recommends things. The one exception is the fast buildout it offers when you found your first colony, and that's only if you say yes.

## The two panels

**Colony panel.** Open it with the **Colony Autopilot** button on the colony screen, or right click Population & Infrastructure and pick **Colony Autopilot...** at the bottom. This is one colony: its stats, goal and mode, its plan, what it would build next, and locks.

**Empire panel.** On the Intel screen, pick the **Colony Autopilot** filter. This is everything at once:

- **Colonies** - your budget, anything waiting for approval, and every colony with its plan and next build.
- **Autopilot Storage** - what's in each colony's storage, what's being shipped, each colony's inspection points, and research if you have Vaults of Knowledge.
- **Colony crises** - the crisis meter, what's coming and where, and how to end each faction's crisis.
- **Mod features** - everything your other mods add, on one page (agents, war fleets, autonomy, trade contracts and so on).
- **Settings**, **Log** and **Diagnostics**.

Both panels list their pages down the left side. In Settings, click a group to open it.

## Your first colony

1. **Found the colony** like normal. When the founding dialog closes, it asks how you want to expand, **Full expansion** or **[Low profile](low-profile.md)**, then offers a **fast buildout** (more on that below). Say yes if you want it built up hands-off, or no to do it step by step.
2. **Open its colony panel.** It opens on the Overview: size, stability, hazard, accessibility, industry slots, your credits and this month's budget.
3. **Pick a goal.** Every colony starts on Balanced, and you can change it any time:
   - **Balanced** - a bit of everything.
   - **Money** - income and accessibility.
   - **Security** - defense, fleets and stability.
   - **War production** - ship quality and fleet size.
   - **Rapid growth** - population growth and accessibility.
   - **Self-sufficiency** - making what your colonies are short of.

   For a first colony, Money or Balanced is usually the right call. Security is good for anything sitting next to pirates or someone hostile.
4. **Look at the plan.** Under **Plan** is the layout it's working toward, worked out from the planet itself (its deposits, hazards, habitability) and the goal, with each step marked Done, Next or Planned.
5. **Build by hand for a bit if you want.** In Advisor mode, **Recommended builds** lists the best options with what each one makes or costs a month and why it's ranked where it is. Click one to build it.
6. **Hand it over.** Set the mode to:
   - **Approve** - it proposes one thing at a time and you click Approve or Reject.
   - **Auto** - it just builds, within your budget.

   The first time you pick Approve or Auto it opens setup with the recommended settings filled in. Save it as is, they're meant to work if you only ever switch colonies to Auto. **Use recommended settings** in the empire panel puts them back any time.

## Fast buildout

When you found your first colony, it asks whether to build it up fast and how many credits to keep: 250,000, 100,000, 500,000 or 1,000,000. If you say yes:

- the colony goes to Auto (with the recommended settings if you haven't run setup yet);
- every day it starts whatever pays back fastest, using everything you have above that floor;
- it only cares about money while doing this, so no defenses or patrols eating upkeep yet;
- it stops once the colony has made money for 30 days in a row, and the colony carries on in Auto with its own goal and the normal budget.

While it runs, the colony panel shows the floor and net income with buttons to raise or lower the floor or stop it. Any colony losing money gets a **Start fast buildout** button too, so you can use it later.

## If Auto isn't building anything

Check the budget line on the colony panel. With the recommended settings a young colony still gets money from your savings, so if it's at zero it's usually one of these:

- **Budget type** is set to a share of net income and your empire's net income is negative (fleet upkeep counts), so the budget is zero. Switch it to **Automatic**.
- **Pause on loss** is on.
- You have less than your **credit reserve**, which is never spent.

All three are under Settings, Budget and spending. **Use recommended settings** fixes them in one click.

The **Log** also tells you when something is waiting and why: budget, next month's upkeep room, research, a siege and so on. If a colony seems properly stuck, `starsector.log` gets a "nothing worth starting" line for it once a month with its best options and what each score is made of.

## More colonies

Every new colony starts as Balanced and Advisor. Set its goal and mode in its colony panel, or use **Next goal** and **Next mode** right in the empire panel's colony list.

Going past your colony limit gets an administrator placed or hired for you, since every colony past the limit costs all of them stability. It never makes a colony autonomous to get around the limit.

## If something looks wrong

- The **Log** says why things are waiting.
- **Diagnostics** rates every building it knows about, shows the state of each mod it works with, and lists anything it's switched off for the session because it kept failing.
- If it's a bug, throw it in the [issues](https://github.com/Mjolniar/jonahs-colony-manager/issues) with `starsector-core/starsector.log` and `saves/common/colony_autopilot_diagnostics.json.data` if you can, and your modlist.

Next up: [How it builds](how-it-builds.md), or jump to [Settings](settings.md).
