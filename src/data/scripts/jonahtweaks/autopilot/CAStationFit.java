package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.plugins.AutofitPlugin;
import com.fs.starfarer.api.plugins.impl.CoreAutofitPlugin;
import com.fs.starfarer.api.util.Misc;

import java.util.*;

/**
 * 0.3.11.7: the Station outfitting package (story mode, Tier IV): Auto Outfitting's optimizer fits the stations of your
 * colonies at star fortress tier and up (CAPlan.stationTier 3 or more, the tier 4 stations too), core and every module, with
 * the weapons, fighter wings and hullmods your faction knows. 0.3.13.0: without Auto Outfitting, the game's own autofit fits
 * them (CoreAutofitPlugin with Upgrade on and no randomness), so the package needs no other mod.
 * - No class of Auto Outfitting is used: its autofit plugin is made by name through the game (getInstanceOfScript), its
 *   "optimal fit for every goal" option is ticked by id, and doFit runs with a delegate that offers what you know at no cost (DefaultFleetInflater's
 *   own weapon and wing entries). Its s-mod option stays off and maxSMods is 0: no built-in hullmods, no story points.
 * - The game fits a station again from its stock design every time the station fleet inflates and builds a new station ship
 *   after a build, upgrade, disruption or loss (OrbitalStation), so the fit is kept here and put back on the inflated station
 *   (Watch, every frame, also while paused), marked with the variant tag TAG.
 * - A new fit is planned when the station or what you know changes, and paid at base price for the weapons and wings it
 *   adds over the last fit paid for this colony (CAData.stationFits), plus the usual fee, never below the credit reserve.
 * - 0.3.17.5: planned again only for another station or hull, or when you know more weapons, wings or hullmods than at the
 *   last plan (needsPlan). A plan with the same weapons and wings as the station has is no refit. CAData.stationFits keeps the
 *   most of each weapon and wing ever paid for there, so a fit that goes back to them costs nothing.
 */
public final class CAStationFit {
    private CAStationFit() { }

    static final String ABILITY = "stationFit", AO_MOD = "jonahs_auto_outfitting", OPTION = "jao_optimal_any_goal";
    static final String PLUGIN = "data.scripts.jonahsautooutfitting.JAOAutofitPlugin";
    static final String TAG = "ca_stationfit";

    /** A planned fit for one colony's station (session only: planned again after a load, and paid again only for what changed). */
    static final class Fit { String key; ShipVariantAPI variant; }

    static final Map<String, Fit> FITS = new HashMap<String, Fit>();
    /** 0.3.15.5: a fit planned but not adopted yet (waiting for budget, or the same as the game's fit), kept while its key holds. */
    static final Map<String, Fit> PENDING = new HashMap<String, Fit>();

    static boolean on() {
        try { return CASync.has(ABILITY); } catch (Throwable t) { return false; }   // 0.3.13.0: Auto Outfitting is optional (plan)
    }

    /** The colony's station at star fortress tier or above (working or not), or null. */
    static Industry station(MarketAPI m) {
        Industry st = Misc.getStationIndustry(m);
        return st != null && CAPlan.stationTier(st.getSpec()) >= 3 ? st : null;
    }

    static FleetMemberAPI member(CampaignFleetAPI f) {
        if (f == null) return null;
        for (FleetMemberAPI fm : f.getFleetData().getMembersListCopy()) if (fm.isStation()) return fm;
        return f.getFlagship();
    }

    /** What a fit depends on: the station building and hull, and how many weapons, wings and hullmods you know. */
    static String key(Industry st, FleetMemberAPI fm, FactionAPI f) {
        return st.getId() + "|" + fm.getHullId() + "|" + f.getKnownWeapons().size() + "|" + f.getKnownFighters().size() + "|" + f.getKnownHullMods().size();
    }

