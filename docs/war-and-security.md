# War and security

## Quick version

- Auto Colonies: tracks threat and handles sieges and colony crises.
- With Nexerelin: emergency defense fleets, agents, a War level (default Retaliate) and a stance per faction (default Auto) that caps it.
- [Low profile](low-profile.md): caps the War level at Defend.

## Threat

- Threat: counts hostile neighbors, crises, raids and Pather cells.
- At 70%: the colony is planned as Security. Under 65% after 60 days: it returns to its own goal.
- Siege: blockade, hostile fleets at the planet or stronger ones in the system, or a raid, invasion or blockade on the way. Only defenses are built.

## Colony crises

- Crisis progress: priced at about 640 credits a month per point of monthly progress.
- Charged: AI cores (Hegemony, Pathers) and Fuel Production (Diktat). Credited: Patrol HQ, Military Base, High Command.
- Next crisis picked (meter at 500): its target is planned as Security, a Pather target sheds AI cores, counted free ports close.

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
