package org.matsim.run;

import org.matsim.scenarioCreation.ConvertPersonAttributes;
import org.matsim.scenarioCreation.DownSampleScenario;
import org.matsim.scenarioCreation.FilterEvents;
import org.matsim.scenarioCreation.MergeEvents;
import picocli.AutoComplete;
import picocli.CommandLine;

@CommandLine.Command(
		name = "ScenarioCreation",
		description = "Scenario creation tool for EpiSim offering various subcommands.",
		mixinStandardHelpOptions = true,
		usageHelpWidth = 120,
		subcommands = {CommandLine.HelpCommand.class, AutoComplete.GenerateCompletion.class,
				ConvertPersonAttributes.class, FilterEvents.class, MergeEvents.class, DownSampleScenario.class}
)
public class ScenarioCreation implements Runnable {

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	public static void main(String[] args) {
		System.exit(new CommandLine(new ScenarioCreation()).execute(args));
	}

	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}
}
