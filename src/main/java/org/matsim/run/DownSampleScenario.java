package org.matsim.run;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.facilities.ActivityFacilitiesImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static picocli.CommandLine.*;

@Command(
        description = "DOwn sample complete scenario",
        mixinStandardHelpOptions = true
)
public class DownSampleScenario implements Callable<Integer> {

    private static Logger log = LogManager.getLogger(DownSampleScenario.class);

    @Parameters(paramLabel = "sampleSize", arity = "1", description = "Desired percentage of the sample between (0, 1)")
    private double sampleSize;

    @Option(names = "--population", required = true, description = "Population xml file")
    private Path population;

    @Option(names = "--output", description = "Output folder", defaultValue = "output/scenario")
    private Path output;

    @Option(names = "--events", required = true, description = "Path to events file")
    private Path events;

    @Option(names = "--facilities", description = "Path to facility file")
    private Path facilities;

    @Option(names = "--seed", defaultValue = "1", description = "Random seed used for sampling")
    private long seed;


    public static void main(String[] args) {
        System.exit(new CommandLine(new DownSampleScenario()).execute(args));
    }

    @Override
    public Integer call() throws Exception {

        if (!Files.exists(population)) {
            log.error("Population file {} does not exists", population);
            return 2;
        }

        if (!Files.exists(events)) {
            log.error("Event file {} does not exists", events);
            return 2;
        }

        if (!Files.exists(output)) Files.createDirectories(output);

        log.info("Sampling with size {}", sampleSize);

        MatsimRandom.reset(seed);

        Population population = PopulationUtils.readPopulation(this.population.toString());
        PopulationUtils.sampleDown(population, sampleSize);

        PopulationUtils.writePopulation(population, output.resolve("population" + sampleSize + ".xml.gz").toString());

        EventsManager manager = EventsUtils.createEventsManager();

        FilterHandler handler = new FilterHandler(population);
        manager.addHandler(handler);
        EventsUtils.readEvents(manager, events.toString());

        EventWriterXML writer = new EventWriterXML(
                IOUtils.getOutputStream(IOUtils.getFileUrl(output.resolve("events" + sampleSize + ".xml.gz").toString()), false)
        );

        log.info("Filtered {} out of {} events = {}%", handler.events.size(), handler.counter, handler.events.size() / handler.counter);

        handler.events.forEach(writer::handleEvent);
        writer.closeFile();

        if (!Files.exists(facilities)) {
            log.warn("Facilities file {} does not exist", facilities);
            return 0;
        }

        log.info("Reading {}...", this.facilities);

        ActivityFacilitiesImpl facilities = new ActivityFacilitiesImpl();
        MatsimFacilitiesReader fReader = new MatsimFacilitiesReader(null, null, facilities);
        fReader.parse(IOUtils.getInputStream(IOUtils.getFileUrl(this.facilities.toString())));

        int n = facilities.getFacilities().size();

        Set<Id<ActivityFacility>> toRemove = facilities.getFacilities().keySet()
                .stream().filter(k -> !handler.facilities.contains(k)).collect(Collectors.toSet());

        toRemove.forEach(k -> facilities.getFacilities().remove(k));

        log.info("Filtered {} out of {} facilities", facilities.getFacilities().size(), n);

        new FacilitiesWriter(facilities).write(
                IOUtils.getOutputStream(IOUtils.getFileUrl(output.resolve("facilities" + sampleSize + ".xml.gz").toString()), false)
        );

        return 0;
    }

    /**
     * Filters events needed for the {@link InfectionEventHandler}
     */
    private static class FilterHandler implements ActivityEndEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler, ActivityStartEventHandler {

        private final Population population;
        private final List<Event> events = new ArrayList<>();
        private final Set<Id<ActivityFacility>> facilities = new HashSet<>();
        private int counter = 0;

        public FilterHandler(Population population) {
            this.population = population;
        }

        @Override
        public void handleEvent(ActivityEndEvent activityEndEvent) {
            counter++;

            if (!InfectionEventHandler.shouldHandleActivityEvent(activityEndEvent, activityEndEvent.getActType()))
                return;
            if (!population.getPersons().containsKey(activityEndEvent.getPersonId()))
                return;

            facilities.add(activityEndEvent.getFacilityId());
            events.add(activityEndEvent);
        }

        @Override
        public void handleEvent(ActivityStartEvent activityStartEvent) {
            counter++;

            if (!InfectionEventHandler.shouldHandleActivityEvent(activityStartEvent, activityStartEvent.getActType()))
                return;
            if (!population.getPersons().containsKey(activityStartEvent.getPersonId()))
                return;

            facilities.add(activityStartEvent.getFacilityId());
            events.add(activityStartEvent);
        }

        @Override
        public void handleEvent(PersonEntersVehicleEvent personEntersVehicleEvent) {
            counter++;

            if (!InfectionEventHandler.shouldHandlePersonEvent(personEntersVehicleEvent))
                return;
            if (!population.getPersons().containsKey(personEntersVehicleEvent.getPersonId()))
                return;

            events.add(personEntersVehicleEvent);
        }

        @Override
        public void handleEvent(PersonLeavesVehicleEvent personLeavesVehicleEvent) {
            counter++;

            if (!InfectionEventHandler.shouldHandlePersonEvent(personLeavesVehicleEvent))
                return;
            if (!population.getPersons().containsKey(personLeavesVehicleEvent.getPersonId()))
                return;

            events.add(personLeavesVehicleEvent);
        }
    }
}
