package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 0.3.7.9: Autarch administrator (story mode). 0.3.8.0: Tier IV, one colony per CASyncCatalog.SEAT_PP bandwidth (CASync.seats),
 * and its own skill, Autarchy (CAAutarchSkill). The Autarch AI Core governs managed colonies (Approve or Auto) up to its
 * seats, the ones it adds most to first (gain: CAItems.adminWorth, the colony limit counted), where the colony stays at or
 * over the stability floor (CAStability). 0.3.8.2: colonies with a hired administrator count too (before, only colonies you
 * governed yourself did); the administrator goes unassigned. An AI core you placed as administrator is never replaced. It has an Alpha Core administrator's skills (Industrial Planning,
 * Hypercognition) plus Autarchy, and no AI core id: the colony screen has no core to hand back, and it adds no inspection
 * points or Pather interest. One person per colony, marked in its memory.
 *
 * Once seated it stays. It leaves a colony that is no longer managed, the colony it adds least to while it holds more
 * colonies than its seats, and every colony when the package is not bought, story mode is off or the core is dormant: one
 * colony a day, and never while that would take the colony, or a colony you govern, under the floor (Autarchy's industry
 * counted: a colony left over the industry limit pays the game's penalty). An administrator you place replaces it. Where you
 * govern a colony yourself in its place, it does not return until that colony leaves management. 0.3.8.9: or until the one
 * who took its place (you or an administrator) no longer governs the colony. A colony it governs and cannot leave keeps its seat.
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

    /** Your colonies it governs now. */
    public static int count() {
        int n = 0;
        try { for (MarketAPI m : Misc.getPlayerMarkets(false)) if (governs(m)) n++; } catch (Throwable ignored) { }
        return n;
    }

    /** A new administrator person: the core's name and portrait, an Alpha Core administrator's skills and Autarchy, no AI core id. */
    static PersonAPI person() {
        PersonAPI p = Global.getFactory().createPerson();
        p.setFaction(Factions.PLAYER);
        p.setName(new FullName(NAME, "", FullName.Gender.ANY));
        p.setPortraitSprite(CAAutarch.PORTRAIT);
        p.setRankId(null);
        p.setPostId(null);   // 0.3.8.1: no post; the colony screen put the faction's post name first ("Secretary" with Nexerelin)
        p.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1);
        p.getStats().setSkillLevel(Skills.HYPERCOGNITION, 1);
        p.getStats().setSkillLevel(CAAutarchSkill.ID, 1);
        p.getMemoryWithoutUpdate().set(FLAG, true);
        return p;
    }

    /** Daily, from CASync.daily (story mode on or off): its seats are checked, one is left if it must, and new ones are taken. */
    public static void daily() {
        boolean on = CASync.has("autarchAdmin");   // false with story mode off, while dormant, or not bought
        int seats = CASync.seats();
        boolean left = false;
        int governing = 0;   // 0.3.8.9: every colony it governs and does not leave today, managed or not (a seat it cannot leave stays taken)
        List<MarketAPI> held = new ArrayList<MarketAPI>();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            MemoryAPI mem = m.getMemoryWithoutUpdate();
            boolean seated = mem.getBoolean(SEATED), here = governs(m);
            if (!seated && !here) {
                // 0.3.8.9: also once the one who took its place no longer governs the colony
                if (mem.contains(DECLINED) && (!CASync.manages(m) || replacedGone(m, mem))) mem.unset(DECLINED);
                continue;
            }
            if (!m.isPlayerOwned()) {   // captured or autonomous: the new owner seats its own
                mem.unset(SEATED);
                if (is(m.getAdmin())) m.setAdmin(null);   // 0.3.9.6 (C12): the Autarch AI Core doesn't govern for them
                continue;
            }
            if (seated && !here) {   // someone else governs it now: you, or an administrator you placed
                mem.unset(SEATED);
                mem.set(DECLINED, governor(m));   // 0.3.8.9: who took its place; it stays away while they govern and the colony is managed
                continue;
            }
            if (!seated) mem.set(SEATED, true);
            learn(m);
            if (!left && (!on || !CASync.manages(m))) { left = leave(m); if (!left) governing++; }
            else {
                governing++;
                if (on && CASync.manages(m)) held.add(m);
            }
        }
        if (!on) return;
        if (governing > seats) {   // more colonies than seats (a seat taken back): the one it adds least to goes first
            if (left) return;
            // 0.3.8.9: least first, until one can leave (the floor may hold the first)
            final java.util.Map<MarketAPI, Float> adds = new java.util.HashMap<MarketAPI, Float>();
            for (MarketAPI m : held) adds.put(m, CAItems.adminWorth(m, m.getAdmin()));
            Collections.sort(held, new Comparator<MarketAPI>() {
                public int compare(MarketAPI a, MarketAPI b) { return Float.compare(adds.get(a), adds.get(b)); }
            });
            for (MarketAPI m : held) if (leave(m)) break;
            return;
        }
        int room = seats - governing;
        if (room <= 0) return;
        final PersonAPI probe = person();
        List<MarketAPI> open = new ArrayList<MarketAPI>();
        final java.util.Map<MarketAPI, Float> worth = new java.util.HashMap<MarketAPI, Float>();
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            if (!CASync.manages(m) || CARogue.noCores(m) || CARogue.adminLocked(m) || governs(m)) continue;
            if (m.getMemoryWithoutUpdate().contains(DECLINED)) continue;   // 0.3.8.9: the value is who took its place
            float w = gain(m, probe);
            if (w <= 0f) continue;
            worth.put(m, w);
            open.add(m);
        }
        Collections.sort(open, new Comparator<MarketAPI>() {
            public int compare(MarketAPI a, MarketAPI b) { return Float.compare(worth.get(b), worth.get(a)); }
        });
        for (MarketAPI m : open) {
            if (room <= 0) break;
            if (!CAStability.canLose(m, arrives(m, probe), null)) continue;   // 0.3.8.9: the probe; a person is made only to seat
            seat(m, person());
            room--;
        }
    }

    /** 0.3.8.9: who governs m, for DECLINED: "you" when you do, else the administrator's id. */
    static String governor(MarketAPI m) {
        PersonAPI p = m.getAdmin();
        return p == null || p.isPlayer() || p.getId() == null ? "you" : p.getId();
    }

    /**
     * 0.3.8.9: the one who took the core's place at m no longer governs it. A save from 0.3.8.2 to 0.3.8.8 stored true: it
     * gets the one who governs now.
     */
    static boolean replacedGone(MarketAPI m, MemoryAPI mem) {
        Object who = mem.get(DECLINED);
        if (!(who instanceof String)) { mem.set(DECLINED, governor(m)); return false; }
        return !who.equals(governor(m));
    }

    /**
     * 0.3.8.2: what the core adds at m over whoever governs it now, in credits a month: you, or a hired administrator (who then
     * goes unassigned). 0 where someone else governs it (an AI core you placed, another mod's person): it never replaces them.
     */
    static float gain(MarketAPI m, PersonAPI core) {
        if (CAItems.governedByYou(m)) return CAItems.adminWorth(m, core);
        PersonAPI now = m.getAdmin();
        if (now == null || now.isAICore() || CAItems.adminAt(m) == null) return 0f;
        return CAItems.adminWorth(m, core) - CAItems.adminWorth(m, now);
    }

    /** 0.3.8.2: m's stability change when the core arrives: from you, the colony limit counted; from a hired administrator, skills only. */
    static float arrives(MarketAPI m, PersonAPI core) {
        if (CAItems.governedByYou(m)) return CAStability.adminArrives(CAStability.adminOwn(core), CAStability.governedPenalty());
        return CAStability.adminOwn(core) - CAStability.adminOwn(m.getAdmin());
    }

    /**
     * 0.3.8.0: a core seated by 0.3.7.9 learns Autarchy; setAdmin again so the colony applies it. 0.3.8.1: and loses the
     * administrator post (shown before its name).
     */
    static void learn(MarketAPI m) {
        PersonAPI p = m.getAdmin();
        if (p == null) return;
        if (p.getPostId() != null) p.setPostId(null);
        if (p.getStats().getSkillLevel(CAAutarchSkill.ID) > 0f) return;
        p.getStats().setSkillLevel(CAAutarchSkill.ID, 1);
        m.setAdmin(p);
    }

    static void seat(MarketAPI m, PersonAPI p) {
        com.fs.starfarer.api.characters.AdminData hired = CAItems.adminAt(m);   // 0.3.8.2: a hired administrator goes unassigned
        String was = hired != null && hired.getPerson() != null ? hired.getPerson().getNameString() : null;
        p.setId("ca_autarch_" + m.getId());
        m.setAdmin(p);
        if (hired != null) hired.setMarket(null);
        m.getMemoryWithoutUpdate().set(SEATED, true);
        m.getMemoryWithoutUpdate().unset(DECLINED);
        CALog.did(m.getName() + ": Autarch AI Core assigned as administrator" + (was != null ? " in place of " + was + "." : "."));
    }

    /**
     * Leaves m to you if m and every colony you govern stay at or over the floor: m loses the core's own stability, takes the
     * colony limit's number with one more colony governed by you, and pays the industry limit's penalty if Autarchy's industry
     * was in use; the others each pay one more colony's share.
     */
    static boolean leave(MarketAPI m) {
        float per = CAStability.oneMoreGoverned();
        float delta = -CAStability.adminOwn(m.getAdmin()) - (CAStability.governedPenalty() + per);
        try {
            boolean slot = m.getAdmin().getStats().getSkillLevel(CAAutarchSkill.ID) > 0f;
            int num = Misc.getNumIndustries(m), max = Misc.getMaxIndustries(m);
            // 0.3.8.9: not when the colony is over the limit already: the game's one penalty is in its stability now
            if (slot && num > max - CAAutarchSkill.INDUSTRIES && num <= max) delta -= Misc.OVER_MAX_INDUSTRIES_PENALTY;
        } catch (Throwable ignored) { }
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
