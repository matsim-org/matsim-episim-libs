package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.*;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
import org.matsim.run.modules.SnzBerlinProductionScenario;
import org.matsim.run.modules.SnzBerlinProductionScenario.ChristmasModel;
import org.matsim.run.modules.SnzBerlinProductionScenario.DiseaseImport;
import org.matsim.run.modules.SnzBerlinProductionScenario.Masks;
import org.matsim.run.modules.SnzBerlinProductionScenario.Restrictions;
import org.matsim.run.modules.SnzBerlinProductionScenario.Snapshot;
import org.matsim.run.modules.SnzBerlinProductionScenario.Tracing;
import org.matsim.run.modules.SnzBerlinProductionScenario.WeatherModel;

import javax.annotation.Nullable;


/**
 * Runs for symmetric Berlin week model
 */
public class BerlinSensitivityRuns implements BatchRun<BerlinSensitivityRuns.Params> {

	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		
		if (params == null) return new SnzBerlinProductionScenario.Builder().createSnzBerlinProductionScenario();	
			
		return getModule(params);
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "basePaper");
	}

	@Override
	public Config prepareConfig(int id, Params params) {
		
		SnzBerlinProductionScenario module = getModule(params);
		Config config = module.config();
		config.global().setRandomSeed(params.seed);
		
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);
				
		episimConfig.setCalibrationParameter(1.7E-5 * params.thetaFactor);		

		return config;
	}

	private SnzBerlinProductionScenario getModule(Params params) {
		DiseaseImport diseaseImport = DiseaseImport.no;
		Restrictions restrictions = Restrictions.no;
		Masks masks = Masks.no;
		Tracing tracing = Tracing.no;
		ChristmasModel christmasModel = ChristmasModel.no;
		WeatherModel weatherModel = WeatherModel.no;
		
		if (params.run.contains("2_withSpringImport")) {
			diseaseImport = DiseaseImport.onlySpring;
		}
		if (params.run.contains("3_withRestrictions")) {
			diseaseImport = DiseaseImport.onlySpring;
			restrictions = Restrictions.yes;
		}
		if (params.run.contains("4_withWeatherModel_175_175")) {
			diseaseImport = DiseaseImport.onlySpring;
			restrictions = Restrictions.yes;
			weatherModel = WeatherModel.midpoints_175_175;
		}
		if (params.run.contains("5_withWeatherModel_175_250")) {
			diseaseImport = DiseaseImport.onlySpring;
			restrictions = Restrictions.yes;
			weatherModel = WeatherModel.midpoints_175_250;
		}
		if (params.run.contains("6_withSummerImport")) {
			diseaseImport = DiseaseImport.yes;
			restrictions = Restrictions.yes;
			weatherModel = WeatherModel.midpoints_175_250;
		}
		if (params.withMasksAndTracing.contains("yes")) {
			masks = Masks.yes;
			tracing = Tracing.yes;
		}
		
		SnzBerlinProductionScenario module = new SnzBerlinProductionScenario.Builder().setDiseaseImport( diseaseImport ).setRestrictions( restrictions ).setMasks( masks ).setTracing(
				tracing ).setChristmasModel(christmasModel).setWeatherModel(weatherModel).createSnzBerlinProductionScenario();
		return module;
	}

	public static final class Params {

		@GenerateSeeds(10)
		public long seed;
		
//		@Parameter({1.8E-5, 1.7E-5, 1.6E-5, 1.5E-5, 1.4E-5, 1.3E-5, 1.27E-5, 1.1E-5, 1.E-5})
//		double theta;
		
		@StringParameter({"1_base", "2_withSpringImport", "3_withRestrictions", "4_withWeatherModel_175_175", "5_withWeatherModel_175_250", "6_withSummerImport"})
		public String run;
		
		@StringParameter({"no", "yes"})
		public String withMasksAndTracing;
		
		@Parameter({0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2})
		double thetaFactor;

	}


}
