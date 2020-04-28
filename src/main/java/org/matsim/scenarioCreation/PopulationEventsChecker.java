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

package org.matsim.scenarioCreation;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class PopulationEventsChecker {


	private static final String INPUT_POPULATION_FILE = "D:/svn/shared-svn/projects/episim/matsim-files/snz/BerlinV2/processed-data/be_v2_snz_population_adults_noPlans.xml.gz";
	private static final String INPUT_EVENTS_FILE = "D:/svn/shared-svn/projects/episim/matsim-files/snz/BerlinV2/processed-data/be_v2_snz_adults_eventsFiltered.xml.gz";
	private static final String OUTPUT_POPULATION_FILE = "D:/svn/shared-svn/projects/episim/matsim-files/snz/BerlinV2/processed-data/be_v2_snz_population_adults_noPlans_eventAttributes.xml.gz";
	private static final String OUTPUT_CSV_FILE ="D:/svn/shared-svn/projects/episim/matsim-files/snz/BerlinV2/processed-data/be_v2_snz_population_adults_noPlans_eventsAnalysis.csv";

	public static void main(String[] args) {

		Population population = PopulationUtils.readPopulation(INPUT_POPULATION_FILE);

		EventsManager manager = EventsUtils.createEventsManager();

		PopulationEventsHandler handler = new PopulationEventsHandler(population);
		manager.addHandler(handler);
		EventsUtils.readEvents(manager, INPUT_EVENTS_FILE);

		new PopulationWriter(population).write(OUTPUT_POPULATION_FILE);


		writeCSVOutput(population, OUTPUT_CSV_FILE);

		System.out.println("persons occuring in events but not in Population:");
		for (Id<Person> personId : handler.personsNotInPopulation) {
			if(! (personId.toString().startsWith("pt") || personId.toString().startsWith("tr") ) ){
				System.out.println(personId);
			}
		}
		System.out.println("....");

		System.out.println("DONE");

	}

	private static void writeCSVOutput(Population population, String outputPath){

		BufferedWriter writer = IOUtils.getBufferedWriter(outputPath);



		try {
			writer.write("person; age; homeId; district; nrActivityStartEvents; nrActivityEndEvents; nrPersonEntersVehicleEvents; nrPersonLeavesVehicleEvents");

			for (Person person : population.getPersons().values()) {
				writer.newLine();
				int age = getAttributeValueOrAbsenceIndication(person , "age");
				String homeId = (String) person.getAttributes().getAttribute("homeId");
				String district = "NA";
				if(person.getAttributes().getAttribute("district") != null){
					district = (String) person.getAttributes().getAttribute("district");
				}
				int nrActivityStartEvents = getAttributeValueOrAbsenceIndication(person, "nrActivityStartEvents");
				int nrActivityEndEvents = getAttributeValueOrAbsenceIndication(person, "nrActivityEndEvents");
				int nrPersonEntersVehicleEvents = getAttributeValueOrAbsenceIndication(person, "nrPersonEntersVehicleEvents");
				int nrPersonLeavesVehicleEvents = getAttributeValueOrAbsenceIndication(person, "nrPersonLeavesVehicleEvents");
				writer.write(person.getId().toString() + ";" + age + ";" + homeId + ";" + district + ";"
						+ nrActivityStartEvents + ";" + nrActivityEndEvents + ";"
						+ nrPersonEntersVehicleEvents + ";" + nrPersonLeavesVehicleEvents);
			}

			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static int getAttributeValueOrAbsenceIndication(Person person, String attribute){
		int zzz = -1;
		Object value = person.getAttributes().getAttribute(attribute);
		if(value != null){
			if(value instanceof String){
				zzz = Integer.valueOf((String) value);
			} else {
				zzz = (int) value;
			}
		}
		return zzz;
	}


	private static class PopulationEventsHandler implements ActivityStartEventHandler, ActivityEndEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler {


		private final Population population;

		Set<Id<Person>> personsNotInPopulation = new HashSet<>();

		PopulationEventsHandler(Population population){
			this.population = population;
		}

		@Override
		public void handleEvent(ActivityEndEvent activityEndEvent) {
			if( ! this.population.getPersons().containsKey(activityEndEvent.getPersonId()) ){
				this.personsNotInPopulation.add(activityEndEvent.getPersonId());
			} else {
				Person person = this.population.getPersons().get(activityEndEvent.getPersonId());
				int zzz = 1;
				if(person.getAttributes().getAttribute("nrActivityEndEvents") == null){
					person.getAttributes().putAttribute("nrActivityEndEvents", zzz);
				} else{
					person.getAttributes().putAttribute("nrActivityEndEvents",((int) person.getAttributes().getAttribute("nrActivityEndEvents")) + zzz);
				}
			}
		}

		@Override
		public void handleEvent(ActivityStartEvent activityStartEvent) {
			if( ! this.population.getPersons().containsKey(activityStartEvent.getPersonId()) ){
				this.personsNotInPopulation.add(activityStartEvent.getPersonId());
			} else {
				Person person = this.population.getPersons().get(activityStartEvent.getPersonId());
				int zzz = 1;
				if (person.getAttributes().getAttribute("nrActivityStartEvents") == null) {
					person.getAttributes().putAttribute("nrActivityStartEvents", zzz);
				} else {
					person.getAttributes().putAttribute("nrActivityStartEvents", ((int) person.getAttributes().getAttribute("nrActivityStartEvents")) + zzz);
				}
			}
		}

		@Override
		public void handleEvent(PersonEntersVehicleEvent personEntersVehicleEvent) {
			if( ! this.population.getPersons().containsKey(personEntersVehicleEvent.getPersonId()) ){
				this.personsNotInPopulation.add(personEntersVehicleEvent.getPersonId());
			} else {
				Person person = this.population.getPersons().get(personEntersVehicleEvent.getPersonId());
				int zzz = 1;
				if (person.getAttributes().getAttribute("nrPersonEntersVehicleEvents") == null) {
					person.getAttributes().putAttribute("nrPersonEntersVehicleEvents", zzz);
				} else {
					person.getAttributes().putAttribute("nrPersonEntersVehicleEvents", ((int) person.getAttributes().getAttribute("nrPersonEntersVehicleEvents")) + zzz);
				}
			}
		}

		@Override
		public void handleEvent(PersonLeavesVehicleEvent personLeavesVehicleEvent) {
			if( ! this.population.getPersons().containsKey(personLeavesVehicleEvent.getPersonId()) ){
				this.personsNotInPopulation.add(personLeavesVehicleEvent.getPersonId());
			} else {
				Person person = this.population.getPersons().get(personLeavesVehicleEvent.getPersonId());
				int zzz = 1;
				if (person.getAttributes().getAttribute("nrPersonLeavesVehicleEvents") == null) {
					person.getAttributes().putAttribute("nrPersonLeavesVehicleEvents", zzz);
				} else {
					person.getAttributes().putAttribute("nrPersonLeavesVehicleEvents", ((int) person.getAttributes().getAttribute("nrPersonLeavesVehicleEvents")) + zzz);
				}
			}
		}
	}
}
