package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimConfigGroup.SnapshotSeed;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.vaccination.NoVaccination;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.CologneStrainScenario;
import org.matsim.run.modules.SnzProductionScenario.Vaccinations;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Strain paper
 */
public class StrainPaper implements BatchRun<StrainPaper.Params> {

	@Override
	public CologneStrainScenario getBindings(int id, @Nullable Params params) {
		
		return new CologneStrainScenario( 1.95, Vaccinations.no, NoVaccination.class, false);
	}

	@Override
	public BatchRun.Metadata getMetadata() {
		return BatchRun.Metadata.of("cologne", "strain");
	}

//	@Override
//	public int getOffset() {
//		return 1500;
//	}

	@Nullable
	@Override	
	public Config prepareConfig(int id, Params params) {

		CologneStrainScenario scenario = getBindings(id, params);

		Config config = scenario.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		
		if (Boolean.valueOf(params.snapshot)) {
			episimConfig.setStartFromSnapshot("/scratch/projects/bzz0020/runs/christian/strains/weekly/snapshots/strain_base_" + params.seed + ".zip");
			episimConfig.setSnapshotSeed(SnapshotSeed.restore);
		}

		episimConfig.setCalibrationParameter(1.13e-05 * 0.92);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
				
		builder.clearAfter(params.date);

		for (String act : AbstractSnzScenario2020.DEFAULT_ACTIVITIES) {
//			if (act.contains("educ_higher")) continue;
			builder.restrict(params.date, params.activityLevel / 1.3, act);
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

		episimConfig.setPolicy(builder.build());
		
		{
			Map<LocalDate, Double> outdoorFractionOld = episimConfig.getLeisureOutdoorFraction();
			Map<LocalDate, Double> outdoorFractionNew = new HashMap<LocalDate, Double>();
			
			for (Entry<LocalDate, Double> entry : outdoorFractionOld.entrySet()) {
				if (entry.getKey().isBefore(LocalDate.parse("2021-01-01")))
						outdoorFractionNew.put(entry.getKey(), entry.getValue());
			}
			
			episimConfig.setLeisureOutdoorFraction(outdoorFractionNew);

		}


		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(params.alphaInf);

		return config;
	}

	public static final class Params {

		@GenerateSeeds(12)
		public long seed;

//		@StringParameter({"50%open", "open", "activityLevel"})
		@StringParameter({"activityLevel"})
		public String schools;
		
		@StringParameter({"false"})
		public String snapshot;
		
		@StringParameter({"2021-03-12"})
		public String date;

		@Parameter({1.2, 1.5, 1.8, 2.1, 2.4})
		double alphaInf;

		@Parameter({0.4, 0.6, 0.8, 1.0})
		double activityLevel;

	}

	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, StrainPaper.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(500),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}


}

