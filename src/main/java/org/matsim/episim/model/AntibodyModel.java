package org.matsim.episim.model;

import org.matsim.episim.EpisimPerson;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Updates the antibody level of a person after each iteration.
 */
public interface AntibodyModel {

	/**
	 * Executed each day in order to update the antibody level of a person.
	 * @param person person to update.
	 * @param day current day / iteration
	 */
	void updateAntibodies(EpisimPerson person, int day);

	/**
	 * Create a new antibody config with default values.
	 */
	static AntibodyModel.Config newConfig() {
		return new Config();
	}

	/**
	 * Create a new antibody config with predefined values.
	 */
	static AntibodyModel.Config newConfig(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies, Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors) {
		return new Config(initialAntibodies, antibodyRefreshFactors);
	}

	/**
	 * Class for antibody model configurations.
	 */
	public static class Config {

		final Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies;
		final Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors;

		private Config() {

			//initial antibodies
			Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();

			for (VaccinationType immunityType : VaccinationType.values()) {
				initialAntibodies.put(immunityType, new EnumMap<>( VirusStrain.class ) );
				for (VirusStrain virusStrain : VirusStrain.values()) {

					if (immunityType == VaccinationType.mRNA) {
						initialAntibodies.get(immunityType).put(virusStrain, 29.2); //10.0
					}
					else if (immunityType == VaccinationType.vector) {
						initialAntibodies.get(immunityType).put(virusStrain, 6.8);  //2.5
					}
					else {
						initialAntibodies.get(immunityType).put(virusStrain, 5.0);
					}
				}
			}

			for (VirusStrain immunityType : VirusStrain.values()) {
				initialAntibodies.put(immunityType, new EnumMap<>( VirusStrain.class ) );
				for (VirusStrain virusStrain : VirusStrain.values()) {
					initialAntibodies.get(immunityType).put(virusStrain, 5.0);
				}
			}

			//DELTA
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, 10.9); //4.0
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, 6.8); //1.0
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, 2.0);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, 2.0);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, 5.0);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 2.0);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 2.0);

			//BA.1
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, 1.9); //0.8
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, 1.5); //0.2
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, 0.01);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, 0.01);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, 0.2 / 6.4);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 0.2);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 0.2 / 1.4);

			//BA.2
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, 0.8 / 1.4);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, 0.2 / 1.4);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, 0.01);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, 0.01);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, 0.2 / 6.4);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 0.2 / 1.4);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 0.2);


			//refresh factors
			Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();

			for (VaccinationType immunityType : VaccinationType.values()) {
				antibodyRefreshFactors.put(immunityType, new EnumMap<>( VirusStrain.class ) );
				for (VirusStrain virusStrain : VirusStrain.values()) {

					if (immunityType == VaccinationType.mRNA) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
					}
					else if (immunityType == VaccinationType.vector) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
					}
					else {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 10.0);
					}

				}
			}

			for (VirusStrain immunityType : VirusStrain.values()) {
				antibodyRefreshFactors.put(immunityType, new EnumMap<>( VirusStrain.class ) );
				for (VirusStrain virusStrain : VirusStrain.values()) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 10.0);
				}
			}

			this.initialAntibodies = initialAntibodies;
			this.antibodyRefreshFactors = antibodyRefreshFactors;
		}

		private Config(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies, Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors) {
			this.initialAntibodies = initialAntibodies;
			this.antibodyRefreshFactors = antibodyRefreshFactors;
		}

	}

}
