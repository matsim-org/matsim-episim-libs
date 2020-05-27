package org.matsim.episim.model;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DefaultInfectionModelTest {

	private static final Offset<Double> OFFSET = Offset.offset(0.001);

	private EpisimConfigGroup config;
	private DefaultInfectionModel model;
	private DefaultFaceMaskModel maskModel;
	private Map<String, Restriction> restrictions;


	@Before
	public void setup() {
		EpisimReporting reporting = mock(EpisimReporting.class);
		SplittableRandom rnd = new SplittableRandom(1);

		config = EpisimTestUtils.createTestConfig();
		maskModel = new DefaultFaceMaskModel(config, rnd);
		model = new DefaultInfectionModel(rnd, config, reporting, maskModel, Integer.MAX_VALUE);
		restrictions = config.createInitialRestrictions();
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
			model.infectionDynamicsFacility(person, container, jointTime.getSeconds(), actType);
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
				model.infectionDynamicsFacility(person, container, jointTime.getSeconds(), actType);
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
		config.getOrAddContainerParams("quarantine_home").setContactIntensity(0);

		rate = sampleInfectionRate(Duration.ofMinutes(30), "home",
				() -> EpisimTestUtils.createFacility(5, "home", EpisimTestUtils.HOME_QUARANTINE),
				createPerson
		);

		assertThat(rate).isCloseTo(0, OFFSET);
	}

	@Test
	public void sameWithOrWithoutTracking() {
		EpisimConfigGroup config = EpisimTestUtils.createTestConfig();

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
		model = new DefaultInfectionModel(new SplittableRandom(1), config, rNoTracking, maskModel, Integer.MAX_VALUE);
		model.setRestrictionsForIteration(1, config.createInitialRestrictions());
		sampleTotalInfectionRate(500, Duration.ofMinutes(15), "leis", container);


		EpisimTestUtils.resetIds();
		EpisimReporting rTracking = mock(EpisimReporting.class);
		model = new DefaultInfectionModel(new SplittableRandom(1), config, rTracking, maskModel, 0);
		model.setRestrictionsForIteration(1, config.createInitialRestrictions());

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
					EpisimPerson p1 = (EpisimPerson) o1[0];
					EpisimPerson p2 = (EpisimPerson) o2[0];

					boolean same = p1.getPersonId().equals(p2.getPersonId());
					if (!same) return -1;

					return ComparisonChain.start()
							.compare((double) o1[2], (double) o2[2])
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

}
