package org.matsim.run;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.testcases.MatsimTestUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class RunEpisimSnapshotTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();
	private Config config;
	private EpisimConfigGroup episimConfig;
	private EpisimRunner runner;

	@Parameterized.Parameter
	public TracingConfigGroup.Strategy strategy;

	@Parameterized.Parameters(name = "tracing-{0}")
	public static Iterable<TracingConfigGroup.Strategy> parameters() {
		return Arrays.asList(TracingConfigGroup.Strategy.INDIVIDUAL_ONLY, TracingConfigGroup.Strategy.LOCATION_WITH_TESTING, TracingConfigGroup.Strategy.IDENTIFY_SOURCE);
	}

	@Before
	public void setup() {
		OutputDirectoryLogging.catchLogEntries();
		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new RunEpisimIntegrationTest.TestScenario(utils)));

		config = injector.getInstance(Config.class);
		episimConfig = injector.getInstance(EpisimConfigGroup.class);
		runner = injector.getInstance(EpisimRunner.class);
		episimConfig.setPolicyConfig(FixedPolicy.config()
				.shutdown(1, "freight")
				.restrict(6, 0.2, "leisure", "edu", "business")
				.restrict(6, 0.2, "work", "other")
				.restrict(6, 0.3, "shop", "errands")
				.build()
		);

		TracingConfigGroup tracingConfig = injector.getInstance(TracingConfigGroup.class);
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(5);
		tracingConfig.setTracingProbability(0.75);
		tracingConfig.setEquipmentRate(0.75);
		tracingConfig.setStrategy(strategy);
		tracingConfig.setLocationThreshold(1);

	}

	@Test
	public void compareSnapshots() {

		episimConfig.setSnapshotInterval(15);
		runner.run(30);

		setup();

		String fromSnapshot = utils.getOutputDirectory().replace(utils.getMethodName(), "fromSnapshot");
		episimConfig.setStartFromSnapshot(utils.getOutputDirectory() + "episim-snapshot-015-1970-01-15.zip");
		config.controler().setOutputDirectory(fromSnapshot);

		runner.run(30);

		for (File file : Objects.requireNonNull(new File(utils.getOutputDirectory()).listFiles())) {

			// check event files
			if (file.getName().equals("events")) {
				for (File event : Objects.requireNonNull(file.listFiles())) {
					assertThat(event)
							.hasSameBinaryContentAs(new File(fromSnapshot, "events/" +  event.getName()));
				}
			}

			if (file.isDirectory() || file.getName().endsWith(".zip") || file.getName().endsWith(".xml") || file.getName().endsWith(".gz")) continue;

			assertThat(file)
					.hasSameTextualContentAs(new File(fromSnapshot, file.getName()));
		}

	}

	@Test
	@Ignore("Snapshot file not checked into git because of its size")
	public void fixedSnapshot() {

		episimConfig.setStartFromSnapshot(utils.getInputDirectory() + "episim-snapshot-015-1970-01-15.zip");
		runner.run(30);

		RunEpisimIntegrationTest.assertSimulationOutput(utils);
	}
}
