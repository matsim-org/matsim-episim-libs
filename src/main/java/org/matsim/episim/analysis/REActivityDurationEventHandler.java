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

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.population.Population;

/**
 * EventHanlder calculating the number of persons for one activityType and the
 * average duration of each person in each type
 *
 * @author rewert
 */

public class REActivityDurationEventHandler implements ActivityStartEventHandler, ActivityEndEventHandler,
		PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler {

	private final Population population;
	private final Map<String, Object2DoubleMap<String>> startedActivitiesMap;
	private final Map<String, Object2DoubleMap<String>> finishedActivitiesMap;
	private final Map<String, Object2DoubleMap<String>> personActivitiesMap;
	private final Object2IntMap<String> countActivities;
	private final String district;

	public REActivityDurationEventHandler(Population population, Map<String, Object2DoubleMap<String>> startedActivitiesMap, Map<String, Object2DoubleMap<String>> finishedActivitiesMap, Map<String, Object2DoubleMap<String>> personActivitiesMap, Object2IntMap<String> countActivities, String district) {
		this.population = population;
		this.startedActivitiesMap = startedActivitiesMap;
		this.finishedActivitiesMap = finishedActivitiesMap;
		this.personActivitiesMap = personActivitiesMap;
		this.countActivities = countActivities;
		this.district = district;
	}

	// counts the persons in each acticityType and safes the time of the
	// startActivity
	@Override
	public void handleEvent(ActivityStartEvent event) {
		// events begins at 2 am and finishes at 6am next day
		if (event.getTime() <= 93600.
				&& population.getPersons().get(event.getPersonId()).getAttributes()
						.getAttribute("district") != null
								&& population.getPersons().get(event.getPersonId()).getAttributes()
						.getAttribute("district").toString().equals(district)) {

			if (!finishedActivitiesMap.containsKey("travel_noPT")) {
				Object2DoubleMap<String> personSetEnd = new Object2DoubleOpenHashMap<>();
				finishedActivitiesMap.put("travel_noPT", personSetEnd);
			}

			if (!startedActivitiesMap.containsKey(event.getActType().toString())) {
				Object2DoubleMap<String> personSet = new Object2DoubleOpenHashMap<>();
				startedActivitiesMap.put(event.getActType().toString(), personSet);
			}

			if (!personActivitiesMap.containsKey(event.getPersonId().toString())) {
				Object2DoubleMap<String> actPerPerson = new Object2DoubleOpenHashMap<>();
				personActivitiesMap.put(event.getPersonId().toString(), actPerPerson);
			}

			String personId = event.getPersonId().toString();
			Object2DoubleMap<String> personSetStart = startedActivitiesMap.get(event.getActType().toString());
			personSetStart.put(personId, event.getTime());

			Object2DoubleMap<String> personSetStartTravel = startedActivitiesMap.get("travel_noPT");
			Object2DoubleMap<String> personSetEndTravel = finishedActivitiesMap.get("travel_noPT");

			//finish travel activity because a new activity has started
			if (personSetStartTravel != null && !personSetStartTravel.containsKey(personId)) {
				personSetEndTravel.put(personId, event.getTime() - 7200.);
				Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
				newTime.put("travel_noPT", event.getTime() - 7200.);
			} else {
				double durationBefore;
				if (personSetEndTravel == null || personSetEndTravel.get(personId) == null)
					durationBefore = 0;
				else
					durationBefore = personSetEndTravel.get(personId);
				double startTime;
				if (personSetStartTravel.get(personId) == null)
					startTime = 7200.;
				else {
					startTime = personSetStartTravel.get(personId);
					personSetStartTravel.remove(personId);
				}
				double duration = event.getTime() - startTime;

				if (personSetEndTravel != null && personSetEndTravel.containsKey(personId)) {
					personSetEndTravel.replace(personId, durationBefore + duration);
					Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
					newTime.replace("travel_noPT", durationBefore + duration);
				} else {
					personSetEndTravel.put(personId, duration);
					Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
					newTime.put("travel_noPT", duration);
				}
			}
		}
	}
	/*
	 * -calculates the duration of one activity - if one person has one activity
	 * more then one time a day, it calculates the sum of the different times
	 */
	@Override
	public void handleEvent(ActivityEndEvent event) {
		if (event.getTime() <= 93600.
				&& population.getPersons().get(event.getPersonId()).getAttributes()
						.getAttribute("district") != null
								&& population.getPersons().get(event.getPersonId()).getAttributes()
						.getAttribute("district").toString().equals(district)) {


			if (!finishedActivitiesMap.containsKey(event.getActType().toString())) {
				Object2DoubleMap<String> personSetEnd = new Object2DoubleOpenHashMap<>();
				finishedActivitiesMap.put(event.getActType().toString(), personSetEnd);
				countActivities.put(event.getActType().toString(), 0);
			}
			if (!startedActivitiesMap.containsKey("travel_noPT")) {
				Object2DoubleMap<String> personSetStart = new Object2DoubleOpenHashMap<>();
				startedActivitiesMap.put("travel_noPT", personSetStart);
			}
			if (!personActivitiesMap.containsKey(event.getPersonId().toString())) {
				Object2DoubleMap<String> actPerPerson = new Object2DoubleOpenHashMap<>();
				personActivitiesMap.put(event.getPersonId().toString(), actPerPerson);
			}
			if (!countActivities.containsKey("travel_noPT"))
				countActivities.put("travel_noPT", 0);

			String personId = event.getPersonId().toString();
			Object2DoubleMap<String> personSetStart = startedActivitiesMap.get(event.getActType().toString());
			Object2DoubleMap<String> personSetEnd = finishedActivitiesMap.get(event.getActType().toString());
			Object2DoubleMap<String> personSetStartTravel = startedActivitiesMap.get("travel_noPT");

			// takes end of act as a beginning of traveling
			personSetStartTravel.put(personId, event.getTime());
			countActivities.replace("travel_noPT", countActivities.get("travel_noPT")+1);

			// if no startAcitivity take place. Assume 2am as startTime
			countActivities.replace(event.getActType().toString(), countActivities.get(event.getActType().toString())+1);
			if (personSetStart != null && !personSetStart.containsKey(personId)) {
				personSetEnd.put(personId, event.getTime() - 7200.);
				Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
				newTime.put(event.getActType().toString(), event.getTime() - 7200.);
			} else {
				double durationBefore;
				if (personSetEnd == null || personSetEnd.get(personId) == null)
					durationBefore = 0;
				else
					durationBefore = personSetEnd.get(personId);
				double startTime;
				if (personSetStart == null)
					startTime = 7200.;
				else {
					startTime = personSetStart.get(personId);
					personSetStart.remove(personId);
				}
				double duration = event.getTime() - startTime;
				if (personSetEnd != null && personSetEnd.containsKey(personId)) {
					personSetEnd.replace(personId, durationBefore + duration);
					Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
					newTime.replace(event.getActType().toString(), durationBefore + duration);
				} else {
					personSetEnd.put(personId, duration);
					Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
					newTime.put(event.getActType().toString(), duration);
				}
			}
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (event.getTime() <= 93600.
				&& population.getPersons().get(event.getPersonId()).getAttributes()
						.getAttribute("district") != null
				&& population.getPersons().get(event.getPersonId()).getAttributes()
						.getAttribute("district").toString().equals(district)) {

			if (!startedActivitiesMap.containsKey("pt")) {
				Object2DoubleMap<String> personSet = new Object2DoubleOpenHashMap<>();
				startedActivitiesMap.put("pt", personSet);
			}
			if (!finishedActivitiesMap.containsKey("travel_noPT")) {
				Object2DoubleMap<String> personSetEnd = new Object2DoubleOpenHashMap<>();
				finishedActivitiesMap.put("travel_noPT", personSetEnd);
			}
			if (!personActivitiesMap.containsKey(event.getPersonId().toString())) {
				Object2DoubleMap<String> actPerPerson = new Object2DoubleOpenHashMap<>();
				personActivitiesMap.put(event.getPersonId().toString(), actPerPerson);
			}
			String personId = event.getPersonId().toString();
			Object2DoubleMap<String> personSetStart = startedActivitiesMap.get("pt");
			personSetStart.put(personId, event.getTime());

			Object2DoubleMap<String> personSetStartTravel = startedActivitiesMap.get("travel_noPT");
			Object2DoubleMap<String> personSetEndTravel = finishedActivitiesMap.get("travel_noPT");

			//finish travel activity because a new activity has started
			if (personSetStartTravel != null && !personSetStartTravel.containsKey(personId)) {
				personSetEndTravel.put(personId, event.getTime() - 7200.);
				Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
				newTime.put("travel_noPT", event.getTime() - 7200.);
			} else {
				double durationBefore;
				if (personSetEndTravel == null || personSetEndTravel.get(personId) == null)
					durationBefore = 0;
				else
					durationBefore = personSetEndTravel.get(personId);
				double startTime;
				if (personSetStartTravel.get(personId) == null)
					startTime = 7200.;
				else {
					startTime = personSetStartTravel.get(personId);
					personSetStartTravel.remove(personId);
				}
				double duration = event.getTime() - startTime;
				if (personSetEndTravel != null && personSetEndTravel.containsKey(personId)) {
					personSetEndTravel.replace(personId, durationBefore + duration);
					Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
					newTime.replace("travel_noPT", durationBefore + duration);
				} else {
					personSetEndTravel.put(personId, duration);
					Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
					newTime.put("travel_noPT", duration);
				}
			}
		}
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if (event.getTime() <= 93600.
				&& population.getPersons().get(event.getPersonId()).getAttributes()
						.getAttribute("district") != null
				&& population.getPersons().get(event.getPersonId()).getAttributes()
						.getAttribute("district").toString().equals(district)) {

			if (!finishedActivitiesMap.containsKey("pt")) {
				Object2DoubleMap<String> personSetEnd = new Object2DoubleOpenHashMap<>();
				finishedActivitiesMap.put("pt", personSetEnd);
				countActivities.put("pt", 0);
			}
			if (!personActivitiesMap.containsKey(event.getPersonId().toString())) {
				Object2DoubleMap<String> actPerPerson = new Object2DoubleOpenHashMap<>();
				personActivitiesMap.put(event.getPersonId().toString(), actPerPerson);
			}
			if (!countActivities.containsKey("travel_noPT"))
				countActivities.put("travel_noPT", 0);

			String personId = event.getPersonId().toString();
			Object2DoubleMap<String> personSetStart = startedActivitiesMap.get("pt");
			Object2DoubleMap<String> personSetEnd = finishedActivitiesMap.get("pt");
			Object2DoubleMap<String> personSetStartTravel = startedActivitiesMap.get("travel_noPT");

			// takes end of act as a beginning of traveling
			personSetStartTravel.put(personId, event.getTime());
			countActivities.replace("travel_noPT", countActivities.get("travel_noPT")+1);

			// if no startAcitivity take place. Assume 2am as startTime
			countActivities.replace("pt", countActivities.get("pt")+1);
			if (personSetStart != null && !personSetStart.containsKey(personId)) {
				personSetEnd.put(personId, event.getTime() - 7200.);
				Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
				newTime.put("pt", event.getTime() - 7200.);
			} else {
				double durationBefore;
				if (personSetEnd == null || personSetEnd.get(personId) == null)
					durationBefore = 0;
				else
					durationBefore = personSetEnd.get(personId);
				double startTime;
				if (personSetStart == null)
					startTime = 7200.;
				else {
					startTime = personSetStart.get(personId);
					personSetStart.remove(personId);
				}
				double duration = event.getTime() - startTime;

				// change personId if you want to analyze all activities without analyze for
				// summarized result for each person
				if (personSetEnd != null && personSetEnd.containsKey(personId)) {
					personSetEnd.replace(personId, durationBefore + duration);
					Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
					newTime.replace("pt", durationBefore + duration);
				} else {
					personSetEnd.put(personId, duration);
					Object2DoubleMap<String> newTime = personActivitiesMap.get(personId);
					newTime.put("pt", duration);
				}
			}
		}
	}
}
