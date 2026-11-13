# War and security

## Quick version

- Auto Colonies: tracks threat and handles sieges and colony crises.
- With Nexerelin: emergency defense fleets, agents, a War level (default Retaliate) and a stance per faction (default Auto) that caps it.
- [Low profile](low-profile.md): caps the War level at Defend.
- [Enforcer patrols](#enforcer-patrols): colony patrols attack enforcement fleets. Off by default.

## Threat

- Threat: counts hostile neighbors, crises, raids and Pather cells.
- At 70%: the colony is planned as Security. Under 65% after 60 days: it returns to its own goal.
- Siege: blockade, hostile fleets at the planet or stronger ones in the system, or a raid, invasion or blockade on the way. Only defenses are built.

## Colony crises

- Crisis progress: priced at about 640 credits a month per point of monthly progress.
- Charged: AI cores (Hegemony, Pathers) and Fuel Production (Diktat). Credited: Patrol HQ, Military Base, High Command.
- Next crisis picked (meter at 500): its target is planned as Security, a Pather target sheds AI cores, counted free ports close.

## Enforcer patrols

- Settings: Advanced, War. Enforcer patrols Off by default. Each fleet type Off by default.
- Fleet types: Hegemony investigators, Hegemony AI inspections, Persean League enforcers, Luddic sacred protectors, Tri-Tachyon commerce raiders, Nexerelin police (with Nexerelin).
- Finding: by the game's memory flag on the fleet. Inspection task forces: by the inspection's intel.
- Where: systems with Auto colonies (default), or systems with any colony of yours.
- Checks: four a day. A target is hostile to your faction for one day, renewed while it stays. Colony patrols and stations engage it.
- End: the fleet leaves the system, a gate applies, or the setting is turned off.
- Left alone: your faction's fleets, support fleets, stations, factions already hostile to you, fleets the game already made hostile.
- Gates, each On by default: your commissioner, allies and League membership, ceasefires (Nexerelin), Low profile, the system your fleet is in.
- Your fleet: a marked fleet is hostile to your fleet too.
- Patrols intercept: Off by default. On: colony patrols in the system get an intercept order.
- Charge: once per fleet, when it loses a battle to your faction's fleets. A battle you fight yourself gets only the game's own penalty.
- Enforcer cost: Reputation (default), Credits, Both, None.

| Fleet type | Game's penalty when you defeat it | Reputation charge |
|---|---|---|
| Hegemony investigators | -3, down to Vengeful (low rep impact) | -3 |
| Hegemony AI inspections | -3 with transponder off. -3 and at best Hostile with it on | -3 |
| Persean League enforcers | -3, down to Vengeful (low rep impact) | -3 |
| Luddic sacred protectors | -3, down to Vengeful (low rep impact) | -3 |
| Tri-Tachyon commerce raiders | None (Independent, no rep impact) | None |
| Nexerelin police | None (Independent, no rep impact) | None |

- Credits: fleet points x Nexerelin's fleet request price per point (default 400). 400 per point without Nexerelin.
- Paid from: the Military rung, the surplus while rich, or the month's budget. Only credits above the credit reserve.
- Credits mode: a fleet is attacked only when its price is available above the reserve.
- Owed: a charge that can't be paid is owed and paid first. No new attacks while credits are owed.
- Log: each attack and each charge. Ended attacks and fleets left alone: starsector.log only.

## Nexerelin

### War level

- Settings: War. Each level adds to the one before.

| Level | Adds |
|---|---|
| Defend | Relief for besieged colonies. Defense fleets at high threat. Strikes on pirate and Pather bases within 15 ly. |
| Retaliate | Raids on markets that attacked you in the last 120 days. Retake your lost colonies. |
| Hold them down | Raids and blockades on military and other markets within 15 ly (any distance while rich), 2 per faction. |
| Conquer | Invasions of Enemy markets costing at most a quarter of the surplus, and of Destroy markets, capital last. |
| Scorched | Saturation bombardment of a Destroy capital after Authorize bombardment. |

- Retake: invasions of former colonies, lost capital first. A retaken colony keeps its old mode.

### Faction stances

| Stance | War level cap | Relations | Ceasefire and peace offers |
|---|---|---|---|
| Ally, Friendly, Neutral | Defend | Raised, also while hostile | Accepted |
| Auto | Retaliate | By ambassador focus. Not raised while hostile | Left to you |
| Rival | Hold them down | Not raised | Left to you |
| Enemy, Destroy | Conquer, Scorched | Not raised | Rejected at Retaliate+ |

- Enemy and Destroy: act only on a hostile faction. Until you declare war they act as Rival.

### Diplomacy


### Conquest

- Invasion fleets: 1.25x Nexerelin's wanted fleet. One at a time, two while rich.
- Invasion stop: infamy x (1 + dominance) reaching 75. Coalition risk allowed (default Off) lifts it.
- Conquered colonies: autonomy taken back the same day. Mode set by Conquered colonies start in (default Auto). Goal Security until stability 5.

### Scorched earth

- Bombardment targets: Capitals only (default), or Capitals and small colonies (size 4 or less).

### What it costs

- Fee: fee level x 10% of Nexerelin's price.
- Fleet request points: 40% kept for emergencies (10% while rich). One launch a day.
- While rich: up to 3 launches, fleets 1.5x bigger, a standing defense fleet at every colony.

### The War page

- Empire panel, War page: War level, fleet request points, infamy, money sources, conquered colonies, per-faction stance buttons and operations.

- Stand down: War level to Defend, declarations and bombardment cleared, organizing offensives cancelled with full refund.
- Recall all: also cancels travelling (67% refund) and acting (33%) offensives, except invasions with troops landed.

### Agents

- Agents: Off, Defense (recommended), Defense and offense. Recruiting 12,000 plus the fee, 60 days.
- Offense: sabotage, isolation of Enemy and Destroy, rebellion at the next Destroy invasion target.

Next: [Research](research.md).
