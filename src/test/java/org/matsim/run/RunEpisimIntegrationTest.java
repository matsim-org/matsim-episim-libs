package org.matsim.run;

import com.google.common.collect.Lists;
import com.google.inject.*;
import com.google.inject.util.Modules;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.*;
import org.matsim.episim.model.ConfigurableProgressionModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.testing.TestType;
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
import java.util.Map;

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
	private VaccinationConfigGroup vaccinationConfig;
	private TestingConfigGroup testingConfig;
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
		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new TestScenario(utils, it)));

		episimConfig = injector.getInstance(EpisimConfigGroup.class);
		tracingConfig = injector.getInstance(TracingConfigGroup.class);
		vaccinationConfig = injector.getInstance(VaccinationConfigGroup.class);
		testingConfig = injector.getInstance(TestingConfigGroup.class);
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

		tracingConfig.setTracingDelay_days(1 );
		tracingConfig.setTracingProbability(0.75);
		tracingConfig.setTracingCapacity_pers_per_day(50_000);
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

	@Test
	public void testVaccination() throws IOException {

		vaccinationConfig.setVaccinationCapacity_pers_per_day(Map.of(
			LocalDate.MIN, 100
		));

		runner.run(it);

		Path baseCase = Path.of(utils.getClassInputDirectory(), utils.getMethodName().replace("Vaccination", "BaseCase"));
		List<String> baseLines = Files.readAllLines(baseCase.resolve("infections.txt"));

		List<String> cmpLines = Files.readAllLines(Path.of(utils.getOutputDirectory(), "infections.txt"));

		assertThat(baseLines).isNotEqualTo(cmpLines);
	}

	@Test
	public void testTesting() {

		testingConfig.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);
		testingConfig.setActivities(List.of("edu", "leisure"));
		testingConfig.getParams(TestType.RAPID_TEST).setTestingCapacity_pers_per_day(Integer.MAX_VALUE);
		testingConfig.getParams(TestType.RAPID_TEST).setTestingRate(0.8);

		runner.run(it);
	}

	public static class TestScenario extends AbstractModule {

		private final MatsimTestUtils utils;
		private final int it;

		TestScenario(MatsimTestUtils utils) {
			this(utils, 1);
		}


		public TestScenario(MatsimTestUtils utils, int it) {
			this.utils = utils;
			this.it = it;
		}

		@Override
		protected void configure() {
			bind(ProgressionModel.class).to(ConfigurableProgressionModel.class).in(Singleton.class);
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
			episimConfig.setCalibrationParameter(0.01 / it);
			episimConfig.setThreads(2);
			episimConfig.setEndEarly(true);

			config.controler().setOutputDirectory(utils.getOutputDirectory());

			OpenBerlinScenario.addDefaultParams(episimConfig);

			return config;
		}

	}
}
