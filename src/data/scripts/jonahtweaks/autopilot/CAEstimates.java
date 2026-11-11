package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Estimated effects for mod buildings whose code isn't simulated. Every line they produce is marked "(estimated)".
 * Effects: fx[0] credits/month, [1] stability, [2] accessibility, [3] defense, [4] fleet, [5] quality, [6] growth.
 */
public class CAEstimates {
    static final Set<String> IDS = new HashSet<String>(Arrays.asList(
            "commerce", "ii_orbitalstation", "ii_battlestation", "ii_starfortress", "ii_stellacastellum",
            "IndEvo_AdInfra", "IndEvo_ComArray", "IndEvo_IntArray", "IndEvo_senate", "IndEvo_embassy", "IndEvo_Academy",
            "IndEvo_dryDock", "IndEvo_EngHub", "IndEvo_ReqCenter", "IndEvo_PrivatePort", "IndEvo_Museum", "IndEvo_PetStore",
            "IndEvo_Warehouse", "IndEvo_ScrapYard", "IndEvo_SupCom", "IndEvo_pirateHaven", "IndEvo_AdAssem", "IndEvo_AdManuf", "IndEvo_ComForge",
            "IndEvo_LightPalace", "IndEvo_DarkPalace", "IndEvo_Memorial", "IndEvo_LeisureOrbitals", "IndEvo_Megachurch",
            "IndEvo_IcePalace", "IndEvo_LavaFortress", "IndEvo_CloudPainter",
            "ix_surveillance_center",
            "tme_agricultural_laboratory", "tme_atmosphere_regulator", "tme_element_synthesizer", "tme_geomorphology_station",
            "tme_mineral_replicator", "tme_terrestrial_engine", "tme_unification_center",
            "aotd_monoculture_plots", "aotd_extractive_operation", "aotd_smelting", "aotd_light_production", "aotd_trade_outpost",
            "aotd_research_facility", "aotd_bfc_ind"));

    public static boolean has(String id) { return IDS.contains(id); }

    /**
     * Vaults of Knowledge's Research Facility: research only runs while you own one, and it costs 10,000 a month. With
     * none, it's worth half of what your colonies' plans wait on in research (CAResearch.needs, from the last ranking),
     * or 5,000 a month with research but no plan need; a second one only adds 10% speed, so it's worth nothing.
     */
    static void researchFacility(CAEngine.Pick p) {
        boolean have = false;
        for (MarketAPI o : com.fs.starfarer.api.util.Misc.getPlayerMarkets(false))
            if (o.hasIndustry("aotd_research_facility") || o.hasIndustry("aotd_blacksite")) have = true;
        p.fx[0] -= 10000f;
        if (have) { note(p, "You have one already; another adds only 10% research speed, for 10,000 a month (surplus research builds more when it pays)"); return; }
        float waiting = 0f;
        int steps = 0;
        if (com.fs.starfarer.api.Global.getSettings().getModManager().isModEnabled(CAResearch.MOD))
            for (CAResearch.Need n : CAResearch.needs) { waiting += n.value; steps++; }
        // research's own ranking only runs once there's a facility (or with surplus research on), so with none it knew of no
        // plan step waiting and the first facility was never worth building (0.2.38.0): count the research-locked steps directly
        int locked = steps > 0 ? 0 : CAPlan.lockedSteps();
        float v = steps > 0 ? 0.5f * waiting : locked > 0 ? 10000f * Math.min(5, locked) : 5000f;
        p.fx[0] += v;
        note(p, steps > 0 ? "Research needs one: " + steps + " plan steps at your colonies wait on research (" + CAEngine.credits(v) + "), 10,000 a month"
                : locked > 0 ? "Research needs one: " + locked + " plan steps at your colonies are locked by research; 10,000 a month"
                : "Research needs one, though no plan step waits on it now; 10,000 a month");
    }

    static void note(CAEngine.Pick p, String text) { p.lines.add(text + " (estimated)"); }

    static boolean anyCond(MarketAPI m, String... ids) { for (String id : ids) if (m.hasCondition(id)) return true; return false; }

