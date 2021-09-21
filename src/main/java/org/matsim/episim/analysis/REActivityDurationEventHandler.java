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
import java.util.function.Function;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.gbl.Gbl;

/**
 * EventHanlder calculating the number of persons for one activityType and the
 * average duration of each person in each type
 * 
 * @author rewert
 */

class REActivityDurationEventHandler implements ActivityStartEventHandler, ActivityEndEventHandler {

	private final Map<String, HashMap<String, Double>> activityStartMap;
	private final Map<String, HashMap<String, Double>> activityEndMap;
	REActivityDurationEventHandler( Map<String, HashMap<String, Double>> activityStartMap, Map<String, HashMap<String, Double>> activityEndMap ){
		this.activityStartMap = activityStartMap;
		this.activityEndMap = activityEndMap;
	}
	// counts the persons in each acticityType and safes the time of the
	// startActivity
	@Override
	public void handleEvent(ActivityStartEvent event) {
		final Id<Person> personId1 = event.getPersonId();
		if( !personIsInRegionOfInterest( personId1 ) ){
			return;
		}

		HashMap<String, Double> personSetStart = activityStartMap.computeIfAbsent( event.getActType(), k -> new HashMap<>() );

		String personId = personId1.toString();
		if (!personSetStart.containsKey(personId)) {
			personSetStart.put(personId, event.getTime());
		} else{
			Gbl.assertIf(!personSetStart.containsKey( personId + "_1" ) );
			personSetStart.put( personId + "_1", event.getTime() );
			// In general, a person should have finished the previous activity before starting the next one.  So I think that this can
			// only happen with the "overnight" messiness.  If at all.  kai, sep'21
		}
	}
	/*
	 * -calculates the duration of one activity - if one person has one activity
	 * more then one time a day, it calculates the sum of the different times
	 */
	@Override
	public void handleEvent(ActivityEndEvent event) {
		final Id<Person> personId1 = event.getPersonId();
		if( !personIsInRegionOfInterest( personId1 ) ){
			return;
		}

		HashMap<String, Double> personMapEnd = activityEndMap.computeIfAbsent( event.getActType(), k -> new HashMap<>() );

		String personId = personId1.toString();

		HashMap<String, Double> personMapStart = activityStartMap.get( event.getActType() );

		// if no startAcitivity take place. Assume midnight as startTime

		if (personMapStart != null && !personMapStart.containsKey(personId)) {
			personMapEnd.put(personId, event.getTime());
			activityEndMap.put( event.getActType(), personMapEnd );
		} else {

			double durationBefore;
			if (personMapEnd == null || personMapEnd.get(personId) == null)
				durationBefore = 0;
			else
				durationBefore = personMapEnd.get(personId);

			double startTime;
			if (personMapStart == null){
				startTime = 0;
			} else {
				startTime = personMapStart.get(personId);
				personMapStart.remove(personId);
			}
			double duration = event.getTime() - startTime;

			//change personId if you want to analyze all activities without analyze for summarized result for each person
			if ( personMapEnd.containsKey(personId ) ) {
				personMapEnd.replace(personId, durationBefore + duration);
//				while (personMapEnd.containsKey(personId)){
//					personId = personId + "_1";
//				}
//				personMapEnd.put(personId, duration);
			} else{
				personMapEnd.put( personId, duration );
			}
			activityEndMap.put( event.getActType(), personMapEnd );
		}
	}

	private static boolean personIsInRegionOfInterest( Id<Person> personId1 ){
		return REAcitivityDurationAnalysis.getPopulation().getPersons().get( personId1 ).getAttributes().getAttribute( "district" ) != null
				       && REAcitivityDurationAnalysis.getPopulation().getPersons().get( personId1 ).getAttributes().getAttribute( "district" ).toString().equals( "Berlin" );
	}

}
