package org.matsim.run;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.testcases.MatsimTestUtils;

import java.io.IOException;

public class RunEpisimTotalShutdownTest{

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
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "tr" ).setShutdownDay( closingIteration ).setRemainingFraction( 0. ) );
                // regular out-of-home acts:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "work" ).setShutdownDay( closingIteration ).setRemainingFraction( 0.0 ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "leis" ).setShutdownDay( closingIteration ).setRemainingFraction( 0. ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "edu" ).setShutdownDay( closingIteration ).setRemainingFraction( 0. ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "shop" ).setShutdownDay( closingIteration ).setRemainingFraction( 0.0 ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "errands" ).setShutdownDay( closingIteration ).setRemainingFraction( 0.0 ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "business" ).setShutdownDay( closingIteration ).setRemainingFraction( 0. ) );
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "other" ).setShutdownDay( closingIteration ).setRemainingFraction( 0. ) );
                // freight act:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "freight" ).setShutdownDay( 0 ).setRemainingFraction( 0.0 ) );
                // home act:
                episimConfig.addContainerParams( new EpisimConfigGroup.InfectionParams( "home" ) );

                config.controler().setOutputDirectory( utils.getOutputDirectory() );

                RunEpisim.runSimulation(config, 10);

                RunEpisimBaseTest.assertSimulationOutput(utils);
        }
}
