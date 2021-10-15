package org.matsim.run.batch;

import com.google.common.collect.Lists;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;

import static org.matsim.episim.EpisimConfigGroup.ActivityHandling;

public class JRBatchMasterA implements BatchRun<JRBatchMasterA.Params> {

	boolean DEBUG = true;

	@Override
	public SnzBerlinProductionScenario getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder()
				.setSample(DEBUG ? 1 : 25)
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setActivityHandling(ActivityHandling.startOfDay)
				.createSnzBerlinProductionScenario();

//		.setDiseaseImport( DiseaseImport.yes ).setRestrictions( Restrictions.yes ).setMasks( Masks.yes ).setTracing( Tracing.yes ).setInfectionModel( AgeDependentInfectionModelWithSeasonality.class ).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "masterA");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		if (DEBUG) {
			if (params.seed != 4711
					|| params.locationBasedRestrictions != EpisimConfigGroup.DistrictLevelRestrictions.yesForActivityLocation) {
				return null;
			}
		}

		SnzBerlinProductionScenario module = getBindings(id, params);

		assert module != null;
		Config config = module.config();

		config.global().setRandomSeed(params.seed);


		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setDistrictLevelRestrictions(params.locationBasedRestrictions);
		episimConfig.setDistrictLevelRestrictionsAttribute("subdistrict");

		// here we can make adjustments to the policy
		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());

		// Change localRf for specific activtiy type and district
		String fromDateLocalRestriction = DEBUG ? "2020-04-01" : "2020-10-01";
		String toDateLocalRestriction = DEBUG ? "2020-05-01" : "2020-10-31";
		double newLocalRf = 0.000077777; //TODO change to zero
		// the locationBasedRf must first be cloned to avoid side effects; without clone, changing work will also change leisure, business, and visit activite
		builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> e.put("locationBasedRf", ((HashMap<String, Double>) e.get("locationBasedRf")).clone()), "leisure");
		builder.apply(fromDateLocalRestriction, toDateLocalRestriction, (d, e) -> ((HashMap<String, Double>) e.get("locationBasedRf")).put("Mitte", newLocalRf), "leisure"); //AbstractSnzScenario2020.DEFAULT_ACTIVITIES

		episimConfig.setPolicy(builder.build()); //FixedPolicy.class,

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
				RunParallel.OPTION_SETUP, JRBatchMasterA.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_ITERATIONS, Integer.toString(360),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}

