package org.matsim.run;

import com.github.difflib.algorithm.DiffException;
import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.events.EventsUtils;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.InfectionEventHandler;
import org.matsim.testcases.MatsimTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class RunEpisimNoPtTest{
        private static final Logger log = Logger.getLogger( RunEpisim.class );
        @Rule public MatsimTestUtils utils = new MatsimTestUtils();

        @Test
        public void testBaseCase10it() throws IOException {

                OutputDirectoryLogging.catchLogEntries();

                Config config = ConfigUtils.createConfig( new EpisimConfigGroup() );
                EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );

                episimConfig.setInputEventsFile(
                                "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/output-berlin-v5.4-1pct/berlin-v5.4-1pct.output_events_for_episim.xml.gz" );
                // still need to push.  is faster

                episimConfig.setFacilitiesHandling( EpisimConfigGroup.FacilitiesHandling.bln );

                episimConfig.setCalibrationParameter(2);

                long closingIteration = 10;
                // pt:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "tr" ).setShutdownDay( 10 ).setRemainingFraction( 0. ) );
                // yyyyyy not sure if this works.  kai, mar'20
                // regular out-of-home acts:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "work" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "leis" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "edu" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "shop" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "errands" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "business" ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "other" ) );
                // freight act:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "freight" ) );
                // home act:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "home" ) );

                config.controler().setOutputDirectory( utils.getOutputDirectory() );

                OutputDirectoryLogging.initLoggingWithOutputDirectory( config.controler().getOutputDirectory() );

                EventsManager events = EventsUtils.createEventsManager();

                events.addHandler( new InfectionEventHandler( config ) );

                List<Event> allEvents = new ArrayList<>();
                events.addHandler(new RunEpisim.ReplayHandler(allEvents) );

                ControlerUtils.checkConfigConsistencyAndWriteToLog(config, "Just before starting iterations" );
                for ( int iteration=0 ; iteration<=10 ; iteration++ ){
                        events.resetHandlers( iteration );
                        if (iteration == 0)
                                EventsUtils.readEvents( events, episimConfig.getInputEventsFile() );
                        else
                                allEvents.forEach(events::processEvent);
                }



                String ORIGINAL = utils.getInputDirectory() + "/infectionEvents.txt";
                String REVISED = utils.getOutputDirectory() + "/infectionEvents.txt";
                final int result = RunEpisimBaseTest.compareWithDiffUtils( ORIGINAL, REVISED );

                Assert.assertEquals( 0, result );

                OutputDirectoryLogging.closeOutputDirLogging();
        }
}
