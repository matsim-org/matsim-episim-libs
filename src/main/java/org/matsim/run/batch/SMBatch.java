package org.matsim.run.batch;

import com.google.inject.AbstractModule;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;



/**
 * Interventions for symmetric Berlin week model with different contact models
 */
public class SMBatch implements BatchRun<SMBatch.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario.Builder().setSnapshot( SnzBerlinProductionScenario.Snapshot.no ).createSnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder().setSnapshot(
				SnzBerlinProductionScenario.Snapshot.no ).createSnzBerlinProductionScenario();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.theta);
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);
		
//		if (params.childInfSusReduced.equals("no")) {
//			episimConfig.setChildInfectivity(1.);
//			episimConfig.setChildSusceptibility(1.);
//		}
		

		try {
			Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractionsFromWeatherData("berlinWeather.csv", params.rainThreshold, params.temperature0, params.temperature1 );
			episimConfig.setLeisureOutdoorFraction(outdoorFractions);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
//		else {
//			Map<LocalDate, Double> leisureOutdoorFraction = new TreeMap<>(Map.of(
//					LocalDate.parse("2020-01-15"), 0.1,
//					LocalDate.parse("2020-04-15"), 0.8,
//					LocalDate.parse(params.summerEnd), 0.8,
//					LocalDate.parse("2020-11-15"), 0.1,
//					LocalDate.parse("2021-02-15"), 0.1,
//					LocalDate.parse("2021-04-15"), 0.8,
//					LocalDate.parse("2021-09-15"), 0.8)
//					);
//			episimConfig.setLeisureOutdoorFraction(leisureOutdoorFraction);
//		}
		
		
//		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
//
//		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
//				LocalDate.of(2020, 4, 1), 300,
//				LocalDate.of(2020, 6, 15), params.tracingCapacityJune
//		));


		return config;
	}

	public static final class Params {

		@GenerateSeeds(2)
		public long seed;
		
		@Parameter({0.75, 0.8, 0.85, 0.9, 0.95, 1., 1.05, 1.1})
		double theta;
		
		@Parameter({1., 2., 3.})
		double rainThreshold;
		
		@Parameter({5., 10., 15., 20.})
		double temperature0;
		
		@Parameter({20., 25., 30.})
		double temperature1;
		
//		@IntParameter({1000, 2000, 3000})
//		int tracingCapacityJune;
		
//		@StringParameter({"2020-09-15", "2020-09-01", "fromWeather1", "fromWeather2", "fromWeather3"})
//		public String summerEnd;
		
//		@StringParameter({"yes", "no"})
//		public String childInfSusReduced;
		
//		@Parameter({1., 0.67, 0.33, 0.})
//		double importFactorAfterJune;

	}


}

