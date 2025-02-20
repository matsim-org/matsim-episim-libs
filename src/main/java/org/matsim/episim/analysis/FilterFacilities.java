package org.matsim.episim.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.*;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLOutput;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static picocli.CommandLine.*;

@Command(
	name = "filterFacilities",
	description = "Filter facilities file based on facility IDs in events files.",
	mixinStandardHelpOptions = true
)
public class FilterFacilities implements Callable<Integer> {

	private static final Logger log = LogManager.getLogger(FilterFacilities.class);

	//	@Option(names = "--events", required = true, description = "Paths to events files")
//	private List<Path> eventsFiles;
	private final Path inputFolder = Path.of("../shared-svn/projects/episim/matsim-files/snz/BerlinBrandenburg/episim-input");
	private final List<Path> eventsFiles = List.of(
		inputFolder.resolve("bb_2020-week_snz_episim_events_wt_25pt_split.xml.gz"),
		inputFolder.resolve("bb_2020-week_snz_episim_events_sa_25pt_split.xml.gz"),
		inputFolder.resolve("bb_2020-week_snz_episim_events_so_25pt_split.xml.gz")
	);

	//	@Option(names = "--facilities", required = true, description = "Path to facilities file")
//	private Path facilitiesFile;
	private final Path facilitiesFile = Path.of("../shared-svn/projects/episim/matsim-files/snz/facilities_assigned_simplified.xml.gz");

//	@Option(names = "--output", required = true, description = "Path to output filtered facilities file")
//	private Path outputFile;
	private final Path outputFile =  inputFolder.resolve("bb_2020-week_snz_episim_facilities_25pt.xml.gz");

	public static void main(String[] args) {
		System.exit(new CommandLine(new FilterFacilities()).execute(args));
	}

	@Override
	public Integer call() throws Exception {
		for (Path eventsFile : eventsFiles) {
			if (!Files.exists(eventsFile)) {
				log.error("Events file {} does not exist", eventsFile);
				return 1;
			}
		}
		if (!Files.exists(facilitiesFile)) {
			log.error("Facilities file {} does not exist", facilitiesFile);
			return 1;
		}

		// Collect facility IDs from events files


		FilterFacilitiesHandler handler = new FilterFacilitiesHandler();
		for (Path eventsFile : eventsFiles) {
			log.info("Reading events file: {}", eventsFile);

			EventsManager eventsManager = EventsUtils.createEventsManager();
			eventsManager.addHandler(handler);


			EventsUtils.readEvents(eventsManager, eventsFile.toString());
			System.out.println(handler.getFacilityIds().size());

		}

		Set<Id<ActivityFacility>> facilityIdsInEvents = handler.getFacilityIds();


		// modified list also contains the roots of the split home facilities
		Set<Id<ActivityFacility>> facilityIdsInEventsMod = new LinkedHashSet<>(facilityIdsInEvents);

		for (Id<ActivityFacility> facilityId : facilityIdsInEvents) {
			String idString = facilityId.toString();
			if (idString.startsWith("home_")) {
				idString = idString.replaceFirst("home_", "");
				idString = idString.replaceFirst("_split\\d*", "");
				facilityIdsInEventsMod.add(Id.create(idString, ActivityFacility.class));
			}
		}

		log.info("Collected {} unique facility IDs from events files.", facilityIdsInEvents.size());

		// Load facilities file
		log.info("Reading facilities file: {}", facilitiesFile);
		ActivityFacilitiesImpl facilities = new ActivityFacilitiesImpl();
		MatsimFacilitiesReader fReader = new MatsimFacilitiesReader(null, null, facilities);
		fReader.parse(IOUtils.getInputStream(IOUtils.getFileUrl(facilitiesFile.toString())));

		int n = facilities.getFacilities().size();

		Set<Id<ActivityFacility>> toRemove = facilities.getFacilities().keySet()
			.stream().filter(k -> !facilityIdsInEventsMod.contains(k)).collect(Collectors.toSet());

		toRemove.forEach(k -> facilities.getFacilities().remove(k));

		log.info("Filtered {} out of {} facilities", facilities.getFacilities().size(), n);

		new FacilitiesWriter(facilities).write(
			IOUtils.getOutputStream(IOUtils.getFileUrl(outputFile.toString()), false)
		);
		return 0;
	}

	public static class FilterFacilitiesHandler implements ActivityEndEventHandler, ActivityStartEventHandler {


		private final Set<Id<ActivityFacility>> facilityIds;


		/**
		 * Constructor.
		 */
		public FilterFacilitiesHandler() {
			this.facilityIds = new HashSet<>();
		}

		public Set<Id<ActivityFacility>> getFacilityIds() {
			return facilityIds;
		}

		@Override
		public void handleEvent(ActivityEndEvent event) {
			facilityIds.add(event.getFacilityId());
		}

		@Override
		public void handleEvent(ActivityStartEvent event) {
			facilityIds.add(event.getFacilityId());
		}

	}

}
