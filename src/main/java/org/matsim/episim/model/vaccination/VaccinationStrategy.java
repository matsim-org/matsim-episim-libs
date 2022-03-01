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
 * Update vaccination campaign for people aged 18-59 and 60+. 
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

		if (date.isAfter(config.start) && date.isBefore(config.start.plusDays(config.campaignDuration))) {
			//handle young persons
			if (config.complianceYoung > 0.0) 
			{
				List<EpisimPerson> youngPersons = persons.values().stream()
						.filter(p -> p.getAge() < 60 && p.getAge() > 17)
						.collect(Collectors.toList());
			
				List<EpisimPerson> youngCandidates = youngPersons.stream()
						.filter(EpisimPerson::isVaccinable)
						.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
						.filter(p -> p.getNumVaccinations() > 0 ? p.daysSinceVaccination(p.getNumVaccinations() - 1, iteration) > 90 : true)
						.collect(Collectors.toList());
		
				Collections.shuffle(youngCandidates, new Random(EpisimUtils.getSeed(rnd)));
				
				int vaccinationsLeft = (int) (config.complianceYoung * youngPersons.size() / config.campaignDuration);

				int n = Math.min(youngCandidates.size(), vaccinationsLeft);

				for (int i = 0; i < n; i++) {
					EpisimPerson person = youngCandidates.get(i);
					vaccinate(person, iteration, config.vaccinationType);
					vaccinationsLeft--;
				}	
			}
			
			//handle old persons
			if (config.complianceOld > 0.0) 
			{
				List<EpisimPerson> oldPersons = persons.values().stream()
						.filter(p -> p.getAge() > 59)
						.collect(Collectors.toList());
			
				List<EpisimPerson> oldCanidates = oldPersons.stream()
						.filter(EpisimPerson::isVaccinable)
						.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
						.filter(p -> p.getNumVaccinations() > 0 ? p.daysSinceVaccination(p.getNumVaccinations() - 1, iteration) > 90 : true)
						.collect(Collectors.toList());
		
				Collections.shuffle(oldCanidates, new Random(EpisimUtils.getSeed(rnd)));
				
				int vaccinationsLeft = (int) (config.complianceOld * oldPersons.size() / config.campaignDuration);
				int n = Math.min(oldCanidates.size(), vaccinationsLeft);

				for (int i = 0; i < n; i++) {
					EpisimPerson person = oldCanidates.get(i);
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
		
		private final double complianceYoung;

		private final double complianceOld;


		public Config(LocalDate start, int campaignDuration, VaccinationType vaccinationType, double complianceYoung, double complianceOld) {
			this.start = start;
			this.campaignDuration = campaignDuration;
			this.vaccinationType = vaccinationType;
			this.complianceYoung = complianceYoung;
			this.complianceOld = complianceOld;

		}
	}

}
