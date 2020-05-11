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
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.episim.events.EpisimEventsReader;
import org.matsim.episim.events.EpisimInfectionEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TSAgeDistribution {


	private static final String INPUT_POPULATION = "D:/svn/shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_entirePopulation_emptyPlans_withDistricts.xml.gz";
	private static final String INPUT_EVENTS_DIR = "D:/svn/public-svn/matsim/scenarios/countries/de/episim/battery/v9/masks/berlin/analysis/baseCase/events/";
	private static final String OUTPUTDIR = "D:/svn/public-svn/matsim/scenarios/countries/de/episim/battery/v9/masks/berlin/analysis/baseCase/";

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

		EventsManager manager = EventsUtils.createEventsManager();
		AgeDistributionAnalysisHandler handler = new AgeDistributionAnalysisHandler();
		manager.addHandler(handler);

		File eventsDir = new File(INPUT_EVENTS_DIR);
		if( ! eventsDir.exists() || ! eventsDir.isDirectory()) throw new IllegalArgumentException();
		Collection<File> fileList = FileUtils.listFiles(eventsDir, new String[]{"gz"}, false);
		Counter counter = new Counter("reading events file nr ");
		fileList.forEach(file -> {
			counter.incCounter();
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
				writer.write("age;nrOfPersonInPop;nrOfInfectionEvents");

				for(int age : ageGroups.keySet()){
					infectionAgeGroups.putIfAbsent(age, 0);
					writer.newLine();
					writer.write("" + age + ";" + ageGroups.get(age) + ";" + infectionAgeGroups.get(age));
				}
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private static class AgeDistributionAnalysisHandler implements EpisimInfectionEventHandler {
		Set<Id<Person>> personCache = new HashSet<>();

		@Override
		public void handleEvent(EpisimInfectionEvent event) {
			personCache.add(event.getInfectorId());
			personCache.add(event.getPersonId());
		}
	}
}
