/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.scenarioCreation;

import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.ParallelEventsManagerImpl;

/**
 *
 * This class aims to account for facility aggregation in the matsim events files. Previously, facilities were aggregated (merged), but the events file
 * still contains all facilities.
 *
 * INPUT:	(1) The facilities file containing all remaining facilities (which also hold information about the merged/deleted ones)
 * 			(2) the events file to alter
 *
 * OUTPUT: 		the events file with altered facilities. For each deleted facility, the id is set to the facility representing the old one.
 */
public class AggregateFacilitiesInEvents {

	private static final String INPUT_MERGED_FACILITIES = "";
	private static final String INPUT_EVENTS_CONTAINING_ALL_FACILITIES = "";
	private static final String OUTPUT_EVENTS_CONTAINING_ONLY_MERGED_FACILITIES = "";


	public static void main(String[] args) {



		EventsManager eventsManager = new ParallelEventsManagerImpl(4);
		MatsimEventsReader eventsReader = new MatsimEventsReader(eventsManager);

		eventsReader.readFile(INPUT_EVENTS_CONTAINING_ALL_FACILITIES);

	}
}
