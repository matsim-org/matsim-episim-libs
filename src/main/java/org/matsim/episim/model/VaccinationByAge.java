package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vaccinate people starting with oldest first
 */
public class VaccinationByAge implements VaccinationModel {

	private final SplittableRandom rnd;

	@Inject
	public VaccinationByAge(SplittableRandom rnd) {
		this.rnd = rnd;
	}

	@Override
	public int handleVaccination(Map<Id<Person>, EpisimPerson> persons, int availableVaccinations, int iteration, double now) {
		List<EpisimPerson> candidates = persons.values().stream()
				.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
				.filter(p -> p.getVaccinationStatus() == EpisimPerson.VaccinationStatus.no)
				.collect(Collectors.toList());

		Collections.shuffle(candidates, new Random(rnd.nextLong()));
		candidates = candidates.stream()
				.sorted(Comparator.comparingInt(EpisimPerson::getAge).reversed())
				.collect(Collectors.toList());

		int vaccinationsLeft = availableVaccinations;
		int vaccinated = 0;
		for (int i = 0; i < candidates.size() && vaccinationsLeft > 0; i++) {
			EpisimPerson randomPerson = candidates.get(i);
			randomPerson.setVaccinationStatus(EpisimPerson.VaccinationStatus.yes, iteration);
			vaccinationsLeft--;
		}

		return vaccinated;
	}
}
