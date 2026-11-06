# Patch notes

Every version, newest first. If you're wondering why the first version on here is 2.1.0: this started as **Jonah Tweaks**, a personal mod of little tweaks for my own games (bigger battle size, sticky combat autopilot, that kind of thing), and Colony Autopilot grew inside it until it became the main thing. 2.1.0 is when it went up on GitHub, and 2.6.0 was the first version with a downloadable release. The balance tweaks moved out into their own mod in 2.7.0, so this one only manages colonies.

## 2.42.0 - 2026-11-06

- **Your capital.** One colony is now your home port, where all your stuff ends up and where you come back to sell loot and refit. The manager picks the colony where your things already are (your production gathering point first), or you pick it on a colony's panel, in Settings, or just by moving your gathering point. Details in [Your capital](capital.md).
- **Everything goes there.** Custom production gets delivered to the capital, and once a week the ships, weapons, wings, blueprints, supplies, fuel and crew in your other Storage tabs get shipped there (the usual storage logistics fee and travel time, one load per Storage). Colony items and AI cores still go where they do the most good.
- **It turns into a trade hub once you're making money.** After the fast buildout, with net income over 100,000 a month (you can change that), the capital's plan puts its trade buildings first: Spaceport and Megaport, Commerce (the Open Market on your own colony), and whatever your mods have: Ashes of the Domain's Trade Outpost and Underworld, Yunru's Bazaar, Industrial Evolution's Requisitions Centre and Salvage Yards, Roider Union's Union HQ. Then a Waystation. They hold industry slots and get built even when they don't pay for themselves, and if every slot's taken the capital takes down its weakest ordinary industry for one, at most one every 90 days (never defenses, fleets or ship production). It waits if your net income goes negative.
- Yunru's Bazaar used to be left to you everywhere. Now it gets built at the capital and nowhere else.
- **A station at every colony.** Stations hardly ever got built unless a colony was on Security or under threat. On Balanced a 250,000 Orbital Station worked out to about -2,000 a month, because its price spread over three years outweighed what its defense and fleet were worth. That's backwards: a station is how a colony fights off fleets, and a raid, bombardment or invasion has to get through it first. Now every colony plans one once its fast buildout is done and it's size 4 (or half a year old), a Battlestation from size 6 and a Star Fortress from size 8 (a size sooner at the capital). They follow your station line, and you can switch it off with **Station at every colony** under Building and planning.

## 2.41.2 - 2026-11-06

- **Administrators for the colony limit actually show up now.** Turns out the game only puts an administrator up for hire when you dock at a market (5% a visit, more at big ones), so friendly markets you never visit never have one, and the manager just kept saying "none for hire" month after month. Now, past your colony limit with nothing on offer for 90 days, it pays a recruiter: a basic administrator for 250,000 on top of the normal hiring cost and fee, showing up 30 days later like any other hire. Expensive on purpose, and it still never dips under your reserve. The "needed" log line now says how many days are left before that happens.

## 2.41.1 - 2026-11-06

- **Easier to read on screen.** 2.41.0 went overboard: the hash tags, the `::` and `//` everywhere, the clipped lines and the blue-ish text made the on-screen lines hard to follow. Now each one is just a short code, a slash, and the normal sentence: `BUILD / Hot Springs: Planetary Shield (750k¢)`, `RECEIVED / Ming Adi: Gamma Core`, `SIEGE / Umuzi: hostile fleets at the colony, defenses only`. Nothing gets cut off, and the text is the game's normal white again (yellow for the important ones), pings included. A line it has no code for just shows the sentence. The panels and settings are unchanged.

## 2.41.0 - 2026-11-06

