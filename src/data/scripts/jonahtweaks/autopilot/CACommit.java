package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.26.0 (plan 2026-11-25, batch 5, part 1): stalled megastructure restorations. A section that is restoring and gains
 * under STALL_PROGRESS of its restoration in a month counts a stalled month; one with STALL_MONTHS of them (or whose ToT
 * restoration contract is that old with nothing restored) is proposed for pausing (Approvals, kind "stall"). Approving it
 * calls the section's own stopRestoration, which freezes its contract, so its goods go back to your exports; it can be
 * started again from the megastructure's screen. Read at every colony, Advisor ones too: it only asks. Monthly look,
 * daily asks (live asks go after LIVE_DAYS without one). Needs Vaults of Knowledge.
 */
public final class CACommit {
    private CACommit() { }

    static final int STALL_MONTHS = 12;
    /** Progress a month (share of the section) under which a month counts as stalled. */
    static final float STALL_PROGRESS = 0.01f;
    /** ToT's restoration contracts start at this many months (BaseRestorationContract), so their age is this less what's left. */
    static final int CONTRACT_MONTHS = 9999;
    static final String P = "stallp:", M = "stallm:";

    static Object sStop;
    static boolean linked = false, broken = false;
    static int lookedMonth = Integer.MIN_VALUE;
    /** Sections to ask about today, "marketId|sectionId" -> the ask's words. */
    static final Map<String, String[]> ASKS = new LinkedHashMap<String, String[]>();

    static void reset() { sStop = null; linked = false; broken = false; lookedMonth = Integer.MIN_VALUE; ASKS.clear(); }

    // ---------------- pure ----------------

    /** Pure: the stalled-month count after a month with this much progress gained. */
    static int monthsAfter(int months, float gained) { return gained < STALL_PROGRESS ? Math.max(0, months) + 1 : 0; }

    /** Pure: a section is stalled after STALL_MONTHS stalled months, or with under STALL_PROGRESS done on a contract at least that old. */
    static boolean stalled(int months, float progress, int contractMonths) {
        return months >= STALL_MONTHS || (progress < STALL_PROGRESS && contractMonths >= STALL_MONTHS);
    }

    // ---------------- the game ----------------

    static boolean link() {
        if (linked || broken) return linked;
        try {
            if (!CAHypershunt.link()) { broken = true; return false; }
            Class<?> sec = Class.forName(CAHypershunt.PKG + "campaign.econ.megastructures.models.BaseMegastructureSection", true, Global.getSettings().getScriptClassLoader());
            sStop = CARefl.method(sec, "stopRestoration");
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Vaults of Knowledge's restorations; stalled ones aren't flagged", t);
        }
        return linked;
    }

    /** A restoring section with its colony and megastructure name. */
    static final class Restoring { MarketAPI m; String mega; CAHypershunt.Section s; }

