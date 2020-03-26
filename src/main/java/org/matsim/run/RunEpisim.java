package org.matsim.run;

import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.FacilitiesHandling;
import org.matsim.episim.EpisimConfigGroup.InfectionParams;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.episim.policy.FixedPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunEpisim {

    /**
     * Activity names of the default params from {@link #addDefaultParams(EpisimConfigGroup)}
     */
    public static final String[] DEFAULT_ACTIVITIES = {
            "pt", "work", "leisure", "edu", "shop", "errands", "business", "other", "freight", "home"
    };

    public static void main(String[] args) throws IOException {
        OutputDirectoryLogging.catchLogEntries();

        Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        episimConfig.setInputEventsFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/output-berlin-v5.4-1pct/berlin-v5.4-1pct.output_events_wo_linkEnterLeave.xml.gz");
//                episimConfig.setInputEventsFile( "../public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/output-berlin-v5.4-1pct/berlin-v5.4-1pct.output_events_for_episim.xml.gz" );
        episimConfig.setFacilitiesHandling(FacilitiesHandling.bln);
        episimConfig.setSampleSize(0.01);
        episimConfig.setCalibrationParameter(2);

        long closingIteration = 14;

        addDefaultParams(episimConfig);

        episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
                .shutdown(closingIteration, "leisure", "edu")
                .restrict(closingIteration, 0.2, "work", "business", "other")
                .restrict(closingIteration, 0.3, "shop", "errands")
                .restrict(closingIteration, 0.5, "pt")
                .open(closingIteration + 60, DEFAULT_ACTIVITIES)
                .build()
        );

        setOutputDirectory(config);

        ConfigUtils.applyCommandline(config, Arrays.copyOfRange(args, 0, args.length));

        runSimulation(config, 130);

    }

    /**
     * Adds default parameters that should be valid for most scenarios.
     */
    public static void addDefaultParams(EpisimConfigGroup config) {
        // pt
        config.addContainerParams(new InfectionParams("pt", "tr"));
        // regular out-of-home acts:
        config.addContainerParams(new InfectionParams("work"));
        config.addContainerParams(new InfectionParams("leisure", "leis"));
        config.addContainerParams(new InfectionParams("edu"));
        config.addContainerParams(new InfectionParams("shop"));
        config.addContainerParams(new InfectionParams("errands"));
        config.addContainerParams(new InfectionParams("business"));
        config.addContainerParams(new InfectionParams("other"));
        // freight act:
        config.addContainerParams(new InfectionParams("freight"));
        // home act:
        config.addContainerParams(new InfectionParams("home"));
    }

    /**
     * Creates an output directory, with a name based on current config and adapt the logging config.
     * This method is not thread-safe unlike {@link #runSimulation(Config, int)}.
     */
    public static void setOutputDirectory(Config config) throws IOException {
        StringBuilder outdir = new StringBuilder("output");
        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        for (InfectionParams infectionParams : episimConfig.getContainerParams().values()) {
            outdir.append("-");
            outdir.append(infectionParams.getContainerName());
//                        if (infectionParams.getShutdownDay() < Long.MAX_VALUE) {
//                                outdir.append(infectionParams.getRemainingFraction());
            //                               outdir.append("it").append(infectionParams.getShutdownDay());
            //                       }
            if (infectionParams.getContactIntensity() != 1.) {
                outdir.append("ci").append(infectionParams.getContactIntensity());
            }
        }
        config.controler().setOutputDirectory(outdir.toString());
        OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory());

    }

    /**
     * Main loop that performs the iterations of the simulation.
     *
     * @param config     fully initialized config file, {@link EpisimConfigGroup} needs to be present.
     * @param iterations ending iteration (inclusive)
     */
    public static void runSimulation(Config config, int iterations) throws IOException {

        Path out = Paths.get(config.controler().getOutputDirectory());
        if (!Files.exists(out))
            Files.createDirectories(out);

        EventsManager events = EventsUtils.createEventsManager();
        events.addHandler(new InfectionEventHandler(config));

        List<Event> allEvents = new ArrayList<>();
        events.addHandler(new ReplayHandler(allEvents));

        EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        ControlerUtils.checkConfigConsistencyAndWriteToLog(config, "Just before starting iterations");

        for (int iteration = 0; iteration <= iterations; iteration++) {
            events.resetHandlers(iteration);
            if (iteration == 0)
                EventsUtils.readEvents(events, episimConfig.getInputEventsFile());
            else
                allEvents.forEach(events::processEvent);
        }

        OutputDirectoryLogging.closeOutputDirLogging();

    }

    /**
     * Helper class that stores all events in a given array (only in iteration 0)
     */
    private static final class ReplayHandler implements BasicEventHandler {
        public final List<Event> events;
        private boolean collect = false;

        public ReplayHandler(List<Event> events) {
            this.events = events;
        }

        @Override
        public void reset(int iteration) {
            collect = iteration == 0;
        }

        @Override
        public void handleEvent(Event event) {
            if (collect) events.add(event);
        }
    }

}
