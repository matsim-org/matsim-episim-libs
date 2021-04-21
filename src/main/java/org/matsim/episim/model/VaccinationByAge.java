package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;

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
	public int handleVaccination(Map<Id<Person>, EpisimPerson> persons, boolean reVaccination, int availableVaccinations, int iteration, double now) {

		if (availableVaccinations == 0)
			return 0;

		List<EpisimPerson> candidates = persons.values().stream()
				.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
				.filter(p -> p.getVaccinationStatus() == (reVaccination ? EpisimPerson.VaccinationStatus.yes : EpisimPerson.VaccinationStatus.no))
				.filter(p -> p.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.no)
				.collect(Collectors.toList());

		Collections.shuffle(candidates, new Random(EpisimUtils.getSeed(rnd)));
		candidates = candidates.stream()
				.sorted(Comparator.comparingInt(EpisimPerson::getAge).reversed())
				.collect(Collectors.toList());

		int vaccinationsLeft = availableVaccinations;
		int vaccinated = 0;
		for (int i = 0; i < candidates.size() && vaccinationsLeft > 0; i++) {
			EpisimPerson randomPerson = candidates.get(i);
			vaccinate(randomPerson, iteration, reVaccination);
			vaccinationsLeft--;
		}

		return vaccinated;
	}
}