    static List<Restoring> restoring() {
        List<Restoring> out = new ArrayList<Restoring>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            try {
                if (CAHypershunt.is(m)) {
                    CAHypershunt.Shunt h = CAHypershunt.read(m);
                    if (h != null) for (CAHypershunt.Section s : new CAHypershunt.Section[]{h.collector, h.wormhole}) add(out, m, "Coronal Hypershunt", s);
                }
                if (m.hasIndustry("pluto_station") || m.hasIndustry("nidavelir_complex")) {
                    CAMegastructures.Mega g = CAMegastructures.read(m);
                    if (g != null) for (CAHypershunt.Section s : g.sections.values()) add(out, m, g.name, s);
                }
            } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "restoration read failed at " + m.getName(), t); }
        }
        return out;
    }

    static void add(List<Restoring> out, MarketAPI m, String mega, CAHypershunt.Section s) {
        if (s == null || !s.restoring || s.restored) return;
        Restoring r = new Restoring(); r.m = m; r.mega = mega; r.s = s;
        out.add(r);
    }

    /** Months ToT's restoration contract for this section has run (its id ends with the section id), or 0. */
    @SuppressWarnings("unchecked")
    static int contractMonths(String sectionId) {
        try {
            if (!CAContracts.link()) return 0;
            for (Object c : ((Map<String, Object>) CARefl.invoke(CAContracts.activeMap, CAContracts.mgr())).values()) {
                String id = String.valueOf(CARefl.invoke(CAContracts.cId, c));
                if (!id.endsWith(sectionId) || !CANeeds.RESTORATION.equals(CANeeds.kind(c.getClass().getSimpleName(), true))) continue;
                int left = ((Number) CARefl.invoke(CAContracts.cMonths, c)).intValue();
                return Math.max(0, CONTRACT_MONTHS - left);
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    /** Daily (after the megastructure step): the monthly look, then the asks. */
    public static void run() {
        if (CALog.whatIf() || !Global.getSettings().getModManager().isModEnabled(CAHypershunt.MOD) || !link()) return;
        int month = Global.getSector().getClock().getMonth();
        if (month != lookedMonth) {
            lookedMonth = month;
            ASKS.clear();
            for (Restoring r : restoring()) {
                String key = r.m.getId() + "|" + r.s.id;
                Long was = CAMark.at(P + key), n = CAMark.at(M + key);
                float gained = was == null ? 1f : r.s.progress - was / 1000000f;
                int months = was == null ? 0 : monthsAfter(n == null ? 0 : n.intValue(), gained);
                CAMark.set(P + key, Math.round(r.s.progress * 1000000f));
                CAMark.set(M + key, months);
                int age = contractMonths(r.s.id);
                if (!stalled(months, r.s.progress, age)) continue;
                int stuck = Math.max(months, age);
                String goods = goods(r.s.monthly);
                ASKS.put(key, new String[]{r.m.getName(), r.mega, r.s.name, String.valueOf(stuck), goods});
                CADiagnostics.LOG.info(CADiagnostics.TAG + r.m.getName() + ": " + r.mega + " " + r.s.name + " restoration stalled: " + Math.round(r.s.progress * 100f)
                        + "% done, " + stuck + " months without progress, asks " + goods + " a month");
            }
        }
        for (Map.Entry<String, String[]> e : ASKS.entrySet()) offer(e.getKey(), e.getValue());
    }

    static String goods(Map<String, Integer> monthly) {
        List<String> l = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : monthly.entrySet()) l.add(Misc.getWithDGS(e.getValue()) + " " + CANeeds.name(e.getKey()));
        return l.isEmpty() ? "nothing" : CAContracts.join(l);
    }

    static void offer(final String key, final String[] w) {
        final String marketId = key.substring(0, key.indexOf('|')), sectionId = key.substring(key.indexOf('|') + 1);
        MarketAPI m = Global.getSector().getEconomy().getMarket(marketId);
        if (m == null) return;
        CAApprovals.offer("stall:" + key, "stall", m, "Pause the " + w[2] + " restoration", 0f, 0f,
                "The " + w[1] + "'s " + w[2] + " hasn't moved in " + w[3] + " months, and it asks for " + w[4] + " a month, more than your colonies have spare.",
                () -> pause(marketId, sectionId),
                "Pausing it stops its contract, so those goods go back to your exports and other uses.",
                "You can start it again from the megastructure's screen any time.");
    }

    /** The approval: the section's own stopRestoration. */
    static String pause(String marketId, String sectionId) {
        try {
            MarketAPI m = Global.getSector().getEconomy().getMarket(marketId);
            if (m == null) return "That colony is gone";
            for (Restoring r : restoring()) {
                if (r.m != m || !r.s.id.equals(sectionId)) continue;
                CARefl.invoke(sStop, r.s.raw);
                ASKS.remove(marketId + "|" + sectionId);
                CAMark.clear(M + marketId + "|" + sectionId);
                CALog.did(m.getName() + ": " + r.mega + " " + r.s.name + " restoration paused.");
                return null;
            }
            return "It isn't restoring any more";
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "pausing a restoration failed at " + marketId, t);
            return "That failed; see starsector.log";
        }
    }
}
