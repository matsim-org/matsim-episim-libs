package org.matsim.episim;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.model.SimulationListener;
import org.matsim.run.RunEpisimIntegrationTest;
import org.matsim.testcases.MatsimTestUtils;

import java.time.LocalDate;

public class InfectionEventHandlerTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();
	private EpisimRunner runner;

	@Before
	public void setup() {
		OutputDirectoryLogging.catchLogEntries();
		Injector injector = Guice.createInjector(Modules.override(new EpisimModule())
				.with(new RunEpisimIntegrationTest.TestScenario(utils, 20), new AbstractModule() {
					@Override
					protected void configure() {

						Multibinder<SimulationListener> binder = Multibinder.newSetBinder(binder(), SimulationListener.class);
						binder.addBinding().to(EventUpdater.class);

					}
				}));

		runner = injector.getInstance(EpisimRunner.class);
	}

	@Test
	public void updateEvents() {

		runner.run(20);

	}

	static class EventUpdater implements SimulationListener {

		private final EpisimRunner runner;
		private final EpisimConfigGroup config;

		@Inject
		EventUpdater(EpisimRunner runner, EpisimConfigGroup config) {
			this.runner = runner;
			this.config = config;
		}

		@Override
		public void onIterationStart(int iteration, LocalDate date) {

			if (iteration == 10) {

				// For testing reload events without changes
				runner.updateEvents(config);
			}

		}
	}


}
