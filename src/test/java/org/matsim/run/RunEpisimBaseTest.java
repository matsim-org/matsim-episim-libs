package org.matsim.run;

import com.google.common.collect.Lists;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.testcases.MatsimTestUtils;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class RunEpisimBaseTest{

        @Rule public MatsimTestUtils utils = new MatsimTestUtils();

        @Test
        public void test10it() throws IOException {

                OutputDirectoryLogging.catchLogEntries();

                Config config = ConfigUtils.createConfig( new EpisimConfigGroup() );
                EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule( config, EpisimConfigGroup.class );

                episimConfig.setInputEventsFile(
                                "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/output-berlin-v5.4-1pct/berlin-v5.4-1pct.output_events_for_episim.xml.gz" );
                // still need to push.  is faster

                episimConfig.setFacilitiesHandling( EpisimConfigGroup.FacilitiesHandling.bln );

                episimConfig.setCalibrationParameter(2);

                addBaseParams(episimConfig);

                config.controler().setOutputDirectory( utils.getOutputDirectory() );

                RunEpisim.runSimulation(config, 10);

                assertSimulationOutput(utils);

                OutputDirectoryLogging.closeOutputDirLogging();
        }

        /**
         * Adds base infection parameters with default values. (Valid for OpenBerlin scenario)
         */
        static void addBaseParams(EpisimConfigGroup config) {
                // pt:
                config.addContainerParams( new EpisimConfigGroup.InfectionParams( "tr" )) ;
                // regular out-of-home acts:
                config.addContainerParams( new EpisimConfigGroup.InfectionParams( "work" ) );
                config.addContainerParams( new EpisimConfigGroup.InfectionParams( "leis" ) );
                config.addContainerParams( new EpisimConfigGroup.InfectionParams( "edu" ) );
                config.addContainerParams( new EpisimConfigGroup.InfectionParams( "shop" ) );
                config.addContainerParams( new EpisimConfigGroup.InfectionParams( "errands" ) );
                config.addContainerParams( new EpisimConfigGroup.InfectionParams( "business" ) );
                config.addContainerParams( new EpisimConfigGroup.InfectionParams( "other" ) );
                // freight act:
                config.addContainerParams( new EpisimConfigGroup.InfectionParams( "freight" ) );
                // home act:
                config.addContainerParams( new EpisimConfigGroup.InfectionParams( "home" ) );
        }

        /**
         * Checks whether output of simulation matches expectation.
         */
        static void assertSimulationOutput(MatsimTestUtils utils) {
                for (String name : Lists.newArrayList("infections.txt", "infectionEvents.txt")) {
                        assertThat(new File(utils.getOutputDirectory(), name))
                                .hasSameTextualContentAs(new File(utils.getInputDirectory(), name));
                }
        }

}
