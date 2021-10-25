package org.matsim.episim.model;

import com.google.common.primitives.Doubles;
import com.typesafe.config.Config;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.assertj.core.data.Percentage;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.*;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.model.progression.DefaultDiseaseStatusTransitionModel;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.episim.model.Transition.to;
import static org.mockito.Mockito.mock;

public class ConfigurableProgressionModelTest {

	private static final Config TEST_CONFIG = Transition.config()
			.from(EpisimPerson.DiseaseStatus.infectedButNotContagious,
					to(EpisimPerson.DiseaseStatus.contagious, Transition.fixed(4)))

			.from(EpisimPerson.DiseaseStatus.contagious,
					to(EpisimPerson.DiseaseStatus.showingSymptoms, Transition.fixed(2)),
					to(EpisimPerson.DiseaseStatus.recovered, Transition.fixed(12)))

			.from(EpisimPerson.DiseaseStatus.showingSymptoms,
					to(EpisimPerson.DiseaseStatus.seriouslySick, Transition.fixed(4)),
					to(EpisimPerson.DiseaseStatus.recovered, Transition.fixed(10)))

			.from(EpisimPerson.DiseaseStatus.seriouslySick,
					to(EpisimPerson.DiseaseStatus.critical, Transition.fixed(1)),
					to(EpisimPerson.DiseaseStatus.recovered, Transition.fixed(13)))

			.from(EpisimPerson.DiseaseStatus.critical,
					to(DiseaseStatus.seriouslySickAfterCritical, Transition.fixed(9)))

			.from(DiseaseStatus.seriouslySickAfterCritical,
					to(DiseaseStatus.recovered, Transition.fixed(1)))

			.from(DiseaseStatus.recovered,
					to(DiseaseStatus.susceptible, Transition.fixed(10)))

			.build();

	private EpisimReporting reporting;
	private ProgressionModel model;
	private EpisimConfigGroup episimConfig;
	private TracingConfigGroup tracingConfig;
	private TestingConfigGroup testingConfig;
	private VirusStrainConfigGroup strainConfig;
	private VaccinationConfigGroup vaccinationConfig;

	@Before
	public void setup() {
		reporting = mock(EpisimReporting.class);
		tracingConfig = new TracingConfigGroup();
		testingConfig = new TestingConfigGroup();
		episimConfig = new EpisimConfigGroup();
		strainConfig = new VirusStrainConfigGroup();
		vaccinationConfig = new VaccinationConfigGroup();
		episimConfig.setProgressionConfig(TEST_CONFIG);

		SplittableRandom rnd = new SplittableRandom(1);
		model = new ConfigurableProgressionModel(rnd, episimConfig, tracingConfig, new DefaultDiseaseStatusTransitionModel(rnd, vaccinationConfig, strainConfig));
		model.setIteration(1);
	}

	@Test
	public void tracing() {

		tracingConfig.setTracingProbability(1);
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(0);
		tracingConfig.setTracingDelay_days(0);

		model.setIteration(1);

		EpisimPerson p = EpisimTestUtils.createPerson(reporting);
		p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);
		for (int day = 0; day <= 5; day++) {
			model.updateState(p, day);
		}

		p.addTraceableContactPerson(EpisimTestUtils.createPerson(reporting), 5 * 24 * 3600);

