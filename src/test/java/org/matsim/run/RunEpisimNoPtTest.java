package org.matsim.run;

import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.testcases.MatsimTestUtils;

import java.io.IOException;

public class RunEpisimNoPtTest{

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

                RunEpisimBaseTest.addBaseParams(episimConfig);

                episimConfig.getOrAddContainerParams("tr")
                        .setShutdownDay(5).setRemainingFraction(0.);

                config.controler().setOutputDirectory( utils.getOutputDirectory() );

                RunEpisim.runSimulation(config, 10);

                RunEpisimBaseTest.assertSimulationOutput(utils);
        }
}
