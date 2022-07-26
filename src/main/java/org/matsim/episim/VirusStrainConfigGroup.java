package org.matsim.episim;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.VirusStrain;

import java.util.EnumMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
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
	private final Map<VirusStrain, StrainParams> strains = new EnumMap<>(VirusStrain.class);

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
			p.strain = strain;
			addParameterSet(p);
			return p;
		}

		return strains.get(strain);
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
			strains.put(p.strain, p);
			super.addParameterSet(set);

		} else
			throw new IllegalStateException("Unknown set type " + set.getName());
	}

	/**
	 * Holds strain specific options.
	 */
	public static final class StrainParams extends ReflectiveConfigGroup {

		static final String SET_TYPE = "strainParams";

		private static final String STRAIN = "strain";
		private static final String INFECTIOUSNESS = "infectiousness";
		private static final String FACTOR_SERIOUSLY_SICK = "factorSeriouslySick";
		private static final String FACTOR_CRITICAL = "factorCritical";
		private static final String FACTOR_SERIOUSLY_SICK_VAC = "factorSeriouslySickVaccinated";
		private static final String AGE_SUSCEPTIBILITY = "ageSusceptibility";
		private static final String AGE_INFECTIVITY = "ageInfectivity";

		/**
		 * Type of the strain.
		 */
		private VirusStrain strain;

		/**
		 * v Infectiousness of this variant.
		 */
		private double infectiousness = 1.0;

		/**
		 * Factor for probability
		 */
		private double factorSeriouslySick = 1.0;

		/**
		 * Factor for critical probability.
		 */
		private double factorCritical = 1.0;

		/**
		 * Factor for probability when person is vaccinated
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
		public VirusStrain getStrain() {
			return strain;
		}

		@StringSetter(STRAIN)
		public void setStrain(VirusStrain strain) {
			this.strain = strain;
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
		public void setAgeInfectivity(Map<Integer, Double> ageSusceptibility) {
			this.ageInfectivity.clear();
			this.ageInfectivity.putAll(ageSusceptibility);
		}

	}


}
