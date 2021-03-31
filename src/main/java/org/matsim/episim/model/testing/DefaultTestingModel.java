package org.matsim.episim.model.testing;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.episim.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Testing model that provides some default testing capabilities and helper functions.
 */
public class DefaultTestingModel implements TestingModel {

	protected final SplittableRandom rnd;
	protected final EpisimConfigGroup episimConfig;
	protected final TestingConfigGroup testingConfig;
	protected final Config config;

	/**
	 * Testing capacity left for the day.
	 */
	private int testingCapacity = Integer.MAX_VALUE;

	/**
	 * Testing rates for configured activities for current day.
	 */
	private Object2DoubleMap<String> testingRateForActivities;

	/**
	 * Ids of households that are not compliant.
	 */
	private final Set<String> nonCompliantHouseholds = new HashSet<>();

	@Inject
	DefaultTestingModel(SplittableRandom rnd, Config config, TestingConfigGroup testingConfig, EpisimConfigGroup episimConfig) {
		this.rnd = rnd;
		this.config = config;
		this.testingConfig = testingConfig;
		this.episimConfig = episimConfig;
	}

	@Override
	public void setIteration(int day) {

		LocalDate date = episimConfig.getStartDate().plusDays(day - 1);

		testingCapacity = EpisimUtils.findValidEntry(testingConfig.getTestingCapacity(), 0, date);
		if (testingCapacity != Integer.MAX_VALUE)
			testingCapacity *= episimConfig.getSampleSize();

		testingRateForActivities = testingConfig.getDailyTestingRateForActivities(date);
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

		if (testingConfig.getStrategy() == TestingConfigGroup.Strategy.NONE)
			return;

		if (testingCapacity <= 0)
			return;

		// person with positive test is not tested twice
		// test status will be set when released from quarantine
		if (person.getTestStatus() == EpisimPerson.TestStatus.positive)
			return;

		// update is run at end of day, the test needs to be for the next day
		DayOfWeek dow = EpisimUtils.getDayOfWeek(episimConfig, day + 1);

		if (testingConfig.getStrategy() == TestingConfigGroup.Strategy.FIXED_DAYS && testingConfig.getTestDays().contains(dow)) {
				testAndQuarantine(person, day, testingConfig.getTestingRate());
		} else if (testingConfig.getStrategy() == TestingConfigGroup.Strategy.ACTIVITIES) {

			double rate = person.matchActivities(dow, testingConfig.getActivities(),
					(act, v) -> Math.max(v, testingRateForActivities.getOrDefault(act, testingConfig.getTestingRate())), 0d);

			testAndQuarantine(person, day, rate);
		} else if (testingConfig.getStrategy() == TestingConfigGroup.Strategy.FIXED_ACTIVITIES && testingConfig.getTestDays().contains(dow)) {

			double rate = person.matchActivities(dow, testingConfig.getActivities(),
					(act, v) -> Math.max(v, testingRateForActivities.getOrDefault(act, testingConfig.getTestingRate())), 0d);

			testAndQuarantine(person, day, rate);
		}
	}

	/**
	 * Perform testing and quarantine person.
	 */
	private void testAndQuarantine(EpisimPerson person, int day, double testingRate) {

		if (testingRate == 0)
			return;

		if (nonCompliantHouseholds.contains(getHomeId(person)))
			return;

		if (testingRate != 1d && rnd.nextDouble() >= testingRate)
			return;

		EpisimPerson.DiseaseStatus status = person.getDiseaseStatus();
		if (status == EpisimPerson.DiseaseStatus.infectedButNotContagious || status == EpisimPerson.DiseaseStatus.susceptible || status == EpisimPerson.DiseaseStatus.recovered) {
			EpisimPerson.TestStatus testStatus = rnd.nextDouble() >= testingConfig.getFalsePositiveRate() ? EpisimPerson.TestStatus.negative : EpisimPerson.TestStatus.positive;
			person.setTestStatus(testStatus, day);

		} else if (status == EpisimPerson.DiseaseStatus.contagious ||
				status == EpisimPerson.DiseaseStatus.showingSymptoms) {

			EpisimPerson.TestStatus testStatus = rnd.nextDouble() >= testingConfig.getFalseNegativeRate() ? EpisimPerson.TestStatus.positive : EpisimPerson.TestStatus.negative;
			person.setTestStatus(testStatus, day);
		}


		if (person.getTestStatus() == EpisimPerson.TestStatus.positive) {
			quarantinePerson(person, day);
		}

		testingCapacity--;
	}

	private void quarantinePerson(EpisimPerson p, int day) {

		if (p.getQuarantineStatus() == EpisimPerson.QuarantineStatus.no && p.getDiseaseStatus() != EpisimPerson.DiseaseStatus.recovered) {
			p.setQuarantineStatus(EpisimPerson.QuarantineStatus.atHome, day);
		}
	}

}
