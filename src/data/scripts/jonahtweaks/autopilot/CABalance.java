package data.scripts.jonahtweaks.autopilot;

/**
 * 0.3.14.0: Balance, how expensive the Autopilot is. Hard is every price as it was before 0.3.14.0 (and what every older
 * save keeps). Normal and Easy lower the story mode bandwidth prices (packages, managed colonies, Autarch seats) and the
 * core-heavy mod surcharge on them, make each fed AI core give more bandwidth, and set Convenience fees to Low.
 * CASettings.balance holds it: 0 Hard, 1 Normal, 2 Easy (0 is what a save from before the field reads).
 */
public final class CABalance {
    private CABalance() { }

    public static final int HARD = 0, NORMAL = 1, EASY = 2;
    /** Menu order (Easy first) and the CASettings.balance value each name stands for. */
    public static final String[] NAMES = {"Easy", "Normal", "Hard"};
    static final int[] VALUE = {EASY, NORMAL, HARD};
    /** By CASettings.balance (Hard, Normal, Easy): bandwidth prices, the core-heavy mod surcharge, fed core bandwidth. */
    static final float[] PRICE = {1f, 0.75f, 0.5f}, SURCHARGE = {1f, 0.5f, 0f}, FEED = {1f, 1.25f, 1.5f};
    /** The Convenience fee level each balance sets (CASettings.FEE_NAMES): Standard, Low, Low. */
    static final int[] FEE = {2, 1, 1};
    /** One sentence each, in menu order, for the new-game pop-up. */
    public static final String[] DESC = {
            "On Easy, story upgrades cost half the bandwidth, fed AI cores count for half again as much, and convenience fees are low.",
            "On Normal, story upgrades cost three quarters of the bandwidth, fed AI cores count for a quarter more, and convenience fees are low.",
            "On Hard, every price is as the mod was balanced, with full bandwidth costs and standard convenience fees."};

    public static int of(CASettings s) { return s == null ? HARD : Math.max(HARD, Math.min(EASY, s.balance)); }

    static int now() {
        try { return of(CAData.settings()); } catch (Throwable t) { return HARD; }   // offline checks: Hard
    }

    /** Multiplier on story bandwidth prices. */
    public static float price() { return PRICE[now()]; }

    /** Share of the core-heavy mod surcharge that applies. */
    public static float surcharge() { return SURCHARGE[now()]; }

    /** Multiplier on the bandwidth a fed AI core gives. */
    public static float feed() { return FEED[now()]; }

    public static String name(CASettings s) {
        int v = of(s);
        for (int i = 0; i < VALUE.length; i++) if (VALUE[i] == v) return NAMES[i];
        return NAMES[2];
    }

    /** Menu index (Easy 0, Normal 1, Hard 2) of a name, or -1. */
    public static int index(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
        return -1;
    }

    /** Sets the balance by menu index and the fee level that goes with it. */
    public static void set(CASettings s, int menuIndex) {
        if (s == null || menuIndex < 0 || menuIndex >= VALUE.length) return;
        s.balance = VALUE[menuIndex];
        s.feeLevel = FEE[s.balance];
    }

    /** The Settings page's click: Easy, Normal, Hard, then Easy again. */
    public static void cycle(CASettings s) { set(s, (index(name(s)) + 1) % NAMES.length); }
}
