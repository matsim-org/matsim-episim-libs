package org.matsim.episim.model.testing;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.episim.*;
import org.matsim.episim.model.VirusStrain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Testing model that provides some default testing capabilities and helper functions.
 */
public class DefaultTestingModel implements TestingModel {

	protected final SplittableRandom rnd;
	protected final VaccinationConfigGroup vaccinationConfig;
	protected final EpisimConfigGroup episimConfig;
	protected final TestingConfigGroup testingConfig;
	protected final Config config;

	/**
	 * Testing capacity left for the day.
	 */
	protected Map<TestType, Integer> testingCapacity = new EnumMap<>(TestType.class);

	/**
	 * Testing rates for configured activities for current day.
	 */
	protected final Map<TestType, Object2DoubleMap<String>> testingRateForActivities = new EnumMap<>(TestType.class);

	protected final Map<TestType, Object2DoubleMap<String>> testingRateForActivitiesVaccinated = new EnumMap<>(TestType.class);

	/**
	 * Current date
	 */
	protected LocalDate date;

	/**
	 * Ids of households that are not compliant.
	 */
	private final Set<String> nonCompliantHouseholds = new HashSet<>();

	/**
	 * Whether to test all persons on this day.
	 */
	private boolean testAllPersons;

	/**
	 * Don't test person with booster.
	 */
	private boolean withOutBooster;

	@Inject
	DefaultTestingModel(SplittableRandom rnd, Config config, TestingConfigGroup testingConfig, VaccinationConfigGroup vaccinationConfig, EpisimConfigGroup episimConfig) {
		this.rnd = rnd;
		this.config = config;
		this.testingConfig = testingConfig;
		this.vaccinationConfig = vaccinationConfig;
		this.episimConfig = episimConfig;
	}

	@Override
	public void setIteration(int day) {

		date = episimConfig.getStartDate().plusDays(day - 1);

		testAllPersons = testingConfig.getTestAllPersonsAfter() != null && date.isAfter(testingConfig.getTestAllPersonsAfter());
		withOutBooster = testingConfig.getStopTestBoosterAfter() != null && date.isAfter(testingConfig.getStopTestBoosterAfter());

		for (TestingConfigGroup.TestingParams params : testingConfig.getTestingParams()) {

			int testingCapacity = EpisimUtils.findValidEntry(params.getTestingCapacity(), 0, date);
			if (testingCapacity != Integer.MAX_VALUE)
				testingCapacity *= episimConfig.getSampleSize();

			this.testingCapacity.put(params.getType(), testingCapacity);
			this.testingRateForActivities.put(params.getType(), params.getDailyTestingRateForActivities(date));
			this.testingRateForActivitiesVaccinated.put(params.getType(), params.getDailyTestingRateForActivitiesVaccinated(date));
		}
	}

	@Override
	public void beforeStateUpdates(Map<Id<Person>, EpisimPerson> personMap, int iteration, EpisimReporting.InfectionReport report) {

		if (nonCompliantHouseholds.isEmpty() && testingConfig.getHouseholdCompliance() < 1.0)
			initCompliance(personMap);

	}

	private void initCompliance(Map<Id<Person>, EpisimPerson> personMap) {

		// TODO: this class may needs to be added to the snapshot
		SplittableRandom rnd = new SplittableRandom(config.global().getRandomSeed());

		// don't draw one household multiple times
		Set<String> checked = new HashSet<>();

		for (EpisimPerson p : personMap.values()) {
			String home = getHomeId(p);

			if (!checked.contains(home)) {
				if (rnd.nextDouble() > testingConfig.getHouseholdCompliance())
					nonCompliantHouseholds.add(home);

				checked.add(home);
			}
		}
	}

	private String getHomeId(EpisimPerson person) {
		String home = (String) person.getAttributes().getAttribute("homeId");
		// fallback to person id if there is no home
		return home != null ? home : person.getPersonId().toString();
	}

