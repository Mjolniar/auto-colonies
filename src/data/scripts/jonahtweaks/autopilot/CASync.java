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
 * and switches to match on load and when LunaLib's menu is saved (setOn). Off: every ability runs except Instant and Free
 * construction, there is no limit on managed colonies, and the quest and its timers pause. On: the Autopilot is dormant
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
    public static final String MOD_ID = "jonah_tweaks";
    public static final String PUBLIC_MOD_ID = "jonah_tweaks";
    static final String KEY = "ac_story";
    public static final String PREFIX = "ca:sync:";

    // ---------------- state ----------------

    /** JTStoryModeCheck: the state and the clock without a game (null in the game). */
    static Map<String, Object> testState = null;
    static float testDay = -1f;
    static final long DAY_MS = 86400000L;

    @SuppressWarnings("unchecked")
    static Map<String, Object> state() {
        if (testState != null) return testState;
        Map<String, Object> pd;
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
            CALog.did(dormant() ? "Story mode is on. The Colony Autopilot is a dormant program on your TriPad until it has the Autarch AI Core."
                    : "Story mode is on. The Autarch AI Core runs the abilities you bought with its processing power.", true);
        } else {
            try { stopGame(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "story mode stop failed", t); }
            CALog.did("Story mode is off. Every Colony Autopilot feature works again. The Autarch AI Core's story picks up where it stopped when you switch story mode back on.", true);
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
            if (c.mode != CAData.Mode.OFF && c.mode != mo) { c.mode = mo; d.clearPending(c); }
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
    /** Keep cores in Autopilot Storage for the next ability (on unless turned off). */
    public static boolean keepCores() { return !flag("noKeep"); }

    // ---------------- processing power ----------------

    public static int fedPoints() { return num("fedPP"); }
    public static int[] fed() { return new int[]{num("fedG"), num("fedB"), num("fedA")}; }
    /** All PP: the core's own plus every core fed to it; 0 before it's ever installed. */
    public static int total() { return installed() ? CASyncCatalog.BASE_PP + fedPoints() : 0; }
    public static int extraColonies() { return num("colonies"); }

    public static List<String> ticked() { return list("ticks"); }

    public static boolean ticked(String id) { return ticked().contains(id); }

    /** PP the ticked abilities and the bought colonies use (abilities whose mod is off don't count). */
    public static int spent() {
        int n = extraColonies() * CASyncCatalog.COLONY_PP;
        for (String id : ticked()) {
            CASyncCatalog.Ability a = CASyncCatalog.get(id);
            if (a != null && a.available()) n += a.cost;
        }
        return n;
    }

    public static int free() { return total() - spent(); }

    /** Whether an ability runs: the core is awake, it's ticked, its mod is on, and so is what it needs. */
    public static boolean has(String id) {
        if (!on()) return !"instantBuild".equals(id) && !"freeBuild".equals(id);   // story mode off: everything but the two story perks
        if (dormant()) return false;
        CASyncCatalog.Ability a = CASyncCatalog.get(id);
        if (a == null || !a.available() || !ticked(id)) return false;
        return a.requires == null || has(a.requires);
    }

    /** Ticks or unticks an ability. Null on success, else why not. */
    public static String toggle(String id) {
        CASyncCatalog.Ability a = CASyncCatalog.get(id);
        if (a == null || !a.available()) return "That ability isn't available.";
        if (!on()) return "Story mode is off.";
        if (ticked(id)) {
            untick(id);
            afterChange();
            return null;
        }
        if (dormant()) return "The Colony Autopilot is dormant.";
        if (a.requires != null && !ticked(a.requires)) return a.name + " needs " + CASyncCatalog.get(a.requires).name + " first.";
        if (free() < a.cost) return a.name + " needs " + a.cost + " PP, and " + Math.max(0, free()) + " are free.";
        ticked().add(id);
        afterChange();
        CALog.did("Autopilot: " + a.name + " is on (" + a.cost + " PP).");
        return null;
    }

    /** Unticks an ability and everything that needs it. */
    static void untick(String id) {
        if (!ticked().remove(id)) return;
        CASyncCatalog.Ability a = CASyncCatalog.get(id);
        if (a != null) CALog.did("Autopilot: " + a.name + " is off.");
        for (CASyncCatalog.Ability x : CASyncCatalog.ALL) if (id.equals(x.requires)) untick(x.id);
    }

    public static String addColony() {
        if (!on()) return "Story mode is off.";
        if (dormant()) return "The Colony Autopilot is dormant.";
        if (extraColonies() >= CASyncCatalog.MAX_COLONIES) return "It can't run more colonies.";
        if (free() < CASyncCatalog.COLONY_PP) return "One more managed colony needs " + CASyncCatalog.COLONY_PP + " PP, and " + Math.max(0, free()) + " are free.";
        num("colonies", extraColonies() + 1);
        afterChange();
        return null;
    }

    public static String removeColony() {
        if (extraColonies() <= 0) return "There are no bought colonies to remove.";
        num("colonies", extraColonies() - 1);
        afterChange();
        return null;
    }

    /** After any change to the abilities or colonies: settings, modes and the engine's lists follow. */
    static void afterChange() {
        fit();
        enforce(CAData.settings());
        enforceModes();
        CAAuto.nextPass.clear();
    }

    /** More used than there is (costs changed in the config file): the last ticked go first, then bought colonies. */
    static void fit() {
        if (!on()) return;
        List<String> t = ticked();
        while (spent() > total() && !t.isEmpty()) untick(t.get(t.size() - 1));
        while (spent() > total() && extraColonies() > 0) num("colonies", extraColonies() - 1);
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
        if (h[0] < g || h[1] < b || h[2] < a) return "Not enough cores: you have " + CASyncCatalog.cores(h[0], h[1], h[2]);
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
        int pts = CASyncCatalog.points(g, b, a);
        num("fedPP", fedPoints() + pts);
        num("fedG", num("fedG") + g); num("fedB", num("fedB") + b); num("fedA", num("fedA") + a);
        CALog.did("Autopilot: integrated " + CASyncCatalog.cores(g, b, a) + ", +" + pts + " PP (" + total() + " PP).", true);
        if (!seen(CATriPad.FEED)) queued = new CATriPad(CATriPad.FEED, null, false);
        return null;
    }

    /**
     * Cores Autopilot leaves in Autopilot Storage for the next ability, {alpha, beta, gamma} like CAItems.CORES: enough PP for
     * the cheapest ability not ticked yet, beyond the PP free now, lowest cores first. None while dormant or turned off.
     */
    public static int[] keepForFeeding() {
        int[] keep = new int[3];
        if (!on() || dormant() || !keepCores()) return keep;
        int cheapest = Integer.MAX_VALUE;
        for (CASyncCatalog.Ability a : CASyncCatalog.available()) if (!ticked(a.id)) cheapest = Math.min(cheapest, a.cost);
        if (extraColonies() < CASyncCatalog.MAX_COLONIES) cheapest = Math.min(cheapest, CASyncCatalog.COLONY_PP);
        if (cheapest == Integer.MAX_VALUE) return keep;
        int left = cheapest - Math.max(0, free());
        if (left <= 0) return keep;
        int[] h = inStorage();   // {gamma, beta, alpha}
        int g = Math.min(h[0], left / Math.max(1, CASyncCatalog.GAMMA_PP)); left -= g * CASyncCatalog.GAMMA_PP;
        int b = Math.min(h[1], (left + CASyncCatalog.BETA_PP - 1) / Math.max(1, CASyncCatalog.BETA_PP)); left -= b * CASyncCatalog.BETA_PP;
        int a = Math.min(h[2], Math.max(0, (left + CASyncCatalog.ALPHA_PP - 1) / Math.max(1, CASyncCatalog.ALPHA_PP)));
        keep[0] = a; keep[1] = b; keep[2] = g;
        return keep;
    }

    /** CAItems.placeCore's cores in storage {alpha, beta, gamma}, less what's kept for feeding. */
    public static void reserve(int[] have) {
        int[] keep = keepForFeeding();
        for (int k = 0; k < Math.min(have.length, keep.length); k++) have[k] = Math.max(0, have[k] - keep[k]);
    }

    // ---------------- what the rest of the mod asks ----------------

    /** What a build costs in credits: nothing with Free construction. */
    public static float buildPrice(float cost) { return has("freeBuild") ? 0f : cost; }

    /** Instant construction: builds and upgrades it starts finish the same day. */
    public static boolean instantBuild() { return has("instantBuild"); }

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

    /** Colonies in Approve or Auto the abilities allow: the first one, plus each bought; none while dormant. */
    public static int managedCap() { return !on() ? Integer.MAX_VALUE : dormant() ? 0 : 1 + extraColonies(); }

    static boolean counts(MarketAPI m) {
        try { return m != null && !CAEngine.isOutpost(m) && !CAHypershunt.is(m); } catch (Throwable t) { return m != null; }
    }

    static boolean managing(CAData.Mode mo) { return mo == CAData.Mode.APPROVE || mo == CAData.Mode.AUTO; }

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
        if (!managing(mode)) { want.remove(id); return mode; }
        CAData.Mode got = mode;
        if (got == CAData.Mode.AUTO && !has("auto")) got = CAData.Mode.APPROVE;
        if (!managing(c.mode)) {
            int n = 0;
            for (MarketAPI x : Misc.getPlayerMarkets(false)) if (x != m && counts(x) && managing(d.colony(x.getId()).mode)) n++;
            if (n >= managedCap()) got = CAData.Mode.ADVISOR;
        }
        if (got != mode) {
            want.put(id, mode.name());
            modeNote = m.getName() + " is in " + got.label + " for now. " + manageWhy(mode);
        } else want.remove(id);
        return got;
    }

    /** Why a colony can't have this mode now. */
    public static String manageWhy(CAData.Mode mode) {
        if (dormant()) return pulled() ? "The Autarch AI Core is out of its cradle." : "The Colony Autopilot needs the Autarch AI Core.";
        if (mode == CAData.Mode.AUTO && !has("auto")) return "Auto mode is off; it costs " + CASyncCatalog.get("auto").cost + " PP on the Story page.";
        int cap = managedCap();
        return "All " + cap + " managed colonies are in use; one more costs " + CASyncCatalog.COLONY_PP + " PP on the Story page.";
    }

    /** Daily and after every change: colonies over the limit wait in Advisor, Auto without Auto mode is Approve, and wanted modes come back. */
    public static void enforceModes() {
        if (!on()) return;
        CAData d;
        try { d = CAData.get(); } catch (Throwable t) { return; }
        Map<String, String> want = wanted();
        List<MarketAPI> mine = new ArrayList<MarketAPI>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) if (counts(m)) mine.add(m);
        Collections.sort(mine, new Comparator<MarketAPI>() {   // biggest first: they keep their place, and get it back first
            public int compare(MarketAPI a, MarketAPI b) {
                if (a.getSize() != b.getSize()) return b.getSize() - a.getSize();
                return a.getName().compareTo(b.getName());
            }
        });
        int cap = managedCap(), n = 0;
        boolean auto = has("auto");
        for (MarketAPI m : mine) {
            CAData.Colony c = d.colony(m.getId());
            if (!managing(c.mode)) continue;
            if (n >= cap) {
                want.put(m.getId(), c.mode.name());
                c.mode = CAData.Mode.ADVISOR;
                d.clearPending(c);
                CALog.did(m.getName() + ": moved to Advisor. " + manageWhy(CAData.Mode.APPROVE), true);
                continue;
            }
            n++;
            if (c.mode == CAData.Mode.AUTO && !auto) {
                want.put(m.getId(), CAData.Mode.AUTO.name());
                c.mode = CAData.Mode.APPROVE;
                d.clearPending(c);
                CALog.did(m.getName() + ": stays in Approve until Auto mode is on.", true);
            }
        }
        for (MarketAPI m : mine) {
            String w = want.get(m.getId());
            if (w == null) continue;
            CAData.Mode mo;
            try { mo = CAData.Mode.valueOf(w); } catch (Throwable t) { want.remove(m.getId()); continue; }
            CAData.Colony c = d.colony(m.getId());
            if (c.mode == CAData.Mode.OFF) { want.remove(m.getId()); continue; }   // you turned it off since
            CAData.Mode to = mo == CAData.Mode.AUTO && !auto ? CAData.Mode.APPROVE : mo;
            if (!managing(c.mode)) {
                if (n >= cap) continue;
                n++;
            }
            if (c.mode != to) {
                c.mode = to;
                d.clearPending(c);
                CALog.did(m.getName() + ": back in " + to.label + ".", true);
            }
            if (to == mo) want.remove(m.getId());
        }
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
        if ("p_spend".equals(key)) return has("spending") ? null : "Lavish needs Higher spending (" + cost("spending") + ") on the Story page.";
        String[] fields = ROWS.get(key);
        if (fields == null) return null;
        List<String> names = new ArrayList<String>();
        for (String f : fields) for (Rule r : RULES) {
            if (!r.field.equals(f) || has(r.ability)) continue;
            CASyncCatalog.Ability a = CASyncCatalog.get(r.ability);
            if (a == null || !a.available()) continue;
            String n = a.name + " (" + a.cost + " PP)";
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

    static String cost(String id) { CASyncCatalog.Ability a = CASyncCatalog.get(id); return a == null ? "" : a.cost + " PP"; }

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
        CALog.did("Autopilot: AUTARCH ONLINE. " + total() + " PP.", true);
        return null;
    }

    /** The cradled core comes out: dormant, every ability and all fed PP on record. */
    public static void pull() {
        if (!cradle() || pulled() || !installed()) return;
        flag("pulled", true);
        CAAutarch.give();
        afterChange();
        CALog.did("Autopilot: the Autarch AI Core is out of its cradle. The Autopilot is dormant.", true);
    }

    /** Engineering breaks the core in your cargo down: BREAKDOWN_DAYS later, Alpha and Beta cores. Null on success. */
    public static String breakDown() {
        if (installed() && !pulled()) return "The core is in the TriPad";
        if (!CAAutarch.take()) return "No Autarch AI Core in your cargo";
        phase("breaking");
        stamp("phaseAt", now()); num("phaseShift", 0);
        CALog.did("Autopilot: Engineering is breaking the Autarch AI Core down. " + CASyncCatalog.BREAKDOWN_DAYS + " days.", true);
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
            queued = new CATriPad(CATriPad.BROKEN, null, false);
        } else if ("sold".equals(ph) && days >= CASyncCatalog.RETURN_DAYS) {
            if (CAAutarch.place(CAAutarch.system())) {
                phase("");
                flag("hadCore", false);
                num("gen", generation() + 1);
                queued = new CATriPad(CATriPad.RETURN, null, false);
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
        return true;
    }

    // ---------------- daily ----------------

    /** Daily, before the steps: the core's timers, the settings holds and the colony modes. */
    public static void daily() {
        if (!on()) return;
        try { lifecycle(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Autarch AI Core timers failed", t); }
        fit();
        enforce(CAData.settings());
        enforceModes();
        if (!has("buildout")) stopBuildouts();
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
    /** A story beat a button asked for, or the daily pass queued; the TriPad shows it once the screen is closed. */
    static CATriPad queued = null;

    public static boolean isArmed(String id) { return id.equals(armed); }

    /** Handles a story button from either panel. True if it was one. */
    public static boolean handle(Object id) {
        if (!(id instanceof String) || !((String) id).startsWith(PREFIX)) { armed = null; return false; }
        String rest = ((String) id).substring(PREFIX.length());
        boolean twoStep = rest.startsWith("feed:");   // cores are used up: asks twice
        if (twoStep && !rest.equals(armed)) { armed = rest; notice = "Click Confirm to feed it. The cores are used up."; return true; }
        armed = null;
        notice = null;
        String err = null;
        if (rest.startsWith("tick:")) err = toggle(rest.substring(5));
        else if (rest.equals("col+")) err = addColony();
        else if (rest.equals("col-")) err = removeColony();
        else if (rest.startsWith("feed:")) {
            int[] h = onHand();
            String what = rest.substring(5);
            if (what.equals("a1")) err = feed(0, 0, 1);
            else if (what.equals("b1")) err = feed(0, 1, 0);
            else if (what.equals("g1")) err = feed(1, 0, 0);
            else if (what.equals("all")) err = feed(h[0], h[1], h[2]);
            if (err == null) notice = total() + " PP.";
        }
        else if (rest.equals("keep")) flag("noKeep", keepCores());
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
            notice = "Beta: +100 PP.";
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
        if (!on()) return null;
        if (queued != null) { CATriPad q = queued; queued = null; return q; }
        MarketAPI first = CATriPad.firstColony();
        if (first != null && !seen(CATriPad.FOUND)) return new CATriPad(CATriPad.FOUND, first, false);
        String chime = CATriPad.CHIME + ":" + generation();
        if (dormant() && CAAutarch.inCargo() > 0 && !seen(chime)) { see(chime); return new CATriPad(CATriPad.CHIME, null, false); }
        return null;
    }

    /** On every load: costs, placement, the holds, the story intel, the watcher and the core's listener. */
    public static void onLoad() {
        CASyncCatalog.load();
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
        for (String b : new String[]{CATriPad.FOUND, CATriPad.INSTALL, CATriPad.FEED, CATriPad.PULL, CATriPad.BROKEN, CATriPad.RETURN}) if (seen(b)) out.add(b);
        return out;
    }

    /** Storage submarket cargo at any market, for the lost-core check. */
    static CargoAPI storageOf(MarketAPI m) {
        try {
            SubmarketAPI sub = m.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            return sub == null ? null : sub.getCargo();
        } catch (Throwable t) { return null; }
    }
}
