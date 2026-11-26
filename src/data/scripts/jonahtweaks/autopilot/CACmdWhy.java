package data.scripts.jonahtweaks.autopilot;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

/**
 * 0.3.22.0: console command ac_why <colony>: a colony's mode, goal, plan steps with where each stands, and the planner's last
 * look (CAAuto.lastWhy). Only Console Commands loads it (data/console/commands.csv).
 */
public class CACmdWhy implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) { Console.showMessage("This only works in the campaign."); return CommandResult.WRONG_CONTEXT; }
        String want = args == null ? "" : args.trim().toLowerCase();
        if (want.isEmpty()) { Console.showMessage("Give it a colony name, like ac_why Row."); return CommandResult.BAD_SYNTAX; }
        MarketAPI hit = null;
        for (MarketAPI m : Misc.getPlayerMarkets(false)) {
            String n = m.getName().toLowerCase();
            if (n.equals(want)) { hit = m; break; }
            if (hit == null && n.startsWith(want)) hit = m;
        }
        if (hit == null) { Console.showMessage("None of your colonies is called " + args.trim() + "."); return CommandResult.ERROR; }
        try {
            JSONObject o = CADiagnostics.colony(hit, CAData.get());
            StringBuilder b = new StringBuilder(hit.getName()).append(" (").append(o.optString("mode")).append(", ").append(o.optString("goal", "no goal"))
                    .append("), industry slots ").append(o.optString("industrySlots")).append('\n');
            if (o.has("plan")) b.append("Plan: ").append(o.optString("plan")).append(o.has("world") ? " (" + o.optString("world") + ")" : "").append('\n');
            JSONArray steps = o.optJSONArray("steps");
            if (steps != null) for (int i = 0; i < steps.length(); i++) b.append("  ").append(steps.optString(i)).append('\n');
            String why = CAAuto.lastWhy.get(hit.getId());
            b.append("Last look: ").append(why == null ? "the planner hasn't logged one this session." : why);
            Console.showMessage(b.toString());
            return CommandResult.SUCCESS;
        } catch (Throwable t) {
            Console.showMessage("Reading " + hit.getName() + " failed: " + t);
            return CommandResult.ERROR;
        }
    }
}
