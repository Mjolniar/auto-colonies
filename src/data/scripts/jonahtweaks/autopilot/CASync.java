package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 0.3.6.0: story mode. The Autarch AI Core and its processing power (PP), and every check the rest of the mod asks.
 *
 * Story mode is a global setting (LunaLib, or the intel Settings page without it). Each save records its own mode (on())
 * and switches to match on load and when LunaLib's menu is saved (setOn). Off: every ability runs except the story perks
 * (0.3.8.9: Rapid construction, two and three builds at once, Rapid terraforming and the Autarch administrator), there is
 * no limit on managed colonies, and the quest and its timers pause. On: the Autopilot is dormant
 * until the core is installed, and runs the abilities bought with PP. Switching keeps the quest; PP is not refunded.
 *
 * State lives in its own persistent-data entry (KEY) as plain maps, lists, numbers and text, never as fields of CAData or
 * CASettings, so a save loads in versions without story mode (which ignore it).
 * - The core: installed (with or without the isolation cradle), pulled out of the cradle, being broken down, sold, or
 *   broken down and waiting to turn up in salvage. Dormant (nothing runs): not installed, or pulled.
 * - PP: BASE_PP once installed, plus every core fed to it. The player ticks abilities up to their PP, and buys managed
 *   colonies past the first.
 * - Settings an unticked ability covers are held at their "off" value (enforce), remembering the value you had, which
 *   comes back when it's ticked again. Abilities with no setting are checked where they run: CAStep (step()),
 *   CAItems, CABuildout, the engine (mayBuild), CABuild and CABudget.
 * - Colony modes: Approve and Auto only within the managed-colony limit, Auto only with Auto mode (allowMode, enforceModes).
 *   A colony held back keeps the mode you wanted, which comes back when there's room.
 */
public class CASync {
    public static final String MOD_ID = "jonah_tweaks";   // 0.3.8.9: PUBLIC_MOD_ID, the same id, is removed (no reader)
    static final String KEY = "ac_story";
    public static final String PREFIX = "ca:sync:";

    // ---------------- state ----------------

    /** JTStoryModeCheck: the state and the clock without a game (null in the game). */
    static Map<String, Object> testState = null;
    static float testDay = -1f;
    /** 0.3.13.1, offline checks only: every tier runs (JTStoryModeCheck tests holds, JTTierLockCheck tests the lock). */
    static boolean testNoTierLock = false;
    static final long DAY_MS = 86400000L;

    @SuppressWarnings("unchecked")
    static Map<String, Object> state() {
        if (testState != null) return testState;
        Map<String, Object> pd;
        if (CARemoval.off) return CARemoval.scratch();   // 0.3.9.4: a save prepared for removal keeps nothing
        try { pd = Global.getSector().getPersistentData(); }
        catch (Throwable t) { return new HashMap<String, Object>(); }   // no game loaded (main menu, offline checks): dormant, nothing kept
        if (pd == null) return new HashMap<String, Object>();
        Object o = pd.get(KEY);
        if (!(o instanceof Map)) { o = new HashMap<String, Object>(); pd.put(KEY, o); }
        return (Map<String, Object>) o;
    }

    static int num(String k) { Object o = state().get(k); return o instanceof Integer ? (Integer) o : 0; }
    static void num(String k, int v) { state().put(k, v); }
    static long stamp(String k) { Object o = state().get(k); return o instanceof Long ? (Long) o : 0L; }
    static void stamp(String k, long v) { state().put(k, v); }
    static boolean flag(String k) { return Boolean.TRUE.equals(state().get(k)); }
    static void flag(String k, boolean v) { state().put(k, v); }
    static String text(String k) { Object o = state().get(k); return o instanceof String ? (String) o : null; }
    static void text(String k, String v) { if (v == null) state().remove(k); else state().put(k, v); }

