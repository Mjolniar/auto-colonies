# Settings

With LunaLib, the settings are in its menu (F3 by default) under Auto Colonies. The General tab has the few settings most people change, and everything else is on the Advanced tab. Without LunaLib, they're on the colony panel under Empire, then Settings. This page goes through them in the same order as LunaLib's menu.

## How defaults work

- "Empire mode" in the Default column means the setting follows the empire mode, and its values are in [Empire mode defaults](#empire-mode-defaults).
- Anything you change shows "set by you" and keeps its value when the empire mode changes.
- Some main settings are presets that set several Advanced settings at once. Mode default puts the group back on the empire mode's values, and Custom means the Advanced settings don't match any option.
- Reset to recommended puts everything back to the defaults. Balance, Low profile, faction stances and this save's picks stay.
- Settings for mods you don't have are hidden, or show "Needs" and the mod's name in LunaLib.
- There are no cheat settings. Cheats are in Easy Sector.

## LunaLib

- LunaLib is optional. Its values are global, and changes you save in its menu apply to the loaded game and count as set by you.
- Story mode follows LunaLib's value in every save, but LunaLib keeps your value for a setting that story mode is holding at off.
- When LunaLib holds settings from a save you've set up, a new save's first Approve or Auto colony keeps them and doesn't change the empire mode.
- Faction stances and this save's picks (Capital, Refit hub, Sell hub and Offense target) are on the Autopilot panel's Settings page.
- With LunaLib, the Autopilot panel's Settings page also has the settings you'd change most, on one screen: empire mode, Balance, Spending, AI cores, Profile, New colonies, War, the credit reserve, Stations, Take down buildings, Colony policies, Administrators, Capital, Fleet limit per system, Gather everything at the capital, Tech-Mining finds, the hubs and the on-screen log. Click a button to change it, hover it to see what it does. Changes there show up in LunaLib's menu too.

## General tab

### Story mode

| Setting | Options | Default | What it does |
|---|---|---|---|
| Story mode | On, Off | Off | On means the Colony Autopilot starts dormant and you unlock it through the story (Story mode). Each save keeps its story progress if you switch. |

### Playstyle

| Setting | Options | Default | What it does |
|---|---|---|---|
| Balance | Easy, Normal, Hard | Hard | How expensive the Autopilot is. Hard is every price as the mod was balanced. Normal makes story bandwidth prices three quarters, halves the core-heavy mod surcharge and makes fed AI cores worth a quarter more, and Easy makes prices half, drops the surcharge and makes fed cores worth half again as much. Both set convenience fees to Low. |
| Profile | Low profile, Normal, High profile | Picked in the new-game pop-up | How your empire grows, setting Low profile, New colonies, AI cores, War and where new colonies go at once. Low profile founds no colonies, keeps AI cores Safe and stays at Defend. Normal founds colonies in systems you hold, uses Defended AI cores and Retaliates. High profile founds colonies within 15 ly of the capital without asking first, uses every AI core you have and Retaliates. It shows Custom when you've changed one of those settings by hand. |
| Empire mode | Off, Advisor, Approve, Auto | Advisor | Empire-wide settings follow this. Off does nothing, Advisor advises, Approve asks you first and Auto just does it. |
| Spending | Thrifty, Normal, Lavish | Normal | Thrifty spends 30% of net income and 5% of savings, Normal 50% and 15%, and Lavish 80% and 30%. The per-project cap is half, all or twice your net income, and new upkeep is capped at 5%, 10% or 20% of net income. |
| War | Off, Defend, Retaliate, Hold them down, Conquer, Scorched | Empire mode | This is how far war fleets go, and faction stances cap it. Defend sends relief, Retaliate raids attackers and retakes your lost colonies, and Hold them down raids anyone at Rival and up. Conquer invades and Scorched bombards (War page). Without Nexerelin, Defend sends Auto Colonies' own defense fleets, and the levels past it need Nexerelin. |

### Money

| Setting | Options | Default | What it does |
|---|---|---|---|
| Credit reserve | 0 to 3,000,000 | A quarter of your credits, 50,000 to 500,000 | It's the money Autopilot won't touch. |

### Interface, reset and removal

| Setting | Options | Default | What it does |
|---|---|---|---|
| On-screen log | Every entry, Important only, Off | Every entry | Short lines show on screen, and the Log page keeps every entry. |
| Reset to recommended | Tick and save | | Your settings go back to the empire mode's defaults, then the box unticks itself. |
| Prepare for removal | On, Off | Off | A dialog asks you first, then it moves Autopilot Storage into each colony's Storage, takes out what it keeps in this save and stops. Then save, quit and remove the mod. Turning it off lets it run again in this save. |

## Advanced tab

### Colony behavior

| Setting | Options | Default | What it does |
|---|---|---|---|
| Low profile | On, Off | Off | Colony crises come slower, but you'll earn less. It won't put AI cores in colonies above size 3, and it stays away from Pather interest, free ports, raids and sabotage ([Low profile](low-profile.md)). Profile sets it too. |
| Extras | Auto, Recommend only, Off | Auto | It covers stable locations, measured mod buildings, trade contracts, research, special project supply, megastructures, station augments, RAT settlement, IndEvo automation and Antimatter Stabilizers. |
| New colonies | Off, Ask first, Auto | Empire mode | It founds new colonies with idle money, and on Ask first you approve each one (New colonies). |
| Surplus investment | Off, Over 1M, Over 3M, Over 5M, Over 10M | Empire mode | Anything you've got over this goes into one focus world (Money). |
| What to buy | Balanced, Fleet, Expansion, Economy, Save | Balanced | It's what idle money buys first (Money ladder). |
| Rich cap | 30,000,000, 50,000,000, 20,000,000, None | 30,000,000 | Rich needs 15 months of your colony net in free cash, but never more than this, so a big income can still get there. |
| Endgame | From 1M/month, From 2M/month, From 500k/month, Off | Empire mode | At this income, it starts building for production and defense. |
| Story points | Yours only, One per colony, 2 a month, 5 a month | Yours only | It caps the story points Autopilot spends on improvements. |

### Building

| Setting | Options | Default | What it does |
|---|---|---|---|
| Stations | Pick by score, Low tech, Midline, High tech, Imperium, Diable Avionics, Roider Union, Only where they pay | Pick by score | It builds stations from size 4 and upgrades them as colonies grow (Battlestation at 6, Star Fortress at 8, and a size sooner at the capital). It asks once before the first station (How it builds). Imperium needs Interstellar Imperium. |
| Take down buildings | Never, Clean up, Free hand | Clean up | Never means nothing comes down. Clean up removes industries that stopped paying, makes room for capital trade buildings and allows endgame changes. Free hand is Clean up plus Allow removals. |
| Colony policies | Auto, Don't touch | Auto | Auto uses hazard pay, free port and edicts where they pay, and Don't touch leaves them alone. |

### Items and people

| Setting | Options | Default | What it does |
|---|---|---|---|
| AI cores | Off, Safe, Small colonies only, Defended, No limit | Safe | AI cores come from Autopilot Storage. Safe is never inspected, and Defended resists inspections, so the Hegemony turns hostile (AI cores). |
| Administrators | Off, Place only, Hire and place | Hire and place | Place only uses the administrators you've got, and Hire and place hires more too. |

### Security and diplomacy

| Setting | Options | Default | What it does |
|---|---|---|---|
| Agents | Off, Defense, Defense and offense | Empire mode | Agents handle security and relations, and offense adds sabotage. Needs Nexerelin. |
| Ambassadors | On, Off | On | It hires and installs ambassadors, Ally and Friendly factions first. Needs Industrial Evolution. |

### Budget

| Setting | Options | Default | What it does |
|---|---|---|---|
| Budget type | Automatic, Share of net income, Fixed amount | Automatic | Automatic spends a share of net income plus a share of savings above the reserve. Share of net income uses just the first share, and Fixed amount uses the Monthly budget. |
| From net income | 10% to 100% | 50% | Automatic and Share of net income both use this. |
| From savings | 5% to 50% | 15% | Each month, Automatic spends this share of your credits above the reserve. |
| Monthly budget | 0 to 100,000,000 | 1,000,000 | Fixed amount spends this each month. |
| Per-project cap | Automatic, Fixed | Automatic | Automatic follows Spending, and Fixed uses Fixed cap. |
| Fixed cap | 250,000 to 100,000,000 | Your net income, 1,000,000 to 5,000,000 | It's the most one project can cost, and changing it switches the cap to Fixed. |
| New upkeep per month | Automatic, Fixed | Automatic | Automatic allows a share of net income that follows Spending (10% on Normal), and Fixed uses Fixed upkeep. |
| Fixed upkeep | 0 to 10,000,000 | 10% of net income, at least 20,000 | Fixed allows this much new upkeep a month. |
| Pause on loss | On, Off | Off | It stops spending while your net income is below 0. |
| Convenience fees | Off, Low, Standard, High | Standard | These apply to remote building, shipping and orders. |

### Surplus and money ladder

| Setting | Options | Default | What it does |
|---|---|---|---|
| Surplus over 10,000,000 | Every colony, Focus world only | Every colony | Once you're this much over the surplus line, every Approve and Auto colony spends from the surplus, or just the focus world. |
| Wonders and gates | On, Off | On | It builds Bifrost gates, the Daedalus Array and the Garden of Elysium once their goods are made, and it asks you before each wonder. Before the Garden finishes, the AI cores and items in the farms and resorts it replaces go to Autopilot Storage. Needs Vaults of Knowledge. |
| Development plan | By plan, Off | By plan | A colony an expedition founds starts as a Centralized Urban Core when its plan has no farming or mining, since those lose output there and everything else gains. Other colonies keep the standard plan. Needs Vaults of Knowledge. |
| Special projects | Ask first, Off | Ask first | It proposes a Black Site project once your colonies can supply it, and after you approve it, it starts each stage when the last one is done. Shroud projects are left to you, and so is a project or stage you pause. Needs Vaults of Knowledge. |
| Surplus terraforming | Max output, Pays back in 10 years, Off | Max output | Whatever's left after the focus world goes into terraforming (Terraforming). Needs Terraforming Made Easy. |
| Surplus research | On, Off | On | It builds more Research Facilities from the surplus while the speed is worth it. Needs Vaults of Knowledge. |
| Stockpiles, Growth, Military, Shipyard, Expansion, Economy and Wonders rungs | Off, On, Recommend only | Empire mode | Each rung is one step of the money ladder. If a rung is Off, its share goes to the next rung. |
| Shipyard stops at | 1 to 30 | 6 | The Shipyard rung counts the shipyard's unclaimed hulls in the capital's Storage and stops at this many. |

### Building

| Setting | Options | Default | What it does |
|---|---|---|---|
| Over-limit fix | On, Off | On | If a colony's over its industry limit, it takes down its weakest industry. |
| Exports valued by market share | On, Off | On | It values exports by how much of each sector market you hold. |
| Take down unpaying industries | On, Off | On | It takes down any industry that hasn't paid for 30 days. |
| Endgame replaces industries | On, Off | On | It replaces weak industries with production chains, fleets and defense. |
| Allow removals | On, Off | Off | It lets Autopilot replace buildings with better ones. |
| Story points a month | 0 to 50 | 2 | It's used when Story points is on 2 a month, 5 a month or Custom. |

### AI cores and items

| Setting | Options | Default | What it does |
|---|---|---|---|
| Use AI cores | On, Off | On | It puts AI cores from Autopilot Storage into colony buildings. |
| Alpha cores | Only where removable, Anywhere, Never | Only where removable | It sets where Alpha cores can go. |
| Inspection risk | Never inspected, Small colonies only, More where defended, No limit | Never inspected | It sets how many AI cores colonies hold against Hegemony inspections. |
| Hegemony inspections | Advise, Resist where defended, Off | Advise | Resist where defended turns the Hegemony hostile. |
| Alphas in research buildings | First, By value | First | Alpha cores cut research buildings' costs in half. Needs Vaults of Knowledge. |
| Research uses colony items | Off, From storage, From storage and buildings | Off | This only happens while credits are over the surplus line. Needs Vaults of Knowledge. |
| Tech-Mining finds to Autopilot Storage | On, Off | On | Colony items your Tech-Mining digs up go to Autopilot Storage, where they get installed. Turn it off to keep them in Storage for yourself. Blueprints, weapons and cores stay in Storage either way. |

### Colony policies

| Setting | Options | Default | What it does |
|---|---|---|---|
| Hazard pay | Auto, Don't touch | Auto | Auto turns on hazard pay where it speeds growth up enough. |
| Hazard pay growth threshold | 5% to 100% | 25% | Hazard pay goes on where it speeds growth up by at least this much. |
| Hazard pay budget | 1% to 100% of net income | 30% | All your hazard pay together stays within this share of net income. |
| Free port | Auto, Don't touch | Auto | Auto sets a free port where it pays. |
| Senate edicts | On, Off | On | It uses edicts where they pay. Needs Industrial Evolution. |

### People and diplomacy

| Setting | Options | Default | What it does |
|---|---|---|---|
| Administrator hiring | On, Off | On | It hires administrators for colonies without one. |
| Assign administrators optimally | On, Off | On | It places each administrator where their skills pay the most. |
| Among equal stances, first | Worst relations first, Strongest factions, Nearest neighbors, Priority list only | Worst relations first | Ambassadors and agents work on these factions first. Needs Industrial Evolution or Nexerelin. |
| Stop raising relations at | Welcoming, Friendly, Cooperative, Never stop | Cooperative | Ambassadors and agents stop raising relations at this level. Needs Industrial Evolution or Nexerelin. |
| Recruit agents | On, Off | On | It recruits agents (12,000 each, 60 days). Needs Nexerelin. |
| Agents to keep | Auto, 1, 2, 3, 4 | Auto | Auto keeps 2, or 3 with offense. Needs Nexerelin. |
| Autonomy | Off, Recommend, Auto | Empire mode | Recommend names the colony that would help in a money emergency, and Auto only recommends too. Needs Nexerelin. |

### War

| Setting | Options | Default | What it does |
|---|---|---|---|
| Emergency defense fleets | On, Off | On | When a raid is coming or hostiles outweigh a colony's defenses, it sends a defense fleet, paid from credits above the reserve. Without Nexerelin, Auto Colonies raises the fleet itself. |
| Fleet limit per system | On, Off | On | A system with your colonies holds 30 fleets. Past that, traders, nomads, miners, pilgrims, mercenaries and other factions' patrols get sent home, farthest from you first. Your fleets, hostiles and mission fleets stay. A full system gets no standing guard or threat-high defense fleet, only emergencies. |
| Retake your lost colonies | On, Off | On | It works at Retaliate and up. Needs Nexerelin. |
| Top hostile threat as Rival | On, Off | Off | The hostile faction on Auto stance with the highest threat counts as Rival. Needs Nexerelin. |
| Conquered colonies start in | Auto, Approve, Advisor | Empire mode | A conquered colony starts in this mode. Needs Nexerelin. |
| Bombardment targets | Capitals only, Capitals and small colonies | Capitals only | It's used at Scorched. Needs Nexerelin. |
| Coalition risk allowed | On, Off | Off | When it's off, offensives stop before a coalition forms. Needs Nexerelin. |
| Sindrian Diktat raiders | On, Off | On | Colony patrols attack them even with Enforcer patrols off, and it doesn't cost anything or change reputation. |
| Patrol kills count for crises | On, Off | On | Ships destroyed by your Auto colonies' patrols and stations count toward the colony crisis and Tri-Tachyon commerce raiding, the same as your own kills (Colony crises). |
| Enforcer patrols | On, Off | Off | Colony patrols attack the fleet types you set to On. You're charged once for each fleet your forces defeat, and if you fight one yourself you get the game's penalty (Enforcer patrols). |
| Hegemony investigators, Hegemony AI inspections, Persean League enforcers, Luddic sacred protectors, Tri-Tachyon commerce raiders, Nexerelin police | On, Off | Off | These are the fleet types Enforcer patrols attack. Nexerelin police needs Nexerelin. |
| Enforcer cost | Reputation, Credits, Both, None | Reputation | Reputation is the game's penalty for defeating that fleet yourself (-3), and commerce raiders and police don't cost any. Credits is fleet points times Nexerelin's fleet request price (400 without it), paid above the credit reserve. |
| Enforcer patrols in | Systems with Auto colonies, Systems with any colony | Systems with Auto colonies | Colony patrols attack in these systems. |
| Skip your commissioner | On, Off | On | Patrols leave the faction you hold a commission with alone. |
| Skip allies and League membership | On, Off | On | Patrols leave allies alone, and the League too while you're a member. |
| Skip ceasefires | On, Off | On | Patrols leave factions with a ceasefire alone. Needs Nexerelin. |
| Off in Low profile | On, Off | On | Patrols don't attack while Low profile is on. |
| Attack in your fleet's system | On, Off | On | Patrols attack even while you're in the system, and a marked fleet turns hostile to you too. |
| Patrols intercept | On, Off | Off | Colony patrols in the system get told to intercept the fleet. |

### Capital and storage

| Setting | Options | Default | What it does |
|---|---|---|---|
| Capital does | Deliveries and commerce, Deliveries only, Off | Empire mode | Deliveries and Storage gather at the capital, and Off means no capital (Your capital). |
| Trade hub starts at | Net income over 100k, over 50k, over 250k, Right away | Net income over 100k | The capital starts its trade buildings at this point. |
| Make room for trade buildings | On, Off | On | The capital takes down an industry to fit its trade buildings. |
| Gather Storage at the capital | On, Off | On | It gathers Autopilot Storage at the capital. |
| Storage logistics | Off, On | Empire mode | It moves goods to the refit hub weekly (Storage logistics). |
| Ships to the refit hub | On, Off | On | It's part of Storage logistics. |
| Supplies, fuel, crew, marines too | On, Off | On | It's part of Storage logistics. |
| Gather everything at the capital | On, Off | Off | Every Storage you can reach ships everything to the capital's Storage once a week: ships, weapons, wings, commodities, crew, marines, cores, colony items and blueprints. A load costs what a Storage logistics load does. In story mode it comes with Supply and items. |

### Extras

| Setting | Options | Default | What it does |
|---|---|---|---|
| Stable locations | Auto, Recommend only, Off | Empire mode | It builds comm relays, nav buoys and sensor arrays at stable locations. |
| Measured mod buildings | Build when best, Recommend only, Ignore | Build when best | Mod buildings get scored from probe-colony measurements. |
| Trade contracts | Advisor, Auto, Off | Empire mode | Needs Theory of Toolbox. |
| Research | Advisor, Auto, Off | Empire mode | On Auto, it starts the best tech whenever nothing is running (Research). Needs Vaults of Knowledge. |
| Special project supply | Auto, Warn only, Off | Empire mode | It builds a producer for special project goods that no colony makes. Needs Vaults of Knowledge. |
| Megastructures | Follow colony mode, Recommend only, Off | Follow colony mode | It covers Coronal Hypershunt range and restoration. Needs Vaults of Knowledge. |
| Station augments | Follow colony mode, Recommend only, Off | Follow colony mode | Needs Station Augments. |
| Station refits | Once a year, Every 2 years, Every 180 days, Off | Once a year | How often a station's weapons and augments can get changed after its first fit. A new or upgraded station always gets fitted, and Off leaves them alone after that. |
| RAT settlement | Advisor, Approve, Auto, Off | Empire mode | Needs Random Assortment of Things. |
| Found when a site earns | 5,000 to 10,000,000 | 20,000 | It founds a settlement where a site earns at least this much a month. Needs Random Assortment of Things. |
| IndEvo specialties | On, Off | On | Needs Industrial Evolution. |
| VPC chips | On, Off | On | Needs Industrial Evolution. |
| Forge may use up chips | On, Off | On | Needs Industrial Evolution. |
| Courier shipping | On, Off | On | Needs Industrial Evolution. |
| Antimatter Stabilizers | On, Off | On | Needs Emergent Threats IX Revival. |
| Reverse engineering | On, Off | On | It sends weapons and fighters from your Storage to the hub's research. Needs Reverse Engineered Private Arsenal. |

### New colonies

| Setting | Options | Default | What it does |
|---|---|---|---|
| Money for new colonies | Money ladder (Idle money and up), Over 5M, Over 25M, Over 50M | Money ladder | New colonies uses this on Ask first or Auto. |
| Where | Systems you hold, Those first then nearby systems, Within 15 ly of the capital | Systems you hold | Low profile means only systems you hold. |
| Goal of a new colony | Fits its plan, Balanced, Money, Security, War production, Rapid growth, Self-sufficiency, Materials | Fits its plan | A new colony starts with this goal. |
| Method | Nexerelin colony fleet when possible, Autopilot crew only | Nexerelin colony fleet when possible | The colony fleet needs Nexerelin. |
| Most colonies | Colony limit and administrators, 5, 10, 15, 20 | Colony limit and administrators | It stops founding colonies at this count. |
| Relocate weak colonies | Off, Ask first, Auto | Off | Once you're at the Rich tier and the colony limit is the only thing stopping new colonies, it abandons a weak colony (size 4 or smaller) so a much better planet can be settled, one a year at most. The colony's AI cores, items and storage get shipped to the capital, and Ask first puts it up for your approval. |
| Fast buildout | On, Off | On | It builds up a new colony right away. |
| Administrator past the limit | On, Off | On | It hires an administrator for a new colony past the limit. |
| TASC stations | Recommend only, Auto, Off | Empire mode | With New colonies on Auto, Auto stations only recommend while the Expansion rung asks first. Needs Terraforming & Station Construction. |

### Production

| Setting | Options | Default | What it does |
|---|---|---|---|
| Production | Off, Light, Full | Light | Off uses the 0.3.1.0 picks, and Light and Full set the floors below. Light and Full also order colony items through Ashes of the Domain's production. |
| Hull sources | 0 to 3 | 1 | It wants this many ship producers. |
| Hull capacity a month | 0 to 1,000,000 | 100,000 | It wants this much ship production capacity, in credits a month. |
| Order goods within | 3 to 24 months | 6 | It spreads Ashes of the Domain order goods over this many months. |
| Materials colonies | Off, Ask first, Auto | Off | When production orders run short of goods, a Balanced Auto colony gets switched to the Materials goal so it mines and refines for your faction instead of for money (one in five colonies at most). It goes back to Balanced once the orders have what they need. Requires Vaults of Knowledge. |
| Production quality | 0.5 to 1.2 | 0.8 | It wants this ship production quality. |
| Parts reserve | 0 to 1,000 | 100 | It keeps this many Domain-grade Parts beyond the fleet's repair need. |
| Supplies floor | Off, No shortage, Fleet use | No shortage | It wants this much supplies production. |
| Fuel floor | Off, No shortage, Fleet use | No shortage | It wants this much fuel production. |
| Floors at Money colonies | On, Off | Off | Floors also apply at colonies with the Money goal. |
| Floor builds first | On, Off | On | Floor builds rank ahead of other builds. |
| Moves on screen | Monthly summary, Every move | Monthly summary | Storage logistics and AI core moves show as one line each a month, or every move as it happens. starsector.log always has every move. |
| Production line | From 500,000/month, From 1,000,000/month, From 2,000,000/month, From 250,000/month, Off | From 500,000/month | Once your colonies make this much a month, goods they're short of get a producer even when it doesn't pay for itself, and Make room can swap out an industry another colony also has for it. It's at most one swap a month per colony and one a day overall. |
| Make room | Off, Ask first, Auto | Empire mode | A floor producer can replace the weakest income industry. |
| Money taper | Off, Gentle, Strong | Gentle | Money counts for less in the planner at high income. |
| Taper from | 100,000 to 9,900,000 | 300,000 | Gentle uses this, and it stays 100,000 below Taper top from. |
| Taper top from | 200,000 to 10,000,000 | 1,000,000 | Gentle uses this, and it stays 100,000 above Taper from. |
| Same building copies | 0 to 6 | 2 | After this many copies of a building family, the next one counts less (0 is off). |
| Next copy counts | 50% to 100% | 75% | Each copy past that count is worth this much. |
| Goal weights | 0.3.2 table, 0.3.0 table | 0.3.2 table | It weighs each colony goal with this table. |
| Contracts keep floor goods | On, Off | On | Trade contracts leave floor goods alone while their floor is open. |
| Parts to the capital | On, Off | Off | Storage logistics brings Domain-grade Parts to the capital's Storage weekly. |
| Shipyard from Surplus | On, Off | On | The Shipyard rung opens at Surplus while floors are on. |
| Value: hull source | 0 to 10,000,000 | 40,000 | The planner values one hull source at this much a month. |
| Value: capacity credit | 0 to 100 | 0.5 | One credit of capacity is worth this much a month. |
| Value: 1.0 quality | 0 to 10,000,000 | 50,000 | 1.0 production quality is worth this much a month. |
| Value: a part | 0 to 100,000 | 250 | One Domain-grade Part is worth this much. |
| Value: order goods (times) | 0 to 100 | 3 | This multiplies order goods' worth. |
| Value: goods (times) | 0 to 100 | 2 | This multiplies floor goods' worth. |

### Interface

| Setting | Options | Default | What it does |
|---|---|---|---|
| Announcement voice | Machine, Plain | Machine | Announcements use machine or plain wording. |
| Button on the colony screen | On, Off | On | It puts the Colony Autopilot button on your colony's screen. |
| Thinking list | Hide, Show | Hide | The Log page lists what Autopilot weighed. |
| Approval summary | Off, every 15, 30, 45, 60, 90, 120 or 180 days | Every 60 days | You get one dialog with every decision waiting for approval. |
| Build menu hints | Advisor colonies, Every colony not Off, Off | Advisor colonies | It shows Autopilot's ranking beside the game's build menu. |
| Score on open | On, Off | Off | It scores an out-of-date colony once a day when you open its build menu. |

## Empire mode defaults

- In Off mode, everything in this table is Off except conquered colonies, which start in Advisor. The Obligations rung is On in every mode.
- AI cores, administrators, policies, stations, take down, surplus terraforming and IndEvo only run at Auto colonies, with the defaults above.

| Setting | Advisor | Approve | Auto |
|---|---|---|---|
| New colonies, Make room | Off | Ask first | Auto |
| TASC stations, Stable locations | Recommend only | Recommend only | Auto |
| Money ladder rungs | Off | Recommend only | On |
| Surplus investment | Off | Over 3,000,000 | Over 3,000,000 |
| Endgame | Off | From 1,000,000 | From 1,000,000 |
| RAT settlement | Advisor | Approve | Auto |
| Trade contracts, Research | Advisor | Advisor | Auto |
| Special project supply | Warn only | Warn only | Auto |
| Agents (Nexerelin) | Off | Off | Defense |
| Autonomy (Nexerelin) | Recommend | Recommend | Recommend |
| War (Nexerelin) | Off | Defend | Retaliate |
| Conquered colonies start in | Advisor | Approve | Auto |
| Storage logistics | Off | Off | On |
| Capital does | Off | Deliveries and commerce | Deliveries and commerce |

Next: [Supported mods](supported-mods.md).
