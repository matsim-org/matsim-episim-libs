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
 * Update vaccination campaign.
 */
public class VaccinationStrategyBMBF0422 implements VaccinationModel {

	private final SplittableRandom rnd;
	private final Config config;

	@Inject
	public VaccinationStrategyBMBF0422(SplittableRandom rnd, Config config) {
		this.rnd = rnd;
		this.config = config;
	}

	@Override
	public void handleVaccination(Map<Id<Person>, EpisimPerson> persons, LocalDate date, int iteration, double now) {

		if (date.isAfter(config.start) && date.isBefore(config.start.plusDays(config.campaignDuration))) {
			//handle young persons
			if (config.compliance > 0.0) 
			{
				List<EpisimPerson> filteredPersons = persons.values().stream()
						.filter(p -> p.getAge() >= config.minAge)
						.collect(Collectors.toList());
			
				List<EpisimPerson> candidates = filteredPersons.stream()
						.filter(EpisimPerson::isVaccinable)
						.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
						.filter(p -> p.getNumVaccinations() > 0 ? p.daysSinceVaccination(p.getNumVaccinations() - 1, iteration) > 90 : true)
						.collect(Collectors.toList());
		
				Collections.shuffle(candidates, new Random(EpisimUtils.getSeed(rnd)));
				
				int vaccinationsLeft = (int) (config.compliance * filteredPersons.size() / config.campaignDuration);

				int n = Math.min(candidates.size(), vaccinationsLeft);

				for (int i = 0; i < n; i++) {
					EpisimPerson person = candidates.get(i);
					vaccinate(person, iteration, config.vaccinationType);
					vaccinationsLeft--;
				}	
			}
			

		}

	}


	public static class Config {

		/**
		 * Start of vaccination campaign.
		 */
		private final LocalDate start;
		/**
		 * Duration of vaccination campaign.
		 */
		private final int campaignDuration;

		private final VaccinationType vaccinationType;
		
		private final int minAge;
		
		private final double compliance;
		


		public Config(LocalDate start, int campaignDuration, VaccinationType vaccinationType, int minAge, double compliance) {
			this.start = start;
			this.campaignDuration = campaignDuration;
			this.vaccinationType = vaccinationType;
			this.minAge = minAge;
			this.compliance = compliance;

		}
	}

}
