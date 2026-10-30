package data.scripts.jonahtweaks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.combat.CombatState;
import java.util.List;
import org.lwjgl.input.Keyboard;

/**
 * Jonah Tweaks: sticky autopilot. Pressing U sets what you want; while it is ON,
 * autopilot is forced back on every frame (ship switches, Zero Day hacks, taking manual control).
 * Only another U press turns it off.
 */
public class JTStickyAutopilot extends BaseEveryFrameCombatPlugin {
    static final String KEY = "jt_sticky_autopilot";
    static boolean wanted = false;
    private CombatEngineAPI engine;
    private boolean pressed = false;

    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        try {
            if (Global.getSector() != null && Global.getSector().getPersistentData().get(KEY) instanceof Boolean)
                wanted = ((Boolean) Global.getSector().getPersistentData().get(KEY)).booleanValue();
        } catch (Throwable t) { }
    }

    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.getCombatUI() == null) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed() || !e.isKeyDownEvent() || e.isRepeat() || e.getEventValue() != Keyboard.KEY_U) continue;
            wanted = !engine.getCombatUI().isAutopilotOn(); // the game flips it; remember where it is going
            pressed = true;
            try { if (Global.getSector() != null) Global.getSector().getPersistentData().put(KEY, Boolean.valueOf(wanted)); } catch (Throwable t) { }
            engine.getCombatUI().addMessage(1, Misc.getHighlightColor(), wanted ? "Sticky autopilot ON (press U to release)" : "Sticky autopilot OFF");
        }
    }

    private ShipAPI lastShip = null;
    private float grace = 0f;
    private int cycle = 0;   // 2 = turn off this frame, 1 = turn on this frame

    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.getCombatUI() == null) return;
        ShipAPI ship = engine.getPlayerShip();
        if (ship == null || ship.isShuttlePod() || !ship.isAlive()) { pressed = false; return; }   // never force on a shuttle pod
        if (ship != lastShip) {
            lastShip = ship;
            grace = 0.5f;                  // let the transfer of command finish
            cycle = wanted ? 2 : 0;        // then re-apply so the new ship actually gets the autopilot
        }
        if (grace > 0f) { grace -= engine.getElapsedInLastFrame(); pressed = false; return; }
        if (cycle == 2 && !pressed) { set(false); cycle = 1; return; }
        if (cycle == 1 && !pressed) { set(true); cycle = 0; return; }
        boolean actual = engine.getCombatUI().isAutopilotOn();
        if (wanted && !actual) set(true);
        else if (!wanted && actual && pressed) set(false);
        pressed = false;
        cycle = 0;
    }

    private void set(boolean on) {
        try {
            Object ui = engine.getCombatUI();
            if (ui instanceof CombatState) { ((CombatState) ui).setAutopilot(on); return; }
            Object st = com.fs.state.AppDriver.getInstance().getCurrentState();
            if (st instanceof CombatState) ((CombatState) st).setAutopilot(on);
        } catch (Throwable t) { Global.getLogger(JTStickyAutopilot.class).warn("Jonah Tweaks: sticky autopilot failed", t); }
    }
}
