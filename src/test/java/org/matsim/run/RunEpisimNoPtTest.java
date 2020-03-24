package org.matsim.run;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.testcases.MatsimTestUtils;

import java.io.IOException;

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

                RunEpisim.runSimulation(config, 10);

                String ORIGINAL = utils.getInputDirectory() + "/infectionEvents.txt";
                String REVISED = utils.getOutputDirectory() + "/infectionEvents.txt";
                final int result = RunEpisimBaseTest.compareWithDiffUtils( ORIGINAL, REVISED );

                Assert.assertEquals( 0, result );

                OutputDirectoryLogging.closeOutputDirLogging();
        }
}
