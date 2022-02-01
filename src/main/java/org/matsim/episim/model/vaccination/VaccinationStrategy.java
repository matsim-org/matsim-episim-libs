package org.matsim.episim.model.vaccination;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.model.VaccinationType;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
public class VaccinationStrategy implements VaccinationModel {

	private final SplittableRandom rnd;
	private final Config config;


	@Inject
	public VaccinationStrategy(SplittableRandom rnd, Config config) {
		this.rnd = rnd;
		this.config = config;
	}

	@Override
	public void handleVaccination(Map<Id<Person>, EpisimPerson> persons, LocalDate date, int iteration, double now) {

		if (date.isBefore(config.start))
			return;

		List<EpisimPerson> candidates = persons.values().stream()
				.filter(EpisimPerson::isVaccinable)
				.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible && !p.isRecentlyRecovered(iteration, 180))
				.filter(p -> p.getNumVaccinations() == 2) // TODO: should persons get 4th shot before booster?
				.collect(Collectors.toList());


		Collections.shuffle(candidates, new Random(EpisimUtils.getSeed(rnd)));

		int vaccinationsLeft = 0; // TODO: number of vaccinations
		int n = Math.min(candidates.size(), vaccinationsLeft);

		for (int i = 0; i < n; i++) {
			EpisimPerson person = candidates.get(i);
			vaccinate(person, iteration, VaccinationType.generic);
			vaccinationsLeft--;
		}

	}


	public static class Config {

		/**
		 * Start of vaccination campaign.
		 */
		private final LocalDate start;

		public Config(LocalDate start) {
			this.start = start;
		}
	}

}