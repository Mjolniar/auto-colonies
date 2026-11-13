package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
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

import java.awt.Color;
import java.util.*;

/**
 * Station Augments: picks the set of known augments worth the most for each colony's station within its augment
 * points (AP), scored with the same profile weights as buildings. Installing costs nothing and can be undone, so Auto
 * colonies get the loadout applied and the rest see it with an Apply button. Augments you installed are never touched,
 * and one you remove isn't added back to that colony. Augments that use up an item, and ones that give AP back for a
 * drawback, are left to you. Economy Conversion is never removed once added: its extra industry slot may be in use.
 * Output augments (Hydroponics Basins: +2 food, +1 organics on the station) count only what they add above the colony's
 * own largest source of each, since a market's supply is its largest single source. Combat is valued from what each
 * augment really does on this station (CAAugmentModel, from Station Augments' source), read from the station's own ships
 * (CAStationKit): fighter augments by its share of fighters, the shield augments by its shields, AI Fighter Uplink and
 * Warmind Refit by its AI core; ones that work against an AI-run station (Macro Thrusters, Backline Deployment, Safety
 * Shunt, Support Outfit) are never picked. While a colony plans with Security (its threat is high, or that's its goal),
 * or a raid or blockade is on its way (CASiege), combat counts CAAugmentModel.THREAT times as much, so the combat loadout
 * takes the AP from output augments before the station is engaged; when the threat drops, the output ones come back.
 * Nothing changes while the station is in a battle. A change of plan profile re-plans at once.
 * Only called when Station Augments is enabled (check CAAugments.MOD first; the constant is inlined, so it doesn't load this class).
 */
public class CAAugments {
    public static final String MOD = "niko_stationAugments";
    static final String CONVERSION = "SA_economyConversion";
    static final float COMBAT = 0.004f;                        // fleet effect per point of the mod's own combat rating
    static final float THREAT_COMBAT = 10f;                    // ...times this while the colony plans with Security
    static final float SWAP_MARGIN = 1.1f, SWAP_MIN = 300f;    // replacing installed augments must gain this much

    /** One augment's worth on one colony. */
    public static class Opt {
        public String id, name;
        public float ap, score;
        public List<String> lines = new ArrayList<String>();
        Set<String> incompatible = new HashSet<String>();
    }

    /** A station's loadout now and the changes Autopilot recommends. */
    public static class Plan {
        public String station, note;
        public CAStationKit kit;
        public float budget, used, gain;
        public List<String> mine = new ArrayList<String>(), yours = new ArrayList<String>();
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
     * stations and military bases there take the fight, so this one can go economic (Mining Station, Hydroponics, Economy
     * Conversion). Only the augments: buildings and fleets still go by the colony's threat. A raid or blockade on its way
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

    /** The most of a commodity any other building here makes. */
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

    static int otherSupply(MarketAPI m, Industry station, String com) {
        int best = 0;
        for (Industry ind : m.getIndustries()) {
            if (ind == station) continue;
            best = Math.max(best, ind.getSupply(com).getQuantity().getModifiedInt());
        }
        return best;
    }

    /** Value of one more industry slot: the best supported industry that's waiting for one. Computed once per plan. */
    static class Slot {
        boolean done; float value; String name;

        void compute(MarketAPI m, CAData.Profile profile) {
            if (done) return;
            done = true;
            if (CABuild.freeSlots(m) > 0) return;   // a slot is already free
            CAEngine.Result r = CAEngine.cached(m, profile, CAData.settings().minStability, false);   // 0.3.0.0 (A4): the day's kept evaluation
            if (r.bestNoSlot == null || r.bestNoSlot.score <= 0f) return;
            value = r.bestNoSlot.score; name = r.bestNoSlot.name;
        }
    }