    /**
     * Pure (0.3.17.5): whether a fit planned for key `was` must be planned again for `now`: no fit yet, another station
     * building or hull, or more weapons, wings or hullmods known than at that plan. Fewer known, or as many, keeps the fit:
     * a known count that went up and back down every few days re-planned all four stations each time.
     */
    static boolean needsPlan(String now, String was) {
        if (was == null) return true;
        String[] a = now.split("\\|"), b = was.split("\\|");
        if (a.length != 5 || b.length != 5) return !now.equals(was);
        if (!a[0].equals(b[0]) || !a[1].equals(b[1])) return true;
        try {
            for (int i = 2; i < 5; i++) if (Integer.parseInt(a[i]) > Integer.parseInt(b[i])) return true;
        } catch (NumberFormatException e) { return !now.equals(was); }
        return false;
    }

    /** Pure (0.3.17.5): the larger count of each weapon and wing in either (what a colony has paid for over all its fits). */
    static Map<String, Integer> most(Map<String, Integer> a, Map<String, Integer> b) {
        Map<String, Integer> out = new TreeMap<String, Integer>(a);
        for (Map.Entry<String, Integer> e : b.entrySet()) out.merge(e.getKey(), e.getValue(), Math::max);
        return out;
    }

    /** Pure: weapon and wing ids of a fit with their counts (the station core and every module). */
    static Map<String, Integer> ordnance(ShipVariantAPI v) {
        Map<String, Integer> out = new TreeMap<String, Integer>();
        add(out, v);
        for (String slot : v.getStationModules().keySet()) {
            ShipVariantAPI mv = v.getModuleVariant(slot);
            if (mv != null) add(out, mv);
        }
        return out;
    }

    static void add(Map<String, Integer> out, ShipVariantAPI v) {
        for (String slot : v.getNonBuiltInWeaponSlots()) { String w = v.getWeaponId(slot); if (w != null) out.merge("w:" + w, 1, Integer::sum); }
        for (String w : v.getNonBuiltInWings()) if (w != null) out.merge("f:" + w, 1, Integer::sum);
    }

