package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.VaccinationType;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.vaccination.VaccinationModel;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Config option specific to vaccination and measures performed in {@link VaccinationModel}.
 */
@SuppressWarnings("checkstyle:MethodName")
public class VaccinationConfigGroup extends ReflectiveConfigGroup {

	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");

	private static final String COMPLIANCE = "compliance";
	private static final String CAPACITY = "vaccinationCapacity";
	private static final String RECAPACITY = "reVaccinationCapacity";
	private static final String SHARE = "vaccinationShare";
	private static final String FROM_FILE = "vaccinationFile";
	private static final String DAYS_VALID = "daysValid";
	private static final String BETA = "beta";
	private static final String IGA = "IGA";
	private static final String TIME_PERIOD_IGA = "timePeriodIgA";
	private static final String VALID_DEADLINE = "validDeadline";

	static final String GROUPNAME = "episimVaccination";

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
	private final Map<VaccinationType, VaccinationParams> params = new HashMap<>();
	/**
	 * Load vaccinations from file instead.
	 */
	private String fromFile;
	/**
	 * Validity of vaccination in days.
	 */
	private int daysValid = 180;
	/**
	 * Needed for antibody model.
	 */
	private double beta = 1.0;
	/**
	 * Needed for antibody model.
	 */
	private boolean useIgA = false;
	private double timePeriodIgA = 120.;
	/**
	 * Deadline after which days valid is in effect.
	 */
	private LocalDate validDeadline = LocalDate.of(2022, 2, 1);


	/**
	 * Default constructor.
	 */
	public VaccinationConfigGroup() {
		super(GROUPNAME);

		// add default params
		getOrAddParams(VaccinationType.generic);
	}

