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
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Merge multiple event files into one, preserving the ordering.
 */
@CommandLine.Command(
		name = "merge",
		description = "Merge multiple event files into one.",
		mixinStandardHelpOptions = true
)
public class MergeEvents implements Callable<Integer>{

	private static final Logger log = LogManager.getLogger(MergeEvents.class);

	@CommandLine.Parameters(paramLabel = "INPUT", arity = "1..*", description = "Input event files")
	private List<Path> input;

	@CommandLine.Option(names = "--output", description = "Path to output file", defaultValue = "merged_events.xml.gz")
	private Path output;

	public static void main(String[] args) {
		System.exit(new CommandLine(new MergeEvents()).execute(args));
	}

	@Override
	public Integer call() throws Exception {

		if (!Files.exists(output.getParent())) Files.createDirectories(output.getParent());

		Map<Double, List<Event>> allEvents = new TreeMap<>(Comparator.comparingDouble(Double::doubleValue));

		for (Path path : input) {
			if (!Files.exists(path)) {
				log.error("Input file {} does not exist ", input);
				return 2;
			}
			EventsManager manager = EventsUtils.createEventsManager();
			FilterHandler handler = new FilterHandler(null, null, null);
			manager.addHandler(handler);

			EventsUtils.readEvents(manager, path.toString());

			throw new RuntimeException("Merging not implemented...");
			//handler.events.forEach( (timeStamp,eventsList) -> allEvents.computeIfAbsent(timeStamp, time -> new ArrayList<Event>()).addAll(eventsList));
		}

		EventWriterXML writer = new EventWriterXML(
				IOUtils.getOutputStream(IOUtils.getFileUrl(output.toString()), false)
		);

		allEvents.forEach( (timeStamp, eventsList) -> eventsList.forEach(writer::handleEvent));
		writer.closeFile();
//		log.info("Merged {} events", handler.events.size());

		return 0;
	}


}