    static int count(MarketAPI m, String prefix) {
        int n = 0;
        for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : m.getConditions()) if (c.getId().startsWith(prefix)) n++;
        return n;
    }

    public static void apply(MarketAPI m, String id, CAEngine.Pick p, java.util.Map<String, Integer> deficit) {
        float gross = Math.max(0f, m.getGrossIncome());
        int s = m.getSize();
        if (id.startsWith("aotd_")) { aotd(m, id, p, deficit, gross, s); return; }
        if (id.equals("commerce")) {
            p.fx[0] += 0.25f * gross; p.fx[1] -= 2f;
            note(p, "Income about +25% (" + CAEngine.credits(0.25f * gross) + "), some stability lost");
        } else if (id.equals("ii_orbitalstation")) {
            p.fx[3] += 0.5f; p.fx[4] += 0.3f; note(p, "Imperium station: ground defense x1.5 and a station fleet");
        } else if (id.equals("ii_battlestation")) {
            p.fx[3] += 1f; p.fx[4] += 0.6f; note(p, "Imperium battlestation: ground defense x2 and a strong station fleet");
        } else if (id.equals("ii_starfortress") || id.equals("ii_stellacastellum")) {
            p.fx[3] += 2f; p.fx[4] += 1f; note(p, "Imperium fortress: ground defense x3 and the strongest station fleet");
        } else if (id.equals("IndEvo_AdAssem") || id.equals("IndEvo_AdManuf") || id.equals("IndEvo_ComForge")) {
            float per = id.equals("IndEvo_AdAssem") ? 900f : id.equals("IndEvo_AdManuf") ? 1500f : 2200f;
            p.fx[0] += per * s; note(p, "Produces the commodity of a VPC chip from Autopilot Storage");
        } else if (id.equals("IndEvo_AdInfra")) {
            p.fx[0] += 1500f * s; note(p, "Boosts output of matching industries in this system");
        } else if (id.equals("IndEvo_ComArray")) {
            p.fx[4] += 0.25f; note(p, "Larger local fleets, shared across relays in the system");
        } else if (id.equals("IndEvo_IntArray")) {
            p.fx[4] += 0.35f; note(p, "Shares fleet size between allied star systems");
        } else if (id.equals("IndEvo_senate")) {
            p.fx[1] += 1f; note(p, "Unlocks edicts for this system; you choose them");
        } else if (id.equals("IndEvo_embassy")) {
            p.fx[0] += 1000f; note(p, "Improves relations with a faction you pick");
        } else if (id.equals("IndEvo_Academy")) {
            p.fx[0] += 2000f; note(p, "Trains officers and administrators");
        } else if (id.equals("IndEvo_PrivatePort")) {
            p.fx[2] += 0.05f; note(p, "Ships cargo anywhere for a fee");
        } else if (id.equals("IndEvo_ScrapYard")) {
            p.fx[0] += 1200f * s; p.fx[5] -= 0.1f; note(p, "More production from salvaged hulls, lower ship quality");
        } else if (id.equals("IndEvo_SupCom")) {
            p.fx[0] += 1000f * s; note(p, "Research output from shackled AI");
        } else if (id.equals("IndEvo_pirateHaven")) {
            p.fx[0] += 1500f * s; p.fx[1] -= 1f; note(p, "Privateer income; adds a lawless subpopulation");
        } else if (id.startsWith("IndEvo_") && (id.endsWith("Palace") || id.equals("IndEvo_Memorial") || id.equals("IndEvo_LeisureOrbitals")
                || id.equals("IndEvo_Megachurch") || id.equals("IndEvo_LavaFortress") || id.equals("IndEvo_CloudPainter"))) {
            p.fx[1] += 1f; p.fx[6] += 0.5f; note(p, "World symbol: stability and prestige");
        } else if (id.startsWith("IndEvo_")) {
            p.fx[0] += 500f; note(p, "Utility building for you, little effect on the colony itself");
        } else if (id.equals("ix_surveillance_center")) {
            p.fx[3] += 0.3f; p.fx[1] += 1f; note(p, "Surveillance: better defense and stability");
        } else if (id.equals("tme_mineral_replicator")) {
            float v = (m.hasIndustry("mining") ? 3000f : 800f) * Math.max(1, s - 2);
            p.fx[0] += v; note(p, "Lets you add or enrich ore and organics deposits here");
        } else if (id.equals("tme_element_synthesizer")) {
            float v = (m.hasIndustry("mining") ? 3000f : 800f) * Math.max(1, s - 2);
            p.fx[0] += v; note(p, "Lets you add or enrich volatiles and transplutonic ore deposits");
        } else if (id.equals("tme_agricultural_laboratory")) {
            float v = m.hasCondition("habitable") ? (m.hasIndustry("farming") ? 2500f : 1000f) * Math.max(1, s - 2) : 0f;
            p.fx[0] += v; note(p, v > 0 ? "Lets you improve farmland" : "Needs a habitable world to do anything");
        } else if (id.equals("tme_atmosphere_regulator")) {
            int bad = (anyCond(m, "no_atmosphere", "toxic_atmosphere", "dense_atmosphere") ? 2 : 0) + (m.hasCondition("thin_atmosphere") ? 1 : 0);
            p.fx[0] += 1500f * bad * Math.max(1, s - 2); note(p, bad > 0 ? "Can remove a hazardous atmosphere" : "Atmosphere is already fine");
        } else if (id.equals("tme_terrestrial_engine")) {
            int bad = (anyCond(m, "high_gravity", "extreme_tectonic_activity") ? 2 : 0) + (anyCond(m, "low_gravity", "tectonic_activity") ? 1 : 0);
            p.fx[0] += 1500f * bad * Math.max(1, s - 2); note(p, bad > 0 ? "Can remove gravity or tectonic hazards" : "No gravity or tectonic hazards to fix");
        } else if (id.equals("tme_geomorphology_station")) {
            int bad = (anyCond(m, "irradiated") ? 2 : 0) + (anyCond(m, "pollution", "inimical_biosphere", "decivilized") ? 1 : 0);
            p.fx[0] += 1500f * bad * Math.max(1, s - 2) + 500f; note(p, bad > 0 ? "Can remove radiation, pollution or a hostile biosphere" : "Can add water or lobster pens on suitable worlds");
        } else if (id.equals("tme_unification_center")) {
            p.fx[0] += 2000f; note(p, "Turns a qualifying colony into a special world type");
        }
    }

    /**
     * Ashes of the Domain starter buildings, read from their apply() code: smaller versions of the vanilla buildings
     * they upgrade into (Monoculture Plots -> Farming, Extractive Operation -> Mining, Smelting -> Refining,
     * Light Production -> Light Industry, Trade Outpost -> Commerce).
     */
    static void aotd(MarketAPI m, String id, CAEngine.Pick p, java.util.Map<String, Integer> deficit, float gross, int s) {
        if (id.equals("aotd_research_facility")) { researchFacility(p); return; }
        // the Bifrost Control Center does nothing on its own: it's built as the wonders plan step (CAWonders.stepsFor), which
        // gives it its priority. Before 0.2.38.0 its spec's hide_in_codex tag kept it from being rated at all, so that step
        // could never be built and the Bifrost plan waited forever
        if (id.equals(CAWonders.BFC)) { note(p, "Lets Bifrost Gates be built; only while it's the wonders plan's step"); return; }
        if (id.equals("aotd_monoculture_plots")) {
            int mod = 0;
            for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : m.getConditions())
                if ("food".equals(CAEngine.DEPOSIT.get(c.getId()))) mod = CAEngine.DEPOSIT_MOD.get(c.getId());
            CAEngine.supply(p, "food", Math.max(1, 2 + mod), deficit);
            CAEngine.demand(p, "heavy_machinery", 2, deficit);
        } else if (id.equals("aotd_extractive_operation")) {
            for (com.fs.starfarer.api.campaign.econ.MarketConditionAPI c : m.getConditions()) {
                String com = CAEngine.DEPOSIT.get(c.getId());
                if (com == null || com.equals("food")) continue;
                CAEngine.supply(p, com, s - 2 + CAEngine.BASE_MOD.get(com) + CAEngine.DEPOSIT_MOD.get(c.getId()), deficit);
            }
            CAEngine.demand(p, "heavy_machinery", s - 2, deficit);
            CAEngine.demand(p, "drugs", s - 2, deficit);
        } else if (id.equals("aotd_smelting")) {
            CAEngine.supply(p, "metals", s - 2, deficit);
            CAEngine.demand(p, "ore", s - 2, deficit);
            CAEngine.demand(p, "heavy_machinery", s - 2, deficit);
        } else if (id.equals("aotd_light_production")) {
            CAEngine.supply(p, "domestic_goods", s - 1, deficit);
            CAEngine.demand(p, "organics", s - 1, deficit);
        } else if (id.equals("aotd_trade_outpost")) {
            p.fx[0] += 0.1f * gross;
            note(p, "Income about +10% (" + CAEngine.credits(0.1f * gross) + ")");
            return;
        }
        note(p, "Ashes of the Domain starter building: less output than the vanilla building it upgrades into");
    }
}
