package org.matsim.run.batch;

import com.google.inject.Module;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.VirusStrainConfigGroup;
import org.matsim.episim.analysis.OutputAnalysis;
import org.matsim.episim.model.InfectionModelWithAntibodies;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBrandenburgProductionScenario;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;


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
			.setSample(1)
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
		return List.of();
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

		return config;
	}


	/*
	 * Specify parameter combinations that will be run.
	 */
	public static final class Params {
		// general
		@GenerateSeeds(1)
		public long seed;

		@Parameter({1.0, 2.0})
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
				RunParallel.OPTION_ITERATIONS, Integer.toString(50),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

}

