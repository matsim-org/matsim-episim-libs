package org.matsim.episim.model;

import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;


public class VaccinationByAgeTest {

	VaccinationByAge model;

	@Before
	public void setUp() throws Exception {
		model = new VaccinationByAge(new SplittableRandom(0));
	}

	@Test
	public void handleVaccination() {

		Map<Id<Person>, EpisimPerson> persons = new HashMap<>();

		for (int i = 0; i < 100; i++) {
			EpisimPerson p = EpisimTestUtils.createPerson(false);
			persons.put(p.getPersonId(), p);
		}


		for (int i = 0; i < 100; i++) {
			model.handleVaccination(persons, false, 100, i, 86400 * i);
		}

		assertThat(persons.values())
				.allMatch(p -> p.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.no);

	}
}