- **It talks like a machine now.** Everything the autopilot does still pops up on screen as it happens, but as a short readout instead of a sentence: `AP\\ #HOT_SPRINGS :: BUILD // Planetary Shield // 750k¢`. The colony gets a hash tag, the action gets a code (BUILD, INSTALL, SHIP, SIEGE, CLEAR, RESEARCH, LEDGER and so on), big numbers get shortened (750k, 1.25M) and long explanations get clipped. The Log page in the panel still has the full sentence for every entry, so nothing's lost. If you liked the old "Autopilot: ..." lines better, there's a new setting under Settings, Interface: **Announcement voice**, Machine or Plain.
- **It tells you more of what it's thinking.** With the machine voice and every log entry on screen, you also get little pings about stuff it's weighing in the background: a colony's scan when nothing's worth building right now (how many options it looked at and the best one), and inspection points after AI cores move. Each one shows at most once a week per colony, and they stay off the Log page.
- **New look for both panels.** The colony panel and the empire panel in Intel got a cleaner, techy look to go with it: a faint grid behind everything, thin frames with cut corners and bright corner brackets around the page list and the page, section headers like `//  BUDGET`, buttons in caps with cut corners, the open page marked with `>` in the page list, and a status readout under the title. Settings rows read `LABEL  ::  value   // rec ...` with a CYCLE button, and the groups open with `[+]`. It all uses the game's own UI colors for your faction, so it still looks like Starsector.

## 2.40.1 - 2026-11-06

- **Economic stations.** A colony in a system that's well defended (other stations and military bases there that aren't hostile to you, yours or anyone's) keeps its station economic even when its own threat is high: the Mining Station augment, Hydroponics and Economy Conversion over combat augments. A raid or blockade on its way still gets the combat loadout. This only changes the station's augments, not what the colony builds.
- Station Augments' **Mining Station** augment counts now, valued with that mod's own formula (the asteroid belts and rings near the station), only for what it mines above what the colony already makes.
- **Terraforming & Station Construction stations.** If you have TASC, Colony Manager can found its mining stations (in an asteroid belt or field) and siphon stations (over a gas giant) as new colonies, in systems with your Auto colonies that are calm or well defended, once TASC's research for them is done and there's room under your colony limit. A crew builds it for TASC's own costs plus a fee, in 60 days, the same way TASC's Construct abilities place it, and the new colony runs in Auto with the Money goal. It's Recommend only out of the box (it just tells you the best site) until you switch it to Auto under Settings, **Station construction**. The astropolis is left to you, it's a habitat rather than an economic station.

## 2.40.0 - 2026-11-06

- **Stable locations.** The stable spots in a system (where comm relays, nav buoys and sensor arrays go) get used now. In any system with your Auto colonies, Colony Manager works out what each one is worth to the colonies there, going by their goals, and sends a construction crew to build the best one: a comm relay first for most goals (+1 stability at every colony of yours in the system), then a nav buoy (+2 burn, worth more to Security colonies), then a sensor array. It costs vanilla's materials at base price plus a fee, takes 10 days, and only builds one of each kind per system since they don't stack. If the spot gets taken before the crew shows up, you get the materials back.
- With **Terraforming Made Easy**, makeshift ones you own get upgraded to Domain-era (twice the stability for the relay) when there's a spare Beta core in Autopilot Storage and it pays for itself.
- With **Ashes of the Domain**, once you've researched them and have a Coronal Hypershunt Tap in Autopilot Storage, it builds Hypershunt Receivers (+2 industry slots at every colony in the system) in systems your hypershunt reaches. While you own a hypershunt, one stable spot per system is kept free for one.
- New setting under Settings, **Stable locations**: Auto (default), Recommend only, or Off. Crews on their way show on the Autopilot Storage page.

## 2.39.3 - 2026-11-06

- **Panopticon cores** (Emergent Threats: IX Revival). The Panopticon Instance and Panopticon Core couldn't go in Autopilot Storage at all. Now they can, and once a day one goes into a Surveillance Center that doesn't have one (the Instance first, and the colony with the most of your colonies in its system first), shipped over first if it's stored at another colony. Whatever regular core was in there goes back to storage. The Hegemony doesn't count either of them for inspections.

## 2.39.2 - 2026-11-06

- Fixed: Autopilot Storage took stuff the manager has no use for. UAF's Access Key Cards (the ones you turn in at the Aurorans for credits) went right in, and so did blueprint packages once you knew everything in them, Niko's building blueprints and so on. Now only things it can actually use go in: colony items it can install, AI cores, databanks, Antimatter Stabilizers, VPC chips and recipes. Anything else that's already in there gets moved to that colony's normal Storage, with one log line.

## 2.39.1 - 2026-11-05