    /** Pure: "w:id=n,f:id=n" (how the paid fit is saved). */
    static String code(Map<String, Integer> o) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : o.entrySet()) { if (sb.length() > 0) sb.append(','); sb.append(e.getKey()).append('=').append(e.getValue()); }
        return sb.toString();
    }

    static Map<String, Integer> decode(String s) {
        Map<String, Integer> out = new TreeMap<String, Integer>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(",")) {
            int eq = part.lastIndexOf('=');
            if (eq <= 0) continue;
            try { out.put(part.substring(0, eq), Integer.parseInt(part.substring(eq + 1))); } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    /** Pure: what `now` has more of than `paid`, with the counts. */
    static Map<String, Integer> added(Map<String, Integer> now, Map<String, Integer> paid) {
        Map<String, Integer> out = new TreeMap<String, Integer>();
        for (Map.Entry<String, Integer> e : now.entrySet()) {
            int more = e.getValue() - (paid.containsKey(e.getKey()) ? paid.get(e.getKey()) : 0);
            if (more > 0) out.put(e.getKey(), more);
        }
        return out;
    }

    static float price(Map<String, Integer> o) {
        float p = 0f;
        for (Map.Entry<String, Integer> e : o.entrySet()) {
            try {
                String id = e.getKey().substring(2);
                float each = e.getKey().startsWith("w:") ? Global.getSettings().getWeaponSpec(id).getBaseValue() : Global.getSettings().getFighterWingSpec(id).getBaseValue();
                p += each * e.getValue();
            } catch (Throwable ignored) { }
        }
        return p;
    }

    // ---------------- daily: plan and pay ----------------

    public static void daily() {
        if (!on()) { FITS.clear(); PENDING.clear(); return; }
        if (!Global.getSector().hasTransientScript(Watch.class)) Global.getSector().addTransientScript(new Watch());
        CAData d = CAData.get();
        CASettings s = CAData.settings();
        FactionAPI f = Global.getSector().getPlayerFaction();
        Set<String> seen = new HashSet<String>();
        boolean planned = false;   // Auto Outfitting can take a few seconds on a station: one new plan a day at most
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            try {
                if (d.colony(m.getId()).mode == CAData.Mode.OFF) continue;
                Industry st = station(m);
                if (st == null) continue;
                seen.add(m.getId());   // kept while it's disrupted or rebuilt, so the fit isn't paid twice
                if (!st.isFunctional() || st.isBuilding()) continue;
                FleetMemberAPI fm = member(Misc.getStationFleet(m));
                if (fm == null) continue;
                String key = key(st, fm, f);
                Fit have = FITS.get(m.getId());
                if (have != null && !needsPlan(key, have.key)) continue;   // 0.3.17.5: only a new station or more known than at the last plan
                String saved = d.stationLayouts().get(m.getId());
                int nl = saved == null ? -1 : saved.indexOf('\n');
                String savedKey = nl > 0 ? saved.substring(0, nl) : null;
                ShipVariantAPI savedFit = have == null && nl > 0 ? rebuild(fm.getVariant(), saved.substring(nl + 1)) : null;
                if (savedFit != null && !needsPlan(key, savedKey)) {   // after a load: the saved fit, no planning
                    Fit fit = new Fit(); fit.key = savedKey; fit.variant = savedFit; FITS.put(m.getId(), fit); continue;
                }
                Fit pend = PENDING.get(m.getId());
                ShipVariantAPI v;
                if (pend != null && key.equals(pend.key)) v = pend.variant;   // 0.3.15.5: planned once per key, not every day it waits
                else {
                    if (planned) continue;
                    planned = true;
                    v = plan(m, fm, f);
                    Fit p = new Fit();
                    p.key = key; p.variant = v;
                    PENDING.put(m.getId(), p);
                }
                if (v == null) continue;
                Map<String, Integer> now = ordnance(v);
                // 0.3.17.5: the fit the station has now (this session's, or the saved one after a load); the same weapons and wings
                // there is no refit, only the new key is kept (a different slot order, vents, capacitors or hullmods don't count)
                ShipVariantAPI cur = have != null ? have.variant : savedFit;
                if (cur != null && now.equals(ordnance(cur))) {
                    PENDING.remove(m.getId());
                    d.stationLayouts().put(m.getId(), key + "\n" + layout(cur));
                    Fit fit = new Fit(); fit.key = key; fit.variant = cur; FITS.put(m.getId(), fit);
                    continue;
                }
                if (cur == null && now.equals(ordnance(fm.getVariant()))) continue;   // Auto Outfitting fell back to the game's fit: nothing to do
                // the first fit pays for what it adds over the station's own fit (free in the game), later ones for what no earlier
                // fit of this colony had (0.3.17.5: every weapon and wing paid for stays paid, so going back to one costs nothing)
                String paidCode = d.stationFits().get(m.getId());
                Map<String, Integer> paid = paidCode != null ? decode(paidCode) : ordnance(fm.getVariant());
                Map<String, Integer> more = added(now, paid);
                float cost = price(more), fee = cost > 0f ? CABudget.constructionFee(s, cost) : 0f;
                if (cost + fee > 0f && (cost + fee > CABudget.available(s) || CABuild.credits() - cost - fee < s.reserve)) {
                    if (CALog.once("stfit:wait:" + m.getId() + ":" + Global.getSector().getClock().getMonth()))
                        CALog.think(m.getName() + ": station outfitting waits for budget (" + Misc.getDGSCredits(cost + fee) + ").");
                    continue;
                }
                if (cost + fee > 0f) CABudget.pay(s, m.getId(), cost, fee, 0f);
                PENDING.remove(m.getId());
                d.stationFits().put(m.getId(), code(most(paid, now)));
                d.stationLayouts().put(m.getId(), key + "\n" + layout(v));
                Fit fit = new Fit();
                fit.key = key; fit.variant = v;
                FITS.put(m.getId(), fit);
                int n = 0;
                for (int c : more.values()) n += c;
                // 0.3.17.5: every refit here changes weapons or wings, so every one is on screen, in a shorter line
                CALog.did(m.getName() + ": " + st.getCurrentName() + " refitted" + (n > 0 ? ", " + n + " new weapons and wings, "
                        + Misc.getDGSCredits(cost + fee) : "") + ".");
            } catch (Throwable t) {
                CADiagnostics.LOG.warn(CADiagnostics.TAG + "station outfitting failed at " + m.getName(), t);
            }
        }
        FITS.keySet().retainAll(seen);   // a station that dropped below the tier, or a colony that's gone, keeps the game's fit
        PENDING.keySet().retainAll(seen);
        d.stationFits().keySet().retainAll(seen);
        d.stationLayouts().keySet().retainAll(seen);
    }

    // ---------------- the saved fit, slot by slot ----------------

    /** "W|part|slot=weapon;F|part|bay=wing;H|part|hullmod=1;V|part|n=vents;C|part|n=caps;" for the core (part "") and each module slot. */
    static String layout(ShipVariantAPI v) {
        StringBuilder sb = new StringBuilder();
        part(sb, "", v);
        for (String slot : v.getStationModules().keySet()) { ShipVariantAPI mv = v.getModuleVariant(slot); if (mv != null) part(sb, slot, mv); }
        return sb.toString();
    }

    static void part(StringBuilder sb, String p, ShipVariantAPI v) {
        for (String s : v.getNonBuiltInWeaponSlots()) { String w = v.getWeaponId(s); if (w != null) item(sb, "W", p, s, w); }
        int builtIn = v.getHullSpec().getBuiltInWings() == null ? 0 : v.getHullSpec().getBuiltInWings().size();
        for (int i = builtIn; i < v.getHullSpec().getFighterBays(); i++) { String w = v.getWingId(i); if (w != null) item(sb, "F", p, "" + i, w); }
        for (String h : v.getNonBuiltInHullmods()) if (!v.getPermaMods().contains(h)) item(sb, "H", p, h, "1");
        item(sb, "V", p, "n", "" + v.getNumFluxVents());
        item(sb, "C", p, "n", "" + v.getNumFluxCapacitors());
    }

    static void item(StringBuilder sb, String type, String p, String k, String val) { sb.append(type).append('|').append(p).append('|').append(k).append('=').append(val).append(';'); }

    /** The station's variant with the saved fit put on it (core and modules), or null when it can't be read. */
    static ShipVariantAPI rebuild(ShipVariantAPI base, String layout) {
        try {
            ShipVariantAPI v = base.clone();
            refit(v);
            clear(v);
            for (String slot : v.getStationModules().keySet()) { ShipVariantAPI mv = v.getModuleVariant(slot); if (mv != null) clear(mv); }
            for (String it : layout.split(";")) {
                if (it.isEmpty()) continue;
                String[] a = it.split("\\|", 3);
                int eq = a.length < 3 ? -1 : a[2].lastIndexOf('=');
                if (eq < 0) continue;
                ShipVariantAPI t = a[1].isEmpty() ? v : v.getModuleVariant(a[1]);
                if (t == null) continue;
                String k = a[2].substring(0, eq), val = a[2].substring(eq + 1);
                if (a[0].equals("W")) { if (Global.getSettings().getWeaponSpec(val) != null) t.addWeapon(k, val); }
                else if (a[0].equals("F")) { if (Global.getSettings().getFighterWingSpec(val) != null) t.setWingId(Integer.parseInt(k), val); }
                else if (a[0].equals("H")) { if (Global.getSettings().getHullModSpec(k) != null) t.addMod(k); }
                else if (a[0].equals("V")) t.setNumFluxVents(Integer.parseInt(val));
                else if (a[0].equals("C")) t.setNumFluxCapacitors(Integer.parseInt(val));
            }
            for (String slot : v.getStationModules().keySet()) { ShipVariantAPI mv = v.getModuleVariant(slot); if (mv != null) v.setModuleVariant(slot, mv); }
            refit(v);
            return v;
        } catch (Throwable t) { return null; }
    }

    /** Takes off what a fit sets: weapons, wings, hullmods (not built-in or s-mods), vents and capacitors. */
    static void clear(ShipVariantAPI v) {
        for (String s : new ArrayList<String>(v.getNonBuiltInWeaponSlots())) v.clearSlot(s);
        int builtIn = v.getHullSpec().getBuiltInWings() == null ? 0 : v.getHullSpec().getBuiltInWings().size();
        for (int i = builtIn; i < v.getHullSpec().getFighterBays(); i++) v.setWingId(i, null);
        for (String h : new ArrayList<String>(v.getNonBuiltInHullmods())) if (!v.getPermaMods().contains(h)) v.removeMod(h);
        v.setNumFluxVents(0);
        v.setNumFluxCapacitors(0);
    }

    /**
     * Auto Outfitting's fit of a copy of this station, or null without its plugin. The game has no public call that picks an
     * autofit plugin, so it's made by name (SettingsAPI.getInstanceOfScript: Auto Outfitting's no-argument constructor).
     */
    static ShipVariantAPI plan(MarketAPI m, FleetMemberAPI fm, FactionAPI f) {
        AutofitPlugin p = null;
        if (Global.getSettings().getModManager().isModEnabled(AO_MOD)) {
            Object o = null;
            try { o = Global.getSettings().getInstanceOfScript(PLUGIN); } catch (Throwable ignored) { }
            if (o instanceof AutofitPlugin) p = (AutofitPlugin) o;
            if (p instanceof CoreAutofitPlugin) ((CoreAutofitPlugin) p).setChecked(OPTION, true);
        }
        if (p == null) {
            // 0.3.13.0: the game's own autofit, the best you know in each slot (Upgrade on) and no random picks
            PersonAPI cmd = fm.getCaptain() != null ? fm.getCaptain() : Global.getSector().getPlayerPerson();
            CoreAutofitPlugin c = new CoreAutofitPlugin(cmd);
            c.setChecked(CoreAutofitPlugin.UPGRADE, true);
            c.setChecked(CoreAutofitPlugin.RANDOMIZE, false);
            p = c;
        }
        p.setRandom(new Random(m.getId().hashCode()));
        ShipVariantAPI v = fm.getVariant().clone();
        refit(v);
        ShipVariantAPI target = v.clone();
        refit(target);   // 0.3.15.5: the target's modules are copies too, never the objects being fitted
        p.doFit(v, target, 0, new Delegate(m, fm, f));
        noSMods(v, target);
        for (String slot : v.getStationModules().keySet())
            if (v.getModuleVariant(slot) != null && target.getModuleVariant(slot) != null) noSMods(v.getModuleVariant(slot), target.getModuleVariant(slot));
        refit(v);
        return v;
    }

    /** No built-in hullmods on a station, ever (the user's rule): any s-mod the fit added comes off again, hullmod and all. */
    static void noSMods(ShipVariantAPI v, ShipVariantAPI before) {
        for (String id : new ArrayList<String>(v.getSMods())) {
            if (before.getSMods().contains(id)) continue;
            v.removePermaMod(id);
            v.removeMod(id);
        }
    }

    /**
     * A variant of its own, saved with the station (core and modules). 0.3.15.5: every module gets its own copy first. Without
     * one, getModuleVariant hands out the game's single stock module variant that every station with that module shares;
     * marking or clearing it emptied the modules of all those stations in battle until a restart.
     */
    static void refit(ShipVariantAPI v) {
        v.setSource(VariantSource.REFIT);
        for (String slot : new ArrayList<String>(v.getStationModules().keySet())) {
            ShipVariantAPI mv = v.getModuleVariant(slot);
            if (mv == null) continue;
            mv = mv.clone();
            mv.setSource(VariantSource.REFIT);
            v.setModuleVariant(slot, mv);
        }
    }

    // ---------------- every frame: put the fit back on an inflated station ----------------

    /** Puts a colony's planned fit on its station once the game has fitted it (the station fleet inflated without our tag). */
    static boolean apply(MarketAPI m, Fit fit) {
        CampaignFleetAPI fleet = Misc.getStationFleet(m);
        if (fleet == null || !fleet.isInflated()) return false;
        FleetMemberAPI fm = member(fleet);
        if (fm == null || fm.getVariant().hasTag(TAG) || !fm.getHullId().equals(fit.variant.getHullSpec().getHullId())) return false;
        ShipVariantAPI v = fit.variant.clone();
        refit(v);
        v.addTag(TAG);
        fm.setVariant(v, false, true);
        fm.updateStats();
        return true;
    }

    public static class Watch implements EveryFrameScript {
        float wait = 0f;
        public boolean isDone() { return false; }
        public boolean runWhilePaused() { return true; }   // the fleet inflates when an encounter opens, before the fight
        public void advance(float amount) {
            wait -= Global.getSector().isPaused() ? 0.05f : amount;
            if (wait > 0f) return;
            wait = 0.25f;
            if (FITS.isEmpty()) return;
            for (Map.Entry<String, Fit> e : FITS.entrySet()) {
                try {
                    MarketAPI m = Global.getSector().getEconomy().getMarket(e.getKey());
                    if (m != null && m.isPlayerOwned()) apply(m, e.getValue());
                } catch (Throwable ignored) { }
            }
        }
    }

    // ---------------- the delegate: what you know, at no cost (paid above) ----------------

    static final class Delegate implements AutofitPlugin.AutofitPluginDelegate {
        final MarketAPI market; final FleetMemberAPI member; final FactionAPI faction;
        final List<AutofitPlugin.AvailableWeapon> weapons = new ArrayList<AutofitPlugin.AvailableWeapon>();
        final List<AutofitPlugin.AvailableFighter> fighters = new ArrayList<AutofitPlugin.AvailableFighter>();
        final List<String> hullmods = new ArrayList<String>();

        Delegate(MarketAPI m, FleetMemberAPI fm, FactionAPI f) {
            market = m; member = fm; faction = f;
            for (String id : f.getKnownWeapons()) {
                try { WeaponSpecAPI w = Global.getSettings().getWeaponSpec(id); if (w != null) weapons.add(new DefaultFleetInflater.AvailableWeaponImpl(w, 99)); } catch (Throwable ignored) { }
            }
            for (String id : f.getKnownFighters()) {
                try { FighterWingSpecAPI w = Global.getSettings().getFighterWingSpec(id); if (w != null) fighters.add(new DefaultFleetInflater.AvailableFighterImpl(w, 99)); } catch (Throwable ignored) { }
            }
            for (String id : f.getKnownHullMods()) {
                try {
                    HullModSpecAPI h = Global.getSettings().getHullModSpec(id);
                    if (h != null && !h.isHidden() && (h.getEffect() == null || h.getEffect().getRequiredItem() == null)) hullmods.add(id);
                } catch (Throwable ignored) { }
            }
        }

        public void fitFighterInSlot(int index, AutofitPlugin.AvailableFighter fighter, ShipVariantAPI variant) { variant.setWingId(index, fighter.getId()); }
        public void clearFighterSlot(int index, ShipVariantAPI variant) { variant.setWingId(index, null); }
        public void fitWeaponInSlot(WeaponSlotAPI slot, AutofitPlugin.AvailableWeapon weapon, ShipVariantAPI variant) { variant.addWeapon(slot.getId(), weapon.getId()); }
        public void clearWeaponSlot(WeaponSlotAPI slot, ShipVariantAPI variant) { variant.clearSlot(slot.getId()); }
        public List<AutofitPlugin.AvailableWeapon> getAvailableWeapons() { return weapons; }
        public List<AutofitPlugin.AvailableFighter> getAvailableFighters() { return fighters; }
        public boolean isPriority(WeaponSpecAPI weapon) { return false; }
        public boolean isPriority(FighterWingSpecAPI wing) { return false; }
        public List<String> getAvailableHullmods() { return hullmods; }
        public void syncUIWithVariant(ShipVariantAPI variant) { }
        public ShipAPI getShip() { return null; }
        public FactionAPI getFaction() { return faction; }
        public boolean isAllowSlightRandomization() { return false; }
        public boolean isPlayerCampaignRefit() { return false; }
        public boolean canAddRemoveHullmodInPlayerCampaignRefit(String modId) { return true; }
        public MarketAPI getMarket() { return market; }
        public FleetMemberAPI getFleetMember() { return member; }
    }
}
