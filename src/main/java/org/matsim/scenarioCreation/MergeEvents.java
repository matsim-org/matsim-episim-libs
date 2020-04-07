package org.matsim.scenarioCreation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.events.*;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
		name = "merge",
		description = "Merge multiple event files into one.",
		mixinStandardHelpOptions = true
)
public class MergeEvents implements Callable<Integer>, Comparator<Event> {

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

		EventsManager manager = EventsUtils.createEventsManager();
		FilterHandler handler = new FilterHandler(null, null, null);
		manager.addHandler(handler);


		for (Path path : input) {
			if (!Files.exists(path)) {
				log.error("Input file {} does not exists", input);
				return 2;
			}

			EventsUtils.readEvents(manager, path.toString());
		}

		EventWriterXML writer = new EventWriterXML(
				IOUtils.getOutputStream(IOUtils.getFileUrl(output.toString()), false)
		);

		// Everything is sorted in-memory afterwards
		// If this is has not sufficient performance it needs to be rewritten
		handler.events.sort(this);
		handler.events.forEach(writer::handleEvent);
		writer.closeFile();
		log.info("Merged {} events", handler.events.size());

		return 0;

	}

	@Override
	public int compare(Event o1, Event o2) {
		int cmp = Double.compare(o1.getTime(), o2.getTime());
		if (cmp != 0) return cmp;

		if (o1 instanceof HasPersonId && o2 instanceof HasPersonId) {
			int value = ((HasPersonId) o1).getPersonId().compareTo(((HasPersonId) o2).getPersonId());
			if (value != 0) return value;

			if (o1 instanceof PersonLeavesVehicleEvent && o2 instanceof ActivityStartEvent) {
				return -1;
			} else if (o1 instanceof ActivityStartEvent && o2 instanceof PersonLeavesVehicleEvent) {
				return 1;
			} else if (o1 instanceof ActivityEndEvent && o2 instanceof PersonEntersVehicleEvent) {
				return -1;
			} else if (o1 instanceof PersonEntersVehicleEvent && o2 instanceof ActivityEndEvent) {
				return 1;
			}
		}
		return o1.getEventType().compareTo(o2.getEventType());
	}

}
