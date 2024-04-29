package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import it.unimi.dsi.fastutil.ints.Int2DoubleAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.*;
import org.matsim.episim.analysis.HospitalNumbersFromEvents;
import org.matsim.episim.analysis.OutputAnalysis;
import org.matsim.episim.model.*;
import org.matsim.episim.model.listener.HouseholdSusceptibility;
import org.matsim.episim.model.vaccination.VaccinationModel;
import org.matsim.episim.model.vaccination.VaccinationStrategyBMBF202310XX;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.RunParallel;
import org.matsim.run.modules.SnzCologneProductionScenario;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;


/**
 * boilerplate batch for cologne
 */
public class StarterBatchCologne implements BatchRun<StarterBatchCologne.Params> {

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
	private SnzCologneProductionScenario getBindings(Params params) {
		return new SnzCologneProductionScenario.Builder()
			.setCarnivalModel(SnzCologneProductionScenario.CarnivalModel.yes)
			.setFutureVacations(SnzCologneProductionScenario.FutureVacations.yes)
			.setSebastianUpdate(true)
			.setLeisureCorrection(1.3) //params == null ? 0.0 : params.actCorrection)
			.setScaleForActivityLevels(1.3)
			.setSuscHouseholds_pct(0.35)
			.setActivityHandling(EpisimConfigGroup.ActivityHandling.startOfDay)
			.setInfectionModel(InfectionModelWithAntibodies.class)
			.build();
	}

	/*
	 * Metadata is needed for covid-sim.
	 */
	@Override
	public Metadata getMetadata() {
		return Metadata.of("cologne", "calibration");
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
				RunParallel.OPTION_SETUP, StarterBatchCologne.class.getName(),
				RunParallel.OPTION_PARAMS, Params.class.getName(),
				RunParallel.OPTION_TASKS, Integer.toString(8),
				RunParallel.OPTION_ITERATIONS, Integer.toString(10),
				RunParallel.OPTION_METADATA
		};

		RunParallel.main(args2);
	}

}

