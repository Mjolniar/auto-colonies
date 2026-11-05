# Money, surplus and the endgame

How much it spends, where the extra goes once you're doing well, and what changes once money stops mattering.

## The budget

All of this is under Settings, **Budget and spending**.

- **Budget type.** **Automatic** is recommended: each month it can spend half of last month's net income plus 15% of your credits above the reserve. That way a young colony that's still losing money gets built up anyway, and spending slows down on its own as your savings shrink. You can also use a share of net income or a fixed amount.
- **Credit reserve.** Never spent, by anything. The recommended amount is a quarter of your credits at setup, somewhere between 50,000 and 500,000.
- **Per-project cap.** Anything more expensive gets proposed to you instead of built.
- **New upkeep cap per month.** How much new monthly upkeep it can take on in one month, so a bad guess or a drop in income gets caught before a lot of permanent cost piles up. **Automatic** is 10% of last month's net income, at least 20,000, so it grows with you.
- **Pause on loss.** Stops spending while net income is negative. Recommended off, since the Automatic budget already slows down.

A couple of things are emergencies and can go over the monthly budget (never the reserve): emergency defense fleets, and an administrator for a colony past your colony limit.

## Surplus investment: the focus world

Once your credits pass a line (**Surplus investment**, recommended over 3,000,000), whatever's over it goes into one **focus world** to make it the best colony it can be:

- it can spend past the monthly budget and the per-project cap;
- it can add new upkeep up to 10% of your net income a month;
- prices count at a quarter when it decides what's worth building, so expensive stuff that pays off gets built;
- its terraforming is paid from the surplus, and research counts its plan twice.

It picks the Auto colony with the most to build on (size, free slots, deposits, habitability, hazard, items waiting that would fit, income, a reachable special world) and sticks with it unless another one is clearly better. To pick it yourself, hit **Make this the focus world** on a colony's panel. **Let Autopilot choose** hands it back.

Credits you've already promised to custom production (the game's own, or Ashes of the Domain's production orders) are kept back so those always get paid.

Whatever the focus world doesn't use can also go into terraforming at every Auto colony and more Research Facilities. See [Terraforming](terraforming.md) and [Research](research.md).

## Spending freely

Past 10,000,000 over the line, one focus world can't spend it all, so every Approve and Auto colony starts spending from the pool:

- prices barely count (5%) and money counts a quarter, so anything that adds defense, stability, growth, accessibility or fleet strength goes up even if it costs more than it earns;
- every colony maxes its station, Heavy Batteries, Planetary Shield, Megaport and Patrol HQ;
- colonies get looked at daily instead of weekly, with up to three upgrades going at once;
- new upkeep can reach a sixtieth of the pool each month, so the pool covers about five years of it;
- hazard pay goes wherever it speeds growth up, terraforming runs up to three projects per colony at once, Research Facilities keep going up while there's research left;
- with Ashes of the Domain it builds the producers for special project goods, the Daedalus Array and the Bifrost gates;
- with Nexerelin, war fleets go out a lot more (see [War and security](war-and-security.md)).

It's all paid from the pool, not from income, so a weak month doesn't stop it. It ends once the surplus drops under 10,000,000. **Over 10,000,000 of surplus** can be set to Focus world only if you'd rather it didn't do this.

## The endgame economy

Once you're spending freely and also making at least 1,000,000 a month (you can set this to 500,000 or 2,000,000, or turn it off under **Endgame economy**), money stops being the goal. Every colony that spends the surplus starts building for the stuff that actually matters late game: production, research, military, security.

What changes:

- A credit counts 5% when it values a building, upgrade, AI core or item. Income basically doesn't matter anymore.
- Fleets, ship quality and defense count a lot more.
- Everything a building makes is valued as part of a **production chain**. A good none of your colonies make is worth the most, then covering your own colonies' shortages, then grabbing more of the sector's production for yourself (pushing toward a monopoly). A second colony making something only one colony makes gets a bonus, so production spreads across your worlds instead of sitting on one. War goods (ships, weapons, supplies, fuel, heavy machinery, metals) count 1.5x, and Ashes of the Domain's advanced goods count double.
- Money plans lose their priority. A Paradise world's Commerce or Light Industry gets valued like anything else. Plan priority only stays for things nothing else would get to: terraforming structures and special-world buildings, special project producers, maxed stations and defenses, and the Bifrost Control Center.
- Research Facilities keep going up while any research is left, and AI cores and items go where they do the most for fleets and defense.

**Replacing industries.** With every industry slot full, an Auto colony can take down its weakest industry and build something better in its place, if the new one is worth at least 10,000 a month more. It's slow about it on purpose: one a day across the whole empire, and one a month per colony. It never touches:

- military industries (Military Base, High Command, Heavy Industry, Orbital Works);
- plan, project, research or wonder buildings;
- anything improved with story points, or anything you locked;
- mod buildings it can't value for certain;
- the last producer of a good while your colonies would end up short;
- anything it built itself in the last year.

Whatever it took down isn't rebuilt there for a year either. The old building's AI core and item go to Autopilot Storage to be placed again. **Endgame replaces industries** turns this part off.

**How much income it can give up.** Net income can drop to a third of what it was when the endgame started, so 3,000,000 a month keeps at least 1,000,000. Each month, builds that earn less draw on that room, and once it's used up they wait (the log says so). The endgame ends if the surplus drops under 5,000,000 or net income goes negative, and comes back once you're spending freely again.

The Colonies page shows when it's on, how much income it keeps, the room left this month, which goods the sector makes that you don't, and how many buildings it's replaced this year.

## Autonomy (Nexerelin)

With Nexerelin, the **Autonomy** setting can be Off, **Recommend** (recommended) or **Auto**. An autonomous colony gives you a quarter of its profit or loss, doesn't count against your colony limit, and can't be built on.

It's only ever about money. It looks at colonies a year old or more that have lost money three months in a row, or the one that would save the most in a money emergency (net income below zero with under three months of it left above the reserve). Recommend tells you in the log, Auto does it (one every 30 days at most, and not while spending freely unless it's an emergency). It never uses autonomy to get around your colony limit, and never on a new colony, since that tanks stability right when it matters most. The colony limit gets an administrator instead.

The Mod features page always has the Autonomy section, with every money-losing colony and a **Grant autonomy** button, plus **Take back** for autonomous ones.

Next: [Items, AI cores and administrators](items-and-cores.md).
