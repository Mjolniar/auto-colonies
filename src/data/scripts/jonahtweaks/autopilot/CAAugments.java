package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.econ.LuddicMajority;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import niko_SA.MarketUtils;
import niko_SA.augments.core.BuiltInMode;
import niko_SA.augments.core.stationAttachment;
import niko_SA.augments.core.stationAugmentSpec;
import niko_SA.augments.core.stationAugmentStore;
import niko_SA.augments.threat.ThreatAugment;

import java.awt.Color;
import java.util.*;

/**
 * Station Augments: picks the set of known augments worth the most for each colony's station within its augment
 * points (AP), scored with the same profile weights as buildings. Installing costs nothing and can be undone, so Auto
 * colonies get the loadout applied and the rest see it with an Apply button. Augments you installed are never touched,
 * and one you remove isn't added back to that colony. Augments that use up an item, and ones that give AP back for a
 * drawback, are left to you. 0.3.17.1: Economy Conversion is player-only: Autopilot never adds it, since it cuts the
 * station's hull and flux for good. 0.3.17.4: it came off every station of yours once (dropConversionOnce); one you add
 * after that is never removed, its AP counts as taken, and the planner uses its slot.
 * Output augments (Hydroponics Basins: +2 food, +1 organics on the station) count only what they add above the colony's
 * own largest source of each, since a market's supply is its largest single source. Combat is valued from what each
 * augment really does on this station (CAAugmentModel, from Station Augments' source), read from the station's own ships
 * (CAStationKit): fighter augments by its share of fighters, the shield augments by its shields, AI Fighter Uplink and
 * Warmind Refit by its AI core; ones that work against an AI-run station (Macro Thrusters, Backline Deployment, Safety
 * Shunt, Support Outfit) are never picked. While a colony plans with Security (its threat is high, or that's its goal),
 * or a raid or blockade is on its way (CASiege), combat counts CAAugmentModel.THREAT times as much at the capital and
 * THREAT_AWAY times elsewhere (0.3.11.4: augments only decide fights you are in), so the combat loadout
 * takes the AP from output augments before the station is engaged; when the threat drops, the output ones come back.
 * Nothing changes while the station is in a battle. A change of plan profile re-plans at once.
 * 0.3.18.4: the combat values follow the station's design line (CAStationKit.tech): K-Type Shield Shunt and Bubble Shield on
 * an armored low-tech station, Axial Overclocking and Deep Shield Dissipators on a midline one, and Tactical Link by the share
 * of the station's damage its ballistic and energy guns do. Fragment augments (CAAugmentModel.FRAGMENT) are picked with an
 * item from Autopilot Storage: the Fragment Swarm only together with the augments that draw on it (pick). The item comes
 * from the nearest Autopilot Storage when the augment goes on and goes back to the colony's Autopilot Storage when it comes off.
 * Only called when Station Augments is enabled (check CAAugments.MOD first; the constant is inlined, so it doesn't load this class).
 */
public class CAAugments {
    public static final String MOD = "niko_stationAugments";
    static final String CONVERSION = "SA_economyConversion";
    static final float COMBAT = 0.004f;                        // fleet effect per point of the mod's own combat rating
    static final float THREAT_COMBAT = CAAugmentModel.THREAT;  // ...times this while the colony plans with Security (0.3.11.2: x4 as the model, was x10)
    static final float SWAP_MARGIN = 1.1f, SWAP_MIN = 300f;    // replacing installed augments must gain this much

    /** One augment's worth on one colony. */
    public static class Opt {
        public String id, name;
        public float ap, score;
        public List<String> lines = new ArrayList<String>();
        Set<String> incompatible = new HashSet<String>();
        /** 0.3.18.4: the augment this one needs on the station (the Fragment Swarm), or null. */
        String needs;
        /** 0.3.18.4: worth nothing alone, only with the augments that need it (the Fragment Swarm). */
        boolean enabler;
        /** 0.3.18.4: the item it uses from Autopilot Storage, or null. */
        String item;
    }

    /** A station's loadout now and the changes Autopilot recommends. */
    public static class Plan {
        public String station;   // 0.3.9.2 (J L13): note, kit, mine and yours are gone (read only by the colony section removed in 0.3.8.7)
        public float budget, used, gain;
        public List<Opt> add = new ArrayList<Opt>();
        public List<String> remove = new ArrayList<String>(), removeNames = new ArrayList<String>();

        public boolean changes() { return !add.isEmpty() || !remove.isEmpty(); }

        public String summary() {
            List<String> parts = new ArrayList<String>();
            if (!add.isEmpty()) { List<String> n = new ArrayList<String>(); for (Opt o : add) n.add(o.name); parts.add("add " + join(n)); }
            if (!remove.isEmpty()) parts.add("remove " + join(removeNames));
            return join(parts, "; ");
        }
    }

    static final Map<String, String> lastSig = new HashMap<String, String>();
    static final Map<String, Float> nextRun = new HashMap<String, Float>();

    static Map<String, stationAugmentSpec> specs() { return stationAugmentStore.allAugmentsExternalGetter(); }

    static Set<String> known() { return stationAugmentStore.getKnownAugmentsOfFac(Global.getSector().getPlayerFaction()); }

    /** 0.3.18.6: the ids of the augments on this colony's station (check CAAugments.MOD is enabled first). For CAStationFit. */
    public static Set<String> installedIds(MarketAPI m) {
        Set<String> out = new HashSet<String>();
        for (stationAttachment a : MarketUtils.getStationAugments(m)) out.add(a.getId());
        return out;
    }

    /** Whether you know this augment (check CAAugments.MOD is enabled first). */
    public static boolean knows(String id) {
        try { return known().contains(id); } catch (Throwable t) { return false; }
    }

    static String join(List<String> l) { return join(l, ", "); }

