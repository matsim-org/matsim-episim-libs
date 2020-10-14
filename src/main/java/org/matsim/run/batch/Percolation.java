package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.run.modules.SyntheticScenario;

import javax.annotation.Nullable;

/**
 * Compares different thetas in the synthetic scenario.
 */
public class Percolation implements BatchRun<Percolation.Params> {

	private static final Logger log = LogManager.getLogger(Percolation.class);

	/**
	 * Base configuration
	 */
	private static final SyntheticBatch.Params base = new SyntheticBatch.Params(
			1000, 1, 1, 1, 10, OldSymmetricContactModel.class, 1
	);

	@Override
	public Metadata getMetadata() {
		return Metadata.of("synthetic", "percolation");
	}

	@Nullable
	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SyntheticScenario(base);
	}

	@Nullable
	@Override
	public Config prepareConfig(int id, Params params) {
		Config config = new SyntheticScenario(base).config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(params.theta);

		return config;
	}


	public static final class Params {

		@GenerateSeeds(100)
		public long seed;

		/**
		 * Calibration parameter.
		 */
		@Parameter({1e-5, 1.5e-5, 2e-5, 2.5e-5, 3e-5, 3.5e-5})
		public double theta;


	}
}
