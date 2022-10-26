package org.matsim.episim.model.vaccination;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdSet;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
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

	private final int dailyVaccinationsToBeDistributed;

	private final SplittableRandom rnd;
	private final Config config;

	private final IdSet<Person> boostBa5Yes = new IdSet<>(Person.class);

	private final IdSet<Person> boostBa5Emergency = new IdSet<>(Person.class);


	@Inject
	public VaccinationStrategyReoccurringCampaigns(SplittableRandom rnd, Config config, Scenario scenario) {
		this.rnd = rnd;
		this.config = config;

		Population population = scenario.getPopulation();

		for (Person person : population.getPersons().values()) {

			double randomNum = rnd.nextDouble();
			if (randomNum < 0.5) {
				boostBa5Yes.add(person.getId());
			}
			if (randomNum < 0.75) {
				boostBa5Emergency.add(person.getId());
			}
		}

		// calculate total number of vaccinations:
		dailyVaccinationsToBeDistributed = 1_500 / 4;

	}

	@Override
	public void handleVaccination(Map<Id<Person>, EpisimPerson> persons, LocalDate date, int iteration, double now) {

		// we check that the compliance of at least one age group is greater than 0.0. If not, there will be no vaccinations anyway
		if (dailyVaccinationsToBeDistributed <= 0) {
			return;
		}

		// Loop through all vaccination campaigns (via the start date of said campaign)
		for (LocalDate vaccinationCampaignStartDate : config.startDateToVaccinationCampaign.keySet()) {
			// Check whether current date falls within vaccination campaign
			if (date.isAfter(vaccinationCampaignStartDate.minusDays(1)) && date.isBefore(vaccinationCampaignStartDate.plusDays(config.campaignDuration))) {

				// vaccine type associated with campaign
				VaccinationType vaccinationType = config.startDateToVaccinationCampaign.get(vaccinationCampaignStartDate);

				// define eligible candidates for booster campaigns:
				//		?) if the person is vaccinable, whatever that means
				// 		a) not infected at the moment
				// 		b) is either already vaccinated or boostered, depending on the configuration
				// 		c) hasn't been vaccinated in the previous 90 days
				final int minDaysAfterInfection;
				if (date.isBefore(config.dateToTurnDownMinDaysAfterInfectionTo90)) {
					minDaysAfterInfection = config.minDaysAfterInfection;
				} else {
					minDaysAfterInfection = 90;
				}

				List<EpisimPerson> candidates = persons.values().stream()
						.filter(EpisimPerson::isVaccinable)
						.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
						.filter(p -> p.getNumVaccinations() >= config.vaccinationPool.vaxCnt)
						.filter(p -> p.getNumVaccinations() == 0 || p.daysSinceVaccination(p.getNumVaccinations() - 1, iteration) > config.minDaysAfterVaccination) // only people who've had their last vaccination more than 90 days ago
						.filter(p -> p.getNumInfections() == 0 || p.daysSinceInfection(p.getNumInfections() - 1, iteration) > minDaysAfterInfection) // only people who've had their last vaccination more than 90 days ago
						.filter(p -> date.isAfter(config.emergencyDate.minusDays(1)) ? boostBa5Emergency.contains(p.getPersonId()) : boostBa5Yes.contains(p.getPersonId()))
						.filter(p -> !p.hadVaccinationType(vaccinationType)) // todo remove in future
						.collect(Collectors.toList());



				// create vaccinations-remaining counter for current day
				int vaccinationsLeft = this.dailyVaccinationsToBeDistributed;
				if (date.isAfter(config.emergencyDate.minusDays(1))) {
					vaccinationsLeft *= 10;
				}

				// list is shuffled to avoid eventual bias
				if (candidates.size() != 0)
					Collections.shuffle(candidates, new Random(EpisimUtils.getSeed(rnd)));

				int n = Math.min(candidates.size(), vaccinationsLeft);
				for (int i = 0; i < n; i++) {
					EpisimPerson person = candidates.get(i);
					vaccinate(person, iteration, vaccinationType);
					vaccinationsLeft--;
				}
			}
		}
	}


	public static class Config {

		/**
		 * Start Dates of vaccination campaigns.
		 */
		private final Map<LocalDate, VaccinationType> startDateToVaccinationCampaign;
		/**
		 * Duration of vaccination campaign.
		 */
		private final int campaignDuration;

		private final VaccinationPool vaccinationPool;

		private final int minDaysAfterVaccination;

		private final int minDaysAfterInfection;

		private final LocalDate emergencyDate;
		private final LocalDate dateToTurnDownMinDaysAfterInfectionTo90;

		public enum VaccinationPool {

			unvaccinated(0),

			vaccinated(1),

			boostered(2);

			private final int vaxCnt;

			VaccinationPool(int vaxCnt) {
				this.vaxCnt = vaxCnt;
			}

		}


		public Config(Map<LocalDate, VaccinationType> startDateToVaccinationCampaign, int campaignDuration, VaccinationPool vaccinationPool, int minDaysAfterInfection, int minDaysAfterVaccination, LocalDate emergencyDate, LocalDate dateToTurnDownMinDaysAfterInfectionTo90) {

			this.startDateToVaccinationCampaign = startDateToVaccinationCampaign;
			this.campaignDuration = campaignDuration;
			this.vaccinationPool = vaccinationPool;
			this.minDaysAfterInfection = minDaysAfterInfection;
			this.minDaysAfterVaccination = minDaysAfterVaccination;
			this.emergencyDate = emergencyDate;
			this.dateToTurnDownMinDaysAfterInfectionTo90 = dateToTurnDownMinDaysAfterInfectionTo90;

		}
	}

}
