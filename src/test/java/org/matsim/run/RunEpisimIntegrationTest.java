package org.matsim.run;

import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.testcases.MatsimTestUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class RunEpisimIntegrationTest {

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();
    /**
     * Iterations
     */
    @Parameterized.Parameter
    public int it;
    private Config config;
    private EpisimConfigGroup episimConfig;

    @Parameterized.Parameters(name = "it{0}")
    public static Iterable<Integer> parameters() {
        return Arrays.asList(10, 100);
    }

    @Before
    public void setup() throws IOException {
        OutputDirectoryLogging.catchLogEntries();

        config = ConfigUtils.createConfig(new EpisimConfigGroup());
        episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

        episimConfig.setInputEventsFile(
                "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/output-berlin-v5.4-1pct/berlin-v5.4-1pct.output_events_for_episim.xml.gz");
        // still need to push.  is faster

        episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.bln);
        episimConfig.setCalibrationParameter(2);

        config.controler().setOutputDirectory(utils.getOutputDirectory());
        OutputDirectoryLogging.initLoggingWithOutputDirectory(config.controler().getOutputDirectory());

        RunEpisim.addDefaultParams(episimConfig);
    }

    @After
    public void tearDown() {
        assertSimulationOutput();
    }


    /**
     * Checks whether output of simulation matches expectation.
     */
    private void assertSimulationOutput() {
        for (String name : Lists.newArrayList("infections.txt", "infectionEvents.txt")) {
            assertThat(new File(utils.getOutputDirectory(), name))
                    .hasSameTextualContentAs(new File(utils.getInputDirectory(), name));
        }
    }

    @Test
    public void testBaseCase() throws IOException {

        RunEpisim.runSimulation(config, it);
    }

    @Test
    public void testNoPt() throws IOException {

        episimConfig.setPolicyConfig(FixedPolicy.config()
                .shutdown(5, "pt")
                .build()
        );

        RunEpisim.runSimulation(config, it);
    }

    @Test
    public void testPlausibleShutdown() throws IOException {

        episimConfig.setPolicyConfig(FixedPolicy.config()
                .shutdown(0, "freight")
                .shutdown(it, "leisure", "edu", "business")
                .restrict(it, 0.2, "work", "other")
                .restrict(it, 0.3, "shop", "errands")
                .build()
        );

        RunEpisim.runSimulation(config, it);
    }

    @Test
    public void testTotalShutdown() throws IOException {

        // there should be no infections after day 1
        episimConfig.setPolicyConfig(FixedPolicy.config()
                .shutdown(1, RunEpisim.DEFAULT_ACTIVITIES)
                .build()
        );

        RunEpisim.runSimulation(config, it);
    }

}
