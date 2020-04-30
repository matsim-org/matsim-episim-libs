/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.scenarioCreation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacility;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static picocli.CommandLine.*;

/**
 * Filter event for only these events that are relevant for episim and optionally also by person ids.
 */
@Command(
		name = "filter",
		description = "Filter event file for relevant events or certain persons.",
		mixinStandardHelpOptions = true
)
public class FilterEvents implements Callable<Integer> {

	private static Logger log = LogManager.getLogger(FilterEvents.class);

	@Parameters(paramLabel = "file", arity = "1", description = "Path to event file", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Deutschland/de_events.xml.gz")
	private Path input;

	@Option(names = "--ids", description = "Path to person ids to filter for.", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_adults_idList.txt")
	private Path personIds;

	@Option(names = "--output", description = "Output file", defaultValue = "output/eventsFilteredBerlin.xml.gz")
	private Path output;

	@Option(names = "--educationFacilities", description = "Path to aggregated facilities file", defaultValue = "../shared-svn/projects/episim/matsim-files/snz/Berlin/processed-data/be_snz_educationFacilities.txt")
	private Path facilities;


	public static void main(String[] args) {
		System.exit(new CommandLine(new FilterEvents()).execute(args));
	}

	private static Map<Id<ActivityFacility>, Id<ActivityFacility>> readAndMapMergedFacilities(String path) throws IOException {
		Set<EducFacility> remainingFacilities = EducFacilities.readEducFacilites(path, null);
		Map<Id<ActivityFacility>, Id<ActivityFacility>> facilityReplacements = new HashMap<>();
		for (EducFacility remainingFacility : remainingFacilities) {
			for (Id<ActivityFacility> containedFacility : remainingFacility.getContainedFacilities()) {
				facilityReplacements.put(containedFacility, remainingFacility.getId());
			}
		}
		return facilityReplacements;
	}

	@Override
	public Integer call() throws Exception {

		if (!Files.exists(input)) {
			log.error("Input file {} does not exists", input);
			return 2;
		}

		if (!Files.exists(output.getParent())) Files.createDirectories(output.getParent());

		Set<String> filterIds = null;
		if (Files.exists(personIds)) {
			log.info("Filtering by person events {}", personIds);
			filterIds = CreationUtils.readIdFile(personIds);
		}

		EventsManager manager = EventsUtils.createEventsManager();

		Map<Id<ActivityFacility>, Id<ActivityFacility>> facilityreplacements = null;
		if (Files.exists(facilities)) {
			facilityreplacements = readAndMapMergedFacilities(facilities.toString());
		}

		FilterHandler handler = new FilterHandler(null, filterIds, facilityreplacements);
		manager.addHandler(handler);
		EventsUtils.readEvents(manager, input.toString());

		EventWriterXML writer = new EventWriterXML(
				IOUtils.getOutputStream(IOUtils.getFileUrl(output.toString()), false)
		);

//		log.info("Filtered {} out of {} events = {}%", handler.events.size(), handler.getCounter(), handler.events.size() / handler.getCounter());

		handler.events.forEach((time, eventsList) -> eventsList.forEach(writer::handleEvent));
		writer.closeFile();

		return 0;
	}

}
