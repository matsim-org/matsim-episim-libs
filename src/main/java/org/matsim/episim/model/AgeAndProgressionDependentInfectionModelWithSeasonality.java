package org.matsim.episim.model;

import com.google.inject.Inject;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.matsim.episim.util.EpisimSplittableRandom;

import static org.matsim.episim.model.DefaultInfectionModel.*;

/**
 * Extension of the {@link DefaultInfectionModel}, with age, time and seasonality-dependen additions.
 */
public final class AgeAndProgressionDependentInfectionModelWithSeasonality implements InfectionModel {

	private final FaceMaskModel maskModel;
	private final ProgressionModel progression;
	private final EpisimConfigGroup episimConfig;
	private final EpisimReporting reporting;
	private final EpisimSplittableRandom rnd;
	private final VaccinationConfigGroup vaccinationConfig;
	private final VirusStrainConfigGroup virusStrainConfig;
	private final PathogenConfigGroup pathogenConfig;

	private final Map<VirusStrain, double[]> susceptibility;// = new EnumMap<>(VirusStrain.class);
	private final Map<VirusStrain, double[]> infectivity;// = new EnumMap<>(VirusStrain.class);
	private final RealDistribution distribution;

	/**
	 * Scale infectivity to 1.0.
	 */
	private final double scale;

	private double outdoorFactor;
	private int iteration;
	private final ImmunityModel immunityModel;
	private double lastUnVac;

	@Inject
	AgeAndProgressionDependentInfectionModelWithSeasonality(FaceMaskModel faceMaskModel, ProgressionModel progression,
															ImmunityModel immunityModel, Config config,
															EpisimReporting reporting, EpisimSplittableRandom rnd) {
		this.maskModel = faceMaskModel;
		this.immunityModel = immunityModel;
		this.progression = progression;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		this.virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		this.pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);
		this.reporting = reporting;
		this.rnd = rnd;
		this.susceptibility = new HashMap<>();
		this.infectivity = new HashMap<>();