    /** What a plan knows about the station once: its loadout, its AI core, whether patrols fight beside it, the Dustkeepers' mood. */
    static class Ctx {
        CAStationKit kit; String core; boolean patrols, dustkeepersHostile;
        static Ctx of(MarketAPI m, Industry station) {
            Ctx x = new Ctx();
            x.kit = CAStationKit.of(m);
            x.core = station.getAICoreId();
            x.patrols = m.hasIndustry("patrolhq") || m.hasIndustry("militarybase") || m.hasIndustry("highcommand");
            try {
                com.fs.starfarer.api.campaign.FactionAPI dk = Global.getSector().getFaction("sotf_dustkeepers");
                x.dustkeepersHostile = Global.getSector().getMemoryWithoutUpdate().contains("$sotf_dustkeeperHatred") || (dk != null && dk.getRelToPlayer().isHostile());
            } catch (Throwable ignored) { }
            return x;
        }
    }

    /** One augment's effects in the engine's units, weighted by the profile. Null when it's never worth having here. */
    static Opt value(MarketAPI m, Industry station, stationAttachment a, CAData.Profile profile, Map<String, Integer> deficit, float upkeep, Slot slot) {
        return value(m, station, a, profile, deficit, upkeep, slot, Ctx.of(m, station));
    }

    static Opt value(MarketAPI m, Industry station, stationAttachment a, CAData.Profile profile, Map<String, Integer> deficit, float upkeep, Slot slot, Ctx ctx) {
        String id = a.getId();
        CAEngine.Pick p = new CAEngine.Pick();
        float extra = 0f;
        int combat = rating(a, "combatgood") - rating(a, "combatbad");
        boolean threatened = profile == CAData.Profile.SECURITY;
        // combat from what the augment really does on this station (CAAugmentModel); the mod's own rating only for augments it doesn't know
        CAAugmentModel.Gain g = OWN_LOGIC.contains(id) ? null : CAAugmentModel.gain(id, ctx.kit, ctx.core, ctx.patrols, ctx.dustkeepersHostile);
        if (g != null) {
            if (g.share < 0f) return null;
            combat = 0;
            float fx = CAAugmentModel.fleetEffect(g, ctx.kit.tier, threatened);
            p.fx[4] += fx;
            p.lines.add(g.line + (fx > 0f && threatened ? "; combat counts four times while the colony is threatened" : ""));
        }
        if (id.equals("SA_commsCenter")) { p.fx[2] += 0.1f; p.lines.add("+10% accessibility"); }
        else if (id.equals("SA_logisticsDrones")) { p.fx[2] += 0.1f; p.lines.add("+10% accessibility; up to +15% combat readiness for your fleets near the station"); }
        else if (id.equals("SA_jumpEngine")) { p.fx[2] += 0.2f; p.lines.add("+20% accessibility; opens a jump point at the station"); }
        else if (id.equals("SA_efficiencyOverhaul")) { p.fx[0] += 0.3f * upkeep; p.lines.add("Station upkeep -30%: about " + Misc.getDGSCredits(0.3f * upkeep) + " per month"); }
        else if (id.equals("SA_hydroponics")) {
            // a colony's supply is its largest single source, so only output above what's already made here counts
            int food = 2 - otherSupply(m, station, "food"), organics = 1 - otherSupply(m, station, "organics");
            CAEngine.supply(p, "food", food, deficit);
            CAEngine.supply(p, "organics", organics, deficit);
            if (food <= 0 && organics <= 0) p.lines.add("Food and organics are already made here in larger amounts");
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
                int more = u - otherSupply(m, station, e.getKey());
                if (more > 0) { CAEngine.supply(p, e.getKey(), more, deficit); added += more; }
            }
            if (out == null) return null;   // Station Augments couldn't say (it needs Nexerelin for this augment)
            p.lines.add(added > 0 ? "Mines the asteroid belts and rings near the station" : "Nothing to mine within reach of the station, or no more than the colony already makes");
        }
        else if (id.equals("SA_ecmPackage")) { p.fx[3] += 0.05f; p.lines.add("Ground defense x1.05"); }
        else if (id.equals("SA_navRelay")) p.lines.add("+1 burn for friendly fleets near the station");
        else if (id.equals("SA_sanctifiedStation")) {
            if (!LuddicMajority.matchesBonusConditions(m)) return null;   // without an active Luddic majority it's only a drawback
            p.fx[0] += 0.4f * upkeep; p.fx[4] += 0.1f;
            p.lines.add("Luddic majority: station upkeep -40% (about " + Misc.getDGSCredits(0.4f * upkeep) + " per month), defense flotillas");
        }
        else if (id.equals(CONVERSION)) {
            if (profile == CAData.Profile.SECURITY) return null;   // it weakens the station for good; not while defense comes first
            slot.compute(m, profile);
            if (slot.value <= 0f) return null;
            extra += slot.value;
            p.lines.add("One more industry slot: " + slot.name + " is waiting for one (" + CAEngine.valueText(slot.value) + ")");
            combat = 0;
            p.fx[4] -= 0.1f;
            p.lines.add("Station hull -30%, flux capacity -30%, dissipation -20%");
        }
        if (combat != 0) {
            p.fx[4] += combat * COMBAT * (threatened && combat > 0 ? THREAT_COMBAT : 1f);
            p.lines.add(combat > 0 ? "Stronger station in combat (Station Augments rates it " + combat + ")"
                    + (threatened ? "; counts ten times while the colony is threatened" : "") : "Weaker station in combat");
        }
        float[] w = CAEngine.WEIGHTS[profile.ordinal()];
        float v = extra;
        for (int i = 0; i < 7; i++) v += w[i] * p.fx[i];
        Opt o = new Opt();
        o.id = id; o.name = a.getName(); o.ap = a.getAugmentCost(); o.score = v; o.lines = p.lines;
        Set<String> inc = a.getIncompatibleAugments();
        if (inc != null) o.incompatible.addAll(inc);
        // K-Type Shield Shunt takes the shields away that Deep Shield Dissipators would help; Station Augments doesn't forbid both
        if (id.equals("SA_shieldShunt")) o.incompatible.add("SA_fluxShunt");
        if (id.equals("SA_fluxShunt")) o.incompatible.add("SA_shieldShunt");
        return o;
    }

