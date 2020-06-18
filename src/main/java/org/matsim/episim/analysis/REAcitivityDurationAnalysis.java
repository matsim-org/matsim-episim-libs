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
import java.util.Map;

import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;

/**
 * Class counts the persons in the events starting and ending an activity type
 * and calculates the average of the duration of for each type.
 * 
 * @author rewert
 */

public class REAcitivityDurationAnalysis {

	private final static Map<String, HashMap<String, Double>> activityStartMap = new HashMap<>();
	private final static Map<String, HashMap<String, Double>> activityEndMap = new HashMap<>();

	public static void main(String[] args) {

		String inputFile = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_25pt.xml.gz";
//		String inputFile = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_100pt.xml.gz";

		EventsManager events = EventsUtils.createEventsManager();

		REActivityDurationEventHandler reActivityDurationEventHandler = new REActivityDurationEventHandler();

		events.addHandler(reActivityDurationEventHandler);

		MatsimEventsReader reader = new MatsimEventsReader(events);

		reader.readFile(inputFile);

		for (String a : activityStartMap.keySet()) {
			System.out.println(a + ";" + activityStartMap.get(a).size() * 4);
		}
		System.out.println("");
		for (String a : activityEndMap.keySet()) {
			double sumDurations = 0;
			for (String personId : activityEndMap.get(a).keySet()) {
				//if activities has started but not finished, the end of the day will be used as the endTime
				if (activityStartMap.get(a).containsKey(personId)) {
					sumDurations = sumDurations + activityEndMap.get(a).get(personId) + (86400.
							- activityStartMap.get(a).get(personId));
				} else
					sumDurations = sumDurations + activityEndMap.get(a).get(personId);
			}
			System.out.println(a + ";" + activityEndMap.get(a).size() * 4 + ";"
					+ (sumDurations / activityEndMap.get(a).size() / 3600));
		}

	}

	static Map<String, HashMap<String, Double>> getStartMap() {
		return activityStartMap;
	}

	static Map<String, HashMap<String, Double>> getEndMap() {
		return activityEndMap;
	}
}