/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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


import org.apache.commons.io.FileUtils;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.episim.events.EpisimEventsReader;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.events.EpisimInfectionEventHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class aims to analyze the age distribution of persons that get infected within episim.
 * You need to provide a population file containing the age of a person as person attribute 'age'.
 * Furthermore, you need to provide the path to the directory containing the episim-events-files.
 * Finally, two csv files are dumped out, containing the number of infected persons per <br>
 *     1) exact age (number) <br>
 *     2) age group (10 year bins, ie. 0-9 years, 10-19 years,....) <br>
 */
class TSAgeDistribution {


	private static final String INPUT_POPULATION = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_entirePopulation_emptyPlans_withDistricts.xml.gz";
	private static final String INPUT_EVENTS = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_episim_events.xml.gz";
//	private static final String OUTPUTDIR = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/";
	private static final String OUTPUTDIR = "./";

	public static void main(String[] args) {

		Population population = PopulationUtils.readPopulation(INPUT_POPULATION);

		Map<Integer, Integer> exactAges = new HashMap<>();
		Map<Integer, Integer> ageGroups = new HashMap<>();

		for (Person person : population.getPersons().values()) {
			int age = (int) person.getAttributes().getAttribute("age");
			exactAges.compute(age, (k,v) -> v==null? 1 : v+1);

			int ageGroup = Math.floorDiv(age,10);
			ageGroups.compute(ageGroup, (k,v) -> v==null? 1 : v+1);
		}

		System.out.println("population size = " + population.getPersons().size());
		System.out.println("exactAges sum = " + exactAges.values().stream().collect(Collectors.summingInt(Integer::intValue)));
		System.out.println("ageGroups sum = " + ageGroups.values().stream().collect(Collectors.summingInt(Integer::intValue)));

		File eventsDir = new File(INPUT_EVENTS);
		if( ! eventsDir.exists() || ! eventsDir.isDirectory()) throw new IllegalArgumentException();
		List<File> fileList = new ArrayList(FileUtils.listFiles(eventsDir, new String[]{"gz"}, false));
		EventsManager manager = EventsUtils.createEventsManager();
		AgeDistributionAnalysisHandler handler = new AgeDistributionAnalysisHandler();
		manager.addHandler(handler);

		Counter counter = new Counter("reading events file nr ");
		fileList.forEach(file -> {
			new EpisimEventsReader(manager).readFile(file.getAbsolutePath());
		});

		Map<Integer, Integer> infectionExactAges = new HashMap<>();
		Map<Integer, Integer> infectionAgeGroups = new HashMap<>();
		for (Id<Person> personId : handler.personCache) {
			Person person = population.getPersons().get(personId);

			int age = (int) person.getAttributes().getAttribute("age");
			infectionExactAges.compute(age, (k,v) -> v==null? 1 : v+1);

			int ageGroup = Math.floorDiv(age,10);
			infectionAgeGroups.compute(ageGroup, (k,v) -> v==null? 1 : v+1);
		}

		Map<Integer, Map<Integer,Integer>> infectionAgeGroupsPerWeek = new HashMap<>();
		for (Integer week : handler.infectedPersonsPerWeek.keySet()) {
			Set<Id<Person>> infectedPersons = handler.infectedPersonsPerWeek.get(week);
			Map<Integer,Integer> weekAgeGroups = new HashMap<>();
			infectionAgeGroups.keySet().forEach(ageGroup -> weekAgeGroups.put(ageGroup,0));
			for (Id<Person> personId : infectedPersons) {
				Person person = population.getPersons().get(personId);
				int age = (int) person.getAttributes().getAttribute("age");
				int ageGroup = Math.floorDiv(age,10);
				weekAgeGroups.compute(ageGroup, (k,v) -> v==null? 1 : v+1);
			}
			infectionAgeGroupsPerWeek.put(week, weekAgeGroups);
		}


		{ //write exactAges
			BufferedWriter writer = IOUtils.getBufferedWriter(OUTPUTDIR + "exactAges.csv");
			try {

				writer.write("age;nrOfPersonInPop;nrOfInfectionEvents");

				for(int age : exactAges.keySet()){
					infectionExactAges.putIfAbsent(age, 0);
					writer.newLine();
					writer.write("" + age + ";" + exactAges.get(age) + ";" + infectionExactAges.get(age));
				}
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		{	//write ageGroups
			BufferedWriter writer = IOUtils.getBufferedWriter(OUTPUTDIR + "ageGroups.csv");
			try {
				String header = "ageGroup;nrOfPersonInPop;nrOfInfectionEvents";
				List<Integer> weeks = new ArrayList(infectionAgeGroupsPerWeek.keySet());
				Collections.sort(weeks);

				for (Integer week : weeks) {
					header += ";week" + week;
				}
				writer.write(header);

				for(int ageGroup : ageGroups.keySet()){
					infectionAgeGroups.putIfAbsent(ageGroup, 0);
					writer.newLine();
					String line = "" + ageGroup + ";" + ageGroups.get(ageGroup) + ";" + infectionAgeGroups.get(ageGroup);
					for (Integer week : weeks) {
						line += ";" + infectionAgeGroupsPerWeek.get(week).get(ageGroup);
					}
					writer.write(line);
				}
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private static class AgeDistributionAnalysisHandler implements EpisimInfectionEventHandler {
		Set<Id<Person>> personCache = new HashSet<>();
		Map<Integer,Set<Id<Person>>> infectedPersonsPerWeek = new HashMap<>();

		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			int day = (int) Math.floor(event.getTime() /  (24*3600) );
			int week = Math.floorDiv(day, 7);

//			personCache.add(event.getInfectorId());
			personCache.add(event.getPersonId());

			infectedPersonsPerWeek.putIfAbsent(week, new HashSet<>());
			infectedPersonsPerWeek.get(week).add(event.getPersonId());
		}
	}
}
