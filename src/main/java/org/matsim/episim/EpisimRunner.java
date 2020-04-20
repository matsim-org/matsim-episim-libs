package org.matsim.episim;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.episim.events.EpisimPersonStatusEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main entry point and runner of one epidemic simulation.
 */
public class EpisimRunner {

	private final Config config;
	private final EventsManager manager;
	private final Provider<InfectionEventHandler> handlerProvider;
	private final Provider<ReplayHandler> replayProvider;

	@Inject
	public EpisimRunner(Config config, EventsManager manager, Provider<InfectionEventHandler> handlerProvider,
						Provider<ReplayHandler> replay) {
		this.config = config;
		this.handlerProvider = handlerProvider;
		this.manager = manager;
		this.replayProvider = replay;
	}

	/**
	 * Main loop that performs the iterations of the simulation.
	 *
	 * @param maxIterations maximum number of iterations (inclusive)
	 */
	public void run(int maxIterations) throws IOException {

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

		ControlerUtils.checkConfigConsistencyAndWriteToLog(config, "Just before starting iterations");

		simulationLoop(maxIterations, eventPath);
	}

	/**
	 * Performs the simulation loop.
	 */
	void simulationLoop(final int maxIterations, @Nullable final Path eventPath) {

		// Construct these dependencies as late as possible, so all other configs etc have been fully configured
		final ReplayHandler replay = replayProvider.get();
		final InfectionEventHandler handler = handlerProvider.get();

		manager.addHandler(handler);

		for (int iteration = 0; iteration <= maxIterations; iteration++) {

			if (!doStep(replay, handler, eventPath, iteration))
				return;

		}

	}

	/**
	 * Perform one iteration of simulation.
	 *
	 * @return false, when the simulation should end
	 */
	boolean doStep(final ReplayHandler replay, InfectionEventHandler handler, @Nullable final Path eventPath, int iteration) {

		EventWriter writer = null;
		// Only write events if output was set
		if (eventPath != null) {
			writer = new EventWriterXML(eventPath.resolve(String.format("day_%03d.xml.gz", iteration)).toString());
			manager.addHandler(writer);
		}

		manager.resetHandlers(iteration);
		if (handler.isFinished())
			return false;

		// report initial status:
		if (iteration == 0) {
			for (EpisimPerson person : handler.getPersons()) {
				if (person.getDiseaseStatus() != EpisimPerson.DiseaseStatus.susceptible) {
					manager.processEvent(new EpisimPersonStatusEvent(0., person.getPersonId(), person.getDiseaseStatus()));
				}
			}
		}

		// Process all events
		replay.replayEvents(manager, iteration);

		if (writer != null) {
			manager.removeHandler(writer);
			writer.closeFile();
		}

		return true;
	}

}
