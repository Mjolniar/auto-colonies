# War and security

## Quick version

- Auto Colonies keeps track of threat and handles sieges and colony crises for you.
- With Nexerelin, you also get emergency defense fleets, agents, a War level (default Retaliate) and a stance for each faction (default Auto) that caps it.
- [Low profile](low-profile.md) caps the War level at Defend.
- [Enforcer patrols](#enforcer-patrols) has your colony patrols go after enforcement fleets (off by default).
- [Patrol kills](#colony-crises) makes kills by your Auto colony patrols count toward colony crises (on by default).

## Threat

- Threat goes up with hostile neighbors, crises, raids and Pather cells.
- Once threat hits 70%, the colony gets planned as Security. It stays that way for at least 60 days, then goes back to its own goal once threat's under 65%.
- A colony's under siege when it's blockaded, when hostile fleets are at the planet or stronger ones are in the system, or when a raid, invasion or blockade is on the way. Then it'll only build defenses.

## Colony crises

- Crisis progress is priced at about 640 credits a month for each point of monthly progress.
- AI cores (Hegemony, Pathers) and Fuel Production (Diktat) get charged that price, and Patrol HQ, Military Base and High Command get credited.
- When the next crisis gets picked (meter at 500), its target is planned as Security. If it's a Pather target, it sheds its AI cores, and any free ports the crisis counts get closed.
- With Patrol kills, ships that Auto colony patrols and stations destroy in battles without your fleet count toward the colony crisis and Tri-Tachyon commerce raiding.
- They use the same rules and amounts as your own kills, so that's fleet points lost near your colonies, pirate and Luddic Path base stations, commerce raiders and Tri-Tachyon trade fleets.
- With Nexerelin, Nexerelin's crisis rules apply, so outposts don't count as colonies and any base station counts.
- Each lost ship counts once, across autoresolve rounds and saves, and ships the game counted in a battle you fought don't get counted again.
- The setting is Patrol kills count for crises (Advanced, War, and LunaLib), and it's on by default in Story and Normal mode.

## Enforcer patrols

- The settings are under Advanced, War. Enforcer patrols and each fleet type are off by default.
- The fleet types are Hegemony investigators, Hegemony AI inspections, Persean League enforcers, Luddic sacred protectors, Tri-Tachyon commerce raiders and Nexerelin police (with Nexerelin).
- To find them, it goes by the game's memory flag on the fleet, the inspection's intel for inspection task forces and the hassle type for League blockade escorts.
- It works in systems with Auto colonies (default) or in systems with any colony of yours.
- It checks four times a day. A target's made hostile to your faction for one day, and that gets renewed while it stays, so colony patrols and stations engage it.
- That ends when the fleet leaves the system, a gate applies or you turn the setting off.
- It leaves alone your faction's fleets, support fleets, stations, factions already hostile to you and fleets the game already made hostile to your faction.
- The gates are your commissioner, allies and League membership, ceasefires (Nexerelin) and Low profile, and each one's on by default.
- Attack in your fleet's system is on by default, and a marked fleet's hostile to your own fleet too.
- Patrols intercept (off by default) gives colony patrols in the system an intercept order.
- A fleet gets charged when it loses a battle to your faction's fleets, and only once in its life, even if it gets marked again. A battle you fight yourself only gets the game's own penalty.
- Enforcer cost can be Reputation (default), Credits, Both or None.
- Sindrian Diktat raiders get handled like pirate raids at all your colonies in Story and Normal mode. They have their own setting, and it's on by default even with Enforcer patrols off.
- There's no gate, credit charge or reputation change for them, and emergency defense fleets and sieges count them as hostiles.

| Fleet type | Game's penalty when you defeat it | Reputation charge |
|---|---|---|
| Hegemony investigators | -3, down to Vengeful (low rep impact) | -3 |
| Hegemony AI inspections | -3 with transponder off. -3 and at best Hostile with it on | -3 |
| Persean League enforcers | -3, down to Vengeful (low rep impact) | -3 |
| Luddic sacred protectors | -3, down to Vengeful (low rep impact) | -3 |
| Tri-Tachyon commerce raiders | None (Independent, no rep impact) | None |
| Nexerelin police | None (Independent, no rep impact) | None |

- On Credits, the charge is fleet points x Nexerelin's fleet request price per point (default 400), or 400 per point without Nexerelin.
- It's paid from the Military rung, the surplus while rich or the month's budget, and only above the credit reserve.
- In Credits mode, it only attacks a fleet it can pay for above the reserve.
- If it can't pay a charge, the charge gets owed and paid first, and there are no new attacks until then.
- The log shows each attack and charge. Ended attacks and ignored fleets only go to starsector.log.

## Nexerelin

### War level

- This is under Settings, War, and each level adds to the one before it.

| Level | Adds |
|---|---|
| Defend | Relief for besieged colonies. Defense fleets at high threat. Strikes on pirate and Pather bases within 15 ly. |
| Retaliate | Raids on markets that attacked you in the last 120 days. Retake your lost colonies. |
| Hold them down | Raids and blockades on military and other markets within 15 ly (any distance while rich), 2 per faction. |
| Conquer | Invasions of Enemy markets costing at most a quarter of the surplus, and of Destroy markets, capital last. |
| Scorched | Saturation bombardment of a Destroy capital after Authorize bombardment. |

- Retake means invasions of your former colonies, with a lost capital first, and a retaken colony keeps its old mode.

### Faction stances

| Stance | War level cap | Relations | Ceasefire and peace offers |
|---|---|---|---|
| Ally, Friendly, Neutral | Defend | Raised, also while hostile | Accepted |
| Auto | Retaliate | By ambassador focus. Not raised while hostile | Left to you |
| Rival | Hold them down | Not raised | Left to you |
| Enemy, Destroy | Conquer, Scorched | Not raised | Rejected at Retaliate+ |

- Enemy and Destroy only act on a hostile faction, and until you declare war they act as Rival.

### Diplomacy


### Conquest

- Invasion fleets go out at 1.25x Nexerelin's wanted fleet, one at a time (two while rich).
- Invasions stop when infamy x (1 + dominance) hits 75, but turning on Coalition risk allowed (default Off) lifts that.
- On a conquered colony, autonomy gets taken back the same day, the mode comes from Conquered colonies start in (default Auto), and the goal's Security until it reaches stability 5.

### Scorched earth

- Bombardment targets can be Capitals only (default), or Capitals and small colonies (size 4 or less).

### What it costs

- The fee is fee level x 10% of Nexerelin's price.
- It keeps 40% of fleet request points for emergencies (10% while rich) and does one launch a day.
- While rich, it can do up to 3 launches, fleets are 1.5x bigger and there's a standing defense fleet at every colony.

### The War page

- The War page on the empire panel shows the War level, fleet request points, infamy, money sources, conquered colonies, stance buttons for each faction and operations.

- Stand down sets the War level to Defend, clears declarations and bombardment, and cancels offensives that are still organizing (full refund).
- Recall all also cancels offensives that are travelling (67% refund) or acting (33%), except invasions that already have troops landed.

### Agents

- Agents can be Off, Defense (recommended) or Defense and offense. Recruiting one costs 12,000 plus the fee and takes 60 days.
- Offense adds sabotage, isolating Enemy and Destroy, and rebellion at the next Destroy invasion target.

Next: [Research](research.md).
