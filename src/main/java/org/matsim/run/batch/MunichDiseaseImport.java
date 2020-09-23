package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;


import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.InfectionModel;
import org.matsim.episim.model.OldSymmetricContactModel;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzMunichWeekScenario2020Symmetric;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * batch class used for calibration of scenario with disease import from data
 */
public class MunichDiseaseImport implements BatchRun<MunichDiseaseImport.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Params params) {
		return (AbstractModule) Modules.override(new SnzMunichWeekScenario2020Symmetric(25))
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
		return Metadata.of("munich", "diseaseImport");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzMunichWeekScenario2020Symmetric module = new SnzMunichWeekScenario2020Symmetric();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);

		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

//		episimConfig.setInitialInfectionDistrict(null);
//		episimConfig.setInitialInfections(Integer.MAX_VALUE);

//		if (params.ageBoundaries.startsWith("0")) {
//			episimConfig.setUpperAgeBoundaryForInitInfections(9);
//		}
//		if (params.ageBoundaries.startsWith("1")) {
//			episimConfig.setLowerAgeBoundaryForInitInfections(10);
//			episimConfig.setUpperAgeBoundaryForInitInfections(19);
//		}
//		if (params.ageBoundaries.startsWith("2")) {
//			episimConfig.setLowerAgeBoundaryForInitInfections(20);
//			episimConfig.setUpperAgeBoundaryForInitInfections(29);
//		}
//		if (params.ageBoundaries.startsWith("3")) {
//			episimConfig.setLowerAgeBoundaryForInitInfections(30);
//			episimConfig.setUpperAgeBoundaryForInitInfections(39);
//		}
//		if (params.ageBoundaries.startsWith("4")) {
//			episimConfig.setLowerAgeBoundaryForInitInfections(40);
//			episimConfig.setUpperAgeBoundaryForInitInfections(49);
//		}
//		if (params.ageBoundaries.startsWith("5")) {
//			episimConfig.setLowerAgeBoundaryForInitInfections(50);
//			episimConfig.setUpperAgeBoundaryForInitInfections(59);
//		}
//		if (params.ageBoundaries.startsWith("6")) {
//			episimConfig.setLowerAgeBoundaryForInitInfections(60);
//			episimConfig.setUpperAgeBoundaryForInitInfections(69);
//		}
//		if (params.ageBoundaries.startsWith("7")) {
//			episimConfig.setLowerAgeBoundaryForInitInfections(70);
//			episimConfig.setUpperAgeBoundaryForInitInfections(79);
//		}
//		if (params.ageBoundaries.startsWith("8")) {
//			episimConfig.setLowerAgeBoundaryForInitInfections(80);
//		}

		episimConfig.setCalibrationParameter(params.calibrationParam);
		episimConfig.setStartDate(params.startDate);

		Map<LocalDate, Integer> importMap = new HashMap<>();

		//scale the disease import according to scenario size (number of agents in relation to berlin scenario)
		double importFactor = 1.0 * SnzMunichWeekScenario2020Symmetric.SCALE_FACTOR_MUNICH_TO_BERLIN;

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
//		double ciCorrection = params.ciCorrection;

		builder.restrict("2020-03-07", Restriction.ofCiCorrection(ciCorrection), AbstractSnzScenario2020.DEFAULT_ACTIVITIES);
		builder.restrict("2020-03-07", Restriction.ofCiCorrection(ciCorrection), "quarantine_home");
		builder.restrict("2020-03-07", Restriction.ofCiCorrection(ciCorrection), "pt");
		builder.restrict("2020-08-08", Restriction.ofCiCorrection(ciCorrection * 0.5), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		long seed;

		@StartDates({"2020-02-15", "2020-02-16", "2020-02-17", "2020-02-18"})
		LocalDate startDate;

//		@Parameter({1.})
//		private double importFactor;

		@Parameter({8.e-6, 6.e-6, 4.e-6})
		private double calibrationParam;

//		@StringParameter({"0-9", "10-19", "20-29", "30-39", "40-49", "50-59", "60-69", "70-79", "80+", "random"})
//		public String ageBoundaries;

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
