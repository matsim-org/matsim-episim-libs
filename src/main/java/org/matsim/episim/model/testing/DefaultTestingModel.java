package org.matsim.episim.model.testing;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import org.matsim.episim.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.SplittableRandom;

/**
 * Testing model that provides some default testing capabilities and helper functions.
 */
public class DefaultTestingModel implements TestingModel {

	protected final SplittableRandom rnd;
	protected final EpisimConfigGroup episimConfig;
	protected final TestingConfigGroup testingConfig;

	/**
	 * Testing capacity left for the day.
	 */
	private int testingCapacity = Integer.MAX_VALUE;

	/**
	 * Testing rates for configured activities for current day.
	 */
	private Object2DoubleMap<String> testingRateForActivities;

	@Inject
	DefaultTestingModel(SplittableRandom rnd, TestingConfigGroup testingConfig, EpisimConfigGroup episimConfig) {
		this.rnd = rnd;
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

		if (testingConfig.getStrategy() == TestingConfigGroup.Strategy.FIXED_DAYS) {
			if (dow == DayOfWeek.MONDAY || dow == DayOfWeek.THURSDAY) {
				testAndQuarantine(person, day, testingConfig.getTestingRate());
			}
		} else if (testingConfig.getStrategy() == TestingConfigGroup.Strategy.ACTIVITIES) {

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
			if (day >= 19) // -6193675208448730525 -- EpisimPerson{personId=489728001}
				System.out.println("bk");

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
