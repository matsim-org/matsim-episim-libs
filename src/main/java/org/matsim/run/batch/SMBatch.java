package org.matsim.run.batch;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.episim.BatchRun;
import org.matsim.episim.EpisimConfigGroup;
import org.matsim.episim.TracingConfigGroup;
import org.matsim.episim.BatchRun.GenerateSeeds;
import org.matsim.episim.BatchRun.IntParameter;
import org.matsim.episim.BatchRun.Metadata;
import org.matsim.episim.BatchRun.Parameter;
import org.matsim.episim.BatchRun.StringParameter;
import org.matsim.episim.EpisimConfigGroup.InfectionParams;
import org.matsim.episim.model.AgeDependentInfectionModelWithSeasonality;
import org.matsim.episim.model.AgeDependentProgressionModel;
import org.matsim.episim.model.ContactModel;
import org.matsim.episim.model.FaceMask;
import org.matsim.episim.model.InfectionModel;
import org.matsim.episim.model.InfectionModelWithSeasonality;
import org.matsim.episim.model.ProgressionModel;
import org.matsim.episim.model.SymmetricContactModel;
import org.matsim.episim.policy.FixedPolicy;
import org.matsim.episim.policy.FixedPolicy.ConfigBuilder;
import org.matsim.episim.policy.Restriction;
import org.matsim.run.batch.BerlinSymmetric.Params;
import org.matsim.run.modules.AbstractSnzScenario2020;
import org.matsim.run.modules.SnzBerlinWeekScenario2020;
import org.matsim.run.modules.SnzBerlinWeekScenario2020Symmetric;
import org.matsim.vehicles.VehicleType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;


/**
 * sebastians playground
 */
public class SMBatch implements BatchRun<SMBatch.Params> {


	@Override
	public AbstractModule getBindings(int id, @Nullable Object params) {
		return (AbstractModule) Modules.override(new SnzBerlinWeekScenario2020Symmetric(25))
				.with(new AbstractModule() {
					@Override
					protected void configure() {
						bind(ContactModel.class).to(SymmetricContactModel.class).in(Singleton.class);
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
		return Metadata.of("berlin", "weekSymmetric");
	}

	@Override
	public Config prepareConfig(int id, Params params) {

		SnzBerlinWeekScenario2020Symmetric module = new SnzBerlinWeekScenario2020Symmetric();
		Config config = module.config();
		config.global().setRandomSeed(params.seed);
		
		EpisimConfigGroup episimConfig = ConfigUtils.addOrGetModule(config, EpisimConfigGroup.class);

//		episimConfig.setStartDate(params.startDate);

		
//		episimConfig.setInitialInfectionDistrict("Berlin");
//		episimConfig.setInitialInfections(Integer.MAX_VALUE);
		
//		Map<LocalDate, Integer> importMap = new HashMap<>();
//		double importFactor = 2.;
//		int importOffset = params.importOffset;
//		importMap.put(episimConfig.getStartDate(), (int) Math.round(0.9 * importFactor));
//		importMap.put(LocalDate.of(2020, 2, 25).plusDays(importOffset), (int) Math.round(2.2 * importFactor));
//		importMap.put(LocalDate.of(2020, 2, 26).plusDays(importOffset), (int) Math.round(3.5 * importFactor));
//		importMap.put(LocalDate.of(2020, 2, 27).plusDays(importOffset), (int) Math.round(4.8 * importFactor));
//		importMap.put(LocalDate.of(2020, 2, 28).plusDays(importOffset), (int) Math.round(6.1 * importFactor));
//		importMap.put(LocalDate.of(2020, 2, 29).plusDays(importOffset), (int) Math.round(7.4 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 1).plusDays(importOffset), (int) Math.round(8.6 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 2).plusDays(importOffset), (int) Math.round(9.9 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 3).plusDays(importOffset), (int) Math.round(11.8 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 4).plusDays(importOffset), (int) Math.round(13.7 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 5).plusDays(importOffset), (int) Math.round(15.6 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 6).plusDays(importOffset), (int) Math.round(17.4 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 7).plusDays(importOffset), (int) Math.round(19.3 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 8).plusDays(importOffset), (int) Math.round(21.2 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 9).plusDays(importOffset), (int) Math.round(23.1 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 10).plusDays(importOffset), (int) Math.round(21.7 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 11).plusDays(importOffset), (int) Math.round(20.4 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 12).plusDays(importOffset), (int) Math.round(19. * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 13).plusDays(importOffset), (int) Math.round(17.7 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 14).plusDays(importOffset), (int) Math.round(16.3 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 15).plusDays(importOffset), (int) Math.round(15. * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 16).plusDays(importOffset), (int) Math.round(13.6 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 17).plusDays(importOffset), (int) Math.round(12.2 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 18).plusDays(importOffset), (int) Math.round(10.8 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 19).plusDays(importOffset), (int) Math.round(9.5 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 20).plusDays(importOffset), (int) Math.round(8.1 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 21).plusDays(importOffset), (int) Math.round(6.7 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 22).plusDays(importOffset), (int) Math.round(5.3 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 23).plusDays(importOffset), (int) Math.round(3.9 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 24).plusDays(importOffset), (int) Math.round(3.5 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 25).plusDays(importOffset), (int) Math.round(3.1 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 26).plusDays(importOffset), (int) Math.round(2.6 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 27).plusDays(importOffset), (int) Math.round(2.2 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 28).plusDays(importOffset), (int) Math.round(1.8 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 29).plusDays(importOffset), (int) Math.round(1.4 * importFactor));
//		importMap.put(LocalDate.of(2020, 3, 30).plusDays(importOffset), (int) Math.round(1. * importFactor));
//		importMap.put(LocalDate.of(2020, 4, 6).plusDays(importOffset), (int) Math.round(0.4 * importFactor));
//		importMap.put(LocalDate.of(2020, 4, 13).plusDays(importOffset), (int) Math.round(0.1 * importFactor));
//		
//		episimConfig.setInfections_pers_per_day(importMap);
		
		FixedPolicy.ConfigBuilder builder = FixedPolicy.parse(episimConfig.getPolicy());
		
		builder.restrict("2020-09-14", Restriction.ofCiCorrection(0.6 * params.eduCiCorrection), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		
		TracingConfigGroup tracingConfig = ConfigUtils.addOrGetModule(config, TracingConfigGroup.class);
		
		tracingConfig.setTracingCapacity_pers_per_day(Map.of(
				LocalDate.of(2020, 4, 1), 30,
				LocalDate.of(2020, 6, 1), params.tracingCapacity)		
		);
		
		if (params.mask.equals("none"));
		else if (params.mask.equals("cloth90")) builder.restrict("2020-09-14", Restriction.ofMask(FaceMask.CLOTH, 0.9), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");
		else if (params.mask.equals("FFP90")) builder.restrict("2020-09-14", Restriction.ofMask(FaceMask.N95, 0.9), "educ_primary", "educ_kiga", "educ_secondary", "educ_higher", "educ_tertiary", "educ_other");

		episimConfig.setPolicy(FixedPolicy.class, builder.build());

		return config;
	}

	public static final class Params {

		@GenerateSeeds(10)
		long seed;
		
//		@StringParameter({"2020-02-16", "2020-02-18"})
//		public String startDate;
		
		@Parameter({0.25, 0.5, 1.})
		private double eduCiCorrection;
		
		@IntParameter({Integer.MAX_VALUE, 100})
		private int tracingCapacity;
		
		@StringParameter({"none", "cloth90", "FFP90"})
		public String mask;
		
//		@IntParameter({0, -5})
//		private int importOffset;
		

	}
	

}
