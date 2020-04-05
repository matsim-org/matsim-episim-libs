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
import org.matsim.episim.EpisimConfigGroup.InfectionParams;
import org.matsim.episim.policy.FixedPolicy;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author smueller
 */
public class RunEpisimSnz {

    public static void main(String[] args) throws IOException {

        OutputDirectoryLogging.catchLogEntries();

        Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        episimConfig.setInputEventsFile("../shared-svn/projects/episim/matsim-files/snz/Berlin/snzDrt220a.0.events.reduced.xml.gz");
        episimConfig.setFacilitiesHandling(FacilitiesHandling.snz);

        episimConfig.setSampleSize(0.25);
        episimConfig.setCalibrationParameter(0.0000012);
        //episimConfig.setPutTracablePersonsInQuarantine(EpisimConfigGroup.PutTracablePersonsInQuarantine.yes);

        int closingIteration = 1000;

        addParams(episimConfig);

        setContactIntensities(episimConfig);

        episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
                .shutdown(closingIteration, DEFAULT_ACTIVITIES)
                .build()
        );

        RunEpisim.setOutputDirectory(config);

        ConfigUtils.applyCommandline(config, Arrays.copyOfRange(args, 0, args.length));
        // yyyyyy I would do this the other way around, i.e. apply cli params _before_ the output dir name is constructed.  ???

        RunEpisim.runSimulation(config, 150);
    }

	private static void setContactIntensities(EpisimConfigGroup episimConfig) {
		episimConfig.getOrAddContainerParams("pt")
        	.setContactIntensity(10.0);
        episimConfig.getOrAddContainerParams("tr")
        	.setContactIntensity(10.0);
        episimConfig.getOrAddContainerParams("leisure")
        	.setContactIntensity(5.0);
        episimConfig.getOrAddContainerParams("educ_kiga")
	        .setContactIntensity(10.0);
        episimConfig.getOrAddContainerParams("educ_primary")
	        .setContactIntensity(4.0);
        episimConfig.getOrAddContainerParams("educ_secondary")
	        .setContactIntensity(2.0);
        episimConfig.getOrAddContainerParams("home")
	        .setContactIntensity(3.0);
	}
    
    public static void addParams(EpisimConfigGroup episimConfig) {
    	
    	episimConfig.addContainerParams(new InfectionParams("pt", "tr"));
        // regular out-of-home acts:
    	episimConfig.addContainerParams(new InfectionParams("work"));
    	episimConfig.addContainerParams(new InfectionParams("leisure"));
    	episimConfig.addContainerParams(new InfectionParams("educ_kiga"));
    	episimConfig.addContainerParams(new InfectionParams("educ_primary"));
    	episimConfig.addContainerParams(new InfectionParams("educ_secondary"));
    	episimConfig.addContainerParams(new InfectionParams("educ_higher"));
    	episimConfig.addContainerParams(new InfectionParams("shopping"));
    	episimConfig.addContainerParams(new InfectionParams("errands"));
        episimConfig.addContainerParams(new InfectionParams("business"));
        
        episimConfig.addContainerParams(new InfectionParams("home"));
    	
    }
    
    private static final String[] DEFAULT_ACTIVITIES = {
            "pt", "work", "leisure", "educ_kiga","educ_primary", "educ_secondary", "educ_higher", "shopping", "errands", "business", "home"
    };

}
