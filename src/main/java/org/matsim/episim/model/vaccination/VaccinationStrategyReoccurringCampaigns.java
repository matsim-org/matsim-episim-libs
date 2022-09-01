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
public class VaccinationStrategyReoccurringCampaigns implements VaccinationModel {

	private final SplittableRandom rnd;
	private final Config config;

	@Inject
	public VaccinationStrategyReoccurringCampaigns(SplittableRandom rnd, Config config) {
		this.rnd = rnd;
		this.config = config;
	}

	@Override
	public void handleVaccination(Map<Id<Person>, EpisimPerson> persons, LocalDate date, int iteration, double now) {

		// We check whether the current date occurs during one of the vaccination campaigns. If so, vaccinations are applied
		boolean occursInCampaign = false;
		VaccinationType vaccinationType = null;
		for (LocalDate startDate : config.startDateToVOC.keySet()) {
			if (date.isAfter(startDate) && date.isBefore(startDate.plusDays(config.campaignDuration))) {

				if (occursInCampaign) {
					throw new RuntimeException("this vaccination strategy doesn't allow for two vaccination campaigns to occur simultaneously");
				}

				occursInCampaign = true;
				vaccinationType = config.startDateToVOC.get(startDate);
			}
		}

		if (!occursInCampaign) {
			return;
		}

		//handle young persons
		if (config.complianceByAge.values().doubleStream()
				.reduce(0, Double::sum) > 0) {

			List<EpisimPerson> filteredPersons = new ArrayList<>(persons.values());

			List<EpisimPerson> candidates = filteredPersons.stream()
					.filter(EpisimPerson::isVaccinable) // todo: what determines who is vaccinable?
					.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
					.filter(p -> p.getNumVaccinations() > 1) // only boostered people are reboostered
					.filter(p -> p.daysSinceVaccination(p.getNumVaccinations() - 1, iteration) > 90) // only people who've had their last vaccination more than 90 days ago
					.collect(Collectors.toList());

	//				Collections.shuffle(candidates, new Random(EpisimUtils.getSeed(rnd)));


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
					vaccinate(person, iteration, vaccinationType);
					vaccinationsLeft--;
				}

				ageIndex--;

			}
		}


	}


	public static class Config {

		/**
		 * Start Dates of vaccination campaigns.
		 */
		private final Map<LocalDate, VaccinationType> startDateToVOC;
		/**
		 * Duration of vaccination campaign.
		 */
		private final int campaignDuration;

		private final Int2DoubleMap complianceByAge;



		public Config(Map<LocalDate, VaccinationType> startDateToVOC, int campaignDuration, Int2DoubleMap complianceByAge) {
			this.startDateToVOC = startDateToVOC;
			this.campaignDuration = campaignDuration;
			this.complianceByAge = complianceByAge;
		}
	}

}
