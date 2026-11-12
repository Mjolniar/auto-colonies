# War and security

## Quick version

- Auto Colonies tracks threat and handles sieges and colony crises.
- With Nexerelin: emergency defense fleets, agents, a **War level** (default Retaliate) and a **stance** per faction (default Auto) that caps it and sets diplomacy. War fleets also act for Approve and Advisor colonies.
- Operations are Nexerelin fleet requests at Nexerelin's price. New offensives add a fee. Nothing goes under the credit reserve.
- Optional: [Low profile](low-profile.md) caps the War level at Defend.

## Threat

A colony's threat counts hostile neighbors, crises, raids and Pather cells. At 70% it is planned as Security. Under 65%, after at least 60 days, it returns to its own goal.

## Sieges

A colony is under siege when it is blockaded, has hostile fleets at the planet or stronger ones in the system, or has a raid, invasion or blockade on the way. During a siege only defenses are built. Items, AI cores, administrator, policies, augments and terraforming are not changed.

## Colony crises

Crisis progress is priced at about 640 credits a month per point of monthly progress. AI cores (Hegemony, Pathers) and Fuel Production (Diktat) are charged for the progress they add. Patrol HQ, Military Base and High Command get credit for slowing it.

When the next crisis is picked (meter at 500), its target is planned as Security, a Pather target sheds AI cores, and free ports that count are closed. The **Colony crises** page shows what is coming. Deals and fights are left to you.

## Nexerelin

### War level

Settings, Playstyle, **War**. Each level adds to the one before. Colonies set to Off get no relief or defense.

| Level | Adds |
|---|---|
| Defend | Relief for besieged colonies. Defense fleets at high threat or before a crisis. Strikes on pirate and Pather bases within 15 ly. |
| Retaliate | Raids on markets that attacked you in the last 120 days. Retake (below). |
| Hold them down | Raids on military markets and blockades of other markets, within 15 ly (any distance while rich), 2 per faction at a time. |
| Conquer | Invasions of Enemy markets costing at most a quarter of the surplus, and of every Destroy market, capital last. |
| Scorched | Saturation bombardment of a Destroy capital, after **Authorize bombardment** (War page) |

- **Emergency defense fleets** (Settings, Advanced, War): a Defense fleet when hostiles target a colony's system or outweigh its defenses, at any War level, over budget if needed.
- **Retake your lost colonies** (Settings, Advanced, War; off in saves from 0.2.45): invasions of your former colonies, lost capital first. A retaken colony keeps its old mode.

### Faction stances

Set on the **Factions** or War page.

| Stance | War level cap | Relations (agents, IndEvo ambassadors) | Ceasefire and peace offers |
|---|---|---|---|
| Ally, Friendly, Neutral | Defend | Raised, also while hostile: Ally first, Friendly up to the stop line, Neutral while below 0 | Accepted |
| Auto | Retaliate | By ambassador focus, not while hostile | Left to you |
| Rival | Hold them down | Not raised | Left to you |
| Enemy, Destroy | Conquer, Scorched | Not raised | Rejected at War level Retaliate+ |

- Enemy and Destroy act only on a faction already hostile to you. Until you declare war, they act as Rival.
- Also capped at **Defend**: allies, your commissioner, factions with a ceasefire, factions not hostile to you (unless Enemy or Destroy after you declared war).
- With a commission: capped at **Hold them down**, with no retakes or invasions (Nexerelin gives the capture to your commissioner).

### Diplomacy

- Offers left to you get a log line with Auto Colonies' answer. It does not join alliances or create ceasefires or treaties.
- **Declare war** (War page): Enemy and Destroy only, once in 30 days, not with a commission or in Low profile. If relations rise, the faction is made hostile again, at most once in 30 days.

### Conquest

- Invasion fleets are 1.25x Nexerelin's wanted fleet and marines. One invasion at a time, two while rich.
- Invasions wait for stable conquered colonies, colony-limit room or an idle administrator, room above the stability floor, and spare fleet request points.
- Invasions stop before infamy x (1 + dominance) reaches 75, where Nexerelin's coalition forms. **Coalition risk allowed** (default Off) lifts this.
- Conquered colonies: autonomy is taken back the same day (after hiring an administrator if needed). Mode: **Conquered colonies start in** (default Auto). Security goal until stability 5 with no rebellion. A relief fleet after Nexerelin's capture timeout. Auto colonies stay above 0 stability. Approve and Advisor colonies get a monthly alert at 0.

### Scorched earth

- **Bombardment targets** (Settings, Advanced, War): Capitals only (default), or Capitals and small colonies (Destroy markets of size 4 or less when bombing costs less than invading).
- Surplus only. One at a time, 120 days apart, under the coalition line. Same reputation penalties as bombing in person.
- A bombarded capital is not bombarded again for 365 days and is that faction's first invasion target.

### What it costs

- **Fee:** fee level x 10% of Nexerelin's price, on retakes, Hold them down raids and blockades, invasions and bombardments.
- **Fleet request points:** 40% kept for emergencies (10% while rich). One launch a day. While rich: up to 3, fleets 1.5x bigger, a standing defense fleet at every colony.

### The War page

Empire panel, **War**: War level, fleet request points, infamy, money sources, conquered colonies, and per faction: stance buttons, **Declare war**, operations and what each waits for.

- **Stand down:** War level to Defend, declarations and bombardment authorization cleared, offensives still organizing cancelled with a full refund.
- **Recall all:** also cancels offensives travelling (67% refund) or in action (33%), except an invasion with troops on the ground.
- Both ask first. Fleet request points and fees are not refunded.

### Agents

**Agents** (Settings, Security and diplomacy): Off, **Defense**, Defense and offense. Recruiting: 12,000 plus the fee, 60 days. Agents you hired wait for **Let Autopilot use it** (Mod features page).

- Security: rebellions, Pather cells, hidden pirate bases. Conquered colonies with rebels at half strength or more come first.
- Offense: sabotage (Auto factions from Retaliate; Rival, Enemy and Destroy from Hold them down), isolation of Enemy and Destroy, and a rebellion at the next Destroy invasion target.

Next: [Research](research.md).
