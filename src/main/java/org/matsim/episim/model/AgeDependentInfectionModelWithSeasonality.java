package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;

import java.util.Map;
import java.util.SplittableRandom;

import static org.matsim.episim.model.DefaultInfectionModel.getImmunityEffectiveness;
import static org.matsim.episim.model.DefaultInfectionModel.getVaccinationEffectiveness;

/**
 * Extension of the {@link DefaultInfectionModel}, with age-dependent additions.
 */
public final class AgeDependentInfectionModelWithSeasonality implements InfectionModel {

	private final FaceMaskModel maskModel;
	private final EpisimConfigGroup episimConfig;
	private final EpisimReporting reporting;
	private final SplittableRandom rnd;
	private final VaccinationConfigGroup vaccinationConfig;
	private final VirusStrainConfigGroup virusStrainConfig;

	private final double[] susceptibility = new double[128];
	private final double[] infectivity = new double[susceptibility.length];

	private double outdoorFactor;
	private int iteration;

	@Inject
	AgeDependentInfectionModelWithSeasonality(FaceMaskModel faceMaskModel, Config config, EpisimReporting reporting, SplittableRandom rnd) {
		this.maskModel = faceMaskModel;
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
										   double contactIntensity, double jointTimeInContainer) {

		//noinspection ConstantConditions 		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

		double susceptibility = this.susceptibility[target.getAge()];
		double infectivity = this.infectivity[infector.getAge()];

		// apply reduced susceptibility of vaccinated persons
		VirusStrainConfigGroup.StrainParams params = virusStrainConfig.getParams(infector.getVirusStrain());
		susceptibility *= Math.min(getVaccinationEffectiveness(params, target, vaccinationConfig, iteration), getImmunityEffectiveness(params, target, vaccinationConfig, iteration));

		double indoorOutdoorFactor = InfectionModelWithSeasonality.getIndoorOutdoorFactor(outdoorFactor, rnd, act1, act2);

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * susceptibility * infectivity * contactIntensity * jointTimeInContainer * ciCorrection
				* target.getSusceptibility()
				* params.getInfectiousness()
				* maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding
				* maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake
				* indoorOutdoorFactor
		);
	}
}
