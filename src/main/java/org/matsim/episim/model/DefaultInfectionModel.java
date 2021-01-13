package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.VaccinationConfigGroup;
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
	private int iteration;

	@Inject
	public DefaultInfectionModel(FaceMaskModel faceMaskModel, Config config) {
		this.maskModel = faceMaskModel;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
	}

	@Override
	public void setIteration(int iteration) {
		this.iteration = iteration;
	}

	@Override
	public double calcInfectionProbability(EpisimPerson target, EpisimPerson infector, Map<String, Restriction> restrictions,
										   EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2, double jointTimeInContainer) {

		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());
		double contactIntensity = Math.min(act1.getContactIntensity(), act2.getContactIntensity());

		// note that for 1pct runs, calibParam is of the order of one, which means that for typical times of 100sec or more,
		// exp( - 1 * 1 * 100 ) \approx 0, and thus the infection proba becomes 1.  Which also means that changes in contactIntensity has
		// no effect.  kai, mar'20
		double susceptibility = target.getVaccinationStatus() == EpisimPerson.VaccinationStatus.no ? 1
				 : getVaccinationEffectiveness(target, vaccinationConfig, iteration);

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * contactIntensity * jointTimeInContainer * ciCorrection
				* susceptibility
				* infector.getVirusStrain().infectiousness
				* maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding
				* maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake
		);
	}

	/**
	 * Calculate the current effectiveness of vaccination.
	 */
	static double getVaccinationEffectiveness(EpisimPerson target, VaccinationConfigGroup config, int iteration) {
		double daysVaccinated = target.daysSince(EpisimPerson.VaccinationStatus.yes, iteration);
		double scale = (1d / config.getDaysBeforeFullEffect()) * daysVaccinated;
		double effectiveness = config.getEffectiveness();
		if (scale < 1)
			effectiveness *= scale;

		return 1 - effectiveness;
	}
}
