# Getting started

## Install

1. Download the zip from the latest [release](https://github.com/Mjolniar/jonahs-colony-manager/releases/latest). Extract it into `Starsector/mods/` so you get `Starsector/mods/Jonah's Colony Manager/mod_info.json`.
2. In the launcher, enable **Jonah's Colony Manager**. No other mods are required. The optional integrations turn on by themselves when their mod is enabled.
3. Start a new game or load a save. Adding the mod to a running save is fine. Removing it from a save that used it is not: Autopilot Storage is a market tab.

Nothing is built automatically after install. Every colony starts in **Advisor** mode, which only makes recommendations.

## The two panels

| Panel | How to open | What it's for |
|---|---|---|
| Colony panel | Colony screen, right-click **Population & Infrastructure**, then **Colony Autopilot...** | One colony: goal, mode, plan, recommended builds, approvals, locks |
| Empire panel | Intel screen, **Colonies** tab, then **Colony Autopilot** | Budget, approvals, every colony, Autopilot Storage, settings, log, diagnostics |

## Your first colony

1. **Found the colony** the usual way.
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
5. **Build by hand at first.** In Advisor mode, **Recommended builds** lists the best options with their estimated effects. The button shows the price; click it to build.
6. **Hand it over when you trust it.** Under **Mode**:
   - **Approve:** one proposal at a time, shown under **Waiting for your approval** in both panels. Click Approve or Reject.
   - **Auto:** builds by itself, within your budget.

   The first switch to Approve or Auto opens **first-time setup**. Every setting is pre-filled with a recommendation based on your income. Click **Use recommended settings** or change them, then click **Save and switch to ...**.

### Fast buildout (offered when you found your first colony)

Once the founding dialog closes, Colony Autopilot asks whether to build the colony up fast, and how many credits to keep: 250,000, 100,000, 500,000 or 1,000,000. If you say yes:

- the colony switches to Auto, with recommended settings if setup hasn't run yet;
- every day it's planned for Money and starts the buildings that pay back fastest (value per credit), paid from everything you hold above the floor;
- the monthly budget, per-project cap, pause on loss and new-upkeep cap don't apply to it, but fees, the stability floor and locks do;
- it ends once the colony has earned money for 30 days in a row. The colony then stays in Auto with its own goal and the normal budget.

While it runs, the colony panel's **Fast buildout** section shows the floor and net income, with **Lower floor**, **Raise floor** and **Stop buildout** buttons. Any colony that loses money gets a **Start fast buildout** button there, so you can start one later, or for another colony.

### First-colony budget trap

Without fast buildout, a young colony usually loses money, so Auto may build nothing at first. Three settings cause this:

- **Monthly budget** defaults to 50% of last month's net income for your whole empire, including fleet upkeep. While that number is negative, the budget is zero.
- **Pause on loss** (on) stops all spending while net income is negative.
- **Credit reserve** (500,000) is never spent. With less than that in the bank, nothing is built.

Fast buildout gets around all three. To let normal Auto build while the colony is still unprofitable, open **Settings** in the empire panel, under **Budget and spending**:

- set **Budget type** to **Fixed amount** with a monthly budget you can afford;
- turn **Pause on loss** off;
- lower the **Credit reserve** to what you want to keep.

The colony panel's budget line shows how much can be spent this month. **Spending is paused** means pause on loss is stopping it.

### Items, AI cores and administrators

- **Colony items and AI cores:** dock at any of your colonies, open the **Autopilot Storage** tab, and deposit them there. The manager uses only items in that storage, and only for colonies in Auto mode; it never takes from your cargo or normal storage. AI cores also need **Use AI cores** turned on in Settings.
- **Administrators:** in Auto mode, idle administrators are assigned where they help most. Hiring is off by default; turn on **Administrator hiring** to allow it.

### Keeping control

- **Locks:** the colony panel lists your buildings. A locked building is never upgraded, replaced or removed.
- **Removals:** buildings are only removed during terraforming, unless you turn on **Allow removals**.
- **Story points:** never spent unless you change **Story points** away from Player only.
- **Fees:** Approve and Auto add a convenience fee to each project they start (at Standard, 5% of the cost, at least 5,000). Moving items and hiring cost a small fee too. **Convenience fees** can be Off, Low, Standard or High.

## More colonies

Each new colony starts as Balanced and Advisor. Set its goal and mode in its colony panel, or use **Next profile** and **Next mode** in the empire panel's **Colonies** list.

A colony whose threat estimate reaches 70% (hostile neighbors, crises, raids, Pather cells) is planned as Security on its own. It goes back to its own goal below 65%, after at least 60 days.

## When something looks wrong

- The empire panel's **Log** says why things wait: budget, next month, locked by research, and so on.
- **Diagnostics** (empire panel) rates every building Supported, Measured, Analyzed or Quarantined, lists the state of each mod integration, and has **Rescan now**.
- When reporting a bug, attach `starsector-core/starsector.log` and `saves/common/colony_autopilot_diagnostics.json.data` to an [issue](https://github.com/Mjolniar/jonahs-colony-manager/issues).

All rules in detail: [details.md](details.md).
