package org.matsim.run;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.FacilitiesHandling;
import org.matsim.episim.EpisimConfigGroup.InfectionParams;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.ReplayHandler;
import org.matsim.episim.events.EpisimPersonStatusEvent;
import org.matsim.episim.policy.FixedPolicy;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class RunEpisim {

	/**
	 * Activity names of the default params from {@link #addDefaultParams(EpisimConfigGroup)}
	 */
	public static final String[] DEFAULT_ACTIVITIES = {
			"pt", "work", "leisure", "edu", "shop", "errands", "business", "other", "freight", "home"
	};

	public static void main(String[] args) throws IOException {
		OutputDirectoryLogging.catchLogEntries();

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		config.network().setInputFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-10pct/input/berlin-v5-network.xml.gz");

		String episimEvents_1pct = "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct-schools/output-berlin-v5.4-1pct-schools/berlin-v5.4-1pct-schools.output_events_for_episim.xml.gz";
		String episimEvents_10pct = "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-10pct-schools/output-berlin-v5.4-10pct-schools/berlin-v5.4-10pct-schools.output_events_for_episim.xml.gz";

		episimConfig.setInputEventsFile(episimEvents_1pct);

		episimConfig.setFacilitiesHandling(FacilitiesHandling.bln);
		episimConfig.setSampleSize(0.01);
		episimConfig.setCalibrationParameter(2);
		//  episimConfig.setOutputEventsFolder("events");

		long closingIteration = 14;

		addDefaultParams(episimConfig);

		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
				.shutdown(closingIteration, "leisure", "edu")
				.restrict(closingIteration, 0.2, "work", "business", "other")
				.restrict(closingIteration, 0.3, "shop", "errands")
				.restrict(closingIteration, 0.5, "pt")
				.open(closingIteration + 60, DEFAULT_ACTIVITIES)
				.build()
		);

		setOutputDirectory(config);

		ConfigUtils.applyCommandline(config, Arrays.copyOfRange(args, 0, args.length));

		OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory());

		runSimulation(config, 130);

		OutputDirectoryLogging.closeOutputDirLogging();
	}

	/**
	 * Adds default parameters that should be valid for most scenarios.
	 */
	public static void addDefaultParams(EpisimConfigGroup config) {
		// pt
		config.addContainerParams(new InfectionParams("pt", "tr"));
		// regular out-of-home acts:
		config.addContainerParams(new InfectionParams("work"));
		config.addContainerParams(new InfectionParams("leisure", "leis"));
		config.addContainerParams(new InfectionParams("edu"));
		config.addContainerParams(new InfectionParams("shop"));
		config.addContainerParams(new InfectionParams("errands"));
		config.addContainerParams(new InfectionParams("business"));
		config.addContainerParams(new InfectionParams("other"));
		// freight act:
		config.addContainerParams(new InfectionParams("freight"));
		// home act:
		config.addContainerParams(new InfectionParams("home"));
	}

	/**
	 * Creates an output directory, with a name based on current config and adapt the logging config.
	 * This method is not thread-safe unlike {@link #runSimulation(Config, int)}.
	 */
	public static void setOutputDirectory(Config config) {
		StringBuilder outdir = new StringBuilder("output");
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		for (InfectionParams infectionParams : episimConfig.getInfectionParams()) {
			outdir.append("-");
			outdir.append(infectionParams.getContainerName());
			if (infectionParams.getContactIntensity() != 1.) {
				outdir.append("ci").append(infectionParams.getContactIntensity());
			}
		}
		config.controler().setOutputDirectory(outdir.toString());

	}

	/**
	 * Main loop that performs the iterations of the simulation.
	 *
	 * @param config        fully initialized config file, {@link EpisimConfigGroup} needs to be present.
	 * @param maxIterations maximum number of iterations (inclusive)
	 */
	public static void runSimulation(Config config, int maxIterations) throws IOException {

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);

		// save some time for not needed inputs
		// only network might be relevant
//        config.plans().setInputFile(null);
		config.facilities().setInputFile(null);
		config.vehicles().setVehiclesFile(null);

		Scenario scenario = ScenarioUtils.loadScenario(config);

		Path out = Paths.get(config.controler().getOutputDirectory());
		if (!Files.exists(out))
			Files.createDirectories(out);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		Path eventPath = null;
		if (episimConfig.getOutputEventsFolder() != null && !episimConfig.getOutputEventsFolder().isEmpty()) {
			eventPath = out.resolve(episimConfig.getOutputEventsFolder());
			if (!Files.exists(eventPath))
				Files.createDirectories(eventPath);
		}

		ReplayHandler replay = new ReplayHandler(episimConfig, scenario);

		simulationLoop(config, scenario, replay, maxIterations, eventPath);

		ControlerUtils.checkConfigConsistencyAndWriteToLog(config, "Just before starting iterations");
	}

	/**
	 * Performs the simulation loop.
	 */
	static void simulationLoop(final Config config, final Scenario scenario,
							   final ReplayHandler replay, final int maxIterations, @Nullable final Path eventPath) {

		final EventsManager events = EventsUtils.createEventsManager();
		final InfectionEventHandler eventHandler = new InfectionEventHandler(config, scenario, events);
		events.addHandler(eventHandler);

		for (int iteration = 0; iteration <= maxIterations; iteration++) {

			EventWriter writer = null;
			// Only write events if output was set
			if (eventPath != null) {
				writer = new EventWriterXML(eventPath.resolve(String.format("day_%03d.xml.gz", iteration)).toString());
				events.addHandler(writer);
			}

			events.resetHandlers(iteration);
			if (eventHandler.isFinished())
				break;

			// report initial status:
			if (iteration == 0) {
				for (EpisimPerson person : eventHandler.getPersons()) {
					if (person.getDiseaseStatus() != EpisimPerson.DiseaseStatus.susceptible) {
						events.processEvent(new EpisimPersonStatusEvent(0., person.getPersonId(), person.getDiseaseStatus()));
					}
				}
			}

			replay.replayEvents(events, iteration);
			if (writer != null) {
				events.removeHandler(writer);
				writer.closeFile();
			}
		}

	}

}
