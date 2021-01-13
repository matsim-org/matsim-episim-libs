package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.policy.Restriction;

import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SplittableRandom;

/**
 * Extension of the {@link DefaultInfectionModel} with a seasonality component.
 */
public final class InfectionModelWithSeasonality implements InfectionModel {

	private final FaceMaskModel maskModel;
	private final SplittableRandom rnd;
	private final EpisimConfigGroup episimConfig;
	private final EpisimReporting reporting;
	private final VaccinationConfigGroup vaccinationConfig;

	private double outdoorFactor;
	private int iteration;

	@Inject
	public InfectionModelWithSeasonality(FaceMaskModel faceMaskModel, SplittableRandom rnd, Config config, EpisimReporting reporting) {
		this.maskModel = faceMaskModel;
		this.rnd = rnd;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.vaccinationConfig = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
		this.reporting = reporting;
	}

	@Override
	public void setIteration(int iteration) {
		this.outdoorFactor = interpolateOutdoorFraction(episimConfig, iteration);
		this.iteration = iteration;
		reporting.reportOutdoorFraction(this.outdoorFactor, iteration);
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
				: DefaultInfectionModel.getVaccinationEffectiveness(target, vaccinationConfig, iteration);

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * contactIntensity * jointTimeInContainer * ciCorrection
				* susceptibility
				* infector.getVirusStrain().infectiousness
				* maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding
				* maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake
				* getIndoorOutdoorFactor(outdoorFactor, rnd, act1, act2)
		);

	}

	/**
	 * Interpolate outdoor fraction for current day.
	 */
	static double interpolateOutdoorFraction(EpisimConfigGroup episimConfig, int iteration) {
		LocalDate date = episimConfig.getStartDate().plusDays(iteration-1);
		return EpisimUtils.interpolateEntry((NavigableMap<LocalDate, ? extends Number>) episimConfig.getLeisureOutdoorFraction(), date);
	}

	static double getIndoorOutdoorFactor(double outdoorFraction, SplittableRandom rnd, EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2) {

		if (!act1.getContainerName().equals("leisure") && !act2.getContainerName().equals("leisure")) return 1.;

		double indoorOutdoorFactor = 1.;
		if (rnd.nextDouble() < outdoorFraction) {
			indoorOutdoorFactor = 0.1;
		}

		return indoorOutdoorFactor;

	}
}
