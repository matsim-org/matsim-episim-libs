package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;

import java.util.*;

import org.matsim.episim.util.EpisimSplittableRandom;

import static org.matsim.episim.model.DefaultInfectionModel.*;

/**
 * Extension of the {@link DefaultInfectionModel}, with age-dependent additions.
 */
public final class AgeDependentInfectionModelWithSeasonality implements InfectionModel {

	private final FaceMaskModel maskModel;
	private final EpisimConfigGroup episimConfig;
	private final EpisimReporting reporting;
	private final EpisimSplittableRandom rnd;
	private final VaccinationConfigGroup vaccinationConfig;
	private final VirusStrainConfigGroup virusStrainConfig;
	private final PathogenConfigGroup pathogenConfig;

	private final Map<VirusStrain, double[]> susceptibility; //= new EnumMap<>(VirusStrain.class);
	private final Map<VirusStrain, double[]> infectivity; //= new EnumMap<>(VirusStrain.class);

	private double outdoorFactor;
	private int iteration;

	@Inject
	AgeDependentInfectionModelWithSeasonality(FaceMaskModel faceMaskModel, Config config, EpisimReporting reporting, EpisimSplittableRandom rnd) {
		this.maskModel = faceMaskModel;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		this.virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		this.pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);
		this.reporting = reporting;
		this.rnd = rnd;
		this.susceptibility = new HashMap<>();
		this.infectivity = new HashMap<>();
		preComputeAgeDependency(susceptibility, infectivity, virusStrainConfig, virusStrainConfig.getVirusStrains());
	}

	/**
	 * Pre-compute interpolated age dependent entries.
	 */
	static void preComputeAgeDependency(Map<VirusStrain, double[]> susceptibility, Map<VirusStrain, double[]> infectivity, VirusStrainConfigGroup virusStrainConfig, Collection<VirusStrain> virusStrains) {

		for (VirusStrain strain : virusStrains) {

			if (!virusStrainConfig.hasParams(strain))
				continue;

			double[] susp = susceptibility.computeIfAbsent(strain, k -> new double[128]);
			double[] inf = infectivity.computeIfAbsent(strain, k -> new double[susp.length]);

			VirusStrainConfigGroup.StrainParams strainConfig = virusStrainConfig.getParams(strain);

			for (int i = 0; i < susp.length; i++) {
				susp[i] = EpisimUtils.interpolateEntry(strainConfig.getAgeSusceptibility(), i);
				inf[i] = EpisimUtils.interpolateEntry(strainConfig.getAgeInfectivity(), i);
			}
		}
	}

	@Override
	public void setIteration(int iteration) {
		this.outdoorFactor = InfectionModelWithSeasonality.interpolateOutdoorFraction(episimConfig, iteration);
		this.iteration = iteration;
		reporting.reportOutdoorFraction(this.outdoorFactor, iteration);

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
		VirusStrainConfigGroup.StrainParams params = virusStrainConfig.getParams(infector.getVirusStrain());
		susceptibility *= Math.min(getVaccinationEffectiveness(params, target, vaccinationConfig, iteration), getImmunityEffectiveness(params, target, vaccinationConfig, iteration));

		double indoorOutdoorFactor = InfectionModelWithSeasonality.getIndoorOutdoorFactor(outdoorFactor, rnd, act1, act2);

		// route-agnostic factors
		double base = episimConfig.getCalibrationParameter() * susceptibility * infectivity * contactIntensity * jointTimeInContainer
				* getVaccinationInfectivity(infector, params, vaccinationConfig, iteration)
				* target.getSusceptibility()
				* params.getInfectiousness();

		// respiratory route only: ventilation / contact-intensity correction, face masks, indoor-outdoor dilution
		double respiratoryModifier = ciCorrection
				* maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding
				* maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake
				* indoorOutdoorFactor;

		// contact-side route split combined with the pathogen's per-route transmissibility
		TransmissionWeights pathogenWeights = pathogenConfig.getParams(params.getPathogen()).getRouteTransmissibility();
		double viaRespiratory = transmissionWeights.getRespiratory() * pathogenWeights.getRespiratory() * respiratoryModifier;
		double viaDirectContact = transmissionWeights.getDirectContact() * pathogenWeights.getDirectContact();
		// fomite: double viaFomite = transmissionWeights.getFomite() * pathogenWeights.getFomite();

		return 1 - Math.exp(-base * (viaRespiratory + viaDirectContact /* fomite: + viaFomite */));
	}
}
