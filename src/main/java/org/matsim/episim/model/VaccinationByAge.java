package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;

import java.util.*;

/**
 * Vaccinate people starting with oldest first
 */
public class VaccinationByAge implements VaccinationModel {

	private final SplittableRandom rnd;

	private final static int MAX_AGE = 130;
	private final static int MINIMUM_AGE_FOR_VACCINATIONS = 6;

	@Inject
	public VaccinationByAge(SplittableRandom rnd) {
		this.rnd = rnd;
	}

	@Override
	public int handleVaccination(Map<Id<Person>, EpisimPerson> persons, boolean reVaccination, int availableVaccinations, int iteration, double now) {

		if (availableVaccinations == 0)
			return 0;

		// perAge is an ArrayList where we have for each age (in years) an
		// ArrayList of Persons that are qualified for a vaccination
		final List<EpisimPerson>[] perAge = new List[MAX_AGE];

		for (int i = 0; i <= MAX_AGE; i++)
			perAge[i] = new ArrayList<>();

		for (EpisimPerson p : persons.values()) {
			if (p.isVaccinable() &&
					p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible &&
					p.getVaccinationStatus() == (reVaccination ? EpisimPerson.VaccinationStatus.yes : EpisimPerson.VaccinationStatus.no) &&
					p.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.no) {

				perAge[p.getAge()].add(p);
			}
		}

		int age = MAX_AGE - 1;
		int vaccinationsLeft = availableVaccinations;

		while (vaccinationsLeft > 0) {

			if (perAge[age].size() == 0) {
				age--;

				if (age < MINIMUM_AGE_FOR_VACCINATIONS)
					return availableVaccinations - vaccinationsLeft;

				// there are not enough vaccinationsLeft for the Persons of
				// this age, so we shuffle this set for we get the first n Persons
				if (perAge[age].size() > vaccinationsLeft)
					Collections.shuffle(perAge[age], new Random(EpisimUtils.getSeed(rnd)));
				continue;
			}

			List<EpisimPerson> candidates = perAge[age];
			for (int i = 0; i < candidates.size() && vaccinationsLeft > 0; i++) {
				EpisimPerson person = candidates.get(i);
				vaccinate(person, iteration, reVaccination);
				vaccinationsLeft--;
			}
		}

		return availableVaccinations - vaccinationsLeft;
	}
}
