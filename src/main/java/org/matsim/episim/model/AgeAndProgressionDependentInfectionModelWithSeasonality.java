package org.matsim.episim.model;

import com.google.inject.Inject;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;

import java.util.Map;
import java.util.SplittableRandom;

import static org.matsim.episim.model.DefaultInfectionModel.*;

/**
 * Extension of the {@link DefaultInfectionModel}, with age, time and seasonality-dependen additions.
 */
public final class AgeAndProgressionDependentInfectionModelWithSeasonality implements InfectionModel {

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
	AgeAndProgressionDependentInfectionModelWithSeasonality(FaceMaskModel faceMaskModel, ProgressionModel progression,
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

		//noinspection ConstantConditions 		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

		double susceptibility = this.susceptibility[target.getAge()];
		double infectivity = this.infectivity[infector.getAge()];

		// apply reduced susceptibility of vaccinated persons
		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getParams(infector.getVirusStrain());
		susceptibility *= Math.min(getVaccinationEffectiveness(strain, target, vaccinationConfig, iteration), getImmunityEffectiveness(strain, target, vaccinationConfig, iteration));

		double indoorOutdoorFactor = InfectionModelWithSeasonality.getIndoorOutdoorFactor(outdoorFactor, rnd, act1, act2);
		double shedding = maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding;
		double intake = maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake;

		lastUnVac = calcUnVacInfectionProbability(target, infector, restrictions, act1, act2, contactIntensity, jointTimeInContainer, indoorOutdoorFactor, shedding, intake);

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * susceptibility * infectivity * contactIntensity * jointTimeInContainer * ciCorrection
				* DefaultInfectionModel.getInfectivity(infector, strain, vaccinationConfig, iteration)
				* target.getSusceptibility()
				* getInfectivity(infector)
				* strain.getInfectiousness()
				* shedding
				* intake
				* indoorOutdoorFactor
		);
	}

	private double calcUnVacInfectionProbability(EpisimPerson target, EpisimPerson infector, Map<String, Restriction> restrictions, EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2, double contactIntensity, double jointTimeInContainer,
	                                            double indoorOutdoorFactor, double shedding, double intake) {
		//noinspection ConstantConditions 		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

		double susceptibility = this.susceptibility[target.getAge()];
		double infectivity = this.infectivity[infector.getAge()];

		// apply reduced susceptibility of vaccinated persons
		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getParams(infector.getVirusStrain());
		// vac is reduced from this term
		susceptibility *= getImmunityEffectiveness(strain, target, vaccinationConfig, iteration);

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * susceptibility * infectivity * contactIntensity * jointTimeInContainer * ciCorrection
				* DefaultInfectionModel.getInfectivity(infector, strain, vaccinationConfig, iteration)
				* target.getSusceptibility()
				* getInfectivity(infector)
				* strain.getInfectiousness()
				* shedding
				* intake
				* indoorOutdoorFactor
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
