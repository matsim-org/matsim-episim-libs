package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import javax.annotation.Nullable;


/**
 * This batch run explores different random seeds via different snapshot times.
 */
public class StabilityRuns implements BatchRun<StabilityRuns.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return new SnzBerlinScenario25pct2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("experimental", "stability6");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinScenario25pct2020 module = new SnzBerlinScenario25pct2020();
		Config config = module.config();


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setTracingCapacity_per_day(params.tracing);

		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		//episimConfig.setCalibrationParameter(params.calibParameter);

		if (params.startFrom > 0)
			episimConfig.setStartFromSnapshot(BatchRun.resolveForCluster(SnzBerlinScenario25pct2020.INPUT,
					String.format("episim-snapshot5-%03d.zip", params.startFrom)));

		config.global().setRandomSeed(params.seed);

		return config;
	}

	public static final class Params {

		@GenerateSeeds(400)
		long seed;

		@IntParameter({120})
		int startFrom;

		@IntParameter({0, Integer.MAX_VALUE})
		int tracing;

	}

}
