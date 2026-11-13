package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 0.3.0.0 (WS-B3, research 02 "Construction 0.3.0" model): one thing Autopilot could do this pass, offered by any module into
 * the planner's one queue (CAPlanner): a build, upgrade, TME project, research facility, wonder site, stable-location crew
 * or story-point improvement (core and item moves, stockpiles, ship orders, expeditions and war operations are kinds for the
 * workstreams that add them).
 *
 * What it's worth and costs: worth a month, the price and fee it pays now, construction days, upkeep it adds, and npv, its
 * net present value over the horizon in credits (CAValue.npv; worth starting when above 0). What it ties up: the colony's
 * one new-building queue (usesQueue), a specific building (an upgrade's, a TME structure's project), an industry slot,
 * stability room, story points, a one-per-empire flag (once). Where its money comes from: the month's budget with savings
 * (fromBudget), surplus credits over the surplus line (fromSurplus), and whether its whole price counts against the month's
 * budget (chargeAll; 0.2.45.1's surplus allocators counted only their fee).
 *
 * Nothing here is saved: an action lives for one pass, or in a colony's offer kept until its next look (CAPlanner.offers).
 */
public class CAAction {
    public enum Kind { BUILD, UPGRADE, PROJECT, CREW, STORY, CORE, ITEM, STOCKPILE, SHIPS, EXPEDITION, WAR }
    public Kind kind;
    public MarketAPI market;          // null for empire-wide actions
    public String id, name, why;
    public float worth, cost, fee, days, upkeep, npv;
    public boolean usesQueue, usesSlot, enabler;
    public float stabilityDelta;
    public int storyPoints;
    /** Runs it through the module's own paid path (CABuild.start etc.); null on success, else the reason. */
    public interface Starter { String start(CAAction a); }
    public Starter starter;

    // ---------------- 0.3.0.0 (B3): what the planner needs on top of the scaffold ----------------

    /** Re-scores an action whose numbers may be stale (something started that shares its colony, a good or a flag); false drops it. */
    public interface Rescore { boolean rescore(CAAction a); }
    /** What may follow a start at the same place this pass (surplus terraforming's next free structure while spending freely). */
    public interface FollowUp { List<CAAction> next(CAAction started); }

    /** The colony's id (market is null in the offline checks); null for empire-wide actions. */
    public String marketId;
    /** Which module offered it: "build" (the engine's picks), "surplus terraforming", "surplus research", "wonders", "stable locations", "story points". */
    public String source = SOURCE_BUILD;
    public static final String SOURCE_BUILD = "build";
    /** The specific building it ties up (an upgrade's, a TME structure's project): never two on one building. */
    public String building;
    /** A one-per-empire flag: only one action with it starts a pass (two Research Facilities the same day, 02 section 14). */
    public String once;
    /** Goods it makes: another colony's action making any of them is re-scored after this one starts (pending-aware deficits). */
    public Set<String> goods = new LinkedHashSet<String>();
    /** Money a month before upkeep, and how sure: the upkeep cap counts only upkeep it doesn't cover itself (02 section 3). */
    public float gross, confidence = 1f;
    /** The colony's new-upkeep cap this month (CASurplus.upkeepCap); unused for actions that add none. */
    public float upkeepCap;
    /** Credits the NPV is per, for ranking by NPV per credit; 0: price and fee. Surplus terraforming's covers its whole sequence. */
    public float basis;
    /** Money it may draw on: the month's budget with savings, and surplus credits over the line. */
    public boolean fromBudget = true, fromSurplus = false;
    /** Its whole price counts against the month's budget (builds); false: only the fee does (0.2.45.1's surplus allocators). */
    public boolean chargeAll = true;
    /**
     * Started whatever its NPV by a rule you set (surplus terraforming's mode, spending freely's wonders, the story point rule,
     * the endgame's research): ranked after everything that pays, never the pick money is saved up for.
     */
    public boolean byRule;
    /** Why it needs your approval at an Auto colony (over the per-project cap, a measured mod building), or null. */
    public String approval;
    /** An Approve colony's action: proposed, never started. */
    public boolean approveOnly;
    /** Days a removal's mark still blocks building it here again (CATombstone), 0 when none. */
    public float tombstone;
    /**
     * 0.3.0.0 (B3 fix): days left of your rejection of it at its colony (CAAuto.reject), 0 when none: it isn't proposed or
     * started again till then. A colony event's fresh look proposed it again within days (0.2.45.1 held it 30).
     */
    public float rejected;
    /**
     * 0.3.0.0 (B3 fix): a defense at a colony under siege. As in 0.2.45.1 it never waits for the money saved up for another pick
     * or the upkeep room kept for one; it still keeps the reserve, the real new-upkeep cap and stability.
     */
    public boolean emergency;
    /**
     * 0.3.0.0 (B3 fix): its starter failed for now (credits, stability, the endgame's income floor), not for good: a better new
     * build that failed this way still keeps long builds out of its colony's queue (DECISIONS 19). Set by the starter.
     */
    public boolean failWaits;
    /**
     * 0.3.0.0 (B3 fix): set by the pass: it waits for money it can't save up in six months (or with no budget coming), so it
     * has no start date and doesn't keep its colony's last industry slot or stability room (0.2.45.1 built the next pick then).
     */
    public boolean openWait;
    /** Scored in today's look at its colony (not a kept offer): only fresh actions are proposed for approval. */
    public boolean fresh = true;
    /** The module's own data (a CAEngine.Pick, a TME sequence, a stable location plan). Session only. */
    public Object payload;
    public Rescore rescore;
    public FollowUp followUp;
    /** How many starts at the same place led to it this pass (a follow-up's is its starter's plus one). */
    public int round;
    /**
     * 0.3.2.0 (M2): what it adds to the open production floors a month (CAEngine.Pick.floorValue), and whether the planner ranks
     * it first for that (CASettings.needsFirst): ahead of other actions by floor value per credit, at the head of its colony's
     * queue, keeping its industry slot against other actions, and taking the earmark from a non-floor pick.
     */
    public float floor;
    public boolean floorFirst;

    /** 0.3.2.0: floor value per credit, the order floor actions go in. */
    public float floorPerCredit() { float r = floor / basis(); return finite(r) ? r : 0f; }

    // ---------------- the pass's own marks (CAPlanner.execute) ----------------

    /** Something started since it was scored that shares its colony, a good or a flag: re-scored before it can start. */
    public boolean stale;
    /** Started this pass; dropped for the pass (gone, flag taken, failed); passed over (waits, with reason). */
    public boolean started, dropped, passed;
    /** Why it didn't start, in words for the log, and its kind (one of the constants below) for the checks. */
    public String reason, reasonKind;
    public static final String MONEY = "money", UPKEEP = "upkeep", QUEUE = "queue", HELD = "held", APPROVAL = "approval", SLOT = "slot",
            STABILITY = "stability", ONCE = "once", BUILDING = "building", TOMBSTONE = "tombstone", WORTHLESS = "worthless", FAILED = "failed",
            STORY = "story", RESCORE = "rescore", GATE = "gate", PROPOSED = "proposed", REJECTED = "rejected";

    /** Price and fee: what it pays now. */
    public float total() { return Math.max(0f, cost) + Math.max(0f, fee); }

    /** Credits the NPV is per. */
    public float basis() { return basis > 0f ? basis : Math.max(1f, total()); }

    /**
     * 0.3.0.9: the NPV the planner ranks and decides by: a NaN or infinite NPV (a scoring that failed) counts as 0, worthless.
     * worthwhile(), perDay(), perCredit(), CAPlanner.RANK, QUEUE, ruleOnly and earmarkFor read it.
     */
    public float value() { return finite(npv) ? npv : 0f; }

    /** Pure (0.3.0.9, JTPlanRankCheck): neither NaN nor infinite. */
    public static boolean finite(float v) { return !Float.isNaN(v) && !Float.isInfinite(v); }

    /** NPV per construction day: the order a colony's new builds go through its one queue (Smith's rule, 02 section 5). 0.3.0.9: on value(), 0 if not finite. */
    public float perDay() { float r = value() / Math.max(1f, days); return finite(r) ? r : 0f; }

    /** NPV per credit: the order money goes to across colonies (02 section 5). 0.3.0.9: on value(), 0 if not finite. */
    public float perCredit() { float r = value() / basis(); return finite(r) ? r : 0f; }

    /** Worth starting on its own numbers: a positive NPV, or a rule you set. 0.3.0.9: a NaN NPV is never positive (value()). */
    public boolean worthwhile() { return value() > 0f || byRule; }

    /** Upkeep the new-upkeep cap counts: what the building doesn't cover itself (02 section 3), never below 0. */
    public float netUpkeep() { return CAPlanner.netRisk(upkeep, gross, confidence); }

    /** For the log: "Heavy Industry at Ming Adi". */
    public String label() {
        String where = null;
        try { where = market != null ? market.getName() : marketId; } catch (Throwable t) { where = marketId; }
        return (name != null ? name : id) + (where != null ? " at " + where : "");
    }

    /** The colony's name for a "Place: sentence" log line; "Autopilot" for empire-wide actions. */
    public String place() {
        try { if (market != null) return market.getName(); } catch (Throwable ignored) { }
        return marketId != null ? marketId : "Autopilot";
    }

    /** A stable key for once-logs and the earmark: "marketId:source:id[:building]". */
    public String key() { return marketId + ":" + source + ":" + id + (building != null ? ":" + building : ""); }

    public String toString() { return kind + " " + key() + " npv " + Math.round(npv) + (reasonKind != null ? " (" + reasonKind + ")" : ""); }
}
