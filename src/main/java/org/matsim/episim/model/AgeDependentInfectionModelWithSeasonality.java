package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.policy.Restriction;

import java.util.Map;
import java.util.SplittableRandom;

/**
 * Extension of the {@link DefaultInfectionModel}, with additional parameter {@link #SUSCEPTIBILITY} and {@link #VIRAL_LOAD},
 *  which are set according to age. 
 */
public final class AgeDependentInfectionModelWithSeasonality implements InfectionModel {

	private final FaceMaskModel maskModel;
	private final EpisimConfigGroup episimConfig;
	private final SplittableRandom rnd;
	
	private int iteration;


	@Inject
	public AgeDependentInfectionModelWithSeasonality(FaceMaskModel faceMaskModel, Config config, SplittableRandom rnd) {
		this.maskModel = faceMaskModel;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.rnd = rnd;
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
		
		int ageTarget = -1;

		for (String attr : target.getAttributes().getAsMap().keySet()) {
			if (attr.contains("age")) {
				ageTarget = (int) target.getAttributes().getAttribute(attr);
				break;
			}
		}
		
		if (ageTarget < 0) throw new RuntimeException("Age attribute not found for person=" + target.getPersonId().toString());
		
		int ageInfector = -1;

		for (String attr : infector.getAttributes().getAsMap().keySet()) {
			if (attr.contains("age")) {
				ageInfector = (int) infector.getAttributes().getAttribute(attr);
				break;
			}
		}
		
		if (ageInfector < 0) throw new RuntimeException("Age attribute not found for person=" + infector.getPersonId().toString());

		double susceptibility = 1.;
		double infectability = 1.;
		
		if (ageTarget < 20) susceptibility = 0.45;
		if (ageInfector < 20) infectability = 0.85;
		
		double indoorOutdoorFactor = InfectionModelWithSeasonality.getIndoorOutdoorFactor(episimConfig.getStartDate(), iteration, rnd, act1, act2);

		return 1 - Math.exp(-episimConfig.getCalibrationParameter() * susceptibility * infectability * contactIntensity * jointTimeInContainer * ciCorrection
				* maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding
				* maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake
				* indoorOutdoorFactor
		);
	}
}
