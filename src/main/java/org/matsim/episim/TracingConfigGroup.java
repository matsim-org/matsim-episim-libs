package org.matsim.episim;

import org.matsim.core.config.ReflectiveConfigGroup;

/**
 * Config option specific to contact tracing and measures performed in {@link org.matsim.episim.model.ProgressionModel}.
 */
public class TracingConfigGroup extends ReflectiveConfigGroup {

	private static final String PUT_TRACEABLE_PERSONS_IN_QUARANTINE = "pubTraceablePersonsInQuarantineAfterDay";
	private static final String TRACING_DAYS_DISTANCE = "tracingDaysDistance";
	private static final String TRACING_PROBABILITY = "tracingProbability";
	private static final String TRACING_DELAY = "tracingDelay";
	private static final String MIN_DURATION = "minDuration";
	private static final String QUARANTINE_HOUSEHOLD = "quarantineHousehold";
	private static final String EQUIPMENT_RATE = "equipmentRate";
	private static final String CAPACITY = "tracingCapacity";
	private static final String GROUPNAME = "episimTracing";

	/**
	 * Day after which tracing starts and puts persons into quarantine.
	 */
	private int putTraceablePersonsInQuarantineAfterDay = Integer.MAX_VALUE;
	/**
	 * How many days the tracing works back.
	 */
	private int tracingDayDistance = 4;

	/**
	 * Amount of days after the person showing symptoms.
	 */
	private int tracingDelay = 0;

	/**
	 * Amount of persons traceable der day.
	 */
	private int tracingCapacity = Integer.MAX_VALUE;

	/**
	 * Probability of successfully tracing a person.
	 */
	private double tracingProbability = 1.0;

	/**
	 * Probability that a person is equipped with a tracing device.
	 */
	private double equipmentRate = 1.0;

	/**
	 * Minimum duration in seconds for a contact to be relevant for tracing.
	 */
	private double minDuration = 0.0;

	/**
	 * Members of the same household will be put always into quarantine.
	 */
	private boolean quarantineHouseholdMembers = false;

	/**
	 * Default constructor.
	 */
	public TracingConfigGroup() {
		super(GROUPNAME);
	}

	@StringGetter(PUT_TRACEABLE_PERSONS_IN_QUARANTINE)
	public int getPutTraceablePersonsInQuarantineAfterDay() {
		return putTraceablePersonsInQuarantineAfterDay;
	}

	@StringSetter(PUT_TRACEABLE_PERSONS_IN_QUARANTINE)
	public void setPutTraceablePersonsInQuarantineAfterDay(int putTraceablePersonsInQuarantineAfterDay) {
		// yyyy change argument to date.  kai, jun'20
		this.putTraceablePersonsInQuarantineAfterDay = putTraceablePersonsInQuarantineAfterDay;
	}

	@StringGetter(TRACING_DAYS_DISTANCE)
	public int getTracingDayDistance() {
		return tracingDayDistance;
	}

	@StringSetter(TRACING_DAYS_DISTANCE)
	public void setTracingPeriod_days( int tracingDayDistance ) {
		this.tracingDayDistance = tracingDayDistance;
	}

	@StringGetter(TRACING_DELAY)
	public int getTracingDelay() {
		return tracingDelay;
	}

	@StringSetter(TRACING_DELAY)
	public void setTracingDelay_days( int tracingDelay ) {
		this.tracingDelay = tracingDelay;
	}

	@StringGetter(TRACING_PROBABILITY)
	public double getTracingProbability() {
		return tracingProbability;
	}

	@StringSetter(TRACING_PROBABILITY)
	public void setTracingProbability(double tracingProbability) {
		this.tracingProbability = tracingProbability;
	}

	@StringSetter(CAPACITY)
	public void setTracingCapacity_pers_per_day( int tracingCapacity ) {
		this.tracingCapacity = tracingCapacity;
	}

	@StringGetter(CAPACITY)
	public int getTracingCapacity() {
		return tracingCapacity;
	}

	@StringGetter(EQUIPMENT_RATE)
	public double getEquipmentRate() {
		return equipmentRate;
	}

	@StringSetter(EQUIPMENT_RATE)
	public void setEquipmentRate(double equipmentRate) {
		this.equipmentRate = equipmentRate;
	}

	@StringGetter(MIN_DURATION)
	public double getMinDuration() {
		return minDuration;
	}

	@StringSetter(MIN_DURATION)
	public void setMinContactDuration_sec( double minDuration ) {
		this.minDuration = minDuration;
	}

	@StringSetter(QUARANTINE_HOUSEHOLD)
	public void setQuarantineHouseholdMembers(boolean quarantineHouseholdMembers) {
		this.quarantineHouseholdMembers = quarantineHouseholdMembers;
	}

	@StringGetter(QUARANTINE_HOUSEHOLD)
	public boolean getQuarantineHousehold() {
		return quarantineHouseholdMembers;
	}
}
