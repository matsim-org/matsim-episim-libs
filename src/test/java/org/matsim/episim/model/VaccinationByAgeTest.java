package org.matsim.episim.model;

import org.junit.Before;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimTestUtils;
import org.matsim.episim.VaccinationConfigGroup;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


public class VaccinationByAgeTest {

	VaccinationByAge model;

	@Before
	public void setUp() throws Exception {
		model = new VaccinationByAge(new SplittableRandom(0), new VaccinationConfigGroup());
	}

	@Test
	public void handleVaccination() {

		Map<Id<Person>, EpisimPerson> persons = new HashMap<>();

		for (int i = 0; i < 100; i++) {
			EpisimPerson p = EpisimTestUtils.createPerson(false, 30);
			persons.put(p.getPersonId(), p);
		}


		for (int i = 0; i < 100; i++) {
			model.handleVaccination(persons, false, 100, LocalDate.now(), i, 86400 * i);
		}

		assertThat(persons.values())
				.allMatch(p -> p.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.no);

	}

	@Test
	public void handleVaccinationsWithAge() {

		SplittableRandom rnd = new SplittableRandom(0);
		Map<Id<Person>, EpisimPerson> persons = new HashMap<>();

		for (int i = 0; i < 1000; i++) {
			EpisimPerson p = EpisimTestUtils.createPerson(true, rnd.nextInt(12, 100));
			persons.put(p.getPersonId(), p);
		}

		int sum = 0;

		for (int i = 0; i < 10; i++) {
			sum += model.handleVaccination(persons, false, 300, LocalDate.now(), i, 86400 * i);

			List<EpisimPerson> vaccinated = persons.values().stream().filter(p -> p.getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes).collect(Collectors.toList());
			assertThat(vaccinated)
					.hasSize(sum);

		}

	}
}
