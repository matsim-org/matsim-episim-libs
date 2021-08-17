package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.doubles.DoubleDoublePair;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;

import java.time.LocalDate;
import java.util.EnumMap;
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

	private static final String COMPLIANCE = "compliance";
	private static final String CAPACITY = "vaccinationCapacity";
	private static final String RECAPACITY = "reVaccinationCapacity";
	private static final String SHARE = "vaccinationShare";

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
		private static final String BOOST_EFFECTIVENESS = "boostEffectiveness";
		private static final String FACTORS = "factors";

		private static final DoubleDoublePair DEFAULT_FACTORS = DoubleDoublePair.of(1d, 1d);

		private VaccinationType type;

		/**
		 * Number of days until vaccination goes into full effect.
		 */
		private int daysBeforeFullEffect = 21;

		/**
		 * Effectiveness, i.e. how much susceptibility is reduced.
		 */
		private Map<VirusStrain, Double> effectiveness = new EnumMap<>(Map.of(VirusStrain.SARS_CoV_2, 0.9));

		/**
		 * Effectiveness after booster shot.
		 */
		private Map<VirusStrain, Double> boostEffectiveness = new EnumMap<>(VirusStrain.class);

		/**
		 * Contains the probability factor for persons transitioning to showing symptoms and seriously sick
		 */
		private Map<VirusStrain, DoubleDoublePair> factors = new EnumMap<>(VirusStrain.class);

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


		@Deprecated
		public VaccinationParams setFactorSeriouslySick(double factorSeriouslySick) {
			throw new UnsupportedOperationException("Use .setFactors(...)");
		}

		@Deprecated
		public VaccinationParams setFactorShowingSymptoms(double factorShowingSymptoms) {
			throw new UnsupportedOperationException("Use .setFactors(...)");
		}

		/**
		 * Set factors for specific virus strain.
		 */
		public VaccinationParams setFactors(VirusStrain strain, double showingSymptoms, double seriouslySick) {
			factors.put(strain, DoubleDoublePair.of(showingSymptoms, seriouslySick));
			return this;
		}

		public double getFactorShowingSymptoms(VirusStrain strain) {
			return factors.getOrDefault(strain, DEFAULT_FACTORS).firstDouble();
		}

		public double getFactorSeriouslySick(VirusStrain strain) {
			return factors.getOrDefault(strain, DEFAULT_FACTORS).secondDouble();
		}


		@StringGetter(FACTORS)
		String getFactors() {
			return JOINER.join(factors);
		}

		@StringSetter(FACTORS)
		void setFactors(String value) {
			if (value.isBlank()) return;

			this.factors.clear();
			for (Map.Entry<String, String> e : SPLITTER.split(value).entrySet()) {

				String[] s = e.getValue().substring(1, e.getValue().length() - 1).split(",");
				this.factors.put(VirusStrain.valueOf(e.getKey()), DoubleDoublePair.of(Double.parseDouble(s[0]), Double.parseDouble(s[1])));
			}
		}

		@StringGetter(DAYS_BEFORE_FULL_EFFECT)
		public int getDaysBeforeFullEffect() {
			return daysBeforeFullEffect;
		}

		@StringSetter(DAYS_BEFORE_FULL_EFFECT)
		public void setDaysBeforeFullEffect(int daysBeforeFullEffect) {
			this.daysBeforeFullEffect = daysBeforeFullEffect;
		}

		@StringSetter(EFFECTIVENESS)
		void setEffectiveness(String value) {
			if (value.isBlank()) return;

			this.effectiveness.clear();
			for (Map.Entry<String, String> e : SPLITTER.split(value).entrySet()) {
				this.effectiveness.put(VirusStrain.valueOf(e.getKey()), Double.parseDouble(e.getValue()));
			}
		}

		@StringGetter(EFFECTIVENESS)
		String getEffectivenessString() {
			return JOINER.join(effectiveness);
		}

		/**
		 * Return effectiveness against base variant.
		 *
		 * @deprecated use {@link #getEffectiveness(VirusStrain)}
		 */
		@Deprecated
		public double getEffectiveness() {
			return getEffectiveness(VirusStrain.SARS_CoV_2);
		}

		/**
		 * Return effectiveness against base variant.
		 * @deprecated use {@link #setEffectiveness(VirusStrain, double)}
		 */
		@Deprecated
		public void setEffectiveness(double effectiveness) {
			setEffectiveness(VirusStrain.SARS_CoV_2, effectiveness);
		}

		/**
		 * Get the effectiveness against virus strain.
		 */
		public double getEffectiveness(VirusStrain strain) {
			return effectiveness.getOrDefault(strain, effectiveness.get(VirusStrain.SARS_CoV_2));
		}

		/**
		 * Set the effectiveness against a virus strain.
		 */
		public VaccinationParams setEffectiveness(VirusStrain strain, double effectiveness) {
			this.effectiveness.put(strain, effectiveness);
			return this;
		}

		////


		@StringSetter(BOOST_EFFECTIVENESS)
		void setBoostEffectiveness(String value) {
			if (value.isBlank()) return;

			this.boostEffectiveness.clear();
			for (Map.Entry<String, String> e : SPLITTER.split(value).entrySet()) {
				this.boostEffectiveness.put(VirusStrain.valueOf(e.getKey()), Double.parseDouble(e.getValue()));
			}
		}

		@StringGetter(BOOST_EFFECTIVENESS)
		String getBoostEffectivenessString() {
			return JOINER.join(boostEffectiveness);
		}

		/**
		 * Get the boost effectiveness against virus strain. If not set will be same as base effectiveness.
		 */
		public double getBoostEffectiveness(VirusStrain strain) {
			return boostEffectiveness.getOrDefault(strain, getEffectiveness(strain));
		}

		/**
		 * Set the boost effectiveness against a virus strain.
		 */
		public VaccinationParams setBoostEffectiveness(VirusStrain strain, double effectiveness) {
			this.boostEffectiveness.put(strain, effectiveness);
			return this;
		}
	}

}
