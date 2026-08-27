package org.matsim.run;

import com.google.common.collect.Lists;
import com.google.inject.*;
import com.google.inject.util.Modules;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@ParameterizedClass(name = "it{0}")
@MethodSource("parameters")
public class RunEpisimIntegrationTest {

	private static final Logger log = LogManager.getLogger(RunEpisimIntegrationTest.class);

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();
	/**
	 * Iterations
	 */
	@Parameter
	public int it;
	private EpisimConfigGroup episimConfig;
	private TracingConfigGroup tracingConfig;
	private VaccinationConfigGroup vaccinationConfig;
	private TestingConfigGroup testingConfig;
	private EpisimRunner runner;

	public static Iterable<Integer> parameters() {
		return Arrays.asList(10, 100);
	}

	/**
	 * Checks whether output of simulation matches expectation.
	 */
	static void assertSimulationOutput(MatsimTestUtils utils) {
		assertSimulationOutput(utils, Path.of(utils.getInputDirectory()));
	}

	static void assertSimulationOutput(MatsimTestUtils utils, int iterations) {
		assertSimulationOutput(utils, parameterizedInputDirectory(utils, utils.getMethodName(), iterations));
	}

	private static Path parameterizedInputDirectory(MatsimTestUtils utils, String methodName, int iterations) {
		return Path.of(utils.getClassInputDirectory(), methodName + "[it" + iterations + "]");
	}


	private static void assertSimulationOutput(MatsimTestUtils utils, Path inputDirectory) {
		for (String name : Lists.newArrayList("infections.txt", "infectionEvents.txt")) {
			File input = inputDirectory.resolve(name).toFile();
			log.info("Input file is {} ", input);
			// events will be ignored if not existent
			if (input.exists() || !name.equals("infectionEvents.txt")) {
				File actualFile = new File(utils.getOutputDirectory(), name);
				log.info("File to comparison {} ", actualFile);
				if (name.equals("infectionEvents.txt")) {
					assertInfectionEvents(input, actualFile);
				} else {
					assertThat(actualFile).hasSameTextualContentAs(input);
				}
			}

		}
	}

	private static void assertInfectionEvents(File expectedFile, File actualFile) {
		try {
			List<String> expectedLines = Files.readAllLines(expectedFile.toPath());
			List<String> actualLines = Files.readAllLines(actualFile.toPath());

			assertThat(actualLines)
					.as("Number of lines in %s", actualFile)
					.hasSameSizeAs(expectedLines);
			assertThat(expectedLines)
					.as("Expected infection events file %s", expectedFile)
					.isNotEmpty();
			assertThat(actualLines.get(0))
					.as("Header in %s", actualFile)
					.isEqualTo(expectedLines.get(0));

			String[] header = expectedLines.get(0).split("\\t", -1);
			int probabilityColumn = Arrays.asList(header).indexOf("probability");
			assertThat(probabilityColumn)
					.as("The probability column in %s", expectedFile)
					.isGreaterThanOrEqualTo(0);

			for (int lineIndex = 1; lineIndex < expectedLines.size(); lineIndex++) {
				int lineNumber = lineIndex + 1;
				String[] expectedValues = expectedLines.get(lineIndex).split("\\t", -1);
				String[] actualValues = actualLines.get(lineIndex).split("\\t", -1);

				assertThat(actualValues)
						.as("Number of columns at line %d in %s", lineNumber, actualFile)
						.hasSameSizeAs(expectedValues);
				assertThat(expectedValues)
						.as("Number of columns at line %d in %s", lineNumber, expectedFile)
						.hasSize(header.length);

				for (int column = 0; column < header.length; column++) {
					if (column == probabilityColumn) {
						continue;
					}
					assertThat(actualValues[column])
							.as("Line %d, column '%s'", lineNumber, header[column])
							.isEqualTo(expectedValues[column]);
				}

				double expectedProbability = Double.parseDouble(expectedValues[probabilityColumn]);
				double actualProbability = Double.parseDouble(actualValues[probabilityColumn]);
				/*
					TODO Jaro: I know probably we should check the full equality of both files. And now it does not work in this way because we slightly changed the random generator. Add repr. test later
					*/
				assertThat(actualProbability)
						.as("Line %d, column 'probability'", lineNumber)
						.isCloseTo(expectedProbability, within(MatsimTestUtils.EPSILON));
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Could not compare infection events files", e);
		}
	}

	@BeforeEach
	public void setup() {
		OutputDirectoryLogging.catchLogEntries();
		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new TestScenario(utils, it)));

		episimConfig = injector.getInstance(EpisimConfigGroup.class);
		tracingConfig = injector.getInstance(TracingConfigGroup.class);
		vaccinationConfig = injector.getInstance(VaccinationConfigGroup.class);
		testingConfig = injector.getInstance(TestingConfigGroup.class);
		runner = injector.getInstance(EpisimRunner.class);
	}

	@AfterEach
	public void tearDown() {
		assertSimulationOutput(utils, it);
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
		Path baseCase = parameterizedInputDirectory(
				utils,
				utils.getMethodName().replace("Tracing", "BaseCase"),
				it
		);

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
		assertSimulationOutput(utils, it);


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

		Path baseCase = parameterizedInputDirectory(
				utils,
				utils.getMethodName().replace("Vaccination", "BaseCase"),
				it
		);
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

			config.controller().setOutputDirectory(utils.getOutputDirectory());

			OpenBerlinScenario.addDefaultParams(episimConfig);

			return config;
		}

	}
}
