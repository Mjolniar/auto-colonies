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
 * Only called when Station Augments is enabled (check CAAugments.MOD first; the constant is inlined, so it doesn't load this class).
 */
public class CAAugments {
    public static final String MOD = "niko_stationAugments";
    static final String CONVERSION = "SA_economyConversion";
    static final float COMBAT = 0.004f;                        // fleet effect per point of the mod's own combat rating
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

    static String join(List<String> l) { return join(l, ", "); }

    static String join(List<String> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append(sep); sb.append(s); }
        return sb.toString();
    }

    static String ap(float v) { return v == Math.round(v) ? "" + Math.round(v) : String.valueOf(Math.round(v * 10f) / 10f); }

    /** The profile to plan with: Security while the threat is high, as buildings do, for Approve and Auto colonies. */
    static CAData.Profile profileFor(MarketAPI m, CAData.Colony c) {
        return c.mode == CAData.Mode.APPROVE || c.mode == CAData.Mode.AUTO ? CAThreat.effective(m, c.profile) : c.profile;
    }

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
            if (Misc.getMaxIndustries(m) > Misc.getNumIndustries(m)) return;   // a slot is already free
            CAEngine.Result r = CAEngine.evaluate(m, profile, CAData.get().minStability);
            if (r.bestNoSlot == null || r.bestNoSlot.score <= 0f) return;
            value = r.bestNoSlot.score; name = r.bestNoSlot.name;
        }
    }

    /** One augment's effects in the engine's units, weighted by the profile. Null when it's never worth having here. */
    static Opt value(MarketAPI m, Industry station, stationAttachment a, CAData.Profile profile, Map<String, Integer> deficit, float upkeep, Slot slot) {
        String id = a.getId();
        CAEngine.Pick p = new CAEngine.Pick();
        float extra = 0f;
        int combat = rating(a, "combatgood") - rating(a, "combatbad");
        if (id.equals("SA_commsCenter")) { p.fx[2] += 0.1f; p.lines.add("+10% accessibility; unlimited command points for the station's defenders"); }
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
        else if (id.equals("SA_ecmPackage")) { p.fx[3] += 0.05f; p.lines.add("Ground defense x1.05"); }
        else if (id.equals("SA_stationRepairUnit")) { p.fx[4] += 0.05f; p.lines.add("The station recovers from disruption twice as fast"); }
        else if (id.equals("SA_navRelay")) p.lines.add("+1 burn for friendly fleets near the station");
        else if (id.equals("SA_highResSensors")) p.lines.add("Sensor range bonus for friendly fleets near the station");
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
            p.fx[4] += combat * COMBAT;
            p.lines.add(combat > 0 ? "Stronger station in combat (Station Augments rates it " + combat + ")" : "Weaker station in combat");
        }
        float[] w = CAEngine.WEIGHTS[profile.ordinal()];
        float v = extra;
        for (int i = 0; i < 7; i++) v += w[i] * p.fx[i];
        Opt o = new Opt();
        o.id = id; o.name = a.getName(); o.ap = a.getAugmentCost(); o.score = v; o.lines = p.lines;
        Set<String> inc = a.getIncompatibleAugments();
        if (inc != null) o.incompatible.addAll(inc);
        return o;
    }

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
     * augments (Backline Deployment and Macro Thrusters) only the better one is considered.
     */
    static List<Opt> choose(List<Opt> current, List<Opt> fresh, float avail) {
        fresh = new ArrayList<Opt>(fresh);
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
        float avail = pl.budget, currentScore = 0f;
        List<Opt> current = new ArrayList<Opt>();   // Autopilot's augments still worth keeping
        for (stationAttachment a : installed.values()) {
            boolean mine = managed.contains(a.getId());
            if (!mine || CONVERSION.equals(a.getId())) {
                if (a.getBuiltInMode() == BuiltInMode.NOT) avail -= a.getAugmentCost();
                continue;
            }
            try {
                Opt o = value(m, ind, a, profile, deficit, upkeep, slot);
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
                Opt o = value(m, ind, inst, profile, deficit, upkeep, slot);
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
        boolean station = Misc.getStationIndustry(m) instanceof OrbitalStation;
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
            CALog.add(m.getName() + ": you removed " + spec.getName() + " from the station; Autopilot won't add it there again");
        }
    }

    /** Removes, then adds, as planned. Returns null on success, or why nothing changed. */
    static String apply(MarketAPI m, CAData.Colony c, Plan pl, String who) {
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
        if (!failed.isEmpty() && CALog.once("augfail:" + m.getId() + ":" + join(failed))) CALog.add(m.getName() + ": could not add station augments: " + join(failed));
        if (done.isEmpty()) return failed.isEmpty() ? "Nothing to change" : "Could not add " + join(failed);
        CALog.add(m.getName() + ": " + who + " station augments: " + join(done), true);
        return null;
    }

    /** What decides whether a colony needs re-planning before its weekly turn: station, AP and what's installed or known. */
    static String signature(MarketAPI m) {
        Industry ind = Misc.getStationIndustry(m);
        StringBuilder sb = new StringBuilder(ind == null ? "-" : ind.getId()).append('|').append(MarketUtils.getRemainingAugmentBudget(m)).append('|').append(known().size());
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
                if (!(Misc.getStationIndustry(m) instanceof OrbitalStation)) continue;
                String sig = signature(m);
                Float next = nextRun.get(m.getId());
                if (sig.equals(lastSig.get(m.getId())) && next != null && now < next) continue;
                lastSig.put(m.getId(), sig);
                nextRun.put(m.getId(), now + 7f);
                Plan pl = plan(m, c, profileFor(m, c));
                if (!pl.changes()) continue;
                if (c.mode == CAData.Mode.AUTO) apply(m, c, pl, "Auto set");
                else if (CALog.once("aug:" + m.getId() + ":" + pl.summary())) {
                    CALog.add(m.getName() + ": recommends station augments (" + pl.summary() + "); Apply in the colony panel", true);
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
        if (!pl.mine.isEmpty()) t.addPara("Added by Autopilot: %s", 4f, hl, join(pl.mine));
        if (!pl.yours.isEmpty()) t.addPara("Yours (never changed): %s", 4f, hl, join(pl.yours));
        if (pl.note != null) t.addPara(CALog.esc(pl.note), gray, 4f);
        if (!pl.changes()) t.addPara("Nothing to change: this is the best loadout of the augments you know.", gray, 6f);
        else {
            for (Opt o : pl.add) {
                t.addPara("Add %s (%s AP): %s", 6f, hl, o.name, ap(o.ap), CAEngine.valueText(o.score));
                for (int j = 0; j < Math.min(3, o.lines.size()); j++) t.addPara("    " + CALog.esc(o.lines.get(j)), gray, 2f);
            }
            for (String n : pl.removeNames) t.addPara("Remove %s to make room", 6f, hl, n);
            t.addPara("Value of the change: about %s", 6f, hl, CAEngine.valueText(pl.gain));
            t.addButton("Apply augment changes", CAColonyDialog.AUG_APPLY, base, dark, Alignment.MID, CutStyle.ALL, 240f, 24f, 6f);
            String when = s.augmentMode != 0 ? "Station augments are set to Recommend only, so they change when you click Apply."
                    : c.mode == CAData.Mode.AUTO ? "Auto applies this within a week." : "Auto colonies apply this by themselves; here it waits for you.";
            t.addPara(when, gray, 4f);
        }
        if (!c.augSkip().isEmpty()) {
            List<String> names = new ArrayList<String>();
            for (String id : c.augSkip()) { stationAugmentSpec sp = specs().get(id); names.add(sp == null ? id : sp.getName()); }
            t.addPara("Not added again because you removed them: %s", 8f, hl, join(names));
            t.addButton("Allow again", CAColonyDialog.AUG_UNSKIP, base, dark, Alignment.MID, CutStyle.ALL, 140f, 22f, 4f);
        }
        t.addPara("You know %s station augments. Augment blueprints from loot and markets teach more; ones that use up an item, "
                + "or give AP back for a drawback, are left to you.", 6f, gray, hl, "" + known().size());
    }

    /** The empire panel's section: one line per colony with a station. */
    public static void renderEmpire(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        t.addPara("Mode: %s. Picks the augments worth the most within each station's AP. Auto colonies apply them; "
                + "the others wait for Apply. Augments you install are never changed.", 6f, hl, CASettings.AUGMENT_NAMES[Math.max(0, Math.min(2, s.augmentMode))]);
        int shown = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if (c.mode == CAData.Mode.OFF || !(Misc.getStationIndustry(m) instanceof OrbitalStation)) continue;
            shown++;
            Plan pl;
            try { pl = plan(m, c, profileFor(m, c)); }
            catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "station augment plan failed at " + m.getName(), e); continue; }
            if (!pl.changes()) { t.addPara("%s, %s: %s of %s AP, best loadout.", 6f, hl, m.getName(), pl.station, ap(pl.used), ap(pl.budget)); continue; }
            t.addPara("%s, %s: %s of %s AP. Recommends: %s (%s).", 6f, hl, m.getName(), pl.station, ap(pl.used), ap(pl.budget), pl.summary(), CAEngine.valueText(pl.gain));
            t.addButton("Apply", new String[]{"aug", "apply", m.getId()}, base, dark, Alignment.MID, CutStyle.ALL, 120f, 22f, 4f);
        }
        if (shown == 0) t.addPara("None of your managed colonies has an orbital station.", gray, 6f);
    }
}
