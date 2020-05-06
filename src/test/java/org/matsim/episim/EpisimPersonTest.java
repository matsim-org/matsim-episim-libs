package org.matsim.episim;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EpisimPersonTest {

	@Test
	public void daysSince() {

		EpisimPerson p = EpisimTestUtils.createPerson("work", null);
		double now = EpisimUtils.getCorrectedTime(0, 5);

		p.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.infectedButNotContagious);
		assertThat(p.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, 5))
				.isEqualTo(0);

		assertThat(p.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, 10))
				.isEqualTo(5);

		// change during the third day
		now = EpisimUtils.getCorrectedTime(3600, 3);
		p.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.critical);
		assertThat(p.daysSince(EpisimPerson.DiseaseStatus.critical, 4))
				.isEqualTo(1);

		now = EpisimUtils.getCorrectedTime(24 * 60 * 60 - 1, 4);
		p.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.recovered);
		assertThat(p.daysSince(EpisimPerson.DiseaseStatus.recovered, 4))
				.isEqualTo(0);

	}


	@Test
	public void isTraceable() {

		EpisimPerson p1 = EpisimTestUtils.createPerson("work", null);
		EpisimPerson p2 = EpisimTestUtils.createPerson("work", null);

		p1.addTraceableContactPerson(p2, 0);
		assertThat(p1.getTraceableContactPersons(0)).containsExactly(p2);

		p1.clearTraceableContractPersons(Integer.MAX_VALUE);

		p1.getAttributes().putAttribute(EpisimPerson.TRACING_ATTR, true);
		p2.getAttributes().putAttribute(EpisimPerson.TRACING_ATTR, false);

		assertThat(p1.isTraceable()).isTrue();

		// not traced because p2 is not traceable
		p1.addTraceableContactPerson(p2, 0);
		assertThat(p1.getTraceableContactPersons(0))
				.isEmpty();

		p2.getAttributes().putAttribute(EpisimPerson.TRACING_ATTR, true);

		p1.addTraceableContactPerson(p2, 0);
		assertThat(p1.getTraceableContactPersons(0)).containsExactly(p2);

	}
}
