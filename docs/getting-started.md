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
| Colony panel | The **Colony Autopilot** button on the colony screen, or right-click **Population & Infrastructure**, then **Colony Autopilot...** | One colony. Pages: **Overview** (stats, approval, goal, mode, fast buildout), **Plan and builds**, **Locks**, and **Mod features** (every mod feature that applies, on one page) |
| Empire panel | Intel screen, the **Colony Autopilot** filter | Pages: **Colonies** (budget, approvals, every colony), **Autopilot Storage** (contents, inspection points, research), **Colony crises** (the crisis meter, what's coming and where, what drives each faction's crisis and how it ends), **Mod features** (every mod feature in use, on one page), **Settings**, **Log**, **Diagnostics** |

Both panels list their pages down the left side; click one to show it. In **Settings**, click a group to open it; one is open at a time.

## Your first colony

1. **Found the colony** the usual way. When the founding dialog closes, Colony Autopilot offers a **fast buildout**. Say yes and pick a credit floor to have the colony built up hands-off until it makes money (see [Fast buildout](#fast-buildout-offered-when-you-found-your-first-colony)), or say no and follow the steps below.
2. **Open its colony panel.** It opens on **Overview**: size, stability, hazard, accessibility, industry slots, your credits, and this month's budget.
3. **Pick a goal** under **Goal**:

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
| AI cores, administrators | Used and hired (only cores you put in Autopilot Storage, where they add most; less the colony crisis progress they add; up to 7 inspection points per colony, so the Hegemony never inspects; an administrator where they add more than their salary, and always one for each colony past your colony limit) |
| Research, trade contracts, settlement | Auto, each only when it pays; research follows what the colony plans need (Ashes of the Domain, Random Assortment of Things) |
| Agents | Defense: hired only when there's a rebellion, Pather cell, pirate base or enemy operation (Nexerelin) |
| Emergency autonomy | Recommend: in a money emergency, names the colony that would help; granting it is yours (Nexerelin) |
| Policies, edicts, IndEvo automation, defense fleets | On; hazard pay goes to the colonies it speeds up most, within 30% of net income |
| Removals, story points, offense | Off: these are your calls |

### Fast buildout (offered when you found your first colony)

Once the founding dialog closes, Colony Autopilot asks whether to build the colony up fast, and how many credits to keep: 250,000, 100,000, 500,000 or 1,000,000. If you say yes:

- the colony switches to Auto, with recommended settings if setup hasn't run yet;
- every day it starts the buildings that pay back fastest (value per credit), paid from everything you hold above the floor;
- it ranks by money only, whatever the colony's goal or threat: defenses, patrols, stations and ship quality count for nothing, so it never adds their upkeep. A threatened colony plans for Security once the buildout ends;
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

- **Colony items, AI cores and research databanks:** dock at any of your colonies, open the **Autopilot Storage** tab, and deposit them there. The manager uses only items in that storage, and only for colonies in Auto mode; it never takes from your normal storage. Research (Vaults of Knowledge) takes what a tech needs from it too, and databanks from your fleet's cargo after that. AI cores are used with the recommended settings (**Use AI cores**). Each core goes where it adds most for the colony's goal: an Alpha where its +1 output (or a structure's bonus) is worth most, a Beta where upkeep is highest, a Gamma only where an input is short. A better core replaces a lesser one, which goes back to storage. **Inspection risk** decides how many inspection points a colony may hold: 7 or less (the Hegemony ignores those), more where its defenses beat the next inspection (**More where defended**, recommended), or **No limit**. A colony over its limit sends cores back to storage one a day, and a colony an inspection is coming for is left as it is. Autopilot Storage lists each colony's points and limit.
- **Administrators:** in Auto mode, an administrator is hired, placed or moved only where they add more than they cost: their +1 output (worth most at a big, productive colony) and the stability from your colony limit, against their salary (2,500 or 20,000 a month) and the hiring cost. The colony panel's Overview shows what its administrator costs and adds. Hiring is **Administrator hiring**, on in the recommended settings. Past your colony limit (your 3rd colony at the base limit of 2), one is placed or hired whatever the money says, since each colony past it costs every colony you govern 2 stability; it may go past the monthly budget, never below the reserve.

### Keeping control

- **Locks:** the colony panel's **Locks** page lists your buildings. A locked building is never upgraded, replaced or removed.
- **Removals:** buildings are only removed during terraforming, unless you turn on **Allow removals**.
- **Story points:** never spent unless you change **Story points** away from Player only.
- **Fees:** Approve and Auto add a convenience fee to each project they start (at Standard, 5% of the cost, at least 5,000). Moving items and hiring cost a small fee too. **Convenience fees** can be Off, Low, Standard or High.

## More colonies

Each new colony starts as Balanced and Advisor. Set its goal and mode in its colony panel, or use **Next goal** and **Next mode** in the empire panel's **Colonies** list.

A colony whose threat estimate reaches 70% (hostile neighbors, crises, raids, Pather cells) is planned as Security on its own. It goes back to its own goal below 65%, after at least 60 days.

A colony under siege (blockaded, hostile fleets at the planet, hostiles in the system that outweigh its defenses, or a raid, invasion or blockade on its way) only gets defenses until it's over. Its items, AI cores, policies and station augments are left alone, and trade contracts don't count on its goods. The log says when a siege starts and ends.

Colony crises (the game's Hostile Activity meter) are priced in credits, about 640 a month per point of monthly progress. AI cores pay for the Hegemony progress they add at colonies above size 3 and the Pather interest that gives a colony of size 4 or more a Pather cell at 7. A Patrol HQ, Military Base or High Command counts how much it slows every crisis (x0.9, x0.7, x0.5), and Fuel Production counts the Sindrian Diktat's progress. Once the next crisis is picked (the meter at 500), the colony it's likely to hit plans with Security, a Pather target sheds cores until its cell can dissolve, and a coming Nexerelin police raid closes free ports. Hegemony inspections: the game's default is to comply, which hands over every core there. **Hegemony inspection orders** on Advise (recommended) logs each one so you can resist or bribe; Resist where defended resists where the defenses beat it, which turns the Hegemony hostile. Deals, fights and memberships are yours; the **Colony crises** page says what each takes.

In a Nexerelin **Derelict Empire** start, every faction has enough points for a blockade or raid on day 0. Only in that start, the settings group **Attacks on your colonies** caps the pirate and crisis fleets in a system (the game allows 11) and makes Nexerelin's invasions, raids and blockades of your colonies come one at a time with a gap of 30 to 180 days. The recommended settings turn both on there (at most 4, 90 days apart).

## Agents (Nexerelin)

Off until setup runs; the recommended settings turn on **Defense**. Change it in the empire panel's **Settings**, under **Agents (Nexerelin)**: **Off**, **Defense**, or **Defense and offense**.

- **Defense:** an agent is hired (12,000) when there's work for one. It handles rebellions, Pather cells and hidden pirate bases. It also raises relations with factions that run operations against your colonies while not at war with you.
- **Offense:** adds a Saboteur that sabotages the military of factions already at war with you, starting with whoever is invading or raiding you.
- Costs come out of the monthly budget; Nexerelin pays salaries.
- **Your own agents:** the **Agents** section of the empire panel's **Mod features** page lists every agent you have, with its type (Negotiator, Saboteur or Hybrid). Yours get no orders until you click **Let Autopilot use it**; **Take it back** returns one. While one of yours sits idle, Autopilot doesn't hire another and says so in the log.
- **Types:** each agent only gets what its type can do. Negotiators and Hybrids raise relations, Saboteurs sabotage industry, and any type handles rebellions, Pather cells and pirate bases.
- The Agents section also lists the enemy operations running against your colonies, which the game doesn't show anywhere else.

## Emergency autonomy (Nexerelin)

Autonomy is your call: Autopilot never grants it. Off until setup runs; the recommended settings turn on **Recommend**. Change it in the empire panel's **Settings**, under **Emergency autonomy (Nexerelin)**.

- **In a money emergency** (net income below zero with under 3 months of it left above the credit reserve), Recommend logs the colony a year old or more that loses the most. **Grant autonomy** on the Mod features page does it: you get 25% of its profit or loss, and it no longer counts against your colony limit. Colonies you make autonomous are left alone.
- **Founding colonies past your limit** isn't an emergency: that gets an administrator.
- **Colonies an older Autopilot made autonomous** are taken back as soon as there's room under your colony limit (or an idle administrator, who then governs it), without Nexerelin's 2 unrest. The Mod features page lists them with **Take it back now**.

## Storage cheat

Off unless you turn it on (**Settings**, **Cheats**); the recommended settings never do. With it on, Autopilot Storage takes anything but ships and ambassadors, and you can take it out at any colony. Every colony that uses stockpiles for shortages draws from it for free, before its own stockpile, at the game's stockpile rate. The Autopilot Storage page lists the shortages it's covering.

## When something looks wrong

- The empire panel's **Log** says why things wait: budget, next month, locked by research, and so on.
- **Diagnostics** (empire panel) rates every building Supported, Measured, Analyzed or Quarantined, lists the state of each mod integration, and has **Rescan now**.
- When reporting a bug, attach `starsector-core/starsector.log` and `saves/common/colony_autopilot_diagnostics.json.data` to an [issue](https://github.com/Mjolniar/jonahs-colony-manager/issues).

Your other mods: [What each supported mod adds](supported-mods.md). All rules in detail: [details.md](details.md).
