package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.13.0: defense fleets without Nexerelin. Auto Colonies raises its own fleets for your faction with the game's fleet
 * factory (your doctrine and known hulls, the source colony's ship quality) and pays for them in credits by fleet points.
 * Runs only while Nexerelin is off; with Nexerelin, CADefense and CAWar use its fleet requests. No other mod's classes.
 * Once a day, sharing the day's launches (one, or WAR_RICH_PER_DAY while rich):
 *  1. Emergency defense (setting Emergency defense fleets): a raid or raid group headed for an Auto colony's system, or
 *     hostiles there that outweigh its defenses. Paid from anything above the credit reserve, once per system every
 *     DEFENSE_COOLDOWN_DAYS.
 *  2. Relief (War level Defend and up): a colony under siege, once per colony every WAR_RELIEF_DAYS, paid the same way.
 *  3. Proactive defense (Defend and up): threat high or a crisis coming. Paid as an emergency only when hostiles really
 *     target the system; otherwise from the military rung, else the budget (the surplus while rich), once per system every
 *     CAWarPolicy.DEFENSE_COOLDOWN_DAYS.
 *  4. Standing guard (Defend and up, while spending freely): a defense fleet at every colony, biggest first.
 * Capacity: the fleet points your colonies can field at once (OWN_CAP_BASE plus each Patrol HQ, Military Base and High
 * Command). Fleets out count against it until they're gone, and a fleet that isn't urgent keeps WAR_RESERVE of it free.
 * Each fleet is recorded in CAData.warOps as "own|fleetId|targetId|sourceId" (ids and a timestamp only), and the record
 * goes when the fleet is gone.
 */
public final class CAOwnWar {
    private CAOwnWar() { }

    static final String OWN = "own|";
    /** Fleet memory key: the kind of operation the fleet was raised for. */
    static final String MEM = "$ac_ownFleet";
    static final float MIN_FP = CATuning.WAR_MIN_FP;
    static final String NAME = "Defense Fleet";

    // ---------------- pure rules (JTOwnWarCheck) ----------------

    /** Fleet points your colonies can field at once. */
    static float capacity(int patrolHQs, int militaryBases, int highCommands) {
        return CATuning.OWN_CAP_BASE + patrolHQs * CATuning.OWN_CAP_PATROL + militaryBases * CATuning.OWN_CAP_MILITARY
                + highCommands * CATuning.OWN_CAP_HIGH;
    }

    /** Fleet points free for a new fleet: an urgent one (emergency, relief) may use all of them, the rest keep the reserve. */
    static float spare(float capacity, float inUse, boolean urgent, boolean rich) {
        float keep = urgent ? 0f : (rich ? CATuning.WAR_RICH_RESERVE : CATuning.WAR_RESERVE) * capacity;
        return capacity - inUse - keep;
    }

    /** Size of a fleet: what it wants (bigger while spending freely), at least MIN_FP, within the spare points; 0 if that's under MIN_FP. */
    static float size(float want, boolean rich, float spare) {
        float fp = Math.round(Math.min(Math.max(MIN_FP, want * (rich ? CATuning.WAR_RICH_SIZE : 1f)), spare));
        return fp < MIN_FP ? 0f : fp;
    }

    /**
     * The defense an Auto colony's system needs, as CADefense sizes it: a quarter more than a raid group's fleets (at least
     * twice MIN_FP), a quarter more than the hostiles when a raid is coming, or when hostiles outweigh the defense; 0 otherwise.
     */
    static float need(float groupFP, boolean raidComing, float hostileFP, float ours) {
        if (groupFP > 0f) return Math.max(MIN_FP * 2f, Math.max(groupFP, hostileFP) * 1.25f - ours);
        if (raidComing) return Math.max(MIN_FP * 2f, hostileFP * 1.25f - ours);
        if (hostileFP > Math.max(150f, ours)) return hostileFP * 1.25f - ours;
        return 0f;
    }

    static float cost(float fp) { return Math.round(fp * CATuning.OWN_FLEET_COST_PER_FP); }

    static int perDay(boolean rich) { return rich ? CATuning.WAR_RICH_PER_DAY : 1; }

    // ---------------- what threatens a system (vanilla only) ----------------

    static float hostileFP(StarSystemAPI sys) {
        float fp = 0f;
        for (CampaignFleetAPI f : sys.getFleets()) if (CASiege.hostile(f)) fp += f.getFleetPoints();
        return fp;
    }

    /** A raid group (GenericRaidFGI) aimed at your colonies in this system, still on its way or there, or null. */
    static GenericRaidFGI raidGroup(StarSystemAPI sys) {
        try {
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel()) {
                if (!(i instanceof GenericRaidFGI) || i.isEnded() || i.isEnding()) continue;
                GenericRaidFGI g = (GenericRaidFGI) i;
                if (!g.isPlayerTargeted() || g.isAborted() || g.isFailed() || g.isSucceeded()) continue;
                if (g.getRaidAction() != null && g.getRaidAction().getWhere() == sys) return g;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    static float groupFP(GenericRaidFGI g) {
        float fp = 0f;
        try { for (CampaignFleetAPI f : g.getFleets()) if (f != null && f.isAlive()) fp += f.getFleetPoints(); }
        catch (Throwable ignored) { }
        return fp;
    }

    /** A hostile raid or invasion (RaidIntel) targeting the system. */
    static boolean raidIntel(StarSystemAPI sys) {
        try {
            for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(RaidIntel.class)) {
                RaidIntel r = (RaidIntel) i;
                if (r.isEnded() || r.isEnding() || r.getSystem() != sys || r.getFaction() == null) continue;
                if (r.getFaction().isHostileTo(Factions.PLAYER)) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    /** Hostiles or a raid really target the colony's system (CADefense.realThreat's rule). */
    static boolean realThreat(MarketAPI m) {
        StarSystemAPI sys = m == null ? null : m.getStarSystem();
        if (sys == null) return false;
        if (raidGroup(sys) != null || raidIntel(sys)) return true;
        return hostileFP(sys) > Math.max(150f, CAItems.defenseFP(m) + CASiege.friendlyFP(sys));
    }

    // ---------------- fleets out ----------------

    /** The fleets out today: their records, total fleet points and targets. */
    static final class Out {
        float cap, inUse;
        final Set<String> targets = new HashSet<String>(), systems = new HashSet<String>();
    }

    static Out tend(CAData d) {
        Out o = new Out();
        int phq = 0, mb = 0, hc = 0;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m.hasIndustry(Industries.PATROLHQ)) phq++;
            if (m.hasIndustry(Industries.MILITARYBASE)) mb++;
            if (m.hasIndustry(Industries.HIGHCOMMAND)) hc++;
        }
        o.cap = capacity(phq, mb, hc);
        for (Iterator<Map.Entry<String, Long>> it = d.warOps().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> e = it.next();
            if (!e.getKey().startsWith(OWN)) continue;
            String[] p = e.getKey().split("\\|");
            CampaignFleetAPI f = p.length < 4 ? null : find(p[1], p[2], p[3]);
            if (f == null || !f.isAlive()) {
                // gone (destroyed or home): the record goes, unless it was made today and the fleet isn't placed yet
                if (e.getValue() == null || Global.getSector().getClock().getElapsedDaysSince(e.getValue()) >= 1f) it.remove();
                continue;
            }
            o.inUse += f.getFleetPoints();
            o.targets.add(p[2]);
            MarketAPI t = Global.getSector().getEconomy().getMarket(p[2]);
            if (t != null && t.getStarSystem() != null) o.systems.add(t.getStarSystem().getId());
        }
        return o;
    }

    /** The fleet with this id, looked for where it can be: the source's and target's locations and hyperspace. */
    static CampaignFleetAPI find(String fleetId, String targetId, String sourceId) {
        List<LocationAPI> locs = new ArrayList<LocationAPI>();
        for (String id : new String[]{targetId, sourceId}) {
            MarketAPI m = Global.getSector().getEconomy().getMarket(id);
            if (m != null && m.getPrimaryEntity() != null && m.getPrimaryEntity().getContainingLocation() != null)
                locs.add(m.getPrimaryEntity().getContainingLocation());
        }
        locs.add(Global.getSector().getHyperspace());
        for (LocationAPI l : locs) for (CampaignFleetAPI f : l.getFleets()) if (fleetId.equals(f.getId())) return f;
        return null;
    }

    static boolean underWay(Out o, MarketAPI m) {
        return o.targets.contains(m.getId()) || (m.getStarSystem() != null && o.systems.contains(m.getStarSystem().getId()));
    }

    /** The nearest colony of yours with a working spaceport, where the fleet is raised. */
    static MarketAPI source(MarketAPI target) {
        MarketAPI best = null; float bestDist = Float.MAX_VALUE;
        if (target.getPrimaryEntity() == null) return null;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m.getPrimaryEntity() == null || !spaceport(m)) continue;
            float dist = Misc.getDistanceLY(m.getPrimaryEntity(), target.getPrimaryEntity());
            if (dist < bestDist) { bestDist = dist; best = m; }
        }
        return best;
    }

    static boolean spaceport(MarketAPI m) {
        Industry i = m.getIndustry(Industries.MEGAPORT);
        if (i == null) i = m.getIndustry(Industries.SPACEPORT);
        return i != null && i.isFunctional();
    }

    /** Raises the fleet at the source and sends it to defend the target, then home to despawn. Null when no hulls are known. */
    static CampaignFleetAPI spawn(MarketAPI from, MarketAPI target, float fp, String kind) {
        FleetParamsV3 p = new FleetParamsV3(from, from.getLocationInHyperspace(), Factions.PLAYER, null, FleetTypes.PATROL_LARGE,
                fp, 0f, 0f, 0f, 0f, 0f, 0f);
        p.ignoreMarketFleetSizeMult = true;   // the fleet points paid for, whatever the colony's own fleet size
        CampaignFleetAPI f = FleetFactoryV3.createFleet(p);
        if (f == null || f.getFleetData() == null || f.getFleetData().getNumMembers() <= 0) return null;
        f.setName(NAME);
        f.getMemoryWithoutUpdate().set(MEM, kind);
        SectorEntityToken home = from.getPrimaryEntity(), dest = target.getPrimaryEntity();
        home.getContainingLocation().addEntity(f);
        f.setLocation(home.getLocation().x, home.getLocation().y);
        if (home.getContainingLocation() != dest.getContainingLocation())
            f.addAssignment(FleetAssignment.GO_TO_LOCATION, dest, 1000f, "travelling to " + target.getName());
        f.addAssignment(FleetAssignment.DEFEND_LOCATION, dest, CATuning.OWN_FLEET_DUTY_DAYS, "defending " + target.getName());
        f.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, home, 1000f, "returning to " + from.getName());
        return f;
    }

    // ---------------- money ----------------

    static int militaryRung() {
        for (int i = 0; i < CARich.RUNGS.length; i++) if ("Military".equals(CARich.RUNGS[i])) return i;
        return 3;
    }

    /** Who pays (CAWarPolicy.payer), with today's budget, surplus and military rung: CAWar.payerFor's rule. */
    static String payerFor(CASettings s, String payKind, boolean rich, float total) {
        int r = militaryRung(), mode = CARich.ON;
        try { mode = CARich.mode(r); } catch (Throwable ignored) { }
        float rung = 0f;
        if (mode == CARich.ON) try { rung = CARich.allowance(r); } catch (Throwable ignored) { }
        boolean guard = CAWarPolicy.DEFENSE.equals(payKind) || CAWarPolicy.GUARD.equals(payKind);
        boolean poolOk = rich && !(guard && mode != CARich.ON);
        return CAWarPolicy.payer(payKind, poolOk, total, CABuild.credits(), s.reserve, CABudget.available(s), CASurplus.amount(s), rung);
    }

    static void pay(CASettings s, String payer, String marketId, float price) {
        if ("pool".equals(payer) || "rung".equals(payer)) CABudget.payFromPool(s, militaryRung(), marketId, price, 0f);
        else CABudget.pay(s, marketId, price, 0f, 0f);
    }

    // ---------------- launching ----------------

    static final Set<String> warned = new HashSet<String>();

    static float daysSince(CAData d, String key) {
        Long at = d.warOps().get(key);
        if (at == null) return Float.MAX_VALUE;
        float days = Global.getSector().getClock().getElapsedDaysSince(at);
        return days < 0f ? Float.MAX_VALUE : days;
    }

    /** Raises and sends one defense fleet. Returns null when it went, or why it waits. */
    static String send(CAData d, CASettings s, Out o, String kind, String payKind, MarketAPI target, float want, boolean rich,
                       String why, String recordKey) {
        boolean urgent = CAWarPolicy.EMERGENCY.equals(payKind) || CAWarPolicy.RELIEF.equals(kind);
        float fp = size(want, rich, spare(o.cap, o.inUse, urgent, rich));
        if (fp <= 0f) return "fleet points";
        MarketAPI from = source(target);
        if (from == null) return "no spaceport";
        float cost = cost(fp);
        String payer = payerFor(s, payKind, rich, cost);
        if (payer == null) return "credits";
        CampaignFleetAPI f;
        try { f = spawn(from, target, fp, kind); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "defense fleet failed for " + target.getName(), t); return "failed"; }
        if (f == null) return "no known hulls";
        pay(s, payer, target.getId(), cost);
        long now = Global.getSector().getClock().getTimestamp();
        d.warOps().put(OWN + f.getId() + "|" + target.getId() + "|" + from.getId(), now);
        if (recordKey != null) d.warOps().put(recordKey, now);
        o.inUse += fp;
        o.targets.add(target.getId());
        if (target.getStarSystem() != null) o.systems.add(target.getStarSystem().getId());
        CALog.did(target.getName() + ": Sent a " + (int) fp + "-point defense fleet from " + from.getName() + " for "
                + Misc.getDGSCredits(cost) + " (" + why + ").", true);
        return null;
    }

    static void waits(MarketAPI m, String w) {
        if (w != null && warned.add(m.getId() + ":" + w)) CALog.quiet(m.getName() + ": defense fleet waits (" + w + ")");
    }

    // ---------------- daily pass ----------------

    public static void run(List<MarketAPI> managed) {
        CAData d = CAData.get();
        CASettings s = d.settings;
        Out o = tend(d);
        boolean rich = CASurplus.rich(s);
        int[] left = {perDay(rich)};
        if (!s.disableDefenseFleets) emergency(d, s, o, managed, rich, left);
        if (CAWarPolicy.clamp(s.warLevel) < CAWarPolicy.DEFEND) return;
        relief(d, s, o, managed, rich, left);
        proactive(d, s, o, managed, rich, left);
        if (rich) guard(d, s, o, managed, left);
    }

    /** 1. The Auto colony whose system needs defense most, as CADefense picks it. */
    static void emergency(CAData d, CASettings s, Out o, List<MarketAPI> managed, boolean rich, int[] left) {
        MarketAPI target = null; float need = 0f; String why = null;
        for (MarketAPI m : managed) {
            StarSystemAPI sys = m.getStarSystem();
            if (sys == null || underWay(o, m)) continue;
            if (daysSince(d, CAWarPolicy.DEFENSE + ":" + sys.getId()) < CATuning.DEFENSE_COOLDOWN_DAYS) continue;
            float hostile = hostileFP(sys), ours = CAItems.defenseFP(m) + CASiege.friendlyFP(sys);
            GenericRaidFGI g = raidGroup(sys);
            float gfp = g == null ? 0f : groupFP(g);
            boolean raid = raidIntel(sys);
            float n = need(gfp, raid, hostile, ours);
            String w = gfp > 0f ? "a " + (int) gfp + "-point raid group is coming for the system"
                    : raid ? "a hostile raid is targeting the system"
                    : (int) hostile + " fleet points of hostiles in the system against about " + (int) ours + " of defense";
            if (n > need) { need = n; target = m; why = w; }
        }
        if (target == null || left[0] <= 0) return;
        String w = send(d, s, o, CAWarPolicy.DEFENSE, CAWarPolicy.EMERGENCY, target, Math.max(need, MIN_FP), rich, why,
                CAWarPolicy.DEFENSE + ":" + target.getStarSystem().getId());
        if (w == null) left[0]--;
        else waits(target, w);
    }

    /** 2. A colony under siege gets a defense fleet. */
    static void relief(CAData d, CASettings s, Out o, List<MarketAPI> managed, boolean rich, int[] left) {
        for (MarketAPI m : managed) {
            if (left[0] <= 0) return;
            String siege = CASiege.why(m);
            if (siege == null || m.getStarSystem() == null || underWay(o, m)) continue;
            String key = CAWarPolicy.RELIEF + ":" + m.getId();
            if (daysSince(d, key) < CATuning.WAR_RELIEF_DAYS) continue;
            StarSystemAPI sys = m.getStarSystem();
            float want = Math.max(MIN_FP * 1.5f, hostileFP(sys) * 1.25f - CAItems.defenseFP(m) - CASiege.friendlyFP(sys));
            String w = send(d, s, o, CAWarPolicy.RELIEF, CAWarPolicy.EMERGENCY, m, want, rich, "under siege: " + siege, key);
            if (w == null) left[0]--;
            else waits(m, w);
        }
    }

    /** 3. Threat high or a crisis coming: a defense fleet before anything arrives. */
    static void proactive(CAData d, CASettings s, Out o, List<MarketAPI> managed, boolean rich, int[] left) {
        Set<MarketAPI> crisis = CACrisis.targets();
        float want = Math.max(MIN_FP * 1.5f, 0.2f * o.cap);
        for (MarketAPI m : managed) {
            if (left[0] <= 0) return;
            StarSystemAPI sys = m.getStarSystem();
            if (sys == null || !(d.colony(m.getId()).threatHigh || crisis.contains(m)) || underWay(o, m)) continue;
            boolean real = realThreat(m);
            String key = CAWarPolicy.GUARD + ":" + sys.getId();
            if (!real && daysSince(d, key) < CAWarPolicy.DEFENSE_COOLDOWN_DAYS) continue;
            String why = real ? "hostiles at the system" : crisis.contains(m) ? "crisis coming" : "threat high";
            String w = send(d, s, o, CAWarPolicy.DEFENSE, real ? CAWarPolicy.EMERGENCY : CAWarPolicy.GUARD, m, want, rich, why, key);
            if (w == null) left[0]--;
            else waits(m, w);
        }
    }

    /** 4. Spending freely: a defense fleet at every colony, biggest first. */
    static void guard(CAData d, CASettings s, Out o, List<MarketAPI> managed, int[] left) {
        List<MarketAPI> byBig = new ArrayList<MarketAPI>(managed);
        Collections.sort(byBig, new Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) { return b.getSize() - a.getSize(); }
        });
        float want = Math.max(MIN_FP * 1.5f, 0.2f * o.cap), due = 0f;
        for (MarketAPI m : byBig) {
            if (left[0] <= 0) break;
            if (m.getStarSystem() == null || underWay(o, m)) continue;
            String key = CAWarPolicy.GUARD + ":" + m.getStarSystem().getId();
            if (daysSince(d, key) < CAWarPolicy.DEFENSE_COOLDOWN_DAYS) continue;
            String w = send(d, s, o, CAWarPolicy.GUARD, CAWarPolicy.GUARD, m, want, true, "standing guard", key);
            if (w == null) { left[0]--; continue; }
            if ("credits".equals(w)) due = cost(size(want, true, spare(o.cap, o.inUse, false, true)));
            break;   // out of fleet points or money: the rest wait too
        }
        CARich.wantFleets(due);
    }

    /** CAReset: the session's warnings. */
    public static void reset() { warned.clear(); }
}
