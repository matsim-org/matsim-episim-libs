package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.policy.Restriction;

import java.util.Map;

/**
 * This infection model calculates the joint time two persons have been at the same place and calculates a infection probability according to:
 * <pre>
 *      1 - e^(calibParam * contactIntensity * jointTimeInContainer * intake * shedding * ci_correction)
 * </pre>
 */
public final class DefaultInfectionModel implements InfectionModel {

	private final FaceMaskModel maskModel;
	private final EpisimConfigGroup episimConfig;
	private final VaccinationConfigGroup vaccinationConfig;
	private final VirusStrainConfigGroup virusStrainConfig;
	private int iteration;

	@Inject
	public DefaultInfectionModel(FaceMaskModel faceMaskModel, Config config) {
		this.maskModel = faceMaskModel;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		this.virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

	}

	@Override
	public void setIteration(int iteration) {
		this.iteration = iteration;
	}

	@Override
	public double calcInfectionProbability(EpisimPerson target, EpisimPerson infector, Map<String, Restriction> restrictions,
	                                       EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2,
	                                       double contactIntensity, double jointTimeInContainer) {

		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

		// note that for 1pct runs, calibParam is of the order of one, which means that for typical times of 100sec or more,
		// exp( - 1 * 1 * 100 ) \approx 0, and thus the infection proba becomes 1.  Which also means that changes in contactIntensity has
		// no effect.  kai, mar'20
		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getParams(infector.getVirusStrain());
		double susceptibility = Math.min(getVaccinationEffectiveness(strain, target, vaccinationConfig, iteration), getImmunityEffectiveness(strain, target, vaccinationConfig, iteration));

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * contactIntensity * jointTimeInContainer * ciCorrection
				* getInfectivity(infector, strain, vaccinationConfig, iteration)
				* target.getSusceptibility()
				* susceptibility
				* strain.getInfectiousness()
				* maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding
				* maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake
		);
	}

	/**
	 * Calculate the current effectiveness of vaccination.
	 */
	static double getVaccinationEffectiveness(VirusStrainConfigGroup.StrainParams virusStrain, EpisimPerson target, VaccinationConfigGroup config, int iteration) {

		if (target.getVaccinationStatus() == EpisimPerson.VaccinationStatus.no)
			return 1;

		int daysVaccinated = target.daysSince(EpisimPerson.VaccinationStatus.yes, iteration);

		VaccinationConfigGroup.VaccinationParams params = config.getParams(target.getVaccinationType());
		VirusStrain strain = virusStrain.getStrain();

		double vaccineEffectiveness;

		// use re vaccine effectiveness if person received the new vaccine
		if (target.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.yes) {
			vaccineEffectiveness = params.getBoostEffectiveness(strain, daysVaccinated);
		} else {
			vaccineEffectiveness = params.getEffectiveness(strain, daysVaccinated);
		}

		// https://www.medrxiv.org/content/10.1101/2021.03.16.21253686v2.full.pdf

		return 1 - vaccineEffectiveness;
	}

	/**
	 * Calculate the infectivity of an infector based on vaccine or previous infections.
	 */
	static double getInfectivity(EpisimPerson infector, VirusStrainConfigGroup.StrainParams strain, VaccinationConfigGroup config, int iteration) {

		double naturalInfectivity = 1;

		if (config.hasParams(VaccinationType.natural) && infector.hadDiseaseStatus(EpisimPerson.DiseaseStatus.recovered)) {
			VaccinationConfigGroup.VaccinationParams params = config.getParams(VaccinationType.natural);
			naturalInfectivity = params.getInfectivity(strain.getStrain(), infector.daysSince(EpisimPerson.DiseaseStatus.recovered, iteration));
		}

		return Math.min(getVaccinationInfectivity(infector, strain, config, iteration), naturalInfectivity);
	}

	/**
	 * Reduced infectivity of a vaccinated persson.
	 */
	static double getVaccinationInfectivity(EpisimPerson infector, VirusStrainConfigGroup.StrainParams strain, VaccinationConfigGroup config, int iteration) {

		if (infector.getVaccinationStatus() == EpisimPerson.VaccinationStatus.no)
			return 1;

		int daysVaccinated = infector.daysSince(EpisimPerson.VaccinationStatus.yes, iteration);

		VaccinationConfigGroup.VaccinationParams params = config.getParams(infector.getVaccinationType());

		if (infector.getReVaccinationStatus() == EpisimPerson.VaccinationStatus.yes) {
			return params.getBoostInfectivity(strain.getStrain(), daysVaccinated);
		} else
			return params.getInfectivity(strain.getStrain(), daysVaccinated);
	}

	/**
	 * Calculate factor for natural immunity after infection.
	 */
	static double getImmunityEffectiveness(VirusStrainConfigGroup.StrainParams virusStrain, EpisimPerson target, VaccinationConfigGroup config, int iteration) {
		if (target.getNumInfections() < 1 || !target.hadDiseaseStatus(EpisimPerson.DiseaseStatus.recovered))
			return 1;

		if (!config.hasParams(VaccinationType.natural))
			return 1;

		int daysSince = target.daysSince(EpisimPerson.DiseaseStatus.recovered, iteration);

		// persons can not get infected for 180 days when
		// they had 2 infections or 1 infection and vaccinations
		// TODO: only here a quick fix and needs to be remodelled
		if (daysSince < 180 && (target.getNumInfections() >= 2 || (target.getNumInfections() >= 1 && target.getVaccinationStatus() == EpisimPerson.VaccinationStatus.yes)))
			return 0;

		VaccinationConfigGroup.VaccinationParams params = config.getParams(VaccinationType.natural);

		return 1 - params.getEffectiveness(virusStrain.getStrain(), daysSince);
	}
}
