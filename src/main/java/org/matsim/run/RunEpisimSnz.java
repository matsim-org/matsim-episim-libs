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
import java.util.Arrays;

/**
 * @author smueller
 */

public class RunEpisimSnz {

    public static void main(String[] args) throws IOException {

        OutputDirectoryLogging.catchLogEntries();

        Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        episimConfig.setInputEventsFile("../snzDrt220.0.events.reduced.xml.gz");
        episimConfig.setFacilitiesHandling(FacilitiesHandling.snz);

        episimConfig.setCalibrationParameter(0.002);

        int closingIteration = 10;

        RunEpisim.addDefaultParams(episimConfig);

        episimConfig.getOrAddContainerParams("pt").setContactIntensity(10.);

        episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
                .shutdown(closingIteration, RunEpisim.DEFAULT_ACTIVITIES)
                .build()
        );

        RunEpisim.setOutputDirectory(config);

        ConfigUtils.applyCommandline(config, Arrays.copyOfRange(args, 0, args.length));

        RunEpisim.runSimulation(config, 100);
    }

}
