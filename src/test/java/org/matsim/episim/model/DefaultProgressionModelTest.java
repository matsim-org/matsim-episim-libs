package org.matsim.episim.model;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.matsim.episim.*;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DefaultProgressionModelTest {

	private EpisimReporting reporting;
	private ProgressionModel model;
	private TracingConfigGroup tracingConfig;

	@Before
	public void setup() {
		reporting = mock(EpisimReporting.class);
		tracingConfig = new TracingConfigGroup();
		model = new DefaultProgressionModel(new SplittableRandom(1), new EpisimConfigGroup(), tracingConfig);
	}

	@Test
	public void tracing() {

		// this test depends a bit on the random seed
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(0);
		tracingConfig.setTracingDelay(0);

		EpisimPerson p = EpisimTestUtils.createPerson(reporting);
		p.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.infectedButNotContagious);
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
		p.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.infectedButNotContagious);
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
		p.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.infectedButNotContagious);
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
		p.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.infectedButNotContagious);
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
		p.setDiseaseStatus(0, EpisimPerson.DiseaseStatus.infectedButNotContagious);
		for (int day = 0; day <= 16; day++) {
			model.updateState(p, day);

			if (day == 3) assertThat(p.getDiseaseStatus()).isEqualTo(EpisimPerson.DiseaseStatus.infectedButNotContagious);
			if (day == 4) assertThat(p.getDiseaseStatus()).isEqualTo(EpisimPerson.DiseaseStatus.contagious);
			if (day == 6) assertThat(p.getDiseaseStatus()).isEqualTo(EpisimPerson.DiseaseStatus.showingSymptoms);
			if (day == 16) assertThat(p.getDiseaseStatus()).isEqualTo(EpisimPerson.DiseaseStatus.recovered);

		}
	}

	@Test
	public void logNormal() {

		SplittableRandom rnd = new SplittableRandom(1);

		Transition t = Transition.logNormal(10, 5);
		double[] values = new double[100_000];
		for (int i = 0; i < values.length; i++) {
			values[i] = t.getTransitionDay(rnd);
		}

		assertThat(new Mean().evaluate(values))
				.isCloseTo(10, Offset.offset(0.1));

		assertThat(new StandardDeviation().evaluate(values))
				.isCloseTo(5, Offset.offset(0.1));

	}
}
