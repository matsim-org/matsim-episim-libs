package org.matsim.episim;

import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.model.VirusStrain;

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

		EpisimPerson p = EpisimTestUtils.createPerson();

		p.setStartOfDay(DayOfWeek.MONDAY);

		p.addToTrajectory(0, new EpisimConfigGroup.InfectionParams("home"),null);
		p.addToTrajectory(1000, new EpisimConfigGroup.InfectionParams("work"),null);
		p.addToTrajectory(2000, new EpisimConfigGroup.InfectionParams("edu"),null);

		p.setEndOfDay(DayOfWeek.MONDAY);

		assertThat(p.getActivity(DayOfWeek.MONDAY, 0).actType())
				.isEqualTo("home");

		assertThat(p.getActivity(DayOfWeek.MONDAY, 1000).actType())
				.isEqualTo("work");

		assertThat(p.getNextActivity(DayOfWeek.MONDAY, 1000).actType())
				.isEqualTo("edu");

		assertThat(p.getActivity(DayOfWeek.MONDAY, 1001).actType())
				.isEqualTo("work");

		assertThat(p.getNextActivity(DayOfWeek.MONDAY, 1001).actType())
				.isEqualTo("edu");

		assertThat(p.getNextActivity(DayOfWeek.MONDAY, 2001))
				.isNull();
	}

	@Test
	public void participation() {

		EpisimPerson p = EpisimTestUtils.createPerson();

		DayOfWeek d = DayOfWeek.MONDAY;
		p.setStartOfDay(d);

		p.addToTrajectory(0, new EpisimConfigGroup.InfectionParams("home"),null);
		p.addToTrajectory(1000, new EpisimConfigGroup.InfectionParams("work"),null);
		p.addToTrajectory(2000, new EpisimConfigGroup.InfectionParams("edu"),null);
		p.addToTrajectory(3000, new EpisimConfigGroup.InfectionParams("leisure"),null);

		p.setEndOfDay(d);
		p.initParticipation();

		p.getActivityParticipation().set(1, false);
		p.getActivityParticipation().set(2, false);


		assertThat(p.checkActivity(d, 0)).isEqualTo(true);
		assertThat(p.checkActivity(d, 1000)).isEqualTo(false);
		assertThat(p.checkActivity(d, 2500)).isEqualTo(false);
		assertThat(p.checkActivity(d, 3100)).isEqualTo(true);


		assertThat(p.checkNextActivity(d, 500)).isEqualTo(false);
		assertThat(p.checkNextActivity(d, 2000)).isEqualTo(true);

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

	/**
	 * Snapshot round-trip with a non-empty infection history and non-empty antibody / max-antibody
	 * maps: the maps must come back keyed by the same {@link VirusStrain} instances, not by synthetic
	 * "strain=>value" keys. Guards the serialization of {@code antibodies} / {@code maxAntibodies} in
	 * {@link EpisimPerson#write(ObjectOutput)}.
	 */
	@Test
	public void readWriteAntibodies() throws IOException {

		EpisimPerson p1 = EpisimTestUtils.createPerson("work", null);

		p1.setInitialInfection(100, VirusStrain.SARS_CoV_2);
		p1.setInitialInfection(200, VirusStrain.DELTA);

		p1.setAntibodies(VirusStrain.SARS_CoV_2, 1.75);
		p1.setAntibodies(VirusStrain.DELTA, 0.5);
		p1.setAntibodies(VirusStrain.OMICRON_BA1, 3.0);

		p1.updateMaxAntibodies(VirusStrain.SARS_CoV_2, 4.25);
		p1.updateMaxAntibodies(VirusStrain.DELTA, 2.0);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectOutputStream bout = new ObjectOutputStream(out);
		p1.write(bout);
		bout.flush();

		EpisimPerson p2 = EpisimTestUtils.createPerson("c1.0", null);
		p2.read(new ObjectInputStream(new ByteArrayInputStream(out.toByteArray())), new HashMap<>());

		assertThat(p2.getNumInfections()).isEqualTo(2);
		assertThat(p2.getVirusStrain(0)).isEqualTo(VirusStrain.SARS_CoV_2);
		assertThat(p2.getVirusStrain(1)).isEqualTo(VirusStrain.DELTA);

		assertThat(p2.getAntibodies().keySet())
				.containsExactlyInAnyOrder(VirusStrain.SARS_CoV_2, VirusStrain.DELTA, VirusStrain.OMICRON_BA1);
		assertThat(p2.getAntibodies(VirusStrain.SARS_CoV_2)).isEqualTo(1.75);
		assertThat(p2.getAntibodies(VirusStrain.DELTA)).isEqualTo(0.5);
		assertThat(p2.getAntibodies(VirusStrain.OMICRON_BA1)).isEqualTo(3.0);

		assertThat(p2.getMaxAntibodies().keySet())
				.containsExactlyInAnyOrder(VirusStrain.SARS_CoV_2, VirusStrain.DELTA);
		assertThat(p2.getMaxAntibodies(VirusStrain.SARS_CoV_2)).isEqualTo(4.25);
		assertThat(p2.getMaxAntibodies(VirusStrain.DELTA)).isEqualTo(2.0);
	}
}
