package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/**
 * Mod watch (0.2.38.0): how each colony mod Autopilot has an integration for is doing in this game, for the playthrough
 * diagnostics. Weekly and at every Diagnostics scan, per installed mod:
 * - its integration health (CACompat), how Autopilot rated each of its buildings, and where they are at your colonies;
 * - measured buildings: what the probe colony said against what the building really makes now (a clean one: no core,
 *   item or improvement, no input short), so a bad measurement shows up;
 * - estimated buildings: the correction your colonies taught it (CACalibrate), flagged when it hits a limit;
 * - its items in Autopilot Storage and installed, its conditions at your colonies;
 * - checks of its own (below), each PASS, INFO, WARN or FAIL, and a verdict: FAIL on any failed check, quarantined
 *   building or integration that's off; WARN on any warning or a mod version it wasn't written for; else OK.
 * Written into the Diagnostics report as "modWatch" and one log line per mod; jtbuild/moddiag.py reads both. Reads only.
 */
public class CAModWatch {
    /** Mod ids watched, with names. */
    static final String[][] MODS = {
            {"omega_core_everywhere", "Omega Core Everywhere"}, {"astral_ascension_renewed", "Astral Ascension Renewed"},
            {"niko_scavengableindustries", "Niko's Scavengable Industries"}, {"roider", "Roider Union"},
            {"JYD", "Junk Yard Dogs"}, {"CFT", "Carter's Freetraders"}, {"vulpoids", "Vulpoids"},
            {"yunruindustries", "Yunru Industries"}, {"SBTM", "Slightly Better Tech-Mining"}, {"yunruworlds", "Yunru's Worlds"}};

    static final List<JSONObject> last = new ArrayList<JSONObject>();
    static final Map<String, String> logged = new HashMap<String, String>();
    static float lastRun = -99f;

    public static List<JSONObject> latest() { return last; }

    /**
     * Weekly from the daily pass; writes the report when it ran, else any report marked since (a quarantine).
     * 0.3.0.0 (research 07 H2): timed on the game clock (CADay), not the pass's session clock that starts at 0 on every load,
     * and a reload of an earlier save (the clock going back) runs it at once instead of waiting out the gap. `now` is kept
     * for the caller and unused.
     */
    public static void weekly(float now) {
        float day = CADay.now();
        if (day - lastRun < 7f && day >= lastRun) { CADiagnostics.flushReport(); return; }
        lastRun = day;
        run();
        CADiagnostics.writeReport();
    }

    /** 0.3.0.0 (07 H6): last week's results and log lines from another save, cleared on load (CAReset.onLoad). */
    public static void reset() { last.clear(); logged.clear(); lastRun = -99f; }

