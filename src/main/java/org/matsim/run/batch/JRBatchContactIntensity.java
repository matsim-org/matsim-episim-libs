package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;


import javax.annotation.Nullable;

import static org.matsim.episim.EpisimConfigGroup.*;

public class JRBatchContactIntensity implements BatchRun<JRBatchContactIntensity.Params> {

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setLocationBasedContactIntensity(params != null ? params.locationBasedContactIntensity : SnzBerlinProductionScenario.LocationBasedContactIntensity.yes)
				.setLocationBasedRestrictions(params != null ? params.locationBasedRestrictions : DistrictLevelRestrictions.yesForHomeLocation)
				.setActivityHandling(ActivityHandling.startOfDay)
				.setSample(25)
				.createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "locationBasedContactIntensity");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = getBindings(id, params);
		assert module != null;

		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.thetaFactor);

		return config;
	}

	public static final class Params {

		@EnumParameter(EpisimConfigGroup.DistrictLevelRestrictions.class)
		EpisimConfigGroup.DistrictLevelRestrictions locationBasedRestrictions;

		@GenerateSeeds(5)
		public long seed;

		@Parameter({1.0, 1.05, 1.10})
		double thetaFactor;

		@EnumParameter(SnzBerlinProductionScenario.LocationBasedContactIntensity.class)
		SnzBerlinProductionScenario.LocationBasedContactIntensity locationBasedContactIntensity;


	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatchContactIntensity.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_ITERATIONS, Integer.toString(450),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}

