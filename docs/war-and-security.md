# War and security

How it keeps your colonies safe, and with Nexerelin, how it handles fleets. It never invades anyone or takes colonies, that's always your call.

## Threat

Every colony has a threat estimate built from hostile neighbors, colony crises, raids, Pather cells and so on. Once it hits 70% the colony gets planned as Security, so defenses and fleets jump up its list. It goes back to its own goal once the threat is under 65% and at least 60 days have passed, so it doesn't flip back and forth.

## Sieges

A colony is under siege when it's blockaded, has hostile fleets at the planet, has hostiles in the system that outweigh its defenses, or has a hostile raid, invasion or blockade on the way. While that lasts:

- only defenses get built there;
- its items, AI cores, administrator, policies, station augments and terraforming are left as they are;
- trade contracts don't count on its goods, since nothing can be shipped out.

The log says when a siege starts and ends, and the colony panel says why.

## Colony crises

The game's crisis meter is priced into everything, at about 640 credits a month per point of monthly progress. So:

- AI cores pay for the Hegemony progress they add (colonies of size 3 or less are free) and the Pather interest they bring.
- A Patrol HQ, Military Base or High Command counts how much it slows every crisis down.
- Fuel Production counts the Sindrian Diktat's interest.

Once the next crisis is picked (the meter at 500), the colony it's likely to hit gets planned as Security, a Pather target sheds AI cores until its cell can dissolve, and a coming Nexerelin police raid closes free ports that would count. The **Colony crises** page in the empire panel shows the meter, what's coming and where, what's driving each faction's crisis, and how to end it. Deals, fights and memberships are up to you.

## Station augments

With Station Augments, stations get the best set of augments they can fit. While a colony's threat is high, or a raid or blockade is on its way, combat counts four times as much, and the output augments come back once things calm down. Nothing changes during a battle.

## Nexerelin

### Emergency defense fleets

When a colony's system is targeted by a hostile raid, invasion or crisis fleet, or hostiles there outweigh its defenses, it requests a Defense fleet the same way Nexerelin's fleet request dialog does. This can go over the monthly budget since it's an emergency, but never under the credit reserve. It's the **Emergency defense fleets** setting under Colony policies.

### War fleets

The **War fleets** setting (also under Colony policies) is **Protect and strike back** by default, or Protect, or Off. Every day it looks at what's needed, in this order:

1. **Relief** - a relief fleet to a colony under siege (once every 60 days per colony).
2. **Proactive defense** - a Defense fleet to a colony whose threat is high or that a colony crisis is heading for, before anything shows up.
3. **Pirate eradication** - base strikes on the pirate and Pather bases you know about within 15 light-years of a colony, at twice the strength of the base's station.
4. **Strike back** - raids on the markets that sent raids, invasions or blockades at your colonies in the last 120 days, as long as they're still hostile. Protect mode skips this one.

It never sends invasions or colony expeditions, and only goes after factions that are already hostile to you. Sending a raid at anyone else would start a war, so it just doesn't.

Normally it sends one fleet a day and always keeps 40% of Nexerelin's fleet request points for emergencies. Relief and defense can go over the monthly budget like the emergency fleet. Strikes and raids come out of the budget or the surplus. Nothing ever goes under the credit reserve.

While spending freely (10,000,000+ over the surplus line) it shotguns them out: up to three a day, half again as big, keeping only 10% of the points back, hitting every known base at any distance, and keeping a standing Defense fleet at every colony, all paid from the surplus. In practice Nexerelin's fleet request points are the real limit, they refill slowly no matter how rich you are.

The **War fleets** section on the Mod features page shows your points, what's waiting, every fleet of yours that's out, and which markets are marked for a strike back.

### Agents

The **Agents** setting is Off before setup, **Defense** with the recommended settings, or **Defense and offense**.

- **Defense** handles rebellions, Pather cells and hidden pirate bases at the closest colony that needs it, then raises relations with factions running operations against you while not at war with you (once they're above Neutral, their AI drops those operations).
- **Offense** adds sabotage of military industries, starting with whoever is invading or raiding you. It only goes after factions already at war with you, never allies, a ceasefire or your commissioner, since a caught operation can start a war.
- **Hiring** - a level 1 agent for 12,000, only while there's work for one, up to **Agents to keep**. Nexerelin pays the salary.

Agents you hired yourself don't get orders until you click **Let Autopilot use it** on the Mod features page, and **Take it back** returns one. That page also lists the enemy operations running against your colonies, which the game doesn't show anywhere else.

### Derelict Empire start

That start gives every faction enough points for a blockade or raid on day 0. Only in that start, the **Attacks on your colonies** settings group shows up: it caps pirate and crisis fleets per system and makes Nexerelin's invasions, raids and blockades of your colonies come one at a time, with a gap of 30 to 180 days. The recommended settings set at most 4 fleets, 90 days apart. In any other game these don't show and the game's own rules stay.

Next: [Research](research.md).
