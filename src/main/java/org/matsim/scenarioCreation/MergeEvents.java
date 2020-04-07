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
import java.util.*;
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

			handler.events.forEach( (timeStamp,eventsList) -> allEvents.computeIfAbsent(timeStamp, time -> new ArrayList<Event>()).addAll(eventsList));
		}

		EventWriterXML writer = new EventWriterXML(
				IOUtils.getOutputStream(IOUtils.getFileUrl(output.toString()), false)
		);

		//		 Everything is sorted in-memory afterwards
		//		 If this is has not sufficient performance it needs to be rewritten
//		handler.events.sort(this);

		allEvents.forEach( (timeStamp, eventsList) -> eventsList.forEach(writer::handleEvent));
		writer.closeFile();
//		log.info("Merged {} events", handler.events.size());

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
			} else if( o1 instanceof ActivityStartEvent && o2 instanceof ActivityEndEvent){
				if ( ((ActivityStartEvent) o1).getActType().equals(((ActivityEndEvent) o2).getActType()) ){
					return -1;
				} else {
					return 1;
				}
			} else if(o1 instanceof ActivityEndEvent && o2 instanceof ActivityStartEvent){
				if ( ((ActivityEndEvent) o1).getActType().equals(((ActivityStartEvent) o2).getActType()) ){
					return 1;
				} else {
					return -1;
				}
			}
		}
		return o1.getEventType().compareTo(o2.getEventType());
	}

}
