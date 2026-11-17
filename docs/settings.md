# Settings

## Quick version

- Location with LunaLib: They're in LunaLib's settings menu (F3 by default) under Auto Colonies. The main rows are on the General tab and the Advanced groups are on the Advanced tab ([LunaLib](#lunalib)).
- Location without LunaLib: They're on the colony panel under Empire, then Settings.
- Layout: There are 20 main rows in 6 groups, and the rest is under Advanced.
- Story mode: It's the first row and it's off by default ([Story mode](story-mode.md)).
- Empire mode: You can pick Off, Advisor, Approve or Auto, and the empire-wide settings follow it ([Mode defaults](#mode-defaults)). The first-time setup sets it.
- Set by you: Any row you change shows "set by you", and it keeps its value when the empire mode changes.
- Presets: Most main rows are presets. Custom means no preset matches, and Mode default puts each field at the empire mode's value.
- Reset to recommended: It clears "set by you" and puts every setting back to the empire mode's defaults, but it keeps your Expansion style, faction stances, capital pick and focus world.
- Missing mods: Rows for mods you don't have are hidden on the Settings page. In LunaLib they show "Needs" and the mod's name, and they don't do anything.

## LunaLib

- LunaLib: It's optional. Without it, the Settings page has every row.
- Values: LunaLib's values are global, so every save uses them.
- Story mode: Each save switches to LunaLib's Story mode value when it's loaded and when you save LunaLib's menu. If story mode is holding a setting at off, LunaLib keeps the value you picked.
- First load after 0.3.4.0: The loaded save's settings get copied into LunaLib once, and that game doesn't change.
- Later loads: LunaLib's values go into the save you load, along with its "set by you" marks.
- Saving in LunaLib's menu: Whatever you changed applies to the loaded game and counts as set by you.
- Presets: They're radio options that write the same fields as the Settings page rows. Mode default sets the group to the empire mode's values, which then follow the mode. Custom just means no option matches, so picking it does nothing.
- Numbers: They have the same range and step as the Settings page rows, and anything you type gets rounded to the step.
- Reset to recommended: It's a tick box (General, Reset), and it unticks itself after you save.
- Prepare for removal: A tick box (General, Removal). Turning it on asks in a campaign dialog, then prepares the save ([Removing it](audit.md#removing-it)). Turning it off runs Auto Colonies in that save again. Each save shows its own state when loaded.
- Settings page (colony panel, Empire): It has one line saying where the settings are, then this save's picks (Capital, Refit hub, Sell hub, Offense target) and Faction stances.
- First-time setup: Once LunaLib holds settings from a save you've set up, a new save's first Approve or Auto colony keeps them. It won't put in recommended values or change the empire mode.

## Main rows

- Defaults: They're the ones marked (recommended).

| Row | Choices | Notes |
|---|---|---|
| Empire mode | Off, Advisor (new saves), Approve, Auto | [Mode defaults](#mode-defaults) |
| Expansion style | Full expansion, Low profile | [Low profile](low-profile.md) |
| Spending | Thrifty, Normal (recommended), Lavish | Net income share 30% / 50% / 80%. Savings share 5% / 15% / 30%. New upkeep cap 5% / 10% / 20% of net income |
| Extras | Auto (recommended), Recommend only, Off | |
| New colonies | Off, Ask first, Auto (by empire mode) | [New colonies](expansion.md) |
| War (Nexerelin) | Off, Defend, Retaliate (Auto mode), Hold them down, Conquer, Scorched | [War page](war-and-security.md#the-war-page) |
| Credit reserve | An amount (recommended: a quarter of credits, 50,000-500,000) | Not spent |
| Surplus investment | Off, over 1M, over 3M (recommended), over 5M, over 10M | [Focus world](money.md) |
| What to buy | Balanced (recommended), Fleet, Expansion, Economy, Save | [Money ladder](money.md#the-money-ladder) |
| Endgame | From 1,000,000/month (recommended), 2,000,000, 500,000, Off | Builds for production and defense |
| Story points | Yours only (recommended), One per colony, 2 a month, 5 a month | |
| Stations | Pick by score (recommended), Low tech, Midline, High tech, Imperium, Diable Avionics, Roider Union, Only where they pay. It asks once before the first station. | [How it builds](how-it-builds.md) |
| Take down buildings | Never, Clean up (recommended), Free hand | |
| Colony policies | Auto (recommended), Don't touch | |
| AI cores | Off, Safe (recommended), Small colonies only, Defended, No limit | |
| Administrators | Off, Place only, Hire and place (recommended) | |
| Capital | Autopilot picks (recommended), one of your colonies, None | [Capital](capital.md) |
| Agents (Nexerelin) | Off, Defense (recommended), Defense and offense | |
| Ambassadors (IndEvo) | Hire and install (recommended), Off | |
| Faction stances | OPEN button | [Factions page](war-and-security.md#faction-stances) |
| On-screen log | Every entry (recommended), Important only, Off | |

## Advanced

- Budget: Budget type is Automatic, From net income is 50% and From savings is 15%. Pause on loss is Off and Convenience fees are Standard.
- Money ladder: The rungs follow the empire mode ([Mode defaults](#mode-defaults)).
- Building: Over-limit fix is On, Allow removals is Off and Story points a month is 2.
- AI cores: Use AI cores is On and Alpha cores is Only where removable. Inspection risk is Never inspected and Hegemony inspections is Advise.
- Colony policies: Hazard pay growth threshold is 25% and Hazard pay budget is 30% of net income. Senate edicts is On.
- Agents: Recruit agents is On (12,000 each, 60 days), and Agents to keep is Auto (2, or 3 with offense).
- War: Emergency defense fleets and Retake your lost colonies are On (retake is Off in saves from 0.2.45). Bombardment targets is Capitals only.
- Enforcer patrols (Advanced, War, also without Nexerelin): Enforcer patrols and each fleet type are Off. Enforcer cost is Reputation, and it covers Systems with Auto colonies ([Enforcer patrols](war-and-security.md#enforcer-patrols)).
- Enforcer rules: Skip your commissioner, Skip allies and League membership, Skip ceasefires and Off in Low profile are On. Attack in your fleet's system is On, Patrols intercept is Off and Sindrian Diktat raiders is On.
- Patrol kills count for crises (Advanced, War): It's On, so kills by Auto colony patrols and stations count toward the colony crisis and Tri-Tachyon commerce raiding ([Colony crises](war-and-security.md#colony-crises)).
- Capital: Capital does is set to Deliveries and commerce, and the Trade hub starts at net income over 100,000. Storage logistics ([Refit hub](items-and-cores.md#storage-logistics)) follows the empire mode.
- Extras: Found when a site earns is 20,000 and Measured mod buildings is Build when best. Station augments and Megastructures are on Follow colony mode, and the other rows follow the empire mode.
- New colonies: Method is Nexerelin colony fleet when possible, and TASC stations follow the empire mode.
- Production: Production is Light, Hull sources is 1 and Hull capacity is 100,000 a month. Order goods within is 6 months, Production quality is 0.8 and Parts reserve is 100.
- Floors: Supplies and fuel floors are No shortage, Floors at Money colonies is Off and Floor builds first is On. Make room is Ask first.
- Taper and copies: Money taper is Gentle (300,000 and 1,000,000). Same building copies is 2, and the next copy counts 75%. Goal weights is the 0.3.2 table.
- Contracts and parts: Contracts keep floor goods is On, Parts to the capital is Off and Shipyard from Surplus is On.
- Production floor values: They're 40,000 a hull source, 0.5 a capacity credit, 50,000 per 1.0 quality, 250 a part, order goods x3 and other goods x2.
- Production Off: It goes back to the 0.3.1 picks and doesn't use floors, taper, diversity or the 0.3.2 table.
- Interface: Announcement voice is Machine, Colony screen button is On and Thinking list is Hide. Approval summary is Every 60 days, Build menu hints is Advisor colonies and Score on open is Off.

## Mode defaults

- Colony settings: AI cores, administrators, policies, stations, take down, surplus terraforming and IndEvo only run at Auto colonies, with the values above.
- Every mode: Over-limit fix and the Obligations rung are On, Convenience fees are Standard and the credit reserve isn't spent. Enforcer patrols are Off and Inspections are on Advise.

| Setting | Off | Advisor | Approve | Auto |
|---|---|---|---|---|
| New colonies | Off | Off | Ask first | Auto |
| TASC stations | Off | Recommend only | Recommend only | Auto |
| Money ladder rungs | Off | Off | Recommend only | On |
| Surplus investment | Off | Off | Over 3,000,000 | Over 3,000,000 |
| Endgame | Off | Off | From 1,000,000 | From 1,000,000 |
| RAT settlement | Off | Advisor | Approve | Auto |
| Trade contracts, Research | Off | Advisor | Advisor | Auto |
| Special project supply | Off | Warn only | Warn only | Auto |
| Stable locations | Off | Recommend only | Recommend only | Auto |
| Agents (Nexerelin) | Off | Off | Off | Defense |
| Autonomy (Nexerelin) | Off | Recommend | Recommend | Recommend |
| War (Nexerelin) | Off | Off | Defend | Retaliate |
| Conquered colonies start in | Advisor | Advisor | Approve | Auto |
| Storage logistics | Off | Off | Off | On |
| Capital does | Off | Off | Deliveries and commerce | Deliveries and commerce |
| Make room | Off | Off | Ask first | Auto |

## Saves from 0.2.45

- Settings: They were moved to the 0.3.0 menu once and didn't change. The mapping is in the [patch notes](changelog.md#0300---2026-11-11).
- Saves from before 0.3.2: Production is set to Light on load, with one log line. Production Off keeps the 0.3.1 picks.
- Saves from before 0.3.3: After setup, the empire mode is Auto if you have an Auto colony, Approve if you have an Approve colony, and Advisor otherwise.
- Their settings: Any setting that differs from 0.3.2's recommended value counts as set by you, and the rest follows the empire mode (one log line).
- Cheat and balance settings: There aren't any. Cheats are in Easy Sector.

Next: [Supported mods](supported-mods.md).
