package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

/**
 * Ashes of the Domain's Pluto Mining Station and Nidavelir Complex (Vaults of Knowledge), claimed by colonizing their
 * planets. Their settings are free to change, and VoK 5.0.7's code makes the best ones clear:
 * - Pluto: the mining laser at full strength (each 10% step costs 5,000 a month for about 285,000 worth of ore), and
 *   every unit of ore it digs assigned to the forges. The Purification Core gets it, except what the Fusion Forge needs
 *   to cover your metals and transplutonics shortages (the Fusion Forge's share is counted in, so it doesn't flip back).
 * - Nidavelir: manpower into the Nexus Core first (it makes machinery and components and cuts the whole complex's
 *   upkeep 5% a point), one point at a time while its four inputs are fully supplied, and back off when they aren't: a
 *   shortage is taken off every restored section's output. Up to 20 points (upkeep 0).
 * Restorations go through the same gates as the hypershunt's: Pluto's three sections and the Nexus Core in Auto.
 * Settings you change yourself are left alone until you hand them back. Reflection only.
 */
public class CAMegastructures {
    static final String PKG = "data.kaysaar.aotd.vok.campaign.econ.megastructures.";
    static final String PLUTO = "aotd_pluto_station", NIDAVELIR = "aotd_nidavelir";
    static final String OCN = "pluto_ocn", FF = "pluto_ff", PC = "pluto_pc", NEXUS = "nidavelir_nexus";
    static final String[] PLUTO_ORDER = {OCN, PC, FF}, NIDAVELIR_AUTO = {NEXUS};
    static final String[] INPUTS = {"metals", "rare_metals", "refined_metal", "purified_rare_metal"};
    static final int MAX_NEXUS = 20;

    static Object ocnMax, ocnSet, forgeRes, forgeSet, nidAvail, setManpower;
    static Object ocnMag, manpower, automated;
    static Class<?> ocnC, forgeC, nidSecC, nidC;
    static boolean linked = false, broken = false;

