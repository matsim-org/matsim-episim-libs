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

import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;

/**
* @author smueller
*/


public class SMFacilitiesAnalysis {
	
	private final static Map<String, HashSet<String>> facilitiesMap = new HashMap<>();

	
	public static void main(String[] args) {

		String inputFile = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_25pt.xml.gz";
//		String inputFile = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_2020_snz_episim_events_100pt.xml.gz";
		
		EventsManager events = EventsUtils.createEventsManager();

		SMFacilitiesEventHandler smFacilitiesEventHandler = new SMFacilitiesEventHandler();
		
		events.addHandler(smFacilitiesEventHandler);
		
		MatsimEventsReader reader = new MatsimEventsReader(events);
		
		reader.readFile(inputFile);
		
		for (String a : facilitiesMap.keySet()) {
			System.out.println(a + ";" + facilitiesMap.get(a).size());	
		}

	}
	
	static Map<String, HashSet<String>> getFaciltiesMap() {
		return facilitiesMap;
	}

}