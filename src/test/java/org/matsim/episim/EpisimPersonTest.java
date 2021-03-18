package org.matsim.episim;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

import java.io.*;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class EpisimPersonTest {

	@Test
	public void daysSince() {

		EpisimPerson p = EpisimTestUtils.createPerson("work", null);
		double now = EpisimUtils.getCorrectedTime(0, 0, 5);

		p.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.infectedButNotContagious);
		assertThat(p.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, 5))
				.isEqualTo(0);

		assertThat(p.daysSince(EpisimPerson.DiseaseStatus.infectedButNotContagious, 10))
				.isEqualTo(5);

		// change during the third day
		now = EpisimUtils.getCorrectedTime(0, 3600, 3);
		p.setDiseaseStatus(now, EpisimPerson.DiseaseStatus.critical);
		assertThat(p.daysSince(EpisimPerson.DiseaseStatus.critical, 4))
				.isEqualTo(1);

		now = EpisimUtils.getCorrectedTime(0, 24 * 60 * 60 - 1, 4);
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

		p1.setTraceable(true);
		p2.setTraceable(false);

		assertThat(p1.isTraceable()).isTrue();

		// not traced because p2 is not traceable
		p1.addTraceableContactPerson(p2, 0);
		assertThat(p1.getTraceableContactPersons(0))
				.isEmpty();

		p2.setTraceable(true);

		p1.addTraceableContactPerson(p2, 0);
		assertThat(p1.getTraceableContactPersons(0)).containsExactly(p2);

	}

	@Test
	public void activities() {

		EpisimPerson p = EpisimTestUtils.createPerson("work", null);

		p.setStartOfDay(DayOfWeek.MONDAY);

		p.addToTrajectory(0, new EpisimPerson.Activity("home", null));
		p.addToTrajectory(1000, new EpisimPerson.Activity("work", null));
		p.addToTrajectory(2000, new EpisimPerson.Activity("edu", null));

		p.setEndOfDay(DayOfWeek.MONDAY);

		assertThat(p.getActivity(DayOfWeek.MONDAY, 0).actType)
				.isEqualTo("home");

		assertThat(p.getActivity(DayOfWeek.MONDAY, 1000).actType)
				.isEqualTo("home");

		assertThat(p.getPrevActivity(DayOfWeek.MONDAY, 1000))
				.isNull();

		assertThat(p.getNextActivity(DayOfWeek.MONDAY, 1000).actType)
				.isEqualTo("work");


		assertThat(p.getActivity(DayOfWeek.MONDAY, 1001).actType)
				.isEqualTo("work");

		assertThat(p.getNextActivity(DayOfWeek.MONDAY, 1001).actType)
				.isEqualTo("edu");

		assertThat(p.getNextActivity(DayOfWeek.MONDAY, 2001))
				.isNull();

	}

	@Test
	public void readWrite() throws IOException {

		EpisimPerson p1 = EpisimTestUtils.createPerson("work", null);

		p1.addTraceableContactPerson(EpisimTestUtils.createPerson("home", null), 100);
		p1.setDiseaseStatus(100, EpisimPerson.DiseaseStatus.showingSymptoms);
		p1.setTraceable(true);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream bout = new ObjectOutputStream(out);

		p1.write(bout);

		bout.flush();

		Map<Id<Person>, EpisimPerson> persons = new HashMap<>();

		EpisimPerson p2 = EpisimTestUtils.createPerson("c1.0", null);
		p2.read(new ObjectInputStream(new ByteArrayInputStream(out.toByteArray())), persons);

		assertThat(p2.getDiseaseStatus())
				.isEqualTo(EpisimPerson.DiseaseStatus.showingSymptoms);

	}
}
