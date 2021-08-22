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

		if (params.ciMultiplier.equals("off")) {
			episimConfig.getOrAddContainerParams("home_15").setContactIntensity(1.0).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_25").setContactIntensity(1.0).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_35").setContactIntensity(1.0).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_45").setContactIntensity(1.0).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_55").setContactIntensity(1.0).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_65").setContactIntensity(1.0).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_75").setContactIntensity(1.0).setSpacesPerFacility(1);

//			InfectionParams home = episimConfig.getInfectionParam("home");
//			home.setContactIntensity(1.0);
//			episimConfig.addContainerParams(infectionParams);
			episimConfig.getOrAddContainerParams("home").setContactIntensity(1.0).setSpacesPerFacility(1);
		} else {


			episimConfig.getOrAddContainerParams("home_15").setContactIntensity(1.47 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_25").setContactIntensity(0.88 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_35").setContactIntensity(0.63 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_45").setContactIntensity(0.49 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_55").setContactIntensity(0.40 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_65").setContactIntensity(0.34 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
			episimConfig.getOrAddContainerParams("home_75").setContactIntensity(0.29 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);

			// weighted average of contact intensities (39 m2 per person)
//			episimConfig.g
			episimConfig.getOrAddContainerParams("home").setContactIntensity(0.56 * Double.parseDouble(params.ciMultiplier)).setSpacesPerFacility(1);
		}


		return config;
	}

	public static final class Params {

		@EnumParameter(EpisimConfigGroup.DistrictLevelRestrictions.class)
		EpisimConfigGroup.DistrictLevelRestrictions locationBasedRestrictions;

		@GenerateSeeds(1)
		public long seed;

		@Parameter({0.6, 0.7, 0.8, 0.9, 1.0, 1.10})
		double thetaFactor;

		@StringParameter({"1.0", "1.25", "1.5", "1.75", "2.0", "2.5", "3.0", "off"})
		String ciMultiplier;

		//		@EnumParameter(SnzBerlinProductionScenario.LocationBasedContactIntensity.class)
		//		SnzBerlinProductionScenario.LocationBasedContactIntensity locationBasedContactIntensity;


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

