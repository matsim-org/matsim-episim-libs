package org.matsim.scenarioCreation;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacility;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/*
appends living space to "home" activities in events file,
e.g. home_35 indicates that agent has between 30 and 40 m2 of living space.
 */
public class DifferentiateHomeSizeInEventsCologne {

	public enum City {
		berlin,
		cologne
	}

	public static final City city = City.cologne;
	public static final String CRS = "EPSG:25832";
	public static final String SHAPE_CRS = "EPSG:25832";
	public static final String ROOT = "../shared-svn/projects/episim/matsim-files/snz/Cologne/episim-input/";
	public static final String SHAPE_FILE = ROOT + "CologneDistricts/CologneDistricts.shp";
	private static final int SAMPLE = 25;

	public static void main(String[] args) throws IOException {

		// collect event files
		List<String> eventFiles = new ArrayList<>();

		eventFiles.add(ROOT + "cologne_snz_episim_events_wt_"+ SAMPLE +"pt_split.xml.gz");
		eventFiles.add(ROOT + "cologne_snz_episim_events_sa_"+ SAMPLE +"pt_split.xml.gz");
		eventFiles.add(ROOT + "cologne_snz_episim_events_so_"+ SAMPLE +"pt_split.xml.gz");

		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(CRS, SHAPE_CRS);

		String attr = "STT_NAME";
		DistrictLookup.Index index = new DistrictLookup.Index(new File(SHAPE_FILE), ct, attr);


		// read population:
		String inputPopulation = "cologne_snz_entirePopulation_emptyPlans_withDistricts_" + SAMPLE + "pt_split.xml.gz";
		String outputPopulation = "cologne_snz_entirePopulation_emptyPlans_withDistricts_andNeighborhood_" + SAMPLE + "pt_split.xml.gz";

		Population population = PopulationUtils.readPopulation( ROOT + inputPopulation);

		Map<String, String> homeIdToDistrict = new HashMap<>();
		Map<String, Integer> subdistrictToPopulation = new HashMap<>();
		for (Person person : population.getPersons().values()) {
			try {
				String district = index.query(Double.parseDouble(person.getAttributes().getAttribute("homeX").toString()), Double.parseDouble(person.getAttributes().getAttribute("homeY").toString()));
				homeIdToDistrict.put(person.getAttributes().getAttribute("homeId").toString(), district);
				person.getAttributes().putAttribute("subdistrict", district);

				Integer cnt = subdistrictToPopulation.getOrDefault(district, 0) + 1;
				subdistrictToPopulation.put(district, cnt);
			} catch (NoSuchElementException e) {
			}
		}

//		for (Map.Entry<String, Integer> entry : subdistrictToPopulation.entrySet()) {
//			System.out.println(entry.getKey() + "," + entry.getValue());
//		}

		for (String district : subdistrictToPopulation.keySet()) {
			System.out.print("\""+district+"\",");
		}

		System.out.println();
		for (Integer integer : subdistrictToPopulation.values()) {
			System.out.print(integer + ",");
		}

		PopulationUtils.writePopulation(population, ROOT + outputPopulation);

		for (String events : eventFiles) {

			EventsManager manager = EventsUtils.createEventsManager();

			LivingSpaceAppender handler = new LivingSpaceAppender(homeIdToDistrict);

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


		Map<String, String> homeIdToDistrict;
		final List<Event> modifiedEvents = new ArrayList<>();

		final Set<String> lowAptSize;
		final Set<String> highAptSize;

		LivingSpaceAppender(Map<String,String> homeIdToDistrict) {
			this.homeIdToDistrict = homeIdToDistrict;
			lowAptSize = homeIdToDistrict.values().stream().filter(x -> x.matches("^([A-L]).*")).collect(Collectors.toSet());
			highAptSize = homeIdToDistrict.values().stream().filter(x -> x.matches("^([M-Z]).*")).collect(Collectors.toSet());
		}

		@Override
		public void handleEvent(ActivityEndEvent activityEndEvent) {
			Id<ActivityFacility> facilityId = activityEndEvent.getFacilityId();

			String actType = activityEndEvent.getActType();
			if (actType.equals("home")) {
				if (lowAptSize.contains(homeIdToDistrict.get(facilityId.toString()))) {
					actType = actType + "_15";
				} else if (highAptSize.contains(homeIdToDistrict.get(facilityId.toString()))) {
					actType = actType + "_75";
				} else {

				}

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
			if (actType.equals("home")) {
				if (lowAptSize.contains(homeIdToDistrict.get(facilityId.toString()))) {
					actType = actType + "_15";
				} else if (highAptSize.contains(homeIdToDistrict.get(facilityId.toString()))) {
					actType = actType + "_75";
				} else {

				}

			}
			ActivityStartEvent modifiedEvent = new ActivityStartEvent(activityStartEvent.getTime(),
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
