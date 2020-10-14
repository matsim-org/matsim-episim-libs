package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;

import javax.annotation.Nullable;


/**
 * Runs {@code variants} from different seeds to analyze the potential of second waves.
 */
public class SecondWavePotential implements BatchRun<SecondWavePotential.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinScenario25pct2020();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("experimental", "secondWave");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinScenario25pct2020 module = new SnzBerlinScenario25pct2020();
		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		long seed;
		switch (params.variant) {
			case "base":
				seed = 4711L;
				break;
			case "low":
				seed = -5393578045340737736L;
				break;
			case "high":
				seed = 4546857235350971184L;
				break;
			default:
				throw new IllegalStateException("Unknown run variant");
		}

		// Used to generate snapshots for base case
		if (params.seed == 0) {
			config.global().setRandomSeed(seed);
			episimConfig.setSnapshotInterval(30);
		} else {
			episimConfig.setStartFromSnapshot(BatchRun.resolveForCluster(SnzBerlinScenario25pct2020.INPUT,
					String.format("variant_%s/episim-snapshot-%03d.zip", params.variant, params.startFrom)));

			episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
			config.global().setRandomSeed(params.seed);

		}


		return config;
	}

	public static final class Params {

		@StringParameter({"base", "high", "low"})
		String variant;

		@GenerateSeeds(200)
		long seed = 0;

		@IntParameter({60, 120})
		int startFrom;

	}

}
