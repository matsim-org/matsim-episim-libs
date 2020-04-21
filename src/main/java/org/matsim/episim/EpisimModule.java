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
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.model.*;
import org.matsim.episim.reporting.EpisimWriter;

import java.util.SplittableRandom;

/**
 * Provides the default bindings needed for Episim.
 */
public class EpisimModule extends AbstractModule {


	@Override
	protected void configure() {

		binder().requireExplicitBindings();

		// TODO: reporting can not be bound eager, because output path is not constructed yet
		// probably reporting should create directory if needed, instead

		bind(InfectionModel.class).to(DefaultInfectionModel.class).in(Singleton.class);
		bind(ProgressionModel.class).to(DefaultProgressionModel.class).in(Singleton.class);
		bind(FaceMaskModel.class).to(DefaultFaceMaskModel.class).in(Singleton.class);
		bind(EpisimRunner.class).in(Singleton.class);
		bind(ReplayHandler.class).in(Singleton.class);
		bind(InfectionEventHandler.class).in(Singleton.class);
	}

	@Provides
	@Singleton
	public Scenario scenario(Config config) {

		// guice will use no args constructor by default, we check if this config was initialized
		if (config.getModules().size() == 0)
			throw new IllegalArgumentException("Please provide a config module or binding.");

		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);

		// save some time for not needed inputs
		config.facilities().setInputFile(null);
		config.vehicles().setVehiclesFile(null);

		return ScenarioUtils.loadScenario(config);
	}

	@Provides
	@Singleton
	public EpisimConfigGroup episimConfigGroup(Config config) {
		return ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
	}

	@Provides
	@Singleton
	public EpisimReporting episimReporting(Config config) {

		// TODO: differentiate when async writer is needed

		return new EpisimReporting(config, new EpisimWriter());
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

}
