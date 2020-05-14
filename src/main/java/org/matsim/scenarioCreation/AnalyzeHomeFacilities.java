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
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AnalyzeHomeFacilities {

	private static final String INPUT_POPULATION = "D:/svn/shared-svn/projects/episim/matsim-files/snz/Berlin/episim-input/be_entirePopulation_noPlans.xml.gz";
	private static final String OUTPUT_CSV = "D:/svn/shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_entirePopulation_noPlans_homeIdCounts.csv";
	private static final String OUTPUT_FACILITIES = "D:/svn/shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_entirePopulation_noPlans_homeFacilities.xml";

	private AnalyzeHomeFacilities() {
	}

	public static void main(String[] args) {

		Population population = PopulationUtils.readPopulation(INPUT_POPULATION);
		Map<String, List<Person>> map = mapPersonsToHomeId(population);

		BufferedWriter writer = IOUtils.getBufferedWriter(OUTPUT_CSV);

		ActivityFacilities facilities = FacilitiesUtils.createActivityFacilities();
		ActivityFacilitiesFactory factory = facilities.getFactory();

		try {
			writer.write("homeId;0-2;2-14;15-18;19-63;63-80;over80");
			for (String home : map.keySet()) {
				List<Person> inhabitants = map.get(home);

				double x = (double) inhabitants.get(0).getAttributes().getAttribute("homeX");
				double y = (double) inhabitants.get(0).getAttributes().getAttribute("homeY");

				facilities.addActivityFacility(factory.createActivityFacility(Id.create(home, ActivityFacility.class), CoordUtils.createCoord(x,y)));

				writer.newLine();
				long nrClass1 = inhabitants.stream().map(person -> (int) person.getAttributes().getAttribute("age")).filter(age -> age <= 2).count();
				long nrClass2 = inhabitants.stream().map(person -> (int) person.getAttributes().getAttribute("age")).filter(age -> age > 2 && age <= 14).count();
				long nrClass3 = inhabitants.stream().map(person -> (int) person.getAttributes().getAttribute("age")).filter(age -> age > 14 && age <= 18).count();
				long nrClass4 = inhabitants.stream().map(person -> (int) person.getAttributes().getAttribute("age")).filter(age -> age > 18 && age <= 63).count();
				long nrClass5 = inhabitants.stream().map(person -> (int) person.getAttributes().getAttribute("age")).filter(age -> age > 63 && age <= 80).count();
				long nrClass6 = inhabitants.stream().map(person -> (int) person.getAttributes().getAttribute("age")).filter(age -> age > 80).count();

				writer.write(home + ";" + nrClass1 + ";" + nrClass2 + ";" + nrClass3 + ";" + nrClass4 + ";" + nrClass5 + ";" + nrClass6);
			}
			writer.close();

			new FacilitiesWriter(facilities).write(OUTPUT_FACILITIES);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}



	private static Map<String, List<Person>> mapPersonsToHomeId(Population childrenPopulation) {
		Map<String, List<Person>> homeIdToPersons = new HashMap<>();

		for (Person person : childrenPopulation.getPersons().values()) {
			String homeIdStr = person.getAttributes().getAttribute("homeId").toString();
			if (homeIdToPersons.containsKey(homeIdStr)) {
				homeIdToPersons.get(homeIdStr).add(person);
			} else {
				List<Person> list = new ArrayList<>();
				list.add(person);
				homeIdToPersons.put(homeIdStr, list);
			}
		}
		return homeIdToPersons;
	}


}
