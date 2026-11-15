package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MarketSkillEffect;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

/**
 * 0.3.8.0: Autarchy, the Autarch administrator's own skill (data/characters/skills/ca_autarchy.skill and skill_data.csv;
 * npc_only, so no officer or player can learn it). At the colony it governs: +1 industry limit, 20% less upkeep, +1
 * stability. CAAutarchAdmin gives it to every core it seats; CAItems and CAStability count it.
 */
public final class CAAutarchSkill {
    private CAAutarchSkill() { }

    public static final String ID = "ca_autarchy", NAME = "Autarchy";
    public static final int INDUSTRIES = 1;
    public static final float UPKEEP_MULT = 0.8f, STABILITY = 1f;

    public static class Level1 implements MarketSkillEffect {
        public void apply(MarketAPI market, String id, float level) {
            market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).modifyFlat(id, INDUSTRIES, NAME);
        }

        public void unapply(MarketAPI market, String id) {
            market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).unmodifyFlat(id);
        }

        public String getEffectDescription(float level) { return "+" + INDUSTRIES + " industry limit"; }

        public String getEffectPerLevelDescription() { return null; }

        public ScopeDescription getScopeDescription() { return ScopeDescription.GOVERNED_OUTPOST; }
    }

    public static class Level2 implements MarketSkillEffect {
        public void apply(MarketAPI market, String id, float level) { market.getUpkeepMult().modifyMult(id, UPKEEP_MULT, NAME); }

        public void unapply(MarketAPI market, String id) { market.getUpkeepMult().unmodifyMult(id); }

        public String getEffectDescription(float level) { return "-" + Math.round((1f - UPKEEP_MULT) * 100f) + "% upkeep"; }

        public String getEffectPerLevelDescription() { return null; }

        public ScopeDescription getScopeDescription() { return ScopeDescription.GOVERNED_OUTPOST; }
    }

    public static class Level3 implements MarketSkillEffect {
        public void apply(MarketAPI market, String id, float level) { market.getStability().modifyFlat(id, STABILITY, NAME); }

        public void unapply(MarketAPI market, String id) { market.getStability().unmodifyFlat(id); }

        public String getEffectDescription(float level) { return "+" + Math.round(STABILITY) + " stability"; }

        public String getEffectPerLevelDescription() { return null; }

        public ScopeDescription getScopeDescription() { return ScopeDescription.GOVERNED_OUTPOST; }
    }
}
