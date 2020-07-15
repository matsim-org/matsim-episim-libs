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

import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;

/**
 * EventHanlder calculating the number of persons for one activityType and the
 * average duration of each person in each type
 * 
 * @author rewert
 */

public class REActivityDurationEventHandler implements ActivityStartEventHandler, ActivityEndEventHandler {

	// counts the persons in each acticityType and safes the time of the
	// startActivity
	@Override
	public void handleEvent(ActivityStartEvent event) {
		if (REAcitivityDurationAnalysis.getPopulation().getPersons().get(event.getPersonId()).getAttributes()
				.getAttribute("district")!= null && REAcitivityDurationAnalysis.getPopulation().getPersons().get(event.getPersonId()).getAttributes()
				.getAttribute("district").toString().equals("Berlin")) {
			Map<String, HashMap<String, Double>> activityStartMap = REAcitivityDurationAnalysis.getStartMap();

			if (!activityStartMap.containsKey(event.getActType().toString())) {
				HashMap<String, Double> personSet = new HashMap<>();
				activityStartMap.put(event.getActType().toString(), personSet);
			}
			String personId = event.getPersonId().toString();
			HashMap<String, Double> personSetStart = activityStartMap.get(event.getActType().toString());
			if (!personSetStart.containsKey(personId)) {
				personSetStart.put(personId, event.getTime());
			}
		}
	}

	/*
	 * -calculates the duration of one activity - if one person has one activity
	 * more then one time a day, it calculates the sum of the different times
	 */
	@Override
	public void handleEvent(ActivityEndEvent event) {
		if (REAcitivityDurationAnalysis.getPopulation().getPersons().get(event.getPersonId()).getAttributes()
				.getAttribute("district")!= null && REAcitivityDurationAnalysis.getPopulation().getPersons().get(event.getPersonId()).getAttributes()
				.getAttribute("district").toString().equals("Berlin")) {
			Map<String, HashMap<String, Double>> activityEndMap = REAcitivityDurationAnalysis.getEndMap();
			Map<String, HashMap<String, Double>> activityStartMap = REAcitivityDurationAnalysis.getStartMap();

			if (!activityEndMap.containsKey(event.getActType().toString())) {
				HashMap<String, Double> personSetEnd = new HashMap<>();
				activityEndMap.put(event.getActType().toString(), personSetEnd);
			}
			String personId = event.getPersonId().toString();
			HashMap<String, Double> personSetStart = activityStartMap.get(event.getActType().toString());
			HashMap<String, Double> personSetEnd = activityEndMap.get(event.getActType().toString());

			// if no startAcitivity take place. Assume midnight as startTime
			if (personSetStart != null && !personSetStart.containsKey(personId)) {
				personSetEnd.put(personId, event.getTime());
				activityEndMap.put(event.getActType().toString(), personSetEnd);
			} else {
				double durationBefore;
				if (personSetEnd == null || personSetEnd.get(personId) == null)
					durationBefore = 0;
				else
					durationBefore = personSetEnd.get(personId);
				double startTime;
				if (personSetStart == null)
					startTime = 0;
				else {
					startTime = personSetStart.get(personId);
					personSetStart.remove(personId);
				}
				double duration = event.getTime() - startTime;
				if (personSetEnd != null && personSetEnd.containsKey(personId))
					personSetEnd.replace(personId, durationBefore + duration);
				else
					personSetEnd.put(personId, duration);

				activityEndMap.put(event.getActType().toString(), personSetEnd);
			}
		}
	}
}
