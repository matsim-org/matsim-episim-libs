package org.matsim.episim;

import org.matsim.episim.model.VirusStrain;

@FunctionalInterface
public interface VaccinationFactorFunction {

	/**
	 * Function to retrieve factor from vaccination params.
	 */
	double getFactor(VaccinationConfigGroup.VaccinationParams params, VirusStrain strain, int day);

}
