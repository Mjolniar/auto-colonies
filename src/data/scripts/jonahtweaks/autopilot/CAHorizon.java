package data.scripts.jonahtweaks.autopilot;

/**
 * 0.3.0.0 (WS-B2, research 02 sections 1, 2, 12 and 16): one payback horizon for every build, instead of 0.2.45's mix of a
 * focus-world quarter price, a rich-mode 5% price, a flat +15,000 follow-through for upgrades and 10 credits a construction
 * day.
 *
 * A pick's score is its net present value over H months, as a monthly figure: NPV = worth x (H - days/30) - price - fee
 * (CAValue.npv), score = NPV / H = worth - (price + fee) / H - worth x days / 30 / H. So a build is worthwhile when it pays
 * back its price, the convenience fee and the months its value waits for construction within H. H is the setting
 * (CASettings.horizonMonths, 36 months) and slides up to CATuning.HORIZON_MAX (120) as surplus credits pile up
 * (H = base + (120 - base) x surplus / CATuning.RICH), the same at every colony. Hysteresis: H rises with the surplus at
 * once and falls only when the surplus drops under CATuning.TIER_EXIT (60%) of what holds it up, so it doesn't move with
 * every purchase. Worked out once a game day (scores kept for the day stay consistent); session only, nothing saved.
 * With the hidden legacyPlanner switch it's the 0.2.45 three years.
 */
public final class CAHorizon {
    private CAHorizon() { }

    /** Shortest horizon a setting can ask for (a broken json value can only make Autopilot stricter down to a year). */
    static final float FLOOR = CATuning.HORIZON_FLOOR;   // 0.3.0.0 (B2): the numbers live in CATuning
    /** 0.3.0.0 (B2, 02 section 1): a plan step carries this share of what the plan steps it opens the way to would add (Orbital Works after Heavy Industry). */
    public static final float DOWNSTREAM_SHARE = CATuning.DOWNSTREAM_SHARE;
    /** 0.3.0.0 (B2): an ordinary plan step's tie-break, this share of its own worth, only once it pays for itself (never makes a bad step pay). */
    public static final float PLAN_TIE = CATuning.PLAN_TIE;
    /** 0.3.0.0 (B2, 02 section 1): days a plan step scores nothing before it stops taking plan rank and holding an industry slot. */
    public static final float WORTHLESS_DAYS = CATuning.WORTHLESS_DAYS;

    /** Today's horizon (NaN: not worked out yet), the base it slid from, and the day it was worked out. */
    static float held = Float.NaN, heldBase = Float.NaN;
    static int heldDay = Integer.MIN_VALUE;

    /** On load and on a rescan (CAEngine.resetCaches): the next ask works it out from the surplus as it is. */
    static void reset() { held = Float.NaN; heldBase = Float.NaN; heldDay = Integer.MIN_VALUE; }

    /**
     * The empire's horizon in months today: the setting, slid up by surplus credits. 36 offline or with no save, and the
     * 0.2.45 three years with legacyPlanner.
     */
    public static float months() {
        CASettings s;
        try { s = CAData.settings(); } catch (Throwable t) { return CATuning.HORIZON_MIN; }
        if (s == null) return CATuning.HORIZON_MIN;
        if (s.legacyPlanner) return CATuning.PAYBACK_MONTHS;
        float b = base(s.horizonMonths);
        int day = CATime.today();
        if (day == heldDay && b == heldBase && !Float.isNaN(held)) return held;
        float surplus;
        try { surplus = CARich.horizonCredits(s); } catch (Throwable t) { surplus = 0f; }   // 0.3.0.0 (C2): at Idle money and up the horizon slides on free cash (WS-C T2: longer horizon at every Auto colony)
        float was = b == heldBase ? held : Float.NaN;
        float h = slide(b, surplus, was);
        if (Float.isNaN(was) ? h > b : Math.abs(h - was) >= 1f) {
            try {
                CADiagnostics.LOG.info(CADiagnostics.TAG + "payback horizon " + Math.round(h) + " months (" + Math.round(b) + " normally, "
                        + CAEngine.credits(surplus) + " surplus)");
            } catch (Throwable ignored) { }
        }
        held = h; heldBase = b; heldDay = day;
        return h;
    }

    /** The setting as a horizon: 0 (an older save before fixNulls) is the default, anything else kept between FLOOR and HORIZON_MAX. */
    static float base(int setting) {
        if (setting <= 0) return CATuning.HORIZON_MIN;
        return Math.max(FLOOR, Math.min(CATuning.HORIZON_MAX, setting));
    }

    /** Pure: the horizon a surplus of this size asks for: base at none, HORIZON_MAX from CATuning.RICH up, in a straight line between. */
    static float raw(float base, float surplus) {
        float f = surplus > 0f ? Math.min(1f, surplus / CATuning.RICH) : 0f;
        return base + Math.max(0f, CATuning.HORIZON_MAX - base) * f;
    }

    /**
     * Pure, with hysteresis: up at once to what the surplus asks for; down only to what a surplus of surplus / TIER_EXIT would
     * ask for, so the held value stands until the surplus falls under 60% of what holds it. held NaN: nothing held yet.
     */
    static float slide(float base, float surplus, float held) {
        float up = raw(base, surplus);
        if (Float.isNaN(held)) return up;
        float down = raw(base, surplus / CATuning.TIER_EXIT);
        return Math.max(up, Math.min(held, down));
    }

    /**
     * Pure: the price part of a score, a month: price and fee over the horizon, plus the worth lost while it's built
     * (worth x days / 30 / H; a loss-maker doesn't get cheaper by taking longer, so only a positive worth counts here).
     */
    static float price(float cost, float fee, float worth, float days, float h) {
        float hh = Math.max(1f, h);
        return (Math.max(0f, cost) + Math.max(0f, fee)) / hh + Math.max(0f, worth) * Math.max(0f, days) / 30f / hh;
    }

    /** Pure: NPV over the horizon, a month (CAValue.npv / H for a positive worth). */
    static float npvMonthly(float worth, float cost, float fee, float days, float h) { return worth - price(cost, fee, worth, days, h); }

    /** For the panels (WS-F): what the horizon is today and why, in one short sentence. */
    public static String text() {
        float h = months(), b;
        try { b = base(CAData.settings().horizonMonths); } catch (Throwable t) { b = CATuning.HORIZON_MIN; }
        return h > b + 0.5f ? "Builds must pay back within " + Math.round(h) + " months (" + Math.round(b) + " normally; surplus credits stretch it)"
                : "Builds must pay back within " + Math.round(h) + " months";
    }
}
