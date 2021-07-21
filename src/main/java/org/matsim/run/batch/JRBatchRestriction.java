package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;

import static org.matsim.episim.EpisimConfigGroup.ActivityHandling;
import static org.matsim.episim.EpisimConfigGroup.DistrictLevelRestrictions;

public class JRBatchRestriction implements BatchRun<JRBatchRestriction.Params> {

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setActivityHandling(params != null ? params.activityHandling : ActivityHandling.startOfDay)
				.setRestrictBerlinMitteOctober2020(params != null ? params.restrictBerlinMitteOctober2020 : SnzBerlinProductionScenario.RestrictBerlinMitteOctober2020.no)
				.setLocationBasedRestrictions(params != null ? params.locationBasedRestrictions : SnzBerlinProductionScenario.LocationBasedRestrictions.no)
				.setSample(25)
				.setLocationBasedContactIntensity(SnzBerlinProductionScenario.LocationBasedContactIntensity.no)
				.createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "locationBasedRestrictions");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = getBindings(id, params);

		Config config = module.config();

		config.global().setRandomSeed(params.seed);

		//		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		//		episimConfig.setActivityHandling(params.activityHandling);

		return config;
	}

	public static final class Params {

		@EnumParameter(SnzBerlinProductionScenario.LocationBasedRestrictions.class)
		SnzBerlinProductionScenario.LocationBasedRestrictions locationBasedRestrictions;

		@GenerateSeeds(5)
		public long seed;

		@EnumParameter(SnzBerlinProductionScenario.RestrictBerlinMitteOctober2020.class)
		SnzBerlinProductionScenario.RestrictBerlinMitteOctober2020 restrictBerlinMitteOctober2020;

		@EnumParameter(ActivityHandling.class)
		ActivityHandling activityHandling;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatchRestriction.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_ITERATIONS, Integer.toString(450),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}