		AgeDependentInfectionModelWithSeasonality.preComputeAgeDependency(susceptibility, infectivity, virusStrainConfig, virusStrainConfig.getVirusStrains());

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
										   TransmissionWeights transmissionWeights,
										   double contactIntensity, double jointTimeInContainer) {

		//noinspection ConstantConditions 		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

		double susceptibility = this.susceptibility.get(infector.getVirusStrain())[target.getAge()];
		double infectivity = this.infectivity.get(infector.getVirusStrain())[infector.getAge()];

		// apply reduced susceptibility of vaccinated persons
		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getParams(infector.getVirusStrain());
		susceptibility *= immunityModel.getFactor(target, strain.getStrain(),
			EpisimPerson.DiseaseStatus.infectedButNotContagious, iteration);

		double indoorOutdoorFactor = InfectionModelWithSeasonality.getIndoorOutdoorFactor(outdoorFactor, rnd, act1, act2);
		double shedding = maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding;
		double intake = maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake;

		lastUnVac = calcUnVacInfectionProbability(target, infector, restrictions, act1, act2, transmissionWeights, contactIntensity, jointTimeInContainer, indoorOutdoorFactor, shedding, intake);

		// route-agnostic factors
		double base = episimConfig.getCalibrationParameter() * susceptibility * infectivity * contactIntensity * jointTimeInContainer
				* immunityModel.getInfectivityFactor(infector, strain.getStrain(), iteration)
				* target.getSusceptibility()
				* getInfectivity(infector)
				* strain.getInfectiousness();

		// respiratory route only: ventilation / contact-intensity correction, face masks, indoor-outdoor dilution
		double respiratoryModifier = ciCorrection * shedding * intake * indoorOutdoorFactor;

		// contact-side route split combined with the pathogen's per-route transmissibility
		TransmissionWeights pathogenWeights = pathogenConfig.getParams(strain.getPathogen()).getRouteTransmissibility();
		double viaRespiratory = transmissionWeights.getRespiratory() * pathogenWeights.getRespiratory() * respiratoryModifier;
		double viaDirectContact = transmissionWeights.getDirectContact() * pathogenWeights.getDirectContact();
		// fomite: double viaFomite = transmissionWeights.getFomite() * pathogenWeights.getFomite();

		return 1 - Math.exp(-base * (viaRespiratory + viaDirectContact /* fomite: + viaFomite */));
	}

	/**
	 * The probability the target would have had without their vaccination, reported for vaccinated persons only
	 * (see {@code AbstractContactModel.potentialInfection}) as a vaccine-effectiveness diagnostic. It deliberately
	 * still uses the static helpers: asking an {@link ImmunityModel} for "immunity without the vaccinations" would
	 * mean exposing which source a factor came from, which is exactly what that interface hides. Note that
	 * {@code InfectionModelWithAntibodies} already reads the same quantity as "without any immunity", so the two
	 * never agreed on what it means.
	 */
	private double calcUnVacInfectionProbability(EpisimPerson target, EpisimPerson infector, Map<String, Restriction> restrictions, EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2, TransmissionWeights transmissionWeights, double contactIntensity, double jointTimeInContainer,
		double indoorOutdoorFactor, double shedding, double intake) {
		//noinspection ConstantConditions 		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

		double susceptibility = this.susceptibility.get(infector.getVirusStrain())[target.getAge()];
		double infectivity = this.infectivity.get(infector.getVirusStrain())[infector.getAge()];

		// apply reduced susceptibility of vaccinated persons
		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getParams(infector.getVirusStrain());
		// vac is reduced from this term
		susceptibility *= getImmunityEffectiveness(strain, target, vaccinationConfig, iteration);

		// route-agnostic factors
		double base = episimConfig.getCalibrationParameter() * susceptibility * infectivity * contactIntensity * jointTimeInContainer
				* DefaultInfectionModel.getInfectivity(infector, strain, vaccinationConfig, iteration)
				* target.getSusceptibility()
				* getInfectivity(infector)
				* strain.getInfectiousness();

		// respiratory route only: ventilation / contact-intensity correction, face masks, indoor-outdoor dilution
		double respiratoryModifier = ciCorrection * shedding * intake * indoorOutdoorFactor;

		// contact-side route split combined with the pathogen's per-route transmissibility
		TransmissionWeights pathogenWeights = pathogenConfig.getParams(strain.getPathogen()).getRouteTransmissibility();
		double viaRespiratory = transmissionWeights.getRespiratory() * pathogenWeights.getRespiratory() * respiratoryModifier;
		double viaDirectContact = transmissionWeights.getDirectContact() * pathogenWeights.getDirectContact();
		// fomite: double viaFomite = transmissionWeights.getFomite() * pathogenWeights.getFomite();

		return 1 - Math.exp(-base * (viaRespiratory + viaDirectContact /* fomite: + viaFomite */));
	}

	/**
	 * Calculates infectivity of infector depending on disease progression.
	 *
	 * @apiNote package private for testing
	 */
	double getInfectivity(EpisimPerson infector) {

		if (infector.getDiseaseStatus() == EpisimPerson.DiseaseStatus.showingSymptoms) {

			int afterSymptomOnset = infector.daysSince(EpisimPerson.DiseaseStatus.showingSymptoms, iteration);
			PathogenConfigGroup.PathogenParams pathogen = pathogenOf(infector);
			return pathogen.hasInfectivityProfile() ? pathogen.getInfectivity(afterSymptomOnset)
					: distribution.density(afterSymptomOnset) * scale;
		} else if (infector.getDiseaseStatus() == EpisimPerson.DiseaseStatus.contagious) {

			EpisimPerson.DiseaseStatus nextDiseaseStatus = progression.getNextDiseaseStatus(infector.getPersonId());
			int transitionDays = progression.getNextTransitionDays(infector.getPersonId());
			int daysSince = infector.daysSince(infector.getDiseaseStatus(), iteration);
			if (nextDiseaseStatus == EpisimPerson.DiseaseStatus.showingSymptoms) {

				int untilOnset = transitionDays - daysSince;
				// the built-in curve is read at the distance to onset, i.e. mirrored; a configured profile is signed
				PathogenConfigGroup.PathogenParams pathogen = pathogenOf(infector);
				return pathogen.hasInfectivityProfile() ? pathogen.getInfectivity(-untilOnset)
						: distribution.density(untilOnset) * scale;

			} else if (nextDiseaseStatus == EpisimPerson.DiseaseStatus.recovered) {

				// when next state is recovered the half of the interval is used
				double sinceMidpoint = daysSince - transitionDays / 2.0;
				PathogenConfigGroup.PathogenParams pathogen = pathogenOf(infector);
				return pathogen.hasInfectivityProfile() ? pathogen.getInfectivity(sinceMidpoint)
						: distribution.density(sinceMidpoint) * scale;
			}
		}


		return 0.0;
	}

	private PathogenConfigGroup.PathogenParams pathogenOf(EpisimPerson infector) {
		return pathogenConfig.getParams(virusStrainConfig.getParams(infector.getVirusStrain()).getPathogen());
	}

	public static void main(String[] args) {
		// test distribution
		NormalDistribution dist = new NormalDistribution(0.5, 2.6);

		for(int i = -5; i <= 10; i++) {
			System.out.println(i + " " + dist.density(i));
		}

	}
}
