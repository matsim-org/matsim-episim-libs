package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;

import javafx.util.Pair;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.BatchRun.StringParameter;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.InfectionModel;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.SymmetricContactModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinWeekScenario2020Symmetric;

import java.time.LocalDate;
import java.util.HashMap;
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

		Map<LocalDate, Integer> importMap = new HashMap<>();
		double importFactor = 1.;
		
		importMap.put(episimConfig.getStartDate(), (int) Math.round(0.9 * importFactor));
		
		int importOffset = 0;
		importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-02-24").plusDays(importOffset), 
				LocalDate.parse("2020-03-09").plusDays(importOffset), 0.9, 23.1);
		importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-03-09").plusDays(importOffset), 
				LocalDate.parse("2020-03-23").plusDays(importOffset), 23.1, 3.9);
		importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-03-23").plusDays(importOffset), 
				LocalDate.parse("2020-04-13").plusDays(importOffset), 3.9, 0.1);
		importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-06-08").plusDays(importOffset), 
				LocalDate.parse("2020-07-13").plusDays(importOffset), 0.1, 2.7);
		importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-07-13").plusDays(importOffset), 
				LocalDate.parse("2020-08-10").plusDays(importOffset), 2.7, 17.9);
		importMap = interpolateImport(importMap, importFactor, LocalDate.parse("2020-08-10").plusDays(importOffset), 
				LocalDate.parse("2020-08-24").plusDays(importOffset), 17.9, 8.6);
		
		episimConfig.setInfections_pers_per_day(importMap);
		
		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		double ciCorrection = 1.;
		builder.restrict("2020-03-07", Restriction.ofCiCorrection(ciCorrection), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
		builder.restrict("2020-03-07", Restriction.ofCiCorrection(ciCorrection), "quarantine_home");
		builder.restrict("2020-03-07", Restriction.ofCiCorrection(ciCorrection), "pt");
		builder.restrict("2020-08-08", Restriction.ofCiCorrection(ciCorrection * 0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		
		episimConfig.setPolicy(FixedPolicy.class, builder.build());
		
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
		
//		@Parameter({1.})
//		private double importFactor;
		
		@Parameter({6.e-6, 4.e-6, 2.e-6})
		private double calibrationParam;
		
		@StringParameter({"0-9", "10-19", "20-29", "30-39", "40-49", "50-59", "60-69", "70-79", "80+", "random"})
		public String ageBoundaries;
		
		@IntParameter({100, Integer.MAX_VALUE})
		private int tracingCapacity;
		
//		@Parameter({1.0, 0.9, 0.8, 0.7})
//		private double ciCorrection;

//		@IntParameter({0})
//		private int importOffset;
		

	}
	
	private static Map<LocalDate, Integer> interpolateImport(Map<LocalDate, Integer> importMap, double importFactor, LocalDate start, LocalDate end, double a, double b) {
		
		int days = end.getDayOfYear() - start.getDayOfYear();
		
		for (int i = 1; i<=days; i++) {
			double fraction = (double) i / days;
			importMap.put(start.plusDays(i), (int) Math.round(importFactor * (a + fraction * (b-a))));
		}
		
		return importMap;
		
	}
	

}
