package org.matsim.run;

import com.google.common.collect.Lists;
import com.google.inject.*;
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
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.OpenBerlinScenario;
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
	private EpisimConfigGroup episimConfig;
	private EpisimRunner runner;

	@Parameterized.Parameters(name = "it{0}")
	public static Iterable<Integer> parameters() {
		return Arrays.asList(10, 100);
	}

	@Before
	public void setup() {
		OutputDirectoryLogging.catchLogEntries();
		Injector injector = Guice.createInjector(new EpisimModule(), new TestScenario());

		runner = injector.getInstance(EpisimRunner.class);

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
			File input = new File(utils.getInputDirectory(), name);
			// events will be ignored if not existent
			if (input.exists() || !name.equals("infectionEvents.txt"))
				assertThat(new File(utils.getOutputDirectory(), name)).hasSameTextualContentAs(input);
		}
	}

	@Test
	public void testBaseCase() throws IOException {
		runner.run(it);
	}

	@Test
	public void testNoPt() throws IOException {

		episimConfig.setPolicyConfig(FixedPolicy.config()
				.shutdown(5, "pt")
				.build()
		);

		runner.run(it);
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

		runner.run(it);
	}

	@Test
	public void testTotalShutdown() throws IOException {

		// there should be no infections after day 1
		episimConfig.setPolicyConfig(FixedPolicy.config()
				.shutdown(1, OpenBerlinScenario.DEFAULT_ACTIVITIES)
				.build()
		);

		runner.run(it);
	}

	private class TestScenario extends AbstractModule {

		@Provides
		@Singleton
		public Config config() {
			Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
			episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

			episimConfig.setInputEventsFile(
					"https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.4-1pct/output-berlin-v5.4-1pct/berlin-v5.4-1pct.output_events_for_episim.xml.gz");
			// still need to push.  is faster

			episimConfig.setFacilitiesHandling(EpisimConfigGroup.FacilitiesHandling.bln);
			episimConfig.setSampleSize(0.01);
			episimConfig.setCalibrationParameter(2);

			config.controler().setOutputDirectory(utils.getOutputDirectory());

			OpenBerlinScenario.addDefaultParams(episimConfig);

			return config;
		}

	}
}
