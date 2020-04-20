package org.matsim.episim;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class EpisimUtils {

	/**
	 * Calculates the time based on the current iteration.
	 *
	 * @param time time relative to start of day
	 */
	public static double getCorrectedTime(double time, long iteration) {
		return Math.min(time, 3600. * 24) + iteration * 24. * 3600;
	}


	/**
	 * Creates an output directory, with a name based on current config and contact intensity..
	 */
	public static void setOutputDirectory(Config config) {
		StringBuilder outdir = new StringBuilder("output");
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		for (EpisimConfigGroup.InfectionParams infectionParams : episimConfig.getInfectionParams()) {
			outdir.append("-");
			outdir.append(infectionParams.getContainerName());
			if (infectionParams.getContactIntensity() != 1.) {
				outdir.append("ci").append(infectionParams.getContactIntensity());
			}
		}
		config.controler().setOutputDirectory(outdir.toString());

	}
}
