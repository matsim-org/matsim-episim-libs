package org.matsim.episim;

import com.google.inject.*;
import com.google.inject.util.Modules;
import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.model.DefaultContactModel;
import org.matsim.episim.model.InitialInfectionHandler;
import org.matsim.episim.model.Transition;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.util.EpisimSplittableRandom;
import org.matsim.facilities.ActivityFacility;
import org.matsim.testcases.MatsimTestUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.episim.model.Transition.to;

public class InfectionEpisodeWriterTest {

	private static final DiseaseStatus[] STAGES = Arrays.stream(DiseaseStatus.values())
			.filter(s -> s != DiseaseStatus.susceptible)
			.toArray(DiseaseStatus[]::new);

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();

	@ParameterizedTest
	@ValueSource(ints = {1, 2})
	public void invariants(int threads) {

		OutputDirectoryLogging.catchLogEntries();

		String output = utils.getOutputDirectory() + "threads-" + threads + "/";

		Injector injector = createInjector(output, threads);
		injector.getInstance(EpisimRunner.class).run(60);

		Collection<EpisimPerson> persons = injector.getInstance(InfectionEventHandler.class).getPersons();

		List<Map<String, String>> personRows = readTsv(output, "persons.tsv");
		List<Map<String, String>> episodes = readTsv(output, "infectionEpisodes.tsv");
		List<Map<String, String>> infectionEvents = readTsv(output, "infectionEvents.txt");

		// 1. one row per person
		assertThat(personRows).hasSize(persons.size());
		assertThat(personRows).allSatisfy(r -> assertThat(r.get("homeContainerCount")).isEqualTo("1"));

		Map<String, Map<String, String>> byEpisode = episodes.stream()
				.collect(Collectors.toMap(r -> r.get("personId") + "#" + r.get("episode"), r -> r));

		assertThat(byEpisode).hasSameSizeAs(episodes);
		assertThat(episodes).allSatisfy(r -> assertThat(r.get("restored")).isEqualTo("0"));

		// 2. each infection event has exactly one contact episode
		List<Map<String, String>> contact = episodes.stream().filter(r -> r.get("source").equals("contact")).collect(Collectors.toList());
		assertThat(contact).hasSameSizeAs(infectionEvents);

		Map<String, Long> contactKeys = contact.stream()
				.collect(Collectors.groupingBy(r -> r.get("personId") + "#" + r.get("day_infectedButNotContagious"), Collectors.counting()));

		for (Map<String, String> ev : infectionEvents) {
			int day = (int) Math.floor(Double.parseDouble(ev.get("time")) / EpisimUtils.DAY);
			assertThat(contactKeys.get(ev.get("infected") + "#" + day))
					.as("Infection event %s", ev)
					.isEqualTo(1L);
		}

		// 3. initial infections
		List<Map<String, String>> initial = episodes.stream().filter(r -> r.get("source").equals("initial")).collect(Collectors.toList());
		assertThat(initial).isNotEmpty()
				.allSatisfy(r -> {
					assertThat(r.get("infectorId")).isEmpty();
					assertThat(r.get("infectorEpisode")).isEmpty();
					assertThat(r.get("infectorInHousehold")).isEmpty();
					assertThat(r.get("facility")).isEmpty();
				});

		assertThat(contact).isNotEmpty();

		for (Map<String, String> r : episodes) {

			// 4. stages are not decreasing
			int last = Integer.MIN_VALUE;
			for (DiseaseStatus stage : STAGES) {
				String d = r.get("day_" + stage.name());
				if (d.isEmpty()) continue;
				assertThat(Integer.parseInt(d)).as("Stage %s of %s", stage, r).isGreaterThanOrEqualTo(last);
				last = Integer.parseInt(d);
			}

			if (r.get("closedBy").equals("susceptible"))
				assertThat(Integer.parseInt(r.get("day_susceptibleAgain"))).isGreaterThanOrEqualTo(last);
			else
				assertThat(r.get("day_susceptibleAgain")).isEmpty();

			assertThat(r.get("closedBy")).isIn("susceptible", "deceased", "endOfRun");
		}

		// 5. infector points to an episode that is contagious on day of infection
		for (Map<String, String> r : contact) {
			int day = Integer.parseInt(r.get("day_infectedButNotContagious"));
			Map<String, String> infector = byEpisode.get(r.get("infectorId") + "#" + r.get("infectorEpisode"));

			assertThat(infector).as("Infector of %s", r).isNotNull();
			assertThat(Integer.parseInt(infector.get("day_infectedButNotContagious"))).isLessThanOrEqualTo(day);
			assertThat(Integer.parseInt(infector.get("day_contagious"))).isLessThanOrEqualTo(day);

			for (String end : List.of("day_seriouslySick", "day_recovered", "day_deceased", "day_susceptibleAgain")) {
				if (!infector.get(end).isEmpty())
					assertThat(Integer.parseInt(infector.get(end))).as("%s of infector %s", end, infector).isGreaterThan(day);
			}

			assertThat(r.get("infectorInHousehold")).isIn("0", "1");
			assertThat(r.get("infectedAtHome")).isIn("0", "1");
		}

		// 6. stages before death are not lost
		List<Map<String, String>> deceased = episodes.stream().filter(r -> r.get("closedBy").equals("deceased")).collect(Collectors.toList());
		assertThat(deceased).isNotEmpty()
				.allSatisfy(r -> {
					for (String stage : List.of("contagious", "showingSymptoms", "seriouslySick", "critical", "deceased"))
						assertThat(r.get("day_" + stage)).as("Stage %s of %s", stage, r).isNotEmpty();
				});

		// deaths are counted cumulatively in infections.txt
		List<Map<String, String>> reports = readTsv(output, "infections.txt");
		String lastDay = reports.get(reports.size() - 1).get("day");
		List<Map<String, String>> lastReports = reports.stream().filter(r -> r.get("day").equals(lastDay)).collect(Collectors.toList());
		// the total row is only present with multiple districts, otherwise the single district is the total
		long nDeceasedCumulative = lastReports.stream().anyMatch(r -> r.get("district").equals("total")) ?
				lastReports.stream().filter(r -> r.get("district").equals("total")).mapToLong(r -> Long.parseLong(r.get("nDeceasedCumulative"))).sum() :
				lastReports.stream().mapToLong(r -> Long.parseLong(r.get("nDeceasedCumulative"))).sum();

		long deceasedPersons = persons.stream().filter(p -> p.getDiseaseStatus() == DiseaseStatus.deceased).count();
		double sampleSize = injector.getInstance(EpisimConfigGroup.class).getSampleSize();

		assertThat(nDeceasedCumulative)
				.isEqualTo((long) (deceasedPersons * (1 / sampleSize)))
				.isEqualTo((long) (deceased.size() * (1 / sampleSize)));

		// 7. consecutive episodes
		Map<String, List<Integer>> perPerson = episodes.stream().collect(Collectors.groupingBy(r -> r.get("personId"),
				Collectors.mapping(r -> Integer.parseInt(r.get("episode")), Collectors.toList())));

		assertThat(perPerson.values()).allSatisfy(nums -> {
			Collections.sort(nums);
			for (int i = 0; i < nums.size(); i++)
				assertThat(nums.get(i)).isEqualTo(i + 1);
		});

		assertThat(perPerson.values()).anySatisfy(nums -> assertThat(nums).hasSizeGreaterThan(1));

		for (EpisimPerson p : persons) {
			assertThat(perPerson.getOrDefault(p.getPersonId().toString(), List.of()))
					.as("Episodes of %s", p)
					.hasSize(p.getNumInfections());
		}

		// 9. episodes open at the end of the run
		Set<String> endOfRun = episodes.stream().filter(r -> r.get("closedBy").equals("endOfRun"))
				.map(r -> r.get("personId")).collect(Collectors.toSet());

		Set<String> infectedAtEnd = persons.stream()
				.filter(p -> p.getDiseaseStatus() != DiseaseStatus.susceptible && p.getDiseaseStatus() != DiseaseStatus.deceased)
				.map(p -> p.getPersonId().toString())
				.collect(Collectors.toSet());

		assertThat(endOfRun).isNotEmpty()
				.isEqualTo(infectedAtEnd);
	}

