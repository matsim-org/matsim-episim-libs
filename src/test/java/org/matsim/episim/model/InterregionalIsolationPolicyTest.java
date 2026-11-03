package org.matsim.episim.model;

import com.google.inject.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.matsim.episim.model.SymmetricContactModelWithOdeCoupling.CommuterQuarantineMode.*;

public class InterregionalIsolationPolicyTest {
	@Rule public TemporaryFolder temp = new TemporaryFolder();
	private Config config;
	private EpisimConfigGroup epi;
	private Scenario scenario;

	@Before public void setup() throws Exception {
		config = EpisimTestUtils.createTestConfig();
		config.global().setRandomSeed(12345);
		epi = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		epi.setStartDate("2020-08-30");
		epi.setCommuterQuarantineDate("2020-09-01");
		epi.setCommuterQuarantineMode(COMMUTERS);
		epi.setCommuterQuarantineShare(1);
		scenario = ScenarioUtils.createScenario(config);
		for (int i = 0; i < 20; i++) {
			scenario.getPopulation().addPerson(scenario.getPopulation().getFactory().createPerson(Id.createPersonId("p" + i)));
		}
		input("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9");
	}

	private void input(String... ids) throws Exception {
		Path path = temp.newFile().toPath();
		List<String> lines = new ArrayList<>(List.of("personId"));
		lines.addAll(Arrays.asList(ids));
		Files.write(path, lines);
		epi.setCommuterQuarantineInput(path.toString());
	}

	private InterregionalIsolationPolicy policy() {
		return new InterregionalIsolationPolicy(config, epi, scenario);
	}

	@Test public void selectionIsUniquePresentAndMatchedBetweenModes() throws Exception {
		input("p0", "p1", "p0", "missing", "missing", " p2 ", "p3");
		epi.setCommuterQuarantineShare(0.75);
		assertThat(policy().getSelectedPersons()).hasSize(3)
			.isSubsetOf(Id.createPersonId("p0"), Id.createPersonId("p1"), Id.createPersonId("p2"), Id.createPersonId("p3"));
		epi.setCommuterQuarantineMode(GENERAL);
		assertThat(policy().getSelectedPersons()).hasSize(3).isSubsetOf(scenario.getPopulation().getPersons().keySet());
		assertThatThrownBy(() -> policy().getSelectedPersons().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test public void selectionIsNestedAndIndependentOfInputOrder() throws Exception {
		for (var mode : List.of(COMMUTERS, GENERAL)) {
			epi.setCommuterQuarantineMode(mode);
			epi.setCommuterQuarantineShare(0.5);
			var half = policy().getSelectedPersons();
			epi.setCommuterQuarantineShare(0.8);
			assertThat(policy().getSelectedPersons()).hasSize(8).containsAll(half);
			epi.setCommuterQuarantineShare(0.5);
			input("p9", "p8", "p7", "p6", "p5", "p4", "p3", "p2", "p1", "p0");
			assertThat(policy().getSelectedPersons()).isEqualTo(half);
		}
	}

	@Test public void offAndZeroDoNotRequireInputOrDate() {
		epi.setCommuterQuarantineInput(null);
		epi.setCommuterQuarantineDate(null);
		for (var mode : List.of(OFF, COMMUTERS, GENERAL)) {
			epi.setCommuterQuarantineMode(mode);
			epi.setCommuterQuarantineShare(mode == OFF ? 1 : 0);
			assertThat(policy().getSelectedPersons()).isEmpty();
			assertThat(policy().isIsolated(Id.createPersonId("p0"), 500)).isFalse();
		}
	}

	@Test public void invalidSharesFailClearly() {
		for (double share : new double[]{-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
			epi.setCommuterQuarantineShare(share);
			assertThatThrownBy(this::policy).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("[0, 1]");
		}
	}

	@Test public void activationUsesCalendarDateAndSkipsInitialization() {
		var id = Id.createPersonId("p0");
		var policy = policy();
		assertThat(policy.isIsolated(id, 0)).isFalse();
		assertThat(policy.isIsolated(id, 2)).isFalse(); // August 31
		assertThat(policy.isIsolated(id, 3)).isTrue(); // September 1
		assertThat(policy.isIsolated(id, 4)).isTrue();
		for (String date : List.of("2020-08-30", "2020-08-01")) {
			epi.setCommuterQuarantineDate(date);
			assertThat(policy().isIsolated(id, 0)).isFalse();
			assertThat(policy().isIsolated(id, 1)).isTrue();
		}
	}

	@Test public void sharedWithCopiedHandlersWithoutConsumingSimulationRandom() {
		for (var mode : List.of(OFF, COMMUTERS, GENERAL)) {
			for (double share : new double[]{0, 0.5, 1}) {
				epi.setCommuterQuarantineMode(mode);
				epi.setCommuterQuarantineShare(share);
				SplittableRandom rnd = new SplittableRandom(12345);
				Injector parent = Guice.createInjector(new AbstractModule() {
					@Override protected void configure() {
						bind(Config.class).toInstance(config);
						bind(EpisimConfigGroup.class).toInstance(epi);
						bind(Scenario.class).toInstance(scenario);
						bind(SplittableRandom.class).toInstance(rnd);
						bind(InterregionalIsolationPolicy.class).in(Singleton.class);
					}
				});
				var selection = parent.getInstance(InterregionalIsolationPolicy.class);
				for (int handler = 0; handler < 3; handler++) {
					Injector child = GuiceUtils.createCopiedInjector(parent, List.of(new AbstractModule() {
						@Override protected void configure() {
							bind(SplittableRandom.class).toInstance(new SplittableRandom(999));
						}
					}));
					assertThat(child.getInstance(InterregionalIsolationPolicy.class)).isSameAs(selection);
				}
				assertThat(rnd.nextLong()).isEqualTo(new SplittableRandom(12345).nextLong());
			}
		}
	}

	@Test public void reconstructedPolicyHasSameSelectionAndDoesNotMutatePersons() {
		var first = policy();
		var restored = policy();
		assertThat(restored.getSelectedPersons()).isEqualTo(first.getSelectedPersons());
		for (var person : scenario.getPopulation().getPersons().values()) {
			assertThat(person.getAttributes().getAsMap()).isEmpty();
			for (int day : new int[]{0, 2, 3, 20}) {
				assertThat(restored.isIsolated(person.getId(), day)).isEqualTo(first.isIsolated(person.getId(), day));
			}
		}
	}
}
