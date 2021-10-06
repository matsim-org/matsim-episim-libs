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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.population.PopulationUtils;

/**
 * Class counts the persons in the events starting and ending an activity type
 * and calculates the average of the duration of for each type.
 * 
 * @author rewert
 */

public class REAcitivityDurationAnalysis {

	private static Population population;

	public static void main(String[] args) {

		String inputFileEvents = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_episim_events_wt_25pt_split.xml.gz";
		String inputFilePop = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz";
//		String inputFile = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_100pt.xml.gz";

		final Map<String, HashMap<String, Double>> activityStartMap = new HashMap<>();
		final Map<String, HashMap<String, Double>> activityDurationMap = new HashMap<>();

		population = PopulationUtils.readPopulation(inputFilePop);
		int countPersons = 0;
		Set<String> allPersons = new HashSet<>();
		for (Person person : population.getPersons().values()) {
			if ( REActivityDurationEventHandler.personIsOfInterest( person.getId() ) ) {
				countPersons++;
				allPersons.add( person.getId().toString() );
			}
		}
		
		EventsManager events = EventsUtils.createEventsManager();

		REActivityDurationEventHandler reActivityDurationEventHandler = new REActivityDurationEventHandler(activityStartMap, activityDurationMap );

		events.addHandler(reActivityDurationEventHandler);

		MatsimEventsReader reader = new MatsimEventsReader(events);

		reader.readFile(inputFileEvents);

		// collect IDs of all mobile persons:
		Set<String> mobilePersons = new HashSet<>();
		for (String activityType : activityDurationMap.keySet()){
			mobilePersons.addAll( activityDurationMap.get( activityType ).keySet() );
		}
		// remove IDs of mobile persons from IDs of all persons ... IDs of not mobile persons remain:
		allPersons.removeAll( mobilePersons );

		// add home activity duration for all not mobile persons:
		for( String person : allPersons ){
			HashMap<String, Double> homeMap = activityDurationMap.get( "home" );
			homeMap.put( person, 24.*3600.) ;
		}


		for (String activityType : activityDurationMap.keySet()) {
			double sumDurations = 0;
			for (String personId : activityDurationMap.get(activityType ).keySet()) {

				/*
				 * if activities has started but not finished, the end of the day will be used
				 * as the endTime
				 */
				if (activityStartMap.get(activityType).containsKey(personId)) {
					sumDurations = sumDurations + activityDurationMap.get(activityType ).get(personId ) + (86400. - activityStartMap.get(activityType ).get(personId ));
				} else {
					sumDurations = sumDurations + activityDurationMap.get( activityType ).get( personId );
				}
			}
			System.out.println(activityType + ";" + activityDurationMap.get(activityType ).size() * 4 + ";" + (sumDurations / activityDurationMap.get(activityType ).size() / 3600) );
		}

		System.out.println("Number of persons in population in Berlin; " + countPersons * 4);
		System.out.println("Number of mobile persons in population in Berlin; " + mobilePersons.size() * 4);

	}

	static Population getPopulation() {
		return population;
	}
//	static Map<String, HashMap<String, Double>> getStartMap() {
//		return activityStartMap;
//	}
//
//	static Map<String, HashMap<String, Double>> getEndMap() {
//		return activityEndMap;
//	}
}
