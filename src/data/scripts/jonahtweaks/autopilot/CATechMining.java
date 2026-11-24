package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.TechMining;

import java.util.Collections;
import java.util.List;

/**
 * 0.3.16.6: with Slightly Better Tech-Mining on (CAMods.sbtm), Tech-Mining is a plan step at the Auto colony with the best
 * ruins: SBTM's ruins factor (vast 2.5, extensive 1.5, widespread 1, scattered 0.5) times what's left of them
 * ($core_techMiningMult, 1 until mined), the bigger colony on a tie. As a plan step it stays: the endgame's replacements,
 * pruning and the over-limit cleanup leave it (CAEndgame.keepsPlanBonus), and a removal mark on it there doesn't hold the
 * build back (CAPlanner). Without SBTM nothing changes. Vanilla API only (SBTM is read through the industry spec).
 */
public final class CATechMining {
    private CATechMining() { }

    static final String ID = "techmining";

    /** Pure: a colony's ruins score, 0 without ruins. */
    static float score(boolean vast, boolean extensive, boolean widespread, boolean scattered, float left) {
        if (!vast && !extensive && !widespread && !scattered) return 0f;
        return CAMods.sbtmRuins(vast, extensive, widespread) * Math.max(0f, left);
    }

    static float score(MarketAPI m) {
        float left = 1f;
        try { if (m.getMemoryWithoutUpdate().contains(TechMining.TECH_MINING_MULT)) left = m.getMemoryWithoutUpdate().getFloat(TechMining.TECH_MINING_MULT); }
        catch (Throwable ignored) { }
        return score(m.hasCondition("ruins_vast"), m.hasCondition("ruins_extensive"), m.hasCondition("ruins_widespread"), m.hasCondition("ruins_scattered"), left);
    }

    /** The Auto colony with the best ruins, or null (SBTM off, or no Auto colony with ruins). */
    static MarketAPI site() {
        if (!CAMods.sbtm()) return null;
        MarketAPI best = null;
        float bestScore = 0f;
        for (MarketAPI m : CAItems.autoMarkets()) {
            float sc = score(m);
            if (sc <= 0f) continue;
            if (best == null || sc > bestScore || (sc == bestScore && m.getSize() > best.getSize())) { best = m; bestScore = sc; }
        }
        return best;
    }

    /** The plan step at the best-ruins colony. */
    static List<CAPlan.Step> stepsFor(MarketAPI m) {
        MarketAPI s = m == null ? null : site();
        if (s == null || !s.getId().equals(m.getId())) return Collections.emptyList();
        return Collections.singletonList(new CAPlan.Step(CAPlan.Kind.BUILD, ID, "Tech-Mining (best ruins)"));
    }

    /** Tech-Mining at the best-ruins colony while SBTM is on. */
    public static boolean isStep(MarketAPI m, String id) {
        if (m == null || !ID.equals(id)) return false;
        MarketAPI s = site();
        return s != null && s.getId().equals(m.getId());
    }
}
