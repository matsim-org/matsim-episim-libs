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

public class JRBatchRestrictionMitte implements BatchRun<JRBatchRestrictionMitte.Params> {

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				// parameters often changed:
				.setLocationBasedRestrictions(params != null ? params.locationBasedRestrictions : EpisimConfigGroup.DistrictLevelRestrictions.no)
				//				.setLocationBasedRestrictions(EpisimConfigGroup.DistrictLevelRestrictions.yesForHomeLocation)
				.setLocationBasedContactIntensity(SnzBerlinProductionScenario.LocationBasedContactIntensity.no)
				// not changed often:
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setActivityHandling(ActivityHandling.startOfDay)
				.setSample(1)
				.build();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "restrictMitte");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = getBindings(id, params);

		Config config = module.config();

		config.global().setRandomSeed(params.seed);


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		// here we can make adjustments to the policy
		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		// Change localRf for specific activty type and district
		String fromDateLocalRestriction = "2020-01-01";//"2020-10-01";
		String toDateLocalRestriction = "2022-01-01";//"2020-10-31";
		List<String> districtsToRestrict = Lists.newArrayList("Mitte");
		double newLocalRf = 0.000077777;
		switch (params.restrictSpecificDistricts) {
			case "work":
				// the locationBasedRf must first be cloned to avoid side effects; without clone, changing work will also change leisure, business, and visit activite
				builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> e.put("locationBasedRf", ((HashMap<String, Double>) e.get("locationBasedRf")).clone()), "work");
				for (String district : districtsToRestrict) {
					builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> ((HashMap<String, Double>) e.get("locationBasedRf")).put(district, newLocalRf), "work");
				}
				break;
			case "leisure":
				builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> e.put("locationBasedRf", ((HashMap<String, Double>) e.get("locationBasedRf")).clone()), "leisure");
				for (String district : districtsToRestrict) {
					builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> ((HashMap<String, Double>) e.get("locationBasedRf")).put(district, newLocalRf), "leisure");
				}

				break;
			case "work_and_leisure":
				builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> e.put("locationBasedRf", ((HashMap<String, Double>) e.get("locationBasedRf")).clone()), "work", "leisure");
				for (String district : districtsToRestrict) {
					builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> ((HashMap<String, Double>) e.get("locationBasedRf")).put(district, newLocalRf), "work", "leisure");
				}
				break;
			case "school":
				builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> e.put("locationBasedRf", ((HashMap<String, Double>) e.get("locationBasedRf")).clone()), "educ_primary", "educ_kiga","educ_secondary", "educ_tertiary", "educ_other");
				for (String district : districtsToRestrict) {
					builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> ((HashMap<String, Double>) e.get("locationBasedRf")).put(district, newLocalRf), "educ_primary", "educ_kiga","educ_secondary", "educ_tertiary", "educ_other");
				}
				break;
		}

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@EnumParameter(EpisimConfigGroup.DistrictLevelRestrictions.class)
		EpisimConfigGroup.DistrictLevelRestrictions locationBasedRestrictions;

		@GenerateSeeds(1)
		public long seed;

		@StringParameter({"no", "work", "leisure", "work_and_leisure", "school"})
		String restrictSpecificDistricts;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatchRestrictionMitte.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_ITERATIONS, Integer.toString(15),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}

