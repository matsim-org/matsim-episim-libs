package org.matsim.run.batch;

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
				.setLocationBasedContactIntensity(SnzBerlinProductionScenario.LocationBasedContactIntensity.yes)
				//				.setLocationBasedContactIntensity(params != null ? params.locationBasedContactIntensity : SnzBerlinProductionScenario.LocationBasedContactIntensity.yes)
				.setLocationBasedRestrictions(params != null ? params.locationBasedRestrictions : DistrictLevelRestrictions.yesForHomeLocation)
				.setActivityHandling(ActivityHandling.startOfDay)
				.setSample(25)
				.createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "ci");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = getBindings(id, params);
		assert module != null;

		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.thetaFactor);


		//			episimConfig.getOrAddContainerParams("home_15").setContactIntensity(1.47 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_25").setContactIntensity(0.88 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_35").setContactIntensity(0.63 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_45").setContactIntensity(0.49 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_55").setContactIntensity(0.40 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_65").setContactIntensity(0.34 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		//			episimConfig.getOrAddContainerParams("home_75").setContactIntensity(0.29 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);


		episimConfig.getOrAddContainerParams("home_15").setContactIntensity(1.0 + 3 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_25").setContactIntensity(1.0 + 2 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_35").setContactIntensity(1.0 + 1 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_45").setContactIntensity(1.0).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_55").setContactIntensity(1.0 - 1 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_65").setContactIntensity(1.0 - 2 * params.ciModifier).setSpacesPerFacility(1);
		episimConfig.getOrAddContainerParams("home_75").setContactIntensity(1.0 - 3 * params.ciModifier).setSpacesPerFacility(1);


		episimConfig.getOrAddContainerParams("home").setContactIntensity(1.).setSpacesPerFacility(1);
		return config;
	}

	public static final class Params {

		@EnumParameter(EpisimConfigGroup.DistrictLevelRestrictions.class)
		EpisimConfigGroup.DistrictLevelRestrictions locationBasedRestrictions;

		@GenerateSeeds(5)
		public long seed;

		@Parameter({0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3})
		double thetaFactor;

		@Parameter({0.0, 0.1, 0.2, 0.3})
		Double ciModifier;


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

