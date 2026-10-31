# Getting started

Install, the two panels, your first colony, and the settings that matter early. What the manager does with each of your other mods is in [What each supported mod adds](supported-mods.md).

## Install

1. Download the zip from the latest [release](https://github.com/Mjolniar/jonahs-colony-manager/releases/latest). Extract it into `Starsector/mods/` so you get `Starsector/mods/Jonah's Colony Manager/mod_info.json`.
2. In the launcher, enable **Jonah's Colony Manager**. No other mods are required. The optional integrations turn on by themselves when their mod is enabled.
3. Start a new game or load a save. Adding the mod to a running save is fine. Removing it from a save that used it is not: Autopilot Storage is a market tab.

Nothing is built automatically after install. Every colony starts in **Advisor** mode, which only makes recommendations; the one exception is the fast buildout you can accept for your first colony.

**Updating from 2.6.0 or earlier:** battle size 2000 and sticky combat autopilot moved to [Jonah's Easy Sector](https://github.com/Mjolniar/jonahs-easy-sector). Install it too if you want them; sticky autopilot keeps its on/off state.

## The two panels

| Panel | How to open | What it's for |
|---|---|---|
| Colony panel | The **Colony Autopilot** button on the colony screen, or right-click **Population & Infrastructure**, then **Colony Autopilot...** | One colony: goal, mode, plan, recommended builds, approvals, locks |
| Empire panel | Intel screen, **Colony threats** filter (also under **Important**), then **Colony Autopilot** | Budget, approvals, every colony, Autopilot Storage, settings, log, diagnostics |

## Your first colony

1. **Found the colony** the usual way. When the founding dialog closes, Colony Autopilot offers a **fast buildout**. Say yes and pick a credit floor to have the colony built up hands-off until it makes money (see [Fast buildout](#fast-buildout-offered-when-you-found-your-first-colony)), or say no and follow the steps below.
2. **Open its colony panel.** The top lines show size, stability, hazard, accessibility, industry slots, your credits, and this month's budget.
3. **Pick a goal** under **Goal profile**:

   | Goal | Weights toward |
   |---|---|
   | Balanced | A bit of everything (the default) |
   | Money | Income and accessibility |
   | Security | Defense, fleets, stability |
   | War production | Ship quality and fleet size |
   | Rapid growth | Population growth and accessibility |
   | Self-sufficiency | Making what your colonies are short of |

   For a first colony, Money or Balanced is usually right. Use Security for a colony next to pirates or a hostile faction.
4. **Read the plan.** **Plan: ...** is the target layout for this planet and goal. Each step is marked Done, Next or Planned, and a step that's still locked shows what it needs.
5. **Build by hand at first.** In Advisor mode, **Recommended builds** lists the best options. Each shows the money it makes or costs a month after upkeep, and a **Ranked** line: its money and other effects (stability, accessibility and so on, weighted for your goal), plus a bonus if it's the next plan step, minus its build cost spread over 3 years. Options that don't pay for themselves are marked, and Approve and Auto never start them. The button shows the price; click it to build.
6. **Hand it over when you trust it.** Under **Mode**:
   - **Approve:** one proposal at a time, shown under **Waiting for your approval** in both panels. Click Approve or Reject.
   - **Auto:** builds by itself, within your budget.

   The first switch to Approve or Auto opens **first-time setup**, pre-filled with the recommended settings (below). Just click **Save and switch to ...**: the recommendations are meant to work without changes. **Use recommended settings** in the empire panel puts them back any time.

### What the recommended settings do

They're chosen so that switching colonies to Auto and doing nothing else works out well:

| Area | Recommended |
|---|---|
| Budget | **Automatic**: half of last month's net income, plus 15% of your credits above the reserve each month. A young colony that loses money still gets built, and spending slows as savings fall. |
| Credit reserve | A quarter of your credits at setup, between 50,000 and 500,000; never spent |
| Pause on loss | Off (the Automatic budget already slows down) |
| AI cores, administrators | Used and hired (only cores you put in Autopilot Storage; within Hegemony inspection limits) |
| Research, trade contracts, settlement | Auto, each only when it pays (Ashes of the Domain, Random Assortment of Things) |
| Agents | Defense: hired only when there's a rebellion, Pather cell, pirate base or enemy operation (Nexerelin) |
| Policies, edicts, IndEvo automation, defense fleets | On |
| Removals, story points, offense | Off: these are your calls |

### Fast buildout (offered when you found your first colony)

Once the founding dialog closes, Colony Autopilot asks whether to build the colony up fast, and how many credits to keep: 250,000, 100,000, 500,000 or 1,000,000. If you say yes:

- the colony switches to Auto, with recommended settings if setup hasn't run yet;
- every day it's planned for Money and starts the buildings that pay back fastest (value per credit), paid from everything you hold above the floor;
- the monthly budget, per-project cap, pause on loss and new-upkeep cap don't apply to it, but fees, the stability floor and locks do;
- it ends once the colony has earned money for 30 days in a row. The colony then stays in Auto with its own goal and the normal budget.

While it runs, the colony panel's **Fast buildout** section shows the floor and net income, with **Lower floor**, **Raise floor** and **Stop buildout** buttons. Any colony that loses money gets a **Start fast buildout** button there, so you can start one later, or for another colony.

### If Auto builds nothing

The colony panel's budget line shows how much can be spent this month. With the recommended settings a young colony still gets money from your savings. If it doesn't:

- **Budget type** is **Share of net income** and your empire's net income (fleet upkeep included) is negative, so the budget is zero. Switch to **Automatic**.
- **Pause on loss** is on: **Spending is paused** shows on the budget line.
- You have less than the **Credit reserve**, which is never spent.

All three are under **Settings**, **Budget and spending**, in the empire panel; **Use recommended settings** fixes them in one click.

### Items, AI cores and administrators

- **Colony items and AI cores:** dock at any of your colonies, open the **Autopilot Storage** tab, and deposit them there. The manager uses only items in that storage, and only for colonies in Auto mode; it never takes from your cargo or normal storage. AI cores are used with the recommended settings (**Use AI cores**).
- **Administrators:** in Auto mode, idle administrators are assigned where they help most. With the recommended settings it also hires one when a colony has none (**Administrator hiring**).

### Keeping control

- **Locks:** the colony panel lists your buildings. A locked building is never upgraded, replaced or removed.
- **Removals:** buildings are only removed during terraforming, unless you turn on **Allow removals**.
- **Story points:** never spent unless you change **Story points** away from Player only.
- **Fees:** Approve and Auto add a convenience fee to each project they start (at Standard, 5% of the cost, at least 5,000). Moving items and hiring cost a small fee too. **Convenience fees** can be Off, Low, Standard or High.

## More colonies

Each new colony starts as Balanced and Advisor. Set its goal and mode in its colony panel, or use **Next goal** and **Next mode** in the empire panel's **Colonies** list.

A colony whose threat estimate reaches 70% (hostile neighbors, crises, raids, Pather cells) is planned as Security on its own. It goes back to its own goal below 65%, after at least 60 days.

## Agents (Nexerelin)

Off until setup runs; the recommended settings turn on **Defense**. Change it in the empire panel's **Settings**, under **Agents (Nexerelin)**: **Off**, **Defense**, or **Defense and offense**.

- **Defense:** an agent is hired (12,000) when there's work for one. It handles rebellions, Pather cells and hidden pirate bases. It also raises relations with factions that run operations against your colonies while not at war with you.
- **Offense:** adds a Saboteur that sabotages the military of factions already at war with you, starting with whoever is invading or raiding you.
- Costs come out of the monthly budget; Nexerelin pays salaries. Agents you hired yourself are never given orders.
- The empire panel lists your agents and the enemy operations running against your colonies, which the game doesn't show anywhere else.

## When something looks wrong

- The empire panel's **Log** says why things wait: budget, next month, locked by research, and so on.
- **Diagnostics** (empire panel) rates every building Supported, Measured, Analyzed or Quarantined, lists the state of each mod integration, and has **Rescan now**.
- When reporting a bug, attach `starsector-core/starsector.log` and `saves/common/colony_autopilot_diagnostics.json.data` to an [issue](https://github.com/Mjolniar/jonahs-colony-manager/issues).

Your other mods: [What each supported mod adds](supported-mods.md). All rules in detail: [details.md](details.md).
