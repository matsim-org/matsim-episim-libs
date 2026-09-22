package org.matsim.episim.model;

import org.matsim.episim.EpisimPerson;

import java.util.Collection;
import java.util.LinkedHashMap;
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
	 * Initialize antibody model.
	 * @param persons persons whose antibodies are initialized
	 * @param iteration current simulation iteration
	 */
	void init(Collection<EpisimPerson> persons, int iteration);

	/**
	 * Recalculates antibody levels after loading a snapshot.
	 */
	void recalculateAntibodiesAfterSnapshot(Collection<EpisimPerson> persons, int iteration);

	/**
	 * Parameters of {@link DefaultAntibodyModel}.
	 *
	 * <p>Deliberately not a MATSim config group. The values are calibrated for SARS-CoV-2 and carry no source, so
	 * they are not meant to be set per run in a config file; an immunity model for other pathogens does not use
	 * them. It is bound in Guice, and a scenario that needs different values binds its own instance, as before
	 * this was briefly a config group.</p>
	 *
	 * <p>An immunity event without an entry induces no antibodies and does not refresh existing ones. This is what
	 * lets a pathogen without antibody parameters run at all, without having to configure zeros for it.</p>
	 */
	final class Config {

		private final Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies;
		private final Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors;

		/**
		 * Sigma of the log-normal distribution of a person's individual immune-response multiplier; {@code 0}
		 * means every person reacts identically.
		 */
		private double immuneResponseSigma = 0.;

		/**
		 * Half-life multiplier for persons whose immunity comes from both infection and vaccination, or from many
		 * events.
		 */
		private double hlMultiplierForInfected = 1.0;

		/**
		 * Default values, calibrated for SARS-CoV-2.
		 */
		public Config() {
			this(defaultInitialAntibodies(), defaultAntibodyRefreshFactors());
		}

		public Config(Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies,
					  Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors) {
			this.initialAntibodies = initialAntibodies;
			this.antibodyRefreshFactors = antibodyRefreshFactors;
		}

		/**
		 * Antibody level against each strain right after this immunity event; empty if it induces none.
		 */
		public Map<VirusStrain, Double> getInitialAntibodies(ImmunityEvent immunityEvent) {
			return initialAntibodies.getOrDefault(immunityEvent, Map.of());
		}

		public void setInitialAntibodies(ImmunityEvent immunityEvent, Map<VirusStrain, Double> antibodies) {
			initialAntibodies.put(immunityEvent, new LinkedHashMap<>(antibodies));
		}

		/**
		 * Factor by which an existing level against each strain is refreshed when this event happens again;
		 * empty if it refreshes nothing.
		 */
		public Map<VirusStrain, Double> getAntibodyRefreshFactors(ImmunityEvent immunityEvent) {
			return antibodyRefreshFactors.getOrDefault(immunityEvent, Map.of());
		}

		public void setAntibodyRefreshFactors(ImmunityEvent immunityEvent, Map<VirusStrain, Double> factors) {
			antibodyRefreshFactors.put(immunityEvent, new LinkedHashMap<>(factors));
		}

		public double getImmuneResponseSigma() {
			return immuneResponseSigma;
		}

		public void setImmuneResponseSigma(double immuneResponseSigma) {
			this.immuneResponseSigma = immuneResponseSigma;
		}

		public double getHlMultiplierForInfected() {
			return hlMultiplierForInfected;
		}

		public void setHlMultiplierForInfected(double hlMultiplierForInfected) {
			this.hlMultiplierForInfected = hlMultiplierForInfected;
		}

		private static Map<ImmunityEvent, Map<VirusStrain, Double>> defaultInitialAntibodies() {

			Map<ImmunityEvent, Map<VirusStrain, Double>> initialAntibodies = new LinkedHashMap<>();

			for (VaccinationType immunityType : VaccinationType.getAllStandardOptions()) {
				initialAntibodies.put(immunityType, new LinkedHashMap<>());
				for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {

					if (immunityType == VaccinationType.mRNA) {
						initialAntibodies.get(immunityType).put(virusStrain, 29.2); //10.0
					} else if (immunityType == VaccinationType.vector) {
						initialAntibodies.get(immunityType).put(virusStrain, 6.8);  //2.5
					} else {
						initialAntibodies.get(immunityType).put(virusStrain, 5.0);
					}
				}
			}

			for (VirusStrain immunityType : VirusStrain.getAllStandardOptions()) {
				initialAntibodies.put(immunityType, new LinkedHashMap<>());
				for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {
					initialAntibodies.get(immunityType).put(virusStrain, 5.0);
				}
			}

			//mRNAAlpha, mRNADelta, mRNABA1 comes from Sydney's calibration.
			//The other values come from Rössler et al.

			double mutEscDelta = 29.2 / 10.9;
			double mutEscBa1 = 10.9 / 1.9;
			double mutEscBa5 = 2.9;

			//Wildtype
			double mRNAAlpha = 29.2;
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.SARS_CoV_2, mRNAAlpha);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 300. / 700.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.SARS_CoV_2, mRNAAlpha * 210. / 700.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.SARS_CoV_2, 0.01);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.SARS_CoV_2, 0.01);
			initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.SARS_CoV_2, 0.01);
			initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.SARS_CoV_2, mRNAAlpha / mutEscDelta / mutEscBa1);
			initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.SARS_CoV_2, mRNAAlpha / mutEscDelta / mutEscBa1 / mutEscBa5);

			//Alpha
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.ALPHA, mRNAAlpha);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.ALPHA, mRNAAlpha * 300. / 700.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.ALPHA, mRNAAlpha * 210. / 700.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.ALPHA, 0.01);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.ALPHA, 0.01);
			initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.ALPHA, 0.01);
			initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.ALPHA, mRNAAlpha / mutEscDelta / mutEscBa1);
			initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.ALPHA, mRNAAlpha / mutEscDelta / mutEscBa1 / mutEscBa5);

			//DELTA
			double mRNADelta = mRNAAlpha / mutEscDelta;
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.DELTA, mRNADelta);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.DELTA, mRNADelta * 150. / 300.);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.DELTA, mRNADelta * 64. / 300.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.DELTA, mRNADelta * 450. / 300.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.DELTA, 0.01);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.DELTA, 0.01);
			initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.DELTA, 0.01);
			initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.DELTA, mRNADelta / mutEscBa1);
			initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.DELTA, mRNADelta / mutEscBa1 / mutEscBa5);

			//BA.1
			double mRNABA1 = mRNADelta / mutEscBa1;
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA1, mRNABA1);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA1, mRNABA1 * 4. / 20.); //???
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 6. / 20.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA1, mRNABA1 * 8. / 20.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA1, 64.0 / 300.);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / 1.4);
			initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA1, 64.0 / 300. / mutEscBa5); //todo: is 1.4
			initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA1, mRNAAlpha);
			initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA1, mRNAAlpha / mutEscBa5);

			//BA.2
			double mRNABA2 = mRNABA1;
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA2, mRNABA2);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA2, mRNABA2 * 4. / 20.);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 6. / 20.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA2, mRNABA2 * 8. / 20.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / 1.4);
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA2, 64.0 / 300.);
			initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA2, 64.0 / 300. / mutEscBa5);
			initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA2, mRNAAlpha);
			initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA2, mRNAAlpha / mutEscBa5);

			//BA.5
			double mRNABa5 = mRNABA2 / mutEscBa5;
			initialAntibodies.get(VaccinationType.mRNA).put(VirusStrain.OMICRON_BA5, mRNABa5);
			initialAntibodies.get(VaccinationType.vector).put(VirusStrain.OMICRON_BA5, mRNABa5 * 4. / 20.);
			initialAntibodies.get(VirusStrain.SARS_CoV_2).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6. / 20.);
			initialAntibodies.get(VirusStrain.ALPHA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 6. / 20.);
			initialAntibodies.get(VirusStrain.DELTA).put(VirusStrain.OMICRON_BA5, mRNABa5 * 8. / 20.);
			initialAntibodies.get(VirusStrain.OMICRON_BA1).put(VirusStrain.OMICRON_BA5, 64.0 / 300. / mutEscBa5); // todo: do we need 1.4?
			initialAntibodies.get(VirusStrain.OMICRON_BA2).put(VirusStrain.OMICRON_BA5, 64.0 / 300. / mutEscBa5);
			initialAntibodies.get(VirusStrain.OMICRON_BA5).put(VirusStrain.OMICRON_BA5, 64.0 / 300.);
			initialAntibodies.get(VaccinationType.ba1Update).put(VirusStrain.OMICRON_BA5, mRNAAlpha / mutEscBa5);
			initialAntibodies.get(VaccinationType.ba5Update).put(VirusStrain.OMICRON_BA5, mRNAAlpha);

			return initialAntibodies;
		}

		/**
		 * Default refresh factors, calibrated for SARS-CoV-2.
		 */
		private static Map<ImmunityEvent, Map<VirusStrain, Double>> defaultAntibodyRefreshFactors() {

			Map<ImmunityEvent, Map<VirusStrain, Double>> antibodyRefreshFactors = new LinkedHashMap<>();

			for (VaccinationType immunityType : VaccinationType.getAllStandardOptions()) {
				antibodyRefreshFactors.put(immunityType, new LinkedHashMap<>());
				for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {

					if (immunityType == VaccinationType.mRNA) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
					} else if (immunityType == VaccinationType.vector) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 5.0);
					} else if (immunityType == VaccinationType.ba1Update) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
					} else if (immunityType == VaccinationType.ba5Update) {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
					} else {
						antibodyRefreshFactors.get(immunityType).put(virusStrain, Double.NaN);
					}

				}
			}

			for (VirusStrain immunityType : VirusStrain.getAllStandardOptions()) {
				antibodyRefreshFactors.put(immunityType, new LinkedHashMap<>());
				for (VirusStrain virusStrain : VirusStrain.getAllStandardOptions()) {
					antibodyRefreshFactors.get(immunityType).put(virusStrain, 15.0);
				}
			}

			return antibodyRefreshFactors;
		}
	}
}
