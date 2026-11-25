package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.3.9.4: Prepare for removal. LunaLib's Prepare for removal row (CALunaSync) or the Mod features page's button asks in a
 * campaign dialog (CARemovalPrompt). On Confirm, everything Auto Colonies keeps in the save is taken out:
 * - Autopilot Storage: each colony's store goes to that colony's Storage (your cargo where there is none). Shipments, loads
 *   and stockpile buys on the way arrive there now. Construction crews and crew expeditions on the way are refunded.
 *   Nexerelin expeditions and ship orders stay with Nexerelin and the game's production queue.
 * - Autarch AI Core: each item becomes an Alpha Core where it is (cargo, Storage); one on a market's shelves is removed; the
 *   one in the TriPad becomes an Alpha Core in your cargo; an unfound one leaves its station's salvage.
 * - Autarch administrators: each is replaced by an administrator with the same name and an Alpha Core administrator's skills
 *   (no Autarchy, no AI core id). Not while that takes a colony to 0 stability (blockers).
 * - Enforcer marks on fleets end. Intel, scripts and listeners of Auto Colonies' classes go. The Autopilot Storage tab and
 *   the retired jt_patronage condition leave every market. Market memory keys ($ca_autarch*, $ca_rapidTerra:*) go. Both persistent data entries go.
 * Then the save is flagged (FLAG, a Boolean in sector memory, no mod class) and Auto Colonies does nothing for the rest of
 * the session or on any later load (JTModPlugin). CAData.get and CASync.state hand out scratch maps while off, so nothing
 * written by code still on the stack reaches the save. Turning the setting Off again (resume) starts Auto Colonies: in the
 * same session the saved data and the replaced cores and administrators come back where they still are; after a load it
 * starts fresh with every colony Off.
 * JTRemovalCheck lists every place the source writes into a save and fails when one has no entry in COVERS or when an entry's
 * code is missing here.
 */
public final class CARemoval {
    private CARemoval() { }

    /** Sector memory flag (a Boolean, no mod class): this save was prepared for removal. */
    public static final String FLAG = "$ac_removalDone";
    /** LunaLib's row id (CALunaSync, key "removal"). */
    public static final String LUNA_ROW = "ca_removal";
    /** The Mod features page's button. */
    static final String BUTTON = "jt_ca_removal";
    static final String ALPHA = "alpha_core";
    /** The game's Alpha Core administrator portrait (AICoreAdminPluginImpl). */
    static final String ALPHA_PORTRAIT = "graphics/portraits/portrait_ai2b.png";
    static final String SITE_KEY = "$autarch_core_site";
    static final String PKG = "data.scripts.jonahtweaks.";

    /**
     * What the removal handles, one token per kind of thing the mod writes into a save. JTRemovalCheck maps every write site
     * in the source to one of these (or to external: no mod class or id) and checks each token's code below.
     */
    static final String[] COVERS = {
            "persistent:jt_colony_autopilot",   // CAData and everything in it (CAMark, CALog, settings, orders)
            "persistent:ac_story",              // CASync's story map
            "cargo:stores",                     // Autopilot Storage
            "cargo:shipments", "cargo:loads", "cargo:stockpiles",
            "refund:objectives", "refund:tasc", "refund:expansion",
            "submarket:jt_ca_storage",
            "condition:jt_patronage",
            "item:autarch_core",
            "skill:ca_autarchy", "admin:autarch", "portrait:autarch_core",
            "memory:autarch_admin",             // $ca_autarchSeated, $ca_autarchDeclined (markets); $ca_autarchAdmin (the replaced person)
            "memory:autarch_site",              // $autarch_core_site
            "memory:enforcers",                 // $ac_enf* and the hostility flag on marked fleets
            "memory:rapid_terra",               // $ca_rapidTerra:* on markets (strings)
            "intel:mod",                        // CAIntel, CAStoryIntel, CAAutarchIntel
            "scripts:mod", "listeners:mod",
            "factor:iron_crisis",               // 0.3.16.4: CAIronCrisisFactor on the crisis intel (CATierFive.removeAll)
    };

