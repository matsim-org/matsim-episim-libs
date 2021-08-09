package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneScenario;

import javax.annotation.Nullable;


/**
 * Calibration for Cologne scenario
 */
public class CologneCalibration implements BatchRun<CologneCalibration.Params> {

	@Override
	public SnzCologneScenario getBindings(int id, @Nullable Params params) {
		return new SnzCologneScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("cologne", "calibration");
	}


	@Override
	public Config prepareConfig(int id, Params params) {

		SnzCologneScenario module = getBindings(id, params);

		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.thetaFactor);


		return config;
	}

	public static final class Params {

		@GenerateSeeds(30)
		public long seed;

		@Parameter({0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5})
		double thetaFactor;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneCalibration.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}

