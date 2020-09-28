package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;


import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.InfectionModel;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.run.modules.SnzBerlinWeekScenario2020Symmetric;

import java.time.LocalDate;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * batch class used for calibration of scenario with disease import from data
 */
public class BerlinDiseaseImport implements BatchRun<BerlinDiseaseImport.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return (AbstractModule) Modules.override(new SnzBerlinWeekScenario2020Symmetric(25))
				.with(new AbstractModule() {
					@Override
					protected void configure() {
						bind(ContactModel.class).to(OldSymmetricContactModel.class).in(Singleton.class);
						bind(ProgressionModel.class).to(AgeDependentProgressionModel.class).in(Singleton.class);
						bind(InfectionModel.class).to(AgeDependentInfectionModelWithSeasonality.class).in(Singleton.class);
					}
//					@Provides
//					@Singleton
//					public Scenario scenario(Config config) {
//
//						// guice will use no args constructor by default, we check if this config was initialized
//						// this is only the case when no explicit binding are required
//						if (config.getModules().size() == 0)
//							throw new IllegalArgumentException("Please provide a config module or binding.");
//
//						config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);
//
//						// save some time for not needed inputs
//						config.facilities().setInputFile(null);
//						
//						final Scenario scenario = ScenarioUtils.loadScenario( config );
//
//						return scenario;
//					}
				});
	}

	@Override
	public Metadata getMetadata() {
		return Metadata.of("berlin", "diseaseImport");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020Symmetric module = new SnzBerlinWeekScenario2020Symmetric();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);
		
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

		episimConfig.setInitialInfectionDistrict(null);
		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		
		if (params.ageBoundaries.startsWith("0")) {
			episimConfig.setUpperAgeBoundaryForInitInfections(9);
		}
		if (params.ageBoundaries.startsWith("1")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(10);
			episimConfig.setUpperAgeBoundaryForInitInfections(19);
		}
		if (params.ageBoundaries.startsWith("2")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(20);
			episimConfig.setUpperAgeBoundaryForInitInfections(29);
		}
		if (params.ageBoundaries.startsWith("3")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(30);
			episimConfig.setUpperAgeBoundaryForInitInfections(39);
		}
		if (params.ageBoundaries.startsWith("4")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(40);
			episimConfig.setUpperAgeBoundaryForInitInfections(49);
		}
		if (params.ageBoundaries.startsWith("5")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(50);
			episimConfig.setUpperAgeBoundaryForInitInfections(59);
		}
		if (params.ageBoundaries.startsWith("6")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(60);
			episimConfig.setUpperAgeBoundaryForInitInfections(69);
		}
		if (params.ageBoundaries.startsWith("7")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(70);
			episimConfig.setUpperAgeBoundaryForInitInfections(79);
		}
		if (params.ageBoundaries.startsWith("8")) {
			episimConfig.setLowerAgeBoundaryForInitInfections(80);
		}
		//6.e-6 is current default for runs without age boundaries
		episimConfig.setCalibrationParameter(params.calibrationParam);
//		episimConfig.setCalibrationParameter(6.e-6);

		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 6, 15), params.tracingCapacity
		));

		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		long seed;
		
		@Parameter({6.e-6, 4.e-6, 2.e-6})
		private double calibrationParam;
		
		@StringParameter({"0-9", "10-19", "20-29", "30-39", "40-49", "50-59", "60-69", "70-79", "80+", "random"})
		public String ageBoundaries;
		
		@IntParameter({100, Integer.MAX_VALUE})
		private int tracingCapacity;

	}

}