    @SuppressWarnings("unchecked")
    static List<String> list(String k) {
        Object o = state().get(k);
        if (!(o instanceof List)) { o = new ArrayList<String>(); state().put(k, o); }
        return (List<String>) o;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Integer> prefs() {
        Object o = state().get("prefs");
        if (!(o instanceof Map)) { o = new HashMap<String, Integer>(); state().put("prefs", o); }
        return (Map<String, Integer>) o;
    }

    @SuppressWarnings("unchecked")
    static Map<String, String> wanted() {
        Object o = state().get("wantMode");
        if (!(o instanceof Map)) { o = new HashMap<String, String>(); state().put("wantMode", o); }
        return (Map<String, String>) o;
    }

    static long now() {
        if (testDay >= 0f) return (long) (testDay * DAY_MS);
        try { return Global.getSector().getClock().getTimestamp(); } catch (Throwable t) { return 0L; }
    }

    static float daysSince(long at) {
        if (testDay >= 0f) return testDay - at / (float) DAY_MS;
        try { return Global.getSector().getClock().getElapsedDaysSince(at); } catch (Throwable t) { return 0f; }
    }

    // ---------------- story mode on and off ----------------

    /** This save is in story mode. */
    public static boolean on() { return flag("storyOn"); }

    /** Sets this save's story mode: the setting on load and in LunaLib's menu, the intel row, the new-save pop-up. */
    public static void setOn(boolean want) {
        if (want == on()) return;
        CASettings s = null;
        try { s = CAData.settings(); } catch (Throwable ignored) { }
        if (want) switchOn(s); else switchOff(s);
        if (want) {
            try { startGame(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode start failed", t); }
            CALog.think(dormant() ? "Story mode is on. The Colony Autopilot on your TriPad is dormant."   // 0.3.8.6: your own setting; Thinking list
                    : "Story mode is on. The Autarch AI Core runs the abilities you switched on.");
        } else {
            try { stopGame(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode stop failed", t); }
            CALog.think("Story mode is off. Every Colony Autopilot feature works again. The Autarch AI Core's story picks up where it stopped when you switch story mode back on.");   // 0.3.8.6: Thinking list
        }
    }

    /** On: the paused days come off the core's timer, the core is placed if it never was, and the holds go on. */
    static void switchOn(CASettings s) {
        flag("storyOn", true);
        long off = stamp("offAt");
        if (off != 0L && !phase().isEmpty()) num("phaseShift", num("phaseShift") - Math.round(daysSince(off)));
        stamp("offAt", 0L);
        try { CASyncCatalog.load(); } catch (Throwable ignored) { }
        // 0.3.6.2: on a new game the core waits for the pop-up's answer, so choosing Normal leaves no station behind
        if (!askMode()) try { CAAutarch.ensurePlaced(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core placement failed", t); }
        fit();
        enforce(s);
        try { enforceModes(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode colony modes failed", t); }
    }

    /** Off: every held setting and colony mode comes back; the quest and its timer pause. PP, abilities and cores stay as they are. */
    static void switchOff(CASettings s) {
        release(s);
        try { restoreModes(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode colony modes failed", t); }
        flag("storyOn", false);
        stamp("offAt", now());
    }

    /** The Story tab, the TriPad's watcher and the core's listener. */
    static void startGame() {
        CAStoryIntel.ensure();
        if (!Global.getSector().hasTransientScript(CATriPadWatch.class)) Global.getSector().addTransientScript(new CATriPadWatch());
        com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI lm = Global.getSector().getListenerManager();
        if (!lm.hasListenerOfClass(CAAutarchListener.class)) lm.addListener(new CAAutarchListener(), true);
    }

    static void stopGame() {
        com.fs.starfarer.api.campaign.comm.IntelManagerAPI mgr = Global.getSector().getIntelManager();
        for (com.fs.starfarer.api.campaign.comm.IntelInfoPlugin i : new ArrayList<com.fs.starfarer.api.campaign.comm.IntelInfoPlugin>(mgr.getIntel(CAStoryIntel.class)))
            mgr.removeIntel(i);
        Global.getSector().removeTransientScriptsOfClass(CATriPadWatch.class);
        Global.getSector().getListenerManager().removeListenerOfClass(CAAutarchListener.class);
        queued = null;
    }

    /** Every colony held back gets the mode you set. */
    static void restoreModes() {
        Map<String, String> want = wanted();
        if (want.isEmpty()) return;
        CAData d = CAData.get();
        for (Map.Entry<String, String> e : new ArrayList<Map.Entry<String, String>>(want.entrySet())) {
            if (!d.colonies.containsKey(e.getKey())) continue;
            MarketAPI held = market(e.getKey());   // 0.3.6.2: a colony lost while story mode was on keeps no remembered mode
            if (held == null || !held.isPlayerOwned()) continue;
            CAData.Colony c = d.colony(e.getKey());
            CAData.Mode mo;
            try { mo = CAData.Mode.valueOf(e.getValue()); } catch (Throwable t) { continue; }
            // 0.3.8.5: a colony held at Off gets its mode back too. Only held colonies have an entry; a mode you pick yourself,
            // Off included, removes it (allowMode). A colony you set to Off stays Off
            if (c.mode != mo) { c.mode = mo; d.clearPending(c); }
        }
        want.clear();
    }

    /** A new game: the pop-up asks Normal or Story mode once (CAStoryPrompt). */
    public static boolean askMode() { return flag("askMode"); }
    public static void askMode(boolean v) { flag("askMode", v); }

    // ---------------- the core ----------------

    /** The core went into the TriPad at least once (its PP and abilities are on record from then on). */
    public static boolean installed() { return flag("installed"); }
    public static boolean cradle() { return flag("cradle"); }
    public static boolean pulled() { return flag("pulled"); }
    public static boolean dormant() { return on() && (!installed() || pulled()); }
    /** What the core is doing when it isn't in the TriPad: "" (somewhere in the sector or your cargo), breaking, sold, broken, salvage. */
    public static String phase() { String p = text("phase"); return p == null ? "" : p; }
    static void phase(String p) { text("phase", p == null || p.isEmpty() ? null : p); }
    public static boolean seen(String beat) { return list("seen").contains(beat); }
    static void see(String beat) { if (!seen(beat)) list("seen").add(beat); }
    /** Which core this is: goes up each time a new one is placed, so finding it chimes again. */
    public static int generation() { return num("gen"); }
    // 0.3.8.9: keepCores, keepForFeeding and reserve are removed (feeding is manual since 0.3.7.5; they kept no cores)

    // ---------------- processing power ----------------

    public static int fedPoints() { return num("fedPP"); }
    public static int[] fed() { return new int[]{num("fedG"), num("fedB"), num("fedA")}; }
    /** All PP: the core's own plus every core fed to it; 0 before it's ever installed. */
    public static int total() { return installed() ? CASyncCatalog.BASE_PP + fedPoints() : 0; }
    public static int extraColonies() { return num("colonies"); }
    /** 0.3.8.0: colonies bought for the Autarch administrator past the one its package gives. */
    public static int extraSeats() { return num("seats"); }
    /** 0.3.8.0: colonies the Autarch administrator may govern: one with the package, plus each bought; 0 without it. */
    public static int seats() { return has("autarchAdmin") ? 1 + extraSeats() : 0; }

    // ---------------- 0.3.10.4: core-heavy mods and fed core falloff ----------------

    /** Pure: how much more things cost with n core-heavy mods on: share each, at most max. */
    static float costMult(int n, float share, float max) { return 1f + Math.min(max, share * Math.max(0, n)); }

    /** The core-heavy mods that are on (CASyncCatalog.CORE_MODS); 0 without a game. */
    public static int coreMods() {
        int n = 0;
        try {
            com.fs.starfarer.api.ModManagerAPI mm = Global.getSettings().getModManager();
            for (String id : CASyncCatalog.CORE_MODS) if (id != null && !id.isEmpty() && mm.isModEnabled(id)) n++;
        } catch (Throwable ignored) { }
        return n;
    }

    /** Packages, colonies and seats cost this much more with the core-heavy mods on. */
    public static float costMult() { return costMult(coreMods(), CASyncCatalog.CORE_MOD_SHARE, CASyncCatalog.CORE_MOD_MAX); }

    /** Pure: a price with the mods' share on top, rounded. */
    static int scaled(int base, float mult) { return Math.round(base * mult); }

    /** A package's price now. */
    public static int price(CASyncCatalog.Pack p) { return p == null ? 0 : scaled(p.cost, costMult()); }

    /**
     * What each owned package was bought for. A save from before 0.3.10.4 records its packages at the base price the first
     * time it's read, so nothing it owns costs more.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Integer> packPaid() {
        Object o = state().get("packPaid");
        if (!(o instanceof Map)) { o = new HashMap<String, Integer>(); state().put("packPaid", o); }
        Map<String, Integer> m = (Map<String, Integer>) o;
        if (!flag("packPaidSet")) {
            for (String id : ticked()) {
                CASyncCatalog.Pack p = CASyncCatalog.pack(id);
                if (p != null && !m.containsKey(id)) m.put(id, p.cost);
            }
            flag("packPaidSet", true);
        }
        return m;
    }

    /** The bandwidth an owned package uses: what it was bought for. */
    static int paidFor(CASyncCatalog.Pack p) {
        Integer v = packPaid().get(p.id);
        return v != null ? v : price(p);
    }

    /** Pure: the share of its bandwidth a core gives once `fed` bandwidth is fed. */
    static float falloff(int fed, int[] at, float[] share) {
        float f = 1f;
        for (int i = 0; i < at.length && i < share.length; i++) if (fed >= at[i]) f = share[i];
        return f;
    }

    /**
     * Pure: feeding a Alphas, b Betas and g Gammas (Alphas first) after `fed` bandwidth and `rem` hundredths: {whole
     * bandwidth gained, hundredths left over}. Each core gives its value times the falloff share at the time it's fed.
     */
    static int[] gain(int fed, int rem, int g, int b, int a, int[] at, float[] share) {
        int[] counts = {a, b, g};
        int[] vals = {CASyncCatalog.ALPHA_PP, CASyncCatalog.BETA_PP, CASyncCatalog.GAMMA_PP};
        int total = fed, r = Math.max(0, rem), got = 0;
        for (int k = 0; k < 3; k++) {
            for (int n = 0; n < counts[k]; n++) {
                r += Math.round(vals[k] * 100f * falloff(total, at, share));
                int whole = r / 100;
                r %= 100;
                total += whole;
                got += whole;
            }
        }
        return new int[]{got, r};
    }

    /** The bandwidth these cores would give now. */
    public static int gainNow(int g, int b, int a) {
        return gain(fedPoints(), num("fedRem"), g, b, a, CASyncCatalog.FALLOFF_AT, CASyncCatalog.FALLOFF_SHARE)[0];
    }

    /** The share of its bandwidth the next core fed gives. */
    public static float falloffNow() { return falloff(fedPoints(), CASyncCatalog.FALLOFF_AT, CASyncCatalog.FALLOFF_SHARE); }

    /** The convenience fee's multiplier: STORY_FEE_MULT in story mode, 1 otherwise. */
    public static float feeMult() { return on() ? CASyncCatalog.STORY_FEE_MULT : 1f; }

    /** Pure (0.3.10.3): the price of the next one when `bought` are bought: the first at base, each after it step more. */
    static int stepCost(int base, int step, int bought) { return base + step * Math.max(0, bought); }

    /** 0.3.10.3: the next managed colony's bandwidth. */
    public static int colonyCost() { return scaled(stepCost(CASyncCatalog.COLONY_PP, CASyncCatalog.COLONY_STEP, extraColonies()), costMult()); }   // 0.3.10.4: with the mods

    /** 0.3.10.3: the next Autarch seat's bandwidth. */
    public static int seatCost() { return scaled(stepCost(CASyncCatalog.SEAT_PP, CASyncCatalog.SEAT_STEP, extraSeats()), costMult()); }   // 0.3.10.4: with the mods

    /**
     * 0.3.10.3: the bandwidth the bought colonies and seats use, as paid. A save from before 0.3.10.3 keeps the flat price it
     * paid (the count times the price then), written the first time it's read.
     */
    static int paid(String k, int bought, int flat) {
        if (bought <= 0) return 0;
        if (!state().containsKey(k)) num(k, bought * flat);
        return num(k);
    }

    static int colonyPaid() { return paid("colPaid", extraColonies(), CASyncCatalog.COLONY_PP); }

    static int seatPaid() { return paid("seatPaid", extraSeats(), CASyncCatalog.SEAT_PP); }

    /** Pure (0.3.10.3): what is left paid after the last of `bought` goes: less what it costs now, never under 0, 0 with none left. */
    static int afterDrop(int paid, int bought, int base, int step) {
        return bought - 1 <= 0 ? 0 : Math.max(0, paid - stepCost(base, step, bought - 1));
    }

    /** 0.3.10.3: one bought colony less, with its bandwidth back. */
    static void dropColony() {
        int n = extraColonies();
        if (n <= 0) return;
        int p = colonyPaid();
        num("colonies", n - 1);
        num("colPaid", afterDrop(p, n, CASyncCatalog.COLONY_PP, CASyncCatalog.COLONY_STEP));
    }

    /** 0.3.10.3: one bought Autarch seat less, with its bandwidth back. */
    static void dropSeat() {
        int n = extraSeats();
        if (n <= 0) return;
        int p = seatPaid();
        num("seats", n - 1);
        num("seatPaid", afterDrop(p, n, CASyncCatalog.SEAT_PP, CASyncCatalog.SEAT_STEP));
    }

    public static List<String> ticked() { return list("ticks"); }

    public static boolean ticked(String id) { return ticked().contains(id); }

    /** Bandwidth the bought packages and colonies use (a package none of whose mods is on doesn't count). 0.3.7.4: packages. */
    public static int spent() {
        int n = colonyPaid() + seatPaid();   // 0.3.8.0: Autarch seats; 0.3.10.3: as paid, each costs more than the last
        for (String id : ticked()) {
            CASyncCatalog.Pack p = CASyncCatalog.pack(id);
            if (p != null && p.available()) n += paidFor(p);   // 0.3.10.4: what it was bought for
        }
        return n;
    }

    public static int free() { return total() - spent(); }

    /** Whether an ability runs: the core is awake, it's ticked, its mod is on, and so is what it needs. */
    public static boolean has(String id) {
        if (!on()) return !CASyncCatalog.perk(id);   // story mode off: everything but the story perks
        if (dormant()) return false;
        CASyncCatalog.Ability a = CASyncCatalog.get(id);
        CASyncCatalog.Pack p = CASyncCatalog.packOf(id);   // 0.3.7.4: an ability runs when its package is bought
        if (a == null || p == null || !a.available() || !ticked(p.id)) return false;
        if (p.tier > runningTier()) return false;   // 0.3.13.1: a tier runs only while the tiers below it have their share on
        return a.requires == null || has(a.requires);
    }

    /** Ticks or unticks an ability. Null on success, else why not. */
    public static String toggle(String id) {
        CASyncCatalog.Pack p = CASyncCatalog.pack(id);   // 0.3.7.4: packages are bought, not single abilities
        if (p == null || !p.available()) return "That package isn't available.";
        if (!on()) return "Story mode is off.";
        if (ticked(id)) {
            untick(id);
            afterChange();
            return null;
        }
        if (dormant()) return "The Colony Autopilot is dormant.";
        if (p.tier > openTier()) return CASyncCatalog.TIERS[p.tier] + " isn't open yet.";   // 0.3.7.3
        if (p.tier > runningTier()) return CASyncCatalog.TIERS[p.tier] + " is paused until the tiers below it have enough packages on.";   // 0.3.13.1
        if (p.requires != null && !ticked(p.requires)) return p.name + " needs " + CASyncCatalog.pack(p.requires).name + " first.";
        int cost = price(p);   // 0.3.10.4: with the core-heavy mods
        if (free() < cost) return p.name + " needs " + cost + " bandwidth, and " + Math.max(0, free()) + " are free.";
        packPaid().put(id, cost);
        ticked().add(id);
        afterChange();
        CALog.quiet("Autopilot: " + p.name + " is on (" + cost + " bandwidth).");   // starsector.log; the Story page shows it
        return null;
    }

    /** Unticks a package and every package that needs it. */
    static void untick(String id) {
        if (!ticked().remove(id)) return;
        packPaid().remove(id);   // 0.3.10.4: bought again at the price then
        if ("autarch".equals(id)) { num("seats", 0); num("seatPaid", 0); }   // 0.3.8.0: its bought colonies go with it
        if (MODES.equals(id)) { num("colonies", 0); num("colPaid", 0); }   // 0.3.8.9: Approve mode's bought colonies go with it (their buttons show only with it); 0.3.12.1: Modes
        CASyncCatalog.Pack p = CASyncCatalog.pack(id);
        if (p != null) CALog.quiet("Autopilot: " + p.name + " is off.");
        for (CASyncCatalog.Pack x : CASyncCatalog.PACKS) if (id.equals(x.requires)) untick(x.id);
    }

    public static String addColony() {
        if (!on()) return "Story mode is off.";
        if (dormant()) return "The Colony Autopilot is dormant.";
        if (!has("approve")) return "Modes comes first.";   // 0.3.7.2; 0.3.12.1: the package's name
        if (extraColonies() >= CASyncCatalog.MAX_COLONIES) return "It can't run more colonies.";
        int cost = colonyCost(), paid = colonyPaid();
        if (free() < cost) return "One more managed colony needs " + cost + " bandwidth, and " + Math.max(0, free()) + " are free.";
        num("colonies", extraColonies() + 1);
        num("colPaid", paid + cost);   // 0.3.10.3
        afterChange();
        return null;
    }

    public static String removeColony() {
        if (extraColonies() <= 0) return "There are no bought colonies to remove.";
        dropColony();   // 0.3.10.3: its bandwidth back
        // 0.3.8.9: the Autarch administrator's seats come down to the managed colonies (one plus each bought)
        while (extraSeats() > extraColonies()) dropSeat();
        afterChange();
        return null;
    }

    /** 0.3.8.0: one more colony for the Autarch administrator, up to the managed colonies. */
    public static String addSeat() {
        if (!on()) return "Story mode is off.";
        if (!has("autarchAdmin")) return "Autarch administrator comes first.";
        if (seats() >= managedCap()) return "It governs as many colonies as you have managed ones.";
        if (extraSeats() >= CASyncCatalog.MAX_SEATS) return "It can't govern more colonies.";   // 0.3.10.3
        int cost = seatCost(), paid = seatPaid();
        if (free() < cost) return "One more colony needs " + cost + " bandwidth, and " + Math.max(0, free()) + " are free.";
        num("seats", extraSeats() + 1);
        num("seatPaid", paid + cost);   // 0.3.10.3
        afterChange();
        return null;
    }

    public static String removeSeat() {
        if (extraSeats() <= 0) return "There are no bought colonies to remove.";
        dropSeat();   // 0.3.10.3: its bandwidth back
        afterChange();
        return null;
    }

    /** After any change to the abilities or colonies: settings, modes and the engine's lists follow. */
    static void afterChange() {
        fit();
        updateTiers();   // 0.3.7.3
        enforce(CAData.settings());
        enforceModes();
        try { CAStoryLimit.sync(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story colony limit failed", t); }   // 0.3.10.3
        CAAuto.nextPass.clear();
    }

    /** More used than there is (costs changed in the config file): the last ticked go first, then bought colonies. */
    static void fit() {
        if (!on()) return;
        List<String> t = ticked();
        // 0.3.8.9: the last ticked package that counts; one whose mods are all off costs nothing and stays
        while (spent() > total()) {
            String last = null;
            for (int i = t.size() - 1; i >= 0 && last == null; i--) {
                CASyncCatalog.Pack p = CASyncCatalog.pack(t.get(i));
                if (p != null && p.available()) last = t.get(i);
            }
            if (last == null) break;
            untick(last);
        }
        while (spent() > total() && extraSeats() > 0) dropSeat();   // 0.3.8.0
        while (spent() > total() && extraColonies() > 0) dropColony();
    }

    // ---------------- cores on hand and feeding ----------------

    /** {gamma, beta, alpha}. */
    static final String[] CORES = {CASyncCatalog.GAMMA, CASyncCatalog.BETA, CASyncCatalog.ALPHA};

    static CargoAPI cargo() { return Global.getSector().getPlayerFleet().getCargo(); }

    public static int[] inCargo() {
        int[] n = new int[3];
        try { for (int k = 0; k < 3; k++) n[k] = (int) cargo().getCommodityQuantity(CORES[k]); } catch (Throwable ignored) { }
        return n;
    }

    public static int[] inStorage() {
        int[] n = new int[3];
        try { for (int k = 0; k < 3; k++) n[k] = (int) CAStores.count(CORES[k]); } catch (Throwable ignored) { }
        return n;
    }

    public static int[] onHand() {
        int[] a = inCargo(), b = inStorage();
        return new int[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    /** Feeds cores {gamma, beta, alpha}: from your cargo first, then Autopilot Storage. Null on success. */
    public static String feed(int g, int b, int a) {
        if (dormant()) return "The Autopilot is dormant";
        int[] h = onHand();
        if (h[0] < g || h[1] < b || h[2] < a) return "Not enough cores (you have " + CASyncCatalog.cores(h[0], h[1], h[2]) + ")";
        if (g + b + a <= 0) return "No cores to feed";
        int[] need = {g, b, a};
        for (int k = 0; k < 3; k++) {
            int left = need[k];
            if (left <= 0) continue;
            int c = Math.min(left, (int) cargo().getCommodityQuantity(CORES[k]));
            if (c > 0) { cargo().removeCommodity(CORES[k], c); left -= c; }
            for (CAStores.Store st : CAStores.all()) {
                if (left <= 0) break;
                int s = Math.min(left, (int) st.cargo.getCommodityQuantity(CORES[k]));
                if (s > 0) { st.cargo.removeCommodity(CORES[k], s); left -= s; }
            }
        }
        int[] gr = gain(fedPoints(), num("fedRem"), g, b, a, CASyncCatalog.FALLOFF_AT, CASyncCatalog.FALLOFF_SHARE);   // 0.3.10.4: falloff
        int pts = gr[0];
        num("fedPP", fedPoints() + pts);
        num("fedRem", gr[1]);
        num("fedG", num("fedG") + g); num("fedB", num("fedB") + b); num("fedA", num("fedA") + a);
        CALog.quiet("Autopilot: integrated " + CASyncCatalog.cores(g, b, a) + ", +" + pts + " bandwidth (" + total() + " in all).");   // 0.3.7.4: no thank-you beat (0.3.8.9: the Story page shows the bandwidth)
        return null;
    }

    // ---------------- what the rest of the mod asks ----------------

    /** What a build costs in credits: its price, plus RAPID_FEE with Rapid construction (0.3.8.8: Free construction is gone). */
    public static float buildPrice(float cost) { return rapidBuild() ? cost + CASyncCatalog.RAPID_FEE : cost; }   // 0.3.7.8: Rapid construction's flat fee (110,000 since 0.3.10.4)

    /** 0.3.7.8: Rapid construction (story mode only): a third of the build time, at RAPID_FEE more. Instant construction is gone. */
    public static boolean rapidBuild() { return has("rapidBuild"); }

    /** 0.3.6.7: new buildings a colony may build at once: 1 (the game's rule), 2 or 3 with the story perks. Upgrades always run alongside. */
    public static int buildsAtOnce() { return has("threeBuilds") ? 3 : has("twoBuilds") ? 2 : 1; }

    /** Whether the engine may plan this building: stations need Stations. */
    public static boolean mayBuild(String id) {
        if (has("stations")) return true;
        try {
            IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(id);
            return spec == null || !spec.hasTag(Industries.TAG_STATION);
        } catch (Throwable t) { return true; }
    }

    public static boolean mayBuild(IndustrySpecAPI spec) { return has("stations") || spec == null || !spec.hasTag(Industries.TAG_STATION); }

    /**
     * Daily steps an ability runs, for abilities with no setting to hold; a step not listed always runs (CAStep). Steps whose
     * ability has a setting keep running with it held at off, so they still advise and clean up (the endgame ends, the
     * floors reset, the Floating City fuel check stays on).
     */
    static final Map<String, String> STEPS = new HashMap<String, String>();
    static {
        STEPS.put("diplomacy step", "diplomacy");
        STEPS.put("terraforming planner", "terraform");
        STEPS.put("TASC terraforming step", "terraform");
        STEPS.put("Industrial Evolution automation", "indevo");
        STEPS.put("Courier Port shipping", "indevo");
    }

    /** CAStep: whether a step runs today. */
    public static boolean step(String name) {
        if (!on()) return true;
        String id = STEPS.get(name);
        return id == null || has(id);
    }

    // ---------------- colony modes ----------------

    /** Colonies in Approve or Auto the abilities allow: one with Approve mode, plus each bought; none while dormant. */
    public static int managedCap() { return !on() ? Integer.MAX_VALUE : dormant() || !has("approve") ? 0 : 1 + extraColonies(); }   // 0.3.7.2: Approve mode is bought

    static boolean counts(MarketAPI m) {
        try { return m != null && !CAEngine.isOutpost(m) && !CAHypershunt.is(m); } catch (Throwable t) { return m != null; }
    }

    static boolean managing(CAData.Mode mo) { return mo == CAData.Mode.APPROVE || mo == CAData.Mode.AUTO; }

    /** 0.3.7.9: story mode is on and this colony of yours is managed (Approve or Auto). */
    public static boolean manages(MarketAPI m) {
        try { return on() && m != null && m.isPlayerOwned() && counts(m) && managing(CAData.get().colony(m.getId()).mode); }
        catch (Throwable t) { return false; }
    }

    static String idOf(CAData d, CAData.Colony c) {
        for (Map.Entry<String, CAData.Colony> e : d.colonies.entrySet()) if (e.getValue() == c) return e.getKey();
        return null;
    }

    static MarketAPI market(String id) {
        try { return Global.getSector().getEconomy().getMarket(id); } catch (Throwable t) { return null; }
    }

    /** The last mode change held back, for the colony panel; UI only. */
    public static String modeNote = null;

    /**
     * CAData.setMode: the mode this colony gets. Approve or Auto past the managed-colony limit waits in Advisor; Auto without
     * Auto mode is Approve. What you asked for is remembered and comes back when the abilities allow it.
     */
    public static CAData.Mode allowMode(CAData d, CAData.Colony c, CAData.Mode mode) {
        modeNote = null;
        if (!on()) return mode;
        String id = idOf(d, c);
        MarketAPI m = id == null ? null : market(id);
        if (id == null || !counts(m)) return mode;
        Map<String, String> want = wanted();
        boolean room = true;
        if (managing(mode) && !managing(c.mode)) {
            int n = 0;
            for (MarketAPI x : Misc.getPlayerMarkets(false)) if (x != m && counts(x) && managing(d.colony(x.getId()).mode)) n++;
            room = n < managedCap();
        }
        CAData.Mode got = allowed(mode, has("auto"), has("approve"), has("advisor"), room);
        if (got != mode) {
            want.put(id, mode.name());
            modeNote = m.getName() + " is " + (got == CAData.Mode.OFF ? "Off" : "in " + got.label) + " for now. " + manageWhy(mode);
        } else want.remove(id);
        return got;
    }

    /**
     * 0.3.7.2, pure: the mode a colony gets for the one it wants. Auto without Auto mode is Approve; Approve or Auto without
     * Approve mode, or with no managed colony left (room), is Advisor; Advisor without Advisor mode is Off.
     */
    static CAData.Mode allowed(CAData.Mode wanted, boolean auto, boolean approve, boolean advisor, boolean room) {
        CAData.Mode to = wanted;
        if (to == CAData.Mode.AUTO && !auto) to = CAData.Mode.APPROVE;
        if (managing(to) && (!approve || !room)) to = CAData.Mode.ADVISOR;
        if (to == CAData.Mode.ADVISOR && !advisor) to = CAData.Mode.OFF;
        return to;
    }

    /** Why a colony can't have this mode now. */
    public static String manageWhy(CAData.Mode mode) {
        if (dormant()) return pulled() ? "The Autarch AI Core is out of its cradle." : "The Colony Autopilot needs the Autarch AI Core.";
        if (managing(mode) && !has("approve")) return "It needs " + CASyncCatalog.buy("approve") + " in the Autopilot panel.";
        if (mode == CAData.Mode.AUTO && !has("auto")) return "It needs " + CASyncCatalog.buy("auto") + " in the Autopilot panel.";
        if (mode == CAData.Mode.ADVISOR && !has("advisor")) return "It needs " + CASyncCatalog.buy("advisor") + " in the Autopilot panel.";
        int cap = managedCap();
        return "All " + cap + " managed colonies are in use. One more costs " + colonyCost() + " bandwidth in the Autopilot panel.";
    }

    /**
     * Daily and after every change: each colony gets the mode it wants as far as the bought abilities allow (allowed), and
     * what it wanted comes back when they do. 0.3.7.2: with nothing bought every colony is Off. Colonies already in Approve
     * or Auto keep their managed places first, then the biggest. A colony you set to Off stays Off.
     */
    public static void enforceModes() {
        if (!on()) return;
        final CAData d;
        try { d = CAData.get(); } catch (Throwable t) { return; }
        Map<String, String> want = wanted();
        List<MarketAPI> mine = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (counts(m)) mine.add(m);
        Collections.sort(mine, new Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) {
                boolean ma = managing(d.colony(a.getId()).mode), mb = managing(d.colony(b.getId()).mode);
                if (ma != mb) return ma ? -1 : 1;
                if (a.getSize() != b.getSize()) return b.getSize() - a.getSize();
                return a.getName().compareTo(b.getName());
            }
        });
        int cap = managedCap(), n = 0;
        boolean auto = has("auto"), approve = has("approve"), advisor = has("advisor");
        for (MarketAPI m : mine) {
            String id = m.getId();
            CAData.Colony c = d.colony(id);
            String w = want.get(id);
            CAData.Mode wanted = c.mode;
            if (w != null) { try { wanted = CAData.Mode.valueOf(w); } catch (Throwable t) { want.remove(id); w = null; } }
            if (w == null && c.mode == CAData.Mode.OFF) continue;   // you set it Off
            if (wanted == CAData.Mode.OFF) { want.remove(id); continue; }
            CAData.Mode to = allowed(wanted, auto, approve, advisor, n < cap);
            if (managing(to)) n++;
            if (to == wanted) want.remove(id); else want.put(id, wanted.name());
            if (c.mode == to) continue;
            boolean down = to.ordinal() < c.mode.ordinal();
            c.mode = to;
            d.clearPending(c);
            // 0.3.8.6: with the core out of its cradle (said by the TriPad scene) the line only says Off
            String why = dormant() && pulled() ? "" : " " + manageWhy(wanted);
            CALog.did(m.getName() + (down ? ": " + (to == CAData.Mode.OFF ? "Off" : to.label) + " for now." + why : ": back in " + to.label + "."), true);
        }
    }

    /**
     * 0.3.7.2, once per save: Advisor and Approve mode were free before. A save that had bought abilities gets both, first in
     * the list, so an over-spent core gives up its newest abilities instead (fit). A save with nothing bought gets nothing.
     */
    static void migrateModes() {
        if (flag("modesBought")) return;
        flag("modesBought", true);
        List<String> t = ticked();
        if (t.isEmpty()) return;
        if (!t.contains(MODES)) t.add(0, MODES);   // 0.3.7.4: as packages; 0.3.12.1: the three modes are one package
        fit();
    }

    /** 0.3.12.1: the package Advisor, Approve and Auto mode are in. */
    static final String MODES = "modes";
    /** 0.3.12.1: the three packages it replaced (Advisor, Approve and Auto mode). */
    static final List<String> OLD_MODE_PACKS = Arrays.asList("advice", "oversight", "automation");

    /**
     * 0.3.12.1, once per save, before the other migrations: a save that bought Advisor, Approve or Auto mode has Modes in the
     * place of the first one, paid what those cost together.
     */
    static void migrateModesPack() {
        if (flag("modesPack")) return;
        flag("modesPack", true);
        List<String> t = ticked();
        int at = -1, paid = 0;
        for (int i = t.size() - 1; i >= 0; i--) {
            if (!OLD_MODE_PACKS.contains(t.get(i))) continue;
            Integer p = packPaid().remove(t.get(i));
            if (p != null) paid += p;
            t.remove(i);
            at = i;
        }
        if (at < 0) return;
        if (!t.contains(MODES)) t.add(Math.min(at, t.size()), MODES);
        if (paid > 0) packPaid().put(MODES, paid);
    }

    /** 0.3.7.4, once per save: abilities bought one by one before packages are swapped for the packages that hold them. */
    static void migratePacks() {
        if (flag("packsSet")) return;
        flag("packsSet", true);
        List<String> t = ticked(), out = new ArrayList<String>();
        for (String id : t) {
            CASyncCatalog.Pack p = CASyncCatalog.pack(id) != null ? CASyncCatalog.pack(id) : CASyncCatalog.packOf(id);
            if (p != null && !out.contains(p.id)) out.add(p.id);
        }
        t.clear();
        t.addAll(out);
    }

    // ---------------- 0.3.7.3: tiers ----------------

    /** The highest open tier (0: Tier I only). A tier stays open once it opened. */
    public static int openTier() { return Math.max(0, Math.min(CASyncCatalog.TIERS.length - 1, num("tier"))); }

    /** {owned, needed} in a tier: what you own of its abilities, and what opens the next tier. */
    public static int[] tierProgress(int t) {
        int owned = 0;
        List<CASyncCatalog.Pack> l = CASyncCatalog.inTier(t);
        for (CASyncCatalog.Pack p : l) if (ticked(p.id)) owned++;
        return new int[]{owned, CASyncCatalog.needed(l.size(), CASyncCatalog.TIER_SHARE)};
    }

    /** Opens each next tier whose tier before it has enough owned. */
    static void updateTiers() {
        int last = CASyncCatalog.TIERS.length - 1;
        int[] owned = new int[last + 1], count = new int[last + 1];
        for (int t = 0; t <= last; t++) {
            List<CASyncCatalog.Pack> l = CASyncCatalog.inTier(t);
            count[t] = l.size();
            for (CASyncCatalog.Pack p : l) if (ticked(p.id)) owned[t]++;
        }
        int was = openTier(), now = tierAfter(was, owned, count, CASyncCatalog.TIER_SHARE);
        if (now != was) {
            num("tier", now);
            CALog.did("Autopilot: " + CASyncCatalog.TIERS[now] + " is open.", true);
        }
        // 0.3.13.1: the tiers that run now; a drop pauses the packages above, a rise lets them run again
        int ran = num("running") - 1, runs = tierAfter(0, owned, count, CASyncCatalog.TIER_SHARE);
        if (ran < 0) ran = runs;   // first look in this save: nothing to report
        num("running", runs + 1);
        if (runs < ran) CALog.did("Autopilot: " + CASyncCatalog.TIERS[runs + 1] + " and up are paused until " + CASyncCatalog.TIERS[runs]
                + " has " + CASyncCatalog.needed(count[runs], CASyncCatalog.TIER_SHARE) + " packages on.", true);
        else if (runs > ran) CALog.did("Autopilot: " + CASyncCatalog.TIERS[runs] + " runs again.", true);
    }

    /**
     * 0.3.13.1: the highest tier whose packages run: every tier below it has its share of packages on right now (a tier that
     * opened stays open for display, but its packages pause while a tier below it falls short).
     */
    public static int runningTier() {
        int last = CASyncCatalog.TIERS.length - 1;
        if (testNoTierLock) return last;
        int[] owned = new int[last + 1], count = new int[last + 1];
        for (int t = 0; t <= last; t++) {
            List<CASyncCatalog.Pack> l = CASyncCatalog.inTier(t);
            count[t] = l.size();
            for (CASyncCatalog.Pack p : l) if (ticked(p.id)) owned[t]++;
        }
        return tierAfter(0, owned, count, CASyncCatalog.TIER_SHARE);
    }

    /** Pure: the open tier after opening every tier whose tier before it has `share` of its abilities owned. Never lower. */
    static int tierAfter(int open, int[] owned, int[] count, float share) {
        int t = open;
        while (t + 1 < count.length && owned[t] >= CASyncCatalog.needed(count[t], share)) t++;
        return Math.max(open, t);
    }

    /** 0.3.7.3, once per save: a save from before the tiers opens up to the highest tier it already owns an ability in. */
    static void migrateTiers() {
        if (flag("tiersSet")) return;
        flag("tiersSet", true);
        int top = 0;
        for (String id : ticked()) top = Math.max(top, CASyncCatalog.tierOf(id));
        if (top > openTier()) num("tier", top);
    }

    // ---------------- settings held at "off" while their ability is off ----------------

    /** One held setting: a CASettings field, the ability it needs, the values that need it, and the value it's held at. */
    static final class Rule {
        final String field, ability; final int[] locked; final int off;
        Rule(String field, String ability, int[] locked, int off) { this.field = field; this.ability = ability; this.locked = locked; this.off = off; }
        boolean locks(int v) { for (int x : locked) if (x == v) return true; return false; }
    }

    static int[] v(int... x) { return x; }

    /** In order: a row with several rules (war level, fees) steps down one rule at a time. */
    static final Rule[] RULES = {
            new Rule("useAICores", "cores", v(1), 0),
            new Rule("adminHiring", "admins", v(1), 0),
            new Rule("adminOptimal", "admins", v(1), 0),
            new Rule("noPrune", "cleanup", v(0), 1),
            new Rule("allowRemovals", "cleanup", v(1), 0),
            new Rule("endgameNoReplace", "cleanup", v(0), 1),
            new Rule("capitalNoRoom", "cleanup", v(0), 1),
            new Rule("hazardPayMode", "policies", v(0), 1),
            new Rule("freePortMode", "policies", v(0), 1),
            new Rule("disableEdicts", "policies", v(0), 1),
            new Rule("disableEdicts", "indevo", v(0), 1),
            new Rule("storyPointRule", "storyPoints", v(1, 2), 0),
            new Rule("capitalMode", "capital", v(0, 1), 2),
            new Rule("logisticsMode", "logistics", v(1), 0),
            new Rule("objectiveMode", "objectives", v(0), 1),
            new Rule("surplusLine", "surplus", v(1, 2, 3, 4), 0),
            new Rule("surplusFocusOnly", "surplus", v(0), 1),
            new Rule("productionMode", "floors", v(1, 2), 0),
            new Rule("rung1", "stockpiles", v(1), 2),
            new Rule("rung3", "military", v(1), 2),
            new Rule("rung4", "shipyard", v(1), 2),
            new Rule("rung5", "expansion", v(1), 2),
            new Rule("rung6", "endgame", v(1), 2),
            new Rule("rung7", "wonders", v(1), 2),
            new Rule("endgameMode", "endgame", v(0, 1, 2), 3),
            new Rule("feeLevel", "noFees", v(0), 1),
            new Rule("feeLevel", "halfFees", v(0, 1), 2),
            new Rule("expandMode", "expansion", v(1, 2), 0),
            new Rule("noExpandBuildout", "buildout", v(0), 1),
            new Rule("expandMethod", "colonyFleets", v(0), 1),
            new Rule("stationMode", "terraform", v(1), 0),
            new Rule("lowProfile", "lowProfile", v(1), 0),
            new Rule("disableDefenseFleets", "defense", v(0), 1),
            new Rule("enfOn", "enforcers", v(1), 0),
            new Rule("spyMode", "offense", v(2), 1),
            new Rule("spyMode", "agents", v(1, 2), 0),
            new Rule("autonomyMode", "autonomy", v(2), 1),
            new Rule("warLevel", "scorched", v(5), 4),
            new Rule("warLevel", "conquest", v(4, 5), 3),
            new Rule("warLevel", "strike", v(3, 4, 5), 2),
            new Rule("warLevel", "retaliate", v(2, 3, 4, 5), 1),
            new Rule("warLevel", "defense", v(1, 2, 3, 4, 5), 0),
            new Rule("surplusTerraform", "surplusTerra", v(0, 1), 2),
            new Rule("researchMode", "research", v(1), 0),
            new Rule("projectSupplyMode", "research", v(0), 1),
            new Rule("researchItemMode", "research", v(1, 2), 0),
            new Rule("noSurplusResearch", "surplusResearch", v(0), 1),
            new Rule("hypershuntMode", "mega", v(0), 1),
            new Rule("noWonders", "wonders", v(0), 1),
            new Rule("contractMode", "contracts", v(1), 0),
            new Rule("disableAmbassadors", "indevo", v(0), 1),
            new Rule("disableSpecialties", "indevo", v(0), 1),
            new Rule("disableVPC", "indevo", v(0), 1),
            new Rule("disableForgeVPC", "indevo", v(0), 1),
            new Rule("disableShipping", "indevo", v(0), 1),
            new Rule("augmentMode", "augments", v(0), 1),
            new Rule("settlementMode", "settlement", v(1, 2), 0),
    };

    static int b(boolean x) { return x ? 1 : 0; }

    static int rung(CASettings s, int r) { return s.rungMode != null && r < s.rungMode.length ? s.rungMode[r] : -1; }

    static int get(CASettings s, String k) {
        switch (k) {
            case "useAICores": return b(s.useAICores);
            case "adminHiring": return b(s.adminHiring);
            case "adminOptimal": return b(s.adminOptimal);
            case "noPrune": return b(s.noPrune);
            case "allowRemovals": return b(s.allowRemovals);
            case "endgameNoReplace": return b(s.endgameNoReplace);
            case "capitalNoRoom": return b(s.capitalNoRoom);
            case "hazardPayMode": return s.hazardPayMode;
            case "freePortMode": return s.freePortMode;
            case "disableEdicts": return b(s.disableEdicts);
            case "storyPointRule": return s.storyPointRule;
            case "capitalMode": return s.capitalMode;
            case "logisticsMode": return s.logisticsMode;
            case "objectiveMode": return s.objectiveMode;
            case "surplusLine": return s.surplusLine;
            case "surplusFocusOnly": return b(s.surplusFocusOnly);
            case "productionMode": return s.productionMode;
            case "rung1": return rung(s, 1);
            case "rung3": return rung(s, 3);
            case "rung4": return rung(s, 4);
            case "rung5": return rung(s, 5);
            case "rung6": return rung(s, 6);
            case "rung7": return rung(s, 7);
            case "endgameMode": return s.endgameMode;
            case "feeLevel": return s.feeLevel;
            case "expandMode": return s.expandMode;
            case "noExpandBuildout": return b(s.noExpandBuildout);
            case "expandMethod": return s.expandMethod;
            case "stationMode": return s.stationMode;
            case "lowProfile": return b(s.lowProfile);
            case "disableDefenseFleets": return b(s.disableDefenseFleets);
            case "enfOn": return b(s.enfOn);
            case "spyMode": return s.spyMode;
            case "autonomyMode": return s.autonomyMode;
            case "warLevel": return s.warLevel;
            case "surplusTerraform": return s.surplusTerraform;
            case "researchMode": return s.researchMode;
            case "projectSupplyMode": return s.projectSupplyMode;
            case "researchItemMode": return s.researchItemMode;
            case "noSurplusResearch": return b(s.noSurplusResearch);
            case "hypershuntMode": return s.hypershuntMode;
            case "noWonders": return b(s.noWonders);
            case "contractMode": return s.contractMode;
            case "disableAmbassadors": return b(s.disableAmbassadors);
            case "disableSpecialties": return b(s.disableSpecialties);
            case "disableVPC": return b(s.disableVPC);
            case "disableForgeVPC": return b(s.disableForgeVPC);
            case "disableShipping": return b(s.disableShipping);
            case "augmentMode": return s.augmentMode;
            case "settlementMode": return s.settlementMode;
            default: return -1;
        }
    }

    static void setRung(CASettings s, int r, int v) {
        if (s.rungMode == null || s.rungMode.length <= r) s.fixNulls();
        if (s.rungMode != null && r < s.rungMode.length) s.rungMode[r] = v;
    }

    static void set(CASettings s, String k, int v) {
        switch (k) {
            case "useAICores": s.useAICores = v == 1; break;
            case "adminHiring": s.adminHiring = v == 1; break;
            case "adminOptimal": s.adminOptimal = v == 1; break;
            case "noPrune": s.noPrune = v == 1; break;
            case "allowRemovals": s.allowRemovals = v == 1; break;
            case "endgameNoReplace": s.endgameNoReplace = v == 1; break;
            case "capitalNoRoom": s.capitalNoRoom = v == 1; break;
            case "hazardPayMode": s.hazardPayMode = v; break;
            case "freePortMode": s.freePortMode = v; break;
            case "disableEdicts": s.disableEdicts = v == 1; break;
            case "storyPointRule": s.storyPointRule = v; break;
            case "capitalMode": s.capitalMode = v; break;
            case "logisticsMode": s.logisticsMode = v; break;
            case "objectiveMode": s.objectiveMode = v; break;
            case "surplusLine": s.surplusLine = v; break;
            case "surplusFocusOnly": s.surplusFocusOnly = v == 1; break;
            case "productionMode": s.productionMode = v; break;
            case "rung1": setRung(s, 1, v); break;
            case "rung3": setRung(s, 3, v); break;
            case "rung4": setRung(s, 4, v); break;
            case "rung5": setRung(s, 5, v); break;
            case "rung6": setRung(s, 6, v); break;
            case "rung7": setRung(s, 7, v); break;
            case "endgameMode": s.endgameMode = v; break;
            case "feeLevel": s.feeLevel = v; break;
            case "expandMode": s.expandMode = v; break;
            case "noExpandBuildout": s.noExpandBuildout = v == 1; break;
            case "expandMethod": s.expandMethod = v; break;
            case "stationMode": s.stationMode = v; break;
            case "lowProfile": s.lowProfile = v == 1; break;
            case "disableDefenseFleets": s.disableDefenseFleets = v == 1; break;
            case "enfOn": s.enfOn = v == 1; break;
            case "spyMode": s.spyMode = v; break;
            case "autonomyMode": s.autonomyMode = v; break;
            case "warLevel": s.warLevel = v; break;
            case "surplusTerraform": s.surplusTerraform = v; break;
            case "researchMode": s.researchMode = v; break;
            case "projectSupplyMode": s.projectSupplyMode = v; break;
            case "researchItemMode": s.researchItemMode = v; break;
            case "noSurplusResearch": s.noSurplusResearch = v == 1; break;
            case "hypershuntMode": s.hypershuntMode = v; break;
            case "noWonders": s.noWonders = v == 1; break;
            case "contractMode": s.contractMode = v; break;
            case "disableAmbassadors": s.disableAmbassadors = v == 1; break;
            case "disableSpecialties": s.disableSpecialties = v == 1; break;
            case "disableVPC": s.disableVPC = v == 1; break;
            case "disableForgeVPC": s.disableForgeVPC = v == 1; break;
            case "disableShipping": s.disableShipping = v == 1; break;
            case "augmentMode": s.augmentMode = v; break;
            case "settlementMode": s.settlementMode = v; break;
            default: break;
        }
    }

    /** The Lavish spending preset needs Higher spending; held at Normal. */
    static final int LAVISH = 2, NORMAL = 1;

    /**
     * Holds every setting whose ability is off at its off value, remembering what it was, and puts remembered values back
     * once the ability is on. A value comes back only where the row still sits at the value it was held at, so a row with
     * several rules (war level, fees) steps back up one rule at a time. Runs on load, daily, after every settings change and
     * after every ability change.
     */
    public static void enforce(final CASettings s) {
        if (s == null || !on()) return;
        // the mod's own write: a hold made during a click isn't marked as set by you (CAModeTable)
        CAModeTable.system(s, new Runnable() { public void run() { enforceNow(s); } });
    }

    static void enforceNow(CASettings s) {
        Map<String, Integer> p = prefs();
        for (int pass = 0; pass < 6; pass++) {
            boolean changed = false;
            // values back first, the lowest rule of a row first, so a row with several rules steps up one rule at a time
            for (int i = RULES.length - 1; i >= 0; i--) {
                Rule r = RULES[i];
                String pk = r.field + ":" + r.ability;
                Integer back = p.get(pk);
                if (back == null || !has(r.ability)) continue;
                int cur = get(s, r.field);
                if (cur < 0) continue;
                p.remove(pk);
                if (cur == r.off) { set(s, r.field, back); changed = true; }   // else you changed it since: yours stays
            }
            // then the holds, the highest rule of a row first, so it steps down one rule at a time
            for (Rule r : RULES) {
                if (has(r.ability)) continue;
                int cur = get(s, r.field);
                if (cur < 0 || !r.locks(cur)) continue;
                String pk = r.field + ":" + r.ability;
                if (!p.containsKey(pk)) p.put(pk, cur);
                set(s, r.field, r.off);
                changed = true;
            }
            // the spending preset
            int spend = CAPresets.match(s, CAPresets.SPENDING);
            if (!has("spending") && spend == LAVISH) {
                if (!p.containsKey("spending")) p.put("spending", LAVISH);
                CAPresets.apply(s, CAPresets.SPENDING, NORMAL);
                changed = true;
            } else if (has("spending") && p.containsKey("spending")) {
                Integer back = p.remove("spending");
                if (spend == NORMAL && back != null) { CAPresets.apply(s, CAPresets.SPENDING, back); changed = true; }
            }
            if (!changed) break;
        }
    }

    /**
     * Puts every remembered value back and forgets the holds: story mode off, and LunaLib (CALunaSync), which keeps the values
     * you set so other saves and the menu never see a held value. enforce() puts the holds back on. A row with several rules
     * (war level, fees) remembered your value in its first rule.
     */
    public static void release(final CASettings s) {
        if (s == null) return;
        final Map<String, Integer> p = prefs();
        if (p.isEmpty()) return;
        CAModeTable.system(s, new Runnable() { public void run() {
            java.util.Set<String> done = new java.util.HashSet<String>();
            for (Rule r : RULES) {
                Integer back = p.get(r.field + ":" + r.ability);
                if (back == null || !done.add(r.field)) continue;
                set(s, r.field, back);
            }
            if (p.containsKey("spending")) CAPresets.apply(s, CAPresets.SPENDING, ((Number) p.get("spending")).intValue());
        } });
        p.clear();
    }

    /** Whether a setting now sits on a value an ability that's off holds back. */
    public static boolean anyLocked(CASettings s) {
        for (Rule r : RULES) if (!has(r.ability) && r.locks(get(s, r.field))) return true;
        return !has("spending") && CAPresets.match(s, CAPresets.SPENDING) == LAVISH;
    }

    /** Snapshot of every held field, to see which ones a click changed. */
    public static int[] snapshot(CASettings s) {
        int[] out = new int[RULES.length + 1];
        for (int i = 0; i < RULES.length; i++) out[i] = get(s, RULES[i].field);
        out[RULES.length] = CAPresets.match(s, CAPresets.SPENDING);
        return out;
    }

    /** You changed these fields yourself: forget what they were before they were held. */
    public static void forgetChanged(int[] before, CASettings s) {
        int[] after = snapshot(s);
        Map<String, Integer> p = prefs();
        for (int i = 0; i < RULES.length; i++) if (before[i] != after[i]) p.remove(RULES[i].field + ":" + RULES[i].ability);
        if (before[RULES.length] != after[RULES.length]) p.remove("spending");
    }

    /** Settings rows to the fields they write, for the lock note on each row. */
    static final Map<String, String[]> ROWS = new HashMap<String, String[]>();
    static {
        ROWS.put("cores", new String[]{"useAICores"});
        ROWS.put("admin", new String[]{"adminHiring"});
        ROWS.put("adminopt", new String[]{"adminOptimal"});
        ROWS.put("prune", new String[]{"noPrune"});
        ROWS.put("removals", new String[]{"allowRemovals"});
        ROWS.put("endgamerep", new String[]{"endgameNoReplace"});
        ROWS.put("caproom", new String[]{"capitalNoRoom"});
        ROWS.put("hazard", new String[]{"hazardPayMode"});
        ROWS.put("freeport", new String[]{"freePortMode"});
        ROWS.put("edicts", new String[]{"disableEdicts"});
        ROWS.put("sp", new String[]{"storyPointRule"});
        ROWS.put("capid", new String[]{"capitalMode"});
        ROWS.put("capmode", new String[]{"capitalMode"});
        ROWS.put("logi", new String[]{"logisticsMode"});
        ROWS.put("objectives", new String[]{"objectiveMode"});
        ROWS.put("surplus", new String[]{"surplusLine"});
        ROWS.put("surplusrich", new String[]{"surplusFocusOnly"});
        ROWS.put("endgame", new String[]{"endgameMode"});
        ROWS.put("fees", new String[]{"feeLevel"});
        ROWS.put("expbuildout", new String[]{"noExpandBuildout"});
        ROWS.put("expmethod", new String[]{"expandMethod"});
        ROWS.put("tascst", new String[]{"stationMode"});
        ROWS.put("lowprofile", new String[]{"lowProfile"});
        ROWS.put("defense", new String[]{"disableDefenseFleets"});
        ROWS.put("enf", new String[]{"enfOn"});
        ROWS.put("spy", new String[]{"spyMode"});
        ROWS.put("autonomy", new String[]{"autonomyMode"});
        ROWS.put("war", new String[]{"warLevel"});
        ROWS.put("p_war", new String[]{"warLevel"});
        ROWS.put("surplustf", new String[]{"surplusTerraform"});
        ROWS.put("research", new String[]{"researchMode"});
        ROWS.put("projsupply", new String[]{"projectSupplyMode"});
        ROWS.put("resitems", new String[]{"researchItemMode"});
        ROWS.put("surplusres", new String[]{"noSurplusResearch"});
        ROWS.put("hs", new String[]{"hypershuntMode"});
        ROWS.put("wonders", new String[]{"noWonders"});
        ROWS.put("contract", new String[]{"contractMode"});
        ROWS.put("amb", new String[]{"disableAmbassadors"});
        ROWS.put("spec", new String[]{"disableSpecialties"});
        ROWS.put("vpc", new String[]{"disableVPC"});
        ROWS.put("forge", new String[]{"disableForgeVPC"});
        ROWS.put("ship", new String[]{"disableShipping"});
        ROWS.put("aug", new String[]{"augmentMode"});
        ROWS.put("settle", new String[]{"settlementMode"});
        ROWS.put("p_cores", new String[]{"useAICores"});
        ROWS.put("p_admins", new String[]{"adminHiring", "adminOptimal"});
        ROWS.put("p_takedown", new String[]{"noPrune", "allowRemovals", "endgameNoReplace", "capitalNoRoom"});
        ROWS.put("p_policy", new String[]{"hazardPayMode", "freePortMode", "disableEdicts"});
        ROWS.put("p_story", new String[]{"storyPointRule"});
        ROWS.put("p_newcol", new String[]{"expandMode", "stationMode"});
        ROWS.put("p_prod", new String[]{"productionMode"});
        ROWS.put("p_extras", new String[]{"objectiveMode", "contractMode", "researchMode", "projectSupplyMode", "hypershuntMode", "augmentMode",
                "settlementMode", "disableSpecialties", "disableVPC", "disableShipping"});
        for (int r : new int[]{1, 3, 4, 5, 6, 7}) ROWS.put("rung_" + r, new String[]{"rung" + r});
    }

    /** Rows whose click cycles through values, so a click that lands on a held one moves on (CASetupUI.handle). */
    public static boolean cycles(String key) {
        return key != null && (ROWS.containsKey(key) || key.equals("p_spend") || key.startsWith("p_"));
    }

    /**
     * The note a settings row shows for the abilities that hold some of its values: null when none do. Whole: every value but
     * the off one is held (the row shows the note in place of Change).
     */
    public static String rowLock(String key, CASettings s) {
        if ("p_spend".equals(key)) return has("spending") ? null : "Lavish needs " + CASyncCatalog.buy("spending") + " on the Story page.";
        String[] fields = ROWS.get(key);
        if (fields == null) return null;
        List<String> names = new ArrayList<String>();
        for (String f : fields) for (Rule r : RULES) {
            if (!r.field.equals(f) || has(r.ability)) continue;
            CASyncCatalog.Ability a = CASyncCatalog.get(r.ability);
            if (a == null || !a.available() || CASyncCatalog.packOf(r.ability) == null) continue;
            String n = CASyncCatalog.buy(r.ability);   // 0.3.7.4: the package that has it
            if (!names.contains(n)) names.add(n);
        }
        if (names.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(rowWhole(key) ? "Held off until you switch on " : "Some values are held off until you switch on ");
        for (int i = 0; i < names.size(); i++) sb.append(i == 0 ? "" : i == names.size() - 1 ? " and " : ", ").append(names.get(i));
        return sb.append(" on the Story page.").toString();
    }

    /** Every value of this row is held: one field, one rule covering every value but the off one, and it's off. */
    public static boolean rowWhole(String key) {
        String[] fields = ROWS.get(key);
        if (fields == null || fields.length != 1 || key.startsWith("p_")) return false;
        for (Rule r : RULES) if (r.field.equals(fields[0]) && !has(r.ability) && r.locked.length == 1 && (r.off == 0 || r.off == 1)
                && (r.locked[0] == 0 || r.locked[0] == 1)) return true;
        return false;
    }

    // 0.3.8.9: cost(String) is removed (no caller)

    // ---------------- the core's life: install, cradle, pull, break down, sell, return ----------------

    /** Installs the Autarch AI Core from your cargo (the chime's choice). Null on success. */
    public static String install(boolean cradle) {
        if (installed() && !pulled()) return "The Autopilot is already running";
        if (!CAAutarch.take()) return "No Autarch AI Core in your cargo";
        boolean back = installed();
        flag("installed", true);
        flag("pulled", false);
        if (!back) flag("cradle", cradle);
        phase("");
        afterChange();
        CALog.did("Autopilot: AUTARCH ONLINE. " + total() + " bandwidth.", true);
        return null;
    }

    /** The cradled core comes out: dormant, every ability and all fed PP on record. */
    public static void pull() {
        if (!cradle() || pulled() || !installed()) return;
        flag("pulled", true);
        CAAutarch.give();
        afterChange();
        CALog.think("Autopilot: the Autarch AI Core is out of its cradle. The Autopilot is dormant.");   // 0.3.8.6: the TriPad scene says it; Thinking list
    }

    /** Engineering breaks the core in your cargo down: BREAKDOWN_DAYS later, Alpha and Beta cores. Null on success. */
    public static String breakDown() {
        if (installed() && !pulled()) return "The core is in the TriPad";
        if (!CAAutarch.take()) return "No Autarch AI Core in your cargo";
        phase("breaking");
        stamp("phaseAt", now()); num("phaseShift", 0);
        CALog.think("Autopilot: Engineering is breaking the Autarch AI Core down. " + CASyncCatalog.BREAKDOWN_DAYS + " days.");   // 0.3.8.6: the TriPad dialog says it; Thinking list
        return null;
    }

    /** The core was sold at a market (CAAutarchListener): a new one is placed RETURN_DAYS later. */
    static void sold(boolean credits) {
        if (!"".equals(phase())) return;
        phase("sold");
        stamp("phaseAt", now()); num("phaseShift", 0);
        CALog.quiet("Autopilot: Autarch AI Core " + (credits ? "sold" : "lost") + "; a new one in " + CASyncCatalog.RETURN_DAYS + " days");
    }

    /** Daily: the breakdown's end, a sold core's return, and when salvage can hold one after a breakdown. */
    static void lifecycle() {
        String ph = phase();
        if (ph.isEmpty()) {
            if (CAAutarch.inCargo() > 0) flag("hadCore", true);
            // lost (jettisoned, left behind, gone with a ship): like a sale without the credits. The core in the TriPad isn't lost.
            // 0.3.6.2: also when its station is gone without the core ever reaching your cargo (left in the loot screen). A core
            // sold at a market while story mode was off counts as sold, and is taken off that market so there's never two
            boolean missing = (!installed() || pulled()) && CAAutarch.inCargo() == 0 && !CAAutarch.anywhere();
            if (missing && flag("autarchPlaced") && (flag("hadCore") || !CAAutarch.siteExists())) {
                boolean atMarket = CAAutarch.takeFromMarkets();
                flag("hadCore", false);
                flag("coreLost", !atMarket);   // 0.3.9.6 (C1): lost, not sold: it may turn up again
                sold(atMarket);
            }
            return;
        }
        float days = daysSince(stamp("phaseAt")) + num("phaseShift");
        if ("breaking".equals(ph) && days >= CASyncCatalog.BREAKDOWN_DAYS) {
            cargo().addCommodity(CASyncCatalog.ALPHA, CASyncCatalog.BREAKDOWN_ALPHA);
            cargo().addCommodity(CASyncCatalog.BETA, CASyncCatalog.BREAKDOWN_BETA);
            phase("broken");
            stamp("phaseAt", now()); num("phaseShift", 0);
            num("returnExtra", new Random().nextInt(CASyncCatalog.RETURN_RANDOM_DAYS + 1));
            flag("hadCore", false);
            CALog.did("Autopilot: Engineering finished. " + CASyncCatalog.cores(0, CASyncCatalog.BREAKDOWN_BETA, CASyncCatalog.BREAKDOWN_ALPHA)
                    + " in your cargo.", true);
            pend(CATriPad.BROKEN);   // 0.3.8.9: kept in the save until it shows; a Story page click no longer replaces it
        } else if ("sold".equals(ph) && flag("coreLost") && CAAutarch.anywhere()) {
            // 0.3.9.6 (C1): the lost core turned up again (picked up from cargo pods, or in a storage the check missed): no second one
            phase("");
            flag("coreLost", false);
            flag("hadCore", true);
        } else if ("sold".equals(ph) && days >= CASyncCatalog.RETURN_DAYS) {
            if (CAAutarch.place(CAAutarch.system())) {
                flag("coreLost", false);
                phase("");
                flag("hadCore", false);
                num("gen", generation() + 1);
                pend(CATriPad.RETURN);   // 0.3.8.9: kept in the save until it shows
            }
        } else if ("broken".equals(ph) && days >= CASyncCatalog.RETURN_DAYS + num("returnExtra")) {
            phase("salvage");
        }
    }

    /** The loot listener: after a breakdown and its wait, an orbital habitat or cryosleeper may hold a new core. */
    static boolean salvageRoll(String entityType) {
        if (!on() || !"salvage".equals(phase()) || entityType == null) return false;
        if (!entityType.contains("orbital_habitat") && !entityType.contains("cryosleeper")) return false;
        if (new Random().nextFloat() >= CASyncCatalog.SALVAGE_CHANCE) return false;
        phase("");
        num("gen", generation() + 1);
        text("autarchSystem", null);
        text("autarchArea", null);
        return true;
    }

    // ---------------- daily ----------------

    /** Daily, before the steps: the core's timers, the settings holds and the colony modes. */
    public static void daily() {
        try { CARapidResearch.sync(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "rapid research failed", t); }   // 0.3.9.9: on or off, story mode on or not
        try { CAStoryLimit.sync(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story colony limit failed", t); }   // 0.3.10.3
        CAStoryIntel.ensure();   // 0.3.7.1: the story entry and its search for the core (removed while story mode is off)
        if (!on()) { autarchAdmins(); return; }   // 0.3.7.9: with story mode off the core's administrators leave
        try { lifecycle(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core timers failed", t); }
        fit();
        enforce(CAData.settings());
        enforceModes();
        autarchAdmins();
        try { CARapidTerra.daily(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "rapid terraforming failed", t); }
        try { CARapidRepair.daily(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "rapid repairs failed", t); }   // 0.3.11.1
        try { CAStationFit.daily(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "station outfitting failed", t); }   // 0.3.11.7
        if (!has("buildout")) stopBuildouts();
    }

    /** 0.3.7.9: the Autarch administrator package (CAAutarchAdmin); never throws. */
    static void autarchAdmins() {
        try { CAAutarchAdmin.daily(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch administrator failed", t); }
        try { CAHistorian.daily(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Historian core failed", t); }   // 0.3.10.7
    }

    /** Fast buildout turned off (or dormant): every running buildout stops; the colony keeps its mode. */
    static void stopBuildouts() {
        CAData d = CAData.get();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            CAData.Colony c = d.colony(m.getId());
            if (c.buildout) CABuildout.stop(m, c, "Fast buildout is off");
        }
    }

    // ---------------- buttons ----------------

    /** The button waiting for its second click (two-step confirm), and a line for the top of the Story page. UI state only. */
    static String armed = null;
    public static String notice = null;
    /** A story beat a button asked for (0.3.8.9: the daily pass's beats are in the story state, pend); the TriPad shows it once the screen is closed. */
    static CATriPad queued = null;

    public static boolean isArmed(String id) { return id.equals(armed); }

    /** Handles a story button from either panel. True if it was one. */
    public static boolean handle(Object id) {
        if (!(id instanceof String) || !((String) id).startsWith(PREFIX)) { armed = null; return false; }
        String rest = ((String) id).substring(PREFIX.length());
        boolean twoStep = rest.startsWith("feed:");   // cores are used up: asks twice
        if (twoStep && !rest.equals(armed)) { armed = rest; notice = "Click Confirm to feed it. The cores don't come back."; return true; }
        armed = null;
        notice = null;
        String err = null;
        if (rest.startsWith("tick:")) err = toggle(rest.substring(5));
        else if (rest.equals("col+")) err = addColony();
        else if (rest.equals("col-")) err = removeColony();
        else if (rest.equals("seat+")) err = addSeat();   // 0.3.8.0
        else if (rest.equals("seat-")) err = removeSeat();
        else if (rest.startsWith("feed:")) {
            int[] h = onHand();
            String what = rest.substring(5);
            if (what.equals("a1")) err = feed(0, 0, 1);
            else if (what.equals("b1")) err = feed(0, 1, 0);
            else if (what.equals("g1")) err = feed(1, 0, 0);
            else if (what.equals("all")) err = feed(h[0], h[1], h[2]);
            if (err == null) notice = total() + " bandwidth.";
        }
        else if (rest.equals("storymode")) setOn(!on());   // the intel Settings page while LunaLib is off (CASetupUI.storyRow)
        else if (rest.equals("none")) { }   // a LOCKED row button
        else if (rest.equals("core") || rest.equals("pull") || rest.startsWith("read:")) {
            String beat = rest.equals("core") ? CATriPad.CHIME : rest.equals("pull") ? CATriPad.PULL : rest.substring(5);
            queued = new CATriPad(beat, null, rest.startsWith("read:"));
            notice = "Your TriPad brings this up once you close this screen.";
        }
        else if (rest.startsWith("beta:") && devMode()) err = beta(rest.substring(5));
        if (err != null) notice = err;
        return true;
    }

    /** The Story page's test tools show in dev mode only. */
    public static boolean devMode() {
        try { return Global.getSettings().isDevMode(); } catch (Throwable t) { return false; }
    }

    /** Test tools (dev mode only): cores, the core, PP, a year on the core's timer, beats again, a cleared story state. */
    static String beta(String op) {
        if (op.equals("cores")) {
            for (String c : CORES) cargo().addCommodity(c, 5);
            notice = "Beta: 5 of each core in your cargo.";
        } else if (op.equals("core")) {
            CAAutarch.give();
            notice = "Beta: an Autarch AI Core in your cargo.";
        } else if (op.equals("pp")) {
            num("fedPP", fedPoints() + 100);
            notice = "Beta: +100 bandwidth.";
        } else if (op.equals("reset")) {
            // 0.3.6.2: held settings and colony modes come back, and the watcher and listener stop, before the state goes
            try { switchOff(CAData.settings()); stopGame(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story reset release failed", t); }
            state().clear();
            notice = "Beta: story state cleared. Reload the save to place a new core.";
        } else if (op.equals("year")) {
            num("phaseShift", num("phaseShift") + 366);
            notice = "Beta: a year passed for the core's timer (" + (phase().isEmpty() ? "none running" : phase()) + ").";
        } else if (op.equals("beats")) {
            list("seen").clear();
            notice = "Beta: story beats show again.";
        }
        afterChange();
        return null;
    }

    // ---------------- the TriPad's own beats ----------------

    /** What the TriPad should show next, if anything: a queued beat, the founding note, the chime. */
    static CATriPad nextBeat() {
        if (!on() || askMode()) return null;   // 0.3.8.9: nothing before a new game's Story or Normal answer
        if (queued != null) { CATriPad q = queued; queued = null; return q; }
        // 0.3.8.9: the day's beats (BROKEN, RETURN) are kept in the save until they show (CATriPad.init takes them off)
        List<String> pend = list("pending");
        if (!pend.isEmpty()) return new CATriPad(pend.get(0), null, false);
        MarketAPI first = CATriPad.firstColony();
        if (first != null && !seen(CATriPad.FOUND)) return new CATriPad(CATriPad.FOUND, first, false);
        String chime = CATriPad.CHIME + ":" + generation();
        // 0.3.8.9: the chime and the tier scenes are marked seen when they show (CATriPad.init), so a failed show tries again
        if (dormant() && CAAutarch.inCargo() > 0 && !seen(chime)) return new CATriPad(CATriPad.CHIME, null, false);
        if (!dormant()) for (String b : CATriPad.TIER_BEATS) if (!seen(b) && beatDue(b)) return new CATriPad(b, null, false);   // 0.3.8.3
        return null;
    }

    /** 0.3.8.9: a beat the daily pass found, kept in the story state until it shows. */
    static void pend(String beat) { if (!list("pending").contains(beat)) list("pending").add(beat); }

    /** 0.3.8.9: CATriPad.init: the beat shows now; the chime is seen per core. */
    static void shown(String beat) {
        list("pending").remove(beat);
        if (CATriPad.CHIME.equals(beat)) see(CATriPad.CHIME + ":" + generation());
    }

    /** On every load: costs, placement, the holds, the story intel, the watcher and the core's listener. */
    public static void onLoad() {
        CASyncCatalog.load();
        try { migrateModesPack(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode Modes migration failed", t); }   // 0.3.12.1: first, so migratePacks keeps it
        try { migratePacks(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode package migration failed", t); }
        try { migrateModes(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode ability migration failed", t); }
        try { migrateTiers(); updateTiers(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode tiers failed", t); }
        queued = null; armed = null; notice = null; modeNote = null;
        if (askMode()) CAStoryPrompt.watch();   // a new game's pop-up not answered yet
        if (!on()) return;
        if (!askMode()) try { CAAutarch.ensurePlaced(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core placement failed", t); }
        fit();
        enforce(CAData.settings());
        enforceModes();
        try { startGame(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode start failed", t); }
    }

    /** Beats that can be read again, in story order. */
    public static List<String> readable() {
        List<String> out = new ArrayList<String>();
        for (String b : new String[]{CATriPad.FOUND, CATriPad.INSTALL, CATriPad.FEED, CATriPad.TIER2, CATriPad.TIER3, CATriPad.TIER4, CATriPad.RECORD,
                CATriPad.PULL, CATriPad.BROKEN, CATriPad.RETURN}) if (seen(b)) out.add(b);
        return out;
    }

    /**
     * 0.3.8.3: whether a tier scene is due: TIER2 to TIER4 once that tier is open (a save already past a tier gets the scene
     * too), RECORD once every package of the last tier is bought.
     */
    static boolean beatDue(String b) {
        int last = CASyncCatalog.TIERS.length - 1;
        if (CATriPad.TIER2.equals(b)) return openTier() >= 1;
        if (CATriPad.TIER3.equals(b)) return openTier() >= 2;
        if (CATriPad.TIER4.equals(b)) return openTier() >= 3;
        if (!CATriPad.RECORD.equals(b) || openTier() < last) return false;
        List<CASyncCatalog.Pack> l = CASyncCatalog.inTier(last);
        for (CASyncCatalog.Pack p : l) if (!ticked(p.id)) return false;
        return !l.isEmpty();
    }

    /** Storage submarket cargo at any market, for the lost-core check. */
    static CargoAPI storageOf(MarketAPI m) {
        try {
            SubmarketAPI sub = m.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            return sub == null ? null : sub.getCargo();
        } catch (Throwable t) { return null; }
    }
}
