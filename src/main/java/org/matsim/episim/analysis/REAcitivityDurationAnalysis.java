/* *********************************************************************** *
b	 * project: org.matsim.*
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
import java.util.Map;

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

	private final static Map<String, HashMap<String, Double>> startedActivitiesMap = new HashMap<>();
	private final static Map<String, HashMap<String, Double>> finishedActivitiesMap = new HashMap<>();
	private final static Map<String, HashMap<String, Double>> personActivitiesMap = new HashMap<>();
	private final static Map<String, Integer> countActivities = new HashMap<>();
	private static Population population;

	public static void main(String[] args) {

		String inputFileEvents = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_episim_events_so_25pt_split.xml.gz";
		String inputFilePop = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz";
//		String inputFile = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_100pt.xml.gz";

		population = PopulationUtils.readPopulation(inputFilePop);

		EventsManager events = EventsUtils.createEventsManager();

		REActivityDurationEventHandler reActivityDurationEventHandler = new REActivityDurationEventHandler();

		events.addHandler(reActivityDurationEventHandler);

		MatsimEventsReader reader = new MatsimEventsReader(events);

		reader.readFile(inputFileEvents);

		// if activity has not finished until 2 am they will finished at 2 am
		for (String activityType : startedActivitiesMap.keySet()) {
			for (String personId : startedActivitiesMap.get(activityType).keySet()) {
				countActivities.replace(activityType, countActivities.get(activityType) + 1);
				if (finishedActivitiesMap.get(activityType).containsKey(personId)) {
					double durationBefore = finishedActivitiesMap.get(activityType).get(personId);
					double additiotionalDuration = 93600. - startedActivitiesMap.get(activityType).get(personId);
					finishedActivitiesMap.get(activityType).replace(personId, durationBefore + additiotionalDuration);
					personActivitiesMap.get(personId).replace(activityType, durationBefore + additiotionalDuration);
				} else {
					double additiotionalDuration = 93600. - startedActivitiesMap.get(activityType).get(personId);
					finishedActivitiesMap.get(activityType).put(personId, additiotionalDuration);
					personActivitiesMap.get(personId).put(activityType, additiotionalDuration);
				}
			}
		}

		for (String personID : personActivitiesMap.keySet()) {
			double sumAct = 0;
			for (double sum : personActivitiesMap.get(personID).values())
				sumAct = sumAct + sum;
			if (sumAct != 86400.) {
				throw new RuntimeException("Day of Person has not 24h");
			}
		}
		int countPersons = 0;
		for (Person person : population.getPersons().values()) {
			if (person.getAttributes().getAttribute("district") != null
					&& person.getAttributes().getAttribute("district").toString().equals("Berlin"))
				countPersons++;
			else
				continue;

			// checks if a person had no activities and add a 24h home activity
			boolean containsPerson = false;
			if (!countActivities.containsKey("onlyHome"))
				countActivities.put("onlyHome", 0);
			for (HashMap<String, Double> personEndSet : finishedActivitiesMap.values())
				if (personEndSet.containsKey(person.getId().toString()))
					containsPerson = true;
			if (!containsPerson) {
				countActivities.replace("onlyHome", countActivities.get("onlyHome") + 1);
				if (!finishedActivitiesMap.containsKey("onlyHome")) {
					HashMap<String, Double> personSet = new HashMap<>();
					finishedActivitiesMap.put("onlyHome", personSet);
				}
				String personId = person.getId().toString();
				HashMap<String, Double> personSetEnd = finishedActivitiesMap.get("onlyHome");
				double fullDayTime = 86400.;
				personSetEnd.put(personId, fullDayTime);
			}
		}
		System.out.println(
				"Aktivität;Anzahl Personen;Mittlere Dauer pro Person mit dieser Aktivität;Mittlere Dauer Population;Anzahl Aktivitäten;Mittlere Dauer pro Aktivität");
		for (String activityType : finishedActivitiesMap.keySet()) {
			double sumDurations = 0;
			for (String personId : finishedActivitiesMap.get(activityType).keySet()) {
				sumDurations = sumDurations + finishedActivitiesMap.get(activityType).get(personId);
			}
			System.out.println(activityType + ";" + finishedActivitiesMap.get(activityType).size() * 4 + ";"
					+ (sumDurations / finishedActivitiesMap.get(activityType).size() / 3600) + ";"
					+ (finishedActivitiesMap.get(activityType).size()
							* (sumDurations / finishedActivitiesMap.get(activityType).size() / 3600) / countPersons)
					+ ";" + countActivities.get(activityType) * 4 + ";"
					+ (sumDurations / countActivities.get(activityType) / 3600));
		}
		System.out.println("Number of persons in population in Berlin: " + countPersons * 4);
	}

	static Population getPopulation() {
		return population;
	}

	static Map<String, HashMap<String, Double>> getStartMap() {
		return startedActivitiesMap;
	}

	static Map<String, HashMap<String, Double>> getEndMap() {
		return finishedActivitiesMap;
	}

	public static Map<String, HashMap<String, Double>> getPersonactivitiesMap() {
		return personActivitiesMap;
	}

	public static Map<String, Integer> getCountActivities() {
		return countActivities;
	}
}