    /** This session: the save was prepared for removal. Every entry point stops; CAData.get and CASync.state use scratch(). */
    public static volatile boolean off;
    /** The dialog is waiting (LunaLib shows the row On meanwhile). */
    static boolean asked;
    static Map<String, Object> scratch;

    // ---------------- undo, this session only ----------------
    static Object savedData, savedStory;
    /** {CargoAPI, Integer n}: n Autarch AI Cores became n Alpha Cores there. */
    static final List<Object[]> swaps = new ArrayList<Object[]>();
    /** {MarketAPI, the Autarch administrator, its stand-in}. */
    static final List<Object[]> admins = new ArrayList<Object[]>();
    static final Map<String, Object> declined = new HashMap<String, Object>();
    static final Set<String> seated = new HashSet<String>();
    /** market id -> {key, value} of the rapid terraforming marks taken off it. */
    static final Map<String, Map<String, Object>> rapid = new HashMap<String, Map<String, Object>>();
    static int installedAlpha;
    static SectorEntityToken site;
    /** 0.3.9.6 (A4): fleets whose once-per-life enforcer charge flag was taken off (set back on a resume this session). */
    static final List<CampaignFleetAPI> charged = new ArrayList<CampaignFleetAPI>();

    // ---------------- state ----------------

    /** onGameLoad, before anything else: the session's state from the save's flag. */
    public static void onGameLoad() {
        asked = false;
        scratch = null;
        savedData = null;
        savedStory = null;
        swaps.clear();
        admins.clear();
        declined.clear();
        seated.clear();
        rapid.clear();
        installedAlpha = 0;
        site = null;
        charged.clear();
        off = flagged();
    }

    static boolean flagged() {
        try { return Global.getSector().getMemoryWithoutUpdate().getBoolean(FLAG); } catch (Throwable t) { return false; }
    }

    /** CAData.get and CASync.state while off: a map that is not the save's. */
    static Map<String, Object> scratch() {
        if (scratch == null) scratch = new HashMap<String, Object>();
        return scratch;
    }

    /** A load of a save prepared for removal: LunaLib's row shows On, and turning it Off can resume. Nothing else starts. */
    public static void onLoadDormant() {
        CADiagnostics.LOG.info(CADiagnostics.TAG + "this save was prepared for removal; Auto Colonies does nothing in it");
        CALunaSync.reset();
        CALunaSync.register();
        CALunaSync.markRemoval(true);
    }

    // ---------------- asking ----------------

