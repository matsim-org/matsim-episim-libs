package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.policy.Restriction;

import java.time.LocalDate;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Extension of the {@link DefaultInfectionModel} with a seasonality component.
 */
public final class InfectionModelWithSeasonality implements InfectionModel {

	private final FaceMaskModel maskModel;
	private final SplittableRandom rnd;
	private final EpisimConfigGroup episimConfig;

	private int iteration;

	@Inject
	public InfectionModelWithSeasonality(FaceMaskModel faceMaskModel, SplittableRandom rnd, Config config) {
		this.maskModel = faceMaskModel;
		this.rnd = rnd;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
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

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * contactIntensity * jointTimeInContainer * ciCorrection
				* maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding
				* maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake
				* getIndoorOutdoorFactor(episimConfig.getStartDate(), iteration, rnd, act1, act2)
		);

	}

	static double getIndoorOutdoorFactor(LocalDate startDate, int iteration, SplittableRandom rnd, EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2) {
		if (!act1.getContainerName().equals("leisure") && !act2.getContainerName().equals("leisure")) return 1.;

		LocalDate date = startDate.plusDays(iteration);

		//anchor dates
		int daysOfYear = 365;
		int winter = 15; //15.01.
		int spring = 105; //15.04.
		int summer = 196; //15.07.
		int autumn = 288; //15.10.

		if (date.isLeapYear()) {
			daysOfYear++;
			spring++;
			summer++;
			autumn++;
		}

//		double probaWinter = 12.44 / 100.;
//		double probaSpring = 23.60 / 100.;
//		double probaSummer = 28.63 / 100.;
//		double probaAutumn = 21.15 / 100.;

		double probaWinter = 10. / 100.;
		double probaSpring = 80. / 100.;
		double probaSummer = 80. / 100.;
		double probaAutumn = 80. / 100.;

		double proba = 1;

		int dayOfYear = date.getDayOfYear();

		if (dayOfYear <= winter) {
			proba = probaAutumn + (probaWinter - probaAutumn) * (dayOfYear + daysOfYear - autumn) / (winter + daysOfYear - autumn);
		} else if (dayOfYear <= spring) {
			proba = probaWinter + (probaSpring - probaWinter) * (dayOfYear - winter) / (spring - winter);
		} else if (dayOfYear <= summer) {
			proba = probaSpring + (probaSummer - probaSpring) * (dayOfYear - spring) / (summer - spring);
		} else if (dayOfYear <= autumn) {
			proba = probaSummer + (probaAutumn - probaSummer) * (dayOfYear - summer) / (autumn - summer);
		} else if (dayOfYear <= daysOfYear) {
			proba = probaAutumn + (probaWinter - probaAutumn) * (dayOfYear - autumn) / (daysOfYear - autumn + winter);
		} else {
			throw new RuntimeException("Something went wrong. The day of the year is =" + dayOfYear);
		}

		double indoorOutdoorFactor = 1.;
		if (rnd.nextDouble() < proba) {
			indoorOutdoorFactor = 0.1;
		}

		return indoorOutdoorFactor;

	}

}
