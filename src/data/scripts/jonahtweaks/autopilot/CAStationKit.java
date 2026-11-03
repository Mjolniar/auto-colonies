package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipSystemSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

/**
 * What a colony's station brings to a fight, read from its own station fleet: the core hull and every module's fighter
 * wings, guns by size, point defense, shields, and whether its ship system launches drones. Works for any mod's
 * station, since it reads the ships rather than the industry id. Used to value Station Augments (fighter augments on
 * carrier stations, shield and turret augments on gun stations) and the AI core in a station.
 */
public class CAStationKit {
    public String industryId;
    public int tier, modules, wings, small, medium, large, pd, missiles, shielded;
    public boolean drones, read;

    /** Fighters' share of the station's damage, 0-1: a wing counts like a large gun, PD doesn't count. */
    public float fighterShare() {
        float guns = small * 0.25f + medium * 0.5f + large;
        return wings + guns <= 0f ? 0f : wings / (wings + guns);
    }

    /** Midline stations: shields always up, guns that can't track without help (Station Augments' own notes). */
    public boolean midline() { return industryId != null && industryId.endsWith("_mid"); }

    public String describe() {
        if (!read) return "station loadout not readable; counted by its tier";
        return modules + " sections, " + wings + " fighter wings, " + large + " large / " + medium + " medium / " + small + " small guns, "
                + pd + " point defense" + (shielded > 0 ? ", shields on " + shielded : ", no shields") + (drones ? ", drones" : "");
    }

    public static CAStationKit of(MarketAPI m) {
        CAStationKit k = new CAStationKit();
        Industry ind = Misc.getStationIndustry(m);
        if (ind == null) return k;
        k.industryId = ind.getId();
        k.tier = CAPlan.stationTier(ind.getSpec());
        try {
            CampaignFleetAPI fleet = Misc.getStationFleet(m);
            if (fleet == null || fleet.getFleetData() == null) return k;
            List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
            if (members.isEmpty()) return k;
            FleetMemberAPI flag = members.get(0);
            ShipVariantAPI v = flag.getVariant();
            if (v == null) return k;
            // between battles the station fleet is deflated (its guns are only fitted when it fights), so read the stock variant
            ShipVariantAPI stock = v.getHullVariantId() == null ? null : Global.getSettings().getVariant(v.getHullVariantId());
            if (stock != null) v = stock;
            k.add(v);
            for (String slot : v.getStationModules().keySet()) {
                ShipVariantAPI mv = v.getModuleVariant(slot);
                if (mv != null) k.add(mv);
            }
            String sys = flag.getHullSpec().getShipSystemId();
            ShipSystemSpecAPI ss = sys == null ? null : Global.getSettings().getShipSystemSpec(sys);
            k.drones = ss != null && ss.getMaxDrones() > 0;
            k.read = true;
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "couldn't read the station at " + m.getName(), t);
        }
        return k;
    }

    void add(ShipVariantAPI v) {
        modules++;
        ShipHullSpecAPI h = v.getHullSpec();
        if (h.getShieldType() == ShieldAPI.ShieldType.FRONT || h.getShieldType() == ShieldAPI.ShieldType.OMNI) shielded++;
        int fitted = 0;
        for (String w : v.getWings()) if (w != null && !w.isEmpty()) fitted++;
        wings += Math.max(fitted, h.getFighterBays() > 0 && fitted == 0 ? h.getFighterBays() : 0);
        for (String slot : v.getFittedWeaponSlots()) {
            WeaponSpecAPI w = v.getWeaponSpec(slot);
            if (w == null || w.getType() == WeaponAPI.WeaponType.DECORATIVE || w.getType() == WeaponAPI.WeaponType.SYSTEM) continue;
            if (w.getAIHints().contains(WeaponAPI.AIHints.PD) || w.getAIHints().contains(WeaponAPI.AIHints.PD_ONLY)) { pd++; continue; }
            if (w.getType() == WeaponAPI.WeaponType.MISSILE) missiles++;
            if (w.getSize() == WeaponAPI.WeaponSize.LARGE) large++;
            else if (w.getSize() == WeaponAPI.WeaponSize.MEDIUM) medium++;
            else small++;
        }
    }
}
