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
 * Vaccinate agents that have not received booster yet. 
 */
public class VaccinationStrategy2 implements VaccinationModel {

	private final SplittableRandom rnd;
	private final Config config;

	@Inject
	public VaccinationStrategy2(SplittableRandom rnd, Config config) {
		this.rnd = rnd;
		this.config = config;
	}

	@Override
	public void handleVaccination(Map<Id<Person>, EpisimPerson> persons, LocalDate date, int iteration, double now) {
		

		if (date.isAfter(config.start)) {
			//handle young persons
			if (config.vaccinateYoung) {
				// collect persons of right age:
				List<EpisimPerson> youngPersons = persons.values().stream()
						.filter(p -> p.getAge() < 50 && p.getAge() > 17)
						.collect(Collectors.toList());

				// out of these, collect persons of right status:
				List<EpisimPerson> youngCandidates = youngPersons.stream()
						.filter(EpisimPerson::isVaccinable)
						.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
						.filter(p -> p.getNumVaccinations() < 2)
						.filter(p -> p.getNumVaccinations() > 0 ? p.daysSinceVaccination(p.getNumVaccinations() - 1, iteration) > 90 : true)
						.filter(config.vaccinateRecovered ? p -> true : p -> p.getNumInfections() == 0)
						.collect(Collectors.toList());

				// shuffle them (can't say if this uses the same seed in every iteration or not)
				Collections.shuffle(youngCandidates, new Random(EpisimUtils.getSeed(rnd)));

				int vaccinationsLeft = (int) (0.005 * youngPersons.size());
				
				int n = Math.min(youngCandidates.size(), vaccinationsLeft);

				for (int i = 0; i < n; i++) {
					EpisimPerson person = youngCandidates.get(i);
					vaccinate(person, iteration, VaccinationType.mRNA);
					vaccinationsLeft--;
				}
				// (the above implies that this should take about 100 days. In the simulation, it is done after less than a month.)

			}
			
			//handle old persons
			if (config.vaccinateOld) {
				List<EpisimPerson> oldPersons = persons.values().stream()
						.filter(p -> p.getAge() > 49)
						.collect(Collectors.toList());
			
				List<EpisimPerson> oldCanidates = oldPersons.stream()
						.filter(EpisimPerson::isVaccinable)
						.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
						.filter(p -> p.getNumVaccinations() < 2)
						.filter(p -> p.getNumVaccinations() > 0 ? p.daysSinceVaccination(p.getNumVaccinations() - 1, iteration) > 90 : true)
						.filter(config.vaccinateRecovered ? p -> true : p -> p.getNumInfections() == 0)
						.collect(Collectors.toList());
		
				Collections.shuffle(oldCanidates, new Random(EpisimUtils.getSeed(rnd)));
				
				int vaccinationsLeft = (int) (0.005 * oldPersons.size());
				
				int n = Math.min(oldCanidates.size(), vaccinationsLeft);

				for (int i = 0; i < n; i++) {
					EpisimPerson person = oldCanidates.get(i);
					vaccinate(person, iteration, VaccinationType.mRNA);
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
		
		private final boolean vaccinateRecovered;
		
		private final boolean vaccinateYoung;
		
		private final boolean vaccinateOld;

		public Config(LocalDate start, boolean vaccinateRecovered, boolean vaccinateYoung, boolean vaccinateOld) {
			this.start = start;
			this.vaccinateRecovered = vaccinateRecovered;
			this.vaccinateYoung = vaccinateYoung;
			this.vaccinateOld = vaccinateOld;

		}
	}

}