	@Test
	public void snapshot() throws IOException {

		OutputDirectoryLogging.catchLogEntries();

		String full = utils.getOutputDirectory() + "full/";
		Injector injector = createInjector(full, 1);
		injector.getInstance(EpisimConfigGroup.class).setSnapshotInterval(30);
		injector.getInstance(EpisimRunner.class).run(40);

		Path snapshot;
		try (var files = Files.list(Path.of(full))) {
			snapshot = files.filter(f -> f.getFileName().toString().endsWith(".zip")).findFirst().orElseThrow();
		}

		String fromSnapshot = utils.getOutputDirectory() + "fromSnapshot/";
		injector = createInjector(fromSnapshot, 1);
		injector.getInstance(EpisimConfigGroup.class).setStartFromSnapshot(snapshot.toString());
		injector.getInstance(EpisimRunner.class).run(40);

		List<Map<String, String>> episodes = readTsv(fromSnapshot, "infectionEpisodes.tsv");
		Collection<EpisimPerson> persons = injector.getInstance(InfectionEventHandler.class).getPersons();

		assertThat(readTsv(fromSnapshot, "persons.tsv")).hasSize(persons.size());

		List<Map<String, String>> restored = episodes.stream().filter(r -> r.get("restored").equals("1")).collect(Collectors.toList());
		assertThat(restored).isNotEmpty()
				.allSatisfy(r -> {
					assertThat(r.get("infectorId")).isEmpty();
					assertThat(r.get("infectorEpisode")).isEmpty();
					assertThat(r.get("infectorInHousehold")).isEmpty();
					assertThat(Integer.parseInt(r.get("day_infectedButNotContagious"))).isLessThan(30);
					assertThat(r.get("closedBy")).isIn("susceptible", "deceased", "endOfRun");
				});

		// episodes closed before the snapshot, restored ones and new ones together form the complete history
		Map<String, List<Integer>> perPerson = episodes.stream().collect(Collectors.groupingBy(r -> r.get("personId"),
				Collectors.mapping(r -> Integer.parseInt(r.get("episode")), Collectors.toList())));

		for (EpisimPerson p : persons) {
			List<Integer> nums = new ArrayList<>(perPerson.getOrDefault(p.getPersonId().toString(), List.of()));
			Collections.sort(nums);
			assertThat(nums).as("Episodes of %s", p)
					.isEqualTo(java.util.stream.IntStream.rangeClosed(1, p.getNumInfections()).boxed().collect(Collectors.toList()));
		}
	}

