package org.matsim.run;

import com.google.inject.*;
import com.google.inject.util.Modules;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.*;
import org.matsim.episim.model.*;
import org.matsim.episim.model.activity.ActivityParticipationModel;
import org.matsim.episim.model.activity.DefaultParticipationModel;
import org.matsim.episim.model.testing.TestType;
import org.matsim.episim.model.progression.AgeDependentDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.testcases.MatsimTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;

import static org.matsim.run.RunEpisimIntegrationTest.assertSimulationOutput;

@RunWith(Parameterized.class)
public class RunSnzIntegrationTest {

	private static final int ITERATIONS = 80;
	static final Path INPUT = EpisimUtils.resolveInputPath("../public-svn/matsim/scenarios/countries/de/episim/openDataModel/input");

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	@Parameterized.Parameter
	public SnzBerlinProductionScenario.Restrictions r;

	private EpisimRunner runner;
	private EpisimConfigGroup episimConfig;
	private TracingConfigGroup tracingConfig;
	private VaccinationConfigGroup vaccinationConfig;
	private TestingConfigGroup testingConfig;
	private boolean skipped = true;

	@Parameterized.Parameters(name = "restriction-{0}")
	public static Iterable<SnzBerlinProductionScenario.Restrictions> parameters() {
		return Arrays.asList(
				SnzBerlinProductionScenario.Restrictions.yes, SnzBerlinProductionScenario.Restrictions.onlyEdu);
	}

	@Before
	public void setup() {
		OutputDirectoryLogging.catchLogEntries();

		// Run only if input is present
		Assume.assumeTrue(Files.exists(INPUT) && Files.isDirectory(INPUT));
		skipped = false;

		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new SnzTestScenario(utils, r)));

		episimConfig = injector.getInstance(EpisimConfigGroup.class);
		tracingConfig = injector.getInstance(TracingConfigGroup.class);
		vaccinationConfig = injector.getInstance(VaccinationConfigGroup.class);
		testingConfig = injector.getInstance(TestingConfigGroup.class);
		runner = injector.getInstance(EpisimRunner.class);
	}

	@After
	public void tearDown() {
		if (!skipped)
			assertSimulationOutput(utils);
	}

	@Test
	public void testBaseCase() throws IOException {
		runner.run(ITERATIONS);
	}

	@Test
	public void tracing() {
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(ITERATIONS / 2);
		runner.run(ITERATIONS);
	}

	@Test
	public void testing() {

		testingConfig.setStrategy(TestingConfigGroup.Strategy.ACTIVITIES);
		testingConfig.setActivities(List.of("work", "leisure"));
		testingConfig.getParams(TestType.RAPID_TEST).setTestingCapacity_pers_per_day(Integer.MAX_VALUE);

		runner.run(ITERATIONS);
	}

	@Test
	public void curfew() {

		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config()
				.restrict(ITERATIONS / 2, Restriction.ofClosingHours(22, 6), "leisure")
				.build());

		runner.run(ITERATIONS);
	}

	static class SnzTestScenario extends AbstractModule {

		private final MatsimTestUtils utils;
		private final SnzBerlinProductionScenario scenario;

		SnzTestScenario(MatsimTestUtils utils, SnzBerlinProductionScenario.Restrictions r) {
			this.utils = utils;
			this.scenario = new SnzBerlinProductionScenario.Builder()
					.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
					.setDiseaseImport(SnzBerlinProductionScenario.DiseaseImport.yes)
					.setRestrictions(r)
					.build();
		}

		@Override
		protected void configure() {
			bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
			bind(DiseaseStatusTransitionModel.class).to(AgeDependentDiseaseStatusTransitionModel.class).in(Singleton.class);
			bind(InfectionModel.class).to(AgeAndProgressionDependentInfectionModelWithSeasonality.class).in(Singleton.class);
			bind(ActivityParticipationModel.class).to(DefaultParticipationModel.class);
		}

		@Provides
		@Singleton
		public Config config() {

			Config config = scenario.config();

			EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

			episimConfig.clearInputEventsFiles();
			episimConfig.addInputEventsFile(INPUT.resolve("sample/be_2020-week_snz_episim_events_wt_1pt_split.xml.gz").toString())
					.addDays(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

			episimConfig.addInputEventsFile(INPUT.resolve("sample/be_2020-week_snz_episim_events_sa_1pt_split.xml.gz").toString())
					.addDays(DayOfWeek.SATURDAY);

			episimConfig.addInputEventsFile(INPUT.resolve("sample/be_2020-week_snz_episim_events_so_1pt_split.xml.gz").toString())
					.addDays(DayOfWeek.SUNDAY);

			config.plans().setInputFile(INPUT.resolve(
					"sample/be_2020-week_snz_entirePopulation_emptyPlans_withDistricts_1pt_split.xml.gz").toString());

			config.vehicles().setVehiclesFile(INPUT.resolve(
					"be_2020-vehicles.xml.gz").toString());

			episimConfig.setCalibrationParameter(1.7E-5 * 25);
			episimConfig.setStartDate("2020-02-16");
			episimConfig.setSampleSize(0.01);
			episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.restore);
			episimConfig.setThreads(2);

			config.controler().setOutputDirectory(utils.getOutputDirectory());

			return config;
		}

		@Provides
		@Singleton
		public Scenario scenario(Config config) {
			return scenario.scenario(config);
		}

	}

}
