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
	private static final String QUARANTINE_HOUSEHOLD = "quarantineHousehold";
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
	 * Amount of days after the person showing symtomps.
	 */
	private int tracingDelay = 0;

	/**
	 * Probability of successfully tracing a person.
	 */
	private double tracingProbability = 1.0;

	/**
	 * Members of the same household will be put into quarantine, as well if one person of it had contact with infected person.
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
		this.putTraceablePersonsInQuarantineAfterDay = putTraceablePersonsInQuarantineAfterDay;
	}

	@StringGetter(TRACING_DAYS_DISTANCE)
	public int getTracingDayDistance() {
		return tracingDayDistance;
	}

	@StringSetter(TRACING_DAYS_DISTANCE)
	public void setTracingDayDistance(int tracingDayDistance) {
		this.tracingDayDistance = tracingDayDistance;
	}

	@StringGetter(TRACING_DELAY)
	public int getTracingDelay() {
		return tracingDelay;
	}

	@StringSetter(TRACING_DELAY)
	public void setTracingDelay(int tracingDelay) {
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

	@StringSetter(QUARANTINE_HOUSEHOLD)
	public void setQuarantineHouseholdMembers(boolean quarantineHouseholdMembers) {
		this.quarantineHouseholdMembers = quarantineHouseholdMembers;
	}

	@StringGetter(QUARANTINE_HOUSEHOLD)
	public boolean getQuarantineHousehold() {
		return quarantineHouseholdMembers;
	}
}