    /** The row turned On, or the page's button: the dialog opens once the campaign is free. */
    public static void ask() {
        if (off) return;
        asked = true;
        try {
            if (!Global.getSector().hasTransientScript(CARemovalPrompt.Watch.class)) Global.getSector().addTransientScript(new CARemovalPrompt.Watch());
        } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "removal dialog failed", t); }
    }

    /** Cancel in the dialog, or a blocker: the row goes back Off. */
    static void cancel() {
        asked = false;
        try { CALunaSync.sync(); } catch (Throwable ignored) { }
    }

    /** A save in LunaLib's menu (CALunaSync.changed). True: nothing else is applied (the save is prepared for removal). */
    static boolean lunaChanged(org.json.JSONObject j) {
        boolean want = j.optBoolean(LUNA_ROW, false);
        if (off) {
            if (!want) resume();
            return true;
        }
        if (want && !asked) ask();
        else if (!want && asked) asked = false;
        return false;
    }

    // ---------------- the stability rule ----------------

    /** Stability m loses when its Autarch administrator loses Autarchy: its +1, and the industry limit's penalty when that puts m over. */
    static float loss(MarketAPI m) {
        PersonAPI p = m.getAdmin();
        if (p == null || p.getStats().getSkillLevel(CAAutarchSkill.ID) <= 0f) return 0f;
        float d = CAAutarchSkill.STABILITY;
        int num = Misc.getNumIndustries(m), max = Misc.getMaxIndustries(m);
        if (num > max - CAAutarchSkill.INDUSTRIES && num <= max) d += Misc.OVER_MAX_INDUSTRIES_PENALTY;
        return d;
    }

    /** Pure: the stability after losing `loss`, raw (before the game's 0 to 10 clamp), stays at 1 or more. */
    static boolean keeps(float raw, float loss) { return loss <= 0f || raw - loss >= 1f; }

    /** The colonies that would fall to 0 stability, as sentences; empty: none. */
    static List<String> blockers() {
        List<String> out = new ArrayList<String>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!CAAutarchAdmin.governs(m) && !CAHistorian.governs(m)) continue;   // 0.3.10.7: the Historian core has Autarchy too
            float l = loss(m);
            if (keeps(m.getStability().getModifiedValue(), l)) continue;
            int num = Misc.getNumIndustries(m), max = Misc.getMaxIndustries(m);
            String s = m.getName() + " would fall to 0 stability without Autarchy.";
            if (num > max - CAAutarchSkill.INDUSTRIES && num <= max) s += " Its industry limit would be " + (max - CAAutarchSkill.INDUSTRIES) + ".";
            out.add(s);
        }
        return out;
    }

    // ---------------- preparing ----------------

    static CargoAPI fleetCargo() { return Global.getSector().getPlayerFleet().getCargo(); }

    /** A colony's Storage, or null when the market is gone, not yours, or has none. */
    static CargoAPI storageFor(String marketId) {
        MarketAPI m = marketId == null ? null : Global.getSector().getEconomy().getMarket(marketId);
        if (m == null || !m.isPlayerOwned()) return null;
        try { return Misc.getStorageCargo(m); } catch (Throwable t) { return null; }
    }

    /** Counts for the report. */
    static final class Report {
        int stacks, stacksToCargo, transit, refunded, cores, coreInstalled, admins, fleets, intel, tabs;
        float credits;
    }

    /** Where something for marketId goes: its Storage, else your cargo (counted). */
    static CargoAPI dest(String marketId, Report r, boolean count) {
        CargoAPI c = storageFor(marketId);
        if (c != null) return c;
        if (count) r.stacksToCargo++;
        return fleetCargo();
    }

    /**
     * Confirm in the dialog. Returns the report as sentences, or null when a colony would fall to 0 stability (nothing
     * changed; blockers() says which). Each step is its own try: one failing leaves the others to run (starsector.log).
     */
    static List<String> prepare() {
        if (off) return new ArrayList<String>();
        if (!blockers().isEmpty()) return null;
        final Report r = new Report();
        final SectorAPI sec = Global.getSector();
        final Map<String, Object> pd = sec.getPersistentData();
        final CAData d = CAData.get();
        savedData = pd.get(CAData.KEY);
        savedStory = pd.get(CASync.KEY);

        step("storage", () -> storage(d, r));
        step("on the way", () -> transit(d, r));
        step("Autarch AI Core", () -> cores(r));
        step("Autarch administrators", () -> admins(r));
        step("enforcer marks", () -> fleets(r));
        step("intel", () -> intel(r));
        step("markets", () -> markets(r));
        step("persistent data", () -> { pd.remove(CAData.KEY); pd.remove(CASync.KEY); });
        step("scripts and listeners", () -> scripts());
        step("rapid research", () -> CARapidResearch.remove());   // 0.3.9.9: Vaults of Knowledge's research speed back to normal
        step("story colony limit", () -> CAStoryLimit.remove());   // 0.3.10.3: the colony limit back to the game's

        sec.getMemoryWithoutUpdate().set(FLAG, true);
        off = true;
        asked = false;
        scratch = null;
        CALunaSync.reset();   // no sync from this save's settings again (beforeGameSave, the panel)
        CALunaSync.markRemoval(true);
        CADiagnostics.LOG.info(CADiagnostics.TAG + "prepared for removal: " + r.stacks + " storage stacks, " + r.transit + " on the way, "
                + Math.round(r.credits) + " credits refunded, " + r.cores + " Autarch AI Cores replaced, " + r.admins + " administrators replaced, "
                + r.fleets + " enforcer marks ended, " + r.intel + " intel entries, " + r.tabs + " storage tabs");
        return report(r);
    }

    interface Step { void run(); }

    static void step(String what, Step s) {
        try { s.run(); }
        catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "removal step failed: " + what, t); }
    }

    /** cargo:stores. Each colony's Autopilot Storage to its Storage; a store of a colony that is gone or not yours to your cargo. */
    static void storage(CAData d, Report r) {
        for (Map.Entry<String, CargoAPI> e : new ArrayList<Map.Entry<String, CargoAPI>>(d.stores().entrySet())) {
            CargoAPI from = e.getValue();
            if (from == null) continue;
            int n = from.getStacksCopy().size();
            List<FleetMemberAPI> ships = from.getMothballedShips() == null ? new ArrayList<FleetMemberAPI>()
                    : from.getMothballedShips().getMembersListCopy();
            if (n == 0 && ships.isEmpty()) continue;
            CargoAPI to = storageFor(e.getKey());
            if (to == null) { to = fleetCargo(); r.stacksToCargo += n; }
            to.addAll(from);
            from.clear();
            for (FleetMemberAPI fm : ships) {
                from.getMothballedShips().removeFleetMember(fm);
                if (to != fleetCargo() && to.getMothballedShips() != null) to.getMothballedShips().addFleetMember(fm);
                else Global.getSector().getPlayerFleet().getFleetData().addFleetMember(fm);
            }
            r.stacks += n;
        }
        d.stores().clear();
    }

    /** cargo:shipments, cargo:loads, cargo:stockpiles, refund:objectives, refund:tasc, refund:expansion. */
    static void transit(CAData d, Report r) {
        for (CAStores.Shipment sh : d.shipments()) {
            CargoAPI to = dest(sh.to, r, true);
            if (sh.commodity != null) to.addCommodity(sh.commodity, sh.qty);
            else if (sh.special != null) to.addSpecial(sh.special, sh.qty);
            r.transit++;
        }
        d.shipments().clear();
        for (CALogistics.Load l : d.loads()) {
            if (l.cargo == null) continue;
            CargoAPI to = dest(l.to, r, true);
            to.addAll(l.cargo);
            // 0.3.9.6 (A1): addAll moves stacks only; the load's ships go to that Storage, or to your fleet with no Storage
            if (l.cargo.getMothballedShips() != null) {
                for (FleetMemberAPI fm : l.cargo.getMothballedShips().getMembersListCopy()) {
                    l.cargo.getMothballedShips().removeFleetMember(fm);
                    if (to != fleetCargo() && to.getMothballedShips() != null) to.getMothballedShips().addFleetMember(fm);
                    else Global.getSector().getPlayerFleet().getFleetData().addFleetMember(fm);
                }
            }
            r.transit++;
        }
        d.loads().clear();
        for (CAStockpile.Buy b : d.stockpiles()) {
            if (b.commodity == null || b.qty <= 0) continue;
            dest(b.marketId, r, true).addCommodity(b.commodity, b.qty);
            r.transit++;
        }
        d.stockpiles().clear();
        for (CAObjectives.Order o : d.objectiveOrders()) {
            r.credits += Math.max(0f, o.paid);
            r.refunded++;
            if (o.core == null) continue;
            CargoAPI c = dest(o.coreFrom, r, false);
            if (CAObjectives.TAP.equals(o.core)) c.addSpecial(new SpecialItemData(CAObjectives.TAP, null), 1f);
            else c.addCommodity(o.core, 1f);
        }
        d.objectiveOrders().clear();
        for (CATascStations.Order o : d.tascOrders()) { r.credits += Math.max(0f, o.paid) + Math.max(0f, o.fee); r.refunded++; }
        d.tascOrders().clear();
        // crew expeditions: the price back, the fee kept (as when one turns back); Nexerelin's carry on under Nexerelin
        List<CAExpansion.Order> keep = new ArrayList<CAExpansion.Order>();
        for (CAExpansion.Order o : d.expandOrders()) {
            if (CAExpansion.NEX.equals(o.method)) { keep.add(o); continue; }
            r.credits += Math.max(0f, o.paid);
            r.refunded++;
        }
        d.expandOrders().clear();
        d.expandOrders().addAll(keep);
        // 0.3.9.6 (A2): a settlement expedition on its way (Random Assortment of Things): its price back, as when it fails
        if (d.settleExpeditionPlanet != null) {
            r.credits += Math.max(0f, d.settleExpeditionCost) + Math.max(0f, d.settleExpeditionFee);   // 0.3.9.7 (G6): the fee too
            r.refunded++;
            d.settleExpeditionPlanet = null;
            d.settleExpeditionAt = 0L;
            d.settleExpeditionDays = 0f;
            d.settleExpeditionCost = 0f;
            d.settleExpeditionFee = 0f;
        }
        if (r.credits > 0f) CABudget.refund(d.settings, null, r.credits);
    }

    /** item:autarch_core, memory:autarch_site. */
    static void cores(Report r) {
        r.cores += swap(fleetCargo());
        // 0.3.9.6 (A3): markets outside the economy too (an abandoned station's Storage, a RAT settlement), and cargo pods
        for (LocationAPI loc : locations())
            for (SectorEntityToken e : loc.getAllEntities())
                if ("cargo_pods".equals(e.getCustomEntityType()) && e.getCargo() != null) r.cores += swap(e.getCargo());
        for (MarketAPI m : allMarkets()) {
            for (SubmarketAPI sub : m.getSubmarketsCopy()) {
                if (CAStoragePlugin.ID.equals(sub.getSpecId())) continue;   // emptied by storage()
                CargoAPI c = sub.getCargo();
                if (c == null) continue;
                if (sub.getPlugin() != null && sub.getPlugin().isFreeTransfer()) r.cores += swap(c);
                else {
                    float n = c.getQuantity(CargoAPI.CargoItemType.SPECIAL, CAAutarch.item());
                    if (n > 0f) c.removeItems(CargoAPI.CargoItemType.SPECIAL, CAAutarch.item(), n);
                }
            }
        }
        if (CASync.installed() && !CASync.pulled()) {
            fleetCargo().addCommodity(ALPHA, 1f);
            installedAlpha = 1;
            r.coreInstalled = 1;
        }
        String id = CASync.text("autarchSite");
        SectorEntityToken e = id == null || "gone".equals(id) ? null : Global.getSector().getEntityById(id);
        if (e != null) {
            BaseSalvageSpecial.ExtraSalvage x = BaseSalvageSpecial.getExtraSalvage(e);
            if (x != null && x.cargo != null && x.cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, CAAutarch.item()) > 0f) {
                x.cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, CAAutarch.item(), x.cargo.getQuantity(CargoAPI.CargoItemType.SPECIAL, CAAutarch.item()));
                site = e;
            }
            e.getMemoryWithoutUpdate().unset(SITE_KEY);
        }
    }

    /** Every Autarch AI Core in c becomes an Alpha Core; returns how many. */
    static int swap(CargoAPI c) {
        float n = c.getQuantity(CargoAPI.CargoItemType.SPECIAL, CAAutarch.item());
        if (n <= 0f) return 0;
        c.removeItems(CargoAPI.CargoItemType.SPECIAL, CAAutarch.item(), n);
        c.addCommodity(ALPHA, n);
        swaps.add(new Object[]{c, Math.round(n)});
        return Math.round(n);
    }

    /** admin:autarch, skill:ca_autarchy, portrait:autarch_core, memory:autarch_admin. */
    static void admins(Report r) {
        try { CATierFive.removeAll(); } catch (Throwable t) { CADiagnostics.LOG.warn(CADiagnostics.TAG + "Tier V cleanup failed", t); }   // 0.3.16.0: Domain forge and Autarch armada
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            MemoryAPI mem = m.getMemoryWithoutUpdate();
            m.getStability().unmodifyFlat(CATierFive.IRON_MOD);   // 0.3.15.0: Tier V Iron stability's floor and its drop penalty
            m.getStability().unmodifyFlat(CATierFive.IRON_DROP_MOD);
            for (String st : CAFleetLimit.PATROL_STATS) m.getStats().getDynamic().getMod(st).unmodifyFlat(CAFleetLimit.PATROL_MOD);   // 0.3.18.2: the fleet limit's patrol caps
            if (mem.contains(CAAutarchAdmin.DECLINED)) { declined.put(m.getId(), mem.get(CAAutarchAdmin.DECLINED)); mem.unset(CAAutarchAdmin.DECLINED); }
            if (mem.getBoolean(CAAutarchAdmin.SEATED)) { seated.add(m.getId()); mem.unset(CAAutarchAdmin.SEATED); }
            PersonAPI p = m.getAdmin();
            if (!CAAutarchAdmin.is(p) && !CAHistorian.is(p)) continue;   // 0.3.10.7: the Historian core too
            PersonAPI s = standIn();
            m.setAdmin(s);
            if (m.getPeopleCopy().contains(p)) { m.removePerson(p); m.addPerson(s); }
            admins.add(new Object[]{m, p, s});
            r.admins++;
        }
    }

    /** The stand-in: the core's name, the game's Alpha Core administrator portrait and skills, no AI core id, no Autarchy. */
    static PersonAPI standIn() {
        PersonAPI s = Global.getFactory().createPerson();
        s.setFaction(Factions.PLAYER);
        s.setName(new FullName(CAAutarchAdmin.NAME, "", FullName.Gender.ANY));
        s.setPortraitSprite(ALPHA_PORTRAIT);
        s.setRankId(null);
        s.setPostId(null);
        s.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);
        s.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);
        return s;
    }

    /** Every location: the star systems and hyperspace. */
    static List<LocationAPI> locations() {
        List<LocationAPI> out = new ArrayList<LocationAPI>();
        for (StarSystemAPI s : Global.getSector().getStarSystems()) out.add(s);
        out.add(Global.getSector().getHyperspace());
        return out;
    }

    /** memory:enforcers. */
    static void fleets(Report r) {
        for (LocationAPI loc : locations()) {
            for (CampaignFleetAPI f : new ArrayList<CampaignFleetAPI>(loc.getFleets())) {
                boolean marked = f.getMemoryWithoutUpdate().contains(CAEnforcers.MEM_KIND);
                if (marked) {
                    CAEnforcers.end(f, loc.getName(), "Auto Colonies prepared for removal");
                    r.fleets++;
                }
                if (f.getMemoryWithoutUpdate().getBoolean(CAEnforcers.MEM_CHARGED)) charged.add(f);   // 0.3.9.6 (A4): set back on a resume
                f.getMemoryWithoutUpdate().unset(CAEnforcers.MEM_CHARGED);   // 0.3.9.5: end() keeps it for the fleet's life
            }
        }
    }

    /** intel:mod. */
    static void intel(Report r) {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();
        for (IntelInfoPlugin i : new ArrayList<IntelInfoPlugin>(mgr.getIntel())) {
            if (!mine(i)) continue;
            mgr.removeIntel(i);
            r.intel++;
        }
    }

    /** submarket:jt_ca_storage, condition:jt_patronage, memory:rapid_terra. Every market in the economy and on any planet or station. */
    static void markets(Report r) {
        for (MarketAPI m : allMarkets()) {
            if (m.hasSubmarket(CAStoragePlugin.ID)) { m.removeSubmarket(CAStoragePlugin.ID); r.tabs++; }
            if (m.hasCondition(data.scripts.jonahtweaks.JTPatronageCondition.ID)) {
                m.removeCondition(data.scripts.jonahtweaks.JTPatronageCondition.ID);
                m.reapplyConditions();
            }
            MemoryAPI mem = m.getMemoryWithoutUpdate();
            for (String k : new ArrayList<String>(mem.getKeys())) {
                if (k == null || !k.startsWith(CARapidTerra.KEY)) continue;
                Map<String, Object> was = rapid.get(m.getId());
                if (was == null) rapid.put(m.getId(), was = new HashMap<String, Object>());
                was.put(k, mem.get(k));
                mem.unset(k);
            }
        }
    }

    /** Every market in the economy and on any planet or station (0.3.9.6: cores() and markets() share it). */
    static Set<MarketAPI> allMarkets() {
        Set<MarketAPI> all = new LinkedHashSet<MarketAPI>(Global.getSector().getEconomy().getMarketsCopy());
        for (LocationAPI loc : locations())
            for (SectorEntityToken e : loc.getAllEntities()) if (e.getMarket() != null) all.add(e.getMarket());
        return all;
    }

    static boolean mine(Object o) { return o != null && o.getClass().getName().startsWith(PKG); }

    /** scripts:mod, listeners:mod. */
    static void scripts() {
        SectorAPI sec = Global.getSector();
        for (EveryFrameScript s : new ArrayList<EveryFrameScript>(sec.getScripts())) if (mine(s)) sec.removeScript(s);
        for (EveryFrameScript s : new ArrayList<EveryFrameScript>(sec.getTransientScripts())) if (mine(s)) sec.removeTransientScript(s);
        for (CampaignEventListener l : new ArrayList<CampaignEventListener>(sec.getAllListeners())) if (mine(l)) sec.removeListener(l);
        ListenerManagerAPI lm = sec.getListenerManager();
        for (Object l : new ArrayList<Object>(lm.getListeners(Object.class))) if (mine(l)) lm.removeListener(l);
        // 0.3.9.6 (A6): no class literals here: CAWarListener implements Nexerelin's interfaces and failed to load without it
    }

    /** The report as sentences for the dialog. */
    static List<String> report(Report r) {
        List<String> out = new ArrayList<String>();
        if (r.stacks > 0) out.add("Moved " + n(r.stacks, "stack") + " from Autopilot Storage to Storage." + (r.stacksToCargo > 0 ? " Some went to your cargo." : ""));
        if (r.transit > 0) out.add("Delivered " + n(r.transit, "shipment") + ".");
        if (r.refunded > 0) out.add("Refunded " + Misc.getDGSCredits(r.credits) + " for crews on the way.");
        int cores = r.cores + r.coreInstalled;
        if (cores > 0) out.add("Replaced " + n(cores, "Autarch AI Core") + " with " + n(cores, "Alpha Core") + ".");
        if (r.admins > 0) out.add("Removed Autarchy from " + n(r.admins, "administrator") + ".");
        if (r.fleets > 0) out.add("Ended " + n(r.fleets, "enforcer mark") + ".");
        return out;
    }

    /** "1 stack", "3 stacks". */
    static String n(int k, String one) { return k + " " + one + (k == 1 ? "" : "s"); }

    // ---------------- resuming ----------------

    /**
     * The row turned Off in a save prepared for removal. In the same session: the saved data comes back (Autopilot Storage
     * and the orders stay as the removal left them), and each Alpha Core and administrator stand-in turns back where it still
     * is. After a load: Auto Colonies starts fresh, every colony Off. Then the startup steps of a load run.
     */
    static void resume() {
        if (!off) return;
        SectorAPI sec = Global.getSector();
        sec.getMemoryWithoutUpdate().unset(FLAG);
        off = false;
        scratch = null;
        asked = false;
        boolean restored = savedData != null;
        Map<String, Object> pd = sec.getPersistentData();
        if (savedData != null) pd.put(CAData.KEY, savedData);
        if (savedStory != null) pd.put(CASync.KEY, savedStory);
        step("cores back", () -> {
            for (Object[] s : swaps) {
                CargoAPI c = (CargoAPI) s[0];
                int k = (int) Math.min((Integer) s[1], c.getCommodityQuantity(ALPHA));
                if (k <= 0) continue;
                c.removeCommodity(ALPHA, k);
                c.addSpecial(CAAutarch.item(), k);
            }
            if (installedAlpha > 0 && fleetCargo().getCommodityQuantity(ALPHA) >= 1f) fleetCargo().removeCommodity(ALPHA, 1f);
            if (site != null) {
                CargoAPI loot = Global.getFactory().createCargo(true);
                loot.addSpecial(CAAutarch.item(), 1);
                BaseSalvageSpecial.addExtraSalvage(site, loot);
                site.getMemoryWithoutUpdate().set(SITE_KEY, true);
            }
        });
        step("administrators back", () -> {
            for (Object[] a : admins) {
                MarketAPI m = (MarketAPI) a[0];
                if (m.getAdmin() != a[2]) continue;
                m.setAdmin((PersonAPI) a[1]);
                if (m.getPeopleCopy().contains(a[2])) { m.removePerson((PersonAPI) a[2]); m.addPerson((PersonAPI) a[1]); }
            }
            for (String id : seated) {
                MarketAPI m = sec.getEconomy().getMarket(id);
                if (m != null) m.getMemoryWithoutUpdate().set(CAAutarchAdmin.SEATED, true);
            }
            for (Map.Entry<String, Object> e : declined.entrySet()) {
                MarketAPI m = sec.getEconomy().getMarket(e.getKey());
                if (m != null) m.getMemoryWithoutUpdate().set(CAAutarchAdmin.DECLINED, e.getValue());
            }
            for (CampaignFleetAPI f : charged) if (f.isAlive()) f.getMemoryWithoutUpdate().set(CAEnforcers.MEM_CHARGED, true);   // 0.3.9.6 (A4)
            for (Map.Entry<String, Map<String, Object>> e : rapid.entrySet()) {   // so a running project is not halved twice
                MarketAPI m = sec.getEconomy().getMarket(e.getKey());
                if (m == null) continue;
                for (Map.Entry<String, Object> k : e.getValue().entrySet()) m.getMemoryWithoutUpdate().set(k.getKey(), k.getValue());
            }
        });
        savedData = null;
        savedStory = null;
        swaps.clear();
        admins.clear();
        declined.clear();
        seated.clear();
        rapid.clear();
        installedAlpha = 0;
        site = null;
        charged.clear();
        data.scripts.jonahtweaks.JTModPlugin.start(false);
        if (!restored) step("colonies Off", () -> {
            CAData d = CAData.get();
            for (MarketAPI m : Misc.getPlayerMarkets(false)) d.colony(m.getId()).mode = CAData.Mode.OFF;
        });
        CADiagnostics.LOG.info(CADiagnostics.TAG + "removal turned off; Auto Colonies runs again" + (restored ? " with this session's data" : " with new data, every colony Off"));
        try { CALog.did("Auto Colonies runs again in this save" + (restored ? "." : ". Every colony is Off."), true); } catch (Throwable ignored) { }
    }

    // ---------------- the Mod features page ----------------

    /** The Removal section of the Empire panel's Mod features page. */
    static void render(TooltipMakerAPI t) {
        t.addPara("It empties Autopilot Storage and takes Auto Colonies out of this save. It'll ask first.", 6f);
        if (asked) t.addPara("Opens when you close this screen.", Misc.getHighlightColor(), 6f);
        else t.addButton("Prepare for removal", BUTTON, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, 200f, 24f, 6f);
    }
}
