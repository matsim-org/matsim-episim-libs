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
    public static Iterable<?> parameters() {
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

        addBaseParams(episimConfig);
    }

    @After
    public void tearDown() {
        assertSimulationOutput();
    }

    /**
     * Adds base infection parameters with default values. (Valid for OpenBerlin scenario)
     */
    private void addBaseParams(EpisimConfigGroup config) {
        // pt:
        config.addContainerParams(new EpisimConfigGroup.InfectionParams("tr"));
        // regular out-of-home acts:
        config.addContainerParams(new EpisimConfigGroup.InfectionParams("work"));
        config.addContainerParams(new EpisimConfigGroup.InfectionParams("leis"));
        config.addContainerParams(new EpisimConfigGroup.InfectionParams("edu"));
        config.addContainerParams(new EpisimConfigGroup.InfectionParams("shop"));
        config.addContainerParams(new EpisimConfigGroup.InfectionParams("errands"));
        config.addContainerParams(new EpisimConfigGroup.InfectionParams("business"));
        config.addContainerParams(new EpisimConfigGroup.InfectionParams("other"));
        // freight act:
        config.addContainerParams(new EpisimConfigGroup.InfectionParams("freight"));
        // home act:
        config.addContainerParams(new EpisimConfigGroup.InfectionParams("home"));
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

        episimConfig.getOrAddContainerParams("tr")
                .setShutdownDay(5).setRemainingFraction(0.);

        RunEpisim.runSimulation(config, it);
    }

    @Test
    public void testPlausibleShutdown() throws IOException {
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("tr").setContactIntensity(1.));
        // regular out-of-home acts:
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("work").setShutdownDay(it).setRemainingFraction(0.2));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("leis").setShutdownDay(it).setRemainingFraction(0.));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("edu").setShutdownDay(it).setRemainingFraction(0.));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("shop").setShutdownDay(it).setRemainingFraction(0.3));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("errands").setShutdownDay(it).setRemainingFraction(0.3));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("business").setShutdownDay(it).setRemainingFraction(0.));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("other").setShutdownDay(it).setRemainingFraction(0.2));
        // freight act:
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("freight").setShutdownDay(0).setRemainingFraction(0.0));
        // home act:
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("home"));

        RunEpisim.runSimulation(config, it);
    }

    @Test
    public void testTotalShutdown() throws IOException {
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("tr").setShutdownDay(it).setRemainingFraction(0.));
        // regular out-of-home acts:
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("work").setShutdownDay(it).setRemainingFraction(0.0));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("leis").setShutdownDay(it).setRemainingFraction(0.));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("edu").setShutdownDay(it).setRemainingFraction(0.));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("shop").setShutdownDay(it).setRemainingFraction(0.0));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("errands").setShutdownDay(it).setRemainingFraction(0.0));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("business").setShutdownDay(it).setRemainingFraction(0.));
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("other").setShutdownDay(it).setRemainingFraction(0.));
        // freight act:
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("freight").setShutdownDay(0).setRemainingFraction(0.0));
        // home act:
        episimConfig.addContainerParams(new EpisimConfigGroup.InfectionParams("home"));

        RunEpisim.runSimulation(config, it);
    }

}
