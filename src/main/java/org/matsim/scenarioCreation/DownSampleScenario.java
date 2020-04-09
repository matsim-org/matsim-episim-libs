package org.matsim.scenarioCreation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacilitiesImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static picocli.CommandLine.*;

@Command(
		name = "downSample",
		description = "Down sample scenario and extract information for episim.",
		mixinStandardHelpOptions = true
)
public class DownSampleScenario implements Callable<Integer> {

	private static Logger log = LogManager.getLogger(DownSampleScenario.class);

	@Parameters(paramLabel = "sampleSize", arity = "1", description = "Desired percentage of the sample between (0, 1)")
	private double sampleSize;

	@Option(names = "--population", required = true, description = "Population xml file")
	private Path population;

	@Option(names = "--output", description = "Output folder", defaultValue = "output/scenario")
	private Path output;

	@Option(names = "--events", required = true, description = "Path to events file")
	private Path events;

	@Option(names = "--facilities", description = "Path to facility file")
	private Path facilities;

	@Option(names = "--seed", defaultValue = "1", description = "Random seed used for sampling")
	private long seed;


	public static void main(String[] args) {
		System.exit(new CommandLine(new DownSampleScenario()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		if (!Files.exists(population)) {
			log.error("Population file {} does not exists", population);
			return 2;
		}

		if (!Files.exists(output)) Files.createDirectories(output);

		log.info("Sampling with size {}", sampleSize);

		MatsimRandom.reset(seed);

		Population population = PopulationUtils.readPopulation(this.population.toString());
		PopulationUtils.sampleDown(population, sampleSize);

		PopulationUtils.writePopulation(population, output.resolve("population" + sampleSize + ".xml.gz").toString());

		if (!Files.exists(events)) {
			log.error("Event file {} does not exists", events);
			return 2;
		}

		EventsManager manager = EventsUtils.createEventsManager();

		FilterHandler handler = new FilterHandler(population, null, null);
		manager.addHandler(handler);
		EventsUtils.readEvents(manager, events.toString());

		EventWriterXML writer = new EventWriterXML(
				IOUtils.getOutputStream(IOUtils.getFileUrl(output.resolve("events" + sampleSize + ".xml.gz").toString()), false)
		);

		log.info("Filtered {} out of {} events = {}%", handler.getEvents().size(), handler.getCounter(), handler.getEvents().size() / handler.getCounter());

		handler.getEvents().forEach( (time, eventsList) -> eventsList.forEach(writer::handleEvent));
		writer.closeFile();

		if (!Files.exists(facilities)) {
			log.warn("Facilities file {} does not exist", facilities);
			return 0;
		}

		log.info("Reading {}...", this.facilities);

		ActivityFacilitiesImpl facilities = new ActivityFacilitiesImpl();
		MatsimFacilitiesReader fReader = new MatsimFacilitiesReader(null, null, facilities);
		fReader.parse(IOUtils.getInputStream(IOUtils.getFileUrl(this.facilities.toString())));

		int n = facilities.getFacilities().size();

		Set<Id<ActivityFacility>> toRemove = facilities.getFacilities().keySet()
				.stream().filter(k -> !handler.getFacilities().contains(k)).collect(Collectors.toSet());

		toRemove.forEach(k -> facilities.getFacilities().remove(k));

		log.info("Filtered {} out of {} facilities", facilities.getFacilities().size(), n);

		new FacilitiesWriter(facilities).write(
				IOUtils.getOutputStream(IOUtils.getFileUrl(output.resolve("facilities" + sampleSize + ".xml.gz").toString()), false)
		);

		return 0;
	}

}
