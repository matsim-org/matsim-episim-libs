package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.testing.TestType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Config option specific to testing and measures performed in {@link org.matsim.episim.model.ProgressionModel}.
 */
public class TestingConfigGroup extends ReflectiveConfigGroup {

	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");

	private static final String TYPE = "type";
	private static final String CAPACITY = "testingCapacity";
	private static final String RATE = "testingRate";
	private static final String DAYS = "days";
	private static final String RATE_PER_ACTIVITY = "testingRatePerActivity";
	private static final String FALSE_POSITIVE_RATE = "falsePositiveRate";
	private static final String FALSE_NEGATIVE_RATE = "falseNegativeRate";
	private static final String HOUSEHOLD_COMPLIANCE = "householdCompliance";
	private static final String ACTIVITIES = "activities";
	private static final String STRATEGY = "strategy";
	private static final String TEST_ALL_PERSONS_AFTER = "testAllPersonsAfter";
	private static final String ACTIVITY_CAPACITIES = "activityCapacities";

	private static final String GROUPNAME = "episimTesting";

	/**
	 * Percentage of (fixed) households that are tested.
	 */
	private double householdCompliance = 1.0;

	/**
	 * Testing and containment strategy.
	 */
	private Strategy strategy = Strategy.NONE;

	/**
	 * Test all persons after this date
	 */
	private LocalDate testAllPersonsAfter = null;

	/**
	 * Activities to test when using {@link Strategy#ACTIVITIES}.
	 */
	private final Set<String> activities = new HashSet<>();

	/**
	 * Path to csv file for individual activity capacities.
	 */
	private String activityCapacities;

	/**
	 * Holds all testing params.
	 */
	private final Map<TestType, TestingParams> types = new EnumMap<>(TestType.class);

	/**
	 * Holds testing specific options
	 */
	public static final class TestingParams extends ReflectiveConfigGroup {

		static final String SET_TYPE = "testingParams";

		/**
		 * Type of test
		 */
		private TestType type;

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
		 * Days on which a person is tested.
		 */
		private final Set<DayOfWeek> testDays = new HashSet<>(Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY));


		public TestingParams() {
			super(SET_TYPE);
		}

		@StringSetter(TYPE)
		public void setType(TestType type) {
			this.type = type;
		}

		@StringGetter(TYPE)
		public TestType getType() {
			return type;
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

		public Set<DayOfWeek> getTestDays() {
			return testDays;
		}

		public void setTestDays(Collection<DayOfWeek> days) {
			this.testDays.clear();
			this.testDays.addAll(days);
		}


		@StringGetter(DAYS)
		String getTestDaysString() {
			return Joiner.on(",").join(testDays);
		}

		@StringSetter(DAYS)
		void setTestDays(String days) {
			setTestDays(Arrays.stream(days.split(",")).map(DayOfWeek::valueOf)
					.collect(Collectors.toSet()));
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

	}


	/**
	 * Default constructor.
	 */
	public TestingConfigGroup() {
		super(GROUPNAME);

		getOrAddParams(TestType.RAPID_TEST);
	}

	/**
	 * Get config parameter for a specific strain.
	 */
	public TestingParams getParams(TestType type) {
		if (!types.containsKey(type))
			throw new IllegalStateException(("Testing type " + type + " is not configured."));

		return types.get(type);
	}

	/**
	 * Get an existing or add new parameter set.
	 */
	public TestingParams getOrAddParams(TestType type) {
		if (!types.containsKey(type)) {
			TestingParams p = new TestingParams();
			p.type = type;
			addParameterSet(p);
			return p;
		}

		return types.get(type);
	}

	/**
	 * Return all testing params.
	 */
	public Collection<TestingParams> getTestingParams() {
		return types.values();
	}

	@Override
	public ConfigGroup createParameterSet(String type) {
		if (TestingParams.SET_TYPE.equals(type)) {
			return new TestingParams();
		}
		throw new IllegalArgumentException("Unknown type" + type);
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		if (TestingParams.SET_TYPE.equals(set.getName())) {
			TestingParams p = (TestingParams) set;
			types.put(p.type, p);
			super.addParameterSet(set);

		} else
			throw new IllegalStateException("Unknown set type " + set.getName());
	}

	@StringSetter(HOUSEHOLD_COMPLIANCE)
	public void setHouseholdCompliance(double householdCompliance) {
		this.householdCompliance = householdCompliance;
	}

	@StringGetter(HOUSEHOLD_COMPLIANCE)
	public double getHouseholdCompliance() {
		return householdCompliance;
	}

	@StringGetter(STRATEGY)
	public Strategy getStrategy() {
		return strategy;
	}

	@StringSetter(STRATEGY)
	public void setStrategy(Strategy strategy) {
		this.strategy = strategy;
	}

	@StringSetter(TEST_ALL_PERSONS_AFTER)
	void setTestAllPersonsAfter(String testAllPersonsAfter) {
		this.testAllPersonsAfter = LocalDate.parse(testAllPersonsAfter);
	}

	public void setTestAllPersonsAfter(LocalDate testAllPersonsAfter) {
		this.testAllPersonsAfter = testAllPersonsAfter;
	}

	@StringGetter(TEST_ALL_PERSONS_AFTER)
	public LocalDate getTestAllPersonsAfter() {
		return testAllPersonsAfter;
	}

	@StringGetter(ACTIVITY_CAPACITIES)
	public String getActivityCapacities() {
		return activityCapacities;
	}

	@StringSetter(ACTIVITY_CAPACITIES)
	public void setActivityCapacities(String activityCapacities) {
		this.activityCapacities = activityCapacities;
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

	/**
	 * Use configuration for individual test types.
	 */
	@Deprecated
	public void setTestingRatePerActivityAndDate(Map<String, Map<LocalDate, Double>> rates) {
		getOrAddParams(TestType.RAPID_TEST).setTestingRatePerActivityAndDate(rates);
	}

	@Deprecated
	public void setFalsePositiveRate(double falsePositiveRate) {
		getParams(TestType.RAPID_TEST).setFalsePositiveRate(falsePositiveRate);
	}

	@Deprecated
	public void setFalseNegativeRate(double falseNegativeRate) {
		getParams(TestType.RAPID_TEST).setFalseNegativeRate(falseNegativeRate);
	}

	@Deprecated
	public void setTestingCapacity_pers_per_day(Map<LocalDate, Integer> capacity) {
		getParams(TestType.RAPID_TEST).setTestingCapacity_pers_per_day(capacity);
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
		ACTIVITIES,

		/**
		 * Test persons at fixed days, if they have certain activity.
		 */
		FIXED_ACTIVITIES,
	}
}
