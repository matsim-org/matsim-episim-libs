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
package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ConfigurableProgressionModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Batch run for tracing options.
 */
public final class BerlinClusterTracing implements BatchRun<BerlinClusterTracing.Params> {

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "clusterTracing");
	}

	@Override
	public Module getBindings(int id, Params params) {
		return Modules.override(new SnzBerlinProductionScenario()).with(new AbstractModule() {
			@Override
			protected void configure() {
				if (params != null) {
					bindConstant().annotatedWith(Names.named("symptomatic")).to(params.symptomatic);
					bind(ProgressionModel.class).to(CustomProgressionModel.class);
				}
			}
		}) ;
	}

	@Override
	public Config prepareConfig(int id, BerlinClusterTracing.Params params) {

		Config config = new SnzBerlinProductionScenario().config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		// unrestricted
		episimConfig.setPolicy(FixedPolicy.class, FixedPolicy.config().build());

		// Filter some runs without tracing
		if (params.tracingStrategy == TracingConfigGroup.Strategy.NONE && params.tracingCapacity != Integer.MAX_VALUE)
			return null;

		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(30);
		tracingConfig.setStrategy(params.tracingStrategy);
		tracingConfig.setLocationThreshold(2);
		tracingConfig.setCapacityType(TracingConfigGroup.CapacityType.PER_PERSON);
		tracingConfig.setTracingCapacity_pers_per_day(params.tracingCapacity);


		return config;
	}

	public static final class Params {

		@GenerateSeeds(30)
		long seed;

		@EnumParameter(value = TracingConfigGroup.Strategy.class, ignore = "RANDOM")
		TracingConfigGroup.Strategy tracingStrategy;

		@Parameter({0.5, 0.6, 0.7, 0.8})
		double symptomatic;

		@IntParameter({200, 500, 1000, Integer.MAX_VALUE})
		int tracingCapacity;


	}

	private static final class CustomProgressionModel extends AgeDependentProgressionModel {

		private final double symptomatic;

		@Inject
		public CustomProgressionModel(
				@Named("symptomatic") double symptomatic,
				SplittableRandom rnd, EpisimConfigGroup episimConfig, TracingConfigGroup tracingConfig) {
			super(rnd, episimConfig, tracingConfig);
			this.symptomatic = symptomatic;
		}

		@Override
		protected double getProbaOfTransitioningToShowingSymptoms(EpisimPerson person) {
			return symptomatic;
		}
	}

}

