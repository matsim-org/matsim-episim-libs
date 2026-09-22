package org.matsim.episim.model;

import com.google.inject.Inject;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimPerson;
import org.matsim.episim.ImmunityConfigGroup;
import org.matsim.episim.PathogenConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.policy.Restriction;

import java.util.Map;

/**
 * Extension of the {@link DefaultInfectionModel}, with additional parameter {@link #SUSCEPTIBILITY} and {@link #VIRAL_LOAD},
 *  which are read from each person individually.
 *
 * @deprecated not supported anymore, this functionality can also be integrated into the regular model via person attributes.
 */
@Deprecated
public final class InfectionModelWithViralLoad implements InfectionModel {

	public static final String SUSCEPTIBILITY = "susceptibility";
	public static final String VIRAL_LOAD = "viralLoad";

	private final FaceMaskModel maskModel;
	private final EpisimConfigGroup episimConfig;
	private final VirusStrainConfigGroup virusStrainConfig;
	private final PathogenConfigGroup pathogenConfig;

	@Inject
	public InfectionModelWithViralLoad(FaceMaskModel faceMaskModel, Config config) {
		this.maskModel = faceMaskModel;
		this.episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		this.virusStrainConfig = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		this.pathogenConfig = ConfigUtils.addOrGetModule(config, PathogenConfigGroup.class);

		ImmunityConfigGroup.requireLegacyImmunity(config, getClass(), "applies no immunity at all");
	}


	@Override
	public double calcInfectionProbability(EpisimPerson target, EpisimPerson infector, Map<String, Restriction> restrictions,
										   EpisimConfigGroup.InfectionParams act1, EpisimConfigGroup.InfectionParams act2,
										   TransmissionWeights transmissionWeights,
										   double contactIntensity, double jointTimeInContainer) {

		// ci corr can not be null, because sim is initialized with non null value
		double ciCorrection = Math.min(restrictions.get(act1.getContainerName()).getCiCorrection(), restrictions.get(act2.getContainerName()).getCiCorrection());

		// note that for 1pct runs, calibParam is of the order of one, which means that for typical times of 100sec or more, exp( - 1 * 1 * 100 ) \approx 0, and
		// thus the infection proba becomes 1.  Which also means that changes in contactIntensity has no effect.  kai, mar'20

		double susceptibility = (double) target.getAttributes().getAttribute(SUSCEPTIBILITY);
		double infectability = (double) infector.getAttributes().getAttribute(VIRAL_LOAD);

		VirusStrainConfigGroup.StrainParams strain = virusStrainConfig.getParams(infector.getVirusStrain());

		// route-agnostic factors
		double base = episimConfig.getCalibrationParameter() * susceptibility * infectability * contactIntensity * jointTimeInContainer
				* target.getSusceptibility()
				* strain.getInfectiousness();

		// respiratory route only: ventilation / contact-intensity correction and face masks
		double respiratoryModifier = ciCorrection
				* maskModel.getWornMask(infector, act2, restrictions.get(act2.getContainerName())).shedding
				* maskModel.getWornMask(target, act1, restrictions.get(act1.getContainerName())).intake;

		// contact-side route split combined with the pathogen's per-route transmissibility
		TransmissionWeights pathogenWeights = pathogenConfig.getParams(strain.getPathogen()).getRouteTransmissibility();
		double viaRespiratory = transmissionWeights.getRespiratory() * pathogenWeights.getRespiratory() * respiratoryModifier;
		double viaDirectContact = transmissionWeights.getDirectContact() * pathogenWeights.getDirectContact();
		// fomite: double viaFomite = transmissionWeights.getFomite() * pathogenWeights.getFomite();

		return 1 - Math.exp(-base * (viaRespiratory + viaDirectContact /* fomite: + viaFomite */));
	}
}
