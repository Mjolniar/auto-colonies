package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.AbandonMarketPlugin;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.AbandonMarketPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 0.3.19.2: abandons a weak colony for a better site (plans/2026-11-25-abandon-colonies.md). Setting: New colonies, Relocate
 * weak colonies (CASettings.abandonMode: Off, Ask first, Auto; Off in every save).
 * - When: CAExpansion.run, on a day only the colony limit or the Most colonies setting stops the next expedition, the money
 *   tier is Rich or higher (Money.rich), no expedition is under way and none is waiting for approval. Looked at every
 *   LOOK_DAYS; one colony per PACE_DAYS (CAMark LAST, saved).
 * - Which (blocked): an Auto colony, not the capital, at least MIN_AGE_DAYS old, not under siege, that the game lets you
 *   abandon (the AbandonMarketPlugin the game picks, so Nexerelin's story-critical rule holds), with no locked building
 *   (Colony.locks: yours, or locked on the colony panel) and no wonder or megastructure building (UNIQUE). Not your faction's
 *   top source of a good another colony of yours needs (topSupplier). Its going must make room (roomAfter). The lowest score
 *   goes: the higher of its net income and its site value (CAExpansion.score at size 5, the same numbers as a candidate),
 *   times its location (location()).
 * - For: the best Expansion candidate (CAExpansion.best). It must score RATIO times the colony, add MIN_GAIN a month, and pay
 *   back the abandon cost, the expedition and its first two buildings from the gain in PAYBACK_MONTHS (worthIt). The
 *   expedition must be affordable today (CAExpansion.afford); the abandon cost keeps the credit reserve.
 * - How: Ask first offers a decision (kind "abandon", held a year after a rejection); Auto abandons on the pass. AI cores and
 *   colony items come out of the buildings, then Autopilot Storage, Storage and the other free-transfer tabs (ships too) and
 *   an AI core administrator go to the capital's Storage after the shipping time (Evac, delivered by daily(), no fee: the
 *   game's abandon cost is the evacuation). The game's abandon cost less its shutdown refund is paid (or paid out), then
 *   the game's own steps run (DecivTracker.removeColony, ListenerUtil.reportPlayerAbandonedColony). The plugin's
 *   abandonConfirmed isn't called: it opens the colony list. The next pass sends the expedition.
 */
public final class CARelocate {
    private CARelocate() { }

    /** The candidate must score this many times the colony. */
    static final float RATIO = 2f;
    /** Credits a month the move must add at least. */
    static final float MIN_GAIN = 20000f;
    /** Months the abandon cost, the expedition and its first two buildings must pay back in from the gain. */
    static final float PAYBACK_MONTHS = 36f;
    /** A colony younger than this many days is kept. */
    static final float MIN_AGE_DAYS = 365f;
    /** Days between abandoned colonies. */
    static final float PACE_DAYS = 365f;
    /** Days between looks. */
    static final float LOOK_DAYS = 7f;
    /** A colony's location: another colony in its system, less PER_LY a light-year from the capital, at least MIN_LOCATION. */
    static final float SAME_SYSTEM = 1.3f, PER_LY = 0.02f, MIN_LOCATION = 0.5f;
    /** CAMark key of the last abandoned colony; the decision kind and key prefix. */
    static final String LAST = "relocate:last", KIND = "abandon", KEY = "abandon:";
    /** Industry ids with any of these parts can't be built again: a colony with one is kept. */
    static final String[] UNIQUE = {"aotd_gw", "wonder", "megastructure", "bifrost", "citadel"};
    public static final String[] NAMES = {"Off", "Ask first", "Auto"};

    /** The game day of the last look this session; -99 none. CAReset sets it back. */
    static float lastLook = -99f;

    /** What an abandoned colony's storage holds on its way to Storage at `to` (CAData.evacs, saved). */
    public static class Evac { public String from, to; public long sent; public float days; public CargoAPI cargo; }

    /** A colony weighed for abandoning (not saved). */
    static final class Weak {
        MarketAPI m;
        float net, site = Float.NaN, worth, location, score, cost;
    }

    // ---------------- pure ----------------

    /** Pure: a colony's location: SAME_SYSTEM with another colony of yours in its system, less PER_LY a light-year from the capital, at least MIN_LOCATION. */
    static float location(boolean shared, float lyCapital) {
        return (shared ? SAME_SYSTEM : 1f) * Math.max(MIN_LOCATION, 1f - PER_LY * Math.max(0f, lyCapital));
    }

    /** Pure: a colony's worth a month: the higher of its net income and its site value (NaN: none, a station). */
    static float worth(float net, float site) { return Float.isNaN(site) ? net : Math.max(net, site); }

    /**
     * Pure: whether a candidate is worth abandoning the colony for. score, worth: the colony's; pScore, pValue: the
     * candidate's; cost: the abandon cost, the expedition and its first two buildings.
     */
    static boolean worthIt(float score, float worth, float pScore, float pValue, float cost) {
        float gain = pValue - worth;
        if (!(pScore > 0f) || gain < MIN_GAIN) return false;
        if (pScore < RATIO * Math.max(0f, score)) return false;
        return Math.max(0f, cost) <= gain * PAYBACK_MONTHS;
    }

    /**
     * Pure: room for one more colony once this one goes. yours: you governed it (one fewer you govern); hired: a hired
     * administrator governed it (one more idle). An AI core administrator's colony frees neither.
     */
    static boolean roomAfter(int governed, int max, int admins, int maxAdmins, int idle, int pending, boolean canHire, boolean yours, boolean hired) {
        return CAExpansion.room(yours ? governed - 1 : governed, max, admins, maxAdmins, hired ? idle + 1 : idle, pending, canHire);
    }

    /** Pure: an industry id that can't be built again (UNIQUE). */
    static boolean unique(String id) {
        if (id == null) return false;
        String l = id.toLowerCase();
        for (String u : UNIQUE) if (l.contains(u)) return true;
        return false;
    }

    /**
     * Pure: whether this colony's supply covers another colony's demand that the rest of your colonies wouldn't. supply:
     * this colony's; best: the best of your other colonies'; demand and own: the other colony's demand and supply.
     */
    static boolean covers(int supply, int best, int demand, int own) {
        int other = Math.max(best, own);
        return supply > other && demand > other;
    }

    // ---------------- the look (CAExpansion.run) ----------------

    /** From CAExpansion.run when only the colony limit or Most colonies stops the next expedition. */
    static void look(CAData d, CASettings s, List<MarketAPI> auto, CAExpansion.Money money) {
        if (s.abandonMode <= 0 || CALog.whatIf() || auto == null || auto.isEmpty() || money == null || !money.rich) return;
        if (!d.expandOrders().isEmpty() || d.expandPendingId != null) return;
        float today = CATime.today();
        if (today - lastLook < LOOK_DAYS && today >= lastLook) return;
        lastLook = today;
        Long last = CAMark.at(LAST);
        if (last != null && CAMark.days(last, CAMark.now()) < PACE_DAYS) return;
        CAExpansion.Prospect p = CAExpansion.best(d, s, false);
        if (p == null) return;
        CAExpansion.ExpansionCost c = CAExpansion.costFor(d, s, p, false);
        if (c.why != null || CAExpansion.afford(money, c.total, CAExpansion.lineIndex(s) == 0) != null) return;
        Weak w = weakest(d, s, auto, p);
        if (w == null) return;
        if (s.abandonMode == 1) { offer(w, p); return; }
        String err = abandon(d, s, w.m, p.planetName, "Auto");
        if (err != null) CALog.quiet("Relocation: " + w.m.getName() + " not abandoned (" + err + ")");
    }

    /** The colony to abandon for p, or null. */
    static Weak weakest(CAData d, CASettings s, List<MarketAPI> auto, CAExpansion.Prospect p) {
        CAExpansion.Room r = CAExpansion.room(d, s);
        int max = CASettings.EXPAND_MAX[Math.max(0, Math.min(CASettings.EXPAND_MAX.length - 1, s.expandMax))];
        int count = CAExpansion.colonies().size() + d.expandOrders().size();
        if (max > 0 && count - 1 >= max) return null;
        CAExpansion.Ctx x = CAExpansion.ctx(d, s);
        boolean rich = CASurplus.rich(s);
        Weak best = null;
        for (MarketAPI m : auto) {
            if (blocked(d, m, x.capital) != null) continue;
            if (r.why != null) {
                boolean yours = CAItems.governedByYou(m), hired = !yours && CAItems.adminAt(m) != null;
                if (!roomAfter(r.governed, r.max, r.admins, r.maxAdmins, r.idle, r.pending, s.expandAdmins(), yours, hired)) continue;
            }
            if (topSupplier(m, x.colonies) != null) continue;
            Weak w = weigh(m, s, x, rich);
            if (!worthIt(w.score, w.worth, p.score(), p.value, w.cost + p.total() + p.steps)) continue;
            if (best == null || w.score < best.score) best = w;
        }
        return best;
    }

    /** Null when the game and these rules let m be abandoned, else why not. */
    static String blocked(CAData d, MarketAPI m, MarketAPI capital) {
        if (m == null || !m.isPlayerOwned() || CAEngine.isOutpost(m)) return "it's not your colony now";
        if (capital != null && capital.getId().equals(m.getId())) return "it's the capital";
        CAData.Colony c = d.colony(m.getId());
        if (c.mode != CAData.Mode.AUTO) return "it's not on Auto";
        if (CASiege.held(m)) return "it's under siege";
        if (m.getDaysInExistence() < MIN_AGE_DAYS) return "it's under a year old";
        if (!c.locks.isEmpty()) return "it has a locked building";
        for (Industry ind : m.getIndustries()) if (unique(ind.getId())) return "it has " + ind.getCurrentName();
        AbandonMarketPlugin pg = plugin(m);
        if (pg == null || !pg.canAbandon(m)) return "the game doesn't allow it";
        return null;
    }

    /** Pure: the game's abandon cost, per credits for each size above 2, at least once. */
    static int vanillaCost(int size, int per) { return Math.max(1, size - 2) * per; }

    /** The picked plugin's cost (getAbandonCost is on the game's class, not the interface), else the game's rule. */
    static int abandonCost(AbandonMarketPlugin pg, MarketAPI m) {
        if (pg instanceof AbandonMarketPluginImpl) return ((AbandonMarketPluginImpl) pg).getAbandonCost(m);
        return vanillaCost(m.getSize(), AbandonMarketPluginImpl.COST_PER_SIZE_MINUS_2);
    }

    static AbandonMarketPlugin plugin(MarketAPI m) {
        try { return Global.getSector().getGenericPlugins().pickPlugin(AbandonMarketPlugin.class, m); }
        catch (Throwable t) { return null; }
    }

    /** The good m supplies that another colony of yours needs and the rest wouldn't cover (covers), or null. */
    static String topSupplier(MarketAPI m, List<MarketAPI> mine) {
        for (CommodityOnMarketAPI com : m.getCommoditiesCopy()) {
            if (com.isNonEcon() || com.isPersonnel()) continue;
            int supply = com.getMaxSupply();
            if (supply <= 0) continue;
            String id = com.getId();
            int best = 0;
            for (MarketAPI o : mine) if (o != m) best = Math.max(best, o.getCommodityData(id).getMaxSupply());
            for (MarketAPI o : mine) {
                if (o == m) continue;
                CommodityOnMarketAPI oc = o.getCommodityData(id);
                if (covers(supply, best, oc.getMaxDemand(), oc.getMaxSupply())) return com.getCommodity().getName();
            }
        }
        return null;
    }

    static Weak weigh(MarketAPI m, CASettings s, CAExpansion.Ctx x, boolean rich) {
        Weak w = new Weak();
        w.m = m;
        w.net = m.getNetIncome();
        PlanetAPI pl = m.getPlanetEntity();
        if (pl != null) {
            try { w.site = CAExpansion.score(pl, s, x, rich).value; } catch (Throwable t) { CAStep.warn("relocation", "site value failed at " + m.getName(), t); }
        }
        w.worth = worth(w.net, w.site);
        StarSystemAPI sys = m.getStarSystem();
        Integer n = sys == null ? null : x.mine.get(sys);
        float ly = x.capital == null || x.capital.getPrimaryEntity() == null ? 0f : Misc.getDistanceLY(m.getPrimaryEntity(), x.capital.getPrimaryEntity());
        w.location = location(n != null && n > 1, ly);
        w.score = w.worth * w.location;
        AbandonMarketPlugin pg = plugin(m);
        w.cost = abandonCost(pg, m) - Misc.computeTotalShutdownRefund(m);
        return w;
    }

    // ---------------- Ask first ----------------

    static void offer(Weak w, CAExpansion.Prospect p) {
        final String id = w.m.getId(), planet = p.planetName;
        MarketAPI cap = CACapital.capital();
        String to = cap == null ? "the capital" : cap.getName();
        String site = Float.isNaN(w.site) ? "" : ", and the planet is worth about " + Misc.getDGSCredits(w.site) + " a month as a site";
        String cost = w.cost > 0f ? "Abandoning it costs " + Misc.getDGSCredits(w.cost) + " after the game's refund for its buildings."
                : "Abandoning it pays " + Misc.getDGSCredits(-w.cost) + " back from the game's refund for its buildings.";
        CAApprovals.dropLive(KEY, KEY + id);   // 0.3.33.1: one abandon ask at a time
        CAApprovals.offer(KEY + id, KIND, w.m, "Abandon for a colony at " + planet, Math.max(0f, w.cost), 0f, null, () -> approve(id, planet),
                w.m.getName() + " makes " + Misc.getDGSCredits(w.net) + " a month" + site + ".",
                planet + " in " + p.systemName + " is worth about " + Misc.getDGSCredits(p.value) + " a month, and the expedition there costs "
                        + Misc.getDGSCredits(p.total()) + ".",
                cost,
                "Its AI cores, colony items and storage are shipped to " + to + ".",
                "If you reject it, it won't be offered again for a year.");
    }

    /** Your approval: the checks again, then abandon. Null when done, else why not. */
    static String approve(String marketId, String planet) {
        CAData d = CAData.get();
        MarketAPI m = Global.getSector().getEconomy().getMarket(marketId);
        String no = blocked(d, m, CACapital.capital());
        if (no != null) return "Not abandoned: " + no;
        Long last = CAMark.at(LAST);   // 0.3.33.1: one colony a year, approvals too
        if (last != null && CAMark.days(last, CAMark.now()) < PACE_DAYS) return "Not abandoned: a colony was abandoned less than a year ago";
        return abandon(d, d.settings, m, planet, "your approval");
    }

    // ---------------- abandoning ----------------

    /** Abandons m (by: "Auto" or "your approval"). Null when done, else why not. */
    static String abandon(CAData d, CASettings s, MarketAPI m, String planet, String by) {
        AbandonMarketPlugin pg = plugin(m);
        if (pg == null || !pg.canAbandon(m)) return "the game doesn't allow it";
        try {
            InteractionDialogAPI dlg = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
            if (dlg != null && dlg.getInteractionTarget() != null && dlg.getInteractionTarget().getMarket() == m) return "you're docked there";
        } catch (Throwable ignored) { }
        MarketAPI to = destination(m);
        if (to == null) return "no other colony can take its storage";
        float net = abandonCost(pg, m) - Misc.computeTotalShutdownRefund(m);
        if (net > 0f && CABuild.credits() - net < s.reserve) return "it would go under the credit reserve";
        String name = m.getName();
        // the evacuation: items and cores out of the buildings, then the storage tabs and ships, and an AI core administrator
        CargoAPI cargo = Global.getFactory().createCargo(true);
        cargo.initMothballedShips("player");
        for (Industry ind : new ArrayList<Industry>(m.getIndustries())) CAStores.emptyInto(m, ind);
        take(CAStores.of(m), cargo);
        d.stores().remove(m.getId());
        for (SubmarketAPI sub : m.getSubmarketsCopy()) {
            if (Submarkets.LOCAL_RESOURCES.equals(sub.getSpecId())) continue;
            boolean storage = Submarkets.SUBMARKET_STORAGE.equals(sub.getSpecId());
            if (!storage && (sub.getPlugin() == null || !sub.getPlugin().isFreeTransfer())) continue;
            take(sub.getCargo(), cargo);
        }
        PersonAPI admin = m.getAdmin();
        if (admin != null && admin.isAICore() && admin.getAICoreId() != null) cargo.addCommodity(admin.getAICoreId(), 1);
        AdminData hired = CAItems.adminAt(m);
        if (hired != null) hired.setMarket(null);   // back to your pool
        // the game's cost less its refund, then the game's own steps (AbandonMarketPluginImpl.abandonConfirmed without its screen change)
        if (net > 0f) CABudget.pay(s, null, net, 0f, 0f);
        else CABudget.give(-net);
        DecivTracker.removeColony(m, false);
        ListenerUtil.reportPlayerAbandonedColony(m);
        CAMark.set(LAST, CAMark.now());
        lastLook = CATime.today();
        float days = CAStores.days(CAStores.ly(m.getPrimaryEntity(), to.getPrimaryEntity()));
        boolean ships = cargo.getMothballedShips() != null && !cargo.getMothballedShips().getMembersListCopy().isEmpty();
        if (!cargo.isEmpty() || ships) {
            Evac e = new Evac();
            e.from = name; e.to = to.getId(); e.sent = Global.getSector().getClock().getTimestamp(); e.days = days; e.cargo = cargo;
            d.evacs().add(e);
        }
        String money = net > 0f ? "Cost " + Misc.getDGSCredits(net) : net < 0f ? "Paid out " + Misc.getDGSCredits(-net) : "No cost";
        CALog.did(name + ": Abandoned for a colony at " + planet + " (" + by + "). " + money + "."
                + (cargo.isEmpty() && !ships ? "" : " Its storage reaches " + to.getName() + " in " + Math.round(days) + " days."), true);
        return null;
    }

    /** Where m's storage goes: the capital, else your biggest other colony. */
    static MarketAPI destination(MarketAPI m) {
        MarketAPI cap = CACapital.capital();
        if (cap != null && cap != m && cap.isPlayerOwned()) return cap;
        MarketAPI best = null;
        for (MarketAPI o : CAExpansion.colonies()) if (o != m && (best == null || o.getSize() > best.getSize())) best = o;
        return best;
    }

    /** Moves everything in `from`, ships too, into `into`. */
    static void take(CargoAPI from, CargoAPI into) {
        if (from == null || into == null) return;
        into.addAll(from);
        from.clear();
        FleetDataAPI ships = from.getMothballedShips();
        if (ships == null) return;
        if (into.getMothballedShips() == null) into.initMothballedShips("player");
        for (FleetMemberAPI fm : ships.getMembersListCopy()) {
            ships.removeFleetMember(fm);
            into.getMothballedShips().addFleetMember(fm);
        }
    }

    // ---------------- daily: storage that has arrived ----------------

    /** Daily from CAAuto, whatever the setting: evacuated storage whose time is up goes into Storage there. */
    public static void daily() {
        CAData d = CAData.get();
        if (d.evacs == null || d.evacs.isEmpty()) return;
        for (Iterator<Evac> i = d.evacs.iterator(); i.hasNext(); ) {
            Evac e = i.next();
            if (e == null || e.cargo == null) { i.remove(); continue; }
            if (Global.getSector().getClock().getElapsedDaysSince(e.sent) < e.days) continue;
            MarketAPI to = e.to == null ? null : Global.getSector().getEconomy().getMarket(e.to);
            if (to == null || !to.isPlayerOwned()) to = destination(null);
            CargoAPI st = to == null ? null : Misc.getStorageCargo(to);
            if (st == null) continue;   // no colony of yours: it waits
            take(e.cargo, st);
            i.remove();
            CALog.did(to.getName() + ": Storage from " + e.from + " arrived.", false);
        }
    }
}