	@Test
	public void homeContainer() {

		OutputDirectoryLogging.catchLogEntries();

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new HomeScenario(utils)));

		InfectionEventHandler handler = injector.getInstance(InfectionEventHandler.class);
		handler.init(injector.getInstance(ReplayHandler.class).getEvents());

		Map<String, EpisimPerson> persons = handler.getPersons().stream()
				.collect(Collectors.toMap(p -> p.getPersonId().toString(), p -> p));

		assertThat(persons).containsOnlyKeys("withEvents", "noHomeId", "stationary", "partial");

		// agent with events uses the facility from the events, which differs from the homeId
		assertHome(persons.get("withEvents"), "facilityA", 1);

		// agent without homeId attribute gets a fallback household on days without events
		assertHome(persons.get("noHomeId"), "home_of_noHomeId", 1);

		// agent without events is put into the container of its homeId
		assertHome(persons.get("stationary"), "homeC", 1);

		// agent with events only on one day, most frequent container is used
		assertHome(persons.get("partial"), "homeE", 2);

		Map<String, Map<String, String>> rows = readTsv(utils.getOutputDirectory(), "persons.tsv").stream()
				.collect(Collectors.toMap(r -> r.get("personId"), r -> r));

		assertThat(rows).hasSize(4);
		assertThat(rows.get("withEvents"))
				.containsEntry("homeIdAttribute", "homeA")
				.containsEntry("homeContainerId", "facilityA")
				.containsEntry("homeContainerCount", "1")
				.containsEntry("age", "30")
				.containsEntry("district", "north");

		assertThat(rows.get("noHomeId"))
				.containsEntry("homeIdAttribute", "")
				.containsEntry("homeContainerId", "home_of_noHomeId")
				.containsEntry("age", "")
				.containsEntry("district", "");

		assertThat(rows.get("stationary")).containsEntry("homeContainerId", "homeC");
		assertThat(rows.get("partial"))
				.containsEntry("homeContainerId", "homeE")
				.containsEntry("homeContainerCount", "2");
	}

	private static Injector createInjector(String output, int threads) {

		SyntheticBatch.Params params = new SyntheticBatch.Params(300, 3, 10, 1, 2, DefaultContactModel.class, 3);
		params.seed = 4711;

		Injector injector = Guice.createInjector(Modules.override(Modules.override(new EpisimModule())
				.with(new SyntheticScenario(params))).with(new AbstractModule() {
			@Override
			protected void configure() {
				bind(DiseaseStatusTransitionModel.class).to(SevereTransitionModel.class).in(Singleton.class);
				bind(InitialInfectionHandler.class).to(DailyImport.class).in(Singleton.class);
			}
		}));

		Config config = injector.getInstance(Config.class);
		config.controller().setOutputDirectory(output);

		EpisimConfigGroup episimConfig = injector.getInstance(EpisimConfigGroup.class);
		episimConfig.setThreads(threads);
		episimConfig.setProgressionConfig(shortProgression());

		return injector;
	}

	private static void assertHome(EpisimPerson person, String id, int count) {
		InfectionEpisodeWriter.HomeContainer home = InfectionEpisodeWriter.findHomeContainer(person);
		assertThat(home.id).isEqualTo(Id.create(id, ActivityFacility.class));
		assertThat(home.count).isEqualTo(count);
	}

	private static List<Map<String, String>> readTsv(String dir, String name) {
		try {
			List<String> lines = Files.readAllLines(Path.of(dir, name));
			String[] header = lines.get(0).split("\t", -1);

			List<Map<String, String>> result = new ArrayList<>();
			for (String line : lines.subList(1, lines.size())) {
				String[] values = line.split("\t", -1);
				assertThat(values).as("Row %s", line).hasSameSizeAs(header);

				Map<String, String> row = new LinkedHashMap<>();
				for (int i = 0; i < header.length; i++)
					row.put(header[i], values[i]);

				result.add(row);
			}
			return result;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Short disease progression so that reinfections and deaths happen within a short run.
	 */
	private static com.typesafe.config.Config shortProgression() {
		return Transition.config()
				.from(DiseaseStatus.infectedButNotContagious, to(DiseaseStatus.contagious, Transition.fixed(2)))
				.from(DiseaseStatus.contagious,
						to(DiseaseStatus.showingSymptoms, Transition.fixed(1)),
						to(DiseaseStatus.recovered, Transition.fixed(4)))
				.from(DiseaseStatus.showingSymptoms,
						to(DiseaseStatus.seriouslySick, Transition.fixed(1)),
						to(DiseaseStatus.recovered, Transition.fixed(3)))
				.from(DiseaseStatus.seriouslySick,
						to(DiseaseStatus.critical, Transition.fixed(1)),
						to(DiseaseStatus.recovered, Transition.fixed(3)))
				.from(DiseaseStatus.critical,
						to(DiseaseStatus.deceased, Transition.fixed(2)),
						to(DiseaseStatus.seriouslySickAfterCritical, Transition.fixed(2)))
				.from(DiseaseStatus.seriouslySickAfterCritical, to(DiseaseStatus.recovered, Transition.fixed(2)))
				.from(DiseaseStatus.recovered, to(DiseaseStatus.susceptible, Transition.fixed(5)))
				.build();
	}

	/**
	 * Infects a few random susceptible persons every day, so that the disease does not die out.
	 */
	static final class DailyImport implements InitialInfectionHandler {

		private final EpisimSplittableRandom rnd;

		@Inject
		DailyImport(EpisimSplittableRandom rnd) {
			this.rnd = rnd;
		}

		@Override
		public Object2IntMap<VirusStrain> handleInfections(Map<Id<Person>, EpisimPerson> persons, int iteration) {

			List<EpisimPerson> candidates = persons.values().stream()
					.filter(p -> p.getDiseaseStatus() == DiseaseStatus.susceptible)
					.sorted(Comparator.comparing(EpisimPerson::getPersonId))
					.collect(Collectors.toList());

			Object2IntMap<VirusStrain> infected = new Object2IntAVLTreeMap<>();
			for (int i = 0; i < 2 && !candidates.isEmpty(); i++) {
				candidates.remove(rnd.nextInt(candidates.size()))
						.setInitialInfection(EpisimUtils.getCorrectedTime(0, 0, iteration), VirusStrain.SARS_CoV_2);
				infected.mergeInt(VirusStrain.SARS_CoV_2, 1, Integer::sum);
			}

			return infected;
		}
	}

	/**
	 * Transition model with high probabilities for severe courses.
	 */
	static final class SevereTransitionModel implements DiseaseStatusTransitionModel {

		private final EpisimSplittableRandom rnd;

		@Inject
		SevereTransitionModel(EpisimSplittableRandom rnd) {
			this.rnd = rnd;
		}

		@Override
		public DiseaseStatus decideNextState(EpisimPerson person, DiseaseStatus status, int day) {
			switch (status) {
				case infectedButNotContagious:
					return DiseaseStatus.contagious;
				case contagious:
					return rnd.nextDouble() < 0.7 ? DiseaseStatus.showingSymptoms : DiseaseStatus.recovered;
				case showingSymptoms:
					return rnd.nextDouble() < 0.5 ? DiseaseStatus.seriouslySick : DiseaseStatus.recovered;
				case seriouslySick:
					return !person.hadDiseaseStatus(DiseaseStatus.critical) && rnd.nextDouble() < 0.7 ? DiseaseStatus.critical : DiseaseStatus.recovered;
				case critical:
					return rnd.nextDouble() < 0.5 ? DiseaseStatus.deceased : DiseaseStatus.seriouslySickAfterCritical;
				case seriouslySickAfterCritical:
					return DiseaseStatus.recovered;
				case recovered:
					return DiseaseStatus.susceptible;
				default:
					throw new IllegalStateException("No state transition defined for " + status);
			}
		}
	}

	/**
	 * Small scenario covering the different ways home containers are created.
	 */
	private static final class HomeScenario extends AbstractModule {

		private final MatsimTestUtils utils;

		private HomeScenario(MatsimTestUtils utils) {
			this.utils = utils;
		}

		@Override
		protected void configure() {
		}

		@Provides
		@Singleton
		public Config config() {
			Config config = new SyntheticScenario().config();
			config.controller().setOutputDirectory(utils.getOutputDirectory());
			return config;
		}

		@Provides
		@Singleton
		public Scenario scenario(Config config) {
			Scenario scenario = ScenarioUtils.createScenario(config);

			addPerson(scenario, "withEvents", "homeA", 30, "north");
			addPerson(scenario, "noHomeId", null, -1, null);
			addPerson(scenario, "stationary", "homeC", 40, null);
			addPerson(scenario, "partial", "homeE", 50, null);
			// no events and no homeId, is not part of the simulation
			addPerson(scenario, "ignored", null, 60, null);

			return scenario;
		}

		@Provides
		@Singleton
		public ReplayHandler replayHandler() {

			Id<Link> link = Id.createLinkId("link");
			Id<ActivityFacility> outside = Id.create("outside", ActivityFacility.class);
			Id<ActivityFacility> facilityA = Id.create("facilityA", ActivityFacility.class);
			Id<ActivityFacility> facilityE = Id.create("facilityE", ActivityFacility.class);
			Id<Person> withEvents = Id.createPersonId("withEvents");
			Id<Person> noHomeId = Id.createPersonId("noHomeId");
			Id<Person> partial = Id.createPersonId("partial");

			List<Event> everyDay = List.of(
					new ActivityEndEvent(8 * 3600, withEvents, link, facilityA, "home"),
					new ActivityStartEvent(9 * 3600, withEvents, link, outside, "outside", null),
					new ActivityEndEvent(17 * 3600, withEvents, link, outside, "outside"),
					new ActivityStartEvent(18 * 3600, withEvents, link, facilityA, "home", null)
			);

			List<Event> monday = new ArrayList<>(everyDay);
			monday.addAll(List.of(
					new ActivityEndEvent(8 * 3600, noHomeId, link, outside, "outside"),
					new ActivityStartEvent(9 * 3600, noHomeId, link, outside, "outside", null),
					new ActivityEndEvent(17 * 3600, noHomeId, link, outside, "outside"),
					new ActivityEndEvent(8 * 3600, partial, link, facilityE, "home"),
					new ActivityStartEvent(9 * 3600, partial, link, outside, "outside", null),
					new ActivityEndEvent(17 * 3600, partial, link, outside, "outside")
			));
			monday.sort(Comparator.comparingDouble(Event::getTime));

			// separate lists, otherwise days would be deduplicated and trajectory entries not repeated
			Map<DayOfWeek, List<Event>> events = new EnumMap<>(DayOfWeek.class);
			events.put(DayOfWeek.MONDAY, monday);
			for (DayOfWeek day : DayOfWeek.values()) {
				if (day != DayOfWeek.MONDAY)
					events.put(day, new ArrayList<>(everyDay));
			}

			return new ReplayHandler(events);
		}

		private static void addPerson(Scenario scenario, String id, String homeId, int age, String district) {
			Person p = scenario.getPopulation().getFactory().createPerson(Id.createPersonId(id));
			if (homeId != null)
				p.getAttributes().putAttribute("homeId", homeId);
			if (age >= 0)
				p.getAttributes().putAttribute("age", age);
			if (district != null)
				p.getAttributes().putAttribute("district", district);
			scenario.getPopulation().addPerson(p);
		}
	}
}
