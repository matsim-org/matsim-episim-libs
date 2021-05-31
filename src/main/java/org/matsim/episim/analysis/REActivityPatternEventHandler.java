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

import java.util.Map;

import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;

/**
 * EventHanlder searching all different activity patter and the related persons 
 * 
 * @author rewert
 */

public class REActivityPatternEventHandler implements ActivityStartEventHandler, ActivityEndEventHandler {

	// counts the persons in each acticityType and safes the time of the
	// startActivity
	@Override
	public void handleEvent(ActivityStartEvent event) {
		if (REAcitivityPatternAnalysis.getPopulation().getPersons().get(event.getPersonId()).getAttributes()
				.getAttribute("district") != null
				&& REAcitivityPatternAnalysis.getPopulation().getPersons().get(event.getPersonId()).getAttributes()
						.getAttribute("district").toString().equals("Berlin")) {
			Map<String, String> activityPatternMap = REAcitivityPatternAnalysis.getPatternMap();

			String personId = event.getPersonId().toString();
			if (!activityPatternMap.containsKey(personId))
				activityPatternMap.put(personId, event.getActType().toString());
			else {
				String newPattern = activityPatternMap.get(personId) + "-" + event.getActType().toString();
				activityPatternMap.put(personId, newPattern);
			}
		}
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		if (REAcitivityPatternAnalysis.getPopulation().getPersons().get(event.getPersonId()).getAttributes()
				.getAttribute("district") != null
				&& REAcitivityPatternAnalysis.getPopulation().getPersons().get(event.getPersonId()).getAttributes()
						.getAttribute("district").toString().equals("Berlin")) {
			
			Map<String, String> activityPatternMap = REAcitivityPatternAnalysis.getPatternMap();
			String personId = event.getPersonId().toString();
			if (!activityPatternMap.containsKey(personId))
				activityPatternMap.put(personId, event.getActType().toString());
		}
		
	}

	/*
	 * -calculates the duration of one activity - if one person has one activity
	 * more then one time a day, it calculates the sum of the different times
	 */
//	@Override
//	public void handleEvent(ActivityEndEvent event) {
//		if (REAcitivityDurationAnalysis.getPopulation().getPersons().get(event.getPersonId()).getAttributes()
//				.getAttribute("district") != null
//				&& REAcitivityDurationAnalysis.getPopulation().getPersons().get(event.getPersonId()).getAttributes()
//						.getAttribute("district").toString().equals("Berlin")) {
//			Map<String, HashMap<String, Double>> activityEndMap = REAcitivityDurationAnalysis.getEndMap();
//			Map<String, HashMap<String, Double>> activityStartMap = REAcitivityDurationAnalysis.getStartMap();
//
//			if (!activityEndMap.containsKey(event.getActType().toString())) {
//				HashMap<String, Double> personSetEnd = new HashMap<>();
//				activityEndMap.put(event.getActType().toString(), personSetEnd);
//			}
//			String personId = event.getPersonId().toString();
//			HashMap<String, Double> personSetStart = activityStartMap.get(event.getActType().toString());
//			HashMap<String, Double> personSetEnd = activityEndMap.get(event.getActType().toString());
//
//			// if no startAcitivity take place. Assume midnight as startTime
//			if (personSetStart != null && !personSetStart.containsKey(personId)) {
//				personSetEnd.put(personId, event.getTime());
//				activityEndMap.put(event.getActType().toString(), personSetEnd);
//			} else {
//				double durationBefore;
//				if (personSetEnd == null || personSetEnd.get(personId) == null)
//					durationBefore = 0;
//				else
//					durationBefore = personSetEnd.get(personId);
//				double startTime;
//				if (personSetStart == null)
//					startTime = 0;
//				else {
//					startTime = personSetStart.get(personId);
//					personSetStart.remove(personId);
//				}
//				double duration = event.getTime() - startTime;
//
//				// change personId if you want to analyze all activities without analyze for
//				// summarized result for each person
//				if (personSetEnd != null && personSetEnd.containsKey(personId)) {
////					personSetEnd.replace(personId, durationBefore + duration);
//					while (personSetEnd.containsKey(personId))
//						personId = personId + "_1";
//					personSetEnd.put(personId, duration);
//				} else
//					personSetEnd.put(personId, duration);
//
//				activityEndMap.put(event.getActType().toString(), personSetEnd);
//			}
//		}
//	}
}
