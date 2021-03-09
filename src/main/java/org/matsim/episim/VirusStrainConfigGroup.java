package org.matsim.episim;

import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.episim.model.VirusStrain;

import java.util.EnumMap;
import java.util.Map;

/**
 * Config option specific to {@link org.matsim.episim.model.VirusStrain}.
 */
public class VirusStrainConfigGroup extends ReflectiveConfigGroup {

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
		private static final String VACCINE_EFFECTIVENESS = "vaccineEffectiveness";

		/**
		 * Type of the strain.
		 */
		private VirusStrain strain;

		/**
		 * v Infectiousness of this variant.
		 */
		private double infectiousness = 1.0;

		/**
		 * Vaccine effectiveness
		 */
		private double vaccineEffectiveness = 1.0;

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

		@StringGetter(VACCINE_EFFECTIVENESS)
		public double getVaccineEffectiveness() {
			return vaccineEffectiveness;
		}

		@StringSetter(VACCINE_EFFECTIVENESS)
		public void setVaccineEffectiveness(double vaccineEffectiveness) {
			this.vaccineEffectiveness = vaccineEffectiveness;
		}
	}
}
