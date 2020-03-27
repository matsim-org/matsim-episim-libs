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

import com.google.common.io.Resources;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.FacilitiesHandling;
import org.matsim.episim.policy.FixedPolicy;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;


/**
 * @author smueller
 */

public class CreateBatteryForCluster {
    private static final String workingDir = "../epidemic/battery/";

    public static void main(String[] args) throws IOException {

        Files.createDirectories(Path.of(workingDir));

        // Copy run script
        Path runScript = Path.of(workingDir, "run.sh");
        Files.copy(Resources.getResource("_run.sh").openStream(), runScript, StandardCopyOption.REPLACE_EXISTING);
        runScript.toFile().setExecutable(true);

        BufferedWriter bashScriptWriter = new BufferedWriter(new FileWriter(workingDir + "_bashScript.sh"));
        BufferedWriter infoWriter = new BufferedWriter(new FileWriter(workingDir + "_info.txt"));

        infoWriter.write("RunScript;Config;RunId;Output;remainingFractionWork;remainingFractionShopping;remainingFractionLeisure;remainingFractionOther;ReopenAfter");
        infoWriter.newLine();
        List<Long> reopenAfter = Arrays.asList(1000L, 21L);
        List<Double> remainingFractionWork = Arrays.asList(1.0, 0.75, 0.5, 0.25, 0.);
        List<Double> remainingFractionShopping = Arrays.asList(1.0, 0.75, 0.5, 0.25, 0.);
        List<Double> remainingFractionLeisure = Arrays.asList(1.0, 0.75, 0.5, 0.25, 0.);
        List<Double> remainingFractionOther = Arrays.asList(1.0, 0.75, 0.5, 0.25, 0.);
        
        
//        List<Long> work = Arrays.asList(1000L, 10L, 20L, 30L);
//        List<Long> leisure = Arrays.asList(1000L, 10L, 20L, 30L);
//        List<Long> otherExceptHome = Arrays.asList(1000L, 10L, 20L, 30L);
        int ii = 1;
        for (long r : reopenAfter) {
	        for (double w : remainingFractionWork) {
	            for (double s : remainingFractionShopping) {
	                for (double l : remainingFractionLeisure) {
	                    for (double o : remainingFractionOther) {
	                        String runId = "sz" + ii;
	                        String configFileName = createConfigFile(w, s, l, o, r, ii);
	                        bashScriptWriter.write("qsub -N " + runId +" run.sh");
	                        bashScriptWriter.newLine();
	                        String outputPath = "output/" + w + "-" + s + "-" + l + "-" + o + "-" + r;
	                        infoWriter.write("run.sh;" + configFileName + ";" + runId + ";" + outputPath + ";" +  w + ";" + s + ";" + l + ";" + o + ";" + r);
	                        infoWriter.newLine();
	                        ii++;
	                    }
	                }
	            }
	        }
        }

        bashScriptWriter.close();
        infoWriter.close();


    }

    public static String createConfigFile(double w, double s, double l, double o, long r, int ii) throws IOException {

        Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        episimConfig.setInputEventsFile("../snzDrt220.0.events.reduced.xml.gz");
        episimConfig.setFacilitiesHandling(FacilitiesHandling.snz);

        episimConfig.setSampleSize(0.25);
        episimConfig.setCalibrationParameter(0.000005);

        RunEpisim.addDefaultParams(episimConfig);

        episimConfig.getOrAddContainerParams("pt")
                .setContactIntensity(10.0);

        com.typesafe.config.Config policyConf = FixedPolicy.config()
                .restrict(30, o, "business", "edu", "errands")
                .restrict(30, l, "leisure")
                .restrict(30, w, "work")
                .restrict(30, s, "shopping")
                .open(30 + r, "pt")
                .open(30 + r, "business", "edu", "errands")
                .open(30 + r, "leisure")
                .open(30 + r, "work")
                .open(30 + r, "shopping")
                .build();

        String policyFileName = "policy" + ii + ".conf";
        episimConfig.setOverwritePolicyLocation(policyFileName);
        Files.writeString(Path.of(workingDir + policyFileName), policyConf.root().render());

        config.controler().setOutputDirectory("output/" + w + "-" + s + "-" + l + "-" + o + "-" + r);

        String configFileName = "config_sz" + ii + ".xml";
        ConfigUtils.writeConfig(config, workingDir + configFileName);

        return configFileName;

    }

}
