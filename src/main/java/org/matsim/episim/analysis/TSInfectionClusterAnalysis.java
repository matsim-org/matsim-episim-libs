/* *********************************************************************** *
 * project: org.matsim.*
 * Controler.java
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

package org.matsim.episim.analysis;

import com.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.misc.Counter;
import org.matsim.episim.events.EpisimEventsReader;
import org.matsim.episim.events.EpisimInfectionEvent;
import org.matsim.episim.events.EpisimInfectionEventHandler;
import org.matsim.facilities.ActivityFacility;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

class TSInfectionClusterAnalysis {

	static Logger log = Logger.getLogger(TSInfectionClusterAnalysis.class);

	private static final String INPUT_INFECTION_EVENTS_DIR = "../../svn/public-svn/matsim/scenarios/countries/de/episim/battery/v9/masks/berlin/analysis/baseCase/events";
	private static final String INPUT_MATSIM_EVENTS = "../../svn/shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/be_v2_snz_episim_events.xml.gz";

	private static final String OUTPUTDIR = "../../svn/public-svn/matsim/scenarios/countries/de/episim/battery/v9/masks/berlin/analysis/baseCase/";


	public static void main(String[] args) {

		try {

			File eventsDir = new File(INPUT_INFECTION_EVENTS_DIR);
			if( ! eventsDir.exists() || ! eventsDir.isDirectory()) throw new IllegalArgumentException();
			EventsManager manager = EventsUtils.createEventsManager();
			ActivityClusterHandler activityClusterHandler = new ActivityClusterHandler();
			manager.addHandler(activityClusterHandler);


			log.info("start reading matsim events");
			EventsUtils.readEvents(manager, INPUT_MATSIM_EVENTS);

			log.info("start writing maximum cluster sizes to " + OUTPUTDIR + "maximumClusterSizes.csv");
			CSVWriter maxClusterWriter = new CSVWriter(Files.newBufferedWriter(Paths.get(OUTPUTDIR + "maximumClusterSizes.csv")));
			maxClusterWriter.writeNext(new String[]{"actType", "containerId", "maxAmountOfAgentsAtTheSameTime"});
			for (String actType : activityClusterHandler.maxAmountOfPeopleInContainerPerActType.keySet()){
				activityClusterHandler.maxAmountOfPeopleInContainerPerActType.get(actType).forEach((activityFacilityId, value) ->
						maxClusterWriter.writeNext(new String[]{actType, activityFacilityId.toString(), "" + value}));
			}
			maxClusterWriter.close();

			manager.removeHandler(activityClusterHandler);
			CSVWriter infectionWriter = new CSVWriter(Files.newBufferedWriter(Paths.get(OUTPUTDIR + "leisureInfectionGroups.csv")));
			InfectionGroupSizeHandler handler = new InfectionGroupSizeHandler(activityClusterHandler, infectionWriter);
			manager.addHandler(handler);

			log.info("start reading infection events");
			log.info("will write infection group size analysis to " + OUTPUTDIR + "leisureInfectionGroups.csv");
			List<File> fileList = new ArrayList(FileUtils.listFiles(eventsDir, new String[]{"gz"}, false));
			Counter counter = new Counter("reading events file nr ");
			fileList.forEach(file -> {
				new EpisimEventsReader(manager).readFile(file.getAbsolutePath());
			});

			infectionWriter.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}


class ActivityClusterHandler implements ActivityStartEventHandler, ActivityEndEventHandler {


	Map<String, Map<Id<ActivityFacility>, Integer>> maxAmountOfPeopleInContainerPerActType = new HashMap<>();
	Map<String, Map<Id<ActivityFacility>, ArrayList<Tuple<Double,Integer>>>> timeLineOfPeopleInContainerPerActType = new HashMap<>();


	@Override
	public void handleEvent(ActivityStartEvent activityStartEvent) {
		String actType = activityStartEvent.getActType();
		Id<ActivityFacility> facility = activityStartEvent.getFacilityId();

		Map<Id<ActivityFacility>, Integer> facilityMaxMap = maxAmountOfPeopleInContainerPerActType.computeIfAbsent(actType, m -> new HashMap<>());
		Map<Id<ActivityFacility>, ArrayList<Tuple<Double, Integer>>> timeLineMap = timeLineOfPeopleInContainerPerActType.computeIfAbsent(actType, m -> new HashMap<>());

		ArrayList<Tuple<Double, Integer>> timeLine = timeLineMap.computeIfAbsent(facility, l -> new ArrayList<>(Arrays.asList(new Tuple<>(0.0d,0)))
		);
		Tuple<Double, Integer> lastTimeLineEntry = timeLine.get(timeLine.size() - 1);

		int newValue = lastTimeLineEntry.getSecond() + 1;
		if(lastTimeLineEntry.getFirst() == activityStartEvent.getTime()){
			timeLine.set(timeLine.size()-1, new Tuple(lastTimeLineEntry.getFirst(), newValue));
		} else {
			timeLine.add(new Tuple(activityStartEvent.getTime(), newValue));
		}

		facilityMaxMap.compute(facility, (f,v) -> v==null ? newValue : Math.max(newValue,v));

	}


	@Override
	public void handleEvent(ActivityEndEvent activityEndEvent) {
		String actType = activityEndEvent.getActType();
		Id<ActivityFacility> facility = activityEndEvent.getFacilityId();

		Map<Id<ActivityFacility>, ArrayList<Tuple<Double, Integer>>> timeLineMap = timeLineOfPeopleInContainerPerActType.computeIfAbsent(actType, m -> new HashMap<>());
		ArrayList<Tuple<Double, Integer>> timeLine = timeLineMap.computeIfAbsent(facility, l -> new ArrayList<>(Arrays.asList(new Tuple<>(0.0d,0))));
		Tuple<Double, Integer> lastEntry = timeLine.get(timeLine.size() - 1);

		int newValue = lastEntry.getSecond() - 1;

		if(newValue < 0 ){
			//an agent starts in the sim with activityEnd - we need to correct values...
			for(int i=0; i < timeLine.size(); i++){
				Tuple<Double, Integer> oldTuple = timeLine.get(i);
				timeLine.set(i, new Tuple<>(oldTuple.getFirst(),oldTuple.getSecond() + 1));
			}
//			if(lastEntry.getFirst() != 0.0) throw new IllegalStateException("time = " + activityEndEvent.getTime() + "; new value = " + newValue + "; container = " + facility + "; acttype = " + actType );
			newValue = 0;
		}

		if(lastEntry.getFirst() == activityEndEvent.getTime()){
			timeLine.set(timeLine.size()-1, new Tuple(lastEntry.getFirst(), newValue ));
		} else {
			timeLine.add(new Tuple<>(activityEndEvent.getTime(),newValue));
		}
	}


}

class InfectionGroupSizeHandler implements EpisimInfectionEventHandler{

	private final ActivityClusterHandler activityClusterHandler;
	private CSVWriter infectionWriter;

	InfectionGroupSizeHandler(@NotNull ActivityClusterHandler activityClusterHandler, CSVWriter infectionWriter) {
		this.activityClusterHandler = activityClusterHandler;
		this.infectionWriter = infectionWriter;

		infectionWriter.writeNext(new String[]{"time",
				"infector",
				"infectedPerson",
				"containerId",
				"currentGroupSizeLeisure",
				"currentGroupSizeOtherThanLeisure"});
	}

	@Override
	public void handleEvent(EpisimInfectionEvent event) {
		if(activityClusterHandler.timeLineOfPeopleInContainerPerActType.size() == 0 ){
			throw new IllegalStateException("you need to read matsim events before infection events!");
		}

		if(event.getInfectionType().equals("leisure_leisure")){

			//count people in the same container that perform leisure
			Integer currentAmountOfPeopleDoingLeisureInTheSameContainer = getNrOfPeopleInContainerPerformingActType("leisure", event.getContainerId(), event.getTime());

			//count people in the same container that perform an activity other than leisure
			int sumOfPeopleInContainerForOtherActTypes = 0;
			for(String actType : activityClusterHandler.timeLineOfPeopleInContainerPerActType.keySet()){
				if( ! actType.equals("leisure")){
					if(activityClusterHandler.timeLineOfPeopleInContainerPerActType.get(actType).get(event.getContainerId()) != null){
						sumOfPeopleInContainerForOtherActTypes += getNrOfPeopleInContainerPerformingActType(actType, event.getContainerId(), event.getTime());
					}
				}
			}

			infectionWriter.writeNext(new String[]{"" + event.getTime(),
					"" + event.getInfectorId(),
					"" + event.getPersonId(),
					"" + event.getContainerId(),
					"" + currentAmountOfPeopleDoingLeisureInTheSameContainer,
					"" + sumOfPeopleInContainerForOtherActTypes});
		}
	}

	private int getNrOfPeopleInContainerPerformingActType(String actType, Id<?> container, double time){
		ArrayList<Tuple<Double, Integer>> timeLine = activityClusterHandler.timeLineOfPeopleInContainerPerActType.get(actType).get(container);
		if (timeLine == null) throw new IllegalStateException();

		double daytime = time % 86400; //episim runs over multiple days

		if(actType.equals("leisure") && (daytime > timeLine.get(timeLine.size() - 1 ).getFirst() || daytime < timeLine.get(0).getFirst()) ){
			if(daytime >= 86399){ //for non-circular trajectories, agents are moved "manually" (without activtyEndEvent) at the end of the day so this might happen
				System.out.println("WARN - END SIM INFECTION ? ");
			} else {
				throw new IllegalArgumentException();
			}
		}
		for (int i = 0; i < timeLine.size() - 1; i++){
			if(timeLine.get(i+1).getFirst() > daytime){
				if (! (timeLine.get(i).getFirst() <= daytime) ) throw new IllegalArgumentException();
				return timeLine.get(i).getSecond();
			}
		}
		return timeLine.get(timeLine.size()-1).getSecond();
	}

}
