package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.*;

/**
 * 0.3.3.1: Kadur Remnant 3.2.4support3 (kadur_remnant), reached by condition ids only; no class of the mod is referenced.
 * Its two output conditions change what a new building makes at a colony you take or govern, and the probe colony copies
 * vanilla conditions only, so a new building's lines are adjusted here (after CADevelopment.adjust):
 * - Kadur Majority Faith (vayra_kadur_majority, KadurMajority): from size 3, +1 on every line of a building tagged
 *   "industrial". Its stability (+2 or -2 by owner and Kadur Remnant relations) is read live like any stability.
 * - Virus Bombed (vayra_virus_bomb): -2 on every line of every building.
 * Without the mod no colony has either condition and nothing changes. Kadur's other conditions and buildings need nothing:
 * the hydroponics complex is in the game's deposit tables, the HQs and the Caliph station are structures Autopilot never takes
 * down. The numbers are the mod's own, kept private or inline there, so they are copied. Checked offline by JTKadurCheck.
 */
public class CAKadur {
    private CAKadur() { }

    public static final String MOD = "kadur_remnant";
    static final String MAJORITY = "vayra_kadur_majority", VIRUS = "vayra_virus_bomb", INDUSTRIAL = "industrial";

    /** KadurMajority.showIcon: the condition works from this size. */
    static final int MAJORITY_MIN_SIZE = 3;
    /** KadurMajority.apply: getSupplyBonusFromOther on every "industrial" building. */
    static final int MAJORITY_OUTPUT = 1;
    /** vayra_virus_bomb.PRODUCTION_MALUS (private): every line of every building. */
    static final int VIRUS_OUTPUT = -2;

    /** Pure: the change to one output line of `units`. A line never goes below 0. */
    static int lineDelta(int units, boolean majority, boolean industrial, boolean virus) {
        if (units <= 0) return 0;
        int d = 0;
        if (majority && industrial) d += MAJORITY_OUTPUT;
        if (virus) d += VIRUS_OUTPUT;
        return Math.max(-units, d);
    }

    /** A new building's output lines at this colony: +1 per line of an industrial building with Kadur Majority Faith, -2 per line when Virus Bombed. */
    public static void adjust(MarketAPI m, CAEngine.Pick p, Industry inst, Map<String, Integer> deficit) {
        if (m == null || p == null || p.supplied == null || p.supplied.isEmpty()) return;
        boolean majority, virus;
        try {
            majority = m.hasCondition(MAJORITY) && m.getSize() >= MAJORITY_MIN_SIZE;
            virus = m.hasCondition(VIRUS);
        } catch (Throwable t) { return; }
        if (!majority && !virus) return;
        boolean industrial = false;
        try { industrial = inst != null ? inst.getSpec().hasTag(INDUSTRIAL) : Global.getSettings().getIndustrySpec(p.id).hasTag(INDUSTRIAL); }
        catch (Throwable ignored) { }
        String why = majority && industrial && virus ? "Kadur Majority Faith and Virus Bombed" : virus ? "Virus Bombed" : "Kadur Majority Faith";
        for (Map.Entry<String, Integer> e : new ArrayList<Map.Entry<String, Integer>>(p.supplied.entrySet())) {
            int d = lineDelta(e.getValue() == null ? 0 : e.getValue(), majority, industrial, virus);
            if (d != 0) CAEngine.supplyChange(p, e.getKey(), d, deficit, why);
        }
    }
}
