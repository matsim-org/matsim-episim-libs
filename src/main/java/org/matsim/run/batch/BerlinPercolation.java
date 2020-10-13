package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
import org.matsim.run.modules.SyntheticScenario;

import javax.annotation.Nullable;

/**
 * Percolation runs for berlin
 */
public class BerlinPercolation implements BatchRun<BerlinPercolation.Params> {

	private static final Logger log = LogManager.getLogger(BerlinPercolation.class);


	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "percolation");
	}

	@Nullable
	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinWeekScenario2020(25, false, true, OldSymmetricContactModel.class);
	}

	@Override
	public Config prepareConfig(int id, Params params) {


		SnzBerlinWeekScenario2020 module = new SnzBerlinWeekScenario2020(25, false, true, OldSymmetricContactModel.class);
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInitialInfections(20);

		// reduced calib param
		episimConfig.setCalibrationParameter(1.07e-5 * params.fraction);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		// no tracing
		tracingConfig.setPutTraceablePersonsInQuarantineAfterDay(Integer.MAX_VALUE);

		FixedPolicy.ConfigBuilder builder = FixedPolicy.config();

		// ci correction that should lead to R approx 1
		//builder.restrict(20, 0.45, SnzBerlinWeekScenario2020.DEFAULT_ACTIVITIES);

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(200)
		public long seed;

		@Parameter({0.15, 0.2, 0.25})
		public double fraction;

	}
}
