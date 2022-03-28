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
		private Map<EpisimPerson.ImmuneResponse, Double> immuneResponseMultiplier;

		public Config() {

			//initial antibodies
			Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new HashMap<>();

			for (VaccinationType immunityType : VaccinationType.values()) {
				initialAntibodies.put(immunityType, new EnumMap<>(VirusStrain.class));
				for (VirusStrain virusStrain : VirusStrain.values()) {

					if (immunityType == VaccinationType.mRNA) {
						initialAntibodies.get(immunityType).put(virusStrain, 29.2); //10.0
					} else if (immunityType == VaccinationType.vector) {
						initialAntibodies.get(immunityType).put(virusStrain, 6.8);  //2.5
					} else {
						initialAntibodies.get(immunityType).put(virusStrain, 5.0);
					}
				}
			}

			for (VirusStrain immunityType : VirusStrain.values()) {
				initialAntibodies.put(immunityType, new EnumMap<>(VirusStrain.class));
				for (VirusStrain virusStrain : VirusStrain.values()) {
					initialAntibodies.get(immunityType).put(virusStrain, 5.0);
				}
			}

			//mRNAAlpha, mRNADelta, mRNABA1 comes from Sydney's calibration.
			//The other values come from Rössler et al.

			//Wildtype
			double mRNAAlpha = 29.2;
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.SARS_CoV_2, mRNAAlpha);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.SARS_CoV_2, 0.01);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.SARS_CoV_2, 0.01);

			//Alpha
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.ALPHA, mRNAAlpha);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.ALPHA, 0.01);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.ALPHA, 0.01);

			//DELTA
			double mRNADelta = 10.9;
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, mRNADelta);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, mRNADelta * 150. / 300.);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, mRNADelta * 450. / 300.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 0.2 / 6.4);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 0.2 / 6.4);

			//BA.1
			double mRNABA1 = 1.9;
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, mRNABA1);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, mRNABA1 * 4. / 20.); //???
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 8. / 20.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 64.0 / 300.);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);


			//BA.2
			double mRNABA2 = mRNABA1 / 1.4;
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, mRNABA2);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, mRNABA2 * 4. / 20.);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 8. / 20.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);


			//refresh factors
			Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new HashMap<>();

			for (VaccinationType immunityType : VaccinationType.values()) {
				antibodyRefreshFactors.put(immunityType, new EnumMap<>(VirusStrain.class));
				for (VirusStrain virusStrain : VirusStrain.values()) {

					if (immunityType == VaccinationType.mRNA) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
					} else if (immunityType == VaccinationType.vector) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
					} else {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, Double.NaN);
					}

				}
			}

			for (VirusStrain immunityType : VirusStrain.values()) {
				antibodyRefreshFactors.put(immunityType, new EnumMap<>(VirusStrain.class));
				for (VirusStrain virusStrain : VirusStrain.values()) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				}
			}

			// immune response multiplier
			final Map<EpisimPerson.ImmuneResponse, Double> immuneResponseMultiplier = new HashMap<>();
			for (EpisimPerson.ImmuneResponse response : EpisimPerson.ImmuneResponse.values()) {
				immuneResponseMultiplier.put(response, 1.);
			}

			this.initialAntibodies = initialAntibodies;
			this.antibodyRefreshFactors = antibodyRefreshFactors;
			this.immuneResponseMultiplier = immuneResponseMultiplier;
		}

		public Config(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies, Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors) {
			this.initialAntibodies = initialAntibodies;
			this.antibodyRefreshFactors = antibodyRefreshFactors;

			final Map<EpisimPerson.ImmuneResponse, Double> immuneResponseMultiplier = new HashMap<>();
			for (EpisimPerson.ImmuneResponse response : EpisimPerson.ImmuneResponse.values()) {
				immuneResponseMultiplier.put(response, 1.);
			}
			this.immuneResponseMultiplier = immuneResponseMultiplier;
		}

		public Map<EpisimPerson.ImmuneResponse, Double> getImmuneResponseMultiplier() {
			return immuneResponseMultiplier;
		}

		public void setImmuneResponseMultiplier(Map<EpisimPerson.ImmuneResponse, Double> immuneResponseMultiplier) {
			this.immuneResponseMultiplier = immuneResponseMultiplier;
		}
	}

}
