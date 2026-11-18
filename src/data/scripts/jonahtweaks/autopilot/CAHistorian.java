package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.util.Misc;

/**
 * 0.3.10.7: the Historian core (story mode, Tier IV, needs United Aurora Federation and the Autarch administrator package).
 * One Autarch AI Core variant with the Autarch administrator's skills (Industrial Planning, Hypercognition, Autarchy) and
 * UAF's Galactic Historian, so ruin restoration (CARuins) can run where it governs. It governs one managed colony: the one
 * with ruins where a restoration is worth the most, and stays while a restoration runs there. It takes the place of you or a
 * hired administrator (who goes unassigned), never an AI core you placed or the Autarch AI Core, and never where the colony
 * would go under the stability floor. With no ruins left it stays where it is. It leaves when the package is off, story mode
 * is off or the core is dormant. Prepare for removal swaps it for a plain administrator (CARemoval.admins).
 */
public final class CAHistorian {
    private CAHistorian() { }

    static final String FLAG = "$ca_historianAdmin", ABILITY = "historianCore";
    static final String NAME = "Historian AI Core";

    public static boolean is(PersonAPI p) {
        try { return p != null && !p.isPlayer() && p.getMemoryWithoutUpdate().getBoolean(FLAG); } catch (Throwable t) { return false; }
    }

    public static boolean governs(MarketAPI m) { return m != null && is(m.getAdmin()); }

    static PersonAPI person() {
        PersonAPI p = Global.getFactory().createPerson();
        p.setFaction(Factions.PLAYER);
        p.setName(new FullName(NAME, "", FullName.Gender.ANY));
        p.setPortraitSprite(CAAutarch.PORTRAIT);
        p.setRankId(null);
        p.setPostId(null);
        p.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);
        p.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);
        p.getStats().setSkillLevel(CAAutarchSkill.ID, 1);
        try { p.getStats().setSkillLevel(CARuins.SKILL, 1); } catch (Throwable ignored) { }   // UAF's skill
        p.getMemoryWithoutUpdate().set(FLAG, true);
        return p;
    }

    /** Pure: it takes the place of you or a hired administrator, never another person (an AI core, the Autarch AI Core). */
    static boolean replaceable(boolean governedByYou, boolean hiredThere) { return governedByYou || hiredThere; }

    /** Daily, from CASync.autarchAdmins (story mode on or off). */
    public static void daily() {
        boolean on = CARuins.modOn() && CASync.has(ABILITY);
        MarketAPI at = null;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!governs(m)) continue;
            if (!m.isPlayerOwned()) { m.setAdmin(null); continue; }   // captured: the new owner seats its own
            at = m;
        }
        if (!on) { if (at != null) leave(at); return; }
        if (at != null && CARuins.restoring(at)) return;   // stays until the restoration is done
        MarketAPI best = null;
        float bestV = 0f;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (m == at || !CARuins.restorable(m) || !CARuins.auto(m) || CARogue.noCores(m) || CARogue.adminLocked(m)) continue;
            if (CARuins.historian(m.getAdmin())) continue;   // Sophia governs it already
            if (!replaceable(CAItems.governedByYou(m), CAItems.adminAt(m) != null)) continue;
            float v = CARuins.expected(m);
            if (v > bestV) { bestV = v; best = m; }
        }
        if (best == null) return;
        PersonAPI p = person();
        if (!CAStability.canLose(best, CAAutarchAdmin.arrives(best, p), null)) return;
        if (at != null && !leave(at)) return;
        seat(best, p);
    }

    static void seat(MarketAPI m, PersonAPI p) {
        com.fs.starfarer.api.characters.AdminData hired = CAItems.adminAt(m);
        String was = hired != null && hired.getPerson() != null ? hired.getPerson().getNameString() : null;
        p.setId("ca_historian");
        m.setAdmin(p);
        if (hired != null) hired.setMarket(null);
        CALog.did(m.getName() + ": Historian AI Core assigned as administrator" + (was != null ? " in place of " + was + "." : "."));
    }

    /** Leaves m to you if m and every colony you govern stay at or over the floor (as the Autarch AI Core does). */
    static boolean leave(MarketAPI m) {
        float per = CAStability.oneMoreGoverned();
        float delta = -CAStability.adminOwn(m.getAdmin()) - (CAStability.governedPenalty() + per);
        if (!CAStability.canLose(m, delta, null) || !CAStability.canGovernedLose(-per, null)) {
            if (CALog.once("historian:stay:" + m.getId()))
                CALog.quiet(m.getName() + ": Historian AI Core stays as administrator while leaving would take a colony under "
                        + Math.round(CAStability.floor()) + " stability.");
            return false;
        }
        m.setAdmin(Global.getSector().getPlayerPerson());
        CALog.forget("historian:stay:" + m.getId());
        CALog.did(m.getName() + ": Historian AI Core removed as administrator.");
        return true;
    }
}
