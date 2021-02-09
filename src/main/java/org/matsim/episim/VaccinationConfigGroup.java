package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.matsim.core.config.ReflectiveConfigGroup;

import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Config option specific to vaccination and measures performed in {@link org.matsim.episim.model.VaccinationModel}.
 */
public class VaccinationConfigGroup extends ReflectiveConfigGroup {

	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");

	private static final String DAYS_BEFORE_FULL_EFFECT = "daysBeforeFullEffect";
	private static final String EFFECTIVENESS = "effectiveness";
	private static final String CAPACITY = "vaccinationCapacity";

	private static final String GROUPNAME = "episimVaccination";

	/**
	 * Number of days until vaccination goes into full effect.
	 */
	private int daysBeforeFullEffect = 21;

	/**
	 * Effectiveness, i.e. how much susceptibility is reduced.
	 */
	private double effectiveness = 0.96;

	/**
	 * Amount of vaccinations available per day.
	 */
	private final NavigableMap<LocalDate, Integer> vaccinationCapacity = new TreeMap<>();

	/**
	 * Default constructor.
	 */
	public VaccinationConfigGroup() {
		super(GROUPNAME);
	}

	@StringGetter(DAYS_BEFORE_FULL_EFFECT)
	public int getDaysBeforeFullEffect() {
		return daysBeforeFullEffect;
	}

	@StringSetter(DAYS_BEFORE_FULL_EFFECT)
	public void setDaysBeforeFullEffect(int daysBeforeFullEffect) {
		this.daysBeforeFullEffect = daysBeforeFullEffect;
	}

	@StringGetter(EFFECTIVENESS)
	public double getEffectiveness() {
		return effectiveness;
	}

	@StringSetter(EFFECTIVENESS)
	public void setEffectiveness(double effectiveness) {
		this.effectiveness = effectiveness;
	}

	/**
	 * Sets the vaccination capacity for individual days. If a day has no entry the previous will be still valid.
	 * If empty, default is 0.
	 *
	 * @param capacity map of dates to changes in capacity.
	 */
	public void setVaccinationCapacity_pers_per_day(Map<LocalDate, Integer> capacity) {
		vaccinationCapacity.clear();
		vaccinationCapacity.putAll(capacity);
	}

	public NavigableMap<LocalDate, Integer> getVaccinationCapacity() {
		return vaccinationCapacity;
	}

	@StringSetter(CAPACITY)
	void setVaccinationCapacity(String capacity) {

		Map<String, String> map = SPLITTER.split(capacity);
		setVaccinationCapacity_pers_per_day(map.entrySet().stream().collect(Collectors.toMap(
				e -> LocalDate.parse(e.getKey()), e -> Integer.parseInt(e.getValue())
		)));
	}

	@StringGetter(CAPACITY)
	String getVaccinationCapacityString() {
		return JOINER.join(vaccinationCapacity);
	}


}
