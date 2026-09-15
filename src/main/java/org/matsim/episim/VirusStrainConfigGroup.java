package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.Pathogen;
import org.matsim.episim.model.VirusStrain;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Config option specific to {@link org.matsim.episim.model.VirusStrain}.
 */
public class VirusStrainConfigGroup extends ReflectiveConfigGroup {

	private static final Splitter.MapSplitter SPLITTER = Splitter.on(";").withKeyValueSeparator("=");
	private static final Joiner.MapJoiner JOINER = Joiner.on(";").withKeyValueSeparator("=");

	private static final String GROUPNAME = "virusStrains";

	/**
	 * Holds all virus strains params.
	 */
	private final Map<VirusStrain, StrainParams> strains = new LinkedHashMap<>();

	/**
	 * Cached result of {@link #getVirusStrains()}, null if it needs to be rebuilt.
	 */
	private volatile Collection<VirusStrain> virusStrains;

	/**
	 * Default constructor.
	 */
	public VirusStrainConfigGroup() {
		super(GROUPNAME);

		// add default params
		getOrAddParams(VirusStrain.SARS_CoV_2);
	}

	/**
	 * Get config parameter for a specific strain.
	 */
	public StrainParams getParams(VirusStrain strain) {
		if (!strains.containsKey(strain))
			throw new IllegalStateException(("Virus strain " + strain + " is not configured."));

		return strains.get(strain);
	}

	/**
	 * Get an existing or add new parameter set.
	 */
	public StrainParams getOrAddParams(VirusStrain strain) {
		if (!strains.containsKey(strain)) {
			StrainParams p = new StrainParams();
			p.setStrain(strain);
			addParameterSet(p);
			return p;
		}

		return strains.get(strain);
	}

	/**
	 * Whether params for strain are present.
	 */
	public boolean hasParams(VirusStrain strain) {
		return strains.containsKey(strain);
	}

	/**
	 * Returns the standard virus strains followed by strains added through this configuration.
	 */
	public Collection<VirusStrain> getVirusStrains() {
		// cached, because this is called on hot paths; reset whenever a strain is added
		Collection<VirusStrain> result = virusStrains;
		if (result == null) {
			Set<VirusStrain> set = new LinkedHashSet<>(VirusStrain.getAllStandardOptions());
			set.addAll(strains.keySet());
			result = Collections.unmodifiableSet(set);
			virusStrains = result;
		}
		return result;
	}

