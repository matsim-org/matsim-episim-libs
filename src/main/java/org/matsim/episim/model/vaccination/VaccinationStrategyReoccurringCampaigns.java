package org.matsim.episim.model.vaccination;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimReporting;
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

	// LocalDate of beginning of vaccination campaign -> age-group -> number of vaccinations left
	private final Map<LocalDate, Map<EpisimReporting.AgeGroup, Integer>> vaccinationsLeftPerAgePerCampaign;


	@Inject
	public VaccinationStrategyReoccurringCampaigns(SplittableRandom rnd, Config config, Scenario scenario) {
		this.rnd = rnd;
		this.config = config;

		Population population = scenario.getPopulation();


		// number of agents in each age group
		int agentCnt_0_11 = (int) population.getPersons().values().stream().filter(p -> ((int) p.getAttributes().getAttribute("microm:modeled:age") >= 0 && (int) p.getAttributes().getAttribute("microm:modeled:age") < 12)).count();
		int agentCnt_12_17 = (int) population.getPersons().values().stream().filter(p -> ((int) p.getAttributes().getAttribute("microm:modeled:age") >= 12 && (int) p.getAttributes().getAttribute("microm:modeled:age") < 18)).count();
		int agentCnt_18_59 = (int) population.getPersons().values().stream().filter(p -> ((int) p.getAttributes().getAttribute("microm:modeled:age") >= 18 && (int) p.getAttributes().getAttribute("microm:modeled:age") < 60)).count();
		int agentCnt_60_plus = (int) population.getPersons().values().stream().filter(p -> (int) p.getAttributes().getAttribute("microm:modeled:age") >= 60).count();

		//multiply compliance by these numbers -> total number of vaccines to be applied to age group
		int vaxCnt_0_11 = (int) (agentCnt_0_11 * config.complianceByAge.getDouble(EpisimReporting.AgeGroup.age_0_11));
		int vaxCnt_12_17 = (int) (agentCnt_12_17 * config.complianceByAge.getDouble(EpisimReporting.AgeGroup.age_12_17));
		int vaxCnt_18_59 = (int) (agentCnt_18_59 * config.complianceByAge.getDouble(EpisimReporting.AgeGroup.age_18_59));
		int vaxCnt_60_plus = (int) (agentCnt_60_plus * config.complianceByAge.getDouble(EpisimReporting.AgeGroup.age_60_plus));

		// put these vaccination counts in map form
		Object2IntMap<EpisimReporting.AgeGroup> vaccinationsLeftPerAge = new Object2IntOpenHashMap(Map.of(
				EpisimReporting.AgeGroup.age_0_11, vaxCnt_0_11,
				EpisimReporting.AgeGroup.age_12_17, vaxCnt_12_17,
				EpisimReporting.AgeGroup.age_18_59, vaxCnt_18_59,
				EpisimReporting.AgeGroup.age_60_plus, vaxCnt_60_plus
		));

		// create a age-group vaccinations remaining counter for each vaccination campaign (will count down to 0)
		// each map is a copy of map created above
		this.vaccinationsLeftPerAgePerCampaign = new HashMap<>();
		for (LocalDate startDate : config.startDateToVaccinationCampaign.keySet()) {
			this.vaccinationsLeftPerAgePerCampaign.put(startDate, new HashMap<>(vaccinationsLeftPerAge));
		}

		// calculate total number of vaccinations:
		int totalVaccinationsDistributedPerCampaign = vaccinationsLeftPerAge.values().intStream().sum();
		dailyVaccinationsToBeDistributed = totalVaccinationsDistributedPerCampaign / config.campaignDuration;

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
				List<EpisimPerson> candidates = persons.values().stream()
					.filter(EpisimPerson::isVaccinable) // todo: what determines who is vaccinable?
					.filter(p -> p.getDiseaseStatus() == EpisimPerson.DiseaseStatus.susceptible)
					.filter(p -> p.getNumVaccinations() >= config.vaccinationPool.vaxCnt) // only boostered people are reboostered
					.filter(p -> p.daysSinceVaccination(p.getNumVaccinations() - 1, iteration) > 90) // only people who've had their last vaccination more than 90 days ago
					.collect(Collectors.toList());


				// create vaccinations-remaining counter for current day
				int vaccinationsLeft = this.dailyVaccinationsToBeDistributed;

				// group candidates into age groups
				Map<EpisimReporting.AgeGroup, List<EpisimPerson>> candidatesPerAgeGroup = new HashMap<>();
				for (EpisimReporting.AgeGroup ageGroup : EpisimReporting.AgeGroup.values()) {
					candidatesPerAgeGroup.put(ageGroup, new ArrayList<>());
				}
				for (EpisimPerson person : candidates) {
					for (EpisimReporting.AgeGroup ageGroup : EpisimReporting.AgeGroup.values()) {
						if (person.getAge() >= ageGroup.lowerBoundAge) {
							candidatesPerAgeGroup.get(ageGroup).add(person);
							break;
						}
					}
				}

				// Apply vaccinations, oldest age-group first. Stop vaccinations for day if:
				//		a) no vaccines left for the day
				//		b) no age-group has any more candidates
				//		c) no age-group has any more vaccines left (for entire campaign)

				Iterator<EpisimReporting.AgeGroup> ageGroupIterator = Arrays.stream(EpisimReporting.AgeGroup.values()).iterator();
//			int ageIndex = AgeGroup.values().length - 1;
				while (ageGroupIterator.hasNext() && vaccinationsLeft > 0) {
					EpisimReporting.AgeGroup ageGroup = ageGroupIterator.next();

					int vaccinationsLeftForAgeGroup = vaccinationsLeftPerAgePerCampaign.get(vaccinationCampaignStartDate).get(ageGroup);

					// list is shuffled to avoid eventual bias
					List<EpisimPerson> candidatesForAge = candidatesPerAgeGroup.get(ageGroup);
					Collections.shuffle(candidatesForAge, new Random(EpisimUtils.getSeed(rnd)));

//
//					int vaccinesForDayAndAgeGroup = Math.min(candidatesForAge.size(), vaccinationsLeft);
					Iterator<EpisimPerson> candidateIterator = candidatesForAge.stream().iterator();
					EpisimPerson person;
					while(candidateIterator.hasNext() && vaccinationsLeft > 0 && vaccinationsLeftForAgeGroup > 0){
						person = candidateIterator.next();

						vaccinate(person, iteration, vaccinationType);
						vaccinationsLeft--;
						vaccinationsLeftForAgeGroup--;
					}
					vaccinationsLeftPerAgePerCampaign.get(vaccinationCampaignStartDate).put(ageGroup, vaccinationsLeftForAgeGroup);
				}

				if (vaccinationsLeft > 0) {
					System.out.println(vaccinationsLeft + " vaccinations were left over at end of day ");
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

		private final Object2DoubleMap<EpisimReporting.AgeGroup> complianceByAge;

		private final VaccinationPool vaccinationPool;

		public enum VaccinationPool {

			vaccinated(1),

			boostered(2);

			private final int vaxCnt;

			VaccinationPool(int vaxCnt) {
				this.vaxCnt = vaxCnt;
			}

		}


		public Config(Map<LocalDate, VaccinationType> startDateToVaccinationCampaign, int campaignDuration, Object2DoubleMap<EpisimReporting.AgeGroup> complianceByAge, VaccinationPool vaccinationPool) {
			this.startDateToVaccinationCampaign = startDateToVaccinationCampaign;
			this.campaignDuration = campaignDuration;
			this.complianceByAge = complianceByAge;
			this.vaccinationPool = vaccinationPool;
		}
	}

}
