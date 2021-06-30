package org.matsim.scenarioCreation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.*;
import org.matsim.utils.objectattributes.attributable.Attributes;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(
		name = "filterFacilities",
		description = "Filter facilities file to match facilities found in events file",
		mixinStandardHelpOptions = true
)

/*
  class to filter global facilities file to match events file;
  takes into account that "_split#" may have been added to some facilities in events file,
   and changes the facility id accordingly
 */
public class FilterFacilitiesToMatchEvents implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(FilterFacilitiesToMatchEvents.class);
	@CommandLine.Option(names = "--output", description = "Output folder", required = true)
	private Path output;

	@CommandLine.Option(names = "--events", required = true, description = "Path to events file")
	private List<Path> eventFiles;

	@CommandLine.Option(names = "--facilities", description = "Path to facility file", required = true)
	private Path facilities;


	public static void main(String[] args) {
		System.exit(new CommandLine(new FilterFacilitiesToMatchEvents()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		if (!eventFiles.stream().allMatch(Files::exists)) {
			log.error("Event files {} do not exists", eventFiles);
			return 2;
		}


		Set<Id<ActivityFacility>> facilitiesToKeep = new HashSet<>();

		for (Path events : eventFiles) {

			log.info("Reading event file {}", events);

			EventsManager manager = EventsUtils.createEventsManager();
			FacilityIdCatcher handler = new FacilityIdCatcher();
			manager.addHandler(handler);
			EventsUtils.readEvents(manager, events.toString());


			facilitiesToKeep.addAll(handler.facilities);
		}


		// if facilityId from facilities file is substring of facilityId from events file, then create a new facility to match events file
		// i.e. from events: home_12345_split1 ; from facilities: 12345; --> create facility in facilities file of id home_12345_split1
		Map<Id<ActivityFacility>, Id<ActivityFacility>> modNameToOldNameEvents = new HashMap<>();
		for (Id<ActivityFacility> eventFacility : facilitiesToKeep) {
			if (eventFacility.toString().contains("home_") || eventFacility.toString().contains("split")) {
				String shortenedIdString = eventFacility.toString().replaceFirst("home_", "").replaceFirst("_split[0-9]+", "");
				Id<ActivityFacility> shortenedId = Id.create(shortenedIdString, ActivityFacility.class);
				modNameToOldNameEvents.put(eventFacility, shortenedId);
			}
		}

		if (facilities == null || !Files.exists(facilities)) {
			log.warn("Facilities file {} does not exist", facilities);
			return 0;
		}


		log.info("Reading {}...", this.facilities);

		ActivityFacilitiesImpl facilities = new ActivityFacilitiesImpl();
		MatsimFacilitiesReader fReader = new MatsimFacilitiesReader(null, null, facilities);
		fReader.parse(IOUtils.getInputStream(IOUtils.getFileUrl(this.facilities.toString())));


		// add new facilities

		ActivityFacilitiesFactory factory = facilities.getFactory();
		for (Id<ActivityFacility> eventFacModName : modNameToOldNameEvents.keySet()) {
			// if facilities does not contain the modified facility (from the events file)
			if (!facilities.getFacilities().containsKey(eventFacModName)) {
				// but it does include the facilityId without the modification (minus "home_" and "split...")
				Id<ActivityFacility> eventFacOriginalName = modNameToOldNameEvents.get(eventFacModName);
				if (facilities.getFacilities().containsKey(eventFacOriginalName)) {
					// then create a copy of the original facility, and give it the modified Id to match events file
					ActivityFacility originalFacility = facilities.getFacilities().get(eventFacOriginalName);
					Coord coord = originalFacility.getCoord();

					ActivityFacility activityFacility = factory.createActivityFacility(eventFacModName, coord);

					// add activity options
					Map<String, ActivityOption> activityOptions = originalFacility.getActivityOptions();
					if (activityOptions != null && !activityOptions.isEmpty()) {
						for (ActivityOption activityOption : activityOptions.values()) {
							activityFacility.addActivityOption(activityOption);
						}
					}
					// add attributes
					Attributes attributes = originalFacility.getAttributes();

					if (attributes != null && !attributes.getAsMap().isEmpty()) {
						for (Map.Entry<String, Object> entry : attributes.getAsMap().entrySet()) {
							activityFacility.getAttributes().putAttribute(entry.getKey(), entry.getValue());
						}
					}

					facilities.addActivityFacility(activityFacility);

				}
			}
		}


		int n = facilities.getFacilities().size();

		Set<Id<ActivityFacility>> toRemove = facilities.getFacilities().keySet()
				.stream().filter(k -> !facilitiesToKeep.contains(k)).collect(Collectors.toSet());

		toRemove.forEach(k -> facilities.getFacilities().remove(k));

		log.info("Filtered {} out of {} facilities", facilities.getFacilities().size(), n);

		new FacilitiesWriter(facilities).write(
				IOUtils.getOutputStream(IOUtils.getFileUrl(output.resolve("facilities_filtered_for_events.xml.gz").toString()), false)
		);

		return 0;
	}


	public static class FacilityIdCatcher implements ActivityEndEventHandler {

		Set<Id<ActivityFacility>> facilities = new HashSet<>();

		@Override
		public void handleEvent(ActivityEndEvent activityEndEvent) {
			facilities.add(activityEndEvent.getFacilityId());
		}
	}

}
