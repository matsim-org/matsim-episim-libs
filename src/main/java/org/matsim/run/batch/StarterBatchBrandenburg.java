package org.matsim.run.batch;

import com.google.inject.Module;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.analysis.InfectionHomeLocation;
import org.matsim.episim.analysis.OutputAnalysis;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBrandenburgProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;


/**
 * boilerplate batch for brandenburg
 */
public class StarterBatchBrandenburg implements BatchRun<StarterBatchBrandenburg.Params> {

	/*
	 * here you can swap out vaccination model, antibody model, etc.
	 * See CologneBMBF202310XX_soup.java for an example
	 */
	@Nullable
	@Override
	public Module getBindings(int id, @Nullable Params params) {
		return getBindings(params);
	}


	/*
	 * here you select & modify models specified in the SnzCologneProductionScenario & SnzProductionScenario.
	 */
	private SnzBrandenburgProductionScenario getBindings(Params params) {
		return new SnzBrandenburgProductionScenario.Builder()
			.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
			.setInfectionModel(InfectionModelWithAntibodies.class)
//			.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
//			.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.no)
			.setSample(25)
			.build();
	}

	/*
	 * Metadata is needed for covid-sim.
	 */
	@Override
	public Metadata getMetadata() {
		return Metadata.of("brandenburg", "calibration");
	}


	/*
	 * Here you can add post-processing classes, that will be executed after the simulation.
	 */
	@Override
	public Collection<OutputAnalysis> postProcessing() {
		return List.of(new InfectionHomeLocation().withArgs("--output","./output/","--input","/scratch/projects/bzz0020/episim-input",
			"--population-file", "br_2020-week_snz_entirePopulation_emptyPlans_withDistricts_25pt_split.xml.gz"));
	}

	/*
	 * Here you can specify configuration options
	 */
	@Override
	public Config prepareConfig(int id, Params params) {

		// Level 1: General (matsim) config. Here you can specify number of iterations and the seed.
		Config config = getBindings(params).config();

		config.global().setRandomSeed(params.seed);

		// Level 2: Episim specific configs:
		// 		 2a: general episim config
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * 1.2 * 1.7 * params.thetaFactor);

		//		 2b: specific config groups, e.g. virusStrainConfigGroup
		VirusStrainConfigGroup virusStrainConfigGroup = ConfigUtils.addOrGetModule(config, VirusStrainConfigGroup.class);

		if (params.rfCorr != 1.0) {
			BiFunction<LocalDate, Double, Double> newRf = (d, rf) -> rf * params.rfCorr;

			FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
			builder.applyToRf("2020-02-01", "2025-01-01", newRf, "business", "errands", "leisure", "leisPublic", "leisPrivate", "shop_daily", "shop_other", "visit", "work");
			episimConfig.setPolicy(builder.build());

		}

		if(params.ci.equals("red")){
			//work
			double workCiMod = 0.75;
			episimConfig.getOrAddContainerParams("work").setContactIntensity(episimConfig.getOrAddContainerParams("work").getContactIntensity() * workCiMod);
			episimConfig.getOrAddContainerParams("business").setContactIntensity(episimConfig.getOrAddContainerParams("business").getContactIntensity() * workCiMod);

			//leisure & visit
			double leisureCiMod = 0.4;
			episimConfig.getOrAddContainerParams("leisure").setContactIntensity(episimConfig.getOrAddContainerParams("leisure").getContactIntensity() * leisureCiMod);
			episimConfig.getOrAddContainerParams("leisPublic").setContactIntensity(episimConfig.getOrAddContainerParams("leisPublic").getContactIntensity() * leisureCiMod);
			episimConfig.getOrAddContainerParams("leisPrivate").setContactIntensity(episimConfig.getOrAddContainerParams("leisPrivate").getContactIntensity() * leisureCiMod);
			episimConfig.getOrAddContainerParams("visit").setContactIntensity(episimConfig.getOrAddContainerParams("visit").getContactIntensity() * leisureCiMod);


			//school
			double schoolCiMod = 0.75;
			episimConfig.getOrAddContainerParams("educ_kiga").setContactIntensity(episimConfig.getOrAddContainerParams("educ_kiga").getContactIntensity() * schoolCiMod);
			episimConfig.getOrAddContainerParams("educ_primary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_primary").getContactIntensity() * schoolCiMod);
			episimConfig.getOrAddContainerParams("educ_secondary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_secondary").getContactIntensity() * schoolCiMod);
			episimConfig.getOrAddContainerParams("educ_tertiary").setContactIntensity(episimConfig.getOrAddContainerParams("educ_tertiary").getContactIntensity() * schoolCiMod);
			episimConfig.getOrAddContainerParams("educ_higher").setContactIntensity(episimConfig.getOrAddContainerParams("educ_higher").getContactIntensity() * schoolCiMod);
			episimConfig.getOrAddContainerParams("educ_other").setContactIntensity(episimConfig.getOrAddContainerParams("educ_other").getContactIntensity() * schoolCiMod);

		} else if (params.ci.equals("base")) {

		} else {
			throw new RuntimeException();
		}

		return config;
	}


	/*
	 * Specify parameter combinations that will be run.
	 */
	public static final class Params {
		// general
		@GenerateSeeds(5)
		public long seed;

		@StringParameter({"base", "red"})
		public String ci;

		@Parameter({0.5, 0.75, 0.1})
		public double rfCorr;

		@Parameter({0.8, 0.9, 1.0, 1.1, 1.2})
		public double thetaFactor;
	}



	/*
	 * top-level parameters for a run on your local machine.
	 */
	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, StarterBatchBrandenburg.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(8),
				RunParallel.OPTION_ITERATIONS, Integer.toString(20),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

}

