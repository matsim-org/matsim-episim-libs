package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.BatchRun.Parameter;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;


/**
 * Strain paper
 */
public class StrainPaper implements BatchRun<StrainPaper.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "strainPaper");
	}
	
//	@Override
//	public int getOffset() {
//		return 1500;
//	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder()
				.setSnapshot(SnzBerlinProductionScenario.Snapshot.no)
				.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.no)
				.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
				.setVaccinations(SnzBerlinProductionScenario.Vaccinations.no)
				.createSnzBerlinProductionScenario();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		episimConfig.setStartFromSnapshot("../episim-snapshot-270-2020-11-20.zip");

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		builder.clearAfter("2020-12-14");
		
		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
//			if (act.contains("educ_higher")) continue;
			builder.restrict("2020-12-15", params.activityLevel, act);
		}
					
		//schools
		if (params.schools.equals("50%open")) {
			builder.clearAfter( "2020-12-14", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict("2020-12-15", .5, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
		}
		
		if (params.schools.equals("open")) {
			builder.clearAfter( "2020-12-14", "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
			builder.restrict("2020-12-15", 1.0, "educ_primary", "educ_secondary", "educ_tertiary", "educ_other", "educ_kiga");
		}		

		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		
		episimConfig.setLeisureOutdoorFraction(0.);
		
		Map<LocalDate, Integer> infPerDayVariant = new HashMap<>();
		infPerDayVariant.put(LocalDate.parse("2020-01-01"), 0);
		infPerDayVariant.put(LocalDate.parse(params.b117date), 1);
		episimConfig.setInfections_pers_per_day(VirusStrain.B117, infPerDayVariant);
		
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setInfectiousness(params.b117inf);
		virusStrainConfigGroup.getOrAddParams(VirusStrain.B117).setFactorSeriouslySick(1.5);

		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		public long seed;
		
//		@StringParameter({"50%open", "open", "activityLevel"})
		@StringParameter({"activityLevel"})
		public String schools;
		
		@StringParameter({"2020-12-15"})
		String b117date;
		
		@Parameter({1.2, 1.5, 1.8, 2.1, 2.4})
		double b117inf;
		
		@Parameter({0.47, 0.57, 0.67, 0.77, 0.87})
		double activityLevel;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, StrainPaper.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_THREADS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}

