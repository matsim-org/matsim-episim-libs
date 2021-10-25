/*-
 * #%L
 * MATSim Episim
 * %%
 * Copyright (C) 2020 matsim-org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.matsim.episim;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.model.*;
import org.matsim.episim.model.activity.ActivityParticipationModel;
import org.matsim.episim.model.activity.AllParticipationModel;
import org.matsim.episim.model.progression.DefaultDiseaseStatusTransitionModel;
import org.matsim.episim.model.progression.DiseaseStatusTransitionModel;
import org.matsim.episim.model.testing.DefaultTestingModel;
import org.matsim.episim.model.testing.TestingModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.ShutdownPolicy;
import org.matsim.episim.reporting.AsyncEpisimWriter;
import org.matsim.episim.reporting.EpisimWriter;

import javax.inject.Named;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides the default bindings needed for Episim.
 */
public class EpisimModule extends AbstractModule {


	@Override
	protected void configure() {

		binder().requireExplicitBindings();

		// Main model classes regarding progression / infection etc..
		bind(ContactModel.class).to(DefaultContactModel.class).in(Singleton.class);
		bind(InfectionModel.class).to(DefaultInfectionModel.class).in(Singleton.class);
		bind(ProgressionModel.class).to(ConfigurableProgressionModel.class).in(Singleton.class);
		bind(DiseaseStatusTransitionModel.class).to(DefaultDiseaseStatusTransitionModel.class).in(Singleton.class);
		bind(FaceMaskModel.class).to(DefaultFaceMaskModel.class).in(Singleton.class);
		bind(ShutdownPolicy.class).to(FixedPolicy.class).in(Singleton.class);
		bind(InitialInfectionHandler.class).to(RandomInitialInfections.class).in(Singleton.class);
		bind(VaccinationModel.class).to(RandomVaccination.class).in(Singleton.class);
		bind(TestingModel.class).to(DefaultTestingModel.class).in(Singleton.class);
		bind(ActivityParticipationModel.class).to(AllParticipationModel.class).in(Singleton.class);

		// Internal classes, should rarely be needed to be reconfigured
		bind(EpisimRunner.class).in(Singleton.class);
		bind(ReplayHandler.class).in(Singleton.class);
		bind(InfectionEventHandler.class).in(Singleton.class);
		bind(EpisimReporting.class).in(Singleton.class);

		Multibinder.newSetBinder(binder(), SimulationStartListener.class);
	}

	@Provides
	@Singleton
	public Scenario scenario(Config config) {

		// guice will use no args constructor by default, we check if this config was initialized
		// this is only the case when no explicit binding are required
		if (config.getModules().size() == 0)
			throw new IllegalArgumentException("Please provide a config module or binding.");

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);

		// save some time for not needed inputs
//		config.facilities().setInputFile(null); // facilities are needed for location-based-restrictions

		return ScenarioUtils.loadScenario(config);
	}

	@Provides
	@Singleton
	public EpisimConfigGroup episimConfigGroup(Config config) {
		return ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
	}

	@Provides
	@Singleton
	public TracingConfigGroup tracingConfigGroup(Config config) {
		return ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
	}

	@Provides
	@Singleton
	public VaccinationConfigGroup vaccinationConfigGroup(Config config) {
		return ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
	}

	@Provides
	@Singleton
	public TestingConfigGroup testingConfigGroup(Config config) {
		return ConfigUtils.addOrGetModule(config, TestingConfigGroup.class);
	}

	@Provides
	@Singleton
	public VirusStrainConfigGroup strainConfigGroup(Config config) {
		return ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
	}

	@Provides
	@Singleton
	public EpisimWriter episimWriter(EpisimConfigGroup episimConfig) {

		// Async writer is used for huge event number
		if (Runtime.getRuntime().availableProcessors() > 1 && episimConfig.getWriteEvents() != EpisimConfigGroup.WriteEvents.episim)
			// by default only one episim simulation is running
			return new AsyncEpisimWriter(1);
		else
			return new EpisimWriter();
	}

	@Provides
	@Singleton
	public EventsManager eventsManager() {
		return EventsUtils.createEventsManager();
	}

	@Provides
	@Singleton
	public SplittableRandom splittableRandom(Config config) {
		return new SplittableRandom(config.global().getRandomSeed());
	}

	@Provides
	@Named("policy")
	@Singleton
	public com.typesafe.config.Config policyConfig(EpisimConfigGroup config) {
		return config.getPolicy();
	}


	@Provides
	@Singleton
	public ExecutorService executorService(EpisimConfigGroup episimConfig) {

		if (episimConfig.getThreads() > 1)
			return Executors.newFixedThreadPool(episimConfig.getThreads());
		else
			return Executors.newSingleThreadScheduledExecutor();
	}

}
