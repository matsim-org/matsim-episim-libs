package org.matsim.run.batch;

import com.google.inject.AbstractModule;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.TracingConfigGroup.CapacityType;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;

import java.time.LocalDate;
import java.util.Map;



/**
 * Interventions for symmetric Berlin week model with different contact models
 */
public class SMBatch implements BatchRun<SMBatch.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

//		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		tracingConfig.setCapacityType(CapacityType.PER_CONTACT_PERSON);

		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), params.tracingCapacityApril,
				LocalDate.of(2020, 6, 15), params.tracingCapacityJune
		));

		return config;
	}

	public static final class Params {

		@GenerateSeeds(3)
		public long seed;

		@IntParameter({100, 200, 300, 400})
		int tracingCapacityApril;

		@IntParameter({500, 1000, 1500, 2000, Integer.MAX_VALUE})
		int tracingCapacityJune;

	}


}

