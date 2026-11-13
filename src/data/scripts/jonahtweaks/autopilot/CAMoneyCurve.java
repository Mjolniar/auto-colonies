package data.scripts.jonahtweaks.autopilot;

/**
 * 0.3.2.0 (production priorities M1): the money taper. Pure, checked by JTValueCheck.
 * - Weight: 1 a credit up to `low` of net income a month, wMid from low to high, wTop above high.
 * - marginal(money, net, ...): the credits a build's money counts, the weight integrated from net to net + money. A loss runs
 *   the same curve downward from net.
 * - Off (no curve): identity.
 * - CAValue.add and CAValue.keepDetail call it on the money term, so build and keep match. The tier cut (CARich.IDLE_MONEY,
 *   CASurplus.RICH_MONEY) multiplies on top; the endgame keeps its 0.05 and no taper.
 */
public final class CAMoneyCurve {
    private CAMoneyCurve() { }

    /** The weight of one credit a month at this net income. */
    static float weight(float at, float low, float high, float wMid, float wTop) {
        if (at < low) return 1f;
        if (at < high) return wMid;
        return wTop;
    }

    /** Credits of income from a to b (a <= b) on the curve. */
    static float area(float a, float b, float low, float high, float wMid, float wTop) {
        if (!(b > a)) return 0f;
        float out = 0f;
        out += Math.max(0f, Math.min(b, low) - a);                                   // weight 1 below low
        out += wMid * Math.max(0f, Math.min(b, high) - Math.max(a, low));          // wMid from low to high
        out += wTop * Math.max(0f, b - Math.max(a, high));                         // wTop above high
        return out;
    }

    /**
     * Pure: what `money` credits a month count at a net income of `net`, on the curve. Positive money counts the area from
     * net to net + money; a loss counts minus the area from net + money to net. A curve with bad lines (low not above 0, high
     * not above low) or NaN numbers returns money unchanged.
     */
    public static float marginal(float money, float net, float low, float high, float wMid, float wTop) {
        if (money == 0f || Float.isNaN(money) || Float.isNaN(net) || !(low > 0f) || !(high > low)) return money;
        float n = Math.max(0f, net);
        if (money > 0f) return area(n, n + money, low, high, wMid, wTop);
        return -area(n + money, n, low, high, wMid, wTop);
    }

    /** Pure: the taper's lines and weights for a setting (CATuning.TAPER_*), with the Gentle lines the settings hold: {low, high, wMid, wTop}; null for Off. */
    static float[] curve(int mode, float low, float high) {
        if (mode == CATuning.TAPER_GENTLE) return new float[]{low, high, CATuning.TAPER_MID, CATuning.TAPER_TOP};
        if (mode == CATuning.TAPER_STRONG) return new float[]{CATuning.TAPER_STRONG_LOW, CATuning.TAPER_STRONG_HIGH, CATuning.TAPER_STRONG_MID, CATuning.TAPER_STRONG_TOP};
        return null;
    }

    /** Pure: marginal on a curve from curve(); identity for null. */
    public static float apply(float money, float net, float[] c) {
        if (c == null || c.length < 4) return money;
        return marginal(money, net, c[0], c[1], c[2], c[3]);
    }
}
