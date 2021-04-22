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

	private final int MAX_AGE = 130;

	@Inject
	public VaccinationByAge(SplittableRandom rnd) {
		this.rnd = rnd;
	}

	@Override
	public int handleVaccination(Map<Id<Person>, EpisimPerson> persons, boolean reVaccination, int availableVaccinations, int iteration, double now) {
		List<EpisimPerson> candidates = new ArrayList<EpisimPerson>(persons.values().size());

		// to improve the performance of sort below, we first remove the persons
		// from the candidates, that are you young to have a chance to get
		// a vaccination. 
		int ageGroup[] = new int[MAX_AGE];
		for (EpisimPerson p : persons.values()) {
			if (p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible &&
				p.getVaccinationStatus() == (reVaccination ? EpisimPerson.VaccinationStatus.yes :
											                 EpisimPerson.VaccinationStatus.no) &&
				p.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.no) {
				int age = p.getAge();
				ageGroup[age]++;
				candidates.add(p);
			}
		}

		// check the minimal age, that can potentially get an vaccination.
		int minAge = MAX_AGE;
		int numPersons = 0;
		while (numPersons < availableVaccinations) {
			minAge--;
			numPersons += ageGroup[minAge];
		}
		final int compareToAge = minAge;

		// and remove the persons, that are younger
		candidates = candidates.stream()
			    .filter(p -> p.getAge() >= compareToAge)
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
