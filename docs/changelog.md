# Patch notes

Every version, newest first. If you're wondering why the first version on here is 2.1.0: this started as **Jonah Tweaks**, a personal mod of little tweaks for my own games (bigger battle size, sticky combat autopilot, that kind of thing), and Colony Autopilot grew inside it until it became the main thing. 2.1.0 is when it went up on GitHub, and 2.6.0 was the first version with a downloadable release. The balance tweaks moved out into their own mod in 2.7.0, so this one only manages colonies.

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
