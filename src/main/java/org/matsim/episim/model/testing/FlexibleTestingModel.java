package org.matsim.episim.model.testing;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import org.matsim.core.config.Config;
import org.matsim.episim.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Testing model that uses guice injection to implement testing logic.
 */
public class FlexibleTestingModel extends DefaultTestingModel {

	private final TestRate rate;
	private final TestPolicy policy;

	@Inject
	public FlexibleTestingModel(SplittableRandom rnd, Config config, TestingConfigGroup testingConfig, TestRate rate, TestPolicy policy,
	                            VaccinationConfigGroup vaccinationConfig, EpisimConfigGroup episimConfig) {
		super(rnd, config, testingConfig, vaccinationConfig, episimConfig);
		this.rate = rate;
		this.policy = policy;
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


		boolean fullyVaccinated = rate.useFullyVaccinatedTestRate(person, day, date);

		// update is run at end of day, the test needs to be for the next day
		DayOfWeek dow = EpisimUtils.getDayOfWeek(episimConfig, day + 1);

		if (!policy.shouldTest(person, dow, date))
			return;

		for (TestingConfigGroup.TestingParams params : testingConfig.getTestingParams()) {

			TestType type = params.getType();

			if (testingCapacity.get(type) <= 0)
				continue;


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

	@FunctionalInterface
	public interface TestRate {

		/**
		 * Decide whether this person is tested according to the fully vaccinated rate or the normal rate in the config.
		 */
		boolean useFullyVaccinatedTestRate(EpisimPerson person, int day, LocalDate date);

	}

	@FunctionalInterface
	public interface TestPolicy {

		/**
		 * Decide whether this person may perform a test according to the testing rate and activities.
		 *
		 * @param dow day of week
		 */
		boolean shouldTest(EpisimPerson person, DayOfWeek dow, LocalDate date);

	}

}
