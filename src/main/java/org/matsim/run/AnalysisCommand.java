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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.episim.analysis.CreateContactGraph;
import org.matsim.episim.analysis.ExtractInfectionsByAge;
import org.matsim.episim.analysis.RValuesFromEvents;
import org.matsim.episim.events.EpisimEventsReader;
import picocli.AutoComplete;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Runnable class that does nothing by itself, but has to be invoked with one subcommand.
 */
@CommandLine.Command(
		name = "analysis",
		description = "Analysis tool for Episim offering various subcommands.",
		mixinStandardHelpOptions = true,
		usageHelpWidth = 120,
		subcommands = {CommandLine.HelpCommand.class, AutoComplete.GenerateCompletion.class,
				RValuesFromEvents.class, ExtractInfectionsByAge.class, CreateContactGraph.class},
		subcommandsRepeatable = true
)
public class AnalysisCommand implements Runnable {

	private static final Logger log = LogManager.getLogger(AnalysisCommand.class);

	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;

	public static void main(String[] args) {
		System.exit(new CommandLine(new AnalysisCommand()).execute(args));
	}

	/**
	 * Iterates over all output folders in a directory in parallel.
	 *
	 * @param function function to execute
	 */
	public static void forEachScenario(Path output, Consumer<Path> function) throws IOException {

		Set<Path> scenarios = new LinkedHashSet<>();

		Files.list(output)
				.filter(p -> Files.isDirectory(p))
				.forEach(scenarios::add);

		log.info("Read " + scenarios.size() + " files");
		log.info(scenarios);

		Files.list(output)
				.filter(p -> Files.isDirectory(p))
				.forEach(scenarios::add);

		scenarios.parallelStream().forEach(scenario -> {
			try {
				function.accept(scenario);
			} catch (RuntimeException e) {
				log.error("Failed processing {}", scenario, e);
			}
		});
	}

	/**
	 * Reads in all event file from a scenario.
	 *
	 * @param scenario path of the scenario, which contains the event folder
	 * @param callback will be executed before reading an event file and pass the path
	 * @param handler  handler for the events
	 */
	public static void forEachEvent(Path scenario, Consumer<Path> callback, EventHandler handler) {

		Path eventFolder = scenario.resolve("events");
		if (!Files.exists(eventFolder)) {
			log.warn("No events found at {}", eventFolder);
			return;
		}

		EventsManager manager = EventsUtils.createEventsManager();
		manager.initProcessing();
		manager.addHandler(handler);

		List<Path> eventFiles;
		try {
			eventFiles = Files.list(eventFolder)
					.filter(p -> p.getFileName().toString().contains("xml.gz"))
					.collect(Collectors.toList());
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}

		for (Path p : eventFiles) {
			try {
				callback.accept(p);
				new EpisimEventsReader(manager).readFile(p.toString());
			} catch (UncheckedIOException e) {
				log.warn("Caught UncheckedIOException. Could not read file {}", p);
			}
		}

		manager.finishProcessing();
	}

	/**
	 * Tries to determine the run id from given folder and files present within it.
	 * @return empty string or prefix for scenario file that ends with "."
	 */
	public static String getScenarioPrefix(Path scenario) throws IOException {

		// find prefixed *config.xml
		Optional<Path> config = Files.find(scenario, 1,
				(path, attr) -> !path.getFileName().toString().equals("config.xml") && path.toString().endsWith("config.xml")).findFirst();

		if (config.isEmpty()) {
			return "";
		}

		String name = config.get().getFileName().toString();
		// this run has no prefix
		if (name.lastIndexOf('.') == name.indexOf('.'))
			return "";

		return name.substring(0, name.indexOf('.') + 1);
	}

	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}

}
