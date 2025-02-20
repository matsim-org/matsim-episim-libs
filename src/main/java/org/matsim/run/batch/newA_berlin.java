package org.matsim.run.batch;

import com.google.inject.Module;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.analysis.OutputAnalysis;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBrandenburgProductionScenario;
import org.matsim.run.modules.SnzProductionScenario;

import javax.annotation.Nullable;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;


/**
 * boilerplate batch for berlin
 */
public class newA_berlin implements BatchRun<newA_berlin.Params> {

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
	private SnzBerlinProductionScenario getBindings(Params params) {
		return new SnzBerlinProductionScenario.Builder()
			.setBerlinBrandenburgInput(SnzBerlinProductionScenario.BerlinBrandenburgInput.berlin)
			.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
			.setEasterModel(SnzBerlinProductionScenario.EasterModel.no)
			.setChristmasModel(SnzBerlinProductionScenario.ChristmasModel.no)
			.setSample(25)
			.setOdeCoupling(params == null || params.ode != -1.0 ? SnzProductionScenario.OdeCoupling.yes : SnzProductionScenario.OdeCoupling.no)
			.build();
	}

	/*
	 * Metadata is needed for covid-sim.
	 */
	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
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

		episimConfig.setCalibrationParameter(1.0e-05 * 0.83 * params.thetaFactor);

		// ODE COUPLING

		if (params.ode != -1.0) {

			episimConfig.setInitialInfections(0);

			for (NavigableMap<LocalDate, Integer> map : episimConfig.getInfections_pers_per_day().values()) {
				map.clear();
			}

			episimConfig.setOdeIncidenceFile(SnzBerlinProductionScenario.INPUT.resolve("ode_br_infectious_250212.csv").toString());
			//		episimConfig.setOdeIncidenceFile(SnzBerlinProductionScenario.INPUT.resolve("ode_inputs/left_s.csv").toString());

			episimConfig.setOdeDistricts(SnzBrandenburgProductionScenario.BRANDENBURG_LANDKREISE);

			episimConfig.setOdeCouplingFactor(params.ode);

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

//		@Parameter({0.7, 0.8, 0.9, 1.0})
		@Parameter({1.0})
		public double thetaFactor;

		@Parameter({0.5, 0.75, 1.0, 1.25, 1.5})
//		@Parameter({1.})
		public double ode;

	}



	/*
	 * top-level parameters for a run on your local machine.
	 */
	public static void main(String[] args) {
		String[] args2 = {
				RunParallel.OPTION_SETUP, newA_berlin.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(1),
				RunParallel.OPTION_ITERATIONS, Integer.toString(50),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

}

