package org.matsim.run;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.testcases.MatsimTestUtils;

public class RunEpisimSnapshotTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();
	private EpisimConfigGroup episimConfig;
	private TracingConfigGroup tracingConfig;
	private EpisimRunner runner;

	@Before
	public void setup() {
		OutputDirectoryLogging.catchLogEntries();
		Injector injector = Guice.createInjector(new EpisimModule(), new RunEpisimIntegrationTest.TestScenario(utils));

		episimConfig = injector.getInstance(EpisimConfigGroup.class);
		tracingConfig = injector.getInstance(TracingConfigGroup.class);
		runner = injector.getInstance(EpisimRunner.class);
	}

	@Test
	public void fromSnapshot() {

		episimConfig.setPolicyConfig(FixedPolicy.config()
				.shutdown(1, "freight")
				.shutdown(6, "leisure", "edu", "business")
				.restrict(6, 0.2, "work", "other")
				.restrict(6, 0.3, "shop", "errands")
				.build()
		);

		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(10);

		runner.run(40);
	}
}
