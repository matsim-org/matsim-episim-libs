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
		
		double thetaFactor = Double.parseDouble(params.weatherMidPoint_Theta.split("_")[1]);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * thetaFactor);
//		episimConfig.setStartDate("2020-02-25");
//		episimConfig.setSnapshotSeed(EpisimConfigGroup.SnapshotSeed.reseed);

		Map<LocalDate, Integer> importMap = new HashMap<>();
		int importOffset = 0;
		importMap.put(episimConfig.getStartDate(), Math.max(1, (int) Math.round(0.9 * 1)));
		importMap = SnzBerlinProductionScenario.interpolateImport(importMap, 1., LocalDate.parse("2020-02-24").plusDays(importOffset),
				LocalDate.parse("2020-03-09").plusDays(importOffset), 0.9, 23.1);
		importMap = SnzBerlinProductionScenario.interpolateImport(importMap, 1., LocalDate.parse("2020-03-09").plusDays(importOffset),
				LocalDate.parse("2020-03-23").plusDays(importOffset), 23.1, 3.9);
		importMap = SnzBerlinProductionScenario.interpolateImport(importMap, 1., LocalDate.parse("2020-03-23").plusDays(importOffset),
				LocalDate.parse("2020-04-13").plusDays(importOffset), 3.9, 0.1);
		
		double importFactor = params.importFactorAfterJune;
		if (importFactor == 0.) {
			importMap.put(LocalDate.parse("2020-06-08"), 1);
		}
		else {
			importMap = SnzBerlinProductionScenario.interpolateImport(importMap, importFactor, LocalDate.parse("2020-06-08").plusDays(importOffset),
					LocalDate.parse("2020-07-13").plusDays(importOffset), 0.1, 2.7);
			importMap = SnzBerlinProductionScenario.interpolateImport(importMap, importFactor, LocalDate.parse("2020-07-13").plusDays(importOffset),
					LocalDate.parse("2020-08-10").plusDays(importOffset), 2.7, 17.9);
			importMap = SnzBerlinProductionScenario.interpolateImport(importMap, importFactor, LocalDate.parse("2020-08-10").plusDays(importOffset),
					LocalDate.parse("2020-09-07").plusDays(importOffset), 17.9, 5.4);
		}
		episimConfig.setInfections_pers_per_day(importMap);	

		double tempMidPoint = Double.parseDouble(params.weatherMidPoint_Theta.split("_")[0]);

		try {
			Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractionsFromWeatherData("berlinWeather.csv", params.rainThreshold, tempMidPoint-10., tempMidPoint+10. );
			episimConfig.setLeisureOutdoorFraction(outdoorFractions);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
//		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
//
//		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
//				LocalDate.of(2020, 4, 1), 300,
//				LocalDate.of(2020, 6, 15), params.tracingCapacityJune
//		));


		return config;
	}

	public static final class Params {

		@GenerateSeeds(3)
		public long seed;
		
//		@Parameter({0.75, 0.8, 0.85, 0.9, 0.95, 1., 1.05, 1.1})
//		double theta;
		
		@Parameter({0.5, 1., 2.})
		double rainThreshold;
		
//		@Parameter({5., 10., 15., 20.})
//		double temperature0;
//		
//		@Parameter({20., 25., 30.})
//		double temperature1;
		
		@Parameter({1., 0.25, 0.5, 0.75, 0.})
		double importFactorAfterJune;
		
		@StringParameter({
			"15.0_0.85", "15.0_0.9", "15.0_0.95",
			"17.5_0.75", "17.5_0.8", "17.5_0.85",
			"20.0_0.7", "20.0_0.75", "20.0_0.8",
			"22.5_0.65", "22.5_0.7", "22.5_0.75",
			"25.0_0.6", "25.0_0.65", "25.0_0.7"})
		public String weatherMidPoint_Theta;
		
//		@IntParameter({1000, 2000, 3000})
//		int tracingCapacityJune;

	}


}

