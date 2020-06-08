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

import java.util.HashSet;
import java.util.Map;

import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;

/**
* @author smueller
*/

public class SMFacilitiesEventHandler implements ActivityStartEventHandler {
	

	@Override
	public void handleEvent(ActivityStartEvent event) {
		Map<String, HashSet<String>> faciltiesMap = SMFacilitiesAnalysis.getFaciltiesMap();
		
		String facId = event.getActType() + ";" + event.getFacilityId().toString();
		if (!faciltiesMap.containsKey(facId)) {
			HashSet<String> personSet = new HashSet<>();
			faciltiesMap.put(facId, personSet);
		}
		String personId = event.getPersonId().toString();
		HashSet<String> personSet = faciltiesMap.get(facId);
		if (!personSet.contains(personId)) {
			personSet.add(personId);
		}
	}


}

