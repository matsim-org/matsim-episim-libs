package org.matsim.episim.model;

import com.google.inject.Inject;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Extension of the {@link DefaultInfectionModel}, with age, time and seasonality-dependen additions.
 */
public final class InfectionModelWithAntibodies implements InfectionModel {

	private final FaceMaskModel maskModel;
	private final ProgressionModel progression;
	private final EpisimConfigGroup episimConfig;
	private final EpisimReporting reporting;
	private final SplittableRandom rnd;
	private final VaccinationConfigGroup vaccinationConfig;
	private final VirusStrainConfigGroup virusStrainConfig;

	private final Map<VirusStrain, double[]> susceptibility = new EnumMap<>(VirusStrain.class);
	private final Map<VirusStrain, double[]> infectivity = new EnumMap<>(VirusStrain.class);
	private final RealDistribution distribution;

	/**
	 * Scale infectivity to 1.0
	 */
	private final double scale;

	private double outdoorFactor;
	private int iteration;
	private double lastUnVac;

	@Inject
	InfectionModelWithAntibodies(FaceMaskModel faceMaskModel, ProgressionModel progression,
															Config config, EpisimReporting reporting, SplittableRandom rnd) {
		this.maskModel = faceMaskModel;
		this.progression = progression;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		this.virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		this.reporting = reporting;
		this.rnd = rnd;

		AgeDependentInfectionModelWithSeasonality.preComputeAgeDependency(susceptibility, infectivity, virusStrainConfig);

		// based on https://arxiv.org/abs/2007.06602
		distribution = new NormalDistribution(0.5, 2.6);
		scale = 1 / distribution.density(distribution.getNumericalMean());
	}

	@Override
	public void setIteration(int iteration) {
		this.outdoorFactor = InfectionModelWithSeasonality.interpolateOutdoorFraction(episimConfig, iteration);
		this.iteration = iteration;
		reporting.reportOutdoorFraction(this.outdoorFactor, iteration);

	}

	@Override
	public double getLastUnVacInfectionProbability() {
		return lastUnVac;
	}