	/**
	 * Every strain that is imported with a positive number of infections needs a parameter set. Since strain names are
	 * resolved leniently while the config is read, this catches misspelled strain names before the simulation starts.
	 */
	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);

		ConfigGroup episim = config.getModules().get("episim");
		if (!(episim instanceof EpisimConfigGroup))
			return;

		List<String> missing = new ArrayList<>();
		for (Map.Entry<VirusStrain, NavigableMap<LocalDate, Integer>> e : ((EpisimConfigGroup) episim).getInfections_pers_per_day().entrySet()) {
			boolean imported = e.getValue().values().stream().anyMatch(n -> n > 0);
			if (imported && !strains.containsKey(e.getKey()))
				missing.add(e.getKey().getVirusStrainName());
		}

		if (!missing.isEmpty())
			throw new IllegalStateException("Virus strains " + missing + " are imported via 'infections_pers_per_day' of config group "
					+ "'episim', but have no '" + StrainParams.SET_TYPE + "' in config group '" + GROUPNAME + "'. "
					+ "Check the strain names for typos or add parameter sets for these strains.");
	}

	@Override
	public ConfigGroup createParameterSet(String type) {
		if (StrainParams.SET_TYPE.equals(type)) {
			return new StrainParams();
		}
		throw new IllegalArgumentException("Unknown type" + type);
	}

	@Override
	public void addParameterSet(final ConfigGroup set) {
		if (StrainParams.SET_TYPE.equals(set.getName())) {
			StrainParams p = (StrainParams) set;
			StrainParams previous = strains.put(p.getStrain(), p);
			// replace a previously registered set (e.g. the auto-added SARS-CoV-2 default) instead of keeping a stale duplicate
			if (previous != null)
				super.removeParameterSet(previous);
			super.addParameterSet(set);
			virusStrains = null;

		} else
			throw new IllegalStateException("Unknown set type " + set.getName());
	}

	/**
	 * Holds strain specific options.
	 */
	public static final class StrainParams extends ReflectiveConfigGroup {

		static final String SET_TYPE = "strainParams";

		private static final String STRAIN = "strain";
		private static final String PATHOGEN = "pathogen";
		private static final String INFECTIOUSNESS = "infectiousness";
		private static final String FACTOR_SERIOUSLY_SICK = "factorSeriouslySick";
		private static final String FACTOR_CRITICAL = "factorCritical";
		private static final String FACTOR_SERIOUSLY_SICK_VAC = "factorSeriouslySickVaccinated";
		private static final String AGE_SUSCEPTIBILITY = "ageSusceptibility";
		private static final String AGE_INFECTIVITY = "ageInfectivity";

		/**
		 * Type of the strain.
		 */
		private String strainName;
		private Pathogen pathogen = Pathogen.SARS_COV_2;
		private VirusStrain strain;

		/**
		 * v Infectiousness of this variant.
		 */
		private double infectiousness = 1.0;

		/**
		 * Factor for probability.
		 */
		private double factorSeriouslySick = 1.0;

		/**
		 * Factor for critical probability.
		 */
		private double factorCritical = 1.0;

		/**
		 * Factor for probability when person is vaccinated.
		 */
		private double factorSeriouslySickVaccinated = 1.0;

		/**
		 * Child susceptibility used in AgeDependentInfectionModelWithSeasonality.
		 * Taken from https://doi.org/10.1101/2020.06.03.20121145
		 */
		private final NavigableMap<Integer, Double> ageSusceptibility = new TreeMap<>(Map.of(
				19, 0.45,
				20, 1d
		));

		/**
		 * Child infectivity used in AgeDependentInfectionModelWithSeasonality.
		 * Taken from https://doi.org/10.1101/2020.06.03.20121145
		 */
		private final NavigableMap<Integer, Double> ageInfectivity = new TreeMap<>(Map.of(
				19, 0.85,
				20, 1d
		));

		StrainParams() {
			super(SET_TYPE);
		}

		@StringGetter(STRAIN)
		public String getStrainName(){
			return strainName;
		}

		public VirusStrain getStrain() {
			if (strain == null && strainName != null) {
				strain = VirusStrain.of(pathogen, strainName);
				pathogen = strain.getPathogen();
			}
			return strain;
		}

		@StringSetter(STRAIN)
		public void setStrain(String strain){
			this.strainName = strain;
			this.strain = null;
		}

		public void setStrain(VirusStrain strain) {
			this.strain = strain;
			this.strainName = strain == null ? null : strain.getVirusStrainName();
			this.pathogen = strain == null ? Pathogen.SARS_COV_2 : strain.getPathogen();
		}

		@StringGetter(PATHOGEN)
		public String getPathogenName() {
			return getPathogen().getName();
		}

		/**
		 * Pathogen of this strain. Once the strain is known, its pathogen is used, which may have been declared after this
		 * parameter set was created (see {@link VirusStrain#of(Pathogen, String)}).
		 */
		public Pathogen getPathogen() {
			return strain != null ? strain.getPathogen() : pathogen;
		}

		@StringSetter(PATHOGEN)
		public void setPathogen(String pathogen) {
			setPathogen(new Pathogen(pathogen));
		}

		public void setPathogen(Pathogen pathogen) {
			this.pathogen = Objects.requireNonNull(pathogen, "Pathogen must not be null");
			this.strain = null;
		}

		@StringGetter(INFECTIOUSNESS)
		public double getInfectiousness() {
			return infectiousness;
		}

		@StringSetter(INFECTIOUSNESS)
		public void setInfectiousness(double infectiousness) {
			this.infectiousness = infectiousness;
		}

		@Deprecated
		public double getVaccineEffectiveness() {
			throw new RuntimeException("Deprecated: Configure effectiveness in hte vaccination config.");
		}

		@Deprecated
		public void setVaccineEffectiveness(double vaccineEffectiveness) {
			throw new RuntimeException("Deprecated: Configure effectiveness in hte vaccination config.");
		}

		@Deprecated
		public double getReVaccineEffectiveness() {
			throw new RuntimeException("Deprecated: Configure effectiveness in hte vaccination config.");
		}

		@Deprecated
		public void setReVaccineEffectiveness(double vaccineEffectiveness) {
			throw new RuntimeException("Deprecated: Configure effectiveness in hte vaccination config.");
		}

		@StringSetter(FACTOR_SERIOUSLY_SICK)
		public void setFactorSeriouslySick(double factorSeriouslySick) {
			this.factorSeriouslySick = factorSeriouslySick;
		}

		@StringGetter(FACTOR_SERIOUSLY_SICK)
		public double getFactorSeriouslySick() {
			return factorSeriouslySick;
		}

		@StringGetter(FACTOR_CRITICAL)
		public double getFactorCritical() {
			return factorCritical;
		}

		@StringSetter(FACTOR_CRITICAL)
		public void setFactorCritical(double factorCritical) {
			this.factorCritical = factorCritical;
		}

		/**
		 * Configure this in the vaccination config instead. Nonetheless, for now this value will still be respected.
		 */
		@Deprecated
		@StringSetter(FACTOR_SERIOUSLY_SICK_VAC)
		public void setFactorSeriouslySickVaccinated(double factorSeriouslySickVaccinated) {
			this.factorSeriouslySickVaccinated = factorSeriouslySickVaccinated;
		}

		@StringGetter(FACTOR_SERIOUSLY_SICK_VAC)
		public double getFactorSeriouslySickVaccinated() {
			return factorSeriouslySickVaccinated;
		}


		@StringGetter(AGE_SUSCEPTIBILITY)
		String getAgeSusceptibilityString() {
			return JOINER.join(ageSusceptibility);
		}

		@StringSetter(AGE_SUSCEPTIBILITY)
		void setAgeSusceptibility(String config) {
			Map<String, String> map = SPLITTER.split(config);
			setAgeSusceptibility(map.entrySet().stream().collect(Collectors.toMap(
					e -> Integer.parseInt(e.getKey()), e -> Double.parseDouble(e.getValue())
			)));
		}

		/**
		 * Return susceptibility for different age groups.
		 */
		public NavigableMap<Integer, Double> getAgeSusceptibility() {
			return ageSusceptibility;
		}

		/**
		 * Set susceptibility for all age groups, previous entries will be overwritten.
		 */
		public void setAgeSusceptibility(Map<Integer, Double> ageSusceptibility) {
			this.ageSusceptibility.clear();
			this.ageSusceptibility.putAll(ageSusceptibility);
		}

		@StringGetter(AGE_INFECTIVITY)
		String getAgeInfectivityString() {
			return JOINER.join(ageInfectivity);
		}

		@StringSetter(AGE_INFECTIVITY)
		void setAgeInfectivity(String config) {
			Map<String, String> map = SPLITTER.split(config);
			setAgeInfectivity(map.entrySet().stream().collect(Collectors.toMap(
					e -> Integer.parseInt(e.getKey()), e -> Double.parseDouble(e.getValue())
			)));
		}

		/**
		 * Return infectivity for different age groups.
		 */
		public NavigableMap<Integer, Double> getAgeInfectivity() {
			return ageInfectivity;
		}

		/**
		 * Set infectivity for all age groups, previous entries will be overwritten.
		 */
		public void setAgeInfectivity(Map<Integer, Double> ageInfectivity) {
			this.ageInfectivity.clear();
			this.ageInfectivity.putAll(ageInfectivity);
		}

	}


}
