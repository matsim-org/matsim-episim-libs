/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.run;

import org.matsim.scenarioCreation.*;
import picocli.AutoComplete;
import picocli.CommandLine;

/**
 * Runnable class that does nothing by itself, but has to be invoked with one subcommand of
 * the scenario creation utils.
 */
@CommandLine.Command(
		name = "scenarioCreation",
		description = "Scenario creation tool for Episim offering various subcommands.",
		mixinStandardHelpOptions = true,
		usageHelpWidth = 120,
		subcommands = {CommandLine.HelpCommand.class, AutoComplete.GenerateCompletion.class, RunTrial.class,
				DistrictLookup.class, SplitHomeFacilities.class, ConvertPersonAttributes.class, FilterEvents.class,
				MergeEvents.class, DownSampleScenario.class, DownloadWeatherData.class}
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
