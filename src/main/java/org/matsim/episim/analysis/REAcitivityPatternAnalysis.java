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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;

/**
 * Class searches all different activity patter iin the events and writes an
 * output file with the number of persons having each patter
 * 
 * @author rewert
 */

public class REAcitivityPatternAnalysis {

	private final static Map<String, String> activityPatternPerPerson = new HashMap<>();
	private static Population population;

	public static void main(String[] args) {

		String inputFileEvents = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_episim_events_wt_25pt_split.xml.gz";
		String inputFilePop = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz";
//		String inputFile = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_100pt.xml.gz";

		population = PopulationUtils.readPopulation(inputFilePop);

		EventsManager events = EventsUtils.createEventsManager();

		REActivityPatternEventHandler reActivityPatternEventHandler = new REActivityPatternEventHandler();

		events.addHandler(reActivityPatternEventHandler);

		MatsimEventsReader reader = new MatsimEventsReader(events);

		reader.readFile(inputFileEvents);

		Map<String, List<String>> numberOfDifferentPattern = new HashMap<>();
		for (String personID : activityPatternPerPerson.keySet()) {
			List<String> personsWithThisPatter = new ArrayList<String>();

			if (!numberOfDifferentPattern.containsKey(activityPatternPerPerson.get(personID))) {
				personsWithThisPatter.add(personID);
				numberOfDifferentPattern.put(activityPatternPerPerson.get(personID), personsWithThisPatter);
			} else {
				List<String> newList = numberOfDifferentPattern.get(activityPatternPerPerson.get(personID));
				newList.add(personID);
				numberOfDifferentPattern.put(activityPatternPerPerson.get(personID), newList);
			}
		}

		BufferedWriter writer = IOUtils.getBufferedWriter("output/patternAnalysis.csv");
		try {
			writer.write("patter;number\n");
			for (String patter : numberOfDifferentPattern.keySet()) {
				writer.write(patter + ";" + numberOfDifferentPattern.get(patter).size() + "\n");
			}
			writer.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static Population getPopulation() {
		return population;
	}

	static Map<String, String> getPatternMap() {
		return activityPatternPerPerson;
	}
}