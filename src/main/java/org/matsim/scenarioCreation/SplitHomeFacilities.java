package org.matsim.scenarioCreation;

import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.ReplayHandler;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.objectattributes.attributable.Attributes;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Executable class to reduce facilities sizes by splitting them.
 */
@CommandLine.Command(
		name = "splitHomeFacilities",
		description = "Splits facilities into smaller ones with a given target distribution",
		mixinStandardHelpOptions = true
)
public class SplitHomeFacilities implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(SplitHomeFacilities.class);

	@CommandLine.Parameters(arity = "1", description = "Path to population file")
	private Path population;

	@CommandLine.Option(names = "--events", description = "Path to event file", required = true)
	private Path events;

	@CommandLine.Option(names = "--output", description = "Output folder", defaultValue = "")
	private Path output;

	@CommandLine.Option(names = "--district", description = "Target district", defaultValue = "Berlin")
	private String district;

	@CommandLine.Option(names = "--target", description = "Target distribution with increasing facility sizes",
			defaultValue = "41.9 33.8 11.9 9.1 3.4", split = " ")
	private List<Double> target;

	public static void main(String[] args) {
		System.exit(new CommandLine(new SplitHomeFacilities()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		Config config = ConfigUtils.createConfig();
		config.plans().setInputFile(population.toString());

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		if (!Files.exists(population)) {
			log.error("Input population file {} does not exists", events);
			return 1;
		}

		if (!Files.exists(events)) {
			log.error("Input events file {} does not exists", events);
			return 1;
		}

		Scenario scenario = ScenarioUtils.loadScenario(config);

		episimConfig.setInputEventsFile(events.toString());

		// Maps facilities to the contained person ids
		Map<Id<ActivityFacility>, Set<Id<Person>>> groups = new HashMap<>();

		for (Person p : scenario.getPopulation().getPersons().values()) {
			Attributes attr = p.getAttributes();
			if (!district.equals(attr.getAttribute("district")))
				continue;

			Id<ActivityFacility> id = Id.create((String) attr.getAttribute("homeId"), ActivityFacility.class);
			groups.computeIfAbsent(id, f -> new HashSet<>())
					.add((p.getId()));
		}


		int before = groups.size();

		log.info("Targeting distribution: {}", target);
		log.info("Distribution before splitting:");
		int[] hist = calcHist(groups, target);
		printHist(hist, target);

		List<Map.Entry<Id<ActivityFacility>, Set<Id<Person>>>> sorted = groups.entrySet()
				.stream().sorted(Comparator.comparingInt(e -> -e.getValue().size()))
				.collect(Collectors.toList());

		int n = 0;
		EnumeratedIntegerDistribution error = calcError(groups, target);

		// new homes of persons if they have been reassigned
		Map<Id<Person>, Id<ActivityFacility>> newHomes = new HashMap<>();

		for (Map.Entry<Id<ActivityFacility>, Set<Id<Person>>> e : sorted) {

			// update error distribution
			if (n % 100 == 0)
				error = calcError(groups, target);

			double prob = error.probability(Math.min(target.size(), e.getValue().size()));
			if (prob != 0) {
				// Group can not be reduced any further
				break;
			}

			Set<Id<Person>> member = e.getValue();

			int sample;
			int split = 0;
			while ((sample = error.sample()) < member.size()) {

				Set<Id<Person>> removed = new HashSet<>();

				// Put the new splitted group
				String homeId = e.getKey().toString() + "split" + split;
				Id<ActivityFacility> id = Id.create(homeId, ActivityFacility.class);

				Iterator<Id<Person>> it = member.iterator();
				for (int i = 0; i < sample; i++) {
					Id<Person> p = it.next();
					removed.add(p);
					scenario.getPopulation().getPersons().get(p).getAttributes()
							.putAttribute("homeId", homeId);

					newHomes.put(p, id);

					it.remove();
				}

				// TODO: split rest of the groups to new id

				groups.put(id, removed);

				split++;
			}

			n++;
		}

		log.info("Distribution after step {}", n);


		int now = groups.size() - before;
		log.info("Created {} new households", now);
		hist = calcHist(groups, target);
		printHist(hist, target);

		PopulationUtils.writePopulation(scenario.getPopulation(), output.resolve("population-split.xml.gz").toString());

		ReplayHandler replay = new ReplayHandler(episimConfig, null);

		EventsManager manager = EventsUtils.createEventsManager();
		EventWriterXML writer = new EventWriterXML(output.resolve("events-split.xml.gz").toString());
		manager.addHandler(writer);

		// create new events if the activity id has changed
		for (Event event : replay.getEvents()) {

			// TODO reasign visit
			if (event instanceof HasPersonId && newHomes.containsKey(((HasPersonId) event).getPersonId())) {
				if (event instanceof ActivityStartEvent) {
					ActivityStartEvent ev = (ActivityStartEvent) event;
					if (ev.getActType().equals("home"))
						manager.processEvent(new ActivityStartEvent(ev.getTime(), ev.getPersonId(), ev.getLinkId(),
								newHomes.get(ev.getPersonId()), ev.getActType(), ev.getCoord()));
					else
						manager.processEvent(ev);

				} else if (event instanceof ActivityEndEvent) {
					ActivityEndEvent ev = (ActivityEndEvent) event;
					if (ev.getActType().equals("home"))
						manager.processEvent(new ActivityEndEvent(ev.getTime(), ev.getPersonId(), ev.getLinkId(),
								newHomes.get(ev.getPersonId()), ev.getActType()));
					else
						manager.processEvent(ev);
				}
			} else
				manager.processEvent(event);
		}

		// Close event file
		manager.resetHandlers(0);
		writer.closeFile();

		return 0;
	}

	/**
	 * Distribution over group-sizes where the probability is the error regarding to the current distribution.
	 */
	private EnumeratedIntegerDistribution calcError(Map<Id<ActivityFacility>, Set<Id<Person>>> groups, List<Double> target) {

		int[] hist = calcHist(groups, target);
		double[] error = new double[hist.length];
		double total = Arrays.stream(hist).sum();

		for (int i = 0; i < hist.length; i++) {
			double current = (hist[i] * 100) / total;
			error[i] = current - target.get(i);
			error[i] = Math.min(0, error[i]);
		}

		double[] p = Arrays.copyOf(error, error.length);
		total = Arrays.stream(p).sum();
		for (int i = 0; i < p.length; i++) {
			p[i] /= total;
		}

		return new EnumeratedIntegerDistribution(IntStream.range(1, target.size() + 1).toArray(), p);
	}

	/**
	 * Prints histogram and error.
	 */
	private void printHist(int[] hist, List<Double> target) {

		double total = Arrays.stream(hist).sum();

		for (int i = 0; i < hist.length; i++) {
			String label = String.valueOf(i + 1);
			if (i == hist.length - 1)
				label += "+";

			double current = (hist[i] * 100) / total;
			String format = String.format("%.2f", current);
			String error = String.format("%+.2f", current - target.get(i));

			log.info("Size {}: {} = {} % (diff: {}%)", label, hist[i], format, error);
		}

	}

	/**
	 * Histogram of group sizes.
	 */
	private int[] calcHist(Map<Id<ActivityFacility>, Set<Id<Person>>> groups, List<Double> target) {

		int[] hist = new int[target.size()];

		for (Map.Entry<Id<ActivityFacility>, Set<Id<Person>>> e : groups.entrySet()) {
			hist[Math.min(e.getValue().size(), target.size()) - 1] += 1;
		}

		return hist;
	}
}
