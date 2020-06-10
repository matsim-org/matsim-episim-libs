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

	private SplittableRandom rnd = new SplittableRandom(0);
	// new homes of persons if they have been reassigned
	private Map<Id<Person>, Id<ActivityFacility>> newHomes = new HashMap<>();
	// list of ids a facility was split into
	private Map<Id<ActivityFacility>, List<Id<ActivityFacility>>> splitHomes = new HashMap<>();
	// set of old valid home ids that have been converted
	private Set<String> oldHomeIds = new HashSet<>();
	// store visits of a person
	private Map<Id<Person>, Id<ActivityFacility>> visits = new HashMap<>();

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

			oldHomeIds.add((String) attr.getAttribute("homeId"));

			// Prefix all home facilities, so they don't interact with others anymore
			Id<ActivityFacility> id = Id.create("home_" + attr.getAttribute("homeId"), ActivityFacility.class);
			attr.putAttribute("homeId", id.toString());

			if (!district.equals(attr.getAttribute("district")))
				continue;

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

			int sample = error.sample();
			int split = 0;
			while (!member.isEmpty()) {

				Set<Id<Person>> removed = new HashSet<>();

				// Put the new split group
				String homeId = e.getKey().toString() + "_split" + split;
				Id<ActivityFacility> id = Id.create(homeId, ActivityFacility.class);

				Iterator<Id<Person>> it = member.iterator();
				for (int i = 0; i < sample; i++) {
					if (!it.hasNext())
						break;

					Id<Person> p = it.next();
					removed.add(p);
					scenario.getPopulation().getPersons().get(p).getAttributes()
							.putAttribute("homeId", homeId);

					newHomes.put(p, id);

					it.remove();
				}

				splitHomes.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(id);
				groups.put(id, removed);

				split++;
				sample = error.sample();
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

			if (event instanceof ActivityStartEvent) {
				ActivityStartEvent ev = (ActivityStartEvent) event;
				manager.processEvent(new ActivityStartEvent(ev.getTime(), ev.getPersonId(), ev.getLinkId(),
						getNewFacilityId(ev.getPersonId(), ev.getFacilityId(), ev.getActType()), ev.getActType(), ev.getCoord()));

			} else if (event instanceof ActivityEndEvent) {
				ActivityEndEvent ev = (ActivityEndEvent) event;
				manager.processEvent(new ActivityEndEvent(ev.getTime(), ev.getPersonId(), ev.getLinkId(),
						getNewFacilityId(ev.getPersonId(), ev.getFacilityId(), ev.getActType()), ev.getActType()));
			} else
				manager.processEvent(event);
		}

		// Close event file
		writer.closeFile();

		return 0;
	}

	/**
	 * Computes the facility id an event should have.
	 */
	private Id<ActivityFacility> getNewFacilityId(Id<Person> personId, Id<ActivityFacility> oldFacility, String actType) {

		Id<ActivityFacility> homeId = Id.create("home_" + oldFacility.toString(), ActivityFacility.class);

		// Check if id was ever handled
		if (!oldHomeIds.contains(oldFacility.toString()))
			return oldFacility;

		if (actType.equals("home")) {
			if (newHomes.containsKey(personId))
				return newHomes.get(personId);

			return homeId;

		} else if (actType.equals("visit")) {

			// choose a visit randomly
			if (splitHomes.containsKey(homeId)) {
				return visits.computeIfAbsent(personId, p -> {
					List<Id<ActivityFacility>> split = splitHomes.get(homeId);
					return split.get(rnd.nextInt(split.size()));
				});

			}

			return homeId;

		} else
			return oldFacility;
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
			if (e.getValue().size() == 0) continue;

			hist[Math.min(e.getValue().size(), target.size()) - 1] += 1;
		}

		return hist;
	}
}
