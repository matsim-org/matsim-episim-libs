package org.matsim.run.batch;

import com.google.inject.AbstractModule;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.EpisimUtils;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.TracingConfigGroup.CapacityType;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;

import javax.annotation.Nullable;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;



/**
 * Interventions for symmetric Berlin week model with different contact models
 */
public class SMBatch implements BatchRun<SMBatch.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return new SnzBerlinProductionScenario();
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "calibration");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
		episimConfig.setCalibrationParameter(episimConfig.getCalibrationParameter() * params.theta);
		
		
		try {
			Map<LocalDate, Double> outdoorFractions = EpisimUtils.getOutdoorFractionsFromWeatherData("berlinWeather.csv", params.rainThreshold);
			episimConfig.setLeisureOutdoorFraction(outdoorFractions);
		} catch (IOException e) {
			e.printStackTrace();
		}

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);

		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), params.tracingCapacityApril,
				LocalDate.of(2020, 6, 15), params.tracingCapacityJune
		));

		return config;
	}

	public static final class Params {

		@GenerateSeeds(4)
		public long seed;

		@IntParameter({200, 300, 400})
		int tracingCapacityApril;

		@IntParameter({1000, 2000, 3000})
		int tracingCapacityJune;
		
		@Parameter({0.85, 0.9, 0.95, 1.})
		double theta;
		
		@Parameter({1., 2., 3., 4.})
		double rainThreshold;

	}


}

