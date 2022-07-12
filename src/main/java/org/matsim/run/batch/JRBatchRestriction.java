package org.matsim.run.batch;

import com.google.common.collect.Lists;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.List;

import static org.matsim.episim.EpisimConfigGroup.ActivityHandling;

public class JRBatchRestriction implements BatchRun<JRBatchRestriction.Params> {

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				// parameters often changed:
				.setLocationBasedRestrictions(params != null ? params.locationBasedRestrictions : EpisimConfigGroup.DistrictLevelRestrictions.no)
				.setLocationBasedContactIntensity(SnzBerlinProductionScenario.LocationBasedContactIntensity.no)
				//.setLocationBasedRestrictions(EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeLocation)
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setActivityHandling(ActivityHandling.startOfDay)
				.setSample(25)
				.build();
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

		return config;
	}

	public static final class Params {

		@EnumParameter(EpisimConfigGroup.DistrictLevelRestrictions.class)
		EpisimConfigGroup.DistrictLevelRestrictions locationBasedRestrictions;

		@GenerateSeeds(5)
		public long seed;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatchRestriction.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_ITERATIONS, Integer.toString(360),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}

