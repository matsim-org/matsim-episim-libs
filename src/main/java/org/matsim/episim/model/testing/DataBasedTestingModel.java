package org.matsim.episim.model.testing;

import com.google.inject.Inject;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.core.config.Config;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TestingConfigGroup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.NavigableMap;
import java.util.SplittableRandom;
import java.util.TreeMap;

/**
 * This class uses testing capacities from csv files to tests persons regularly throughout the week.
 */
public final class DataBasedTestingModel extends DefaultTestingModel {

	/**
	 * The testing capacities for each context.
	 */
	protected final NavigableMap<LocalDate, Object2IntMap<String>> capacities;

	/**
	 * Capacities for the current day.
	 */
	private Object2IntMap<String> forDay;

	@Inject
	DataBasedTestingModel(SplittableRandom rnd, Config config, TestingConfigGroup testingConfig, EpisimConfigGroup episimConfig) {
		super(rnd, config, testingConfig, null, episimConfig);

		capacities = readActivities();
	}

	/**
	 * Read capacities fro each day from file.
	 */
	private NavigableMap<LocalDate, Object2IntMap<String>> readActivities() {

		try (CSVParser parser = new CSVParser(Files.newBufferedReader(Path.of(testingConfig.getActivityCapacities())),
				CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

			NavigableMap<LocalDate, Object2IntMap<String>> result = new TreeMap<>();

			for (CSVRecord record : parser.getRecords()) {

				LocalDate date = LocalDate.parse(record.get(0));

				Object2IntOpenHashMap<String> forDay = new Object2IntOpenHashMap<>();

				for (int i = 1; i < record.size(); i++) {
					forDay.put(parser.getHeaderNames().get(i), Integer.parseInt(record.get(i)));
				}

				result.put(date, forDay);
			}

			return result;

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void setIteration(int day) {
		super.setIteration(day);

		testingCapacity.put(TestType.RAPID_TEST, Integer.MAX_VALUE);

		LocalDate date = episimConfig.getStartDate().plusDays(day - 1);

		Object2IntMap<String> local = EpisimUtils.findValidEntry(capacities, null, date);

		// create copy that will be modified
		if (local != null) {
			forDay = new Object2IntOpenHashMap<>(local);

			// scale weekly values to daily capacities
			for (String k : forDay.keySet()) {
				forDay.put(k, (int) (episimConfig.getSampleSize() * forDay.getInt(k) / 7));
			}
		}
	}

	@Override
	public void performTesting(EpisimPerson person, int day) {

		// no capacities until first valid value
		if (forDay == null)
			return;

		TestingConfigGroup.TestingParams params = testingConfig.getParams(TestType.RAPID_TEST);

		// check if testing is disabled
		if (params.getTestingRate() == 0d || testingConfig.getStrategy() == TestingConfigGroup.Strategy.NONE)
			return;

		// person with recent test is not tested again
		if (person.daysSinceTest(day) <= 2)
			return;

		// all capacity used up
		if (forDay.values().stream().allMatch(i -> i <= 0))
			return;

		// update is run at end of day, the test needs to be for the next day
		DayOfWeek dow = EpisimUtils.getDayOfWeek(episimConfig, day + 1);

		String act = person.matchActivities(dow, testingConfig.getActivities(), this::chooseActivity, null);

		if (act != null) {

			// testing rate can be reduced to introduce more randomness
			// otherwise always the same persons are tested
			boolean tested = testAndQuarantine(person, day, params, params.getTestingRate());

			if (tested)
				forDay.mergeInt(act, -1, Integer::sum);

		}

	}

	/**
	 * Choose from which pool test capacity will be drawn.
	 *
	 * @param activity performed activity
	 * @param chosen   current chosen activity
	 * @return chosen capacity pool according to headers in the csv.
	 */
	public String chooseActivity(String activity, String chosen) {

		if (chosen != null)
			return chosen;

		for (Object2IntMap.Entry<String> entry : forDay.object2IntEntrySet()) {

			if (activity.startsWith(entry.getKey()) && entry.getIntValue() > 0)
				return entry.getKey();
		}

		return null;
	}

}