- **The Unification Center waits for the rest of its world.** By itself it's +1 stability for a pile of upkeep plus marines and supplies; its whole job is the final conversion (Hive World and so on), which needs every other building and condition in place first. It used to go up as soon as research unlocked it, which is way before the Geomorphology Station (Global Centralization comes before Surface Restructuring), then just sat there for months eating upkeep and hogging the colony's one construction slot. Now it waits until everything before it in the plan is done, or the last condition is already being made by a running project, so it's built during that last project and ready about when the world qualifies. The colony panel says "waits until the rest of the plan is ready" in the meantime.

## 2.39.0 - 2026-11-05

- **Storage logistics** (new, off until you switch it on, under Settings). Pick a refit hub (your production gathering point by default) and once a week everything you'd refit a fleet with gets shipped there from your other Storage tabs: mothballed ships, weapons, fighter wings, blueprints and hull mod specs you don't know yet, and supplies, fuel, crew and marines if you want. Pick a sell hub too and blueprints and specs you already know, plus spare copies, go there, so you can sell them all in one place. Fair play like everything else: each load costs a shipping fee (plus 1,000 a ship) from the monthly budget and takes travel time.
- **Production orders get their goods.** Ashes of the Domain's production orders (Jonah's Auto Outfitting's big weapon orders, say) take goods from your colonies, and crawl when nobody makes one. Now Colony Manager plans producers for any good your open orders need that none of your colonies make, the same way it already did for special projects. It also knows the plain vanilla producers now (Refining for metals, Heavy Industry for heavy machinery and supplies, and so on), not just AotD's.
- **Unspent budget is saved up now.** The monthly budget didn't carry over, so anything that cost more than one month's budget (a lot of buildings early on, when the budget's around 60k) just sat there waiting while your credits piled up, and the colony didn't build much of anything. Now whatever's left each month carries over, up to six months' worth, so it saves up and builds the thing. The budget lines show what's saved up.
- Mod watch only writes to the log when something changes, instead of ten identical lines every week.
- Fixed: once a colony got Luddic Path cells, every mod building came up "can't be measured". The test colony Colony Manager uses to measure buildings copies your colony's conditions, and Pather cells (like pirate activity, a blockade and the other event conditions) break without the event behind them. Those are left off the test colony now, and if any other condition ever breaks it, the measurement goes ahead without it.
- **Colony items go to the best building first.** Items used to go in storage order, so a lesser item could grab a building a better one was waiting for and get swapped out a week later (an Auroran nanoforge into Heavy Industry, then the Tenebrian one), which costs a fee and a day each time. Now every waiting item is matched against every free building and the best pairs go first.
- Fixed: a second copy of an item could get shipped to a colony whose first copy had already arrived but wasn't installed yet (two Orbital Fusion Lamps and two Fullerene Spools to Umuzi). A copy that's already there now gets the building, and nothing else gets shipped for it.
- **Research Facility first.** With research on Auto and no Research Facility anywhere, one now goes first in your focus world's plan (or your biggest colony's). Before, spending freely put every defense structure first, so the Research Facility waited months behind a Planetary Shield and a Patrol HQ, nothing got researched, and plan steps that needed research gave up.
- Roider Dives and Union HQ: Roider takes a heavy machinery shortage off every mined good, so being 1 short turns 1+1+1+1 into nothing. Colony Manager counts that now when it decides whether a Dives is worth building, and mod watch names the shortage instead of calling it a mismatch.
- The diagnostics report has a colonies section: each colony's plan and where each step stands, its buildings with their items and cores, the construction queue and what's in its Autopilot Storage.
- **Contracts are safe from the endgame.** The endgame took down Hot Springs's Farming for Light Production, and the big food contract Farming was feeding started missing deliveries. A building that makes something one of your trade contracts needs is never taken down while that contract runs now.
- Fixed: VPC chips for Industrial Evolution's Commodity Forge (and the other chip buildings) did the same double shipping as colony items, and worse: a different chip each day, every kind twice, for one forge. A chip that's already at the colony goes in first now, and nothing else is shipped while one's on its way.
- Mod watch: an Omega core in Patrol HQ or High Command doubles fleet size there, not output, so it no longer warns that the doubled output is missing. Roider's check counts AI cores now (an Alpha or Omega adds 1 to each mined good, and an Omega doubles it), and lists each good, mined against expected.

## 2.38.0 - 2026-11-05

A bunch of new colony mods supported, a big fix for how mod buildings get measured, and a pass to make sure nothing ever gets stuck waiting on a mod (or a building) you don't have.

