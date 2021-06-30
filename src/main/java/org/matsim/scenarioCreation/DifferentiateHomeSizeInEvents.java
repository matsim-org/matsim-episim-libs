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
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacilitiesImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;

import java.io.File;
import java.io.IOException;
import java.util.*;
/*
appends living space to "home" activities in events file,
e.g. home_35 indicates that agent has between 30 and 40 m2 of living space.
 */
public class DifferentiateHomeSizeInEvents {

	public static void main(String[] args) throws IOException {

		String root = "../shared-svn/projects/episim/matsim-files/snz/BerlinV2/episim-input/";
		List<String> eventFiles = new ArrayList<>();
		eventFiles.add(root + "be_2020-week_snz_episim_events_wt_25pt_split.xml.gz");
		eventFiles.add(root + "be_2020-week_snz_episim_events_sa_25pt_split.xml.gz");
		eventFiles.add(root + "be_2020-week_snz_episim_events_so_25pt_split.xml.gz");

		String shapeFile = "D:/Dropbox/Documents/VSP/episim/local_contact_intensity/LORs_with_living_space/lors.shp";

		String inputFacilities = root + "be_2020-week_snz_episim_facilities_mo_so_25pt_split_withDistrict.xml.gz";
		String crs = "EPSG:25832";
		String shapeCRS = "EPSG:25833";

		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(crs, shapeCRS);

		String attr = "m2pp";
		DistrictLookup.Index index = new DistrictLookup.Index(new File(shapeFile), ct, attr);

		// Read Facilities
		ActivityFacilitiesImpl facilities = new ActivityFacilitiesImpl();
		MatsimFacilitiesReader fReader = new MatsimFacilitiesReader(null, null, facilities);
		fReader.parse(IOUtils.getInputStream(IOUtils.getFileUrl(inputFacilities)));

		Map<Id<ActivityFacility>, String> facilityToLivingSpace = new HashMap<>();
		long notInBerlin = 0;
		long inBerlin = 0;
		for (ActivityFacility facility : facilities.getFacilities().values()) {
			try {
				Id<ActivityFacility> facilityId = facility.getId();
				String m2 = index.query(facility.getCoord().getX(), facility.getCoord().getY());
				facilityToLivingSpace.put(facilityId, m2);
				inBerlin++;
			} catch (NoSuchElementException e) {
				notInBerlin++;
			}
		}

		System.out.println("in berlin: " + inBerlin + "; out of berlin: " + notInBerlin);

		Map<Id<ActivityFacility>, Integer> facilityToLivingSpaceBinned = new HashMap<>();

		double sumLivingSpace = 0.;
		long cntLivingSpace = 0;

		int nanCnt = 0;
		for (Map.Entry<Id<ActivityFacility>, String> entry : facilityToLivingSpace.entrySet()) {
			try {
				double m2 = Double.parseDouble(entry.getValue());
				sumLivingSpace += m2;
				cntLivingSpace++;

				int m2_binned =(int) (((Math.ceil(m2 / 10)) ) * 10. - 5.);

				facilityToLivingSpaceBinned.put(entry.getKey(), m2_binned);
			} catch (NumberFormatException e) {
				nanCnt++;
			}
		}


		double avgLivingSpace = sumLivingSpace / cntLivingSpace;
		System.out.println("Average Living Space: " + avgLivingSpace + " m2");

		for (String events : eventFiles) {

			EventsManager manager = EventsUtils.createEventsManager();

			LivingSpaceAppender handler = new LivingSpaceAppender(facilityToLivingSpaceBinned);

			manager.addHandler(handler);
			EventsUtils.readEvents(manager, events);


			String outputName =events.replace(".xml.gz", "") + "_withLivingSpace.xml.gz";

			EventWriterXML writer = new EventWriterXML(
					IOUtils.getOutputStream(IOUtils.getFileUrl(outputName), false)
			);

			handler.modifiedEvents.forEach(writer::handleEvent);
			writer.closeFile();

		}

	}

	public static class LivingSpaceAppender implements ActivityEndEventHandler, ActivityStartEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler {


		Map<Id<ActivityFacility>, Integer> facilityToLivingSpace;
		final List<Event> modifiedEvents = new ArrayList<>();

		LivingSpaceAppender(Map<Id<ActivityFacility>, Integer> facilityToLivingSpace) {
			this.facilityToLivingSpace = facilityToLivingSpace;
		}

		@Override
		public void handleEvent(ActivityEndEvent activityEndEvent) {
			Id<ActivityFacility> facilityId = activityEndEvent.getFacilityId();

			String actType = activityEndEvent.getActType();
			if (actType.equals("home") && facilityToLivingSpace.containsKey(facilityId)) {
				actType = actType + "_" + facilityToLivingSpace.get(facilityId);
			}

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
			if (actType.equals("home") && facilityToLivingSpace.containsKey(facilityId)) {
				actType = actType + "_" + facilityToLivingSpace.get(facilityId);
			}

			ActivityEndEvent modifiedEvent = new ActivityEndEvent(activityStartEvent.getTime(),
					activityStartEvent.getPersonId(),
					activityStartEvent.getLinkId(),
					facilityId,
					actType);

			modifiedEvents.add(modifiedEvent);
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
