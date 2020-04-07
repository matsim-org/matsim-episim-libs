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

import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.FacilitiesHandling;

import java.io.IOException;

public class KNRunEpisim {
	private static final Logger log = Logger.getLogger(KNRunEpisim.class);

	public static void main(String[] args) throws IOException {

		OutputDirectoryLogging.catchLogEntries();

		Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

//        episimConfig.setInputEventsFile( "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/output-berlin-v5.4-1pct/berlin-v5.4-1pct.output_events_for_episim.xml.gz" );
//        episimConfig.setFacilitiesHandling(FacilitiesHandling.bln);
//        episimConfig.setSampleSize(0.01);
//        episimConfig.setCalibrationParameter(0.00021);

		episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/snzDrt220a.0.events.reduced.xml.gz");
		episimConfig.setFacilitiesHandling(FacilitiesHandling.snz);
		episimConfig.setSampleSize(0.25);
		episimConfig.setCalibrationParameter(0.0000003);

		config.controler().setOutputDirectory("output-base-" + episimConfig.getCalibrationParameter());

		RunEpisim.addDefaultParams(episimConfig);

//        public static final String[] DEFAULT_ACTIVITIES = {
//                        "pt", "work", "leisure", "edu", "shop", "errands", "business", "other", "freight", "home"
//        };

//        int closingIteration = 10; // intro of current regime
//        final com.typesafe.config.Config policyConfig = FixedPolicy.config()
//                                                            .restrict( 0, 0.0, "freight" )
//                                                            .restrict( closingIteration, 0.2, "pt" )
//                                                            .restrict( closingIteration, 0.2, "work" )
//                                                            .restrict( closingIteration, 0.1, "leisure" )
//                                                            .restrict( closingIteration, 0.0, "edu" )
//                                                            .restrict( closingIteration, 0.2, "shop" )
//                                                            .restrict( closingIteration, 0.2, "errands" )
//                                                            .restrict( closingIteration, 0.2, "business" )
//                                                            .restrict( closingIteration, 0.2, "other" )
//                                                            .build();
//
//        final String reduced = policyConfig.toString()
//                                           .replace( "\"", "" )
//                                           .replace( "Config(SimpleConfigObject(", "" )
//                                           .replace( ")", "" )
//                                           .replace(",","")
//                                           .replace(":{","It")
//                                           .replace("}}","")
//                                           .replace("}","-")
//                                           .replace("{","")
//                        ;
//        log.warn( reduced );
//
//        episimConfig.setPolicy(FixedPolicy.class, policyConfig );
//        config.controler().setOutputDirectory( "output-" + reduced );

		RunEpisim.runSimulation(config, 100);
	}

}
