package org.matsim.episim.model;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.*;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.policy.Restriction;
import org.matsim.episim.policy.RestrictionTest;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacility;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DefaultContactModelTest {

	private static final Offset<Double> OFFSET = Offset.offset(0.001);

	private Config config;
	private DefaultContactModel model;
	private InfectionModel infectionModel;
	private Map<String, Restriction> restrictions;
	private EpisimReporting reporting;
	private SplittableRandom rnd;


	@Before
	public void setup() {
		// No verification, since it results in oom error
		reporting = Mockito.mock(EpisimReporting.class, Mockito.withSettings().stubOnly());
		rnd = new SplittableRandom(1);

		config = EpisimTestUtils.createTestConfig();
		final EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		infectionModel = new DefaultInfectionModel(new DefaultFaceMaskModel(rnd), config);
		model = new DefaultContactModel(rnd, config, reporting, infectionModel);
		restrictions = episimConfig.createInitialRestrictions();
		model.setRestrictionsForIteration(1, restrictions);

	}

	/**
	 * Samples how many time person {@code p} gets infected over many runs.
	 *
	 * @param jointTime leaving time of person p
	 * @param actType   activity type
	 * @param f         provider for facility
	 * @param p         provider for person
	 * @return sampled infection rate
	 */
	private double sampleInfectionRate(Duration jointTime, String actType, Supplier<InfectionEventHandler.EpisimFacility> f,
									   Function<InfectionEventHandler.EpisimFacility, EpisimPerson> p) {

		int infections = 0;

		for (int i = 0; i < 30_000; i++) {
			InfectionEventHandler.EpisimFacility container = f.get();
			EpisimPerson person = p.apply(container);
			model.infectionDynamicsFacility(person, container, jointTime.getSeconds());
			if (person.getDiseaseStatus() == EpisimPerson.DiseaseStatus.infectedButNotContagious)
				infections++;
		}

		return infections / 30_000d;
	}

	/**
	 * Samples the total infection rate when all persons leave a container at the same time.
	 *
	 * @return infection rate of all persons in the container
	 */
	private double sampleTotalInfectionRate(int n, Duration jointTime, String actType, Supplier<InfectionEventHandler.EpisimFacility> f) {

		double rate = 0;

		Random r = new Random(0);

		for (int i = 0; i < n; i++) {
			InfectionEventHandler.EpisimFacility container = f.get();
			List<EpisimPerson> allPersons = Lists.newArrayList(container.getPersons());

			while (!container.getPersons().isEmpty()) {
				EpisimPerson person = container.getPersons().get(r.nextInt(container.getPersons().size()));
				model.infectionDynamicsFacility(person, container, jointTime.getSeconds());
				EpisimTestUtils.removePerson(container, person);
			}

			// Percentage of infected persons
			rate += (double) allPersons.stream().filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.infectedButNotContagious).count() / allPersons.size();
		}

		return rate / n;
	}


	@Test
	public void highContactRate() {
		double rate = sampleInfectionRate(Duration.ofMinutes(15), "c10",
				() -> EpisimTestUtils.createFacility(1, "c10", EpisimTestUtils.CONTAGIOUS),
				(f) -> EpisimTestUtils.createPerson("c10", f)
		);
		assertThat(rate).isCloseTo(1, OFFSET);

		rate = sampleInfectionRate(Duration.ZERO, "c10",
				() -> EpisimTestUtils.createFacility(1, "c10", EpisimTestUtils.CONTAGIOUS),
				(f) -> EpisimTestUtils.createPerson("c10", f)
		);
		assertThat(rate).isCloseTo(0, OFFSET);
	}

	@Test
	public void alone() {
		double rate = sampleInfectionRate(Duration.ofMinutes(10), "c10",
				() -> EpisimTestUtils.createFacility(0, "c10", EpisimTestUtils.CONTAGIOUS),
				(f) -> EpisimTestUtils.createPerson("c10", f)
		);
		assertThat(rate).isCloseTo(0, OFFSET);
	}

	@Test
	public void showingSymptoms() {

		double rate = sampleInfectionRate(Duration.ofMinutes(10), "c10",
				() -> EpisimTestUtils.createFacility(2, "c10", EpisimTestUtils.SYMPTOMS),
				(f) -> EpisimTestUtils.createPerson("c10", f)
		);
		assertThat(rate).isCloseTo(1, OFFSET);
	}

	@Test
	public void noInfection() {
		double now = 0d;

		double rate = sampleInfectionRate(Duration.ofHours(2), "c10",
				() -> EpisimTestUtils.createFacility(5, "c10", p -> p.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.infectedButNotContagious)),
				(f) -> EpisimTestUtils.createPerson("c10", f)
		);
		assertThat(rate).isCloseTo(0, OFFSET);

		rate = sampleInfectionRate(Duration.ofHours(2), "c10",
				() -> EpisimTestUtils.createFacility(5, "c10", p -> p.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.recovered)),
				(f) -> EpisimTestUtils.createPerson("c10", f)
		);
		assertThat(rate).isCloseTo(0, OFFSET);

		rate = sampleInfectionRate(Duration.ofHours(2), "c10",
				() -> EpisimTestUtils.createFacility(5, "c10", EpisimTestUtils.FULL_QUARANTINE),
				(f) -> EpisimTestUtils.createPerson("c10", f)
		);
		assertThat(rate).isCloseTo(0, OFFSET);

		rate = sampleInfectionRate(Duration.ofHours(2), "c00",
				() -> EpisimTestUtils.createFacility(5, "c00", EpisimTestUtils.FULL_QUARANTINE),
				(f) -> EpisimTestUtils.createPerson("c00", f)
		);
		assertThat(rate).isCloseTo(0, OFFSET);

		// no infections without contact intensity
		restrictions.put("c10", Restriction.of(1.0, 0.0));
		rate = sampleInfectionRate(Duration.ofHours(2), "c10",
				() -> EpisimTestUtils.createFacility(1, "c10", EpisimTestUtils.CONTAGIOUS),
				(f) -> EpisimTestUtils.createPerson("c10", f)
		);
		assertThat(rate).isCloseTo(0, OFFSET);

	}

	@Test
	public void quarantineEffectiveness() {

		double rate = sampleInfectionRate(Duration.ofMinutes(30), "c0.1",
				() -> EpisimTestUtils.createFacility(5, "c0.1", EpisimTestUtils.CONTAGIOUS),
				(f) -> EpisimTestUtils.createPerson("c0.1", f)
		);

		double rateWithQuarantined = sampleInfectionRate(Duration.ofMinutes(30), "c0.1",
				() -> EpisimTestUtils.addPersons(EpisimTestUtils.createFacility(5, "c0.1", EpisimTestUtils.CONTAGIOUS),
						5, "c0.1", EpisimTestUtils.FULL_QUARANTINE),
				(f) -> EpisimTestUtils.createPerson("c0.1", f)
		);

		// Test case has very low effectiveness
		assertThat(rateWithQuarantined / rate).as("Quarantine effectiveness")
				.isCloseTo(0.998, Offset.offset(0.01));

	}

	@Test
	public void noCrossInfection() {
		double rate = sampleInfectionRate(Duration.ofMinutes(30), "home",
				() -> EpisimTestUtils.createFacility(1, "home", EpisimTestUtils.CONTAGIOUS),
				(f) -> EpisimTestUtils.createPerson("edu", f)
		);

		assertThat(rate).isCloseTo(0, OFFSET);

		rate = sampleInfectionRate(Duration.ofMinutes(30), "edu",
				() -> EpisimTestUtils.createFacility(1, "edu", EpisimTestUtils.CONTAGIOUS),
				(f) -> EpisimTestUtils.createPerson("leis", f)
		);

		assertThat(rate).isCloseTo(0, OFFSET);
	}

	@Test
	public void crossInfection() {

		for (String other : List.of("leis", "work", "home")) {
			double rate = sampleInfectionRate(Duration.ofHours(24), "home",
					() -> EpisimTestUtils.createFacility(1, "home", EpisimTestUtils.CONTAGIOUS),
					(f) -> EpisimTestUtils.createPerson(other, f)
			);

			assertThat(rate).as("home with " + other)
					.isCloseTo(1, OFFSET);
		}
	}

	@Test
	public void homeQuarantine() {

		// Full infections in home
		double rate = sampleInfectionRate(Duration.ofMinutes(30), "home",
				() -> EpisimTestUtils.createFacility(5, "home", EpisimTestUtils.HOME_QUARANTINE),
				(f) -> EpisimTestUtils.createPerson("home", f)
		);

		assertThat(rate).isCloseTo(1, OFFSET);

		// no infection for home quarantine during other activities
		rate = sampleInfectionRate(Duration.ofHours(2), "c10",
				() -> EpisimTestUtils.createFacility(5, "c10", EpisimTestUtils.HOME_QUARANTINE),
				(f) -> EpisimTestUtils.createPerson("c10", f)
		);

		assertThat(rate).isCloseTo(0, OFFSET);
	}

	@Test
	public void homeQuarantineContact() {

		Function<InfectionEventHandler.EpisimFacility, EpisimPerson> createPerson = (f) -> {
			EpisimPerson p = EpisimTestUtils.createPerson("home", f);
			p.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, 0);
			return p;
		};

		double rate = sampleInfectionRate(Duration.ofMinutes(30), "home",
				() -> EpisimTestUtils.createFacility(5, "home", EpisimTestUtils.HOME_QUARANTINE),
				createPerson
		);

		assertThat(rate).isCloseTo(1, OFFSET);

		// no contact between home quarantined persons
		ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class).getOrAddContainerParams("quarantine_home").setContactIntensity(0);

		rate = sampleInfectionRate(Duration.ofMinutes(30), "home",
				() -> EpisimTestUtils.createFacility(5, "home", EpisimTestUtils.HOME_QUARANTINE),
				createPerson
		);

		assertThat(rate).isCloseTo(0, OFFSET);
	}

	@Test
	public void groupSizes() {

		restrictions.put("c10", RestrictionTest.update(restrictions.get("c10"), Restriction.ofGroupSize(20)));
		double rate = sampleInfectionRate(Duration.ofMinutes(30), "c10",
				() -> EpisimTestUtils.createFacility(10, "c10", 21, EpisimTestUtils.CONTAGIOUS),
				f -> EpisimTestUtils.createPerson("c10", f)
		);

		assertThat(rate).isCloseTo(0, OFFSET);
	}

	@Test
	public void closingHours() {

		// closed from 0 - 5 o'clock
		restrictions.put("c10", RestrictionTest.update(restrictions.get("c10"), Restriction.ofClosingHours(0, 5)));
		double rate = sampleInfectionRate(Duration.ofHours(5), "c10",
				() -> EpisimTestUtils.createFacility(10, "c10", 21, EpisimTestUtils.CONTAGIOUS),
				f -> EpisimTestUtils.createPerson("c10", f)
		);

		assertThat(rate).isCloseTo(0, OFFSET);

		rate = sampleInfectionRate(Duration.ofHours(6), "c10",
				() -> EpisimTestUtils.createFacility(10, "c10", 21, EpisimTestUtils.CONTAGIOUS),
				f -> EpisimTestUtils.createPerson("c10", f)
		);

		assertThat(rate).isGreaterThan(0);
	}

	@Test
	public void closedAllDay() {
		restrictions.put("c10", RestrictionTest.update(restrictions.get("c10"), Restriction.ofClosingHours(0, 24)));
		double rate = sampleInfectionRate(Duration.ofHours(6), "c10",
				() -> EpisimTestUtils.createFacility(10, "c10", 21, EpisimTestUtils.CONTAGIOUS),
				f -> EpisimTestUtils.createPerson("c10", f)
		);

		assertThat(rate).isCloseTo(0, OFFSET);
	}

	@Test
	public void reducedGroupSize() {
		restrictions.put("c0.5", RestrictionTest.update(restrictions.get("c0.5"), Restriction.ofReducedGroupSize(10)));
		double baseRate = sampleInfectionRate(Duration.ofMinutes(10), "c0.5",
				() -> EpisimTestUtils.createFacility(9, "c0.5", 10, EpisimTestUtils.CONTAGIOUS),
				f -> EpisimTestUtils.createPerson("c0.5", f)
		);

		restrictions.put("c0.5", RestrictionTest.update(restrictions.get("c0.5"), Restriction.ofReducedGroupSize(5)));
		double rate = sampleInfectionRate(Duration.ofMinutes(10), "c0.5",
				() -> EpisimTestUtils.createFacility(9, "c0.5", 10, EpisimTestUtils.CONTAGIOUS),
				f -> EpisimTestUtils.createPerson("c0.5", f)
		);


		// strong reduction in infections
		assertThat(baseRate - rate).isCloseTo(0.6d, Offset.offset(0.1));
	}

	@Test
	public void vaccinated() {

		VaccinationConfigGroup vac = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		vac.getParams(VaccinationType.generic).setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2).atDay(0, 1.0d));

		Function<InfectionEventHandler.EpisimFacility, EpisimPerson> fp = f -> {
			EpisimPerson p = EpisimTestUtils.createPerson("c10", f);
			p.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes,  VaccinationType.generic, 0);
			return p;
		};

		double rate = sampleInfectionRate(Duration.ofMinutes(10), "c10",
				() -> EpisimTestUtils.createFacility(9, "c10", 10, EpisimTestUtils.CONTAGIOUS), fp);

		assertThat(rate)
				.isEqualTo(0);

		vac.getParams(VaccinationType.generic).setEffectiveness(VaccinationConfigGroup.forStrain(VirusStrain.SARS_CoV_2)
				.atDay(0, 0.1)
				.atDay(15, 1.0d));

		rate = sampleInfectionRate(Duration.ofMinutes(10), "c10",
				() -> EpisimTestUtils.createFacility(9, "c10", 10, EpisimTestUtils.CONTAGIOUS), fp);

		assertThat(rate)
				.isGreaterThan(0);
	}

	@Test
	public void sameWithOrWithoutTracking() {
		Config config = EpisimTestUtils.createTestConfig();
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		// Container with persons of different state
		Supplier<InfectionEventHandler.EpisimFacility> container = () -> {
			InfectionEventHandler.EpisimFacility f = EpisimTestUtils.createFacility(3, "leis", EpisimTestUtils.CONTAGIOUS);
			EpisimTestUtils.addPersons(f, 3, "leis", EpisimTestUtils.FULL_QUARANTINE);
			EpisimTestUtils.addPersons(f, 1, "leis", (p) -> { });
			EpisimTestUtils.addPersons(f, 1, "work", (p) -> { });
			EpisimTestUtils.addPersons(f, 1, "home", (p) -> { });
			EpisimTestUtils.addPersons(f, 3, "leis", (p) -> p.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.recovered));
			EpisimTestUtils.addPersons(f, 3, "leis", (p) -> p.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.infectedButNotContagious));
			return f;
		};

		EpisimTestUtils.resetIds();
		EpisimReporting rNoTracking = mock(EpisimReporting.class);


		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);
		tracingConfig.setMinContactDuration_sec(0);
		model = new DefaultContactModel(new SplittableRandom(1), config, rNoTracking, infectionModel);
		model.setRestrictionsForIteration(1, episimConfig.createInitialRestrictions());
		sampleTotalInfectionRate(500, Duration.ofMinutes(15), "leis", container);


		EpisimTestUtils.resetIds();
		EpisimReporting rTracking = mock(EpisimReporting.class);
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(0);
		tracingConfig.setMinContactDuration_sec(0);
		model = new DefaultContactModel(new SplittableRandom(1), config, rTracking, infectionModel);
		model.setRestrictionsForIteration(1, episimConfig.createInitialRestrictions());

		sampleTotalInfectionRate(500, Duration.ofMinutes(15), "leis", container);

		List<Object[]> noTracking = Mockito.mockingDetails(rNoTracking).getInvocations().stream()
				.filter(inv -> inv.getMethod().getName().equals("reportInfection"))
				.map(InvocationOnMock::getArguments).collect(Collectors.toList());
		List<Object[]> tracking = Mockito.mockingDetails(rTracking).getInvocations().stream()
				.filter(inv -> inv.getMethod().getName().equals("reportInfection"))
				.map(InvocationOnMock::getArguments).collect(Collectors.toList());

		assertThat(noTracking)
				// Compares arguments of the calls [person1, person2, time, activity]
				.usingElementComparator((o1, o2) -> {
					EpisimInfectionEvent p1 = (EpisimInfectionEvent) o1[0];
					EpisimInfectionEvent p2 = (EpisimInfectionEvent) o2[0];

					boolean same = p1.getPersonId().equals(p2.getPersonId());
					if (!same) return -1;

					return ComparisonChain.start()
							.compare(p1.getTime(), p2.getTime())
							.result();
				})
				.hasSizeGreaterThan(0)
				.isEqualTo(tracking);
	}

	@Test
	public void restrictionEffectiveness() {

		String type = "c1.0";
		double rate = sampleTotalInfectionRate(20_000, Duration.ofMinutes(30), type,
				() -> EpisimTestUtils.addPersons(EpisimTestUtils.createFacility(5, type, EpisimTestUtils.CONTAGIOUS), 15, type, p -> {
				})
		);

		restrictions.put(type, Restriction.of(0.5, 1.0));

		double rateRestricted = sampleTotalInfectionRate(20_000, Duration.ofMinutes(30), type,
				() -> EpisimTestUtils.addPersons(EpisimTestUtils.createFacility(5, type, EpisimTestUtils.CONTAGIOUS), 15, type, p -> {
				})
		);

		// This test fails if the effectiveness of restrictions changes
		// Please check if it is intended and update the value below
		assertThat(rateRestricted / rate).as("Restriction effectiveness")
				.isCloseTo(0.53, Offset.offset(0.01));
	}

	@Test
	public void infectionRates() {

		// This test fails for changes in infection rates
		// Please check if they are intended and update the values below

		List<Pair<Integer, Double>> expectation = Lists.newArrayList(
				// Number of persons & expected infection rate
				Pair.of(1, 0.45),
				Pair.of(3, 0.83),
				Pair.of(6, 0.95),
				Pair.of(10, 0.93),
				Pair.of(50, 0.92)
		);

		for (Pair<Integer, Double> p : expectation) {

			double rate = sampleInfectionRate(Duration.ofMinutes(20), "c0.5",
					() -> EpisimTestUtils.addPersons(EpisimTestUtils.createFacility(
							p.getLeft(), "c0.5", EpisimTestUtils.CONTAGIOUS),
							p.getLeft(), "c0.5", EpisimTestUtils.FULL_QUARANTINE),
					(f) -> EpisimTestUtils.createPerson("c0.5", f)
			);

			assertThat(rate).as("Infection rate for %d persons", p.getLeft())
					.isCloseTo(p.getRight(), Offset.offset(0.01));

		}

	}

	/**
	 * Tests that locationBasedRestrictions. There are three facilities in different NYC boroughs: Bronx, Queens,
	 * and Staten Island. There are location based restrictions for the first two. The infection rate should be roughly
	 * the same as the remaining fraction; therefore we expect rates of 0.0 and 0.5 for Bronx and Queens respectively.
	 * Since there is no location based restriction for Staten Island, the default remaining fraction should be used, 1.0
	 */
	@Test
	public void locationBasedRestrictions() {

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setDistrictLevelRestrictions(EpisimConfigGroup.DistrictLevelRestrictions.yes);
		episimConfig.setDistrictLevelRestrictionsAttribute("subdistrict");

		// Add activity facilities to scenario
		Scenario scenario = ScenarioUtils.loadScenario(config);

		ActivityFacilitiesFactory factory = scenario.getActivityFacilities().getFactory();
		ActivityFacility bronxFacility = factory.createActivityFacility(Id.create("BronxFacility", ActivityFacility.class), new Coord(0, 0));
		bronxFacility.getAttributes().putAttribute("subdistrict", "Bronx");

		ActivityFacility queensFacility = factory.createActivityFacility(Id.create("QueensFacility", ActivityFacility.class), new Coord(0, 0));
		queensFacility.getAttributes().putAttribute("subdistrict", "Queens");

		ActivityFacility statenIslandFacility = factory.createActivityFacility(Id.create("StatenIslandFacility", ActivityFacility.class), new Coord(100, 100));
		statenIslandFacility.getAttributes().putAttribute("subdistrict", "StatenIsland");

		scenario.getActivityFacilities().addActivityFacility(bronxFacility);
		scenario.getActivityFacilities().addActivityFacility(queensFacility);
		scenario.getActivityFacilities().addActivityFacility(statenIslandFacility);


		// Add location based restrictions for Bronx and Queens
		Map<String, Double> nycBoroughs = new HashMap<>();
		nycBoroughs.put("Bronx", 0.0);
		nycBoroughs.put("Queens", 0.5);
		restrictions.put("c10", RestrictionTest.update(restrictions.get("c10"), Restriction.ofLocationBasedRf(nycBoroughs)));
		restrictions.put("c10", RestrictionTest.update(restrictions.get("c10"), Restriction.of(1.0)));

		// These 2 lines are necessary repeats from setup(); TODO: a more elegant solution
		model = new DefaultContactModel(rnd, config, reporting, infectionModel, scenario);
		model.setRestrictionsForIteration(1, restrictions);

		double rateBronx = sampleInfectionRate(Duration.ofHours(6), "c10",
				() -> EpisimTestUtils.createFacility("BronxFacility", 10, "c10", 21, EpisimTestUtils.CONTAGIOUS),
				f -> EpisimTestUtils.createPerson("c10", f)
		);

		double rateQueens = sampleInfectionRate(Duration.ofHours(6), "c10",
				() -> EpisimTestUtils.createFacility("QueensFacility", 10, "c10", 21, EpisimTestUtils.CONTAGIOUS),
				f -> EpisimTestUtils.createPerson("c10", f)
		);

		double rateStatenIsland = sampleInfectionRate(Duration.ofHours(6), "c10",
				() -> EpisimTestUtils.createFacility("StatenIslandFacility", 10, "c10", 21, EpisimTestUtils.CONTAGIOUS),
				f -> EpisimTestUtils.createPerson("c10", f)
		);

		Offset<Double> offset = Offset.offset(0.1);
		assertThat(rateBronx).isCloseTo(0.0, offset);
		assertThat(rateQueens).isCloseTo(0.5, offset);
		assertThat(rateStatenIsland).isCloseTo(1.0, offset);

	}

}
