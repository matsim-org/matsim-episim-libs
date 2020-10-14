package org.matsim.run;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimModule;
import org.matsim.episim.EpisimRunner;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.testcases.MatsimTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

@RunWith(Parameterized.class)
public class RunEpisimScenarioTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();
	private EpisimRunner runner;

	@Parameterized.Parameter
	public String scenario;

	@Parameterized.Parameters(name = "scenario-{0}")
	public static Iterable<String> parameters() {
		return Arrays.asList("jlm.output_events-0.1.xml.gz", "TAMA.output_events-0.1.xml.gz");
	}

	@Before
	public void setup() {
		OutputDirectoryLogging.catchLogEntries();
		Injector injector = Guice.createInjector(Modules.override(new EpisimModule()).with(new RunEpisimIntegrationTest.TestScenario(utils)));

		Assume.assumeTrue(Files.exists(Path.of(scenario)));

		EpisimConfigGroup episimConfig = injector.getInstance(EpisimConfigGroup.class);
		episimConfig.setInputEventsFile(scenario);

		runner = injector.getInstance(EpisimRunner.class);
		episimConfig.setPolicyConfig(FixedPolicy.config()
				.shutdown(1, "freight")
				.restrict(6, 0.2,"leisure", "edu", "business")
				.restrict(6, 0.2, "work", "other")
				.restrict(6, 0.3, "shop", "errands")
				.build()
		);


		episimConfig.getOrAddContainerParams("school");
		episimConfig.getOrAddContainerParams("tta");
	}

	@Test
	public void runScenario() {

		runner.run(30);

	}
}
