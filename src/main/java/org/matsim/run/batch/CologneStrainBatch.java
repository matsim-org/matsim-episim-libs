package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.CologneStrainScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Batch to run all seeds for the cologne strain scenario.
 */
public class CologneStrainBatch implements BatchRun<CologneStrainBatch.Params> {

	@Override
	public CologneStrainScenario getBindings(int id, @Nullable Params params) {
		return new CologneStrainScenario(1.964770489586272);
	}

	@Override
	public BatchRun.Metadata getMetadata() {
		return BatchRun.Metadata.of("cologne", "strain");
	}

	@Nullable
	@Override
	public Config prepareConfig(int id, Params params) {

		CologneStrainScenario scenario = getBindings(id, params);

		Config config = scenario.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(1.2202709637374418e-05);
		episimConfig.setSnapshotInterval((int) ChronoUnit.DAYS.between(episimConfig.getStartDate(), LocalDate.parse("2021-01-14")));
		episimConfig.setSnapshotPrefix("strain_base_" + params.seed);

		return config;
	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, CologneStrainBatch.class.getName(),
				RunParallel.OPTION_PARAMS, CologneStrainBatch.Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(350),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


	public static final class Params {

		@GenerateSeeds(12)
		long seed;

	}
}