	@Override
	public double calcInfectionProbability(EpisimPerson target, EpisimPerson infector, Map<String, Restriction> restrictions,
										   EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2,
										   double contactIntensity, double jointTimeInContainer) {

		//noinspection ConstantConditions 		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

		double susceptibility = this.susceptibility.get(infector.getVirusStrain())[target.getAge()];
		double infectivity = this.infectivity.get(infector.getVirusStrain())[infector.getAge()];

		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getParams(infector.getVirusStrain());

		double relativeAntibodyLevelTarget = target.getAntibodies(infector.getVirusStrain());

		double indoorOutdoorFactor = InfectionModelWithSeasonality.getIndoorOutdoorFactor(outdoorFactor, rnd, act1, act2);
		double shedding = maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding;
		double intake = maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake;

		//reduced infectivity if infector has antibodies
		double immunityFactorInfector = 1.0 / (1.0 + Math.pow(infector.getAntibodyLevelAtInfection(), vaccinationConfig.getBeta()));
		infectivity *= (1.0 - (0.25 * (1.0 - immunityFactorInfector)));

		{
			double igaFactor = 0.0;

			double igaTimePeriod = vaccinationConfig.getTimePeriodIgA();
			if (target.hadStrain(infector.getVirusStrain())) {

				int lastInfectionWithStrain = 0;
				for (int ii = 0; ii < target.getNumInfections();  ii++) {
					if (target.getVirusStrain(ii) == infector.getVirusStrain()) {
						lastInfectionWithStrain = ii;
					}
				}
//				igaFactor = Math.exp( - target.daysSinceInfection(lastInfectionWithStrain, iteration) / 120.0);
				igaFactor = 1.0 / (1.0 + Math.exp(-2.0 * (1.0 - target.daysSinceInfection(lastInfectionWithStrain, iteration) / igaTimePeriod)));

			} else if (vaccinationConfig.getUseIgA()) {
				List<VirusStrain> crossImmunityStrainsOmicron = List.of(VirusStrain.OMICRON_BA1,VirusStrain.OMICRON_BA2,VirusStrain.OMICRON_BA5,VirusStrain.STRAIN_A);
				List<VirusStrain> crossImmunityStrainsDelta = List.of(VirusStrain.DELTA, VirusStrain.STRAIN_B);


				if(crossImmunityStrainsOmicron.contains(infector.getVirusStrain())){
					int lastInfectionWithStrain = 0;
					boolean targetHadStrain = false;
					for (int ii = 0; ii < target.getNumInfections();  ii++) {
						if (crossImmunityStrainsOmicron.contains(target.getVirusStrain(ii))){
							targetHadStrain = true;
							lastInfectionWithStrain = ii;
						}
					}

					if (targetHadStrain) {
						double fac = 1.0 / (1.0 + Math.exp(-2.0 * (1.0 - target.daysSinceInfection(lastInfectionWithStrain, iteration) / igaTimePeriod)));
						fac = fac / 1.4;
						igaFactor = Math.max(fac, igaFactor);
					}
				} else if(crossImmunityStrainsDelta.contains(infector.getVirusStrain())) {
					int lastInfectionWithStrain = 0;
					boolean targetHadStrain = false;
					for (int ii = 0; ii < target.getNumInfections(); ii++) {
						if (crossImmunityStrainsDelta.contains(target.getVirusStrain(ii))) {
							targetHadStrain = true;
							lastInfectionWithStrain = ii;
						}
					}

					if (targetHadStrain) {
						double fac = 1.0 / (1.0 + Math.exp(-2.0 * (1.0 - target.daysSinceInfection(lastInfectionWithStrain, iteration) / igaTimePeriod)));
						fac = fac / 1.4;
						igaFactor = Math.max(fac, igaFactor);
					}
				}
			}
			susceptibility = susceptibility * (1.0 - igaFactor);
		}



		lastUnVac = calcInfectionProbabilityWoImmunity(target, infector, restrictions, act1, act2, contactIntensity, jointTimeInContainer, indoorOutdoorFactor, shedding, intake, infectivity, susceptibility);
		double immunityFactor = 1.0 / (1.0 + Math.pow(relativeAntibodyLevelTarget, vaccinationConfig.getBeta()));

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * susceptibility * infectivity * contactIntensity * jointTimeInContainer * ciCorrection
				* target.getSusceptibility()
				* getInfectivity(infector)
				* strain.getInfectiousness()
				* shedding
				* intake
				* indoorOutdoorFactor
				* immunityFactor
		);
	}

	private double calcInfectionProbabilityWoImmunity(EpisimPerson target, EpisimPerson infector, Map<String, Restriction> restrictions, EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2, double contactIntensity, double jointTimeInContainer,
	                                            double indoorOutdoorFactor, double shedding, double intake, double infectivity, double susceptibility) {

		//noinspection ConstantConditions 		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getParams(infector.getVirusStrain());

		double relativeAntibodyLevel = 0.0;

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * susceptibility * infectivity * contactIntensity * jointTimeInContainer * ciCorrection
				* target.getSusceptibility()
				* getInfectivity(infector)
				* strain.getInfectiousness()
				* shedding
				* intake
				* indoorOutdoorFactor
				/ (1.0 + Math.pow(relativeAntibodyLevel, vaccinationConfig.getBeta()))

		);
	}

	/**
	 * Calculates infectivity of infector depending on disease progression.
	 *
	 * @apiNote package private for testing
	 */
	double getInfectivity(EpisimPerson infector) {

		if (infector.getDiseaseStatus() == EpisimPerson.DiseaseStatus.showingSymptoms) {

			int afterSymptomOnset = infector.daysSince(EpisimPerson.DiseaseStatus.showingSymptoms, iteration);
			return distribution.density(afterSymptomOnset) * scale;
		} else if (infector.getDiseaseStatus() == EpisimPerson.DiseaseStatus.contagious) {

			EpisimPerson.DiseaseStatus nextDiseaseStatus = progression.getNextDiseaseStatus(infector.getPersonId());
			int transitionDays = progression.getNextTransitionDays(infector.getPersonId());
			int daysSince = infector.daysSince(infector.getDiseaseStatus(), iteration);
			if (nextDiseaseStatus == EpisimPerson.DiseaseStatus.showingSymptoms) {

				return distribution.density(transitionDays - daysSince) * scale;

			} else if (nextDiseaseStatus == EpisimPerson.DiseaseStatus.recovered) {

				// when next state is recovered the half of the interval is used
				return distribution.density(daysSince - transitionDays / 2.0) * scale;
			}
		}


		return 0.0;
	}

	public static void main(String[] args) {
		// test distribution
		NormalDistribution dist = new NormalDistribution(0.5, 2.6);

		for(int i = -5; i <= 10; i++) {
			System.out.println(i + " " + dist.density(i));
		}

	}
}
