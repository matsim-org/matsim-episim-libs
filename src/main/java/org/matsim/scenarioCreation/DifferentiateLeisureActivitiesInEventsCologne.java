package org.matsim.scenarioCreation;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacility;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/*
appends living space to "home" activities in events file,
e.g. home_35 indicates that agent has between 30 and 40 m2 of living space.
 */
public class DifferentiateLeisureActivitiesInEventsCologne {

	public static final String ROOT = "../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/";
	private static final int SAMPLE = 25;

	public static void main(String[] args) throws IOException {

		// collect event files
		List<String> eventFiles = new ArrayList<>();

		eventFiles.add(ROOT + "cologne_snz_episim_events_wt_"+ SAMPLE +"pt_split.xml.gz");
		eventFiles.add(ROOT + "cologne_snz_episim_events_sa_"+ SAMPLE +"pt_split.xml.gz");
		eventFiles.add(ROOT + "cologne_snz_episim_events_so_"+ SAMPLE +"pt_split.xml.gz");


		// 1) get list of all facilities where leisure events take place

		Set<String> leisureFacilitiesFullWeek = new HashSet<>();

		for (String events : eventFiles) {

			EventsManager manager = EventsUtils.createEventsManager();

			FacilityFinder handler = new FacilityFinder();

			manager.addHandler(handler);
			EventsUtils.readEvents(manager, events);

			leisureFacilitiesFullWeek.addAll(handler.leisureFacilities);
		}

		// 2) make list of facilities assigned to leisPrivate & leisPublic
		Set<String> facilitiesPublic = new HashSet<>();
		Set<String> facilitiesPrivate = new HashSet<>();

		Random rnd = new Random(4711);
		for (String facility : leisureFacilitiesFullWeek) {
			if (rnd.nextDouble() > 0.5) {
				facilitiesPublic.add(facility);
			} else {
				facilitiesPrivate.add(facility);
			}
		}


		// 3) process events to replace all leisure activity types
		for (String events : eventFiles) {

			EventsManager manager = EventsUtils.createEventsManager();

			LeisureSplitter handler = new LeisureSplitter(facilitiesPublic,facilitiesPrivate);

			manager.addHandler(handler);
			EventsUtils.readEvents(manager, events);


			String outputName = events.replace(".xml.gz", "") + "_withLeisureSplit.xml.gz";

			EventWriterXML writer = new EventWriterXML(
					IOUtils.getOutputStream(IOUtils.getFileUrl(outputName), false)
			);

			handler.modifiedEvents.forEach(writer::handleEvent);
			writer.closeFile();

		}

	}

	public static class FacilityFinder implements ActivityEndEventHandler, ActivityStartEventHandler{

		Set<String> leisureFacilities = new HashSet<>();

		@Override
		public void handleEvent(ActivityEndEvent activityEndEvent) {
			if (activityEndEvent.getActType().equals("leisure")) {
				leisureFacilities.add(activityEndEvent.getFacilityId().toString());
			}

		}

		@Override
		public void handleEvent(ActivityStartEvent activityStartEvent) {
			if (activityStartEvent.getActType().equals("leisure")) {
				leisureFacilities.add(activityStartEvent.getFacilityId().toString());
			}
		}
	}


	public static class LeisureSplitter implements ActivityEndEventHandler, ActivityStartEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler {
		final List<Event> modifiedEvents = new ArrayList<>();

		final Set<String> facilitiesPublic;
		final Set<String> facilitiesPrivate;

		LeisureSplitter(Set<String> facilitiesPublic, Set<String> facilitiesPrivate) {
			this.facilitiesPublic = facilitiesPublic;
			this.facilitiesPrivate = facilitiesPrivate;
		}

		@Override
		public void handleEvent(ActivityEndEvent activityEndEvent) {
			Id<ActivityFacility> facilityId = activityEndEvent.getFacilityId();

			String actType = activityEndEvent.getActType();
			actType = getModifiedLeisureAct(facilityId, actType);

			ActivityEndEvent modifiedEvent = new ActivityEndEvent(activityEndEvent.getTime(),
					activityEndEvent.getPersonId(),
					activityEndEvent.getLinkId(),
					facilityId,
					actType);

			modifiedEvents.add(modifiedEvent);
		}


		@Override
		public void handleEvent(ActivityStartEvent activityStartEvent) {
			Id<ActivityFacility> facilityId = activityStartEvent.getFacilityId();
			String actType = activityStartEvent.getActType();

			actType = getModifiedLeisureAct(facilityId, actType);
			ActivityStartEvent modifiedEvent = new ActivityStartEvent(activityStartEvent.getTime(),
					activityStartEvent.getPersonId(),
					activityStartEvent.getLinkId(),
					facilityId,
					actType);

			modifiedEvents.add(modifiedEvent);
		}

		private String getModifiedLeisureAct(Id<ActivityFacility> facilityId, String actType) {
			if (actType.equals("leisure")) {
				if (facilitiesPublic.contains(facilityId.toString())) {
					actType = "leisPublic";
				} else if (facilitiesPrivate.contains(facilityId.toString())) {
					actType = "leisPrivate";
				} else {
					throw new RuntimeException("all leisure facilities should have been designated either public or private");
				}

			}
			return actType;
		}

		@Override
		public void handleEvent(PersonEntersVehicleEvent personEntersVehicleEvent) {
			modifiedEvents.add(personEntersVehicleEvent);
		}

		@Override
		public void handleEvent(PersonLeavesVehicleEvent personLeavesVehicleEvent) {
			modifiedEvents.add(personLeavesVehicleEvent);
		}
	}
}
