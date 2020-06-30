package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.run.modules.SnzBerlinScenario25pct2020;
import org.matsim.run.modules.SnzBerlinSuperSpreaderScenario;

/**
 * Run to analyze different viral load and susceptibility for persons.
 */
public class BerlinSuperSpreading implements BatchRun<BerlinSuperSpreading.Params> {

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "superSpreading3");
	}

	@Override
	public AbstractModule getBindings(int id, Object params) {
		return new SnzBerlinSuperSpreaderScenario();
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinScenario25pct2020 module = new SnzBerlinScenario25pct2020();

		Config config = module.config();

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		// increase calib parameter
		episimConfig.setCalibrationParameter(params.calib);

		// start a bit earlier
		episimConfig.setStartDate("2020-02-16");

		// evtl. Ci correction auf 0.35

		return config;
	}

	public static final class Params {

		//@GenerateSeeds(200)
		//private long seed;

		@Parameter({0.000_012_5, 0.000_013_0})
		private double calib;

		@Parameter({0.75})
		private double sigmaInfect;

		@Parameter({0.75})
		private double sigmaSusc;
	}
}