	/**
	 * Creates an empty {@link Parameter} progression for one or multiple strain.
	 */
	public static VaccinationConfigGroupParameter forStrain(VirusStrain... strain) {
		return new VaccinationConfigGroupParameter(strain);
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
	 * Get vaccination compliance by age.
	 */
	public NavigableMap<Integer, Double> getCompliancePerAge() {
		return compliance;
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

	@StringGetter(FROM_FILE)
	public String getFromFile() {
		return fromFile;
	}

	@StringSetter(FROM_FILE)
	public void setFromFile(String fromFile) {
		this.fromFile = fromFile;
	}

	@StringGetter(DAYS_VALID)
	int getDaysValid() {
		return daysValid;
	}

	@StringSetter(DAYS_VALID)
	public void setDaysValid(int daysValid) {
		this.daysValid = daysValid;
	}

	@StringGetter(BETA)
	public double getBeta() {
		return beta;
	}

	@StringSetter(BETA)
	public void setBeta(double beta) {
		this.beta = beta;
	}

	@StringGetter(IGA)
	public boolean getUseIgA() {
		return useIgA;
	}

	@StringSetter(IGA)
	public void setUseIgA(boolean useIgA) {
		this.useIgA = useIgA;
	}

	@StringGetter(TIME_PERIOD_IGA)
	public double getTimePeriodIgA() {
		return this.timePeriodIgA;
	}

	@StringSetter(TIME_PERIOD_IGA)
	public void setTimePeriodIgA(double timePeriodIgA) {
		this.timePeriodIgA = timePeriodIgA;
	}

	@StringGetter(VALID_DEADLINE)
	public LocalDate getValidDeadline() {
		return validDeadline;
	}

	@StringSetter(VALID_DEADLINE)
	public void setValidDeadline(String validDeadline) {
		this.validDeadline = LocalDate.parse(validDeadline);
	}

	public void setValidDeadline(LocalDate validDeadline) {
		this.validDeadline = validDeadline;
	}

	/**
	 * Check if person is recently recovered or vaccinated.
	 */
	public boolean hasGreenPass(EpisimPerson person, int day, LocalDate date) {
		return hasGreenPass(person, day, date, daysValid);
	}

	/**
	 * Check 2G plus status, but use given {@code daysValid}.
	 */
	public boolean hasGreenPass(EpisimPerson person, int day, LocalDate date, int daysValid) {
		return hasRecoveredStatus(person, day, date, daysValid > -1 ? daysValid : this.daysValid) || hasValidVaccination(person, day, date, daysValid > -1 ? daysValid : this.daysValid);
	}

	/**
	 * Special type of green pass with separate setting for boostered or equivalent status.
	 */
	public boolean hasGreenPassForBooster(EpisimPerson p, int day, LocalDate date, int greenPassValidDays, int greenPassBoosterValidDays) {
		int valid = greenPassValidDays;

		// infected and vaccinated count as booster
		if (p.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.yes || (p.getNumInfections() >= 1 && p.getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes))
			valid = greenPassBoosterValidDays;

		return hasGreenPass(p, day, date, valid);
	}

	/**
	 * Check whether person has the recovered status.
	 */
	private boolean hasRecoveredStatus(EpisimPerson person, int day, LocalDate date, int daysValid) {
		// Initial the threshold was 180 days, this setting is adjusted to the threshold after the deadline
		return date.isBefore(validDeadline) ? person.isRecentlyRecovered(day, 180) : person.isRecentlyRecovered(day, daysValid);
	}

	/**
	 * Check if person has a valid vaccination card.
	 *
	 * @param person person to check
	 * @param day    current simulation day
	 * @param date   simulation date
	 */
	public boolean hasValidVaccination(EpisimPerson person, int day, LocalDate date) {
		return hasValidVaccination(person, day, date, getDaysValid());
	}

	/**
	 * Check if person has a valid vaccination card for the given validity period.
	 *
	 * @param person    person to check
	 * @param day       current simulation day
	 * @param date      simulation date
	 * @param daysValid number of days the vaccination remains valid
	 */
	public boolean hasValidVaccination(EpisimPerson person, int day, LocalDate date, int daysValid) {
		if (person.getVaccinationStatus() == EpisimPerson.VaccinationStatus.no)
			return false;

		boolean fullyVaccinated = person.daysSince(EpisimPerson.VaccinationStatus.yes, day) > getParams(person.getVaccinationType()).getDaysBeforeFullEffect();
		boolean booster = person.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.yes;

		if (date.isBefore(validDeadline))
			return fullyVaccinated || booster;

		return (fullyVaccinated || booster) && person.daysSince(EpisimPerson.VaccinationStatus.yes, day) <= daysValid;

	}

	/**
	 * Computes the minimum factor over all vaccinations.
	 *
	 * @param person person
	 * @param day    current iteration
	 * @param f      function of VaccinationParams to retrieve the desired factor
	 * @return minimum factor or 1 if not vaccinated
	 */
	public double getMinFactor(EpisimPerson person, int day, VaccinationFactorFunction f) {

		if (person.getNumVaccinations() == 0)
			return 1;

		double factor = 1d;
		for (int i = 0; i < person.getNumVaccinations(); i++) {

			VaccinationType type = person.getVaccinationType(i);

			factor = Math.min(factor, f.getFactor(getParams(type), person.getVirusStrain(), person.daysSince(EpisimPerson.VaccinationStatus.yes, day)));
		}

		return factor;
	}

	/**
	 * Set the daily revaccination capacity.
	 *
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
	 * Return vaccination share per date.
	 */
	public NavigableMap<LocalDate, Map<VaccinationType, Double>> getVaccinationShare() {
		return vaccinationShare;
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

	@StringSetter(SHARE)
	void setVaccinationShare(String value) {

		Map<String, String> share = Splitter.on("|").withKeyValueSeparator(">").split(value);
		Map<LocalDate, Map<VaccinationType, Double>> collect = share.entrySet().stream().collect(Collectors.toMap(
			e -> LocalDate.parse(e.getKey()),
			e -> SPLITTER.split(e.getValue()).entrySet().stream().collect(Collectors.toMap(k -> VaccinationType.of(k.getKey()), k -> Double.parseDouble(k.getValue())))
		));

		setVaccinationShare(collect);
	}

	/**
	 * Return the cumulative probability for all vaccination types, based on {@link #getVaccinationShare()}.
	 *
	 * @param date date to lookup
	 */
	public Map<VaccinationType, Double> getVaccinationTypeProb(LocalDate date) {

		// LinkedHashMap: chooseVaccinationType() iterates this and returns the first entry with p < value,
		// so the entries must stay in the ascending (declaration) order they are inserted in below.
		Map<VaccinationType, Double> prob = new LinkedHashMap<>();

		Map<VaccinationType, Double> share = EpisimUtils.findValidEntry(vaccinationShare, null, date);

		if (share == null)
			share = Map.of(VaccinationType.generic, 1d);

		double total = share.values().stream().sorted().mapToDouble(Double::doubleValue).sum();

		double sum = 1 - total;
		for (VaccinationType t : VaccinationType.getAllStandardOptions()) {
			sum += share.getOrDefault(t, 0d);
			prob.put(t, sum);
		}

		// custom vaccination types follow the standard ones in a deterministic order, so standard results are unchanged
		List<VaccinationType> custom = share.keySet().stream()
			.filter(t -> !prob.containsKey(t))
			.sorted(Comparator.comparing(VaccinationType::getId))
			.collect(Collectors.toList());

		for (VaccinationType t : custom) {
			sum += share.get(t);
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
		private static final String BOOST_INFECTIVITY = "boostInfectivity";
		private static final String BOOST_WAIT_PERIOD = "boostWaitPeriod";
		private static final String FACTOR_SHOWINGS_SYMPTOMS = "factorShowingSymptoms";
		private static final String FACTOR_SERIOUSLY_SICK = "factorSeriouslySick";
		private static final String FACTOR_CRITICAL = "factorCritical";

		private VaccinationType type;

		/**
		 * Number of days until vaccination goes into full effect.
		 */
		private int daysBeforeFullEffect = 28;

		/**
		 * Wait period before boost can be applied.
		 */
		private int boostWaitPeriod = 5 * 30;

		/**
		 * Effectiveness, i.e. how much susceptibility is reduced.
		 */
		private Map<VirusStrain, VaccinationConfigGroupParameter> effectiveness = new HashMap<>(Map.of(VirusStrain.SARS_CoV_2,
			forStrain(VirusStrain.SARS_CoV_2)
				.atDay(4, 0)
				.atDay(5, 0.45)
				.atFullEffect(0.9)
		));

		/**
		 * Infectivity of a vaccinated person towards others.
		 */
		private Map<VirusStrain, VaccinationConfigGroupParameter> infectivity = new HashMap<>(Map.of(VirusStrain.SARS_CoV_2,
			forStrain(VirusStrain.SARS_CoV_2)
				.atDay(0, 1)
				.atFullEffect(1.0)
		));

		/**
		 * Effectiveness after booster shot.
		 */
		private Map<VirusStrain, VaccinationConfigGroupParameter> boostEffectiveness = new HashMap<>();

		/**
		 * Infectivity of a vaccinated person towards others.
		 */
		private Map<VirusStrain, VaccinationConfigGroupParameter> boostInfectivity = new HashMap<>(Map.of(VirusStrain.SARS_CoV_2,
			forStrain(VirusStrain.SARS_CoV_2)
				.atDay(0, 1)
				.atFullEffect(1.0)
		));

		/**
		 * Factor for probability if person is vaccinated.
		 */
		private Map<VirusStrain, VaccinationConfigGroupParameter> factorShowingSymptoms = new HashMap<>(Map.of(VirusStrain.SARS_CoV_2,
			forStrain(VirusStrain.SARS_CoV_2)
				.atDay(5, 0.5)
		));

		/**
		 * Factor for probability if person is vaccinated.
		 */
		private Map<VirusStrain, VaccinationConfigGroupParameter> factorSeriouslySick = new HashMap<>(Map.of(VirusStrain.SARS_CoV_2,
			forStrain(VirusStrain.SARS_CoV_2)
				.atDay(5, 0.5)
		));

		/**
		 * Factor for probability if person is vaccinated.
		 */
		private Map<VirusStrain, VaccinationConfigGroupParameter> factorCritical = new HashMap<>(Map.of(VirusStrain.SARS_CoV_2,
			forStrain(VirusStrain.SARS_CoV_2)
				.atDay(0, 1)
		));

		VaccinationParams() {
			super(SET_TYPE);
		}

		@StringGetter(TYPE)
		String getTypeName() {
			return type == null ? null : type.getName();
		}

		@StringSetter(TYPE)
		void setType(String type) {
			this.type = VaccinationType.of(type);
		}

		/**
		 * Vaccination type this parameter set applies to.
		 */
		public VaccinationType getType() {
			return type;
		}

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

		@StringGetter(BOOST_WAIT_PERIOD)
		public int getBoostWaitPeriod() {
			return boostWaitPeriod;
		}

		@StringSetter(BOOST_WAIT_PERIOD)
		public VaccinationParams setBoostWaitPeriod(int boostWaitPeriod) {
			this.boostWaitPeriod = boostWaitPeriod;
			return this;
		}

		private VaccinationParams setParamsInternal(Map<VirusStrain, VaccinationConfigGroupParameter> map, VaccinationConfigGroupParameter[] params) {
			for (VaccinationConfigGroupParameter p : params) {
				for (VirusStrain s : p.strain) {
					p.setDaysBeforeFullEffect(getDaysBeforeFullEffect());
					map.put(s, p);
				}
			}
			return this;
		}

		/**
		 * Load serialized parameters.
		 */
		private void setParamsInternal(Map<VirusStrain, VaccinationConfigGroupParameter> map, String value) {
			map.clear();
			if (value.isBlank()) return;

			map.clear();
			for (Map.Entry<String, String> e : SPLITTER.split(value).entrySet()) {
				map.put(VirusStrain.of(e.getKey()), VaccinationConfigGroupParameter.parse(e.getValue()));
			}
		}

		/**
		 * Interpolate parameter for day after vaccination.
		 *
		 * @param map    map for lookup
		 * @param strain virus strain
		 * @param day    days since vaccination
		 * @return interpolated factor
		 */
		private double getParamsInternal(Map<VirusStrain, VaccinationConfigGroupParameter> map, VirusStrain strain, int day) {
			VaccinationConfigGroupParameter p = map.getOrDefault(strain, map.get(VirusStrain.SARS_CoV_2));
			return p.get(day);
		}

		private String getParamsInternal(Map<VirusStrain, VaccinationConfigGroupParameter> map) {

			Map<VirusStrain, String> result = map.entrySet().stream().collect(Collectors.toMap(
				Map.Entry::getKey,
				e -> e.getValue().toString()
			));

			return JOINER.join(result);
		}

		public VaccinationParams setBoostEffectiveness(VaccinationConfigGroupParameter... parameters) {
			return setParamsInternal(boostEffectiveness, parameters);
		}

		@StringSetter(BOOST_EFFECTIVENESS)
		void setBoostEffectiveness(String value) {
			setParamsInternal(boostEffectiveness, value);
		}

		public double getEffectiveness(VirusStrain strain, int day) {
			return getParamsInternal(effectiveness, strain, day);
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

		public double getInfectivity(VirusStrain strain, int day) {
			return getParamsInternal(infectivity, strain, day);
		}

		@StringGetter(INFECTIVITY)
		public String getInfectivity() {
			return getParamsInternal(infectivity);
		}

		public double getBoostInfectivity(VirusStrain strain, int day) {
			return getParamsInternal(boostInfectivity, strain, day);
		}

		@StringGetter(BOOST_INFECTIVITY)
		public String getBoostInfectivity() {
			return getParamsInternal(boostInfectivity);
		}

		public double getBoostEffectiveness(VirusStrain strain, int day) {
			return getParamsInternal(boostEffectiveness.containsKey(strain) ? boostEffectiveness : effectiveness, strain, day);
		}

		public double getFactorShowingSymptoms(VirusStrain strain, int day) {
			return getParamsInternal(factorShowingSymptoms, strain, day);
		}

		@StringGetter(FACTOR_SHOWINGS_SYMPTOMS)
		String getFactorShowingSymptoms() {
			return getParamsInternal(factorShowingSymptoms);
		}

		public double getFactorSeriouslySick(VirusStrain strain, int day) {
			return getParamsInternal(factorSeriouslySick, strain, day);
		}

		@StringGetter(FACTOR_SERIOUSLY_SICK)
		String getFactorSeriouslySick() {
			return getParamsInternal(factorSeriouslySick);
		}

		public double getFactorCritical(VirusStrain strain, int day) {
			return getParamsInternal(factorCritical, strain, day);
		}

		@StringGetter(FACTOR_CRITICAL)
		public String getFactorCritical() {
			return getParamsInternal(factorCritical);
		}

		@StringGetter(EFFECTIVENESS)
		String getEffectivenessString() {
			return getParamsInternal(effectiveness);
		}

		@StringGetter(BOOST_EFFECTIVENESS)
		String getBoostEffectivenessString() {
			return getParamsInternal(boostEffectiveness);
		}

		public VaccinationParams setFactorShowingSymptoms(VaccinationConfigGroupParameter... parameters) {
			return setParamsInternal(factorShowingSymptoms, parameters);
		}

		@StringSetter(FACTOR_SHOWINGS_SYMPTOMS)
		void setFactorShowingSymptoms(String value) {
			setParamsInternal(factorShowingSymptoms, value);
		}

		@Deprecated
		public VaccinationParams setFactorShowingSymptoms(double factorShowingSymptoms) {
			throw new UnsupportedOperationException("Use .setFactorShowingSymptoms(VaccinationConfigGroupParameter ...)");
		}

		public VaccinationParams setFactorSeriouslySick(VaccinationConfigGroupParameter... parameters) {
			return setParamsInternal(factorSeriouslySick, parameters);
		}

		@StringSetter(FACTOR_SERIOUSLY_SICK)
		void setFactorSeriouslySick(String value) {
			setParamsInternal(factorSeriouslySick, value);
		}

		@Deprecated
		public VaccinationParams setFactorSeriouslySick(double factorSeriouslySick) {
			throw new UnsupportedOperationException("Use .setFactorSeriouslySick(VaccinationConfigGroupParameter ...)");
		}

		public VaccinationParams setFactorCritical(VaccinationConfigGroupParameter... parameters) {
			return setParamsInternal(factorCritical, parameters);
		}

		@StringSetter(FACTOR_CRITICAL)
		void setFactorCritical(String value) {
			setParamsInternal(factorCritical, value);
		}

		public VaccinationParams setInfectivity(VaccinationConfigGroupParameter... parameters) {
			return setParamsInternal(infectivity, parameters);
		}

		@StringSetter(INFECTIVITY)
		void setInfectivity(String value) {
			setParamsInternal(infectivity, value);
		}

		public VaccinationParams setBoostInfectivity(VaccinationConfigGroupParameter... parameters) {
			return setParamsInternal(boostInfectivity, parameters);
		}

		@StringSetter(BOOST_INFECTIVITY)
		void setBoostInfectivity(String value) {
			setParamsInternal(boostInfectivity, value);
		}

		public VaccinationParams setEffectiveness(VaccinationConfigGroupParameter... parameters) {
			return setParamsInternal(effectiveness, parameters);
		}

		@StringSetter(EFFECTIVENESS)
		void setEffectiveness(String value) {
			setParamsInternal(effectiveness, value);
		}

		/**
		 * Return effectiveness against base variant.
		 *
		 * @deprecated use {@link #setEffectiveness(VaccinationConfigGroupParameter...)}
		 */
		@Deprecated
		public void setEffectiveness(double effectiveness) {
			throw new UnsupportedOperationException("Use .setEffectiveness(VaccinationConfigGroupParameter ...)");
		}

	}

	/**
	 * Holds the temporal progression of certain value for each virus strains.
	 */
	public static final class VaccinationConfigGroupParameter {

		private static final Splitter.MapSplitter SPLITTER = Splitter.on("|").withKeyValueSeparator(">");
		private static final Joiner.MapJoiner JOINER = Joiner.on("|").withKeyValueSeparator(">");

		private final VirusStrain[] strain;
		private final NavigableMap<Integer, Double> map = new TreeMap<>();

		private VaccinationConfigGroupParameter(VirusStrain[] strain) {
			this.strain = strain;
		}

		private VaccinationConfigGroupParameter(Map<String, String> map) {
			this.strain = new VirusStrain[0];
			for (Map.Entry<String, String> e : map.entrySet()) {
				this.map.put(Integer.parseInt(e.getKey()), Double.parseDouble(e.getValue()));
			}

		}

		private static VaccinationConfigGroupParameter parse(String value) {
			Map<String, String> m = SPLITTER.split(value);
			return new VaccinationConfigGroupParameter(m);
		}

		/**
		 * Sets the value for a parameter at a specific day.
		 */
		public VaccinationConfigGroupParameter atDay(int day, double value) {
			map.put(day, value);
			return this;
		}

		/**
		 * Sets the value for parameter for the day of full effect.
		 * {@link VaccinationParams#setDaysBeforeFullEffect(int)} has to be set before calling this method!
		 */
		public VaccinationConfigGroupParameter atFullEffect(double value) {
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
	}

}
