package org.matsim.episim.model;

import com.google.inject.Inject;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;

import java.util.HashMap;
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

	private final double[] susceptibility = new double[128];
	private final double[] infectivity = new double[susceptibility.length];
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

		// pre-compute interpolated age dependent entries
		for (int i = 0; i < susceptibility.length; i++) {
			susceptibility[i] = EpisimUtils.interpolateEntry(episimConfig.getAgeSusceptibility(), i);
			infectivity[i] = EpisimUtils.interpolateEntry(episimConfig.getAgeInfectivity(), i);
		}
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

		final Map<VirusStrain, Double> AK50_PERSTRAIN = vaccinationConfig.getAk50PerStrain();

		//noinspection ConstantConditions 		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

		double susceptibility = this.susceptibility[target.getAge()];
		double infectivity = this.infectivity[infector.getAge()];

		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getParams(infector.getVirusStrain());

		double relativeAntibodyLevelTarget = target.getAntibodies(infector.getVirusStrain());

		double relativeAntibodyLevelInfector = infector.getAntibodies(infector.getVirusStrain());
		double indoorOutdoorFactor = InfectionModelWithSeasonality.getIndoorOutdoorFactor(outdoorFactor, rnd, act1, act2);
		double shedding = maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding;
		double intake = maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake;

		//reduced infectivity if infector has antibodies
		infectivity *= 1.0 - (0.25 * (1.0 - 1.0 / (1.0 + Math.pow(relativeAntibodyLevelInfector, vaccinationConfig.getBeta()))));

		// An infection always protects against further infections with the same variant for 3 months.
		for (int infection = 0; infection < target.getNumInfections(); infection++) {
 			if (target.getVirusStrain(infection) == infector.getVirusStrain() && target.daysSinceInfection(infection, iteration) <= 90) {
 				susceptibility = 0.0;
 				break;
 			}
 			if (vaccinationConfig.getBa1ba2ShortTermCrossImmunity()) {
 	 			if (target.getVirusStrain(infection) == VirusStrain.OMICRON_BA1 && infector.getVirusStrain() == VirusStrain.OMICRON_BA2 && target.daysSinceInfection(infection, iteration) <= 90) {
 	 				susceptibility = 0.0;
 	 				break;
 	 			}
 	 			if (target.getVirusStrain(infection) == VirusStrain.OMICRON_BA2 && infector.getVirusStrain() == VirusStrain.OMICRON_BA1 && target.daysSinceInfection(infection, iteration) <= 90) {
 	 				susceptibility = 0.0;
 	 				break;
 	 			}
 			}

 		}

		lastUnVac = calcInfectionProbabilityWoImmunity(target, infector, restrictions, act1, act2, contactIntensity, jointTimeInContainer, indoorOutdoorFactor, shedding, intake, infectivity, susceptibility, AK50_PERSTRAIN);
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
	                                            double indoorOutdoorFactor, double shedding, double intake, double infectivity, double susceptibility, Map<VirusStrain, Double> AK50_PERSTRAIN) {

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
