package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.VaccinationConfigGroup;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenarioJR;

import javax.annotation.Nullable;

public class JRBatch implements BatchRun<JRBatch.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenarioJR.Builder().setSnapshot(SnzBerlinProductionScenarioJR.Snapshot.no).createSnzBerlinProductionScenarioJR();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "locationBasedRestrictions");
	}

	//	@Override
	//	public int getOffset() {
	//		return 400;
	//	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenarioJR module = new SnzBerlinProductionScenarioJR.Builder().setSnapshot(
				SnzBerlinProductionScenarioJR.Snapshot.no).setSample(1).createSnzBerlinProductionScenarioJR();
		Config config = module.config();
		//		config.global().setRandomSeed(params.seed);

		config.global().setRandomSeed(7564655870752979346L);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
//		episimConfig.setStartFromSnapshot("");
		episimConfig.setDistrictLevelRestrictions(params.districtLevelRestrictions);
//		episimConfig.setSampleSize(0.01);
//		double sampleSize = episimConfig.getSampleSize();

		//		episimConfig.setSnapshotInterval();

//		VaccinationConfigGroup vaccinationConfigGroup = ConfigUtils.addOrGetModule(config, VaccinationConfigGroup.class);
//		vaccinationConfigGroup.setEffectiveness(params.dailyInitialVaccinations);


		return config;
	}

	public static final class Params {

		//		@GenerateSeeds(2)
		//		public long seed;

//		@IntParameter({3000, 10000})
//		int dailyInitialVaccinations;
//
//		@StringParameter({"restrictive"})
//		public String christmasModel;
//
//		@StringParameter({"closed", "open", "open&masks", "50%&masks", "50%open"})
//		public String schools;
//
//		@StringParameter({"no", "ffp"})
//		public String work;
//
//		@StringParameter({"no", "20-5", "22-5"})
//		public String curfew;
//
//		@StringParameter({"2020-12-15", "2020-11-15", "2020-10-15"})
//		String newVariantDate;
//
//		@StringParameter({"no", "yes", "yesUntil80", "no100%"})
//		String extrapolateRestrictions;
//
//		@Parameter({1.35})
//		double newVariantInfectiousness;

		@StringParameter({"yes", "no"})
		String districtLevelRestrictions;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, JRBatch.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(330),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}