    /** Augments with their own logic below, which the combat model leaves alone. */
    static final Set<String> OWN_LOGIC = new HashSet<String>(Collections.singletonList(CONVERSION));

    static float total(List<Opt> l) { float t = 0f; for (Opt o : l) t += o.score; return t; }

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
    static List<Opt> choose(List<Opt> current, List<Opt> fresh, float avail) {
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
        List<Opt> a = best(all, avail);
        float keptAp = totalAp(current);
        if (keptAp > avail + 0.01f) return a;   // the budget shrank below what's installed
        List<Opt> b = new ArrayList<Opt>(current);
        b.addAll(best(fresh, avail - keptAp));
        return total(a) >= total(b) * SWAP_MARGIN + SWAP_MIN ? a : b;
    }

    // ---------------- planning ----------------

    /** The station's loadout and the best changes, without changing anything. */
    public static Plan plan(MarketAPI m, CAData.Colony c, CAData.Profile profile) {
        Plan pl = new Plan();
        Industry ind = Misc.getStationIndustry(m);
        if (!(ind instanceof OrbitalStation)) { pl.note = "No orbital station."; return pl; }
        if (ind.isHidden()) { pl.note = "This colony's station is built in and hidden, so its augments are left to you."; return pl; }
        pl.station = ind.getCurrentName();
        pl.budget = MarketUtils.getAugmentBudget((OrbitalStation) ind);
        pl.used = MarketUtils.getUsedAugmentBudget(m);
        Set<String> managed = c.augAuto();
        Map<String, stationAttachment> installed = new LinkedHashMap<String, stationAttachment>();
        for (stationAttachment a : MarketUtils.getStationAugments(m)) installed.put(a.getId(), a);
        for (stationAttachment a : installed.values()) (managed.contains(a.getId()) ? pl.mine : pl.yours).add(a.getName());
        if (ind.isBuilding() && !ind.isUpgrading()) { pl.note = "The station is still being built."; return pl; }

        Map<String, Integer> deficit = CAEngine.empireDeficits();
        float upkeep = baseUpkeep(ind);
        Slot slot = new Slot();
        Ctx ctx = Ctx.of(m, ind);
        pl.kit = ctx.kit;
        float avail = pl.budget, currentScore = 0f;
        List<Opt> current = new ArrayList<Opt>();   // Autopilot's augments still worth keeping
        for (stationAttachment a : installed.values()) {
            boolean mine = managed.contains(a.getId());
            if (!mine || CONVERSION.equals(a.getId())) {
                if (a.getBuiltInMode() == BuiltInMode.NOT) avail -= a.getAugmentCost();
                continue;
            }
            try {
                Opt o = value(m, ind, a, profile, deficit, upkeep, slot, ctx);
                if (o != null) currentScore += o.score;
                if (o != null && o.score > 0f) current.add(o);
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment " + a.getId() + " could not be scored at " + m.getName(), t);
            }
        }
        List<Opt> fresh = new ArrayList<Opt>();
        Set<String> skip = c.augSkip();
        for (String id : known()) {
            if (installed.containsKey(id) || skip.contains(id)) continue;
            stationAugmentSpec spec = specs().get(id);
            if (spec == null || spec.getRequiredItemId() != null || spec.getApCost() <= 0f) continue;
            try {
                stationAttachment inst = spec.getNewPluginInstance(m);
                inst.setConsiderAP(false);
                inst.setConsiderEngagement(false);
                if (inst.getUnavailableReason() != null || inst.getAugmentCost() <= 0f) continue;
                Opt o = value(m, ind, inst, profile, deficit, upkeep, slot, ctx);
                if (o != null && o.score > 0f) fresh.add(o);
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment " + id + " could not be scored at " + m.getName(), t);
            }
        }
        List<Opt> pick = choose(current, fresh, avail);
        Set<String> keep = new HashSet<String>();
        for (Opt o : pick) keep.add(o.id);
        for (Opt o : pick) if (!installed.containsKey(o.id)) pl.add.add(o);
        Collections.sort(pl.add, new Comparator<Opt>() { public int compare(Opt x, Opt y) { return Float.compare(y.score, x.score); } });
        for (String id : managed) {
            stationAttachment inst = installed.get(id);
            if (inst == null || CONVERSION.equals(id) || keep.contains(id) || !inst.canBeRemoved()) continue;
            pl.remove.add(id);
            pl.removeNames.add(inst.getName());
        }
        pl.gain = total(pick) - currentScore;
        return pl;
    }

