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
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.OpenBerlinScenario;
import org.matsim.testcases.MatsimTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

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
	private TracingConfigGroup tracingConfig;
	private EpisimRunner runner;

	@Parameterized.Parameters(name = "it{0}")
	public static Iterable<Integer> parameters() {
		return Arrays.asList(10, 100);
	}

	/**
	 * Checks whether output of simulation matches expectation.
	 */
	static void assertSimulationOutput(MatsimTestUtils utils) {
		for (String name : Lists.newArrayList("infections.txt", "infectionEvents.txt")) {
			File input = new File(utils.getInputDirectory(), name);
			// events will be ignored if not existent
			if (input.exists() || !name.equals("infectionEvents.txt"))
				assertThat(new File(utils.getOutputDirectory(), name)).hasSameTextualContentAs(input);
		}
	}

	@Before
	public void setup() {
		OutputDirectoryLogging.catchLogEntries();
		Injector injector = Guice.createInjector(new EpisimModule(), new TestScenario(utils));

		episimConfig = injector.getInstance(EpisimConfigGroup.class);
		tracingConfig = injector.getInstance(TracingConfigGroup.class);
		runner = injector.getInstance(EpisimRunner.class);
	}

	@After
	public void tearDown() {
		assertSimulationOutput(utils);
	}

	@Test
	public void testBaseCase() throws IOException {
		runner.run(it);
	}

	@Test
	public void testTracing() throws IOException {

		// day when tracing starts
		int tDay = it / 2;

		tracingConfig.setTracingDelay(1);
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(tDay);

		runner.run(it);

		// the input of the base case
		Path baseCase = Path.of(utils.getClassInputDirectory(), utils.getMethodName().replace("Tracing", "BaseCase"));

		List<String> baseLines = Files.readAllLines(baseCase.resolve("infections.txt"));
		List<String> cmpLines = Files.readAllLines(Path.of(utils.getOutputDirectory(), "infections.txt"));

		// Check that first 50% are identical (before tracking started)
		assertThat(baseLines.subList(0, tDay)).isEqualTo(cmpLines.subList(0, tDay));
		assertThat(baseLines.subList(tDay, Math.min(it, baseLines.size())))
				.isNotEqualTo(cmpLines.subList(tDay, Math.min(it, cmpLines.size())));
	}


	@Test
	public void testPlausibleShutdown() {

		LocalDate start = LocalDate.of(2020, 2, 1);

		episimConfig.setStartDate(start);
		episimConfig.setPolicyConfig(FixedPolicy.config()
				.shutdown(1, "freight")
				.shutdown(6, "leisure", "edu", "business")
				.restrict(6, 0.2, "work", "other")
				.restrict(6, 0.3, "shop", "errands")
				.build()
		);

		runner.run(it);
		assertSimulationOutput(utils);


		// re-test with fixed date config, which should be the same result
		setup();
		episimConfig.setStartDate(start);
		episimConfig.setPolicyConfig(FixedPolicy.config()
				.restrict(start, 0.0, "freight")
				.restrict(start.withDayOfMonth(6), 0.0, "leisure", "edu", "business")
				.restrict(start.withDayOfMonth(6), 0.2, "work", "other")
				.restrict(start.withDayOfMonth(6), 0.3, "shop", "errands")
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

	static class TestScenario extends AbstractModule {

		private final MatsimTestUtils utils;

		TestScenario(MatsimTestUtils utils) {
			this.utils = utils;
		}

		@Provides
		@Singleton
		public Config config() {
			Config config = ConfigUtils.createConfig(new EpisimConfigGroup());
			EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

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