    static boolean link() {
        if (linked || broken) return linked;
        if (!CAHypershunt.link()) { broken = true; return false; }
        try {
            ClassLoader cl = Global.getSettings().getScriptClassLoader();
            ocnC = Class.forName(PKG + "impl.sections.pluto.OpticCommandNexus", true, cl);
            forgeC = Class.forName(PKG + "impl.sections.pluto.PlutoForgeSection", true, cl);
            nidSecC = Class.forName(PKG + "impl.sections.nidavelir.BaseNidavelirSection", true, cl);
            nidC = Class.forName(PKG + "impl.scripts.NidavelirMegastructure", true, cl);
            ocnMax = CARefl.method(ocnC, "getMaxMagnitude"); ocnSet = CARefl.method(ocnC, "setCurrentMagnitude", int.class); ocnMag = CARefl.field(ocnC, "currentMagnitude");
            forgeRes = CARefl.method(forgeC, "getRes"); forgeSet = CARefl.method(forgeC, "updateResourceDesignated", String.class, Integer.class);
            nidAvail = CARefl.method(nidC, "getAvailableManpower");
            setManpower = CARefl.method(nidSecC, "setAssignedManpower", int.class);
            manpower = CARefl.field(nidSecC, "assignedManpower"); automated = CARefl.field(nidSecC, "isAutomated");
            linked = true;
        } catch (Throwable t) {
            broken = true;
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "can't read Vaults of Knowledge's Pluto and Nidavelir settings; they're left to you", t);
        }
        return linked;
    }

    static class Mega {
        MarketAPI m;
        String kind, name;
        Object script;
        Map<String, CAHypershunt.Section> sections = new LinkedHashMap<String, CAHypershunt.Section>();
    }

    @SuppressWarnings("unchecked")
    static Mega read(MarketAPI m) throws Exception {
        SectorEntityToken e = m.getPrimaryEntity();
        if (e == null || !link()) return null;
        for (String kind : new String[]{PLUTO, NIDAVELIR}) {
            Object script = CARefl.invoke(CAHypershunt.scriptOf, null, e, kind);
            if (script == null) continue;
            Mega g = new Mega();
            g.m = m; g.kind = kind; g.script = script;
            g.name = kind.equals(PLUTO) ? "Pluto Mining Station" : "Nidavelir Complex";
            for (Object raw : (List<Object>) CARefl.invoke(CAHypershunt.sections, script)) if (raw != null) { CAHypershunt.Section s = CAHypershunt.section(raw); g.sections.put(s.id, s); }
            return g;
        }
        return null;
    }

    /** Your Pluto and Nidavelir colonies. */
    static List<Mega> all() {
        List<Mega> out = new ArrayList<Mega>();
        if (!Global.getSettings().getModManager().isModEnabled(CAHypershunt.MOD)) return out;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!m.hasIndustry("pluto_station") && !m.hasIndustry("nidavelir_complex")) continue;
            try { Mega g = read(m); if (g != null) out.add(g); }
            catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "megastructure read failed at " + m.getName(), t); }
        }
        return out;
    }

    static boolean up(Mega g, String id) { CAHypershunt.Section s = g.sections.get(id); return s != null && s.restored; }

    // ---------------- Pluto ----------------

    /** What Autopilot would set at Pluto: key -> value (laser magnitude, then ore units per forge). Empty when nothing is restored. */
    @SuppressWarnings("unchecked")
    static Map<String, Integer> plutoTarget(Mega g, Map<String, Integer> now) throws Exception {
        Map<String, Integer> t = new LinkedHashMap<String, Integer>();
        if (!up(g, OCN)) return t;
        Object ocn = g.sections.get(OCN).raw;
        int mag = ((Number) CARefl.invoke(ocnMax, ocn)).intValue();
        t.put("laser", mag);
        boolean ff = up(g, FF), pc = up(g, PC);
        if (!ff && !pc) return t;
        int ore = 10 * mag, rare = 12 * mag;
        int ffOre = 0, ffRare = 0;
        if (ff && !pc) { ffOre = ore; ffRare = rare; }
        else if (ff) {
            Map<String, Integer> d = CAEngine.empireDeficits();
            int curOre = now.containsKey("ff:ore") ? now.get("ff:ore") : 0, curRare = now.containsKey("ff:rare_ore") ? now.get("ff:rare_ore") : 0;
            int needMetals = (d.containsKey("metals") ? d.get("metals") : 0) + 4 * curOre;          // what the forge covers now counts as needed
            int needRare = (d.containsKey("rare_metals") ? d.get("rare_metals") : 0) + 2 * curRare;
            ffOre = Math.min(ore, (int) Math.ceil(needMetals / 4f));
            ffRare = Math.min(rare, (int) Math.ceil(needRare / 2f));
        }
        if (ff) { t.put("ff:ore", ffOre); t.put("ff:rare_ore", ffRare); }
        if (pc) { t.put("pc:ore", ore - ffOre); t.put("pc:rare_ore", rare - ffRare); }
        return t;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Integer> plutoNow(Mega g) throws Exception {
        Map<String, Integer> n = new LinkedHashMap<String, Integer>();
        if (!up(g, OCN)) return n;
        n.put("laser", CARefl.getInt(ocnMag, g.sections.get(OCN).raw));
        for (String f : new String[]{FF, PC}) {
            if (!up(g, f)) continue;
            Map<String, Integer> res = (Map<String, Integer>) CARefl.invoke(forgeRes, g.sections.get(f).raw);
            String k = f.equals(FF) ? "ff" : "pc";
            n.put(k + ":ore", res == null || res.get("ore") == null ? 0 : res.get("ore"));
            n.put(k + ":rare_ore", res == null || res.get("rare_ore") == null ? 0 : res.get("rare_ore"));
        }
        return n;
    }

    static void plutoApply(Mega g, Map<String, Integer> t) throws Exception {
        Integer laser = t.get("laser");
        if (laser != null && laser != CARefl.getInt(ocnMag, g.sections.get(OCN).raw)) CARefl.invoke(ocnSet, g.sections.get(OCN).raw, laser);   // lowering it clears the forges
        for (Map.Entry<String, Integer> e : t.entrySet()) {
            if (e.getKey().equals("laser")) continue;
            String[] k = e.getKey().split(":");
            CARefl.invoke(forgeSet, g.sections.get(k[0].equals("ff") ? FF : PC).raw, k[1], e.getValue());
        }
        g.m.reapplyIndustries();
    }

    // ---------------- Nidavelir ----------------

    /** Nexus Core manpower: one more point while its inputs are fully supplied, fewer while short. */
    static Map<String, Integer> nidTarget(Mega g, Map<String, Integer> now, CAData.Colony c) throws Exception {
        Map<String, Integer> t = new LinkedHashMap<String, Integer>();
        CAHypershunt.Section nx = g.sections.get(NEXUS);
        if (nx == null || !nx.restored || CARefl.getBoolean(automated, nx.raw)) return t;
        int cur = now.containsKey("nexus") ? now.get("nexus") : 0;
        int pool = cur + ((Number) CARefl.invoke(nidAvail, g.script)).intValue();
        int worst = 0;
        int[] perPoint = {5, 12, 6, 3};
        for (int i = 0; i < INPUTS.length; i++) {
            CommodityOnMarketAPI com = g.m.getCommodityData(INPUTS[i]);
            if (com != null && com.getDeficitQuantity() > 0) worst = Math.max(worst, (int) Math.ceil(com.getDeficitQuantity() / (float) perPoint[i]));
        }
        int want;
        if (worst > 0) want = cur - worst;
        else if (c.megaHold > 0L && Global.getSector().getClock().getElapsedDaysSince(c.megaHold) < 30f) want = cur;   // just backed off
        else want = cur + 1;
        t.put("nexus", Math.max(0, Math.min(Math.min(MAX_NEXUS, pool), want)));
        return t;
    }

    static Map<String, Integer> nidNow(Mega g) throws Exception {
        Map<String, Integer> n = new LinkedHashMap<String, Integer>();
        CAHypershunt.Section nx = g.sections.get(NEXUS);
        if (nx != null && nx.restored && !CARefl.getBoolean(automated, nx.raw)) n.put("nexus", CARefl.getInt(manpower, nx.raw));
        return n;
    }

    static void nidApply(Mega g, Map<String, Integer> t) throws Exception {
        Integer n = t.get("nexus");
        if (n != null) CARefl.invoke(setManpower, g.sections.get(NEXUS).raw, n);
        g.m.reapplyIndustries();
    }

    // ---------------- shared ----------------

    static Map<String, Integer> now(Mega g) throws Exception { return g.kind.equals(PLUTO) ? plutoNow(g) : nidNow(g); }

    static Map<String, Integer> target(Mega g, Map<String, Integer> now, CAData.Colony c) throws Exception {
        return g.kind.equals(PLUTO) ? plutoTarget(g, now) : nidTarget(g, now, c);
    }

    static String describe(Mega g, Map<String, Integer> v) {
        List<String> l = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : v.entrySet()) {
            String k = e.getKey();
            if (k.equals("laser")) l.add("laser " + e.getValue() * 10 + "%");
            else if (k.equals("nexus")) l.add("Nexus Core manpower " + e.getValue());
            else l.add((k.startsWith("ff") ? "Fusion Forge " : "Purification Core ") + (k.endsWith("rare_ore") ? "transplutonic ore " : "ore ") + e.getValue());
        }
        return CAContracts.join(l);
    }

    /** True when a setting Autopilot set has been changed since (so you set it); marks the colony as yours. */
    static boolean yours(Mega g, CAData.Colony c, Map<String, Integer> now) {
        if (c.megaManual) return true;
        Map<String, Integer> set = c.megaSet();
        boolean changed = false;
        if (set.isEmpty()) {
            for (Integer v : now.values()) if (v != null && v != 0) changed = true;   // configured before Autopilot managed it
        } else for (Map.Entry<String, Integer> e : now.entrySet()) {
            Integer was = set.get(e.getKey());
            if (was != null && !was.equals(e.getValue())) changed = true;
        }
        if (changed) {
            c.megaManual = true;
            CALog.add(g.m.getName() + ": you set the " + g.name + " yourself; Autopilot leaves it (Let Autopilot manage it, in the colony panel)");
        }
        return changed;
    }

    static String applyTarget(Mega g, CAData.Colony c, Map<String, Integer> t, String who) throws Exception {
        if (g.kind.equals(PLUTO)) plutoApply(g, t); else nidApply(g, t);
        c.megaSet().clear();
        c.megaSet().putAll(t);
        CALog.add(g.m.getName() + ": " + who + " set the " + g.name + ": " + describe(g, t));
        return null;
    }

    /** Follow colony mode: Auto applies the best settings and starts Pluto's and the Nexus Core's restorations; Approve logs them once. */
    public static void run(CAData d, CASettings s) {
        if (s.hypershuntMode != 0) return;
        for (Mega g : all()) {
            CAData.Colony c = d.colony(g.m.getId());
            if (c.mode != CAData.Mode.APPROVE && c.mode != CAData.Mode.AUTO) continue;
            boolean auto = c.mode == CAData.Mode.AUTO;
            try {
                Map<String, Integer> now = now(g);
                if (!yours(g, c, now)) {
                    Map<String, Integer> t = target(g, now, c);
                    if (!t.isEmpty() && !t.equals(now)) {
                        Integer was = now.get("nexus"), will = t.get("nexus");
                        if (auto && was != null && will != null && will < was) c.megaHold = Global.getSector().getClock().getTimestamp();   // wait 30 days before adding again
                        if (auto) applyTarget(g, c, t, "Auto");
                        else if (CALog.once("mega:" + g.m.getId() + ":" + t)) CALog.add(g.m.getName() + ": recommends " + describe(g, t) + " at the " + g.name + "; Apply in the colony panel");
                    }
                }
                CAHypershunt.Section next = nextRestore(g);
                if (next != null) CAHypershunt.restore(g.m, next, s, auto);
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "megastructure step failed at " + g.m.getName(), t);
            }
        }
    }

    /** The next section Auto may restore: Pluto's in order, and Nidavelir's Nexus Core (the others are left to you). */
    static CAHypershunt.Section nextRestore(Mega g) {
        for (CAHypershunt.Section s : g.sections.values()) if (s.restoring) return null;   // one at a time
        if (!CAHypershunt.researched(CAHypershunt.TECH_RESTORE)) return null;
        for (String id : g.kind.equals(PLUTO) ? PLUTO_ORDER : NIDAVELIR_AUTO) {
            CAHypershunt.Section s = g.sections.get(id);
            if (s != null && !s.restored && s.canStart) return s;
        }
        return null;
    }

    /** A colony panel button: apply, manage, or restore:sectionId. */
    public static String handle(MarketAPI m, String action, String arg) {
        try {
            Mega g = read(m);
            if (g == null) return "The megastructure can't be read";
            CAData.Colony c = CAData.get().colony(m.getId());
            if ("restore".equals(action)) {
                CAHypershunt.Section s = g.sections.get(arg);
                if (s == null) return "Unknown section";
                String err = CAHypershunt.start(m, s, "you");
                return err == null ? "Restoration started" : err;
            }
            if ("manage".equals(action)) { c.megaManual = false; c.megaSet().clear(); c.megaHold = 0L; return "Autopilot manages the " + g.name + " again"; }
            if ("apply".equals(action)) {
                Map<String, Integer> t = target(g, now(g), c);
                if (t.isEmpty()) return "Nothing to set yet";
                c.megaManual = false;
                applyTarget(g, c, t, "you");
                return "Settings applied";
            }
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "megastructure action failed at " + m.getName(), t);
            return "That failed; see starsector.log";
        }
        return "Unknown action";
    }

    public static boolean has(MarketAPI m) { return m.hasIndustry("pluto_station") || m.hasIndustry("nidavelir_complex"); }

    /** The empire panel: one line per Pluto or Nidavelir colony. */
    public static void renderEmpire(TooltipMakerAPI t, CAData d, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor();
        for (Mega g : all()) {
            CAData.Colony c = d.colony(g.m.getId());
            int done = 0;
            for (CAHypershunt.Section x : g.sections.values()) if (x.restored) done++;
            String state;
            try {
                Map<String, Integer> now = now(g), tgt = target(g, now, c);
                state = now.isEmpty() ? "nothing to set until a section is restored"
                        : c.megaManual ? "your settings (" + describe(g, now) + ")"
                        : tgt.equals(now) ? "best settings (" + describe(g, now) + ")" : "recommends " + describe(g, tgt);
            } catch (Throwable e) { state = "settings can't be read"; }
            CAHypershunt.Section next = nextRestore(g);
            t.addPara("%s, %s (%s): %s of %s sections restored; %s." + (next != null ? " Next restoration: " + CALog.esc(next.name) + "." : ""), 8f, hl,
                    g.name, g.m.getName(), c.mode.label, "" + done, "" + g.sections.size(), state);
        }
        t.addPara("Open the colony's panel to apply settings or start a restoration.", gray, 4f);
    }

    /** The colony panel's section for a Pluto or Nidavelir colony. */
    public static void renderColony(TooltipMakerAPI t, MarketAPI m, CAData.Colony c, CASettings s) {
        Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        Mega g;
        try { g = read(m); } catch (Throwable e) { g = null; CADiagnostics.LOG.warn(CADiagnostics.TAG + "megastructure read failed at " + m.getName(), e); }
        if (g == null) { t.addPara("The megastructure's state can't be read; see starsector.log.", gray, 6f); return; }
        boolean buttons = s.hypershuntMode != 2;
        for (CAHypershunt.Section x : g.sections.values()) CAHypershunt.section(t, null, x, buttons, CAColonyDialog.MEGA + "restore:" + x.id);
        if (!CAHypershunt.researched(CAHypershunt.TECH_RESTORE)) t.addPara("Restoring sections needs %s researched.", 4f, hl, CAHypershunt.techName(CAHypershunt.TECH_RESTORE));
        try {
            Map<String, Integer> now = now(g), tgt = target(g, now, c);
            if (!now.isEmpty()) t.addPara("Now: " + CALog.esc(describe(g, now)) + ".", hl, 8f);
            if (!tgt.isEmpty() && !tgt.equals(now)) {
                t.addPara("Best: " + CALog.esc(describe(g, tgt)) + ".", hl, 4f);
                if (buttons) t.addButton("Apply these settings", CAColonyDialog.MEGA + "apply:x", base, dark, Alignment.MID, CutStyle.TL_BR, 200f, 22f, 4f);
            } else if (!now.isEmpty()) t.addPara("These are the best settings.", gray, 4f);
        } catch (Throwable e) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "megastructure settings failed at " + m.getName(), e); }
        if (c.megaManual) {
            t.addPara("You set it yourself, so Autopilot leaves it.", gray, 4f);
            t.addButton("Let Autopilot manage it", CAColonyDialog.MEGA + "manage:x", base, dark, Alignment.MID, CutStyle.TL_BR, 200f, 22f, 4f);
        }
        t.addPara(g.kind.equals(PLUTO) ? "The laser runs at full strength and every unit of ore goes to the forges: the Purification Core, "
                        + "except what the Fusion Forge needs to cover your metals and transplutonics shortages."
                        : "Manpower goes to the Nexus Core one point at a time while its inputs are fully supplied, and comes back off when "
                        + "they're short (a shortage hits every restored section). Other sections' manpower and automation are yours.", gray, 6f);
    }
}
