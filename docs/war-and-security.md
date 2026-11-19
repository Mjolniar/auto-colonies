# War and security

## Quick version

- With Nexerelin, Auto Colonies runs emergency defense fleets, agents, a War level (default Retaliate) and a stance for each faction (default Auto) that caps it. [Low profile](low-profile.md) caps the War level at Defend.

## Threat

- Threat goes up with hostile neighbors, crises, raids and Pather cells. At 70%, the colony is planned as Security for at least 60 days, until threat's under 65%.
- A colony is under siege, and only builds defenses, while it's blockaded, hostile fleets are at the planet or stronger ones are in the system, or a raid, invasion or blockade is on the way.

## Colony crises

- Crisis progress is priced at about 640 credits a month per point of monthly progress, charged to AI cores (Hegemony, Pathers) and Fuel Production (Diktat) and credited to Patrol HQ, Military Base and High Command.
- When the next crisis is picked (meter at 500), its target is planned as Security, a Pather target sheds its AI cores and free ports the crisis counts are closed.
- For Nexerelin's police raid, if your best defended Auto colony can beat it, that colony keeps its free port and the others close, so the raid goes there. Beating the raid ends the police crisis for good.
- With Patrol kills count for crises (Advanced, War, on by default), kills by Auto colony patrols and stations in battles without your fleet count toward the colony crisis and Tri-Tachyon commerce raiding like yours.

## Enforcer patrols

- Enforcer patrols (Advanced, War) and each fleet type are off by default: Hegemony investigators, Hegemony AI inspections, Persean League enforcers, Luddic sacred protectors, Tri-Tachyon commerce raiders and Nexerelin police.
- In systems with Auto colonies (default) or any colony of yours, a target is made hostile to your faction while it stays, so colony patrols and stations engage it.
- The gates are your commissioner, allies and League membership, ceasefires and Low profile, and each one's on by default.
- Attack in your fleet's system is on by default, and a marked fleet is hostile to your fleet too. Patrols intercept (off by default) gives colony patrols an intercept order.
- Enforcer cost can be Reputation (default, -3, none for commerce raiders and Nexerelin police), Credits, Both or None. A fleet is charged once, when it loses to your faction's fleets.
- On Credits, the charge is fleet points x Nexerelin's fleet request price per point (default 400), or 400 without Nexerelin, and it only attacks fleets it can pay for above the credit reserve.
- Sindrian Diktat raiders are handled like pirate raids at all your colonies, with their own setting (on by default, even with Enforcer patrols off) and no gate, charge or reputation change.

## Nexerelin

### War level

Each level (Settings, War) adds to the one before it.

- Defend sends relief to besieged colonies and defense fleets at high threat, and strikes pirate and Pather bases within 15 ly.
- Retaliate raids markets that attacked you in the last 120 days and retakes your lost colonies (a lost capital first), which keep their old mode.
- Hold them down raids and blockades military and other markets within 15 ly (any distance while rich), 2 per faction.
- Conquer invades Enemy markets that cost at most a quarter of the surplus, and Destroy markets, capital last.
- Scorched saturation bombards a Destroy capital after Authorize bombardment.

### Faction stances

| Stance | War level cap | Relations | Peace offers |
|---|---|---|---|
| Ally, Friendly, Neutral | Defend | Raised, even while hostile | Accepted |
| Auto | Retaliate | Raised by ambassador focus, but not while hostile | Left to you |
| Rival | Hold them down | Not raised | Left to you |
| Enemy, Destroy | Conquer, Scorched | Not raised | Rejected at Retaliate+ |

- Enemy and Destroy only act on a hostile faction, and they act as Rival until you declare war.

### Conquest

- Invasion fleets go out at 1.25x Nexerelin's wanted fleet, one at a time (two while rich), and invasions stop when infamy x (1 + dominance) hits 75 unless Coalition risk allowed (default Off) is on.
- A conquered colony's autonomy is taken back the same day, its mode comes from Conquered colonies start in (default Auto) and its goal is Security until stability 5.
- Bombardment targets can be Capitals only (default) or Capitals and small colonies (size 4 or less).

### Costs

- The fee is fee level x 10% of Nexerelin's price. It keeps 40% of fleet request points for emergencies (10% while rich) and does one launch a day. While rich, it can do up to 3 launches, fleets are 1.5x bigger and every colony has a standing defense fleet.

### The War page

- Stand down sets the War level to Defend, clears declarations and bombardment and cancels offensives still organizing (full refund). Recall all also cancels ones travelling (67% refund) or acting (33%), except invasions with troops landed.

### Agents

- Agents can be Off, Defense (recommended) or Defense and offense, and recruiting one costs 12,000 plus the fee and takes 60 days.

Next: [Research](research.md).
