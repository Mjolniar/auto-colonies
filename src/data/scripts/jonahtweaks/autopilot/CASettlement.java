package data.scripts.jonahtweaks.autopilot;

import assortment_of_things.frontiers.FrontiersUtils;
import assortment_of_things.frontiers.SettlementData;
import assortment_of_things.frontiers.SettlementManager;
import assortment_of_things.frontiers.data.SettlementFacilitySlot;
import assortment_of_things.frontiers.data.SettlementModifierSpec;
import assortment_of_things.frontiers.data.SiteData;
import assortment_of_things.frontiers.intel.SettlementIntel;
import assortment_of_things.frontiers.plugins.facilities.BaseSettlementFacility;
import assortment_of_things.frontiers.plugins.modifiers.BaseSettlementModifier;
import assortment_of_things.misc.RATSettings;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * Random Assortment of Things "Frontiers" settlement (one per save). Founds it on the best surveyed site once that
 * site's income clears the threshold, then builds the facilities that raise its income (Refining, Trading Post) when
 * they pay for themselves within two years. Founding repeats what RAT's own "Establish the settlement" option does.
 * Utility facilities (shipyard, training, beacons, ...) change how you play, so they're left to you.
 * Only called when RAT is enabled (check CASettlement.MOD first; the constant is inlined, so it doesn't load this class).
 */
public class CASettlement {
    public static final String MOD = "assortment_of_things";
    static final float BASE_COST = 350000f;         // RAT's founding cost before its cost multiplier
    static final float PAD_INCOME = 5000f;          // RAT's Landing Pad, built with every settlement
    static final float PAYBACK_MONTHS = 24f;
    static final String[] INCOME_FACILITIES = {"refinery", "trade_post"};
    static final float[] INCOME_PERCENT = {75f, 20f};

    /** The best place for the settlement on one planet: its best resource and the monthly income before facilities. */
    public static class Site {
        public String planetId, planetName, systemName, resource, resourceName;
        public float income, refinedIncome, distanceLY;
        public boolean refinable;
    }

    static List<Site> cache = null;
    static float cachedAt = -999f;

    static FrontiersUtils fu() { return FrontiersUtils.INSTANCE; }

    /** RAT is enabled and Frontiers is on for this save. */
    public static boolean active() { return fu().isFrontiersActive(); }

    static float costMult() { Float f = RATSettings.INSTANCE.getFrontiersCostMult(); return f == null ? 1f : f; }
    static float incomeMult() { Float f = RATSettings.INSTANCE.getFrontiersIncomeMult(); return f == null ? 1f : f; }
    public static float foundingCost() { return BASE_COST * costMult(); }

    static SettlementData settlement() { return fu().getFrontiersData().getActiveSettlement(); }

    static float days() { return CADay.now(); }

    // ---------------- sites ----------------

    /** RAT's own rules for offering "Establish a new Settlement", plus: surveyed, and unclaimed or your own planet. */
    static boolean eligible(PlanetAPI p) {
        if (p == null || p.isStar() || p.isGasGiant() || p.hasTag("gas_giant") || p.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
        StarSystemAPI sys = p.getStarSystem();
        if (sys == null || sys.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
        if (CALowProfile.remnantSystem(sys)) return false;   // Low profile: a Remnant nexus's crisis only ends with the nexus (0.2.43.0)
        MarketAPI m = p.getMarket();
        if (m == null || m.getSurveyLevel() != MarketAPI.SurveyLevel.FULL) return false;
        if (p.getFaction() == null || !p.getFaction().getRelToPlayer().isAtWorst(RepLevel.INHOSPITABLE)) return false;
        // a faction's world can revoke access if relations sour, so only unclaimed planets and your own colonies
        return m.isPlanetConditionMarketOnly() || m.isPlayerOwned() || p.getFaction().isNeutralFaction();
    }

    /** Monthly income RAT would pay for this resource on this planet, before and after Refining. */
    static Site evaluate(PlanetAPI p) {
        List<MarketConditionAPI> conds = p.getMarket().getConditions();
        Site best = null;
        for (SettlementModifierSpec spec : fu().getModifierSpecs()) {
            if (!spec.isResource()) continue;
            BaseSettlementModifier plugin = fu().getModifierPlugin(spec);
            boolean present = false;
            for (MarketConditionAPI c : conds) if (plugin.includeForCondition(c.getId()) && spec.getConditions().containsKey(c.getId())) present = true;
            if (!present) continue;
            int base = spec.getIncomeForCondition(conds);
            if (base <= 0) continue;
            MutableStat stat = new MutableStat(base);
            stat.modifyFlat("jt_pad", PAD_INCOME);
            float plain = stat.getModifiedValue() * incomeMult();
            stat.modifyPercent("jt_refinery", 75f);
            float refined = stat.getModifiedValue() * incomeMult();
            if (best != null && plain <= best.income) continue;
            best = new Site();
            best.planetId = p.getId(); best.planetName = p.getName(); best.systemName = p.getStarSystem().getNameWithLowercaseTypeShort();
            best.resource = spec.getId(); best.resourceName = spec.getName();
            best.income = plain; best.refinable = spec.getCanBeRefined(); best.refinedIncome = spec.getCanBeRefined() ? refined : plain;
        }
        return best;
    }

    /** Surveyed sites ranked by income, nearest to your colonies first on ties. Rescanned every 30 days or on request. */
    public static List<Site> sites(boolean refresh) {
        float now = days();
        if (!refresh && cache != null && now - cachedAt < 30f) return cache;
        List<MarketAPI> mine = Misc.getPlayerMarkets(false);
        List<Site> out = new ArrayList<Site>();
        for (StarSystemAPI sys : Global.getSector().getStarSystems()) {
            for (PlanetAPI p : sys.getPlanets()) {
                try {
                    if (!eligible(p)) continue;
                    Site s = evaluate(p);
                    if (s == null) continue;
                    s.distanceLY = 999f;
                    for (MarketAPI m : mine) s.distanceLY = Math.min(s.distanceLY, Misc.getDistanceLY(p, m.getPrimaryEntity()));
                    out.add(s);
                } catch (Throwable t) {
                    CADiagnostics.LOG.warn(CADiagnostics.TAG + "settlement site check failed at " + p.getName(), t);
                }
            }
        }
        Collections.sort(out, new Comparator<Site>() {
            public int compare(Site a, Site b) {
                int c = Float.compare(b.refinedIncome, a.refinedIncome);
                return c != 0 ? c : Float.compare(a.distanceLY, b.distanceLY);
            }
        });
        cache = out; cachedAt = now;
        return out;
    }

    // ---------------- founding ----------------

    /**
     * An expedition founds it (0.2.37.0): RAT's dialog founds a settlement when your fleet is at the planet, so from afar
     * Autopilot pays now and the settlement is founded once an expedition from your nearest colony gets there (SETUP_DAYS
     * plus the shipping travel time, a day a light-year). If the planet can't take it any more by then, the cost is refunded.
     */
    static final float SETUP_DAYS = 10f;

    static float expeditionDays(PlanetAPI planet) {
        float best = Float.MAX_VALUE;
        for (MarketAPI m : Misc.getPlayerMarkets(false))
            if (m.getPrimaryEntity() != null) best = Math.min(best, Misc.getDistanceLY(m.getPrimaryEntity(), planet));
        return SETUP_DAYS + CAStores.days(best == Float.MAX_VALUE ? 0f : best);
    }

    /** Daily: an expedition that has arrived founds the settlement (paid already), or refunds the cost if it can't. */
    static void expedition(CAData d) {
        if (d.settleExpeditionPlanet == null) return;
        if (Global.getSector().getClock().getElapsedDaysSince(d.settleExpeditionAt) < d.settleExpeditionDays) return;
        String planetId = d.settleExpeditionPlanet;
        float paid = d.settleExpeditionCost;
        d.settleExpeditionPlanet = null;
        String err = found(planetId, false, false);
        if (err == null) { CALog.did("Settlement: the expedition founded the settlement.", true); return; }
        CABudget.refund(d.settings, null, paid);
        CALog.did("Settlement: the expedition could not found it: " + err + ". " + Misc.getDGSCredits(paid) + " refunded.", true);
    }

    /** Founds the settlement on this planet's best site, exactly as RAT's dialog does. Returns null on success. */
    public static String found(String planetId) { return found(planetId, true, false); }

    /** pay: take the founding cost now; check: only check it can be founded, change nothing. */
    static String found(String planetId, boolean pay, boolean check) {
        if (settlement() != null) return "You already have a settlement";
        SectorEntityToken e = Global.getSector().getEntityById(planetId);
        if (!(e instanceof PlanetAPI) || !eligible((PlanetAPI) e)) return "That planet can't host a settlement any more";
        PlanetAPI planet = (PlanetAPI) e;
        Site want = evaluate(planet);
        if (want == null) return "No resource site on " + planet.getName();
        float cost = foundingCost();
        if (pay && CABuild.credits() < cost) return "Not enough credits (" + Misc.getDGSCredits(cost) + " needed)";

        // pick the site holding that resource (RAT puts every resource the planet has on at least one site)
        List<SiteData> sites = fu().getSiteData(planet);
        if (sites == null || sites.isEmpty()) sites = fu().generateNewSites(planet);
        SiteData site = null;
        for (SiteData sd : sites) if (sd.getModifierIDs().contains(want.resource)) { site = sd; break; }
        if (site == null) return "No site on " + planet.getName() + " has its " + want.resourceName;
        // every modifier must match a planet condition, or RAT's own code would fail; check before paying
        List<BaseSettlementModifier> mods = new ArrayList<BaseSettlementModifier>();
        for (String id : site.getModifierIDs()) {
            SettlementModifierSpec spec = fu().getModifierByID(id);
            String cond = null;
            for (MarketConditionAPI c : planet.getMarket().getConditions()) if (spec.getConditions().containsKey(c.getId())) { cond = c.getId(); break; }
            if (cond == null) return "Site modifier " + spec.getName() + " has no matching condition on " + planet.getName();
            BaseSettlementModifier plugin = fu().getModifierPlugin(spec);
            plugin.setConditionId(cond);
            mods.add(plugin);
        }
        if (check) return null;

        if (pay) CABudget.payPrice(cost);   // 0.3.0.0 (C12 fix): credits move only in CABudget (not booked, as before)
        SectorEntityToken ent = planet.getStarSystem().addCustomEntity("rat_frontiers_settlement_" + Misc.genUID(), planet.getName() + " Settlement",
                "rat_frontiers_settlement", Factions.PLAYER);
        ent.setCircularOrbitPointingDown(planet, 1f, 1f, 360f);
        ent.getMemoryWithoutUpdate().set("$abandonedStation", true);
        MarketAPI market = Global.getFactory().createMarket("rat_station_commander_market", ent.getName(), 3);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        market.setPrimaryEntity(ent);
        market.setFactionId(Factions.PLAYER);
        market.addIndustry(Industries.SPACEPORT);
        market.addSubmarket("rat_settlement_storage");
        ((StoragePlugin) market.getSubmarket("rat_settlement_storage").getPlugin()).setPlayerPaidToUnlock(true);
        market.setPlanetConditionMarketOnly(false);
        ent.setMarket(market);
        ent.getMemoryWithoutUpdate().unset("$tradeMode");
        market.getStability().modifyFlat("rat_base", 10f);
        market.getAccessibilityMod().modifyFlat("rat_base", 0.40f);
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyFlat("rat_base", 200f);

        SettlementData data = new SettlementData(planet, ent);
        data.setLocation(site.getLocation());
        data.setAngleFromCenter(site.getAngleFromCenter());
        data.setDistanceFromCenteer(site.getDistanceFromCenter());
        fu().getFrontiersData().setActiveSettlement(data);
        for (int i = 0; i < 6; i++) {
            SettlementFacilitySlot slot = new SettlementFacilitySlot(data);
            if (i == 0) slot.installNewFacility("landing_pad");
            data.getFacilitySlots().add(slot);
        }
        for (BaseSettlementModifier plugin : mods) {
            plugin.setSettlement(data);
            data.getModifiers().add(plugin);
            if (plugin.getSpec().isResource()) data.getStats().getIncome().setBaseValue(plugin.getSpec().getIncomeForCondition(planet.getMarket().getConditions()));
            else data.getStats().getIncome().setBaseValue(0f);
            plugin.apply();
        }
        SettlementManager manager = new SettlementManager(data);
        Global.getSector().addScript(manager);
        Global.getSector().getListenerManager().addListener(manager);
        data.setMananger(manager);
        SettlementIntel intel = new SettlementIntel(data);
        data.setIntel(intel);
        Global.getSector().getIntelManager().addIntel(intel);
        cache = null;
        CADiagnostics.LOG.info(CADiagnostics.TAG + "founded settlement on " + planet.getName() + " (" + want.resourceName + ") for " + Misc.getDGSCredits(cost));
        return null;
    }

    // ---------------- facilities ----------------

    /** An income facility worth building now: id, name, cost and the monthly income it adds. */
    public static class Facility { public String id, name; public float cost, gain; }

    /** The income facility with the shortest payback that's buildable now, or null. */
    public static Facility nextFacility() {
        SettlementData data = settlement();
        if (data == null) return null;
        boolean freeSlot = false;
        for (SettlementFacilitySlot slot : data.getFacilitySlots()) if (slot.getFacilityID().isEmpty()) freeSlot = true;
        if (!freeSlot) return null;
        Facility best = null;
        for (int i = 0; i < INCOME_FACILITIES.length; i++) {
            String id = INCOME_FACILITIES[i];
            boolean has = false;
            for (SettlementFacilitySlot slot : data.getFacilitySlots()) if (id.equals(slot.getFacilityID())) has = true;
            if (has) continue;
            BaseSettlementFacility plugin = null;
            for (BaseSettlementFacility f : fu().getAllFacilityPlugins()) if (id.equals(f.getID())) plugin = f;
            if (plugin == null || !plugin.canBeBuild()) continue;
            MutableStat income = data.getStats().getIncome();
            float before = income.getModifiedValue();
            income.modifyPercent("jt_probe", INCOME_PERCENT[i]);
            float gain = (income.getModifiedValue() - before) * incomeMult();
            income.unmodify("jt_probe");
            Facility f = new Facility();
            f.id = id; f.name = plugin.getName(); f.cost = plugin.getCost() * costMult(); f.gain = gain;
            if (gain <= 0f || f.cost / gain > PAYBACK_MONTHS) continue;
            if (best == null || f.cost / f.gain < best.cost / best.gain) best = f;
        }
        return best;
    }

    /** Builds an income facility in a free slot. 0.3.0.0 (C12 fix): the caller (execute) pays RAT's price and the fee with CABudget.pay once it's built. */
    public static String buildFacility(String id) {
        SettlementData data = settlement();
        if (data == null) return "No settlement";
        Facility f = nextFacility();
        if (f == null || !f.id.equals(id)) return "That facility isn't the one to build now";
        if (CABuild.credits() < f.cost) return "Not enough credits (" + Misc.getDGSCredits(f.cost) + " needed)";
        for (SettlementFacilitySlot slot : data.getFacilitySlots()) {
            if (!slot.getFacilityID().isEmpty()) continue;
            slot.installNewFacility(id);
            CADiagnostics.LOG.info(CADiagnostics.TAG + "settlement facility " + id + " for " + Misc.getDGSCredits(f.cost));
            return null;
        }
        return "No free facility slot";
    }

    // ---------------- daily pass ----------------

    public static void run(CAData d, CASettings s) {
        if (!active()) return;
        expedition(d);   // even with the setting off: one already paid for still arrives
        if (s.settlementMode == 3 || d.settleExpeditionPlanet != null) return;
        SettlementData data = settlement();
        // a proposal goes stale when you found or build it yourself first
        if ("found".equals(d.settlePendingKind) && data != null) clearPending(d);
        if ("facility".equals(d.settlePendingKind)) { Facility f = data == null ? null : nextFacility(); if (f == null || !f.id.equals(d.settlePendingId)) clearPending(d); }
        boolean approve = s.settlementMode == 1, auto = s.settlementMode == 2;
        if (!approve && !auto) return;   // Advisor: the empire panel shows the recommendation and a button
        String kind, id, name; float cost;
        if (data == null) {
            List<Site> list = sites(false);
            if (list.isEmpty()) return;
            Site best = list.get(0);
            if (best.income < s.settlementMinIncome) return;
            kind = "found"; id = best.planetId; cost = foundingCost();
            name = "Found a settlement on " + best.planetName + " (" + best.resourceName + ", about " + Misc.getDGSCredits(best.income) + "/month)";
        } else {
            Facility f = nextFacility();
            if (f == null) return;
            kind = "facility"; id = f.id; cost = f.cost;
            name = "Build " + f.name + " at the settlement (+" + Misc.getDGSCredits(f.gain) + "/month)";
        }
        if (rejected(kind, id)) return;   // 0.3.3.0: you rejected it within 30 days
        if (approve || cost > CAPresets.projectCap(s)) { propose(d, kind, id, name, cost, approve ? CAPlanner.APPROVE_MODE : "Approval needed: over the per-project cap"); return; }
        float total = cost + CABudget.constructionFee(s, cost);
        if (total > CABudget.available(s)) {
            if (CALog.once("settle:" + kind + ":" + id)) CALog.think("Settlement: " + name + " waits for budget (" + Misc.getDGSCredits(total) + " needed, "
                    + Misc.getDGSCredits(CABudget.available(s)) + " left this month).");
            return;
        }
        execute(d, s, kind, id, name, "Auto");
    }

    static void propose(CAData d, String kind, String id, String name, float cost, String why) {
        if (id.equals(d.settlePendingId) && kind.equals(d.settlePendingKind)) return;
        d.settlePendingKind = kind; d.settlePendingId = id; d.settlePendingName = name; d.settlePendingCost = cost;
        CAApprovals.say("Settlement: Proposal: " + name + " for " + Misc.getDGSCredits(cost) + ". " + why + ".", true);
    }

    /** 0.3.3.0: a rejected proposal is held 30 days (saved mark "settleno:<kind>:<id>"); an older mark is cleared. */
    static boolean rejected(String kind, String id) {
        String key = "settleno:" + kind + ":" + id;
        Long at = CAMark.at(key);
        if (at == null) return false;
        if (CAMark.days(at, CAMark.now()) < 30f) return true;
        CAMark.clear(key);
        return false;
    }

    /** 0.3.3.0: Approve on the waiting proposal: the credit reserve first, then execute. Returns null on success, else why. */
    public static String approve(CAData d, CASettings s) {
        if (d.settlePendingKind == null) return "Nothing to approve";
        float cost = "found".equals(d.settlePendingKind) ? foundingCost() : facilityCost(d.settlePendingId);
        String gate = CAApprovals.reserveGate(cost + CABudget.constructionFee(s, cost));
        if (gate != null) return gate;
        return execute(d, s, d.settlePendingKind, d.settlePendingId, d.settlePendingName, "Approved:");
    }

    /** Pays the fee and starts the founding or facility. Returns null on success. */
    public static String execute(CAData d, CASettings s, String kind, String id, String name, String who) {
        float cost = "found".equals(kind) ? foundingCost() : facilityCost(id);
        float fee = CABudget.constructionFee(s, cost);
        if (CABuild.credits() < cost + fee) return "Not enough credits for it plus the " + Misc.getDGSCredits(fee) + " fee";
        String err;
        if ("found".equals(kind)) {
            err = found(id, false, true);
            if (err == null) {
                PlanetAPI planet = (PlanetAPI) Global.getSector().getEntityById(id);
                d.settleExpeditionPlanet = id;
                d.settleExpeditionAt = Global.getSector().getClock().getTimestamp();
                d.settleExpeditionDays = expeditionDays(planet);
                d.settleExpeditionCost = cost;
                name = name + "; the expedition gets there in " + Math.round(d.settleExpeditionDays) + " days";
            }
        } else err = buildFacility(id);
        if (err != null) {
            // 0.3.0.9: Auto's failed start is starsector.log only; a start you clicked in the empire panel keeps its line
            if ("Auto".equals(who)) CALog.quiet("Settlement: could not start " + name + ": " + err); else CALog.advise("Settlement: could not start " + name + ": " + err);
            return err;
        }
        CABudget.pay(s, null, cost, fee, 0f);   // 0.3.0.0 (C12 fix): price and fee in one payment, after the founding or facility started
        CALog.forget("settle:" + kind + ":" + id);
        if (kind.equals(d.settlePendingKind) && id.equals(d.settlePendingId)) clearPending(d);
        CALog.did("Settlement: " + name + " for " + Misc.getDGSCredits(cost)
                + (fee > 0 ? " + " + Misc.getDGSCredits(fee) + " fee" : "") + "." + ("Auto".equals(who) ? "" : "You".equals(who) ? " Started by you." : " Approved."), true);
        return null;
    }

    static float facilityCost(String id) {
        for (BaseSettlementFacility f : fu().getAllFacilityPlugins()) if (id.equals(f.getID())) return f.getCost() * costMult();
        return 0f;
    }

    public static void clearPending(CAData d) {
        d.settlePendingKind = null; d.settlePendingId = null; d.settlePendingName = null; d.settlePendingCost = 0f;
    }

    // ---------------- empire panel ----------------

    public static void render(com.fs.starfarer.api.ui.TooltipMakerAPI t, CAData d, CASettings s) {
        java.awt.Color hl = Misc.getHighlightColor(), gray = Misc.getGrayColor(), base = Misc.getBasePlayerColor(), dark = Misc.getDarkPlayerColor();
        if (!active()) { t.addPara("Frontiers is off (RAT setting).", gray, 6f); return; }
        CATheme.mode(t, CASettings.SETTLE_NAMES[Math.max(0, Math.min(3, s.settlementMode))]);   // 0.3.0.0 (WS-F3 fix): the mode as a readout
        t.addPara("Founds a settlement at a site making %s a month or more; builds facilities that pay.",
                4f, hl, Misc.getDGSCredits(s.settlementMinIncome));
        if (d.settleExpeditionPlanet != null) {
            float left = Math.max(0f, d.settleExpeditionDays - Global.getSector().getClock().getElapsedDaysSince(d.settleExpeditionAt));
            SectorEntityToken pl = Global.getSector().getEntityById(d.settleExpeditionPlanet);
            t.addPara("Settlement expedition" + (pl != null ? " to " + CALog.esc(pl.getName()) : "") + ": %s days left.", 8f, hl,
                    "" + (int) Math.ceil(left));
        }
        if (d.settlePendingKind != null) {
            t.addPara("Waiting for approval: %s for %s", 8f, hl, d.settlePendingName, Misc.getDGSCredits(d.settlePendingCost));
            com.fs.starfarer.api.ui.ButtonAPI a = t.addButton("Approve", new String[]{"settle", "approve", ""}, hl, dark, com.fs.starfarer.api.ui.Alignment.MID, com.fs.starfarer.api.ui.CutStyle.TL_BR, 120f, 22f, 4f);
            com.fs.starfarer.api.ui.ButtonAPI r = t.addButton("Reject", new String[]{"settle", "reject", ""}, base, dark, com.fs.starfarer.api.ui.Alignment.MID, com.fs.starfarer.api.ui.CutStyle.TL_BR, 120f, 22f, 0f);
            r.getPosition().rightOfTop(a, 6f);
            CALog.endRow(t, a);
        }
        SettlementData data = settlement();
        if (data != null) {
            float income = data.getStats().getIncome().getModifiedValue() * incomeMult();
            StringBuilder fac = new StringBuilder();
            for (SettlementFacilitySlot slot : data.getFacilitySlots()) {
                if (slot.getFacilityID().isEmpty()) continue;
                if (fac.length() > 0) fac.append(", ");
                fac.append(slot.getPlugin() == null ? slot.getFacilityID() : slot.getPlugin().getName()).append(slot.isBuilding() ? " (building)" : "");
            }
            t.addPara("Settlement on %s: about %s per month. Facilities: %s.", 8f, hl, data.getPrimaryPlanet().getName(), Misc.getDGSCredits(income), fac.toString());
            Facility f = nextFacility();
            if (f == null) t.addPara("No facility left to build.", gray, 4f);
            else {
                t.addPara("Next: %s for %s, +%s per month (payback %s months).", 4f, hl, f.name, Misc.getDGSCredits(f.cost), Misc.getDGSCredits(f.gain), "" + Math.round(f.cost / f.gain));
                if (s.settlementMode == 0) {
                    com.fs.starfarer.api.ui.ButtonAPI b = t.addButton("Build " + f.name + " (" + Misc.getDGSCredits(f.cost) + ")", new String[]{"settle", "facility", f.id}, base, dark,
                            com.fs.starfarer.api.ui.Alignment.MID, com.fs.starfarer.api.ui.CutStyle.TL_BR, 260f, 22f, 4f);
                    b.setEnabled(CABuild.credits() >= f.cost);
                }
            }
            return;
        }
        List<Site> list = sites(false);
        if (list.isEmpty()) { t.addPara("No site can host a settlement yet.", gray, 6f); return; }
        t.addPara("Best sites (founding %s):", 8f, hl, Misc.getDGSCredits(foundingCost()));
        for (int i = 0; i < Math.min(3, list.size()); i++) {
            Site st = list.get(i);
            t.addPara("%s. %s, %s: %s, about %s per month" + (st.refinable ? " (%s with Refining)" : "%s") + ", %s ly away", 4f, hl,
                    "" + (i + 1), st.planetName, st.systemName, st.resourceName, Misc.getDGSCredits(st.income),
                    st.refinable ? Misc.getDGSCredits(st.refinedIncome) : "", st.distanceLY >= 999f ? "-" : "" + Math.round(st.distanceLY));
            if (s.settlementMode == 0 && i == 0) {
                com.fs.starfarer.api.ui.ButtonAPI b = t.addButton("Found on " + st.planetName + " (" + Misc.getDGSCredits(foundingCost()) + ")", new String[]{"settle", "found", st.planetId}, base, dark,
                        com.fs.starfarer.api.ui.Alignment.MID, com.fs.starfarer.api.ui.CutStyle.TL_BR, 260f, 22f, 4f);
                b.setEnabled(CABuild.credits() >= foundingCost());
            }
        }
        if (list.get(0).income < s.settlementMinIncome) t.addPara("None reaches your threshold yet.", gray, 4f);
    }

    /** Empire panel buttons: {"settle", action, id}. Returns a message for the log, or null. */
    public static void handle(String action, String id, CAData d, CASettings s) {
        if ("reject".equals(action)) {
            if (d.settlePendingName != null) CALog.did("Settlement: Proposal rejected: " + d.settlePendingName + ".");
            if (d.settlePendingKind != null) CAMark.set("settleno:" + d.settlePendingKind + ":" + d.settlePendingId, CAMark.now());   // 0.3.3.0: held 30 days
            clearPending(d);
            return;
        }
        if ("approve".equals(action)) {
            if (d.settlePendingKind == null) return;
            String err = approve(d, s);
            if (err != null) CALog.advise("Settlement: " + err + ".", true);
            return;
        }
        if ("found".equals(action)) {
            Site st = null;
            for (Site x : sites(false)) if (x.planetId.equals(id)) st = x;
            execute(d, s, "found", id, "Found a settlement on " + (st == null ? id : st.planetName), "You");
        } else if ("facility".equals(action)) {
            Facility f = nextFacility();
            execute(d, s, "facility", id, "Build " + (f == null ? id : f.name) + " at the settlement", "You");
        }
    }
}