**New mods supported** (all optional, any mix works, details in [Supported mods](supported-mods.md)):
- **Omega Core Everywhere** and **Astral Ascension Renewed**: drop Omega cores in Autopilot Storage and they go where they do the most. In a building with Omega Core Everywhere (they don't count toward Hegemony inspections there), or governing a colony with Astral Ascension Renewed, but only where your Inspection risk setting allows its 10 inspection points. The manager never swaps out an AI core administrator, so it'll never set off that mod's civil war.
- **Niko's Scavengable Industries**: only blueprints you've learned get built, and the ones that do big things you'd want to decide on (Blast-Core Mining eating your deposits, the spy buildings, the Cryosanctum's crisis, the size-5 Planetary Defense Network and a few more) are left to you.
- **Junk Yard Dogs** and **Carter's Freetraders**: their forges and Biolab get built when they pay. Drugs and hand weapons your faction bans don't export without a free port now, from any mod's building, so they only count for what the colony needs itself.
- **Vulpoids**: Organ Farms and the Biotech Facility, the Midas Nanoforge (never polluting a habitable world), the Mangonut tree, the Nanofiltration Core where it actually clears something, the Lobster Bioforge only where it doesn't kill the organs, and Profecto administrators. The Vulpoid Bioforge itself is left to you, since it starts the whole sanctions and Knights of Ludd story.
- **Yunru Industries**, **Slightly Better Tech-Mining** and **Yunru's Worlds**: Secondary Drydocks valued by what it adds next to your Heavy Industry, Tech-Mining valued with Slightly Better Tech-Mining's much bigger numbers, Advent Outreach never improved (it does nothing), and the Bazaar left to you.
- **Roider Union** got rechecked against 2.3.1: a couple of Union HQ numbers were off, and the Historical Roider Charter's extra output and range count now.

**Mod watch.** The Diagnostics page (and the report file) now says how each of those mods is doing in your game, weekly: OK, WARN or FAIL, what's built where, whether measured buildings really make what the test colony said, and checks of its own for each mod (is the Omega core's double output actually applied, does the Omega-governed colony's size limit really go to 15, and so on).

