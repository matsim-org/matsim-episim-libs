package org.matsim.episim.model.vaccination;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
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
public class VaccinationStrategyBMBF202310XX implements VaccinationModel {

	private final SplittableRandom rnd;
	private final Config config;

	@Inject
	public VaccinationStrategyBMBF202310XX(SplittableRandom rnd, Config config) {
		this.rnd = rnd;
		this.config = config;
	}

	@Override
	public void handleVaccination(Map<Id<Person>, EpisimPerson> persons, LocalDate date, int iteration, double now) {

		if (date.isAfter(config.start) && date.isBefore(config.start.plusDays(config.campaignDuration))) {

			if (config.complianceByAge.values().doubleStream()
					.reduce(0, Double::sum) > 0) {

				List<EpisimPerson> filteredPersons = new ArrayList<>(persons.values());

				List<EpisimPerson> candidates = filteredPersons.stream()
						.filter(EpisimPerson::isVaccinable) // todo: what determines who is vaccinable?
						.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
						.filter(p -> p.getNumVaccinations() >= config.vaccinationPool.vaxCnt)
						.filter(p -> p.getNumVaccinations() == 0 || p.daysSinceVaccination(p.getNumVaccinations() - 1, iteration) > config.minDaysAfterVaccination) // only people who've had their last vaccination more than 90 days ago
						.filter(p -> p.getNumInfections() == 0 || p.daysSinceInfection(p.getNumInfections() - 1, iteration) > config.minDaysAfterInfection) // only people who've had their last vaccination more than 90 days ago
						.collect(Collectors.toList());

				int num0 = (int) filteredPersons.stream().filter(p -> (p.getAge() >= 0 && p.getAge() < 12)).count();
				int num12 = (int) filteredPersons.stream().filter(p -> (p.getAge() >= 12 && p.getAge() < 18)).count();
				int num18 = (int) filteredPersons.stream().filter(p -> (p.getAge() >= 18 && p.getAge() < 60)).count();
				int num60 = (int) filteredPersons.stream().filter(p -> p.getAge() >= 60).count();

				double numVaccines = num0 * config.complianceByAge.get(0)
						+ num12 * config.complianceByAge.get(12)
						+ num18 * config.complianceByAge.get(18)
						+ num60 * config.complianceByAge.get(60);

				int vaccinationsLeft = (int) (numVaccines / config.campaignDuration);


				List<EpisimPerson> people0 = candidates.stream().filter(p -> (p.getAge() >= 0 && p.getAge() < 12)).collect(Collectors.toList());
				List<EpisimPerson> people12 = candidates.stream().filter(p -> (p.getAge() >= 12 && p.getAge() < 18)).collect(Collectors.toList());
				List<EpisimPerson> people18 = candidates.stream().filter(p -> (p.getAge() >= 18 && p.getAge() < 60)).collect(Collectors.toList());
				List<EpisimPerson> people60 = candidates.stream().filter(p -> p.getAge() >= 60).collect(Collectors.toList());

				final List<EpisimPerson>[] perAge = new List[4];
				perAge[0] = people0;
				perAge[1] = people12;
				perAge[2] = people18;
				perAge[3] = people60;

				int ageIndex = 3;
				while (ageIndex >= 0 && vaccinationsLeft > 0) {

					List<EpisimPerson> candidatesForAge = perAge[ageIndex];

					// list is shuffled to avoid eventual bias
					if (candidatesForAge.size() > vaccinationsLeft)
						Collections.shuffle(perAge[ageIndex], new Random(EpisimUtils.getSeed(rnd)));


					int vaccinesForDayAndAgeGroup = Math.min(candidatesForAge.size(), vaccinationsLeft);
					for (int i = 0; i < vaccinesForDayAndAgeGroup; i++) {
						EpisimPerson person = candidatesForAge.get(i);
						vaccinate(person, iteration, config.vaccinationType);
						vaccinationsLeft--;
					}

					ageIndex--;

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

		private final Int2DoubleMap complianceByAge;

		private final VaccinationPool vaccinationPool;

		private final int minDaysAfterVaccination;

		private final int minDaysAfterInfection;

		public enum VaccinationPool {

			unvaccinated(0),

			vaccinated(1),

			boostered(2);

			private final int vaxCnt;

			VaccinationPool(int vaxCnt) {
				this.vaxCnt = vaxCnt;
			}

		}


		public Config(LocalDate start, int campaignDuration, VaccinationType vaccinationType,
					  Int2DoubleMap complianceByAge, VaccinationPool vaccinationPool,
					  int minDaysAfterInfection, int minDaysAfterVaccination) {

			this.start = start;
			this.campaignDuration = campaignDuration;
			this.vaccinationType = vaccinationType;
			this.complianceByAge = complianceByAge;
			this.vaccinationPool = vaccinationPool;
			this.minDaysAfterInfection = minDaysAfterInfection;
			this.minDaysAfterVaccination = minDaysAfterVaccination;

		}
	}

}
