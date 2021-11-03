package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.vaccination.VaccinationModel;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Config option specific to vaccination and measures performed in {@link VaccinationModel}.
 */
public class VaccinationConfigGroup extends ReflectiveConfigGroup {

	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");

	private static final String COMPLIANCE = "compliance";
	private static final String CAPACITY = "vaccinationCapacity";
	private static final String RECAPACITY = "reVaccinationCapacity";
	private static final String SHARE = "vaccinationShare";
	private static final String FROM_FILE = "vaccinationFile";

	private static final String GROUPNAME = "episimVaccination";


	/**
	 * Amount of vaccinations available per day.
	 */
	private final NavigableMap<LocalDate, Integer> vaccinationCapacity = new TreeMap<>();

	/**
	 * Amount of re-vaccinations available per day.
	 */
	private final NavigableMap<LocalDate, Integer> reVaccinationCapacity = new TreeMap<>();

	/**
	 * Share of vaccination for the different {@link VaccinationType}.
	 */
	private final NavigableMap<LocalDate, Map<VaccinationType, Double>> vaccinationShare = new TreeMap<>(Map.of(LocalDate.EPOCH, Map.of(VaccinationType.generic, 1d)));

	/**
	 * Load vaccinations from file instead.
	 */
	private String fromFile;

	/**
	 * Vaccination compliance by age groups. Keys are the left bounds of age group intervals.
	 * -1 is used as lookup when no age is present.
	 */
	private final NavigableMap<Integer, Double> compliance = new TreeMap<>(Map.of(-1, 1.0));

	/**
	 * Holds all specific vaccination params.
	 */
	private final Map<VaccinationType, VaccinationParams> params = new EnumMap<>(VaccinationType.class);

	/**
	 * Default constructor.
	 */
	public VaccinationConfigGroup() {
		super(GROUPNAME);

		// add default params
		getOrAddParams(VaccinationType.generic);
	}

	/**
	 * Get config parameter for a specific vaccination type.
	 */
	public VaccinationParams getParams(VaccinationType type) {
		if (!params.containsKey(type))
			throw new IllegalStateException("Vaccination type " + type + " is not configured.");

		return params.get(type);
	}

	/**
	 * Whether config contains certain type.
	 */
	public boolean hasParams(VaccinationType type) {
		return params.containsKey(type);
	}

	/**
	 * Get an existing or add new parameter set.
	 */
	public VaccinationParams getOrAddParams(VaccinationType type) {
		if (!params.containsKey(type)) {
			VaccinationParams p = new VaccinationParams();
			p.type = type;
			addParameterSet(p);
			return p;
		}

		return params.get(type);
	}

	@Override
	public ConfigGroup createParameterSet(String type) {
		if (VaccinationParams.SET_TYPE.equals(type)) {
			return new VaccinationParams();
		}
		throw new IllegalArgumentException("Unknown type " + type);
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		if (VaccinationParams.SET_TYPE.equals(set.getName())) {
			VaccinationParams p = (VaccinationParams) set;
			params.put(p.type, p);
			super.addParameterSet(set);

		} else
			throw new IllegalStateException("Unknown set type " + set.getName());
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

		if (capacity.isBlank())
			return;

		Map<String, String> map = SPLITTER.split(capacity);
		setVaccinationCapacity_pers_per_day(map.entrySet().stream().collect(Collectors.toMap(
				e -> LocalDate.parse(e.getKey()), e -> Integer.parseInt(e.getValue())
		)));
	}

	@StringGetter(CAPACITY)
	String getVaccinationCapacityString() {
		return JOINER.join(vaccinationCapacity);
	}

	@StringSetter(FROM_FILE)
	public void setFromFile(String fromFile) {
		this.fromFile = fromFile;
	}

	@StringGetter(FROM_FILE)
	public String getFromFile() {
		return fromFile;
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

		if (capacity.isBlank())
			return;

		Map<String, String> map = SPLITTER.split(capacity);
		setReVaccinationCapacity_pers_per_day(map.entrySet().stream().collect(Collectors.toMap(
				e -> LocalDate.parse(e.getKey()), e -> Integer.parseInt(e.getValue())
		)));
	}

	@StringGetter(RECAPACITY)
	String getReVaccinationCapacityString() {
		return JOINER.join(reVaccinationCapacity);
	}


	/**
	 * Set the vaccination share per date.
	 */
	public void setVaccinationShare(Map<LocalDate, Map<VaccinationType, Double>> share) {
		for (Map<VaccinationType, Double> v : share.values()) {
			double total = v.values().stream().sorted().mapToDouble(Double::doubleValue).sum();
			if (total > 1) throw new IllegalArgumentException("Sum of shares must be < 1");
		}

		this.vaccinationShare.clear();
		this.vaccinationShare.putAll(share);
	}

