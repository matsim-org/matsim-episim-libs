package org.matsim.episim;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Id;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.episim.model.SimulationListener;
import org.matsim.run.RunEpisimIntegrationTest;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class InfectionEventHandlerTest {

	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils();
	private EpisimRunner runner;

	@BeforeEach
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

	@Test
	public void checkHouseholds() {

		List<EpisimPerson> persons = List.of(
				person("parent", "h1", 35),
				person("child", "h1", 4),
				person("infant", "h2", 0),
				person("sibling", "h2", 9),
				person("noHome", null, 50),
				person("unknownAge", "h3", -1),
				person("kid", "h3", 10)
		);

		assertThat(InfectionEventHandler.checkHouseholds(persons))
				.contains("homeId")
				.contains("fallback household: 1 of 7")
				.contains("households of size 1 (including fallback households): 1 of 4")
				.contains("no adult (age >= 18): 1, persons living in them: 2")
				.contains("age 0 in a household without adult: 1")
				.contains("without age attribute (households containing them are not counted as without adult): 1");

		assertThat(InfectionEventHandler.checkHouseholds(persons.subList(0, 2))).isNull();
	}

	private static EpisimPerson person(String id, String homeId, int age) {
		Attributes attrs = new AttributesImpl();
		if (homeId != null)
			attrs.putAttribute("homeId", homeId);
		if (age >= 0)
			attrs.putAttribute("age", age);

		return new EpisimPerson(Id.createPersonId(id), attrs, null);
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
