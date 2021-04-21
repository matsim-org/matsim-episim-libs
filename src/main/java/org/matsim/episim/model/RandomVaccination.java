package org.matsim.episim.model;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

/**
 * Vaccinate people in the population randomly.
 */
public class RandomVaccination implements VaccinationModel {

	private static final Logger log = LogManager.getLogger(RandomVaccination.class);

	private final SplittableRandom rnd;

	@Inject
	public RandomVaccination(SplittableRandom rnd) {
		this.rnd = rnd;
	}


	@Override
	public int handleVaccination(Map<Id<Person>, EpisimPerson> persons, boolean reVaccination, int availableVaccinations, int iteration, double now) {
		List<EpisimPerson> candidates = persons.values().stream()
				.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
				.filter(p -> p.getVaccinationStatus() == (reVaccination ? EpisimPerson.VaccinationStatus.yes : EpisimPerson.VaccinationStatus.no))
				.filter(p -> p.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.no)
				.collect(Collectors.toList());

		if (candidates.size() < availableVaccinations) {
			log.warn("Not enough people to vaccinate left ({})", availableVaccinations);
			return 0;
		}

		int vaccinationsLeft = availableVaccinations;
		int vaccinated = 0;
		while (vaccinationsLeft > 0) {
			EpisimPerson randomPerson = candidates.get(rnd.nextInt(candidates.size()));
			if (randomPerson.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible) {
				vaccinate(randomPerson, iteration, reVaccination);
				vaccinationsLeft--;
			}
		}

		return vaccinated;
	}
}
