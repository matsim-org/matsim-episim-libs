package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Config option specific to testing and measures performed in {@link org.matsim.episim.model.ProgressionModel}.
 */
public class TestingConfigGroup extends ReflectiveConfigGroup {

	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");

	private static final String CAPACITY = "testingCapacity";
	private static final String RATE = "testingRate";
	private static final String RATE_PER_ACTIVITY = "testingRatePerActivity";
	private static final String FALSE_POSITIVE_RATE = "falsePositiveRate";
	private static final String FALSE_NEGATIVE_RATE = "falseNegativeRate";
	private static final String ACTIVITIES = "activities";
	private static final String STRATEGY = "strategy";
	private static final String SELECTION = "selection";

	private static final String GROUPNAME = "episimTesting";

	/**
	 * Amount of tests per day.
	 */
	private final Map<LocalDate, Integer> testingCapacity = new TreeMap<>();

	/**
	 * Probability that a not infected person is reported as positive.
	 */
	private double falsePositiveRate = 0.03;

	/**
	 * Probability that an infected person is not identified.
	 */
	private double falseNegativeRate = 0.1;

	/**
	 * Share of people that are tested (if applicable for a test)
	 */
	private double testingRate = 1.0;

	/**
	 * Separate testing rates for individual activities.
	 */
	private final Map<String, NavigableMap<LocalDate, Double>> ratePerActivity = new HashMap<>();

	/**
	 * Tracing and containment strategy.
	 */
	private Strategy strategy = Strategy.NONE;

	/**
	 * Which persons are selected for testing.
	 */
	private Selection selection = Selection.ALL_PERSONS;

	/**
	 * Activities to test when using {@link Strategy#ACTIVITIES}.
	 */
	private final Set<String> activities = new HashSet<>();

	/**
	 * Default constructor.
	 */
	public TestingConfigGroup() {
		super(GROUPNAME);
	}

	/**
	 * Sets the tracing capacity for the whole simulation period.
	 *
	 * @param capacity number of persons to trace per day.
	 * @see #setTestingCapacity_pers_per_day(int) (Map)
	 */
	public void setTestingCapacity_pers_per_day(int capacity) {
		setTestingCapacity_pers_per_day(Map.of(LocalDate.of(1970, 1, 1), capacity));
	}

	/**
	 * Sets the tracing capacity for individual days. If a day has no entry the previous will be still valid.
	 *
	 * @param capacity map of dates to changes in capacity.
	 */
	public void setTestingCapacity_pers_per_day(Map<LocalDate, Integer> capacity) {
		testingCapacity.clear();
		testingCapacity.putAll(capacity);
	}

	public Map<LocalDate, Integer> getTestingCapacity() {
		return testingCapacity;
	}

	@StringSetter(CAPACITY)
	void setTestingCapacity(String capacity) {

		Map<String, String> map = SPLITTER.split(capacity);
		setTestingCapacity_pers_per_day(map.entrySet().stream().collect(Collectors.toMap(
				e -> LocalDate.parse(e.getKey()), e -> Integer.parseInt(e.getValue())
		)));
	}

	@StringGetter(CAPACITY)
	String getTracingCapacityString() {
		return JOINER.join(testingCapacity);
	}

	@StringGetter(RATE)
	public double getTestingRate() {
		return testingRate;
	}

	@StringSetter(RATE)
	public void setTestingRate(double testingRate) {
		this.testingRate = testingRate;
	}

	/**
	 * Set testing rate for activities that is valid on all days.
	 */
	public void setTestingRatePerActivity(Map<String, Double> rates) {
		this.ratePerActivity.clear();
		for (Map.Entry<String, Double> e : rates.entrySet()) {
			ratePerActivity.put(e.getKey(), new TreeMap<>(Map.of(LocalDate.MIN, e.getValue())));
		}
	}

	/**
	 * Set testing rate for activities individually for certain dates.
	 */
	public void setTestingRatePerActivityAndDate(Map<String, Map<LocalDate, Double>> rates) {
		this.ratePerActivity.clear();
		for (Map.Entry<String, Map<LocalDate, Double>> e : rates.entrySet()) {
			ratePerActivity.put(e.getKey(), new TreeMap<>(e.getValue()));
		}
	}

	@StringSetter(RATE_PER_ACTIVITY)
	void setRatePerActivity(String rates) {

		Map<String, String> rate = Splitter.on("|").withKeyValueSeparator(">").split(rates);
		this.ratePerActivity.clear();

		for (Map.Entry<String, String> v : rate.entrySet()) {
			Map<String, String> map = SPLITTER.split(v.getValue());
			ratePerActivity.put(v.getKey(), new TreeMap<>(map.entrySet().stream().collect(Collectors.toMap(
					e -> LocalDate.parse(e.getKey()), e -> Double.parseDouble(e.getValue())
			))));
		}
	}

	@StringGetter(RATE_PER_ACTIVITY)
	String getRatesPerActivity() {

		Map<String, String> collect =
				ratePerActivity.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> JOINER.join(e.getValue())));

		return Joiner.on("|").withKeyValueSeparator(">").join(collect);
	}

	/**
	 * Return the testing rate for configured activities for a specific date.
	 */
	public Object2DoubleMap<String> getDailyTestingRateForActivities(LocalDate date) {

		Object2DoubleOpenHashMap<String> map = new Object2DoubleOpenHashMap<>();
		for (Map.Entry<String, NavigableMap<LocalDate, Double>> e : ratePerActivity.entrySet()) {
			map.put(e.getKey(), (double) EpisimUtils.findValidEntry(e.getValue(), 0.0, date));
		}

		return map;
	}

	@StringGetter(FALSE_POSITIVE_RATE)
	public double getFalsePositiveRate() {
		return falsePositiveRate;
	}

	@StringSetter(FALSE_POSITIVE_RATE)
	public void setFalsePositiveRate(double falsePositiveRate) {
		this.falsePositiveRate = falsePositiveRate;
	}

	@StringGetter(FALSE_NEGATIVE_RATE)
	public double getFalseNegativeRate() {
		return falseNegativeRate;
	}

	@StringSetter(FALSE_NEGATIVE_RATE)
	public void setFalseNegativeRate(double falseNegativeRate) {
		this.falseNegativeRate = falseNegativeRate;
	}

	public void setActivities(List<String> activities) {
		this.activities.clear();
		this.activities.addAll(activities);
	}

	public Set<String> getActivities() {
		return activities;
	}

	@StringSetter(ACTIVITIES)
	void setActivitiesString(String activitiesString) {
		setActivities(Splitter.on(",").splitToList(activitiesString));
	}

	@StringGetter(ACTIVITIES)
	String getActivitiesString() {
		return Joiner.on(",").join(activities);
	}

	@StringGetter(STRATEGY)
	public Strategy getStrategy() {
		return strategy;
	}

	@StringSetter(STRATEGY)
	public void setStrategy(Strategy strategy) {
		this.strategy = strategy;
	}

	@StringGetter(SELECTION)
	public Selection getSelection() {
		return selection;
	}

	@StringSetter(SELECTION)
	public void setSelection(Selection selection) {
		this.selection = selection;
	}

	public enum Strategy {

		/**
		 * No tracing.
		 */
		NONE,

		/**
		 * Test with at fixed days.
		 */
		FIXED_DAYS,

		/**
		 * Test persons that have certain activity at each day.
		 */
		ACTIVITIES
	}

	public enum Selection {

		/**
		 * All persons will be tested.
		 */
		ALL_PERSONS,

		/**
		 * Only persons with covid like symptoms are tested.
		 */
		//SYMPTOMS_ONLY

	}

}