		model.updateState(p, 6);
		assertThat(p.getTraceableContactPersons(0)).allMatch(t -> t.getQuarantineStatus() == EpisimPerson.QuarantineStatus.atHome);
	}

	@Test
	public void tracingCapacity() {

		tracingConfig.setTracingProbability(1);
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(0);
		tracingConfig.setTracingDelay_days(0);
		tracingConfig.setTracingCapacity_pers_per_day(500);

		episimConfig.setStartDate("2020-06-01");
		episimConfig.setSampleSize(1);

		model.setIteration(1);

		List<EpisimPerson> persons = new ArrayList<>();

		for (int i = 0; i < 1000; i++) {
			EpisimPerson p = EpisimTestUtils.createPerson("home", null);
			p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);
			persons.add(p);
		}

		for (int day = 0; day <= 5; day++) {
			int thisDay = day;
			model.setIteration(day);
			persons.forEach(p -> model.updateState(p, thisDay));
		}

		persons.forEach(p -> p.addTraceableContactPerson(EpisimTestUtils.createPerson("work", null), 5 * 24 * 3600));

		model.setIteration(6);

		persons.forEach(p -> model.updateState(p, 6));

		// Tests depends on random seed
		// because only 80% are showing symptoms, on average the first 625 persons can be traced
		for (int i = 0; i < 1000; i++) {

			EpisimPerson p = persons.get(i);
			if (i < 600 && p.getDiseaseStatus() == DiseaseStatus.showingSymptoms)
				assertThat(p.getTraceableContactPersons(0))
						.describedAs("Person %d with status %s", i, p.getDiseaseStatus())
						.allMatch(t -> t.getQuarantineStatus() == EpisimPerson.QuarantineStatus.atHome);
			else if (i >= 625)
				assertThat(p.getTraceableContactPersons(0))
						.describedAs("Person %d", i)
						.allMatch(t -> t.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no);

		}
	}

	@Test
	public void tracingDelay() {

		// test with delay
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(0);
		tracingConfig.setTracingDelay_days(2);

		EpisimPerson p = EpisimTestUtils.createPerson(reporting);
		p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);
		for (int day = 0; day <= 5; day++) {
			model.setIteration(day);
			model.updateState(p, day);
		}

		p.addTraceableContactPerson(EpisimTestUtils.createPerson(reporting), 5 * 24 * 3600);

		model.updateState(p, 6);
		assertThat(p.getTraceableContactPersons(0)).allMatch(t -> t.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no);


		model.updateState(p, 7);
		assertThat(p.getTraceableContactPersons(0)).allMatch(t -> t.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no);


		model.updateState(p, 8);
		assertThat(p.getTraceableContactPersons(0)).allMatch(t -> t.getQuarantineStatus() == EpisimPerson.QuarantineStatus.atHome);

	}

	@Test
	public void tracingDistance() {

		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(0);
		tracingConfig.setTracingDelay_days(2);
		tracingConfig.setTracingPeriod_days(1);

		EpisimPerson p = EpisimTestUtils.createPerson(reporting);
		p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);
		for (int day = 0; day <= 5; day++) {
			model.updateState(p, day);
		}

		EpisimPerson first = EpisimTestUtils.createPerson(reporting);
		EpisimPerson last = EpisimTestUtils.createPerson(reporting);

		p.addTraceableContactPerson(first, 4 * 24 * 3600);
		p.addTraceableContactPerson(last, 5 * 24 * 3600);

		model.updateState(p, 6);
		model.updateState(p, 7);
		model.updateState(p, 8);


		assertThat(first.getQuarantineStatus()).isEqualTo(EpisimPerson.QuarantineStatus.no);
		assertThat(last.getQuarantineStatus()).isEqualTo(EpisimPerson.QuarantineStatus.atHome);

	}

	@Test
	public void traceHome() {

		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(0);
		tracingConfig.setTracingDelay_days(0);
		tracingConfig.setTracingProbability(0);
		tracingConfig.setQuarantineHouseholdMembers(false);

		// needed to update probability
		model.setIteration(1);

		EpisimPerson p = EpisimTestUtils.createPerson(reporting);
		p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);
		for (int day = 0; day <= 5; day++) {
			model.setIteration(day);
			model.updateState(p, day);
		}

		p.getAttributes().putAttribute("homeId", "1");

		EpisimPerson contact = EpisimTestUtils.createPerson(reporting);
		contact.getAttributes().putAttribute("homeId", "1");

		p.addTraceableContactPerson(contact, 5 * 24 * 3600);

		model.updateState(p, 6);
		assertThat(p.getTraceableContactPersons(0)).allMatch(t -> t.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no);

		// person is not traced one day later when activated, as person is only traced one time

		tracingConfig.setQuarantineHouseholdMembers(true);
		tracingConfig.setTracingDelay_days(1);

		model.setIteration(7);
		model.updateState(p, 7);
		assertThat(p.getTraceableContactPersons(0)).allMatch(t -> t.getQuarantineStatus() == EpisimPerson.QuarantineStatus.atHome);


	}

	@Test
	public void defaultTransition() {

		// Depends on random seed
		EpisimPerson p = EpisimTestUtils.createPerson(reporting);
		p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);

		for (int day = 0; day <= 26; day++) {
			model.updateState(p, day);

			if (day == 3) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.infectedButNotContagious);
			if (day == 4) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.contagious);
			if (day == 6) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.showingSymptoms);
			if (day == 16) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.recovered);
			if (day == 26) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.susceptible);
		}

		// reinfection after susceptible
		p.setDiseaseStatus(30 * 86400, DiseaseStatus.infectedButNotContagious);

		for (int day = 30; day <= 34; day++) {
			model.updateState(p, day);
			if (day == 33) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.infectedButNotContagious);
			if (day == 34) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.contagious);
		}

	}


	@Test
	public void showingSymptom() {

		// 80% should show symptoms after 6 days
		int showSymptoms = 0;
		for (int i = 0; i < 10_000; i++) {

			EpisimPerson p = EpisimTestUtils.createPerson(reporting);
			p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);

			for (int day = 0; day <= 6; day++) {
				model.updateState(p, day);
			}

			if (p.getDiseaseStatus() == DiseaseStatus.showingSymptoms)
				showSymptoms++;

		}

		assertThat(showSymptoms)
				.isCloseTo((int) (10_000 * 0.8), Percentage.withPercentage(1));

	}


	@Test
	public void transitionDay() {

		EpisimConfigGroup config = new EpisimConfigGroup();

		config.setProgressionConfig(Transition.config()
				.from(DiseaseStatus.infectedButNotContagious,
						to(DiseaseStatus.contagious, Transition.fixed(4)))
				.from(DiseaseStatus.contagious,
						to(DiseaseStatus.showingSymptoms, Transition.logNormalWithMeanAndStd(10, 5)),
						to(DiseaseStatus.recovered, Transition.logNormalWithMeanAndStd(10, 5)))
				.from(DiseaseStatus.showingSymptoms,
						to(DiseaseStatus.seriouslySick, Transition.fixed(0)),
						to(DiseaseStatus.recovered, Transition.fixed(0)))
				.from(DiseaseStatus.seriouslySick,
						to(DiseaseStatus.critical, Transition.fixed(0)),
						to(DiseaseStatus.recovered, Transition.fixed(0)))
				.from(DiseaseStatus.critical,
						to(DiseaseStatus.seriouslySickAfterCritical, Transition.fixed(0)))
				.from(DiseaseStatus.seriouslySickAfterCritical,
						to(DiseaseStatus.recovered, Transition.fixed(0)))
				.from(DiseaseStatus.recovered,
						to(DiseaseStatus.susceptible, Transition.fixed(40)))
				.build());

		SplittableRandom rnd = new SplittableRandom(1);
		model = new ConfigurableProgressionModel(rnd, config, tracingConfig, new DefaultDiseaseStatusTransitionModel(rnd, vaccinationConfig, strainConfig));

		List<Double> recoveredDays = new ArrayList<>();

		for (int i = 0; i < 10_000; i++) {

			EpisimPerson p = EpisimTestUtils.createPerson(reporting);
			p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);

			int toDay = 40;
			for (int day = 0; day <= toDay; day++) {
				model.updateState(p, day);
			}

			if (p.getDiseaseStatus() == DiseaseStatus.recovered) {
				recoveredDays.add((double) toDay - p.daysSince(DiseaseStatus.recovered, toDay));
			}

			if (p.hadDiseaseStatus(DiseaseStatus.critical)) {
				// Transitions all happened on the same day
				assertThat(p.daysSince(DiseaseStatus.critical, toDay))
						.isEqualTo(p.daysSince(DiseaseStatus.showingSymptoms, toDay))
						.isEqualTo(p.daysSince(DiseaseStatus.seriouslySick, toDay));
			}

		}

		// In average persons should recover on day 14
		assertThat(new Mean().evaluate(Doubles.toArray(recoveredDays)))
				.isCloseTo(14, Percentage.withPercentage(1));
	}


}
