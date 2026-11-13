# Settings

## Quick version

- Location: Empire panel (Intel screen, Colony Autopilot filter), Settings.
- Layout: 20 main rows in 6 groups. The rest is under Advanced.
- Empire mode: Off, Advisor, Approve or Auto. Empire-wide settings follow it ([Mode defaults](#mode-defaults)). The first-time setup sets it.
- Set by you: A row you change shows "set by you". It keeps its value when the empire mode changes.
- Presets: most main rows are presets. Custom: no preset matches. Mode default: each field at the empire mode's value.
- Reset to recommended: clears "set by you" and returns every setting to the empire mode's defaults. Keeps Expansion style, faction stances, the capital pick and the focus world.
- Missing mods: rows for missing mods are hidden.

## Main rows

- Defaults: Marked (recommended).

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
| Endgame | From 1,000,000/month (recommended), 2,000,000, 500,000, Off | Build for production and defense |
| Story points | Yours only (recommended), One per colony, 2 a month, 5 a month | |
| Stations | Pick by score (recommended), Low tech, Midline, High tech, Imperium, Only where they pay | [How it builds](how-it-builds.md) |
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

- Budget: Budget type Automatic. From net income 50%. From savings 15%. Pause on loss Off. Convenience fees Standard.
- Money ladder: Rungs follow the empire mode ([Mode defaults](#mode-defaults)).
- Building: Over-limit fix On. Allow removals Off. Story points a month 2.
- AI cores: Use AI cores On. Alpha cores Only where removable. Inspection risk Never inspected. Hegemony inspections Advise.
- Colony policies: Hazard pay growth threshold 25%. Hazard pay budget 30% of net income. Senate edicts On.
- Agents: Recruit agents On (12,000 each, 60 days). Agents to keep Auto (2, or 3 with offense).
- War: Emergency defense fleets On. Retake your lost colonies On (Off in saves from 0.2.45). Bombardment targets Capitals only.
- Enforcer patrols (Advanced, War, also without Nexerelin): Off. Each fleet type Off. Enforcer cost Reputation. Systems with Auto colonies. Skip your commissioner, Skip allies and League membership, Skip ceasefires, Off in Low profile, Off in your fleet's system: On. Patrols intercept Off. [Enforcer patrols](war-and-security.md#enforcer-patrols).
- Capital: Capital does Deliveries and commerce. Trade hub starts at net income over 100,000. Storage logistics ([Refit hub](items-and-cores.md#storage-logistics)) follows the empire mode.
- Extras: Found when a site earns 20,000. Measured mod buildings Build when best. Station augments and Megastructures Follow colony mode. Other rows follow the empire mode.
- New colonies: Method Nexerelin colony fleet when possible. TASC stations follow the empire mode.
- Production: Production Light. Hull sources 1. Hull capacity 100,000 a month. Order goods within 6 months. Production quality 0.8. Parts reserve 100. Supplies and fuel floors No shortage. Floors at Money colonies Off. Floor builds first On. Make room Ask first. Money taper Gentle (300,000 and 1,000,000). Same building copies 2, next copy counts 75%. Goal weights 0.3.2 table. Contracts keep floor goods On. Parts to the capital Off. Shipyard from Surplus On.
- Production floor values: 40,000 a hull source, 0.5 a capacity credit, 50,000 per 1.0 quality, 250 a part, order goods x3, other goods x2.
- Production Off: The 0.3.1 picks. Floors, taper, diversity and the 0.3.2 table are not used.
- Interface: Announcement voice Machine. Colony screen button On. Thinking list Hide. Approval summary Every 60 days. Build menu hints Advisor colonies. Score on open Off.

## Mode defaults

- Colony settings: AI cores, administrators, policies, stations, take down, surplus terraforming and IndEvo run at Auto colonies only, with the values above.
- Every mode: Over-limit fix On. Obligations rung On. Convenience fees Standard. Credit reserve not spent. Enforcer patrols Off. Inspections Advise.

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

- Settings: moved to the 0.3.0 menu once, unchanged. Mapping: [patch notes](changelog.md#0300---2026-11-11).
- Saves from before 0.3.2: Production is Light on load, with one log line. Production Off keeps the 0.3.1 picks.
- Saves from before 0.3.3: Empire mode Auto after setup with an Auto colony, Approve with an Approve colony, else Advisor. A setting that differs from 0.3.2's recommended value counts as set by you. The rest follows the empire mode, with one log line.
- Cheat and balance settings: none. Cheats are in Easy Sector.

Next: [Supported mods](supported-mods.md).
