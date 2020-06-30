/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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

package org.matsim.episim.analysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.episim.EpisimPerson.DiseaseStatus;
import org.matsim.episim.events.EpisimEventsReader;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.events.EpisimInfectionEventHandler;
import org.matsim.episim.events.EpisimPersonStatusEvent;
import org.matsim.episim.events.EpisimPersonStatusEventHandler;


/**
* @author smueller
* Calculates R values for all runs in given directory, dated on day of switching to contagious
* Output is written to rValues.txt in the working directory
*/

public class SMRValuesFromEvents {
	
	private static final String WORKINGDIR = "./output-unrestrictedRuns/";
	private static final LocalDate startDate = LocalDate.parse("2020-02-16");

	private static HashMap<String, InfectedPerson> infectedPersons = new LinkedHashMap<String, InfectedPerson>();
	private static final Logger log = LogManager.getLogger(SMRValuesFromEvents.class);
	
	public static void main(String[] args) throws IOException {
		Configurator.setLevel("org.matsim.core.config", Level.WARN);
		Configurator.setLevel("org.matsim.core.controler", Level.WARN);
		Configurator.setLevel("org.matsim.core.events", Level.WARN);
		Configurator.setLevel("org.matsim.core.utils", Level.WARN);
		
		HashSet<String> scenarios = new LinkedHashSet<String>(); 
		
		File[] files = new File(WORKINGDIR).listFiles();
		 
		 for (File file : files) {
		        if (file.isDirectory()) {
		        	scenarios.add(file.getName());
		        }
		 }
		 
		 FileWriter fw = new FileWriter(new File(WORKINGDIR + "rValues.txt"));
		 BufferedWriter bw = new BufferedWriter(fw);
		 bw.write("day\tdate\trValue\tscenario\tnewContagious");
		    
		 EventsManager manager = EventsUtils.createEventsManager();
		 RHandler rHandler = new RHandler(); 
		 manager.addHandler(rHandler);

		 log.info("Reading " + scenarios.size() + " files");
		 log.info(scenarios);
		
		 for (String scenario : scenarios) {
			 infectedPersons.clear();
			 File[] eventFiles = new File(WORKINGDIR + scenario + "/events").listFiles();
			 for (File file : eventFiles) {
				 if (file.getName().contains("xml.gz")) new EpisimEventsReader(manager).readFile(file.getAbsolutePath());
			 }
				 
			 for(int i = 0; i <= eventFiles.length; i++) {
				int noOfInfectors = 0;
				int noOfInfected = 0;
				for (InfectedPerson ip : infectedPersons.values()) {
					if (ip.getContagiousDay() == i) {
						noOfInfectors++;
						noOfInfected = noOfInfected + ip.getNoOfInfected();
					}
				}
				if (noOfInfectors != 0) {
					bw.newLine();
					double r = (double) noOfInfected / noOfInfectors;
					bw.write(i + "\t" + startDate.plusDays(i).toString() + "\t" + r + "\t" + scenario + "\t" + noOfInfectors);
				}
			 }				 
			 bw.flush();
			 log.info("processed scenario " + scenario);
			}
		 bw.close();
	}
	
	private static class InfectedPerson {
		
		private String id;
		private int noOfInfected;
		private int contagiousDay;
		
		InfectedPerson (String id) {
			this.id = id;
			this.noOfInfected= 0;
		}
		
		String getId() {
			return id;
		}
		void setId(String id) {
			this.id = id;
		}
		int getNoOfInfected() {
			return noOfInfected;
		}
		void increaseNoOfInfectedByOne() {
			this.noOfInfected++;
		}

		int getContagiousDay() {
			return contagiousDay;
		}

		 void setContagiousDay(int contagiousDay) {
			this.contagiousDay = contagiousDay;
		}

	}
	
	private static class RHandler implements EpisimPersonStatusEventHandler, EpisimInfectionEventHandler {
		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			String infectorId = event.getInfectorId().toString();
			InfectedPerson infector = infectedPersons.computeIfAbsent(infectorId, InfectedPerson::new);
			infector.increaseNoOfInfectedByOne();
		}
		@Override
		public void handleEvent(EpisimPersonStatusEvent event) {
			
			if (event.getDiseaseStatus() == DiseaseStatus.contagious) {
				
				String personId = event.getPersonId().toString();				
				InfectedPerson person = infectedPersons.computeIfAbsent(personId, InfectedPerson::new);
				person.setContagiousDay((int) event.getTime() / 86400);
			}
		}
	}
}




