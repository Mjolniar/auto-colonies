package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager;
import com.fs.starfarer.api.impl.campaign.intel.events.EventFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HegemonyAICoresActivityCause;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.LuddicChurchHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.PerseanLeagueHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.RemnantHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.SindrianDiktatHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.TriTachyonHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.TriTachyonStandardActivityCause;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Low profile (0.2.43.0): your colonies keep growing, but draw as little attention from the sector as the game allows.
 * Pirates, and the Persean League once you could join it, count colony size alone, so nothing avoids those; every other
 * crisis comes from what your colonies have and make, and Low profile changes the plan to stay clear of it:
 * - every point of colony crisis progress is priced PRICE_MULT times higher (CACrisis.pointCost), so attention has to pay
 *   for itself many times over, and Patrol HQ, Military Base and High Command, which slow every crisis, count for as much more;
 * - Hegemony: no AI cores above size 3 (CAItems.coreLimit), and an AI core administrator makes way for a human one;
 * - Luddic Path: no building, item or core takes a colony to 7 Pather interest, where a cell comes (at size 4 or more),
 *   and items that keep a colony over it go back to Autopilot Storage;
 * - Tri-Tachyon: exports competing with it (two or more of the goods it makes, 4 units from a size 4 colony) are priced;
 * - Sindrian Diktat: fuel from 4 units is priced, from any building (Fuel Production's was already);
 * - Luddic Church: a habitable colony with farming or light industry and no industrial or military building (a Luddic
 *   majority) is priced, and an industrial or military building that ends one is worth what it saves;
 * - no free ports (Nexerelin's police), no raids on other factions' colonies, no sabotage by agents, no Omega or IX cores,
 *   no RAT settlement in a system with a Remnant nexus, and Hegemony inspections are never resisted.
 * Each faction's rule lifts once its crisis is over for good (beaten, a deal made, Persean League membership for the
 * Hegemony): from then on Low profile expands at full speed as far as that faction goes. Nothing here skips the game's
 * own rules: it only changes what Autopilot chooses.
 */
public class CALowProfile {
    /** Attention is priced this many times Full expansion's rate. */
    public static final float PRICE_MULT = 10f;
    /** Log lines start with this after the colony's name (CATheme's QUIET code). */
    static final String TAG = "low profile: ";

    public static boolean on() {
        try { CASettings s = CAData.settings(); return s != null && s.lowProfile; } catch (Throwable t) { return false; }
    }

    /** Turns it on or off (Settings, or the first colony's dialog); every Auto colony replans on its next pass. */
    public static void set(CASettings s, boolean on, String who) {
        if (s.lowProfile == on) return;
        s.lowProfile = on;
        CAAuto.nextPass.clear();
        if (!on) {
            CALog.forget("lowprofile:on");
            CALog.add(TAG + "off (" + who + "). Full expansion again: Autopilot builds whatever pays most", true);
        }
    }

    // ---------------- whose crisis can still come: each rule lifts once that faction's is over for good ----------------

    static boolean hegemony() { return CACrisis.hegemonyCounts(); }

    static boolean pather() { return CACrisis.pathCounts(); }

    static boolean church() {
        try { return !LuddicChurchHostileActivityFactor.isDefeatedExpedition() && !LuddicChurchHostileActivityFactor.isMadeDeal(); }
        catch (Throwable t) { return true; }
    }

    static boolean triTachyon() {
        try { return !TriTachyonHostileActivityFactor.isPlayerCounterRaidedTriTach() && TriTachyonHostileActivityFactor.getPrimaryTriTachyonSystem() != null; }
        catch (Throwable t) { return true; }
    }

    static boolean diktat() {
        // 0.3.0.9: the rule CACrisis.diktat prices by (on 0.98.5a only while the Askonia standoff is under way)
        try { return CACrisis.diktatCounts(SindrianDiktatHostileActivityFactor.isMadeDeal(), SindrianDiktatHostileActivityFactor.isPlayerDefeatedDiktatAttack(), CASurvival.diktatStandoff()); }
        catch (Throwable t) { return true; }
    }

    // ---------------- the Luddic Path ----------------

    /** Pather interest that gives a colony of size 4 or more a cell (minInterestForPatherCells). */
    static float cellLine() { return CACrisis.setting("minInterestForPatherCells", 7f); }

    public static float interest(MarketAPI m) {
        try { return LuddicPathBaseManager.getLuddicPathMarketInterest(m); } catch (Throwable t) { return 0f; }
    }

    /** Pather interest an item adds in a building (BaseIndustry.getPatherInterest's item tags). */
    public static int itemInterest(SpecialItemSpecAPI spec) {
        if (spec == null) return 0;
        if (spec.hasTag(Items.TAG_PATHER1)) return 1;
        if (spec.hasTag(Items.TAG_PATHER2)) return 2;
        if (spec.hasTag(Items.TAG_PATHER4)) return 4;
        if (spec.hasTag(Items.TAG_PATHER6)) return 6;
        if (spec.hasTag(Items.TAG_PATHER8)) return 8;
        if (spec.hasTag(Items.TAG_PATHER10)) return 10;
        return 0;
    }

    static int itemInterest(String itemId) {
        try { return itemInterest(Global.getSettings().getSpecialItemSpec(itemId)); } catch (Throwable t) { return 0; }
    }

    /** Pure: whether `add` more interest on top of `now` reaches the line. */
    static boolean crosses(float now, float add, float line) { return add > 0f && now + add >= line; }

    /**
     * Whether adding `add` Pather interest at m goes over Low profile's line, while Pather crises can come. Whatever the
     * colony's size: a cell needs size 4, and colonies grow.
     */
    public static boolean pathBlocked(MarketAPI m, float add) {
        return add > 0f && on() && pather() && crosses(interest(m), add, cellLine());
    }

    /** Pure: the most core points that keep interest under the line with `other` from everything else; 0 when it's over already. */
    static int pathLimit(float other, float line) {
        return other >= line ? 0 : Math.max(0, (int) Math.ceil(line - other) - 1);
    }

    /** The most AI core points m may hold in Low profile, or -1 where it sets no limit. */
    public static int coreLimit(MarketAPI m) {
        if (!on()) return -1;
        int lim = Integer.MAX_VALUE;
        if (hegemony() && m.getSize() > HegemonyAICoresActivityCause.IGNORE_COLONY_THRESHOLD) lim = 0;
        if (pather()) lim = Math.min(lim, pathLimit(interest(m) - CAItems.aiPoints(m), cellLine()));
        return lim == Integer.MAX_VALUE ? -1 : lim;
    }

    /** Why m's core limit is Low profile's, for the panels and the log; null when it isn't. */
    public static String limitWhy(MarketAPI m, int lim) {
        if (!on() || coreLimit(m) != lim) return null;
        if (lim == 0 && hegemony() && m.getSize() > HegemonyAICoresActivityCause.IGNORE_COLONY_THRESHOLD)
            return "limit 0: Low profile keeps AI cores out of colonies above size 3, where the Hegemony counts every one";
        return "limit " + lim + ": Low profile keeps Pather interest under " + (int) cellLine() + ", where a Pather cell comes";
    }

    // ---------------- the planner ----------------

    /**
     * Low profile's part of a build or upgrade pick (CAEngine.evaluate, before finish): the Pather line, which it never
     * starts past (CAEngine.Pick.lowProfile), and the attention it draws from Tri-Tachyon, the Sindrian Diktat and the
     * Luddic Church, priced at Low profile's rate.
     */
    public static void score(MarketAPI m, Industry inst, Industry replaced, CAEngine.Pick p) {
        if (!on() || m == null || inst == null) return;
        try {
            float add = inst.getPatherInterest() - (replaced != null ? baseInterest(m, replaced.getId()) : 0f);
            if (pathBlocked(m, add)) {
                float now = interest(m);
                p.lowProfile = "Low profile: takes Pather interest here from " + Misc.getRoundedValueMaxOneAfterDecimal(now) + " to "
                        + Misc.getRoundedValueMaxOneAfterDecimal(now + add) + "; a Pather cell comes at " + (int) cellLine()
                        + (m.getSize() < 4 ? " once the colony reaches size 4" : "") + ", so Autopilot never starts this";
            }
        } catch (Throwable ignored) { }
        try { triTachyon(m, p); } catch (Throwable ignored) { }
        try {
            Integer fuel = p.supplied.get(Commodities.FUEL);
            if (fuel != null && fuel > 0 && !"fuelprod".equals(p.id)) CACrisis.diktat(m, fuel, p);   // Fuel Production's own is in CAEngine.effects
        } catch (Throwable ignored) { }
        try { church(m, inst.getSpec(), p); } catch (Throwable ignored) { }
    }

    /** What a building of that kind adds on its own, without a core or item (both carry over to an upgrade). */
    static float baseInterest(MarketAPI m, String id) {
        try { return Global.getSettings().getIndustrySpec(id).getNewPluginInstance(m).getPatherInterest(); } catch (Throwable t) { return 0f; }
    }

    // ---- Tri-Tachyon: competing exports

    static float ttDay = -1f;
    static Map<String, int[]> tt = new LinkedHashMap<String, int[]>();

    /**
     * Every good Tri-Tachyon makes 5 or more of (TriTachyonStandardActivityCause), with your best output of it and your
     * biggest producer's size; read once a day.
     */
    static Map<String, int[]> ttData() {
        float day = CADay.now();
        if (CATime.fresh(ttDay, day, 1f)) return tt;   // 0.3.0.0 (A1 fix): day >= 0 never held on the game's negative clock, so it was never kept
        ttDay = day;
        Map<String, int[]> out = new LinkedHashMap<String, int[]>();
        try {
            Set<String> coms = new LinkedHashSet<String>();
            for (MarketAPI x : Global.getSector().getEconomy().getMarketsCopy()) {
                if (!Factions.TRITACHYON.equals(x.getFactionId())) continue;
                for (String c : TriTachyonStandardActivityCause.COMPETING_COMMODITIES) {
                    CommodityOnMarketAPI com = x.getCommodityData(c);
                    if (com != null && com.getMaxSupply() >= TriTachyonStandardActivityCause.MIN_TRITACH_PRODUCTION) coms.add(c);
                }
            }
            for (String c : coms) {
                TriTachyonStandardActivityCause.CompetitorData cd = TriTachyonStandardActivityCause.computeCompetitorData(Factions.TRITACHYON, Factions.PLAYER, c);
                out.put(c, new int[]{cd.competitorMaxProd, cd.competitorMaxMarketSize});
            }
        } catch (Throwable t) { out.clear(); }
        tt = out;
        return out;
    }

    /**
     * Pure, as TriTachyonStandardActivityCause.getProgress: nothing unless two or more goods compete (minProd units, a
     * producer of minSize); then each one's best output times mult, rounded, all times dealtMult.
     */
    static int ttProgress(Collection<int[]> rows, int minProd, int minSize, float mult, float dealtMult) {
        int n = 0, total = 0;
        for (int[] r : rows) {
            if (r[0] < minProd || r[1] < minSize) continue;
            n++;
            total += Math.round(r[0] * mult);
        }
        if (n <= 1) return 0;
        return dealtMult == 1f ? total : Math.round(total * dealtMult);
    }

    static void triTachyon(MarketAPI m, CAEngine.Pick p) {
        if (p.supplied.isEmpty() || !triTachyon()) return;
        Map<String, int[]> now = ttData();
        if (now.isEmpty()) return;
        int minProd = TriTachyonStandardActivityCause.MIN_COMPETITOR_PRODUCTION, minSize = TriTachyonStandardActivityCause.MIN_COMPETITOR_MARKET_SIZE;
        List<int[]> before = new ArrayList<int[]>(), after = new ArrayList<int[]>();
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, int[]> e : now.entrySet()) {
            int[] r = e.getValue();
            before.add(r);
            Integer u = p.supplied.get(e.getKey());
            if (u == null || u <= 0) { after.add(r); continue; }
            int[] q = {Math.max(r[0], u), Math.max(r[1], m.getSize())};   // a market's supply is its best source's, so outputs don't add up
            after.add(q);
            if (q[0] > r[0] || q[1] > r[1]) {
                try { names.add(Global.getSettings().getCommoditySpec(e.getKey()).getName()); } catch (Throwable ignored) { names.add(e.getKey()); }
            }
        }
        float mult = TriTachyonStandardActivityCause.PROD_PROGRESS_MULT;
        float dm = 1f;
        try { if (TriTachyonHostileActivityFactor.isDealtWithMercAttack()) dm = TriTachyonHostileActivityFactor.DEALT_WITH_MERC_PROGRESS_MULT; } catch (Throwable ignored) { }
        int b = ttProgress(before, minProd, minSize, mult, dm), a = ttProgress(after, minProd, minSize, mult, dm);
        if (a <= b) return;
        float cost = CACrisis.progressCost(CACrisis.meter(), b, a);
        p.fx[0] -= cost;
        p.lines.add("Low profile: competes with Tri-Tachyon's exports (" + CAContracts.join(names) + "): +" + (a - b)
                + " colony crisis progress a month, about " + Misc.getDGSCredits(cost) + " a month");
    }

    // ---- the Luddic Church: Luddic majorities

    /** As LuddicMajority.matchesBonusConditions: industrial, military and command buildings keep a Luddic majority away. */
    static boolean blocksMajority(IndustrySpecAPI s) {
        return s != null && (s.hasTag(Industries.TAG_INDUSTRIAL) || s.hasTag(Industries.TAG_MILITARY) || s.hasTag(Industries.TAG_COMMAND));
    }

    static boolean keepsChurchOut(MarketAPI m) {
        for (Industry ind : m.getIndustries()) if (blocksMajority(ind.getSpec())) return true;
        return false;
    }

    static boolean rural(MarketAPI m) {
        for (Industry ind : m.getIndustries()) if (ind.getSpec().hasTag(Industries.TAG_RURAL)) return true;
        return false;
    }

    /** The Church's progress now (LuddicChurchStandardActivityCause): the size of every colony of yours with a Luddic majority. */
    static int churchProgress() {
        int n = 0;
        for (MarketAPI x : Misc.getPlayerMarkets(false)) if (x.hasCondition(Conditions.LUDDIC_MAJORITY)) n += x.getSize();
        return n;
    }

    /**
     * A Luddic majority comes to a habitable colony with farming or light industry and no industrial, military or command
     * building, at its next size change; the Knights of Ludd's crisis counts its size, and ends with the colony going to
     * the Church. A rural building that would bring one pays for that progress; an industrial or military building that
     * ends one (or keeps one from coming) earns it.
     */
    static void church(MarketAPI m, IndustrySpecAPI spec, CAEngine.Pick p) {
        if (spec == null || !church() || !m.hasCondition(Conditions.HABITABLE) || keepsChurchOut(m)) return;
        boolean has = m.hasCondition(Conditions.LUDDIC_MAJORITY), ruralNow = rural(m);
        int now = churchProgress(), without = now - (has ? m.getSize() : 0);
        float worth = CACrisis.progressCost(CACrisis.meter(), without, without + m.getSize());
        if (worth <= 0f) return;
        if (blocksMajority(spec) && (has || ruralNow)) {
            p.fx[0] += worth;
            p.lines.add("Low profile: " + (has ? "ends the Luddic majority here at the colony's next size change" : "keeps a Luddic majority from coming here")
                    + " (-" + m.getSize() + " Knights of Ludd crisis progress a month), worth about " + Misc.getDGSCredits(worth) + " a month");
        } else if (spec.hasTag(Industries.TAG_RURAL) && !has && !ruralNow) {
            p.fx[0] -= worth;
            p.lines.add("Low profile: with no industrial or military building here, this brings a Luddic majority at the colony's next size "
                    + "change (+" + m.getSize() + " Knights of Ludd crisis progress a month), about " + Misc.getDGSCredits(worth) + " a month");
        }
    }

    // ---------------- other systems ----------------

    /** RAT settlements: never in a system with a Remnant nexus, whose crisis only ends with the nexus destroyed. */
    public static boolean remnantSystem(StarSystemAPI sys) {
        if (sys == null || !on()) return false;
        try { return RemnantHostileActivityFactor.getRemnantNexus(sys) != null; } catch (Throwable t) { return false; }
    }

    // ---------------- daily: what crossed a line (growth, or before Low profile was on) ----------------

    /** Daily from CAAuto with your Auto colonies: one change a day at most, like the item and core passes. */
    public static void run(CAData d, CASettings s, List<MarketAPI> auto) {
        if (!s.lowProfile) { CALog.forget("lowprofile:on"); return; }
        if (CALog.once("lowprofile:on"))
            CALog.add(TAG + "on. Your colonies keep growing, but attention counts ten times as much, and Autopilot stays clear of what "
                    + "starts each faction's crisis; the Crises page says what that is", true);
        Set<MarketAPI> frozen = CAItems.inspected();   // pulling anything out once an inspection is sent disrupts every building there
        for (MarketAPI m : auto) {
            if (frozen.contains(m) || CAEngine.isOutpost(m) || CAHypershunt.is(m)) continue;
            try { if (pullItem(d, m) || swapAdmin(d, m)) return; }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "low profile check failed at " + m.getName(), t); }
        }
        league();
    }

    /**
     * A colony at or over the Pather line whose items and cores are enough to get it under: the item adding least per
     * point of interest goes back to Autopilot Storage (cores go by CAItems.coreLimit). Never a locked building's, one that
     * uses its item up, or at a colony under the stability floor (0.3.0.0: CAStability's floor, max(3, minStability), instead
     * of its own 3); and if pulling it costs the colony stability that takes it under the floor, it goes back in.
     */
    static boolean pullItem(CAData d, MarketAPI m) {
        if (!pather() || CAStability.below(m)) return false;
        float now = interest(m), line = cellLine();
        if (now < line) return false;
        CAData.Colony c = d.colony(m.getId());
        float items = 0f, worstPer = Float.MAX_VALUE;
        Industry worst = null;
        for (Industry ind : m.getIndustries()) {
            SpecialItemData it = ind.getSpecialItem();
            if (it == null) continue;
            int add = itemInterest(it.getId());
            if (add <= 0) continue;
            items += add;
            if (c.locks.contains(ind.getId()) || ind.getSpec().hasTag("consumes") || CAMods.leftToYou(ind.getId()) != null) continue;
            float per = Math.max(0f, CAItems.itemScore(it.getId(), m, c.profile, ind)) / add;
            if (per < worstPer) { worstPer = per; worst = ind; }
        }
        if (worst == null || now - items - CAItems.aiPoints(m) >= line) {
            if (CALog.once("lowprofile:over:" + m.getId()))   // 0.3.0.9: kept, starsector.log only
                CALog.quiet(m.getName() + ": " + TAG + "Pather interest " + Misc.getRoundedValueMaxOneAfterDecimal(now) + " (a cell comes at " + (int) line
                        + ") from its buildings or locked items, which Autopilot doesn't take down; no more is added here");
            return false;
        }
        SpecialItemData it = worst.getSpecialItem();
        String itName = CAItems.name(it.getId()), from = worst.getCurrentName();   // 0.3.0.0 (fix): named before the item comes out
        float before = CAStability.now(m);
        worst.setSpecialItem(null);
        // 0.3.0.0 (fix): from here until it's in Storage the item is nowhere, so anything that throws (a mod industry's apply()
        // failing once its item is gone) puts it back in the building first; 0.2.45.1 stored it first, and 0.3.0's measuring moved that
        try {
            m.reapplyIndustries();
            // 0.3.0.0: what an item adds to stability is easier to measure than to predict; one the colony can't lose goes straight back
            if (!CAStability.canKeep(m, before, TAG + "taking the " + itName + " out of " + from)) {
                worst.setSpecialItem(it);
                m.reapplyIndustries();
                return false;
            }
            CAStores.of(m).addSpecial(it, 1f);
        } catch (Throwable t) {
            if (worst.getSpecialItem() == null) {
                worst.setSpecialItem(it);
                try { m.reapplyIndustries(); } catch (Throwable ignored) { }
            }
            throw t;
        }
        CALog.add(m.getName() + ": " + TAG + "took the " + itName + " out of " + from
                + " and back to Autopilot Storage: Pather interest " + Misc.getRoundedValueMaxOneAfterDecimal(now) + ", and a Pather cell comes at " + (int) line);
        return true;
    }

    /**
     * An AI core administrator counts 10 toward Hegemony inspections and 10 Pather interest: an idle human administrator
     * takes over and the core goes to Autopilot Storage. The AI's skills go with it (Hypercognition's +1 stability), so 0.3.0.0
     * asks CAStability whether the colony can lose that and stay at or above the floor, and still never below 4 (0.2.45.1's rule:
     * CAStability.adminOwn only knows two skills). The result is measured too: one that leaves the colony under the floor is
     * undone. Never an Omega core (AAR: +3 stability, and past size 10 taking it out starts the Great Civil War).
     */
    static boolean swapAdmin(CAData d, MarketAPI m) {
        PersonAPI a = m.getAdmin();
        if (a == null || !a.isAICore()) return false;
        boolean heg = hegemony() && m.getSize() > HegemonyAICoresActivityCause.IGNORE_COLONY_THRESHOLD;
        if (!heg && !pather()) return false;
        // 0.3.0.0 (fix): the 0.2.45.1 guard stays alongside canLose, since a mod AI core's stability skill counts 0 there
        if (CAStability.below(m) || m.getStabilityValue() < 4f) return false;
        if (CAOmega.governs(m)) {   // 0.3.0.0 (fix): never swap out an Omega governor
            if (CALog.once("lowprofile:omega:" + m.getId()))   // 0.3.0.9: kept, starsector.log only
                CALog.quiet(m.getName() + ": " + TAG + "its Omega core administrator stays: taking it out costs its +3 stability, and past size 10 starts AAR's Great Civil War");
            return false;
        }
        if (!CAStability.canLose(m, -CAStability.adminOwn(a), null)) return false;   // quiet: checked again below with the human's own
        AdminData h = CAItems.idleAdmin(d);
        if (h == null) {
            // 0.3.0.9: the advice on screen; the waiting part ("takes over once one is free") is gone
            if (CALog.once("lowprofile:aiadmin:" + m.getId()))
                CALog.add(m.getName() + ": " + TAG + "hire an administrator, or turn on Hire administrators, to replace its AI core administrator "
                        + "(it counts 10 toward Hegemony inspections and the Pathers' interest)");
            return false;
        }
        if (!CAStability.canLose(m, CAStability.adminOwn(h.getPerson()) - CAStability.adminOwn(a), TAG + "administrator "
                + h.getPerson().getNameString() + " taking over from the AI core")) return false;
        String core = a.getAICoreId(), human = h.getPerson().getNameString();
        float before = CAStability.now(m);
        m.setAdmin(h.getPerson());
        h.setMarket(m);
        // 0.3.0.0 (fix): measure it (setAdmin refreshes the governing skills); under the floor, or anything throwing before the
        // core is in Storage, puts the AI core back in charge
        try {
            m.reapplyIndustries();
            if (!CAStability.canKeep(m, before, TAG + "administrator " + human + " taking over from the AI core")) {
                undoAdmin(m, a, h);
                return false;
            }
            if (core != null) CAStores.of(m).addCommodity(core, 1f);
        } catch (Throwable t) {
            if (m.getAdmin() != a) undoAdmin(m, a, h);
            throw t;
        }
        CALog.forget("lowprofile:aiadmin:" + m.getId());
        CALog.add(m.getName() + ": " + TAG + "administrator " + human + " takes over from the AI core administrator"
                + (core != null ? ", which went back to Autopilot Storage" : ""));
        return true;
    }

    /** 0.3.0.0 (fix): the AI core governs again and the human administrator is free (setAdmin frees them too); never throws. */
    static void undoAdmin(MarketAPI m, PersonAPI ai, AdminData h) {
        try { m.setAdmin(ai); } catch (Throwable ignored) { }
        try { if (h.getMarket() == m) h.setMarket(null); } catch (Throwable ignored) { }
        try { m.reapplyIndustries(); } catch (Throwable ignored) { }
    }

    /** The Persean League's crisis counts colony size alone: one line saying so, and what ends it. */
    static void league() {
        try {
            HostileActivityEventIntel ha = CACrisis.event();
            if (ha == null) return;
            EventFactor f = ha.getFactorOfClass(PerseanLeagueHostileActivityFactor.class);
            if (f == null || f.getProgress(ha) <= 0) return;
            if (CALog.once("lowprofile:league"))
                CALog.add(TAG + "the Persean League's crisis counts colony size alone, so nothing Autopilot builds avoids it. Joining the League "
                        + "at Kazeron ends it and the Hegemony's; that's your call", true);
        } catch (Throwable ignored) { }
    }

    // ---------------- the Crises page ----------------

    public static void render(TooltipMakerAPI t, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), good = Misc.getPositiveHighlightColor();
        CATheme.heading(t, "Low profile", 12f);
        if (!s.lowProfile) {   // 0.3.0.0 (WS-F3 fix): shorter; the Settings row has the rest
            t.addPara("Off (Settings, Expansion style).", gray, 6f);
            return;
        }
        t.addPara("On: crisis progress counts %s; a rule lifts once its crisis ends.", 6f, hl, "x" + (int) PRICE_MULT);
        rule(t, "Hegemony", hegemony(), "no AI cores above size 3", hl, good);
        rule(t, "Luddic Path", pather(), "no colony reaches " + (int) cellLine() + " Pather interest", hl, good);
        rule(t, "Luddic Church", church(), "Luddic-majority colonies are priced in", hl, good);
        rule(t, "Tri-Tachyon", triTachyon(), "exports competing with theirs are priced in", hl, good);
        rule(t, "Sindrian Diktat", diktat(), "fuel from 4 units is priced in", hl, good);
        t.addPara("Always: no free ports, raids, sabotage, Omega or IX cores, or settlements near a Remnant nexus.", gray, 6f);
        // 0.3.0.0 (WS-F3 fix pass 2): shorter; the pirate and League crises and Kazeron are in the guide
        t.addPara("Auto-expansion stays in systems you hold.", gray, 4f);
    }

    static void rule(TooltipMakerAPI t, String who, boolean live, String what, Color hl, Color good) {
        if (live) t.addPara("%s: " + what + ".", 4f, hl, who);
        else t.addPara("%s: crisis over for good; rule lifted.", 4f, good, who);
    }
}
