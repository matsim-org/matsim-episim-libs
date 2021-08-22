package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;

import static org.matsim.episim.EpisimConfigGroup.ActivityHandling;

public class JRBatchAutomaticallyRestrict implements BatchRun<JRBatchAutomaticallyRestrict.Params> {

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				// parameters often changed:
				.setLocationBasedRestrictions(EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeLocation)
				//.setLocationBasedRestrictions(EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeLocation)
				.setSample(25)

				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setActivityHandling(ActivityHandling.startOfDay)
				.setLocationBasedContactIntensity(SnzBerlinProductionScenario.LocationBasedContactIntensity.no)
				.createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "auto");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = getBindings(id, params);

		Config config = module.config();
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setAutomaticallyRestrictDistricts(params.automaticallyRestrictDistricts);

		return config;
	}

	public static final class Params {

//		@EnumParameter(EpisimConfigGroup.DistrictLevelRestrictions.class)
//		EpisimConfigGroup.DistrictLevelRestrictions locationBasedRestrictions;

		@EnumParameter(EpisimConfigGroup.AutomaticallyRestrictDistricts.class)
		EpisimConfigGroup.AutomaticallyRestrictDistricts automaticallyRestrictDistricts;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatchAutomaticallyRestrict.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_ITERATIONS, Integer.toString(360),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}

