package data.scripts.jonahtweaks.autopilot;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import java.util.List;

/** 0.3.22.0: console command ac_undo: the loop guard's records (CAUndo), newest first. */
public class CACmdUndo implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) { Console.showMessage("This only works in the campaign."); return CommandResult.WRONG_CONTEXT; }
        List<String> l = CAUndo.lines();
        if (l.isEmpty()) { Console.showMessage("The loop guard isn't holding anything right now."); return CommandResult.SUCCESS; }
        StringBuilder b = new StringBuilder("Loop guard records (colony:kind:+added or -removed target):\n");
        for (int i = 0; i < Math.min(40, l.size()); i++) b.append("  ").append(l.get(i)).append('\n');
        if (l.size() > 40) b.append("  and ").append(l.size() - 40).append(" more in the report.");
        Console.showMessage(b.toString());
        return CommandResult.SUCCESS;
    }
}
