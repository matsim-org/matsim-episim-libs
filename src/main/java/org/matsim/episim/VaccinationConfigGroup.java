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
	private static final String COMPLIANCE = "compliance";
	private static final String CAPACITY = "vaccinationCapacity";
	private static final String RECAPACITY = "reVaccinationCapacity";
	private static final String FACTOR_SERIOUSLY_SICK = "factorSeriouslySick";

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
	 * Factor for probability if person is vaccinated.
	 */
	private double factorSeriouslySick = 1.0;

	/**
	 * Amount of vaccinations available per day.
	 */
	private final NavigableMap<LocalDate, Integer> vaccinationCapacity = new TreeMap<>();

	/**
	 * Amount of re-vaccinations available per day.
	 */
	private final NavigableMap<LocalDate, Integer> reVaccinationCapacity = new TreeMap<>();

	/**
	 * Vaccination compliance by age groups. Keys are the left bounds of age group intervals.
	 * -1 is used as lookup when no age is present.
	 */
	private final NavigableMap<Integer, Double> compliance = new TreeMap<>(Map.of(-1, 1.0));


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
	 * Set vaccination compliance by age.
	 *
	 * @see #compliance
	 */
	public void setCompliancePerAge(Map<Integer, Double> compliance) {
		this.compliance.clear();
		this.compliance.putAll(compliance);
	}

	/**
	 * Get vaccination compliance by age.
	 */
	public NavigableMap<Integer, Double> getCompliancePerAge() {
		return compliance;
	}

	@StringSetter(COMPLIANCE)
	void setCompliance(String compliance) {
		Map<String, String> map = SPLITTER.split(compliance);
		setCompliancePerAge(map.entrySet().stream().collect(Collectors.toMap(
				e -> Integer.parseInt(e.getKey()), e -> Double.parseDouble(e.getValue())
		)));
	}

	@StringGetter(COMPLIANCE)
	String getComplianceString() {
		return JOINER.join(compliance);
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

	/**
	 * @see #setVaccinationCapacity_pers_per_day(Map)
	 */
	public void setReVaccinationCapacity_pers_per_day(Map<LocalDate, Integer> capacity) {
		reVaccinationCapacity.clear();
		reVaccinationCapacity.putAll(capacity);
	}

	public NavigableMap<LocalDate, Integer> getReVaccinationCapacity() {
		return reVaccinationCapacity;
	}

	@StringSetter(RECAPACITY)
	void setReVaccinationCapacity(String capacity) {

		Map<String, String> map = SPLITTER.split(capacity);
		setReVaccinationCapacity_pers_per_day(map.entrySet().stream().collect(Collectors.toMap(
				e -> LocalDate.parse(e.getKey()), e -> Integer.parseInt(e.getValue())
		)));
	}

	@StringGetter(RECAPACITY)
	String getReVaccinationCapacityString() {
		return JOINER.join(reVaccinationCapacity);
	}

	@StringGetter(FACTOR_SERIOUSLY_SICK)
	public double getFactorSeriouslySick() {
		return factorSeriouslySick;
	}

	@StringSetter(FACTOR_SERIOUSLY_SICK)
	public void setFactorSeriouslySick(double factorSeriouslySick) {
		this.factorSeriouslySick = factorSeriouslySick;
	}
}