	/**
	 * Perform the testing procedure.
	 */
	public void performTesting(EpisimPerson person, int day) {

		// person with positive test is not tested twice
		// test status will be set when released from quarantine
		if (person.getTestStatus() == EpisimPerson.TestStatus.positive)
			return;

		if (person.getQuarantineStatus() == EpisimPerson.QuarantineStatus.testing) {
			testAndQuarantine(person, day, testingConfig.getParams(TestType.RAPID_TEST), 1.0);
			return;
		}

		if (testingConfig.getStrategy() == TestingConfigGroup.Strategy.NONE)
			return;

		// vaccinated and recovered persons are not tested
		boolean fullyVaccinated = vaccinationConfig.hasValidVaccination(person, day, date);

		if (!testAllPersons && (vaccinationConfig.hasGreenPass(person, day, date)))
			return;

		if (withOutBooster && person.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.yes)
			return;

		for (TestingConfigGroup.TestingParams params : testingConfig.getTestingParams()) {

			TestType type = params.getType();

			if (testingCapacity.get(type) <= 0)
				continue;

			// update is run at end of day, the test needs to be for the next day
			DayOfWeek dow = EpisimUtils.getDayOfWeek(episimConfig, day + 1);

			// Choose testing rate depending on vaccination status
			Object2DoubleMap<String> useRate = fullyVaccinated ? testingRateForActivitiesVaccinated.get(type) : testingRateForActivities.get(type);

			if (testingConfig.getStrategy() == TestingConfigGroup.Strategy.FIXED_DAYS && params.getTestDays().contains(dow)) {
				testAndQuarantine(person, day, params, params.getTestingRate());
			} else if (testingConfig.getStrategy() == TestingConfigGroup.Strategy.ACTIVITIES) {

				double rate = person.matchActivities(dow, testingConfig.getActivities(),
						(act, v) -> Math.max(v, useRate.getOrDefault(act, params.getTestingRate())), 0d);

				testAndQuarantine(person, day, params, rate);
			} else if (testingConfig.getStrategy() == TestingConfigGroup.Strategy.FIXED_ACTIVITIES && params.getTestDays().contains(dow)) {

				double rate = person.matchActivities(dow, testingConfig.getActivities(),
						(act, v) -> Math.max(v, useRate.getOrDefault(act, params.getTestingRate())), 0d);

				testAndQuarantine(person, day, params, rate);
			}

		}

	}

	/**
	 * Perform testing and quarantine person.
	 *
	 * @return true if the person was tested (test result does not matter)
	 */
	protected boolean testAndQuarantine(EpisimPerson person, int day, TestingConfigGroup.TestingParams params, double testingRate) {

		if (testingRate == 0)
			return false;

		if (nonCompliantHouseholds.contains(getHomeId(person)))
			return false;

		if (testingRate != 1d && rnd.nextDouble() >= testingRate)
			return false;

		if (params.getType().shouldDetectNegative(person, day)) {
			EpisimPerson.TestStatus testStatus = rnd.nextDouble() >= params.getFalsePositiveRate() ? EpisimPerson.TestStatus.negative : EpisimPerson.TestStatus.positive;
			person.setTestStatus(testStatus, day);

		} else if (params.getType().canDetectPositive(person, day)) {

			double rate = params.getFalseNegativeRate();

			// TODO: configurable
			if (params.getType().equals(TestType.RAPID_TEST) && (person.getVirusStrain() == VirusStrain.OMICRON_BA1 ||
					person.getVirusStrain() == VirusStrain.OMICRON_BA2 ||
					person.getVirusStrain() == VirusStrain.OMICRON_BA5 ||
					person.getVirusStrain() == VirusStrain.STRAIN_A ||
					person.getVirusStrain() == VirusStrain.STRAIN_B ||
					person.getVirusStrain().toString().startsWith("A_") ||
					person.getVirusStrain().toString().startsWith("B_"))) {
				rate = 0.5;
			}

			EpisimPerson.TestStatus testStatus = rnd.nextDouble() >= rate ? EpisimPerson.TestStatus.positive : EpisimPerson.TestStatus.negative;
			person.setTestStatus(testStatus, day);
		}


		if (person.getTestStatus() == EpisimPerson.TestStatus.positive) {
			quarantinePerson(person, day);
		}

		testingCapacity.merge(params.getType(), -1, Integer::sum);
		return true;
	}

	private void quarantinePerson(EpisimPerson p, int day) {

		// recovered state will be reset quickly
		if (p.getQuarantineStatus() != EpisimPerson.QuarantineStatus.full && p.getDiseaseStatus() != EpisimPerson.DiseaseStatus.recovered) {
			p.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);
		}
	}

}
