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
import java.util.Map.Entry;

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
	
//	private static final String WORKINGDIR = "./output-PtInterventions/";
	private static final String WORKINGDIR = "output/";
	private static final LocalDate startDate = LocalDate.parse("2020-02-16");

	private static HashMap<String, InfectedPerson> infectedPersons = new LinkedHashMap<String, InfectedPerson>();
	private static final Logger log = LogManager.getLogger(SMRValuesFromEvents.class);
	
	private static HashMap<String, HashMap<Integer, Integer>> infectionsPerActivity = new LinkedHashMap<String, HashMap<Integer, Integer>>();
	
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
		
		log.info("Read " + scenarios.size() + " files");
		log.info(scenarios);
		
		calcInfectionsPerAct(scenarios);

		calcRValues(scenarios);
		
	}

	private static void calcInfectionsPerAct(HashSet<String> scenarios) throws IOException {
		FileWriter fw = new FileWriter(new File(WORKINGDIR + "infectionsPerActivity.txt"));
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write("day\tdate\tactivity\tinfections\tscenario");

		 EventsManager manager = EventsUtils.createEventsManager();
		 InfectionsHandler iHandler = new InfectionsHandler();
		 manager.addHandler(iHandler);
		 for (String scenario : scenarios) {
			 infectionsPerActivity.clear();
			 File[] eventFiles = new File(WORKINGDIR + scenario + "/events").listFiles();
			 for (File file : eventFiles) {
				 if (file.getName().contains("xml.gz")) new EpisimEventsReader(manager).readFile(file.getAbsolutePath());
			 }

			 for(int i = 0; i <= eventFiles.length; i++) {
				 for (Entry<String, HashMap<Integer, Integer>> e : infectionsPerActivity.entrySet()) {
					 if (e.getKey().equals("pt") || e.getKey().equals("total")) {
						 int infections = 0;
						 if (e.getValue().get(i) != null) infections = e.getValue().get(i);
						 bw.newLine();
						 bw.write(i+ "\t" + startDate.plusDays(i).toString() + "\t" + e.getKey() + "\t" + infections + "\t" + scenario);
					 }
				 }
			 }
			 bw.flush();
			 log.info("calculated infections per activity in scenario " + scenario);
		 }
		bw.close();
	}

	private static void calcRValues(HashSet<String> scenarios) throws IOException {

		 FileWriter fw = new FileWriter(new File(WORKINGDIR + "rValues.txt"));
		 BufferedWriter bw = new BufferedWriter(fw);
		 bw.write("day\tdate\trValue\tscenario\tnewContagious");
		    
		 EventsManager manager = EventsUtils.createEventsManager();
		 RHandler rHandler = new RHandler(); 
		 manager.addHandler(rHandler);
		
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
			 log.info("calculated r values in scenario " + scenario);
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
	
	private static class InfectionsHandler implements EpisimInfectionEventHandler {
		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			String infectionType = event.getInfectionType();
			if (!infectionsPerActivity.containsKey("total")) infectionsPerActivity.put("total", new HashMap<Integer, Integer>());
			if (!infectionsPerActivity.containsKey(infectionType)) infectionsPerActivity.put(infectionType, new HashMap<Integer, Integer>());
			
			HashMap<Integer, Integer> infectionsPerDay = infectionsPerActivity.get(infectionType);
			HashMap<Integer, Integer> infectionsPerDayTotal = infectionsPerActivity.get("total");

			int day = (int) event.getTime() / 86400;
			
			if (!infectionsPerDay.containsKey(day)) infectionsPerDay.put(day, 1);
			else infectionsPerDay.replace(day, infectionsPerDay.get(day) + 1);
			
			if (!infectionsPerDayTotal.containsKey(day)) infectionsPerDayTotal.put(day, 1);
			else infectionsPerDayTotal.replace(day, infectionsPerDayTotal.get(day) + 1);
			
		}
	}
	
	
	
	
}




