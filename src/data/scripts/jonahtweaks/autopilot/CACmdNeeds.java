package data.scripts.jonahtweaks.autopilot;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import java.util.List;

/** 0.3.23.0: console command ac_needs: the needs ledger's short goods (CANeeds). */
public class CACmdNeeds implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) { Console.showMessage("This only works in the campaign."); return CommandResult.WRONG_CONTEXT; }
        if (CANeeds.get() == null) { Console.showMessage("The needs list is worked out on the next daily pass."); return CommandResult.SUCCESS; }
        List<String> l = CANeeds.lines();
        if (l.isEmpty()) { Console.showMessage("Your colonies make everything they and your contracts use."); return CommandResult.SUCCESS; }
        StringBuilder b = new StringBuilder("Needs:\n");
        for (String s : l) b.append("  ").append(s).append('\n');
        Console.showMessage(b.toString());
        return CommandResult.SUCCESS;
    }
}
