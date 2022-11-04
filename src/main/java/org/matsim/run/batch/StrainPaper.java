package org.matsim.run.batch;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.EpisimConfigGroup.SnapshotSeed;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.model.VirusStrain;
import org.matsim.episim.model.vaccination.NoVaccination;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.CologneStrainScenario;
import org.matsim.run.modules.SnzCologneProductionScenario;
import org.matsim.run.modules.SnzProductionScenario.Vaccinations;

import javax.annotation.Nullable;

import java.io.IOException;
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

		return new CologneStrainScenario( 1.95, 7, Vaccinations.no, NoVaccination.class, true, 1);
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
			episimConfig.setStartFromSnapshot("../snapshots/strain_base_" + params.seed + ".zip");
			episimConfig.setSnapshotSeed(SnapshotSeed.restore);
		}

		episimConfig.setCalibrationParameter(1.13e-05 * params.tf);

		ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
				

		episimConfig.setPolicy(builder.build());

		try {
			Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutDoorFractionFromDateAndTemp2(SnzCologneProductionScenario.INPUT.resolve("cologneWeather.csv").toFile(),
					SnzCologneProductionScenario.INPUT.resolve("weatherDataAvgCologne2000-2020.csv").toFile(), 0.5, 18.5, 25.0, params.temp, 18.5, 5., 1.0);
			episimConfig.setLeisureOutdoorFraction(outdoorFractions);
		} catch (IOException e) {
			e.printStackTrace();
		}


		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);
		
		double alpha = 0.0;
		
		if (params.calibr.equals("2022-01-31")) {
			alpha = 1.98867;
		}
		else if (params.calibr.equals("2022-02-07")) {
			alpha = 1.57724;
		}
		else if (params.calibr.equals("2022-02-14")) {
			alpha = 1.98978;
		}
		else if (params.calibr.equals("2022-02-21")) {
			alpha = 2.15176;
		}
		else if (params.calibr.equals("2022-02-28")) {
			alpha = 2.14461;
		}
		else {
			throw new RuntimeException();
		}

		virusStrainConfigGroup.getOrAddParams(VirusStrain.ALPHA).setInfectiousness(alpha);
		
		episimConfig.getOrAddContainerParams("pt", "tr").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("work").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("educ_kiga").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("educ_primary").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("educ_secondary").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("educ_tertiary").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("educ_higher").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("educ_other").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("shop_daily").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("shop_other").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("errands").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("business").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("visit").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("home").setSeasonality(params.nonLeis);
		episimConfig.getOrAddContainerParams("quarantine_home").setSeasonality(params.nonLeis);
		
		return config;
	}

	public static final class Params {

		@GenerateSeeds(12)
		public long seed;

//		@StringParameter({"50%open", "open", "activityLevel"})
//		@StringParameter({"activityLevel"})
//		public String schools;
		
		@StringParameter({"true"})
		public String snapshot;
		
//		@StringParameter({"2021-03-12"})
//		public String date;
		
		@Parameter({0.82})
		double tf;
		
		@StringParameter({"7-r1"})
		public String scen;
		
		@StringParameter({"2022-01-31", "2022-02-07", "2022-02-14", "2022-02-21", "2022-02-28"})
		public String calibr;
		
		@Parameter({16.5, 18.5, 20.5})
		double temp;
		
		@Parameter({0.0, 0.5, 1.0})
		double nonLeis;

//		@Parameter({0.4, 0.6, 0.8, 1.0})
//		double activityLevel;

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

