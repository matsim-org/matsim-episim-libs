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
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().setSnapshot(SnzBerlinProductionScenario.Snapshot.no).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "locationBasedContactIntensity");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder().setSnapshot(
				SnzBerlinProductionScenario.Snapshot.no).setSample(25).setLocationBasedContactIntensity(SnzBerlinProductionScenario.LocationBasedContactIntensity.yes)
				.createSnzBerlinProductionScenario();
		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setDistrictLevelRestrictions(DistrictLevelRestrictions.no);
//		episimConfig.setDistrictLevelRestrictions(params.districtLevelRestrictions);

		episimConfig.setActivityHandling(ActivityHandling.startOfDay);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.thetaFactor);
		return config;
	}

	public static final class Params {

//		@EnumParameter(DistrictLevelRestrictions.class)
//		DistrictLevelRestrictions districtLevelRestrictions;

		@GenerateSeeds(1)
		public long seed;

		@Parameter({0.75,1,1.25,1.5})
		double thetaFactor;

//		@EnumParameter(SnzBerlinProductionScenario.LocationBasedContactIntensity.class)
//		SnzBerlinProductionScenario.LocationBasedContactIntensity locationBasedContactIntensity;


		//		@EnumParameter(ActivityHandling.class) //TODO Why does this cause errors?
		//		ActivityHandling activityHandling;

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