	/**
	 * Return vaccination share per date.
	 */
	public NavigableMap<LocalDate, Map<VaccinationType, Double>> getVaccinationShare() {
		return vaccinationShare;
	}

	/**
	 * Return the cumulative probability for all vaccination types, based on {@link #getVaccinationShare()}.
	 *
	 * @param date date to lookup
	 */
	public Map<VaccinationType, Double> getVaccinationTypeProb(LocalDate date) {

		EnumMap<VaccinationType, Double> prob = new EnumMap<>(VaccinationType.class);

		Map<VaccinationType, Double> share = EpisimUtils.findValidEntry(vaccinationShare, null, date);

		if (share == null)
			share = Map.of(VaccinationType.generic, 1d);

		double total = share.values().stream().sorted().mapToDouble(Double::doubleValue).sum();

		double sum = 1 - total;
		for (VaccinationType t : VaccinationType.values()) {
			sum += share.getOrDefault(t, 0d);
			prob.put(t, sum);
		}

		return prob;
	}

	@StringGetter(SHARE)
	String getVaccinationShareString() {
		Map<LocalDate, String> collect =
				vaccinationShare.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> JOINER.join(e.getValue())));

		return Joiner.on("|").withKeyValueSeparator(">").join(collect);
	}

	@StringSetter(SHARE)
	void setVaccinationShare(String value) {

		Map<String, String> share = Splitter.on("|").withKeyValueSeparator(">").split(value);
		Map<LocalDate, Map<VaccinationType, Double>> collect = share.entrySet().stream().collect(Collectors.toMap(
				e -> LocalDate.parse(e.getKey()),
				e -> SPLITTER.split(e.getValue()).entrySet().stream().collect(Collectors.toMap(k -> VaccinationType.valueOf(k.getKey()), k -> Double.parseDouble(k.getValue())))
		));

		setVaccinationShare(collect);
	}

	/**
	 * Holds strain specific options.
	 */
	public static final class VaccinationParams extends ReflectiveConfigGroup {

		static final String SET_TYPE = "vaccinationParams";

		private static final String TYPE = "type";
		private static final String DAYS_BEFORE_FULL_EFFECT = "daysBeforeFullEffect";
		private static final String EFFECTIVENESS = "effectiveness";
		private static final String INFECTIVITY = "infectivity";
		private static final String BOOST_EFFECTIVENESS = "boostEffectiveness";
		private static final String BOOST_WAIT_PERIOD = "boostWaitPeriod";
		private static final String FACTOR_SHOWINGS_SYMPTOMS = "factorShowingSymptoms";
		private static final String FACTOR_SERIOUSLY_SICK = "factorSeriouslySick";

		private VaccinationType type;

		/**
		 * Number of days until vaccination goes into full effect.
		 */
		private int daysBeforeFullEffect = 28;

		/**
		 * Wait period before boost can be applied.
		 */
		private int boostWaitPeriod = 5* 30;

		/**
		 * Effectiveness, i.e. how much susceptibility is reduced.
		 */
		private Map<VirusStrain, Parameter> effectiveness = new EnumMap<>(Map.of(VirusStrain.SARS_CoV_2,
				forStrain(VirusStrain.SARS_CoV_2)
						.atDay(4, 0)
						.atDay(5, 0.45)
						.atFullEffect(0.9)
		));

		/**
		 * Infectivity of a vaccinated person towards others.
		 */
		private Map<VirusStrain, Parameter> infectivity = new EnumMap<>(Map.of(VirusStrain.SARS_CoV_2,
				forStrain(VirusStrain.SARS_CoV_2)
						.atDay(0, 1)
						.atFullEffect(1.0)
		));

		/**
		 * Effectiveness after booster shot.
		 */
		private Map<VirusStrain, Parameter> boostEffectiveness = new EnumMap<>(VirusStrain.class);

		/**
		 * Factor for probability if person is vaccinated.
		 */
		private Map<VirusStrain, Parameter> factorShowingSymptoms = new EnumMap<>(Map.of(VirusStrain.SARS_CoV_2,
				forStrain(VirusStrain.SARS_CoV_2)
						.atDay(5, 0.5)
		));

		/**
		 * Factor for probability if person is vaccinated.
		 */
		private Map<VirusStrain, Parameter> factorSeriouslySick = new EnumMap<>(Map.of(VirusStrain.SARS_CoV_2,
				forStrain(VirusStrain.SARS_CoV_2)
						.atDay(5, 0.5)
		));

		VaccinationParams() {
			super(SET_TYPE);
		}

		@StringGetter(TYPE)
		public VaccinationType getType() {
			return type;
		}

		@StringSetter(TYPE)
		public void setType(VaccinationType type) {
			this.type = type;
		}

		@StringGetter(DAYS_BEFORE_FULL_EFFECT)
		public int getDaysBeforeFullEffect() {
			return daysBeforeFullEffect;
		}

		@StringSetter(DAYS_BEFORE_FULL_EFFECT)
		public VaccinationParams setDaysBeforeFullEffect(int daysBeforeFullEffect) {
			this.daysBeforeFullEffect = daysBeforeFullEffect;
			return this;
		}

		@StringSetter(BOOST_WAIT_PERIOD)
		public void setBoostWaitPeriod(int boostWaitPeriod) {
			this.boostWaitPeriod = boostWaitPeriod;
		}

		@StringGetter(BOOST_WAIT_PERIOD)
		public int getBoostWaitPeriod() {
			return boostWaitPeriod;
		}

		private VaccinationParams setParamsInternal(Map<VirusStrain, Parameter> map, Parameter[] params) {
			for (Parameter p : params) {
				for (VirusStrain s : p.strain) {
					p.setDaysBeforeFullEffect(getDaysBeforeFullEffect());
					map.put(s, p);
				}
			}
			return this;
		}

		/**
		 * Interpolate parameter for day after vaccination.
		 *
		 * @param map    map for lookup
		 * @param strain virus strain
		 * @param day    days since vaccination
		 * @return interpolated factor
		 */
		private double getParamsInternal(Map<VirusStrain, Parameter> map, VirusStrain strain, int day) {
			Parameter p = map.getOrDefault(strain, map.get(VirusStrain.SARS_CoV_2));
			return p.get(day);
		}

		public VaccinationParams setEffectiveness(Parameter... parameters) {
			return setParamsInternal(effectiveness, parameters);
		}

		public VaccinationParams setInfectivity(Parameter... parameters) {
			return setParamsInternal(infectivity, parameters);
		}

		public VaccinationParams setBoostEffectiveness(Parameter... parameters) {
			return setParamsInternal(boostEffectiveness, parameters);
		}

		public VaccinationParams setFactorShowingSymptoms(Parameter... parameters) {
			return setParamsInternal(factorShowingSymptoms, parameters);
		}

		public VaccinationParams setFactorSeriouslySick(Parameter... parameters) {
			return setParamsInternal(factorSeriouslySick, parameters);
		}

		public double getEffectiveness(VirusStrain strain, int day) {
			return getParamsInternal(effectiveness, strain, day);
		}

		public double getInfectivity(VirusStrain strain, int day) {
			return getParamsInternal(infectivity, strain, day);
		}

		public double getBoostEffectiveness(VirusStrain strain, int day) {
			return getParamsInternal(boostEffectiveness.containsKey(strain) ? boostEffectiveness : effectiveness, strain, day);
		}

		public double getFactorShowingSymptoms(VirusStrain strain, int day) {
			return getParamsInternal(factorShowingSymptoms, strain, day);
		}

		public double getFactorSeriouslySick(VirusStrain strain, int day) {
			return getParamsInternal(factorSeriouslySick, strain, day);
		}
		/**
		 * Load serialized parameters
		 */
		private void setParamsInternal(Map<VirusStrain, Parameter> map, String value) {
			map.clear();
			if (value.isBlank()) return;

			map.clear();
			for (Map.Entry<String, String> e : SPLITTER.split(value).entrySet()) {
				map.put(VirusStrain.valueOf(e.getKey()), Parameter.parse(e.getValue()));
			}
		}

		private String getParamsInternal(Map<VirusStrain, Parameter> map) {

			Map<VirusStrain, String> result = map.entrySet().stream().collect(Collectors.toMap(
					Map.Entry::getKey,
					e -> e.getValue().toString()
			));

			return JOINER.join(result);
		}

		@StringSetter(EFFECTIVENESS)
		void setEffectiveness(String value) {
			setParamsInternal(effectiveness, value);
		}

		@StringGetter(EFFECTIVENESS)
		String getEffectivenessString() {
			return getParamsInternal(effectiveness);
		}

		@StringSetter(BOOST_EFFECTIVENESS)
		void setBoostEffectiveness(String value) {
			setParamsInternal(boostEffectiveness, value);
		}

		@StringGetter(BOOST_EFFECTIVENESS)
		String getBoostEffectivenessString() {
			return getParamsInternal(boostEffectiveness);
		}

		@StringSetter(FACTOR_SHOWINGS_SYMPTOMS)
		void setFactorShowingSymptoms(String value) {
			setParamsInternal(factorShowingSymptoms, value);
		}

		@StringGetter(FACTOR_SHOWINGS_SYMPTOMS)
		String getFactorShowingSymptoms() {
			return getParamsInternal(factorShowingSymptoms);
		}

		@StringSetter(FACTOR_SERIOUSLY_SICK)
		void setFactorSeriouslySick(String value) {
			setParamsInternal(factorSeriouslySick, value);
		}

		@StringGetter(FACTOR_SERIOUSLY_SICK)
		String getFactorSeriouslySick() {
			return getParamsInternal(factorSeriouslySick);
		}

		@StringSetter(INFECTIVITY)
		void setInfectivity(String value) {
			setParamsInternal(infectivity, value);
		}

		@StringGetter(INFECTIVITY)
		public String getInfectivity() {
			return getParamsInternal(infectivity);
		}

		/**
		 * Return effectiveness against base variant.
		 *
		 * @deprecated use {@link #getEffectiveness(VirusStrain, int)}
		 */
		@Deprecated
		public double getEffectiveness() {
			return getEffectiveness(VirusStrain.SARS_CoV_2, getDaysBeforeFullEffect());
		}

		/**
		 * Return effectiveness against base variant.
		 *
		 * @deprecated use {@link #setEffectiveness(Parameter...)}
		 */
		@Deprecated
		public void setEffectiveness(double effectiveness) {
			throw new UnsupportedOperationException("Use .setEffectiveness(Parameter...)");
		}

		@Deprecated
		public VaccinationParams setFactorSeriouslySick(double factorSeriouslySick) {
			throw new UnsupportedOperationException("Use .setFactorSeriouslySick(Parameter...)");
		}

		@Deprecated
		public VaccinationParams setFactorShowingSymptoms(double factorShowingSymptoms) {
			throw new UnsupportedOperationException("Use .setFactorShowingSymptoms(Parameter...)");
		}

	}

	/**
	 * Creates an empty {@link Parameter} progression for one or multiple strain.
	 */
	public static Parameter forStrain(VirusStrain... strain) {
		return new Parameter(strain);
	}

	/**
	 * Holds the temporal progression of certain value for each virus strains.
	 */
	public static final class Parameter {

		private static final Splitter.MapSplitter SPLITTER = Splitter.on("|").withKeyValueSeparator(">");
		private static final Joiner.MapJoiner JOINER = Joiner.on("|").withKeyValueSeparator(">");

		private final VirusStrain[] strain;
		private final NavigableMap<Integer, Double> map = new TreeMap<>();

		private Parameter(VirusStrain[] strain) {
			this.strain = strain;
		}

		private Parameter(Map<String, String> map) {
			this.strain = new VirusStrain[0];
			for (Map.Entry<String, String> e : map.entrySet()) {
				this.map.put(Integer.parseInt(e.getKey()), Double.parseDouble(e.getValue()));
			}

		}

		/**
		 * Sets the value for a parameter at a specific day.
		 */
		public Parameter atDay(int day, double value) {
			map.put(day, value);
			return this;
		}


		/**
		 * Sets the value for parameter for the day of full effect.
		 * {@link VaccinationParams#setDaysBeforeFullEffect(int)} has to be set before calling this method!
		 */
		public Parameter atFullEffect(double value) {
			map.put(Integer.MAX_VALUE, value);
			return this;
		}


		/**
		 * Interpolate for given day.
		 */
		private double get(int day) {

			Map.Entry<Integer, Double> floor = map.floorEntry(day);

			if (floor == null)
				return map.firstEntry().getValue();

			if (floor.getKey().equals(day))
				return floor.getValue();

			Map.Entry<Integer, Double> ceil = map.ceilingEntry(day);

			// there is no higher entry to interpolate
			if (ceil == null)
				return floor.getValue();

			double between = ceil.getKey() - floor.getKey();
			double diff = day - floor.getKey();
			return floor.getValue() + diff * (ceil.getValue() - floor.getValue()) / between;
		}

		private void setDaysBeforeFullEffect(int daysBeforeFullEffect) {
			if (map.containsKey(Integer.MAX_VALUE))
				map.put(daysBeforeFullEffect, map.remove(Integer.MAX_VALUE));
		}

		@Override
		public String toString() {
			return JOINER.join(map);
		}

		private static Parameter parse(String value) {
			Map<String, String> m = SPLITTER.split(value);
			return new Parameter(m);
		}
	}

}
