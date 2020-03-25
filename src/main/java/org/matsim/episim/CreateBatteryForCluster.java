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


package org.matsim.episim;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.EpisimConfigGroup.FacilitiesHandling;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.InfectionParams;



/**
* @author smueller
*/

public class CreateBatteryForCluster {
	private static final String workingDir = "../epidemic/battery/";
	public static void main(String[] args) throws IOException {
		
		BufferedWriter bashScriptWriter = new BufferedWriter(new FileWriter(workingDir + "_bashScript.sh"));
		BufferedWriter infoWriter = new BufferedWriter(new FileWriter(workingDir + "_info.txt"));
		
		infoWriter.write("RunScript;Config;RunId;Output;PtClosingDate;WorkClosingDate;LeisureClosingDate;OtherExceptHomeClosingDate");
		infoWriter.newLine();
		 List<Long> pt = Arrays.asList(1000L, 10L, 20L, 30L);
		 List<Long> work = Arrays.asList(1000L, 10L, 20L, 30L);
		 List<Long> leisure = Arrays.asList(1000L, 10L, 20L, 30L);
		 List<Long> otherExceptHome = Arrays.asList(1000L, 10L, 20L, 30L);
		 int ii = 1;
		 for (long p : pt) {
	            for (long w : work) {
	                for (long l : leisure) {
	                    for (long o : otherExceptHome) {
	                    	String runScriptFileName = createRunScript(ii);
	                    	String configFileName = createConfigFile(p, w, l, o, ii);
	                    	bashScriptWriter.write("qsub snz" + ii + ".sh");
	                    	bashScriptWriter.newLine();
	                    	String outputPath = "output/" + p + "-" + w + "-" + l + "-" + o;
	                    	String runId = "snz" + ii;
	                    	infoWriter.write(runScriptFileName +";" + configFileName + ";" + runId + ";" + outputPath + ";" +  p + ";" + w + ";" + l + ";" + o);
	                    	infoWriter.newLine();
	                    	ii++;
	                    }
	                }
	            }
	        }
		 
		 bashScriptWriter.close();
		 infoWriter.close();
		
		
		
	}
	
	public static String createRunScript(int ii) throws IOException {
		
		BufferedReader reader = new BufferedReader(new FileReader(workingDir + "__snz0.sh"));
		
		String runScriptFileName = "snz" + ii + ".sh";
		
		BufferedWriter writer = new BufferedWriter(new FileWriter(workingDir + runScriptFileName));

		String line;
		
		int lineNo = 0;

		while ((line = reader.readLine()) != null)
		{		
				if (lineNo != 2) {
					writer.write(line);
				}
				else {
					writer.write("#$ -N snz" + ii);
				}
				writer.newLine();
				lineNo++;   
				if (ii == 1) {
					System.out.println(line);
				}
		}
			writer.close();
	        
			reader.close();
			
			return runScriptFileName;
	}
	
	public static String createConfigFile (long p, long w, long l, long o, int ii) {
		
		 Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
         EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

         episimConfig.setInputEventsFile("../snzDrt220.0.events.reduced.xml.gz");
         episimConfig.setFacilitiesHandling(FacilitiesHandling.snz);

         episimConfig.setCalibrationParameter(0.000005);

//	        int closingIteration = 10;
         // pt:
         episimConfig.addContainerParams(new InfectionParams("tr").setContactIntensity(10.).setShutdownDay(p));
         // regular out-of-home acts:
         episimConfig.addContainerParams(new InfectionParams("business").setShutdownDay(o).setRemainingFraction(0.));
         episimConfig.addContainerParams(new InfectionParams("educ_higher").setShutdownDay(o).setRemainingFraction(0.));
         episimConfig.addContainerParams(new InfectionParams("educ_secondary").setShutdownDay(o).setRemainingFraction(0.));
         episimConfig.addContainerParams(new InfectionParams("errands").setShutdownDay(o).setRemainingFraction(0.));
         episimConfig.addContainerParams(new InfectionParams("leisure").setShutdownDay(l).setRemainingFraction(0.));
         episimConfig.addContainerParams(new InfectionParams("shopping").setShutdownDay(o).setRemainingFraction(0.));
         episimConfig.addContainerParams(new InfectionParams("work").setShutdownDay(w).setRemainingFraction(0.));
         // home act:
         episimConfig.addContainerParams(new InfectionParams("home"));

         config.controler().setOutputDirectory("output/" + p + "-" + w + "-" + l + "-" + o);
         
         String configFileName = "config_snz" + ii + ".xml";
        
         ConfigUtils.writeConfig(config, workingDir + configFileName);
         
         return configFileName;
		
	}
	
	public static void createBashScript(int ii) {
		
	}

}