    public static void run() {
        last.clear();
        if (Global.getSector() == null) return;
        List<MarketAPI> mine = Misc.getPlayerMarkets(false);
        for (String[] mod : MODS) {
            if (!Global.getSettings().getModManager().isModEnabled(mod[0])) continue;
            try {
                JSONObject o = watch(mod[0], mod[1], mine);
                last.add(o);
                String line = CADiagnostics.TAG + "mod watch " + mod[1] + ": " + o.optString("verdict") + " - " + o.optString("summary");
                // a line when it changes (and the first time each session), not ten identical lines a week (0.2.39.0)
                if (line.equals(logged.get(mod[0]))) continue;
                logged.put(mod[0], line);
                // a warning says what it is (0.2.43.1): the report holds only the latest week, so a line that said "1 warnings"
                // and nothing else was gone by the time anyone looked
                if ("OK".equals(o.optString("verdict"))) CADiagnostics.LOG.info(line); else CADiagnostics.LOG.warn(line + problems(o));
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "mod watch for " + mod[1] + " failed", t);
            }
        }
    }

    /** The WARN and FAIL checks of one mod's watch, for its log line. */
    static String problems(JSONObject o) {
        StringBuilder b = new StringBuilder();
        JSONArray arr = o.optJSONArray("checks");
        for (int i = 0; arr != null && i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            String r = c.optString("result");
            if (r.equals("WARN") || r.equals("FAIL")) b.append(" | ").append(r).append(": ").append(c.optString("text"));
        }
        return b.toString();
    }

    // ---------------- one mod ----------------

    static class Checks {
        JSONArray arr = new JSONArray();
        int warn, fail;
        void add(String result, String text) {
            try { arr.put(new JSONObject().put("result", result).put("text", text)); } catch (Throwable ignored) { }
            if (result.equals("WARN")) warn++;
            if (result.equals("FAIL")) fail++;
        }
    }

    static String srcMod(ModSpecAPI m) { return m == null ? null : m.getId(); }

    static JSONObject watch(String modId, String name, List<MarketAPI> mine) throws Exception {
        JSONObject o = new JSONObject();
        o.put("mod", modId); o.put("name", name);
        ModSpecAPI spec = Global.getSettings().getModManager().getModSpec(modId);
        o.put("version", spec == null ? "?" : spec.getVersion());
        Checks ch = new Checks();
        CACompat.Entry e = CACompat.BY_MOD.get(modId);
        if (e != null) {
            o.put("integration", e.status.name() + (e.missing.isEmpty() ? "" : " missing " + e.missing));
            if (e.status == CACompat.Status.OFF || e.status == CACompat.Status.PARTIAL) ch.add("FAIL", "integration " + CACompat.describe(e));
            else if (e.status == CACompat.Status.NEWER) ch.add("WARN", "integration " + CACompat.describe(e));
        }

        // its buildings: how Autopilot rates them, and where they are
        JSONArray builds = new JSONArray();
        int present = 0, quarantined = 0;
        for (IndustrySpecAPI s : Global.getSettings().getAllIndustrySpecs()) {
            if (!modId.equals(srcMod(s.getSourceMod())) && !(modId.equals("SBTM") && s.getId().equals("techmining"))) continue;
            CADiagnostics.Level lvl = CADiagnostics.industryLevel(s.getId());
            JSONObject b = new JSONObject();
            b.put("id", s.getId()); b.put("level", lvl.name());
            String left = CAMods.leftToYou(s.getId());
            if (left != null) b.put("left", left);
            if (lvl == CADiagnostics.Level.QUARANTINED && left == null && !s.hasTag("hide_in_codex")) {
                quarantined++;
                ch.add("FAIL", s.getName() + " is quarantined (its code failed while Autopilot checked it; see the Diagnostics list)");
            }
            JSONArray at = new JSONArray();
            for (MarketAPI m : mine) {
                Industry ind = m.getIndustry(s.getId());
                if (ind == null) continue;
                present++;
                at.put(m.getName() + (ind.isFunctional() ? "" : ind.isBuilding() ? " (building)" : " (not working)"));
                if (lvl == CADiagnostics.Level.MEASURED) measuredVsReal(m, ind, ch);
            }
            if (at.length() > 0) b.put("at", at);
            if (CAEngine.estimated(s.getId())) {
                Integer n = CAData.get().calOutN().get(s.getId());
                if (n != null && n > 0) {
                    float f = CACalibrate.outputFactor(s.getId());
                    b.put("outputCorrection", f);
                    if (f <= 0.51f || f >= 1.99f) ch.add("WARN", s.getName() + ": its estimate is off by the most Autopilot corrects (x" + fmt(f) + "); the formula needs a look");
                }
            }
            builds.put(b);
        }
        o.put("buildings", builds);

        // its items and conditions
        int stored = 0;
        for (CAStores.Held h : CAStores.stacks()) {
            CargoStackAPI st = h.stack;
            SpecialItemSpecAPI sp = st.isSpecialStack() ? st.getSpecialItemSpecIfSpecial() : null;
            if (sp != null && modId.equals(srcMod(sp.getSourceMod()))) stored += (int) st.getSize();
        }
        JSONArray installed = new JSONArray(), conds = new JSONArray();
        for (MarketAPI m : mine) {
            for (Industry ind : m.getIndustries()) {
                if (ind.getSpecialItem() == null) continue;
                SpecialItemSpecAPI sp = Global.getSettings().getSpecialItemSpec(ind.getSpecialItem().getId());
                if (sp != null && modId.equals(srcMod(sp.getSourceMod()))) installed.put(sp.getName() + " in " + m.getName() + "'s " + ind.getCurrentName());
            }
            for (MarketConditionAPI c : m.getConditions())
                if (c.getSpec() != null && modId.equals(srcMod(c.getSpec().getSourceMod()))) conds.put(c.getName() + " at " + m.getName());
        }
        if (stored > 0) o.put("itemsInStorage", stored);
        if (installed.length() > 0) o.put("itemsInstalled", installed);
        if (conds.length() > 0) o.put("conditions", conds);

        own(modId, mine, o, ch);

        o.put("checks", ch.arr);
        String verdict = ch.fail > 0 ? "FAIL" : ch.warn > 0 ? "WARN" : "OK";
        o.put("verdict", verdict);
        o.put("summary", present + " of its buildings at your colonies, " + installed.length() + " items installed, " + stored + " in Autopilot Storage, "
                + conds.length() + " conditions; " + ch.fail + " failed, " + ch.warn + " warnings" + (quarantined > 0 ? ", " + quarantined + " quarantined" : ""));
        return o;
    }

    static String fmt(float f) { return String.format(Locale.ROOT, "%.2f", f); }

    /** A clean, working measured building: the probe colony's output against what it really makes. */
    static void measuredVsReal(MarketAPI m, Industry ind, Checks ch) {
        try {
            if (!ind.isFunctional() || ind.getAICoreId() != null || ind.getSpecialItem() != null || ind.isImproved()) return;
            if (!ind.getAllDeficit().isEmpty()) {
                boolean any = false;
                for (com.fs.starfarer.api.util.Pair<String, Integer> p : ind.getAllDeficit()) if (p.two != null && p.two > 0) any = true;
                if (any) return;   // its inputs are short: lower output is the shortage, not the measurement
            }
            CASimulator.Effects e = CASimulator.measure(m, ind.getId());
            if (e.error != null) return;
            for (MutableCommodityQuantity q : ind.getAllSupply()) {
                int real = q.getQuantity().getModifiedInt();
                Integer meas = e.supply.get(q.getCommodityId());
                int mv = meas == null ? 0 : meas;
                if (real <= 0 && mv <= 0) continue;
                if (Math.abs(real - mv) >= 2 && (mv == 0 || real > 1.5f * mv || real < 0.5f * mv))
                    ch.add("WARN", ind.getCurrentName() + " at " + m.getName() + " makes " + real + " " + q.getCommodityId() + "; the probe colony measured " + mv);
            }
        } catch (Throwable t) { ch.add("INFO", "couldn't compare " + ind.getCurrentName() + " with its measurement: " + t); }
    }

    // ---------------- each mod's own checks ----------------

    static void own(String modId, List<MarketAPI> mine, JSONObject o, Checks ch) throws Exception {
        if (modId.equals("omega_core_everywhere") || modId.equals("astral_ascension_renewed")) omega(modId, mine, o, ch);
        else if (modId.equals("vulpoids")) vulpoids(mine, ch);
        else if (modId.equals("roider")) roider(mine, ch);
        else if (modId.equals("CFT") || modId.equals("yunruindustries")) illegal(modId, mine, ch);
        else if (modId.equals("SBTM")) sbtm(mine, ch);
        else if (modId.equals("niko_scavengableindustries")) nsi(mine, ch);
        if (modId.equals("yunruindustries")) drydocks(mine, ch);
    }

    static void omega(String modId, List<MarketAPI> mine, JSONObject o, Checks ch) throws Exception {
        o.put("omegaInStorage", (int) CAStores.count(CAOmega.CORE));
        boolean oce = modId.equals("omega_core_everywhere");
        if (oce) {
            boolean fx = CAOmega.buildingEffects();
            ch.add("INFO", "building effects " + (fx ? "on" : "off (its LunaLib setting): Autopilot leaves Omega cores out of buildings"));
            for (MarketAPI m : mine) for (Industry ind : m.getIndustries()) {
                if (!CAOmega.CORE.equals(ind.getAICoreId())) continue;
                boolean seen = false, makes = false;
                for (MutableCommodityQuantity q : ind.getAllSupply()) {
                    if (q.getQuantity().getModifiedInt() > 0) makes = true;
                    if (q.getQuantity().getMultMods().containsKey("oce_supply") || q.getQuantity().getFlatMods().containsKey("oce_supply")) seen = true;
                }
                boolean doubles = makes && fx && ind.isFunctional() && CAOmega.doublesOutput(m, ind);   // not Patrol HQ or High Command: fleet size there
                ch.add(!doubles || seen ? "PASS" : "WARN", "Omega core in " + m.getName() + "'s " + ind.getCurrentName()
                        + (doubles ? (seen ? ": its x2 output is applied" : ": its x2 output isn't applied (Omega Core Everywhere's effect missing)") : ""));
            }
        } else {
            for (MarketAPI m : mine) {
                if (!CAOmega.governs(m)) continue;
                int max = Misc.getMaxMarketSize(m);
                ch.add(max >= 15 || m.hasCondition("aotd_archeo_site") ? "PASS" : "WARN", m.getName() + " (size " + m.getSize() + ") is governed by an Omega core; "
                        + "its size limit is " + max + (max >= 15 ? "" : ", not the 15 Astral Ascension Renewed promises"));
                if (CAItems.inspected().contains(m)) ch.add("WARN", "a Hegemony inspection is coming for " + m.getName() + "'s Omega administrator");
            }
            if (Global.getSettings().getModManager().isModEnabled("Planetace_AstralAscension"))
                ch.add("FAIL", "the original Astral Ascension is enabled too; Astral Ascension Renewed refuses to run with it");
        }
    }

    static void vulpoids(List<MarketAPI> mine, Checks ch) {
        for (MarketAPI m : mine) {
            for (Industry ind : m.getIndustries()) {
                if (ind.getSpecialItem() == null) continue;
                String it = ind.getSpecialItem().getId();
                if (it.equals("midas_nanoforge") && m.hasCondition("habitable") && ind instanceof com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry)
                    ch.add("WARN", "a Midas Nanoforge pollutes " + m.getName() + " (habitable); Autopilot never puts one there, so it was placed by hand");
                if (it.equals("vulpoid_biofactory")) ch.add("INFO", "the Vulpoid Bioforge is at " + m.getName() + " (its acceptance story runs from there)");
                if (it.equals("lobster_bioforge") && ind.getId().equals("organfarms")) ch.add("WARN", "the Lobster Bioforge in " + m.getName() + "'s Organ Farm stops its organs");
            }
            if (m.hasCondition("vulpoid_blockade")) ch.add("WARN", m.getName() + " is under anti-vulpoid sanctions (-50% accessibility) until they end");
            if (m.hasCondition("lobsters_growing")) ch.add("INFO", m.getName() + " is growing lobster pens (permanent after a year with the Lobster Bioforge in)");
        }
    }

    /**
     * Roider's Dives and Union HQ: Roider's own supply lines for each mined commodity (base, deposit, the -1 on a
     * Psychoactive world) against Autopilot's copy of its formula (CAMods.minedLines), which is what drifts when Roider
     * rebalances. What the game adds on top isn't Roider's and is only reported: the building's supply bonus (AI core,
     * administrator, improvement, Charter) goes on every output with a positive base or deposit line, even one that nets
     * to 0, and a heavy machinery shortage comes off each (BaseIndustry.supply, applyDeficitToProduction).
     * 0.2.43.1: before, the whole output was checked against the formula with only an AI core added, so Wind's Industrial
     * Planning administrator (+1 ore, +1 organics, +1 transplutonic ore from abundant roids that net to 0) read as 3 units
     * off, and the warning came and went with Wind's heavy machinery shortage.
     */
    static void roider(List<MarketAPI> mine, Checks ch) {
        for (MarketAPI m : mine) {
            for (String id : new String[]{"roider_dives", "roider_union_hq"}) {
                Industry ind = m.getIndustry(id);
                if (ind == null || !ind.isFunctional()) continue;
                float range = id.equals("roider_dives") ? CAMods.divesRange() + (CAMods.charter(m) ? CAMods.charterRange() : 0f)
                        : CAMods.hqRange() + (CAMods.charter(m) ? CAMods.charterRange() : 0f);
                Map<String, MutableCommodityQuantity> sup = new HashMap<String, MutableCommodityQuantity>();
                for (MutableCommodityQuantity q : ind.getAllSupply()) sup.put(q.getCommodityId(), q);
                int total = 0, off = 0, offOwn = 0;
                StringBuilder per = new StringBuilder();
                for (String com : CAMods.MINED) {
                    MutableCommodityQuantity q = sup.get(com);
                    Integer want = CAMods.minedLines(m, com, range), have = q == null ? null : roiderLines(q.getQuantity(), com);
                    int units = q == null ? 0 : Math.max(0, q.getQuantity().getModifiedInt());
                    total += units;
                    if (!linesMatch(CAMods.minedLines(m, com, -1f), have)) offOwn++;   // against the colony's own deposits only
                    if (want == null && have == null) continue;
                    boolean same = linesMatch(want, have);
                    if (!same) off++;
                    per.append(per.length() > 0 ? ", " : "").append(com).append(" ").append(have == null ? "none" : have)
                            .append(same ? "" : " (formula " + (want == null ? "none" : want) + ")");
                }
                int bonus = 0, shortBy = 0;
                StringBuilder from = new StringBuilder();
                try {
                    bonus = ind.getSupplyBonus().getModifiedInt();
                    for (com.fs.starfarer.api.combat.MutableStat.StatMod s : ind.getSupplyBonus().getFlatMods().values())
                        if (Math.round(s.value) != 0 && s.desc != null) from.append(from.length() > 0 ? ", " : "").append(s.desc);
                } catch (Throwable ignored) { }
                try { shortBy = Math.max(0, ind.getMaxDeficit("heavy_machinery").two); } catch (Throwable ignored) { }
                String game = (bonus != 0 ? "; +" + bonus + " supply bonus" + (from.length() > 0 ? " (" + from + ")" : "") + " on each output with a positive line" : "")
                        + (shortBy > 0 ? "; " + shortBy + " heavy machinery short, off each output" : "");
                // 0.3.0.2: lines that match the colony's own deposits only are Roider Union's save-load behaviour (CAMods.areaMined)
                boolean ownOnly = off > 0 && offOwn == 0;
                ch.add(off > 0 && !ownOnly ? "WARN" : ownOnly || shortBy > 0 ? "INFO" : "PASS", ind.getCurrentName() + " at " + m.getName()
                        + (ownOnly ? ": Roider Union is mining only this planet's own deposits (after a save load it drops the area in range; Fixes refreshes it)"
                           : off > 0 ? ": Roider's supply lines differ from Autopilot's formula" : ": Roider's supply lines match Autopilot's formula")
                        + " [" + per + "]; mines " + total + " units" + game);
            }
        }
    }

    /** Roider's own lines on one mined output (roider_dives_&lt;commodity&gt; and its deposit and spore lines), summed; null when it has none. */
    static Integer roiderLines(com.fs.starfarer.api.combat.MutableStat q, String com) {
        String pre = "roider_dives_" + com;
        Integer sum = null;
        for (Map.Entry<String, com.fs.starfarer.api.combat.MutableStat.StatMod> e : q.getFlatMods().entrySet()) {
            String k = e.getKey();
            if (!k.equals(pre) && !k.startsWith(pre + "_")) continue;
            sum = (sum == null ? 0 : sum) + Math.round(e.getValue().value);
        }
        return sum;
    }

    /** Formula against lines: both none, or the same number. */
    static boolean linesMatch(Integer want, Integer have) {
        return want == null ? have == null : have != null && have.intValue() == want.intValue();
    }

    /** Output your faction bans that a building makes without a free port: it exports nothing (CAEngine.illegalUnits). */
    static void illegal(String modId, List<MarketAPI> mine, Checks ch) {
        for (MarketAPI m : mine) {
            for (Industry ind : m.getIndustries()) {
                if (ind.getSpec().getSourceMod() == null || !modId.equals(ind.getSpec().getSourceMod().getId()) || !ind.isFunctional()) continue;
                List<String> banned = new ArrayList<String>();
                for (MutableCommodityQuantity q : ind.getAllSupply()) {
                    if (q.getQuantity().getModifiedInt() <= 0) continue;
                    try { if (!ind.isSupplyLegal(m.getCommodityData(q.getCommodityId()))) banned.add(q.getCommodityId()); } catch (Throwable ignored) { }
                }
                if (!banned.isEmpty()) ch.add("INFO", ind.getCurrentName() + " at " + m.getName() + ": " + banned + " are illegal there"
                        + (m.isFreePort() ? "" : " and it isn't a free port, so they only cover the colony's own needs"));
            }
        }
    }

    static void sbtm(List<MarketAPI> mine, Checks ch) {
        ch.add(CAMods.sbtm() ? "PASS" : "FAIL", "Tech-Mining runs " + (CAMods.sbtm() ? "Slightly Better Tech-Mining's code, valued with its formula"
                : "another mod's code, so Autopilot values it the vanilla way"));
        for (MarketAPI m : mine) {
            if (!m.hasIndustry("techmining")) continue;
            float left = m.getMemoryWithoutUpdate().contains("$core_techMiningMult") ? m.getMemoryWithoutUpdate().getFloat("$core_techMiningMult") : 1f;
            ch.add("INFO", m.getName() + ": ruins " + Math.round(left * 100f) + "% left, worth about " + Misc.getDGSCredits(CAMods.sbtmMonthly(m, left)) + " a month");
        }
    }

    static void drydocks(List<MarketAPI> mine, Checks ch) {
        for (MarketAPI m : mine) {
            Industry dd = m.getIndustry(CAMods.DRYDOCK);
            if (dd == null || !dd.isFunctional()) continue;
            Industry f = CAMods.forge(m);
            int expect = CAMods.drydockOutput(m.getSize(), f != null, CAMods.forgeBonus(f));
            int real = 0;
            try { real = dd.getSupply("ships").getQuantity().getModifiedInt(); } catch (Throwable ignored) { }
            if (dd.getAICoreId() == null && !dd.isImproved() && dd.getAllDeficit().isEmpty())
                ch.add(Math.abs(real - expect) <= 1 ? "PASS" : "WARN", "Secondary Drydocks at " + m.getName() + " makes " + real + " ships; Autopilot's formula says " + expect);
        }
    }

    static void nsi(List<MarketAPI> mine, Checks ch) {
        for (MarketAPI m : mine) for (Industry ind : m.getIndustries()) {
            String left = CAMods.leftToYou(ind.getId());
            if (left != null && !ind.isHidden()) ch.add("INFO", ind.getCurrentName() + " at " + m.getName() + " (built by you; Autopilot leaves it alone: " + left + ")");
        }
        int bps = 0;
        for (MarketAPI m : mine) {
            com.fs.starfarer.api.campaign.CargoAPI c = Misc.getStorageCargo(m);
            if (c == null) continue;
            for (CargoStackAPI st : c.getStacksCopy())
                if (st.isSpecialStack() && st.getSpecialDataIfSpecial() != null && "NSI_IndustryBPItem".equals(st.getSpecialDataIfSpecial().getId())) bps += (int) st.getSize();
        }
        if (bps > 0) ch.add("INFO", bps + " unlearned building blueprints in your colonies' storage (learning one is a right-click in your cargo)");
    }

    // ---------------- report ----------------

    public static JSONArray json() {
        JSONArray a = new JSONArray();
        for (JSONObject o : last) a.put(o);
        return a;
    }
}