    static String join(List<String> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append(sep); sb.append(s); }
        return sb.toString();
    }

    static String ap(float v) { return v == Math.round(v) ? "" + Math.round(v) : String.valueOf(Math.round(v * 10f) / 10f); }

    /**
     * The profile to plan with: Security while the threat is high, as buildings do, for Approve and Auto colonies. A station
     * in a system with heavy military presence of its own (protected) stays on its colony's goal anyway (0.2.40.1): other
     * stations and military bases there take the fight, so this one can go economic (Mining Station, Hydroponics). Only the augments: buildings and fleets still go by the colony's threat. A raid or blockade on its way
     * still puts the combat loadout on (CASiege, in run).
     */
    static CAData.Profile profileFor(MarketAPI m, CAData.Colony c) {
        if (c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) return c.profile;
        CAData.Profile p = CAThreat.effective(m, c.profile);
        if (p == CAData.Profile.SECURITY && c.profile != CAData.Profile.SECURITY && protection(m) >= PROTECTED) return c.profile;
        return p;
    }

    static final float PROTECTED = CAThreat.PROTECTED;

    static float protection(MarketAPI m) { return CAThreat.protection(m, true); }

    // ---------------- scoring ----------------

    /** The mod's own combat rating for an augment, from its usage tags (combatgoodN minus combatbadN). */
    static int rating(stationAttachment a, String prefix) {
        int r = 0;
        for (String t : a.getSpec().getUsageTags()) {
            String s = t.trim();
            if (!s.startsWith(prefix)) continue;
            try { r += Integer.parseInt(s.substring(prefix.length())); } catch (NumberFormatException ignored) { }
        }
        return r;
    }

    /** A station you can see and manage (megastructure colonies such as the Coronal Hypershunt hide theirs). */
    static boolean hasStation(MarketAPI m) {
        Industry ind = Misc.getStationIndustry(m);
        return ind instanceof OrbitalStation && !ind.isHidden();
    }

    /** Station upkeep before any augment's upkeep multiplier. */
    static float baseUpkeep(Industry station) {
        MutableStat u = station.getUpkeep();
        float v = u.getModifiedValue();
        for (Map.Entry<String, MutableStat.StatMod> e : u.getMultMods().entrySet()) {
            if (e.getKey() != null && e.getKey().startsWith("SA_") && e.getValue().getValue() > 0f) v /= e.getValue().getValue();
        }
        return Math.max(0f, v);
    }

    /** The Mining Station augment's id. */
    static final String MINING = "SA_miningStation";

    /** What the Mining Station augment would mine on this station, from Station Augments' own code, or null. */
    @SuppressWarnings("unchecked")
    static Map<String, Integer> miningOutput(MarketAPI m, Industry station) {
        try {
            if (!(station instanceof OrbitalStation)) return null;
            com.fs.starfarer.api.campaign.SectorEntityToken entity = m.getPrimaryEntity() != null && m.getPrimaryEntity().hasTag("station")
                    ? m.getPrimaryEntity() : ((OrbitalStation) station).getStationEntity();
            com.fs.starfarer.api.campaign.CampaignFleetAPI fleet = Misc.getStationFleet(m);
            if (entity == null || fleet == null) return null;
            return (Map<String, Integer>) (Map<?, ?>) niko_SA.augments.miningStation.Companion.getOutput(entity, station, m, fleet);
        } catch (Throwable t) { return null; }
    }

    // 0.3.9.2 (J L16, L17): the most of a commodity any other building here makes is CAMods.otherSupply
    // 0.3.17.1: Slot (the value of Economy Conversion's extra industry slot) is gone with Autopilot's picks of it

    /** What a plan knows about the station once: its loadout, its AI core, whether patrols fight beside it, the Dustkeepers' mood. */
    static class Ctx {
        CAStationKit kit; String core; boolean patrols, dustkeepersHostile;
        static Ctx of(MarketAPI m, Industry station) {
            Ctx x = new Ctx();
            x.kit = CAStationKit.of(m);
            try {   // 0.3.11.6: the stations Axial Overclocking speeds the guns of, from Station Augments itself
                Set<String> axial = niko_SA.augments.axialOverclocking.Companion.getStationTypesToGetExtraWeaponTurnrate();
                if (axial != null && !axial.isEmpty() && !axial.equals(CAStationKit.AXIAL_GUNS)) { CAStationKit.AXIAL_GUNS.clear(); CAStationKit.AXIAL_GUNS.addAll(axial); }
            } catch (Throwable ignored) { }
            x.core = station.getAICoreId();
            x.patrols = m.hasIndustry("patrolhq") || m.hasIndustry("militarybase") || m.hasIndustry("highcommand");
            try {
                com.fs.starfarer.api.campaign.FactionAPI dk = Global.getSector().getFaction("sotf_dustkeepers");
                x.dustkeepersHostile = Global.getSector().getMemoryWithoutUpdate().contains("$sotf_dustkeeperHatred") || (dk != null && dk.getRelToPlayer().isHostile());
            } catch (Throwable ignored) { }
            return x;
        }
    }

    /** One augment's effects in the engine's units, weighted by the profile. Null when it's never worth having here. 0.3.9.2 (J L13): the overload without ctx is gone. */
    static Opt value(MarketAPI m, Industry station, stationAttachment a, CAData.Profile profile, Map<String, Integer> deficit, float upkeep, Ctx ctx) {
        String id = a.getId();
        if (PLAYER_ONLY.contains(id)) return null;   // 0.3.17.1
        CAEngine.Pick p = new CAEngine.Pick();
        float extra = 0f;
        int combat = rating(a, "combatgood") - rating(a, "combatbad");
        boolean threatened = profile == CAData.Profile.SECURITY;
        // 0.3.11.4: x4 at the capital, x2 elsewhere (CAAugmentModel.threatMult)
        boolean capital = false;
        try { capital = CACapital.is(m); } catch (Throwable ignored) { }
        float tm = CAAugmentModel.threatMult(threatened, capital);
        String counts = ". Combat counts x" + Math.round(tm) + " while the colony is threatened";
        // combat from what the augment really does on this station (CAAugmentModel); the mod's own rating only for augments it doesn't know
        CAAugmentModel.Gain g = CAAugmentModel.gain(id, ctx.kit, ctx.core, ctx.patrols, ctx.dustkeepersHostile);
        if (g != null) {
            if (g.share < 0f) return null;
            combat = 0;
            float fx = CAAugmentModel.fleetEffect(g, ctx.kit.tier, tm);
            p.fx[4] += fx;
            p.lines.add(g.line + (fx > 0f && threatened ? counts : ""));
        }
        if (id.equals("SA_commsCenter")) { p.fx[2] += 0.1f; p.lines.add("+10% accessibility"); }
        else if (id.equals("SA_logisticsDrones")) { p.fx[2] += 0.1f; p.lines.add("+10% accessibility. Up to +15% combat readiness for your fleets near the station"); }
        else if (id.equals("SA_jumpEngine")) { p.fx[2] += 0.2f; p.lines.add("+20% accessibility. Opens a jump point at the station"); }
        else if (id.equals("SA_efficiencyOverhaul")) { p.fx[0] += 0.3f * upkeep; p.lines.add("Station upkeep -30%: about " + Misc.getDGSCredits(0.3f * upkeep) + " per month"); }
        else if (id.equals("SA_hydroponics")) {
            // a colony's supply is its largest single source, so only output above what's already made here counts
            int food = 2 - CAMods.otherSupply(m, station.getId(), "food"), organics = 1 - CAMods.otherSupply(m, station.getId(), "organics");
            CAEngine.supply(p, "food", food, deficit);
            CAEngine.supply(p, "organics", organics, deficit);
            if (food <= 0 && organics <= 0) p.lines.add("The colony already makes more food and organics");
        }
        else if (id.equals(MINING)) {
            // Station Augments' own formula (miningStation.getOutput): the asteroid belts and rings within reach of the
            // station, each with the yield the mod rolls and keeps for it, times the station's mining strength. Like
            // Hydroponics, only what it makes above the colony's own largest source of each good counts (0.2.40.1)
            Map<String, Integer> out = miningOutput(m, station);
            int added = 0;
            if (out != null) for (Map.Entry<String, Integer> e : out.entrySet()) {
                int u = e.getValue() == null ? 0 : e.getValue();
                if (u == 1) u = 2;   // the mod turns 1 into 2 when it applies
                int more = u - CAMods.otherSupply(m, station.getId(), e.getKey());
                if (more > 0) { CAEngine.supply(p, e.getKey(), more, deficit); added += more; }
            }
            if (out == null) return null;   // Station Augments couldn't say (it needs Nexerelin for this augment)
            p.lines.add(added > 0 ? "Mines the asteroid belts and rings near the station" : "Nothing to mine within reach of the station, or no more than the colony already makes");
        }
        else if (id.equals("SA_ecmPackage")) { p.fx[3] += 0.05f; p.lines.add("Ground defense x1.05"); }
        else if (id.equals("SA_navRelay")) p.lines.add("+1 burn for friendly fleets near the station");
        else if (id.equals("SA_sanctifiedStation")) {
            if (!LuddicMajority.matchesBonusConditions(m)) return null;   // without an active Luddic majority it's only a drawback
            // 0.3.11.2: up to four Sacred Protector fleets (50-60 FP each), deleted when it's removed: valued like a Patrol HQ's
            // patrols and weighted as combat under threat, so the station keeps it then (it counted 0.1 and was swapped out)
            p.fx[0] += 0.4f * upkeep; p.fx[4] += 0.3f * tm;
            p.lines.add("Luddic majority: station upkeep -40% (about " + Misc.getDGSCredits(0.4f * upkeep) + " per month), defense flotillas");
        }
        if (combat != 0) {
            p.fx[4] += combat * COMBAT * (combat > 0 ? tm : 1f);
            p.lines.add(combat > 0 ? "Stronger station in combat (Station Augments rates it " + combat + ")"
                    + (threatened ? counts : "") : "Weaker station in combat");
        }
        float[] w = CAEngine.goalWeights(profile);
        float v = extra;
        for (int i = 0; i < 7; i++) v += w[i] * p.fx[i];
        Opt o = new Opt();
        o.id = id; o.name = a.getName(); o.ap = a.getAugmentCost(); o.score = v; o.lines = p.lines;
        // 0.3.18.4: fragment augments: the swarm enables the rest; each uses an item from Autopilot Storage
        o.enabler = CAAugmentModel.SWARM.equals(id);
        if (a instanceof ThreatAugment) o.needs = CAAugmentModel.SWARM;
        o.item = CAAugmentModel.FRAGMENT.contains(id) ? a.getRequiredItemId() : null;
        if (o.item != null) o.lines.add("Uses " + itemName(o.item) + " from Autopilot Storage");
        Set<String> inc = a.getIncompatibleAugments();
        if (inc != null) o.incompatible.addAll(inc);
        // K-Type Shield Shunt takes the shields away that Deep Shield Dissipators would help; Station Augments doesn't forbid both
        if (id.equals("SA_shieldShunt")) o.incompatible.add("SA_fluxShunt");
        if (id.equals("SA_fluxShunt")) o.incompatible.add("SA_shieldShunt");
        return o;
    }

    /**
     * 0.3.17.1: augments Autopilot never adds. Economy Conversion: station hull -30%, flux capacity -30%, dissipation -20%
     * for an extra industry slot, which weakens the station in the fights it is there for. Was OWN_LOGIC.
     */
    static final Set<String> PLAYER_ONLY = new HashSet<String>(Collections.singletonList(CONVERSION));

    /**
     * 0.3.17.3: 1 while this colony's station has Economy Conversion but its extra industry slot is off, else 0. Station
     * Augments re-applies every augment each economy tick, and Economy Conversion adds its slot (MAX_INDUSTRIES, source
     * SA_economyConversion) only while the station is functional, so a disrupted station drops it until repaired.
     * Check CAAugments.MOD is enabled first.
     */
    public static int pausedSlots(MarketAPI m) {
        try {
            if (m == null || !MarketUtils.hasStationAugment(m, CONVERSION)) return 0;
            com.fs.starfarer.api.combat.StatBonus b = m.getStats().getDynamic().getMod(com.fs.starfarer.api.impl.campaign.ids.Stats.MAX_INDUSTRIES);
            return b != null && b.getFlatBonuses().containsKey(CONVERSION) ? 0 : 1;
        } catch (Throwable t) { return 0; }
    }

    static float total(List<Opt> l) { float t = 0f; for (Opt o : l) t += o.score; return t; }

    static String itemName(String item) {
        try { String n = Global.getSettings().getSpecialItemSpec(item).getName(); return Misc.getAOrAnFor(n) + " " + n; }
        catch (Throwable t) { return item; }
    }

    /**
     * 0.3.18.4: the best set like best(), with dependencies: an augment that needs another (Opt.needs) counts only once that
     * one is in `have` (on the station and kept) or is picked with it. An enabler (the Fragment Swarm) is picked only together
     * with at least one augment that needs it, and only when the set with it beats the best set without it.
     */
    static List<Opt> pick(List<Opt> opts, float ap, Set<String> have) {
        List<Opt> base = new ArrayList<Opt>();
        List<Opt> enablers = new ArrayList<Opt>();
        for (Opt o : opts) {
            if (o.enabler) { if (!have.contains(o.id)) enablers.add(o); }
            else if (o.needs == null || have.contains(o.needs)) base.add(o);
        }
        List<Opt> out = best(base, ap);
        for (Opt e : enablers) {
            List<Opt> with = new ArrayList<Opt>(base);
            for (Opt o : opts) if (!o.enabler && e.id.equals(o.needs) && !have.contains(o.needs)) with.add(o);
            if (with.size() == base.size() || ap - e.ap < 0f) continue;
            List<Opt> b = best(with, ap - e.ap);
            boolean uses = false;
            for (Opt o : b) if (e.id.equals(o.needs)) uses = true;
            if (!uses) continue;
            b.add(0, e);
            if (total(b) > total(out)) out = b;
        }
        return out;
    }

    /**
     * 0.3.18.4: keeps at most as many augments per item as Autopilot Storage holds of it: enablers first, then by score.
     * `held`: how many of each item there are.
     */
    static List<Opt> limitItems(List<Opt> fresh, Map<String, Integer> held) {
        List<Opt> sorted = new ArrayList<Opt>(fresh);
        Collections.sort(sorted, new Comparator<Opt>() { public int compare(Opt a, Opt b) {
            if (a.enabler != b.enabler) return a.enabler ? -1 : 1;
            return Float.compare(b.score, a.score); } });
        Map<String, Integer> used = new HashMap<String, Integer>();
        List<Opt> out = new ArrayList<Opt>();
        for (Opt o : sorted) {
            if (o.item != null) {
                int u = used.containsKey(o.item) ? used.get(o.item) : 0;
                Integer h = held.get(o.item);
                if (h == null || u >= h) continue;
                used.put(o.item, u + 1);
            }
            out.add(o);
        }
        return out;
    }

    static float totalAp(List<Opt> l) { float t = 0f; for (Opt o : l) t += o.ap; return t; }

    static int units(float ap) { return (int) Math.ceil(ap * 2f - 1e-3f); }

    /** The highest-scoring set that fits in the AP (knapsack over half-AP steps). */
    static List<Opt> best(List<Opt> opts, float apLeft) {
        List<Opt> out = new ArrayList<Opt>();
        int cap = (int) Math.floor(apLeft * 2f + 1e-3f);
        if (cap <= 0 || opts.isEmpty()) return out;
        int n = opts.size();
        float[][] v = new float[n + 1][cap + 1];
        for (int i = 1; i <= n; i++) {
            Opt o = opts.get(i - 1);
            int w = units(o.ap);
            for (int k = 0; k <= cap; k++) {
                v[i][k] = v[i - 1][k];
                if (w <= k && v[i - 1][k - w] + o.score > v[i][k]) v[i][k] = v[i - 1][k - w] + o.score;
            }
        }
        for (int i = n, k = cap; i > 0; i--) {
            if (v[i][k] != v[i - 1][k]) { out.add(opts.get(i - 1)); k -= units(opts.get(i - 1).ap); }
        }
        return out;
    }

    /**
     * The set to have: A, the best set from scratch, or B, Autopilot's current augments plus the best fill of the free
     * AP. A wins only by a margin, so estimates that wobble don't swap augments back and forth. Of two incompatible new
     * augments (K-Type Shield Shunt and Deep Shield Dissipators) only the better one is considered, and none that clashes
     * with one Autopilot keeps.
     */
    static List<Opt> choose(List<Opt> current, List<Opt> fresh, float avail) { return choose(current, fresh, avail, new HashSet<String>()); }

    /** 0.3.18.4: as above; `have`: augments on the station that stay whatever Autopilot picks (yours), for Opt.needs. */
    static List<Opt> choose(List<Opt> current, List<Opt> fresh, float avail, Set<String> have) {
        fresh = new ArrayList<Opt>(fresh);
        for (Iterator<Opt> it = fresh.iterator(); it.hasNext(); ) {
            Opt f = it.next();
            for (Opt k : current) if (f.incompatible.contains(k.id) || k.incompatible.contains(f.id)) { it.remove(); break; }
        }
        Collections.sort(fresh, new Comparator<Opt>() { public int compare(Opt a, Opt b) { return Float.compare(b.score, a.score); } });
        for (int i = 0; i < fresh.size(); i++) {
            for (int j = fresh.size() - 1; j > i; j--) {
                Opt a = fresh.get(i), b = fresh.get(j);
                if (a.incompatible.contains(b.id) || b.incompatible.contains(a.id)) fresh.remove(j);
            }
        }
        List<Opt> all = new ArrayList<Opt>(current);
        all.addAll(fresh);
        List<Opt> a = pick(all, avail, have);   // 0.3.18.4: pick (dependencies) in place of best
        float keptAp = totalAp(current);
        if (keptAp > avail + 0.01f) return a;   // the budget shrank below what's installed
        List<Opt> b = new ArrayList<Opt>(current);
        Set<String> haveB = new HashSet<String>(have);
        for (Opt o : current) haveB.add(o.id);
        b.addAll(pick(fresh, avail - keptAp, haveB));
        return total(a) >= total(b) * SWAP_MARGIN + SWAP_MIN ? a : b;
    }

    // ---------------- planning ----------------

    /** The station's loadout and the best changes, without changing anything. */
    public static Plan plan(MarketAPI m, CAData.Colony c, CAData.Profile profile) {
        Plan pl = new Plan();
        Industry ind = Misc.getStationIndustry(m);
        if (!(ind instanceof OrbitalStation) || ind.isHidden()) return pl;   // no station, or one built in and hidden: left to you
        pl.station = ind.getCurrentName();
        pl.budget = MarketUtils.getAugmentBudget((OrbitalStation) ind);
        pl.used = MarketUtils.getUsedAugmentBudget(m);
        Set<String> managed = c.augAuto();
        Map<String, stationAttachment> installed = new LinkedHashMap<String, stationAttachment>();
        for (stationAttachment a : MarketUtils.getStationAugments(m)) installed.put(a.getId(), a);
        if (ind.isBuilding() && !ind.isUpgrading()) return pl;   // the station is still being built

        Map<String, Integer> deficit = CAEngine.empireDeficits();
        float upkeep = baseUpkeep(ind);
        Ctx ctx = Ctx.of(m, ind);
        float avail = pl.budget, currentScore = 0f;
        Set<String> have = new HashSet<String>();   // 0.3.18.4: augments that stay whatever Autopilot picks (yours, and ones it can't score now)
        List<Opt> current = new ArrayList<Opt>();   // Autopilot's augments still worth keeping
        Set<String> unknown = new HashSet<String>();   // 0.3.9.2 (J M4): Autopilot's augments that could not be scored now: kept as they are
        for (stationAttachment a : installed.values()) {
            boolean mine = managed.contains(a.getId());
            if (!mine || CONVERSION.equals(a.getId())) {
                if (a.getBuiltInMode() == BuiltInMode.NOT) avail -= a.getAugmentCost();
                have.add(a.getId());   // 0.3.18.4
                continue;
            }
            try {
                Opt o = value(m, ind, a, profile, deficit, upkeep, ctx);
                if (o == null && MINING.equals(a.getId())) unknown.add(a.getId());   // 0.3.9.2 (J M4): Station Augments could not say what it mines
                if (o != null) currentScore += o.score;
                if (o != null && (o.score > 0f || o.enabler)) current.add(o);   // 0.3.18.4: enabler
            } catch (Throwable t) {
                unknown.add(a.getId());   // 0.3.9.2 (J M4)
                CAStep.warn(STEP, "station augment " + a.getId() + " could not be scored at " + m.getName(), t);   // 0.3.9.2 (J L3)
            }
            if (unknown.contains(a.getId()) && a.getBuiltInMode() == BuiltInMode.NOT) avail -= a.getAugmentCost();   // 0.3.9.2 (J M4): its AP stays taken
            if (unknown.contains(a.getId())) have.add(a.getId());   // 0.3.18.4
        }
        List<Opt> fresh = new ArrayList<Opt>();
        // 0.3.18.4: an enabler Autopilot put on (the Fragment Swarm) is kept only beside an augment that needs it; otherwise
        // it is weighed again with the new ones, so it comes off unless they are picked
        for (Iterator<Opt> it = current.iterator(); it.hasNext(); ) {
            Opt e = it.next();
            if (!e.enabler) continue;
            boolean used = false;
            for (Opt o : current) if (e.id.equals(o.needs)) used = true;
            if (!used) { it.remove(); e.item = null; fresh.add(e); }   // on the station already: it needs no item from storage
        }
        Map<String, Integer> held = new HashMap<String, Integer>();   // 0.3.18.4: items in Autopilot Storage
        Set<String> skip = c.augSkip();
        for (String id : known()) {
            if (installed.containsKey(id) || skip.contains(id) || PLAYER_ONLY.contains(id)) continue;   // 0.3.17.1: PLAYER_ONLY
            stationAugmentSpec spec = specs().get(id);
            if (spec == null || spec.getApCost() <= 0f) continue;
            // 0.3.18.4: of the augments that use an item, only fragment augments, with the item in Autopilot Storage
            String item = spec.getRequiredItemId();
            if (item != null) {
                if (!CAAugmentModel.FRAGMENT.contains(id)) continue;
                if (!held.containsKey(item)) held.put(item, (int) CAStores.count(item));
                if (held.get(item) <= 0) continue;
            }
            try {
                stationAttachment inst = spec.getNewPluginInstance(m);
                inst.setConsiderAP(false);
                inst.setConsiderEngagement(false);
                if (item != null) inst.setConsiderReqItem(false);   // 0.3.18.4: Autopilot brings the item from Autopilot Storage
                String why = inst.getUnavailableReason();
                // 0.3.18.4: a fragment augment waits on the swarm, which may be picked with it (pick)
                if (why != null && inst instanceof ThreatAugment && !MarketUtils.hasFragmentSwarm(m)) why = null;
                if (why != null || inst.getAugmentCost() <= 0f) continue;
                Opt o = value(m, ind, inst, profile, deficit, upkeep, ctx);
                if (o != null && (o.score > 0f || o.enabler)) fresh.add(o);   // 0.3.18.4: enabler
            } catch (Throwable t) {
                CAStep.warn(STEP, "station augment " + id + " could not be scored at " + m.getName(), t);   // 0.3.9.2 (J L3)
            }
        }
        fresh = limitItems(fresh, held);   // 0.3.18.4
        List<Opt> pick = choose(current, fresh, avail, have);
        Set<String> keep = new HashSet<String>();
        for (Opt o : pick) keep.add(o.id);
        for (Opt o : pick) if (!installed.containsKey(o.id)) pl.add.add(o);
        // 0.3.18.4: an enabler goes on before the augments that need it
        Collections.sort(pl.add, new Comparator<Opt>() { public int compare(Opt x, Opt y) {
            if (x.enabler != y.enabler) return x.enabler ? -1 : 1;
            return Float.compare(y.score, x.score); } });
        for (String id : managed) {
            stationAttachment inst = installed.get(id);
            if (inst == null || CONVERSION.equals(id) || keep.contains(id) || unknown.contains(id) || !inst.canBeRemoved()) continue;   // 0.3.9.2 (J M4): unknown
            pl.remove.add(id);
            pl.removeNames.add(inst.getName());
        }
        // 0.3.18.4: the Fragment Swarm comes off after the augments that need it
        int sw = pl.remove.indexOf(CAAugmentModel.SWARM);
        if (sw >= 0) { pl.remove.add(pl.remove.remove(sw)); pl.removeNames.add(pl.removeNames.remove(sw)); }
        pl.gain = total(pick) - currentScore;
        return pl;
    }

    /** 0.3.17.4: CAMark key: the day Economy Conversion was taken off your stations (dropConversionOnce); set once per save. */
    static final String CONVERSION_DROPPED = "augconvdrop";

    /**
     * 0.3.17.4: once per save, Economy Conversion comes off every station of yours, at once and in every augment mode (you
     * asked for it off everywhere when it became player-only). Colonies built on its slot are then over the industry limit:
     * CAOverLimit takes the weakest industry down at Auto colonies, as with any other over-limit colony. One you add after this
     * is yours: Autopilot never removes it, counts its AP as taken, and plans with the slot. Nothing changes under a what-if.
     */
    static void dropConversionOnce(CAData d) {
        if (CAMark.at(CONVERSION_DROPPED) != null || CALog.whatIf()) return;
        List<String> names = new ArrayList<String>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            try {
                stationAttachment conv = null;
                for (stationAttachment a : MarketUtils.getStationAugments(m)) if (CONVERSION.equals(a.getId())) conv = a;
                if (conv == null || !conv.canBeRemoved()) continue;   // none, or built into the station
                d.colony(m.getId()).augAuto().remove(CONVERSION);   // before removing, so sync() doesn't read it as yours to hold
                MarketUtils.removeStationAugment(m, conv);
                lastSig.remove(m.getId());
                names.add(m.getName());
            } catch (Throwable t) {
                CAStep.warn(STEP, "removing Economy Conversion failed at " + m.getName(), t);
            }
        }
        CAMark.set(CONVERSION_DROPPED, CAMark.now());
        if (!names.isEmpty()) CALog.did("Station augments: Economy Conversion removed at " + join(names) + ".", true);
    }

    /** 0.3.9.3 (J M3): CAMark key prefix of the day you removed an augment ("augskip:<marketId>|<augment id>"), per colony. */
    static final String SKIP_AT = "augskip:";

    /**
     * 0.3.9.3 (J M3): an augment you removed is held as long as a rejection (CAApprovals.holds), then may be planned again.
     * A mark with no date (an older save) is dated today. Nothing changes under a what-if.
     */
    static void expireSkips(MarketAPI m, CAData.Colony c) {
        if (c.augSkip == null || c.augSkip.isEmpty() || CALog.whatIf()) return;
        long now = CAMark.now();
        for (String id : new ArrayList<String>(c.augSkip)) {
            String key = SKIP_AT + m.getId() + "|" + id;
            Long at = CAMark.at(key);
            if (at == null) { CAMark.set(key, now); continue; }
            if (CAApprovals.holds("augments", CAMark.days(at, now))) continue;
            c.augSkip.remove(id);
            CAMark.clear(key);
            CALog.quiet(m.getName() + ": the hold on " + id + " ended; Autopilot may add it to the station again");   // starsector.log only
        }
    }

    /** Forgets augments Autopilot added that are gone; one you removed yourself isn't added back to this colony for 180 days. */
    static void sync(MarketAPI m, CAData.Colony c) {
        expireSkips(m, c);   // 0.3.9.3 (J M3)
        Set<String> mine = c.augAuto();
        if (mine.isEmpty()) return;
        boolean station = hasStation(m);
        for (String id : new ArrayList<String>(mine)) {
            if (MarketUtils.hasStationAugment(m, id)) continue;
            mine.remove(id);
            stationAugmentSpec spec = specs().get(id);
            // 0.3.18.4: a fragment augment of Autopilot's that is gone: its item goes back to the colony's Autopilot Storage
            // (Autopilot put it on without Station Augments' item handling, so nothing else gives the item back)
            if (spec != null && spec.getRequiredItemId() != null && CAAugmentModel.FRAGMENT.contains(id)) returnItem(m, spec.getRequiredItemId(), spec.getName());
            if (!station || spec == null) continue;
            stationAttachment inst = spec.getNewPluginInstance(m);
            inst.setConsiderAP(false);
            inst.setConsiderEngagement(false);
            inst.setConsiderReqItem(false);   // 0.3.18.4
            if (inst.getUnavailableReason() != null) continue;   // the game took it off (station changed or disrupted), not you
            c.augSkip().add(id);
            CAMark.set(SKIP_AT + m.getId() + "|" + id, CAMark.now());   // 0.3.9.3 (J M3): the hold starts today
            CALog.quiet(m.getName() + ": you removed " + spec.getName() + " from the station; Autopilot won't add it there for 180 days");   // 0.3.0.9: left alone, starsector.log only
        }
    }

    // ---------------- refit crews (0.2.37.0) ----------------
    // Station Augments only changes a station's augments while you're docked there. From afar, Autopilot books a refit crew:
    // the change happens REFIT_DAYS later, or at once while your fleet is in the colony's system.

    static final float REFIT_DAYS = 7f;

    /** 0.3.9.2 (J L3): the pass step this runs under (CAAuto), for CAStep.warn's one stack trace per failure. */
    static final String STEP = "station augment step";

    /** 0.3.9.2 (J L1): the station is fighting; Station Augments adds nothing then. */
    static boolean inBattle(MarketAPI m) {
        com.fs.starfarer.api.campaign.CampaignFleetAPI sf = Misc.getStationFleet(m);
        return sf != null && sf.getBattle() != null;
    }

    static boolean fleetHere(MarketAPI m) {
        com.fs.starfarer.api.campaign.CampaignFleetAPI f = com.fs.starfarer.api.Global.getSector().getPlayerFleet();
        return f != null && m.getPrimaryEntity() != null && f.getContainingLocation() == m.getPrimaryEntity().getContainingLocation();
    }

    /** Whether the augments may change today; books a refit crew (and says so) when none is booked yet. */
    static boolean refitReady(MarketAPI m) {
        Map<String, Long> booked = CAData.get().augRefit();
        if (fleetHere(m)) { booked.remove(m.getId()); return true; }
        Long at = booked.get(m.getId());
        if (at == null) {
            booked.put(m.getId(), com.fs.starfarer.api.Global.getSector().getClock().getTimestamp());
            CALog.thinkStatus("augrefit:" + m.getId(), m.getName() + ": Station augments: refit crew booked. Augments change in " + Math.round(REFIT_DAYS) + " days.");
            return false;
        }
        if (com.fs.starfarer.api.Global.getSector().getClock().getElapsedDaysSince(at) < REFIT_DAYS) return false;
        booked.remove(m.getId());
        return true;
    }

    /** Removes, then adds, as planned. Returns null on success, or why nothing changed. */
    static String apply(MarketAPI m, CAData.Colony c, Plan pl, String who) {
        // Station Augments lets an augment come off mid-battle but not go on, so nothing changes while the station fights
        if (inBattle(m)) return "Station in battle. Its augments change after the battle";
        Map<String, stationAttachment> installed = new HashMap<String, stationAttachment>();
        for (stationAttachment a : MarketUtils.getStationAugments(m)) installed.put(a.getId(), a);
        List<String> done = new ArrayList<String>(), failed = new ArrayList<String>();
        for (String id : pl.remove) {
            stationAttachment a = installed.get(id);
            if (a == null || !a.canBeRemoved()) continue;
            // 0.3.18.4: a fragment augment's item goes back to this colony's Autopilot Storage, not your fleet's cargo
            String item = CAAugmentModel.FRAGMENT.contains(id) ? a.getRequiredItemId() : null;
            if (item != null) a.setConsiderReqItem(false);
            MarketUtils.removeStationAugment(m, a);
            c.augAuto().remove(id);
            if (item != null) returnItem(m, item, null);
            done.add("removed " + a.getName());
        }
        for (Opt o : pl.add) {
            stationAugmentSpec spec = specs().get(o.id);
            if (spec == null) continue;
            stationAttachment inst = spec.getNewPluginInstance(m);
            String item = CAAugmentModel.FRAGMENT.contains(o.id) ? spec.getRequiredItemId() : null;
            if (item != null) inst.setConsiderReqItem(false);   // 0.3.18.4: the item comes from Autopilot Storage (takeItem)
            String why = inst.getUnavailableReason();
            if (why != null) { failed.add(o.name + " (" + why.toLowerCase() + ")"); continue; }
            CAStores.Store from = null;
            if (item != null && (from = takeItem(m, item)) == null) { failed.add(o.name + " (none of its item in Autopilot Storage)"); continue; }
            if (MarketUtils.addStationAugment(m, inst, true) == null) {
                if (from != null) from.cargo.addSpecial(new SpecialItemData(item, null), 1f);
                failed.add(o.name); continue;
            }
            c.augAuto().add(o.id);
            done.add("added " + o.name);
        }
        lastSig.remove(m.getId());
        // 0.3.0.9: a failed add is not acting: starsector.log only
        if (!failed.isEmpty() && CALog.once("augfail:" + m.getId() + ":" + join(failed))) CALog.quiet(m.getName() + ": could not add station augments: " + join(failed));
        if (done.isEmpty()) return failed.isEmpty() ? "Nothing to change" : "Could not add " + join(failed);
        // short, and at most every 10 days a colony (they follow the threat back and forth): "Gargor augments (defense): +Bubble Shield, -Jump Engine"
        List<String> brief = new ArrayList<String>();
        for (String x : done) brief.add(x.replaceFirst("^added ", "+").replaceFirst("^removed ", "-"));
        CALog.status("aug:" + m.getId(), m.getName() + ": Station augments" + (who.isEmpty() ? "" : " (" + who + ")") + ": " + join(brief) + ".");
        return null;
    }

    /** 0.3.18.4: takes one of the item from the nearest Autopilot Storage; the store it came from, or null when none holds one. */
    static CAStores.Store takeItem(MarketAPI m, String item) {
        SpecialItemData d = new SpecialItemData(item, null);
        for (CAStores.Store s : CAStores.nearest(m)) {
            if (s.cargo.getQuantity(com.fs.starfarer.api.campaign.CargoAPI.CargoItemType.SPECIAL, d) < 1f) continue;
            s.cargo.removeItems(com.fs.starfarer.api.campaign.CargoAPI.CargoItemType.SPECIAL, d, 1f);
            return s;
        }
        return null;
    }

    /** 0.3.18.4: one of the item into this colony's Autopilot Storage; `name`: the augment, logged (starsector.log) when given. */
    static void returnItem(MarketAPI m, String item, String name) {
        CAStores.of(m).addSpecial(new SpecialItemData(item, null), 1f);
        if (name != null) CALog.quiet(m.getName() + ": " + name + " is off the station; its item is back in Autopilot Storage");
    }

    /** What decides whether a colony needs re-planning before its weekly turn: station, AP and what's installed or known. */
    static String signature(MarketAPI m, CAData.Profile profile) {
        Industry ind = Misc.getStationIndustry(m);
        StringBuilder sb = new StringBuilder(profile.name()).append('|').append(ind == null ? "-" : ind.getId()).append('|').append(MarketUtils.getRemainingAugmentBudget(m)).append('|').append(known().size());
        // 0.3.18.4: fragment items in Autopilot Storage: new ones re-plan at once
        sb.append('|').append((int) CAStores.count("fragment_fabricator")).append('|').append((int) CAStores.count("threat_processing_unit"));
        for (stationAttachment a : MarketUtils.getStationAugments(m)) sb.append('|').append(a.getId());
        return sb.toString();
    }

    // ---------------- daily pass ----------------

    /** Auto colonies get the best loadout applied; Approve colonies get the recommendation in the log once. */
    public static void run(CAData d, CASettings s, float now) {
        dropConversionOnce(d);   // 0.3.17.4
        finishRefits(d);   // 0.3.8.5: refits you approved or applied, in every augment mode
        if (s.augmentMode != 0) return;   // 1 recommend only, 2 off
        int tried = 0, failed = 0;   // 0.3.9.2 (J L3)
        Throwable last = null;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if (c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) continue;
            tried++;
            try {
                sync(m, c);
                if (!(hasStation(m))) continue;
                // a raid or blockade on the way: the defense loadout goes on now, while the station can still be refitted
                boolean siege = CASiege.held(m);
                CAData.Profile profile = siege ? CAData.Profile.SECURITY : profileFor(m, c);
                String sig = signature(m, profile);   // the threat state is in it, so a colony that turns to defense re-plans at once
                Float next = nextRun.get(m.getId());
                if (sig.equals(lastSig.get(m.getId())) && next != null && now < next) continue;
                lastSig.put(m.getId(), sig);
                nextRun.put(m.getId(), now + 7f);
                Plan pl = plan(m, c, profile);
                if (!pl.changes()) {
                    if (CAMark.at(REFIT_OK + m.getId()) == null) d.augRefit().remove(m.getId());   // 0.3.9.2 (J L2): a crew booked for a change no longer planned is let go
                    continue;
                }
                boolean defense = profile == CAData.Profile.SECURITY && c.profile != CAData.Profile.SECURITY;
                if (c.mode == CAData.Mode.AUTO) {
                    if (inBattle(m)) { lastSig.remove(m.getId()); continue; }   // 0.3.9.2 (J L1): the booked crew waits out the battle
                    if (!refitReady(m)) { lastSig.remove(m.getId()); continue; }   // the refit crew isn't there yet: look again tomorrow
                    String done = apply(m, c, pl, siege || defense ? "defense" : c.augDefense ? "calm" : "");
                    if (done == null) c.augDefense = defense;
                    else lastSig.remove(m.getId());   // try again tomorrow (the station was fighting)
                }
                else {
                    if (CAMark.at(REFIT_OK + m.getId()) != null) continue;   // 0.3.8.5: approved; its refit crew is booked (finishRefits)
                    // 0.3.9.2 (J M2): one ask key per colony; the loadout is in the title. A rejection holds 180 days
                    String key = "aug:" + m.getId();
                    CAApprovals.moveHold(key);
                    if (!CAApprovals.held(key, "augments") && CALog.once("aug:" + m.getId() + ":" + pl.summary()))
                        CAApprovals.say(m.getName() + ": Station augments suggested: " + pl.summary() + (defense ? ", for defense" : "") + ".", false);   // 0.3.9.2 (J L8): no panel to point at
                    // 0.3.3.0: a decision in the approval summary (installing augments is free and can be undone)
                    final MarketAPI mm = m;
                    CAApprovals.offer(key, "augments", m, "Station augments: " + pl.summary(), 0f, 0f, null, () -> {
                        if (!Global.getSettings().getModManager().isModEnabled(MOD)) return "Station Augments is off";
                        String r = applyNow(mm);
                        return BOOKED.equals(r) ? null : r;   // 0.3.8.5: a booked refit crew is the approval; finishRefits makes the change
                    });
                }
            } catch (Throwable t) {
                failed++; last = t;
                CAStep.warn(STEP, "station augment step failed at " + m.getName(), t);   // 0.3.9.2 (J L3)
            }
        }
        // 0.3.9.2 (J L3): every colony failed: the step fails, so CAStep's breaker can stop it
        if (tried > 0 && failed == tried) throw new IllegalStateException("station augments failed at every colony", last);
    }

    // ---------------- panels ----------------

    /** 0.3.8.5: a refit crew booked for a change you approved or applied: "augok:<marketId>" (CAMark, per colony). */
    static final String REFIT_OK = "augok:";

    /** 0.3.8.5: applyNow's answer when the refit crew is booked; finishRefits makes the change when it is there. */
    static final String BOOKED = "Refit crew booked. Augments change in " + Math.round(REFIT_DAYS) + " days, or at once with your fleet in this system";

    /** Apply button: plans afresh and applies. Returns null on success, or BOOKED when the refit crew is booked. */
    public static String applyNow(MarketAPI m) {
        CAData.Colony c = CAData.get().colony(m.getId());
        sync(m, c);
        Plan pl = plan(m, c, profileFor(m, c));
        if (!pl.changes()) { CAMark.clear(REFIT_OK + m.getId()); return "Nothing to change"; }   // 0.3.8.5
        if (!refitReady(m)) { CAMark.set(REFIT_OK + m.getId(), CAMark.now()); return BOOKED; }   // 0.3.8.5: finishRefits makes the change
        CAMark.clear(REFIT_OK + m.getId());   // 0.3.8.5
        return apply(m, c, pl, "you set");
    }

    /**
     * 0.3.8.5: makes the change you approved or applied (REFIT_OK) once its refit crew is there, at a colony not set to Off,
     * in every augment mode. The plan is made again then, as applyNow does.
     */
    static void finishRefits(CAData d) {
        for (String k : CAMark.keys(REFIT_OK)) {
            String id = k.substring(REFIT_OK.length());
            try {
                MarketAPI m = CAAuto.market(id);
                CAData.Colony c = d.colonies.get(id);
                if (m == null || !m.isPlayerOwned() || c == null || c.mode == CAData.Mode.OFF || !hasStation(m)) { CAMark.clear(k); continue; }
                if (inBattle(m)) continue;   // 0.3.9.2 (J L1): the crew and your approval wait out the battle
                if (!refitReady(m)) continue;
                CAMark.clear(k);
                sync(m, c);
                Plan pl = plan(m, c, profileFor(m, c));
                if (pl.changes()) apply(m, c, pl, "you set");
            } catch (Throwable t) {
                CAStep.warn(STEP, "station augment refit failed at " + id, t);   // 0.3.9.2 (J L3)
            }
        }
    }

    // 0.3.8.7: the colony panel's section (renderColony) and its Allow again (unskip) are gone: the panel has not listed it since 0.3.7.6

    /** 0.3.9.2 (J L9): the Mod features page's plans by market id: {signature and day, Plan}. Cleared on load (CAReset). */
    static final Map<String, Object[]> drawn = new HashMap<String, Object[]>();

    /** 0.3.9.2 (J L9): the plan the page draws, made again only when the station's signature or the day changes. */
    static Plan drawnPlan(MarketAPI m, CAData.Colony c) {
        CAData.Profile prof = profileFor(m, c);
        String key = signature(m, prof) + "|" + CATime.today();
        Object[] was = drawn.get(m.getId());
        if (was != null && key.equals(was[0])) return (Plan) was[1];
        Plan pl = plan(m, c, prof);
        drawn.put(m.getId(), new Object[]{key, pl});
        return pl;
    }

    /** The empire panel's section: one line per colony with a station. */
    public static void renderEmpire(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        CATheme.mode(t, CASettings.AUGMENT_NAMES[Math.max(0, Math.min(2, s.augmentMode))]);   // 0.3.0.0 (WS-F3 fix): the mode as a readout
        t.addPara("Picks the best augments within each station's AP.", 4f);
        int shown = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if (c.mode == CAData.Mode.OFF || !(hasStation(m))) continue;
            shown++;
            Plan pl;
            try { pl = drawnPlan(m, c); }   // 0.3.9.2 (J L9)
            catch (Throwable e) { CAStep.warn(STEP, "station augment plan failed at " + m.getName(), e); continue; }   // 0.3.9.2 (J L3)
            if (!pl.changes()) { t.addPara("%s, %s: %s of %s AP, best loadout.", 6f, hl, m.getName(), pl.station, ap(pl.used), ap(pl.budget)); continue; }
            t.addPara("%s, %s: %s of %s AP. Recommends: %s (%s).", 6f, hl, m.getName(), pl.station, ap(pl.used), ap(pl.budget), pl.summary(), CAEngine.valueText(pl.gain));
            t.addButton("Apply", new String[]{"aug", "apply", m.getId()}, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 4f);
        }
        if (shown == 0) t.addPara("No managed colony has a station.", gray, 6f);
    }
}
