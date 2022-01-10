package org.matsim.episim.model.vaccination;

import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.model.VaccinationType;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Vaccinate people in the population randomly.
 */
public class RandomVaccination implements VaccinationModel {

	private static final Logger log = LogManager.getLogger(RandomVaccination.class);

	private final SplittableRandom rnd;
	private final VaccinationConfigGroup vaccinationConfig;

	@Inject
	public RandomVaccination(SplittableRandom rnd, VaccinationConfigGroup vaccinationConfig) {
		this.rnd = rnd;
		this.vaccinationConfig = vaccinationConfig;
	}


	@Override
	public int handleVaccination(Map<Id<Person>, EpisimPerson> persons, boolean reVaccination, int availableVaccinations, LocalDate date, int iteration, double now) {

		if (availableVaccinations <= 0)
			return 0;

		Map<VaccinationType, Double> prob = vaccinationConfig.getVaccinationTypeProb(date);

		List<EpisimPerson> candidates = persons.values().stream()
				.filter(EpisimPerson::isVaccinable)
				.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible && !p.isRecentlyRecovered(iteration, 180))
				.filter(p -> p.getVaccinationStatus() == (reVaccination ? EpisimPerson.VaccinationStatus.yes : EpisimPerson.VaccinationStatus.no))
				.filter(p -> p.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.no)
				.filter(p -> reVaccination ? p.daysSince(EpisimPerson.VaccinationStatus.yes, iteration) >= vaccinationConfig.getParams(p.getVaccinationType()).getBoostWaitPeriod() : true)
				.collect(Collectors.toList());

		if (candidates.isEmpty()) {
			log.warn("Not enough people to vaccinate left ({})", availableVaccinations);
			return 0;
		}

		Collections.shuffle(candidates, new Random(EpisimUtils.getSeed(rnd)));

		int vaccinationsLeft = availableVaccinations;
		for (int i = 0; i < Math.min(candidates.size(), vaccinationsLeft); i++) {
			EpisimPerson person = candidates.get(i);
			vaccinate(person, iteration, VaccinationModel.chooseVaccinationType(prob, rnd));
			vaccinationsLeft--;
		}

		return availableVaccinations - vaccinationsLeft;
	}
}