**Fixed:**
- Mod buildings measured on the test colony were getting their output cut by fake shortages, since the test colony isn't in the economy and nothing's ever for sale there. Anything that needs inputs measured as making little or nothing (AotD's Artisanal Farming read zero food). Now the test colony has everything on hand, and inputs are counted separately like they always were.
- The Paradise plan was missing Light Industry, Commerce and Waystation since 2.30.0 (they'd slipped into a code comment).
- If Building Menu Overhaul's code ever can't be read (after an update, say), the manager used to build nothing at all anywhere. Now only what research would lock stays locked.
- Fast buildout never ended for a colony that couldn't turn a profit, and that colony never got its plan, defenses or anything else. Now a day with nothing that pays runs the normal plan, and the buildout ends after 60 days of that or a year in total.
- Hostile fleets that just live in a system (Remnant, IX patrols, a pirate base) kept a colony "under siege" forever, building nothing but defenses. After 30 days of only that, it's treated as how the system is. Fleets at the colony, blockades and raids still count.
- The Bifrost Control Center step could never be built, so the Bifrost plan waited forever.
- On a Floating City short on fuel, even the Fuel Production that would fix it was blocked.
- A station line whose first station needs a blueprint you don't have meant no station at all; that colony picks by score now.
- Special project producers the manager doesn't build don't sit in the plan anymore; you get a log line instead.
- Surplus terraforming's markers on hazards it wants to keep now expire even with surplus terraforming off.
- With Vaults of Knowledge, the first Research Facility is worth building when your plans have research-locked steps, even before research has ever run.

**Nothing gets stuck anymore.** Every plan step gets checked each day for a way to it: can it (or something on the way to it) be built here, or will research the manager is running unlock it? A step with no way for 60 days stops holding the plan's top spot and its industry slot, and the log tells you. It counts again as soon as a way opens. A Terraforming Made Easy world whose own buildings are out of reach gets dropped for a year and the colony follows the plain plan for its goal. A world that needs Mining or Farming isn't picked where those can never go. And the slot a plan keeps free now counts one per upgrade chain (Heavy Industry and Orbital Works used to reserve two).

## 2.37.0 - 2026-11-05

The fair play release. A review went through everything the mod does and anything that gave you something for free, or changed the game's rules, got fixed or removed.

- **Autopilot Storage is per colony now.** It used to be one shared storage at every colony, which meant items and cores basically teleported across the sector. Now each colony has its own, and anything the manager needs somewhere else gets shipped there for a fee and some travel time (a couple of days plus a day a light-year). Your old shared storage gets moved into your biggest colony's.
- **Agents are recruited, not conjured.** They used to be created on the spot. Now recruiting one costs 12,000 plus the fee and takes 60 days. They also get roles: the first one on security, the second raising relations with your preferred factions, and with offense on a third that sabotages whoever you set as the target (whoever attacks you, a random enemy, or a faction you pick). Enemy operations the game hides from you aren't shown or used anymore.
- **Remote stuff takes time.** Administrators and IndEvo ambassadors always take 30 days to arrive (that used to be optional). The RAT settlement gets founded by an expedition that has to get there. Station augment changes from afar need a refit crew, 7 days, or right away if your fleet's in the system. Research items and databanks from other colonies or your fleet get shipped to the Research Facility.
- **Removed:** the storage cheat and the Derelict Empire attack settings. Both changed the game's rules, so they're not in this mod anymore.
- **Fixed:** relief fleets were way underpriced, now they cost what Nexerelin charges. Taking back a colony an old version made autonomous now costs the normal unrest. Surplus terraforming could delete a core or item when it took a structure down. Every measured mod building was being charged an extra 25% of the colony's upkeep because of a test colony quirk. A few places where an error could have reached the game's UI are now caught.

## 2.36.3 - 2026-11-05

- Wording: the storage cheat's description just says what it adds (commodities, weapons and fighters) instead of listing ships and ambassadors, which never went in storage like items anyway.

## 2.36.2 - 2026-11-05

- A nanoforge that pollutes never goes into a forge on a habitable world anymore, whatever the colony's goal, threat or the endgame. Before, it only counted against it, and a War production colony barely minded, so it could happen.
- If one is already in there and its pollution can still be undone (under 90 days), it comes back out to Autopilot Storage. Lock the building to keep it.
- A forge upgrade that would pollute a habitable world again is shown but never started for you.

## 2.36.1 - 2026-11-05

- Campaign consumables and blueprints can't go into Autopilot Storage anymore (Industrial Evolution's consumables, ship and weapon blueprints, Random Assortment's Forge-Assisted Industry BP, anything that's used up on right click). Any already in there get moved to your biggest colony's normal Storage.
- New option, off by default: **Research spends colony items**. With surplus credits, Auto research can spend items like Pristine Nanoforges, taken from storage, or also out of buildings (the one where the item does least first, never locked ones), but only once everything the tech needs is on hand.

## 2.36.0 - 2026-11-05

- **Endgame economy.** Once you're spending freely and making 1M a month or more, money stops being the goal. Credits count 5%, fleets, ship quality and defense count a lot more, and what a building makes is valued as production chains: goods none of your colonies make first, then your own shortages, then taking more of the sector's production, spread across your worlds.
- With no free slot, an Auto colony can replace its weakest non-military industry with something worth a lot more, slowly (one a day, one a month per colony). Cores and items from the old building go back to storage.
- It keeps at least a third of the net income it started at.
- Research Facilities keep going up while research is left, and cores and items use the endgame weights.

## 2.35.0 - 2026-11-05

- **War fleets** (Nexerelin). Relief fleets for colonies under siege, defense fleets where the threat is high or a crisis is coming, base strikes on known pirate and Pather bases near your colonies, and raids on whoever attacked you in the last 120 days. Never invasions or colony expeditions, and only against factions already hostile to you.
- One a day normally, keeping 40% of your fleet request points for emergencies. While spending freely it's up to three a day, bigger, every known base, and a standing defense fleet at every colony.

## 2.34.3 - 2026-11-05

- Autonomy recommendations are always visible now (they only showed in a money emergency before). Autonomy is Off, Recommend or Auto, money only, and never used to get around the colony limit.

## 2.34.2 - 2026-11-05

- Every log entry shows on screen again, just as one short line. 2.34.0 took most of them off the screen, which went too far. New setting to choose every entry, important only, or off.

## 2.34.1 - 2026-11-03

- Spending freely runs on the pool, not on income. Removed the pause when net income dropped under half its best month (one weak month stopped everything with 60M in the bank), and new upkeep can now reach a sixtieth of the pool each month.

## 2.34.0 - 2026-11-03

- **Wonders and gates** while spending freely (Ashes of the Domain): the Daedalus Array, and the Bifrost Control Center as a plan step followed by a gate in each of your systems, each only once your colonies make every good it needs.
- Research can spend Fullerene Spools from any colony's Storage while spending freely.
- Much shorter log. Things that flip back and forth (sieges, threat, policies) log at most every 10 days, and the day's trade contracts are one line.

## 2.33.0 - 2026-11-03

- Spending freely goes all out: build prices count 5%, every colony maxes its station, Heavy Batteries, Planetary Shield, Megaport and Patrol HQ, colonies re-plan daily with up to three upgrades at once.
- Surplus terraforming goes for the richest deposits instead of trace ones, adding tectonic activity first when a deposit needs it.
- Producers for Ashes of the Domain's advanced goods get built even with no special project running.

## 2.32.0 - 2026-11-03

- **Spending freely.** Past 10M over the surplus line, every Approve and Auto colony spends from the surplus, not just the focus world. Defense, stability, growth and access buildings go up even when they cost more than they earn, hazard pay goes wherever it helps, and terraforming runs up to three projects per colony.

## 2.31.0 - 2026-11-03

- United Aurora Federation colony items, valued from what they actually do. The Rice Cooker, Dimensional Stove and Access Router are never installed since they don't help.

## 2.30.0 - 2026-11-03

- Fixed daily checks only running once per loaded save (terraforming plans, research ranking, the focus world, siege checks and more), which caused some expensive loops.
- Fixed surplus terraforming undoing its own work and looping (pollution and high gravity being added back).
- **Special project supply** (Vaults of Knowledge): goods a black site project needs that nobody makes get their producer built as a plan step.
- Research lists the techs that take colony items and where those items come from.

## 2.29.0 - 2026-11-03

- AI cores in Research Facilities and Black Sites are valued by what they really do there, and Alpha cores go into research buildings first.
- **Surplus research**: more Research Facilities from the surplus, up to 8.

## 2.28.0 - 2026-11-03

- Station augments are valued by what each one does on that specific station (its guns, fighters, shields and AI core), instead of one rating for every station. A few useless or harmful ones are never picked.

## 2.27.0 - 2026-11-02

- Stations from other mods (Interstellar Imperium, Knights of Ludd, Ashes of the Domain) count like vanilla stations everywhere. New Imperium station line.

## 2.26.0 - 2026-11-02

- **Surplus terraforming** at every Auto colony, planned on TME's own rules so it never trades away something the colony needs. Structures it builds come down again once their work is done.

## 2.25.0 - 2026-11-02

- Auto research no longer sits idle when no tech pays for itself; it starts the best of the rest, and the log says why when nothing can start.

## 2.24.0 - 2026-11-02

- The new upkeep cap is Automatic by default (10% of net income, at least 20,000) so it keeps up as you grow.

## 2.23.0 - 2026-11-02

- A fix for another mod's Income report moved out into its own mod. Colony Manager only does colony stuff.

## 2.22.0 - 2026-11-02

- The colony screen button stays off the refit and fleet screens.
- Surplus investment keeps back credits already promised to custom production.
- Diagnostics gets a section for my other mods, and stuck colonies get a monthly explanation in the log.

## 2.21.0 - 2026-11-02

- **Surplus investment**: credits over a line go into one focus world.
- Each colony offers several builds at once, so one held-back build doesn't stall it.
- Better items swap in for lesser ones, and items waiting in storage can get the building they need built.

## 2.20.1 - 2026-11-01

- The story point monthly cap counts story points instead of improvements, cheapest first.

## 2.20.0 - 2026-11-01

- The Orbital Fusion Lamp only goes where it actually helps instead of just heating a planet up.
- The hazard pay limit goes to 30% of net income.

## 2.19.0 - 2026-11-01

- Growth techs count the growth they give, research stops waiting on databanks after 90 days, research-locked plan steps stop hogging the plan's priority, and plan terraforming can go past the per-project cap.

## 2.18.1 - 2026-11-01

- Fallbacks for any mix of mods: plan steps from mods you don't have are dropped, and a first Research Facility gets built when research is what's missing.

## 2.18.0 - 2026-11-01

- Research works toward whole chains of techs that colony plans need, instead of just the next step.
- Special-world plans only get picked when the colony can actually fit them.

## 2.17.0 - 2026-11-01

- Hazard pay goes to the colonies it speeds up the most (new colonies never got it before).
- Autonomy became the player's call.

## 2.16.1 - 2026-11-01

- Colonies it made autonomous get taken back as soon as there's room.

## 2.16.0 - 2026-11-01

- Going past the colony limit gets an administrator, never autonomy. Founding a third colony made it autonomous right away before, which was bad.

## 2.15.0 - 2026-10-31

- Station augments swap to combat when a colony is threatened and back to output when it calms down.

## 2.14.1 - 2026-10-31

- The colony screen button stays with the colony screen instead of floating over other screens.

## 2.14.0 - 2026-10-31

- **Colony crises** priced into everything, a Colony crises page, Hegemony inspection orders, and Never inspected became the recommended core limit.

## 2.13.1 - 2026-10-31

- The attack spacing settings only show up in a Nexerelin Derelict Empire start.

## 2.13.0 - 2026-10-31

- Optional settings to cap pirate and crisis fleets per system and space out Nexerelin attacks on your colonies.

## 2.12.0 - 2026-10-31

- **Sieges**: colonies under attack only get defenses until it's over. Trade contracts skip goods that can't be shipped.

## 2.11.0 - 2026-10-31

- Research moves to the Autopilot Storage page and takes what it needs from storage.
- The storage cheat (off unless you turn it on).

## 2.10.0 - 2026-10-31

- AI cores placed by value against Hegemony inspection risk, administrators placed by cost, and emergency autonomy.
- One Mod features page instead of a page per mod.

## 2.9.0 - 2026-10-31

- Panels show one page at a time from a list on the left instead of one giant scroll.
- The Agents page shows every agent you have and lets you hand yours over.

## 2.8.0 - 2026-10-30

- The **Colony Autopilot button** right on the colony screen, and its own Intel filter.
- Recommended settings that work if you only switch colonies to Auto, with the new Automatic budget.
- Fixed upkeep being predicted 500 times too high, which had it building whatever had the least upkeep.
- Only builds that pay for themselves get built, and Nexerelin outposts are left alone.
- Fast buildout ranks by money only and never builds defenses.

## 2.7.0 - 2026-10-30

- **Fast buildout** for new colonies.
- **Nexerelin agents.**
- Battle size and sticky combat autopilot moved out to a separate mod, so this one makes no balance changes.

## 2.6.0 - 2026-10-30

- First downloadable release.
- Fixed every reflected integration (Ashes of the Domain and others) failing in game because of the game's script loader. This fix was first built as 2.5.1, which only ever ran on my own game.
- Health checks for each integration, calibration of estimates against your real buildings, and a breaker that pauses anything that keeps failing.
- The Getting started guide.

## 2.5.0 - 2026-10-30

- Support for a bunch of colony mods from the Mod Index: Terraforming & Station Construction, Grand.Colonies, Substance.Abuse, Volkov Industrial Conglomerate, Roider Union, Planetary Shield: Access Control, Unknown Skies, What We Left Behind and Astral Ascension.
- Any mod's deposits and colony items through the game's own tables, and no builds past 12 buildings.

## 2.4.1 - 2026-10-30

- Renamed from Jonah Tweaks to Jonah's Colony Manager. Same mod id, so saves keep working.

## 2.4.0 - 2026-10-30

- Ashes of the Domain: research, management styles, the Coronal Hypershunt, Pluto, Nidavelir and its colony items.

## 2.3.0 - 2026-10-30

- Ashes of the Domain industry synergies and trade contracts.

## 2.2.0 - 2026-10-30

- Station Augments support.

## 2.1.1 - 2026-10-30

- Fixed a crash in the colony panel when a colony was planning for Security.

## 2.1.0 - 2026-10-30

- First version on GitHub: Colony Autopilot for Starsector 0.98a, with optional support for Terraforming Made Easy, Industrial Evolution, Nexerelin, Emergent Threats, Ashes of the Domain and Random Assortment of Things.