    /** Forgets augments Autopilot added that are gone; one you removed yourself isn't added back to this colony. */
    static void sync(MarketAPI m, CAData.Colony c) {
        Set<String> mine = c.augAuto();
        if (mine.isEmpty()) return;
        boolean station = hasStation(m);
        for (String id : new ArrayList<String>(mine)) {
            if (MarketUtils.hasStationAugment(m, id)) continue;
            mine.remove(id);
            stationAugmentSpec spec = specs().get(id);
            if (!station || spec == null) continue;
            stationAttachment inst = spec.getNewPluginInstance(m);
            inst.setConsiderAP(false);
            inst.setConsiderEngagement(false);
            if (inst.getUnavailableReason() != null) continue;   // the game took it off (station changed or disrupted), not you
            c.augSkip().add(id);
            CALog.quiet(m.getName() + ": you removed " + spec.getName() + " from the station; Autopilot won't add it there again");   // 0.3.0.9: left alone, starsector.log only
        }
    }

    // ---------------- refit crews (0.2.37.0) ----------------
    // Station Augments only changes a station's augments while you're docked there. From afar, Autopilot books a refit crew:
    // the change happens REFIT_DAYS later, or at once while your fleet is in the colony's system.

    static final float REFIT_DAYS = 7f;

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
        com.fs.starfarer.api.campaign.CampaignFleetAPI sf = Misc.getStationFleet(m);
        if (sf != null && sf.getBattle() != null) return "The station is in a battle; its augments change once it's over";
        Map<String, stationAttachment> installed = new HashMap<String, stationAttachment>();
        for (stationAttachment a : MarketUtils.getStationAugments(m)) installed.put(a.getId(), a);
        List<String> done = new ArrayList<String>(), failed = new ArrayList<String>();
        for (String id : pl.remove) {
            stationAttachment a = installed.get(id);
            if (a == null || !a.canBeRemoved()) continue;
            MarketUtils.removeStationAugment(m, a);
            c.augAuto().remove(id);
            done.add("removed " + a.getName());
        }
        for (Opt o : pl.add) {
            stationAugmentSpec spec = specs().get(o.id);
            if (spec == null) continue;
            stationAttachment inst = spec.getNewPluginInstance(m);
            String why = inst.getUnavailableReason();
            if (why != null) { failed.add(o.name + " (" + why.toLowerCase() + ")"); continue; }
            if (MarketUtils.addStationAugment(m, inst, true) == null) { failed.add(o.name); continue; }
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

    /** What decides whether a colony needs re-planning before its weekly turn: station, AP and what's installed or known. */
    static String signature(MarketAPI m, CAData.Profile profile) {
        Industry ind = Misc.getStationIndustry(m);
        StringBuilder sb = new StringBuilder(profile.name()).append('|').append(ind == null ? "-" : ind.getId()).append('|').append(MarketUtils.getRemainingAugmentBudget(m)).append('|').append(known().size());
        for (stationAttachment a : MarketUtils.getStationAugments(m)) sb.append('|').append(a.getId());
        return sb.toString();
    }

    // ---------------- daily pass ----------------

    /** Auto colonies get the best loadout applied; Approve colonies get the recommendation in the log once. */
    public static void run(CAData d, CASettings s, float now) {
        if (s.augmentMode != 0) return;   // 1 recommend only, 2 off
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if (c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) continue;
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
                if (!pl.changes()) continue;
                boolean defense = profile == CAData.Profile.SECURITY && c.profile != CAData.Profile.SECURITY;
                if (c.mode == CAData.Mode.AUTO) {
                    if (!refitReady(m)) { lastSig.remove(m.getId()); continue; }   // the refit crew isn't there yet: look again tomorrow
                    String done = apply(m, c, pl, siege || defense ? "defense" : c.augDefense ? "calm" : "");
                    if (done == null) c.augDefense = defense;
                    else lastSig.remove(m.getId());   // try again tomorrow (the station was fighting)
                }
                else if (CALog.once("aug:" + m.getId() + ":" + pl.summary())) {
                    CALog.advise(m.getName() + ": Station augments suggested: " + pl.summary() + (defense ? ", for defense" : "") + ". Apply on its panel.");
                }
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment step failed at " + m.getName(), t);
            }
        }
    }

    // ---------------- panels ----------------

    /** Apply button: plans afresh and applies. Returns null on success. */
    public static String applyNow(MarketAPI m) {
        CAData.Colony c = CAData.get().colony(m.getId());
        sync(m, c);
        Plan pl = plan(m, c, profileFor(m, c));
        if (!pl.changes()) return "Nothing to change";
        if (!refitReady(m)) return "Refit crew booked. Augments change in " + Math.round(REFIT_DAYS) + " days, or at once with your fleet in this system";
        return apply(m, c, pl, "you set");
    }

    /** Lets Autopilot add the augments you removed from this colony again. */
    public static void unskip(MarketAPI m) {
        CAData.get().colony(m.getId()).augSkip().clear();
        lastSig.remove(m.getId());
    }

    /** The colony panel's section. */
    public static void renderColony(TooltipMakerAPI t, MarketAPI m, CAData.Colony c, CASettings s, CAData.Profile profile) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        sync(m, c);
        Plan pl = plan(m, c, profile);
        if (pl.station == null) { t.addPara(CALog.esc(pl.note), gray, 6f); return; }
        t.addPara("%s: %s of %s AP used.", 6f, hl, pl.station, ap(pl.used), ap(pl.budget));
        if (pl.kit != null) t.addPara("What it fights with: " + CALog.esc(pl.kit.describe()) + ".", gray, 4f);
        if (!pl.mine.isEmpty()) t.addPara("Added by Autopilot: %s", 4f, hl, join(pl.mine));
        if (!pl.yours.isEmpty()) t.addPara("Yours (left alone): %s", 4f, hl, join(pl.yours));
        if (pl.note != null) t.addPara(CALog.esc(pl.note), gray, 4f);
        if (!pl.changes()) t.addPara("Nothing to change.", gray, 6f);
        else {
            for (Opt o : pl.add) {
                t.addPara("Add %s (%s AP): %s", 6f, hl, o.name, ap(o.ap), CAEngine.valueText(o.score));
                for (int j = 0; j < Math.min(3, o.lines.size()); j++) t.addPara("    " + CALog.esc(o.lines.get(j)), gray, 2f);
            }
            for (String n : pl.removeNames) t.addPara("Remove %s to make room", 6f, hl, n);
            t.addPara("Value of the change: about %s", 6f, hl, CAEngine.valueText(pl.gain));
            t.addButton("Apply augment changes", CAColonyDialog.AUG_APPLY, base, dark, Alignment.MID, CutStyle.TL_BR, 240f, 24f, 6f);
            String when = s.augmentMode != 0 ? "Recommend only: changes on Apply."
                    : c.mode == CAData.Mode.AUTO ? "Auto applies this within a week." : "Waits for Apply here.";
            t.addPara(when, gray, 4f);
        }
        if (profile == CAData.Profile.SECURITY) t.addPara(c.profile == CAData.Profile.SECURITY ? "Security goal: combat augments first." :
                "High threat: combat augments first.", gray, 6f);
        else if (CAThreat.effective(m, c.profile) == CAData.Profile.SECURITY && c.profile != CAData.Profile.SECURITY)
            t.addPara("High threat, defended here: output augments first.", gray, 6f);
        else t.addPara("Calm: output augments first; combat loadout before a raid.", gray, 6f);   // 0.3.0.0 (WS-F3 fix pass 2): shorter
        if (!c.augSkip().isEmpty()) {
            List<String> names = new ArrayList<String>();
            for (String id : c.augSkip()) { stationAugmentSpec sp = specs().get(id); names.add(sp == null ? id : sp.getName()); }
            t.addPara("You removed these (not added again): %s", 8f, hl, join(names));
            t.addButton("Allow again", CAColonyDialog.AUG_UNSKIP, base, dark, Alignment.MID, CutStyle.TL_BR, 140f, 22f, 4f);
        }
        t.addPara("You know %s station augments.",
                6f, gray, hl, "" + known().size());
        t.addPara("Not added (bad for an AI-run station): " + CALog.esc(join(new ArrayList<String>(CAAugmentModel.NEVER.values()), "; ")) + ".", gray, 4f);
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
            try { pl = plan(m, c, profileFor(m, c)); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment plan failed at " + m.getName(), e); continue; }
            if (!pl.changes()) { t.addPara("%s, %s: %s of %s AP, best loadout.", 6f, hl, m.getName(), pl.station, ap(pl.used), ap(pl.budget)); continue; }
            t.addPara("%s, %s: %s of %s AP. Recommends: %s (%s).", 6f, hl, m.getName(), pl.station, ap(pl.used), ap(pl.budget), pl.summary(), CAEngine.valueText(pl.gain));
            t.addButton("Apply", new String[]{"aug", "apply", m.getId()}, base, dark, Alignment.MID, CutStyle.TL_BR, 120f, 22f, 4f);
        }
        if (shown == 0) t.addPara("No managed colony has a station.", gray, 6f);
    }
}
