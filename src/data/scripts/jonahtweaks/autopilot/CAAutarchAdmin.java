package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.util.Misc;

/**
 * 0.3.7.9: Autarch administrator (story mode, Tier III). The Autarch AI Core governs each managed colony (Approve or Auto)
 * that you govern yourself, where that is worth more than you governing it (CAItems.adminWorth, the colony limit counted)
 * and the colony stays at or over the stability floor (CAStability). It has an Alpha Core administrator's skills (Industrial
 * Planning, Hypercognition) and no AI core id: the colony screen has no core to hand back, and it adds no inspection points
 * or Pather interest. One person per colony, marked in its memory.
 *
 * Once seated it stays. It leaves a colony that is no longer managed, and every colony when the package is not bought,
 * story mode is off or the core is dormant: one colony a day, and never while that would take the colony, or a colony you
 * govern, under the floor. An administrator you place replaces it. Where you govern a colony yourself in its place, it
 * does not return until that colony leaves management.
 */
public final class CAAutarchAdmin {
    private CAAutarchAdmin() { }

    static final String FLAG = "$ca_autarchAdmin", SEATED = "$ca_autarchSeated", DECLINED = "$ca_autarchDeclined";
    static final String NAME = "Autarch AI Core";

    /** The Autarch AI Core as an administrator (not the player, marked in its memory). */
    public static boolean is(PersonAPI p) {
        try { return p != null && !p.isPlayer() && p.getMemoryWithoutUpdate().getBoolean(FLAG); } catch (Throwable t) { return false; }
    }

    public static boolean governs(MarketAPI m) { return m != null && is(m.getAdmin()); }

    /** A new administrator person: the core's name and portrait, an Alpha Core administrator's skills, no AI core id. */
    static PersonAPI person() {
        PersonAPI p = Global.getFactory().createPerson();
        p.setFaction(Factions.PLAYER);
        p.setName(new FullName(NAME, "", FullName.Gender.ANY));
        p.setPortraitSprite(CAAutarch.PORTRAIT);
        p.setRankId(null);
        p.setPostId(Ranks.POST_ADMINISTRATOR);
        p.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);
        p.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);
        p.getMemoryWithoutUpdate().set(FLAG, true);
        return p;
    }

    /** Daily, from CASync.daily (story mode on or off): its seats are checked, one is left if it must, and new ones are taken. */
    public static void daily() {
        boolean on = CASync.has("autarchAdmin");   // false with story mode off, while dormant, or not bought
        boolean left = false;
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            MemoryAPI mem = m.getMemoryWithoutUpdate();
            boolean seated = mem.getBoolean(SEATED), here = governs(m);
            if (!seated && !here) {
                if (mem.contains(DECLINED) && !CASync.manages(m)) mem.unset(DECLINED);
                continue;
            }
            if (!m.isPlayerOwned()) { mem.unset(SEATED); continue; }   // captured or autonomous: the new owner seats its own
            if (seated && !here) {   // someone else governs it now
                mem.unset(SEATED);
                if (m.getAdmin() == null || m.getAdmin().isPlayer()) mem.set(DECLINED, true);   // you took it over yourself
                continue;
            }
            if (!seated) mem.set(SEATED, true);
            if (!left && (!on || !CASync.manages(m))) left = leave(m);
        }
        if (!on) return;
        PersonAPI p = null;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!CASync.manages(m) || !CAItems.governedByYou(m) || CARogue.noCores(m)) continue;
            if (m.getMemoryWithoutUpdate().getBoolean(DECLINED)) continue;
            if (p == null) p = person();
            if (CAItems.adminWorth(m, p) <= 0f) continue;
            if (!CAStability.canLose(m, CAStability.adminArrives(CAStability.adminOwn(p), CAStability.governedPenalty()), null)) continue;
            seat(m, p);
            p = null;
        }
    }

    static void seat(MarketAPI m, PersonAPI p) {
        p.setId("ca_autarch_" + m.getId());
        m.setAdmin(p);
        m.getMemoryWithoutUpdate().set(SEATED, true);
        m.getMemoryWithoutUpdate().unset(DECLINED);
        CALog.did(m.getName() + ": Autarch AI Core assigned as administrator.");
    }

    /**
     * Leaves m to you if m and every colony you govern stay at or over the floor: m loses the core's own stability and takes
     * the colony limit's number with one more colony governed by you; the others each pay one more colony's share.
     */
    static boolean leave(MarketAPI m) {
        float per = CAStability.oneMoreGoverned();
        float delta = -CAStability.adminOwn(m.getAdmin()) - (CAStability.governedPenalty() + per);
        if (!CAStability.canLose(m, delta, null) || !CAStability.canGovernedLose(-per, null)) {
            if (CALog.once("autarchAdmin:stay:" + m.getId()))
                CALog.quiet(m.getName() + ": Autarch AI Core stays as administrator while leaving would take a colony under "
                        + Math.round(CAStability.floor()) + " stability.");
            return false;
        }
        m.setAdmin(Global.getSector().getPlayerPerson());
        m.getMemoryWithoutUpdate().unset(SEATED);
        CALog.forget("autarchAdmin:stay:" + m.getId());
        CALog.did(m.getName() + ": Autarch AI Core removed as administrator.");
        return true;
    }
}
