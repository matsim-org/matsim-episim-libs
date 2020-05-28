package org.matsim.episim.model;

import com.google.common.primitives.Doubles;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.assertj.core.data.Percentage;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.*;
import org.matsim.episim.EpisimPerson.DiseaseStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.matsim.episim.model.Transition.to;
import static org.mockito.Mockito.mock;

public class DefaultProgressionModelTest {

	private EpisimReporting reporting;
	private ProgressionModel model;
	private TracingConfigGroup tracingConfig;

	@Before
	public void setup() {
		reporting = mock(EpisimReporting.class);
		tracingConfig = new TracingConfigGroup();
		model = new NewProgressionModel(new SplittableRandom(1), new EpisimConfigGroup(), tracingConfig);
	}

	@Test
	public void tracing() {

		// this test depends a bit on the random seed
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(0);
		tracingConfig.setTracingDelay(0);

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
	public void tracingDelay() {

		// test with delay
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(0);
		tracingConfig.setTracingDelay(2);

		EpisimPerson p = EpisimTestUtils.createPerson(reporting);
		p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);
		for (int day = 0; day <= 5; day++) {
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
		tracingConfig.setTracingDelay(2);
		tracingConfig.setTracingDayDistance(1);

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
		tracingConfig.setTracingDelay(0);
		tracingConfig.setTracingProbability(0);
		tracingConfig.setQuarantineHouseholdMembers(false);

		EpisimPerson p = EpisimTestUtils.createPerson(reporting);
		p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);
		for (int day = 0; day <= 5; day++) {
			model.updateState(p, day);
		}

		p.getAttributes().putAttribute("homeId", "1");

		EpisimPerson contact = EpisimTestUtils.createPerson(reporting);
		contact.getAttributes().putAttribute("homeId", "1");

		p.addTraceableContactPerson(contact, 5 * 24 * 3600);

		model.updateState(p, 6);
		assertThat(p.getTraceableContactPersons(0)).allMatch(t -> t.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no);

		// person is traced one day later when activated

		tracingConfig.setQuarantineHouseholdMembers(true);
		tracingConfig.setTracingDelay(1);

		model.updateState(p, 7);
		assertThat(p.getTraceableContactPersons(0)).allMatch(t -> t.getQuarantineStatus() == EpisimPerson.QuarantineStatus.atHome);


	}

	@Test
	public void defaultTransition() {

		// Depends on random seed
		EpisimPerson p = EpisimTestUtils.createPerson(reporting);
		p.setDiseaseStatus(0, DiseaseStatus.infectedButNotContagious);
		for (int day = 0; day <= 16; day++) {
			model.updateState(p, day);

			if (day == 3) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.infectedButNotContagious);
			if (day == 4) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.contagious);
			if (day == 6) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.showingSymptoms);
			if (day == 16) assertThat(p.getDiseaseStatus()).isEqualTo(DiseaseStatus.recovered);

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
						to(DiseaseStatus.seriouslySick, Transition.fixed(0)))
				.build());

		model = new NewProgressionModel(new SplittableRandom(1), config, tracingConfig);

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
