/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */


package org.matsim.run;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.FacilitiesHandling;
import org.matsim.episim.policy.FixedPolicy;

import java.io.IOException;

/**
 * @author smueller
 */
public class KNRunEpisimSnz {

	public static void main(String[] args) throws IOException {

		OutputDirectoryLogging.catchLogEntries();

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/snzDrt220a.0.events.reduced.xml.gz");
		episimConfig.setFacilitiesHandling(FacilitiesHandling.snz);
		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.0000012);

		config.controler().setOutputDirectory("output-base-" + episimConfig.getCalibrationParameter());

		RunEpisimSnz.addParams(episimConfig);

		RunEpisimSnz.setContactIntensities(episimConfig);

		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
				.restrict(36, 0.1, "educ_kiga", "educ_primary")
				.restrict(36, 0.0, "educ_secondary", "educ_higher")
				.restrict(36, 0.9, "leisure")
				.build()
		);


		// TODO: guice RunEpisim.runSimulation(config, 150);
	}

}
