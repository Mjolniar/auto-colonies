package data.scripts.jonahtweaks.autopilot;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

/** 0.3.22.0: console command ac_report: writes the report (CADiagnostics.REPORT) to saves/common now. */
public class CACmdReport implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) { Console.showMessage("This only works in the campaign."); return CommandResult.WRONG_CONTEXT; }
        try {
            CADiagnostics.writeReport();
            Console.showMessage("The report is in saves/common/" + CADiagnostics.REPORT + ".data.");
            return CommandResult.SUCCESS;
        } catch (Throwable t) {
            Console.showMessage("Writing the report failed: " + t);
            return CommandResult.ERROR;
        }
    }
}
